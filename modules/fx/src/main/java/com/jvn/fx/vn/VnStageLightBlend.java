package com.jvn.fx.vn;

import com.jvn.core.vn.stage.VnStagePreset.LightBlend;
import javafx.scene.effect.BlendMode;

/** Canvas equivalent of the stage light's raster blend mode. */
public final class VnStageLightBlend {
  private VnStageLightBlend() {}

  public static BlendMode canvasMode(LightBlend blend) {
    return switch (blend) {
      case PHYSICAL, NORMAL -> BlendMode.SRC_OVER;
      case ADD -> BlendMode.ADD;
      case SCREEN -> BlendMode.SCREEN;
      case MULTIPLY -> BlendMode.MULTIPLY;
      case OVERLAY -> BlendMode.OVERLAY;
      case SOFT_LIGHT -> BlendMode.SOFT_LIGHT;
    };
  }
}
