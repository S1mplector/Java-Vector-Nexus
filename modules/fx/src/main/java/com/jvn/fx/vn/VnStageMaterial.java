package com.jvn.fx.vn;

import com.jvn.core.vn.stage.VnStagePreset.SubjectMaterial;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

/** Tangent-space normal-map response with a Blinn–Phong highlight. */
public final class VnStageMaterial {
  private final PixelReader normals;
  private final int width;
  private final int height;
  private final double strength;
  private final double specular;
  private final double gloss;

  public VnStageMaterial(Image normalMap, SubjectMaterial material) {
    normals = normalMap == null ? null : normalMap.getPixelReader();
    width = normalMap == null ? 0 : (int) normalMap.getWidth();
    height = normalMap == null ? 0 : (int) normalMap.getHeight();
    strength = material.normalStrength();
    specular = material.specular();
    gloss = material.gloss();
  }

  public int sample(double u, double v) {
    if (normals == null || width == 0 || height == 0 || strength <= 0) return 0;
    return normals.getArgb((int) Math.round(u * (width - 1)), (int) Math.round(v * (height - 1)));
  }

  public double response(int normal, double lightX, double lightY, double lightHeight) {
    if ((normal >>> 24) == 0) return 1;
    double nx = ((normal >>> 16) & 255) * (2.0 / 255) - 1;
    double ny = 1 - ((normal >>> 8) & 255) * (2.0 / 255);
    double nz = (normal & 255) * (2.0 / 255) - 1;
    double normalLength = Math.sqrt(nx * nx + ny * ny + nz * nz);
    if (normalLength < 1e-6) return 1;
    double inverseLightLength = 1 / Math.sqrt(lightX * lightX + lightY * lightY + lightHeight * lightHeight);
    double lx = lightX * inverseLightLength;
    double ly = lightY * inverseLightLength;
    double lz = lightHeight * inverseLightLength;
    double diffuse = Math.max(0, (nx * lx + ny * ly + nz * lz) / normalLength);
    double modifier = 1 + (diffuse - 1) * strength;
    if (diffuse <= 0 || specular <= 0) return modifier;
    // The viewer points out of the image. Normalize (L + V) for the halfway vector.
    double cosine = (nx * lx + ny * ly + nz * (lz + 1)) / (normalLength * Math.sqrt(2 + 2 * lz));
    return modifier + specular * strength * Math.pow(Math.clamp(cosine, 0, 1), gloss);
  }
}
