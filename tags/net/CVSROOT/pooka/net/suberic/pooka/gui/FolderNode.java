package net.suberic.pooka.gui;
import javax.swing.JComponent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import java.awt.event.ActionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.mail.Store;
import javax.mail.Folder;
import javax.mail.MessagingException;
import java.util.Hashtable;
import java.util.StringTokenizer;
import net.suberic.pooka.Pooka;
import javax.mail.FolderNotFoundException;
import javax.swing.JOptionPane;
import net.suberic.pooka.FolderInfo;

public class FolderNode extends MailTreeNode {
    
    protected FolderInfo folderInfo = null;
    protected boolean hasLoaded = false;

    /**
     * creates a tree node that points to a folder
     *
     * @param newFolder	the store for this node
     */
    public FolderNode(FolderInfo newFolderInfo, JComponent newParent) {
	super(newFolderInfo, newParent);
	folderInfo = newFolderInfo;

	commands = new Hashtable();

	
	Action[] actions = defaultActions;

	if (actions != null) {
	    for (int i = 0; i < actions.length; i++) {
		Action a = actions[i];
		commands.put(a.getValue(Action.NAME), a);
	    }
	}
    }

    
    /**
     * a Folder is a leaf if it cannot contain sub folders
     */
    public boolean isLeaf() {
	try {
	    if ((getFolder().getType() & Folder.HOLDS_FOLDERS) == 0)
	    	return true;
	} catch (MessagingException me) { }
	
	// otherwise it does hold folders, and therefore not
	// a leaf
	return false;
    }
   
    /**
     * returns the folder for this node
     */
    public Folder getFolder() {
	return folderInfo.getFolder();
    }
    


    /**
     * return the number of children for this folder node. The first
     * time this method is called we load up all of the folders
     * under the store's defaultFolder
     */

    public int getChildCount() {
	if (!hasLoaded) {
	    loadChildren();
	}
	return super.getChildCount();
    }
    
    protected void loadChildren() {
	// if it is a leaf, just say we have loaded them
	if (isLeaf()) {
	    hasLoaded = true;
	    return;
	}
	// get the default folder, and list the
	// subscribed folders on it
	
	Folder folder = getFolder();

	Folder[] subscribed;
	
	StringTokenizer tokens = new StringTokenizer(Pooka.getProperty("Store." + getFolderID() + ".folderList", "INBOX"), ":");
	    
	String newFolderName;

	for (int i = 0 ; tokens.hasMoreTokens() ; i++) {
	    try {
		newFolderName = (String)tokens.nextToken();
		subscribed = folder.list(newFolderName);
		FolderNode node = new FolderNode(new FolderInfo(subscribed[0], getFolderID() + "." + newFolderName), getParentContainer());
		// we used insert here, since add() would mak
		// another recursive call to getChildCount();
		insert(node, i);
	    } catch (MessagingException me) {
		if (me instanceof FolderNotFoundException) {
		    JOptionPane.showInternalMessageDialog(((FolderPanel)getParentContainer()).getMainPanel().getMessagePanel(), Pooka.getProperty("error.FolderWindow.folderNotFound", "Could not find folder.") + "\n" + me.getMessage());
		} else {
		    me.printStackTrace();
		}
	    }
	}

	hasLoaded = true;

    }

    public String getFolderID() {
	return getFolderInfo().getFolderID();
    }

    public FolderInfo getFolderInfo() {
	return folderInfo;
    }

    /**
     * override toString() since we only want to display a folder's
     * name, and not the full path of the folder
     */
    public String toString() {
	return getFolder().getName();
    }
    
    public Action[] getActions() {
	return defaultActions;
    }

    public Action[] defaultActions = {
	new OpenAction()
    };

    class OpenAction extends AbstractAction {

	OpenAction() {
	    super("folder-open");
	}

	OpenAction(String nm) {
	    super(nm);
	}

	public void actionPerformed(ActionEvent e) {
	    try {
		if ((getFolder().getType() & Folder.HOLDS_MESSAGES) != 0)
		    ((FolderPanel)getParentContainer()).getMainPanel().getMessagePanel().openFolderWindow(folderInfo);
		if ((getFolder().getType() & Folder.HOLDS_FOLDERS) != 0) {
		    javax.swing.JTree folderTree = ((FolderPanel)getParentContainer()).getFolderTree();
		    folderTree.expandPath(folderTree.getSelectionPath());
		}
	    } catch (MessagingException me) {
	    }
	}
    }
}
