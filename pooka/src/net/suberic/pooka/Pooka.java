package net.suberic.pooka;
import net.suberic.pooka.gui.*;
import net.suberic.util.VariableBundle;
import net.suberic.pooka.resource.*;

import java.awt.*;
import javax.swing.*;
import java.util.Vector;
import javax.help.*;

public class Pooka {

  // globals
  
  // the resources for Pooka
  static public net.suberic.util.VariableBundle resources;
  
  // the log manager.
  static public PookaLogManager mLogManager;

  // the startup/configuration file
  static public String localrc = null;
  
  // mail globals
  static public javax.mail.Session defaultSession;
  static public javax.activation.CommandMap mailcap;
  static public javax.activation.MimetypesFileTypeMap mimeTypesMap = new javax.activation.MimetypesFileTypeMap();
  static public javax.mail.Authenticator defaultAuthenticator = null;

  // the DateFormatter, which we cache for convenience.
  static public DateFormatter dateFormatter;

  // threads
  static public net.suberic.util.thread.ActionThread searchThread = null;
  static public net.suberic.pooka.thread.FolderTracker folderTracker;

  // Pooka managers and factories
  static public AddressBookManager addressBookManager = null;
  static public StoreManager storeManager;
  static public PookaUIFactory uiFactory;
  static public SearchTermManager searchManager;
  static public NetworkConnectionManager connectionManager;
  static public OutgoingMailServerManager outgoingMailManager;
  static public PookaEncryptionManager cryptoManager;
  static public net.suberic.pooka.resource.ResourceManager resourceManager;
  static public net.suberic.pooka.ssl.PookaTrustManager sTrustManager = null;

  // the main Pooka panel.
  static public net.suberic.pooka.gui.MainPanel panel;

  // settings
  static public boolean openFolders = true;
  static public boolean useHttp = false;
  static public boolean useLocalFiles = true;

  static public String pookaHome = null;

  static public HelpBroker helpBroker;

  /**
   * Runs Pooka.  Takes the following arguments:
   *
   * -nf 
   * --noOpenSavedFolders    don't open saved folders on startup.
   * 
   * -rc <filename>
   * --rcfile <filename>     use the given file as the pooka startup file.
   *
   * --http  runs with a configuration file loaded via http
   *
   * --help shows these options.
   */
  static public void main(String argv[]) {
    sStartTime = System.currentTimeMillis();

    loadInitialResources();

    updateTime("intial resources parsed.");

    parseArgs(argv);

    updateTime("args parsed.");

    loadResources();

    mLogManager = new PookaLogManager();

    updateTime("resources loaded.");

    final net.suberic.pooka.gui.PookaStartup startup = new net.suberic.pooka.gui.PookaStartup();
    startup.show();

    updateTime("startup invoked.");

    if (! checkJavaVersion()) {
      versionError();
      System.exit(-1);
    }

    startup.setStatus("Pooka.startup.ssl");
    updateTime("loading ssl");
    StoreManager.setupSSL();
    updateTime("ssl loaded.");

    try {
      UIManager.setLookAndFeel(getProperty("Pooka.looknfeel", UIManager.getCrossPlatformLookAndFeelClassName()));
    } catch (Exception e) { System.out.println("Cannot set look and feel...");
    }
    
    updateTime("set looknfeel");
    startup.setStatus("Pooka.startup.addressBook");
    addressBookManager = new AddressBookManager();
    updateTime("loaded address book");
    
    connectionManager = new NetworkConnectionManager();
    updateTime("loaded connections");
    
    outgoingMailManager = new OutgoingMailServerManager();
    updateTime("loaded mailservers");

    dateFormatter = new DateFormatter();

    startup.setStatus("Pooka.startup.profiles");
    UserProfile.createProfiles(resources);
    updateTime("created profiles");
    
    resources.addValueChangeListener(UserProfile.vcl, "UserProfile");
    
    String mailcapSource = null;
    if (System.getProperty("file.separator").equals("\\")) {
      mailcapSource = System.getProperty("user.home") + "\\pooka_mailcap.txt";
    } else {
      mailcapSource = System.getProperty("user.home") + System.getProperty("file.separator") + ".pooka_mailcap";
    }

    try {
      mailcap = resourceManager.createMailcap(mailcapSource);
    } catch (java.io.IOException ioe) {
      System.err.println("exception loading mailcap:  " + ioe);
    }

    updateTime("created mailcaps");
    folderTracker = new net.suberic.pooka.thread.FolderTracker();
    folderTracker.start();
    updateTime("started folderTracker");
    
    searchThread = new net.suberic.util.thread.ActionThread(getProperty("thread.searchThread", "Search Thread "));
    searchThread.start();
    updateTime("started search thread");
    
    javax.activation.CommandMap.setDefaultCommandMap(mailcap);
    javax.activation.FileTypeMap.setDefaultFileTypeMap(mimeTypesMap);
    updateTime("set command/file maps");

    startup.setStatus("Pooka.startup.crypto");
    cryptoManager = new PookaEncryptionManager(resources, "EncryptionManager");
    updateTime("loaded encryption manager");

    searchManager = new SearchTermManager("Search");
    updateTime("created search manager");
    
    if (Pooka.getProperty("Pooka.guiType", "Desktop").equalsIgnoreCase("Preview"))
      uiFactory=new PookaPreviewPaneUIFactory();
    else
      uiFactory = new PookaDesktopPaneUIFactory();
    
    updateTime("created ui factory");
    resources.addValueChangeListener(new net.suberic.util.ValueChangeListener() {
	public void valueChanged(String changedValue) {
	  if (Pooka.getProperty("Pooka.guiType", "Desktop").equalsIgnoreCase("Preview")) {
	    MessagePanel mp = (MessagePanel) Pooka.getMainPanel().getContentPanel();
	    uiFactory=new PookaPreviewPaneUIFactory();
	    ContentPanel cp = ((PookaPreviewPaneUIFactory)uiFactory).createContentPanel(mp);
	    Pooka.getMainPanel().setContentPanel(cp);
	  } else {
	    PreviewContentPanel pcp = (PreviewContentPanel) Pooka.getMainPanel().getContentPanel();
	    uiFactory = new PookaDesktopPaneUIFactory();
	    ContentPanel mp = ((PookaDesktopPaneUIFactory)uiFactory).createContentPanel(pcp);
	    Pooka.getMainPanel().setContentPanel(mp);
	  }
	}
      }, "Pooka.guiType");

    resources.addValueChangeListener(new net.suberic.util.ValueChangeListener() {
	public void valueChanged(String changedValue) {
	  try {
	    UIManager.setLookAndFeel(getProperty("Pooka.looknfeel", UIManager.getCrossPlatformLookAndFeelClassName()));
	    javax.swing.SwingUtilities.updateComponentTreeUI(javax.swing.SwingUtilities.windowForComponent(getMainPanel()));
	  } catch (Exception e) { 
	    System.out.println("Cannot set look and feel..."); }
	}
      }, "Pooka.looknfeel");
    
    updateTime("created resource listeners");
    // set up help
    startup.setStatus("Pooka.startup.help");
    try {
      ClassLoader cl = new Pooka().getClass().getClassLoader();
      java.net.URL hsURL = HelpSet.findHelpSet(cl, "net/suberic/pooka/doc/en/help/Master.hs");
      HelpSet hs = new HelpSet(cl, hsURL);
      helpBroker = hs.createHelpBroker();
    } catch (Exception ee) {
      System.out.println("HelpSet net/suberic/pooka/doc/en/help/merge/Master.hs not found:  " + ee);
      ee.printStackTrace();
    }
    updateTime("loaded help");
    
    JFrame frame = new JFrame("Pooka");
    updateTime("created frame");

    defaultAuthenticator = new SimpleAuthenticator(frame);
    java.util.Properties sysProps = System.getProperties();
    sysProps.setProperty("mail.mbox.mailspool", resources.getProperty("Pooka.spoolDir", "/var/spool/mail"));
    defaultSession = javax.mail.Session.getDefaultInstance(sysProps, defaultAuthenticator);
    if (Pooka.getProperty("Pooka.sessionDebug", "false").equalsIgnoreCase("true"))
      defaultSession.setDebug(true);

    updateTime("created session.");    
    startup.setStatus("Pooka.startup.mailboxInfo");
    storeManager = new StoreManager();
    updateTime("created store manager.");
    
    storeManager.loadAllSentFolders();
    outgoingMailManager.loadOutboxFolders();
    updateTime("loaded sent/outbox");
    
    final JFrame finalFrame = frame;

    startup.setStatus("Pooka.startup.configuringWindow");
    // do all of this on the awt event thread.
    Runnable createPookaUI = new Runnable() {
	public void run() {
	  finalFrame.setBackground(Color.lightGray);
	  finalFrame.getContentPane().setLayout(new BorderLayout());
	  panel = new MainPanel(finalFrame);
	  finalFrame.getContentPane().add("Center", panel);

	  updateTime("created main panel");
	  startup.setStatus("Pooka.startup.starting");

	  panel.configureMainPanel();

	  updateTime("configured main panel");

	  finalFrame.getContentPane().add("North", panel.getMainToolbar());
	  finalFrame.setJMenuBar(panel.getMainMenu());
	  finalFrame.getContentPane().add("South", panel.getInfoPanel());
	  finalFrame.pack();
	  finalFrame.setSize(Integer.parseInt(Pooka.getProperty("Pooka.hsize", "800")), Integer.parseInt(Pooka.getProperty("Pooka.vsize", "600")));
	  
	  int x = Integer.parseInt(getProperty("Pooka.lastX", "10"));
	  int y = Integer.parseInt(getProperty("Pooka.lastY", "10"));

	  finalFrame.setLocation(x, y);
	  updateTime("configured frame");
	  startup.hide();
	  finalFrame.show();
	  updateTime("showed frame");
	  
	  uiFactory.setShowing(true);
	  
	  if (getProperty("Store", "").equals("")) {
	    if (panel.getContentPanel() instanceof MessagePanel) {
	      SwingUtilities.invokeLater(new Runnable() {
		  public void run() {
		    NewAccountPooka nap = new NewAccountPooka((MessagePanel)panel.getContentPanel());
		    nap.start();
		  }
		});
	    }
	  } else if (openFolders && getProperty("Pooka.openSavedFoldersOnStartup", "false").equalsIgnoreCase("true")) {
	    panel.getContentPanel().openSavedFolders(resources.getPropertyAsVector("Pooka.openFolderList", ""));
	  }
	  panel.refreshActiveMenus();
	}
      };
    
    try {
      javax.swing.SwingUtilities.invokeAndWait(createPookaUI);
    } catch (Exception e) {
      System.err.println("caught exception creating ui:  " + e);
      e.printStackTrace();
    }

  }

  /**
   * Loads the initial resources for Pooka.  These are used during startup.
   */
  private static void loadInitialResources() {
    try {
      ClassLoader cl = new Pooka().getClass().getClassLoader();
      java.net.URL url;
      if (cl == null) {
	url = ClassLoader.getSystemResource("net/suberic/pooka/Pookarc");
      } else {
	url = cl.getResource("net/suberic/pooka/Pookarc");
      }

      if (url == null) {
	//sigh
	url = new Pooka().getClass().getResource("/net/suberic/pooka/Pookarc");
      }
      
      java.io.InputStream is = url.openStream();
      resources = new net.suberic.util.VariableBundle(is, "net.suberic.pooka.Pooka");
    } catch (Exception e) {
      System.err.println("caught exception loading system resources:  " + e);
      e.printStackTrace();
      System.exit(-1);
    }
  }
  

  /**
   * Loads all the resources for Pooka.
   */
  public static void loadResources() {
    if (resources == null) {
      System.err.println("Error starting up Pooka:  No system resource files found.");
      System.exit(-1);
    }

    try {
      /*
      ClassLoader cl = new Pooka().getClass().getClassLoader();
      java.net.URL url;
      if (cl == null) {
	url = ClassLoader.getSystemResource("net/suberic/pooka/Pookarc");
      } else {
	url = cl.getResource("net/suberic/pooka/Pookarc");
      }
      if (url == null) {
	//sigh
	url = new Pooka().getClass().getResource("/net/suberic/pooka/Pookarc");
      }
      
      java.io.InputStream is = url.openStream();
      net.suberic.util.VariableBundle pookaDefaultBundle = new net.suberic.util.VariableBundle(is, "net.suberic.pooka.Pooka");
      */

      net.suberic.util.VariableBundle pookaDefaultBundle = resources;
      if (! useLocalFiles || pookaDefaultBundle.getProperty("Pooka.useLocalFiles", "true").equalsIgnoreCase("false")) {
	resourceManager = new DisklessResourceManager();
      } else {
	resourceManager = new FileResourceManager();
      }

      // if localrc hasn't been set, use the user's home directory.
      if (localrc == null)
	localrc = new String (System.getProperty("user.home") + System.getProperty("file.separator") + ".pookarc"); 
      
      resources = resourceManager.createVariableBundle(localrc, pookaDefaultBundle);
    } catch (Exception e) {
      System.err.println("caught exception:  " + e);
      e.printStackTrace();
    }

    if (useHttp || resources.getProperty("Pooka.httpConfig", "false").equalsIgnoreCase("true")) {
      net.suberic.pooka.gui.LoadHttpConfigPooka configPooka = new net.suberic.pooka.gui.LoadHttpConfigPooka();
      configPooka.start();
    }
  }

  /**
   * This parses any command line arguments, and makes the appropriate
   * changes.
   */
  public static void parseArgs(String[] argv) {
    if (argv == null || argv.length < 1)
      return;
    
    for (int i = 0; i < argv.length; i++) {
      if (argv[i] != null) {
	if (argv[i].equals("-nf") || argv[i].equals("--noOpenSavedFolders")) {
	  openFolders = false;
	} else if (argv[i].equals("-rc") || argv[i].equals("--rcfile")) {
	  String filename = argv[++i];
	  if (filename == null) {
	    System.err.println("error:  no startup file specified.");
	    System.err.println("Usage:  java net.suberic.pooka.Pooka [-rc <filename>]");
	    System.exit(-1);
	  }
	  
	  localrc = filename;
	} else if (argv[i].equals("--http")) {
	  useHttp = true;
	  useLocalFiles = false;
	} else if (argv[i].equals("--help")) {
	  System.out.println(Pooka.getProperty("info.startup.help", "\nUsage:  net.suberic.pooka.Pooka [OPTIONS]\n\n  -nf, --noOpenSavedFolders    don't open saved folders on startup.\n  -rc, --rcfile FILE           use the given file as the pooka startup file.\n  --http                       runs with a configuration file loaded via http\n  --help                       shows these options.\n"));
	  System.exit(0);
	}
      }
    }
  }
  
  /**
   * Checks to make sure that the Java version is valid.
   */
  public static boolean checkJavaVersion() {
    String javaVersion = System.getProperty("java.version");
    if (javaVersion.compareTo("1.4") >= 0) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Called if an incorrect version of Java is being used.
   */
  private static void versionError() {
    String errorString = Pooka.getProperty("error.incorrectJavaVersion", "Error running Pooka.  This version (1.0 beta) \nof Pooka requires a 1.2 or 1.3 JDK.  \n\nFor JDK 1.4, please use a release of Pooka 1.1.\n\nPooka can be downloaded from\nhttp://pooka.sourceforge.net/\n\nYour JDK version:  ");
    javax.swing.JOptionPane.showMessageDialog(null, errorString + System.getProperty("java.version"));
  }

  /**
   * Exits Pooka.  Attempts to close all stores first.
   */
  public static void exitPooka(int exitValue) {
    Vector v = getStoreManager().getStoreList();
    final java.util.HashMap doneMap = new java.util.HashMap();
    for (int i = 0; i < v.size(); i++) {
      // FIXME:  we should check to see if there are any messages
      // to be deleted, and ask the user if they want to expunge the
      // deleted messages.
      final StoreInfo currentStore = (StoreInfo)v.elementAt(i);
      net.suberic.util.thread.ActionThread storeThread = currentStore.getStoreThread();
      if (storeThread != null) {
	doneMap.put(currentStore, new Boolean(false));
	storeThread.addToQueue(new net.suberic.util.thread.ActionWrapper(new javax.swing.AbstractAction() {
	    
	    public void actionPerformed(java.awt.event.ActionEvent actionEvent) {
	      try {
		if (currentStore.isConnected()) {
		  currentStore.closeAllFolders(false, true);
		  currentStore.disconnectStore();
		  doneMap.put(currentStore, new Boolean(true));
		} else {
		  doneMap.put(currentStore, new Boolean(true));
		}
	      } catch (Exception e) {
		doneMap.put(currentStore, new Boolean(true));
	      }
	    }
	  }, storeThread), new java.awt.event.ActionEvent(getMainPanel(), 1, "store-close"), net.suberic.util.thread.ActionThread.PRIORITY_HIGH);
      }
    }
    long sleepTime = 30000;
    try {
      sleepTime = Long.parseLong(getProperty("Pooka.exitTimeout", "30000"));
    } catch (Exception e) {
    }
    long currentTime = System.currentTimeMillis();
    boolean done = false;
    while (! done && System.currentTimeMillis() - currentTime < sleepTime) {
      try {
	Thread.currentThread().sleep(1000);
      } catch (InterruptedException ie) {
      }
      done = true;
      for (int i = 0; done && i < v.size(); i++) {
	Object key = v.get(i);
	Boolean value = (Boolean) doneMap.get(key);
	if (value != null && ! value.booleanValue()) {
	  uiFactory.showStatusMessage(Pooka.getProperty("info.exit.waiting", "Closing store ") + ((StoreInfo) key).getStoreID());
	  done = false;
	}
      }
    }

    Pooka.resources.saveProperties();
    System.exit(exitValue);
  }
  
  /**
   * Convenience method for getting Pooka configuration properties.  Calls
   * getResources().getProperty(propName, defVal).
   */
  static public String getProperty(String propName, String defVal) {
    return (resources.getProperty(propName, defVal));
  }
  
  /**
   * Convenience method for getting Pooka configuration properties.  Calls
   * getResources().getProperty(propName).
   */
  static public String getProperty(String propName) {
    return (resources.getProperty(propName));
  }
  
  /**
   * Convenience method for setting Pooka configuration properties.  Calls
   * getResources().setProperty(propName, propValue).
   */
  static public void setProperty(String propName, String propValue) {
    resources.setProperty(propName, propValue);
  }
  
  /**
   * Returns the VariableBundle which provides all of the Pooka resources.
   */
  static public net.suberic.util.VariableBundle getResources() {
    return resources;
  }
  
  /**
   * Returns whether or not debug is enabled for this Pooka instance.
   */
  static public boolean isDebug() {
    if (resources.getProperty("Pooka.debug", "true").equals("true"))
      return true;
    else
      return false;
  }
  
  /**
   * Returns the DateFormatter used by Pooka.
   */
  static public DateFormatter getDateFormatter() {
    return dateFormatter;
  }
  
  /**
   * Returns the mailcap command map.  This is what is used to determine
   * which external programs are used to handle files of various MIME
   * types.
   */
  static public javax.activation.CommandMap getMailcap() {
    return mailcap;
  }
  
  /**
   * Returns the Mime Types map.  This is used to map file extensions to
   * MIME types.
   */
  static public javax.activation.MimetypesFileTypeMap getMimeTypesMap() {
    return mimeTypesMap;
  }
  
  /**
   * Gets the default mail Session for Pooka.
   */
  static public javax.mail.Session getDefaultSession() {
    return defaultSession;
  }
  
  /**
   * Gets the Folder Tracker thread.  This is the thread that monitors the
   * individual folders and checks to make sure that they stay connected,
   * checks for new email, etc.
   */
  static public net.suberic.pooka.thread.FolderTracker getFolderTracker() {
    return folderTracker;
  }
  
  /**
   * Gets the Pooka Main Panel.  This is the root of the entire Pooka UI.
   */
  static public MainPanel getMainPanel() {
    return panel;
  }
  
  /**
   * The Store Manager.  This tracks all of the Mail Stores that Pooka knows
   * about.
   */
  static public StoreManager getStoreManager() {
    return storeManager;
  }
  
  /**
   * The Search Manager.  This manages the Search Terms that Pooka knows 
   * about, and also can be used to construct Search queries from sets
   * of properties.
   */
  static public SearchTermManager getSearchManager() {
    return searchManager;
  }
  
  /**
   * The UIFactory for Pooka.  This is used to create just about all of the
   * graphical UI components for Pooka.  Usually this is either an instance
   * of PookaDesktopPaneUIFactory or PookaPreviewPaneUIFactory, for the
   * Desktop and Preview UI styles, respectively.
   */
  static public PookaUIFactory getUIFactory() {
    return uiFactory;
  }
  
  /**
   * The Search Thread.  This is the thread that folder searches are done
   * on.
   */
  static public net.suberic.util.thread.ActionThread getSearchThread() {
    return searchThread;
  }

  /**
   * The Address Book Manager keeps track of all of the configured Address 
   * Books.
   */
  static public AddressBookManager getAddressBookManager() {
    return addressBookManager;
  }

  /**
   * The ConnectionManager tracks the configured Network Connections.
   */
  static public NetworkConnectionManager getConnectionManager() {
    return connectionManager;
  }

  /**
   * The OutgoingMailManager tracks the various SMTP server that Pooka can
   * use to send mail.
   */
  static public OutgoingMailServerManager getOutgoingMailManager() {
    return outgoingMailManager;
  }

  /**
   * The EncryptionManager, not surprisingly, manages Pooka's encryption
   * facilities.
   */
  public static PookaEncryptionManager getCryptoManager() {
    return cryptoManager;
  }

  /**
   * The HelpBroker is used to bring up the Pooka help system.
   */
  static public HelpBroker getHelpBroker() {
    return helpBroker;
  }

  /**
   * The ResourceManager controls access to resource files.
   */
  static public ResourceManager getResourceManager() {
    return resourceManager;
  }

  /**
   * The SSL Trust Manager.
   */
  static public net.suberic.pooka.ssl.PookaTrustManager getTrustManager() {
    return sTrustManager;
  }

  /**
   * The SSL Trust Manager.
   */
  static public void setTrustManager(net.suberic.pooka.ssl.PookaTrustManager pTrustManager) {
    sTrustManager = pTrustManager;
  }

  private static long sStartTime = 0;
  private static long sLastUpdate = 0;
  /**
   * debug.
   */
  public static void updateTime(String message) {
    if (resources != null && isDebug()) {
      long current = System.currentTimeMillis();
      System.err.println(message + ", time " + (current - sLastUpdate) + ", total " + (current - sStartTime));
      sLastUpdate = current;
    }
  }
}


