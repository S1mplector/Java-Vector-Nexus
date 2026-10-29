package com.jvn.fx.image;

import com.jvn.core.assets.ImageEffects;
import com.jvn.plugin.api.image.*;
import javafx.scene.image.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;

/** FX-thread-owned, bounded prepared-frame cache. Pixel transforms run on a daemon worker. */
public final class FxImageEffectCache implements AutoCloseable {
  private static final long BUDGET = 96L * 1024 * 1024;
  private record Key(Image source, ImageEffect effect, Map<String, Double> parameters) {
    @Override public boolean equals(Object other) {
      return other instanceof Key key && source == key.source && effect == key.effect && parameters.equals(key.parameters);
    }
    @Override public int hashCode() { return 31 * (31 * System.identityHashCode(source) + System.identityHashCode(effect)) + parameters.hashCode(); }
  }
  private static final class Entry {
    Future<ImageAnimation> pending;
    List<Image> frames;
    double fps;
    long bytes;
    String failure;
  }
  private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
  private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<>(4), task -> {
    Thread thread = new Thread(task, "jvn-image-effect"); thread.setDaemon(true); return thread;
  });
  private ImageEffect lastEffect;
  private Map<String, Double> lastSupplied, lastResolved;
  private long bytes;
  private boolean closed;
  private String status = "Choose an image";
  public String status() { return status; }

  public Image frame(Image source, String effectId, Map<String, Double> supplied, long timeMs) {
    if (closed || source == null || effectId == null || effectId.isBlank()) return source;
    ImageEffect effect = ImageEffects.registry() == null ? null : ImageEffects.registry().find(effectId).orElse(null);
    if (effect == null) { status = "Effect unavailable: " + effectId; return source; }
    Map<String, Double> parameters;
    try {
      if (effect == lastEffect && supplied.equals(lastSupplied)) parameters = lastResolved;
      else {
        parameters = effect.resolve(supplied);
        lastEffect = effect; lastSupplied = Map.copyOf(supplied); lastResolved = parameters;
      }
    }
    catch (RuntimeException error) { status = "Invalid settings: " + error.getMessage(); return source; }
    Key key = new Key(source, effect, parameters);
    Entry entry = entries.get(key);
    if (entry == null) {
      if (source.getPixelReader() == null) { status = "Loading image…"; return source; }
      if (entries.values().stream().filter(e -> e.pending != null).count() >= 4) {
        status = "Preparing drawings…"; return source;
      }
      ImagePixels pixels = copyPixels(source);
      entry = new Entry();
      worker.purge();
      entry.pending = worker.submit(() -> {
        ImageAnimation result = effect.prepare(pixels, parameters);
        if (result.frames().getFirst().width() != pixels.width() || result.frames().getFirst().height() != pixels.height())
          throw new IllegalArgumentException("Image effects must preserve dimensions");
        return result;
      });
      entries.put(key, entry);
      trim(key);
    }
    if (entry.pending != null && entry.pending.isDone()) {
      try {
        ImageAnimation animation = entry.pending.get();
        List<Image> frames = new ArrayList<>();
        for (ImagePixels pixels : animation.frames()) {
          WritableImage image = new WritableImage(pixels.width(), pixels.height());
          image.getPixelWriter().setPixels(0, 0, pixels.width(), pixels.height(), PixelFormat.getIntArgbInstance(), pixels.pixels(), 0, pixels.width());
          frames.add(image);
        }
        entry.frames = List.copyOf(frames); entry.fps = animation.framesPerSecond();
        entry.bytes = (long) animation.frames().getFirst().width() * animation.frames().getFirst().height() * 4 * frames.size();
        bytes += entry.bytes;
      } catch (Exception error) {
        if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        Throwable cause = error.getCause() == null ? error : error.getCause();
        entry.failure = "Effect failed: " + cause.getMessage();
        LoggerFactory.getLogger(FxImageEffectCache.class).warn("Image effect {} failed", effectId, cause);
      } finally { entry.pending = null; }
      trim(key);
    }
    if (entry.frames == null) {
      status = entry.failure == null ? "Preparing drawings…" : entry.failure;
      return source;
    }
    status = entry.frames.size() + " drawings · " + (int) entry.fps + " fps";
    int frame = (int) ((Math.max(0L, timeMs) / 1000.0 * entry.fps) % entry.frames.size());
    return entry.frames.get(frame);
  }
  private void trim(Key keep) {
    var it = entries.entrySet().iterator();
    while ((bytes > BUDGET || entries.size() > 24) && it.hasNext()) {
      var oldest = it.next();
      if (oldest.getKey().equals(keep)) continue;
      Entry value = oldest.getValue();
      if (value.pending != null) value.pending.cancel(true);
      bytes -= value.bytes; it.remove();
    }
  }
  /** Bound preparation work, retaining aspect ratio and source alpha. */
  public static ImagePixels copyPixels(Image source) {
    int sw = (int) source.getWidth(), sh = (int) source.getHeight();
    double scale = Math.min(1, 1080.0 / Math.max(sw, sh));
    int w = Math.max(1, (int) Math.round(sw * scale)), h = Math.max(1, (int) Math.round(sh * scale));
    int[] pixels = new int[w * h]; PixelReader reader = source.getPixelReader();
    if (w == sw && h == sh) {
      reader.getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
    } else {
      for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
        pixels[y * w + x] = reader.getArgb(Math.min(sw - 1, (int) (x / scale)), Math.min(sh - 1, (int) (y / scale)));
    }
    return new ImagePixels(w, h, pixels);
  }
  public void clear() {
    entries.values().forEach(entry -> { if (entry.pending != null) entry.pending.cancel(true); });
    entries.clear(); bytes = 0; worker.purge(); lastEffect = null; lastSupplied = null; lastResolved = null;
  }
  @Override public void close() { closed = true; clear(); worker.shutdownNow(); }
}
