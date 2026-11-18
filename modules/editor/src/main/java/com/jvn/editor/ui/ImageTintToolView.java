package com.jvn.editor.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import com.jvn.core.scene2d.Sprite2D;
import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.stage.VnCasterSegmentation;
import com.jvn.core.vn.stage.VnStagePreset.LightBlend;
import com.jvn.core.vn.stage.VnStagePreset.LightFalloff;

import com.jvn.core.vn.stage.VnStagePreset.OccluderShape;
import com.jvn.core.vn.stage.VnStagePreset.ShadowQuality;
import com.jvn.core.vn.stage.VnStagePresetLoader;
import com.jvn.fx.vn.VnGroundShadows;
import com.jvn.fx.vn.VnStageGradeBlend;
import com.jvn.fx.vn.VnStageLightField;
import com.jvn.fx.vn.VnStageLightOverlay;
import com.jvn.fx.vn.VnStageLightingSupport;
import com.jvn.fx.vn.VnStageMaterial;
import com.jvn.fx.vn.VnStageShadow;
import com.jvn.fx.vn.VnStageShadowDebug;
import com.jvn.editor.ui.actioneditor.AnimationProject;
import com.jvn.editor.ui.actioneditor.EntityGroup;
import com.jvn.editor.ui.actioneditor.EntitySelector;
import com.jvn.editor.ui.actioneditor.EntityTrack;
import com.jvn.scripting.jes.runtime.JesScene2D;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.util.Duration;

/**
 * Standalone scene-lighting utility inspired by Ren'Py's Image Tint Tool.
 * This tool is intentionally independent from layered/image-attributes tools.
 * It focuses on scene-aware grading, staged light rigs, and local grade zones, which can be used in combination with layered setups but do not require them.
 * The tool scans the project for image files and charpreset tags, allowing you to quickly light and grade character images, then export the results
 * as PNG files or reusable scene setup files. Those setups can be loaded back into the tool or used as a reference for manual lighting in layered/image-attributes tools.
 */

public class ImageTintToolView extends BorderPane implements ImageToolPanel {
  private static final String STATE_FILE = ".jvn/image-tint-tool.properties";
  private static final String TOOL_TITLE = "Scene Lighting Studio";
  private static final String PRESET_TAG_PREFIX = "preset:";
  private static final String ASSET_SCOPE_CHARACTER_PRESETS_ONLY = "Charpresets only";
  private static final String ASSET_SCOPE_CHARACTER_ASSETS_ONLY = "Character assets only";
  private static final String ASSET_SCOPE_CHARACTER_ASSETS_AND_PRESETS = "Character assets + charpresets";
  private static final String ASSET_SCOPE_ALL_ASSETS_AND_PRESETS = "All image assets + charpresets";
  private static final Pattern CHARLAYER_PATTERN = Pattern.compile("^\\s*@charlayer\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARPRESET_PATTERN = Pattern.compile("^\\s*@charpreset\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);

  private static final String DEFAULT_EXPORT_PROFILE = "Lighting Profile";
  private static final String DEFAULT_EXPORT_SETUP = "Scene Setup";
  private static final String DEFAULT_EXPORT_STAGE_PRESET = "Stage Preset";
  private static final String LEGACY_ZONE_PREFIX = "zone.";
  private static final String ZONE_PROFILE_PREFIX = "zone.profile.";
  private static final String GLOBAL_ZONE_PROFILE_KEY = "_global_";
  private static final double DEFAULT_SIDEBAR_DIVIDER = 0.66;
  private static final double DEFAULT_LIGHT_RADIUS = 0.22;
  private static final double LIGHT_HANDLE_RADIUS_PX = 12.0;
  private static final double DEFAULT_LIGHT_SILHOUETTE = 28.0;
  private static final double INTERACTION_PREVIEW_SCALE = 0.62;
  private static final int MAX_IDLE_PREVIEW_RASTER = 4096;
  private static final int MAX_INTERACTION_PREVIEW_RASTER = 1152;

  private final Label summaryLabel = new Label("Open a project to build the lighting catalog.");
  private final Label statusLabel = new Label("");
  private final Label previewInfoLabel = new Label("No subject image selected.");
  private final Label interactionHintLabel = new Label("Drag the subject to test scene framing. Cross handle = fall target, diamond handle = source direction. Scroll to zoom, double-click to reset.");
  private final Label lightRigSummaryLabel = new Label("No lights staged.");

  private final TextField filterField = new TextField();
  private final ComboBox<String> assetScopeBox = new ComboBox<>();
  private final ComboBox<String> characterTagBox = new ComboBox<>();
  private final ComboBox<String> backgroundTagBox = new ComboBox<>();
  private final ComboBox<String> setupBox = new ComboBox<>();
  private final TextField setupNameField = new TextField();
  private final ComboBox<String> exportFormatBox = new ComboBox<>();
  private final CheckBox customExportNameCheck = new CheckBox("Custom name");
  private final TextField exportNameField = new TextField();
  private final TextField exportDirectoryField = new TextField();
  private final Label exportSelectionLabel = new Label("");
  private final Label exportInfoLabel = new Label("");
  private final ColorPicker tintColorPicker = new ColorPicker(Color.web("#ffffff"));
  private final Slider tintStrengthSlider = slider(0, 100, 30);
  private final Slider saturationSlider = slider(-100, 100, 0);
  private final Slider contrastSlider = slider(-100, 100, 0);
  private final Region tintColorSwatch = new Region();
  private final ColorPicker bgTintColorPicker = new ColorPicker(Color.web("#ffffff"));
  private final Slider bgTintStrengthSlider = slider(0, 100, 0);
  private final Slider bgSaturationSlider = slider(-100, 100, 0);
  private final Slider bgContrastSlider = slider(-100, 100, 0);
  private final Slider bgExposureSlider = slider(-400, 400, 0);
  private final ComboBox<String> bgTintBlendModeBox = new ComboBox<>();
  private final ColorPicker bgOverlayColorPicker = new ColorPicker(Color.web("#000000"));
  private final Slider bgOverlayOpacitySlider = slider(0, 100, 0);
  private final ComboBox<String> bgOverlayBlendModeBox = new ComboBox<>();
  private final Region bgTintColorSwatch = new Region();
  private final Region bgOverlayColorSwatch = new Region();
  private final VBox backgroundControlsSection = new VBox(8);
  private TitledPane backgroundPane;
  private TitledPane exportPane;
  private TitledPane lightsPane;
  private TitledPane occlusionPane;

  private final Canvas previewCanvas = new Canvas(320, 240);
  private final LoadingProgressOverlay previewLoadingOverlay = new LoadingProgressOverlay();
  private final EntitySelector sceneEntitySelector = new EntitySelector();
  private final VBox controlsSection = new VBox(8);
  private final SplitPane workspaceSplit = new SplitPane();
  private TitledPane controlsPane;
  private TitledPane sceneEntitiesPane;

  private final Map<String, File> imageByTag = new LinkedHashMap<>();
  private final Map<String, PresetTagEntry> presetByTag = new LinkedHashMap<>();
  private final Map<String, String> setupNameToKey = new LinkedHashMap<>();
  private final Map<String, Image> imageCache = new HashMap<>();
  private final Map<ComboBox<String>, AssetTagSearchPopup> assetTagSearchPopups = new HashMap<>();
  private final Map<String, SceneSelectorBinding> sceneSelectorBindings = new LinkedHashMap<>();
  private final Properties persisted = new Properties();
  private final PauseTransition stateSaveDebounce = new PauseTransition(Duration.millis(250));

  private File projectRoot;
  private Supplier<FileEditorTab> activeScriptSupplier = () -> null;
  private final Label sceneInfo = new Label();
  private final ComboBox<SceneSubject> sceneSubjectBox = new ComboBox<>();
  private final Map<String, PresetTagEntry> sceneSubjectLayers = new HashMap<>();
  private final TabPane inspector = new TabPane();
  private final TextField normalMapField = new TextField();
  private final Slider normalStrengthSlider = slider(0, 100, 70);
  private final Slider specularSlider = slider(0, 100, 20);
  private final Slider glossSlider = slider(2, 128, 32);
  private Tab rigTab;
  private Tab shadowsTab;
  private Runnable fullscreenToggleHandler;
  private boolean fullscreenActive;
  private Button fullscreenButton;
  private Button sidebarHideButton;
  private Button sidebarShowButton;
  private Button refreshCatalogButton;
  private Button exportPrimaryButton;
  private Button exportPrimaryAsButton;
  private boolean applyingState;
  private boolean stateSavePending;
  private Task<TintCatalogScanResult> scanTask;
  private Task<Image> backgroundTintTask;
  private String backgroundTintTaskTag;
  private String backgroundTintTaskKey;
  private long backgroundTintTaskSerial;
  private boolean disposed;
  private boolean backgroundFxSliderDragging;
  private boolean backgroundFxSliderCommitPending;
  private String activeZoneProfileTag = "";

  private boolean dragging;
  private double dragLastX;
  private double dragLastY;
  private double zoom = 1.0;
  private double offsetX;
  private double offsetY;
  private double sidebarDividerPosition = DEFAULT_SIDEBAR_DIVIDER;
  private boolean sidebarCollapsed;
  private StackPane previewHost;
  private javafx.scene.Node sidebarScroll;

  private String tintedImageTag;
  private String tintedImageKey;
  private Image tintedImage;
  private String tintedBackgroundTag;
  private String tintedBackgroundKey;
  private Image tintedBackground;
  private AnimationProject sceneEntityProject = new AnimationProject();
  private JesScene2D sceneEntityScene = new JesScene2D();
  private boolean syncingSceneEntitySelector;

  private record SceneSelectorBinding(String tag, boolean background) {}

  // ── Zone area selector ──
  private static final String[] BLEND_MODES = {
      "Normal",
      "Multiply",
      "Screen",
      "Overlay",
      "Soft Light",
      "Hard Light",
      "Color Dodge",
      "Color Burn",
      "Difference",
      "Exclusion",
      "Lighten",
      "Darken",
      "Add",
      "Subtract"
  };
  private static final double FREEHAND_CAPTURE_MIN_DISTANCE = 1.4;
  private static final double FREEHAND_CLOSE_DISTANCE = 12.0;
  private static final double FREEHAND_RESAMPLE_STEP = 2.4;
  private static final double FREEHAND_SPLINE_STEP = 1.8;
  private static final double FREEHAND_SIMPLIFY_EPSILON = 0.9;
  private static final int FREEHAND_MAX_VERTICES = 220;
  private final List<TintZone> tintZones = new ArrayList<>();
  private int selectedZoneIndex = -1;
  private boolean zoneDrawMode;       // rectangle draw mode
  private boolean polyDrawMode;       // point-nail polygon draw mode
  private boolean freehandDrawMode;   // hold-drag freehand/lasso draw mode
  private boolean lightPolyDrawMode;  // point-nail polygon light mode
  private boolean lightFreehandDrawMode; // freehand polygon light mode
  private boolean occluderPolyDrawMode; // point-nail polygon occluder mode
  private boolean occluderFreehandDrawMode; // freehand polygon occluder mode
  private boolean drawingZone;
  private boolean drawingFreehand;
  private double zoneDrawStartX;
  private double zoneDrawStartY;
  private double zoneDrawEndX;
  private double zoneDrawEndY;
  private final List<double[]> nailPoints = new ArrayList<>(); // canvas coords for polygon nailing
  private final List<double[]> freehandPoints = new ArrayList<>(); // canvas coords for freehand stroke

  private final ListView<TintZone> zoneListView = new ListView<>();
  private final ColorPicker zoneColorPicker = new ColorPicker(Color.web("#ff8844"));
  private final Slider zoneStrengthSlider = slider(0, 100, 50);
  private final Slider zoneSaturationSlider = slider(-100, 100, 0);
  private final Slider zoneContrastSlider = slider(-100, 100, 0);
  private final Slider zoneFeatherSlider = slider(0, 100, 15);
  private final Slider zoneRotationSlider = slider(-180, 180, 0);
  private final ComboBox<String> zoneBlendModeBox = new ComboBox<>();
  private final ComboBox<String> zoneSurfaceClassBox = new ComboBox<>();
  private final TextField zoneNameField = new TextField();
  private final Region zoneColorSwatch = new Region();
  private final Slider zoneDepthBiasSlider = slider(-100, 100, 0);
  private final Slider zoneResponseScaleSlider = slider(10, 250, 100);
  private final VBox zoneControlsSection = new VBox(6);
  private TitledPane zonesPane;
  private Button zoneDrawToggleButton;
  private Button polyDrawToggleButton;
  private Button freehandDrawToggleButton;

  // ── Scene light selector ──
  private final List<SceneLight> sceneLights = new ArrayList<>();
  private int selectedLightIndex = -1;
  private boolean draggingLight;
  private boolean draggingLightSource;
  private final ListView<SceneLight> lightListView = new ListView<>();
  private final ColorPicker lightColorPicker = new ColorPicker(Color.web("#ffd7a8"));
  private final Slider lightIntensitySlider = slider(0, 100, 42);
  private final Slider lightRadiusSlider = slider(5, 80, DEFAULT_LIGHT_RADIUS * 100.0);
  private final Slider lightSoftnessSlider = slider(0, 100, 55);
  private final Slider lightSilhouetteSlider = slider(0, 100, DEFAULT_LIGHT_SILHOUETTE);
  private final ComboBox<String> lightShapeBox = new ComboBox<>();
  private final ComboBox<String> lightLayerBox = new ComboBox<>();
  private final ComboBox<LightBlend> lightBlendBox = new ComboBox<>();
  private final ComboBox<LightFalloff> lightFalloffBox = new ComboBox<>();
  private final CheckBox lightShadowsCheck = new CheckBox("Cast shadows");
  private final ComboBox<ShadowQuality> shadowQualityBox = new ComboBox<>();
  private final Slider beamAngleSlider = slider(0, 175, 0);
  private final Slider areaAspectSlider = slider(0, 100, 0);
  private final CheckBox groundProjectionCheck = new CheckBox("Project shadows onto floor");
  private final TextField groundProjectionField = new TextField();
  private final Slider floorLightSlider = slider(0, 100, 0);
  private final Slider floorExposureSlider = slider(-400, 400, 0);
  private Image groundShadowPreview;
  private final CheckBox compareOriginal = new CheckBox("Original");
  private VnStagePreset previewRig;
  private double previewOverlayWidth;
  private double previewOverlayHeight;
  private final Map<SceneLightLayer, VnStageLightOverlay> previewOverlays = new java.util.EnumMap<>(SceneLightLayer.class);
  private final TextField lightNameField = new TextField();
  private final TextField lightGroupField = new TextField();
  private final CheckBox lightMutedCheck = new CheckBox("Mute");
  private final CheckBox lightLockedCheck = new CheckBox("Lock");
  private final CheckBox lightSoloCheck = new CheckBox("Solo");
  private final CheckBox showLightVectorsCheck = new CheckBox("Show light vectors");
  private final Region lightColorSwatch = new Region();
  private final VBox lightControlsSection = new VBox(6);
  private Button lightPolyDrawToggleButton;
  private Button lightFreehandDrawToggleButton;

  // ── Occlusion / blockers ──
  private final List<SceneOccluder> sceneOccluders = new ArrayList<>();
  private int selectedOccluderIndex = -1;
  private final ListView<SceneOccluder> occluderListView = new ListView<>();
  private final TextField occluderNameField = new TextField();
  private final TextField occluderBoundsField = new TextField();
  private final TextField occluderFloorContactsField = new TextField();
  private final TextField occluderFramingField = new TextField();
  private final ComboBox<OccluderShape> occluderShapeBox = new ComboBox<>();
  private final ComboBox<String> shadowDebugBox = new ComboBox<>();
  private ShadowDebugCache shadowDebugCache;
  private record ShadowDebugCache(VnStagePreset rig, VnStagePreset.Light light, int width, int height,
      Image mask, List<VnStageShadowDebug.Ray> rays) {}
  private OccluderShape occluderBoundsDrawMode;
  private boolean drawingOccluderBounds;
  private final Slider occluderOpacitySlider = slider(0, 100, 85);
  private final Slider occluderSoftnessSlider = slider(0, 100, 12);
  private final Slider occluderContactSlider = slider(0, 100, 0);
  private final Slider occluderHeightSlider = slider(0, 300, 100);
  private final Slider occluderFootprintSlider = slider(0, 50, 0);
  private final Slider occluderFadeSlider = slider(0, 100, 0);
  private final CheckBox occluderRoundCheck = new CheckBox("Round floor shadow");
  private final Slider occluderExposureSlider = slider(-400, 400, 0);
  private final Slider occluderContrastSlider = slider(-80, 200, 0);
  private final CheckBox occluderEnabledCheck = new CheckBox("Enabled");
  private final VBox occluderControlsSection = new VBox(6);
  private Button occluderPolyDrawToggleButton;
  private Button occluderFreehandDrawToggleButton;

  public ImageTintToolView() {

    stateSaveDebounce.setOnFinished(e -> flushPendingStateSave());
    getStyleClass().addAll("sidebar-tool-root", "lighting-studio");
    getStylesheets().add(ImageTintToolView.class.getResource("/com/jvn/editor/lighting-studio.css").toExternalForm());
    buildUi();
    refreshCatalog();
  }

  private void buildUi() {
    Label title = new Label(TOOL_TITLE);
    title.getStyleClass().add("sidebar-tool-title");
    summaryLabel.getStyleClass().add("sidebar-tool-summary");
    summaryLabel.setWrapText(true);
    interactionHintLabel.getStyleClass().add("sidebar-tool-subtitle");
    interactionHintLabel.setWrapText(true);
    previewInfoLabel.getStyleClass().add("sidebar-tool-subtitle");
    lightRigSummaryLabel.getStyleClass().add("sidebar-tool-subtitle");
    lightRigSummaryLabel.setWrapText(true);
    statusLabel.getStyleClass().add("sidebar-tool-status");
    statusLabel.setWrapText(true);

    filterField.setPromptText("Filter tags...");
    filterField.textProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      refreshTagLists();
      persistGlobalState();
    });
    assetScopeBox.getItems().setAll(
        ASSET_SCOPE_CHARACTER_ASSETS_AND_PRESETS,
        ASSET_SCOPE_CHARACTER_ASSETS_ONLY,
        ASSET_SCOPE_CHARACTER_PRESETS_ONLY,
        ASSET_SCOPE_ALL_ASSETS_AND_PRESETS);
    assetScopeBox.getSelectionModel().select(ASSET_SCOPE_CHARACTER_ASSETS_AND_PRESETS);
    assetScopeBox.setTooltip(new Tooltip("Choose which assets appear in the character tag loader."));
    assetScopeBox.valueProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      refreshTagLists();
      ensureDefaultSelections();
      redrawPreview();
      persistGlobalState();
    });

    configureAssetTagBox(characterTagBox);
    characterTagBox.setPromptText("Character image tag");
    configureAssetTagBox(backgroundTagBox);
    backgroundTagBox.setPromptText("Background image tag");

    setupBox.setPromptText("Saved setup");
    setupNameField.setPromptText("Setup name");
    exportFormatBox.getItems().setAll(DEFAULT_EXPORT_PROFILE, DEFAULT_EXPORT_SETUP, DEFAULT_EXPORT_STAGE_PRESET);
    exportFormatBox.getSelectionModel().select(DEFAULT_EXPORT_PROFILE);
    exportFormatBox.valueProperty().addListener((o, ov, nv) -> {
      updateExportControls();
      if (!applyingState) persistGlobalState();
    });
    customExportNameCheck.selectedProperty().addListener((o, ov, nv) -> {
      updateExportControls();
      if (!applyingState) persistGlobalState();
    });
    exportNameField.setPromptText("Auto from current image");
    exportNameField.textProperty().addListener((o, ov, nv) -> {
      updateExportControls();
      if (!applyingState) persistGlobalState();
    });
    exportDirectoryField.setEditable(false);
    exportDirectoryField.setFocusTraversable(false);
    exportDirectoryField.setPromptText("Project root");
    exportSelectionLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #d9dde4;");
    exportSelectionLabel.setWrapText(true);
    exportInfoLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #9a9a9a;");
    exportInfoLabel.setWrapText(true);

    refreshCatalogButton = iconButton(SidebarToolIcon.refresh("#7ec8e3"), "Rescan project images", this::onCatalogRefreshRequested);
    updateRefreshButtonUi(false);

    // ── Compact sidebar rows ──
    HBox filterRow = new HBox(4, new Label("Filter"), filterField, refreshCatalogButton);
    filterRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(filterField, Priority.ALWAYS);

    HBox scopeRow = new HBox(4, new Label("Scope"), assetScopeBox);
    scopeRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(assetScopeBox, Priority.ALWAYS);

    Button characterPickerButton = assetTagPickerButton(
        SidebarToolIcon.person("#d8c48a"),
        "Choose character asset or charpreset",
        characterTagBox);
    HBox charRow = new HBox(4, characterPickerButton, characterTagBox);
    charRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(characterTagBox, Priority.ALWAYS);

    Button backgroundPickerButton = assetTagPickerButton(
        SidebarToolIcon.landscape("#f5c46b"),
        "Choose background image",
        backgroundTagBox);
    HBox bgRow = new HBox(4, backgroundPickerButton, backgroundTagBox);
    bgRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(backgroundTagBox, Priority.ALWAYS);

    Button loadSetupButton = iconButton(SidebarToolIcon.download("#8ab4f8"), "Load selected setup", this::loadSelectedSetup);
    Button deleteSetupButton = iconButton(SidebarToolIcon.clearX("#f38ba8"), "Delete selected setup", this::deleteSelectedSetup);
    HBox setupLoadRow = new HBox(4, new Label("Setup"), setupBox, loadSetupButton, deleteSetupButton);
    setupLoadRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(setupBox, Priority.ALWAYS);

    Button saveSetupButton = iconButton(SidebarToolIcon.save("#9ed67a"), "Save current setup", this::saveCurrentSetup);
    HBox setupSaveRow = new HBox(4, new Label("Save"), setupNameField, saveSetupButton);
    setupSaveRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(setupNameField, Priority.ALWAYS);

    sceneSubjectBox.setPromptText("Subject from scene");
    sceneSubjectBox.setMaxWidth(Double.MAX_VALUE);
    sceneSubjectBox.valueProperty().addListener((obs, old, subject) -> {
      if (applyingState || subject == null) return;
      setComboTagValue(characterTagBox, subject.tag());
      frameSceneSubject(subject);
      persistGlobalState();
      redrawPreview();
    });
    TitledPane tagsPane = new TitledPane("Scene Assets",
        new VBox(8, sceneSubjectBox, bgRow, charRow, filterRow, scopeRow, setupLoadRow, setupSaveRow));
    tagsPane.setExpanded(true);
    tagsPane.setAnimated(false);
    tagsPane.setCollapsible(true);

    buildSceneEntitiesPane();

    // ── Preview canvas fills the center ──
    StackPane previewPane = new StackPane(previewCanvas, previewLoadingOverlay);
    StackPane.setAlignment(previewLoadingOverlay, Pos.CENTER);
    previewLoadingOverlay.hideOverlay();
    previewPane.getStyleClass().add("lighting-viewport");
    Runnable fitStage = () -> {
      double parentWidth = Math.max(140, previewPane.getWidth() - 4);
      double parentHeight = Math.max(100, previewPane.getHeight() - 4);
      double stageWidth = Math.min(parentWidth, parentHeight * 16 / 9);
      previewCanvas.setWidth(stageWidth);
      previewCanvas.setHeight(stageWidth * 9 / 16);
      redrawPreview();
    };
    previewPane.widthProperty().addListener((o, ov, nv) -> fitStage.run());
    previewPane.heightProperty().addListener((o, ov, nv) -> fitStage.run());

    installPreviewInteractions();

    // ── Action buttons ──
    Button resetViewButton = iconButton(SidebarToolIcon.expand("#8ab4f8"), "Reset position and zoom", this::resetView);
    Button resetTintButton = iconButton(SidebarToolIcon.palette("#f5b971"), "Reset global grade controls", this::resetTintControls);
    fullscreenButton = iconButton(SidebarToolIcon.expand("#f5c46b"), "Fullscreen", this::requestFullscreenToggle);
    updateFullscreenButtonUi();
    Button chooseExportFolderButton = iconButton(SidebarToolIcon.folder("#f5c46b"), "Choose export folder", this::chooseExportDirectory);
    Button revealExportFolderButton = iconButton(SidebarToolIcon.link("#9cc7ff"), "Reveal export folder in JVN Path Explorer", this::revealExportDirectory);
    exportPrimaryButton = actionButton("Export Profile", SidebarToolIcon.download("#8ab4f8"), "Quick export the selected format to the configured folder", this::quickExportSelectedFormat);
    exportPrimaryAsButton = actionButton("Export Profile As", SidebarToolIcon.save("#8ab4f8"), "Choose a destination for the selected format", this::exportSelectedFormatAs);
    Button exportPngButton = actionButton("PNG", SidebarToolIcon.download("#8ab4f8"), "Quick export the graded PNG to the configured folder", this::quickExportTintedPng);
    Button exportPngAsButton = actionButton("PNG As", SidebarToolIcon.save("#8ab4f8"), "Choose a PNG destination", this::exportTintedPngAs);
    Button exportSetupBtn = actionButton("Setup", SidebarToolIcon.save("#9ed67a"), "Quick export .tintsetup to the configured folder", this::quickExportSetupToFile);
    Button exportSetupAsButton = actionButton("Setup As", SidebarToolIcon.download("#9ed67a"), "Choose a .tintsetup destination", this::exportSetupToFileAs);
    Button exportStageBtn = actionButton("Stage", SidebarToolIcon.save("#d2c6ff"), "Quick export runtime-ready .stagepreset to the configured folder", this::quickExportStagePresetToFile);
    Button exportStageAsButton = actionButton("Stage As", SidebarToolIcon.download("#d2c6ff"), "Choose a .stagepreset destination", this::exportStagePresetToFileAs);
    Button importSetupBtn = actionButton("Import", SidebarToolIcon.folder("#f5c46b"), "Open a .tintsetup or .stagepreset file", this::importSetupFromFile);
    Button exportBundleButton = actionButton("PNG + Setup + Stage", SidebarToolIcon.download("#d8c48a"), "Quick export PNG, editor setup, and runtime stage preset to the configured folder", this::quickExportBundle);
    Button copyExportButton = actionButton("Copy", SidebarToolIcon.copy("#9ad19c"), "Copy selected export format", this::copySelectedExport);

    HBox exportModeRow = new HBox(4, new Label("Mode"), exportFormatBox);
    exportModeRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportFormatBox, Priority.ALWAYS);
    HBox exportDirRow = new HBox(4, new Label("Folder"), exportDirectoryField, chooseExportFolderButton, revealExportFolderButton);
    exportDirRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportDirectoryField, Priority.ALWAYS);
    HBox exportNameRow = new HBox(4, customExportNameCheck, exportNameField);
    exportNameRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportNameField, Priority.ALWAYS);
    HBox exportPrimaryRow = new HBox(4, exportPrimaryButton, exportPrimaryAsButton, copyExportButton);
    exportPrimaryRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportPrimaryButton, Priority.ALWAYS);
    HBox.setHgrow(exportPrimaryAsButton, Priority.ALWAYS);
    HBox.setHgrow(copyExportButton, Priority.ALWAYS);
    HBox exportUtilityRow = new HBox(4, importSetupBtn, exportBundleButton);
    exportUtilityRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(importSetupBtn, Priority.ALWAYS);
    HBox.setHgrow(exportBundleButton, Priority.ALWAYS);
    HBox filePngRow = new HBox(4, exportPngButton, exportPngAsButton);
    filePngRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportPngButton, Priority.ALWAYS);
    HBox.setHgrow(exportPngAsButton, Priority.ALWAYS);
    HBox fileSetupRow = new HBox(4, exportSetupBtn, exportSetupAsButton, importSetupBtn);
    fileSetupRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportSetupBtn, Priority.ALWAYS);
    HBox.setHgrow(exportSetupAsButton, Priority.ALWAYS);
    HBox.setHgrow(importSetupBtn, Priority.ALWAYS);
    HBox fileStageRow = new HBox(4, exportStageBtn, exportStageAsButton);
    fileStageRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportStageBtn, Priority.ALWAYS);
    HBox.setHgrow(exportStageAsButton, Priority.ALWAYS);
    exportPane = new TitledPane(
        "Export & Share",
        new VBox(4, exportModeRow, exportDirRow, exportNameRow, exportSelectionLabel, exportInfoLabel, exportPrimaryRow, exportUtilityRow, filePngRow, fileSetupRow, fileStageRow));
    exportPane.setExpanded(true);
    exportPane.setAnimated(false);
    exportPane.setCollapsible(true);
    exportPane.expandedProperty().addListener((o, ov, expanded) -> {
      if (!applyingState) persistGlobalState();
    });

    // ── Tint controls ──
    tintColorPicker.valueProperty().addListener((o, ov, nv) -> onTintChanged(true));
    tintStrengthSlider.valueProperty().addListener((o, ov, nv) -> onTintChanged(true));
    saturationSlider.valueProperty().addListener((o, ov, nv) -> onTintChanged(true));
    contrastSlider.valueProperty().addListener((o, ov, nv) -> onTintChanged(true));

    updateTintColorSwatch(tintColorPicker.getValue());
    controlsSection.getChildren().setAll(
        tintPickerRow("Color", tintColorPicker, tintColorSwatch, Color.WHITE),
        sliderRow("Strength", tintStrengthSlider),
        sliderRow("Saturation", saturationSlider),
        sliderRow("Contrast", contrastSlider));
    controlsPane = new TitledPane("Global Grade", controlsSection);
    controlsPane.setExpanded(true);
    controlsPane.setAnimated(false);
    controlsPane.setCollapsible(true);
    controlsPane.expandedProperty().addListener((o, ov, expanded) -> {
      if (!applyingState) persistGlobalState();
    });

    buildBackgroundSection();
    buildSceneLightsSection();
    buildOcclusionSection();

    // ── Zone area selector section ──
    buildZoneSection();

    sidebarHideButton = iconButton(SidebarToolIcon.arrowRight("#d0d0d0"), "Hide controls sidebar", () -> setSidebarCollapsed(true, true));
    sidebarHideButton.getStyleClass().add("image-tool-sidebar-toggle");
    Button loadScene = actionButton("Load from VNS", SidebarToolIcon.download("#8ab4f8"),
        "Load the background, visible subjects and stage preset at the VNS cursor", this::loadActiveVnsScene);
    Button saveScene = actionButton("Apply to VNS", SidebarToolIcon.save("#9ed67a"),
        "Save a stage preset and activate it at the VNS cursor", this::applyLightingToVns);
    loadScene.setId("lighting-load-vns");
    saveScene.setId("lighting-apply-vns");
    saveScene.getStyleClass().add("lighting-apply");
    compareOriginal.setTooltip(new Tooltip("Compare against the unlit scene at the same framing"));
    compareOriginal.selectedProperty().addListener((obs, old, selected) -> redrawPreview());
    HBox titleRow = new HBox(6, title, SidebarToolHelp.button(this, TOOL_TITLE, """
            Put the cursor on a scene beat in a VNS script and choose Load from VNS.
            Select a subject, then arrange and blend lights in the Rig tab. The cross moves
            the light's target and the diamond moves its source. Use Shadows for occlusion,
            Grade for color and material zones, or Output to export reusable files.
            Apply to VNS saves a preset and activates it at the cursor. Save the script to
            keep that activation if the script already had unsaved edits.
            """));
    titleRow.setAlignment(Pos.CENTER_LEFT);
    FlowPane toolbar = new FlowPane(6, 5, loadScene, saveScene, compareOriginal, resetViewButton,
        fullscreenButton, sidebarHideButton);
    toolbar.setAlignment(Pos.CENTER_LEFT);
    toolbar.getStyleClass().add("lighting-toolbar");
    sceneInfo.setWrapText(true);
    sceneInfo.getStyleClass().add("lighting-scene-info");
    sceneInfo.setVisible(false);
    sceneInfo.managedProperty().bind(sceneInfo.visibleProperty());
    VBox header = new VBox(6, titleRow, toolbar, sceneInfo);
    header.getStyleClass().add("sidebar-tool-header");
    setTop(header);

    Tab sceneTab = inspectorTab("Scene", tagsPane, sceneEntitiesPane, summaryLabel);
    rigTab = inspectorTab("Rig", lightsPane, interactionHintLabel);
    shadowsTab = inspectorTab("Shadows", occlusionPane);
    Tab gradeTab = inspectorTab("Grade", controlsPane, resetTintButton, buildMaterialPane(), backgroundPane, zonesPane);
    Tab outputTab = inspectorTab("Output", exportPane);
    lightsPane.setExpanded(true);
    lightsPane.setCollapsible(false);
    occlusionPane.setExpanded(true);
    occlusionPane.setCollapsible(false);
    inspector.getTabs().setAll(sceneTab, rigTab, shadowsTab, gradeTab, outputTab);
    inspector.setId("lighting-inspector");
    inspector.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    inspector.getStyleClass().add("lighting-inspector");
    inspector.setPrefWidth(380);
    inspector.setMinWidth(0);
    inspector.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
      if (selected != shadowsTab) disableOccluderShapeDrawModes();
      if (!applyingState) persistGlobalState();
      redrawPreview();
    });

    sidebarShowButton = iconButton(SidebarToolIcon.arrowLeft("#d0d0d0"), "Show controls sidebar", () -> setSidebarCollapsed(false, true));
    sidebarShowButton.getStyleClass().addAll("image-tool-sidebar-toggle", "image-tool-sidebar-overlay-toggle");
    sidebarShowButton.setManaged(false);
    sidebarShowButton.setVisible(false);

    previewHost = new StackPane(previewPane, sidebarShowButton);
    previewHost.getStyleClass().add("image-tool-preview-host");
    previewHost.setAlignment(Pos.TOP_RIGHT);
    previewHost.setMinWidth(0);
    StackPane.setAlignment(sidebarShowButton, Pos.TOP_RIGHT);
    StackPane.setMargin(sidebarShowButton, new Insets(10));

    sidebarScroll = inspector;
    workspaceSplit.getItems().setAll(previewHost, sidebarScroll);
    workspaceSplit.getStyleClass().add("image-tool-workspace");
    workspaceSplit.setDividerPositions(DEFAULT_SIDEBAR_DIVIDER);
    SplitPane.setResizableWithParent(previewHost, true);
    SplitPane.setResizableWithParent(sidebarScroll, true);

    setCenter(workspaceSplit);
    widthProperty().addListener((obs, old, width) -> {
      Orientation orientation = width.doubleValue() < 670 ? Orientation.VERTICAL : Orientation.HORIZONTAL;
      if (orientation != workspaceSplit.getOrientation()) {
        if (workspaceSplit.getOrientation() == Orientation.HORIZONTAL) captureSidebarDividerPosition();
        workspaceSplit.setOrientation(orientation);
        workspaceSplit.setDividerPositions(orientation == Orientation.VERTICAL ? 0.52 : sidebarDividerPosition);
      }
    });
    previewInfoLabel.setWrapText(true);
    VBox footer = new VBox(3, statusLabel, previewInfoLabel);
    statusLabel.managedProperty().bind(statusLabel.textProperty().isNotEmpty());
    previewInfoLabel.managedProperty().bind(previewInfoLabel.textProperty().isNotEmpty());
    footer.getStyleClass().add("lighting-footer");
    setBottom(footer);

    bindTagSelectionHandlers();
    updateExportControls();
  }

  private static Tab inspectorTab(String name, javafx.scene.Node... controls) {
    VBox content = new VBox(8, controls);
    content.getStyleClass().add("lighting-inspector-body");
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    return new Tab(name, scroll);
  }

  private TitledPane buildMaterialPane() {
    normalMapField.setEditable(false);
    normalMapField.setPromptText("Subject normal map");
    normalMapField.setTooltip(new Tooltip("Use a tangent-space RGB normal map with the same framing as the subject."));
    Button choose = iconButton(SidebarToolIcon.folder("#f5c46b"), "Choose a normal map in this project", this::chooseNormalMap);
    Button clear = iconButton(SidebarToolIcon.clearX("#f38ba8"), "Clear the normal map", () -> normalMapField.clear());
    HBox mapRow = new HBox(5, normalMapField, choose, clear);
    HBox.setHgrow(normalMapField, Priority.ALWAYS);
    normalMapField.textProperty().addListener((obs, old, value) -> onMaterialChanged());
    for (Slider slider : List.of(normalStrengthSlider, specularSlider, glossSlider)) {
      slider.disableProperty().bind(normalMapField.textProperty().isEmpty());
      slider.valueProperty().addListener((obs, old, value) -> onMaterialChanged());
    }
    TitledPane pane = new TitledPane("Subject Material", new VBox(7, mapRow,
        sliderRow("Normal Strength", normalStrengthSlider), sliderRow("Specular", specularSlider), sliderRow("Gloss", glossSlider)));
    pane.setAnimated(false);
    pane.setExpanded(false);
    return pane;
  }

  private void chooseNormalMap() {
    if (projectRoot == null || !projectRoot.isDirectory()) {
      status("Open a project before choosing a normal map.");
      return;
    }
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Choose Subject Normal Map");
    chooser.setInitialDirectory(projectRoot);
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Normal Map Images", "*.png"));
    File file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
    if (file == null) return;
    try {
      Path directory = projectRoot.toPath().toRealPath();
      Path imagePath = file.toPath().toRealPath();
      if (!imagePath.startsWith(directory)) throw new IOException("Choose an image inside the project.");
      String tag = directory.relativize(imagePath).toString().replace('\\', '/');
      if (loadImage(tag) == null) throw new IOException("The selected image could not be opened.");
      normalMapField.setText(tag);
    } catch (IOException ex) {
      status(ex.getMessage());
    }
  }

  private void onMaterialChanged() {
    if (applyingState) return;
    invalidateTintCache();
    persistGlobalState();
    redrawPreview();
  }

  public void setActiveScriptSupplier(Supplier<FileEditorTab> supplier) {
    activeScriptSupplier = Objects.requireNonNull(supplier);
  }

  private FileEditorTab activeVnsScript() {
    FileEditorTab tab = activeScriptSupplier.get();
    if (tab == null || tab.getKind() != FileEditorTab.Kind.VNS || tab.getFile() == null || projectRoot == null) {
      status("Open a project VNS script and place the cursor on a scene beat.");
      return null;
    }
    return tab;
  }

  private void loadActiveVnsScene() {
    FileEditorTab tab = activeVnsScript();
    if (tab == null) return;
    try {
      var snapshot = PuppeteerLauncherPanel.resolveProjectSnapshot(
          tab.getCurrentTextSnapshot(), tab.getVnsCaretLine(), tab.getFile(), projectRoot);
      Properties setup = new Properties();
      if (snapshot.activeStagePresetId != null) {
        String presetPath = snapshot.resolveStagePresetPath(projectRoot);
        if (presetPath.isBlank()) throw new IOException("No preset file for " + snapshot.activeStagePresetId);
        setup = readSetup(projectRoot.toPath().resolve(presetPath));
      }
      sceneSubjectLayers.keySet().forEach(imageCache::remove);
      sceneSubjectLayers.clear();
      List<SceneSubject> subjects = snapshot.characters.stream()
          .map(subject -> sceneSubject(snapshot, subject)).toList();
      String storedSubject = setup.getProperty("character", "");
      SceneSubject selected = subjects.stream().filter(subject -> subject.tag().equals(storedSubject))
          .findFirst().orElse(subjects.isEmpty() ? null : subjects.get(0));
      String background = Boolean.parseBoolean(setup.getProperty("jvn.stagePreset.useSceneBackground"))
          ? snapshot.resolveBackgroundPath() : setup.getProperty("background", snapshot.resolveBackgroundPath());
      setup.setProperty("background", sceneAssetTag(background));
      setup.setProperty("character", selected == null ? "" : selected.tag());
      applyingState = true;
      try {
        sceneSubjectBox.getItems().setAll(subjects);
        sceneSubjectBox.setValue(selected);
      } finally {
        applyingState = false;
      }
      applyPortableSetup(setup);
      if (selected != null && snapshot.activeStagePresetId == null) frameSceneSubject(selected);
      inspector.getSelectionModel().select(rigTab);
      showSceneInfo(tab, snapshot.currentLabel, snapshot.activeStagePresetId);
      persistGlobalState();
      redrawPreview();
      status("Loaded the scene at line " + (tab.getVnsCaretLine() + 1) + ".");
    } catch (IOException | IllegalArgumentException ex) {
      status("Could not load scene lighting: " + ex.getMessage());
    }
  }

  private SceneSubject sceneSubject(PuppeteerLauncherPanel.SceneSnapshot snapshot,
      PuppeteerLauncherPanel.CharacterEntry character) {
    List<String> layers = snapshot.resolveCharacterLayers(character.characterId, character.expression).stream()
        .map(layer -> sceneAssetTag(layer.path)).filter(path -> !path.isBlank()).toList();
    String tag;
    if (layers.size() > 1) {
      tag = buildPresetTag(character.characterId, character.expression);
      sceneSubjectLayers.put(tag, new PresetTagEntry(tag, layers));
    } else {
      tag = layers.isEmpty() ? sceneAssetTag(snapshot.resolveCharacterPath(character.characterId, character.expression)) : layers.getFirst();
    }
    return new SceneSubject(character.characterId, character.expression, tag,
        PuppeteerLauncherPanel.snapshotPosition(character), character.scale);
  }

  private String sceneAssetTag(String path) {
    String value = normalize(path);
    if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
        || value.startsWith("'") && value.endsWith("'"))) {
      value = value.substring(1, value.length() - 1);
    }
    return value.replace('\\', '/');
  }

  private record SceneSubject(String character, String expression, String tag, CharacterPosition position, double scale) {
    @Override public String toString() { return character + " · " + expression; }
  }

  private void frameSceneSubject(SceneSubject subject) {
    Image image = loadImage(subject.tag());
    if (image == null) return;
    double w = previewCanvas.getWidth();
    double h = previewCanvas.getHeight();
    double spriteHeight = h * 0.85 * subject.scale();
    zoom = clamp(spriteHeight / image.getHeight(), 0.01, 8.0);
    double drawWidth = image.getWidth() * zoom;
    double drawHeight = image.getHeight() * zoom;
    double x = subject.position().computeScreenX(w, drawWidth);
    double y = subject.position().computeScreenY(h, drawHeight, 1.0);
    offsetX = Double.isFinite(x) ? x - (w - drawWidth) * 0.5 : 0;
    offsetY = (Double.isFinite(y) ? y : h - drawHeight) - (h - drawHeight) * 0.5;
  }

  private void showSceneInfo(FileEditorTab tab, String label, String preset) {
    sceneInfo.setText(tab.getFile().getName() + (label == null ? "" : "  /  " + label)
        + (preset == null ? "" : "  /  " + preset));
    sceneInfo.setVisible(true);
  }

  private void applyLightingToVns() {
    FileEditorTab tab = activeVnsScript();
    if (tab == null) return;
    Path pendingFile = null;
    try {
      Path script = tab.getFile().toPath().toRealPath();
      if (!script.startsWith(projectRoot.toPath().toRealPath())) throw new IOException("The script is outside the project.");
      var snapshot = PuppeteerLauncherPanel.resolveProjectSnapshot(
          tab.getCurrentTextSnapshot(), tab.getVnsCaretLine(), tab.getFile(), projectRoot);
      Path directory = script.getParent().resolve("lighting");
      Files.createDirectories(directory);
      if (!directory.toRealPath().startsWith(projectRoot.toPath().toRealPath())) {
        throw new IOException("The lighting folder is outside the project.");
      }
      String stem = buildStagePresetId(script.getFileName().toString().replaceFirst("(?i)\\.vns$", "")
          + "_" + Objects.toString(snapshot.currentLabel, "scene"));
      String active = snapshot.activeStagePresetId;
      Path existing = active != null && active.matches(Pattern.quote(stem) + "(?:_\\d+)?")
          && snapshot.activeStageLine == tab.getVnsCaretLine() ? directory.resolve(active + ".stagepreset") : null;
      boolean update = existing != null && Files.isRegularFile(existing)
          && Files.isSameFile(existing, projectRoot.toPath().resolve(snapshot.resolveStagePresetPath(projectRoot)))
          && Boolean.parseBoolean(readSetup(existing).getProperty("jvn.stagePreset.useSceneBackground"));
      String id = update ? active : stem;
      Path target = update ? existing : directory.resolve(id + ".stagepreset");
      for (int number = 2; !update && (Files.exists(target) || snapshot.stagePresetPaths.containsKey(id)); number++) {
        id = stem + "_" + number;
        target = directory.resolve(id + ".stagepreset");
      }
      String scriptPath = script.getParent().relativize(target).toString().replace('\\', '/');
      String preset = buildStagePresetText(id, target.getFileName().toString(), scriptPath, true);
      pendingFile = Files.createTempFile(directory, ".lighting-", ".tmp");
      Files.writeString(pendingFile, preset, StandardCharsets.UTF_8);
      if (update) {
        Files.move(pendingFile, target, StandardCopyOption.REPLACE_EXISTING);
        status("Updated " + target.getFileName() + ".");
      } else {
        Files.move(pendingFile, target);
        boolean scriptWasDirty = tab.isDirty();
        tab.getVnsEditor().insertStagePreset(id, quoteVnsTokenIfNeeded(scriptPath));
        if (!scriptWasDirty) tab.saveTo(tab.getFile());
        status(tab.isDirty() ? "Preset saved. Save the VNS script to keep its activation."
            : "Saved lighting to " + tab.getDisplayName() + ".");
      }
      showSceneInfo(tab, snapshot.currentLabel, id);
    } catch (IOException | IllegalArgumentException ex) {
      status("Could not apply scene lighting: " + ex.getMessage());
    } finally {
      if (pendingFile != null) {
        try { Files.deleteIfExists(pendingFile); } catch (IOException ignored) { }
      }
    }
  }

  private void buildSceneEntitiesPane() {
    sceneEntitySelector.setMinHeight(190);
    sceneEntitySelector.setPrefHeight(240);
    sceneEntitySelector.setScene(sceneEntityScene);
    sceneEntitySelector.setOnSelectionChanged((name, group) -> {
      if (syncingSceneEntitySelector || applyingState) return;
      SceneSelectorBinding binding = sceneSelectorBindings.get(selectorKey(name, group));
      if (binding == null) return;
      if (binding.background()) {
        setComboTagValue(backgroundTagBox, binding.tag());
        applyBackgroundTintIfPresent(selectedBackgroundTag());
      } else {
        setComboTagValue(characterTagBox, binding.tag());
        onCharacterTagChanged(false);
      }
      persistGlobalState();
    });
    sceneEntitySelector.setOnCreateGroup(groupName -> {
      sceneEntityProject.getOrCreateGroup(groupName);
      refreshSceneEntitySelector(false);
    });
    sceneEntitySelector.setOnAddSelectionToGroup((name, selectionIsGroup, groupName) -> {
      if (selectionIsGroup) {
        sceneEntityProject.addGroupToGroup(name, groupName);
      } else {
        sceneEntityProject.addEntityToGroup(name, groupName);
      }
      refreshSceneEntitySelector(false);
    });
    sceneEntitySelector.setOnRenameGroup((currentName, nextName) -> {
      sceneEntityProject.renameGroup(currentName, nextName);
      refreshSceneEntitySelector(false);
      sceneEntitySelector.selectGroup(nextName);
    });
    sceneEntitySelector.setOnDeleteSelection((name, group) -> {
      if (group) {
        sceneEntityProject.removeGroup(name);
      } else {
        sceneEntityProject.removeTrack(name);
        if (sceneEntityScene != null) sceneEntityScene.removeEntity(name);
      }
      refreshSceneEntitySelector(false);
    });
    sceneEntitySelector.setOnEntityLayerDelta((entityName, delta) -> {
      EntityTrack track = sceneEntityProject.getTrack(entityName);
      if (track != null) track.setLayerOrder(track.getLayerOrder() + delta);
      refreshSceneEntitySelector(false);
    });
    sceneEntitySelector.setOnGroupLayerDelta((groupName, delta) -> {
      EntityGroup group = sceneEntityProject.getGroup(groupName);
      if (group != null) group.setLayerOrder(group.getLayerOrder() + delta);
      refreshSceneEntitySelector(false);
    });
    sceneEntitySelector.setOnEntityVisibilityChanged((entityName, visible) -> {
      EntityTrack track = sceneEntityProject.getTrack(entityName);
      if (track != null) track.setVisible(visible);
      refreshSceneEntitySelector(false);
      sceneEntitySelector.selectEntity(entityName);
    });

    sceneEntitiesPane = new TitledPane("Scene Entities", sceneEntitySelector);
    sceneEntitiesPane.setExpanded(true);
    sceneEntitiesPane.setAnimated(false);
    sceneEntitiesPane.setCollapsible(true);
  }

  private void buildBackgroundSection() {
    bgTintBlendModeBox.getItems().setAll(BLEND_MODES);
    bgTintBlendModeBox.getSelectionModel().select("Normal");
    bgOverlayBlendModeBox.getItems().setAll(BLEND_MODES);
    bgOverlayBlendModeBox.getSelectionModel().select("Overlay");

    bgTintColorPicker.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) onBackgroundFxChanged(true);
    });
    bindBackgroundFxSlider(bgTintStrengthSlider);
    bindBackgroundFxSlider(bgSaturationSlider);
    bindBackgroundFxSlider(bgContrastSlider);
    bindBackgroundFxSlider(bgExposureSlider);
    bgTintBlendModeBox.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) onBackgroundFxChanged(true);
    });
    bgOverlayColorPicker.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) onBackgroundFxChanged(true);
    });
    bindBackgroundFxSlider(bgOverlayOpacitySlider);
    bgOverlayBlendModeBox.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) onBackgroundFxChanged(true);
    });

    updateBackgroundSwatches();
    backgroundControlsSection.getChildren().setAll(
        tintPickerRow("Grade Color", bgTintColorPicker, bgTintColorSwatch, Color.WHITE),
        sliderRow("Grade %", bgTintStrengthSlider),
        sliderRow("Saturation", bgSaturationSlider),
        sliderRow("Contrast", bgContrastSlider),
        sliderRow("Exposure ×100 EV", bgExposureSlider),
        comboRow("Grade Blend", bgTintBlendModeBox),
        tintPickerRow("Overlay", bgOverlayColorPicker, bgOverlayColorSwatch, Color.BLACK),
        sliderRow("Overlay %", bgOverlayOpacitySlider),
        comboRow("Overlay Blend", bgOverlayBlendModeBox));

    backgroundPane = new TitledPane("Background Grade", backgroundControlsSection);
    backgroundPane.setExpanded(false);
    backgroundPane.setAnimated(false);
    backgroundPane.setCollapsible(true);
    backgroundPane.expandedProperty().addListener((o, ov, expanded) -> {
      if (!applyingState) persistGlobalState();
    });
  }

  private void buildSceneLightsSection() {
    lightNameField.setPromptText("Light name");
    lightGroupField.setPromptText("Group / cluster");
    lightColorSwatch.setMinSize(18, 18);
    lightColorSwatch.setMaxSize(18, 18);
    lightColorSwatch.setPrefSize(18, 18);
    updateLightColorSwatch(lightColorPicker.getValue());
    lightShapeBox.getItems().setAll(SceneLightShape.labels());
    lightShapeBox.getSelectionModel().select(SceneLightShape.RADIAL.toString());
    beamAngleSlider.disableProperty().bind(lightShapeBox.valueProperty().isNotEqualTo(SceneLightShape.CONE.toString()));
    areaAspectSlider.disableProperty().bind(lightShapeBox.valueProperty().isNotEqualTo(SceneLightShape.AREA.toString())
        .and(lightShapeBox.valueProperty().isNotEqualTo(SceneLightShape.WINDOW.toString())));
    lightLayerBox.getItems().setAll(SceneLightLayer.labels());
    lightLayerBox.getSelectionModel().select(SceneLightLayer.CHARACTER.toString());
    lightBlendBox.setId("lighting-blend");
    lightBlendBox.getItems().setAll(LightBlend.values());
    lightBlendBox.setValue(LightBlend.PHYSICAL);
    lightBlendBox.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightControlsToSelected();
    });
    lightFalloffBox.setId("lighting-falloff");
    lightFalloffBox.getItems().setAll(LightFalloff.values());
    lightFalloffBox.setValue(LightFalloff.CLASSIC);
    lightFalloffBox.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightControlsToSelected();
    });
    lightShadowsCheck.setSelected(true);
    lightShadowsCheck.selectedProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightControlsToSelected();
    });
    beamAngleSlider.setTooltip(new Tooltip("Full spotlight angle in degrees. Use 0 for automatic framing."));
    areaAspectSlider.setTooltip(new Tooltip("Width of the Area or Window light as a percentage of its length. 0 uses the default."));
    beamAngleSlider.valueProperty().addListener((o, old, value) -> { if (!applyingState) applyLightControlsToSelected(); });
    areaAspectSlider.valueProperty().addListener((o, old, value) -> { if (!applyingState) applyLightControlsToSelected(); });
    groundProjectionField.setPromptText("Floor X, Y, Horizon Y, Strength (%)");
    groundProjectionField.setTooltip(new Tooltip("Four percentages. The floor point sits directly below the light, and the horizon sets ground perspective."));
    groundProjectionField.disableProperty().bind(groundProjectionCheck.selectedProperty().not());
    for (Slider slider : List.of(floorLightSlider, floorExposureSlider)) {
      slider.disableProperty().bind(groundProjectionCheck.selectedProperty().not());
      slider.valueProperty().addListener((o, old, value) -> { if (!applyingState) applyLightControlsToSelected(); });
    }
    groundProjectionField.setOnAction(event -> applyLightControlsToSelected());
    groundProjectionField.focusedProperty().addListener((obs, old, focused) -> {
      if (!focused && !applyingState && groundProjectionCheck.isSelected()) applyLightControlsToSelected();
    });
    groundProjectionCheck.selectedProperty().addListener((obs, old, value) -> {
      if (applyingState) return;
      if (value && groundProjectionField.getText().isBlank() && selectedLightIndex >= 0) {
        SceneLight light = sceneLights.get(selectedLightIndex);
        groundProjectionField.setText(groundProjectionString(new VnStagePreset.GroundProjection(light.sourceX, 0.70, 0.25, 0.55), 100));
      }
      applyLightControlsToSelected();
    });
    shadowQualityBox.getItems().setAll(ShadowQuality.values());
    shadowQualityBox.setValue(ShadowQuality.SOFT);
    shadowQualityBox.disableProperty().bind(lightShadowsCheck.selectedProperty().not());
    shadowQualityBox.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightControlsToSelected();
    });
    showLightVectorsCheck.setSelected(true);
    showLightVectorsCheck.setFocusTraversable(false);
    showLightVectorsCheck.selectedProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      redrawPreview();
      persistGlobalState();
    });

    lightListView.setPrefHeight(100);
    lightListView.setMinHeight(100);
    lightListView.setMaxHeight(132);
    lightListView.getStyleClass().add("lighting-rig-list");
    lightListView.setCellFactory(lv -> new ListCell<SceneLight>() {
      @Override protected void updateItem(SceneLight item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
          setStyle("");
          return;
        }
        Region swatch = new Region();
        swatch.setMinSize(10, 10);
        swatch.setPrefSize(10, 10);
        swatch.setMaxSize(10, 10);
        updateColorSwatch(swatch, item.color, Color.web("#ffd7a8"));
        setGraphic(swatch);
        setText(fallbackLightName(item) + "  ·  " + describeLightMeta(item));
        setContentDisplay(ContentDisplay.LEFT);
        setStyle(item.muted ? "-fx-opacity: 0.60;" : "");
      }
    });
    lightListView.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      selectLight(nv == null ? -1 : nv.intValue());
    });

    lightColorPicker.valueProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      updateLightColorSwatch(nv);
      applyLightControlsToSelected();
    });
    lightIntensitySlider.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightControlsToSelected();
    });
    lightRadiusSlider.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightControlsToSelected();
    });
    lightSoftnessSlider.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightControlsToSelected();
    });
    lightSilhouetteSlider.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightControlsToSelected();
    });
    lightShapeBox.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightControlsToSelected();
    });
    lightLayerBox.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightControlsToSelected();
    });
    lightNameField.textProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightNameToSelected();
    });
    lightGroupField.textProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightGroupToSelected();
    });
    lightMutedCheck.selectedProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightFlagsToSelected();
    });
    lightLockedCheck.selectedProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightFlagsToSelected();
    });
    lightSoloCheck.selectedProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyLightFlagsToSelected();
    });
    lightMutedCheck.setFocusTraversable(false);
    lightLockedCheck.setFocusTraversable(false);
    lightSoloCheck.setFocusTraversable(false);

    lightPolyDrawToggleButton = iconButton(SidebarToolIcon.polygon("#f5c46b"), "Draw a polygonal scene light on the preview", this::toggleLightPolyDrawMode);
    lightFreehandDrawToggleButton = iconButton(SidebarToolIcon.freehand("#9ad4ff"), "Sketch a freehand polygonal scene light on the preview", this::toggleLightFreehandDrawMode);
    Button addLightButton = iconButton(SidebarToolIcon.plus("#f6cc7a"), "Add a scene light at center", this::addDefaultLight);
    addLightButton.setId("lighting-add-light");
    Button duplicateLightButton = iconButton(SidebarToolIcon.copy("#d0d0d0"), "Duplicate selected light", this::duplicateSelectedLight);
    Button removeLightButton = iconButton(SidebarToolIcon.minus("#f38ba8"), "Remove selected light", this::removeSelectedLight);
    Button clearLightsButton = iconButton(SidebarToolIcon.clearX("#f5b971"), "Remove all scene lights", this::clearAllLights);
    Button moveLightUpButton = iconButton(SidebarToolIcon.arrowUp("#d0d0d0"), "Move light up in stack", this::moveLightUp);
    Button moveLightDownButton = iconButton(SidebarToolIcon.arrowDown("#d0d0d0"), "Move light down in stack", this::moveLightDown);
    Button resetLightVectorButton = iconButton(SidebarToolIcon.undo("#cfd8e6"), "Reset selected light source vector", this::resetSelectedLightSource);
    Button mirrorLightButton = iconButton(SidebarToolIcon.arrowLeft("#d7c3ff"), "Mirror selected light across the scene", this::mirrorSelectedLight);
    Button normalizeRigButton = iconButton(SidebarToolIcon.check("#9ed67a"), "Normalize light intensities in the rig", this::normalizeRigIntensity);
    FlowPane lightActions = new FlowPane(
        4, 4,
        lightPolyDrawToggleButton,
        lightFreehandDrawToggleButton,
        addLightButton,
        duplicateLightButton,
        removeLightButton,
        clearLightsButton,
        moveLightUpButton,
        moveLightDownButton,
        resetLightVectorButton,
        mirrorLightButton,
        normalizeRigButton);
    lightActions.setAlignment(Pos.CENTER_LEFT);

    FlowPane presetRow = new FlowPane(
        4, 4,
        presetChip("Key", this::addKeyLightPreset),
        presetChip("Fill", this::addFillLightPreset),
        presetChip("Rim", this::addRimLightPreset),
        presetChip("Sun", this::addSunPatchPreset),
        presetChip("Screen", this::addScreenGlowPreset),
        presetChip("Bounce", this::addBounceLightPreset));
    presetRow.setAlignment(Pos.CENTER_LEFT);

    HBox lightOptionsRow = new HBox(10, showLightVectorsCheck);
    lightOptionsRow.setAlignment(Pos.CENTER_LEFT);

    HBox lightNameRow = new HBox(8, new Label("Name"), lightNameField);
    lightNameRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(lightNameField, Priority.ALWAYS);

    HBox lightGroupRow = new HBox(8, new Label("Group"), lightGroupField);
    Button groupMute = new Button("Mute Group");
    groupMute.setOnAction(event -> toggleLightGroup(false));
    Button groupSolo = new Button("Solo Group");
    groupSolo.setOnAction(event -> toggleLightGroup(true));
    groupMute.disableProperty().bind(lightGroupField.textProperty().isEmpty());
    groupSolo.disableProperty().bind(lightGroupField.textProperty().isEmpty());
    FlowPane lightGroupActions = new FlowPane(5, 4, groupMute, groupSolo);
    lightGroupRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(lightGroupField, Priority.ALWAYS);

    HBox lightColorRow = new HBox(8, new Label("Color"), lightColorSwatch, lightColorPicker);
    lightColorRow.setAlignment(Pos.CENTER_LEFT);

    HBox lightShapeRow = new HBox(8, new Label("Shape"), lightShapeBox);
    lightShapeRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(lightShapeBox, Priority.ALWAYS);

    HBox lightLayerRow = new HBox(8, new Label("Layer"), lightLayerBox);
    lightLayerRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(lightLayerBox, Priority.ALWAYS);

    HBox lightBlendRow = new HBox(8, new Label("Blend"), lightBlendBox);
    lightBlendRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(lightBlendBox, Priority.ALWAYS);

    HBox lightFalloffRow = new HBox(8, new Label("Falloff"), lightFalloffBox);
    lightFalloffRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(lightFalloffBox, Priority.ALWAYS);
    FlowPane lightFlagsRow = new FlowPane(7, 4, lightMutedCheck, lightLockedCheck, lightSoloCheck, lightShadowsCheck);
    lightFlagsRow.setAlignment(Pos.CENTER_LEFT);

    lightControlsSection.getChildren().setAll(
        lightNameRow,
        lightGroupRow,
        lightGroupActions,
        lightShapeRow,
        lightLayerRow,
        lightBlendRow,
        lightFalloffRow,
        lightColorRow,
        sliderRow("Intensity", lightIntensitySlider),
        sliderRow("Radius / Feather", lightRadiusSlider),
        sliderRow("Softness", lightSoftnessSlider),
        sliderRow("Beam Angle", beamAngleSlider),
        sliderRow("Area Width %", areaAspectSlider),
        sliderRow("Silhouette", lightSilhouetteSlider),
        lightFlagsRow,
        new HBox(8, new Label("Shadows"), shadowQualityBox), groundProjectionCheck,
        new VBox(3, new Label("Floor X, Y, Horizon Y, Strength (%)"), groundProjectionField),
        sliderRow("Floor Light %", floorLightSlider), sliderRow("Floor Exposure ×100 EV", floorExposureSlider));
    lightControlsSection.setDisable(true);

    VBox lightsContent = new VBox(6, presetRow, lightActions, lightOptionsRow, lightRigSummaryLabel, lightListView, lightControlsSection);
    lightsPane = new TitledPane("Light Rig", lightsContent);
    lightsPane.setExpanded(false);
    lightsPane.setAnimated(false);
    lightsPane.setCollapsible(true);
    lightsPane.expandedProperty().addListener((o, ov, expanded) -> {
      if (!applyingState) persistGlobalState();
    });
  }

  private void bindTagSelectionHandlers() {
    characterTagBox.valueProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      onCharacterTagChanged(false);
    });

    backgroundTagBox.valueProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      applyBackgroundTintIfPresent(selectedBackgroundTag());
      refreshSceneEntitySelector(true);
      persistGlobalState();
    });
  }

  private void onCharacterTagChanged(boolean fromEditorTyping) {
    if (!fromEditorTyping || isKnownCharacterTag(selectedCharacterTag())) {
      switchZoneProfileForCharacter(selectedCharacterTag(), true);
    }
    updateExportControls();
    redrawPreview();
    refreshSceneEntitySelector(true);
    persistGlobalState();
  }

  private void onTintChanged(boolean redraw) {
    updateTintColorSwatch(tintColorPicker.getValue());
    invalidateTintCache();
    if (redraw) redrawPreview();
    if (!applyingState) {
      persistGlobalState();
    }
  }

  private void onBackgroundFxChanged(boolean redraw) {
    updateBackgroundSwatches();
    invalidateBackgroundTintCache();
    if (redraw) redrawPreview();
    if (!applyingState) {
      persistBackgroundTint(selectedBackgroundTag());
      persistGlobalState();
    }
  }

  private void bindBackgroundFxSlider(Slider slider) {
    if (slider == null) return;
    slider.valueProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      if (backgroundFxSliderDragging || slider.isValueChanging()) {
        backgroundFxSliderCommitPending = true;
        onBackgroundFxChanged(false);
        return;
      }
      onBackgroundFxChanged(true);
    });
    slider.valueChangingProperty().addListener((o, wasChanging, changing) -> {
      if (applyingState) return;
      if (!Boolean.TRUE.equals(changing)) {
        backgroundFxSliderDragging = false;
        commitBackgroundFxSliderChange();
      }
    });
    slider.setOnMousePressed(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      backgroundFxSliderDragging = true;
      backgroundFxSliderCommitPending = false;
    });
    slider.setOnMouseReleased(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      backgroundFxSliderDragging = false;
      commitBackgroundFxSliderChange();
    });
  }

  private void commitBackgroundFxSliderChange() {
    if (applyingState || !backgroundFxSliderCommitPending) return;
    backgroundFxSliderCommitPending = false;
    onBackgroundFxChanged(true);
  }

  private void resetBackgroundFxControls() {
    applyingState = true;
    try {
      bgTintColorPicker.setValue(Color.WHITE);
      bgTintStrengthSlider.setValue(0.0);
      bgSaturationSlider.setValue(0.0);
      bgContrastSlider.setValue(0.0);
      bgExposureSlider.setValue(0);
      bgTintBlendModeBox.getSelectionModel().select("Normal");
      bgOverlayColorPicker.setValue(Color.BLACK);
      bgOverlayOpacitySlider.setValue(0.0);
      bgOverlayBlendModeBox.getSelectionModel().select("Overlay");
    } finally {
      applyingState = false;
    }
    updateBackgroundSwatches();
    invalidateBackgroundTintCache();
    redrawPreview();
  }

  @Override
  public void setProjectRoot(File projectRoot) {
    if (disposed) return;
    persistGlobalState();
    if (!Objects.equals(this.projectRoot, projectRoot)) {
      sceneSubjectBox.getItems().clear();
      sceneSubjectLayers.clear();
      sceneInfo.setVisible(false);
    }
    this.projectRoot = projectRoot;
    refreshCatalog();
  }

  @Override
  public void setOnToggleFullscreen(Runnable handler) {
    fullscreenToggleHandler = handler;
  }

  @Override
  public void setFullscreenActive(boolean active) {
    if (fullscreenActive == active) return;
    fullscreenActive = active;
    updateFullscreenButtonUi();
  }

  public void dispose() {
    if (disposed) return;
    disposed = true;
    stateSaveDebounce.stop();
    assetTagSearchPopups.values().forEach(AssetTagSearchPopup::hide);
    assetTagSearchPopups.clear();
    Task<TintCatalogScanResult> task = scanTask;
    scanTask = null;
    if (task != null && task.isRunning()) {
      task.cancel();
    }
    backgroundTintTaskSerial++;
    cancelBackgroundTintTask();
    setupNameToKey.clear();
    tintZones.clear();
    sceneLights.clear();
    zoneListView.getItems().clear();
    lightListView.getItems().clear();
    clearCatalogUi("Scene lighting studio disposed.");
    projectRoot = null;
  }

  private void onCatalogRefreshRequested() {
    if (scanTask != null && scanTask.isRunning()) {
      scanTask.cancel();
      status("Cancelling scan...");
      return;
    }
    refreshCatalog();
  }

  private void updateRefreshButtonUi(boolean scanning) {
    if (refreshCatalogButton == null) return;
    if (scanning) {
      refreshCatalogButton.setGraphic(SidebarToolIcon.clearX("#f38ba8"));
      refreshCatalogButton.setTooltip(new Tooltip("Cancel image scan"));
    } else {
      refreshCatalogButton.setGraphic(SidebarToolIcon.refresh("#7ec8e3"));
      refreshCatalogButton.setTooltip(new Tooltip("Rescan project images"));
    }
  }

  private void clearCatalogUi(String message) {
    imageByTag.clear();
    presetByTag.clear();
    imageCache.clear();
    invalidateTintCache();
    invalidateBackgroundTintCache();
    summaryLabel.setText(message);
    characterTagBox.getItems().clear();
    backgroundTagBox.getItems().clear();
    refreshSetupOptions();
    updateLightRigSummary();
    redrawPreview();
  }

  @Override
  public void refreshCatalog() {
    if (disposed) return;
    loadPersistentState();
    if (scanTask != null && scanTask.isRunning()) {
      scanTask.cancel();
    }
    updateRefreshButtonUi(false);

    if (projectRoot == null || !projectRoot.isDirectory()) {
      clearCatalogUi("Open a project to build the lighting catalog.");
      return;
    }

    File rootDir = projectRoot;
    clearCatalogUi("Scanning image catalog...");
    status("Scanning project images...");
    updateRefreshButtonUi(true);

    Task<TintCatalogScanResult> task = new Task<>() {
      @Override
      protected TintCatalogScanResult call() throws Exception {
        Map<String, File> scannedImages = new LinkedHashMap<>();
        Path root = rootDir.toPath();
        List<String> imagePaths;
        try (Stream<Path> stream = Files.walk(root, 10)) {
          imagePaths = stream
              .filter(Files::isRegularFile)
              .filter(ImageTintToolView.this::isImageFile)
              .map(path -> root.relativize(path).toString().replace('\\', '/'))
              .filter(relative -> !isIgnoredPath(relative))
              .sorted(Comparator.naturalOrder())
              .toList();
        }
        int total = imagePaths.size();
        int index = 0;
        for (String relative : imagePaths) {
          if (isCancelled()) return TintCatalogScanResult.cancelledResult();
          scannedImages.put(relative, root.resolve(relative).toFile());
          index++;
          if (index == total || (index % 200) == 0) {
            updateProgress(index, Math.max(total, 1));
          }
        }
        if (isCancelled()) return TintCatalogScanResult.cancelledResult();
        Map<String, PresetTagEntry> scannedPresets = scanScriptCharpresets(root, this);
        if (isCancelled()) return TintCatalogScanResult.cancelledResult();
        return new TintCatalogScanResult(scannedImages, scannedPresets, false);
      }
    };
    scanTask = task;

    task.setOnSucceeded(e -> {
      if (disposed || scanTask != task) return;
      scanTask = null;
      updateRefreshButtonUi(false);
      TintCatalogScanResult result = task.getValue();
      if (result == null || result.cancelled()) {
        summaryLabel.setText("Image scan cancelled.");
        status("Scan cancelled.");
        return;
      }
      imageByTag.clear();
      imageByTag.putAll(result.images());
      presetByTag.clear();
      presetByTag.putAll(result.presets());
      imageCache.clear();
      invalidateTintCache();
      invalidateBackgroundTintCache();

      updateCatalogSummary();
      refreshTagLists();
      applyPersistedSelections();
      ensureDefaultSelections();
      refreshSetupOptions();
      switchZoneProfileForCharacter(selectedCharacterTag(), false);
      refreshSceneEntitySelector(true);
      redrawPreview();
      status("Scan complete.");
    });

    task.setOnCancelled(e -> {
      if (disposed || scanTask != task) return;
      scanTask = null;
      updateRefreshButtonUi(false);
      summaryLabel.setText("Image scan cancelled.");
      status("Scan cancelled.");
    });

    task.setOnFailed(e -> {
      if (disposed || scanTask != task) return;
      scanTask = null;
      updateRefreshButtonUi(false);
      Throwable ex = task.getException();
      clearCatalogUi("Image scan failed: " + (ex == null ? "Unknown error" : ex.getMessage()));
      status("Scan failed.");
    });

    Thread scanThread = new Thread(task, "jvn-image-tint-scan");
    scanThread.setDaemon(true);
    scanThread.start();
  }

  private void refreshTagLists() {
    String keepChar = selectedCharacterTag();
    String keepBg = selectedBackgroundTag();
    String filter = normalize(filterField.getText());
    String scope = normalize(assetScopeBox.getValue());
    List<String> characterTags = buildCharacterTagList(scope);
    List<String> bgs = new ArrayList<>(imageByTag.keySet());
    characterTags.removeIf(tag -> !matchesTagSearch(tag, filter));
    bgs.removeIf(tag -> !matchesTagSearch(tag, filter));
    characterTagBox.getItems().setAll(characterTags);

    bgs.sort(Comparator.comparingInt(tag -> isLikelyBackgroundTag(tag) ? 0 : 1));
    backgroundTagBox.getItems().setAll(bgs);
    refreshVisibleAssetTagPopup(characterTagBox);
    refreshVisibleAssetTagPopup(backgroundTagBox);

    boolean previousApplying = applyingState;
    applyingState = true;
    try {
      restoreComboSelection(characterTagBox, keepChar);
      restoreComboSelection(backgroundTagBox, keepBg);
    } finally {
      applyingState = previousApplying;
    }
  }

  private void applyPersistedSelections() {
    applyingState = true;
    try {
      int inspectorIndex = (int) parseDouble(persisted.getProperty("global.inspectorTab"), 0);
      inspector.getSelectionModel().select(Math.clamp(inspectorIndex, 0, inspector.getTabs().size() - 1));
      filterField.setText(persisted.getProperty("global.filter", ""));
      String scope = normalize(persisted.getProperty("global.assetScope", ASSET_SCOPE_CHARACTER_ASSETS_AND_PRESETS));
      if (!assetScopeBox.getItems().contains(scope)) {
        scope = ASSET_SCOPE_CHARACTER_ASSETS_AND_PRESETS;
      }
      assetScopeBox.getSelectionModel().select(scope);
      if (controlsPane != null) {
        boolean hide = parseBoolean(persisted.getProperty("global.hideControls"), false);
        controlsPane.setExpanded(!hide);
      }
      if (backgroundPane != null) {
        boolean hideBackground = parseBoolean(persisted.getProperty("global.hideBackgroundFx"), true);
        backgroundPane.setExpanded(!hideBackground);
      }
      if (exportPane != null) {
        boolean hideExport = parseBoolean(persisted.getProperty("global.hideExport"), false);
        exportPane.setExpanded(!hideExport);
      }

      String savedChar = persisted.getProperty("global.characterTag", "");
      String savedBg = persisted.getProperty("global.backgroundTag", "");
      setComboTagValue(characterTagBox, savedChar);
      setComboTagValue(backgroundTagBox, savedBg);
      String exportFormat = persisted.getProperty("global.exportFormat", DEFAULT_EXPORT_PROFILE);
      if (!exportFormatBox.getItems().contains(exportFormat)) exportFormat = DEFAULT_EXPORT_PROFILE;
      exportFormatBox.getSelectionModel().select(exportFormat);
      customExportNameCheck.setSelected(parseBoolean(persisted.getProperty("global.exportCustomName"), false));
      exportNameField.setText(persisted.getProperty("global.exportName", ""));

      tintColorPicker.setValue(parseColor(persisted.getProperty("global.tintColor"), Color.WHITE));
      tintStrengthSlider.setValue(parseDouble(persisted.getProperty("global.tintStrength"), 30.0));
      saturationSlider.setValue(parseDouble(persisted.getProperty("global.saturation"), 0.0));
      contrastSlider.setValue(parseDouble(persisted.getProperty("global.contrast"), 0.0));
      bgTintColorPicker.setValue(parseColor(persisted.getProperty("global.bgTintColor"), Color.WHITE));
      bgTintStrengthSlider.setValue(parseDouble(persisted.getProperty("global.bgTintStrength"), 0.0));
      bgSaturationSlider.setValue(parseDouble(persisted.getProperty("global.bgSaturation"), 0.0));
      bgContrastSlider.setValue(parseDouble(persisted.getProperty("global.bgContrast"), 0.0));
      bgExposureSlider.setValue(parseDouble(persisted.getProperty("global.bgExposure"), 0));
      bgTintBlendModeBox.getSelectionModel().select(canonicalBlendMode(persisted.getProperty("global.bgTintBlend"), "Normal"));
      bgOverlayColorPicker.setValue(parseColor(persisted.getProperty("global.bgOverlayColor"), Color.BLACK));
      bgOverlayOpacitySlider.setValue(parseDouble(persisted.getProperty("global.bgOverlayOpacity"), 0.0));
      bgOverlayBlendModeBox.getSelectionModel().select(canonicalBlendMode(persisted.getProperty("global.bgOverlayBlend"), "Overlay"));
      updateBackgroundSwatches();
      showLightVectorsCheck.setSelected(parseBoolean(persisted.getProperty("global.showLightVectors"), true));
      zoom = clamp(parseDouble(persisted.getProperty("global.zoom"), 1.0), 0.1, 8.0);
      offsetX = parseDouble(persisted.getProperty("global.offsetX"), 0.0);
      offsetY = parseDouble(persisted.getProperty("global.offsetY"), 0.0);
      sidebarDividerPosition = clampSidebarDivider(
          parseDouble(persisted.getProperty("global.sidebarDivider"), DEFAULT_SIDEBAR_DIVIDER),
          DEFAULT_SIDEBAR_DIVIDER);
      sidebarCollapsed = parseBoolean(persisted.getProperty("global.sidebarCollapsed"), false);

      if (zonesPane != null) {
        boolean hideZones = parseBoolean(persisted.getProperty("global.hideZones"), true);
        zonesPane.setExpanded(!hideZones);
      }

    } finally {
      applyingState = false;
    }
    setSidebarCollapsed(sidebarCollapsed, false);
    updateExportControls();
    activeZoneProfileTag = normalize(selectedCharacterTag());
    loadPersistedZones();
    loadSceneLightsFromPrefix("global.light.");
    loadSceneOccludersFromPrefix("global.occluder.");
    loadMaterialFromPrefix("global.material.");
    applyBackgroundTintIfPresent(selectedBackgroundTag());
  }

  private void setSidebarCollapsed(boolean collapsed, boolean persist) {
    if (workspaceSplit == null || previewHost == null || sidebarScroll == null) return;
    if (collapsed) {
      captureSidebarDividerPosition();
      if (workspaceSplit.getItems().size() != 1 || workspaceSplit.getItems().get(0) != previewHost) {
        workspaceSplit.getItems().setAll(previewHost);
      }
      sidebarCollapsed = true;
    } else {
      if (workspaceSplit.getItems().size() != 2 || workspaceSplit.getItems().get(1) != sidebarScroll) {
        workspaceSplit.getItems().setAll(previewHost, sidebarScroll);
      }
      sidebarCollapsed = false;
      double divider = workspaceSplit.getOrientation() == Orientation.VERTICAL ? 0.52
          : clampSidebarDivider(sidebarDividerPosition, DEFAULT_SIDEBAR_DIVIDER);
      Platform.runLater(() -> workspaceSplit.setDividerPositions(divider));
    }
    updateSidebarToggleButtons();
    if (persist && !applyingState) {
      persistGlobalState();
    }
  }

  private void updateSidebarToggleButtons() {
    boolean collapsed = sidebarCollapsed;
    if (sidebarHideButton != null) {
      sidebarHideButton.setManaged(!collapsed);
      sidebarHideButton.setVisible(!collapsed);
    }
    if (sidebarShowButton != null) {
      sidebarShowButton.setManaged(collapsed);
      sidebarShowButton.setVisible(collapsed);
    }
  }

  private void captureSidebarDividerPosition() {
    if (workspaceSplit.getOrientation() == Orientation.VERTICAL || workspaceSplit.getItems().size() < 2
        || workspaceSplit.getDividers().isEmpty()) return;
    sidebarDividerPosition = clampSidebarDivider(workspaceSplit.getDividerPositions()[0], DEFAULT_SIDEBAR_DIVIDER);
  }

  private static double clampSidebarDivider(double value, double fallback) {
    if (!Double.isFinite(value)) return fallback;
    return Math.max(0.35, Math.min(0.90, value));
  }

  private void ensureDefaultSelections() {
    boolean changed = false;
    applyingState = true;
    try {
      String currentCharacter = selectedCharacterTag();
      if (currentCharacter.isBlank() || !isKnownCharacterTag(currentCharacter)) {
        String fallbackCharacter = pickDefaultCharacterTag(buildCharacterTagList(normalize(assetScopeBox.getValue())));
        if (!fallbackCharacter.isBlank()) {
          setComboTagValue(characterTagBox, fallbackCharacter);
          changed = true;
        }
      }

      String currentBackground = selectedBackgroundTag();
      if (currentBackground.isBlank() || !imageByTag.containsKey(currentBackground)) {
        String fallbackBackground = pickDefaultBackgroundTag(new ArrayList<>(imageByTag.keySet()));
        if (!fallbackBackground.isBlank()) {
          setComboTagValue(backgroundTagBox, fallbackBackground);
          changed = true;
        }
      }
    } finally {
      applyingState = false;
    }

    if (changed) {
      applyBackgroundTintIfPresent(selectedBackgroundTag());
      switchZoneProfileForCharacter(selectedCharacterTag(), false);
      refreshSceneEntitySelector(true);
      persistGlobalState();
    }
  }

  private void refreshSceneEntitySelector(boolean preserveSelectedTags) {
    String previousCharacter = preserveSelectedTags ? selectedCharacterTag() : "";
    String previousBackground = preserveSelectedTags ? selectedBackgroundTag() : "";

    sceneEntityProject = new AnimationProject();
    sceneEntityScene = new JesScene2D();
    sceneSelectorBindings.clear();

    String backgroundTag = selectedBackgroundTag();
    if (!backgroundTag.isBlank()) {
      String entityName = uniqueSelectorEntityName("bg_" + selectorName(backgroundTag));
      Sprite2D sprite = new Sprite2D(resolveSelectorImagePath(backgroundTag), 1, 1);
      sprite.setZ(-100);
      sceneEntityScene.add(sprite);
      sceneEntityScene.registerEntity(entityName, sprite);
      EntityTrack track = sceneEntityProject.getOrCreateTrack(entityName);
      track.setLayerOrder(-100);
      sceneSelectorBindings.put(selectorKey(entityName, false), new SceneSelectorBinding(backgroundTag, true));
    }

    String characterTag = selectedCharacterTag();
    PresetTagEntry preset = presetByTag.get(characterTag);
    if (preset != null && !preset.layerTags().isEmpty()) {
      String groupName = uniqueSelectorGroupName(selectorName(characterTag));
      sceneEntityProject.getOrCreateGroup(groupName);
      sceneSelectorBindings.put(selectorKey(groupName, true), new SceneSelectorBinding(characterTag, false));
      int layerIndex = 0;
      for (String layerTag : preset.layerTags()) {
        String normalizedLayer = normalize(layerTag);
        if (normalizedLayer.isBlank()) continue;
        String entityName = uniqueSelectorEntityName(selectorName(characterTag) + "_" + selectorName(normalizedLayer));
        Sprite2D sprite = new Sprite2D(resolveSelectorImagePath(normalizedLayer), 1, 1);
        sprite.setZ(layerIndex);
        sceneEntityScene.add(sprite);
        sceneEntityScene.registerEntity(entityName, sprite);
        EntityTrack track = sceneEntityProject.getOrCreateTrack(entityName);
        track.setLayerOrder(layerIndex);
        sceneEntityProject.addEntityToGroup(entityName, groupName);
        sceneSelectorBindings.put(selectorKey(entityName, false), new SceneSelectorBinding(characterTag, false));
        layerIndex++;
      }
    } else if (!characterTag.isBlank()) {
      String entityName = uniqueSelectorEntityName("char_" + selectorName(characterTag));
      Sprite2D sprite = new Sprite2D(resolveSelectorImagePath(characterTag), 1, 1);
      sprite.setZ(0);
      sceneEntityScene.add(sprite);
      sceneEntityScene.registerEntity(entityName, sprite);
      sceneEntityProject.getOrCreateTrack(entityName);
      sceneSelectorBindings.put(selectorKey(entityName, false), new SceneSelectorBinding(characterTag, false));
    }

    syncingSceneEntitySelector = true;
    try {
      sceneEntitySelector.setScene(sceneEntityScene);
      sceneEntitySelector.refresh(sceneEntityProject);
      if (preserveSelectedTags) {
        selectSceneEntityForTag(previousCharacter, false);
        if (sceneEntitySelector.isGroupSelected() || !previousCharacter.isBlank()) return;
        selectSceneEntityForTag(previousBackground, true);
      }
    } finally {
      syncingSceneEntitySelector = false;
    }
  }

  private void selectSceneEntityForTag(String tag, boolean background) {
    String normalized = normalize(tag);
    if (normalized.isBlank()) return;
    for (Map.Entry<String, SceneSelectorBinding> entry : sceneSelectorBindings.entrySet()) {
      SceneSelectorBinding binding = entry.getValue();
      if (binding == null || binding.background() != background || !normalized.equals(binding.tag())) continue;
      String key = entry.getKey();
      if (key.startsWith("group:")) {
        sceneEntitySelector.selectGroup(key.substring("group:".length()));
      } else if (key.startsWith("entity:")) {
        sceneEntitySelector.selectEntity(key.substring("entity:".length()));
      }
      return;
    }
  }

  private String uniqueSelectorEntityName(String requested) {
    String base = selectorName(requested);
    if (base.isBlank()) base = "entity";
    String name = base;
    int suffix = 2;
    while (sceneEntityScene != null && sceneEntityScene.find(name) != null) {
      name = base + "_" + suffix++;
    }
    return name;
  }

  private String uniqueSelectorGroupName(String requested) {
    String base = selectorName(requested);
    if (base.isBlank()) base = "group";
    String name = base;
    int suffix = 2;
    while (sceneEntityProject != null && sceneEntityProject.getGroup(name) != null) {
      name = base + "_" + suffix++;
    }
    return name;
  }

  private String resolveSelectorImagePath(String tag) {
    String normalized = normalize(tag);
    File file = imageByTag.get(normalized);
    if (file != null) return file.getPath();
    if (projectRoot != null && !normalized.isBlank()) {
      return projectRoot.toPath().resolve(normalized).toString();
    }
    return normalized.isBlank() ? "selector-placeholder.png" : normalized;
  }

  private static String selectorKey(String name, boolean group) {
    return (group ? "group:" : "entity:") + normalize(name);
  }

  private static String selectorName(String raw) {
    String value = normalize(raw);
    if (value.startsWith(PRESET_TAG_PREFIX)) value = value.substring(PRESET_TAG_PREFIX.length());
    value = value.replace('\\', '/');
    int slash = value.lastIndexOf('/');
    if (slash >= 0 && slash < value.length() - 1) value = value.substring(slash + 1);
    int dot = value.lastIndexOf('.');
    if (dot > 0) value = value.substring(0, dot);
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-') {
        out.append(ch);
      } else {
        out.append('_');
      }
    }
    String cleaned = out.toString().replaceAll("_+", "_");
    while (cleaned.startsWith("_")) cleaned = cleaned.substring(1);
    while (cleaned.endsWith("_")) cleaned = cleaned.substring(0, cleaned.length() - 1);
    return cleaned;
  }

  private void restoreComboSelection(ComboBox<String> box, String value) {
    if (box == null) return;
    String normalized = normalize(value);
    if (normalized.isBlank()) return;
    if (box.getItems().contains(normalized)) {
      box.getSelectionModel().select(normalized);
    }
    if (box.getEditor() != null) {
      box.getEditor().setText(normalized);
    }
  }

  private void refreshSetupOptions() {
    setupNameToKey.clear();
    List<String> names = new ArrayList<>();
    for (String key : persisted.stringPropertyNames()) {
      if (!key.startsWith("setup.") || !key.endsWith(".name")) continue;
      String setupKey = key.substring("setup.".length(), key.length() - ".name".length());
      String name = persisted.getProperty(key, "");
      if (name.isBlank()) continue;
      setupNameToKey.put(name, setupKey);
      names.add(name);
    }
    names.sort(String.CASE_INSENSITIVE_ORDER);
    setupBox.getItems().setAll(names);
    String selected = persisted.getProperty("global.selectedSetup", "");
    if (!selected.isBlank() && names.contains(selected)) {
      setupBox.getSelectionModel().select(selected);
    }
    updateCatalogSummary();
  }

  private void updateCatalogSummary() {
    if (projectRoot == null || !projectRoot.isDirectory()) return;
    if (scanTask != null && scanTask.isRunning()) return;
    summaryLabel.setText("Catalog: "
        + imageByTag.size()
        + " images · "
        + presetByTag.size()
        + " presets · "
        + setupNameToKey.size()
        + " saved setups");
  }

  private void saveCurrentSetup() {
    String rawName = setupNameField.getText();
    String name = rawName == null ? "" : rawName.trim();
    if (name.isBlank()) {
      status("Setup name is required.");
      return;
    }
    String key = encodeKey(name);
    String prefix = "setup." + key + ".";
    persisted.setProperty(prefix + "name", name);
    persisted.setProperty(prefix + "characterTag", selectedCharacterTag());
    persisted.setProperty(prefix + "backgroundTag", selectedBackgroundTag());
    persisted.setProperty(prefix + "tintColor", colorToHex(tintColorPicker.getValue()));
    persisted.setProperty(prefix + "tintStrength", formatDouble(tintStrengthSlider.getValue()));
    persisted.setProperty(prefix + "saturation", formatDouble(saturationSlider.getValue()));
    persisted.setProperty(prefix + "contrast", formatDouble(contrastSlider.getValue()));
    persisted.setProperty(prefix + "bgTintColor", colorToHex(bgTintColorPicker.getValue()));
    persisted.setProperty(prefix + "bgTintStrength", formatDouble(bgTintStrengthSlider.getValue()));
    persisted.setProperty(prefix + "bgSaturation", formatDouble(bgSaturationSlider.getValue()));
    persisted.setProperty(prefix + "bgContrast", formatDouble(bgContrastSlider.getValue()));
    persisted.setProperty(prefix + "bgExposure", formatDouble(bgExposureSlider.getValue()));
    persisted.setProperty(prefix + "bgTintBlend", canonicalBlendMode(bgTintBlendModeBox.getValue(), "Normal"));
    persisted.setProperty(prefix + "bgOverlayColor", colorToHex(bgOverlayColorPicker.getValue()));
    persisted.setProperty(prefix + "bgOverlayOpacity", formatDouble(bgOverlayOpacitySlider.getValue()));
    persisted.setProperty(prefix + "bgOverlayBlend", canonicalBlendMode(bgOverlayBlendModeBox.getValue(), "Overlay"));
    persisted.setProperty(prefix + "zoom", formatDouble(zoom));
    persisted.setProperty(prefix + "offsetX", formatDouble(offsetX));
    persisted.setProperty(prefix + "offsetY", formatDouble(offsetY));
    writeSceneLightsToPrefix(prefix + "light.");
    writeSceneOccludersToPrefix(prefix + "occluder.");
    writeMaterialToPrefix(prefix + "material.");

    // A: Save zone data with setup.
    clearPrefix(prefix + "zone.");
    persisted.setProperty(prefix + "zone.count", Integer.toString(tintZones.size()));
    for (int i = 0; i < tintZones.size(); i++) {
      TintZone z = tintZones.get(i);
      String zp = prefix + "zone." + i + ".";
      persisted.setProperty(zp + "name", z.name == null ? "" : z.name);
      persisted.setProperty(zp + "boundsX", formatDouble(z.boundsX));
      persisted.setProperty(zp + "boundsY", formatDouble(z.boundsY));
      persisted.setProperty(zp + "boundsW", formatDouble(z.boundsW));
      persisted.setProperty(zp + "boundsH", formatDouble(z.boundsH));
      persisted.setProperty(zp + "color", colorToHex(z.color));
      persisted.setProperty(zp + "strength", formatDouble(z.strength));
      persisted.setProperty(zp + "saturation", formatDouble(z.saturation));
      persisted.setProperty(zp + "contrast", formatDouble(z.contrast));
      persisted.setProperty(zp + "feather", formatDouble(z.feather));
      persisted.setProperty(zp + "rotation", formatDouble(z.rotation));
      persisted.setProperty(zp + "blendMode", canonicalBlendMode(z.blendMode, "Normal"));
      persisted.setProperty(zp + "overlayVisible", Boolean.toString(z.overlayVisible));
      persisted.setProperty(zp + "surface", z.surfaceClass.persistedValue());
      persisted.setProperty(zp + "depthBias", formatDouble(z.depthBias));
      persisted.setProperty(zp + "responseScale", formatDouble(z.responseScale));
      if (z.isPolygon()) {
        StringBuilder polyStr = new StringBuilder();
        for (int p = 0; p < z.polygon.size(); p++) {
          if (p > 0) polyStr.append(";");
          polyStr.append(formatDouble(z.polygon.get(p)[0])).append(",").append(formatDouble(z.polygon.get(p)[1]));
        }
        persisted.setProperty(zp + "polygon", polyStr.toString());
      }
    }

    // E: Sync background per-tag tint.
    persistBackgroundTint(selectedBackgroundTag());

    persisted.setProperty("global.selectedSetup", name);
    savePersistentState();
    refreshSetupOptions();
    setupBox.getSelectionModel().select(name);
    status("Saved setup: " + name + " (" + sceneLights.size() + " lights, " + tintZones.size() + " zones)");
  }

  private void loadSelectedSetup() {
    String name = setupBox.getValue();
    if (name == null || name.isBlank()) {
      status("Select a setup first.");
      return;
    }
    String key = setupNameToKey.get(name);
    if (key == null || key.isBlank()) {
      status("Setup not found.");
      return;
    }
    String prefix = "setup." + key + ".";
    applyingState = true;
    try {
      String characterTag = persisted.getProperty(prefix + "characterTag", "");
      String backgroundTag = persisted.getProperty(prefix + "backgroundTag", "");
      setComboTagValue(characterTagBox, characterTag);
      setComboTagValue(backgroundTagBox, backgroundTag);

      tintColorPicker.setValue(parseColor(persisted.getProperty(prefix + "tintColor"), Color.WHITE));
      tintStrengthSlider.setValue(parseDouble(persisted.getProperty(prefix + "tintStrength"), tintStrengthSlider.getValue()));
      saturationSlider.setValue(parseDouble(persisted.getProperty(prefix + "saturation"), saturationSlider.getValue()));
      contrastSlider.setValue(parseDouble(persisted.getProperty(prefix + "contrast"), contrastSlider.getValue()));
      bgTintColorPicker.setValue(parseColor(persisted.getProperty(prefix + "bgTintColor"), bgTintColorPicker.getValue()));
      bgTintStrengthSlider.setValue(parseDouble(persisted.getProperty(prefix + "bgTintStrength"), bgTintStrengthSlider.getValue()));
      bgSaturationSlider.setValue(parseDouble(persisted.getProperty(prefix + "bgSaturation"), bgSaturationSlider.getValue()));
      bgContrastSlider.setValue(parseDouble(persisted.getProperty(prefix + "bgContrast"), bgContrastSlider.getValue()));
      bgExposureSlider.setValue(parseDouble(persisted.getProperty(prefix + "bgExposure"), 0));
      bgTintBlendModeBox.getSelectionModel().select(canonicalBlendMode(persisted.getProperty(prefix + "bgTintBlend"), bgTintBlendModeBox.getValue()));
      bgOverlayColorPicker.setValue(parseColor(persisted.getProperty(prefix + "bgOverlayColor"), bgOverlayColorPicker.getValue()));
      bgOverlayOpacitySlider.setValue(parseDouble(persisted.getProperty(prefix + "bgOverlayOpacity"), bgOverlayOpacitySlider.getValue()));
      bgOverlayBlendModeBox.getSelectionModel().select(canonicalBlendMode(persisted.getProperty(prefix + "bgOverlayBlend"), bgOverlayBlendModeBox.getValue()));
      zoom = clamp(parseDouble(persisted.getProperty(prefix + "zoom"), zoom), 0.1, 8.0);
      offsetX = parseDouble(persisted.getProperty(prefix + "offsetX"), offsetX);
      offsetY = parseDouble(persisted.getProperty(prefix + "offsetY"), offsetY);
      loadMaterialFromPrefix(prefix + "material.");
      loadSceneLightsFromPrefix(prefix + "light.");
      loadSceneOccludersFromPrefix(prefix + "occluder.");

      // A: Load zone data from setup.
      int zoneCount = (int) parseDouble(persisted.getProperty(prefix + "zone.count"), -1);
      if (zoneCount >= 0) {
        tintZones.clear();
        selectedZoneIndex = -1;
        int loaded = 0;
        for (int i = 0; i < zoneCount; i++) {
          try {
            TintZone z = loadZoneFromPrefix(persisted, prefix + "zone." + i + ".", i);
            tintZones.add(z);
            loaded++;
          } catch (Exception ignored) {
            // reason: malformed zone entry in persisted state; skip and continue loading remaining zones
          }
        }
        refreshZoneList();
        if (!tintZones.isEmpty()) selectZone(0);
        else zoneControlsSection.setDisable(true);
        if (loaded < zoneCount) {
          status("Loaded setup: " + name + " (" + loaded + "/" + zoneCount + " zones, " + (zoneCount - loaded) + " skipped)");
        }
      }
    } finally {
      applyingState = false;
    }

    // E: Sync background per-tag tint with loaded values.
    updateBackgroundSwatches();
    invalidateBackgroundTintCache();
    persistBackgroundTint(selectedBackgroundTag());
    activeZoneProfileTag = normalize(selectedCharacterTag());
    persistZones();

    persisted.setProperty("global.selectedSetup", name);
    persistGlobalState();
    invalidateTintCache();
    invalidateBackgroundTintCache();
    redrawPreview();
    if (statusLabel.getText().isBlank() || !statusLabel.getText().contains("skipped"))
      status("Loaded setup: " + name + " (" + sceneLights.size() + " lights, " + tintZones.size() + " zones)");
  }

  private void deleteSelectedSetup() {
    String name = setupBox.getValue();
    if (name == null || name.isBlank()) {
      status("Select a setup first.");
      return;
    }
    String key = setupNameToKey.get(name);
    if (key == null || key.isBlank()) {
      status("Setup not found.");
      return;
    }
    clearPrefix("setup." + key + ".");
    if (name.equals(persisted.getProperty("global.selectedSetup", ""))) {
      persisted.remove("global.selectedSetup");
    }
    savePersistentState();
    refreshSetupOptions();
    status("Deleted setup: " + name);
  }

  private void resetView() {
    zoom = 1.0;
    offsetX = 0.0;
    offsetY = 0.0;
    invalidateTintCache();
    persistGlobalState();
    redrawPreview();
    status("Reset view.");
  }

  private void resetTintControls() {
    applyingState = true;
    try {
      tintColorPicker.setValue(Color.WHITE);
      tintStrengthSlider.setValue(30.0);
      saturationSlider.setValue(0.0);
      contrastSlider.setValue(0.0);
    } finally {
      applyingState = false;
    }
    onTintChanged(true);
    status("Reset global grade controls.");
  }

  private void requestFullscreenToggle() {
    if (fullscreenToggleHandler != null) {
      fullscreenToggleHandler.run();
      return;
    }
    status("Fullscreen toggle is unavailable in this host.");
  }

  private void updateFullscreenButtonUi() {
    if (fullscreenButton == null) return;
    if (fullscreenActive) {
      fullscreenButton.setGraphic(SidebarToolIcon.clearX("#f38ba8"));
      fullscreenButton.setTooltip(new Tooltip("Exit panel fullscreen"));
    } else {
      fullscreenButton.setGraphic(SidebarToolIcon.expand("#f5c46b"));
      fullscreenButton.setTooltip(new Tooltip("Fullscreen this panel in the current editor window"));
    }
  }

  private void copyTintProfile() {
    String payload = "color=" + colorToHex(tintColorPicker.getValue())
        + " strength=" + formatNormalized(tintStrengthSlider.getValue() / 100.0)
        + " saturation=" + formatNormalized(saturationSlider.getValue() / 100.0)
        + " contrast=" + formatNormalized(contrastSlider.getValue() / 100.0);
    copy(payload);
    status("Copied lighting profile.");
  }

  private void copyFullSetup() {
    copy(buildFullSetupText());
    status("Copied scene setup.");
  }

  private void copySelectedExport() {
    String format = currentExportFormat();
    if (DEFAULT_EXPORT_SETUP.equals(format)) {
      copyFullSetup();
      return;
    }
    if (DEFAULT_EXPORT_STAGE_PRESET.equals(format)) {
      copy(buildStagePresetText());
      status("Copied stage preset.");
      return;
    }
    copyTintProfile();
  }

  private void quickExportSelectedFormat() {
    String format = currentExportFormat();
    if (DEFAULT_EXPORT_PROFILE.equals(format)) {
      copyTintProfile();
      return;
    }
    if (DEFAULT_EXPORT_SETUP.equals(format)) {
      quickExportSetupToFile();
      return;
    }
    if (DEFAULT_EXPORT_STAGE_PRESET.equals(format)) {
      quickExportStagePresetToFile();
      return;
    }
    quickExportTintedPng();
  }

  private void exportSelectedFormatAs() {
    String format = currentExportFormat();
    if (DEFAULT_EXPORT_SETUP.equals(format)) {
      exportSetupToFileAs();
      return;
    }
    if (DEFAULT_EXPORT_STAGE_PRESET.equals(format)) {
      exportStagePresetToFileAs();
      return;
    }
    exportTintedPngAs();
  }

  private void installPreviewInteractions() {
    previewCanvas.setOnMouseClicked(e -> {
      if (e.getButton() == MouseButton.SECONDARY && (polyDrawMode || lightPolyDrawMode || occluderPolyDrawMode)) {
        // Right-click removes last nail point.
        if (!nailPoints.isEmpty()) {
          nailPoints.remove(nailPoints.size() - 1);
          redrawPreview();
        }
        e.consume();
        return;
      }
      if (e.getButton() == MouseButton.SECONDARY && (freehandDrawMode || lightFreehandDrawMode || occluderFreehandDrawMode)) {
        if (!freehandPoints.isEmpty()) {
          freehandPoints.clear();
          drawingFreehand = false;
          redrawPreview();
          status("Freehand stroke cleared.");
        }
        e.consume();
        return;
      }
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (zoneDrawMode || freehandDrawMode || lightFreehandDrawMode || occluderFreehandDrawMode || occluderBoundsDrawMode != null) return;
      if (polyDrawMode || lightPolyDrawMode || occluderPolyDrawMode) {
        double mx = e.getX(), my = e.getY();
        // Close polygon on double-click or clicking near first point.
        if (nailPoints.size() >= 3 && (e.getClickCount() >= 2 || isNearFirstNail(mx, my))) {
          if (polyDrawMode) commitPolygonZone();
          else if (occluderPolyDrawMode) commitDrawnOccluder(nailPoints);
          else commitPolygonSceneLight();
          e.consume();
          return;
        }
        nailPoints.add(new double[]{mx, my});
        redrawPreview();
        int n = nailPoints.size();
        status((polyDrawMode ? "Polygon zone" : occluderPolyDrawMode ? "Shadow caster" : "Polygon light") + ": " + n + " point(s) — "
            + (n >= 3 ? "click near first point to close" : "place more points"));
        e.consume();
        return;
      }
      LightHandleHit lightHit = findLightHandleAt(e.getX(), e.getY());
      if (lightHit != null) {
        selectLight(lightHit.index());
        e.consume();
        return;
      }
      if (e.getClickCount() < 2) return;
      resetView();
      e.consume();
    });

    previewCanvas.setOnMousePressed(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (polyDrawMode || lightPolyDrawMode || occluderPolyDrawMode) return; // handled by onMouseClicked
      if (freehandDrawMode || lightFreehandDrawMode || occluderFreehandDrawMode) {
        drawingFreehand = true;
        freehandPoints.clear();
        freehandPoints.add(new double[]{e.getX(), e.getY()});
        redrawPreview();
        return;
      }
      if (zoneDrawMode || occluderBoundsDrawMode != null) {
        drawingZone = zoneDrawMode;
        drawingOccluderBounds = occluderBoundsDrawMode != null;
        zoneDrawStartX = e.getX();
        zoneDrawStartY = e.getY();
        zoneDrawEndX = e.getX();
        zoneDrawEndY = e.getY();
        redrawPreview();
        return;
      }
      LightHandleHit lightHit = findLightHandleAt(e.getX(), e.getY());
      if (lightHit != null) {
        draggingLight = true;
        draggingLightSource = lightHit.sourceHandle();
        selectLight(lightHit.index());
        dragLastX = e.getX();
        dragLastY = e.getY();
        e.consume();
        return;
      }
      dragging = true;
      dragLastX = e.getX();
      dragLastY = e.getY();
    });
    previewCanvas.setOnMouseDragged(e -> {
      if (polyDrawMode || lightPolyDrawMode || occluderPolyDrawMode) return;
      if ((freehandDrawMode || lightFreehandDrawMode || occluderFreehandDrawMode) && drawingFreehand) {
        appendFreehandPoint(e.getX(), e.getY());
        redrawPreview();
        return;
      }
      if (drawingZone || drawingOccluderBounds) {
        zoneDrawEndX = e.getX();
        zoneDrawEndY = e.getY();
        redrawPreview();
        return;
      }
      if (draggingLight) {
        if (draggingLightSource) moveSelectedLightSourceToCanvas(e.getX(), e.getY());
        else moveSelectedLightToCanvas(e.getX(), e.getY());
        redrawPreview();
        e.consume();
        return;
      }
      if (!dragging) return;
      double dx = e.getX() - dragLastX;
      double dy = e.getY() - dragLastY;
      dragLastX = e.getX();
      dragLastY = e.getY();
      offsetX += dx;
      offsetY += dy;
      redrawPreview();
    });
    previewCanvas.setOnMouseReleased(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (polyDrawMode || lightPolyDrawMode || occluderPolyDrawMode) return;
      if ((freehandDrawMode || lightFreehandDrawMode || occluderFreehandDrawMode) && drawingFreehand) {
        appendFreehandPoint(e.getX(), e.getY());
        drawingFreehand = false;
        if (freehandDrawMode) commitFreehandZone();
        else if (occluderFreehandDrawMode) commitDrawnOccluder(freehandPoints);
        else commitFreehandSceneLight();
        redrawPreview();
        return;
      }
      if (drawingZone || drawingOccluderBounds) {
        if (drawingOccluderBounds) {
          drawingOccluderBounds = false;
          commitDrawnOccluderBounds(e.getX(), e.getY());
        } else {
          drawingZone = false;
          commitDrawnZone();
        }
        redrawPreview();
        return;
      }
      if (draggingLight) {
        draggingLight = false;
        draggingLightSource = false;
        persistGlobalState();
        redrawPreview();
        e.consume();
        return;
      }
      dragging = false;
      invalidateTintCache();
      persistGlobalState();
      redrawPreview();
    });
    previewCanvas.setOnScroll(e -> {
      Image img = loadImage(selectedCharacterTag());
      if (img == null) return;
      double oldZoom = zoom;
      double nextZoom = clamp(oldZoom * Math.pow(1.10, e.getDeltaY() / 40.0), 0.1, 8.0);
      if (Math.abs(nextZoom - oldZoom) < 0.0001) return;

      double cw = previewCanvas.getWidth();
      double ch = previewCanvas.getHeight();
      double oldW = img.getWidth() * oldZoom;
      double oldH = img.getHeight() * oldZoom;
      double oldX = (cw - oldW) * 0.5 + offsetX;
      double oldY = (ch - oldH) * 0.5 + offsetY;
      double localX = (e.getX() - oldX) / Math.max(oldZoom, 0.0001);
      double localY = (e.getY() - oldY) / Math.max(oldZoom, 0.0001);

      zoom = nextZoom;
      double newW = img.getWidth() * nextZoom;
      double newH = img.getHeight() * nextZoom;
      double newX = e.getX() - localX * nextZoom;
      double newY = e.getY() - localY * nextZoom;
      offsetX = newX - (cw - newW) * 0.5;
      offsetY = newY - (ch - newH) * 0.5;
      invalidateTintCache();
      redrawPreview();
      persistGlobalState();
      e.consume();
    });
  }

  private void redrawPreview() {
    GraphicsContext g = previewCanvas.getGraphicsContext2D();
    double w = previewCanvas.getWidth();
    double h = previewCanvas.getHeight();
    g.setFill(Color.web("#161616"));
    g.fillRect(0, 0, w, h);

    boolean original = compareOriginal.isSelected();
    Image bg = loadImage(selectedBackgroundTag());
    if (bg != null) {
      Image previewBg = original ? bg : resolvePreviewBackgroundImage(selectedBackgroundTag(), bg, w, h);
      drawCover(g, previewBg == null ? bg : previewBg, w, h);
    } else {
      cancelBackgroundTintTask();
      drawChecker(g, w, h);
    }
    if (!original && bg == null) {
      drawSceneLightEffects(g, w, h, SceneLightLayer.BACKGROUND);
      drawGroundShadowPreview(g, w, h);
    }

    String characterTag = selectedCharacterTag();
    Image rawCharacter = loadImage(characterTag);
    if (rawCharacter == null) {
      if (!original) {
        drawSceneLightEffects(g, w, h, SceneLightLayer.FOREGROUND);
        drawSceneLightOverlays(g, w, h);
        drawOccluderGuides(g, w, h);
      }
      if (bg == null) drawHint(g, w, h, "Load a VNS scene or select scene assets.");
      previewInfoLabel.setText(bg == null ? "" : "Background only");
      return;
    }

    CharacterRenderContext renderContext = buildCharacterRenderContext(rawCharacter, w, h);
    TintRasterSpec rasterSpec = buildPreviewTintRasterSpec(rawCharacter, renderContext);
    Image tintedCharacter = original ? rawCharacter : buildTintedImage(characterTag, rawCharacter, renderContext, rasterSpec);
    double drawW = renderContext.drawWidth();
    double drawH = renderContext.drawHeight();
    double drawX = renderContext.drawX();
    double drawY = renderContext.drawY();
    g.drawImage(tintedCharacter, drawX, drawY, drawW, drawH);
    if (original) {
      previewInfoLabel.setText("Original scene");
      return;
    }
    drawSceneLightEffects(g, w, h, SceneLightLayer.FOREGROUND);

    drawSceneLightOverlays(g, w, h);
    // Draw zone overlays on the character image region.
    drawZoneOverlays(g, rawCharacter, drawX, drawY, drawW, drawH);
    drawOccluderGuides(g, w, h);

    // Draw in-progress zone rectangle while user is dragging.
    if (zoneDrawMode && drawingZone) {
      double rx = Math.min(zoneDrawStartX, zoneDrawEndX);
      double ry = Math.min(zoneDrawStartY, zoneDrawEndY);
      double rw = Math.abs(zoneDrawEndX - zoneDrawStartX);
      double rh = Math.abs(zoneDrawEndY - zoneDrawStartY);
      g.setStroke(Color.web("#ffcc00"));
      g.setLineDashes(6, 4);
      g.setLineWidth(2);
      g.strokeRect(rx, ry, rw, rh);
      g.setFill(Color.color(1, 0.8, 0, 0.12));
      g.fillRect(rx, ry, rw, rh);
      g.setLineDashes((double[]) null);
    }

    // Draw in-progress polygon nail points.
    if ((polyDrawMode || lightPolyDrawMode) && !nailPoints.isEmpty()) {
      g.setStroke(Color.rgb(255, 120, 120, 0.85));
      g.setLineWidth(1.5);
      g.setLineDashes((double[]) null);
      // Connect placed points.
      if (nailPoints.size() >= 2) {
        for (int i = 0; i < nailPoints.size() - 1; i++) {
          double[] a = nailPoints.get(i);
          double[] b = nailPoints.get(i + 1);
          g.strokeLine(a[0], a[1], b[0], b[1]);
        }
      }
      // Dashed closing line preview.
      if (nailPoints.size() >= 3) {
        double[] first = nailPoints.get(0);
        double[] last = nailPoints.get(nailPoints.size() - 1);
        g.setLineDashes(4, 3);
        g.strokeLine(last[0], last[1], first[0], first[1]);
        g.setLineDashes((double[]) null);
      }
      // Red dots at each nail.
      g.setFill(Color.rgb(255, 80, 80, 0.95));
      for (double[] pt : nailPoints) {
        g.fillOval(pt[0] - 5, pt[1] - 5, 10, 10);
        g.setStroke(Color.WHITE);
        g.setLineWidth(1.2);
        g.strokeOval(pt[0] - 5, pt[1] - 5, 10, 10);
      }
      // Hint label at bottom.
      g.setFill(Color.color(1, 1, 1, 0.8));
      g.setTextAlign(TextAlignment.LEFT);
      String noun = polyDrawMode ? "zone" : "light";
      String hint = nailPoints.size() >= 3
          ? noun + ": " + nailPoints.size() + " pts — click near first point to close polygon"
          : nailPoints.size() + " pts — place more points";
      g.fillText(hint, 8, h - 8);
    }

    if ((freehandDrawMode || lightFreehandDrawMode) && !freehandPoints.isEmpty()) {
      g.setStroke(Color.color(0.43, 0.90, 0.95, 0.95));
      g.setLineWidth(2.4);
      g.setLineDashes((double[]) null);
      for (int i = 0; i < freehandPoints.size() - 1; i++) {
        double[] a = freehandPoints.get(i);
        double[] b = freehandPoints.get(i + 1);
        g.strokeLine(a[0], a[1], b[0], b[1]);
      }
      if (!drawingFreehand && freehandPoints.size() >= 3) {
        double[] first = freehandPoints.get(0);
        double[] last = freehandPoints.get(freehandPoints.size() - 1);
        g.setLineDashes(4, 3);
        g.strokeLine(last[0], last[1], first[0], first[1]);
        g.setLineDashes((double[]) null);
      }
      g.setFill(Color.color(0.65, 0.95, 1.0, 0.9));
      for (double[] pt : freehandPoints) {
        g.fillOval(pt[0] - 2.0, pt[1] - 2.0, 4.0, 4.0);
      }
      g.setFill(Color.color(1, 1, 1, 0.82));
      g.setTextAlign(TextAlignment.LEFT);
      String noun = freehandDrawMode ? "zone" : "light";
      String hint = drawingFreehand
          ? "Freehand " + noun + ": " + freehandPoints.size() + " pts — release to finish"
          : "Freehand " + noun + ": " + freehandPoints.size() + " pts — draw and release";
      g.fillText(hint, 8, h - 8);
    }

    int backgroundLights = 0;
    int characterLights = 0;
    int foregroundLights = 0;
    for (SceneLight light : sceneLights) {
      SceneLightLayer layer = light.layer == null ? SceneLightLayer.CHARACTER : light.layer;
      switch (layer) {
        case BACKGROUND -> backgroundLights++;
        case FOREGROUND -> foregroundLights++;
        case CHARACTER -> characterLights++;
      }
    }
    previewInfoLabel.setText("Subject: " + shortTag(characterTag)
        + "  |  Plate: " + shortTag(selectedBackgroundTag())
        + "  |  Zoom: " + formatNormalized(zoom)
        + "  |  Rig: " + sceneLights.size()
        + " (" + characterLights + "/" + backgroundLights + "/" + foregroundLights + ")"
        + "  |  Local grades: " + tintZones.size());
  }

  private LightHandleHit findLightHandleAt(double canvasX, double canvasY) {
    if (sceneLights.isEmpty()) return null;
    double hitRadiusSq = LIGHT_HANDLE_RADIUS_PX * LIGHT_HANDLE_RADIUS_PX;
    double sourceHitRadiusSq = (LIGHT_HANDLE_RADIUS_PX - 3.0) * (LIGHT_HANDLE_RADIUS_PX - 3.0);
    double canvasWidth = Math.max(1.0, previewCanvas.getWidth());
    double canvasHeight = Math.max(1.0, previewCanvas.getHeight());
    for (int i = sceneLights.size() - 1; i >= 0; i--) {
      SceneLight light = sceneLights.get(i);
      boolean showVectorHandles = showLightVectorsCheck.isSelected() || i == selectedLightIndex;
      if (showVectorHandles) {
        double sourceX = light.sourceX * canvasWidth;
        double sourceY = light.sourceY * canvasHeight;
        double dxSource = canvasX - sourceX;
        double dySource = canvasY - sourceY;
        if ((dxSource * dxSource + dySource * dySource) <= sourceHitRadiusSq) {
          return new LightHandleHit(i, true);
        }
      }
      if (light.isPolygon()) {
        List<double[]> poly = new ArrayList<>(light.polygon.size());
        for (double[] pt : light.polygon) {
          poly.add(new double[]{pt[0] * canvasWidth, pt[1] * canvasHeight});
        }
        if (scenePolygonLightWeightPx(canvasX, canvasY, poly, 14.0, 0.75) > 0.0) {
          return new LightHandleHit(i, false);
        }
      }
      double lightX = light.sceneX * canvasWidth;
      double lightY = light.sceneY * canvasHeight;
      double dx = canvasX - lightX;
      double dy = canvasY - lightY;
      if ((dx * dx + dy * dy) <= hitRadiusSq) {
        return new LightHandleHit(i, false);
      }
    }
    return null;
  }

  private void moveSelectedLightToCanvas(double canvasX, double canvasY) {
    if (selectedLightIndex < 0 || selectedLightIndex >= sceneLights.size()) return;
    SceneLight light = sceneLights.get(selectedLightIndex);
    if (light.locked) return;
    double canvasWidth = Math.max(1.0, previewCanvas.getWidth());
    double canvasHeight = Math.max(1.0, previewCanvas.getHeight());
    double nextX = clamp(canvasX / canvasWidth, 0.0, 1.0);
    double nextY = clamp(canvasY / canvasHeight, 0.0, 1.0);
    double dx = nextX - light.sceneX;
    double dy = nextY - light.sceneY;
    light.sceneX = nextX;
    light.sceneY = nextY;
    light.sourceX = clamp(light.sourceX + dx, 0.0, 1.0);
    light.sourceY = clamp(light.sourceY + dy, 0.0, 1.0);
    if (light.isPolygon()) {
      for (double[] pt : light.polygon) {
        pt[0] = clamp(pt[0] + dx, 0.0, 1.0);
        pt[1] = clamp(pt[1] + dy, 0.0, 1.0);
      }
    }
    invalidateTintCache();
    lightListView.refresh();
  }

  private void moveSelectedLightSourceToCanvas(double canvasX, double canvasY) {
    if (selectedLightIndex < 0 || selectedLightIndex >= sceneLights.size()) return;
    SceneLight light = sceneLights.get(selectedLightIndex);
    if (light.locked) return;
    double canvasWidth = Math.max(1.0, previewCanvas.getWidth());
    double canvasHeight = Math.max(1.0, previewCanvas.getHeight());
    light.sourceX = clamp(canvasX / canvasWidth, 0.0, 1.0);
    light.sourceY = clamp(canvasY / canvasHeight, 0.0, 1.0);
    invalidateTintCache();
    lightListView.refresh();
  }

  private void resetSelectedLightSource() {
    if (selectedLightIndex < 0 || selectedLightIndex >= sceneLights.size()) {
      status("Select a scene light first.");
      return;
    }
    SceneLight light = sceneLights.get(selectedLightIndex);
    light.sourceX = defaultLightSourceX(light.sceneX);
    light.sourceY = defaultLightSourceY(light.sceneY);
    invalidateTintCache();
    lightListView.refresh();
    persistGlobalState();
    redrawPreview();
    status("Reset source vector for " + fallbackLightName(light) + ".");
  }

  private Image resolvePreviewBackgroundImage(String tag, Image source, double width, double height) {
    VnStagePreset stage = previewRig();
    if (!stage.hasBackgroundLighting()) {
      cancelBackgroundTintTask();
      return source;
    }
    int workWidth = Math.max(1, (int) Math.round(Math.min(1100, width)));
    int workHeight = Math.max(1, (int) Math.round(height * workWidth / width));
    String key = tag + "|" + stage.getCacheToken() + "|" + workWidth + "x" + workHeight;
    if (Objects.equals(tintedBackgroundTag, tag) && Objects.equals(tintedBackgroundKey, key) && tintedBackground != null) {
      return tintedBackground;
    }
    ensureBackgroundTintTask(tag, source, key, stage, workWidth, workHeight);
    return Objects.equals(tintedBackgroundTag, tag) ? tintedBackground : null;
  }

  private void ensureBackgroundTintTask(String tag, Image source, String key, VnStagePreset stage, int width, int height) {
    if (backgroundTintTask != null
        && !backgroundTintTask.isDone()
        && Objects.equals(backgroundTintTaskTag, tag)
        && Objects.equals(backgroundTintTaskKey, key)) {
      return;
    }

    cancelBackgroundTintTask();
    final long serial = ++backgroundTintTaskSerial;
    Task<Image> task = new Task<>() {
      @Override
      protected Image call() {
        return VnStageLightingSupport.renderBackgroundPreview(source, stage, width, height);
      }
    };
    backgroundTintTask = task;
    backgroundTintTaskTag = tag;
    backgroundTintTaskKey = key;
    task.setOnSucceeded(e -> {
      if (disposed || serial != backgroundTintTaskSerial) return;
      Image rendered = task.getValue();
      if (rendered != null) {
        tintedBackgroundTag = tag;
        tintedBackgroundKey = key;
        tintedBackground = rendered;
      }
      backgroundTintTask = null;
      backgroundTintTaskTag = null;
      backgroundTintTaskKey = null;
      previewLoadingOverlay.hideOverlay();
      redrawPreview();
    });
    task.setOnCancelled(e -> {
      if (disposed || serial != backgroundTintTaskSerial) return;
      backgroundTintTask = null;
      backgroundTintTaskTag = null;
      backgroundTintTaskKey = null;
      previewLoadingOverlay.hideOverlay();
      redrawPreview();
    });
    task.setOnFailed(e -> {
      if (disposed || serial != backgroundTintTaskSerial) return;
      backgroundTintTask = null;
      backgroundTintTaskTag = null;
      backgroundTintTaskKey = null;
      previewLoadingOverlay.hideOverlay();
      Throwable ex = task.getException();
      status("Background tint render failed: " + (ex == null ? "unknown error" : ex.getMessage()));
      redrawPreview();
    });
    Thread renderThread = new Thread(task, "jvn-bg-tint-render");
    renderThread.setDaemon(true);
    renderThread.start();
  }

  private void cancelBackgroundTintTask() {
    if (backgroundTintTask != null && backgroundTintTask.isRunning()) {
      backgroundTintTask.cancel();
    }
    backgroundTintTask = null;
    backgroundTintTaskTag = null;
    backgroundTintTaskKey = null;
    previewLoadingOverlay.hideOverlay();
  }

  private CharacterRenderContext buildCharacterRenderContext(Image source, double canvasWidth, double canvasHeight) {
    double safeCanvasWidth = Math.max(1.0, canvasWidth);
    double safeCanvasHeight = Math.max(1.0, canvasHeight);
    double sourceWidth = source == null ? 1.0 : Math.max(1.0, source.getWidth());
    double sourceHeight = source == null ? 1.0 : Math.max(1.0, source.getHeight());
    double drawWidth = sourceWidth * zoom;
    double drawHeight = sourceHeight * zoom;
    double drawX = (safeCanvasWidth - drawWidth) * 0.5 + offsetX;
    double drawY = (safeCanvasHeight - drawHeight) * 0.5 + offsetY;
    return new CharacterRenderContext(safeCanvasWidth, safeCanvasHeight, drawX, drawY, drawWidth, drawHeight);
  }

  private TintRasterSpec buildPreviewTintRasterSpec(Image source, CharacterRenderContext renderContext) {
    int sourceWidth = source == null ? 1 : Math.max(1, (int) Math.round(source.getWidth()));
    int sourceHeight = source == null ? 1 : Math.max(1, (int) Math.round(source.getHeight()));
    CharacterRenderContext context = renderContext == null
        ? buildCharacterRenderContext(source, previewCanvas.getWidth(), previewCanvas.getHeight())
        : renderContext;
    boolean interaction = isInteractionPreviewRender();
    int width = computePreviewRasterDimension(sourceWidth, context.drawWidth(), interaction);
    int height = computePreviewRasterDimension(sourceHeight, context.drawHeight(), interaction);
    return new TintRasterSpec(width, height, true, interaction);
  }

  private static TintRasterSpec buildFullResolutionTintRasterSpec(Image source) {
    int sourceWidth = source == null ? 1 : Math.max(1, (int) Math.round(source.getWidth()));
    int sourceHeight = source == null ? 1 : Math.max(1, (int) Math.round(source.getHeight()));
    return new TintRasterSpec(sourceWidth, sourceHeight, false, false);
  }

  private boolean isInteractionPreviewRender() {
    return dragging || draggingLight || drawingZone || drawingFreehand;
  }

  static int computePreviewRasterDimension(int sourceDimension, double drawDimension, boolean interactive) {
    int safeSource = Math.max(1, sourceDimension);
    int hardCap = interactive ? MAX_INTERACTION_PREVIEW_RASTER : MAX_IDLE_PREVIEW_RASTER;
    if (!interactive) {
      // Keep the settled preview sharp. Only the active drag/draw path should
      // fall back to a display-sized raster.
      return Math.max(1, Math.min(safeSource, hardCap));
    }
    double qualityScale = interactive ? INTERACTION_PREVIEW_SCALE : 1.0;
    int target = (int) Math.round(Math.max(1.0, drawDimension * qualityScale));
    return Math.max(1, Math.min(Math.min(safeSource, hardCap), target));
  }

  private static int[] buildSourceIndexMap(int sourceSize, int targetSize) {
    int safeSource = Math.max(1, sourceSize);
    int safeTarget = Math.max(1, targetSize);
    int[] map = new int[safeTarget];
    for (int i = 0; i < safeTarget; i++) {
      map[i] = Math.min(safeSource - 1, (int) Math.floor(((i + 0.5) * safeSource) / safeTarget));
    }
    return map;
  }

  private static double[] buildNormalizedMap(int size) {
    int safeSize = Math.max(1, size);
    double[] map = new double[safeSize];
    for (int i = 0; i < safeSize; i++) {
      map[i] = (i + 0.5) / safeSize;
    }
    return map;
  }

  private static double[] buildSceneCoordinateMap(double start, double span, int size) {
    int safeSize = Math.max(1, size);
    double[] map = new double[safeSize];
    for (int i = 0; i < safeSize; i++) {
      map[i] = start + ((i + 0.5) / safeSize) * span;
    }
    return map;
  }

  private Image buildTintedImage(String tag, Image source, CharacterRenderContext renderContext, TintRasterSpec rasterSpec) {
    if (source == null) return null;
    CharacterRenderContext context = renderContext == null
        ? buildCharacterRenderContext(source, previewCanvas.getWidth(), previewCanvas.getHeight())
        : renderContext;
    TintRasterSpec resolvedRasterSpec = rasterSpec == null ? buildFullResolutionTintRasterSpec(source) : rasterSpec;
    String key = tintKey(tag, source, context, resolvedRasterSpec);
    if (Objects.equals(tintedImageTag, tag) && Objects.equals(tintedImageKey, key) && tintedImage != null) {
      return tintedImage;
    }
    int sourceWidth = (int) Math.max(1, Math.round(source.getWidth()));
    int sourceHeight = (int) Math.max(1, Math.round(source.getHeight()));
    int width = Math.max(1, Math.min(sourceWidth, resolvedRasterSpec.outputWidth()));
    int height = Math.max(1, Math.min(sourceHeight, resolvedRasterSpec.outputHeight()));
    WritableImage out = new WritableImage(width, height);
    PixelReader reader = source.getPixelReader();
    PixelWriter writer = out.getPixelWriter();
    if (reader == null) return source;
    int[] sourceXMap = buildSourceIndexMap(sourceWidth, width);
    int[] sourceYMap = buildSourceIndexMap(sourceHeight, height);
    double[] normalizedX = buildNormalizedMap(width);
    double[] normalizedY = buildNormalizedMap(height);
    double[] sceneXMap = buildSceneCoordinateMap(context.drawX(), context.drawWidth(), width);
    double[] sceneYMap = buildSceneCoordinateMap(context.drawY(), context.drawHeight(), height);

    double tintStrength = clamp(tintStrengthSlider.getValue() / 100.0, 0.0, 1.0);
    double satAdjust = clamp(saturationSlider.getValue() / 100.0, -1.0, 1.0);
    double conAdjust = clamp(contrastSlider.getValue() / 100.0, -1.0, 1.0);
    Color tint = tintColorPicker.getValue() == null ? Color.WHITE : tintColorPicker.getValue();
    double tr = tint.getRed();
    double tg = tint.getGreen();
    double tb = tint.getBlue();

    // Pre-compute zone parameters for performance.
    int zoneCount = tintZones.size();
    double[] zx = new double[zoneCount], zy = new double[zoneCount];
    double[] zw = new double[zoneCount], zh = new double[zoneCount];
    double[] zStr = new double[zoneCount], zSat = new double[zoneCount], zCon = new double[zoneCount];
    double[] zFeath = new double[zoneCount];
    double[] zRot = new double[zoneCount]; // rotation in radians
    double[] zcr = new double[zoneCount], zcg = new double[zoneCount], zcb = new double[zoneCount];
    int[] zBlend = new int[zoneCount];
    @SuppressWarnings("unchecked")
    List<double[]>[] zPoly = new List[zoneCount]; // null = rectangle, non-null = polygon
    for (int i = 0; i < zoneCount; i++) {
      TintZone zone = tintZones.get(i);
      zx[i] = zone.boundsX;
      zy[i] = zone.boundsY;
      zw[i] = zone.boundsW;
      zh[i] = zone.boundsH;
      zStr[i] = clamp(zone.strength / 100.0, 0.0, 1.0);
      zSat[i] = clamp(zone.saturation / 100.0, -1.0, 1.0);
      zCon[i] = clamp(zone.contrast / 100.0, -1.0, 1.0);
      zFeath[i] = clamp(zone.feather / 100.0, 0.0, 1.0);
      zRot[i] = Math.toRadians(zone.rotation);
      zcr[i] = zone.color.getRed();
      zcg[i] = zone.color.getGreen();
      zcb[i] = zone.color.getBlue();
      zBlend[i] = blendModeIndex(zone.blendMode);
      zPoly[i] = zone.isPolygon() ? zone.polygon : null;
    }

    int lightCount = sceneLights.size();
    double[] lightPx = new double[lightCount];
    double[] lightPy = new double[lightCount];
    double[] lightSourcePx = new double[lightCount];
    double[] lightSourcePy = new double[lightCount];
    double[] lightIntensity = new double[lightCount];
    double[] lightSilhouette = new double[lightCount];
    double[] lightR = new double[lightCount];
    double[] lightG = new double[lightCount];
    double[] lightB = new double[lightCount];
    SceneLightLayer[] lightLayer = new SceneLightLayer[lightCount];
    LightBlend[] lightBlend = new LightBlend[lightCount];
    VnStageLightField[] fields = new VnStageLightField[lightCount];
    VnStagePreset.Light[] activeLights = new VnStagePreset.Light[lightCount];
    VnStagePreset rig = previewRig();
    List<VnStageShadow> shadows = VnStageShadow.prepare(rig.getOccluders(), context.canvasWidth(), context.canvasHeight());
    var materialSettings = new VnStagePreset.SubjectMaterial(normalMapField.getText(),
        normalStrengthSlider.getValue() / 100, specularSlider.getValue() / 100, glossSlider.getValue(), List.of());
    VnStageMaterial material = new VnStageMaterial(loadImage(materialSettings.normalMap()), materialSettings);
    double lightHeight = Math.max(1, Math.min(context.canvasWidth(), context.canvasHeight()) * 0.20);
    for (int i = 0; i < lightCount; i++) {
      SceneLight light = sceneLights.get(i);
      VnStagePreset.Light runtimeLight = rig.getLights().get(i);
      if (!rig.isActive(runtimeLight)) continue;
      fields[i] = VnStageLightField.forLight(runtimeLight, context.canvasWidth(), context.canvasHeight(), 1);
      activeLights[i] = runtimeLight;
      lightPx[i] = light.sceneX * context.canvasWidth();
      lightPy[i] = light.sceneY * context.canvasHeight();
      lightSourcePx[i] = light.sourceX * context.canvasWidth();
      lightSourcePy[i] = light.sourceY * context.canvasHeight();
      lightIntensity[i] = runtimeLight.intensity();
      lightBlend[i] = light.blend;
      lightSilhouette[i] = runtimeLight.silhouette();
      lightR[i] = light.color.getRed();
      lightG[i] = light.color.getGreen();
      lightB[i] = light.color.getBlue();
      lightLayer[i] = light.layer;
    }

    double imgAspect = (double) width / height; // for aspect-correct rotation

    for (int y = 0; y < height; y++) {
      int sourceY = sourceYMap[y];
      double ny = normalizedY[y];
      double sceneY = sceneYMap[y];
      for (int x = 0; x < width; x++) {
        int sourceX = sourceXMap[x];
        int argb = reader.getArgb(sourceX, sourceY);
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) {
          writer.setArgb(x, y, argb);
          continue;
        }
        double r = ((argb >>> 16) & 0xFF) / 255.0;
        double g = ((argb >>> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;

        // Global tint pass.
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        r = lum + (r - lum) * (1.0 + satAdjust);
        g = lum + (g - lum) * (1.0 + satAdjust);
        b = lum + (b - lum) * (1.0 + satAdjust);
        r = (r - 0.5) * (1.0 + conAdjust) + 0.5;
        g = (g - 0.5) * (1.0 + conAdjust) + 0.5;
        b = (b - 0.5) * (1.0 + conAdjust) + 0.5;
        r = r * (1.0 - tintStrength) + tr * tintStrength;
        g = g * (1.0 - tintStrength) + tg * tintStrength;
        b = b * (1.0 - tintStrength) + tb * tintStrength;

        // Per-zone tint passes (applied in order, composited on top).
        double nx = normalizedX[x];
        for (int i = 0; i < zoneCount; i++) {
          double weight = zPoly[i] != null
              ? polyZoneWeight(nx, ny, zPoly[i], zFeath[i])
              : zoneWeight(nx, ny, zx[i], zy[i], zw[i], zh[i], zFeath[i], zRot[i], imgAspect);
          if (weight <= 0.0) continue;
          double effStr = zStr[i];

          // Compute zone-tinted pixel from the current (already globally-tinted) color.
          double zl = 0.2126 * r + 0.7152 * g + 0.0722 * b;
          double zr = zl + (r - zl) * (1.0 + zSat[i]);
          double zgg = zl + (g - zl) * (1.0 + zSat[i]);
          double zb = zl + (b - zl) * (1.0 + zSat[i]);
          zr = (zr - 0.5) * (1.0 + zCon[i]) + 0.5;
          zgg = (zgg - 0.5) * (1.0 + zCon[i]) + 0.5;
          zb = (zb - 0.5) * (1.0 + zCon[i]) + 0.5;
          zr = zr * (1.0 - effStr) + zcr[i] * effStr;
          zgg = zgg * (1.0 - effStr) + zcg[i] * effStr;
          zb = zb * (1.0 - effStr) + zcb[i] * effStr;

          // Apply blend mode.
          r = applyBlend(r, zr, weight, zBlend[i]);
          g = applyBlend(g, zgg, weight, zBlend[i]);
          b = applyBlend(b, zb, weight, zBlend[i]);
        }

        if (lightCount > 0 && context.hasDrawableArea()) {
          double sceneX = sceneXMap[x];
          int normal = material.sample(nx, ny);
          double edgeFactor = 0.0;
          double edgeNormalX = 0.0;
          double edgeNormalY = 0.0;
          boolean edgeSampled = false;
          for (int i = 0; i < lightCount; i++) {
            if (lightIntensity[i] == 0) continue;
            double weight = fields[i].weight(sceneX, sceneY);
            if (weight > 0 && activeLights[i].castsShadows() && activeLights[i].groundProjection() == null) {
              weight *= fields[i].transmission(shadows, sceneX, sceneY, activeLights[i].shadowQuality());
            }
            if (weight <= 0.0) continue;
            if (lightLayer[i] == SceneLightLayer.CHARACTER) {
              double influence = weight * lightIntensity[i]
                  * sceneLightDirectionalBias(
                      sceneX,
                      sceneY,
                      lightPx[i],
                      lightPy[i],
                      lightSourcePx[i],
                      lightSourcePy[i])
                  * material.response(normal, lightSourcePx[i] - sceneX, lightSourcePy[i] - sceneY, lightHeight);
              if (lightBlend[i] != LightBlend.PHYSICAL) {
                r = lightBlend[i].composite(r, lightR[i], influence);
                g = lightBlend[i].composite(g, lightG[i], influence);
                b = lightBlend[i].composite(b, lightB[i], influence);
                continue;
              }
              double albedo = linearLuminance(r, g, b);
              r = applySceneLightChannel(r, lightR[i], influence, albedo, false);
              g = applySceneLightChannel(g, lightG[i], influence, albedo, false);
              b = applySceneLightChannel(b, lightB[i], influence, albedo, false);
              double tintWeight = clamp(influence * (0.10 + 0.10 * (1.0 - albedo)), 0.0, 0.28);
              r = applyBlend(r, lightR[i], tintWeight, 4);
              g = applyBlend(g, lightG[i], tintWeight, 4);
              b = applyBlend(b, lightB[i], tintWeight, 4);
              continue;
            }
            if (lightSilhouette[i] <= 0.0) continue;
            if (!edgeSampled) {
              int left = alphaAt(reader, sourceX - 1, sourceY, sourceWidth, sourceHeight);
              int right = alphaAt(reader, sourceX + 1, sourceY, sourceWidth, sourceHeight);
              int up = alphaAt(reader, sourceX, sourceY - 1, sourceWidth, sourceHeight);
              int down = alphaAt(reader, sourceX, sourceY + 1, sourceWidth, sourceHeight);
              double diff = Math.max(
                  Math.max(Math.abs(a - left), Math.abs(a - right)),
                  Math.max(Math.abs(a - up), Math.abs(a - down))
              ) / 255.0;
              edgeFactor = diff > 0.0
                  ? clamp(diff, 0.0, 1.0)
                  : clamp(1.0 - ((left + right + up + down) / (4.0 * 255.0)), 0.0, 1.0);
              edgeNormalX = left - right;
              edgeNormalY = up - down;
              double normalLength = Math.hypot(edgeNormalX, edgeNormalY);
              if (normalLength > 1e-6) {
                edgeNormalX /= normalLength;
                edgeNormalY /= normalLength;
              } else {
                edgeNormalX = 0.0;
                edgeNormalY = 0.0;
              }
              edgeSampled = true;
            }
            if (edgeFactor <= 0.0) continue;
            double lightDirX = lightSourcePx[i] - sceneX;
            double lightDirY = lightSourcePy[i] - sceneY;
            double lightDirLength = Math.hypot(lightDirX, lightDirY);
            double facing = 1.0;
            if (lightDirLength > 1e-6 && (Math.abs(edgeNormalX) > 1e-6 || Math.abs(edgeNormalY) > 1e-6)) {
              facing = clamp(
                  edgeNormalX * (lightDirX / lightDirLength) + edgeNormalY * (lightDirY / lightDirLength),
                  0.0,
                  1.0
              );
            }
            double directionalFactor = 0.28 + 0.72 * facing;
            double rimWeight = weight * lightIntensity[i] * lightSilhouette[i] * edgeFactor * directionalFactor;
            double rimBoost = lightLayer[i] == SceneLightLayer.BACKGROUND ? 1.35 : 1.08;
            double albedo = linearLuminance(r, g, b);
            if (lightBlend[i] == LightBlend.PHYSICAL) {
              r = applySceneLightChannel(r, lightR[i], rimWeight * rimBoost, albedo, true);
              g = applySceneLightChannel(g, lightG[i], rimWeight * rimBoost, albedo, true);
              b = applySceneLightChannel(b, lightB[i], rimWeight * rimBoost, albedo, true);
            } else {
              r = lightBlend[i].composite(r, lightR[i], rimWeight * rimBoost);
              g = lightBlend[i].composite(g, lightG[i], rimWeight * rimBoost);
              b = lightBlend[i].composite(b, lightB[i], rimWeight * rimBoost);
            }
            double rimTintWeight = clamp(rimWeight * 0.20, 0.0, 0.34);
            r = applyBlend(r, lightR[i], rimTintWeight, 4);
            g = applyBlend(g, lightG[i], rimTintWeight, 4);
            b = applyBlend(b, lightB[i], rimTintWeight, 4);
          }
        }

        int rr = (int) Math.round(clamp(r, 0.0, 1.0) * 255.0);
        int gg = (int) Math.round(clamp(g, 0.0, 1.0) * 255.0);
        int bb = (int) Math.round(clamp(b, 0.0, 1.0) * 255.0);
        writer.setArgb(x, y, (a << 24) | (rr << 16) | (gg << 8) | bb);
      }
    }

    tintedImageTag = tag;
    tintedImageKey = key;
    tintedImage = out;
    return out;
  }

  private static double zoneWeight(double nx, double ny, double zx, double zy, double zw, double zh, double feather, double rotRad, double imgAspect) {
    if (zw <= 0 || zh <= 0) return 0.0;
    // Rotate test point into the rectangle's local (un-rotated) coordinate frame.
    // We must rotate in pixel-equivalent space (aspect-corrected) so that the
    // tinting matches the overlay which rotates in pixel space via g.rotate().
    if (rotRad != 0.0) {
      double cx = zx + zw * 0.5, cy = zy + zh * 0.5;
      double cosR = Math.cos(-rotRad), sinR = Math.sin(-rotRad);
      double dx = nx - cx;
      double dy = (ny - cy) * imgAspect; // scale to pixel-proportional space
      nx = cx + dx * cosR - dy * sinR;
      ny = cy + (dx * sinR + dy * cosR) / imgAspect; // scale back
    }
    double x1 = zx, y1 = zy, x2 = zx + zw, y2 = zy + zh;
    if (nx < x1 || nx > x2 || ny < y1 || ny > y2) {
      if (feather <= 0.0) return 0.0;
      // Outside: compute distance-based falloff.
      double dx = nx < x1 ? x1 - nx : (nx > x2 ? nx - x2 : 0.0);
      double dy = ny < y1 ? y1 - ny : (ny > y2 ? ny - y2 : 0.0);
      double dist = Math.sqrt(dx * dx + dy * dy);
      if (dist >= feather) return 0.0;
      return 1.0 - dist / feather;
    }
    // Inside: always fully tinted. Feather applies outward from the boundary only.
    return 1.0;
  }

  private static double polyZoneWeight(double px, double py, List<double[]> poly, double feather) {
    int n = poly.size();
    if (n < 3) return 0.0;
    // Ray-casting point-in-polygon test.
    boolean inside = false;
    for (int i = 0, j = n - 1; i < n; j = i++) {
      double yi = poly.get(i)[1], yj = poly.get(j)[1];
      double xi = poly.get(i)[0], xj = poly.get(j)[0];
      if ((yi > py) != (yj > py) &&
          px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
        inside = !inside;
      }
    }
    if (inside) {
      // Inside: always fully tinted. Feather applies outward from the boundary only.
      return 1.0;
    } else {
      if (feather <= 0.0) return 0.0;
      // Outside: distance-based falloff from polygon edges.
      double minDist = Double.MAX_VALUE;
      for (int i = 0, j = n - 1; i < n; j = i++) {
        double d = pointToSegmentDist(px, py,
            poly.get(j)[0], poly.get(j)[1],
            poly.get(i)[0], poly.get(i)[1]);
        if (d < minDist) minDist = d;
      }
      if (minDist >= feather) return 0.0;
      return 1.0 - minDist / feather;
    }
  }

  private static double pointToSegmentDist(double px, double py, double ax, double ay, double bx, double by) {
    double dx = bx - ax, dy = by - ay;
    double lenSq = dx * dx + dy * dy;
    if (lenSq < 1e-12) return Math.sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay));
    double t = clamp(((px - ax) * dx + (py - ay) * dy) / lenSq, 0.0, 1.0);
    double cx = ax + t * dx, cy = ay + t * dy;
    return Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy));
  }

  static double sceneLightWeightPx(double px, double py, double lightX, double lightY, double radiusPx, double softness) {
    return new VnStageLightField(VnStagePreset.LightType.RADIAL, lightX, lightY, lightX, lightY,
        radiusPx, softness, LightFalloff.CLASSIC, List.of()).weight(px, py);
  }

  static double scenePolygonLightWeightPx(double px, double py, List<double[]> polygon, double radiusPx, double softness) {
    return new VnStageLightField(VnStagePreset.LightType.POLYGON, px, py, px, py,
        radiusPx, softness, LightFalloff.CLASSIC, polygon).weight(px, py);
  }

  private static int alphaAt(PixelReader reader, int x, int y, int width, int height) {
    if (reader == null || width <= 0 || height <= 0) return 0;
    int safeX = Math.max(0, Math.min(width - 1, x));
    int safeY = Math.max(0, Math.min(height - 1, y));
    return (reader.getArgb(safeX, safeY) >>> 24) & 0xFF;
  }

  static double alphaEdgeWeight(PixelReader reader, int x, int y, int width, int height) {
    if (reader == null || width <= 0 || height <= 0) return 0.0;
    int center = alphaAt(reader, x, y, width, height);
    if (center == 0) return 0.0;
    int left = alphaAt(reader, x - 1, y, width, height);
    int right = alphaAt(reader, x + 1, y, width, height);
    int up = alphaAt(reader, x, y - 1, width, height);
    int down = alphaAt(reader, x, y + 1, width, height);
    double diff = Math.max(
        Math.max(Math.abs(center - left), Math.abs(center - right)),
        Math.max(Math.abs(center - up), Math.abs(center - down))
    ) / 255.0;
    if (diff > 0.0) return clamp(diff, 0.0, 1.0);
    double neighborOpacity = (left + right + up + down) / (4.0 * 255.0);
    return clamp(1.0 - neighborOpacity, 0.0, 1.0);
  }

  static double sceneLightDirectionalBias(double sceneX,
                                          double sceneY,
                                          double targetX,
                                          double targetY,
                                          double sourceX,
                                          double sourceY) {
    double towardSourceX = sourceX - targetX;
    double towardSourceY = sourceY - targetY;
    double towardSourceLength = Math.hypot(towardSourceX, towardSourceY);
    double pixelOffsetX = sceneX - targetX;
    double pixelOffsetY = sceneY - targetY;
    double pixelOffsetLength = Math.hypot(pixelOffsetX, pixelOffsetY);
    if (towardSourceLength < 1e-6 || pixelOffsetLength < 1e-6) return 1.0;
    double facing = clamp(
        (pixelOffsetX / pixelOffsetLength) * (towardSourceX / towardSourceLength)
            + (pixelOffsetY / pixelOffsetLength) * (towardSourceY / towardSourceLength),
        -1.0,
        1.0
    );
    return 0.78 + 0.22 * Math.max(0.0, facing);
  }

  private static double linearLuminance(double r, double g, double b) {
    return clamp(
        0.2126 * srgbToLinear(r) + 0.7152 * srgbToLinear(g) + 0.0722 * srgbToLinear(b),
        0.0,
        1.0
    );
  }

  static double applySceneLightChannel(double base, double light, double influence, double albedo, boolean rim) {
    double safeBase = clamp(base, 0.0, 1.0);
    double safeLight = clamp(light, 0.0, 1.0);
    double strength = Math.max(0.0, influence);
    if (strength <= 1e-6) return safeBase;
    double safeAlbedo = clamp(albedo, 0.0, 1.0);
    double exposure = strength * (rim ? 1.85 : 1.35) * (0.55 + 0.45 * Math.sqrt(Math.max(0.02, safeAlbedo)));
    double litLinear = 1.0 - Math.exp(-(srgbToLinear(safeBase) + srgbToLinear(safeLight) * exposure));
    double lit = linearToSrgb(clamp(litLinear, 0.0, 1.0));
    double blendWeight = clamp(strength * (rim ? 0.92 : 0.78), 0.0, 1.0);
    return clamp(safeBase * (1.0 - blendWeight) + lit * blendWeight, 0.0, 1.0);
  }

  private static double applyBlend(double base, double tone, double weight, int mode) {
    return VnStageGradeBlend.composite(base, tone, weight, mode);
  }

  private static int blendModeIndex(String mode) {
    return VnStageGradeBlend.index(canonicalBlendMode(mode, "Normal"));
  }



  private static double srgbToLinear(double value) {
    double v = clamp(value, 0.0, 1.0);
    if (v <= 0.04045) return v / 12.92;
    return Math.pow((v + 0.055) / 1.055, 2.4);
  }

  private static double linearToSrgb(double value) {
    double v = clamp(value, 0.0, 1.0);
    if (v <= 0.0031308) return v * 12.92;
    return 1.055 * Math.pow(v, 1.0 / 2.4) - 0.055;
  }

  private String tintKey(String tag, Image source, CharacterRenderContext context, TintRasterSpec rasterSpec) {
    StringBuilder sb = new StringBuilder();
    sb.append(normalize(tag))
        .append("|").append(source.getWidth()).append("x").append(source.getHeight())
        .append("|raster:")
        .append(rasterSpec == null ? "full" : rasterSpec.cacheToken())
        .append(":")
        .append(rasterSpec == null ? (int) Math.round(source.getWidth()) : rasterSpec.outputWidth())
        .append("x")
        .append(rasterSpec == null ? (int) Math.round(source.getHeight()) : rasterSpec.outputHeight())
        .append("|").append(colorToHex(tintColorPicker.getValue()))
        .append("|").append(formatDouble(tintStrengthSlider.getValue()))
        .append("|").append(formatDouble(saturationSlider.getValue()))
        .append("|").append(formatDouble(contrastSlider.getValue()));
    for (int i = 0; i < tintZones.size(); i++) {
      TintZone z = tintZones.get(i);
      sb.append("|z").append(i).append(":")
          .append(formatDouble(z.boundsX)).append(",").append(formatDouble(z.boundsY)).append(",")
          .append(formatDouble(z.boundsW)).append(",").append(formatDouble(z.boundsH)).append(",")
          .append(colorToHex(z.color)).append(",")
          .append(formatDouble(z.strength)).append(",").append(formatDouble(z.saturation)).append(",")
          .append(formatDouble(z.contrast)).append(",").append(formatDouble(z.feather)).append(",")
          .append(formatDouble(z.rotation)).append(",").append(canonicalBlendMode(z.blendMode, "Normal"));
      if (z.isPolygon()) {
        sb.append(",poly");
        for (double[] pt : z.polygon) {
          sb.append(";").append(formatDouble(pt[0])).append(",").append(formatDouble(pt[1]));
        }
      }
    }
    if (!sceneLights.isEmpty()) {
      CharacterRenderContext resolved = context == null
          ? buildCharacterRenderContext(source, previewCanvas.getWidth(), previewCanvas.getHeight())
          : context;
      sb.append("|ctx:")
          .append(formatDouble(resolved.canvasWidth())).append(",")
          .append(formatDouble(resolved.canvasHeight())).append(",")
          .append(formatDouble(resolved.drawX())).append(",")
          .append(formatDouble(resolved.drawY())).append(",")
          .append(formatDouble(resolved.drawWidth())).append(",")
          .append(formatDouble(resolved.drawHeight()));
      for (int i = 0; i < sceneLights.size(); i++) {
        SceneLight light = sceneLights.get(i);
        sb.append("|l").append(i).append(":")
            .append(formatDouble(light.sceneX)).append(",")
            .append(formatDouble(light.sceneY)).append(",")
            .append(formatDouble(light.sourceX)).append(",")
            .append(formatDouble(light.sourceY)).append(",")
            .append(colorToHex(light.color)).append(",")
            .append(formatDouble(light.intensity)).append(",")
            .append(formatDouble(light.radius)).append(",")
            .append(formatDouble(light.softness)).append(",")
            .append(formatDouble(light.silhouette)).append(",")
            .append(light.shape == null ? SceneLightShape.RADIAL.persistedValue() : light.shape.persistedValue()).append(",")
            .append(light.layer == null ? SceneLightLayer.CHARACTER.persistedValue() : light.layer.persistedValue());
        if (light.isPolygon()) {
          sb.append(",poly");
          for (double[] pt : light.polygon) {
            sb.append(";").append(formatDouble(pt[0])).append(",").append(formatDouble(pt[1]));
          }
        }
      }
    }
    return sb.toString();
  }

  private Image loadImage(String tag) {
    if (tag == null || tag.isBlank()) return null;
    String normalized = tag.trim().replace('\\', '/');
    if (normalized.isBlank()) return null;
    Image cached = imageCache.get(normalized);
    if (cached != null) return cached;

    PresetTagEntry preset = sceneSubjectLayers.getOrDefault(normalized, presetByTag.get(normalized));
    if (preset != null) {
      Image composed = composePresetImage(preset);
      if (composed != null) {
        imageCache.put(normalized, composed);
      }
      return composed;
    }

    File file = imageByTag.get(normalized);
    if (file == null && projectRoot != null) {
      file = projectRoot.toPath().resolve(normalized).toFile();
    }
    if (file == null || !file.isFile()) return null;
    try (InputStream in = Files.newInputStream(file.toPath())) {
      Image image = new Image(in);
      if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) return null;
      imageCache.put(normalized, image);
      return image;
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return null;
    }
  }

  private Image composePresetImage(PresetTagEntry preset) {
    if (preset == null || preset.layerTags().isEmpty()) return null;
    List<Image> layers = new ArrayList<>();
    int width = 1;
    int height = 1;
    for (String layerTag : preset.layerTags()) {
      String normalizedLayerTag = normalize(layerTag);
      if (normalizedLayerTag.isBlank() || normalizedLayerTag.equals(preset.tag())) continue;
      Image layer = loadImage(normalizedLayerTag);
      if (layer == null) continue;
      layers.add(layer);
      width = Math.max(width, (int) Math.round(layer.getWidth()));
      height = Math.max(height, (int) Math.round(layer.getHeight()));
    }
    if (layers.isEmpty()) return null;
    Canvas canvas = new Canvas(width, height);
    GraphicsContext gc = canvas.getGraphicsContext2D();
    for (Image layer : layers) {
      gc.drawImage(layer, 0, 0);
    }
    SnapshotParameters snapshotParameters = new SnapshotParameters();
    snapshotParameters.setFill(Color.TRANSPARENT);
    WritableImage out = new WritableImage(width, height);
    canvas.snapshot(snapshotParameters, out);
    return out;
  }

  private void drawCover(GraphicsContext g, Image image, double w, double h) {
    double iw = image.getWidth();
    double ih = image.getHeight();
    if (iw <= 0 || ih <= 0) return;
    double scale = Math.max(w / iw, h / ih);
    double drawW = iw * scale;
    double drawH = ih * scale;
    double x = (w - drawW) * 0.5;
    double y = (h - drawH) * 0.5;
    g.drawImage(image, x, y, drawW, drawH);
  }

  private static void drawChecker(GraphicsContext g, double w, double h) {
    g.setFill(Color.web("#242424"));
    g.fillRect(0, 0, w, h);
    double tile = 20.0;
    for (double y = 0; y < h; y += tile) {
      for (double x = 0; x < w; x += tile) {
        boolean alt = (((int) (x / tile)) + ((int) (y / tile))) % 2 == 0;
        g.setFill(alt ? Color.web("#2b2b2b") : Color.web("#232323"));
        g.fillRect(x, y, tile, tile);
      }
    }
  }

  private record CharacterRenderContext(
      double canvasWidth,
      double canvasHeight,
      double drawX,
      double drawY,
      double drawWidth,
      double drawHeight
  ) {
    boolean hasDrawableArea() {
      return canvasWidth > 1.0 && canvasHeight > 1.0 && drawWidth > 1.0 && drawHeight > 1.0;
    }
  }

  private record TintRasterSpec(
      int outputWidth,
      int outputHeight,
      boolean preview,
      boolean interaction
  ) {
    String cacheToken() {
      return preview ? (interaction ? "preview-interaction" : "preview") : "full";
    }
  }

  private record LightHandleHit(int index, boolean sourceHandle) {}

  private static void drawHint(GraphicsContext g, double w, double h, String text) {
    g.setFill(Color.color(0, 0, 0, 0.55));
    g.fillRect(0, h - 38, w, 38);
    g.setFill(Color.web("#d4d9e2"));
    g.setTextAlign(TextAlignment.LEFT);
    g.fillText(text, 12, h - 14);
  }

  private void persistBackgroundTint(String backgroundTag) {
    String tag = normalize(backgroundTag);
    if (tag.isBlank()) return;
    String prefix = "bg." + encodeKey(tag) + ".";
    String tintColor = colorToHex(bgTintColorPicker.getValue());
    String tintStrength = formatDouble(bgTintStrengthSlider.getValue());
    String saturation = formatDouble(bgSaturationSlider.getValue());
    String contrast = formatDouble(bgContrastSlider.getValue());
    persisted.setProperty(prefix + "tintColor", tintColor);
    persisted.setProperty(prefix + "tintStrength", tintStrength);
    persisted.setProperty(prefix + "saturation", saturation);
    persisted.setProperty(prefix + "contrast", contrast);
    persisted.setProperty(prefix + "exposure", formatDouble(bgExposureSlider.getValue()));
    persisted.setProperty(prefix + "tintBlend", canonicalBlendMode(bgTintBlendModeBox.getValue(), "Normal"));
    persisted.setProperty(prefix + "overlayColor", colorToHex(bgOverlayColorPicker.getValue()));
    persisted.setProperty(prefix + "overlayOpacity", formatDouble(bgOverlayOpacitySlider.getValue()));
    persisted.setProperty(prefix + "overlayBlend", canonicalBlendMode(bgOverlayBlendModeBox.getValue(), "Overlay"));
    // Backward compatibility for older keys used by previous tool versions.
    persisted.setProperty(prefix + "color", tintColor);
    persisted.setProperty(prefix + "strength", tintStrength);
    savePersistentState();
  }

  private void applyBackgroundTintIfPresent(String backgroundTag) {
    String tag = normalize(backgroundTag);
    if (tag.isBlank()) {
      resetBackgroundFxControls();
      return;
    }
    String prefix = "bg." + encodeKey(tag) + ".";
    boolean hasBackgroundFx = persisted.containsKey(prefix + "tintColor")
        || persisted.containsKey(prefix + "color")
        || persisted.containsKey(prefix + "tintBlend")
        || persisted.containsKey(prefix + "overlayColor")
        || persisted.containsKey(prefix + "overlayOpacity");
    if (!hasBackgroundFx) {
      resetBackgroundFxControls();
      return;
    }
    applyingState = true;
    try {
      bgTintColorPicker.setValue(parseColor(
          persisted.getProperty(prefix + "tintColor", persisted.getProperty(prefix + "color")),
          bgTintColorPicker.getValue()));
      bgTintStrengthSlider.setValue(parseDouble(
          persisted.getProperty(prefix + "tintStrength", persisted.getProperty(prefix + "strength")),
          bgTintStrengthSlider.getValue()));
      bgSaturationSlider.setValue(parseDouble(persisted.getProperty(prefix + "saturation"), bgSaturationSlider.getValue()));
      bgContrastSlider.setValue(parseDouble(persisted.getProperty(prefix + "contrast"), bgContrastSlider.getValue()));
      bgExposureSlider.setValue(parseDouble(persisted.getProperty(prefix + "exposure"), 0));
      bgTintBlendModeBox.getSelectionModel().select(
          canonicalBlendMode(persisted.getProperty(prefix + "tintBlend"), bgTintBlendModeBox.getValue()));
      bgOverlayColorPicker.setValue(parseColor(persisted.getProperty(prefix + "overlayColor"), bgOverlayColorPicker.getValue()));
      bgOverlayOpacitySlider.setValue(parseDouble(persisted.getProperty(prefix + "overlayOpacity"), bgOverlayOpacitySlider.getValue()));
      bgOverlayBlendModeBox.getSelectionModel().select(
          canonicalBlendMode(persisted.getProperty(prefix + "overlayBlend"), bgOverlayBlendModeBox.getValue()));
    } finally {
      applyingState = false;
    }
    onBackgroundFxChanged(true);
  }

  private void persistGlobalState() {
    captureSidebarDividerPosition();
    persisted.setProperty("global.inspectorTab", Integer.toString(inspector.getSelectionModel().getSelectedIndex()));
    persisted.setProperty("global.filter", normalize(filterField.getText()));
    persisted.setProperty("global.assetScope", normalize(assetScopeBox.getValue()));
    persisted.setProperty("global.characterTag", selectedCharacterTag());
    persisted.setProperty("global.backgroundTag", selectedBackgroundTag());
    persisted.setProperty("global.exportFormat", normalize(exportFormatBox.getValue()));
    persisted.setProperty("global.exportCustomName", Boolean.toString(customExportNameCheck.isSelected()));
    persisted.setProperty("global.exportName", normalize(exportNameField.getText()));
    File configuredExportDirectory = configuredExportDirectory();
    if (configuredExportDirectory == null) {
      persisted.remove("global.exportDir");
    } else {
      persisted.setProperty("global.exportDir", configuredExportDirectory.getAbsolutePath());
    }
    persisted.setProperty("global.tintColor", colorToHex(tintColorPicker.getValue()));
    persisted.setProperty("global.tintStrength", formatDouble(tintStrengthSlider.getValue()));
    persisted.setProperty("global.saturation", formatDouble(saturationSlider.getValue()));
    persisted.setProperty("global.contrast", formatDouble(contrastSlider.getValue()));
    persisted.setProperty("global.bgTintColor", colorToHex(bgTintColorPicker.getValue()));
    persisted.setProperty("global.bgTintStrength", formatDouble(bgTintStrengthSlider.getValue()));
    persisted.setProperty("global.bgSaturation", formatDouble(bgSaturationSlider.getValue()));
    persisted.setProperty("global.bgContrast", formatDouble(bgContrastSlider.getValue()));
    persisted.setProperty("global.bgExposure", formatDouble(bgExposureSlider.getValue()));
    persisted.setProperty("global.bgTintBlend", canonicalBlendMode(bgTintBlendModeBox.getValue(), "Normal"));
    persisted.setProperty("global.bgOverlayColor", colorToHex(bgOverlayColorPicker.getValue()));
    persisted.setProperty("global.bgOverlayOpacity", formatDouble(bgOverlayOpacitySlider.getValue()));
    persisted.setProperty("global.bgOverlayBlend", canonicalBlendMode(bgOverlayBlendModeBox.getValue(), "Overlay"));
    persisted.setProperty("global.showLightVectors", Boolean.toString(showLightVectorsCheck.isSelected()));
    persisted.setProperty("global.zoom", formatDouble(zoom));
    persisted.setProperty("global.offsetX", formatDouble(offsetX));
    persisted.setProperty("global.offsetY", formatDouble(offsetY));
    persisted.setProperty("global.sidebarCollapsed", Boolean.toString(sidebarCollapsed));
    persisted.setProperty("global.sidebarDivider", formatDouble(sidebarDividerPosition));
    boolean hideControls = controlsPane != null && !controlsPane.isExpanded();
    persisted.setProperty("global.hideControls", Boolean.toString(hideControls));
    boolean hideExport = exportPane != null && !exportPane.isExpanded();
    persisted.setProperty("global.hideExport", Boolean.toString(hideExport));
    boolean hideBackgroundFx = backgroundPane != null && !backgroundPane.isExpanded();
    persisted.setProperty("global.hideBackgroundFx", Boolean.toString(hideBackgroundFx));
    boolean hideZones = zonesPane != null && !zonesPane.isExpanded();
    persisted.setProperty("global.hideZones", Boolean.toString(hideZones));
    writeSceneLightsToPrefix("global.light.");
    writeSceneOccludersToPrefix("global.occluder.");
    writeMaterialToPrefix("global.material.");
    savePersistentState();
  }

  private void loadPersistentState() {
    flushPendingStateSave();
    persisted.clear();
    Path file = statePath();
    if (file == null || !Files.exists(file)) return;
    try (InputStream in = Files.newInputStream(file)) {
      persisted.load(in);
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
  }

  private void savePersistentState() {
    stateSavePending = true;
    stateSaveDebounce.playFromStart();
  }

  private void flushPendingStateSave() {
    if (!stateSavePending) return;
    stateSaveDebounce.stop();
    stateSavePending = false;
    savePersistentStateNow();
  }

  private void savePersistentStateNow() {
    Path file = statePath();
    if (file == null) return;
    try {
      Path parent = file.getParent();
      if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
      try (OutputStream out = Files.newOutputStream(file)) {
        persisted.store(out, "JVN Scene Lighting Studio State");
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
  }

  private Path statePath() {
    if (projectRoot == null || !projectRoot.isDirectory()) return null;
    return projectRoot.toPath().resolve(STATE_FILE);
  }

  private void clearPrefix(String prefix) {
    List<String> keys = new ArrayList<>(persisted.stringPropertyNames());
    for (String key : keys) {
      if (key.startsWith(prefix)) persisted.remove(key);
    }
  }

  private boolean isImageFile(Path path) {
    String n = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return n.endsWith(".png")
        || n.endsWith(".jpg")
        || n.endsWith(".jpeg")
        || n.endsWith(".webp")
        || n.endsWith(".gif");
  }

  private static boolean isIgnoredPath(String relative) {
    String n = normalize(relative);
    return n.startsWith(".git/")
        || n.startsWith(".gradle/")
        || n.startsWith("build/")
        || n.startsWith("out/");
  }

  private static boolean isVnsFile(Path path) {
    String n = path == null || path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
    return n.endsWith(".vns");
  }

  private static boolean isLikelyBackgroundTag(String tag) {
    String n = normalize(tag);
    return n.contains("/background")
        || n.contains("/backgrounds/")
        || n.contains("/bg/")
        || n.contains("/scene/")
        || n.contains("/scenes/")
        || n.contains("bg_")
        || n.contains("background_")
        || n.contains("field")
        || n.contains("mainmenu");
  }

  static boolean matchesTagSearch(String tag, String queryRaw) {
    String normalizedTag = normalize(tag).toLowerCase(Locale.ROOT);
    if (normalizedTag.isBlank()) return false;
    String query = normalize(queryRaw).toLowerCase(Locale.ROOT);
    if (query.isBlank()) return true;

    boolean presetTag = normalizedTag.startsWith(PRESET_TAG_PREFIX);
    boolean backgroundTag = !presetTag && isLikelyBackgroundTag(normalizedTag);

    String[] terms = query.split("\\s+");
    for (String term : terms) {
      String t = normalize(term).toLowerCase(Locale.ROOT);
      if (t.isBlank()) continue;
      if (!matchesSearchTerm(normalizedTag, t, presetTag, backgroundTag)) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchesSearchTerm(String normalizedTag, String term, boolean presetTag, boolean backgroundTag) {
    int sep = term.indexOf(':');
    if (sep <= 0) {
      return normalizedTag.contains(term);
    }
    String key = term.substring(0, sep);
    String value = term.substring(sep + 1);
    boolean valueMatches = value.isBlank() || normalizedTag.contains(value);

    switch (key) {
      case "preset":
      case "charpreset":
        return presetTag && valueMatches;
      case "char":
      case "character":
        return (presetTag || !backgroundTag) && valueMatches;
      case "asset":
      case "image":
      case "file":
        return !presetTag && valueMatches;
      case "bg":
      case "background":
        return backgroundTag && valueMatches;
      default:
        return normalizedTag.contains(term);
    }
  }

  static String pickDefaultCharacterTag(List<String> tags) {
    if (tags == null || tags.isEmpty()) return "";
    for (String tag : tags) {
      if (!isLikelyBackgroundTag(tag)) return normalize(tag);
    }
    return normalize(tags.get(0));
  }

  static String pickDefaultBackgroundTag(List<String> tags) {
    if (tags == null || tags.isEmpty()) return "";
    for (String tag : tags) {
      if (isLikelyBackgroundTag(tag)) return normalize(tag);
    }
    return "";
  }

  private String selectedCharacterTag() {
    return selectedTag(characterTagBox);
  }

  private String selectedBackgroundTag() {
    return selectedTag(backgroundTagBox);
  }

  private void updateExportControls() {
    exportNameField.setDisable(!customExportNameCheck.isSelected());
    File directory = resolveExportDirectory();
    exportDirectoryField.setText(directory == null ? "(choose folder)" : describePathRelativeToProject(directory));
    String format = currentExportFormat();
    String selectedKind = exportKindLabel(format);
    if (DEFAULT_EXPORT_PROFILE.equals(format)) {
      exportSelectionLabel.setText(selectedKind + " · " + exportKindDescription(format) + "\nPrimary action: copy profile snippet to the clipboard.");
    } else {
      String quickTarget = describeQuickExportPath(fileNameForExportFormat(format));
      exportSelectionLabel.setText(selectedKind + " · " + exportKindDescription(format) + "\nQuick target: " + quickTarget);
    }
    exportInfoLabel.setText("PNG  " + describeQuickExportPath(buildTintPngFileName())
        + "\nSetup  " + describeQuickExportPath(buildTintSetupFileName())
        + "\nStage  " + describeQuickExportPath(buildStagePresetFileName()));
    if (exportPrimaryButton != null) {
      exportPrimaryButton.setText(primaryActionLabel(format));
      exportPrimaryButton.setGraphic(exportQuickGraphic(format));
      exportPrimaryButton.setTooltip(new Tooltip(primaryActionTooltip(format, selectedKind)));
    }
    if (exportPrimaryAsButton != null) {
      exportPrimaryAsButton.setText(secondaryActionLabel(format, selectedKind));
      exportPrimaryAsButton.setGraphic(exportAsGraphic(format));
      exportPrimaryAsButton.setTooltip(new Tooltip(secondaryActionTooltip(format, selectedKind)));
    }
  }

  private String currentExportFormat() {
    String format = normalize(exportFormatBox.getValue());
    if (DEFAULT_EXPORT_SETUP.equalsIgnoreCase(format)) return DEFAULT_EXPORT_SETUP;
    if (DEFAULT_EXPORT_STAGE_PRESET.equalsIgnoreCase(format)) return DEFAULT_EXPORT_STAGE_PRESET;
    return DEFAULT_EXPORT_PROFILE;
  }

  private static String exportKindLabel(String format) {
    if (DEFAULT_EXPORT_SETUP.equals(format)) return "Setup";
    if (DEFAULT_EXPORT_STAGE_PRESET.equals(format)) return "Stage Preset";
    return "Lighting Profile";
  }

  private static String exportKindDescription(String format) {
    if (DEFAULT_EXPORT_SETUP.equals(format)) return "editor .tintsetup";
    if (DEFAULT_EXPORT_STAGE_PRESET.equals(format)) return "runtime-ready .stagepreset";
    return "clipboard-ready profile snippet";
  }

  private static String primaryActionLabel(String format) {
    if (DEFAULT_EXPORT_SETUP.equals(format)) return "Export Setup";
    if (DEFAULT_EXPORT_STAGE_PRESET.equals(format)) return "Export Stage";
    return "Copy Profile";
  }

  private static String secondaryActionLabel(String format, String selectedKind) {
    if (DEFAULT_EXPORT_PROFILE.equals(format)) return "Export PNG As";
    return "Export " + selectedKind + " As";
  }

  private static String primaryActionTooltip(String format, String selectedKind) {
    if (DEFAULT_EXPORT_PROFILE.equals(format)) return "Copy the lighting profile snippet to the clipboard";
    return "Quick export " + selectedKind + " to the configured folder";
  }

  private static String secondaryActionTooltip(String format, String selectedKind) {
    if (DEFAULT_EXPORT_PROFILE.equals(format)) return "Choose a destination for the graded PNG";
    return "Choose a destination for " + selectedKind;
  }

  private String fileNameForExportFormat(String format) {
    if (DEFAULT_EXPORT_SETUP.equals(format)) return buildTintSetupFileName();
    if (DEFAULT_EXPORT_STAGE_PRESET.equals(format)) return buildStagePresetFileName();
    return buildTintPngFileName();
  }

  private String describeQuickExportPath(String fileName) {
    File directory = resolveExportDirectory();
    String relative = fileName == null ? "" : fileName;
    if (directory == null) {
      return "(choose folder)/" + relative;
    }
    String base = describePathRelativeToProject(directory);
    if ("Project root".equals(base)) return relative;
    return base + "/" + relative;
  }

  private javafx.scene.layout.Region exportQuickGraphic(String format) {
    if (DEFAULT_EXPORT_SETUP.equals(format)) return SidebarToolIcon.save("#9ed67a");
    if (DEFAULT_EXPORT_STAGE_PRESET.equals(format)) return SidebarToolIcon.save("#d2c6ff");
    return SidebarToolIcon.copy("#9ad19c");
  }

  private javafx.scene.layout.Region exportAsGraphic(String format) {
    if (DEFAULT_EXPORT_SETUP.equals(format)) return SidebarToolIcon.download("#9ed67a");
    if (DEFAULT_EXPORT_STAGE_PRESET.equals(format)) return SidebarToolIcon.download("#d2c6ff");
    return SidebarToolIcon.save("#8ab4f8");
  }

  private File configuredExportDirectory() {
    String raw = normalize(persisted.getProperty("global.exportDir", ""));
    return raw.isBlank() ? null : new File(raw);
  }

  private File resolveExportDirectory() {
    File configured = configuredExportDirectory();
    if (configured != null) return configured;
    return projectRoot != null && projectRoot.isDirectory() ? projectRoot : null;
  }

  private File resolveExistingExportDirectory() {
    File directory = resolveExportDirectory();
    while (directory != null && (!directory.exists() || !directory.isDirectory())) {
      directory = directory.getParentFile();
    }
    if (directory != null) return directory;
    return projectRoot != null && projectRoot.isDirectory() ? projectRoot : null;
  }

  private void chooseExportDirectory() {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Choose Scene Lighting Studio Export Folder");
    File initial = resolveExistingExportDirectory();
    if (initial != null && initial.isDirectory()) chooser.setInitialDirectory(initial);
    File selected = chooser.showDialog(getScene() == null ? null : getScene().getWindow());
    if (selected == null) return;
    setConfiguredExportDirectory(selected);
    status("Export folder: " + describePathRelativeToProject(selected));
  }

  private void revealExportDirectory() {
    File directory = resolveExportDirectory();
    if (directory == null) {
      status("Choose an export folder first.");
      return;
    }
    try {
      Files.createDirectories(directory.toPath());
    } catch (Exception ex) {
      status("Failed to prepare export folder: " + ex.getMessage());
      return;
    }
    if (AssetPickerSupport.revealFile(directory)) {
      status("Revealed export folder: " + describePathRelativeToProject(directory));
    } else {
      status("Could not reveal export folder.");
    }
  }

  private void setConfiguredExportDirectory(File directory) {
    if (directory == null) {
      persisted.remove("global.exportDir");
    } else {
      persisted.setProperty("global.exportDir", directory.getAbsolutePath());
    }
    updateExportControls();
    savePersistentState();
  }

  private File resolveQuickExportFile(String fileName) {
    File directory = resolveExportDirectory();
    if (directory == null || fileName == null || fileName.isBlank()) return null;
    return new File(directory, fileName);
  }

  private String currentExportBaseName() {
    String defaultName = buildDefaultTintExportStem(selectedCharacterTag());
    if (customExportNameCheck.isSelected()) {
      String custom = sanitizeExportStem(exportNameField.getText(), "");
      if (!custom.isBlank()) return custom;
    }
    return defaultName;
  }

  private String buildTintPngFileName() {
    return buildExportFileName(currentExportBaseName(), "png");
  }

  private String buildTintSetupFileName() {
    return buildExportFileName(currentExportBaseName(), "tintsetup");
  }

  private String buildStagePresetFileName() {
    return buildExportFileName(currentExportBaseName(), "stagepreset");
  }

  private String currentStagePresetId() {
    return buildStagePresetId(currentExportBaseName());
  }

  static String buildStagePresetId(String baseName) {
    String normalized = normalize(baseName);
    String stem = normalized.startsWith(PRESET_TAG_PREFIX)
        ? sanitizeExportStem(normalized.substring(PRESET_TAG_PREFIX.length()), "stage_preset")
        : sanitizeExportStem(stripExtension(baseName), "stage_preset");
    if (stem.endsWith("_tint")) {
      stem = stem.substring(0, stem.length() - "_tint".length());
    }
    if (stem.startsWith("preset_") && stem.length() > "preset_".length()) {
      stem = stem.substring("preset_".length());
    }
    return stem.isBlank() ? "stage_preset" : stem;
  }

  private String buildStagePresetUsagePath(String fileName) {
    File directory = resolveExportDirectory();
    if (directory == null || fileName == null || fileName.isBlank()) return fileName == null ? "" : fileName;
    String path = describePathRelativeToProject(new File(directory, fileName));
    return "Project root".equals(path) ? fileName : path;
  }

  static String buildDefaultTintExportStem(String tag) {
    String normalized = tag == null ? "" : tag.trim().replace('\\', '/');
    if (normalized.isBlank()) return "image_tint";
    if (normalized.startsWith(PRESET_TAG_PREFIX)) {
      String presetName = normalized.substring(PRESET_TAG_PREFIX.length());
      String stem = sanitizeExportStem(presetName, "preset_tint");
      return stem.endsWith("_tint") ? stem : stem + "_tint";
    }
    String[] segments = normalized.split("/");
    String fileStem = sanitizeExportStem(stripExtension(segments.length == 0 ? normalized : segments[segments.length - 1]), "image");
    String parentStem = segments.length >= 2 ? sanitizeExportStem(segments[segments.length - 2], "") : "";
    String stem;
    if (parentStem.isBlank() || fileStem.contains(parentStem)) {
      stem = fileStem;
    } else {
      stem = parentStem + "_" + fileStem;
    }
    return stem.endsWith("_tint") ? stem : stem + "_tint";
  }

  static String buildExportFileName(String baseName, String extension) {
    String ext = sanitizeExportStem(extension, "dat");
    String stem = sanitizeExportStem(stripExtension(baseName), "export");
    return stem + "." + ext;
  }

  private static String sanitizeExportStem(String raw, String fallback) {
    String value = raw == null ? "" : raw.trim().replace('\\', '/');
    if (value.isBlank()) return fallback == null ? "" : fallback;
    String sanitized = value
        .replaceAll("\\.[A-Za-z0-9]+$", "")
        .replaceAll("[^A-Za-z0-9]+", "_")
        .replaceAll("_+", "_")
        .replaceAll("^_+", "")
        .replaceAll("_+$", "")
        .toLowerCase(Locale.ROOT);
    if (sanitized.isBlank()) return fallback == null ? "" : fallback;
    return sanitized;
  }

  private static String stripExtension(String raw) {
    if (raw == null) return "";
    String value = raw.trim();
    int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
    if (slash >= 0 && slash < value.length() - 1) {
      value = value.substring(slash + 1);
    }
    int dot = value.lastIndexOf('.');
    if (dot > 0) {
      value = value.substring(0, dot);
    }
    return value;
  }

  private String describePathRelativeToProject(File file) {
    if (file == null) return "Project root";
    try {
      if (projectRoot != null && projectRoot.isDirectory()) {
        Path root = projectRoot.toPath().toAbsolutePath().normalize();
        Path target = file.toPath().toAbsolutePath().normalize();
        if (target.equals(root)) return "Project root";
        if (target.startsWith(root)) {
          String relative = root.relativize(target).toString().replace('\\', '/');
          return relative.isBlank() ? "Project root" : relative;
        }
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return file.getAbsolutePath();
  }

  private static String selectedTag(ComboBox<String> box) {
    if (box == null) return "";
    String selected = normalize(box.getValue());
    if (!selected.isBlank()) return selected;
    if (!box.isEditable()) return "";
    return normalize(box.getEditor() == null ? "" : box.getEditor().getText());
  }

  private static String shortTag(String tag) {
    String value = normalize(tag);
    if (value.isBlank()) return "(none)";
    if (value.length() <= 48) return value;
    return "..." + value.substring(value.length() - 45);
  }

  private void configureAssetTagBox(ComboBox<String> box) {
    if (box == null) return;
    box.setEditable(false);
    box.setVisibleRowCount(14);
    box.setButtonCell(createAssetTagCell(true));
    box.setCellFactory(list -> createAssetTagCell(false));
    AssetTagSearchPopup popup = assetTagSearchPopups.computeIfAbsent(box, AssetTagSearchPopup::new);
    box.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
      if (disposed || box.isDisabled() || event.getButton() != MouseButton.PRIMARY) return;
      event.consume();
      box.requestFocus();
      // Defer until after the click cycle finishes so the popup is not
      // immediately dismissed by the matching mouse-release event.
      Platform.runLater(() -> {
        if (!disposed && !box.isDisabled()) {
          toggleAssetTagPopup(box);
        }
      });
    });
    box.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
      if (disposed || box.isDisabled() || event.getButton() != MouseButton.PRIMARY) return;
      event.consume();
    });
    box.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
      if (disposed || box.isDisabled() || event.getButton() != MouseButton.PRIMARY) return;
      event.consume();
    });
    box.setOnShowing(event -> {
      event.consume();
      popup.hide();
    });
    box.setOnHidden(event -> popup.hide());
    box.showingProperty().addListener((obs, wasShowing, showing) -> {
      if (!showing || disposed) return;
      Platform.runLater(() -> {
        if (box.isShowing()) {
          box.hide();
        }
      });
    });
    box.setOnKeyPressed(event -> {
      if (disposed || box.isDisabled()) return;
      if (event.getCode() == KeyCode.DOWN
          || event.getCode() == KeyCode.UP
          || event.getCode() == KeyCode.SPACE
          || event.getCode() == KeyCode.ENTER
          || event.getCode() == KeyCode.F4) {
        event.consume();
        openAssetTagPopup(box);
      } else if (event.getCode() == KeyCode.ESCAPE) {
        popup.hide();
      }
    });
  }

  private ListCell<String> createAssetTagCell(boolean compact) {
    return new ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null || item.isBlank()) {
          setText(null);
          setTooltip(null);
          return;
        }
        setText(describeAssetTag(item, compact));
        setTooltip(new Tooltip(item));
      }
    };
  }

  static String describeAssetTag(String rawTag, boolean compact) {
    String tag = normalize(rawTag);
    if (tag.isBlank()) return "";
    if (tag.startsWith(PRESET_TAG_PREFIX)) {
      String remainder = normalize(tag.substring(PRESET_TAG_PREFIX.length()));
      return remainder.isBlank() ? "Preset" : "Preset · " + remainder;
    }
    if (!compact) return tag;
    String[] parts = tag.split("/");
    if (parts.length <= 4) return tag;
    return ".../" + String.join("/", java.util.Arrays.copyOfRange(parts, parts.length - 4, parts.length));
  }

  static List<String> filterAssetDropdownItems(List<String> items, String query) {
    if (items == null || items.isEmpty()) {
      return List.of();
    }
    String normalizedQuery = normalize(query);
    if (normalizedQuery.isBlank()) {
      return new ArrayList<>(items);
    }
    List<String> filtered = new ArrayList<>();
    for (String item : items) {
      if (matchesTagSearch(item, normalizedQuery)
          || describeAssetTag(item, false).toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
        filtered.add(item);
      }
    }
    return filtered;
  }

  private void setComboTagValue(ComboBox<String> box, String rawValue) {
    if (box == null) return;
    String value = normalize(rawValue);
    if (value.isBlank()) {
      box.getSelectionModel().clearSelection();
      box.setValue(null);
      if (box.isEditable() && box.getEditor() != null) {
        box.getEditor().clear();
      }
      return;
    }
    if (box.getItems().contains(value)) {
      box.getSelectionModel().select(value);
    } else {
      box.setValue(value);
    }
    if (box.isEditable() && box.getEditor() != null) {
      box.getEditor().setText(value);
    }
  }

  private void toggleAssetTagPopup(ComboBox<String> box) {
    AssetTagSearchPopup popup = assetTagSearchPopups.get(box);
    if (popup == null) return;
    if (popup.isShowing()) {
      popup.hide();
      return;
    }
    openAssetTagPopup(box);
  }

  private void openAssetTagPopup(ComboBox<String> box) {
    if (box == null || disposed || box.isDisabled()) return;
    if (box.isShowing()) {
      box.hide();
    }
    if ((imageByTag.isEmpty() && presetByTag.isEmpty()) && (scanTask == null || !scanTask.isRunning())) {
      refreshCatalog();
      return;
    }
    refreshTagLists();
    AssetTagSearchPopup popup = assetTagSearchPopups.computeIfAbsent(box, AssetTagSearchPopup::new);
    popup.show();
  }

  private void refreshVisibleAssetTagPopup(ComboBox<String> box) {
    AssetTagSearchPopup popup = assetTagSearchPopups.get(box);
    if (popup != null && popup.isShowing()) {
      popup.refreshFromItems();
    }
  }

  private final class AssetTagSearchPopup {
    private final ComboBox<String> owner;
    private final Popup popup = new Popup();
    private final VBox root = new VBox(6);
    private final TextField searchField = new TextField();
    private final ListView<String> listView = new ListView<>();
    private List<String> sourceItems = List.of();

    private AssetTagSearchPopup(ComboBox<String> owner) {
      this.owner = owner;
      popup.setAutoHide(true);
      popup.setHideOnEscape(true);
      popup.setAutoFix(true);

      root.setPadding(new Insets(6));
      root.setMinWidth(220);
      root.setStyle(
          "-fx-background-color: #161616;"
              + "-fx-border-color: #3a3a3a;"
              + "-fx-border-radius: 4;"
              + "-fx-background-radius: 4;");
      searchField.setPromptText("Search assets...");
      listView.setCellFactory(list -> createAssetTagCell(false));
      listView.setPlaceholder(new Label("No matching assets"));
      VBox.setVgrow(listView, Priority.ALWAYS);
      root.getChildren().setAll(searchField, listView);
      popup.getContent().setAll(root);

      searchField.textProperty().addListener((o, ov, nv) -> applyFilter());
      searchField.setOnKeyPressed(event -> {
        if (event.getCode() == KeyCode.DOWN) {
          if (!listView.getItems().isEmpty()) {
            listView.requestFocus();
            listView.getSelectionModel().select(Math.max(0, listView.getSelectionModel().getSelectedIndex()));
            listView.scrollTo(listView.getSelectionModel().getSelectedIndex());
          }
          event.consume();
        } else if (event.getCode() == KeyCode.ENTER) {
          commitSelection();
          event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
          hide();
          owner.requestFocus();
          event.consume();
        }
      });

      listView.setOnMouseClicked(event -> {
        if (event.getButton() != MouseButton.PRIMARY) return;
        commitSelection();
        event.consume();
      });
      listView.setOnKeyPressed(event -> {
        if (event.getCode() == KeyCode.ENTER) {
          commitSelection();
          event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
          hide();
          owner.requestFocus();
          event.consume();
        }
      });
    }

    private boolean isShowing() {
      return popup.isShowing();
    }

    private void show() {
      sourceItems = new ArrayList<>(owner.getItems());
      searchField.clear();
      applyFilter();
      selectCurrentValue();

      Bounds bounds = owner.localToScreen(owner.getBoundsInLocal());
      if (bounds == null) return;
      double width = Math.max(260.0, bounds.getWidth());
      root.setPrefWidth(width);
      root.setMaxWidth(width);
      searchField.setPrefWidth(width - 12);
      listView.setPrefWidth(width - 12);
      listView.setPrefHeight(Math.min(360.0, Math.max(160.0, listView.getItems().size() * 28.0 + 8.0)));

      popup.show(owner, bounds.getMinX(), bounds.getMaxY());
      Platform.runLater(searchField::requestFocus);
    }

    private void hide() {
      popup.hide();
    }

    private void refreshFromItems() {
      sourceItems = new ArrayList<>(owner.getItems());
      applyFilter();
      selectCurrentValue();
    }

    private void applyFilter() {
      List<String> filtered = filterAssetDropdownItems(sourceItems, searchField.getText());
      listView.getItems().setAll(filtered);
      if (!filtered.isEmpty() && listView.getSelectionModel().isEmpty()) {
        listView.getSelectionModel().select(0);
      }
    }

    private void selectCurrentValue() {
      String current = normalize(owner.getValue());
      if (current.isBlank()) {
        if (!listView.getItems().isEmpty()) {
          listView.getSelectionModel().select(0);
          listView.scrollTo(0);
        }
        return;
      }
      int index = listView.getItems().indexOf(current);
      if (index >= 0) {
        listView.getSelectionModel().select(index);
        listView.scrollTo(index);
      } else if (!listView.getItems().isEmpty()) {
        listView.getSelectionModel().select(0);
        listView.scrollTo(0);
      }
    }

    private void commitSelection() {
      String selected = normalize(listView.getSelectionModel().getSelectedItem());
      if (selected.isBlank()) return;
      setComboTagValue(owner, selected);
      hide();
      owner.requestFocus();
    }
  }

  private List<String> buildCharacterTagList(String scopeRaw) {
    return filterCharacterTagsForScope(
        new ArrayList<>(imageByTag.keySet()),
        new ArrayList<>(presetByTag.keySet()),
        scopeRaw);
  }

  static List<String> filterCharacterTagsForScope(List<String> imageTags, List<String> presetTags, String scopeRaw) {
    String scope = normalize(scopeRaw);
    List<String> tags = new ArrayList<>();
    boolean includePresets = false;
    boolean includeAllImages = false;
    boolean includeCharacterImages = false;

    if (ASSET_SCOPE_CHARACTER_PRESETS_ONLY.equals(scope)) {
      includePresets = true;
    } else if (ASSET_SCOPE_CHARACTER_ASSETS_ONLY.equals(scope)) {
      includeCharacterImages = true;
    } else if (ASSET_SCOPE_ALL_ASSETS_AND_PRESETS.equals(scope)) {
      includePresets = true;
      includeAllImages = true;
    } else {
      includePresets = true;
      includeCharacterImages = true;
    }

    if (includeAllImages && imageTags != null) {
      tags.addAll(imageTags);
    } else if (includeCharacterImages && imageTags != null) {
      for (String tag : imageTags) {
        if (!isLikelyBackgroundTag(tag)) {
          tags.add(tag);
        }
      }
    }
    if (includePresets && presetTags != null) {
      tags.addAll(presetTags);
    }

    tags.sort(String.CASE_INSENSITIVE_ORDER);
    return tags;
  }

  private boolean isKnownCharacterTag(String tag) {
    String n = normalize(tag);
    return imageByTag.containsKey(n) || presetByTag.containsKey(n);
  }

  private Map<String, PresetTagEntry> scanScriptCharpresets(Path root, Task<?> scanTask) {
    Map<String, PresetTagEntry> out = new LinkedHashMap<>();
    if (root == null) return out;
    Map<String, Map<String, String>> layersByCharacter = new LinkedHashMap<>();
    List<PresetDecl> declarations = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(root, 10)) {
      List<String> scriptFiles = stream
          .filter(Files::isRegularFile)
          .filter(ImageTintToolView::isVnsFile)
          .map(path -> root.relativize(path).toString().replace('\\', '/'))
          .filter(relative -> !isIgnoredPath(relative))
          .sorted(Comparator.naturalOrder())
          .toList();
      for (String relative : scriptFiles) {
        if (scanTask != null && scanTask.isCancelled()) return out;
        parsePresetDeclarations(root.resolve(relative), layersByCharacter, declarations);
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return out;
    }

    for (PresetDecl declaration : declarations) {
      if (scanTask != null && scanTask.isCancelled()) return out;
      List<String> layers = resolvePresetLayerTags(layersByCharacter, declaration.characterId(), declaration.spec());
      if (layers.isEmpty()) continue;
      String tag = buildPresetTag(declaration.characterId(), declaration.expressionId());
      out.put(tag, new PresetTagEntry(tag, layers));
    }
    return out;
  }

  private void parsePresetDeclarations(Path scriptFile,
                                       Map<String, Map<String, String>> layersByCharacter,
                                       List<PresetDecl> declarations) {
    if (scriptFile == null) return;
    List<String> lines;
    try {
      lines = Files.readAllLines(scriptFile, StandardCharsets.UTF_8);
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return;
    }
    for (String rawLine : lines) {
      if (rawLine == null) continue;
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;

      Matcher layerMatcher = CHARLAYER_PATTERN.matcher(rawLine);
      if (layerMatcher.find()) {
        String characterId = normalize(layerMatcher.group(1));
        String layerId = normalize(layerMatcher.group(2));
        String path = normalize(unquote(layerMatcher.group(3)));
        if (characterId.isBlank() || layerId.isBlank() || path.isBlank()) continue;
        layersByCharacter.computeIfAbsent(characterId, k -> new LinkedHashMap<>()).put(layerId, path);
        continue;
      }

      Matcher presetMatcher = CHARPRESET_PATTERN.matcher(rawLine);
      if (presetMatcher.find()) {
        String characterId = normalize(presetMatcher.group(1));
        String expressionId = normalize(presetMatcher.group(2));
        String spec = normalize(presetMatcher.group(3));
        if (characterId.isBlank() || expressionId.isBlank() || spec.isBlank()) continue;
        declarations.add(new PresetDecl(characterId, expressionId, spec));
      }
    }
  }

  static String buildPresetTag(String characterId, String expressionId) {
    String character = normalize(characterId);
    String expression = normalize(expressionId);
    if (character.isBlank() || expression.isBlank()) return "";
    return PRESET_TAG_PREFIX + character + "/" + expression;
  }

  static List<String> resolvePresetLayerTags(Map<String, Map<String, String>> layersByCharacter,
                                             String defaultCharacterId,
                                             String spec) {
    List<String> resolved = new ArrayList<>();
    if (layersByCharacter == null) return resolved;
    String rawSpec = normalize(spec);
    if (rawSpec.isBlank()) return resolved;

    String[] tokens = rawSpec.split("\\|");
    for (String token : tokens) {
      String part = normalize(unquote(token));
      if (part.isBlank()) continue;
      if (part.startsWith("$")) {
        LayerRef layerRef = parseLayerRef(part.substring(1), defaultCharacterId);
        if (layerRef.characterId().isBlank() || layerRef.layerId().isBlank()) continue;
        Map<String, String> characterLayers = layersByCharacter.get(layerRef.characterId());
        if (characterLayers == null) continue;
        String path = normalize(characterLayers.get(layerRef.layerId()));
        if (path.isBlank()) continue;
        resolved.add(path);
      } else {
        resolved.add(part);
      }
    }
    return resolved;
  }

  private static LayerRef parseLayerRef(String rawRef, String defaultCharacterId) {
    String ref = normalize(rawRef);
    if (ref.isBlank()) return new LayerRef(normalize(defaultCharacterId), "");
    String characterId = normalize(defaultCharacterId);
    String layerId = ref;

    int colon = ref.indexOf(':');
    int dot = ref.indexOf('.');
    int sep = colon >= 0 ? colon : dot;
    if (colon >= 0 && dot >= 0) sep = Math.min(colon, dot);
    if (sep > 0 && sep < ref.length() - 1) {
      characterId = normalize(ref.substring(0, sep));
      layerId = normalize(ref.substring(sep + 1));
    }
    return new LayerRef(characterId, layerId);
  }

  private static String unquote(String raw) {
    String value = normalize(raw);
    if (value.length() < 2) return value;
    char first = value.charAt(0);
    char last = value.charAt(value.length() - 1);
    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
      return value.substring(1, value.length() - 1).trim();
    }
    return value;
  }

  private static Slider slider(double min, double max, double value) {
    Slider slider = new Slider(min, max, value);
    slider.setShowTickMarks(false);
    slider.setShowTickLabels(false);
    return slider;
  }

  private static HBox sliderRow(String label, Slider slider) {
    Label l = new Label(label);
    l.setMinWidth(60);
    TextField valueField = new TextField(String.format(Locale.ROOT, "%.0f", slider.getValue()));
    valueField.setPrefWidth(58);
    slider.valueProperty().addListener((o, ov, nv) -> valueField.setText(String.format(Locale.ROOT, "%.0f", nv.doubleValue())));
    valueField.setOnAction(e -> {
      try {
        slider.setValue(Double.parseDouble(valueField.getText().trim()));
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
    });
    HBox row = new HBox(8, l, slider, valueField);
    row.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(slider, Priority.ALWAYS);
    return row;
  }

  private HBox tintPickerRow(String label, ColorPicker picker, Region swatch, Color fallback) {
    Label l = new Label(label);
    l.setMinWidth(90);

    swatch.setMinSize(26, 26);
    swatch.setPrefSize(26, 26);
    swatch.setMaxSize(26, 26);
    swatch.setMouseTransparent(true);

    picker.setMinSize(26, 26);
    picker.setPrefSize(26, 26);
    picker.setMaxSize(26, 26);
    picker.setStyle(
        "-fx-color-label-visible: false;"
            + " -fx-background-radius: 999;"
            + " -fx-border-radius: 999;");
    picker.setOpacity(0.001);

    updateColorSwatch(swatch, picker.getValue(), fallback);

    StackPane pickerHost = new StackPane(swatch, picker);
    pickerHost.setMinSize(26, 26);
    pickerHost.setPrefSize(26, 26);
    pickerHost.setMaxSize(26, 26);

    HBox row = new HBox(8, l, pickerHost);
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
  }

  private void updateTintColorSwatch(Color color) {
    updateColorSwatch(tintColorSwatch, color, Color.WHITE);
  }

  private void updateBackgroundSwatches() {
    updateColorSwatch(bgTintColorSwatch, bgTintColorPicker.getValue(), Color.WHITE);
    updateColorSwatch(bgOverlayColorSwatch, bgOverlayColorPicker.getValue(), Color.BLACK);
  }

  private static void updateColorSwatch(Region swatch, Color color, Color fallback) {
    if (swatch == null) return;
    Color base = fallback == null ? Color.WHITE : fallback;
    Color c = color == null ? base : color;
    int r = (int) Math.round(clamp(c.getRed(), 0.0, 1.0) * 255.0);
    int g = (int) Math.round(clamp(c.getGreen(), 0.0, 1.0) * 255.0);
    int b = (int) Math.round(clamp(c.getBlue(), 0.0, 1.0) * 255.0);
    String hex = String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b);
    swatch.setStyle(
        "-fx-background-color: " + hex + ";"
            + " -fx-background-radius: 999;"
            + " -fx-border-color: rgba(255,255,255,0.40);"
            + " -fx-border-radius: 999;"
            + " -fx-border-width: 1;");
  }

  private static HBox comboRow(String label, ComboBox<String> box) {
    Label l = new Label(label);
    l.setMinWidth(60);
    HBox row = new HBox(8, l, box);
    row.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(box, Priority.ALWAYS);
    return row;
  }

  private Button iconButton(javafx.scene.layout.Region icon, String tooltip, Runnable action) {
    Button button = new Button();
    button.setGraphic(icon);
    button.setMinSize(28, 28);
    button.setPrefSize(28, 28);
    button.setMaxSize(28, 28);
    button.setFocusTraversable(false);
    if (tooltip != null && !tooltip.isBlank()) button.setTooltip(new Tooltip(tooltip));
    button.setOnAction(e -> {
      if (action != null) action.run();
    });
    return button;
  }

  private Button presetChip(String text, Runnable action) {
    Button button = new Button(text);
    button.setFocusTraversable(false);
    button.setMinHeight(24);
    button.setStyle("-fx-background-color: #262626; -fx-background-radius: 999; -fx-padding: 3 10 3 10;");
    button.setOnAction(e -> {
      if (action != null) action.run();
    });
    return button;
  }

  private Button assetTagPickerButton(javafx.scene.layout.Region icon, String tooltip, ComboBox<String> box) {
    Button button = iconButton(icon, tooltip, () -> openAssetTagPopup(box));
    button.setAccessibleText(tooltip);
    return button;
  }

  private static String colorToHex(Color color) {
    Color c = color == null ? Color.WHITE : color;
    int r = (int) Math.round(clamp(c.getRed(), 0.0, 1.0) * 255.0);
    int g = (int) Math.round(clamp(c.getGreen(), 0.0, 1.0) * 255.0);
    int b = (int) Math.round(clamp(c.getBlue(), 0.0, 1.0) * 255.0);
    return String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b);
  }

  private static Color parseColor(String raw, Color fallback) {
    String value = normalize(raw);
    if (value.isBlank()) return fallback == null ? Color.WHITE : fallback;
    try {
      return Color.web(value);
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return fallback == null ? Color.WHITE : fallback;
    }
  }

  private static String encodeKey(String raw) {
    String n = normalize(raw);
    if (n.isBlank()) return "_";
    return Base64.getUrlEncoder().withoutPadding().encodeToString(n.getBytes(StandardCharsets.UTF_8));
  }

  private static String normalize(String raw) {
    if (raw == null) return "";
    return raw.trim().replace('\\', '/');
  }

  private static String canonicalBlendMode(String raw, String fallback) {
    String mode = normalize(raw);
    if (!mode.isBlank()) {
      for (String candidate : BLEND_MODES) {
        if (candidate.equalsIgnoreCase(mode)) return candidate;
      }
    }
    String fb = normalize(fallback);
    if (!fb.isBlank()) {
      for (String candidate : BLEND_MODES) {
        if (candidate.equalsIgnoreCase(fb)) return candidate;
      }
    }
    return BLEND_MODES[0];
  }

  private static boolean parseBoolean(String raw, boolean fallback) {
    String n = normalize(raw).toLowerCase(Locale.ROOT);
    if (n.isBlank()) return fallback;
    if ("true".equals(n) || "1".equals(n) || "yes".equals(n) || "on".equals(n)) return true;
    if ("false".equals(n) || "0".equals(n) || "no".equals(n) || "off".equals(n)) return false;
    return fallback;
  }

  private static double parseDouble(String raw, double fallback) {
    String n = normalize(raw);
    if (n.isBlank()) return fallback;
    try {
      double parsed = Double.parseDouble(n);
      return Double.isFinite(parsed) ? parsed : fallback;
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return fallback;
    }
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  static double defaultLightSourceX(double targetX) {
    return clamp(targetX - 0.12, 0.0, 1.0);
  }

  static double defaultLightSourceY(double targetY) {
    return clamp(targetY - 0.18, 0.0, 1.0);
  }

  private static String formatDouble(double value) {
    return String.format(Locale.ROOT, "%.4f", value);
  }

  private static String formatNormalized(double value) {
    return String.format(Locale.ROOT, "%.3f", value);
  }

  private void copy(String text) {
    ClipboardContent content = new ClipboardContent();
    content.putString(text == null ? "" : text);
    Clipboard.getSystemClipboard().setContent(content);
  }

  private void status(String message) {
    statusLabel.setText(message == null ? "" : message);
  }

  private record PresetDecl(String characterId, String expressionId, String spec) {}

  private record PresetTagEntry(String tag, List<String> layerTags) {}

  private record LayerRef(String characterId, String layerId) {}

  private enum SceneLightShape {
    RADIAL("Radial", "radial"),
    POLYGON("Polygon", "polygon"),
    CONE("Cone", "cone"),
    STRIP("Strip", "strip"),
    WINDOW("Window", "window"),
    BOUNCE("Bounce", "bounce"),
    AREA("Area", "area"),
    DIRECTIONAL("Directional", "directional"),
    AMBIENT("Ambient", "ambient");

    private final String label;
    private final String persisted;

    SceneLightShape(String label, String persisted) {
      this.label = label;
      this.persisted = persisted;
    }

    String persistedValue() {
      return persisted;
    }

    static SceneLightShape fromPersisted(String raw) {
      if (raw == null || raw.isBlank()) return RADIAL;
      for (SceneLightShape shape : values()) {
        if (shape.persisted.equalsIgnoreCase(raw.trim())
            || shape.label.equalsIgnoreCase(raw.trim())) {
          return shape;
        }
      }
      return RADIAL;
    }

    static List<String> labels() {
      return Stream.of(values()).map(SceneLightShape::toString).toList();
    }

    @Override public String toString() {
      return label;
    }
  }

  private enum SubjectSurfaceClass {
    DEFAULT("Default", "default"),
    SKIN("Skin", "skin"),
    HAIR("Hair", "hair"),
    FABRIC("Fabric", "fabric"),
    METAL("Metal", "metal"),
    GLASS("Glass", "glass");

    private final String label;
    private final String persisted;

    SubjectSurfaceClass(String label, String persisted) {
      this.label = label;
      this.persisted = persisted;
    }

    String persistedValue() {
      return persisted;
    }

    static SubjectSurfaceClass fromPersisted(String raw) {
      if (raw == null || raw.isBlank()) return DEFAULT;
      for (SubjectSurfaceClass value : values()) {
        if (value.persisted.equalsIgnoreCase(raw.trim())
            || value.label.equalsIgnoreCase(raw.trim())) {
          return value;
        }
      }
      return DEFAULT;
    }

    static List<String> labels() {
      return Stream.of(values()).map(SubjectSurfaceClass::toString).toList();
    }

    @Override public String toString() {
      return label;
    }
  }

  private enum SceneLightLayer {
    BACKGROUND("Behind Character", "background"),
    CHARACTER("On Character", "character"),
    FOREGROUND("In Front", "foreground");

    private final String label;
    private final String persisted;

    SceneLightLayer(String label, String persisted) {
      this.label = label;
      this.persisted = persisted;
    }

    String persistedValue() {
      return persisted;
    }

    static SceneLightLayer fromPersisted(String raw) {
      if (raw == null || raw.isBlank()) return CHARACTER;
      for (SceneLightLayer layer : values()) {
        if (layer.persisted.equalsIgnoreCase(raw.trim())
            || layer.label.equalsIgnoreCase(raw.trim())) {
          return layer;
        }
      }
      return CHARACTER;
    }

    static List<String> labels() {
      return Stream.of(values()).map(SceneLightLayer::toString).toList();
    }

    @Override public String toString() {
      return label;
    }
  }

  private record TintCatalogScanResult(
      Map<String, File> images,
      Map<String, PresetTagEntry> presets,
      boolean cancelled
  ) {
    static TintCatalogScanResult cancelledResult() {
      return new TintCatalogScanResult(Map.of(), Map.of(), true);
    }
  }

  // ── TintZone model ──

  private static final class TintZone {
    String name;
    double boundsX, boundsY, boundsW, boundsH; // fractional 0..1 bounding rect
    Color color;
    double strength;   // 0..100
    double saturation; // -100..100
    double contrast;   // -100..100
    double feather;    // 0..100
    double rotation;   // degrees, -180..180 (only for rectangles)
    String blendMode;  // one of BLEND_MODES
    boolean overlayVisible; // preview overlay visibility only; tint effect remains active
    SubjectSurfaceClass surfaceClass;
    double depthBias; // -100..100 authoring guide
    double responseScale; // 10..250%
    List<double[]> polygon; // fractional image coords (x,y pairs); null or <3 = rectangle

    TintZone(String name, double bx, double by, double bw, double bh) {
      this.name = name;
      this.boundsX = bx;
      this.boundsY = by;
      this.boundsW = bw;
      this.boundsH = bh;
      this.color = Color.web("#ff8844");
      this.strength = 50;
      this.saturation = 0;
      this.contrast = 0;
      this.feather = 15;
      this.rotation = 0;
      this.blendMode = "Normal";
      this.overlayVisible = true;
      this.surfaceClass = SubjectSurfaceClass.DEFAULT;
      this.depthBias = 0.0;
      this.responseScale = 100.0;
      this.polygon = null;
    }

    boolean isPolygon() {
      return polygon != null && polygon.size() >= 3;
    }

    @Override public String toString() {
      String shape = isPolygon()
          ? " poly(" + polygon.size() + ")"
          : String.format(Locale.ROOT, " [%.0f%%,%.0f%% %.0f%%x%.0f%%]",
              boundsX * 100, boundsY * 100, boundsW * 100, boundsH * 100);
      String surface = surfaceClass == null ? SubjectSurfaceClass.DEFAULT.persistedValue() : surfaceClass.persistedValue();
      return (name == null || name.isBlank() ? "Zone" : name) + shape + " · " + surface;
    }
  }

  private static final class SceneLight {
    String name;
    double sceneX;
    double sceneY;
    double sourceX;
    double sourceY;
    Color color;
    double intensity;
    double radius;
    double softness;
    double silhouette;
    SceneLightShape shape;
    SceneLightLayer layer;
    LightBlend blend = LightBlend.PHYSICAL;
    LightFalloff falloff = LightFalloff.CLASSIC;
    boolean castsShadows = true;
    ShadowQuality shadowQuality = ShadowQuality.SOFT;
    double beamAngle;
    double areaAspect;
    VnStagePreset.GroundProjection groundProjection;
    boolean muted;
    boolean locked;
    boolean solo;
    String group;
    List<double[]> polygon;

    SceneLight(String name, double sceneX, double sceneY) {
      this.name = name;
      this.sceneX = sceneX;
      this.sceneY = sceneY;
      this.sourceX = defaultLightSourceX(sceneX);
      this.sourceY = defaultLightSourceY(sceneY);
      this.color = Color.web("#ffd7a8");
      this.intensity = 42.0;
      this.radius = DEFAULT_LIGHT_RADIUS;
      this.softness = 55.0;
      this.silhouette = DEFAULT_LIGHT_SILHOUETTE;
      this.shape = SceneLightShape.RADIAL;
      this.layer = SceneLightLayer.CHARACTER;
      this.muted = false;
      this.locked = false;
      this.solo = false;
      this.group = "";
      this.polygon = null;
    }

    boolean isPolygon() {
      return shape == SceneLightShape.POLYGON && polygon != null && polygon.size() >= 3;
    }

    @Override public String toString() {
      return String.format(
          Locale.ROOT,
          "%s · %s · %s [%.0f%%, %.0f%%]",
          (name == null || name.isBlank()) ? "Light" : name,
          layer,
          isPolygon() ? "poly" : (shape == null ? SceneLightShape.RADIAL : shape).persistedValue(),
          sceneX * 100.0,
          sceneY * 100.0
      );
    }
  }

  private static final class SceneOccluder {
    String name;
    double opacity;
    double softness;
    boolean enabled;
    List<double[]> polygon;
    OccluderShape shape = OccluderShape.POLYGON;
    VnStagePreset.Bounds bounds;
    double contactY = -1;
    double heightScale = 1;
    double footprintDepth;
    boolean roundFootprint;
    VnStagePreset.SurfaceTone surfaceTone;
    List<Double> floorContacts = List.of();
    double shadowFade;
    VnStagePreset.ShadowFraming shadowFraming;

    SceneOccluder(String name) {
      this.name = name;
      this.opacity = 85.0;
      this.softness = 12.0;
      this.enabled = true;
      this.polygon = new ArrayList<>();
    }

    boolean isPolygon() {
      return polygon != null && polygon.size() >= 3;
    }

    @Override public String toString() {
      return (name == null || name.isBlank() ? "Occluder" : name)
          + " · "
          + Math.round(opacity)
          + "% block"
          + (enabled ? "" : " · off");
    }
  }

  // ── Zone UI builder ──

  private void buildZoneSection() {
    zoneBlendModeBox.getItems().setAll(BLEND_MODES);
    zoneBlendModeBox.getSelectionModel().select("Normal");
    zoneSurfaceClassBox.getItems().setAll(SubjectSurfaceClass.labels());
    zoneSurfaceClassBox.getSelectionModel().select(SubjectSurfaceClass.DEFAULT.toString());
    zoneNameField.setPromptText("Zone name");

    zoneColorSwatch.setMinSize(18, 18);
    zoneColorSwatch.setMaxSize(18, 18);
    zoneColorSwatch.setPrefSize(18, 18);
    updateZoneColorSwatch(zoneColorPicker.getValue());

    zoneListView.setPrefHeight(100);
    zoneListView.setMaxHeight(140);
    zoneListView.setStyle("-fx-background-color: #1f1f1f; -fx-border-color: #333333;");
    zoneListView.setCellFactory(lv -> new ListCell<TintZone>() {
      private final Label textLabel = new Label();
      private final Region spacer = new Region();
      private final Button visibilityButton = new Button();
      private final HBox row = new HBox(6, textLabel, spacer, visibilityButton);

      {
        textLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        visibilityButton.setFocusTraversable(false);
        visibilityButton.setMinSize(22, 22);
        visibilityButton.setPrefSize(22, 22);
        visibilityButton.setMaxSize(22, 22);
        visibilityButton.setStyle("-fx-background-color: transparent; -fx-padding: 2;");
        visibilityButton.setOnAction(e -> {
          TintZone zone = getItem();
          if (zone == null) return;
          zone.overlayVisible = !zone.overlayVisible;
          updateVisibilityButton(zone.overlayVisible);
          zoneListView.refresh();
          persistZones();
          redrawPreview();
          status((zone.overlayVisible ? "Showing" : "Hiding") + " bounds for " + (zone.name == null ? "zone" : zone.name) + ".");
          e.consume();
        });
      }

      private void updateVisibilityButton(boolean visible) {
        visibilityButton.setGraphic(visible ? SidebarToolIcon.visibility("#d0d0d0") : SidebarToolIcon.visibilityOff("#6b7280"));
        visibilityButton.setTooltip(new Tooltip(visible ? "Hide bounds overlay" : "Show bounds overlay"));
      }

      @Override protected void updateItem(TintZone item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
          setStyle("");
        } else {
          setText(null);
          textLabel.setText(item.toString());
          updateVisibilityButton(item.overlayVisible);
          setGraphic(row);
          boolean sel = getIndex() == selectedZoneIndex;
          textLabel.setStyle(sel ? "-fx-text-fill: #e2e8f0;" : "-fx-text-fill: #c8d0dc;");
          setStyle(sel
              ? "-fx-background-color: #2b2b2b; -fx-text-fill: #e2e8f0;"
              : "-fx-text-fill: #c8d0dc;");
        }
      }
    });
    zoneListView.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      int idx = nv == null ? -1 : nv.intValue();
      selectZone(idx);
    });

    zoneDrawToggleButton = iconButton(SidebarToolIcon.rectSelect("#d0d0d0"), "Drag on preview to draw a rectangular zone", this::toggleZoneDrawMode);
    polyDrawToggleButton = iconButton(SidebarToolIcon.polygon("#c49cf8"), "Click on preview to place polygon vertices; click near first point or double-click to close", this::togglePolyDrawMode);
    freehandDrawToggleButton = iconButton(SidebarToolIcon.freehand("#7dd3fc"), "Hold and draw to create a smoothed freehand zone", this::toggleFreehandDrawMode);

    Button addZoneButton = iconButton(SidebarToolIcon.plus("#9ed67a"), "Add zone at center", this::addDefaultZone);
    Button removeZoneButton = iconButton(SidebarToolIcon.minus("#f38ba8"), "Remove selected zone", this::removeSelectedZone);
    Button clearZonesButton = iconButton(SidebarToolIcon.clearX("#f5b971"), "Remove all zones", this::clearAllZones);
    Button moveUpButton = iconButton(SidebarToolIcon.arrowUp("#d0d0d0"), "Move zone up in order", this::moveZoneUp);
    Button moveDownButton = iconButton(SidebarToolIcon.arrowDown("#d0d0d0"), "Move zone down in order", this::moveZoneDown);

    HBox zoneActions = new HBox(6, zoneDrawToggleButton, polyDrawToggleButton, freehandDrawToggleButton, addZoneButton, removeZoneButton, clearZonesButton, moveUpButton, moveDownButton);
    zoneActions.setAlignment(Pos.CENTER_LEFT);

    // Per-zone controls
    zoneColorPicker.valueProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      updateZoneColorSwatch(nv);
      applyZoneControlsToSelected();
    });
    zoneStrengthSlider.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneSaturationSlider.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneContrastSlider.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneFeatherSlider.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneRotationSlider.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneBlendModeBox.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneSurfaceClassBox.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneDepthBiasSlider.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneResponseScaleSlider.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneNameField.textProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneNameToSelected(); });

    HBox zoneNameRow = new HBox(8, new Label("Name"), zoneNameField);
    zoneNameRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(zoneNameField, Priority.ALWAYS);

    HBox zoneColorRow = new HBox(8, new Label("Color"), zoneColorSwatch, zoneColorPicker);
    zoneColorRow.setAlignment(Pos.CENTER_LEFT);

    HBox zoneBlendRow = new HBox(8, new Label("Blend"), zoneBlendModeBox);
    zoneBlendRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(zoneBlendModeBox, Priority.ALWAYS);

    HBox zoneSurfaceRow = new HBox(8, new Label("Surface"), zoneSurfaceClassBox);
    zoneSurfaceRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(zoneSurfaceClassBox, Priority.ALWAYS);

    zoneControlsSection.getChildren().setAll(
        zoneNameRow,
        zoneColorRow,
        sliderRow("Strength", zoneStrengthSlider),
        sliderRow("Saturation", zoneSaturationSlider),
        sliderRow("Contrast", zoneContrastSlider),
        sliderRow("Feather", zoneFeatherSlider),
        sliderRow("Rotation", zoneRotationSlider),
        zoneBlendRow,
        zoneSurfaceRow,
        sliderRow("Depth", zoneDepthBiasSlider),
        sliderRow("Response %", zoneResponseScaleSlider));
    zoneControlsSection.setDisable(true);

    VBox zonesContent = new VBox(6, zoneActions, zoneListView, zoneControlsSection);
    zonesPane = new TitledPane("Local Grades", zonesContent);
    zonesPane.setExpanded(false);
    zonesPane.setAnimated(false);
    zonesPane.setCollapsible(true);
    zonesPane.expandedProperty().addListener((o, ov, expanded) -> {
      if (!applyingState) persistGlobalState();
    });
  }

  private void buildOcclusionSection() {
    occluderNameField.setPromptText("Occluder name");
    occluderEnabledCheck.setFocusTraversable(false);
    occluderEnabledCheck.setSelected(true);
    occluderShapeBox.getItems().setAll(OccluderShape.values());
    occluderShapeBox.valueProperty().addListener((obs, old, shape) -> changeOccluderShape(shape));
    occluderBoundsField.setPromptText("X, Y, width, height (%)");
    occluderBoundsField.setTooltip(new Tooltip("Enter left, top, width and height as percentages of the stage"));
    occluderBoundsField.setOnAction(event -> commitOccluderBounds());
    occluderFloorContactsField.setPromptText("Optional floor Y for each polygon vertex (%)");
    occluderFloorContactsField.setTooltip(new Tooltip("Assign floor depth to each vertex of a rigid caster. Leave blank for a flat silhouette."));
    occluderFloorContactsField.setOnAction(event -> commitFloorContacts());
    occluderFramingField.setPromptText("Shadow offset X, Y, width %, height %, angle°");
    occluderFramingField.setTooltip(new Tooltip("Optional floor shadow adjustment. Offsets and scale are percentages. Leave blank for the original projection."));
    occluderFramingField.setOnAction(event -> commitShadowFraming());

    occluderListView.setPrefHeight(84);
    occluderListView.setMaxHeight(124);
    occluderListView.setStyle("-fx-background-color: #1f1f1f; -fx-border-color: #333333;");
    occluderListView.setCellFactory(lv -> new ListCell<SceneOccluder>() {
      @Override protected void updateItem(SceneOccluder item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
          setStyle("");
          return;
        }
        Label label = new Label(item.shape + "  ·  " + item);
        label.setStyle(getIndex() == selectedOccluderIndex
            ? "-fx-text-fill: #e2e8f0;"
            : "-fx-text-fill: #c8d0dc;");
        setText(null);
        setGraphic(label);
        setStyle(getIndex() == selectedOccluderIndex
            ? "-fx-background-color: #2b2b2b;"
            : "");
      }
    });
    occluderListView.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      selectOccluder(nv == null ? -1 : nv.intValue());
    });

    occluderPolyDrawToggleButton = iconButton(SidebarToolIcon.polygon("#a6b7ff"), "Draw a polygonal shadow blocker on the preview", this::toggleOccluderPolyDrawMode);
    occluderFreehandDrawToggleButton = iconButton(SidebarToolIcon.freehand("#b6d8ff"), "Sketch a freehand shadow blocker on the preview", this::toggleOccluderFreehandDrawMode);
    Button addOccluderButton = iconButton(SidebarToolIcon.plus("#9ed67a"), "Add a default occluder", this::addDefaultOccluder);
    Button removeOccluderButton = iconButton(SidebarToolIcon.minus("#f38ba8"), "Remove selected occluder", this::removeSelectedOccluder);
    Button clearOccludersButton = iconButton(SidebarToolIcon.clearX("#f5b971"), "Remove all occluders", this::clearAllOccluders);
    Button box = actionButton("Draw Box", SidebarToolIcon.rectSelect("#9ed67a"), "Drag a rectangular shadow caster", () -> startOccluderBounds(OccluderShape.BOX));
    Button ellipse = actionButton("Draw Ellipse", SidebarToolIcon.rectSelect("#9ed67a"), "Drag bounds for a round shadow caster", () -> startOccluderBounds(OccluderShape.ELLIPSE));
    Button mask = actionButton("Import Mask", SidebarToolIcon.folder("#a6b7ff"),
        "Trace the alpha or white areas of a full-stage PNG into shadow casters", this::importCasterMask);
    FlowPane actions = new FlowPane(6, 4, mask, box, ellipse, occluderPolyDrawToggleButton,
        occluderFreehandDrawToggleButton, addOccluderButton, removeOccluderButton, clearOccludersButton);
    actions.setAlignment(Pos.CENTER_LEFT);

    occluderNameField.textProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyOccluderControlsToSelected();
    });
    occluderOpacitySlider.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyOccluderControlsToSelected();
    });
    occluderSoftnessSlider.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyOccluderControlsToSelected();
    });
    for (Slider control : List.of(occluderContactSlider, occluderHeightSlider, occluderFootprintSlider, occluderFadeSlider, occluderExposureSlider, occluderContrastSlider)) {
      control.valueProperty().addListener((o, old, value) -> { if (!applyingState) applyOccluderControlsToSelected(); });
    }
    occluderContactSlider.setTooltip(new Tooltip("Floor contact measured from the top of the stage. 0 uses the bottom of the silhouette."));
    occluderRoundCheck.selectedProperty().addListener((o, old, value) -> { if (!applyingState) applyOccluderControlsToSelected(); });
    occluderEnabledCheck.selectedProperty().addListener((o, ov, nv) -> {
      if (!applyingState) applyOccluderControlsToSelected();
    });

    HBox nameRow = new HBox(8, new Label("Name"), occluderNameField);
    nameRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(occluderNameField, Priority.ALWAYS);

    occluderControlsSection.getChildren().setAll(
        nameRow, new HBox(8, new Label("Shape"), occluderShapeBox),
        new VBox(3, new Label("Bounds · X, Y, width, height (%)"), occluderBoundsField),
        sliderRow("Block %", occluderOpacitySlider),
        sliderRow("Penumbra", occluderSoftnessSlider),
        sliderRow("Floor Contact %", occluderContactSlider),
        sliderRow("Height Scale %", occluderHeightSlider),
        sliderRow("Floor Depth %", occluderFootprintSlider),
        sliderRow("Shadow Fade %", occluderFadeSlider),
        occluderRoundCheck, occluderFloorContactsField, occluderFramingField,
        sliderRow("Object Exposure ×100 EV", occluderExposureSlider),
        sliderRow("Object Contrast %", occluderContrastSlider),
        occluderEnabledCheck
    );
    occluderControlsSection.setDisable(true);

    shadowDebugBox.getItems().setAll("Casters", "Sample Rays", "Shadow Coverage", "Rays + Coverage", "Off");
    shadowDebugBox.setValue("Casters");
    shadowDebugBox.valueProperty().addListener((obs, old, selected) -> redrawPreview());
    VBox content = new VBox(6, actions, new HBox(8, new Label("Debug view"), shadowDebugBox),
        occluderListView, occluderControlsSection);
    occlusionPane = new TitledPane("Occlusion & Shadows", content);
    occlusionPane.setExpanded(false);
    occlusionPane.setAnimated(false);
    occlusionPane.setCollapsible(true);
    occlusionPane.expandedProperty().addListener((o, ov, expanded) -> {
      if (!applyingState) persistGlobalState();
    });
  }

  // ── Zone drawing ──

  private void toggleZoneDrawMode() {
    disableZoneShapeDrawModes();
    disableLightShapeDrawModes();
    zoneDrawMode = !zoneDrawMode;
    drawingZone = false;
    resetDrawButtons();
    if (zoneDrawMode) {
      zoneDrawToggleButton.setStyle("-fx-background-color: #d4a017; -fx-padding: 4;");
      zoneDrawToggleButton.setGraphic(SidebarToolIcon.rectSelect("#1a1a2e"));
      status("Rect draw mode ON — drag on preview to create a rectangular zone.");
    } else {
      status("Draw mode OFF.");
    }
    redrawPreview();
  }

  private void togglePolyDrawMode() {
    disableZoneRectDrawMode();
    disableZoneFreehandDrawMode();
    disableLightShapeDrawModes();
    polyDrawMode = !polyDrawMode;
    nailPoints.clear();
    resetDrawButtons();
    if (polyDrawMode) {
      polyDrawToggleButton.setStyle("-fx-background-color: #d4a017; -fx-padding: 4;");
      polyDrawToggleButton.setGraphic(SidebarToolIcon.polygon("#1a1a2e"));
      status("Polygon draw mode ON — click to place vertices, click first point or double-click to close.");
    } else {
      status("Draw mode OFF.");
    }
    redrawPreview();
  }

  private void toggleFreehandDrawMode() {
    disableZoneRectDrawMode();
    disableZonePolyDrawMode();
    disableLightShapeDrawModes();
    freehandDrawMode = !freehandDrawMode;
    drawingFreehand = false;
    freehandPoints.clear();
    resetDrawButtons();
    if (freehandDrawMode) {
      freehandDrawToggleButton.setStyle("-fx-background-color: #d4a017; -fx-padding: 4;");
      freehandDrawToggleButton.setGraphic(SidebarToolIcon.freehand("#1a1a2e"));
      status("Freehand draw mode ON — hold and draw a zone boundary, then release to auto-smooth.");
    } else {
      status("Draw mode OFF.");
    }
    redrawPreview();
  }

  private void toggleLightPolyDrawMode() {
    ensureSelectedLightForDrawing();
    disableZoneAllDrawModes();
    disableLightFreehandDrawMode();
    lightPolyDrawMode = !lightPolyDrawMode;
    nailPoints.clear();
    resetDrawButtons();
    if (lightPolyDrawMode) {
      lightPolyDrawToggleButton.setStyle("-fx-background-color: #d4a017; -fx-padding: 4;");
      lightPolyDrawToggleButton.setGraphic(SidebarToolIcon.polygon("#1a1a2e"));
      status("Polygon light mode ON — click to place vertices for the selected scene light.");
    } else {
      status("Draw mode OFF.");
    }
    redrawPreview();
  }

  private void toggleLightFreehandDrawMode() {
    ensureSelectedLightForDrawing();
    disableZoneAllDrawModes();
    disableLightPolyDrawMode();
    lightFreehandDrawMode = !lightFreehandDrawMode;
    drawingFreehand = false;
    freehandPoints.clear();
    resetDrawButtons();
    if (lightFreehandDrawMode) {
      lightFreehandDrawToggleButton.setStyle("-fx-background-color: #d4a017; -fx-padding: 4;");
      lightFreehandDrawToggleButton.setGraphic(SidebarToolIcon.freehand("#1a1a2e"));
      status("Freehand light mode ON — draw a scene-light boundary, then release to smooth it.");
    } else {
      status("Draw mode OFF.");
    }
    redrawPreview();
  }

  private void toggleOccluderPolyDrawMode() {
    occluderBoundsDrawMode = null;
    drawingOccluderBounds = false;
    disableZoneAllDrawModes();
    disableLightShapeDrawModes();
    disableOccluderFreehandDrawMode();
    occluderPolyDrawMode = !occluderPolyDrawMode;
    nailPoints.clear();
    resetDrawButtons();
    if (occluderPolyDrawMode) {
      occluderPolyDrawToggleButton.setStyle("-fx-background-color: #d4a017; -fx-padding: 4;");
      occluderPolyDrawToggleButton.setGraphic(SidebarToolIcon.polygon("#1a1a2e"));
      status("Occluder polygon mode ON — click to place blocker vertices.");
    } else {
      status("Draw mode OFF.");
    }
    redrawPreview();
  }

  private void toggleOccluderFreehandDrawMode() {
    occluderBoundsDrawMode = null;
    drawingOccluderBounds = false;
    disableZoneAllDrawModes();
    disableLightShapeDrawModes();
    disableOccluderPolyDrawMode();
    occluderFreehandDrawMode = !occluderFreehandDrawMode;
    drawingFreehand = false;
    freehandPoints.clear();
    resetDrawButtons();
    if (occluderFreehandDrawMode) {
      occluderFreehandDrawToggleButton.setStyle("-fx-background-color: #d4a017; -fx-padding: 4;");
      occluderFreehandDrawToggleButton.setGraphic(SidebarToolIcon.freehand("#1a1a2e"));
      status("Occluder freehand mode ON — draw a blocker and release to smooth it.");
    } else {
      status("Draw mode OFF.");
    }
    redrawPreview();
  }

  private void resetDrawButtons() {
    zoneDrawToggleButton.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 4;");
    zoneDrawToggleButton.setGraphic(SidebarToolIcon.rectSelect("#d0d0d0"));
    polyDrawToggleButton.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 4;");
    polyDrawToggleButton.setGraphic(SidebarToolIcon.polygon("#c49cf8"));
    freehandDrawToggleButton.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 4;");
    freehandDrawToggleButton.setGraphic(SidebarToolIcon.freehand("#7dd3fc"));
    if (lightPolyDrawToggleButton != null) {
      lightPolyDrawToggleButton.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 4;");
      lightPolyDrawToggleButton.setGraphic(SidebarToolIcon.polygon("#f5c46b"));
    }
    if (lightFreehandDrawToggleButton != null) {
      lightFreehandDrawToggleButton.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 4;");
      lightFreehandDrawToggleButton.setGraphic(SidebarToolIcon.freehand("#9ad4ff"));
    }
    if (occluderPolyDrawToggleButton != null) {
      occluderPolyDrawToggleButton.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 4;");
      occluderPolyDrawToggleButton.setGraphic(SidebarToolIcon.polygon("#a6b7ff"));
    }
    if (occluderFreehandDrawToggleButton != null) {
      occluderFreehandDrawToggleButton.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 4;");
      occluderFreehandDrawToggleButton.setGraphic(SidebarToolIcon.freehand("#b6d8ff"));
    }
  }

  private void disableZoneRectDrawMode() {
    zoneDrawMode = false;
    drawingZone = false;
  }

  private void disableZonePolyDrawMode() {
    polyDrawMode = false;
    nailPoints.clear();
  }

  private void disableZoneFreehandDrawMode() {
    freehandDrawMode = false;
    drawingFreehand = false;
    freehandPoints.clear();
  }

  private void disableZoneShapeDrawModes() {
    disableZonePolyDrawMode();
    disableZoneFreehandDrawMode();
  }

  private void disableZoneAllDrawModes() {
    disableZoneRectDrawMode();
    disableZoneShapeDrawModes();
  }

  private void disableLightPolyDrawMode() {
    lightPolyDrawMode = false;
    nailPoints.clear();
  }

  private void disableLightFreehandDrawMode() {
    lightFreehandDrawMode = false;
    drawingFreehand = false;
    freehandPoints.clear();
  }

  private void disableLightShapeDrawModes() {
    disableLightPolyDrawMode();
    disableLightFreehandDrawMode();
  }

  private void disableOccluderPolyDrawMode() {
    occluderPolyDrawMode = false;
    nailPoints.clear();
  }

  private void disableOccluderFreehandDrawMode() {
    occluderFreehandDrawMode = false;
    drawingFreehand = false;
    freehandPoints.clear();
  }

  private void disableOccluderShapeDrawModes() {
    occluderBoundsDrawMode = null;
    drawingOccluderBounds = false;
    disableOccluderPolyDrawMode();
    disableOccluderFreehandDrawMode();
  }

  private void appendFreehandPoint(double x, double y) {
    if (freehandPoints.isEmpty()) {
      freehandPoints.add(new double[]{x, y});
      return;
    }
    double[] last = freehandPoints.get(freehandPoints.size() - 1);
    double dx = x - last[0];
    double dy = y - last[1];
    if ((dx * dx + dy * dy) < (FREEHAND_CAPTURE_MIN_DISTANCE * FREEHAND_CAPTURE_MIN_DISTANCE)) {
      return;
    }
    freehandPoints.add(new double[]{x, y});
  }

  private boolean isNearFirstNail(double mx, double my) {
    if (nailPoints.isEmpty()) return false;
    double[] first = nailPoints.get(0);
    double dx = mx - first[0];
    double dy = my - first[1];
    return (dx * dx + dy * dy) <= (8.0 * 8.0); // 8px snap radius
  }

  private void commitPolygonZone() {
    if (nailPoints.size() < 3) {
      status("Need at least 3 points for a polygon zone.");
      nailPoints.clear();
      redrawPreview();
      return;
    }
    Image img = loadImage(selectedCharacterTag());
    if (img == null) {
      status("No character image to place zone on.");
      nailPoints.clear();
      redrawPreview();
      return;
    }
    double cw = previewCanvas.getWidth();
    double ch = previewCanvas.getHeight();
    double imgDrawW = img.getWidth() * zoom;
    double imgDrawH = img.getHeight() * zoom;
    double imgDrawX = (cw - imgDrawW) * 0.5 + offsetX;
    double imgDrawY = (ch - imgDrawH) * 0.5 + offsetY;

    // Convert canvas coords to fractional image coords and compute bounding rect.
    List<double[]> fracPoly = new ArrayList<>();
    double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
    for (double[] pt : nailPoints) {
      double fx = clamp((pt[0] - imgDrawX) / imgDrawW, 0.0, 1.0);
      double fy = clamp((pt[1] - imgDrawY) / imgDrawH, 0.0, 1.0);
      fracPoly.add(new double[]{fx, fy});
      if (fx < minX) minX = fx;
      if (fy < minY) minY = fy;
      if (fx > maxX) maxX = fx;
      if (fy > maxY) maxY = fy;
    }
    double bw = maxX - minX;
    double bh = maxY - minY;
    if (bw < 0.005 || bh < 0.005) {
      status("Polygon too small.");
      nailPoints.clear();
      redrawPreview();
      return;
    }

    TintZone zone = new TintZone("Poly " + (tintZones.size() + 1), minX, minY, bw, bh);
    zone.polygon = fracPoly;
    zone.color = zoneColorPicker.getValue() == null ? Color.web("#ff8844") : zoneColorPicker.getValue();
    zone.strength = zoneStrengthSlider.getValue();
    zone.saturation = zoneSaturationSlider.getValue();
    zone.contrast = zoneContrastSlider.getValue();
    zone.feather = zoneFeatherSlider.getValue();
    zone.blendMode = canonicalBlendMode(zoneBlendModeBox.getValue(), "Normal");
    tintZones.add(zone);
    nailPoints.clear();
    invalidateTintCache();
    refreshZoneList();
    selectZone(tintZones.size() - 1);
    persistZones();
    redrawPreview();
    status("Created polygon " + zone.name + " with " + zone.polygon.size() + " vertices.");
  }

  private void commitDrawnZone() {
    Image img = loadImage(selectedCharacterTag());
    if (img == null) {
      status("No character image to place zone on.");
      return;
    }
    double cw = previewCanvas.getWidth();
    double ch = previewCanvas.getHeight();
    double drawW = img.getWidth() * zoom;
    double drawH = img.getHeight() * zoom;
    double drawX = (cw - drawW) * 0.5 + offsetX;
    double drawY = (ch - drawH) * 0.5 + offsetY;

    // Convert canvas coords to fractional image coords.
    double rx = Math.min(zoneDrawStartX, zoneDrawEndX);
    double ry = Math.min(zoneDrawStartY, zoneDrawEndY);
    double rw = Math.abs(zoneDrawEndX - zoneDrawStartX);
    double rh = Math.abs(zoneDrawEndY - zoneDrawStartY);
    if (rw < 4 || rh < 4) {
      status("Zone too small — drag a larger rectangle.");
      return;
    }

    double fx = clamp((rx - drawX) / drawW, 0.0, 1.0);
    double fy = clamp((ry - drawY) / drawH, 0.0, 1.0);
    double fw = clamp(rw / drawW, 0.001, 1.0 - fx);
    double fh = clamp(rh / drawH, 0.001, 1.0 - fy);

    TintZone zone = new TintZone("Zone " + (tintZones.size() + 1), fx, fy, fw, fh);
    zone.color = zoneColorPicker.getValue() == null ? Color.web("#ff8844") : zoneColorPicker.getValue();
    zone.strength = zoneStrengthSlider.getValue();
    zone.saturation = zoneSaturationSlider.getValue();
    zone.contrast = zoneContrastSlider.getValue();
    zone.feather = zoneFeatherSlider.getValue();
    zone.blendMode = canonicalBlendMode(zoneBlendModeBox.getValue(), "Normal");
    tintZones.add(zone);
    invalidateTintCache();
    refreshZoneList();
    selectZone(tintZones.size() - 1);
    persistZones();
    status("Created " + zone.name + ".");
  }

  private void commitFreehandZone() {
    if (freehandPoints.size() < 3) {
      freehandPoints.clear();
      status("Freehand stroke too short.");
      return;
    }
    Image img = loadImage(selectedCharacterTag());
    if (img == null) {
      freehandPoints.clear();
      status("No character image to place zone on.");
      return;
    }
    double iw = Math.max(1.0, img.getWidth());
    double ih = Math.max(1.0, img.getHeight());
    double cw = previewCanvas.getWidth();
    double ch = previewCanvas.getHeight();
    double drawW = iw * zoom;
    double drawH = ih * zoom;
    double drawX = (cw - drawW) * 0.5 + offsetX;
    double drawY = (ch - drawH) * 0.5 + offsetY;

    // Convert canvas coords to image pixel coordinates for stroke processing.
    List<double[]> strokePx = new ArrayList<>();
    for (double[] pt : freehandPoints) {
      double px = clamp((pt[0] - drawX) / drawW, 0.0, 1.0) * iw;
      double py = clamp((pt[1] - drawY) / drawH, 0.0, 1.0) * ih;
      strokePx.add(new double[]{px, py});
    }
    List<double[]> smoothPx = smoothFreehandStroke(
        strokePx,
        FREEHAND_CLOSE_DISTANCE,
        FREEHAND_RESAMPLE_STEP,
        FREEHAND_SPLINE_STEP,
        FREEHAND_SIMPLIFY_EPSILON,
        FREEHAND_MAX_VERTICES
    );
    if (smoothPx.size() < 3) {
      freehandPoints.clear();
      status("Freehand stroke did not form a valid zone.");
      return;
    }

    List<double[]> fracPoly = new ArrayList<>();
    double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
    for (double[] pt : smoothPx) {
      double fx = clamp(pt[0] / iw, 0.0, 1.0);
      double fy = clamp(pt[1] / ih, 0.0, 1.0);
      fracPoly.add(new double[]{fx, fy});
      if (fx < minX) minX = fx;
      if (fy < minY) minY = fy;
      if (fx > maxX) maxX = fx;
      if (fy > maxY) maxY = fy;
    }
    double bw = maxX - minX;
    double bh = maxY - minY;
    double area = polygonAreaAbs(fracPoly);
    if (bw < 0.005 || bh < 0.005 || area < 0.00005) {
      freehandPoints.clear();
      status("Freehand zone too small.");
      return;
    }

    TintZone zone = new TintZone("Stroke " + (tintZones.size() + 1), minX, minY, bw, bh);
    zone.polygon = fracPoly;
    zone.color = zoneColorPicker.getValue() == null ? Color.web("#ff8844") : zoneColorPicker.getValue();
    zone.strength = zoneStrengthSlider.getValue();
    zone.saturation = zoneSaturationSlider.getValue();
    zone.contrast = zoneContrastSlider.getValue();
    zone.feather = zoneFeatherSlider.getValue();
    zone.blendMode = canonicalBlendMode(zoneBlendModeBox.getValue(), "Normal");
    tintZones.add(zone);
    freehandPoints.clear();
    invalidateTintCache();
    refreshZoneList();
    selectZone(tintZones.size() - 1);
    persistZones();
    status("Created freehand " + zone.name + " (" + zone.polygon.size() + " vertices from " + strokePx.size() + " samples).");
  }

  private void commitPolygonSceneLight() {
    if (nailPoints.size() < 3) {
      status("Need at least 3 points for a polygon light.");
      nailPoints.clear();
      redrawPreview();
      return;
    }
    SceneLight light = ensureSelectedLightForDrawing();
    if (light == null) {
      status("Could not select a scene light.");
      nailPoints.clear();
      redrawPreview();
      return;
    }
    List<double[]> fracPoly = canvasStrokeToScenePolygon(nailPoints);
    if (!isValidScenePolygon(fracPoly)) {
      nailPoints.clear();
      status("Polygon light too small.");
      redrawPreview();
      return;
    }
    applyPolygonToLight(light, fracPoly);
    nailPoints.clear();
    disableLightPolyDrawMode();
    resetDrawButtons();
    invalidateTintCache();
    refreshLightList();
    selectLight(selectedLightIndex);
    persistGlobalState();
    redrawPreview();
    status("Updated " + fallbackLightName(light) + " with a polygonal light shape.");
  }

  private void commitFreehandSceneLight() {
    if (freehandPoints.size() < 3) {
      freehandPoints.clear();
      status("Freehand light stroke too short.");
      return;
    }
    SceneLight light = ensureSelectedLightForDrawing();
    if (light == null) {
      freehandPoints.clear();
      status("Could not select a scene light.");
      return;
    }
    double cw = Math.max(1.0, previewCanvas.getWidth());
    double ch = Math.max(1.0, previewCanvas.getHeight());
    List<double[]> strokePx = new ArrayList<>();
    for (double[] pt : freehandPoints) {
      strokePx.add(new double[]{
          clamp(pt[0], 0.0, cw),
          clamp(pt[1], 0.0, ch)
      });
    }
    List<double[]> smoothPx = smoothFreehandStroke(
        strokePx,
        FREEHAND_CLOSE_DISTANCE,
        FREEHAND_RESAMPLE_STEP,
        FREEHAND_SPLINE_STEP,
        FREEHAND_SIMPLIFY_EPSILON,
        FREEHAND_MAX_VERTICES
    );
    if (smoothPx.size() < 3) {
      freehandPoints.clear();
      status("Freehand light did not form a valid polygon.");
      return;
    }
    List<double[]> fracPoly = canvasStrokeToScenePolygon(smoothPx);
    if (!isValidScenePolygon(fracPoly)) {
      freehandPoints.clear();
      status("Freehand light too small.");
      return;
    }
    applyPolygonToLight(light, fracPoly);
    freehandPoints.clear();
    disableLightFreehandDrawMode();
    resetDrawButtons();
    invalidateTintCache();
    refreshLightList();
    selectLight(selectedLightIndex);
    persistGlobalState();
    redrawPreview();
    status("Updated " + fallbackLightName(light) + " from freehand light shape.");
  }

  private List<double[]> canvasStrokeToScenePolygon(List<double[]> points) {
    double cw = Math.max(1.0, previewCanvas.getWidth());
    double ch = Math.max(1.0, previewCanvas.getHeight());
    List<double[]> out = new ArrayList<>();
    for (double[] pt : points) {
      if (pt == null || pt.length < 2) continue;
      out.add(new double[]{
          clamp(pt[0] / cw, 0.0, 1.0),
          clamp(pt[1] / ch, 0.0, 1.0)
      });
    }
    return out;
  }

  private static boolean isValidScenePolygon(List<double[]> polygon) {
    if (polygon == null || polygon.size() < 3) return false;
    double minX = Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    for (double[] pt : polygon) {
      minX = Math.min(minX, pt[0]);
      minY = Math.min(minY, pt[1]);
      maxX = Math.max(maxX, pt[0]);
      maxY = Math.max(maxY, pt[1]);
    }
    double area = polygonAreaAbs(polygon);
    return (maxX - minX) >= 0.01 && (maxY - minY) >= 0.01 && area >= 0.0002;
  }

  private void applyPolygonToLight(SceneLight light, List<double[]> polygon) {
    if (light == null || polygon == null || polygon.size() < 3) return;
    double previousTargetX = light.sceneX;
    double previousTargetY = light.sceneY;
    light.shape = SceneLightShape.POLYGON;
    light.polygon = new ArrayList<>();
    double minX = Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    double sumX = 0.0;
    double sumY = 0.0;
    for (double[] pt : polygon) {
      double[] copy = new double[]{pt[0], pt[1]};
      light.polygon.add(copy);
      sumX += copy[0];
      sumY += copy[1];
      minX = Math.min(minX, copy[0]);
      minY = Math.min(minY, copy[1]);
      maxX = Math.max(maxX, copy[0]);
      maxY = Math.max(maxY, copy[1]);
    }
    light.sceneX = clamp(sumX / polygon.size(), 0.0, 1.0);
    light.sceneY = clamp(sumY / polygon.size(), 0.0, 1.0);
    light.sourceX = clamp(light.sourceX + (light.sceneX - previousTargetX), 0.0, 1.0);
    light.sourceY = clamp(light.sourceY + (light.sceneY - previousTargetY), 0.0, 1.0);
    double polygonSpan = Math.max(maxX - minX, maxY - minY);
    light.radius = clamp(Math.max(light.radius, polygonSpan * 0.18), 0.05, 0.80);
  }

  private SceneLight ensureSelectedLightForDrawing() {
    if (selectedLightIndex >= 0 && selectedLightIndex < sceneLights.size()) {
      return sceneLights.get(selectedLightIndex);
    }
    addDefaultLight();
    if (selectedLightIndex >= 0 && selectedLightIndex < sceneLights.size()) {
      return sceneLights.get(selectedLightIndex);
    }
    return null;
  }

  private static String fallbackLightName(SceneLight light) {
    if (light == null || light.name == null || light.name.isBlank()) return "Light";
    return light.name;
  }

  static List<double[]> smoothFreehandStroke(List<double[]> rawStroke,
                                             double closeDistancePx,
                                             double resampleStepPx,
                                             double splineStepPx,
                                             double simplifyEpsilonPx,
                                             int maxVertices) {
    if (rawStroke == null || rawStroke.size() < 3) return List.of();
    List<double[]> deduped = dedupeSequentialPoints(rawStroke, 0.75);
    if (deduped.size() < 3) return List.of();

    List<double[]> ring = toClosedRing(deduped, closeDistancePx);
    if (ring.size() < 3) return List.of();

    List<double[]> resampled = resampleClosedRing(ring, Math.max(0.75, resampleStepPx));
    if (resampled.size() < 3) return List.of();

    List<double[]> spline = catmullRomClosed(resampled, Math.max(0.6, splineStepPx));
    if (spline.size() < 3) return List.of();

    double epsilon = Math.max(0.2, simplifyEpsilonPx);
    List<double[]> simplified = simplifyClosedRingRdp(spline, epsilon);
    while (simplified.size() > Math.max(3, maxVertices) && epsilon < 20.0) {
      epsilon *= 1.25;
      simplified = simplifyClosedRingRdp(spline, epsilon);
    }
    if (simplified.size() > Math.max(3, maxVertices)) {
      simplified = downsampleRing(simplified, Math.max(3, maxVertices));
    }
    return simplified;
  }

  private static List<double[]> dedupeSequentialPoints(List<double[]> points, double minDistance) {
    if (points == null || points.isEmpty()) return List.of();
    double minDistSq = minDistance * minDistance;
    List<double[]> out = new ArrayList<>();
    for (double[] src : points) {
      if (src == null || src.length < 2) continue;
      double[] p = new double[]{src[0], src[1]};
      if (out.isEmpty()) {
        out.add(p);
        continue;
      }
      double[] last = out.get(out.size() - 1);
      double dx = p[0] - last[0];
      double dy = p[1] - last[1];
      if ((dx * dx + dy * dy) >= minDistSq) {
        out.add(p);
      }
    }
    return out;
  }

  private static List<double[]> toClosedRing(List<double[]> points, double closeDistance) {
    List<double[]> ring = dedupeSequentialPoints(points, 0.0001);
    if (ring.size() < 3) return List.of();
    double[] first = ring.get(0);
    double[] last = ring.get(ring.size() - 1);
    double dx = first[0] - last[0];
    double dy = first[1] - last[1];
    double closeDistSq = closeDistance * closeDistance;
    if ((dx * dx + dy * dy) <= closeDistSq) {
      ring.set(ring.size() - 1, new double[]{first[0], first[1]});
    } else {
      ring.add(new double[]{first[0], first[1]});
    }
    return ring;
  }

  private static List<double[]> normalizeRing(List<double[]> closedPoints) {
    if (closedPoints == null || closedPoints.isEmpty()) return List.of();
    List<double[]> out = new ArrayList<>();
    for (double[] p : closedPoints) {
      if (p == null || p.length < 2) continue;
      out.add(new double[]{p[0], p[1]});
    }
    if (out.size() < 3) return List.of();
    if (out.size() > 1) {
      double[] first = out.get(0);
      double[] last = out.get(out.size() - 1);
      if (distanceSq(first, last) < 1e-6) out.remove(out.size() - 1);
    }
    if (out.size() < 3) return List.of();
    return out;
  }

  private static List<double[]> resampleClosedRing(List<double[]> closedPoints, double step) {
    List<double[]> ring = normalizeRing(closedPoints);
    int n = ring.size();
    if (n < 3) return List.of();
    if (step <= 0.0) return ring;

    double[] cumulative = new double[n + 1];
    cumulative[0] = 0.0;
    for (int i = 0; i < n; i++) {
      double[] a = ring.get(i);
      double[] b = ring.get((i + 1) % n);
      cumulative[i + 1] = cumulative[i] + Math.sqrt(distanceSq(a, b));
    }
    double totalLength = cumulative[n];
    if (totalLength <= 1e-6) return ring;

    int samples = Math.max(3, (int) Math.round(totalLength / step));
    List<double[]> out = new ArrayList<>(samples);
    int seg = 0;
    for (int i = 0; i < samples; i++) {
      double target = (i * totalLength) / samples;
      while (seg < n - 1 && target > cumulative[seg + 1]) seg++;
      double segStart = cumulative[seg];
      double segEnd = cumulative[seg + 1];
      double segLen = Math.max(segEnd - segStart, 1e-6);
      double t = clamp((target - segStart) / segLen, 0.0, 1.0);
      double[] a = ring.get(seg);
      double[] b = ring.get((seg + 1) % n);
      out.add(new double[]{
          a[0] + (b[0] - a[0]) * t,
          a[1] + (b[1] - a[1]) * t
      });
    }
    return out;
  }

  private static List<double[]> catmullRomClosed(List<double[]> closedPoints, double targetStep) {
    List<double[]> ring = normalizeRing(closedPoints);
    int n = ring.size();
    if (n < 3) return List.of();

    List<double[]> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      double[] p0 = ring.get((i - 1 + n) % n);
      double[] p1 = ring.get(i);
      double[] p2 = ring.get((i + 1) % n);
      double[] p3 = ring.get((i + 2) % n);
      double segmentLength = Math.sqrt(distanceSq(p1, p2));
      int subdivisions = Math.max(1, Math.min(16, (int) Math.ceil(segmentLength / Math.max(0.4, targetStep))));
      for (int s = 0; s < subdivisions; s++) {
        double t = s / (double) subdivisions;
        out.add(catmullRomPoint(p0, p1, p2, p3, t));
      }
    }
    return dedupeSequentialPoints(out, 0.35);
  }

  private static double[] catmullRomPoint(double[] p0, double[] p1, double[] p2, double[] p3, double t) {
    final double alpha = 0.5;
    double t0 = 0.0;
    double t1 = t0 + Math.pow(Math.sqrt(distanceSq(p0, p1)), alpha);
    double t2 = t1 + Math.pow(Math.sqrt(distanceSq(p1, p2)), alpha);
    double t3 = t2 + Math.pow(Math.sqrt(distanceSq(p2, p3)), alpha);
    if (Math.abs(t1 - t0) < 1e-6) t1 = t0 + 1e-6;
    if (Math.abs(t2 - t1) < 1e-6) t2 = t1 + 1e-6;
    if (Math.abs(t3 - t2) < 1e-6) t3 = t2 + 1e-6;

    double tt = t1 + (t2 - t1) * clamp(t, 0.0, 1.0);
    double[] a1 = lerp(p0, p1, (tt - t0) / (t1 - t0));
    double[] a2 = lerp(p1, p2, (tt - t1) / (t2 - t1));
    double[] a3 = lerp(p2, p3, (tt - t2) / (t3 - t2));
    double[] b1 = lerp(a1, a2, (tt - t0) / (t2 - t0));
    double[] b2 = lerp(a2, a3, (tt - t1) / (t3 - t1));
    return lerp(b1, b2, (tt - t1) / (t2 - t1));
  }

  private static double[] lerp(double[] a, double[] b, double t) {
    double clamped = clamp(t, 0.0, 1.0);
    return new double[]{
        a[0] + (b[0] - a[0]) * clamped,
        a[1] + (b[1] - a[1]) * clamped
    };
  }

  private static List<double[]> simplifyClosedRingRdp(List<double[]> closedPoints, double epsilon) {
    List<double[]> ring = normalizeRing(closedPoints);
    if (ring.size() < 4 || epsilon <= 0.0) return ring;

    int start = farthestFromCentroidIndex(ring);
    List<double[]> rotated = rotateRing(ring, start);
    List<double[]> open = new ArrayList<>(rotated);
    open.add(new double[]{rotated.get(0)[0], rotated.get(0)[1]});

    List<double[]> simplifiedOpen = simplifyPolylineRdp(open, epsilon);
    if (!simplifiedOpen.isEmpty()) simplifiedOpen.remove(simplifiedOpen.size() - 1);
    if (simplifiedOpen.size() < 3) return ring;
    return simplifiedOpen;
  }

  private static int farthestFromCentroidIndex(List<double[]> points) {
    double cx = 0.0;
    double cy = 0.0;
    for (double[] p : points) {
      cx += p[0];
      cy += p[1];
    }
    cx /= points.size();
    cy /= points.size();
    int bestIndex = 0;
    double bestDistSq = -1.0;
    for (int i = 0; i < points.size(); i++) {
      double[] p = points.get(i);
      double dx = p[0] - cx;
      double dy = p[1] - cy;
      double d = dx * dx + dy * dy;
      if (d > bestDistSq) {
        bestDistSq = d;
        bestIndex = i;
      }
    }
    return bestIndex;
  }

  private static List<double[]> rotateRing(List<double[]> points, int startIndex) {
    int n = points.size();
    List<double[]> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      double[] p = points.get((startIndex + i) % n);
      out.add(new double[]{p[0], p[1]});
    }
    return out;
  }

  private static List<double[]> simplifyPolylineRdp(List<double[]> points, double epsilon) {
    int n = points.size();
    if (n <= 2) return points;
    boolean[] keep = new boolean[n];
    keep[0] = true;
    keep[n - 1] = true;
    rdpMark(points, 0, n - 1, epsilon * epsilon, keep);
    List<double[]> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      if (keep[i]) {
        double[] p = points.get(i);
        out.add(new double[]{p[0], p[1]});
      }
    }
    return out;
  }

  private static void rdpMark(List<double[]> points, int start, int end, double epsilonSq, boolean[] keep) {
    if (end <= start + 1) return;
    double[] a = points.get(start);
    double[] b = points.get(end);
    int farthest = -1;
    double maxDistSq = -1.0;
    for (int i = start + 1; i < end; i++) {
      double d = pointToSegmentDistSq(points.get(i), a, b);
      if (d > maxDistSq) {
        maxDistSq = d;
        farthest = i;
      }
    }
    if (farthest >= 0 && maxDistSq > epsilonSq) {
      keep[farthest] = true;
      rdpMark(points, start, farthest, epsilonSq, keep);
      rdpMark(points, farthest, end, epsilonSq, keep);
    }
  }

  private static double pointToSegmentDistSq(double[] p, double[] a, double[] b) {
    double dx = b[0] - a[0];
    double dy = b[1] - a[1];
    double lenSq = dx * dx + dy * dy;
    if (lenSq < 1e-12) {
      double px = p[0] - a[0];
      double py = p[1] - a[1];
      return px * px + py * py;
    }
    double t = clamp(((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / lenSq, 0.0, 1.0);
    double cx = a[0] + t * dx;
    double cy = a[1] + t * dy;
    double px = p[0] - cx;
    double py = p[1] - cy;
    return px * px + py * py;
  }

  private static List<double[]> downsampleRing(List<double[]> points, int maxVertices) {
    if (points.size() <= maxVertices) return points;
    List<double[]> out = new ArrayList<>(maxVertices);
    double step = points.size() / (double) maxVertices;
    for (int i = 0; i < maxVertices; i++) {
      int idx = Math.min(points.size() - 1, (int) Math.floor(i * step));
      double[] p = points.get(idx);
      out.add(new double[]{p[0], p[1]});
    }
    return out;
  }

  private static double polygonAreaAbs(List<double[]> polygon) {
    if (polygon == null || polygon.size() < 3) return 0.0;
    double area = 0.0;
    int n = polygon.size();
    for (int i = 0; i < n; i++) {
      double[] a = polygon.get(i);
      double[] b = polygon.get((i + 1) % n);
      area += a[0] * b[1] - b[0] * a[1];
    }
    return Math.abs(area) * 0.5;
  }

  private static double distanceSq(double[] a, double[] b) {
    double dx = a[0] - b[0];
    double dy = a[1] - b[1];
    return dx * dx + dy * dy;
  }

  // ── Zone management ──

  private void addDefaultZone() {
    // If polygon nail points are active, commit them as a polygon zone.
    if (polyDrawMode && nailPoints.size() >= 3) {
      commitPolygonZone();
      return;
    }
    if (freehandDrawMode && freehandPoints.size() >= 3) {
      drawingFreehand = false;
      commitFreehandZone();
      redrawPreview();
      return;
    }
    TintZone zone = new TintZone("Zone " + (tintZones.size() + 1), 0.25, 0.25, 0.5, 0.5);
    tintZones.add(zone);
    invalidateTintCache();
    refreshZoneList();
    selectZone(tintZones.size() - 1);
    persistZones();
    redrawPreview();
    status("Added " + zone.name + " at center.");
  }

  private void removeSelectedZone() {
    if (selectedZoneIndex < 0 || selectedZoneIndex >= tintZones.size()) {
      status("Select a zone first.");
      return;
    }
    String name = tintZones.get(selectedZoneIndex).name;
    tintZones.remove(selectedZoneIndex);
    invalidateTintCache();
    selectedZoneIndex = Math.min(selectedZoneIndex, tintZones.size() - 1);
    refreshZoneList();
    if (selectedZoneIndex >= 0) selectZone(selectedZoneIndex);
    else zoneControlsSection.setDisable(true);
    persistZones();
    redrawPreview();
    status("Removed " + name + ".");
  }

  private void clearAllZones() {
    if (tintZones.isEmpty()) return;
    tintZones.clear();
    selectedZoneIndex = -1;
    invalidateTintCache();
    refreshZoneList();
    zoneControlsSection.setDisable(true);
    persistZones();
    redrawPreview();
    status("All zones cleared.");
  }

  private void moveZoneUp() {
    if (selectedZoneIndex <= 0 || selectedZoneIndex >= tintZones.size()) return;
    TintZone z = tintZones.remove(selectedZoneIndex);
    tintZones.add(selectedZoneIndex - 1, z);
    invalidateTintCache();
    selectedZoneIndex--;
    refreshZoneList();
    selectZone(selectedZoneIndex);
    persistZones();
    redrawPreview();
  }

  private void moveZoneDown() {
    if (selectedZoneIndex < 0 || selectedZoneIndex >= tintZones.size() - 1) return;
    TintZone z = tintZones.remove(selectedZoneIndex);
    tintZones.add(selectedZoneIndex + 1, z);
    invalidateTintCache();
    selectedZoneIndex++;
    refreshZoneList();
    selectZone(selectedZoneIndex);
    persistZones();
    redrawPreview();
  }

  private void selectZone(int index) {
    selectedZoneIndex = (index >= 0 && index < tintZones.size()) ? index : -1;
    if (selectedZoneIndex < 0) {
      zoneControlsSection.setDisable(true);
      refreshZoneList();
      return;
    }
    zoneControlsSection.setDisable(false);
    TintZone zone = tintZones.get(selectedZoneIndex);
    applyingState = true;
    try {
      zoneNameField.setText(zone.name == null ? "" : zone.name);
      zoneColorPicker.setValue(zone.color == null ? Color.web("#ff8844") : zone.color);
      updateZoneColorSwatch(zoneColorPicker.getValue());
      zoneStrengthSlider.setValue(zone.strength);
      zoneSaturationSlider.setValue(zone.saturation);
      zoneContrastSlider.setValue(zone.contrast);
      zoneFeatherSlider.setValue(zone.feather);
      zoneRotationSlider.setValue(zone.rotation);
      zoneBlendModeBox.getSelectionModel().select(canonicalBlendMode(zone.blendMode, "Normal"));
      zoneSurfaceClassBox.getSelectionModel().select((zone.surfaceClass == null ? SubjectSurfaceClass.DEFAULT : zone.surfaceClass).toString());
      zoneDepthBiasSlider.setValue(zone.depthBias);
      zoneResponseScaleSlider.setValue(zone.responseScale);
    } finally {
      applyingState = false;
    }
    zoneListView.getSelectionModel().select(selectedZoneIndex);
    redrawPreview();
  }

  private void applyZoneControlsToSelected() {
    if (selectedZoneIndex < 0 || selectedZoneIndex >= tintZones.size()) return;
    TintZone zone = tintZones.get(selectedZoneIndex);
    zone.color = zoneColorPicker.getValue() == null ? Color.web("#ff8844") : zoneColorPicker.getValue();
    zone.strength = zoneStrengthSlider.getValue();
    zone.saturation = zoneSaturationSlider.getValue();
    zone.contrast = zoneContrastSlider.getValue();
    zone.feather = zoneFeatherSlider.getValue();
    zone.rotation = zoneRotationSlider.getValue();
    zone.blendMode = canonicalBlendMode(zoneBlendModeBox.getValue(), "Normal");
    zone.surfaceClass = SubjectSurfaceClass.fromPersisted(zoneSurfaceClassBox.getValue());
    zone.depthBias = zoneDepthBiasSlider.getValue();
    zone.responseScale = zoneResponseScaleSlider.getValue();
    invalidateTintCache();
    persistZones();
    redrawPreview();
  }

  private void applyZoneNameToSelected() {
    if (selectedZoneIndex < 0 || selectedZoneIndex >= tintZones.size()) return;
    tintZones.get(selectedZoneIndex).name = normalize(zoneNameField.getText());
    refreshZoneList();
    persistZones();
  }

  private void refreshZoneList() {
    applyingState = true;
    try {
      zoneListView.getItems().setAll(tintZones);
      if (selectedZoneIndex >= 0 && selectedZoneIndex < tintZones.size()) {
        zoneListView.getSelectionModel().select(selectedZoneIndex);
      }
    } finally {
      applyingState = false;
    }
  }

  private void addDefaultLight() {
    SceneLight light = new SceneLight("Light " + (sceneLights.size() + 1), 0.5, 0.35);
    sceneLights.add(light);
    invalidateTintCache();
    refreshLightList();
    selectLight(sceneLights.size() - 1);
    persistGlobalState();
    redrawPreview();
    status("Added " + light.name + ".");
  }

  private void addLightPreset(String name,
                              SceneLightShape shape,
                              SceneLightLayer layer,
                              double targetX,
                              double targetY,
                              double sourceX,
                              double sourceY,
                              String colorHex,
                              double intensity,
                              double radius,
                              double softness,
                              double silhouette) {
    SceneLight light = new SceneLight(name, clamp(targetX, 0.0, 1.0), clamp(targetY, 0.0, 1.0));
    light.shape = shape == null ? SceneLightShape.RADIAL : shape;
    light.layer = layer == null ? SceneLightLayer.CHARACTER : layer;
    light.sourceX = clamp(sourceX, 0.0, 1.0);
    light.sourceY = clamp(sourceY, 0.0, 1.0);
    light.color = parseColor(colorHex, Color.web("#ffd7a8"));
    light.intensity = clamp(intensity, 0.0, 100.0);
    light.radius = clamp(radius, 0.05, 0.80);
    light.softness = clamp(softness, 0.0, 100.0);
    light.silhouette = clamp(silhouette, 0.0, 100.0);
    sceneLights.add(light);
    invalidateTintCache();
    refreshLightList();
    selectLight(sceneLights.size() - 1);
    persistGlobalState();
    redrawPreview();
    status("Added preset: " + fallbackLightName(light) + ".");
  }

  private void addKeyLightPreset() {
    addLightPreset("Key", SceneLightShape.CONE, SceneLightLayer.CHARACTER, 0.58, 0.34, 0.28, 0.11, "#FFD4A3", 64.0, 0.24, 42.0, 18.0);
  }

  private void addFillLightPreset() {
    addLightPreset("Fill", SceneLightShape.RADIAL, SceneLightLayer.CHARACTER, 0.44, 0.42, 0.25, 0.32, "#CFE2FF", 30.0, 0.26, 72.0, 10.0);
  }

  private void addRimLightPreset() {
    addLightPreset("Rim", SceneLightShape.STRIP, SceneLightLayer.FOREGROUND, 0.72, 0.39, 0.88, 0.18, "#FFD9B4", 48.0, 0.20, 36.0, 54.0);
  }

  private void addSunPatchPreset() {
    addLightPreset("Sun Patch", SceneLightShape.WINDOW, SceneLightLayer.BACKGROUND, 0.62, 0.28, 0.46, 0.02, "#FFE2A6", 52.0, 0.26, 24.0, 20.0);
  }

  private void addScreenGlowPreset() {
    addLightPreset("Screen Glow", SceneLightShape.STRIP, SceneLightLayer.FOREGROUND, 0.48, 0.56, 0.36, 0.56, "#8EC5FF", 34.0, 0.16, 78.0, 12.0);
  }

  private void addBounceLightPreset() {
    addLightPreset("Bounce", SceneLightShape.BOUNCE, SceneLightLayer.CHARACTER, 0.51, 0.73, 0.51, 0.92, "#FFD7BB", 26.0, 0.22, 84.0, 8.0);
  }

  private void duplicateSelectedLight() {
    if (selectedLightIndex < 0 || selectedLightIndex >= sceneLights.size()) {
      status("Select a scene light first.");
      return;
    }
    SceneLight source = sceneLights.get(selectedLightIndex);
    SceneLight copy = new SceneLight(fallbackLightName(source) + " Copy", source.sceneX, source.sceneY);
    copy.sourceX = clamp(source.sourceX + 0.03, 0.0, 1.0);
    copy.sourceY = clamp(source.sourceY + 0.03, 0.0, 1.0);
    copy.color = source.color;
    copy.intensity = source.intensity;
    copy.radius = source.radius;
    copy.softness = source.softness;
    copy.silhouette = source.silhouette;
    copy.shape = source.shape;
    copy.layer = source.layer;
    copy.blend = source.blend;
    copy.falloff = source.falloff;
    copy.castsShadows = source.castsShadows;
    copy.shadowQuality = source.shadowQuality;
    copy.beamAngle = source.beamAngle;
    copy.areaAspect = source.areaAspect;
    copy.groundProjection = source.groundProjection;
    copy.muted = source.muted;
    copy.locked = false;
    copy.solo = source.solo;
    copy.group = source.group;
    if (source.polygon != null && !source.polygon.isEmpty()) {
      copy.polygon = new ArrayList<>();
      for (double[] pt : source.polygon) {
        copy.polygon.add(new double[]{clamp(pt[0] + 0.03, 0.0, 1.0), clamp(pt[1] + 0.03, 0.0, 1.0)});
      }
    }
    sceneLights.add(selectedLightIndex + 1, copy);
    invalidateTintCache();
    refreshLightList();
    selectLight(selectedLightIndex + 1);
    persistGlobalState();
    redrawPreview();
    status("Duplicated " + fallbackLightName(source) + ".");
  }

  private void mirrorSelectedLight() {
    if (selectedLightIndex < 0 || selectedLightIndex >= sceneLights.size()) {
      status("Select a scene light first.");
      return;
    }
    SceneLight light = sceneLights.get(selectedLightIndex);
    light.sceneX = 1.0 - light.sceneX;
    light.sourceX = 1.0 - light.sourceX;
    if (light.polygon != null) {
      for (double[] pt : light.polygon) {
        pt[0] = 1.0 - pt[0];
      }
    }
    invalidateTintCache();
    refreshLightList();
    selectLight(selectedLightIndex);
    persistGlobalState();
    redrawPreview();
    status("Mirrored " + fallbackLightName(light) + ".");
  }

  private void normalizeRigIntensity() {
    if (sceneLights.isEmpty()) return;
    double peak = 0.0;
    for (SceneLight light : sceneLights) {
      if (light == null || light.muted) continue;
      peak = Math.max(peak, light.intensity);
    }
    if (peak <= 1e-6) {
      status("No active rig intensity to normalize.");
      return;
    }
    double scale = 100.0 / peak;
    for (SceneLight light : sceneLights) {
      if (light == null || light.muted) continue;
      light.intensity = clamp(light.intensity * scale, 0.0, 100.0);
    }
    invalidateTintCache();
    refreshLightList();
    selectLight(selectedLightIndex);
    persistGlobalState();
    redrawPreview();
    status("Normalized rig intensity to a 100% peak.");
  }

  private void removeSelectedLight() {
    if (selectedLightIndex < 0 || selectedLightIndex >= sceneLights.size()) {
      status("Select a scene light first.");
      return;
    }
    String name = sceneLights.get(selectedLightIndex).name;
    sceneLights.remove(selectedLightIndex);
    invalidateTintCache();
    selectedLightIndex = Math.min(selectedLightIndex, sceneLights.size() - 1);
    refreshLightList();
    if (selectedLightIndex >= 0) selectLight(selectedLightIndex);
    else lightControlsSection.setDisable(true);
    persistGlobalState();
    redrawPreview();
    status("Removed " + name + ".");
  }

  private void clearAllLights() {
    if (sceneLights.isEmpty()) return;
    sceneLights.clear();
    selectedLightIndex = -1;
    lightControlsSection.setDisable(true);
    refreshLightList();
    invalidateTintCache();
    persistGlobalState();
    redrawPreview();
    status("All scene lights cleared.");
  }

  private void importCasterMask() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Import Full-Stage Caster Mask");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG images", "*.png"));
    File file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
    if (file == null) return;
    Image image = new Image(file.toURI().toString());
    PixelReader reader = image.getPixelReader();
    int width = (int) image.getWidth();
    int height = (int) image.getHeight();
    if (reader == null || width < 1 || height < 1) {
      status("Could not read the caster mask.");
      return;
    }
    int[] pixels = new int[width * height];
    reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
    Task<List<List<VnStagePreset.Point>>> task = new Task<>() {
      @Override protected List<List<VnStagePreset.Point>> call() {
        return VnCasterSegmentation.trace(width, height, pixels, 128, 0.8);
      }
    };
    File sourceProject = projectRoot;
    task.setOnSucceeded(event -> {
      if (disposed || !Objects.equals(sourceProject, projectRoot)) return;
      if (task.getValue().isEmpty()) { status("The mask contains no solid silhouettes."); return; }
      int index = 1;
      for (List<VnStagePreset.Point> polygon : task.getValue()) {
        SceneOccluder caster = new SceneOccluder(file.getName().replaceFirst("(?i)\\.png$", "") + " " + index++);
        for (var point : polygon) caster.polygon.add(new double[]{point.x(), point.y()});
        sceneOccluders.add(caster);
      }
      invalidateTintCache();
      refreshOccluderList();
      selectOccluder(sceneOccluders.size() - 1);
      persistGlobalState();
      status("Traced " + task.getValue().size() + " caster silhouettes.");
    });
    task.setOnFailed(event -> status("Could not trace the caster mask: " + task.getException().getMessage()));
    Thread thread = new Thread(task, "jvn-caster-mask");
    thread.setDaemon(true);
    thread.start();
  }

  private void startOccluderBounds(OccluderShape shape) {
    disableZoneAllDrawModes();
    disableLightShapeDrawModes();
    disableOccluderShapeDrawModes();
    resetDrawButtons();
    occluderBoundsDrawMode = shape;
    status("Drag on the scene to place a " + shape.name().toLowerCase(Locale.ROOT) + " caster.");
  }

  private void commitDrawnOccluderBounds(double endX, double endY) {
    double w = previewCanvas.getWidth();
    double h = previewCanvas.getHeight();
    OccluderShape shape = occluderBoundsDrawMode;
    occluderBoundsDrawMode = null;
    if (shape == null || Math.abs(zoneDrawStartX - endX) < 3 || Math.abs(zoneDrawStartY - endY) < 3) return;
    var bounds = new VnStagePreset.Bounds(Math.min(zoneDrawStartX, endX) / w, Math.min(zoneDrawStartY, endY) / h,
        Math.abs(zoneDrawStartX - endX) / w, Math.abs(zoneDrawStartY - endY) / h);
    SceneOccluder occluder = new SceneOccluder("Caster " + (sceneOccluders.size() + 1));
    setOccluderBounds(occluder, shape, bounds);
    addOccluder(occluder);
  }

  private void commitDrawnOccluder(List<double[]> points) {
    List<double[]> polygon = canvasStrokeToScenePolygon(points);
    if (!isValidScenePolygon(polygon)) {
      status("Draw at least three distinct points to create a caster.");
      return;
    }
    SceneOccluder occluder = new SceneOccluder("Caster " + (sceneOccluders.size() + 1));
    occluder.polygon.addAll(polygon);
    disableOccluderShapeDrawModes();
    resetDrawButtons();
    addOccluder(occluder);
  }

  private static void setOccluderBounds(SceneOccluder occluder, OccluderShape shape, VnStagePreset.Bounds bounds) {
    occluder.shape = shape;
    occluder.bounds = bounds;
    occluder.polygon.clear();
    occluder.floorContacts = List.of();
    for (VnStagePreset.Point point : bounds.outline(shape)) {
      occluder.polygon.add(new double[]{point.x(), point.y()});
    }
  }

  private void changeOccluderShape(OccluderShape shape) {
    if (applyingState || shape == null || selectedOccluderIndex < 0) return;
    SceneOccluder occluder = sceneOccluders.get(selectedOccluderIndex);
    if (shape == OccluderShape.POLYGON) {
      occluder.shape = shape;
      occluder.bounds = null;
    } else {
      var bounds = occluder.bounds;
      if (bounds == null) {
        double left = 1, top = 1, right = 0, bottom = 0;
        for (double[] p : occluder.polygon) {
          left = Math.min(left, p[0]);
          top = Math.min(top, p[1]);
          right = Math.max(right, p[0]);
          bottom = Math.max(bottom, p[1]);
        }
        bounds = new VnStagePreset.Bounds(left, top, right - left, bottom - top);
      }
      setOccluderBounds(occluder, shape, bounds);
    }
    occluderEdited();
  }

  private void commitOccluderBounds() {
    if (selectedOccluderIndex < 0) return;
    SceneOccluder occluder = sceneOccluders.get(selectedOccluderIndex);
    if (occluder.shape == OccluderShape.POLYGON) return;
    try {
      double[] values = Stream.of(occluderBoundsField.getText().split(","))
          .map(String::strip).mapToDouble(Double::parseDouble).toArray();
      if (values.length != 4 || java.util.Arrays.stream(values).anyMatch(value -> !Double.isFinite(value))) {
        throw new IllegalArgumentException();
      }
      var bounds = new VnStagePreset.Bounds(values[0] / 100, values[1] / 100, values[2] / 100, values[3] / 100);
      if (bounds.width() <= 0 || bounds.height() <= 0) throw new IllegalArgumentException();
      setOccluderBounds(occluder, occluder.shape, bounds);
      occluderEdited();
    } catch (IllegalArgumentException invalid) {
      status("Enter four percentages with a positive width and height inside the scene.");
    }
  }

  private void commitShadowFraming() {
    if (selectedOccluderIndex < 0) return;
    SceneOccluder caster = sceneOccluders.get(selectedOccluderIndex);
    String raw = occluderFramingField.getText().strip();
    try {
      if (raw.isEmpty()) caster.shadowFraming = null;
      else {
        double[] v = Stream.of(raw.split(",")).map(String::strip).mapToDouble(Double::parseDouble).toArray();
        if (v.length != 5 || java.util.Arrays.stream(v).anyMatch(value -> !Double.isFinite(value))) throw new IllegalArgumentException();
        caster.shadowFraming = new VnStagePreset.ShadowFraming(v[0] / 100, v[1] / 100, v[2] / 100, v[3] / 100, v[4]);
      }
      occluderEdited();
    } catch (IllegalArgumentException invalid) {
      status("Enter shadow X and Y offsets, width, height and angle.");
    }
  }

  private static String framingString(VnStagePreset.ShadowFraming framing, double units) {
    if (framing == null) return "";
    return formatNormalized(framing.offsetX() * units) + "," + formatNormalized(framing.offsetY() * units) + ","
        + formatNormalized(framing.scaleX() * units) + "," + formatNormalized(framing.scaleY() * units) + ","
        + formatNormalized(framing.angle());
  }

  private void commitFloorContacts() {
    if (selectedOccluderIndex < 0) return;
    SceneOccluder caster = sceneOccluders.get(selectedOccluderIndex);
    String text = occluderFloorContactsField.getText().strip();
    try {
      List<Double> values = text.isEmpty() ? List.of() : Stream.of(text.split(","))
          .map(String::strip).map(value -> Double.parseDouble(value) / 100).toList();
      if (!values.isEmpty() && (values.size() != caster.polygon.size()
          || values.stream().anyMatch(value -> !Double.isFinite(value) || value < 0 || value > 1))) {
        throw new IllegalArgumentException();
      }
      caster.floorContacts = values;
      occluderEdited();
    } catch (IllegalArgumentException invalid) {
      status("Enter one floor Y percentage for each of the " + caster.polygon.size() + " vertices.");
    }
  }

  private static String floorContactsString(List<Double> contacts, double scale) {
    return contacts.stream().map(value -> formatNormalized(value * scale)).collect(java.util.stream.Collectors.joining(","));
  }

  private void occluderEdited() {
    invalidateTintCache();
    selectOccluder(selectedOccluderIndex);
    persistGlobalState();
    redrawPreview();
  }

  private static String boundsString(VnStagePreset.Bounds bounds, double units) {
    return formatNormalized(bounds.x() * units) + "," + formatNormalized(bounds.y() * units) + ","
        + formatNormalized(bounds.width() * units) + "," + formatNormalized(bounds.height() * units);
  }

  private void drawOccluderGuides(GraphicsContext g, double width, double height) {
    if (inspector.getSelectionModel().getSelectedItem() != shadowsTab) return;
    String mode = shadowDebugBox.getValue();
    if (!"Casters".equals(mode) && !"Off".equals(mode)) drawShadowDebug(g, width, height, mode);
    if ("Off".equals(mode) && !drawingOccluderBounds && !occluderPolyDrawMode && !occluderFreehandDrawMode) return;
    g.save();
    for (int i = 0; i < sceneOccluders.size(); i++) {
      SceneOccluder caster = sceneOccluders.get(i);
      if (!caster.isPolygon() || (!caster.enabled && caster.surfaceTone != null && i != selectedOccluderIndex)) continue;
      double[] xs = new double[caster.polygon.size()];
      double[] ys = new double[xs.length];
      for (int vertex = 0; vertex < xs.length; vertex++) {
        xs[vertex] = caster.polygon.get(vertex)[0] * width;
        ys[vertex] = caster.polygon.get(vertex)[1] * height;
      }
      g.setStroke(i == selectedOccluderIndex ? Color.web("#ffcc78") : Color.web("#9ed4ff"));
      g.setLineWidth(i == selectedOccluderIndex ? 2 : 1);
      g.setLineDashes(caster.enabled ? null : new double[]{4, 4});
      g.strokePolygon(xs, ys, xs.length);
      g.setFill(g.getStroke());
      g.fillText(caster.name, xs[0] + 4, ys[0] - 4);
    }
    g.setStroke(Color.web("#ffcc78"));
    g.setLineDashes(4, 3);
    if (drawingOccluderBounds) {
      double x = Math.min(zoneDrawStartX, zoneDrawEndX);
      double y = Math.min(zoneDrawStartY, zoneDrawEndY);
      double w = Math.abs(zoneDrawStartX - zoneDrawEndX);
      double h = Math.abs(zoneDrawStartY - zoneDrawEndY);
      if (occluderBoundsDrawMode == OccluderShape.ELLIPSE) g.strokeOval(x, y, w, h);
      else g.strokeRect(x, y, w, h);
    }
    List<double[]> inProgress = occluderPolyDrawMode ? nailPoints : occluderFreehandDrawMode ? freehandPoints : List.of();
    for (int i = 1; i < inProgress.size(); i++) {
      double[] a = inProgress.get(i - 1);
      double[] b = inProgress.get(i);
      g.strokeLine(a[0], a[1], b[0], b[1]);
    }
    g.restore();
  }

  private static double[] parsePercentVector(String input) {
    double[] values = Stream.of(input.split(",")).map(String::strip).mapToDouble(Double::parseDouble).toArray();
    if (values.length != 4 || java.util.Arrays.stream(values).anyMatch(value -> !Double.isFinite(value))) {
      throw new IllegalArgumentException("Expected four percentages");
    }
    for (int i = 0; i < values.length; i++) values[i] /= 100;
    return values;
  }

  private static String groundProjectionString(VnStagePreset.GroundProjection ground, double units) {
    return formatNormalized(ground.groundX() * units) + "," + formatNormalized(ground.groundY() * units) + ","
        + formatNormalized(ground.horizon() * units) + "," + formatNormalized(ground.strength() * units);
  }

  private void drawGroundShadowPreview(GraphicsContext g, double width, double height) {
    VnStagePreset stage = previewRig();
    if (stage.getOccluders().isEmpty() || stage.getLights().stream().noneMatch(light -> light.groundProjection() != null)) return;
    double scale = Math.min(1, 900 / Math.max(width, height));
    int w = Math.max(1, (int) Math.round(width * scale));
    int h = Math.max(1, (int) Math.round(height * scale));
    if (groundShadowPreview == null || groundShadowPreview.getWidth() != w || groundShadowPreview.getHeight() != h) {
      byte[] mask = VnGroundShadows.mask(stage, w, h);
      if (mask == null) return;
      int[] ink = new int[mask.length];
      for (int i = 0; i < mask.length; i++) ink[i] = (mask[i] & 255) << 24;
      WritableImage shadow = new WritableImage(w, h);
      shadow.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), ink, 0, w);
      groundShadowPreview = shadow;
    }
    g.drawImage(groundShadowPreview, 0, 0, width, height);
  }

  private void drawShadowDebug(GraphicsContext g, double width, double height, String mode) {
    VnStagePreset rig = previewRig();
    VnStagePreset.Light light = selectedLightIndex >= 0 && selectedLightIndex < rig.getLights().size()
        ? rig.getLights().get(selectedLightIndex) : null;
    if (light == null || !light.castsShadows()) {
      light = rig.getLights().stream().filter(item -> rig.isActive(item) && item.castsShadows()).findFirst().orElse(null);
    }
    if (light == null) return;
    double scale = Math.min(1, 512 / Math.max(width, height));
    int w = Math.max(1, (int) Math.round(width * scale));
    int h = Math.max(1, (int) Math.round(height * scale));
    if (shadowDebugCache == null || shadowDebugCache.rig() != rig || shadowDebugCache.light() != light
        || shadowDebugCache.width() != w || shadowDebugCache.height() != h) {
      VnStageShadowDebug debug = new VnStageShadowDebug(rig, light, w, h);
      WritableImage mask = new WritableImage(w, h);
      mask.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), debug.mask(), 0, w);
      shadowDebugCache = new ShadowDebugCache(rig, light, w, h, mask, debug.sampledRays());
    }
    if ("Shadow Coverage".equals(mode) || "Rays + Coverage".equals(mode)) g.drawImage(shadowDebugCache.mask(), 0, 0, width, height);
    if ("Sample Rays".equals(mode) || "Rays + Coverage".equals(mode)) {
      g.save();
      g.setLineWidth(1);
      for (var ray : shadowDebugCache.rays()) {
        g.setStroke(ray.blocked() > 0.05 ? Color.web("#ff9db9", 0.72) : Color.web("#ffe292", 0.56));
        g.strokeLine(ray.sourceX() * width / w, ray.sourceY() * height / h, ray.x() * width / w, ray.y() * height / h);
      }
      g.restore();
    }
  }

  private void addDefaultOccluder() {
    SceneOccluder occluder = new SceneOccluder("Occluder " + (sceneOccluders.size() + 1));
    setOccluderBounds(occluder, OccluderShape.BOX, new VnStagePreset.Bounds(0.35, 0.18, 0.27, 0.24));
    addOccluder(occluder);
  }

  private void addOccluder(SceneOccluder occluder) {
    sceneOccluders.add(occluder);
    refreshOccluderList();
    invalidateTintCache();
    selectOccluder(sceneOccluders.size() - 1);
    invalidateTintCache();
    persistGlobalState();
    redrawPreview();
    status("Added " + occluder.name + ".");
  }

  private void removeSelectedOccluder() {
    if (selectedOccluderIndex < 0 || selectedOccluderIndex >= sceneOccluders.size()) {
      status("Select an occluder first.");
      return;
    }
    String name = sceneOccluders.get(selectedOccluderIndex).name;
    sceneOccluders.remove(selectedOccluderIndex);
    selectedOccluderIndex = Math.min(selectedOccluderIndex, sceneOccluders.size() - 1);
    refreshOccluderList();
    if (selectedOccluderIndex >= 0) selectOccluder(selectedOccluderIndex);
    else occluderControlsSection.setDisable(true);
    invalidateTintCache();
    persistGlobalState();
    redrawPreview();
    status("Removed " + name + ".");
  }

  private void clearAllOccluders() {
    if (sceneOccluders.isEmpty()) return;
    sceneOccluders.clear();
    selectedOccluderIndex = -1;
    occluderControlsSection.setDisable(true);
    refreshOccluderList();
    invalidateTintCache();
    persistGlobalState();
    redrawPreview();
    status("All occluders cleared.");
  }

  private void selectOccluder(int index) {
    selectedOccluderIndex = (index >= 0 && index < sceneOccluders.size()) ? index : -1;
    if (selectedOccluderIndex < 0) {
      occluderControlsSection.setDisable(true);
      refreshOccluderList();
      return;
    }
    SceneOccluder occluder = sceneOccluders.get(selectedOccluderIndex);
    occluderControlsSection.setDisable(false);
    applyingState = true;
    try {
      occluderNameField.setText(occluder.name == null ? "" : occluder.name);
      occluderShapeBox.setValue(occluder.shape);
      occluderBoundsField.setDisable(occluder.bounds == null);
      occluderBoundsField.setText(occluder.bounds == null ? "" : boundsString(occluder.bounds, 100));
      occluderOpacitySlider.setValue(occluder.opacity);
      occluderSoftnessSlider.setValue(occluder.softness);
      occluderContactSlider.setValue(Math.max(0, occluder.contactY * 100));
      occluderHeightSlider.setValue(occluder.heightScale * 100);
      occluderFootprintSlider.setValue(occluder.footprintDepth * 100);
      occluderFadeSlider.setValue(occluder.shadowFade * 100);
      occluderRoundCheck.setSelected(occluder.roundFootprint);
      occluderFloorContactsField.setText(floorContactsString(occluder.floorContacts, 100));
      occluderFramingField.setText(framingString(occluder.shadowFraming, 100));
      occluderExposureSlider.setValue(occluder.surfaceTone == null ? 0 : occluder.surfaceTone.exposure() * 100);
      occluderContrastSlider.setValue(occluder.surfaceTone == null ? 0 : occluder.surfaceTone.contrast() * 100);
      occluderEnabledCheck.setSelected(occluder.enabled);
    } finally {
      applyingState = false;
    }
    occluderListView.getSelectionModel().select(selectedOccluderIndex);
    refreshOccluderList();
    redrawPreview();
  }

  private void refreshOccluderList() {
    applyingState = true;
    try {
      occluderListView.getItems().setAll(sceneOccluders);
      if (selectedOccluderIndex >= 0 && selectedOccluderIndex < sceneOccluders.size()) {
        occluderListView.getSelectionModel().select(selectedOccluderIndex);
      }
    } finally {
      applyingState = false;
    }
  }

  private void applyOccluderControlsToSelected() {
    if (selectedOccluderIndex < 0 || selectedOccluderIndex >= sceneOccluders.size()) return;
    SceneOccluder occluder = sceneOccluders.get(selectedOccluderIndex);
    occluder.name = normalize(occluderNameField.getText());
    occluder.opacity = occluderOpacitySlider.getValue();
    occluder.softness = occluderSoftnessSlider.getValue();
    occluder.contactY = occluderContactSlider.getValue() <= 0 ? -1 : occluderContactSlider.getValue() / 100;
    occluder.heightScale = occluderHeightSlider.getValue() / 100;
    occluder.footprintDepth = occluderFootprintSlider.getValue() / 100;
    occluder.shadowFade = occluderFadeSlider.getValue() / 100;
    occluder.roundFootprint = occluderRoundCheck.isSelected();
    double ev = occluderExposureSlider.getValue() / 100;
    double contrast = occluderContrastSlider.getValue() / 100;
    occluder.surfaceTone = ev == 0 && contrast == 0 ? null : new VnStagePreset.SurfaceTone(ev, contrast);
    occluder.enabled = occluderEnabledCheck.isSelected();
    refreshOccluderList();
    invalidateTintCache();
    persistGlobalState();
    redrawPreview();
  }

  private void moveLightUp() {
    if (selectedLightIndex <= 0 || selectedLightIndex >= sceneLights.size()) return;
    SceneLight light = sceneLights.remove(selectedLightIndex);
    sceneLights.add(selectedLightIndex - 1, light);
    selectedLightIndex--;
    invalidateTintCache();
    refreshLightList();
    selectLight(selectedLightIndex);
    persistGlobalState();
    redrawPreview();
  }

  private void moveLightDown() {
    if (selectedLightIndex < 0 || selectedLightIndex >= sceneLights.size() - 1) return;
    SceneLight light = sceneLights.remove(selectedLightIndex);
    sceneLights.add(selectedLightIndex + 1, light);
    selectedLightIndex++;
    invalidateTintCache();
    refreshLightList();
    selectLight(selectedLightIndex);
    persistGlobalState();
    redrawPreview();
  }

  private void selectLight(int index) {
    selectedLightIndex = (index >= 0 && index < sceneLights.size()) ? index : -1;
    if (selectedLightIndex < 0) {
      lightControlsSection.setDisable(true);
      updateLightRigSummary();
      refreshLightList();
      return;
    }
    lightControlsSection.setDisable(false);
    SceneLight light = sceneLights.get(selectedLightIndex);
    applyingState = true;
    try {
      lightNameField.setText(light.name == null ? "" : light.name);
      lightColorPicker.setValue(light.color == null ? Color.web("#ffd7a8") : light.color);
      updateLightColorSwatch(lightColorPicker.getValue());
      lightIntensitySlider.setValue(light.intensity);
      lightRadiusSlider.setValue(light.radius * 100.0);
      lightSoftnessSlider.setValue(light.softness);
      lightSilhouetteSlider.setValue(light.silhouette);
      lightShapeBox.getSelectionModel().select((light.shape == null ? SceneLightShape.RADIAL : light.shape).toString());
      lightLayerBox.getSelectionModel().select((light.layer == null ? SceneLightLayer.CHARACTER : light.layer).toString());
      lightBlendBox.setValue(light.blend);
      lightFalloffBox.setValue(light.falloff);
      lightShadowsCheck.setSelected(light.castsShadows);
      shadowQualityBox.setValue(light.shadowQuality);
      beamAngleSlider.setValue(light.beamAngle);
      areaAspectSlider.setValue(light.areaAspect * 100);
      groundProjectionCheck.setSelected(light.groundProjection != null);
      groundProjectionField.setText(light.groundProjection == null ? "" : groundProjectionString(light.groundProjection, 100));
      floorLightSlider.setValue(light.groundProjection == null ? 0 : light.groundProjection.floorLight() * 100);
      floorExposureSlider.setValue(light.groundProjection == null ? 0 : light.groundProjection.floorExposure() * 100);
      lightGroupField.setText(light.group == null ? "" : light.group);
      lightMutedCheck.setSelected(light.muted);
      lightLockedCheck.setSelected(light.locked);
      lightSoloCheck.setSelected(light.solo);
    } finally {
      applyingState = false;
    }
    lightListView.getSelectionModel().select(selectedLightIndex);
    updateLightRigSummary();
    redrawPreview();
  }

  private void refreshLightList() {
    applyingState = true;
    try {
      lightListView.getItems().setAll(sceneLights);
      if (selectedLightIndex >= 0 && selectedLightIndex < sceneLights.size()) {
        lightListView.getSelectionModel().select(selectedLightIndex);
      }
    } finally {
      applyingState = false;
    }
    updateLightRigSummary();
  }

  private void applyLightControlsToSelected() {
    if (selectedLightIndex < 0 || selectedLightIndex >= sceneLights.size()) return;
    SceneLight light = sceneLights.get(selectedLightIndex);
    light.color = lightColorPicker.getValue() == null ? Color.web("#ffd7a8") : lightColorPicker.getValue();
    light.intensity = lightIntensitySlider.getValue();
    light.radius = clamp(lightRadiusSlider.getValue() / 100.0, 0.05, 0.80);
    light.softness = lightSoftnessSlider.getValue();
    light.silhouette = lightSilhouetteSlider.getValue();
    SceneLightShape nextShape = SceneLightShape.fromPersisted(lightShapeBox.getValue());
    light.layer = SceneLightLayer.fromPersisted(lightLayerBox.getValue());
    light.blend = Objects.requireNonNullElse(lightBlendBox.getValue(), LightBlend.PHYSICAL);
    light.falloff = Objects.requireNonNullElse(lightFalloffBox.getValue(), LightFalloff.CLASSIC);
    light.castsShadows = lightShadowsCheck.isSelected();
    light.shadowQuality = Objects.requireNonNullElse(shadowQualityBox.getValue(), ShadowQuality.SOFT);
    light.beamAngle = beamAngleSlider.getValue();
    light.areaAspect = areaAspectSlider.getValue() / 100;
    if (!groundProjectionCheck.isSelected()) light.groundProjection = null;
    else {
      try {
        double[] v = parsePercentVector(groundProjectionField.getText());
        light.groundProjection = new VnStagePreset.GroundProjection(v[0], v[1], v[2], v[3],
            floorLightSlider.getValue() / 100, floorExposureSlider.getValue() / 100);
      } catch (IllegalArgumentException ignored) {
        status("Enter floor X, floor Y, horizon Y and shadow strength as four percentages.");
      }
    }
    if (nextShape != SceneLightShape.POLYGON) {
      light.shape = nextShape;
      if (nextShape != SceneLightShape.POLYGON) {
        light.polygon = null;
      }
    } else {
      light.shape = SceneLightShape.POLYGON;
      if (light.polygon == null || light.polygon.size() < 3) {
        double halfSize = clamp(light.radius * 0.6, 0.04, 0.20);
        light.polygon = new ArrayList<>();
        light.polygon.add(new double[]{clamp(light.sceneX - halfSize, 0.0, 1.0), clamp(light.sceneY - halfSize, 0.0, 1.0)});
        light.polygon.add(new double[]{clamp(light.sceneX + halfSize, 0.0, 1.0), clamp(light.sceneY - halfSize, 0.0, 1.0)});
        light.polygon.add(new double[]{clamp(light.sceneX + halfSize, 0.0, 1.0), clamp(light.sceneY + halfSize, 0.0, 1.0)});
        light.polygon.add(new double[]{clamp(light.sceneX - halfSize, 0.0, 1.0), clamp(light.sceneY + halfSize, 0.0, 1.0)});
      }
    }
    light.group = normalize(lightGroupField.getText());
    light.muted = lightMutedCheck.isSelected();
    light.locked = lightLockedCheck.isSelected();
    light.solo = lightSoloCheck.isSelected();
    invalidateTintCache();
    refreshLightList();
    persistGlobalState();
    redrawPreview();
  }

  private void applyLightNameToSelected() {
    if (selectedLightIndex < 0 || selectedLightIndex >= sceneLights.size()) return;
    sceneLights.get(selectedLightIndex).name = normalize(lightNameField.getText());
    refreshLightList();
    persistGlobalState();
  }

  private void applyLightGroupToSelected() {
    if (selectedLightIndex < 0 || selectedLightIndex >= sceneLights.size()) return;
    sceneLights.get(selectedLightIndex).group = normalize(lightGroupField.getText());
    refreshLightList();
    persistGlobalState();
  }

  private void applyLightFlagsToSelected() {
    if (selectedLightIndex < 0 || selectedLightIndex >= sceneLights.size()) return;
    SceneLight light = sceneLights.get(selectedLightIndex);
    light.muted = lightMutedCheck.isSelected();
    light.locked = lightLockedCheck.isSelected();
    light.solo = lightSoloCheck.isSelected();
    invalidateTintCache();
    refreshLightList();
    persistGlobalState();
    redrawPreview();
  }

  private void toggleLightGroup(boolean solo) {
    if (selectedLightIndex < 0 || selectedLightIndex >= sceneLights.size()) return;
    String group = sceneLights.get(selectedLightIndex).group;
    if (group == null || group.isBlank()) return;
    List<SceneLight> members = sceneLights.stream().filter(light -> group.equals(light.group)).toList();
    boolean turnOn = members.stream().anyMatch(light -> solo ? !light.solo : !light.muted);
    for (SceneLight light : members) {
      if (solo) light.solo = turnOn;
      else light.muted = turnOn;
    }
    invalidateTintCache();
    refreshLightList();
    selectLight(selectedLightIndex);
    persistGlobalState();
    status((solo ? (turnOn ? "Soloed " : "Restored ") : (turnOn ? "Muted " : "Unmuted ")) + group + ".");
  }

  private void updateLightRigSummary() {
    if (lightRigSummaryLabel == null) return;
    if (sceneLights.isEmpty()) {
      lightRigSummaryLabel.setText("No lights staged. Add a key, fill, or backlight to build the rig.");
      return;
    }
    int count = sceneLights.size();
    long active = sceneLights.stream().filter(light -> !light.muted && light.intensity > 0).count();
    lightRigSummaryLabel.setText(count + (count == 1 ? " light" : " lights") + " · " + active + " enabled");
  }

  private static String describeLightMeta(SceneLight light) {
    return light.shape + " · " + Math.round(light.intensity) + "%"
        + (light.solo ? " · Solo" : "") + (light.locked ? " · Locked" : "")
        + (light.group == null || light.group.isBlank() ? "" : " · " + light.group);
  }

  private void invalidateTintCache() {
    previewRig = null;
    previewOverlays.clear();
    groundShadowPreview = null;
    tintedImageTag = null;
    tintedImageKey = null;
    tintedImage = null;
  }

  private void invalidateBackgroundTintCache() {
    previewRig = null;
    previewOverlays.clear();
    cancelBackgroundTintTask();
    tintedBackgroundTag = null;
    tintedBackgroundKey = null;
    tintedBackground = null;
  }

  private static void drawDiamondHandle(GraphicsContext g, double centerX, double centerY, double radius, Color fill, Color stroke) {
    double[] xs = {centerX, centerX + radius, centerX, centerX - radius};
    double[] ys = {centerY - radius, centerY, centerY + radius, centerY};
    g.setFill(fill);
    g.fillPolygon(xs, ys, 4);
    g.setStroke(stroke);
    g.strokePolygon(xs, ys, 4);
  }

  private static void drawEmitterHandle(GraphicsContext g,
                                        double centerX,
                                        double centerY,
                                        double radius,
                                        Color color,
                                        boolean selected) {
    double haloRadius = radius * (selected ? 3.8 : 3.0);
    g.setFill(new RadialGradient(
        0,
        0,
        centerX,
        centerY,
        haloRadius,
        false,
        CycleMethod.NO_CYCLE,
        new Stop(0.0, Color.color(color.getRed(), color.getGreen(), color.getBlue(), selected ? 0.42 : 0.28)),
        new Stop(0.48, Color.color(color.getRed(), color.getGreen(), color.getBlue(), selected ? 0.18 : 0.10)),
        new Stop(1.0, Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.0))
    ));
    g.fillOval(centerX - haloRadius, centerY - haloRadius, haloRadius * 2.0, haloRadius * 2.0);
    drawDiamondHandle(
        g,
        centerX,
        centerY,
        radius,
        Color.color(color.getRed(), color.getGreen(), color.getBlue(), selected ? 0.97 : 0.84),
        selected ? Color.WHITE : Color.color(1, 1, 1, 0.62));
    g.setStroke(Color.color(1, 1, 1, selected ? 0.34 : 0.18));
    g.setLineWidth(selected ? 1.5 : 1.0);
    g.strokeOval(centerX - radius * 1.15, centerY - radius * 1.15, radius * 2.3, radius * 2.3);
  }

  private static void fillBeamPolygon(GraphicsContext g,
                                      double sourceX,
                                      double sourceY,
                                      double targetX,
                                      double targetY,
                                      double[] xs,
                                      double[] ys,
                                      Color color,
                                      boolean selected) {
    g.setFill(new LinearGradient(
        sourceX,
        sourceY,
        targetX,
        targetY,
        false,
        CycleMethod.NO_CYCLE,
        new Stop(0.0, Color.color(color.getRed(), color.getGreen(), color.getBlue(), selected ? 0.24 : 0.14)),
        new Stop(0.58, Color.color(color.getRed(), color.getGreen(), color.getBlue(), selected ? 0.12 : 0.07)),
        new Stop(1.0, Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.0))
    ));
    g.fillPolygon(xs, ys, xs.length);
    g.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), selected ? 0.38 : 0.20));
    g.setLineWidth(selected ? 1.2 : 0.9);
    g.strokePolygon(xs, ys, xs.length);
  }

  private static void drawBeam(GraphicsContext g,
                               double sourceX,
                               double sourceY,
                               double targetX,
                               double targetY,
                               double startHalfWidth,
                               double endHalfWidth,
                               Color color,
                               boolean selected) {
    double dx = targetX - sourceX;
    double dy = targetY - sourceY;
    double length = Math.hypot(dx, dy);
    if (length < 1e-6) return;
    double nx = dx / length;
    double ny = dy / length;
    double perpX = -ny;
    double perpY = nx;
    double[] xs = {
        sourceX + perpX * startHalfWidth,
        targetX + perpX * endHalfWidth,
        targetX - perpX * endHalfWidth,
        sourceX - perpX * startHalfWidth
    };
    double[] ys = {
        sourceY + perpY * startHalfWidth,
        targetY + perpY * endHalfWidth,
        targetY - perpY * endHalfWidth,
        sourceY - perpY * startHalfWidth
    };
    fillBeamPolygon(g, sourceX, sourceY, targetX, targetY, xs, ys, color, selected);
  }

  private static void drawFootprintEllipse(GraphicsContext g,
                                           double centerX,
                                           double centerY,
                                           double angleDeg,
                                           double width,
                                           double height,
                                           Color color,
                                           boolean selected) {
    g.save();
    g.translate(centerX, centerY);
    g.rotate(angleDeg);
    g.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), selected ? 0.17 : 0.11));
    g.fillOval(-width * 0.5, -height * 0.5, width, height);
    g.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), selected ? 0.92 : 0.58));
    g.setLineWidth(selected ? 1.9 : 1.1);
    g.strokeOval(-width * 0.5, -height * 0.5, width, height);
    g.restore();
  }

  private static void drawFootprintRect(GraphicsContext g,
                                        double centerX,
                                        double centerY,
                                        double angleDeg,
                                        double width,
                                        double height,
                                        Color color,
                                        boolean selected) {
    g.save();
    g.translate(centerX, centerY);
    g.rotate(angleDeg);
    g.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), selected ? 0.15 : 0.10));
    g.fillRoundRect(-width * 0.5, -height * 0.5, width, height, 8.0, 8.0);
    g.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), selected ? 0.92 : 0.58));
    g.setLineWidth(selected ? 1.8 : 1.1);
    g.strokeRoundRect(-width * 0.5, -height * 0.5, width, height, 8.0, 8.0);
    g.restore();
  }

  private static double[] polygonCentroid(List<double[]> polygon, double canvasWidth, double canvasHeight) {
    if (polygon == null || polygon.isEmpty()) return new double[]{0.0, 0.0};
    double cx = 0.0;
    double cy = 0.0;
    for (double[] point : polygon) {
      cx += point[0] * canvasWidth;
      cy += point[1] * canvasHeight;
    }
    return new double[]{cx / polygon.size(), cy / polygon.size()};
  }

  private static void drawLightVectorGuide(GraphicsContext g,
                                           SceneLight light,
                                           double canvasWidth,
                                           double canvasHeight,
                                           double sourceX,
                                           double sourceY,
                                           double targetX,
                                           double targetY,
                                           double radius,
                                           Color color,
                                           boolean selected) {
    double dx = targetX - sourceX;
    double dy = targetY - sourceY;
    double length = Math.hypot(dx, dy);
    if (length < 1e-6) return;
    double nx = dx / length;
    double ny = dy / length;
    double angleDeg = Math.toDegrees(Math.atan2(dy, dx));
    SceneLightShape shape = light == null || light.shape == null ? SceneLightShape.RADIAL : light.shape;
    Color guide = Color.color(color.getRed(), color.getGreen(), color.getBlue(), selected ? 0.92 : 0.58);
    double beamStart = Math.max(4.0, radius * 0.08);
    switch (shape) {
      case CONE -> {
        drawBeam(g, sourceX, sourceY, targetX, targetY, beamStart, radius * 0.76, color, selected);
        drawFootprintEllipse(g, targetX, targetY, angleDeg, radius * 1.55, radius * 0.82, color, selected);
      }
      case STRIP -> {
        drawBeam(g, sourceX, sourceY, targetX, targetY, beamStart, radius * 0.46, color, selected);
        drawFootprintRect(g, targetX, targetY, angleDeg, radius * 1.35, radius * 0.34, color, selected);
      }
      case WINDOW -> {
        drawBeam(g, sourceX, sourceY, targetX, targetY, beamStart * 1.2, radius * 0.62, color, selected);
        drawFootprintRect(g, targetX, targetY, angleDeg, radius * 1.12, radius * 1.48, color, selected);
      }
      case BOUNCE -> {
        double bounceTargetX = targetX - nx * Math.max(6.0, radius * 0.16);
        double bounceTargetY = targetY - ny * Math.max(6.0, radius * 0.16);
        drawBeam(g, sourceX, sourceY, bounceTargetX, bounceTargetY, beamStart * 1.5, radius * 0.52, color, selected);
        drawFootprintEllipse(g, targetX, targetY, angleDeg, radius * 1.62, radius * 0.62, color, selected);
      }
      case POLYGON -> {
        double[] centroid = polygonCentroid(light == null ? null : light.polygon, canvasWidth, canvasHeight);
        double centroidX = centroid[0];
        double centroidY = centroid[1];
        if (light != null && light.polygon != null && light.polygon.size() >= 3) {
          drawBeam(g, sourceX, sourceY, centroidX, centroidY, beamStart, Math.max(6.0, radius * 0.28), color, selected);
          int step = Math.max(1, light.polygon.size() / 3);
          g.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), selected ? 0.46 : 0.24));
          g.setLineWidth(selected ? 1.1 : 0.8);
          for (int i = 0; i < light.polygon.size(); i += step) {
            double[] point = light.polygon.get(i);
            g.strokeLine(sourceX, sourceY, point[0] * canvasWidth, point[1] * canvasHeight);
          }
        }
      }
      case AREA, RADIAL -> {
        drawBeam(g, sourceX, sourceY, targetX, targetY, beamStart, radius * 0.54, color, selected);
        drawFootprintEllipse(g, targetX, targetY, angleDeg, radius * 1.22, radius * 1.00, color, selected);
      }
    }

    g.setStroke(guide);
    g.setLineWidth(selected ? 1.6 : 1.0);
    g.setLineDashes(selected ? null : new double[]{6, 4});
    g.strokeLine(sourceX, sourceY, targetX, targetY);
    g.setLineDashes((double[]) null);
  }

  private VnStagePreset previewRig() {
    if (previewRig == null) {
      List<VnStagePreset.Light> lights = sceneLights.stream().map(light -> new VnStagePreset.Light(
          light.name, VnStagePreset.LightType.from(light.shape.persistedValue()),
          VnStagePreset.LightLayer.from(light.layer.persistedValue()), light.sceneX, light.sceneY,
          light.sourceX, light.sourceY, colorToHex(light.color), light.intensity / 100, light.radius,
          light.softness / 100, light.silhouette / 100, light.muted, light.locked, light.solo, light.group,
          presetPoints(light.polygon), light.blend, light.falloff, light.castsShadows, light.shadowQuality, light.beamAngle, light.areaAspect, light.groundProjection)).toList();
      List<VnStagePreset.Occluder> shadows = sceneOccluders.stream().map(occluder -> new VnStagePreset.Occluder(
          occluder.name, occluder.opacity / 100, occluder.softness / 100, occluder.enabled,
          presetPoints(occluder.polygon), occluder.shape, occluder.bounds, occluder.contactY, occluder.heightScale, occluder.footprintDepth, occluder.roundFootprint, occluder.surfaceTone, occluder.floorContacts, occluder.shadowFade, occluder.shadowFraming)).toList();
      var grade = new VnStagePreset.BackgroundGrade(colorToHex(bgTintColorPicker.getValue()),
          bgTintStrengthSlider.getValue() / 100, bgSaturationSlider.getValue() / 100,
          bgContrastSlider.getValue() / 100, colorToHex(bgOverlayColorPicker.getValue()),
          bgOverlayOpacitySlider.getValue() / 100, canonicalBlendMode(bgTintBlendModeBox.getValue(), "Normal"),
          canonicalBlendMode(bgOverlayBlendModeBox.getValue(), "Overlay"), bgExposureSlider.getValue() / 100);
      previewRig = new VnStagePreset("", "", "", "", grade, lights, shadows, List.of());
    }
    return previewRig;
  }

  private static List<VnStagePreset.Point> presetPoints(List<double[]> points) {
    return points == null ? List.of() : points.stream().map(point -> new VnStagePreset.Point(point[0], point[1])).toList();
  }

  private void drawSceneLightEffects(GraphicsContext g, double canvasWidth, double canvasHeight, SceneLightLayer layer) {
    if (sceneLights.isEmpty()) return;
    if (previewOverlayWidth != canvasWidth || previewOverlayHeight != canvasHeight) {
      previewOverlayWidth = canvasWidth;
      previewOverlayHeight = canvasHeight;
      previewOverlays.clear();
    }
    previewOverlays.computeIfAbsent(layer, key -> VnStageLightOverlay.prepare(previewRig(),
        VnStagePreset.LightLayer.from(key.persistedValue()), canvasWidth, canvasHeight)).draw(g);
  }

  private void drawSceneLightOverlays(GraphicsContext g, double canvasWidth, double canvasHeight) {
    if (sceneLights.isEmpty()) return;
    double minDimension = Math.max(1.0, Math.min(canvasWidth, canvasHeight));
    for (int i = 0; i < sceneLights.size(); i++) {
      SceneLight light = sceneLights.get(i);
      double sourceX = light.sourceX * canvasWidth;
      double sourceY = light.sourceY * canvasHeight;
      double cx = light.sceneX * canvasWidth;
      double cy = light.sceneY * canvasHeight;
      double radius = Math.max(10.0, light.radius * minDimension);
      boolean selected = i == selectedLightIndex;
      boolean showVector = showLightVectorsCheck.isSelected() || selected;
      Color color = light.color == null ? Color.web("#ffd7a8") : light.color;
      SceneLightLayer lightLayer = light.layer == null ? SceneLightLayer.CHARACTER : light.layer;
      if (showVector) {
        drawLightVectorGuide(g, light, canvasWidth, canvasHeight, sourceX, sourceY, cx, cy, radius, color, selected);
      }
      if (light.isPolygon()) {
        double[] xs = new double[light.polygon.size()];
        double[] ys = new double[light.polygon.size()];
        for (int p = 0; p < light.polygon.size(); p++) {
          xs[p] = light.polygon.get(p)[0] * canvasWidth;
          ys[p] = light.polygon.get(p)[1] * canvasHeight;
        }
        g.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), selected ? 0.14 : 0.08));
        g.fillPolygon(xs, ys, xs.length);
        g.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), selected ? 0.95 : 0.60));
        g.setLineWidth(selected ? 2.0 : 1.2);
        g.setLineDashes(lightLayer == SceneLightLayer.CHARACTER ? null : new double[]{6, 4});
        g.strokePolygon(xs, ys, xs.length);
        g.setLineDashes((double[]) null);
      } else if (!showVector) {
        double dx = cx - sourceX;
        double dy = cy - sourceY;
        double angleDeg = Math.toDegrees(Math.atan2(dy, dx));
        SceneLightShape shape = light.shape == null ? SceneLightShape.RADIAL : light.shape;
        switch (shape) {
          case CONE -> drawFootprintEllipse(g, cx, cy, angleDeg, radius * 1.55, radius * 0.82, color, selected);
          case STRIP -> drawFootprintRect(g, cx, cy, angleDeg, radius * 1.35, radius * 0.34, color, selected);
          case WINDOW -> drawFootprintRect(g, cx, cy, angleDeg, radius * 1.12, radius * 1.48, color, selected);
          case BOUNCE -> drawFootprintEllipse(g, cx, cy, angleDeg, radius * 1.62, radius * 0.62, color, selected);
          default -> drawFootprintEllipse(g, cx, cy, angleDeg, radius * 1.22, radius * 1.00, color, selected);
        }
      }

      if (showVector) {
        double sourceHandleRadius = selected ? (LIGHT_HANDLE_RADIUS_PX - 2.0) : (LIGHT_HANDLE_RADIUS_PX - 4.0);
        drawEmitterHandle(
            g,
            sourceX,
            sourceY,
            sourceHandleRadius,
            color,
            selected);
      }

      double handleRadius = selected ? LIGHT_HANDLE_RADIUS_PX : (LIGHT_HANDLE_RADIUS_PX - 2.0);
      g.setFill(color);
      g.fillOval(cx - handleRadius, cy - handleRadius, handleRadius * 2.0, handleRadius * 2.0);
      g.setStroke(selected ? Color.WHITE : Color.color(1, 1, 1, 0.65));
      g.setLineWidth(selected ? 2.0 : 1.0);
      g.strokeOval(cx - handleRadius, cy - handleRadius, handleRadius * 2.0, handleRadius * 2.0);
      g.strokeLine(cx - 5.0, cy, cx + 5.0, cy);
      g.strokeLine(cx, cy - 5.0, cx, cy + 5.0);

      String label = ((light.name == null || light.name.isBlank()) ? "Light " + (i + 1) : light.name)
          + " · "
          + (lightLayer == SceneLightLayer.BACKGROUND ? "bg" : lightLayer == SceneLightLayer.FOREGROUND ? "fg" : "char");
      g.setFill(Color.color(1, 1, 1, selected ? 0.92 : 0.78));
      g.setTextAlign(TextAlignment.LEFT);
      g.fillText(label, cx + handleRadius + 5.0, cy - handleRadius - 2.0);
      if (showVector) {
        g.setFill(Color.color(1, 1, 1, selected ? 0.74 : 0.58));
        g.fillText("src", sourceX + 8.0, sourceY - 6.0);
      }
    }
  }

  // ── Zone overlay drawing ──

  private void drawZoneOverlays(GraphicsContext g, Image img, double drawX, double drawY, double drawW, double drawH) {
    for (int i = 0; i < tintZones.size(); i++) {
      TintZone zone = tintZones.get(i);
      if (!zone.overlayVisible) continue;
      boolean selected = (i == selectedZoneIndex);
      Color zc = zone.color == null ? Color.web("#ff8844") : zone.color;

      if (zone.isPolygon()) {
        // Draw polygon overlay.
        List<double[]> poly = zone.polygon;
        double[] xs = new double[poly.size()];
        double[] ys = new double[poly.size()];
        for (int p = 0; p < poly.size(); p++) {
          xs[p] = drawX + poly.get(p)[0] * drawW;
          ys[p] = drawY + poly.get(p)[1] * drawH;
        }
        g.setFill(Color.color(zc.getRed(), zc.getGreen(), zc.getBlue(), selected ? 0.20 : 0.12));
        g.fillPolygon(xs, ys, poly.size());
        g.setStroke(selected
            ? Color.color(zc.getRed(), zc.getGreen(), zc.getBlue(), 0.9)
            : Color.color(zc.getRed(), zc.getGreen(), zc.getBlue(), 0.55));
        g.setLineWidth(selected ? 2.2 : 1.3);
        g.setLineDashes((double[]) null);
        g.strokePolygon(xs, ys, poly.size());
        // Vertex dots.
        g.setFill(selected ? Color.rgb(255, 240, 140, 0.95) : Color.rgb(255, 140, 140, 0.85));
        for (int p = 0; p < poly.size(); p++) {
          g.fillOval(xs[p] - 3, ys[p] - 3, 6, 6);
        }
        // Label at first vertex.
        String label = (zone.name == null || zone.name.isBlank() ? "Zone " + (i + 1) : zone.name);
        g.setFill(Color.color(1, 1, 1, 0.85));
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText(label, xs[0] + 6, ys[0] - 4);
      } else {
        // Draw rectangle overlay (with optional rotation).
        double zx = drawX + zone.boundsX * drawW;
        double zy = drawY + zone.boundsY * drawH;
        double zw = zone.boundsW * drawW;
        double zh = zone.boundsH * drawH;

        boolean rotated = zone.rotation != 0.0;
        if (rotated) {
          g.save();
          double rcx = zx + zw * 0.5, rcy = zy + zh * 0.5;
          g.translate(rcx, rcy);
          g.rotate(zone.rotation);
          g.translate(-rcx, -rcy);
        }

        g.setFill(Color.color(zc.getRed(), zc.getGreen(), zc.getBlue(), selected ? 0.18 : 0.10));
        g.fillRect(zx, zy, zw, zh);
        g.setStroke(selected
            ? Color.color(zc.getRed(), zc.getGreen(), zc.getBlue(), 0.9)
            : Color.color(zc.getRed(), zc.getGreen(), zc.getBlue(), 0.5));
        g.setLineWidth(selected ? 2.0 : 1.0);
        g.setLineDashes(selected ? null : new double[]{5, 3});
        g.strokeRect(zx, zy, zw, zh);
        g.setLineDashes((double[]) null);

        String label = (zone.name == null || zone.name.isBlank() ? "Zone " + (i + 1) : zone.name);
        g.setFill(Color.color(1, 1, 1, 0.85));
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText(label, zx + 4, zy + 13);

        if (rotated) g.restore();
      }
    }
  }

  private void updateZoneColorSwatch(Color color) {
    Color c = color == null ? Color.web("#ff8844") : color;
    int r = (int) Math.round(clamp(c.getRed(), 0.0, 1.0) * 255.0);
    int g = (int) Math.round(clamp(c.getGreen(), 0.0, 1.0) * 255.0);
    int b = (int) Math.round(clamp(c.getBlue(), 0.0, 1.0) * 255.0);
    String hex = String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b);
    zoneColorSwatch.setStyle(
        "-fx-background-color: " + hex + ";"
            + " -fx-background-radius: 999;"
            + " -fx-border-color: rgba(255,255,255,0.40);"
            + " -fx-border-radius: 999;"
            + " -fx-border-width: 1;");
  }

  private void updateLightColorSwatch(Color color) {
    Color c = color == null ? Color.web("#ffd7a8") : color;
    int r = (int) Math.round(clamp(c.getRed(), 0.0, 1.0) * 255.0);
    int g = (int) Math.round(clamp(c.getGreen(), 0.0, 1.0) * 255.0);
    int b = (int) Math.round(clamp(c.getBlue(), 0.0, 1.0) * 255.0);
    String hex = String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b);
    lightColorSwatch.setStyle(
        "-fx-background-color: " + hex + ";"
            + " -fx-background-radius: 999;"
            + " -fx-border-color: rgba(255,255,255,0.40);"
            + " -fx-border-radius: 999;"
            + " -fx-border-width: 1;");
  }

  private void writeMaterialToPrefix(String prefix) {
    persisted.setProperty(prefix + "normalMap", normalMapField.getText());
    persisted.setProperty(prefix + "normalStrength", formatDouble(normalStrengthSlider.getValue()));
    persisted.setProperty(prefix + "specular", formatDouble(specularSlider.getValue()));
    persisted.setProperty(prefix + "gloss", formatDouble(glossSlider.getValue()));
  }

  private void loadMaterialFromPrefix(String prefix) {
    boolean previous = applyingState;
    applyingState = true;
    try {
      normalMapField.setText(persisted.getProperty(prefix + "normalMap", ""));
      normalStrengthSlider.setValue(parseDouble(persisted.getProperty(prefix + "normalStrength"), 70));
      specularSlider.setValue(parseDouble(persisted.getProperty(prefix + "specular"), 20));
      glossSlider.setValue(parseDouble(persisted.getProperty(prefix + "gloss"), 32));
    } finally {
      applyingState = previous;
    }
    invalidateTintCache();
  }

  private void writeSceneOccludersToPrefix(String prefix) {
    clearPrefix(prefix);
    persisted.setProperty(prefix + "count", Integer.toString(sceneOccluders.size()));
    for (int i = 0; i < sceneOccluders.size(); i++) {
      SceneOccluder occluder = sceneOccluders.get(i);
      String key = prefix + i + ".";
      persisted.setProperty(key + "name", occluder.name);
      persisted.setProperty(key + "opacity", formatNormalized(occluder.opacity / 100));
      persisted.setProperty(key + "softness", formatNormalized(occluder.softness / 100));
      persisted.setProperty(key + "enabled", Boolean.toString(occluder.enabled));
      persisted.setProperty(key + "shape", occluder.shape.name());
      persisted.setProperty(key + "contactY", formatNormalized(occluder.contactY));
      persisted.setProperty(key + "heightScale", formatNormalized(occluder.heightScale));
      persisted.setProperty(key + "footprintDepth", formatNormalized(occluder.footprintDepth));
      persisted.setProperty(key + "roundFootprint", Boolean.toString(occluder.roundFootprint));
      persisted.setProperty(key + "shadowFade", formatNormalized(occluder.shadowFade));
      if (occluder.shadowFraming != null) persisted.setProperty(key + "shadowFraming", framingString(occluder.shadowFraming, 1));
      if (!occluder.floorContacts.isEmpty()) persisted.setProperty(key + "floorContacts", floorContactsString(occluder.floorContacts, 1));
      if (occluder.surfaceTone != null) {
        persisted.setProperty(key + "surfaceExposure", formatNormalized(occluder.surfaceTone.exposure()));
        persisted.setProperty(key + "surfaceContrast", formatNormalized(occluder.surfaceTone.contrast()));
      }
      if (occluder.bounds != null) persisted.setProperty(key + "bounds", boundsString(occluder.bounds, 1));
      persisted.setProperty(key + "polygon", occluder.polygon.stream()
          .map(point -> formatNormalized(point[0]) + "," + formatNormalized(point[1]))
          .collect(java.util.stream.Collectors.joining(";")));
    }
  }

  private void loadSceneOccludersFromPrefix(String prefix) {
    Properties portable = new Properties();
    portable.setProperty("occluders", persisted.getProperty(prefix + "count", "0"));
    for (String key : persisted.stringPropertyNames()) {
      if (key.startsWith(prefix)) portable.setProperty("occluder." + key.substring(prefix.length()), persisted.getProperty(key));
    }
    replaceSceneOccluders(VnStagePresetLoader.loadOccluders(portable));
    refreshOccluderList();
    selectOccluder(sceneOccluders.isEmpty() ? -1 : 0);
  }

  private void replaceSceneOccluders(List<VnStagePreset.Occluder> saved) {
    sceneOccluders.clear();
    selectedOccluderIndex = -1;
    for (VnStagePreset.Occluder stored : saved) {
      SceneOccluder occluder = new SceneOccluder(stored.name());
      occluder.enabled = stored.enabled();
      occluder.opacity = stored.opacity() * 100;
      occluder.softness = stored.softness() * 100;
      occluder.shape = stored.shape();
      occluder.bounds = stored.bounds();
      occluder.contactY = stored.contactY();
      occluder.heightScale = stored.heightScale();
      occluder.footprintDepth = stored.footprintDepth();
      occluder.roundFootprint = stored.roundFootprint();
      occluder.surfaceTone = stored.surfaceTone();
      occluder.floorContacts = stored.floorContacts();
      occluder.shadowFade = stored.shadowFade();
      occluder.shadowFraming = stored.shadowFraming();
      for (VnStagePreset.Point point : stored.polygon()) {
        occluder.polygon.add(new double[]{point.x(), point.y()});
      }
      sceneOccluders.add(occluder);
    }
    invalidateTintCache();
  }

  private void writeSceneLightsToPrefix(String prefix) {
    clearPrefix(prefix);
    persisted.setProperty(prefix + "count", Integer.toString(sceneLights.size()));
    for (int i = 0; i < sceneLights.size(); i++) {
      SceneLight light = sceneLights.get(i);
      String lightPrefix = prefix + i + ".";
      persisted.setProperty(lightPrefix + "name", light.name == null ? "" : light.name);
      persisted.setProperty(lightPrefix + "sceneX", formatDouble(light.sceneX));
      persisted.setProperty(lightPrefix + "sceneY", formatDouble(light.sceneY));
      persisted.setProperty(lightPrefix + "sourceX", formatDouble(light.sourceX));
      persisted.setProperty(lightPrefix + "sourceY", formatDouble(light.sourceY));
      persisted.setProperty(lightPrefix + "color", colorToHex(light.color));
      persisted.setProperty(lightPrefix + "intensity", formatDouble(light.intensity));
      persisted.setProperty(lightPrefix + "radius", formatDouble(light.radius));
      persisted.setProperty(lightPrefix + "softness", formatDouble(light.softness));
      persisted.setProperty(lightPrefix + "silhouette", formatDouble(light.silhouette));
      persisted.setProperty(lightPrefix + "shape", (light.shape == null ? SceneLightShape.RADIAL : light.shape).persistedValue());
      persisted.setProperty(lightPrefix + "layer", (light.layer == null ? SceneLightLayer.CHARACTER : light.layer).persistedValue());
      persisted.setProperty(lightPrefix + "blend", light.blend.persisted());
      persisted.setProperty(lightPrefix + "falloff", light.falloff.persisted());
      persisted.setProperty(lightPrefix + "castsShadows", Boolean.toString(light.castsShadows));
      persisted.setProperty(lightPrefix + "shadowQuality", light.shadowQuality.persisted());
      persisted.setProperty(lightPrefix + "beamAngle", formatNormalized(light.beamAngle));
      persisted.setProperty(lightPrefix + "areaAspect", formatNormalized(light.areaAspect));
      if (light.groundProjection != null) {
        persisted.setProperty(lightPrefix + "ground", groundProjectionString(light.groundProjection, 1));
        persisted.setProperty(lightPrefix + "floorLight", formatNormalized(light.groundProjection.floorLight()));
        persisted.setProperty(lightPrefix + "floorExposure", formatNormalized(light.groundProjection.floorExposure()));
      }
      persisted.setProperty(lightPrefix + "muted", Boolean.toString(light.muted));
      persisted.setProperty(lightPrefix + "locked", Boolean.toString(light.locked));
      persisted.setProperty(lightPrefix + "solo", Boolean.toString(light.solo));
      persisted.setProperty(lightPrefix + "group", Objects.toString(light.group, ""));
      if (light.isPolygon()) {
        StringBuilder poly = new StringBuilder();
        for (int p = 0; p < light.polygon.size(); p++) {
          if (p > 0) poly.append(';');
          poly.append(formatDouble(light.polygon.get(p)[0])).append(',').append(formatDouble(light.polygon.get(p)[1]));
        }
        persisted.setProperty(lightPrefix + "polygon", poly.toString());
      }
    }
  }

  private void loadSceneLightsFromPrefix(String prefix) {
    sceneLights.clear();
    selectedLightIndex = -1;
    int count = (int) parseDouble(persisted.getProperty(prefix + "count"), 0);
    for (int i = 0; i < count; i++) {
      try {
        sceneLights.add(loadLightFromPrefix(persisted, prefix + i + ".", i));
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    }
    refreshLightList();
    if (!sceneLights.isEmpty()) selectLight(0);
    else lightControlsSection.setDisable(true);
    invalidateTintCache();
  }

  private static SceneLight loadLightFromPrefix(Properties props, String prefix, int index) {
    double sceneX = clamp(parseDouble(props.getProperty(prefix + "sceneX"), 0.5), 0.0, 1.0);
    double sceneY = clamp(parseDouble(props.getProperty(prefix + "sceneY"), 0.35), 0.0, 1.0);
    String name = props.getProperty(prefix + "name", "Light " + (index + 1));
    SceneLight light = new SceneLight(name, sceneX, sceneY);
    light.sourceX = clamp(parseDouble(props.getProperty(prefix + "sourceX"), defaultLightSourceX(sceneX)), 0.0, 1.0);
    light.sourceY = clamp(parseDouble(props.getProperty(prefix + "sourceY"), defaultLightSourceY(sceneY)), 0.0, 1.0);
    light.color = parseColor(props.getProperty(prefix + "color"), Color.web("#ffd7a8"));
    light.intensity = clamp(parseDouble(props.getProperty(prefix + "intensity"), 42.0), 0.0, 100.0);
    light.radius = clamp(parseDouble(props.getProperty(prefix + "radius"), DEFAULT_LIGHT_RADIUS), 0.05, 0.80);
    light.softness = clamp(parseDouble(props.getProperty(prefix + "softness"), 55.0), 0.0, 100.0);
    light.silhouette = clamp(parseDouble(props.getProperty(prefix + "silhouette"), DEFAULT_LIGHT_SILHOUETTE), 0.0, 100.0);
    light.shape = SceneLightShape.fromPersisted(props.getProperty(prefix + "shape"));
    light.layer = SceneLightLayer.fromPersisted(props.getProperty(prefix + "layer"));
    light.blend = LightBlend.from(props.getProperty(prefix + "blend"));
    light.falloff = LightFalloff.from(props.getProperty(prefix + "falloff"));
    light.castsShadows = parseBoolean(props.getProperty(prefix + "castsShadows"), true);
    light.shadowQuality = ShadowQuality.from(props.getProperty(prefix + "shadowQuality"));
    light.beamAngle = clamp(parseDouble(props.getProperty(prefix + "beamAngle"), 0), 0, 175);
    light.areaAspect = clamp(parseDouble(props.getProperty(prefix + "areaAspect"), 0), 0, 1);
    light.groundProjection = VnStagePresetLoader.loadGroundProjection(props, prefix);
    light.muted = parseBoolean(props.getProperty(prefix + "muted"), false);
    light.locked = parseBoolean(props.getProperty(prefix + "locked"), false);
    light.solo = parseBoolean(props.getProperty(prefix + "solo"), false);
    light.group = props.getProperty(prefix + "group", "");
    String polygonRaw = props.getProperty(prefix + "polygon", "");
    if (!polygonRaw.isBlank()) {
      List<double[]> pts = new ArrayList<>();
      for (String pair : polygonRaw.split(";")) {
        String[] xy = pair.split(",");
        if (xy.length == 2) {
          pts.add(new double[]{
              clamp(parseDouble(xy[0].trim(), 0.5), 0.0, 1.0),
              clamp(parseDouble(xy[1].trim(), 0.5), 0.0, 1.0)
          });
        }
      }
      if (pts.size() >= 3) {
        light.polygon = pts;
        light.shape = SceneLightShape.POLYGON;
      }
    }
    return light;
  }

  // ── Zone persistence ──

  private String zoneProfilePrefix(String characterTag) {
    String tag = normalize(characterTag);
    if (tag.isBlank()) return ZONE_PROFILE_PREFIX + GLOBAL_ZONE_PROFILE_KEY + ".";
    return ZONE_PROFILE_PREFIX + encodeKey(tag) + ".";
  }

  private void writeZonesToPrefix(String prefix) {
    clearPrefix(prefix);
    persisted.setProperty(prefix + "count", Integer.toString(tintZones.size()));
    for (int i = 0; i < tintZones.size(); i++) {
      TintZone z = tintZones.get(i);
      String zonePrefix = prefix + i + ".";
      persisted.setProperty(zonePrefix + "name", z.name == null ? "" : z.name);
      persisted.setProperty(zonePrefix + "boundsX", formatDouble(z.boundsX));
      persisted.setProperty(zonePrefix + "boundsY", formatDouble(z.boundsY));
      persisted.setProperty(zonePrefix + "boundsW", formatDouble(z.boundsW));
      persisted.setProperty(zonePrefix + "boundsH", formatDouble(z.boundsH));
      persisted.setProperty(zonePrefix + "color", colorToHex(z.color));
      persisted.setProperty(zonePrefix + "strength", formatDouble(z.strength));
      persisted.setProperty(zonePrefix + "saturation", formatDouble(z.saturation));
      persisted.setProperty(zonePrefix + "contrast", formatDouble(z.contrast));
      persisted.setProperty(zonePrefix + "feather", formatDouble(z.feather));
      persisted.setProperty(zonePrefix + "rotation", formatDouble(z.rotation));
      persisted.setProperty(zonePrefix + "blendMode", canonicalBlendMode(z.blendMode, "Normal"));
      persisted.setProperty(zonePrefix + "overlayVisible", Boolean.toString(z.overlayVisible));
      persisted.setProperty(zonePrefix + "surface", z.surfaceClass.persistedValue());
      persisted.setProperty(zonePrefix + "depthBias", formatDouble(z.depthBias));
      persisted.setProperty(zonePrefix + "responseScale", formatDouble(z.responseScale));
      if (z.isPolygon()) {
        StringBuilder polyStr = new StringBuilder();
        for (int p = 0; p < z.polygon.size(); p++) {
          if (p > 0) polyStr.append(";");
          polyStr.append(formatDouble(z.polygon.get(p)[0])).append(",").append(formatDouble(z.polygon.get(p)[1]));
        }
        persisted.setProperty(zonePrefix + "polygon", polyStr.toString());
      }
    }
  }

  private int loadZonesFromPrefix(String prefix) {
    tintZones.clear();
    selectedZoneIndex = -1;
    int count = (int) parseDouble(persisted.getProperty(prefix + "count"), 0);
    int skipped = 0;
    for (int i = 0; i < count; i++) {
      try {
        TintZone z = loadZoneFromPrefix(persisted, prefix + i + ".", i);
        tintZones.add(z);
      } catch (Exception e) {
        skipped++;
      }
    }
    refreshZoneList();
    if (!tintZones.isEmpty()) selectZone(0);
    else zoneControlsSection.setDisable(true);
    return skipped;
  }

  private void switchZoneProfileForCharacter(String characterTag, boolean persistCurrentFirst) {
    String nextTag = normalize(characterTag);
    if (Objects.equals(nextTag, activeZoneProfileTag)) return;
    if (persistCurrentFirst && !activeZoneProfileTag.isBlank()) {
      writeZonesToPrefix(zoneProfilePrefix(activeZoneProfileTag));
      savePersistentState();
    }
    activeZoneProfileTag = nextTag;
    loadPersistedZones();
  }

  private void persistZones() {
    String selectedTag = normalize(selectedCharacterTag());
    if (!selectedTag.isBlank()) {
      activeZoneProfileTag = selectedTag;
    }
    writeZonesToPrefix(zoneProfilePrefix(activeZoneProfileTag));
    savePersistentState();
  }

  private void loadPersistedZones() {
    if (activeZoneProfileTag.isBlank()) {
      activeZoneProfileTag = normalize(selectedCharacterTag());
    }

    String profilePrefix = zoneProfilePrefix(activeZoneProfileTag);
    boolean hasProfile = persisted.containsKey(profilePrefix + "count");
    boolean hasLegacy = persisted.containsKey(LEGACY_ZONE_PREFIX + "count");
    int skipped;
    if (hasProfile) {
      skipped = loadZonesFromPrefix(profilePrefix);
    } else if (hasLegacy) {
      skipped = loadZonesFromPrefix(LEGACY_ZONE_PREFIX);
      writeZonesToPrefix(profilePrefix);
      savePersistentState();
    } else {
      tintZones.clear();
      selectedZoneIndex = -1;
      refreshZoneList();
      zoneControlsSection.setDisable(true);
      return;
    }
    if (skipped > 0) {
      status("Loaded " + tintZones.size() + " zones (" + skipped + " malformed entries skipped).");
    }
  }

  /** Shared helper: parse a single TintZone from a Properties source with the given prefix. */
  private static TintZone loadZoneFromPrefix(Properties props, String prefix, int index) {
    double bx = parseDouble(props.getProperty(prefix + "boundsX"), 0.25);
    double by = parseDouble(props.getProperty(prefix + "boundsY"), 0.25);
    double bw = parseDouble(props.getProperty(prefix + "boundsW"), 0.5);
    double bh = parseDouble(props.getProperty(prefix + "boundsH"), 0.5);
    String name = props.getProperty(prefix + "name", "Zone " + (index + 1));
    TintZone z = new TintZone(name, bx, by, bw, bh);
    z.color = parseColor(props.getProperty(prefix + "color"), Color.web("#ff8844"));
    z.strength = parseDouble(props.getProperty(prefix + "strength"), 50);
    z.saturation = parseDouble(props.getProperty(prefix + "saturation"), 0);
    z.contrast = parseDouble(props.getProperty(prefix + "contrast"), 0);
    z.feather = parseDouble(props.getProperty(prefix + "feather"), 15);
    z.rotation = parseDouble(props.getProperty(prefix + "rotation"), 0);
    String bm = props.getProperty(prefix + "blendMode", "Normal");
    z.blendMode = canonicalBlendMode(bm, "Normal");
    z.surfaceClass = SubjectSurfaceClass.fromPersisted(props.getProperty(prefix + "surface"));
    z.depthBias = parseDouble(props.getProperty(prefix + "depthBias"), 0);
    z.responseScale = parseDouble(props.getProperty(prefix + "responseScale"), 100);
    z.overlayVisible = parseBoolean(props.getProperty(prefix + "overlayVisible"), true);
    String polyRaw = props.getProperty(prefix + "polygon", "");
    if (polyRaw != null && !polyRaw.isBlank()) {
      List<double[]> pts = new ArrayList<>();
      for (String pair : polyRaw.split(";")) {
        String[] xy = pair.split(",");
        if (xy.length == 2) {
          try {
            pts.add(new double[]{Double.parseDouble(xy[0].trim()), Double.parseDouble(xy[1].trim())});
          } catch (NumberFormatException ignored) { // reason: malformed numeric text input; caller uses fallback value
        }
        }
      }
      if (pts.size() >= 3) z.polygon = pts;
    }
    return z;
  }

  // ── B: Export tinted PNG ──

  private Image buildTintedExportImage() {
    Image source = loadImage(selectedCharacterTag());
    if (source == null) {
      status("No character image loaded.");
      return null;
    }
    CharacterRenderContext renderContext = buildCharacterRenderContext(source, previewCanvas.getWidth(), previewCanvas.getHeight());
    Image tinted = buildTintedImage(selectedCharacterTag(), source, renderContext, buildFullResolutionTintRasterSpec(source));
    if (tinted == null) {
      status("No tinted image available.");
      return null;
    }
    return tinted;
  }

  private void quickExportTintedPng() {
    Image tinted = buildTintedExportImage();
    if (tinted == null) return;
    File file = resolveQuickExportFile(buildTintPngFileName());
    if (file == null) {
      status("Choose an export folder first.");
      return;
    }
    writeTintedPng(tinted, file);
  }

  private void exportTintedPngAs() {
    Image tinted = buildTintedExportImage();
    if (tinted == null) return;
    File file = chooseSaveFile("Export Tinted PNG", "PNG Image", "*.png", buildTintPngFileName());
    if (file == null) return;
    writeTintedPng(tinted, file);
  }

  private void writeTintedPng(Image tinted, File file) {
    writeTintedPng(tinted, file, true);
  }

  private boolean writeTintedPng(Image tinted, File file, boolean reportSuccess) {
    try {
      Path parent = file.toPath().getParent();
      if (parent != null) Files.createDirectories(parent);
      BufferedImage bufferedImage = SwingFXUtils.fromFXImage(tinted, null);
      ImageIO.write(bufferedImage, "png", file);
      setConfiguredExportDirectory(file.getParentFile());
      if (reportSuccess) {
        status("Exported PNG: " + describePathRelativeToProject(file));
      }
      return true;
    } catch (Exception ex) {
      status("PNG export failed: " + ex.getMessage());
      return false;
    }
  }

  // ── C: File-based setup export/import (.tintsetup) ──

  private void quickExportSetupToFile() {
    File file = resolveQuickExportFile(buildTintSetupFileName());
    if (file == null) {
      status("Choose an export folder first.");
      return;
    }
    writeSetupFile(file, buildFullSetupText(), "setup");
  }

  private void exportSetupToFileAs() {
    File file = chooseSaveFile("Export Tint Setup", "Tint Setup", "*.tintsetup", buildTintSetupFileName());
    if (file == null) return;
    writeSetupFile(file, buildFullSetupText(), "setup");
  }

  private void quickExportStagePresetToFile() {
    File file = resolveQuickExportFile(buildStagePresetFileName());
    if (file == null) {
      status("Choose an export folder first.");
      return;
    }
    writeSetupFile(file, buildStagePresetText(), "stage preset");
  }

  private void exportStagePresetToFileAs() {
    File file = chooseSaveFile("Export Stage Preset", "Stage Preset", "*.stagepreset", buildStagePresetFileName());
    if (file == null) return;
    writeSetupFile(file, buildStagePresetText(file), "stage preset");
  }

  private void writeSetupFile(File file, String content) {
    writeSetupFile(file, content, "setup", true);
  }

  private void writeSetupFile(File file, String content, String kind) {
    writeSetupFile(file, content, kind, true);
  }

  private boolean writeSetupFile(File file, String content, String kind, boolean reportSuccess) {
    try {
      Path parent = file.toPath().getParent();
      if (parent != null) Files.createDirectories(parent);
      Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
      setConfiguredExportDirectory(file.getParentFile());
      if (reportSuccess) {
        status("Exported " + (kind == null || kind.isBlank() ? "file" : kind) + ": " + describePathRelativeToProject(file));
      }
      return true;
    } catch (Exception ex) {
      status((kind == null || kind.isBlank() ? "File" : kind.substring(0, 1).toUpperCase(Locale.ROOT) + kind.substring(1)) + " export failed: " + ex.getMessage());
      return false;
    }
  }

  private void quickExportBundle() {
    Image tinted = buildTintedExportImage();
    if (tinted == null) return;
    File pngFile = resolveQuickExportFile(buildTintPngFileName());
    File setupFile = resolveQuickExportFile(buildTintSetupFileName());
    File stageFile = resolveQuickExportFile(buildStagePresetFileName());
    if (pngFile == null || setupFile == null || stageFile == null) {
      status("Choose an export folder first.");
      return;
    }
    boolean pngOk = writeTintedPng(tinted, pngFile, false);
    boolean setupOk = writeSetupFile(setupFile, buildFullSetupText(), "setup", false);
    boolean stageOk = writeSetupFile(stageFile, buildStagePresetText(stageFile), "stage preset", false);
    if (pngOk && setupOk && stageOk) {
      status("Exported PNG + setup + stage preset to " + describePathRelativeToProject(pngFile.getParentFile()));
    } else if (pngOk || setupOk || stageOk) {
      status("Partially exported bundle. Check the target folder and previous export messages.");
    }
  }

  private File chooseSaveFile(String title, String description, String pattern, String suggestedName) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle(title);
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, pattern));
    File initial = resolveExistingExportDirectory();
    if (initial != null && initial.isDirectory()) chooser.setInitialDirectory(initial);
    chooser.setInitialFileName(suggestedName);
    return chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
  }

  private void importSetupFromFile() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Open Lighting Setup or Stage Preset");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Lighting Files", "*.tintsetup", "*.stagepreset"));
    File initial = resolveExistingExportDirectory();
    if (initial != null && initial.isDirectory()) chooser.setInitialDirectory(initial);
    File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
    if (file == null) return;
    try {
      applyPortableSetup(readSetup(file.toPath()));
      setConfiguredExportDirectory(file.getParentFile());
      sceneInfo.setText(file.getName());
      sceneInfo.setVisible(true);
      status("Loaded " + file.getName() + ".");
    } catch (IOException | IllegalArgumentException ex) {
      status("Could not open lighting setup: " + ex.getMessage());
    }
  }

  private static Properties readSetup(Path file) throws IOException {
    Properties props = new Properties();
    try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      props.load(reader);
    }
    return props;
  }

  private void applyPortableSetup(Properties props) {
    List<SceneLight> lights = new ArrayList<>();
    for (int i = 0, count = entryCount(props, "lights"); i < count; i++) {
      lights.add(loadLightFromNormalized(props, "light." + i + ".", i));
    }
    List<TintZone> zones = new ArrayList<>();
    for (int i = 0, count = entryCount(props, "zones"); i < count; i++) {
      zones.add(loadZoneFromNormalized(props, "zone." + i + ".", i));
    }
    entryCount(props, "occluders");
    List<VnStagePreset.Occluder> occluders = VnStagePresetLoader.loadOccluders(props);
    applyingState = true;
    try {
      setComboTagValue(characterTagBox, props.getProperty("character", ""));
      setComboTagValue(backgroundTagBox, props.getProperty("background", ""));
      var material = VnStagePresetLoader.loadMaterial(props);
      normalMapField.setText(material.normalMap());
      normalStrengthSlider.setValue(material.normalStrength() * 100);
      specularSlider.setValue(material.specular() * 100);
      glossSlider.setValue(material.gloss());
      tintColorPicker.setValue(parseColor(props.getProperty("color"), Color.WHITE));
      tintStrengthSlider.setValue(setupPercentage(props.getProperty("strength"), 0));
      saturationSlider.setValue(setupPercentage(props.getProperty("saturation"), 0));
      contrastSlider.setValue(setupPercentage(props.getProperty("contrast"), 0));
      bgTintColorPicker.setValue(parseColor(props.getProperty("bgTintColor", props.getProperty("bg.color")), Color.WHITE));
      bgTintStrengthSlider.setValue(setupPercentage(props.getProperty("bgTintStrength", props.getProperty("bg.strength")), 0));
      bgSaturationSlider.setValue(setupPercentage(props.getProperty("bgSaturation", props.getProperty("bg.saturation")), 0));
      bgContrastSlider.setValue(setupPercentage(props.getProperty("bgContrast", props.getProperty("bg.contrast")), 0));
      bgExposureSlider.setValue(parseDouble(props.getProperty("bgExposure"), 0) * 100);
      bgTintBlendModeBox.setValue(canonicalBlendMode(props.getProperty("bgTintBlend", props.getProperty("bg.tintBlend")), "Normal"));
      bgOverlayColorPicker.setValue(parseColor(props.getProperty("bgOverlayColor", props.getProperty("bg.overlayColor")), Color.BLACK));
      bgOverlayOpacitySlider.setValue(setupPercentage(props.getProperty("bgOverlayOpacity", props.getProperty("bg.overlayOpacity")), 0));
      bgOverlayBlendModeBox.setValue(canonicalBlendMode(props.getProperty("bgOverlayBlend", props.getProperty("bg.overlayBlend")), "Overlay"));
      zoom = clamp(parseDouble(props.getProperty("zoom"), 1), 0.1, 8.0);
      offsetX = parseDouble(props.getProperty("offsetX"), 0);
      offsetY = parseDouble(props.getProperty("offsetY"), 0);

      sceneLights.clear();
      sceneLights.addAll(lights);
      tintZones.clear();
      tintZones.addAll(zones);
      replaceSceneOccluders(occluders);
      selectedLightIndex = selectedZoneIndex = selectedOccluderIndex = -1;
    } finally {
      applyingState = false;
    }
    refreshLightList();
    refreshZoneList();
    refreshOccluderList();
    selectLight(sceneLights.isEmpty() ? -1 : 0);
    selectZone(tintZones.isEmpty() ? -1 : 0);
    selectOccluder(sceneOccluders.isEmpty() ? -1 : 0);
    refreshSceneEntitySelector(false);
    updateTintColorSwatch(tintColorPicker.getValue());
    updateBackgroundSwatches();
    invalidateTintCache();
    invalidateBackgroundTintCache();
    persistBackgroundTint(selectedBackgroundTag());
    activeZoneProfileTag = normalize(selectedCharacterTag());
    persistZones();
    persistGlobalState();
    redrawPreview();
  }

  private static int entryCount(Properties props, String key) {
    int count = Integer.parseInt(props.getProperty(key, "0").strip());
    if (count < 0 || count > props.size()) throw new IllegalArgumentException("Invalid " + key + " count");
    return count;
  }

  private static double setupPercentage(String raw, double fallback) {
    double value = parseDouble(raw, fallback);
    return Math.abs(value) <= 1 ? value * 100 : value;
  }

  /** Parse zone from normalized export format (strength/sat/contrast/feather as 0.0–1.0). */
  private static TintZone loadZoneFromNormalized(Properties props, String prefix, int index) {
    String boundsRaw = props.getProperty(prefix + "bounds", "");
    double bx = 0.25, by = 0.25, bw = 0.5, bh = 0.5;
    if (!boundsRaw.isBlank()) {
      String[] parts = boundsRaw.split(",");
      if (parts.length == 4) {
        bx = Double.parseDouble(parts[0].trim());
        by = Double.parseDouble(parts[1].trim());
        bw = Double.parseDouble(parts[2].trim());
        bh = Double.parseDouble(parts[3].trim());
      }
    }
    String name = props.getProperty(prefix + "name", "Zone " + (index + 1));
    TintZone z = new TintZone(name, bx, by, bw, bh);
    z.color = parseColor(props.getProperty(prefix + "color"), Color.web("#ff8844"));
    // D: Denormalize from 0.0–1.0 to slider ranges.
    z.strength = parseDouble(props.getProperty(prefix + "strength"), 0.5) * 100.0;
    z.saturation = parseDouble(props.getProperty(prefix + "saturation"), 0) * 100.0;
    z.contrast = parseDouble(props.getProperty(prefix + "contrast"), 0) * 100.0;
    z.feather = parseDouble(props.getProperty(prefix + "feather"), 0.15) * 100.0;
    z.rotation = parseDouble(props.getProperty(prefix + "rotation"), 0);
    String bm = props.getProperty(prefix + "blend", "Normal");
    z.blendMode = canonicalBlendMode(bm, "Normal");
    z.surfaceClass = SubjectSurfaceClass.fromPersisted(props.getProperty(prefix + "surface"));
    z.depthBias = setupPercentage(props.getProperty(prefix + "depthBias"), 0);
    z.responseScale = parseDouble(props.getProperty(prefix + "responseScale"), 1.0) * 100.0;
    z.overlayVisible = parseBoolean(
        props.getProperty(prefix + "overlayVisible", props.getProperty(prefix + "visible")),
        true
    );
    String polyRaw = props.getProperty(prefix + "polygon", "");
    if (polyRaw != null && !polyRaw.isBlank()) {
      List<double[]> pts = new ArrayList<>();
      for (String pair : polyRaw.split(";")) {
        String[] xy = pair.split(",");
        if (xy.length == 2) {
          try {
            pts.add(new double[]{Double.parseDouble(xy[0].trim()), Double.parseDouble(xy[1].trim())});
          } catch (NumberFormatException ignored) { // reason: malformed numeric text input; caller uses fallback value
        }
        }
      }
      if (pts.size() >= 3) z.polygon = pts;
    }
    return z;
  }

  private static SceneLight loadLightFromNormalized(Properties props, String prefix, int index) {
    String positionRaw = props.getProperty(prefix + "position", "");
    double sceneX = 0.5;
    double sceneY = 0.35;
    if (!positionRaw.isBlank()) {
      String[] parts = positionRaw.split(",");
      if (parts.length == 2) {
        sceneX = clamp(parseDouble(parts[0].trim(), sceneX), 0.0, 1.0);
        sceneY = clamp(parseDouble(parts[1].trim(), sceneY), 0.0, 1.0);
      }
    }
    String name = props.getProperty(prefix + "name", "Light " + (index + 1));
    SceneLight light = new SceneLight(name, sceneX, sceneY);
    String sourceRaw = props.getProperty(prefix + "source", "");
    if (!sourceRaw.isBlank()) {
      String[] parts = sourceRaw.split(",");
      if (parts.length == 2) {
        light.sourceX = clamp(parseDouble(parts[0].trim(), light.sourceX), 0.0, 1.0);
        light.sourceY = clamp(parseDouble(parts[1].trim(), light.sourceY), 0.0, 1.0);
      }
    }
    light.color = parseColor(props.getProperty(prefix + "color"), Color.web("#ffd7a8"));
    double intensity = parseDouble(props.getProperty(prefix + "intensity"), 0.42);
    if (intensity <= 1.0) intensity *= 100.0;
    light.intensity = clamp(intensity, 0.0, 100.0);
    double radius = parseDouble(props.getProperty(prefix + "radius"), DEFAULT_LIGHT_RADIUS);
    if (radius > 1.0) radius /= 100.0;
    light.radius = clamp(radius, 0.05, 0.80);
    double softness = parseDouble(props.getProperty(prefix + "softness"), 0.55);
    if (softness <= 1.0) softness *= 100.0;
    light.softness = clamp(softness, 0.0, 100.0);
    double silhouette = parseDouble(props.getProperty(prefix + "silhouette"), DEFAULT_LIGHT_SILHOUETTE / 100.0);
    if (silhouette <= 1.0) silhouette *= 100.0;
    light.silhouette = clamp(silhouette, 0.0, 100.0);
    light.shape = SceneLightShape.fromPersisted(props.getProperty(prefix + "shape"));
    light.layer = SceneLightLayer.fromPersisted(props.getProperty(prefix + "layer"));
    light.blend = LightBlend.from(props.getProperty(prefix + "blend"));
    light.falloff = LightFalloff.from(props.getProperty(prefix + "falloff"));
    light.castsShadows = parseBoolean(props.getProperty(prefix + "castsShadows"), true);
    light.shadowQuality = ShadowQuality.from(props.getProperty(prefix + "shadowQuality"));
    light.beamAngle = clamp(parseDouble(props.getProperty(prefix + "beamAngle"), 0), 0, 175);
    light.areaAspect = clamp(parseDouble(props.getProperty(prefix + "areaAspect"), 0), 0, 1);
    light.groundProjection = VnStagePresetLoader.loadGroundProjection(props, prefix);
    light.muted = parseBoolean(props.getProperty(prefix + "muted"), false);
    light.locked = parseBoolean(props.getProperty(prefix + "locked"), false);
    light.solo = parseBoolean(props.getProperty(prefix + "solo"), false);
    light.group = props.getProperty(prefix + "group", "");
    String polygonRaw = props.getProperty(prefix + "polygon", "");
    if (!polygonRaw.isBlank()) {
      List<double[]> pts = new ArrayList<>();
      for (String pair : polygonRaw.split(";")) {
        String[] xy = pair.split(",");
        if (xy.length == 2) {
          pts.add(new double[]{
              clamp(parseDouble(xy[0].trim(), 0.5), 0.0, 1.0),
              clamp(parseDouble(xy[1].trim(), 0.5), 0.0, 1.0)
          });
        }
      }
      if (pts.size() >= 3) {
        light.polygon = pts;
        light.shape = SceneLightShape.POLYGON;
      }
    }
    return light;
  }

  /** Build the full setup text (same format for clipboard and file export). D: Uses normalized values. */
  private String buildStagePresetText() {
    File target = resolveQuickExportFile(buildStagePresetFileName());
    return buildStagePresetText(target);
  }

  private String buildStagePresetText(File targetFile) {
    String id = currentStagePresetId();
    String fileName = targetFile != null ? targetFile.getName() : buildStagePresetFileName();
    String usagePath = targetFile != null ? describePathRelativeToProject(targetFile) : buildStagePresetUsagePath(fileName);
    if ("Project root".equals(usagePath)) usagePath = fileName;
    return buildStagePresetText(id, fileName, usagePath, false);
  }

  private String buildStagePresetText(String id, String fileName, String usagePath, boolean sceneBound) {
    String usagePathToken = quoteVnsTokenIfNeeded(usagePath);
    StringBuilder out = new StringBuilder();
    out.append("# JVN Stage Preset\n");
    out.append("# Exported by Scene Lighting Studio.\n");
    out.append("# VNS handoff:\n");
    out.append("# @stagepreset ").append(id).append(' ').append(usagePathToken).append('\n');
    out.append("# [stage ").append(id).append("]\n");
    out.append("jvn.stagePreset.schema=3\n");
    if (sceneBound) out.append("jvn.stagePreset.useSceneBackground=true\n");
    out.append("jvn.stagePreset.id=").append(id).append('\n');
    out.append("jvn.stagePreset.file=").append(fileName == null ? "" : fileName).append('\n');
    out.append("jvn.stagePreset.vnsDeclaration=@stagepreset ").append(id).append(' ').append(usagePathToken).append('\n');
    out.append("jvn.stagePreset.vnsCommand=[stage ").append(id).append("]\n");
    out.append("jvn.stagePreset.lightCount=").append(sceneLights.size()).append('\n');
    out.append("jvn.stagePreset.occluderCount=").append(sceneOccluders.size()).append('\n');
    out.append("jvn.stagePreset.responseZoneCount=").append(tintZones.size()).append('\n');
    out.append('\n');
    out.append(buildFullSetupText());
    return out.toString();
  }

  private static String quoteVnsTokenIfNeeded(String raw) {
    String value = normalize(raw);
    if (value.isBlank()) return "\"\"";
    if (value.matches("[A-Za-z0-9_./:@+-]+")) return value;
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  /** Build the full setup text (same format for clipboard and file export). D: Uses normalized values. */
  private String buildFullSetupText() {
    StringBuilder out = new StringBuilder();
    out.append("# JVN Scene Lighting Studio setup\n");
    out.append("character=").append(selectedCharacterTag()).append('\n');
    out.append("background=").append(selectedBackgroundTag()).append('\n');
    out.append("color=").append(colorToHex(tintColorPicker.getValue())).append('\n');
    out.append("strength=").append(formatNormalized(tintStrengthSlider.getValue() / 100.0)).append('\n');
    out.append("saturation=").append(formatNormalized(saturationSlider.getValue() / 100.0)).append('\n');
    out.append("contrast=").append(formatNormalized(contrastSlider.getValue() / 100.0)).append('\n');
    out.append("bgTintColor=").append(colorToHex(bgTintColorPicker.getValue())).append('\n');
    out.append("bgTintStrength=").append(formatNormalized(bgTintStrengthSlider.getValue() / 100.0)).append('\n');
    out.append("bgSaturation=").append(formatNormalized(bgSaturationSlider.getValue() / 100.0)).append('\n');
    out.append("bgContrast=").append(formatNormalized(bgContrastSlider.getValue() / 100.0)).append('\n');
    out.append("bgExposure=").append(formatNormalized(bgExposureSlider.getValue() / 100)).append('\n');
    out.append("bgTintBlend=").append(canonicalBlendMode(bgTintBlendModeBox.getValue(), "Normal")).append('\n');
    out.append("bgOverlayColor=").append(colorToHex(bgOverlayColorPicker.getValue())).append('\n');
    out.append("bgOverlayOpacity=").append(formatNormalized(bgOverlayOpacitySlider.getValue() / 100.0)).append('\n');
    out.append("bgOverlayBlend=").append(canonicalBlendMode(bgOverlayBlendModeBox.getValue(), "Overlay")).append('\n');
    out.append("material.normalMap=").append(escapeSetupValue(normalMapField.getText())).append('\n');
    out.append("material.normalStrength=").append(formatNormalized(normalStrengthSlider.getValue() / 100)).append('\n');
    out.append("material.specular=").append(formatNormalized(specularSlider.getValue() / 100)).append('\n');
    out.append("material.gloss=").append(formatNormalized(glossSlider.getValue())).append('\n');
    PresetTagEntry subject = sceneSubjectLayers.getOrDefault(selectedCharacterTag(), presetByTag.get(selectedCharacterTag()));
    if (subject != null) {
      out.append("material.layers=").append(subject.layerTags().size()).append('\n');
      for (int i = 0; i < subject.layerTags().size(); i++) {
        out.append("material.layer.").append(i).append('=').append(escapeSetupValue(subject.layerTags().get(i))).append('\n');
      }
    }
    out.append("zoom=").append(formatNormalized(zoom)).append('\n');
    out.append("offsetX=").append(formatNormalized(offsetX)).append('\n');
    out.append("offsetY=").append(formatNormalized(offsetY)).append('\n');
    if (!sceneLights.isEmpty()) {
      out.append("lights=").append(sceneLights.size()).append('\n');
      for (int i = 0; i < sceneLights.size(); i++) {
        SceneLight light = sceneLights.get(i);
        String p = "light." + i + ".";
        out.append(p).append("name=").append(escapeSetupValue(light.name)).append('\n');
        out.append(p).append("position=").append(formatNormalized(light.sceneX)).append(",")
            .append(formatNormalized(light.sceneY)).append('\n');
        out.append(p).append("source=").append(formatNormalized(light.sourceX)).append(",")
            .append(formatNormalized(light.sourceY)).append('\n');
        out.append(p).append("color=").append(colorToHex(light.color)).append('\n');
        out.append(p).append("intensity=").append(formatNormalized(light.intensity / 100.0)).append('\n');
        out.append(p).append("radius=").append(formatNormalized(light.radius)).append('\n');
        out.append(p).append("softness=").append(formatNormalized(light.softness / 100.0)).append('\n');
        out.append(p).append("silhouette=").append(formatNormalized(light.silhouette / 100.0)).append('\n');
        out.append(p).append("shape=").append((light.shape == null ? SceneLightShape.RADIAL : light.shape).persistedValue()).append('\n');
        out.append(p).append("layer=").append((light.layer == null ? SceneLightLayer.CHARACTER : light.layer).persistedValue()).append('\n');
        out.append(p).append("blend=").append(light.blend.persisted()).append('\n');
        out.append(p).append("falloff=").append(light.falloff.persisted()).append('\n');
        out.append(p).append("castsShadows=").append(light.castsShadows).append('\n');
        out.append(p).append("shadowQuality=").append(light.shadowQuality.persisted()).append('\n');
        out.append(p).append("beamAngle=").append(formatNormalized(light.beamAngle)).append('\n');
        out.append(p).append("areaAspect=").append(formatNormalized(light.areaAspect)).append('\n');
        if (light.groundProjection != null) {
          out.append(p).append("ground=").append(groundProjectionString(light.groundProjection, 1)).append('\n');
          out.append(p).append("floorLight=").append(formatNormalized(light.groundProjection.floorLight())).append('\n');
          out.append(p).append("floorExposure=").append(formatNormalized(light.groundProjection.floorExposure())).append('\n');
        }
        out.append(p).append("muted=").append(light.muted).append('\n');
        out.append(p).append("locked=").append(light.locked).append('\n');
        out.append(p).append("solo=").append(light.solo).append('\n');
        out.append(p).append("group=").append(escapeSetupValue(light.group)).append('\n');
        if (light.isPolygon()) {
          StringBuilder polyStr = new StringBuilder();
          for (int pi = 0; pi < light.polygon.size(); pi++) {
            if (pi > 0) polyStr.append(";");
            polyStr.append(formatNormalized(light.polygon.get(pi)[0])).append(",")
                .append(formatNormalized(light.polygon.get(pi)[1]));
          }
          out.append(p).append("polygon=").append(polyStr).append('\n');
        }
      }
    }
    if (!tintZones.isEmpty()) {
      out.append("zones=").append(tintZones.size()).append('\n');
      for (int i = 0; i < tintZones.size(); i++) {
        TintZone tz = tintZones.get(i);
        String p = "zone." + i + ".";
        out.append(p).append("name=").append(escapeSetupValue(tz.name)).append('\n');
        out.append(p).append("bounds=").append(formatNormalized(tz.boundsX)).append(",")
            .append(formatNormalized(tz.boundsY)).append(",")
            .append(formatNormalized(tz.boundsW)).append(",")
            .append(formatNormalized(tz.boundsH)).append('\n');
        out.append(p).append("color=").append(colorToHex(tz.color)).append('\n');
        out.append(p).append("strength=").append(formatNormalized(tz.strength / 100.0)).append('\n');
        out.append(p).append("saturation=").append(formatNormalized(tz.saturation / 100.0)).append('\n');
        out.append(p).append("contrast=").append(formatNormalized(tz.contrast / 100.0)).append('\n');
        out.append(p).append("feather=").append(formatNormalized(tz.feather / 100.0)).append('\n');
        out.append(p).append("rotation=").append(formatNormalized(tz.rotation)).append('\n');
        out.append(p).append("blend=").append(canonicalBlendMode(tz.blendMode, "Normal")).append('\n');
        out.append(p).append("visible=").append(tz.overlayVisible).append('\n');
        out.append(p).append("surface=").append((tz.surfaceClass == null ? SubjectSurfaceClass.DEFAULT : tz.surfaceClass).persistedValue()).append('\n');
        out.append(p).append("depthBias=").append(formatNormalized(tz.depthBias / 100.0)).append('\n');
        out.append(p).append("responseScale=").append(formatNormalized(tz.responseScale / 100.0)).append('\n');
        if (tz.isPolygon()) {
          StringBuilder polyStr = new StringBuilder();
          for (int pi = 0; pi < tz.polygon.size(); pi++) {
            if (pi > 0) polyStr.append(";");
            polyStr.append(formatNormalized(tz.polygon.get(pi)[0])).append(",")
                .append(formatNormalized(tz.polygon.get(pi)[1]));
          }
          out.append(p).append("polygon=").append(polyStr).append('\n');
        }
      }
    }
    if (!sceneOccluders.isEmpty()) {
      out.append("occluders=").append(sceneOccluders.size()).append('\n');
      for (int i = 0; i < sceneOccluders.size(); i++) {
        SceneOccluder occ = sceneOccluders.get(i);
        String p = "occluder." + i + ".";
        out.append(p).append("name=").append(escapeSetupValue(occ.name)).append('\n');
        out.append(p).append("opacity=").append(formatNormalized(occ.opacity / 100.0)).append('\n');
        out.append(p).append("softness=").append(formatNormalized(occ.softness / 100.0)).append('\n');
        out.append(p).append("enabled=").append(occ.enabled).append('\n');
        out.append(p).append("shape=").append(occ.shape.name().toLowerCase(Locale.ROOT)).append('\n');
        out.append(p).append("contactY=").append(formatNormalized(occ.contactY)).append('\n');
        out.append(p).append("heightScale=").append(formatNormalized(occ.heightScale)).append('\n');
        out.append(p).append("footprintDepth=").append(formatNormalized(occ.footprintDepth)).append('\n');
        out.append(p).append("roundFootprint=").append(occ.roundFootprint).append('\n');
        out.append(p).append("shadowFade=").append(formatNormalized(occ.shadowFade)).append('\n');
        if (occ.shadowFraming != null) out.append(p).append("shadowFraming=").append(framingString(occ.shadowFraming, 1)).append('\n');
        if (!occ.floorContacts.isEmpty()) out.append(p).append("floorContacts=")
            .append(floorContactsString(occ.floorContacts, 1)).append('\n');
        if (occ.surfaceTone != null) {
          out.append(p).append("surfaceExposure=").append(formatNormalized(occ.surfaceTone.exposure())).append('\n');
          out.append(p).append("surfaceContrast=").append(formatNormalized(occ.surfaceTone.contrast())).append('\n');
        }
        if (occ.bounds != null) out.append(p).append("bounds=").append(boundsString(occ.bounds, 1)).append('\n');
        if (occ.isPolygon()) {
          StringBuilder polyStr = new StringBuilder();
          for (int pi = 0; pi < occ.polygon.size(); pi++) {
            if (pi > 0) polyStr.append(";");
            polyStr.append(formatNormalized(occ.polygon.get(pi)[0])).append(",")
                .append(formatNormalized(occ.polygon.get(pi)[1]));
          }
          out.append(p).append("polygon=").append(polyStr).append('\n');
        }
      }
    }
    return out.toString();
  }

  private static String escapeSetupValue(String value) {
    return Objects.toString(value, "").replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n");
  }

  private Button actionButton(String text, javafx.scene.layout.Region icon, String tooltip, Runnable action) {
    Button button = new Button(text, icon);
    button.setContentDisplay(ContentDisplay.LEFT);
    button.setGraphicTextGap(6);
    button.setMinHeight(28);
    button.setMaxWidth(Double.MAX_VALUE);
    button.setFocusTraversable(false);
    if (tooltip != null && !tooltip.isBlank()) button.setTooltip(new Tooltip(tooltip));
    button.setOnAction(e -> {
      if (action != null) action.run();
    });
    return button;
  }
}
