package net.suberic.pooka.gui.filter;
import net.suberic.pooka.gui.search.*;
import net.suberic.pooka.*;
import net.suberic.util.gui.propedit.*;
import net.suberic.util.VariableBundle;
import javax.swing.*;
import java.util.Vector;

/**
 * This is a class that lets you choose your filter actions.
 */
public class FilterEditorPane extends SwingPropertyEditor implements java.awt.event.ItemListener {

  JLabel label;
  JComboBox typeCombo;
  JPanel filterConfigPanel;
  java.awt.CardLayout layout;
  
  java.util.HashMap editorTable;
  
  /**
   * Configures the FilterEditorPane.
   */
  public void configureEditor(String propertyName, String template, PropertyEditorManager newManager, boolean isEnabled) {
    property=propertyName;
    manager=newManager;
    editorTemplate = template;
    originalValue = manager.getProperty(property, "");

    if (debug) {
      System.out.println("property is " + property + "; editorTemplate is " + editorTemplate);
    }

    editorTable = new java.util.HashMap();
    
    // create the label
    label = new JLabel(manager.getProperty(editorTemplate + ".label", "Action"));
    
    // find out if we're a display filter or a backend filter
    String filterType = manager.getProperty(editorTemplate + ".filterType", "display");
    
    // create the combo
    Vector filterLabels = null;
    if (filterType.equalsIgnoreCase("display"))
      filterLabels = Pooka.getSearchManager().getDisplayFilterLabels();
    else
      filterLabels = Pooka.getSearchManager().getBackendFilterLabels();
    
    typeCombo = new JComboBox(filterLabels);
    typeCombo.addItemListener(this);
    
    
    // create the filterConfigPanel.
    
    filterConfigPanel = new JPanel();
    layout = new java.awt.CardLayout();
    filterConfigPanel.setLayout(layout);
    for (int i = 0; i < filterLabels.size(); i++) {
      String label = (String) filterLabels.elementAt(i);
      FilterEditor currentEditor = Pooka.getSearchManager().getEditorForFilterLabel(label);
      currentEditor.configureEditor(manager, property);
      
      filterConfigPanel.add(label, currentEditor);
      editorTable.put(label, currentEditor);
    }
    
    JPanel valuePanel = new JPanel();
    
    valuePanel.add(typeCombo);
    valuePanel.add(filterConfigPanel);
    
    this.add(label);
    this.add(valuePanel);
    
    labelComponent = label;
    valueComponent = valuePanel;
    
    resetDefaultValue();
  }
  
  /**
   * Sets the value for this PropertyEditor.
   */
  public void setValue() {
    getFilterEditor().setValue();
  }
  
  /**
   * Returns the currently selected FilterEditor.
   */
  public FilterEditor getFilterEditor() {
    return (FilterEditor) editorTable.get(typeCombo.getSelectedItem());
  }
  
  /**
   * Gets the value that would be set by this PropertyEditor.
   */
  public java.util.Properties getValue() {
    return getFilterEditor().getValue();
  }
  
  /**
   * Resets the current Editor to its original value.
   */
  public void resetDefaultValue() {
    // get the current value, if any
    String currentLabel = Pooka.getSearchManager().getLabelForFilterClass(manager.getProperty(property + ".class", ""));
    if (currentLabel != null) {
      typeCombo.setSelectedItem(currentLabel);
    } else {
      typeCombo.setSelectedIndex(0);
    }
    
    FilterEditor currentEditor = getFilterEditor();
    
  }
  
  /**
   * Enables or disables this editor.
   */
  public void setEnabled(boolean newValue) {
    typeCombo.setEnabled(newValue);
  }
  
  
  /**
   * This handles the switch of the filterConfigPanel when the typeCombo
   * value changes.
   */
  public void itemStateChanged(java.awt.event.ItemEvent e) {
    String selectedString = (String) typeCombo.getSelectedItem();
    
    layout.show(filterConfigPanel, selectedString);
  }
  
}