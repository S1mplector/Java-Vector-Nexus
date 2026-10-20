package com.jvn.core.assets;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * Generic LRU cache backed by {@link LinkedHashMap} in access-order mode.
 *
 * <p>Evicts least-recently-used entries when either the entry-count or optional
 * weight budget is exceeded. The weighted form is important for raster caches:
 * limiting a cache to 256 images is not safe when each image can occupy several
 * megabytes. Public operations use coarse synchronization, which is adequate
 * for the low-contention renderer caches and preserves access order.
 *
 * @param <V> value type (intentionally not locked to JavaFX Image so :core stays JavaFX-free)
 */
public class BoundedImageCache<V> {

  private int maxEntries;
  private final long maxWeight;
  private final ToLongFunction<? super V> weightFunction;
  private record Entry<V>(V value, long weight) {}

  private final LinkedHashMap<String, Entry<V>> map;
  private final AtomicLong hits = new AtomicLong();
  private final AtomicLong misses = new AtomicLong();
  private long currentWeight;

  public BoundedImageCache(int maxEntries) {
    this(maxEntries, Long.MAX_VALUE, ignored -> 1L);
  }

  /**
   * Creates an LRU cache constrained by both entry count and total value weight.
   * Weights are measured once on insertion; reinsert a mutated value to update its weight.
   * Values whose individual weight exceeds the budget are returned to callers
   * but not retained by the cache.
   */
  public BoundedImageCache(
      int maxEntries,
      long maxWeight,
      ToLongFunction<? super V> weightFunction
  ) {
    if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be >= 1");
    if (maxWeight < 1L) throw new IllegalArgumentException("maxWeight must be >= 1");
    if (weightFunction == null) throw new IllegalArgumentException("weightFunction cannot be null");
    this.maxEntries = maxEntries;
    this.maxWeight = maxWeight;
    this.weightFunction = weightFunction;
    this.map = new LinkedHashMap<>(16, 0.75f, true);
  }

  /** Returns the cached value, or {@code null} if absent. */
  public synchronized V get(String key) {
    Entry<V> entry = map.get(key);
    V v = entry == null ? null : entry.value();
    if (v != null) hits.incrementAndGet(); else misses.incrementAndGet();
    return v;
  }

  /**
   * Returns the cached value if present, otherwise calls {@code loader}, stores
   * the result, and returns it. The loader is called at most once per missing key
   * per call.
   */
  public synchronized V computeIfAbsent(String key, Function<String, V> loader) {
    Entry<V> entry = map.get(key);
    V existing = entry == null ? null : entry.value();
    if (existing != null) {
      hits.incrementAndGet();
      return existing;
    }
    misses.incrementAndGet();
    V computed = loader.apply(key);
    if (computed != null) {
      putInternal(key, computed);
    }
    return computed;
  }

  /** Explicitly stores a value, subject to LRU eviction like any other entry. */
  public synchronized void put(String key, V value) {
    if (value != null) putInternal(key, value);
  }

  /** Removes and returns one entry, or {@code null} when the key is absent. */
  public synchronized V remove(String key) {
    Entry<V> removed = map.remove(key);
    if (removed != null) currentWeight -= removed.weight();
    return removed == null ? null : removed.value();
  }

  /** Removes every entry whose key matches the predicate. */
  public synchronized void removeKeysIf(Predicate<String> predicate) {
    if (predicate == null) return;
    Iterator<Map.Entry<String, Entry<V>>> iterator = map.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, Entry<V>> entry = iterator.next();
      if (predicate.test(entry.getKey())) {
        currentWeight = currentWeight - entry.getValue().weight();
        iterator.remove();
      }
    }
  }

  /** Removes all entries from the cache. */
  public synchronized void clear() {
    map.clear();
    currentWeight = 0L;
  }

  /** Returns the current number of entries. */
  public synchronized int size() {
    return map.size();
  }

  /** Returns the configured maximum number of entries. */
  public synchronized int maxEntries() {
    return maxEntries;
  }

  /** Changes the entry ceiling and immediately evicts LRU entries if necessary. */
  public synchronized void setMaxEntries(int maxEntries) {
    if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be >= 1");
    this.maxEntries = maxEntries;
    evictToBudget();
  }

  /** Returns the configured total value-weight budget. */
  public long maxWeight() { return maxWeight; }

  /** Returns the total estimated weight currently retained. */
  public synchronized long currentWeight() { return currentWeight; }

  /** Returns the cumulative hit count since construction or last reset. */
  public long hitCount() {
    return hits.get();
  }

  /** Returns the cumulative miss count since construction or last reset. */
  public long missCount() {
    return misses.get();
  }

  private void putInternal(String key, V value) {
    long weight = weightOf(value);
    Entry<V> previous = map.remove(key);
    if (previous != null) currentWeight -= previous.weight();
    if (weight > maxWeight) return;

    // Make room before addition so even a Long.MAX_VALUE budget cannot overflow.
    Iterator<Map.Entry<String, Entry<V>>> iterator = map.entrySet().iterator();
    while (currentWeight > maxWeight - weight && iterator.hasNext()) {
      currentWeight -= iterator.next().getValue().weight();
      iterator.remove();
    }
    map.put(key, new Entry<>(value, weight));
    currentWeight += weight;
    evictToBudget();
  }

  private void evictToBudget() {
    Iterator<Map.Entry<String, Entry<V>>> iterator = map.entrySet().iterator();
    while ((map.size() > maxEntries || currentWeight > maxWeight) && iterator.hasNext()) {
      Map.Entry<String, Entry<V>> eldest = iterator.next();
      currentWeight = currentWeight - eldest.getValue().weight();
      iterator.remove();
    }
  }

  private long weightOf(V value) {
    if (value == null) return 0L;
    try {
      return Math.max(1L, weightFunction.applyAsLong(value));
    } catch (RuntimeException ignored) {
      return 1L;
    }
  }

}
