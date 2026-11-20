package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;

class BackgroundPreviewSourceTest {
  @Test
  void editsReuseTheCapturedPlateAndReplacementImagesGetTheirOwnPixels() {
    WritableImage plate = new WritableImage(2, 1);
    plate.getPixelWriter().setArgb(0, 0, 0xff123456);
    var source = new VnStageLightingSupport.BackgroundPreviewSource(plate);
    assertEquals(0xff123456, source.render(null, 2, 1).getPixelReader().getArgb(0, 0));
    plate.getPixelWriter().setArgb(0, 0, 0xffabcdef);
    assertEquals(0xff123456, source.render(null, 2, 1).getPixelReader().getArgb(0, 0));
    assertEquals(0xffabcdef, VnStageLightingSupport.renderBackgroundPreview(plate, null, 2, 1)
        .getPixelReader().getArgb(0, 0));
    assertTrue(source.matches(plate));
    assertFalse(source.matches(new WritableImage(2, 1)));
  }
}
