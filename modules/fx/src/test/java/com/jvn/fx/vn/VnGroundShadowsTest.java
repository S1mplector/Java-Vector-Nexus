package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.stage.VnStagePresetLoader;
import java.io.StringReader;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class VnGroundShadowsTest {
  @Test
  void projectFloorShadowAwayFromLightAndKeepFeetAnchored() throws Exception {
    VnStagePreset stage = preset("occluder.0.contactY=0.58\n");
    var caster = stage.getOccluders().getFirst();
    var shadow = VnGroundShadows.project(stage.getLights().getFirst(), caster);
    assertTrue(shadow.getFirst().x() > caster.polygon().getFirst().x());
    assertTrue(shadow.getFirst().y() < 0.58);
    assertTrue(shadow.stream().allMatch(point -> point.y() > 0.27));
    assertEquals(caster.polygon().get(2).x(), shadow.get(2).x(), 1e-9);
    assertEquals(caster.polygon().get(2).y(), shadow.get(2).y(), 1e-9);
    byte[] coverage = VnGroundShadows.mask(stage, 320, 180);
    assertEquals(0, coverage[90 * 320 + 208]);
    int darkest = 0;
    for (byte pixel : coverage) darkest = Math.max(darkest, pixel & 255);
    assertTrue(darkest > 30);
  }

  @Test
  void explicitFloorContactsAndRoundVolumeChangeTheProjection() throws Exception {
    VnStagePreset flat = preset("occluder.0.contactY=0.58\n");
    VnStagePreset depth = preset("occluder.0.floorContacts=0.56,0.52,0.58,0.58\n");
    VnStagePreset round = preset("occluder.0.roundFootprint=true\noccluder.0.heightScale=0.4\n");
    var baseline = VnGroundShadows.project(flat.getLights().getFirst(), flat.getOccluders().getFirst());
    assertNotEquals(baseline, VnGroundShadows.project(depth.getLights().getFirst(), depth.getOccluders().getFirst()));
    assertEquals(40, VnGroundShadows.project(round.getLights().getFirst(), round.getOccluders().getFirst()).size());
  }

  @Test
  void overlappingSoftFootprintsMatchFullFrameBlurAtTheRightEdge() throws Exception {
    int width = 160;
    int height = 120;
    String first = offsetCaster(0, "0.30", "0.85");
    String second = offsetCaster(0, "0.34", "0.5");
    String combined = "occluders=2\nlight.0.shadowQuality=soft\n" + first + offsetCaster(1, "0.34", "0.5");
    byte[] hardA = VnGroundShadows.mask(preset(first), width, height);
    byte[] hardB = VnGroundShadows.mask(preset(second), width, height);
    byte[] expected = new byte[width * height];
    boolean reachesEdge = false;
    boolean overlaps = false;
    // At 120px and full softness the renderer uses a two-pixel blur radius.
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int a = boxBlurAt(hardA, width, height, x, y, 2);
        int b = boxBlurAt(hardB, width, height, x, y, 2);
        overlaps |= a > 0 && b > 0;
        reachesEdge |= x == width - 1 && b > 0;
        expected[y * width + x] = (byte) Math.max(a, b);
      }
    }
    assertTrue(overlaps, "Fixture should exercise overlapping blur kernels");
    assertTrue(reachesEdge, "Fixture should exercise clipping at the canvas edge");
    assertArrayEquals(expected, VnGroundShadows.mask(preset(combined), width, height));
  }

  private static String offsetCaster(int index, String offset, String opacity) {
    return """
        occluder.%1$d.polygon=0.62,0.35;0.68,0.35;0.68,0.58;0.62,0.58
        occluder.%1$d.softness=1
        occluder.%1$d.heightScale=0.01
        occluder.%1$d.footprintDepth=0.08
        occluder.%1$d.shadowFraming=%2$s,0,1,1,0
        occluder.%1$d.opacity=%3$s
        """.formatted(index, offset, opacity);
  }

  @Test
  void floorLightingNeedsNoMaterialMapWhenThereAreNoOccluders() throws Exception {
    assertNull(VnGroundShadows.materialMap(preset("occluders=0\n"), 320, 180, true));
  }

  private static int boxBlurAt(byte[] mask, int width, int height, int x, int y, int radius) {
    int sum = 0;
    for (int row = Math.max(0, y - radius); row <= Math.min(height - 1, y + radius); row++) {
      for (int col = Math.max(0, x - radius); col <= Math.min(width - 1, x + radius); col++) {
        sum += mask[row * width + col] & 255;
      }
    }
    int window = 2 * radius + 1;
    return sum / (window * window);
  }

  private static VnStagePreset preset(String casterSettings) throws Exception {
    Properties props = new Properties();
    props.load(new StringReader("""
        lights=1
        light.0.shape=radial
        light.0.layer=background
        light.0.source=0.42,0.14
        light.0.ground=0.42,0.69,0.27,0.4
        light.0.shadowQuality=hard
        occluders=1
        occluder.0.polygon=0.62,0.35;0.68,0.35;0.68,0.58;0.62,0.58
        """ + casterSettings));
    return VnStagePresetLoader.load("test", "", props);
  }
}
