package net.suberic.pooka.cache;
import javax.mail.*;
import javax.mail.event.MessageCountEvent;
import javax.mail.internet.MimeMessage;
import javax.mail.event.MessageChangedEvent;
import net.suberic.pooka.*;
import java.util.Vector;
import java.util.StringTokenizer;
import java.util.HashMap;
import net.suberic.pooka.gui.MessageProxy;
import net.suberic.pooka.gui.FolderTableModel;

/**
 * A FolderInfo which caches its messages in a MessageCache.
 */

public class CachingFolderInfo extends net.suberic.pooka.UIDFolderInfo {
    protected MessageCache cache = null;
    protected HashMap uidToInfoTable = new HashMap();
    protected long uidValidity;

    public CachingFolderInfo(StoreInfo parent, String fname) {
	super(parent, fname);
	
	try {
	    cache = new SimpleFileCache(this, Pooka.getProperty(getFolderProperty() + ".cacheDir", ""));
	} catch (java.io.IOException ioe) {
	    System.out.println("Error creating cache!");
	    ioe.printStackTrace();
	}
    }

    public CachingFolderInfo(FolderInfo parent, String fname) {
	super(parent, fname);
	
	try {
	    cache = new SimpleFileCache(this, Pooka.getProperty(getFolderProperty() + ".cacheDir", ""));
	} catch (java.io.IOException ioe) {
	    System.out.println("Error creating cache!");
	    ioe.printStackTrace();
	}
    }

    /**
     * Loads all Messages into a new FolderTableModel, sets this 
     * FolderTableModel as the current FolderTableModel, and then returns
     * said FolderTableModel.  This is the basic way to populate a new
     * FolderTableModel.
     */
    public synchronized void loadAllMessages() {
	if (folderTableModel == null) {
	    Vector messageProxies = new Vector();
	    
	    FetchProfile fp = createColumnInformation();
	    if (loaderThread == null) 
		loaderThread = createLoaderThread();
	    
	    try {
		if (!(getFolder().isOpen())) {
		    openFolder(Folder.READ_WRITE);
		}
		
		long[] uids = cache.getMessageUids();
		MessageInfo mi;
		
		for (int i = 0; i < uids.length; i++) {
		    Message m = new CachingMimeMessage(this, uids[i]);
		    mi = new MessageInfo(m, this);
		    
		    messageProxies.add(new MessageProxy(getColumnValues() , mi));
		    messageToInfoTable.put(m, mi);
		    uidToInfoTable.put(new Long(uids[i]), mi);
		}
	    } catch (MessagingException me) {
		System.out.println("aigh!  messaging exception while loading!  implement Pooka.showError()!");
	    }
	    
	    FolderTableModel ftm = new FolderTableModel(messageProxies, getColumnNames(), getColumnSizes());
	    
	    setFolderTableModel(ftm);
	    
	    loaderThread.loadMessages(messageProxies);
	    
	    if (!loaderThread.isAlive())
		loaderThread.start();
	}
    }
    
    /**
     * This just checks to see if we can get a NewMessageCount from the
     * folder.  As a brute force method, it also accesses the folder
     * at every check, catching and throwing away any Exceptions that happen.  
     * It's nasty, but it _should_ keep the Folder open..
     */
    /*
      // excluded--same as UIDFolderInfo.
    public void checkFolder() {
	if (Pooka.isDebug())
	    System.out.println("checking folder " + getFolderName());

	// i'm taking this almost directly from ICEMail; i don't know how
	// to keep the stores/folders open, either.  :)

	StoreInfo s = null;
	try {
	    
	    if (isOpen()) {
                Folder current = getFolder();
                if (current != null && current.isOpen()) {
                    current.getNewMessageCount();
                    current.getUnreadMessageCount();
                }
	    } else if (isAvailable() && (status == PASSIVE || status == LOST_CONNECTION)) {
		s = getParentStore();
		if (! s.isConnected())
		    s.connectStore();
		
		openFolder(Folder.READ_WRITE);

		if (isAvailable() && preferred_state == PASSIVE)
		    closeFolder(false);
	    } 
	    

	} catch ( MessagingException me ) {
	}
	
	resetMessageCounts();
    }
    */

    /*
      // excluded--same as parent class.
    protected void updateFolderOpenStatus(boolean isNowOpen) {
	if (isNowOpen) {
	    status = CONNECTED;
	    try {
		uidValidity = ((UIDFolder) getFolder()).getUIDValidity();
		if (getFolderTableModel() != null)
		    synchronizeCache();
	    } catch (Exception e) { }
	    
	} else
	    status = CLOSED;
    }
    */

    /**
     * Gets the row number of the first unread message.  Returns -1 if
     * there are no unread messages, or if the FolderTableModel is not
     * set or empty.
     */
    
    public int getFirstUnreadMessage() {
	// one part brute, one part force, one part ignorance.

	if (Pooka.isDebug())
	    System.out.println("getting first unread message");

	if (getFolderTableModel() == null)
	    return -1;

	try {
	    int countUnread = 0;
	    int i;
	    int unreadCount = cache.getUnreadMessageCount();
	    if (unreadCount > 0) {
		long[] uids = getCache().getMessageUids();

		for (i = uids.length - 1; ( i >= 0 && countUnread < unreadCount) ; i--) {
		    if (!(getMessageInfoByUid(uids[i]).getFlags().contains(Flags.Flag.SEEN))) 
			countUnread++;
		}
		if (Pooka.isDebug())
		    System.out.println("Returning " + i);
		return i + 1;
	    } else { 
		if (Pooka.isDebug())
		    System.out.println("Returning -1");
		return -1;
	    }
	} catch (MessagingException me) {
	    if (Pooka.isDebug())
		System.out.println("Messaging Exception.  Returning -1");
	    return -1;
	}

    }
    
    /**
     * This synchronizes the cache with the new information from the 
     * Folder.
     */
    public void synchronizeCache() throws MessagingException {
	if (Pooka.isDebug())
	    System.out.println("synchronizing cache.");

	long cacheUidValidity = getCache().getUIDValidity();
	
	if (uidValidity != cacheUidValidity) {
	    getCache().invalidateCache();
	    getCache().setUIDValidity(uidValidity);
	}

	// first write all the changes that we made back to the server.
	getCache().writeChangesToServer(getFolder());

	FetchProfile fp = new FetchProfile();
	fp.add(FetchProfile.Item.ENVELOPE);
	fp.add(FetchProfile.Item.FLAGS);
	fp.add(UIDFolder.FetchProfileItem.UID);
	Message[] messages = getFolder().getMessages();
	getFolder().fetch(messages, fp);

	long[] uids = new long[messages.length];

	for (int i = 0; i < messages.length; i++) {
	    uids[i] = ((UIDFolder)getFolder()).getUID(messages[i]);
	}
	
	if (Pooka.isDebug())
	    System.out.println("synchronizing--uids.length = " + uids.length);

	long[] addedUids = cache.getAddedMessages(uids, uidValidity);
	if (Pooka.isDebug())
	    System.out.println("synchronizing--addedUids.length = " + addedUids.length);

	if (addedUids.length > 0) {
	    Message[] addedMsgs = ((UIDFolder)getFolder()).getMessagesByUID(addedUids);
	    MessageCountEvent mce = new MessageCountEvent(getFolder(), MessageCountEvent.ADDED, false, addedMsgs);
	    messagesAdded(mce);
	}    

	long[] removedUids = cache.getRemovedMessages(uids, uidValidity);
	if (Pooka.isDebug())
	    System.out.println("synchronizing--removedUids.length = " + removedUids.length);

	if (removedUids.length > 0) {
	    Message[] removedMsgs = new Message[removedUids.length];
	    for (int i = 0 ; i < removedUids.length; i++) {
		removedMsgs[i] = getMessageInfoByUid(removedUids[i]).getRealMessage();
	    }
	    MessageCountEvent mce = new MessageCountEvent(getFolder(), MessageCountEvent.REMOVED, false, removedMsgs);
	    messagesRemoved(mce);
	    
	}

	updateFlags(uids, messages, cacheUidValidity);
	
    }

    protected void updateFlags(long[] uids, Message[] messages, long uidValidity) throws MessagingException {
	for (int i = 0; i < messages.length; i++) {
	    getCache().cacheMessage((MimeMessage)messages[i], uids[i], uidValidity, SimpleFileCache.FLAGS);
	}
	
    }

    
    protected void runMessagesAdded(MessageCountEvent mce) {
	if (folderTableModel != null) {
	    Message[] addedMessages = mce.getMessages();
	    MessageInfo mp;
	    Vector addedProxies = new Vector();
	    for (int i = 0; i < addedMessages.length; i++) {
		if (addedMessages[i] instanceof CachingMimeMessage) {
		    mp = new MessageInfo(addedMessages[i], CachingFolderInfo.this);
		    addedProxies.add(new MessageProxy(getColumnValues(), mp));
		    messageToInfoTable.put(addedMessages[i], mp);
		    uidToInfoTable.put(new Long(((CachingMimeMessage) addedMessages[i]).getUID()), mp);
		    try {
			getCache().cacheMessage((MimeMessage)addedMessages[i], ((CachingMimeMessage)addedMessages[i]).getUID(), getUIDValidity(), SimpleFileCache.FLAGS_AND_HEADERS);
		    } catch (MessagingException me) {
			System.out.println("caught exception:  " + me);
			me.printStackTrace();
			
		    }
		    
		} else {
		    // it's a 'real' message from the server.
		    
		    long uid = -1;
		    try {
			uid = ((UIDFolder)getFolder()).getUID(addedMessages[i]);
		    } catch (MessagingException me) {
		    }
		    
		    CachingMimeMessage newMsg = new CachingMimeMessage(CachingFolderInfo.this, uid);
		    mp = new MessageInfo(newMsg, CachingFolderInfo.this);
		    addedProxies.add(new MessageProxy(getColumnValues(), mp));
		    messageToInfoTable.put(newMsg, mp);
		    uidToInfoTable.put(new Long(uid), mp);
		    try {
			getCache().cacheMessage((MimeMessage)addedMessages[i], uid, getUIDValidity(), SimpleFileCache.FLAGS_AND_HEADERS);
		    } catch (MessagingException me) {
			System.out.println("caught exception:  " + me);
			me.printStackTrace();
		    }
		}
	    }
	    addedProxies.removeAll(applyFilters(addedProxies));
	    if (addedProxies.size() > 0) {
		if (getFolderTableModel() != null) 
		    getFolderTableModel().addRows(addedProxies);
		setNewMessages(true);
		resetMessageCounts();
		fireMessageCountEvent(mce);
	    }
	
	}
    }

    protected void runMessagesRemoved(MessageCountEvent mce) {
	Message[] removedMessages = mce.getMessages();
	if (Pooka.isDebug())
	    System.out.println("removedMessages was of size " + removedMessages.length);
	MessageInfo mi;
	Vector removedProxies=new Vector();
	for (int i = 0; i < removedMessages.length; i++) {
	    if (Pooka.isDebug())
		System.out.println("checking for existence of message.");
	    
	    if (removedMessages[i] != null && removedMessages[i] instanceof CachingMimeMessage) {
		mi = getMessageInfo(removedMessages[i]);
		if (mi.getMessageProxy() != null)
		    mi.getMessageProxy().close();
		
		if (mi != null) {
		    if (Pooka.isDebug())
			System.out.println("message exists--removing");
		    removedProxies.add(mi.getMessageProxy());
		    messageToInfoTable.remove(mi);
		    uidToInfoTable.remove(new Long(((CachingMimeMessage) removedMessages[i]).getUID()));
		    getCache().invalidateCache(((CachingMimeMessage) removedMessages[i]).getUID(), SimpleFileCache.CONTENT);
		    
		}
	    } else {
		// not a CachingMimeMessage.
		long uid = -1;
		try {
		    uid =((UIDFolder)getFolder()).getUID(removedMessages[i]);
		} catch (MessagingException me) {
		    
		}
		
		mi = getMessageInfoByUid(uid);
		if (mi != null) {

		    if (mi.getMessageProxy() != null)
			mi.getMessageProxy().close();
		    
		    if (Pooka.isDebug())
			System.out.println("message exists--removing");
		    
		    Message localMsg = mi.getMessage();
		    removedProxies.add(mi.getMessageProxy());
		    messageToInfoTable.remove(localMsg);
		    uidToInfoTable.remove(new Long(uid));
		    getCache().invalidateCache(uid, SimpleFileCache.CONTENT);
		}
	    }
	    if (getFolderTableModel() != null)
		getFolderTableModel().removeRows(removedProxies);
	}
	resetMessageCounts();
	fireMessageCountEvent(mce);
	
	//}
    }

    /**
     * This updates the TableInfo on the changed messages.
     * 
     * As defined by java.mail.MessageChangedListener.
     */
    
    public void runMessageChanged(MessageChangedEvent mce) {
	// if the message is getting deleted, then we don't
	// really need to update the table info.  for that 
	// matter, it's likely that we'll get MessagingExceptions
	// if we do, anyway.
	try {
	    if (!mce.getMessage().isSet(Flags.Flag.DELETED) || ! Pooka.getProperty("Pooka.autoExpunge", "true").equalsIgnoreCase("true")) {
		Message msg = mce.getMessage();
		long uid = -1;
		if (msg != null && msg instanceof CachingMimeMessage) {
		    uid = ((CachingMimeMessage) msg).getUID();
		} else {
		    uid = ((UIDFolder)getFolder()).getUID(msg);
		}
		MessageInfo mi = getMessageInfoByUid(uid);
		MessageProxy mp = mi.getMessageProxy();
		if (mp != null) {
		    if (msg != null && msg instanceof CachingMimeMessage) {
			if (mce.getMessageChangeType() == MessageChangedEvent.FLAGS_CHANGED)
			    getCache().cacheMessage((MimeMessage)msg, uid, uidValidity, SimpleFileCache.FLAGS);
			else if (mce.getMessageChangeType() == MessageChangedEvent.ENVELOPE_CHANGED)
			    getCache().cacheMessage((MimeMessage)msg, uid, uidValidity, SimpleFileCache.HEADERS);
		    }
		    mp.unloadTableInfo();
		    mp.loadTableInfo();
		}
	    }
	} catch (MessagingException me) {
	    // if we catch a MessagingException, it just means
	    // that the message has already been expunged.
	}
	
	fireMessageChangedEvent(mce);
    }
    
    /**
     * This sets the given Flag for all the MessageInfos given.
     */
    public void setFlags(MessageInfo[] msgs, Flags flag, boolean value) throws MessagingException {
	// no optimization here.
	for (int i = 0; i < msgs.length; i++) {
	    msgs[i].getRealMessage().setFlags(flag, value);
	}
    }

    /**
     * This copies the given messages to the given FolderInfo.
     */
    public void copyMessages(MessageInfo[] msgs, FolderInfo targetFolder) throws MessagingException {
	targetFolder.appendMessages(msgs);
    }

    /**
     * This appends the given message to the given FolderInfo.
     */
    public void appendMessages(MessageInfo[] msgs) throws MessagingException {
	if (isAvailable()) {
	    super.appendMessages(msgs);
	} else {
	    throw new MessagingException("cannot append messages to an unavailable folder.");
	}
    }
    
    /**
     * This expunges the deleted messages from the Folder.
     */
    public void expunge() throws MessagingException {
	if (isOpen())
	    getFolder().expunge();
	else if (shouldBeOpen()) {
	    openFolder(Folder.READ_WRITE);
	    getFolder().expunge();
	} else {
	    getCache().expungeMessages();
	}
    }

    /**
     * This updates the children of the current folder.  Generally called
     * when the folderList property is changed.
     */
    
    public void updateChildren() {
	Vector newChildren = new Vector();

	String childList = Pooka.getProperty(getFolderProperty() + ".folderList", "");
	if (childList != "") {
	    StringTokenizer tokens = new StringTokenizer(childList, ":");
	    
	    String newFolderName;
	
	    for (int i = 0 ; tokens.hasMoreTokens() ; i++) {
		newFolderName = (String)tokens.nextToken();
		FolderInfo childFolder = getChild(newFolderName);
		if (childFolder == null) {
		    childFolder = new CachingFolderInfo(this, newFolderName);
		    newChildren.add(childFolder);
		} else {
		    newChildren.add(childFolder);
		}
	    }
       
	    children = newChildren;
	    
	    if (folderNode != null) 
		folderNode.loadChildren();
	}
    }

    /**
     * Unloads all messages.  This should be run if ever the current message
     * information becomes out of date, as can happen when the connection
     * to the folder goes down.
     *
     * Note that for this implementation, we just keep everything; we only
     * need to worry when we do the cache synchronization.
     */
    public void unloadAllMessages() {
	//folderTableModel = null;
    }

    /**
     * This method closes the Folder.  If you open the Folder using 
     * openFolder (which you should), then you should use this method
     * instead of calling getFolder.close().  If you don't, then the
     * FolderInfo will try to reopen the folder.
     */
    public void closeFolder(boolean expunge) throws MessagingException {

	if (getFolderTracker() != null) {
	    getFolderTracker().removeFolder(this);
	    setFolderTracker(null);
	}

	if (isLoaded() && isAvailable()) {
	    setStatus(CLOSED);
	    try {
		getFolder().close(expunge);
	    } catch (java.lang.IllegalStateException ise) {
		throw new MessagingException(ise.getMessage(), ise);
	    }
	}

    }

    /**
     * This returns the MessageCache associated with this FolderInfo,
     * if any.
     */
    public MessageCache getCache() {
	return cache;
    }

    /**
     * Returns the MessageInfo associated with the given uid.
     */
    public MessageInfo getMessageInfoByUid(long uid) {
	return (MessageInfo) uidToInfoTable.get(new Long(uid));
    }

    /**
     * Returns the "real" message from the underlying folder that matches up
     * to the given UID.
     */
    public javax.mail.internet.MimeMessage getRealMessageById(long uid) throws MessagingException {
	Folder f = getFolder();
	if (f != null && f instanceof UIDFolder) {
	    javax.mail.internet.MimeMessage m = null;
	    try {
		m = (javax.mail.internet.MimeMessage) ((UIDFolder) f).getMessageByUID(uid);
		return m;
	    } catch (IllegalStateException ise) {
		return null;
	    }
	} else {
	    return null;
	}
    }

    public long getUIDValidity() {
	return uidValidity;
    }
}
