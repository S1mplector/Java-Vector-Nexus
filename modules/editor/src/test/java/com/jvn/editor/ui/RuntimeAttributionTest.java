package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.jvn.fx.RuntimeAttributionOverlay;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.embed.swing.SwingFXUtils;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class RuntimeAttributionTest {
  @Test void settingsCreditStaysVisibleAndHasVersionAndSvgAtSmallAndLargeSizes() throws Exception {
    FxToolkit.runFx(() -> {
      int[] actions = {0,0};
      var overlay = new RuntimeAttributionOverlay(() -> actions[0]++, () -> actions[1]++);
      var scene = new Scene(overlay, 640, 360);
      overlay.applyCss(); overlay.layout();
      ((Button)overlay.lookup("#jvn-runtime-settings")).fire();
      assertEquals(1,actions[0]);
      overlay.showSettingsCredit(true);
      for (int[] size : new int[][] {{320,240},{640,360},{1280,720}}) {
        overlay.resize(size[0],size[1]); overlay.applyCss(); overlay.layout();
        var credit=overlay.lookup("#jvn-settings-credit");
        assertTrue(credit.isVisible()); assertEquals(1,credit.getOpacity());
        assertEquals("Made with JVN (0.7.0)",credit.getAccessibleText());
        assertEquals(72,overlay.reservedHeightProperty().get());
        assertTrue(credit.getBoundsInParent().getMaxY() <= size[1]+1);
        assertEquals(1,credit.lookupAll(".image-view").size());
      }
      overlay.resize(640,360); overlay.layout();
      Path image=Path.of("build/reports/licensing/runtime-settings-credit.png");
      Files.createDirectories(image.getParent());
      ImageIO.write(SwingFXUtils.fromFXImage(overlay.snapshot(null,null),null),"png",image.toFile());
      ((Button)overlay.lookup("#jvn-runtime-settings")).fire();
      assertEquals(1,actions[1]);
      overlay.showSettingsCredit(false);
      assertEquals(0,overlay.reservedHeightProperty().get());
      return null;
    });
  }
}
