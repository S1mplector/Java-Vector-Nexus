package com.jvn.editor.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.audio.simp3.Simp3AudioService;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.audio.AudioFacade;
import com.jvn.core.graphics.ViewportScaler2D;
import com.jvn.core.menu.HistoryMenuScene;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.menu.SaveMenuScene;
import com.jvn.core.menu.SettingsScene;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.phone.PhoneScene;
import com.jvn.core.phone.VnPhoneCommands;
import com.jvn.core.phone.VnPhoneData;
import com.jvn.core.phone.VnPhonePropertiesCodec;
import com.jvn.core.phone.VnPhoneStateStore;
import com.jvn.core.project.StoryMapPaths;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.DefaultVnInterop;
import com.jvn.core.vn.VnAudioCommand;
import com.jvn.core.vn.VnErrorOverlay;
import com.jvn.core.vn.VnExternalCommand;
import com.jvn.core.vn.VnInteropResult;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioLoader;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.core.vn.ui.VnCursorConfigLoader;
import com.jvn.core.vn.ui.VnUiActionButtonSpec;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;
import com.jvn.fx.audio.FxAudioService;
import com.jvn.fx.menu.MenuRenderer;
import com.jvn.fx.menu.MenuTheme;
import com.jvn.fx.phone.PhoneRenderer;
import com.jvn.fx.vn.VnRenderer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class VnPreviewView extends StackPane {
  private static final String PREVIEW_HINT = String.join("\n",
      "VNS Preview Controls",
      "Click: Advance / Choose",
      "Space, Enter: Advance",
      "Ctrl/Cmd: Toggle Skip    A: Toggle Auto    H: Toggle UI",
      "B: Toggle History    Esc: Close overlays",
      "F5: Save menu    F9: Load menu",
      "Digits: Choice selection");

  private final Canvas canvas = new Canvas(1200, 740);
  private final GraphicsContext gc = canvas.getGraphicsContext2D();
  private final VnRenderer renderer = new VnRenderer(gc);
  private final MenuRenderer menuRenderer = new MenuRenderer(gc, MenuTheme.fromAssets());
  private final PhoneRenderer phoneRenderer = new PhoneRenderer();
  private final Tooltip previewTooltip = new Tooltip(PREVIEW_HINT);
  private static final Pattern TIMELINE_ARC_PATTERN = Pattern.compile(
      "^\\s*arc\\s+(?:\"([^\"]+)\"|(\\S+))\\s+script\\s+(?:\"([^\"]+)\"|(\\S+)).*$");
  private static final String VAR_DIALOGUE_PRESENTATION_MODE = "ui.dialogueMode";
  private static final String VAR_DIALOGUE_UI = "ui.dialogueUi";
  private VnScene scene;
  private double mouseX, mouseY;
  private AudioFacade audio;
  private boolean playbackActive;
  private File projectRoot;
  private String audioBackend = "auto";
  private String sourceScriptName;
  private VnUiLayoutSpec uiLayoutOverride;
  private VnUiStyleSpec uiStyleOverride;
  private List<VnUiActionButtonSpec> textBoxButtonsOverride;
  private final VnSaveManager previewSaveManager = new VnSaveManager();
  private Scene overlayScene;
  private Cursor configuredCursor = Cursor.DEFAULT;
  private StoryboardOverlayState storyboardOverlay = StoryboardOverlayState.none();
  private Consumer<StoryboardOverlayState> onStoryboardStateAdjusted;
  private Consumer<Integer> onStoryboardPreviewLineChanged;
  private Runnable onHotReloadRequested;
  private final VBox storyboardHud = new VBox(6);
  private final Label storyboardHudTitle = new Label("Storyboard");
  private final Label storyboardHudFrameLabel = new Label("");
  private final Slider storyboardOpacitySlider = new Slider(0, 100, 35);
  private final Label storyboardOpacityValue = new Label("35%");
  private final CheckBox storyboardHideUiCheck = new CheckBox("Hide UI");
  private boolean applyingStoryboardHud = false;
  private int storyboardPreviewLine = -1;
  private Boolean storyboardUiHiddenRestore;
  private VnErrorOverlay activeError;
  private int errorOverlayHoveredButton = -1;
  private boolean storyboardOffsetDragging;
  private boolean storyboardOffsetDragMoved;
  private boolean suppressNextStoryboardClick;
  private double storyboardDragStartX;
  private double storyboardDragStartY;
  private double storyboardDragStartOffsetX;
  private double storyboardDragStartOffsetY;

  // Virtual viewport: render at the game's target resolution, scale to fit canvas
  private int virtualWidth = 0;
  private int virtualHeight = 0;

  public VnPreviewView() {
    this(null);
  }

  VnPreviewView(AudioFacade audio) {
    this.audio = audio;
    configureStoryboardHud();
    getChildren().addAll(canvas, phoneRenderer);
    StackPane.setAlignment(storyboardHud, Pos.TOP_RIGHT);
    StackPane.setMargin(storyboardHud, new Insets(12));
    setFocusTraversable(true);
    canvas.setFocusTraversable(true);

    // Input handlers
    canvas.setOnMouseMoved(e -> {
      mouseX = e.getX();
      mouseY = e.getY();
      updateOverlayHover(mouseX, mouseY);
    });
    canvas.setOnMouseClicked(e -> handleMouseClick(e.getButton(), e.getClickCount(), e.getX(), e.getY()));
    canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::handleStoryboardOffsetPress);
    canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::handleStoryboardOffsetDrag);
    canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, this::handleStoryboardOffsetRelease);
    canvas.setOnScroll(this::handleScroll);

    setOnKeyPressed(this::handleKeyPressed);

    Tooltip.install(canvas, previewTooltip);

    // Keep focus for key handling when mouse enters
    canvas.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> requestFocus());
    canvas.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> requestFocus());
    canvas.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> applyCursor(configuredCursor));
    phoneRenderer.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> applyCursor(configuredCursor));
  }

  private void configureStoryboardHud() {
    storyboardHud.setManaged(false);
    storyboardHud.setVisible(false);
    storyboardHud.setMaxWidth(220);
    storyboardHud.setPadding(new Insets(10));
    storyboardHud.setStyle(
        "-fx-background-color: rgba(12, 16, 22, 0.88);"
            + "-fx-background-radius: 8;"
            + "-fx-border-color: rgba(255,255,255,0.10);"
            + "-fx-border-radius: 8;");
    storyboardHudTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #f1f5fb;");
    storyboardHudFrameLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #b9c3d4;");
    storyboardHudFrameLabel.setWrapText(true);
    storyboardOpacityValue.setStyle("-fx-font-size: 10px; -fx-text-fill: #d6dde8;");
    storyboardHideUiCheck.setStyle("-fx-text-fill: #d6dde8;");

    storyboardOpacitySlider.valueProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingStoryboardHud) return;
      storyboardOpacityValue.setText(Integer.toString((int) Math.round(newValue.doubleValue())) + "%");
      if (!isStoryboardModeActive()) return;
      storyboardOverlay = new StoryboardOverlayState(
          true,
          storyboardOverlay.image(),
          newValue.doubleValue() / 100.0,
          storyboardOverlay.sourcePath(),
          storyboardHideUiCheck.isSelected(),
          storyboardOverlay.fitMode(),
          storyboardOverlay.runtimeWidth(),
          storyboardOverlay.runtimeHeight(),
          storyboardOverlay.storyboardWidth(),
          storyboardOverlay.storyboardHeight(),
          storyboardOverlay.scale(),
          storyboardOverlay.offsetX(),
          storyboardOverlay.offsetY(),
          storyboardOverlay.cropEnabled(),
          storyboardOverlay.cropX(),
          storyboardOverlay.cropY(),
          storyboardOverlay.cropWidth(),
          storyboardOverlay.cropHeight());
      emitStoryboardStateAdjusted();
    });
    storyboardHideUiCheck.selectedProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingStoryboardHud) return;
      if (!isStoryboardModeActive()) return;
      storyboardOverlay = new StoryboardOverlayState(
          true,
          storyboardOverlay.image(),
          storyboardOverlay.opacity(),
          storyboardOverlay.sourcePath(),
          newValue,
          storyboardOverlay.fitMode(),
          storyboardOverlay.runtimeWidth(),
          storyboardOverlay.runtimeHeight(),
          storyboardOverlay.storyboardWidth(),
          storyboardOverlay.storyboardHeight(),
          storyboardOverlay.scale(),
          storyboardOverlay.offsetX(),
          storyboardOverlay.offsetY(),
          storyboardOverlay.cropEnabled(),
          storyboardOverlay.cropX(),
          storyboardOverlay.cropY(),
          storyboardOverlay.cropWidth(),
          storyboardOverlay.cropHeight());
      applyStoryboardUiState();
      emitStoryboardStateAdjusted();
    });

    storyboardHud.getChildren().addAll(
        storyboardHudTitle,
        storyboardHudFrameLabel,
        storyboardOpacitySlider,
        storyboardOpacityValue,
        storyboardHideUiCheck);
  }

  public void setScenario(VnScenario scenario) {
    initializeScenario(scenario, null);
  }

  public void runScenario(VnScenario scenario, String label) {
    initializeScenario(scenario, label);
  }

  /** Starts preview at the first parsed node on or after the requested source line. */
  public void runScenarioFromSourceLine(VnScenario scenario, int oneBasedLine) {
    renderer.resetParticleState();
    activeError = null;
    stopAudio();
    if (scenario == null) {
      initializeScenario(null, null);
      return;
    }

    VnSettings existingSettings = scene == null ? null : scene.getState().getSettings();
    VnScene nextScene = buildScene(scenario, null, sourceScriptName, existingSettings, false);
    int targetIndex = findLaunchNodeIndexForSourceLine(scenario, Math.max(1, oneBasedLine));
    nextScene.preflightState(targetIndex);
    nextScene.getState().setCurrentNodeIndex(targetIndex);
    this.scene = nextScene;
    this.overlayScene = null;
    phoneRenderer.setSceneModel(null);
    renderer.setAudioFacade(activeAudioFacade());
    nextScene.onEnter();
    storyboardPreviewLine = resolveCurrentStoryboardLine();
    emitStoryboardPreviewLineChanged();
    applyStoryboardUiState();
    requestFocus();
  }

  int getCurrentSourceLine() {
    return resolveCurrentStoryboardLine();
  }

  public void reloadScenarioPreservingPosition(VnScenario scenario) {
    if (scenario == null) {
      initializeScenario(null, null);
      return;
    }
    String anchorLabel = null;
    int currentLine = -1;
    java.util.Map<String, Object> variables = java.util.Map.of();
    boolean uiHidden = false;
    boolean skipMode = false;
    boolean autoPlayMode = false;
    VnSettings existingSettings = scene == null ? null : scene.getState().getSettings();
    if (scene != null && scene.getState() != null) {
      currentLine = resolveCurrentStoryboardLine();
      if (scene.getScenario() != null) {
        anchorLabel = scene.getScenario().findLabelAtOrBefore(scene.getState().getCurrentNodeIndex());
      }
      variables = new java.util.HashMap<>(scene.getState().getVariables());
      uiHidden = scene.getState().isUiHidden();
      skipMode = scene.getState().isSkipMode();
      autoPlayMode = scene.getState().isAutoPlayMode();
    }

    renderer.resetParticleState();
    activeError = null;
    stopAudio();
    VnScene nextScene = buildScene(scenario, null, sourceScriptName, existingSettings, false);
    nextScene.getState().getVariables().putAll(variables);
    nextScene.getState().setUiHidden(uiHidden);
    nextScene.getState().setSkipMode(skipMode);
    nextScene.getState().setAutoPlayMode(autoPlayMode);

    if (anchorLabel != null && scenario.getLabelIndex(anchorLabel) != null) {
      nextScene.getState().jumpToLabel(anchorLabel);
      nextScene.preflightState(nextScene.getState().getCurrentNodeIndex());
    } else if (currentLine > 0) {
      int targetIndex = findNodeIndexForSourceLine(scenario, currentLine);
      if (targetIndex > 0) {
        nextScene.preflightState(targetIndex);
        nextScene.getState().setCurrentNodeIndex(targetIndex);
      }
    }

    this.scene = nextScene;
    this.overlayScene = null;
    phoneRenderer.setSceneModel(null);
    renderer.setAudioFacade(activeAudioFacade());
    this.scene.onEnter();
    storyboardPreviewLine = resolveCurrentStoryboardLine();
    emitStoryboardPreviewLineChanged();
    applyStoryboardUiState();
    requestFocus();
  }

  public void setSourceScriptName(String sourceScriptName) {
    this.sourceScriptName = normalizeScriptKey(sourceScriptName);
    if (scene != null) {
      scene.getState().setSourceScriptName(this.sourceScriptName);
    }
  }

  public void setProjectRoot(File root) {
    String nextBackend = readAudioBackendFromManifest(root);
    boolean rootChanged = !Objects.equals(this.projectRoot, root);
    boolean backendChanged = !Objects.equals(this.audioBackend, nextBackend);
    if (rootChanged || backendChanged) {
      stopAudio();
      audio = null;
    }
    this.audioBackend = nextBackend;
    this.projectRoot = root;
    renderer.setProjectRoot(root);
    menuRenderer.setProjectRoot(root);
    phoneRenderer.setProjectRoot(root);
    resolveVirtualViewport(root);
    applyConfiguredCursor();
    applyUiOverrides();
    bindProjectRoot(audio, root);
    if (scene != null) {
      scene.setAudioFacade(playbackActive ? ensureAudioFacade() : null);
    }
  }

  /**
   * Enables runtime audio only while a preview surface is actually visible.
   * Loading and analysing a VNS document keeps this disabled.
   */
  public void setPlaybackActive(boolean active) {
    if (playbackActive == active) return;
    playbackActive = active;
    if (!active) {
      stopAudio();
      if (scene != null) scene.setAudioFacade(null);
      renderer.setAudioFacade(null);
      return;
    }
    if (scene == null) return;
    AudioFacade activeAudio = ensureAudioFacade();
    scene.setAudioFacade(activeAudio);
    renderer.setAudioFacade(activeAudio);
    restoreAmbientBgm();
  }

  public boolean isPlaybackActive() {
    return playbackActive;
  }

  private void resolveVirtualViewport(File root) {
    ProjectViewportSpec.Dimensions dims = ProjectViewportSpec.resolve(root);
    this.virtualWidth = dims.width();
    this.virtualHeight = dims.height();
  }

  public void setSize(double w, double h) {
    double sw = sanitizeCanvasDimension(w);
    double sh = sanitizeCanvasDimension(h);
    if (Math.abs(canvas.getWidth() - sw) >= 0.5) canvas.setWidth(sw);
    if (Math.abs(canvas.getHeight() - sh) >= 0.5) canvas.setHeight(sh);
  }

  public void setOnStoryboardStateAdjusted(Consumer<StoryboardOverlayState> listener) {
    this.onStoryboardStateAdjusted = listener;
  }

  public void setOnStoryboardPreviewLineChanged(Consumer<Integer> listener) {
    this.onStoryboardPreviewLineChanged = listener;
  }

  public void setOnHotReloadRequested(Runnable listener) {
    this.onHotReloadRequested = listener;
  }

  public int getStoryboardPreviewLine() {
    return storyboardPreviewLine;
  }

  public void setStoryboardOverlay(StoryboardOverlayState storyboardOverlay) {
    boolean wasActive = isStoryboardModeActive();
    this.storyboardOverlay = storyboardOverlay == null ? StoryboardOverlayState.none() : storyboardOverlay;
    syncStoryboardHud();
    applyStoryboardUiState(wasActive);
  }

  public void navigateToStoryboardLine(String sourceText, String sourceName, int oneBasedLine) throws IOException {
    if (sourceText == null || sourceText.isBlank()) return;
    int targetLine = Math.max(1, oneBasedLine);
    VnScenario scenario = parseStoryboardScenario(sourceText, sourceName);
    int targetIndex = findNodeIndexForSourceLine(scenario, targetLine);
    VnSettings existingSettings = scene == null ? null : scene.getState().getSettings();
    this.scene = buildScene(scenario, null, normalizeScriptKey(sourceName), existingSettings, false);
    if (targetIndex > 0) {
      this.scene.preflightState(targetIndex);
      this.scene.getState().setCurrentNodeIndex(targetIndex);
    }
    this.scene.onEnter();
    storyboardPreviewLine = resolveCurrentStoryboardLine();
    emitStoryboardPreviewLineChanged();
    applyStoryboardUiState();
  }

  public void render(long deltaMs) {
    double canvasW = canvas.getWidth();
    double canvasH = canvas.getHeight();
    if (scene == null) {
      renderEmptyPreview(canvasW, canvasH);
      VnErrorOverlay visibleError = resolveVisibleError();
      if (visibleError != null) {
        errorOverlayHoveredButton = renderer.renderErrorOverlay(
            visibleError, canvasW, canvasH, mouseX, mouseY);
      } else {
        errorOverlayHoveredButton = -1;
        gc.setFill(javafx.scene.paint.Color.WHITE);
        gc.fillText("Open a VNS file to preview", 20, 30);
      }
      return;
    }
    scene.update(deltaMs);
    renderer.updateAnimation(deltaMs);
    renderer.setAudioFacade(scene.getAudioFacade());
    syncRequestedOverlayScene();
    if (overlayScene instanceof PhoneScene phone && phone.consumeCloseRequested()) {
      closeOverlayScene();
    }
    gc.setFill(javafx.scene.paint.Color.BLACK);
    gc.fillRect(0, 0, canvasW, canvasH);

    if (isStoryboardModeActive()) {
      renderStoryboardMode(canvasW, canvasH);
    } else {
      double vw = viewportW();
      double vh = viewportH();
      ViewportScaler2D.Transform transform = ViewportScaler2D.fit(vw, vh, canvasW, canvasH);
      double virtualMouseX = transform.screenToLogicalX(mouseX);
      double virtualMouseY = transform.screenToLogicalY(mouseY);

      gc.save();
      gc.translate(transform.offsetX(), transform.offsetY());
      gc.scale(transform.scale(), transform.scale());
      renderer.render(scene.getState(), scene.getScenario(), vw, vh, virtualMouseX, virtualMouseY);
      renderOverlayScene(vw, vh);
      gc.restore();
    }
    syncPhoneOverlay();

    // Render error overlay on top of everything (in screen space)
    VnErrorOverlay visibleError = resolveVisibleError();
    if (visibleError != null) {
      errorOverlayHoveredButton = renderer.renderErrorOverlay(
          visibleError, canvasW, canvasH, mouseX, mouseY);
    } else {
      errorOverlayHoveredButton = -1;
    }
  }

  public void setUiOverrides(VnUiLayoutSpec layout, VnUiStyleSpec style, List<VnUiActionButtonSpec> textBoxButtons) {
    uiLayoutOverride = layout;
    uiStyleOverride = style;
    // Null means "no override", preserving project/runtime defaults.
    textBoxButtonsOverride = textBoxButtons == null ? null : List.copyOf(textBoxButtons);
    renderer.reloadUiLayout();
    applyUiOverrides();
  }

  public void clearUiOverrides() {
    uiLayoutOverride = null;
    uiStyleOverride = null;
    textBoxButtonsOverride = null;
    renderer.reloadUiLayout();
    applyUiOverrides();
  }

  private VnScenario parseStoryboardScenario(String sourceText, String sourceName) throws IOException {
    com.jvn.core.vn.script.VnScriptParser parser = new com.jvn.core.vn.script.VnScriptParser();
    byte[] bytes = sourceText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
      return parser.parse(in, sourceName == null || sourceName.isBlank() ? "<editor>" : sourceName, this::openEditorInclude);
    }
  }

  private InputStream openEditorInclude(String includePath) throws IOException {
    if (includePath == null || includePath.isBlank()) throw new IOException("Empty include path");
    if (projectRoot != null) {
      Path direct = projectRoot.toPath().resolve(includePath).normalize();
      if (Files.isRegularFile(direct)) return Files.newInputStream(direct);
      Path scripts = projectRoot.toPath().resolve("scripts").resolve(includePath).normalize();
      if (Files.isRegularFile(scripts)) return Files.newInputStream(scripts);
    }
    File directFile = new File(includePath);
    if (directFile.isFile()) return new FileInputStream(directFile);
    throw new IOException("Include not found: " + includePath);
  }

  private int findNodeIndexForSourceLine(VnScenario scenario, int sourceLine) {
    if (scenario == null || scenario.getNodes().isEmpty()) return 0;
    int bestIndex = 0;
    int bestLine = Integer.MIN_VALUE;
    List<VnNode> nodes = scenario.getNodes();
    for (int i = 0; i < nodes.size(); i++) {
      VnNode node = nodes.get(i);
      if (node == null) continue;
      int nodeLine = node.getSourceLine();
      if (nodeLine <= 0) continue;
      if (nodeLine <= sourceLine && nodeLine >= bestLine) {
        bestLine = nodeLine;
        bestIndex = i;
      }
    }
    return bestIndex;
  }

  static int findLaunchNodeIndexForSourceLine(VnScenario scenario, int sourceLine) {
    if (scenario == null || scenario.getNodes().isEmpty()) return 0;
    List<VnNode> nodes = scenario.getNodes();
    int firstAfterIndex = -1;
    int firstAfterLine = Integer.MAX_VALUE;
    int lastBeforeIndex = 0;
    int lastBeforeLine = Integer.MIN_VALUE;
    for (int i = 0; i < nodes.size(); i++) {
      VnNode node = nodes.get(i);
      if (node == null || node.getSourceLine() <= 0) continue;
      int nodeLine = node.getSourceLine();
      if (nodeLine >= sourceLine && nodeLine < firstAfterLine) {
        firstAfterLine = nodeLine;
        firstAfterIndex = i;
      }
      if (nodeLine <= sourceLine && nodeLine >= lastBeforeLine) {
        lastBeforeLine = nodeLine;
        lastBeforeIndex = i;
      }
    }
    return firstAfterIndex >= 0 ? firstAfterIndex : lastBeforeIndex;
  }

  private void syncStoryboardHud() {
    applyingStoryboardHud = true;
    try {
      storyboardOpacitySlider.setValue(storyboardOverlay.opacity() * 100.0);
      storyboardOpacityValue.setText(Integer.toString((int) Math.round(storyboardOpacitySlider.getValue())) + "%");
      storyboardHideUiCheck.setSelected(storyboardOverlay.hideUi());
      storyboardHudFrameLabel.setText(
          storyboardOverlay.sourcePath() == null || storyboardOverlay.sourcePath().isBlank()
              ? "No storyboard frame selected"
              : storyboardOverlay.sourcePath());
      storyboardHud.setVisible(false);
      storyboardHud.setManaged(false);
    } finally {
      applyingStoryboardHud = false;
    }
  }

  private boolean isStoryboardModeActive() {
    return storyboardOverlay != null && storyboardOverlay.enabled() && storyboardOverlay.hasImage();
  }

  private void emitStoryboardStateAdjusted() {
    if (onStoryboardStateAdjusted != null) {
      onStoryboardStateAdjusted.accept(storyboardOverlay == null ? StoryboardOverlayState.none() : storyboardOverlay);
    }
  }

  private void emitStoryboardPreviewLineChanged() {
    if (onStoryboardPreviewLineChanged != null && storyboardPreviewLine > 0) {
      onStoryboardPreviewLineChanged.accept(storyboardPreviewLine);
    }
  }

  private int resolveCurrentStoryboardLine() {
    if (scene == null || scene.getState() == null) return -1;
    VnNode node = scene.getState().getCurrentNode();
    return node == null ? -1 : Math.max(-1, node.getSourceLine());
  }

  private void applyStoryboardUiState() {
    applyStoryboardUiState(isStoryboardModeActive());
  }

  private void applyStoryboardUiState(boolean wasActive) {
    boolean active = isStoryboardModeActive();
    if (scene == null) return;
    if (!wasActive && active) {
      storyboardUiHiddenRestore = scene.getState().isUiHidden();
    } else if (wasActive && !active) {
      if (storyboardUiHiddenRestore != null) {
        scene.getState().setUiHidden(storyboardUiHiddenRestore);
      }
      storyboardUiHiddenRestore = null;
      return;
    }
    if (active) {
      scene.getState().setUiHidden(storyboardOverlay.hideUi());
    }
  }

  private void applyUiOverrides() {
    if (uiLayoutOverride != null) renderer.setUiLayout(uiLayoutOverride);
    if (uiStyleOverride != null) renderer.setUiStyle(uiStyleOverride);
    if (textBoxButtonsOverride != null) renderer.setTextBoxButtons(textBoxButtonsOverride);
  }

  private void initializeScenario(VnScenario scenario, String startLabel) {
    renderer.resetParticleState();
    activeError = null;
    if (scenario == null) {
      stopAudio();
      this.scene = null;
      this.overlayScene = null;
      phoneRenderer.setSceneModel(null);
      renderer.setAudioFacade(null);
      storyboardPreviewLine = -1;
      return;
    }
    stopAudio();
    VnSettings existingSettings = scene == null ? null : scene.getState().getSettings();
    VnScene nextScene = buildScene(scenario, startLabel, sourceScriptName, existingSettings);
    this.scene = nextScene;
    this.overlayScene = null;
    phoneRenderer.setSceneModel(null);
    renderer.setAudioFacade(activeAudioFacade());
    storyboardPreviewLine = resolveCurrentStoryboardLine();
    emitStoryboardPreviewLineChanged();
    applyStoryboardUiState();
    requestFocus();
  }

  private VnScene buildScene(VnScenario scenario, String startLabel, String scriptName, VnSettings settingsTemplate) {
    return buildScene(scenario, startLabel, scriptName, settingsTemplate, true);
  }

  private VnScene buildScene(
      VnScenario scenario,
      String startLabel,
      String scriptName,
      VnSettings settingsTemplate,
      boolean enterNow) {
    VnScene nextScene = new VnScene(scenario);
    PreviewVnInterop interop = new PreviewVnInterop();
    com.jvn.core.vn.VnCharacterSceneAccessor accessor = new com.jvn.core.vn.VnCharacterSceneAccessor();
    interop.setSceneAccessor(accessor);
    renderer.setTimelineAccessor(accessor);
    nextScene.setInterop(interop);
    if (playbackActive) {
      nextScene.setAudioFacade(ensureAudioFacade());
    }
    if (settingsTemplate != null) {
      copySettings(settingsTemplate, nextScene.getState().getSettings());
    }
    if (scriptName != null && !scriptName.isBlank()) {
      nextScene.getState().setSourceScriptName(scriptName);
    }
    applyPreviewScriptUiDefaults(nextScene, scenario, scriptName);
    if (startLabel != null && !startLabel.isBlank()) {
      nextScene.getState().jumpToLabel(startLabel);
      nextScene.preflightState(nextScene.getState().getCurrentNodeIndex());
    }
    if (enterNow) {
      nextScene.onEnter();
    }
    return nextScene;
  }

  private void applyPreviewScriptUiDefaults(VnScene nextScene, VnScenario scenario, String scriptName) {
    if (nextScene == null || !isTutorialScriptPreview(scenario, scriptName)) return;
    nextScene.getState().setVariable(VAR_DIALOGUE_PRESENTATION_MODE, "standard");
    nextScene.getState().setVariable(VAR_DIALOGUE_UI, "default");
  }

  private boolean isTutorialScriptPreview(VnScenario scenario, String scriptName) {
    String normalizedScript = normalizeScriptKey(scriptName);
    if (normalizedScript != null) {
      String lower = normalizedScript.toLowerCase(Locale.ROOT);
      if (lower.contains("/tutorial/") || lower.endsWith("scripts/story/tutorial_hub.vns") || lower.endsWith("story/tutorial_hub.vns")) {
        return true;
      }
    }
    String scenarioId = scenario == null ? null : scenario.getId();
    if (scenarioId == null) return false;
    String lowerId = scenarioId.toLowerCase(Locale.ROOT);
    return lowerId.contains("_tutorial_") || lowerId.endsWith("_tutorial_hub") || lowerId.equals("tutorial_hub");
  }

  private void renderOverlayScene(double vw, double vh) {
    if (overlayScene instanceof SaveMenuScene save) {
      menuRenderer.renderSaveMenu(save, vw, vh);
    } else if (overlayScene instanceof LoadMenuScene load) {
      menuRenderer.renderLoadMenu(load, vw, vh);
    } else if (overlayScene instanceof HistoryMenuScene history) {
      menuRenderer.renderHistoryMenu(history, vw, vh);
    } else if (overlayScene instanceof SettingsScene settings) {
      menuRenderer.renderSettings(settings, vw, vh);
    }
  }

  private void renderEmptyPreview(double canvasW, double canvasH) {
    gc.setFill(javafx.scene.paint.Color.BLACK);
    gc.fillRect(0, 0, canvasW, canvasH);
    if (isStoryboardModeActive()) {
      StoryboardCanvasLayout layout = storyboardCanvasLayout(canvasW, canvasH);
      gc.save();
      gc.beginPath();
      gc.rect(layout.viewportX, layout.viewportY, layout.viewportWidth, layout.viewportHeight);
      gc.closePath();
      gc.clip();
      gc.translate(layout.viewportX, layout.viewportY);
      gc.scale(layout.viewportScale, layout.viewportScale);
      gc.setFill(javafx.scene.paint.Color.color(0.06, 0.06, 0.08, 0.96));
      gc.fillRect(0, 0, layout.logicalWidth, layout.logicalHeight);
      gc.restore();
      drawStoryboardOverlay(layout);
      return;
    }
    double vw = viewportW();
    double vh = viewportH();
    ViewportScaler2D.Transform transform = ViewportScaler2D.fit(vw, vh, canvasW, canvasH);
    gc.save();
    gc.translate(transform.offsetX(), transform.offsetY());
    gc.scale(transform.scale(), transform.scale());
    gc.setFill(javafx.scene.paint.Color.color(0.06, 0.06, 0.08));
    gc.fillRect(0, 0, vw, vh);
    gc.restore();
  }

  private void renderStoryboardMode(double canvasW, double canvasH) {
    StoryboardCanvasLayout layout = storyboardCanvasLayout(canvasW, canvasH);
    gc.save();
    gc.beginPath();
    gc.rect(layout.viewportX, layout.viewportY, layout.viewportWidth, layout.viewportHeight);
    gc.closePath();
    gc.clip();
    gc.translate(layout.viewportX, layout.viewportY);
    gc.scale(layout.viewportScale, layout.viewportScale);
    double virtualMouseX = (mouseX - layout.viewportX) / layout.viewportScale;
    double virtualMouseY = (mouseY - layout.viewportY) / layout.viewportScale;
    renderer.render(scene.getState(), scene.getScenario(), layout.logicalWidth, layout.logicalHeight, virtualMouseX, virtualMouseY);
    renderOverlayScene(layout.logicalWidth, layout.logicalHeight);
    gc.restore();
    drawStoryboardOverlay(layout);
  }

  private StoryboardCanvasLayout storyboardCanvasLayout(double canvasW, double canvasH) {
    double logicalWidth = viewportW();
    double logicalHeight = viewportH();
    ViewportScaler2D.Transform viewportTransform =
        ViewportScaler2D.fit(logicalWidth, logicalHeight, canvasW, canvasH);
    double viewportScale = viewportTransform.scale();
    return new StoryboardCanvasLayout(
        viewportTransform.offsetX(),
        viewportTransform.offsetY(),
        viewportTransform.contentWidth(),
        viewportTransform.contentHeight(),
        viewportScale,
        logicalWidth,
        logicalHeight);
  }

  private void drawStoryboardOverlay(StoryboardCanvasLayout layout) {
    if (!isStoryboardModeActive() || layout == null) return;
    gc.save();
    gc.beginPath();
    gc.rect(layout.viewportX, layout.viewportY, layout.viewportWidth, layout.viewportHeight);
    gc.closePath();
    gc.clip();
    gc.setGlobalAlpha(storyboardOverlay.opacity());
    StoryboardOverlayPlacement.Rect placement = StoryboardOverlayPlacement.compute(
        storyboardOverlay,
        layout.viewportX,
        layout.viewportY,
        layout.viewportWidth,
        layout.viewportHeight);
    drawStoryboardImage(placement);
    gc.restore();
  }

  private void drawStoryboardImage(StoryboardOverlayPlacement.Rect placement) {
    if (placement == null || placement.width() <= 0.0 || placement.height() <= 0.0) return;
    Image image = storyboardOverlay.image();
    if (storyboardOverlay.cropEnabled()) {
      double sx = Math.max(0.0, Math.min(image.getWidth(), storyboardOverlay.cropX()));
      double sy = Math.max(0.0, Math.min(image.getHeight(), storyboardOverlay.cropY()));
      double sw = Math.max(1.0, Math.min(image.getWidth() - sx, storyboardOverlay.cropWidth()));
      double sh = Math.max(1.0, Math.min(image.getHeight() - sy, storyboardOverlay.cropHeight()));
      gc.drawImage(image, sx, sy, sw, sh, placement.x(), placement.y(), placement.width(), placement.height());
      return;
    }
    gc.drawImage(image, placement.x(), placement.y(), placement.width(), placement.height());
  }

  private void syncPhoneOverlay() {
    if (overlayScene instanceof PhoneScene phone) {
      phoneRenderer.setSceneModel(phone);
    } else {
      phoneRenderer.setSceneModel(null);
    }
  }

  private void syncRequestedOverlayScene() {
    if (scene == null || overlayScene != null) return;
    var state = scene.getState();
    if (state == null) return;
    if (state.isSaveSlotOverlayShown()) {
      boolean saveMode = state.isSaveSlotOverlaySaveMode();
      state.hideSaveSlotOverlay();
      if (saveMode) {
        overlayScene = new SaveMenuScene(null, previewSaveManager, scene, sourceScriptName);
      } else {
        overlayScene = new LoadMenuScene(null, previewSaveManager, normalizeScriptKey(sourceScriptName), state.getSettings(), audio);
      }
      return;
    }
    if (state.isHistoryOverlayShown()) {
      state.setHistoryOverlayShown(false);
      overlayScene = new HistoryMenuScene(null, scene);
    }
  }

  private void closeOverlayScene() {
    overlayScene = null;
    phoneRenderer.setSceneModel(null);
  }

  private final class PreviewVnInterop extends DefaultVnInterop {
    @Override
    public VnInteropResult handle(VnExternalCommand command, VnScene activeScene) {
      if (command != null && "phone".equalsIgnoreCase(command.getProvider())) {
        return handlePhoneCommand(command.getPayload(), activeScene);
      }
      if (command != null && "vns".equalsIgnoreCase(command.getProvider())) {
        return handleVnsCommand(command.getPayload(), activeScene);
      }
      return super.handle(command, activeScene);
    }
  }

  private VnInteropResult handlePhoneCommand(String payload, VnScene activeScene) {
    VnPhoneCommands.Result result = VnPhoneCommands.handle(payload, activeScene, this::loadPhoneSeed);
    switch (result.action()) {
      case OPEN_HOME -> overlayScene = new PhoneScene(
          activeScene,
          VnPhoneStateStore.load(activeScene.getState(), this::loadPhoneSeed),
          updated -> VnPhoneStateStore.save(activeScene.getState(), updated));
      case OPEN_CHAT -> overlayScene = new PhoneScene(
          activeScene,
          VnPhoneStateStore.load(activeScene.getState(), this::loadPhoneSeed),
          updated -> VnPhoneStateStore.save(activeScene.getState(), updated),
          result.targetId());
      case OPEN_CALL -> {
        PhoneScene phoneScene = new PhoneScene(
            activeScene,
            VnPhoneStateStore.load(activeScene.getState(), this::loadPhoneSeed),
            updated -> VnPhoneStateStore.save(activeScene.getState(), updated));
        phoneScene.openCall(result.targetId());
        overlayScene = phoneScene;
      }
      case CLOSE -> closeOverlayScene();
      case NONE -> {
      }
    }
    syncPhoneOverlay();
    return VnInteropResult.advance();
  }

  private VnInteropResult handleVnsCommand(String payload, VnScene activeScene) {
    List<String> tokens = new ArrayList<>(Arrays.asList(splitPayload(payload)));
    if (tokens.isEmpty()) return VnInteropResult.advance();
    String cmd = tokens.remove(0).toLowerCase(Locale.ROOT);

    if ("goto".equals(cmd)) {
      if (tokens.isEmpty()) return VnInteropResult.advance();
      String target = tokens.remove(0);
      int colon = target.indexOf(':');
      if (colon < 0) {
        activeScene.getState().jumpToLabel(target);
        return VnInteropResult.stay();
      }
      String scriptToken = target.substring(0, colon).trim();
      String label = target.substring(colon + 1).trim();
      String script = resolveVnsScriptTarget(scriptToken);
      if (script == null) return VnInteropResult.advance();
      return switchPreviewScript(script, label, activeScene);
    }

    if ("replace".equals(cmd) || "push".equals(cmd)) {
      if (tokens.isEmpty()) return VnInteropResult.advance();
      String script = resolveVnsScriptTarget(tokens.remove(0));
      String label = null;
      if (!tokens.isEmpty() && "label".equalsIgnoreCase(tokens.get(0))) {
        tokens.remove(0);
        if (!tokens.isEmpty()) label = tokens.remove(0);
      }
      if (script == null) return VnInteropResult.advance();
      return switchPreviewScript(script, label, activeScene);
    }

    activeScene.getState().showHudMessage("Unsupported [vns] command in preview: " + cmd, 1500);
    return VnInteropResult.advance();
  }

  private VnInteropResult switchPreviewScript(String script, String label, VnScene activeScene) {
    try {
      VnScenario loaded = loadScenarioFromScript(script);
      VnSettings settings = activeScene == null ? null : activeScene.getState().getSettings();
      this.sourceScriptName = normalizeScriptKey(script);
      this.scene = buildScene(loaded, label, sourceScriptName, settings, false);
      if (activeScene != null) scene.getState().getVariables().putAll(activeScene.getState().getVariables());
      scene.onEnter();
      renderer.setAudioFacade(activeAudioFacade());
      storyboardPreviewLine = resolveCurrentStoryboardLine();
      emitStoryboardPreviewLineChanged();
      applyStoryboardUiState();
      return VnInteropResult.block();
    } catch (Exception ex) {
      setActiveError(VnErrorOverlay.fromScriptLoadFailure(script, ex));
    }
    return VnInteropResult.advance();
  }

  private VnScenario loadScenarioFromScript(String script) throws IOException {
    VnScenarioLoader loader;
    if (projectRoot != null) {
      loader = new VnScenarioLoader(
          new AssetCatalog(new FilesystemAssetManager(projectRoot.toPath())),
          new com.jvn.core.vn.script.VnScriptParser(),
          "game/scripts/");
    } else {
      loader = new VnScenarioLoader();
    }
    IOException last = null;
    for (String candidate : scriptCandidates(script)) {
      try {
        return loader.load(candidate);
      } catch (IOException ex) {
        last = ex;
      }
    }
    if (last != null) throw last;
    throw new IOException("Script not found: " + script);
  }

  private List<String> scriptCandidates(String script) {
    String normalized = normalizeScriptKey(script);
    if (normalized == null || normalized.isBlank()) return List.of();
    List<String> candidates = new ArrayList<>();
    candidates.add(normalized);
    if (normalized.startsWith("scripts/")) {
      candidates.add(normalized.substring("scripts/".length()));
    } else {
      candidates.add("scripts/" + normalized);
    }
    if (!normalized.contains("/")) {
      candidates.add("story/" + normalized);
    }
    return candidates.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
  }

  private String resolveVnsScriptTarget(String token) {
    String normalized = normalizeScriptKey(token);
    if (normalized == null || normalized.isBlank()) return null;
    if (!normalized.endsWith(".vns")) {
      String fromTimeline = resolveTimelineArcScript(normalized);
      if (fromTimeline != null && !fromTimeline.isBlank()) {
        return normalizeScriptKey(fromTimeline);
      }
      normalized = normalized + ".vns";
    }
    return normalized;
  }

  private String resolveTimelineArcScript(String arcName) {
    if (projectRoot == null || arcName == null || arcName.isBlank()) return null;
    File timeline = StoryMapPaths.resolveForProjectRoot(projectRoot);
    if (timeline == null) return null;
    if (!timeline.isFile()) return null;
    try {
      for (String line : Files.readAllLines(timeline.toPath())) {
        if (line == null) continue;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
        Matcher m = TIMELINE_ARC_PATTERN.matcher(trimmed);
        if (!m.matches()) continue;
        String arc = m.group(1) != null ? m.group(1) : m.group(2);
        String script = m.group(3) != null ? m.group(3) : m.group(4);
        if (arcName.equalsIgnoreCase(safe(arc))) {
          return script;
        }
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return null;
  }

  private String[] splitPayload(String payload) {
    if (payload == null || payload.isBlank()) return new String[0];
    return payload.trim().split("\\s+");
  }

  private String normalizeScriptKey(String raw) {
    if (raw == null) return null;
    String normalized = raw.trim().replace('\\', '/');
    while (normalized.startsWith("/")) normalized = normalized.substring(1);
    return normalized;
  }

  private String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private void applyConfiguredCursor() {
    if (projectRoot == null) {
      applyCursor(Cursor.DEFAULT);
      return;
    }

    VnCursorConfigLoader.LoadResult load = VnCursorConfigLoader.loadFromProjectRootWithDiagnostics(projectRoot);
    VnCursorConfigLoader.VnCursorConfig cfg = load.config();
    if (cfg == null || cfg.assetPath() == null || cfg.assetPath().isBlank()) {
      applyCursor(Cursor.DEFAULT);
      return;
    }

    Image image = loadCursorImage(cfg.assetPath());
    if (image == null || image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
      applyCursor(Cursor.DEFAULT);
      return;
    }

    double hotspotX = Math.max(0.0, Math.min(cfg.hotspotX(), Math.max(0.0, image.getWidth() - 1)));
    double hotspotY = Math.max(0.0, Math.min(cfg.hotspotY(), Math.max(0.0, image.getHeight() - 1)));
    applyCursor(new ImageCursor(image, hotspotX, hotspotY));
  }

  private Image loadCursorImage(String path) {
    if (path == null || path.isBlank()) return null;
    try {
      if (projectRoot != null) {
        AssetCatalog assets = new AssetCatalog(new FilesystemAssetManager(projectRoot.toPath()));
        var url = assets.url(AssetType.IMAGE, path);
        if (url != null) {
          Image image = new Image(url.toExternalForm());
          if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0) return image;
        }
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }

    try {
      File direct = new File(path);
      if (!direct.isAbsolute() && projectRoot != null) {
        direct = new File(projectRoot, path);
      }
      if (direct.exists()) {
        Image image = new Image(direct.toURI().toString());
        if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0) return image;
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return null;
  }

  private void applyCursor(Cursor cursor) {
    configuredCursor = cursor == null ? Cursor.DEFAULT : cursor;
    setCursor(configuredCursor);
    canvas.setCursor(configuredCursor);
    phoneRenderer.setCursor(configuredCursor);
  }

  private void updateOverlayHover(double x, double y) {
    double vx = toVirtualX(x);
    double vy = toVirtualY(y);
    double vw = viewportW();
    double vh = viewportH();
    if (overlayScene instanceof SaveMenuScene save) {
      int idx = menuRenderer.getHoverIndexForSaveMenu(save, vw, vh, vx, vy);
      if (idx >= 0) save.setSelected(idx);
    } else if (overlayScene instanceof LoadMenuScene load) {
      int idx = menuRenderer.getHoverIndexForLoadMenu(load, vw, vh, vx, vy);
      if (idx >= 0) load.setSelected(idx);
    }
  }

  private void handleMouseClick(MouseButton button, int clickCount, double x, double y) {
    mouseX = x;
    mouseY = y;
    requestFocus();
    if (button != MouseButton.PRIMARY) return;
    if (suppressNextStoryboardClick) {
      suppressNextStoryboardClick = false;
      return;
    }

    // Handle error overlay button clicks
    if (resolveVisibleError() != null) {
      if (errorOverlayHoveredButton >= 0) {
        handleErrorOverlayButton(errorOverlayHoveredButton);
      }
      return;
    }

    if (scene == null) return;
    if (isStoryboardModeActive() && !isInsideStoryboardViewport(x, y)) return;

    if (handleOverlayMouseClick(clickCount, x, y)) return;

    double vx = toVirtualX(x);
    double vy = toVirtualY(y);
    double vw = viewportW();
    double vh = viewportH();

    com.jvn.core.vn.ui.VnUiActionButtonSpec textBoxButton =
        renderer.getHoveredTextBoxButton(scene.getState(), vw, vh, vx, vy);
    if (textBoxButton != null && executeTextBoxButtonAction(textBoxButton)) {
      syncStoryboardLineFromScene();
      return;
    }

    VnNode node = scene.getState().getCurrentNode();
    if (node != null && node.getType() == VnNodeType.CHOICE) {
      int idx = renderer.getHoveredChoiceIndex(node.getChoices(), vw, vh, vx, vy);
      if (idx >= 0) {
        scene.selectChoice(idx);
        syncStoryboardLineFromScene();
      }
      return;
    }

    scene.advanceFromClick();
    syncStoryboardLineFromScene();
  }

  private void handleStoryboardOffsetPress(MouseEvent event) {
    if (!isStoryboardModeActive() || event.getButton() != MouseButton.PRIMARY) return;
    if (!isInsideStoryboardImage(event.getX(), event.getY())) return;
    storyboardOffsetDragging = true;
    storyboardOffsetDragMoved = false;
    storyboardDragStartX = event.getX();
    storyboardDragStartY = event.getY();
    storyboardDragStartOffsetX = storyboardOverlay.offsetX();
    storyboardDragStartOffsetY = storyboardOverlay.offsetY();
    requestFocus();
  }

  private void handleStoryboardOffsetDrag(MouseEvent event) {
    if (!storyboardOffsetDragging || !isStoryboardModeActive()) return;
    StoryboardCanvasLayout layout = storyboardCanvasLayout(canvas.getWidth(), canvas.getHeight());
    double runtimeWidth = storyboardOverlay.runtimeWidth() > 0.0 ? storyboardOverlay.runtimeWidth() : layout.logicalWidth;
    double runtimeHeight = storyboardOverlay.runtimeHeight() > 0.0 ? storyboardOverlay.runtimeHeight() : layout.logicalHeight;
    double scaleX = layout.viewportWidth / Math.max(1.0, runtimeWidth);
    double scaleY = layout.viewportHeight / Math.max(1.0, runtimeHeight);
    double nextOffsetX = storyboardDragStartOffsetX + (event.getX() - storyboardDragStartX) / Math.max(1e-9, scaleX);
    double nextOffsetY = storyboardDragStartOffsetY + (event.getY() - storyboardDragStartY) / Math.max(1e-9, scaleY);
    if (Math.abs(nextOffsetX - storyboardOverlay.offsetX()) < 0.01
        && Math.abs(nextOffsetY - storyboardOverlay.offsetY()) < 0.01) {
      return;
    }
    storyboardOffsetDragMoved = true;
    storyboardOverlay = storyboardWithOffset(nextOffsetX, nextOffsetY);
    emitStoryboardStateAdjusted();
    event.consume();
  }

  private void handleStoryboardOffsetRelease(MouseEvent event) {
    if (!storyboardOffsetDragging || event.getButton() != MouseButton.PRIMARY) return;
    storyboardOffsetDragging = false;
    suppressNextStoryboardClick = storyboardOffsetDragMoved;
    storyboardOffsetDragMoved = false;
    event.consume();
  }

  private boolean isInsideStoryboardImage(double canvasX, double canvasY) {
    if (!isStoryboardModeActive()) return false;
    StoryboardCanvasLayout layout = storyboardCanvasLayout(canvas.getWidth(), canvas.getHeight());
    StoryboardOverlayPlacement.Rect placement = StoryboardOverlayPlacement.compute(
        storyboardOverlay,
        layout.viewportX,
        layout.viewportY,
        layout.viewportWidth,
        layout.viewportHeight);
    return placement != null
        && canvasX >= placement.x()
        && canvasX <= placement.x() + placement.width()
        && canvasY >= placement.y()
        && canvasY <= placement.y() + placement.height();
  }

  private StoryboardOverlayState storyboardWithOffset(double offsetX, double offsetY) {
    return new StoryboardOverlayState(
        true,
        storyboardOverlay.image(),
        storyboardOverlay.opacity(),
        storyboardOverlay.sourcePath(),
        storyboardOverlay.hideUi(),
        storyboardOverlay.fitMode(),
        storyboardOverlay.runtimeWidth(),
        storyboardOverlay.runtimeHeight(),
        storyboardOverlay.storyboardWidth(),
        storyboardOverlay.storyboardHeight(),
        storyboardOverlay.scale(),
        offsetX,
        offsetY,
        storyboardOverlay.cropEnabled(),
        storyboardOverlay.cropX(),
        storyboardOverlay.cropY(),
        storyboardOverlay.cropWidth(),
        storyboardOverlay.cropHeight());
  }

  private void syncStoryboardLineFromScene() {
    storyboardPreviewLine = resolveCurrentStoryboardLine();
    emitStoryboardPreviewLineChanged();
  }

  private void syncStoryboardHideUiFromScene() {
    if (!isStoryboardModeActive() || scene == null) return;
    storyboardOverlay = new StoryboardOverlayState(
        true,
        storyboardOverlay.image(),
        storyboardOverlay.opacity(),
        storyboardOverlay.sourcePath(),
        scene.getState().isUiHidden(),
        storyboardOverlay.fitMode(),
        storyboardOverlay.runtimeWidth(),
        storyboardOverlay.runtimeHeight(),
        storyboardOverlay.storyboardWidth(),
        storyboardOverlay.storyboardHeight(),
        storyboardOverlay.scale(),
        storyboardOverlay.offsetX(),
        storyboardOverlay.offsetY(),
        storyboardOverlay.cropEnabled(),
        storyboardOverlay.cropX(),
        storyboardOverlay.cropY(),
        storyboardOverlay.cropWidth(),
        storyboardOverlay.cropHeight());
    syncStoryboardHud();
    emitStoryboardStateAdjusted();
  }

  private boolean executeTextBoxButtonAction(com.jvn.core.vn.ui.VnUiActionButtonSpec button) {
    if (scene == null || button == null || !button.enabled()) return false;
    String action = normalizeButtonAction(button.action());
    var state = scene.getState();
    switch (action) {
      case "advance" -> {
        scene.advanceFromClick();
        return true;
      }
      case "quick_save", "save_quick" -> {
        saveToSlot(0);
        return true;
      }
      case "quick_load", "load_quick" -> {
        loadFromSlot(0);
        return true;
      }
      case "save_slots", "open_save_slots", "save_menu", "open_save_menu", "menu_save" -> {
        overlayScene = new SaveMenuScene(null, previewSaveManager, scene, sourceScriptName);
        return true;
      }
      case "load_slots", "open_load_slots", "load_menu", "open_load_menu", "menu_load" -> {
        overlayScene = new LoadMenuScene(null, previewSaveManager, normalizeScriptKey(sourceScriptName), state.getSettings(), audio);
        return true;
      }
      case "toggle_history", "history" -> {
        if (overlayScene instanceof HistoryMenuScene) {
          closeOverlayScene();
        } else {
          state.clearHistoryScroll();
          overlayScene = new HistoryMenuScene(null, scene);
        }
        return true;
      }
      case "toggle_skip", "skip" -> {
        scene.toggleSkipMode();
        return true;
      }
      case "toggle_auto", "auto" -> {
        scene.toggleAutoPlayMode();
        return true;
      }
      case "toggle_ui", "ui" -> {
        state.toggleUiHidden();
        syncStoryboardHideUiFromScene();
        return true;
      }
      case "settings_menu", "open_settings_menu", "menu_settings" -> {
        overlayScene = createPreviewSettingsScene("settings");
        return true;
      }
      case "open_menu", "menu_open" -> {
        String target = button.target() == null ? "" : button.target().trim();
        if (target.equalsIgnoreCase("settings")
            || target.equalsIgnoreCase("settings_audio")
            || target.equalsIgnoreCase("settings_controls")) {
          overlayScene = createPreviewSettingsScene(target);
          return true;
        }
        state.showHudMessage("Preview menu not available: " + (target.isBlank() ? "(empty)" : target), 1400);
        return true;
      }
      case "noop", "none" -> {
        return true;
      }
      default -> {
        state.showHudMessage("Unsupported preview action: " + action, 1400);
        return true;
      }
    }
  }

  private static String normalizeButtonAction(String raw) {
    if (raw == null || raw.isBlank()) return "noop";
    return raw.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
  }

  private boolean handleOverlayMouseClick(int clickCount, double x, double y) {
    double vx = toVirtualX(x);
    double vy = toVirtualY(y);
    double vw = viewportW();
    double vh = viewportH();

    if (overlayScene instanceof SaveMenuScene save) {
      int idx = menuRenderer.getHoverIndexForSaveMenu(save, vw, vh, vx, vy);
      if (idx < 0) {
        closeOverlayScene();
        return true;
      }
      int previous = save.getSelected();
      save.setSelected(idx);
      if (clickCount >= 2 || previous == idx) {
        confirmOverlayAction();
      }
      return true;
    }

    if (overlayScene instanceof LoadMenuScene load) {
      int idx = menuRenderer.getHoverIndexForLoadMenu(load, vw, vh, vx, vy);
      if (idx < 0) {
        closeOverlayScene();
        return true;
      }
      int previous = load.getSelected();
      load.setSelected(idx);
      if (clickCount >= 2 || previous == idx) {
        confirmOverlayAction();
      }
      return true;
    }

    if (overlayScene instanceof HistoryMenuScene) {
      closeOverlayScene();
      return true;
    }

    if (overlayScene instanceof SettingsScene settings) {
      int idx = menuRenderer.getHoverIndexForSettings(settings, vw, vh, vx, vy);
      if (idx < 0) {
        closeOverlayScene();
        return true;
      }
      settings.setSelected(idx);
      if (!settings.hasSliderAt(idx)) {
        settings.toggleCurrent();
      } else if (menuRenderer.isSettingsSliderResetHit(settings, idx, vw, vh, vx, vy)) {
        settings.resetValueByIndex(idx);
      } else {
        double value = menuRenderer.computeSettingsSliderValue01(settings, idx, vw, vh, vx);
        settings.setValueByIndex(idx, value);
      }
      if (settings.consumeCloseRequested()) {
        closeOverlayScene();
      } else {
        syncRequestedSettingsOverlay();
      }
      return true;
    }

    return false;
  }

  private void handleKeyPressed(KeyEvent e) {
    KeyCode code = e.getCode();
    if (e.isShiftDown() && code == KeyCode.R) {
      if (onHotReloadRequested != null) {
        onHotReloadRequested.run();
        e.consume();
      }
      return;
    }
    if (scene == null) return;
    var state = scene.getState();

    if (overlayScene != null) {
      if (overlayScene instanceof PhoneScene phone) {
        if (phoneRenderer.handleKeyPressed(code, e.isShiftDown())) {
          if (phone.consumeCloseRequested()) {
            closeOverlayScene();
          } else {
            syncPhoneOverlay();
          }
          e.consume();
          return;
        }
      }
      handleOverlayKey(code, e.isShiftDown());
      e.consume();
      return;
    }

    if (trySelectChoiceByDigit(code)) {
      e.consume();
      return;
    }

    if (code == KeyCode.SPACE || code == KeyCode.ENTER) {
      advanceFromInput();
      e.consume();
    } else if (code == KeyCode.CONTROL || code == KeyCode.COMMAND) {
      scene.toggleSkipMode();
      e.consume();
    } else if (code == KeyCode.A) {
      scene.toggleAutoPlayMode();
      e.consume();
    } else if (code == KeyCode.H) {
      state.toggleUiHidden();
      syncStoryboardHideUiFromScene();
      e.consume();
    } else if (code == KeyCode.B) {
      state.clearHistoryScroll();
      overlayScene = new HistoryMenuScene(null, scene);
      e.consume();
    } else if (code == KeyCode.F5) {
      overlayScene = new SaveMenuScene(null, previewSaveManager, scene, sourceScriptName);
      e.consume();
    } else if (code == KeyCode.F9) {
      overlayScene = new LoadMenuScene(null, previewSaveManager, normalizeScriptKey(sourceScriptName), state.getSettings(), audio);
      e.consume();
    } else if (code == KeyCode.ESCAPE) {
      closeOverlayScene();
      e.consume();
    }
  }

  private void handleOverlayKey(KeyCode code, boolean shiftDown) {
    if (overlayScene instanceof HistoryMenuScene history) {
      int pageLines = history.linesPerPage(viewportH());
      int step = shiftDown ? 5 : 1;
      if (code == KeyCode.ESCAPE || code == KeyCode.SPACE || code == KeyCode.ENTER || code == KeyCode.B) {
        closeOverlayScene();
      } else if (code == KeyCode.UP) {
        history.scrollByLines(step);
      } else if (code == KeyCode.DOWN) {
        history.scrollByLines(-step);
      } else if (code == KeyCode.PAGE_UP) {
        history.scrollByLines(pageLines);
      } else if (code == KeyCode.PAGE_DOWN) {
        history.scrollByLines(-pageLines);
      }
      return;
    }

    if (overlayScene instanceof SaveMenuScene save) {
      if (code == KeyCode.ESCAPE) {
        closeOverlayScene();
        return;
      }
      if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
        confirmOverlayAction();
        return;
      }
      if (code == KeyCode.UP) {
        save.moveSelection(-1);
        return;
      }
      if (code == KeyCode.DOWN) {
        save.moveSelection(1);
        return;
      }
      return;
    }

    if (overlayScene instanceof LoadMenuScene load) {
      if (code == KeyCode.ESCAPE) {
        closeOverlayScene();
        return;
      }
      if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
        confirmOverlayAction();
        return;
      }
      if (code == KeyCode.UP) {
        load.moveSelection(-1);
        return;
      }
      if (code == KeyCode.DOWN) {
        load.moveSelection(1);
      }
      return;
    }

    if (overlayScene instanceof SettingsScene settings) {
      if (code == KeyCode.ESCAPE) {
        closeOverlayScene();
        return;
      }
      if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
        settings.toggleCurrent();
      } else if (code == KeyCode.UP) {
        settings.moveSelection(-1);
      } else if (code == KeyCode.DOWN) {
        settings.moveSelection(1);
      } else if (code == KeyCode.LEFT) {
        settings.adjustCurrent(-1);
      } else if (code == KeyCode.RIGHT) {
        settings.adjustCurrent(1);
      }
      if (settings.consumeCloseRequested()) {
        closeOverlayScene();
      } else {
        syncRequestedSettingsOverlay();
      }
    }
  }

  private void handleScroll(ScrollEvent e) {
    if (scene == null) return;
    if (overlayScene instanceof PhoneScene phone) {
      phoneRenderer.scrollContent(e.getDeltaY(), e.isShiftDown());
      if (phone.consumeCloseRequested()) {
        closeOverlayScene();
      } else {
        syncPhoneOverlay();
      }
      e.consume();
      return;
    }
    if (overlayScene instanceof HistoryMenuScene history) {
      int step = e.isShiftDown() ? 6 : 2;
      if (e.getDeltaY() > 0) {
        history.scrollByLines(step);
      } else if (e.getDeltaY() < 0) {
        history.scrollByLines(-step);
      }
      e.consume();
    }
  }

  private void advanceFromInput() {
    VnNode node = scene.getState().getCurrentNode();
    if (node != null && node.getType() == VnNodeType.CHOICE) {
      int hover = renderer.getHoveredChoiceIndex(node.getChoices(), viewportW(), viewportH(), toVirtualX(mouseX), toVirtualY(mouseY));
      if (hover >= 0) {
        scene.selectChoice(hover);
        syncStoryboardLineFromScene();
      }
      return;
    }
    scene.advance();
    syncStoryboardLineFromScene();
  }

  private boolean trySelectChoiceByDigit(KeyCode code) {
    VnNode node = scene.getState().getCurrentNode();
    if (node == null || node.getType() != VnNodeType.CHOICE) return false;
    int digit = toDigit(code);
    if (digit <= 0) return false; // choices are 1-based
    int idx = digit - 1;
    if (idx >= 0 && idx < node.getChoices().size()) {
      scene.selectChoice(idx);
      syncStoryboardLineFromScene();
      return true;
    }
    return false;
  }

  private void confirmOverlayAction() {
    if (overlayScene instanceof SaveMenuScene save) {
      if (!save.activateSelectedWithoutPrompt()) {
        String slotName = save.isNewItemSelected()
            ? save.saveNew(save.generateSaveName())
            : save.saveOverwriteSelected();
        if (slotName != null) {
          closeOverlayScene();
        }
      }
      return;
    }
    if (overlayScene instanceof LoadMenuScene load) {
      String saveName = load.getSelectedName();
      if (saveName != null) {
        loadFromSaveName(saveName);
        closeOverlayScene();
      }
      return;
    }
    if (overlayScene instanceof SettingsScene settings) {
      settings.toggleCurrent();
      if (settings.consumeCloseRequested()) {
        closeOverlayScene();
      } else {
        syncRequestedSettingsOverlay();
      }
    }
  }

  private SettingsScene createPreviewSettingsScene(String menuId) {
    return createPreviewSettingsScene(menuId, null);
  }

  private SettingsScene createPreviewSettingsScene(String menuId, String preferredSelectionKey) {
    SettingsScene previewScene;
    MenuProfile profile = loadPreviewMenuProfile();
    if (profile != null && profile.hasScreen(menuId)) {
      previewScene = new SettingsScene(scene.getState().getSettings(), audio, profile, menuId);
    } else {
      previewScene = new SettingsScene(scene.getState().getSettings(), audio);
    }
    previewScene.preferSelectionKey(preferredSelectionKey);
    return previewScene;
  }

  private void syncRequestedSettingsOverlay() {
    if (!(overlayScene instanceof SettingsScene settings) || scene == null) return;
    String requestedMenuId = settings.consumeRequestedMenuId();
    if (requestedMenuId == null || requestedMenuId.isBlank()) return;
    overlayScene = createPreviewSettingsScene(requestedMenuId, settings.consumeRequestedSelectionKey());
  }

  private MenuProfile loadPreviewMenuProfile() {
    if (projectRoot == null) return null;
    try {
      MenuProfileLoader.LoadResult load = MenuProfileLoader.loadWithDiagnostics(
          new AssetCatalog(new FilesystemAssetManager(projectRoot.toPath())));
      return load.profile();
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return null;
    }
  }

  private void saveToSlot(int slot) {
    String slotName = slot == 0 ? "_quicksave" : ("slot_" + slot);
    try {
      previewSaveManager.save(scene.getState(), slotName);
      scene.getState().showHudMessage("Saved to " + slotLabel(slot), 1500);
    } catch (Exception e) {
      scene.getState().showHudMessage("Save failed", 1500);
    }
  }

  private void loadFromSlot(int slot) {
    String slotName = slot == 0 ? "_quicksave" : ("slot_" + slot);
    loadFromSaveName(slotName, slot == 0 ? "No quick save found" : ("Slot " + slot + " is empty"), "Loaded from " + slotLabel(slot));
  }

  private void loadFromSaveName(String saveName) {
    loadFromSaveName(saveName, "Save is empty", "Loaded from " + saveName);
  }

  private void loadFromSaveName(String saveName, String missingMessage, String successMessage) {
    try {
      var saveData = previewSaveManager.load(saveName);
      String expectedScenarioId = scene.getScenario() != null ? scene.getScenario().getId() : null;
      if (!Objects.equals(saveData.getScenarioId(), expectedScenarioId)) {
        scene.getState().showHudMessage("Save is for different scenario", 1800);
        return;
      }
      previewSaveManager.applyToState(saveData, scene.getState());
      if (audio != null) {
        var s = scene.getState().getSettings();
        audio.setBgmVolume(s.getBgmVolume());
        audio.setSfxVolume(s.getSfxVolume());
        audio.setVoiceVolume(s.getVoiceVolume());
      }
      scene.getState().showHudMessage(successMessage, 1500);
    } catch (Exception e) {
      scene.getState().showHudMessage(missingMessage, 1800);
    }
  }

  private String slotLabel(int slot) {
    return slot == 0 ? "Quick Save" : ("Slot " + slot);
  }

  private int toDigit(KeyCode code) {
    return switch (code) {
      case DIGIT0, NUMPAD0 -> 0;
      case DIGIT1, NUMPAD1 -> 1;
      case DIGIT2, NUMPAD2 -> 2;
      case DIGIT3, NUMPAD3 -> 3;
      case DIGIT4, NUMPAD4 -> 4;
      case DIGIT5, NUMPAD5 -> 5;
      case DIGIT6, NUMPAD6 -> 6;
      case DIGIT7, NUMPAD7 -> 7;
      case DIGIT8, NUMPAD8 -> 8;
      case DIGIT9, NUMPAD9 -> 9;
      default -> -1;
    };
  }

  // ── Virtual viewport helpers ──────────────────────────────────────

  private double viewportW() { return virtualWidth > 0 ? virtualWidth : canvas.getWidth(); }
  private double viewportH() { return virtualHeight > 0 ? virtualHeight : canvas.getHeight(); }

  private double viewportScale() {
    if (isStoryboardModeActive()) {
      return storyboardCanvasLayout(canvas.getWidth(), canvas.getHeight()).viewportScale;
    }
    return Math.min(canvas.getWidth() / viewportW(), canvas.getHeight() / viewportH());
  }

  private double toVirtualX(double canvasX) {
    if (isStoryboardModeActive()) {
      StoryboardCanvasLayout layout = storyboardCanvasLayout(canvas.getWidth(), canvas.getHeight());
      return (canvasX - layout.viewportX) / Math.max(1e-9, layout.viewportScale);
    }
    double scale = viewportScale();
    double offsetX = (canvas.getWidth() - viewportW() * scale) / 2.0;
    return (canvasX - offsetX) / scale;
  }

  private double toVirtualY(double canvasY) {
    if (isStoryboardModeActive()) {
      StoryboardCanvasLayout layout = storyboardCanvasLayout(canvas.getWidth(), canvas.getHeight());
      return (canvasY - layout.viewportY) / Math.max(1e-9, layout.viewportScale);
    }
    double scale = viewportScale();
    double offsetY = (canvas.getHeight() - viewportH() * scale) / 2.0;
    return (canvasY - offsetY) / scale;
  }

  private boolean isInsideStoryboardViewport(double canvasX, double canvasY) {
    if (!isStoryboardModeActive()) return true;
    StoryboardCanvasLayout layout = storyboardCanvasLayout(canvas.getWidth(), canvas.getHeight());
    return canvasX >= layout.viewportX
        && canvasX <= layout.viewportX + layout.viewportWidth
        && canvasY >= layout.viewportY
        && canvasY <= layout.viewportY + layout.viewportHeight;
  }

  private static double sanitizeCanvasDimension(double value) {
    if (!Double.isFinite(value)) return 1.0;
    return Math.max(1.0, Math.min(8192.0, value));
  }

  private record StoryboardCanvasLayout(
      double viewportX,
      double viewportY,
      double viewportWidth,
      double viewportHeight,
      double viewportScale,
      double logicalWidth,
      double logicalHeight) {
  }

  private void copySettings(VnSettings src, VnSettings dst) {
    if (src == null || dst == null) return;
    dst.copyFrom(src);
  }

  private VnPhoneData loadPhoneSeed() {
    try {
      if (projectRoot != null) {
        File direct = new File(projectRoot, "config/phone/phone.properties");
        if (direct.isFile()) {
          try (InputStream in = new FileInputStream(direct)) {
            return VnPhonePropertiesCodec.load(in);
          }
        }
        File gamePath = new File(projectRoot, "game/config/phone/phone.properties");
        if (gamePath.isFile()) {
          try (InputStream in = new FileInputStream(gamePath)) {
            return VnPhonePropertiesCodec.load(in);
          }
        }
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return VnPhonePropertiesCodec.loadSeedFromAssets();
  }

  public void stopAudio() {
    if (audio != null) {
      try {
        audio.stopAllAudio();
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
    }
  }

  /** Releases reloadable preview caches without closing the editor tab. */
  public void trimMemoryCaches() {
    renderer.clearCache();
    menuRenderer.clearImageCache();
    menuRenderer.clearTextMeasureCache();
    phoneRenderer.clearAssetCache();
  }

  public void dispose() {
    playbackActive = false;
    stopAudio();
    scene = null;
    overlayScene = null;
    phoneRenderer.setSceneModel(null);
    projectRoot = null;
    audio = null;
    renderer.setAudioFacade(null);
    renderer.dispose();
    menuRenderer.dispose();
    phoneRenderer.dispose();
  }

  private AudioFacade activeAudioFacade() {
    return playbackActive ? audio : null;
  }

  private AudioFacade ensureAudioFacade() {
    if (audio == null) {
      audio = createAudioFacade(projectRoot, audioBackend);
    }
    bindProjectRoot(audio, projectRoot);
    return audio;
  }

  private void restoreAmbientBgm() {
    if (!playbackActive || audio == null || scene == null || scene.getScenario() == null) return;
    stopAudio();

    VnAudioCommand ambient = null;
    List<VnNode> nodes = scene.getScenario().getNodes();
    int limit = Math.min(Math.max(0, scene.getState().getCurrentNodeIndex()), nodes.size());
    for (int i = 0; i < limit; i++) {
      VnNode node = nodes.get(i);
      if (node == null || node.getType() != VnNodeType.AUDIO || node.getAudioCommand() == null) continue;
      VnAudioCommand command = node.getAudioCommand();
      switch (command.getType()) {
        case PLAY_BGM -> ambient = command;
        case STOP_BGM, FADE_OUT_BGM -> ambient = null;
        default -> {
          // Historical voice and SFX commands must not replay when the preview becomes visible.
        }
      }
    }

    VnSettings settings = scene.getState().getSettings();
    audio.setBgmVolume(settings.getBgmVolume());
    audio.setSfxVolume(settings.getSfxVolume());
    audio.setVoiceVolume(settings.getVoiceVolume());
    if (ambient != null && ambient.getTrackId() != null && !ambient.getTrackId().isBlank()) {
      audio.playBgm(ambient.getTrackId(), ambient.isLoop());
    }
  }

  static String normalizeAudioBackendValue(String raw) {
    if (raw == null || raw.isBlank()) return "auto";
    String key = raw.trim().toLowerCase(Locale.ROOT);
    if ("fx".equals(key) || "javafx".equals(key)) return "fx";
    if ("simp3".equals(key) || "simp".equals(key)) return "simp3";
    return "auto";
  }

  static String resolveAudioBackend(Properties manifest) {
    if (manifest == null) return "auto";
    return normalizeAudioBackendValue(manifest.getProperty("runtime.audio", "auto"));
  }

  private static String readAudioBackendFromManifest(File root) {
    if (root == null || !root.isDirectory()) return "auto";
    File manifest = new File(root, "jvn.project");
    if (!manifest.isFile()) return "auto";
    Properties props = new Properties();
    try (InputStream in = new FileInputStream(manifest)) {
      props.load(in);
      return resolveAudioBackend(props);
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return "auto";
    }
  }

  private AudioFacade createAudioFacade(File root, String backend) {
    String selected = normalizeAudioBackendValue(backend);
    AudioFacade facade;
    if ("fx".equals(selected)) {
      FxAudioService fx = new FxAudioService();
      fx.setProjectRoot(root);
      return fx;
    }
    try {
      Simp3AudioService simp3 = new Simp3AudioService();
      simp3.setProjectRoot(root);
      facade = simp3;
    } catch (Throwable t) {
      FxAudioService fx = new FxAudioService();
      fx.setProjectRoot(root);
      facade = fx;
    }
    return facade;
  }

  private void bindProjectRoot(AudioFacade facade, File root) {
    if (facade instanceof FxAudioService fx) {
      fx.setProjectRoot(root);
    } else if (facade instanceof Simp3AudioService simp3) {
      simp3.setProjectRoot(root);
    }
  }

  // ─── Error Overlay ─────────────────────────────────────────────────

  /** Set an error to display as a full-screen overlay (e.g. parse error from editor). */
  public void setActiveError(VnErrorOverlay error) {
    this.activeError = error;
  }

  /** Clear any externally-set error overlay. */
  public void clearActiveError() {
    this.activeError = null;
    if (scene != null) scene.clearActiveError();
  }

  /** Resolve which error to display: prefer externally-set error, then scene runtime error. */
  private VnErrorOverlay resolveVisibleError() {
    if (activeError != null) return activeError;
    if (scene != null && scene.hasActiveError()) return scene.getActiveError();
    return null;
  }

  /** Handle button clicks on the error overlay (0=Ignore, 1=Reload, 2=Copy). */
  private void handleErrorOverlayButton(int buttonIndex) {
    switch (buttonIndex) {
      case 0 -> { // Ignore — dismiss the overlay
        if (activeError != null) {
          activeError = null;
        } else if (scene != null) {
          scene.clearActiveError();
        }
      }
      case 1 -> { // Reload — re-load the current scenario
        if (activeError != null) activeError = null;
        if (scene != null) {
          scene.clearActiveError();
          String scriptName = this.sourceScriptName;
          if (scriptName != null && projectRoot != null) {
            reloadCurrentScript();
          }
        }
      }
      case 2 -> { // Copy — copy error details to clipboard
        VnErrorOverlay error = resolveVisibleError();
        if (error != null) {
          String text = error.toDisplaySummary();
          javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
          javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
          content.putString(text);
          clipboard.setContent(content);
        }
      }
    }
  }

  private void reloadCurrentScript() {
    if (projectRoot == null || sourceScriptName == null) return;
    try {
      VnScenario loaded = loadScenarioFromScript(sourceScriptName);
      if (loaded != null) {
        initializeScenario(loaded, null);
      }
    } catch (Exception e) {
      setActiveError(VnErrorOverlay.fromScriptLoadFailure(sourceScriptName, e));
    }
  }
}
