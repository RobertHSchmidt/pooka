package net.suberic.pooka.messaging;

import java.net.*;
import java.nio.channels.*;

import java.io.*;

import javax.mail.*;
import javax.mail.internet.MimeMessage;

import net.suberic.pooka.*;
import net.suberic.pooka.gui.NewMessageProxy;
import net.suberic.pooka.gui.NewMessageFrame;
import net.suberic.pooka.gui.MessageUI;

/** 
 * This handles an already-made connection between 
 */
public class PookaMessageHandler extends Thread {
  
  private static int sCounter = 1;

  Socket mSocket = null;
  boolean mStopped = false;
  PookaMessageListener mParent = null;

  BufferedWriter mWriter = null;
  BufferedReader mReader = null;
  
  /**
   * Creates a new PookaMessageHandler.
   */
  public PookaMessageHandler(PookaMessageListener pParent, Socket pSocket) {
    super("PookaMessageHandler-" + sCounter++);
    System.err.println("creating new PookaMessageHandler");
    mSocket = pSocket;
    mParent = pParent;
  }
  
  /**
   * Opens the socket and listens to it.
   */
  public void run() {
    try {
      while (! mStopped && ! mSocket.isClosed()) {
	System.err.println("handling messages.");
	mReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
	handleMessage(mReader.readLine());
      }
    } catch (Exception e) {
      System.out.println("error in MessageHandler -- closing down.");
      e.printStackTrace();
    }
    
    cleanup();
  }
  
  /**
   * Handles the received message.
   */
  public void handleMessage(String pMessage) throws java.io.IOException {
    System.err.println("handling message:  '" + pMessage + "'.");

    if (pMessage != null) {
      if (pMessage.startsWith(PookaMessagingConstants.S_NEW_MESSAGE)) {
	handleNewEmailMessage(pMessage);
      } else if (pMessage.startsWith(PookaMessagingConstants.S_CHECK_VERSION)) {
	handleCheckVersionMessage();
      } else if (pMessage.startsWith(PookaMessagingConstants.S_BYE)) {
	handleByeMessage();
      } else if (pMessage.startsWith(PookaMessagingConstants.S_START_POOKA)) {
	handleStartPookaMessage();
      }
    } else {
      // bye on null.
      handleByeMessage();
    }
  }

  /**
   * Handles a newEmail message.
   */
  protected void handleNewEmailMessage(String pMessage) {
    System.err.println("it's a new message command.");
    // see if there's an address to send to.
    String address = null;
    UserProfile profile = null;
    if (pMessage.length() > PookaMessagingConstants.S_NEW_MESSAGE.length()) {
      // go to the next space
      int toAddressEnd = pMessage.indexOf(' ', PookaMessagingConstants.S_NEW_MESSAGE.length() + 1);
      if (toAddressEnd == -1)
	toAddressEnd = pMessage.length();
      
      address = pMessage.substring(PookaMessagingConstants.S_NEW_MESSAGE.length() + 1, toAddressEnd);
      if (toAddressEnd != pMessage.length() && toAddressEnd != pMessage.length() + 1) {
	String profileString = pMessage.substring(toAddressEnd + 1);
	profile = UserProfile.getProfile(profileString);
      }
    }
    sendNewEmail(address, profile);
  }

  /**
   * Sends a new email message.
   */
  public void sendNewEmail(String pAddress, UserProfile pProfile) {
    final String fAddress = pAddress;
    final UserProfile fProfile = pProfile;

    javax.swing.SwingUtilities.invokeLater(new Runnable() {
	public void run() {
	  try {
	    System.err.println("creating new message.");
	    // create the template first.  this is done so the new message
	    // opens as a top-level window.
	    NewMessageFrame template = new NewMessageFrame(new NewMessageProxy(new NewMessageInfo(new MimeMessage(Pooka.getDefaultSession()))));
	    
	    MimeMessage mm = new MimeMessage(Pooka.getDefaultSession());
	    mm.setRecipients(Message.RecipientType.TO, fAddress);
	    
	    NewMessageInfo info = new NewMessageInfo(mm);
	    if (fProfile != null)
	      info.setDefaultProfile(fProfile);
	    
	    NewMessageProxy proxy = new NewMessageProxy(info);
	    
	    MessageUI nmu = Pooka.getUIFactory().createMessageUI(proxy, template);
	    nmu.openMessageUI();
	  } catch (MessagingException me) {
	    Pooka.getUIFactory().showError(Pooka.getProperty("error.NewMessage.errorLoadingMessage", "Error creating new message:  ") + "\n" + me.getMessage(), Pooka.getProperty("error.NewMessage.errorLoadingMessage.title", "Error creating new message."), me);
	  }
	}
      });
  }

  /**
   * Handles a checkVersionMessage.
   */
  public void handleCheckVersionMessage() throws java.io.IOException {
    sendResponse(Pooka.getPookaManager().getLocalrc());
  }

  /**
   * Handles a start Pooka message.
   */
  protected void handleStartPookaMessage() {
    javax.swing.SwingUtilities.invokeLater(new Runnable() {
	public void run() {
	  net.suberic.pooka.gui.MainPanel mainPanel = Pooka.getMainPanel();
	  if (mainPanel != null) {
	    
	  }
	}
      });    
  }

  /**
   * Handles a bye message.
   */
  public void handleByeMessage() throws java.io.IOException {
    closeSocket();
  }

  /**
   * Sends a response.
   */
  public void sendResponse(String pMessage) throws java.io.IOException {
    BufferedWriter writer = getWriter();
    System.err.println("sending response '" + pMessage);
    writer.write(pMessage);
    writer.newLine();
    writer.flush();
  }

  /**
   * Closes the socket.
   */
  public void closeSocket() throws java.io.IOException {
    mSocket.close();
  }

  /**
   * Stops this handler.
   */
  void stopHandler() {
    mStopped = true;

    try {
      closeSocket();
    } catch (Exception e) {
      // ignore--we're stopping.
    }
  }

  /**
   * Gets the writer for this handler.
   */
  public BufferedWriter getWriter() throws java.io.IOException {
    if (mWriter == null) {
      synchronized(this) {
	if (mWriter == null) {
	  mWriter = new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream()));
	}
      }
    }

    return mWriter;
  }

  /** 
   * Cleans up this handler.
   */
  void cleanup() {
    mParent.removeHandler(this);
  }
}
