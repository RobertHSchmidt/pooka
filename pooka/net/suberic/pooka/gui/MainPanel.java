package net.suberic.pooka.gui;
import net.suberic.pooka.Pooka;
import net.suberic.pooka.StoreInfo;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.border.*;
import javax.mail.Session;
import net.suberic.pooka.MailQueue;
import net.suberic.pooka.UserProfile;
import net.suberic.util.gui.*;

/**
 * The main panel for PookaMail
 * 
 * @author  Allen Petersen
 * @version 0.9 10/27/2000
 */

public class MainPanel extends JSplitPane implements net.suberic.pooka.UserProfileContainer, ActionContainer {
    private ConfigurableMenuBar mainMenu;
    private ConfigurableToolbar mainToolbar;
    private FolderPanel folderPanel;
    private ContentPanel contentPanel;
    private Session session;
    private MailQueue mailQueue;
    private UserProfile currentUser = null;
    private ConfigurableKeyBinding keyBindings;
    private boolean newMessageFlag = false;
    private String standardTitle = Pooka.getProperty("Title", "Pooka");
    private String newMessageTitle = Pooka.getProperty("Title.withNewMessages", "* Pooka *");

    public MainPanel(JFrame frame) {
	super(JSplitPane.HORIZONTAL_SPLIT);

	SimpleAuthenticator auth = new SimpleAuthenticator(frame);

	session = Session.getDefaultInstance(System.getProperties(), auth);
	
	if (Pooka.getProperty("Pooka.sessionDebug", "false").equalsIgnoreCase("true"))
	    session.setDebug(true);

	mailQueue = new MailQueue(Pooka.getDefaultSession());

    }
    
    /**
     * This actually sets up the main panel.
     */
    public void configureMainPanel() {
	// set supported actions
	// this.setLayout(new BorderLayout());
	// create the menu bar.

	contentPanel = Pooka.getUIFactory().createContentPanel();
	folderPanel = new FolderPanel(this);
	Pooka.getResources().addValueChangeListener(folderPanel, "Store");

	this.setLeftComponent(folderPanel);
	this.setRightComponent(contentPanel.getUIComponent());
	this.setDividerLocation(folderPanel.getPreferredSize().width);

	mainMenu = new ConfigurableMenuBar("MenuBar", Pooka.getResources());
	mainToolbar = new ConfigurableToolbar("MainToolbar", Pooka.getResources());

	keyBindings = new ConfigurableKeyBinding(this, "MainPanel.keyBindings", Pooka.getResources());
	keyBindings.setCondition(JComponent.WHEN_IN_FOCUSED_WINDOW);

	// set the default active menus.
	// actually, don't do this here--let Pooka do it.  this is because
	// the MenuBar hasn't actually been set yet.
	//mainMenu.setActive(getActions());
	//mainToolbar.setActive(getActions());
	//keyBindings.setActive(getActions());
	
	getParentFrame().addWindowListener(new WindowAdapter() {
		public void windowActivated(WindowEvent e) {
		    setNewMessageFlag(false);
		}
		
		public void windowClosing(WindowEvent e) {
		    exitPooka(1);
		}
	    });
	
	// set the initial currentUser
	refreshCurrentUser();

	// if openSavedFoldersOnStartup is set to true, then open all the
	// saved folders.
    }

    /**
     * This gets all the actinos associated with this panel.  Useful for
     * populating the MenuBar and Toolbar.
     *
     * The method actually returns the Panel's defaultActions plus the
     * actions of the folderPanel and/or contentPanel, depending on which
     * one currently has the focus.
     */    
    public Action[] getActions() {
	Action[] actions = getDefaultActions();
	Component focusedComponent = SwingUtilities.findFocusOwner(this);
	if (focusedComponent != null) {
	    if (folderPanel != null) 
		if (SwingUtilities.isDescendingFrom(focusedComponent, folderPanel))
		    if (folderPanel.getActions() != null)
			actions = TextAction.augmentList(folderPanel.getActions(), actions);
	    if (contentPanel != null) 
		if (SwingUtilities.isDescendingFrom(focusedComponent, contentPanel.getUIComponent()))
		    if (contentPanel.getActions() != null) 
			actions = TextAction.augmentList(contentPanel.getActions(), actions);
	}
	return actions;
    }


    /**
     * Called by ExtendedDesktopManager every time the focus on the windows
     * changes.  Resets the Actions associated with the menu items and toolbar
     * to the ones in the active window.
     *
     * Also called when the selected message in a FolderWindow is changed.
     */

    public void refreshActiveMenus() {
	mainMenu.setActive(getActions());
	mainToolbar.setActive(getActions());
	keyBindings.setActive(getActions());
	setNewMessageFlag(false);
    }

    /**
     * refreshCurrentUser() is called to get a new value for the currently
     * selected item.  In MainPanel, all it does is tries to get a 
     * UserProfile from the currently selected object in the ContentPanel.
     * If there is no object in the ContentPanel which gives a default
     * UserProfile, it then checks the FolderPanel.  If neither of these
     * returns a UserProfile, then the default UserProfile is returned.
     */
    protected void refreshCurrentUser() {
	UserProfile selectedProfile = getDefaultProfile();
	if (selectedProfile != null) {
	    currentUser = selectedProfile;
	} else {
	    currentUser = UserProfile.getDefaultProfile();
	}
    }

    /**
     * This resets the title of the main Frame to have the newMessageFlag
     * or not, depending on if there are any new messages or not.
     */
    protected void resetFrameTitle() {
	String currentTitle = getParentFrame().getTitle();
	if (getNewMessageFlag()) {
	    if (!currentTitle.equals(newMessageTitle))
		getParentFrame().setTitle(newMessageTitle);
	} else {
	    if (!currentTitle.equals(standardTitle))
		getParentFrame().setTitle(standardTitle);
	}
    }

    /**
     * As defined in net.suberic.pooka.UserProfileContainer.
     *
     * Note that this method can return null, and is primarily used to 
     * get the currentUser.  If you want to get the current default 
     * profile, use getCurrentUser() instead.
     */
    public UserProfile getDefaultProfile() {
	UserProfile returnValue = null;

	if (contentPanel != null) {
	    returnValue = contentPanel.getDefaultProfile();
	}

	if (returnValue != null)
	    return returnValue;

	if (folderPanel != null)
	    returnValue = folderPanel.getDefaultProfile();

	return returnValue;

    }

    public UserProfile getCurrentUser() {
	return currentUser;
    }
    
    /**
     * This exits Pooka.
     */
    
    public void exitPooka(int exitValue) {
	if (contentPanel instanceof MessagePanel && 
	    ((MessagePanel)contentPanel).isSavingWindowLocations()) {
	    ((MessagePanel)contentPanel).saveWindowLocations();
	}
	
	Pooka.setProperty("Pooka.hsize", Integer.toString(this.getParentFrame().getWidth()));
	Pooka.setProperty("Pooka.vsize", Integer.toString(this.getParentFrame().getHeight()));
	Pooka.setProperty("Pooka.folderPanel.hsize", Integer.toString(folderPanel.getWidth()));
	Pooka.setProperty("Pooka.folderPanel.vsize", Integer.toString(folderPanel.getHeight()));
	Pooka.setProperty("Pooka.messagePanel.hsize", Integer.toString(contentPanel.getUIComponent().getWidth()));
	Pooka.setProperty("Pooka.messagePanel.vsize", Integer.toString(contentPanel.getUIComponent().getHeight()));
	
	if (contentPanel.isSavingOpenFolders()) {
	    contentPanel.saveOpenFolders();
	}
	
	Vector v = Pooka.getStoreManager().getStoreList();
	for (int i = 0; i < v.size(); i++) {
	    // FIXME:  we should check to see if there are any messages
	    // to be deleted, and ask the user if they want to expunge the
	    // deleted messages.
	    try {
		((StoreInfo)v.elementAt(i)).closeAllFolders(false);
	    } catch (Exception e) {
		// we really don't care.
	    }
	}

	Pooka.resources.saveProperties(new File(Pooka.localrc));
	System.exit(exitValue);
	
    }


    // Accessor methods.
    // These shouldn't all be public.

    public ConfigurableMenuBar getMainMenu() {
	return mainMenu;
    }
    
    public void setMainMenu(ConfigurableMenuBar newMainMenu) {
	mainMenu=newMainMenu;
    }

    public ConfigurableToolbar getMainToolbar() {
	return mainToolbar;
    }

    public ConfigurableKeyBinding getKeyBindings() {
	return keyBindings;
    }
    
    public void setKeyBindings(ConfigurableKeyBinding newKeyBindings) {
	keyBindings = newKeyBindings;
    }

    public void setMainToolbar(ConfigurableToolbar newMainToolbar) {
        mainToolbar = newMainToolbar;
    }

    public ContentPanel getContentPanel() {
	return contentPanel;
    }

    public FolderPanel getFolderPanel() {
	return folderPanel;
    }

    public Session getSession() {
	return session;
    }

    public MailQueue getMailQueue() {
	return mailQueue;
    }

    public boolean getNewMessageFlag() {
	return newMessageFlag;
    }

    public void setNewMessageFlag(boolean newValue) {
	newMessageFlag = newValue;
	resetFrameTitle();
    }

    public Action[] getDefaultActions() {
	return defaultActions;
    }

    /**
     * Find the hosting frame, for the file-chooser dialog.
     */
    public JFrame getParentFrame() {
	return (JFrame) getTopLevelAncestor();
    }

    //-----------actions----------------
    // Actions supported by the main Panel itself.  These should always
    // be available, even when no documents are open.

    private Action[] defaultActions = {
	new ExitAction(),
	new EditUserConfigAction(),
	new EditStoreConfigAction(),
	new EditPreferencesAction(),
	new HelpAboutAction(),
	new HelpLicenseAction(),
	new SelectMessagePanelAction(),
	new SelectFolderPanelAction(),
	new NewMessageAction()
	    //new SelectMenuAction("select-menu-F"),
	    //new SelectMenuAction("select-menu-E"),
	    //new SelectMenuAction("select-menu-M"),
	    //new SelectMenuAction("select-menu-W"),
	    //new SelectMenuAction("select-menu-H")
    };


    /*
     * TODO:  This really needs to check and ask if you want to save any
     * modified documents.  Of course, we don't check to see if the docs
     * are modified yet, so this will do for now.
     */
    class ExitAction extends AbstractAction {

	ExitAction() {
	    super("file-exit");
	}

        public void actionPerformed(ActionEvent e) {
	    exitPooka(0);
	}
    }

    class ActivateWindowAction extends AbstractAction {

	ActivateWindowAction() {
	    super("activate-window");
	}

        public void actionPerformed(ActionEvent e) {
	    try { 
		((JInternalFrame)(((MessagePanel)contentPanel).getComponent(Integer.parseInt(e.getActionCommand())))).setSelected(true);
	    } catch (java.beans.PropertyVetoException pve) {
	    } catch (NumberFormatException nfe) {
	    }
	}
    }
    
    class EditUserConfigAction extends AbstractAction {

	EditUserConfigAction() {
	    super("cfg-users");
	}

        public void actionPerformed(ActionEvent e) {
	    Vector valuesToEdit = new Vector();
	    valuesToEdit.add("UserProfile");
	    valuesToEdit.add("UserProfile.default");

	    Pooka.getUIFactory().showEditorWindow(Pooka.getProperty("title.userConfig", "Edit User Information"), valuesToEdit);
	}
    }


    class EditStoreConfigAction extends AbstractAction {

	EditStoreConfigAction() {
	    super("cfg-stores");
	}

        public void actionPerformed(ActionEvent e) {
	    Vector valuesToEdit = new Vector();
	    valuesToEdit.add("Store");

	    Pooka.getUIFactory().showEditorWindow(Pooka.getProperty("title.storeConfig", "Edit Mailbox Information"), valuesToEdit);
	}
    }

    class EditPreferencesAction extends AbstractAction {

	EditPreferencesAction() {
	    super("cfg-prefs");
	}

        public void actionPerformed(ActionEvent e) {
	    //Vector valuesToEdit = Pooka.getResources().getPropertyAsVector("Preferences", "");

	    Vector valuesToEdit = new Vector();
	    valuesToEdit.add("Preferences");
	    Pooka.getUIFactory().showEditorWindow(Pooka.getProperty("title.preferences", "Edit Preferences"), valuesToEdit);
	}
    }

    class HelpAboutAction extends AbstractAction {
	
	HelpAboutAction() {
	    super("help-about");
	}
	
	public void actionPerformed(ActionEvent e) {
	    String fileName="About.html";
	    String dir="/net/suberic/pooka/doc";
	    contentPanel.showHelpScreen(Pooka.getProperty("MenuBar.Help.About.Label", "About Pooka"), this.getClass().getResource(dir + "/" + java.util.Locale.getDefault().getLanguage() + "/" + fileName));
	    
	}
    }
    
    class HelpLicenseAction extends AbstractAction {
	
	HelpLicenseAction() {
	    super("help-license");
	}

	public void actionPerformed(ActionEvent e) {
	    String fileName="COPYING";
	    String dir="/net/suberic/pooka";
	    contentPanel.showHelpScreen(Pooka.getProperty("MenuBar.Help.License.Label", "License"), this.getClass().getResource(dir + "/" + fileName));
	}
    }

    class SelectMessagePanelAction extends AbstractAction {
	
	SelectMessagePanelAction() {
	    super("select-message-panel");
	}

	public void actionPerformed(ActionEvent e) {
	    contentPanel.getUIComponent().requestFocus();
	}
    }

    class SelectFolderPanelAction extends AbstractAction {
	
	SelectFolderPanelAction() {
	    super("select-folder-panel");
	}

	public void actionPerformed(ActionEvent e) {
	    folderPanel.requestFocus();
	}
    }

    public class NewMessageAction extends AbstractAction {
	NewMessageAction() {
	    super("message-new");
	}

	public void actionPerformed(ActionEvent e) {
	    MessageUI nmu = Pooka.getUIFactory().createMessageUI(new NewMessageProxy(new net.suberic.pooka.NewMessageInfo(new javax.mail.internet.MimeMessage(getSession()))));
	    nmu.openMessageUI();
	    
	}

    }

    /*
      // this doesn't appear to be necessary.
    public class SelectMenuAction extends AbstractAction {
	SelectMenuAction() {
	    super("menu-select");
	}

	SelectMenuAction(String cmd) {
	    super(cmd);
	}

	public void actionPerformed(ActionEvent e) {
	    	    System.out.println("performing Action " + e.getActionCommand());
	    //	    selectMenu(e.getActionCommand().charAt(e.getActionCommand().length() -1));
	}

    }
    */


}