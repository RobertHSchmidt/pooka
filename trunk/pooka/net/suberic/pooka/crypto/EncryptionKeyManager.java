package net.suberic.pooka.crypto;

import net.suberic.pooka.*;
import net.suberic.util.*;

import java.security.*;
import java.io.*;
import java.util.*;

/**
 * This manages a set of Encryption keys for use with PGP or S/MIME.
 */
public interface EncryptionKeyManager {

  /*
   * Loads this KeyStore from the given input stream.
   *
   * <p>If a password is given, it is used to check the integrity of the
   * keystore data. Otherwise, the integrity of the keystore is not checked.
   *
   * <p>In order to create an empty keystore, or if the keystore cannot
   * be initialized from a stream (e.g., because it is stored on a hardware
   * token device), you pass <code>null</code>
   * as the <code>stream</code> argument.
   *
   * <p> Note that if this KeyStore has already been loaded, it is
   * reinitialized and loaded again from the given input stream.
   *
   * @param stream the input stream from which the keystore is loaded, or
   * null if an empty keystore is to be created.
   * @param password the (optional) password used to check the integrity of
   * the keystore.
   *
   * @exception IOException if there is an I/O or format problem with the
   * keystore data
   * @exception NoSuchAlgorithmException if the algorithm used to check
   * the integrity of the keystore cannot be found
   */
  public void load(InputStream stream, char[] password)
    throws IOException, EncryptionException;
  
  /**
   * Stores this keystore to the given output stream, and protects its
   * integrity with the given password.
   *
   * @param stream the output stream to which this keystore is written.
   * @param password the password to generate the keystore integrity check
   *
   * @exception KeyStoreException if the keystore has not been initialized
   * (loaded).
   * @exception IOException if there was an I/O problem with data
   * @exception NoSuchAlgorithmException if the appropriate data integrity
   * algorithm could not be found
   */
  public void store(OutputStream stream, char[] password)
    throws IOException, EncryptionException;
  
  /**
   * Retrieves the number of entries in this keystore.
   *
   * @return the number of entries in this keystore
   *
   * @exception KeyStoreException if the keystore has not been initialized
   * (loaded).
   */
  public int size()
    throws KeyStoreException;
  
  /**
   * Returns the key associated with the given alias, using the given
   * password to recover it.
   *
   * @param alias the alias name
   * @param password the password for recovering the key
   *
   * @return the requested key, or null if the given alias does not exist
   * or does not identify a <i>key entry</i>.
   *
   * @exception KeyStoreException if the keystore has not been initialized
   * (loaded).
   * @exception NoSuchAlgorithmException if the algorithm for recovering the
   * key cannot be found
   * @exception UnrecoverableKeyException if the key cannot be recovered
   * (e.g., the given password is wrong).
   */
  public EncryptionKey getPublicKey(String alias)
    throws KeyStoreException;

  /**
   * Returns the key associated with the given alias, using the given
   * password to recover it.
   *
   * @param alias the alias name
   * @param password the password for recovering the key
   *
   * @return the requested key, or null if the given alias does not exist
   * or does not identify a <i>key entry</i>.
   *
   * @exception KeyStoreException if the keystore has not been initialized
   * (loaded).
   * @exception NoSuchAlgorithmException if the algorithm for recovering the
   * key cannot be found
   * @exception UnrecoverableKeyException if the key cannot be recovered
   * (e.g., the given password is wrong).
   */
  public EncryptionKey getPrivateKey(String alias, char[] password)
    throws KeyStoreException, EncryptionException;
  
  
  /**
   * Assigns the given key to the given alias, protecting it with the given
   * password.
   *
   * <p>If the given key is of type <code>java.security.PrivateKey</code>,
   * it must be accompanied by a certificate chain certifying the
   * corresponding public key.
   *
   * <p>If the given alias already exists, the keystore information
   * associated with it is overridden by the given key (and possibly
   * certificate chain).
   *
   * @param alias the alias name
   * @param key the key to be associated with the alias
   * @param password the password to protect the key
   * @param chain the certificate chain for the corresponding public
   * key (only required if the given key is of type
   * <code>java.security.PrivateKey</code>).
   *
   * @exception KeyStoreException if the keystore has not been initialized
   * (loaded), the given key cannot be protected, or this operation fails
   * for some other reason
   */
  public void setPublicKeyEntry(String alias, EncryptionKey key)
    throws KeyStoreException;
  
  /**
   * Assigns the given key to the given alias, protecting it with the given
   * password.
   *
   * <p>If the given key is of type <code>java.security.PrivateKey</code>,
   * it must be accompanied by a certificate chain certifying the
   * corresponding public key.
   *
   * <p>If the given alias already exists, the keystore information
   * associated with it is overridden by the given key (and possibly
   * certificate chain).
   *
   * @param alias the alias name
   * @param key the key to be associated with the alias
   * @param password the password to protect the key
   * @param chain the certificate chain for the corresponding public
   * key (only required if the given key is of type
   * <code>java.security.PrivateKey</code>).
   *
   * @exception KeyStoreException if the keystore has not been initialized
   * (loaded), the given key cannot be protected, or this operation fails
   * for some other reason
   */
  public void setPrivateKeyEntry(String alias, EncryptionKey key, char[] password)
    throws KeyStoreException;
  
  /**
   * Deletes the entry identified by the given alias from this keystore.
   *
   * @param alias the alias name
   *
   * @exception KeyStoreException if the keystore has not been initialized,
   * or if the entry cannot be removed.
   */
  public void deletePublicKeyEntry(String alias)
    throws KeyStoreException;
  
  /**
   * Deletes the entry identified by the given alias from this keystore.
   *
   * @param alias the alias name
   *
   * @exception KeyStoreException if the keystore has not been initialized,
   * or if the entry cannot be removed.
   */
  public void deletePrivateKeyEntry(String alias, char[] password)
    throws KeyStoreException;
  
  /**
   * Lists all the alias names of this keystore.
   *
   * @return set of the alias names
   *
   * @exception KeyStoreException if the keystore has not been initialized
   * (loaded).
   */
  public Set publicKeyAliases()
    throws KeyStoreException;

  /**
   * Lists all the alias names of this keystore.
   *
   * @return set of the alias names
   *
   * @exception KeyStoreException if the keystore has not been initialized
   * (loaded).
   */
  public Set privateKeyAliases()
    throws KeyStoreException;
  
  
  /**
   * Checks if the given alias exists in this keystore.
   *
   * @param alias the alias name
   *
   * @return true if the alias exists, false otherwise
   *
   * @exception KeyStoreException if the keystore has not been initialized
   * (loaded).
   */
  public boolean containsPublicKeyAlias(String alias)
    throws KeyStoreException;

  /**
   * Checks if the given alias exists in this keystore.
   *
   * @param alias the alias name
   *
   * @return true if the alias exists, false otherwise
   *
   * @exception KeyStoreException if the keystore has not been initialized
   * (loaded).
   */
  public boolean containsPrivateKeyAlias(String alias)
    throws KeyStoreException;
  
}
