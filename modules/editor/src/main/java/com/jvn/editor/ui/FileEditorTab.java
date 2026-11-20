package com.jvn.editor.ui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import com.jvn.editor.hotreload.HotReloadClient;
import com.jvn.core.animation.SceneAccessor;
import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineDataParser;
import com.jvn.core.animation.TimelineRunner;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.Label2D;
import com.jvn.core.scene2d.Panel2D;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnErrorOverlay;
import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.scripting.jes.JesLoader;
import com.jvn.scripting.jes.JesParseException;
import com.jvn.scripting.jes.runtime.JesScene2D;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class FileEditorTab extends BorderPane {
  public enum Kind { JES, VNS, JAVA, TIMELINE, THEME, MENU_SCREEN, MENU_LAYOUT, MENU_STYLE, DIALOGUE_LAYOUT, OTHER }
  private enum PreviewDockPosition { TOP, BOTTOM, LEFT, RIGHT, WINDOW }
  private enum PreviewLayoutMode { PREVIEW, CODE, SPLIT }
  private static final String[] TEXT_EDITABLE_EXTENSIONS = new String[] {
      ".jes", ".txt", ".vns", ".java", ".timeline", ".theme", ".menu", ".layout", ".style", ".registry",
      ".settings", ".project", ".properties", ".md", ".json",
      ".yaml", ".yml", ".toml", ".ini", ".cfg", ".xml", ".csv", ".tsv"
  };

  private final File file;
  private final Kind kind;

  private final JesCodeEditor jesEditor;
  private final VnsCodeEditor vnsEditor;
  private final JavaCodeEditor javaEditor;
  private final JavaCodeEditor textEditor;
  private final TimelineCodeEditor timelineEditor;
  private final JavaCodeEditor themeEditor;
  private final JavaCodeEditor menuScreenEditor;
  private final JavaCodeEditor menuLayoutEditor;
  private final JavaCodeEditor menuStyleEditor;
  private final JavaCodeEditor dialogueLayoutEditor;
  private final StoryTimelineView timelineView;

  private final ViewportView viewport; // JES preview
  private JesScene2D jesScene;

  private final VnPreviewView vnPreview; // VNS preview

  private Consumer<Entity2D> onSelected;
  private Consumer<String> onStatus;
  private Consumer<String> onVnsTextChanged;
  private Consumer<String> onDiagnosticsTextChanged;
  private Runnable onOpenDiagnostics;
  private Consumer<StoryboardOverlayState> onStoryboardOverlayAdjusted;
  private com.jvn.editor.commands.CommandStack commands;
  private File projectRoot;
  private VnsFoldStateStore foldStateStore;
  private String savedSnapshot = "";
  private double lastSizedWidth = -1;
  private double lastSizedHeight = -1;
  private SplitPane primarySplit;
  private int primarySplitCodeIndex = 1;
  private boolean editorFullscreen;
  private double restoreDividerPosition = 0.6;
  private BorderPane previewWorkspaceContent;
  private Node dockPreviewNode;
  private CompositionGuideOverlay compositionGuideOverlay;
  private Node dockEditorNode;
  private ToggleButton previewModePreviewButton;
  private ToggleButton previewModeCodeButton;
  private ToggleButton previewModeSplitButton;
  private ToggleButton vnsWordWrapButton;
  private MenuButton previewDockMenu;
  private PreviewDockPosition previewDockPosition = PreviewDockPosition.TOP;
  private PreviewDockPosition lastEmbeddedPreviewDock = PreviewDockPosition.TOP;
  private Stage detachedPreviewStage;
  private AnimationTimer detachedPreviewTimer;
  private PreviewFramePacer detachedPreviewPacer;
  private double verticalDockDivider = 0.6;
  private double horizontalDockDivider = 0.5;
  private PreviewLayoutMode previewModeBeforeDetach = PreviewLayoutMode.SPLIT;
  private boolean disposed;

  public FileEditorTab(File file) {
    this.file = file;
    Kind detected = detectKind(file);
    this.kind = detected != null ? detected : Kind.OTHER;

    this.jesEditor = (kind == Kind.JES) ? new JesCodeEditor() : null;
    this.vnsEditor = (kind == Kind.VNS) ? new VnsCodeEditor() : null;
    this.javaEditor = (kind == Kind.JAVA) ? new JavaCodeEditor() : null;
    this.textEditor = (kind == Kind.OTHER) ? new JavaCodeEditor() : null;
    this.timelineEditor = (kind == Kind.TIMELINE) ? new TimelineCodeEditor() : null;
    this.themeEditor = (kind == Kind.THEME) ? newDslEditor() : null;
    this.menuScreenEditor = (kind == Kind.MENU_SCREEN) ? newDslEditor() : null;
    this.menuLayoutEditor = (kind == Kind.MENU_LAYOUT) ? newDslEditor() : null;
    this.menuStyleEditor = (kind == Kind.MENU_STYLE) ? newDslEditor() : null;
    this.dialogueLayoutEditor = (kind == Kind.DIALOGUE_LAYOUT) ? newDslEditor() : null;
    this.timelineView = (kind == Kind.TIMELINE) ? new StoryTimelineView() : null;
    this.viewport = (kind == Kind.JES) ? new ViewportView() : null;
    this.vnPreview = (kind == Kind.VNS) ? new VnPreviewView() : null;

    if (viewport != null) {
      viewport.setOnSelected(e -> { if (onSelected != null) onSelected.accept(e); });
      viewport.setOnStatus(s -> { if (onStatus != null) onStatus.accept(s); });
      viewport.setOnHotReloadRequested(this::hotReloadFromPreview);
      viewport.setOnStoryboardStateAdjusted(state -> {
        if (onStoryboardOverlayAdjusted != null) {
          onStoryboardOverlayAdjusted.accept(state);
        }
      });
    }
    
    if (vnsEditor != null) {
      vnsEditor.setOnLaunchFromHere(this::runFromLabel);
      vnsEditor.setOnLaunchFromCursor(this::runFromSourceLine);
      vnsEditor.setOnStoryboardLineRequested(this::syncStoryboardPreviewToLine);
      vnsEditor.setOnFoldStateChanged(this::saveTimelineFoldState);
    }
    if (vnPreview != null) {
      vnPreview.setOnHotReloadRequested(this::hotReloadFromPreview);
      vnPreview.setOnStoryboardPreviewLineChanged(this::syncStoryboardEditorCursor);
      vnPreview.setOnStoryboardStateAdjusted(state -> {
        if (onStoryboardOverlayAdjusted != null) {
          onStoryboardOverlayAdjusted.accept(state);
        }
      });
    }

    setupLayout();

    if (file != null && file.exists()) reloadFromDisk();
    else savedSnapshot = getCurrentText();

    // Key forwarding for JES viewport camera controls
    addEventFilter(KeyEvent.KEY_PRESSED, e -> {
      if (viewport != null) viewport.getInput().keyDown(mapKey(e.getCode()));
    });
    addEventFilter(KeyEvent.KEY_RELEASED, e -> {
      if (viewport != null) viewport.getInput().keyUp(mapKey(e.getCode()));
    });

    // Timeline sync between code and graph
    if (kind == Kind.TIMELINE && timelineEditor != null && timelineView != null) {
      if (file != null) timelineView.setTimelineFile(file);
      timelineEditor.setOnTextChanged(text -> {
        timelineView.fromText(text);
        notifyDiagnosticsTextChanged(text);
      });
      timelineView.setOnChanged(() -> {
        String text = timelineView.toDsl();
        timelineEditor.setTextNoEvent(text);
        notifyDiagnosticsTextChanged(text);
      });
    }
  }

  public static boolean supportsTextEditing(File file) {
    if (file == null) return false;
    String name = file.getName().toLowerCase(Locale.ROOT);
    if ("dialogue.layout".equals(name) || "menu.theme".equals(name)) return true;
    for (String extension : TEXT_EDITABLE_EXTENSIONS) {
      if (name.endsWith(extension)) return true;
    }
    return false;
  }

  public static Kind detectKind(File file) {
    if (file == null || !supportsTextEditing(file)) return null;
    String name = file.getName().toLowerCase(Locale.ROOT);
    String path = file.getPath().replace('\\', '/').toLowerCase(Locale.ROOT);
    if (name.endsWith(".jes") || name.endsWith(".txt")) return Kind.JES;
    if (name.endsWith(".vns")) return Kind.VNS;
    if (name.endsWith(".java")) return Kind.JAVA;
    if (name.endsWith(".storymap") || name.endsWith(".timeline")) return Kind.TIMELINE;
    if (name.endsWith(".theme") || "menu.theme".equals(name)) return Kind.THEME;
    if (name.endsWith(".menu")) return Kind.MENU_SCREEN;
    if (name.endsWith(".layout") && (path.contains("/config/menu/layouts/") || path.contains("/menu/layouts/") || path.contains("/config/menu/"))) {
      return Kind.MENU_LAYOUT;
    }
    if (name.endsWith(".style") || path.contains("/config/menu/styles/")) return Kind.MENU_STYLE;
    if ("dialogue.layout".equals(name) || (name.endsWith(".layout") && (path.contains("/config/ui/") || path.contains("/config/vn/")))) {
      return Kind.DIALOGUE_LAYOUT;
    }
    return Kind.OTHER;
  }

  public void showSearchBar() {
    if (vnsEditor != null) vnsEditor.showSearchBar();
    else if (javaEditor != null) javaEditor.showSearchBar();
    else if (themeEditor != null) themeEditor.showSearchBar();
    else if (menuScreenEditor != null) menuScreenEditor.showSearchBar();
    else if (menuLayoutEditor != null) menuLayoutEditor.showSearchBar();
    else if (menuStyleEditor != null) menuStyleEditor.showSearchBar();
    else if (dialogueLayoutEditor != null) dialogueLayoutEditor.showSearchBar();
    else if (textEditor != null) textEditor.showSearchBar();
  }

  public void insertVnsSnippet(String snippet) {
    if (kind != Kind.VNS || vnsEditor == null || snippet == null) return;
    vnsEditor.insertSnippet(snippet);
  }

  public void setCodeEditorFontSize(double fontSizePx) {
    if (vnsEditor != null) vnsEditor.setFontSizePx(fontSizePx);
    if (jesEditor != null) jesEditor.setFontSizePx(fontSizePx);
    if (javaEditor != null) javaEditor.setFontSizePx(fontSizePx);
    if (textEditor != null) textEditor.setFontSizePx(fontSizePx);
    if (timelineEditor != null) timelineEditor.setFontSizePx(fontSizePx);
    if (themeEditor != null) themeEditor.setFontSizePx(fontSizePx);
    if (menuScreenEditor != null) menuScreenEditor.setFontSizePx(fontSizePx);
    if (menuLayoutEditor != null) menuLayoutEditor.setFontSizePx(fontSizePx);
    if (menuStyleEditor != null) menuStyleEditor.setFontSizePx(fontSizePx);
    if (dialogueLayoutEditor != null) dialogueLayoutEditor.setFontSizePx(fontSizePx);
  }

  public void setVnsAuthoringPreferences(
      boolean wordWrapEnabled, boolean minimapVisible, int largeTimelineBlockActionThreshold) {
    if (vnsEditor == null) return;
    vnsEditor.setWordWrapEnabled(wordWrapEnabled);
    vnsEditor.setMinimapVisible(minimapVisible);
    vnsEditor.setLargeTimelineBlockActionThreshold(largeTimelineBlockActionThreshold);
    if (vnsWordWrapButton != null) vnsWordWrapButton.setSelected(wordWrapEnabled);
  }

  public void launchFromHere() {
    if (kind != Kind.VNS || vnsEditor == null) return;
    vnsEditor.launchFromCurrentLabel();
  }

  public void launchFromCursor() {
    if (kind != Kind.VNS || vnsEditor == null) return;
    vnsEditor.launchFromCursor();
  }

  public void runFromSourceLine(int oneBasedLine) {
    try {
      if (kind != Kind.VNS || vnsEditor == null || vnPreview == null) return;
      String code = vnsEditor.getText();
      if (code == null || code.isBlank()) {
        if (onStatus != null) onStatus.accept("Cannot run an empty VNS script");
        return;
      }
      VnScenario scenario = parseVnsScenarioFromText(code);
      vnPreview.setSourceScriptName(resolveVnsScriptKey());
      vnPreview.runScenarioFromSourceLine(scenario, Math.max(1, oneBasedLine));
      setPreviewDockPosition(PreviewDockPosition.WINDOW);
      if (onStatus != null) onStatus.accept("Run from cursor: line " + Math.max(1, oneBasedLine));
    } catch (Exception ex) {
      showVnsParseOverlay(ex);
      setPreviewDockPosition(PreviewDockPosition.WINDOW);
      if (onStatus != null) onStatus.accept("VNS launch failed: " + ex.getMessage());
    }
  }

  int getVnsPreviewSourceLine() {
    return vnPreview == null ? -1 : vnPreview.getCurrentSourceLine();
  }

  public void runFromLabel(String label) {
    try {
      if (kind != Kind.VNS || vnsEditor == null || vnPreview == null) return;
      String code = vnsEditor.getText();
      if (code == null || code.isBlank()) {
        if (onStatus != null) onStatus.accept("Cannot run an empty VNS script");
        return;
      }
      VnScenario scenario = parseVnsScenarioFromText(code);
      vnPreview.setSourceScriptName(resolveVnsScriptKey());
      vnPreview.runScenario(scenario, label);
      setPreviewDockPosition(PreviewDockPosition.WINDOW);
      if (onStatus != null) onStatus.accept("Run from label: " + (label == null ? "<start>" : label));
    } catch (Exception ex) {
      showVnsParseOverlay(ex);
      setPreviewDockPosition(PreviewDockPosition.WINDOW);
      if (onStatus != null) onStatus.accept("VNS launch failed: " + ex.getMessage());
    }
  }

  public void hotReloadFromPreview() {
    try {
      if (kind == Kind.JES && jesEditor != null) {
        saveCurrentFileIfPossible();
        applyJesPreviewFromCode(jesEditor.getText());
        if (onStatus != null) onStatus.accept("Hot reloaded JES preview");
        return;
      }
      if (kind == Kind.VNS && vnsEditor != null && vnPreview != null) {
        saveCurrentFileIfPossible();
        String code = vnsEditor.getText();
        if (code == null || code.isBlank()) return;
        VnScenario scenario = parseVnsScenarioFromText(code);
        vnPreview.setSourceScriptName(resolveVnsScriptKey());
        vnPreview.reloadScenarioPreservingPosition(scenario);
        HotReloadClient.sendReload(resolveVnsScriptKey());
        if (onStatus != null) onStatus.accept("Hot reloaded VNS preview");
      }
    } catch (Exception ex) {
      if (kind == Kind.VNS) {
        showVnsParseOverlay(ex);
      } else if (viewport != null) {
        viewport.setActiveError(VnErrorOverlay.dslRuntimeError("JES", sourceNameForOverlay(), -1, ex.getMessage(), ex));
      }
      if (onStatus != null) onStatus.accept("Hot reload failed: " + ex.getMessage());
    }
  }

  private void saveCurrentFileIfPossible() {
    if (file == null) return;
    saveTo(file);
  }

  private VnScenario parseVnsScenarioFromText(String code) throws IOException {
    VnScriptParser parser = new VnScriptParser();
    byte[] bytes = code == null ? new byte[0] : code.getBytes(StandardCharsets.UTF_8);
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      return parser.parse(in, resolveVnsSourceName(), this::openVnsInclude);
    }
  }

  private String resolveVnsSourceName() {
    String scriptKey = resolveVnsScriptKey();
    if (scriptKey != null && !scriptKey.isBlank()) return scriptKey;
    return file != null ? file.getName() : "<editor>";
  }

  private String resolveVnsScriptKey() {
    if (file == null) return null;
    Path filePath = file.toPath().toAbsolutePath().normalize();
    Path scriptsRoot = resolveScriptsRootForVnsFile();
    if (scriptsRoot != null && filePath.startsWith(scriptsRoot)) {
      return scriptsRoot.relativize(filePath).toString().replace('\\', '/');
    }
    return file.getName();
  }

  private void restoreTimelineFoldState() {
    if (kind != Kind.VNS || vnsEditor == null || foldStateStore == null) return;
    String scriptKey = resolveVnsScriptKey();
    if (scriptKey == null) return;
    vnsEditor.restoreFoldedTimelineBlocks(foldStateStore.getFoldedBlocks(scriptKey));
  }

  private void saveTimelineFoldState() {
    if (kind != Kind.VNS || vnsEditor == null || foldStateStore == null) return;
    String scriptKey = resolveVnsScriptKey();
    if (scriptKey == null) return;
    foldStateStore.setFoldedBlocks(scriptKey, vnsEditor.exportFoldedTimelineBlocks());
    foldStateStore.save();
  }

  private InputStream openVnsInclude(String includePath) throws IOException {
    String normalized = includePath == null ? "" : includePath.trim().replace('\\', '/');
    if (normalized.isBlank()) {
      throw new IOException("Include path is empty");
    }

    Path scriptsRoot = resolveScriptsRootForVnsFile();
    if (scriptsRoot == null) {
      throw new IOException("Include resolver unavailable for " + includePath);
    }

    Path sourcePath = file == null ? null : file.toPath().toAbsolutePath().normalize();
    Path sourceParent = sourcePath == null ? scriptsRoot : sourcePath.getParent();
    if (sourceParent == null) sourceParent = scriptsRoot;
    Path workspaceRoot = resolveWorkspaceRootForVnsFile();
    if (workspaceRoot == null) workspaceRoot = scriptsRoot;

    Path resolved = normalized.startsWith("/")
        ? scriptsRoot.resolve(normalized.substring(1)).normalize()
        : sourceParent.resolve(normalized).normalize();
    if (!resolved.startsWith(workspaceRoot)) {
      throw new IOException("Include path escapes project root: " + includePath);
    }
    return Files.newInputStream(resolved);
  }

  private Path resolveScriptsRootForVnsFile() {
    if (projectRoot != null) {
      Path root = projectRoot.toPath().toAbsolutePath().normalize();
      Path scripts = root.resolve("scripts").normalize();
      if (Files.isDirectory(scripts)) return scripts;
      return root;
    }
    if (file == null) return null;
    Path current = file.toPath().toAbsolutePath().normalize().getParent();
    while (current != null) {
      Path name = current.getFileName();
      if (name != null && "scripts".equalsIgnoreCase(name.toString())) {
        return current;
      }
      current = current.getParent();
    }
    return file.getParentFile() == null ? null : file.getParentFile().toPath().toAbsolutePath().normalize();
  }

  private Path resolveWorkspaceRootForVnsFile() {
    if (projectRoot != null) return projectRoot.toPath().toAbsolutePath().normalize();
    Path scriptsRoot = resolveScriptsRootForVnsFile();
    if (scriptsRoot != null && scriptsRoot.getParent() != null) {
      return scriptsRoot.getParent().toAbsolutePath().normalize();
    }
    return scriptsRoot;
  }

  private void setupLayout() {
    if (kind == Kind.JES) {
      setCenter(createPreviewWorkspace("JES Preview", viewport, jesEditor, 0.6));
    } else if (kind == Kind.VNS) {
      setCenter(createPreviewWorkspace("VNS Preview", vnPreview, vnsEditor, 0.6));
    } else if (kind == Kind.JAVA) {
      setCenter(javaEditor);
    } else if (kind == Kind.TIMELINE) {
      setCenter(createPreviewWorkspace("Timeline Preview", timelineView, timelineEditor, 0.6));
    } else if (kind == Kind.THEME) {
      setCenter(themeEditor);
    } else if (kind == Kind.MENU_SCREEN) {
      setCenter(menuScreenEditor);
    } else if (kind == Kind.MENU_LAYOUT) {
      setCenter(menuLayoutEditor);
    } else if (kind == Kind.MENU_STYLE) {
      setCenter(menuStyleEditor);
    } else if (kind == Kind.DIALOGUE_LAYOUT) {
      setCenter(dialogueLayoutEditor);
    } else if (kind == Kind.OTHER) {
      setCenter(textEditor);
    } else {
      setCenter(new javafx.scene.control.Label("Unsupported file type"));
    }
  }

  public Kind getKind() { return kind; }
  public File getFile() { return file; }

  public void setStoryMapActions(Consumer<StoryTimelineView.Arc> open, Consumer<StoryTimelineView.Arc> run) {
    if (timelineView == null) return;
    timelineView.setOnOpenArc(open);
    timelineView.setOnRunArc(run);
    timelineView.setOnRunLink(link -> {
      var target = timelineView.findArc(link.toArc);
      if (target != null) open.accept(target);
    });
  }

  public void setOnSelected(Consumer<Entity2D> c) { this.onSelected = c; }
  public void setOnStatus(Consumer<String> c) { this.onStatus = c; }
  public void setOnStoryboardOverlayAdjusted(Consumer<StoryboardOverlayState> c) { this.onStoryboardOverlayAdjusted = c; }
  public void setProjectRoot(File root) {
    this.projectRoot = root;
    this.foldStateStore = VnsFoldStateStore.load(root);
    restoreTimelineFoldState();
    if (jesEditor != null) jesEditor.setProjectRoot(root);
    if (vnsEditor != null) vnsEditor.setProjectRoot(root);
    if (timelineEditor != null) timelineEditor.setProjectRoot(root);
    if (timelineView != null) timelineView.setProjectRoot(root);
    if (viewport != null) viewport.setProjectRoot(root);
    if (vnPreview != null) vnPreview.setProjectRoot(root);
    if (compositionGuideOverlay != null) {
      ProjectViewportSpec.Dimensions dimensions = ProjectViewportSpec.resolve(root);
      compositionGuideOverlay.setVirtualResolution(dimensions.width(), dimensions.height());
    }

    if (kind == Kind.VNS && vnsEditor != null && vnPreview != null) {
      String code = vnsEditor.getText();
      if (code != null && !code.isBlank()) {
        try {
          VnScenario scenario = parseVnsScenarioFromText(code);
          vnPreview.setSourceScriptName(resolveVnsScriptKey());
          vnPreview.setScenario(scenario);
        } catch (Exception ex) {
          showVnsParseOverlay(ex);
          if (onStatus != null) onStatus.accept("VNS parse warning: " + ex.getMessage());
        }
      }
    }
  }

  public void setCommandStack(com.jvn.editor.commands.CommandStack cs) {
    this.commands = cs;
    if (viewport != null) viewport.setCommandStack(cs);
  }

  public void render(long dt) {
    if (isDetachedPreviewVisible()) return;
    if (kind == Kind.JES && viewport != null) {
      viewport.render(dt);
    }
  }

  public void setSize(double w, double h) {
    double safeW = sanitizeDimension(w);
    double safeH = sanitizeDimension(h);
    if (Math.abs(lastSizedWidth - safeW) < 0.5 && Math.abs(lastSizedHeight - safeH) < 0.5) return;
    lastSizedWidth = safeW;
    lastSizedHeight = safeH;
    applyPreviewSizing(safeW, safeH);
  }

  public void apply() throws Exception {
    if (kind == Kind.JES) {
      String code = jesEditor.getText();
      if (code == null || code.isBlank()) return;
      applyJesPreviewFromCode(code);
    } else if (kind == Kind.VNS) {
      String code = vnsEditor.getText();
      if (code == null || code.isBlank()) return;
      try {
        VnScenario scenario = parseVnsScenarioFromText(code);
        if (vnPreview != null) {
          vnPreview.setSourceScriptName(resolveVnsScriptKey());
          vnPreview.setScenario(scenario);
        }
      } catch (Exception ex) {
        showVnsParseOverlay(ex);
        if (onStatus != null) onStatus.accept("VNS error: " + ex.getMessage());
      }
    }
  }

  public void reloadFromDisk() {
    if (file == null) return;
    try {
      boolean suppressReloadStatus = false;
      if (kind == Kind.JES) {
        String code = Files.readString(file.toPath());
        jesEditor.setText(code);
        if (!applyJesPreviewFromCode(code)) suppressReloadStatus = true;
      } else if (kind == Kind.VNS) {
        String code = Files.readString(file.toPath());
        vnsEditor.setText(code);
        restoreTimelineFoldState();
        try {
          VnScenario scenario = parseVnsScenarioFromText(code);
          if (vnPreview != null) {
            vnPreview.setSourceScriptName(resolveVnsScriptKey());
            vnPreview.setScenario(scenario);
          }
        } catch (Exception ex) {
          showVnsParseOverlay(ex);
          if (onStatus != null) onStatus.accept("VNS parse warning: " + ex.getMessage());
        }
      } else if (kind == Kind.TIMELINE) {
        String text = Files.exists(file.toPath()) ? Files.readString(file.toPath()) : "";
        if (timelineEditor != null) timelineEditor.setText(text);
        if (timelineView != null) timelineView.fromText(text);
      } else if (kind == Kind.THEME) {
        String text = Files.exists(file.toPath()) ? Files.readString(file.toPath()) : "";
        if (themeEditor != null) themeEditor.setText(text);
      } else if (kind == Kind.MENU_SCREEN) {
        String text = Files.exists(file.toPath()) ? Files.readString(file.toPath()) : "";
        if (menuScreenEditor != null) menuScreenEditor.setText(text);
      } else if (kind == Kind.MENU_LAYOUT) {
        String text = Files.exists(file.toPath()) ? Files.readString(file.toPath()) : "";
        if (menuLayoutEditor != null) menuLayoutEditor.setText(text);
      } else if (kind == Kind.MENU_STYLE) {
        String text = Files.exists(file.toPath()) ? Files.readString(file.toPath()) : "";
        if (menuStyleEditor != null) menuStyleEditor.setText(text);
      } else if (kind == Kind.DIALOGUE_LAYOUT) {
        String text = Files.exists(file.toPath()) ? Files.readString(file.toPath()) : "";
        if (dialogueLayoutEditor != null) dialogueLayoutEditor.setText(text);
      } else if (kind == Kind.JAVA) {
        String code = Files.readString(file.toPath());
        javaEditor.setText(code);
      } else if (kind == Kind.OTHER && textEditor != null) {
        String code = Files.readString(file.toPath());
        textEditor.setText(code);
      }
      savedSnapshot = getCurrentText();
      if (!suppressReloadStatus && onStatus != null) onStatus.accept("Reloaded: " + file.getName());
    } catch (Exception ex) {
      if (onStatus != null) onStatus.accept("Reload failed: " + ex.getMessage());
    }
  }

  public void saveTo(File target) {
    try {
      if (kind == Kind.JES) {
        String content = jesEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.VNS) {
        String content = vnsEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.TIMELINE) {
        String content = timelineEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.THEME) {
        String content = themeEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.MENU_SCREEN && menuScreenEditor != null) {
        String content = menuScreenEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.MENU_LAYOUT && menuLayoutEditor != null) {
        String content = menuLayoutEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.MENU_STYLE && menuStyleEditor != null) {
        String content = menuStyleEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.DIALOGUE_LAYOUT && dialogueLayoutEditor != null) {
        String content = dialogueLayoutEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.JAVA) {
        String content = javaEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.OTHER && textEditor != null) {
        String content = textEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      }
      savedSnapshot = getCurrentText();
      if (onStatus != null) onStatus.accept("Saved: " + target.getName());
      if (kind == Kind.VNS) {
        HotReloadClient.sendReload(resolveVnsScriptKey());
      }
    } catch (Exception ex) {
      if (onStatus != null) onStatus.accept("Save failed");
    }
  }

  public boolean isDirty() {
    return !Objects.equals(savedSnapshot, getCurrentText());
  }

  public String getDisplayName() {
    return file != null ? file.getName() : "Untitled";
  }

  public void setOnVnsTextChanged(Consumer<String> listener) {
    this.onVnsTextChanged = listener;
    installVnsTextChangedHandler();
  }

  public void setOnDiagnosticsTextChanged(Consumer<String> listener) {
    this.onDiagnosticsTextChanged = listener;
    if (kind == Kind.VNS) {
      installVnsTextChangedHandler();
    } else if (kind == Kind.JES && jesEditor != null) {
      jesEditor.setOnTextChanged(this::notifyDiagnosticsTextChanged);
    } else if (kind == Kind.JAVA && javaEditor != null) {
      javaEditor.setOnTextChanged(this::notifyDiagnosticsTextChanged);
    } else if (kind == Kind.OTHER && textEditor != null) {
      textEditor.setOnTextChanged(this::notifyDiagnosticsTextChanged);
    }
  }

  public void setOnOpenDiagnostics(Runnable listener) {
    this.onOpenDiagnostics = listener;
  }

  private void installVnsTextChangedHandler() {
    if (vnsEditor == null) return;
    vnsEditor.setOnTextChanged(text -> {
      if (onVnsTextChanged != null) onVnsTextChanged.accept(text);
      notifyDiagnosticsTextChanged(text);
    });
  }

  private void notifyDiagnosticsTextChanged(String text) {
    if (onDiagnosticsTextChanged != null) onDiagnosticsTextChanged.accept(text);
  }

  public void setOnVnsCaretLineChanged(Consumer<Integer> listener) {
    if (vnsEditor != null) vnsEditor.setOnCaretLineChanged(listener);
  }

  public int getVnsCaretLine() {
    return vnsEditor != null ? vnsEditor.getCurrentLine() : -1;
  }

  /** Returns the underlying {@link VnsCodeEditor} for a VNS-kind tab, or {@code null} otherwise. */
  public VnsCodeEditor getVnsEditor() {
    return kind == Kind.VNS ? vnsEditor : null;
  }

  public String getCurrentTextSnapshot() {
    return getCurrentText();
  }

  public void navigateToLine(int oneBasedLine) {
    if (kind == Kind.VNS && vnsEditor != null) vnsEditor.goToLine(oneBasedLine);
    else if (kind == Kind.JES && jesEditor != null) jesEditor.goToLine(oneBasedLine);
    else if (kind == Kind.JAVA && javaEditor != null) javaEditor.goToLine(oneBasedLine);
    else if (kind == Kind.TIMELINE && timelineEditor != null) timelineEditor.goToLine(oneBasedLine);
    else if (kind == Kind.THEME && themeEditor != null) themeEditor.goToLine(oneBasedLine);
    else if (kind == Kind.MENU_SCREEN && menuScreenEditor != null) menuScreenEditor.goToLine(oneBasedLine);
    else if (kind == Kind.MENU_LAYOUT && menuLayoutEditor != null) menuLayoutEditor.goToLine(oneBasedLine);
    else if (kind == Kind.MENU_STYLE && menuStyleEditor != null) menuStyleEditor.goToLine(oneBasedLine);
    else if (kind == Kind.DIALOGUE_LAYOUT && dialogueLayoutEditor != null) dialogueLayoutEditor.goToLine(oneBasedLine);
    else if (kind == Kind.OTHER && textEditor != null) textEditor.goToLine(oneBasedLine);
  }

  public void navigateToOffset(int offset) {
    if (kind == Kind.VNS && vnsEditor != null) {
      vnsEditor.goToOffset(offset);
    }
  }

  public void navigateToRange(int startOffset, int endOffset) {
    if (kind == Kind.VNS && vnsEditor != null) {
      vnsEditor.goToRange(startOffset, endOffset);
    }
  }

  public JesScene2D getJesScene() { return jesScene; }
  public ViewportView getViewport() { return viewport; }
  public VnPreviewView getVnPreview() { return vnPreview; }
  public void setStoryboardOverlay(StoryboardOverlayState storyboardOverlay) {
    if (viewport != null) viewport.setStoryboardOverlay(storyboardOverlay);
    if (vnPreview != null) {
      vnPreview.setStoryboardOverlay(storyboardOverlay);
      if (vnsEditor != null) {
        boolean active = storyboardOverlay != null && storyboardOverlay.enabled() && storyboardOverlay.hasImage();
        vnsEditor.setStoryboardModeActive(active);
        if (active && vnsEditor.getStoryboardCursorLine() <= 0) {
          int line = vnPreview.getStoryboardPreviewLine();
          if (line <= 0) line = Math.max(1, vnsEditor.getCurrentLine() + 1);
          syncStoryboardPreviewToLine(line);
        }
      }
    }
  }

  private void syncStoryboardPreviewToLine(int oneBasedLine) {
    if (kind != Kind.VNS || vnsEditor == null || vnPreview == null) return;
    int targetLine = Math.max(1, oneBasedLine);
    vnsEditor.setStoryboardCursorLine(targetLine);
    try {
      vnPreview.navigateToStoryboardLine(getCurrentText(), resolveVnsSourceName(), targetLine);
    } catch (Exception ex) {
      if (onStatus != null) onStatus.accept("Storyboard sync failed: " + ex.getMessage());
    }
  }

  private void syncStoryboardEditorCursor(int oneBasedLine) {
    if (kind != Kind.VNS || vnsEditor == null) return;
    if (oneBasedLine <= 0) return;
    vnsEditor.setStoryboardCursorLine(oneBasedLine);
    vnsEditor.scrollToLineIfNeeded(oneBasedLine);
  }
  public void stopPreviewAudio() {
    if (vnPreview != null) vnPreview.stopAudio();
  }
  public void trimPreviewMemoryCaches() {
    if (vnPreview != null) vnPreview.trimMemoryCaches();
    if (viewport != null) viewport.trimMemoryCaches();
  }
  public void dispose() {
    if (disposed) return;
    disposed = true;
    saveTimelineFoldState();
    closeDetachedPreviewWindow(true);
    stopPreviewAudio();
    if (vnPreview != null) vnPreview.dispose();
    if (viewport != null) viewport.dispose();
  }
  public Node getEditorNode() {
    if (kind == Kind.JES) return jesEditor;
    if (kind == Kind.VNS) return vnsEditor;
    if (kind == Kind.JAVA) return javaEditor;
    if (kind == Kind.TIMELINE) return timelineEditor;
    if (kind == Kind.MENU_SCREEN) return menuScreenEditor;
    if (kind == Kind.MENU_LAYOUT) return menuLayoutEditor;
    if (kind == Kind.MENU_STYLE) return menuStyleEditor;
    if (kind == Kind.DIALOGUE_LAYOUT) return dialogueLayoutEditor;
    if (kind == Kind.OTHER) return textEditor;
    return null;
  }

  public void setShowGrid(boolean b) { if (viewport != null) viewport.setShowGrid(b); }
  public void fitToContent() { if (viewport != null) viewport.fitToContent(); }
  public void focusEditor() { Node ed = getEditorNode(); if (ed != null) ed.requestFocus(); }
  public boolean supportsEditorFullscreenToggle() { return primarySplit != null; }
  public boolean isEditorFullscreen() { return editorFullscreen; }

  private boolean applyJesPreviewFromCode(String code) {
    if (viewport == null) return false;
    if (code == null || code.isBlank()) {
      jesScene = null;
      viewport.clearActiveError();
      viewport.setBeforeSceneUpdateHook(null);
      viewport.setScene(null);
      return false;
    }
    try {
      JesScene2D scene = JesLoader.load(code);
      if (scene == null) return false;
      viewport.clearActiveError();
      bindJesScene(scene);
      return true;
    } catch (JesParseException ex) {
      boolean timelineLoaded = tryLoadTimelinePreview(code);
      if (!timelineLoaded) {
        viewport.setActiveError(VnErrorOverlay.jesParseError(sourceNameForOverlay(), code, ex));
        if (onStatus != null) onStatus.accept("JES error: " + ex.getMessage());
      }
      return timelineLoaded;
    } catch (Exception ex) {
      boolean timelineLoaded = tryLoadTimelinePreview(code);
      if (!timelineLoaded) {
        viewport.setActiveError(VnErrorOverlay.dslRuntimeError("JES", sourceNameForOverlay(), -1, ex.getMessage(), ex));
        if (onStatus != null) onStatus.accept("JES error: " + ex.getMessage());
      }
      return timelineLoaded;
    }
  }

  private void bindJesScene(JesScene2D scene) {
    jesScene = scene;
    if (viewport == null) return;
    viewport.setBeforeSceneUpdateHook(null);
    if (scene != null) {
      scene.setInput(viewport.getInput());
      scene.setCamera(viewport.getCamera());
      viewport.setScene(scene);
    } else {
      viewport.setScene(null);
    }
  }

  private boolean tryLoadTimelinePreview(String code) {
    if (viewport == null || code == null || !code.contains("timeline")) return false;
    TimelineData timelineData;
    try {
      timelineData = TimelineDataParser.parse(timelineNameForPreview(), code);
    } catch (Exception ex) {
      viewport.setActiveError(VnErrorOverlay.puppeteerJesParseError(sourceNameForOverlay(), code, ex));
      if (onStatus != null) onStatus.accept("Puppeteer JES error: " + ex.getMessage());
      return false;
    }
    if (timelineData == null) return false;

    JesScene2D previewScene = buildTimelinePreviewScene(timelineData);
    timelineData.setLooping(true);
    TimelineRunner runner = new TimelineRunner(timelineData, createTimelineSceneAccessor(previewScene));
    runner.applyFrame(0.0);
    bindJesScene(previewScene);
    viewport.clearActiveError();
    viewport.setBeforeSceneUpdateHook(deltaMs -> runner.update(deltaMs));
    viewport.fitToContent();
    return true;
  }

  private String sourceNameForOverlay() {
    return file == null ? null : file.getPath();
  }

  private String timelineNameForPreview() {
    if (file == null) return "_timeline_preview";
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  private JesScene2D buildTimelinePreviewScene(TimelineData data) {
    JesScene2D scene = new JesScene2D();
    if (data == null) return scene;

    List<TimelineData.Track> tracks = new ArrayList<>();
    for (TimelineData.Track track : data.getTracks()) {
      if (track == null) continue;
      String name = track.getEntityName();
      if (name == null || name.isBlank() || "__camera__".equals(name)) continue;
      tracks.add(track);
    }
    tracks.sort(Comparator.comparing(TimelineData.Track::getEntityName));

    for (int i = 0; i < tracks.size(); i++) {
      TimelineData.Track track = tracks.get(i);
      String name = track.getEntityName();
      double fallbackX = 80.0 + (i % 5) * 170.0;
      double fallbackY = 90.0 + (i / 5) * 130.0;
      double x = track.hasKeyframes(TimelineData.Property.X) ? track.getValueAt(TimelineData.Property.X, 0.0) : fallbackX;
      double y = track.hasKeyframes(TimelineData.Property.Y) ? track.getValueAt(TimelineData.Property.Y, 0.0) : fallbackY;

      Panel2D card = new Panel2D(140, 84);
      card.setFill(colorByIndex(i, 0.35), colorByIndex(i + 2, 0.45), colorByIndex(i + 4, 0.55), 0.9);
      card.setStroke(1.0, 1.0, 1.0, 0.75, 1.5);
      card.setPosition(x, y);
      card.setZ(i * 2.0);
      scene.add(card);
      scene.registerEntity(name, card);

      Label2D label = new Label2D(name);
      label.setAlign(Label2D.Align.CENTER);
      label.setFont("Arial", 13, true);
      label.setColor(1.0, 1.0, 1.0, 0.95);
      label.setPosition(x + 70.0, y + 48.0);
      label.setZ(i * 2.0 + 1.0);
      scene.add(label);
    }
    return scene;
  }

  private static double colorByIndex(int index, double floor) {
    double wave = Math.sin(index * 1.37) * 0.5 + 0.5;
    return floor + wave * (1.0 - floor);
  }

  private SceneAccessor createTimelineSceneAccessor(JesScene2D previewScene) {
    return new SceneAccessor() {
      @Override
      public Entity2D findEntity(String name) {
        return previewScene == null ? null : previewScene.find(name);
      }

      @Override
      public void setCameraX(double x) {
        if (viewport == null) return;
        var camera = viewport.getCamera();
        camera.setPosition(x, camera.getY());
      }

      @Override
      public void setCameraY(double y) {
        if (viewport == null) return;
        var camera = viewport.getCamera();
        camera.setPosition(camera.getX(), y);
      }

      @Override
      public void setCameraZoom(double zoom) {
        if (viewport == null) return;
        viewport.getCamera().setZoom(zoom);
      }

      @Override
      public void applyCustomProperty(String target, String propertyKey, double value) {
        if (propertyKey == null || propertyKey.isBlank()) return;
        if (viewport != null && "__camera__".equals(target)) {
          viewport.getCamera().applyCustomProperty(propertyKey, value);
          return;
        }
        Entity2D entity = findEntity(target);
        if (entity != null) entity.applyCustomProperty(propertyKey, value);
      }

      @Override
      public void onEventCue(String type, java.util.Map<String, String> payload) {
        if (previewScene == null || type == null || type.isBlank()) return;
        java.util.Map<String, String> safePayload = payload == null ? java.util.Map.of() : payload;
        String normalized = type.trim().toLowerCase(java.util.Locale.ROOT);
        String target = safePayload.getOrDefault("target", "");
        Entity2D entity = target.isBlank() ? null : previewScene.find(target);

        switch (normalized) {
          case "show":
            if (entity != null) entity.setVisible(true);
            applyPreviewSpritePath(entity, safePayload.get("path"));
            break;
          case "hide":
            if (entity != null) entity.setVisible(false);
            break;
          case "replace":
          case "expression":
            applyPreviewSpritePath(entity, safePayload.get("path"));
            if (entity != null && safePayload.get("path") != null && !safePayload.get("path").isBlank()) {
              entity.setVisible(true);
            }
            break;
          case "scene":
            Entity2D background = entity != null ? entity : findPreviewBackground(previewScene);
            applyPreviewSpritePath(background, safePayload.get("path"));
            if (background != null && safePayload.get("path") != null && !safePayload.get("path").isBlank()) {
              background.setVisible(true);
            }
            break;
          default:
            break;
        }
      }
    };
  }

  private static void applyPreviewSpritePath(Entity2D entity, String rawPath) {
    if (!(entity instanceof com.jvn.core.scene2d.Sprite2D sprite)) return;
    if (rawPath == null || rawPath.isBlank()) return;
    sprite.setImagePath(rawPath);
  }

  private static Entity2D findPreviewBackground(JesScene2D scene) {
    if (scene == null) return null;
    for (String name : scene.names()) {
      if (name == null || name.isBlank() || !name.startsWith("bg_")) continue;
      return scene.find(name);
    }
    return null;
  }

  public void toggleEditorFullscreen() {
    if (!supportsEditorFullscreenToggle()) return;
    if (!editorFullscreen) {
      double pos = currentDividerPosition();
      restoreDividerPosition = (pos > 0.02 && pos < 0.98) ? pos : restoreDividerPosition;
      double codeOnly = primarySplitCodeIndex <= 0 ? 1.0 : 0.0;
      primarySplit.setDividerPositions(codeOnly);
      editorFullscreen = true;
    } else {
      double restore = Math.max(0.05, Math.min(0.95, restoreDividerPosition));
      primarySplit.setDividerPositions(restore);
      editorFullscreen = false;
    }
  }

  private SplitPane createVerticalSplit(Node top, Node bottom, double divider) {
    return createSplit(top, bottom, Orientation.VERTICAL, divider, 1);
  }

  private SplitPane createSplit(Node first, Node second, Orientation orientation, double divider, int codeIndex) {
    detachFromParent(first);
    detachFromParent(second);
    SplitPane sp = new SplitPane();
    sp.setOrientation(orientation);
    sp.getItems().addAll(first, second);
    sp.setDividerPositions(divider);
    primarySplit = sp;
    primarySplitCodeIndex = codeIndex <= 0 ? 0 : 1;
    restoreDividerPosition = divider;
    editorFullscreen = false;
    return sp;
  }

  private Node createPreviewWorkspace(String title, Node previewNode, Node editorNode, double divider) {
    BorderPane root = new BorderPane();
    root.getStyleClass().add("script-editor-workspace-root");
    previewWorkspaceContent = new BorderPane();
    previewWorkspaceContent.getStyleClass().add("script-editor-workspace-content");
    compositionGuideOverlay = new CompositionGuideOverlay();
    ProjectViewportSpec.Dimensions dimensions = ProjectViewportSpec.resolve(projectRoot);
    compositionGuideOverlay.setVirtualResolution(dimensions.width(), dimensions.height());
    StackPane previewWithGuides = new StackPane(previewNode, compositionGuideOverlay);
    dockPreviewNode = previewWithGuides;
    dockEditorNode = editorNode;
    previewDockPosition = PreviewDockPosition.TOP;
    lastEmbeddedPreviewDock = PreviewDockPosition.TOP;
    verticalDockDivider = clampDivider(divider);
    horizontalDockDivider = 0.5;

    previewModePreviewButton = new ToggleButton("Preview");
    previewModeCodeButton = new ToggleButton("Code");
    previewModeSplitButton = new ToggleButton("Split");
    boolean vnsDetachedOnly = isVnsPreviewWorkspace();
    boolean allowSplitToggle = !vnsDetachedOnly;
    ToggleGroup modeGroup = new ToggleGroup();
    previewModePreviewButton.setToggleGroup(modeGroup);
    previewModeCodeButton.setToggleGroup(modeGroup);
    previewModeSplitButton.setToggleGroup(modeGroup);
    if (allowSplitToggle) {
      previewModeSplitButton.setSelected(true);
    } else {
      previewModeCodeButton.setSelected(true);
      previewModeSplitButton.setManaged(false);
      previewModeSplitButton.setVisible(false);
    }
    if (vnsDetachedOnly) {
      previewModePreviewButton.setManaged(false);
      previewModePreviewButton.setVisible(false);
      previewModePreviewButton.setDisable(true);
      previewModeCodeButton.setManaged(false);
      previewModeCodeButton.setVisible(false);
      previewModeCodeButton.setDisable(true);
    }

    MenuItem dockTop = new MenuItem("Snap Top");
    dockTop.setOnAction(e -> setPreviewDockPosition(PreviewDockPosition.TOP));
    MenuItem dockBottom = new MenuItem("Snap Bottom");
    dockBottom.setOnAction(e -> setPreviewDockPosition(PreviewDockPosition.BOTTOM));
    MenuItem dockLeft = new MenuItem("Snap Left");
    dockLeft.setOnAction(e -> setPreviewDockPosition(PreviewDockPosition.LEFT));
    MenuItem dockRight = new MenuItem("Snap Right");
    dockRight.setOnAction(e -> setPreviewDockPosition(PreviewDockPosition.RIGHT));
    MenuItem dockWindow = new MenuItem("Open Separate Window");
    dockWindow.setOnAction(e -> setPreviewDockPosition(PreviewDockPosition.WINDOW));
    MenuItem dockBack = new MenuItem("Re-dock from Window");
    dockBack.setOnAction(e -> closeDetachedPreviewWindow(false));

    previewDockMenu = new MenuButton("Snap");
    if (vnsDetachedOnly) {
      previewDockMenu.getItems().add(dockWindow);
    } else {
      previewDockMenu.getItems().addAll(dockTop, dockBottom, dockLeft, dockRight, dockWindow, dockBack);
    }

    previewModePreviewButton.getStyleClass().add("layout-studio-toolbar-toggle");
    previewModeCodeButton.getStyleClass().add("layout-studio-toolbar-toggle");
    previewModeSplitButton.getStyleClass().add("layout-studio-toolbar-toggle");
    previewDockMenu.getStyleClass().add("layout-studio-toolbar-button");
    previewModePreviewButton.getStyleClass().add("script-editor-workspace-toggle");
    previewModeCodeButton.getStyleClass().add("script-editor-workspace-toggle");
    previewModeSplitButton.getStyleClass().add("script-editor-workspace-toggle");
    previewDockMenu.getStyleClass().addAll("script-editor-workspace-menu-button", "preview-toolbar-icon-menu");

    configureIconToggle(previewModePreviewButton, CssIcon.visibility("#b8c5d8"), "Show preview only");
    configureIconToggle(previewModeCodeButton, CssIcon.list("#b8c5d8"), "Show code only");
    configureIconToggle(previewModeSplitButton, CssIcon.grid("#b8c5d8"), "Show preview and code");
    configureIconMenuButton(previewDockMenu,
        isVnsPreviewWorkspace() ? CssIcon.popOut("#b8c5d8") : CssIcon.dock("#b8c5d8"),
        isVnsPreviewWorkspace() ? "Open preview in a separate window" : "Preview dock options");

    Node[] toolbarActions;
    if (vnsDetachedOnly && vnsEditor != null) {
      Button runLabel = new Button();
      configureVnsAeroButton(
          runLabel,
          AeroIcon.of(AeroIcon.Kind.VNS_RUN_LABEL, 32),
          "Run from current label",
          "Start at the nearest @label at or above the caret");
      runLabel.setOnAction(e -> vnsEditor.launchFromCurrentLabel());

      Button runCursor = new Button();
      configureVnsAeroButton(
          runCursor,
          AeroIcon.of(AeroIcon.Kind.VNS_RUN_CURSOR, 32),
          "Run from cursor",
          "Start at the first executable VNS line at or after the blinking caret (F5)");
      runCursor.setOnAction(e -> vnsEditor.launchFromCursor());

      Button runStart = new Button();
      configureVnsAeroButton(
          runStart,
          AeroIcon.of(AeroIcon.Kind.VNS_RUN_ENTRY, 32),
          "Run from script entry",
          "Start at the project entry point and open the runtime preview (Shift+F5)");
      runStart.setOnAction(e -> vnsEditor.launchFromStart());

      Button symbols = new Button();
      configureVnsAeroButton(
          symbols,
          AeroIcon.of(AeroIcon.Kind.VNS_SYMBOLS, 32),
          "Go to VNS symbol",
          "Find an @label declaration in this script (Ctrl/Cmd+Shift+O)");
      symbols.setOnAction(e -> vnsEditor.showSymbolNavigator());

      Button snippets = new Button();
      configureVnsAeroButton(
          snippets,
          AeroIcon.of(AeroIcon.Kind.VNS_SNIPPET, 32),
          "Insert VNS snippet",
          "Open the VNS command and declaration palette (Ctrl/Cmd+J)");
      snippets.setOnAction(e -> vnsEditor.showSnippetPicker());

      Button find = new Button();
      configureVnsAeroButton(
          find,
          AeroIcon.of(AeroIcon.Kind.VNS_FIND, 32),
          "Find in VNS script",
          "Find text in this script (Ctrl/Cmd+F)");
      find.setOnAction(e -> vnsEditor.showSearchBar());

      Button commands = new Button();
      configureVnsAeroButton(
          commands,
          AeroIcon.of(AeroIcon.Kind.VNS_COMMANDS, 32),
          "Open VNS command palette",
          "Search all VNS editor commands (Ctrl/Cmd+Shift+P)");
      commands.setOnAction(e -> vnsEditor.showCommandPalette());

      ToggleButton wordWrap = new ToggleButton();
      configureVnsAeroToggle(
          wordWrap,
          AeroIcon.of(AeroIcon.Kind.VNS_WORD_WRAP, 32),
          "Toggle VNS word wrap",
          "Wrap long script lines in the editor (Ctrl/Cmd+Shift+W)");
      wordWrap.setSelected(vnsEditor.isWordWrapEnabled());
      wordWrap.setOnAction(e -> wordWrap.setSelected(vnsEditor.toggleWordWrap()));
      vnsWordWrapButton = wordWrap;

      Button diff = new Button();
      configureVnsAeroButton(
          diff,
          AeroIcon.of(AeroIcon.Kind.VNS_DIFF, 32),
          "Compare VNS script with saved version",
          "Show changes since the script was last saved or loaded (Ctrl/Cmd+Shift+D)");
      diff.setOnAction(e -> vnsEditor.showDiffView());

      Button diagnostics = new Button();
      configureVnsAeroButton(
          diagnostics,
          AeroIcon.of(AeroIcon.Kind.VNS_DIAGNOSTICS, 32),
          "Open VNS diagnostics",
          "Inspect errors, warnings, and source locations for this script");
      diagnostics.setOnAction(e -> {
        if (onOpenDiagnostics != null) onOpenDiagnostics.run();
      });

      Button openPreview = new Button();
      configureVnsAeroButton(
          openPreview,
          AeroIcon.of(AeroIcon.Kind.VNS_PREVIEW, 32),
          "Open runtime preview",
          "Open the live game preview in a separate resizable window");
      openPreview.setOnAction(e -> setPreviewDockPosition(PreviewDockPosition.WINDOW));

      Button help = SidebarToolHelp.button(
          root,
          "VNS editor tools",
          """
          Run from current label
          Starts at the nearest @label at or above the blinking caret.

          Run from cursor
          Starts at the first executable VNS line at or after the blinking caret. Shortcut: F5.

          Run from script entry
          Starts the runtime from the project entry point. Shortcut: Shift+F5.

          Go to symbol
          Searches @label declarations in this script. Shortcut: Ctrl/Cmd+Shift+O.

          Insert snippet
          Opens the VNS command and declaration palette. Shortcut: Ctrl/Cmd+J.

          Find in script
          Opens the editor search bar. Shortcut: Ctrl/Cmd+F.

          Command palette
          Searches all VNS editor commands. Shortcut: Ctrl/Cmd+Shift+P.

          Word wrap
          Wraps long script lines without changing the file. Shortcut: Ctrl/Cmd+Shift+W.

          Compare with saved
          Shows changes since the file was last saved or loaded. Shortcut: Ctrl/Cmd+Shift+D.

          Diagnostics
          Opens the diagnostics panel for errors, warnings, and source navigation.

          Open runtime preview
          Opens the live game preview in its own resizable window.
          """);
      help.setGraphic(AeroIcon.of(AeroIcon.Kind.HELP, 30));
      help.setMinSize(38, 36);
      help.setPrefSize(38, 36);
      help.setMaxSize(38, 36);
      help.getStyleClass().add("vns-tools-help-button");

      toolbarActions = new Node[] {
          runLabel, runCursor, runStart, vnsToolSeparator(),
          symbols, snippets, find, commands, vnsToolSeparator(),
          wordWrap, diff, diagnostics, vnsToolSeparator(),
          openPreview, vnsToolSeparator(), help
      };
    } else {
      toolbarActions = new Node[] {
          previewModePreviewButton, previewModeCodeButton, previewModeSplitButton, previewDockMenu
      };
    }

    HBox toolbar = vnsDetachedOnly
        ? buildVnsToolsStrip(toolbarActions)
        : buildWorkspaceToolbar(
            title == null ? "Preview" : title,
            previewWorkspaceSubtitle(false),
            previewWorkspaceTitleIcon(),
            toolbarActions);

    root.setTop(toolbar);
    root.setCenter(previewWorkspaceContent);

    modeGroup.selectedToggleProperty().addListener((o, ov, nv) -> {
      if (nv == null) {
        if (allowSplitToggle) previewModeSplitButton.setSelected(true);
        else previewModeCodeButton.setSelected(true);
      }
      applyPreviewWorkspaceMode();
    });

    applyPreviewWorkspaceMode();
    return root;
  }

  private void setPreviewDockPosition(PreviewDockPosition position) {
    if (position == null || dockPreviewNode == null || dockEditorNode == null) return;
    if (isVnsPreviewWorkspace() && position != PreviewDockPosition.WINDOW) return;
    if (position == PreviewDockPosition.WINDOW) {
      openDetachedPreviewWindow();
      return;
    }
    lastEmbeddedPreviewDock = position;
    previewDockPosition = position;
    closeDetachedPreviewWindow(false);
    applyPreviewWorkspaceMode();
  }

  private PreviewLayoutMode currentPreviewLayoutMode() {
    if (isVnsPreviewWorkspace()) return PreviewLayoutMode.CODE;
    if (previewModePreviewButton != null && previewModePreviewButton.isSelected()) return PreviewLayoutMode.PREVIEW;
    if (previewModeCodeButton != null && previewModeCodeButton.isSelected()) return PreviewLayoutMode.CODE;
    return PreviewLayoutMode.SPLIT;
  }

  private void restorePreviewLayoutMode() {
    if (!isVnsPreviewWorkspace()
        && previewModeBeforeDetach == PreviewLayoutMode.PREVIEW
        && previewModePreviewButton != null) {
      previewModePreviewButton.setSelected(true);
      return;
    }
    if (!isVnsPreviewWorkspace()
        && previewModeBeforeDetach == PreviewLayoutMode.SPLIT
        && previewModeSplitButton != null) {
      previewModeSplitButton.setSelected(true);
      return;
    }
    if (previewModeCodeButton != null) {
      previewModeCodeButton.setSelected(true);
      return;
    }
    applyPreviewWorkspaceMode();
  }

  private void applyPreviewWorkspaceMode() {
    if (previewWorkspaceContent == null || dockEditorNode == null) return;
    PreviewLayoutMode mode = currentPreviewLayoutMode();
    boolean detached = isDetachedPreviewVisible();

    if (!detached && mode == PreviewLayoutMode.SPLIT && dockPreviewNode != null) {
      PreviewDockPosition dock = previewDockPosition == PreviewDockPosition.WINDOW ? lastEmbeddedPreviewDock : previewDockPosition;
      previewWorkspaceContent.setCenter(buildDockedPreviewSplit(dock));
    } else if (!detached && mode == PreviewLayoutMode.PREVIEW && dockPreviewNode != null) {
      detachFromParent(dockPreviewNode);
      previewWorkspaceContent.setCenter(dockPreviewNode);
      primarySplit = null;
      editorFullscreen = false;
    } else {
      detachFromParent(dockEditorNode);
      previewWorkspaceContent.setCenter(dockEditorNode);
      primarySplit = null;
      editorFullscreen = false;
    }
    updatePreviewDockMenuText();
    refreshPreviewSizeFromLast();
  }

  private SplitPane buildDockedPreviewSplit(PreviewDockPosition dock) {
    if (dockPreviewNode == null || dockEditorNode == null) return createVerticalSplit(new Label("No preview"), dockEditorNode, 0.6);
    PreviewDockPosition resolved = dock == null ? PreviewDockPosition.TOP : dock;
    SplitPane split;
    if (resolved == PreviewDockPosition.BOTTOM) {
      split = createSplit(dockEditorNode, dockPreviewNode, Orientation.VERTICAL, verticalDockDivider, 0);
    } else if (resolved == PreviewDockPosition.LEFT) {
      split = createSplit(dockPreviewNode, dockEditorNode, Orientation.HORIZONTAL, horizontalDockDivider, 1);
    } else if (resolved == PreviewDockPosition.RIGHT) {
      split = createSplit(dockEditorNode, dockPreviewNode, Orientation.HORIZONTAL, horizontalDockDivider, 0);
    } else {
      split = createSplit(dockPreviewNode, dockEditorNode, Orientation.VERTICAL, verticalDockDivider, 1);
    }
    if (!split.getDividers().isEmpty()) {
      split.getDividers().get(0).positionProperty().addListener((o, ov, nv) -> {
        if (split.getOrientation() == Orientation.VERTICAL) {
          verticalDockDivider = clampDivider(nv.doubleValue());
        } else {
          horizontalDockDivider = clampDivider(nv.doubleValue());
        }
        refreshPreviewSizeFromLast();
      });
    }
    return split;
  }

  private void openDetachedPreviewWindow() {
    if (dockPreviewNode == null) return;
    if (detachedPreviewStage != null && detachedPreviewStage.isShowing()) {
      detachedPreviewStage.toFront();
      return;
    }
    previewModeBeforeDetach = currentPreviewLayoutMode();
    if (previewDockPosition != PreviewDockPosition.WINDOW) {
      lastEmbeddedPreviewDock = previewDockPosition;
    }
    previewDockPosition = PreviewDockPosition.WINDOW;

    detachFromParent(dockPreviewNode);
    StackPane host = new StackPane(dockPreviewNode);
    double[] initialSize = initialDetachedPreviewSize();
    Scene scene = new Scene(host, initialSize[0], initialSize[1]);
    scene.widthProperty().addListener((o, ov, nv) -> refreshPreviewSizeFromLast());
    scene.heightProperty().addListener((o, ov, nv) -> refreshPreviewSizeFromLast());

    Stage stage = new Stage();
    stage.setTitle((file != null ? file.getName() : "Preview") + " - Detached Preview");
    stage.setScene(scene);
    stage.setOnHidden(e -> handleDetachedPreviewStageHidden(stage));
    detachedPreviewStage = stage;
    stage.show();
    if (kind == Kind.VNS && vnPreview != null) {
      vnPreview.setPlaybackActive(true);
    }
    if (previewModeBeforeDetach != PreviewLayoutMode.CODE && previewModeCodeButton != null) {
      previewModeCodeButton.setSelected(true);
    }
    startDetachedPreviewTimer();
    applyPreviewWorkspaceMode();
  }

  private void closeDetachedPreviewWindow(boolean disposing) {
    if (detachedPreviewStage == null) return;
    Stage stage = detachedPreviewStage;
    detachedPreviewStage = null;
    stopDetachedPreviewTimer();
    if (kind == Kind.VNS && vnPreview != null) {
      vnPreview.setPlaybackActive(false);
    }
    removeDockPreviewFromParent();
    if (stage.isShowing()) stage.hide();
    if (disposing) return;
    previewDockPosition = lastEmbeddedPreviewDock;
    restorePreviewLayoutMode();
  }

  private void showVnsParseOverlay(Exception ex) {
    if (vnPreview == null) return;
    String sourceName = resolveVnsSourceName();
    vnPreview.setSourceScriptName(resolveVnsScriptKey());
    vnPreview.setActiveError(VnErrorOverlay.fromScriptLoadFailure(sourceName, ex));
  }

  boolean isDetachedPreviewVisible() {
    return detachedPreviewStage != null && detachedPreviewStage.isShowing();
  }

  private void startDetachedPreviewTimer() {
    if (detachedPreviewTimer != null) return;
    detachedPreviewPacer = PreviewFramePacer.forCurrentPipeline();
    detachedPreviewTimer = new AnimationTimer() {
      @Override
      public void handle(long now) {
        if (!isDetachedPreviewVisible()) return;
        PreviewFramePacer.Frame frame = detachedPreviewPacer.next(now);
        if (!frame.render()) return;
        try {
          if (kind == Kind.JES && viewport != null) {
            viewport.render(frame.deltaMs());
          } else if (kind == Kind.VNS && vnPreview != null) {
            vnPreview.render(frame.deltaMs());
          }
        } catch (Exception ex) {
          stopDetachedPreviewTimer();
          if (onStatus != null) onStatus.accept("Detached preview stopped: " + ex.getMessage());
        }
      }
    };
    detachedPreviewTimer.start();
  }

  private void handleDetachedPreviewStageHidden(Stage stage) {
    if (stage == null || detachedPreviewStage != stage) return;
    detachedPreviewStage = null;
    stopDetachedPreviewTimer();
    if (kind == Kind.VNS && vnPreview != null) {
      vnPreview.setPlaybackActive(false);
    } else {
      stopPreviewAudio();
    }
    removeDockPreviewFromParent();
    if (disposed) return;

    Platform.runLater(() -> {
      if (disposed || detachedPreviewStage != null) return;
      previewDockPosition = lastEmbeddedPreviewDock;
      restorePreviewLayoutMode();
    });
  }

  private void removeDockPreviewFromParent() {
    Parent parent = dockPreviewNode == null ? null : dockPreviewNode.getParent();
    if (parent instanceof Pane pane) {
      pane.getChildren().remove(dockPreviewNode);
    } else if (parent instanceof BorderPane border && border.getCenter() == dockPreviewNode) {
      border.setCenter(null);
    }
  }

  private void stopDetachedPreviewTimer() {
    if (detachedPreviewTimer == null) return;
    detachedPreviewTimer.stop();
    detachedPreviewTimer = null;
  }

  private void updatePreviewDockMenuText() {
    if (previewDockMenu == null) return;
    String tooltipText;
    if (isDetachedPreviewVisible()) {
      tooltipText = isVnsPreviewWorkspace()
          ? "Runtime preview window is open"
          : "Preview is detached in a separate window";
    } else {
      PreviewDockPosition dock = previewDockPosition == PreviewDockPosition.WINDOW ? lastEmbeddedPreviewDock : previewDockPosition;
      String label = switch (dock) {
        case LEFT -> "left";
        case RIGHT -> "right";
        case BOTTOM -> "bottom";
        case WINDOW -> "window";
        case TOP -> "top";
      };
      tooltipText = isVnsPreviewWorkspace()
          ? "Open runtime preview in a separate window"
          : "Preview dock: " + label;
    }
    previewDockMenu.setText("");
    previewDockMenu.setTooltip(new Tooltip(tooltipText));
    previewDockMenu.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
  }

  private void refreshPreviewSizeFromLast() {
    if (lastSizedWidth <= 0 || lastSizedHeight <= 0) return;
    applyPreviewSizing(lastSizedWidth, lastSizedHeight);
  }

  private void applyPreviewSizing(double safeW, double safeH) {
    double previewW = safeW;
    double previewH = sanitizeDimension(safeH * 0.6);

    if (isDetachedPreviewVisible() && detachedPreviewStage != null && detachedPreviewStage.getScene() != null) {
      previewW = sanitizeDimension(detachedPreviewStage.getScene().getWidth());
      previewH = sanitizeDimension(detachedPreviewStage.getScene().getHeight());
    } else if (previewModePreviewButton != null && previewModePreviewButton.isSelected()) {
      previewW = safeW;
      previewH = safeH;
    } else if (previewModeSplitButton != null && previewModeSplitButton.isSelected() && primarySplit != null) {
      double pos = currentDividerPosition();
      double ratio = primarySplitCodeIndex == 1 ? pos : (1.0 - pos);
      ratio = clampDivider(ratio);
      if (primarySplit.getOrientation() == Orientation.HORIZONTAL) {
        previewW = sanitizeDimension(safeW * ratio);
        previewH = safeH;
      } else {
        previewW = safeW;
        previewH = sanitizeDimension(safeH * ratio);
      }
    }

    if (viewport != null) viewport.setSize(previewW, previewH);
    if (vnPreview != null) vnPreview.setSize(previewW, previewH);
  }

  private double currentDividerPosition() {
    if (primarySplit == null) return restoreDividerPosition;
    if (primarySplit.getDividers().isEmpty()) return restoreDividerPosition;
    return primarySplit.getDividers().get(0).getPosition();
  }

  private String getCurrentText() {
    if (kind == Kind.JES && jesEditor != null) return jesEditor.getText();
    if (kind == Kind.VNS && vnsEditor != null) return vnsEditor.getText();
    if (kind == Kind.TIMELINE && timelineEditor != null) return timelineEditor.getText();
    if (kind == Kind.THEME && themeEditor != null) return themeEditor.getText();
    if (kind == Kind.MENU_SCREEN && menuScreenEditor != null) return menuScreenEditor.getText();
    if (kind == Kind.MENU_LAYOUT && menuLayoutEditor != null) return menuLayoutEditor.getText();
    if (kind == Kind.MENU_STYLE && menuStyleEditor != null) return menuStyleEditor.getText();
    if (kind == Kind.DIALOGUE_LAYOUT && dialogueLayoutEditor != null) return dialogueLayoutEditor.getText();
    if (kind == Kind.JAVA && javaEditor != null) return javaEditor.getText();
    if (kind == Kind.OTHER && textEditor != null) return textEditor.getText();
    return "";
  }

  private static void detachFromParent(Node node) {
    if (node == null) return;
    Parent parent = node.getParent();
    if (parent == null) return;
    if (parent instanceof SplitPane split) {
      split.getItems().remove(node);
      return;
    }
    if (parent instanceof BorderPane border) {
      if (border.getCenter() == node) border.setCenter(null);
      else if (border.getTop() == node) border.setTop(null);
      else if (border.getBottom() == node) border.setBottom(null);
      else if (border.getLeft() == node) border.setLeft(null);
      else if (border.getRight() == node) border.setRight(null);
      return;
    }
    if (parent instanceof Pane pane) {
      pane.getChildren().remove(node);
    }
  }

  private static double clampDivider(double value) {
    if (!Double.isFinite(value)) return 0.5;
    return Math.max(0.05, Math.min(0.95, value));
  }

  private static String mapKey(KeyCode code) {
    if (code == null) return "";
    String name = code.getName();
    if (name == null || name.isBlank()) name = code.toString();
    return name.toUpperCase();
  }

  private static String normalizeLineEndings(String text) {
    if (text == null) return "";
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static double sanitizeDimension(double value) {
    if (!Double.isFinite(value)) return 1.0;
    return Math.max(1.0, Math.min(8192.0, value));
  }

  private double[] initialDetachedPreviewSize() {
    double width = Math.max(640.0, sanitizeDimension(lastSizedWidth * 0.75));
    double height = Math.max(360.0, sanitizeDimension(lastSizedHeight * 0.65));

    if (kind != Kind.VNS) {
      return new double[] {width, height};
    }

    double aspect = ProjectViewportSpec.resolve(projectRoot).aspect();
    if (!Double.isFinite(aspect) || aspect <= 0.05) aspect = 16.0 / 9.0;

    if ((width / height) > aspect) width = height * aspect;
    else height = width / aspect;

    width = Math.max(640.0, sanitizeDimension(width));
    height = Math.max(360.0, sanitizeDimension(height));
    return new double[] {width, height};
  }

  private boolean isVnsPreviewWorkspace() {
    return kind == Kind.VNS;
  }

  private String previewWorkspaceSubtitle(boolean vnsDetachedOnly) {
    if (vnsDetachedOnly) return null;
    return switch (kind) {
      case JES -> "Scene preview and source editor";
      case TIMELINE -> "Story Map preview and source editor";
      case THEME -> "Theme preview and source editor";
      default -> "Preview, source, and split modes";
    };
  }

  private Region previewWorkspaceTitleIcon() {
    return switch (kind) {
      case VNS -> AeroIcon.of(AeroIcon.Kind.SCRIPT_EDITOR, 26);
      case THEME -> CssIcon.palette("#d7e1f0");
      case TIMELINE -> CssIcon.play("#d7e1f0");
      default -> CssIcon.visibility("#d7e1f0");
    };
  }

  private static HBox buildWorkspaceToolbar(String title, String meta, Region icon, Node... actions) {
    HBox titleRow = new HBox(8);
    titleRow.setAlignment(Pos.CENTER_LEFT);
    if (icon != null) titleRow.getChildren().add(icon);

    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("script-editor-workspace-title");
    titleRow.getChildren().add(titleLabel);

    VBox titleBox = new VBox(1);
    titleBox.getChildren().add(titleRow);
    if (meta != null && !meta.isBlank()) {
      Label metaLabel = new Label(meta);
      metaLabel.getStyleClass().add("script-editor-workspace-meta");
      titleBox.getChildren().add(metaLabel);
    }
    titleBox.getStyleClass().add("script-editor-workspace-title-box");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    HBox toolbar = new HBox(8);
    toolbar.getStyleClass().add("script-editor-workspace-toolbar");
    toolbar.setPadding(new Insets(8, 10, 8, 10));
    toolbar.setAlignment(Pos.CENTER_LEFT);
    toolbar.getChildren().addAll(titleBox, spacer);
    if (actions != null) {
      for (Node action : actions) {
        if (action != null) toolbar.getChildren().add(action);
      }
    }
    return toolbar;
  }

  private static HBox buildVnsToolsStrip(Node... actions) {
    HBox toolbar = new HBox(5);
    toolbar.getStyleClass().addAll("script-editor-workspace-toolbar", "vns-tools-strip");
    toolbar.setAlignment(Pos.CENTER_LEFT);
    if (actions != null) {
      for (Node action : actions) {
        if (action != null) toolbar.getChildren().add(action);
      }
    }
    return toolbar;
  }

  private static void configureIconToggle(ToggleButton button, Node icon, String tooltipText) {
    if (button == null) return;
    button.setText("");
    button.setGraphic(icon);
    button.setTooltip(new Tooltip(tooltipText));
    button.setMinWidth(32);
    button.setPrefWidth(32);
    button.setMaxWidth(32);
    button.setMinHeight(30);
    button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    if (!button.getStyleClass().contains("layout-studio-icon-button")) {
      button.getStyleClass().add("layout-studio-icon-button");
    }
    if (!button.getStyleClass().contains("script-editor-workspace-icon-button")) {
      button.getStyleClass().add("script-editor-workspace-icon-button");
    }
  }

  private static void configureIconButton(Button button, Node icon, String tooltipText) {
    if (button == null) return;
    button.setText("");
    button.setGraphic(icon);
    button.setTooltip(new Tooltip(tooltipText));
    button.setMinSize(32, 30);
    button.setPrefSize(32, 30);
    button.setMaxSize(32, 30);
    button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    button.getStyleClass().addAll(
        "layout-studio-toolbar-button",
        "layout-studio-icon-button",
        "script-editor-workspace-icon-button");
  }

  private static void configureVnsAeroButton(
      Button button, AeroIcon icon, String accessibleText, String tooltipText) {
    button.setText("");
    button.setGraphic(icon);
    button.setTooltip(new Tooltip(tooltipText));
    button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    button.setAccessibleText(accessibleText);
    button.setMinSize(38, 36);
    button.setPrefSize(38, 36);
    button.setMaxSize(38, 36);
    button.setFocusTraversable(false);
    button.getStyleClass().add("vns-tools-aero-button");
  }

  private static void configureVnsAeroToggle(
      ToggleButton button, AeroIcon icon, String accessibleText, String tooltipText) {
    button.setText("");
    button.setGraphic(icon);
    button.setTooltip(new Tooltip(tooltipText));
    button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    button.setAccessibleText(accessibleText);
    button.setMinSize(38, 36);
    button.setPrefSize(38, 36);
    button.setMaxSize(38, 36);
    button.setFocusTraversable(false);
    button.getStyleClass().add("vns-tools-aero-button");
  }

  private static Separator vnsToolSeparator() {
    Separator separator = new Separator(Orientation.VERTICAL);
    separator.setMinHeight(28);
    separator.setPrefHeight(28);
    separator.setMaxHeight(28);
    separator.getStyleClass().add("vns-tools-separator");
    return separator;
  }

  private static void configureIconMenuButton(MenuButton button, Node icon, String tooltipText) {
    if (button == null) return;
    button.setText("");
    button.setGraphic(icon);
    button.setTooltip(new Tooltip(tooltipText));
    button.setMinWidth(34);
    button.setPrefWidth(34);
    button.setMaxWidth(34);
    button.setMinHeight(30);
    button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    if (!button.getStyleClass().contains("layout-studio-icon-button")) {
      button.getStyleClass().add("layout-studio-icon-button");
    }
    if (!button.getStyleClass().contains("script-editor-workspace-icon-button")) {
      button.getStyleClass().add("script-editor-workspace-icon-button");
    }
  }

  private static JavaCodeEditor newDslEditor() {
    JavaCodeEditor editor = new JavaCodeEditor();
    editor.useDslHighlighting();
    return editor;
  }
}
