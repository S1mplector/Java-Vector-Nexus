package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.vn.stage.VnStagePreset.LightFalloff;
import com.jvn.core.vn.stage.VnStagePreset.LightType;
import com.jvn.core.vn.stage.VnStagePreset.Occluder;
import com.jvn.core.vn.stage.VnStagePreset.Point;
import com.jvn.core.vn.stage.VnStagePreset.SubjectMaterial;
import java.util.List;
import org.junit.jupiter.api.Test;

class VnStageLightFieldTest {
  @Test
  void inverseSquareCurveHasFiniteCenterAndZeroAtRange() {
    var field = new VnStageLightField(LightType.RADIAL, 100, 100, 100, 100, 80, 0.5,
        LightFalloff.INVERSE_SQUARE, List.of());
    assertEquals(1, field.weight(100, 100));
    assertTrue(field.weight(120, 100) > field.weight(150, 100));
    assertEquals(0, field.weight(180, 100));
  }

  @Test
  void occlusionBlocksOnTheFarSideAndSamplesReuseTheSameField() {
    var shape = new Occluder("Wall", 1, 0, true,
        List.of(new Point(0.4, 0.1), new Point(0.5, 0.1), new Point(0.5, 0.9), new Point(0.4, 0.9)));
    var shadows = VnStageShadow.prepare(List.of(shape), 100, 100);
    assertEquals(1, VnStageShadow.transmission(shadows, 20, 50, 30, 50));
    assertEquals(0, VnStageShadow.transmission(shadows, 20, 50, 55, 50));
    var field = new VnStageLightField(LightType.RADIAL, 50, 50, 20, 50, 45, 0.5, LightFalloff.SMOOTH, List.of());
    var samples = new VnStageLightSamples(field, shadows, com.jvn.core.vn.stage.VnStagePreset.ShadowQuality.HARD, 100, 100);
    assertEquals(field.weight(30, 50), samples.weight(30, 50), 1e-7);
    assertEquals(0, samples.weight(55, 50));
  }

  @Test
  void rotatedSurfaceRespondsToLightDirection() {
    VnStageMaterial material = new VnStageMaterial(null, new SubjectMaterial("", 1, 0, 32, List.of()));
    int rightFacing = 0xffff80da;
    assertTrue(material.response(rightFacing, 100, 0, 25) > material.response(rightFacing, -100, 0, 25));
    assertEquals(1, material.response(0, 100, 0, 25));
  }
}
