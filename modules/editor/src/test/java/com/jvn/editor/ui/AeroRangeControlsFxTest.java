package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.util.List;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class AeroRangeControlsFxTest {
  @Test
  void bothThemesUseAeroArrowButtonsAndPointedSliderThumbs() throws Exception {
    for (String theme : List.of("editor.css", "editor-light.css")) {
      FxToolkit.runFx(() -> {
        ScrollBar horizontal = new ScrollBar();
        ScrollBar vertical = new ScrollBar();
        vertical.setOrientation(Orientation.VERTICAL);
        Slider slider = new Slider(0, 100, 50);
        VBox root = new VBox(new HBox(vertical, new VBox(horizontal, slider)));
        Scene scene = new Scene(root, 400, 200);
        scene.getStylesheets().add(getClass().getResource("/com/jvn/editor/" + theme).toExternalForm());
        root.applyCss();
        root.layout();
        assertEquals(17, horizontal.prefHeight(-1));
        assertEquals(17, vertical.prefWidth(-1));
        for (ScrollBar bar : List.of(horizontal, vertical)) {
          Region arrow = (Region) bar.lookup(".decrement-arrow");
          assertNotNull(arrow.getShape());
          assertTrue(((Region) bar.lookup(".decrement-button")).prefWidth(-1) >= 17);
        }
        Region thumb = (Region) slider.lookup(".thumb");
        assertNotNull(thumb.getShape());
        assertTrue(thumb.prefHeight(-1) > thumb.prefWidth(-1));
        return null;
      });
    }
  }
}
