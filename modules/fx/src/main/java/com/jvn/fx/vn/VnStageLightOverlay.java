package com.jvn.fx.vn;

import java.util.ArrayList;
import java.util.List;

import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.stage.VnStagePreset.LightLayer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/** Prepared light textures for canvas passes that sit above gameplay artwork. */
public final class VnStageLightOverlay {
  private static final double MAX_TEXTURE_SIDE = 640;
  private final List<Patch> patches;

  private VnStageLightOverlay(List<Patch> patches) { this.patches = patches; }

  public static VnStageLightOverlay prepare(VnStagePreset stage, LightLayer layer, double width, double height) {
    List<Patch> patches = new ArrayList<>();
    if (width <= 0 || height <= 0) return new VnStageLightOverlay(patches);
    double scale = Math.min(1, MAX_TEXTURE_SIDE / Math.max(width, height));
    double rasterWidth = width * scale;
    double rasterHeight = height * scale;
    List<VnStageShadow> shadows = VnStageShadow.prepare(stage.getOccluders(), rasterWidth, rasterHeight);
    for (VnStagePreset.Light light : stage.getLights()) {
      if (light.layer() != layer || !stage.isActive(light)) continue;
      VnStageLightField field = VnStageLightField.forLight(light, width, height, scale);
      int left = (int) Math.max(0, Math.floor(field.left()));
      int top = (int) Math.max(0, Math.floor(field.top()));
      int right = (int) Math.min(Math.ceil(rasterWidth), Math.ceil(field.right()));
      int bottom = (int) Math.min(Math.ceil(rasterHeight), Math.ceil(field.bottom()));
      if (left >= right || top >= bottom) continue;
      Color color = VnStageLightingSupport.parseColor(light.color(), Color.web("#ffd7a8"));
      int rgb = ((int) Math.round(color.getRed() * 255) << 16)
          | ((int) Math.round(color.getGreen() * 255) << 8) | (int) Math.round(color.getBlue() * 255);
      double opacity = light.intensity() * (layer == LightLayer.FOREGROUND ? 0.16 : 0.18) * color.getOpacity() * 255;
      int w = right - left;
      int h = bottom - top;
      int[] pixels = new int[w * h];
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          double px = left + x + 0.5;
          double py = top + y + 0.5;
          double weight = field.weight(px, py);
          if (weight <= 0) continue;
          if (light.castsShadows() && light.groundProjection() == null) weight *= field.transmission(shadows, px, py, light.shadowQuality());
          pixels[y * w + x] = ((int) Math.round(opacity * weight) << 24) | rgb;
        }
      }
      WritableImage image = new WritableImage(w, h);
      image.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
      patches.add(new Patch(image, VnStageLightBlend.canvasMode(light.blend()), left / scale, top / scale, w / scale, h / scale));
    }
    return new VnStageLightOverlay(patches);
  }

  public void draw(GraphicsContext graphics) {
    if (patches.isEmpty()) return;
    graphics.save();
    for (Patch patch : patches) {
      graphics.setGlobalBlendMode(patch.blend());
      graphics.drawImage(patch.image(), patch.x(), patch.y(), patch.width(), patch.height());
    }
    graphics.restore();
  }

  private record Patch(Image image, BlendMode blend, double x, double y, double width, double height) {}
}
