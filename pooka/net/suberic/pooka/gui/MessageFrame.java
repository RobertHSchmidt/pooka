package net.suberic.pooka.gui;
import net.suberic.pooka.*;
import net.suberic.util.gui.*;
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

/**
 * A top-level window for displaying a message.
 */
public abstract class MessageFrame extends JFrame implements MessageUI {

    protected MessageProxy msg;
    protected MessageDisplayPanel messageDisplay;

    protected ConfigurableToolbar toolbar;
    protected ConfigurableKeyBinding keyBindings;
    protected ConfigurableMenuBar menuBar;

    /**
     * Creates a MessageFrame from the given Message.
     */

    public MessageFrame(MessageProxy newMsgProxy) {
	super(Pooka.getProperty("Pooka.messageInternalFrame.messageTitle.newMessage", "New Message"));

	msg=newMsgProxy;

	this.getContentPane().setLayout(new BorderLayout());

	msg.setMessageUI(this);
	
	this.addWindowListener(new WindowAdapter() {
		public void windowClosing(WindowEvent e) {
		    if (getMessageProxy().getMessageUI() == MessageFrame.this)
			getMessageProxy().setMessageUI(null);
		}
	    });
    }

    protected MessageFrame() {
	this.getContentPane().setLayout(new BorderLayout());

	this.addWindowListener(new WindowAdapter() {
		public void windowClosing(WindowEvent e) {
		    if (getMessageProxy().getMessageUI() == MessageFrame.this)
			getMessageProxy().setMessageUI(null);
		}
	    });
    }

    /**
     * this method is expected to do all the implementation-specific
     * duties.
     */

    protected abstract void configureMessageFrame();

    /**
     * This opens the MessageFrame.
     */
    public void openMessageUI() {
	this.show();
    }

    /**
     * This closes the MessageFrame.
     */
    public void closeMessageUI() {
	this.dispose();
    }

    /**
     * Attaches the window to a MessagePanel.
     */
    public abstract void attachWindow();

    /**
     * This shows an Confirm Dialog window.  We include this so that
     * the MessageProxy can call the method without caring abou the
     * actual implementation of the Dialog.
    */    
    public int showConfirmDialog(String messageText, String title, int type) {
	return JOptionPane.showConfirmDialog(this, messageText, title, type);
    }

    /**
     * This shows an Confirm Dialog window.  We include this so that
     * the MessageProxy can call the method without caring abou the
     * actual implementation of the Dialog.
     */    
    public int showConfirmDialog(String messageText, String title, int optionType, int iconType) {
	return JOptionPane.showConfirmDialog(this, messageText, title, optionType, iconType);
    }

    /**
     * This shows an Error Message window.  We include this so that
     * the MessageProxy can call the method without caring abou the
     * actual implementation of the Dialog.
     */
    public void showError(String errorMessage, String title) {
	JOptionPane.showMessageDialog(this, errorMessage, title, JOptionPane.ERROR_MESSAGE);
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
	return JOptionPane.showInputDialog(this, inputMessage, title, JOptionPane.QUESTION_MESSAGE);
    }

    /**
     * This shows an Input window.  We include this so that the 
     * MessageProxy can call the method without caring about the actual
     * implementation of the dialog.
     */
    public String showInputDialog(Object[] inputPanes, String title) {
	return JOptionPane.showInputDialog(this, inputPanes, title, JOptionPane.QUESTION_MESSAGE);
    }

    /**
     * A convenience method to set the PreferredSize and Size of the
     * component to that of the current preferred width.
     */
    public void resizeByWidth() {
	//int width = (int)messageDisplay.getPreferredSize().getWidth();
	//this.setPreferredSize(new Dimension(width, width));
	this.setSize(this.getPreferredSize());
    }

    /**
     * As specified by interface net.suberic.pooka.gui.MessageUI.
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
     * As specified by interface net.suberic.pooka.UserProfileContainer.
     *
     * This implementation returns the DefaultProfile of the associated
     * MessageProxy if the MessageFrame is not editable.  If the 
     * MessageFrame is editable, it returns the currently selected 
     * UserProfile object.
     */

    public UserProfile getDefaultProfile() {
	return getMessageProxy().getDefaultProfile();
    }

    public MessageDisplayPanel getMessageDisplay() {
	return messageDisplay;
    }

    public MessageProxy getMessageProxy() {
	return msg;
    }

    public void setMessageProxy(MessageProxy newValue) {
	msg = newValue;
    }

    public String getMessageText() {
	return getMessageDisplay().getMessageText();
    }

    public String getMessageContentType() {
	return getMessageDisplay().getMessageContentType();
    }

    public AttachmentPane getAttachmentPanel() {
	return getMessageDisplay().getAttachmentPanel();
    }

    public ConfigurableToolbar getToolbar() {
	return toolbar;
    }

    public ConfigurableKeyBinding getKeyBindings() {
	return keyBindings;
    }

    //------- Actions ----------//

    public Action[] getActions() {
	return defaultActions;
    }

    public Action[] getDefaultActions() {
	return defaultActions;
    }

    //-----------actions----------------

    // The actions supported by the window itself.

    public Action[] defaultActions = {
	new CloseAction(),
	new AttachAction()
    };

    class CloseAction extends AbstractAction {

	CloseAction() {
	    super("file-close");
	}
	
        public void actionPerformed(ActionEvent e) {
	    closeMessageUI();
	}
    }

    public class AttachAction extends AbstractAction {
	AttachAction() {
	    super("window-detach");
	}

	public void actionPerformed(ActionEvent e) {
	    attachWindow();
	}
    }
}




