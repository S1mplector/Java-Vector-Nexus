package com.jvn.editor.ui;

import javafx.scene.control.PopupControl;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.TooltipSkin;

/** Keeps ordinary hover text readable while honoring explicitly sized tooltips. */
public final class EditorTooltipSkin extends TooltipSkin {
  public EditorTooltipSkin(Tooltip tooltip) {
    super(tooltip);
    if (!tooltip.maxWidthProperty().isBound() && tooltip.getMaxWidth() == PopupControl.USE_COMPUTED_SIZE) {
      tooltip.setMaxWidth(360);
    }
  }
}
