package net.suberic.pooka.vcard;
import net.suberic.pooka.*;
import javax.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.io.*;

/**
 * An AddressBook which uses Vcards. 
 */
public class VcardAddressBook implements AddressBook, AddressMatcher {

  String fileName;
  Vcard[] orderedList;
  ArrayList arrayList = new ArrayList();

  int sortingMethod;
  
  /**
   * Creates a new VcardAddressBook from the given Vcard.  It uses the
   * file represented by the given pFileName as the source for the 
   * addresses.
   */
    public VcardAddressBook(String pFileName) throws java.text.ParseException, java.io.IOException {
      fileName = pFileName;
      loadAddressBook();
    }
  
  /**
   * Loads the AddressBook from the saved filename.
   */
  protected void loadAddressBook() throws java.text.ParseException, java.io.IOException {
    File f = new File(fileName);
    if (f.exists()) {
      BufferedReader reader = new BufferedReader(new FileReader(f));
      for(Vcard newCard = Vcard.parse(reader); newCard != null; newCard = Vcard.parse(reader)) {
	insertIntoList(newCard);
      }
    }
    
    sortList();
  }
  
  /**
   * Inserts the given Vcard into the ordered list.
   */
  protected void insertIntoList(Vcard newCard) {
    arrayList.add(newCard);
  }
  
  /**
   * Adds the given Vcard to the address book.
   */
  public void addAddress(Vcard newCard) {
    Vcard[] newList = new Vcard[orderedList.length + 1];
    int searchResult = java.util.Arrays.binarySearch(orderedList, newCard);
    if (searchResult < 0) {
      int insertLocation = (searchResult + 1) * -1;
      if (insertLocation > 0)
	System.arraycopy(orderedList, 0, newList, 0, insertLocation);
      newList[insertLocation] = newCard;
      if (orderedList.length - insertLocation > 0)
	System.arraycopy(orderedList, insertLocation, newList, insertLocation + 1, orderedList.length - insertLocation); 

      orderedList = newList;
    }
  }
  
  /**
   * Sorts the list.
   */
  protected void sortList() {
    orderedList = new Vcard[arrayList.size()];
    orderedList = (Vcard[]) arrayList.toArray(orderedList);
    java.util.Arrays.sort(orderedList);
  }
  
  /**
   * Gets the AddressMatcher for this AddressBook.
   */
  public AddressMatcher getAddressMatcher() {
    return this;
  }
  
  /**
   * Returns all of the InternetAddresses which match the given String.
   */
  public InternetAddress[] match(String matchString) {
    int value = java.util.Arrays.binarySearch(orderedList, matchString);
    // now get all the matches, if any.
    if (value < 0) {
      return new InternetAddress[0];
    }

    if (orderedList[value].compareTo(matchString) == 0) {
      // get all the matches.
      int minimum = value;
      while (minimum > 0 && (orderedList[minimum - 1].compareTo(matchString) == 0))
	minimum--;


      int maximum = value;
      while (maximum < orderedList.length -1 && (orderedList[maximum + 1].compareTo(matchString) == 0))
	maximum++;

      InternetAddress[] returnValue = new InternetAddress[maximum - minimum + 1];

      for(int i = 0; i < returnValue.length; i++) {
	returnValue[i] = orderedList[minimum + i].getAddress();
      }

      return returnValue;
    } else {
      return new InternetAddress[0];
    }
    //return binarySearch(matchString, 0, orderedList.size());
  }
  
  /**
   * Returns all of the InternetAddresses whose FirstName matches the given 
   * String.
   */
  public InternetAddress[] matchFirstName(String matchString) {
    return match(matchString);
  }
  
  /**
   * Returns all of the InternetAddresses whose LastName matches the given 
   * String.
   */
  public InternetAddress[] matchLastName(String matchString) {
    return match(matchString);
  }
  
  /**
   * Returns all of the InternetAddresses whose email addresses match the
   * given String.
   */
  public InternetAddress[] matchEmailAddress(String matchString) {
    return match(matchString);
  }
  
  /**
   * Returns the InternetAddress which follows the given String alphabetically.
   */
  public InternetAddress getNextMatch(String matchString) {
    int value = java.util.Arrays.binarySearch(orderedList, matchString);
    // now get all the matches, if any.
    if (value < 0) {
      value = (value + 1) * -1;
    } else {
      // if we got a match, we want to return the next one.
      value = value + 1;
    }
    if (value >= orderedList.length) {
      return orderedList[orderedList.length - 1].getAddress();
    } else {
      return orderedList[value].getAddress();
    }
  }
  
  /**
   * Returns the InternetAddress which precedes the given String 
   * alphabetically.
   */
  public InternetAddress getPreviousMatch(String matchString) {
    int value = java.util.Arrays.binarySearch(orderedList, matchString);
    // now get all the matches, if any.
    if (value < 0) {
      value = (value + 2) * -1;
    } else {
      // if we got a match, we want to return the previous one.
      value = value - 1;
    }
    if (value < 0) {
      return orderedList[0].getAddress();
    } else {
      return orderedList[value].getAddress();
    }
  }
  
}