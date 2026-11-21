package com.jvn.fx.ui;

/** Receives painted element bounds and their authoring coordinate space in game pixels. */
@FunctionalInterface
public interface RenderedBoundsListener {
  void accept(String id, double x, double y, double width, double height,
              double originX, double originY, double spaceWidth, double spaceHeight);
}
