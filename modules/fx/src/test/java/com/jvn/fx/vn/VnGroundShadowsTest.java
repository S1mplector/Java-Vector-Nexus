package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
