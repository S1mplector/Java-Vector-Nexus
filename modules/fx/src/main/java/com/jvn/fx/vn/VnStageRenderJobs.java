package com.jvn.fx.vn;

import com.jvn.core.assets.BoundedImageCache;
import com.jvn.fx.vn.VnStageLightingSupport.Raster;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javafx.scene.image.Image;
import org.slf4j.LoggerFactory;

/** Holds the last completed lighting for a draw slot while its current pose is rendered. */
final class VnStageRenderJobs implements AutoCloseable {
  private static final int MAX_PENDING = 4;

  private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 2, TimeUnit.SECONDS,
      new ArrayBlockingQueue<>(MAX_PENDING), task -> {
        Thread thread = new Thread(task, "jvn-stage-lighting");
        thread.setDaemon(true);
        return thread;
      });
  private final Map<String, Job> pending = new LinkedHashMap<>();
  private long frame;
  private final LinkedHashMap<String, Completed> completed = new LinkedHashMap<>(16, 0.75f, true);

  VnStageRenderJobs() { workers.allowCoreThreadTimeOut(true); }

  void collect() {
    frame++;
    Iterator<Map.Entry<String, Job>> jobs = pending.entrySet().iterator();
    while (jobs.hasNext()) {
      var entry = jobs.next();
      Job job = entry.getValue();
      if (!job.result().isDone()) continue;
      jobs.remove();
      try {
        Raster raster = job.result().get();
        job.cache().put(job.key(), raster == null ? job.source() : raster.image());
        completed.put(entry.getKey(), new Completed(job.key(), frame));
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      } catch (ExecutionException ex) {
        job.cache().put(job.key(), job.source());
        LoggerFactory.getLogger(VnStageRenderJobs.class).warn("Stage lighting render failed", ex.getCause());
      }
    }
    while (completed.size() > 64) completed.pollFirstEntry();
  }

  boolean canSubmit(String slot) {
    Completed last = completed.get(slot);
    return !workers.isShutdown() && pending.size() < MAX_PENDING && !pending.containsKey(slot)
        && (last == null || frame - last.frame() >= 2);
  }

  void submit(String slot, String key, Image source, BoundedImageCache<Image> cache, Supplier<Raster> shade) {
    pending.put(slot, new Job(key, source, cache, workers.submit(shade::get)));
  }

  Image previous(String slot, BoundedImageCache<Image> cache) {
    Completed last = completed.get(slot);
    return last == null ? null : cache.get(last.key());
  }

  void clear() {
    pending.values().forEach(job -> job.result().cancel(true));
    pending.clear();
    completed.clear();
    workers.purge();
  }

  @Override public void close() {
    clear();
    workers.shutdownNow();
  }

  private record Completed(String key, long frame) {}

  private record Job(String key, Image source, BoundedImageCache<Image> cache, Future<Raster> result) {}
}
