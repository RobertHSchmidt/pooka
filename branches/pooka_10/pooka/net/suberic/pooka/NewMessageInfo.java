package net.suberic.pooka;
import javax.mail.*;
import java.util.Vector;
import java.util.Enumeration;
import java.io.*;
import javax.activation.*;
import javax.mail.internet.*;

/**
 * A MessageInfo representing a new message.
 */
public class NewMessageInfo extends MessageInfo {

  public NewMessageInfo(Message newMessage) {
    message = newMessage;
    attachments = new AttachmentBundle();
  }

  /**
   * Sends the new message, using the given Profile, the given 
   * InternetHeaders, the given messageText, the given ContentType, and 
   * the attachments already set for this object.
   */
  public void sendMessage(UserProfile profile, InternetHeaders headers, String messageText, String messageContentType) throws MessagingException {
    
    MimeMessage mMsg = (MimeMessage) message;
    
    URLName urlName = null;
    String sendPrecommand = null;
    
    if (profile != null) {
      profile.populateMessage(mMsg);
      mMsg.setHeader(Pooka.getProperty("Pooka.userProfileProperty", "X-Pooka-UserProfile"), profile.getName());
      urlName = profile.getSendMailURL();
      sendPrecommand = profile.getSendPrecommand();
    }
    
    Enumeration individualHeaders = headers.getAllHeaders();
    while(individualHeaders.hasMoreElements()) {
      Header currentHeader = (Header) individualHeaders.nextElement();
      message.setHeader(currentHeader.getName(), currentHeader.getValue());
    }
    
    mMsg.setHeader("X-Mailer", Pooka.getProperty("Pooka.xmailer", "Pooka"));

    if (Pooka.getProperty("Pooka.lineWrap", "").equalsIgnoreCase("true"))
      messageText=net.suberic.pooka.MailUtilities.wrapText(messageText);
    
    if (attachments.getAttachments() != null && attachments.getAttachments().size() > 0) {
      MimeBodyPart mbp = new MimeBodyPart();
      mbp.setContent(messageText, messageContentType);
      MimeMultipart multipart = new MimeMultipart();
      multipart.addBodyPart(mbp);
      for (int i = 0; i < attachments.getAttachments().size(); i++) 
	multipart.addBodyPart(((MBPAttachment)attachments.getAttachments().elementAt(i)).getMimeBodyPart());
      multipart.setSubType("mixed");
      getMessage().setContent(multipart);
      getMessage().saveChanges();
    } else {
      getMessage().setContent(messageText, messageContentType);
    }
    
    boolean sent = false;
    if (profile != null) {
      OutgoingMailServer mailServer = profile.getMailServer();
      if (mailServer != null) {
	mailServer.sendMessage(this);
	sent = true;
      }
    } 
    
    if (! sent) {
      if (urlName != null) {
	Pooka.getMainPanel().getMailQueue().sendMessage(this, urlName, sendPrecommand);
	sent = true;
      }
    } 

    if (! sent) {
      throw new MessagingException(Pooka.getProperty("error.noSMTPServer", "Error sending Message:  No mail server configured."));
    }
  }

  /**
   * Converts the given address line into an address line suitable for
   * this NewMessageInfo.  Specifically, this goes through each address
   * in the list and adds the UserProfile's defaultDomain to each entry
   * which doesn't have a domain already.
   */
  public String convertAddressLine(String oldLine, UserProfile p) throws javax.mail.internet.AddressException {
    StringBuffer returnValue = new StringBuffer();
    InternetAddress[] addresses = InternetAddress.parse(oldLine, false);
    for (int i = 0; i < addresses.length; i++) {
      String currentAddress = addresses[i].getAddress();
      if (currentAddress.lastIndexOf('@') < 0) {
	currentAddress = currentAddress + "@" + p.getDefaultDomain();
	addresses[i].setAddress(currentAddress);
      }

      returnValue.append(addresses[i].toString());
      if (i+1 < addresses.length)
	returnValue.append(", ");
    }

    return returnValue.toString();
  }

  /**
   * Saves the NewMessageInfo to the sentFolder associated with the 
   * given Profile, if any.
   */
  public void saveToSentFolder(UserProfile profile) {
    final FolderInfo sentFolder = profile.getSentFolder();
    if (sentFolder != null) {
      try {
	final Message newMessage = new MimeMessage((MimeMessage) getMessage());
	
	sentFolder.getFolderThread().addToQueue(new net.suberic.util.thread.ActionWrapper(new javax.swing.AbstractAction() {
	    public void actionPerformed(java.awt.event.ActionEvent actionEvent) {
	      if (sentFolder.getFolder() != null) {
		try {
		  newMessage.setSentDate(java.util.Calendar.getInstance().getTime());
		  sentFolder.getFolder().appendMessages(new Message[] {newMessage});
		} catch (MessagingException me) {
		  Pooka.getUIFactory().showError(Pooka.getProperty("Error.SaveFile.toSentFolder", "Error saving file to sent folder."), Pooka.getProperty("error.SaveFile.toSentFolder.title", "Error storing message."));
		  
		}
	      }
	    }
	  }, sentFolder.getFolderThread()), new java.awt.event.ActionEvent(this, 1, "message-send"));
      } catch (MessagingException me) {
	Pooka.getUIFactory().showError(Pooka.getProperty("Error.SaveFile.toSentFolder", "Error saving file to sent folder."), Pooka.getProperty("error.SaveFile.toSentFolder.title", "Error storing message."));

      }
    }
  }

  /**
   * Adds an attachment to this message.
   */
  public void addAttachment(Attachment attachment) {
    attachments.getAttachments().add(attachment);
  }
  
  /**
   * Removes an attachment from this message.
   */
  public int removeAttachment(Attachment part) {
    if (attachments != null) {
      int index = attachments.getAttachments().indexOf(part);	
      attachments.getAttachments().remove(index);
      return index;
    }
    
    return -1;
  }
  
  /**
   * Attaches the given File to the message.
   */
  public void attachFile(File f) throws MessagingException {
    // borrowing liberally from ICEMail here.
    
    MimeBodyPart mbp = new MimeBodyPart();
    
    FileDataSource fds = new FileDataSource(f);
    
    DataHandler dh = new DataHandler(fds);
    
    mbp.setFileName(f.getName());
    
    if (Pooka.getMimeTypesMap().getContentType(f).startsWith("text"))
      mbp.setDisposition(Part.ATTACHMENT);
    else
      mbp.setDisposition(Part.INLINE);
    
    mbp.setDescription(f.getName());
    
    mbp.setDataHandler( dh );
    
    String type = dh.getContentType();
    
    mbp.setHeader("Content-Type", type);
    
    addAttachment(new MBPAttachment(mbp));
  }
  
  /**
   * Returns the given header on the wrapped Message.
   */
  public String getHeader(String headerName, String delimeter) throws MessagingException {
    return ((MimeMessage)getMessage()).getHeader(headerName, delimeter);
  }
  
  /**
   * Gets the text part of the wrapped message.
   */
  public String getTextPart(boolean showFullHeaders) {
    try {
      return (String) message.getContent();
    } catch (java.io.IOException ioe) {
      // since this is a NewMessageInfo, there really shouldn't be an
      // IOException
      return null;
    } catch (MessagingException me) {
	    // since this is a NewMessageInfo, there really shouldn't be a
      // MessagingException
      return null;
    }
  }
  
  /**
   * Marks the message as a draft message and then saves it to the outbox
   * folder given.
   */
  public void saveDraft(FolderInfo outboxFolder) throws MessagingException {
    getMessage().setFlag(Flags.Flag.DRAFT, true);

    outboxFolder.appendMessages(new MessageInfo[] { this });

  }
}