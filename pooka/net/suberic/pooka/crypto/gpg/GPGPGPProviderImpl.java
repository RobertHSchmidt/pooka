package net.suberic.pooka.crypto.gpg;

import net.suberic.pooka.crypto.*;
import net.suberic.pooka.*;

import java.io.*;

/**
 * Something which decrypts PGP streams.
 */
public class GPGPGPProviderImpl implements PGPProviderImpl {

  /**
   * Decrypts a section of text using an EncryptionKey.
   */
  public byte[] decrypt(java.io.InputStream encryptedStream, EncryptionKey key)
    throws EncryptionException {
    GPGEncryptionKey gpgKey = (GPGEncryptionKey) key;

    String alias = null;
    String passphrase = null;

    if (gpgKey != null) {
      alias = gpgKey.getAlias();
      passphrase = gpgKey.getPassphrase();
      if (alias == null || alias.length() < 1) {
	throw new EncryptionException("key has no associated alias.");
      }

      if (passphrase == null || passphrase.length() < 1) {
	throw new EncryptionException("passphrase required for decryption.");
      }
    } else 
      throw new EncryptionException("no key provided.");

    System.err.println("decrypting with alias " + alias + ", passphrase " + passphrase);
    try {
      File outFile = writeStreamToFile(encryptedStream);
      outFile.deleteOnExit();
      
      Process p = Runtime.getRuntime().exec("gpg --passphrase-fd 0 -d " + outFile);

      // we probably need to write the passphrase.

      OutputStream os = p.getOutputStream();

      BufferedWriter processWriter = new BufferedWriter(new OutputStreamWriter(os));

      processWriter.write(passphrase);
      processWriter.newLine();
      processWriter.flush();
      processWriter.close();

      System.err.println("getting input stream.");

      InputStream is = p.getInputStream();
      
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      byte[] bytesRead = new byte[256];
      int numRead = is.read(bytesRead);
      
      while (numRead > -1) {
	baos.write(bytesRead, 0, numRead);
	numRead = is.read(bytesRead);
      }

      System.err.println("got response " + new String(baos.toByteArray()));
      return baos.toByteArray();
      
    } catch (java.io.IOException ioe) {
      throw new EncryptionException(ioe.getMessage());
    }
  }
  
  /**
   * Encrypts a section of text using an EncryptionKey.
   */
  public byte[] encrypt(java.io.InputStream rawStream, EncryptionKey key)
    throws EncryptionException {
    
    GPGEncryptionKey gpgKey = (GPGEncryptionKey) key;

    String alias = gpgKey.getAlias();
    String passphrase = gpgKey.getPassphrase();

    try {
      File outFile = writeStreamToFile(rawStream);

      String[] cmdArray = new String[] {
	"gpg","-a","-r",alias,"--passphrase-fd","0","-e",outFile.toString() 
      };

      //Process p = Runtime.getRuntime().exec("gpg -a -r " + alias + " --passphrase-fd 0 -e " + outFile.toString());
      Process p = Runtime.getRuntime().exec(cmdArray);

      // we probably need to write the passphrase.

      OutputStream os = p.getOutputStream();

      BufferedWriter processWriter = new BufferedWriter(new OutputStreamWriter(os));

      processWriter.write(passphrase);
      processWriter.newLine();
      processWriter.flush();
      processWriter.close();

      try {
	p.waitFor();
      } catch (InterruptedException ie) {
      }

      File inFile = new File(outFile.getAbsolutePath() + ".asc");
      inFile.deleteOnExit();
      InputStream is = new FileInputStream(inFile);
      
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      byte[] bytesRead = new byte[256];
      int numRead = is.read(bytesRead);
      
      while (numRead > -1) {
	baos.write(bytesRead, 0, numRead);
	numRead = is.read(bytesRead);
      }

      System.out.println("read:  ");
      System.out.println(new String(baos.toByteArray()));
      return baos.toByteArray();
      
    } catch (java.io.IOException ioe) {
      ioe.printStackTrace();
      throw new EncryptionException(ioe.getMessage());
    }
  }


  /**
   * Signs a section of text.
   */
  public byte[] sign(InputStream rawStream, EncryptionKey key)
    throws EncryptionException {
    
    GPGEncryptionKey gpgKey = (GPGEncryptionKey) key;
    
    String alias = gpgKey.getAlias();
    String passphrase = gpgKey.getPassphrase();

    try {
      File outFile = writeStreamToFile(rawStream);

      String[] cmdArray = new String[] {
	"gpg","-a","--passphrase-fd","0","-b",outFile.toString() 
      };

      //Process p = Runtime.getRuntime().exec("gpg -a -r '" + alias + "' --passphrase-fd 0 -b " + outFile);
      Process p = Runtime.getRuntime().exec(cmdArray);

      /*
      InputStream errorStream = p.getErrorStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
      String errorString = reader.readLine();
      while (errorString != null) {
	System.err.println(errorString);
	errorString = reader.readLine();
      }
      */
      // we probably need to write the passphrase.

      OutputStream os = p.getOutputStream();

      BufferedWriter processWriter = new BufferedWriter(new OutputStreamWriter(os));
      
      processWriter.write(passphrase);
      processWriter.newLine();
      processWriter.flush();
      processWriter.close();

      try {
	p.waitFor();
      } catch (InterruptedException ie) {
      }

      File inFile = new File(outFile.getAbsolutePath() + ".asc");
      inFile.deleteOnExit();
      InputStream is = new FileInputStream(inFile);
      
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      byte[] bytesRead = new byte[256];
      int numRead = is.read(bytesRead);
      
      while (numRead > -1) {
	baos.write(bytesRead, 0, numRead);
	numRead = is.read(bytesRead);
      }

      System.out.println("read:  ");
      System.out.println(new String(baos.toByteArray()));
      return baos.toByteArray();
      
    } catch (java.io.IOException ioe) {
      ioe.printStackTrace();
      throw new EncryptionException(ioe.getMessage());
    }
  }

  /**
   * Checks a signature against a section of text.
   */
  public boolean checkSignature(InputStream rawStream, 
				byte[] signature, EncryptionKey key)
    throws EncryptionException {
    
    try {
      File contentFile = writeStreamToFile(rawStream);
      
      Process p = Runtime.getRuntime().exec("gpg --verify " + contentFile.getAbsolutePath());
      
      // we probably need to write the passphrase.
      
      OutputStream os = p.getOutputStream();
      
      /*
	BufferedWriter processWriter = new BufferedWriter(new OutputStreamWriter(os));
	
	processWriter.write(signature);
	processWriter.newLine();
	processWriter.flush();
	processWriter.close();
	
      */
      os.write(signature);
      os.close();
      
      try {
	p.waitFor();
      } catch (InterruptedException ie) {
      }
      
      System.out.println("p.exitValue() = " + p.exitValue());
      
      return (p.exitValue() == 0);
    } catch (java.io.IOException ioe) {
      throw new EncryptionException(ioe.getMessage());
    }
    
  }

  /**
   * Writes an input stream to a temporary File.
   */
  File writeStreamToFile(InputStream encryptedStream) throws IOException {
    File tmpFile = File.createTempFile("gpg", ".txt");
    tmpFile.deleteOnExit();
    FileOutputStream fos = new FileOutputStream(tmpFile);

    byte[] bytesRead = new byte[256];
    int numRead = encryptedStream.read(bytesRead);
    
    while (numRead > -1) {
      fos.write(bytesRead, 0, numRead);
      numRead = encryptedStream.read(bytesRead);
    }
      
    fos.flush();
    fos.close();

    return tmpFile;
  }

  /**
   * Returns a KeyStore provider.
   */
  public EncryptionKeyManager createKeyManager() {
    return new GPGEncryptionKeyManager();
  }

  /**
   * Returns a KeyStore provider.
   */
  public EncryptionKeyManager createKeyManager(java.io.InputStream inputStream, char[] password) throws java.io.IOException {
    GPGEncryptionKeyManager mgr = new GPGEncryptionKeyManager();
    mgr.load(inputStream, password);
    return mgr;
  }
}