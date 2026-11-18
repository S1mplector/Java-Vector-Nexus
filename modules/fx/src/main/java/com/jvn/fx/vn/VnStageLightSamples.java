package com.jvn.fx.vn;

import java.util.List;
import com.jvn.core.vn.stage.VnStagePreset.ShadowQuality;

/** Scene-space light and shadow samples reused as sprites move through the stage. */
final class VnStageLightSamples {
  private static final double SAMPLES_ALONG_LONG_EDGE = 640;
  private final double step;
  private final int left;
  private final int top;
  private final int width;
  private final int height;
  private final float[] values;

  VnStageLightSamples(VnStageLightField field, List<VnStageShadow> shadows, ShadowQuality quality,
      double canvasWidth, double canvasHeight) {
    step = Math.max(1, Math.max(canvasWidth, canvasHeight) / SAMPLES_ALONG_LONG_EDGE);
    left = Math.max(-1, (int) Math.floor(field.left() / step));
    top = Math.max(-1, (int) Math.floor(field.top() / step));
    int right = Math.min((int) Math.ceil(canvasWidth / step) + 1, (int) Math.ceil(field.right() / step));
    int bottom = Math.min((int) Math.ceil(canvasHeight / step) + 1, (int) Math.ceil(field.bottom() / step));
    width = Math.max(0, right - left + 1);
    height = Math.max(0, bottom - top + 1);
    values = new float[width * height];
    for (int row = 0; row < height; row++) {
      double y = (top + row) * step;
      for (int col = 0; col < width; col++) {
        double x = (left + col) * step;
        double weight = field.weight(x, y);
        if (weight > 0) {
          values[row * width + col] = (float) (weight * field.transmission(shadows, x, y, quality));
        }
      }
    }
  }

  double weight(double x, double y) {
    double col = x / step - left;
    double row = y / step - top;
    if (col < 0 || row < 0 || col >= width - 1 || row >= height - 1) return 0;
    int ix = (int) col;
    int iy = (int) row;
    int index = iy * width + ix;
    double tx = col - ix;
    double ty = row - iy;
    double upper = values[index] + (values[index + 1] - values[index]) * tx;
    double lower = values[index + width] + (values[index + width + 1] - values[index + width]) * tx;
    return upper + (lower - upper) * ty;
  }
}
