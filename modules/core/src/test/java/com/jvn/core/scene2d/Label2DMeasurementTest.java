package com.jvn.core.scene2d;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class Label2DMeasurementTest {
  @Test void onlyMeasuresAlignedLabelsAndPreservesTheirOffsets() {
    int[] measurements = {0};
    double[] drawnX = {Double.NaN};
    Blitter2D blitter = (Blitter2D) Proxy.newProxyInstance(
        Blitter2D.class.getClassLoader(), new Class<?>[] {Blitter2D.class}, (proxy, method, args) -> {
          if (method.getName().equals("measureTextWidth")) {
            measurements[0]++;
            return 100.0;
          }
          if (method.getName().equals("drawText")) drawnX[0] = (double) args[1];
          return null;
        });
    Label2D label = new Label2D("Title");
    label.render(blitter);
    assertEquals(0, measurements[0]);
    assertEquals(0.0, drawnX[0]);
    label.setAlign(Label2D.Align.CENTER);
    label.render(blitter);
    assertEquals(1, measurements[0]);
    assertEquals(-50.0, drawnX[0]);
    label.setAlign(Label2D.Align.RIGHT);
    label.render(blitter);
    assertEquals(2, measurements[0]);
    assertEquals(-100.0, drawnX[0]);
  }
}
