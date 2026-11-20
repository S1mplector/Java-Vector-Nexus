package com.jvn.editor.ui;

import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.Sprite2D;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.fx.vn.VnStageLightingSupport;

/** A graded background stays editable as a normal timeline sprite. */
public final class PuppeteerStagePlate extends Sprite2D {
  private final VnStagePreset stage;

  public PuppeteerStagePlate(String path, double width, double height, VnStagePreset stage) {
    super(path, width, height);
    this.stage = stage;
  }

  @Override
  protected void drawLayer(Blitter2D blitter, String path, double dx, double dy) {
    String lower = path.toLowerCase(java.util.Locale.ROOT);
    if (blitter instanceof FxBlitter2D fx && !lower.endsWith(".mp4") && !lower.endsWith(".mov")) {
      double width = getWidth(), height = getHeight();
      String variant = stage.getCacheToken() + "|" + Math.round(width) + "x" + Math.round(height);
      fx.drawImageVariant(path, variant, source -> VnStageLightingSupport.renderBackgroundPreview(source, stage, width, height),
          dx, dy, width, height);
    } else {
      super.drawLayer(blitter, path, dx, dy);
    }
  }
}
