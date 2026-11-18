package com.jvn.hub;

import com.jvn.licensing.AcceptanceStore;
import com.jvn.licensing.LicenseTerms;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.nio.file.Files;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Voluntary acceptance page. Opening or dismissing it never records consent. */
public final class LicenseAcceptanceDialog {
  private LicenseAcceptanceDialog() {}

  public static void show(Frame owner, String appVersion) {
    try {
      LicenseTerms terms = LicenseTerms.bundled();
      AcceptanceStore store = AcceptanceStore.local();
      create(owner, appVersion, store, terms).setVisible(true);
    } catch (Exception e) { error(owner, e); }
  }

  public static boolean requireAcceptance() {
    try {
      var terms = LicenseTerms.bundled(); var store = AcceptanceStore.local();
      if (store.status(terms).state() == AcceptanceStore.State.CURRENT) return true;
      create(null, LicenseTerms.applicationVersion(), store, terms, true).setVisible(true);
      return store.status(terms).state() == AcceptanceStore.State.CURRENT;
    } catch (Exception e) { error(null, e); return false; }
  }

  static JDialog create(Frame owner, String appVersion, AcceptanceStore store, LicenseTerms terms) {
    return create(owner, appVersion, store, terms, false);
  }

  static JDialog create(Frame owner, String appVersion, AcceptanceStore store, LicenseTerms terms, boolean required) {
      double scale = JvnHub.currentUiScale();
      JDialog dialog = new JDialog(owner, "JVN License & Terms", true);
      dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      JPanel content = new JPanel(new BorderLayout(12, 12));
      content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
      JLabel status = new JLabel();
      Runnable refresh = () -> status.setText(store.status(terms).detail());
      refresh.run();
      JPanel heading = new JPanel(new BorderLayout(0, 10));
      JLabel title = new JLabel("License & Terms", HubIcon.of(HubIcon.Kind.DOCUMENTATION, 28), JLabel.LEADING);
      title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 19f));
      JPanel titleRow = new JPanel(new BorderLayout(16, 0));
      titleRow.add(title, BorderLayout.WEST);
      status.setName("legalStatus");
      titleRow.add(status, BorderLayout.EAST);
      heading.add(titleRow, BorderLayout.NORTH);
      heading.add(new JLabel(required ? "Accept the current license and terms to use JVN. Declining closes this application."
          : "Review the documents below. Closing this page does not record acceptance."), BorderLayout.SOUTH);
      content.add(heading, BorderLayout.NORTH);
      JTabbedPane documents = new JTabbedPane();
      documents.addTab("License " + terms.licenseVersion(), HubIcon.of(HubIcon.Kind.DOCUMENTATION, 20),
          LegalMarkdown.view(terms.license(), scale));
      documents.addTab("Terms " + terms.termsVersion(), HubIcon.of(HubIcon.Kind.DOCUMENT, 20),
          LegalMarkdown.view(terms.terms(), scale));
      documents.addTab("Receipt details", HubIcon.of(HubIcon.Kind.RECEIPT, 20), text("Storage: " + store.root() + "\n\nBundle SHA-256:\n" + terms.bundleHash()
          + "\n\nReceipts contain your supplied name, optional organization, local time, document copies and a local signature."
          + "\nNothing is uploaded. Local signatures do not verify identity or provide a trusted timestamp."
          + "\nYou can export the receipt or delete local records at the storage location."
          + "\nExports include your supplied name. The private signing key is never exported."));
      content.add(documents, BorderLayout.CENTER);
      JPanel actions = new JPanel(new BorderLayout(8, 8));
      JPanel fields = new JPanel(new GridLayout(1, 2, 16, 0));
      JTextField name = new JTextField(24);
      name.setName("acceptanceName");
      JTextField organization = new JTextField(24);
      fields.add(field("Your name · required, self-declared", name));
      fields.add(field("Organization · optional", organization));
      actions.add(fields, BorderLayout.NORTH);
      JPanel consentPanel = new JPanel(new BorderLayout(4, 4));
      JCheckBox consent = new JCheckBox("I agree to the License and Terms of Use");
      consent.setName("acceptanceConsent");
      JTextArea consentText = new JTextArea(LicenseTerms.CONSENT);
      consentText.setEditable(false); consentText.setLineWrap(true); consentText.setWrapStyleWord(true);
      consentText.setOpaque(false); consentText.setRows(2);
      consentText.setName("legalMuted");
      consentPanel.add(consentText, BorderLayout.NORTH);
      consentPanel.add(consent, BorderLayout.SOUTH);
      actions.add(consentPanel, BorderLayout.CENTER);
      JPanel buttons = new JPanel(new BorderLayout(12, 0));
      buttons.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
      JButton save = new JButton("Save documents…", HubIcon.of(HubIcon.Kind.SAVE, 16));
      save.addActionListener(event -> {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
        try {
          var folder = Files.createTempDirectory(chooser.getSelectedFile().toPath(), "jvn-terms-");
          Files.writeString(folder.resolve("LICENSE.md"), terms.license());
          Files.writeString(folder.resolve("TERMS.md"), terms.terms());
          JOptionPane.showMessageDialog(dialog, "Documents saved to " + folder);
        } catch (Exception e) { error(dialog, e); }
      });
      JButton export = new JButton("Export receipt…", HubIcon.of(HubIcon.Kind.EXPORT, 16));
      export.setEnabled(store.status(terms).receipt() != null);
      export.addActionListener(event -> {
        var current = store.status(terms);
        if (current.receipt() == null) { refresh.run(); export.setEnabled(false); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("jvn-acceptance-receipt.zip"));
        chooser.setDialogTitle("Export receipt — includes your supplied name");
        if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
        try {
          AcceptanceStore.exportReceipt(current.receipt(), chooser.getSelectedFile().toPath());
          JOptionPane.showMessageDialog(dialog, "Receipt exported. Keep your copy secure.");
        } catch (Exception e) { error(dialog, e); }
      });
      JButton accept = new JButton("Accept and save receipt", HubIcon.of(HubIcon.Kind.CHECK, 16));
      accept.setName("acceptanceSave");
      accept.setEnabled(false);
      Runnable enable = () -> accept.setEnabled(consent.isSelected() && !name.getText().isBlank());
      consent.addActionListener(event -> enable.run());
      name.getDocument().addDocumentListener(new DocumentListener() {
        public void insertUpdate(DocumentEvent e) { enable.run(); }
        public void removeUpdate(DocumentEvent e) { enable.run(); }
        public void changedUpdate(DocumentEvent e) { enable.run(); }
      });
      accept.addActionListener(event -> {
        try {
          store.accept(terms, terms.bundleHash(), name.getText(), organization.getText(), appVersion,
              "hub-checkbox-v1", consent.isSelected());
          consent.setSelected(false); enable.run(); refresh.run(); export.setEnabled(true);
          if (required) dialog.dispose();
          else JOptionPane.showMessageDialog(dialog, "Acceptance saved locally. You can now export your receipt.");
        } catch (Exception e) { error(dialog, e); }
      });
      JButton close = new JButton(required ? "Decline and exit" : "Close",
          HubIcon.of(required ? HubIcon.Kind.CANCEL : HubIcon.Kind.CLOSE, 16));
      close.addActionListener(event -> dialog.dispose());
      JPanel utilities = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
      utilities.add(save); utilities.add(export);
      JPanel decisions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
      decisions.add(close); decisions.add(accept);
      buttons.add(utilities, BorderLayout.WEST); buttons.add(decisions, BorderLayout.EAST);
      actions.add(buttons, BorderLayout.SOUTH);
      content.add(actions, BorderLayout.SOUTH);
      graphite(content);
      scaleComponents(content, scale);
      // Keep the complete form reachable on small work areas without shrinking text.
      content.setPreferredSize(new Dimension(px(980, scale), px(740, scale)));
      JScrollPane viewport = new JScrollPane(content);
      viewport.setBorder(BorderFactory.createEmptyBorder());
      viewport.getVerticalScrollBar().setUnitIncrement(px(24, scale));
      viewport.getHorizontalScrollBar().setUnitIncrement(px(24, scale));
      dialog.setContentPane(viewport);
      var configuration = dialog.getGraphicsConfiguration();
      var available = new java.awt.Rectangle(configuration.getBounds());
      var insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(configuration);
      available.x += insets.left; available.y += insets.top;
      available.width -= insets.left + insets.right;
      available.height -= insets.top + insets.bottom;
      Dimension size = sizeForDisplay(scale, available.width, available.height);
      dialog.setMinimumSize(new Dimension(Math.min(640, size.width), Math.min(480, size.height)));
      dialog.setSize(size);
      dialog.setLocation(available.x + (available.width - size.width) / 2,
          available.y + (available.height - size.height) / 2);
      return dialog;
  }

  static Dimension sizeForDisplay(double scale, int width, int height) {
    return new Dimension(Math.min(px(1040, scale), Math.max(1, width - 32)),
        Math.min(px(840, scale), Math.max(1, height - 32)));
  }

  private static int px(int value, double scale) {
    return Math.max(1, (int) Math.round(value * scale));
  }

  private static void scaleComponents(java.awt.Component component, double scale) {
    // Explicit base sizes avoid multiplying fonts already scaled by Hub defaults.
    if (component.getFont() != null) {
      float base = component instanceof JLabel label && label.getIcon() != null ? 21f : 13f;
      component.setFont(component.getFont().deriveFont((float) (base * scale)));
    }
    if (component instanceof JButton button) {
      button.setMargin(new java.awt.Insets(px(9, scale), px(12, scale), px(9, scale), px(12, scale)));
      button.setIcon(scaleIcon(button.getIcon(), scale));
      button.setBorder(BorderFactory.createEmptyBorder(px(10, scale), px(14, scale), px(10, scale), px(14, scale)));
    } else if (component instanceof JCheckBox box) {
      box.setIcon(scaleIcon(box.getIcon(), scale));
    } else if (component instanceof JLabel label) {
      label.setIcon(scaleIcon(label.getIcon(), scale));
    }
    if (component instanceof JTabbedPane tabs)
      for (int i = 0; i < tabs.getTabCount(); i++) tabs.setIconAt(i, scaleIcon(tabs.getIconAt(i), scale));
    if (component instanceof java.awt.Container container)
      for (var child : container.getComponents()) scaleComponents(child, scale);
  }

  private static Icon scaleIcon(Icon icon, double scale) {
    if (icon == null) return null;
    return new Icon() {
      public int getIconWidth() { return px(icon.getIconWidth(), scale); }
      public int getIconHeight() { return px(icon.getIconHeight(), scale); }
      public void paintIcon(java.awt.Component c, java.awt.Graphics graphics, int x, int y) {
        var g = (java.awt.Graphics2D) graphics.create();
        try { g.translate(x, y); g.scale(scale, scale); icon.paintIcon(c, g, 0, 0); }
        finally { g.dispose(); }
      }
    };
  }

  private static void graphite(java.awt.Component component) {
    component.setBackground(java.awt.Color.decode("#242424"));
    component.setForeground(java.awt.Color.decode("#e7e7e7"));
    if (component instanceof JTextArea || component instanceof JTextField || component instanceof JEditorPane) {
      component.setBackground(java.awt.Color.decode("#1c1c1c"));
      if (component instanceof javax.swing.text.JTextComponent text) {
        text.setCaretColor(java.awt.Color.WHITE);
        text.setSelectionColor(java.awt.Color.decode("#595959"));
      }
    }
    if (component instanceof JTabbedPane tabs) {
      tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
        @Override protected void installDefaults() {
          super.installDefaults();
          highlight = java.awt.Color.decode("#555555");
          lightHighlight = highlight;
          shadow = java.awt.Color.decode("#303030");
          darkShadow = shadow;
          focus = java.awt.Color.decode("#aaaaaa");
        }
        @Override protected void paintTabBackground(java.awt.Graphics g, int placement, int index,
                                                    int x, int y, int width, int height, boolean selected) {
          g.setColor(java.awt.Color.decode(selected ? "#414141" : "#303030"));
          g.fillRect(x, y, width, height);
        }
      });
    }
    if (component instanceof JScrollPane pane) {
      pane.setBorder(BorderFactory.createLineBorder(java.awt.Color.decode("#414141")));
      for (JScrollBar bar : new JScrollBar[] {pane.getVerticalScrollBar(), pane.getHorizontalScrollBar()}) {
        JvnHub.styleScrollBar(bar);
        bar.setUnitIncrement(24);
      }
    }
    if (component instanceof JTextField field)
      field.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(java.awt.Color.decode("#484848")),
          BorderFactory.createEmptyBorder(5, 8, 5, 8)));
    if (component instanceof JButton button) {
      button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
        @Override public void paint(java.awt.Graphics graphics, JComponent c) {
          boolean primary = "acceptanceSave".equals(button.getName());
          var g = (java.awt.Graphics2D) graphics.create();
          g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
              java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
          String fill = !button.isEnabled() ? "#2c2c2c" : button.getModel().isPressed()
              ? "#484848" : button.getModel().isRollover() ? "#505050" : primary ? "#525252" : "#363636";
          g.setColor(java.awt.Color.decode(fill));
          g.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 8, 8);
          g.setColor(java.awt.Color.decode(button.hasFocus() ? "#aaaaaa" : "#484848"));
          g.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 8, 8);
          g.dispose();
          super.paint(graphics, c);
        }
        @Override protected void paintText(java.awt.Graphics g, AbstractButton b,
            java.awt.Rectangle rect, String value) {
          g.setColor(java.awt.Color.decode(b.isEnabled() ? "#ededed" : "#858585"));
          var metrics = g.getFontMetrics();
          javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(
              g, value, b.getDisplayedMnemonicIndex(), rect.x, rect.y + metrics.getAscent());
        }
      });
      button.setContentAreaFilled(false);
      button.setOpaque(false);
      button.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
      button.setRolloverEnabled(true);
      if ("acceptanceSave".equals(button.getName()))
        button.setFont(button.getFont().deriveFont(java.awt.Font.BOLD));
    }
    if (component instanceof JCheckBox box) {
      box.setUI(new javax.swing.plaf.basic.BasicCheckBoxUI());
      box.setOpaque(false);
      box.setIcon(HubIcon.selection(false, 20));
      box.setIconTextGap(8);
    }
    if ("legalMuted".equals(component.getName()) || "legalStatus".equals(component.getName()))
      component.setForeground(java.awt.Color.decode("#aaaaaa"));
    if (component instanceof java.awt.Container container)
      for (var child : container.getComponents()) graphite(child);
  }



  private static JPanel field(String label, JTextField input) {
    JPanel panel = new JPanel(new BorderLayout(0, 6));
    panel.add(new JLabel(label), BorderLayout.NORTH);
    panel.add(input, BorderLayout.CENTER);
    input.getAccessibleContext().setAccessibleName(label);
    return panel;
  }

  private static JScrollPane text(String value) {
    JTextArea text = new JTextArea(value);
    text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true);
    text.setCaretPosition(0); text.setMargin(new java.awt.Insets(12, 12, 12, 12));
    return new JScrollPane(text);
  }
  private static void error(java.awt.Component owner, Exception e) {
    JOptionPane.showMessageDialog(owner, "Could not complete the license operation. " + e.getMessage(),
        "License operation failed", JOptionPane.ERROR_MESSAGE);
  }
}
