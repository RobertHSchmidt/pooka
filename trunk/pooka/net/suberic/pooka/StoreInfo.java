package net.suberic.pooka;

import javax.mail.*;
import javax.mail.event.*;
import javax.swing.event.EventListenerList;
import java.util.*;
import net.suberic.pooka.gui.*;
import net.suberic.util.ValueChangeListener;
import net.suberic.util.thread.ActionThread;
import net.suberic.util.VariableBundle;

/**
 * This class does all of the work for a Store.  It keeps track of the
 * StoreNode for the Store, as well as keeping the children of the store
 * and the properties of the Store.
 */

public class StoreInfo implements ValueChangeListener {
    //public static Vector allStores = null;

    private Store store;

    // The is the store ID.
    private String storeID;

    // Information for the StoreNode
    private StoreNode storeNode;
    private Vector children;

    // the status indicators
    private boolean connected = false;
    private boolean authorized = false;
    private boolean available = false;

    private UserProfile defaultProfile;

    // the connection information.
    private String user;
    private String password;
    private String server;
    private String protocol;
    private URLName url;

    // the Thread for connections to this Store.
    private ActionThread storeThread;

    // the Trash folder for this Store, if any.
    private FolderInfo trashFolder;

    /**
     * Creates a new StoreInfo from a Store ID.
     */

    public StoreInfo(String sid) {
	setStoreID(sid);
    
	user = Pooka.getProperty("Store." + storeID + ".user", "");
	password = Pooka.getProperty("Store." + storeID + ".password", "");
	if (!password.equals(""))
	    password = net.suberic.util.gui.PasswordEditorPane.descrambleString(password);
	server = Pooka.getProperty("Store." + storeID + ".server", "");
	protocol = Pooka.getProperty("Store." + storeID + ".protocol", "");
	
	url = new URLName(protocol, server, -1, "", user, password);
	
	try {
	    store = Pooka.getDefaultSession().getStore(url);
	    available=true;
	} catch (NoSuchProviderException nspe) {
	    available=false;
	}

	// don't allow a StoreInfo to get created with an empty folderList.
	
	if (Pooka.getProperty("Store." + storeID + ".folderList", "").equals(""))
	    Pooka.setProperty("Store." + storeID + ".folderList", "INBOX");
	
	Pooka.getResources().addValueChangeListener(this, getStoreProperty());
	Pooka.getResources().addValueChangeListener(this, getStoreProperty() + ".folderList");
	Pooka.getResources().addValueChangeListener(this, getStoreProperty() + ".defaultProfile");
	
	store.addConnectionListener(new ConnectionAdapter() { 
		
		public void disconnected(ConnectionEvent e) {
		    if (Pooka.isDebug())
			System.out.println("Store " + getStoreID() + " disconnected.");
		    if (connected == true) {
			try {
			    if (!(store.isConnected()))
				store.connect();
			} catch (MessagingException me) {
			    System.out.println("Store " + getStoreID() + " disconnected and unable to reconnect:  " + me.getMessage());
			}
		    }
		}
	    });

	storeThread = new ActionThread(this.getStoreID() + " - ActionThread");
	storeThread.start();

	defaultProfile = UserProfile.getProfile(Pooka.getProperty(getStoreProperty() + ".defaultProfile", ""));

	updateChildren();
	
	String trashFolderName = Pooka.getProperty(getStoreProperty() + ".trashFolder", "");
	if (trashFolderName.length() > 0) {
	    trashFolder = getChild(trashFolderName);
	    trashFolder.setTrashFolder(true);
	}
    }	
    
    /**
     * This updates the children of the current store.  Generally called
     * when the folderList property is changed.
     */

    public void updateChildren() {

	Vector newChildren = new Vector();

	StringTokenizer tokens = new StringTokenizer(Pooka.getProperty(getStoreProperty() + ".folderList", "INBOX"), ":");
	
	String newFolderName;

	for (int i = 0 ; tokens.hasMoreTokens() ; i++) {
	    newFolderName = (String)tokens.nextToken();
	    FolderInfo childFolder = getChild(newFolderName);
	    if (childFolder == null) {
		childFolder = new FolderInfo(this, newFolderName);
		newChildren.add(childFolder);
	    }
	} 
	children = newChildren;

	if (storeNode != null)
	    storeNode.loadChildren();
    }
    
    /**
     * This goes through the list of children of this store and
     * returns the FolderInfo for the given childName, if one exists.
     * If none exists, or if the children Vector has not been loaded
     * yet, or if this is a leaf node, then this method returns null.
     */
    public FolderInfo getChild(String childName) {
	FolderInfo childFolder = null;
	String folderName  = null, subFolderName = null;

	if (children != null) {
	    int divider = childName.indexOf('/');
	    if (divider > 0) {
		folderName = childName.substring(0, divider);
		if (divider < childName.length() - 1)
		    subFolderName = childName.substring(divider + 1);
	    } else 
		folderName = childName;
	    
	    for (int i = 0; i < children.size(); i++)
		if (((FolderInfo)children.elementAt(i)).getFolderName().equals(folderName))
		    childFolder = (FolderInfo)children.elementAt(i);
	}
	
	if (childFolder != null && subFolderName != null)
	    return childFolder.getChild(subFolderName);
	else
	    return childFolder;
    }


    /**
     * This goes through the list of children of this store and
     * returns the FolderInfo that matches this folderID.
     * If none exists, or if the children Vector has not been loaded
     * yet, or if this is a leaf node, then this method returns null.
     */
    public FolderInfo getFolderById(String folderID) {
	FolderInfo childFolder = null;

	if (children != null) {
	    for (int i = 0; i < children.size(); i++) {
		FolderInfo possibleMatch = ((FolderInfo)children.elementAt(i)).getFolderById(folderID);
		if (possibleMatch != null) {
		    return possibleMatch;
		}
	    }
	}

	return null;
    }

    /**
     * This handles the changes if the source property is modified.
     *
     * As defined in net.suberic.util.ValueChangeListener.
     */

    public void valueChanged(String changedValue) {
	if (changedValue.equals(getStoreProperty() + ".folderList")) {
	    updateChildren();
	} else if (changedValue.equals(getStoreProperty() + ".defaultProfile")) {
	    defaultProfile = UserProfile.getProfile(Pooka.getProperty(changedValue, ""));
	}
    }

    /**
     * This method connects the Store, and sets the StoreInfo to know that
     * the Store should be connected.  You should use this method instead of
     * calling getStore().connect(), because if you use this method, then
     * the StoreInfo will try to keep the Store connected, and will try to
     * reconnect the Store if it gets disconnected before 
     * disconnectStore is called.
     *
     * This method also calls updateChildren() to load the children of 
     * the Store, if the children vector has not been loaded yet.
     */
    public void connectStore() throws MessagingException {
	if (store.isConnected()) {
	    connected=true;
	    return;
	} else { 
	    store.connect();
	    connected=true;

	    if (Pooka.getProperty("Pooka.openFoldersOnConnect", "true").equalsIgnoreCase("true"))
		for (int i = 0; i < children.size(); i++)
		    ((FolderInfo)children.elementAt(i)).openAllFolders(Folder.READ_WRITE);
	}
	
    }
    
    /**
     * This method disconnects the Store.  If you connect to the Store using 
     * connectStore() (which you should), then you should use this method
     * instead of calling getStore.disconnect().  If you don't, then the
     * StoreInfo will try to reconnect the store.
     */
    public void disconnectStore() throws MessagingException {
	if (!(store.isConnected())) {
	    connected=false;
	    return;
	} else {
	    connected=false;
	    store.close();
	}
    }

    /**
     * This loads and creates all the Stores from the Store property of
     * the VariableBundle.
     */
    /*
    public static Vector loadAllStores(VariableBundle resources) {
	allStores = new Vector();
	String storeID = null;
	StringTokenizer tokens =  new StringTokenizer(resources.getProperty("Store", ""), ":");
	
	while (tokens.hasMoreTokens()) {
	    storeID=(String)tokens.nextElement();
	    
	    addStore(storeID);	    
	}

	return allStores;
    }
    */

    /**
     * This adds the store with the given storeName to the allStores list.
     */
    /*
    public static void addStore(String storeName) {
	if (getStoreInfo(storeName) == null) {
	    StoreInfo store = new StoreInfo(storeName);
	    allStores.add(store);
	}
    }
    */

    /**
     * This adds the StoreInfo to the allStores list.
     */
    /*
    public static void addStore(StoreInfo storeInfo) {
	if (!allStores.contains(storeInfo))
	    allStores.add(storeInfo);
    }
    */

    /**
     * This removes the StoreInfo with the given storeName from the allStores
     * list.
     */
    /*
    public static void removeStore (String storeName) {
	boolean removed = false;

	for (int i = 0; (removed == false) && i < allStores.size(); i++) {
	    StoreInfo si = (StoreInfo)(allStores.elementAt(i));

	    if (si.getStoreID().equals(storeName)) {
		allStores.remove(si);
		removed = true;
	    }
	}	
    }
    */
    
    /**
     * This removes the StoreInfo from the allStores list.
     */
    /*
    public static void removeStore (StoreInfo storeInfo) {
	allStores.remove(storeInfo);
    }
    */

    /**
     * This returns the StoreInfo with the given storeName if it exists
     * in the allStores Vector; otherwise, returns null.
     */
    static public StoreInfo getStoreInfo(String storeName) {
	Vector allStores = getAllStores();
	for (int i = 0; i < allStores.size(); i++) {
	    StoreInfo si = (StoreInfo)(getAllStores().elementAt(i));

	    if (si.getStoreID().equals(storeName)) {
		return si;
	    }
	}	
	return null;
    }

    // Accessor methods.

    public Store getStore() {
	return store;
    }

    private void setStore(Store newValue) {
	store=newValue;
    }

    /**
     * This returns the StoreID.
     */
    public String getStoreID() {
	return storeID;
    }

    /**
     * This sets the storeID.
     */
    private void setStoreID(String newValue) {
	storeID=newValue;
    }

    /**
     * This returns the property which defines this StoreNode, such as
     * "Store.myStore".
     */
    public String getStoreProperty() {
	return "Store." + getStoreID();
    }

    public Vector getChildren() {
	return children;
    }

    public StoreNode getStoreNode() {
	return storeNode;
    }

    public void setStoreNode(StoreNode newValue) {
	storeNode = newValue;
    }

    public boolean isConnected() {
	return connected;
    }

    public boolean isAvailable() {
	return available;
    }

    public boolean isAuthorized() {
	return authorized;
    }

    public UserProfile getDefaultProfile() {
	return defaultProfile;
    }

    public ActionThread getStoreThread() {
	return storeThread;
    }

    public void setStoreThread(ActionThread newValue) {
	storeThread=newValue;
    }

    public FolderInfo getTrashFolder() {
	return trashFolder;
    }

    public void setTrashFolder(FolderInfo newValue) {
	trashFolder = newValue;
    }

    public static Vector getAllStores() {
	return Pooka.getAllStoreInfos();
    }
}
