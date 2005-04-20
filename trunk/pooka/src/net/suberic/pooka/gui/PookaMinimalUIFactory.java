package net.suberic.pooka.gui;
import net.suberic.util.gui.propedit.PropertyEditorFactory;
import net.suberic.util.swing.*;
import net.suberic.pooka.*;
import net.suberic.pooka.gui.search.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;
import java.util.*;


/**
 * This is an implementation of PookaUIFactory which is used when Pooka
 * is started up just to send an email.
 */
public class PookaMinimalUIFactory implements PookaUIFactory {
   
  PropertyEditorFactory mEditorFactory = null;

  ThemeManager mThemeManager = null;

  public boolean mShowing = false;

  int mMaxErrorLine = 50;

  java.util.List mNewMessages = new LinkedList();

  StatusDisplay mStatusPanel = null;

  WindowAdapter mWindowAdapter = null;

  /**
   * Constructor.
   */
  public PookaMinimalUIFactory() {
    mEditorFactory = new PropertyEditorFactory(Pooka.getResources());
    mThemeManager = new ThemeManager("Pooka.theme", Pooka.getResources());

    mWindowAdapter = new WindowAdapter() {
	public void windowClosed(WindowEvent we) {
	  System.err.println("window closed.");
	  Window window = we.getWindow();
	  if (window instanceof NewMessageFrame) {
	    System.err.println("window is a NMF.  removing.");
	    mNewMessages.remove(window);
	    if (mNewMessages.isEmpty()) {
	      System.err.println("exiting.");
	      System.exit(0);
	    }
	  }
	}
      };
  }
  
  /**
   * Returns the ThemeManager for fonts and colors.
   */
  public ThemeManager getPookaThemeManager() {
    return mThemeManager;
  }

  /**
   * Creates an appropriate MessageUI object for the given MessageProxy.
   */
  public MessageUI createMessageUI(MessageProxy mp) {
    return createMessageUI(mp, null);
  }

  /**
   * Creates an appropriate MessageUI object for the given MessageProxy, 
   * using the provided MessageUI as a guideline.
   *
   * Note that this implementation ignores the templateMui component.
   */
  public MessageUI createMessageUI(MessageProxy mp, MessageUI templateMui) {
    // each MessageProxy can have exactly one MessageUI.
    if (mp.getMessageUI() != null)
      return mp.getMessageUI();
    
    MessageUI mui;
    if (mp instanceof NewMessageProxy) {
      NewMessageFrame nmf = new NewMessageFrame((NewMessageProxy) mp);
      mNewMessages.add(nmf);
      nmf.addWindowListener(mWindowAdapter);
      mui = nmf;
    } else
      mui = new ReadMessageFrame(mp);
    
    mp.setMessageUI(mui);

    //applyNewWindowLocation((JFrame)mui);
    if (templateMui != null && templateMui instanceof JComponent) 
      applyNewWindowLocation((JFrame)mui, (JComponent)templateMui);
    else
      applyNewWindowLocation((JFrame)mui, null);

    return mui;
  }
  
  /**
   * Unregisters listeners for this Factory.  Should be called if this
   * ceases to be the active UIFactory.
   */
  public void unregisterListeners() {
    Iterator iter =  mNewMessages.iterator();
    while (iter.hasNext()) {
      java.awt.Window current = (java.awt.Window) iter.next();
      current.removeWindowListener(mWindowAdapter);
    }
  }

  /**
   * Opens the given MessageProxy in the default manner for this UI.
   * Usually this will just be callen createMessageUI() and openMessageUI()
   * on it.  However, in some cases (Preview Panel without auto display)
   * it may be necessary to act differently.
   *
   */
  public void doDefaultOpen(MessageProxy mp) {
  }

  /**
   * Creates an appropriate FolderDisplayUI object for the given
   * FolderInfo.
   *
   * Returns null; this implementation doesn't allow for folder views.
   */
  public FolderDisplayUI createFolderDisplayUI(net.suberic.pooka.FolderInfo fi) {
    return null;
  }
  
  /**
   * Creates a JPanel which will be used to show messages and folders.
   *
   * This implementation returns null.
   */
  public ContentPanel createContentPanel() {
    return null;
  }
  
  /**
   * Shows an Editor Window with the given title, which allows the user
   * to edit the values in the properties Vector.  The given properties
   * will be shown according to the values in the templates Vector.
   * Note that there should be an entry in the templates Vector for
   * each entry in the properties Vector.
   */
  public void showEditorWindow(String title, java.util.Vector properties, java.util.Vector templates) {
    JFrame jf = (JFrame)getEditorFactory().createEditorWindow(title, properties, templates);
    jf.pack();
    Component currentFocusedComponent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (currentFocusedComponent != null && currentFocusedComponent instanceof JComponent) {
      applyNewWindowLocation(jf, ((JComponent) currentFocusedComponent));
    } else {
      applyNewWindowLocation(jf, null);
    }
    jf.show();
  }
  
  /**
   * Shows an Editor Window with the given title, which allows the user
   * to edit the values in the properties Vector.
   */
  public void showEditorWindow(String title, java.util.Vector properties) {
    showEditorWindow(title, properties, properties);
  }
  
  /**
   * Shows an Editor Window with the given title, which allows the user
   * to edit the given property.
   */
  public void showEditorWindow(String title, String property) {
    java.util.Vector v = new java.util.Vector();
    v.add(property);
    showEditorWindow(title, v, v);
  }

  /**
   * Shows an Editor Window with the given title, which allows the user
   * to edit the given property, which is in turn defined by the 
   * given template.
   */
  public void showEditorWindow(String title, String property, String template) {
    java.util.Vector prop = new java.util.Vector();
    prop.add(property);
    java.util.Vector templ = new java.util.Vector();
    templ.add(template);
    showEditorWindow(title, prop, templ);
  }
  
  /**
   * This shows an Confirm Dialog window.  We include this so that
   * the MessageProxy can call the method without caring abou the
   * actual implementation of the Dialog.
   */    
  public int showConfirmDialog(String messageText, String title, int type) {
    String displayMessage = formatMessage(messageText);
    final ResponseWrapper fResponseWrapper = new ResponseWrapper();
    final String fDisplayMessage = displayMessage;
    final String fTitle = title;
    final int fType = type;
    Runnable runMe = new Runnable() {
	public void run() {
	  fResponseWrapper.setInt(JOptionPane.showConfirmDialog(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(), fDisplayMessage, fTitle, fType));
	}
      };
    
    if (! SwingUtilities.isEventDispatchThread()) {
      try {
	SwingUtilities.invokeAndWait(runMe);
      } catch (Exception e) {
      }
    } else {
      runMe.run();
    }

    return fResponseWrapper.getInt();
  }
  

  /**
   * Shows a Confirm dialog with the given Object[] as the Message.
   */
  public int showConfirmDialog(Object[] messageComponents, String title, int type) {
    final ResponseWrapper fResponseWrapper = new ResponseWrapper();
    final Object[] fMessageComponents = messageComponents;
    final String fTitle = title;
    final int fType = type;
    Runnable runMe = new Runnable() {
	public void run() {
	  fResponseWrapper.setInt(JOptionPane.showConfirmDialog(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(), fMessageComponents, fTitle, fType));
	}
      };
    
    if (! SwingUtilities.isEventDispatchThread()) {
      try {
	SwingUtilities.invokeAndWait(runMe);
      } catch (Exception e) {
      }
    } else {
      runMe.run();
    }

    return fResponseWrapper.getInt();
  }
  
  /**
   * This shows an Error Message window.  We include this so that
   * the MessageProxy can call the method without caring abou the
   * actual implementation of the Dialog.
   */
  public void showError(String errorMessage, String title) {
    final String displayErrorMessage = formatMessage(errorMessage);
    final String fTitle = title;

    if (mShowing) {
      SwingUtilities.invokeLater(new Runnable() {
	  public void run() {
	    JOptionPane.showMessageDialog(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(), displayErrorMessage, fTitle, JOptionPane.ERROR_MESSAGE);
	  }
	});
    } else
      System.out.println(errorMessage);
    
    
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
   * the MessageProxy can call the method without caring abou the
   * actual implementation of the Dialog.
   */
  public void showError(String errorMessage, Exception e) {
    showError(errorMessage, Pooka.getProperty("Error", "Error"), e);
  }
  
  /**
   * This shows an Error Message window.  We include this so that
   * the MessageProxy can call the method without caring about the
   * actual implementation of the Dialog.
   */
  public void showError(String errorMessage, String title, Exception e) {
    final String displayErrorMessage = formatMessage(errorMessage + ":  " + e.getMessage());
    final Exception fE = e;
    final String fTitle = title;
    if (mShowing) {
      SwingUtilities.invokeLater(new Runnable() {
	  public void run() {
	    JOptionPane.showMessageDialog(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(), createErrorPanel(displayErrorMessage, fE), fTitle, JOptionPane.ERROR_MESSAGE);
	  }
	});
    } else
      System.out.println(errorMessage);
    
    //e.printStackTrace();
  }
  
  /**
   * This formats a display message.
   */
  public String formatMessage(String message) {
    return net.suberic.pooka.MailUtilities.wrapText(message, mMaxErrorLine, "\r\n", 5);
  }
  
  /**
   * This shows an Input window.  We include this so that the 
   * MessageProxy can call the method without caring about the actual
   * implementation of the dialog.
   */
  public String showInputDialog(String inputMessage, String title) {
    final String displayMessage = formatMessage(inputMessage);
    final String fTitle = title;
    final ResponseWrapper fResponseWrapper = new ResponseWrapper();

    Runnable runMe = new Runnable() {
	public void run() {
	  fResponseWrapper.setString(JOptionPane.showInputDialog(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(), displayMessage, fTitle, JOptionPane.QUESTION_MESSAGE));
	}
      };
    
    if (! SwingUtilities.isEventDispatchThread()) {
      try {
	SwingUtilities.invokeAndWait(runMe);
      } catch (Exception e) {
      }
    } else {
      runMe.run();
    }
    
    return fResponseWrapper.getString();
  }
  
  /**
   * This shows an Input window.  We include this so that the 
   * MessageProxy can call the method without caring about the actual
   * implementation of the dialog.
   */
  public String showInputDialog(Object[] inputPanes, String title) {
    final String fTitle = title;
    final Object[] fInputPanes = inputPanes;
    final ResponseWrapper fResponseWrapper = new ResponseWrapper();

    Runnable runMe = new Runnable() {
	public void run() {
	  fResponseWrapper.setString(JOptionPane.showInputDialog(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(), fInputPanes, fTitle, JOptionPane.QUESTION_MESSAGE));
	}
      };
    
    if (! SwingUtilities.isEventDispatchThread()) {
      try {
	SwingUtilities.invokeAndWait(runMe);
      } catch (Exception e) {
      }
    } else {
      runMe.run();
    }
    
    return fResponseWrapper.getString();

  }
  
  /**
   * Returns the PropertyEditorFactory used by this component.
   */
  public PropertyEditorFactory getEditorFactory() {
    return mEditorFactory;
  }
  
  /**
   * Shows a message.
   */
  public void showMessage(String newMessage, String title) {
    final String displayMessage = formatMessage(newMessage);
    final String fTitle = title;
    Runnable runMe = new Runnable() {
	public void run() {
	  JTextArea displayPanel = new JTextArea(displayMessage);
	  displayPanel.setEditable(false);
	  java.awt.Dimension dpSize = displayPanel.getPreferredSize();
	  JScrollPane scrollPane = new JScrollPane(displayPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
	  scrollPane.setPreferredSize(new java.awt.Dimension(Math.min(dpSize.width + 10, 500), Math.min(dpSize.height + 10, 300)));
	  
	  JOptionPane.showMessageDialog(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(), scrollPane, fTitle, JOptionPane.PLAIN_MESSAGE);
	}
      };

    if (! SwingUtilities.isEventDispatchThread()) {
      try {
	SwingUtilities.invokeAndWait(runMe);
      } catch (Exception e) {
      }
    } else {
      runMe.run();
    }

  }
  
  /**
   * Shows a status message.
   */
  public void showStatusMessage(String newMessage) {
    final String msg = newMessage;
    System.err.println("sleeping for 5 seconds...  message=" + msg);
    try {
      Thread.sleep(5000);
    } catch (Exception e) {
    }

    Runnable runMe = new Runnable() {
	public void run() {
	  synchronized(this) {
	    if (mStatusPanel == null) {
	      JFrame currentFrame = findJFrame();
	      mStatusPanel = new StatusDisplay(currentFrame);
	      mStatusPanel.pack();
	      mStatusPanel.setLocationRelativeTo(currentFrame);
	      mStatusPanel.setTitle("Message Status");
	      mStatusPanel.setStatusMessage(msg);
	      mStatusPanel.setVisible(true);
	    } else {
	      mStatusPanel.setStatusMessage(msg);
	    }
	  }
	}
      };

    if (SwingUtilities.isEventDispatchThread())
      runMe.run();
    else
      SwingUtilities.invokeLater(runMe);
    
  }
  
  /**
   * Creates a ProgressDialog using the given values.
   */
  public ProgressDialog createProgressDialog(int min, int max, int initialValue, String title, String content) {
    return new ProgressDialogImpl(min, max, initialValue, title, content);
  }

  /**
   * Clears the main status message panel.
   */
  public void clearStatus() {
    Runnable runMe = new Runnable() {
	public void run() {
	  synchronized(this) {
	    if (mStatusPanel != null) {
	      System.err.println("clearing infoPanel.");
	      mStatusPanel.clear();
	    }
	  }
	}
      };
    if (SwingUtilities.isEventDispatchThread())
      runMe.run();
    else
      SwingUtilities.invokeLater(runMe);
  }

  /**
   * Shows a SearchForm with the given FolderInfos selected from the list
   * of the given allowedValues.
   *
   * Not implemented.
   */
  public void showSearchForm(net.suberic.pooka.FolderInfo[] selectedFolders, java.util.Vector allowedValues) {
  }
  
  /**
   * Shows a SearchForm with the given FolderInfos selected.  The allowed
   * values will be the list of all available Folders.
   *
   * Not implemented.
   */
  public void showSearchForm(net.suberic.pooka.FolderInfo[] selectedFolders) {
    showSearchForm(selectedFolders, null);
  }
 
  /**
   * Shows an Address Selection form for the given AddressEntryTextArea.
   */
  public void showAddressWindow(AddressEntryTextArea aeta) {
    JFrame jf = new JFrame("Choose Address");
    jf.getContentPane().add(new AddressBookSelectionPanel(aeta, jf));
    jf.pack();
    applyNewWindowLocation(jf, aeta);
    jf.show();
  }

  /**
   * This tells the factory whether or not its ui components are showing
   * yet or not.
   */
  public void setShowing(boolean newValue) {
    mShowing=newValue;
  }

  /**
   * Determines the location for new windows.
   */
  public void applyNewWindowLocation(JFrame f, JComponent pParentComponent) {
    f.setLocationRelativeTo(pParentComponent);
  }

  /**
   * Creates the panels for showing an error message.
   */
  public Object[] createErrorPanel(String message, Exception e) {
    Object[] returnValue = new Object[2];
    returnValue[0] = message;
    returnValue[1] = new net.suberic.util.swing.ExceptionDisplayPanel(Pooka.getProperty("error.showStackTrace", "Stack Trace"), e);

    return returnValue;
  }

  /**
   * Finds the selected JFrame, if there is one.
   */
  JFrame findJFrame() {
    Window current = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    if (current != null && current instanceof JFrame) {
      return (JFrame) current;
    } 
    
    // if we haven't gotten one yet...
    if (mNewMessages.size() > 0) {
      Object first = mNewMessages.get(0);
      if (first instanceof JFrame)
	return (JFrame) first;
    }
    
    return null;
  }
  
  /**
   * A window which shows status messages.
   */
  class StatusDisplay extends JDialog {
    
    // the display area.
    JLabel mDisplayLabel = null;

    /**
     * Creates a new StatusDisplay object.
     */
    public StatusDisplay(JFrame pParentFrame) {
      super(pParentFrame);
      mDisplayLabel = new JLabel();
      mDisplayLabel.setLayout(new FlowLayout(FlowLayout.CENTER));
      mDisplayLabel.setPreferredSize(new Dimension(300,60));
      mDisplayLabel.setIcon(UIManager.getIcon("Message"));
      JPanel displayPanel = new JPanel();
      displayPanel.setBorder(BorderFactory.createEtchedBorder());
      displayPanel.add(mDisplayLabel);
      this.getContentPane().add(displayPanel);
    }
    
  /**
   * Shows a status message.
   */
    public void setStatusMessage(String pMessage) {
      final String msg = pMessage;

      Runnable runMe = new Runnable() {
	  public void run() {
	    mDisplayLabel.setText(msg);
	    mDisplayLabel.repaint();
	  }
	};
  
      if (SwingUtilities.isEventDispatchThread()) {
	runMe.run();
      } else {
	SwingUtilities.invokeLater(runMe);
      }
    }
    
  /**
   * Clears the status panel.
   */
    public void clear() {
      Runnable runMe = new Runnable() {
	  public void run() {
	    StatusDisplay.this.dispose();
	  }
	};
      if (SwingUtilities.isEventDispatchThread()) {
	runMe.run();
      } else {
	SwingUtilities.invokeLater(runMe);
      }
    }
  }
  
}
