package com.jvn.editor.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.jvn.editor.runtime.RuntimeGradleOptions;
import com.jvn.editor.runtime.RuntimeGradleOptionsStore;
import org.jspecify.annotations.Nullable;

import com.jvn.core.diagnostics.RunTelemetry;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

/**
 * A run console that filters verbose Gradle output and presents
 * friendly engine-state messages with color-coded output.
 */
public class RunConsoleView extends BorderPane {

    public enum EngineState { BUILDING, STARTING, RUNNING, STOPPED, FAILED }

    @FunctionalInterface
    public interface ProcessStarter {
        Process start(RuntimeGradleOptions options) throws Exception;
    }

    private final ListView<String> outputList = new ListView<>();
    private final DoubleProperty launchProgressValue = new SimpleDoubleProperty(0.0);
    private final Label workspaceValue = new Label();
    private final Label taskValue = new Label();
    private final Tab outputTab = new Tab("Output");
    private final TabPane tabs = new TabPane();
    private final Label launchActivityLabel = new Label();
    private final Label launchDetailLabel = new Label();
    private final Label stateLabel = new Label();
    private final Label elapsedLabel = new Label();
    private final ToggleButton showAllToggle =
        iconToggle(AeroIcon.buildProject(22), "Include full Gradle/build output");
    private final Button runBtn =
        iconButton(AeroIcon.runProject(22), "Run current build again");
    private final Button copyBtn =
        iconButton(PuppeteerAeroIcon.of(PuppeteerAeroIcon.Kind.COPY, 22), "Copy visible output to clipboard");
    private final Button clearBtn =
        iconButton(AeroIcon.of(AeroIcon.Kind.TRASHMAN, 22), "Clear output");
    private final Button stopBtn =
        iconButton(PuppeteerAeroIcon.of(PuppeteerAeroIcon.Kind.STOP, 22), "Stop current build");
    private final MenuButton launchOptionsButton = new MenuButton();

    // Enhanced UI components
    private final TextField searchField = new TextField();
    private final ComboBox<String> logLevelFilter = new ComboBox<>(
        FXCollections.observableArrayList("All", "Engine", "Errors", "Warnings"));
    private final ToggleButton autoScrollBtn =
        iconToggle(SidebarToolIcon.arrowDown(), "Auto-scroll to latest output");
    private final ToggleButton wordWrapBtn =
        iconToggle(AeroIcon.of(AeroIcon.Kind.VNS_WORD_WRAP, 22), "Wrap long output lines");
    private final Label lineCountLabel = new Label("0 lines");
    private final Label errorCountLabel = new Label("0 errors");
    private final Label warnCountLabel = new Label("0 warnings");
    private final PerfGraph perfGraph = new PerfGraph();
    private final Tooltip perfGraphTooltip = new Tooltip("CPU -- | JVM -- MB | FPS --");
    private final Label perfCpuValue = new Label("CPU --");
    private final Label perfJvmValue = new Label("JVM -- MB");
    private final Label perfFpsValue = new Label("FPS --");

    // Raw line buffer for search/filter replay
    private final List<String> rawLineBuffer = new ArrayList<>();

    private volatile EngineState engineState = EngineState.BUILDING;
    private @Nullable Process runningProcess;
    private @Nullable ProcessStarter processStarter;
    private final RuntimeGradleOptionsStore gradleOptionsStore;
    private RuntimeGradleOptions gradleOptions;
    private RuntimeGradleOptions activeGradleOptions;
    private long startTime = System.currentTimeMillis();
    private int lineCount = 0;
    private int errorCount = 0;
    private int warnCount = 0;
    private String currentSearchTerm = "";
    private Path inspectorSession;
    private final Label frameStats = new Label("Waiting for a frame from the game…");
    private final TableView<RunTelemetry.Cache> caches = new TableView<>();
    private final Button inspectAssets = new Button("Capture asset inventory", AeroIcon.of(AeroIcon.Kind.ASSETS, 18));
    private final Button reload = new Button("Hot reload", AeroIcon.of(AeroIcon.Kind.REFRESH, 20));
    private final Tab inspectTab = new Tab("Render inspector");
    private final Set<String> compiledModules = Collections.synchronizedSet(new LinkedHashSet<>());
    private volatile boolean sawGradleBootstrap = false;
    private volatile boolean compileMilestoneAnnounced = false;
    private volatile boolean launchMilestoneAnnounced = false;
    private volatile boolean runtimeMilestoneAnnounced = false;
    private volatile boolean processExited = false;
    private boolean stopRequested;
    private Integer lastExitCode;
    private String launchToolLabel = "Gradle wrapper";
    private String launchTaskLabel = "";
    private String launchWorkspaceLabel = "";

    // Patterns for Gradle noise lines we suppress by default
    private static final Pattern GRADLE_NOISE = Pattern.compile(
        "^(> Task |> Configure |BUILD SUCCESSFUL|BUILD FAILED|Deprecated Gradle|" +
        "\\d+ actionable|See the profiling|Starting a Gradle|To honour the JVM|" +
        "Starting process|Successfully started|> Connecting to|Generating |" +
        "Note: |Download |Execution optimizations|> Building|" +
        "\\s*$)" // blank lines
    );

    // Patterns for engine lifecycle messages we always surface
    private static final Pattern ENGINE_MSG = Pattern.compile(
        "^\\[(JVN|Engine|Scene|Audio|Script|VN|Menu|Runtime|Init|Asset|Error|WARN|INFO)\\]",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RUNTIME_LOG_LINE = Pattern.compile(
        "^\\d{2}:\\d{2}:\\d{2}[.,]\\d{3}\\s+\\|?-?(TRACE|DEBUG|INFO|WARN|ERROR)\\b",
        Pattern.CASE_INSENSITIVE
    );

    // Patterns for errors and warnings
    private static final Pattern ERROR_LINE = Pattern.compile(
        "(Exception|Error|FAILED|error:|fatal:|\\bat line \\d+)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WARN_LINE = Pattern.compile(
        "(Warning|WARN|deprecated|could not)",
        Pattern.CASE_INSENSITIVE
    );

    private static final String STATE_BUILDING_CLASS = "run-console-state-building";
    private static final String STATE_STARTING_CLASS = "run-console-state-starting";
    private static final String STATE_RUNNING_CLASS = "run-console-state-running";
    private static final String STATE_STOPPED_CLASS = "run-console-state-stopped";
    private static final String STATE_FAILED_CLASS = "run-console-state-failed";
    private static final String COUNTER_ERROR_ACTIVE_CLASS = "run-console-meta-alert";
    private static final String COUNTER_WARN_ACTIVE_CLASS = "run-console-meta-warn";

    private static final Color PERF_GRID_BG = Color.web("#111111");
    private static final Color PERF_GRID_LINE = Color.web("#2a2a2a");
    private static final Color PERF_CPU_COLOR = Color.web("#f27333");
    private static final Color PERF_JVM_COLOR = Color.web("#49a5ff");
    private static final Color PERF_FPS_COLOR = Color.web("#f4f4f4");
    private static final Pattern COMPILE_TASK = Pattern.compile("^> Task (:[^\\s]+):compileJava(?:\\s+.*)?$");

    public RunConsoleView(String title) {
        this(title, new RuntimeGradleOptionsStore());
    }

    RunConsoleView(String title, RuntimeGradleOptionsStore optionsStore) {
        gradleOptionsStore =
            optionsStore == null ? new RuntimeGradleOptionsStore() : optionsStore;
        gradleOptions = gradleOptionsStore.load();
        activeGradleOptions = gradleOptions;
        getStyleClass().add("run-console-root");
        launchActivityLabel.getStyleClass().add("run-console-launch-activity");
        launchDetailLabel.getStyleClass().add("run-console-launch-detail");
        launchDetailLabel.setWrapText(true);
        stateLabel.getStyleClass().add("run-console-state");
        elapsedLabel.getStyleClass().add("run-console-elapsed");

        Label heading = new Label("JVN Runtime".equals(title) ? "Run workspace" : title, AeroIcon.of(AeroIcon.Kind.RUN, 30));
        heading.getStyleClass().add("session-window-title");
        heading.setGraphicTextGap(10);
        HBox header = new HBox(16, heading, createToolBar());
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("session-header");
        HBox.setHgrow(heading, Priority.ALWAYS);
        heading.setMaxWidth(Double.MAX_VALUE);
        setTop(new VBox(createMenuBar(title), header));

        outputList.getStyleClass().add("run-console-output");
        outputList.setCellFactory(list -> new LogLineCell());
        outputList.setPlaceholder(new Label("No matching output"));
        outputList.setFocusTraversable(true);
        HBox filters = new HBox(6, searchField, logLevelFilter, copyBtn, clearBtn,
            showAllToggle, autoScrollBtn, wordWrapBtn);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.getStyleClass().add("session-log-tools");
        searchField.setMinWidth(70);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        VBox output = new VBox(filters, outputList);
        VBox.setVgrow(outputList, Priority.ALWAYS);
        outputTab.setContent(output);
        outputTab.setGraphic(AeroIcon.of(AeroIcon.Kind.SCRIPT_EDITOR, 18));
        inspectTab.setGraphic(AeroIcon.of(AeroIcon.Kind.INSPECTOR, 18));
        inspectTab.setContent(createInspector());
        inspectTab.setDisable(true);
        tabs.getTabs().addAll(outputTab, inspectTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        setCenter(tabs);
        setLeft(createSessionRail());
        setBottom(createStatusBar());

        perfGraphTooltip.setShowDelay(javafx.util.Duration.millis(120));
        Tooltip.install(perfGraph.getCanvas(), perfGraphTooltip);

        perfCpuValue.getStyleClass().addAll("run-console-perf-value", "run-console-perf-value-cpu");
        perfJvmValue.getStyleClass().addAll("run-console-perf-value", "run-console-perf-value-jvm");
        perfFpsValue.getStyleClass().addAll("run-console-perf-value", "run-console-perf-value-fps");

        setState(EngineState.BUILDING);
        refreshLaunchBanner();
    }

    private static Button iconButton(Node icon, String tooltipText) {
        Button btn = new Button();
        btn.getStyleClass().add("run-console-icon-btn");
        btn.setGraphic(icon);
        btn.setAccessibleText(tooltipText);
        btn.setTooltip(new Tooltip(tooltipText));
        btn.setFocusTraversable(true);
        return btn;
    }

    private static ToggleButton iconToggle(
        Node icon, String tooltipText) {
        ToggleButton button = new ToggleButton();
        button.setGraphic(icon);
        button.setAccessibleText(tooltipText);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setTooltip(new Tooltip(tooltipText));
        button.setFocusTraversable(true);
        return button;
    }

    public void setProcessStarter(@Nullable ProcessStarter processStarter) {
        this.processStarter = processStarter;
        runBtn.setDisable(processStarter == null || !(engineState == EngineState.STOPPED || engineState == EngineState.FAILED));
    }

    public void setLaunchContext(String toolLabel, String taskLabel, String workspaceLabel) {
        launchToolLabel = toolLabel == null || toolLabel.isBlank() ? "Gradle wrapper" : toolLabel.trim();
        launchTaskLabel = taskLabel == null ? "" : taskLabel.trim();
        launchWorkspaceLabel = workspaceLabel == null ? "" : workspaceLabel.trim();
        workspaceValue.setText(launchWorkspaceLabel);
        workspaceValue.setTooltip(new Tooltip(launchWorkspaceLabel));
        taskValue.setText(launchTaskLabel);
        refreshLaunchBanner();
    }

    public void startProcess(Process process) {
        attachProcess(process);
    }

    public boolean startConfiguredProcess() {
        if (processStarter == null) return false;
        try {
            attachProcess(processStarter.start(gradleOptions));
            return true;
        } catch (Exception ex) {
            closeInspector();
            setState(EngineState.FAILED);
            appendInfoMessage("Failed to start process: " + ex.getMessage());
            return false;
        }
    }

    private void attachProcess(Process process) {
        this.runningProcess = process;
        activeGradleOptions = gradleOptions;
        startTime = System.currentTimeMillis();
        clearOutput();
        compiledModules.clear();
        sawGradleBootstrap = false;
        compileMilestoneAnnounced = false;
        launchMilestoneAnnounced = false;
        runtimeMilestoneAnnounced = false;
        processExited = false;
        stopRequested = false;
        lastExitCode = null;
        launchProgressValue.set(0.04);
        setState(EngineState.BUILDING);
        appendInfoMessage("Preparing " + launchToolLabel + "...");
        refreshLaunchBanner();

        Thread reader = new Thread(() -> {
            Process active = process;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(active.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String next = line;
                    Platform.runLater(() -> { if (runningProcess == active) appendLine(next); });
                }
            } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }

            int exitCode = -1;
            try {
                exitCode = active.waitFor();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            int result = exitCode;
            Platform.runLater(() -> { if (runningProcess == active) onProcessExit(result); });
        }, "jvn-run-console-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public void setState(EngineState state) {

        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> setState(state));
            return;
        }
        this.engineState = state;
        stateLabel.setText(switch (state) {
            case BUILDING -> "Building";
            case STARTING -> "Starting";
            case RUNNING -> "Running";
            case STOPPED -> stopRequested ? "Stopped" : "Finished";
            case FAILED -> "Failed";
        });
        updateStateClass(state);
        boolean finished = state == EngineState.STOPPED || state == EngineState.FAILED;
        stopBtn.setDisable(finished || stopRequested);
        runBtn.setDisable(processStarter == null || !finished);
        refreshLaunchBanner();
    }

    private void updateStateClass(EngineState state) {
        setClassEnabled(stateLabel, STATE_BUILDING_CLASS, state == EngineState.BUILDING);
        setClassEnabled(stateLabel, STATE_STARTING_CLASS, state == EngineState.STARTING);
        setClassEnabled(stateLabel, STATE_RUNNING_CLASS, state == EngineState.RUNNING);
        setClassEnabled(stateLabel, STATE_STOPPED_CLASS, state == EngineState.STOPPED);
        setClassEnabled(stateLabel, STATE_FAILED_CLASS, state == EngineState.FAILED);
    }

    private static void setClassEnabled(Node node, String styleClass, boolean enabled) {
        if (node == null || styleClass == null || styleClass.isBlank()) return;
        var classes = node.getStyleClass();
        if (enabled) {
            if (!classes.contains(styleClass)) classes.add(styleClass);
        } else {
            classes.remove(styleClass);
        }
    }

    /** Append a raw line from the process. Handles filtering and coloring. */
    public void appendLine(String rawLine) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> appendLine(rawLine));
            return;
        }
        if (inspectorSession != null && RunTelemetry.INVENTORY_READY.equals(rawLine)) {
            Path session = inspectorSession;
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return Files.readString(session.resolve(RunTelemetry.INVENTORY_REPORT)); }
                catch (java.io.IOException ex) { return "Could not read cache inventory: " + ex.getMessage(); }
            }).thenAccept(report -> Platform.runLater(() -> {
                if (inspectorSession == session && getScene() != null)
                    EditorDialogs.showTextBlock(getScene().getWindow(), "VN asset cache", "Asset inventory",
                        report.isEmpty() ? "No project images or resident entries." : report, "Close");
            }));
            return;
        }
        if (inspectorSession != null && rawLine.startsWith(RunTelemetry.PREFIX)) {
            try { showSample(RunTelemetry.decode(rawLine)); }
            catch (IllegalArgumentException e) { appendInfoMessage("Invalid render sample"); }
            return;
        }
        observeBuildProgress(rawLine);
        lineCount++;
        rawLineBuffer.add(rawLine);

        // Detect engine state transitions from output
        if (rawLine.contains("> Task") && rawLine.contains(":run")) {
            setState(EngineState.STARTING);
        }
        if (isEngineOutputLine(rawLine) && engineState == EngineState.STARTING) {
            setState(EngineState.RUNNING);
        }
        if (rawLine.contains("BUILD FAILED")) {
            setState(EngineState.FAILED);
        }

        // Track error/warning counts
        String severity = classifyLine(rawLine);
        boolean isErr = severity.equals("run-console-line-error");
        boolean isWarn = severity.equals("run-console-line-warn");
        if (isErr) errorCount++;
        if (isWarn) warnCount++;

        if (passesFilter(rawLine)) {
            outputList.getItems().add(rawLine);
            if (autoScrollBtn.isSelected()) scrollToBottom();
        }
        elapsedLabel.setText(formatElapsed((System.currentTimeMillis() - startTime) / 1000));
        lineCountLabel.setText(lineCount + (lineCount == 1 ? " line" : " lines"));
        errorCountLabel.setText(errorCount + (errorCount == 1 ? " error" : " errors"));
        warnCountLabel.setText(warnCount + (warnCount == 1 ? " warning" : " warnings"));
        updateCounterBadgeStyles();
    }

    private void updateCounterBadgeStyles() {
        setClassEnabled(errorCountLabel, COUNTER_ERROR_ACTIVE_CLASS, errorCount > 0);
        setClassEnabled(warnCountLabel, COUNTER_WARN_ACTIVE_CLASS, warnCount > 0);
    }

    /** Check if a line passes the current filter settings. */
    private boolean passesFilter(String rawLine) {
        boolean isEngineMsg = isEngineOutputLine(rawLine);
        String severity = classifyLine(rawLine);
        boolean isError = severity.equals("run-console-line-error");
        boolean isWarning = severity.equals("run-console-line-warn");
        boolean isNoise = GRADLE_NOISE.matcher(rawLine).find();

        // Log level filter
        String level = logLevelFilter.getValue();
        if ("Engine".equals(level) && !isEngineMsg) return false;
        if ("Errors".equals(level) && !isError) return false;
        if ("Warnings".equals(level) && !isWarning) return false;

        // Show-all toggle (suppress noise unless enabled)
        if (!showAllToggle.isSelected() && isNoise && !isEngineMsg && !isError) return false;

        // Search filter
        if (!currentSearchTerm.isEmpty()) {
            if (!rawLine.toLowerCase().contains(currentSearchTerm.toLowerCase())) return false;
        }

        return true;
    }

    private void scrollToBottom() {
        int size = outputList.getItems().size();
        if (size > 0) outputList.scrollTo(size - 1);
    }

    static String classifyLine(String line) {
        if (line.startsWith("\u200B")) return "run-console-line-milestone";
        java.util.regex.Matcher rtm = RUNTIME_LOG_LINE.matcher(line);
        if (rtm.find()) {
            String level = rtm.group(1).toUpperCase();
            return switch (level) {
                case "ERROR" -> "run-console-line-error";
                case "WARN"  -> "run-console-line-warn";
                case "INFO"  -> "run-console-line-info";
                default       -> "run-console-line-engine";
            };
        }
        if (ERROR_LINE.matcher(line).find()) return "run-console-line-error";
        if (WARN_LINE.matcher(line).find()) return "run-console-line-warn";
        if (ENGINE_MSG.matcher(line).find()) return "run-console-line-engine";
        if (GRADLE_NOISE.matcher(line).find()) return "run-console-line-noise";
        return "run-console-line-normal";
    }

    private class LogLineCell extends ListCell<String> {
        LogLineCell() {
            wrapTextProperty().bind(wordWrapBtn.selectedProperty());
            // VirtualFlow measures wrapped cells against the visible log width.
            maxWidthProperty().bind(javafx.beans.binding.Bindings.when(wordWrapBtn.selectedProperty())
                .then(outputList.widthProperty().subtract(20)).otherwise(Double.MAX_VALUE));
        }

        private String currentClass = null;

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                if (currentClass != null) {
                    getStyleClass().remove(currentClass);
                    currentClass = null;
                }
                return;
            }
            setText(item);
            String newClass = classifyLine(item);
            if (!newClass.equals(currentClass)) {
                if (currentClass != null) getStyleClass().remove(currentClass);
                getStyleClass().add(newClass);
                currentClass = newClass;
            }
        }
    }

    /** Called when the process exits. */
    public void onProcessExit(int exitCode) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> onProcessExit(exitCode));
            return;
        }
        lastExitCode = exitCode;
        processExited = true;
        setState(stopRequested || exitCode == 0 ? EngineState.STOPPED : EngineState.FAILED);
        appendInfoMessage(stopRequested ? "Stopped by user." : "Process exited with code " + exitCode + ".");
        reload.setDisable(true);
        closeInspector();
        elapsedLabel.setText(formatElapsed((System.currentTimeMillis() - startTime) / 1000));
    }

    private void appendInfoMessage(String msg) {
        Runnable task = () -> {
            outputList.getItems().add("\u200B" + msg);
            scrollToBottom();
        };
        if (Platform.isFxApplicationThread()) task.run();
        else Platform.runLater(task);
    }

    private static Label railHeading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("session-group-heading");
        return label;
    }

    private ScrollPane createSessionRail() {
        ProgressBar progress = new ProgressBar();
        progress.progressProperty().bind(launchProgressValue);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.getStyleClass().add("session-progress");
        progress.visibleProperty().bind(stateLabel.textProperty().isEqualTo("Building")
            .or(stateLabel.textProperty().isEqualTo("Starting")));
        progress.managedProperty().bind(progress.visibleProperty());

        HBox state = new HBox(8, stateLabel, elapsedLabel);
        state.setAlignment(Pos.CENTER_LEFT);
        launchActivityLabel.setWrapText(true);
        launchDetailLabel.setWrapText(true);
        // Session outcome is already displayed next to the elapsed time.
        launchActivityLabel.visibleProperty().bind(progress.visibleProperty());
        launchActivityLabel.managedProperty().bind(progress.visibleProperty());
        VBox session = new VBox(8, railHeading("Session"), state, progress, launchActivityLabel, launchDetailLabel);

        workspaceValue.setWrapText(true);
        taskValue.setWrapText(true);
        taskValue.getStyleClass().add("session-task");
        VBox target = new VBox(6, railHeading("Launch target"), workspaceValue, taskValue, launchOptionsButton);
        perfCpuValue.getStyleClass().add("session-metric");
        perfJvmValue.getStyleClass().add("session-metric");
        perfFpsValue.getStyleClass().add("session-metric");
        VBox metrics = new VBox(7, railHeading("Runtime metrics"), perfGraph.getCanvas(),
            perfCpuValue, perfJvmValue, perfFpsValue);
        VBox rail = new VBox(16, session, new Separator(), target, new Separator(), metrics);
        rail.getStyleClass().add("session-rail");
        ScrollPane scroll = new ScrollPane(rail);
        scroll.getStyleClass().add("session-rail-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefWidth(208);
        scroll.setMinWidth(180);
        return scroll;
    }

    private void observeBuildProgress(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) return;
        boolean changed = false;

        if (!sawGradleBootstrap && rawLine.startsWith("Starting a Gradle")) {
            sawGradleBootstrap = true;
            changed = true;
        }

        java.util.regex.Matcher compileMatcher = COMPILE_TASK.matcher(rawLine);
        if (compileMatcher.matches()) {
            String moduleName = compileMatcher.group(1);
            if (moduleName != null && compiledModules.add(moduleName)) {
                changed = true;
            }
            if (!compileMilestoneAnnounced) {
                compileMilestoneAnnounced = true;
                appendInfoMessage("Compiling submodules...");
            }
        }

        if (!launchMilestoneAnnounced && rawLine.startsWith("> Task ") && rawLine.contains(":run")) {
            launchMilestoneAnnounced = true;
            if (!compiledModules.isEmpty()) {
                appendInfoMessage("Compiling submodules OK");
            }
            appendInfoMessage("Launching...");
            changed = true;
        }

        if (!runtimeMilestoneAnnounced && isEngineOutputLine(rawLine) && engineState == EngineState.STARTING) {
            runtimeMilestoneAnnounced = true;
            appendInfoMessage("Runtime online.");
            changed = true;
        }

        if (changed) refreshLaunchBanner();
    }

    private void refreshLaunchBanner() {
        Runnable task = () -> {
            launchActivityLabel.setText(currentActivityText());
            launchDetailLabel.setText(currentDetailText());
            updateLaunchProgress();
        };
        if (Platform.isFxApplicationThread()) task.run();
        else Platform.runLater(task);
    }

    private String currentActivityText() {
        return switch (engineState) {
            case BUILDING -> compileMilestoneAnnounced ? "Compiling submodules..." : ("Preparing " + launchToolLabel + "...");
            case STARTING -> "Launching...";
            case RUNNING -> "Running.";
            case STOPPED -> "Finished.";
            case FAILED -> "Build failed.";
        };
    }

    private String currentDetailText() {
        if (engineState == EngineState.STARTING && !compiledModules.isEmpty()) {
            return "Compiling submodules OK";
        }
        if (engineState == EngineState.RUNNING) {
            return "Receiving game output.";
        }
        if (engineState == EngineState.STOPPED) {
            return stopRequested ? "Stopped by user." : "Exit code 0";
        }
        if (engineState == EngineState.FAILED) {
            return lastExitCode == null ? "See the error in Output." : "Exit code " + lastExitCode;
        }
        if (!compiledModules.isEmpty()) {
            return compiledModules.size() + (compiledModules.size() == 1 ? " module ready." : " modules ready.");
        }
        if (sawGradleBootstrap) {
            return "Gradle daemon bootstrapped. Resolving build graph...";
        }
        return "Starting " + launchToolLabel + ".";
    }

    private void updateLaunchProgress() {
        launchProgressValue.set(computeLaunchProgress());
    }

    private double computeLaunchProgress() {
        if (engineState == EngineState.RUNNING || engineState == EngineState.STOPPED || processExited) {
            return 1.0;
        }
        if (engineState == EngineState.FAILED) {
            return Math.max(launchProgressValue.get(), 1.0);
        }
        if (engineState == EngineState.STARTING) {
            return runtimeMilestoneAnnounced ? 0.92 : 0.78;
        }

        double progress = 0.06;
        if (sawGradleBootstrap) progress = 0.16;
        if (compileMilestoneAnnounced) progress = Math.max(progress, 0.34);
        if (!compiledModules.isEmpty()) {
            progress = Math.max(progress, 0.34 + Math.min(0.34, compiledModules.size() * 0.055));
        }
        if (launchMilestoneAnnounced) progress = Math.max(progress, 0.74);
        return Math.min(0.76, Math.max(0.0, progress));
    }

    static boolean isEngineOutputLine(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) return false;
        return ENGINE_MSG.matcher(rawLine).find() || RUNTIME_LOG_LINE.matcher(rawLine).find();
    }

    private void copyTraceback() {
        String text = String.join("\n", outputList.getItems()).trim();
        if (text.isEmpty()) {
            appendInfoMessage("Nothing to copy.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        appendInfoMessage("Copied " + text.lines().count() + " lines to clipboard.");
    }

    private void clearOutput() {
        outputList.getItems().clear();
        rawLineBuffer.clear();
        lineCount = 0;
        errorCount = 0;
        warnCount = 0;
        lineCountLabel.setText("0 lines");
        errorCountLabel.setText("0 errors");
        warnCountLabel.setText("0 warnings");
        updateCounterBadgeStyles();
    }

    private void rebuildOutput() {
        outputList.getItems().clear();
        for (String line : rawLineBuffer) {
            if (passesFilter(line)) {
                outputList.getItems().add(line);
            }
        }
        if (autoScrollBtn.isSelected()) {
            scrollToBottom();
        }
    }

    private void stopProcess() {
        if (runningProcess != null && runningProcess.isAlive()) {
            stopRequested = true;
            appendInfoMessage("Stopping process...");
            stopBtn.setDisable(true);
            terminateProcessTree(runningProcess, activeGradleOptions.reuseDaemon());
        }
    }

    private void rerunProcess() {
        if (processStarter == null) return;
        if (runningProcess != null && runningProcess.isAlive()) {
            appendInfoMessage("Process is still running.");
            return;
        }
        try {
            appendInfoMessage("Re-running build...");
            Process process = processStarter.start(gradleOptions);
            attachProcess(process);
        } catch (Exception ex) {
            closeInspector();
            setState(EngineState.FAILED);
            appendInfoMessage("Failed to start process: " + ex.getMessage());
        }
    }

    // ─── Menu Bar ────────────────────────────────────────────────────────────

    private MenuBar createMenuBar(String title) {
        MenuBar bar = new MenuBar();
        bar.getStyleClass().add("run-console-menubar");
        bar.setUseSystemMenuBar(isMac());

        // — File menu —
        Menu fileMenu = new Menu("File");
        MenuItem miSaveLog = new MenuItem("Save Log...");
        miSaveLog.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        miSaveLog.setOnAction(e -> saveLogToFile());

        MenuItem miCopyAll = new MenuItem("Copy All");
        miCopyAll.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        miCopyAll.setOnAction(e -> copyTraceback());

        MenuItem miCopyErrors = new MenuItem("Copy Errors Only");
        miCopyErrors.setOnAction(e -> copyFilteredLines(true, false));

        MenuItem miCopyWarnings = new MenuItem("Copy Errors + Warnings");
        miCopyWarnings.setOnAction(e -> copyFilteredLines(true, true));

        MenuItem miClose = new MenuItem("Close");
        miClose.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
        miClose.setOnAction(e -> {
            javafx.stage.Stage stage = (javafx.stage.Stage) getScene().getWindow();
            if (stage != null) stage.close();
        });

        fileMenu.getItems().addAll(miSaveLog, new SeparatorMenuItem(),
            miCopyAll, miCopyErrors, miCopyWarnings, new SeparatorMenuItem(), miClose);

        // — Edit menu —
        Menu editMenu = new Menu("Edit");
        MenuItem miClear = new MenuItem("Clear Output");
        miClear.setAccelerator(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN));
        miClear.setOnAction(e -> clearOutput());

        MenuItem miFind = new MenuItem("Find...");
        miFind.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));
        miFind.setOnAction(e -> {
            tabs.getSelectionModel().select(outputTab);
            searchField.requestFocus();
            searchField.selectAll();
        });

        editMenu.getItems().addAll(miClear, new SeparatorMenuItem(), miFind);

        // — View menu —
        Menu viewMenu = new Menu("View");

        MenuItem miShowAll = new MenuItem("Toggle Build Output");
        miShowAll.setOnAction(e -> {
            showAllToggle.setSelected(!showAllToggle.isSelected());
            rebuildOutput();
        });

        MenuItem miAutoScroll = new MenuItem("Toggle Auto-Scroll");
        miAutoScroll.setOnAction(e -> autoScrollBtn.setSelected(!autoScrollBtn.isSelected()));

        MenuItem miWordWrap = new MenuItem("Toggle Word Wrap");
        miWordWrap.setOnAction(e -> {
            wordWrapBtn.setSelected(!wordWrapBtn.isSelected());
            if (wordWrapBtn.isSelected()) {
                outputList.setFixedCellSize(-1);
            } else {
                outputList.setFixedCellSize(20);
            }
            outputList.refresh();
        });

        MenuItem miScrollTop = new MenuItem("Scroll to Top");
        miScrollTop.setAccelerator(new KeyCodeCombination(KeyCode.HOME, KeyCombination.SHORTCUT_DOWN));
        miScrollTop.setOnAction(e -> { if (!outputList.getItems().isEmpty()) outputList.scrollTo(0); });

        MenuItem miScrollBottom = new MenuItem("Scroll to Bottom");
        miScrollBottom.setAccelerator(new KeyCodeCombination(KeyCode.END, KeyCombination.SHORTCUT_DOWN));
        miScrollBottom.setOnAction(e -> scrollToBottom());

        viewMenu.getItems().addAll(miShowAll, miAutoScroll, miWordWrap,
            new SeparatorMenuItem(), miScrollTop, miScrollBottom);

        // — Run menu —
        Menu runMenu = new Menu("Run");
        MenuItem miRerun = new MenuItem("Re-run");
        miRerun.setAccelerator(new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN));
        miRerun.setOnAction(e -> rerunProcess());

        MenuItem miStop = new MenuItem("Stop");
        miStop.setAccelerator(new KeyCodeCombination(KeyCode.PERIOD, KeyCombination.SHORTCUT_DOWN));
        miStop.setOnAction(e -> stopProcess());

        MenuItem miLaunchOptions = new MenuItem("Gradle Launch Options");
        miLaunchOptions.setOnAction(e -> Platform.runLater(launchOptionsButton::show));

        runMenu.getItems().addAll(
            miRerun, miStop, new SeparatorMenuItem(), miLaunchOptions);

        // — Help menu —
        Menu helpMenu = new Menu("Help");
        MenuItem miShortcuts = new MenuItem("Keyboard Shortcuts");
        miShortcuts.setOnAction(e -> {
            EditorDialogs.showTextBlock(
                getScene() != null ? getScene().getWindow() : null,
                "Console Shortcuts",
                "Run Console Keyboard Shortcuts",
                "Cmd+S ........... Save log to file\n" +
                "Cmd+Shift+C ..... Copy all output\n" +
                "Cmd+K ........... Clear output\n" +
                "Cmd+F ........... Focus search field\n" +
                "Cmd+R ........... Re-run build\n" +
                "Cmd+. ........... Stop process\n" +
                "Cmd+W ........... Close window\n" +
                "Cmd+Home ........ Scroll to top\n" +
                "Cmd+End ......... Scroll to bottom\n",
                "Close");
        });

        MenuItem miAbout = new MenuItem("About " + title);
        miAbout.setOnAction(e -> {
            EditorDialogs.showTextBlock(
                getScene() != null ? getScene().getWindow() : null,
                "About",
                title,
                "JVN Runtime Console\nLine buffer: " + rawLineBuffer.size()
                    + " lines\nErrors: " + errorCount + "  Warnings: " + warnCount,
                "Close");
        });

        helpMenu.getItems().addAll(miShortcuts, new SeparatorMenuItem(), miAbout);

        bar.getMenus().addAll(fileMenu, editMenu, viewMenu, runMenu, helpMenu);
        return bar;
    }

    // ─── Tool Bar ───────────────────────────────────────────────────────────

    private FlowPane createToolBar() {
        FlowPane bar = new FlowPane(6, 6);
        bar.getStyleClass().add("session-actions");
        bar.setPrefWrapLength(340);
        bar.setAlignment(Pos.CENTER_LEFT);

        configureLaunchOptionsButton();

        runBtn.setOnAction(e -> rerunProcess());
        stopBtn.setOnAction(e -> stopProcess());
        copyBtn.setOnAction(e -> copyTraceback());
        clearBtn.setOnAction(e -> clearOutput());

        showAllToggle.getStyleClass().add("run-console-toggle");
        showAllToggle.setSelected(false);
        showAllToggle.setOnAction(e -> rebuildOutput());
        showAllToggle.setFocusTraversable(false);
        showAllToggle.setTooltip(new Tooltip("Include full Gradle/build chatter"));

        autoScrollBtn.setSelected(true);
        autoScrollBtn.getStyleClass().add("run-console-toggle");
        autoScrollBtn.setTooltip(new Tooltip("Auto-scroll to latest output"));
        autoScrollBtn.setFocusTraversable(false);

        wordWrapBtn.setSelected(true);
        wordWrapBtn.getStyleClass().add("run-console-toggle");
        wordWrapBtn.setTooltip(new Tooltip("Toggle word wrapping"));
        wordWrapBtn.setFocusTraversable(false);
        wordWrapBtn.setOnAction(e -> {
            if (wordWrapBtn.isSelected()) {
                outputList.setFixedCellSize(-1);
            } else {
                outputList.setFixedCellSize(20);
            }
            outputList.refresh();
        });

        // Search field
        searchField.setPromptText("Search output...");
        searchField.setPrefWidth(240);
        searchField.getStyleClass().add("run-console-search-field");
        searchField.setTooltip(new Tooltip("Filter output by text (Cmd+F)"));
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            currentSearchTerm = newVal != null ? newVal.trim() : "";
            rebuildOutput();
        });

        // Log level filter
        logLevelFilter.setValue("All");
        logLevelFilter.setPrefWidth(132);
        logLevelFilter.getStyleClass().add("run-console-filter-combo");
        logLevelFilter.setTooltip(new Tooltip("Filter by log level"));
        logLevelFilter.setOnAction(e -> rebuildOutput());


        reload.setDisable(true);
        reload.setTooltip(new Tooltip("Reload scripts, images, layouts and menus from disk"));
        reload.setOnAction(e -> {
            if (inspectorSession == null) return;
            try {
                Files.writeString(inspectorSession.resolve(RunTelemetry.RELOAD), "");
                appendInfoMessage("Requested hot reload from the game.");
            } catch (java.io.IOException ex) { appendInfoMessage("Reload failed: " + ex.getMessage()); }
        });

        runBtn.setText("Run again");
        stopBtn.setText("Stop");
        for (Button button : List.of(runBtn, stopBtn, reload)) {
            button.getStyleClass().remove("run-console-icon-btn");
            button.getStyleClass().add("editor-dialog-button");
            button.setContentDisplay(ContentDisplay.LEFT);
        }
        bar.getChildren().addAll(runBtn, stopBtn, reload);
        return bar;
    }

    private VBox createInspector() {
        frameStats.setWrapText(true);
        caches.getStyleClass().add("layout-studio-table");
        addCacheColumn("VN cache", c -> c.name(), 140);
        addCacheColumn("Entries", c -> Integer.toString(c.entries()), 60);
        addCacheColumn("MiB", c -> String.format(Locale.ROOT, "%.2f", bytesToMb(c.bytes())), 65);
        addCacheColumn("Hits", c -> Long.toString(c.hits()), 70);
        addCacheColumn("Misses", c -> Long.toString(c.misses()), 65);
        addCacheColumn("Most recent resident", c -> c.recentResident(), 215);
        addCacheColumn("Last lookup miss", c -> c.lastMiss(), 215);
        caches.setPlaceholder(new Label("Waiting for renderer cache data"));
        inspectAssets.disableProperty().bind(reload.disableProperty());
        inspectAssets.setTooltip(new Tooltip("List resident VN image keys and project images outside the source caches"));
        inspectAssets.setOnAction(e -> {
            if (inspectorSession == null) return;
            try { Files.writeString(inspectorSession.resolve(RunTelemetry.INVENTORY_REQUEST), ""); }
            catch (java.io.IOException ex) { appendInfoMessage("Cache inspection failed: " + ex.getMessage()); }
        });
        VBox inspector = new VBox(10, railHeading("Frame statistics"), frameStats, new Separator(), railHeading("Image caches"), inspectAssets, caches);
        inspector.setPadding(new Insets(12));
        VBox.setVgrow(caches, Priority.ALWAYS);
        return inspector;
    }

    private void addCacheColumn(String title, java.util.function.Function<RunTelemetry.Cache, String> value,
                                int width) {
        TableColumn<RunTelemetry.Cache, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        column.setPrefWidth(width);
        caches.getColumns().add(column);
    }

    public Path prepareInspector(String task) throws java.io.IOException {
        closeInspector();
        if (!":runtime:run".equals(task)) return null;
        inspectorSession = Files.createTempDirectory("jvn-run-");
        inspectTab.setDisable(false);
        reload.setDisable(true);
        frameStats.setText("Waiting for a frame from the game…");
        caches.getItems().clear();
        perfGraph.index = 0;
        perfGraph.filled = false;
        perfGraph.redraw();
        perfCpuValue.setText("CPU —");
        perfJvmValue.setText("Heap —");
        perfFpsValue.setText("FPS —");
        return inspectorSession;
    }

    private void closeInspector() {
        if (inspectorSession == null) return;
        try {
            Files.deleteIfExists(inspectorSession.resolve(RunTelemetry.INVENTORY_REQUEST));
            Files.deleteIfExists(inspectorSession.resolve(RunTelemetry.INVENTORY_REPORT));
            Files.deleteIfExists(inspectorSession.resolve(RunTelemetry.RELOAD));
            Files.deleteIfExists(inspectorSession);
        } catch (java.io.IOException ex) { inspectorSession.toFile().deleteOnExit(); }
        inspectorSession = null;
    }

    private void showSample(RunTelemetry s) {
        if (processExited) return;
        reload.setDisable(false);
        elapsedLabel.setText(formatElapsed((System.currentTimeMillis() - startTime) / 1000));
        double heap = bytesToMb(s.heapBytes());
        if (engineState == EngineState.STARTING || engineState == EngineState.BUILDING) setState(EngineState.RUNNING);
        perfCpuValue.setText(s.cpuPercent() < 0 ? "CPU —" : String.format(Locale.ROOT, "CPU %.0f%%", s.cpuPercent()));
        perfJvmValue.setText(String.format(Locale.ROOT, "Heap %.0f MiB", heap));
        perfFpsValue.setText(String.format(Locale.ROOT, "%.1f FPS", s.fps()));
        perfGraphTooltip.setText("Game process • last 120 samples • CPU, heap and frame rate");
        perfGraph.pushSample(s.cpuPercent(), heap, s.fps(), heap);
        String draws = s.layerDraws() < 0 ? "" : String.format(Locale.ROOT,
            "   •   VN draws %d character / %d other   •   %d relights", s.layerDraws(), s.otherDraws(), s.relights());
        frameStats.setText(String.format(Locale.ROOT, "%s   •   %d × %d   •   %.2f ms mean render submission time%s",
            s.scene(), s.width(), s.height(), s.renderMs(), draws));
        caches.getItems().setAll(s.caches());
    }

    private void configureLaunchOptionsButton() {
        launchOptionsButton.getStyleClass().add("session-profile");
        launchOptionsButton.setGraphic(AeroIcon.of(AeroIcon.Kind.SETTINGS, 22));
        launchOptionsButton.setContentDisplay(ContentDisplay.LEFT);
        launchOptionsButton.setFocusTraversable(false);

        CheckMenuItem reuseDaemonItem = new CheckMenuItem("Reuse warm Gradle daemon");
        CheckMenuItem buildCacheItem = new CheckMenuItem("Reuse build cache");
        CheckMenuItem configurationCacheItem = new CheckMenuItem("Reuse configuration cache");
        CheckMenuItem parallelItem = new CheckMenuItem("Build projects in parallel");
        CheckMenuItem sharedDependenciesItem = new CheckMenuItem("Share dependency cache across projects");

        reuseDaemonItem.setSelected(gradleOptions.reuseDaemon());
        buildCacheItem.setSelected(gradleOptions.buildCache());
        configurationCacheItem.setSelected(gradleOptions.configurationCache());
        parallelItem.setSelected(gradleOptions.parallelExecution());
        sharedDependenciesItem.setSelected(gradleOptions.sharedDependencyCache());

        reuseDaemonItem.setOnAction(
            e -> updateGradleOptions(gradleOptions.withReuseDaemon(reuseDaemonItem.isSelected())));
        buildCacheItem.setOnAction(
            e -> updateGradleOptions(gradleOptions.withBuildCache(buildCacheItem.isSelected())));
        configurationCacheItem.setOnAction(
            e -> updateGradleOptions(
                gradleOptions.withConfigurationCache(configurationCacheItem.isSelected())));
        parallelItem.setOnAction(
            e -> updateGradleOptions(
                gradleOptions.withParallelExecution(parallelItem.isSelected())));
        sharedDependenciesItem.setOnAction(
            e -> updateGradleOptions(
                gradleOptions.withSharedDependencyCache(sharedDependenciesItem.isSelected())));

        Menu workersMenu = new Menu("Maximum workers");
        ToggleGroup workersGroup = new ToggleGroup();
        RadioMenuItem automaticWorkers = workerItem("Automatic", 0, workersGroup);
        RadioMenuItem twoWorkers = workerItem("2 (recommended)", 2, workersGroup);
        RadioMenuItem fourWorkers = workerItem("4", 4, workersGroup);
        RadioMenuItem eightWorkers = workerItem("8", 8, workersGroup);
        workersMenu.getItems().addAll(
            automaticWorkers, twoWorkers, fourWorkers, eightWorkers);
        selectWorkerItem(
            gradleOptions.maxWorkers(),
            automaticWorkers,
            twoWorkers,
            fourWorkers,
            eightWorkers);

        MenuItem fastPreset = new MenuItem("Use Fast Launch preset");
        MenuItem compatibilityPreset = new MenuItem("Use Compatibility preset");
        fastPreset.setOnAction(e -> {
            RuntimeGradleOptions options = RuntimeGradleOptions.fastDefaults();
            syncLaunchOptionItems(
                options,
                reuseDaemonItem,
                buildCacheItem,
                configurationCacheItem,
                parallelItem,
                sharedDependenciesItem,
                automaticWorkers,
                twoWorkers,
                fourWorkers,
                eightWorkers);
            updateGradleOptions(options);
        });
        compatibilityPreset.setOnAction(e -> {
            RuntimeGradleOptions options = RuntimeGradleOptions.compatibilityDefaults();
            syncLaunchOptionItems(
                options,
                reuseDaemonItem,
                buildCacheItem,
                configurationCacheItem,
                parallelItem,
                sharedDependenciesItem,
                automaticWorkers,
                twoWorkers,
                fourWorkers,
                eightWorkers);
            updateGradleOptions(options);
        });

        launchOptionsButton.getItems().setAll(
            reuseDaemonItem,
            buildCacheItem,
            configurationCacheItem,
            parallelItem,
            sharedDependenciesItem,
            new SeparatorMenuItem(),
            workersMenu,
            new SeparatorMenuItem(),
            fastPreset,
            compatibilityPreset);
        refreshLaunchOptionsButton();
    }

    private RadioMenuItem workerItem(String label, int workers, ToggleGroup group) {
        RadioMenuItem item = new RadioMenuItem(label);
        item.setToggleGroup(group);
        item.setOnAction(e -> {
            if (item.isSelected()) updateGradleOptions(gradleOptions.withMaxWorkers(workers));
        });
        return item;
    }

    private static void selectWorkerItem(
        int workers, RadioMenuItem automatic, RadioMenuItem two, RadioMenuItem four, RadioMenuItem eight) {
        if (workers == 2) two.setSelected(true);
        else if (workers == 4) four.setSelected(true);
        else if (workers == 8) eight.setSelected(true);
        else automatic.setSelected(true);
    }

    private static void syncLaunchOptionItems(
        RuntimeGradleOptions options,
        CheckMenuItem reuseDaemon,
        CheckMenuItem buildCache,
        CheckMenuItem configurationCache,
        CheckMenuItem parallel,
        CheckMenuItem sharedDependencies,
        RadioMenuItem automaticWorkers,
        RadioMenuItem twoWorkers,
        RadioMenuItem fourWorkers,
        RadioMenuItem eightWorkers) {
        reuseDaemon.setSelected(options.reuseDaemon());
        buildCache.setSelected(options.buildCache());
        configurationCache.setSelected(options.configurationCache());
        parallel.setSelected(options.parallelExecution());
        sharedDependencies.setSelected(options.sharedDependencyCache());
        selectWorkerItem(
            options.maxWorkers(),
            automaticWorkers,
            twoWorkers,
            fourWorkers,
            eightWorkers);
    }

    private void updateGradleOptions(RuntimeGradleOptions options) {
        if (options == null || options.equals(gradleOptions)) return;
        gradleOptions = options;
        try {
            gradleOptionsStore.save(options);
        } catch (java.io.IOException ex) {
            appendInfoMessage("Could not save Gradle launch options: " + ex.getMessage());
        }
        refreshLaunchOptionsButton();
    }

    private void refreshLaunchOptionsButton() {
        launchOptionsButton.setText(gradleOptions.shortLabel());
        launchOptionsButton.setTooltip(new Tooltip(
            "Gradle launch options for the next run\n" + gradleOptions.summary()));
    }

    // ─── Status Bar ─────────────────────────────────────────────────────────

    private HBox createStatusBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(3, 10, 3, 10));
        bar.getStyleClass().add("session-status-bar");

        lineCountLabel.getStyleClass().add("run-console-meta");
        errorCountLabel.getStyleClass().add("run-console-meta");
        warnCountLabel.getStyleClass().add("run-console-meta");
        bar.getChildren().addAll(lineCountLabel, warnCountLabel, errorCountLabel);
        updateCounterBadgeStyles();
        return bar;
    }

    // ─── File Operations ────────────────────────────────────────────────────

    private void saveLogToFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Log");
        chooser.setInitialFileName("jvn-runtime.log");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Log files", "*.log", "*.txt"),
            new FileChooser.ExtensionFilter("All files", "*.*"));
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(file)) {
            for (String line : rawLineBuffer) {
                pw.println(line);
            }
            appendInfoMessage("Saved " + rawLineBuffer.size() + " lines to " + file.getName());
        } catch (Exception ex) {
            appendInfoMessage("Failed to save log: " + ex.getMessage());
        }
    }

    private void copyFilteredLines(boolean includeErrors, boolean includeWarnings) {
        StringBuilder sb = new StringBuilder();
        for (String line : rawLineBuffer) {
            String severity = classifyLine(line);
            boolean isErr = severity.equals("run-console-line-error");
            boolean isWarn = severity.equals("run-console-line-warn");
            if (includeErrors && isErr) sb.append(line).append("\n");
            if (includeWarnings && isWarn) sb.append(line).append("\n");
        }
        String text = sb.toString().trim();
        if (text.isEmpty()) {
            appendInfoMessage("No matching lines to copy.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        appendInfoMessage("Copied " + text.lines().count() + " lines to clipboard.");
    }

    private static String formatElapsed(long seconds) {
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    public void dispose() {
        closeInspector();
        if (runningProcess != null && runningProcess.isAlive()) {
            terminateProcessTree(runningProcess, activeGradleOptions.reuseDaemon());
        }
    }

    private static void terminateProcessTree(Process process, boolean preserveReusableDaemon) {
        if (process == null) return;
        ProcessHandle handle = process.toHandle();
        List<ProcessHandle> descendants = descendantsOf(handle);
        handle.destroy();

        Thread cleanup = new Thread(() -> {
            try {
                process.waitFor(900, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            descendants.stream()
                .filter(ProcessHandle::isAlive)
                .filter(child -> !preserveReusableDaemon || !isGradleDaemon(child))
                .sorted(java.util.Comparator.comparingLong(ProcessHandle::pid).reversed())
                .forEach(ProcessHandle::destroyForcibly);
            if (handle.isAlive()) handle.destroyForcibly();
        }, "jvn-runtime-process-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    private static List<ProcessHandle> descendantsOf(ProcessHandle process) {
        try (var children = process.descendants()) {
            return children.toList();
        } catch (RuntimeException unavailable) {
            // Some operating-system sandboxes permit stopping our child but deny process enumeration.
            return List.of();
        }
    }

    private static boolean isGradleDaemon(ProcessHandle process) {
        if (process == null) return false;
        String commandLine = process.info().commandLine().orElse("");
        return commandLine.contains("org.gradle.launcher.daemon.bootstrap.GradleDaemon");
    }

    private static final class PerfGraph {
        private final Canvas canvas = new Canvas(154, 34);
        private final double[] cpu = new double[120];
        private final double[] jvm = new double[120];
        private final double[] fps = new double[120];
        private final double[] jvmCeiling = new double[120];
        private int index = 0;
        private boolean filled = false;

        private PerfGraph() {
            canvas.setWidth(154);
            canvas.setHeight(34);
            redraw();
        }

        private Canvas getCanvas() {
            return canvas;
        }

        private void pushSample(double cpuPercent, double jvmMb, double fpsValue, double jvmCeilingMb) {
            int slot = index % cpu.length;
            cpu[slot] = sanitizeMetric(cpuPercent);
            jvm[slot] = sanitizeMetric(jvmMb);
            fps[slot] = sanitizeMetric(fpsValue);
            jvmCeiling[slot] = sanitizeMetric(jvmCeilingMb);
            index++;
            if (index >= cpu.length) filled = true;
            redraw();
        }

        private void redraw() {
            GraphicsContext g = canvas.getGraphicsContext2D();
            double w = canvas.getWidth();
            double h = canvas.getHeight();
            if (!Double.isFinite(w) || !Double.isFinite(h) || w <= 0 || h <= 0) return;

            g.setFill(PERF_GRID_BG);
            g.fillRect(0, 0, w, h);

            g.setStroke(PERF_GRID_LINE);
            g.setLineWidth(1.0);
            for (int row = 1; row < 4; row++) {
                double y = h * row / 4.0;
                g.strokeLine(0, y, w, y);
            }
            double stepX = w / 4.0;
            for (double x = stepX; x < w; x += stepX) {
                g.strokeLine(x, 0, x, h);
            }

            int samples = filled ? cpu.length : Math.min(index, cpu.length);
            if (samples <= 1) return;
            double scaleX = w / (cpu.length - 1.0);
            double cpuCeiling = Math.max(6.0, maxRecent(cpu, samples) * 1.35);
            double jvmMax = Math.max(96.0, maxRecent(jvm, samples) * 1.14);
            double jvmCeilingMax = Math.max(jvmMax, maxRecent(jvmCeiling, samples));
            double graphJvmCeiling = Math.min(jvmCeilingMax, Math.max(jvmMax, jvmMax * 1.45));
            double fpsCeiling = Math.max(60.0, maxRecent(fps, samples) * 1.08);

            g.setFill(PERF_JVM_COLOR.deriveColor(0, 1, 1, 0.22));
            g.beginPath();
            for (int i = 0; i < samples; i++) {
                int si = (index - samples + i + cpu.length) % cpu.length;
                double x = i * scaleX;
                double y = graphY(jvm[si], graphJvmCeiling, h);
                if (i == 0) g.moveTo(x, h);
                g.lineTo(x, y);
            }
            g.lineTo((samples - 1) * scaleX, h);
            g.closePath();
            g.fill();

            g.setStroke(PERF_JVM_COLOR.deriveColor(0, 1, 1, 0.92));
            g.setLineWidth(1.25);
            g.beginPath();
            for (int i = 0; i < samples; i++) {
                int si = (index - samples + i + cpu.length) % cpu.length;
                double x = i * scaleX;
                double y = graphY(jvm[si], graphJvmCeiling, h);
                if (i == 0) g.moveTo(x, y);
                else g.lineTo(x, y);
            }
            g.stroke();

            g.setStroke(PERF_CPU_COLOR.deriveColor(0, 1, 1, 0.92));
            g.setLineWidth(1.5);
            g.beginPath();
            for (int i = 0; i < samples; i++) {
                int si = (index - samples + i + cpu.length) % cpu.length;
                double x = i * scaleX;
                double y = graphY(cpu[si], cpuCeiling, h);
                if (i == 0) g.moveTo(x, y);
                else g.lineTo(x, y);
            }
            g.stroke();

            g.setStroke(PERF_FPS_COLOR.deriveColor(0, 1, 1, 0.9));
            g.setLineWidth(1.5);
            g.beginPath();
            for (int i = 0; i < samples; i++) {
                int si = (index - samples + i + cpu.length) % cpu.length;
                double x = i * scaleX;
                double y = graphY(fps[si], fpsCeiling, h);
                if (i == 0) g.moveTo(x, y);
                else g.lineTo(x, y);
            }
            g.stroke();
        }

        private static double sanitizeMetric(double value) {
            return Double.isFinite(value) && value > 0.0 ? value : 0.0;
        }

        private double maxRecent(double[] values, int samples) {
            double max = 0.0;
            for (int i = 0; i < samples; i++) {
                int si = (index - samples + i + values.length) % values.length;
                max = Math.max(max, sanitizeMetric(values[si]));
            }
            return max;
        }

        private double graphY(double value, double ceiling, double height) {
            double normalized = ceiling <= 0.0 ? 0.0 : Math.min(1.0, sanitizeMetric(value) / ceiling);
            double inset = 2.0;
            return inset + ((height - inset * 2.0) * (1.0 - normalized));
        }
    }

    private static double bytesToMb(long bytes) {
        return bytes < 0L ? -1.0 : bytes / (1024.0 * 1024.0);
    }



    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }
}
