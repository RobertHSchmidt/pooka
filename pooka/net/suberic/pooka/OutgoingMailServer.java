package net.suberic.pooka;

import java.util.LinkedList;

import javax.mail.*;
import javax.mail.internet.*;

import net.suberic.util.*;
import net.suberic.util.thread.*;

/**
 * <p>Represents a mail server than can be used to send outgoing messages.
 *
 * @author Allen Petersen
 * @version $Revision$
 */
public class OutgoingMailServer implements net.suberic.util.Item, net.suberic.util.ValueChangeListener, NetworkConnectionListener {

  String id = null;

  String propertyName = null;

  URLName sendMailURL = null;

  String connectionID = null;

  String outboxID = null;

  NetworkConnection.ConnectionLock connectionLock = null;

  boolean sending = false;

  ActionThread mailServerThread = null;

  /**
   * <p>Creates a new OutgoingMailServer from the given property.</p>
   */
  public OutgoingMailServer (String newId) {
    id = newId;
    propertyName = "OutgoingServer." + newId;
    
    configure();
  }

  /**
   * <p>Configures this mail server.</p>
   */
  protected void configure() {
    VariableBundle bundle = Pooka.getResources();

    connectionID = bundle.getProperty(getItemProperty() + ".connection", "");
    NetworkConnection currentConnection = getConnection();
    if (currentConnection != null)
      currentConnection.addConnectionListener(this);

    sendMailURL = new URLName("smtp://" + bundle.getProperty(getItemProperty() + ".server", "") + ":" + bundle.getProperty(getItemProperty() + ".port", "") + "/");

    outboxID = bundle.getProperty(getItemProperty() + ".outbox", "");

    bundle.addValueChangeListener(this, getItemProperty() + ".connection");
    bundle.addValueChangeListener(this, getItemProperty() + ".server");
    bundle.addValueChangeListener(this, getItemProperty() + ".outbox");

    mailServerThread = new net.suberic.util.thread.ActionThread("default - smtp thread");
    mailServerThread.start();
  }

  /**
   * <p>Called when one of the values that defines this OutgoingMailServer
   * is changed.</p>
   */
  public void valueChanged(String changedValue) {
    VariableBundle bundle = Pooka.getResources();

    if (changedValue != null) {
      if (changedValue.equals(getItemProperty() + ".connection")) {
	NetworkConnection currentConnection = getConnection();
	if (currentConnection != null)
	  currentConnection.removeConnectionListener(this);

	connectionID = bundle.getProperty(getItemProperty() + ".connection", "");
	currentConnection = getConnection();
	if (currentConnection != null)
	  currentConnection.addConnectionListener(this);
	
      } else if (changedValue.equals(getItemProperty() + ".server")) {
	sendMailURL = new URLName("smtp://" + bundle.getProperty(getItemProperty() + ".server", "") + ":" + bundle.getProperty(getItemProperty() + ".port", "") + "/");
      } else if (changedValue.equals(getItemProperty() + ".outbox")) {
	String newOutboxID = bundle.getProperty(getItemProperty() + ".outbox", "");
	if (newOutboxID != outboxID) {
	  FolderInfo outbox = getOutbox();
	  if (outbox != null) {
	    outbox.setOutboxFolder(null);
	  }

	  outboxID = newOutboxID;
	  loadOutboxFolder();
	}
      }
    }
  }
  
  /**
   * Loads the outbox folders.
   */
  public void loadOutboxFolder() {
    FolderInfo outbox = getOutbox();
    if (outbox != null) {
      outbox.setOutboxFolder(this);
    }
  }

  /**
   * Sends all available messages in the outbox.
   */
  public void sendAll() {
    mailServerThread.addToQueue(new javax.swing.AbstractAction() {
	public void actionPerformed(java.awt.event.ActionEvent ae) {
	  try {
	    internal_sendAll();
	  } catch (javax.mail.MessagingException me) {
	    Pooka.getUIFactory().showError(Pooka.getProperty("Error.sendingMessage", "Error sending message:  "), me);
	  }
	}
      }, new java.awt.event.ActionEvent(this, 0, "message-send-all"));
  }

  /**
   * Sends all available messages in the outbox.
   */
  protected synchronized void internal_sendAll() throws javax.mail.MessagingException {
    
    try {
      sending = true;
      
      Transport sendTransport = prepareTransport(true);

      try {
	sendTransport.connect();
	
	sendAll(sendTransport);
      } finally {
	sendTransport.close();
      }
    } finally {
      sending = false;
      if (connectionLock != null)
	connectionLock.release();
    }
  }
  
  /**
   * Sends all available messages in the outbox using the given, already 
   * open Transport object.  Leaves the Transport object open.
   */
  private void sendAll(Transport sendTransport) throws javax.mail.MessagingException {
    
    LinkedList exceptionList = new LinkedList();

    FolderInfo outbox = getOutbox();
    
    if (outbox != null) {
      // we need the thread lock for this folder.
      Object runLock = outbox.getFolderThread().getRunLock();
      synchronized(runLock) {
	if ( ! outbox.isConnected()) {
	  outbox.openFolder(Folder.READ_WRITE);
	}

	Message[] msgs = outbox.getFolder().getMessages();    
	
	try {
	  for (int i = 0; i < msgs.length; i++) {
	    Message m = msgs[i];
	    if (! m.isSet(Flags.Flag.DRAFT)) {
	      try {
		sendTransport.sendMessage(m, m.getAllRecipients());
		m.setFlag(Flags.Flag.DELETED, true);
	      } catch (MessagingException me) {
		exceptionList.add(me);
	      }
	    }
	  }
	} finally {
	  if (exceptionList.size() > 0) {
	    final int exceptionCount = exceptionList.size();
	    javax.swing.SwingUtilities.invokeLater( new Runnable() {
		public void run() {
		  Pooka.getUIFactory().showError(Pooka.getProperty("error.OutgoingServer.queuedSendFailed", "Failed to send message(s) in the Outbox.  Number of errors:  ") +  exceptionCount );
		}
	      } );
	  }
	  outbox.getFolder().expunge();
	}
      }
    }
  }

  /**
   * Virtually sends a message.  If the current status is connected, then
   * the message will actually be sent now.  If not, and the 
   * Message.sendImmediately setting is true, then we'll attempt to send
   * the message anyway.  
   */
  public synchronized void sendMessage(NewMessageInfo nmi, boolean connect) throws javax.mail.MessagingException {
    final NewMessageInfo nmi_final = nmi;
    final boolean connect_final = connect;
    
    mailServerThread.addToQueue(new javax.swing.AbstractAction() {
	public void actionPerformed(java.awt.event.ActionEvent ae) {
	  internal_sendMessage(nmi_final, connect_final);
	}
      }, new java.awt.event.ActionEvent(this, 0, "message-send-all"));
  }

  private synchronized void internal_sendMessage(NewMessageInfo nmi, boolean connect) {
    sending = true;

    try {
      boolean connected = false;
      Transport sendTransport = null;
      try {
	sendTransport = prepareTransport(connect);
	sendTransport.connect();
	connected = true;
      } catch (MessagingException me) {
	// if the connection/mail transport isn't available.
	FolderInfo outbox = getOutbox();
	
	if (outbox != null) {
	  // we need the lock
	  Object runLock = outbox.getFolderThread().getRunLock();
	  synchronized(runLock) {
	    try {
	      outbox.appendMessages(new MessageInfo[] { nmi });
	    
	      javax.swing.SwingUtilities.invokeLater(new Runnable() {
		  public void run() {
		    Pooka.getUIFactory().showError(Pooka.getProperty("error.MessageWindow.sendDelayed", "Connection unavailable.  Message saved to Outbox."));
		  }
		});
	      ((net.suberic.pooka.gui.NewMessageProxy)nmi.getMessageProxy()).sendSucceeded();
	    } catch(MessagingException nme) {
	      ((net.suberic.pooka.gui.NewMessageProxy)nmi.getMessageProxy()).sendFailed(nme);	  
	    }
	  }
	} else {
	  MessagingException nme = new MessagingException("Connection unavailable, and no Outbox specified.");
	  ((net.suberic.pooka.gui.NewMessageProxy)nmi.getMessageProxy()).sendFailed(nme);	  
	}
	
      }

      
      // if the connection worked.
      if (connected) {
	try {
	  try {
	    Message m = nmi.getMessage();
	    sendTransport.sendMessage(m, m.getAllRecipients());
	    ((net.suberic.pooka.gui.NewMessageProxy)nmi.getMessageProxy()).sendSucceeded();
	  } catch (MessagingException me) {
	    ((net.suberic.pooka.gui.NewMessageProxy)nmi.getMessageProxy()).sendFailed(me);	  
	  }
	  // whether or not the send failed. try sending all the other 
	  // messages in the queue, too.
	  try {
	    sendAll(sendTransport);
	  } catch (MessagingException exp) {
	    final MessagingException me = exp;
	    javax.swing.SwingUtilities.invokeLater(new Runnable() {
		public void run() {
		  Pooka.getUIFactory().showError(Pooka.getProperty("error.OutgoingServer.outboxSendFailed", "Failed to send all messages in Outbox:  "), me);
		}
	      });
	  }
	} finally {
	  try {
	    sendTransport.close();
	  } catch (MessagingException me) {
	    // we don't care.
	  }
	}
      }
    } finally {
      sending = false;
      if (connectionLock != null)
	connectionLock.release();
    }
  }
  
  /**
   * Virtually sends a message.  If the current status is connected, then
   * the message will actually be sent now.  
   */
  public void sendMessage(NewMessageInfo nmi) throws javax.mail.MessagingException {
    sendMessage(nmi, false);
  }

  /**
   * Gets a Transport object for this OutgoingMailServer.
   */
  protected Transport prepareTransport(boolean connect) throws javax.mail.MessagingException {
    
    NetworkConnection connection = getConnection();
    
    if (connect) {
      if (connection.getStatus() == NetworkConnection.DISCONNECTED) {
	connection.connect();
      }
    }
    
    if (connection.getStatus() != NetworkConnection.CONNECTED) {
      throw new MessagingException(Pooka.getProperty("error.connectionDown", "Connection down for Mail Server:  ") + getItemID());
    } else {
      connectionLock = connection.getConnectionLock();
    }
    
    Session session = Pooka.getDefaultSession();


    boolean useAuth = Pooka.getProperty(getItemProperty() + ".authenticated", "false").equalsIgnoreCase("true");
    if (useAuth) {
      java.util.Properties sysProps = new java.util.Properties(System.getProperties());
      sysProps.setProperty("mail.mbox.mailspool", Pooka.getProperty("Pooka.spoolDir", "/var/spool/mail"));
      sysProps.setProperty("mail.smtp.auth", "true");
      String userName = Pooka.getProperty(getItemProperty() + ".user", "");
      if (! userName.equals(""))
	sysProps.setProperty("mail.smtp.user", userName);
      String password = Pooka.getProperty(getItemProperty() + ".password", "");
      if (! password.equals(""))
	sysProps.setProperty("mail.smtp.password", password);

      session = javax.mail.Session.getInstance(sysProps, Pooka.defaultAuthenticator);
      if (Pooka.getProperty("Pooka.sessionDebug", "false").equalsIgnoreCase("true"))
	session.setDebug(true);
    }
    Transport sendTransport = session.getTransport(sendMailURL); 
    return sendTransport;
  }

  /**
   * <p>The NetworkConnection that this OutgoingMailServer depends on.
   */
  public NetworkConnection getConnection() {
    NetworkConnectionManager connectionManager = Pooka.getConnectionManager();
    NetworkConnection returnValue = connectionManager.getConnection(connectionID);
    if (returnValue != null)
      return returnValue;
    else
      return connectionManager.getDefaultConnection();
  }

  /**
   * Notifies this component that the state of a network connection has
   * changed.
   */
  public void connectionStatusChanged(NetworkConnection connection, int newStatus) {
    if (newStatus == NetworkConnection.CONNECTED && ! sending && Pooka.getProperty(getItemProperty() + ".sendOnConnect", "false").equalsIgnoreCase("true")) {
      sendAll();
    }
  }

  /**
   * <p>The FolderInfo where messages for this MailServer are
   * stored until they're ready to be sent.
   */
  public FolderInfo getOutbox() {
    
    return Pooka.getStoreManager().getFolder(outboxID);
  }

  /**
   * <p>The Item ID for this OutgoingMailServer.</p>
   */
  public String getItemID() {
    return id;
  }

  /**
   * <p>The Item property for this OutgoingMailServer.  This is usually
   * OutgoingMailServer.<i>itemID</i>.</p>
   */
  public String getItemProperty() {
    return propertyName;
  }

}
