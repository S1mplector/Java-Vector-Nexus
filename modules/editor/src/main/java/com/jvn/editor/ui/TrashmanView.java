package com.jvn.editor.ui;

import static com.jvn.editor.ui.TrashmanTelemetry.formatBytes;
import static com.jvn.editor.ui.TrashmanTelemetry.maxText;
import static com.jvn.editor.ui.TrashmanTelemetry.percentText;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import com.jvn.editor.ui.TrashmanTelemetry.Snapshot;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Live editor JVM memory monitor, cache maintenance and diagnostic inspection. */
public class TrashmanView extends BorderPane {
  private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final int HISTORY_LIMIT = 12;
  private final IntSupplier trimCaches;
  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "jvn-trashman");
    t.setDaemon(true);
    return t;
  });
  private final Timeline timer = new Timeline(new KeyFrame(Duration.seconds(2), e -> refreshStatus()));
  private final CheckBox live = new CheckBox("Live");
  private final Label status = label("Reading JVM counters…", "tm-status");
  private final Label clock = label("Editor memory", "tm-muted");
  private final Label heap = label("—", "tm-hero");
  private final Label capacity = label("Waiting for the first sample", "tm-muted");
  private final ProgressBar pressure = new ProgressBar(0);
  private final TrashmanHeapChart chart = new TrashmanHeapChart();
  private final Label peak = label("—", "tm-number");
  private final Label headroom = label("—", "tm-number");
  private final Label gcActivity = label("—", "tm-number");
  private final Label comparison = label("Pin the current sample to measure what changes as you work.", "tm-muted");
  private final Label heapChange = label("—", "tm-number");
  private final Label nonHeapChange = label("—", "tm-number");
  private final Label bufferChange = label("—", "tm-number");
  private final FlowPane changes = flow(metric("Heap", heapChange), metric("Non-heap", nonHeapChange),
      metric("Buffers", bufferChange));
  private final Button pin = new Button("Pin baseline");
  private final Button refresh = new Button();
  private final Button collect = new Button("Run GC");
  private final Button trim = new Button("Release preview caches");
  private final Button sweep = new Button("Run sweep");
  private final Button stop = new Button("Stop sweep");
  private final Button histogram = new Button("Class histogram");
  private final Button copy = new Button("Copy report");
  private final Spinner<Integer> passes = new Spinner<>(1, 8, 2);
  private final Spinner<Integer> pause = new Spinner<>(0, 1000, 120, 20);
  private final CheckBox finalize = new CheckBox("Request legacy finalization");
  private final VBox historyRows = new VBox();
  private final VBox runtimeRows = new VBox();
  private final VBox poolRows = new VBox();
  private final VBox bufferRows = new VBox();
  private final VBox collectorRows = new VBox();
  private final VBox classRows = new VBox();
  private final VBox histogramResult = new VBox(6);
  private final TextArea histogramOutput = new TextArea();
  private final Label jdkInfo = label("", "tm-muted");
  private final TabPane pages = new TabPane();
  private final AtomicBoolean stopRequested = new AtomicBoolean();
  private final Deque<Cleanup> history = new ArrayDeque<>();
  private Snapshot latest;
  private Snapshot baseline;
  private String baselineTime;
  private String sampleTime;
  private long peakHeap;
  private boolean sampling;
  private boolean busy;
  private boolean sweeping;
  private boolean disposed;
  private Tab inspect;

  public TrashmanView() { this(null); }

  public TrashmanView(IntSupplier trimCaches) {
    this.trimCaches = trimCaches;
    getStyleClass().add("tm-root");
    getStylesheets().add(TrashmanView.class.getResource("trashman.css").toExternalForm());
    setMinWidth(0);
    setTop(header());
    pages.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    inspect = page("Inspect", inspector());
    pages.getTabs().addAll(page("Monitor", monitor()), page("Cleanup", cleanup()), inspect);
    pages.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> {
      if (selected == inspect && latest != null) renderInspector(latest);
    });
    setCenter(pages);
    setBottom(status);
    status.setMaxWidth(Double.MAX_VALUE);
    live.setSelected(true);
    timer.setCycleCount(Timeline.INDEFINITE);
    live.selectedProperty().addListener((o, old, value) -> updateTimer());
    sceneProperty().addListener((o, old, value) -> updateTimer());
    updateControls();
    refreshStatus();
  }

  private Node header() {
    Label title = label("Trashman", "tm-title");
    VBox identity = new VBox(1, title, clock);
    HBox.setHgrow(identity, Priority.ALWAYS);
    icon(refresh, SidebarToolIcon.refresh(), "Sample now");
    refresh.setOnAction(e -> refreshStatus());
    live.setTooltip(new Tooltip("Sample editor memory every two seconds"));
    Button help = SidebarToolHelp.button(this, "Trashman", """
        Monitor plots used and committed editor heap over the last two minutes. Pin a sample before opening a preview to compare the change.

        Cleanup releases reloadable preview caches, requests one collection, or runs a sweep. The JVM decides when to collect. Each result records the observed change while the editor was running.

        Inspect lists the runtime counters and copies the current report. A class histogram ranks objects by their shallow size and can pause the editor while the JVM counts them.
        """);
    Node artwork = AeroIcon.of(AeroIcon.Kind.TRASHMAN, 30);
    artwork.visibleProperty().bind(widthProperty().greaterThanOrEqualTo(310));
    artwork.managedProperty().bind(artwork.visibleProperty());
    HBox row = line(8, artwork, identity, live, refresh, help);
    row.getStyleClass().add("tm-header");
    return row;
  }

  private VBox monitor() {
    pressure.getStyleClass().add("tm-pressure");
    pressure.setMaxWidth(Double.MAX_VALUE);
    Region usedKey = key("tm-used-key");
    Region committedKey = key("tm-committed-key");
    FlowPane legend = flow(line(5, usedKey, label("Used", "tm-muted")),
        line(5, committedKey, label("Committed", "tm-muted")));
    Label start = label("", "tm-axis");
    start.textProperty().bind(chart.windowLabel());
    HBox axis = line(0, start, spring(), label("Latest sample", "tm-axis"));
    axis.getStyleClass().add("tm-chart-axis");
    changes.setVisible(false);
    changes.managedProperty().bind(changes.visibleProperty());
    changes.getStyleClass().add("tm-metrics");
    FlowPane metrics = flow(metric("Sampled peak", peak), metric("Headroom", headroom),
        metric("GC since sample", gcActivity));
    metrics.getStyleClass().add("tm-metrics");
    pin.setOnAction(e -> {
      if (latest == null) return;
      baseline = latest;
      baselineTime = sampleTime;
      pin.setText("Replace baseline");
      renderComparison();
      status.setText("Pinned the sample from " + baselineTime + ".");
    });
    return body(new VBox(4, label("Heap used", "tm-heading"), heap, capacity, pressure),
        new VBox(6, label("Heap history", "tm-heading"), legend, chart, axis), metrics,
        section("Baseline comparison", comparison, changes, pin));
  }

  private VBox cleanup() {
    collect.setGraphic(SidebarToolIcon.refresh());
    trim.setGraphic(SidebarToolIcon.delete());
    sweep.setGraphic(SidebarToolIcon.sparkles());
    collect.setOnAction(e -> startCleanup(Operation.COLLECT));
    trim.setOnAction(e -> startCleanup(Operation.CACHES));
    sweep.setOnAction(e -> startCleanup(Operation.SWEEP));
    stop.setOnAction(e -> {
      stopRequested.set(true);
      stop.setDisable(true);
      status.setText("Stopping after the current request…");
    });
    configure(passes, "Number of sweep passes");
    configure(pause, "Pause between sweep passes in milliseconds");
    updateHistory();
    return body(section("Preview caches", label("Release reloadable images and renderer caches in open VNS and JES previews, then request collection.", "tm-muted"), trim),
        section("Garbage collection", label("Request one collection or repeat it with a pause between passes.", "tm-muted"),
            collect, flow(field("Passes", passes), field("Pause (ms)", pause)), finalize, flow(sweep, stop)),
        section("Cleanup history", historyRows));
  }

  private VBox inspector() {
    copy.setGraphic(SidebarToolIcon.copy());
    copy.setOnAction(e -> {
      if (latest == null) return;
      ClipboardContent content = new ClipboardContent();
      content.putString(report());
      boolean ok = Clipboard.getSystemClipboard().setContent(content);
      status.setText(ok ? "Copied memory report." : "Clipboard unavailable.");
    });
    histogram.setOnAction(e -> startHistogram());
    histogramOutput.setEditable(false);
    histogramOutput.setWrapText(false);
    histogramOutput.setPrefRowCount(10);
    histogramOutput.getStyleClass().add("tm-output");
    TitledPane raw = new TitledPane("Raw histogram", histogramOutput);
    raw.setExpanded(false);
    histogramResult.getChildren().addAll(label("Largest classes by shallow size", "tm-heading"), classRows, raw);
    histogramResult.setVisible(false);
    histogramResult.managedProperty().bind(histogramResult.visibleProperty());
    return body(section("JVM diagnostics", jdkInfo, flow(copy, histogram), histogramResult),
        section("Runtime", runtimeRows), section("Garbage collectors", collectorRows),
        section("Memory pools", poolRows), section("Buffer pools", bufferRows));
  }

  private void updateTimer() {
    if (!disposed && getScene() != null && live.isSelected()) {
      timer.playFromStart();
      refreshStatus();
    } else {
      timer.stop();
    }
  }

  public void refreshStatus() {
    if (disposed || busy || sampling) return;
    sampling = true;
    submit(Snapshot::capture, snapshot -> {
      sampling = false;
      accept(snapshot);
    }, error -> {
      sampling = false;
      status.setText("Sampling failed · " + message(error));
    });
  }

  private <T> void submit(Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
    Task<T> task = new Task<>() {
      @Override protected T call() throws Exception { return work.call(); }
    };
    task.setOnSucceeded(e -> { if (!disposed) success.accept(task.getValue()); });
    task.setOnFailed(e -> { if (!disposed) failure.accept(task.getException()); });
    worker.execute(task);
  }

  public void dispose() {
    disposed = true;
    stopRequested.set(true);
    timer.stop();
    worker.shutdownNow();
  }

  private void accept(Snapshot s) {
    Snapshot previous = latest;
    latest = s;
    sampleTime = LocalTime.now().format(CLOCK);
    if (previous == null && !busy) status.setText("Monitoring editor JVM · PID " + s.pid());
    clock.setText("Sampled " + sampleTime);
    heap.setText(formatBytes(s.heapUsed()));
    capacity.setText(s.heapMax() > 0 ? percentText(s.heapPressure()) + " of " + formatBytes(s.heapMax()) + " limit"
        : formatBytes(s.heapCommitted()) + " committed");
    pressure.setProgress(s.heapPressure());
    peakHeap = Math.max(peakHeap, s.heapUsed());
    peak.setText(formatBytes(peakHeap));
    headroom.setText(s.heapMax() > 0 ? formatBytes(s.heapFreeFromMax()) : "Unknown");
    if (previous != null) gcActivity.setText(gcDelta(previous, s));
    chart.add(s.uptimeMs(), s.heapUsed(), s.heapCommitted());
    renderComparison();
    updateControls();
    if (pages.getSelectionModel().getSelectedItem() == inspect) renderInspector(s);
  }

  private void renderComparison() {
    if (baseline == null) return;
    comparison.setText("Pinned at " + baselineTime);
    heapChange.setText(signed(latest.heapUsed() - baseline.heapUsed()));
    nonHeapChange.setText(signed(latest.nonHeapUsed() - baseline.nonHeapUsed()));
    bufferChange.setText(latest.bufferUsed() < 0 || baseline.bufferUsed() < 0 ? "Unknown"
        : signed(latest.bufferUsed() - baseline.bufferUsed()));
    changes.setVisible(true);
  }

  private void renderInspector(Snapshot s) {
    jdkInfo.setText(s.jvm() + " · PID " + s.pid() + "\n"
        + (s.jcmdAvailable() ? "A class histogram may pause the editor while the JVM counts objects."
                            : "Class histogram requires jcmd in the JDK or on PATH."));
    infoRow(runtimeRows, 0, "Non-heap", formatBytes(s.nonHeapUsed()), formatBytes(s.nonHeapCommitted()) + " committed");
    infoRow(runtimeRows, 1, "Loaded classes", Long.toString(s.loadedClasses()), s.unloadedClasses() + " unloaded");
    infoRow(runtimeRows, 2, "Threads", s.threadCount() + " live", s.daemonThreadCount() + " daemon · " + s.peakThreadCount() + " peak");
    infoRow(runtimeRows, 3, "Finalizer queue", Integer.toString(s.pendingFinalization()), "Objects awaiting finalization");
    int i = 0;
    for (var c : s.collectors()) infoRow(collectorRows, i++, c.name(), c.countText(), c.timeText() + " cumulative");
    trimRows(collectorRows, i);
    i = 0;
    for (var p : s.pools()) infoRow(poolRows, i++, p.name(), formatBytes(p.used()),
        p.type() + " · " + formatBytes(p.committed()) + " committed · " + formatBytes(p.peakUsed()) + " peak");
    trimRows(poolRows, i);
    i = 0;
    for (var b : s.buffers()) infoRow(bufferRows, i++, b.name(), formatBytes(b.used()),
        counter(b.count()) + " buffers · " + formatBytes(b.capacity()) + " capacity");
    trimRows(bufferRows, i);
  }

  private static void infoRow(VBox list, int index, String name, String value, String detail) {
    if (index == list.getChildren().size()) list.getChildren().add(new InfoRow());
    ((InfoRow) list.getChildren().get(index)).update(name, value, detail);
  }

  private static void trimRows(VBox list, int size) {
    list.getChildren().remove(size, list.getChildren().size());
  }

  private static final class InfoRow extends VBox {
    final Label name = label("", "tm-row-name");
    final Label value = label("", "tm-row-value");
    final Label detail = label("", "tm-muted");
    InfoRow() {
      setSpacing(2);
      getStyleClass().add("tm-row");
      getChildren().addAll(line(8, name, spring(), value), detail);
    }
    void update(String title, String amount, String note) {
      name.setText(title);
      value.setText(amount);
      detail.setText(note);
    }
  }

  private enum Operation {
    COLLECT("Run GC"), CACHES("Preview caches"), SWEEP("Sweep");
    final String title;
    Operation(String title) { this.title = title; }
  }

  private record Cleanup(String time, Operation operation, Snapshot before, Snapshot after, String detail) {
    String change() { return signed(after.heapUsed() - before.heapUsed()); }
    String summary() {
      return time + " · " + operation.title + " · " + formatBytes(before.heapUsed()) + " → "
          + formatBytes(after.heapUsed()) + " (" + change() + ") · " + detail
          + " · GC " + gcDelta(before, after);
    }
  }

  private void startCleanup(Operation action) {
    if (disposed || busy || latest == null) return;
    if (action == Operation.SWEEP) {
      passes.commitValue();
      pause.commitValue();
    }
    int count = action == Operation.SWEEP ? passes.getValue() : 1;
    int delay = pause.getValue();
    boolean finalization = action != Operation.CACHES && finalize.isSelected();
    busy = true;
    sweeping = action == Operation.SWEEP;
    stopRequested.set(false);
    updateControls();
    status.setText(action.title + " in progress…");
    submit(() -> {
      Snapshot before = Snapshot.capture();
      String detail;
      if (action == Operation.CACHES) {
        FutureTask<Integer> release = new FutureTask<>(() -> disposed ? 0 : trimCaches.getAsInt());
        Platform.runLater(release);
        int tabs = release.get();
        if (tabs > 0) requestCollection(false);
        detail = tabs == 0 ? "No open previews" : tabs + (tabs == 1 ? " preview cleared" : " previews cleared");
      } else {
        int completed = 0;
        while (completed < count && !stopRequested.get()) {
          if (action == Operation.SWEEP) {
            int pass = completed + 1;
            Platform.runLater(() -> {
              if (!disposed && sweeping && !stopRequested.get()) status.setText("Sweep · requesting pass " + pass + " of " + count);
            });
          }
          requestCollection(finalization);
          completed++;
          if (completed < count && !stopRequested.get()) Thread.sleep(delay);
        }
        detail = (completed < count ? "Stopped · " : "") + completed + " of " + count
            + (count == 1 ? " request" : " requests");
      }
      return new Cleanup(LocalTime.now().format(CLOCK), action, before, Snapshot.capture(), detail);
    }, result -> {
      busy = false;
      sweeping = false;
      history.addFirst(result);
      if (history.size() > HISTORY_LIMIT) history.removeLast();
      updateHistory();
      accept(result.after());
      status.setText(action.title + " · " + result.detail() + " · heap " + result.change());
    }, this::operationFailed);
  }

  @SuppressWarnings("removal")
  private static void requestCollection(boolean finalization) {
    if (finalization) System.runFinalization();
    ManagementFactory.getMemoryMXBean().gc();
  }

  private void updateControls() {
    boolean unavailable = busy || latest == null;
    for (Node control : List.of(collect, sweep, passes, pause, finalize)) control.setDisable(unavailable);
    trim.setDisable(unavailable || trimCaches == null);
    stop.setVisible(sweeping);
    stop.setManaged(sweeping);
    stop.setDisable(stopRequested.get());
    histogram.setDisable(unavailable || !latest.jcmdAvailable());
    pin.setDisable(latest == null);
    copy.setDisable(latest == null);
    refresh.setDisable(busy);
  }

  private void updateHistory() {
    historyRows.getChildren().clear();
    if (history.isEmpty()) historyRows.getChildren().add(label("Your next cleanup will appear here with its before and after measurements.", "tm-muted"));
    for (Cleanup entry : history) {
      InfoRow row = new InfoRow();
      row.update(entry.operation().title, entry.change(), entry.time() + " · " + entry.detail()
          + "\n" + formatBytes(entry.before().heapUsed()) + " → " + formatBytes(entry.after().heapUsed())
          + " · GC " + gcDelta(entry.before(), entry.after()));
      historyRows.getChildren().add(row);
    }
  }

  private void startHistogram() {
    if (disposed || busy || latest == null || !latest.jcmdAvailable()) return;
    Snapshot target = latest;
    busy = true;
    updateControls();
    status.setText("Counting objects in the editor JVM…");
    submit(() -> classHistogram(target), output -> {
      histogramOutput.setText("Class histogram (all objects) · " + LocalTime.now().format(CLOCK) + "\n" + output.output());
      histogramOutput.positionCaret(0);
      int row = 0;
      for (var entry : output.topClasses()) {
        infoRow(classRows, row++, entry.name(), formatBytes(entry.bytes()), entry.instances() + " instances");
      }
      trimRows(classRows, row);
      histogramResult.setVisible(true);
      busy = false;
      updateControls();
      status.setText("Class histogram captured at " + LocalTime.now().format(CLOCK) + ".");
      refreshStatus();
    }, this::operationFailed);
  }

  private record Histogram(String output, List<TrashmanTelemetry.ClassEntry> topClasses) {}

  private static Histogram classHistogram(Snapshot target) throws Exception {
    Process process = new ProcessBuilder(target.jcmdCommand(), target.pid(), "GC.class_histogram", "-all")
        .redirectErrorStream(true).start();
    // Drain the pipe even after the display limit so a large histogram cannot block jcmd.
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int limit = 64 * 1024;
    Thread reader = new Thread(() -> {
      try (var stream = process.getInputStream()) {
        byte[] chunk = new byte[4096];
        int n;
        while ((n = stream.read(chunk)) != -1) {
          int take = Math.min(n, limit + 1 - output.size());
          if (take > 0) output.write(chunk, 0, take);
        }
      } catch (IOException ignored) {
        // Closing a timed-out process closes its pipe.
      }
    }, "jvn-trashman-histogram");
    reader.setDaemon(true);
    reader.start();
    try {
      if (!process.waitFor(30, TimeUnit.SECONDS)) throw new IOException("jcmd timed out after 30 seconds");
      reader.join();
      String text = output.toString(StandardCharsets.UTF_8).strip();
      var topClasses = TrashmanTelemetry.largestClasses(text);
      if (process.exitValue() != 0 || topClasses.isEmpty()) {
        throw new IOException(text.lines().filter(l -> !l.isBlank() && !l.matches("[0-9]+:"))
            .findFirst().orElse("jcmd produced no class counts"));
      }
      if (output.size() > limit) text = text.substring(0, Math.max(0, text.lastIndexOf('\n'))) + "\n… output limited to 64 KiB";
      return new Histogram(text, topClasses);
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }

  private void operationFailed(Throwable error) {
    busy = false;
    sweeping = false;
    updateControls();
    status.setText("Action failed · " + message(error));
  }

  private String report() {
    Snapshot s = latest;
    StringBuilder out = new StringBuilder("Trashman · editor memory\n");
    out.append(sampleTime).append(" · PID ").append(s.pid()).append(" · ").append(s.jvm())
        .append("\nUptime: ").append(s.uptimeMs()).append(" ms\nHeap used: ").append(formatBytes(s.heapUsed()))
        .append(" · committed ").append(formatBytes(s.heapCommitted())).append(" · limit ").append(maxText(s.heapMax()))
        .append("\nSampled peak: ").append(formatBytes(peakHeap)).append("\nNon-heap: ").append(formatBytes(s.nonHeapUsed()))
        .append(" used · ").append(formatBytes(s.nonHeapCommitted())).append(" committed · limit ").append(maxText(s.nonHeapMax()))
        .append("\nGC: ").append(counter(s.gcCount())).append(" collections · ").append(counter(s.gcTimeMs())).append(" ms · ")
        .append(percentText(s.gcOverhead())).append(" of uptime\nClasses: ").append(s.loadedClasses()).append(" loaded · ")
        .append(s.totalLoadedClasses()).append(" total · ").append(s.unloadedClasses()).append(" unloaded\nThreads: ")
        .append(s.threadCount()).append(" live · ").append(s.daemonThreadCount()).append(" daemon · ")
        .append(s.peakThreadCount()).append(" peak\nFinalizer queue: ").append(s.pendingFinalization())
        .append("\nSample cost: ").append(s.captureDurationMs()).append(" ms\njcmd: ").append(s.jcmdDetail()).append('\n');
    out.append("Buffers: ").append(counter(s.bufferCount())).append(" buffers · ").append(formatBytes(s.bufferUsed()))
        .append(" used · ").append(formatBytes(s.bufferCapacity())).append(" capacity\n");
    if (baseline != null) out.append("\nBaseline comparison\n").append(comparison.getText())
        .append("\nHeap ").append(heapChange.getText()).append(" · non-heap ").append(nonHeapChange.getText())
        .append(" · buffers ").append(bufferChange.getText()).append('\n');
    out.append("\nCollectors\n");
    s.collectors().forEach(c -> out.append(c.name()).append(" · ").append(c.countText()).append(" · ").append(c.timeText()).append('\n'));
    out.append("\nMemory pools\n");
    s.pools().forEach(p -> out.append(p.name()).append(" · ").append(p.type()).append(" · ").append(p.detail()).append('\n'));
    out.append("\nBuffer pools\n");
    s.buffers().forEach(b -> out.append(b.name()).append(" · ").append(counter(b.count())).append(" buffers · ")
        .append(formatBytes(b.used())).append(" used · ").append(formatBytes(b.capacity())).append(" capacity\n"));
    out.append("\nCleanup history\n");
    history.forEach(entry -> out.append(entry.summary()).append('\n'));
    if (histogramResult.isVisible()) out.append("\n").append(histogramOutput.getText());
    return out.toString();
  }

  private static String counter(long value) { return value < 0 ? "unknown" : Long.toString(value); }

  private static String signed(long n) {
    return (n > 0 ? "+" : n < 0 ? "−" : "") + formatBytes(Math.abs(n));
  }

  private static String gcDelta(Snapshot a, Snapshot b) {
    String count = a.gcCount() < 0 || b.gcCount() < 0 ? "—" : Long.toString(b.gcCount() - a.gcCount());
    String time = a.gcTimeMs() < 0 || b.gcTimeMs() < 0 ? "—" : (b.gcTimeMs() - a.gcTimeMs()) + " ms";
    return count + " / " + time;
  }

  private static String message(Throwable error) {
    return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
  }

  private static Tab page(String title, VBox body) {
    ScrollPane scroll = new ScrollPane(body);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    return new Tab(title, scroll);
  }

  private static VBox body(Node... nodes) {
    VBox box = new VBox(16, nodes);
    box.setMinWidth(0);
    if (nodes.length > 0 && nodes[0].getStyleClass().contains("tm-section")) nodes[0].getStyleClass().add("tm-first");
    box.getStyleClass().add("tm-body");
    return box;
  }

  private static VBox section(String title, Node... content) {
    VBox box = new VBox(8, label(title, "tm-heading"));
    box.getChildren().addAll(content);
    box.getStyleClass().add("tm-section");
    return box;
  }

  private static VBox metric(String title, Label value) {
    VBox box = new VBox(4, label(title, "tm-muted"), value);
    box.setMinWidth(90);
    return box;
  }

  private static void configure(Spinner<Integer> spinner, String hint) {
    spinner.setPrefWidth(86);
    spinner.setAccessibleText(hint);
    var values = (IntegerSpinnerValueFactory) spinner.getValueFactory();
    spinner.setEditable(true);
    spinner.getEditor().setTextFormatter(new TextFormatter<>(change ->
        change.getControlNewText().matches("[0-9]{0,4}") ? change : null));
    values.setConverter(new javafx.util.StringConverter<>() {
      @Override public String toString(Integer value) { return value.toString(); }
      @Override public Integer fromString(String text) {
        if (text == null || text.isBlank()) return values.getValue();
        return Math.max(values.getMin(), Math.min(values.getMax(), Integer.parseInt(text)));
      }
    });
  }

  private static VBox field(String title, Node control) {
    return new VBox(3, label(title, "tm-muted"), control);
  }

  private static Label label(String text, String style) {
    Label label = new Label(text);
    label.setWrapText(true);
    label.getStyleClass().add(style);
    return label;
  }

  private static HBox line(double gap, Node... children) {
    HBox line = new HBox(gap, children);
    line.setAlignment(Pos.CENTER_LEFT);
    return line;
  }

  private static FlowPane flow(Node... children) {
    return new FlowPane(12, 6, children);
  }

  private static Region spring() {
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    return spacer;
  }

  private static Region key(String style) {
    Region key = new Region();
    key.getStyleClass().add(style);
    key.setMinSize(16, 2);
    key.setMaxSize(16, 2);
    return key;
  }

  private static void icon(Button button, Node icon, String hint) {
    button.setGraphic(icon);
    button.setAccessibleText(hint);
    button.setTooltip(new Tooltip(hint));
    button.getStyleClass().add("aero-icon-button");
  }
}
