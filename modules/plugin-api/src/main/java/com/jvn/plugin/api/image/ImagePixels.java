package com.jvn.plugin.api.image;

/**
 * Immutable, straight-alpha ARGB pixels, independent of any rendering toolkit.
 * @param width image width in pixels
 * @param height image height in pixels
 * @param pixels row-major ARGB values, defensively copied
 */
public record ImagePixels(int width, int height, int[] pixels) {
  /** Maximum pixel count accepted by a single image. */
  public static final int MAX_PIXELS = 4_194_304;
  /** Validates dimensions and copies the caller-owned buffer. */
  public ImagePixels {
    if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS
        || pixels == null || pixels.length != (long) width * height) {
      throw new IllegalArgumentException("Invalid image dimensions or pixel count");
    }
    pixels = pixels.clone();
  }
  /** Describes this value.
   * @return a defensive copy of the pixel buffer */
  @Override public int[] pixels() { return pixels.clone(); }
}
