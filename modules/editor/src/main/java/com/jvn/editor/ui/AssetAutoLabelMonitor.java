package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Project-scoped, read-only discovery. File metadata polling also works on network/unsupported watch roots. */
public final class AssetAutoLabelMonitor implements AutoCloseable {
  private final AssetAutoLabelService service = new AssetAutoLabelService();
  private final Consumer<AssetAutoLabelService.ScanResult> onScan;
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
    Thread thread = new Thread(task, "jvn-new-asset-monitor");
    thread.setDaemon(true);
    return thread;
  });
  private volatile Path root;
  private final java.util.concurrent.atomic.AtomicLong generation = new java.util.concurrent.atomic.AtomicLong();
  private long lastGeneration = -1;
  private Path lastRoot;
  private Map<Path, Stamp> lastStamp;

  public AssetAutoLabelMonitor(Consumer<AssetAutoLabelService.ScanResult> onScan) {
    this.onScan = Objects.requireNonNull(onScan);
    executor.scheduleWithFixedDelay(this::poll, 0, 4, TimeUnit.SECONDS);
  }

  public void setProjectRoot(File project) {
    Path next = project == null ? null : project.toPath().toAbsolutePath().normalize();
    if (Objects.equals(root, next)) return;
    root = next;
    generation.incrementAndGet();
    executor.execute(this::poll);
  }

  private void poll() {
    long revision = generation.get();
    Path project = root;
    if (project == null || !Files.isDirectory(project)) return;
    try {
      Map<Path, Stamp> stamp = fingerprint(project);
      if (revision == lastGeneration && project.equals(lastRoot) && stamp.equals(lastStamp)) return;
      AssetAutoLabelService.ScanResult result = service.preview(project);
      if (revision != generation.get() || !project.equals(root)) return;
      lastGeneration = revision;
      lastRoot = project;
      lastStamp = stamp;
      onScan.accept(result);
    } catch (IOException | RuntimeException ignored) {
      // A partially copied/locked file is retried on the next poll; no UI thread I/O or modal errors.
    }
  }

  static Map<Path, Stamp> fingerprint(Path project) throws IOException {
    Map<Path, Stamp> result = new LinkedHashMap<>();
    for (String directory : new String[]{"assets", "game/images", "scripts"}) {
      Path base = project.resolve(directory);
      if (!Files.isDirectory(base)) continue;
      try (var paths = Files.walk(base, 16)) {
        for (Path file : paths.filter(Files::isRegularFile).toList()) {
          if (AssetAutoLabelService.isSupportedAsset(file) || file.toString().endsWith(".vns")) {
            addStamp(result, file);
          }
        }
      }
    }
    addStamp(result, project.resolve("jvn.project"));
    addStamp(result, project.resolve(AssetAutoLabelService.REGISTRY_PATH));
    return Map.copyOf(result);
  }

  private static void addStamp(Map<Path, Stamp> result, Path file) throws IOException {
    if (!Files.isRegularFile(file)) return;
    BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
    result.put(file, new Stamp(attributes.size(), attributes.lastModifiedTime()));
  }

  record Stamp(long size, java.nio.file.attribute.FileTime modified) {}

  @Override public void close() {
    root = null;
    executor.shutdownNow();
  }
}
