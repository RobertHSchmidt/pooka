package net.suberic.pooka.gui;
import net.suberic.pooka.*;
import net.suberic.util.gui.ConfigurablePopupMenu;
import net.suberic.util.thread.*;
import net.suberic.pooka.gui.filter.DisplayFilter;
import net.suberic.pooka.gui.crypto.*;
import javax.mail.*;
import javax.mail.internet.MimeMessage;
import javax.mail.event.*;
import javax.swing.*;
import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import javax.print.event.*;
import java.util.Hashtable;
import java.util.HashMap;
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
	if (getMessageUI() != null)
	  getMessageUI().showError(Pooka.getProperty("error.SaveFile", "Error saving file:  ") + ioe.getMessage());
	else
	  Pooka.getUIFactory().showError(Pooka.getProperty("error.SaveFile", "Error saving file:  ") + ioe.getMessage());
	cancelSave();
      } catch (MessagingException me) {
	if (getMessageUI() != null)
	  getMessageUI().showError(Pooka.getProperty("error.SaveFile", "Error saving file:  ") + me.getMessage());
	else
	  Pooka.getUIFactory().showError(Pooka.getProperty("error.SaveFile", "Error saving file:  ") + me.getMessage());
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
  HashMap tableInfo;
  
  // matching Filters.
  DisplayFilter[] matchingFilters;
  
  // the column Headers for the FolderInfo Vector; used for loading the
  // tableInfo.
  Vector columnHeaders;
  
  // if the tableInfo has been loaded yet.
  boolean loaded = false;

  // if the tableInfo needs to be refreshed.
  boolean refresh = false;
  
  // if the display filters have been run
  boolean filtersMatched = false;
  
  // commands for the GUI
  Hashtable commands;
  
  // The Window associated with this MessageProxy.
  MessageUI msgWindow;

  // the display modes.
  public static int RFC_822 = -1;
  public static int TEXT_ONLY = 0;
  public static int TEXT_PREFERRED = 5;
  public static int HTML_PREFERRED = 10;
  public static int HTML_ONLY = 15;

  // the header modes
  public static int HEADERS_DEFAULT = 0;
  public static int HEADERS_FULL = 1;
  public static int RFC822_STYLE = 2;

  // the types of headers to show for this Message.
  int headerMode = HEADERS_DEFAULT;

  // whether this should be displayed as html, text, or raw RFC822.
  int displayMode = getDefaultDisplayMode();

  // the default actions for this MessageProxy.
  public Action[] defaultActions = null;
  
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
     * Override to compare underlying values.
     */
    public boolean equals(Object o) {
      return (compareTo(o) == 0);
    }
    
    /**
     * toString() just returns the original subject.
     */
    public String toString() {
      return subject;
    }
  }
  
  /**
   * This class should make it easy for us to sort addresses correctly.
   * It stores both the address itself and a sortingString which
   * is taken to lowercase and also has any non alphanumeric cruft (quotation
   * marks, etc.) removed.
   */
  public class AddressLine implements Comparable {
    String address;
    String sortingAddress;
    
    /**
     * Constructor.
     */
    public AddressLine(String newAddress) {
      address = newAddress;
      if (address != null)
	sortingAddress = address.toLowerCase();
      else
	sortingAddress = new String("");
      
      while(sortingAddress.length() > 0 && ! Character.isLetterOrDigit(sortingAddress.charAt(0)))
	sortingAddress = sortingAddress.substring(1);
    }
    
    /**
     * Compare function.
     */
    public int compareTo(Object o) {
      // proper AddressLines are always greater than null.
      if (o == null)
	return 1;
      
      if (o instanceof AddressLine) {
	return sortingAddress.compareTo(((AddressLine)o).sortingAddress);
      } else
	return sortingAddress.compareToIgnoreCase(o.toString());
    }

    /**
     * Override to compare underlying values.
     */
    public boolean equals(Object o) {
      return (compareTo(o) == 0);
    }
    
    /**
     * toString() just returns the original address.
     */
    public String toString() {
      return address;
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
    
  }
  
  /**
   * This loads the tableInfo (the fields that will be displayed in the
   * FolderTable) using the columnHeaders property to know which fields
   * to load.
   */
  public synchronized void loadTableInfo() {
    if (!loaded) {
      try {
	tableInfo = createTableInfo();
	
	getMessageInfo().isSeen();
	
	matchFilters();
	
	loaded=true;
	
	// notify the JTable that this proxy has loaded.
	MessageChangedEvent mce = new net.suberic.pooka.event.MessageTableInfoChangedEvent(this, MessageChangedEvent.ENVELOPE_CHANGED, getMessageInfo().getMessage());
	
	FolderInfo fi = getFolderInfo();
	if (fi != null) {
	  fi.fireMessageChangedEvent(mce);
	}

      } catch (MessagingException me) {
	me.printStackTrace();
      }
    }
    
    
  } 

  /**
   * Refreshes the MessageProxy by reloading the information for the
   * MessageInfo, and then updating the FolderTableInfo if necessary.
   */
  public synchronized void refreshMessage() {
    if (needsRefresh()) {
      try {
	
	// first, tag this so we won't need to be refereshed again.
	
	refresh = false;
	
	// second, refresh the MessageInfo itself.
	
	getMessageInfo().refreshFlags();
	
	// third, compare.
	
	HashMap newTableInfo = createTableInfo();
	
	// check to see if anything has actually changed
	boolean hasChanged = (tableInfo == null);
	// assume that we're not actually changing the values...
	java.util.Iterator it = newTableInfo.keySet().iterator();
	while ((! hasChanged) && it.hasNext()) {
	  Object key = it.next();
	  Object newValue = newTableInfo.get(key);
	  Object oldValue = tableInfo.get(key);
	  if (newValue == null) {
	    if (oldValue != null) {
	      hasChanged = true;
	    }
	  } else if (oldValue == null || ! newValue.equals(oldValue)) {
	    hasChanged = true;
	  }
	}

	// check for the matching filters, also.
	DisplayFilter[] newMatchingFilters = doFilterMatch();
	if (newMatchingFilters == null) {
	  if (matchingFilters != null)
	    hasChanged = true;
	} else if (matchingFilters == null) {
	  hasChanged = true;
	} else if (matchingFilters.length != newMatchingFilters.length) {
	  hasChanged = true;
	} else {
	  for (int i = 0; hasChanged != true && i < newMatchingFilters.length; i++) {
	    DisplayFilter newValue = newMatchingFilters[i];
	    DisplayFilter oldValue = matchingFilters[i];
	    if (newValue != oldValue) {
	      hasChanged = true;
	    }
	  }
	}
	
	if (hasChanged) {
	  tableInfo = newTableInfo;
	  matchingFilters = newMatchingFilters;
	  
	  // notify the JTable that this proxy has loaded.
	  MessageChangedEvent mce = new net.suberic.pooka.event.MessageTableInfoChangedEvent(this, MessageChangedEvent.ENVELOPE_CHANGED, getMessageInfo().getMessage());
	  
	  FolderInfo fi = getFolderInfo();
	  if (fi != null) {
	    fi.fireMessageChangedEvent(mce);
	  }
	}
      } catch (MessagingException me) {
	me.printStackTrace();
      }
    }

  }

  /**
   * Creates a TableInfo Vector.
   */
  protected HashMap createTableInfo() throws MessagingException {
    int columnCount = columnHeaders.size();
    
    HashMap returnValue = new HashMap();
    
    for(int j=0; j < columnCount; j++) {
      try {
	Object newProperty = columnHeaders.elementAt(j);
	if (newProperty instanceof String) {
	  String propertyName = (String)newProperty;
	  
	  if (propertyName.startsWith("FLAG")) 
	    returnValue.put(newProperty, getMessageFlag(propertyName));
	  else if (propertyName.equals("attachments"))
	    returnValue.put(newProperty, new BooleanIcon(getMessageInfo().hasAttachments(), Pooka.getProperty("FolderTable.Attachments.icon", "")));
	  else if (propertyName.equals("crypto"))
	    returnValue.put(newProperty, new BooleanIcon(getMessageInfo().hasEncryption(), Pooka.getProperty("FolderTable.Crypto.icon", "")));
	  else if (propertyName.equalsIgnoreCase("subject")) 
	    returnValue.put(newProperty, new SubjectLine((String) getMessageInfo().getMessageProperty(propertyName)));
	  else if (propertyName.equalsIgnoreCase("from")) 
	    returnValue.put(newProperty, new AddressLine((String) getMessageInfo().getMessageProperty(propertyName)));
	  else
	    returnValue.put(newProperty, getMessageInfo().getMessageProperty(propertyName));
	} else if (newProperty instanceof SearchTermIconManager) {
	  SearchTermIconManager stm = (SearchTermIconManager) newProperty;
	  returnValue.put(newProperty, new SearchTermIcon(stm, this));
	} else if (newProperty instanceof RowCounter) {
	  returnValue.put(newProperty, newProperty);
	}
      } catch (Exception e) {
	// if we catch an exception, keep going for the rest.
	e.printStackTrace();
      }
    }

    return returnValue;
  }

  /**
   * This matches the FolderInfo's display filters.
   */
  public void matchFilters() {
    if (! filtersMatched ) {
      // match the given filters for the FolderInfo.
      
      matchingFilters = doFilterMatch();

      filtersMatched = true;
    }
  }

  /**
   * This returns a list of matching filters, or null if there are no
   * filters.
   */
  private DisplayFilter[] doFilterMatch() {
    MessageFilter[] folderFilters = getFolderInfo().getDisplayFilters();
    if (folderFilters != null) {
      Vector tmpMatches = new Vector();
      for (int i = 0; i < folderFilters.length; i++) {
	if (folderFilters[i].getSearchTerm().match(getMessageInfo().getMessage()))
	  tmpMatches.add(folderFilters[i].getAction());
      }
      
      DisplayFilter[] newMatchingFilters = new DisplayFilter[tmpMatches.size()];
      for (int i = 0; i < tmpMatches.size(); i++) {
	newMatchingFilters[i] = (DisplayFilter) tmpMatches.elementAt(i);
      }

      return newMatchingFilters;
    }

    return null;
  }
  
  /**
   * Runs server filters on this Message.
   */
  public void runBackendFilters() {
    MessageInfo info = getMessageInfo();
    if (info != null)
      info.runBackendFilters();
  }

  /**
   * Attempts to decrypt the given message.
   */
  public void decryptMessage() {
    MessageInfo info = getMessageInfo();
    if (info != null) {
      if (info.hasEncryption()) {

	MessageCryptoInfo cInfo = info.getCryptoInfo();

	try {
	  if (cInfo != null && cInfo.isEncrypted()) {
	    
	    java.security.Key key = getDefaultProfile().getEncryptionKey(cInfo.getEncryptionType());

	    if (key != null) {
	      try {
		cInfo.decryptMessage(key, true);
	      } catch (Exception e) {
		// ignore here.
	      } 
	    }
	    
	    if (key == null) {
	      try {
		key = selectPrivateKey(Pooka.getProperty("Pooka.crypto.privateKey.forDecrypt", "Select key to decrypt this message."), cInfo.getEncryptionType());
	      } catch (Exception e) {
		showError(Pooka.getProperty("Error.encryption.keystoreException", "Error selecting key:  "), e);
	      }
	    }
	    // check the encryption
	    
	    if (key != null) {
	      try {
		cInfo.decryptMessage(key, true);
	      } catch (Exception e) {
		showError(Pooka.getProperty("Error.encryption.decryptionFailed", "Decryption Failed:  "), e);
	      }
	      
	      MessageUI ui = getMessageUI();
	      if (ui != null) {
		
		CryptoStatusDisplay csd = ui.getCryptoStatusDisplay();
		
		if (csd != null)
		  csd.cryptoUpdated(cInfo);
		try {
		  ui.refreshDisplay();
		} catch (MessagingException me) {
		  showError(Pooka.getProperty("Error.encryption.decryptionFailed", "Decryption Failed:  "), me);
		}
	      }
	    }
	  }
	} catch (MessagingException me) {
	  showError(Pooka.getProperty("Error.encryption.decryptionFailed", "Decryption Failed:  "), me);
	}
      }
    }
  }
  
  /**
   * Attempts to check the signature on the given message.
   */
  public void checkSignature() {
    MessageInfo info = getMessageInfo();
    if (info != null) {
      MessageCryptoInfo cInfo = info.getCryptoInfo();
      try {
	if (cInfo != null && cInfo.isSigned()) {
	  CryptoStatusDisplay csd = null;
	  
	  String fromString = "";
	  Address[] fromAddr = getMessageInfo().getMessage().getFrom();
	  if (fromAddr != null && fromAddr.length > 0) {
	    fromString = ((javax.mail.internet.InternetAddress)fromAddr[0]).getAddress();
	  }
	  java.security.Key[] keys = Pooka.getCryptoManager().getPublicKeys(fromString, cInfo.getEncryptionType());

	  if (keys == null || keys.length < 1) {
	    java.security.Key newKey = selectPublicKey(Pooka.getProperty("Pooka.crypto.publicKey.forSig", "Select key for verifying the signature on this message."), cInfo.getEncryptionType());
	    keys = new java.security.Key[] { newKey };
	  }
	  
	  if (keys != null) {
	    boolean checked = false;
	    for (int i = 0; (! checked) && i < keys.length; i++) {
	      checked = cInfo.checkSignature(keys[i], true);
	    }
	  }
	  MessageUI ui = getMessageUI();
	  if (ui != null) {
	    csd = ui.getCryptoStatusDisplay();
	  }
	  
	  if (csd != null)
	    csd.cryptoUpdated(cInfo);
	 
	}
      } catch (Exception e) {
	showError(Pooka.getProperty("Error.encryption.signatureValidationFailed", "Signature Validation Failed"), e);
      }
    }
  }

  /**
   * Imports the keys on this message.
   */
  public void importKeys() {
    MessageInfo info = getMessageInfo();
    if (info != null) {
      MessageCryptoInfo cInfo = info.getCryptoInfo();
      if (cInfo != null) {
	try {
	  java.security.Key[] newKeys = cInfo.extractKeys();
	  if (newKeys != null && newKeys.length > 0) {
	    // check to see if these match our current keys.
	    String changedMessage = Pooka.getProperty("Pooka.crypto.importKeysMessage", "Import the following keys:") + "\n";
	    
	    for (int i = 0; i < newKeys.length; i++) {
	      // FIXME check to see if changed.
	      if (newKeys[i] instanceof net.suberic.crypto.EncryptionKey)
		changedMessage = changedMessage + ((net.suberic.crypto.EncryptionKey)newKeys[i]).getDisplayAlias() + "\n";
	      else
		changedMessage = changedMessage + newKeys[i].toString() + "\n";
	    }
	    
	    int doImport = JOptionPane.NO_OPTION;

	    if (getMessageUI() != null)
	      doImport = getMessageUI().showConfirmDialog(changedMessage, Pooka.getProperty("Pooka.crypto.importKeysTitle", "Import keys"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
	    else
	      doImport = Pooka.getUIFactory().showConfirmDialog(changedMessage, Pooka.getProperty("Pooka.crypto.importKeysTitle", "Import keys"), JOptionPane.YES_NO_OPTION);

	    if (doImport == JOptionPane.YES_OPTION) {
	      for (int i = 0; i < newKeys.length; i++) {
		if (newKeys[i] instanceof net.suberic.crypto.EncryptionKey) {
		  net.suberic.crypto.EncryptionKey eKey = (net.suberic.crypto.EncryptionKey) newKeys[i];
		  Pooka.getCryptoManager().addPublicKey(eKey.getDisplayAlias(), eKey, eKey.getEncryptionUtils().getType());
		}
	      }
	    }
	    
	  } else {
	    if (getMessageUI() != null)
	      getMessageUI().showMessageDialog("No keys found.", "No keys found");
	    else
	      Pooka.getUIFactory().showMessage("No keys found.", "No keys found");
	  }
	} catch (Exception e) {
	  showError(Pooka.getProperty("Error.encryption.keyExtractionFailed", "Failed to extract keys."), e);
	}
      }
    }
  }

  /**
   * Opens up a dialog to select a public key.
   */
  public java.security.Key selectPublicKey(String flavorText, String type) throws java.security.GeneralSecurityException {
    return CryptoKeySelector.selectPublicKey(flavorText, type);
  }

  /**
   * Opens up a dialog to select a private key.
   */
  public java.security.Key selectPrivateKey(String flavorText, String type) throws java.security.GeneralSecurityException {
    return CryptoKeySelector.selectPrivateKey(flavorText, type);
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
    openWindow(getDefaultDisplayMode(), HEADERS_DEFAULT);
  }

  /**
   * this opens a MessageUI for this Message.
   */
  public void openWindow(int newDisplayMode, int newHeaderMode) {

    try {
      if (getMessageUI() == null) {
	setDisplayMode(newDisplayMode);
	setHeaderMode(newHeaderMode);
	
	MessageUI newUI = Pooka.getUIFactory().createMessageUI(this);
	setMessageUI(newUI);
      } else if (newDisplayMode != getDisplayMode() || newHeaderMode != getHeaderMode()) {
	setDisplayMode(newDisplayMode);
	setHeaderMode(newHeaderMode);
	getMessageUI().refreshDisplay();
      }
      
      SwingUtilities.invokeLater(new Runnable() {
	  public void run() {
	    getMessageUI().openMessageUI();
	  }
	});
      
      getMessageInfo().setSeen(true);
    } catch (MessagingException me) {
      showError(Pooka.getProperty("error.Message.openWindow", "Error opening window:  "), me);
    }
  }
  
  /**
   * This creates a NewMessageUI for this MessageProxy and then opens an
   * editor for it.  If 
   */
  public void openWindowAsNew(boolean removeProxy) {
    try {
      // first create the NewMessageProxy from this MessageProxy.
      Message newMessage = new MimeMessage((MimeMessage)getMessageInfo().getMessage());
      NewMessageInfo nmi = new NewMessageInfo(newMessage);
      NewMessageProxy nmp = new NewMessageProxy(nmi);
      
      final MessageUI nmu = Pooka.getUIFactory().createMessageUI(nmp, getMessageUI());

      nmp.matchUserProfile();

      SwingUtilities.invokeLater(new Runnable() {
	  public void run() {
	    nmu.openMessageUI();
	  }
	});
      

      if (removeProxy) 
	deleteMessage();
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
  
  /**
   * Copies the Message to the target Folder.
   */
  public void copyMessage(FolderInfo targetFolder) {
    try {
      messageInfo.copyMessage(targetFolder);
    } catch (MessagingException me) {
      showError( Pooka.getProperty("error.Message.CopyErrorMessage", "Error:  could not copy messages to folder:  ") + targetFolder.toString() +"\n", me);
      if (Pooka.isDebug())
	me.printStackTrace();
    }
  }
  
  /**
   * Creates a NewMessageInfo & Proxy for a message which is a reply
   * to the current message.  Opens a NewMessageUI for said Proxy.
   */
  private void replyToMessage(boolean replyAll, boolean withAttachments) {
    if (getMessageUI() != null)
      getMessageUI().setBusy(true);
    
    FolderDisplayUI fw = getFolderDisplayUI();
    if (fw != null)
      fw.setBusy(true);;
    try {
      NewMessageProxy nmp = new NewMessageProxy(getMessageInfo().populateReply(replyAll, withAttachments));
      final MessageUI nmui = Pooka.getUIFactory().createMessageUI(nmp, this.getMessageUI());

      // if this has a messageui up, then make the reply 
      SwingUtilities.invokeLater(new Runnable() {
	  public void run() {
	    nmui.openMessageUI();
	  }
	});
      
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
  
  /**
   * Creates a NewMessageInfo & Proxy for a message which is a forward
   * to the current message.  Opens a NewMessageUI for said Proxy.
   */
  private void forwardMessage(boolean withAttachments, int method) {
    if (getMessageUI() != null)
      getMessageUI().setBusy(true);
    FolderDisplayUI fw = getFolderDisplayUI();
    if (fw != null)
      fw.setBusy(true);;
    try {
      NewMessageProxy nmp = new NewMessageProxy(getMessageInfo().populateForward(withAttachments, method));
      final MessageUI nmui = Pooka.getUIFactory().createMessageUI(nmp, getMessageUI());
      SwingUtilities.invokeLater(new Runnable() {
	public void run() {
	  nmui.openMessageUI();
	}
	});
      
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
   * Bounces the message.  Pops up a dialog to request the address(es)
   * to which to bounce the message.
   */
  public void bounceMessage() {
    
    String addressString = null;
    Address[] addresses = null;

    boolean resolved = false;
    while (! resolved) {
      if (getMessageUI() != null)
	addressString = getMessageUI().showInputDialog(Pooka.getProperty("message.bounceMessage.addresses", "Bounce to address(es):"), Pooka.getProperty("message.bounceMessage.title", "Bounce to addresses"));
      else if (getMessageInfo().getFolderInfo().getFolderDisplayUI() != null) {
	addressString = getMessageInfo().getFolderInfo().getFolderDisplayUI().showInputDialog(Pooka.getProperty("message.bounceMessage.addresses", "Bounce to address(es):"), Pooka.getProperty("message.bounceMessage.title", "Bounce to addresses"));
      } else {
	addressString = Pooka.getUIFactory().showInputDialog(Pooka.getProperty("message.bounceMessage.addresses", "Bounce to address(es):"), Pooka.getProperty("message.bounceMessage.title", "Bounce to addresses"));
      }
      if (addressString == null) {
	resolved = true;
      } else {
	try {
	  addresses = javax.mail.internet.InternetAddress.parse(addressString, false);
	  resolved = true;
	} catch (MessagingException me) {
	  showError(Pooka.getProperty("error.bounceMessage.addresses", "Error parsing address entry."), me);
	}
      }
    }
    
    if (addresses != null) {
      bounceMessage(addresses, false);
    }
  }

  /**
   * Bounces the message.  Passes on the actual work of bouncing the
   * message to the underlying MessageInfo, but handles the job of
   * showing any errors, if they happen.
   */
  public void bounceMessage(Address[] addresses, boolean deleteOnSuccess) {
    final Address[] final_addresses = addresses;
    final boolean final_delete = deleteOnSuccess;

    ActionThread folderThread = getMessageInfo().getFolderInfo().getFolderThread();
    folderThread.addToQueue(new javax.swing.AbstractAction() {
	public void actionPerformed(java.awt.event.ActionEvent ae) {
	  try {
	    getMessageInfo().bounceMessage(final_addresses);
	    if (final_delete)
	      deleteMessage(false);

	  } catch (javax.mail.MessagingException me) {
	    final MessagingException final_me = me;
	    SwingUtilities.invokeLater(new Runnable() {
		public void run() { 
		  showError(Pooka.getProperty("error.bounceMessage.error", "Error bouncing Message"), final_me);
		}
	      });
	  }
	}
      }, new java.awt.event.ActionEvent(this, 0, "message-bounce"));
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
	final boolean finalAutoExpunge = autoExpunge;
	try {
	  SwingUtilities.invokeAndWait(new Runnable() {
	      public void run() {
		try {
		  if (getMessageUI().showConfirmDialog(Pooka.getProperty("error.Messsage.DeleteNoTrashFolder", "The Trash Folder configured is not available.\nDelete messages anyway?"), Pooka.getProperty("error.Messsage.DeleteNoTrashFolder.title", "Trash Folder Unavailable"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
		    getMessageInfo().remove(finalAutoExpunge);
		    close();
		  }
		} catch (MessagingException mex) {
		  showError(Pooka.getProperty("error.Message.DeleteErrorMessage", "Error:  could not delete message.") +"\n", mex);
		}
	      }
	    });
	} catch (Exception e) {
	}
      } else {
	final Exception mEx = me;
	SwingUtilities.invokeLater(new Runnable() {
	    public void run() {
	      showError(Pooka.getProperty("error.Message.DeleteErrorMessage", "Error:  could not delete message.") +"\n", mEx);
	    }
	  });
      }
    }
  }
  
  /**
   * Opens up a dialog to save the message to a file.
   */
  public void saveMessageToFile() {
    JFileChooser saveChooser;
    String currentDirectoryPath = Pooka.getProperty("Pooka.tmp.currentDirectory", "");
    if (currentDirectoryPath == "")
      saveChooser = new JFileChooser();
    else
      saveChooser = new JFileChooser(currentDirectoryPath);
    
    int saveConfirm = saveChooser.showSaveDialog(Pooka.getMainPanel().getContentPanel().getUIComponent());
    Pooka.getResources().setProperty("Pooka.tmp.currentDirectory", saveChooser.getCurrentDirectory().getPath(), true);

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
      getMessageUI().showError(message, ex);
    else
      Pooka.getUIFactory().showError(message, ex);
  }
  
  /**
   * Closes this MessageProxy. 
   *
   * For this implementation, the only result is that the MessageUI,
   * if any, is closed.
   */
  public void close() {
    Runnable runMe = new Runnable() {
	public void run() {
	  if (getMessageUI() != null)
	    getMessageUI().closeMessageUI();
	}
      };
    if (SwingUtilities.isEventDispatchThread())
      runMe.run();
    else
      SwingUtilities.invokeLater(runMe);
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
  public void printMessage(Object source) {
    // starts on FolderThread.

    // Load up the message into the MessagePrinter, just to be safe.

    try {
      MessagePrinter mp = new MessagePrinter(getMessageInfo());
      mp.createTextPane();
      
      // now switch over to the AWTEventThread.
      
      final MessagePrinter messagePrinter = mp;
      
      final Object final_source = source;
      
      SwingUtilities.invokeLater(new Runnable() {
	  public void run() {
	    
	    // Set the document type
	    final DocFlavor messageFormat = DocFlavor.SERVICE_FORMATTED.PRINTABLE;
	    
	    // bring up a dialog.
	    PrintService[] services = PrintServiceLookup.lookupPrintServices(messageFormat, null);
	    
	    PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
	    PrintService service =  ServiceUI.printDialog(null, 50, 50,
							  services, 
							  PrintServiceLookup.lookupDefaultPrintService(), 
							  messageFormat,
							  attributes);
	    
	    if (service != null) {
	      final PrintRequestAttributeSet final_attributes = attributes;
	      
	      final Doc myDoc = new SimpleDoc(messagePrinter, messageFormat, null); 
	      
	      final DocPrintJob final_job = service.createPrintJob();
	      
	      final MessagePrinterDisplay mpd = new MessagePrinterDisplay(messagePrinter, final_job, final_source);
	      
	      final_job.addPrintJobListener(mpd);

	      mpd.show();

	      Runnable runMe = new Runnable() {
		  public void run() {
		    try {
		      final_job.print(myDoc, final_attributes); 
		    } catch (PrintException pe) {
		      mpd.showError("Failed to print", pe);
		    } 	
		  }
		};
	      
	      Thread messagePrintThread = new Thread(runMe);
	      messagePrintThread.start();
	    }
	  }
	});
    } catch (MessagingException e) {
      showError("error printing", e);
    }
  }
  
  /**
   * This creates and shows a PopupMenu for this component.  
   */
  public void showPopupMenu(JComponent component, MouseEvent e) {
    ConfigurablePopupMenu popupMenu = new ConfigurablePopupMenu();
    FolderInfo fi = getMessageInfo().getFolderInfo();
    if ( fi != null ) {
      if (fi instanceof net.suberic.pooka.cache.CachingFolderInfo) {
      popupMenu.configureComponent("MessageProxy.cachingPopupMenu", Pooka.getResources());	
      } else if (fi.isOutboxFolder()) {
	popupMenu.configureComponent("NewMessageProxy.popupMenu", Pooka.getResources());	
      } else {
	popupMenu.configureComponent("MessageProxy.popupMenu", Pooka.getResources());	
      }
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
  public HashMap getTableInfo() {
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
  
  public void setTableInfo(HashMap newValue) {
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
   * Returns whether or not this MessageProxy should refresh its display
   * information.
   */
  public boolean needsRefresh() {
    return refresh;
  }

  /**
   * Notifies this MessageProxy whether or not this MessageProxy needs to 
   * refresh its display information.
   */
  public void setRefresh(boolean newValue) {
    refresh = newValue;
  }

  /**
   * This sets the loaded value for the MessageProxy to false.   This 
   * should be called only if the TableInfo of the Message has been 
   * changed and needs to be reloaded.
   *
   * Note that this also sets the filtersMatched property to false.
   */
  public void unloadTableInfo() {
    loaded=false;
    filtersMatched=false;
  }
  
  /**
   * This flags the message that we should check again to see which 
   * filters it matches.
   */
  public void clearMatchedFilters() {
    filtersMatched = false;
  }

  /**
   * Shows whether or not we need to rematch the filters.
   */
  public boolean matchedFilters() {
    return filtersMatched;
  }
  
  public MessageUI getMessageUI() {
    return msgWindow;
  }
  
  public void setMessageUI(MessageUI newValue) {
    msgWindow = newValue;
    if (newValue == null) {
      setHeaderMode(HEADERS_DEFAULT);
  
      setDisplayMode(getDefaultDisplayMode());
    }
  }
  
  public MessageInfo getMessageInfo() {
    return messageInfo;
  }

  /**
   * Returns the current displayMode.  Valid values are the following
   * constants:
   *   RFC_822, TEXT_ONLY, TEXT_PREFERRED, HTML_PREFERRED, HTML_ONLY
   */
  public int getDisplayMode() {
    return displayMode;
  }
  /**
   * Sets the displayMode.  Note that you will still need to calls something
   * like ReadMessageDisplayPanel.resetEditorText() in order to have the
   * change take effect.
   */
  public void setDisplayMode(int newDisplayMode) {
    displayMode = newDisplayMode;
  }

  /**
   * Returns the default display mode.
   */
  public static int getDefaultDisplayMode() {
    if (Pooka.getProperty("Pooka.displayHtmlAsDefault", "false").equalsIgnoreCase("true"))
      return HTML_PREFERRED;
    else
      return TEXT_PREFERRED;
  }

  /**
   * Returns the current headerMode.  Valid values are the following
   * constants:
   *   HEADERS_DEFAULT, HEADERS_FULL, and RFC822_STYLE.
   */
  public int getHeaderMode() {
    return headerMode;
  }
  /**
   * Sets the headerMode.  Note that you will still need to calls something
   * like ReadMessageDisplayPanel.resetEditorText() in order to have the
   * change take effect.
   */
  public void setHeaderMode(int newHeaderMode) {
    headerMode = newHeaderMode;
  }

  public FolderDisplayUI getFolderDisplayUI() {
    FolderInfo fi = getMessageInfo().getFolderInfo();
    if (fi != null)
      return fi.getFolderDisplayUI();
    else
      return null;
    
  }
  
  /**
   * Returns the matching filters for this MessageProxy.
   */
  public net.suberic.pooka.gui.filter.DisplayFilter[] getMatchingFilters() {
    if (filtersMatched)
      return matchingFilters;
    else {
      if (isLoaded()) {
	matchFilters();
	return matchingFilters;
      } else {
	return new DisplayFilter[0];
      }
    }
  }
  
  public Action getAction(String name) {
    if (defaultActions == null) {
      getActions();
    }
    return (Action)commands.get(name);
  }
  
  public Action[] getActions() { 
    if (defaultActions == null) {
      ActionThread folderThread = messageInfo.getFolderInfo().getFolderThread();
    
      defaultActions = new Action[] {
	new ActionWrapper(new OpenAction(), folderThread),
	new ActionWrapper(new OpenDefaultDisplayAction(), folderThread),
	new ActionWrapper(new OpenFullDisplayAction(), folderThread),
	new ActionWrapper(new OpenRawDisplayAction(), folderThread),
	new ActionWrapper(new OpenTextDisplayAction(), folderThread),
	new ActionWrapper(new OpenHtmlDisplayAction(), folderThread),
	new ActionWrapper(new DefaultOpenAction(), folderThread),
	new ActionWrapper(new MoveAction(), folderThread),
	new ActionWrapper(new CopyAction(), folderThread),
	new ActionWrapper(new ReplyAction(), folderThread),
	new ActionWrapper(new ReplyAllAction(), folderThread),
	new ActionWrapper(new ReplyWithAttachmentsAction(), folderThread),
	new ActionWrapper(new ReplyAllWithAttachmentsAction(), folderThread),
	new ActionWrapper(new ForwardAction(), folderThread),
	new ActionWrapper(new ForwardWithAttachmentsAction(), folderThread),
	new ActionWrapper(new ForwardAsInlineAction(), folderThread),
	new ActionWrapper(new ForwardAsAttachmentAction(), folderThread),
	new ActionWrapper(new ForwardQuotedAction(), folderThread),
	new BounceAction(),
	new ActionWrapper(new DeleteAction(), folderThread),
	new ActionWrapper(new PrintAction(), folderThread),
	new ActionWrapper(new SaveMessageAction(), folderThread),
	new ActionWrapper(new CacheMessageAction(), folderThread),
	new ActionWrapper(new SaveAddressAction(), folderThread),
	new ActionWrapper(new OpenAsNewAction(), folderThread),
	new ActionWrapper(new MessageFilterAction(), folderThread),
	new ActionWrapper(new SpamAction(), folderThread),
	new ActionWrapper(new DecryptAction(), folderThread),
	new ActionWrapper(new CheckSignatureAction(), folderThread),
	new ActionWrapper(new ImportKeysAction(), folderThread),
	new ActionWrapper(new SignatureStatusAction(), folderThread),
	new ActionWrapper(new EncryptionStatusAction(), folderThread)
      };

      commands = new Hashtable();
      
      for (int i = 0; i < defaultActions.length; i++) {
	Action a = defaultActions[i];
	commands.put(a.getValue(Action.NAME), a);
      }
      
    }
    return defaultActions;
  }
  
  public class OpenAction extends AbstractAction {
    protected int displayModeValue = 999;
    protected int headerModeValue = 999;

    public int getDisplayModeValue() {
      return displayModeValue;
    }
    public int getHeaderModeValue() {
      return headerModeValue;
    }
    protected String cmd = "";
    public String getCommand() {
      // i should probably actually get this using getValue()...
      return cmd;
    }
    public MessageProxy getMessageProxy() {
      return MessageProxy.this;
    }
    OpenAction() {
      super("file-open");
      cmd = "file-open";
    }

    OpenAction(String id) {
      super(id);
      cmd = id;
    }

    public void actionPerformed(java.awt.event.ActionEvent e) {
      
      FolderDisplayUI fw = getFolderDisplayUI();
      if (fw != null)
	fw.setBusy(true);

      int newDisplayMode;
      if (displayModeValue != 999)
	newDisplayMode = displayModeValue;
      else
	newDisplayMode = getDisplayMode();

      int newHeaderMode;
      if (headerModeValue != 999)
	newHeaderMode = headerModeValue;
      else
	newHeaderMode = getHeaderMode();

      openWindow(newDisplayMode, newHeaderMode);

      if (fw != null)
	fw.setBusy(false);
    }
  }

  public class OpenDefaultDisplayAction extends OpenAction {

    OpenDefaultDisplayAction() {
      super("file-open-defaultdisplay");
      headerModeValue = HEADERS_DEFAULT;
    }
  }

  public class OpenFullDisplayAction extends OpenAction {

    OpenFullDisplayAction() {
      super("file-open-fulldisplay");
      headerModeValue = HEADERS_FULL;
    }
  }

  public class OpenRawDisplayAction extends OpenAction {
    OpenRawDisplayAction() {
      super("file-open-rawdisplay");
      displayModeValue = RFC_822;
    }
  }
  
  public class OpenTextDisplayAction extends OpenAction {
    OpenTextDisplayAction() {
      super("file-open-textdisplay");
      displayModeValue = TEXT_ONLY;
    }
  }
  
  public class OpenHtmlDisplayAction extends OpenAction {
    OpenHtmlDisplayAction() {
      super("file-open-htmldisplay");
      displayModeValue = HTML_ONLY;
    }
  }
  
  public class DefaultOpenAction extends AbstractAction {
    DefaultOpenAction() {
      super("file-default-open");
    }
    
    public void actionPerformed(java.awt.event.ActionEvent e) {
      
      FolderDisplayUI fw = getFolderDisplayUI();
      if (fw != null)
	fw.setBusy(true);;
      openWindow();
      Pooka.getUIFactory().doDefaultOpen(MessageProxy.this);
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
  
  public class CopyAction extends net.suberic.util.DynamicAbstractAction {
    CopyAction() {
      super("message-copy");
    }
    
    public void actionPerformed(java.awt.event.ActionEvent e) {
      if (getMessageUI() != null)
	getMessageUI().setBusy(true);
      FolderDisplayUI fw = getFolderDisplayUI();
      if (fw != null)
	fw.setBusy(true);;
      copyMessage((FolderInfo)getValue("target"));
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
  
  
  public class BounceAction extends AbstractAction {
    
    BounceAction() {
      super("message-bounce");
    }
    
    public void actionPerformed(ActionEvent e) {
      if (getMessageUI() != null)
	getMessageUI().setBusy(true);

      bounceMessage();

      if (getMessageUI() != null)
	getMessageUI().setBusy(false);
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

      printMessage(e.getSource());
      
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
	showError(Pooka.getProperty("Pooka.cache.errorCachingMessage", "Error caching message"), me);
      }
      
      if (fw != null)
	fw.setBusy(false);
      if (getMessageUI() != null)
	getMessageUI().setBusy(false);
    }
  }

  public class SaveAddressAction extends AbstractAction {
    SaveAddressAction() {
      super("message-save-address");
    }
    
    public void actionPerformed(ActionEvent e) {
      if (getMessageUI() != null)
	getMessageUI().setBusy(true);
      FolderDisplayUI fw = getFolderDisplayUI();
      if (fw != null)
	fw.setBusy(true);;
	
      try {
	UserProfile defaultProfile = getDefaultProfile();
	AddressBook book = null;

	if (defaultProfile != null) {
	  book = defaultProfile.getAddressBook();
	}

	if (book == null) {	  
	  // get the default Address Book.
	  book = Pooka.getAddressBookManager().getDefault();
	}
	if (book != null)
	  getMessageInfo().addAddress(book, true);
	else {
	  SwingUtilities.invokeLater(new Runnable() {
	      public void run() {
		getMessageUI().showError(Pooka.getProperty("error.noAddressBook", "No Address Book set as default."));
	      }
	    });
	}
      } catch (MessagingException me) {
	showError(Pooka.getProperty("error.savingAddress", "Error saving Address"), me);
      }
      
      if (fw != null)
	fw.setBusy(false);
      if (getMessageUI() != null)
	getMessageUI().setBusy(false);
    }
  }

  
  public class OpenAsNewAction extends AbstractAction {
    OpenAsNewAction() {
      super("message-open-as-new");
    }
    
    public void actionPerformed(ActionEvent e) {
      if (getMessageUI() != null)
	getMessageUI().setBusy(true);
      FolderDisplayUI fw = getFolderDisplayUI();
      if (fw != null)
	fw.setBusy(true);;
	
      openWindowAsNew(true);
      
      if (fw != null)
	fw.setBusy(false);
      if (getMessageUI() != null)
	getMessageUI().setBusy(false);
    }
  }

  public class MessageFilterAction extends AbstractAction {
    MessageFilterAction() {
      super("message-filter");
    }
    
    public void actionPerformed(ActionEvent e) {
      if (getMessageUI() != null)
	getMessageUI().setBusy(true);
      FolderDisplayUI fw = getFolderDisplayUI();
      if (fw != null)
	fw.setBusy(true);;
	
      runBackendFilters();
      
      if (fw != null)
	fw.setBusy(false);
      if (getMessageUI() != null)
	getMessageUI().setBusy(false);
    }
  }

  public class SpamAction extends AbstractAction {
    SpamAction() {
      super("message-spam");
    }
    
    public void actionPerformed(ActionEvent e) {
      if (getMessageUI() != null)
	getMessageUI().setBusy(true);
      FolderDisplayUI fw = getFolderDisplayUI();
      if (fw != null)
	fw.setBusy(true);;
	
      MessageInfo info = getMessageInfo();
      if (info != null) {
	info.runSpamAction();
      }
      
      if (fw != null)
	fw.setBusy(false);
      if (getMessageUI() != null)
	getMessageUI().setBusy(false);
    }
  }

  public class DecryptAction extends AbstractAction {
    DecryptAction() {
      super("message-decrypt");
    }
    
    public void actionPerformed(ActionEvent e) {
      if (getMessageUI() != null)
	getMessageUI().setBusy(true);
      FolderDisplayUI fw = getFolderDisplayUI();
      if (fw != null)
	fw.setBusy(true);;
	
      decryptMessage();
      
      if (fw != null)
	fw.setBusy(false);
      if (getMessageUI() != null)
	getMessageUI().setBusy(false);
    }
  }

  public class CheckSignatureAction extends AbstractAction {
    CheckSignatureAction() {
      super("message-check-signature");
    }
    
    public void actionPerformed(ActionEvent e) {
      if (getMessageUI() != null)
	getMessageUI().setBusy(true);
      FolderDisplayUI fw = getFolderDisplayUI();
      if (fw != null)
	fw.setBusy(true);;
	
      checkSignature();
      
      if (fw != null)
	fw.setBusy(false);
      if (getMessageUI() != null)
	getMessageUI().setBusy(false);
    }
  }

  public class ImportKeysAction extends AbstractAction {
    ImportKeysAction() {
      super("message-import-keys");
    }
    
    public void actionPerformed(ActionEvent e) {
      if (getMessageUI() != null)
	getMessageUI().setBusy(true);
      FolderDisplayUI fw = getFolderDisplayUI();
      if (fw != null)
	fw.setBusy(true);;
	
      importKeys();
      
      if (fw != null)
	fw.setBusy(false);
      if (getMessageUI() != null)
	getMessageUI().setBusy(false);
    }
  }

  public class EncryptionStatusAction extends AbstractAction {
    EncryptionStatusAction() {
      super("message-encryption-status");
    }
    
    public void actionPerformed(ActionEvent e) {
      if (getMessageUI() != null)
	getMessageUI().setBusy(true);
      FolderDisplayUI fw = getFolderDisplayUI();
      if (fw != null)
	fw.setBusy(true);;

      if (getMessageUI() != null)
	getMessageUI().showMessageDialog("(Encryption Status)", "Encryption Status");
      else
	Pooka.getUIFactory().showMessage("(Encryption Status)", "Encryption Status");

      if (fw != null)
	fw.setBusy(false);
      if (getMessageUI() != null)
	getMessageUI().setBusy(false);
    }
  }

  public class SignatureStatusAction extends AbstractAction {
    SignatureStatusAction() {
      super("message-signature-status");
    }
    
    public void actionPerformed(ActionEvent e) {
      if (getMessageUI() != null)
	getMessageUI().setBusy(true);
      FolderDisplayUI fw = getFolderDisplayUI();
      if (fw != null)
	fw.setBusy(true);;
	
      if (getMessageUI() != null)
	getMessageUI().showMessageDialog("(Signature Status)", "Signature Status");
      else
	Pooka.getUIFactory().showMessage("(Signature Status)", "Signature Status");

      if (fw != null)
	fw.setBusy(false);
      if (getMessageUI() != null)
	getMessageUI().setBusy(false);
    }
  }
}







