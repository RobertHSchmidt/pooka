package net.suberic.pooka.gui.filter;

/**
 * This is a class that lets you choose your filter actions.
 */
public abstract class FilterEditor extends javax.swing.Box {
  
  protected net.suberic.util.gui.propedit.PropertyEditorManager manager;
  
  protected String property;
  
  public FilterEditor() {
    super(javax.swing.BoxLayout.X_AXIS);
  }
  
  /**
   * Configures the given FilterEditor from the given VariableBundle and
   * property.
   */
  public abstract void configureEditor(net.suberic.util.gui.propedit.PropertyEditorManager manager, String propertyName);
  
  /**
   * Gets the values that would be set by this FilterEditor.
   */
  public abstract java.util.Properties getValue();
  
  /**
   * Sets the values represented by this FilterEditor in the sourceBundle.
   */
  public abstract void setValue();
  
  /**
   * Returns the class that will be set for this FilterEditor.
   */
  public abstract String getFilterClassValue();
}
