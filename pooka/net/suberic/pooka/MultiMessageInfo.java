package net.suberic.pooka;
import javax.mail.*;

/**
 * This represents a bundle of MessageInfos.
 */
public class MultiMessageInfo extends MessageInfo {

    MessageInfo[] messages;

    /**
     * Creates a new MultiMessageInfo for the given newMessageInfos.
     */
    public MultiMessageInfo(MessageInfo[] newMessageInfos) {
	messages = newMessageInfos;
    }

    /**
     * Creates a new MultiMessageInfo for the given newMessageInfos,
     * where all the given MessageInfos are from the FolderInfo
     * newFolder.
     */
    public MultiMessageInfo(MessageInfo[] newMessageInfos, FolderInfo newFolder) {
	messages = newMessageInfos;
	folderInfo = newFolder;
    }


    /**
     * This implementation just throws an exception, since this is not
     * allowed on multiple messages.
     *
     * @overrides flagIsSet in MessageInfo
     */
    public boolean flagIsSet(String flagName) throws MessagingException {
	throw new MessagingException(Pooka.getProperty("error.MultiMessage.operationNotAllowed", "This operation is not allowed on multiple messages."));
    }

    /**
     * This implementation just throws an exception, since this is not
     * allowed on multiple messages.
     *
     * @overrides getFlags() in MessageInfo
     */
    public Flags getFlags() throws MessagingException {
	throw new MessagingException(Pooka.getProperty("error.MultiMessage.operationNotAllowed", "This operation is not allowed on multiple messages."));
    }

    /**
     * This implementation just throws an exception, since this is not
     * allowed on multiple messages.
     *
     * @overrides getMessageProperty() in MessageInfo
     */
    public Object getMessageProperty(String prop) throws MessagingException {
	throw new MessagingException(Pooka.getProperty("error.MultiMessage.operationNotAllowed", "This operation is not allowed on multiple messages."));
    }

    /**
     * Moves the Message into the target Folder.
     */
    public void moveMessage(FolderInfo targetFolder, boolean expunge) throws MessagingException {
	if (folderInfo != null) {
	    folderInfo.copyMessages(messages, targetFolder);
	    folderInfo.setFlags(messages, new Flags(Flags.Flag.DELETED), true);
	    if (expunge)
		folderInfo.expunge();
	} else {
	    for (int i = 0; i < messages.length; i++)
		messages[i].moveMessage(targetFolder, expunge);
	}
    }

    /**
     * deletes all the messages in the MultiMessageInfo.
     */
    public void deleteMessage(boolean expunge) throws MessagingException {
	if (folderInfo != null) {
	    FolderInfo trashFolder = folderInfo.getTrashFolder();
	    if ((folderInfo.useTrashFolder()) && (trashFolder != null) && (trashFolder != folderInfo)) {
		try {
		    moveMessage(trashFolder, expunge);
		} catch (MessagingException me) {
		    throw new NoTrashFolderException(Pooka.getProperty("error.Messsage.DeleteNoTrashFolder", "No trash folder available."),  me);
		}
	    } else {
		remove(expunge);
	    }
	} else {
	    for (int i = 0; i < messages.length; i++)
		messages[i].deleteMessage(expunge);
	    
	}
    }

   /**
     * This actually marks the message as deleted, and, if autoexpunge is
     * set to true, expunges the folder.
     *
     * This should not be called directly; rather, deleteMessage() should
     * be used in order to ensure that the delete is done properly (using
     * trash folders, for instance).  If, however, the deleteMessage() 
     * throws an Exception, it may be necessary to follow up with a call
     * to remove().
     */
    public void remove(boolean autoExpunge) throws MessagingException {
	if (folderInfo != null) {
	    folderInfo.setFlags(messages, new Flags(Flags.Flag.DELETED), true);
	    if (autoExpunge)
		folderInfo.expunge();
	} else {
	    for (int i = 0; i < messages.length; i++)
		messages[i].remove(autoExpunge);
	}
	
    }

    /**
     * This returns the MessageInfo at the given index.
     */
    public MessageInfo getMessageInfo(int index) {
	return messages[index];
    }

    /**
     * This returns the number of Messages wrapped by the MultiMessageInfo.
     */
    public int getMessageCount() {
	return messages.length;
    }

}


