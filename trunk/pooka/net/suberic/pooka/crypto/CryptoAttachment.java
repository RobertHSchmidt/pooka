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

  boolean encrypted = false;

  boolean signed = false;

  BodyPart decryptedBodyPart = null;
  

  /**
   * Creates a CryptoAttachment out of a MimeBodyPart.
   */
  public CryptoAttachment(MimeBodyPart mbp) throws MessagingException {
    super(mbp);
    ContentType ct = new ContentType(mbp.getContentType());
    if (ct.getSubType().equalsIgnoreCase("encrypted"))
      encrypted = true;
    else if (ct.getSubType().equalsIgnoreCase("signed"))
      signed = true;

  }
  
  /**
   * Returns if the signature matches.
   */
  public boolean checkSignature() 
    throws MessagingException, EncryptionException, java.io.IOException {
    if (! signed)
      return false;

    PGPMimeEncryptionUtils utils = new PGPMimeEncryptionUtils();
    utils.setPGPProviderImpl(new net.suberic.pooka.crypto.gpg.GPGPGPProviderImpl());

    return utils.checkSignature((MimeMultipart)getContent(), null);
  }

  /**
   * Creates a CryptoAttachment out of a MimeMessage.  This is typically
   * used when the content of a Message is too large to display, and
   * therefore it needs to be treated as an attachment rather than
   * as the text of the Message.
   */
  public CryptoAttachment(MimeMessage msg) throws MessagingException {
    super(msg);
    ContentType ct = new ContentType(msg.getContentType());
    if (ct.getSubType().equalsIgnoreCase("encrypted"))
      encrypted = true;
    else if (ct.getSubType().equalsIgnoreCase("signed"))
      signed = true;
    else if (ct.getPrimaryType().equalsIgnoreCase("application") && ct.getSubType().equalsIgnoreCase("pkcs7-mime")) {
      encrypted = true;
    }
  }

  /**
   * Tries to decrypt this Attachment.
   */
  public BodyPart decryptAttachment(EncryptionUtils utils, EncryptionKey key)
    throws EncryptionException, MessagingException, java.io.IOException {
    
    if (decryptedBodyPart != null)
      return decryptedBodyPart;
    else {
      
      /*
      Object o = super.getDataHandler().getContent();
      if (o instanceof Multipart) {
	decryptedBodyPart = utils.decryptMultipart((Multipart)o, key);
	
	return decryptedBodyPart;
      } else {
	return null;
      }
      */
      MimeBodyPart mbp = new MimeBodyPart(super.getDataHandler().getInputStream());
      decryptedBodyPart = utils.decryptBodyPart(mbp, key);

      return decryptedBodyPart;
    }
    
  }

  // accessor methods.
  
  /**
   * Returns the decrypted version of the wrapped attachment, or null 
   * if the attachment is either not actually encrypted, or cannot be 
   * decrypted.
   */
  public BodyPart getDecryptedBodyPart() 
    throws EncryptionException, MessagingException, java.io.IOException {
    if (decryptedBodyPart != null)
      return decryptedBodyPart;

    throw new EncryptionException("not decrypted yet.");
  }

  /**
   * Returns the DataHandler for this Attachment.
   */
  public DataHandler getDataHandler() {
    if (encrypted) {
      try {
	BodyPart bp = getDecryptedBodyPart();
	
	if (bp != null) {
	  return bp.getDataHandler();
	}
      } catch (Exception e) {
	e.printStackTrace();
      }
    }

    return super.getDataHandler();
  }


  /**
   * Returns the MimeType.
   */
  public ContentType getMimeType() {
    if (encrypted && decryptedBodyPart != null) {
      try {
	BodyPart bp = getDecryptedBodyPart();
	return new ContentType(bp.getContentType());
      } catch (Exception e) {
	//e.printStackTrace();
      }
    }

    return super.getMimeType();
  }
  
}