package com.jvn.pikupiku;

import com.jvn.plugin.api.image.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PikupikuEffectTest {
  private final PikupikuEffect effect = new PikupikuEffect();
  private ImagePixels source() {
    int[] pixels = new int[64 * 64];
    for (int y = 8; y < 56; y++) for (int x = 8; x < 56; x++) {
      pixels[y * 64 + x] = (x < 12 ? 0x80000000 : 0xff000000) | (x % 17 < 3 ? 0x202020 : 0xb9a3de);
    }
    return new ImagePixels(64, 64, pixels);
  }
  @Test void drawingsAreDeterministicAndWiggleWithoutMovingAlpha() throws Exception {
    ImagePixels source = source();
    ImageAnimation a = effect.prepare(source, Map.of()), b = effect.prepare(source, Map.of());
    assertEquals(3, a.frames().size()); assertEquals(6, a.framesPerSecond());
    assertFalse(Arrays.equals(a.frames().get(0).pixels(), a.frames().get(1).pixels()));
    int[] original = source.pixels();
    for (int n = 0; n < a.frames().size(); n++) {
      assertArrayEquals(a.frames().get(n).pixels(), b.frames().get(n).pixels());
      int[] frame = a.frames().get(n).pixels();
      for (int i = 0; i < frame.length; i++) assertEquals(original[i] >>> 24, frame[i] >>> 24);
    }
  }
  @Test void offPreservesSourceAndSeedChangesDrawings() throws Exception {
    ImagePixels source = source();
    for (ImagePixels frame : effect.prepare(source, effect.presets().get("Off")).frames()) assertArrayEquals(source.pixels(), frame.pixels());
    assertFalse(Arrays.equals(effect.prepare(source, Map.of("seed", 1.0)).frames().getFirst().pixels(),
        effect.prepare(source, Map.of("seed", 2.0)).frames().getFirst().pixels()));
  }
  @Test void everyPresetIsValidAndPingPongReturnsThroughInteriorDrawings() throws Exception {
    for (var preset : effect.presets().values()) assertNotNull(effect.prepare(source(), preset));
    var animation = effect.prepare(source(), Map.of("frames", 4.0, "pingpong", 1.0));
    assertEquals(6, animation.frames().size());
    assertSame(animation.frames().get(2), animation.frames().get(4));
    assertSame(animation.frames().get(1), animation.frames().get(5));
    assertEquals(0, animation.frameIndex(1000));
    assertEquals(1, animation.frameIndex(200));
  }
  @Test void invalidParametersAndMemoryExhaustionAreRejectedBeforeRendering() {
    assertThrows(IllegalArgumentException.class, () -> effect.resolve(Map.of("fps", Double.NaN)));
    assertThrows(IllegalArgumentException.class, () -> effect.resolve(Map.of("frames", 2.5)));
    assertThrows(IllegalArgumentException.class, () -> effect.resolve(Map.of("typo", 1.0)));
    ImagePixels large = new ImagePixels(2048, 2048, new int[2048 * 2048]);
    assertThrows(IllegalArgumentException.class, () -> effect.prepare(large, Map.of("frames", 12.0)));
  }
  @Test void sourceAndFramesAreImmutable() throws Exception {
    int[] pixels = {0xff123456}; ImagePixels source = new ImagePixels(1, 1, pixels);
    pixels[0] = 0; source.pixels()[0] = 0;
    assertEquals(0xff123456, source.pixels()[0]);
    var animation = effect.prepare(source, Map.of());
    assertThrows(UnsupportedOperationException.class, () -> animation.frames().clear());
    assertThrows(IllegalArgumentException.class, () -> new ImageAnimation(List.of(source, source()), 6));
  }
  @Test void preparationHonorsCancellation() {
    Thread.currentThread().interrupt();
    try { assertThrows(InterruptedException.class, () -> effect.prepare(source(), Map.of())); }
    finally { Thread.interrupted(); }
  }
}
