package net.suberic.pooka.gui;
import net.suberic.pooka.*;
import javax.mail.event.*;

public interface FolderDisplayUI extends UserProfileContainer, ErrorHandler, ActionContainer, net.suberic.pooka.event.MessageLoadedListener, MessageCountListener, MessageChangedListener, ConnectionListener {
    
    public void openFolderDisplay();
    
    public void closeFolderDisplay();

    public FolderInfo getFolderInfo();

    public void setEnabled(boolean newValue);

    public void setBusy(boolean newValue);

    public String showInputDialog(String inputMessage, String title);

    public int selectMessage(int messageNumber);

    public int selectNextMessage();

    public int selectPreviousMessage();

    /*
    public void resetFolderTableModel(FolderTableModel ftm);

    public void showStatusMessage(String msg);

    public void clearStatusMessage();
    */
}
