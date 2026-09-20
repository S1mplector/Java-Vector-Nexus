package com.jvn.editor;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.tools.ToolProvider;

import org.jspecify.annotations.Nullable;

import com.jvn.core.project.StoryMapPaths;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiStyleSpec;
import com.jvn.editor.commands.CommandStack;
import com.jvn.editor.runtime.GradleRuntimeLauncher;
import com.jvn.editor.ui.AeroIcon;
import com.jvn.editor.ui.AssetBrowserView;
import com.jvn.editor.ui.CssIcon;
import com.jvn.editor.ui.CompositionGuideOverlay;
import com.jvn.editor.ui.DeveloperLogPanel;
import com.jvn.editor.ui.DeveloperDiagnosticsExporter;
import com.jvn.editor.ui.DeveloperToolsMenu;
import com.jvn.editor.ui.DslPropertyDiagnostics;
import com.jvn.editor.ui.EditorDialogs;
import com.jvn.editor.ui.EditorPanelPlacement;
import com.jvn.editor.ui.EditorPathExplorer;
import com.jvn.editor.ui.EditorPreferences;
import com.jvn.editor.ui.EditorPreferencesStore;
import com.jvn.editor.ui.EditorSettingsView;
import com.jvn.editor.ui.EditorSidebarPanel;
import com.jvn.editor.ui.EditorTheme;
import com.jvn.editor.ui.EditorWorkspaceHubView;
import com.jvn.editor.ui.FileEditorTab;
import com.jvn.editor.ui.GameBuildPublisherView;
import com.jvn.editor.ui.ImageAttributesToolView;
import com.jvn.editor.ui.ImageTintToolView;
import com.jvn.editor.ui.ImageToolPanel;
import com.jvn.editor.ui.InspectorView;
import com.jvn.editor.ui.JesScriptAnalyzer;
import com.jvn.editor.ui.JvnStatusBar;
import com.jvn.editor.ui.LanguageDiagnostic;
import com.jvn.editor.ui.LayeredImageVisualizerView;
import com.jvn.editor.ui.LayoutEditorLauncherView;
import com.jvn.editor.ui.LayoutStudioWindowManager;
import com.jvn.editor.ui.MaintenanceOverlay;
import com.jvn.editor.ui.MetallicJvnLogo;
import com.jvn.editor.ui.NewTabIcon;
import com.jvn.editor.ui.NewProjectWizard;
import com.jvn.editor.ui.PanelChooserActionIcon;
import com.jvn.editor.ui.ProjectExplorerView;
import com.jvn.editor.ui.ProjectViewportSpec;
import com.jvn.editor.ui.RefreshIcon;
import com.jvn.editor.ui.PuppeteerLauncherPanel;
import com.jvn.editor.ui.RunConsoleView;
import com.jvn.editor.ui.ScriptEditorLauncherView;
import com.jvn.editor.ui.SettingsEditorView;
import com.jvn.editor.ui.SidebarToolIcon;
import com.jvn.editor.ui.StartupSplashOverlay;
import com.jvn.editor.ui.ShutdownSplashOverlay;
import com.jvn.editor.ui.StoryTimelineView;
import com.jvn.editor.ui.StoryboardOverlayState;
import com.jvn.editor.ui.StoryboardOverlayView;
import com.jvn.editor.ui.TilemapEditorView;
import com.jvn.editor.ui.TrashmanView;
import com.jvn.editor.ui.VersionControlView;
import com.jvn.editor.ui.VnsCodeEditor;
import com.jvn.editor.ui.VnsDiagnosticsView;
import com.jvn.editor.ui.VnsFlowMapView;
import com.jvn.editor.ui.VnsTimelineOutlineView;
import com.jvn.editor.ui.VnsScriptAnalyzer;
import com.jvn.editor.ui.WhatsNewCatalog;
import com.jvn.editor.ui.WhatsNewDialog;
import com.jvn.editor.ui.actioneditor.AnimationProject;
import com.jvn.editor.ui.actioneditor.CodeImporter;
import com.jvn.editor.ui.actioneditor.EntityTrack;
import com.jvn.editor.ui.actioneditor.Keyframe;
import com.jvn.editor.ui.actioneditor.PropertyType;
import com.jvn.editor.ui.actioneditor.PuppeteerWindow;
import com.jvn.editor.ui.actioneditor.TimelineDiagnostic;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.sun.management.OperatingSystemMXBean;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

public class EditorApp extends Application {
  // UI
  private AnimationTimer timer;
  private Label status;
  private Label fps;
  private JvnStatusBar statusBar;
  private FlowPane perfChips;
  private Label cpuChip;
  private Label heapChip;
  private Label jvmChip;
  private Label fpsChip;
  private Label graphicsChip;
  private Label threadsChip;
  private Label gcChip;
  private Label javaChip;
  private Label jdkChip;
  private Label gradleChip;
  private Label projectShapeChip;
  private PerfGraph perfGraph;
  private EditorGraphicsStatus.Snapshot graphicsStatus;
  private File lastOpened;
  private Entity2D selected;
  private String lastSelectedName;
  private InspectorView inspectorView;
  private TabPane filesTabs;
  private boolean showGrid = true;
  
  private ProjectExplorerView projView;
  private TrashmanView trashmanView;
  private StoryTimelineView timelineView;
  private VnsDiagnosticsView vnsDiagnosticsView;
  private VnsFlowMapView vnsFlowMapView;
  private VnsTimelineOutlineView vnsTimelineOutlineView;
  private AssetBrowserView assetBrowserView;
  private VersionControlView versionControlView;
  private LayoutEditorLauncherView layoutEditorLauncherView;
  private StoryboardOverlayView storyboardOverlayView;
  private LayeredImageVisualizerView layeredImageVisualizerView;
  private ImageAttributesToolView imageAttributesToolView;
  private ImageTintToolView imageTintToolView;
  private LayoutStudioWindowManager layoutStudioWindowManager;
  private EditorSettingsView editorSettingsView;
  private SettingsEditorView settingsEditor;
  private com.jvn.editor.ui.MenuThemeEditorView menuThemeEditor;
  private TilemapEditorView mapEditorView;
  private PuppeteerLauncherPanel puppeteerLauncherPanel;
  private ScriptEditorLauncherView scriptEditorLauncherView;
  private Tab tabEditorSettings;
  private Tab tabPuppeteerLauncher;
  private Tab tabScriptEditorLauncher;
  private final CommandStack commands = new CommandStack();
  private Runnable refreshMainCommandUi = () -> {};
  private TabPane leftTabs;
  private TabPane rightTabs;
  private SplitPane centerSplit;
  private StackPane leftSidebarShell;
  private StackPane rightSidebarShell;
  private PauseTransition leftEmptySidebarAutoCloseDelay;
  private PauseTransition rightEmptySidebarAutoCloseDelay;
  private Timeline leftEmptySidebarCloseAnimation;
  private Timeline rightEmptySidebarCloseAnimation;
  private boolean editorFullscreen;
  private double[] savedCenterDividers;
  private boolean layeredVisualizerFullscreen;
  private double[] savedLayeredVisualizerDividers;
  private ImageToolPanel fullscreenImageToolView;
  private EditorWorkspaceHubView workspaceHubView;
  private Tab tabWorkspaceHub;
  private Tab tabProject;
  private Tab tabTrashman;
  private Tab tabTimeline;
  private Tab tabInspector;
  private Tab tabVnsDiagnostics;
  private Tab tabVnsFlowMap;
  private Tab tabVnsTimelineOutline;
  private Tab tabAssetBrowser;
  private Tab tabVersionControl;
  private Tab tabLayoutLauncher;
  private Tab tabStoryboardOverlay;
  private Tab tabLayeredImageVisualizer;
  private Tab tabImageAttributesTool;
  private Tab tabImageTintTool;
  private Tab tabLeftAdd;
  private Tab tabRightAdd;
  private StackPane leftSidebarDividerNode;
  private StackPane rightSidebarDividerNode;
  private Region leftSidebarHiddenArrow;
  private Region rightSidebarHiddenArrow;
  private ScrollPane inspectorScroll;
  private File projectRoot;
  private StoryboardOverlayState storyboardOverlayState = StoryboardOverlayState.none();
  private EditorPreferencesStore editorPreferencesStore;
  private EditorPreferences editorPreferences = EditorPreferences.defaults();
  private OperatingSystemMXBean osBean;
  private long lastPerfUpdateNs = -1L;
  private double lastFps = 0.0;
  private double smoothedProcessCpu = Double.NaN;
  private double smoothedFps = Double.NaN;
  private long lastGcCollectionCount = -1L;
  private long lastGcCollectionTimeMs = -1L;
  private final EditorMemoryPressureMonitor memoryPressureMonitor = new EditorMemoryPressureMonitor();
  private boolean memoryPressureDialogShowing;
  private File perfEnvironmentProjectRoot;
  private PerfEnvironment perfEnvironment;
  private static final long PERF_UPDATE_INTERVAL_NS = 300_000_000L;
  private static final long TAB_DIRTY_REFRESH_INTERVAL_NS = 250_000_000L;
  private static final long DEV_DIAGNOSTIC_WRITE_INTERVAL_NS = 5_000_000_000L;
  private static final DateTimeFormatter DEV_DIAGNOSTIC_LOG_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private static final double PERF_CPU_SMOOTH_ALPHA = 0.28;
  private static final double PERF_FPS_SMOOTH_ALPHA = 0.20;
  private final EditorFrameStats editorFrameStats = new EditorFrameStats(180);
  private double targetFps = EditorPreferences.DEFAULT_EDITOR_MAX_FPS;
  private long minFrameIntervalNs = 0L; // 0 = uncapped
  private @Nullable Path developerDiagnosticsLogFile;
  private long lastDeveloperDiagnosticWriteNs = -1L;
  private static final Color CPU_COLOR = Color.web("#f27333");
  private static final Color HEAP_COLOR = Color.web("#a855f7");
  private static final Color FPS_COLOR = Color.web("#f4f4f4");
  private static final Color GRID_BG = Color.color(0.08, 0.08, 0.08, 0.8);
  private static final Color GRID_LINE = Color.color(1, 1, 1, 0.08);
  private static final String[] EDITABLE_EXTENSIONS = new String[] {
      ".jes", ".txt", ".vns", ".java", ".storymap", ".timeline", ".theme", ".menu", ".layout", ".style", ".registry",
      ".settings", ".project", ".properties", ".md", ".json",
      ".yaml", ".yml", ".toml", ".ini", ".cfg", ".xml", ".csv", ".tsv"
  };
  private static final long MIN_STARTUP_SPLASH_MS = 1500L;
  private static final long STARTUP_STEP_DELAY_MS = 0L;
  private static final boolean STRICT_STARTUP_GRADLE_CHECK =
      Boolean.getBoolean("jvn.editor.strictStartupGradleCheck");
  private static final DateTimeFormatter STARTUP_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final Pattern STARTUP_PROCESS_NOISE = Pattern.compile(
      "^(> Task |> Configure |BUILD SUCCESSFUL|Deprecated Gradle|\\d+ actionable|To honour the JVM|Daemon will be stopped|\\s*$)");
  private static final int STARTUP_COMMAND_TAIL_LINES = 14;
  private static final double SIDEBAR_COLLAPSED_EPSILON = 0.01;
  private static final Duration EMPTY_SIDEBAR_AUTO_CLOSE_DELAY = Duration.seconds(5);
  private static final Duration EMPTY_SIDEBAR_CLOSE_DURATION = Duration.millis(520);
  private static final Duration SIDEBAR_TOOL_TAB_CLOSE_DURATION = Duration.millis(145);
  private static final String SIDEBAR_RESIZE_HANDLER_KEY = "jvn.sidebarResize.handlerInstalled";
  private static final String SIDEBAR_TOOL_TAB_CLOSE_INSTALLED_KEY = "jvn.sidebarToolTabClose.installed";
  private static final String SIDEBAR_TOOL_TAB_CLOSE_ACTIVE_KEY = "jvn.sidebarToolTabClose.active";
  private static final String PANEL_CHOOSER_TAB_ROLE = "panel-chooser";
  private static final String PANEL_CHOOSER_REFRESH_KEY = "panel-chooser-refresh";
  private static final String PANEL_WINDOW_SUPPRESS_UNLOAD_KEY = "jvn.panelWindow.suppressUnload";
  private static final String EDITOR_START_PROJECT_PROPERTY = "jvn.editor.openProject";
  private static final String EDITOR_OPEN_FILE_PROPERTY = "jvn.editor.openFile";
  private static final String MAINTENANCE_LATE_MAY_2026 = "Late May 2026";
  private static final boolean ALLOW_MAINTENANCE_TOOL_LAUNCHES =
      Boolean.getBoolean("jvn.editor.allowMaintenanceTools");
  private static final int MAINTENANCE_CHOOSER_STRIPE_WIDTH = 18;
  private static final double MAINTENANCE_CHOOSER_STRIPE_SPEED = 48.0;
  private static final Color MAINTENANCE_CHOOSER_STRIPE_COLOR = Color.rgb(255, 130, 0, 0.28);
  private static final Color MAINTENANCE_CHOOSER_TINT_COLOR = Color.rgb(26, 14, 0, 0.38);
  private static final boolean DEVELOPER_MODE = Boolean.getBoolean("jvn.editor.developerMode");
  private static final boolean SAFE_MODE = Boolean.getBoolean("jvn.editor.safeMode");
  // VNS z-order applies to scene entities, while backgrounds are always painted first.
  // Reserve values below the complete Integer range accepted by VNS `z=` so negative-z
  // props remain visible above the background in Puppeteer just as they are in preview.
  private static final double PUPPETEER_PREVIOUS_BACKGROUND_Z = (double) Integer.MIN_VALUE - 0.5;
  private static final double PUPPETEER_CURRENT_BACKGROUND_Z = (double) Integer.MIN_VALUE - 0.25;
  private static final Pattern DSL_DIAGNOSTIC_PATTERN =
      Pattern.compile("^L(\\d+)\\s+(\\S+?):\\s+(.+?)(?:\\s+Quick fix:\\s+(.+))?$");
  private final EnumMap<EditorSidebarPanel, Stage> panelWindows =
      new EnumMap<>(EditorSidebarPanel.class);
  private Stage editorSettingsWindow;
  private Stage gameBuildPublisherWindow;
  private GameBuildPublisherView gameBuildPublisherView;
  private DeveloperLogPanel developerLogPanel;
  private List<EditorWorkspaceHubView.SetupIssue> startupSetupIssues = List.of();
  private com.jvn.plugin.runtime.PluginHost pluginHost;
  private Menu pluginToolsMenu;
  private boolean shutdownInProgress;

  public static void main(String[] args) {
    com.jvn.core.diagnostics.GraphicsPipeline.configure();
    launch(args);
  }

  private Window dialogOwner() {
    if (status != null && status.getScene() != null) return status.getScene().getWindow();
    if (filesTabs != null && filesTabs.getScene() != null) return filesTabs.getScene().getWindow();
    return null;
  }

  private List<Path> developerLogRoots() {
    List<Path> roots = new ArrayList<>();
    if (projectRoot != null) roots.add(projectRoot.toPath());
    File workspace = resolveWorkspaceRoot();
    if (workspace != null) roots.add(workspace.toPath());
    roots.add(Path.of(System.getProperty("user.dir", ".")));
    return roots;
  }

  private void refreshDeveloperLogs() {
    if (developerLogPanel != null) developerLogPanel.refresh();
  }

  private void setEditorTheme(EditorTheme.Theme theme) {
    setEditorTheme(theme, true);
  }

  private void setEditorTheme(EditorTheme.Theme theme, boolean persist) {
    EditorTheme.Theme target = theme == null ? EditorTheme.Theme.DARK : theme;
    boolean changed = target != EditorTheme.theme();
    if (changed) {
      EditorTheme.setTheme(target);
      applyThemeToOpenWindows();
    }
    if (persist && editorPreferences != null) {
      editorPreferences.setEditorTheme(target == EditorTheme.Theme.LIGHT
          ? EditorPreferences.LAUNCHER_THEME_LIGHT
          : EditorPreferences.LAUNCHER_THEME_DARK);
      persistEditorPreferences();
      if (editorSettingsView != null) {
        editorSettingsView.loadIntoForm(editorPreferences);
      }
    }
    if (status != null) {
      status.setText("Theme: " + (target == EditorTheme.Theme.LIGHT ? "Light" : "Dark"));
    }
    if (statusBar != null) statusBar.setTheme(target);
  }

  private void applyThemeToOpenWindows() {
    for (Window window : Window.getWindows()) {
      if (window == null) continue;
      Scene scene = window.getScene();
      if (scene != null) {
        EditorTheme.apply(scene);
      }
    }
  }

  private Label createDialogDetailLabel(String text) {
    Label label = new Label(text == null ? "" : text);
    label.getStyleClass().add("editor-dialog-message");
    label.setWrapText(true);
    label.setMaxWidth(480);
    return label;
  }

  private void doOpenVns(Stage stage) {
    try {
      FileChooser fc = new FileChooser();
      fc.setTitle("Open VNS Script");
      fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("VNS scripts", "*.vns"));
      File f = fc.showOpenDialog(stage);
      if (f == null) return;
      openVnsFile(f);
    } catch (Exception ex) {
      status.setText("Load failed");
      EditorDialogs.error(
          stage,
          "Open VNS Script",
          "Failed to load the selected VNS script.",
          ex,
          "Confirm the script file still exists and is readable.",
          "Check included scripts and referenced assets if the parser reported a nested error.");
    }
  }

  private void doRunProject(Stage stage) {
    File root = ensureProjectRoot(stage);
    if (root == null) return;
    doRunProject(root);
  }

  private void doRunProject(File root) {
    if (root == null) return;
    if (editorPreferences != null
        && editorPreferences.isEditorConfirmRunProject()
        && !EditorDialogs.confirm(
            dialogOwner(),
            "Run Project",
            "Run " + root.getName() + " from the editor?",
            "Run",
            false)) {
      if (status != null) status.setText("Run cancelled");
      return;
    }
    if (editorPreferences == null || editorPreferences.isAutoSaveBeforeRun()) {
      if (!saveDirtyEditorsBeforeRun()) return;
    }
    Properties mf = loadManifest(root);
    if (mf == null) { status.setText("jvn.project not found"); return; }
    String type = mf.getProperty("type", "gradle").trim();
    if ("gradle".equalsIgnoreCase(type)) {
      String path = mf.getProperty("path", ":runtime").trim();
      String task = mf.getProperty("task", "run").trim();
      String args = mf.getProperty("args", defaultGradleRunArgs());
      runGradle(root, composeGradleTask(path, task), args == null ? new String[]{} : args.split("\\s+"), "Run Project");
    } else if ("vn".equalsIgnoreCase(type)) {
      runVnProjectInRuntime(root, mf);
    } else if ("jes".equalsIgnoreCase(type)) {
      // For JES-only projects: open the entry script and set as project
      String entry = mf.getProperty("entry", "scripts/main.jes");
      File f = new File(root, entry);
      if (f.exists()) openJesFile(f);
      this.projectRoot = root;
      if (projView != null) projView.setRootDirectory(root);
      status.setText("Opened JES project: " + root.getName());
    } else {
      status.setText("Unknown project type: " + type);
    }
  }

  private File ensureProjectRoot(Stage stage) {
    File root = this.projectRoot;
    if (root == null) {
      DirectoryChooser dc = new DirectoryChooser();
      dc.setTitle("Select Project Root");
      root = dc.showDialog(stage);
      if (root == null) return null;
      openProjectDirectory(root);
      selectProjectTab();
    }
    return root;
  }

  private void openProjectDirectory(File dir) {
    if (dir == null || !dir.exists() || !dir.isDirectory()) return;
    stopAllPreviewAudio();
    this.projectRoot = dir;
    reloadPlugins(dir);
    this.developerDiagnosticsLogFile = null;
    this.lastDeveloperDiagnosticWriteNs = -1L;
    Properties mf = loadManifest(dir);
    configureProjectContext(dir, mf);
    applyProjectRootToTabs();
    if (workspaceHubView != null) {
      workspaceHubView.setCurrentProject(dir);
    }
    refreshDeveloperLogs();
    refreshMainCommandUi.run();
  }

  private void reloadPlugins(File project) {
    if (pluginHost != null) pluginHost.close();
    pluginHost = com.jvn.plugin.runtime.PluginHost.builder(com.jvn.plugin.api.PluginEnvironment.EDITOR)
        .jvnVersion(System.getProperty("jvn.version", "dev"))
        .projectDirectory(project == null ? null : project.toPath())
        .build();
    pluginHost.discoverAndStart();
    com.jvn.core.animation.EasingExtensions.install(pluginHost.registries().animationEasings());
    refreshPluginToolsMenu();
  }

  private void refreshPluginToolsMenu() {
    if (pluginToolsMenu == null) return;
    pluginToolsMenu.getItems().clear();
    if (pluginHost == null || pluginHost.registries().editorTools().entries().isEmpty()) {
      MenuItem empty = new MenuItem("No plugin tools installed");
      empty.setDisable(true);
      pluginToolsMenu.getItems().add(empty);
      return;
    }
    for (var entry : pluginHost.registries().editorTools().entries()) {
      var tool = entry.extension();
      MenuItem item = new MenuItem(tool.label());
      item.setOnAction(event -> {
        try {
          Map<String, Object> services = new LinkedHashMap<>();
          Window owner = dialogOwner();
          if (owner != null) services.put("window", owner);
          tool.open(new com.jvn.plugin.api.editor.EditorToolContext(
              projectRoot == null ? null : projectRoot.toPath(), services));
        } catch (Exception error) {
          EditorDialogs.error(dialogOwner(), "Plugin tool", "Could not open " + tool.label(), error,
              "Review the plugin log and API compatibility information.");
        }
      });
      pluginToolsMenu.getItems().add(item);
    }
  }

  @Override
  public void stop() {
    if (pluginHost != null) pluginHost.close();
  }

  private Properties loadManifest(File dir) {
    try (FileInputStream fis = new FileInputStream(new File(dir, "jvn.project"))) {
      Properties p = new Properties();
      p.load(fis);
      return p;
    } catch (Exception ignore) { return null; }
  }

  private File resolveStoryMapFile(File root, Properties mf) {
    return StoryMapPaths.resolveExistingOrDefault(root, mf);
  }

  private void configureProjectContext(File root, Properties mf) {
    if (root == null) return;
    if (projView != null) {
      projView.setRootDirectory(root);
      if (tabProject != null && tabProject.getContent() != projView) {
        tabProject.setContent(projView);
      }
    }
    if (timelineView != null) {
      timelineView.setTimelineFile(resolveStoryMapFile(root, mf));
      timelineView.setProjectRoot(root);
    }
    if (settingsEditor != null) settingsEditor.setProjectRoot(root);
    if (menuThemeEditor != null) menuThemeEditor.setProjectRoot(root);
    if (mapEditorView != null) mapEditorView.setProjectRoot(root);
    if (assetBrowserView != null) assetBrowserView.setProjectRoot(root);
    if (versionControlView != null) versionControlView.setProjectRoot(root);
    if (layoutEditorLauncherView != null) layoutEditorLauncherView.setProjectRoot(root);
    if (storyboardOverlayView != null) storyboardOverlayView.setProjectRoot(root);
    if (layeredImageVisualizerView != null) layeredImageVisualizerView.setProjectRoot(root);
    if (imageAttributesToolView != null) imageAttributesToolView.setProjectRoot(root);
    if (imageTintToolView != null) imageTintToolView.setProjectRoot(root);
    if (scriptEditorLauncherView != null) {
      scriptEditorLauncherView.setProjectRoot(root);
      scriptEditorLauncherView.setWorkspaceRoot(resolveWorkspaceRoot());
    }
    syncStoryboardOverlayProjectState();
  }

  private String composeGradleTask(String path, String task) {
    String p = path == null ? "" : path.trim();
    String t = task == null ? "run" : task.trim();
    if (t.startsWith(":")) return t; // full task provided
    if (p.isEmpty()) return t;
    if (!p.startsWith(":")) p = ":" + p;
    return p + ":" + t;
  }

  private String defaultGradleRunArgs() {
    return editorPreferences == null || editorPreferences.isGradleSkipTestsOnRun()
        ? "-x test"
        : "";
  }

  private void runGradle(File root, String task, String[] args, String title) {
    boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
    File gradlew = new File(root, windows ? "gradlew.bat" : "gradlew");
    try {
      RunConsoleView console = new RunConsoleView(title);
      console.setLaunchContext(gradlew.exists() ? "Gradle wrapper" : "Gradle CLI", task, root.getName());
      RunConsoleView.ProcessStarter starter =
          options -> GradleRuntimeLauncher.start(root, gradlew, task, args, options);
      console.setProcessStarter(starter);

      javafx.stage.Stage logStage = new javafx.stage.Stage();
      logStage.setTitle(title);
      javafx.scene.Scene logScene = new javafx.scene.Scene(console, 980, 620);
      EditorTheme.apply(logScene);
      logStage.setScene(logScene);
      logStage.setMinWidth(860);
      logStage.setMinHeight(520);
      applyLinuxDefaultWindowState(logStage);
      logStage.setOnHiding(e -> console.dispose());
      logStage.show();
      if (!console.startConfiguredProcess()) {
        status.setText("Run failed");
      }
    } catch (Exception ex) {
      status.setText("Run failed");
    }
  }

  private void showGameBuildPublisherWindow(Stage owner) {
    File root = ensureProjectRoot(owner);
    if (root == null) return;
    File workspace = resolveWorkspaceRoot();
    if (workspace == null || !workspace.isDirectory()) {
      status.setText("Cannot locate JVN workspace root");
      EditorDialogs.error(
          dialogOwner(),
          "Build Unavailable",
          "Cannot locate the JVN workspace root.",
          null,
          "Launch the editor from the JVN repository root.",
          "Use the launcher to reopen the project if the workspace path changed.");
      return;
    }

    if (gameBuildPublisherWindow != null && gameBuildPublisherWindow.isShowing()) {
      if (gameBuildPublisherView != null) gameBuildPublisherView.setProjectRoot(root);
      gameBuildPublisherWindow.toFront();
      gameBuildPublisherWindow.requestFocus();
      return;
    }

    gameBuildPublisherView = new GameBuildPublisherView(workspace, root, request ->
        runGradle(workspace, request.taskName(), request.args(), request.title()));

    BorderPane popupRoot = new BorderPane(gameBuildPublisherView);
    popupRoot.setStyle("-fx-background-color: #111;");
    javafx.scene.Scene scene = new javafx.scene.Scene(popupRoot, 1040, 720);
    EditorTheme.apply(scene);

    Stage window = new Stage();
    if (owner != null) window.initOwner(owner);
    window.setTitle("JVN Game Build & Publish");
    window.setScene(scene);
    window.setMinWidth(880);
    window.setMinHeight(620);
    window.setOnHidden(e -> {
      gameBuildPublisherWindow = null;
      gameBuildPublisherView = null;
    });

    gameBuildPublisherWindow = window;
    window.show();
  }

  private void runVnProjectInRuntime(File root, Properties mf) {
    String entryVns = mf.getProperty("entryVns", "").trim();
    String runtimeScript = normalizeRuntimeScriptPath(entryVns);
    File workspaceRoot = resolveWorkspaceRoot();
    if (workspaceRoot == null) {
      status.setText("Cannot locate JVN workspace root");
      return;
    }

    StringBuilder runtimeArgs = new StringBuilder();
    runtimeArgs.append("--assets ").append(quoteCliArg(root.getAbsolutePath()));
    if (runtimeScript != null && !runtimeScript.isBlank()) {
      runtimeArgs.append(" --script ").append(quoteCliArg(runtimeScript));
    }

    String title = mf.getProperty("name", "").trim();
    if (!title.isBlank()) runtimeArgs.append(" --title ").append(quoteCliArg(title));

    String width = mf.getProperty("width", "").trim();
    if (!width.isBlank()) runtimeArgs.append(" --width ").append(width);
    String height = mf.getProperty("height", "").trim();
    if (!height.isBlank()) runtimeArgs.append(" --height ").append(height);

    String runtimeUi = mf.getProperty("runtime.ui", "").trim();
    if (!runtimeUi.isBlank()) runtimeArgs.append(" --ui ").append(quoteCliArg(runtimeUi));
    String runtimeAudio = mf.getProperty("runtime.audio", "").trim();
    if (!runtimeAudio.isBlank()) runtimeArgs.append(" --audio ").append(quoteCliArg(runtimeAudio));
    String runtimeLocale = mf.getProperty("runtime.locale", "").trim();
    if (!runtimeLocale.isBlank()) runtimeArgs.append(" --locale ").append(quoteCliArg(runtimeLocale));
    if (editorPreferences == null || editorPreferences.isEditorRuntimePerfHud()) {
      runtimeArgs.append(" --perf-hud");
    }

    runGradle(workspaceRoot, ":runtime:run", new String[] { "--args=" + runtimeArgs }, "JVN Runtime");
    status.setText("Launching runtime: " + root.getName());
  }

  private String normalizeRuntimeScriptPath(String entryVns) {
    if (entryVns == null || entryVns.isBlank()) return null;
    String script = entryVns.trim().replace('\\', '/');
    if (script.startsWith("./")) script = script.substring(2);
    if (script.startsWith("game/scripts/")) script = script.substring("game/scripts/".length());
    if (script.startsWith("scripts/")) script = script.substring("scripts/".length());
    return script.isBlank() ? null : script;
  }

  static String cleanStartupPathValue(String raw) {
    if (raw == null) return "";
    if (raw.isBlank()) return "";
    int start = firstNonWhitespace(raw);
    int end = lastNonWhitespace(raw);
    if (start < 0 || end < start) return "";
    char first = raw.charAt(start);
    char last = raw.charAt(end);
    boolean doubleQuoted = first == '"' && last == '"';
    boolean singleQuoted = first == '\'' && last == '\'';
    if (doubleQuoted || singleQuoted) {
      return raw.substring(start + 1, end);
    }
    return raw;
  }

  private static int firstNonWhitespace(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (!Character.isWhitespace(value.charAt(i))) return i;
    }
    return -1;
  }

  private static int lastNonWhitespace(String value) {
    for (int i = value.length() - 1; i >= 0; i--) {
      if (!Character.isWhitespace(value.charAt(i))) return i;
    }
    return -1;
  }

  private String quoteCliArg(String raw) {
    String value = raw == null ? "" : raw;
    if (value.isEmpty() || value.contains(" ")) {
      return "'" + value + "'";
    }
    return value;
  }

  private File resolveWorkspaceRoot() {
    File fromCwd = findGradleRoot(new File(System.getProperty("user.dir", ".")));
    if (fromCwd != null) return fromCwd;
    return findGradleRoot(new File("."));
  }

  private File resolveStartupProjectOverride() {
    String raw = System.getProperty(EDITOR_START_PROJECT_PROPERTY, "");
    if (raw == null || raw.isBlank()) {
      Application.Parameters parameters = getParameters();
      if (parameters != null && parameters.getUnnamed() != null && !parameters.getUnnamed().isEmpty()) {
        raw = parameters.getUnnamed().get(0);
      }
    }
    String path = cleanStartupPathValue(raw);
    if (path.isBlank()) return null;
    File candidate = new File(path);
    if (candidate.isFile() && "jvn.project".equalsIgnoreCase(candidate.getName())) {
      candidate = candidate.getParentFile();
    }
    if (candidate == null || !candidate.isDirectory()) return null;
    try {
      return candidate.getCanonicalFile();
    } catch (Exception ignore) {
      return candidate.getAbsoluteFile();
    }
  }

  private File resolveStartupFileOverride() {
    String raw = System.getProperty(EDITOR_OPEN_FILE_PROPERTY, "");
    String path = cleanStartupPathValue(raw);
    if (path.isBlank()) return null;
    File candidate = new File(path);
    if (candidate == null || !candidate.isFile()) return null;
    try {
      return candidate.getCanonicalFile();
    } catch (Exception ignore) {
      return candidate.getAbsoluteFile();
    }
  }

  private void applyStartupProjectOverride() {
    File startupProject = resolveStartupProjectOverride();
    File startupFile = resolveStartupFileOverride();
    if (startupProject == null && startupFile == null) return;
    if (startupProject != null) {
      openProjectDirectory(startupProject);
      selectProjectTab();
    }
    if (startupFile != null) {
      openFile(startupFile);
      if (status != null) status.setText("Opened: " + startupFile.getName());
    } else if (status != null && startupProject != null) {
      status.setText("Project: " + startupProject.getName());
    }
  }

  private File findGradleRoot(File start) {
    File cur = start;
    while (cur != null) {
      File gradlew = new File(cur, "gradlew");
      File gradlewBat = new File(cur, "gradlew.bat");
      File settings = new File(cur, "settings.gradle.kts");
      File settingsGroovy = new File(cur, "settings.gradle");
      if ((gradlew.exists() || gradlewBat.exists()) && (settings.exists() || settingsGroovy.exists())) {
        return cur;
      }
      cur = cur.getParentFile();
    }
    return null;
  }

  private void doNewProject(Stage stage) {
    File projectDir = NewProjectWizard.showAndWait(stage);
    if (projectDir == null) return;

    openProjectDirectory(projectDir);
    Properties mf = loadManifest(projectDir);
    
    // Open the entry script
    String entryRel = (mf != null) ? mf.getProperty("entryVns", "scripts/story/prologue.vns") : "scripts/story/prologue.vns";
    File entryScript = new File(projectDir, entryRel);
    if (!entryScript.exists() && "scripts/story/prologue.vns".equals(entryRel)) {
      entryScript = new File(projectDir, "scripts/prologue.vns");
    }
    if (entryScript.exists()) {
      openFile(entryScript);
    }
    
    selectProjectTab();
    status.setText("Created project: " + projectDir.getName());
  }

  private void openSample(String absolutePath) {
    File f = new File(absolutePath);
    if (!f.exists()) {
      EditorDialogs.error(
          dialogOwner(),
          "Open Sample",
          "Sample file was not found:\n" + absolutePath,
          null,
          "Confirm the sample assets are present in this checkout.",
          "Refresh or restore the project files if the sample was moved.");
      return;
    }
    openFile(f);
  }

  private void openTimelineArc(StoryTimelineView.Arc arc) {
    if (arc == null || arc.script == null || arc.script.isBlank()) return;
    File target = resolveProjectFile(arc.script);
    if (target == null || !target.exists()) {
      status.setText("Arc script not found: " + arc.script);
      return;
    }
    openFile(target);
    if (arc.entryLabel != null && !arc.entryLabel.isBlank()) {
      status.setText("Opened arc " + arc.name + " @ " + arc.entryLabel);
    } else {
      status.setText("Opened arc " + arc.name);
    }
  }

  private void openTimelineLinkTarget(StoryTimelineView.Link link) {
    if (link == null || timelineView == null || link.toArc == null || link.toArc.isBlank()) return;
    StoryTimelineView.Arc target = timelineView.findArc(link.toArc);
    if (target == null) {
      status.setText("Unknown target arc: " + link.toArc);
      return;
    }
    openTimelineArc(target);
  }

  // legacy helpers removed; ViewportView now owned by per-file tabs

  @Override
  public void start(Stage primaryStage) {
    EditorCrashSupport.installProcessHandler();
    StartupSplashOverlay splash = new StartupSplashOverlay();
    splash.show();
    launchStartupSequence(primaryStage, splash, false);
  }

  private void launchStartupSequence(Stage primaryStage,
                                     StartupSplashOverlay splash,
                                     boolean clearLogs) {
    splash.prepareForChecks(clearLogs);
    splash.setProgress(0.0);
    splash.setStatus("Running startup health checks...");
    splash.appendLog(startupLogLine("INFO", "Bootstrap",
        clearLogs ? "Retrying preflight checks" : "Launching preflight checks"));

    long splashShownNs = System.nanoTime();
    Task<Void> startupTask = createStartupHealthCheckTask(splash);
    startupTask.messageProperty().addListener((o, ov, nv) -> {
      if (nv == null || nv.isBlank()) return;
      splash.setStatus(nv);
    });
    startupTask.progressProperty().addListener((o, ov, nv) -> {
      double progress = nv == null ? -1.0 : nv.doubleValue();
      splash.setProgress(progress);
    });
    startupTask.setOnSucceeded(e -> finalizeStartupSuccess(primaryStage, splash, splashShownNs));
    startupTask.setOnFailed(e -> finalizeStartupFailure(primaryStage, splash, startupTask.getException()));
    Thread startupThread = new Thread(startupTask, "jvn-editor-startup-checks");
    startupThread.setDaemon(true);
    startupThread.start();
  }

  private void finalizeStartupSuccess(Stage primaryStage,
                                      StartupSplashOverlay splash,
                                      long splashShownNs) {
    long elapsedMs = (System.nanoTime() - splashShownNs) / 1_000_000L;
    long waitMs = Math.max(0L, MIN_STARTUP_SPLASH_MS - elapsedMs);
    PauseTransition delay = new PauseTransition(Duration.millis(waitMs));
    delay.setOnFinished(evt -> {
      try {
        initializeEditorStage(primaryStage);
      } catch (Exception ex) {
        EditorDialogs.error(
            primaryStage,
            "JVN Editor",
            "Startup failed while preparing the editor window.",
            ex,
            "Retry editor startup from the splash screen if it is still available.",
            "Confirm the workspace and editor resources are readable.");
      } finally {
        splash.close();
      }
    });
    delay.play();
  }

  private void finalizeStartupFailure(Stage primaryStage,
                                      StartupSplashOverlay splash,
                                      Throwable startupFailure) {
    StartupFailure failure = startupFailure instanceof StartupFailure sf
        ? sf
        : new StartupFailure(
            "Startup checks failed",
            startupFailure == null ? "Unknown startup error" : safeMessage(startupFailure),
            startupFailure);
    splash.appendLog(startupLogLine("ERROR", "Preflight", failure.summary()));
    if (failure.detail() != null && !failure.detail().isBlank()) {
      splash.appendLog(startupLogLine("ERROR", "Preflight", failure.detail()));
    }
    splash.showFailure(
        failure.summary(),
        failure.detail(),
        () -> launchStartupSequence(primaryStage, splash, true),
        Platform::exit);
  }

  private Task<Void> createStartupHealthCheckTask(StartupSplashOverlay splash) {
    return new Task<>() {
      @Override
      protected Void call() {
        final int totalChecks = 12;
        int step = 0;
        List<EditorWorkspaceHubView.SetupIssue> setupIssues = new ArrayList<>();
        updateProgress(0, totalChecks);

        File workspace = resolveWorkspaceRoot();
        if (workspace == null || !workspace.isDirectory()) {
          throw new StartupFailure(
              "Workspace root not found",
              "Launch the editor from the JVN repository root so Gradle and smoke tests can run.");
        }
        logSplash(splash, "OK", "Workspace", workspace.getAbsolutePath());
        updateMessage("Workspace root resolved");
        advance(++step, totalChecks);

        Path stateDir = workspace.toPath().resolve(".jvn");
        ensureWritableStateDir(stateDir);
        logSplash(splash, "OK", "State", "Writable state path: " + stateDir.toAbsolutePath());
        updateMessage("State directory writable");
        advance(++step, totalChecks);

        int requiredJava = readRequiredJavaVersion(workspace.toPath());
        int runtimeJava = parseJavaMajor(System.getProperty("java.version", "unknown"));
        if (requiredJava > 0 && runtimeJava > 0 && runtimeJava < requiredJava) {
          setupIssues.add(new EditorWorkspaceHubView.SetupIssue(
              "error",
              "JDK version mismatch",
              "Running Java " + runtimeJava + " but Gradle toolchain requires Java " + requiredJava + ".",
              "Launch JVN with JDK " + requiredJava + "+ or set JAVA_HOME to a compatible JDK before opening the editor."));
          logSplash(splash, "WARN", "Runtime",
              "Running Java " + runtimeJava + " but Gradle toolchain requires Java " + requiredJava + ".");
        }
        if (ToolProvider.getSystemJavaCompiler() == null) {
          setupIssues.add(new EditorWorkspaceHubView.SetupIssue(
              "error",
              "Full JDK not detected",
              "The editor is running without javac. Java source tools and Gradle checks may fail.",
              "Install JDK 21 and launch JVN with JAVA_HOME pointing at that JDK."));
          logSplash(splash, "WARN", "Runtime", "javac is not available in this Java runtime.");
        }
        logSplash(splash, "OK", "Runtime",
            "Java " + System.getProperty("java.version", "unknown")
                + " at " + System.getProperty("java.home", "<unknown>"));
        updateMessage("JDK toolchain verified");
        advance(++step, totalChecks);

        String fxVersion = System.getProperty("javafx.version", "unknown");
        logSplash(splash, "OK", "Runtime", "JavaFX " + fxVersion);
        updateMessage("JavaFX runtime verified");
        advance(++step, totalChecks);

        Path gradlew = workspace.toPath().resolve(isWindowsOs() ? "gradlew.bat" : "gradlew");
        Path wrapperProps = workspace.toPath().resolve("gradle/wrapper/gradle-wrapper.properties");
        if (!Files.isRegularFile(gradlew) || !Files.isRegularFile(wrapperProps)) {
          setupIssues.add(new EditorWorkspaceHubView.SetupIssue(
              "error",
              "Gradle wrapper is missing",
              "The workspace is missing gradlew or gradle/wrapper/gradle-wrapper.properties.",
              "Restore the Gradle wrapper files before running builds from the editor."));
          logSplash(splash, "WARN", "Gradle", "Gradle wrapper files are missing.");
        } else if (!isWindowsOs() && !Files.isExecutable(gradlew)) {
          setupIssues.add(new EditorWorkspaceHubView.SetupIssue(
              "warn",
              "Gradle wrapper is not executable",
              gradlew.toAbsolutePath() + " exists but is not executable.",
              "Run: chmod +x gradlew"));
          logSplash(splash, "WARN", "Gradle", "Wrapper is not executable: " + gradlew.toAbsolutePath());
        } else {
          logSplash(splash, "OK", "Gradle", "Wrapper ready (Gradle " + readGradleWrapperVersion(wrapperProps) + ")");
        }
        updateMessage("Gradle wrapper located");
        advance(++step, totalChecks);

        if (STRICT_STARTUP_GRADLE_CHECK) {
          updateMessage("Checking Gradle environment");
          runStartupProcess(
              workspace,
              splash,
              "Gradle",
              List.of(resolveGradleCommand(workspace), "--version"),
              "Gradle wrapper check failed",
              "Fix the Gradle wrapper or local JDK configuration, then retry.");
        } else {
          logSplash(splash, "INFO", "Gradle",
              "Wrapper process check skipped; set -Djvn.editor.strictStartupGradleCheck=true to enable it.");
          updateMessage("Gradle wrapper process check skipped");
        }
        advance(++step, totalChecks);

        collectGitSetupIssues(workspace, splash, setupIssues);
        updateMessage("Git setup checked");
        advance(++step, totalChecks);

        collectGitHubSetupIssues(workspace, splash, setupIssues);
        updateMessage("GitHub setup checked");
        advance(++step, totalChecks);

        showStartupLaunchProgress(splash);
        logSplash(splash, "INFO", "Tests", "Full repository test suite not run during splash launch.");
        updateMessage("Launching editor");
        advance(++step, totalChecks);

        File diskRoot = workspace;
        long usableBytes = diskRoot.getUsableSpace();
        double freeGb = usableBytes / (1024.0 * 1024.0 * 1024.0);
        boolean diskOk = usableBytes >= 512L * 1024L * 1024L;
        logSplash(splash, diskOk ? "OK" : "WARN", "Storage",
            String.format(Locale.ROOT, "Free disk %.2f GB at %s", freeGb, diskRoot.getAbsolutePath()));
        updateMessage("Storage check complete");
        advance(++step, totalChecks);

        long maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        boolean heapOk = maxHeapMb >= 768L;
        logSplash(splash, heapOk ? "OK" : "WARN", "Memory", "Max heap " + maxHeapMb + " MB");
        updateMessage("Memory check complete");
        advance(++step, totalChecks);

        boolean projectOk = projectRoot == null || projectRoot.isDirectory();
        logSplash(splash, projectOk ? "OK" : "WARN", "Project",
            projectRoot == null
                ? "No project selected yet"
                : (projectOk ? projectRoot.getAbsolutePath() : "Missing project path: " + projectRoot.getAbsolutePath()));
        updateMessage("Startup health checks complete");
        advance(++step, totalChecks);

        logSplash(splash, "INFO", "Bootstrap", "Health checks complete");
        startupSetupIssues = EditorWorkspaceHubView.SetupIssue.copyOf(setupIssues);
        return null;
      }

      private void advance(int currentStep, int totalChecks) {
        updateProgress(currentStep, totalChecks);
        if (!isCancelled()) {
          startupSleep(STARTUP_STEP_DELAY_MS);
        }
      }
    };
  }

  private static String startupLogLine(String level, String category, String detail) {
    String time = LocalTime.now().format(STARTUP_TIME_FORMAT);
    String lv = level == null ? "INFO" : level.trim().toUpperCase(Locale.ROOT);
    String cat = category == null ? "Startup" : category.trim();
    String msg = detail == null ? "" : detail.trim();
    return "[" + time + "] [" + lv + "] " + cat + " - " + msg;
  }

  private static void startupSleep(long millis) {
    try {
      Thread.sleep(Math.max(0L, millis));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private static void logSplash(StartupSplashOverlay splash, String level, String category, String detail) {
    if (splash == null) return;
    splash.appendLog(startupLogLine(level, category, detail));
  }

  private void showStartupLaunchProgress(StartupSplashOverlay splash) {
    logSplash(splash, "INFO", "Launch", "Startup checks passed; launching editor immediately.");
    splash.showLaunchingEditor();
  }

  private void collectGitSetupIssues(File workspace,
                                     StartupSplashOverlay splash,
                                     List<EditorWorkspaceHubView.SetupIssue> issues) {
    QuickCommandResult gitVersion = runQuickCommand(workspace, List.of("git", "--version"), 1_000L);
    if (!gitVersion.success()) {
      issues.add(new EditorWorkspaceHubView.SetupIssue(
          "warn",
          "Git command not available",
          "Git was not found from the editor process. Version Control and update workflows may fail.",
          "Install Git or launch the editor from an environment where git is on PATH."));
      logSplash(splash, "WARN", "Git", "git --version failed: " + gitVersion.summary());
      return;
    }
    logSplash(splash, "OK", "Git", gitVersion.summary());

    QuickCommandResult name = runQuickCommand(workspace, List.of("git", "config", "--global", "--get", "user.name"), 800L);
    QuickCommandResult email = runQuickCommand(workspace, List.of("git", "config", "--global", "--get", "user.email"), 800L);
    boolean missingName = !name.success() || name.output().isBlank();
    boolean missingEmail = !email.success() || email.output().isBlank();
    if (missingName || missingEmail) {
      issues.add(new EditorWorkspaceHubView.SetupIssue(
          "warn",
          "Git identity is incomplete",
          "Commits need a global user.name and user.email. Current name: "
              + (missingName ? "(missing)" : name.output())
              + ", email: " + (missingEmail ? "(missing)" : email.output()) + ".",
          "Run: git config --global user.name \"Your Name\" && git config --global user.email \"you@example.com\""));
      logSplash(splash, "WARN", "Git", "Global Git identity incomplete.");
    } else {
      logSplash(splash, "OK", "Git", "Global identity: " + name.output() + " <" + email.output() + ">");
    }
  }

  private void collectGitHubSetupIssues(File workspace,
                                        StartupSplashOverlay splash,
                                        List<EditorWorkspaceHubView.SetupIssue> issues) {
    QuickCommandResult ghVersion = runQuickCommand(workspace, List.of("gh", "--version"), 1_000L);
    if (!ghVersion.success()) {
      issues.add(new EditorWorkspaceHubView.SetupIssue(
          "info",
          "GitHub CLI is not available",
          "GitHub auth, PR helpers, and repository publishing need the gh command.",
          "Install GitHub CLI, then run: gh auth login"));
      logSplash(splash, "INFO", "GitHub", "gh command not found or not runnable.");
      return;
    }

    QuickCommandResult auth = runQuickCommand(workspace, List.of("gh", "auth", "status"), 1_500L);
    if (!auth.success()) {
      issues.add(new EditorWorkspaceHubView.SetupIssue(
          "warn",
          "GitHub CLI is not authenticated",
          "gh is installed but no authenticated GitHub session was detected.",
          "Run: gh auth login"));
      logSplash(splash, "WARN", "GitHub", "gh auth status failed: " + auth.summary());
    } else {
      logSplash(splash, "OK", "GitHub", "gh auth status is ready.");
    }
  }

  private QuickCommandResult runQuickCommand(File workspace, List<String> command, long timeoutMs) {
    if (command == null || command.isEmpty()) {
      return new QuickCommandResult(-1, "No command");
    }
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      if (workspace != null && workspace.isDirectory()) pb.directory(workspace);
      pb.redirectErrorStream(true);
      Process process = pb.start();
      boolean finished = process.waitFor(Math.max(250L, timeoutMs), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        return new QuickCommandResult(-1, "Timed out");
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      return new QuickCommandResult(process.exitValue(), output);
    } catch (Exception ex) {
      return new QuickCommandResult(-1, safeMessage(ex));
    }
  }

  private static String safeMessage(Throwable throwable) {
    if (throwable == null) return "Unknown startup error";
    String message = throwable.getMessage();
    if (message == null || message.isBlank()) return throwable.getClass().getSimpleName();
    return message.trim();
  }

  private static void ensureWritableStateDir(Path stateDir) {
    try {
      Files.createDirectories(stateDir);
      Path probe = stateDir.resolve(".startup-health-probe");
      Files.writeString(probe, "ok-" + System.nanoTime());
      Files.deleteIfExists(probe);
    } catch (Exception ex) {
      throw new StartupFailure(
          "State directory is not writable",
          "Unable to write under " + stateDir.toAbsolutePath() + ": " + safeMessage(ex),
          ex);
    }
  }

  private int readRequiredJavaVersion(Path root) {
    String raw = readGradleProperty(root, "javaVersion");
    if (raw == null || raw.isBlank()) return -1;
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception ignore) {
      return -1;
    }
  }

  private String readGradleProperty(Path root, String key) {
    if (root == null || key == null || key.isBlank()) return null;
    Path propsPath = root.resolve("gradle.properties");
    if (!Files.isRegularFile(propsPath)) return null;
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(propsPath)) {
      props.load(in);
      return props.getProperty(key);
    } catch (Exception ignore) {
      return null;
    }
  }

  private String readGradleWrapperVersion(Path wrapperProps) {
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(wrapperProps)) {
      props.load(in);
      String distributionUrl = props.getProperty("distributionUrl", "");
      int gradleIndex = distributionUrl.indexOf("gradle-");
      int zipIndex = distributionUrl.indexOf(".zip");
      if (gradleIndex < 0 || zipIndex <= gradleIndex) return "unknown";
      String version = distributionUrl.substring(gradleIndex + "gradle-".length(), zipIndex);
      if (version.endsWith("-bin")) version = version.substring(0, version.length() - 4);
      if (version.endsWith("-all")) version = version.substring(0, version.length() - 4);
      return version;
    } catch (Exception ignore) {
      return "unknown";
    }
  }

  private int parseJavaMajor(String raw) {
    if (raw == null || raw.isBlank()) return -1;
    String trimmed = raw.trim();
    if (trimmed.startsWith("1.")) {
      trimmed = trimmed.substring(2);
    }
    int dot = trimmed.indexOf('.');
    int dash = trimmed.indexOf('-');
    int end = trimmed.length();
    if (dot >= 0) end = Math.min(end, dot);
    if (dash >= 0) end = Math.min(end, dash);
    try {
      return Integer.parseInt(trimmed.substring(0, end));
    } catch (Exception ex) {
      return -1;
    }
  }

  private static boolean isWindowsOs() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private record QuickCommandResult(int exitCode, String output) {
    boolean success() {
      return exitCode == 0;
    }

    public String output() {
      return output == null ? "" : output.strip();
    }

    String summary() {
      String text = output();
      if (text.isBlank()) return "exit " + exitCode;
      String firstLine = text.split("\\R", 2)[0].trim();
      return firstLine.isBlank() ? "exit " + exitCode : firstLine;
    }
  }

  private static String resolveGradleCommand(File workspace) {
    File gradlew = new File(workspace, isWindowsOs() ? "gradlew.bat" : "gradlew");
    if (gradlew.isFile()) {
      return gradlew.getAbsolutePath();
    }
    return "gradle";
  }

  private List<String> commonGradleStartupCommand(File workspace) {
    File gradleUserHome = new File(workspace, ".jvn-gradle-user-home");
    if (!gradleUserHome.exists()) {
      gradleUserHome.mkdirs();
    }
    String runtimeJavaHome = System.getProperty("java.home", "").trim();
    List<String> cmd = new ArrayList<>();
    cmd.add(resolveGradleCommand(workspace));
    cmd.add("--no-daemon");
    cmd.add("--console=plain");
    cmd.add("--gradle-user-home");
    cmd.add(gradleUserHome.getAbsolutePath());
    cmd.add("-Dorg.gradle.vfs.watch=false");
    if (!runtimeJavaHome.isEmpty()) {
      cmd.add("-Dorg.gradle.java.home=" + runtimeJavaHome);
      cmd.add("-PjvnNativeJavaHome=" + runtimeJavaHome);
    }
    return cmd;
  }

  private void runGradleStartupProcess(File workspace,
                                       StartupSplashOverlay splash,
                                       String category,
                                       List<String> taskArgs,
                                       String failureSummary,
                                       String failureDetail) {
    List<String> cmd = commonGradleStartupCommand(workspace);
    cmd.addAll(taskArgs);
    runStartupProcess(workspace, splash, category, cmd, failureSummary, failureDetail);
  }

  private void runStartupProcess(File workspace,
                                 StartupSplashOverlay splash,
                                 String category,
                                 List<String> command,
                                 String failureSummary,
                                 String failureDetail) {
    logSplash(splash, "INFO", category, "Running: " + String.join(" ", command));
    Deque<String> tail = new ArrayDeque<>();
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.directory(workspace);
      pb.redirectErrorStream(true);
      if (workspace != null) {
        File gradleUserHome = new File(workspace, ".jvn-gradle-user-home");
        pb.environment().put("GRADLE_USER_HOME", gradleUserHome.getAbsolutePath());
      }
      String runtimeJavaHome = System.getProperty("java.home", "").trim();
      if (!runtimeJavaHome.isEmpty()) {
        pb.environment().put("JAVA_HOME", runtimeJavaHome);
      }
      Process process = pb.start();
      try (BufferedReader reader =
               new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line == null ? "" : line.trim();
          if (trimmed.isBlank()) continue;
          rememberStartupTail(tail, trimmed);
          if (STARTUP_PROCESS_NOISE.matcher(trimmed).matches()) continue;
          logSplash(splash, classifyStartupLine(trimmed), category, trimmed);
        }
      }
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        String detail = failureDetail;
        if (!tail.isEmpty()) {
          detail = (detail == null ? "" : detail + " ")
              + "Recent output: " + String.join(" | ", tail);
        }
        throw new StartupFailure(failureSummary, detail == null ? "Command failed." : detail.trim());
      }
    } catch (StartupFailure ex) {
      throw ex;
    } catch (Exception ex) {
      String detail = (failureDetail == null ? "" : failureDetail + " ")
          + safeMessage(ex);
      throw new StartupFailure(failureSummary, detail.trim(), ex);
    }
  }

  private static void rememberStartupTail(Deque<String> tail, String line) {
    if (line == null || line.isBlank()) return;
    while (tail.size() >= STARTUP_COMMAND_TAIL_LINES) {
      tail.removeFirst();
    }
    tail.addLast(line.trim());
  }

  private static String classifyStartupLine(String line) {
    String normalized = line == null ? "" : line.toLowerCase(Locale.ROOT);
    if (normalized.contains("error") || normalized.contains("failed") || normalized.contains("exception")) {
      return "ERROR";
    }
    if (normalized.contains("warn") || normalized.contains("deprecated")) {
      return "WARN";
    }
    return "INFO";
  }

  private static final class StartupFailure extends RuntimeException {
    private final String summary;
    private final String detail;

    private StartupFailure(String summary, String detail) {
      super((summary == null ? "Startup failure" : summary) + ": " + (detail == null ? "" : detail));
      this.summary = summary == null ? "Startup failure" : summary;
      this.detail = detail == null ? "" : detail;
    }

    private StartupFailure(String summary, String detail, Throwable cause) {
      super((summary == null ? "Startup failure" : summary) + ": " + (detail == null ? "" : detail), cause);
      this.summary = summary == null ? "Startup failure" : summary;
      this.detail = detail == null ? "" : detail;
    }

    private String summary() {
      return summary;
    }

    private String detail() {
      return detail;
    }
  }

  private void initializeEditorStage(Stage primaryStage) {
    primaryStage.setTitle("JVN Editor");
    URL editorIcon = getClass().getResource("/com/jvn/editor/images/jvn_editor_icon.png");
    if (editorIcon != null) primaryStage.getIcons().add(new Image(editorIcon.toExternalForm()));
    editorPreferencesStore = new EditorPreferencesStore();
    editorPreferences = SAFE_MODE ? EditorPreferences.defaults() : editorPreferencesStore.load();
    layoutStudioWindowManager = new LayoutStudioWindowManager(primaryStage, this::doRunProject);
    BorderPane root = new BorderPane();
    AppBuildInfo.BuildInfo buildInfo = AppBuildInfo.resolve(EditorApp.class);
    reloadPlugins(projectRoot);

    // Menu
    MenuBar mb = new MenuBar();
    mb.setUseSystemMenuBar(false);
    mb.setFocusTraversable(false);
    Menu menuFile = new Menu("File");
    MenuItem miNewProject = new MenuItem("New Project...");
    miNewProject.setOnAction(e -> doNewProject(primaryStage));
    MenuItem miOpenProject = new MenuItem("Open Project...");
    miOpenProject.setOnAction(e -> doOpenProject(primaryStage));
    MenuItem miOpen = new MenuItem("Open JES...");
    miOpen.setOnAction(e -> doOpen(primaryStage));
    MenuItem miOpenVns = new MenuItem("Open VNS...");
    miOpenVns.setOnAction(e -> doOpenVns(primaryStage));
    MenuItem miOpenTextFile = new MenuItem("Open Text File...");
    miOpenTextFile.setOnAction(e -> doOpenTextFile(primaryStage));
    MenuItem miCloseTab = new MenuItem("Close Tab");
    miCloseTab.setOnAction(e -> closeActiveTab());
    MenuItem miSave = new MenuItem("Save");
    miSave.setOnAction(e -> doSave(primaryStage));
    MenuItem miSaveAs = new MenuItem("Save As...");
    miSaveAs.setOnAction(e -> doSaveAs(primaryStage));
    MenuItem miSaveAllTabs = new MenuItem("Save All Open Tabs");
    miSaveAllTabs.setOnAction(e -> saveAllOpenTabs());
    miOpenProject.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
    miOpen.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    miOpenVns.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN));
    miOpenTextFile.setAccelerator(new KeyCodeCombination(
        KeyCode.O,
        KeyCombination.SHORTCUT_DOWN,
        KeyCombination.SHIFT_DOWN,
        KeyCombination.ALT_DOWN));
    miSave.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
    miSaveAs.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    miSaveAllTabs.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN));
    miCloseTab.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
    MenuItem miCloseAllTabs = new MenuItem("Close All Tabs");
    miCloseAllTabs.setOnAction(e -> closeAllClosableTabs());
    miCloseAllTabs.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    MenuItem miRevealActiveFile = new MenuItem("Reveal Active File in JVN Path Explorer");
    miRevealActiveFile.setOnAction(e -> revealActiveFileInPathExplorer());
    MenuItem miCopyActiveFilePath = new MenuItem("Copy Active File Path");
    miCopyActiveFilePath.setOnAction(e -> copyActiveFilePathToClipboard());
    MenuItem miFileWelcome = new MenuItem("Workspace Hub");
    miFileWelcome.setOnAction(e -> selectWorkspaceHubTab());
    MenuItem miFileProjectExplorer = new MenuItem("Project Explorer");
    miFileProjectExplorer.setOnAction(e -> selectProjectTab());
    MenuItem miFileRunProject = new MenuItem("Run Project");
    miFileRunProject.setOnAction(e -> doRunProject(primaryStage));
    MenuItem miFileBuildPublish = new MenuItem("Build & Publish...");
    miFileBuildPublish.setOnAction(e -> showGameBuildPublisherWindow(primaryStage));
    MenuItem miFileRevealProjectRoot = new MenuItem("Reveal Project Root in JVN Path Explorer");
    miFileRevealProjectRoot.setOnAction(e -> revealProjectRootInPathExplorer());
    MenuItem miFileCopyProjectRoot = new MenuItem("Copy Project Root Path");
    miFileCopyProjectRoot.setOnAction(e -> copyProjectRootPathToClipboard());
    MenuItem miFileProjectDocs = new MenuItem("Open Project Docs Folder");
    miFileProjectDocs.setOnAction(e -> openProjectDocsFolder());
    MenuItem miFileWorkspaceDocs = new MenuItem("Open Workspace Docs Folder");
    miFileWorkspaceDocs.setOnAction(e -> openWorkspaceDocsFolder());
    MenuItem miExitEditor = new MenuItem("Exit JVN Editor");
    miExitEditor.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
    miExitEditor.setOnAction(e ->
        primaryStage.fireEvent(new javafx.stage.WindowEvent(primaryStage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));
    Menu menuFileOpen = new Menu("Open");
    menuFileOpen.getItems().addAll(
        miNewProject,
        miOpenProject,
        new SeparatorMenuItem(),
        miOpen,
        miOpenVns,
        miOpenTextFile);
    Menu menuFileSave = new Menu("Save");
    menuFileSave.getItems().addAll(miSave, miSaveAs, miSaveAllTabs);
    Menu menuFileCurrent = new Menu("Current File");
    menuFileCurrent.getItems().addAll(miRevealActiveFile, miCopyActiveFilePath);
    Menu menuFileClose = new Menu("Close");
    menuFileClose.getItems().addAll(miCloseTab, miCloseAllTabs);
    Menu menuFileProject = new Menu("Project");
    menuFileProject.getItems().addAll(
        miFileWelcome,
        miFileProjectExplorer,
        new SeparatorMenuItem(),
        miFileRunProject,
        miFileBuildPublish,
        new SeparatorMenuItem(),
        miFileRevealProjectRoot,
        miFileCopyProjectRoot);
    Menu menuFileDocs = new Menu("Docs & Folders");
    menuFileDocs.getItems().addAll(miFileProjectDocs, miFileWorkspaceDocs);
    menuFile.getItems().addAll(
        menuFileOpen,
        new SeparatorMenuItem(),
        menuFileSave,
        new SeparatorMenuItem(),
        menuFileCurrent,
        menuFileProject,
        menuFileDocs,
        new SeparatorMenuItem(),
        menuFileClose,
        new SeparatorMenuItem(),
        miExitEditor);

    // ── Edit ──
    Menu menuEdit = new Menu("Edit");
    MenuItem miUndo = new MenuItem("Undo");
    miUndo.setOnAction(e -> executeUndo());
    miUndo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
    MenuItem miRedo = new MenuItem("Redo");
    miRedo.setOnAction(e -> executeRedo());
    miRedo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    MenuItem miFind = new MenuItem("Find / Replace");
    miFind.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));
    miFind.setOnAction(e -> {
      FileEditorTab ft = getActiveFileTab();
      if (ft != null) ft.showSearchBar();
    });
    MenuItem miGoToLine = new MenuItem("Go to VNS Line...");
    miGoToLine.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN));
    miGoToLine.setOnAction(e -> {
      EditorDialogs.promptText(primaryStage, "Go to Line", "Jump to a specific line in the active VNS editor.",
          "Line number", "", "42", "Go").ifPresent(text -> {
        try {
          int line = Integer.parseInt(text.trim());
          FileEditorTab ft = getActiveFileTab();
          if (ft != null) ft.navigateToLine(line);
        } catch (NumberFormatException ignored) { // reason: malformed numeric text input; caller uses fallback value
        }
      });
    });
    MenuItem miReload = new MenuItem("Reload from Disk");
    miReload.setGraphic(RefreshIcon.of(16));
    miReload.setOnAction(e -> doReload());
    MenuItem miEditorSettings = new MenuItem("Editor Settings");
    miEditorSettings.setOnAction(e -> selectEditorSettingsTab());
    miEditorSettings.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN));
    MenuItem miEditApplyPreview = new MenuItem("Apply to Preview");
    miEditApplyPreview.setOnAction(e -> applyCodeFromEditor());
    MenuItem miEditResetCamera = new MenuItem("Reset Camera");
    miEditResetCamera.setOnAction(e -> resetCamera());
    MenuItem miEditFitContent = new MenuItem("Fit Content / Open Fullscreen Preview");
    miEditFitContent.setOnAction(e -> fitCameraToContent());
    MenuItem miEditRevealProjectRoot = new MenuItem("Reveal Project Root in JVN Path Explorer");
    miEditRevealProjectRoot.setOnAction(e -> revealProjectRootInPathExplorer());
    MenuItem miEditCopyProjectRootPath = new MenuItem("Copy Project Root Path");
    miEditCopyProjectRootPath.setOnAction(e -> copyProjectRootPathToClipboard());
    Menu menuEditSearch = new Menu("Search");
    menuEditSearch.getItems().addAll(miFind, miGoToLine);
    Menu menuEditDocument = new Menu("Document");
    menuEditDocument.getItems().addAll(miReload, miEditorSettings);
    Menu menuEditPaths = new Menu("Paths & Reveal");
    menuEditPaths.getItems().addAll(
        miEditRevealProjectRoot,
        miEditCopyProjectRootPath);
    Menu menuEditPreview = new Menu("Preview");
    menuEditPreview.getItems().addAll(miEditApplyPreview, miEditResetCamera, miEditFitContent);
    menuEdit.getItems().addAll(
        miUndo,
        miRedo,
        new SeparatorMenuItem(),
        menuEditSearch,
        menuEditPreview,
        menuEditPaths,
        new SeparatorMenuItem(),
        menuEditDocument);

    // ── View ──
    Menu menuView = new Menu("View");
    MenuItem miToggleEditorFullscreen = new MenuItem("Toggle Editor Fullscreen");
    miToggleEditorFullscreen.setOnAction(e -> toggleActiveEditorFullscreen());
    miToggleEditorFullscreen.setAccelerator(new KeyCodeCombination(KeyCode.F11));
    MenuItem miResetCamera = new MenuItem("Reset Camera");
    miResetCamera.setOnAction(e -> resetCamera());
    MenuItem miFitContent = new MenuItem("Fit Content / Open Fullscreen Preview");
    miFitContent.setOnAction(e -> fitCameraToContent());

    Menu menuPanels = new Menu("Panels");
    MenuItem miShowProject = new MenuItem("Project Explorer");
    miShowProject.setOnAction(e -> selectProjectTab());
    MenuItem miShowTrashman = new MenuItem("Trashman");
    miShowTrashman.setOnAction(e -> selectTrashmanTab());
    MenuItem miShowWelcomePanel = new MenuItem("Workspace Hub");
    miShowWelcomePanel.setOnAction(e -> selectWorkspaceHubTab());
    MenuItem miShowTimeline = new MenuItem("Story Map");
    miShowTimeline.setOnAction(e -> selectTimelineTab());
    MenuItem miShowInspector = new MenuItem("Inspector");
    miShowInspector.setOnAction(e -> selectInspectorTab());
    MenuItem miShowVersionControlPanel = new MenuItem("Version Control");
    miShowVersionControlPanel.setOnAction(e -> selectVersionControlTab());
    MenuItem miShowAssets = new MenuItem("Asset Browser");
    miShowAssets.setOnAction(e -> selectAssetBrowserTab());
    MenuItem miShowDiagnostics = new MenuItem("Diagnostics");
    miShowDiagnostics.setOnAction(e -> selectVnsDiagnosticsTab());
    MenuItem miShowFlowMap = new MenuItem("Label Flow Map");
    miShowFlowMap.setOnAction(e -> selectVnsFlowMapTab());
    MenuItem miShowLayoutLauncher = new MenuItem("Layout Launcher");
    miShowLayoutLauncher.setOnAction(e -> selectLayoutLauncherTab());
    MenuItem miShowStoryboardOverlay = new MenuItem("Storyboard Overlay");
    miShowStoryboardOverlay.setOnAction(e -> selectStoryboardOverlayTab());
    disableMaintenanceMenuItem(miShowStoryboardOverlay, EditorSidebarPanel.STORYBOARD_OVERLAY);
    MenuItem miShowLayeredVisualizer = new MenuItem("Layered Image Visualizer");
    miShowLayeredVisualizer.setOnAction(e -> selectLayeredImageVisualizerTab());
    MenuItem miShowImageAttributes = new MenuItem("Image Attributes Tool");
    miShowImageAttributes.setOnAction(e -> selectImageAttributesToolTab());
    disableMaintenanceMenuItem(miShowImageAttributes, EditorSidebarPanel.IMAGE_ATTRIBUTES);
    MenuItem miShowImageTint = new MenuItem("Scene Lighting Studio");
    miShowImageTint.setOnAction(e -> selectImageTintToolTab());
    MenuItem miShowPuppeteerLauncher = new MenuItem("Puppeteer Launcher");
    miShowPuppeteerLauncher.setOnAction(e -> selectPuppeteerLauncherTab());
    MenuItem miShowEditorSettings = new MenuItem("Editor Settings");
    miShowEditorSettings.setOnAction(e -> selectEditorSettingsTab());
    MenuItem miAddLeftPanel = new MenuItem("Add Left Panel...");
    miAddLeftPanel.setOnAction(e -> showLeftAddMenu());
    MenuItem miAddRightPanel = new MenuItem("Add Right Panel...");
    miAddRightPanel.setOnAction(e -> showRightAddMenu());
    Menu menuPanelsWorkspace = new Menu("Workspace");
    menuPanelsWorkspace.getItems().addAll(
        miShowWelcomePanel, miShowProject, miShowTrashman, miShowTimeline, miShowInspector, miShowVersionControlPanel);
    Menu menuPanelsAuthoring = new Menu("Authoring");
    menuPanelsAuthoring.getItems().addAll(
        miShowAssets, miShowPuppeteerLauncher);
    Menu menuPanelsAnalysis = new Menu("Analysis & Flow");
    menuPanelsAnalysis.getItems().addAll(miShowDiagnostics, miShowFlowMap, miShowLayoutLauncher);
    Menu menuPanelsVisual = new Menu("Visual Tools");
    menuPanelsVisual.getItems().addAll(
        miShowStoryboardOverlay, miShowLayeredVisualizer, miShowImageAttributes, miShowImageTint);
    Menu menuPanelsSettings = new Menu("Settings");
    menuPanelsSettings.getItems().addAll(miShowEditorSettings);
    menuPanels.getItems().addAll(
        menuPanelsWorkspace,
        menuPanelsAuthoring,
        menuPanelsAnalysis,
        menuPanelsVisual,
        menuPanelsSettings,
        new SeparatorMenuItem(),
        miAddLeftPanel,
        miAddRightPanel);

    Menu menuViewport = new Menu("Viewport");
    menuViewport.getItems().addAll(
        miToggleEditorFullscreen, miResetCamera, miFitContent,
        new SeparatorMenuItem(), CompositionGuideOverlay.createMenu());
    Menu menuTheme = new Menu("Theme");
    ToggleGroup themeToggleGroup = new ToggleGroup();
    RadioMenuItem miThemeDark = new RadioMenuItem("Dark");
    miThemeDark.setToggleGroup(themeToggleGroup);
    RadioMenuItem miThemeLight = new RadioMenuItem("Light");
    miThemeLight.setToggleGroup(themeToggleGroup);
    Runnable syncThemeMenuSelection = () -> {
      boolean lightSelected = EditorTheme.theme() == EditorTheme.Theme.LIGHT;
      miThemeDark.setSelected(!lightSelected);
      miThemeLight.setSelected(lightSelected);
    };
    syncThemeMenuSelection.run();
    miThemeDark.setOnAction(e -> {
      if (miThemeDark.isSelected()) setEditorTheme(EditorTheme.Theme.DARK);
    });
    miThemeLight.setOnAction(e -> {
      if (miThemeLight.isSelected()) setEditorTheme(EditorTheme.Theme.LIGHT);
    });
    menuTheme.getItems().addAll(miThemeDark, miThemeLight);
    menuView.getItems().addAll(menuViewport, menuTheme, new SeparatorMenuItem(), menuPanels);

    // ── Navigate ──
    Menu menuNavigate = new Menu("Navigate");
    Menu menuNavigateCore = new Menu("Core Panels");
    MenuItem miNavigateWelcome = new MenuItem("Workspace Hub");
    miNavigateWelcome.setOnAction(e -> selectWorkspaceHubTab());
    MenuItem miNavigateProject = new MenuItem("Project Explorer");
    miNavigateProject.setOnAction(e -> selectProjectTab());
    MenuItem miNavigateTrashman = new MenuItem("Trashman");
    miNavigateTrashman.setOnAction(e -> selectTrashmanTab());
    MenuItem miNavigateTimeline = new MenuItem("Story Map");
    miNavigateTimeline.setOnAction(e -> selectTimelineTab());
    MenuItem miNavigateInspector = new MenuItem("Inspector");
    miNavigateInspector.setOnAction(e -> selectInspectorTab());
    MenuItem miNavigateVersionControl = new MenuItem("Version Control");
    miNavigateVersionControl.setOnAction(e -> selectVersionControlTab());
    menuNavigateCore.getItems().addAll(
        miNavigateWelcome, miNavigateProject, miNavigateTrashman, miNavigateTimeline, miNavigateInspector, miNavigateVersionControl);

    Menu menuNavigateEditors = new Menu("Editors & Tools");
    MenuItem miNavigateAssetBrowser = new MenuItem("Asset Browser");
    miNavigateAssetBrowser.setOnAction(e -> selectAssetBrowserTab());
    MenuItem miNavigatePuppeteer = new MenuItem("Puppeteer Launcher");
    miNavigatePuppeteer.setOnAction(e -> selectPuppeteerLauncherTab());
    MenuItem miNavigateLayoutLauncher = new MenuItem("Layout Launcher");
    miNavigateLayoutLauncher.setOnAction(e -> selectLayoutLauncherTab());
    menuNavigateEditors.getItems().addAll(
        miNavigateAssetBrowser, miNavigatePuppeteer, miNavigateLayoutLauncher);

    Menu menuNavigateVisual = new Menu("Visual Tools");
    MenuItem miNavigateStoryboard = new MenuItem("Storyboard Overlay");
    miNavigateStoryboard.setOnAction(e -> selectStoryboardOverlayTab());
    disableMaintenanceMenuItem(miNavigateStoryboard, EditorSidebarPanel.STORYBOARD_OVERLAY);
    MenuItem miNavigateLayered = new MenuItem("Layered Image Visualizer");
    miNavigateLayered.setOnAction(e -> selectLayeredImageVisualizerTab());
    MenuItem miNavigateImageAttributes = new MenuItem("Image Attributes Tool");
    miNavigateImageAttributes.setOnAction(e -> selectImageAttributesToolTab());
    disableMaintenanceMenuItem(miNavigateImageAttributes, EditorSidebarPanel.IMAGE_ATTRIBUTES);
    MenuItem miNavigateImageTint = new MenuItem("Scene Lighting Studio");
    miNavigateImageTint.setOnAction(e -> selectImageTintToolTab());
    menuNavigateVisual.getItems().addAll(
        miNavigateStoryboard, miNavigateLayered, miNavigateImageAttributes, miNavigateImageTint);

    Menu menuNavigateAnalysis = new Menu("Analysis");
    MenuItem miNavigateDiagnostics = new MenuItem("Diagnostics");
    miNavigateDiagnostics.setOnAction(e -> selectVnsDiagnosticsTab());
    MenuItem miNavigateFlowMap = new MenuItem("Label Flow Map");
    miNavigateFlowMap.setOnAction(e -> selectVnsFlowMapTab());
    MenuItem miNavigateSettings = new MenuItem("Editor Settings");
    miNavigateSettings.setOnAction(e -> selectEditorSettingsTab());
	    menuNavigateAnalysis.getItems().addAll(miNavigateDiagnostics, miNavigateFlowMap, miNavigateSettings);
	    menuNavigate.getItems().addAll(menuNavigateCore, menuNavigateEditors, menuNavigateVisual, menuNavigateAnalysis);

	    // ── Project ──
	    Menu menuProjectTop = new Menu("Project");
	    MenuItem miProjectWorkspaceHub = new MenuItem("Workspace Hub");
	    miProjectWorkspaceHub.setOnAction(e -> selectWorkspaceHubTab());
	    MenuItem miProjectNew = new MenuItem("New Project...");
	    miProjectNew.setOnAction(e -> doNewProject(primaryStage));
	    MenuItem miProjectOpen = new MenuItem("Open Project...");
	    miProjectOpen.setOnAction(e -> doOpenProject(primaryStage));
	    MenuItem miProjectExplorer = new MenuItem("Project Explorer");
	    miProjectExplorer.setOnAction(e -> selectProjectTab());
	    MenuItem miProjectRun = new MenuItem("Run Project");
	    miProjectRun.setOnAction(e -> doRunProject(primaryStage));
	    MenuItem miProjectBuildPublish = new MenuItem("Build & Publish...");
	    miProjectBuildPublish.setOnAction(e -> showGameBuildPublisherWindow(primaryStage));
	    MenuItem miProjectRevealRoot = new MenuItem("Reveal Project Root in JVN Path Explorer");
	    miProjectRevealRoot.setOnAction(e -> revealProjectRootInPathExplorer());
	    MenuItem miProjectCopyRoot = new MenuItem("Copy Project Root Path");
	    miProjectCopyRoot.setOnAction(e -> copyProjectRootPathToClipboard());
	    MenuItem miProjectDocs = new MenuItem("Open Project Docs Folder");
	    miProjectDocs.setOnAction(e -> openProjectDocsFolder());
	    MenuItem miProjectWorkspaceDocs = new MenuItem("Open Workspace Docs Folder");
	    miProjectWorkspaceDocs.setOnAction(e -> openWorkspaceDocsFolder());
	    Menu menuProjectOpenTop = new Menu("Open");
	    menuProjectOpenTop.getItems().addAll(miProjectWorkspaceHub, miProjectNew, miProjectOpen, miProjectExplorer);
	    Menu menuProjectActions = new Menu("Actions");
	    menuProjectActions.getItems().addAll(miProjectRun, miProjectBuildPublish);
	    Menu menuProjectLocations = new Menu("Locations");
	    menuProjectLocations.getItems().addAll(miProjectRevealRoot, miProjectCopyRoot, miProjectDocs, miProjectWorkspaceDocs);
	    menuProjectTop.getItems().addAll(menuProjectOpenTop, menuProjectActions, menuProjectLocations);

	    // ── Text ──
	    Menu menuText = new Menu("Text");
	    MenuItem miTextOpen = new MenuItem("Open Text File...");
	    miTextOpen.setOnAction(e -> doOpenTextFile(primaryStage));
	    MenuItem miTextSave = new MenuItem("Save Active File");
	    miTextSave.setOnAction(e -> doSave(primaryStage));
	    MenuItem miTextSaveAs = new MenuItem("Save Active File As...");
	    miTextSaveAs.setOnAction(e -> doSaveAs(primaryStage));
	    MenuItem miTextSaveAll = new MenuItem("Save All Open Tabs");
	    miTextSaveAll.setOnAction(e -> saveAllOpenTabs());
	    MenuItem miTextReload = new MenuItem("Reload Active File from Disk");
	    miTextReload.setGraphic(RefreshIcon.of(16));
	    miTextReload.setOnAction(e -> doReload());
	    MenuItem miTextClose = new MenuItem("Close Active Tab");
	    miTextClose.setOnAction(e -> closeActiveTab());
	    MenuItem miTextCloseAll = new MenuItem("Close All Tabs");
	    miTextCloseAll.setOnAction(e -> closeAllClosableTabs());
	    MenuItem miTextUndo = new MenuItem("Undo");
	    miTextUndo.setOnAction(e -> executeUndo());
	    MenuItem miTextRedo = new MenuItem("Redo");
	    miTextRedo.setOnAction(e -> executeRedo());
	    MenuItem miTextFind = new MenuItem("Find / Replace");
	    miTextFind.setOnAction(e -> {
	      FileEditorTab ft = getActiveFileTab();
	      if (ft != null) ft.showSearchBar();
	    });
	    MenuItem miTextGoToLine = new MenuItem("Go to VNS Line...");
	    miTextGoToLine.setOnAction(e -> {
	      EditorDialogs.promptText(primaryStage, "Go to Line", "Jump to a specific line in the active VNS editor.",
	          "Line number", "", "42", "Go").ifPresent(text -> {
	        try {
	          int line = Integer.parseInt(text.trim());
	          FileEditorTab ft = getActiveFileTab();
	          if (ft != null) ft.navigateToLine(line);
	        } catch (NumberFormatException ignored) {
	        }
	      });
	    });
	    MenuItem miTextRevealActive = new MenuItem("Reveal Active File in JVN Path Explorer");
	    miTextRevealActive.setOnAction(e -> revealActiveFileInPathExplorer());
	    MenuItem miTextCopyActive = new MenuItem("Copy Active File Path");
	    miTextCopyActive.setOnAction(e -> copyActiveFilePathToClipboard());
	    Menu menuTextFile = new Menu("File");
	    menuTextFile.getItems().addAll(miTextOpen, miTextSave, miTextSaveAs, miTextSaveAll, new SeparatorMenuItem(), miTextReload, miTextClose, miTextCloseAll);
	    Menu menuTextEdit = new Menu("Editing");
	    menuTextEdit.getItems().addAll(miTextUndo, miTextRedo, new SeparatorMenuItem(), miTextFind, miTextGoToLine);
	    Menu menuTextPaths = new Menu("Paths");
	    menuTextPaths.getItems().addAll(miTextRevealActive, miTextCopyActive);
	    menuText.getItems().addAll(menuTextFile, menuTextEdit, menuTextPaths);

	    // ── VNS ──
	    Menu menuVns = new Menu("VNS");
	    MenuItem miVnsOpen = new MenuItem("Open VNS...");
	    miVnsOpen.setOnAction(e -> doOpenVns(primaryStage));
	    MenuItem miVnsApplyPreview = new MenuItem("Apply to Preview");
	    miVnsApplyPreview.setOnAction(e -> applyCodeFromEditor());
	    MenuItem miVnsLaunchHere = new MenuItem("Run from Cursor");
	    miVnsLaunchHere.setOnAction(e -> {
	      FileEditorTab ft = getActiveFileTab();
	      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
	        ft.launchFromCursor();
	      } else {
	        status.setText("Run from Cursor is only available for VNS files");
	      }
	    });
	    MenuItem miVnsLaunchStart = new MenuItem("Launch from Start");
	    miVnsLaunchStart.setOnAction(e -> {
	      FileEditorTab ft = getActiveFileTab();
	      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
	        ft.runFromLabel(null);
	      } else {
	        status.setText("Launch from Start is only available for VNS files");
	      }
	    });
	    MenuItem miVnsApplyAndLaunchHere = new MenuItem("Apply Preview and Run from Cursor");
	    miVnsApplyAndLaunchHere.setOnAction(e -> {
	      applyCodeFromEditor();
	      FileEditorTab ft = getActiveFileTab();
	      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
	        ft.launchFromCursor();
	      } else {
	        status.setText("Apply + Run from Cursor is only available for VNS files");
	      }
	    });
	    MenuItem miVnsApplyAndLaunchStart = new MenuItem("Apply Preview and Launch From Start");
	    miVnsApplyAndLaunchStart.setOnAction(e -> {
	      applyCodeFromEditor();
	      FileEditorTab ft = getActiveFileTab();
	      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
	        ft.runFromLabel(null);
	      } else {
	        status.setText("Apply + Launch Start is only available for VNS files");
	      }
	    });
	    MenuItem miVnsDiagnostics = new MenuItem("Diagnostics");
	    miVnsDiagnostics.setOnAction(e -> selectVnsDiagnosticsTab());
	    MenuItem miVnsFlowMap = new MenuItem("Label Flow Map");
	    miVnsFlowMap.setOnAction(e -> selectVnsFlowMapTab());
	    MenuItem miVnsPuppeteerLauncher = new MenuItem("Puppeteer Launcher");
	    miVnsPuppeteerLauncher.setOnAction(e -> selectPuppeteerLauncherTab());
	    MenuItem miVnsPuppeteerWindow = new MenuItem("Puppeteer Window");
	    miVnsPuppeteerWindow.setOnAction(e -> openActionEditor());
	    MenuItem miVnsStoryboard = new MenuItem("Storyboard Overlay");
	    miVnsStoryboard.setOnAction(e -> selectStoryboardOverlayTab());
	    disableMaintenanceMenuItem(miVnsStoryboard, EditorSidebarPanel.STORYBOARD_OVERLAY);
	    Menu menuVnsPreview = new Menu("Preview & Launch");
	    menuVnsPreview.getItems().addAll(miVnsApplyPreview, miVnsLaunchHere, miVnsLaunchStart, new SeparatorMenuItem(), miVnsApplyAndLaunchHere, miVnsApplyAndLaunchStart);
	    Menu menuVnsAnalysis = new Menu("Analysis");
	    menuVnsAnalysis.getItems().addAll(miVnsDiagnostics, miVnsFlowMap);
	    Menu menuVnsAnimation = new Menu("Animation");
	    menuVnsAnimation.getItems().addAll(miVnsPuppeteerLauncher, miVnsPuppeteerWindow, miVnsStoryboard);
	    menuVns.getItems().addAll(miVnsOpen, new SeparatorMenuItem(), menuVnsPreview, menuVnsAnalysis, menuVnsAnimation);

	    // ── Assets ──
	    Menu menuAssetsTop = new Menu("Assets");
	    MenuItem miAssetsAutoLabel = new MenuItem("Auto-label Assets...");
	    miAssetsAutoLabel.setOnAction(e -> selectAssetAutoLabelDashboard());
	    MenuItem miAssetsBrowser = new MenuItem("Asset Browser");
	    miAssetsBrowser.setOnAction(e -> selectAssetBrowserTab());
	    MenuItem miAssetsLayered = new MenuItem("Layered Image Visualizer");
	    miAssetsLayered.setOnAction(e -> selectLayeredImageVisualizerTab());
	    MenuItem miAssetsAttributes = new MenuItem("Image Attributes Tool");
	    miAssetsAttributes.setOnAction(e -> selectImageAttributesToolTab());
	    disableMaintenanceMenuItem(miAssetsAttributes, EditorSidebarPanel.IMAGE_ATTRIBUTES);
	    MenuItem miAssetsTint = new MenuItem("Scene Lighting Studio");
	    miAssetsTint.setOnAction(e -> selectImageTintToolTab());
	    MenuItem miAssetsRevealRoot = new MenuItem("Reveal Project Root in JVN Path Explorer");
	    miAssetsRevealRoot.setOnAction(e -> revealProjectRootInPathExplorer());
	    MenuItem miAssetsProjectDocs = new MenuItem("Open Project Docs Folder");
	    miAssetsProjectDocs.setOnAction(e -> openProjectDocsFolder());
	    Menu menuAssetTools = new Menu("Tools");
	    menuAssetTools.getItems().addAll(miAssetsBrowser, miAssetsLayered, miAssetsAttributes, miAssetsTint);
	    Menu menuAssetLocations = new Menu("Locations");
	    menuAssetLocations.getItems().addAll(miAssetsRevealRoot, miAssetsProjectDocs);
	    menuAssetsTop.getItems().addAll(miAssetsAutoLabel, new SeparatorMenuItem(), menuAssetTools, menuAssetLocations);

	    // ── Diagnostics ──
	    Menu menuDiagnosticsTop = new Menu("Diagnostics");
	    MenuItem miDiagnosticsVns = new MenuItem("VNS Diagnostics");
	    miDiagnosticsVns.setOnAction(e -> selectVnsDiagnosticsTab());
	    MenuItem miDiagnosticsFlow = new MenuItem("Label Flow Map");
	    miDiagnosticsFlow.setOnAction(e -> selectVnsFlowMapTab());
	    MenuItem miDiagnosticsVersionControl = new MenuItem("Version Control Status");
	    miDiagnosticsVersionControl.setOnAction(e -> selectVersionControlTab());
	    MenuItem miDiagnosticsRefreshVcs = new MenuItem("Refresh Version Control Status");
	    miDiagnosticsRefreshVcs.setGraphic(RefreshIcon.of(16));
	    miDiagnosticsRefreshVcs.setOnAction(e -> {
	      if (versionControlView != null) versionControlView.refreshStatus();
	      selectVersionControlTab();
	    });
	    MenuItem miDiagnosticsSettings = new MenuItem("Editor Settings");
	    miDiagnosticsSettings.setOnAction(e -> selectEditorSettingsTab());
	    menuDiagnosticsTop.getItems().addAll(
	        miDiagnosticsVns,
	        miDiagnosticsFlow,
	        new SeparatorMenuItem(),
	        miDiagnosticsVersionControl,
	        miDiagnosticsRefreshVcs,
	        new SeparatorMenuItem(),
	        miDiagnosticsSettings);

	    // ── Run ──
	    Menu menuRun = new Menu("Run");
    MenuItem miApplyCode = new MenuItem("Apply to Preview");
    miApplyCode.setOnAction(e -> applyCodeFromEditor());
    miApplyCode.setAccelerator(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN));
    MenuItem miRunProject = new MenuItem("Run Project");
    miRunProject.setOnAction(e -> doRunProject(primaryStage));
    MenuItem miBuildPublishProject = new MenuItem("Build & Publish...");
    miBuildPublishProject.setOnAction(e -> showGameBuildPublisherWindow(primaryStage));
    MenuItem miLaunchHere = new MenuItem("Run VNS from Cursor");
    miLaunchHere.setAccelerator(new KeyCodeCombination(KeyCode.F5));
    miLaunchHere.setOnAction(e -> {
      FileEditorTab ft = getActiveFileTab();
      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
        ft.launchFromCursor();
      } else {
        status.setText("Run from Cursor is only available for VNS files");
      }
    });
    MenuItem miLaunchStart = new MenuItem("Launch VNS from Start");
    miLaunchStart.setAccelerator(new KeyCodeCombination(KeyCode.F5, KeyCombination.SHIFT_DOWN));
    miLaunchStart.setOnAction(e -> {
      FileEditorTab ft = getActiveFileTab();
      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
        ft.runFromLabel(null);
      } else {
        status.setText("Launch from Start is only available for VNS files");
      }
    });
    MenuItem miApplyAndLaunchHere = new MenuItem("Apply Preview and Run from Cursor");
    miApplyAndLaunchHere.setOnAction(e -> {
      applyCodeFromEditor();
      FileEditorTab ft = getActiveFileTab();
      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
        ft.launchFromCursor();
      } else {
        status.setText("Apply + Run from Cursor is only available for VNS files");
      }
    });
    MenuItem miApplyAndLaunchStart = new MenuItem("Apply Preview and Launch From Start");
    miApplyAndLaunchStart.setOnAction(e -> {
      applyCodeFromEditor();
      FileEditorTab ft = getActiveFileTab();
      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
        ft.runFromLabel(null);
      } else {
        status.setText("Apply + Launch Start is only available for VNS files");
      }
    });
    Menu menuRunPreview = new Menu("Preview");
    menuRunPreview.getItems().addAll(miApplyCode, miApplyAndLaunchHere, miApplyAndLaunchStart);
    Menu menuRunLaunchVns = new Menu("Launch Active VNS");
    menuRunLaunchVns.getItems().addAll(miLaunchHere, miLaunchStart);
    menuRun.getItems().addAll(
        menuRunPreview,
        new SeparatorMenuItem(),
        miRunProject,
        miBuildPublishProject,
        new SeparatorMenuItem(),
        menuRunLaunchVns);

    // ── Build ──
    Menu menuBuild = new Menu("Build");
    MenuItem miBuildPublishMain = new MenuItem("Build & Publish...");
    miBuildPublishMain.setOnAction(e -> showGameBuildPublisherWindow(primaryStage));
    menuBuild.getItems().add(miBuildPublishMain);

    // ── Tools ──
    Menu menuTools = new Menu("Tools");
    pluginToolsMenu = new Menu("Plugins");
    refreshPluginToolsMenu();
    MenuItem miActionEditor = new MenuItem("Puppeteer (Window)");
    miActionEditor.setOnAction(e -> openActionEditor());
    miActionEditor.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    MenuItem miPuppeteerPanel = new MenuItem("Puppeteer Launcher");
    miPuppeteerPanel.setOnAction(e -> selectPuppeteerLauncherTab());
    MenuItem miLayoutLauncher = new MenuItem("Layout Launcher");
    miLayoutLauncher.setOnAction(e -> selectLayoutLauncherTab());
    MenuItem miStoryboardOverlay = new MenuItem("Storyboard Overlay");
    miStoryboardOverlay.setOnAction(e -> selectStoryboardOverlayTab());
    disableMaintenanceMenuItem(miStoryboardOverlay, EditorSidebarPanel.STORYBOARD_OVERLAY);
    MenuItem miLayeredVisualizer = new MenuItem("Layered Image Visualizer");
    miLayeredVisualizer.setOnAction(e -> selectLayeredImageVisualizerTab());
    MenuItem miImageAttributes = new MenuItem("Image Attributes Tool");
    miImageAttributes.setOnAction(e -> selectImageAttributesToolTab());
    disableMaintenanceMenuItem(miImageAttributes, EditorSidebarPanel.IMAGE_ATTRIBUTES);
    MenuItem miImageTint = new MenuItem("Scene Lighting Studio");
    miImageTint.setOnAction(e -> selectImageTintToolTab());

    Menu menuVnsTools = new Menu("Analysis");
    MenuItem miToolDiagnostics = new MenuItem("Diagnostics");
    miToolDiagnostics.setOnAction(e -> selectVnsDiagnosticsTab());
    MenuItem miToolFlowMap = new MenuItem("Label Flow Map");
    miToolFlowMap.setOnAction(e -> selectVnsFlowMapTab());
    menuVnsTools.getItems().addAll(miToolDiagnostics, miToolFlowMap);

    MenuItem miToolAutoLabel = new MenuItem("Auto-label Assets...");
    miToolAutoLabel.setOnAction(e -> selectAssetAutoLabelDashboard());
    MenuItem miToolAssets = new MenuItem("Asset Browser");
    miToolAssets.setOnAction(e -> selectAssetBrowserTab());
    MenuItem miToolInspector = new MenuItem("Inspector");
    miToolInspector.setOnAction(e -> selectInspectorTab());
    MenuItem miToolEditorSettings = new MenuItem("Editor Settings");
    miToolEditorSettings.setOnAction(e -> selectEditorSettingsTab());
    MenuItem miToolVersionControl = new MenuItem("Version Control");
    miToolVersionControl.setOnAction(e -> selectVersionControlTab());

    Menu menuAnimationTools = new Menu("Animation");
    menuAnimationTools.getItems().addAll(miActionEditor, miPuppeteerPanel);
    Menu menuScriptTools = new Menu("Scripts & Analysis");
    menuScriptTools.getItems().add(menuVnsTools);
    Menu menuLayoutTools = new Menu("Layout & UI");
    menuLayoutTools.getItems().addAll(miLayoutLauncher, miStoryboardOverlay);
    Menu menuImageTools = new Menu("Image & Assets");
    menuImageTools.getItems().addAll(miToolAutoLabel, miToolAssets, miLayeredVisualizer, miImageAttributes, miImageTint);
    Menu menuWorkspaceTools = new Menu("Workspace");
    menuWorkspaceTools.getItems().addAll(miToolInspector, miToolVersionControl, miToolEditorSettings);
    menuTools.getItems().addAll(menuAnimationTools, menuScriptTools, menuLayoutTools, menuImageTools,
        menuWorkspaceTools, new SeparatorMenuItem(), pluginToolsMenu);

    // ── Version Control ──
    Menu menuVcs = new Menu("Version Control");
    MenuItem miOpenVcs = new MenuItem("Open Version Control");
    miOpenVcs.setOnAction(e -> selectVersionControlTab());
    miOpenVcs.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    MenuItem miRefreshVcs = new MenuItem("Refresh Status");
    miRefreshVcs.setGraphic(RefreshIcon.of(16));
    miRefreshVcs.setOnAction(e -> {
      if (versionControlView != null) versionControlView.refreshStatus();
      selectVersionControlTab();
    });
    MenuItem miRevealProjectRoot = new MenuItem("Reveal Project Root in JVN Path Explorer");
    miRevealProjectRoot.setOnAction(e -> revealProjectRootInPathExplorer());
    MenuItem miCopyProjectRootPath = new MenuItem("Copy Project Root Path");
    miCopyProjectRootPath.setOnAction(e -> copyProjectRootPathToClipboard());
    menuVcs.getItems().addAll(miOpenVcs, miRefreshVcs, new SeparatorMenuItem(), miRevealProjectRoot, miCopyProjectRootPath);

    // ── Window ──
    Menu menuWindow = new Menu("Window");
    MenuItem miBringMainWindowToFront = new MenuItem("Bring Main Window to Front");
    miBringMainWindowToFront.setOnAction(e -> {
      primaryStage.toFront();
      primaryStage.requestFocus();
    });
    MenuItem miCloseFloatingWindows = new MenuItem("Close All Floating Panel Windows");
    miCloseFloatingWindows.setOnAction(e -> closeAllFloatingPanelWindows());

    Menu menuWindowWorkspace = new Menu("Workspace Tabs");
    MenuItem miWindowWelcome = new MenuItem("Workspace Hub");
    miWindowWelcome.setOnAction(e -> selectWorkspaceHubTab());
    MenuItem miWindowProject = new MenuItem("Project Explorer");
    miWindowProject.setOnAction(e -> selectProjectTab());
    MenuItem miWindowTrashman = new MenuItem("Trashman");
    miWindowTrashman.setOnAction(e -> selectTrashmanTab());
    MenuItem miWindowTimeline = new MenuItem("Story Map");
    miWindowTimeline.setOnAction(e -> selectTimelineTab());
    MenuItem miWindowInspector = new MenuItem("Inspector");
    miWindowInspector.setOnAction(e -> selectInspectorTab());
    MenuItem miWindowVersionControl = new MenuItem("Version Control");
    miWindowVersionControl.setOnAction(e -> selectVersionControlTab());
    MenuItem miWindowSettings = new MenuItem("Editor Settings");
    miWindowSettings.setOnAction(e -> selectEditorSettingsTab());
    menuWindowWorkspace.getItems().addAll(
        miWindowWelcome,
        miWindowProject,
        miWindowTrashman,
        miWindowTimeline,
        miWindowInspector,
        miWindowVersionControl,
        miWindowSettings);

    Menu menuWindowTools = new Menu("Open Tool Window");
    MenuItem miWindowTimelineTool = new MenuItem("Story Map");
    miWindowTimelineTool.setOnAction(e ->
        launchPanelAsWindow("Story Map", ensureTimelineView(), 600, 700, EditorSidebarPanel.TIMELINE));
    MenuItem miWindowDiagnostics = new MenuItem("Diagnostics");
    miWindowDiagnostics.setOnAction(e ->
        launchPanelAsWindow("Diagnostics", ensureVnsDiagnosticsView(), 700, 600, EditorSidebarPanel.VNS_DIAGNOSTICS));
    MenuItem miWindowFlowMap = new MenuItem("Label Flow Map");
    miWindowFlowMap.setOnAction(e ->
        launchPanelAsWindow("Label Flow Map", ensureVnsFlowMapView(), 700, 600, EditorSidebarPanel.LABEL_FLOW));
    MenuItem miWindowTimelineOutline = new MenuItem("Timeline Outline");
    miWindowTimelineOutline.setOnAction(e ->
        launchPanelAsWindow("Timeline Outline", ensureVnsTimelineOutlineView(), 500, 600, EditorSidebarPanel.TIMELINE_OUTLINE));
    MenuItem miWindowAssets = new MenuItem("Asset Browser");
    miWindowAssets.setOnAction(e ->
        launchPanelAsWindow("Asset Browser", ensureAssetBrowserView(), 700, 600, EditorSidebarPanel.ASSETS));
    MenuItem miWindowVersionControlTool = new MenuItem("Version Control");
    miWindowVersionControlTool.setOnAction(e ->
        launchPanelAsWindow("Version Control", ensureVersionControlView(), 700, 600, EditorSidebarPanel.VERSION_CONTROL));
    MenuItem miWindowLayoutLauncher = new MenuItem("Layout Launcher");
    miWindowLayoutLauncher.setOnAction(e -> {
      LayoutEditorLauncherView view = ensureLayoutEditorLauncherView();
      if (view != null) view.refreshStatus();
      launchPanelAsWindow("Layout Launcher", view, 700, 700, EditorSidebarPanel.LAYOUT_LAUNCHER);
    });
    MenuItem miWindowStoryboard = new MenuItem("Storyboard Overlay");
    miWindowStoryboard.setOnAction(e -> {
      StoryboardOverlayView view = ensureStoryboardOverlayView();
      refreshStoryboardOverlayContext(getActiveFileTab());
      launchPanelAsWindow("Storyboard Overlay", view, 520, 780, EditorSidebarPanel.STORYBOARD_OVERLAY);
    });
    disableMaintenanceMenuItem(miWindowStoryboard, EditorSidebarPanel.STORYBOARD_OVERLAY);
    MenuItem miWindowLayered = new MenuItem("Layered Image Visualizer");
    miWindowLayered.setOnAction(e -> {
      LayeredImageVisualizerView view = ensureLayeredImageVisualizerView();
      if (view != null) view.refreshCatalog();
      launchPanelAsWindow("Layered Image Visualizer", view, 900, 700, EditorSidebarPanel.LAYERED_IMAGES);
    });
    MenuItem miWindowImageAttributes = new MenuItem("Image Attributes Tool");
    miWindowImageAttributes.setOnAction(e -> {
      ImageAttributesToolView view = ensureImageAttributesToolView();
      if (view != null) view.refreshCatalog();
      launchPanelAsWindow("Image Attributes Tool", wrapMaintenance(view), 800, 650, EditorSidebarPanel.IMAGE_ATTRIBUTES);
    });
    disableMaintenanceMenuItem(miWindowImageAttributes, EditorSidebarPanel.IMAGE_ATTRIBUTES);
    MenuItem miWindowImageTint = new MenuItem("Scene Lighting Studio");
    miWindowImageTint.setOnAction(e -> {
      ImageTintToolView view = ensureImageTintToolView();
      if (view != null) view.refreshCatalog();
      launchPanelAsWindow("Scene Lighting Studio", view, 800, 650, EditorSidebarPanel.IMAGE_TINT);
    });
    MenuItem miWindowPuppeteer = new MenuItem("Puppeteer Launcher");
    miWindowPuppeteer.setOnAction(e ->
        launchPanelAsWindow("Puppeteer Launcher", ensurePuppeteerLauncherPanel(), 600, 500, EditorSidebarPanel.PUPPETEER_LAUNCHER));
    MenuItem miWindowEditorSettings = new MenuItem("Editor Settings");
    miWindowEditorSettings.setOnAction(e ->
        launchPanelAsWindow("Editor Settings", ensureEditorSettingsView(), 520, 760, null));
    menuWindowTools.getItems().addAll(
        miWindowTimelineTool,
        miWindowDiagnostics,
        miWindowFlowMap,
        miWindowTimelineOutline,
        miWindowAssets,
        miWindowVersionControlTool,
        new SeparatorMenuItem(),
        miWindowLayoutLauncher,
        miWindowStoryboard,
        miWindowLayered,
        miWindowImageAttributes,
        miWindowImageTint,
        miWindowPuppeteer,
        miWindowEditorSettings);
    menuWindow.getItems().addAll(
        miBringMainWindowToFront,
        new SeparatorMenuItem(),
        menuWindowWorkspace,
        menuWindowTools,
        new SeparatorMenuItem(),
        miCloseFloatingWindows);

    // ── Help ──
    Menu menuHelp = new Menu("Help");
    MenuItem miWelcome = new MenuItem("Workspace Hub");
    miWelcome.setOnAction(e -> selectWorkspaceHubTab());
    miWelcome.setAccelerator(new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    MenuItem miOpenProjectDocs = new MenuItem("Open Project Docs Folder");
    miOpenProjectDocs.setOnAction(e -> openProjectDocsFolder());
    MenuItem miOpenWorkspaceDocs = new MenuItem("Open Workspace Docs Folder");
    miOpenWorkspaceDocs.setOnAction(e -> openWorkspaceDocsFolder());
    MenuItem miWhatsNew = new MenuItem("What's New in " + buildInfo.versionLabel());
    miWhatsNew.setOnAction(e -> showWhatsNew(primaryStage, buildInfo));
    MenuItem miAbout = new MenuItem("About JVN Editor");
    miAbout.setOnAction(e -> {
      EditorDialogs.show(primaryStage,
          "About JVN Editor",
          "JVN Editor " + buildInfo.fullLabel(),
          createDialogDetailLabel("Java Vector Nexus — Visual Novel & 2D Game Toolkit"),
          EditorDialogs.ActionSpec.accent("close", "Close", null));
    });
    menuHelp.getItems().addAll(
        miWelcome,
        new SeparatorMenuItem(),
        miOpenProjectDocs, miOpenWorkspaceDocs,
        new SeparatorMenuItem(),
        miWhatsNew,
        miAbout);

    // Blender-style application menu: the same scalable logo renderer used at top right.
    Menu menuJvn = new Menu("JVN");
    menuJvn.setGraphic(new MetallicJvnLogo(36, 18));
    menuJvn.getStyleClass().add("jvn-application-menu");
    MenuItem miJvnSplash = new MenuItem("Show JVN Splash");
    miJvnSplash.setOnAction(e -> {
      StartupSplashOverlay splash = new StartupSplashOverlay();
      splash.prepareForChecks(true);
      splash.setProgress(1.0);
      splash.setStatus("JVN Editor " + buildInfo.versionLabel());
      splash.show();
      PauseTransition closeSplash = new PauseTransition(Duration.seconds(2.4));
      closeSplash.setOnFinished(done -> splash.close());
      closeSplash.play();
    });
    MenuItem miJvnCopySystemInfo = new MenuItem("Copy System Information");
    miJvnCopySystemInfo.setOnAction(e -> {
      ClipboardContent content = new ClipboardContent();
      content.putString(String.join(System.lineSeparator(),
          "JVN Editor " + buildInfo.fullLabel(),
          "Java: " + System.getProperty("java.version", "unknown"),
          "JavaFX: " + System.getProperty("javafx.runtime.version", "unknown"),
          "OS: " + System.getProperty("os.name", "unknown") + " "
              + System.getProperty("os.version", "") + " (" + System.getProperty("os.arch", "unknown") + ")",
          "Graphics: " + EditorGraphicsStatus.detect().tooltip()));
      Clipboard.getSystemClipboard().setContent(content);
      status.setText("Copied JVN system information");
    });
    MenuItem miJvnDataFolder = new MenuItem("Open Editor Data Folder");
    miJvnDataFolder.setOnAction(e -> {
      Path preferencesFile = editorPreferencesStore.preferencesFile();
      Path dataDirectory = preferencesFile == null ? null : preferencesFile.getParent();
      if (dataDirectory != null) {
        try {
          Files.createDirectories(dataDirectory);
          EditorPathExplorer.show(primaryStage, dataDirectory.toFile());
        } catch (IOException ex) {
          EditorDialogs.error(primaryStage, "Editor Data Folder", "Could not open the editor data folder.", ex);
        }
      }
    });
    menuJvn.getItems().addAll(
        miJvnSplash,
        miJvnCopySystemInfo,
        miJvnDataFolder);

	    mb.getMenus().addAll(
	        menuJvn,
	        menuFile,
	        menuEdit,
	        menuView,
	        menuNavigate,
	        menuProjectTop,
	        menuText,
	        menuVns,
	        menuAssetsTop,
	        menuDiagnosticsTop,
	        menuRun,
	        menuBuild,
	        menuTools,
	        menuVcs,
	        menuWindow);
    if (DEVELOPER_MODE) {
      mb.getMenus().add(DeveloperToolsMenu.create(
          "JVN Editor",
          this::dialogOwner,
          this::refreshDeveloperLogs,
          this::developerLogRoots,
          true));
    }
    mb.getMenus().add(menuHelp);

    mb.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(mb, Priority.ALWAYS);
    HBox commandBar = new HBox(mb);
    commandBar.getStyleClass().add("main-editor-command-bar");
    commandBar.setAlignment(Pos.CENTER_LEFT);

    // Toolbar
    osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    BorderPane toolbar = new BorderPane();
    toolbar.getStyleClass().add("master-toolbar");
    statusBar = new JvnStatusBar("JVN Editor", buildInfo);
    statusBar.applyStatusBarPreferences(editorPreferences);
    statusBar.setProjectRoot(projectRoot);
    statusBar.setTheme(EditorTheme.theme());
    statusBar.setOnRevealProjectRoot(this::revealProjectRootInPathExplorer);
    statusBar.setOnCopyProjectRootPath(this::copyProjectRootPathToClipboard);
    statusBar.setOnRunProject(() -> doRunProject(primaryStage));
    statusBar.setOnOpenVersionControl(this::selectVersionControlTab);
    statusBar.setOnOpenSettings(this::selectEditorSettingsTab);
    statusBar.setOnRevealActiveFile(this::revealActiveFileInPathExplorer);
    statusBar.setOnCopyActiveFilePath(this::copyActiveFilePathToClipboard);
    statusBar.setOnSaveAll(this::saveAllOpenTabs);
    statusBar.setOnOpenDiagnostics(this::selectVnsDiagnosticsTab);
    status = statusBar.messageLabel();
    fps = new Label("");
    cpuChip = perfChip("CPU --", "perf-chip-cpu");
    heapChip = perfChip("Heap --", "perf-chip-heap");
    jvmChip = perfChip("JVM --", "perf-chip-jvm");
    fpsChip = perfChip("FPS --", "perf-chip-fps");
    graphicsStatus = EditorGraphicsStatus.detect();
    graphicsChip = perfChip(
        graphicsStatus.chipText(),
        graphicsStatus.hardwareAccelerated()
            ? "perf-chip-graphics"
            : "perf-chip-graphics-fallback");
    updatePerfChip(graphicsChip, graphicsStatus.chipText(), graphicsStatus.tooltip());
    threadsChip = perfChip("Threads --", "perf-chip-neutral");
    gcChip = perfChip("GC --", "perf-chip-neutral");
    javaChip = perfChip("Java --", "perf-chip-java");
    jdkChip = perfChip("JDK --", "perf-chip-java");
    gradleChip = perfChip("Gradle --", "perf-chip-build");
    projectShapeChip = perfChip("Project --", "perf-chip-build");
    perfChips = new FlowPane(6, 4,
        cpuChip,
        heapChip,
        jvmChip,
        fpsChip,
        graphicsChip,
        threadsChip,
        gcChip,
        javaChip,
        jdkChip,
        gradleChip,
        projectShapeChip);
    perfChips.getStyleClass().add("editor-perf-chip-row");
    perfChips.setMaxHeight(48);
    perfGraph = new PerfGraph();
    Runnable refreshChrome = () -> {
      FileEditorTab ft = getActiveFileTab();
      Tab activeTab = filesTabs != null ? filesTabs.getSelectionModel().getSelectedItem() : null;
      boolean hasFile = ft != null;
      boolean hasProject = projectRoot != null && projectRoot.isDirectory();
      boolean canFullscreen = canToggleActiveEditorLayout() || editorFullscreen || layeredVisualizerFullscreen;
      int dirtyTabs = countDirtyFileTabs();
      int closableTabs = countClosableTabs();

      miSave.setText(hasFile ? "Save " + ft.getDisplayName() : "Save");
      miSaveAs.setText(hasFile ? "Save " + ft.getDisplayName() + " As..." : "Save As...");
      miSave.setDisable(!hasFile);
      miSaveAs.setDisable(!hasFile);
      miSaveAllTabs.setText(dirtyTabs > 0 ? "Save All Open Tabs (" + dirtyTabs + " dirty)" : "Save All Open Tabs");
      miSaveAllTabs.setDisable(dirtyTabs == 0);
      String activeTitle = activeTab != null && activeTab.getText() != null
          ? activeTab.getText().replace(" *", "")
          : "";
      miCloseTab.setText(activeTab != null && activeTab.isClosable() && !activeTitle.isBlank()
          ? "Close " + activeTitle
          : "Close Tab");
      miCloseTab.setDisable(activeTab == null || !activeTab.isClosable());
      miCloseAllTabs.setText(closableTabs > 0 ? "Close All Tabs (" + closableTabs + ")" : "Close All Tabs");
      miCloseAllTabs.setDisable(closableTabs == 0);
      miRevealActiveFile.setText(hasFile
          ? "Reveal " + ft.getDisplayName() + " in JVN Path Explorer"
          : "Reveal Active File in JVN Path Explorer");
      miRevealActiveFile.setDisable(!hasFile);
      miCopyActiveFilePath.setText(hasFile ? "Copy " + ft.getDisplayName() + " Path" : "Copy Active File Path");
      miCopyActiveFilePath.setDisable(!hasFile);

      miUndo.setText(commands.canUndo() ? "Undo " + commands.undoDescription() : "Undo");
      miRedo.setText(commands.canRedo() ? "Redo " + commands.redoDescription() : "Redo");
      miUndo.setDisable(!commands.canUndo());
      miRedo.setDisable(!commands.canRedo());
      miFind.setDisable(!hasFile);
      miGoToLine.setDisable(ft == null || ft.getKind() != FileEditorTab.Kind.VNS);
      miReload.setDisable(!hasFile);

      miToggleEditorFullscreen.setText(editorFullscreen || layeredVisualizerFullscreen
          ? "Restore Editor Layout"
          : "Toggle Editor Fullscreen");
      miToggleEditorFullscreen.setDisable(!canFullscreen);
      miResetCamera.setDisable(!canResetActiveCamera());
      miFitContent.setDisable(!canFitActiveContent());

      miApplyCode.setDisable(!canApplyPreview());
      miLaunchHere.setDisable(!canLaunchFromActiveTab());
      miLaunchStart.setDisable(!canLaunchFromActiveTab());
      miFileRunProject.setDisable(!hasProject);
      miRunProject.setDisable(!hasProject);
      miFileBuildPublish.setDisable(!hasProject);
      miBuildPublishProject.setDisable(!hasProject);
      miBuildPublishMain.setDisable(!hasProject);
      miFileRevealProjectRoot.setDisable(!hasProject);
      miFileCopyProjectRoot.setDisable(!hasProject);
      miOpenVcs.setDisable(!hasProject);
      miRefreshVcs.setDisable(!hasProject);
      miRevealProjectRoot.setDisable(!hasProject);
      miCopyProjectRootPath.setDisable(!hasProject);
      miOpenProjectDocs.setDisable(resolveDocsDirectory(projectRoot) == null);
      miOpenWorkspaceDocs.setDisable(resolveDocsDirectory(resolveWorkspaceRoot()) == null);

      refreshStatusBarContext(ft);
    };
    refreshMainCommandUi = refreshChrome;
    commands.setOnChange(refreshChrome);
    menuFile.setOnShowing(e -> refreshChrome.run());
    menuEdit.setOnShowing(e -> refreshChrome.run());
    menuView.setOnShowing(e -> {
      syncThemeMenuSelection.run();
      refreshChrome.run();
    });
    menuNavigate.setOnShowing(e -> refreshChrome.run());
    menuRun.setOnShowing(e -> refreshChrome.run());
    menuBuild.setOnShowing(e -> refreshChrome.run());
    menuTools.setOnShowing(e -> refreshChrome.run());
    menuVcs.setOnShowing(e -> refreshChrome.run());
    menuHelp.setOnShowing(e -> refreshChrome.run());

    MetallicJvnLogo wordmark = new MetallicJvnLogo(126, 54);
    Label verLabel = new Label(buildInfo.versionLabel());
    verLabel.getStyleClass().add("jvn-wordmark-version");
    Label sourceLabel = new Label(buildInfo.sourceLabel());
    sourceLabel.getStyleClass().add("jvn-wordmark-source");
    sourceLabel.setVisible(buildInfo.runningFromSource());
    sourceLabel.setManaged(buildInfo.runningFromSource());
    VBox logoBox = new VBox(2);
    logoBox.setAlignment(Pos.CENTER_RIGHT);
    logoBox.getStyleClass().add("jvn-wordmark-box");
    logoBox.getChildren().addAll(wordmark, verLabel, sourceLabel);
    VBox perfBox = new VBox(4, perfChips, perfGraph.getCanvas());
    perfBox.setAlignment(Pos.CENTER);
    perfBox.setFillWidth(true);
    HBox.setHgrow(perfBox, Priority.ALWAYS);
    perfBox.widthProperty().addListener((o, ov, nv) -> {
      perfGraph.setWidth(Math.max(180.0, nv.doubleValue() - 22.0));
    });
    toolbar.setCenter(perfBox);
    BorderPane.setAlignment(logoBox, Pos.TOP_RIGHT);
    toolbar.setRight(logoBox);

    // Layout
    VBox top = new VBox(commandBar, toolbar);
    if (DEVELOPER_MODE) {
      developerLogPanel = new DeveloperLogPanel("Logs", this::developerLogRoots);
      top.getChildren().add(developerLogPanel);
    }
    top.getStyleClass().add("master-toolbar-shell");
    root.setTop(top);
    // Center: per-file tabs with embedded preview
    filesTabs = new TabPane();
    filesTabs.getStyleClass().addAll("sidebar-tab-pane", "main-editor-tab-pane");
    filesTabs.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
    filesTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
    filesTabs.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
      updateContextForActiveTab();
    });
    workspaceHubView = new EditorWorkspaceHubView();
    workspaceHubView.setWorkspaceRoot(resolveWorkspaceRoot());
    workspaceHubView.setCurrentProject(projectRoot);
    workspaceHubView.setFirstRunIssues(startupSetupIssues);
    workspaceHubView.setOnCreateProject(() -> doNewProject(primaryStage));
    workspaceHubView.setOnOpenProjectDialog(() -> doOpenProject(primaryStage));
    workspaceHubView.setOnRunProject(() -> {
      File rootDir = projectRoot == null ? ensureProjectRoot(primaryStage) : projectRoot;
      if (rootDir != null) doRunProject(rootDir);
    });
    workspaceHubView.setOnShowSettings(this::selectEditorSettingsTab);
    tabWorkspaceHub = new Tab("Workspace", workspaceHubView);
    tabWorkspaceHub.setClosable(false);
    if (editorPreferences.isShowWelcomeOnStartup()) {
      filesTabs.getTabs().add(tabWorkspaceHub);
      filesTabs.getSelectionModel().select(tabWorkspaceHub);
    }
    root.setCenter(filesTabs);
    inspectorView = new InspectorView(s -> status.setText(s));
    inspectorView.setCommandStack(commands);
    inspectorView.setMinWidth(280);
    inspectorView.setPrefWidth(320);
    inspectorScroll = new ScrollPane(inspectorView);
    inspectorScroll.setFitToWidth(true);
    rightTabs = new TabPane();
    rightTabs.getStyleClass().add("sidebar-tab-pane");
    rightTabs.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
    rightTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
    tabRightAdd = new Tab("", createSidebarEmptyState("right")); tabRightAdd.setClosable(false);
    tabRightAdd.setGraphic(NewTabIcon.compact());
    tabRightAdd.getStyleClass().add("sidebar-add-tab");
    rightTabs.getTabs().addAll(tabRightAdd);
    installAddTabBehavior(rightTabs, tabRightAdd, this::showRightAddMenu);
    rightTabs.setPrefWidth(360);
    installEmptySidebarAutoClose(rightTabs);
    projView = new ProjectExplorerView();
    if (projectRoot != null) {
      projView.setRootDirectory(projectRoot);
    }
    projView.setOnOpenFile(f -> {
      if (f == null) return;
      if (isEditableFile(f)) {
        openFile(f);
      } else {
        try { java.awt.Desktop.getDesktop().open(f); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
      }
    });
    projView.setOnRunProject(projectDir -> {
      if (projectDir == null) return;
      openProjectDirectory(projectDir);
      selectProjectTab();
      doRunProject(projectDir);
    });
    projView.setOnBuildProject(projectDir -> {
      if (projectDir == null) return;
      openProjectDirectory(projectDir);
      selectProjectTab();
      showGameBuildPublisherWindow(primaryStage);
    });
    leftTabs = new TabPane();
    leftTabs.getStyleClass().add("sidebar-tab-pane");
    leftTabs.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
    leftTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
    tabProject = new Tab("Project", projView); tabProject.setClosable(false);
    applySidebarPanelGraphic(tabProject, EditorSidebarPanel.PROJECT);
    tabLeftAdd = new Tab("", createSidebarEmptyState("left")); tabLeftAdd.setClosable(false);
    tabLeftAdd.setGraphic(NewTabIcon.compact());
    tabLeftAdd.getStyleClass().add("sidebar-add-tab");
    leftTabs.getTabs().addAll(tabProject, tabLeftAdd);
    installAddTabBehavior(leftTabs, tabLeftAdd, this::showLeftAddMenu);
    leftTabs.getSelectionModel().select(tabProject);
    leftTabs.setPrefWidth(300);
    installEmptySidebarAutoClose(leftTabs);
    leftSidebarShell = createSidebarShell(leftTabs);
    rightSidebarShell = createSidebarShell(rightTabs);
    centerSplit = new SplitPane();
    centerSplit.getStyleClass().add("editor-main-split-pane");
    centerSplit.getItems().addAll(leftSidebarShell, filesTabs, rightSidebarShell);
    double initLeft = editorPreferences != null ? editorPreferences.getCenterDividerLeft() : 0.22;
    double initRight = editorPreferences != null ? editorPreferences.getCenterDividerRight() : 0.78;
    centerSplit.setDividerPositions(initLeft, initRight);
    savedCenterDividers = new double[]{initLeft, initRight};
    applyEditorPreferences(editorPreferences);
    root.setLeft(null);
   root.setRight(null);
    root.setCenter(centerSplit);
    root.setBottom(statusBar);
    applyStartupProjectOverride();
    refreshStatusBarContext();
    refreshMainCommandUi.run();

    Scene scene = new Scene(root, 1200, 800);
    // Load editor stylesheet (icons, theme, etc.)
    EditorTheme.apply(scene);
    primaryStage.setScene(scene);
    primaryStage.setOnCloseRequest(e -> {
      if (shutdownInProgress) {
        e.consume();
        return;
      }
      if (!confirmCloseAllTabs()) {
        e.consume();
        return;
      }
      if (layoutStudioWindowManager != null && !layoutStudioWindowManager.requestCloseAll()) {
        e.consume();
        return;
      }
      e.consume();
      shutdownInProgress = true;
      new ShutdownSplashOverlay().show(
          "Verifying editor workspace and background services...",
          () -> {
            saveWorkspaceStateToPreferences();
            disposeAllFileTabs();
            if (pluginHost != null) {
              pluginHost.close();
              pluginHost = null;
            }
          },
          Platform::exit);
    });
    applyLinuxDefaultWindowState(primaryStage);
    primaryStage.show();
    Platform.runLater(() -> maybeShowWhatsNew(primaryStage, buildInfo));
    installSidebarDividerHoverHints();
    scene.setOnDragOver((DragEvent e) -> {
      Dragboard db = e.getDragboard();
      if (db != null && db.hasFiles()) e.acceptTransferModes(TransferMode.COPY);
      e.consume();
    });
    scene.setOnDragDropped((DragEvent e) -> {
      Dragboard db = e.getDragboard();
      boolean success = false;
      if (db != null && db.hasFiles()) {
        List<File> assets = db.getFiles().stream()
            .filter(AssetBrowserView::isSupportedAssetFile)
            .toList();
        if (projectRoot != null && !assets.isEmpty()) {
          AssetBrowserView browser = ensureAssetBrowserView();
          success = browser.acceptDroppedFiles(assets);
          if (success) selectAssetBrowserTab();
        } else {
          File file = db.getFiles().get(0);
          if (file != null) openFile(file);
          success = file != null;
        }
      }
      e.setDropCompleted(success);
      e.consume();
    });

    // Keep preview draw work within the Hub budget independently of editor UI updates.
    final com.jvn.editor.ui.PreviewFramePacer previewPacer =
        com.jvn.editor.ui.PreviewFramePacer.forCurrentPipeline();
    // Timer
    timer = new AnimationTimer() {
      long last = -1;
      long lastTabDirtyRefreshNs = -1;
      @Override public void handle(long now) {
        if (last < 0) { last = now; return; }
        if (minFrameIntervalNs > 0 && now - last < minFrameIntervalNs) return;
        long elapsedNs = now - last;
        long dt = elapsedNs / 1_000_000L;
        last = now;
        editorFrameStats.record(elapsedNs);
        FileEditorTab ft = getActiveFileTab();
        if (ft != null) {
          ft.setSize(filesTabs.getWidth(), filesTabs.getHeight());
          if (EditorPulsePolicy.shouldRenderActivePreview(
              primaryStage.isShowing(),
              primaryStage.isIconified(),
              primaryStage.isFocused())) {
            var previewFrame = previewPacer.next(now);
            if (previewFrame.render()) ft.render(previewFrame.deltaMs());
          }
        }
        if (lastTabDirtyRefreshNs < 0
            || now - lastTabDirtyRefreshNs >= TAB_DIRTY_REFRESH_INTERVAL_NS) {
          lastTabDirtyRefreshNs = now;
          refreshTabDirtyIndicators();
        }
        if (fps != null) {
          lastFps = elapsedNs > 0 ? 1_000_000_000.0 / elapsedNs : 0.0;
        }
        updatePerf(now);
      }
    };
    timer.start();
  }

  private void maybeShowWhatsNew(Window owner, AppBuildInfo.BuildInfo buildInfo) {
    if (SAFE_MODE || editorPreferences == null || buildInfo == null) return;
    String currentVersion = buildInfo.versionLabel();
    if (!WhatsNewCatalog.shouldShow(
        currentVersion,
        editorPreferences.getLastSeenWhatsNewVersion())) {
      return;
    }

    showWhatsNew(owner, buildInfo);
    editorPreferences.setLastSeenWhatsNewVersion(currentVersion);
    persistEditorPreferences();
  }

  private void showWhatsNew(Window owner, AppBuildInfo.BuildInfo buildInfo) {
    if (buildInfo == null) return;
    WhatsNewDialog.show(owner, WhatsNewCatalog.forVersion(buildInfo.versionLabel()));
  }

  private void doOpen(Stage stage) {
    try {
      FileChooser fc = new FileChooser();
      fc.setTitle("Open JES Script");
      fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JES scripts", "*.jes", "*.txt"));
      File f = fc.showOpenDialog(stage);
      if (f == null) return;
      openJesFile(f);
    } catch (Exception ex) {
      status.setText("Load failed");
      EditorDialogs.error(
          stage,
          "Open JES Script",
          "Failed to load the selected JES script.",
          ex,
          "Confirm the script file still exists and is readable.",
          "Check the script syntax near the reported parser location.");
    }
  }

  private void doOpenTextFile(Stage stage) {
    try {
      FileChooser fc = new FileChooser();
      fc.setTitle("Open JVN Text File");
      List<String> patterns = new ArrayList<>();
      for (String ext : EDITABLE_EXTENSIONS) {
        if (ext == null || ext.isBlank()) continue;
        patterns.add("*" + ext.trim());
      }
      fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JVN text files", patterns));
      fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
      File f = fc.showOpenDialog(stage);
      if (f == null) return;
      openFile(f);
    } catch (Exception ex) {
      status.setText("Load failed");
      EditorDialogs.error(
          stage,
          "Open Text File",
          "Failed to load the selected text file.",
          ex,
          "Confirm the file still exists and is readable.",
          "Try opening the file from the project explorer if it was recently moved.");
    }
  }

  private void doOpenProject(Stage stage) {
    DirectoryChooser dc = new DirectoryChooser();
    dc.setTitle("Open Project Directory");
    File dir = dc.showDialog(stage);
    if (dir == null) return;
    openProjectDirectory(dir);
    status.setText("Project: " + dir.getName());
    selectProjectTab();
  }

  private void openJesFile(File f) { openFile(f); }

  private void openVnsFile(File f) { openFile(f); }

  private File resolveProjectFile(String p) {
    if (p == null) return null;
    File f = new File(p);
    if (f.isAbsolute() || projectRoot == null) return f;
    return new File(projectRoot, p);
  }

  private void doReload() {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return;
    ft.reloadFromDisk();
    updateContextForActiveTab();
  }

  private void applyCodeFromEditor() {
    try {
      FileEditorTab ft = getActiveFileTab();
      if (ft == null) return;
      ft.apply();
      status.setText("Applied");
      updateContextForActiveTab();
      if (lastSelectedName != null && !lastSelectedName.isBlank()) {
        JesScene2D scene = ft.getJesScene();
        if (scene != null) {
          Entity2D ent = scene.find(lastSelectedName);
          if (ent != null) {
            selected = ent;
            if (inspectorView != null) inspectorView.setSelection(ent);
          }
        }
      }
    } catch (Exception ex) {
      status.setText("Apply failed");
      EditorDialogs.error(
          dialogOwner(),
          "Apply Code",
          "Failed to apply the current code to the preview.",
          ex,
          "Check the script or Java syntax around the latest edits.",
          "Undo recent changes if the preview was working before this edit.");
    }
    refreshMainCommandUi.run();
  }

  private void toggleActiveEditorFullscreen() {
    if (centerSplit == null) return;
    if (layeredVisualizerFullscreen) {
      restoreLayeredImageVisualizerLayout(true);
      return;
    }
    if (!editorFullscreen) {
      // Save current divider positions and collapse sidebars
      savedCenterDividers = centerSplit.getDividerPositions().clone();
      centerSplit.setDividerPositions(0.0, 1.0);
      editorFullscreen = true;
      status.setText("Editor fullscreen — press F11 or Fullscreen to restore");
    } else {
      // Restore saved divider positions
      double left = savedCenterDividers != null && savedCenterDividers.length >= 2 ? savedCenterDividers[0] : 0.22;
      double right = savedCenterDividers != null && savedCenterDividers.length >= 2 ? savedCenterDividers[1] : 0.78;
      centerSplit.setDividerPositions(Math.max(0.05, left), Math.min(0.95, right));
      editorFullscreen = false;
      status.setText("Editor layout restored");
    }
    refreshMainCommandUi.run();
  }

  private void toggleImageToolFullscreen(ImageToolPanel tool) {
    if (tool == null) return;
    if (centerSplit == null || rightTabs == null) return;
    if (layeredVisualizerFullscreen) {
      if (fullscreenImageToolView == tool) {
        restoreLayeredImageVisualizerLayout(true);
        return;
      }
      restoreLayeredImageVisualizerLayout(false);
    }
    if (editorFullscreen) {
      toggleActiveEditorFullscreen();
    }
    Tab imageToolTab = ensureTabForImageTool(tool, rightTabs);
    if (imageToolTab == null || imageToolTab.getTabPane() == null) return;
    imageToolTab.getTabPane().getSelectionModel().select(imageToolTab);
    savedLayeredVisualizerDividers = centerSplit.getDividerPositions().clone();
    centerSplit.setDividerPositions(0.0, 0.0);
    layeredVisualizerFullscreen = true;
    fullscreenImageToolView = tool;
    setImageToolFullscreenState(tool);
    status.setText(imageToolName(tool) + " fullscreen — use the fullscreen button again to restore");
    refreshMainCommandUi.run();
  }

  private void restoreLayeredImageVisualizerLayout(boolean announce) {
    double left = savedLayeredVisualizerDividers != null && savedLayeredVisualizerDividers.length >= 2
        ? savedLayeredVisualizerDividers[0]
        : 0.22;
    double right = savedLayeredVisualizerDividers != null && savedLayeredVisualizerDividers.length >= 2
        ? savedLayeredVisualizerDividers[1]
        : 0.78;
    centerSplit.setDividerPositions(Math.max(0.05, left), Math.min(0.95, right));
    savedLayeredVisualizerDividers = null;
    layeredVisualizerFullscreen = false;
    fullscreenImageToolView = null;
    setImageToolFullscreenState(null);
    if (announce) {
      status.setText("Image tool layout restored");
    }
    refreshMainCommandUi.run();
  }

  private Tab ensureTabForImageTool(ImageToolPanel tool, TabPane targetPane) {
    if (tool == null || targetPane == null) return null;
    if (tool == layeredImageVisualizerView) return ensureLayeredImageVisualizerTab(targetPane);
    if (tool == imageAttributesToolView) return ensureImageAttributesToolTab(targetPane);
    if (tool == imageTintToolView) return ensureImageTintToolTab(targetPane);
    return null;
  }

  private void setImageToolFullscreenState(ImageToolPanel activeTool) {
    if (layeredImageVisualizerView != null) layeredImageVisualizerView.setFullscreenActive(activeTool == layeredImageVisualizerView);
    if (imageAttributesToolView != null) imageAttributesToolView.setFullscreenActive(activeTool == imageAttributesToolView);
    if (imageTintToolView != null) imageTintToolView.setFullscreenActive(activeTool == imageTintToolView);
  }

  private String imageToolName(ImageToolPanel tool) {
    if (tool == layeredImageVisualizerView) return "Layered visualizer";
    if (tool == imageAttributesToolView) return "Image attributes tool";
    if (tool == imageTintToolView) return "Image tint tool";
    return "Image tool";
  }

  private void doSave(Stage stage) {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return;
    File f = ft.getFile();
    if (f == null) { doSaveAs(stage); return; }
    ft.saveTo(f);
    notifyVersionControlOfFileChange();
  }

  /**
   * Nudges Git-status displays to refresh after a file save: the Version Control panel's
   * changed-files list, and the status bar's "Clean"/"N changes" segment, both of which
   * otherwise only refresh on a timer or when the project is reloaded.
   */
  private void notifyVersionControlOfFileChange() {
    if (versionControlView != null) versionControlView.refreshStatus();
    if (statusBar != null) statusBar.refreshGitState();
  }

  private void saveAllOpenTabs() {
    int savedFileTabs = 0;
    int failedFileTabs = 0;
    if (filesTabs != null) {
      for (Tab tab : new ArrayList<>(filesTabs.getTabs())) {
        if (!(tab.getContent() instanceof FileEditorTab ft) || !ft.isDirty()) continue;
        File target = ft.getFile();
        if (target == null) {
          failedFileTabs++;
          continue;
        }
        ft.saveTo(target);
        if (ft.isDirty()) failedFileTabs++;
        else savedFileTabs++;
      }
    }

    boolean studiosSaved = true;
    if (layoutStudioWindowManager != null) {
      studiosSaved = layoutStudioWindowManager.saveAllDirty(msg -> {
        if (status != null && msg != null && !msg.isBlank()) status.setText(msg);
      });
    }

    refreshTabDirtyIndicators();
    refreshMainCommandUi.run();
    if (savedFileTabs > 0) notifyVersionControlOfFileChange();

    if (status != null) {
      if (savedFileTabs == 0 && failedFileTabs == 0 && studiosSaved) {
        status.setText("Nothing to save.");
      } else {
        List<String> parts = new ArrayList<>();
        if (savedFileTabs > 0) parts.add("saved " + savedFileTabs + " tab" + (savedFileTabs == 1 ? "" : "s"));
        if (failedFileTabs > 0) parts.add(failedFileTabs + " tab" + (failedFileTabs == 1 ? "" : "s") + " still dirty");
        if (!studiosSaved) parts.add("studio windows need attention");
        status.setText(parts.isEmpty() ? "Nothing to save." : "Save all: " + String.join(", ", parts) + ".");
      }
    }
  }

  private void doSaveAs(Stage stage) {
    try {
      FileEditorTab ft = getActiveFileTab(); if (ft == null) return;
      Tab currentTab = filesTabs != null ? filesTabs.getSelectionModel().getSelectedItem() : null;
      FileChooser fc = new FileChooser();
      fc.setTitle("Save File");
      if (ft.getFile() != null) fc.setInitialFileName(ft.getFile().getName());
      File f = fc.showSaveDialog(stage);
      if (f == null) return;
      ft.saveTo(f);
      notifyVersionControlOfFileChange();
      openFile(f);
      if (currentTab != null && filesTabs != null) closeAndDisposeTab(currentTab);
    } catch (Exception ex) {
      status.setText("Save As failed");
      EditorDialogs.error(
          stage,
          "Save As",
          "Failed to save the file to the selected location.",
          ex,
          "Confirm the destination folder exists and is writable.",
          "Choose a different folder if the current location is protected.");
    }
  }

  private void openJavaFile(File f) { openFile(f); }

  private static String stripExt(String name) {
    if (name == null) return "scene";
    int i = name.lastIndexOf('.');
    return (i > 0) ? name.substring(0, i) : name;
  }

  private void buildSceneGraph() {
    updateContextForActiveTab();
  }

  private void executeUndo() {
    if (!commands.canUndo()) return;
    commands.undo();
    if (status != null) status.setText("Undo");
    if (inspectorView != null) inspectorView.setSelection(selected);
    refreshMainCommandUi.run();
  }

  private void executeRedo() {
    if (!commands.canRedo()) return;
    commands.redo();
    if (status != null) status.setText("Redo");
    if (inspectorView != null) inspectorView.setSelection(selected);
    refreshMainCommandUi.run();
  }

  private boolean canApplyPreview() {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return false;
    return ft.getKind() == FileEditorTab.Kind.JES || ft.getKind() == FileEditorTab.Kind.VNS;
  }

  private boolean canLaunchFromActiveTab() {
    FileEditorTab ft = getActiveFileTab();
    return ft != null && ft.getKind() == FileEditorTab.Kind.VNS;
  }

  private boolean canResetActiveCamera() {
    FileEditorTab ft = getActiveFileTab();
    return ft != null && ft.getKind() == FileEditorTab.Kind.JES && ft.getViewport() != null;
  }

  private boolean canFitActiveContent() {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return false;
    return ft.getKind() == FileEditorTab.Kind.JES || ft.getKind() == FileEditorTab.Kind.VNS;
  }

  private boolean canToggleActiveEditorLayout() {
    FileEditorTab ft = getActiveFileTab();
    return ft != null && ft.supportsEditorFullscreenToggle();
  }

  private int countDirtyFileTabs() {
    if (filesTabs == null) return 0;
    int dirtyCount = 0;
    for (Tab tab : filesTabs.getTabs()) {
      if (tab.getContent() instanceof FileEditorTab ft && ft.isDirty()) dirtyCount++;
    }
    return dirtyCount;
  }

  private int countClosableTabs() {
    if (filesTabs == null) return 0;
    int closableCount = 0;
    for (Tab tab : filesTabs.getTabs()) {
      if (tab.isClosable()) closableCount++;
    }
    return closableCount;
  }

  private String mapKey(KeyCode code) { return code == null ? "" : (code.getName() == null || code.getName().isBlank() ? code.toString() : code.getName()).toUpperCase(); }
  private int mapButton(MouseButton b) { if (b == MouseButton.PRIMARY) return 1; if (b == MouseButton.MIDDLE) return 2; if (b == MouseButton.SECONDARY) return 3; return 0; }

  private void updatePerf(long nowNs) {
    if (perfChips == null) return;
    if (lastPerfUpdateNs > 0 && (nowNs - lastPerfUpdateNs) < PERF_UPDATE_INTERVAL_NS) return;
    lastPerfUpdateNs = nowNs;

    double processCpu = -1;
    if (osBean != null) {
      processCpu = osBean.getProcessCpuLoad();
    }

    smoothedProcessCpu = smoothRatio(smoothedProcessCpu, processCpu, PERF_CPU_SMOOTH_ALPHA);
    smoothedFps = smoothRatio(smoothedFps, lastFps / targetFps, PERF_FPS_SMOOTH_ALPHA);

    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    MemoryUsage heap = memoryBean.getHeapMemoryUsage();
    MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
    double heapUsedMb = Math.max(0.0, bytesToMb(heap == null ? -1L : heap.getUsed()));
    long heapMaxBytes = heap == null ? -1L : heap.getMax();
    if (heapMaxBytes <= 0 && heap != null) {
      heapMaxBytes = heap.getCommitted();
    }
    if (heapMaxBytes <= 0) {
      heapMaxBytes = Runtime.getRuntime().maxMemory();
    }
    double heapMaxMb = Math.max(1.0, bytesToMb(heapMaxBytes));
    double nonHeapMb = Math.max(0.0, bytesToMb(nonHeap == null ? -1L : nonHeap.getUsed()));
    double heapRatio = clamp01(heapUsedMb / heapMaxMb);
    double heapUsedClampedMb = clamp(heapUsedMb, 0.0, heapMaxMb);
    double jvnUsedMb = Math.max(0.0, heapUsedMb + nonHeapMb);

    long gcCount = 0;
    long gcTimeMs = 0;
    for (var gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
      if (gcBean == null) continue;
      long c = gcBean.getCollectionCount();
      long t = gcBean.getCollectionTime();
      if (c > 0) gcCount += c;
      if (t > 0) gcTimeMs += t;
    }
    long gcCountDelta = (lastGcCollectionCount >= 0L) ? Math.max(0L, gcCount - lastGcCollectionCount) : 0L;
    long gcTimeDelta = (lastGcCollectionTimeMs >= 0L) ? Math.max(0L, gcTimeMs - lastGcCollectionTimeMs) : 0L;
    lastGcCollectionCount = gcCount;
    lastGcCollectionTimeMs = gcTimeMs;
    memoryPressureMonitor.sample(
        nowNs,
        heap == null ? 0L : heap.getUsed(),
        heapMaxBytes,
        gcCountDelta,
        gcTimeDelta).ifPresent(this::showMemoryPressureAlert);
    String gcText = gcCountDelta > 0
        ? String.format(Locale.ROOT, "GC +%d/%dms", gcCountDelta, gcTimeDelta)
        : "GC idle";

    var threadBean = ManagementFactory.getThreadMXBean();
    int threadCount = threadBean.getThreadCount();
    int daemonThreadCount = threadBean.getDaemonThreadCount();
    PerfEnvironment environment = perfEnvironment();

    String cpuTextValue = isRatioValid(smoothedProcessCpu)
        ? String.format(Locale.ROOT, "CPU %.0f%%", safePercent(smoothedProcessCpu))
        : "CPU --";
    String heapChipText = String.format(
        Locale.ROOT,
        "Heap %.0f%%",
        heapRatio * 100.0);
    String heapTooltip = String.format(
        Locale.ROOT,
        "Heap memory: %.0f MB used of %.0f MB max (%.0f%%)",
        heapUsedClampedMb,
        heapMaxMb,
        heapRatio * 100.0);
    String ramTextValue = String.format(
        Locale.ROOT,
        "JVM %.0f MB",
        jvnUsedMb);
    String jvmTooltip = String.format(
        Locale.ROOT,
        "JVM memory: %.0f MB total tracked, %.0f MB heap, %.0f MB non-heap",
        jvnUsedMb,
        heapUsedMb,
        nonHeapMb);
    EditorFrameStats.Snapshot frameStats = editorFrameStats.snapshot(targetFps);
    String fpsTextValue = String.format(
        Locale.ROOT,
        "FPS %.0f · P95 %.0fms",
        Math.max(0.0, smoothedFps * targetFps),
        frameStats.p95Millis());

    updatePerfChip(cpuChip, cpuTextValue, isRatioValid(smoothedProcessCpu)
        ? "Editor process CPU load: " + String.format(Locale.ROOT, "%.0f%%", safePercent(smoothedProcessCpu))
        : "Editor process CPU load unavailable.");
    updatePerfChip(heapChip, heapChipText, heapTooltip);
    updatePerfChip(jvmChip, ramTextValue, jvmTooltip);
    updatePerfChip(
        fpsChip,
        fpsTextValue,
        String.format(
            Locale.ROOT,
            "Rolling frame pacing: %.1f average FPS, %.1f ms P95, %.1f ms worst, %d stalls in %d frames.",
            frameStats.averageFps(),
            frameStats.p95Millis(),
            frameStats.maxMillis(),
            frameStats.stalls(),
            frameStats.samples()));
    if (graphicsStatus != null) {
      updatePerfChip(graphicsChip, graphicsStatus.chipText(), graphicsStatus.tooltip());
    }
    updatePerfChip(threadsChip, "Threads " + threadCount, threadCount + " live threads, " + daemonThreadCount + " daemon threads.");
    updatePerfChip(gcChip, gcText, "Garbage collection delta since the previous sample.");
    updatePerfChip(javaChip, environment.javaChip(), environment.javaText());
    updatePerfChip(jdkChip, environment.jdkChip(), environment.javaText());
    updatePerfChip(gradleChip, environment.gradleChip(), environment.gradleText());
    updatePerfChip(projectShapeChip, environment.projectShapeChip(), environment.gradleText());

    perfGraph.pushSample(
        isRatioValid(smoothedProcessCpu) ? smoothedProcessCpu : 0,
        heapRatio,
        clamp01(smoothedFps));
    autoWriteDeveloperDiagnostics(
        heapUsedMb,
        heapMaxMb,
        nonHeapMb,
        jvnUsedMb,
        heapRatio,
        threadCount,
        daemonThreadCount,
        gcCountDelta,
        gcTimeDelta,
        cpuTextValue,
        fpsTextValue);
  }

  private void showMemoryPressureAlert(EditorMemoryPressureMonitor.Snapshot snapshot) {
    if (snapshot == null || memoryPressureDialogShowing || shutdownInProgress) return;
    memoryPressureDialogShowing = true;
    try {
      long usedMiB = Math.max(0L, snapshot.heapUsedBytes() / EditorMemoryPressureMonitor.MIB);
      long maxMiB = Math.max(1L, snapshot.heapMaxBytes() / EditorMemoryPressureMonitor.MIB);
      String percent = String.format(Locale.ROOT, "%.0f%%", snapshot.heapRatio() * 100.0);
      String title;
      String message;
      String explanation;
      switch (snapshot.event()) {
        case LOW_MAX_HEAP -> {
          title = "Editor Heap Is Configured Too Low";
          message = "This editor process has a maximum Java heap of " + maxMiB + " MiB.";
          explanation = "Large projects and animated previews are more reliable with at least 768 MiB. "
              + "This is a launch setting, so changing it takes effect after restarting the editor.";
        }
        case SUSTAINED_HIGH_HEAP -> {
          title = "Sustained Editor Memory Pressure";
          message = "Java heap use has remained high: " + usedMiB + " / " + maxMiB + " MiB (" + percent + ").";
          explanation = "This was sustained rather than a short allocation spike. Release reloadable preview caches, "
              + "close unused tabs, or save diagnostics if the value continues rising.";
        }
        case CRITICAL_HEAP -> {
          title = "Editor Memory Is Nearly Exhausted";
          message = "Java heap use is critical: " + usedMiB + " / " + maxMiB + " MiB (" + percent + ").";
          explanation = "Save important work now. Releasing preview caches can recover space; a diagnostics bundle "
              + "and OOM heap dump are the most useful evidence if pressure returns.";
        }
        case GC_THRASHING -> {
          title = "Garbage Collection Cannot Recover Enough Memory";
          message = "The JVM spent " + snapshot.windowGcTimeMs() + " ms in "
              + snapshot.windowGcCount() + " collections while heap use stayed at " + percent + ".";
          explanation = "The collector is running, but live objects still occupy most of the heap. This usually means "
              + "retained caches/data or a heap ceiling that is too low—not a broken garbage collector.";
        }
        default -> throw new IllegalStateException("Unexpected memory event: " + snapshot.event());
      }

      Label details = new Label(explanation + "\n\nCurrent heap: " + usedMiB + " MiB used / "
          + maxMiB + " MiB maximum\nAlert policy: sustained and cooldown-limited");
      details.setWrapText(true);
      details.getStyleClass().add("editor-dialog-message");
      details.setMaxWidth(520);

      List<EditorDialogs.ActionSpec> actions = new ArrayList<>();
      if (snapshot.event() != EditorMemoryPressureMonitor.Event.LOW_MAX_HEAP) {
        actions.add(EditorDialogs.ActionSpec.accent(
            "release", "Release Preview Caches", this::releasePreviewMemoryCaches));
        actions.add(EditorDialogs.ActionSpec.neutral(
            "diagnostics", "Save Diagnostics...", () -> Platform.runLater(() ->
                DeveloperDiagnosticsExporter.chooseAndExport(
                    dialogOwner(), "JVN Editor", this::developerLogRoots))));
      }
      actions.add(EditorDialogs.ActionSpec.neutral(
          "settings", "Open JVM Settings", () -> Platform.runLater(this::openHubJvmMemorySettings)));
      actions.add(EditorDialogs.ActionSpec.neutral("dismiss", "Dismiss", null));
      EditorDialogs.show(
          dialogOwner(),
          title,
          message,
          details,
          actions.toArray(EditorDialogs.ActionSpec[]::new));
    } finally {
      memoryPressureDialogShowing = false;
    }
  }

  private void releasePreviewMemoryCaches() {
    int trimmed = 0;
    if (filesTabs != null) {
      for (Tab tab : filesTabs.getTabs()) {
        if (tab.getContent() instanceof FileEditorTab fileEditorTab) {
          fileEditorTab.trimPreviewMemoryCaches();
          trimmed++;
        }
      }
    }
    ManagementFactory.getMemoryMXBean().gc();
    if (status != null) {
      status.setText("Released reloadable preview caches in " + trimmed + " editor tab"
          + (trimmed == 1 ? "" : "s") + "; requested GC");
    }
  }

  private void openHubJvmMemorySettings() {
    File workspace = resolveWorkspaceRoot();
    if (workspace == null || !workspace.isDirectory()) {
      EditorDialogs.warning(
          dialogOwner(),
          "JVM Memory Settings",
          "Open Engine Hub, then choose Tools > JVM Memory Settings. Restart the editor after applying changes.");
      return;
    }
    try {
      List<String> command = new ArrayList<>();
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      if (os.contains("win")) {
        command.add(new File(workspace, "gradlew.bat").getAbsolutePath());
        command.add("--console=plain");
        command.add(":hub:run");
        command.add("--args=--jvm-memory-settings");
      } else {
        command.add(new File(workspace, "scripts/launch-hub.sh").getAbsolutePath());
        command.add("--jvm-memory-settings");
      }
      new ProcessBuilder(command)
          .directory(workspace)
          .redirectOutput(ProcessBuilder.Redirect.DISCARD)
          .redirectError(ProcessBuilder.Redirect.DISCARD)
          .start();
      if (status != null) status.setText("Opening Engine Hub JVM Memory Settings");
    } catch (Exception error) {
      EditorDialogs.error(
          dialogOwner(),
          "JVM Memory Settings",
          "Could not open Engine Hub JVM Memory Settings.",
          error,
          "Open Engine Hub manually, then choose Tools > JVM Memory Settings.");
    }
  }

  private void autoWriteDeveloperDiagnostics(
      double heapUsedMb,
      double heapMaxMb,
      double nonHeapMb,
      double jvnUsedMb,
      double heapRatio,
      int threadCount,
      int daemonThreadCount,
      long gcCountDelta,
      long gcTimeDelta,
      String cpuText,
      String fpsText) {
    if (!DEVELOPER_MODE || !DeveloperToolsMenu.isAutoWriteEditorDiagnosticsEnabled()) return;
    long now = System.nanoTime();
    if (lastDeveloperDiagnosticWriteNs > 0
        && now - lastDeveloperDiagnosticWriteNs < DEV_DIAGNOSTIC_WRITE_INTERVAL_NS) {
      return;
    }
    lastDeveloperDiagnosticWriteNs = now;

    try {
      Path logFile = developerDiagnosticsLogFile();
      Files.writeString(
          logFile,
          developerDiagnosticLine(
              heapUsedMb,
              heapMaxMb,
              nonHeapMb,
              jvnUsedMb,
              heapRatio,
              threadCount,
              daemonThreadCount,
              gcCountDelta,
              gcTimeDelta,
              cpuText,
              fpsText),
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.APPEND);
    } catch (Exception ignored) {
      // Best-effort heartbeat for freeze diagnostics; editor UI must keep running if logging fails.
    }
  }

  private Path developerDiagnosticsLogFile() throws IOException {
    if (developerDiagnosticsLogFile != null) return developerDiagnosticsLogFile;
    Path base;
    if (projectRoot != null && projectRoot.isDirectory()) {
      base = projectRoot.toPath();
    } else {
      File workspace = resolveWorkspaceRoot();
      base = workspace != null ? workspace.toPath() : Path.of(System.getProperty("user.home", "."));
    }
    Path dir = base.toAbsolutePath().normalize().resolve(".jvn").resolve("logs");
    Files.createDirectories(dir);
    developerDiagnosticsLogFile = dir.resolve(
        "editor-heartbeat-" + DEV_DIAGNOSTIC_LOG_TIME.format(LocalDateTime.now()) + ".log");
    Files.writeString(
        developerDiagnosticsLogFile,
        "# JVN editor developer heartbeat\n"
            + "# Created " + LocalDateTime.now() + "\n"
            + "# Toggle: DevTools > Auto-write Editor Diagnostics\n",
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND);
    return developerDiagnosticsLogFile;
  }

  private String developerDiagnosticLine(
      double heapUsedMb,
      double heapMaxMb,
      double nonHeapMb,
      double jvnUsedMb,
      double heapRatio,
      int threadCount,
      int daemonThreadCount,
      long gcCountDelta,
      long gcTimeDelta,
      String cpuText,
      String fpsText) {
    FileEditorTab active = getActiveFileTab();
    File activeFile = active == null ? null : active.getFile();
    return String.format(
        Locale.ROOT,
        "%s | uptimeMs=%d | project=%s | active=%s | dirtyTabs=%d | %s | heap=%.1f/%.1fMB %.1f%% | nonHeap=%.1fMB | jvm=%.1fMB | %s | threads=%d daemon=%d | gcDelta=%d/%dms%n",
        LocalDateTime.now(),
        ManagementFactory.getRuntimeMXBean().getUptime(),
        projectRoot == null ? "(none)" : projectRoot.getAbsolutePath(),
        activeFile == null ? "(none)" : activeFile.getAbsolutePath(),
        countDirtyFileTabs(),
        cpuText == null || cpuText.isBlank() ? "CPU --" : cpuText,
        heapUsedMb,
        heapMaxMb,
        heapRatio * 100.0,
        nonHeapMb,
        jvnUsedMb,
        fpsText == null || fpsText.isBlank() ? "FPS --" : fpsText,
        threadCount,
        daemonThreadCount,
        gcCountDelta,
        gcTimeDelta);
  }

  private static Label perfChip(String text, String toneClass) {
    Label label = new Label(text);
    label.getStyleClass().add("editor-perf-chip");
    if (toneClass != null && !toneClass.isBlank()) label.getStyleClass().add(toneClass);
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    label.setMaxWidth(150);
    label.setMinHeight(22);
    return label;
  }

  private static void updatePerfChip(Label label, String text, String tooltip) {
    if (label == null) return;
    String resolvedText = text == null || text.isBlank() ? "--" : text;
    if (!resolvedText.equals(label.getText())) {
      label.setText(resolvedText);
    }
    if (tooltip == null || tooltip.isBlank()) {
      if (label.getTooltip() != null) label.setTooltip(null);
    } else {
      Tooltip existing = label.getTooltip();
      if (existing == null) {
        label.setTooltip(new Tooltip(tooltip));
      } else if (!tooltip.equals(existing.getText())) {
        existing.setText(tooltip);
      }
    }
  }

  private PerfEnvironment perfEnvironment() {
    File root = projectRoot == null ? null : projectRoot.getAbsoluteFile();
    if (perfEnvironment == null || !sameFile(perfEnvironmentProjectRoot, root)) {
      perfEnvironmentProjectRoot = root;
      perfEnvironment = PerfEnvironment.capture(root, resolveWorkspaceRoot());
    }
    return perfEnvironment;
  }

  private static boolean sameFile(File a, File b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    return a.toPath().toAbsolutePath().normalize().equals(b.toPath().toAbsolutePath().normalize());
  }

  private static double smoothRatio(double previous, double sample, double alpha) {
    if (!Double.isFinite(sample) || sample < 0) return previous;
    double clampedSample = clamp01(sample);
    if (!Double.isFinite(previous) || previous < 0) return clampedSample;
    double a = Math.min(1.0, Math.max(0.01, alpha));
    return previous + (clampedSample - previous) * a;
  }

  private static boolean isRatioValid(double ratio) {
    return Double.isFinite(ratio) && ratio >= 0;
  }

  private static double safePercent(double ratio) {
    if (!isRatioValid(ratio)) return 0.0;
    return clamp01(ratio) * 100.0;
  }

  private static double bytesToMb(long bytes) {
    if (bytes <= 0) return 0.0;
    return bytes / (1024.0 * 1024.0);
  }

  private static double clamp(double value, double min, double max) {
    if (!Double.isFinite(value)) return min;
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  private static double clamp01(double value) {
    if (!Double.isFinite(value)) return 0;
    if (value < 0) return 0;
    if (value > 1) return 1;
    return value;
  }

  private record PerfEnvironment(
      String javaText,
      String gradleText,
      String javaChip,
      String jdkChip,
      String gradleChip,
      String projectShapeChip) {
    private static final int COUNT_LIMIT = 999;

    static PerfEnvironment capture(File projectRoot, File workspaceRoot) {
      File buildRoot = firstExistingDirectory(projectRoot, workspaceRoot);
      String toolchain = gradleProperty(buildRoot, "javaVersion");
      String runtime = cleanProperty("java.runtime.version", cleanProperty("java.version", "?"));
      String compiler = ToolProvider.getSystemJavaCompiler() == null ? "JRE" : "JDK";
      String java = javaRuntimeSummary(buildRoot, runtime, toolchain, compiler);
      GradleSummary gradleSummary = gradleSummary(buildRoot, projectRoot);
      String javaMajor = javaFeatureVersion(runtime);
      String target = toolchain == null || toolchain.isBlank() ? "" : " -> " + toolchain;
      return new PerfEnvironment(
          java,
          gradleSummary.text(),
          "Java " + javaMajor + target,
          compiler,
          "Gradle " + gradleSummary.wrapper(),
          gradleSummary.shape().chipText());
    }

    private static String javaRuntimeSummary(File root, String runtime, String toolchain, String compiler) {
      String vendor = cleanProperty("java.vendor", "unknown vendor");
      String vm = cleanProperty("java.vm.name", "VM");
      String home = cleanProperty("java.home", "");
      String target = toolchain == null || toolchain.isBlank() ? "" : " target " + toolchain;
      return "Java " + runtime + target + " | " + compiler + " | " + vendor + " " + vm
          + (home.isBlank() ? "" : " | " + compactHome(home));
    }

    private static GradleSummary gradleSummary(File root, File projectRoot) {
      String wrapper = gradleWrapperVersion(root);
      String jvmArgs = gradleProperty(root, "org.gradle.jvmargs");
      String cache = booleanGradleProperty(root, "org.gradle.caching") ? "cache on" : "cache off";
      String parallel = booleanGradleProperty(root, "org.gradle.parallel") ? "parallel on" : "parallel off";
      String autoJdk = booleanGradleProperty(root, "org.gradle.java.installations.auto-download")
          ? "JDK auto-download on"
          : "JDK auto-download off";
      ProjectShape shape = ProjectShape.capture(projectRoot == null ? root : projectRoot);
      String wrapperText = wrapper == null ? "?" : wrapper;
      String text = "Gradle " + wrapperText
          + " | " + cache
          + " | " + parallel
          + " | " + autoJdk
          + " | " + shape.text()
          + (jvmArgs == null || jvmArgs.isBlank() ? "" : " | JVM args " + summarizeJvmArgs(jvmArgs));
      return new GradleSummary(wrapperText, shape, text);
    }

    private static String javaFeatureVersion(String version) {
      if (version == null || version.isBlank()) return "?";
      String clean = version.trim();
      if (clean.startsWith("1.")) {
        int next = clean.indexOf('.', 2);
        return next > 0 ? clean.substring(2, next) : clean.substring(2);
      }
      int dot = clean.indexOf('.');
      int dash = clean.indexOf('-');
      int plus = clean.indexOf('+');
      int end = clean.length();
      if (dot > 0) end = Math.min(end, dot);
      if (dash > 0) end = Math.min(end, dash);
      if (plus > 0) end = Math.min(end, plus);
      return clean.substring(0, end);
    }

    private static File firstExistingDirectory(File first, File second) {
      if (first != null && first.isDirectory()) return first.getAbsoluteFile();
      if (second != null && second.isDirectory()) return second.getAbsoluteFile();
      return null;
    }

    private static String cleanProperty(String key, String fallback) {
      String value = System.getProperty(key, "").trim();
      return value.isBlank() ? fallback : value;
    }

    private static String compactHome(String path) {
      String home = System.getProperty("user.home", "").trim();
      if (!home.isBlank() && path.startsWith(home)) return "~" + path.substring(home.length());
      return path;
    }

    private static String gradleProperty(File root, String key) {
      if (root == null || key == null || key.isBlank()) return null;
      Path path = root.toPath().resolve("gradle.properties");
      if (!Files.isRegularFile(path)) return null;
      try (InputStream in = Files.newInputStream(path)) {
        Properties props = new Properties();
        props.load(in);
        String value = props.getProperty(key);
        return value == null ? null : value.trim();
      } catch (Exception ignored) {
        return null;
      }
    }

    private static boolean booleanGradleProperty(File root, String key) {
      return Boolean.parseBoolean(String.valueOf(gradleProperty(root, key)));
    }

    private static String gradleWrapperVersion(File root) {
      if (root == null) return null;
      Path path = root.toPath().resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties");
      if (!Files.isRegularFile(path)) return null;
      try {
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
          int marker = line.indexOf("gradle-");
          if (marker < 0) continue;
          int start = marker + "gradle-".length();
          int end = line.indexOf('-', start);
          if (end > start) return line.substring(start, end);
        }
      } catch (Exception ignored) {
        return null;
      }
      return null;
    }

    private static String summarizeJvmArgs(String value) {
      String normalized = value.replaceAll("\\s+", " ").trim();
      if (normalized.length() <= 42) return normalized;
      return normalized.substring(0, 39) + "...";
    }

    private record GradleSummary(String wrapper, ProjectShape shape, String text) {
    }

    private record ProjectShape(int modules, int javaFiles, int gradleFiles) {
      static ProjectShape capture(File root) {
        if (root == null || !root.isDirectory()) return new ProjectShape(0, 0, 0);
        int modules = 0;
        int javaFiles = 0;
        int gradleFiles = 0;
        try (var stream = Files.walk(root.toPath(), 5)) {
          var iterator = stream.iterator();
          while (iterator.hasNext()) {
            Path path = iterator.next();
            Path fileName = path.getFileName();
            if (fileName == null) continue;
            String name = fileName.toString();
            if ("build.gradle.kts".equals(name) || "build.gradle".equals(name)) {
              gradleFiles = Math.min(COUNT_LIMIT, gradleFiles + 1);
              modules = Math.min(COUNT_LIMIT, modules + 1);
            } else if (name.endsWith(".java")) {
              javaFiles = Math.min(COUNT_LIMIT, javaFiles + 1);
            }
            if (modules >= COUNT_LIMIT && javaFiles >= COUNT_LIMIT && gradleFiles >= COUNT_LIMIT) break;
          }
        } catch (Exception ignored) {
          return new ProjectShape(modules, javaFiles, gradleFiles);
        }
        return new ProjectShape(modules, javaFiles, gradleFiles);
      }

      String text() {
        if (modules == 0 && javaFiles == 0 && gradleFiles == 0) return "project shape ?";
        return modules + " modules, " + javaFiles + " Java files, " + gradleFiles + " Gradle files";
      }

      String chipText() {
        if (modules == 0 && javaFiles == 0 && gradleFiles == 0) return "Project ?";
        return modules + " modules";
      }
    }
  }

  /** Tiny inline graph renderer for CPU/heap/FPS trends. */
  private static class PerfGraph {
    private final Canvas canvas = new Canvas(320, 64);
    private final double[] cpu = new double[240]; // ~72s at ~300ms updates
    private final double[] ram = new double[240];
    private final double[] fps = new double[240];
    private int idx = 0;
    private boolean filled = false;

    public Canvas getCanvas() { return canvas; }
    public void setWidth(double w) {
      canvas.setWidth(sanitizeCanvasDimension(w, 160.0));
      redraw();
    }

    public void pushSample(double cpu01, double ram01, double fps01) {
      int i = idx % cpu.length;
      cpu[i] = clamp01(cpu01);
      ram[i] = clamp01(ram01);
      fps[i] = clamp01(fps01);
      idx++;
      if (idx >= cpu.length) filled = true;
      redraw();
    }

    private void redraw() {
      GraphicsContext g = canvas.getGraphicsContext2D();
      double w = canvas.getWidth();
      double h = canvas.getHeight();
      if (!Double.isFinite(w) || !Double.isFinite(h) || w <= 0 || h <= 0) return;
      g.setFill(GRID_BG);
      g.fillRect(0, 0, w, h);

      // grid
      g.setStroke(GRID_LINE);
      g.setLineWidth(1);
      int rows = 4;
      for (int r = 1; r < rows; r++) {
        double y = h * r / rows;
        g.strokeLine(0, y, w, y);
      }
      double stepX = w / 6.0;
      for (double x = stepX; x < w; x += stepX) {
        g.strokeLine(x, 0, x, h);
      }

      int samples = filled ? cpu.length : Math.min(idx, cpu.length);
      if (samples <= 1) return;
      double scaleX = w / (cpu.length - 1);

      // HEAP area
      g.setFill(HEAP_COLOR.deriveColor(0, 1, 1, 0.26));
      g.beginPath();
      for (int i = 0; i < samples; i++) {
        int si = (idx - samples + i + cpu.length) % cpu.length;
        double x = i * scaleX;
        double y = h * (1 - ram[si]);
        if (i == 0) g.moveTo(x, h);
        g.lineTo(x, y);
      }
      g.lineTo((samples - 1) * scaleX, h);
      g.closePath();
      g.fill();

      // HEAP outline
      g.setStroke(HEAP_COLOR.deriveColor(0, 1, 1, 0.95));
      g.setLineWidth(1.6);
      g.beginPath();
      for (int i = 0; i < samples; i++) {
        int si = (idx - samples + i + cpu.length) % cpu.length;
        double x = i * scaleX;
        double y = h * (1 - ram[si]);
        if (i == 0) g.moveTo(x, y); else g.lineTo(x, y);
      }
      g.stroke();

      // CPU line
      g.setStroke(CPU_COLOR.deriveColor(0, 1, 1, 0.9));
      g.setLineWidth(2);
      g.beginPath();
      for (int i = 0; i < samples; i++) {
        int si = (idx - samples + i + cpu.length) % cpu.length;
        double x = i * scaleX;
        double y = h * (1 - cpu[si]);
        if (i == 0) g.moveTo(x, y); else g.lineTo(x, y);
      }
      g.stroke();

      // FPS line
      g.setStroke(FPS_COLOR.deriveColor(0, 1, 1, 0.88));
      g.setLineWidth(2);
      g.beginPath();
      for (int i = 0; i < samples; i++) {
        int si = (idx - samples + i + cpu.length) % cpu.length;
        double x = i * scaleX;
        double y = h * (1 - fps[si]);
        if (i == 0) g.moveTo(x, y); else g.lineTo(x, y);
      }
      g.stroke();
    }

    private double clamp01(double v) {
      if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
      if (v < 0) return 0;
      if (v > 1) return 1;
      return v;
    }

    private static double sanitizeCanvasDimension(double value, double minimum) {
      if (!Double.isFinite(value)) return minimum;
      double min = Math.max(1.0, minimum);
      if (value < min) return min;
      if (value > 8192.0) return 8192.0;
      return value;
    }
  }

  private Region icon(String... styleClasses) {
    Region r = CssIcon.prepare(new Region());
    if (styleClasses != null) r.getStyleClass().addAll(styleClasses);
    return r;
  }

  private Node sidebarPanelIcon(EditorSidebarPanel panel, String... extraStyleClasses) {
    if (panel == null) return null;
    Region icon = getSidebarAeroIcon(panel, sidebarPanelAssetIconSize(extraStyleClasses));
    if (extraStyleClasses != null && extraStyleClasses.length > 0) {
      icon.getStyleClass().addAll(extraStyleClasses);
    }
    return icon;
  }

  private Region getSidebarAeroIcon(EditorSidebarPanel panel, double size) {
    AeroIcon.Kind kind = switch (panel) {
      case PROJECT -> AeroIcon.Kind.PROJECT;
      case TRASHMAN -> AeroIcon.Kind.TRASHMAN;
      case TIMELINE -> AeroIcon.Kind.STORY_MAP;
      case INSPECTOR -> AeroIcon.Kind.INSPECTOR;
      case VNS_DIAGNOSTICS -> AeroIcon.Kind.DIAGNOSTICS;
      case LABEL_FLOW -> AeroIcon.Kind.LABEL_FLOW;
      case TIMELINE_OUTLINE -> AeroIcon.Kind.TIMELINE_OUTLINE;
      case ASSETS -> AeroIcon.Kind.ASSETS;
      case LAYOUT_LAUNCHER -> AeroIcon.Kind.LAYOUT;
      case STORYBOARD_OVERLAY -> AeroIcon.Kind.STORYBOARD;
      case LAYERED_IMAGES -> AeroIcon.Kind.LAYERS;
      case IMAGE_ATTRIBUTES -> AeroIcon.Kind.IMAGE_ATTRIBUTES;
      case IMAGE_TINT -> AeroIcon.Kind.LIGHTING;
      case VERSION_CONTROL -> AeroIcon.Kind.VERSION_CONTROL;
      case PUPPETEER_LAUNCHER -> AeroIcon.Kind.PUPPETEER;
      case SCRIPT_EDITOR -> AeroIcon.Kind.SCRIPT_EDITOR;
    };
    return AeroIcon.of(kind, size);
  }

  private ImageView sidebarPanelAssetIcon(EditorSidebarPanel panel, String... styleClasses) {
    if (panel == null) {
      return null;
    }
    return sidebarAssetIcon(panel.iconAssetName(), styleClasses);
  }

  private ImageView sidebarAssetIcon(String assetName, String... styleClasses) {
    if (!useSidebarAssetIcons() || assetName == null || assetName.isBlank()) {
      return null;
    }
    URL url = getClass().getResource("/com/jvn/editor/images/sidebar/" + assetName);
    if (url == null) return null;

    double size = sidebarPanelAssetIconSize(styleClasses);
    ImageView view = new ImageView(new Image(url.toExternalForm(), size * 2.0, size * 2.0, true, true, true));
    view.setPreserveRatio(true);
    view.setSmooth(true);
    view.setCache(true);
    view.setMouseTransparent(true);
    view.getStyleClass().add("sidebar-asset-icon");
    if (styleClasses != null) view.getStyleClass().addAll(styleClasses);
    view.setFitWidth(size);
    view.setFitHeight(size);
    return view;
  }

  private Node editorSettingsSidebarIcon(String... extraStyleClasses) {
    Region icon = AeroIcon.of(AeroIcon.Kind.SETTINGS, sidebarPanelAssetIconSize(extraStyleClasses));
    if (extraStyleClasses != null && extraStyleClasses.length > 0) {
      icon.getStyleClass().addAll(extraStyleClasses);
    }
    return icon;
  }

  private double sidebarPanelAssetIconSize(String[] styleClasses) {
    if (hasStyleClass(styleClasses, "panel-chooser-tool-icon")) return 28.0;
    if (hasStyleClass(styleClasses, "sidebar-tab-icon")) return 22.0;
    return 22.0;
  }

  private boolean useSidebarAssetIcons() {
    String mode = System.getProperty("jvn.editor.sidebarIconMode", "asset");
    return !"css".equalsIgnoreCase(mode) && !"fx".equalsIgnoreCase(mode);
  }

  private boolean hasStyleClass(String[] styleClasses, String target) {
    if (styleClasses == null || target == null) return false;
    for (String styleClass : styleClasses) {
      if (target.equals(styleClass)) return true;
    }
    return false;
  }

  private void applySidebarPanelGraphic(Tab tab, EditorSidebarPanel panel) {
    if (tab == null || panel == null) return;
    tab.setGraphic(sidebarPanelIcon(panel, "sidebar-tab-icon"));
    applySidebarToolStyle(tab.getContent(), panel);
  }

  private Tab attachSidebarPanelTab(Tab tab, EditorSidebarPanel panel, TabPane targetPane) {
    if (tab == null) return null;
    if (blockMaintenancePanelLaunch(panel, panel != null ? panel.displayName() : null)) return null;
    applySidebarPanelGraphic(tab, panel);
    installAnimatedSidebarToolTabClose(tab);
    attachPanelTabToPane(tab, targetPane);
    return tab;
  }

  private void installAnimatedSidebarToolTabClose(Tab tab) {
    if (tab == null || Boolean.TRUE.equals(tab.getProperties().get(SIDEBAR_TOOL_TAB_CLOSE_INSTALLED_KEY))) return;
    EventHandler<Event> originalClosed = tab.getOnClosed();
    tab.getProperties().put(SIDEBAR_TOOL_TAB_CLOSE_INSTALLED_KEY, Boolean.TRUE);
    tab.setOnCloseRequest(event -> {
      if (Boolean.TRUE.equals(tab.getProperties().get(SIDEBAR_TOOL_TAB_CLOSE_ACTIVE_KEY))) {
        event.consume();
        return;
      }
      TabPane pane = tab.getTabPane();
      if (pane == null || tab == getAddTabForPane(pane)
          || PANEL_CHOOSER_TAB_ROLE.equals(tab.getProperties().get(PANEL_CHOOSER_TAB_ROLE))) {
        return;
      }
      event.consume();
      animateSidebarToolTabClose(tab, pane, originalClosed);
    });
  }

  private void animateSidebarToolTabClose(Tab tab, TabPane pane, EventHandler<Event> originalClosed) {
    if (tab == null || pane == null || !pane.getTabs().contains(tab)) return;
    Node content = tab.getContent();
    if (content == null) {
      removeSidebarToolTabAfterAnimation(tab, pane, originalClosed);
      return;
    }

    tab.getProperties().put(SIDEBAR_TOOL_TAB_CLOSE_ACTIVE_KEY, Boolean.TRUE);
    tab.setClosable(false);
    double startOpacity = content.getOpacity();
    double startTranslate = content.getTranslateX();
    double exitTranslate = startTranslate + (pane == leftTabs ? -18.0 : 18.0);

    Timeline animation = new Timeline(
        new KeyFrame(Duration.ZERO,
            new KeyValue(content.opacityProperty(), startOpacity, Interpolator.EASE_OUT),
            new KeyValue(content.translateXProperty(), startTranslate, Interpolator.EASE_OUT)),
        new KeyFrame(SIDEBAR_TOOL_TAB_CLOSE_DURATION,
            new KeyValue(content.opacityProperty(), 0.0, Interpolator.EASE_BOTH),
            new KeyValue(content.translateXProperty(), exitTranslate, Interpolator.EASE_BOTH)));
    animation.setOnFinished(e -> {
      content.setOpacity(startOpacity);
      content.setTranslateX(startTranslate);
      removeSidebarToolTabAfterAnimation(tab, pane, originalClosed);
    });
    animation.play();
  }

  private void removeSidebarToolTabAfterAnimation(Tab tab, TabPane pane, EventHandler<Event> originalClosed) {
    tab.getProperties().remove(SIDEBAR_TOOL_TAB_CLOSE_ACTIVE_KEY);
    if (pane != null) {
      pane.getTabs().remove(tab);
    }
    if (originalClosed != null) {
      originalClosed.handle(new Event(tab, tab, Tab.CLOSED_EVENT));
    }
    refreshOpenPanelChooserIndicators();
    scheduleEmptySidebarAutoClose(pane);
  }

  private void applySidebarToolStyle(Node node, EditorSidebarPanel panel) {
    if (node == null || panel == null) return;
    addStyleClassIfMissing(node, "sidebar-tool-root");
    addStyleClassIfMissing(node, "sidebar-tool-panel-" + panel.key().replace('_', '-'));
    if (node instanceof ScrollPane scrollPane) {
      addStyleClassIfMissing(scrollPane, "sidebar-tool-scroll");
      applySidebarToolStyle(scrollPane.getContent(), panel);
    }
  }

  private static void addStyleClassIfMissing(Node node, String styleClass) {
    if (node == null || styleClass == null || styleClass.isBlank()) return;
    if (!node.getStyleClass().contains(styleClass)) {
      node.getStyleClass().add(styleClass);
    }
  }

  private boolean isEditableFile(File f) {
    if (f == null || !f.isFile()) return false;
    String name = f.getName().toLowerCase();
    if ("menu.theme".equals(name) || "jvn.project".equals(name) || "vn.settings".equals(name)) return true;
    for (String ext : EDITABLE_EXTENSIONS) {
      if (name.endsWith(ext)) return true;
    }
    return false;
  }

  private boolean saveDirtyEditorsBeforeRun() {
    int savedFileTabs = 0;
    if (filesTabs != null) {
      for (Tab tab : new ArrayList<>(filesTabs.getTabs())) {
        if (!(tab.getContent() instanceof FileEditorTab ft) || !ft.isDirty()) continue;
        File target = ft.getFile();
        if (target == null) {
          showRunBlockedAlert("Run cancelled: unsaved file",
              "Save " + ft.getDisplayName() + " before running the project.");
          return false;
        }
        ft.saveTo(target);
        if (ft.isDirty()) {
          showRunBlockedAlert("Run cancelled: save failed",
              "Failed to save " + ft.getDisplayName() + ". Resolve save errors and run again.");
          return false;
        }
        savedFileTabs++;
      }
    }

    if (layoutStudioWindowManager != null) {
      boolean studiosSaved = layoutStudioWindowManager.saveAllDirty(msg -> {
        if (status != null && msg != null && !msg.isBlank()) status.setText(msg);
      });
      if (!studiosSaved) {
        showRunBlockedAlert("Run cancelled: Studio save failed",
            "One or more open Studio windows could not be saved. Resolve the save error and run again.");
        return false;
      }
    }

    if (savedFileTabs > 0) {
      refreshTabDirtyIndicators();
      if (status != null) status.setText("Saved " + savedFileTabs + " file(s) before run.");
    }
    return true;
  }

  private void showRunBlockedAlert(String header, String content) {
    if (status != null && header != null && !header.isBlank()) status.setText(header);
    EditorDialogs.show(dialogOwner(),
        "Run Blocked",
        header == null || header.isBlank() ? "Run cancelled" : header,
        createDialogDetailLabel(content),
        EditorDialogs.ActionSpec.accent("close", "Close", null));
  }

  private void closeActiveTab() {
    if (filesTabs == null) return;
    Tab active = filesTabs.getSelectionModel().getSelectedItem();
    if (active == null) return;
    if (!active.isClosable()) return;
    if (active.getContent() instanceof FileEditorTab ft && !confirmCanCloseFileTab(ft)) return;
    closeAndDisposeTab(active);
  }

  private void closeAllClosableTabs() {
    if (filesTabs == null) return;
    if (!confirmCloseAllTabs()) return;
    int closed = 0;
    for (Tab tab : new ArrayList<>(filesTabs.getTabs())) {
      if (!tab.isClosable()) continue;
      closeAndDisposeTab(tab);
      closed++;
    }
    refreshMainCommandUi.run();
    if (status != null) {
      status.setText(closed > 0 ? "Closed " + closed + " tab" + (closed == 1 ? "" : "s") + "." : "No closable tabs.");
    }
  }

  private boolean confirmCloseAllTabs() {
    if (filesTabs == null) return true;
    for (Tab tab : new ArrayList<>(filesTabs.getTabs())) {
      if (tab.getContent() instanceof FileEditorTab ft) {
        if (!confirmCanCloseFileTab(ft)) return false;
      }
    }
    return true;
  }

  private boolean confirmCanCloseFileTab(FileEditorTab ft) {
    if (ft == null || !ft.isDirty()) return true;
    Optional<String> result = EditorDialogs.show(
        dialogOwner(),
        "Unsaved Changes",
        "Save changes to " + ft.getDisplayName() + "?",
        createDialogDetailLabel("Your changes will be lost if you discard."),
        EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null),
        EditorDialogs.ActionSpec.danger("discard", "Discard", null),
        EditorDialogs.ActionSpec.accent("save", "Save", null));
    if (result.isEmpty() || "cancel".equals(result.get())) return false;
    if ("discard".equals(result.get())) return true;
    File f = ft.getFile();
    if (f == null) return false;
    ft.saveTo(f);
    refreshTabDirtyIndicators();
    return !ft.isDirty();
  }

  private void revealActiveFileInPathExplorer() {
    FileEditorTab ft = getActiveFileTab();
    File target = ft != null ? ft.getFile() : null;
    if (target == null) {
      if (status != null) status.setText("No active file to reveal.");
      return;
    }
    revealPathInExplorer(target,
        "Could not reveal " + ft.getDisplayName() + ".");
  }

  private void revealProjectRootInPathExplorer() {
    revealPathInExplorer(projectRoot, "Project root is not available.");
  }

  private void copyActiveFilePathToClipboard() {
    FileEditorTab ft = getActiveFileTab();
    File target = ft != null ? ft.getFile() : null;
    if (target == null) {
      if (status != null) status.setText("No active file path to copy.");
      return;
    }
    copyToClipboard(target.getAbsolutePath());
    if (status != null) status.setText("Copied path for " + ft.getDisplayName() + ".");
  }

  private void copyProjectRootPathToClipboard() {
    if (projectRoot == null) {
      if (status != null) status.setText("Project root is not available.");
      return;
    }
    copyToClipboard(projectRoot.getAbsolutePath());
    if (status != null) status.setText("Copied project root path.");
  }

  private void openProjectDocsFolder() {
    revealPathInExplorer(resolveDocsDirectory(projectRoot), "Project docs folder not found.");
  }

  private void openWorkspaceDocsFolder() {
    revealPathInExplorer(resolveDocsDirectory(resolveWorkspaceRoot()), "Workspace docs folder not found.");
  }

  private void revealPathInExplorer(File target, String missingMessage) {
    if (target == null || !target.exists()) {
      if (status != null && missingMessage != null && !missingMessage.isBlank()) status.setText(missingMessage);
      return;
    }
    if (EditorPathExplorer.show(target)) {
      if (status != null) status.setText("Revealed " + target.getName() + ".");
    } else if (status != null) {
      status.setText("Failed to reveal " + target.getAbsolutePath() + ".");
    }
  }

  private File resolveDocsDirectory(File root) {
    if (root == null) return null;
    File docs = new File(root, "docs");
    return docs.isDirectory() ? docs : null;
  }

  private void copyToClipboard(String text) {
    ClipboardContent content = new ClipboardContent();
    content.putString(text == null ? "" : text);
    Clipboard.getSystemClipboard().setContent(content);
  }

  private void refreshTabDirtyIndicators() {
    if (filesTabs == null) return;
    for (Tab t : filesTabs.getTabs()) {
      if (t.getContent() instanceof FileEditorTab ft) {
        updateTabTitle(t, ft);
      }
    }
  }

  private void updateTabTitle(Tab tab, FileEditorTab ft) {
    if (tab == null || ft == null) return;
    String base = ft.getDisplayName();
    String title = ft.isDirty() ? (base + " *") : base;
    if (!title.equals(tab.getText())) tab.setText(title);
  }

  private void openFile(File f) {
    if (f == null) return;

    if (layoutStudioWindowManager != null && layoutStudioWindowManager.supports(f)) {
      if (!closeFileTabIfOpen(f)) return;
      layoutStudioWindowManager.open(f, projectRoot, s -> {
        if (status != null && s != null && !s.isBlank()) status.setText(s);
      });
      return;
    }

    // Find existing tab
    for (Tab t : filesTabs.getTabs()) {
      if (t.getUserData() instanceof File ff && ff.equals(f)) {
        filesTabs.getSelectionModel().select(t);
        return;
      }
    }
    // Create new tab
    FileEditorTab editor = new FileEditorTab(f);
    if (projectRoot != null) editor.setProjectRoot(projectRoot);
    editor.setCodeEditorFontSize(editorPreferences.getCodeEditorFontSize());
    editor.setVnsAuthoringPreferences(
        editorPreferences.isVnsWordWrapByDefault(),
        editorPreferences.isVnsMinimapVisible(),
        editorPreferences.getLargeTimelineBlockActionThreshold());
    editor.setStoryboardOverlay(storyboardOverlayState);
    editor.setOnStoryboardOverlayAdjusted(this::setStoryboardOverlayState);
    editor.setOnSelected(ent -> {
      selected = ent;
      if (inspectorView != null) inspectorView.setSelection(ent);
      FileEditorTab ft = getActiveFileTab();
      if (ft != null) {
        JesScene2D scene = ft.getJesScene();
        if (scene != null && ent != null) {
          for (var e : scene.exportNamed().entrySet()) {
            if (e.getValue() == ent) { lastSelectedName = e.getKey(); break; }
          }
        }
      }
    });
    editor.setOnStatus(s -> status.setText(s));
    editor.setOnOpenDiagnostics(this::selectVnsDiagnosticsTab);
    editor.setCommandStack(commands);
    editor.setOnDiagnosticsTextChanged(text -> {
      if (editor != getActiveFileTab()) return;
      refreshVnsToolPanels(editor, text);
    });
    if (editor.getKind() == FileEditorTab.Kind.VNS) {
      editor.setOnVnsTextChanged(text -> {
        if (editor != getActiveFileTab()) return;
        if (puppeteerLauncherPanel != null) {
          puppeteerLauncherPanel.setProjectRoot(projectRoot);
          puppeteerLauncherPanel.setActiveScriptFile(editor.getFile());
          puppeteerLauncherPanel.setSource(text);
        }
      });
      editor.setOnVnsCaretLineChanged(line -> {
        if (editor != getActiveFileTab()) return;
        refreshStatusBarContext(editor);
        if (puppeteerLauncherPanel != null) puppeteerLauncherPanel.setCaretLine(line);
      });
    }
    Tab tab = new Tab(editor.getDisplayName(), editor);
    tab.setClosable(true);
    tab.setUserData(f);
    updateTabTitle(tab, editor);
    tab.setOnCloseRequest(e -> {
      if (!confirmCanCloseFileTab(editor)) e.consume();
    });
    tab.setOnClosed(e -> {
      try {
        editor.dispose();
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
    });
    filesTabs.getTabs().add(tab);
    filesTabs.getSelectionModel().select(tab);
    lastOpened = f;
    status.setText("Loaded: " + f.getName());
    updateContextForActiveTab();
    if (editor.getKind() == FileEditorTab.Kind.VNS || editor.getKind() == FileEditorTab.Kind.TIMELINE) {
      selectTimelineTab();
    }
  }

  private boolean closeFileTabIfOpen(File file) {
    if (filesTabs == null || file == null) return true;
    for (Tab tab : new ArrayList<>(filesTabs.getTabs())) {
      if (!(tab.getUserData() instanceof File openFile) || !openFile.equals(file)) continue;
      if (tab.getContent() instanceof FileEditorTab ft && !confirmCanCloseFileTab(ft)) {
        return false;
      }
      closeAndDisposeTab(tab);
    }
    return true;
  }

  private void applyProjectRootToTabs() {
    if (filesTabs == null) return;
    for (Tab t : filesTabs.getTabs()) {
      if (t.getContent() instanceof com.jvn.editor.ui.FileEditorTab fet) {
        fet.setProjectRoot(projectRoot);
      }
    }
    if (mapEditorView != null) mapEditorView.setProjectRoot(projectRoot);
    if (assetBrowserView != null) assetBrowserView.setProjectRoot(projectRoot);
    if (versionControlView != null) versionControlView.setProjectRoot(projectRoot);
    if (layoutEditorLauncherView != null) layoutEditorLauncherView.setProjectRoot(projectRoot);
    if (storyboardOverlayView != null) storyboardOverlayView.setProjectRoot(projectRoot);
    if (layeredImageVisualizerView != null) layeredImageVisualizerView.setProjectRoot(projectRoot);
    if (imageAttributesToolView != null) imageAttributesToolView.setProjectRoot(projectRoot);
    if (imageTintToolView != null) imageTintToolView.setProjectRoot(projectRoot);
    if (puppeteerLauncherPanel != null) puppeteerLauncherPanel.setProjectRoot(projectRoot);
    if (workspaceHubView != null) workspaceHubView.setCurrentProject(projectRoot);
    syncStoryboardOverlayProjectState();
    refreshStatusBarContext();
    refreshMainCommandUi.run();
  }

  private void applyEditorPreferences(EditorPreferences preferences) {
    editorPreferences = preferences == null ? EditorPreferences.defaults() : preferences.copy();
    if (editorSettingsView != null) {
      editorSettingsView.loadIntoForm(editorPreferences);
    }
    int maxFps = editorPreferences.getEditorMaxFps();
    if (maxFps <= 0) {
      targetFps = 60.0; // used only for perf HUD display
      minFrameIntervalNs = 0L; // uncapped: let AnimationTimer run every pulse
    } else {
      targetFps = maxFps;
      minFrameIntervalNs = (long) (1_000_000_000.0 / targetFps);
    }
    setEditorTheme(EditorPreferences.LAUNCHER_THEME_LIGHT.equals(editorPreferences.getEditorTheme())
        ? EditorTheme.Theme.LIGHT
        : EditorTheme.Theme.DARK,
        false);
    if (statusBar != null) {
      statusBar.applyStatusBarPreferences(editorPreferences);
    }
    applyCodeEditorFontSizePreference();
    applyVnsAuthoringPreferences();
    applyWelcomeTabPreference();
    applyDefaultSidebarPreferences();
    if (status != null) {
      status.setText("Editor preferences applied");
    }
  }

  private void rememberPanelPlacement(EditorSidebarPanel panel, EditorPanelPlacement placement) {
    if (panel == null || placement == null) return;
    editorPreferences.setPlacement(panel, placement);
    if (editorSettingsView != null) {
      editorSettingsView.loadIntoForm(editorPreferences);
    }
    persistEditorPreferences();
    refreshOpenPanelChooserIndicators();
  }

  private void persistEditorPreferences() {
    if (editorPreferencesStore == null || editorPreferences == null) return;
    try {
      editorPreferencesStore.save(editorPreferences);
    } catch (IOException ex) {
      if (status != null) {
        status.setText("Failed to save editor preferences: " + ex.getMessage());
      }
    }
  }

  private void saveWorkspaceStateToPreferences() {
    if (editorPreferences == null || centerSplit == null) return;
    
    // Save dividers only if we're not currently in a fullscreen mode that overwrites them
    if (!editorFullscreen && !layeredVisualizerFullscreen) {
        double[] divs = centerSplit.getDividerPositions();
        if (divs != null && divs.length >= 2) {
            editorPreferences.setCenterDividerLeft(divs[0]);
            editorPreferences.setCenterDividerRight(divs[1]);
        }
    } else if (savedCenterDividers != null && savedCenterDividers.length >= 2) {
        // If we are in fullscreen, save the pre-fullscreen backed up dividers
        editorPreferences.setCenterDividerLeft(savedCenterDividers[0]);
        editorPreferences.setCenterDividerRight(savedCenterDividers[1]);
    }
    
    // Save active tabs
    if (leftTabs != null) {
      Tab leftActive = leftTabs.getSelectionModel().getSelectedItem();
      if (leftActive != null) {
        String title = leftActive.getText();
        if (title != null && !title.isBlank()) editorPreferences.setActiveLeftTab(title);
      }
    }
    
    if (rightTabs != null) {
      Tab rightActive = rightTabs.getSelectionModel().getSelectedItem();
      if (rightActive != null) {
        String title = rightActive.getText();
        if (title != null && !title.isBlank()) editorPreferences.setActiveRightTab(title);
      }
    }
    
    persistEditorPreferences();
  }

  private void applyCodeEditorFontSizePreference() {
    double fontSize = editorPreferences.getCodeEditorFontSize();
    if (filesTabs != null) {
      for (Tab tab : filesTabs.getTabs()) {
        if (tab.getContent() instanceof FileEditorTab fileEditorTab) {
          fileEditorTab.setCodeEditorFontSize(fontSize);
        }
      }
    }
    if (scriptEditorLauncherView != null) {
      scriptEditorLauncherView.setCodeEditorFontSize(fontSize);
    }
  }

  private void applyVnsAuthoringPreferences() {
    if (filesTabs == null) return;
    boolean wordWrapEnabled = editorPreferences.isVnsWordWrapByDefault();
    boolean minimapVisible = editorPreferences.isVnsMinimapVisible();
    int largeTimelineBlockActionThreshold = editorPreferences.getLargeTimelineBlockActionThreshold();
    for (Tab tab : filesTabs.getTabs()) {
      if (tab.getContent() instanceof FileEditorTab fileEditorTab) {
        fileEditorTab.setVnsAuthoringPreferences(
            wordWrapEnabled, minimapVisible, largeTimelineBlockActionThreshold);
      }
    }
  }

  private void applyWelcomeTabPreference() {
    if (filesTabs == null || tabWorkspaceHub == null) return;
    boolean wantsWelcome = editorPreferences.isShowWelcomeOnStartup();
    boolean hasWelcome = filesTabs.getTabs().contains(tabWorkspaceHub);
    if (wantsWelcome && !hasWelcome) {
      filesTabs.getTabs().add(0, tabWorkspaceHub);
      if (filesTabs.getSelectionModel().getSelectedItem() == null) {
        filesTabs.getSelectionModel().select(tabWorkspaceHub);
      }
      return;
    }
    if (!wantsWelcome && hasWelcome) {
      boolean selected = filesTabs.getSelectionModel().getSelectedItem() == tabWorkspaceHub;
      filesTabs.getTabs().remove(tabWorkspaceHub);
      if (selected && !filesTabs.getTabs().isEmpty()) {
        filesTabs.getSelectionModel().select(filesTabs.getTabs().get(0));
      }
    }
  }

  private void applyDefaultSidebarPreferences() {
    if (leftTabs == null || rightTabs == null) return;
    EnumSet<EditorSidebarPanel> attachedBeforeRefresh = EnumSet.noneOf(EditorSidebarPanel.class);
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      if (isPanelAttached(panel)) {
        attachedBeforeRefresh.add(panel);
      }
    }
    boolean loadOnDemand = editorPreferences != null && editorPreferences.isLoadSidebarExtensionsOnDemand();
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      detachConfiguredPanel(panel);
    }
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      EditorPanelPlacement placement = editorPreferences.getPlacement(panel);
      if (!panel.supportsDocking()) {
        continue;
      }
      if (loadOnDemand
          && panel != EditorSidebarPanel.PROJECT
          && !attachedBeforeRefresh.contains(panel)) {
        continue;
      }
      if (placement == EditorPanelPlacement.LEFT) {
        ensureSidebarPanel(panel, leftTabs);
      } else if (placement == EditorPanelPlacement.RIGHT) {
        ensureSidebarPanel(panel, rightTabs);
      }
    }
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      releaseSidebarPanelIfUnused(panel);
    }
    releaseEditorSettingsIfUnused();
    
    // Restore active tabs
    String savedLeftTab = editorPreferences.getActiveLeftTab();
    Tab leftDefault = null;
    if (savedLeftTab != null && !savedLeftTab.isBlank()) {
      for (Tab t : leftTabs.getTabs()) {
        if (savedLeftTab.equals(t.getText())) {
          leftDefault = t;
          break;
        }
      }
    }
    if (leftDefault == null) leftDefault = firstRegularTab(leftTabs, tabLeftAdd);
    if (leftDefault != null) {
      leftTabs.getSelectionModel().select(leftDefault);
    }

    String savedRightTab = editorPreferences.getActiveRightTab();
    Tab rightDefault = null;
    if (savedRightTab != null && !savedRightTab.isBlank()) {
      for (Tab t : rightTabs.getTabs()) {
        if (savedRightTab.equals(t.getText())) {
          rightDefault = t;
          break;
        }
      }
    }
    if (rightDefault == null) rightDefault = firstRegularTab(rightTabs, tabRightAdd);
    if (rightDefault != null) {
      rightTabs.getSelectionModel().select(rightDefault);
    }

    if (centerSplit != null) {
      boolean hasLeft = firstRegularTab(leftTabs, tabLeftAdd) != null;
      boolean hasRight = firstRegularTab(rightTabs, tabRightAdd) != null;
      double leftDivider = hasLeft ? 0.22 : 0.0;
      double rightDivider = hasRight ? 0.78 : 1.0;
      centerSplit.setDividerPositions(leftDivider, rightDivider);
      savedCenterDividers = new double[] { leftDivider, rightDivider };
    }
    refreshOpenPanelChooserIndicators();
    scheduleEmptySidebarAutoClose(leftTabs);
    scheduleEmptySidebarAutoClose(rightTabs);
  }

  private void detachConfiguredPanel(EditorSidebarPanel panel) {
    Tab tab = configuredPanelTab(panel);
    if (tab != null && tab.getTabPane() != null) {
      TabPane pane = tab.getTabPane();
      tab.getTabPane().getTabs().remove(tab);
      refreshOpenPanelChooserIndicators();
      scheduleEmptySidebarAutoClose(pane);
    }
  }

  private boolean isPanelAttached(EditorSidebarPanel panel) {
    Tab tab = configuredPanelTab(panel);
    return tab != null && tab.getTabPane() != null;
  }

  private Tab configuredPanelTab(EditorSidebarPanel panel) {
    if (panel == null) return null;
    return switch (panel) {
      case PROJECT -> tabProject;
      case TRASHMAN -> tabTrashman;
      case TIMELINE -> tabTimeline;
      case INSPECTOR -> tabInspector;
      case VNS_DIAGNOSTICS -> tabVnsDiagnostics;
      case LABEL_FLOW -> tabVnsFlowMap;
      case TIMELINE_OUTLINE -> tabVnsTimelineOutline;
      case ASSETS -> tabAssetBrowser;
      case LAYOUT_LAUNCHER -> tabLayoutLauncher;
      case STORYBOARD_OVERLAY -> tabStoryboardOverlay;
      case LAYERED_IMAGES -> tabLayeredImageVisualizer;
      case IMAGE_ATTRIBUTES -> tabImageAttributesTool;
      case IMAGE_TINT -> tabImageTintTool;
      case VERSION_CONTROL -> tabVersionControl;
      case PUPPETEER_LAUNCHER -> tabPuppeteerLauncher;
      case SCRIPT_EDITOR -> tabScriptEditorLauncher;
    };
  }

  private Tab ensureSidebarPanel(EditorSidebarPanel panel, TabPane targetPane) {
    if (panel == null || targetPane == null) return null;
    if (!panel.supportsDocking()) return null;
    if (blockMaintenancePanelLaunch(panel, panel.displayName())) return null;
    return switch (panel) {
      case PROJECT -> ensureProjectTab(targetPane);
      case TRASHMAN -> ensureTrashmanTab(targetPane);
      case TIMELINE -> ensureTimelineTab(targetPane);
      case INSPECTOR -> ensureInspectorTab(targetPane);
      case VNS_DIAGNOSTICS -> ensureVnsDiagnosticsTab(targetPane);
      case LABEL_FLOW -> ensureVnsFlowMapTab(targetPane);
      case TIMELINE_OUTLINE -> ensureVnsTimelineOutlineTab(targetPane);
      case ASSETS -> ensureAssetBrowserTab(targetPane);
      case LAYOUT_LAUNCHER -> ensureLayoutLauncherTab(targetPane);
      case STORYBOARD_OVERLAY -> ensureStoryboardOverlayTab(targetPane);
      case LAYERED_IMAGES -> ensureLayeredImageVisualizerTab(targetPane);
      case IMAGE_ATTRIBUTES -> ensureImageAttributesToolTab(targetPane);
      case IMAGE_TINT -> ensureImageTintToolTab(targetPane);
      case VERSION_CONTROL -> ensureVersionControlTab(targetPane);
      case PUPPETEER_LAUNCHER -> ensurePuppeteerLauncherTab(targetPane);
      case SCRIPT_EDITOR -> ensureScriptEditorLauncherTab(targetPane);
    };
  }

  private void openEditableOrExternal(File target) {
    if (target == null) return;
    if (isEditableFile(target)) {
      openFile(target);
      return;
    }
    try {
      java.awt.Desktop.getDesktop().open(target);
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
  }

  private EditorSettingsView ensureEditorSettingsView() {
    if (editorSettingsView != null) return editorSettingsView;
    editorSettingsView = new EditorSettingsView(editorPreferencesStore);
    editorSettingsView.setOnPreferencesApplied(this::applyEditorPreferences);
    editorSettingsView.loadIntoForm(editorPreferences);
    return editorSettingsView;
  }

  private TrashmanView ensureTrashmanView() {
    if (trashmanView != null) return trashmanView;
    trashmanView = new TrashmanView();
    trashmanView.refreshStatus();
    return trashmanView;
  }

  private VnsDiagnosticsView ensureVnsDiagnosticsView() {
    if (vnsDiagnosticsView != null) return vnsDiagnosticsView;
    vnsDiagnosticsView = new VnsDiagnosticsView();
    vnsDiagnosticsView.setOnOpenTarget(this::jumpToActiveVnsDiagnostic);
    vnsDiagnosticsView.setOnRefresh(() -> {
      FileEditorTab activeTab = getActiveFileTab();
      refreshVnsToolPanels(
          activeTab,
          activeTab == null ? null : activeTab.getCurrentTextSnapshot());
    });
    FileEditorTab ft = getActiveFileTab();
    refreshVnsToolPanels(ft, ft != null ? ft.getCurrentTextSnapshot() : null);
    return vnsDiagnosticsView;
  }

  private VnsFlowMapView ensureVnsFlowMapView() {
    if (vnsFlowMapView != null) return vnsFlowMapView;
    vnsFlowMapView = new VnsFlowMapView();
    vnsFlowMapView.setOnOpenLine(this::jumpToActiveVnsLine);
    FileEditorTab ft = getActiveFileTab();
    refreshVnsToolPanels(ft, ft != null ? ft.getCurrentTextSnapshot() : null);
    return vnsFlowMapView;
  }

  private VnsTimelineOutlineView ensureVnsTimelineOutlineView() {
    if (vnsTimelineOutlineView != null) return vnsTimelineOutlineView;
    vnsTimelineOutlineView = new VnsTimelineOutlineView();
    vnsTimelineOutlineView.setOnOpenLine(this::jumpToActiveVnsLine);
    FileEditorTab ft = getActiveFileTab();
    refreshVnsToolPanels(ft, ft != null ? ft.getCurrentTextSnapshot() : null);
    return vnsTimelineOutlineView;
  }

  private PuppeteerLauncherPanel ensurePuppeteerLauncherPanel() {
    if (puppeteerLauncherPanel != null) return puppeteerLauncherPanel;
    puppeteerLauncherPanel = new PuppeteerLauncherPanel();
    puppeteerLauncherPanel.setProjectRoot(projectRoot);
    puppeteerLauncherPanel.setOnLaunch(this::launchPuppeteerFromLauncher);
    puppeteerLauncherPanel.setOnOpenTarget(this::openPuppeteerLauncherTarget);
    FileEditorTab ft = getActiveFileTab();
    if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
      puppeteerLauncherPanel.setActiveScriptFile(ft.getFile());
      puppeteerLauncherPanel.setSource(ft.getCurrentTextSnapshot());
      puppeteerLauncherPanel.setCaretLine(ft.getVnsCaretLine());
    }
    return puppeteerLauncherPanel;
  }

  private AssetBrowserView ensureAssetBrowserView() {
    if (assetBrowserView != null) return assetBrowserView;
    assetBrowserView = new AssetBrowserView();
    assetBrowserView.setProjectRoot(projectRoot);
    assetBrowserView.setOnOpenAsset(this::openEditableOrExternal);
    assetBrowserView.setOnAssetSelected(relativePath -> {
      FileEditorTab activeTab = getActiveFileTab();
      if (activeTab == null || activeTab.getKind() != FileEditorTab.Kind.VNS) {
        status.setText("Open a .vns file to insert an asset path.");
        return;
      }
      activeTab.insertVnsSnippet(AssetBrowserView.vnsTokenForPath(relativePath));
      activeTab.focusEditor();
      status.setText("Inserted asset path: " + relativePath);
    });
    return assetBrowserView;
  }

  private VersionControlView ensureVersionControlView() {
    if (versionControlView != null) return versionControlView;
    versionControlView = new VersionControlView();
    versionControlView.setProjectRoot(projectRoot);
    versionControlView.setOnOpenRelativePath(relativePath -> {
      if (projectRoot == null || relativePath == null || relativePath.isBlank()) return;
      File target = new File(projectRoot, relativePath);
      if (!target.exists()) return;
      if (isEditableFile(target)) openFile(target);
    });
    versionControlView.setOnWorkingTreeChanged(() -> {
      if (projView != null) projView.refresh();
      if (statusBar != null) statusBar.refreshGitState();
    });
    return versionControlView;
  }

  private LayoutEditorLauncherView ensureLayoutEditorLauncherView() {
    if (layoutEditorLauncherView != null) return layoutEditorLauncherView;
    layoutEditorLauncherView = new LayoutEditorLauncherView();
    layoutEditorLauncherView.setProjectRoot(projectRoot);
    layoutEditorLauncherView.setOnOpenFile(this::openEditableOrExternal);
    return layoutEditorLauncherView;
  }

  private StoryboardOverlayView ensureStoryboardOverlayView() {
    if (storyboardOverlayView != null) return storyboardOverlayView;
    storyboardOverlayView = new StoryboardOverlayView();
    storyboardOverlayView.setOnOverlayChanged(this::setStoryboardOverlayState);
    storyboardOverlayView.applyExternalState(storyboardOverlayState);
    storyboardOverlayView.setProjectRoot(projectRoot);
    refreshStoryboardOverlayContext(getActiveFileTab());
    return storyboardOverlayView;
  }

  private LayeredImageVisualizerView ensureLayeredImageVisualizerView() {
    if (layeredImageVisualizerView != null) return layeredImageVisualizerView;
    layeredImageVisualizerView = new LayeredImageVisualizerView();
    layeredImageVisualizerView.setProjectRoot(projectRoot);
    layeredImageVisualizerView.setOnToggleFullscreen(() -> toggleImageToolFullscreen(layeredImageVisualizerView));
    layeredImageVisualizerView.setFullscreenActive(false);
    return layeredImageVisualizerView;
  }

  private ImageAttributesToolView ensureImageAttributesToolView() {
    if (imageAttributesToolView != null) return imageAttributesToolView;
    imageAttributesToolView = new ImageAttributesToolView();
    imageAttributesToolView.setProjectRoot(projectRoot);
    imageAttributesToolView.setOnToggleFullscreen(() -> toggleImageToolFullscreen(imageAttributesToolView));
    imageAttributesToolView.setFullscreenActive(false);
    return imageAttributesToolView;
  }

  private ImageTintToolView ensureImageTintToolView() {
    if (imageTintToolView != null) return imageTintToolView;
    imageTintToolView = new ImageTintToolView();
    imageTintToolView.setProjectRoot(projectRoot);
    imageTintToolView.setOnToggleFullscreen(() -> toggleImageToolFullscreen(imageTintToolView));
    imageTintToolView.setFullscreenActive(false);
    return imageTintToolView;
  }

  private ScriptEditorLauncherView ensureScriptEditorLauncherView() {
    if (scriptEditorLauncherView != null) return scriptEditorLauncherView;
    scriptEditorLauncherView = new ScriptEditorLauncherView();
    scriptEditorLauncherView.setProjectRoot(projectRoot);
    scriptEditorLauncherView.setWorkspaceRoot(resolveWorkspaceRoot());
    scriptEditorLauncherView.setCodeEditorFontSize(editorPreferences.getCodeEditorFontSize());
    scriptEditorLauncherView.setOnStatus(message -> {
      if (status != null) status.setText(message);
    });
    scriptEditorLauncherView.setOnOpenFile(this::openFile);
    scriptEditorLauncherView.setOnOpenFileAtLine((file, line) -> {
      openFile(file);
      javafx.application.Platform.runLater(() -> {
        if (filesTabs == null) return;
        for (Tab t : filesTabs.getTabs()) {
          if (t.getUserData() instanceof File ff && ff.equals(file) && t.getContent() instanceof FileEditorTab ft) {
            ft.navigateToLine(line);
            break;
          }
        }
      });
    });
    return scriptEditorLauncherView;
  }

  private StoryTimelineView ensureTimelineView() {
    if (timelineView != null) return timelineView;
    timelineView = new StoryTimelineView();
    timelineView.setMinWidth(240);
    timelineView.setPrefWidth(320);
    timelineView.setOnRunArc(this::openTimelineArc);
    timelineView.setOnRunLink(this::openTimelineLinkTarget);
    if (projectRoot != null) {
      Properties mf = loadManifest(projectRoot);
      if (mf != null) {
        timelineView.setTimelineFile(resolveStoryMapFile(projectRoot, mf));
      }
      timelineView.setProjectRoot(projectRoot);
    }
    return timelineView;
  }

  private void ensureSidebarVisible(TabPane targetPane) {
    if (targetPane == null || centerSplit == null) return;
    double[] positions = centerSplit.getDividerPositions();
    double leftDivider = positions.length > 0 ? positions[0] : 0.22;
    double rightDivider = positions.length > 1 ? positions[1] : 0.78;
    if (targetPane == leftTabs && firstRegularTab(leftTabs, tabLeftAdd) != null && leftDivider <= 0.01) {
      leftDivider = 0.22;
    }
    if (targetPane == rightTabs && firstRegularTab(rightTabs, tabRightAdd) != null && rightDivider >= 0.99) {
      rightDivider = 0.78;
    }
    StackPane shell = targetPane == leftTabs ? leftSidebarShell : targetPane == rightTabs ? rightSidebarShell : null;
    setEmptySidebarWidthCap(targetPane, false);
    if (shell != null) shell.setOpacity(1.0);
    centerSplit.setDividerPositions(leftDivider, rightDivider);
    savedCenterDividers = new double[] { leftDivider, rightDivider };
    updateSidebarDividerHoverHints();
    scheduleEmptySidebarAutoClose(targetPane);
  }

  private void installEmptySidebarAutoClose(TabPane pane) {
    if (pane == null) return;
    pane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) change ->
        scheduleEmptySidebarAutoClose(pane));
    pane.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) ->
        scheduleEmptySidebarAutoClose(pane));
    pane.sceneProperty().addListener((o, ov, nv) -> scheduleEmptySidebarAutoClose(pane));
    Platform.runLater(() -> scheduleEmptySidebarAutoClose(pane));
  }

  private void scheduleEmptySidebarAutoClose(TabPane pane) {
    PauseTransition delay = emptySidebarDelayFor(pane);
    if (delay == null) return;
    delay.stop();
    if (!shouldAutoCloseEmptySidebar(pane)) {
      stopEmptySidebarCloseAnimation(pane);
      return;
    }
    delay.setDuration(EMPTY_SIDEBAR_AUTO_CLOSE_DELAY);
    delay.setOnFinished(e -> animateCloseEmptySidebar(pane));
    delay.playFromStart();
  }

  private PauseTransition emptySidebarDelayFor(TabPane pane) {
    if (pane == leftTabs) {
      if (leftEmptySidebarAutoCloseDelay == null) {
        leftEmptySidebarAutoCloseDelay = new PauseTransition(EMPTY_SIDEBAR_AUTO_CLOSE_DELAY);
      }
      return leftEmptySidebarAutoCloseDelay;
    }
    if (pane == rightTabs) {
      if (rightEmptySidebarAutoCloseDelay == null) {
        rightEmptySidebarAutoCloseDelay = new PauseTransition(EMPTY_SIDEBAR_AUTO_CLOSE_DELAY);
      }
      return rightEmptySidebarAutoCloseDelay;
    }
    return null;
  }

  private boolean shouldAutoCloseEmptySidebar(TabPane pane) {
    if (pane == null || centerSplit == null || centerSplit.getDividers().size() < 2) return false;
    if (hasSidebarToolTab(pane)) return false;
    if (findPanelChooserTab(pane, pane == leftTabs) != null) return false;
    if (pane == leftTabs) {
      return centerSplit.getDividers().get(0).getPosition() > SIDEBAR_COLLAPSED_EPSILON;
    }
    if (pane == rightTabs) {
      return centerSplit.getDividers().get(1).getPosition() < 1.0 - SIDEBAR_COLLAPSED_EPSILON;
    }
    return false;
  }

  private boolean hasSidebarToolTab(TabPane pane) {
    if (pane == null) return false;
    Tab addTab = getAddTabForPane(pane);
    for (Tab tab : pane.getTabs()) {
      if (tab == null || tab == addTab) continue;
      if (PANEL_CHOOSER_TAB_ROLE.equals(tab.getProperties().get(PANEL_CHOOSER_TAB_ROLE))) continue;
      return true;
    }
    return false;
  }

  private void animateCloseEmptySidebar(TabPane pane) {
    if (!shouldAutoCloseEmptySidebar(pane) || centerSplit.getDividers().size() < 2) return;
    Timeline existingAnimation = emptySidebarCloseAnimationFor(pane);
    if (existingAnimation != null) {
      existingAnimation.stop();
    }

    SplitPane.Divider divider = pane == leftTabs
        ? centerSplit.getDividers().get(0)
        : centerSplit.getDividers().get(1);
    StackPane shell = pane == leftTabs ? leftSidebarShell : rightSidebarShell;
    double target = pane == leftTabs ? 0.0 : 1.0;
    setEmptySidebarWidthCap(pane, false);
    if (shell != null) shell.setOpacity(1.0);

    Timeline animation = shell == null
        ? new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(divider.positionProperty(), divider.getPosition(), Interpolator.EASE_BOTH)),
            new KeyFrame(EMPTY_SIDEBAR_CLOSE_DURATION,
                new KeyValue(divider.positionProperty(), target, Interpolator.EASE_BOTH)))
        : new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(divider.positionProperty(), divider.getPosition(), Interpolator.EASE_BOTH),
                new KeyValue(shell.opacityProperty(), shell.getOpacity(), Interpolator.EASE_BOTH)),
            new KeyFrame(EMPTY_SIDEBAR_CLOSE_DURATION,
                new KeyValue(divider.positionProperty(), target, Interpolator.EASE_BOTH),
                new KeyValue(shell.opacityProperty(), 0.0, Interpolator.EASE_BOTH)));
    setEmptySidebarCloseAnimation(pane, animation);
    animation.setOnFinished(e -> {
      if (emptySidebarCloseAnimationFor(pane) == animation) {
        setEmptySidebarCloseAnimation(pane, null);
      }
      if (pane == leftTabs) {
        centerSplit.setDividerPositions(0.0, currentRightDividerPosition());
      } else {
        centerSplit.setDividerPositions(currentLeftDividerPosition(), 1.0);
      }
      if (shell != null) shell.setOpacity(1.0);
      setEmptySidebarWidthCap(pane, true);
      savedCenterDividers = centerSplit.getDividerPositions().clone();
      updateSidebarDividerHoverHints();
    });
    animation.play();
  }

  private Timeline emptySidebarCloseAnimationFor(TabPane pane) {
    if (pane == leftTabs) return leftEmptySidebarCloseAnimation;
    if (pane == rightTabs) return rightEmptySidebarCloseAnimation;
    return null;
  }

  private void setEmptySidebarCloseAnimation(TabPane pane, Timeline animation) {
    if (pane == leftTabs) {
      leftEmptySidebarCloseAnimation = animation;
    } else if (pane == rightTabs) {
      rightEmptySidebarCloseAnimation = animation;
    }
  }

  private void stopEmptySidebarCloseAnimation(TabPane pane) {
    Timeline animation = emptySidebarCloseAnimationFor(pane);
    boolean stoppedAnimation = animation != null;
    if (animation != null) {
      animation.stop();
      setEmptySidebarCloseAnimation(pane, null);
    }
    StackPane shell = pane == leftTabs ? leftSidebarShell : pane == rightTabs ? rightSidebarShell : null;
    if (stoppedAnimation || !isSidebarCollapsed(pane)) {
      setEmptySidebarWidthCap(pane, false);
    }
    if (shell != null) shell.setOpacity(1.0);
  }

  private boolean isSidebarCollapsed(TabPane pane) {
    if (pane == null || centerSplit == null || centerSplit.getDividers().size() < 2) return false;
    if (pane == leftTabs) {
      return centerSplit.getDividers().get(0).getPosition() <= SIDEBAR_COLLAPSED_EPSILON;
    }
    if (pane == rightTabs) {
      return centerSplit.getDividers().get(1).getPosition() >= 1.0 - SIDEBAR_COLLAPSED_EPSILON;
    }
    return false;
  }

  private void setEmptySidebarWidthCap(TabPane pane, boolean collapsed) {
    StackPane shell = pane == leftTabs ? leftSidebarShell : pane == rightTabs ? rightSidebarShell : null;
    if (shell == null || pane == null) return;
    double max = collapsed ? 0.0 : Double.MAX_VALUE;
    shell.setMaxWidth(max);
    pane.setMaxWidth(max);
  }

  private double currentLeftDividerPosition() {
    if (centerSplit == null || centerSplit.getDividers().isEmpty()) return 0.0;
    return centerSplit.getDividers().get(0).getPosition();
  }

  private double currentRightDividerPosition() {
    if (centerSplit == null || centerSplit.getDividers().size() < 2) return 1.0;
    return centerSplit.getDividers().get(1).getPosition();
  }

  private StackPane createSidebarShell(TabPane pane) {
    if (pane == null) return new StackPane();
    pane.setMinWidth(0);
    StackPane shell = new StackPane(pane);
    shell.setMinWidth(0);
    return shell;
  }

  private void installSidebarDividerHoverHints() {
    installSidebarDividerHoverHints(0);
  }

  private void installSidebarDividerHoverHints(int attempt) {
    if (centerSplit == null) return;
    Platform.runLater(() -> {
      List<StackPane> dividerNodes = centerSplit.lookupAll(".split-pane-divider").stream()
          .filter(StackPane.class::isInstance)
          .map(StackPane.class::cast)
          .sorted((a, b) -> Double.compare(a.getLayoutX(), b.getLayoutX()))
          .toList();
      if (dividerNodes.size() < 2) {
        if (attempt < 8) {
          PauseTransition retry = new PauseTransition(Duration.millis(80));
          retry.setOnFinished(e -> installSidebarDividerHoverHints(attempt + 1));
          retry.play();
        }
        return;
      }

      leftSidebarDividerNode = dividerNodes.get(0);
      rightSidebarDividerNode = dividerNodes.get(dividerNodes.size() - 1);
      leftSidebarHiddenArrow = ensureDividerArrow(leftSidebarDividerNode, true);
      rightSidebarHiddenArrow = ensureDividerArrow(rightSidebarDividerNode, false);

      leftSidebarDividerNode.hoverProperty().addListener((o, ov, nv) -> updateSidebarDividerHoverHints());
      rightSidebarDividerNode.hoverProperty().addListener((o, ov, nv) -> updateSidebarDividerHoverHints());
      installSidebarResizeHandlers(leftSidebarDividerNode, leftTabs);
      installSidebarResizeHandlers(rightSidebarDividerNode, rightTabs);
      if (centerSplit.getDividers().size() >= 2) {
        centerSplit.getDividers().get(0).positionProperty().addListener((o, ov, nv) -> {
          updateSidebarDividerHoverHints();
          scheduleEmptySidebarAutoClose(leftTabs);
        });
        centerSplit.getDividers().get(1).positionProperty().addListener((o, ov, nv) -> {
          updateSidebarDividerHoverHints();
          scheduleEmptySidebarAutoClose(rightTabs);
        });
      }
      updateSidebarDividerHoverHints();
    });
  }

  private void installSidebarResizeHandlers(StackPane divider, TabPane targetPane) {
    if (divider == null || targetPane == null) return;
    if (Boolean.TRUE.equals(divider.getProperties().get(SIDEBAR_RESIZE_HANDLER_KEY))) return;
    divider.getProperties().put(SIDEBAR_RESIZE_HANDLER_KEY, true);

    divider.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
      if (event.getButton() == MouseButton.PRIMARY) {
        setEmptySidebarWidthCap(targetPane, false);
      }
    });
    divider.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
      if (event.isPrimaryButtonDown()) {
        setEmptySidebarWidthCap(targetPane, false);
      }
    });
  }

  private Region ensureDividerArrow(StackPane divider, boolean leftSide) {
    if (divider == null) return null;
    for (Node child : divider.getChildren()) {
      if (child instanceof Region region && region.getStyleClass().contains("sidebar-hidden-drag-arrow")) {
        return region;
      }
    }
    Region arrow = new Region();
    arrow.getStyleClass().add("sidebar-hidden-drag-arrow");
    arrow.getStyleClass().add(leftSide ? "left" : "right");
    arrow.setManaged(false);
    arrow.setVisible(false);
    arrow.setOpacity(0.0);
    arrow.setMouseTransparent(true);
    divider.getChildren().add(arrow);
    return arrow;
  }

  private void updateSidebarDividerHoverHints() {
    if (centerSplit == null || centerSplit.getDividers().size() < 2) return;
    boolean leftHidden = centerSplit.getDividers().get(0).getPosition() <= SIDEBAR_COLLAPSED_EPSILON;
    boolean rightHidden = centerSplit.getDividers().get(1).getPosition() >= (1.0 - SIDEBAR_COLLAPSED_EPSILON);
    updateSidebarDividerArrow(leftSidebarDividerNode, leftSidebarHiddenArrow, leftHidden);
    updateSidebarDividerArrow(rightSidebarDividerNode, rightSidebarHiddenArrow, rightHidden);
  }

  private void updateSidebarDividerArrow(StackPane divider, Region arrow, boolean collapsed) {
    if (arrow == null) return;
    if (!collapsed) {
      arrow.setVisible(false);
      arrow.setOpacity(0.0);
      return;
    }
    arrow.setVisible(true);
    boolean shouldShow = divider != null && divider.isHover();
    animateSidebarDividerArrow(arrow, shouldShow ? 1.0 : 0.0);
  }

  private void animateSidebarDividerArrow(javafx.scene.Node arrow, double targetOpacity) {
    if (arrow == null) return;
    Object existing = arrow.getProperties().get("sidebar-arrow-fade");
    if (existing instanceof FadeTransition fade) {
      fade.stop();
    }
    if (Math.abs(arrow.getOpacity() - targetOpacity) < 0.01) {
      arrow.setOpacity(targetOpacity);
      return;
    }
    FadeTransition fade = new FadeTransition(Duration.millis(140), arrow);
    fade.setToValue(targetOpacity);
    arrow.getProperties().put("sidebar-arrow-fade", fade);
    fade.play();
  }

  private FileEditorTab getActiveFileTab() {
    Tab t = filesTabs.getSelectionModel().getSelectedItem();
    if (t == null) return null;
    return (t.getContent() instanceof FileEditorTab) ? (FileEditorTab) t.getContent() : null;
  }

  private void updateContextForActiveTab() {
    FileEditorTab ft = getActiveFileTab();
    refreshStatusBarContext(ft);
    stopPreviewAudioInInactiveTabs(ft);
    JesScene2D scene = (ft != null) ? ft.getJesScene() : null;
    if (inspectorView != null) inspectorView.setScene(scene);
    if (mapEditorView != null) {
      if (ft != null && ft.getKind() == FileEditorTab.Kind.JES && ft.getFile() != null && projectRoot != null) {
        mapEditorView.setContext(projectRoot, ft.getFile());
      } else {
        mapEditorView.clearContext();
      }
    }
    refreshStoryboardOverlayContext(ft);
    refreshVnsToolPanels(ft, null);
    if (puppeteerLauncherPanel != null) {
      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
        puppeteerLauncherPanel.setProjectRoot(projectRoot);
        puppeteerLauncherPanel.setActiveScriptFile(ft.getFile());
        puppeteerLauncherPanel.setSource(ft.getCurrentTextSnapshot());
        puppeteerLauncherPanel.setCaretLine(ft.getVnsCaretLine());
      } else {
        puppeteerLauncherPanel.setActiveScriptFile(null);
        puppeteerLauncherPanel.clear();
      }
    }
    refreshMainCommandUi.run();
  }

  private void refreshStatusBarContext() {
    refreshStatusBarContext(getActiveFileTab());
  }

  private void refreshStatusBarContext(FileEditorTab ft) {
    if (statusBar == null) return;
    statusBar.setProjectRoot(projectRoot);
    statusBar.setTheme(EditorTheme.theme());
    statusBar.setWorkspaceState(countDirtyFileTabs(), countClosableTabs(), commands.canUndo(), commands.canRedo());
    MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    statusBar.setMemoryUsage(heap.getUsed(), heap.getCommitted(), heap.getMax());
    if (ft == null) {
      statusBar.setActiveFile(null, null, -1);
      statusBar.setDiagnostics(0, 0);
      return;
    }
    String kind = ft.getKind() == null ? "" : ft.getKind().name();
    int line = ft.getKind() == FileEditorTab.Kind.VNS ? ft.getVnsCaretLine() + 1 : -1;
    statusBar.setActiveFile(ft.getDisplayName(), kind, line, ft.getFile());
    int errors = 0;
    int warnings = 0;
    for (VnsDiagnosticsView.Diagnostic issue : diagnosticsFor(ft, ft.getCurrentTextSnapshot())) {
      if (issue.warning()) warnings++;
      else errors++;
    }
    statusBar.setDiagnostics(errors, warnings);
  }

  private void closeAndDisposeTab(Tab tab) {
    if (filesTabs == null || tab == null) return;
    if (tab.getContent() instanceof FileEditorTab ft) {
      try {
        ft.dispose();
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
    }
    filesTabs.getTabs().remove(tab);
    refreshMainCommandUi.run();
  }

  private void stopPreviewAudioInInactiveTabs(FileEditorTab active) {
    if (filesTabs == null) return;
    for (Tab tab : filesTabs.getTabs()) {
      if (!(tab.getContent() instanceof FileEditorTab ft)) continue;
      if (ft == active) continue;
      try {
        ft.stopPreviewAudio();
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
    }
  }

  private void stopAllPreviewAudio() {
    if (filesTabs == null) return;
    for (Tab tab : filesTabs.getTabs()) {
      if (!(tab.getContent() instanceof FileEditorTab ft)) continue;
      try {
        ft.stopPreviewAudio();
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
    }
  }

  private void disposeAllFileTabs() {
    if (filesTabs == null) return;
    for (Tab tab : new ArrayList<>(filesTabs.getTabs())) {
      if (tab.getContent() instanceof FileEditorTab ft) {
        try {
          ft.dispose();
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        }
      }
    }
  }

  private void refreshVnsToolPanels(FileEditorTab fileTab, String currentText) {
    if (vnsDiagnosticsView == null && vnsFlowMapView == null && vnsTimelineOutlineView == null) return;
    if (fileTab == null) {
      if (vnsDiagnosticsView != null) vnsDiagnosticsView.clear();
      if (vnsFlowMapView != null) vnsFlowMapView.clear();
      if (vnsTimelineOutlineView != null) vnsTimelineOutlineView.clear();
      return;
    }

    File scriptFile = fileTab.getFile();
    String source = currentText != null ? currentText : fileTab.getCurrentTextSnapshot();
    if (fileTab.getKind() != FileEditorTab.Kind.VNS) {
      if (vnsFlowMapView != null) vnsFlowMapView.clear();
      if (vnsTimelineOutlineView != null) vnsTimelineOutlineView.clear();
      if (vnsDiagnosticsView != null) {
        vnsDiagnosticsView.setDiagnostics(
            scriptFile,
            diagnosticsLanguageLabel(fileTab),
            source,
            diagnosticsStatsSummary(fileTab, source),
            diagnosticsFor(fileTab, source));
      }
      return;
    }

    File analysisRoot = resolveVnsProjectRoot(scriptFile);

    VnsScriptAnalyzer.Analysis analysis = VnsScriptAnalyzer.analyze(source, analysisRoot, scriptFile);
    if (vnsDiagnosticsView != null) vnsDiagnosticsView.setAnalysis(scriptFile, analysis);
    if (vnsFlowMapView != null) vnsFlowMapView.setAnalysis(scriptFile, analysis);
    if (vnsTimelineOutlineView != null) {
      VnsCodeEditor vnsEditor = fileTab.getVnsEditor();
      if (vnsEditor != null) vnsTimelineOutlineView.scheduleRefresh(scriptFile, vnsEditor);
    }
  }

  private List<VnsDiagnosticsView.Diagnostic> diagnosticsFor(FileEditorTab fileTab, String source) {
    if (fileTab == null) return List.of();
    String text = source == null ? "" : source;
    return switch (fileTab.getKind()) {
      case JES -> diagnosticsFromLanguage(JesScriptAnalyzer.analyze(
          text,
          projectRoot,
          fileTab.getFile(),
          JesScriptAnalyzer.Mode.JES_DOCUMENT).diagnostics());
      case TIMELINE -> diagnosticsFromTimeline(text);
      case MENU_SCREEN -> diagnosticsFromDslStrings(text, DslPropertyDiagnostics.menuScreenIssues(
          text,
          Set.of("titleText", "subtitleText", "hintsText", "layout", "layoutId",
              "defaultItemStyle", "wrapSelection", "items", "backgroundAsset"),
          Set.of("label", "style", "icon", "enabled", "action", "target",
              "bgAsset", "bgSelectedAsset", "bgDisabledAsset",
              "boundsX", "boundsY", "boundsWidth", "boundsHeight",
              "slotPreviewEnabled", "slotPreviewPlaceholderAsset", "slotPreviewFrameAsset",
              "slotPreviewX", "slotPreviewY", "slotPreviewWidth", "slotPreviewHeight",
              "sliderX", "sliderY", "sliderWidth",
              "sliderTrackAsset", "sliderBaseAsset", "sliderTrackHeight", "sliderShowFill",
              "sliderFillAsset", "sliderFillActiveAsset", "sliderFillInactiveAsset",
              "sliderKnobAsset", "sliderKnobActiveAsset", "sliderKnobInactiveAsset",
              "sliderKnobWidth", "sliderKnobHeight", "sliderKnobOffsetX", "sliderKnobOffsetY",
              "sliderResetAsset", "sliderResetActiveAsset", "sliderResetInactiveAsset",
              "sliderResetX", "sliderResetY", "sliderResetWidth", "sliderResetHeight",
              "toggleCheckedAsset", "toggleUncheckedAsset",
              "toggleX", "toggleY", "toggleWidth", "toggleHeight")));
      case MENU_LAYOUT -> diagnosticsFromDslStrings(text, DslPropertyDiagnostics.menuLayoutIssues(
          text,
          Set.of("listYStart", "lineHeight", "listWidthFactor", "textAlign", "hintsBottomMargin",
              "titleY", "subtitleGap", "listXCenter", "titleX", "maxVisibleItems", "titleAlign", "hintsAlign", "hintsX")));
      case MENU_STYLE -> diagnosticsFromDslStrings(text, DslPropertyDiagnostics.menuStyleIssues(
          text,
          Set.of("extends", "itemColor", "itemSelectedColor", "itemHoverColor", "itemDisabledColor",
              "itemPrefix", "itemSelectedPrefix", "itemDisabledPrefix",
              "itemFontFamily", "itemFontWeight", "itemFontSize",
              "itemShadowColor", "itemShadowOffsetX", "itemShadowOffsetY", "itemOpacity",
              "buttonAsset", "buttonSelectedAsset", "buttonHoverAsset", "buttonDisabledAsset",
              "buttonTextPaddingX", "buttonTextPaddingY",
              "titleColor", "titleFontFamily", "titleFontWeight", "titleFontSize", "titleShadowColor",
              "hintsColor", "hintsFontFamily", "hintsFontWeight", "hintsFontSize",
              "backgroundAsset", "backgroundColor", "backgroundOpacity")));
      case DIALOGUE_LAYOUT -> diagnosticsFromDslStrings(text, DslPropertyDiagnostics.dialogueIssues(text, List.of()));
      default -> List.of();
    };
  }

  private List<VnsDiagnosticsView.Diagnostic> diagnosticsFromLanguage(List<LanguageDiagnostic> diagnostics) {
    if (diagnostics == null || diagnostics.isEmpty()) return List.of();
    List<VnsDiagnosticsView.Diagnostic> out = new ArrayList<>();
    for (LanguageDiagnostic diagnostic : diagnostics) {
      if (diagnostic == null || diagnostic.severity() == LanguageDiagnostic.Severity.INFO) continue;
      boolean warning = diagnostic.severity() == LanguageDiagnostic.Severity.WARNING;
      out.add(new VnsDiagnosticsView.Diagnostic(
          warning,
          diagnostic.code(),
          diagnostic.message(),
          diagnostic.startOffset(),
          diagnostic.endOffset(),
          diagnostic.line()));
    }
    return out;
  }

  private List<VnsDiagnosticsView.Diagnostic> diagnosticsFromTimeline(String source) {
    List<TimelineDiagnostic.Message> messages = TimelineDiagnostic.diagnoseDsl(source);
    if (messages.isEmpty()) return List.of();
    List<VnsDiagnosticsView.Diagnostic> out = new ArrayList<>();
    for (TimelineDiagnostic.Message message : messages) {
      if (message == null || message.severity() == TimelineDiagnostic.Severity.INFO) continue;
      int zeroBasedLine = Math.max(0, message.line() - 1);
      int start = offsetForLineColumn(source, zeroBasedLine, 0);
      int end = Math.max(start + 1, lineEndOffset(source, zeroBasedLine));
      String kind = message.entityOrTrack() == null || message.entityOrTrack().isBlank()
          ? "timeline"
          : message.entityOrTrack();
      String text = message.description();
      if (message.quickFix() != null && !message.quickFix().isBlank()) {
        text += " Quick fix: " + message.quickFix();
      }
      out.add(new VnsDiagnosticsView.Diagnostic(
          message.severity() == TimelineDiagnostic.Severity.WARNING,
          kind,
          text,
          start,
          end,
          zeroBasedLine));
    }
    return out;
  }

  private List<VnsDiagnosticsView.Diagnostic> diagnosticsFromDslStrings(String source, List<String> issues) {
    if (issues == null || issues.isEmpty()) return List.of();
    List<VnsDiagnosticsView.Diagnostic> out = new ArrayList<>();
    for (String raw : issues) {
      if (raw == null || raw.isBlank()) continue;
      String issue = raw.trim();
      java.util.regex.Matcher matcher = DSL_DIAGNOSTIC_PATTERN.matcher(issue);
      int zeroBasedLine = 0;
      String kind = "dsl";
      String message = issue;
      if (matcher.matches()) {
        zeroBasedLine = Math.max(0, Integer.parseInt(matcher.group(1)) - 1);
        kind = matcher.group(2);
        message = matcher.group(3);
        if (matcher.group(4) != null && !matcher.group(4).isBlank()) {
          message += " Quick fix: " + matcher.group(4);
        }
      }
      int start = offsetForLineColumn(source, zeroBasedLine, 0);
      int end = Math.max(start + 1, lineEndOffset(source, zeroBasedLine));
      boolean warning = !(message.toLowerCase(Locale.ROOT).contains("unknown")
          || message.toLowerCase(Locale.ROOT).contains("invalid")
          || message.toLowerCase(Locale.ROOT).contains("malformed"));
      out.add(new VnsDiagnosticsView.Diagnostic(warning, kind, message, start, end, zeroBasedLine));
    }
    return out;
  }

  private String diagnosticsLanguageLabel(FileEditorTab fileTab) {
    if (fileTab == null) return "File";
    return switch (fileTab.getKind()) {
      case JES -> "JES";
      case TIMELINE -> "Story Map DSL";
      case JAVA -> "Java";
      case THEME -> "Theme DSL";
      case MENU_SCREEN -> "Menu screen DSL";
      case MENU_LAYOUT -> "Menu layout DSL";
      case MENU_STYLE -> "Menu style DSL";
      case DIALOGUE_LAYOUT -> "Dialogue layout DSL";
      case OTHER -> "Text";
      case VNS -> "VNS";
    };
  }

  private String diagnosticsStatsSummary(FileEditorTab fileTab, String source) {
    if (fileTab == null) return "";
    String text = source == null ? "" : source;
    long nonBlankLines = text.lines().filter(line -> !line.isBlank()).count();
    return switch (fileTab.getKind()) {
      case JES -> {
        JesScriptAnalyzer.Analysis analysis = JesScriptAnalyzer.analyze(
            text,
            projectRoot,
            fileTab.getFile(),
            JesScriptAnalyzer.Mode.JES_DOCUMENT);
        yield analysis.entityNames().size() + " entities"
            + " | " + analysis.timelineLabelNames().size() + " timeline labels"
            + " | " + nonBlankLines + " lines";
      }
      case TIMELINE -> nonBlankLines + " story map DSL lines";
      case MENU_SCREEN, MENU_LAYOUT, MENU_STYLE, DIALOGUE_LAYOUT, THEME -> nonBlankLines + " DSL lines";
      default -> "No diagnostics provider for this file type";
    };
  }

  private static int offsetForLineColumn(String source, int targetLine, int targetColumn) {
    String text = source == null ? "" : source;
    if (text.isEmpty()) return 0;
    int line = 0;
    int lineStart = 0;
    for (int i = 0; i < text.length() && line < targetLine; i++) {
      if (text.charAt(i) == '\n') {
        line++;
        lineStart = i + 1;
      }
    }
    return Math.min(text.length(), lineStart + Math.max(0, targetColumn));
  }

  private static int lineEndOffset(String source, int targetLine) {
    String text = source == null ? "" : source;
    if (text.isEmpty()) return 0;
    int start = offsetForLineColumn(text, targetLine, 0);
    int next = text.indexOf('\n', start);
    return next < 0 ? text.length() : next;
  }

  private void jumpToActiveVnsLine(int oneBasedLine) {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null || ft.getKind() != FileEditorTab.Kind.VNS) return;
    ft.navigateToLine(oneBasedLine);
  }

  private void jumpToActiveVnsDiagnostic(VnsDiagnosticsView.OpenTarget target) {
    if (target == null) return;
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return;

    if (ft.getKind() == FileEditorTab.Kind.VNS && target.startOffset() >= 0) {
      ft.navigateToRange(target.startOffset(), target.endOffset());
      return;
    }
    ft.navigateToLine(target.oneBasedLine());
  }

  private void openPuppeteerLauncherTarget(PuppeteerLauncherPanel.OpenTarget target) {
    if (target == null || target.file() == null) return;
    File file = target.file();
    openFile(file);
    if (target.oneBasedLine() <= 0) return;
    Platform.runLater(() -> {
      if (filesTabs == null) return;
      for (Tab t : filesTabs.getTabs()) {
        if (t.getUserData() instanceof File ff && ff.equals(file) && t.getContent() instanceof FileEditorTab ft) {
          ft.navigateToLine(target.oneBasedLine());
          break;
        }
      }
    });
  }

  private void fitCameraToEntity(Entity2D e) {
    FileEditorTab ft = getActiveFileTab();
    if (ft != null && ft.getViewport() != null) {
      ft.getViewport().fitToEntity(e);
    }
  }

  private void resetCamera() {
    FileEditorTab ft = getActiveFileTab();
    if (ft != null && ft.getViewport() != null) {
      ft.getViewport().getCamera().setPosition(0,0);
      ft.getViewport().getCamera().setZoom(1.0);
    }
  }

  private void fitCameraToContent() {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return;
    
    // For VNS files, open fullscreen preview
    if (ft.getKind() == FileEditorTab.Kind.VNS && ft.getVnPreview() != null) {
      openFullscreenVnPreview(ft);
      return;
    }
    
    // For JES files, fit camera to content
    ft.fitToContent();
  }

  private void openFullscreenVnPreview(FileEditorTab sourceTab) {
    if (sourceTab == null) return;
    // Prevent the embedded tab preview from leaking BGM while fullscreen preview is open.
    try {
      sourceTab.stopPreviewAudio();
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }

    // Create a new fullscreen stage with VN preview
    javafx.stage.Stage fullscreenStage = new javafx.stage.Stage();
    fullscreenStage.setTitle("VN Preview - " + (sourceTab.getFile() != null ? sourceTab.getFile().getName() : "Untitled"));
    
    // Create a new VnPreviewView for fullscreen
    com.jvn.editor.ui.VnPreviewView fullscreenPreview = new com.jvn.editor.ui.VnPreviewView();
    File previewRoot = resolveVnsProjectRoot(sourceTab.getFile());
    if (previewRoot != null) fullscreenPreview.setProjectRoot(previewRoot);
    
    // Copy the scenario from the source tab
    String sourceName = resolveVnsSourceName(sourceTab.getFile(), previewRoot);
    try {
      String code = null;
      var editorNode = sourceTab.getEditorNode();
      if (editorNode instanceof com.jvn.editor.ui.VnsCodeEditor vnsEditor) {
        code = vnsEditor.getText();
      }
      if (code != null && !code.isBlank()) {
        com.jvn.core.vn.script.VnScriptParser parser = new com.jvn.core.vn.script.VnScriptParser();
        byte[] bytes = code.getBytes(StandardCharsets.UTF_8);
        com.jvn.core.vn.VnScenario scenario;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
          scenario = parser.parse(in, sourceName, includePath -> openVnsIncludeForEditor(sourceTab, includePath));
        }
        fullscreenPreview.setSourceScriptName(sourceName);
        fullscreenPreview.setScenario(scenario);
      }
    } catch (Exception ex) {
      status.setText("Failed to load VN for fullscreen: " + ex.getMessage());
      fullscreenPreview.setSourceScriptName(sourceName);
      fullscreenPreview.setActiveError(com.jvn.core.vn.VnErrorOverlay.fromScriptLoadFailure(sourceName, ex));
    }
    
    CompositionGuideOverlay fullscreenGuides = new CompositionGuideOverlay();
    ProjectViewportSpec.Dimensions fullscreenDimensions = ProjectViewportSpec.resolve(previewRoot);
    fullscreenGuides.setVirtualResolution(fullscreenDimensions.width(), fullscreenDimensions.height());
    javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(fullscreenPreview, fullscreenGuides);
    root.setStyle("-fx-background-color: black;");
    javafx.scene.Scene scene = new javafx.scene.Scene(root, 1280, 720);
    
    // Animation timer for rendering
    javafx.animation.AnimationTimer timer = new javafx.animation.AnimationTimer() {
      final com.jvn.editor.ui.PreviewFramePacer pacer =
          com.jvn.editor.ui.PreviewFramePacer.forCurrentPipeline();
      @Override
      public void handle(long now) {
        com.jvn.editor.ui.PreviewFramePacer.Frame frame = pacer.next(now);
        if (!frame.render()) return;
        fullscreenPreview.setSize(scene.getWidth(), scene.getHeight());
        fullscreenPreview.render(frame.deltaMs());
      }
    };
    
    fullscreenStage.setScene(scene);
    fullscreenStage.setFullScreen(true);
    fullscreenStage.setFullScreenExitHint("Press ESC to exit fullscreen");

    // Ensure fullscreen preview never leaks audio/timers after close.
    final boolean[] cleaned = new boolean[] {false};
    Runnable cleanup = () -> {
      if (cleaned[0]) return;
      cleaned[0] = true;
      try {
        timer.stop();
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
      try {
        fullscreenPreview.stopAudio();
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
      try {
        fullscreenPreview.dispose();
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
      try {
        sourceTab.stopPreviewAudio();
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
    };
    fullscreenStage.setOnCloseRequest(e -> cleanup.run());
    fullscreenStage.setOnHidden(e -> cleanup.run());
    
    fullscreenStage.show();
    fullscreenPreview.setPlaybackActive(true);
    timer.start();
    fullscreenPreview.requestFocus();
  }

  private java.io.InputStream openVnsIncludeForEditor(FileEditorTab tab, String includePath) throws java.io.IOException {
    if (tab == null) throw new java.io.IOException("Include resolver unavailable");
    File sourceFile = tab.getFile();
    if (sourceFile == null) throw new java.io.IOException("Include resolver unavailable");
    File root = resolveVnsProjectRoot(sourceFile);
    if (root == null) throw new java.io.IOException("Include resolver unavailable");

    Path rootPath = root.toPath().toAbsolutePath().normalize();
    Path scriptsRoot = resolveVnsScriptsRoot(root);
    if (scriptsRoot == null) scriptsRoot = rootPath;

    String normalized = includePath == null ? "" : includePath.trim().replace('\\', '/');
    if (normalized.isBlank()) {
      throw new java.io.IOException("Include path is empty");
    }

    List<Path> candidates = new ArrayList<>();
    if (normalized.startsWith("/")) {
      candidates.add(scriptsRoot.resolve(normalized.substring(1)));
    } else {
      Path sourcePath = sourceFile.toPath().toAbsolutePath().normalize();
      Path sourceParent = sourcePath.getParent();
      if (sourceParent != null) {
        candidates.add(sourceParent.resolve(normalized));
      }
      candidates.add(scriptsRoot.resolve(normalized));
    }
    candidates.add(rootPath.resolve(normalized));

    for (Path candidate : candidates) {
      Path resolved = candidate.toAbsolutePath().normalize();
      if (!resolved.startsWith(rootPath)) continue;
      if (Files.isRegularFile(resolved)) {
        return Files.newInputStream(resolved);
      }
    }
    throw new java.io.IOException("Included script not found: " + includePath);
  }

  private File resolveVnsProjectRoot(File scriptFile) {
    if (projectRoot != null) return projectRoot;
    File inferred = inferProjectRootFromScript(scriptFile);
    if (inferred != null) return inferred;
    return scriptFile == null ? null : scriptFile.getParentFile();
  }

  private Path resolveVnsScriptsRoot(File root) {
    if (root == null) return null;
    Path rootPath = root.toPath().toAbsolutePath().normalize();
    Path scriptsRoot = rootPath.resolve("scripts").normalize();
    if (Files.isDirectory(scriptsRoot)) return scriptsRoot;
    return rootPath;
  }

  private String resolveVnsSourceName(File sourceFile, File root) {
    if (sourceFile == null) return "<editor>";
    Path filePath = sourceFile.toPath().toAbsolutePath().normalize();
    Path scriptsRoot = resolveVnsScriptsRoot(root);
    if (scriptsRoot != null && filePath.startsWith(scriptsRoot)) {
      return scriptsRoot.relativize(filePath).toString().replace('\\', '/');
    }
    if (root != null) {
      Path rootPath = root.toPath().toAbsolutePath().normalize();
      if (filePath.startsWith(rootPath)) {
        return rootPath.relativize(filePath).toString().replace('\\', '/');
      }
    }
    return sourceFile.getName();
  }

  private File inferProjectRootFromScript(File scriptFile) {
    if (scriptFile == null) return null;
    Path current = scriptFile.toPath().toAbsolutePath().normalize().getParent();
    while (current != null) {
      Path name = current.getFileName();
      if (name != null && "scripts".equalsIgnoreCase(name.toString())) {
        Path parent = current.getParent();
        return (parent != null ? parent : current).toFile();
      }
      current = current.getParent();
    }
    return null;
  }

  private void installAddTabBehavior(TabPane pane, Tab addTab, Runnable onAddRequested) {
    if (pane == null || addTab == null || onAddRequested == null) return;
    pane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (!isAddTabHeaderClick(e.getTarget(), addTab)) return;
      Tab selected = pane.getSelectionModel().getSelectedItem();
      triggerAddTabChooser(pane, addTab, selected, onAddRequested);
      e.consume();
    });
  }

  private void triggerAddTabChooser(TabPane pane, Tab addTab, Tab oldTab, Runnable onAddRequested) {
    if (pane == null || addTab == null || onAddRequested == null) return;
    Platform.runLater(() -> {
      Tab fallback = (oldTab != null && oldTab != addTab && pane.getTabs().contains(oldTab))
          ? oldTab
          : firstRegularTab(pane, addTab);
      if (fallback != null && pane.getTabs().contains(fallback)) {
        pane.getSelectionModel().select(fallback);
      }
      onAddRequested.run();
    });
  }

  private boolean isAddTabHeaderClick(Object target, Tab addTab) {
    if (!(target instanceof javafx.scene.Node node)) return false;
    String addLabel = addTab == null ? "+" : addTab.getText();
    javafx.scene.Node current = node;
    while (current != null) {
      List<String> styleClasses = current.getStyleClass();
      if (styleClasses.contains("sidebar-add-tab")) {
        return true;
      }
      if (current instanceof Labeled labeled
          && addLabel != null
          && !addLabel.isBlank()
          && addLabel.equals(labeled.getText())) {
        return true;
      }
      if (styleClasses.contains("tab-content-area")) return false;
      current = current.getParent();
    }
    return false;
  }

  private Tab firstRegularTab(TabPane pane, Tab addTab) {
    if (pane == null) return null;
    for (Tab t : pane.getTabs()) {
      if (t != addTab) return t;
    }
    return null;
  }

  private Tab getAddTabForPane(TabPane pane) {
    if (pane == null) return null;
    if (pane == leftTabs) return tabLeftAdd;
    if (pane == rightTabs) return tabRightAdd;
    return null;
  }

  private void attachPanelTabToPane(Tab tab, TabPane targetPane) {
    if (tab == null || targetPane == null) return;
    TabPane current = tab.getTabPane();
    if (current == targetPane) return;
    if (current != null) current.getTabs().remove(tab);
    Tab addTab = getAddTabForPane(targetPane);
    int idx = (addTab != null) ? targetPane.getTabs().indexOf(addTab) : -1;
    if (idx < 0) idx = targetPane.getTabs().size();
    if (!targetPane.getTabs().contains(tab)) targetPane.getTabs().add(idx, tab);
    ensureSidebarVisible(targetPane);
    refreshOpenPanelChooserIndicators();
  }

  private Tab ensureProjectTab(TabPane targetPane) {
    if (targetPane == null || projView == null) return null;
    closePanelWindow(EditorSidebarPanel.PROJECT, true);
    if (projectRoot != null) {
      projView.setRootDirectory(projectRoot);
    }
    if (tabProject == null) {
      tabProject = new Tab("Project", projView);
      tabProject.setClosable(false);
    } else if (tabProject.getContent() != projView) {
      tabProject.setContent(projView);
    }
    return attachSidebarPanelTab(tabProject, EditorSidebarPanel.PROJECT, targetPane);
  }

  private Tab ensureTrashmanTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.TRASHMAN, true);
    TrashmanView trashman = ensureTrashmanView();
    if (targetPane == null || trashman == null) return null;
    trashman.refreshStatus();
    if (tabTrashman == null) {
      tabTrashman = new Tab("Trashman", trashman);
      tabTrashman.setClosable(true);
      tabTrashman.setOnClosed(e -> {
        tabTrashman = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.TRASHMAN);
      });
    } else if (tabTrashman.getContent() != trashman) {
      tabTrashman.setContent(trashman);
    }
    return attachSidebarPanelTab(tabTrashman, EditorSidebarPanel.TRASHMAN, targetPane);
  }

  private Tab ensureTimelineTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.TIMELINE, true);
    StoryTimelineView timeline = ensureTimelineView();
    if (targetPane == null || timeline == null) return null;
    if (tabTimeline == null) {
      tabTimeline = new Tab("Story Map", timeline);
      tabTimeline.setClosable(true);
      tabTimeline.setOnClosed(e -> {
        tabTimeline = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.TIMELINE);
      });
    } else if (tabTimeline.getContent() != timeline) {
      tabTimeline.setContent(timeline);
    }
    return attachSidebarPanelTab(tabTimeline, EditorSidebarPanel.TIMELINE, targetPane);
  }

  private Tab ensureInspectorTab(TabPane targetPane) {
    if (targetPane == null || inspectorScroll == null) return null;
    if (tabInspector == null) {
      tabInspector = new Tab("Inspector", inspectorScroll);
      tabInspector.setClosable(true);
      tabInspector.setOnClosed(e -> tabInspector = null);
    }
    return attachSidebarPanelTab(tabInspector, EditorSidebarPanel.INSPECTOR, targetPane);
  }

  private Tab ensureVnsDiagnosticsTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.VNS_DIAGNOSTICS, true);
    VnsDiagnosticsView diagnostics = ensureVnsDiagnosticsView();
    if (targetPane == null || diagnostics == null) return null;
    if (tabVnsDiagnostics == null) {
      tabVnsDiagnostics = new Tab("Diagnostics", diagnostics);
      tabVnsDiagnostics.setClosable(true);
      tabVnsDiagnostics.setOnClosed(e -> {
        tabVnsDiagnostics = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.VNS_DIAGNOSTICS);
      });
    } else if (tabVnsDiagnostics.getContent() != diagnostics) {
      tabVnsDiagnostics.setContent(diagnostics);
    }
    return attachSidebarPanelTab(tabVnsDiagnostics, EditorSidebarPanel.VNS_DIAGNOSTICS, targetPane);
  }

  private Tab ensureVnsFlowMapTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.LABEL_FLOW, true);
    VnsFlowMapView flowMap = ensureVnsFlowMapView();
    if (targetPane == null || flowMap == null) return null;
    if (tabVnsFlowMap == null) {
      tabVnsFlowMap = new Tab("Label Flow", flowMap);
      tabVnsFlowMap.setClosable(true);
      tabVnsFlowMap.setOnClosed(e -> {
        tabVnsFlowMap = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.LABEL_FLOW);
      });
    } else if (tabVnsFlowMap.getContent() != flowMap) {
      tabVnsFlowMap.setContent(flowMap);
    }
    return attachSidebarPanelTab(tabVnsFlowMap, EditorSidebarPanel.LABEL_FLOW, targetPane);
  }

  private Tab ensureVnsTimelineOutlineTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.TIMELINE_OUTLINE, true);
    VnsTimelineOutlineView outline = ensureVnsTimelineOutlineView();
    if (targetPane == null || outline == null) return null;
    if (tabVnsTimelineOutline == null) {
      tabVnsTimelineOutline = new Tab("Timeline Outline", outline);
      tabVnsTimelineOutline.setClosable(true);
      tabVnsTimelineOutline.setOnClosed(e -> {
        tabVnsTimelineOutline = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.TIMELINE_OUTLINE);
      });
    } else if (tabVnsTimelineOutline.getContent() != outline) {
      tabVnsTimelineOutline.setContent(outline);
    }
    return attachSidebarPanelTab(tabVnsTimelineOutline, EditorSidebarPanel.TIMELINE_OUTLINE, targetPane);
  }

  private Tab ensureAssetBrowserTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.ASSETS, true);
    AssetBrowserView assets = ensureAssetBrowserView();
    if (targetPane == null || assets == null) return null;
    if (tabAssetBrowser == null) {
      tabAssetBrowser = new Tab("Assets", assets);
      tabAssetBrowser.setClosable(true);
      tabAssetBrowser.setOnClosed(e -> {
        tabAssetBrowser = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.ASSETS);
      });
    } else if (tabAssetBrowser.getContent() != assets) {
      tabAssetBrowser.setContent(assets);
    }
    return attachSidebarPanelTab(tabAssetBrowser, EditorSidebarPanel.ASSETS, targetPane);
  }

  private Tab ensureVersionControlTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.VERSION_CONTROL, true);
    VersionControlView vcs = ensureVersionControlView();
    if (targetPane == null || vcs == null) return null;
    if (tabVersionControl == null) {
      tabVersionControl = new Tab("Version Control", vcs);
      tabVersionControl.setClosable(true);
      tabVersionControl.setOnClosed(e -> {
        tabVersionControl = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.VERSION_CONTROL);
      });
      tabVersionControl.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
        if (isSelected) {
          vcs.showGuidePopupIfActive();
        } else {
          vcs.hideGuidePopup();
        }
      });
    } else if (tabVersionControl.getContent() != vcs) {
      tabVersionControl.setContent(vcs);
    }
    return attachSidebarPanelTab(tabVersionControl, EditorSidebarPanel.VERSION_CONTROL, targetPane);
  }

  private Tab ensureLayoutLauncherTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.LAYOUT_LAUNCHER, true);
    LayoutEditorLauncherView launcher = ensureLayoutEditorLauncherView();
    if (targetPane == null || launcher == null) return null;
    if (tabLayoutLauncher == null) {
      tabLayoutLauncher = new Tab("Layout Launcher", launcher);
      tabLayoutLauncher.setClosable(true);
      tabLayoutLauncher.setOnClosed(e -> {
        tabLayoutLauncher = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.LAYOUT_LAUNCHER);
      });
    } else if (tabLayoutLauncher.getContent() != launcher) {
      tabLayoutLauncher.setContent(launcher);
    }
    return attachSidebarPanelTab(tabLayoutLauncher, EditorSidebarPanel.LAYOUT_LAUNCHER, targetPane);
  }

  private MaintenanceOverlay wrapMaintenance(Node content) {
    return MaintenanceOverlay.wrap(content == null ? new StackPane() : content, MAINTENANCE_LATE_MAY_2026);
  }

  private boolean isUnderMaintenancePanel(EditorSidebarPanel panel) {
    return panel == EditorSidebarPanel.IMAGE_ATTRIBUTES;
  }

  private boolean isMaintenanceLaunchBlocked(EditorSidebarPanel panel) {
    return isUnderMaintenancePanel(panel) && !ALLOW_MAINTENANCE_TOOL_LAUNCHES;
  }

  private boolean blockMaintenancePanelLaunch(EditorSidebarPanel panel, String panelName) {
    if (!isMaintenanceLaunchBlocked(panel)) return false;
    String name = panelName == null || panelName.isBlank()
        ? panel != null ? panel.displayName() : "This tool"
        : panelName;
    if (status != null) {
      status.setText(name + " is under maintenance until " + MAINTENANCE_LATE_MAY_2026 + ".");
    }
    return true;
  }

  private String maintenanceText(EditorSidebarPanel panel, String panelName) {
    String name = panelName == null || panelName.isBlank()
        ? panel != null ? panel.displayName() : "This tool"
        : panelName;
    String action = ALLOW_MAINTENANCE_TOOL_LAUNCHES
        ? "Launch override is enabled, but incomplete behavior is expected."
        : "Launch is disabled by default.";
    return name + " is under maintenance until " + MAINTENANCE_LATE_MAY_2026 + ". " + action;
  }

  private void disableMaintenanceMenuItem(MenuItem item, EditorSidebarPanel panel) {
    if (item == null || !isMaintenanceLaunchBlocked(panel)) return;
    item.setDisable(true);
    if (!item.getText().contains("Maintenance")) {
      item.setText(item.getText() + " (Maintenance)");
    }
  }

  private Canvas maintenanceChooserStripeCanvas(StackPane shell) {
    Canvas canvas = new Canvas();
    double period = MAINTENANCE_CHOOSER_STRIPE_WIDTH * 2.0;
    canvas.setMouseTransparent(true);
    canvas.setManaged(false);
    canvas.widthProperty().bind(shell.widthProperty().add(period * 2.0));
    canvas.heightProperty().bind(shell.heightProperty());
    canvas.setTranslateX(-period);

    Rectangle clip = new Rectangle();
    clip.widthProperty().bind(shell.widthProperty());
    clip.heightProperty().bind(shell.heightProperty());
    shell.setClip(clip);

    Runnable redraw = () -> drawMaintenanceChooserStripes(canvas);
    canvas.widthProperty().addListener(o -> redraw.run());
    canvas.heightProperty().addListener(o -> redraw.run());

    Timeline stripeAnimation = new Timeline(
        new KeyFrame(Duration.ZERO,
            new KeyValue(canvas.translateXProperty(), -period, Interpolator.LINEAR)),
        new KeyFrame(Duration.seconds(period / MAINTENANCE_CHOOSER_STRIPE_SPEED),
            new KeyValue(canvas.translateXProperty(), 0.0, Interpolator.LINEAR))
    );
    stripeAnimation.setCycleCount(Timeline.INDEFINITE);
    canvas.getProperties().put("maintenanceStripeAnimation", stripeAnimation);
    canvas.sceneProperty().addListener((obs, oldScene, newScene) -> {
      if (newScene == null) {
        stripeAnimation.stop();
        canvas.setTranslateX(-period);
      } else {
        redraw.run();
        stripeAnimation.playFromStart();
      }
    });
    if (canvas.getScene() != null) {
      redraw.run();
      stripeAnimation.playFromStart();
    }
    return canvas;
  }

  private void drawMaintenanceChooserStripes(Canvas canvas) {
    if (canvas == null) return;
    double w = canvas.getWidth();
    double h = canvas.getHeight();
    if (w <= 0 || h <= 0) return;

    GraphicsContext gc = canvas.getGraphicsContext2D();
    gc.clearRect(0, 0, w, h);
    gc.setFill(MAINTENANCE_CHOOSER_TINT_COLOR);
    gc.fillRect(0, 0, w, h);

    double period = MAINTENANCE_CHOOSER_STRIPE_WIDTH * 2.0;
    gc.setFill(MAINTENANCE_CHOOSER_STRIPE_COLOR);
    for (double x = -h - period; x < w + period; x += period) {
      gc.fillPolygon(
          new double[]{x, x + MAINTENANCE_CHOOSER_STRIPE_WIDTH,
              x + MAINTENANCE_CHOOSER_STRIPE_WIDTH + h, x + h},
          new double[]{0, 0, h, h},
          4);
    }
  }

  private boolean isMaintenanceWrapped(Tab tab) {
    return tab != null && tab.getContent() instanceof MaintenanceOverlay;
  }

  private Tab ensureStoryboardOverlayTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.STORYBOARD_OVERLAY, true);
    StoryboardOverlayView storyboardOverlay = ensureStoryboardOverlayView();
    if (targetPane == null || storyboardOverlay == null) return null;
    if (tabStoryboardOverlay == null) {
      tabStoryboardOverlay = new Tab("Storyboard Overlay", storyboardOverlay);
      tabStoryboardOverlay.setClosable(true);
      tabStoryboardOverlay.setOnClosed(e -> {
        tabStoryboardOverlay = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.STORYBOARD_OVERLAY);
      });
    } else if (tabStoryboardOverlay.getContent() != storyboardOverlay) {
      tabStoryboardOverlay.setContent(storyboardOverlay);
    }
    return attachSidebarPanelTab(tabStoryboardOverlay, EditorSidebarPanel.STORYBOARD_OVERLAY, targetPane);
  }

  private Tab ensureLayeredImageVisualizerTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.LAYERED_IMAGES, true);
    LayeredImageVisualizerView layered = ensureLayeredImageVisualizerView();
    if (targetPane == null || layered == null) return null;
    if (tabLayeredImageVisualizer == null) {
      tabLayeredImageVisualizer = new Tab("Layered Images", layered);
      tabLayeredImageVisualizer.setClosable(true);
      tabLayeredImageVisualizer.setOnClosed(e -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == layered) {
          restoreLayeredImageVisualizerLayout(false);
        }
        tabLayeredImageVisualizer = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.LAYERED_IMAGES);
      });
    } else if (tabLayeredImageVisualizer.getContent() != layered) {
      tabLayeredImageVisualizer.setContent(layered);
    }
    return attachSidebarPanelTab(tabLayeredImageVisualizer, EditorSidebarPanel.LAYERED_IMAGES, targetPane);
  }

  private Tab ensureImageAttributesToolTab(TabPane targetPane) {
    if (blockMaintenancePanelLaunch(EditorSidebarPanel.IMAGE_ATTRIBUTES, "Image Attributes Tool")) return null;
    closePanelWindow(EditorSidebarPanel.IMAGE_ATTRIBUTES, true);
    ImageAttributesToolView attributes = ensureImageAttributesToolView();
    if (targetPane == null || attributes == null) return null;
    if (tabImageAttributesTool == null) {
      tabImageAttributesTool = new Tab("Image Attributes", wrapMaintenance(attributes));
      tabImageAttributesTool.setClosable(true);
      tabImageAttributesTool.setOnClosed(e -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == attributes) {
          restoreLayeredImageVisualizerLayout(false);
        }
        tabImageAttributesTool = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.IMAGE_ATTRIBUTES);
      });
    } else if (!isMaintenanceWrapped(tabImageAttributesTool)) {
      tabImageAttributesTool.setContent(wrapMaintenance(attributes));
    }
    return attachSidebarPanelTab(tabImageAttributesTool, EditorSidebarPanel.IMAGE_ATTRIBUTES, targetPane);
  }

  private Tab ensureImageTintToolTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.IMAGE_TINT, true);
    ImageTintToolView tint = ensureImageTintToolView();
    if (targetPane == null || tint == null) return null;
    if (tabImageTintTool == null) {
      tabImageTintTool = new Tab("Image Tint", tint);
      tabImageTintTool.setClosable(true);
      tabImageTintTool.setOnClosed(e -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == tint) {
          restoreLayeredImageVisualizerLayout(false);
        }
        tabImageTintTool = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.IMAGE_TINT);
      });
    } else if (tabImageTintTool.getContent() != tint) {
      tabImageTintTool.setContent(tint);
    }
    return attachSidebarPanelTab(tabImageTintTool, EditorSidebarPanel.IMAGE_TINT, targetPane);
  }

  private Tab ensureScriptEditorLauncherTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.SCRIPT_EDITOR, true);
    ScriptEditorLauncherView launcher = ensureScriptEditorLauncherView();
    if (targetPane == null || launcher == null) return null;
    if (tabScriptEditorLauncher == null) {
      tabScriptEditorLauncher = new Tab("Text Editor", launcher);
      tabScriptEditorLauncher.setClosable(true);
      tabScriptEditorLauncher.setOnClosed(e -> {
        tabScriptEditorLauncher = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.SCRIPT_EDITOR);
      });
    } else if (tabScriptEditorLauncher.getContent() != launcher) {
      tabScriptEditorLauncher.setContent(launcher);
    }
    return attachSidebarPanelTab(tabScriptEditorLauncher, EditorSidebarPanel.SCRIPT_EDITOR, targetPane);
  }

  private String panelActionLabel(String panelName, Tab tab, TabPane targetPane) {
    if (tab != null && tab.getTabPane() == targetPane) return "Open " + panelName;
    if (tab != null && tab.getTabPane() != null) return "Move " + panelName + " Here";
    return "Add " + panelName;
  }

  private void addChooserActionButton(javafx.scene.layout.VBox actions, String text, String iconClass, Runnable action) {
    if (actions == null || text == null || action == null) return;
    Button button = new Button(text);
    button.setMaxWidth(Double.MAX_VALUE);
    button.setAlignment(Pos.CENTER_LEFT);
    if (iconClass != null && !iconClass.isBlank()) {
      button.setGraphic(icon("icon", iconClass));
      button.setContentDisplay(ContentDisplay.LEFT);
      button.setGraphicTextGap(8);
    }
    button.setOnAction(e -> action.run());
    actions.getChildren().add(button);
  }

  private void addChooserActionRow(TabPane chooserPane,
      javafx.scene.layout.VBox actions, EditorSidebarPanel panel,
      EditorPanelPlacement targetPlacement, String panelName, String iconClass,
      Runnable embedAction, Runnable windowAction, Runnable removeAction) {
    if (actions == null || panelName == null) return;
    if (panel != null && !editorPreferences.isVisibleInChooser(panel)) return;
    boolean underMaintenance = isUnderMaintenancePanel(panel);
    boolean launchBlocked = isMaintenanceLaunchBlocked(panel);
    Tooltip maintenanceTooltip = new Tooltip(maintenanceText(panel, panelName));

    String resolvedIconClass = panel != null ? panel.iconStyleClass() : iconClass;
    Node panelIcon = panel != null
        ? sidebarPanelIcon(panel, "panel-chooser-tool-icon")
        : "icon-panel-settings".equals(resolvedIconClass)
            ? editorSettingsSidebarIcon("panel-chooser-tool-icon")
            : icon("icon", "panel-chooser-tool-icon", resolvedIconClass);
    StackPane iconChip = new StackPane(panelIcon);
    iconChip.getStyleClass().add("panel-chooser-icon-chip");
    if (resolvedIconClass != null && !resolvedIconClass.isBlank()) {
      iconChip.getStyleClass().add(resolvedIconClass + "-chip");
    }
    Tooltip versionTooltip = panel == null ? null : new Tooltip(panel.versionTooltip());
    if (panel != null) {
      Tooltip.install(iconChip, versionTooltip);
    }

    Label label = new Label(panelName);
    label.setMaxWidth(Double.MAX_VALUE);
    label.getStyleClass().add("panel-chooser-title");
    label.setWrapText(false);
    HBox titleGroup = new HBox(6, label);
    titleGroup.setAlignment(Pos.CENTER_LEFT);
    titleGroup.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(titleGroup, Priority.ALWAYS);
    if (panel != null) {
      Label versionText = new Label(panel.versionBadge());
      versionText.getStyleClass().addAll("panel-chooser-version-text", panel.versionStyleClass());
      versionText.setTooltip(versionTooltip);
      titleGroup.getChildren().add(versionText);
    }

    Label placementBadge = new Label();
    placementBadge.getStyleClass().add("panel-chooser-placement-badge");
    placementBadge.setMinWidth(10);
    placementBadge.setMaxWidth(10);
    placementBadge.setAlignment(Pos.CENTER);
    Tooltip placementTooltip = new Tooltip();
    placementBadge.setTooltip(placementTooltip);
    Region memoryIcon = SidebarToolIcon.of(SidebarToolIcon.Kind.MEMORY, "#9a9a9a", 18);
    memoryIcon.getStyleClass().add("panel-chooser-memory-icon");
    Label memoryIndicator = new Label("●");
    memoryIndicator.getStyleClass().add("panel-chooser-memory-indicator");
    Tooltip memoryTooltip = new Tooltip();
    memoryIndicator.setTooltip(memoryTooltip);
    Tooltip.install(memoryIcon, memoryTooltip);
    HBox memoryGroup = new HBox(3, memoryIcon, memoryIndicator);
    memoryGroup.getStyleClass().add("panel-chooser-memory-group");
    memoryGroup.setAlignment(Pos.CENTER_LEFT);

    Button dockBtn = new Button();
    dockBtn.setGraphic(PanelChooserActionIcon.of(PanelChooserActionIcon.Kind.ADD_HERE));
    dockBtn.setTooltip(new Tooltip("Add to sidebar"));
    dockBtn.setMinSize(26, 26); dockBtn.setPrefSize(26, 26); dockBtn.setMaxSize(26, 26);
    dockBtn.setFocusTraversable(false);
    dockBtn.getStyleClass().add("panel-chooser-icon-btn");
    if (embedAction != null && !launchBlocked) {
      dockBtn.setOnAction(e -> embedAction.run());
    } else {
      dockBtn.setDisable(true);
      dockBtn.setTooltip(launchBlocked ? maintenanceTooltip : new Tooltip("Pop-out only"));
    }

    Button popOutBtn = new Button();
    popOutBtn.setGraphic(PanelChooserActionIcon.of(PanelChooserActionIcon.Kind.POP_OUT));
    popOutBtn.setTooltip(new Tooltip("Open in separate window"));
    popOutBtn.setMinSize(26, 26); popOutBtn.setPrefSize(26, 26); popOutBtn.setMaxSize(26, 26);
    popOutBtn.setFocusTraversable(false);
    popOutBtn.getStyleClass().add("panel-chooser-icon-btn");
    if (windowAction != null && !launchBlocked) {
      popOutBtn.setOnAction(e -> windowAction.run());
    } else {
      popOutBtn.setDisable(true);
      if (launchBlocked) popOutBtn.setTooltip(maintenanceTooltip);
    }

    Runnable refreshState = () -> {
      updateChooserMemoryIndicator(memoryIndicator, panel, memoryTooltip);
      if (panel == null) {
        dockBtn.setGraphic(PanelChooserActionIcon.of(PanelChooserActionIcon.Kind.ADD_HERE));
        dockBtn.setTooltip(new Tooltip("Open in this panel"));
        placementBadge.setManaged(false);
        placementBadge.setVisible(false);
        memoryGroup.setManaged(true);
        memoryGroup.setVisible(true);
        return;
      }

      EditorPanelPlacement placement = editorPreferences.getPlacement(panel);
      boolean attached = isPanelAttached(panel);
      placementBadge.setManaged(true);
      placementBadge.setVisible(true);
      updateChooserPlacementBadge(placementBadge, placement, attached, placementTooltip);
      if (launchBlocked) {
        dockBtn.setGraphic(SidebarToolIcon.warning("#f5a34a"));
        dockBtn.setTooltip(maintenanceTooltip);
        dockBtn.setDisable(true);
        popOutBtn.setTooltip(maintenanceTooltip);
        popOutBtn.setDisable(true);
        return;
      }
      if (!panel.supportsDocking()) {
        dockBtn.setGraphic(PanelChooserActionIcon.of(PanelChooserActionIcon.Kind.POP_OUT));
        dockBtn.setTooltip(new Tooltip("Pop-out only"));
        dockBtn.setDisable(true);
        return;
      }
      if (placement == EditorPanelPlacement.HIDDEN || !attached) {
        dockBtn.setGraphic(PanelChooserActionIcon.of(PanelChooserActionIcon.Kind.ADD_HERE));
        dockBtn.setTooltip(new Tooltip(
            "Add to " + (targetPlacement == EditorPanelPlacement.RIGHT ? "right" : "left") + " sidebar"));
      } else if (placement != targetPlacement) {
        dockBtn.setGraphic(SidebarToolIcon.dock("#d6dbe5"));
        dockBtn.setTooltip(new Tooltip(
            "Move to " + (targetPlacement == EditorPanelPlacement.RIGHT ? "right" : "left") + " sidebar"));
      } else {
        dockBtn.setGraphic(SidebarToolIcon.dock("#d6dbe5"));
        dockBtn.setTooltip(new Tooltip("Select panel"));
      }
    };

    final Runnable dockAction;
    if (embedAction != null && !launchBlocked) {
      dockAction = () -> {
        embedAction.run();
        dismissPanelChooser(chooserPane);
        refreshState.run();
      };
      dockBtn.setOnAction(e -> dockAction.run());
    } else {
      dockAction = null;
    }
    if (windowAction != null && !launchBlocked) {
      popOutBtn.setOnAction(e -> {
        windowAction.run();
        dismissPanelChooser(chooserPane);
        refreshState.run();
      });
    }

    HBox row = new HBox(8, iconChip, titleGroup, memoryGroup, placementBadge, dockBtn, popOutBtn);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setPadding(new javafx.geometry.Insets(4, 6, 4, 6));
    row.setMaxWidth(Double.MAX_VALUE);
    row.getStyleClass().add("panel-chooser-row");
    if (underMaintenance) {
      row.getStyleClass().add("panel-chooser-row-maintenance");
    }
    String filterKey = panelName.toLowerCase(Locale.ROOT)
        + (panel != null
            ? " " + panel.key().toLowerCase(Locale.ROOT)
                + " " + panel.version().toLowerCase(Locale.ROOT)
                + " " + panel.maturity().name().toLowerCase(Locale.ROOT)
            : "");
    EventHandler<MouseEvent> openOnDoubleClick = e -> {
      if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() < 2) return;
      if (isInsideChooserIconButton(e.getTarget())) return;
      if (launchBlocked) {
        blockMaintenancePanelLaunch(panel, panelName);
        e.consume();
        return;
      }
      if (dockAction != null) {
        dockAction.run();
      } else if (windowAction != null) {
        windowAction.run();
        dismissPanelChooser(chooserPane);
        refreshState.run();
      } else {
        return;
      }
      e.consume();
    };
    refreshState.run();

    Node chooserNode = row;
    if (underMaintenance) {
      StackPane shell = new StackPane(row);
      shell.setMaxWidth(Double.MAX_VALUE);
      shell.getStyleClass().add("panel-chooser-maintenance-shell");

      Canvas stripes = maintenanceChooserStripeCanvas(shell);
      shell.getChildren().add(stripes);
      Tooltip.install(shell, maintenanceTooltip);
      chooserNode = shell;
    }
    chooserNode.getProperties().put("chooserFilterKey", filterKey);
    chooserNode.getProperties().put(PANEL_CHOOSER_REFRESH_KEY, refreshState);
    chooserNode.setOnMouseClicked(openOnDoubleClick);
    actions.getChildren().add(chooserNode);
  }

  private void dismissPanelChooser(TabPane pane) {
    if (pane == null) return;
    Tab chooser = null;
    Tab selected = pane.getSelectionModel().getSelectedItem();
    Tab selectedPanel = isRegularSidebarTab(pane, selected) ? selected : null;
    if (selected != null && PANEL_CHOOSER_TAB_ROLE.equals(selected.getProperties().get(PANEL_CHOOSER_TAB_ROLE))) {
      chooser = selected;
    }
    if (chooser == null) {
      chooser = findPanelChooserTab(pane, pane == leftTabs);
    }
    if (chooser == null) return;
    Tab fallback = firstRegularTab(pane, getAddTabForPane(pane));
    Tab addTab = getAddTabForPane(pane);
    pane.getTabs().remove(chooser);
    if (selectedPanel != null && pane.getTabs().contains(selectedPanel)) {
      pane.getSelectionModel().select(selectedPanel);
    } else if (fallback != null && pane.getTabs().contains(fallback)) {
      pane.getSelectionModel().select(fallback);
    } else if (addTab != null && pane.getTabs().contains(addTab)) {
      pane.getSelectionModel().select(addTab);
    }
    scheduleEmptySidebarAutoClose(pane);
  }

  private boolean isRegularSidebarTab(TabPane pane, Tab tab) {
    if (pane == null || tab == null) return false;
    if (tab == getAddTabForPane(pane)) return false;
    return !PANEL_CHOOSER_TAB_ROLE.equals(tab.getProperties().get(PANEL_CHOOSER_TAB_ROLE));
  }

  private StackPane createSidebarEmptyState(String side) {
    Region plusIcon = NewTabIcon.of(32);

    Label title = new Label("No sidebar tools added");
    title.getStyleClass().add("sidebar-empty-title");

    String placement = "right".equalsIgnoreCase(side) ? "right" : "left";
    Label message = new Label(
        "Use the new-tab button above to add a " + placement + " sidebar tool like Help, Puppeteer Launcher, or Scene Lighting Studio.");
    message.getStyleClass().add("sidebar-empty-copy");
    message.setWrapText(true);

    VBox content = new VBox(12, plusIcon, title, message);
    content.getStyleClass().add("sidebar-empty-state");
    content.setAlignment(Pos.CENTER);
    content.setFillWidth(true);
    content.setMaxWidth(260);
    content.setMaxHeight(Region.USE_PREF_SIZE);
    content.setPadding(new Insets(24));

    StackPane shell = new StackPane(content);
    shell.getStyleClass().add("sidebar-empty-shell");
    return shell;
  }

  private boolean isInsideChooserIconButton(Object target) {
    if (!(target instanceof javafx.scene.Node node)) return false;
    javafx.scene.Node current = node;
    while (current != null) {
      if (current.getStyleClass().contains("panel-chooser-icon-btn")) return true;
      current = current.getParent();
    }
    return false;
  }

  private void updateChooserPlacementBadge(
      Label badge, EditorPanelPlacement placement, boolean attached, Tooltip tooltip) {
    if (badge == null) return;
    badge.getStyleClass().removeAll(
        "panel-chooser-placement-hidden",
        "panel-chooser-placement-left",
        "panel-chooser-placement-right");
    badge.setText("");
    if (!attached) {
      badge.getStyleClass().add("panel-chooser-placement-hidden");
      if (tooltip != null) tooltip.setText("Detached from sidebars");
      return;
    }
    if (placement == EditorPanelPlacement.LEFT) {
      badge.getStyleClass().add("panel-chooser-placement-left");
      if (tooltip != null) tooltip.setText("Attached to left sidebar");
      return;
    }
    if (placement == EditorPanelPlacement.RIGHT) {
      badge.getStyleClass().add("panel-chooser-placement-right");
      if (tooltip != null) tooltip.setText("Attached to right sidebar");
      return;
    }
    badge.getStyleClass().add("panel-chooser-placement-hidden");
    if (tooltip != null) tooltip.setText("Hidden by default");
  }

  private void updateChooserMemoryIndicator(
      Label indicator, EditorSidebarPanel panel, Tooltip tooltip) {
    if (indicator == null) return;
    boolean loaded = isPanelLoadedInMemory(panel);
    indicator.getStyleClass().removeAll(
        "panel-chooser-memory-loaded",
        "panel-chooser-memory-unloaded");
    indicator.getStyleClass().add(
        loaded ? "panel-chooser-memory-loaded" : "panel-chooser-memory-unloaded");
    if (tooltip != null) {
      tooltip.setText(loaded ? "Loaded in memory" : "Not loaded in memory");
    }
  }

  private void installPanelChooserFilter(TextField filterField, VBox actions) {
    if (filterField == null || actions == null) return;
    Runnable apply = () -> {
      String needle = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
      for (javafx.scene.Node child : actions.getChildren()) {
        Object key = child.getProperties().get("chooserFilterKey");
        String searchable = key == null ? "" : key.toString();
        boolean visible = needle.isBlank() || searchable.contains(needle);
        child.setManaged(visible);
        child.setVisible(visible);
      }
    };
    filterField.textProperty().addListener((obs, oldText, newText) -> apply.run());
    filterField.setOnAction(e -> {
      for (javafx.scene.Node child : actions.getChildren()) {
        if (!child.isVisible()) continue;
        child.fireEvent(new MouseEvent(
            MouseEvent.MOUSE_CLICKED,
            0, 0, 0, 0,
            MouseButton.PRIMARY,
            2,
            false, false, false, false,
            true, false, false, true, false, false,
            null));
        break;
      }
    });
    apply.run();
  }

  private Tab findPanelChooserTab(TabPane pane, boolean leftSide) {
    if (pane == null) return null;
    String side = leftSide ? "left" : "right";
    for (Tab tab : pane.getTabs()) {
      Object role = tab.getProperties().get(PANEL_CHOOSER_TAB_ROLE);
      Object tag = tab.getProperties().get("chooserSide");
      if (PANEL_CHOOSER_TAB_ROLE.equals(role) && side.equals(tag)) {
        return tab;
      }
    }
    return null;
  }

  private void refreshOpenPanelChooserIndicators() {
    refreshOpenPanelChooserIndicators(leftTabs);
    refreshOpenPanelChooserIndicators(rightTabs);
  }

  private void refreshOpenPanelChooserIndicators(TabPane pane) {
    if (pane == null) return;
    for (Tab tab : pane.getTabs()) {
      if (!PANEL_CHOOSER_TAB_ROLE.equals(tab.getProperties().get(PANEL_CHOOSER_TAB_ROLE))) {
        continue;
      }
      refreshChooserNode(tab.getContent());
    }
  }

  private void refreshChooserNode(javafx.scene.Node node) {
    if (node == null) return;
    Object refresher = node.getProperties().get(PANEL_CHOOSER_REFRESH_KEY);
    if (refresher instanceof Runnable runnable) {
      runnable.run();
    }
    if (node instanceof javafx.scene.Parent parent) {
      for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
        refreshChooserNode(child);
      }
    }
  }

  private void launchPanelAsWindow(
      String title,
      javafx.scene.Parent content,
      double width,
      double height,
      EditorSidebarPanel panel) {
    if (content == null) return;
    if (blockMaintenancePanelLaunch(panel, title)) return;
    if (panel != null) {
      Stage existing = panelWindows.get(panel);
      if (existing != null) {
        if (existing.isShowing()) {
          existing.toFront();
          existing.requestFocus();
          return;
        }
        panelWindows.remove(panel);
      }
    } else if (content == editorSettingsView && editorSettingsWindow != null) {
      if (editorSettingsWindow.isShowing()) {
        editorSettingsWindow.toFront();
        editorSettingsWindow.requestFocus();
        return;
      }
      editorSettingsWindow = null;
    }
    // Detach from any current parent (Tab or other container)
    if (content.getParent() != null) {
      javafx.scene.Parent parent = content.getParent();
      if (parent instanceof javafx.scene.layout.Pane pane) {
        pane.getChildren().remove(content);
      }
    }
    // Remove from any sidebar tab
    detachFromSidebarTab(content);
    applySidebarToolStyle(content, panel);

    Stage windowStage = new Stage();
    windowStage.setTitle(title != null ? title : "Utility");
    javafx.scene.layout.BorderPane wrapper = new javafx.scene.layout.BorderPane(content);
    Scene windowScene = new Scene(wrapper, width, height);
    EditorTheme.apply(windowScene);
    windowStage.setScene(windowScene);
    if (panel != null) {
      panelWindows.put(panel, windowStage);
    } else if (content == editorSettingsView) {
      editorSettingsWindow = windowStage;
    }
    windowStage.setOnHidden(e -> {
      boolean suppressUnload = Boolean.TRUE.equals(
          windowStage.getProperties().remove(PANEL_WINDOW_SUPPRESS_UNLOAD_KEY));
      if (panel != null) {
        panelWindows.remove(panel, windowStage);
        if (!suppressUnload) {
          releaseSidebarPanelIfUnused(panel);
        }
      } else if (content == editorSettingsView) {
        if (editorSettingsWindow == windowStage) {
          editorSettingsWindow = null;
        }
        if (!suppressUnload) {
          releaseEditorSettingsIfUnused();
        }
      }
      refreshOpenPanelChooserIndicators();
    });
    applyLinuxDefaultWindowState(windowStage);
    windowStage.show();
    refreshOpenPanelChooserIndicators();
  }

  private void detachFromSidebarTab(javafx.scene.Parent content) {
    Tab[] allTabs = {
        tabProject, tabTimeline, tabInspector, tabVnsDiagnostics,
        tabVnsFlowMap, tabVnsTimelineOutline, tabAssetBrowser, tabVersionControl, tabLayoutLauncher,
        tabLayeredImageVisualizer, tabImageAttributesTool, tabImageTintTool,
        tabPuppeteerLauncher, tabScriptEditorLauncher,
        tabEditorSettings
    };
    for (Tab t : allTabs) {
      if (t != null && t.getContent() == content) {
        TabPane tp = t.getTabPane();
        if (tp != null) tp.getTabs().remove(t);
        t.setContent(null);
        nullifyTab(t);
        refreshOpenPanelChooserIndicators();
        break;
      }
      // Also check if content is wrapped in a ScrollPane
      if (t != null && t.getContent() instanceof ScrollPane sp && sp.getContent() == content) {
        TabPane tp = t.getTabPane();
        if (tp != null) tp.getTabs().remove(t);
        sp.setContent(null);
        t.setContent(null);
        nullifyTab(t);
        refreshOpenPanelChooserIndicators();
        break;
      }
    }
  }

  private void nullifyTab(Tab tab) {
    if (tab == tabProject) tabProject = null;
    else if (tab == tabTrashman) tabTrashman = null;
    else if (tab == tabTimeline) tabTimeline = null;
    else if (tab == tabInspector) tabInspector = null;
    else if (tab == tabVnsDiagnostics) tabVnsDiagnostics = null;
    else if (tab == tabVnsFlowMap) tabVnsFlowMap = null;
    else if (tab == tabVnsTimelineOutline) tabVnsTimelineOutline = null;
    else if (tab == tabAssetBrowser) tabAssetBrowser = null;
    else if (tab == tabVersionControl) tabVersionControl = null;
    else if (tab == tabLayoutLauncher) tabLayoutLauncher = null;
    else if (tab == tabLayeredImageVisualizer) tabLayeredImageVisualizer = null;
    else if (tab == tabImageAttributesTool) tabImageAttributesTool = null;
    else if (tab == tabImageTintTool) tabImageTintTool = null;
    else if (tab == tabPuppeteerLauncher) tabPuppeteerLauncher = null;
    else if (tab == tabScriptEditorLauncher) tabScriptEditorLauncher = null;
    else if (tab == tabEditorSettings) tabEditorSettings = null;
  }

  private void clearTabContent(Tab tab) {
    if (tab == null) return;
    if (tab.getContent() instanceof ScrollPane sp) {
      sp.setContent(null);
    }
    tab.setContent(null);
  }

  private void releaseSidebarPanelTab(EditorSidebarPanel panel) {
    if (panel == null) return;
    Tab tab = configuredPanelTab(panel);
    if (tab == null) return;
    TabPane pane = tab.getTabPane();
    if (pane != null) {
      pane.getTabs().remove(tab);
    }
    clearTabContent(tab);
    nullifyTab(tab);
  }

  private void closePanelWindow(EditorSidebarPanel panel, boolean suppressUnload) {
    if (panel == null) return;
    Stage stage = panelWindows.get(panel);
    if (stage == null) return;
    if (suppressUnload) {
      stage.getProperties().put(PANEL_WINDOW_SUPPRESS_UNLOAD_KEY, Boolean.TRUE);
    }
    if (stage.isShowing()) {
      stage.close();
    } else {
      panelWindows.remove(panel);
      refreshOpenPanelChooserIndicators();
    }
  }

  private void closeAllFloatingPanelWindows() {
    for (EditorSidebarPanel panel : new ArrayList<>(panelWindows.keySet())) {
      closePanelWindow(panel, true);
    }
    closeEditorSettingsWindow(true);
  }

  private void closeEditorSettingsWindow(boolean suppressUnload) {
    if (editorSettingsWindow == null) return;
    if (suppressUnload) {
      editorSettingsWindow.getProperties().put(PANEL_WINDOW_SUPPRESS_UNLOAD_KEY, Boolean.TRUE);
    }
    if (editorSettingsWindow.isShowing()) {
      editorSettingsWindow.close();
    } else {
      editorSettingsWindow = null;
      refreshOpenPanelChooserIndicators();
    }
  }

  private boolean isPanelLoadedInMemory(EditorSidebarPanel panel) {
    if (panel == null) return editorSettingsView != null;
    return switch (panel) {
      case PROJECT -> projView != null;
      case TRASHMAN -> trashmanView != null;
      case TIMELINE -> timelineView != null;
      case INSPECTOR -> inspectorView != null;
      case VNS_DIAGNOSTICS -> vnsDiagnosticsView != null;
      case LABEL_FLOW -> vnsFlowMapView != null;
      case TIMELINE_OUTLINE -> vnsTimelineOutlineView != null;
      case ASSETS -> assetBrowserView != null;
      case LAYOUT_LAUNCHER -> layoutEditorLauncherView != null;
      case STORYBOARD_OVERLAY -> storyboardOverlayView != null;
      case LAYERED_IMAGES -> layeredImageVisualizerView != null;
      case IMAGE_ATTRIBUTES -> imageAttributesToolView != null;
      case IMAGE_TINT -> imageTintToolView != null;
      case VERSION_CONTROL -> versionControlView != null;
      case PUPPETEER_LAUNCHER -> puppeteerLauncherPanel != null;
      case SCRIPT_EDITOR -> scriptEditorLauncherView != null;
    };
  }

  private void releaseSidebarPanelIfUnused(EditorSidebarPanel panel) {
    if (panel == null || isPanelAttached(panel)) return;
    Stage window = panelWindows.get(panel);
    if (window != null && window.isShowing()) return;
    releaseSidebarPanel(panel);
    refreshOpenPanelChooserIndicators();
  }

  private void releaseSidebarPanel(EditorSidebarPanel panel) {
    if (panel == null) return;
    releaseSidebarPanelTab(panel);
    panelWindows.remove(panel);
    switch (panel) {
      case PROJECT -> {
      }
      case TRASHMAN -> {
        if (trashmanView != null) {
          trashmanView.dispose();
        }
        trashmanView = null;
      }
      case TIMELINE -> {
        if (timelineView != null) {
          timelineView.setProjectRoot(null);
        }
        timelineView = null;
      }
      case INSPECTOR -> {
      }
      case VNS_DIAGNOSTICS -> vnsDiagnosticsView = null;
      case LABEL_FLOW -> vnsFlowMapView = null;
      case TIMELINE_OUTLINE -> vnsTimelineOutlineView = null;
      case ASSETS -> {
        if (assetBrowserView != null) {
          assetBrowserView.setProjectRoot(null);
        }
        assetBrowserView = null;
      }
      case LAYOUT_LAUNCHER -> {
        if (layoutEditorLauncherView != null) {
          layoutEditorLauncherView.setProjectRoot(null);
        }
        layoutEditorLauncherView = null;
      }
      case STORYBOARD_OVERLAY -> {
        if (storyboardOverlayView != null) {
          storyboardOverlayView.dispose();
        }
        storyboardOverlayView = null;
      }
      case LAYERED_IMAGES -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == layeredImageVisualizerView) {
          restoreLayeredImageVisualizerLayout(false);
        }
        if (layeredImageVisualizerView != null) {
          layeredImageVisualizerView.dispose();
        }
        layeredImageVisualizerView = null;
      }
      case IMAGE_ATTRIBUTES -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == imageAttributesToolView) {
          restoreLayeredImageVisualizerLayout(false);
        }
        if (imageAttributesToolView != null) {
          imageAttributesToolView.dispose();
        }
        imageAttributesToolView = null;
      }
      case IMAGE_TINT -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == imageTintToolView) {
          restoreLayeredImageVisualizerLayout(false);
        }
        if (imageTintToolView != null) {
          imageTintToolView.dispose();
        }
        imageTintToolView = null;
      }
      case VERSION_CONTROL -> {
        if (versionControlView != null) {
          versionControlView.dispose();
        }
        versionControlView = null;
      }
      case PUPPETEER_LAUNCHER -> {
        if (puppeteerLauncherPanel != null) {
          puppeteerLauncherPanel.setProjectRoot(null);
        }
        puppeteerLauncherPanel = null;
      }
      case SCRIPT_EDITOR -> {
        if (scriptEditorLauncherView != null) {
          scriptEditorLauncherView.setProjectRoot(null);
          scriptEditorLauncherView.setWorkspaceRoot(null);
        }
        scriptEditorLauncherView = null;
      }
    }
    refreshOpenPanelChooserIndicators();
  }

  private void releaseEditorSettingsIfUnused() {
    if (tabEditorSettings != null && tabEditorSettings.getTabPane() != null) return;
    if (editorSettingsWindow != null && editorSettingsWindow.isShowing()) return;
    clearTabContent(tabEditorSettings);
    tabEditorSettings = null;
    editorSettingsView = null;
    refreshOpenPanelChooserIndicators();
  }

  private void openPanelChooserTab(TabPane pane, boolean leftSide) {
    if (pane == null) return;
    Tab addTab = leftSide ? tabLeftAdd : tabRightAdd;
    if (addTab == null) return;
    Tab existing = findPanelChooserTab(pane, leftSide);
    if (existing != null) {
      pane.getTabs().remove(existing);
    }
    EditorPanelPlacement targetPlacement =
        leftSide ? EditorPanelPlacement.LEFT : EditorPanelPlacement.RIGHT;

    String title = leftSide ? "Add Left Panel" : "Add Right Panel";
    String details = leftSide
      ? "Choose a panel to add on the left. This keeps the workspace focused by default."
      : "Choose a panel to add on the right. Add only the tools you need for the current workflow.";

    javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(10);
    root.setPadding(new javafx.geometry.Insets(12));
    root.setFillWidth(true);
    root.getStyleClass().addAll("panel-chooser-root", "sidebar-tool-root");
    Label heading = new Label(title);
    heading.getStyleClass().add("panel-chooser-heading");
    Label info = new Label(details);
    info.setWrapText(true);
    info.getStyleClass().add("panel-chooser-copy");
    TextField filter = new TextField();
    filter.setPromptText("Filter panels...");
    filter.getStyleClass().add("panel-chooser-filter");
    javafx.scene.layout.VBox actions = new javafx.scene.layout.VBox(4);
    addChooserActionRow(pane, actions, EditorSidebarPanel.PROJECT, targetPlacement, "Project", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.PROJECT, targetPlacement);
      Tab t = ensureProjectTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Project", projView, 600, 700, EditorSidebarPanel.PROJECT), () -> {
      rememberPanelPlacement(EditorSidebarPanel.PROJECT, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.TRASHMAN, targetPlacement, "Trashman", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.TRASHMAN, targetPlacement);
      Tab t = ensureTrashmanTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (trashmanView != null) trashmanView.refreshStatus();
    }, () -> {
      TrashmanView view = ensureTrashmanView();
      if (view != null) view.refreshStatus();
      launchPanelAsWindow("Trashman", view, 760, 760, EditorSidebarPanel.TRASHMAN);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.TRASHMAN, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.TIMELINE, targetPlacement, "Story Map", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.TIMELINE, targetPlacement);
      Tab t = ensureTimelineTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Story Map", ensureTimelineView(), 600, 700, EditorSidebarPanel.TIMELINE), () -> {
      rememberPanelPlacement(EditorSidebarPanel.TIMELINE, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.INSPECTOR, targetPlacement, "Inspector", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.INSPECTOR, targetPlacement);
      Tab t = ensureInspectorTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Inspector", inspectorScroll, 420, 700, EditorSidebarPanel.INSPECTOR), () -> {
      rememberPanelPlacement(EditorSidebarPanel.INSPECTOR, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.VNS_DIAGNOSTICS, targetPlacement, "Diagnostics", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.VNS_DIAGNOSTICS, targetPlacement);
      Tab t = ensureVnsDiagnosticsTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Diagnostics", ensureVnsDiagnosticsView(), 700, 600, EditorSidebarPanel.VNS_DIAGNOSTICS), () -> {
      rememberPanelPlacement(EditorSidebarPanel.VNS_DIAGNOSTICS, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.LABEL_FLOW, targetPlacement, "Label Flow", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.LABEL_FLOW, targetPlacement);
      Tab t = ensureVnsFlowMapTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Label Flow Map", ensureVnsFlowMapView(), 700, 600, EditorSidebarPanel.LABEL_FLOW), () -> {
      rememberPanelPlacement(EditorSidebarPanel.LABEL_FLOW, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.TIMELINE_OUTLINE, targetPlacement, "Timeline Outline", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.TIMELINE_OUTLINE, targetPlacement);
      Tab t = ensureVnsTimelineOutlineTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Timeline Outline", ensureVnsTimelineOutlineView(), 500, 600, EditorSidebarPanel.TIMELINE_OUTLINE), () -> {
      rememberPanelPlacement(EditorSidebarPanel.TIMELINE_OUTLINE, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.ASSETS, targetPlacement, "Assets", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.ASSETS, targetPlacement);
      Tab t = ensureAssetBrowserTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Asset Browser", ensureAssetBrowserView(), 700, 600, EditorSidebarPanel.ASSETS), () -> {
      rememberPanelPlacement(EditorSidebarPanel.ASSETS, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.LAYOUT_LAUNCHER, targetPlacement, "Layout Launcher", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.LAYOUT_LAUNCHER, targetPlacement);
      Tab t = ensureLayoutLauncherTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (layoutEditorLauncherView != null) layoutEditorLauncherView.refreshStatus();
    }, () -> {
      LayoutEditorLauncherView view = ensureLayoutEditorLauncherView();
      if (view != null) view.refreshStatus();
      launchPanelAsWindow("Layout Launcher", view, 700, 700, EditorSidebarPanel.LAYOUT_LAUNCHER);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.LAYOUT_LAUNCHER, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.STORYBOARD_OVERLAY, targetPlacement, "Storyboard Overlay", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.STORYBOARD_OVERLAY, targetPlacement);
      Tab t = ensureStoryboardOverlayTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      refreshStoryboardOverlayContext(getActiveFileTab());
    }, () -> {
      StoryboardOverlayView view = ensureStoryboardOverlayView();
      refreshStoryboardOverlayContext(getActiveFileTab());
      launchPanelAsWindow("Storyboard Overlay", view, 520, 780, EditorSidebarPanel.STORYBOARD_OVERLAY);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.STORYBOARD_OVERLAY, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.LAYERED_IMAGES, targetPlacement, "Layered Image Visualizer", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.LAYERED_IMAGES, targetPlacement);
      Tab t = ensureLayeredImageVisualizerTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (layeredImageVisualizerView != null) layeredImageVisualizerView.refreshCatalog();
    }, () -> {
      LayeredImageVisualizerView view = ensureLayeredImageVisualizerView();
      if (view != null) view.refreshCatalog();
      launchPanelAsWindow("Layered Image Visualizer", view, 900, 700, EditorSidebarPanel.LAYERED_IMAGES);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.LAYERED_IMAGES, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.IMAGE_ATTRIBUTES, targetPlacement, "Image Attributes Tool", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.IMAGE_ATTRIBUTES, targetPlacement);
      Tab t = ensureImageAttributesToolTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (imageAttributesToolView != null) imageAttributesToolView.refreshCatalog();
    }, () -> {
      ImageAttributesToolView view = ensureImageAttributesToolView();
      if (view != null) view.refreshCatalog();
      launchPanelAsWindow("Image Attributes Tool", wrapMaintenance(view), 800, 650, EditorSidebarPanel.IMAGE_ATTRIBUTES);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.IMAGE_ATTRIBUTES, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.IMAGE_TINT, targetPlacement, "Scene Lighting Studio", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.IMAGE_TINT, targetPlacement);
      Tab t = ensureImageTintToolTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (imageTintToolView != null) imageTintToolView.refreshCatalog();
    }, () -> {
      ImageTintToolView view = ensureImageTintToolView();
      if (view != null) view.refreshCatalog();
      launchPanelAsWindow("Scene Lighting Studio", view, 800, 650, EditorSidebarPanel.IMAGE_TINT);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.IMAGE_TINT, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.VERSION_CONTROL, targetPlacement, "Version Control", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.VERSION_CONTROL, targetPlacement);
      Tab t = ensureVersionControlTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (versionControlView != null) versionControlView.refreshStatus();
    }, () -> {
      VersionControlView view = ensureVersionControlView();
      if (view != null) view.refreshStatus();
      launchPanelAsWindow("Version Control", view, 700, 600, EditorSidebarPanel.VERSION_CONTROL);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.VERSION_CONTROL, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.PUPPETEER_LAUNCHER, targetPlacement, "Puppeteer Launcher", null, () -> {
      rememberPanelPlacement(EditorSidebarPanel.PUPPETEER_LAUNCHER, targetPlacement);
      Tab t = ensurePuppeteerLauncherTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Puppeteer Launcher", ensurePuppeteerLauncherPanel(), 600, 500, EditorSidebarPanel.PUPPETEER_LAUNCHER), () -> {
      rememberPanelPlacement(EditorSidebarPanel.PUPPETEER_LAUNCHER, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, null, targetPlacement, "Editor Settings", "icon-panel-settings", () -> {
      Tab t = ensureEditorSettingsTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Editor Settings", ensureEditorSettingsView(), 520, 760, null), null);

    installPanelChooserFilter(filter, actions);
    VBox.setVgrow(actions, Priority.ALWAYS);
    ScrollPane chooserScroll = new ScrollPane(root);
    chooserScroll.setFitToWidth(true);
    chooserScroll.setFitToHeight(true);
    chooserScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    chooserScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    chooserScroll.setPannable(true);
    root.getChildren().addAll(heading, info, filter, new javafx.scene.control.Separator(), actions);
    Tab chooser = new Tab("New Panel", chooserScroll);
    chooser.setClosable(true);
    chooser.getStyleClass().add("panel-chooser-tab");
    chooser.getProperties().put(PANEL_CHOOSER_TAB_ROLE, PANEL_CHOOSER_TAB_ROLE);
    chooser.getProperties().put("chooserSide", leftSide ? "left" : "right");
    chooser.setOnClosed(e -> refreshOpenPanelChooserIndicators());
    int addIdx = pane.getTabs().indexOf(addTab);
    if (addIdx < 0) addIdx = pane.getTabs().size();
    pane.getTabs().add(addIdx, chooser);
    ensureSidebarVisible(pane);
    pane.getSelectionModel().select(chooser);
    Platform.runLater(() -> {
      pane.requestLayout();
      chooserScroll.requestLayout();
      root.requestLayout();
      filter.requestFocus();
    });
  }

  private void showLeftAddMenu() {
    openPanelChooserTab(leftTabs, true);
  }

  private void showRightAddMenu() {
    openPanelChooserTab(rightTabs, false);
  }

  private void selectProjectTab() {
    Tab t = (tabProject != null && tabProject.getTabPane() != null)
        ? tabProject
        : ensureProjectTab(leftTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectTrashmanTab() {
    Tab t = (tabTrashman != null && tabTrashman.getTabPane() != null)
        ? tabTrashman
        : ensureTrashmanTab(leftTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
    if (trashmanView != null) trashmanView.refreshStatus();
  }

  private void selectWorkspaceHubTab() {
    if (filesTabs == null || tabWorkspaceHub == null) return;
    if (!filesTabs.getTabs().contains(tabWorkspaceHub)) {
      filesTabs.getTabs().add(0, tabWorkspaceHub);
    }
    filesTabs.getSelectionModel().select(tabWorkspaceHub);
  }

  private void selectTimelineTab() {
    Tab t = (tabTimeline != null && tabTimeline.getTabPane() != null) ? tabTimeline : ensureTimelineTab(leftTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectVersionControlTab() {
    Tab t = (tabVersionControl != null && tabVersionControl.getTabPane() != null)
        ? tabVersionControl
        : ensureVersionControlTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
    if (versionControlView != null) versionControlView.refreshStatus();
  }

  private void selectScriptEditorLauncherTab() {
    ScriptEditorLauncherView launcher = ensureScriptEditorLauncherView();
    launcher.setProjectRoot(projectRoot);
    launcher.setWorkspaceRoot(resolveWorkspaceRoot());
    launcher.launchEditorWindow();
    rememberPanelPlacement(EditorSidebarPanel.SCRIPT_EDITOR, EditorPanelPlacement.HIDDEN);
  }

  private void selectVnsDiagnosticsTab() {
    Tab t = (tabVnsDiagnostics != null && tabVnsDiagnostics.getTabPane() != null)
        ? tabVnsDiagnostics
        : ensureVnsDiagnosticsTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectVnsFlowMapTab() {
    Tab t = (tabVnsFlowMap != null && tabVnsFlowMap.getTabPane() != null)
        ? tabVnsFlowMap
        : ensureVnsFlowMapTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectAssetAutoLabelDashboard() {
    selectAssetBrowserTab();
    ensureAssetBrowserView().showAutoLabelDashboard();
  }

  private void selectAssetBrowserTab() {
    Tab t = (tabAssetBrowser != null && tabAssetBrowser.getTabPane() != null)
        ? tabAssetBrowser
        : ensureAssetBrowserTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectLayoutLauncherTab() {
    Tab t = (tabLayoutLauncher != null && tabLayoutLauncher.getTabPane() != null)
        ? tabLayoutLauncher
        : ensureLayoutLauncherTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
    if (layoutEditorLauncherView != null) layoutEditorLauncherView.refreshStatus();
  }

  private void selectStoryboardOverlayTab() {
    Tab t = (tabStoryboardOverlay != null && tabStoryboardOverlay.getTabPane() != null)
        ? tabStoryboardOverlay
        : ensureStoryboardOverlayTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
    refreshStoryboardOverlayContext(getActiveFileTab());
  }

  private void selectLayeredImageVisualizerTab() {
      LayeredImageVisualizerView view = ensureLayeredImageVisualizerView();
    if (view != null) view.refreshCatalog();
    launchPanelAsWindow("Layered Image Visualizer", view, 900, 700, EditorSidebarPanel.LAYERED_IMAGES);
  }

  private void selectImageAttributesToolTab() {
    Tab t = (tabImageAttributesTool != null && tabImageAttributesTool.getTabPane() != null)
        ? tabImageAttributesTool
        : ensureImageAttributesToolTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
    if (imageAttributesToolView != null) imageAttributesToolView.refreshCatalog();
  }

  private void selectImageTintToolTab() {
    Tab t = (tabImageTintTool != null && tabImageTintTool.getTabPane() != null)
        ? tabImageTintTool
        : ensureImageTintToolTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
    if (imageTintToolView != null) imageTintToolView.refreshCatalog();
  }

  private void setStoryboardOverlayState(StoryboardOverlayState storyboardOverlayState) {
    this.storyboardOverlayState =
        storyboardOverlayState == null ? StoryboardOverlayState.none() : storyboardOverlayState;
    if (storyboardOverlayView != null) {
      storyboardOverlayView.applyExternalState(this.storyboardOverlayState);
    }
    applyStoryboardOverlayToOpenTabs();
  }

  private void applyStoryboardOverlayToOpenTabs() {
    if (filesTabs == null) return;
    for (Tab tab : filesTabs.getTabs()) {
      if (tab.getContent() instanceof FileEditorTab fileTab) {
        fileTab.setStoryboardOverlay(storyboardOverlayState);
      }
    }
  }

  private void syncStoryboardOverlayProjectState() {
    if (storyboardOverlayView != null) {
      storyboardOverlayView.setProjectRoot(projectRoot);
      refreshStoryboardOverlayContext(getActiveFileTab());
    } else {
      setStoryboardOverlayState(StoryboardOverlayState.none());
    }
  }

  private void refreshStoryboardOverlayContext(FileEditorTab fileTab) {
    if (storyboardOverlayView == null) return;
    ProjectViewportSpec.Dimensions dims = ProjectViewportSpec.resolve(projectRoot);
    String label;
    File activeScript = null;
    if (fileTab == null) {
      label = "Active preview: open a JES or VNS tab. Overlay locks to " + dims.width() + "x" + dims.height() + ".";
    } else if (fileTab.getKind() == FileEditorTab.Kind.JES) {
      activeScript = fileTab.getFile();
      label = "Active preview: JES scene. Overlay locks to " + dims.width() + "x" + dims.height() + ".";
    } else if (fileTab.getKind() == FileEditorTab.Kind.VNS) {
      activeScript = fileTab.getFile();
      label = "Active preview: VNS scene. Overlay locks to " + dims.width() + "x" + dims.height() + ".";
    } else {
      label = "Active preview: " + fileTab.getDisplayName() + " has no JES/VNS preview.";
    }
    storyboardOverlayView.setActivePreviewLabel(label);
    storyboardOverlayView.setActiveScriptFile(activeScript);
  }

  private void selectInspectorTab() {
    Tab t = (tabInspector != null && tabInspector.getTabPane() != null)
        ? tabInspector
        : ensureInspectorTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectPuppeteerLauncherTab() {
    Tab t = (tabPuppeteerLauncher != null && tabPuppeteerLauncher.getTabPane() != null)
        ? tabPuppeteerLauncher
        : ensurePuppeteerLauncherTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectEditorSettingsTab() {
    Tab t = (tabEditorSettings != null && tabEditorSettings.getTabPane() != null)
        ? tabEditorSettings
        : ensureEditorSettingsTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private Tab ensurePuppeteerLauncherTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.PUPPETEER_LAUNCHER, true);
    PuppeteerLauncherPanel launcher = ensurePuppeteerLauncherPanel();
    if (targetPane == null || launcher == null) return null;
    if (tabPuppeteerLauncher == null) {
      tabPuppeteerLauncher = new Tab("Puppeteer Launcher", launcher);
      tabPuppeteerLauncher.setClosable(true);
      tabPuppeteerLauncher.setOnClosed(e -> {
        tabPuppeteerLauncher = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.PUPPETEER_LAUNCHER);
      });
    } else if (tabPuppeteerLauncher.getContent() != launcher) {
      tabPuppeteerLauncher.setContent(launcher);
    }
    return attachSidebarPanelTab(tabPuppeteerLauncher, EditorSidebarPanel.PUPPETEER_LAUNCHER, targetPane);
  }

  private Tab ensureEditorSettingsTab(TabPane targetPane) {
    closeEditorSettingsWindow(true);
    EditorSettingsView settingsView = ensureEditorSettingsView();
    if (targetPane == null || settingsView == null) return null;
    if (tabEditorSettings == null) {
      tabEditorSettings = new Tab("Editor Settings", settingsView);
      tabEditorSettings.setClosable(true);
      tabEditorSettings.setOnClosed(e -> {
        tabEditorSettings = null;
        releaseEditorSettingsIfUnused();
      });
    } else if (tabEditorSettings.getContent() != settingsView) {
      tabEditorSettings.setContent(settingsView);
    }
    tabEditorSettings.setGraphic(editorSettingsSidebarIcon("sidebar-tab-icon"));
    attachPanelTabToPane(tabEditorSettings, targetPane);
    return tabEditorSettings;
  }

  private void launchPuppeteerFromLauncher(PuppeteerLauncherPanel.LaunchRequest request) {
    PuppeteerLauncherPanel.SceneSnapshot snapshot = request != null ? request.snapshot() : null;
    String selectedTimelineName = request != null ? request.timelineName() : null;
    PuppeteerLauncherPanel.LaunchTimelineMode launchMode = request != null
        ? request.mode()
        : PuppeteerLauncherPanel.LaunchTimelineMode.AUTO;
    boolean newFromSnapshot = launchMode == PuppeteerLauncherPanel.LaunchTimelineMode.NEW_FROM_SNAPSHOT;
    String preferredTimelineName = selectedTimelineName;
    AnimationProject imported;
    if (selectedTimelineName != null && !selectedTimelineName.isBlank()) {
      imported = importNamedTimeline(selectedTimelineName, true);
    } else if (newFromSnapshot) {
      imported = null;
      preferredTimelineName = null;
    } else {
      preferredTimelineName = snapshot != null ? snapshot.preferredTimelineName() : null;
      imported = importTimelineFromSnapshot(snapshot);
    }
    if (imported != null && shouldApplySnapshotTimelineAtCursor(selectedTimelineName, snapshot)) {
      imported.setPlayheadMs(imported.getTotalDurationMs());
    }
    PuppeteerWindow puppeteer = imported != null
        ? new PuppeteerWindow(imported)
        : new PuppeteerWindow();
    puppeteer.setOnCopyCode(code -> status.setText("Copied timeline code to clipboard"));
    puppeteer.setOnSyncSnapshotRequested(() -> {
      FileEditorTab currentFt = getActiveFileTab();
      if (currentFt == null) return;
      int caretLine = currentFt.getVnsCaretLine();
      PuppeteerLauncherPanel.SceneSnapshot newSnapshot = PuppeteerLauncherPanel.resolveSnapshot(currentFt.getCurrentTextSnapshot(), caretLine);
      JesScene2D newScene = buildSceneFromSnapshot(newSnapshot);
      puppeteer.syncWithSnapshot(newSnapshot, newScene);
    });
    if (projectRoot != null) puppeteer.setProjectRoot(projectRoot);
    puppeteer.setLaunchSceneSnapshot(snapshot);
    if (preferredTimelineName != null && !preferredTimelineName.isBlank()) {
      puppeteer.setTimelineName(preferredTimelineName);
    }
    FileEditorTab ft = getActiveFileTab();
    if (ft != null) {
      puppeteer.setSourceScriptFile(ft.getFile());
    }

    boolean treatImportedTimelineAsVnOffsets = shouldTreatImportedTimelineAsVnOffsets(
        selectedTimelineName,
        snapshot,
        imported,
        launchMode);
    Map<String, Map<PropertyType, Double>> runtimeExportBaselines = Map.of();
    Map<String, Map<PropertyType, Double>> importVnOffsetBaselines = Map.of();
    boolean loadTimelineFromSnapshot = shouldPrimeLoadedTimelineScene(
        selectedTimelineName,
        snapshot,
        imported,
        launchMode);
    JesScene2D launchScene;
    if (loadTimelineFromSnapshot) {
      launchScene = resolvePuppeteerBaseLaunchScene(ft, imported, snapshot);
      if (launchScene != null) {
        importVnOffsetBaselines = captureRuntimeExportBaselines(launchScene);
        applySnapshotTimelineHistoryBeforeLoadedTimeline(
            launchScene,
            snapshot,
            importVnOffsetBaselines);
        runtimeExportBaselines = importVnOffsetBaselines;
        attachImportedTimelineToLaunchScene(
            launchScene,
            imported,
            snapshot,
            treatImportedTimelineAsVnOffsets,
            importVnOffsetBaselines);
      }
    } else {
      launchScene = resolvePuppeteerLaunchScene(
          ft,
          imported,
          snapshot,
          treatImportedTimelineAsVnOffsets);
    }
    if (newFromSnapshot && launchScene != null && snapshot != null) {
      runtimeExportBaselines = captureRuntimeExportBaselines(launchScene);
      applySnapshotTimelineEndStateToScene(
          launchScene,
          snapshot,
          selectedTimelineName,
          runtimeExportBaselines);
    }
    if (launchScene != null) {
      puppeteer.setRuntimeExportBaselines(runtimeExportBaselines);
      puppeteer.setScene(launchScene);
    }

    if (snapshot != null) {
      String title = "Puppeteer";
      if (preferredTimelineName != null && !preferredTimelineName.isBlank()) {
        title += " - " + preferredTimelineName;
      }
      if (snapshot.currentLabel != null) title += " @ " + snapshot.currentLabel;
      title += " (line " + (snapshot.atLine + 1) + ")";
      puppeteer.setTitle(title);
    }
    puppeteer.show();
    if (puppeteerLauncherPanel != null && preferredTimelineName != null && !preferredTimelineName.isBlank()) {
      puppeteerLauncherPanel.setActiveEditingTimeline(preferredTimelineName);
      puppeteer.showingProperty().addListener((obs, oldVal, newVal) -> {
        if (!newVal && puppeteerLauncherPanel != null) {
          puppeteerLauncherPanel.setActiveEditingTimeline(null);
        }
      });
    }
  }

  private boolean shouldApplySnapshotTimelineAtCursor(
      String selectedTimelineName,
      PuppeteerLauncherPanel.SceneSnapshot snapshot
  ) {
    if (selectedTimelineName != null && !selectedTimelineName.isBlank()) return false;
    if (snapshot == null) return false;
    if (snapshot.hasInlineTimeline()) return true;
    return snapshot.referencedTimelineName != null && !snapshot.referencedTimelineName.isBlank();
  }

  private boolean shouldPrimeLoadedTimelineScene(
      String selectedTimelineName,
      PuppeteerLauncherPanel.SceneSnapshot snapshot,
      AnimationProject imported,
      PuppeteerLauncherPanel.LaunchTimelineMode launchMode
  ) {
    if (imported == null || snapshot == null || !snapshot.hasTimelineContext()) return false;
    if (selectedTimelineName != null && !selectedTimelineName.isBlank()) return false;
    return launchMode == PuppeteerLauncherPanel.LaunchTimelineMode.LOAD_TIMELINE;
  }

  private JesScene2D resolvePuppeteerLaunchScene(
      FileEditorTab fileTab,
      AnimationProject imported,
      PuppeteerLauncherPanel.SceneSnapshot snapshot
  ) {
    return resolvePuppeteerLaunchScene(fileTab, imported, snapshot, false);
  }

  private JesScene2D resolvePuppeteerLaunchScene(
      FileEditorTab fileTab,
      AnimationProject imported,
      PuppeteerLauncherPanel.SceneSnapshot snapshot,
      boolean importedTimelineUsesVnOffsets
  ) {
    JesScene2D scene = resolvePuppeteerBaseLaunchScene(fileTab, imported, snapshot);
    attachImportedTimelineToLaunchScene(scene, imported, snapshot, importedTimelineUsesVnOffsets, null);
    return scene;
  }

  private JesScene2D resolvePuppeteerBaseLaunchScene(
      FileEditorTab fileTab,
      AnimationProject imported,
      PuppeteerLauncherPanel.SceneSnapshot snapshot
  ) {
    boolean snapshotHasScene = snapshot != null && (snapshot.backgroundId != null || !snapshot.characters.isEmpty());
    JesScene2D scene;
    if (snapshotHasScene) {
      scene = buildSceneFromSnapshot(snapshot);
    } else {
      scene = new JesScene2D();
    }
    return scene;
  }

  private void attachImportedTimelineToLaunchScene(
      JesScene2D scene,
      AnimationProject imported,
      PuppeteerLauncherPanel.SceneSnapshot snapshot,
      boolean importedTimelineUsesVnOffsets,
      Map<String, Map<PropertyType, Double>> vnOffsetBaselines
  ) {
    if (scene != null && imported != null) {
      ensureSceneEntitiesForProject(scene, imported, snapshot);
      rebaseImportedPuppeteerProject(scene, imported, importedTimelineUsesVnOffsets, vnOffsetBaselines);
    }
  }

  private boolean shouldTreatImportedTimelineAsVnOffsets(
      String selectedTimelineName,
      PuppeteerLauncherPanel.SceneSnapshot snapshot,
      AnimationProject imported,
      PuppeteerLauncherPanel.LaunchTimelineMode launchMode
  ) {
    if (imported == null || snapshot == null) return false;
    if (selectedTimelineName != null && !selectedTimelineName.isBlank()) return false;
    if (launchMode == PuppeteerLauncherPanel.LaunchTimelineMode.NEW_FROM_SNAPSHOT) return false;
    if (!imported.getSceneEntitySnapshotsView().isEmpty()) return false;
    return snapshot.hasTimelineContext();
  }

  private boolean isPuppeteerTimelineFile(File file) {
    if (file == null) return false;
    String name = file.getName().toLowerCase(Locale.ROOT);
    if (!name.endsWith(".jes")) return false;
    try {
      Path filePath = file.toPath().toAbsolutePath().normalize();
      if (projectRoot != null) {
        Path timelinesRoot = projectRoot.toPath().toAbsolutePath().normalize()
            .resolve("scripts").resolve("timelines").normalize();
        return filePath.startsWith(timelinesRoot);
      }
      String normalized = filePath.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
      return normalized.contains("/scripts/timelines/");
    } catch (Exception ex) {
      return false;
    }
  }

  private void ensureSceneEntitiesForProject(
      JesScene2D scene,
      AnimationProject imported,
      PuppeteerLauncherPanel.SceneSnapshot snapshot
  ) {
    if (scene == null || imported == null) return;
    List<EntityTrack> missingTracks = new ArrayList<>();
    for (EntityTrack track : imported.getTracks()) {
      if (track == null) continue;
      String entityName = track.getEntityName();
      if (entityName == null || entityName.isBlank()) continue;
      if (scene.find(entityName) != null) continue;
      if (isCameraTrack(track)) continue;
      missingTracks.add(track);
    }

    for (int i = 0; i < missingTracks.size(); i++) {
      addMissingSceneEntity(scene, imported, missingTracks.get(i), snapshot, i, missingTracks.size());
    }
  }

  private void rebaseImportedPuppeteerProject(JesScene2D scene, AnimationProject imported) {
    rebaseImportedPuppeteerProject(scene, imported, false);
  }

  private void rebaseImportedPuppeteerProject(
      JesScene2D scene,
      AnimationProject imported,
      boolean importedTimelineUsesVnOffsets
  ) {
    rebaseImportedPuppeteerProject(scene, imported, importedTimelineUsesVnOffsets, null);
  }

  private void rebaseImportedPuppeteerProject(
      JesScene2D scene,
      AnimationProject imported,
      boolean importedTimelineUsesVnOffsets,
      Map<String, Map<PropertyType, Double>> vnOffsetBaselines
  ) {
    if (scene == null || imported == null) return;
    Map<String, Map<PropertyType, Double>> baseline = captureTimelineEntityBaseline(scene, imported);
    if (baseline.isEmpty()) return;
    if (importedTimelineUsesVnOffsets) {
      translateVnOffsetPositionKeysToSceneCoordinates(
          imported,
          timelineEntityBaselines(imported, vnOffsetBaselines, baseline));
    }
    imported.setInitialSnapshot(baseline);
    if (!importedTimelineUsesVnOffsets) {
      imported.rebaseAutogeneratedStartKeyframes(baseline);
    }
  }

  private Map<String, Map<PropertyType, Double>> timelineEntityBaselines(
      AnimationProject imported,
      Map<String, Map<PropertyType, Double>> preferredBaselines,
      Map<String, Map<PropertyType, Double>> fallbackBaselines
  ) {
    if (imported == null) return fallbackBaselines == null ? Map.of() : fallbackBaselines;
    Map<String, Map<PropertyType, Double>> resolved = new LinkedHashMap<>();
    for (EntityTrack track : imported.getTracks()) {
      if (track == null || isCameraTrack(track)) continue;
      String entityName = track.getEntityName();
      if (entityName == null || entityName.isBlank()) continue;
      Map<PropertyType, Double> preferred = preferredBaselines == null ? null : preferredBaselines.get(entityName);
      Map<PropertyType, Double> fallback = fallbackBaselines == null ? null : fallbackBaselines.get(entityName);
      Map<PropertyType, Double> chosen = preferred != null ? preferred : fallback;
      if (chosen != null && !chosen.isEmpty()) {
        resolved.put(entityName, chosen);
      }
    }
    return resolved;
  }

  private Map<String, Map<PropertyType, Double>> captureTimelineEntityBaseline(
      JesScene2D scene,
      AnimationProject imported
  ) {
    Map<String, Map<PropertyType, Double>> baseline = new LinkedHashMap<>();
    if (scene == null || imported == null) return baseline;
    for (EntityTrack track : imported.getTracks()) {
      if (track == null || isCameraTrack(track)) continue;
      String entityName = track.getEntityName();
      if (entityName == null || entityName.isBlank()) continue;
      Entity2D entity = scene.find(entityName);
      if (entity == null) continue;
      baseline.put(entityName, captureEntityProperties(entity));
    }
    return baseline;
  }

  private Map<String, Map<PropertyType, Double>> captureRuntimeExportBaselines(JesScene2D scene) {
    Map<String, Map<PropertyType, Double>> baseline = new LinkedHashMap<>();
    if (scene == null) return baseline;
    for (String entityName : scene.names()) {
      if (entityName == null || entityName.isBlank()) continue;
      Entity2D entity = scene.find(entityName);
      if (entity == null) continue;
      baseline.put(entityName, captureEntityProperties(entity));
    }
    return baseline;
  }

  private Map<PropertyType, Double> captureEntityProperties(Entity2D entity) {
    Map<PropertyType, Double> props = new EnumMap<>(PropertyType.class);
    if (entity == null) return props;
    props.put(PropertyType.X, entity.getX());
    props.put(PropertyType.Y, entity.getY());
    props.put(PropertyType.Z, entity.getZ());
    props.put(PropertyType.PIVOT_X, entity.getOriginX());
    props.put(PropertyType.PIVOT_Y, entity.getOriginY());
    props.put(PropertyType.ROTATION, entity.getRotationDeg());
    props.put(PropertyType.SCALE_X, entity.getScaleX());
    props.put(PropertyType.SCALE_Y, entity.getScaleY());
    props.put(PropertyType.MIRROR_X, PropertyType.MIRROR_X.getDefaultValue());
    props.put(PropertyType.ALPHA, entityAlpha(entity));
    props.put(PropertyType.VISIBILITY, entity.isVisible() ? 1.0 : 0.0);
    props.put(PropertyType.MATRIX_MXX, entity.getMatrixMxx());
    props.put(PropertyType.MATRIX_MXY, entity.getMatrixMxy());
    props.put(PropertyType.MATRIX_MYX, entity.getMatrixMyx());
    props.put(PropertyType.MATRIX_MYY, entity.getMatrixMyy());
    props.put(PropertyType.MATRIX_TX, entity.getMatrixTx());
    props.put(PropertyType.MATRIX_TY, entity.getMatrixTy());
    props.put(PropertyType.BLUR, entity.getBlurRadius());
    props.put(PropertyType.BRIGHTNESS, entity.getBrightness());
    return props;
  }

  private void translateVnOffsetPositionKeysToSceneCoordinates(
      AnimationProject imported,
      Map<String, Map<PropertyType, Double>> baseline
  ) {
    if (imported == null || baseline == null || baseline.isEmpty()) return;
    for (EntityTrack track : imported.getTracks()) {
      if (track == null || isCameraTrack(track)) continue;
      Map<PropertyType, Double> props = baseline.get(track.getEntityName());
      if (props == null) continue;
      translateKeyframes(track.getKeyframes(PropertyType.X), props.get(PropertyType.X));
      translateKeyframes(track.getKeyframes(PropertyType.Y), props.get(PropertyType.Y));
    }
  }

  private void translateKeyframes(List<Keyframe> keyframes, Double delta) {
    if (keyframes == null || keyframes.isEmpty() || delta == null || !Double.isFinite(delta)) return;
    if (Math.abs(delta) <= 0.0001) return;
    for (Keyframe keyframe : keyframes) {
      if (keyframe != null) {
        keyframe.setValue(keyframe.getValue() + delta);
      }
    }
  }

	  private void applySnapshotTimelineEndStateToScene(
	      JesScene2D scene,
	      PuppeteerLauncherPanel.SceneSnapshot snapshot,
	      String selectedTimelineName,
	      Map<String, Map<PropertyType, Double>> vnOffsetBaselines
	  ) {
	    if (scene == null || snapshot == null || !snapshot.hasTimelineContext()) return;
	    Map<String, Map<PropertyType, Double>> snapshotBaselines = snapshotTimelineBaselines(
	        scene, snapshot, vnOffsetBaselines);
	    if (selectedTimelineName == null && snapshot.hasInlineTimelineHistory()) {
	      boolean applied = false;
	      for (PuppeteerLauncherPanel.InlineTimelineContext context : snapshot.inlineTimelineHistory) {
	        AnimationProject stateTimeline = importInlineTimelineFromSnapshot(snapshot, context, false);
	        if (stateTimeline == null) continue;
	        applySnapshotStateTimelineToScene(scene, snapshot, stateTimeline, true, snapshotBaselines);
	        applied = true;
	      }
	      if (applied) return;
	    }
	    AnimationProject stateTimeline = importTimelineFromSnapshot(snapshot, false);
	    if (stateTimeline == null) return;
	    boolean usesVnOffsets = selectedTimelineName == null
	        && stateTimeline.getSceneEntitySnapshotsView().isEmpty()
	        && snapshot.hasTimelineContext();
	    applySnapshotStateTimelineToScene(scene, snapshot, stateTimeline, usesVnOffsets, snapshotBaselines);
	  }

	  private Map<String, Map<PropertyType, Double>> snapshotTimelineBaselines(
	      JesScene2D scene,
	      PuppeteerLauncherPanel.SceneSnapshot snapshot,
	      Map<String, Map<PropertyType, Double>> provided
	  ) {
	    Map<String, Map<PropertyType, Double>> baselines = new LinkedHashMap<>();
	    if (provided != null) baselines.putAll(provided);
	    if (scene == null || snapshot == null) return baselines;
	    for (PuppeteerLauncherPanel.CharacterEntry character : snapshot.characters) {
	      if (character == null) continue;
	      Entity2D anchor = firstSnapshotEntity(scene, snapshot, character);
	      if (anchor != null && character.characterId != null && !character.characterId.isBlank()) {
	        baselines.putIfAbsent(character.characterId, captureEntityProperties(anchor));
	        String safeCharacter = selectorSafeName(character.characterId);
	        if (!safeCharacter.isBlank()) {
	          baselines.putIfAbsent(safeCharacter, captureEntityProperties(anchor));
	        }
	      }
	      for (PuppeteerLauncherPanel.CharacterLayerGroupEntry group :
	          snapshot.resolveCharacterLayerGroups(character.characterId, character.expression)) {
	        if (group == null) continue;
	        for (String alias : PuppeteerLauncherPanel.equivalentSnapshotLayerGroupEntityNames(
	            snapshot, character, group.groupId)) {
	          Entity2D groupAnchor = scene.find(alias);
	          if (groupAnchor != null && alias != null && !alias.isBlank()) {
	            baselines.putIfAbsent(alias, captureEntityProperties(groupAnchor));
	          }
	        }
	      }
	    }
	    return baselines;
	  }

	  private void applySnapshotTimelineHistoryBeforeLoadedTimeline(
	      JesScene2D scene,
	      PuppeteerLauncherPanel.SceneSnapshot snapshot,
	      Map<String, Map<PropertyType, Double>> vnOffsetBaselines
	  ) {
	    if (scene == null || snapshot == null || !snapshot.hasInlineTimelineHistory()) return;
	    Map<String, Map<PropertyType, Double>> snapshotBaselines = snapshotTimelineBaselines(
	        scene, snapshot, vnOffsetBaselines);
	    PuppeteerLauncherPanel.InlineTimelineContext loadedTimeline = snapshot.hasInlineTimeline()
	        ? snapshot.inlineTimelineHistory.get(snapshot.inlineTimelineHistory.size() - 1)
	        : null;
	    for (PuppeteerLauncherPanel.InlineTimelineContext context : snapshot.inlineTimelineHistory) {
	      if (context == null) continue;
	      if (loadedTimeline != null
	          && context.startLine() == loadedTimeline.startLine()
	          && context.endLine() == loadedTimeline.endLine()) {
	        break;
	      }
	      AnimationProject stateTimeline = importInlineTimelineFromSnapshot(snapshot, context, false);
	      if (stateTimeline == null) continue;
	      applySnapshotStateTimelineToScene(scene, snapshot, stateTimeline, true, snapshotBaselines);
	    }
	  }

	  private void applySnapshotStateTimelineToScene(
	      JesScene2D scene,
	      PuppeteerLauncherPanel.SceneSnapshot snapshot,
	      AnimationProject stateTimeline,
	      boolean usesVnOffsets,
	      Map<String, Map<PropertyType, Double>> vnOffsetBaselines
	  ) {
	    if (scene == null || snapshot == null || stateTimeline == null) return;
	    Set<String> snapshotEntities = currentSnapshotEntityNames(snapshot);
	    ensureSceneEntitiesForProject(scene, stateTimeline, snapshot);
	    rebaseImportedPuppeteerProject(
	        scene,
	        stateTimeline,
	        usesVnOffsets && stateTimeline.getSceneEntitySnapshotsView().isEmpty(),
	        vnOffsetBaselines);
	    applyTimelineFrameToScene(scene, stateTimeline, stateTimeline.getTotalDurationMs(), snapshot);
	    hideReplayOnlyEntities(scene, stateTimeline, snapshotEntities);
	  }

	  private void applyTimelineFrameToScene(JesScene2D scene, AnimationProject project, double timeMs) {
	    applyTimelineFrameToScene(scene, project, timeMs, null);
	  }

	  private void applyTimelineFrameToScene(
	      JesScene2D scene,
	      AnimationProject project,
	      double timeMs,
	      PuppeteerLauncherPanel.SceneSnapshot snapshot
	  ) {
	    if (scene == null || project == null) return;
	    double t = Math.max(0.0, timeMs);
	    Map<String, SnapshotCharacterTimelineState> characterStates = snapshot == null
	        ? Map.of()
	        : new LinkedHashMap<>();
	    for (EntityTrack track : project.getTracks()) {
	      if (track == null || isCameraTrack(track)) continue;
	      SnapshotLayerGroupTarget groupTarget = resolveSnapshotLayerGroupTarget(
	          scene, snapshot, track.getEntityName());
	      if (groupTarget != null) {
	        for (Entity2D member : groupTarget.members()) {
	          applySnapshotGroupPivot(member, groupTarget.group(), track);
	          applyTimelineTrackFrame(member, track, t);
	        }
	        // A declared but inactive overlapping group is still a resolved group target. Do not
	        // fall through and reinterpret it as a whole-character alias.
	        continue;
	      }
	      String characterId = resolveSnapshotTrackCharacter(track.getEntityName(), snapshot);
	      if (characterId != null && !characterId.isBlank()) {
	        characterStates
	            .computeIfAbsent(characterId, ignored -> new SnapshotCharacterTimelineState())
	            .accept(track, t, track.getEntityName() != null && track.getEntityName().trim().equals(characterId));
	      }
	      Entity2D entity = scene.find(track.getEntityName());
	      if (entity == null) continue;
	
	      applyTimelineTrackFrame(entity, track, t);
	    }
	    applySnapshotCharacterTimelineStates(scene, snapshot, characterStates);
	  }

	  private SnapshotLayerGroupTarget resolveSnapshotLayerGroupTarget(
	      JesScene2D scene,
	      PuppeteerLauncherPanel.SceneSnapshot snapshot,
	      String entityName
	  ) {
	    if (scene == null || snapshot == null || entityName == null || entityName.isBlank()) return null;
	    String target = entityName.trim();
	    for (PuppeteerLauncherPanel.CharacterEntry character : snapshot.characters) {
	      if (character == null) continue;
	      List<PuppeteerLauncherPanel.CharacterLayerEntry> visibleLayers =
	          snapshot.resolveCharacterLayers(character.characterId, character.expression);
	      List<PuppeteerLauncherPanel.CharacterLayerGroupEntry> visibleGroups =
	          snapshot.resolveCharacterLayerGroups(character.characterId, character.expression);
	      for (PuppeteerLauncherPanel.CharacterLayerGroupEntry group : visibleGroups) {
	        if (group == null || !PuppeteerLauncherPanel.equivalentSnapshotLayerGroupEntityNames(
	            snapshot, character, group.groupId).contains(target)) {
	          continue;
	        }
	        List<Entity2D> members = new ArrayList<>();
	        for (PuppeteerLauncherPanel.CharacterLayerEntry layer : visibleLayers) {
	          if (layer == null || !snapshotLayerGroupChain(layer.layerId, visibleGroups).contains(group)) {
	            continue;
	          }
	          Entity2D member = scene.find(PuppeteerLauncherPanel.snapshotStableLayerEntityName(
	              character.characterId, layer.layerId));
	          if (member != null && !members.contains(member)) members.add(member);
	        }
	        return new SnapshotLayerGroupTarget(group, List.copyOf(members));
	      }
	    }
	    return null;
	  }

	  private List<PuppeteerLauncherPanel.CharacterLayerGroupEntry> snapshotLayerGroupChain(
	      String layerId,
	      List<PuppeteerLauncherPanel.CharacterLayerGroupEntry> groups
	  ) {
	    if (layerId == null || layerId.isBlank() || groups == null || groups.isEmpty()) return List.of();
	    PuppeteerLauncherPanel.CharacterLayerGroupEntry deepest = null;
	    int deepestDepth = -1;
	    for (PuppeteerLauncherPanel.CharacterLayerGroupEntry candidate : groups) {
	      if (candidate == null || !candidate.layerIds.contains(layerId)) continue;
	      int depth = snapshotLayerGroupDepth(candidate, groups, new LinkedHashSet<>());
	      if (depth > deepestDepth) {
	        deepest = candidate;
	        deepestDepth = depth;
	      }
	    }
	    if (deepest == null) return List.of();

	    LinkedHashSet<String> seen = new LinkedHashSet<>();
	    List<PuppeteerLauncherPanel.CharacterLayerGroupEntry> chain = new ArrayList<>();
	    PuppeteerLauncherPanel.CharacterLayerGroupEntry current = deepest;
	    while (current != null && current.groupId != null && seen.add(current.groupId)) {
	      chain.add(0, current);
	      current = snapshotLayerGroupById(groups, current.parentGroupId);
	    }
	    return List.copyOf(chain);
	  }

	  private int snapshotLayerGroupDepth(
	      PuppeteerLauncherPanel.CharacterLayerGroupEntry group,
	      List<PuppeteerLauncherPanel.CharacterLayerGroupEntry> groups,
	      Set<String> seen
	  ) {
	    if (group == null || group.groupId == null || !seen.add(group.groupId)) return 0;
	    PuppeteerLauncherPanel.CharacterLayerGroupEntry parent =
	        snapshotLayerGroupById(groups, group.parentGroupId);
	    return parent == null ? 0 : 1 + snapshotLayerGroupDepth(parent, groups, seen);
	  }

	  private PuppeteerLauncherPanel.CharacterLayerGroupEntry snapshotLayerGroupById(
	      List<PuppeteerLauncherPanel.CharacterLayerGroupEntry> groups,
	      String groupId
	  ) {
	    if (groupId == null || groupId.isBlank() || groups == null) return null;
	    for (PuppeteerLauncherPanel.CharacterLayerGroupEntry group : groups) {
	      if (group != null && groupId.equals(group.groupId)) return group;
	    }
	    return null;
	  }

	  private void applySnapshotGroupPivot(
	      Entity2D entity,
	      PuppeteerLauncherPanel.CharacterLayerGroupEntry group,
	      EntityTrack track
	  ) {
	    if (entity == null || group == null || !group.hasPivot || track == null) return;
	    if (track.hasKeyframes(PropertyType.PIVOT_X) || track.hasKeyframes(PropertyType.PIVOT_Y)) return;
	    double originX = group.pivotX;
	    double originY = group.pivotY;
	    if (Math.abs(originX - entity.getOriginX()) <= 1e-9
	        && Math.abs(originY - entity.getOriginY()) <= 1e-9) {
	      return;
	    }
	    double[] size = entityVisualSize(entity);
	    entity.setPosition(
	        entity.getX() + ((originX - entity.getOriginX()) * size[0]),
	        entity.getY() + ((originY - entity.getOriginY()) * size[1]));
	    entity.setOrigin(originX, originY);
	  }

	  private void applyTimelineTrackFrame(Entity2D entity, EntityTrack track, double timeMs) {
	    if (entity == null || track == null) return;
	    boolean hasX = track.hasKeyframes(PropertyType.X);
	    boolean hasY = track.hasKeyframes(PropertyType.Y);
	    boolean hasPivotX = track.hasKeyframes(PropertyType.PIVOT_X);
	    boolean hasPivotY = track.hasKeyframes(PropertyType.PIVOT_Y);
	    double x = hasX ? track.getValueAt(PropertyType.X, timeMs) : entity.getX();
	    double y = hasY ? track.getValueAt(PropertyType.Y, timeMs) : entity.getY();
	    if (track.hasKeyframes(PropertyType.Z)) {
	      entity.setZ(track.getValueAt(PropertyType.Z, timeMs));
	    }
	    double ox = hasPivotX
	        ? track.getValueAt(PropertyType.PIVOT_X, timeMs)
	        : entity.getOriginX();
	    double oy = hasPivotY
	        ? track.getValueAt(PropertyType.PIVOT_Y, timeMs)
	        : entity.getOriginY();
	    if (hasPivotX || hasPivotY) {
	      double[] size = entityVisualSize(entity);
	      if (!hasX) x += (ox - entity.getOriginX()) * size[0];
	      if (!hasY) y += (oy - entity.getOriginY()) * size[1];
	    }
	    if (hasX || hasY || hasPivotX || hasPivotY) {
	      entity.setPosition(x, y);
	    }
	    if (hasPivotX || hasPivotY) {
	      entity.setOrigin(ox, oy);
	    }
	    if (track.hasKeyframes(PropertyType.ROTATION)) {
	      entity.setRotationDeg(track.getValueAt(PropertyType.ROTATION, timeMs));
	    }
	    double sx = track.hasKeyframes(PropertyType.SCALE_X)
	        ? track.getValueAt(PropertyType.SCALE_X, timeMs)
	        : entity.getScaleX();
	    if (track.hasKeyframes(PropertyType.MIRROR_X)) {
	      sx *= mirrorFactor(track.getValueAt(PropertyType.MIRROR_X, timeMs));
	    }
	    double sy = track.hasKeyframes(PropertyType.SCALE_Y)
	        ? track.getValueAt(PropertyType.SCALE_Y, timeMs)
	        : entity.getScaleY();
	    if (track.hasKeyframes(PropertyType.SCALE_X)
	        || track.hasKeyframes(PropertyType.SCALE_Y)
	        || track.hasKeyframes(PropertyType.MIRROR_X)) {
	      entity.setScale(sx, sy);
	    }
	    if (track.hasKeyframes(PropertyType.VISIBILITY)) {
	      entity.setVisible(track.getValueAt(PropertyType.VISIBILITY, timeMs) >= 0.5);
	    }
	    if (track.hasKeyframes(PropertyType.ALPHA)) {
	      applyEntityAlpha(entity, track.getValueAt(PropertyType.ALPHA, timeMs));
	    }
	    applyTimelineFrameEffects(entity, track, timeMs);
	  }

	  private void applySnapshotCharacterTimelineStates(
	      JesScene2D scene,
	      PuppeteerLauncherPanel.SceneSnapshot snapshot,
	      Map<String, SnapshotCharacterTimelineState> characterStates
	  ) {
	    if (scene == null || snapshot == null || characterStates == null || characterStates.isEmpty()) return;
	    for (PuppeteerLauncherPanel.CharacterEntry character : snapshot.characters) {
	      if (character == null || character.characterId == null || character.characterId.isBlank()) continue;
	      SnapshotCharacterTimelineState state = characterStates.get(character.characterId);
	      if (state == null) continue;
	      for (String entityName : currentSnapshotEntityNames(snapshot, character)) {
	        Entity2D entity = scene.find(entityName);
	        if (entity == null) continue;
	        if (state.shouldApplyX() || state.shouldApplyY()) {
	          entity.setPosition(
	              state.shouldApplyX() ? state.x : entity.getX(),
	              state.shouldApplyY() ? state.y : entity.getY());
	        }
	        if (state.shouldApplyPivotX() || state.shouldApplyPivotY()) {
	          entity.setOrigin(
	              state.shouldApplyPivotX() ? state.pivotX : entity.getOriginX(),
	              state.shouldApplyPivotY() ? state.pivotY : entity.getOriginY());
	        }
	        if (state.shouldApplyRotation()) {
	          entity.setRotationDeg(state.rotation);
	        }
	        if (state.shouldApplyScaleX() || state.shouldApplyScaleY()) {
	          entity.setScale(
	              state.shouldApplyScaleX() ? state.scaleX : entity.getScaleX(),
	              state.shouldApplyScaleY() ? state.scaleY : entity.getScaleY());
	        }
	      }
	    }
	  }

	  private Set<String> currentSnapshotEntityNames(PuppeteerLauncherPanel.SceneSnapshot snapshot) {
	    Set<String> names = new LinkedHashSet<>();
	    if (snapshot == null) return names;
	    for (PuppeteerLauncherPanel.CharacterEntry character : snapshot.characters) {
	      names.addAll(currentSnapshotEntityNames(snapshot, character));
	    }
	    return names;
	  }

	  private List<String> currentSnapshotEntityNames(
	      PuppeteerLauncherPanel.SceneSnapshot snapshot,
	      PuppeteerLauncherPanel.CharacterEntry character
	  ) {
	    if (snapshot == null || character == null) return List.of();
	    List<PuppeteerLauncherPanel.CharacterLayerEntry> layers =
	        snapshot.resolveCharacterLayers(character.characterId, character.expression);
	    if (layers.isEmpty()) return List.of(character.characterId);
	    LinkedHashSet<String> names = new LinkedHashSet<>();
	    for (PuppeteerLauncherPanel.CharacterLayerEntry layer : layers) {
	      if (layer == null || layer.path == null || layer.path.isBlank()) continue;
	      names.addAll(PuppeteerLauncherPanel.equivalentSnapshotLayerEntityNames(snapshot, character, layer.layerId));
	    }
	    for (PuppeteerLauncherPanel.CharacterLayerGroupEntry group :
	        snapshot.resolveCharacterLayerGroups(character.characterId, character.expression)) {
	      if (group == null || group.groupId == null || group.groupId.isBlank()) continue;
	      names.addAll(PuppeteerLauncherPanel.equivalentSnapshotLayerGroupEntityNames(snapshot, character, group.groupId));
	    }
	    return new ArrayList<>(names);
	  }

	  private void hideReplayOnlyEntities(
	      JesScene2D scene,
	      AnimationProject stateTimeline,
	      Set<String> snapshotEntities
	  ) {
	    if (scene == null || stateTimeline == null || snapshotEntities == null) return;
	    for (EntityTrack track : stateTimeline.getTracks()) {
	      if (track == null || isCameraTrack(track)) continue;
	      String entityName = track.getEntityName();
	      if (entityName == null || entityName.isBlank() || snapshotEntities.contains(entityName)) continue;
	      Entity2D entity = scene.find(entityName);
	      if (entity != null) entity.setVisible(false);
	    }
	  }

	  private Entity2D firstSnapshotEntity(
	      JesScene2D scene,
	      PuppeteerLauncherPanel.SceneSnapshot snapshot,
	      PuppeteerLauncherPanel.CharacterEntry snapshotEntry
	  ) {
	    if (scene == null || snapshot == null || snapshotEntry == null) return null;
	    for (String entityName : currentSnapshotEntityNames(snapshot, snapshotEntry)) {
	      Entity2D entity = scene.find(entityName);
	      if (entity != null) return entity;
	    }
	    return null;
	  }

	  private String resolveSnapshotTrackCharacter(
	      String entityName,
	      PuppeteerLauncherPanel.SceneSnapshot snapshot
	  ) {
	    if (entityName == null || entityName.isBlank() || snapshot == null || snapshot.characters.isEmpty()) {
	      return null;
	    }
	    String target = entityName.trim();
	    List<PuppeteerLauncherPanel.CharacterEntry> characters = new ArrayList<>(snapshot.characters);
	    characters.sort((a, b) -> Integer.compare(
	        selectorSafeName(b == null ? "" : b.characterId).length(),
	        selectorSafeName(a == null ? "" : a.characterId).length()));
	    for (PuppeteerLauncherPanel.CharacterEntry character : characters) {
	      if (character == null || character.characterId == null || character.characterId.isBlank()) continue;
	      String characterId = character.characterId.trim();
	      String safeCharacter = selectorSafeName(characterId);
	      if (target.equals(characterId) || target.equals(safeCharacter)) return characterId;
	      if (!safeCharacter.isBlank() && target.startsWith(safeCharacter + "_")) return characterId;
	    }
	    return null;
	  }

	  private static double mirrorFactor(double mirrorX) {
	    if (!Double.isFinite(mirrorX)) return 1.0;
	    double clamped = Math.max(0.0, Math.min(1.0, mirrorX));
	    return Math.cos(clamped * Math.PI);
	  }

	  private static final class SnapshotCharacterTimelineState {
	    private double x;
	    private double y;
	    private double scaleX = 1.0;
	    private double scaleY = 1.0;
	    private double rotation;
	    private double pivotX = 0.5;
	    private double pivotY = 1.0;
	    private int xCount;
	    private int yCount;
	    private int scaleXCount;
	    private int scaleYCount;
	    private int rotationCount;
	    private int pivotXCount;
	    private int pivotYCount;
	    private boolean xConsistent = true;
	    private boolean yConsistent = true;
	    private boolean scaleXConsistent = true;
	    private boolean scaleYConsistent = true;
	    private boolean rotationConsistent = true;
	    private boolean pivotXConsistent = true;
	    private boolean pivotYConsistent = true;
	    private boolean characterX;
	    private boolean characterY;
	    private boolean characterScaleX;
	    private boolean characterScaleY;
	    private boolean characterRotation;
	    private boolean characterPivotX;
	    private boolean characterPivotY;

	    private void accept(EntityTrack track, double timeMs, boolean characterTrack) {
	      if (track == null) return;
	      acceptX(track.hasKeyframes(PropertyType.X), track.hasKeyframes(PropertyType.X)
	          ? track.getValueAt(PropertyType.X, timeMs)
	          : 0.0, characterTrack);
	      acceptY(track.hasKeyframes(PropertyType.Y), track.hasKeyframes(PropertyType.Y)
	          ? track.getValueAt(PropertyType.Y, timeMs)
	          : 0.0, characterTrack);

	      boolean hasScaleX = track.hasKeyframes(PropertyType.SCALE_X) || track.hasKeyframes(PropertyType.MIRROR_X);
	      double nextScaleX = track.hasKeyframes(PropertyType.SCALE_X)
	          ? track.getValueAt(PropertyType.SCALE_X, timeMs)
	          : 1.0;
	      if (track.hasKeyframes(PropertyType.MIRROR_X)) {
	        nextScaleX *= mirrorFactor(track.getValueAt(PropertyType.MIRROR_X, timeMs));
	      }
	      acceptScaleX(hasScaleX, nextScaleX, characterTrack);
	      acceptScaleY(track.hasKeyframes(PropertyType.SCALE_Y), track.hasKeyframes(PropertyType.SCALE_Y)
	          ? track.getValueAt(PropertyType.SCALE_Y, timeMs)
	          : 1.0, characterTrack);
	      acceptRotation(track.hasKeyframes(PropertyType.ROTATION), track.hasKeyframes(PropertyType.ROTATION)
	          ? track.getValueAt(PropertyType.ROTATION, timeMs)
	          : 0.0, characterTrack);
	      acceptPivotX(track.hasKeyframes(PropertyType.PIVOT_X), track.hasKeyframes(PropertyType.PIVOT_X)
	          ? track.getValueAt(PropertyType.PIVOT_X, timeMs)
	          : 0.5, characterTrack);
	      acceptPivotY(track.hasKeyframes(PropertyType.PIVOT_Y), track.hasKeyframes(PropertyType.PIVOT_Y)
	          ? track.getValueAt(PropertyType.PIVOT_Y, timeMs)
	          : 1.0, characterTrack);
	    }

	    private void acceptX(boolean hasValue, double value, boolean characterTrack) {
	      if (!hasValue || !Double.isFinite(value)) return;
	      if (xCount > 0 && Math.abs(value - x) > 0.01) xConsistent = false;
	      x = value;
	      xCount++;
	      characterX |= characterTrack;
	    }

	    private void acceptY(boolean hasValue, double value, boolean characterTrack) {
	      if (!hasValue || !Double.isFinite(value)) return;
	      if (yCount > 0 && Math.abs(value - y) > 0.01) yConsistent = false;
	      y = value;
	      yCount++;
	      characterY |= characterTrack;
	    }

	    private void acceptScaleX(boolean hasValue, double value, boolean characterTrack) {
	      if (!hasValue || !Double.isFinite(value)) return;
	      if (scaleXCount > 0 && Math.abs(value - scaleX) > 0.0001) scaleXConsistent = false;
	      scaleX = value;
	      scaleXCount++;
	      characterScaleX |= characterTrack;
	    }

	    private void acceptScaleY(boolean hasValue, double value, boolean characterTrack) {
	      if (!hasValue || !Double.isFinite(value)) return;
	      if (scaleYCount > 0 && Math.abs(value - scaleY) > 0.0001) scaleYConsistent = false;
	      scaleY = value;
	      scaleYCount++;
	      characterScaleY |= characterTrack;
	    }

	    private void acceptRotation(boolean hasValue, double value, boolean characterTrack) {
	      if (!hasValue || !Double.isFinite(value)) return;
	      if (rotationCount > 0 && Math.abs(value - rotation) > 0.0001) rotationConsistent = false;
	      rotation = value;
	      rotationCount++;
	      characterRotation |= characterTrack;
	    }

	    private void acceptPivotX(boolean hasValue, double value, boolean characterTrack) {
	      if (!hasValue || !Double.isFinite(value)) return;
	      if (pivotXCount > 0 && Math.abs(value - pivotX) > 0.0001) pivotXConsistent = false;
	      pivotX = value;
	      pivotXCount++;
	      characterPivotX |= characterTrack;
	    }

	    private void acceptPivotY(boolean hasValue, double value, boolean characterTrack) {
	      if (!hasValue || !Double.isFinite(value)) return;
	      if (pivotYCount > 0 && Math.abs(value - pivotY) > 0.0001) pivotYConsistent = false;
	      pivotY = value;
	      pivotYCount++;
	      characterPivotY |= characterTrack;
	    }

	    private boolean shouldApplyX() {
	      return characterX || (xCount > 1 && xConsistent);
	    }

	    private boolean shouldApplyY() {
	      return characterY || (yCount > 1 && yConsistent);
	    }

	    private boolean shouldApplyScaleX() {
	      return characterScaleX || (scaleXCount > 1 && scaleXConsistent);
	    }

	    private boolean shouldApplyScaleY() {
	      return characterScaleY || (scaleYCount > 1 && scaleYConsistent);
	    }

	    private boolean shouldApplyRotation() {
	      return characterRotation || (rotationCount > 1 && rotationConsistent);
	    }

	    private boolean shouldApplyPivotX() {
	      return characterPivotX || (pivotXCount > 1 && pivotXConsistent);
	    }

	    private boolean shouldApplyPivotY() {
	      return characterPivotY || (pivotYCount > 1 && pivotYConsistent);
	    }
	  }

	  private record SnapshotLayerGroupTarget(
	      PuppeteerLauncherPanel.CharacterLayerGroupEntry group,
	      List<Entity2D> members
	  ) {
	  }

	  private void applyTimelineFrameEffects(Entity2D entity, EntityTrack track, double timeMs) {
    if (entity == null || track == null) return;
    if (track.hasKeyframes(PropertyType.MATRIX_MXX)
        || track.hasKeyframes(PropertyType.MATRIX_MXY)
        || track.hasKeyframes(PropertyType.MATRIX_MYX)
        || track.hasKeyframes(PropertyType.MATRIX_MYY)
        || track.hasKeyframes(PropertyType.MATRIX_TX)
        || track.hasKeyframes(PropertyType.MATRIX_TY)) {
      entity.setSupplementalTransform(
          track.hasKeyframes(PropertyType.MATRIX_MXX) ? track.getValueAt(PropertyType.MATRIX_MXX, timeMs) : entity.getMatrixMxx(),
          track.hasKeyframes(PropertyType.MATRIX_MXY) ? track.getValueAt(PropertyType.MATRIX_MXY, timeMs) : entity.getMatrixMxy(),
          track.hasKeyframes(PropertyType.MATRIX_MYX) ? track.getValueAt(PropertyType.MATRIX_MYX, timeMs) : entity.getMatrixMyx(),
          track.hasKeyframes(PropertyType.MATRIX_MYY) ? track.getValueAt(PropertyType.MATRIX_MYY, timeMs) : entity.getMatrixMyy(),
          track.hasKeyframes(PropertyType.MATRIX_TX) ? track.getValueAt(PropertyType.MATRIX_TX, timeMs) : entity.getMatrixTx(),
          track.hasKeyframes(PropertyType.MATRIX_TY) ? track.getValueAt(PropertyType.MATRIX_TY, timeMs) : entity.getMatrixTy());
    }
    if (track.hasKeyframes(PropertyType.BLUR)) {
      entity.setBlurRadius(track.getValueAt(PropertyType.BLUR, timeMs));
    }
    if (track.hasKeyframes(PropertyType.BRIGHTNESS)) {
      entity.setBrightness(track.getValueAt(PropertyType.BRIGHTNESS, timeMs));
    }
  }

  private void applyEntityAlpha(Entity2D entity, double alpha) {
    double clamped = Math.max(0.0, Math.min(1.0, alpha));
    if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
      sprite.setAlpha(clamped);
    } else if (entity instanceof com.jvn.core.scene2d.Panel2D panel) {
      panel.setFill(panel.getFillR(), panel.getFillG(), panel.getFillB(), clamped);
    } else if (entity instanceof com.jvn.core.scene2d.Label2D label) {
      label.setColor(label.getColorR(), label.getColorG(), label.getColorB(), clamped);
    }
  }

  private double entityAlpha(Entity2D entity) {
    if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) return sprite.getAlpha();
    if (entity instanceof com.jvn.core.scene2d.Label2D label) return label.getAlpha();
    if (entity instanceof com.jvn.core.scene2d.Panel2D panel) return panel.getFillA();
    return 1.0;
  }

  private static double[] entityVisualSize(Entity2D entity) {
    if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
      return new double[] { Math.max(1.0, sprite.getWidth()), Math.max(1.0, sprite.getHeight()) };
    }
    if (entity instanceof com.jvn.core.scene2d.Panel2D panel) {
      return new double[] { Math.max(1.0, panel.getWidth()), Math.max(1.0, panel.getHeight()) };
    }
    return new double[] { 1.0, 1.0 };
  }

  private void addMissingSceneEntity(
      JesScene2D scene,
      AnimationProject imported,
      EntityTrack track,
      PuppeteerLauncherPanel.SceneSnapshot snapshot,
      int missingIndex,
      int missingCount
  ) {
    if (scene == null || track == null) return;
    String entityName = track.getEntityName();
    if (entityName == null || entityName.isBlank()) return;

    ProjectViewportSpec.Dimensions viewport = ProjectViewportSpec.resolve(projectRoot);
    double sceneW = Math.max(1.0, viewport.width());
    double sceneH = Math.max(1.0, viewport.height());
    double characterHeight = sceneH * 0.85;
    double fallbackWidth = sceneW * 0.16;
    double fallbackHeight = sceneH * 0.62;

    PuppeteerLauncherPanel.CharacterEntry snapshotEntry = null;
    if (snapshot != null) {
      String snapshotCharacterId = resolveSnapshotTrackCharacter(entityName, snapshot);
      for (PuppeteerLauncherPanel.CharacterEntry entry : snapshot.characters) {
        if (entry != null
            && (entityName.equals(entry.characterId)
                || (snapshotCharacterId != null && snapshotCharacterId.equals(entry.characterId)))) {
          snapshotEntry = entry;
          break;
        }
      }
    }

    Entity2D snapshotAnchorEntity = firstSnapshotEntity(scene, snapshot, snapshotEntry);
    if (snapshotEntry != null) {
      characterHeight *= snapshotEntry.scale;
    }
    boolean expressionLayerOutsideSnapshot = snapshotEntry != null
        && !isVisibleSnapshotTarget(snapshot, snapshotEntry, entityName);
    String expression = snapshotEntry != null ? snapshotEntry.expression : "neutral";
    String position = snapshotEntry != null ? snapshotEntry.position : fallbackTrackPosition(missingIndex, missingCount);
    String spritePathSpec = "";
    if (snapshot != null && snapshot.hasCharacterPathMapping(entityName, expression)) {
      spritePathSpec = resolveProjectPathSpec(snapshot.resolveCharacterPath(entityName, expression));
    } else if (snapshot != null && snapshot.hasCharacterPathMapping(entityName, "neutral")) {
      spritePathSpec = resolveProjectPathSpec(snapshot.resolveCharacterPath(entityName, "neutral"));
    }

    AnimationProject.SceneEntitySnapshot metadata = imported != null
        ? imported.getSceneEntitySnapshot(entityName)
        : null;
    if (spritePathSpec.isBlank() && metadata != null && !metadata.imagePath().isBlank()) {
      spritePathSpec = resolveProjectPathSpec(metadata.imagePath());
    }

    boolean hasTrackX = track.hasKeyframes(PropertyType.X);
    boolean hasTrackY = track.hasKeyframes(PropertyType.Y);
    double fallbackLeftX = positionToLeftX(position, sceneW, fallbackWidth);
    double fallbackCenterX = fallbackLeftX + (fallbackWidth * 0.5);
    double fallbackBottomY = sceneH;
    double x = metadata != null
        ? metadata.x()
        : (snapshotAnchorEntity != null
            ? snapshotAnchorEntity.getX()
            : (snapshotEntry != null ? fallbackCenterX : (hasTrackX ? track.getValueAt(PropertyType.X, 0.0) : fallbackCenterX)));
    double y = metadata != null
        ? metadata.y()
        : (snapshotAnchorEntity != null
            ? snapshotAnchorEntity.getY()
            : (snapshotEntry != null ? fallbackBottomY : (hasTrackY ? track.getValueAt(PropertyType.Y, 0.0) : fallbackBottomY)));
    double originX = metadata != null
        ? metadata.originX()
        : (snapshotAnchorEntity != null ? snapshotAnchorEntity.getOriginX() : 0.5);
    double originY = metadata != null
        ? metadata.originY()
        : (snapshotAnchorEntity != null ? snapshotAnchorEntity.getOriginY() : 1.0);

    if (!spritePathSpec.isBlank()) {
      double[] spriteSize = metadata != null && metadata.width() > 1.0 && metadata.height() > 1.0
          ? new double[] { metadata.width(), metadata.height() }
          : estimateSpriteSize(firstLayerPath(spritePathSpec), characterHeight);
      double charW = spriteSize[0];
      double charH = spriteSize[1];
      if (!hasTrackX && metadata == null) {
        x = positionToLeftX(position, sceneW, charW) + (charW * 0.5);
      }
      if (!hasTrackY && metadata == null) {
        y = sceneH;
      }
      com.jvn.core.scene2d.Sprite2D sprite = new com.jvn.core.scene2d.Sprite2D(spritePathSpec, charW, charH);
      sprite.setOrigin(originX, originY);
      sprite.setPosition(x, y);
      sprite.setZ(metadata != null ? metadata.z() : track.getLayerOrder());
      sprite.setVisible(!expressionLayerOutsideSnapshot && (metadata == null || metadata.visible()));
      if (metadata != null) sprite.setAlpha(metadata.alpha());
      scene.add(sprite);
      scene.registerEntity(entityName, sprite);
      return;
    }

    com.jvn.core.scene2d.Panel2D placeholder = new com.jvn.core.scene2d.Panel2D(
        metadata != null ? metadata.width() : fallbackWidth,
        metadata != null ? metadata.height() : fallbackHeight);
    placeholder.setOrigin(originX, originY);
    placeholder.setFill(0.23, 0.30, 0.40, 0.22);
    placeholder.setStroke(0.56, 0.76, 1.0, 0.88, 3.0);
    placeholder.setPosition(x, y);
    placeholder.setZ(metadata != null ? metadata.z() : track.getLayerOrder());
    placeholder.setVisible(!expressionLayerOutsideSnapshot && (metadata == null || metadata.visible()));
    scene.add(placeholder);
    scene.registerEntity(entityName, placeholder);
  }

  private boolean isVisibleSnapshotTarget(
      PuppeteerLauncherPanel.SceneSnapshot snapshot,
      PuppeteerLauncherPanel.CharacterEntry snapshotEntry,
      String entityName
  ) {
    if (snapshot == null || snapshotEntry == null || entityName == null || entityName.isBlank()) return true;
    if (entityName.equals(snapshotEntry.characterId)) return true;
    return currentSnapshotEntityNames(snapshot, snapshotEntry).contains(entityName);
  }

  private JesScene2D buildSceneFromSnapshot(PuppeteerLauncherPanel.SceneSnapshot snapshot) {
    JesScene2D scene = new JesScene2D();
    ProjectViewportSpec.Dimensions viewport = ProjectViewportSpec.resolve(projectRoot);
    double sceneW = Math.max(1.0, viewport.width());
    double sceneH = Math.max(1.0, viewport.height());
    PuppeteerCharacterFraming framing = resolvePuppeteerCharacterFraming();
    double characterHeight = sceneH * framing.heightFactor();

    if (snapshot.previousBackgroundId != null) {
      String prevBgPath = resolveProjectPathSpec(snapshot.resolvePreviousBackgroundPath());
      com.jvn.core.scene2d.Sprite2D prevBg = new com.jvn.core.scene2d.Sprite2D(prevBgPath, sceneW, sceneH);
      prevBg.setOrigin(0.0, 0.0);
      prevBg.setPosition(0.0, 0.0);
      prevBg.setZ(PUPPETEER_PREVIOUS_BACKGROUND_Z);
      scene.add(prevBg);
      scene.registerEntity("bg_prev", prevBg);
    }

    if (snapshot.backgroundId != null) {
      String bgPath = resolveProjectPathSpec(snapshot.resolveBackgroundPath());
      com.jvn.core.scene2d.Sprite2D bg = new com.jvn.core.scene2d.Sprite2D(bgPath, sceneW, sceneH);
      bg.setOrigin(0.0, 0.0);
      bg.setPosition(0.0, 0.0);
      bg.setZ(PUPPETEER_CURRENT_BACKGROUND_Z);
      scene.add(bg);
      scene.registerEntity("bg_" + snapshot.backgroundId, bg);
      scene.registerEntity("bg_current", bg);
    }

    for (PuppeteerLauncherPanel.CharacterEntry ch : snapshot.characters) {
      List<PuppeteerLauncherPanel.CharacterLayerEntry> layers = snapshot.resolveCharacterLayers(ch.characterId, ch.expression);
      if (!layers.isEmpty()) {
        addLayeredSnapshotCharacter(
            scene, snapshot, ch, layers, sceneW, sceneH,
            characterHeight * ch.scale, framing.baselineY());
        continue;
      }
      String spritePathSpec = resolveProjectPathSpec(snapshot.resolveCharacterPath(ch.characterId, ch.expression));
      SnapshotSpriteLayout spriteLayout = estimateSnapshotSpriteLayout(
          firstLayerPath(spritePathSpec), characterHeight * ch.scale,
          sceneW, sceneH, framing.baselineY());
      double[] spriteSize = spriteLayout.size();
      double charW = spriteSize[0];
      double charH = spriteSize[1];
      double leftX = snapshotPositionToLeftX(ch, sceneW, charW);
      double bottomY = snapshotPositionBottomY(ch, sceneH, spriteLayout.baselineY());
      com.jvn.core.scene2d.Sprite2D sprite = new com.jvn.core.scene2d.Sprite2D(spritePathSpec, charW, charH);
      // Character-friendly pivot for puppeteering: bottom-center (feet/contact point).
      sprite.setOrigin(0.5, 1.0);
      // Keep visual placement equivalent to prior top-left anchoring.
      sprite.setPosition(leftX + (charW * 0.5), bottomY);
      if (ch.layerOrder != null) sprite.setZ(ch.layerOrder);
      scene.add(sprite);
      scene.registerEntity(ch.characterId, sprite);
    }

    return scene;
  }

  private void addLayeredSnapshotCharacter(
      JesScene2D scene,
      PuppeteerLauncherPanel.SceneSnapshot snapshot,
      PuppeteerLauncherPanel.CharacterEntry ch,
      List<PuppeteerLauncherPanel.CharacterLayerEntry> layers,
      double sceneW,
      double sceneH,
      double characterHeight,
      double characterBaselineY
  ) {
    if (scene == null || ch == null || layers == null || layers.isEmpty()) return;
    double charW = 1.0;
    double charH = 1.0;
    double baselineY = characterBaselineY;
    List<String> resolvedPaths = new ArrayList<>();
    for (PuppeteerLauncherPanel.CharacterLayerEntry layer : layers) {
      if (layer == null || layer.path == null || layer.path.isBlank()) continue;
      String resolvedPath = resolveProjectPath(layer.path);
      resolvedPaths.add(resolvedPath);
      SnapshotSpriteLayout layout = estimateSnapshotSpriteLayout(
          resolvedPath, characterHeight, sceneW, sceneH, characterBaselineY);
      double[] size = layout.size();
      charW = Math.max(charW, size[0]);
      charH = Math.max(charH, size[1]);
      if (layout.canvasAligned()) baselineY = 1.0;
    }
    if (resolvedPaths.isEmpty()) return;
    double leftX = snapshotPositionToLeftX(ch, sceneW, charW);
    double bottomY = snapshotPositionBottomY(ch, sceneH, baselineY);
    double baseZ = ch.layerOrder == null ? 0.0 : ch.layerOrder;
    int layerIndex = 0;
    Map<String, Entity2D> visibleEntitiesByLayerId = new LinkedHashMap<>();
    for (PuppeteerLauncherPanel.CharacterLayerEntry layer : layers) {
      if (layer == null || layer.path == null || layer.path.isBlank()) continue;
      String resolvedPath = resolveProjectPath(layer.path);
      String entityName = PuppeteerLauncherPanel.snapshotStableLayerEntityName(ch.characterId, layer.layerId);
      while (scene.find(entityName) != null) {
        entityName = entityName + "_" + (layerIndex + 2);
      }
      com.jvn.core.scene2d.Sprite2D sprite = new com.jvn.core.scene2d.Sprite2D(resolvedPath, charW, charH);
      sprite.setOrigin(0.5, 1.0);
      sprite.setPosition(leftX + (charW * 0.5), bottomY);
      sprite.setZ(baseZ + (layerIndex * 0.001));
      scene.add(sprite);
      scene.registerEntity(entityName, sprite);
      for (String alias : PuppeteerLauncherPanel.equivalentSnapshotLayerEntityNames(snapshot, ch, layer.layerId)) {
        if (alias == null || alias.isBlank() || alias.equals(entityName)) continue;
        scene.registerEntityAlias(alias, sprite);
      }
      visibleEntitiesByLayerId.putIfAbsent(layer.layerId, sprite);
      layerIndex++;
    }
    for (PuppeteerLauncherPanel.CharacterLayerGroupEntry group :
        snapshot.resolveCharacterLayerGroups(ch.characterId, ch.expression)) {
      if (group == null) continue;
      Entity2D anchor = null;
      for (String layerId : group.layerIds) {
        anchor = visibleEntitiesByLayerId.get(layerId);
        if (anchor != null) break;
      }
      if (anchor == null) continue;
      for (String alias : PuppeteerLauncherPanel.equivalentSnapshotLayerGroupEntityNames(
          snapshot, ch, group.groupId)) {
        if (alias != null && !alias.isBlank()) scene.registerEntityAlias(alias, anchor);
      }
    }
  }

  private boolean isCameraTrack(EntityTrack track) {
    if (track == null) return true;
    String entityName = track.getEntityName();
    if ("__camera__".equals(entityName)) return true;
    boolean hasAnimatedProperties = false;
    for (PropertyType property : track.getAnimatedProperties()) {
      hasAnimatedProperties = true;
      if (property != PropertyType.CAMERA_X
          && property != PropertyType.CAMERA_Y
          && property != PropertyType.CAMERA_ZOOM) {
        return false;
      }
    }
    return hasAnimatedProperties;
  }

  private String fallbackTrackPosition(int index, int total) {
    if (total <= 1) return "center";
    return switch (Math.max(0, index) % 5) {
      case 0 -> "left";
      case 1 -> "center";
      case 2 -> "right";
      case 3 -> "far_left";
      default -> "far_right";
    };
  }

  private double positionToLeftX(String position, double sceneW, double spriteW) {
    if (position == null) return (sceneW - spriteW) * 0.5;
    return switch (position) {
      case "far_left"  -> sceneW * 0.05;
      case "left"      -> sceneW * 0.20;
      case "center"    -> (sceneW - spriteW) * 0.5;
      case "right"     -> sceneW * 0.80 - spriteW;
      case "far_right" -> sceneW * 0.95 - spriteW;
      default          -> (sceneW - spriteW) * 0.5;
    };
  }

  private double snapshotPositionToLeftX(
      PuppeteerLauncherPanel.CharacterEntry character, double sceneW, double spriteW) {
    if (character != null && character.customPosition && Double.isFinite(character.positionX)) {
      return (sceneW * character.positionX) - (spriteW * 0.5);
    }
    return positionToLeftX(character == null ? null : character.position, sceneW, spriteW);
  }

  private double snapshotPositionBottomY(
      PuppeteerLauncherPanel.CharacterEntry character, double sceneH, double baselineY) {
    if (character != null && character.customPosition
        && Double.isFinite(character.positionY) && character.positionY >= 0.0) {
      return sceneH * character.positionY;
    }
    return sceneH * baselineY;
  }

  private PuppeteerCharacterFraming resolvePuppeteerCharacterFraming() {
    double heightFactor = 0.85;
    double baselineY = 1.0;
    if (projectRoot == null || !projectRoot.isDirectory()) {
      return new PuppeteerCharacterFraming(heightFactor, baselineY);
    }
    try {
      VnUiStyleSpec style = VnUiLayoutLoader.loadFromProjectRootWithDiagnostics(projectRoot).style();
      if (style != null) {
        if (style.characterHeightFactor() != null) {
          heightFactor = Math.max(0.1, Math.min(3.0, style.characterHeightFactor()));
        }
        if (style.characterBaselineY() != null) {
          baselineY = Math.max(-0.5, Math.min(2.0, style.characterBaselineY()));
        }
      }
    } catch (Exception ignored) {
      // Snapshot construction retains runtime defaults if a project's optional UI layout is bad.
    }
    return new PuppeteerCharacterFraming(heightFactor, baselineY);
  }

  private SnapshotSpriteLayout estimateSnapshotSpriteLayout(
      String spritePath,
      double targetHeight,
      double viewportWidth,
      double viewportHeight,
      double baselineY
  ) {
    double height = Math.max(1.0, targetHeight);
    double width = height * 0.5;
    boolean canvasAligned = false;
    if (spritePath != null && !spritePath.isBlank()) {
      try {
        javafx.scene.image.Image image = new javafx.scene.image.Image(
            new java.io.File(spritePath).toURI().toString(), 0, 0, true, false);
        if (image.getWidth() > 0.0 && image.getHeight() > 0.0) {
          double imageAspect = image.getWidth() / image.getHeight();
          double viewportAspect = viewportWidth / Math.max(1.0, viewportHeight);
          canvasAligned = Math.abs(imageAspect - viewportAspect)
              <= Math.max(1e-6, viewportAspect * 0.001);
          height = canvasAligned ? Math.max(1.0, viewportHeight) : height;
          width = image.getWidth() * (height / image.getHeight());
        }
      } catch (Exception ignored) {
        // Keep the deterministic fallback size for missing or unreadable source art.
      }
    }
    return new SnapshotSpriteLayout(
        new double[] { width, height }, canvasAligned ? 1.0 : baselineY, canvasAligned);
  }

  private double[] estimateSpriteSize(String spritePath, double targetHeight) {
    double height = Math.max(1.0, targetHeight);
    double width = height * 0.5;
    if (spritePath == null || spritePath.isBlank()) return new double[] { width, height };
    try {
      javafx.scene.image.Image image = new javafx.scene.image.Image(
          new java.io.File(spritePath).toURI().toString(), 0, 0, true, false);
      if (image.getWidth() > 0.0 && image.getHeight() > 0.0) {
        width = image.getWidth() * (height / image.getHeight());
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return new double[] { width, height };
  }

  private record PuppeteerCharacterFraming(double heightFactor, double baselineY) {
  }

  private record SnapshotSpriteLayout(double[] size, double baselineY, boolean canvasAligned) {
  }

  private static String firstLayerPath(String pathSpec) {
    if (pathSpec == null) return "";
    int idx = pathSpec.indexOf('|');
    String raw = idx >= 0 ? pathSpec.substring(0, idx) : pathSpec;
    return raw == null ? "" : raw.trim();
  }

  private static String snapshotCharacterGroupName(PuppeteerLauncherPanel.CharacterEntry ch) {
    return PuppeteerLauncherPanel.snapshotCharacterGroupName(ch);
  }

  private static String selectorSafeName(String raw) {
    return PuppeteerLauncherPanel.selectorSafeName(raw);
  }

  private String resolveProjectPath(String relativePath) {
    if (relativePath == null) return "";
    if (projectRoot != null) {
      java.io.File f = new java.io.File(projectRoot, relativePath);
      if (f.exists()) return f.getAbsolutePath();
    }
    return relativePath;
  }

  private String resolveProjectPathSpec(String pathSpec) {
    if (pathSpec == null || pathSpec.isBlank()) return "";
    if (pathSpec.indexOf('|') < 0) return resolveProjectPath(pathSpec.trim());
    StringBuilder out = new StringBuilder();
    for (String token : pathSpec.split("\\|")) {
      String part = token == null ? "" : token.trim();
      if (part.isEmpty()) continue;
      if (!out.isEmpty()) out.append(" | ");
      out.append(resolveProjectPath(part));
    }
    return out.toString();
  }

  private void openActionEditor() {
    AnimationProject imported = discoverAndImportTimeline();
    PuppeteerWindow puppeteer = imported != null
        ? new PuppeteerWindow(imported)
        : new PuppeteerWindow();
    puppeteer.setOnCopyCode(code -> status.setText("Copied timeline code to clipboard"));
    puppeteer.setOnSyncSnapshotRequested(() -> {
      FileEditorTab currentFt = getActiveFileTab();
      if (currentFt == null) return;
      int caretLine = currentFt.getVnsCaretLine();
      PuppeteerLauncherPanel.SceneSnapshot newSnapshot = PuppeteerLauncherPanel.resolveSnapshot(currentFt.getCurrentTextSnapshot(), caretLine);
      JesScene2D newScene = buildSceneFromSnapshot(newSnapshot);
      puppeteer.syncWithSnapshot(newSnapshot, newScene);
    });
    if (projectRoot != null) puppeteer.setProjectRoot(projectRoot);
    FileEditorTab ft = getActiveFileTab();
    if (ft != null) {
      puppeteer.setSourceScriptFile(ft.getFile());
    }
    JesScene2D launchScene = resolvePuppeteerLaunchScene(ft, imported, null);
    if (launchScene != null) {
      puppeteer.setScene(launchScene);
    }
    puppeteer.show();
  }

  private AnimationProject importTimelineFromSnapshot(PuppeteerLauncherPanel.SceneSnapshot snapshot) {
    return importTimelineFromSnapshot(snapshot, true);
  }

	  private AnimationProject importTimelineFromSnapshot(
	      PuppeteerLauncherPanel.SceneSnapshot snapshot,
	      boolean showWarningOnFailure
	  ) {
    if (snapshot == null) return null;
    String preferredTimelineName = snapshot.preferredTimelineName();
    if (snapshot.hasInlineTimeline()) {
      try {
        return CodeImporter.importCode(
            preferredTimelineName != null && !preferredTimelineName.isBlank() ? preferredTimelineName : "inline_timeline",
            wrapInlineTimeline(snapshot.inlineTimelineBody));
      } catch (Exception ex) {
        if (showWarningOnFailure) {
          showTimelineImportWarning("inline timeline", ex);
        }
        return null;
      }
    }
    if (preferredTimelineName != null && !preferredTimelineName.isBlank()) {
      return importNamedTimeline(preferredTimelineName, showWarningOnFailure);
    }
	    return null;
	  }

	  private AnimationProject importInlineTimelineFromSnapshot(
	      PuppeteerLauncherPanel.SceneSnapshot snapshot,
	      PuppeteerLauncherPanel.InlineTimelineContext context,
	      boolean showWarningOnFailure
	  ) {
	    if (context == null || context.body() == null || context.body().isBlank()) return null;
	    String name = inlineTimelineName(snapshot, context);
	    try {
	      return CodeImporter.importCode(name, wrapInlineTimeline(context.body()));
	    } catch (Exception ex) {
	      if (showWarningOnFailure) {
	        showTimelineImportWarning(name, ex);
	      }
	      return null;
	    }
	  }

	  private static String inlineTimelineName(
	      PuppeteerLauncherPanel.SceneSnapshot snapshot,
	      PuppeteerLauncherPanel.InlineTimelineContext context
	  ) {
	    String base = snapshot != null && snapshot.currentLabel != null && !snapshot.currentLabel.isBlank()
	        ? snapshot.currentLabel
	        : "inline_timeline";
	    String normalized = base.replaceAll("[^A-Za-z0-9_]+", "_")
	        .replaceAll("_+", "_")
	        .replaceAll("^_+|_+$", "");
	    if (normalized.isBlank()) normalized = "inline_timeline";
	    int line = context == null ? 1 : Math.max(1, context.startLine() + 1);
	    return normalized + "_inline_" + line;
	  }

	  private static String wrapInlineTimeline(String body) {
    String content = body != null ? body.strip() : "";
    if (content.isEmpty()) return "timeline {\n}\n";
    return "timeline {\n" + content + "\n}\n";
  }

  /**
   * Scans scripts/timelines/ for existing .jes files and offers a choice dialog.
   * Returns the imported AnimationProject, or null if the user chose "New" or no files exist.
   */
  private AnimationProject discoverAndImportTimeline() {
    if (projectRoot == null) return null;
    java.io.File timelinesDir = new java.io.File(projectRoot, "scripts/timelines");
    if (!timelinesDir.isDirectory()) return null;
    java.io.File[] jesFiles = timelinesDir.listFiles((dir, name) ->
        name.endsWith(".jes") && !name.startsWith("."));
    if (jesFiles == null || jesFiles.length == 0) return null;

    java.util.List<String> names = new java.util.ArrayList<>();
    for (java.io.File f : jesFiles) {
      String n = f.getName();
      names.add(n.substring(0, n.length() - 4));
    }
    java.util.Collections.sort(names);

    ListView<String> listView = new ListView<>();
    listView.getItems().setAll(names);
    listView.getStyleClass().add("editor-dialog-choice-list");
    listView.getSelectionModel().selectFirst();
    listView.setPrefHeight(Math.min(320, 48 + names.size() * 30));

    Optional<String> action = EditorDialogs.show(
        dialogOwner(),
        "Open Timeline",
        "Found " + names.size() + " existing timeline(s). Open one or start fresh?",
        listView,
        listView,
        EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null),
        EditorDialogs.ActionSpec.neutral("new", "New (empty)", null),
        EditorDialogs.ActionSpec.accent("open", "Open", null));
    if (action.isEmpty() || !"open".equals(action.get())) return null;

    String selectedName = listView.getSelectionModel().getSelectedItem();
    if (selectedName == null || selectedName.isBlank()) return null;

    return importNamedTimeline(selectedName, true);
  }

  private AnimationProject importNamedTimeline(String selectedName, boolean showWarningOnFailure) {
    if (projectRoot == null || selectedName == null || selectedName.isBlank()) return null;
    try {
      java.io.File timelinesDir = new java.io.File(projectRoot, "scripts/timelines");
      java.io.File jesFile = new java.io.File(timelinesDir, selectedName + ".jes");
      if (!jesFile.isFile()) return null;
      String code = java.nio.file.Files.readString(jesFile.toPath());
      return CodeImporter.importCode(selectedName, code);
    } catch (Exception ex) {
      if (showWarningOnFailure) {
        showTimelineImportWarning(selectedName, ex);
      }
      return null;
    }
  }

  private void showTimelineImportWarning(String timelineName, Exception ex) {
    EditorDialogs.show(dialogOwner(),
        "Import Failed",
        "Could not import timeline '" + timelineName + "'",
        createDialogDetailLabel(ex != null ? ex.getMessage() : "Unknown error"),
        EditorDialogs.ActionSpec.accent("close", "Close", null));
  }

  private static void applyLinuxDefaultWindowState(Stage stage) {
    if (stage == null || !isLinux()) return;
    stage.setIconified(false);
    stage.setMaximized(true);
    Platform.runLater(() -> {
      stage.setIconified(false);
      stage.setMaximized(true);
    });
  }

  private static boolean isLinux() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
  }

}
