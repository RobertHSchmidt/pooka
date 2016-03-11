package net.suberic.pooka.cache;

import javax.mail.*;

import net.suberic.pooka.Pooka;

public class StoreProxy extends javax.mail.Store {
  public StoreProxy(Session s, URLName un) {
    super(s, un);
  }

  public  Folder getDefaultFolder() throws MessagingException {
    throw new MessagingException(Pooka.getProperty("error.folderNotAvailable", "Folder not loaded."));
  }
  public  Folder getFolder(String name) throws MessagingException  {
    throw new MessagingException(Pooka.getProperty("error.folderNotAvailable", "Folder not loaded."));
  }
      public  Folder getFolder(URLName url) throws MessagingException {
        throw new MessagingException(Pooka.getProperty("error.folderNotAvailable", "Folder not loaded."));
      }
}
