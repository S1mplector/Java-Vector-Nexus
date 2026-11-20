package com.jvn.core.scene2d;

/**
 * A textured 2D entity that draws an image (or image region) on screen.
 *
 * <p>{@code Sprite2D} is the most commonly used {@link Entity2D} subclass.
 * It supports three image-drawing modes:</p>
 * <ol>
 *   <li><b>Full image</b> — draws the entire image asset at the sprite's size.</li>
 *   <li><b>Region / atlas slice</b> — draws a rectangular sub-region of the image,
 *       useful for sprite-sheet animations and texture atlases (see {@link #region}).</li>
 *   <li><b>Layer compositing</b> — if the {@link #imagePath} contains pipe ({@code |})
 *       separators, each segment is treated as a separate image path and all layers
 *       are drawn on top of each other at the same transform. This is used for
 *       character expression compositing (e.g. {@code "base.png | eyes_happy.png"}).</li>
 * </ol>
 *
 * <h2>Origin &amp; Pivot</h2>
 * <p>The inherited {@link Entity2D#originX} / {@link Entity2D#originY} values are
 * interpreted as <b>normalised fractions</b> of the sprite's width and height.
 * For example, {@code (0.5, 1.0)} places the pivot at the bottom-centre.</p>
 *
 * @see SpriteAnimation2D
 * @see SpriteSheet
 * @see SpriteAtlas
 */
public class Sprite2D extends Entity2D {

  /** Asset path for the image (or pipe-separated composite layers). */
  private String imagePath;

  /** Display width in logical pixels. */
  private double width;

  /** Display height in logical pixels. */
  private double height;

  /** Opacity multiplier for this sprite [0.0, 1.0]. */
  private double alpha = 1.0;

  /** Whether to draw a sub-region of the image instead of the full image. */
  private boolean useRegion = false;

  /** Source-region coordinates within the texture (used when {@link #useRegion} is true). */
  private double sx, sy, sw, sh;

  /**
   * Construct a sprite that draws the full image at the given display size.
   *
   * @param imagePath asset path (classpath or filesystem)
   * @param width     display width in logical pixels
   * @param height    display height in logical pixels
   */
  public Sprite2D(String imagePath, double width, double height) {
    this.imagePath = imagePath;
    this.width = width;
    this.height = height;
  }

  /**
   * Configure this sprite to draw a rectangular sub-region of an image.
   * Used for sprite-sheet / texture-atlas slicing.
   *
   * @param imagePath asset path
   * @param sx source X within the texture
   * @param sy source Y within the texture
   * @param sw source width
   * @param sh source height
   * @param dw display width
   * @param dh display height
   * @return this sprite for chaining
   */
  public Sprite2D region(String imagePath, double sx, double sy, double sw, double sh, double dw, double dh) {
    this.imagePath = imagePath;
    this.useRegion = true;
    this.sx = sx; this.sy = sy; this.sw = sw; this.sh = sh;
    this.width = dw; this.height = dh;
    return this;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Accessors
  // ──────────────────────────────────────────────────────────────────────────

  /** @param a opacity [0.0 = invisible, 1.0 = fully opaque] */
  public void setAlpha(double a) { this.alpha = a; }

  /** @return current opacity */
  public double getAlpha() { return alpha; }

  /** @return current image asset path */
  public String getImagePath() { return imagePath; }

  /** @param path new image asset path */
  public void setImagePath(String path) { this.imagePath = path; }

  /** @return display width in logical pixels */
  public double getWidth() { return width; }

  /** @return display height in logical pixels */
  public double getHeight() { return height; }

  /**
   * Resize the sprite's display dimensions.
   *
   * @param w new width
   * @param h new height
   */
  public void setSize(double w, double h) { this.width = w; this.height = h; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Rendering
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Draw the sprite image (or region / composite layers) using the blitter.
   *
   * <p>The draw position is offset by {@code (-originX * width, -originY * height)}
   * so that the sprite's origin acts as the pivot for the inherited transform.</p>
   */
  @Override
  public void render(Blitter2D b) {
    if (imagePath == null) return;
    b.push();
    if (alpha != 1.0) b.setGlobalAlpha(alpha);
    double dx = -originX * width;
    double dy = -originY * height;
    if (useRegion) {
      b.drawImageRegion(imagePath, sx, sy, sw, sh, dx, dy, width, height);
    } else if (imagePath.indexOf('|') >= 0) {
      // Layer syntax: pathA | pathB | pathC (all rendered at the same transform).
      for (String layer : imagePath.split("\\|")) {
        String path = layer == null ? "" : layer.trim();
        if (!path.isEmpty()) {
          drawLayer(b, path, dx, dy);
        }
      }
    } else {
      drawLayer(b, imagePath, dx, dy);
    }
    b.pop();
  }

  /** Draws a full-image layer inside the sprite's transform and opacity state. */
  protected void drawLayer(Blitter2D blitter, String path, double dx, double dy) {
    blitter.drawImage(path, dx, dy, width, height);
  }
}
