package net.suberic.pooka.gui;

import net.suberic.pooka.Pooka;
import net.suberic.pooka.MessageInfo;
import net.suberic.pooka.MessageCryptoInfo;
import net.suberic.pooka.OperationCancelledException;
import net.suberic.pooka.gui.crypto.CryptoPanel;

import javax.mail.MessagingException;
import javax.swing.*;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import org.w3c.dom.*;
import org.w3c.dom.events.*;

public class ReadMessageJFXDisplayPanel extends ReadMessageDisplayPanel {
  public static String HTML_LAYOUT = "html";
  JFXPanel fxPanel = null;

  public ReadMessageJFXDisplayPanel(MessageUI ui) {
    super(ui);
  }

  /**
   * This sets the text of the editorPane to the content of the current
   * message.
   *
   * Should only be called from within the FolderThread for the message.
   *
   * Also updates the current keybindings.
   */
  public void resetEditorText() throws MessagingException, OperationCancelledException {
    // ok.  here's how this has to go:  we need to load the information from
    // the message on the message editor thread, but then actually do the
    // display changing on the awt event thread.  seem simple enough?

    // assume that we're actually on the FolderThread for now.

    if (getMessageProxy() != null) {
      MessageInfo msgInfo = getMessageProxy().getMessageInfo();

      StringBuffer messageText = new StringBuffer();

      String content = null;

      String contentType = "text/plain";

      boolean displayHtml = false;

      int msgDisplayMode = getMessageProxy().getDisplayMode();

      // figure out html vs. text
      if (Pooka.getProperty("Pooka.displayHtml", "").equalsIgnoreCase("true")) {
        if (msgInfo.isHtml()) {
          if (msgDisplayMode > MessageProxy.TEXT_ONLY)
            displayHtml = true;

        } else if (msgInfo.containsHtml()) {
          if (msgDisplayMode >= MessageProxy.HTML_PREFERRED)
            displayHtml = true;

        } else {
          // if we don't have any html, just display as text.
        }
      }

      //Original was true, changed to false by Liao
      boolean includeHeaders = false;
      boolean showFullheaders = showFullHeaders();

      // Get the header Information
      String header;
      if(showFullheaders){
        header = getFullHeaderInfo(msgInfo);
      } else {
        String list = Pooka.getProperty("MessageWindow.Header.DefaultHeaders", "From:To:CC:Date:Subject");
        header = getHeaderInfo(msgInfo, list);
      }
      headerPane.setText(header);

      headerPane.repaint();

      // set the content
      if (msgDisplayMode == MessageProxy.RFC_822) {
        content = msgInfo.getRawText();
      } else {
        if (displayHtml) {
          contentType = "text/html";

          if (Pooka.getProperty("Pooka.displayTextAttachments", "").equalsIgnoreCase("true")) {
            content = msgInfo.getHtmlAndTextInlines(includeHeaders, showFullheaders);
          } else {
            content = msgInfo.getHtmlPart(includeHeaders, showFullheaders);
          }

        } else {
          if (Pooka.getProperty("Pooka.displayTextAttachments", "").equalsIgnoreCase("true")) {
            // Is there only an HTML part?  Regardless, we've determined that
            // we will still display it as text.
            if (getMessageProxy().getMessageInfo().isHtml())
              content = msgInfo.getHtmlAndTextInlines(includeHeaders, showFullheaders);
            else
              content = msgInfo.getTextAndTextInlines(includeHeaders, showFullheaders);
          }
          else {
            // Is there only an HTML part?  Regardless, we've determined that
            // we will still display it as text.
            if (getMessageProxy().getMessageInfo().isHtml())
              content = msgInfo.getHtmlPart(includeHeaders, showFullheaders);
            else
              content = msgInfo.getTextPart(includeHeaders, showFullheaders);
          }
        }
      }

      if (content != null)
        messageText.append(content);

      final String finalMessageText = messageText.toString();
      final String finalContentType = contentType;
      hasAttachment = getMessageProxy().hasAttachments(false);
      final boolean hasEncryption = (getMessageProxy().getMessageInfo() == null) ? false : getMessageProxy().getMessageInfo().hasEncryption();
      final boolean contentIsNull = (content == null);

      final boolean fDisplayHtml = displayHtml;

      SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (getDisplayCombo() != null)
              getDisplayCombo().styleUpdated(getMessageProxy().getDisplayMode(), getMessageProxy().getHeaderMode());

            if (getHeaderCombo() != null && getHeaderCombo() != getDisplayCombo()) {
              getHeaderCombo().styleUpdated(getMessageProxy().getDisplayMode(), getMessageProxy().getHeaderMode());
            }

            clearVariableComponents();

            if (! contentIsNull) {
              try {
                editorPane.setContentType(finalContentType);
              } catch (Exception e) {
                // if we can't show the html, just set the type as text/plain.
                editorPane.setEditorKit(new javax.swing.text.StyledEditorKit());
              }
              //editorPane.setEditable(false);
              if (fDisplayHtml) {
                if (fxPanel == null) {
                  fxPanel = new JFXPanel();
                  contentPane.add(fxPanel, HTML_LAYOUT);
                  Platform.setImplicitExit(false);
                  Platform.runLater(new Runnable() {
                      @Override
                      public void run() {
                        WebView browser = new WebView();
                        WebEngine webEngine = browser.getEngine();
                        webEngine.setJavaScriptEnabled(false);
                        //
                        webEngine.getLoadWorker().stateProperty().addListener(new javafx.beans.value.ChangeListener<Worker.State>() {
                            public void changed(ObservableValue ov, Worker.State oldState, Worker.State newState) {
                              if (newState == Worker.State.SUCCEEDED) {

                                EventListener listener = new EventListener() {
                                    public void handleEvent(Event ev) {
                                      try {

                                        ev.stopPropagation();
                                        ev.preventDefault();
                                        Element targetElem = (Element) ev.getCurrentTarget();
                                        String href = targetElem.getAttribute("href");
                                        HyperlinkDispatcher.openLink(new java.net.URL(href));
                                      } catch (Exception e) {
                                        System.out.println("error opening url: " + e.getMessage());
                                      }
                                    }
                                  };

                                // add a click handler for all a tags
                                Document doc = webEngine.getDocument();
                                NodeList elements = doc.getElementsByTagName("a");
                                for (int i = 0; i < elements.getLength(); i++) {
                                  ((EventTarget) elements.item(i)).addEventListener("click", listener, false);
                                }
                              }
                            }
                          });
                        webEngine.loadContent(finalMessageText);
                        Scene scene = new Scene(browser);
                        fxPanel.setScene(scene);
                      }
                    });
                }
                contentPaneLayout.show(contentPane, HTML_LAYOUT);
              } else {
                contentPaneLayout.show(contentPane, TEXT_LAYOUT);
                editorPane.setText(finalMessageText);
                editorPane.setCaretPosition(0);
              }
            }

            if (hasAttachment) {
              attachmentPanel = new AttachmentPane(getMessageProxy());
              layout.putConstraint(SpringLayout.NORTH, attachmentPanel, BORDER, SpringLayout.SOUTH, editorScrollPane);
              layout.putConstraint(SpringLayout.SOUTH, ReadMessageJFXDisplayPanel.this, BORDER, SpringLayout.SOUTH, attachmentPanel);
              layout.getConstraints(attachmentPanel).setWidth(layout.getConstraints(editorScrollPane).getWidth());

              ReadMessageJFXDisplayPanel.this.add(attachmentPanel);

              // set the theme for the attachmentpanel.
              MessageUI mui = getMessageUI();
              if (mui instanceof net.suberic.util.swing.ThemeSupporter) {
                try {
                  Pooka.getUIFactory().getPookaThemeManager().updateUI((net.suberic.util.swing.ThemeSupporter) mui, attachmentPanel, true);
                } catch (Exception etwo) {
                  java.util.logging.Logger.getLogger("Pooka.debug.gui").fine("error setting theme:  " + etwo);
                }
              }

            }

            if (hasEncryption) {
              CryptoPanel cp = new CryptoPanel();
              cryptoStatusDisplay = cp;

              layout.putConstraint(SpringLayout.WEST, cp, BORDER, SpringLayout.EAST, headerScrollPane);
              layout.putConstraint(SpringLayout.NORTH, cp, BORDER, SpringLayout.NORTH, ReadMessageJFXDisplayPanel.this);
              layout.getConstraints(headerScrollPane).setWidth(Spring.sum(layout.getConstraints(editorScrollPane).getWidth(), Spring.minus(Spring.sum(Spring.constant(BORDER), layout.getConstraints(cp).getWidth()))));

              MessageCryptoInfo cryptoInfo = getMessageProxy().getMessageInfo().getCryptoInfo();
              if (cryptoInfo != null)
                cp.cryptoUpdated(cryptoInfo);

              ReadMessageJFXDisplayPanel.this.add(cp);
            }
            layout.layoutContainer(ReadMessageJFXDisplayPanel.this);
            ReadMessageJFXDisplayPanel.this.repaint();
          }
        });
    } else {
      SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            // if getMessageProxy() == null
            //editorPane.setEditable(false);
            editorPane.setText("");
            editorPane.setCaretPosition(0);

            headerPane.setText("");

            clearVariableComponents();

            layout.layoutContainer(ReadMessageJFXDisplayPanel.this);
          }
        });
    }

    keyBindings.setActive(getActions());
  }


}
