package com.jvn.fx.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/** Translucent surfaces shared by the tutorial's menus and dialogue. */
public enum AeroGlass {
  TRAY(Color.rgb(235, 251, 255, .68), Color.rgb(165, 214, 241, .46),
      Color.rgb(108, 168, 207, .37), Color.rgb(212, 241, 255, .56), Color.rgb(74, 113, 148, .62), 1),
  PANEL(Color.rgb(251, 254, 255, .78), Color.rgb(225, 244, 255, .68),
      Color.rgb(196, 225, 247, .61), Color.rgb(239, 250, 255, .78), Color.rgb(102, 145, 180, .55), 1),
  BUTTON(Color.rgb(255, 255, 255, .89), Color.rgb(232, 248, 255, .75),
      Color.rgb(164, 211, 241, .63), Color.rgb(226, 245, 255, .84), Color.rgb(82, 126, 163, .75), 1),
  FOCUSED(Color.rgb(246, 253, 255, .94), Color.rgb(191, 236, 255, .83),
      Color.rgb(99, 192, 243, .78), Color.rgb(209, 245, 255, .92), Color.rgb(26, 119, 187, .96), 2),
  DIALOGUE(Color.rgb(59, 93, 128, .88), Color.rgb(32, 62, 95, .86),
      Color.rgb(15, 41, 69, .88), Color.rgb(24, 53, 84, .85), Color.rgb(51, 103, 148, .92), 1);

  private final LinearGradient fill;
  private final Color rim;
  private final double rimWidth;

  AeroGlass(Color top, Color highlight, Color lower, Color bottom, Color rim, double rimWidth) {
    fill = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, top), new Stop(.46, highlight), new Stop(.50, lower), new Stop(1, bottom));
    this.rim = rim;
    this.rimWidth = rimWidth;
  }

  public void draw(GraphicsContext gc, double x, double y, double w, double h, double arc) {
    if (w < 4 || h < 4) return;
    gc.save();
    gc.setFill(fill);
    gc.fillRoundRect(x, y, w, h, arc, arc);
    gc.setStroke(rim);
    gc.setLineWidth(rimWidth);
    gc.strokeRoundRect(x, y, w, h, arc, arc);
    gc.setStroke(Color.rgb(255, 255, 255, .67));
    gc.setLineWidth(1);
    gc.strokeRoundRect(x + 1.5, y + 1.5, w - 3, h - 3, Math.max(0, arc - 2), Math.max(0, arc - 2));
    gc.restore();
  }
}
