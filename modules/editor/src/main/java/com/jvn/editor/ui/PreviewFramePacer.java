package com.jvn.editor.ui;

import com.jvn.core.diagnostics.GraphicsPipeline;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;

/** Stable frame pacing for editor previews, especially JavaFX's CPU renderer. */
public final class PreviewFramePacer {
  private static final int HARDWARE_DEFAULT_FPS = 60;
  private static final int SOFTWARE_DEFAULT_FPS = 30;
  private static final long PULSE_JITTER_TOLERANCE_NS = 500_000L;
  private final long minimumFrameIntervalNs;
  private long lastRenderNs = -1L;
  private long remainderNs;

  public record Frame(boolean render, long deltaMs) {
    private static final Frame SKIP = new Frame(false, 0L);
  }

  public PreviewFramePacer(int targetFps) {
    int boundedFps = Math.max(1, Math.min(240, targetFps));
    minimumFrameIntervalNs = 1_000_000_000L / boundedFps;
  }

  public static PreviewFramePacer forCurrentPipeline() {
    return new PreviewFramePacer(targetFpsForCurrentPipeline());
  }

  static int targetFpsForCurrentPipeline() {
    int override = Integer.getInteger("jvn.editor.previewMaxFps", 0);
    if (override > 0) return Math.min(240, override);
    if (GraphicsPipeline.requestedMode() == GraphicsPipeline.Mode.SOFTWARE) {
      return SOFTWARE_DEFAULT_FPS;
    }
    try {
      if (!Platform.isSupported(ConditionalFeature.SCENE3D)) return SOFTWARE_DEFAULT_FPS;
    } catch (IllegalStateException ignored) {
      // A unit test or early startup call has no JavaFX toolkit yet.
    }
    return HARDWARE_DEFAULT_FPS;
  }

  public Frame next(long nowNs) {
    if (lastRenderNs < 0L) {
      lastRenderNs = nowNs;
      return Frame.SKIP;
    }
    long elapsedNs = nowNs - lastRenderNs;
    if (elapsedNs + PULSE_JITTER_TOLERANCE_NS < minimumFrameIntervalNs) return Frame.SKIP;
    lastRenderNs = nowNs;
    long accumulatedNs = Math.min(100_000_000L, elapsedNs) + remainderNs;
    long deltaMs = Math.max(1L, accumulatedNs / 1_000_000L);
    remainderNs = accumulatedNs % 1_000_000L;
    return new Frame(true, deltaMs);
  }
}
