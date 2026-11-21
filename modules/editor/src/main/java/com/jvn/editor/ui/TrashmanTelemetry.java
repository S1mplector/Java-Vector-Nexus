package com.jvn.editor.ui;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Runtime counters captured together on the Trashman worker. */
final class TrashmanTelemetry {
  private static final Pattern HISTOGRAM_ROW = Pattern.compile("^\\s*\\d+:\\s+(\\d+)\\s+(\\d+)\\s+(\\S+).*");
  private static volatile String cachedJcmdCommand;
  private TrashmanTelemetry() {}

  record ClassEntry(String name, long instances, long bytes) {}

  static List<ClassEntry> largestClasses(String histogram) {
    return histogram.lines().map(HISTOGRAM_ROW::matcher).filter(m -> m.matches()).limit(8)
        .map(m -> new ClassEntry(m.group(3), Long.parseLong(m.group(1)), Long.parseLong(m.group(2)))).toList();
  }

  private static long addCounter(long total, long next) {
    return total < 0 || next < 0 ? -1 : total + next;
  }

  private static long positiveOr(long first, long fallback) {
    return first > 0 ? first : Math.max(0L, fallback);
  }

  private static double ratio(long used, long max) {
    if (used < 0 || max <= 0) return 0.0;
    return Math.min(1.0, used / (double) max);
  }

  static String maxText(long bytes) {
    return bytes < 0 ? "unspecified" : formatBytes(bytes);
  }

  static String percentText(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) return "--";
    return String.format(Locale.ROOT, "%.1f%%", Math.min(1.0, value) * 100.0);
  }

  static String formatBytes(long bytes) {
    if (bytes < 0) return "--";
    double value = bytes;
    String[] units = {"B", "KiB", "MiB", "GiB"};
    int unit = 0;
    while (value >= 1024.0 && unit < units.length - 1) {
      value /= 1024.0;
      unit++;
    }
    return unit == 0
        ? String.format(Locale.ROOT, "%.0f %s", value, units[unit])
        : String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
  }

  record CollectorInfo(String name, long count, long timeMs) {
    String countText() {
      return count < 0 ? "count unavailable" : count + " collections";
    }

    String timeText() {
      return timeMs < 0 ? "time unavailable" : timeMs + " ms";
    }
  }

  record PoolInfo(
      String name,
      String type,
      long used,
      long committed,
      long max,
      long peakUsed,
      boolean usageThresholdSupported,
      long usageThreshold,
      long usageThresholdCount,
      boolean usageThresholdExceeded,
      boolean collectionThresholdSupported,
      long collectionThreshold,
      long collectionThresholdCount,
      boolean collectionThresholdExceeded) {
    String detail() {
      String usage = usageThresholdSupported
          ? "usage threshold " + formatBytes(usageThreshold) + " (" + usageThresholdCount + " trips"
              + (usageThresholdExceeded ? ", exceeded" : "") + ")"
          : "usage threshold unsupported";
      String collection = collectionThresholdSupported
          ? "collection threshold " + formatBytes(collectionThreshold) + " (" + collectionThresholdCount + " trips"
              + (collectionThresholdExceeded ? ", exceeded" : "") + ")"
          : "collection threshold unsupported";
      return formatBytes(used) + " used / " + formatBytes(committed) + " committed / "
          + maxText(max) + " max | peak " + formatBytes(peakUsed) + " | " + usage + " | " + collection;
    }
  }

  record BufferInfo(String name, long count, long used, long capacity) {
  }

  record Snapshot(
      String pid,
      String jvm,
      long uptimeMs,
      long heapUsed,
      long heapCommitted,
      long heapMax,
      long nonHeapUsed,
      long nonHeapCommitted,
      long nonHeapMax,
      long gcCount,
      long gcTimeMs,
      double gcOverhead,
      int pendingFinalization,
      int loadedClasses,
      long totalLoadedClasses,
      long unloadedClasses,
      int threadCount,
      int daemonThreadCount,
      int peakThreadCount,
      long bufferCount,
      long bufferUsed,
      long bufferCapacity,
      long captureDurationMs,
      List<CollectorInfo> collectors,
      List<PoolInfo> pools,
      List<BufferInfo> buffers,
      String jcmdCommand,
      boolean jcmdAvailable,
      String jcmdDetail) {
    long heapFreeFromMax() {
      long denominator = positiveOr(heapMax, heapCommitted);
      return Math.max(0L, denominator - heapUsed);
    }

    double heapPressure() {
      return ratio(heapUsed, positiveOr(heapMax, heapCommitted));
    }

    static Snapshot capture() {
      long captureStartNs = System.nanoTime();
      RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
      MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
      ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
      ThreadMXBean threads = ManagementFactory.getThreadMXBean();
      MemoryUsage heap = memory.getHeapMemoryUsage();
      MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
      long count = 0L;
      long time = 0L;
      List<CollectorInfo> collectors = new ArrayList<>();
      for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
        long c = collector.getCollectionCount();
        long t = collector.getCollectionTime();
        count = addCounter(count, c);
        time = addCounter(time, t);
        collectors.add(new CollectorInfo(collector.getName(), c, t));
      }
      List<PoolInfo> pools = new ArrayList<>();
      for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
        MemoryUsage usage = pool.getUsage();
        if (usage == null) continue;
        MemoryUsage peak = pool.getPeakUsage();
        long max = usage.getMax();
        long used = usage.getUsed();
        boolean usageSupported = pool.isUsageThresholdSupported();
        boolean collectionSupported = pool.isCollectionUsageThresholdSupported();
        pools.add(new PoolInfo(
            pool.getName(),
            pool.getType() == MemoryType.HEAP ? "heap" : "non-heap",
            used,
            usage.getCommitted(),
            max,
            peak == null ? -1L : peak.getUsed(),
            usageSupported,
            thresholdValue(pool, usageSupported, false),
            thresholdCount(pool, usageSupported, false),
            thresholdExceeded(pool, usageSupported, false),
            collectionSupported,
            thresholdValue(pool, collectionSupported, true),
            thresholdCount(pool, collectionSupported, true),
            thresholdExceeded(pool, collectionSupported, true)));
      }
      List<BufferInfo> buffers = new ArrayList<>();
      long bufferCount = 0L;
      long bufferUsed = 0L;
      long bufferCapacity = 0L;
      for (BufferPoolMXBean buffer : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
        long c = buffer.getCount();
        long used = buffer.getMemoryUsed();
        long capacity = buffer.getTotalCapacity();
        bufferCount = addCounter(bufferCount, c);
        bufferUsed = addCounter(bufferUsed, used);
        bufferCapacity = addCounter(bufferCapacity, capacity);
        buffers.add(new BufferInfo(buffer.getName(), c, used, capacity));
      }
      String pid = Long.toString(runtime.getPid());
      String jcmd = resolveJcmdCached();
      boolean jcmdAvailable = jcmd != null && !jcmd.isBlank();
      String jcmdDetail = jcmdAvailable ? jcmd : "jcmd not found in current JDK.";
      long captureDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - captureStartNs);
      return new Snapshot(
          pid,
          System.getProperty("java.vm.name", "unknown") + " " + System.getProperty("java.version", "unknown"),
          runtime.getUptime(),
          heap.getUsed(),
          heap.getCommitted(),
          heap.getMax(),
          nonHeap.getUsed(),
          nonHeap.getCommitted(),
          nonHeap.getMax(),
          count,
          time,
          runtime.getUptime() <= 0 ? 0.0 : Math.min(1.0, time / (double) runtime.getUptime()),
          memory.getObjectPendingFinalizationCount(),
          classes.getLoadedClassCount(),
          classes.getTotalLoadedClassCount(),
          classes.getUnloadedClassCount(),
          threads.getThreadCount(),
          threads.getDaemonThreadCount(),
          threads.getPeakThreadCount(),
          bufferCount,
          bufferUsed,
          bufferCapacity,
          captureDurationMs,
          List.copyOf(collectors),
          List.copyOf(pools),
          List.copyOf(buffers),
          jcmdAvailable ? jcmd : "",
          jcmdAvailable,
          jcmdDetail);
    }

    private static long thresholdValue(MemoryPoolMXBean pool, boolean supported, boolean collectionUsage) {
      if (!supported) return -1L;
      try {
        return collectionUsage ? pool.getCollectionUsageThreshold() : pool.getUsageThreshold();
      } catch (Exception ignored) {
        return -1L;
      }
    }

    private static long thresholdCount(MemoryPoolMXBean pool, boolean supported, boolean collectionUsage) {
      if (!supported) return -1L;
      try {
        return collectionUsage ? pool.getCollectionUsageThresholdCount() : pool.getUsageThresholdCount();
      } catch (Exception ignored) {
        return -1L;
      }
    }

    private static boolean thresholdExceeded(MemoryPoolMXBean pool, boolean supported, boolean collectionUsage) {
      if (!supported) return false;
      try {
        return collectionUsage ? pool.isCollectionUsageThresholdExceeded() : pool.isUsageThresholdExceeded();
      } catch (Exception ignored) {
        return false;
      }
    }

    private static String resolveJcmdCached() {
      String cached = cachedJcmdCommand;
      if (cached != null) return cached;
      synchronized (Snapshot.class) {
        cached = cachedJcmdCommand;
        if (cached != null) return cached;
        String resolved = resolveJcmd();
        cachedJcmdCommand = resolved == null ? "" : resolved;
        return cachedJcmdCommand;
      }
    }

    private static String resolveJcmd() {
      String exe = isWindows() ? "jcmd.exe" : "jcmd";
      String javaHome = System.getProperty("java.home", "");
      if (!javaHome.isBlank()) {
        Path local = Path.of(javaHome, "bin", exe);
        if (Files.isRegularFile(local) && Files.isExecutable(local)) return local.toAbsolutePath().toString();
        Path parent = Path.of(javaHome).getParent();
        if (parent != null) {
          Path sibling = parent.resolve("bin").resolve(exe);
          if (Files.isRegularFile(sibling) && Files.isExecutable(sibling)) return sibling.toAbsolutePath().toString();
        }
      }
      String path = System.getenv("PATH");
      if (path != null && !path.isBlank()) {
        for (String entry : path.split(java.io.File.pathSeparator)) {
          if (entry == null || entry.isBlank()) continue;
          try {
            Path candidate = Path.of(entry, exe);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
              return candidate.toAbsolutePath().toString();
            }
          } catch (Exception ignored) {
          }
        }
      }
      return null;
    }

    private static boolean isWindows() {
      return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
  }
}
