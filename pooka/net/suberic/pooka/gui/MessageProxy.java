package net.suberic.pooka.gui;
import net.suberic.pooka.*;
import net.suberic.util.gui.ConfigurablePopupMenu;
import net.suberic.util.thread.*;
import net.suberic.pooka.gui.filter.DisplayFilter;
import javax.mail.*;
import javax.mail.internet.MimeMessage;
import javax.mail.event.*;
import javax.swing.*;
import java.util.Hashtable;
import java.util.Vector;
import java.awt.event.*;
import java.awt.print.*;
import java.io.*;

public class MessageProxy {

    class SaveMessageThread extends Thread {
	
	MimeMessage msg;
	File saveFile;
	JDialog jd;
	JProgressBar progressBar;
	boolean running = true;

	SaveMessageThread(MimeMessage newMsg, File newSaveFile) {
	    msg = newMsg;
	    saveFile = newSaveFile;
	}

	public void run() {
	    InputStream decodedIS = null;
	    BufferedOutputStream outStream = null;
	    
	    int msgSize = 0;
	    
	    try {
		msgSize = msg.getSize();
		
		createDialog(msgSize);
		jd.show();
		
		outStream = new BufferedOutputStream(new FileOutputStream(saveFile));
		int b=0;
		byte[] buf = new byte[32768];
		
		b = decodedIS.read(buf);
		while (b != -1 && running) {
		    outStream.write(buf, 0, b);
		    progressBar.setValue(progressBar.getValue() + b);
		    b = decodedIS.read(buf);
		}
		
		jd.dispose();
		
	    } catch (IOException ioe) {
		Pooka.getUIFactory().showError("Error saving file:  " + ioe.getMessage());
		cancelSave();
	    } catch (MessagingException me) {
		Pooka.getUIFactory().showError("Error saving file:  " + me.getMessage());
		cancelSave();
	    } finally {
		if (outStream != null) {
		    try {
			outStream.flush();
			outStream.close();
		    } catch (IOException ioe) {}
		}
	    }
	}

	public void createDialog(int msgSize) {
	    progressBar = new JProgressBar(0, msgSize);
	    progressBar.setBorderPainted(true);
	    progressBar.setStringPainted(true);

	    jd = new JDialog();
	    jd.getContentPane().setLayout(new BoxLayout(jd.getContentPane(), BoxLayout.Y_AXIS));
	    JLabel nameLabel = new JLabel(saveFile.getName());
	    JPanel buttonPanel = new JPanel();
	    JButton cancelButton = new JButton(Pooka.getProperty("button.cancel", "Cancel"));
	    cancelButton.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
			cancelSave();
		    }
		});
	    buttonPanel.add(cancelButton);

	    jd.getContentPane().add(nameLabel);
	    jd.getContentPane().add(progressBar);
	    jd.getContentPane().add(buttonPanel);

	    jd.pack();
	}

	public void cancelSave() {
	    try {
		saveFile.delete();
	    } catch (Exception e) {}
	    jd.dispose();
	}
    }

    // the underlying MessageInfo
    MessageInfo messageInfo;

    // the information for the FolderTable
    Vector tableInfo;

    // matching Filters.
    DisplayFilter[] matchingFilters;

    // the column Headers for the FolderInfo Vector; used for loading the
    // tableInfo.
    Vector columnHeaders;

    // if the tableInfo has been loaded yet.
    boolean loaded = false;

    // commands for the GUI
    Hashtable commands;

    // The Window associated with this MessageProxy.
    MessageUI msgWindow;

    // The GUI factory used by this MessageProxy.
    PookaUIFactory uiFactory;

    public Action[] defaultActions;

    /**
     * This class should make it easy for us to sort subjects correctly.
     * It stores both the subject String itself and a sortingString which
     * is taken to lowercase and also has all of the starting 're:' characters
     * removed.
     */
    public class SubjectLine implements Comparable {
	String subject;
	String sortingSubject;

	/**
	 * Constructor.
	 */
	public SubjectLine(String newSubject) {
	    subject = newSubject;
	    if (subject != null)
		sortingSubject = subject.toLowerCase();
	    else
		sortingSubject = new String("");

	    int cutoffPoint = 0;
	    while(sortingSubject.startsWith("re:", cutoffPoint)) 
		for(cutoffPoint = cutoffPoint + 3; cutoffPoint < sortingSubject.length() && Character.isWhitespace(sortingSubject.charAt(cutoffPoint)); cutoffPoint++) { }
	    if (cutoffPoint != 0)
		sortingSubject = sortingSubject.substring(cutoffPoint);
	}
	
	/**
	 * Compare function.
	 */
	public int compareTo(Object o) {
	    // proper SubjectLines are always greater than null.
	    if (o == null)
		return 1;

	    if (o instanceof SubjectLine) {
		return sortingSubject.compareTo(((SubjectLine)o).sortingSubject);
	    } else
		return sortingSubject.compareToIgnoreCase(o.toString());
	}

	/**
	 * toString() just returns the original subject.
	 */
	public String toString() {
	    return subject;
	}
    }

    protected MessageProxy() {
    }

    /**
     * This creates a new MessageProxy from a set of Column Headers (for 
     * the tableInfo), a Message, and a link to a FolderInfo object.
     */
    public MessageProxy(Vector newColumnHeaders, MessageInfo newMessage) {
	messageInfo = newMessage;
	messageInfo.setMessageProxy(this);

	columnHeaders = newColumnHeaders;

	commands = new Hashtable();
	
	ActionThread folderThread = messageInfo.getFolderInfo().getFolderThread();
	
	defaultActions = new Action[] {
	    new ActionWrapper(new OpenAction(), folderThread),
	    new ActionWrapper(new MoveAction(), folderThread),
	    new ActionWrapper(new ReplyAction(), folderThread),
	    new ActionWrapper(new ReplyAllAction(), folderThread),
	    new ActionWrapper(new ReplyWithAttachmentsAction(), folderThread),
	    new ActionWrapper(new ReplyAllWithAttachmentsAction(), folderThread),
	    new ActionWrapper(new ForwardAction(), folderThread),
	    new ActionWrapper(new ForwardWithAttachmentsAction(), folderThread),
	    new ActionWrapper(new ForwardAsInlineAction(), folderThread),
	    new ActionWrapper(new ForwardAsAttachmentAction(), folderThread),
	    new ActionWrapper(new ForwardQuotedAction(), folderThread),
	    new ActionWrapper(new DeleteAction(), folderThread),
	    new ActionWrapper(new PrintAction(), folderThread),
	    new ActionWrapper(new SaveMessageAction(), folderThread),
	    new ActionWrapper(new CacheMessageAction(), folderThread)
		};
	
        Action[] actions = getActions();
        if (actions != null) {
            for (int i = 0; i < actions.length; i++) {
                Action a = actions[i];
                commands.put(a.getValue(Action.NAME), a);
            }
        }
	
    }

    /**
     * This loads the tableInfo (the fields that will be displayed in the
     * FolderTable) using the columnHeaders property to know which fields
     * to load.
     */
    public synchronized void loadTableInfo() {
	if (!loaded) {
	    try {
		int columnCount = columnHeaders.size();
		
		tableInfo = new Vector();
		
		for(int j=0; j < columnCount; j++) {
		    Object newProperty = columnHeaders.elementAt(j);
		    if (newProperty instanceof String) {
			String propertyName = (String)newProperty;
			
			if (propertyName.startsWith("FLAG")) 
			    tableInfo.addElement(getMessageFlag(propertyName));
			else if (propertyName.equals("attachments"))
			    tableInfo.addElement(new BooleanIcon(getMessageInfo().hasAttachments(), Pooka.getProperty("FolderTable.Attachments.icon", "")));
			else
			    tableInfo.addElement(getMessageInfo().getMessageProperty(propertyName));
		    } else if (newProperty instanceof SearchTermIconManager) {
			SearchTermIconManager stm = (SearchTermIconManager) newProperty;
			tableInfo.addElement(new SearchTermIcon(stm, this));
		    } else if (newProperty instanceof RowCounter) {
			tableInfo.addElement(newProperty);
		    }
		}
		
		getMessageInfo().isSeen();
	    
		// match the given filters for the FolderInfo.
		
		MessageFilter[] folderFilters = getFolderInfo().getDisplayFilters();
		if (folderFilters != null) {
		    Vector tmpMatches = new Vector();
		    for (int i = 0; i < folderFilters.length; i++) {
			if (folderFilters[i].getSearchTerm().match(getMessageInfo().getMessage()))
			    tmpMatches.add(folderFilters[i].getAction());
		    }
		    
		    matchingFilters = new DisplayFilter[tmpMatches.size()];
		    for (int i = 0; i < tmpMatches.size(); i++) {
			//System.out.println("adding a matching filter.");
			matchingFilters[i] = (DisplayFilter) tmpMatches.elementAt(i);
		    }
		}

		loaded=true;
	    } catch (MessagingException me) {
	    }
	}
    }	

    /**
     * This loads the Attachment information into the attachments vector.
     */

    public void loadAttachmentInfo() throws MessagingException {
	messageInfo.loadAttachmentInfo();
    }

    /**
     * Returns the attachments for this Message.
     */
    public Vector getAttachments() throws MessagingException {
	return messageInfo.getAttachments();
    }

    /**
     * Returns whether or not this message has attachments.
     */
    public boolean hasAttachments() throws MessagingException {
	return messageInfo.hasAttachments();
    }

    /**
     * This gets a Flag property from the Message.
     */

    public BooleanIcon getMessageFlag(String flagName) {
	try {
	    if (flagName.equals("FLAG.ANSWERED") )
		return new BooleanIcon(getMessageInfo().flagIsSet(flagName), Pooka.getProperty("FolderTable.Answered.icon", ""));
	    else if (flagName.equals("FLAG.DELETED"))
		return new BooleanIcon(getMessageInfo().flagIsSet(flagName),Pooka.getProperty("FolderTable.Deleted.icon", ""));
	    else if (flagName.equals("FLAG.DRAFT"))
		return new BooleanIcon(getMessageInfo().flagIsSet(flagName), Pooka.getProperty("FolderTable.Draft.icon", ""));
	    else if (flagName.equals("FLAG.FLAGGED"))
		return new BooleanIcon(getMessageInfo().flagIsSet(flagName), Pooka.getProperty("FolderTable.Flagged.icon", ""));
	    else if (flagName.equals("FLAG.RECENT"))
		return new BooleanIcon(getMessageInfo().flagIsSet(flagName), Pooka.getProperty("FolderTable.Recent.icon", ""));
	    else if (flagName.equals("FLAG.NEW")) 
		return new MultiValueIcon(getMessageInfo().flagIsSet("FLAG.SEEN"), getMessageInfo().flagIsSet("FLAG.RECENT"), Pooka.getProperty("FolderTable.New.recentAndUnseenIcon", ""), Pooka.getProperty("FolderTable.New.justUnseenIcon", ""));
	    else if (flagName.equals("FLAG.SEEN"))
		return new BooleanIcon(getMessageInfo().flagIsSet(flagName), Pooka.getProperty("FolderTable.Seen.icon", ""));
	    else
		return new BooleanIcon(false, "");
	} catch (MessagingException me) {
	    return new BooleanIcon(false, "");
	}
    }

    /**
     * this opens a MessageUI for this Message.
     */
    public void openWindow() {
	try {
	    if (getMessageUI() == null)
		setMessageUI(getPookaUIFactory().createMessageUI(this));
	    getMessageUI().openMessageUI();
	    getMessageInfo().setSeen(true);
	} catch (MessagingException me) {
	    showError(Pooka.getProperty("error.Message.openWindow", "Error opening window:  "), me);
	}
    }

    /**
     * Moves the Message into the target Folder.
     */
    public void moveMessage(FolderInfo targetFolder) {
	try {
	    messageInfo.moveMessage(targetFolder);
	} catch (MessagingException me) {
	    showError( Pooka.getProperty("error.Message.CopyErrorMessage", "Error:  could not copy messages to folder:  ") + targetFolder.toString() +"\n", me);
	    if (Pooka.isDebug())
		me.printStackTrace();
	}
    }

    private void replyToMessage(boolean replyAll, boolean withAttachments) {
	if (getMessageUI() != null)
	    getMessageUI().setBusy(true);
	
	FolderDisplayUI fw = getFolderDisplayUI();
	if (fw != null)
	    fw.setBusy(true);;
	try {
	    NewMessageProxy nmp = new NewMessageProxy(getMessageInfo().populateReply(replyAll, withAttachments));
	    MessageUI nmui = getPookaUIFactory().createMessageUI(nmp);
	    nmui.openMessageUI();
	} catch (MessagingException me) {
	    showError(Pooka.getProperty("error.MessageUI.replyFailed", "Failed to create new Message.") + "\n", me);
	}
	if (fw != null)
	    fw.setBusy(false);
	if (getMessageUI() != null)
	    getMessageUI().setBusy(true);;
	
    }

    private void forwardMessage(boolean withAttachments) {
	//forwardMessage(withAttachments, getDefaultProfile().getDefaultForwardMethod());
	forwardMessage(withAttachments, MessageInfo.FORWARD_QUOTED);
    }

    private void forwardMessage(boolean withAttachments, int method) {
	if (getMessageUI() != null)
	    getMessageUI().setBusy(true);
	FolderDisplayUI fw = getFolderDisplayUI();
	if (fw != null)
	    fw.setBusy(true);;
	try {
	    NewMessageProxy nmp = new NewMessageProxy(getMessageInfo().populateForward(withAttachments, method));
	    MessageUI nmui = getPookaUIFactory().createMessageUI(nmp);
	    nmui.openMessageUI();
	    
	} catch (MessagingException me) {
	    if (getMessageUI() != null)
		getMessageUI().showError(Pooka.getProperty("error.MessageUI.replyFailed", "Failed to create new Message.") + "\n" + me.getMessage());
	    else
		Pooka.getUIFactory().showError(Pooka.getProperty("error.MessageUI.replyFailed", "Failed to create new Message.") + "\n" + me.getMessage());
	    
	    me.printStackTrace();
	}
	
	if (fw != null)
	    fw.setBusy(false);
	if (getMessageUI() != null)
	    getMessageUI().setBusy(true);;
	
    }
    
    /**
     * Deletes the Message from the current Folder.  If a Trash folder is
     * set, this method moves the message into the Trash folder.  If no
     * Trash folder is set, this marks the message as deleted.  In addition,
     * if the autoExpunge variable is set to true, it also expunges
     * the message from the mailbox.
     */
    public void deleteMessage(boolean autoExpunge) {
	try {
	    getMessageInfo().deleteMessage(autoExpunge);
	    this.close();
	} catch (MessagingException me) {
	    if (me instanceof NoTrashFolderException) {
		if (getMessageUI().showConfirmDialog(Pooka.getProperty("error.Messsage.DeleteNoTrashFolder", "The Trash Folder configured is not available.\nDelete messages anyway?"), Pooka.getProperty("error.Messsage.DeleteNoTrashFolder.title", "Trash Folder Unavailable"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION)
		    try {
			getMessageInfo().remove(autoExpunge);
			this.close();
		    } catch (MessagingException mex) {
			showError(Pooka.getProperty("error.Message.DeleteErrorMessage", "Error:  could not delete message.") +"\n", mex);
		    }
	    } else {
		showError(Pooka.getProperty("error.Message.DeleteErrorMessage", "Error:  could not delete message.") +"\n", me);
	    }
	}
    }

    /**
     * Opens up a dialog to save the message to a file.
     */
    public void saveMessageToFile() {
	JFileChooser saveChooser = new JFileChooser();
	
	int saveConfirm = saveChooser.showSaveDialog(Pooka.getMainPanel().getContentPanel().getUIComponent());

	if (saveConfirm == JFileChooser.APPROVE_OPTION) 
	    try {
		getMessageInfo().saveMessageAs(saveChooser.getSelectedFile());
	    } catch (MessagingException exc) {
		if (getMessageUI() != null)
		    getMessageUI().showError(Pooka.getProperty("error.SaveFile", "Error saving file") + ":\n", Pooka.getProperty("error.SaveFile", "Error saving file"), exc);
		else
		    Pooka.getUIFactory().showError(Pooka.getProperty("error.SaveFile", "Error saving file") + ":\n", Pooka.getProperty("error.SaveFile", "Error saving file"), exc);
	    }
    }

    public void showError(String message, Exception ex) {
	if (getMessageUI() != null) 
	    getMessageUI().showError(message + ex.getMessage(), ex);
	else
	    Pooka.getUIFactory().showError(message + ex.getMessage(), ex);
    }

    /**
     * Closes this MessageProxy. 
     *
     * For this implementation, the only result is that the MessageUI,
     * if any, is closed.
     */
    public void close() {
	if (getMessageUI() != null)
	    getMessageUI().closeMessageUI();
    }

    /**
     * A convenience method which sets autoExpunge by the value of 
     * Pooka.autoExpunge, and then calls deleteMessage(boolean autoExpunge)
     * with that value.
     */
    public void deleteMessage() {
	deleteMessage(Pooka.getProperty("Pooka.autoExpunge", "true").equals("true"));
    }

    /**
     * This puts the reply prefix 'prefix' in front of each line in the
     * body of the Message.
     */
    public String prefixMessage(String originalMessage, String prefix, String intro) {
	StringBuffer newValue = new StringBuffer(originalMessage);
	
	int currentCR = originalMessage.lastIndexOf('\n', originalMessage.length());
	while (currentCR != -1) {
	    newValue.insert(currentCR+1, prefix);
	    currentCR=originalMessage.lastIndexOf('\n', currentCR-1);
	}
	newValue.insert(0, prefix);
	newValue.insert(0, intro);
	
	return newValue.toString();
    }

    /**
     * This sends the message to the printer, first creating an appropriate
     * print dialog, etc.
     */

    public void printMessage() {
	try {
	    PrinterJob job = PrinterJob.getPrinterJob ();
	    Book book = new Book ();
	    MessagePrinter printer = new MessagePrinter(getMessageInfo());
	    PageFormat pf = job.pageDialog (job.defaultPage ());
	    int count = printer.getPageCount(pf);
	    book.append (printer, pf, count);
	    job.setPageable (book);
	    final PrinterJob externalJob = job;
	    
	    if (job.printDialog ()) {
		Thread t = new Thread(new net.suberic.util.swing.RunnableAdapter() {
			public void run() {
			    try {
				externalJob.print ();
			    }
			    catch (PrinterException ex) {
				ex.printStackTrace ();
			    }
			}
		    });
		t.start();
		
	    }
	} catch (MessagingException me) {
	    showError(Pooka.getProperty("error.Printing", "Error printing Message:  ") + "\n", me);
	}
    }

    /**
     * This creates and shows a PopupMenu for this component.  
     */
    public void showPopupMenu(JComponent component, MouseEvent e) {
	ConfigurablePopupMenu popupMenu = new ConfigurablePopupMenu();
	if (getMessageInfo().getFolderInfo() instanceof net.suberic.pooka.cache.CachingFolderInfo) {
	    popupMenu.configureComponent("MessageProxy.cachingPopupMenu", Pooka.getResources());	
	} else {
	    popupMenu.configureComponent("MessageProxy.popupMenu", Pooka.getResources());	
	}
	popupMenu.setActive(getActions());
	popupMenu.show(component, e.getX(), e.getY());
	    
    }
    
    /**
     * As specified by interface net.suberic.pooka.UserProfileContainer.
     *
     * If the MessageProxy's getMessageInfo().getFolderInfo() is set, this returns the 
     * DefaultProfile of that getMessageInfo().getFolderInfo().  If the getMessageInfo().getFolderInfo() isn't set
     * (should that happen?), this returns null.
     */

    public UserProfile getDefaultProfile() {
	return getMessageInfo().getDefaultProfile();
    }

    /**
     * This returns the tableInfo for this MessageProxy.
     */
    public Vector getTableInfo() {
	if (isLoaded()) {
	    return tableInfo;
	} else {
	    loadTableInfo();
	    return tableInfo;
	}
    }

    public FolderInfo getFolderInfo() {
	return getMessageInfo().getFolderInfo();
    }

    public void setTableInfo(Vector newValue) {
	tableInfo=newValue;
    }

    public boolean isSeen() {
	return getMessageInfo().isSeen();
    }

    public void setSeen(boolean newValue) {
	if (newValue != getMessageInfo().isSeen()) {
	    try {
		getMessageInfo().setSeen(newValue);
	    } catch (MessagingException me) {
		showError( Pooka.getProperty("error.MessageUI.setSeenFailed", "Failed to set Seen flag to ") + newValue + "\n", me);
	    }
	}
    }

    public boolean isLoaded() {
	return loaded;
    }

    /**
     * This sets the loaded value for the MessageProxy to false.   This 
     * should be called only if the TableInfo of the Message has been 
     * changed and needs to be reloaded.
     */
    public void unloadTableInfo() {
	loaded=false;
    }

    public MessageUI getMessageUI() {
	return msgWindow;
    }

    public void setMessageUI(MessageUI newValue) {
	msgWindow = newValue;
    }

    public MessageInfo getMessageInfo() {
	return messageInfo;
    }

    public FolderDisplayUI getFolderDisplayUI() {
	FolderInfo fi = getMessageInfo().getFolderInfo();
	if (fi != null)
	    return fi.getFolderDisplayUI();
	else
	    return null;
	    
    }

    /**
     * Returns the UI Factory currently being used by this MessageProxy.
     */
    public PookaUIFactory getPookaUIFactory() {
	if (uiFactory != null)
	    return uiFactory;
	else
	    return Pooka.getUIFactory();
    }

    /**
     * Sets the UI Factory currently being used by this MessageProxy.
     */
    public void setPookaUIFactory(PookaUIFactory puif) {
	uiFactory = puif;
    }

    /**
     * Returns the matching filters for this MessageProxy.
     */
    public net.suberic.pooka.gui.filter.DisplayFilter[] getMatchingFilters() {
	return matchingFilters;
    }

    public Action getAction(String name) {
	return (Action)commands.get(name);
    }

    public Action[] getActions() {
	return defaultActions;
    }

    public class OpenAction extends AbstractAction {
	OpenAction() {
	    super("file-open");
	}

	public void actionPerformed(java.awt.event.ActionEvent e) {

	    FolderDisplayUI fw = getFolderDisplayUI();
	    if (fw != null)
		fw.setBusy(true);;
	    openWindow();
	    if (fw != null)
		fw.setBusy(false);
	}
    }

    public class MoveAction extends net.suberic.util.DynamicAbstractAction {
	MoveAction() {
	    super("message-move");
	}

	public void actionPerformed(java.awt.event.ActionEvent e) {
	    if (getMessageUI() != null)
		getMessageUI().setBusy(true);
	    FolderDisplayUI fw = getFolderDisplayUI();
	    if (fw != null)
		fw.setBusy(true);;
	    moveMessage((FolderInfo)getValue("target"));
	    if (fw != null)
		fw.setBusy(false);
	    if (getMessageUI() != null)
		getMessageUI().setBusy(true);;
	}

    }

    public class ReplyAction extends AbstractAction {

	ReplyAction() {
	    super("message-reply");
	}

	public void actionPerformed(ActionEvent e) {
	    replyToMessage(false, false);
	}
    }

    public class ReplyWithAttachmentsAction extends AbstractAction {

	ReplyWithAttachmentsAction() {
	    super("message-reply-with-attachments");
	}

	public void actionPerformed(ActionEvent e) {
	    replyToMessage(false, true);
	}
    }

    public class ReplyAllAction extends AbstractAction {

	ReplyAllAction() {
	    super("message-reply-all");
	}

	public void actionPerformed(ActionEvent e) {
	    replyToMessage(true, false);
	}  
    }
    
    public class ReplyAllWithAttachmentsAction extends AbstractAction {

	ReplyAllWithAttachmentsAction() {
	    super("message-reply-all-with-attachments");
	}

	public void actionPerformed(ActionEvent e) {
	    replyToMessage(true, true);
	}	
    }

    public class ForwardAction extends AbstractAction {

	ForwardAction() {
	    super("message-forward");
	}

	public void actionPerformed(ActionEvent e) {
	    forwardMessage(false);
	}
    }

    public class ForwardWithAttachmentsAction extends AbstractAction {

	ForwardWithAttachmentsAction() {
	    super("message-forward-with-attachments");
	}

	public void actionPerformed(ActionEvent e) {
	    forwardMessage(true);
	}
    }

    public class ForwardAsInlineAction extends AbstractAction {

	ForwardAsInlineAction() {
	    super("message-forward-as-inline");
	}

	public void actionPerformed(ActionEvent e) {
	    forwardMessage(false, MessageInfo.FORWARD_AS_INLINE);
	}
    }

    public class ForwardAsAttachmentAction extends AbstractAction {

	ForwardAsAttachmentAction() {
	    super("message-forward-as-attachment");
	}

	public void actionPerformed(ActionEvent e) {
	    forwardMessage(false, MessageInfo.FORWARD_AS_ATTACHMENT);
	}
    }

    public class ForwardQuotedAction extends AbstractAction {

	ForwardQuotedAction() {
	    super("message-forward-quoted");
	}

	public void actionPerformed(ActionEvent e) {
	    forwardMessage(false, MessageInfo.FORWARD_QUOTED);
	}
    }


    public class DeleteAction extends AbstractAction {
	DeleteAction() {
	    super("message-delete");
	}

	public void actionPerformed(ActionEvent e) {
	    if (getMessageUI() != null)
		getMessageUI().setBusy(true);
	    FolderDisplayUI fw = getFolderDisplayUI();
	    if (fw != null)
		fw.setBusy(true);;
	    deleteMessage();
	   
	    if (fw != null)
		fw.setBusy(false);
	}
    }


    public class PrintAction extends AbstractAction {
	PrintAction() {
	    super("file-print");
	}

	public void actionPerformed(ActionEvent e) {
	    if (getMessageUI() != null)
		getMessageUI().setBusy(true);
	    FolderDisplayUI fw = getFolderDisplayUI();
	    if (fw != null)
		fw.setBusy(true);;
	    printMessage();

	    if (fw != null)
		fw.setBusy(false);
	    if (getMessageUI() != null)
		getMessageUI().setBusy(false);
	}
    }

    public class SaveMessageAction extends AbstractAction {
	SaveMessageAction() {
	    super("file-save-as");
	}

	public void actionPerformed(ActionEvent e) {
	    if (getMessageUI() != null)
		getMessageUI().setBusy(true);
	    FolderDisplayUI fw = getFolderDisplayUI();
	    if (fw != null)
		fw.setBusy(true);;
	    saveMessageToFile();

	    if (fw != null)
		fw.setBusy(false);
	    if (getMessageUI() != null)
		getMessageUI().setBusy(false);
	}
    }

    public class CacheMessageAction extends AbstractAction {
	CacheMessageAction() {
	    super("message-cache");
	}

	public void actionPerformed(ActionEvent e) {
	    if (getMessageUI() != null)
		getMessageUI().setBusy(true);
	    FolderDisplayUI fw = getFolderDisplayUI();
	    if (fw != null)
		fw.setBusy(true);;

	    try {
		getMessageInfo().cacheMessage();
	    } catch (MessagingException me) {
		showError(me.getMessage(), me);
	    }

	    if (fw != null)
		fw.setBusy(false);
	    if (getMessageUI() != null)
		getMessageUI().setBusy(false);
	}
    }
}






