package com.jvn.core.assets;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BoundedImageCacheTest {

  @Test
  void lruEviction_oldestEntryRemovedWhenFull() {
    BoundedImageCache<String> cache = new BoundedImageCache<>(256);
    // Insert 257 items; key "key-0" should be evicted (oldest, never re-accessed)
    for (int i = 0; i < 257; i++) {
      cache.computeIfAbsent("key-" + i, k -> "val-" + k);
    }
    assertNull(cache.get("key-0"), "oldest entry should have been evicted");
    assertNotNull(cache.get("key-256"), "newest entry should survive");
    assertTrue(cache.size() <= 256, "size must not exceed maxEntries");
  }

  @Test
  void lruEviction_accessedEntryNotEvicted() {
    BoundedImageCache<String> cache = new BoundedImageCache<>(256);
    cache.computeIfAbsent("key-0", k -> "val-0");
    // Fill up to 256
    for (int i = 1; i < 256; i++) {
      cache.computeIfAbsent("key-" + i, k -> "val-" + k);
    }
    // Re-access key-0 to make it recently used
    cache.get("key-0");
    // Add one more to trigger eviction
    cache.computeIfAbsent("key-256", k -> "val-256");
    assertNotNull(cache.get("key-0"), "recently accessed entry should survive eviction");
  }

  @Test
  void hitMissCounters_incrementCorrectly() {
    BoundedImageCache<String> cache = new BoundedImageCache<>(10);
    cache.computeIfAbsent("a", k -> "A"); // miss
    cache.computeIfAbsent("a", k -> "A"); // hit
    cache.get("a");                        // hit
    cache.get("missing");                  // miss

    assertEquals(2, cache.hitCount());
    assertEquals(2, cache.missCount());
  }

  @Test
  void concurrentComputeIfAbsent_loaderCalledOnce() throws InterruptedException {
    BoundedImageCache<String> cache = new BoundedImageCache<>(256);
    AtomicInteger loaderCallCount = new AtomicInteger();
    int threads = 4;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    for (int i = 0; i < threads; i++) {
      pool.submit(() -> {
        try {
          start.await();
          cache.computeIfAbsent("shared-key", k -> {
            loaderCallCount.incrementAndGet();
            return "value";
          });
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          done.countDown();
        }
      });
    }
    start.countDown();
    assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS));
    pool.shutdown();

    assertEquals(1, loaderCallCount.get());
    assertNotNull(cache.get("shared-key"));
    assertTrue(cache.size() <= 256);
  }

  @Test
  void weightedEvictionCapsRetainedRasterBytesDuringAnimationStress() {
    long frameBytes = 512L * 512L * 4L;
    long budgetBytes = 16L * 1024L * 1024L;
    BoundedImageCache<SimulatedRaster> cache =
        new BoundedImageCache<>(256, budgetBytes, SimulatedRaster::bytes);

    for (int frame = 0; frame < 10_000; frame++) {
      cache.put("character-at-x-" + frame, new SimulatedRaster(frameBytes));
      assertTrue(cache.currentWeight() <= budgetBytes);
    }

    assertEquals(16, cache.size());
    assertEquals(budgetBytes, cache.currentWeight());
    assertNull(cache.get("character-at-x-0"));
    assertNotNull(cache.get("character-at-x-9999"));
  }

  @Test
  void replacingAndClearingEntriesUpdatesRetainedWeight() {
    BoundedImageCache<SimulatedRaster> cache =
        new BoundedImageCache<>(8, 1_000L, SimulatedRaster::bytes);

    cache.put("frame", new SimulatedRaster(700L));
    cache.put("frame", new SimulatedRaster(200L));
    assertEquals(200L, cache.currentWeight());

    cache.clear();
    assertEquals(0, cache.size());
    assertEquals(0L, cache.currentWeight());
  }

  @Test
  void rasterLargerThanBudgetIsNotRetained() {
    BoundedImageCache<SimulatedRaster> cache =
        new BoundedImageCache<>(8, 1_000L, SimulatedRaster::bytes);

    SimulatedRaster raster = cache.computeIfAbsent("oversized", key -> new SimulatedRaster(1_001L));

    assertNotNull(raster);
    assertEquals(0, cache.size());
    assertEquals(0L, cache.currentWeight());
  }

  @Test
  void targetedRemovalUpdatesRetainedWeight() {
    BoundedImageCache<SimulatedRaster> cache =
        new BoundedImageCache<>(8, 1_000L, SimulatedRaster::bytes);
    cache.put("sprite.png", new SimulatedRaster(100L));
    cache.put("sprite.png::tint-1", new SimulatedRaster(200L));
    cache.put("sprite.png::tint-2", new SimulatedRaster(300L));
    cache.put("other.png::tint-1", new SimulatedRaster(400L));

    cache.remove("sprite.png");
    cache.removeKeysIf(key -> key.startsWith("sprite.png::"));

    assertEquals(1, cache.size());
    assertEquals(400L, cache.currentWeight());
  }

  @Test
  void reducingEntryLimitEvictsLeastRecentlyUsedImmediately() {
    BoundedImageCache<String> cache = new BoundedImageCache<>(4);
    cache.put("a", "A");
    cache.put("b", "B");
    cache.put("c", "C");
    cache.get("a");

    cache.setMaxEntries(2);

    assertEquals(2, cache.size());
    assertNull(cache.get("b"));
    assertNotNull(cache.get("a"));
  }

  @Test
  void weightsAreStableUntilMutableValuesAreReinserted() {
    BoundedImageCache<long[]> cache = new BoundedImageCache<>(8, 100, value -> value[0]);
    long[] mutable = {70};
    cache.put("mutable", mutable);
    cache.put("other", new long[] {20});
    mutable[0] = 1;
    cache.remove("mutable");
    assertEquals(20, cache.currentWeight());
    cache.put("new", new long[] {80});
    assertEquals(2, cache.size());
    assertEquals(100, cache.currentWeight());
  }

  @Test
  void maximumLongBudgetDoesNotOverflowOrUnderCount() {
    BoundedImageCache<Long> cache = new BoundedImageCache<>(8, Long.MAX_VALUE, value -> value);
    cache.put("a", Long.MAX_VALUE - 10);
    cache.put("b", 20L);
    assertNull(cache.get("a"));
    assertEquals(20L, cache.currentWeight());
    cache.remove("b");
    assertEquals(0, cache.currentWeight());
  }

  private record SimulatedRaster(long bytes) {}
}
