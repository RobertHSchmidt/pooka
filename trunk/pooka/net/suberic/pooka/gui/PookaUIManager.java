package net.suberic.pooka.gui;

import javax.swing.*;
import java.awt.Font;
import java.awt.Color;
import java.util.*;

import net.suberic.util.*;
import net.suberic.pooka.Pooka;

/**
 * A class that controls the colors and fonts for Pooka.
 */
public class PookaUIManager implements ValueChangeListener {
  String[] resourceStrings = new String[] {
    "font",
    "background",
    "foreground",
    "disabledText",
    "select",
    "focus"
  };

  String[] resourceNames = new String[] {
    "Button",
    "ToggleButton",
    "RadioButtonCheckBox",
    "ColorChooser",
    "ComboBox",
    "Label",
    "List",
    "MenuBar",
    "MenuItem",
    "RadioButtonMenuItem",
    "CheckBoxMeuItem",
    "Menu",
    "PopupMenu",
    "OptionPane",
    "Panel",
    "ProgressBar",
    "ScrollPane",
    "Viewport",
    "TabbedPane",
    "Table",
    "TableHeader",
    "TextField",
    "PasswordField",
    "TextArea",
    "TextPane",
    "EditorPane",
    "TitledBorder",
    "ToolBar",
    "ToolTip",
    "Tree"
  };

  boolean updateUI = false;

  /**
   * Creates a PookaUIManager.
   */
  public PookaUIManager() {
    for (int i = 0; i < resourceNames.length; i++) 
      updateResource(resourceNames[i]);

    Pooka.getResources().addValueChangeListener(this, "Pooka.uiConfig.*");
  }

  /**
   * Updates the given resource.
   */
  private void updateResource(String resource) {
    if (resource != null && resource.length() > 0) {
      if (resource.equalsIgnoreCase("messagePanel")) {
	ContentPanel cp = Pooka.getMainPanel().getContentPanel();
	if (cp instanceof MessagePanel) {
	  ((MessagePanel) cp).configureInterfaceStyle();
	}
      } else if (resource.equalsIgnoreCase("folderPanel")) {
	Pooka.getMainPanel().getFolderPanel().configureInterfaceStyle();
      } else if (resource.equalsIgnoreCase("messageWindow")) {
      } else if (resource.equalsIgnoreCase("newMessageWindow")) {
      } else if (resource.equalsIgnoreCase("folderTable")) {
      } else {
	// if it's a system resource, then we set the values.

	for (int i = 0; i < resourceStrings.length; i++) {
	  String isEnabled = Pooka.getProperty("Pooka.uiConfig." + resource + "." + resourceStrings[i] + "._enabled", "false");
	  if (isEnabled.equalsIgnoreCase("true")) {
	    if (resourceStrings[i].equalsIgnoreCase("font")) {
	      applyFont(resource + "." + resourceStrings[i], Pooka.getProperty("Pooka.uiConfig." + resource + "." + resourceStrings[i], ""));
	    } else {
	      applyColor(resource + "." + resourceStrings[i], Pooka.getProperty("Pooka.uiConfig." + resource + "." + resourceStrings[i] + ".rgb", ""));
	    }
	  }
	}

	//and, what the hell, we also update the UI.
	updateUI = true;
	SwingUtilities.invokeLater(new Runnable() {
	    public void run() {
	      if (updateUI) {
		if (Pooka.getMainPanel() != null)
		  javax.swing.SwingUtilities.updateComponentTreeUI(Pooka.getMainPanel());
	      }
	    }
	  });
      }
    
    }
  }

  /**
   * Applies the given font to the given resource.
   */
  private void applyFont(String resource, String fontString) {
    Font newFont = Font.decode(fontString);
    UIManager.put(resource,newFont);
  }

  /**
   * Applies the given color to the given resource.
   */
  private void applyColor(String resource, String rgbString) {
    try {
      int rgbValue = Integer.parseInt(rgbString);
      Color c = new Color(rgbValue);
      UIManager.put(resource, c);
    } catch (Exception e) {
      System.out.println("caught exception setting color:  " + e);
      e.printStackTrace();
    }
  }
      
  /**
   * Called when one of the Pooka ui values changes.
   */
  public void valueChanged(String changedValue) {
    // the changed value is going to be Pooka.uiConfig.something...  see
    // which top-level component has changed and just redo it.
    
    String resource = changedValue.substring(15, changedValue.indexOf('.', 15));
    updateResource(resource);
  }

  /**
   * Returns a UIStyleDefinition for the MessagePanel.
   */
  public HashMap getMessagePanelStyle() {
    return createDefinitionForProperty("Pooka.uiConfig.messagePanel");
  }

  /**
   * Returns a UIStyleDefinition for the folder panel.
   */
  public HashMap getFolderPanelStyle() {
    return createDefinitionForProperty("Pooka.uiConfig.folderPanel");
  }

  /**
   * Returns a UIStyleDefinition for the given MessageUI.
   */
  public HashMap getMessageWindowStyle(MessageUI ui) {

    // not the most efficient method, but it should work.
    net.suberic.pooka.FolderInfo fi = ui.getMessageProxy().getMessageInfo().getFolderInfo();
    net.suberic.pooka.StoreInfo si = fi.getParentStore();

    HashMap defaultValues= createDefinitionForProperty("Pooka.uiConfig.messageWindow");

    HashMap storeValues = createDefinitionForProperty(si.getStoreProperty() + ".uiConfig.messageWindow");
    HashMap folderValues = createDefinitionForProperty(fi.getFolderProperty() + ".uiConfig.messageWindow");
    
    HashMap returnValue = overrideStyle(defaultValues, storeValues);

    returnValue = overrideStyle(returnValue, folderValues);

    return returnValue;
  }

  /**
   * Returns a UIStyleDefinition for the given NewMessageUI.
   */
  public HashMap getNewMessageWindowStyle(NewMessageUI ui) {
    // not the most efficient method, but it should work.
    net.suberic.pooka.UserProfile pr = ui.getSelectedProfile();

    HashMap defaultValues= createDefinitionForProperty("Pooka.uiConfig.newMessageWindow");
    HashMap profileValues = createDefinitionForProperty(pr.getUserProperty() + ".uiConfig.newMessageWindow");
    
    HashMap returnValue = overrideStyle(defaultValues, profileValues);
    return returnValue;
  }

  /**
   * Returns a UIStyleDefinition for the given FolderDisplayUI.
   */
  public HashMap getFolderDisplayStyle(FolderDisplayUI ui) {
    // not the most efficient method, but it should work.
    net.suberic.pooka.FolderInfo fi = ui.getFolderInfo();
    net.suberic.pooka.StoreInfo si = fi.getParentStore();

    HashMap defaultValues= createDefinitionForProperty("Pooka.uiConfig.folderTable");
    HashMap storeValues = createDefinitionForProperty(si.getStoreProperty() + ".uiConfig.folderTable");
    HashMap folderValues = createDefinitionForProperty(fi.getFolderProperty() + ".uiConfig.folderTable");
    
    HashMap returnValue = overrideStyle(defaultValues, storeValues);
    returnValue = overrideStyle(returnValue, folderValues);
    return returnValue;
  }

  /**
   * Overrides the values in defaultMap with the values in overrideMap.
   */
  public HashMap overrideStyle(HashMap defaultMap, HashMap overrideMap) {
    HashMap returnValue = new HashMap(defaultMap);
    Set overrideSet = overrideMap.keySet();
    Iterator iter = overrideSet.iterator();
    while (iter.hasNext()) {
      Object key = iter.next();
      returnValue.put(key, overrideMap.get(key));
    }

    return returnValue;
  }

  /**
   * Creates a HashMap for the given UI resources.
   */
  public HashMap createDefinitionForProperty(String property) {
    HashMap returnValue = new HashMap();

    for (int i = 0; i < resourceStrings.length; i++) {
      String isEnabled = Pooka.getProperty(property + "." + resourceStrings[i] + "._enabled", "false");
      if (isEnabled.equalsIgnoreCase("true")) {
	if (resourceStrings[i].equalsIgnoreCase("font")) {
	  returnValue.put(resourceStrings[i], Font.decode(Pooka.getProperty(property + "." + resourceStrings[i], "")));
	} else {
	  int rgbValue = Integer.parseInt(Pooka.getProperty(property + "." + resourceStrings[i] + ".rgb", ""));
	  Color c = new Color(rgbValue);

	  returnValue.put(resourceStrings[i], c);
	}
      }
    }
    
    return returnValue;
  }

}