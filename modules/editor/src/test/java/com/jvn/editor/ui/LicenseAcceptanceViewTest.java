package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import com.jvn.licensing.*;
import java.nio.file.*;
import java.util.List;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.*;
import javafx.stage.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FxToolkitExtension.class)
class LicenseAcceptanceViewTest {
  @TempDir Path temporary;

  @Test void openingCheckingAndClosingNeverImpliesAcceptance() throws Exception {
    var store = new AcceptanceStore(temporary.resolve("legal"));
    var terms = LicenseTerms.bundled();
    FxToolkit.runFx(() -> {
      Stage page = LicenseAcceptanceView.create(null, "test", store, terms);
      try {
        page.show(); page.getScene().getRoot().applyCss(); page.getScene().getRoot().layout();
        CheckBox consent = (CheckBox) page.getScene().lookup("#acceptanceConsent");
        Button save = (Button) page.getScene().lookup("#acceptanceSave");
        TextField name = (TextField) page.getScene().lookup("#acceptanceName");
        assertFalse(consent.isSelected()); assertTrue(save.isDisabled());
        name.setText("Test Person"); assertTrue(save.isDisabled());
        consent.setSelected(true); assertFalse(save.isDisabled());
        name.setText(" "); assertTrue(save.isDisabled());
        consent.setSelected(false); name.clear();
        Path image = Path.of("build/reports/licensing/acceptance-page.png");
        Files.createDirectories(image.getParent());
        ImageIO.write(SwingFXUtils.fromFXImage(page.getScene().snapshot(null), null), "png", image.toFile());
      } finally { page.close(); }
      return null;
    });
    assertFalse(Files.exists(store.root()));
  }

  @Test void explicitActionRecordsTheDisplayedDocumentsAndResetsCheckbox() throws Exception {
    var store = new AcceptanceStore(temporary.resolve("legal"));
    var terms = LicenseTerms.bundled();
    FxToolkit.runFx(() -> {
      Stage page = LicenseAcceptanceView.create(null, "test", store, terms);
      try {
        page.show(); page.getScene().getRoot().applyCss(); page.getScene().getRoot().layout();
        ((TextField) page.getScene().lookup("#acceptanceName")).setText("Test Person");
        CheckBox consent = (CheckBox) page.getScene().lookup("#acceptanceConsent");
        consent.setSelected(true);
        Platform.runLater(() -> List.copyOf(Window.getWindows()).stream()
            .filter(window -> window != page).forEach(Window::hide));
        ((Button) page.getScene().lookup("#acceptanceSave")).fire();
        assertFalse(consent.isSelected());
        assertEquals(AcceptanceStore.State.CURRENT, store.status(terms).state());
      } finally { page.close(); }
      return null;
    });
  }
}
