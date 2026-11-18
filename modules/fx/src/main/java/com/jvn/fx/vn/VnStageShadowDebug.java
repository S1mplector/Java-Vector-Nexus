package com.jvn.fx.vn;

import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.stage.VnStagePreset.Light;
import java.util.ArrayList;
import java.util.List;

/** Inspect the same coverage and ray tests that shade gameplay pixels. */
public final class VnStageShadowDebug {
  private final VnStagePreset stage;
  private final VnStageLightField field;
  private final List<VnStageShadow> blockers;
  private final Light light;
  private final int width;
  private final int height;

  public VnStageShadowDebug(VnStagePreset preset, Light light, int width, int height) {
    this.stage = preset;
    this.light = light;
    this.width = width;
    this.height = height;
    field = VnStageLightField.forLight(light, width, height, 1);
    blockers = light.castsShadows() ? VnStageShadow.prepare(preset.getOccluders(), width, height) : List.of();
  }

  public int[] mask() {
    int[] pixels = new int[width * height];
    if (light.groundProjection() != null) {
      byte[] coverage = VnGroundShadows.mask(stage, width, height, light);
      if (coverage != null) for (int i = 0; i < coverage.length; i++) {
        int amount = coverage[i] & 255;
        if (amount > 0) pixels[i] = (Math.min(200, amount * 2) << 24) | 0xff406d;
      }
      return pixels;
    }
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        double blocked = blocked(x + 0.5, y + 0.5);
        if (blocked > 0) pixels[y * width + x] = ((int) Math.round(blocked * 184) << 24) | 0xff406d;
      }
    }
    return pixels;
  }

  public List<Ray> sampledRays() {
    List<Ray> rays = new ArrayList<>();
    if (light.groundProjection() != null) {
      var plane = light.groundProjection();
      double sx = plane.groundX() * width;
      double sy = plane.groundY() * height;
      for (var caster : stage.getOccluders()) {
        if (!caster.enabled()) continue;
        List<VnStagePreset.Point> projected = VnGroundShadows.project(light, caster);
        int stride = Math.max(1, projected.size() / 3);
        for (int i = 0; i < projected.size(); i += stride) {
          var point = projected.get(i);
          if (point.x() >= 0 && point.x() <= 1 && point.y() >= 0 && point.y() <= 1) {
            rays.add(new Ray(sx, sy, point.x() * width, point.y() * height, caster.opacity()));
          }
        }
      }
      rays.add(new Ray(light.sourceX() * width, light.sourceY() * height, sx, sy, 0));
      return rays;
    }
    for (int row = 1; row <= 6; row++) {
      for (int col = 1; col <= 11; col++) {
        double x = col * width / 12.0;
        double y = row * height / 7.0;
        if (field.weight(x, y) > 0.015) {
          rays.add(new Ray(field.raySourceX(x), field.raySourceY(y), x, y, blocked(x, y)));
        }
      }
    }
    return rays;
  }

  private double blocked(double x, double y) {
    if (field.weight(x, y) <= 0.001) return 0;
    return 1 - field.transmission(blockers, x, y, light.shadowQuality());
  }

  public record Ray(double sourceX, double sourceY, double x, double y, double blocked) {}
}
