package com.jvn.plugin.api.image;

import java.util.List;

/**
 * Prepared held drawings. Hosts cache these; effects never run on every paint.
 * @param frames immutable frame sequence, all with equal dimensions
 * @param framesPerSecond held-frame playback rate, from 1 through 60
 */
public record ImageAnimation(List<ImagePixels> frames, double framesPerSecond) {
  /** Maximum total decoded frame storage for one animation. */
  public static final long MAX_BYTES = 64L * 1024 * 1024;
  /** Validates dimensions, frame count, playback rate, and memory budget. */
  public ImageAnimation {
    frames = List.copyOf(frames);
    if (frames.isEmpty() || frames.size() > 120 || !Double.isFinite(framesPerSecond)
        || framesPerSecond < 1 || framesPerSecond > 60) throw new IllegalArgumentException("Invalid animation timing");
    ImagePixels first = frames.getFirst();
    long bytes = (long) first.width() * first.height() * Integer.BYTES * frames.size();
    if (bytes > MAX_BYTES) throw new IllegalArgumentException("Image animation exceeds 64 MiB");
    for (ImagePixels frame : frames) {
      if (frame.width() != first.width() || frame.height() != first.height())
        throw new IllegalArgumentException("Animation frame dimensions must agree");
    }
  }
  /**
   * Selects a frame, looping the animation.
   * @param elapsedMillis elapsed playback time; negative values select frame zero
   * @return zero-based index into {@link #frames()}
   */
  public int frameIndex(long elapsedMillis) {
    return (int) ((Math.max(0L, elapsedMillis) / 1000.0 * framesPerSecond) % frames.size());
  }
}
