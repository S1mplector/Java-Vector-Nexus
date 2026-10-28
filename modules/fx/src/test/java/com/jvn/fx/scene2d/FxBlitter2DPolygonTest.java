package com.jvn.fx.scene2d;

import static org.junit.jupiter.api.Assertions.*;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class FxBlitter2DPolygonTest {
  @Test void queuedPolygonsKeepTheirCoordinatesWhenBuffersAreReused() throws Exception {
    FxToolkit.runFx(() -> {
      Canvas canvas = new Canvas(80, 40);
      FxBlitter2D blitter = new FxBlitter2D(canvas.getGraphicsContext2D());
      blitter.setFill(1, 0, 0, 1);
      blitter.fillPolygon(new double[] {2, 2, 30, 2, 30, 30, 2, 30});
      blitter.setFill(0, 0, 1, 1);
      blitter.fillPolygon(new double[] {42, 2, 70, 2, 42, 30});
      blitter.setStroke(0, 1, 0, 1);
      blitter.strokePolygon(new double[] {40, 35, 70, 35, 70, 39});
      var pixels = canvas.snapshot(new SnapshotParameters(), null).getPixelReader();
      assertEquals(Color.RED, pixels.getColor(10, 10));
      assertEquals(Color.BLUE, pixels.getColor(45, 5));
      assertEquals(Color.WHITE, pixels.getColor(65, 25));
    });
  }
}
