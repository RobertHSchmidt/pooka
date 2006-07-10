package net.suberic.util.gui.propedit;
import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.util.Vector;
import java.util.List;
import java.util.logging.Logger;

/**
 * <p>This will make an editor for a list of properties.</p>
 *
 * <p>Note that CompositeSwingPropertyEditors generally will append
 * subProperties that start with "." to the source template and property.
 * In addition, CompositeSwingPropertyEditors have two properties
 * that can vary this behavior:  if "propertyScoped" is set to true and
 * the subproperty does not start with ".", then the original property is
 * passed through.  If "addSubProperty" is set to false, then the original
 * property will be passed through even if the subProperty starts with "."
 * </p>
 *
 * <p>Also, for template properties, the value 'templateBase" can be set
 * to indicate the template base to use for scoped subtemplates, so that
 * you can use the same set of subproperty definitions for multiple templated
 * locations (i.e. using Store.editor.main.server as the key for .server
 * for both Store.editor.main.imap and Store.editor.main.pop3).</p>
 */
public abstract class CompositeSwingPropertyEditor extends SwingPropertyEditor {
  protected List editors;
  protected Logger mLogger = Logger.getLogger("editors.debug");

  /**
   * This writes the currently configured values in the PropertyEditorUI
   * to the source VariableBundle.
   */
  public void setValue() throws PropertyValueVetoException {
    if (isEnabled()) {
      for (int i = 0; i < editors.size() ; i++) {
        ((PropertyEditorUI) editors.get(i)).setValue();
      }
    }
  }

  /**
   * This resets the editor to the original (or latest set, if setValue()
   * has been called) value of the edited property.
   */
  public void resetDefaultValue() throws PropertyValueVetoException {
    if (isEnabled()) {
      for (int i = 0; i < editors.size() ; i++) {
        ((PropertyEditorUI) editors.get(i)).resetDefaultValue();
      }
    }
  }

  /**
   * Returns the current values of the edited properties as a
   * java.util.Properties object.
   */
  public java.util.Properties getValue() {
    java.util.Properties currentRetValue = new java.util.Properties();
    java.util.Iterator iter = editors.iterator();
    while (iter.hasNext()) {
      currentRetValue.putAll(((SwingPropertyEditor)iter.next()).getValue());
    }

    return currentRetValue;
  }

  /**
   * Sets the enabled property of the PropertyEditorUI.  Disabled
   * editors should not be able to do setValue() calls.
   */
  public void setEnabled(boolean newValue) {
    for (int i = 0; i < editors.size() ; i++) {
      ((PropertyEditorUI) editors.get(i)).setEnabled(newValue);
    }
    enabled=newValue;
  }

  /**
   * Returns the appropriate property for this source property.
   */
  public String createSubProperty(String pSource) {
    if (pSource.startsWith(".")) {
      if (manager.getProperty(editorTemplate + ".addSubProperty", "").equalsIgnoreCase("false")) {
        return property;
      } else {
        return property + pSource;
      }
    } else {
      if (manager.getProperty(editorTemplate + ".propertyScoped", "").equalsIgnoreCase("true")) {
        return property;
      } else {
        return pSource;
      }
    }
  }

  /**
   * Returns the appropriate tempate for this source property.
   */
  public String createSubTemplate(String pSource) {
    if (pSource.startsWith(".")) {
      return manager.getProperty(editorTemplate + ".templateBase", editorTemplate) + pSource;
    } else {
      return pSource;
    }
  }

  /**
   * Returns the appropriate propertyBase for this source property.
   */
  public String createSubPropertyBase(String pSource) {
    if (! pSource.startsWith(".") && ! manager.getProperty(editorTemplate + ".propertyScoped", "").equalsIgnoreCase("true")) {
      return pSource;
    } else {
      return propertyBase;
    }
  }

  /**
   * Lays out the composite property editor in a grid.
   */
  protected void layoutGrid(Container parent, Component[] labelComponents, Component[] valueComponents, int initialX, int initialY, int xPad, int yPad) {
    SpringLayout layout;
    try {
      layout = (SpringLayout)parent.getLayout();
    } catch (ClassCastException exc) {
      System.err.println("The first argument to layoutGrid must use SpringLayout.");
      return;
    }

    if (labelComponents == null || labelComponents.length < 1) {
      System.err.println("Attempt to layoutGrid with no components.");
      return;
    }

    // go through both columns.
    Spring labelWidth = Spring.constant(0);
    Spring valueWidth = Spring.constant(0);
    Spring fullWidth = Spring.constant(0);

    Spring labelValueXOffset = Spring.constant(initialX, initialX, 32000);
    Spring xOffset = Spring.constant(initialX, initialX, initialX);
    Spring fullXOffset = Spring.constant(initialX, initialX, 32000);

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

    for (int i = 0; i < labelComponents.length; i++) {
      if (valueComponents[i] != null) {
        SpringLayout.Constraints constraints = layout.getConstraints(labelComponents[i]);
        //layout.putConstraint(SpringLayout.WEST, labelComponents[i], labelValueXOffset, SpringLayout.WEST, parent);
        layout.putConstraint(SpringLayout.WEST, labelComponents[i], xOffset, SpringLayout.WEST, parent);
        constraints.setWidth(labelWidth);

        constraints = layout.getConstraints(valueComponents[i]);
        layout.putConstraint(SpringLayout.WEST, valueComponents[i],  xPad, SpringLayout.EAST, labelComponents[i]);
        constraints.setWidth(valueWidth);
        if (i == 0) {
          layout.putConstraint(SpringLayout.EAST, parent, fullXOffset, SpringLayout.EAST, valueComponents[i]);
        }
      } else {
        // set for the full width.
        SpringLayout.Constraints constraints = layout.getConstraints(labelComponents[i]);
        //layout.putConstraint(SpringLayout.WEST, labelComponents[i], fullXOffset, SpringLayout.WEST, parent);
        layout.putConstraint(SpringLayout.WEST, labelComponents[i], xOffset, SpringLayout.WEST, parent);
        //constraints.setWidth(fullWidth);
        if (i == 0) {
          layout.putConstraint(SpringLayout.EAST, parent, fullXOffset, SpringLayout.EAST, labelComponents[i]);
        }
      }
    }

    //Align all cells in each row and make them the same height.
    for (int i = 0; i < labelComponents.length; i++) {
      Spring height = Spring.constant(0);
      if (valueComponents[i] != null) {
        height = Spring.max(layout.getConstraints(labelComponents[i]).getHeight(), layout.getConstraints(valueComponents[i]).getHeight());
        if (i == 0) {
          layout.putConstraint(SpringLayout.NORTH, labelComponents[i], yPad, SpringLayout.NORTH, parent);
        } else {
          layout.putConstraint(SpringLayout.NORTH, labelComponents[i], yPad, SpringLayout.SOUTH, labelComponents[i - 1]);
        }
        layout.putConstraint(SpringLayout.NORTH, valueComponents[i], 0, SpringLayout.NORTH, labelComponents[i]);
        layout.putConstraint(SpringLayout.SOUTH, valueComponents[i], 0, SpringLayout.SOUTH, labelComponents[i]);

        layout.getConstraints(labelComponents[i]).setHeight(height);
        layout.getConstraints(valueComponents[i]).setHeight(height);
      } else {
        if (i == 0) {
          layout.putConstraint(SpringLayout.NORTH, labelComponents[i], yPad, SpringLayout.NORTH, parent);
        } else {
          layout.putConstraint(SpringLayout.NORTH, labelComponents[i], yPad, SpringLayout.SOUTH, labelComponents[i - 1]);
        }
      }
    }

    Spring southBoundary = Spring.constant(yPad, yPad, 32000);
    layout.putConstraint(SpringLayout.SOUTH, parent, southBoundary, SpringLayout.SOUTH, labelComponents[labelComponents.length - 1]);
    //Set the parent's size.
    //pCons.setConstraint(SpringLayout.EAST, Spring.sum(fullWidth, Spring.constant(initialX)));
  }

  /**
   * Gets the parent PropertyEditorPane for the given component.
   */
  public PropertyEditorPane getPropertyEditorPane() {
    return getPropertyEditorPane(this);
  }


}


