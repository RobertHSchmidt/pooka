package net.suberic.pooka.gui;
import net.suberic.pooka.Pooka;
import net.suberic.pooka.UserProfile;
import net.suberic.util.gui.ConfigurableToolbar;
import javax.mail.*;
import javax.mail.internet.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.TextAction;
import java.util.*;
import javax.swing.text.JTextComponent;
import javax.swing.event.*;
import java.io.File;

public class MessageWindow extends JInternalFrame {

    public static int HEADERS_DEFAULT = 0;
    public static int HEADERS_FULL = 1;

    MessageProxy msg;
    JSplitPane splitPane = null;
    JComponent headerPanel = null;
    JComponent bodyPanel = null;
    int headerStyle = MessageWindow.HEADERS_DEFAULT;
    boolean editable = false;
    boolean showFullHeaders = false;
    boolean modified = false;
    Hashtable inputTable = null;
    JEditorPane editorPane = null;
    ConfigurableToolbar toolbar;
    boolean hasAttachment = false;

    /**
     * Creates a MessageWindow from the given Message.
     */

    public MessageWindow(MessageProxy newMsgProxy, boolean isEditable) {
	super(Pooka.getProperty("Pooka.messageWindow.messageTitle.newMessage", "New Message"), true, true, true, true);

	editable = isEditable;
	if (isEditable()) 
	    inputTable = new Hashtable();

	this.getContentPane().setLayout(new BorderLayout());

	msg=newMsgProxy;
	if (editable) {
	    this.setModified(true);
	    this.setTitle(Pooka.getProperty("Pooka.messageWindow.messageTitle.newMessage", "New Message"));
	    toolbar = new ConfigurableToolbar("NewMessageWindowToolbar", Pooka.getResources());
	} else {
	    try {
		this.setTitle(msg.getMessage().getSubject());
	    } catch (MessagingException me) {
		this.setTitle(Pooka.getProperty("Pooka.messageWindow.messageTitle.noSubject", "<no subject>"));
	    }
	    toolbar = new ConfigurableToolbar("MessageWindowToolbar", Pooka.getResources());
	}
	
	toolbar.setActive(this.getActions());
	this.getContentPane().add("North", toolbar);

	splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
	
	splitPane.setTopComponent(createHeaderPanel(msg));
	splitPane.setBottomComponent(createBodyPanel(msg));
	this.getContentPane().add("Center", splitPane);
	
	this.setSize(Integer.parseInt(Pooka.getProperty("MessageWindow.hsize", "300")), Integer.parseInt(Pooka.getProperty("MessageWindow.vsize", "200")));
	
	newMsgProxy.setMessageWindow(this);

	this.addInternalFrameListener(new InternalFrameAdapter() {
		public void internalFrameClosed(InternalFrameEvent e) {
		    if (getMessageProxy().getMessageWindow() == MessageWindow.this)
			getMessageProxy().setMessageWindow(null);
		}
	    });
	
    }

    /**
     * Create a New Message Window


    public MessageWindow(Session thisSession) {
	super(Pooka.getProperty("Pooka.messageWindow.messageTitle.newMessage", "New Message"), true, true, true, true);
	
	editable=true;
	
	try {
	} catch (MessagingException me) {
	}

	splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
	
	splitPane.setTopComponent(createHeaderPanel(msg));
	splitPane.setBottomComponent(createBodyPanel(msg));
	this.getContentPane().add(splitPane);
    }
	
    */		 


    public void closeMessageWindow() {
	
	if (isModified()) {
	    int saveDraft = showConfirmDialog(Pooka.getProperty("error.saveDraft.message", "This message has unsaved changes.  Would you like to save a draft copy?"), Pooka.getProperty("error.saveDraft.title", "Save Draft"), JOptionPane.YES_NO_CANCEL_OPTION);
	    switch (saveDraft) {
	    case JOptionPane.YES_OPTION:
		//this.saveDraft();
	    case JOptionPane.NO_OPTION:
		try {
		    this.setClosed(true);
		} catch (java.beans.PropertyVetoException e) {
		}
	    default:
		return;
	    }
	} else {
	    try {
		this.setClosed(true);
	    } catch (java.beans.PropertyVetoException e) {
	    }
	}
    }

    public Container createHeaderPanel(MessageProxy aMsg) {
	/*
	 * This is interesting.  We need to deal with several possibilities:
	 *
	 * 1)  Simple (non-editable) with just basic headers
	 * 2)  Simple with full headers
	 * 3)  Simple with attachments and both header sets
	 * 4)  Editable with just UserProfile
	 * 5)  Editable with normal headers
	 * 6)  Editable with full headers
	 */

	if (isEditable()) {
	    return createHeaderInputPanel(aMsg, inputTable);
	} else if (aMsg.getMessage() instanceof MimeMessage) {
	    MimeMessage mMsg = (MimeMessage)aMsg.getMessage();

	    boolean multiTest = false;
	    
	    try {
		multiTest = (mMsg.getContent() instanceof Multipart);
	    } catch (Exception e) {
	    }

	    if (multiTest) {
		JSplitPane hdrSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		hdrSplitPane.setTopComponent(createHeaderTextField(mMsg));
		hdrSplitPane.setBottomComponent(createAttachmentPanel(mMsg));
		return hdrSplitPane;
	    } else {
		return createHeaderTextField(mMsg);
	    }
	}

	//shouldn't happen.
	return null;
    }
	

    public JTextArea createHeaderTextField(MimeMessage mMsg) {
	JTextArea headerArea = new JTextArea();

	if (showFullHeaders()) {
	}
	else {
	    StringTokenizer tokens = new StringTokenizer(Pooka.getProperty("MessageWindow.Header.DefaultHeaders", "From:To:CC:Date:Subject"), ":");
	    String hdrLabel,currentHeader = null;
	    String[] hdrValue = null;
	    
	    while (tokens.hasMoreTokens()) {
		currentHeader=tokens.nextToken();
		hdrLabel = Pooka.getProperty("MessageWindow.Header." + currentHeader + ".label", currentHeader);
		try {
		    hdrValue = mMsg.getHeader(Pooka.getProperty("MessageWindow.Header." + currentHeader + ".MIMEHeader", currentHeader));
		} catch (MessagingException me) {
		    hdrValue = null;
		}

		if (hdrValue != null && hdrValue.length > 0) {
		    headerArea.append(hdrLabel + ":  ");
		    for (int i = 0; i < hdrValue.length; i++) {
			headerArea.append(hdrValue[i]);
			if (i != hdrValue.length -1) 
			    headerArea.append(", ");
		    }
		    headerArea.append("\n");
		}
	    }
	}
	return headerArea;
    }
		    

    public Container createHeaderInputPanel(MessageProxy aMsg, Hashtable proptDict) {
	
	Box inputPanel = new Box(BoxLayout.Y_AXIS);

	Box inputRow = new Box(BoxLayout.X_AXIS);

	// Create UserProfile DropDown
	JLabel userProfileLabel = new JLabel(Pooka.getProperty("UserProfile.label","User:"), SwingConstants.RIGHT);
	userProfileLabel.setPreferredSize(new Dimension(75,userProfileLabel.getPreferredSize().height));
	JComboBox profileCombo = new JComboBox(UserProfile.getProfileList());
	inputRow.add(userProfileLabel);
	inputRow.add(profileCombo);
	
	profileCombo.setSelectedItem(UserProfile.getDefaultProfile(aMsg.getMessage()));
	proptDict.put("UserProfile", profileCombo);

	inputPanel.add(inputRow);
	
	// Create Address panel

	StringTokenizer tokens = new StringTokenizer(Pooka.getProperty("MessageWindow.Input.DefaultFields", "To:CC:BCC:Subject"), ":");
	String currentHeader = null;
	JLabel hdrLabel = null;
	JTextField inputField = null;

	while (tokens.hasMoreTokens()) {
	    inputRow = new Box(BoxLayout.X_AXIS);
	    currentHeader=tokens.nextToken();
	    hdrLabel = new JLabel(Pooka.getProperty("MessageWindow.Input.." + currentHeader + ".label", currentHeader) + ":", SwingConstants.RIGHT);
	    hdrLabel.setPreferredSize(new Dimension(75,hdrLabel.getPreferredSize().height));
	    inputRow.add(hdrLabel);

	    if (aMsg.getMessage() instanceof MimeMessage) {
		MimeMessage mMsg = (MimeMessage)aMsg.getMessage();
		try {
		    inputField = new JTextField(mMsg.getHeader(Pooka.getProperty("MessageWindow.Input." + currentHeader + ".MIMEHeader", "") , ","));
		} catch (MessagingException me) {
		    inputField = new JTextField();
		}
	    } else {
		inputField = new JTextField();
	    }
		inputRow.add(inputField);
	    
	    inputPanel.add(inputRow);

	    proptDict.put(Pooka.getProperty("MessageWindow.Input." + currentHeader + ".value", currentHeader), inputField);
	}

	return inputPanel;
    }

    /**
     * This returns the JComponent which shows the attachments.
     *
     * All it does now is returns a new AttachmentPane.
     */
    public JComponent createAttachmentPanel(MimeMessage mMsg) {
	return new AttachmentPane(mMsg);
    }

    public JComponent createBodyPanel(MessageProxy aMsg) {
	editorPane = new JEditorPane();
	
	if (isEditable()) {
	    
	    // see if this message already has a text part, and if so,
	    // include it.
	    
	    String origText = net.suberic.pooka.MailUtilities.getTextPart(aMsg.getMessage());
	    if (origText != null && origText.length() > 0) 
		editorPane.setText(origText);
	    
	    //	    bodyInputPane.setContentType("text");
	    editorPane.setSize(200,300);
	    return new JScrollPane(editorPane, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
	} else {
	    if (aMsg.getMessage() instanceof javax.mail.internet.MimeMessage) {
		javax.mail.internet.MimeMessage mMsg = (javax.mail.internet.MimeMessage) aMsg.getMessage();
		String content = net.suberic.pooka.MailUtilities.getTextPart(mMsg);
		if (content != null) {
		    editorPane.setEditable(false);
		    editorPane.setText(content);
		    editorPane.setSize(200,300);
		    return new JScrollPane(editorPane, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		} else { 
		    
		    /* nothing found.  return a blank TextArea. */
		    
		    return new JScrollPane(new JTextArea(), JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		}
	    } else 
		return null;
	}
    }

    /**
     * This will populate a Message with the values entered in the 
     * MessageWindow.
     */

    public URLName populateMessageHeaders(Message m) throws MessagingException {
	if (m instanceof MimeMessage) {
	    MimeMessage mMsg = (MimeMessage)m;
	    String key;
	    URLName urlName = null;
	    
	    Enumeration keys = inputTable.keys();
	    while (keys.hasMoreElements()) {
		key = (String)(keys.nextElement());

		if (key.equals("UserProfile")) {
		    UserProfile up =  (UserProfile)(((JComboBox)(inputTable.get(key))).getSelectedItem());
		    up.populateMessage(mMsg);
		    urlName = new URLName(up.getMailProperties().getProperty("sendMailURL", "smtp://localhost/"));
		} else {
		    String header = new String(Pooka.getProperty("MessageWindow.Header." + key + ".MIMEHeader", key));
		    String value = ((JTextField)(inputTable.get(key))).getText();
		    mMsg.setHeader(header, value);
		}
	    }
	    return urlName;
	}
	return null;
    }

    /**
     * Pops up a JFileChooser and returns the results.
     */
    public File[] getFiles(String title, String buttonText) {
	JFileChooser jfc = new JFileChooser();
	jfc.setDialogTitle(title);
	jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
	jfc.setMultiSelectionEnabled(true);
	int a = jfc.showDialog(this, buttonText);
	if (a == JFileChooser.APPROVE_OPTION)
	    return jfc.getSelectedFiles();
	else
	    return null;
    }

    /**
     * This updates the attachment panel.  This should be called when an
     * attachment is added, removed, or changed on the underlying 
     * MessageInfo object.
     */
    public void updateAttachmentPane() {

    }

    /**
     * This shows an Confirm Dialog window.  We include this so that
     * the MessageProxy can call the method without caring abou the
     * actual implementation of the Dialog.
     */    
    public int showConfirmDialog(String messageText, String title, int type) {
	return JOptionPane.showInternalConfirmDialog(this.getDesktopPane(), messageText, title, type);
    }

    /**
     * This shows an Error Message window.  We include this so that
     * the MessageProxy can call the method without caring abou the
     * actual implementation of the Dialog.
     */
    public void showError(String errorMessage, String title) {
	JOptionPane.showInternalMessageDialog(this.getDesktopPane(), errorMessage, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * This shows an Error Message window.  We include this so that
     * the MessageProxy can call the method without caring abou the
     * actual implementation of the Dialog.
     */
    public void showError(String errorMessage, String title, Exception e) {
	showError(errorMessage + e.getMessage(), title);
    }

    public String getMessageText() {
	return getEditorPane().getText();
    }

    public String getMessageContentType() {
	return getEditorPane().getContentType();
    }

    public boolean isEditable() {
	return editable;
    }

    public boolean isModified() {
	return modified;
    }

    public void setModified(boolean mod) {
	if (isEditable())
	    modified=mod;
    }

    public boolean showFullHeaders() {
	return showFullHeaders;
    }

    public JEditorPane getEditorPane() {
	return editorPane;
    }

    public MessageProxy getMessageProxy() {
	return msg;
    }

    public void setMessageProxy(MessageProxy newValue) {
	msg = newValue;
    }

    //------- Actions ----------//

    /**
     * performTextAction grabs the focused component on the MessageWindow
     * and, if it is a JTextComponent, tries to get it to perform the
     * appropriate ActionEvent.
     */
    public void performTextAction(String name, ActionEvent e) {
	Action[] textActions;

	Component focusedComponent = getFocusedComponent(this);

	// this is going to suck more.

	if (focusedComponent != null) {
	    if (focusedComponent instanceof JTextComponent) {
		JTextComponent fTextComp = (JTextComponent) focusedComponent;
		textActions = fTextComp.getActions();
		Action selectedAction = null;
		for (int i = 0; (selectedAction == null) && i < textActions.length; i++) {
		    if (textActions[i].getValue(Action.NAME).equals(name))
			selectedAction = textActions[i];
		}
		
		if (selectedAction != null) {
		    selectedAction.actionPerformed(e);
		}
	    }
	}
    }

    private Component getFocusedComponent(Container container) {
	Component[] componentList = container.getComponents();
	
	Component focusedComponent = null;
	
	// this is going to suck.
	
	for (int i = 0; (focusedComponent == null) && i < componentList.length; i++) {
	    if (componentList[i].hasFocus())
		focusedComponent = componentList[i];
	    else if (componentList[i] instanceof Container) 
		focusedComponent=getFocusedComponent((Container)componentList[i]);
	    
	}
	
	return focusedComponent;
	
    }	

    public Action[] getActions() {
	if (msg.getActions() != null) {
	    return TextAction.augmentList(msg.getActions(), getDefaultActions());
	} else 
	    return getDefaultActions();

	/*
	  if (getSelectedField() != null) 
	  return TextAction.augmentList(getSelectedField().getActions(), getDefaultActions());
	  else 
	*/
    }

    public Action[] getDefaultActions() {
	return defaultActions;
    }

    //-----------actions----------------

    // The actions supported by the window itself.

    public Action[] defaultActions = {
	new CloseAction(),
	new CutAction(),
	new CopyAction(),
	new PasteAction()
    };

    class CloseAction extends AbstractAction {

	CloseAction() {
	    super("file-close");
	}
	
        public void actionPerformed(ActionEvent e) {
	    closeMessageWindow();
	}
    }

    class CutAction extends AbstractAction {
	
	CutAction() {
	    super("cut-to-clipboard");
	}

	public void actionPerformed(ActionEvent e) {
	    performTextAction((String)getValue(Action.NAME), e);
	}
    }

    class CopyAction extends AbstractAction {
	
	CopyAction() {
	    super("copy-to-clipboard");
	}

	public void actionPerformed(ActionEvent e) {
	    performTextAction((String)getValue(Action.NAME), e);
	}
    }

    class PasteAction extends AbstractAction {
	
	PasteAction() {
	    super("paste-from-clipboard");
	}

	public void actionPerformed(ActionEvent e) {
	    performTextAction((String)getValue(Action.NAME), e);
	}
    }
}





