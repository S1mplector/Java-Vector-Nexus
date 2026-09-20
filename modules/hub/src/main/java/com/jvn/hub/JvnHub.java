package com.jvn.hub;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import javax.swing.JToggleButton;
import javax.swing.JWindow;
import javax.swing.JProgressBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.SpinnerNumberModel;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicCheckBoxUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicSpinnerUI;

/**
 * Standalone engine hub. a tiny Swing app that lets the user launch the editor,
 * launcher, or runtime, run common Gradle tasks, and pull-rebase the repository
 * without touching the {@code jvnw} wrapper CLI.
 *
 * <p>Visually mirrors the launcher/editor palette: dark charcoal neutral-gray
 * background, light text, compact header, and simple activity panel.</p>
 *
 * <p>Shells out to {@code ./gradlew} and {@code git}. The hub remains responsive while
 * a task runs; only one task at a time is allowed and the buttons disable for the
 * duration.</p>
 */
public final class JvnHub {

  private static final String PACKAGED_GRADLE_CACHE_MARKER_FILE = ".jvn-packaged-gradle-cache.properties";
  private static final String PUBLIC_DOCUMENTATION_URL = "https://javavectornexus.com";
  private static final String SOURCE_REPOSITORY_URL = "https://github.com/S1mplector/Java-Vector-Nexus";
  private static final String ENGINE_UPDATE_REMOTE = "origin";
  private static final String ENGINE_UPDATE_BRANCH = "stable";
  private static final String ENGINE_UPDATE_REMOTE_REF = ENGINE_UPDATE_REMOTE + "/" + ENGINE_UPDATE_BRANCH;
  private static final String ENGINE_UPDATE_FETCH_REFSPEC =
      "refs/heads/" + ENGINE_UPDATE_BRANCH + ":refs/remotes/" + ENGINE_UPDATE_REMOTE_REF;

  // --- Editor dark neutral-gray palette -------------------------------------
  // Mirrors the dark editor/launcher CSS: neutral graphite surfaces, no blue
  // cast, and very subtle gradients instead of flat pure black.
  private static final Color BG             = Color.decode("#101010");
  private static final Color BG_TOP         = Color.decode("#151515");
  private static final Color BG_BOTTOM      = Color.decode("#101010");
  private static final Color PANEL_BG       = Color.decode("#1c1c1c");
  private static final Color PANEL_BG_TOP   = Color.decode("#262626");
  private static final Color PANEL_BG_BOTTOM = Color.decode("#1c1c1c");
  private static final Color HOVER_BG       = Color.decode("#303030");
  private static final Color PRESSED_BG     = Color.decode("#181818");
  private static final Color BORDER_NEUTRAL = Color.decode("#3a3a3a");
  private static final Color TEXT_PRIMARY   = Color.decode("#f0f0f0");
  private static final Color TEXT_MUTED     = Color.decode("#9a9a9a");
  private static final Color TEXT_SOFT      = Color.decode("#c5c5c5");
  /** High-contrast neutral for emphasis (version tag, dates, running-task state). */
  private static final Color ACCENT_NEUTRAL = Color.decode("#c2c2c2");
  private static final Color ACCENT_GREEN   = Color.decode("#7ed39a");
  private static final Color ACCENT_DEV     = Color.decode("#8cc5ff");
  private static final Color ACCENT_RENDER  = Color.decode("#c58cff");
  private static final Color ACCENT_TOOLS   = Color.decode("#68c7d8");
  private static final Color ACCENT_SAFE    = Color.decode("#ffd166");
  private static final Color ACCENT_ERROR   = Color.decode("#f38ba8");
  private static final Color ACCENT_MAINTENANCE = Color.decode("#ff9933");
  private static final Color LOG_TEXT       = Color.decode("#cfcfcf");
  private static final Color SCROLL_THUMB   = Color.decode("#2a2a2a");
  private static final Color SCROLL_THUMB_HOVER = Color.decode("#3a3a3a");
  private static final int PROCESS_OUTPUT_PREFIX_LIMIT = 8192;
  private static final int PROCESS_OUTPUT_TAIL_LINES = 40;

  /** Resolved at class-init time from a Gradle-generated resource. */
  private static final String VERSION = readVersion();
  private static final int BASE_HUB_WIDTH = 640;
  private static final int BASE_HUB_HEIGHT = 540;
  private static final double MIN_UI_SCALE = 0.75;
  private static final double MAX_UI_SCALE = 1.85;
  private static final String UI_SCALE_KEY = "ui.scale";
  private static final String PERFORMANCE_GRAPH_VISIBLE_KEY = "performance.graph.visible";
  private static final String PERFORMANCE_CHIPS_VISIBLE_KEY = "performance.chips.visible";
  private static final String TOOLTIPS_ENABLED_KEY = "tooltips.enabled";
  private static final HubDisplayProfile DISPLAY_PROFILE = HubDisplayProfile.detect();
  private static double activeUiScale = initialUiScale();

  private static int ui(int value) {
    if (value == 0) return 0;
    return Math.max(1, (int) Math.round(value * activeUiScale));
  }

  private static float uiFont(float value) {
    return (float) (value * activeUiScale);
  }

  private static float uiStroke(float value) {
    return (float) Math.max(1.0, value * activeUiScale);
  }

  private static Dimension uiDimension(int width, int height) {
    return new Dimension(ui(width), ui(height));
  }

  private static EmptyBorder uiPadding(int top, int left, int bottom, int right) {
    return new EmptyBorder(ui(top), ui(left), ui(bottom), ui(right));
  }

  private static VectorIcon uiIcon(VectorIcon.Kind kind, int size, Color color) {
    return VectorIcon.of(kind, ui(size), color);
  }

  private final Path projectRoot;
  private final Path renderPipelinePreferencesFile;
  private final Path renderPipelineTuningFile;
  private final Path projectIconSettingsFile;
  private final Path jvmLaunchSettingsFile;
  private final Path jvmLaunchArgumentsFile;
  private final Path heapDumpDirectory;
  private final JFrame frame = new JFrame("JVN Engine Hub");
  private final JLabel statusLabel = new JLabel("Idle");
  private final JLabel footerBranchLabel = new JLabel("No branch");
  private final JLabel footerRootLabel = new JLabel();
  private final JLabel footerModeLabel = new JLabel("Standard");
  private final JLabel versionLabel = new JLabel();
  private final ActivitySpinner activitySpinner = new ActivitySpinner();
  private final JLabel activityTitle = new JLabel("Ready");
  private final JLabel activityDetail = new JLabel("Choose an action to get started.");
  private final StepListPanel activitySteps = new StepListPanel();
  private final ActivityProgressPanel activityPanel = new ActivityProgressPanel(new BorderLayout(ui(10), ui(8)));
  private final javax.swing.Timer spinnerTimer = new javax.swing.Timer(70, e -> activitySpinner.tick());
  private final javax.swing.Timer autoStepTimer = new javax.swing.Timer(1800, e -> autoAdvanceDuringSilence());
  private final List<AbstractButton> actionButtons = new ArrayList<>();
  private final AtomicReference<Process> runningProcess = new AtomicReference<>();
  private final AtomicReference<UpdateProgressDialog> updateProgressDialog = new AtomicReference<>();
  private final AtomicReference<UpdateAttempt> activeUpdateAttempt = new AtomicReference<>();
  private final AtomicReference<HubPerformancePanel> performancePanel = new AtomicReference<>();
  private final RenderGraphCapture renderGraphCapture = new RenderGraphCapture();
  private final AtomicReference<JDialog> renderGraphViewer = new AtomicReference<>();
  private final AtomicBoolean updateCheckRunning = new AtomicBoolean(false);
  private int lastKnownIncoming = -1;
  private int activeStepIndex = -1;
  private String activeStepLabel = "";
  private boolean performanceGraphVisible = loadUiBoolean(PERFORMANCE_GRAPH_VISIBLE_KEY, true);
  private boolean performanceChipsVisible = loadUiBoolean(PERFORMANCE_CHIPS_VISIBLE_KEY, true);
  private boolean tooltipsEnabled = loadUiBoolean(TOOLTIPS_ENABLED_KEY, true);

  /** Developer Mode exposes engineering-focused actions and launch flags. */
  private boolean developerModeEnabled = false;
  private DeveloperModeToggleButton developerModeButton;
  /** Safe Mode launch toggle; applies to editor-side processes launched from the hub. */
  private boolean safeModeEnabled = false;
  private SafeModeToggleButton safeModeButton;
  private JPanel actionGrid;
  private JButton runEditorButton;
  private JButton buildAllButton;
  private JButton runTestsButton;
  private JButton gradleOptionsButton;
  private JButton buildShortcutsButton;
  private boolean gradleStacktraceEnabled = true;
  private boolean gradleInfoLoggingEnabled = false;
  private boolean gradleDebugLoggingEnabled = false;
  private boolean gradleOfflineEnabled = false;
  private boolean gradleRefreshDependenciesEnabled = false;
  private boolean gradleNoBuildCacheEnabled = false;
  private boolean gradleNoDaemonEnabled = false;
  private String gradleExtraArgs = "";
  /** Rendering profile persisted for editor, preview, launcher, and game-runtime processes. */
  private RenderPipelineSettings.Mode renderPipelineMode;
  private RenderPipelineSettings.Options renderPipelineOptions;
  /** Project-tree icon profile shared with editor processes launched by the Hub. */
  private ProjectIconThemeSettings.Options projectIconOptions;
  /** Memory and GC policy applied to every Hub-managed JVN application process. */
  private JvmLaunchSettings jvmLaunchSettings;
  /** Header shortcut for a lightweight local environment report. */
  private HeaderIconButton diagnosticsButton;
  /** Header shortcut for version, source, install, and update details. */
  private HeaderIconButton aboutButton;
  /** Header shortcut that opens the public documentation website. */
  private HeaderIconButton documentationButton;
  /** Update button with a right-aligned incoming-commit badge. */
  private UpdateEngineButton updateEngineButton;
  private boolean frameConfigured;
  private boolean shutdownInProgress;
  private ResizeOverlay resizeOverlay;
  private javax.swing.Timer resizeOverlayTimer;

  private JvnHub(Path projectRoot) {
    this.projectRoot = projectRoot;
    this.renderPipelinePreferencesFile = RenderPipelineSettings.defaultPreferencesFile();
    this.renderPipelineTuningFile = RenderPipelineSettings.defaultTuningFile();
    this.projectIconSettingsFile = ProjectIconThemeSettings.defaultFile();
    this.jvmLaunchSettingsFile = JvmLaunchSettings.defaultSettingsFile();
    this.jvmLaunchArgumentsFile = JvmLaunchSettings.defaultArgumentsFile();
    this.heapDumpDirectory = JvmLaunchSettings.defaultHeapDumpDirectory();
    this.renderPipelineMode = RenderPipelineSettings.load(renderPipelinePreferencesFile);
    this.renderPipelineOptions = RenderPipelineSettings.loadOptions(renderPipelineTuningFile);
    this.projectIconOptions = ProjectIconThemeSettings.load(projectIconSettingsFile);
    this.jvmLaunchSettings = JvmLaunchSettings.load(jvmLaunchSettingsFile);
    writeJvmLaunchArguments(jvmLaunchSettings, false);
    configureToolTipManager(tooltipsEnabled);
    buildUi();
    checkIncomingUpdates(true);
  }

  /** Entry point. Can be invoked directly or via the {@code :hub:run} Gradle task. */
  public static void main(String[] args) {
    Path root = resolveProjectRoot(args);
    boolean openJvmMemorySettings = hasArgument(args, "--jvm-memory-settings");
    // Keep the cross-platform (Metal) L&F — Aqua on macOS tints custom backgrounds
    // with system chrome we cannot override per-component. Cross-platform L&F honors
    // setBackground/setForeground verbatim, which is what the custom theme needs.
    applyHubDefaults();
    SwingUtilities.invokeLater(() -> {
      HubLifecycleSplash splash = HubLifecycleSplash.startup();
      splash.showCentered();
      javax.swing.Timer launchDelay = new javax.swing.Timer(120, event -> {
        ((javax.swing.Timer) event.getSource()).stop();
        JvnHub hub = new JvnHub(root);
        hub.frame.setVisible(true);
        signalRelaunchReady();
        splash.closeAfter(320, null);
        if (openJvmMemorySettings) {
          SwingUtilities.invokeLater(hub::showJvmMemorySettingsDialog);
        }
      });
      launchDelay.setRepeats(false);
      launchDelay.start();
    });
  }

  /** Seed UIManager so ancillary components (tooltips, split panes, dialogs) match. */
  private static void applyHubDefaults() {
    UIManager.put("control", BG);
    UIManager.put("info", BG);
    UIManager.put("nimbusBase", BG);
    UIManager.put("nimbusBlueGrey", BORDER_NEUTRAL);
    // Root chrome that can otherwise bleed through as the cross-platform L&F's
    // default light grey behind the content pane and at the window edges.
    UIManager.put("Frame.background", BG);
    UIManager.put("Window.background", BG);
    UIManager.put("RootPane.background", BG);
    UIManager.put("Dialog.background", BG);
    UIManager.put("Panel.background", BG);
    UIManager.put("OptionPane.background", BG);
    UIManager.put("ToolTip.background", BG);
    UIManager.put("ToolTip.foreground", TEXT_SOFT);
    UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER_NEUTRAL));
    ToolTipManager.sharedInstance().setInitialDelay(280);
    ToolTipManager.sharedInstance().setReshowDelay(80);
    ToolTipManager.sharedInstance().setDismissDelay(12000);
    // macOS's native delegates paint a light menu strip regardless of component
    // background values. Basic delegates respect the Hub palette on every host.
    UIManager.put("MenuBarUI", "javax.swing.plaf.basic.BasicMenuBarUI");
    UIManager.put("MenuUI", "javax.swing.plaf.basic.BasicMenuUI");
    UIManager.put("MenuItemUI", "javax.swing.plaf.basic.BasicMenuItemUI");
    UIManager.put("CheckBoxUI", "javax.swing.plaf.basic.BasicCheckBoxUI");
    UIManager.put("ComboBoxUI", "javax.swing.plaf.basic.BasicComboBoxUI");
    UIManager.put("CheckBoxMenuItemUI", "javax.swing.plaf.basic.BasicCheckBoxMenuItemUI");
    UIManager.put("RadioButtonMenuItemUI", "javax.swing.plaf.basic.BasicRadioButtonMenuItemUI");
    UIManager.put("PopupMenuUI", "javax.swing.plaf.basic.BasicPopupMenuUI");
    UIManager.put("PopupMenuSeparatorUI", "javax.swing.plaf.basic.BasicPopupMenuSeparatorUI");
    UIManager.put("MenuBar.background", BG_TOP);
    UIManager.put("MenuBar.foreground", TEXT_PRIMARY);
    UIManager.put("Menu.background", BG_TOP);
    UIManager.put("Menu.foreground", TEXT_PRIMARY);
    UIManager.put("Menu.selectionBackground", HOVER_BG);
    UIManager.put("Menu.selectionForeground", TEXT_PRIMARY);
    UIManager.put("MenuItem.background", PANEL_BG);
    UIManager.put("MenuItem.foreground", TEXT_PRIMARY);
    UIManager.put("MenuItem.selectionBackground", HOVER_BG);
    UIManager.put("MenuItem.selectionForeground", TEXT_PRIMARY);
    UIManager.put("CheckBoxMenuItem.background", PANEL_BG);
    UIManager.put("CheckBoxMenuItem.foreground", TEXT_PRIMARY);
    UIManager.put("CheckBoxMenuItem.selectionBackground", HOVER_BG);
    UIManager.put("CheckBoxMenuItem.selectionForeground", TEXT_PRIMARY);
    UIManager.put("CheckBoxMenuItem.checkIcon", new HubMenuSelectionIcon(false));
    UIManager.put("RadioButtonMenuItem.background", PANEL_BG);
    UIManager.put("RadioButtonMenuItem.foreground", TEXT_PRIMARY);
    UIManager.put("RadioButtonMenuItem.selectionBackground", HOVER_BG);
    UIManager.put("RadioButtonMenuItem.selectionForeground", TEXT_PRIMARY);
    UIManager.put("RadioButtonMenuItem.checkIcon", new HubMenuSelectionIcon(true));
    UIManager.put("CheckBox.background", PANEL_BG);
    UIManager.put("CheckBox.foreground", TEXT_SOFT);
    UIManager.put("ComboBox.background", BG);
    UIManager.put("ComboBox.foreground", TEXT_PRIMARY);
    UIManager.put("ComboBox.selectionBackground", HOVER_BG);
    UIManager.put("ComboBox.selectionForeground", TEXT_PRIMARY);
    UIManager.put("PopupMenu.background", PANEL_BG);
    UIManager.put("PopupMenu.foreground", TEXT_PRIMARY);
    UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDER_NEUTRAL));
    UIManager.put("Separator.background", PANEL_BG);
    UIManager.put("Separator.foreground", BORDER_NEUTRAL);
    UIManager.put("PopupMenuSeparator.background", PANEL_BG);
    UIManager.put("PopupMenuSeparator.foreground", BORDER_NEUTRAL);
    UIManager.put("ScrollPane.background", BG);
    UIManager.put("TextArea.background", BG);
    UIManager.put("TextArea.foreground", LOG_TEXT);
    UIManager.put("TextArea.caretForeground", LOG_TEXT);
    UIManager.put("ScrollBar.background", BG);
    UIManager.put("ScrollBar.track", BG);
    UIManager.put("ScrollBar.thumb", SCROLL_THUMB);
    scaleDefaultUiFonts();
  }

  private static void scaleDefaultUiFonts() {
    if (Math.abs(activeUiScale - 1.0) < 0.01) return;
    for (String key : List.of(
        "Button.font",
        "CheckBox.font",
        "Label.font",
        "Menu.font",
        "MenuItem.font",
        "OptionPane.buttonFont",
        "OptionPane.messageFont",
        "PopupMenu.font",
        "TextArea.font",
        "TextField.font",
        "ToggleButton.font",
        "ToolTip.font")) {
      Object value = UIManager.get(key);
      if (value instanceof Font font) {
        UIManager.put(key, font.deriveFont(uiFont(font.getSize2D())));
      }
    }
  }

  private record HubDisplayProfile(
      double uiScale,
      int screenWidth,
      int screenHeight,
      int dpi,
      double deviceScale,
      boolean override) {

    static HubDisplayProfile detect() {
      double overrideScale = parseScaleOverride(System.getProperty("jvn.hub.uiScale"));
      if (!Double.isFinite(overrideScale)) overrideScale = parseScaleOverride(System.getenv("JVN_HUB_UI_SCALE"));
      if (Double.isFinite(overrideScale)) {
        return new HubDisplayProfile(clampScale(overrideScale), 0, 0, 0, 1.0, true);
      }

      int screenWidth = 0;
      int screenHeight = 0;
      int dpi = 96;
      double deviceScale = 1.0;
      try {
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = environment.getDefaultScreenDevice();
        GraphicsConfiguration configuration = device.getDefaultConfiguration();
        Rectangle bounds = configuration.getBounds();
        screenWidth = Math.max(0, bounds.width);
        screenHeight = Math.max(0, bounds.height);
        AffineTransform transform = configuration.getDefaultTransform();
        deviceScale = Math.max(Math.abs(transform.getScaleX()), Math.abs(transform.getScaleY()));
      } catch (Exception ignored) {
        // Headless or unusual desktop environments fall back to a normal 1x hub.
      }
      try {
        dpi = Toolkit.getDefaultToolkit().getScreenResolution();
      } catch (Exception ignored) {
        // Some containerized desktops do not expose DPI to AWT.
      }

      double scale = automaticScaleForDisplay(screenWidth, screenHeight, dpi);
      return new HubDisplayProfile(scale, screenWidth, screenHeight, dpi, deviceScale, false);
    }

    private static double parseScaleOverride(String raw) {
      if (raw == null || raw.isBlank()) return Double.NaN;
      try {
        double parsed = Double.parseDouble(raw.trim());
        return Double.isFinite(parsed) && parsed > 0.0 ? parsed : Double.NaN;
      } catch (NumberFormatException ignored) {
        return Double.NaN;
      }
    }

    private static double clampScale(double value) {
      return Math.max(MIN_UI_SCALE, Math.min(MAX_UI_SCALE, value));
    }
  }

  static double automaticScaleForDisplay(int screenWidth, int screenHeight, int dpi) {
      int longSide = Math.max(screenWidth, screenHeight);
      int shortSide = screenWidth > 0 && screenHeight > 0 ? Math.min(screenWidth, screenHeight) : 0;
      double resolutionScale = 1.0;
      if (longSide >= 3800 || shortSide >= 2100) {
        resolutionScale = 1.45;
      } else if (longSide >= 3200 || shortSide >= 1800) {
        resolutionScale = 1.32;
      } else if (longSide >= 2800 || shortSide >= 1600) {
        resolutionScale = 1.20;
      }

      double dpiScale = 1.0;
      if (dpi >= 216) {
        dpiScale = 1.50;
      } else if (dpi >= 168) {
        dpiScale = 1.35;
      } else if (dpi >= 132) {
        dpiScale = 1.20;
      }

      // Swing already renders in logical coordinates on HiDPI displays. Applying the
      // graphics transform again makes Retina windows roughly 1.5x too large.
      double scale = Math.max(resolutionScale, dpiScale);
      if (screenWidth > 0 && screenHeight > 0) {
        double widthCap = (screenWidth * 0.82) / BASE_HUB_WIDTH;
        double heightCap = (screenHeight * 0.82) / BASE_HUB_HEIGHT;
        double screenCap = Math.max(1.0, Math.min(widthCap, heightCap));
        scale = Math.min(scale, screenCap);
      }
      return HubDisplayProfile.clampScale(scale);
  }

  static Dimension hubSizeForScale(double scale) {
    double bounded = HubDisplayProfile.clampScale(scale);
    return new Dimension(
        (int) Math.round(BASE_HUB_WIDTH * bounded),
        (int) Math.round(BASE_HUB_HEIGHT * bounded));
  }

  private static String displayScaleSummary() {
    return String.format(Locale.ROOT, "Hub UI scale %.2fx", activeUiScale);
  }

  private static String displayScaleDetails() {
    if (DISPLAY_PROFILE.override()) {
      return "Manual override from jvn.hub.uiScale or JVN_HUB_UI_SCALE.";
    }
    String screen = DISPLAY_PROFILE.screenWidth() > 0 && DISPLAY_PROFILE.screenHeight() > 0
        ? DISPLAY_PROFILE.screenWidth() + "x" + DISPLAY_PROFILE.screenHeight()
        : "unknown";
    String dpi = DISPLAY_PROFILE.dpi() > 0 ? Integer.toString(DISPLAY_PROFILE.dpi()) : "unknown";
    return "screen=" + screen
        + "; dpi=" + dpi
        + "; deviceScale=" + String.format(Locale.ROOT, "%.2f", DISPLAY_PROFILE.deviceScale());
  }

  private static double initialUiScale() {
    if (DISPLAY_PROFILE.override()) return DISPLAY_PROFILE.uiScale();
    Properties properties = loadUiState();
    String value = properties.getProperty(UI_SCALE_KEY, "auto").trim();
    if (value.equalsIgnoreCase("auto")) return DISPLAY_PROFILE.uiScale();
    try {
      return HubDisplayProfile.clampScale(Double.parseDouble(value));
    } catch (NumberFormatException ignored) {
      return DISPLAY_PROFILE.uiScale();
    }
  }

  private static Path uiStateFile() {
    return Paths.get(System.getProperty("user.home", "."), ".jvn", "hub-ui.properties");
  }

  private static boolean automaticUiScaleSelected() {
    if (DISPLAY_PROFILE.override()) return false;
    return loadUiState().getProperty(UI_SCALE_KEY, "auto").trim().equalsIgnoreCase("auto");
  }

  private void saveUiScale(String value) {
    Properties properties = loadUiState();
    properties.setProperty(UI_SCALE_KEY, value);
    writeUiState(properties);
  }

  private static boolean loadUiBoolean(String key, boolean fallback) {
    return booleanPreference(loadUiState(), key, fallback);
  }

  static boolean booleanPreference(Properties properties, String key, boolean fallback) {
    if (properties == null || key == null || key.isBlank()) return fallback;
    String value = properties.getProperty(key);
    if (value == null || value.isBlank()) return fallback;
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "true", "1", "yes", "on", "enabled" -> true;
      case "false", "0", "no", "off", "disabled" -> false;
      default -> fallback;
    };
  }

  private static Properties loadUiState() {
    Properties properties = new Properties();
    Path file = uiStateFile();
    if (!Files.isRegularFile(file)) return properties;
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
    } catch (IOException | IllegalArgumentException ignored) {
      // Invalid or unreadable preferences fall back to defaults without blocking Hub startup.
    }
    return properties;
  }

  private void savePerformanceVisibility() {
    Properties properties = loadUiState();
    properties.setProperty(PERFORMANCE_GRAPH_VISIBLE_KEY, Boolean.toString(performanceGraphVisible));
    properties.setProperty(PERFORMANCE_CHIPS_VISIBLE_KEY, Boolean.toString(performanceChipsVisible));
    writeUiState(properties);
  }

  private void saveTooltipVisibility() {
    Properties properties = loadUiState();
    properties.setProperty(TOOLTIPS_ENABLED_KEY, Boolean.toString(tooltipsEnabled));
    writeUiState(properties);
  }

  private void writeUiState(Properties properties) {
    Path file = uiStateFile();
    try {
      Files.createDirectories(file.getParent());
      try (var output = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
        properties.store(output, "JVN Engine Hub UI preferences. Auto-generated.");
      }
    } catch (IOException e) {
      appendLog("[hub] could not save UI preferences: " + e.getMessage());
    }
  }

  // --- UI assembly -----------------------------------------------------------

  private void buildUi() {
    if (!frameConfigured) {
      frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      frame.addWindowListener(new WindowAdapter() {
        @Override public void windowClosing(WindowEvent e) { confirmAndExit(); }
      });
      frameConfigured = true;
    }

    JPanel root = new GradientPanel(new BorderLayout(0, ui(12)), BG_TOP, BG_BOTTOM);
    root.setBackground(BG);
    root.setBorder(uiPadding(16, 16, 16, 16));

    root.add(buildHeader(), BorderLayout.NORTH);
    root.add(buildCenter(), BorderLayout.CENTER);
    root.add(buildFooter(), BorderLayout.SOUTH);

    frame.setContentPane(root);
    frame.setJMenuBar(buildMenuBar());
    // Keep frame/root chrome aligned with the custom charcoal-neutral surface.
    frame.setBackground(BG);
    frame.getRootPane().setBackground(BG);
    frame.getRootPane().setOpaque(true);
    frame.getContentPane().setBackground(BG);
    installResizeOverlay();

    Dimension hubSize = uiDimension(BASE_HUB_WIDTH, BASE_HUB_HEIGHT);
    frame.setResizable(true);
    frame.setMinimumSize(hubSize);
    frame.setPreferredSize(hubSize);
    frame.pack();
    frame.setSize(hubSize);
    frame.setLocationRelativeTo(null);

    installApplicationIcon(frame);
  }

  private void installResizeOverlay() {
    resizeOverlay = new ResizeOverlay(frame);
    resizeOverlayTimer = new javax.swing.Timer(520, event -> resizeOverlay.setVisible(false));
    resizeOverlayTimer.setRepeats(false);
    frame.setGlassPane(resizeOverlay);
    frame.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        if (!frame.isShowing() || shutdownInProgress) return;
        resizeOverlay.updateDimensions();
        resizeOverlay.setVisible(true);
        resizeOverlayTimer.restart();
      }
    });
  }

  static String formatWindowPixels(int width, int height) {
    return Math.max(0, width) + " × " + Math.max(0, height) + " px";
  }

  private JMenuBar buildMenuBar() {
    JMenuBar bar = new JMenuBar() {
      @Override
      protected void paintComponent(Graphics graphics) {
        graphics.setColor(BG_TOP);
        graphics.fillRect(0, 0, getWidth(), getHeight());
      }
    };
    bar.setOpaque(true);
    bar.setBackground(BG_TOP);
    bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_NEUTRAL));

    boolean busy = runningProcess.get() != null;
    String branch = resolveBranch(projectRoot);
    String version = displayVersionLabel(readDiskVersion());
    String updateSummary = lastKnownIncoming > 0
        ? lastKnownIncoming + " incoming commit" + (lastKnownIncoming == 1 ? "" : "s")
        : lastKnownIncoming == 0 ? "Up to date with " + ENGINE_UPDATE_REMOTE_REF : "Update status unavailable";

    JMenu file = hubMenu("File", TEXT_SOFT, KeyEvent.VK_F);
    file.add(menuStatusCard("Engine workspace", compactMenuPath(projectRoot), TEXT_SOFT));
    file.addSeparator();
    file.add(hubMenuItem(
        "Show Engine Folder",
        "Reveal the active engine checkout in the system file manager.",
        VectorIcon.Kind.INFO,
        TEXT_SOFT,
        this::revealEngineRoot));
    file.add(hubMenuItem(
        "Show Hub Data Folder",
        "Open user-local JVN Hub state and configuration.",
        VectorIcon.Kind.SLIDERS,
        TEXT_SOFT,
        () -> revealHubFolder("data", Paths.get(System.getProperty("user.home", "."), ".jvn"))));
    file.add(hubMenuItem(
        "Show Hub Logs",
        "Open user-local Hub logs.",
        VectorIcon.Kind.DOCUMENTATION,
        TEXT_SOFT,
        () -> revealHubFolder("logs", Paths.get(System.getProperty("user.home", "."), ".jvn", "logs"))));
    file.add(hubMenuItem(
        "Copy Engine Path",
        "Copy the active engine root to the clipboard.",
        VectorIcon.Kind.CHECK,
        TEXT_SOFT,
        this::copyEngineRootPath));
    file.addSeparator();
    file.add(withAccelerator(hubMenuItem(
        "Quit Engine Hub",
        "Close the Hub after confirming any running task.",
        VectorIcon.Kind.CLOSE,
        TEXT_SOFT,
        this::confirmAndExit), KeyEvent.VK_Q, menuShortcutMask()));

    JMenu engine = hubMenu("Engine", ACCENT_NEUTRAL, KeyEvent.VK_E);
    engine.add(menuStatusCard(version + " · " + branch,
        isRunningFromSource() ? "Source checkout" : "Packaged engine workspace", ACCENT_NEUTRAL));
    engine.addSeparator();
    engine.add(hubMenuItem(
        "Module Inventory",
        "List every configured JVN module.",
        VectorIcon.Kind.INFO,
        ACCENT_NEUTRAL,
        this::showModuleInventory));
    engine.add(hubMenuItem(
        "Engine Configuration",
        "Inspect toolchain, JavaFX, cache, and build settings.",
        VectorIcon.Kind.SLIDERS,
        ACCENT_NEUTRAL,
        this::showEngineConfiguration));
    engine.addSeparator();
    engine.add(hubMenuItem(
        "Open gradle.properties",
        "Edit the root Gradle configuration file.",
        VectorIcon.Kind.EDIT,
        ACCENT_NEUTRAL,
        this::openEngineConfiguration));
    engine.add(hubMenuItem(
        "Copy Environment Summary",
        "Copy a support-ready engine and host summary.",
        VectorIcon.Kind.CHECK,
        ACCENT_NEUTRAL,
        this::copyEngineEnvironmentSummary));
    engine.addSeparator();
    JMenu plugins = hubMenu("Plugins", ACCENT_DEV, KeyEvent.VK_P);
    plugins.add(menuStatusCard(
        "Plugin bundles",
        "Verify and load JARs without running plugin code",
        ACCENT_DEV));
    plugins.addSeparator();
    plugins.add(hubMenuItem(
        "Manage Plugins…",
        "Inspect installed plugin bundles, verify their manifests and entrypoints, or load a verified JAR.",
        VectorIcon.Kind.HEALTH,
        ACCENT_DEV,
        this::showPluginManager));
    plugins.add(hubMenuItem(
        "Open User Plugin Folder",
        "Open the user-wide plugin folder used by every JVN project.",
        VectorIcon.Kind.INFO,
        ACCENT_DEV,
        () -> revealHubFolder("plugins", Paths.get(System.getProperty("user.home", "."), ".jvn", "plugins"))));
    if (projectRoot != null) {
      plugins.add(hubMenuItem(
          "Open Project Plugin Folder",
          "Open this project’s plugin folder.",
          VectorIcon.Kind.INFO,
          ACCENT_DEV,
          () -> revealHubFolder("project plugins", projectRoot.resolve("plugins"))));
    }
    engine.add(plugins);
    engine.addSeparator();
    engine.add(withAccelerator(hubMenuItem(
        "Refresh Engine Metadata",
        "Reload the engine version, rendering preferences, and menu status.",
        VectorIcon.Kind.REFRESH,
        ACCENT_NEUTRAL,
        this::refreshFromDisk), KeyEvent.VK_F5, 0));

    JMenu build = hubMenu("Build", ACCENT_GREEN, KeyEvent.VK_B);
    build.add(menuStatusCard(
        busy ? "Build system busy" : "Build system ready",
        developerModeEnabled ? describeGradleOptions() : "Standard cached Gradle execution",
        ACCENT_GREEN));
    build.addSeparator();
    JMenuItem compileAll = hubMenuItem(
        "Compile All Modules",
        "Compile the complete workspace without packaging outputs.",
        VectorIcon.Kind.HAMMER,
        ACCENT_GREEN,
        () -> guardedRun("Compile All Modules", () -> runGradle("compileAll", "Compile All Modules")));
    compileAll.setEnabled(!busy);
    build.add(compileAll);
    JMenuItem buildAll = hubMenuItem(
        "Build All Modules",
        "Run the full workspace build.",
        VectorIcon.Kind.ROCKET,
        ACCENT_GREEN,
        () -> clickIfAvailable(buildAllButton));
    buildAll.setEnabled(!busy);
    build.add(buildAll);
    JMenuItem quickCheck = hubMenuItem(
        "Quick Verification",
        "Run the fast engine verification path.",
        VectorIcon.Kind.CHECK,
        ACCENT_GREEN,
        () -> guardedRun("Quick Verification", () -> runGradle("quickCheck", "Quick Verification")));
    quickCheck.setEnabled(!busy);
    build.add(quickCheck);
    JMenuItem tests = hubMenuItem(
        "Run Test Suite",
        "Execute the complete automated test suite.",
        VectorIcon.Kind.HEALTH,
        ACCENT_GREEN,
        () -> clickIfAvailable(runTestsButton));
    tests.setEnabled(!busy);
    build.add(tests);
    build.addSeparator();
    build.add(hubMenuItem(
        "Gradle Options",
        "Configure diagnostic and dependency flags used in Developer Mode.",
        VectorIcon.Kind.SLIDERS,
        ACCENT_GREEN,
        this::showGradleOptionsDialog));
    JMenuItem shortcuts = hubMenuItem(
        "Install Platform Shortcuts",
        "Create native desktop and application-menu launchers.",
        VectorIcon.Kind.SHORTCUT,
        ACCENT_GREEN,
        () -> clickIfAvailable(buildShortcutsButton));
    shortcuts.setEnabled(!busy);
    build.add(shortcuts);

    JMenu sourceControl = hubMenu("Source Control", ACCENT_MAINTENANCE, KeyEvent.VK_S);
    sourceControl.add(menuStatusCard(branch, updateSummary, ACCENT_MAINTENANCE));
    sourceControl.addSeparator();
    JMenuItem checkUpdates = hubMenuItem(
        "Check for Engine Updates",
        "Fetch and compare the local checkout with origin/stable.",
        VectorIcon.Kind.REFRESH,
        ACCENT_MAINTENANCE,
        () -> checkIncomingUpdates(true));
    checkUpdates.setEnabled(!busy && !updateCheckRunning.get());
    sourceControl.add(checkUpdates);
    JMenuItem update = hubMenuItem(
        "Update from Stable",
        "Run the guarded stable update and recovery flow.",
        VectorIcon.Kind.ROCKET,
        ACCENT_MAINTENANCE,
        () -> clickIfAvailable(updateEngineButton));
    update.setEnabled(!busy);
    sourceControl.add(update);
    sourceControl.addSeparator();
    sourceControl.add(hubMenuItem(
        "Open GitHub Repository",
        "Open the JVN source repository in a browser.",
        VectorIcon.Kind.DOCUMENTATION,
        ACCENT_MAINTENANCE,
        this::openSourceRepository));
    sourceControl.add(hubMenuItem(
        "Copy Current Branch",
        "Copy the active Git branch name.",
        VectorIcon.Kind.CHECK,
        ACCENT_MAINTENANCE,
        this::copyCurrentBranch));

    JMenu tools = hubMenu("Tools", ACCENT_TOOLS, KeyEvent.VK_T);
    tools.add(menuStatusCard(
        busy ? "Background task active" : "Tooling ready",
        busy ? firstNonBlank(activeStepLabel, "Working") : "No background task is running",
        ACCENT_TOOLS));
    tools.add(menuStatusCard(
        "Application JVM",
        jvmLaunchSettings.summary(),
        ACCENT_TOOLS));
    tools.addSeparator();
    JMenu checks = hubMenu("Build & Verify", ACCENT_TOOLS);
    JMenuItem toolsQuickCheck = hubMenuItem("Quick Verification", "Compile all modules and run the core/runtime tests.",
        VectorIcon.Kind.HEALTH, ACCENT_TOOLS, () -> runGradle("quickCheck", "Quick Verification"));
    toolsQuickCheck.setEnabled(!busy);
    checks.add(toolsQuickCheck);
    JMenuItem prepareEditor = hubMenuItem("Prepare Editor Launch", "Compile the editor and refresh its direct-launch cache.",
        VectorIcon.Kind.REFRESH, ACCENT_TOOLS, () -> runGradle(":editor:prepareFastLaunch", "Prepare Editor Launch"));
    prepareEditor.setEnabled(!busy);
    checks.add(prepareEditor);
    tools.add(checks);
    JMenu profiling = hubMenu("Performance & Recordings", ACCENT_TOOLS);
    JMenuItem record = hubMenuItem("Record Editor Session (JFR)",
        "Record CPU, memory allocation, and garbage collection; close the editor to finish the recording.",
        VectorIcon.Kind.PLAY, ACCENT_TOOLS, () -> guardedRun("Profile Editor", this::runProfiledEditor));
    record.setEnabled(!busy);
    profiling.add(record);
    profiling.add(hubMenuItem("Open Recordings Folder", "Open saved Java Flight Recorder sessions.",
        VectorIcon.Kind.DOCUMENTATION, ACCENT_TOOLS, () -> revealHubFolder("recordings",
            RenderPipelineSettings.profileDirectory(System.getProperty("os.name", ""),
                Path.of(System.getProperty("user.home", ".")), System.getenv()))));
    profiling.add(hubMenuItem("Copy Performance Setup", "Copy renderer, preview budget, and JVM settings for comparison.",
        VectorIcon.Kind.CHECK, ACCENT_TOOLS, () -> {
          Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
              new StringSelection(renderPipelineSummary() + "\nApplication JVM: " + jvmLaunchSettings.summary()), null);
          setStatus("Performance setup copied", ACCENT_TOOLS);
        }));
    tools.add(profiling);
    tools.add(hubMenuItem(
        "JVM Memory Settings...",
        "Set real heap, garbage collector, and out-of-memory options for managed application launches.",
        VectorIcon.Kind.SLIDERS,
        ACCENT_TOOLS,
        this::showJvmMemorySettingsDialog));
    tools.add(hubMenuItem(
        "Run Diagnostics",
        "Run lightweight workspace, toolchain, and repository checks.",
        VectorIcon.Kind.HEALTH,
        ACCENT_TOOLS,
        this::showDiagnosticsReport));
    tools.add(hubMenuItem(
        "Show Hub Data Folder",
        "Open user-local Hub state.",
        VectorIcon.Kind.SLIDERS,
        ACCENT_TOOLS,
        () -> revealHubFolder("data", Paths.get(System.getProperty("user.home", "."), ".jvn"))));
    tools.add(hubMenuItem(
        "Show Hub Logs",
        "Open Hub process and recovery logs.",
        VectorIcon.Kind.DOCUMENTATION,
        ACCENT_TOOLS,
        () -> revealHubFolder("logs", Paths.get(System.getProperty("user.home", "."), ".jvn", "logs"))));
    tools.addSeparator();
    JMenuItem cancel = hubMenuItem(
        "Cancel Running Task",
        busy ? "Stop the active Hub-managed process." : "No Hub-managed process is running.",
        VectorIcon.Kind.STOP,
        ACCENT_ERROR,
        this::cancelRunning);
    cancel.setEnabled(busy);
    tools.add(cancel);

    JMenu view = hubMenu("View", TEXT_SOFT, KeyEvent.VK_V);
    view.add(menuStatusCard(
        displayScaleSummary(),
        (developerModeEnabled ? "Developer Mode" : "Standard Mode")
            + " · " + (safeModeEnabled ? "Safe Mode" : "Normal launch guardrails"),
        TEXT_SOFT));
    view.add(menuStatusCard(
        "Performance monitor",
        performanceVisibilitySummary(),
        ACCENT_TOOLS));
    view.add(menuStatusCard(
        "Project Explorer icons",
        ProjectIconThemeSettings.summary(projectIconOptions),
        ACCENT_GREEN));
    view.addSeparator();
    view.add(buildUiScaleMenu());
    view.add(buildProjectIconThemeMenu());
    view.addSeparator();
    JCheckBoxMenuItem performanceGraph = hubCheckMenuItem(
        "Show Performance Graph",
        "Show or hide the scrolling CPU, heap, and Hub-task history graph.",
        ACCENT_TOOLS,
        performanceGraphVisible);
    performanceGraph.addActionListener(e -> setPerformanceGraphVisible(performanceGraph.isSelected()));
    view.add(performanceGraph);
    JCheckBoxMenuItem performanceChips = hubCheckMenuItem(
        "Show Performance Metric Chips",
        "Show or hide the CPU, JVM heap, thread, and Hub-task metric chips.",
        ACCENT_TOOLS,
        performanceChipsVisible);
    performanceChips.addActionListener(e -> setPerformanceChipsVisible(performanceChips.isSelected()));
    view.add(performanceChips);
    JCheckBoxMenuItem tooltips = hubCheckMenuItem(
        "Show Tooltips",
        "Show or hide contextual help popups throughout Engine Hub.",
        ACCENT_NEUTRAL,
        tooltipsEnabled);
    tooltips.addActionListener(e -> setTooltipsEnabled(tooltips.isSelected()));
    view.add(tooltips);
    view.addSeparator();
    JCheckBoxMenuItem safeMode = hubCheckMenuItem(
        "Safe Mode",
        "Enable guarded launches and update recovery.",
        ACCENT_SAFE,
        safeModeEnabled);
    safeMode.addActionListener(e -> setSafeModeEnabled(safeMode.isSelected()));
    view.add(safeMode);
    JCheckBoxMenuItem developerMode = hubCheckMenuItem(
        "Developer Mode",
        "Expose test, Gradle, and engineering diagnostics without resizing the Hub.",
        ACCENT_DEV,
        developerModeEnabled);
    developerMode.addActionListener(e -> setDeveloperModeEnabled(developerMode.isSelected()));
    view.add(developerMode);

    JMenu help = hubMenu("Help", TEXT_SOFT, KeyEvent.VK_H);
    help.add(menuStatusCard("JVN " + version, "Documentation and install information", TEXT_SOFT));
    help.addSeparator();
    help.add(hubMenuItem(
        "Documentation",
        "Open the public JVN documentation website.",
        VectorIcon.Kind.DOCUMENTATION,
        TEXT_SOFT,
        this::openDocumentationWebsite));
    help.addSeparator();
    help.add(hubMenuItem(
        "About Engine Hub",
        "Show version, revision, install, Java, OS, and display details.",
        VectorIcon.Kind.INFO,
        TEXT_SOFT,
        this::showAboutReport));

    bar.add(file);
    bar.add(engine);
    bar.add(buildRenderPipelineMenu());
    bar.add(build);
    bar.add(sourceControl);
    bar.add(tools);
    if (developerModeEnabled) bar.add(buildDeveloperModeMenu());
    if (safeModeEnabled) bar.add(buildSafeModeMenu());
    bar.add(view);
    bar.add(help);
    return bar;
  }

  private void showPluginManager() {
    HubPluginManagerDialog dialog = new HubPluginManagerDialog(frame, projectRoot, () -> {
      statusLabel.setText("Plugin folders changed. Reload the project in the editor to apply the new plugin set.");
    });
    dialog.setVisible(true);
  }

  private JMenu buildDeveloperModeMenu() {
    boolean busy = runningProcess.get() != null;
    JMenu developer = hubMenu("Developer", ACCENT_DEV, KeyEvent.VK_D);
    developer.add(menuStatusCard(
        "Developer Mode active",
        describeGradleOptions(),
        ACCENT_DEV));
    developer.addSeparator();
    JMenuItem compile = hubMenuItem(
        "Compile All Modules",
        "Compile the workspace with Developer Mode launch flags.",
        VectorIcon.Kind.HAMMER,
        ACCENT_DEV,
        () -> guardedRun("Compile All Modules", () -> runGradle("compileAll", "Compile All Modules")));
    compile.setEnabled(!busy);
    developer.add(compile);
    JMenuItem verify = hubMenuItem(
        "Quick Verification",
        "Run the fast engineering verification path.",
        VectorIcon.Kind.CHECK,
        ACCENT_DEV,
        () -> guardedRun("Quick Verification", () -> runGradle("quickCheck", "Quick Verification")));
    verify.setEnabled(!busy);
    developer.add(verify);
    JMenuItem tests = hubMenuItem(
        "Run Full Test Suite",
        "Execute every automated test.",
        VectorIcon.Kind.HEALTH,
        ACCENT_DEV,
        () -> clickIfAvailable(runTestsButton));
    tests.setEnabled(!busy);
    developer.add(tests);
    developer.addSeparator();
    developer.add(hubMenuItem(
        "Configure Gradle",
        "Configure stacktraces, logging, dependency, cache, daemon, and custom flags.",
        VectorIcon.Kind.SLIDERS,
        ACCENT_DEV,
        this::showGradleOptionsDialog));
    developer.add(hubMenuItem(
        "Inspect Engine Diagnostics",
        "Run the Hub health report.",
        VectorIcon.Kind.HEALTH,
        ACCENT_DEV,
        this::showDiagnosticsReport));
    return developer;
  }

  private JMenu buildSafeModeMenu() {
    boolean busy = runningProcess.get() != null;
    JMenu safe = hubMenu("Safe Mode", ACCENT_SAFE, KeyEvent.VK_A);
    safe.add(menuStatusCard(
        "Safe Mode active",
        "Guarded launches · update recovery · preserved caches",
        ACCENT_SAFE));
    safe.addSeparator();
    JMenuItem runEditor = hubMenuItem(
        "Run Editor with Guardrails",
        "Launch the editor with safe-mode flags.",
        VectorIcon.Kind.SHIELD,
        ACCENT_SAFE,
        () -> clickIfAvailable(runEditorButton));
    runEditor.setEnabled(!busy);
    safe.add(runEditor);
    JMenuItem update = hubMenuItem(
        "Update Stable with Recovery",
        "Update with autostash and interrupted-operation recovery.",
        VectorIcon.Kind.REFRESH,
        ACCENT_SAFE,
        () -> clickIfAvailable(updateEngineButton));
    update.setEnabled(!busy);
    safe.add(update);
    safe.addSeparator();
    safe.add(hubMenuItem(
        "Recheck Workspace Health",
        "Run workspace and repository diagnostics.",
        VectorIcon.Kind.HEALTH,
        ACCENT_SAFE,
        this::showDiagnosticsReport));
    safe.add(hubMenuItem(
        "Open Recovery Logs",
        "Open update and process recovery logs.",
        VectorIcon.Kind.DOCUMENTATION,
        ACCENT_SAFE,
        () -> revealHubFolder("logs", Paths.get(System.getProperty("user.home", "."), ".jvn", "logs"))));
    JMenuItem cancel = hubMenuItem(
        "Cancel Running Task",
        busy ? "Stop the active safe-mode process." : "No Hub-managed process is running.",
        VectorIcon.Kind.STOP,
        ACCENT_ERROR,
        this::cancelRunning);
    cancel.setEnabled(busy);
    safe.add(cancel);
    return safe;
  }

  private JMenu buildRenderPipelineMenu() {
    boolean busy = runningProcess.get() != null;
    JMenu render = hubMenu("Render Pipeline", ACCENT_RENDER, KeyEvent.VK_R);
    render.add(menuStatusCard(
        "Next launch · " + renderPipelineMode.displayName(),
        renderPipelineMode.backendOrder(System.getProperty("os.name", "")),
        ACCENT_RENDER));
    render.add(menuStatusCard(
        "VSync " + (renderPipelineOptions.vsync() ? "on" : "off")
            + " · Dirty regions " + (renderPipelineOptions.dirtyRegions() ? "on" : "off"),
        "Preview budget · " + RenderPipelineSettings.previewBudgetSummary(renderPipelineOptions),
        ACCENT_RENDER));
    if (renderPipelineOptions.diagnosticsEnabled()) {
      render.add(menuStatusCard(
          "Render diagnostics active",
          "Visualization and logging may reduce frame rate",
          ACCENT_ERROR));
    }
    render.addSeparator();

    ButtonGroup profiles = new ButtonGroup();
    addRenderPipelineChoice(
        render,
        profiles,
        "Adaptive Selection (Recommended)",
        RenderPipelineSettings.Mode.AUTO);
    addRenderPipelineChoice(
        render,
        profiles,
        "GPU Preferred (Safe Fallback)",
        RenderPipelineSettings.Mode.HARDWARE);
    addRenderPipelineChoice(
        render,
        profiles,
        "Software Compatibility",
        RenderPipelineSettings.Mode.SOFTWARE);

    render.addSeparator();
    JMenu budget = hubMenu("Preview Frame Budget", ACCENT_RENDER);
    ButtonGroup budgets = new ButtonGroup();
    int[] frameRates = {0, 30, 60, 120};
    String[] budgetLabels = {"Automatic (Recommended)", "Efficient — 30 FPS", "Balanced — 60 FPS", "High Refresh — 120 FPS"};
    for (int i = 0; i < frameRates.length; i++) {
      int fps = frameRates[i];
      JRadioButtonMenuItem choice = new HelpRadioButtonMenuItem(budgetLabels[i], renderPipelineOptions.previewMaxFps() == fps);
      styleMenuItem(choice, ACCENT_RENDER);
      choice.setToolTipText("Caps editor preview draw work. Display refresh and the editor frame limit still apply; game runtime is unchanged.");
      choice.addActionListener(event -> setRenderPipelineOptions(
          renderPipelineOptions.withPreviewMaxFps(fps), "Preview frame budget"));
      budgets.add(choice);
      budget.add(choice);
    }
    render.add(budget);
    JMenu performance = hubMenu("Advanced Rendering", ACCENT_RENDER);
    performance.setToolTipText("Tune presentation, redraw, culling, and vector-shape caching.");
    JCheckBoxMenuItem vsync = hubCheckMenuItem(
        "Display Synchronization (VSync)",
        "Synchronize Prism presentation to the display refresh cycle.",
        ACCENT_RENDER,
        renderPipelineOptions.vsync());
    vsync.addActionListener(e -> setRenderPipelineOptions(
        renderPipelineOptions.withVsync(vsync.isSelected()),
        "Display synchronization"));
    performance.add(vsync);

    JCheckBoxMenuItem dirtyRegions = hubCheckMenuItem(
        "Dirty-Region Rendering",
        "Redraw changed areas instead of repainting the whole scene.",
        ACCENT_RENDER,
        renderPipelineOptions.dirtyRegions());
    dirtyRegions.addActionListener(e -> setRenderPipelineOptions(
        renderPipelineOptions.withDirtyRegions(dirtyRegions.isSelected()),
        "Dirty-region rendering"));
    performance.add(dirtyRegions);

    JCheckBoxMenuItem occlusion = hubCheckMenuItem(
        "Occlusion Culling",
        "Skip obscured content while dirty-region rendering is active.",
        ACCENT_RENDER,
        renderPipelineOptions.occlusionCulling());
    occlusion.setEnabled(renderPipelineOptions.dirtyRegions());
    occlusion.addActionListener(e -> setRenderPipelineOptions(
        renderPipelineOptions.withOcclusionCulling(occlusion.isSelected()),
        "Occlusion culling"));
    performance.add(occlusion);

    JMenu shapeCache = hubMenu("Shape Cache", ACCENT_RENDER);
    shapeCache.setToolTipText("Choose how aggressively Prism caches vector shapes.");
    ButtonGroup cacheChoices = new ButtonGroup();
    addShapeCacheChoice(shapeCache, cacheChoices, RenderPipelineSettings.ShapeCache.COMPLEX);
    addShapeCacheChoice(shapeCache, cacheChoices, RenderPipelineSettings.ShapeCache.ALL);
    addShapeCacheChoice(shapeCache, cacheChoices, RenderPipelineSettings.ShapeCache.OFF);
    performance.add(shapeCache);
    render.add(performance);

    JMenu diagnostics = hubMenu("Render Diagnostics", ACCENT_RENDER);
    diagnostics.setToolTipText("Temporarily inspect Prism startup, redraw, and overdraw behavior.");
    JCheckBoxMenuItem verbose = hubCheckMenuItem(
        "Verbose Pipeline Startup",
        "Print JavaFX Prism pipeline selection and capabilities at startup.",
        ACCENT_RENDER,
        renderPipelineOptions.verbose());
    verbose.addActionListener(e -> setRenderPipelineOptions(
        renderPipelineOptions.withVerbose(verbose.isSelected()),
        "Verbose pipeline startup"));
    diagnostics.add(verbose);

    JCheckBoxMenuItem showDirty = hubCheckMenuItem(
        "Visualize Dirty Regions",
        "Overlay repainted regions during a short diagnostic session.",
        ACCENT_RENDER,
        renderPipelineOptions.showDirtyRegions());
    showDirty.addActionListener(e -> setRenderPipelineOptions(
        renderPipelineOptions.withShowDirtyRegions(showDirty.isSelected()),
        "Dirty-region visualization"));
    diagnostics.add(showDirty);

    JCheckBoxMenuItem showOverdraw = hubCheckMenuItem(
        "Visualize Overdraw",
        "Highlight repeatedly rendered pixels during a short diagnostic session.",
        ACCENT_RENDER,
        renderPipelineOptions.showOverdraw());
    showOverdraw.addActionListener(e -> setRenderPipelineOptions(
        renderPipelineOptions.withShowOverdraw(showOverdraw.isSelected()),
        "Overdraw visualization"));
    diagnostics.add(showOverdraw);

    JCheckBoxMenuItem printGraph = hubCheckMenuItem(
        "Print Render Graph",
        "Capture JavaFX slow-pulse render trees for the Hub viewer on the next launch.",
        ACCENT_RENDER,
        renderPipelineOptions.printRenderGraph());
    printGraph.addActionListener(e -> setRenderPipelineOptions(
        renderPipelineOptions.withPrintRenderGraph(printGraph.isSelected()),
        "Render-graph logging"));
    diagnostics.add(printGraph);
    diagnostics.addSeparator();
    diagnostics.add(hubMenuItem(
        "Open Render Graph Viewer...",
        "View, refresh, copy, or clear the latest captured JavaFX render tree.",
        VectorIcon.Kind.INFO,
        ACCENT_RENDER,
        this::showRenderGraphViewer));
    JMenuItem profileEditor = hubMenuItem(
        "Launch Editor with Java Flight Recorder...",
        "Record CPU, allocation, GC, and JavaFX submission activity while using the selected GPU pipeline.",
        VectorIcon.Kind.PLAY,
        ACCENT_RENDER,
        () -> guardedRun("Profile Editor", this::runProfiledEditor));
    profileEditor.setEnabled(!busy);
    diagnostics.add(profileEditor);
    JMenuItem disableDiagnostics = hubMenuItem(
        "Disable All Render Diagnostics",
        renderPipelineOptions.diagnosticsEnabled()
            ? "Turn off every performance-sensitive render overlay and log."
            : "No render diagnostics are currently active.",
        VectorIcon.Kind.STOP,
        ACCENT_RENDER,
        this::disableRenderDiagnostics);
    disableDiagnostics.setEnabled(renderPipelineOptions.diagnosticsEnabled());
    diagnostics.add(disableDiagnostics);
    render.add(diagnostics);

    JCheckBoxMenuItem glxRecovery = hubCheckMenuItem(
        "Automatic Mesa GLX Recovery (Linux)",
        "Retry a broken default GLX provider with Mesa during managed Linux launches.",
        ACCENT_RENDER,
        renderPipelineOptions.linuxGlxRecovery());
    glxRecovery.setEnabled(System.getProperty("os.name", "")
        .toLowerCase(Locale.ROOT)
        .contains("linux"));
    glxRecovery.addActionListener(e -> setRenderPipelineOptions(
        renderPipelineOptions.withLinuxGlxRecovery(glxRecovery.isSelected()),
        "Linux GLX recovery"));
    render.add(glxRecovery);

    render.addSeparator();
    JMenuItem launch = hubMenuItem(
        "Launch Editor with This Pipeline",
        "Start the editor with the selected pipeline and tuning options.",
        VectorIcon.Kind.PLAY,
        ACCENT_RENDER,
        () -> clickIfAvailable(runEditorButton));
    launch.setEnabled(!busy);
    render.add(launch);
    JMenuItem probe = hubMenuItem("Check Actual Renderer", "Start JavaFX briefly and report the initialized backend in the console.",
        VectorIcon.Kind.HEALTH, ACCENT_RENDER, () -> runGradle(":editor:probeGraphics", "Check Actual Renderer"));
    probe.setEnabled(!busy);
    render.add(probe);
    render.add(hubMenuItem(
        "Inspect Render Stack...",
        "Show the selected backends, active tuning, and host graphics environment.",
        VectorIcon.Kind.INFO,
        ACCENT_RENDER,
        this::showRenderPipelineReport));
    render.add(hubMenuItem(
        "Copy Render Stack Summary",
        "Copy a support-ready rendering report.",
        VectorIcon.Kind.CHECK,
        ACCENT_RENDER,
        this::copyRenderPipelineSummary));
    render.addSeparator();
    render.add(hubMenuItem(
        "Open Render Pipeline Settings",
        "Edit the persisted rendering preferences file.",
        VectorIcon.Kind.EDIT,
        ACCENT_RENDER,
        this::openRenderPipelinePreferences));
    render.add(hubMenuItem(
        "Reset All Rendering Defaults",
        "Restore adaptive selection and all recommended performance settings.",
        VectorIcon.Kind.REFRESH,
        ACCENT_RENDER,
        this::resetRenderPipelineDefaults));
    return render;
  }

  private void addRenderPipelineChoice(
      JMenu menu,
      ButtonGroup profiles,
      String label,
      RenderPipelineSettings.Mode mode) {
    JRadioButtonMenuItem item = new HelpRadioButtonMenuItem(label, renderPipelineMode == mode);
    styleMenuItem(item, ACCENT_RENDER);
    item.setToolTipText(mode.description());
    item.addActionListener(e -> setRenderPipelineMode(mode));
    profiles.add(item);
    menu.add(item);
  }

  private void addShapeCacheChoice(
      JMenu menu,
      ButtonGroup choices,
      RenderPipelineSettings.ShapeCache cache) {
    JRadioButtonMenuItem item = new HelpRadioButtonMenuItem(
        cache.displayName(),
        renderPipelineOptions.shapeCache() == cache);
    styleMenuItem(item, ACCENT_RENDER);
    item.setToolTipText(switch (cache) {
      case COMPLEX -> "Cache complex vector shapes while leaving simple shapes on the direct path.";
      case ALL -> "Cache simple and complex shapes; may trade additional memory for lower rasterization work.";
      case OFF -> "Disable Prism shape caching for compatibility or cache-related diagnostics.";
    });
    item.addActionListener(e -> setRenderPipelineOptions(
        renderPipelineOptions.withShapeCache(cache),
        "Shape cache"));
    choices.add(item);
    menu.add(item);
  }

  private JMenu buildUiScaleMenu() {
    JMenu scale = hubMenu("UI Scale", TEXT_SOFT);
    scale.setBackground(PANEL_BG);
    scale.setToolTipText("Resize the complete Hub interface or fit it automatically to this display.");
    ButtonGroup choices = new ButtonGroup();
    boolean automatic = automaticUiScaleSelected();
    addScaleChoice(scale, choices, "Auto (Fit Display)", Double.NaN, automatic);
    addScaleChoice(scale, choices, "Compact (75%)", 0.75, !automatic && nearScale(0.75));
    addScaleChoice(scale, choices, "Small (85%)", 0.85, !automatic && nearScale(0.85));
    addScaleChoice(scale, choices, "Default (100%)", 1.0, !automatic && nearScale(1.0));
    addScaleChoice(scale, choices, "Large (125%)", 1.25, !automatic && nearScale(1.25));
    scale.addSeparator();
    JMenuItem custom = hubMenuItem(
        "Custom Scale...",
        "Enter a fixed Hub scale from 75% through 185%.",
        VectorIcon.Kind.SLIDERS,
        TEXT_SOFT,
        this::showCustomUiScaleDialog);
    custom.setEnabled(runningProcess.get() == null);
    scale.add(custom);
    return scale;
  }

  private JMenu buildProjectIconThemeMenu() {
    ProjectIconThemeSettings.Options options = projectIconOptions;
    JMenu icons = hubMenu("Project Explorer Icons", ACCENT_GREEN);
    icons.setToolTipText(
        "Choose the icon source, installed Linux theme, size, semantic variants, and fallback behavior.");
    icons.add(menuStatusCard(
        options.source().displayName(),
        ProjectIconThemeSettings.resolvedTheme(options) + " · " + options.size() + " px",
        ACCENT_GREEN));
    icons.add(menuStatusCard(
        "Detected desktop theme",
        ProjectIconThemeSettings.detectedDesktopTheme(),
        TEXT_SOFT));
    icons.addSeparator();

    JMenu source = hubMenu("Icon Source", ACCENT_GREEN);
    ButtonGroup sourceChoices = new ButtonGroup();
    for (ProjectIconThemeSettings.Source candidate : ProjectIconThemeSettings.Source.values()) {
      JRadioButtonMenuItem item = new HelpRadioButtonMenuItem(
          candidate.displayName(),
          options.source() == candidate);
      styleMenuItem(item, ACCENT_GREEN);
      item.setToolTipText(candidate.description());
      item.addActionListener(e -> {
        ProjectIconThemeSettings.Options requested = projectIconOptions.withSource(candidate);
        if (candidate == ProjectIconThemeSettings.Source.THEME && requested.theme().isBlank()) {
          requested = requested.withTheme(ProjectIconThemeSettings.detectedDesktopTheme());
        }
        setProjectIconOptions(requested, "Icon source");
      });
      sourceChoices.add(item);
      source.add(item);
    }
    icons.add(source);

    JMenu size = hubMenu("Icon Size", ACCENT_GREEN);
    ButtonGroup sizeChoices = new ButtonGroup();
    addProjectIconSizeChoice(size, sizeChoices, "Compact (14 px)", 14);
    addProjectIconSizeChoice(size, sizeChoices, "Small (16 px)", 16);
    addProjectIconSizeChoice(size, sizeChoices, "Standard (18 px)", 18);
    addProjectIconSizeChoice(size, sizeChoices, "Comfortable (20 px)", 20);
    addProjectIconSizeChoice(size, sizeChoices, "Large (22 px)", 22);
    size.addSeparator();
    size.add(hubMenuItem(
        "Custom Size...",
        "Enter any Project Explorer icon size from 12 through 28 pixels.",
        VectorIcon.Kind.SLIDERS,
        ACCENT_GREEN,
        this::showProjectIconThemeDialog));
    icons.add(size);

    icons.addSeparator();
    JCheckBoxMenuItem folderVariants = hubCheckMenuItem(
        "Semantic Folder Icons",
        "Use specialized artwork for assets, audio, exports, documents, and other known folders.",
        ACCENT_GREEN,
        options.folderVariants());
    folderVariants.addActionListener(e -> setProjectIconOptions(
        projectIconOptions.withFolderVariants(folderVariants.isSelected()),
        "Semantic folder icons"));
    icons.add(folderVariants);

    JCheckBoxMenuItem fileVariants = hubCheckMenuItem(
        "File-Type Icons",
        "Use distinct theme icons for source, image, audio, archive, document, and executable files.",
        ACCENT_GREEN,
        options.fileTypeVariants());
    fileVariants.addActionListener(e -> setProjectIconOptions(
        projectIconOptions.withFileTypeVariants(fileVariants.isSelected()),
        "File-type icons"));
    icons.add(fileVariants);

    JCheckBoxMenuItem inheritance = hubCheckMenuItem(
        "Follow Theme Inheritance",
        "Search inherited themes, Adwaita, and hicolor when the selected theme lacks an icon.",
        ACCENT_GREEN,
        options.inheritTheme());
    inheritance.addActionListener(e -> setProjectIconOptions(
        projectIconOptions.withInheritTheme(inheritance.isSelected()),
        "Theme inheritance"));
    icons.add(inheritance);

    JCheckBoxMenuItem bundledFallback = hubCheckMenuItem(
        "Use JVN Default Fallback Icons",
        "Use JVN's previous bundled SVG pack when a Linux theme has no JavaFX-compatible PNG entry.",
        ACCENT_GREEN,
        options.bundledFallback());
    bundledFallback.addActionListener(e -> setProjectIconOptions(
        projectIconOptions.withBundledFallback(bundledFallback.isSelected()),
        "Bundled icon fallback"));
    icons.add(bundledFallback);

    JCheckBoxMenuItem smooth = hubCheckMenuItem(
        "Smooth Icon Scaling",
        "Use filtered image scaling; disable it for crisp pixel-oriented icon packs.",
        ACCENT_GREEN,
        options.smoothScaling());
    smooth.addActionListener(e -> setProjectIconOptions(
        projectIconOptions.withSmoothScaling(smooth.isSelected()),
        "Icon scaling"));
    icons.add(smooth);

    icons.addSeparator();
    icons.add(hubMenuItem(
        "Configure and Preview...",
        "Open the complete Project Explorer icon-theme configuration and live preview.",
        VectorIcon.Kind.SLIDERS,
        ACCENT_GREEN,
        this::showProjectIconThemeDialog));
    icons.add(hubMenuItem(
        "Open Settings File",
        "Open the shared project-icons.properties file used by the editor.",
        VectorIcon.Kind.EDIT,
        ACCENT_GREEN,
        this::openProjectIconSettingsFile));
    icons.add(hubMenuItem(
        "Restore Icon Defaults",
        "Follow the desktop theme at 18 px with semantic icons and safe fallbacks.",
        VectorIcon.Kind.REFRESH,
        ACCENT_GREEN,
        this::resetProjectIconOptions));
    return icons;
  }

  private void addProjectIconSizeChoice(
      JMenu menu,
      ButtonGroup choices,
      String label,
      int iconSize) {
    JRadioButtonMenuItem item = new HelpRadioButtonMenuItem(
        label,
        projectIconOptions.size() == iconSize);
    styleMenuItem(item, ACCENT_GREEN);
    item.setToolTipText("Render Project Explorer icons at " + iconSize + " logical pixels.");
    item.addActionListener(e -> setProjectIconOptions(
        projectIconOptions.withSize(iconSize),
        "Project icon size"));
    choices.add(item);
    menu.add(item);
  }

  private void showProjectIconThemeDialog() {
    JDialog dialog = new JDialog(frame, "Project Explorer Icon Theme", true);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    JPanel root = new JPanel(new BorderLayout(0, ui(12)));
    root.setBackground(BG);
    root.setBorder(uiPadding(16, 16, 16, 16));
    root.add(dialogHeader(
        "Project Explorer Icon Theme",
        "Configure Linux desktop artwork used across every editor file and project browser."),
        BorderLayout.NORTH);

    List<String> installedThemes = new ArrayList<>(ProjectIconThemeSettings.installedThemes());
    if (!projectIconOptions.theme().isBlank()
        && installedThemes.stream().noneMatch(projectIconOptions.theme()::equalsIgnoreCase)) {
      installedThemes.add(projectIconOptions.theme());
      installedThemes.sort(String.CASE_INSENSITIVE_ORDER);
    }
    if (installedThemes.isEmpty()) installedThemes.add(ProjectIconThemeSettings.detectedDesktopTheme());

    JComboBox<String> source = projectIconCombo(List.of(
        ProjectIconThemeSettings.Source.DESKTOP.displayName(),
        ProjectIconThemeSettings.Source.THEME.displayName(),
        ProjectIconThemeSettings.Source.BUNDLED.displayName()));
    source.setToolTipText(
        "Desktop follows GTK, Installed Theme locks a theme name, and JVN Defaults uses the previous SVG pack.");
    source.setSelectedIndex(projectIconOptions.source().ordinal());

    JComboBox<String> theme = projectIconCombo(installedThemes);
    theme.setEditable(true);
    theme.setToolTipText("Select a detected theme or enter its exact freedesktop directory name.");
    if (theme.getEditor().getEditorComponent() instanceof JTextField themeEditor) {
      themeEditor.setBackground(BG);
      themeEditor.setForeground(TEXT_PRIMARY);
      themeEditor.setCaretColor(TEXT_PRIMARY);
    }
    String selectedTheme = projectIconOptions.theme().isBlank()
        ? ProjectIconThemeSettings.detectedDesktopTheme()
        : projectIconOptions.theme();
    theme.setSelectedItem(selectedTheme);

    JSpinner size = new JSpinner(new SpinnerNumberModel(
        projectIconOptions.size(),
        ProjectIconThemeSettings.MIN_ICON_SIZE,
        ProjectIconThemeSettings.MAX_ICON_SIZE,
        1));
    size.setToolTipText("Logical Project Explorer icon size, from 12 through 28 pixels.");
    styleProjectIconSpinner(size);

    JCheckBox folderVariants = optionCheckBox(
        "Semantic folder icons",
        "Differentiate assets, audio, exports, documents, and other recognized folders.",
        projectIconOptions.folderVariants());
    JCheckBox fileVariants = optionCheckBox(
        "File-type icons",
        "Differentiate source, image, audio, archive, document, and executable files.",
        projectIconOptions.fileTypeVariants());
    JCheckBox inheritance = optionCheckBox(
        "Follow freedesktop theme inheritance",
        "Search inherited themes plus Adwaita and hicolor for missing icons.",
        projectIconOptions.inheritTheme());
    JCheckBox bundledFallback = optionCheckBox(
        "Use JVN default SVGs as fallback icons",
        "Use the previous bundled Project Explorer pack when the selected desktop theme has no PNG icon.",
        projectIconOptions.bundledFallback());
    JCheckBox smooth = optionCheckBox(
        "Smooth image scaling",
        "Use filtered scaling for high-resolution desktop icons.",
        projectIconOptions.smoothScaling());

    JPanel form = new JPanel();
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
    form.setBackground(PANEL_BG);
    form.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER_NEUTRAL),
        uiPadding(12, 14, 12, 14)));
    form.add(projectIconField(
        "Icon source",
        "Follow the desktop, lock a named installed theme, or use JVN's bundled artwork.",
        source));
    form.add(Box.createVerticalStrut(ui(8)));
    form.add(projectIconField(
        "Installed Linux theme",
        installedThemes.size() + " freedesktop theme" + (installedThemes.size() == 1 ? "" : "s")
            + " detected. Current desktop: " + ProjectIconThemeSettings.detectedDesktopTheme(),
        theme));
    form.add(Box.createVerticalStrut(ui(8)));
    form.add(projectIconField(
        "Icon size",
        "Any logical size from 12 through 28 pixels.",
        size));
    form.add(Box.createVerticalStrut(ui(8)));
    form.add(folderVariants);
    form.add(fileVariants);
    form.add(inheritance);
    form.add(bundledFallback);
    form.add(smooth);

    JPanel preview = new JPanel(new FlowLayout(FlowLayout.LEFT, ui(16), ui(8)));
    preview.setBackground(PRESSED_BG);
    preview.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(ACCENT_GREEN.darker()),
        uiPadding(8, 10, 8, 10)));
    preview.setAlignmentX(Component.LEFT_ALIGNMENT);
    preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, ui(88)));

    JLabel previewStatus = new JLabel();
    previewStatus.setForeground(TEXT_MUTED);
    previewStatus.setFont(previewStatus.getFont().deriveFont(Font.PLAIN, uiFont(10f)));
    previewStatus.setAlignmentX(Component.LEFT_ALIGNMENT);

    JPanel center = new JPanel();
    center.setOpaque(false);
    center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
    form.setAlignmentX(Component.LEFT_ALIGNMENT);
    center.add(form);
    center.add(Box.createVerticalStrut(ui(10)));
    center.add(preview);
    center.add(Box.createVerticalStrut(ui(4)));
    center.add(previewStatus);
    JScrollPane settingsScroll = new JScrollPane(center);
    settingsScroll.setBorder(null);
    settingsScroll.setOpaque(false);
    settingsScroll.getViewport().setOpaque(false);
    settingsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    settingsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    settingsScroll.getVerticalScrollBar().setUnitIncrement(ui(18));
    styleScrollBar(settingsScroll.getVerticalScrollBar());
    root.add(settingsScroll, BorderLayout.CENTER);

    Runnable updatePreview = () -> {
      boolean installed = source.getSelectedIndex() == ProjectIconThemeSettings.Source.THEME.ordinal();
      theme.setEnabled(installed);
      ProjectIconThemeSettings.Options draft = projectIconOptionsFromControls(
          source, theme, size, folderVariants, fileVariants, inheritance, bundledFallback, smooth);
      updateProjectIconPreview(preview, previewStatus, draft);
    };
    source.addActionListener(e -> updatePreview.run());
    theme.addActionListener(e -> updatePreview.run());
    size.addChangeListener(e -> updatePreview.run());
    folderVariants.addActionListener(e -> updatePreview.run());
    fileVariants.addActionListener(e -> updatePreview.run());
    inheritance.addActionListener(e -> updatePreview.run());
    bundledFallback.addActionListener(e -> updatePreview.run());
    smooth.addActionListener(e -> updatePreview.run());
    updatePreview.run();

    FlatButton reset = new FlatButton(
        "Defaults",
        uiIcon(VectorIcon.Kind.REFRESH, 14, TEXT_SOFT),
        TEXT_SOFT);
    reset.addActionListener(e -> {
      ProjectIconThemeSettings.Options defaults = ProjectIconThemeSettings.Options.defaults();
      source.setSelectedIndex(defaults.source().ordinal());
      theme.setSelectedItem(ProjectIconThemeSettings.detectedDesktopTheme());
      size.setValue(defaults.size());
      folderVariants.setSelected(defaults.folderVariants());
      fileVariants.setSelected(defaults.fileTypeVariants());
      inheritance.setSelected(defaults.inheritTheme());
      bundledFallback.setSelected(defaults.bundledFallback());
      smooth.setSelected(defaults.smoothScaling());
      updatePreview.run();
    });

    FlatButton cancel = new FlatButton(
        "Cancel",
        uiIcon(VectorIcon.Kind.CLOSE, 14, TEXT_SOFT),
        TEXT_SOFT);
    cancel.addActionListener(e -> dialog.dispose());

    FlatButton apply = new FlatButton(
        "Apply Icon Theme",
        uiIcon(VectorIcon.Kind.CHECK, 14, ACCENT_GREEN),
        ACCENT_GREEN);
    apply.addActionListener(e -> {
      ProjectIconThemeSettings.Options requested = projectIconOptionsFromControls(
          source, theme, size, folderVariants, fileVariants, inheritance, bundledFallback, smooth);
      dialog.dispose();
      setProjectIconOptions(requested, "Project Explorer icon theme");
    });

    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, ui(8), 0));
    footer.setOpaque(false);
    footer.add(reset);
    footer.add(cancel);
    footer.add(apply);
    root.add(footer, BorderLayout.SOUTH);

    dialog.setContentPane(root);
    dialog.getRootPane().setDefaultButton(apply);
    dialog.pack();
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    int targetWidth = Math.min(ui(640), Math.max(ui(480), screen.width - ui(80)));
    int targetHeight = Math.min(ui(650), Math.max(ui(520), screen.height - ui(100)));
    dialog.setMinimumSize(new Dimension(Math.min(targetWidth, ui(560)), Math.min(targetHeight, ui(520))));
    dialog.setSize(new Dimension(
        targetWidth,
        targetHeight));
    dialog.setLocationRelativeTo(frame);
    dialog.setVisible(true);
  }

  private JComboBox<String> projectIconCombo(List<String> values) {
    JComboBox<String> combo = new JComboBox<>(values.toArray(String[]::new));
    styleHubComboBox(combo, uiDimension(420, 34));
    combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, ui(34)));
    combo.setAlignmentX(Component.LEFT_ALIGNMENT);
    return combo;
  }

  private void styleProjectIconSpinner(JSpinner spinner) {
    styleHubSpinner(spinner, uiDimension(420, 34));
    spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, ui(34)));
    spinner.setAlignmentX(Component.LEFT_ALIGNMENT);
  }

  private JPanel projectIconField(String labelText, String helpText, JComponent control) {
    JPanel field = new JPanel();
    field.setOpaque(false);
    field.setLayout(new BoxLayout(field, BoxLayout.Y_AXIS));
    JLabel label = new JLabel(labelText);
    label.setForeground(TEXT_SOFT);
    label.setFont(label.getFont().deriveFont(Font.BOLD, uiFont(11f)));
    JLabel help = new JLabel(helpText);
    help.setForeground(TEXT_MUTED);
    help.setFont(help.getFont().deriveFont(Font.PLAIN, uiFont(9.5f)));
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    help.setAlignmentX(Component.LEFT_ALIGNMENT);
    control.setAlignmentX(Component.LEFT_ALIGNMENT);
    field.setAlignmentX(Component.LEFT_ALIGNMENT);
    field.add(label);
    field.add(Box.createVerticalStrut(ui(3)));
    field.add(control);
    field.add(Box.createVerticalStrut(ui(3)));
    field.add(help);
    return field;
  }

  private ProjectIconThemeSettings.Options projectIconOptionsFromControls(
      JComboBox<String> source,
      JComboBox<String> theme,
      JSpinner size,
      JCheckBox folderVariants,
      JCheckBox fileVariants,
      JCheckBox inheritance,
      JCheckBox bundledFallback,
      JCheckBox smooth) {
    int sourceIndex = Math.max(0, source.getSelectedIndex());
    ProjectIconThemeSettings.Source[] sources = ProjectIconThemeSettings.Source.values();
    ProjectIconThemeSettings.Source selectedSource = sources[Math.min(sourceIndex, sources.length - 1)];
    Object themeValue = theme.getSelectedItem();
    return new ProjectIconThemeSettings.Options(
        selectedSource,
        themeValue == null ? "" : themeValue.toString(),
        ((Number) size.getValue()).intValue(),
        folderVariants.isSelected(),
        fileVariants.isSelected(),
        inheritance.isSelected(),
        bundledFallback.isSelected(),
        smooth.isSelected());
  }

  private void updateProjectIconPreview(
      JPanel preview,
      JLabel status,
      ProjectIconThemeSettings.Options options) {
    preview.removeAll();
    List<ProjectIconPreview> samples = List.of(
        new ProjectIconPreview("Folder", List.of("folder-pictures", "folder")),
        new ProjectIconPreview("Java", List.of("text-x-java-source", "text-x-generic")),
        new ProjectIconPreview("Image", List.of("image-x-generic", "text-x-generic")),
        new ProjectIconPreview("Script", List.of("text-x-script", "text-x-generic")));
    int resolved = 0;
    for (ProjectIconPreview sample : samples) {
      JLabel tile = new JLabel(sample.label(), SwingConstants.CENTER);
      tile.setForeground(TEXT_SOFT);
      tile.setFont(tile.getFont().deriveFont(Font.PLAIN, uiFont(10f)));
      tile.setHorizontalTextPosition(SwingConstants.CENTER);
      tile.setVerticalTextPosition(SwingConstants.BOTTOM);
      tile.setIconTextGap(ui(5));
      Optional<Path> artwork = ProjectIconThemeSettings.previewIcon(options, sample.names());
      if (artwork.isPresent()) {
        ImageIcon raw = new ImageIcon(artwork.get().toString());
        int hint = options.smoothScaling() ? Image.SCALE_SMOOTH : Image.SCALE_FAST;
        tile.setIcon(new ImageIcon(raw.getImage().getScaledInstance(
            ui(options.size()), ui(options.size()), hint)));
        resolved++;
      } else {
        tile.setIcon(uiIcon(VectorIcon.Kind.SLIDERS, options.size(), ACCENT_GREEN));
      }
      tile.setPreferredSize(uiDimension(76, 56));
      preview.add(tile);
    }
    String theme = ProjectIconThemeSettings.resolvedTheme(options);
    status.setText(options.source() == ProjectIconThemeSettings.Source.BUNDLED
        ? "JVN default SVG artwork · preview placeholders shown · applies on the next editor launch."
        : theme + " · " + resolved + "/" + samples.size()
            + " PNG samples resolved · applies on the next editor launch.");
    preview.revalidate();
    preview.repaint();
  }

  private void setProjectIconOptions(
      ProjectIconThemeSettings.Options requested,
      String changedSetting) {
    ProjectIconThemeSettings.Options options = requested == null
        ? ProjectIconThemeSettings.Options.defaults()
        : requested;
    try {
      ProjectIconThemeSettings.save(projectIconSettingsFile, options);
      projectIconOptions = options;
      String label = firstNonBlank(changedSetting, "Project icon setting");
      appendLog("[hub] project icon theme updated: " + ProjectIconThemeSettings.summary(options) + ".");
      setStatus(label + " updated", ACCENT_GREEN);
      setActivity(
          "Project Explorer icons updated",
          "The complete icon profile applies when the next editor process starts.",
          false,
          ACCENT_GREEN);
    } catch (IOException error) {
      appendLog("[hub] could not save project icon settings: " + error.getMessage());
      setStatus("Could not save Project Explorer icons", ACCENT_ERROR);
      setActivity(
          "Project icon theme unchanged",
          error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
          false,
          ACCENT_ERROR);
    }
    refreshModeMenus();
  }

  private void resetProjectIconOptions() {
    setProjectIconOptions(ProjectIconThemeSettings.Options.defaults(), "Project icon defaults");
  }

  private void openProjectIconSettingsFile() {
    if (!Files.isRegularFile(projectIconSettingsFile)) {
      try {
        ProjectIconThemeSettings.save(projectIconSettingsFile, projectIconOptions);
      } catch (IOException error) {
        setStatus("Could not create Project Explorer icon settings", ACCENT_ERROR);
        return;
      }
    }
    try {
      Desktop desktop = Desktop.getDesktop();
      desktop.open(projectIconSettingsFile.toFile());
      setStatus("Opened Project Explorer icon settings", ACCENT_GREEN);
    } catch (Exception error) {
      appendLog("[hub] could not open project icon settings: " + error.getMessage());
      setStatus("Could not open Project Explorer icon settings", ACCENT_ERROR);
    }
  }

  private record ProjectIconPreview(String label, List<String> names) {}

  private void addScaleChoice(JMenu menu, ButtonGroup choices, String label, double value, boolean selected) {
    JRadioButtonMenuItem item = new HelpRadioButtonMenuItem(label, selected);
    styleMenuItem(item, TEXT_SOFT);
    item.setToolTipText(Double.isFinite(value)
        ? "Use a fixed " + Math.round(value * 100.0) + "% Hub scale."
        : "Choose a comfortable Hub scale from the current display bounds.");
    item.setEnabled(runningProcess.get() == null);
    item.addActionListener(e -> applyUiScale(value));
    choices.add(item);
    menu.add(item);
  }

  private boolean nearScale(double value) {
    return Math.abs(activeUiScale - value) < 0.01;
  }

  private void showCustomUiScaleDialog() {
    if (runningProcess.get() != null) {
      setStatus("UI scale is locked while a task is running", ACCENT_ERROR);
      return;
    }

    JDialog dialog = new JDialog(frame, "Custom UI Scale", true);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    dialog.setResizable(false);

    JPanel root = new JPanel(new BorderLayout(0, ui(14)));
    root.setBackground(BG);
    root.setBorder(uiPadding(16, 16, 16, 16));
    root.add(dialogHeader(
        "Custom UI Scale",
        "Set a fixed Engine Hub scale between 75% and 185%."), BorderLayout.NORTH);

    JPanel form = new JPanel();
    form.setOpaque(true);
    form.setBackground(PANEL_BG);
    form.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER_NEUTRAL),
        uiPadding(12, 14, 12, 14)));
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

    JLabel inputLabel = new JLabel("Scale value");
    inputLabel.setForeground(TEXT_SOFT);
    inputLabel.setFont(inputLabel.getFont().deriveFont(Font.BOLD, uiFont(11f)));
    inputLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

    JTextField input = gradleTextField(String.format(Locale.ROOT, "%.0f%%", activeUiScale * 100.0));
    input.setToolTipText("Examples: 125, 125%, or 1.25");
    input.setAlignmentX(Component.LEFT_ALIGNMENT);
    input.setPreferredSize(uiDimension(300, 38));
    input.setMaximumSize(new Dimension(Integer.MAX_VALUE, ui(38)));
    input.selectAll();

    JLabel help = new JLabel("Examples: 125 · 125% · 1.25");
    help.setForeground(TEXT_MUTED);
    help.setFont(help.getFont().deriveFont(Font.PLAIN, uiFont(10f)));
    help.setAlignmentX(Component.LEFT_ALIGNMENT);

    JLabel validation = new JLabel(" ");
    validation.setForeground(ACCENT_ERROR);
    validation.setFont(validation.getFont().deriveFont(Font.BOLD, uiFont(10f)));
    validation.setAlignmentX(Component.LEFT_ALIGNMENT);

    form.add(inputLabel);
    form.add(Box.createVerticalStrut(ui(6)));
    form.add(input);
    form.add(Box.createVerticalStrut(ui(6)));
    form.add(help);
    form.add(Box.createVerticalStrut(ui(4)));
    form.add(validation);
    root.add(form, BorderLayout.CENTER);

    FlatButton cancel = new FlatButton(
        "Cancel",
        uiIcon(VectorIcon.Kind.CLOSE, 14, TEXT_SOFT),
        TEXT_SOFT);
    cancel.addActionListener(e -> dialog.dispose());

    FlatButton apply = new FlatButton(
        "Apply Scale",
        uiIcon(VectorIcon.Kind.CHECK, 14, ACCENT_NEUTRAL),
        ACCENT_NEUTRAL);
    apply.addActionListener(e -> {
      double scale = parseCustomUiScale(input.getText());
      if (!Double.isFinite(scale)) {
        validation.setText("Enter a value from 75% to 185%.");
        input.requestFocusInWindow();
        input.selectAll();
        return;
      }
      dialog.dispose();
      SwingUtilities.invokeLater(() -> applyUiScale(scale));
    });

    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, ui(8), 0));
    footer.setOpaque(false);
    footer.add(cancel);
    footer.add(apply);
    root.add(footer, BorderLayout.SOUTH);

    dialog.setContentPane(root);
    dialog.getRootPane().setDefaultButton(apply);
    dialog.setSize(uiDimension(430, 300));
    dialog.setLocationRelativeTo(frame);
    dialog.setVisible(true);
  }

  static double parseCustomUiScale(String rawValue) {
    if (rawValue == null || rawValue.isBlank()) return Double.NaN;
    String value = rawValue.trim().replace(" ", "");
    boolean percent = value.endsWith("%");
    if (percent) value = value.substring(0, value.length() - 1);
    if (value.indexOf(',') >= 0 && value.indexOf('.') < 0) value = value.replace(',', '.');
    try {
      double parsed = Double.parseDouble(value);
      if (!Double.isFinite(parsed)) return Double.NaN;
      double scale = percent || parsed > MAX_UI_SCALE ? parsed / 100.0 : parsed;
      return scale >= MIN_UI_SCALE && scale <= MAX_UI_SCALE ? scale : Double.NaN;
    } catch (NumberFormatException ignored) {
      return Double.NaN;
    }
  }

  private String performanceVisibilitySummary() {
    if (performanceGraphVisible && performanceChipsVisible) return "Graph and metric chips shown";
    if (performanceGraphVisible) return "Graph shown · metric chips hidden";
    if (performanceChipsVisible) return "Graph hidden · metric chips shown";
    return "Hidden · performance sampling paused";
  }

  private void setPerformanceGraphVisible(boolean visible) {
    performanceGraphVisible = visible;
    updatePerformanceVisibility("Performance graph " + (visible ? "shown" : "hidden"));
  }

  private void setPerformanceChipsVisible(boolean visible) {
    performanceChipsVisible = visible;
    updatePerformanceVisibility("Performance metric chips " + (visible ? "shown" : "hidden"));
  }

  private void updatePerformanceVisibility(String status) {
    savePerformanceVisibility();
    HubPerformancePanel monitor = performancePanel.get();
    if (monitor != null) monitor.applyVisibility(performanceGraphVisible, performanceChipsVisible);
    setStatus(status, ACCENT_TOOLS);
    refreshModeMenus();
  }

  private void setTooltipsEnabled(boolean enabled) {
    tooltipsEnabled = enabled;
    configureToolTipManager(enabled);
    saveTooltipVisibility();
    setStatus("Tooltips " + (enabled ? "shown" : "hidden"), ACCENT_NEUTRAL);
    refreshModeMenus();
  }

  static void configureToolTipManager(boolean enabled) {
    ToolTipManager.sharedInstance().setEnabled(enabled);
  }

  private void applyUiScale(double requestedScale) {
    if (runningProcess.get() != null) {
      appendLog("[hub] UI scale cannot change while an action is running.");
      return;
    }
    boolean automatic = !Double.isFinite(requestedScale);
    double newScale = automatic ? DISPLAY_PROFILE.uiScale() : HubDisplayProfile.clampScale(requestedScale);
    saveUiScale(automatic ? "auto" : String.format(Locale.ROOT, "%.2f", newScale));
    if (Math.abs(activeUiScale - newScale) < 0.01) {
      frame.setJMenuBar(buildMenuBar());
      return;
    }

    activeUiScale = newScale;
    actionButtons.clear();
    activityPanel.removeAll();
    buildUi();
    setDeveloperModeEnabled(developerModeEnabled);
    setSafeModeEnabled(safeModeEnabled);
    frame.revalidate();
    frame.repaint();
    appendLog("[hub] UI scale changed to " + String.format(Locale.ROOT, "%.0f%%", newScale * 100.0) + ".");
  }

  private static JMenu hubMenu(String text, Color accent) {
    return hubMenu(text, accent, 0);
  }

  private static JMenu hubMenu(String text, Color accent, int mnemonic) {
    Color tone = accent == null ? TEXT_PRIMARY : accent;
    JMenu menu = new HelpMenu(text);
    menu.setOpaque(true);
    menu.setBackground(BG_TOP);
    menu.setForeground(tone);
    menu.setFont(menu.getFont().deriveFont(Font.PLAIN, uiFont(12f)));
    if (mnemonic > 0) menu.setMnemonic(mnemonic);
    menu.getPopupMenu().setOpaque(true);
    menu.getPopupMenu().setBackground(PANEL_BG);
    menu.getPopupMenu().setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(ui(2), ui(1), ui(1), ui(1), tone.darker()),
        uiPadding(4, 3, 4, 3)));
    menu.putClientProperty("jvn.menu.accent", tone);
    return menu;
  }

  private static JMenuItem hubMenuItem(
      String text,
      String tooltip,
      VectorIcon.Kind iconKind,
      Color accent,
      Runnable action) {
    JMenuItem item = new HelpMenuItem(text);
    styleMenuItem(item, accent);
    if (tooltip != null && !tooltip.isBlank()) item.setToolTipText(tooltip);
    if (iconKind != null) item.setIcon(uiIcon(iconKind, 13, accent == null ? TEXT_SOFT : accent));
    item.addActionListener(e -> action.run());
    return item;
  }

  private static JCheckBoxMenuItem hubCheckMenuItem(
      String text,
      String tooltip,
      Color accent,
      boolean selected) {
    JCheckBoxMenuItem item = new HelpCheckBoxMenuItem(text, selected);
    styleMenuItem(item, accent);
    if (tooltip != null && !tooltip.isBlank()) item.setToolTipText(tooltip);
    return item;
  }

  private static void styleMenuItem(JMenuItem item, Color accent) {
    item.setOpaque(true);
    item.setBackground(PANEL_BG);
    item.setForeground(TEXT_PRIMARY);
    item.setFont(item.getFont().deriveFont(Font.PLAIN, uiFont(12f)));
    item.setBorder(uiPadding(5, 8, 5, 10));
    item.setBorderPainted(false);
    item.putClientProperty("jvn.menu.accent", accent == null ? TEXT_SOFT : accent);
  }

  private static JComponent menuStatusCard(String titleText, String detailText, Color accent) {
    Color tone = accent == null ? ACCENT_NEUTRAL : accent;
    JPanel card = new JPanel(new BorderLayout(0, ui(2)));
    card.setBackground(PRESSED_BG);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, ui(3), 0, 0, tone),
        uiPadding(7, 10, 7, 10)));

    JLabel title = new JLabel(firstNonBlank(titleText, "Status"));
    title.setForeground(tone);
    title.setFont(title.getFont().deriveFont(Font.BOLD, uiFont(11f)));
    JLabel detail = new JLabel(firstNonBlank(detailText, "Ready"));
    detail.setForeground(TEXT_MUTED);
    detail.setFont(detail.getFont().deriveFont(Font.PLAIN, uiFont(9.5f)));
    card.add(title, BorderLayout.NORTH);
    card.add(detail, BorderLayout.CENTER);
    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, ui(48)));
    card.setPreferredSize(uiDimension(310, 48));
    return card;
  }

  private static JMenuItem withAccelerator(JMenuItem item, int keyCode, int modifiers) {
    if (item != null) item.setAccelerator(KeyStroke.getKeyStroke(keyCode, modifiers));
    return item;
  }

  private static int menuShortcutMask() {
    try {
      return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    } catch (java.awt.HeadlessException ignored) {
      return java.awt.event.InputEvent.CTRL_DOWN_MASK;
    }
  }

  private static String compactMenuPath(Path path) {
    if (path == null) return "Path unavailable";
    String text = path.toAbsolutePath().normalize().toString();
    int max = 54;
    return text.length() <= max ? text : "…" + text.substring(text.length() - max + 1);
  }

  private static Dimension contextHelpPreferredSize(JMenuItem item, Dimension base) {
    if (!hasContextHelp(item)) return base;
    return new Dimension(base.width + ui(24), base.height);
  }

  private static void paintContextHelpIndicator(JMenuItem item, Graphics graphics) {
    if (!hasContextHelp(item)) return;
    AeroHelpIcon icon = new AeroHelpIcon(ui(16));
    int trailing = ui(7);
    if (item instanceof JMenu) trailing += ui(13);
    KeyStroke accelerator = item.getAccelerator();
    if (accelerator != null) {
      String modifier = java.awt.event.InputEvent.getModifiersExText(accelerator.getModifiers());
      String key = KeyEvent.getKeyText(accelerator.getKeyCode());
      String label = modifier.isBlank() ? key : modifier + "+" + key;
      trailing += item.getFontMetrics(item.getFont()).stringWidth(label) + ui(12);
    }
    int x = Math.max(ui(4), item.getWidth() - trailing - icon.getIconWidth());
    int y = Math.max(0, (item.getHeight() - icon.getIconHeight()) / 2);
    if (item.getModel().isArmed()) {
      Graphics2D glow = (Graphics2D) graphics.create();
      glow.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      glow.setColor(new Color(169, 229, 251, 55));
      glow.fillOval(x - ui(2), y - ui(2), icon.getIconWidth() + ui(4), icon.getIconHeight() + ui(4));
      glow.dispose();
    }
    icon.paintIcon(item, graphics, x, y);
  }

  static boolean hasContextHelp(JMenuItem item) {
    return ToolTipManager.sharedInstance().isEnabled()
        && item != null
        && item.getToolTipText() != null
        && !item.getToolTipText().isBlank();
  }

  private static JToolTip hubToolTip(JComponent owner) {
    return new HubToolTip(owner);
  }

  private static final class HelpMenu extends JMenu {
    HelpMenu(String text) {
      super(text);
    }

    @Override public Dimension getPreferredSize() {
      return contextHelpPreferredSize(this, super.getPreferredSize());
    }

    @Override protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      paintContextHelpIndicator(this, graphics);
    }

    @Override public JToolTip createToolTip() {
      return hubToolTip(this);
    }
  }

  private static final class HelpMenuItem extends JMenuItem {
    HelpMenuItem(String text) {
      super(text);
    }

    @Override public Dimension getPreferredSize() {
      return contextHelpPreferredSize(this, super.getPreferredSize());
    }

    @Override protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      paintContextHelpIndicator(this, graphics);
    }

    @Override public JToolTip createToolTip() {
      return hubToolTip(this);
    }
  }

  private static final class HelpCheckBoxMenuItem extends JCheckBoxMenuItem {
    HelpCheckBoxMenuItem(String text, boolean selected) {
      super(text, selected);
    }

    @Override public Dimension getPreferredSize() {
      return contextHelpPreferredSize(this, super.getPreferredSize());
    }

    @Override protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      paintContextHelpIndicator(this, graphics);
    }

    @Override public JToolTip createToolTip() {
      return hubToolTip(this);
    }
  }

  private static final class HelpRadioButtonMenuItem extends JRadioButtonMenuItem {
    HelpRadioButtonMenuItem(String text, boolean selected) {
      super(text, selected);
    }

    @Override public Dimension getPreferredSize() {
      return contextHelpPreferredSize(this, super.getPreferredSize());
    }

    @Override protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      paintContextHelpIndicator(this, graphics);
    }

    @Override public JToolTip createToolTip() {
      return hubToolTip(this);
    }
  }

  private static final class HelpCheckBox extends JCheckBox {
    HelpCheckBox(String text, boolean selected) {
      super(text, selected);
    }

    @Override public JToolTip createToolTip() {
      return hubToolTip(this);
    }
  }

  private static final class HelpTextField extends JTextField {
    HelpTextField(String text) {
      super(text);
    }

    @Override public JToolTip createToolTip() {
      return hubToolTip(this);
    }
  }

  private static final class HubToolTip extends JToolTip {
    private final Icon helpIcon = new AeroHelpIcon(ui(18));

    HubToolTip(JComponent owner) {
      setComponent(owner);
      setOpaque(true);
      setBackground(BG);
      setForeground(TEXT_SOFT);
      setFont(getFont().deriveFont(Font.PLAIN, uiFont(11f)));
      setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(Color.decode("#345d78")),
          uiPadding(6, 31, 6, 10)));
    }

    @Override protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      int y = Math.max(0, (getHeight() - helpIcon.getIconHeight()) / 2);
      helpIcon.paintIcon(this, graphics, ui(7), y);
    }
  }

  /** Swing reproduction of the Editor's glossy Aero help orb. */
  static final class AeroHelpIcon implements Icon {
    private final int size;

    AeroHelpIcon(int size) {
      this.size = Math.max(12, size);
    }

    @Override public int getIconWidth() {
      return size;
    }

    @Override public int getIconHeight() {
      return size;
    }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
      Graphics2D g2 = (Graphics2D) graphics.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.translate(x, y);
      float s = size;
      float diameter = s * 0.78f;
      float left = (s - diameter) / 2f;
      float top = (s - diameter) / 2f;
      float cx = s / 2f;
      float cy = s / 2f;
      float radius = diameter / 2f;

      g2.setColor(new Color(0, 10, 20, 125));
      g2.fill(new Ellipse2D.Float(left, top + s * 0.07f, diameter, diameter));

      g2.setPaint(new RadialGradientPaint(
          new Point2D.Float(cx, cy),
          radius,
          new Point2D.Float(s * 0.36f, s * 0.31f),
          new float[]{0f, 0.34f, 0.72f, 1f},
          new Color[]{
              Color.decode("#f7fdff"),
              Color.decode("#94d9f4"),
              Color.decode("#3c7fae"),
              Color.decode("#173b5a")},
          java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE));
      g2.fill(new Ellipse2D.Float(left, top, diameter, diameter));

      g2.setColor(new Color(5, 28, 45, 150));
      g2.setStroke(new BasicStroke(Math.max(1f, s * 0.08f)));
      g2.draw(new Ellipse2D.Float(
          left + s * 0.045f,
          top + s * 0.045f,
          diameter - s * 0.09f,
          diameter - s * 0.09f));
      g2.setColor(Color.decode("#e8f8ff"));
      g2.setStroke(new BasicStroke(Math.max(0.8f, s * 0.045f)));
      g2.draw(new Ellipse2D.Float(left, top, diameter, diameter));

      Font questionFont = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, Math.round(s * 0.55f)));
      g2.setFont(questionFont);
      FontMetrics metrics = g2.getFontMetrics();
      String question = "?";
      float questionX = cx - metrics.stringWidth(question) / 2f;
      float questionY = cy + (metrics.getAscent() - metrics.getDescent()) / 2f + s * 0.01f;
      g2.setColor(new Color(0, 23, 42, 225));
      g2.drawString(question, questionX, questionY + Math.max(1f, s * 0.04f));
      g2.setColor(Color.WHITE);
      g2.drawString(question, questionX, questionY);

      g2.setColor(new Color(255, 255, 255, 132));
      g2.fill(new Ellipse2D.Float(s * 0.24f, s * 0.19f, s * 0.38f, s * 0.15f));
      g2.dispose();
    }
  }

  private static void installApplicationIcon(JFrame frame) {
    if (frame == null) return;
    BufferedImage icon512 = JvnLogoIcon.renderToImage(512, 512);
    frame.setIconImages(List.of(
        JvnLogoIcon.renderToImage(16, 16),
        JvnLogoIcon.renderToImage(32, 32),
        JvnLogoIcon.renderToImage(64, 64),
        JvnLogoIcon.renderToImage(128, 128),
        icon512));
    try {
      if (java.awt.Taskbar.isTaskbarSupported()) {
        java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
        if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
          taskbar.setIconImage(icon512);
        }
      }
    } catch (UnsupportedOperationException | SecurityException ignored) {
      // Some desktop environments expose the Taskbar API but reject icon changes.
    }
  }

  private JPanel buildHeader() {
    JPanel header = new JPanel(new BorderLayout());
    header.setOpaque(false);

    // --- Left: vector logo + text stack -------------------------------------
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, ui(12), 0));
    left.setOpaque(false);

    JLabel logoLabel = new JLabel(new JvnLogoIcon(ui(124), ui(66)));
    left.add(logoLabel);

    JLabel title = new JLabel("Engine Hub");
    title.setForeground(TEXT_PRIMARY);
    title.setFont(title.getFont().deriveFont(Font.BOLD, uiFont(16f)));

    versionLabel.setText(formatVersionLabel(readDiskVersion()));
    versionLabel.setForeground(ACCENT_NEUTRAL);
    versionLabel.setFont(versionLabel.getFont().deriveFont(Font.BOLD, uiFont(10f)));

    JPanel titleBox = new JPanel();
    titleBox.setOpaque(false);
    titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
    title.setAlignmentX(Component.LEFT_ALIGNMENT);
    versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    titleBox.add(title);
    titleBox.add(Box.createVerticalStrut(ui(2)));
    titleBox.add(versionLabel);

    left.add(titleBox);
    header.add(left, BorderLayout.WEST);

    // --- Right: mode toggles and utility shortcuts ---------------------------
    developerModeButton = new DeveloperModeToggleButton();
    developerModeButton.addActionListener(e -> setDeveloperModeEnabled(developerModeButton.isSelected()));
    actionButtons.add(developerModeButton);

    safeModeButton = new SafeModeToggleButton();
    safeModeButton.addActionListener(e -> setSafeModeEnabled(safeModeButton.isSelected()));
    actionButtons.add(safeModeButton);

    diagnosticsButton = new HeaderIconButton(
        WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.DIAGNOSTICS, 24),
        "Diagnostics — run a lightweight health check");
    diagnosticsButton.addActionListener(e -> showDiagnosticsReport());
    actionButtons.add(diagnosticsButton);

    aboutButton = new HeaderIconButton(
        WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.ABOUT, 24),
        "About — version and install details");
    aboutButton.addActionListener(e -> showAboutReport());
    actionButtons.add(aboutButton);

    documentationButton = new HeaderIconButton(
        WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.DOCUMENTATION, 24),
        "Documentation — open the documentation website");
    documentationButton.addActionListener(e -> openDocumentationWebsite());
    actionButtons.add(documentationButton);

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, ui(6), 0));
    right.setOpaque(false);
    right.add(developerModeButton);
    right.add(safeModeButton);
    right.add(diagnosticsButton);
    right.add(aboutButton);
    right.add(documentationButton);
    header.add(right, BorderLayout.EAST);

    return header;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return "";
    for (String value : values) {
      if (value != null && !value.isBlank()) return value.trim();
    }
    return "";
  }

  private JPanel buildCenter() {
    actionGrid = new JPanel(new GridLayout(3, 2, ui(10), ui(10)));
    actionGrid.setOpaque(false);

    runEditorButton = makeAction("Run Editor", "Launch the full JVN editor.",
        VectorIcon.Kind.EDIT, null, () -> guardedRun("Run Editor", () -> runFastApp("editor", "Run Editor")));
    runEditorButton.setIcon(WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.EDITOR));

    buildAllButton = makeAction("Build All", "Compile every module.",
        VectorIcon.Kind.HAMMER, null, () -> guardedRun("Build All", () -> runGradle("build", "Build All")));
    buildAllButton.setIcon(WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.BUILD));

    runTestsButton = makeAction("Run Tests", "Developer Mode: execute the full test suite.",
        VectorIcon.Kind.CHECK, ACCENT_DEV, () -> runGradle("test", "Run Tests"));
    runTestsButton.setIcon(WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.TESTS));

    gradleOptionsButton = makeAction("Gradle Options", "Developer Mode: configure Gradle flags for hub actions.",
        VectorIcon.Kind.SLIDERS, ACCENT_DEV, this::showGradleOptionsDialog);
    gradleOptionsButton.setIcon(WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.OPTIONS));

    buildShortcutsButton = makeAction("Build Shortcuts", "Install Start Menu / Applications shortcuts for this OS.",
        VectorIcon.Kind.SHORTCUT, null, () -> guardedRun("Build Shortcuts", this::installShortcuts));
    buildShortcutsButton.setIcon(WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.SHORTCUT));

    updateEngineButton = new UpdateEngineButton("Update Engine",
        WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.UPDATE));
    updateEngineButton.setToolTipText("git pull --rebase " + ENGINE_UPDATE_REMOTE + " " + ENGINE_UPDATE_BRANCH);
    updateEngineButton.addActionListener(e -> updateEngine());
    actionButtons.add(updateEngineButton);
    rebuildActionGrid();

    JPanel workspace = new JPanel(new BorderLayout(0, ui(10)));
    workspace.setOpaque(false);
    HubPerformancePanel monitor = new HubPerformancePanel();
    performancePanel.set(monitor);
    workspace.add(monitor, BorderLayout.CENTER);

    JPanel center = new JPanel(new BorderLayout(0, ui(8)));
    center.setOpaque(false);
    center.add(actionGrid, BorderLayout.NORTH);
    center.add(buildActivityPanel(), BorderLayout.SOUTH);
    center.add(workspace, BorderLayout.CENTER);
    return center;
  }

  private void rebuildActionGrid() {
    if (actionGrid == null) return;
    actionGrid.removeAll();
    actionGrid.setLayout(new GridLayout(developerModeEnabled ? 3 : 2, 2, ui(10), ui(10)));
    actionGrid.add(runEditorButton);
    actionGrid.add(buildAllButton);
    if (developerModeEnabled) {
      actionGrid.add(runTestsButton);
      actionGrid.add(gradleOptionsButton);
    }
    actionGrid.add(buildShortcutsButton);
    actionGrid.add(updateEngineButton);
    actionGrid.revalidate();
    actionGrid.repaint();
  }

  private JPanel buildActivityPanel() {
    activityTitle.setForeground(TEXT_PRIMARY);
    activityTitle.setFont(activityTitle.getFont().deriveFont(Font.BOLD, uiFont(12f)));
    activityDetail.setForeground(TEXT_MUTED);
    activityDetail.setFont(activityDetail.getFont().deriveFont(Font.PLAIN, uiFont(10f)));

    JPanel text = new JPanel(new BorderLayout(ui(10), 0));
    text.setOpaque(false);
    text.add(activityTitle, BorderLayout.WEST);
    text.add(activityDetail, BorderLayout.CENTER);

    JPanel header = new JPanel(new BorderLayout(ui(10), 0));
    header.setOpaque(false);
    header.add(activitySpinner, BorderLayout.WEST);
    header.add(text, BorderLayout.CENTER);

    activitySteps.setSteps(List.of(
        "Choose an action",
        "The hub will show every background stage here."));

    activityPanel.setProgress(0.0);
    activityPanel.setTone(ACCENT_NEUTRAL, false);
    activityPanel.setBackground(PANEL_BG);
    activityPanel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER_NEUTRAL),
        uiPadding(8, 10, 8, 12)));
    activityPanel.add(header, BorderLayout.NORTH);
    activityPanel.add(activitySteps, BorderLayout.CENTER);
    activityPanel.setPreferredSize(new Dimension(0, ui(118)));
    activityPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, ui(118)));
    return activityPanel;
  }

  private static void styleScrollBar(JScrollBar bar) {
    if (bar == null) return;
    bar.setUI(new NeutralScrollBarUI());
    bar.setOpaque(true);
    bar.setBackground(BG);
    bar.setBorder(BorderFactory.createEmptyBorder());
    bar.setPreferredSize(uiDimension(10, 10));
    bar.setUnitIncrement(ui(16));
  }

  private JPanel buildFooter() {
    JPanel footer = new JPanel(new GridBagLayout());
    footer.setBackground(Color.decode("#191919"));
    footer.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, Color.decode("#303030")),
        uiPadding(3, 8, 3, 8)));

    statusLabel.setForeground(TEXT_SOFT);
    statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, uiFont(11f)));

    footerBranchLabel.setText(resolveBranch(projectRoot));
    footerBranchLabel.setForeground(TEXT_SOFT);
    footerBranchLabel.setFont(footerBranchLabel.getFont().deriveFont(Font.BOLD, uiFont(11f)));

    footerRootLabel.setText(projectRoot.getFileName() == null ? projectRoot.toString() : projectRoot.getFileName().toString());
    footerRootLabel.setToolTipText(projectRoot.toString());
    footerRootLabel.setForeground(TEXT_SOFT);
    footerRootLabel.setFont(footerRootLabel.getFont().deriveFont(Font.BOLD, uiFont(11f)));

    footerModeLabel.setForeground(TEXT_SOFT);
    footerModeLabel.setFont(footerModeLabel.getFont().deriveFont(Font.BOLD, uiFont(11f)));

    JLabel javaLabel = footerLabel("Java " + javaFeatureVersion(), TEXT_SOFT);
    JLabel version = footerLabel(VERSION, TEXT_SOFT);

    FlatButton cancel = new FlatButton("Cancel",
        WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.CANCEL, 18), ACCENT_ERROR);
    cancel.addActionListener(e -> cancelRunning());

    FlatButton quit = new FlatButton("Quit",
        WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.QUIT, 18), null);
    quit.addActionListener(e -> confirmAndExit());

    FlatButton more = new FlatButton("More",
        WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.MORE, 18), null);
    more.addActionListener(e -> showFooterMenu(more));

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, ui(6), 0));
    right.setOpaque(false);
    right.add(more);
    right.add(cancel);
    right.add(quit);
    right.setMinimumSize(right.getPreferredSize());

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, ui(8), 0));
    left.setOpaque(false);
    left.add(footerItem("Hub", statusLabel));
    left.add(footerDivider());
    left.add(footerItem("Branch", footerBranchLabel));
    left.add(footerDivider());
    left.add(footerItem("Root", footerRootLabel));
    left.add(footerDivider());
    left.add(footerItem("Mode", footerModeLabel));
    left.add(footerDivider());
    left.add(footerItem("Java", javaLabel));
    left.add(footerDivider());
    left.add(footerItem("Version", version));
    left.setMinimumSize(new Dimension(0, left.getPreferredSize().height));

    GridBagConstraints leftConstraints = new GridBagConstraints();
    leftConstraints.gridx = 0;
    leftConstraints.gridy = 0;
    leftConstraints.weightx = 1.0;
    leftConstraints.fill = GridBagConstraints.HORIZONTAL;
    leftConstraints.anchor = GridBagConstraints.WEST;
    footer.add(left, leftConstraints);

    GridBagConstraints rightConstraints = new GridBagConstraints();
    rightConstraints.gridx = 1;
    rightConstraints.gridy = 0;
    rightConstraints.weightx = 0.0;
    rightConstraints.fill = GridBagConstraints.NONE;
    rightConstraints.anchor = GridBagConstraints.EAST;
    footer.add(right, rightConstraints);
    installFooterPopup(footer);
    installFooterPopup(left);
    installFooterPopup(right);
    return footer;
  }

  private void showFooterMenu(Component invoker) {
    JPopupMenu menu = buildFooterMenu();
    menu.show(invoker, 0, -menu.getPreferredSize().height);
  }

  private void installFooterPopup(JComponent component) {
    component.setComponentPopupMenu(buildFooterMenu());
  }

  private JPopupMenu buildFooterMenu() {
    JPopupMenu menu = new JPopupMenu() {
      @Override
      public void show(Component invoker, int x, int y) {
        populateFooterMenu(this);
        super.show(invoker, x, y);
      }
    };
    populateFooterMenu(menu);
    return menu;
  }

  private void populateFooterMenu(JPopupMenu menu) {
    boolean busy = runningProcess.get() != null;
    menu.removeAll();
    menu.setBackground(PANEL_BG);
    menu.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(ui(2), ui(1), ui(1), ui(1), ACCENT_TOOLS.darker()),
        uiPadding(4, 3, 4, 3)));
    menu.add(menuStatusCard(
        busy ? "Background task active" : "Engine Hub ready",
        busy ? firstNonBlank(activeStepLabel, "Working") : compactMenuPath(projectRoot),
        ACCENT_TOOLS));
    menu.addSeparator();
    menu.add(popupItem(
        "Run Editor",
        "Launch the JVN editor from this workspace.",
        VectorIcon.Kind.PLAY,
        ACCENT_TOOLS,
        !busy,
        () -> clickIfAvailable(runEditorButton)));
    menu.add(popupItem(
        "Build All",
        "Run the complete workspace build.",
        VectorIcon.Kind.HAMMER,
        ACCENT_GREEN,
        !busy,
        () -> clickIfAvailable(buildAllButton)));
    menu.addSeparator();
    menu.add(popupItem(
        "Update Engine",
        "Update this checkout from the stable branch.",
        VectorIcon.Kind.REFRESH,
        ACCENT_MAINTENANCE,
        !busy,
        this::updateEngine));
    menu.add(popupItem(
        "Diagnostics",
        "Run the Hub health report.",
        VectorIcon.Kind.HEALTH,
        ACCENT_TOOLS,
        true,
        this::showDiagnosticsReport));
    menu.add(popupItem(
        "Documentation Website",
        "Open the public JVN documentation.",
        VectorIcon.Kind.DOCUMENTATION,
        TEXT_SOFT,
        true,
        this::openDocumentationWebsite));
    menu.addSeparator();
    menu.add(popupItem(
        "Reveal Engine Root",
        "Open the active checkout in the system file manager.",
        VectorIcon.Kind.INFO,
        TEXT_SOFT,
        true,
        this::revealEngineRoot));
    menu.add(popupItem(
        "Copy Engine Root Path",
        "Copy the active checkout path.",
        VectorIcon.Kind.CHECK,
        TEXT_SOFT,
        true,
        this::copyEngineRootPath));
    menu.addSeparator();
    menu.add(popupItem(
        "Cancel Running Task",
        busy ? "Stop the active Hub-managed process." : "No Hub-managed process is running.",
        VectorIcon.Kind.STOP,
        ACCENT_ERROR,
        busy,
        this::cancelRunning));
    menu.add(popupItem(
        "Quit Hub",
        "Close the Engine Hub after confirming any active task.",
        VectorIcon.Kind.CLOSE,
        TEXT_SOFT,
        true,
        this::confirmAndExit));
  }

  private JMenuItem popupItem(
      String label,
      String tooltip,
      VectorIcon.Kind iconKind,
      Color accent,
      boolean enabled,
      Runnable action) {
    JMenuItem item = hubMenuItem(label, tooltip, iconKind, accent, action);
    item.setEnabled(enabled);
    return item;
  }

  private void clickIfAvailable(AbstractButton button) {
    if (button == null || !button.isEnabled()) return;
    button.doClick();
  }

  private void revealEngineRoot() {
    try {
      java.awt.Desktop.getDesktop().open(projectRoot.toFile());
      setStatus("Opened engine root", ACCENT_NEUTRAL);
    } catch (Exception e) {
      setStatus("Could not open engine root", ACCENT_ERROR);
      setActivity("Reveal failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), false, ACCENT_ERROR);
    }
  }

  private void revealHubFolder(String label, Path folder) {
    try {
      Files.createDirectories(folder);
      java.awt.Desktop.getDesktop().open(folder.toFile());
      setStatus("Opened Hub " + label, ACCENT_NEUTRAL);
    } catch (Exception e) {
      setStatus("Could not open Hub " + label, ACCENT_ERROR);
      setActivity("Open folder failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), false, ACCENT_ERROR);
    }
  }

  private void copyEngineRootPath() {
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(projectRoot.toString()), null);
    setStatus("Copied engine root path", ACCENT_NEUTRAL);
  }

  private void copyCurrentBranch() {
    String branch = resolveBranch(projectRoot);
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(branch), null);
    setStatus("Copied branch " + branch, ACCENT_NEUTRAL);
  }

  private static JPanel footerItem(String caption, JLabel value) {
    JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, ui(4), 0));
    item.setOpaque(false);
    JLabel cap = footerLabel(caption + ":", TEXT_MUTED);
    item.add(cap);
    item.add(value);
    return item;
  }

  private static JLabel footerLabel(String text, Color color) {
    JLabel label = new JLabel(text == null || text.isBlank() ? "--" : text);
    label.setForeground(color != null ? color : TEXT_SOFT);
    label.setFont(label.getFont().deriveFont(Font.BOLD, uiFont(11f)));
    return label;
  }

  private static JComponent footerDivider() {
    JPanel divider = new JPanel();
    divider.setBackground(Color.decode("#353535"));
    divider.setPreferredSize(uiDimension(1, 14));
    divider.setMaximumSize(uiDimension(1, 14));
    return divider;
  }

  private void refreshFooterMode() {
    String mode;
    Color color;
    if (developerModeEnabled && safeModeEnabled) {
      mode = "Dev + Safe";
      color = ACCENT_SAFE;
    } else if (developerModeEnabled) {
      mode = "Developer";
      color = ACCENT_DEV;
    } else if (safeModeEnabled) {
      mode = "Safe";
      color = ACCENT_SAFE;
    } else {
      mode = "Standard";
      color = TEXT_SOFT;
    }
    footerModeLabel.setText(mode);
    footerModeLabel.setForeground(color);
  }

  private static String javaFeatureVersion() {
    String version = System.getProperty("java.version", "");
    if (version.isBlank()) return "--";
    if (version.startsWith("1.")) {
      int next = version.indexOf('.', 2);
      return next > 0 ? version.substring(2, next) : version.substring(2);
    }
    int dot = version.indexOf('.');
    int dash = version.indexOf('-');
    int end = dot > 0 ? dot : (dash > 0 ? dash : version.length());
    return version.substring(0, end);
  }

  private static String resolveBranch(Path projectRoot) {
    Path dir = projectRoot == null ? null : projectRoot.toAbsolutePath().normalize();
    for (int i = 0; i < 8 && dir != null; i++, dir = dir.getParent()) {
      Optional<Path> gitDirOpt = resolveGitDir(dir.resolve(".git"));
      if (gitDirOpt.isEmpty()) continue;
      Path gitDir = gitDirOpt.get();
      Path head = gitDir.resolve("HEAD");
      if (!Files.isRegularFile(head)) continue;
      try {
        String value = Files.readString(head).trim();
        if (value.startsWith("ref:")) {
          String ref = value.substring(4).trim();
          int slash = ref.lastIndexOf('/');
          return slash >= 0 ? ref.substring(slash + 1) : ref;
        }
        return value.length() > 7 ? value.substring(0, 7).toLowerCase(Locale.ROOT) : value;
      } catch (IOException ignored) {
        return "No branch";
      }
    }
    return "No branch";
  }

  private static Optional<Path> resolveGitDir(Path gitPath) {
    try {
      if (gitPath == null) return Optional.empty();
      if (Files.isDirectory(gitPath)) return Optional.of(gitPath);
      if (Files.isRegularFile(gitPath)) {
        String text = Files.readString(gitPath).trim();
        if (text.startsWith("gitdir:")) {
          Path target = Paths.get(text.substring("gitdir:".length()).trim());
          Path parent = gitPath.getParent();
          if (parent == null) return Optional.empty();
          return Optional.of(target.isAbsolute()
              ? target.normalize()
              : parent.resolve(target).normalize());
        }
      }
    } catch (IOException ignored) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private void setSafeModeEnabled(boolean enabled) {
    safeModeEnabled = enabled;
    if (safeModeButton != null) safeModeButton.setSafeModeEnabled(enabled);
    refreshFooterMode();
    String title = enabled ? "Safe Mode enabled" : "Safe Mode disabled";
    String detail = enabled
        ? "Editor-side launches use safe-mode flags. Update Engine uses guarded Git recovery."
        : "Editor-side launches use the standard engine startup path. Update Engine uses the normal Git update path.";
    setStatus(title, enabled ? ACCENT_SAFE : TEXT_SOFT);
    setActivity(title, detail, false, enabled ? ACCENT_SAFE : TEXT_MUTED);
    refreshModeMenus();
  }

  private void setDeveloperModeEnabled(boolean enabled) {
    developerModeEnabled = enabled;
    if (developerModeButton != null) developerModeButton.setDeveloperModeEnabled(enabled);
    rebuildActionGrid();
    refreshFooterMode();
    String title = enabled ? "Developer Mode enabled" : "Developer Mode disabled";
    String detail = enabled
        ? "Run Tests is visible and editor-side launches receive developer-mode flags."
        : "Developer-only actions are hidden from the main hub controls.";
    setStatus(title, enabled ? ACCENT_DEV : TEXT_SOFT);
    setActivity(title, detail, false, enabled ? ACCENT_DEV : TEXT_MUTED);
    refreshModeMenus();
  }

  private void setRenderPipelineMode(RenderPipelineSettings.Mode requestedMode) {
    RenderPipelineSettings.Mode mode = requestedMode == null
        ? RenderPipelineSettings.Mode.AUTO
        : requestedMode;
    try {
      RenderPipelineSettings.save(renderPipelinePreferencesFile, mode);
      renderPipelineMode = mode;
      appendLog("[hub] render pipeline set to " + mode.displayName()
          + " (" + mode.backendOrder(System.getProperty("os.name", "")) + ").");
      setStatus("Render Pipeline: " + mode.displayName(), ACCENT_RENDER);
      setActivity(
          "Render pipeline updated",
          "Applies to the next editor, preview, launcher, and game-runtime process.",
          false,
          ACCENT_RENDER);
    } catch (IOException error) {
      appendLog("[hub] could not save render pipeline: " + error.getMessage());
      setStatus("Could not save Render Pipeline", ACCENT_ERROR);
      setActivity(
          "Render pipeline unchanged",
          error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
          false,
          ACCENT_ERROR);
    }
    refreshModeMenus();
  }

  private void setRenderPipelineOptions(
      RenderPipelineSettings.Options requestedOptions,
      String changedSetting) {
    RenderPipelineSettings.Options options = requestedOptions == null
        ? RenderPipelineSettings.Options.defaults()
        : requestedOptions;
    try {
      RenderPipelineSettings.saveOptions(renderPipelineTuningFile, options);
      renderPipelineOptions = options;
      String label = firstNonBlank(changedSetting, "Rendering option");
      appendLog("[hub] render tuning updated: " + label + ".");
      setStatus(label + " updated", ACCENT_RENDER);
      setActivity(
          "Render tuning updated",
          "The change applies when the next JVN process starts.",
          false,
          ACCENT_RENDER);
    } catch (IOException error) {
      appendLog("[hub] could not save render tuning: " + error.getMessage());
      setStatus("Could not save render tuning", ACCENT_ERROR);
      setActivity(
          "Render tuning unchanged",
          error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
          false,
          ACCENT_ERROR);
    }
    refreshModeMenus();
  }

  private void disableRenderDiagnostics() {
    RenderPipelineSettings.Options current = renderPipelineOptions;
    RenderPipelineSettings.Options quiet = current
        .withVerbose(false)
        .withShowDirtyRegions(false)
        .withShowOverdraw(false)
        .withPrintRenderGraph(false);
    setRenderPipelineOptions(quiet, "Render diagnostics");
  }

  private void resetRenderPipelineDefaults() {
    try {
      RenderPipelineSettings.Mode mode = RenderPipelineSettings.Mode.AUTO;
      RenderPipelineSettings.Options options = RenderPipelineSettings.Options.defaults();
      RenderPipelineSettings.save(renderPipelinePreferencesFile, mode);
      RenderPipelineSettings.saveOptions(renderPipelineTuningFile, options);
      renderPipelineMode = mode;
      renderPipelineOptions = options;
      appendLog("[hub] Render Pipeline reset to adaptive JavaFX defaults.");
      setStatus("Render Pipeline defaults restored", ACCENT_RENDER);
      setActivity(
          "Rendering defaults restored",
          "Adaptive selection and safe JavaFX performance defaults apply on the next launch.",
          false,
          ACCENT_RENDER);
    } catch (IOException error) {
      appendLog("[hub] could not reset Render Pipeline: " + error.getMessage());
      setStatus("Could not reset Render Pipeline", ACCENT_ERROR);
      setActivity(
          "Render Pipeline reset failed",
          error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
          false,
          ACCENT_ERROR);
    }
    refreshModeMenus();
  }

  private void showRenderPipelineReport() {
    setStatus("Inspecting render stack", ACCENT_RENDER);
    setActivity(
        "Inspecting render stack",
        "Checking display and JavaFX launch configuration.",
        true,
        ACCENT_RENDER);
    setButtonsEnabled(false);
    RenderPipelineSettings.Mode selectedMode = renderPipelineMode;
    RenderPipelineSettings.Options selectedOptions = renderPipelineOptions;

    new SwingWorker<List<HealthCheck>, Void>() {
      @Override protected List<HealthCheck> doInBackground() {
        return buildRenderPipelineReport(selectedMode, selectedOptions);
      }

      @Override protected void done() {
        setButtonsEnabled(true);
        List<HealthCheck> report;
        try {
          report = get();
        } catch (Exception error) {
          report = List.of(new HealthCheck(
              CheckStatus.FAIL,
              "Render stack inspection",
              "The rendering report could not be completed.",
              error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        }
        setStatus("Render stack report ready", ACCENT_RENDER);
        setActivity(
            "Render stack inspected",
            selectedMode.displayName() + " is selected for the next launch.",
            false,
            ACCENT_RENDER);
        showReportDialog(
            "Render Pipeline / Render Stack",
            report,
            selectedMode.displayName() + " · next process launch");
      }
    }.execute();
  }

  private List<HealthCheck> buildRenderPipelineReport(
      RenderPipelineSettings.Mode mode,
      RenderPipelineSettings.Options options) {
    RenderPipelineSettings.Mode selected = mode == null
        ? RenderPipelineSettings.Mode.AUTO
        : mode;
    RenderPipelineSettings.Options tuning = options == null
        ? RenderPipelineSettings.Options.defaults()
        : options;
    List<HealthCheck> report = new ArrayList<>();
    report.add(new HealthCheck(
        CheckStatus.INFO,
        "Launch profile",
        selected.displayName(),
        selected.description() + " Changes take effect when a new JVN process starts."));
    report.add(new HealthCheck(
        selected == RenderPipelineSettings.Mode.SOFTWARE ? CheckStatus.INFO : CheckStatus.PASS,
        "JavaFX backend order",
        selected.backendOrder(System.getProperty("os.name", "")),
        selected == RenderPipelineSettings.Mode.HARDWARE
            ? "Hardware acceleration is preferred; the software backend remains available if GPU initialization fails."
            : selected == RenderPipelineSettings.Mode.SOFTWARE
                ? "Hardware rendering is intentionally disabled for compatibility diagnostics."
                : "JavaFX selects its platform default and may fall back when required."));
    report.add(new HealthCheck(
        tuning.vsync() ? CheckStatus.PASS : CheckStatus.INFO,
        "Frame presentation",
        tuning.vsync() ? "Display synchronization enabled" : "Display synchronization disabled",
        "Prism VSync is applied before the JavaFX toolkit initializes."));
    report.add(new HealthCheck(
        tuning.dirtyRegions() && tuning.occlusionCulling() ? CheckStatus.PASS : CheckStatus.INFO,
        "Scene repaint optimization",
        "Dirty regions " + (tuning.dirtyRegions() ? "enabled" : "disabled")
            + " · occlusion culling " + (tuning.occlusionCulling() ? "enabled" : "disabled"),
        "Shape cache: " + tuning.shapeCache().displayName() + "."));
    report.add(new HealthCheck(
        tuning.diagnosticsEnabled() ? CheckStatus.WARN : CheckStatus.PASS,
        "Render diagnostics",
        tuning.diagnosticsEnabled() ? "One or more diagnostic probes are enabled" : "Disabled",
        "Verbose startup=" + tuning.verbose()
            + "; dirty-region overlay=" + tuning.showDirtyRegions()
            + "; overdraw overlay=" + tuning.showOverdraw()
            + "; render-graph logging=" + tuning.printRenderGraph()
            + (tuning.diagnosticsEnabled()
                ? ". Diagnostic overlays and logging may reduce performance."
                : ".")));

    report.add(new HealthCheck(CheckStatus.INFO, "Editor preview budget",
        RenderPipelineSettings.previewBudgetSummary(tuning),
        "Applies to docked, detached, and fullscreen editor previews. Lower caps reduce draw work; game runtime timing is unchanged."));
    report.add(displayDeviceCheck());
    report.add(new HealthCheck(
        CheckStatus.INFO,
        "Desktop graphics session",
        renderSessionSummary(),
        "OS=" + firstNonBlank(System.getProperty("os.name"), "unknown")
            + "; arch=" + firstNonBlank(System.getProperty("os.arch"), "unknown")));

    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("linux")) {
      report.add(new HealthCheck(
          tuning.linuxGlxRecovery() ? CheckStatus.PASS : CheckStatus.INFO,
          "Managed GLX recovery",
          tuning.linuxGlxRecovery() ? "Enabled" : "Disabled",
              "When enabled, JVN retries a broken default GLX provider with Mesa."));
      report.add(linuxOpenGlCheck());
      if (selected == RenderPipelineSettings.Mode.HARDWARE) {
        report.add(linuxDiscreteGpuLaunchCheck());
      }
    }

    report.add(new HealthCheck(
        CheckStatus.INFO,
        "Shared process scope",
        "Editor, previews, launcher, and game runtime",
        "The Hub persists graphics.mode and explicitly forwards JVN_GRAPHICS_MODE to managed launches."));
    report.add(new HealthCheck(
        renderSettingsWritable() ? CheckStatus.PASS : CheckStatus.WARN,
        "Render Pipeline settings",
        renderPipelineTuningFile.toAbsolutePath().toString(),
        "Profile: graphics.mode=" + selected.id()
            + " in " + renderPipelinePreferencesFile.toAbsolutePath()));
    return List.copyOf(report);
  }

  private boolean renderSettingsWritable() {
    Path folder = renderPipelineTuningFile.toAbsolutePath().getParent();
    if (folder == null) return false;
    if (Files.isDirectory(folder)) return Files.isWritable(folder);
    Path parent = folder.getParent();
    return parent != null && Files.isDirectory(parent) && Files.isWritable(parent);
  }

  private HealthCheck displayDeviceCheck() {
    if (GraphicsEnvironment.isHeadless()) {
      return new HealthCheck(
          CheckStatus.WARN,
          "Display device",
          "No desktop display is available to the Hub.",
          "The editor needs a graphical desktop session to initialize JavaFX rendering.");
    }
    try {
      GraphicsDevice device = GraphicsEnvironment
          .getLocalGraphicsEnvironment()
          .getDefaultScreenDevice();
      GraphicsConfiguration configuration = device.getDefaultConfiguration();
      Rectangle bounds = configuration.getBounds();
      java.awt.ImageCapabilities capabilities = configuration.getImageCapabilities();
      return new HealthCheck(
          capabilities.isAccelerated() ? CheckStatus.PASS : CheckStatus.INFO,
          "Hub display device",
          firstNonBlank(device.getIDstring(), "Default display"),
          bounds.width + "x" + bounds.height
              + "; color depth=" + configuration.getColorModel().getPixelSize()
              + " bit; AWT accelerated images=" + capabilities.isAccelerated()
              + ". JavaFX confirms its own active backend after launch.");
    } catch (Exception error) {
      return new HealthCheck(
          CheckStatus.WARN,
          "Display device",
          "Display capabilities could not be queried.",
          error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
    }
  }

  private String renderSessionSummary() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (!os.contains("linux")) return firstNonBlank(System.getProperty("os.name"), "Desktop session");
    String session = firstNonBlank(System.getenv("XDG_SESSION_TYPE"), "unknown");
    String wayland = firstNonBlank(System.getenv("WAYLAND_DISPLAY"), "not set");
    String x11 = firstNonBlank(System.getenv("DISPLAY"), "not set");
    return session + " · WAYLAND_DISPLAY=" + wayland + " · DISPLAY=" + x11;
  }

  private HealthCheck linuxOpenGlCheck() {
    if (!commandExists("glxinfo")) {
      return new HealthCheck(
          CheckStatus.INFO,
          "Linux OpenGL probe",
          "glxinfo is not installed; the launcher's automatic fallback remains available.",
          "Install mesa-utils to include the active OpenGL vendor and renderer in this report.");
    }
    if (firstNonBlank(System.getenv("DISPLAY"), "").isBlank()) {
      return new HealthCheck(
          CheckStatus.INFO,
          "Linux OpenGL probe",
          "No X11 DISPLAY is available for glxinfo.",
          "A native Wayland session may still provide a working JavaFX hardware pipeline.");
    }
    CommandResult result = runLocalCommand(List.of("glxinfo", "-B"), 6);
    if (result.exitCode != 0) {
      CommandResult mesa = commandExists("env")
          ? runLocalCommand(
              List.of("env", "__GLX_VENDOR_LIBRARY_NAME=mesa", "glxinfo", "-B"),
              6)
          : new CommandResult(-1, "env command not found");
      if (mesa.exitCode == 0) {
        return new HealthCheck(
            CheckStatus.PASS,
            "Linux OpenGL recovery",
            "Default GLX failed, but the Mesa GPU provider is available.",
            "Mesa can be selected by the managed GLX recovery option.\n"
                + summarizeOpenGlProbe(mesa.output));
      }
      return new HealthCheck(
          CheckStatus.WARN,
          "Linux OpenGL probe",
          "Neither the default nor Mesa GLX provider initialized.",
          "Default: " + compactMessage(result.output)
              + "\nMesa: " + compactMessage(mesa.output));
    }
    return new HealthCheck(
        CheckStatus.PASS,
        "Linux OpenGL probe",
        "Direct OpenGL initialization succeeded.",
        summarizeOpenGlProbe(result.output));
  }

  private HealthCheck linuxDiscreteGpuLaunchCheck() {
    Path launcher = projectRoot.resolve("scripts").resolve("launch-app.sh");
    if (!Files.isExecutable(launcher) || !commandExists("env")) {
      return new HealthCheck(
          CheckStatus.INFO,
          "Linux discrete-GPU launch probe",
          "The managed launch probe is unavailable.",
          "Expected executable: " + launcher.toAbsolutePath());
    }
    CommandResult result = runLocalCommand(
        List.of(
            "env",
            RenderPipelineSettings.GRAPHICS_MODE_ENVIRONMENT + "=hardware",
            launcher.toString(),
            "runtime",
            "--gpu-info"),
        8);
    String detail = result.output == null ? "" : result.output.trim();
    if (result.exitCode != 0) {
      return new HealthCheck(
          CheckStatus.WARN,
          "Linux discrete-GPU launch probe",
          "The GPU preference probe did not complete.",
          detail.isBlank() ? "Exit code " + result.exitCode : detail);
    }
    boolean rendererReported = detail.toLowerCase(Locale.ROOT).contains("renderer=");
    return new HealthCheck(
        rendererReported ? CheckStatus.PASS : CheckStatus.INFO,
        "Linux discrete-GPU launch probe",
        rendererReported ? "The managed launch resolved an OpenGL adapter." : "Adapter selection was requested.",
        detail.isBlank()
            ? "No OpenGL renderer was reported; install mesa-demos or the distribution's glxinfo package."
            : detail);
  }

  private static String summarizeOpenGlProbe(String output) {
    if (output == null || output.isBlank()) return "glxinfo -B completed successfully.";
    List<String> lines = output.lines()
        .map(String::trim)
        .filter(line -> {
          String lower = line.toLowerCase(Locale.ROOT);
          return lower.startsWith("direct rendering:")
              || lower.startsWith("accelerated:")
              || lower.startsWith("opengl vendor string:")
              || lower.startsWith("opengl renderer string:")
              || lower.startsWith("opengl core profile version string:")
              || lower.startsWith("opengl version string:");
        })
        .limit(6)
        .toList();
    return lines.isEmpty() ? "glxinfo -B completed successfully." : String.join("\n", lines);
  }

  private String renderPipelineSummary() {
    RenderPipelineSettings.Mode mode = renderPipelineMode;
    RenderPipelineSettings.Options tuning = renderPipelineOptions;
    return String.join("\n",
        "JVN Render Pipeline",
        "Profile: " + mode.displayName() + " (" + mode.id() + ")",
        "Backend order: " + mode.backendOrder(System.getProperty("os.name", "")),
        "Preview budget: " + RenderPipelineSettings.previewBudgetSummary(tuning),
        "VSync: " + tuning.vsync(),
        "Dirty regions: " + tuning.dirtyRegions(),
        "Occlusion culling: " + tuning.occlusionCulling(),
        "Shape cache: " + tuning.shapeCache().displayName(),
        "Diagnostics: " + (tuning.diagnosticsEnabled() ? "enabled" : "disabled"),
        "Linux GLX recovery: " + tuning.linuxGlxRecovery(),
        "Scope: editor, previews, launcher, and game runtime",
        "Desktop session: " + renderSessionSummary(),
        "OS: " + firstNonBlank(System.getProperty("os.name"), "unknown")
            + " " + firstNonBlank(System.getProperty("os.arch"), "unknown"),
        "Java: " + firstNonBlank(System.getProperty("java.version"), "unknown"),
        "Profile preferences: " + renderPipelinePreferencesFile.toAbsolutePath(),
        "Tuning preferences: " + renderPipelineTuningFile.toAbsolutePath(),
        "Applies on next process launch: yes");
  }

  private void copyRenderPipelineSummary() {
    String summary = renderPipelineSummary();
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(summary), null);
    setStatus("Copied render stack summary", ACCENT_RENDER);
    setActivity(
        "Render stack summary copied",
        "Ready to paste into a performance report.",
        false,
        ACCENT_RENDER);
  }

  private void showRenderGraphViewer() {
    JDialog openViewer = renderGraphViewer.get();
    if (openViewer != null && openViewer.isDisplayable()) {
      openViewer.setVisible(true);
      openViewer.toFront();
      openViewer.requestFocus();
      return;
    }

    JDialog dialog = new JDialog(frame, "Render Graph Viewer", false);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    renderGraphViewer.set(dialog);

    JPanel root = new JPanel(new BorderLayout(0, ui(12)));
    root.setBackground(BG);
    root.setBorder(uiPadding(14, 14, 14, 14));
    root.add(dialogHeader(
        "Render Graph Viewer",
        "Latest detailed JavaFX slow-pulse tree from a Hub-managed launch."), BorderLayout.NORTH);

    JLabel captureStatus = new JLabel("Waiting for capture state...");
    captureStatus.setForeground(ACCENT_RENDER);
    captureStatus.setFont(captureStatus.getFont().deriveFont(Font.BOLD, uiFont(10.5f)));
    captureStatus.setBorder(uiPadding(0, 2, 5, 2));

    JTextArea graphArea = new JTextArea();
    graphArea.setEditable(false);
    graphArea.setLineWrap(false);
    graphArea.setWrapStyleWord(false);
    graphArea.setBackground(PRESSED_BG);
    graphArea.setForeground(LOG_TEXT);
    graphArea.setCaretColor(ACCENT_RENDER);
    graphArea.setSelectionColor(new Color(83, 54, 108));
    graphArea.setSelectedTextColor(TEXT_PRIMARY);
    graphArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.round(uiFont(10.5f))));
    graphArea.setBorder(uiPadding(10, 12, 10, 12));

    JScrollPane scroll = new JScrollPane(graphArea);
    scroll.setBorder(BorderFactory.createLineBorder(ACCENT_RENDER.darker()));
    scroll.getViewport().setBackground(PRESSED_BG);
    styleScrollBar(scroll.getVerticalScrollBar());
    styleScrollBar(scroll.getHorizontalScrollBar());

    JPanel graphPanel = new JPanel(new BorderLayout());
    graphPanel.setOpaque(false);
    graphPanel.add(captureStatus, BorderLayout.NORTH);
    graphPanel.add(scroll, BorderLayout.CENTER);
    root.add(graphPanel, BorderLayout.CENTER);

    JCheckBox captureToggle = optionCheckBox(
        "Capture slow-pulse render trees on the next launch",
        "Requests Prism render-graph output without forcing JavaFX's unstable internal PulseLogger.",
        renderPipelineOptions.printRenderGraph());
    captureToggle.addActionListener(e -> setRenderPipelineOptions(
        renderPipelineOptions.withPrintRenderGraph(captureToggle.isSelected()),
        "Render-graph capture"));

    FlatButton launch = new FlatButton(
        "Launch Editor",
        uiIcon(VectorIcon.Kind.PLAY, 14, ACCENT_RENDER),
        ACCENT_RENDER);
    launch.setToolTipText("Launch the editor with the current Render Pipeline settings.");
    launch.addActionListener(e -> clickIfAvailable(runEditorButton));

    FlatButton copy = new FlatButton(
        "Copy Graph",
        uiIcon(VectorIcon.Kind.CHECK, 14, TEXT_SOFT),
        TEXT_SOFT);
    copy.addActionListener(e -> {
      String graph = renderGraphCapture.snapshot().graph();
      if (graph.isBlank()) return;
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(graph), null);
      setStatus("Copied render graph", ACCENT_RENDER);
    });

    FlatButton clear = new FlatButton(
        "Clear",
        uiIcon(VectorIcon.Kind.CLOSE, 14, TEXT_SOFT),
        TEXT_SOFT);
    clear.addActionListener(e -> renderGraphCapture.clear());

    FlatButton close = new FlatButton(
        "Close",
        uiIcon(VectorIcon.Kind.CLOSE, 14, TEXT_PRIMARY),
        TEXT_PRIMARY);
    close.addActionListener(e -> dialog.dispose());

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, ui(7), 0));
    actions.setOpaque(false);
    actions.add(launch);
    actions.add(copy);
    actions.add(clear);
    actions.add(close);

    JPanel footer = new JPanel(new BorderLayout(0, ui(7)));
    footer.setOpaque(false);
    footer.add(captureToggle, BorderLayout.NORTH);
    footer.add(actions, BorderLayout.SOUTH);
    root.add(footer, BorderLayout.SOUTH);

    long[] shownRevision = {-1L};
    boolean[] shownPreference = {!renderPipelineOptions.printRenderGraph()};
    Runnable refresh = () -> {
      RenderGraphCapture.Snapshot snapshot = renderGraphCapture.snapshot();
      boolean preference = renderPipelineOptions.printRenderGraph();
      if (snapshot.revision() != shownRevision[0] || preference != shownPreference[0]) {
        shownRevision[0] = snapshot.revision();
        shownPreference[0] = preference;
        captureToggle.setSelected(preference);
        String text = snapshot.graph().isBlank()
            ? renderGraphEmptyState(preference, snapshot.processRunning(), snapshot.captureEnabled())
            : snapshot.graph();
        graphArea.setText(text);
        graphArea.setCaretPosition(0);
      }
      launch.setEnabled(runningProcess.get() == null);
      copy.setEnabled(!snapshot.graph().isBlank());
      clear.setEnabled(!snapshot.graph().isBlank());
      captureStatus.setText(renderGraphStatus(snapshot, preference));
    };

    javax.swing.Timer refreshTimer = new javax.swing.Timer(240, e -> refresh.run());
    refreshTimer.setCoalesce(true);
    dialog.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent event) {
        refreshTimer.stop();
        renderGraphViewer.compareAndSet(dialog, null);
      }
    });

    dialog.setContentPane(root);
    dialog.setMinimumSize(uiDimension(650, 420));
    dialog.setSize(uiDimension(760, 500));
    dialog.setLocationRelativeTo(frame);
    refresh.run();
    refreshTimer.start();
    dialog.setVisible(true);
  }

  private static String renderGraphEmptyState(
      boolean preferenceEnabled,
      boolean processRunning,
      boolean launchCaptureEnabled) {
    if (!preferenceEnabled) {
      return "No render graph captured.\n\n"
          + "Enable capture below, launch the editor or a game from the Hub, then interact with "
          + "the UI. Detailed graphs are emitted for slow JavaFX pulses.";
    }
    if (processRunning && !launchCaptureEnabled) {
      return "Capture was enabled after this process started.\n\n"
          + "JavaFX diagnostics are startup-only; relaunch the editor or game to collect a graph.";
    }
    if (processRunning) {
      return "Listening for a detailed render graph...\n\n"
          + "Interact with the running JavaFX UI. The latest slow-pulse tree will appear here.";
    }
    return "Render-graph capture is ready.\n\n"
        + "Launch the editor or a game from the Hub, then interact with its UI.";
  }

  private static String renderGraphStatus(
      RenderGraphCapture.Snapshot snapshot,
      boolean preferenceEnabled) {
    if (snapshot.processRunning() && snapshot.captureEnabled()) {
      return "● Listening · " + snapshot.session()
          + " · " + snapshot.capturedGraphs() + " graph"
          + (snapshot.capturedGraphs() == 1 ? "" : "s");
    }
    if (!snapshot.graph().isBlank()) {
      return "Latest · " + snapshot.graphSession()
          + " · " + snapshot.capturedGraphs() + " graph"
          + (snapshot.capturedGraphs() == 1 ? "" : "s");
    }
    return preferenceEnabled ? "Ready for the next managed launch" : "Capture disabled";
  }

  private void openRenderPipelinePreferences() {
    try {
      if (!Files.isRegularFile(renderPipelineTuningFile)) {
        RenderPipelineSettings.saveOptions(renderPipelineTuningFile, renderPipelineOptions);
      }
      java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
      if (desktop.isSupported(java.awt.Desktop.Action.EDIT)) {
        desktop.edit(renderPipelineTuningFile.toFile());
      } else {
        desktop.open(renderPipelineTuningFile.toFile());
      }
      setStatus("Opened Render Pipeline settings", ACCENT_RENDER);
    } catch (Exception error) {
      appendLog("[hub] could not open graphics preferences: " + error.getMessage());
      setStatus("Could not open graphics preferences", ACCENT_ERROR);
      setActivity(
          "Open graphics preferences failed",
          error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
          false,
          ACCENT_ERROR);
    }
  }

  private void refreshModeMenus() {
    if (!frameConfigured) return;
    frame.setJMenuBar(buildMenuBar());
    frame.revalidate();
    frame.repaint();
  }

  private void showGradleOptionsDialog() {
    JDialog dialog = new JDialog(frame, "Developer Gradle Options", true);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    JPanel root = new JPanel(new BorderLayout(0, ui(14)));
    root.setBackground(BG);
    root.setBorder(uiPadding(16, 16, 16, 16));
    root.add(dialogHeader("Developer Gradle Options", "Applied to hub Gradle actions while Developer Mode is enabled."), BorderLayout.NORTH);

    JCheckBox stacktrace = optionCheckBox("Stacktrace", "Add --stacktrace to failures.", gradleStacktraceEnabled);
    JCheckBox info = optionCheckBox("Info logging", "Add --info for more Gradle output.", gradleInfoLoggingEnabled);
    JCheckBox debug = optionCheckBox("Debug logging", "Add --debug for very verbose Gradle output.", gradleDebugLoggingEnabled);
    JCheckBox offline = optionCheckBox("Offline mode", "Add --offline and avoid network dependency resolution.", gradleOfflineEnabled);
    JCheckBox refresh = optionCheckBox("Refresh dependencies", "Add --refresh-dependencies.", gradleRefreshDependenciesEnabled);
    JCheckBox noBuildCache = optionCheckBox("No build cache", "Add --no-build-cache.", gradleNoBuildCacheEnabled);
    JCheckBox noDaemon = optionCheckBox("No daemon", "Add --no-daemon. Enable only when invoking via ./gradlew :hub:run to avoid nested-daemon conflicts. Leave off for normal hub launches to allow daemon reuse.", gradleNoDaemonEnabled);

    JTextField extraArgs = gradleTextField(gradleExtraArgs);
    extraArgs.setToolTipText("Extra Gradle arguments, for example: --scan -PmyFlag=true");

    JPanel options = new JPanel(new GridBagLayout());
    options.setBackground(PANEL_BG);
    options.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER_NEUTRAL),
        uiPadding(12, 14, 12, 14)));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets = new java.awt.Insets(0, 0, ui(6), 0);
    int row = 0;
    for (JCheckBox box : List.of(stacktrace, info, debug, offline, refresh, noBuildCache, noDaemon)) {
      gbc.gridy = row++;
      options.add(box, gbc);
    }
    JLabel extraLabel = new JLabel("Extra arguments");
    extraLabel.setForeground(TEXT_MUTED);
    extraLabel.setFont(extraLabel.getFont().deriveFont(Font.BOLD, uiFont(10f)));
    gbc.gridy = row++;
    gbc.insets = new java.awt.Insets(ui(6), 0, ui(5), 0);
    options.add(extraLabel, gbc);
    gbc.gridy = row;
    gbc.insets = new java.awt.Insets(0, 0, 0, 0);
    options.add(extraArgs, gbc);
    root.add(options, BorderLayout.CENTER);

    FlatButton reset = new FlatButton("Reset", null, null);
    reset.addActionListener(e -> {
      gradleStacktraceEnabled = true;
      gradleInfoLoggingEnabled = false;
      gradleDebugLoggingEnabled = false;
      gradleOfflineEnabled = false;
      gradleRefreshDependenciesEnabled = false;
      gradleNoBuildCacheEnabled = false;
      gradleNoDaemonEnabled = false;
      gradleExtraArgs = "";
      dialog.dispose();
      setStatus("Gradle options reset", ACCENT_DEV);
      setActivity("Gradle options reset", describeGradleOptions(), false, ACCENT_DEV);
    });

    FlatButton cancel = new FlatButton("Cancel", null, null);
    cancel.addActionListener(e -> dialog.dispose());

    FlatButton apply = new FlatButton("Apply", uiIcon(VectorIcon.Kind.CHECK, 14, ACCENT_DEV), ACCENT_DEV);
    apply.addActionListener(e -> {
      gradleStacktraceEnabled = stacktrace.isSelected();
      gradleInfoLoggingEnabled = info.isSelected();
      gradleDebugLoggingEnabled = debug.isSelected();
      gradleOfflineEnabled = offline.isSelected();
      gradleRefreshDependenciesEnabled = refresh.isSelected() && !offline.isSelected();
      gradleNoBuildCacheEnabled = noBuildCache.isSelected();
      gradleNoDaemonEnabled = noDaemon.isSelected();
      gradleExtraArgs = extraArgs.getText() == null ? "" : extraArgs.getText().trim();
      dialog.dispose();
      setStatus("Gradle options updated", ACCENT_DEV);
      setActivity("Gradle options updated", describeGradleOptions(), false, ACCENT_DEV);
    });

    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, ui(8), 0));
    footer.setOpaque(false);
    footer.add(reset);
    footer.add(cancel);
    footer.add(apply);
    root.add(footer, BorderLayout.SOUTH);

    dialog.setContentPane(root);
    Dimension minimum = uiDimension(620, 430);
    dialog.setMinimumSize(minimum);
    dialog.pack();
    if (dialog.getWidth() < minimum.width || dialog.getHeight() < minimum.height) {
      dialog.setSize(new Dimension(
          Math.max(dialog.getWidth(), minimum.width),
          Math.max(dialog.getHeight(), minimum.height)));
    }
    dialog.setLocationRelativeTo(frame);
    dialog.getRootPane().setDefaultButton(apply);
    dialog.setVisible(true);
  }

  private void showJvmMemorySettingsDialog() {
    JDialog dialog = new JDialog(frame, "JVM Memory Settings", true);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    JPanel root = new JPanel(new BorderLayout(0, ui(14)));
    root.setBackground(BG);
    root.setBorder(uiPadding(16, 16, 16, 16));
    root.add(dialogHeader(
        "JVM Memory Settings",
        "Applied at process startup to Editor, Launcher, previews, and game runtime launches."),
        BorderLayout.NORTH);

    JCheckBox customInitial = optionCheckBox(
        "Set initial heap (-Xms)",
        "Reserve an exact initial Java heap. Automatic is normally best unless startup allocation is known.",
        jvmLaunchSettings.initialHeapMb() > 0);
    JSpinner initialHeap = new JSpinner(new SpinnerNumberModel(
        jvmLaunchSettings.initialHeapMb() > 0 ? jvmLaunchSettings.initialHeapMb() : 512,
        JvmLaunchSettings.MIN_HEAP_MB,
        JvmLaunchSettings.MAX_HEAP_MB,
        128));
    styleHubSpinner(initialHeap, uiDimension(110, 32));
    initialHeap.setEnabled(customInitial.isSelected());
    customInitial.addActionListener(event -> initialHeap.setEnabled(customInitial.isSelected()));

    JCheckBox customMaximum = optionCheckBox(
        "Set maximum heap (-Xmx)",
        "Set the exact Java heap ceiling. This does not include native JavaFX textures or process overhead.",
        jvmLaunchSettings.maxHeapMb() > 0);
    JSpinner maximumHeap = new JSpinner(new SpinnerNumberModel(
        jvmLaunchSettings.maxHeapMb() > 0 ? jvmLaunchSettings.maxHeapMb() : 4096,
        JvmLaunchSettings.MIN_HEAP_MB,
        JvmLaunchSettings.MAX_HEAP_MB,
        128));
    styleHubSpinner(maximumHeap, uiDimension(110, 32));
    maximumHeap.setEnabled(customMaximum.isSelected());
    customMaximum.addActionListener(event -> maximumHeap.setEnabled(customMaximum.isSelected()));

    JComboBox<JvmLaunchSettings.Collector> collector =
        new JComboBox<>(JvmLaunchSettings.Collector.values());
    styleHubComboBox(collector, uiDimension(230, 34));
    collector.setSelectedItem(jvmLaunchSettings.collector());
    collector.setToolTipText("G1 is balanced, ZGC favors low pauses, and Serial minimizes collector overhead for small heaps.");

    JCheckBox heapDump = optionCheckBox(
        "Write heap dump on OutOfMemoryError",
        "Writes an HPROF file under ~/.jvn/heap-dumps for real retained-object diagnosis.",
        jvmLaunchSettings.heapDumpOnOutOfMemory());
    JCheckBox exitOnOom = optionCheckBox(
        "Exit process after OutOfMemoryError",
        "Terminates a corrupted or unusable process promptly after the JVM reports an OOM.",
        jvmLaunchSettings.exitOnOutOfMemory());
    JCheckBox stringDedup = optionCheckBox(
        "Enable string deduplication",
        "Allows G1/ZGC to merge duplicate String backing arrays; useful for text-heavy projects at a small GC cost.",
        jvmLaunchSettings.stringDeduplication());

    JTextField extraArguments = gradleTextField(jvmLaunchSettings.extraJvmArgs());
    extraArguments.setToolTipText(
        "Advanced JVM options. Quotes preserve spaces. Heap, GC, OOM, and deduplication flags belong in the controls above.");

    JPanel options = new JPanel(new GridBagLayout());
    options.setBackground(PANEL_BG);
    options.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER_NEUTRAL),
        uiPadding(12, 14, 12, 14)));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets = new java.awt.Insets(0, 0, ui(7), 0);
    int row = 0;
    gbc.gridy = row++;
    options.add(memorySpinnerRow(customInitial, initialHeap), gbc);
    gbc.gridy = row++;
    options.add(memorySpinnerRow(customMaximum, maximumHeap), gbc);
    gbc.gridy = row++;
    options.add(labeledJvmControl("Garbage collector", collector), gbc);
    for (JCheckBox checkBox : List.of(heapDump, exitOnOom, stringDedup)) {
      gbc.gridy = row++;
      options.add(checkBox, gbc);
    }
    gbc.gridy = row++;
    gbc.insets = new java.awt.Insets(ui(7), 0, ui(5), 0);
    JLabel extraLabel = new JLabel("Extra JVM arguments (advanced)");
    extraLabel.setForeground(TEXT_MUTED);
    extraLabel.setFont(extraLabel.getFont().deriveFont(Font.BOLD, uiFont(10f)));
    options.add(extraLabel, gbc);
    gbc.gridy = row;
    gbc.insets = new java.awt.Insets(0, 0, 0, 0);
    options.add(extraArguments, gbc);
    root.add(options, BorderLayout.CENTER);

    FlatButton reset = new FlatButton("Reset to JDK Defaults", null, null);
    reset.addActionListener(event -> {
      JvmLaunchSettings defaults = JvmLaunchSettings.defaults();
      customInitial.setSelected(false);
      initialHeap.setEnabled(false);
      initialHeap.setValue(512);
      customMaximum.setSelected(false);
      maximumHeap.setEnabled(false);
      maximumHeap.setValue(4096);
      collector.setSelectedItem(defaults.collector());
      heapDump.setSelected(defaults.heapDumpOnOutOfMemory());
      exitOnOom.setSelected(defaults.exitOnOutOfMemory());
      stringDedup.setSelected(defaults.stringDeduplication());
      extraArguments.setText("");
    });

    FlatButton cancel = new FlatButton("Cancel", null, null);
    cancel.addActionListener(event -> dialog.dispose());

    FlatButton apply = new FlatButton(
        "Apply to Next Launch", uiIcon(VectorIcon.Kind.CHECK, 14, ACCENT_TOOLS), ACCENT_TOOLS);
    apply.addActionListener(event -> {
      JvmLaunchSettings candidate = new JvmLaunchSettings(
          customInitial.isSelected() ? ((Number) initialHeap.getValue()).intValue() : 0,
          customMaximum.isSelected() ? ((Number) maximumHeap.getValue()).intValue() : 0,
          (JvmLaunchSettings.Collector) collector.getSelectedItem(),
          heapDump.isSelected(),
          exitOnOom.isSelected(),
          stringDedup.isSelected(),
          extraArguments.getText());
      Optional<String> validationError = candidate.validationError();
      if (validationError.isPresent()) {
        javax.swing.JOptionPane.showMessageDialog(
            dialog, validationError.get(), "Invalid JVM Settings", javax.swing.JOptionPane.WARNING_MESSAGE);
        return;
      }
      if (!writeJvmLaunchArguments(candidate, true)) return;
      dialog.dispose();
      refreshModeMenus();
      setStatus("JVM memory settings saved", ACCENT_TOOLS);
      setActivity(
          "JVM settings ready for next launch",
          candidate.summary(),
          false,
          ACCENT_TOOLS);
    });

    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, ui(8), 0));
    footer.setOpaque(false);
    footer.add(reset);
    footer.add(cancel);
    footer.add(apply);
    root.add(footer, BorderLayout.SOUTH);

    dialog.setContentPane(root);
    Dimension minimum = uiDimension(680, 560);
    dialog.setMinimumSize(minimum);
    dialog.pack();
    if (dialog.getWidth() < minimum.width || dialog.getHeight() < minimum.height) {
      dialog.setSize(minimum);
    }
    dialog.setLocationRelativeTo(frame);
    dialog.getRootPane().setDefaultButton(apply);
    dialog.setVisible(true);
  }

  private JPanel memorySpinnerRow(JCheckBox checkBox, JSpinner spinner) {
    JPanel row = new JPanel(new BorderLayout(ui(10), 0));
    row.setOpaque(false);
    row.add(checkBox, BorderLayout.CENTER);
    JPanel value = new JPanel(new FlowLayout(FlowLayout.RIGHT, ui(6), 0));
    value.setOpaque(false);
    JLabel unit = new JLabel("MiB");
    unit.setForeground(TEXT_MUTED);
    value.add(spinner);
    value.add(unit);
    row.add(value, BorderLayout.EAST);
    return row;
  }

  private JPanel labeledJvmControl(String label, JComponent control) {
    JPanel row = new JPanel(new BorderLayout(ui(10), 0));
    row.setOpaque(false);
    JLabel text = new JLabel(label);
    text.setForeground(TEXT_SOFT);
    row.add(text, BorderLayout.CENTER);
    row.add(control, BorderLayout.EAST);
    return row;
  }

  static <T> void styleHubComboBox(JComboBox<T> combo, Dimension preferredSize) {
    combo.setRenderer(new HubComboBoxRenderer());
    combo.setUI(new HubComboBoxUI());
    combo.setOpaque(true);
    combo.setBackground(BG);
    combo.setForeground(TEXT_PRIMARY);
    combo.setBorder(BorderFactory.createLineBorder(BORDER_NEUTRAL));
    combo.setFont(combo.getFont().deriveFont(Font.PLAIN, uiFont(12f)));
    combo.setPreferredSize(preferredSize);
  }

  static void styleHubSpinner(JSpinner spinner, Dimension preferredSize) {
    spinner.setUI(new HubSpinnerUI());
    spinner.setOpaque(true);
    spinner.setBackground(BG);
    spinner.setForeground(TEXT_PRIMARY);
    spinner.setBorder(BorderFactory.createLineBorder(BORDER_NEUTRAL));
    spinner.setPreferredSize(preferredSize);
    if (spinner.getEditor() instanceof JSpinner.DefaultEditor defaultEditor) {
      JTextField field = defaultEditor.getTextField();
      defaultEditor.setOpaque(true);
      defaultEditor.setBackground(BG);
      defaultEditor.setBorder(null);
      field.setOpaque(true);
      field.setBackground(BG);
      field.setForeground(TEXT_PRIMARY);
      field.setCaretColor(TEXT_PRIMARY);
      field.setSelectionColor(HOVER_BG);
      field.setSelectedTextColor(TEXT_PRIMARY);
      field.setDisabledTextColor(TEXT_MUTED);
      field.setHorizontalAlignment(SwingConstants.RIGHT);
      field.setBorder(uiPadding(0, 8, 0, 8));
      field.setFont(field.getFont().deriveFont(Font.PLAIN, uiFont(12f)));
    }
  }

  private boolean writeJvmLaunchArguments(JvmLaunchSettings settings, boolean persistSettings) {
    try {
      if (persistSettings) settings.save(jvmLaunchSettingsFile);
      if (settings.heapDumpOnOutOfMemory()) Files.createDirectories(heapDumpDirectory);
      settings.writeArguments(jvmLaunchArgumentsFile, heapDumpDirectory);
      jvmLaunchSettings = settings;
      return true;
    } catch (IOException | IllegalArgumentException error) {
      if (frame.isShowing()) {
        javax.swing.JOptionPane.showMessageDialog(
            frame,
            "Could not save JVM launch settings:\n" + exceptionMessage(error),
            "JVM Settings Error",
            javax.swing.JOptionPane.ERROR_MESSAGE);
      }
      return false;
    }
  }

  private Map<String, String> managedApplicationEnvironment(String taskOrApp) {
    String value = taskOrApp == null ? "" : taskOrApp.trim().toLowerCase(Locale.ROOT);
    boolean managedApplication = value.equals("editor")
        || value.equals("launcher")
        || value.equals("runtime")
        || value.equals(":editor:run")
        || value.equals(":editor:runlauncher")
        || value.equals(":runtime:run");
    if (!managedApplication) return Map.of();
    writeJvmLaunchArguments(jvmLaunchSettings, false);
    return Map.of("JVN_APP_JAVA_OPTS_FILE", jvmLaunchArgumentsFile.toAbsolutePath().toString());
  }

  private JCheckBox optionCheckBox(String label, String tooltip, boolean selected) {
    JCheckBox box = new HelpCheckBox(label, selected);
    box.setUI(new BasicCheckBoxUI());
    box.setToolTipText(tooltip);
    box.setOpaque(false);
    box.setForeground(TEXT_SOFT);
    box.setFocusPainted(false);
    box.setIcon(new HubCheckIcon(false));
    box.setSelectedIcon(new HubCheckIcon(true));
    box.setDisabledIcon(new HubCheckIcon(false));
    box.setDisabledSelectedIcon(new HubCheckIcon(true));
    box.setFont(box.getFont().deriveFont(Font.PLAIN, uiFont(12.5f)));
    box.setBorder(uiPadding(4, 2, 4, 2));
    box.setAlignmentX(Component.LEFT_ALIGNMENT);
    return box;
  }

  private JTextField gradleTextField(String value) {
    JTextField field = new HelpTextField(value == null ? "" : value);
    field.setForeground(TEXT_PRIMARY);
    field.setBackground(BG);
    field.setCaretColor(TEXT_PRIMARY);
    field.setSelectionColor(new Color(60, 110, 160));
    field.setSelectedTextColor(TEXT_PRIMARY);
    field.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER_NEUTRAL),
        uiPadding(8, 10, 8, 10)));
    Dimension size = uiDimension(560, 38);
    field.setPreferredSize(size);
    field.setMaximumSize(new Dimension(Integer.MAX_VALUE, size.height));
    return field;
  }

  private void showDiagnosticsReport() {
    setStatus("Running health check", ACCENT_NEUTRAL);
    setActivity("Running diagnostics", "Checking local engine setup.", true, ACCENT_NEUTRAL);
    setButtonsEnabled(false);

    new SwingWorker<List<HealthCheck>, Void>() {
      @Override protected List<HealthCheck> doInBackground() {
        return runHealthChecks();
      }

      @Override protected void done() {
        setButtonsEnabled(true);
        List<HealthCheck> checks;
        try {
          checks = get();
        } catch (Exception e) {
          checks = List.of(new HealthCheck(
              CheckStatus.FAIL,
              "Diagnostics",
              "Health check failed to run.",
              e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        long failures = checks.stream().filter(c -> c.status() == CheckStatus.FAIL).count();
        long warnings = checks.stream().filter(c -> c.status() == CheckStatus.WARN).count();
        Color tone = failures > 0 ? ACCENT_ERROR : warnings > 0 ? ACCENT_NEUTRAL : ACCENT_GREEN;
        String title = failures > 0
            ? "Health check found " + failures + " issue" + (failures == 1 ? "" : "s")
            : warnings > 0
                ? "Health check found " + warnings + " warning" + (warnings == 1 ? "" : "s")
                : "Health check passed";
        setStatus(title, tone);
        setActivity(title, "Diagnostics report is ready.", false, tone);
        showReportDialog("Diagnostics / Health Check", checks, healthSummary(checks));
      }
    }.execute();
  }

  private void showModuleInventory() {
    List<String> modules = discoverEngineModules(projectRoot);
    List<HealthCheck> report = modules.stream()
        .map(module -> new HealthCheck(
            CheckStatus.INFO,
            module,
            "JVN engine module",
            projectRoot.resolve("modules").resolve(module).toString()))
        .toList();
    if (report.isEmpty()) {
      report = List.of(new HealthCheck(
          CheckStatus.WARN,
          "No modules found",
          "The engine module inventory is empty.",
          projectRoot.resolve("modules").toString()));
    }
    setStatus("Module inventory ready", ACCENT_NEUTRAL);
    setActivity(
        "Engine modules",
        modules.size() + " module" + (modules.size() == 1 ? "" : "s") + " discovered.",
        false,
        ACCENT_NEUTRAL);
    showReportDialog(
        "Engine Module Inventory",
        report,
        modules.size() + " configured engine module" + (modules.size() == 1 ? "" : "s"));
  }

  static List<String> discoverEngineModules(Path engineRoot) {
    if (engineRoot == null) return List.of();
    Path modulesRoot = engineRoot.resolve("modules");
    if (!Files.isDirectory(modulesRoot)) return List.of();
    try (var entries = Files.list(modulesRoot)) {
      return entries
          .filter(Files::isDirectory)
          .filter(path -> Files.isRegularFile(path.resolve("build.gradle.kts"))
              || Files.isRegularFile(path.resolve("build.gradle")))
          .map(path -> path.getFileName().toString())
          .sorted(String.CASE_INSENSITIVE_ORDER)
          .toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private void showEngineConfiguration() {
    String buildDir = firstNonBlank(readGradleProperty("jvnBuildDir"), "build/ (workspace default)");
    List<HealthCheck> report = List.of(
        new HealthCheck(
            CheckStatus.INFO,
            "Engine version",
            displayVersionLabel(readDiskVersion()),
            "Configured by jvnVersion."),
        new HealthCheck(
            CheckStatus.INFO,
            "Java toolchain",
            "Java " + firstNonBlank(readGradleProperty("javaVersion"), "not configured"),
            "Running Hub JVM: " + firstNonBlank(System.getProperty("java.version"), "unknown")),
        new HealthCheck(
            CheckStatus.INFO,
            "JavaFX",
            firstNonBlank(readGradleProperty("jvnJavaFxVersion"), "not configured"),
            "Shared JavaFX dependency version."),
        new HealthCheck(
            CheckStatus.INFO,
            "Build execution",
            "Parallel: " + enabledLabel(readGradleProperty("org.gradle.parallel"))
                + " · Cache: " + enabledLabel(readGradleProperty("org.gradle.caching")),
            describeGradleOptions()),
        new HealthCheck(
            CheckStatus.INFO,
            "Build output",
            buildDir,
            "Workspace build-output location."),
        new HealthCheck(
            CheckStatus.INFO,
            "Gradle wrapper",
            gradleCommand(),
            "Wrapper used for Hub build actions."));
    setStatus("Engine configuration ready", ACCENT_NEUTRAL);
    setActivity("Engine configuration", "Build and runtime properties loaded.", false, ACCENT_NEUTRAL);
    showReportDialog(
        "Engine Configuration",
        report,
        displayVersionLabel(readDiskVersion()) + " build environment");
  }

  private static String enabledLabel(String value) {
    return Boolean.parseBoolean(firstNonBlank(value, "false")) ? "enabled" : "disabled";
  }

  private void openEngineConfiguration() {
    Path configuration = projectRoot.resolve("gradle.properties");
    if (!Files.isRegularFile(configuration)) {
      setStatus("gradle.properties not found", ACCENT_ERROR);
      setActivity("Open configuration failed", configuration.toString(), false, ACCENT_ERROR);
      return;
    }
    try {
      java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
      if (desktop.isSupported(java.awt.Desktop.Action.EDIT)) {
        desktop.edit(configuration.toFile());
      } else {
        desktop.open(configuration.toFile());
      }
      setStatus("Opened engine configuration", ACCENT_NEUTRAL);
    } catch (Exception e) {
      setStatus("Could not open engine configuration", ACCENT_ERROR);
      setActivity(
          "Open configuration failed",
          e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
          false,
          ACCENT_ERROR);
    }
  }

  private void copyEngineEnvironmentSummary() {
    String summary = String.join("\n",
        "JVN " + displayVersionLabel(readDiskVersion()),
        "Mode: " + (isRunningFromSource() ? "source" : "packaged"),
        "Revision: " + readGitValue(List.of("git", "rev-parse", "--short", "HEAD"), "unknown"),
        "Branch: " + resolveBranch(projectRoot),
        "Modules: " + discoverEngineModules(projectRoot).size(),
        "Java: " + firstNonBlank(System.getProperty("java.version"), "unknown")
            + " (toolchain " + firstNonBlank(readGradleProperty("javaVersion"), "unknown") + ")",
        "JavaFX: " + firstNonBlank(readGradleProperty("jvnJavaFxVersion"), "unknown"),
        "OS: " + firstNonBlank(System.getProperty("os.name"), "unknown")
            + " " + firstNonBlank(System.getProperty("os.arch"), "unknown"),
        "Engine root: " + projectRoot.toAbsolutePath());
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(summary), null);
    setStatus("Copied engine environment summary", ACCENT_NEUTRAL);
    setActivity("Environment summary copied", "Ready to paste into an issue or support request.", false, ACCENT_NEUTRAL);
  }

  private List<HealthCheck> runHealthChecks() {
    List<HealthCheck> checks = new ArrayList<>();

    int requiredJava = readRequiredJavaVersion();
    int runtimeJava = parseJavaMajor(System.getProperty("java.version", ""));
    boolean javaOk = requiredJava <= 0 || runtimeJava <= 0 || runtimeJava >= requiredJava;
    checks.add(new HealthCheck(
        javaOk ? CheckStatus.PASS : CheckStatus.FAIL,
        "Java runtime",
        "Running Java " + firstNonBlank(System.getProperty("java.version"), "unknown")
            + (requiredJava > 0 ? "; project requests Java " + requiredJava + "." : "."),
        "java.home=" + firstNonBlank(System.getProperty("java.home"), "unknown")));

    Path wrapper = Path.of(gradleCommand());
    boolean wrapperExists = Files.isRegularFile(wrapper);
    boolean wrapperRunnable = wrapperExists && (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
        || Files.isExecutable(wrapper));
    checks.add(new HealthCheck(
        wrapperRunnable ? CheckStatus.PASS : CheckStatus.FAIL,
        "Gradle wrapper",
        wrapperRunnable ? "Wrapper is present and runnable." : "Gradle wrapper is missing or not executable.",
        wrapper.toString()));

    Path gitDir = projectRoot.resolve(".git");
    CommandResult status = commandExists("git") && Files.isDirectory(gitDir)
        ? runGit(List.of("git", "-c", "core.quotePath=false", "status", "--porcelain=v1", "--untracked-files=all"), 8)
        : new CommandResult(-1, commandExists("git") ? ".git directory not found" : "git command not found");
    if (status.exitCode == 0) {
      List<GitStatusEntry> entries = parseGitStatus(status.output);
      checks.add(new HealthCheck(
          entries.isEmpty() ? CheckStatus.PASS : CheckStatus.WARN,
          "Repository status",
          entries.isEmpty()
              ? "Working tree is clean."
              : entries.size() + " local change" + (entries.size() == 1 ? "" : "s") + " detected.",
          entries.isEmpty() ? "git status --porcelain returned no changes." : summarizeEntries(entries)));
    } else {
      checks.add(new HealthCheck(
          CheckStatus.WARN,
          "Repository status",
          "Git status is unavailable.",
          status.output.strip()));
    }

    String javafxVersion = readGradleProperty("jvnJavaFxVersion");
    boolean hasJavaFxConfig = javafxVersion != null && !javafxVersion.isBlank()
        && Files.isRegularFile(projectRoot.resolve("modules/editor/build.gradle.kts"));
    checks.add(new HealthCheck(
        hasJavaFxConfig ? CheckStatus.PASS : CheckStatus.WARN,
        "JavaFX configuration",
        hasJavaFxConfig
            ? "Editor JavaFX runtime is configured for version " + javafxVersion + "."
            : "JavaFX configuration could not be confirmed.",
        "Validated gradle.properties and modules/editor/build.gradle.kts."));

    Path stateDir = projectRoot.resolve(".jvn");
    checks.add(checkWritableStateDirectory(stateDir));

    int incoming = readIncomingCommitCount();
    checks.add(new HealthCheck(
        incoming < 0 ? CheckStatus.WARN : CheckStatus.PASS,
        "Update status",
        incoming > 0
            ? incoming + " incoming commit" + (incoming == 1 ? "" : "s") + " available."
            : incoming == 0
                ? "Engine appears up to date with " + ENGINE_UPDATE_REMOTE_REF + "."
                : "Incoming update count is unavailable.",
        incoming < 0
            ? "Git unavailable, " + ENGINE_UPDATE_REMOTE_REF + " unavailable, or rev-list failed."
            : "Compared HEAD.." + ENGINE_UPDATE_REMOTE_REF + "."));

    return checks;
  }

  private HealthCheck checkWritableStateDirectory(Path stateDir) {
    try {
      Files.createDirectories(stateDir);
      Path probe = Files.createTempFile(stateDir, "health-", ".tmp");
      Files.writeString(probe, "ok", StandardCharsets.UTF_8);
      Files.deleteIfExists(probe);
      return new HealthCheck(
          CheckStatus.PASS,
          "Writable .jvn state",
          "Hub state directory is writable.",
          stateDir.toAbsolutePath().toString());
    } catch (IOException e) {
      return new HealthCheck(
          CheckStatus.FAIL,
          "Writable .jvn state",
          "Hub state directory is not writable.",
          stateDir.toAbsolutePath() + "\n" + e.getMessage());
    }
  }

  private String healthSummary(List<HealthCheck> checks) {
    long failures = checks.stream().filter(c -> c.status() == CheckStatus.FAIL).count();
    long warnings = checks.stream().filter(c -> c.status() == CheckStatus.WARN).count();
    return failures > 0
        ? failures + " issue" + (failures == 1 ? "" : "s") + " need attention"
        : warnings > 0
            ? warnings + " warning" + (warnings == 1 ? "" : "s") + " to review"
            : "All lightweight checks passed";
  }

  private void showReportDialog(String title, List<HealthCheck> checks, String summary) {
    JDialog dialog = new JDialog(frame, title, true);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    JPanel root = new JPanel(new BorderLayout(0, ui(12)));
    root.setBackground(BG);
    root.setBorder(uiPadding(16, 16, 16, 16));

    root.add(dialogHeader(title, summary), BorderLayout.NORTH);

    JPanel rows = new JPanel();
    rows.setBackground(BG);
    rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
    for (int i = 0; i < checks.size(); i++) {
      rows.add(healthCheckCard(checks.get(i)));
      if (i < checks.size() - 1) rows.add(Box.createVerticalStrut(ui(8)));
    }

    JScrollPane scroll = new JScrollPane(rows);
    scroll.setBorder(BorderFactory.createLineBorder(BORDER_NEUTRAL));
    scroll.setBackground(BG);
    scroll.getViewport().setBackground(BG);
    scroll.setPreferredSize(uiDimension(620, 390));
    styleScrollBar(scroll.getVerticalScrollBar());
    styleScrollBar(scroll.getHorizontalScrollBar());
    root.add(scroll, BorderLayout.CENTER);
    root.add(dialogFooter(dialog), BorderLayout.SOUTH);

    dialog.setContentPane(root);
    dialog.pack();
    dialog.setLocationRelativeTo(frame);
    dialog.setVisible(true);
  }

  private JPanel healthCheckCard(HealthCheck check) {
    JPanel card = new JPanel(new BorderLayout(ui(10), 0));
    card.setBackground(PANEL_BG);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(check.status().color()),
        uiPadding(10, 12, 10, 12)));

    JLabel icon = new JLabel(uiIcon(check.status().icon(), 18, check.status().color()));
    card.add(icon, BorderLayout.WEST);

    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
    JLabel title = new JLabel(check.title());
    title.setForeground(TEXT_PRIMARY);
    title.setFont(title.getFont().deriveFont(Font.BOLD, uiFont(13f)));
    JTextArea summary = dialogText(check.summary(), TEXT_SOFT, 11f, Font.PLAIN);
    JTextArea details = dialogText(check.details(), TEXT_MUTED, 10f, Font.PLAIN);
    details.setBorder(uiPadding(4, 0, 0, 0));
    title.setAlignmentX(Component.LEFT_ALIGNMENT);
    summary.setAlignmentX(Component.LEFT_ALIGNMENT);
    details.setAlignmentX(Component.LEFT_ALIGNMENT);
    text.add(title);
    text.add(Box.createVerticalStrut(ui(2)));
    text.add(summary);
    if (check.details() != null && !check.details().isBlank()) {
      text.add(details);
    }
    card.add(text, BorderLayout.CENTER);
    return card;
  }

  private void showAboutReport() {
    String version = displayVersionLabel(readDiskVersion());
    String sourceMode = isRunningFromSource() ? "Running from source" : "Packaged build";
    String commit = readGitValue(List.of("git", "rev-parse", "--short", "HEAD"), "unknown");
    String branch = readGitValue(List.of("git", "rev-parse", "--abbrev-ref", "HEAD"), "unknown");
    int incoming = readIncomingCommitCount();
    String updateStatus = incoming > 0
        ? incoming + " incoming commit" + (incoming == 1 ? "" : "s") + " available"
        : incoming == 0
            ? "Up to date"
            : "Unavailable";

    List<HealthCheck> report = List.of(
        new HealthCheck(CheckStatus.INFO, "Version", version, sourceMode),
        new HealthCheck(CheckStatus.INFO, "Source / build mode", sourceMode,
            isRunningFromSource()
                ? "Launched from compiled classes in the engine checkout."
                : "Launched from a packaged runtime artifact."),
        new HealthCheck(CheckStatus.INFO, "Git revision",
            commit + ("unknown".equals(branch) ? "" : " on " + branch),
            "Current engine checkout revision."),
        new HealthCheck(CheckStatus.INFO, "Install path",
            projectRoot.toAbsolutePath().toString(),
            "The hub uses this folder as its engine workspace."),
        new HealthCheck(CheckStatus.INFO,
            "Developer Mode",
            developerModeEnabled ? "Enabled" : "Disabled",
            developerModeEnabled
                ? "Run Tests is visible and launch commands receive developer-mode flags."
                : "Developer-focused actions are hidden from the main action grid."),
        new HealthCheck(CheckStatus.INFO,
            "Gradle options",
            describeGradleOptions(),
            developerModeEnabled
                ? "These options are applied before the Gradle task name."
                : "Enable Developer Mode to apply configurable Gradle options."),
        new HealthCheck(safeModeEnabled ? CheckStatus.WARN : CheckStatus.INFO,
            "Safe Mode",
            safeModeEnabled ? "Enabled" : "Disabled",
            safeModeEnabled
                ? "Editor-side launches receive safe-mode flags while preserving the normal Gradle cache."
                : "Editor-side launches use the standard startup path."),
        new HealthCheck(incoming > 0 ? CheckStatus.WARN : incoming == 0 ? CheckStatus.PASS : CheckStatus.INFO,
            "Update status", updateStatus, "Compared HEAD.." + ENGINE_UPDATE_REMOTE_REF + "."),
        new HealthCheck(CheckStatus.INFO, "Java runtime",
            firstNonBlank(System.getProperty("java.version"), "unknown"),
            "java.home=" + firstNonBlank(System.getProperty("java.home"), "unknown")),
        new HealthCheck(CheckStatus.INFO, "Operating system",
            firstNonBlank(System.getProperty("os.name"), "unknown"),
            firstNonBlank(System.getProperty("os.arch"), "unknown")),
        new HealthCheck(CheckStatus.INFO, "Display scale",
            displayScaleSummary(),
            displayScaleDetails())
    );
    setStatus("Engine info ready", ACCENT_NEUTRAL);
    setActivity("Engine info", version + " · " + sourceMode, false, ACCENT_NEUTRAL);
    showReportDialog("About / Version Info", report, version + " · " + sourceMode);
  }

  private JPanel dialogHeader(String titleText, String subtitleText) {
    JLabel title = new JLabel(titleText);
    title.setForeground(TEXT_PRIMARY);
    title.setFont(title.getFont().deriveFont(Font.BOLD, uiFont(16f)));
    JLabel subtitle = new JLabel(subtitleText == null ? "" : subtitleText);
    subtitle.setForeground(TEXT_MUTED);
    subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, uiFont(11f)));

    JPanel box = new JPanel();
    box.setOpaque(false);
    box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
    title.setAlignmentX(Component.LEFT_ALIGNMENT);
    subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
    box.add(title);
    box.add(Box.createVerticalStrut(ui(2)));
    box.add(subtitle);
    return box;
  }

  private JPanel dialogFooter(JDialog dialog) {
    FlatButton close = new FlatButton("Close",
        uiIcon(VectorIcon.Kind.CLOSE, 14, TEXT_PRIMARY), null);
    close.addActionListener(e -> dialog.dispose());
    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    footer.setOpaque(false);
    footer.add(close);
    return footer;
  }

  private JButton makeAction(String label, String tooltip, VectorIcon.Kind iconKind,
                             boolean accent, Runnable action) {
    return makeAction(label, tooltip, iconKind, accent ? ACCENT_NEUTRAL : null, action);
  }

  private JButton makeAction(String label, String tooltip, VectorIcon.Kind iconKind,
                             Color accentOrNull, Runnable action) {
    Color foreground = accentOrNull != null ? accentOrNull : TEXT_PRIMARY;
    Icon icon = iconKind != null ? uiIcon(iconKind, 16, foreground) : null;
    FlatButton button = new FlatButton(label, icon, accentOrNull);
    button.setToolTipText(tooltip);
    button.addActionListener(e -> action.run());
    actionButtons.add(button);
    return button;
  }

  // --- Task execution --------------------------------------------------------

  /**
   * Runs {@code action} immediately, or first warns the user that the engine is behind
   * origin/stable when the last update check reported one or more incoming commits.
   * Choosing "Update Now" triggers {@link #updateEngine()}; "Run Anyway" proceeds with
   * the original action; closing the dialog cancels entirely.
   */
  private void guardedRun(String label, Runnable action) {
    if (lastKnownIncoming <= 0) {
      action.run();
      return;
    }
    String commitWord = lastKnownIncoming == 1 ? "commit" : "commits";
    int choice = showUpdateDialog(
        "Engine Update Available",
        "Your engine is " + lastKnownIncoming + " " + commitWord + " behind " + ENGINE_UPDATE_REMOTE_REF + ".",
        "Running '" + label + "' without updating may cause unexpected behaviour "
            + "or use outdated project files.\n\n"
            + "Click 'Update Now' to pull the latest changes first, "
            + "or 'Run Anyway' to continue with the current version.",
        new String[]{"Update Now", "Run Anyway"},
        UpdateDialogTone.WARNING);
    if (choice == 0) {
      updateEngine();
    } else {
      action.run();
    }
  }

  private void runGradle(String task, String label) {
    if (!acquire(label)) return;
    List<String> cmd = new ArrayList<>();
    cmd.add(gradleCommand());
    cmd.add("--console=plain");
    if (RenderPipelineSettings.isManagedGradleTask(task)) {
      cmd.add("-D" + RenderPipelineSettings.GRAPHICS_MODE_PROPERTY + "=" + renderPipelineMode.id());
    }
    if (developerModeEnabled) {
      cmd.add("-Djvn.hub.developerMode=true");
      cmd.add("-Djvn.editor.developerMode=true");
      cmd.add("-Djvn.launcher.developerMode=true");
      cmd.add("-Djvn.help.developerMode=true");
      cmd.addAll(developerGradleOptions());
    }
    if (safeModeEnabled) {
      cmd.add("-Djvn.hub.safeMode=true");
      cmd.add("-Djvn.editor.safeMode=true");
      cmd.add("-Djvn.launcher.safeMode=true");
      cmd.add("-Djvn.help.safeMode=true");
    }
    if (shouldPreferConfigurationCache(task)) {
      cmd.add("--configuration-cache");
    }
    if (shouldLimitLaunchWorkers(task)) {
      cmd.add("--max-workers=" + balancedLaunchWorkerCount());
    }
    cmd.add(task);
    completeCurrentStep("Gradle command assembled.");
    advanceStep(modeLaunchDetail());
    appendLog("$ " + String.join(" ", cmd));
    startProcess(cmd, label, managedApplicationEnvironment(task));
  }

  private void runFastApp(String app, String label) {
    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
      runGradle(app.equals("runtime") ? ":runtime:run" : ":editor:run", label);
      return;
    }
    if (!acquire(label)) return;
    List<String> cmd = new ArrayList<>();
    cmd.add(projectRoot.resolve("scripts").resolve("launch-app.sh").toString());
    cmd.add(app);
    if (developerModeEnabled) cmd.add("--developer-mode");
    if (safeModeEnabled) cmd.add("--safe-mode");
    completeCurrentStep("Cache-first launch command assembled.");
    advanceStep(modeLaunchDetail());
    appendLog("$ " + String.join(" ", cmd));
    startProcess(cmd, label, managedApplicationEnvironment(app));
  }

  private void runProfiledEditor() {
    if (!acquire("Profile Editor")) return;
    List<String> cmd = new ArrayList<>();
    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
      cmd.add(gradleCommand());
      cmd.add("--console=plain");
      cmd.add("-D" + RenderPipelineSettings.GRAPHICS_MODE_PROPERTY + "=" + renderPipelineMode.id());
      cmd.add("--max-workers=" + balancedLaunchWorkerCount());
      cmd.add(":editor:run");
    } else {
      cmd.add(projectRoot.resolve("scripts").resolve("launch-app.sh").toString());
      cmd.add("editor");
      cmd.add("--jfr");
      if (developerModeEnabled) cmd.add("--developer-mode");
      if (safeModeEnabled) cmd.add("--safe-mode");
    }
    completeCurrentStep("Profiled launch command assembled.");
    advanceStep("Starting the editor with Java Flight Recorder.");
    appendLog("$ " + String.join(" ", cmd));
    startProcess(cmd, "Profile Editor", Map.of(
        "JVN_PROFILE_JFR", "1",
        "JVN_PROFILE_DIR", RenderPipelineSettings.profileDirectory(System.getProperty("os.name", ""),
            Path.of(System.getProperty("user.home", ".")), System.getenv()).toString(),
        "JVN_APP_JAVA_OPTS_FILE", jvmLaunchArgumentsFile.toAbsolutePath().toString()));
  }

  private String modeLaunchDetail() {
    if (developerModeEnabled && safeModeEnabled) return "Starting background process in Developer + Safe Mode.";
    if (developerModeEnabled) return "Starting background process in Developer Mode.";
    if (safeModeEnabled) return "Starting background process in Safe Mode.";
    return "Starting background process.";
  }

  private boolean shouldPreferConfigurationCache(String task) {
    if (safeModeEnabled) return false;
    if (hasConfigurationCacheFlag()) return false;
    return switch (task) {
      case ":editor:run", ":runtime:run",
          "build", "test", "check", "ci", "compileAll", "quickCheck" -> true;
      default -> false;
    };
  }

  private boolean hasConfigurationCacheFlag() {
    for (String arg : splitExtraGradleArgs(gradleExtraArgs)) {
      if (arg.equals("--configuration-cache")
          || arg.startsWith("--configuration-cache=")
          || arg.equals("--no-configuration-cache")
          || arg.equals("-Dorg.gradle.configuration-cache")
          || arg.startsWith("-Dorg.gradle.configuration-cache=")) {
        return true;
      }
    }
    return false;
  }

  private boolean shouldLimitLaunchWorkers(String task) {
    if (hasMaxWorkersFlag()) return false;
    return switch (task) {
      case ":editor:run", ":runtime:run" -> true;
      default -> false;
    };
  }

  private boolean hasMaxWorkersFlag() {
    for (String arg : splitExtraGradleArgs(gradleExtraArgs)) {
      if (arg.equals("--max-workers") || arg.startsWith("--max-workers=")) {
        return true;
      }
    }
    return false;
  }

  private int balancedLaunchWorkerCount() {
    int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
    return Math.max(2, processors <= 4 ? 2 : processors - 2);
  }

  private List<String> developerGradleOptions() {
    List<String> options = new ArrayList<>();
    if (gradleStacktraceEnabled) options.add("--stacktrace");
    if (gradleDebugLoggingEnabled) {
      options.add("--debug");
    } else if (gradleInfoLoggingEnabled) {
      options.add("--info");
    }
    if (gradleOfflineEnabled) {
      options.add("--offline");
    } else if (gradleRefreshDependenciesEnabled) {
      options.add("--refresh-dependencies");
    }
    if (gradleNoBuildCacheEnabled) options.add("--no-build-cache");
    if (gradleNoDaemonEnabled) options.add("--no-daemon");
    options.addAll(splitExtraGradleArgs(gradleExtraArgs));
    return options;
  }

  private String describeGradleOptions() {
    if (!developerModeEnabled) return "Developer Mode is off.";
    List<String> options = developerGradleOptions();
    return options.isEmpty() ? "No additional Gradle flags." : String.join(" ", options);
  }

  private void openDocumentationWebsite() {
    openWebsite("Documentation website", PUBLIC_DOCUMENTATION_URL);
  }

  private void openSourceRepository() {
    openWebsite("Source repository", SOURCE_REPOSITORY_URL);
  }

  private void openWebsite(String label, String url) {
    try {
      if (!java.awt.Desktop.isDesktopSupported()
          || !java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
        throw new IOException("Desktop browser integration is not available.");
      }
      java.awt.Desktop.getDesktop().browse(URI.create(url));
      setActivity(label, url, false, ACCENT_NEUTRAL);
    } catch (Exception ex) {
      String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
      appendLog("[hub] could not open " + label.toLowerCase(Locale.ROOT) + ": " + detail);
      setActivity("Open website failed", detail, false, ACCENT_ERROR);
    }
  }

  private void updateEngine() {
    if (runningProcess.get() != null) {
      appendLog("[hub] a task is already running; wait for it to finish or cancel it.");
      return;
    }
    UpdateProgressDialog progress = showUpdateProgress();
    progress.begin(
        "Preparing engine update",
        "Inspecting the checkout before integrating " + ENGINE_UPDATE_REMOTE_REF + ".",
        "Preflight status: running\nRemote target: " + ENGINE_UPDATE_REMOTE_REF);
    if (updateEngineButton != null) updateEngineButton.setChecking(true);
    setButtonsEnabled(false);
    startSteps("Update Engine");
    setActivity("Inspecting update readiness", "Checking Git worktree before pulling.", true, ACCENT_NEUTRAL);
    new SwingWorker<UpdatePreflightInspection, Void>() {
      @Override protected UpdatePreflightInspection doInBackground() {
        String beforeRevision = readGitValue(
            List.of("git", "rev-parse", "--short", "HEAD"), "unknown");
        String branch = readGitValue(
            List.of("git", "rev-parse", "--abbrev-ref", "HEAD"), "unknown");
        String version = readDiskVersion();
        return new UpdatePreflightInspection(
            inspectUpdatePreflight(),
            new UpdateAttempt(beforeRevision, branch, version, System.nanoTime()));
      }

      @Override protected void done() {
        setButtonsEnabled(true);
        UpdatePreflightInspection inspection;
        try {
          inspection = get();
        } catch (Exception ex) {
          inspection = new UpdatePreflightInspection(
              UpdatePreflight.unavailable(exceptionMessage(ex)),
              new UpdateAttempt("unknown", "unknown", readDiskVersion(), System.nanoTime()));
        }
        activeUpdateAttempt.set(inspection.attempt());
        handleUpdatePreflight(inspection.preflight());
      }
    }.execute();
  }

  private void handleUpdatePreflight(UpdatePreflight preflight) {
    completeCurrentStep("Git worktree inspected.");
    updateProgress(
        "Engine update ready",
        "The checkout was inspected. Preparing the integration command.",
        "Preflight status: " + (preflight.statusUnavailable() ? "unavailable" : "complete")
            + "\nLocal changes: " + preflight.allChanges().size()
            + "\nInterrupted Git operations: " + preflight.interruptedGitOperations().size(),
        true,
        UpdateDialogTone.QUESTION);
    if (preflight.statusUnavailable()) {
      if (!confirmUpdateWithUnknownStatus(preflight.statusError())) {
        cancelUpdateLifecycle("Preflight status was unavailable and the update was cancelled.");
        return;
      }
    } else if (preflight.hasInterruptedGitOperation() || preflight.hasChanges()) {
      if (safeModeEnabled && preflight.hasInterruptedGitOperation()) {
        finishSteps(false, "Safe Mode recovery started.");
        recoverSafeModeUpdateFailure("Interrupted Git operation found before update.\n\n" + preflight.summary());
        return;
      }
      if (safeModeEnabled && preflight.hasChanges() && !preflight.onlyBuildOutput()) {
        completeCurrentStep("Local changes detected; Safe Mode will use Git autostash.");
        appendLog("[hub] Safe Mode: local changes detected; using git pull --rebase --autostash "
            + ENGINE_UPDATE_REMOTE + " " + ENGINE_UPDATE_BRANCH + ".");
        startUpdateEngine();
        return;
      }
      UpdatePreflightAction action = chooseUpdatePreflightAction(preflight);
      if (action == UpdatePreflightAction.CANCEL) {
        cancelUpdateLifecycle("Local checkout changes were left untouched.");
        return;
      }
      cleanBeforeUpdate(preflight);
      return;
    }
    startUpdateEngine();
  }

  private void startUpdateEngine() {
    if (!acquire("Update Engine")) return;
    if (updateEngineButton != null) updateEngineButton.setChecking(true);
    List<String> cmd = safeModeEnabled
        ? List.of("git", "pull", "--rebase", "--autostash", ENGINE_UPDATE_REMOTE, ENGINE_UPDATE_BRANCH)
        : List.of("git", "pull", "--rebase", ENGINE_UPDATE_REMOTE, ENGINE_UPDATE_BRANCH);
    completeCurrentStep("Git command assembled.");
    advanceStep("Fetching from " + ENGINE_UPDATE_REMOTE_REF + ".");
    if (safeModeEnabled) {
      appendLog("[hub] Safe Mode: Update Engine will autostash tracked local changes and auto-recover failed rebase state.");
    }
    updateProgress(
        "Updating JVN Engine Hub",
        "Fetching and integrating " + ENGINE_UPDATE_REMOTE_REF + ".",
        "Integration command: " + quoteCommandForLog(cmd)
            + "\nSafe Mode autostash: " + (safeModeEnabled ? "enabled" : "disabled")
            + "\nIntegration status: running",
        true,
        UpdateDialogTone.QUESTION);
    appendLog("$ " + String.join(" ", cmd));
    startProcess(cmd, "Update Engine");
  }

  private void cancelUpdateLifecycle(String technicalReason) {
    setButtonsEnabled(true);
    if (updateEngineButton != null) updateEngineButton.setChecking(false);
    finishSteps(false, "Update cancelled before pull.");
    setActivity("Update cancelled", "No engine files were changed.", false, TEXT_MUTED);
    updateProgress(
        "Engine update cancelled",
        "No update was integrated and the Hub will remain open.",
        "Integration status: cancelled\n" + technicalReason,
        false,
        UpdateDialogTone.WARNING);
  }

  private UpdatePreflight inspectUpdatePreflight() {
    CommandResult status = runGit(
        List.of("git", "-c", "core.quotePath=false", "status", "--porcelain=v1", "--untracked-files=all"),
        10);
    if (status.exitCode != 0) {
      return UpdatePreflight.unavailable(status.output.strip().isBlank()
          ? "git status failed with exit " + status.exitCode
          : status.output.strip());
    }
    return UpdatePreflight.from(parseGitStatus(status.output), inspectInterruptedGitOperations());
  }

  private boolean confirmUpdateWithUnknownStatus(String details) {
    String[] options = {"Update Anyway", "Cancel"};
    int choice = showUpdateDialog(
        "Update Engine",
        "The hub could not check whether local engine files have changed.",
        "Updating may still work, but Git may stop if local files would be overwritten.\n\n" + details,
        options,
        UpdateDialogTone.WARNING);
    return choice == 0;
  }

  private UpdatePreflightAction chooseUpdatePreflightAction(UpdatePreflight preflight) {
    if (preflight.hasInterruptedGitOperation()) {
      String[] options = {"Abort Git Operation and Update", "Cancel"};
      int choice = showUpdateDialog(
          "Interrupted Git Update Found",
          "Update Engine found an unfinished Git operation in the engine checkout.",
          "This usually happens after a previous update failed during rebase or conflict resolution. "
              + "The Hub can abort that unfinished Git operation, clean the checkout, and retry the update.\n\n"
              + preflight.summary(),
          options,
          UpdateDialogTone.WARNING);
      return choice == 0 ? UpdatePreflightAction.CLEAN_AND_UPDATE : UpdatePreflightAction.CANCEL;
    }

    if (preflight.onlyBuildOutput()) {
      String[] options = {"Clear Build Output and Update", "Cancel"};
      int choice = showUpdateDialog(
          "Clear Build Output?",
          "Update Engine found generated build output in the engine checkout.",
          "Build output can be safely recreated. Clear these files before updating?\n\n"
              + preflight.summary(),
          options,
          UpdateDialogTone.QUESTION);
      return choice == 0 ? UpdatePreflightAction.CLEAN_AND_UPDATE : UpdatePreflightAction.CANCEL;
    }

    String[] options = {"Clean Changes and Update", "Cancel"};
    int choice = showUpdateDialog(
        "Local Engine Changes Found",
        "Update Engine found local changes that do not look like build output.",
        "Cleaning will permanently discard these local engine changes and delete untracked files.\n"
            + "Choose Cancel if you want to keep or commit them first.\n\n"
            + preflight.summary(),
        options,
        UpdateDialogTone.DANGER);
    return choice == 0 ? UpdatePreflightAction.CLEAN_AND_UPDATE : UpdatePreflightAction.CANCEL;
  }

  private int showUpdateDialog(
      String title,
      String message,
      String details,
      String[] options,
      UpdateDialogTone tone
  ) {
    boolean hasSecondaryOption = options != null
        && options.length > 1
        && options[1] != null
        && !options[1].isBlank();
    AtomicReference<Integer> result = new AtomicReference<>(hasSecondaryOption ? 1 : 0);

    JDialog dialog = new JDialog(frame, title, true);
    dialog.setUndecorated(true);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    dialog.getRootPane().setBorder(BorderFactory.createLineBorder(tone.borderColor(), 1));

    JPanel card = new JPanel(new BorderLayout(0, ui(16)));
    card.setBackground(PANEL_BG);
    card.setBorder(uiPadding(18, 20, 18, 20));

    JPanel header = new JPanel(new BorderLayout(ui(14), 0));
    header.setOpaque(false);
    header.add(new JLabel(new UpdateDialogIcon(tone, ui(34))), BorderLayout.WEST);

    JPanel titleStack = new JPanel();
    titleStack.setOpaque(false);
    titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));

    JLabel titleLabel = new JLabel(title == null || title.isBlank() ? "Update Engine" : title.trim());
    titleLabel.setForeground(TEXT_PRIMARY);
    titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, uiFont(17f)));
    titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

    JTextArea messageArea = dialogText(message, TEXT_SOFT, 12f, Font.BOLD);
    messageArea.setAlignmentX(Component.LEFT_ALIGNMENT);
    messageArea.setBorder(uiPadding(4, 0, 0, 0));

    titleStack.add(titleLabel);
    titleStack.add(messageArea);
    header.add(titleStack, BorderLayout.CENTER);

    JButton close = iconOnlyButton(uiIcon(VectorIcon.Kind.CLOSE, 12, TEXT_MUTED));
    close.addActionListener(e -> {
      result.set(hasSecondaryOption ? 1 : 0);
      dialog.dispose();
    });
    header.add(close, BorderLayout.EAST);
    card.add(header, BorderLayout.NORTH);

    JTextArea detailArea = dialogText(details, LOG_TEXT, 11f, Font.PLAIN);
    detailArea.setBorder(uiPadding(10, 12, 10, 12));
    detailArea.setCaretPosition(0);

    JScrollPane scroll = new JScrollPane(detailArea);
    scroll.setBorder(BorderFactory.createLineBorder(BORDER_NEUTRAL));
    scroll.setBackground(BG);
    scroll.getViewport().setBackground(BG);
    scroll.setPreferredSize(uiDimension(540, 190));
    styleScrollBar(scroll.getVerticalScrollBar());
    styleScrollBar(scroll.getHorizontalScrollBar());
    card.add(scroll, BorderLayout.CENTER);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, ui(10), 0));
    actions.setOpaque(false);

    String cancelLabel = hasSecondaryOption ? options[1] : "";
    String primaryLabel = options != null && options.length > 0 && options[0] != null ? options[0] : "Update";

    if (hasSecondaryOption) {
      FlatButton cancel = new FlatButton(cancelLabel, null, null);
      cancel.addActionListener(e -> {
        result.set(1);
        dialog.dispose();
      });
      actions.add(cancel);
    }

    FlatButton primary = new FlatButton(primaryLabel, null, tone.primaryColor());
    primary.addActionListener(e -> {
      result.set(0);
      dialog.dispose();
    });

    actions.add(primary);
    card.add(actions, BorderLayout.SOUTH);

    dialog.setContentPane(card);
    dialog.pack();
    dialog.setMinimumSize(uiDimension(620, 330));
    dialog.setLocationRelativeTo(frame);
    dialog.getRootPane().setDefaultButton(primary);
    dialog.getRootPane().registerKeyboardAction(
        e -> {
          result.set(hasSecondaryOption ? 1 : 0);
          dialog.dispose();
        },
        javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW);
    dialog.setVisible(true);

    return result.get();
  }

  private static JTextArea dialogText(String text, Color color, float size, int style) {
    JTextArea area = new JTextArea(text == null ? "" : text);
    area.setEditable(false);
    area.setFocusable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setOpaque(false);
    area.setForeground(color);
    area.setFont(area.getFont().deriveFont(style, uiFont(size)));
    return area;
  }

  private static JButton iconOnlyButton(Icon icon) {
    JButton button = new JButton(icon);
    button.setBorder(uiPadding(6, 6, 6, 6));
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setRolloverEnabled(true);
    button.setToolTipText("Close");
    return button;
  }

  private UpdateProgressDialog showUpdateProgress() {
    UpdateProgressDialog existing = updateProgressDialog.get();
    if (existing != null && existing.isDisplayable()) {
      existing.showCentered();
      return existing;
    }
    UpdateProgressDialog created = new UpdateProgressDialog();
    updateProgressDialog.set(created);
    created.showCentered();
    return created;
  }

  private void updateProgress(
      String title,
      String message,
      String details,
      boolean running,
      UpdateDialogTone tone
  ) {
    SwingUtilities.invokeLater(() -> {
      UpdateProgressDialog dialog = updateProgressDialog.get();
      if (dialog == null || !dialog.isDisplayable()) return;
      dialog.update(title, message, details, running, tone);
    });
  }

  private void appendUpdateProgressOutput(String line) {
    SwingUtilities.invokeLater(() -> {
      UpdateProgressDialog dialog = updateProgressDialog.get();
      if (dialog != null && dialog.isDisplayable()) dialog.appendTechnicalLine(line);
    });
  }

  private void cleanBeforeUpdate(UpdatePreflight preflight) {
    setButtonsEnabled(false);
    updateProgress(
        "Preparing the engine checkout",
        preflight.hasInterruptedGitOperation()
            ? "Recovering an interrupted Git operation before updating."
            : "Removing the approved local files before updating.",
        "Checkout cleanup: running\n"
            + (preflight.summary().isBlank() ? "No local file details." : preflight.summary()),
        true,
        UpdateDialogTone.WARNING);
    setStatus(preflight.hasInterruptedGitOperation() ? "Recovering Git checkout" : "Cleaning before update", ACCENT_NEUTRAL);
    startSteps("Clean Before Update");
    setActivity(
        preflight.hasInterruptedGitOperation()
            ? "Recovering Git checkout"
            : preflight.onlyBuildOutput() ? "Clearing build output" : "Cleaning local engine changes",
        "Preparing the engine checkout for update.",
        true,
        ACCENT_NEUTRAL);

    new SwingWorker<Boolean, String>() {
      private String failure = "";

      @Override protected Boolean doInBackground() {
        publish("[hub] cleaning local files before Update Engine...");
        CommandResult result = preflight.onlyBuildOutput() && !preflight.hasInterruptedGitOperation()
            ? cleanBuildOutputChanges(preflight)
            : cleanAllLocalChanges();
        if (result.exitCode != 0) {
          failure = result.output.strip().isBlank()
              ? "clean command failed with exit " + result.exitCode
              : compactMessage(result.output);
          return false;
        }
        return true;
      }

      @Override protected void process(List<String> chunks) {
        if (!chunks.isEmpty()) {
          updateStepsFromOutput("Clean Before Update", chunks.get(chunks.size() - 1));
          appendLog(chunks.get(chunks.size() - 1));
        }
      }

      @Override protected void done() {
        setButtonsEnabled(true);
        boolean ok = false;
        try {
          ok = get();
        } catch (Exception e) {
          failure = e.getMessage();
        }
        if (!ok) {
          if (updateEngineButton != null) updateEngineButton.setChecking(false);
          setStatus("Clean failed", ACCENT_ERROR);
          finishSteps(false, failure == null || failure.isBlank() ? "Clean failed." : failure);
          setActivity("Clean failed", failure == null || failure.isBlank() ? "Git could not clean the checkout." : failure,
              false, ACCENT_ERROR);
          updateProgress(
              "Engine update could not start",
              "The checkout cleanup failed, so no update was integrated.",
              "Integration status: not started\nCleanup status: failed\nTechnical result: "
                  + firstNonBlank(failure, "Git could not clean the checkout."),
              false,
              UpdateDialogTone.DANGER);
          return;
        }
        finishSteps(true, "Checkout cleaned.");
        setActivity("Clean complete", "Starting engine update.", true, ACCENT_NEUTRAL);
        startUpdateEngine();
      }
    }.execute();
  }

  private CommandResult cleanBuildOutputChanges(UpdatePreflight preflight) {
    List<String> tracked = preflight.buildOutputChanges().stream()
        .filter(GitStatusEntry::trackedChange)
        .map(GitStatusEntry::path)
        .distinct()
        .toList();
    if (!tracked.isEmpty()) {
      List<String> restore = new ArrayList<>(List.of("git", "restore", "--staged", "--worktree", "--"));
      restore.addAll(tracked);
      CommandResult result = runGit(restore, 60);
      if (result.exitCode != 0) return result;
    }

    List<String> untracked = preflight.buildOutputChanges().stream()
        .filter(GitStatusEntry::untracked)
        .map(GitStatusEntry::path)
        .distinct()
        .toList();
    if (!untracked.isEmpty()) {
      List<String> clean = new ArrayList<>(List.of("git", "clean", "-fd", "--"));
      clean.addAll(untracked);
      CommandResult result = runGit(clean, 60);
      if (result.exitCode != 0) return result;
    }

    return new CommandResult(0, "cleaned build output");
  }

  private CommandResult cleanAllLocalChanges() {
    CommandResult abort = abortInterruptedGitOperations();
    if (abort.exitCode != 0) return abort;
    CommandResult reset = runGit(List.of("git", "reset", "--hard", "HEAD"), 60);
    if (reset.exitCode != 0) return reset;
    CommandResult clean = runGit(List.of("git", "clean", "-fd"), 60);
    if (clean.exitCode != 0) return clean;
    return new CommandResult(0, abort.output + reset.output + clean.output);
  }

  private CommandResult abortInterruptedGitOperations() {
    StringBuilder output = new StringBuilder();
    if (gitPathExists("rebase-merge") || gitPathExists("rebase-apply")) {
      CommandResult result = runGit(List.of("git", "rebase", "--abort"), 30);
      output.append(result.output);
      if (result.exitCode != 0) {
        return new CommandResult(result.exitCode, "git rebase --abort failed:\n" + result.output);
      }
    }
    if (gitPathExists("MERGE_HEAD")) {
      CommandResult result = runGit(List.of("git", "merge", "--abort"), 30);
      output.append(result.output);
      if (result.exitCode != 0) {
        return new CommandResult(result.exitCode, "git merge --abort failed:\n" + result.output);
      }
    }
    if (gitPathExists("CHERRY_PICK_HEAD")) {
      CommandResult result = runGit(List.of("git", "cherry-pick", "--abort"), 30);
      output.append(result.output);
      if (result.exitCode != 0) {
        return new CommandResult(result.exitCode, "git cherry-pick --abort failed:\n" + result.output);
      }
    }
    if (gitPathExists("REVERT_HEAD")) {
      CommandResult result = runGit(List.of("git", "revert", "--abort"), 30);
      output.append(result.output);
      if (result.exitCode != 0) {
        return new CommandResult(result.exitCode, "git revert --abort failed:\n" + result.output);
      }
    }
    return new CommandResult(0, output.toString());
  }

  private void installShortcuts() {
    if (runningProcess.get() != null) {
      appendLog("[hub] a task is already running; wait for it to finish or cancel it.");
      return;
    }
    startSteps("Build Shortcuts");
    setActivity("Preparing shortcuts", "Detecting operating system.", true, ACCENT_NEUTRAL);
    String os = System.getProperty("os.name", "").toLowerCase();
    List<String> cmd = new ArrayList<>();
    Path script;
    String label = "Build Shortcuts";

    if (os.contains("win")) {
      script = projectRoot.resolve("install-windows-launcher.ps1");
      cmd.add(windowsPowerShellCommand());
      cmd.add("-NoProfile");
      cmd.add("-ExecutionPolicy");
      cmd.add("Bypass");
      cmd.add("-File");
      cmd.add(script.toAbsolutePath().toString());
    } else if (os.contains("mac") || os.contains("darwin")) {
      script = projectRoot.resolve("install-macos-launcher.sh");
      cmd.add("bash");
      cmd.add(script.toAbsolutePath().toString());
    } else {
      script = projectRoot.resolve("install-linux-launcher.sh");
      cmd.add("bash");
      cmd.add(script.toAbsolutePath().toString());
    }
    completeCurrentStep("Operating system detected.");
    advanceStep("Looking for shortcut installer.");

    if (!Files.isRegularFile(script)) {
      appendLog("[hub] shortcut installer not found: " + script.toAbsolutePath());
      setStatus("Failed: " + label, ACCENT_ERROR);
      finishSteps(false, "Shortcut installer not found.");
      setActivity("Failed: " + label, "Shortcut installer not found.", false, ACCENT_ERROR);
      return;
    }

    if (!acquire(label)) return;
    completeCurrentStep("Installer command assembled.");
    advanceStep("Starting shortcut installer.");
    appendLog("$ " + quoteCommandForLog(cmd));
    startProcess(cmd, label);
  }

  private boolean acquire(String label) {
    if (runningProcess.get() != null) {
      appendLog("[hub] a task is already running; wait for it to finish or cancel it.");
      return false;
    }
    setButtonsEnabled(false);
    setStatus("Running: " + label + activeModeSuffix(), activeModeColor());
    startSteps(label);
    startAutoStepTicker(label);
    setActivity("Working on " + label, activeModeActivityDetail(), true, activeModeColor());
    return true;
  }

  private String activeModeSuffix() {
    if (developerModeEnabled && safeModeEnabled) return " (Developer + Safe Mode)";
    if (developerModeEnabled) return " (Developer Mode)";
    if (safeModeEnabled) return " (Safe Mode)";
    return "";
  }

  private Color activeModeColor() {
    if (safeModeEnabled) return ACCENT_SAFE;
    if (developerModeEnabled) return ACCENT_DEV;
    return ACCENT_NEUTRAL;
  }

  private String activeModeActivityDetail() {
    if (developerModeEnabled && safeModeEnabled) {
      return "Developer diagnostics are enabled while launch state is isolated.";
    }
    if (developerModeEnabled) return "Developer Mode is exposing engineering-focused launch behavior.";
    if (safeModeEnabled) return "Safe Mode flags are enabled for this process.";
    return "This can take a moment.";
  }

  private void release(String label, int exitCode) {
    runningProcess.set(null);
    setButtonsEnabled(true);
    refreshModeMenus();
    Color tone = exitCode == 0 ? ACCENT_GREEN : ACCENT_ERROR;
    String prefix = exitCode == 0 ? "Done" : "Failed (exit " + exitCode + ")";
    setStatus(prefix + ": " + label, tone);
    finishSteps(exitCode == 0,
        exitCode == 0 ? "All stages completed." : "Stopped before every stage completed.");
    setActivity(prefix + ": " + label,
        exitCode == 0 ? "Ready for the next action." : "Check the terminal or generated launcher log for details.",
        false,
        tone);
  }

  private void startProcess(List<String> command, String label) {
    startProcess(command, label, Map.of());
  }

  private void startProcess(
      List<String> command,
      String label,
      Map<String, String> launchEnvironment) {
    RenderPipelineSettings.Mode launchPipelineMode = renderPipelineMode;
    RenderPipelineSettings.Options launchPipelineOptions = renderPipelineOptions;
    Map<String, String> extraEnvironment = launchEnvironment == null
        ? Map.of()
        : Map.copyOf(launchEnvironment);
    new SwingWorker<Integer, String>() {
      private String lastOutput = "";
      private final StringBuilder fullOutput = new StringBuilder();
      private final List<String> recentOutput = new ArrayList<>();

      @Override protected Integer doInBackground() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command)
            .directory(projectRoot.toFile())
            .redirectErrorStream(true);
        pb.environment().putAll(extraEnvironment);
        boolean managedGraphicsLaunch = RenderPipelineSettings.applyLaunchEnvironment(
            pb.environment(), command, launchPipelineMode, launchPipelineOptions);
        if (managedGraphicsLaunch) {
          renderGraphCapture.beginSession(label, launchPipelineOptions.printRenderGraph());
          publish("[hub] render pipeline: " + launchPipelineMode.displayName()
              + " (" + launchPipelineMode.backendOrder(System.getProperty("os.name", "")) + ").");
        }
        if (isGradleWrapperCommand(command)) {
          Path packagedGradleHome = projectRoot.resolve(".jvn-gradle-user-home");
          if (isPackagedGradleHome(packagedGradleHome)) {
            pb.environment().put("GRADLE_USER_HOME", packagedGradleHome.toAbsolutePath().toString());
            publish("[hub] using packaged Gradle user home.");
          } else if (Files.isDirectory(packagedGradleHome)) {
            publish("[hub] using default Gradle user home; packaged cache marker was not found.");
          }
        }
        Process process;
        try {
          publish("[hub] preparing process environment...");
          process = pb.start();
        } catch (IOException e) {
          if (managedGraphicsLaunch) renderGraphCapture.endSession();
          publish("[hub] failed to start process: " + e.getMessage());
          return -1;
        }
        runningProcess.set(process);
        SwingUtilities.invokeLater(JvnHub.this::refreshModeMenus);
        publish("[hub] process started; reading live output...");
        try {
          try (BufferedReader reader = new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
              boolean renderGraphLine = managedGraphicsLaunch && renderGraphCapture.accept(line);
              if (!line.isBlank() && !renderGraphLine) {
                if (fullOutput.length() < PROCESS_OUTPUT_PREFIX_LIMIT) fullOutput.append(line).append('\n');
                rememberProcessOutput(recentOutput, line);
                lastOutput = compactMessage(line);
                publish(line);
              }
            }
          } catch (IOException e) {
            lastOutput = "Stream error: " + e.getMessage();
            publish("[hub] " + lastOutput);
          }
          return process.waitFor();
        } finally {
          if (managedGraphicsLaunch) renderGraphCapture.endSession();
        }
      }

      @Override protected void process(List<String> chunks) {
        if (!chunks.isEmpty()) {
          String line = chunks.get(chunks.size() - 1);
          updateStepsFromOutput(label, line);
          appendLog(line);
          if ("Update Engine".equals(label)) appendUpdateProgressOutput(line);
        }
      }

      @Override protected void done() {
        int exit;
        try {
          exit = get();
        } catch (Exception e) {
          exit = -1;
          lastOutput = "Task raised: " + e.getMessage();
        }
        boolean engineUpdate = "Update Engine".equals(label);
        if (engineUpdate) {
          runningProcess.set(null);
          refreshModeMenus();
          stopAutoStepTicker();
        } else {
          release(label, exit);
        }
        if (exit == 0) {
          if (!lastOutput.isBlank()) {
            setActivityDetail(lastOutput);
          }
        } else {
          String failureSummary = summarizeProcessFailure(fullOutput.toString(), recentOutput, lastOutput);
          if (!failureSummary.isBlank()) {
            appendLog("[hub] failure summary: " + failureSummary);
            setActivityDetail(failureSummary);
          } else if (!lastOutput.isBlank()) {
            setActivityDetail(lastOutput);
          }
        }
        // Update Engine touched the working tree — re-read on-disk state so
        // version and maintenance state reflect the new HEAD.
        if (engineUpdate && exit == 0) {
          verifyUpdateIntegration(fullOutput.toString().isBlank() ? lastOutput : fullOutput.toString());
        } else if (engineUpdate) {
          setButtonsEnabled(true);
          finishSteps(false, "Git could not integrate the engine update.");
          setStatus("Failed (exit " + exit + "): Update Engine", ACCENT_ERROR);
          if (updateEngineButton != null) updateEngineButton.setChecking(false);
          String output = fullOutput.toString().isBlank() ? lastOutput : fullOutput.toString();
          updateProgress(
              "Engine update failed",
              "Git did not integrate the update. The Hub will not relaunch.",
              "Integration status: failed\nGit exit code: " + exit + "\n\nGit output:\n"
                  + compactForDialog(output),
              false,
              UpdateDialogTone.DANGER);
          handleUpdateEngineFailure(exit, output);
        }
      }
    }.execute();
  }

  private void verifyUpdateIntegration(String gitOutput) {
    setButtonsEnabled(false);
    setStatus("Verifying engine integration", ACCENT_NEUTRAL);
    setActivity(
        "Verifying engine integration",
        "Checking the updated revision, upstream state, and Git operation state.",
        true,
        ACCENT_NEUTRAL);
    updateProgress(
        "Verifying the engine update",
        "Git completed. The Hub is checking whether the update was properly integrated.",
        "Git command: completed successfully\nIntegration verification: running",
        true,
        UpdateDialogTone.QUESTION);

    UpdateAttempt attempt = Optional.ofNullable(activeUpdateAttempt.get())
        .orElseGet(() -> new UpdateAttempt("unknown", "unknown", readDiskVersion(), System.nanoTime()));
    new SwingWorker<UpdateIntegrationResult, Void>() {
      @Override protected UpdateIntegrationResult doInBackground() {
        CommandResult revisionResult = runGit(List.of("git", "rev-parse", "--short", "HEAD"), 8);
        String afterRevision = revisionResult.exitCode() == 0
            ? firstNonBlank(revisionResult.output(), "unknown")
            : "unknown";
        CommandResult branchResult = runGit(List.of("git", "rev-parse", "--abbrev-ref", "HEAD"), 8);
        String branch = branchResult.exitCode() == 0
            ? firstNonBlank(branchResult.output(), attempt.branch())
            : attempt.branch();
        CommandResult behindResult = runGit(
            List.of("git", "rev-list", "--count", "HEAD.." + ENGINE_UPDATE_REMOTE_REF), 8);
        int behind = parseNonNegativeInt(behindResult.output(), -1);
        UpdatePreflight postflight = inspectUpdatePreflight();
        String afterVersion = readDiskVersion();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - attempt.startedNanos());
        boolean integrated = updateProperlyIntegrated(
            revisionResult.exitCode(), behindResult.exitCode(), behind,
            postflight.statusUnavailable(), postflight.hasInterruptedGitOperation(), afterRevision);
        return new UpdateIntegrationResult(
            integrated,
            attempt.beforeRevision(),
            afterRevision,
            branch,
            attempt.beforeVersion(),
            afterVersion,
            behind,
            postflight,
            elapsedMillis,
            gitOutput);
      }

      @Override protected void done() {
        UpdateIntegrationResult result;
        try {
          result = get();
        } catch (Exception ex) {
          result = UpdateIntegrationResult.failed(attempt, gitOutput, exceptionMessage(ex));
        }
        refreshFromDisk();
        if (updateEngineButton != null) updateEngineButton.setChecking(false);
        String details = formatUpdateIntegrationDetails(result);
        if (!result.integrated()) {
          setButtonsEnabled(true);
          finishSteps(false, "Post-update integration checks did not pass.");
          setStatus("Update integration unverified", ACCENT_ERROR);
          setActivity(
              "Update integration unverified",
              "The Git command completed, but the updated checkout did not pass every safety check.",
              false,
              ACCENT_ERROR);
          updateProgress(
              "Engine update needs attention",
              "The Hub could not verify that the update was properly integrated, so it will not relaunch.",
              details,
              false,
              UpdateDialogTone.DANGER);
          checkIncomingUpdates(false);
          return;
        }

        finishSteps(true, "Update integrated and verified.");
        setStatus("Update integrated", ACCENT_GREEN);
        setActivity(
            "Engine update integrated",
            "All post-update checks passed. Relaunching the Hub with the updated code.",
            false,
            ACCENT_GREEN);
        updateProgress(
            "Engine update integrated",
            "The update passed every integration check. The Hub will now relaunch automatically.",
            details + "\n\nRelaunch status: preparing updated Hub",
            false,
            UpdateDialogTone.SUCCESS);
        javax.swing.Timer relaunchDelay = new javax.swing.Timer(1400, event -> {
          ((javax.swing.Timer) event.getSource()).stop();
          relaunchUpdatedHub(details);
        });
        relaunchDelay.setRepeats(false);
        relaunchDelay.start();
      }
    }.execute();
  }

  private void relaunchUpdatedHub(String integrationDetails) {
    List<String> command = hubRelaunchCommand(projectRoot, System.getProperty("os.name", ""));
    Path logFile = Paths.get(System.getProperty("user.home", "."), ".jvn", "logs", "hub-relaunch.log");
    Path readyFile = Paths.get(
        System.getProperty("user.home", "."),
        ".jvn",
        "run",
        "hub-relaunch-ready-" + ProcessHandle.current().pid() + "-" + System.nanoTime());
    updateProgress(
        "Relaunching JVN Engine Hub",
        "Building and starting the updated Hub. This window stays open until the replacement is ready.",
        integrationDetails + "\n\nRelaunch status: waiting for startup confirmation"
            + "\nCommand: " + quoteCommandForLog(command)
            + "\nRelaunch log: " + logFile,
        true,
        UpdateDialogTone.SUCCESS);

    new SwingWorker<HubRelaunchResult, Void>() {
      @Override protected HubRelaunchResult doInBackground() {
        try {
          Files.createDirectories(logFile.getParent());
          Files.createDirectories(readyFile.getParent());
          Files.deleteIfExists(readyFile);
          ProcessBuilder builder = new ProcessBuilder(command)
              .directory(projectRoot.toFile())
              .redirectErrorStream(true)
              .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
          builder.environment().put("JVN_HUB_RELAUNCH_READY_FILE", readyFile.toString());
          Process replacement = builder.start();
          appendLog("[hub] replacement Hub command started: " + quoteCommandForLog(command));
          boolean detachedWindowsLauncher = System.getProperty("os.name", "")
              .toLowerCase(Locale.ROOT).contains("win");
          long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
          while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(readyFile)) {
              Files.deleteIfExists(readyFile);
              return new HubRelaunchResult(true, "Replacement Hub confirmed its window is visible.");
            }
            if (!detachedWindowsLauncher && !replacement.isAlive()) {
              return new HubRelaunchResult(
                  false,
                  "Replacement process exited before startup confirmation (exit "
                      + replacement.exitValue() + ").");
            }
            Thread.sleep(100);
          }
          return new HubRelaunchResult(false, "Timed out waiting 45 seconds for startup confirmation.");
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          return new HubRelaunchResult(false, "Interrupted while waiting for the replacement Hub.");
        } catch (IOException ex) {
          return new HubRelaunchResult(false, exceptionMessage(ex));
        }
      }

      @Override protected void done() {
        HubRelaunchResult result;
        try {
          result = get();
        } catch (Exception ex) {
          result = new HubRelaunchResult(false, exceptionMessage(ex));
        }
        if (!result.ready()) {
          setButtonsEnabled(true);
          setStatus("Hub relaunch failed", ACCENT_ERROR);
          setActivity(
              "Hub relaunch failed",
              "The engine update is integrated, but the replacement Hub did not confirm startup.",
              false,
              ACCENT_ERROR);
          updateProgress(
              "Engine updated; relaunch failed",
              "The update was properly integrated, but the Hub could not confirm an automatic relaunch.",
              integrationDetails + "\n\nRelaunch status: failed\nTechnical result: " + result.details()
                  + "\nCommand: " + quoteCommandForLog(command)
                  + "\nRelaunch log: " + logFile,
              false,
              UpdateDialogTone.WARNING);
          return;
        }

        updateProgress(
            "Relaunching JVN Engine Hub",
            "The updated Hub confirmed it is ready. This window is closing.",
            integrationDetails + "\n\nRelaunch status: confirmed\n" + result.details()
                + "\nRelaunch log: " + logFile,
            false,
            UpdateDialogTone.SUCCESS);
        shutdownInProgress = true;
        if (spinnerTimer.isRunning()) spinnerTimer.stop();
        if (autoStepTimer.isRunning()) autoStepTimer.stop();
        javax.swing.Timer closeDelay = new javax.swing.Timer(350, event -> {
          ((javax.swing.Timer) event.getSource()).stop();
          frame.setVisible(false);
          frame.dispose();
          System.exit(0);
        });
        closeDelay.setRepeats(false);
        closeDelay.start();
      }
    }.execute();
  }

  private static void signalRelaunchReady() {
    String marker = System.getenv("JVN_HUB_RELAUNCH_READY_FILE");
    if (marker == null || marker.isBlank()) return;
    try {
      Path file = Paths.get(marker).toAbsolutePath().normalize();
      Path parent = file.getParent();
      if (parent != null) Files.createDirectories(parent);
      Files.writeString(file, "ready\n", StandardCharsets.UTF_8);
    } catch (IOException ignored) {
      // The previous Hub will keep running and report a confirmation timeout.
    }
  }

  static List<String> hubRelaunchCommand(Path projectRoot, String osName) {
    Path root = projectRoot.toAbsolutePath().normalize();
    String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      return List.of("cmd.exe", "/c", "start", "", root.resolve("jvn.bat").toString());
    }
    return List.of("bash", root.resolve("jvn").toString(), "--rebuild-launcher");
  }

  static boolean updateProperlyIntegrated(
      int revisionExit,
      int behindExit,
      int behindCount,
      boolean statusUnavailable,
      boolean interruptedOperation,
      String afterRevision
  ) {
    return revisionExit == 0
        && behindExit == 0
        && behindCount == 0
        && !statusUnavailable
        && !interruptedOperation
        && afterRevision != null
        && !afterRevision.isBlank()
        && !"unknown".equalsIgnoreCase(afterRevision.trim());
  }

  private static int parseNonNegativeInt(String value, int fallback) {
    try {
      return Math.max(0, Integer.parseInt(value == null ? "" : value.strip()));
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static String formatUpdateIntegrationDetails(UpdateIntegrationResult result) {
    UpdatePreflight postflight = result.postflight();
    String revisionStatus = result.beforeRevision().equals(result.afterRevision())
        ? "already current"
        : result.beforeRevision() + " -> " + result.afterRevision();
    String versionStatus = result.beforeVersion().equals(result.afterVersion())
        ? result.afterVersion() + " (unchanged)"
        : result.beforeVersion() + " -> " + result.afterVersion();
    String worktreeStatus = postflight.statusUnavailable()
        ? "unavailable: " + postflight.statusError()
        : postflight.hasChanges()
            ? postflight.allChanges().size() + " local change(s) present/preserved"
            : "clean";
    String integration = result.integrated() ? "verified" : "not verified";
    String behind = result.behindCount() < 0 ? "unavailable" : Integer.toString(result.behindCount());
    return "Integration status: " + integration
        + "\nBranch: " + result.branch()
        + "\nRevision: " + revisionStatus
        + "\nEngine version: " + versionStatus
        + "\nCommits pending from " + ENGINE_UPDATE_REMOTE_REF + ": " + behind
        + "\nInterrupted Git operation: " + (postflight.hasInterruptedGitOperation() ? "detected" : "none")
        + "\nWorktree after integration: " + worktreeStatus
        + "\nElapsed: " + Math.max(0, result.elapsedMillis()) + " ms"
        + "\n\nGit output:\n" + compactForDialog(result.gitOutput());
  }

  private void handleUpdateEngineFailure(int exitCode, String output) {
    if (safeModeEnabled) {
      recoverSafeModeUpdateFailure(output);
      return;
    }

    setActivity(
        "Update Engine failed",
        friendlyUpdateFailureSummary(output, UpdatePreflight.empty())
            + " Enable Safe Mode to recover the checkout automatically.",
        false,
        ACCENT_ERROR);

    new SwingWorker<UpdatePreflight, Void>() {
      @Override protected UpdatePreflight doInBackground() {
        return inspectUpdatePreflight();
      }

      @Override protected void done() {
        UpdatePreflight preflight = UpdatePreflight.empty();
        try {
          preflight = get();
        } catch (Exception ignored) {
          // reason: best-effort post-failure diagnosis; the raw failure message is still shown
        }
        if (preflight != null && preflight.hasInterruptedGitOperation()) {
          int choice = showUpdateDialog(
              "Update Engine Needs Recovery",
              "Git was left in an unfinished update state.",
              "Safe Mode can abort the unfinished Git operation, re-check the checkout, and let the user retry without staying stuck in a failure loop.\n\n"
                  + friendlyUpdateFailureSummary(output, preflight)
                  + "\n\n"
                  + preflight.summary(),
              new String[]{"Recover in Safe Mode", "Close"},
              UpdateDialogTone.WARNING);
          if (choice == 0) {
            setSafeModeEnabled(true);
            recoverSafeModeUpdateFailure(output);
          }
        } else {
          showUpdateFailureDialog("Update Engine Failed", output, preflight);
        }
      }
    }.execute();
  }

  private void recoverSafeModeUpdateFailure(String output) {
    if (runningProcess.get() != null) {
      appendLog("[hub] Safe Mode recovery is waiting for the running process to finish.");
      return;
    }
    setStatus("Safe Mode recovering update", ACCENT_SAFE);
    updateProgress(
        "Recovering the engine checkout",
        "Safe Mode is removing interrupted Git state before another update attempt.",
        "Integration status: not integrated\nRecovery status: running\n\nSource details:\n"
            + compactForDialog(output),
        true,
        UpdateDialogTone.WARNING);
    startSteps("Safe Mode Update Recovery");
    setActivity(
        "Recovering failed update",
        "Safe Mode is aborting unfinished Git operations and checking whether the checkout is stable.",
        true,
        ACCENT_SAFE);
    advanceStep("Aborting interrupted Git operation.");

    new SwingWorker<UpdateRecoveryResult, String>() {
      @Override protected UpdateRecoveryResult doInBackground() {
        CommandResult abort = abortInterruptedGitOperations();
        publish(abort.exitCode == 0
            ? "[hub] Safe Mode recovery: interrupted Git operations aborted."
            : "[hub] Safe Mode recovery: abort failed.");
        if (abort.exitCode != 0) {
          return new UpdateRecoveryResult(false, abort, UpdatePreflight.empty(), output);
        }
        UpdatePreflight preflight = inspectUpdatePreflight();
        return new UpdateRecoveryResult(true, abort, preflight, output);
      }

      @Override protected void process(List<String> chunks) {
        if (!chunks.isEmpty()) {
          appendLog(chunks.get(chunks.size() - 1));
        }
      }

      @Override protected void done() {
        UpdateRecoveryResult result;
        try {
          result = get();
        } catch (Exception ex) {
          result = new UpdateRecoveryResult(
              false,
              new CommandResult(-1, exceptionMessage(ex)),
              UpdatePreflight.empty(),
              output);
        }

        if (result.ok()) {
          advanceToStep(2, "Checkout state re-checked.");
          finishSteps(true, "Safe Mode returned Git to a stable checkout.");
          setStatus("Safe Mode recovered update", ACCENT_SAFE);
          setActivity(
              "Safe Mode recovered update",
              recoverySummary(result),
              false,
              ACCENT_SAFE);
          updateProgress(
              "Engine checkout recovered",
              "Git is stable again. The previous update was not integrated; you can now retry it.",
              "Integration status: not integrated\nRecovery status: complete\n"
                  + recoverySummary(result),
              false,
              UpdateDialogTone.SUCCESS);
          showSafeModeRecoveryDialog(result);
        } else {
          finishSteps(false, "Safe Mode could not recover the checkout automatically.");
          setStatus("Safe Mode recovery failed", ACCENT_ERROR);
          setActivity(
              "Safe Mode recovery failed",
              compactMessage(result.abortResult() != null ? result.abortResult().output : ""),
              false,
              ACCENT_ERROR);
          updateProgress(
              "Engine checkout recovery failed",
              "The update was not integrated and Git still needs manual attention.",
              "Integration status: not integrated\nRecovery status: failed\n"
                  + recoverySummary(result),
              false,
              UpdateDialogTone.DANGER);
          showManualRecoveryDialog(result);
        }
      }
    }.execute();
  }

  private void showSafeModeRecoveryDialog(UpdateRecoveryResult result) {
    UpdatePreflight preflight = result != null ? result.preflight() : UpdatePreflight.empty();
    String details = recoverySummary(result);
    if (preflight != null && preflight.statusUnavailable()) {
      details += "\n\nGit status check:\n" + preflight.statusError();
    } else if (preflight != null && (preflight.hasChanges() || preflight.hasInterruptedGitOperation())) {
      details += "\n\nCurrent checkout state:\n" + preflight.summary();
    }
    details += "\n\nOriginal failure:\n" + compactForDialog(result != null ? result.sourceOutput() : "");

    int choice = showUpdateDialog(
        "Safe Mode Recovery Complete",
        "The checkout is no longer stuck in an interrupted Git operation.",
        details,
        new String[]{"Retry Update", "Close"},
        UpdateDialogTone.QUESTION);
    if (choice == 0) {
      updateEngine();
    }
  }

  private void showUpdateFailureDialog(String title, String output, UpdatePreflight preflight) {
    String details = friendlyUpdateFailureSummary(output, preflight)
        + "\n\n"
        + (preflight != null && (preflight.hasChanges() || preflight.hasInterruptedGitOperation())
            ? "Current checkout state:\n" + preflight.summary() + "\n\n"
            : "")
        + "Git output:\n"
        + compactForDialog(output);
    showUpdateDialog(
        title,
        "Update Engine could not complete.",
        details,
        new String[]{"Close"},
        UpdateDialogTone.WARNING);
  }

  private void showManualRecoveryDialog(UpdateRecoveryResult result) {
    String output = result != null && result.abortResult() != null ? result.abortResult().output : "";
    String details = "Safe Mode could not abort the interrupted Git operation automatically.\n\n"
        + "Open a terminal in the engine folder and run:\n\n"
        + "git status\n"
        + "git rebase --abort\n"
        + "git merge --abort\n"
        + "git cherry-pick --abort\n"
        + "git revert --abort\n\n"
        + "Then retry Update Engine. If those abort commands report that no operation is in progress, send the git status output.\n\n"
        + "Git output:\n"
        + compactForDialog(output);
    showUpdateDialog(
        "Manual Git Recovery Needed",
        "The Hub could not safely recover the engine checkout.",
        details,
        new String[]{"Close"},
        UpdateDialogTone.DANGER);
  }

  private String recoverySummary(UpdateRecoveryResult result) {
    UpdatePreflight preflight = result != null ? result.preflight() : UpdatePreflight.empty();
    if (preflight.isEmpty()) {
      return "Safe Mode aborted any unfinished Git operation. Retry Update Engine when ready.";
    }
    if (preflight.statusUnavailable()) {
      return "Safe Mode aborted the unfinished Git operation, but Git status could not be checked.";
    }
    if (preflight.hasInterruptedGitOperation()) {
      return "Git still reports an interrupted operation after recovery.";
    }
    if (preflight.hasChanges()) {
      return "Git is no longer mid-update. Local changes are still present; Safe Mode will use autostash when retrying.";
    }
    return "Git is no longer mid-update and the checkout is clean. Retry Update Engine when ready.";
  }

  private String friendlyUpdateFailureSummary(String output, UpdatePreflight preflight) {
    if (preflight != null && preflight.hasInterruptedGitOperation()) {
      return "Git stopped during an update and left an unfinished operation in the checkout.";
    }
    String text = output == null ? "" : output.toLowerCase(Locale.ROOT);
    if (text.contains("conflict") || text.contains("resolve all conflicts")) {
      return "Git hit a content conflict while applying the engine update.";
    }
    if (text.contains("would be overwritten")) {
      return "Local files would be overwritten by the engine update.";
    }
    if (text.contains("no tracking information") || text.contains("no upstream")) {
      return "This checkout does not have an upstream branch configured.";
    }
    if (text.contains("could not resolve host") || text.contains("failed to connect")
        || text.contains("network is unreachable") || text.contains("timed out")) {
      return "Git could not reach the remote repository. This is likely a network problem.";
    }
    if (text.contains("permission denied") || text.contains("authentication failed")
        || text.contains("could not read from remote repository")) {
      return "Git could not authenticate with the remote repository.";
    }
    if (text.contains("index.lock") || text.contains("another git process")) {
      return "Another Git process or a stale Git lock is blocking the update.";
    }
    if (text.contains("not a git repository")) {
      return "The Hub is not running from a valid Git checkout.";
    }
    return "Git returned a failure while updating the engine checkout.";
  }

  private static String compactForDialog(String text) {
    if (text == null || text.isBlank()) return "(no Git output captured)";
    String trimmed = text.strip();
    int limit = 1800;
    if (trimmed.length() <= limit) return trimmed;
    return trimmed.substring(0, limit) + "\n... output truncated ...";
  }

  private static String exceptionMessage(Throwable ex) {
    if (ex == null) return "Unknown failure";
    String message = ex.getMessage();
    return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
  }

  private static boolean isGradleWrapperCommand(List<String> command) {
    if (command.isEmpty()) return false;
    Path executable = Path.of(command.get(0)).getFileName();
    if (executable == null) return false;
    String name = executable.toString();
    return name.equals("gradlew") || name.equals("gradlew.bat");
  }

  private static boolean isPackagedGradleHome(Path gradleHome) {
    return Files.isDirectory(gradleHome)
        && Files.isRegularFile(gradleHome.resolve(PACKAGED_GRADLE_CACHE_MARKER_FILE));
  }

  private void cancelRunning() {
    Process p = runningProcess.get();
    if (p == null) {
      appendLog("[hub] no task is running.");
      return;
    }
    appendLog("[hub] cancelling current task...");
    setActivity("Cancelling task", "Stopping the running process.", true, ACCENT_ERROR);
    p.descendants().forEach(ProcessHandle::destroy);
    p.destroy();
  }

  private void confirmAndExit() {
    if (shutdownInProgress) return;
    shutdownInProgress = true;
    HubLifecycleSplash splash = HubLifecycleSplash.shutdown(
        "Verifying tasks and engine state...");
    splash.showCentered();
    Process p = runningProcess.get();
    if (p != null) {
      appendLog("[hub] cancelling running task before exit...");
      setActivity("Cancelling task", "Closing the hub after the process stops.", true, ACCENT_ERROR);
      p.descendants().forEach(ProcessHandle::destroy);
      p.destroy();
    }
    if (spinnerTimer.isRunning()) spinnerTimer.stop();
    frame.setVisible(false);
    new Thread(() -> {
      if (p != null) {
        try {
          p.waitFor(650, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
        }
      }
      SwingUtilities.invokeLater(() -> splash.completeShutdown(
          () -> {
            frame.dispose();
            System.exit(0);
          }));
    }, "jvn-hub-shutdown").start();
  }

  // --- Helpers ---------------------------------------------------------------

  private void setButtonsEnabled(boolean enabled) {
    SwingUtilities.invokeLater(() -> actionButtons.forEach(b -> b.setEnabled(enabled)));
  }

  private void setStatus(String text, Color color) {
    SwingUtilities.invokeLater(() -> {
      statusLabel.setText(text);
      statusLabel.setToolTipText(text);
      statusLabel.setForeground(color != null ? color : TEXT_SOFT);
    });
  }

  private void appendLog(String line) {
    if (line == null) return;
    setActivityDetail(compactMessage(line));
  }

  private void setActivity(String title, String detail, boolean spinning, Color tone) {
    SwingUtilities.invokeLater(() -> {
      activityTitle.setText(compactMessage(title));
      activityTitle.setForeground(tone != null ? tone : TEXT_PRIMARY);
      activityDetail.setText(compactMessage(detail));
      activitySpinner.setActive(spinning);
      activityPanel.setTone(tone != null ? tone : ACCENT_NEUTRAL, spinning || runningProcess.get() != null);
      if (spinning && !spinnerTimer.isRunning()) {
        spinnerTimer.start();
      } else if (!spinning && spinnerTimer.isRunning()) {
        spinnerTimer.stop();
      }
    });
  }

  private void setActivityDetail(String detail) {
    SwingUtilities.invokeLater(() -> activityDetail.setText(compactMessage(detail)));
  }

  private void startSteps(String label) {
    List<String> steps = stepsForAction(label);
    SwingUtilities.invokeLater(() -> {
      activeStepLabel = label == null ? "" : label.trim();
      activeStepIndex = steps.isEmpty() ? -1 : 0;
      activitySteps.setSteps(steps);
      if (activeStepIndex >= 0) activitySteps.setStatus(activeStepIndex, StepStatus.RUNNING);
      updateActivityProgressFill();
    });
  }

  private void completeCurrentStep(String detail) {
    SwingUtilities.invokeLater(() -> {
      if (activeStepIndex >= 0) activitySteps.setStatus(activeStepIndex, StepStatus.DONE);
      if (detail != null && !detail.isBlank()) activityDetail.setText(compactMessage(detail));
      updateActivityProgressFill();
    });
  }

  private void advanceStep(String detail) {
    SwingUtilities.invokeLater(() -> {
      if (activeStepIndex >= 0) activitySteps.setStatus(activeStepIndex, StepStatus.DONE);
      int next = Math.max(0, activeStepIndex + 1);
      if (next < activitySteps.stepCount()) {
        activeStepIndex = next;
        activitySteps.setStatus(activeStepIndex, StepStatus.RUNNING);
      }
      if (detail != null && !detail.isBlank()) activityDetail.setText(compactMessage(detail));
      updateActivityProgressFill();
    });
  }

  private void advanceToStep(int targetIndex, String detail) {
    SwingUtilities.invokeLater(() -> {
      if (activitySteps.stepCount() == 0) return;
      int target = Math.max(0, Math.min(targetIndex, activitySteps.stepCount() - 1));
      if (activeStepIndex < 0) activeStepIndex = 0;
      if (target > activeStepIndex) {
        for (int i = activeStepIndex; i < target; i++) {
          activitySteps.setStatus(i, StepStatus.DONE);
        }
        activeStepIndex = target;
        activitySteps.setStatus(activeStepIndex, StepStatus.RUNNING);
      }
      if (detail != null && !detail.isBlank()) activityDetail.setText(compactMessage(detail));
      updateActivityProgressFill();
    });
  }

  private void finishSteps(boolean success, String detail) {
    SwingUtilities.invokeLater(() -> {
      stopAutoStepTicker();
      int count = activitySteps.stepCount();
      if (count > 0) {
        for (int i = 0; i < count; i++) {
          StepStatus status = success ? StepStatus.DONE : (i == activeStepIndex ? StepStatus.FAILED : activitySteps.statusAt(i));
          if (!success && i > activeStepIndex && activitySteps.statusAt(i) == StepStatus.PENDING) {
            status = StepStatus.PENDING;
          }
          activitySteps.setStatus(i, status);
        }
      }
      activeStepIndex = -1;
      activeStepLabel = "";
      if (detail != null && !detail.isBlank()) activityDetail.setText(compactMessage(detail));
      activityPanel.setProgress(success ? 1.0 : Math.max(activityPanel.progress(), activitySteps.progressFraction()));
      activityPanel.setTone(success ? ACCENT_GREEN : ACCENT_ERROR, false);
    });
  }

  private void updateActivityProgressFill() {
    activityPanel.setProgress(activitySteps.progressFraction());
  }

  private List<String> stepsForAction(String label) {
    String key = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
    return switch (key) {
      case "run editor" -> List.of(
          "Read launch request",
          "Resolve engine workspace",
          "Locate Gradle wrapper",
          "Apply safe-mode launch flags",
          "Start Gradle process",
          "Attach live output stream",
          "Configure project modules",
          "Check Java toolchain",
          "Resolve project dependencies",
          "Compile shared engine classes",
          "Build editor classpath",
          "Process editor resources",
          "Assemble runtime classpath",
          "Start JavaFX toolkit",
          "Load editor preferences",
          "Initialize workspace services",
          "Create editor window",
          "Wait for editor handoff",
          "Monitor editor process");
      case "run launcher" -> List.of(
          "Read launch request",
          "Resolve engine workspace",
          "Locate Gradle wrapper",
          "Apply safe-mode launch flags",
          "Start Gradle process",
          "Attach live output stream",
          "Configure project modules",
          "Check Java toolchain",
          "Resolve project dependencies",
          "Compile shared engine classes",
          "Build launcher classpath",
          "Process launcher resources",
          "Assemble runtime classpath",
          "Start JavaFX toolkit",
          "Load launcher preferences",
          "Scan project workspace",
          "Create launcher window",
          "Wait for launcher handoff",
          "Monitor launcher process");
      case "build all" -> List.of(
          "Read build request",
          "Resolve engine workspace",
          "Locate Gradle wrapper",
          "Start Gradle process",
          "Configure project modules",
          "Compile source sets",
          "Assemble module outputs",
          "Finalize build result");
      case "run tests" -> List.of(
          "Read test request",
          "Resolve engine workspace",
          "Locate Gradle wrapper",
          "Start Gradle process",
          "Configure project modules",
          "Compile test classes",
          "Run test suites",
          "Collect test reports");
      case "build shortcuts" -> List.of(
          "Detect operating system",
          "Find shortcut installer",
          "Prepare installer command",
          "Start installer process",
          "Create application entries",
          "Verify shortcut install");
      case "update engine" -> List.of(
          "Inspect local Git state",
          "Prepare update command",
          "Fetch upstream changes",
          "Rebase local checkout",
          "Refresh version metadata",
          "Reload engine state");
      case "clean before update" -> List.of(
          "Inspect local files",
          "Select safe clean command",
          "Restore tracked build output",
          "Remove generated files",
          "Confirm clean checkout");
      case "safe mode update recovery" -> List.of(
          "Inspect failed update",
          "Abort interrupted Git operation",
          "Re-check checkout state",
          "Present recovery options");
      default -> List.of(
          "Read action request",
          "Resolve engine workspace",
          "Prepare command",
          "Start background process",
          "Read process output",
          "Finalize result");
    };
  }

  private void updateStepsFromOutput(String label, String rawLine) {
    if (rawLine == null || rawLine.isBlank()) return;
    String line = rawLine.trim();
    String lower = line.toLowerCase(Locale.ROOT);
    String key = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);

    if (lower.contains("preparing process environment")) {
      advanceToStep(stepIndexFor(key, "prepare_environment"), "Preparing command environment.");
      return;
    }
    if (lower.contains("process started")) {
      advanceToStep(stepIndexFor(key, "process_started"), "Background process started.");
      return;
    }
    if (lower.contains("starting a gradle daemon") || lower.contains("daemon")) {
      advanceToStep(stepIndexFor(key, "process_started"), "Starting Gradle daemon.");
      return;
    }
    if (lower.contains("configure project") || lower.contains("calculating task graph")) {
      advanceToStep(stepIndexFor(key, "configure"), "Configuring Gradle project modules.");
      return;
    }
    if (lower.startsWith("> task")) {
      handleGradleTaskStep(key, lower, line);
      return;
    }
    if (lower.contains("build successful") || lower.contains("build failed")) {
      advanceToStep(99, line);
      return;
    }
    if (key.equals("update engine")) {
      if (lower.contains("fetch") || lower.contains("from ")) {
        advanceToStep(2, "Fetching upstream changes.");
      } else if (lower.contains("rebas") || lower.contains("updating") || lower.contains("fast-forward")) {
        advanceToStep(3, "Applying engine update.");
      }
    } else if (key.equals("build shortcuts")) {
      if (lower.contains("install") || lower.contains("shortcut") || lower.contains("application")) {
        advanceToStep(4, "Creating OS shortcuts.");
      }
    } else if (key.equals("clean before update")) {
      if (lower.contains("restore")) {
        advanceToStep(2, "Restoring tracked build output.");
      } else if (lower.contains("clean") || lower.contains("remov")) {
        advanceToStep(3, "Removing generated files.");
      }
    }
  }

  private void handleGradleTaskStep(String key, String lowerTaskLine, String originalLine) {
    if (lowerTaskLine.contains("compiletest") || lowerTaskLine.contains(":testclasses")) {
      advanceToStep(stepIndexFor(key, "test_compile"), originalLine);
      return;
    }
    if (lowerTaskLine.matches(".*:test(\\s|$).*")) {
      advanceToStep(stepIndexFor(key, "test_run"), originalLine);
      return;
    }
    if (isEditorUiLaunch(key) && lowerTaskLine.contains(":editor:compile")) {
      advanceToStep(stepIndexFor(key, "app_compile"), originalLine);
      return;
    }
    if (isEditorUiLaunch(key) && lowerTaskLine.contains("processresources")) {
      advanceToStep(stepIndexFor(key, "resources"), originalLine);
      return;
    }
    if (lowerTaskLine.contains("compile")) {
      advanceToStep(stepIndexFor(key, "compile"), originalLine);
      return;
    }
    if (lowerTaskLine.contains(":classes") || lowerTaskLine.contains(":jar")) {
      advanceToStep(stepIndexFor(key, "classpath"), originalLine);
      return;
    }
    if (lowerTaskLine.contains(":editor:runlauncher")) {
      advanceToStep(stepIndexFor(key, "app_start"), "Starting standalone launcher.");
      return;
    }
    if (lowerTaskLine.contains(":editor:run")) {
      advanceToStep(stepIndexFor(key, "app_start"), originalLine);
      return;
    }
    if (lowerTaskLine.contains("assemble") || lowerTaskLine.contains("build")) {
      advanceToStep(key.equals("build all") ? 6 : 5, originalLine);
    }
  }

  private int stepIndexFor(String key, String milestone) {
    boolean launch = isEditorUiLaunch(key);
    if (launch) {
      return switch (milestone) {
        case "prepare_environment" -> 3;
        case "process_started" -> 4;
        case "configure" -> 6;
        case "compile" -> 9;
        case "app_compile" -> 10;
        case "resources" -> 11;
        case "classpath" -> 12;
        case "app_start" -> 13;
        default -> activeStepIndex;
      };
    }
    return switch (milestone) {
      case "prepare_environment" -> 2;
      case "process_started" -> 3;
      case "configure" -> 4;
      case "compile", "test_compile" -> 5;
      case "test_run" -> 6;
      case "classpath", "resources", "app_compile", "app_start" -> 5;
      default -> activeStepIndex;
    };
  }

  private static boolean isEditorUiLaunch(String key) {
    return "run editor".equals(key) || "run launcher".equals(key) || "help center".equals(key);
  }

  private void startAutoStepTicker(String label) {
    SwingUtilities.invokeLater(() -> {
      activeStepLabel = label == null ? "" : label.trim();
      autoStepTimer.setInitialDelay(1200);
      if (!autoStepTimer.isRunning()) autoStepTimer.start();
    });
  }

  private void stopAutoStepTicker() {
    if (autoStepTimer.isRunning()) autoStepTimer.stop();
  }

  private void autoAdvanceDuringSilence() {
    if (activeStepIndex < 0 || activitySteps.stepCount() == 0) {
      stopAutoStepTicker();
      return;
    }
    String key = activeStepLabel == null ? "" : activeStepLabel.trim().toLowerCase(Locale.ROOT);
    int limit = autoAdvanceLimit(key);
    if (activeStepIndex >= limit) {
      stopAutoStepTicker();
      return;
    }
    int next = activeStepIndex + 1;
    activitySteps.setStatus(activeStepIndex, StepStatus.DONE);
    activeStepIndex = next;
    activitySteps.setStatus(activeStepIndex, StepStatus.RUNNING);
    activityDetail.setText(compactMessage(activitySteps.stepAt(activeStepIndex)));
    updateActivityProgressFill();
  }

  private int autoAdvanceLimit(String key) {
    int last = Math.max(0, activitySteps.stepCount() - 1);
    if ("run editor".equals(key) || "run launcher".equals(key)) {
      return Math.max(0, last - 1);
    }
    if ("build all".equals(key) || "run tests".equals(key)) {
      return Math.max(0, last - 1);
    }
    if ("build shortcuts".equals(key) || "update engine".equals(key)) {
      return Math.max(0, last - 1);
    }
    return Math.max(0, last - 1);
  }

  private void checkIncomingUpdates(boolean fetchFirst) {
    if (updateEngineButton == null) return;
    if (!Files.isDirectory(projectRoot.resolve(".git"))) {
      lastKnownIncoming = -1;
      updateEngineButton.setIncomingCount(-1);
      return;
    }
    if (!commandExists("git")) {
      lastKnownIncoming = -1;
      updateEngineButton.setIncomingCount(-1);
      return;
    }
    if (!updateCheckRunning.compareAndSet(false, true)) return;

    updateEngineButton.setChecking(true);
    refreshModeMenus();
    new SwingWorker<Integer, Void>() {
      @Override protected Integer doInBackground() {
        if (fetchFirst) {
          runGit(List.of("git", "fetch", "--quiet", "--prune", "--no-tags", ENGINE_UPDATE_REMOTE, ENGINE_UPDATE_FETCH_REFSPEC), 45);
        }
        CommandResult result = runGit(List.of("git", "rev-list", "--count", "HEAD.." + ENGINE_UPDATE_REMOTE_REF), 10);
        if (result.exitCode != 0) return -1;
        try {
          return Math.max(0, Integer.parseInt(result.output.strip()));
        } catch (NumberFormatException e) {
          return -1;
        }
      }

      @Override protected void done() {
        updateCheckRunning.set(false);
        int count = -1;
        try {
          count = get();
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
          // Keep the badge hidden when Git cannot report an upstream count.
        }
        lastKnownIncoming = count;
        updateEngineButton.setIncomingCount(count);
        refreshModeMenus();
      }
    }.execute();
  }

  private CommandResult runGit(List<String> command, long timeoutSeconds) {
    return runLocalCommand(command, timeoutSeconds);
  }

  private CommandResult runLocalCommand(List<String> command, long timeoutSeconds) {
    Process process;
    try {
      process = new ProcessBuilder(command)
          .directory(projectRoot.toFile())
          .redirectErrorStream(true)
          .start();
    } catch (IOException e) {
      return new CommandResult(-1, e.getMessage());
    }

    StringBuilder output = new StringBuilder();
    Thread reader = new Thread(() -> {
      try (BufferedReader br = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) {
          if (output.length() < 4096) output.append(line).append('\n');
        }
      } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
        // Best-effort capture only.
      }
    }, "jvn-hub-command-output");
    reader.setDaemon(true);
    reader.start();

    try {
      boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return new CommandResult(-1, "timed out");
      }
      reader.join(1000);
      return new CommandResult(process.exitValue(), output.toString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      return new CommandResult(-1, "interrupted");
    }
  }

  private List<String> inspectInterruptedGitOperations() {
    List<String> operations = new ArrayList<>();
    if (gitPathExists("rebase-merge") || gitPathExists("rebase-apply")) {
      operations.add("rebase in progress");
    }
    if (gitPathExists("MERGE_HEAD")) {
      operations.add("merge in progress");
    }
    if (gitPathExists("CHERRY_PICK_HEAD")) {
      operations.add("cherry-pick in progress");
    }
    if (gitPathExists("REVERT_HEAD")) {
      operations.add("revert in progress");
    }
    return List.copyOf(operations);
  }

  private boolean gitPathExists(String gitPath) {
    if (gitPath == null || gitPath.isBlank()) return false;
    CommandResult result = runGit(List.of("git", "rev-parse", "--git-path", gitPath), 5);
    if (result.exitCode != 0 || result.output.isBlank()) return false;
    String firstLine = result.output.split("\\R", 2)[0].trim();
    if (firstLine.isBlank()) return false;
    Path path = Paths.get(firstLine);
    if (!path.isAbsolute()) path = projectRoot.resolve(path);
    return Files.exists(path);
  }

  private static List<GitStatusEntry> parseGitStatus(String output) {
    if (output == null || output.isBlank()) return List.of();
    List<GitStatusEntry> entries = new ArrayList<>();
    for (String line : output.split("\\R")) {
      if (line.length() < 4) continue;
      String code = line.substring(0, 2);
      String path = line.substring(3).trim();
      int renameArrow = path.indexOf(" -> ");
      if (renameArrow >= 0) {
        path = path.substring(renameArrow + 4).trim();
      }
      if (!path.isBlank()) entries.add(new GitStatusEntry(code, path));
    }
    return entries;
  }

  private static boolean isBuildOutputPath(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) return false;
    String path = rawPath.replace('\\', '/');
    return path.equals("build")
        || path.startsWith("build/")
        || path.contains("/build/")
        || path.endsWith("/build")
        || path.equals("bin")
        || path.startsWith("bin/")
        || path.contains("/bin/")
        || path.endsWith("/bin")
        || path.equals("out")
        || path.startsWith("out/")
        || path.contains("/out/")
        || path.endsWith("/out")
        || path.equals(".gradle")
        || path.startsWith(".gradle/")
        || path.contains("/.gradle/")
        || path.equals(".jvn-gradle-user-home")
        || path.startsWith(".jvn-gradle-user-home/");
  }

  private static String summarizeEntries(List<GitStatusEntry> entries) {
    if (entries == null || entries.isEmpty()) return "No changed files.";
    StringBuilder out = new StringBuilder();
    int limit = Math.min(entries.size(), 12);
    for (int i = 0; i < limit; i++) {
      GitStatusEntry entry = entries.get(i);
      out.append(entry.code().trim().isBlank() ? "changed" : entry.code().trim())
          .append("  ")
          .append(entry.path())
          .append('\n');
    }
    if (entries.size() > limit) {
      out.append("... and ").append(entries.size() - limit).append(" more.\n");
    }
    return out.toString().trim();
  }

  private static String escapeHtml(String text) {
    if (text == null) return "";
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private record CommandResult(int exitCode, String output) {}

  private record UpdateAttempt(
      String beforeRevision,
      String branch,
      String beforeVersion,
      long startedNanos) {}

  private record UpdatePreflightInspection(UpdatePreflight preflight, UpdateAttempt attempt) {}

  private record HubRelaunchResult(boolean ready, String details) {}

  private record UpdateIntegrationResult(
      boolean integrated,
      String beforeRevision,
      String afterRevision,
      String branch,
      String beforeVersion,
      String afterVersion,
      int behindCount,
      UpdatePreflight postflight,
      long elapsedMillis,
      String gitOutput) {

    static UpdateIntegrationResult failed(UpdateAttempt attempt, String gitOutput, String failure) {
      return new UpdateIntegrationResult(
          false,
          attempt.beforeRevision(),
          "unknown",
          attempt.branch(),
          attempt.beforeVersion(),
          attempt.beforeVersion(),
          -1,
          UpdatePreflight.unavailable(firstNonBlank(failure, "Integration verification failed.")),
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - attempt.startedNanos()),
          gitOutput);
    }
  }

  private record UpdateRecoveryResult(
      boolean ok,
      CommandResult abortResult,
      UpdatePreflight preflight,
      String sourceOutput) {}

  private enum UpdatePreflightAction {
    CLEAN_AND_UPDATE,
    CANCEL
  }

  private record GitStatusEntry(String code, String path) {
    boolean untracked() {
      return "??".equals(code);
    }

    boolean trackedChange() {
      return !untracked() && !"!!".equals(code);
    }
  }

  private record UpdatePreflight(
      List<GitStatusEntry> allChanges,
      List<GitStatusEntry> buildOutputChanges,
      List<GitStatusEntry> otherChanges,
      List<String> interruptedGitOperations,
      String statusError) {

    static UpdatePreflight unavailable(String error) {
      return new UpdatePreflight(List.of(), List.of(), List.of(), List.of(), error == null ? "" : error);
    }

    static UpdatePreflight empty() {
      return new UpdatePreflight(List.of(), List.of(), List.of(), List.of(), "");
    }

    static UpdatePreflight from(List<GitStatusEntry> entries) {
      return from(entries, List.of());
    }

    static UpdatePreflight from(List<GitStatusEntry> entries, List<String> interruptedGitOperations) {
      List<GitStatusEntry> build = new ArrayList<>();
      List<GitStatusEntry> other = new ArrayList<>();
      for (GitStatusEntry entry : entries) {
        if (isBuildOutputPath(entry.path())) {
          build.add(entry);
        } else {
          other.add(entry);
        }
      }
      return new UpdatePreflight(
          List.copyOf(entries),
          List.copyOf(build),
          List.copyOf(other),
          interruptedGitOperations == null ? List.of() : List.copyOf(interruptedGitOperations),
          "");
    }

    boolean statusUnavailable() {
      return statusError != null && !statusError.isBlank();
    }

    boolean isEmpty() {
      return !statusUnavailable() && !hasChanges() && !hasInterruptedGitOperation();
    }

    boolean hasChanges() {
      return !allChanges.isEmpty();
    }

    boolean hasInterruptedGitOperation() {
      return interruptedGitOperations != null && !interruptedGitOperations.isEmpty();
    }

    boolean onlyBuildOutput() {
      return hasChanges() && !buildOutputChanges.isEmpty() && otherChanges.isEmpty();
    }

    String summary() {
      StringBuilder out = new StringBuilder();
      if (hasInterruptedGitOperation()) {
        out.append("Interrupted Git state:\n");
        for (String operation : interruptedGitOperations) {
          out.append("- ").append(operation).append('\n');
        }
      }
      if (onlyBuildOutput()) {
        if (out.length() > 0) out.append('\n');
        out.append("Build output:\n").append(summarizeEntries(buildOutputChanges));
        return out.toString();
      }
      if (!otherChanges.isEmpty()) {
        if (out.length() > 0) out.append('\n');
        out.append("Other local changes:\n").append(summarizeEntries(otherChanges));
      }
      if (!buildOutputChanges.isEmpty()) {
        if (out.length() > 0) out.append("\n\n");
        out.append("Build output:\n").append(summarizeEntries(buildOutputChanges));
      }
      return out.length() == 0 ? "No changed files." : out.toString();
    }
  }

  private static String compactMessage(String text) {
    if (text == null) return "";
    String compact = text.replaceAll("\\s+", " ").trim();
    if (compact.startsWith("[hub] ")) compact = compact.substring(6);
    if (compact.length() > 96) {
      compact = compact.substring(0, 93) + "...";
    }
    return compact;
  }

  private static void rememberProcessOutput(List<String> recentOutput, String line) {
    if (recentOutput == null || line == null || line.isBlank()) return;
    while (recentOutput.size() >= PROCESS_OUTPUT_TAIL_LINES) {
      recentOutput.remove(0);
    }
    recentOutput.add(line.trim());
  }

  private static String summarizeProcessFailure(String outputPrefix, List<String> recentOutput, String fallback) {
    String combined = ((outputPrefix == null ? "" : outputPrefix) + "\n"
        + (recentOutput == null ? "" : String.join("\n", recentOutput))).strip();
    String friendly = friendlyProcessFailure(combined);
    if (!friendly.isBlank()) return friendly;

    List<String> candidates = new ArrayList<>();
    if (outputPrefix != null && !outputPrefix.isBlank()) {
      candidates.addAll(List.of(outputPrefix.split("\\R")));
    }
    if (recentOutput != null) candidates.addAll(recentOutput);

    for (int i = candidates.size() - 1; i >= 0; i--) {
      String line = candidates.get(i);
      if (isActionableFailureLine(line)) return compactMessage(line);
    }
    if (fallback != null && !fallback.isBlank() && !isProcessNoiseLine(fallback)) {
      return compactMessage(fallback);
    }
    return "";
  }

  private static String friendlyProcessFailure(String output) {
    if (output == null || output.isBlank()) return "";
    String lower = output.toLowerCase(Locale.ROOT);
    if (lower.contains("javafx runtime components are missing")) {
      return "JavaFX runtime components are missing from the launch classpath.";
    }
    if (lower.contains("unable to open display")) {
      return "JavaFX could not open a desktop display for this session.";
    }
    if (lower.contains("incompatible architecture") && lower.contains("javafx")) {
      return "JavaFX native libraries do not match this CPU architecture.";
    }
    if (lower.contains("glass gtk") || lower.contains("glassgtk") || lower.contains("libgtk")
        || (lower.contains("gtk") && lower.contains("javafx"))) {
      return "JavaFX GTK native support is missing or cannot load on this Linux desktop.";
    }
    if (lower.contains("x11") && (lower.contains("javafx") || lower.contains("glass"))) {
      return "JavaFX could not connect to the Linux X11 desktop session.";
    }
    if (lower.contains("graphics device initialization failed") || lower.contains("quantumrenderer")) {
      return "JavaFX graphics pipeline failed to initialize.";
    }
    if (lower.contains("no toolkit found")) {
      return "JavaFX toolkit native runtime was not available.";
    }
    return "";
  }

  private static boolean isActionableFailureLine(String line) {
    if (line == null || line.isBlank() || isProcessNoiseLine(line)) return false;
    String lower = line.toLowerCase(Locale.ROOT);
    return lower.contains("exception")
        || lower.contains("caused by:")
        || lower.contains("error")
        || lower.contains("failed")
        || lower.contains("could not")
        || lower.contains("unable to")
        || lower.contains("unsupportedclassversion")
        || lower.contains("java.lang.")
        || lower.contains("com.sun.glass")
        || lower.contains("graphics device initialization");
  }

  private static boolean isProcessNoiseLine(String line) {
    if (line == null) return true;
    String lower = line.trim().toLowerCase(Locale.ROOT);
    return lower.isBlank()
        || lower.startsWith("> task ")
        || lower.startsWith("reusing configuration cache")
        || lower.startsWith("configuration cache entry")
        || lower.startsWith("calculating task graph")
        || lower.startsWith("build failed")
        || lower.startsWith("build successful")
        || lower.startsWith("deprecated gradle features were used");
  }

  private String gradleCommand() {
    String os = System.getProperty("os.name", "").toLowerCase();
    String name = os.contains("win") ? "gradlew.bat" : "gradlew";
    Path wrapper = projectRoot.resolve(name);
    return wrapper.toAbsolutePath().toString();
  }

  private String windowsPowerShellCommand() {
    if (commandExists("pwsh")) return "pwsh";
    for (String envName : List.of("SystemRoot", "WINDIR")) {
      String root = System.getenv(envName);
      if (root == null || root.isBlank()) continue;
      Path candidate = Paths.get(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
      if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().toString();
    }
    return "powershell.exe";
  }

  private boolean commandExists(String command) {
    if (command == null || command.isBlank()) return false;
    String path = System.getenv("PATH");
    if (path == null || path.isBlank()) return false;
    String os = System.getProperty("os.name", "").toLowerCase();
    String[] dirs = path.split(java.io.File.pathSeparator);
    if (os.contains("win")) {
      String pathext = System.getenv("PATHEXT");
      String[] exts = pathext == null || pathext.isBlank()
          ? new String[] { ".exe", ".cmd", ".bat", "" }
          : pathext.toLowerCase().split(";");
      for (String dir : dirs) {
        for (String ext : exts) {
          Path candidate = Paths.get(dir, command + ext);
          if (Files.isRegularFile(candidate) || Files.isExecutable(candidate)) return true;
        }
      }
      return false;
    }
    for (String dir : dirs) {
      Path candidate = Paths.get(dir, command);
      if (Files.isExecutable(candidate)) return true;
    }
    return false;
  }

  private static String quoteCommandForLog(List<String> command) {
    if (command == null || command.isEmpty()) return "";
    List<String> quoted = new ArrayList<>();
    for (String part : command) {
      if (part == null) continue;
      boolean needsQuotes = part.isBlank()
          || part.chars().anyMatch(Character::isWhitespace)
          || part.contains("\"");
      quoted.add(needsQuotes ? "\"" + part.replace("\"", "\\\"") + "\"" : part);
    }
    return String.join(" ", quoted);
  }

  private static List<String> splitExtraGradleArgs(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    List<String> out = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inSingle = false;
    boolean inDouble = false;
    boolean escaping = false;
    for (int i = 0; i < raw.length(); i++) {
      char ch = raw.charAt(i);
      if (escaping) {
        current.append(ch);
        escaping = false;
        continue;
      }
      if (ch == '\\' && !inSingle) {
        escaping = true;
        continue;
      }
      if (ch == '\'' && !inDouble) {
        inSingle = !inSingle;
        continue;
      }
      if (ch == '"' && !inSingle) {
        inDouble = !inDouble;
        continue;
      }
      if (Character.isWhitespace(ch) && !inSingle && !inDouble) {
        if (current.length() > 0) {
          out.add(current.toString());
          current.setLength(0);
        }
        continue;
      }
      current.append(ch);
    }
    if (escaping) current.append('\\');
    if (current.length() > 0) out.add(current.toString());
    return out;
  }

  private int readRequiredJavaVersion() {
    String raw = readGradleProperty("javaVersion");
    if (raw == null || raw.isBlank()) return -1;
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
      return -1;
    }
  }

  private String readGradleProperty(String key) {
    if (key == null || key.isBlank()) return null;
    Path props = projectRoot.resolve("gradle.properties");
    if (!Files.isRegularFile(props)) return null;
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(props)) {
      p.load(in);
      return p.getProperty(key);
    } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
      return null;
    }
  }

  private int readIncomingCommitCount() {
    if (!commandExists("git") || !Files.isDirectory(projectRoot.resolve(".git"))) return -1;
    CommandResult result = runGit(List.of("git", "rev-list", "--count", "HEAD.." + ENGINE_UPDATE_REMOTE_REF), 8);
    if (result.exitCode != 0) return -1;
    try {
      return Math.max(0, Integer.parseInt(result.output.strip()));
    } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
      return -1;
    }
  }

  private String readGitValue(List<String> command, String fallback) {
    if (!commandExists("git") || !Files.isDirectory(projectRoot.resolve(".git"))) return fallback;
    CommandResult result = runGit(command, 5);
    if (result.exitCode != 0) return fallback;
    String value = result.output.strip();
    return value.isBlank() ? fallback : value;
  }

  private static int parseJavaMajor(String version) {
    if (version == null || version.isBlank()) return -1;
    String v = version.trim();
    try {
      if (v.startsWith("1.")) {
        int dot = v.indexOf('.', 2);
        return Integer.parseInt(dot > 0 ? v.substring(2, dot) : v.substring(2));
      }
      int end = 0;
      while (end < v.length() && Character.isDigit(v.charAt(end))) end++;
      return end == 0 ? -1 : Integer.parseInt(v.substring(0, end));
    } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
      return -1;
    }
  }

  private static String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private enum CheckStatus {
    PASS(ACCENT_GREEN, VectorIcon.Kind.CHECK),
    WARN(ACCENT_NEUTRAL, VectorIcon.Kind.INFO),
    FAIL(ACCENT_ERROR, VectorIcon.Kind.CLOSE),
    INFO(TEXT_MUTED, VectorIcon.Kind.INFO);

    private final Color color;
    private final VectorIcon.Kind icon;

    CheckStatus(Color color, VectorIcon.Kind icon) {
      this.color = color;
      this.icon = icon;
    }

    Color color() { return color; }
    VectorIcon.Kind icon() { return icon; }
  }

  private record HealthCheck(CheckStatus status, String title, String summary, String details) {}

  private static String readVersion() {
    try (InputStream in = JvnHub.class.getResourceAsStream("/com/jvn/hub/version.properties")) {
      if (in != null) {
        Properties props = new Properties();
        props.load(in);
        String v = props.getProperty("version");
        if (v != null && !v.isBlank()) return v.trim();
      }
    } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
      // fall through
    }
    return "dev";
  }

  /**
   * Live version lookup: read {@code gradle.properties} from disk so an Update
   * Engine pull surfaces a bumped {@code jvnVersion} without rebuilding. Falls
   * back to the build-time classpath resource if the file is unreadable.
   */
  private String readDiskVersion() {
    Path props = projectRoot.resolve("gradle.properties");
    if (Files.isRegularFile(props)) {
      Properties p = new Properties();
      try (InputStream in = Files.newInputStream(props)) {
        p.load(in);
        String v = p.getProperty("jvnVersion");
        if (v != null && !v.isBlank()) return v.trim();
      } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
        // fall through
      }
    }
    return VERSION;
  }

  static String formatVersionLabel(String rawVersion) {
    String version = displayVersionLabel(rawVersion);
    if (!isRunningFromSource()) return version;
    return "<html>" + version
        + "<br><span style='color:#ff9933;font-size:9px;font-weight:normal'>Running from source</span></html>";
  }

  private static String displayVersionLabel(String rawVersion) {
    String raw = rawVersion == null ? "" : rawVersion.trim();
    if (raw.isBlank() || raw.equalsIgnoreCase("dev") || raw.equalsIgnoreCase("vdev")) {
      return "v0.4.4.1";
    }

    String version = raw.startsWith("v") || raw.startsWith("V") ? raw.substring(1) : raw;
    String lower = version.toLowerCase(Locale.ROOT);
    String maturity = null;
    if (lower.contains("alpha") || lower.contains("snapshot") || lower.contains("dev")) {
      maturity = "Alpha";
    } else if (lower.contains("beta")) {
      maturity = "Beta";
    } else if (lower.contains("rc")) {
      maturity = "RC";
    }

    int suffix = version.indexOf('-');
    if (suffix >= 0) version = version.substring(0, suffix);
    int plus = version.indexOf('+');
    if (plus >= 0) version = version.substring(0, plus);
    version = version.trim();
    if (version.isBlank()) version = "0.4.4.1";
    return "v" + version + (maturity == null ? "" : " " + maturity);
  }

  private static boolean isRunningFromSource() {
    String override = System.getProperty("jvn.runningFromSource");
    if (override != null && !override.isBlank()) {
      String normalized = override.trim().toLowerCase(Locale.ROOT);
      return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes");
    }
    try {
      CodeSource source = JvnHub.class.getProtectionDomain().getCodeSource();
      if (source == null || source.getLocation() == null) return false;
      Path location = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
      if (Files.isDirectory(location)) return true;
      String path = location.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
      return path.contains("/build/classes/") || path.contains("/out/production/");
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return false;
    }
  }

  /** Re-reads engine metadata from disk and refreshes the Hub UI on the EDT. */
  private void refreshFromDisk() {
    String newVersion = readDiskVersion();
    RenderPipelineSettings.Mode refreshedPipeline =
        RenderPipelineSettings.load(renderPipelinePreferencesFile);
    RenderPipelineSettings.Options refreshedRenderOptions =
        RenderPipelineSettings.loadOptions(renderPipelineTuningFile);
    ProjectIconThemeSettings.Options refreshedProjectIcons =
        ProjectIconThemeSettings.load(projectIconSettingsFile);
    SwingUtilities.invokeLater(() -> {
      renderPipelineMode = refreshedPipeline;
      renderPipelineOptions = refreshedRenderOptions;
      projectIconOptions = refreshedProjectIcons;
      versionLabel.setText(formatVersionLabel(newVersion));
      appendLog("[hub] refresh: engine version " + newVersion + ".");
      frame.setJMenuBar(buildMenuBar());
      frame.revalidate();
      frame.repaint();
    });
  }

  private static Path resolveProjectRoot(String[] args) {
    // 1. Explicit override from args.
    for (int i = 0; i < args.length - 1; i++) {
      if ("--project-root".equals(args[i])) return Paths.get(args[i + 1]).toAbsolutePath();
    }
    // 2. System property set by the :hub:run Gradle task.
    String prop = System.getProperty("jvn.projectRoot");
    if (prop != null && !prop.isBlank()) return Paths.get(prop).toAbsolutePath();
    // 3. Walk up from CWD looking for gradlew + settings.gradle.kts.
    Path cwd = Paths.get(".").toAbsolutePath().normalize();
    Path probe = cwd;
    for (int i = 0; i < 8 && probe != null; i++) {
      if (looksLikeProjectRoot(probe)) return probe;
      probe = probe.getParent();
    }
    return cwd;
  }

  static boolean hasArgument(String[] args, String expected) {
    if (args == null || expected == null || expected.isBlank()) return false;
    for (String argument : args) {
      if (expected.equalsIgnoreCase(argument == null ? "" : argument.trim())) return true;
    }
    return false;
  }

  private static boolean looksLikeProjectRoot(Path dir) {
    return Files.isRegularFile(dir.resolve("gradlew"))
        || Files.isRegularFile(dir.resolve("gradlew.bat"))
        || Files.isRegularFile(dir.resolve("settings.gradle.kts"))
        || Files.isDirectory(dir.resolve(".git"));
  }

  // ===========================================================================
  //  Nested UI primitives: flat neutral button, vector icons, scrollbar UI.
  // ===========================================================================

  /** Lightweight Swing panel for the hub's charcoal launcher-style surface. */
  private static final class GradientPanel extends JPanel {
    private final Color top;
    private final Color bottom;

    GradientPanel(LayoutManager layout, Color top, Color bottom) {
      super(layout);
      this.top = top;
      this.bottom = bottom;
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setPaint(new LinearGradientPaint(
          0f,
          0f,
          0f,
          Math.max(1f, getHeight()),
          new float[] {0f, 1f},
          new Color[] {top, bottom}));
      g2.fillRect(0, 0, getWidth(), getHeight());
      g2.dispose();
      super.paintComponent(g);
    }
  }

  private static Color lighten(Color color, float amount) {
    float t = Math.max(0f, Math.min(1f, amount));
    int r = Math.round(color.getRed() + (255 - color.getRed()) * t);
    int g = Math.round(color.getGreen() + (255 - color.getGreen()) * t);
    int b = Math.round(color.getBlue() + (255 - color.getBlue()) * t);
    return new Color(r, g, b, color.getAlpha());
  }

  private static Color alpha(Color color, float alpha) {
    return new Color(
        color.getRed(),
        color.getGreen(),
        color.getBlue(),
        Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f));
  }

  private enum StepStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED
  }

  /** Paints launch progress behind the activity header and auto-scrolling step list. */
  private static final class ActivityProgressPanel extends JPanel {
    private double progress = 0.0;
    private Color tone = ACCENT_NEUTRAL;
    private boolean active = false;

    ActivityProgressPanel(LayoutManager layout) {
      super(layout);
      setOpaque(true);
    }

    void setProgress(double value) {
      double next = Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
      if (Math.abs(progress - next) < 0.001) return;
      progress = next;
      repaint();
    }

    double progress() {
      return progress;
    }

    void setTone(Color color, boolean active) {
      tone = color == null ? ACCENT_NEUTRAL : color;
      this.active = active;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (progress <= 0.001 || getWidth() <= 0 || getHeight() <= 0) return;

      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Shape oldClip = g2.getClip();
      RoundRectangle2D clip = new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), ui(14), ui(14));
      g2.setClip(clip);

      int fillWidth = Math.max(1, (int) Math.round(getWidth() * progress));
      float alpha = active ? 0.22f : 0.16f;
      g2.setPaint(new LinearGradientPaint(
          0f,
          0f,
          Math.max(1f, fillWidth),
          0f,
          new float[] {0f, 1f},
          new Color[] {withAlpha(tone, alpha), withAlpha(tone, Math.max(0.08f, alpha * 0.58f))}));
      g2.fillRect(0, 0, fillWidth, getHeight());

      g2.setPaint(new LinearGradientPaint(
          0f,
          0f,
          0f,
          Math.max(1f, getHeight()),
          new float[] {0f, 0.55f, 1f},
          new Color[] {
              withAlpha(Color.WHITE, active ? 0.045f : 0.025f),
              withAlpha(Color.WHITE, 0.0f),
              withAlpha(Color.BLACK, 0.10f)
          }));
      g2.fillRect(0, 0, fillWidth, getHeight());

      g2.setClip(oldClip);
      g2.dispose();
    }

    private static Color withAlpha(Color color, float alpha) {
      if (color == null) color = ACCENT_NEUTRAL;
      return new Color(
          color.getRed(),
          color.getGreen(),
          color.getBlue(),
          Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f));
    }
  }

  /** Checklist-style progress surface for long hub actions. */
  private static final class StepListPanel extends JComponent {
    private static final int ROW_HEIGHT = ui(22);
    private static final int TOP_BLUR_BAND = ui(46);

    private final List<String> steps = new ArrayList<>();
    private final List<StepStatus> statuses = new ArrayList<>();
    private final javax.swing.Timer scrollTimer;
    private double scrollOffset = 0.0;
    private double targetScrollOffset = 0.0;

    StepListPanel() {
      setOpaque(true);
      setBackground(PANEL_BG);
      setPreferredSize(uiDimension(0, 62));
      setMinimumSize(uiDimension(0, 62));
      scrollTimer = new javax.swing.Timer(16, e -> animateScroll());
    }

    void setSteps(List<String> newSteps) {
      steps.clear();
      statuses.clear();
      scrollOffset = 0.0;
      targetScrollOffset = 0.0;
      if (scrollTimer.isRunning()) scrollTimer.stop();
      if (newSteps != null) {
        for (String step : newSteps) {
          if (step == null || step.isBlank()) continue;
          steps.add(step.trim());
          statuses.add(StepStatus.PENDING);
        }
      }
      repaint();
    }

    int stepCount() {
      return steps.size();
    }

    StepStatus statusAt(int index) {
      if (index < 0 || index >= statuses.size()) return StepStatus.PENDING;
      return statuses.get(index);
    }

    String stepAt(int index) {
      if (index < 0 || index >= steps.size()) return "";
      return steps.get(index);
    }

    void setStatus(int index, StepStatus status) {
      if (index < 0 || index >= statuses.size()) return;
      statuses.set(index, status == null ? StepStatus.PENDING : status);
      retargetScroll();
      repaint();
    }

    double progressFraction() {
      if (statuses.isEmpty()) return 0.0;
      double units = 0.0;
      for (StepStatus status : statuses) {
        if (status == StepStatus.DONE || status == StepStatus.FAILED) {
          units += 1.0;
        } else if (status == StepStatus.RUNNING) {
          units += 0.55;
        }
      }
      return Math.max(0.0, Math.min(1.0, units / statuses.size()));
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setColor(getBackground());
      g2.fillRect(0, 0, getWidth(), getHeight());
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      Shape oldClip = g2.getClip();
      g2.clipRect(0, 0, getWidth(), getHeight());

      Font stepFont = getFont().deriveFont(Font.PLAIN, uiFont(10f));
      g2.setFont(stepFont);
      FontMetrics fm = g2.getFontMetrics();

      BufferedImage blurredDoneLayer = null;
      Graphics2D blurG = null;
      if (getWidth() > 0 && getHeight() > 0) {
        blurredDoneLayer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        blurG = blurredDoneLayer.createGraphics();
        blurG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        blurG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        blurG.setFont(stepFont);
        blurG.clipRect(0, 0, getWidth(), Math.min(getHeight(), TOP_BLUR_BAND));
      }

      for (int i = 0; i < steps.size(); i++) {
        StepStatus status = statuses.get(i);
        int yTop = (int) Math.round(i * ROW_HEIGHT - scrollOffset);
        int yCenter = yTop + ROW_HEIGHT / 2;
        if (yTop > getHeight() + ROW_HEIGHT || yTop + ROW_HEIGHT < -ROW_HEIGHT) continue;

        boolean blurCompleted = status == StepStatus.DONE && yTop < TOP_BLUR_BAND;
        if (blurCompleted && blurG != null) {
          drawStepRow(blurG, i, yCenter, fm, 0.68f, true);
        } else {
          drawStepRow(g2, i, yCenter, fm, 1.0f, false);
        }
      }

      if (blurG != null) {
        blurG.dispose();
        BufferedImage softened = soften(blurredDoneLayer);
        g2.drawImage(softened, 0, 0, null);
        paintTopVeil(g2);
      }
      g2.setClip(oldClip);
      g2.dispose();
    }

    private void drawStepRow(Graphics2D g2, int index, int y, FontMetrics fm, float alpha, boolean softened) {
      StepStatus status = statuses.get(index);
      Color color = switch (status) {
        case DONE -> ACCENT_GREEN;
        case RUNNING -> ACCENT_NEUTRAL;
        case FAILED -> ACCENT_ERROR;
        case PENDING -> TEXT_MUTED;
      };

      int cx = ui(8);
      int r = ui(4);
      g2.setColor(withAlpha(status == StepStatus.PENDING ? BORDER_NEUTRAL : color, alpha));
      g2.setStroke(new BasicStroke(uiStroke(1.2f)));
      g2.drawOval(cx - r, y - r, r * 2, r * 2);
      if (index < steps.size() - 1) {
        g2.setColor(withAlpha(BORDER_NEUTRAL, softened ? 0.24f : 0.75f));
        g2.drawLine(cx, y + r + ui(2), cx, y + ROW_HEIGHT - r - ui(2));
      }

      if (status == StepStatus.DONE) {
        g2.setColor(withAlpha(ACCENT_GREEN, alpha));
        g2.setStroke(new BasicStroke(uiStroke(1.4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D check = new Path2D.Float();
        check.moveTo(cx - ui(5), y);
        check.lineTo(cx - ui(1), y + ui(4));
        check.lineTo(cx + ui(6), y - ui(5));
        g2.draw(check);
      } else if (status == StepStatus.RUNNING) {
        g2.setColor(withAlpha(ACCENT_NEUTRAL, alpha));
        g2.fillOval(cx - ui(2), y - ui(2), ui(4), ui(4));
      } else if (status == StepStatus.FAILED) {
        g2.setColor(withAlpha(ACCENT_ERROR, alpha));
        g2.setStroke(new BasicStroke(uiStroke(1.4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx - ui(4), y - ui(4), cx + ui(4), y + ui(4));
        g2.drawLine(cx + ui(4), y - ui(4), cx - ui(4), y + ui(4));
      }

      g2.setColor(withAlpha(status == StepStatus.PENDING ? TEXT_MUTED : TEXT_SOFT, softened ? 0.54f : alpha));
      String text = steps.get(index);
      int textX = ui(22);
      int maxW = Math.max(ui(20), getWidth() - textX - ui(4));
      while (fm.stringWidth(text) > maxW && text.length() > 4) {
        text = text.substring(0, text.length() - 4) + "...";
      }
      g2.drawString(text, textX, y + (fm.getAscent() - fm.getDescent()) / 2 - ui(1));
    }

    private void paintTopVeil(Graphics2D g2) {
      int band = Math.min(getHeight(), TOP_BLUR_BAND);
      if (band <= 0) return;
      g2.setPaint(new LinearGradientPaint(
          0f,
          0f,
          0f,
          band,
          new float[] {0f, 1f},
          new Color[] {withAlpha(PANEL_BG, 0.38f), withAlpha(PANEL_BG, 0.02f)}));
      g2.fillRect(0, 0, getWidth(), band);
    }

    private void retargetScroll() {
      int focus = focusedStepIndex();
      int visibleRows = Math.max(1, getHeight() / ROW_HEIGHT);
      double desired = Math.max(0, (focus - Math.max(1, visibleRows - 2)) * ROW_HEIGHT);
      double max = Math.max(0, steps.size() * ROW_HEIGHT - getHeight());
      targetScrollOffset = Math.max(0, Math.min(max, desired));
      if (!scrollTimer.isRunning()) scrollTimer.start();
    }

    private int focusedStepIndex() {
      for (int i = 0; i < statuses.size(); i++) {
        if (statuses.get(i) == StepStatus.RUNNING || statuses.get(i) == StepStatus.FAILED) return i;
      }
      for (int i = 0; i < statuses.size(); i++) {
        if (statuses.get(i) == StepStatus.PENDING) return i;
      }
      return Math.max(0, statuses.size() - 1);
    }

    private void animateScroll() {
      double delta = targetScrollOffset - scrollOffset;
      if (Math.abs(delta) < 0.35) {
        scrollOffset = targetScrollOffset;
        scrollTimer.stop();
      } else {
        scrollOffset += delta * 0.18;
      }
      repaint();
    }

    private static BufferedImage soften(BufferedImage source) {
      if (source == null) return null;
      float ninth = 1f / 9f;
      float[] kernel = {
          ninth, ninth, ninth,
          ninth, ninth, ninth,
          ninth, ninth, ninth
      };
      return new java.awt.image.ConvolveOp(
          new java.awt.image.Kernel(3, 3, kernel),
          java.awt.image.ConvolveOp.EDGE_NO_OP,
          null).filter(source, null);
    }

    private static Color withAlpha(Color color, float alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f));
    }
  }

  /** Compact indeterminate spinner used in place of terminal-style output. */
  private static final class ActivitySpinner extends JComponent {
    private int frame = 0;
    private boolean active = false;

    ActivitySpinner() {
      setOpaque(false);
      Dimension size = uiDimension(24, 24);
      setPreferredSize(size);
      setMinimumSize(size);
      setMaximumSize(size);
    }

    void setActive(boolean active) {
      this.active = active;
      if (!active) frame = 0;
      repaint();
    }

    void tick() {
      if (!active) return;
      frame = (frame + 1) % 12;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int w = getWidth();
      int h = getHeight();
      float cx = w / 2f;
      float cy = h / 2f;
      float radius = Math.min(w, h) * 0.34f;
      float shellPad = Math.max(3f, Math.min(w, h) * 0.16f);

      g2.setColor(active ? Color.decode("#1f1f1f") : PANEL_BG);
      g2.fillOval(Math.round(cx - radius - shellPad), Math.round(cy - radius - shellPad),
          Math.round((radius + shellPad) * 2), Math.round((radius + shellPad) * 2));

      for (int i = 0; i < 12; i++) {
        int age = active ? Math.floorMod(i - frame, 12) : i;
        float alpha = active ? (0.22f + (11 - age) * 0.065f) : 0.16f;
        double angle = (Math.PI * 2.0 * i / 12.0) - Math.PI / 2.0;
        float x = cx + (float) Math.cos(angle) * radius;
        float y = cy + (float) Math.sin(angle) * radius;
        int dot = Math.max(active && age == 0 ? ui(4) : ui(3), Math.round(Math.min(w, h) * (active && age == 0 ? 0.16f : 0.12f)));
        g2.setColor(withAlpha(active ? ACCENT_NEUTRAL : TEXT_MUTED, Math.min(1.0f, alpha)));
        g2.fillOval(Math.round(x - dot / 2f), Math.round(y - dot / 2f), dot, dot);
      }

      if (!active) {
        g2.setColor(ACCENT_GREEN);
        g2.setStroke(new BasicStroke(uiStroke(1.8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D check = new Path2D.Float();
        check.moveTo(cx - Math.min(w, h) * 0.25f, cy);
        check.lineTo(cx - Math.min(w, h) * 0.04f, cy + Math.min(w, h) * 0.21f);
        check.lineTo(cx + Math.min(w, h) * 0.29f, cy - Math.min(w, h) * 0.25f);
        g2.draw(check);
      }
      g2.dispose();
    }

    private static Color withAlpha(Color color, float alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(alpha * 255f));
    }
  }

  private final class UpdateProgressDialog {
    private final JDialog dialog = new JDialog(frame, "Update Engine", false);
    private final JLabel iconLabel = new JLabel();
    private final JLabel titleLabel = new JLabel();
    private final JTextArea messageArea = dialogText("", TEXT_SOFT, 12f, Font.BOLD);
    private final JTextArea detailArea = dialogText("", LOG_TEXT, 11f, Font.PLAIN);
    private final JProgressBar progress = new JProgressBar();
    private UpdateDialogTone tone = UpdateDialogTone.QUESTION;

    UpdateProgressDialog() {
      dialog.setUndecorated(true);
      dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

      JPanel card = new JPanel(new BorderLayout(0, ui(14)));
      card.setBackground(PANEL_BG);
      card.setBorder(uiPadding(18, 20, 18, 20));

      JPanel header = new JPanel(new BorderLayout(ui(14), 0));
      header.setOpaque(false);
      iconLabel.setIcon(new UpdateDialogIcon(tone, ui(34)));
      header.add(iconLabel, BorderLayout.WEST);

      JPanel titleStack = new JPanel();
      titleStack.setOpaque(false);
      titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
      titleLabel.setForeground(TEXT_PRIMARY);
      titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, uiFont(17f)));
      titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
      messageArea.setAlignmentX(Component.LEFT_ALIGNMENT);
      messageArea.setBorder(uiPadding(4, 0, 0, 0));
      titleStack.add(titleLabel);
      titleStack.add(messageArea);
      header.add(titleStack, BorderLayout.CENTER);

      JButton hide = iconOnlyButton(uiIcon(VectorIcon.Kind.CLOSE, 12, TEXT_MUTED));
      hide.setToolTipText("Hide update details");
      hide.addActionListener(e -> dialog.setVisible(false));
      header.add(hide, BorderLayout.EAST);
      card.add(header, BorderLayout.NORTH);

      detailArea.setBorder(uiPadding(10, 12, 10, 12));
      JScrollPane scroll = new JScrollPane(detailArea);
      scroll.setBorder(BorderFactory.createLineBorder(BORDER_NEUTRAL));
      scroll.setBackground(BG);
      scroll.getViewport().setBackground(BG);
      scroll.setPreferredSize(uiDimension(540, 155));
      styleScrollBar(scroll.getVerticalScrollBar());
      styleScrollBar(scroll.getHorizontalScrollBar());
      card.add(scroll, BorderLayout.CENTER);

      progress.setIndeterminate(true);
      progress.setBorderPainted(false);
      progress.setBackground(BG);
      progress.setForeground(tone.primaryColor());
      progress.setPreferredSize(uiDimension(540, 5));
      card.add(progress, BorderLayout.SOUTH);

      dialog.setContentPane(card);
      applyTone(tone);
      dialog.pack();
      dialog.setMinimumSize(uiDimension(620, 300));
    }

    boolean isDisplayable() {
      return dialog.isDisplayable();
    }

    void showCentered() {
      if (!dialog.isVisible()) {
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
      }
      dialog.toFront();
    }

    void begin(String title, String message, String details) {
      update(title, message, details, true, UpdateDialogTone.QUESTION);
      showCentered();
    }

    void update(String title, String message, String details, boolean running, UpdateDialogTone nextTone) {
      titleLabel.setText(firstNonBlank(title, "Update Engine"));
      messageArea.setText(message == null ? "" : message);
      detailArea.setText(details == null ? "" : details);
      detailArea.setCaretPosition(0);
      progress.setIndeterminate(running);
      progress.setVisible(running);
      applyTone(nextTone == null ? UpdateDialogTone.QUESTION : nextTone);
      dialog.pack();
    }

    void appendTechnicalLine(String line) {
      if (line == null || line.isBlank()) return;
      String text = detailArea.getText();
      String updated = text + (text.isBlank() ? "" : "\n") + line.strip();
      if (updated.length() > 12000) updated = updated.substring(updated.length() - 12000);
      detailArea.setText(updated);
      detailArea.setCaretPosition(detailArea.getDocument().getLength());
    }

    private void applyTone(UpdateDialogTone nextTone) {
      tone = nextTone;
      dialog.getRootPane().setBorder(BorderFactory.createLineBorder(tone.borderColor(), 1));
      iconLabel.setIcon(new UpdateDialogIcon(tone, ui(34)));
      progress.setForeground(tone.primaryColor());
    }
  }

  private enum UpdateDialogTone {
    QUESTION(ACCENT_NEUTRAL, BORDER_NEUTRAL),
    SUCCESS(ACCENT_GREEN, Color.decode("#376347")),
    WARNING(Color.decode("#d7b56d"), Color.decode("#6d5830")),
    DANGER(ACCENT_ERROR, Color.decode("#6d3440"));

    private final Color primary;
    private final Color border;

    UpdateDialogTone(Color primary, Color border) {
      this.primary = primary;
      this.border = border;
    }

    Color primaryColor() {
      return primary;
    }

    Color borderColor() {
      return border;
    }
  }

  private static final class UpdateDialogIcon implements Icon {
    private final UpdateDialogTone tone;
    private final int size;

    private UpdateDialogIcon(UpdateDialogTone tone, int size) {
      this.tone = tone == null ? UpdateDialogTone.WARNING : tone;
      this.size = Math.max(24, size);
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.translate(x, y);

      float s = size;
      Color accent = tone.primaryColor();
      g2.setPaint(new LinearGradientPaint(
          0f, 0f, 0f, s,
          new float[] {0f, 1f},
          new Color[] {alpha(accent, 0.22f), alpha(accent, 0.08f)}));
      g2.fillOval(1, 1, size - 2, size - 2);

      g2.setColor(accent);
      g2.setStroke(new BasicStroke(uiStroke(1.3f)));
      g2.drawOval(1, 1, size - 2, size - 2);

      g2.setStroke(new BasicStroke(Math.max(2f, s * 0.09f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      if (tone == UpdateDialogTone.QUESTION) {
        g2.draw(new Arc2D.Float(s * 0.24f, s * 0.24f, s * 0.52f, s * 0.52f, 35f, 285f, Arc2D.OPEN));
        Path2D arrow = new Path2D.Float();
        arrow.moveTo(s * 0.28f, s * 0.50f);
        arrow.lineTo(s * 0.62f, s * 0.50f);
        arrow.lineTo(s * 0.50f, s * 0.38f);
        g2.draw(arrow);
      } else if (tone == UpdateDialogTone.SUCCESS) {
        Path2D check = new Path2D.Float();
        check.moveTo(s * 0.25f, s * 0.52f);
        check.lineTo(s * 0.43f, s * 0.69f);
        check.lineTo(s * 0.75f, s * 0.34f);
        g2.draw(check);
      } else {
        g2.drawLine(Math.round(s * 0.50f), Math.round(s * 0.24f), Math.round(s * 0.50f), Math.round(s * 0.58f));
        g2.fillOval(Math.round(s * 0.45f), Math.round(s * 0.70f), Math.round(s * 0.10f), Math.round(s * 0.10f));
      }
      g2.dispose();
    }

    private static Color alpha(Color color, float alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f));
    }
  }

  /**
   * Custom-painted button that stays free of L&F chrome and paints its own
   * 1px border with optional accent color. Text + icon render via {@code super.paintComponent}.
   */
  private static class FlatButton extends JButton {
    private final Color accent;

    FlatButton(String text, Icon icon, Color accentOrNull) {
      super(text);
      this.accent = accentOrNull;
      if (icon != null) {
        setIcon(icon);
        setIconTextGap(ui(10));
      }
      setHorizontalAlignment(SwingConstants.CENTER);
      setHorizontalTextPosition(SwingConstants.RIGHT);
      setForeground(accentOrNull != null ? accentOrNull : TEXT_PRIMARY);
      setFont(getFont().deriveFont(Font.PLAIN, uiFont(12f)));
      setBorder(uiPadding(10, 18, 10, 18));
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setRolloverEnabled(true);
    }

    @Override public JToolTip createToolTip() {
      return hubToolTip(this);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int arc = ui(8);
      int w = getWidth();
      int h = getHeight();

      Color fill;
      if (!isEnabled()) {
        fill = BG;
      } else if (getModel().isPressed()) {
        fill = PRESSED_BG;
      } else if (getModel().isRollover()) {
        fill = HOVER_BG;
      } else {
        fill = PANEL_BG;
      }
      Color topFill = fill.equals(PANEL_BG) ? PANEL_BG_TOP : lighten(fill, 0.035f);
      g2.setPaint(new LinearGradientPaint(
          0f,
          0f,
          0f,
          Math.max(1f, h),
          new float[] {0f, 1f},
          new Color[] {topFill, fill}));
      g2.fillRoundRect(0, 0, w, h, arc, arc);

      Color borderColor = accent != null ? accent : BORDER_NEUTRAL;
      if (!isEnabled()) borderColor = BORDER_NEUTRAL;
      g2.setColor(borderColor);
      g2.setStroke(new BasicStroke(uiStroke(1f)));
      g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

      g2.dispose();
      super.paintComponent(g);
    }
  }

  /** Update button that paints incoming commit count as a right-aligned badge. */
  private static final class UpdateEngineButton extends FlatButton {
    private int incomingCount = -1;
    private boolean checking = false;

    UpdateEngineButton(String text, Icon icon) {
      super(text, icon, ACCENT_NEUTRAL);
      setBorder(uiPadding(10, 18, 10, 52));
    }

    void setChecking(boolean checking) {
      SwingUtilities.invokeLater(() -> {
        this.checking = checking;
        if (checking && incomingCount < 0) {
          setToolTipText("Checking for incoming engine updates...");
        } else if (!checking) {
          refreshTooltip();
        }
        repaint();
      });
    }

    void setIncomingCount(int count) {
      SwingUtilities.invokeLater(() -> {
        this.checking = false;
        this.incomingCount = count;
        refreshTooltip();
        repaint();
      });
    }

    private void refreshTooltip() {
      if (incomingCount > 0) {
        setToolTipText(incomingCount + " incoming commit" + (incomingCount == 1 ? "" : "s")
            + " available from " + ENGINE_UPDATE_REMOTE_REF + ". Click to pull --rebase.");
      } else if (incomingCount == 0) {
        setToolTipText("Engine is up to date with " + ENGINE_UPDATE_REMOTE_REF + ".");
      } else {
        setToolTipText("Update Engine — incoming commit count unavailable.");
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (incomingCount <= 0) return;

      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      String text = incomingCount > 99 ? "99+" : Integer.toString(incomingCount);
      Font badgeFont = getFont().deriveFont(Font.BOLD, uiFont(11f));
      g2.setFont(badgeFont);
      FontMetrics fm = g2.getFontMetrics();
      int textW = fm.stringWidth(text);
      int badgeH = ui(22);
      int badgeW = Math.max(ui(26), textW + ui(14));
      int badgeX = getWidth() - badgeW - ui(14);
      int badgeY = (getHeight() - badgeH) / 2;

      Color fill = isEnabled() ? ACCENT_GREEN : BORDER_NEUTRAL;
      g2.setColor(fill);
      g2.fillRoundRect(badgeX, badgeY, badgeW, badgeH, badgeH, badgeH);
      g2.setColor(BG);
      int textX = badgeX + (badgeW - textW) / 2;
      int textY = badgeY + badgeH - (badgeH - fm.getAscent() + fm.getDescent()) / 2 - ui(1);
      g2.drawString(text, textX, textY);
      g2.dispose();
    }
  }

  /** Toggleable Developer Mode header control; blue when active, muted when inactive. */
  private static final class DeveloperModeToggleButton extends JToggleButton {
    DeveloperModeToggleButton() {
      setDeveloperModeEnabled(false);
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setRolloverEnabled(true);
      setBorder(uiPadding(8, 8, 8, 8));
      setPreferredSize(uiDimension(40, 40));
    }

    @Override public JToolTip createToolTip() {
      return hubToolTip(this);
    }

    void setDeveloperModeEnabled(boolean enabled) {
      setSelected(enabled);
      setIcon(WindowsSevenActionIcon.of(
          enabled ? WindowsSevenActionIcon.Kind.DEVELOPER_ACTIVE : WindowsSevenActionIcon.Kind.DEVELOPER, 24));
      setToolTipText(enabled
          ? "Developer Mode ON — tests and engineering launch flags are enabled"
          : "Developer Mode OFF — click to show developer actions");
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D h = (Graphics2D) g.create();
      h.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (getModel().isRollover() || getModel().isPressed()) {
        paintHeaderHoverBackdrop(h, this, getModel().isPressed());
      }
      if (isSelected()) {
        h.setColor(alpha(ACCENT_DEV, getModel().isPressed() ? 0.26f : getModel().isRollover() ? 0.20f : 0.14f));
        h.fillRoundRect(0, 0, getWidth(), getHeight(), ui(10), ui(10));
        h.setColor(alpha(ACCENT_DEV, 0.70f));
        h.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ui(10), ui(10));
      }
      h.dispose();
      super.paintComponent(g);
    }
  }

  /** Toggleable Safe Mode header control; amber when active, muted when inactive. */
  private static final class SafeModeToggleButton extends JToggleButton {
    SafeModeToggleButton() {
      setSafeModeEnabled(false);
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setRolloverEnabled(true);
      setBorder(uiPadding(8, 8, 8, 8));
      setPreferredSize(uiDimension(40, 40));
    }

    @Override public JToolTip createToolTip() {
      return hubToolTip(this);
    }

    void setSafeModeEnabled(boolean enabled) {
      setSelected(enabled);
      setIcon(WindowsSevenActionIcon.of(
          enabled ? WindowsSevenActionIcon.Kind.SAFE_ACTIVE : WindowsSevenActionIcon.Kind.SAFE, 24));
      setToolTipText(enabled
          ? "Safe Mode ON — launches use safe-mode flags; Update Engine uses guarded Git recovery"
          : "Safe Mode OFF — click to launch with safe-mode flags; Update Engine uses normal Git update");
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D h = (Graphics2D) g.create();
      h.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (getModel().isRollover() || getModel().isPressed()) {
        paintHeaderHoverBackdrop(h, this, getModel().isPressed());
      }
      if (isSelected()) {
        h.setColor(alpha(ACCENT_SAFE, getModel().isPressed() ? 0.26f : getModel().isRollover() ? 0.20f : 0.14f));
        h.fillRoundRect(0, 0, getWidth(), getHeight(), ui(10), ui(10));
        h.setColor(alpha(ACCENT_SAFE, 0.70f));
        h.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ui(10), ui(10));
      }
      h.dispose();
      super.paintComponent(g);
    }
  }

  /** Borderless header utility button with the shared Hub hover treatment. */
  private static class HeaderIconButton extends JButton {
    HeaderIconButton(Icon icon, String tooltip) {
      setIcon(icon);
      setToolTipText(tooltip);
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setRolloverEnabled(true);
      setBorder(uiPadding(8, 8, 8, 8));
      setPreferredSize(uiDimension(40, 40));
    }

    @Override public JToolTip createToolTip() {
      return hubToolTip(this);
    }

    @Override
    protected void paintComponent(Graphics g) {
      if (getModel().isRollover() || getModel().isPressed()) {
        Graphics2D h = (Graphics2D) g.create();
        h.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintHeaderHoverBackdrop(h, this, getModel().isPressed());
        h.dispose();
      }
      super.paintComponent(g);
    }
  }

  private static void paintHeaderHoverBackdrop(Graphics2D g, JComponent component, boolean pressed) {
    float radius = ui(17);
    float centerX = component.getWidth() / 2.0f;
    float centerY = component.getHeight() / 2.0f + ui(1);
    float strength = pressed ? 0.34f : 0.24f;
    RadialGradientPaint shadow = new RadialGradientPaint(
        new Point2D.Float(centerX, centerY),
        radius,
        new float[]{0.0f, 0.48f, 0.76f, 1.0f},
        new Color[]{
            alpha(Color.WHITE, strength),
            alpha(Color.WHITE, strength * 0.72f),
            alpha(Color.WHITE, strength * 0.24f),
            alpha(Color.WHITE, 0.0f)
        });
    g.setPaint(shadow);
    g.fillOval(
        Math.round(centerX - radius),
        Math.round(centerY - radius),
        Math.round(radius * 2.0f),
        Math.round(radius * 2.0f));
  }

  /** Temporary terminal-style size readout painted over the Hub during live resize. */
  private static final class ResizeOverlay extends JComponent {
    private final JFrame owner;
    private String windowSize = "";
    private String contentSize = "";

    ResizeOverlay(JFrame owner) {
      this.owner = owner;
      setOpaque(false);
      setVisible(false);
    }

    void updateDimensions() {
      windowSize = formatWindowPixels(owner.getWidth(), owner.getHeight());
      Component content = owner.getContentPane();
      contentSize = "CONTENT  " + formatWindowPixels(content.getWidth(), content.getHeight());
      repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      int width = getWidth();
      int height = getHeight();
      if (width <= 0 || height <= 0) return;
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(30, 14, 2, 112));
        g.fillRect(0, 0, width, height);
        paintCloud(g, width * 0.18f, height * 0.35f, Math.max(width, height) * 0.46f, 86);
        paintCloud(g, width * 0.52f, height * 0.62f, Math.max(width, height) * 0.55f, 72);
        paintCloud(g, width * 0.84f, height * 0.28f, Math.max(width, height) * 0.40f, 64);
        paintCloud(g, width * 0.70f, height * 0.88f, Math.max(width, height) * 0.34f, 52);

        Font dimensionFont = getFont().deriveFont(Font.BOLD, uiFont(34f));
        g.setFont(dimensionFont);
        FontMetrics dimensionMetrics = g.getFontMetrics();
        int dimensionX = (width - dimensionMetrics.stringWidth(windowSize)) / 2;
        int baseline = height / 2 + dimensionMetrics.getAscent() / 3;
        g.setColor(new Color(0, 0, 0, 145));
        g.drawString(windowSize, dimensionX + ui(2), baseline + ui(3));
        g.setColor(Color.decode("#fff4e8"));
        g.drawString(windowSize, dimensionX, baseline);

        Font detailFont = getFont().deriveFont(Font.BOLD, uiFont(10f));
        g.setFont(detailFont);
        FontMetrics detailMetrics = g.getFontMetrics();
        int detailX = (width - detailMetrics.stringWidth(contentSize)) / 2;
        int detailBaseline = baseline + ui(24);
        g.setColor(new Color(255, 210, 168, 225));
        g.drawString(contentSize, detailX, detailBaseline);
      } finally {
        g.dispose();
      }
    }

    private static void paintCloud(Graphics2D g, float centerX, float centerY, float radius, int alpha) {
      float safeRadius = Math.max(1f, radius);
      g.setPaint(new RadialGradientPaint(
          new Point2D.Float(centerX, centerY),
          safeRadius,
          new float[] {0f, 0.45f, 1f},
          new Color[] {
              new Color(255, 145, 38, alpha),
              new Color(225, 91, 12, alpha / 2),
              new Color(116, 36, 0, 0)
          }));
      g.fill(new Ellipse2D.Float(
          centerX - safeRadius,
          centerY - safeRadius,
          safeRadius * 2f,
          safeRadius * 2f));
    }
  }

  /** Glass-and-chrome action glyphs inspired by the Windows 7 desktop era. */
  private static final class WindowsSevenActionIcon implements Icon {
    enum Kind {
      EDITOR, LAUNCHER, LAUNCHER_MAINTENANCE, BUILD, SHORTCUT, UPDATE, TESTS, OPTIONS,
      MORE, CANCEL, QUIT,
      DEVELOPER, DEVELOPER_ACTIVE, SAFE, SAFE_ACTIVE,
      DIAGNOSTICS, ABOUT, DOCUMENTATION
    }

    private final Kind kind;
    private final int size;

    private WindowsSevenActionIcon(Kind kind, int size) {
      this.kind = kind;
      this.size = size;
    }

    static WindowsSevenActionIcon of(Kind kind) {
      return new WindowsSevenActionIcon(kind, ui(26));
    }

    static WindowsSevenActionIcon of(Kind kind, int logicalSize) {
      return new WindowsSevenActionIcon(kind, ui(logicalSize));
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (WindowsSevenHubIconPainter.paint(g, kind.name())) return;
        switch (kind) {
          case EDITOR -> paintEditor(g);
          case LAUNCHER -> paintLauncher(g, false);
          case LAUNCHER_MAINTENANCE -> paintLauncher(g, true);
          case BUILD -> paintBuild(g);
          case SHORTCUT -> paintShortcut(g);
          case UPDATE -> paintUpdate(g);
          case TESTS -> paintTests(g);
          case OPTIONS -> paintOptions(g);
          case MORE -> paintMore(g);
          case CANCEL -> paintCancel(g);
          case QUIT -> paintQuit(g);
          case DEVELOPER -> paintDeveloper(g, false);
          case DEVELOPER_ACTIVE -> paintDeveloper(g, true);
          case SAFE -> paintSafe(g, false);
          case SAFE_ACTIVE -> paintSafe(g, true);
          case DIAGNOSTICS -> paintDiagnostics(g);
          case ABOUT -> paintAbout(g);
          case DOCUMENTATION -> paintDocumentation(g);
        }
      } finally {
        g.dispose();
      }
    }

    private static void paintEditor(Graphics2D g) {
      shadow(g, 3, 3, 18, 15, 3);
      g.setPaint(new java.awt.GradientPaint(0, 3, Color.decode("#65c9ff"), 0, 18, Color.decode("#1265b5")));
      g.fillRoundRect(3, 2, 18, 15, 3, 3);
      g.setColor(Color.decode("#b9eaff"));
      g.drawRoundRect(3, 2, 17, 14, 3, 3);
      gloss(g, 4, 3, 16, 6, 3);
      g.setColor(Color.decode("#d8e9f4"));
      g.fillRoundRect(9, 17, 6, 2, 1, 1);
      g.fillRoundRect(6, 19, 12, 2, 2, 2);
      g.rotate(-0.72, 17, 14);
      g.setPaint(new java.awt.GradientPaint(14, 8, Color.decode("#ffe77a"), 20, 18, Color.decode("#f28b19")));
      g.fillRoundRect(16, 7, 4, 13, 2, 2);
      g.setColor(Color.decode("#7a491c"));
      g.drawRoundRect(16, 7, 3, 12, 2, 2);
      g.setColor(Color.decode("#f8d8b4"));
      g.fillPolygon(new int[] {16, 20, 18}, new int[] {20, 20, 23}, 3);
    }

    private static void paintLauncher(Graphics2D g, boolean maintenance) {
      Color top = maintenance ? Color.decode("#ffd36e") : Color.decode("#74ddff");
      Color bottom = maintenance ? Color.decode("#b76513") : Color.decode("#1769b5");
      glassOrb(g, top, bottom);

      Path2D rocket = new Path2D.Float();
      rocket.moveTo(13, 5);
      rocket.curveTo(17, 6, 19, 9, 19, 12);
      rocket.lineTo(14, 17);
      rocket.lineTo(10, 17);
      rocket.lineTo(6, 21);
      rocket.lineTo(7, 15);
      rocket.lineTo(12, 10);
      rocket.curveTo(12, 8, 12, 6, 13, 5);
      rocket.closePath();
      g.setPaint(new LinearGradientPaint(
          7f, 5f, 18f, 19f,
          new float[] {0f, 0.45f, 1f},
          new Color[] {Color.WHITE, Color.decode("#dceaf3"), Color.decode("#8297a8")}));
      g.fill(rocket);
      g.setColor(maintenance ? Color.decode("#7b3b0c") : Color.decode("#174b74"));
      g.setStroke(new BasicStroke(1.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.draw(rocket);
      g.setPaint(new RadialGradientPaint(
          new Point2D.Float(15f, 10f), 3.2f,
          new float[] {0f, 0.62f, 1f},
          new Color[] {Color.WHITE, Color.decode("#7fdcff"), Color.decode("#1765a2")}));
      g.fillOval(13, 8, 4, 4);
      g.setColor(new Color(255, 255, 255, 190));
      g.drawOval(13, 8, 4, 4);
      g.setPaint(new LinearGradientPaint(
          7f, 17f, 7f, 23f,
          new float[] {0f, 0.5f, 1f},
          new Color[] {Color.decode("#fff4a0"), Color.decode("#ff9b28"), new Color(214, 48, 18, 40)}));
      g.fillPolygon(new int[] {8, 11, 7}, new int[] {17, 18, 23}, 3);
    }

    private static void paintBuild(Graphics2D g) {
      shadow(g, 2, 10, 15, 11, 3);
      for (int row = 0; row < 2; row++) {
        for (int col = 0; col < 2; col++) {
          int bx = 2 + col * 7;
          int by = 9 + row * 6;
          g.setPaint(new java.awt.GradientPaint(bx, by, Color.decode("#6fd7ff"), bx, by + 6, Color.decode("#1670be")));
          g.fillRoundRect(bx, by, 6, 5, 2, 2);
          g.setColor(Color.decode("#bcecff"));
          g.drawRoundRect(bx, by, 5, 4, 2, 2);
        }
      }
      g.rotate(-0.68, 16, 10);
      g.setPaint(new java.awt.GradientPaint(14, 2, Color.decode("#fff0a0"), 19, 8, Color.decode("#d78919")));
      g.fillRoundRect(13, 2, 9, 6, 2, 2);
      g.setColor(Color.decode("#76501f"));
      g.drawRoundRect(13, 2, 8, 5, 2, 2);
      g.setPaint(new java.awt.GradientPaint(16, 7, Color.decode("#a96b37"), 18, 21, Color.decode("#5b2e18")));
      g.fillRoundRect(16, 7, 3, 14, 2, 2);
    }

    private static void paintShortcut(Graphics2D g) {
      shadow(g, 4, 2, 16, 18, 4);
      g.setPaint(new java.awt.GradientPaint(0, 2, Color.decode("#76e1ff"), 0, 21, Color.decode("#1575c7")));
      g.fillRoundRect(4, 2, 16, 18, 4, 4);
      g.setColor(Color.decode("#c7f4ff"));
      g.drawRoundRect(4, 2, 15, 17, 4, 4);
      gloss(g, 5, 3, 14, 7, 3);
      g.setColor(Color.WHITE);
      g.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(8, 15, 16, 7);
      g.drawLine(11, 7, 16, 7);
      g.drawLine(16, 7, 16, 12);
      g.setColor(Color.decode("#75d04a"));
      g.fillRoundRect(1, 14, 10, 8, 4, 4);
      g.setColor(Color.decode("#eaffdf"));
      g.drawLine(3, 18, 8, 18);
      g.drawLine(6, 16, 8, 18);
      g.drawLine(6, 20, 8, 18);
    }

    private static void paintUpdate(Graphics2D g) {
      shadow(g, 3, 3, 18, 18, 9);
      g.setPaint(new java.awt.GradientPaint(4, 3, Color.decode("#79dcff"), 19, 21, Color.decode("#1976bd")));
      g.fillOval(3, 3, 18, 18);
      g.setColor(Color.decode("#b9f2ff"));
      g.drawOval(3, 3, 17, 17);
      g.setColor(new Color(255, 255, 255, 100));
      g.fillOval(6, 4, 10, 5);
      g.setColor(Color.decode("#79d34f"));
      g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawArc(5, 5, 14, 14, 30, 145);
      g.fillPolygon(new int[] {5, 4, 9}, new int[] {8, 14, 11}, 3);
      g.setColor(Color.decode("#f2fbff"));
      g.drawArc(5, 5, 14, 14, 210, 140);
      g.fillPolygon(new int[] {19, 20, 15}, new int[] {16, 10, 13}, 3);
    }

    private static void paintTests(Graphics2D g) {
      glassTile(g, 3, 2, 18, 20, Color.decode("#80e77b"), Color.decode("#258b35"));
      g.setColor(Color.decode("#f5fff2"));
      g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(7, 8, 9, 10);
      g.drawLine(9, 10, 13, 6);
      g.drawLine(7, 15, 9, 17);
      g.drawLine(9, 17, 13, 13);
      g.setColor(Color.decode("#d8f6ff"));
      g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(15, 8, 19, 8);
      g.drawLine(15, 15, 19, 15);
    }

    private static void paintOptions(Graphics2D g) {
      glassTile(g, 2, 3, 20, 18, Color.decode("#c09aff"), Color.decode("#6740a8"));
      g.setColor(Color.decode("#f8f2ff"));
      g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      int[] ys = {8, 12, 16};
      int[] knobs = {9, 16, 12};
      for (int i = 0; i < ys.length; i++) {
        g.drawLine(6, ys[i], 18, ys[i]);
        g.setColor(Color.decode("#ffd66d"));
        g.fillOval(knobs[i] - 2, ys[i] - 2, 4, 4);
        g.setColor(Color.decode("#fff5c7"));
        g.drawOval(knobs[i] - 2, ys[i] - 2, 3, 3);
        g.setColor(Color.decode("#f8f2ff"));
      }
    }

    private static void paintMore(Graphics2D g) {
      glassTile(g, 2, 3, 20, 18, Color.decode("#77d9ff"), Color.decode("#266da9"));
      g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      for (int i = 0; i < 3; i++) {
        int y = 8 + i * 4;
        g.setColor(Color.decode("#eaf9ff"));
        g.drawLine(7, y, 18, y);
        g.setPaint(new java.awt.GradientPaint(4, y - 2, Color.WHITE, 7, y + 2, Color.decode("#9daab4")));
        g.fillOval(4, y - 2, 4, 4);
      }
    }

    private static void paintCancel(Graphics2D g) {
      glassOrb(g, Color.decode("#ff8b8b"), Color.decode("#b51f31"));
      g.setPaint(new java.awt.GradientPaint(8, 7, Color.WHITE, 16, 17, Color.decode("#e4e9ed")));
      g.fillRoundRect(7, 7, 10, 10, 2, 2);
      g.setColor(Color.decode("#8d1727"));
      g.drawRoundRect(7, 7, 9, 9, 2, 2);
    }

    private static void paintQuit(Graphics2D g) {
      glassOrb(g, Color.decode("#ff876f"), Color.decode("#a71920"));
      g.setColor(Color.decode("#fff9f4"));
      g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawArc(6, 5, 12, 14, 42, 276);
      g.drawLine(12, 3, 12, 11);
    }

    private static void paintDeveloper(Graphics2D g, boolean active) {
      glassOrb(g,
          active ? Color.decode("#66d8ff") : Color.decode("#87a8c4"),
          active ? Color.decode("#1764bc") : Color.decode("#405467"));
      g.setColor(Color.WHITE);
      g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(9, 7, 5, 12);
      g.drawLine(5, 12, 9, 17);
      g.drawLine(15, 7, 19, 12);
      g.drawLine(19, 12, 15, 17);
      g.setColor(Color.decode("#ffe879"));
      g.drawLine(14, 6, 10, 18);
    }

    private static void paintSafe(Graphics2D g, boolean active) {
      shadow(g, 3, 2, 18, 20, 7);
      Path2D shield = new Path2D.Float();
      shield.moveTo(12, 2);
      shield.curveTo(16, 5, 19, 5, 21, 6);
      shield.lineTo(20, 13);
      shield.curveTo(19, 18, 15, 21, 12, 22);
      shield.curveTo(9, 21, 5, 18, 4, 13);
      shield.lineTo(3, 6);
      shield.curveTo(7, 5, 9, 4, 12, 2);
      shield.closePath();
      g.setPaint(new java.awt.GradientPaint(4, 3,
          active ? Color.decode("#a8ef72") : Color.decode("#b1bec6"), 19, 22,
          active ? Color.decode("#318638") : Color.decode("#53626c")));
      g.fill(shield);
      g.setColor(active ? Color.decode("#eaffd8") : Color.decode("#edf3f6"));
      g.draw(shield);
      g.setColor(new Color(255, 255, 255, 105));
      g.fillOval(7, 5, 10, 5);
      g.setColor(active ? Color.decode("#fff078") : Color.WHITE);
      g.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(8, 12, 11, 15);
      g.drawLine(11, 15, 17, 9);
    }

    private static void paintDiagnostics(Graphics2D g) {
      glassTile(g, 2, 3, 20, 18, Color.decode("#74dcff"), Color.decode("#176da8"));
      g.setColor(Color.decode("#102a3a"));
      g.fillRoundRect(5, 7, 14, 10, 2, 2);
      g.setColor(Color.decode("#83f18b"));
      g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      Path2D pulse = new Path2D.Float();
      pulse.moveTo(6, 13);
      pulse.lineTo(9, 13);
      pulse.lineTo(11, 9);
      pulse.lineTo(14, 16);
      pulse.lineTo(16, 12);
      pulse.lineTo(19, 12);
      g.draw(pulse);
    }

    private static void paintAbout(Graphics2D g) {
      glassOrb(g, Color.decode("#83d8ff"), Color.decode("#235cae"));
      g.setColor(Color.WHITE);
      g.fillOval(11, 6, 3, 3);
      g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(12, 11, 12, 17);
    }

    private static void paintDocumentation(Graphics2D g) {
      shadow(g, 3, 3, 18, 18, 3);
      g.setPaint(new java.awt.GradientPaint(3, 3, Color.decode("#72cfff"), 20, 21, Color.decode("#225aa8")));
      g.fillRoundRect(3, 3, 18, 18, 3, 3);
      g.setColor(Color.decode("#d9f3ff"));
      g.drawRoundRect(3, 3, 17, 17, 3, 3);
      g.setColor(Color.WHITE);
      g.fillRoundRect(6, 6, 5, 12, 1, 1);
      g.fillRoundRect(13, 6, 5, 12, 1, 1);
      g.setColor(Color.decode("#6d92b8"));
      g.drawLine(12, 7, 12, 18);
      g.drawLine(7, 9, 10, 9);
      g.drawLine(14, 9, 17, 9);
    }

    private static void glassTile(Graphics2D g, int x, int y, int width, int height,
                                  Color top, Color bottom) {
      shadow(g, x, y, width, height, 4);
      g.setPaint(new LinearGradientPaint(
          x, y, x, y + height,
          new float[] {0f, 0.14f, 0.55f, 1f},
          new Color[] {
              brighter(top, 0.26f),
              top,
              blend(top, bottom, 0.58f),
              darker(bottom, 0.18f)
          }));
      g.fillRoundRect(x, y, width, height, 4, 4);
      g.setColor(new Color(255, 255, 255, 185));
      g.drawRoundRect(x, y, width - 1, height - 1, 4, 4);
      g.setColor(new Color(0, 18, 34, 105));
      g.drawRoundRect(x + 1, y + 1, width - 3, height - 3, 3, 3);
      gloss(g, x + 1, y + 1, width - 2, Math.max(4, height / 2), 3);
      g.setColor(new Color(255, 255, 255, 72));
      g.drawLine(x + 3, y + height - 2, x + width - 4, y + height - 2);
    }

    private static void glassOrb(Graphics2D g, Color top, Color bottom) {
      shadow(g, 3, 3, 18, 18, 18);
      g.setPaint(new RadialGradientPaint(
          new Point2D.Float(8.5f, 6.5f), 18f,
          new float[] {0f, 0.30f, 0.72f, 1f},
          new Color[] {
              brighter(top, 0.38f),
              top,
              blend(top, bottom, 0.64f),
              darker(bottom, 0.25f)
          }));
      g.fillOval(3, 3, 18, 18);
      g.setColor(new Color(255, 255, 255, 205));
      g.drawOval(3, 3, 17, 17);
      g.setColor(new Color(0, 18, 34, 100));
      g.drawOval(4, 4, 15, 15);
      g.setPaint(new LinearGradientPaint(
          0f, 4f, 0f, 11f,
          new float[] {0f, 1f},
          new Color[] {new Color(255, 255, 255, 175), new Color(255, 255, 255, 12)}));
      g.fillOval(6, 4, 12, 7);
      g.setColor(new Color(255, 255, 255, 70));
      g.drawArc(6, 7, 12, 11, 205, 128);
    }

    private static Color brighter(Color color, float amount) {
      float safe = Math.max(0f, Math.min(1f, amount));
      return new Color(
          Math.round(color.getRed() + (255 - color.getRed()) * safe),
          Math.round(color.getGreen() + (255 - color.getGreen()) * safe),
          Math.round(color.getBlue() + (255 - color.getBlue()) * safe));
    }

    private static Color darker(Color color, float amount) {
      float safe = Math.max(0f, Math.min(1f, amount));
      return new Color(
          Math.round(color.getRed() * (1f - safe)),
          Math.round(color.getGreen() * (1f - safe)),
          Math.round(color.getBlue() * (1f - safe)),
          color.getAlpha());
    }

    private static Color blend(Color first, Color second, float secondWeight) {
      float weight = Math.max(0f, Math.min(1f, secondWeight));
      float firstWeight = 1f - weight;
      return new Color(
          Math.round(first.getRed() * firstWeight + second.getRed() * weight),
          Math.round(first.getGreen() * firstWeight + second.getGreen() * weight),
          Math.round(first.getBlue() * firstWeight + second.getBlue() * weight));
    }

    private static void shadow(Graphics2D g, int x, int y, int width, int height, int arc) {
      g.setColor(new Color(0, 0, 0, 24));
      g.fillRoundRect(x - 1, y + 1, width + 2, height + 3, arc + 2, arc + 2);
      g.setColor(new Color(0, 0, 0, 48));
      g.fillRoundRect(x, y + 2, width, height + 1, arc + 1, arc + 1);
      g.setColor(new Color(0, 0, 0, 82));
      g.fillRoundRect(x + 1, y + 2, width - 2, height, arc, arc);
    }

    private static void gloss(Graphics2D g, int x, int y, int width, int height, int arc) {
      g.setPaint(new LinearGradientPaint(
          0f, y, 0f, y + height,
          new float[] {0f, 0.48f, 1f},
          new Color[] {
              new Color(255, 255, 255, 180),
              new Color(255, 255, 255, 76),
              new Color(255, 255, 255, 5)
          }));
      g.fillRoundRect(x, y, width, height, arc, arc);
    }
  }

  /**
   * Resolution-independent vector icon painted via Java2D. Color and size are both
   * configurable so the same {@link Kind} can be reused across contexts.
   */
  private static final class VectorIcon implements Icon {
    enum Kind { PLAY, EDIT, ROCKET, HAMMER, CHECK, REFRESH, STOP, CLOSE, SHORTCUT, DOCUMENTATION, HEALTH, INFO, SHIELD, DEVELOPER, SLIDERS }

    private final Kind kind;
    private final int size;
    private final Color color;

    private VectorIcon(Kind kind, int size, Color color) {
      this.kind = kind;
      this.size = size;
      this.color = color;
    }

    static VectorIcon of(Kind kind, int size, Color color) {
      return new VectorIcon(kind, size, color);
    }

    @Override public int getIconWidth()  { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g2.translate(x, y);
      g2.setColor(color);
      float s = size;
      // Strokes are sized as a fraction of the icon so all icons read consistently
      // at any size and never look hairline at large sizes.
      float strokeMain = Math.max(1.4f, s * 0.10f);
      float strokeBold = Math.max(1.6f, s * 0.13f);
      g2.setStroke(new BasicStroke(strokeMain, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

      switch (kind) {
        case PLAY -> {
          // Equilateral-ish triangle, fill + stroke gives slightly rounded corners.
          Path2D tri = new Path2D.Float();
          tri.moveTo(s * 0.30f, s * 0.20f);
          tri.lineTo(s * 0.80f, s * 0.50f);
          tri.lineTo(s * 0.30f, s * 0.80f);
          tri.closePath();
          g2.setStroke(new BasicStroke(s * 0.14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(tri);
          g2.fill(tri);
        }
        case EDIT -> {
          // Pencil drawn vertically into a rotated frame, then tilted -45°
          // (eraser at top-right, graphite tip at bottom-left).
          AffineTransform saved = g2.getTransform();
          g2.translate(s * 0.50f, s * 0.50f);
          g2.rotate(Math.PI / 4);

          float w = s * 0.22f;          // pencil width
          float halfBody = s * 0.24f;   // half the wood body length
          float eraserH = s * 0.08f;
          float ferruleH = s * 0.04f;
          float tipH = s * 0.12f;
          float corner = s * 0.04f;

          // Eraser — small rounded rectangle above the wood body.
          g2.fill(new RoundRectangle2D.Float(
              -w / 2f, -halfBody - eraserH - ferruleH,
              w, eraserH, corner, corner));
          // Ferrule (metal band) — same width, no rounding.
          g2.fillRect(
              (int) Math.round(-w / 2f), (int) Math.round(-halfBody - ferruleH),
              (int) Math.round(w), (int) Math.round(ferruleH));
          // Wood body — long rectangle.
          g2.fillRect(
              (int) Math.round(-w / 2f), (int) Math.round(-halfBody),
              (int) Math.round(w), (int) Math.round(halfBody * 2));
          // Graphite tip — triangle below the body.
          Path2D tip = new Path2D.Float();
          tip.moveTo(-w / 2f, halfBody);
          tip.lineTo(0f, halfBody + tipH);
          tip.lineTo(w / 2f, halfBody);
          tip.closePath();
          g2.fill(tip);

          g2.setTransform(saved);
        }
        case ROCKET -> {
          // Pointed-nose capsule with two angled side fins and a small tail flame.
          Path2D body = new Path2D.Float();
          body.moveTo(s * 0.50f, s * 0.08f);
          body.curveTo(s * 0.66f, s * 0.18f, s * 0.66f, s * 0.32f, s * 0.66f, s * 0.42f);
          body.lineTo(s * 0.66f, s * 0.74f);
          body.lineTo(s * 0.34f, s * 0.74f);
          body.lineTo(s * 0.34f, s * 0.42f);
          body.curveTo(s * 0.34f, s * 0.32f, s * 0.34f, s * 0.18f, s * 0.50f, s * 0.08f);
          body.closePath();
          g2.fill(body);

          // Side fins — sweep down and out from the lower body.
          Path2D finL = new Path2D.Float();
          finL.moveTo(s * 0.34f, s * 0.56f);
          finL.lineTo(s * 0.16f, s * 0.84f);
          finL.lineTo(s * 0.34f, s * 0.78f);
          finL.closePath();
          g2.fill(finL);

          Path2D finR = new Path2D.Float();
          finR.moveTo(s * 0.66f, s * 0.56f);
          finR.lineTo(s * 0.84f, s * 0.84f);
          finR.lineTo(s * 0.66f, s * 0.78f);
          finR.closePath();
          g2.fill(finR);

          // Tail flame — small downward triangle exiting the bottom of the body.
          Path2D flame = new Path2D.Float();
          flame.moveTo(s * 0.42f, s * 0.78f);
          flame.lineTo(s * 0.50f, s * 0.92f);
          flame.lineTo(s * 0.58f, s * 0.78f);
          flame.closePath();
          g2.fill(flame);
        }
        case HAMMER -> {
          // Classic T-shape: wide rounded head sitting on a vertical handle.
          float headW = s * 0.72f;
          float headH = s * 0.26f;
          float headX = (s - headW) / 2f;
          float headY = s * 0.18f;
          float handleW = s * 0.18f;
          float handleX = (s - handleW) / 2f;
          float handleY = headY + headH - s * 0.02f;
          float handleH = s - handleY - s * 0.10f;
          float corner = s * 0.06f;

          g2.fill(new RoundRectangle2D.Float(headX, headY, headW, headH, corner, corner));
          g2.fill(new RoundRectangle2D.Float(handleX, handleY, handleW, handleH, corner, corner));
        }
        case CHECK -> {
          // Bold tick with rounded joins; balanced inside the icon box.
          Path2D check = new Path2D.Float();
          check.moveTo(s * 0.18f, s * 0.52f);
          check.lineTo(s * 0.42f, s * 0.76f);
          check.lineTo(s * 0.84f, s * 0.28f);
          g2.setStroke(new BasicStroke(strokeBold, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(check);
        }
        case REFRESH -> {
          // Open circle with a tangent arrowhead at the start, pointing into the rotation direction.
          float r = s * 0.34f;
          float cx = s * 0.50f;
          float cy = s * 0.50f;
          // Arc spans 290° starting at 50° (top-right gap) — leaves room for the arrow.
          Shape arc = new Arc2D.Float(cx - r, cy - r, r * 2f, r * 2f, 50f, 290f, Arc2D.OPEN);
          g2.setStroke(new BasicStroke(strokeBold, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(arc);

          // Arrowhead at the arc's start point (50 degrees), pointing tangent to the circle.
          double theta = Math.toRadians(50);
          float ax = (float) (cx + r * Math.cos(theta));
          float ay = (float) (cy - r * Math.sin(theta));
          // Tangent direction for clockwise traversal at this point: (sin θ, cos θ) in screen coords.
          float tx = (float) Math.sin(theta);
          float ty = (float) Math.cos(theta);
          // Perpendicular for arrow width.
          float px = -ty;
          float py = tx;
          float headLen = s * 0.22f;
          float headWid = s * 0.11f;
          Path2D head = new Path2D.Float();
          head.moveTo(ax + tx * headLen * 0.5f, ay + ty * headLen * 0.5f);
          head.lineTo(ax - tx * headLen * 0.5f + px * headWid,
                      ay - ty * headLen * 0.5f + py * headWid);
          head.lineTo(ax - tx * headLen * 0.5f - px * headWid,
                      ay - ty * headLen * 0.5f - py * headWid);
          head.closePath();
          g2.fill(head);
        }
        case STOP -> {
          // Slightly-rounded filled square; corner radius scales with icon size.
          float pad = s * 0.22f;
          float r = s * 0.10f;
          g2.fill(new RoundRectangle2D.Float(pad, pad, s - pad * 2f, s - pad * 2f, r, r));
        }
        case CLOSE -> {
          g2.setStroke(new BasicStroke(strokeBold, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.drawLine((int) Math.round(s * 0.26f), (int) Math.round(s * 0.26f),
                      (int) Math.round(s * 0.74f), (int) Math.round(s * 0.74f));
          g2.drawLine((int) Math.round(s * 0.74f), (int) Math.round(s * 0.26f),
                      (int) Math.round(s * 0.26f), (int) Math.round(s * 0.74f));
        }
        case SHORTCUT -> {
          // Desktop/app shortcut: small window tile with a launch arrow.
          // Holy fuck! This is the last of the bunch. 
          float pad = s * 0.16f;
          float corner = s * 0.08f;
          g2.setStroke(new BasicStroke(strokeMain, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(new RoundRectangle2D.Float(pad, pad, s * 0.58f, s * 0.58f, corner, corner));
          g2.setStroke(new BasicStroke(strokeBold, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          Path2D arrow = new Path2D.Float();
          arrow.moveTo(s * 0.42f, s * 0.68f);
          arrow.lineTo(s * 0.82f, s * 0.28f);
          g2.draw(arrow);
          Path2D head = new Path2D.Float();
          head.moveTo(s * 0.82f, s * 0.28f);
          head.lineTo(s * 0.80f, s * 0.52f);
          head.lineTo(s * 0.58f, s * 0.30f);
          head.closePath();
          g2.fill(head);
        }
        case DOCUMENTATION -> {
          // Document sheet with a folded corner and two guide lines.
          float pad = s * 0.16f;
          float fold = s * 0.22f;
          Path2D sheet = new Path2D.Float();
          sheet.moveTo(pad, pad);
          sheet.lineTo(s - pad - fold, pad);
          sheet.lineTo(s - pad, pad + fold);
          sheet.lineTo(s - pad, s - pad);
          sheet.lineTo(pad, s - pad);
          sheet.closePath();
          g2.fill(sheet);

          g2.setColor(BG);
          Path2D cut = new Path2D.Float();
          cut.moveTo(s - pad - fold, pad);
          cut.lineTo(s - pad - fold, pad + fold);
          cut.lineTo(s - pad, pad + fold);
          cut.closePath();
          g2.fill(cut);

          g2.setColor(color);
          g2.setStroke(new BasicStroke(strokeMain, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          Path2D foldLine = new Path2D.Float();
          foldLine.moveTo(s - pad - fold, pad);
          foldLine.lineTo(s - pad - fold, pad + fold);
          foldLine.lineTo(s - pad, pad + fold);
          g2.draw(foldLine);

          g2.setColor(BG);
          g2.setStroke(new BasicStroke(Math.max(1.2f, s * 0.08f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.drawLine(Math.round(pad + s * 0.12f), Math.round(s * 0.50f),
                      Math.round(s - pad - s * 0.12f), Math.round(s * 0.50f));
          g2.drawLine(Math.round(pad + s * 0.12f), Math.round(s * 0.66f),
                      Math.round(s - pad - s * 0.22f), Math.round(s * 0.66f));
        }
        case HEALTH -> {
          // Pulse line inside a rounded monitor frame.
          float pad = s * 0.12f;
          float corner = s * 0.10f;
          g2.setStroke(new BasicStroke(strokeMain, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(new RoundRectangle2D.Float(pad, pad, s - pad * 2f, s - pad * 2f, corner, corner));
          Path2D pulse = new Path2D.Float();
          pulse.moveTo(s * 0.22f, s * 0.56f);
          pulse.lineTo(s * 0.36f, s * 0.56f);
          pulse.lineTo(s * 0.45f, s * 0.34f);
          pulse.lineTo(s * 0.56f, s * 0.70f);
          pulse.lineTo(s * 0.66f, s * 0.48f);
          pulse.lineTo(s * 0.80f, s * 0.48f);
          g2.setStroke(new BasicStroke(strokeBold, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(pulse);
        }
        case INFO -> {
          g2.setStroke(new BasicStroke(strokeMain, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(new Ellipse2D.Float(s * 0.16f, s * 0.16f, s * 0.68f, s * 0.68f));
          g2.fill(new Ellipse2D.Float(s * 0.46f, s * 0.28f, s * 0.08f, s * 0.08f));
          g2.setStroke(new BasicStroke(strokeBold, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.drawLine(Math.round(s * 0.50f), Math.round(s * 0.46f),
                      Math.round(s * 0.50f), Math.round(s * 0.70f));
        }
        case SHIELD -> {
          Path2D shield = new Path2D.Float();
          shield.moveTo(s * 0.50f, s * 0.10f);
          shield.curveTo(s * 0.66f, s * 0.20f, s * 0.78f, s * 0.22f, s * 0.84f, s * 0.24f);
          shield.lineTo(s * 0.80f, s * 0.54f);
          shield.curveTo(s * 0.76f, s * 0.74f, s * 0.62f, s * 0.86f, s * 0.50f, s * 0.92f);
          shield.curveTo(s * 0.38f, s * 0.86f, s * 0.24f, s * 0.74f, s * 0.20f, s * 0.54f);
          shield.lineTo(s * 0.16f, s * 0.24f);
          shield.curveTo(s * 0.28f, s * 0.22f, s * 0.40f, s * 0.20f, s * 0.50f, s * 0.10f);
          shield.closePath();
          g2.fill(shield);
          g2.setColor(BG);
          g2.setStroke(new BasicStroke(Math.max(1.4f, s * 0.10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          Path2D check = new Path2D.Float();
          check.moveTo(s * 0.34f, s * 0.52f);
          check.lineTo(s * 0.46f, s * 0.64f);
          check.lineTo(s * 0.68f, s * 0.40f);
          g2.draw(check);
        }
        case DEVELOPER -> {
          g2.setStroke(new BasicStroke(strokeBold, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          Path2D left = new Path2D.Float();
          left.moveTo(s * 0.38f, s * 0.30f);
          left.lineTo(s * 0.18f, s * 0.50f);
          left.lineTo(s * 0.38f, s * 0.70f);
          g2.draw(left);

          Path2D right = new Path2D.Float();
          right.moveTo(s * 0.62f, s * 0.30f);
          right.lineTo(s * 0.82f, s * 0.50f);
          right.lineTo(s * 0.62f, s * 0.70f);
          g2.draw(right);

          g2.drawLine(Math.round(s * 0.54f), Math.round(s * 0.22f),
                      Math.round(s * 0.46f), Math.round(s * 0.78f));
        }
        case SLIDERS -> {
          g2.setStroke(new BasicStroke(strokeMain, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          float[] ys = {s * 0.28f, s * 0.50f, s * 0.72f};
          float[] knobs = {s * 0.66f, s * 0.36f, s * 0.58f};
          for (int i = 0; i < ys.length; i++) {
            g2.drawLine(Math.round(s * 0.18f), Math.round(ys[i]), Math.round(s * 0.82f), Math.round(ys[i]));
            g2.fill(new Ellipse2D.Float(knobs[i] - s * 0.07f, ys[i] - s * 0.07f, s * 0.14f, s * 0.14f));
          }
        }
      }
      g2.dispose();
    }
  }

  /**
   * Vector logomark for the JVN engine, fully painted with no PNG dependency.
   * Renders "JVN" in bold sans-serif with the same white-to-gray treatment used
   * by the editor and launcher wordmark.
   * {@link #renderToImage(int, int)} exposes the same artwork as a raster so
   * the OS window/dock icon stays in sync.
   */
  private static final class JvnLogoIcon implements Icon {
    private static final Color WORDMARK_TOP = Color.decode("#ffffff");
    private static final Color WORDMARK_HIGH = Color.decode("#cfd8df");
    private static final Color WORDMARK_DARK = Color.decode("#7c8791");
    private static final Color WORDMARK_FLASH = Color.decode("#f9fbfc");
    private static final Color WORDMARK_MID = Color.decode("#9da8b2");
    private static final Color WORDMARK_LOW = Color.decode("#eef3f7");
    private static final Color WORDMARK_BOTTOM = Color.decode("#59636d");

    private final int width;
    private final int height;

    JvnLogoIcon(int width, int height) {
      this.width = width;
      this.height = height;
    }

    /** Render the logo into a fresh ARGB raster suitable for {@link JFrame#setIconImage}. */
    static BufferedImage renderToImage(int w, int h) {
      BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      try {
        new JvnLogoIcon(w, h).paintIcon(null, g, 0, 0);
      } finally {
        g.dispose();
      }
      return img;
    }

    @Override public int getIconWidth()  { return width; }
    @Override public int getIconHeight() { return height; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
      g2.translate(x, y);

      int jvnSize = Math.max(18, Math.round(height * 0.72f));
      Font jvnFont = new Font(Font.SANS_SERIF, Font.BOLD, jvnSize);
      g2.setFont(jvnFont);
      String jvn = "JVN";
      GlyphVector glyphs = jvnFont.createGlyphVector(g2.getFontRenderContext(), jvn);
      Rectangle bounds = glyphs.getPixelBounds(g2.getFontRenderContext(), 0, 0);
      double tx = (width - bounds.getWidth()) / 2.0 - bounds.getX();
      double ty = (height - bounds.getHeight()) / 2.0 - bounds.getY() - Math.max(1.0, height * 0.03);
      Shape wordmark = AffineTransform.getTranslateInstance(tx, ty).createTransformedShape(glyphs.getOutline());
      Rectangle shapeBounds = wordmark.getBounds();

      paintBrushSwipe(g2, width, height);

      g2.setColor(new Color(0, 0, 0, 95));
      g2.translate(0, Math.max(1, height * 0.05));
      g2.fill(wordmark);
      g2.translate(0, -Math.max(1, height * 0.05));

      LinearGradientPaint gradient = new LinearGradientPaint(
          0f, (float) shapeBounds.getMinY(),
          0f, (float) shapeBounds.getMaxY(),
          new float[] {0f, 0.14f, 0.27f, 0.42f, 0.58f, 0.76f, 1f},
          new Color[] {
              WORDMARK_TOP,
              WORDMARK_HIGH,
              WORDMARK_DARK,
              WORDMARK_FLASH,
              WORDMARK_MID,
              WORDMARK_LOW,
              WORDMARK_BOTTOM
          });
      g2.setPaint(gradient);
      g2.fill(wordmark);

      g2.dispose();
    }

    private static void paintBrushSwipe(Graphics2D g2, int width, int height) {
      Path2D main = new Path2D.Double();
      main.moveTo(width * 0.08, height * 0.76);
      main.curveTo(
          width * 0.24, height * 0.58,
          width * 0.47, height * 0.66,
          width * 0.88, height * 0.44);
      g2.setPaint(new LinearGradientPaint(
          0f, 0f,
          (float) width, 0f,
          new float[] {0f, 0.18f, 0.56f, 0.84f, 1f},
          new Color[] {
              new Color(255, 122, 26, 15),
              new Color(255, 143, 36, 118),
              new Color(242, 106, 33, 148),
              new Color(255, 179, 71, 78),
              new Color(255, 122, 26, 10)
          }));
      g2.setStroke(new BasicStroke(
          Math.max(7f, height * 0.22f),
          BasicStroke.CAP_ROUND,
          BasicStroke.JOIN_ROUND));
      g2.draw(main);

      Path2D highlight = new Path2D.Double();
      highlight.moveTo(width * 0.17, height * 0.84);
      highlight.curveTo(
          width * 0.36, height * 0.70,
          width * 0.56, height * 0.74,
          width * 0.80, height * 0.58);
      g2.setColor(new Color(255, 179, 71, 55));
      g2.setStroke(new BasicStroke(
          Math.max(1.8f, height * 0.045f),
          BasicStroke.CAP_ROUND,
          BasicStroke.JOIN_ROUND));
      g2.draw(highlight);

      Path2D texture = new Path2D.Double();
      texture.moveTo(width * 0.12, height * 0.64);
      texture.curveTo(
          width * 0.33, height * 0.50,
          width * 0.57, height * 0.55,
          width * 0.92, height * 0.32);
      g2.setColor(new Color(243, 107, 33, 40));
      g2.setStroke(new BasicStroke(
          Math.max(2.4f, height * 0.07f),
          BasicStroke.CAP_ROUND,
          BasicStroke.JOIN_ROUND));
      g2.draw(texture);
    }
  }

  /**
   * Flat neutral scrollbar UI: no arrow buttons, rounded thumb, BG-matching track.
   * Lightens the thumb on hover/drag for subtle feedback.
   */
  private static final class NeutralScrollBarUI extends BasicScrollBarUI {
    @Override
    protected void configureScrollBarColors() {
      this.thumbColor = SCROLL_THUMB;
      this.trackColor = BG;
      this.thumbDarkShadowColor = BG;
      this.thumbHighlightColor = SCROLL_THUMB;
      this.thumbLightShadowColor = SCROLL_THUMB;
    }

    @Override
    protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
    @Override
    protected JButton createIncreaseButton(int orientation) { return zeroButton(); }

    private static JButton zeroButton() {
      JButton b = new JButton();
      Dimension zero = new Dimension(0, 0);
      b.setPreferredSize(zero);
      b.setMinimumSize(zero);
      b.setMaximumSize(zero);
      b.setVisible(false);
      return b;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
      g.setColor(BG);
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
      if (bounds.isEmpty() || !scrollbar.isEnabled()) return;
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Color fill = (isThumbRollover() || isDragging) ? SCROLL_THUMB_HOVER : SCROLL_THUMB;
      int inset = 2;
      int arc = Math.min(bounds.width, bounds.height) - inset * 2;
      if (arc < 4) arc = 4;
      g2.setColor(fill);
      g2.fillRoundRect(bounds.x + inset, bounds.y + inset,
          bounds.width - inset * 2, bounds.height - inset * 2, arc, arc);
      g2.dispose();
    }
  }

  /**
   * Plain menu indicator used instead of host look-and-feel artwork. Aqua may
   * otherwise supply light or offset check/radio glyphs inside the dark menus.
   */
  private static final class HubMenuSelectionIcon implements Icon {
    private final boolean radio;

    HubMenuSelectionIcon(boolean radio) {
      this.radio = radio;
    }

    @Override
    public int getIconWidth() {
      return ui(14);
    }

    @Override
    public int getIconHeight() {
      return ui(14);
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      Graphics2D g2 = (Graphics2D) graphics.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      ButtonModel model = component instanceof AbstractButton button ? button.getModel() : null;
      boolean selected = model != null && model.isSelected();
      boolean enabled = component == null || component.isEnabled();
      Color accent = component instanceof JComponent jc
          && jc.getClientProperty("jvn.menu.accent") instanceof Color color
              ? color
              : TEXT_SOFT;
      Color outline = enabled ? (selected ? accent : BORDER_NEUTRAL) : Color.decode("#303030");
      int size = Math.min(getIconWidth(), getIconHeight());

      g2.setStroke(new BasicStroke(uiStroke(1f)));
      g2.setColor(outline);
      if (radio) {
        g2.drawOval(x + ui(1), y + ui(1), size - ui(3), size - ui(3));
        if (selected) {
          int dot = Math.max(ui(5), size / 2);
          int inset = (size - dot) / 2;
          g2.fillOval(x + inset, y + inset, dot, dot);
        }
      } else {
        g2.drawRoundRect(x + ui(1), y + ui(1), size - ui(3), size - ui(3), ui(3), ui(3));
        if (selected) {
          g2.setStroke(new BasicStroke(uiStroke(1.8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          Path2D mark = new Path2D.Double();
          mark.moveTo(x + size * 0.25, y + size * 0.52);
          mark.lineTo(x + size * 0.43, y + size * 0.70);
          mark.lineTo(x + size * 0.76, y + size * 0.30);
          g2.draw(mark);
        }
      }
      g2.dispose();
    }
  }

  /** Basic combo delegate with a host-independent popup and arrow button. */
  private static final class HubComboBoxUI extends BasicComboBoxUI {
    @Override
    protected JButton createArrowButton() {
      return new HubComboArrowButton();
    }

    @Override
    protected BasicComboPopup createPopup() {
      BasicComboPopup result = new BasicComboPopup(comboBox);
      result.setBorder(BorderFactory.createLineBorder(BORDER_NEUTRAL));
      result.getList().setBackground(BG);
      result.getList().setForeground(TEXT_PRIMARY);
      result.getList().setSelectionBackground(HOVER_BG);
      result.getList().setSelectionForeground(TEXT_PRIMARY);
      return result;
    }

    @Override
    public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean focused) {
      graphics.setColor(comboBox.isEnabled() ? BG : PANEL_BG);
      graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }
  }

  private static final class HubComboBoxRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        JList<?> list,
        Object value,
        int index,
        boolean selected,
        boolean focused) {
      super.getListCellRendererComponent(list, value, index, selected, focused);
      setOpaque(true);
      setBackground(selected ? HOVER_BG : BG);
      setForeground(list.isEnabled() ? TEXT_PRIMARY : TEXT_MUTED);
      setBorder(uiPadding(6, 9, 6, 9));
      return this;
    }
  }

  private static final class HubComboArrowButton extends JButton {
    HubComboArrowButton() {
      setName("ComboBox.arrowButton");
      setFocusable(false);
      setBorderPainted(false);
      setContentAreaFilled(false);
      setRolloverEnabled(true);
      setPreferredSize(uiDimension(30, 32));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g2 = (Graphics2D) graphics.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      ButtonModel model = getModel();
      Color fill = model.isPressed() ? PRESSED_BG : model.isRollover() ? HOVER_BG : PANEL_BG;
      if (!isEnabled()) fill = BG;
      g2.setColor(fill);
      g2.fillRect(0, 0, getWidth(), getHeight());
      g2.setColor(BORDER_NEUTRAL);
      g2.drawLine(0, 0, 0, getHeight());

      int centerX = getWidth() / 2;
      int centerY = getHeight() / 2;
      int halfWidth = ui(4);
      int halfHeight = ui(2);
      Path2D arrow = new Path2D.Double();
      arrow.moveTo(centerX - halfWidth, centerY - halfHeight);
      arrow.lineTo(centerX + halfWidth, centerY - halfHeight);
      arrow.lineTo(centerX, centerY + halfHeight);
      arrow.closePath();
      g2.setColor(isEnabled() ? TEXT_SOFT : TEXT_MUTED.darker());
      g2.fill(arrow);
      g2.dispose();
    }
  }

  /** Plain graphite spinner delegate with host-independent arrow controls. */
  private static final class HubSpinnerUI extends BasicSpinnerUI {
    @Override
    protected Component createNextButton() {
      JButton button = new HubSpinnerArrowButton(true);
      installNextButtonListeners(button);
      return button;
    }

    @Override
    protected Component createPreviousButton() {
      JButton button = new HubSpinnerArrowButton(false);
      installPreviousButtonListeners(button);
      return button;
    }
  }

  private static final class HubSpinnerArrowButton extends JButton {
    private final boolean pointsUp;

    HubSpinnerArrowButton(boolean pointsUp) {
      this.pointsUp = pointsUp;
      setName(pointsUp ? "Spinner.nextButton" : "Spinner.previousButton");
      setFocusable(false);
      setBorderPainted(false);
      setContentAreaFilled(false);
      setRolloverEnabled(true);
      setPreferredSize(uiDimension(24, 16));
      setMinimumSize(uiDimension(20, 12));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g2 = (Graphics2D) graphics.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      ButtonModel model = getModel();
      Color fill = model.isPressed() ? PRESSED_BG : model.isRollover() ? HOVER_BG : PANEL_BG;
      if (!isEnabled()) fill = BG;
      g2.setColor(fill);
      g2.fillRect(0, 0, getWidth(), getHeight());
      g2.setColor(BORDER_NEUTRAL);
      g2.drawLine(0, 0, 0, getHeight());
      if (pointsUp) g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);

      int centerX = getWidth() / 2;
      int centerY = getHeight() / 2;
      int halfWidth = ui(4);
      int halfHeight = ui(2);
      Path2D arrow = new Path2D.Double();
      if (pointsUp) {
        arrow.moveTo(centerX, centerY - halfHeight);
        arrow.lineTo(centerX + halfWidth, centerY + halfHeight);
        arrow.lineTo(centerX - halfWidth, centerY + halfHeight);
      } else {
        arrow.moveTo(centerX - halfWidth, centerY - halfHeight);
        arrow.lineTo(centerX + halfWidth, centerY - halfHeight);
        arrow.lineTo(centerX, centerY + halfHeight);
      }
      arrow.closePath();
      g2.setColor(isEnabled() ? TEXT_SOFT : TEXT_MUTED.darker());
      g2.fill(arrow);
      g2.dispose();
    }
  }

  private static final class HubCheckIcon implements Icon {
    private final boolean selected;

    HubCheckIcon(boolean selected) {
      this.selected = selected;
    }

    @Override
    public int getIconWidth() {
      return ui(15);
    }

    @Override
    public int getIconHeight() {
      return ui(15);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      ButtonModel model = c instanceof AbstractButton button ? button.getModel() : null;
      boolean enabled = c == null || c.isEnabled();
      boolean rollover = model != null && model.isRollover();
      boolean pressed = model != null && model.isPressed();
      int size = Math.min(getIconWidth(), getIconHeight());
      Color border = enabled ? (rollover ? ACCENT_DEV : BORDER_NEUTRAL) : Color.decode("#2b2b2b");
      Color fill = selected
          ? (enabled ? Color.decode("#1e3450") : Color.decode("#242424"))
          : (enabled ? BG : Color.decode("#181818"));
      if (pressed && enabled) fill = Color.decode("#16283d");

      g2.setColor(fill);
      g2.fillRoundRect(x, y, size, size, ui(4), ui(4));
      g2.setColor(border);
      g2.drawRoundRect(x, y, size - 1, size - 1, ui(4), ui(4));

      if (selected) {
        g2.setStroke(new BasicStroke(Math.max(1.6f, ui(2)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(enabled ? ACCENT_DEV : TEXT_MUTED);
        Path2D mark = new Path2D.Double();
        mark.moveTo(x + size * 0.26, y + size * 0.54);
        mark.lineTo(x + size * 0.44, y + size * 0.72);
        mark.lineTo(x + size * 0.76, y + size * 0.30);
        g2.draw(mark);
      }
      g2.dispose();
    }
  }

  private static final class HubLifecycleSplash extends JWindow {
    private final JLabel status;
    private final JLabel thanks;
    private final JProgressBar progress;

    private HubLifecycleSplash(JPanel content, int width, int height,
                               JLabel status, JLabel thanks, JProgressBar progress) {
      this.status = status;
      this.thanks = thanks;
      this.progress = progress;
      setBackground(new Color(0, 0, 0, 0));
      setContentPane(content);
      setAlwaysOnTop(true);
      setSize(ui(width), ui(height));
    }

    static HubLifecycleSplash startup() {
      JPanel pane = new JPanel();
      pane.setOpaque(false);
      pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
      pane.setBorder(uiPadding(18, 18, 18, 18));

      JLabel logo = centered(new JLabel(new JvnLogoIcon(ui(230), ui(118))));
      JLabel version = centered(new JLabel(displayVersionLabel(VERSION)));
      version.setForeground(Color.decode("#f4f6f8"));
      version.setFont(version.getFont().deriveFont(Font.BOLD, uiFont(18f)));
      JLabel source = centered(new JLabel(isRunningFromSource() ? "Running from source" : ""));
      source.setForeground(Color.decode("#d5dae0"));
      source.setFont(source.getFont().deriveFont(Font.BOLD, uiFont(12f)));
      source.setVisible(!source.getText().isBlank());

      pane.add(Box.createVerticalGlue());
      pane.add(logo);
      pane.add(version);
      pane.add(Box.createVerticalStrut(ui(4)));
      pane.add(source);
      pane.add(Box.createVerticalGlue());
      return new HubLifecycleSplash(pane, 520, 300, null, null, null);
    }

    static HubLifecycleSplash shutdown(String detail) {
      JPanel pane = new JPanel(new GridBagLayout());
      pane.setOpaque(false);
      pane.setBorder(uiPadding(24, 34, 24, 34));
      JPanel stack = new JPanel();
      stack.setOpaque(false);
      stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

      JLabel logo = centered(new JLabel(new JvnLogoIcon(ui(150), ui(76))));
      JLabel title = centered(new JLabel("Closing JVN"));
      title.setForeground(Color.decode("#f4f4f4"));
      title.setFont(title.getFont().deriveFont(Font.BOLD, uiFont(20f)));
      JLabel status = centered(new JLabel(detail));
      status.setForeground(Color.decode("#b8b8b8"));
      status.setFont(status.getFont().deriveFont(Font.BOLD, uiFont(12f)));
      JProgressBar progress = new JProgressBar();
      progress.setAlignmentX(Component.CENTER_ALIGNMENT);
      progress.setIndeterminate(true);
      progress.setBorderPainted(false);
      progress.setBackground(Color.decode("#111111"));
      progress.setForeground(ACCENT_DEV);
      progress.setMaximumSize(new Dimension(ui(300), ui(5)));
      progress.setPreferredSize(new Dimension(ui(300), ui(5)));
      JLabel thanks = centered(new JLabel("Thank you for choosing JVN."));
      thanks.setForeground(Color.decode("#ff9933"));
      thanks.setFont(thanks.getFont().deriveFont(Font.BOLD, uiFont(14f)));
      thanks.setVisible(false);

      stack.add(logo);
      stack.add(Box.createVerticalStrut(ui(9)));
      stack.add(title);
      stack.add(Box.createVerticalStrut(ui(9)));
      stack.add(status);
      stack.add(Box.createVerticalStrut(ui(9)));
      stack.add(progress);
      stack.add(Box.createVerticalStrut(ui(9)));
      stack.add(thanks);
      pane.add(stack);
      return new HubLifecycleSplash(pane, 390, 245, status, thanks, progress);
    }

    private static JLabel centered(JLabel label) {
      label.setAlignmentX(Component.CENTER_ALIGNMENT);
      label.setHorizontalAlignment(SwingConstants.CENTER);
      return label;
    }

    void showCentered() {
      setLocationRelativeTo(null);
      setVisible(true);
      toFront();
    }

    void completeShutdown(Runnable afterClose) {
      status.setText("Everything is safely closed.");
      progress.setIndeterminate(false);
      progress.setValue(100);
      thanks.setVisible(true);
      revalidate();
      repaint();
      closeAfter(850, afterClose);
    }

    void closeAfter(int visibleMillis, Runnable afterClose) {
      javax.swing.Timer closeDelay = new javax.swing.Timer(visibleMillis, event -> {
        ((javax.swing.Timer) event.getSource()).stop();
        dispose();
        if (afterClose != null) afterClose.run();
      });
      closeDelay.setRepeats(false);
      closeDelay.start();
    }
  }

  private final class HubPerformancePanel extends JPanel {
    private final JLabel cpuValue = metricValue();
    private final JLabel heapValue = metricValue();
    private final JLabel threadsValue = metricValue();
    private final JLabel activityValue = metricValue();
    private final JLabel engineValue = new JLabel();
    private final PerformanceGraph graph = new PerformanceGraph();
    private final JPanel header = new JPanel(new BorderLayout());
    private final JPanel metricsSafeArea = new JPanel(new BorderLayout());
    private final String revision = readGitValue(List.of("git", "rev-parse", "--short", "HEAD"), "unknown");
    private final javax.swing.Timer refreshTimer = new javax.swing.Timer(1000, event -> refreshMetrics());
    private boolean graphVisible;
    private boolean chipsVisible;

    HubPerformancePanel() {
      super(new BorderLayout(0, ui(3)));
      setBackground(PANEL_BG);
      setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(BORDER_NEUTRAL),
          uiPadding(9, 12, 10, 12)));

      engineValue.setForeground(TEXT_MUTED);
      engineValue.setFont(engineValue.getFont().deriveFont(Font.PLAIN, uiFont(9f)));
      engineValue.setHorizontalAlignment(SwingConstants.RIGHT);

      header.setOpaque(false);
      header.add(engineValue, BorderLayout.CENTER);
      add(header, BorderLayout.NORTH);

      add(graph, BorderLayout.CENTER);

      JPanel metrics = new JPanel(new GridLayout(1, 4, ui(8), 0));
      metrics.setOpaque(false);
      metrics.add(metric("CPU", cpuValue));
      metrics.add(metric("JVM heap", heapValue));
      metrics.add(metric("Threads", threadsValue));
      metrics.add(metric("Hub task", activityValue));
      metrics.setMinimumSize(new Dimension(0, ui(24)));
      metrics.setPreferredSize(new Dimension(0, ui(24)));
      metricsSafeArea.setOpaque(false);
      metricsSafeArea.setBorder(BorderFactory.createEmptyBorder(0, 0, ui(3), 0));
      metricsSafeArea.add(metrics, BorderLayout.CENTER);
      add(metricsSafeArea, BorderLayout.SOUTH);
      setPreferredSize(new Dimension(0, ui(128)));
      applyVisibility(performanceGraphVisible, performanceChipsVisible);
      refreshMetrics();
    }

    void applyVisibility(boolean showGraph, boolean showChips) {
      graphVisible = showGraph;
      chipsVisible = showChips;
      boolean monitorVisible = showGraph || showChips;
      graph.setVisible(showGraph);
      metricsSafeArea.setVisible(showChips);
      header.setVisible(monitorVisible);
      setVisible(monitorVisible);
      int preferredHeight = showGraph ? (showChips ? 128 : 98) : (showChips ? 52 : 0);
      setPreferredSize(new Dimension(0, ui(preferredHeight)));
      if (monitorVisible && isDisplayable()) {
        refreshMetrics();
        refreshTimer.start();
      } else {
        refreshTimer.stop();
      }
      revalidate();
      repaint();
      if (getParent() != null) {
        getParent().revalidate();
        getParent().repaint();
      }
    }

    @Override
    public void addNotify() {
      super.addNotify();
      refreshMetrics();
      if (graphVisible || chipsVisible) refreshTimer.start();
    }

    @Override
    public void removeNotify() {
      refreshTimer.stop();
      super.removeNotify();
    }

    private void refreshMetrics() {
      if (!graphVisible && !chipsVisible) return;
      Runtime runtime = Runtime.getRuntime();
      long heapUsed = runtime.totalMemory() - runtime.freeMemory();
      long heapMax = runtime.maxMemory();
      boolean active = runningProcess.get() != null;
      if (chipsVisible) {
        heapValue.setText(formatBytes(heapUsed) + " / " + formatBytes(heapMax));
        threadsValue.setText(Integer.toString(ManagementFactory.getThreadMXBean().getThreadCount()));
        activityValue.setText(active ? statusLabel.getText() : "Idle");
        activityValue.setForeground(active ? ACCENT_GREEN : TEXT_SOFT);
      }

      java.lang.management.OperatingSystemMXBean genericBean = ManagementFactory.getOperatingSystemMXBean();
      double cpuLoad = 0.0;
      if (genericBean instanceof com.sun.management.OperatingSystemMXBean systemBean) {
        cpuLoad = systemBean.getProcessCpuLoad();
        if (chipsVisible) {
          cpuValue.setText(cpuLoad >= 0.0 ? String.format(Locale.ROOT, "%.0f%%", cpuLoad * 100.0) : "--");
        }
      } else if (chipsVisible) {
        cpuValue.setText("--");
      }

      long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;
      String updates = lastKnownIncoming > 0
          ? lastKnownIncoming + " incoming"
          : lastKnownIncoming == 0 ? "up to date" : "updates unknown";
      engineValue.setText(resolveBranch(projectRoot) + " @ " + revision + "  ·  " + updates
          + "  ·  uptime " + formatUptime(uptimeSeconds));
      double heapRatio = heapMax > 0L ? Math.min(1.0, (double) heapUsed / heapMax) : 0.0;
      if (graphVisible) {
        graph.pushSample(Math.max(0.0, cpuLoad), heapRatio, active ? 1.0 : 0.0);
      }
    }

    private static JPanel metric(String name, JLabel value) {
      JPanel chip = new JPanel(new BorderLayout(ui(6), 0));
      chip.setBackground(PANEL_BG_TOP);
      chip.setBorder(uiPadding(2, 7, 2, 7));
      JLabel label = new JLabel(name);
      label.setForeground(TEXT_MUTED);
      label.setFont(label.getFont().deriveFont(Font.BOLD, uiFont(8f)));
      chip.add(label, BorderLayout.WEST);
      value.setHorizontalAlignment(SwingConstants.RIGHT);
      chip.add(value, BorderLayout.EAST);
      return chip;
    }

    private static JLabel metricValue() {
      JLabel value = new JLabel("--");
      value.setForeground(TEXT_SOFT);
      value.setFont(value.getFont().deriveFont(Font.BOLD, uiFont(9f)));
      return value;
    }

    private static String formatBytes(long bytes) {
      if (bytes < 0L) return "--";
      double gibibytes = bytes / (1024.0 * 1024.0 * 1024.0);
      if (gibibytes >= 1.0) return String.format(Locale.ROOT, "%.1f GB", gibibytes);
      return String.format(Locale.ROOT, "%.0f MB", bytes / (1024.0 * 1024.0));
    }

    private static String formatUptime(long seconds) {
      long hours = seconds / 3600L;
      long minutes = (seconds % 3600L) / 60L;
      long remainder = seconds % 60L;
      return hours > 0L
          ? String.format(Locale.ROOT, "%dh %02dm", hours, minutes)
          : String.format(Locale.ROOT, "%dm %02ds", minutes, remainder);
    }

    private static final class PerformanceGraph extends JComponent {
      private static final int SAMPLE_COUNT = 120;
      private final double[] cpu = new double[SAMPLE_COUNT];
      private final double[] heap = new double[SAMPLE_COUNT];
      private final double[] activity = new double[SAMPLE_COUNT];
      private int index;
      private boolean filled;

      PerformanceGraph() {
        setOpaque(true);
        setBackground(Color.decode("#121212"));
        setPreferredSize(new Dimension(0, ui(48)));
      }

      void pushSample(double cpuRatio, double heapRatio, double activityRatio) {
        int slot = index % SAMPLE_COUNT;
        cpu[slot] = clampRatio(cpuRatio);
        heap[slot] = clampRatio(heapRatio);
        activity[slot] = clampRatio(activityRatio);
        index++;
        if (index >= SAMPLE_COUNT) filled = true;
        repaint();
      }

      @Override
      protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
          g2.setColor(getBackground());
          g2.fillRect(0, 0, getWidth(), getHeight());
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          int width = getWidth();
          int height = getHeight();
          g2.setColor(Color.decode("#242424"));
          for (int row = 1; row < 4; row++) {
            int y = height * row / 4;
            g2.drawLine(0, y, width, y);
          }
          for (int column = 1; column < 6; column++) {
            int x = width * column / 6;
            g2.drawLine(x, 0, x, height);
          }

          int samples = filled ? SAMPLE_COUNT : Math.min(index, SAMPLE_COUNT);
          if (samples <= 1) return;
          double scaleX = width / (double) (SAMPLE_COUNT - 1);
          Path2D heapArea = metricPath(heap, samples, scaleX, height, true);
          g2.setColor(new Color(168, 85, 247, 56));
          g2.fill(heapArea);
          drawMetric(g2, heap, samples, scaleX, height, new Color(192, 132, 252), uiStroke(1.5f));
          drawMetric(g2, cpu, samples, scaleX, height, Color.decode("#f27333"), uiStroke(1.8f));
          drawMetric(g2, activity, samples, scaleX, height, Color.decode("#f4f4f4"), uiStroke(1.5f));
        } finally {
          g2.dispose();
        }
      }

      private Path2D metricPath(double[] values, int samples, double scaleX, int height, boolean close) {
        Path2D path = new Path2D.Double();
        if (close) path.moveTo(0, height);
        for (int i = 0; i < samples; i++) {
          int slot = (index - samples + i + SAMPLE_COUNT) % SAMPLE_COUNT;
          double x = i * scaleX;
          double y = height * (1.0 - values[slot]);
          if (i == 0 && !close) path.moveTo(x, y); else path.lineTo(x, y);
        }
        if (close) {
          path.lineTo((samples - 1) * scaleX, height);
          path.closePath();
        }
        return path;
      }

      private void drawMetric(
          Graphics2D graphics, double[] values, int samples, double scaleX, int height, Color color, float width) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(metricPath(values, samples, scaleX, height, false));
      }

      private static double clampRatio(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
      }
    }
  }

}
