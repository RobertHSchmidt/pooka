package net.suberic.util.gui;
import javax.swing.*;
import java.util.Vector;
import java.util.HashMap;
import java.util.Properties;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import net.suberic.util.VariableBundle;

/**
 * This will made a button which will call up another editor.  The 
 * exact properties which are then edited will depend on the value of 
 * another propery. 
 */
public class VariableEditorPane extends DefaultPropertyEditor {
  PropertyEditorFactory factory;
  VariableBundle sourceBundle;
  
  Properties parentUIProperties;
  
  String property;
  String template;
  String keyProperty;
  HashMap valueToTemplateMap = new HashMap();
  
  JButton button;
  
  boolean scoped;
  
  public VariableEditorPane(String newProperty, String newTemplate, PropertyEditorFactory newFactory) {
    configureEditor(newFactory, newProperty, newTemplate, newFactory.getBundle(), true);
  }
  
  /**
   * This configures this editor with the following values.
   */
  public void configureEditor(PropertyEditorFactory newFactory, String propertyName, String templateType, VariableBundle bundle, boolean isEnabled) {

    property=propertyName;
    template=templateType;
    factory = newFactory;
    sourceBundle = bundle;
    enabled=isEnabled;
    
    String remove = sourceBundle.getProperty(template + ".removeString", "");
    if (! remove.equals(""))
      property = property.substring(0, property.lastIndexOf(remove));

    scoped = sourceBundle.getProperty(template + ".scoped", "false").equalsIgnoreCase("true");
    if (scoped) {
      keyProperty = property + "." + sourceBundle.getProperty(template + ".keyProperty", "");
    } else {
      keyProperty =  sourceBundle.getProperty(template + ".keyProperty", "");
    }
    //System.out.println("Variable:  property = " + property + "; keyProperty = " + keyProperty);
    
    Vector allowedValues = sourceBundle.getPropertyAsVector(template + ".allowedValues", "");
    for (int i = 0; i < allowedValues.size(); i++) {
      String value = (String) allowedValues.elementAt(i);
      String editValue = sourceBundle.getProperty(template + ".allowedValues." + value,  "");
      valueToTemplateMap.put(value, editValue);
    }
    
    String label = sourceBundle.getProperty(template + ".label", template);
    labelComponent = new JLabel(label);
    
    String buttonLabel = sourceBundle.getProperty(template + ".button.label", template);
    
    button = new JButton(buttonLabel);
    button.addActionListener(new ActionListener() {
	public void actionPerformed(ActionEvent e) {
	  
	  showEditorWindow();
	}
      });
    
    valueComponent = new JPanel();
    valueComponent.setLayout(new FlowLayout(FlowLayout.LEFT));
    valueComponent.add(button);
  }
  
  /**
   * This shows the editor window for the configured value.
   */
  public void showEditorWindow() {
    PropertyEditorUI parent = getParentPropertyEditor();
    //System.out.println("keyProperty = " + keyProperty);
    String currentValue = sourceBundle.getProperty(keyProperty, "");
    
    //System.out.println("currentValue initial = " + keyProperty + ":  " + currentValue);
    if (parent != null) {
      String test = parent.getValue().getProperty(keyProperty);
      //System.out.println("parent.getValue() = ");
      //parent.getValue().list(System.out);
      //System.out.println("parent != null; parent.getValue.getProperty(" + keyProperty + ") = " + test);
      if (test != null)
	currentValue = test;
    }
    
    /*
      String editValue = (String) valueToTemplateMap.get(currentValue);
      if (editValue == null)
      editValue = (String) valueToTemplateMap.get("default");
    */
    
    String editValue = currentValue;
    
    if (scoped) {
      editValue = template + "." + editValue;
      //System.out.println("scoped; editValue = " + editValue);
    } else {
      //System.out.println("not scoped; editValue = " + editValue);
    }
    
    Vector propertyVector = new Vector();
    propertyVector.add(property);
    Vector templateVector = new Vector();
    templateVector.add(editValue);
    
    factory.showNewEditorWindow(sourceBundle.getProperty(editValue + ".title", editValue), propertyVector, templateVector);
  }
  
  /**
   * Returns the parent PropertyEditorUI.
   */
  protected PropertyEditorUI getParentPropertyEditor() {
    java.awt.Container parent = valueComponent.getParent();
    PropertyEditorUI returnValue = null;
    while (returnValue == null && parent != null) {
      if (parent instanceof PropertyEditorUI)
	returnValue = (PropertyEditorUI) parent;
      else
	parent = parent.getParent();
    }
    
    return returnValue;
  }
  
  public void setValue() {
  }
  
  public void resetDefaultValue() {
  }
  
  public java.util.Properties getValue() {
    java.util.Properties currentRetValue = new java.util.Properties();
    
    PropertyEditorUI parent = getParentPropertyEditor();
    if (parent instanceof CompositeEditorPane) {
      String currentValue = sourceBundle.getProperty(keyProperty, "");
      if (parent != null) {
	String test = ((CompositeEditorPane)parent).getOtherValues(this).getProperty(keyProperty);
	if (test != null)
	  currentValue = test;
      }
      
      String editValue = currentValue;
      
      if (scoped)
	editValue = template + "." + editValue;
      
      PropertyEditorUI ui = 	factory.createEditor(property, editValue);
	    
      java.util.Properties props = ui.getValue();
      java.util.Enumeration keys = props.keys();
      while (keys.hasMoreElements()) {
	String currentProperty = (String) keys.nextElement();
	currentRetValue.setProperty(currentProperty, props.getProperty(currentProperty));
      }
    }
    return currentRetValue;
  }
  
  public void setEnabled(boolean newValue) {
    button.setEnabled(newValue);
    enabled=newValue;
  }
}


