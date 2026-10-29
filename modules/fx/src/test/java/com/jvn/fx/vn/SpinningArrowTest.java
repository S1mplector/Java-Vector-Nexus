package com.jvn.fx.vn;

import com.jvn.fx.testkit.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class SpinningArrowTest {
  @Test void projectedArrowRotatesAndReducedMotionIsStable() throws Exception {
    FxToolkit.runFx(() -> {
      WritableImage first = render(0, false), second = render(700, false);
      WritableImage still = render(0, true), later = render(700, true);
      int changed = 0, ink = 0;
      for (int y=0;y<64;y++) for(int x=0;x<64;x++) {
        if(first.getPixelReader().getArgb(x,y)!=second.getPixelReader().getArgb(x,y)) changed++;
        if(first.getPixelReader().getArgb(x,y)!=0xffffffff) ink++;
        assertEquals(still.getPixelReader().getArgb(x,y),later.getPixelReader().getArgb(x,y));
      }
      assertTrue(changed>50);assertTrue(ink>100);
    });
  }
  private WritableImage render(long time, boolean still) {
    Canvas canvas=new Canvas(64,64);SpinningArrow.draw(canvas.getGraphicsContext2D(),32,32,time,still);return canvas.snapshot(null,null);
  }
}
