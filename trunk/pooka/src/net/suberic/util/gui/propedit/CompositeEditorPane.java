package net.suberic.util.gui.propedit;
import javax.swing.*;
import java.awt.Container;
import java.awt.Component;
import java.util.Vector;
import java.util.List;
import net.suberic.util.VariableBundle;

/**
 * This is a Property Editor which displays a group of properties.
 * These properties should all be defined by a single property.
 *
 * An example:
 *
 * Configuration=foo:bar
 * Configuration.propertyType=Composite
 * Configuration.scoped=false
 * foo=zork
 * bar=frobozz
 *
 * Options:
 *
 * Configuration.scoped - shows that the properties listed are subproperties
 *   of both the property and the template.  So, in this example, if you 
 *   had Configuration.scoped, the properties edited would be 
 *   Configuration.foo and Configuration.bar
 * Configuration.scopeRoot - if the setting is scoped, then this is the
 *   root for the template's scope.  Useful when dealing with properties that
 *   can be reached from multiple points (i.e. if 
 *   Configuration.one.two.three=foo:bar also, then you could set the 
 *   scopeRoot to Configuration and use the already configured foo and bar.
 * Configuration.subProperty.addSubProperty - shows whether or not you
 *   should add the given subproperty to the edited property for this editor.
 *   Useful if you have a CompositeEditorPane that contains other 
 *   Composite or Tabbed EditorPanes.  If Configuration.foo is another
 *   CompositeEditorPane which in turn edits .frotz and .ozmoo, and 
 *   Configuration.foo.addSubProperty=true (the default), then 
 *   Configuration.foo.frotz and Configuration.foo.ozmoo will be edited.
 *   If Configuration.foo.addSubProperty=false, then Configuration.frotz
 *   and Configuration.ozmoo will be edited, using Configuration.foo.frotz
 *   and Configuration.foo.ozmoo as templates.  This is primarily useful
 *   when using MultiEditorPanes.
 *
 */
public class CompositeEditorPane extends CompositeSwingPropertyEditor {
  boolean scoped;

  /**
   * Creates a CompositeEditorPane.
   */
  public CompositeEditorPane() {

  }

  /**
   * Creates a CompositeEditorPane editing the given list.
   */
  public CompositeEditorPane(List properties, List templates, PropertyEditorManager mgr) {
    configureEditor(properties, templates, mgr);
  }
  
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
    enabled=isEnabled;
    originalValue = manager.getProperty(property, "");
    
    this.setBorder(BorderFactory.createEtchedBorder());

    debug = manager.getProperty("editors.debug", "false").equalsIgnoreCase("true");

    if (debug) {
      System.out.println("creating CompositeEditorPane for " + property + " with template " + editorTemplate);
    }
    
    scoped = manager.getProperty(template + ".scoped", "false").equalsIgnoreCase("true");

    if (debug) {
      System.out.println("manager.getProperty (" + template + ".scoped) = " +  manager.getProperty(template + ".scoped", "false") + " = " + scoped);
    }

    List properties = new Vector();
    List templates = new Vector();
    
    if (scoped) {
      if (debug) {
        System.out.println("testing for template " + template);
      }
      String scopeRoot = manager.getProperty(template + ".scopeRoot", template);
      if (debug) {
        System.out.println("scopeRoot is " + scopeRoot);
      }
      List templateNames = manager.getPropertyAsList(template, "");
      if (debug) {
        System.out.println("templateNames = getProp(" + template + ") = " + manager.getProperty(template, ""));
      }
      
      for (int i = 0; i < templateNames.size() ; i++) {
        String propToEdit = null;
        String currentSubProperty =  (String) templateNames.get(i);
        if (manager.getProperty(scopeRoot + "." + currentSubProperty + ".addSubProperty", "true").equalsIgnoreCase("false")) {
          propToEdit = property;
        } else {
          propToEdit = property + "." + (String) templateNames.get(i);
        }
        String templateToEdit = scopeRoot + "." + (String) templateNames.get(i);
        properties.add(propToEdit);
        templates.add(templateToEdit);
        if (debug) {
          System.out.println("adding " + propToEdit + ", template " + templateToEdit);
        }
      }
    } else {
      if (debug) {
        System.out.println("creating prop list for Composite EP using " + property + ", " + template);
      }
      properties = manager.getPropertyAsList(property, "");
      templates = manager.getPropertyAsList(template, "");
    }
    
    addEditors(properties, templates);
  }

  public void addEditors(List properties, List templates) {
    SwingPropertyEditor currentEditor;
    
    editors = new Vector();

    SpringLayout layout = new SpringLayout();
    JPanel contentPanel = new JPanel();

    contentPanel.setLayout(new SpringLayout());
    Component[] labelComponents = new Component[properties.size()];
    Component[] valueComponents = new Component[properties.size()];
    for (int i = 0; i < properties.size(); i++) {
      currentEditor = (SwingPropertyEditor) manager.createEditor((String)properties.get(i), (String) templates.get(i));
      currentEditor.setEnabled(enabled);
      editors.add(currentEditor);
      
      if (currentEditor.valueComponent != null) {
        if (currentEditor.labelComponent != null) {
          contentPanel.add(currentEditor.labelComponent);
          labelComponents[i] = currentEditor.labelComponent;
          contentPanel.add(currentEditor.valueComponent);
          valueComponents[i] = currentEditor.valueComponent;
        } else {
          labelComponents[i] = currentEditor.valueComponent;
          contentPanel.add(currentEditor.valueComponent);
        }
      } else {
        contentPanel.add(currentEditor);
        labelComponents[i] = currentEditor;
      }
    }
    
    this.add(contentPanel);
    makeCompactGrid(contentPanel, labelComponents, valueComponents, 5, 5, 5, 5);

    manager.registerPropertyEditor(property, this);
  }
  
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
  public void configureEditor(List properties, List templates, PropertyEditorManager newManager) {
    manager=newManager;
    enabled = true;

    this.setBorder(BorderFactory.createEtchedBorder());

    debug = manager.getProperty("editors.debug", "false").equalsIgnoreCase("true");

    addEditors(properties, templates);
  }
  
  /**
   */
  protected void makeCompactGrid(Container parent,
                                 Component[] labelComponents,
                                 Component[] valueComponents,
                                 int initialX, int initialY,
                                 int xPad, int yPad) {
    SpringLayout layout;
    try {
      layout = (SpringLayout)parent.getLayout();
    } catch (ClassCastException exc) {
      System.err.println("The first argument to makeCompactGrid must use SpringLayout.");
      return;
    }

    // go through both columns.
    Spring labelX = Spring.constant(initialX);
    Spring valueX = Spring.constant(initialX);
    Spring labelWidth = Spring.constant(0);
    Spring valueWidth = Spring.constant(0);
    Spring fullWidth = Spring.constant(0);
    for (int i = 0; i < labelComponents.length; i++) {
      // for components with a label and a value, add to labelWidth and 
      // valueWidth.
      if (valueComponents[i] != null) {
        labelWidth = Spring.max(labelWidth, layout.getConstraints(labelComponents[i]).getWidth());
        valueWidth = Spring.max(valueWidth, layout.getConstraints(valueComponents[i]).getWidth());
      } else {
        // otherwise just add to fullWidth.
        fullWidth = Spring.max(fullWidth, layout.getConstraints(labelComponents[i]).getWidth());
      }
    }

    // make sure fullWidth and labelWidth + valueWidth match.
    if (fullWidth.getValue() <= labelWidth.getValue() + xPad + valueWidth.getValue()) {
      fullWidth = Spring.sum(labelWidth, Spring.sum(Spring.constant(xPad), valueWidth));
    } else {
      valueWidth = Spring.sum(fullWidth, Spring.minus(Spring.sum(Spring.constant(xPad), labelWidth)));
    }

    // recalculate valueX.
    valueX = Spring.sum(labelX, Spring.sum(labelWidth, Spring.constant(xPad)));
    
    // now set the widths and x values for all of our components.
    for (int i = 0; i < labelComponents.length; i++) {
      if (valueComponents[i] != null) {
        SpringLayout.Constraints constraints = layout.getConstraints(labelComponents[i]);
        constraints.setX(labelX);
        constraints.setWidth(labelWidth);

        constraints = layout.getConstraints(valueComponents[i]);
        constraints.setX(valueX);
        constraints.setWidth(valueWidth);
      } else {
        // set for the full width.
        SpringLayout.Constraints constraints = layout.getConstraints(labelComponents[i]);
        constraints.setX(labelX);
        constraints.setWidth(fullWidth);
      }
    }

    //Align all cells in each row and make them the same height.
    Spring y = Spring.constant(initialY);
    for (int i = 0; i < labelComponents.length; i++) {
      Spring height = Spring.constant(0);
      if (valueComponents[i] != null) {
        height = Spring.max(layout.getConstraints(labelComponents[i]).getHeight(), layout.getConstraints(valueComponents[i]).getHeight());
        layout.getConstraints(labelComponents[i]).setY(y);
        layout.getConstraints(valueComponents[i]).setY(y);

        layout.getConstraints(labelComponents[i]).setHeight(height);
        layout.getConstraints(valueComponents[i]).setHeight(height);
      } else {
        height = layout.getConstraints(labelComponents[i]).getHeight();
        layout.getConstraints(labelComponents[i]).setY(y);
      }
      y = Spring.sum(y, Spring.sum(height, Spring.constant(yPad)));
    }

    //Set the parent's size.
    SpringLayout.Constraints pCons = layout.getConstraints(parent);
    pCons.setConstraint(SpringLayout.SOUTH, y);
    pCons.setConstraint(SpringLayout.EAST, Spring.sum(fullWidth, Spring.constant(initialX)));
  }

  private static SpringLayout.Constraints getConstraintsForCell(int row, int col,Container parent, int cols) {
    SpringLayout layout = (SpringLayout) parent.getLayout();
    Component c = parent.getComponent(row * cols + col);
    return layout.getConstraints(c);
  }
}



