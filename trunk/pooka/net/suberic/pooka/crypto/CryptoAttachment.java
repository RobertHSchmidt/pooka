package net.suberic.pooka.crypto;

import net.suberic.pooka.*;
import javax.mail.internet.*;
import javax.mail.*;
import javax.activation.DataHandler;

import java.io.*;

/**
 * An encrypted attachment.
 */
public class CryptoAttachment extends Attachment {

  boolean parsed = false;

  BodyPart decryptedBodyPart = null;
  

  /**
   * Creates a CryptoAttachment out of a MimeBodyPart.
   */
  public CryptoAttachment(MimeBodyPart mbp) throws MessagingException {
    super(mbp);
  }
  
  /**
   * Creates a CryptoAttachment out of a MimeMessage.  This is typically
   * used when the content of a Message is too large to display, and
   * therefore it needs to be treated as an attachment rather than
   * as the text of the Message.
   */
  public CryptoAttachment(MimeMessage msg) throws MessagingException {
    super(msg);
  }

  // accessor methods.
  
  protected BodyPart getDecryptedBodyPart() 
    throws EncryptionException, MessagingException, java.io.IOException {
    if (decryptedBodyPart != null)
      return decryptedBodyPart;
    else {
      // we should always be wrapping a Multipart object here.
      Object o = super.getDataHandler().getContent();
      if (o instanceof Multipart) {
	PGPMimeEncryptionUtils utils = new PGPMimeEncryptionUtils();
	utils.setPGPProviderImpl(new net.suberic.pooka.crypto.gpg.GPGPGPProviderImpl());
	decryptedBodyPart = utils.decryptMultipart((Multipart)o, new net.suberic.pooka.crypto.gpg.GPGEncryptionKey("allen", "biteme"));


	return decryptedBodyPart;
      } else {
	return null;
      }
    }
  }

  /**
   * Returns the DataHandler for this Attachment.
   */
  public DataHandler getDataHandler() {
    try {
      BodyPart bp = getDecryptedBodyPart();
      
      if (bp != null) {
	return bp.getDataHandler();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return super.getDataHandler();
  }


  /**
   * Returns the MimeType.
   */
  public ContentType getMimeType() {
    try {
      BodyPart bp = getDecryptedBodyPart();
      System.err.println("decryptedBodyPart.getContentType() = " + bp.getContentType());
      return new ContentType(bp.getContentType());
    } catch (Exception e) {
      e.printStackTrace();
    }

    return super.getMimeType();
  }
  
}
