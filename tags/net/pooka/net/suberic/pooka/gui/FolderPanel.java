package net.suberic.pooka.gui;
import net.suberic.pooka.*;
import net.suberic.util.ValueChangeListener;
import java.awt.*;
import java.awt.event.*;
import javax.mail.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import net.suberic.pooka.gui.*;
import java.util.StringTokenizer;
import java.util.Vector;

public class FolderPanel extends JScrollPane implements ValueChangeListener {
    MainPanel mainPanel=null;
    JTree folderTree;
    DefaultTreeModel folderModel;
    Session session;
    Folder trashFolder = null;
    
     public FolderPanel(MainPanel newMainPanel, Session newSession) {
	mainPanel=newMainPanel;
	session = newSession;

	setPreferredSize(new Dimension(Integer.parseInt(Pooka.getProperty("Pooka.folderPanel.hsize", "200")), Integer.parseInt(Pooka.getProperty("Pooka.folderPanel.vsize", Pooka.getProperty("Pooka.vsize","600")))));

	folderModel = new DefaultTreeModel(createTreeRoot());
	folderTree = new JTree(folderModel);

	this.getViewport().add(folderTree);

	folderTree.addMouseListener(new MouseAdapter() {
	    public void mouseClicked(MouseEvent e) {
		if (e.getClickCount() == 2) {
		    MailTreeNode tmpNode = getSelectedNode();
		    if (tmpNode != null) {
			String actionCommand = Pooka.getProperty("FolderPanel.2xClickAction", "folder-open");
			Action clickAction = getSelectedNode().getAction(actionCommand);
			if (clickAction != null ) {
			    clickAction.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, actionCommand));
			} 

		    }
		}
	    }
	});
	folderTree.addTreeSelectionListener(getMainPanel());
    }

    public MailTreeNode getSelectedNode() {
	TreePath tp = folderTree.getSelectionPath();

	if (tp != null) {
	    return (MailTreeNode)tp.getLastPathComponent();
	} else {
	    return null;
	}
    }

    private MailTreeNode createTreeRoot() {
	MailTreeNode root = new MailTreeNode("Pooka", this);

	// Get the stores we have listed.
	String storeID = null;

	StringTokenizer tokens =  new StringTokenizer(Pooka.getProperty("Store", ""), ":");

	while (tokens.hasMoreTokens()) {
	    storeID=(String)tokens.nextElement();

	    addStore(storeID, root);	    
	}
	
	return root;
    }

    /**
     * refreshStores() goes through the list of registered stores and 
     * compares these to the value of the "Store" property.  If any
     * stores are no longer listed in that property, they are removed
     * from the FolderPanel.  If any new stores are found, they are
     * added to the FolderPanel.
     *
     * This function does not add new subfolders to already existing 
     * Stores.  Use refreshStore(Store) for that.
     *
     * This function is usually called in response to a ValueChanged
     * action on the "Store" property.
     *
     */

    public void refreshStores() {
	String storeID = null;
	MailTreeNode root = (MailTreeNode)getFolderTree().getModel().getRoot();

	StringTokenizer tokens =  new StringTokenizer(Pooka.getProperty("Store", ""), ":");

	Vector allStores = new Vector();
	java.util.Enumeration storeEnum = root.children();
	while (storeEnum.hasMoreElements()) {
	    allStores.add(storeEnum.nextElement());
	}

	while (tokens.hasMoreTokens()) {
	    boolean found = false;
	    storeID=(String)tokens.nextElement();
	    for (int i=0; !(found) && i < allStores.size(); i++) {
		StoreNode currentStore = (StoreNode)allStores.elementAt(i);
		if (currentStore.getStoreID().equals(storeID)) {
		    found = true;
		    allStores.removeElement(currentStore);
		}
	    }
	    if (!(found) )
		this.addStore(storeID, root);
	}

	for (int i = 0; i < allStores.size() ; i++) {
	    this.removeStore(((StoreNode)allStores.elementAt(i)).getStoreID(), root);
	}

	getFolderTree().updateUI();
    }

    public void addStore(String storeID, MailTreeNode root) {
	URLName urln = new URLName(Pooka.getProperty("Store." + storeID + ".protocol"), Pooka.getProperty("Store." + storeID + ".server"), -1, "", Pooka.getProperty("Store." + storeID + ".user"), Pooka.getProperty("Store." + storeID + ".password", ""));
	
	
	try {
	    Store store = session.getStore(urln);
	    StoreNode storenode = new StoreNode(store, storeID, this);
	    root.add(storenode);
	} catch (NoSuchProviderException nspe) {
	}
	
    }

    public void removeStore(String storeID, MailTreeNode root) {
	java.util.Enumeration children = root.children();
	boolean removed=false;

	while (children.hasMoreElements() && (removed == false)) {
	    StoreNode sn = (StoreNode)(children.nextElement());

	    if (sn.getStoreID().equals(storeID)) {
		root.remove(sn);
		removed = true;
	    }
	}
    }

    public MainPanel getMainPanel() {
	return mainPanel;
    }

    public JTree getFolderTree() {
	return folderTree;
    }

    public void parseFolderTree(TreeModel model, Object currentNode, Vector fList) {
	if (model.isLeaf(currentNode)) {
	    if (currentNode instanceof FolderNode) {
		fList.add(((FolderNode)currentNode).getFolder());
	    } 
	} else {
	    for (int i=0; i < model.getChildCount(currentNode); i++) {
		parseFolderTree(model, model.getChild(currentNode, i), fList);
	    }
	}
    }

    /**
     * Returns a vector of all the leaf folders in the tree.
     */

    public Vector getFolderList() {
	Vector fList = new Vector();

	parseFolderTree(folderTree.getModel(), folderTree.getModel().getRoot(), fList);
	return fList;
    }

    /**
     * Specified by interface net.suberic.util.ValueChangeListener
     *
     */
    
    public void valueChanged(String changedValue) {
	if (changedValue.equals("Store"))
	    refreshStores();
	
    }

    public Folder getTrashFolder() {
	return trashFolder;
    }

    public void setTrashFolder(Folder f) {
	trashFolder = f;
    }

    public Action[] getActions() {
	if (getSelectedNode() != null) {
	    return (getSelectedNode().getActions());
	} else {
	    return null;
	}
    }
}




