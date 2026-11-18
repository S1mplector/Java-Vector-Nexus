package com.jvn.editor.ui;

import java.io.ByteArrayOutputStream;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import com.sun.management.HotSpotDiagnosticMXBean;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.paint.Color;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class TrashmanView extends BorderPane {
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final long JCMD_TIMEOUT_MS = 6_000L;
  private static final String NO_JCMD = "";
  private static volatile String cachedJcmdCommand;

  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "jvn-trashman");
    t.setDaemon(true);
    return t;
  });
  private final Timeline autoRefreshTimer = new Timeline(new KeyFrame(Duration.seconds(2), e -> refreshStatus()));

  private final Label heapValue = new Label("--");
  private final Label heapDetail = new Label("--");
  private final ProgressBar heapBar = new ProgressBar(0);
  private final Label nonHeapValue = new Label("--");
  private final Label nonHeapDetail = new Label("--");
  private final ProgressBar nonHeapBar = new ProgressBar(0);
  private final Label gcValue = new Label("--");
  private final Label gcDetail = new Label("--");
  private final Label jcmdValue = new Label("--");
  private final Label jcmdDetail = new Label("--");
  private final Label pressureValue = new Label("--");
  private final Label pressureDetail = new Label("--");
  private final ProgressBar pressureBar = new ProgressBar(0);
  private final Label gcLoadValue = new Label("--");
  private final Label gcLoadDetail = new Label("--");
  private final Label classesValue = new Label("--");
  private final Label classesDetail = new Label("--");
  private final Label threadsValue = new Label("--");
  private final Label threadsDetail = new Label("--");
  private final Label buffersValue = new Label("--");
  private final Label buffersDetail = new Label("--");
  private final Label telemetryValue = new Label("--");
  private final Label telemetryDetail = new Label("--");
  private final Label finalizerValue = new Label("--");
  private final Label finalizerDetail = new Label("--");
  private final Label statusLabel = new Label("Ready.");
  private final Label lastRunLabel = new Label("Last run: never");
  private final VBox collectorRows = new VBox(6);
  private final VBox poolRows = new VBox(6);
  private final VBox bufferRows = new VBox(6);
  private final TextArea reportArea = new TextArea();
  private final MiniGraph heapGraph = new MiniGraph("Heap", "#d6b16e", 96);
  private final MiniGraph nonHeapGraph = new MiniGraph("Non-heap", "#8fc7ff", 96);
  private final MiniGraph gcLoadGraph = new MiniGraph("GC load", "#8be28b", 96);
  private final MiniGraph telemetryGraph = new MiniGraph("Telemetry", "#d0a7ff", 96);

  private final Spinner<Integer> passSpinner = new Spinner<>(1, 8, 2);
  private final Spinner<Integer> pauseSpinner = new Spinner<>(0, 1_000, 120, 20);
  private final Spinner<Integer> thresholdSpinner = new Spinner<>(1, 99, 85);
  private final CheckBox finalizationCheck = option("Run finalization", true, "Attempt legacy finalization before each GC pass.");
  private final CheckBox jcmdCheck = option("Prefer jcmd", false, "Use jcmd <pid> GC.run when available, then fall back to JVM GC.");
  private final CheckBox beforeAfterCheck = option("Before/after report", true, "Capture memory snapshots around GC runs.");
  private final CheckBox autoRefreshCheck = option("Auto refresh", true, "Refresh memory telemetry every two seconds.");
  private final CheckBox liveReportCheck = option("Live report", false, "Rewrite the detailed report on each telemetry refresh.");
  private final CheckBox liveHeapDumpCheck = option("Live heap dump", true, "When dumping the heap, include only live objects after a full GC.");
  private final CheckBox histogramAllCheck = option("Histogram -all", false, "Ask jcmd to include all objects in the class histogram.");

  private volatile boolean busy;
  private volatile boolean telemetryBusy;
  private Snapshot latestSnapshot;
  private String lastActionReport = "No GC action has run yet.";

  public TrashmanView() {
    getStyleClass().add("sidebar-tool-root");
    autoRefreshTimer.setCycleCount(Timeline.INDEFINITE);
    buildUi();
    refreshStatus();
    autoRefreshTimer.playFromStart();
  }

  public void refreshStatus() {
    refreshStatus(false);
  }

  private void refreshStatus(boolean forceReportUpdate) {
    if (busy || telemetryBusy) return;
    telemetryBusy = true;
    try {
      worker.submit(() -> {
        try {
          Snapshot snapshot = Snapshot.capture();
          Platform.runLater(() -> {
            telemetryBusy = false;
            latestSnapshot = snapshot;
            renderSnapshot(snapshot, forceReportUpdate);
          });
        } catch (Exception ex) {
          Platform.runLater(() -> {
            telemetryBusy = false;
            statusLabel.setText("Telemetry refresh failed: " + ex.getClass().getSimpleName());
          });
        }
      });
    } catch (RejectedExecutionException ex) {
      telemetryBusy = false;
    }
  }

  public void dispose() {
    autoRefreshTimer.stop();
    worker.shutdownNow();
  }

  private void buildUi() {
    Label title = new Label("Trashman");
    title.getStyleClass().add("sidebar-tool-title");
    Label subtitle = new Label("Heap memory cleanup utility.");
    subtitle.getStyleClass().add("sidebar-tool-summary");
    subtitle.setWrapText(true);
    HBox titleRow = new HBox(6, title, SidebarToolHelp.button(this, "Trashman", """
        Trashman requests JVM garbage collection to free heap memory.
        Run GC performs a single pass. Sweep runs multiple passes with
        optional finalization between each. GC is only a request — the
        VM decides when and how much memory is actually reclaimed.
        """));
    titleRow.setAlignment(Pos.CENTER_LEFT);

    Button refreshButton = actionButton("Refresh", SidebarToolIcon.refresh("#d0d0d0"), "Refresh heap telemetry.");
    refreshButton.setOnAction(e -> refreshStatus(true));
    Button gcButton = actionButton("Run GC", SidebarToolIcon.delete("#d0d0d0"), "Request one JVM garbage collection pass.");
    gcButton.setOnAction(e -> runGc(false, false));
    Button sweepButton = actionButton("Sweep", SidebarToolIcon.sparkles("#f0b673"), "Run the configured multi-pass sweep.");
    sweepButton.setOnAction(e -> runGc(true, jcmdCheck.isSelected()));
    Button copyButton = actionButton("Copy Report", SidebarToolIcon.copy("#d0d0d0"), "Copy the current Trashman report.");
    copyButton.setOnAction(e -> copyText(reportText(latestSnapshot)));

    FlowPane actions = new FlowPane(6, 6, refreshButton, gcButton, sweepButton, copyButton);
    actions.getStyleClass().add("trashman-actions");
    actions.setAlignment(Pos.CENTER_LEFT);

    configureSpinner(passSpinner, "GC pass count for Sweep.");
    configureSpinner(pauseSpinner, "Milliseconds to pause between sweep passes.");
    Label passLabel = optionLabel("Passes");
    Label pauseLabel = optionLabel("Pause ms");
    HBox sweepOptions = new HBox(6, passLabel, passSpinner, pauseLabel, pauseSpinner);
    sweepOptions.setAlignment(Pos.CENTER_LEFT);

    autoRefreshCheck.selectedProperty().addListener((obs, oldValue, enabled) -> {
      if (enabled) {
        autoRefreshTimer.playFromStart();
      } else {
        autoRefreshTimer.stop();
      }
    });

    FlowPane toggles = new FlowPane(8, 7, finalizationCheck, autoRefreshCheck);
    toggles.getStyleClass().add("trashman-options");

    VBox header = new VBox(6, titleRow, subtitle, actions, sweepOptions, toggles, statusLabel, lastRunLabel);
    header.getStyleClass().add("sidebar-tool-header");
    setTop(header);

    FlowPane cards = new FlowPane(8, 8,
        metricCard("Heap", heapValue, heapDetail, heapBar),
        metricCard("Pressure", pressureValue, pressureDetail, pressureBar),
        metricCard("GC Load", gcLoadValue, gcLoadDetail, null));
    cards.getStyleClass().add("trashman-card-grid");

    FlowPane graphs = new FlowPane(8, 8,
        graphCard(heapGraph));
    graphs.getStyleClass().add("trashman-graph-grid");

    Label collectorsTitle = sectionTitle("Collectors");
    reportArea.getStyleClass().add("trashman-report");
    reportArea.setEditable(false);
    reportArea.setWrapText(false);
    reportArea.setPrefRowCount(8);

    VBox body = new VBox(10,
        cards,
        graphs,
        new Separator(),
        collectorsTitle,
        collectorRows,
        new Separator(),
        sectionTitle("Report"),
        reportArea);
    body.getStyleClass().add("trashman-body");

    ScrollPane scroll = new ScrollPane(body);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("sidebar-tool-scroll");
    setCenter(scroll);
  }

  private VBox metricCard(String title, Label value, Label detail, ProgressBar bar) {
    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("trashman-card-title");
    value.getStyleClass().add("trashman-card-value");
    detail.getStyleClass().add("trashman-card-detail");
    detail.setWrapText(true);
    VBox card = new VBox(5, titleLabel, value, detail);
    if (bar != null) {
      bar.setMaxWidth(Double.MAX_VALUE);
      bar.getStyleClass().add("trashman-progress");
      card.getChildren().add(bar);
    }
    card.getStyleClass().add("trashman-card");
    card.setPrefWidth(172);
    return card;
  }

  private VBox graphCard(MiniGraph graph) {
    Label titleLabel = new Label(graph.title());
    titleLabel.getStyleClass().add("trashman-card-title");
    graph.getStyleClass().add("trashman-live-graph");
    VBox card = new VBox(6, titleLabel, graph);
    card.getStyleClass().add("trashman-graph-card");
    card.setPrefWidth(260);
    return card;
  }

  private void renderSnapshot(Snapshot snapshot, boolean forceReportUpdate) {
    if (snapshot == null) return;
    heapValue.setText(formatBytes(snapshot.heapUsed()) + " used");
    heapDetail.setText(formatBytes(snapshot.heapCommitted()) + " committed / " + maxText(snapshot.heapMax()) + " max");
    heapBar.setProgress(ratio(snapshot.heapUsed(), positiveOr(snapshot.heapMax(), snapshot.heapCommitted())));
    nonHeapValue.setText(formatBytes(snapshot.nonHeapUsed()) + " used");
    nonHeapDetail.setText(formatBytes(snapshot.nonHeapCommitted()) + " committed / " + maxText(snapshot.nonHeapMax()) + " max");
    nonHeapBar.setProgress(ratio(snapshot.nonHeapUsed(), positiveOr(snapshot.nonHeapMax(), snapshot.nonHeapCommitted())));
    pressureValue.setText(percentText(snapshot.heapPressure()) + " heap");
    pressureDetail.setText(formatBytes(snapshot.heapFreeFromMax()) + " max headroom / "
        + formatBytes(snapshot.heapFreeFromCommitted()) + " committed free");
    pressureBar.setProgress(snapshot.heapPressure());
    gcValue.setText(snapshot.gcCount() + " collections");
    gcDetail.setText(snapshot.gcTimeMs() + " ms total GC time");
    gcLoadValue.setText(percentText(snapshot.gcOverhead()));
    gcLoadDetail.setText("GC time / JVM uptime");
    classesValue.setText(snapshot.loadedClasses() + " loaded");
    classesDetail.setText(snapshot.totalLoadedClasses() + " total / " + snapshot.unloadedClasses() + " unloaded");
    threadsValue.setText(snapshot.threadCount() + " live");
    threadsDetail.setText(snapshot.daemonThreadCount() + " daemon / " + snapshot.peakThreadCount() + " peak");
    buffersValue.setText(formatBytes(snapshot.bufferUsed()));
    buffersDetail.setText(snapshot.bufferCount() + " buffers / " + formatBytes(snapshot.bufferCapacity()) + " capacity");
    telemetryValue.setText(snapshot.captureDurationMs() + " ms");
    telemetryDetail.setText("last snapshot capture");
    finalizerValue.setText(Integer.toString(snapshot.pendingFinalization()));
    finalizerDetail.setText("objects pending finalization");
    jcmdValue.setText(snapshot.jcmdAvailable() ? "Available" : "Unavailable");
    jcmdDetail.setText(snapshot.jcmdDetail());
    lastRunLabel.setText("Last telemetry refresh: " + LocalTime.now().format(TIME_FORMAT));
    renderLiveGraphs(snapshot);

    collectorRows.getChildren().clear();
    for (CollectorInfo collector : snapshot.collectors()) {
      collectorRows.getChildren().add(collectorRow(collector));
    }
    poolRows.getChildren().clear();
    for (PoolInfo pool : snapshot.pools()) {
      poolRows.getChildren().add(poolRow(pool));
    }
    bufferRows.getChildren().clear();
    for (BufferInfo buffer : snapshot.buffers()) {
      bufferRows.getChildren().add(bufferRow(buffer));
    }
    if (forceReportUpdate || liveReportCheck.isSelected() || reportArea.getText().isBlank()) {
      reportArea.setText(reportText(snapshot));
    }
  }

  private void renderLiveGraphs(Snapshot snapshot) {
    long heapDenominator = positiveOr(snapshot.heapMax(), snapshot.heapCommitted());
    long nonHeapDenominator = positiveOr(snapshot.nonHeapMax(), snapshot.nonHeapCommitted());
    heapGraph.addSample(ratio(snapshot.heapUsed(), heapDenominator), percentText(snapshot.heapPressure()));
    nonHeapGraph.addSample(ratio(snapshot.nonHeapUsed(), nonHeapDenominator), percentText(ratio(snapshot.nonHeapUsed(), nonHeapDenominator)));
    gcLoadGraph.addSample(snapshot.gcOverhead(), percentText(snapshot.gcOverhead()));
    telemetryGraph.addSample(ratio(snapshot.captureDurationMs(), 50L), snapshot.captureDurationMs() + " ms");
  }

  private HBox collectorRow(CollectorInfo collector) {
    Label name = new Label(collector.name());
    name.getStyleClass().add("trashman-row-title");
    Label detail = new Label(collector.countText() + " | " + collector.timeText());
    detail.getStyleClass().add("trashman-row-detail");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox row = new HBox(8, name, spacer, detail);
    row.getStyleClass().add("trashman-row");
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
  }

  private VBox poolRow(PoolInfo pool) {
    Label name = new Label(pool.name());
    name.getStyleClass().add("trashman-row-title");
    Label type = new Label(pool.type());
    type.getStyleClass().add("trashman-chip");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox heading = new HBox(8, name, spacer, type);
    heading.setAlignment(Pos.CENTER_LEFT);
    ProgressBar bar = new ProgressBar(pool.percent());
    bar.setMaxWidth(Double.MAX_VALUE);
    bar.getStyleClass().add("trashman-progress");
    Label detail = new Label(pool.detail());
    detail.getStyleClass().add("trashman-row-detail");
    detail.setWrapText(true);
    VBox row = new VBox(5, heading, bar, detail);
    row.getStyleClass().add("trashman-row");
    if (pool.percent() >= 0.85 || pool.usageThresholdExceeded() || pool.collectionThresholdExceeded()) {
      row.getStyleClass().add("trashman-row-warn");
    }
    return row;
  }

  private VBox bufferRow(BufferInfo buffer) {
    Label name = new Label(buffer.name());
    name.getStyleClass().add("trashman-row-title");
    Label count = new Label(buffer.count() + " buffers");
    count.getStyleClass().add("trashman-chip");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox heading = new HBox(8, name, spacer, count);
    heading.setAlignment(Pos.CENTER_LEFT);
    ProgressBar bar = new ProgressBar(ratio(buffer.used(), positiveOr(buffer.capacity(), buffer.used())));
    bar.setMaxWidth(Double.MAX_VALUE);
    bar.getStyleClass().add("trashman-progress");
    Label detail = new Label(formatBytes(buffer.used()) + " used / " + formatBytes(buffer.capacity()) + " capacity");
    detail.getStyleClass().add("trashman-row-detail");
    detail.setWrapText(true);
    VBox row = new VBox(5, heading, bar, detail);
    row.getStyleClass().add("trashman-row");
    return row;
  }

  private void runGc(boolean sweep, boolean preferJcmd) {
    if (busy) return;
    busy = true;
    boolean captureBeforeAfter = beforeAfterCheck.isSelected();
    boolean runFinalization = finalizationCheck.isSelected();
    int passes = sweep ? passSpinner.getValue() : 1;
    int pauseMs = sweep ? pauseSpinner.getValue() : 0;
    statusLabel.setText(sweep ? "Running configured sweep..." : "Requesting JVM GC...");
    worker.submit(() -> {
      Snapshot before = captureBeforeAfter ? Snapshot.capture() : null;
      StringBuilder log = new StringBuilder();
      boolean usedJcmd = false;
      for (int i = 0; i < passes; i++) {
        if (preferJcmd) {
          CommandResult result = runJcmdGcCommand();
          log.append("jcmd pass ").append(i + 1).append(": ").append(result.summary()).append('\n');
          usedJcmd = result.success();
          if (!result.success()) requestJvmGc(runFinalization);
        } else {
          requestJvmGc(runFinalization);
          log.append("JVM GC pass ").append(i + 1).append(" requested.\n");
        }
        if (pauseMs > 0 && i < passes - 1) sleep(pauseMs);
      }
      Snapshot after = Snapshot.capture();
      String report = actionReport(sweep ? "Sweep" : "Run GC", before, after, log.toString(), usedJcmd);
      Platform.runLater(() -> {
        busy = false;
        latestSnapshot = after;
        lastActionReport = report;
        statusLabel.setText((sweep ? "Sweep" : "GC") + " complete. Reclaimed " + reclaimedText(before, after) + ".");
        renderSnapshot(after, true);
      });
    });
  }

  private void runJcmdGc() {
    if (busy) return;
    busy = true;
    boolean captureBeforeAfter = beforeAfterCheck.isSelected();
    statusLabel.setText("Executing jcmd GC.run...");
    worker.submit(() -> {
      Snapshot before = captureBeforeAfter ? Snapshot.capture() : null;
      CommandResult result = runJcmdGcCommand();
      Snapshot after = Snapshot.capture();
      String report = actionReport("jcmd GC.run", before, after, result.output(), result.success());
      Platform.runLater(() -> {
        busy = false;
        latestSnapshot = after;
        lastActionReport = report;
        statusLabel.setText(result.success()
            ? "jcmd GC.run complete. Reclaimed " + reclaimedText(before, after) + "."
            : "jcmd GC.run failed. See report.");
        renderSnapshot(after, true);
      });
    });
  }

  private void runFinalizationOnly() {
    if (busy) return;
    busy = true;
    boolean captureBeforeAfter = beforeAfterCheck.isSelected();
    statusLabel.setText("Requesting legacy finalization...");
    worker.submit(() -> {
      Snapshot before = captureBeforeAfter ? Snapshot.capture() : null;
      requestFinalization();
      Snapshot after = Snapshot.capture();
      String report = actionReport("Finalize", before, after, "System finalization requested.", false);
      Platform.runLater(() -> {
        busy = false;
        latestSnapshot = after;
        lastActionReport = report;
        statusLabel.setText("Finalization request complete.");
        renderSnapshot(after, true);
      });
    });
  }

  private void runJcmdDiagnostic(String action, String... arguments) {
    runJcmdDiagnostic(action, List.of(arguments));
  }

  private void runJcmdDiagnostic(String action, List<String> arguments) {
    if (busy) return;
    busy = true;
    statusLabel.setText("Executing " + action + "...");
    worker.submit(() -> {
      CommandResult result = runJcmdCommand(arguments);
      Snapshot after = Snapshot.capture();
      String report = diagnosticReport(action, result);
      Platform.runLater(() -> {
        busy = false;
        latestSnapshot = after;
        lastActionReport = report;
        statusLabel.setText(result.success() ? action + " complete." : action + " failed. See report.");
        renderSnapshot(after, true);
      });
    });
  }

  private void dumpHeap(boolean liveOnly) {
    if (busy) return;
    busy = true;
    statusLabel.setText(liveOnly ? "Writing live heap dump..." : "Writing full heap dump...");
    worker.submit(() -> {
      Path file = heapDumpPath(liveOnly);
      CommandResult result;
      try {
        Files.createDirectories(file.getParent());
        HotSpotDiagnosticMXBean diagnostics = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        if (diagnostics == null) {
          result = new CommandResult(-1, "HotSpot diagnostic MXBean is not available in this JVM.");
        } else {
          diagnostics.dumpHeap(file.toAbsolutePath().toString(), liveOnly);
          result = new CommandResult(0, "Wrote " + file.toAbsolutePath());
        }
      } catch (Exception ex) {
        result = new CommandResult(-1, ex.getClass().getSimpleName() + ": " + ex.getMessage());
      }
      Snapshot after = Snapshot.capture();
      String report = diagnosticReport(liveOnly ? "Live heap dump" : "Full heap dump", result);
      CommandResult dumpResult = result;
      Platform.runLater(() -> {
        busy = false;
        latestSnapshot = after;
        lastActionReport = report;
        statusLabel.setText(dumpResult.success() ? "Heap dump written." : "Heap dump failed. See report.");
        renderSnapshot(after, true);
      });
    });
  }

  private void resetPeaks() {
    int reset = 0;
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
      try {
        pool.resetPeakUsage();
        reset++;
      } catch (Exception ignored) {
      }
    }
    statusLabel.setText("Reset peak usage counters for " + reset + " memory pools.");
    refreshStatus(true);
  }

  private void armThresholds(boolean collectionUsage) {
    int percent = thresholdSpinner.getValue();
    int armed = 0;
    int unsupported = 0;
    int failed = 0;
    StringBuilder report = new StringBuilder();
    report.append(collectionUsage ? "Collection usage thresholds" : "Usage thresholds")
        .append(" at ").append(percent).append("%\n");
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
      try {
        boolean supported = collectionUsage
            ? pool.isCollectionUsageThresholdSupported()
            : pool.isUsageThresholdSupported();
        if (!supported) {
          unsupported++;
          continue;
        }
        MemoryUsage usage = collectionUsage ? pool.getCollectionUsage() : pool.getUsage();
        if (usage == null) usage = pool.getUsage();
        if (usage == null) {
          unsupported++;
          continue;
        }
        long denominator = positiveOr(usage.getMax(), usage.getCommitted());
        if (denominator <= 0) {
          unsupported++;
          continue;
        }
        long threshold = Math.max(1L, Math.round(denominator * (percent / 100.0)));
        if (collectionUsage) {
          pool.setCollectionUsageThreshold(threshold);
        } else {
          pool.setUsageThreshold(threshold);
        }
        armed++;
        report.append("- ").append(pool.getName()).append(": ").append(formatBytes(threshold)).append('\n');
      } catch (Exception ex) {
        failed++;
        report.append("- ").append(pool.getName()).append(": ")
            .append(ex.getClass().getSimpleName()).append(": ").append(ex.getMessage()).append('\n');
      }
    }
    lastActionReport = report.toString();
    statusLabel.setText("Armed " + armed + " threshold" + (armed == 1 ? "" : "s")
        + " (" + unsupported + " unsupported, " + failed + " failed).");
    refreshStatus(true);
  }

  private void clearThresholds() {
    int cleared = 0;
    int failed = 0;
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
      try {
        if (pool.isUsageThresholdSupported()) {
          pool.setUsageThreshold(0L);
          cleared++;
        }
        if (pool.isCollectionUsageThresholdSupported()) {
          pool.setCollectionUsageThreshold(0L);
          cleared++;
        }
      } catch (Exception ignored) {
        failed++;
      }
    }
    lastActionReport = "Cleared " + cleared + " memory pool thresholds"
        + (failed > 0 ? " (" + failed + " failed)." : ".");
    statusLabel.setText(lastActionReport);
    refreshStatus(true);
  }

  private void requestJvmGc(boolean runFinalization) {
    if (runFinalization) {
      requestFinalization();
    }
    System.gc();
    ManagementFactory.getMemoryMXBean().gc();
  }

  private void requestFinalization() {
    try {
      System.class.getMethod("runFinalization").invoke(null);
    } catch (Exception ignored) {
    }
  }

  private Path heapDumpPath(boolean liveOnly) {
    Snapshot snapshot = latestSnapshot == null ? Snapshot.capture() : latestSnapshot;
    String pid = snapshot.pid().replaceAll("[^A-Za-z0-9_.-]", "_");
    String suffix = liveOnly ? "live" : "full";
    String fileName = "jvn-heap-" + pid + "-" + System.currentTimeMillis() + "-" + suffix + ".hprof";
    String home = System.getProperty("user.home", ".");
    return Path.of(home, ".jvn", "trashman", fileName);
  }

  private CommandResult runJcmdGcCommand() {
    return runJcmdCommand(List.of("GC.run"));
  }

  private CommandResult runJcmdCommand(List<String> arguments) {
    Snapshot snapshot = Snapshot.capture();
    if (!snapshot.jcmdAvailable()) {
      return new CommandResult(-1, snapshot.jcmdDetail());
    }
    List<String> command = new ArrayList<>();
    command.add(snapshot.jcmdCommand());
    command.add(snapshot.pid());
    command.addAll(arguments);
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();
      ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
      Thread reader = new Thread(() -> {
        try {
          process.getInputStream().transferTo(outputBuffer);
        } catch (Exception ignored) {
        }
      }, "jvn-trashman-jcmd-output");
      reader.setDaemon(true);
      reader.start();
      boolean finished = process.waitFor(JCMD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        reader.join(500L);
        return new CommandResult(-1, "Timed out after " + JCMD_TIMEOUT_MS + " ms");
      }
      reader.join(500L);
      String output = outputBuffer.toString(StandardCharsets.UTF_8).trim();
      if (output.length() > 200_000) {
        output = output.substring(0, 200_000) + "\n[Trashman truncated output after 200000 characters.]";
      }
      return new CommandResult(process.exitValue(), output);
    } catch (Exception ex) {
      return new CommandResult(-1, ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }
  }

  private String reportText(Snapshot snapshot) {
    if (snapshot == null) return "No Trashman report yet.";
    StringBuilder out = new StringBuilder();
    out.append("# Trashman JVM GC Report\n");
    out.append("Generated: ").append(LocalTime.now().format(TIME_FORMAT)).append('\n');
    out.append("PID: ").append(snapshot.pid()).append('\n');
    out.append("JVM: ").append(snapshot.jvm()).append('\n');
    out.append("Uptime: ").append(snapshot.uptimeMs()).append(" ms\n\n");
    out.append("Heap: ").append(formatBytes(snapshot.heapUsed())).append(" used / ")
        .append(maxText(snapshot.heapMax())).append(" max\n");
    out.append("Non-heap: ").append(formatBytes(snapshot.nonHeapUsed())).append(" used / ")
        .append(maxText(snapshot.nonHeapMax())).append(" max\n");
    out.append("Heap pressure: ").append(percentText(snapshot.heapPressure()))
        .append(" / headroom ").append(formatBytes(snapshot.heapFreeFromMax())).append('\n');
    out.append("GC: ").append(snapshot.gcCount()).append(" collections / ")
        .append(snapshot.gcTimeMs()).append(" ms / ")
        .append(percentText(snapshot.gcOverhead())).append(" uptime overhead\n");
    out.append("Classes: ").append(snapshot.loadedClasses()).append(" loaded / ")
        .append(snapshot.totalLoadedClasses()).append(" total / ")
        .append(snapshot.unloadedClasses()).append(" unloaded\n");
    out.append("Threads: ").append(snapshot.threadCount()).append(" live / ")
        .append(snapshot.daemonThreadCount()).append(" daemon / ")
        .append(snapshot.peakThreadCount()).append(" peak\n");
    out.append("Buffers: ").append(snapshot.bufferCount()).append(" buffers / ")
        .append(formatBytes(snapshot.bufferUsed())).append(" used / ")
        .append(formatBytes(snapshot.bufferCapacity())).append(" capacity\n");
    out.append("Telemetry capture: ").append(snapshot.captureDurationMs()).append(" ms\n");
    out.append("Finalizers: ").append(snapshot.pendingFinalization()).append(" pending\n");
    out.append("jcmd: ").append(snapshot.jcmdDetail()).append("\n\n");
    out.append("Last action:\n").append(lastActionReport).append("\n\n");
    out.append("Collectors:\n");
    for (CollectorInfo collector : snapshot.collectors()) {
      out.append("- ").append(collector.name()).append(": ")
          .append(collector.countText()).append(", ")
          .append(collector.timeText()).append('\n');
    }
    out.append("\nMemory pools:\n");
    for (PoolInfo pool : snapshot.pools()) {
      out.append("- ").append(pool.name()).append(" [").append(pool.type()).append("]: ")
          .append(pool.detail()).append('\n');
    }
    out.append("\nBuffer pools:\n");
    for (BufferInfo buffer : snapshot.buffers()) {
      out.append("- ").append(buffer.name()).append(": ")
          .append(buffer.count()).append(" buffers, ")
          .append(formatBytes(buffer.used())).append(" used, ")
          .append(formatBytes(buffer.capacity())).append(" capacity\n");
    }
    return out.toString();
  }

  private String diagnosticReport(String action, CommandResult result) {
    StringBuilder out = new StringBuilder();
    out.append(action).append(" at ").append(LocalTime.now().format(TIME_FORMAT)).append('\n');
    out.append("Mode: jcmd diagnostic\n");
    out.append("Exit: ").append(result.exitCode()).append('\n');
    if (result.output() != null && !result.output().isBlank()) {
      out.append(result.output().strip()).append('\n');
    }
    return out.toString();
  }

  private String actionReport(String action, Snapshot before, Snapshot after, String commandLog, boolean externalGc) {
    StringBuilder out = new StringBuilder();
    out.append(action).append(" at ").append(LocalTime.now().format(TIME_FORMAT)).append('\n');
    out.append("Mode: ").append(externalGc ? "jcmd/external" : "JVM request").append('\n');
    if (before != null && after != null) {
      out.append("Heap before: ").append(formatBytes(before.heapUsed())).append('\n');
      out.append("Heap after: ").append(formatBytes(after.heapUsed())).append('\n');
      out.append("Heap reclaimed: ").append(reclaimedText(before, after)).append('\n');
      out.append("GC count delta: ").append(after.gcCount() - before.gcCount()).append('\n');
      out.append("GC time delta: ").append(after.gcTimeMs() - before.gcTimeMs()).append(" ms\n");
    }
    if (commandLog != null && !commandLog.isBlank()) {
      out.append(commandLog.strip()).append('\n');
    }
    return out.toString();
  }

  private String reclaimedText(Snapshot before, Snapshot after) {
    if (before == null || after == null) return "unknown";
    long reclaimed = before.heapUsed() - after.heapUsed();
    if (reclaimed < 0) return "-" + formatBytes(Math.abs(reclaimed));
    return formatBytes(reclaimed);
  }

  private static CheckBox option(String text, boolean selected, String tooltip) {
    CheckBox box = new CheckBox(text);
    box.setSelected(selected);
    box.setTooltip(new Tooltip(tooltip));
    return box;
  }

  private void configureSpinner(Spinner<Integer> spinner, String tooltip) {
    spinner.setEditable(true);
    spinner.getStyleClass().add("trashman-spinner");
    spinner.setTooltip(new Tooltip(tooltip));
    spinner.setMaxWidth(84);
  }

  private Label optionLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("trashman-option-label");
    return label;
  }

  private Button actionButton(String text, Region icon, String tooltip) {
    Button button = new Button(text);
    button.getStyleClass().add("trashman-action-button");
    button.setGraphic(icon);
    button.setContentDisplay(ContentDisplay.LEFT);
    button.setTooltip(new Tooltip(tooltip));
    return button;
  }

  private Label sectionTitle(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("trashman-section-title");
    return label;
  }

  private void copyText(String text) {
    ClipboardContent content = new ClipboardContent();
    content.putString(text == null ? "" : text);
    Clipboard.getSystemClipboard().setContent(content);
    statusLabel.setText("Copied Trashman report.");
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(Math.max(0L, millis));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private static long positiveOr(long first, long fallback) {
    return first > 0 ? first : Math.max(0L, fallback);
  }

  private static double ratio(long used, long max) {
    if (used < 0 || max <= 0) return 0.0;
    return Math.min(1.0, used / (double) max);
  }

  private static String maxText(long bytes) {
    return bytes <= 0 ? "unbounded" : formatBytes(bytes);
  }

  private static String percentText(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) return "--";
    return String.format(Locale.ROOT, "%.1f%%", Math.min(1.0, value) * 100.0);
  }

  private static String formatBytes(long bytes) {
    if (bytes < 0) return "--";
    double value = bytes;
    String[] units = {"B", "KB", "MB", "GB"};
    int unit = 0;
    while (value >= 1024.0 && unit < units.length - 1) {
      value /= 1024.0;
      unit++;
    }
    return unit == 0
        ? String.format(Locale.ROOT, "%.0f %s", value, units[unit])
        : String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
  }

  private record CommandResult(int exitCode, String output) {
    boolean success() {
      return exitCode == 0;
    }

    String summary() {
      if (success()) return output == null || output.isBlank() ? "OK" : output.lines().findFirst().orElse("OK");
      return output == null || output.isBlank() ? "exit " + exitCode : output.lines().findFirst().orElse("exit " + exitCode);
    }
  }

  private record CollectorInfo(String name, long count, long timeMs) {
    String countText() {
      return count < 0 ? "count unavailable" : count + " collections";
    }

    String timeText() {
      return timeMs < 0 ? "time unavailable" : timeMs + " ms";
    }
  }

  private record PoolInfo(
      String name,
      String type,
      long used,
      long committed,
      long max,
      long peakUsed,
      double percent,
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

  private record BufferInfo(String name, long count, long used, long capacity) {
  }

  private static final class MiniGraph extends Canvas {
    private static final double WIDTH = 242.0;
    private static final double HEIGHT = 74.0;
    private final String title;
    private final Color lineColor;
    private final double[] samples;
    private int size;
    private int cursor;
    private String latestLabel = "--";

    MiniGraph(String title, String lineColor, int capacity) {
      super(WIDTH, HEIGHT);
      this.title = title;
      this.lineColor = Color.web(lineColor);
      this.samples = new double[Math.max(8, capacity)];
      setMouseTransparent(true);
      draw();
    }

    String title() {
      return title;
    }

    void addSample(double value, String label) {
      samples[cursor] = clamp01(value);
      cursor = (cursor + 1) % samples.length;
      if (size < samples.length) size++;
      latestLabel = label == null || label.isBlank() ? "--" : label;
      draw();
    }

    private void draw() {
      GraphicsContext gc = getGraphicsContext2D();
      double w = getWidth();
      double h = getHeight();
      gc.clearRect(0, 0, w, h);
      gc.setFill(Color.web("#151515"));
      gc.fillRoundRect(0, 0, w, h, 8, 8);
      gc.setStroke(Color.web("#2c2c2c"));
      gc.setLineWidth(1.0);
      gc.strokeRoundRect(0.5, 0.5, w - 1.0, h - 1.0, 8, 8);
      gc.setStroke(Color.web("#262626"));
      gc.setLineWidth(1.0);
      for (int i = 1; i <= 3; i++) {
        double y = Math.round((h - 18.0) * i / 4.0) + 0.5;
        gc.strokeLine(8.0, y, w - 8.0, y);
      }
      if (size > 1) {
        double graphTop = 8.0;
        double graphBottom = h - 21.0;
        double graphHeight = graphBottom - graphTop;
        double xStep = (w - 18.0) / Math.max(1, samples.length - 1);
        gc.setStroke(lineColor.deriveColor(0, 1, 1, 0.35));
        gc.setLineWidth(5.0);
        drawLine(gc, graphTop, graphBottom, graphHeight, xStep);
        gc.setStroke(lineColor);
        gc.setLineWidth(2.0);
        drawLine(gc, graphTop, graphBottom, graphHeight, xStep);
      }
      gc.setFill(Color.web("#d9d6d2"));
      gc.fillText(latestLabel, 8.0, h - 7.0);
    }

    private void drawLine(GraphicsContext gc, double graphTop, double graphBottom, double graphHeight, double xStep) {
      gc.beginPath();
      for (int i = 0; i < size; i++) {
        int sampleIndex = (cursor - size + i + samples.length) % samples.length;
        double x = 9.0 + (samples.length - size + i) * xStep;
        double y = graphBottom - samples[sampleIndex] * graphHeight;
        if (i == 0) {
          gc.moveTo(x, y);
        } else {
          gc.lineTo(x, y);
        }
      }
      gc.stroke();
    }

    private static double clamp01(double value) {
      if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
      return Math.max(0.0, Math.min(1.0, value));
    }
  }

  private record Snapshot(
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
    long heapFreeFromCommitted() {
      return Math.max(0L, heapCommitted - heapUsed);
    }

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
        if (c > 0) count += c;
        if (t > 0) time += t;
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
            ratio(used, positiveOr(max, usage.getCommitted())),
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
        long c = Math.max(0L, buffer.getCount());
        long used = Math.max(0L, buffer.getMemoryUsed());
        long capacity = Math.max(0L, buffer.getTotalCapacity());
        bufferCount += c;
        bufferUsed += used;
        bufferCapacity += capacity;
        buffers.add(new BufferInfo(buffer.getName(), c, used, capacity));
      }
      String pid = runtime.getName();
      int at = pid.indexOf('@');
      if (at > 0) pid = pid.substring(0, at);
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
        cachedJcmdCommand = resolved == null ? NO_JCMD : resolved;
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
