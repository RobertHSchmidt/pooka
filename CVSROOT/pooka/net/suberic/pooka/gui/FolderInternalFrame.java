package net.suberic.pooka.gui;
import net.suberic.pooka.*;
import javax.mail.*;
import javax.mail.internet.MimeMessage;
import javax.mail.event.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.text.TextAction;
import java.util.*;
import net.suberic.pooka.event.MessageLoadedEvent;
import net.suberic.util.gui.*;
import net.suberic.util.event.*;
import net.suberic.util.thread.*;
import net.suberic.util.swing.*;

/**
 * This basically is just the GUI representation of the Messages in
 * a Folder.  Most of the real work is done by the FolderInfo
 * class.  Also, most of the display is done by the FolderDisplayPanel.
 */

public class FolderInternalFrame extends JInternalFrame implements FolderDisplayUI {
    FolderInfo folderInfo = null;
    FolderDisplayPanel folderDisplay = null;
    FolderStatusBar folderStatusBar = null;
    MessagePanel messagePanel = null;
    ConfigurableToolbar toolbar;
    ConfigurableKeyBinding keyBindings;
    boolean enabled = true;

    /**
     * Creates a Folder window from the given Folder.
     */

    public FolderInternalFrame(FolderInfo newFolderInfo, MessagePanel newMessagePanel) {
	super(newFolderInfo.getFolderName() + " - " + newFolderInfo.getParentStore().getStoreID(), true, true, true, true);

	this.getContentPane().setLayout(new BorderLayout());
	
	messagePanel = newMessagePanel;
	
	setFolderInfo(newFolderInfo);
	
	getFolderInfo().setFolderDisplayUI(this);

	defaultActions = new Action[] {
	    new CloseAction(),
	    new ActionWrapper(new ExpungeAction(), getFolderInfo().getFolderThread()),
	    new NextMessageAction(),
	    new PreviousMessageAction(),
	    new GotoMessageAction(),
	    new SearchAction()
		};

	// note:  you have to set the Status Bar before you create the
	// FolderDisplayPanel, or else you'll get a null pointer exception
	// from the LoadMessageThread.

	setFolderStatusBar(new FolderStatusBar(this.getFolderInfo()));
	
	folderDisplay = new FolderDisplayPanel(getFolderInfo());
	toolbar = new ConfigurableToolbar("FolderWindowToolbar", Pooka.getResources());
	this.getContentPane().add("North", toolbar);
	this.getContentPane().add("Center", folderDisplay);
	this.getContentPane().add("South", getFolderStatusBar());
	
	this.setPreferredSize(new Dimension(Integer.parseInt(Pooka.getProperty("folderWindow.height", "570")), Integer.parseInt(Pooka.getProperty("folderWindow.width","380"))));
	this.setSize(this.getPreferredSize());

	keyBindings = new ConfigurableKeyBinding(this, "FolderWindow.keyBindings", Pooka.getResources());
	
	keyBindings.setActive(getActions());
	toolbar.setActive(getActions());
	
	// if the FolderInternalFrame itself gets the focus, pass it on to
	// the folderDisplay
	
	this.addFocusListener(new FocusAdapter() {
		public void focusGained(FocusEvent e) {
		    folderDisplay.requestFocus();
		}
	    });
	
	this.setPreferredSize(new Dimension(Integer.parseInt(Pooka.getProperty("folderWindow.height", "570")), Integer.parseInt(Pooka.getProperty("folderWindow.width","380"))));

	getFolderDisplay().getMessageTable().getSelectionModel().addListSelectionListener(new SelectionListener());

	this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

	this.addInternalFrameListener(new javax.swing.event.InternalFrameAdapter() {
		public void internalFrameClosed(javax.swing.event.InternalFrameEvent e) {
		    getFolderInfo().setFolderDisplayUI(null);
		}
	    });
	
    }

    /**
     * Searches the underlying FolderInfo's messages for messages matching
     * the search term.
     */
    public void searchFolder() {
	javax.mail.search.SearchTerm term = new javax.mail.search.SubjectTerm("info");
	try {
	    MessageInfo[] matches = getFolderInfo().search(term);
	} catch (Exception e) {
	    System.out.println("caught exception.");
	}
    }

    /**
     * This method takes the currently selected row(s) and returns the
     * appropriate MessageProxy object.
     *
     * If no rows are selected, null is returned.
     */
    public MessageProxy getSelectedMessage() {
	return getFolderDisplay().getSelectedMessage();
    }

    /**
     * This resets the size to that of the parent component.
     */
    public void resize() {
	this.setSize(getParent().getSize());
    }

    /**
     * This opens the FolderInternalFrame.
     */
    public void openFolderDisplay() {
	getMessagePanel().openFolderWindow(getFolderInfo());
    }

    /**
     * This closes the FolderInternalFrame.
     */
    public void closeFolderDisplay(){
	try {
	    this.setClosed(true);
	} catch (java.beans.PropertyVetoException e) {
	}
    }

    /**
     * This expunges all the messages marked as deleted in the folder.
     */
    public void expungeMessages() {
	try {
	    getFolderInfo().getFolder().expunge();
	} catch (MessagingException me) {
	    showError(Pooka.getProperty("error.Message.ExpungeErrorMessage", "Error:  could not expunge messages.") +"\n" + me.getMessage());
	}   
    }

    /**
     * This shows an Error Message window.  We include this so that
     * the MessageProxy can call the method without caring abou the
     * actual implementation of the Dialog.
     */
    public void showError(String errorMessage, String title) {
	JOptionPane.showInternalMessageDialog(getMessagePanel(), errorMessage, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * This shows an Error Message window.  We include this so that
     * the MessageProxy can call the method without caring abou the
     * actual implementation of the Dialog.
     */
    public void showError(String errorMessage) {
	showError(errorMessage, Pooka.getProperty("Error", "Error"));
    }

    /**
     * This shows an Error Message window.  We include this so that
     * the MessageProxy can call the method without caring about the
     * actual implementation of the Dialog.
     */
    public void showError(String errorMessage, String title, Exception e) {
	showError(errorMessage + e.getMessage(), title);
	e.printStackTrace();
    }

    /**
     * This shows an Input window.  We include this so that the 
     * MessageProxy can call the method without caring about the actual
     * implementation of the dialog.
     */
    public String showInputDialog(String inputMessage, String title) {
	return JOptionPane.showInternalInputDialog(getMessagePanel(), inputMessage, title, JOptionPane.QUESTION_MESSAGE);
    }

    /**
     * As specified by interface net.suberic.pooka.gui.FolderDisplayUI.
     * 
     * This skips to the given message.
     */
    public int selectMessage(int messageNumber) {
	return getFolderDisplay().selectMessage(messageNumber);
    }

    public int selectNextMessage() {
	return getFolderDisplay().selectNextMessage();
    }

    public int selectPreviousMessage() {
	return getFolderDisplay().selectPreviousMessage();
    }

    /**
     * As specified by interface net.suberic.pooka.gui.FolderDisplayUI.
     * 
     * This implementation sets the cursor to either Cursor.WAIT_CURSOR
     * if busy, or Cursor.DEFAULT_CURSOR if not busy.
     */
    public void setBusy(boolean newValue) {
	if (newValue)
	    this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
	else
	    this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    /**
     * Displays a status message for the folder.
     */
    public void updateStatus(String message) {
	
    }
   
    /**
     * Displays a status message for the folder.
     */
    public void updateStatus(Event e, String message) {
	if (message != null)
	    updateStatus(message);

    }

    // Accessor methods.

    public MessagePanel getMessagePanel() {
	if (messagePanel != null)
	    return messagePanel;
	else {
	    ContentPanel cp = Pooka.getMainPanel().getContentPanel();
	    if (cp instanceof MessagePanel)
		return (MessagePanel) cp;
	    else
		return null;
	}
    }

    public FolderDisplayPanel getFolderDisplay() {
	return folderDisplay;
    }

    public void setFolderInfo(FolderInfo newValue) {
	folderInfo=newValue;
    }

    public FolderInfo getFolderInfo() {
	return folderInfo;
    }

    public FolderStatusBar getFolderStatusBar() {
	return folderStatusBar;
    }

    public void setFolderStatusBar(FolderStatusBar newValue) {
	folderStatusBar = newValue;
    }

    /**
     * gets the actions handled both by the FolderInternalFrame and the 
     * selected Message(s).
     */

    public class SelectionListener implements javax.swing.event.ListSelectionListener {
	SelectionListener() {
	}

	public void valueChanged(javax.swing.event.ListSelectionEvent e) {
	    // the main menus are handled by the FolderDisplayPanel itself.
	    if (toolbar != null)
		toolbar.setActive(getActions());
	    if (keyBindings != null)
		keyBindings.setActive(getActions());
	}
    }

    /**
     * This registers the Keyboard action not only for the FolderInternalFrame
     * itself, but also for pretty much all of its children, also.  This
     * is to work around something which I think is a bug in jdk 1.2.
     * (this is not really necessary in jdk 1.3.)
     *
     * Overrides JComponent.registerKeyboardAction(ActionListener anAction,
     *            String aCommand, KeyStroke aKeyStroke, int aCondition)
     */

    public void registerKeyboardAction(ActionListener anAction,
       	       String aCommand, KeyStroke aKeyStroke, int aCondition) {
	super.registerKeyboardAction(anAction, aCommand, aKeyStroke, aCondition);

	getFolderDisplay().registerKeyboardAction(anAction, aCommand, aKeyStroke, aCondition);
	folderStatusBar.registerKeyboardAction(anAction, aCommand, aKeyStroke, aCondition);
	toolbar.registerKeyboardAction(anAction, aCommand, aKeyStroke, aCondition);
    }
    
    /**
     * This unregisters the Keyboard action not only for the FolderInternalFrame
     * itself, but also for pretty much all of its children, also.  This
     * is to work around something which I think is a bug in jdk 1.2.
     * (this is not really necessary in jdk 1.3.)
     *
     * Overrides JComponent.unregisterKeyboardAction(KeyStroke aKeyStroke)
     */

    public void unregisterKeyboardAction(KeyStroke aKeyStroke) {
	super.unregisterKeyboardAction(aKeyStroke);

	getFolderDisplay().unregisterKeyboardAction(aKeyStroke);
	folderStatusBar.unregisterKeyboardAction(aKeyStroke);
	toolbar.unregisterKeyboardAction(aKeyStroke);
    }

    /**
     * As specified by net.subeic.pooka.UserProfileContainer
     */

    public UserProfile getDefaultProfile() {
	if (getFolderInfo() != null) {
	    return getFolderInfo().getDefaultProfile();
	}
	else {
	    return null;
	}
    }

    /**
     * Returns whether or not this window is enabled.  This should be true
     * just about all of the time.  The only time it won't be true is if
     * the Folder is closed or disconnected, and the mail store isn't set
     * up to work in disconnected mode.
     */
    public boolean isEnabled() {
	return enabled;
    }

    /**
     * This sets whether or not the window is enabled.  This should only
     * be set to false when the Folder is no longer available.
     */
    public void setEnabled(boolean newValue) {
	enabled = newValue;
    }

    // MessageLoadedListener
    
    /**
     * Displays that a message has been loaded.
     * 
     * Defined in net.suberic.pooka.event.MessageLoadedListener.
     */
    public void handleMessageLoaded(MessageLoadedEvent e) {
	final MessageLoadedEvent event = e;

	Runnable runMe = new Runnable() {

		public void run() {
	if (event.getType() == MessageLoadedEvent.LOADING_STARTING) {
	    if (getFolderStatusBar().getTracker() != null) {
		getFolderStatusBar().setTracker(new LoadMessageTracker(event.getLoadedMessageCount(), 0, event.getNumMessages()));
		getFolderStatusBar().getLoaderPanel().add(getFolderStatusBar().getTracker());
	    }
	} else if (event.getType() == MessageLoadedEvent.LOADING_COMPLETE) {

	    if (getFolderStatusBar().getTracker() != null) {
		getFolderStatusBar().getLoaderPanel().remove(getFolderStatusBar().getTracker());
		getFolderStatusBar().setTracker(null);
	    }
	} else if (event.getType() == MessageLoadedEvent.MESSAGES_LOADED) {
	    if (getFolderStatusBar().getTracker() != null)
		getFolderStatusBar().getTracker().handleMessageLoaded(event);
	}
	getFolderStatusBar().repaint();
		}
	    };

	if (!SwingUtilities.isEventDispatchThread()) {
	    SwingUtilities.invokeLater(runMe);
	} else {
	    runMe.run();
	}
    }

    // ConnectionListener
    
    /**
     *
     */
    public void closed(ConnectionEvent e) {

    }

    /**
     *
     */
    public void disconnected(ConnectionEvent e) {

    }

    /**
     *
     */
    public void opened(ConnectionEvent e) {

    }

    // MessageCountListener
    /**
     *
     */
    public void messagesAdded(MessageCountEvent e) {
	getFolderStatusBar().messagesAdded(e);
    }

    public void messagesRemoved(MessageCountEvent e) { 
	getFolderStatusBar().messagesRemoved(e);
	Runnable updateAdapter = new Runnable() {
		public void run() {
		    getMessagePanel().getMainPanel().refreshActiveMenus();
		    if (toolbar != null)
			toolbar.setActive(getActions());
		    if (keyBindings != null)
			keyBindings.setActive(getActions());
		}
	    };
	if (SwingUtilities.isEventDispatchThread())
	    updateAdapter.run();
	else
	    SwingUtilities.invokeLater(updateAdapter);
	
    }
    
    // MessageChangedListener
    public void messageChanged(MessageChangedEvent e) {
	getFolderStatusBar().messageChanged(e);
	try {
	    if (e.getMessageChangeType() == MessageChangedEvent.FLAGS_CHANGED && e.getMessage().getFlags().contains(Flags.Flag.DELETED)) {
		MessageProxy selectedProxy = getSelectedMessage();
		if ( selectedProxy != null && selectedProxy.getMessageInfo().getMessage() == e.getMessage()) {
		    SwingUtilities.invokeLater(new Runnable() {
			    public void run() {
				selectNextMessage();
			    }
			});
		}
	    }
	} catch (MessagingException me) {
	    
	}
	
	SwingUtilities.invokeLater(new Runnable() {
		public void run() {
		    getFolderDisplay().repaint();
		}
	    });
    }

    /**
     * Gets the Actions for this component.
     */
    public Action[] getActions() {
	if (isEnabled()) {
	    Action[] returnValue;
	    MessageProxy m = getSelectedMessage();
	    
	    if (m != null) 
		returnValue = TextAction.augmentList(m.getActions(), getDefaultActions());
	    else 
		returnValue = getDefaultActions();
	    
	    if (folderInfo.getActions() != null)
		returnValue = TextAction.augmentList(folderInfo.getActions(), returnValue);
	    
	    return returnValue;
	} else {
	    return null;
	}
    }

    public Action[] getDefaultActions() {
	return defaultActions;
    }

    //-----------actions----------------

    // The actions supported by the window itself.

    private Action[] defaultActions;

    class CloseAction extends AbstractAction {

	CloseAction() {
	    super("file-close");
	}
	
        public void actionPerformed(ActionEvent e) {
	    closeFolderDisplay();
	}
    }

    public class ExpungeAction extends AbstractAction {

	ExpungeAction() {
	    super("message-expunge");
	}
	
        public void actionPerformed(ActionEvent e) {
	    expungeMessages();
	}
    }


    public class NextMessageAction extends AbstractAction {

	NextMessageAction() {
	    super("message-next");
	}
	
        public void actionPerformed(ActionEvent e) {
	    selectNextMessage();
	}
    }

    public class PreviousMessageAction extends AbstractAction {

	PreviousMessageAction() {
	    super("message-previous");
	}
	
        public void actionPerformed(ActionEvent e) {
	    selectPreviousMessage();
	}
    }

    public class GotoMessageAction extends AbstractAction {

	GotoMessageAction() {
	    super("message-goto");
	}
	
        public void actionPerformed(ActionEvent e) {
	    getFolderStatusBar().activateGotoDialog();
	}
    }

    public class SearchAction extends AbstractAction {

	SearchAction() {
	    super("folder-search");
	}
	
        public void actionPerformed(ActionEvent e) {
	    searchFolder();
	}
    }

}




