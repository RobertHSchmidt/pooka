package net.suberic.util.gui.propedit;
import javax.swing.*;
import net.suberic.util.*;
import java.awt.CardLayout;
import javax.swing.event.*;
import java.util.*;
import javax.swing.*;

/**
 * This class will make an editor for a list of elements, where each of 
 * the elements has a set of subproperties.  
 *
 * Configuration is as follows:
 *
 * Foo.propertyType=Multi  --  shows this is a property editor for an
 *                             attribute with multiple values.
 *
 * Foo.editableFields=bar:baz -- shows which subfields are to be edited
 *
 * So if your Foo property equals "fooOne:fooTwo", then you'll end up with
 * a MultiPropertyEditor that has an entry for fooOne and fooTwo, along with
 * ways to add and delete these properties.
 *
 * If your Foo.editableFields=bar:baz, then your editor screen for, say,
 * fooOne will have two entries, one for Foo.fooOne.bar, and the other for
 * Foo.fooOne.baz.  These editors will use Foo.editableFields.bar and
 * Foo.editableFields.baz for templates.
 *
 */

public class MultiEditorPane extends CompositeSwingPropertyEditor implements ListSelectionListener {

  JList optionList;
  JPanel entryPanel;
  JLabel label;
  boolean changed = false;
  Vector removeValues = new Vector();
  DefaultListModel optionListModel;
  Vector templates;
  Box optionBox;
  
  Hashtable originalPanels = new Hashtable();
  Hashtable currentPanels = new Hashtable();
  
  /**
   * This configures this editor with the following values.
   *
   * @param propertyName The property to be edited.  
   * @param template The property that will define the layout of the 
   *                 editor.
   * @param manager The PropertyEditorManager that will manage the
   *                   changes.
   * @param isEnabled Whether or not this editor is enabled by default. 
   */
  public void configureEditor(String propertyName, String template, PropertyEditorManager newManager, boolean isEnabled) {
    property=propertyName;
    manager=newManager;
    editorTemplate = template;
    originalValue = manager.getProperty(property, "");
    
    
    if (manager.getProperty(editorTemplate + "._useTemplateForValue", "false").equalsIgnoreCase("true")) {
      originalValue = manager.getProperty(editorTemplate, "");
    } else {
      originalValue = manager.getProperty(property, "");
    }
    
    // set the default label.
    
    label = createLabel();
    
    // create the current list of edited items.  so if this is a User list,
    // these values might be 'allen', 'deborah', 'marc', 'jessica', etc.
    
    Vector optionVector = createEditedList(originalValue);
    
    optionListModel = new DefaultListModel();
    
    for (int i = 0; i < optionVector.size(); i++) {
      optionListModel.addElement(optionVector.elementAt(i));
    }
    
    optionList = new JList(optionListModel);
    
    optionBox = createOptionBox(label, optionList);
    this.add(optionBox);
    
    // now create the list of subproperties to be edited.
    
    template = editorTemplate + ".editableFields";
    
    // create entryPanels (the panels which show the subproperties
    // of each item in the optionList) for each option.
    
    entryPanel = createEntryPanel(optionVector, true);
    
    if (manager.getProperty(template + "._useScrollPane", "false").equalsIgnoreCase("true")) {
      JScrollPane jsp = new JScrollPane(entryPanel);
      java.awt.Dimension size = jsp.getPreferredSize();
      size.height = Math.min(size.height, 300);
      size.width = Math.min(size.width, 475);
      jsp.setPreferredSize(size);
      this.add(jsp);
      valueComponent = jsp;
    } else {
      this.add(entryPanel);
      valueComponent = entryPanel;
    }
    
    labelComponent = optionBox;
    
    this.setEnabled(isEnabled);
  }
  
  /**
   * Creates the list of edited items.
   */
  private Vector createEditedList(String origValue) {
    Vector items = new Vector();
    StringTokenizer tokens;
    
    tokens = new StringTokenizer(origValue, ":");
    
    for (int i=0; tokens.hasMoreTokens(); i++) {
      items.add(tokens.nextToken());
    }
    return items;
  }	      
  
  /**
   * Creates the option box.
   */
  private Box createOptionBox(JLabel label, JList itemList) {
    Box optBox = new Box(BoxLayout.Y_AXIS);
    optBox.add(label);
    
    optionList.addListSelectionListener(this);
    JScrollPane listScrollPane = new JScrollPane(optionList);
    optBox.add(listScrollPane);
    
    if (! manager.getProperty(property + "._fixed", "false").equalsIgnoreCase("true"))
      optBox.add(createButtonBox());
    
    return optBox;
  }
  
  /**
   * This creates a panel for each option.  It uses a CardLayout.
   *
   * Note that this is also the section of code which determines which 
   * subproperties are to be edited.
   */
  private JPanel createEntryPanel (Vector itemList, boolean original) {
    JPanel entryPanel = new JPanel(new CardLayout());
    
    String rootProp;
    Vector propList;
    Vector templateList;
    
    // create the default 
    
    int i = itemList.size();
    
    rootProp = new String(property + ".default");
    
    SwingPropertyEditor pep = (SwingPropertyEditor) manager.createEditor(rootProp, editorTemplate);
    pep.setEnabled(false);
    
    if (original == true) {
      originalPanels.put("___default", pep);
    }
    
    currentPanels.put("___default", pep);

    entryPanel.add("___default", pep);
    CardLayout entryLayout = (CardLayout)entryPanel.getLayout();
    entryLayout.show(entryPanel, "___default");
    
    return entryPanel;
  }
  
  /**
   * Creates the box which holds the "Add" and "Remove" buttons.
   */
  private Box createButtonBox() {
    Box buttonBox = new Box(BoxLayout.X_AXIS);
    
    buttonBox.add(createButton("Add", new AbstractAction() {
	public void actionPerformed(java.awt.event.ActionEvent e) {
	  addNewValue(getNewValueName());
	}
      }, true));
    
    buttonBox.add(createButton("Remove", new AbstractAction() {
	public void actionPerformed(java.awt.event.ActionEvent e) {
	  removeSelectedValue();
	}
      }, false));
    
    /*
      buttonBox.add(createButton("Rename", new AbstractAction() {
      public void actionPerformed(java.awt.event.ActionEvent e) {
      editSelectedValue();
      }
      }, false));
    */
    
    return buttonBox;
  }
  
  /**
   * Creates a Button for the ButtonBox with the appropriate label and
   * Action.
   */
  private JButton createButton(String label, Action e, boolean isDefault) {
    JButton thisButton;
    
    thisButton = new JButton(manager.getProperty("label." + label, label));
    String mnemonic = manager.getProperty("label." + label + ".mnemonic", "");
    if (!mnemonic.equals(""))
      thisButton.setMnemonic(mnemonic.charAt(0));
    
    thisButton.setSelected(isDefault);
    
    thisButton.addActionListener(e);
    
    return thisButton;
  }
  
  
  /**
   * Called when the selected value changed.  Should result in the 
   * entryPane changing.
   */
  public void valueChanged(ListSelectionEvent e) {
    
    CardLayout entryLayout = (CardLayout)entryPanel.getLayout();
    
    String selectedId = (String)((JList)e.getSource()).getSelectedValue();
    
    if (selectedId != null) {
      Object newSelected = currentPanels.get(selectedId);
      if (newSelected == null) {
	String rootProp = new String(property + "." + selectedId);
	
	SwingPropertyEditor pep = (SwingPropertyEditor) manager.createEditor(rootProp, editorTemplate);

	// save reference to new pane in hash table
	currentPanels.put(selectedId, pep);
	editors.add(pep);
	
	entryPanel.add(selectedId, pep);
	
      }
      entryLayout.show(entryPanel, selectedId);
    } else
      entryLayout.show(entryPanel, "___default");
  }
  
  /**
   * Adds a new value to the edited List.
   */
  public void addNewValue(String newValueName) {
    if (newValueName == null || newValueName.length() == 0)
      return;
    
    try {
      // get what will be the new value.
      String newValueString = getStringFromList(optionListModel);
      newValueString = newValueString + "." + newValueName;
      firePropertyChangingEvent(newValueString);

      String rootProp = new String(property + "." + newValueName);
      
      SwingPropertyEditor pep = (SwingPropertyEditor) manager.createEditor(rootProp, editorTemplate);
      
      optionListModel.addElement(newValueName);
      
      entryPanel.add(newValueName, pep);
      
      getOptionList().setSelectedValue(newValueName, true);
    
      this.setChanged(true);

      firePropertyChangedEvent(getStringFromList(optionListModel));

    } catch (PropertyValueVetoException pvve) {
      manager.getFactory().showError(this, "Error adding value " + newValueName + " to " + label.getText() + ":  " + pvve.getReason());
    }
  }
  
  /**
   * Removes the currently selected value from the edited List.
   */
  public void removeSelectedValue() {
    
    String selValue = (String)getOptionList().getSelectedValue();
    if (selValue == null)
      return;
    
    try {
      DefaultListModel tmpListModel = new DefaultListModel();
      for (int i = 0; i < optionListModel.size(); i++) {
	tmpListModel.addElement(optionListModel.get(i));
      }

      tmpListModel.removeElement(selValue);
      firePropertyChangingEvent(getStringFromList(tmpListModel));

      String rootProp = new String(property.concat("." + selValue));
      PropertyEditorUI removedUI = (PropertyEditorUI) currentPanels.get(selValue);
      if (removedUI != null) {
	editors.remove(removedUI);
	java.util.Properties removedProperties = removedUI.getValue();
	java.util.Enumeration keys = removedProperties.keys();
	while (keys.hasMoreElements()) {
	  String currentKey = (String) keys.nextElement();
	  removeValues.add(currentKey);
	}

	currentPanels.remove(selValue);
      }
      
      optionListModel.removeElement(selValue);
      
      this.setChanged(true);
    } catch (PropertyValueVetoException pvve) {
      manager.getFactory().showError(this, "Error removing value " + selValue + " from " + label.getText() + ":  " + pvve.getReason());
    }
    
  }
  
  /**
   * Edits the currently selected value.
   */
  public void editSelectedValue() {
  }
  
  /**
   * Puts up a dialog to get a name for the new value.
   */
  public String getNewValueName() {
    boolean goodValue = false;
    boolean matchFound = false;
    
    String newName = null;
    newName = manager.getFactory().showInputDialog(this, manager.getProperty("MultiEditorPane.renameProperty", "Enter new name."));
    
    while (goodValue == false) {
      matchFound = false;
      if (newName != null) {
	for (int i = 0; i < optionListModel.getSize() && matchFound == false; i++) {
	  if (((String)optionListModel.getElementAt(i)).equals(newName)) 
	    matchFound = true;
	  
	}
	
	if (matchFound == false)
	  goodValue = true;
	else
	  newName = manager.getFactory().showInputDialog(this, manager.getProperty("MultiEditorPane.error.duplicateName", "Name already exists:") + "  " + newName + "\n" + manager.getProperty("MultiEditorPane.renameProperty", "Enter new name."));
      } else {
	goodValue = true;
      }
    }
    
    return newName;
  }
  
  /**
   * This renames the selected property.
   */
  public void renameProperty(String oldName, String newName) {
    /*
    newName = getNewValueName();
    if (newName != null) {
      CompositeEditorPane oldPane = (CompositeEditorPane)currentPanels.get(oldName);
      if (oldPane != null) {
	String rootProp =new String(property.concat("." + newName));
	
	CompositeEditorPane pep = new CompositeEditorPane(manager, rootProp, editorTemplate);;
	java.util.Properties oldProps = oldPane.getValue();
      }
    }
    */
  }
  
  /**
   * This produces a string for the given JList.
   */
  public String getStringFromList(DefaultListModel dlm) {
    
    String retVal;
    if (dlm.getSize() < 1)
      return "";
    else 
      retVal = new String((String)dlm.getElementAt(0));
    
    for (int i = 1; i < dlm.getSize(); i++) {
      retVal = retVal.concat(":" + (String)dlm.getElementAt(i));
    }
    
    return retVal;
  }
  
  /**
   * Sets the value for this MultiEditorPane.
   */
  public void setValue() throws PropertyValueVetoException {
    if (isEnabled()) {
      
      for (int i = 0; i < removeValues.size() ; i++) 
	manager.removeProperty((String)removeValues.elementAt(i));
      
      removeValues = new Vector();
      
      super.setValue();
      
      if (isChanged()) {
	if (debug) {
	  System.out.println("setting property.  property is " + property + "; getStringFromList is " + getStringFromList(optionListModel));
	}
	manager.setProperty(property, getStringFromList(optionListModel));
      }
    }
  }
  
  /**
   * Resets the default values.
   */
  public void resetDefaultValue() throws PropertyValueVetoException {
    
    removeValues = new Vector();
    
    if (isChanged()) {
      firePropertyChangingEvent(originalValue);
      optionListModel.removeAllElements();
      entryPanel.removeAll();
      
      java.util.Enumeration en = originalPanels.keys();
      
      while (en.hasMoreElements()) {
	String key = (String)en.nextElement();
	entryPanel.add(key, (JPanel)originalPanels.get(key));
      }
      firePropertyChangedEvent(originalValue);
    }
    
    java.awt.Component[] components = entryPanel.getComponents();
    for (int i = 0; i < components.length; i++) {
      ((CompositeEditorPane)components[i]).resetDefaultValue();
    }
  }
  
  /**
   * Returns the currently edited values as a Properties object.
   */
  public java.util.Properties getValue() {
    java.util.Properties currentRetValue = super.getValue();
    currentRetValue.setProperty(property, getStringFromList(optionListModel));
    return currentRetValue;
  }
  
  /**
   * Returns whether or not the top-level edited values of this EditorPane
   * have changed.
   */
  public boolean isChanged() {
    return changed;
  }
  
  /**
   * Sets whether or not the top-level edited values of this EditorPane
   * have changed.
   */
  public void setChanged(boolean newChanged) {
    changed=newChanged;
  }
  
  /**
   * Returns the optionList.
   */
  public JList getOptionList() {
    return optionList;
  }
  
  /**
   * Returns the entryPanel.
   */
  public JPanel getEntryPanel() {
    return entryPanel;
  }
}






