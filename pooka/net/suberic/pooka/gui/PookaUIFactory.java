package net.suberic.pooka.gui;

public interface PookaUIFactory extends ErrorHandler {

    /**
     * Creates an appropriate MessageUI object for the given MessageProxy.
     */
    public MessageUI createMessageUI(MessageProxy mp) throws javax.mail.MessagingException;

    /**
     * Creates an appropriate FolderDisplayUI object for the given
     * FolderInfo.
     */
    public FolderDisplayUI createFolderDisplayUI(net.suberic.pooka.FolderInfo fi);

    /**
     * Creates a ContentPanel which will be used to show messages and folders.
     */
    public ContentPanel createContentPanel();

    /**
     * Shows an Editor Window with the given title, which allows the user
     * to edit the values in the properties Vector.
     */
    public void showEditorWindow(String title, java.util.Vector properties);

    /**
     * Shows an Editor Window with the given title, which allows the user
     * to edit the values in the properties Vector.  The given properties
     * will be shown according to the values in the templates Vector.
     * Note that there should be an entry in the templates Vector for
     * each entry in the properties Vector.
     */
    public void showEditorWindow(String title, java.util.Vector properties, java.util.Vector templates);

    /**
     * Shows an Editor Window with the given title, which allows the user
     * to edit the given property.
     */
    public void showEditorWindow(String title, String property);

    /**
     * Shows an Editor Window with the given title, which allows the user
     * to edit the given property, which is in turn defined by the 
     * given template.
     */
    public void showEditorWindow(String title, String property, String template);

    /**
     * Returns the PropertyEditorFactory used by this component.
     */
    public net.suberic.util.gui.PropertyEditorFactory getEditorFactory();

    /**
     * Shows a Confirm dialog.
     */
    public int showConfirmDialog(String message, String title, int type);

   /**
     * Shows a Confirm dialog with the given Object[] as the Message.
     */
    public int showConfirmDialog(Object[] messageComponents, String title, int type);

    /**
     * This shows an Input window.
     */
    public String showInputDialog(String inputMessage, String title);
    
    /**
     * Shows an Input window.
     */
    public String showInputDialog(Object[] inputPanels, String title);

    /**
     * Shows a status message.
     */
    public void showStatusMessage(String newMessage);

    /**
     * Clears the main status message panel.
     */
    public void clearStatus();

    /**
     * Shows a SearchForm with the given FolderInfos selected from the list
     * of the given allowedValues.
     */
    public void showSearchForm(net.suberic.pooka.FolderInfo[] selectedFolders, java.util.Vector allowedValues); 

    /**
     * Shows a SearchForm with the given FolderInfos selected.  The allowed
     * values will be the list of all available Folders.
     */
    public void showSearchForm(net.suberic.pooka.FolderInfo[] selectedFolders); 

}