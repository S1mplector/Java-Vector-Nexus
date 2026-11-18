package com.jvn.core.vn.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VnCasterSegmentationTest {
  @Test
  void retainsConcavitiesAndSeparateSolidComponents() {
    int[] pixels = new int[100];
    for (int y = 1; y <= 5; y++) for (int x = 1; x <= 5; x++) {
      if (x <= 2 || y >= 4) pixels[y * 10 + x] = 0xff336677;
    }
    for (int y = 7; y < 9; y++) for (int x = 7; x < 9; x++) pixels[y * 10 + x] = 0xffffffff;
    var contours = VnCasterSegmentation.trace(10, 10, pixels, 128, 0.25);
    assertEquals(2, contours.size());
    assertTrue(contours.getFirst().size() >= 6);
    assertTrue(contours.getFirst().contains(new VnStagePreset.Point(0.3, 0.4)));
  }

  @Test
  void recognizesWhiteInAnOpaqueGrayscaleMask() {
    int[] pixels = {0xff000000, 0xffffffff, 0xffffffff, 0xff000000,
        0xff000000, 0xffffffff, 0xffffffff, 0xff000000};
    var contours = VnCasterSegmentation.trace(4, 2, pixels, 128, 0.25);
    assertEquals(1, contours.size());
    assertEquals(4, contours.getFirst().size());
  }
}
