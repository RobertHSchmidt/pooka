package net.suberic.util.gui.propedit;
import net.suberic.util.VariableBundle;
import java.util.*;

/**
 * This manages a set of PropertyEditors.  Basically, this acts as a
 * Transaction for the PropertyEditors.
 */
public class PropertyEditorManager {

  protected HashMap editorMap = new HashMap();

  protected VariableBundle sourceBundle;

  protected PropertyEditorFactory propertyFactory;

  protected HashMap pendingListenerMap = new HashMap();

  /**
   * Creates a new PropertyEditorManager.
   */
  protected PropertyEditorManager() {
  }

  /**
   * Creates a PropertyEditorManager using the given VariableBundle and
   * PropertyEditorFactory.
   */
  public PropertyEditorManager(VariableBundle vb, PropertyEditorFactory factory) {
    sourceBundle = vb;
    propertyFactory = factory;
  }

  /**
   * Gets the PropertyEditor for the given Property.
   */
  public PropertyEditorUI getPropertyEditor(String propertyName) {
    return (PropertyEditorUI) editorMap.get(propertyName);
  }

  /**
   * Registers the given PropertyEditorUI as the editor for the given
   * Property.
   */
  public void registerPropertyEditor(String property, PropertyEditorUI peui) {
    editorMap.put(property, peui);
  }

  /**
   * Gets the PropertyEditorFactory for this manager.
   */
  public PropertyEditorFactory getFactory() {
    return propertyFactory;
  }
  
  /**
   * Gets the value of the given property.
   */
  public String getProperty(String property, String defaultValue) {
    return sourceBundle.getProperty(property, defaultValue);
  }

  /**
   * Gets the value of the given property.
   */
  public List getPropertyAsList(String property, String defaultValue) {
    return sourceBundle.getPropertyAsVector(property, defaultValue);
  }

  /**
   * Sets the given property to the given value.
   */
  public void setProperty(String property, String value) {
    sourceBundle.setProperty(property, value);
  }

  /**
   * Removes the given property.
   */
  public void removeProperty(String property) {
    sourceBundle.removeProperty(property);
  }

  /**
   * Creates an appropriate PropertyEditorUI for the given property and
   * editorTemplate, using this PropertyEditorManager.
   */
  public PropertyEditorUI createEditor(String property, String editorTemplate) {  
    return getFactory().createEditor(property, editorTemplate, this);
  }
  
  /**
   * Creates an appropriate PropertyEditorUI for the given properties and
   * editorTemplates, using this PropertyEditorManager.
   */
  public PropertyEditorUI createEditor(List properties, List editorTemplates) {  
    return getFactory().createEditor(properties, editorTemplates, this);
  }
  
  /**
   * Commits the changes to the underlying VariableBundle.
   */
  public void commit() {
    sourceBundle.saveProperties();
  }

  /**
   * Adds the given PropertyEditorListener as a listener to the editor
   * for the given property.  If no editor exists yet, then the listener
   * is saved until a PropertyEditorUI is registered.
   */
  public void addPropertyEditorListener(String property, PropertyEditorListener listener) {
    PropertyEditorUI editor = (PropertyEditorUI) editorMap.get(property);
    if ( editor != null) {
      editor.addPropertyEditorListener(listener);
    } else {
      List listenerList = (List) pendingListenerMap.get(property);
      if (listenerList == null) {
	listenerList = new ArrayList();
      }
      listenerList.add(listener);
      pendingListenerMap.put(property, listenerList);
    }
  }

  /**
   * Registers the given PropertyEditorUI as the editor for the given
   * property.
   */
  public void registerEditor(String property, PropertyEditorUI editor) {
    List listenerList = (List) pendingListenerMap.get(property);
    if (listenerList != null) {
      Iterator it = listenerList.iterator();
      while (it.hasNext()) {
	editor.addPropertyEditorListener((PropertyEditorListener) it.next());
      }
    }

    editorMap.put(property, editor);
  }
}