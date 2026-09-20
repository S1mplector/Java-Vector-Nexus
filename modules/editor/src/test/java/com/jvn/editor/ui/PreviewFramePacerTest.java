package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.diagnostics.GraphicsPipeline;
import org.junit.jupiter.api.Test;

class PreviewFramePacerTest {
  @Test
  void capsRenderingAndCarriesElapsedTimeIntoTheRenderedFrame() {
    PreviewFramePacer pacer = new PreviewFramePacer(30);

    assertFalse(pacer.next(1_000_000_000L).render());
    assertFalse(pacer.next(1_020_000_000L).render());
    PreviewFramePacer.Frame frame = pacer.next(1_034_000_000L);

    assertTrue(frame.render());
    assertEquals(34L, frame.deltaMs());
  }

  @Test
  void clampsLongPausesSoPreviewAnimationsResumeSmoothly() {
    PreviewFramePacer pacer = new PreviewFramePacer(60);
    pacer.next(1_000_000_000L);

    PreviewFramePacer.Frame frame = pacer.next(2_000_000_000L);

    assertTrue(frame.render());
    assertEquals(100L, frame.deltaMs());
  }

  @Test
  void softwareRenderingUsesTheConservativeDefaultCap() {
    String previousMode = System.getProperty(GraphicsPipeline.MODE_PROPERTY);
    String previousOverride = System.getProperty("jvn.editor.previewMaxFps");
    try {
      System.setProperty(GraphicsPipeline.MODE_PROPERTY, "software");
      System.clearProperty("jvn.editor.previewMaxFps");

      assertEquals(30, PreviewFramePacer.targetFpsForCurrentPipeline());
    } finally {
      restore(GraphicsPipeline.MODE_PROPERTY, previousMode);
      restore("jvn.editor.previewMaxFps", previousOverride);
    }
  }

  @Test
  void efficientBudgetHalvesDrawWorkWithoutSlowingAnimationTime() {
    PreviewFramePacer balanced = new PreviewFramePacer(60);
    PreviewFramePacer efficient = new PreviewFramePacer(30);
    int balancedFrames = 0;
    int efficientFrames = 0;
    long balancedTime = 0;
    long efficientTime = 0;
    for (int pulse = 0; pulse <= 600; pulse++) {
      long now = 1_000_000_000L + pulse * 16_666_667L;
      var normal = balanced.next(now);
      var reduced = efficient.next(now);
      if (normal.render()) { balancedFrames++; balancedTime += normal.deltaMs(); }
      if (reduced.render()) { efficientFrames++; efficientTime += reduced.deltaMs(); }
    }
    assertEquals(600, balancedFrames);
    assertEquals(300, efficientFrames);
    assertEquals(10_000L, balancedTime);
    assertEquals(10_000L, efficientTime, "Skipped pulses must preserve elapsed animation time");
    System.out.println("PREVIEW WORK: 600 pulses -> " + balancedFrames + " balanced draws / " + efficientFrames + " efficient draws");
  }

  private static void restore(String key, String value) {
    if (value == null) System.clearProperty(key);
    else System.setProperty(key, value);
  }
}
