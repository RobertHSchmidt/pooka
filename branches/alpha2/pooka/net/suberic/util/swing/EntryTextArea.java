package net.suberic.util.swing;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.event.KeyEvent;

/**
 * This is a subclass of JTextArea which allows for tab field navigation.
 */
public class EntryTextArea extends JTextArea {

    /**
     * Constructor; calls super.
     */
    public EntryTextArea() {
	super();
    }

    /**
     * Constructor; calls super.
     */
    public EntryTextArea(Document doc) {
	super(doc);
    }

    /**
     * Constructor; calls super.
     */
    public EntryTextArea(Document doc, String text, int rows, int columns) {
	super(doc, text, rows, columns);
    }

    /**
     * Constructor; calls super.
     */
    public EntryTextArea(int rows, int columns) {
	super(rows, columns);
    }

    /**
     * Constructor; calls super.
     */
    public EntryTextArea(String text) {
	super(text);
    }

    /**
     * Constructor; calls super.
     */
    public EntryTextArea(String text, int rows, int columns) {
	super(text, rows, columns);
    }

    /**
     * Overriedes processComponentKeyEvent in order to avoid consuming
     * tab characters, and thus to allow focus management for the
     * TextArea. 
     */
    protected void processComponentKeyEvent(KeyEvent e) {
	if (!isManagingFocus() || (e.getKeyCode() != KeyEvent.VK_TAB || e.getKeyChar() != '\t')) {
	    super.processComponentKeyEvent(e);
	}
    }
}