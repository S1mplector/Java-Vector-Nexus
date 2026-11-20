package com.jvn.core.diagnostics;

import com.jvn.core.assets.BoundedImageCache;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RunTelemetryTest {
  @Test void retainsWireBoundariesForAssetNamesAndEmptyCaches() {
    var cache = new RunTelemetry.Cache("Lighting", 1, 80, 3, 2, "assets/é\t☀.png", "");
    var sample = new RunTelemetry("VnScene", 59.5, 2.1, 42, 960, 540, 2, 1, 0, -1, List.of(cache));
    assertEquals(sample, RunTelemetry.decode(sample.encode()));
  }

  @Test void rejectsPartialOrUnrelatedOutput() {
    assertThrows(IllegalArgumentException.class, () -> RunTelemetry.decode("normal output"));
    assertThrows(IllegalArgumentException.class, () -> RunTelemetry.decode(RunTelemetry.PREFIX + "bad"));
  }

  @Test void inspectingCachePreservesCountsAndEvictionOrder() {
    var cache = new BoundedImageCache<String>(2);
    cache.put("a", "first");
    cache.put("b", "second");
    cache.get("a");
    cache.get("missing");
    var sample = cache.inspect("sprites");
    assertEquals("a", sample.recentResident());
    assertEquals("missing", sample.lastMiss());
    assertEquals(1, sample.hits());
    assertEquals(1, sample.misses());
    assertEquals(sample, cache.inspect("sprites"));
    cache.put("c", "third");
    assertNull(cache.get("b"));
    cache.clear();
    assertEquals("", cache.inspect("sprites").recentResident());
  }
}
