package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import com.jvn.licensing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseAcceptanceDialogTest {
  @TempDir Path temporary;
  @Test void requiresExplicitAgreementAndPreservesDisplayedTerms() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    var store = new AcceptanceStore(temporary.resolve("legal"));
    var terms = LicenseTerms.bundled();
    var failure = new AtomicReference<Throwable>();
    SwingUtilities.invokeAndWait(() -> {
      JDialog dialog = LicenseAcceptanceDialog.create(null, "test", store, terms);
      try {
        dialog.setModal(false); dialog.setVisible(true);
        JTextField name = (JTextField) find(dialog, "acceptanceName");
        JCheckBox consent = (JCheckBox) find(dialog, "acceptanceConsent");
        JButton save = (JButton) find(dialog, "acceptanceSave");
        assertFalse(consent.isSelected()); assertFalse(save.isEnabled());
        name.setText("Test Person"); assertFalse(save.isEnabled());
        consent.doClick(); assertTrue(save.isEnabled());
        assertFalse(Files.exists(store.root()));
        consent.doClick(); name.setText("");
        BufferedImage image = new BufferedImage(dialog.getWidth(), dialog.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        dialog.paint(graphics); graphics.dispose();
        Path destination = Path.of("build/reports/licensing/hub-acceptance-page.png");
        Files.createDirectories(destination.getParent()); ImageIO.write(image, "png", destination.toFile());
        name.setText("Test Person"); consent.doClick();
        Timer closeConfirmation = new Timer(100, event -> {
          for (Window window : Window.getWindows()) if (window != dialog && window instanceof JDialog) window.dispose();
        });
        closeConfirmation.setRepeats(false); closeConfirmation.start();
        save.doClick();
        assertFalse(consent.isSelected()); assertFalse(save.isEnabled());
        assertEquals(AcceptanceStore.State.CURRENT, store.status(terms).state());
      } catch (Throwable e) { failure.set(e); }
      finally { dialog.dispose(); }
    });
    if (failure.get() != null) throw new AssertionError(failure.get());
  }
  private static Component find(Container container, String name) {
    for (Component component : container.getComponents()) {
      if (name.equals(component.getName())) return component;
      if (component instanceof Container nested) {
        Component result = find(nested, name); if (result != null) return result;
      }
    }
    return null;
  }
}
