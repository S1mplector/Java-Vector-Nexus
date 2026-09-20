package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import com.jvn.core.animation.SceneAccessor;
import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineRegistry;
import com.jvn.core.animation.TimelineRunner;
import com.jvn.core.vn.VnEyeFocusProfile;
import com.jvn.core.vn.VnEyeFocusProfileStore;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.stage.VnStagePresetLoader;
import com.jvn.editor.ui.CssIcon;
import com.jvn.editor.ui.CompositionGuideOverlay;
import com.jvn.editor.ui.EditorTheme;
import com.jvn.editor.ui.LayeredCharacterProjectCatalog;
import com.jvn.editor.ui.ProjectViewportSpec;
import com.jvn.editor.ui.PuppeteerLauncherPanel;
import com.jvn.editor.ui.RefreshIcon;
import com.jvn.editor.ui.SidebarToolHelp;
import com.jvn.scripting.jes.runtime.JesScene2D;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

public class PuppeteerWindow extends Stage {
    static final String WINDOW_ICON_RESOURCE = "/com/jvn/editor/images/jvn_puppeteer_icon.png";
    private static final StringConverter<PropertyType> PROPERTY_TYPE_LABEL_CONVERTER =
        new StringConverter<>() {
            @Override
            public String toString(PropertyType property) {
                return property == null ? "" : property.getDisplayName();
            }

            @Override
            public PropertyType fromString(String value) {
                if (value == null || value.isBlank()) return PropertyType.X;
                for (PropertyType property : PropertyType.values()) {
                    if (property.getDisplayName().equalsIgnoreCase(value.trim())
                        || property.name().equalsIgnoreCase(value.trim())
                        || property.getCode().equalsIgnoreCase(value.trim())) {
                        return property;
                    }
                }
                return PropertyType.X;
            }
        };
    private static final double LEFT_LIBRARY_WORKING_WIDTH = 360.0;
    private static final double FLOATING_DOCKER_SNAP_DISTANCE = 24.0;
    private static final double FLOATING_DOCKER_SNAP_ALIGNMENT_DISTANCE = 28.0;
    private static final double FLOATING_DOCKER_SNAP_RANGE_PADDING = 32.0;
    private static final double FLOATING_DOCKER_SNAP_LINK_TOLERANCE = 6.0;
    private static final double FLOATING_DOCKER_SNAP_OVERLAP_RATIO = 0.22;
    private static final double EDGE_BAR_HANDLE_THICKNESS = 8.0;
    private static final double EDGE_BAR_HANDLE_LENGTH = 78.0;
    private static final double EDGE_BAR_PANEL_MIN_WIDTH = 220.0;
    private static final double EDGE_BAR_PANEL_MIN_HEIGHT = 180.0;
    private static final double EDGE_BAR_PANEL_WIDTH = 286.0;
    private static final double EDGE_BAR_PANEL_HEIGHT = 420.0;
    private static final List<PropertyType> GROUP_PROPERTY_CHOICES = List.of(
        PropertyType.X,
        PropertyType.Y,
        PropertyType.Z,
        PropertyType.PIVOT_X,
        PropertyType.PIVOT_Y,
        PropertyType.ROTATION,
        PropertyType.SCALE_X,
        PropertyType.SCALE_Y,
        PropertyType.MIRROR_X,
        PropertyType.ALPHA,
        PropertyType.VISIBILITY,
        PropertyType.MATRIX_MXX,
        PropertyType.MATRIX_MXY,
        PropertyType.MATRIX_MYX,
        PropertyType.MATRIX_MYY,
        PropertyType.MATRIX_TX,
        PropertyType.MATRIX_TY,
        PropertyType.BLUR
    );
    private static final List<PropertyType> ENTITY_MATRIX_PROPERTY_CHOICES = List.of(
        PropertyType.MATRIX_MXX,
        PropertyType.MATRIX_MXY,
        PropertyType.MATRIX_MYX,
        PropertyType.MATRIX_MYY,
        PropertyType.MATRIX_TX,
        PropertyType.MATRIX_TY,
        PropertyType.BLUR,
        PropertyType.BRIGHTNESS
    );
    private static final List<String> ENTITY_DEDICATED_CUSTOM_KEYS = List.of(
        "matrix.mxx", "matrix.mxy", "matrix.myx", "matrix.myy", "matrix.tx", "matrix.ty",
        "effect.blur", "effect.brightness", "effect.exposure",
        "color.m00", "color.m01", "color.m02", "color.m03", "color.m04",
        "color.m10", "color.m11", "color.m12", "color.m13", "color.m14",
        "color.m20", "color.m21", "color.m22", "color.m23", "color.m24",
        "color.m30", "color.m31", "color.m32", "color.m33", "color.m34"
    );
    private static final List<String> CAMERA_DEDICATED_CUSTOM_KEYS = List.of(
        "dof.focus", "dof.strength", "dof.maxBlur"
    );
    public final AnimationProject project;
    public JesScene2D scene;

    private final PuppeteerExpressionOverride expressionOverride = new PuppeteerExpressionOverride();
    private List<PuppeteerExpressionOverride.SpriteSnapshot> expressionPreviewSnapshot = List.of();
    private String expressionPreviewTarget;
    private String expressionPreviewExpression;

    private final EntitySelector entitySelector;
    private ExpressionPreviewPanel expressionPreviewPanel;
    public final TimelinePanel timelinePanel;
    public final KeyframeEditor keyframeEditor;
    private AnchorEditor anchorEditor;
    private ConstraintEditor constraintEditor;
    public final AnimationPreview animationPreview;
    public final CodePreviewPane codePreview;

    final Button btnPlay;
    final Button btnPause;
    final Button btnStop;
    final Button btnRewind;
    final Button btnUndo;
    final Button btnRedo;
    private final TextField tfDuration;
    public final ToggleButton cbLoop;
    private final Label lblTime;
    private ComboBox<PropertyType> cbProperty;
    private ToggleButton cbSnap;
    private TextField tfSnapMs;
    ToggleButton cbOrbitTool;
    private ToggleButton cbOrbitAlign;
    private ToggleButton cbRuntimePreview;
    private ToggleButton cbViewportStabilize;
    private ToggleButton cbAutoKey;
    private ToggleButton cbSnapGrid;
    private ToggleButton cbSnapEntity;
    private ToggleButton cbCompactExport;
    private ComboBox<String> cbSpeed;
    private ComboBox<String> cbWheelMode;

    AnimationTimer playbackTimer;
    long lastNanos = 0;
    private static final long PLAYBACK_TIMELINE_REFRESH_INTERVAL_NS = 33_333_333L;
    private static final long PLAYBACK_CHROME_REFRESH_INTERVAL_NS = 100_000_000L;
    private final PlaybackRefreshGate playbackTimelineRefreshGate =
        new PlaybackRefreshGate(PLAYBACK_TIMELINE_REFRESH_INTERVAL_NS);
    private final PlaybackRefreshGate playbackChromeRefreshGate =
        new PlaybackRefreshGate(PLAYBACK_CHROME_REFRESH_INTERVAL_NS);
    private double playbackSpeed = 1.0;
    private boolean autoKeyEnabled = false;
    private boolean runtimeParityPreview = false;
    private boolean viewportStabilizationEnabled = false;

    public final PuppeteerCommand.Stack commandStack = new PuppeteerCommand.Stack();
    private final KeyframeSelectionModel selectionModel;
    Consumer<String> onCopyCode;
    public final TextField tfTimelineName;
    private Runnable onSyncSnapshotRequested;
    public final Map<String, CollapsibleToolbarCluster> toolbarClusters = new LinkedHashMap<>();
    private AnimatedToolbarPane toolbarPane;
    private HBox toolbarCommandBar;
    private Label lblToolbarCommandSummary;
    Label statusBar;
    private Label statusPlaybackLabel;
    private Label statusTimelineLabel;
    private Label statusSelectionLabel;
    private Label statusSceneLabel;
    private Label statusComplexityLabel;
    private Label statusModeLabel;
    private Label statusSaveLabel;
    private Label statusViewportLabel;
    private Label statusProjectLabel;
    private Label statusExportLabel;
    private HBox statusPlaybackSegment;
    private HBox statusComplexitySegment;
    private HBox statusSaveSegment;
    private HBox statusModeSegment;
    private HBox statusViewportSegment;
    private HBox statusExportSegment;
    private Label viewportInfoLabel;
    private Button btnSidebarPreviewLayout;
    private BorderPane previewPane;
    private StackPane previewViewportHost;
    private CompositionGuideOverlay compositionGuideOverlay;
    private Button btnPreviewFullscreen;
    private Button btnPreviewBack;
    private MediaPlayer audioPreviewPlayer;
    private Label lblSidebarSelectionTarget;
    private Label lblSidebarSelectionScope;
    private Label lblSidebarSelectionProperty;
    private Label lblSidebarSelectionPlayhead;
    private Label lblSidebarSelectionCount;
    private VBox sidebarAdvancedUnavailableCard;
    private VBox sidebarMatrixCard;
    private VBox sidebarColorMatrixCard;
    private VBox sidebarCameraDofCard;
    private VBox sidebarCustomChannelsCard;
    private Label lblSidebarAdvancedHint;
    private ComboBox<String> cbSidebarCustomPropertyKey;
    private TextField tfSidebarCustomPropertyValue;
    private final TextField[] sidebarMatrixFields = new TextField[8];
    private final TextField[] sidebarColorMatrixFields = new TextField[20];
    private final TextField[] sidebarCameraDofFields = new TextField[3];
    private Label lblSidebarSceneTracks;
    private Label lblSidebarSceneGroups;
    private Label lblSidebarSceneDuration;
    private Label lblSidebarSceneViewport;
    private Label lblSidebarSceneCamera;
    private Label lblSidebarSceneCodePane;
    private Label lblSidebarSceneAnchors;
    private Label lblSidebarSceneStage;
    private Button btnSidebarAddKeyframe;
    private Button btnSidebarAddExpressionKeyframe;
    private Button btnSidebarFocusSelection;
    private Button btnSidebarClearSelection;
    private Button btnSidebarCodePane;
    private Stage assetImporterWindow;
    private AssetPickerPanel assetImporterPanel;
    private SplitPane topWorkspaceSplit;
    private SplitPane bottomWorkspaceSplit;
    private SplitPane workspaceContentSplit;
    private SplitPane mainWorkspaceSplit;
    private SplitPane rootWorkspaceSplit;
    private SplitPane previewFocusSplit;
    private StackPane workspaceModeHost;
    private final Map<String, DockSlot> dockSlots = new LinkedHashMap<>();
    private final Map<String, DockItem> dockItems = new LinkedHashMap<>();
    private final Map<String, DockItem> toolbarDockItems = new LinkedHashMap<>();
    private final Map<SplitPane, String> dockGroupIds = new LinkedHashMap<>();
    private final Map<String, SplitPane> dockGroupsById = new LinkedHashMap<>();
    private final Map<String, FloatingDocker> floatingToolbarDockers = new LinkedHashMap<>();
    private final Map<String, Set<String>> floatingDockerSnapLinks = new LinkedHashMap<>();
    private final Set<String> edgeBarDockItemIds = new LinkedHashSet<>();
    private final Set<String> hiddenDockItemIds = new LinkedHashSet<>();
    private Pane floatingToolbarLayer;
    private EdgeBar edgeBar;
    private boolean edgeBarEnabled;
    private FloatingEdge edgeBarEdge = FloatingEdge.RIGHT;
    private double edgeBarOffsetRatio = 0.5;
    private double edgeBarPreferredWidth = EDGE_BAR_PANEL_WIDTH;
    private double edgeBarPreferredHeight = EDGE_BAR_PANEL_HEIGHT;
    private boolean floatingLayerDropCaptureEnabled;
    private int dynamicDockSlotCounter = 1;
    private DockSlot previewFocusPreviewReturnSlot;
    private DockSlot previewFocusTimelineReturnSlot;
    private DockItem previewFocusPreviewItem;
    private DockItem previewFocusTimelineItem;
    private final Map<DockSlot, DockItem> previewFocusReturnActiveItems = new LinkedHashMap<>();
    private boolean applyingDockLayoutPrefs;
    public boolean dirty = false;
    private boolean compactExport = false;
    private boolean exportNestedBlocks = true;
    public boolean previewStaged = false;
    private boolean dirtyBeforePreviewStage = false;
    private AnimationProject previewBaselineProject;
    private TransformInteractionState activeTransformInteraction;
    private CameraInteractionState activeCameraInteraction;
    public final ActionEditorDialogOverlay overlayDialog = new ActionEditorDialogOverlay();
    private final Map<String, String> launchCharacterImagePaths = new LinkedHashMap<>();
    private final Map<String, List<PuppeteerLauncherPanel.CharacterLayerEntry>> launchCharacterPresetLayers = new LinkedHashMap<>();
    private final Map<String, List<ExpressionLayerSpec>> projectCharacterPresetLayers = new LinkedHashMap<>();
    private final Map<String, String> launchBackgroundPaths = new LinkedHashMap<>();
    private final Map<String, String> launchAudioPaths = new LinkedHashMap<>();
    final List<List<com.jvn.editor.ui.actioneditor.TimelinePanel.ClipboardEntry>> clipboardHistory = new java.util.ArrayList<>();
    static final int MAX_CLIPBOARD_HISTORY = 10;
    private final Map<String, Boolean> sceneBaselineVisibility = new LinkedHashMap<>();
    private final Map<String, Map<String, Double>> sceneBaselineCustomProperties = new LinkedHashMap<>();
    private final Map<String, Double> sceneBaselineCameraCustomProperties = new LinkedHashMap<>();
    private final Map<String, String> sceneBaselineImagePaths = new LinkedHashMap<>();
    private boolean bypassCloseConfirmation = false;
    public boolean codePaneVisible = true;
    private double codePaneDividerPosition = DEFAULT_CODE_PANE_DIVIDER_POSITION;
    private boolean previewFocusMode = false;
    private double previewFocusDividerPosition = DEFAULT_PREVIEW_FOCUS_DIVIDER_POSITION;
    private double topWorkspaceDividerPosition = DEFAULT_TOP_WORKSPACE_DIVIDER_POSITION;
    private double bottomWorkspaceDividerPosition = DEFAULT_BOTTOM_WORKSPACE_DIVIDER_POSITION;
    private double workspaceContentDividerPosition = DEFAULT_CONTENT_DIVIDER_POSITION;
    private double toolbarDividerPosition = DEFAULT_TOOLBAR_DIVIDER_POSITION;
    private boolean topToolbarVisible = true;
    private double toolbarDragStartSceneY = 0.0;
    private double toolbarDragStartDivider = DEFAULT_TOOLBAR_DIVIDER_POSITION;
    private double uiScale = 1.0;

    private static final double MOVE_INTERACTION_EPSILON = 0.01;
    private static final double TOOLBAR_COLLAPSED_DIVIDER_POSITION = 0.035;
    private static final double TOOLBAR_MIN_VISIBLE_DIVIDER_POSITION = 0.075;
    private static final double TOOLBAR_MAX_DIVIDER_POSITION = 0.55;
    private static final double DEFAULT_TOOLBAR_DIVIDER_POSITION = 0.16;
    private static final double DEFAULT_TOP_WORKSPACE_DIVIDER_POSITION = 0.2;
    private static final double DEFAULT_BOTTOM_WORKSPACE_DIVIDER_POSITION = 0.28;
    private static final double DEFAULT_CONTENT_DIVIDER_POSITION = 0.4;
    private static final double DEFAULT_CODE_PANE_DIVIDER_POSITION = 0.78;
    private static final double DEFAULT_PREVIEW_FOCUS_DIVIDER_POSITION = 0.72;
    private static final double MIN_UI_SCALE = 0.80;
    private static final double MAX_UI_SCALE = 1.60;
    private static final double BASE_UI_FONT_SIZE = 12.0;
    private static final Insets TOOLBAR_PADDING_DYNAMIC = new Insets(8, 10, 8, 10);
    private static final Insets TOOLBAR_PADDING_COMPACT = new Insets(1, 4, 1, 4);
    private static final Insets TOOLBAR_COMMAND_BAR_PADDING_DYNAMIC = new Insets(6, 10, 0, 10);
    private static final Insets TOOLBAR_COMMAND_BAR_PADDING_COMPACT = new Insets(1, 4, 0, 4);
    private static final double TOOLBAR_SHELL_SPACING_DYNAMIC = 6.0;
    private static final double TOOLBAR_SHELL_SPACING_COMPACT = 1.0;
    private static final String PROP_TOOLBAR_BASE_SPACING = "puppeteerToolbarBaseSpacing";
    private static final String PROP_TOOLBAR_BASE_PREF_WIDTH = "puppeteerToolbarBasePrefWidth";
    private static final String PROP_TOOLBAR_BASE_PREF_HEIGHT = "puppeteerToolbarBasePrefHeight";
    private static final String PROP_TOOLBAR_BASE_ICON_WIDTH = "puppeteerToolbarBaseIconWidth";
    private static final String PROP_TOOLBAR_BASE_ICON_HEIGHT = "puppeteerToolbarBaseIconHeight";
    private static final String DOCK_DRAG_SLOT_PREFIX = "jvn-puppeteer-dock-slot:";
    private static final String DOCK_DRAG_TOOLBAR_PREFIX = "jvn-puppeteer-toolbar:";
    private static final String EMPTY_DOCK_VALUE = "-";
    private static final String DOCK_SLOT_ITEM_DELIMITER = "|";
    private static final String WORKSPACE_PRESET_NAME_SUFFIX = ".name";
    private static final String WORKSPACE_PRESET_PAYLOAD_SUFFIX = ".payload";
    private static final PropertyType[] TRANSFORM_INTERACTION_PROPERTIES = {
        PropertyType.X,
        PropertyType.Y,
        PropertyType.PIVOT_X,
        PropertyType.PIVOT_Y,
        PropertyType.ROTATION,
        PropertyType.MATRIX_MXX,
        PropertyType.MATRIX_MXY,
        PropertyType.MATRIX_MYX,
        PropertyType.MATRIX_MYY,
        PropertyType.MATRIX_TX,
        PropertyType.MATRIX_TY,
        PropertyType.MIRROR_X
    };
    private static final PropertyType[] CAMERA_INTERACTION_PROPERTIES = {
        PropertyType.CAMERA_X,
        PropertyType.CAMERA_Y,
        PropertyType.CAMERA_ZOOM
    };

    enum PuppeteerErrorType {
        VALIDATION("Validation"),
        PROJECT_CONTEXT("Project context"),
        CODE_PARSE("Code parse"),
        EXPORT("Export"),
        SAVE_IO("Save / disk"),
        REGISTRATION("Runtime registration"),
        PREVIEW("Preview"),
        ASSET("Asset"),
        AUDIO("Audio"),
        CLIP("Animation clip"),
        CLIPBOARD("Clipboard"),
        TIMELINE("Timeline"),
        KEYFRAME("Keyframe"),
        PROPERTY("Property"),
        ENTITY("Entity / Track"),
        CAMERA("Camera"),
        CUE("Cue"),
        INTERPOLATION("Interpolation"),
        IMPORT("Import"),
        STATE("State"),
        PLAYBACK("Playback"),
        UNKNOWN("Unknown");

        private final String label;

        PuppeteerErrorType(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private record TransformInteractionState(
        String entityName,
        double timeMs,
        Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> beforeStates
    ) {}

    private record CameraInteractionState(
        double timeMs,
        Map<PropertyType, PuppeteerCommand.PropertySnapshot> beforeStates
    ) {}

    private record GroupPivotAdjustment(double x, double y, boolean translate) {}

    private record ExpressionLayerSpec(String layerId, String path) {}
    private record ExpressionLayerCandidate(String name, com.jvn.core.scene2d.Sprite2D sprite) {}

    public PuppeteerWindow() {
        this(new AnimationProject());
    }

    public PuppeteerWindow(AnimationProject project) {
        this.project = project != null ? project : new AnimationProject();

        setTitle("Puppeteer - " + this.project.getName());
        loadWindowIcon().ifPresent(icon -> getIcons().add(icon));
        setWidth(1400);
        setHeight(900);

        entitySelector = new EntitySelector();
        expressionPreviewPanel = new ExpressionPreviewPanel();
        timelinePanel = new TimelinePanel(this.project, commandStack);
        selectionModel = timelinePanel.getSelectionModel();
        keyframeEditor = new KeyframeEditor();
        keyframeEditor.setTimelineDurationMs(this.project.getTotalDurationMs());
        animationPreview = new AnimationPreview();
        animationPreview.setProject(this.project);
        animationPreview.setOrbitAnchors(this.project.getOrbitAnchorsView());
        animationPreview.setOrbitAnchorSources(this.project.getOrbitAnchorSourcesView());
        animationPreview.setOrbitAnchorSourceOffsets(this.project.getOrbitAnchorSourceOffsetsView());
        animationPreview.setOnOrbitAnchorChanged((entityName, anchor) -> {
            if (entityName == null || anchor == null || anchor.length < 2) return;
            this.project.setOrbitAnchor(entityName, anchor[0], anchor[1]);
        });
        animationPreview.setOnOrbitAnchorSourceChanged(this.project::setOrbitAnchorSource);
        animationPreview.setOnOrbitAnchorSourceOffsetChanged((entityName, offset) -> {
            if (entityName == null || entityName.isBlank()) return;
            if (offset == null || offset.length < 2) {
                this.project.clearOrbitAnchorSourceOffset(entityName);
                return;
            }
            if (!Double.isFinite(offset[0]) || !Double.isFinite(offset[1])) return;
            this.project.setOrbitAnchorSourceOffset(entityName, offset[0], offset[1]);
        });
        animationPreview.setOnOrbitAnchorRemoved(this.project::removeOrbitAnchor);
        animationPreview.setOnCameraStateChanged(state -> {
            if (state != null && state.length >= 3) {
                keyframeEditor.setCameraState(state[0], state[1], state[2]);
                refreshSidebarTabs();
            }
        });
        animationPreview.setOnCameraInteractionStarted(() -> {
            double time = this.project.getPlayheadMs();
            EntityTrack track = resolveRuntimeCameraTrack(true);
            activeCameraInteraction = new CameraInteractionState(
                time,
                captureTrackSnapshots(track, time, CAMERA_INTERACTION_PROPERTIES, null)
            );
        });
        animationPreview.setOnCameraMoved(state -> {
            if (state == null || state.length < 3) return;
            EntityTrack track = resolveRuntimeCameraTrack(true);
            if (track == null) return;
            double time = activeCameraInteraction != null ? activeCameraInteraction.timeMs() : this.project.getPlayheadMs();
            track.upsertKeyframe(PropertyType.CAMERA_X, new Keyframe(time, state[0]));
            track.upsertKeyframe(PropertyType.CAMERA_Y, new Keyframe(time, state[1]));
            track.sortKeyframes(PropertyType.CAMERA_X);
            track.sortKeyframes(PropertyType.CAMERA_Y);
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });
        animationPreview.setOnCameraInteractionFinished(state -> {
            CameraInteractionState interaction = activeCameraInteraction;
            activeCameraInteraction = null;
            if (interaction == null || state == null || state.length < 3) return;
            EntityTrack track = resolveRuntimeCameraTrack(true);
            if (track == null) return;
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> afterStates =
                captureTrackSnapshots(track, interaction.timeMs(), CAMERA_INTERACTION_PROPERTIES, null);
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> beforeStates = interaction.beforeStates();
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> changedBefore = new EnumMap<>(PropertyType.class);
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> changedAfter = new EnumMap<>(PropertyType.class);
            for (PropertyType property : CAMERA_INTERACTION_PROPERTIES) {
                PuppeteerCommand.PropertySnapshot before = beforeStates.get(property);
                PuppeteerCommand.PropertySnapshot after = afterStates.get(property);
                if (!transformChanged(property, before, after)) continue;
                changedBefore.put(property, before != null ? before : new PuppeteerCommand.PropertySnapshot(false, 0.0));
                changedAfter.put(property, after != null ? after : new PuppeteerCommand.PropertySnapshot(false, 0.0));
            }
            if (changedBefore.isEmpty()) {
                restoreTrackSnapshots(track, interaction.timeMs(), beforeStates);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
                return;
            }
            commandStack.execute(PuppeteerCommand.applyPropertiesAtTime(
                track,
                interaction.timeMs(),
                changedBefore,
                changedAfter,
                "Edit runtime camera"
            ));
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });
        animationPreview.setOnAssetDropped(payload -> {
            if (payload == null || !payload.isValid()) return;
            addAssetToScene(payload.relativePath(), payload.suggestedName(), PuppeteerAssetPlacementRole.PROP);
        });
        codePreview = new CodePreviewPane();
        keyframeEditor.setCameraState(animationPreview.getCamera().getX(), animationPreview.getCamera().getY(), animationPreview.getCamera().getZoom());

        timelinePanel.setOnTargetSelectionChanged((name, isGroup) -> {
            clearExpressionPreview();
            refreshExpressionPreviewPanel(name, isGroup);
            keyframeEditor.setSelectionContext(
                selectionLabel(name, isGroup),
                isGroup,
                timelinePanel.isRuntimeCameraSelected());
            animationPreview.setRuntimeCameraSelected(timelinePanel.isRuntimeCameraSelected());
            if (timelinePanel.isRuntimeCameraSelected()) {
                entitySelector.selectEntity(null);
                animationPreview.clearSelection();
                anchorEditor.setSelectedEntityName(null, false);
                if (constraintEditor != null) constraintEditor.selectEntity(null);
            } else if (isGroup) {
                entitySelector.selectGroup(name);
                animationPreview.selectGroup(name);
                anchorEditor.setSelectedEntityName(name, true);
                if (constraintEditor != null) constraintEditor.selectEntity(null);
            } else {
                entitySelector.selectEntity(name);
                animationPreview.selectEntity(name);
                anchorEditor.setSelectedEntityName(name, false);
                if (constraintEditor != null) constraintEditor.selectEntity(name);
            }
            PropertyType selectedProp = timelinePanel.getSelectedProperty();
            if (selectedProp != null && cbProperty != null && cbProperty.getValue() != selectedProp) {
                cbProperty.setValue(selectedProp);
            }
            refreshPropertyPickerChoices();
            refreshSidebarTabs();
            updateStatusBar();
            animationPreview.render();
        });

        entitySelector.setOnSelectionChanged((name, isGroup) -> {
            clearExpressionPreview();
            refreshExpressionPreviewPanel(name, isGroup);
            timelinePanel.setSelectedTarget(name, isGroup);
            if (!isGroup) {
                animationPreview.selectEntities(entitySelector.getSelectedEntityNames(), name);
            }
            anchorEditor.setSelectedEntityName(name, isGroup);
            if (constraintEditor != null) constraintEditor.selectEntity(isGroup ? null : name);
            refreshPropertyPickerChoices();
            updateStatusBar();
        });

        expressionPreviewPanel.setOnExpressionChosen(expression -> {
            String target = timelinePanel != null ? timelinePanel.getSelectedEntity() : null;
            if (target == null || target.isBlank()) return;
            previewExpression(target, expression);
            expressionPreviewPanel.setPreviewingState(expression);
            animationPreview.setExpressionPreviewIndicator(target, expression, true);
        });

        expressionPreviewPanel.setOnClear(() -> {
            String target = timelinePanel != null ? timelinePanel.getSelectedEntity() : null;
            clearExpressionPreview();
            expressionPreviewPanel.setPreviewingState(null);
            if (target != null && !target.isBlank()) {
                animationPreview.setExpressionPreviewIndicator(
                    target, preferredExpressionForTarget(target), false);
            }
        });

        expressionPreviewPanel.setOnApply(() -> {
            String target = getExpressionPreviewEntityName();
            String expression = getExpressionPreviewName();
            if (target == null || expression == null) return;
            showExpressionKeyframeDialog(target, expression);
        });

        entitySelector.setOnCreateGroup(groupName -> {
            this.project.getOrCreateGroup(groupName);
            entitySelector.refresh(this.project);
            entitySelector.selectGroup(groupName);
            timelinePanel.setSelectedTarget(groupName, true);
            timelinePanel.refresh();
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });

        entitySelector.setOnAddSelectionToGroup((name, selectionIsGroup, groupName) -> {
            if (selectionIsGroup) {
                this.project.addGroupToGroup(name, groupName);
            } else {
                this.project.addEntityToGroup(name, groupName);
            }
            entitySelector.refresh(this.project);
            timelinePanel.refresh();
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });

        entitySelector.setOnDeleteSelection(this::deleteSceneSelection);

        entitySelector.setOnRenameGroup((currentName, nextName) -> {
            if (!this.project.renameGroup(currentName, nextName)) return;
            entitySelector.refresh(this.project);
            entitySelector.selectGroup(nextName);
            timelinePanel.setSelectedTarget(nextName, true);
            timelinePanel.refresh();
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });

        entitySelector.setOnEntityLayerDelta((entityName, delta) -> {
            EntityTrack track = this.project.getTrack(entityName);
            if (track == null) return;
            track.setLayerOrder(track.getLayerOrder() + delta);
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });

        entitySelector.setOnGroupLayerDelta((groupName, delta) -> {
            EntityGroup group = this.project.getGroup(groupName);
            if (group == null) return;
            group.setLayerOrder(group.getLayerOrder() + delta);
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });

        entitySelector.setOnEntityVisibilityChanged((entityName, visible) -> {
            EntityTrack track = this.project.getTrack(entityName);
            if (track == null) return;
            track.setVisible(visible);
            entitySelector.refresh(this.project);
            entitySelector.selectEntity(entityName);
            updatePreview();
        });

        entitySelector.setOnEntityLockChanged((entityName, locked) -> {
            EntityTrack track = this.project.getTrack(entityName);
            if (track == null) return;
            track.setLocked(locked);
            entitySelector.refresh(this.project);
            entitySelector.selectEntity(entityName);
        });

        entitySelector.setOnGroupLockChanged((groupName, locked) -> {
            EntityGroup group = this.project.getGroup(groupName);
            if (group == null) return;
            group.setLocked(locked);
            entitySelector.refresh(this.project);
            entitySelector.selectGroup(groupName);
        });

        entitySelector.setOnSelectionSoloChanged((name, isGroup, soloed) -> {
            // When solo is toggled, force-select the soloed target and update the timeline.
            if (soloed) {
                timelinePanel.setSelectedTarget(name, isGroup);
                if (isGroup) {
                    animationPreview.selectGroup(name);
                    anchorEditor.setSelectedEntityName(name, true);
                } else {
                    animationPreview.selectEntity(name);
                    anchorEditor.setSelectedEntityName(name, false);
                }
            }
            timelinePanel.refresh();
            refreshPropertyPickerChoices();
        });

        entitySelector.setOnGroupResetRequested(groupName -> {
            EntityGroup group = this.project.getGroup(groupName);
            if (group == null) return;
            
            double time = this.project.getPlayheadMs();
            java.util.List<PuppeteerCommand> commands = new ArrayList<>();
            collectResetCommands(group, time, commands);
            
            if (!commands.isEmpty()) {
                commandStack.execute(PuppeteerCommand.composite("Reset Group Transforms", commands));
                refreshExportPreviewAndMarkDirty();
                updatePreview();
                if (timelinePanel != null) timelinePanel.refresh();
            }
        });

        timelinePanel.setOnEventCueSelected(this::showEventCueManagerDialog);
        timelinePanel.setOnExpressionKeyframeRequested((entityName, timeMs) -> {
            timelinePanel.setSelectedTarget(entityName, false);
            project.setPlayheadMs(timeMs);
            updateTimeLabel();
            updatePreview();
            showExpressionKeyframeDialog();
        });

        timelinePanel.setOnKeyframeSelected(kf -> {
            if (timelinePanel.getSelectionCount() > 1) {
                keyframeEditor.setSelection(new ArrayList<>(timelinePanel.getSelectedKeyframes()), timelinePanel.getSelectedProperty());
                keyframeEditor.setAdjacentKeyframes(null);
            } else {
                keyframeEditor.setKeyframe(kf, timelinePanel.getSelectedProperty());
                // --- A) Feed adjacent keyframes for ghost curve overlay ---
                keyframeEditor.setAdjacentKeyframes(findAdjacentKeyframes(kf, timelinePanel.getSelectedProperty()));
            }
            PropertyType selectedProp = timelinePanel.getSelectedProperty();
            if (selectedProp != null && cbProperty.getValue() != selectedProp) {
                cbProperty.setValue(selectedProp);
            }
            refreshSidebarTabs();
            updateStatusBar();
        });

        timelinePanel.setOnPlayheadChanged(time -> {
            clearExpressionPreview();
            this.project.setPlayheadMs(time);
            updateTimeLabel();
            updatePreview();
            refreshSidebarTabs();
            updateStatusBar();
        });
        timelinePanel.setOnEdited(() -> {
            syncDurationUi();
            refreshExportPreviewAndMarkDirty();
        });

        keyframeEditor.setOnKeyframeChanged(() -> {
            if (timelinePanel.getSelectionCount() > 1) {
                for (EntityTrack track : project.getTracks()) {
                    for (PropertyType property : PropertyType.values()) {
                        track.sortKeyframes(property);
                    }
                }
            } else {
                PropertyType property = keyframeEditor.getCurrentProperty();
                if (property != null) {
                    EntityTrack track = selectedTrackForEditing(false);
                    if (track != null) track.sortKeyframes(property);
                }
            }
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        keyframeEditor.setOnDeleteRequested(() -> {
            timelinePanel.deleteSelectedKeyframe();
            refreshExportPreviewAndMarkDirty();
        });

        animationPreview.setOnEntitySelected(name -> {
            timelinePanel.setSelectedTarget(name, false);
            anchorEditor.setSelectedEntityName(name, false);
        });

        animationPreview.setOnEntityMoved((name, pos) -> {
            if (name == null || pos == null || pos.length < 2) return;
            if (!Double.isFinite(pos[0]) || !Double.isFinite(pos[1])) return;
            EntityTrack track = resolveAnimatedTrack(name, true);
            if (track == null) return;
            double time = this.project.getPlayheadMs();
            boolean liveDragSource = false;
            if (activeTransformInteraction != null && name.equals(activeTransformInteraction.entityName())) {
                time = activeTransformInteraction.timeMs();
                liveDragSource = true;
            }
            double previousX = valueAtTimeOrFallback(track, PropertyType.X, time, pos[0]);
            double previousY = valueAtTimeOrFallback(track, PropertyType.Y, time, pos[1]);
            track.upsertKeyframe(PropertyType.X, new Keyframe(time, pos[0]));
            track.upsertKeyframe(PropertyType.Y, new Keyframe(time, pos[1]));
            if (liveDragSource) {
                double dx = pos[0] - previousX;
                double dy = pos[1] - previousY;
                if (Math.abs(dx) > MOVE_INTERACTION_EPSILON || Math.abs(dy) > MOVE_INTERACTION_EPSILON) {
                    applyAnchorFollowerDelta(name, time, dx, dy, new LinkedHashSet<>());
                }
            }
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        animationPreview.setOnEntityMoveInteractionStarted((name, pos) -> {
            if (name == null || name.isBlank() || pos == null || pos.length < 2) {
                activeTransformInteraction = null;
                return;
            }
            if (!Double.isFinite(pos[0]) || !Double.isFinite(pos[1])) {
                activeTransformInteraction = null;
                return;
            }
            double time = this.project.getPlayheadMs();
            activeTransformInteraction = new TransformInteractionState(
                name,
                time,
                captureTransformSnapshots(name, time)
            );
        });

        animationPreview.setOnEntityMoveInteractionFinished((name, pos) -> {
            TransformInteractionState interaction = activeTransformInteraction;
            activeTransformInteraction = null;
            if (interaction == null || name == null || pos == null || pos.length < 2) return;
            if (!name.equals(interaction.entityName())) return;
            if (!Double.isFinite(pos[0]) || !Double.isFinite(pos[1])) return;

            double time = interaction.timeMs();
            Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> beforeStates = interaction.beforeStates();
            Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> afterStates = captureTransformSnapshots(name, time);
            List<PuppeteerCommand> commands = buildTransformInteractionCommands(time, beforeStates, afterStates);

            if (commands.isEmpty()) {
                restoreTransformSnapshots(time, beforeStates);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
                return;
            }

            restoreTransformSnapshots(time, beforeStates);
            commandStack.execute(PuppeteerCommand.composite("Edit transform", commands));
            timelinePanel.refresh();
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });

        animationPreview.setOnEntityPivotChanged((name, pivot) -> {
            if (name == null || pivot == null || pivot.length < 2) return;
            EntityTrack track = resolveAnimatedTrack(name, true);
            if (track == null) return;
            double time = this.project.getPlayheadMs();
            if (activeTransformInteraction != null && name.equals(activeTransformInteraction.entityName())) {
                time = activeTransformInteraction.timeMs();
            }
            if (project.getGroup(name) != null) {
                upsertGroupPivotPreservingPose(name, track, time, pivot[0], pivot[1]);
            } else {
                track.upsertKeyframe(PropertyType.PIVOT_X, new Keyframe(time, pivot[0]));
                track.upsertKeyframe(PropertyType.PIVOT_Y, new Keyframe(time, pivot[1]));
            }
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        keyframeEditor.setOnPivotPresetApplied((px, py) -> {
            String name = timelinePanel.getSelectedEntity();
            if (name == null || name.isBlank()) return;
            EntityTrack track = selectedTrackForEditing(true);
            if (track == null) return;
            double time = this.project.getPlayheadMs();
            commandStack.execute(PuppeteerCommand.composite(
                "Apply pivot preset",
                List.of(
                    PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_X, time, px),
                    PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_Y, time, py)
                )
            ));
            timelinePanel.refresh();
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });

        animationPreview.setOnEntityRotationChanged((name, rotationDeg) -> {
            if (name == null || rotationDeg == null || !Double.isFinite(rotationDeg)) return;
            boolean groupTarget = this.project.getGroup(name) != null;
            EntityTrack track = resolveAnimatedTrack(name, true);
            if (track == null) return;
            double time = this.project.getPlayheadMs();
            if (activeTransformInteraction != null && name.equals(activeTransformInteraction.entityName())) {
                time = activeTransformInteraction.timeMs();
            }
            track.upsertKeyframe(PropertyType.ROTATION, new Keyframe(time, rotationDeg));
            timelinePanel.refresh();
            if (groupTarget) {
                updatePreview();
            }
            refreshExportPreviewAndMarkDirty();
        });

        animationPreview.setOnEntityScaleChanged((name, scale) -> {
            if (name == null || scale == null || scale.length < 2) return;
            double scaleX = scale[0], scaleY = scale[1];
            if (!Double.isFinite(scaleX) || !Double.isFinite(scaleY)) return;
            EntityTrack track = this.project.getOrCreateTrack(name);
            double time = this.project.getPlayheadMs();
            if (activeTransformInteraction != null && name.equals(activeTransformInteraction.entityName())) {
                time = activeTransformInteraction.timeMs();
            }
            track.upsertKeyframe(PropertyType.SCALE_X, new Keyframe(time, scaleX));
            track.upsertKeyframe(PropertyType.SCALE_Y, new Keyframe(time, scaleY));
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        animationPreview.setOnEntityMatrixChanged((name, matrixValues) -> {
            if (name == null || name.isBlank() || matrixValues == null || matrixValues.length < 6) return;
            EntityTrack track = this.project.getOrCreateTrack(name);
            double time = this.project.getPlayheadMs();
            if (activeTransformInteraction != null && name.equals(activeTransformInteraction.entityName())) {
                time = activeTransformInteraction.timeMs();
            }
            PropertyType[] properties = {
                PropertyType.MATRIX_MXX,
                PropertyType.MATRIX_MXY,
                PropertyType.MATRIX_MYX,
                PropertyType.MATRIX_MYY,
                PropertyType.MATRIX_TX,
                PropertyType.MATRIX_TY
            };
            for (int i = 0; i < properties.length; i++) {
                double value = matrixValues[i];
                if (!Double.isFinite(value)) continue;
                track.upsertKeyframe(properties[i], new Keyframe(time, value));
                track.sortKeyframes(properties[i]);
            }
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        codePreview.setOnCopy(() -> {
            copyExportedCodeToClipboard();
        });

        codePreview.setOnRegenerate(() -> {
            if (previewStaged) {
                discardStagedPreview();
                return;
            }
            requestRefreshGeneratedCode();
        });

        // --- Transport controls ---
        btnRewind = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.REWIND), "Rewind (Home)");
        btnPlay = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.PLAY), "Play (Space)");
        btnPause = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.PAUSE), "Pause (Space)");
        btnStop = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.STOP), "Stop");
        btnUndo = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.UNDO), "Undo (Ctrl/Cmd+Z)");
        btnRedo = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.REDO), "Redo (Ctrl/Cmd+Shift+Z)");

        btnPlay.setOnAction(e -> play());
        btnPause.setOnAction(e -> pause());
        btnStop.setOnAction(e -> stop());
        btnRewind.setOnAction(e -> rewind());
        btnUndo.setOnAction(e -> executeUndo());
        btnRedo.setOnAction(e -> executeRedo());
        refreshUndoRedoControls();

        lblTime = new Label("0 ms");
        lblTime.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 12px; -fx-font-weight: bold; -fx-min-width: 72; -fx-alignment: center;");

        HBox transportBox = new HBox(4, btnRewind, btnPlay, btnPause, btnStop, makeSpacer(6), lblTime);
        transportBox.setAlignment(Pos.CENTER_LEFT);
        HBox historyBox = new HBox(4, btnUndo, btnRedo);
        historyBox.setAlignment(Pos.CENTER_LEFT);

        // --- Duration controls ---
        tfDuration = new TextField(String.valueOf((int) this.project.getTotalDurationMs()));
        tfDuration.setPrefWidth(64);
        tfDuration.setStyle(STYLE_TEXT_FIELD);
        tfDuration.setOnAction(e -> {
            try {
                double dur = Double.parseDouble(tfDuration.getText());
                if (!Double.isFinite(dur) || dur < 0.0) {
                    throw new NumberFormatException("Duration must be a finite non-negative number.");
                }
                this.project.setTotalDurationMs(dur);
                keyframeEditor.setTimelineDurationMs(this.project.getTotalDurationMs());
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            } catch (NumberFormatException ex) {
                syncDurationUi();
                showOverlayError(
                    PuppeteerErrorType.VALIDATION,
                    "Invalid Duration",
                    "Puppeteer could not apply the timeline duration.",
                    "Duration must be a finite non-negative number of milliseconds.",
                    ex
                );
            }
        });

        Button btnFitDuration = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.FIT_DURATION), "Fit duration to content");
        btnFitDuration.setOnAction(e -> {
            this.project.fitDurationToContent();
            tfDuration.setText(String.valueOf((int) this.project.getTotalDurationMs()));
            keyframeEditor.setTimelineDurationMs(this.project.getTotalDurationMs());
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        cbLoop = makeToolbarIconToggle(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.LOOP), "Loop timeline playback");
        cbLoop.setSelected(this.project.isLooping());
        cbLoop.setOnAction(e -> {
            this.project.setLooping(cbLoop.isSelected());
            refreshExportPreviewAndMarkDirty();
            updateStatusBar();
        });

        Button btnLoopIn = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.LOOP_IN), "Set loop IN at playhead");
        btnLoopIn.setOnAction(e -> {
            double inMs = project.getPlayheadMs();
            double outMs = project.hasLoopRegion() ? project.getLoopEndMs() : project.getTotalDurationMs();
            if (inMs < outMs) {
                project.setLoopRegion(inMs, outMs);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnLoopOut = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.LOOP_OUT), "Set loop OUT at playhead");
        btnLoopOut.setOnAction(e -> {
            double outMs = project.getPlayheadMs();
            double inMs = project.hasLoopRegion() ? project.getLoopStartMs() : 0;
            if (outMs > inMs) {
                project.setLoopRegion(inMs, outMs);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnLoopClear = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.CLEAR), "Clear loop region");
        btnLoopClear.setOnAction(e -> {
            project.clearLoopRegion();
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        tfDuration.setTooltip(new Tooltip("Timeline duration (ms)"));
        HBox durationBox = new HBox(4, tfDuration, btnFitDuration, cbLoop, btnLoopIn, btnLoopOut, btnLoopClear);
        durationBox.setAlignment(Pos.CENTER_LEFT);

        // --- Presets ---
        Button presetButton = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.PRESET), "Apply animation preset to selected entity");
        presetButton.setOnAction(e -> showPresetMenuOverlay());

        // --- Property target + snapping ---
        cbProperty = new ComboBox<>();
        cbProperty.getItems().setAll(PropertyType.values());
        cbProperty.setConverter(PROPERTY_TYPE_LABEL_CONVERTER);
        cbProperty.setButtonCell(propertyTypeListCell());
        cbProperty.setCellFactory(list -> propertyTypeListCell());
        cbProperty.setValue(PropertyType.X);
        cbProperty.setStyle(STYLE_TEXT_FIELD);
        cbProperty.setPrefWidth(158);
        cbProperty.setTooltip(new Tooltip("Active property track for add-keyframe and keyboard nudging"));
        cbProperty.setOnAction(e -> {
            timelinePanel.setSelectedProperty(cbProperty.getValue());
            PropertyType effective = timelinePanel.getSelectedProperty();
            if (effective != null && cbProperty.getValue() != effective) {
                cbProperty.setValue(effective);
            }
            animationPreview.render();
        });
        timelinePanel.setSelectedProperty(PropertyType.X);
        refreshPropertyPickerChoices();

        HBox propertyBox = new HBox(4, cbProperty);
        propertyBox.setAlignment(Pos.CENTER_LEFT);

        Button btnCopyKeyframes = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.COPY), "Copy selected keyframes (Ctrl/Cmd+Alt+C)");
        btnCopyKeyframes.setOnAction(e -> copySelectedKeyframesToClipboard());
        Button btnPasteKeyframes = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.PASTE), "Paste keyframes at playhead (Ctrl/Cmd+Alt+V)");
        btnPasteKeyframes.setOnAction(e -> pasteCopiedKeyframesAtPlayhead());
        Button btnClipboardHistory = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.HISTORY), "Clipboard history");
        btnClipboardHistory.setOnAction(e -> showClipboardHistoryPopup());
        Button btnDuplicateKeyframes = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.DUPLICATE), "Duplicate selected keyframes by snap step (Ctrl/Cmd+Alt+D)");
        btnDuplicateKeyframes.setOnAction(e -> duplicateSelectedKeyframesBySnapStep());
        Button btnSaveClip = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.SAVE_CLIP), "Save selection as reusable clip");
        btnSaveClip.setOnAction(e -> saveSelectionAsClip());
        Button btnLoadClip = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.LOAD_CLIP), "Load and apply a saved clip at playhead");
        btnLoadClip.setOnAction(e -> loadAndApplyClip());

        Button slotButton = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.CHARACTER_SLOT), "Place selected entity at a VN character slot");
        slotButton.setOnAction(e -> showSlotMenuOverlay());

        Button btnBatchKeyframe = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.ADD_ALL), "Add keyframe for ALL entities at playhead (batch)");
        btnBatchKeyframe.setOnAction(e -> {
            PropertyType prop = cbProperty.getValue();
            if (prop == null) prop = PropertyType.X;
            timelinePanel.addKeyframeForAllEntities(project.getPlayheadMs(), prop);
            refreshExportPreviewAndMarkDirty();
        });

        Button btnZoomFit = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.ZOOM_FIT), "Zoom timeline to fit content");
        btnZoomFit.setOnAction(e -> timelinePanel.zoomToFit());
        Button btnFocusSelection = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.FOCUS), "Zoom timeline to the current selection or active track");
        btnFocusSelection.setOnAction(e -> timelinePanel.zoomToSelection());
        Button btnPrevKeyframe = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.PREVIOUS), "Jump playhead to previous keyframe (Page Up)");
        btnPrevKeyframe.setOnAction(e -> timelinePanel.jumpPlayheadToPreviousKeyframe());
        Button btnNextKeyframe = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.NEXT), "Jump playhead to next keyframe (Page Down)");
        btnNextKeyframe.setOnAction(e -> timelinePanel.jumpPlayheadToNextKeyframe());

        ToggleButton cbRipple = makeToolbarIconToggle(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.RIPPLE), "Ripple-retime: shift following keys when nudging a selection");
        cbRipple.setSelected(timelinePanel.isRippleRetimeEnabled());
        cbRipple.setOnAction(e -> timelinePanel.setRippleRetimeEnabled(cbRipple.isSelected()));

        Button btnDistributeKeys = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.DISTRIBUTE), "Distribute selected keyframes evenly across their current range");
        btnDistributeKeys.setOnAction(e -> {
            if (timelinePanel.distributeSelectedKeyframes()) {
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnReverseKeys = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.REVERSE), "Reverse selected keyframes within their current range");
        btnReverseKeys.setOnAction(e -> {
            if (timelinePanel.reverseSelectedKeyframes()) {
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnStretchKeys = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.STRETCH), "Stretch selected keyframes 25% wider");
        btnStretchKeys.setOnAction(e -> {
            if (timelinePanel.stretchSelectedKeyframes(1.25)) {
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnCompressKeys = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.COMPRESS), "Compress selected keyframes to 80% of their current range");
        btnCompressKeys.setOnAction(e -> {
            if (timelinePanel.stretchSelectedKeyframes(0.8)) {
                refreshExportPreviewAndMarkDirty();
            }
        });

        cbCompactExport = makeToolbarIconToggle(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.COMPACT_EXPORT), "Use compact export format");
        cbCompactExport.setSelected(false);
        cbCompactExport.setOnAction(e -> setCompactExportEnabled(cbCompactExport.isSelected()));

        HBox keyframeOpsPrimaryRow = new HBox(4,
            btnCopyKeyframes,
            btnPasteKeyframes,
            btnClipboardHistory,
            btnDuplicateKeyframes,
            btnBatchKeyframe,
            btnSaveClip,
            btnLoadClip,
            slotButton,
            btnPrevKeyframe,
            btnNextKeyframe,
            btnFocusSelection,
            btnZoomFit
        );
        keyframeOpsPrimaryRow.setAlignment(Pos.CENTER_LEFT);

        HBox keyframeOpsSecondaryRow = new HBox(4,
            btnDistributeKeys,
            btnReverseKeys,
            btnStretchKeys,
            btnCompressKeys,
            cbRipple,
            cbCompactExport
        );
        keyframeOpsSecondaryRow.setAlignment(Pos.CENTER_LEFT);

        cbSnap = makeToolbarIconToggle(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.SNAP), "Enable snapping");
        cbSnap.setSelected(timelinePanel.isSnapEnabled());
        cbSnap.setOnAction(e -> setTimelineSnapEnabled(cbSnap.isSelected()));

        tfSnapMs = new TextField("50");
        tfSnapMs.setPrefWidth(56);
        tfSnapMs.setStyle(STYLE_TEXT_FIELD);
        tfSnapMs.setTooltip(new Tooltip("Snap step in milliseconds"));
        tfSnapMs.setOnAction(e -> applySnapStepFromField());
        tfSnapMs.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) applySnapStepFromField();
        });
        tfSnapMs.setTooltip(new Tooltip("Snap step in milliseconds"));

        HBox snapBox = new HBox(4, cbSnap, tfSnapMs);
        snapBox.setAlignment(Pos.CENTER_LEFT);

        // --- Playback speed ---
        cbSpeed = new ComboBox<>();
        cbSpeed.getItems().addAll("0.25x", "0.5x", "1x", "2x", "4x");
        cbSpeed.setValue("1x");
        cbSpeed.setStyle(STYLE_TEXT_FIELD);
        cbSpeed.setPrefWidth(64);
        cbSpeed.setTooltip(new Tooltip("Playback speed"));
        cbSpeed.setOnAction(e -> {
            String val = cbSpeed.getValue();
            if (val != null) {
                try { setPlaybackSpeed(Double.parseDouble(val.replace("x", ""))); }
                catch (NumberFormatException ignored) { setPlaybackSpeed(1.0); }
            }
        });

        cbWheelMode = new ComboBox<>();
        cbWheelMode.getItems().addAll("Wheel: View", "Wheel: Camera");
        cbWheelMode.setValue("Wheel: View");
        cbWheelMode.setStyle(STYLE_TEXT_FIELD);
        cbWheelMode.setPrefWidth(118);
        cbWheelMode.setTooltip(new Tooltip("Choose what mouse wheel controls in preview"));
        animationPreview.setScrollZoomMode(AnimationPreview.ScrollZoomMode.VIEW);
        cbWheelMode.setOnAction(e -> {
            String mode = cbWheelMode.getValue();
            setScrollZoomMode("Wheel: Camera".equals(mode)
                ? AnimationPreview.ScrollZoomMode.CAMERA
                : AnimationPreview.ScrollZoomMode.VIEW);
        });

        cbRuntimePreview = makeToolbarIconToggle(
            puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.RUNTIME_PREVIEW),
            "Runtime data preview: render through TimelineData/TimelineRunner"
        );
        cbRuntimePreview.setSelected(runtimeParityPreview);
        cbRuntimePreview.setOnAction(e -> setRuntimeParityPreviewEnabled(cbRuntimePreview.isSelected()));

        cbViewportStabilize = makeToolbarIconToggle(
            puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.VIEWPORT_STABILIZE),
            "Stabilize preview viewport: lock framing so animated bounds cannot shake the view"
        );
        cbViewportStabilize.setSelected(viewportStabilizationEnabled);
        cbViewportStabilize.setOnAction(e -> setViewportStabilizationEnabled(cbViewportStabilize.isSelected()));

        // --- Auto-key toggle ---
        cbAutoKey = makeToolbarIconToggle(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.AUTO_KEY), "Auto-key: automatically insert keyframe on drag");
        cbAutoKey.setSelected(false);
        cbAutoKey.setOnAction(e -> setAutoKeyEnabled(cbAutoKey.isSelected()));
        Label lblAutoKey = new Label("Auto");
        lblAutoKey.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10px;");

        // --- Snap-to-grid / snap-to-entity toggles ---
        cbSnapGrid = makeToolbarIconToggle(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.SNAP_GRID), "Snap entities to grid when dragging");
        cbSnapGrid.setSelected(false);
        cbSnapGrid.setOnAction(e -> setPreviewSnapToGridEnabled(cbSnapGrid.isSelected()));
        cbSnapEntity = makeToolbarIconToggle(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.SNAP_ENTITY), "Snap to nearby entity positions");
        cbSnapEntity.setSelected(false);
        cbSnapEntity.setOnAction(e -> setPreviewSnapToEntityEnabled(cbSnapEntity.isSelected()));

        HBox autoKeyBox = new HBox(4, cbAutoKey, lblAutoKey);
        autoKeyBox.setAlignment(Pos.CENTER_LEFT);
        HBox previewSnapBox = new HBox(4, cbSnapGrid, cbSnapEntity, cbRuntimePreview, cbViewportStabilize, cbSpeed, cbWheelMode);
        previewSnapBox.setAlignment(Pos.CENTER_LEFT);

        cbOrbitTool = makeToolbarIconToggle(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.ORBIT), "Enable orbit-anchor tool. Shift+click preview to place anchor. Alt+Shift+click another entity to link the anchor at the exact cursor point (joint/nail).");
        cbOrbitTool.setSelected(animationPreview.isOrbitToolEnabled());
        cbOrbitTool.setOnAction(e -> animationPreview.setOrbitToolEnabled(cbOrbitTool.isSelected()));

        cbOrbitAlign = makeToolbarIconToggle(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.ORBIT_ALIGN), "When orbiting, update entity rotation to face outward.");
        cbOrbitAlign.setSelected(animationPreview.isOrbitAlignRotation());
        cbOrbitAlign.setOnAction(e -> animationPreview.setOrbitAlignRotation(cbOrbitAlign.isSelected()));

        Button btnClearAnchor = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.CLEAR), "Clear orbit anchor for selected entity");
        btnClearAnchor.setOnAction(e -> {
            animationPreview.clearOrbitAnchorForSelectedEntity();
            updatePreview();
        });

        HBox orbitBox = new HBox(4, cbOrbitTool, cbOrbitAlign, btnClearAnchor);
        orbitBox.setAlignment(Pos.CENTER_LEFT);

        // --- Help button ---
        Button btnHelp = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.HELP), "Show keyboard shortcuts");
        btnHelp.setOnAction(e -> showShortcutsOverlay());

        // --- Audio + event cues ---
        Button btnAddCue = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.ADD_AUDIO_CUE), "Add audio cue at playhead");
        btnAddCue.setOnAction(e -> showAddAudioCueDialog());
        Button btnClearCues = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.CLEAR), "Remove all timeline audio cues");
        btnClearCues.setOnAction(e -> {
            if (project.getAudioCues().isEmpty()) return;
            overlayDialog.showDialog(
                "Clear Audio Cues",
                "Remove all audio cues from this animation? This cannot be undone from the cue panel.",
                null,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.danger("Clear", () -> {
                    project.clearAudioCues();
                    timelinePanel.refresh();
                    refreshExportPreviewAndMarkDirty();
                })
            );
        });
        Button btnAddExpressionCue = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.ADD_EXPRESSION_CUE), "Add expression keyframe at playhead");
        btnAddExpressionCue.setOnAction(e -> showExpressionKeyframeDialog());
        Button btnManageEvents = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.MANAGE_EVENTS), "Manage timeline event cues");
        btnManageEvents.setOnAction(e -> showEventCueManagerDialog(null));
        Button btnClearEvents = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.CLEAR), "Remove all timeline event cues");
        btnClearEvents.setOnAction(e -> {
            if (project.getEditorEventCues().isEmpty()) return;
            overlayDialog.showDialog(
                "Clear Event Cues",
                "Remove all timeline event cues from this animation?",
                null,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.danger("Clear", () -> {
                    project.clearEditorEventCues();
                    timelinePanel.refresh();
                    updatePreview();
                    refreshExportPreviewAndMarkDirty();
                })
            );
        });
        HBox cueBox = new HBox(4, btnAddCue, btnClearCues, btnAddExpressionCue, btnManageEvents, btnClearEvents);
        cueBox.setAlignment(Pos.CENTER_LEFT);

        // --- Timeline name + Register ---
        String initialTimelineName = this.project.getName();
        if (initialTimelineName == null || initialTimelineName.isBlank()
            || "Untitled Animation".equalsIgnoreCase(initialTimelineName)) {
            initialTimelineName = "my_animation";
        }
        tfTimelineName = new TextField(initialTimelineName);
        tfTimelineName.setPrefWidth(110);
        tfTimelineName.setPromptText("timeline_name");
        tfTimelineName.setStyle(STYLE_TEXT_FIELD);
        tfTimelineName.setTooltip(new Tooltip("Name for @external jes_timeline"));

        Button btnSync = makeToolbarIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.SYNC), "Sync snapshot from VNS script");
        btnSync.setOnAction(e -> requestSyncSnapshot());

        Button btnRegister = makeToolbarSuccessIconButton(puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.REGISTER), "Register timeline for VNS interop");
        btnRegister.setOnAction(e -> requestRegisterTimeline());

        HBox nameBox = new HBox(4, btnSync, tfTimelineName, btnRegister);
        nameBox.setAlignment(Pos.CENTER_LEFT);

        // --- Apply Code to Model (text-first round-trip) ---
        codePreview.setOnApplyToModel(() -> {
            stagePreviewFromCode();
        });
        codePreview.setOnCommitPreview(this::commitStagedPreview);
        codePreview.setOnDiscardPreview(this::discardStagedPreview);

        // --- Assemble toolbar ---
        CollapsibleToolbarCluster transportCluster = registerToolbarCluster("transport", "Transport", transportBox);
        CollapsibleToolbarCluster historyCluster = registerToolbarCluster("history", "History", historyBox);
        CollapsibleToolbarCluster durationCluster = registerToolbarCluster("duration", "Timeline", durationBox);
        CollapsibleToolbarCluster presetsCluster = registerToolbarCluster("presets", "Presets", presetButton);
        CollapsibleToolbarCluster propertyCluster = registerToolbarCluster("property", "Track", propertyBox);
        CollapsibleToolbarCluster keyframesCluster = registerToolbarCluster("keyframes", "Keyframes",
            keyframeOpsPrimaryRow, keyframeOpsSecondaryRow);
        CollapsibleToolbarCluster snapCluster = registerToolbarCluster("snap", "Snap", snapBox);
        CollapsibleToolbarCluster previewCluster = registerToolbarCluster("preview", "Preview", autoKeyBox, previewSnapBox);
        CollapsibleToolbarCluster orbitCluster = registerToolbarCluster("orbit", "Orbit", orbitBox);
        CollapsibleToolbarCluster audioCluster = registerToolbarCluster("audio", "Cues", cueBox);
        CollapsibleToolbarCluster registerCluster = registerToolbarCluster("register", "Register", nameBox);

        // --- Export cluster ---
        Button btnExportGif = makeToolbarIconButton(
            puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.RECORD_GIF), "Record preview as GIF animation");
        btnExportGif.setOnAction(e -> showRecordGifDialog());
        Button btnExportCopyCode = makeToolbarIconButton(
            puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.COPY_CODE), "Copy exported JES code to clipboard");
        btnExportCopyCode.setOnAction(e -> copyExportedCodeToClipboard());
        HBox exportBox = new HBox(4, btnExportGif, btnExportCopyCode);
        exportBox.setAlignment(Pos.CENTER_LEFT);
        CollapsibleToolbarCluster exportCluster = registerToolbarCluster("export", "Export", exportBox);

        // --- Diagnostics cluster ---
        Button btnVerify = makeToolbarIconButton(
            puppeteerAeroIcon(com.jvn.editor.ui.PuppeteerAeroIcon.Kind.VERIFY), "Verify runtime timeline registration");
        btnVerify.setOnAction(e -> showRuntimeVerificationReport());
        CollapsibleToolbarCluster diagnosticsCluster = registerToolbarCluster("diagnostics", "Diagnostics", btnVerify);

        CollapsibleToolbarCluster helpCluster = registerToolbarCluster("help", "Help", btnHelp);

        toolbarPane = new AnimatedToolbarPane(8, 5);
        toolbarPane.setId("puppeteer-toolbar");
        toolbarPane.setPadding(TOOLBAR_PADDING_DYNAMIC);
        toolbarPane.setMinHeight(Region.USE_PREF_SIZE);
        toolbarPane.setMaxWidth(Double.MAX_VALUE);
        
        // Add the missing toolbar cluster lines 
        
        toolbarPane.addCluster(transportCluster);
        toolbarPane.addCluster(historyCluster);
        toolbarPane.addCluster(durationCluster);
        toolbarPane.addCluster(presetsCluster);
        toolbarPane.addCluster(propertyCluster);
        toolbarPane.addCluster(keyframesCluster);
        toolbarPane.addCluster(snapCluster);
        toolbarPane.addCluster(previewCluster);
        toolbarPane.addCluster(orbitCluster);
        toolbarPane.addCluster(audioCluster);
        toolbarPane.addCluster(registerCluster);
        toolbarPane.addCluster(exportCluster);
		toolbarPane.addCluster(diagnosticsCluster);
		toolbarPane.addCluster(helpCluster);
		
		// Build the command bar right afterwards
        toolbarCommandBar = buildToolbarCommandBar();
        setToolbarClustersExpanded(true);
        setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode.COMPACT);

        entitySelector.setMinWidth(0);

        Tab entitiesTab = new Tab("Entities", entitySelector);
        entitiesTab.setClosable(false);
        Tab selectionTab = buildSelectionTab();
        selectionTab.setClosable(false);
        Tab sceneTab = buildSceneTab();
        sceneTab.setClosable(false);
        anchorEditor = new AnchorEditor();
        anchorEditor.setProject(this.project);
        anchorEditor.setAnimationPreview(animationPreview);
        animationPreview.setOnAnchorPlacementAt(coords -> {
            if (coords == null || coords.length < 2) return;
            anchorEditor.startPendingPlacement(coords[0], coords[1]);
        });
        anchorEditor.setOnAnchorChanged(() -> {
            animationPreview.render();
            timelinePanel.refresh();
        });
        anchorEditor.setOnAnchorPlaced((entityName, anchor) -> {
            // Seed PIVOT_X/Y at t=0 when no pivot keyframes exist yet.
            // Leaf entities also get X/Y compensation to preserve visual placement.
            if (entityName == null || !anchor.isRelative()) return;
            EntityTrack track = resolveAnimatedTrack(entityName, true);
            if (track == null) return;
            if (!track.getKeyframes(PropertyType.PIVOT_X).isEmpty()
                    || !track.getKeyframes(PropertyType.PIVOT_Y).isEmpty()) return;
            if (project.getGroup(entityName) != null) {
                upsertGroupPivotPreservingPose(entityName, track, 0.0, anchor.getX(), anchor.getY());
                timelinePanel.refresh();
                updatePreview();
                refreshExportPreviewAndMarkDirty();
                return;
            }
            com.jvn.core.scene2d.Entity2D ent = scene != null ? scene.find(entityName) : null;
            double newPX = anchor.getX(), newPY = anchor.getY();
            double oldPX = ent != null ? ent.getOriginX() : 0.5;
            double oldPY = ent != null ? ent.getOriginY() : 0.5;
            double baseX = baselinePropertyValue(entityName, ent, PropertyType.X);
            double baseY = baselinePropertyValue(entityName, ent, PropertyType.Y);
            double[] comp = pivotCompensation(ent, newPX, newPY, oldPX, oldPY);
            track.upsertKeyframe(PropertyType.PIVOT_X, new Keyframe(0.0, newPX));
            track.upsertKeyframe(PropertyType.PIVOT_Y, new Keyframe(0.0, newPY));
            track.upsertKeyframe(PropertyType.X, new Keyframe(0.0, baseX + comp[0]));
            track.upsertKeyframe(PropertyType.Y, new Keyframe(0.0, baseY + comp[1]));
            timelinePanel.refresh();
            updatePreview();
        });
        anchorEditor.setOnAnchorUsedAsPivot((entityName, anchor) -> {
            // Insert PIVOT_X/Y at the current playhead time.
            // Leaf entities also get X/Y compensation to preserve visual placement.
            if (entityName == null || !anchor.isRelative()) return;
            EntityTrack track = resolveAnimatedTrack(entityName, true);
            if (track == null) return;
            double time = project.getPlayheadMs();
            double newPX = anchor.getX(), newPY = anchor.getY();
            if (project.getGroup(entityName) != null) {
                commandStack.execute(PuppeteerCommand.composite(
                    "Set group rotation pivot to anchor",
                    groupPivotPreservingCommands(entityName, track, time, newPX, newPY)
                ));
                timelinePanel.refresh();
                updatePreview();
                refreshExportPreviewAndMarkDirty();
                return;
            }
            com.jvn.core.scene2d.Entity2D ent = scene != null ? scene.find(entityName) : null;
            double oldPX = ent != null ? ent.getOriginX() : 0.5;
            double oldPY = ent != null ? ent.getOriginY() : 0.5;
            double curX  = ent != null ? ent.getX() : baselinePropertyValue(entityName, ent, PropertyType.X);
            double curY  = ent != null ? ent.getY() : baselinePropertyValue(entityName, ent, PropertyType.Y);
            double[] comp = pivotCompensation(ent, newPX, newPY, oldPX, oldPY);
            commandStack.execute(PuppeteerCommand.composite(
                "Set rotation pivot to anchor",
                List.of(
                    PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_X, time, newPX),
                    PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_Y, time, newPY),
                    PuppeteerCommand.upsertKeyframe(track, PropertyType.X, time, curX + comp[0]),
                    PuppeteerCommand.upsertKeyframe(track, PropertyType.Y, time, curY + comp[1])
                )
            ));
            timelinePanel.refresh();
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });
        Tab anchorsTab = new Tab("Anchors", anchorEditor);
        anchorsTab.setClosable(false);
        constraintEditor = new ConstraintEditor();
        constraintEditor.setProject(this.project);
        constraintEditor.setCurrentTimeSupplier(this.project::getPlayheadMs);
        constraintEditor.setOnConstraintChanged(() -> {
            timelinePanel.refresh();
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });
        TabPane leftTabs = new TabPane(entitiesTab, selectionTab, sceneTab, anchorsTab);
        leftTabs.getStyleClass().add("sidebar-tab-pane");
        leftTabs.setMinWidth(0);
        leftTabs.setMaxWidth(Double.MAX_VALUE);
        leftTabs.setTabMinWidth(56);
        leftTabs.setStyle("-fx-background-color: #1a1a1a;");

        StackPane leftTabsContent = new StackPane(leftTabs);
        leftTabsContent.setAlignment(Pos.TOP_LEFT);
        leftTabsContent.setMinWidth(0);
        leftTabsContent.setPrefWidth(LEFT_LIBRARY_WORKING_WIDTH);
        leftTabsContent.setMaxWidth(Double.MAX_VALUE);

        ScrollPane leftTabsScrollPane = new ScrollPane(leftTabsContent);
        leftTabsScrollPane.setMinWidth(0);
        leftTabsScrollPane.setPrefViewportWidth(LEFT_LIBRARY_WORKING_WIDTH);
        leftTabsScrollPane.setPrefWidth(LEFT_LIBRARY_WORKING_WIDTH);
        leftTabsScrollPane.setMaxWidth(Double.MAX_VALUE);
        leftTabsScrollPane.setMaxHeight(Double.MAX_VALUE);
        leftTabsScrollPane.setFitToWidth(true);
        leftTabsScrollPane.setFitToHeight(true);
        leftTabsScrollPane.setPannable(false);
        leftTabsScrollPane.setMinHeight(0);
        leftTabsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftTabsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftTabsScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        DockSlot entitiesSlot = createDockSlot(createDockItem("entities", "Entities", leftTabsScrollPane, false));
        keyframeEditor.setMinWidth(0);
        keyframeEditor.setMinHeight(0);
        keyframeEditor.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        final double[] collapsedWorkspaceDivider = {0.4};
        keyframeEditor.setOnCurveEditorExpandedChanged(expanded -> {
            if (workspaceContentSplit == null || workspaceContentSplit.getDividers().isEmpty()) {
                return;
            }
            if (expanded) {
                collapsedWorkspaceDivider[0] = workspaceContentSplit.getDividerPositions()[0];
                workspaceContentSplit.setDividerPositions(Math.min(collapsedWorkspaceDivider[0], 0.42));
                return;
            }
            workspaceContentSplit.setDividerPositions(collapsedWorkspaceDivider[0]);
        });
        viewportInfoLabel = new Label();
        viewportInfoLabel.setStyle("-fx-text-fill: #979797; -fx-font-size: 10px; -fx-padding: 3 8 5 8;");
        viewportInfoLabel.setTooltip(new Tooltip(
            "Preview shows full scene bounds. The red rectangle marks the runtime viewport " +
            "size from jvn.project."
        ));
        updateViewportInfoLabel();

        btnPreviewFullscreen = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.expand(), "Focus the preview in the editor workspace");
        btnPreviewFullscreen.getStyleClass().add("puppeteer-preview-overlay-button");
        btnPreviewFullscreen.getStyleClass().add("puppeteer-preview-overlay-button-idle");
        btnPreviewFullscreen.setText("Focus");
        btnPreviewFullscreen.setContentDisplay(ContentDisplay.LEFT);
        btnPreviewFullscreen.setGraphicTextGap(6);
        btnPreviewFullscreen.setMinSize(Region.USE_PREF_SIZE, 34);
        btnPreviewFullscreen.setPrefSize(Region.USE_COMPUTED_SIZE, 34);
        btnPreviewFullscreen.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        btnPreviewFullscreen.setManaged(false);
        btnPreviewFullscreen.setVisible(false);
        btnPreviewFullscreen.setOnAction(e -> enterFullscreenPreview());

        btnPreviewBack = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.arrowLeft(), "Return to the standard editor workspace");
        btnPreviewBack.getStyleClass().add("puppeteer-preview-overlay-button");
        btnPreviewBack.getStyleClass().add("puppeteer-preview-overlay-button-back");
        btnPreviewBack.setText("Back to Editor");
        btnPreviewBack.setContentDisplay(ContentDisplay.LEFT);
        btnPreviewBack.setGraphicTextGap(8);
        btnPreviewBack.setMinSize(160, 42);
        btnPreviewBack.setPrefSize(160, 42);
        btnPreviewBack.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        btnPreviewBack.setManaged(false);
        btnPreviewBack.setVisible(false);
        btnPreviewBack.setOnAction(e -> exitFullscreenPreview());

        compositionGuideOverlay = new CompositionGuideOverlay();
        ProjectViewportSpec.Dimensions guideDimensions = animationPreview.getViewportDimensions();
        compositionGuideOverlay.setVirtualResolution(guideDimensions.width(), guideDimensions.height());
        previewViewportHost = new StackPane(
            animationPreview, compositionGuideOverlay, btnPreviewBack, btnPreviewFullscreen);
        previewViewportHost.getStyleClass().add("puppeteer-preview-viewport-host");
        previewViewportHost.setMinHeight(0);
        previewViewportHost.hoverProperty().addListener((obs, wasHover, isHover) -> updatePreviewOverlayVisibility());
        // Unmanaged nodes ignore StackPane alignment, so position manually.
        // Keep fullscreen/back controls pinned to the same top-right anchor.
        btnPreviewBack.layoutXProperty().bind(
            previewViewportHost.widthProperty()
                .subtract(btnPreviewBack.widthProperty())
                .subtract(14));
        btnPreviewBack.setLayoutY(8);
        btnPreviewFullscreen.layoutXProperty().bind(
            previewViewportHost.widthProperty()
                .subtract(btnPreviewFullscreen.widthProperty())
                .subtract(14));
        btnPreviewFullscreen.setLayoutY(8);
        updatePreviewOverlayVisibility();

        previewPane = new BorderPane(previewViewportHost);
        previewPane.setMinWidth(0);
        animationPreview.setMinHeight(0);
        animationPreview.setMinWidth(0);
        previewPane.setMinHeight(0);
        timelinePanel.setMinWidth(0);
        timelinePanel.setMinHeight(0);
        previewPane.setTop(viewportInfoLabel);
        previewPane.setStyle("-fx-background-color: #121212;");
        DockSlot previewSlot = createDockSlot(createDockItem("preview", "Preview", previewPane, false));
        DockSlot expressionPreviewSlot = createDockSlot(
            createDockItem("expression-preview", "Expression Preview", expressionPreviewPanel, false));
        DockSlot keyframeSlot = createDockSlot(createDockItem("keyframes-panel", "Keyframes", keyframeEditor, false));
        DockSlot timelineSlot = createDockSlot(createDockItem("timeline-panel", "Timeline", timelinePanel, false));
        DockSlot codeSlot = createDockSlot(createDockItem("code", "Code", codePreview, false));

        floatingToolbarLayer = new FloatingDockerLayer();
        floatingToolbarLayer.getStyleClass().add("puppeteer-floating-docker-layer");
        floatingToolbarLayer.setMinSize(0, 0);
        floatingToolbarLayer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        floatingToolbarLayer.setOnDragOver(event -> {
            String payload = dockPayload(event.getDragboard());
            if (payload != null && isFloatingToolbarPayload(payload)) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        floatingToolbarLayer.setOnDragDropped(event -> {
            String payload = dockPayload(event.getDragboard());
            boolean success = false;
            if (payload != null && isFloatingToolbarPayload(payload)) {
                DockItem item = dockItemFromPayload(payload);
                if (item != null) {
                    DockSlot sourceSlot = findDockSlotContaining(item.contentNode());
                    if (sourceSlot != null) {
                        sourceSlot.removeItem(item);
                    } else {
                        CollapsibleToolbarCluster sourceCluster = item.toolbarCluster();
                        if (sourceCluster != null && toolbarDockItems.get(sourceCluster.getClusterKey()) == item) {
                            toolbarPane.removeCluster(sourceCluster);
                            toolbarDockItems.remove(sourceCluster.getClusterKey());
                            item.clearToolbarCluster();
                        } else {
                            FloatingDocker oldFloating = floatingToolbarDockers.remove(item.id());
                            if (oldFloating != null) {
                                floatingToolbarLayer.getChildren().removeAll(oldFloating, oldFloating.restoreTab());
                            }
                        }
                    }
                    createFloatingToolbarDocker(item, event.getSceneX() - 40, event.getSceneY() - 15);
                    refreshAfterDockSwap();
                    success = true;
                }
            }
            event.setDropCompleted(success);
            setFloatingLayerDropCaptureEnabled(false);
            event.consume();
        });
        edgeBar = new EdgeBar();
        floatingToolbarLayer.getChildren().add(edgeBar);
        refreshEdgeBar();

        topWorkspaceSplit = new SplitPane();
        topWorkspaceSplit.getStyleClass().add("puppeteer-split-pane");
        topWorkspaceSplit.setOrientation(Orientation.HORIZONTAL);
        topWorkspaceSplit.setMinWidth(0);
        topWorkspaceSplit.setMinHeight(0);
        topWorkspaceSplit.getItems().addAll(entitiesSlot, previewSlot, expressionPreviewSlot);
        SplitPane.setResizableWithParent(entitiesSlot, Boolean.TRUE);
        SplitPane.setResizableWithParent(previewSlot, Boolean.TRUE);
        SplitPane.setResizableWithParent(expressionPreviewSlot, Boolean.TRUE);
        registerDockSlotHome(entitiesSlot, topWorkspaceSplit, 0, false);
        registerDockSlotHome(previewSlot, topWorkspaceSplit, 1, false);
        registerDockSlotHome(expressionPreviewSlot, topWorkspaceSplit, 2, false);
        registerDockGroup("workspace-top", topWorkspaceSplit);
        topWorkspaceSplit.setDividerPositions(DEFAULT_TOP_WORKSPACE_DIVIDER_POSITION);

        bottomWorkspaceSplit = new SplitPane();
        bottomWorkspaceSplit.getStyleClass().add("puppeteer-split-pane");
        bottomWorkspaceSplit.setOrientation(Orientation.HORIZONTAL);
        bottomWorkspaceSplit.setMinWidth(0);
        bottomWorkspaceSplit.setMinHeight(0);
        bottomWorkspaceSplit.getItems().addAll(keyframeSlot, timelineSlot);
        SplitPane.setResizableWithParent(keyframeSlot, Boolean.TRUE);
        SplitPane.setResizableWithParent(timelineSlot, Boolean.TRUE);
        registerDockSlotHome(keyframeSlot, bottomWorkspaceSplit, 0, false);
        registerDockSlotHome(timelineSlot, bottomWorkspaceSplit, 1, false);
        registerDockGroup("workspace-bottom", bottomWorkspaceSplit);
        bottomWorkspaceSplit.setDividerPositions(DEFAULT_BOTTOM_WORKSPACE_DIVIDER_POSITION);

        workspaceContentSplit = new SplitPane();
        workspaceContentSplit.getStyleClass().add("puppeteer-split-pane");
        workspaceContentSplit.setOrientation(Orientation.VERTICAL);
        workspaceContentSplit.setMinWidth(0);
        workspaceContentSplit.setMinHeight(0);
        workspaceContentSplit.getItems().addAll(topWorkspaceSplit, bottomWorkspaceSplit);
        SplitPane.setResizableWithParent(topWorkspaceSplit, Boolean.TRUE);
        SplitPane.setResizableWithParent(bottomWorkspaceSplit, Boolean.TRUE);
        workspaceContentSplit.setDividerPositions(DEFAULT_CONTENT_DIVIDER_POSITION);
        collapsedWorkspaceDivider[0] = workspaceContentSplit.getDividerPositions()[0];

        mainWorkspaceSplit = new SplitPane();
        mainWorkspaceSplit.getStyleClass().add("puppeteer-split-pane");
        mainWorkspaceSplit.setOrientation(Orientation.HORIZONTAL);
        mainWorkspaceSplit.setMinWidth(0);
        mainWorkspaceSplit.setMinHeight(0);
        codePreview.setMinWidth(0);
        codePreview.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        mainWorkspaceSplit.getItems().addAll(workspaceContentSplit, codeSlot);
        SplitPane.setResizableWithParent(workspaceContentSplit, Boolean.TRUE);
        SplitPane.setResizableWithParent(codeSlot, Boolean.TRUE);
        registerDockSlotHome(codeSlot, mainWorkspaceSplit, 1, false);
        registerDockGroup("workspace-main", mainWorkspaceSplit);
        mainWorkspaceSplit.setDividerPositions(codePaneDividerPosition);

        previewFocusSplit = new SplitPane();
        previewFocusSplit.getStyleClass().add("puppeteer-split-pane");
        previewFocusSplit.setOrientation(Orientation.VERTICAL);
        previewFocusSplit.setMinWidth(0);
        previewFocusSplit.setMinHeight(0);
        previewFocusSplit.setVisible(false);
        previewFocusSplit.setManaged(false);

        workspaceModeHost = new StackPane(mainWorkspaceSplit, previewFocusSplit);
        workspaceModeHost.setMinWidth(0);
        workspaceModeHost.setMinHeight(0);

        rootWorkspaceSplit = new SplitPane();
        rootWorkspaceSplit.getStyleClass().add("puppeteer-split-pane");
        rootWorkspaceSplit.getStyleClass().add("puppeteer-root-layout-split");
        rootWorkspaceSplit.setOrientation(Orientation.VERTICAL);
        rootWorkspaceSplit.setMinWidth(0);
        rootWorkspaceSplit.setMinHeight(0);
        rootWorkspaceSplit.getItems().addAll(workspaceModeHost);
        SplitPane.setResizableWithParent(workspaceModeHost, Boolean.TRUE);
        rootWorkspaceSplit.setDividerPositions(toolbarDividerPosition);

        HBox puppeteerStatusBar = buildPuppeteerStatusBar();
        updateStatusBar();

        BorderPane root = new BorderPane();
        root.setTop(toolbarCommandBar);
        root.setCenter(rootWorkspaceSplit);
        root.setBottom(puppeteerStatusBar);
        root.setStyle("-fx-background-color: #121212;");

        StackPane rootStack = new StackPane(root, floatingToolbarLayer, overlayDialog);
        Scene fxScene = new Scene(rootStack);
        EditorTheme.apply(fxScene);
        setScene(fxScene);
        fxScene.widthProperty().addListener((obs, oldValue, newValue) -> {
            relayoutFloatingRestoreTabs();
            relayoutEdgeBar();
        });
        fxScene.heightProperty().addListener((obs, oldValue, newValue) -> {
            relayoutFloatingRestoreTabs();
            relayoutEdgeBar();
        });
        applyUiScale();
        applyLinuxDefaultWindowState();

        setupKeyboardShortcuts(fxScene);
        setupPlaybackTimer();
        Platform.runLater(this::applyToolbarDivider);
        setCodePaneVisible(true);
        applyToolbarChromeDensity(getToolbarLayoutMode());
        tfTimelineName.textProperty().addListener((obs, ov, nv) -> setDirty(dirty));
        setDirty(false);
        setOnCloseRequest(e -> {
            if (bypassCloseConfirmation) {
                if (playbackTimer != null) playbackTimer.stop();
                return;
            }
            e.consume();
            requestWindowClose();
        });

        refreshExportPreview();
    }

    static Optional<javafx.scene.image.Image> loadWindowIcon() {
        java.net.URL resource = PuppeteerWindow.class.getResource(WINDOW_ICON_RESOURCE);
        return resource == null
            ? Optional.empty()
            : Optional.of(new javafx.scene.image.Image(resource.toExternalForm(), false));
    }

    private HBox buildPuppeteerStatusBar() {
        statusBar = puppeteerStatusLabel("Ready", 300);
        statusPlaybackLabel = puppeteerStatusLabel("Stopped", 120);
        statusTimelineLabel = puppeteerStatusLabel("00:00.000 / 00:00.000", 160);
        statusSelectionLabel = puppeteerStatusLabel("No selection", 260);
        statusSceneLabel = puppeteerStatusLabel("0 tracks", 170);
        statusComplexityLabel = puppeteerStatusLabel("0 keys", 150);
        statusModeLabel = puppeteerStatusLabel("Snap 50ms", 220);
        statusSaveLabel = puppeteerStatusLabel("Saved", 120);
        statusViewportLabel = puppeteerStatusLabel("Viewport --", 130);
        statusProjectLabel = puppeteerStatusLabel("No project", 180);
        statusExportLabel = puppeteerStatusLabel("Code Pane", 180);

        HBox productSegment = puppeteerStatusSegment(CssIcon.movie("#d6d6d6"), statusBar, "jvn-status-segment-strong");
        statusPlaybackSegment = puppeteerStatusSegment(CssIcon.play("#d6d6d6"), statusPlaybackLabel);
        HBox timelineSegment = puppeteerStatusSegment(CssIcon.timeline("#d6d6d6"), statusTimelineLabel);
        HBox selectionSegment = puppeteerStatusSegment(CssIcon.rectSelect("#d6d6d6"), statusSelectionLabel);
        HBox sceneSegment = puppeteerStatusSegment(CssIcon.landscape("#d6d6d6"), statusSceneLabel);
        statusComplexitySegment = puppeteerStatusSegment(CssIcon.memory("#d6d6d6"), statusComplexityLabel);
        statusModeSegment = puppeteerStatusSegment(CssIcon.grid4x4("#d6d6d6"), statusModeLabel);
        statusSaveSegment = puppeteerStatusSegment(CssIcon.save("#d6d6d6"), statusSaveLabel);
        statusViewportSegment = puppeteerStatusSegment(CssIcon.openInFull("#d6d6d6"), statusViewportLabel);
        HBox projectSegment = puppeteerStatusSegment(CssIcon.folder("#d6d6d6"), statusProjectLabel);
        statusExportSegment = puppeteerStatusSegment(CssIcon.document("#d6d6d6"), statusExportLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox();
        bar.getStyleClass().addAll("jvn-status-bar", "puppeteer-status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMinHeight(25);
        bar.setPrefHeight(25);
        bar.setPadding(new Insets(0, 8, 0, 8));
        bar.getChildren().addAll(
            productSegment,
            puppeteerStatusSeparator(),
            statusPlaybackSegment,
            puppeteerStatusSeparator(),
            timelineSegment,
            puppeteerStatusSeparator(),
            selectionSegment,
            puppeteerStatusSeparator(),
            sceneSegment,
            puppeteerStatusSeparator(),
            statusComplexitySegment,
            spacer,
            statusModeSegment,
            puppeteerStatusSeparator(),
            statusSaveSegment,
            puppeteerStatusSeparator(),
            statusViewportSegment,
            puppeteerStatusSeparator(),
            projectSegment,
            puppeteerStatusSeparator(),
            statusExportSegment
        );

        installPuppeteerStatusMenu(productSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Keyboard Shortcuts", this::showShortcutsOverlay),
            puppeteerStatusItem("Copy Exported Code", this::copyExportedCodeToClipboard),
            puppeteerStatusSeparatorItem(),
            puppeteerStatusItem(isCodePaneVisible() ? "Hide Code Pane" : "Show Code Pane", () -> setCodePaneVisible(!isCodePaneVisible()))
        ));
        installPuppeteerStatusMenu(statusPlaybackSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem(project.isPlaying() ? "Pause" : "Play", () -> { if (project.isPlaying()) pause(); else play(); }),
            puppeteerStatusItem("Stop", this::stop),
            puppeteerStatusItem("Rewind", this::rewind),
            puppeteerStatusSeparatorItem(),
            puppeteerStatusItem(project.isLooping() ? "Disable Loop" : "Enable Loop", () -> {
                project.setLooping(!project.isLooping());
                if (cbLoop != null) cbLoop.setSelected(project.isLooping());
                refreshExportPreviewAndMarkDirty();
            })
        ));
        installPuppeteerStatusMenu(timelineSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Add Keyframe at Playhead", () -> {
                timelinePanel.addKeyframeAtPlayhead();
                refreshExportPreviewAndMarkDirty();
            }),
            puppeteerStatusItem("Previous Keyframe", () -> {
                if (timelinePanel.jumpPlayheadToPreviousKeyframe()) updateStatusBar();
            }),
            puppeteerStatusItem("Next Keyframe", () -> {
                if (timelinePanel.jumpPlayheadToNextKeyframe()) updateStatusBar();
            }),
            puppeteerStatusItem("Zoom to Selection", () -> timelinePanel.zoomToSelection())
        ));
        installPuppeteerStatusMenu(selectionSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Focus Preview Selection", () -> animationPreview.fitToContent()),
            puppeteerStatusItem("Clear Keyframe Selection", () -> {
                timelinePanel.clearKeyframeSelection();
                updateStatusBar();
                refreshSidebarTabs();
            }),
            puppeteerStatusItem("Copy Selected Keyframes", () -> {
                timelinePanel.copySelectedKeyframes();
                updateStatusBar();
            })
        ));
        installPuppeteerStatusMenu(sceneSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Import Assets", this::showAssetImporterWindow),
            puppeteerStatusItem("Fit Preview Viewport", () -> animationPreview.fitToContent()),
            puppeteerStatusItem("Manage Event Cues", () -> showEventCueManagerDialog(null)),
            puppeteerStatusItem("Add Audio Cue at Playhead", this::showAddAudioCueDialog)
        ));
        installPuppeteerStatusMenu(statusComplexitySegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Runtime Verification", this::showRuntimeVerificationReport),
            puppeteerStatusItem("Zoom Timeline to Fit", () -> timelinePanel.zoomToFit()),
            puppeteerStatusItem("Manage Event Cues", () -> showEventCueManagerDialog(null))
        ));
        installPuppeteerStatusMenu(statusModeSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem(timelinePanel.isSnapEnabled() ? "Disable Timeline Snap" : "Enable Timeline Snap",
                () -> setTimelineSnapEnabled(!timelinePanel.isSnapEnabled())),
            puppeteerStatusItem(runtimeParityPreview ? "Disable Runtime Preview" : "Enable Runtime Preview",
                () -> setRuntimeParityPreviewEnabled(!runtimeParityPreview)),
            puppeteerStatusItem(viewportStabilizationEnabled ? "Disable Viewport Stabilization" : "Enable Viewport Stabilization",
                () -> setViewportStabilizationEnabled(!viewportStabilizationEnabled))
        ));
        installPuppeteerStatusMenu(statusSaveSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Save & Register", this::requestRegisterTimeline),
            puppeteerStatusItem("Stage Code Preview", this::stagePreviewFromCode),
            puppeteerStatusItem("Commit Staged Preview", this::commitStagedPreview),
            puppeteerStatusItem("Discard Staged Preview", this::discardStagedPreview)
        ));
        installPuppeteerStatusMenu(statusViewportSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Fit Preview Viewport", () -> animationPreview.fitToContent()),
            puppeteerStatusItem(previewFocusMode ? "Exit Preview Focus" : "Focus Preview", () -> {
                if (previewFocusMode) exitFullscreenPreview();
                else enterFullscreenPreview();
            }),
            puppeteerStatusItem("Record GIF / Frames", this::showRecordGifDialog)
        ));
        installPuppeteerStatusMenu(projectSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Import Assets", this::showAssetImporterWindow),
            puppeteerStatusItem("Record GIF / Frames", this::showRecordGifDialog),
            puppeteerStatusItem("Fit Preview Viewport", () -> animationPreview.fitToContent())
        ));
        installPuppeteerStatusMenu(statusExportSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Copy Exported Code", this::copyExportedCodeToClipboard),
            puppeteerStatusItem("Refresh Code Preview", this::refreshExportPreview),
            puppeteerStatusItem(isCodePaneVisible() ? "Hide Code Pane" : "Show Code Pane", () -> setCodePaneVisible(!isCodePaneVisible()))
        ));
        return bar;
    }

    private static Label puppeteerStatusLabel(String text, double maxWidth) {
        Label label = new Label(text);
        label.getStyleClass().add("jvn-status-label");
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setMinWidth(0);
        label.setMaxWidth(maxWidth);
        return label;
    }

    private static HBox puppeteerStatusSegment(Node icon, Label label, String... extraStyleClasses) {
        HBox segment = new HBox(5);
        segment.getStyleClass().add("jvn-status-segment");
        if (extraStyleClasses != null) {
            segment.getStyleClass().addAll(extraStyleClasses);
        }
        segment.setAlignment(Pos.CENTER_LEFT);
        segment.setMinWidth(0);
        if (icon != null) segment.getChildren().add(icon);
        segment.getChildren().add(label);
        return segment;
    }

    private static Region puppeteerStatusSeparator() {
        Region separator = new Region();
        separator.getStyleClass().add("jvn-status-separator");
        separator.setMinWidth(1);
        separator.setPrefWidth(1);
        separator.setMaxWidth(1);
        return separator;
    }

    private static void installPuppeteerStatusMenu(Node node, java.util.function.Supplier<ContextMenu> menuSupplier) {
        node.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY && event.getButton() != MouseButton.SECONDARY) return;
            ContextMenu menu = menuSupplier.get();
            if (menu == null || menu.getItems().isEmpty()) return;
            menu.show(node, Side.TOP, 0, 0);
            event.consume();
        });
    }

    private static ContextMenu puppeteerStatusMenu(MenuItem... items) {
        ContextMenu menu = new ContextMenu();
        if (items != null) {
            for (MenuItem item : items) {
                if (item != null) menu.getItems().add(item);
            }
        }
        return menu;
    }

    private static MenuItem puppeteerStatusItem(String label, Runnable action) {
        MenuItem item = new MenuItem(label);
        item.setDisable(action == null);
        if (action != null) item.setOnAction(e -> action.run());
        return item;
    }

    private static MenuItem puppeteerStatusSeparatorItem() {
        return new SeparatorMenuItem();
    }

    private HBox buildToolbarCommandBar() {
        MenuItem miSaveRegister = new MenuItem("Save & Register");
        miSaveRegister.setOnAction(e -> requestRegisterTimeline());
        MenuItem miVerifyRuntime = new MenuItem("Verify Runtime Registration...");
        miVerifyRuntime.setOnAction(e -> showRuntimeVerificationReport());
        MenuItem miRefreshCode = new MenuItem("Refresh Generated Code");
        miRefreshCode.setGraphic(RefreshIcon.of(16));
        miRefreshCode.setOnAction(e -> requestRefreshGeneratedCode());
        MenuItem miStagePreview = new MenuItem("Stage Code Preview");
        miStagePreview.setOnAction(e -> stagePreviewFromCode());
        MenuItem miCommitPreview = new MenuItem("Commit Staged Preview");
        miCommitPreview.setOnAction(e -> commitStagedPreview());
        MenuItem miDiscardPreview = new MenuItem("Discard Staged Preview");
        miDiscardPreview.setOnAction(e -> discardStagedPreview());
        MenuItem miCopyExportCode = new MenuItem("Copy Exported Code");
        miCopyExportCode.setOnAction(e -> copyExportedCodeToClipboard());
        MenuItem miSaveClip = new MenuItem("Save Selection as Clip");
        miSaveClip.setOnAction(e -> saveSelectionAsClip());
        MenuItem miLoadClip = new MenuItem("Load Clip at Playhead");
        miLoadClip.setOnAction(e -> loadAndApplyClip());
        MenuItem miImportAssets = new MenuItem("Import Assets...");
        miImportAssets.setOnAction(e -> showAssetImporterWindow());
        MenuItem miRecordGif = new MenuItem("Record Preview as GIF...");
        miRecordGif.setOnAction(e -> showRecordGifDialog());
        MenuItem miClose = new MenuItem("Close Puppeteer");
        miClose.setOnAction(e -> requestWindowClose());

        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(
            miSaveRegister,
            miClose
        );
        fileMenu.setOnShowing(e -> {
            String timelineName = tfTimelineName != null ? tfTimelineName.getText().trim() : "";
            boolean hasTimelineName = timelineName != null && !timelineName.isBlank();
            miSaveRegister.setText(dirty || previewStaged ? "Save & Register" : "Save & Register Again");
            miSaveRegister.setDisable(!hasTimelineName);
            miClose.setText(dirty || previewStaged ? "Close..." : "Close");
        });

        MenuItem miUndo = new MenuItem("Undo");
        miUndo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
        miUndo.setOnAction(e -> executeUndo());
        MenuItem miRedo = new MenuItem("Redo");
        miRedo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        miRedo.setOnAction(e -> executeRedo());
        MenuItem miAddKeyframe = new MenuItem("Add Keyframe at Playhead");
        miAddKeyframe.setAccelerator(new KeyCodeCombination(KeyCode.K));
        miAddKeyframe.setOnAction(e -> {
            timelinePanel.addKeyframeAtPlayhead();
            refreshExportPreviewAndMarkDirty();
        });
        MenuItem miDeleteKeyframes = new MenuItem("Delete Selected Keyframes");
        miDeleteKeyframes.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        miDeleteKeyframes.setOnAction(e -> {
            timelinePanel.deleteSelectedKeyframe();
            refreshExportPreviewAndMarkDirty();
        });
        MenuItem miCopyKeyframes = new MenuItem("Copy Keyframes");
        miCopyKeyframes.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN));
        miCopyKeyframes.setOnAction(e -> copySelectedKeyframesToClipboard());
        MenuItem miPasteKeyframes = new MenuItem("Paste Keyframes at Playhead");
        miPasteKeyframes.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN));
        miPasteKeyframes.setOnAction(e -> pasteCopiedKeyframesAtPlayhead());
        MenuItem miDuplicateKeyframes = new MenuItem("Duplicate Keyframes");
        miDuplicateKeyframes.setAccelerator(new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN));
        miDuplicateKeyframes.setOnAction(e -> duplicateSelectedKeyframesBySnapStep());
        MenuItem miClipboardHistory = new MenuItem("Clipboard History...");
        miClipboardHistory.setOnAction(e -> showClipboardHistoryPopup());
        MenuItem miBatchKeyframe = new MenuItem("Add Keyframe for All Entities");
        miBatchKeyframe.setOnAction(e -> {
            PropertyType prop = cbProperty != null && cbProperty.getValue() != null
                ? cbProperty.getValue()
                : PropertyType.X;
            timelinePanel.addKeyframeForAllEntities(project.getPlayheadMs(), prop);
            refreshExportPreviewAndMarkDirty();
        });
        MenuItem miDistributeKeyframes = new MenuItem("Distribute Selected Keyframes");
        miDistributeKeyframes.setAccelerator(new KeyCodeCombination(KeyCode.E, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN));
        miDistributeKeyframes.setOnAction(e -> {
            if (timelinePanel.distributeSelectedKeyframes()) {
                refreshExportPreviewAndMarkDirty();
            }
        });
        MenuItem miReverseKeyframes = new MenuItem("Reverse Selected Keyframes");
        miReverseKeyframes.setAccelerator(new KeyCodeCombination(KeyCode.R, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN));
        miReverseKeyframes.setOnAction(e -> {
            if (timelinePanel.reverseSelectedKeyframes()) {
                refreshExportPreviewAndMarkDirty();
            }
        });
        MenuItem miStretchKeyframes = new MenuItem("Stretch Selected Keyframes 25%");
        miStretchKeyframes.setOnAction(e -> {
            if (timelinePanel.stretchSelectedKeyframes(1.25)) {
                refreshExportPreviewAndMarkDirty();
            }
        });
        MenuItem miCompressKeyframes = new MenuItem("Compress Selected Keyframes 20%");
        miCompressKeyframes.setOnAction(e -> {
            if (timelinePanel.stretchSelectedKeyframes(0.8)) {
                refreshExportPreviewAndMarkDirty();
            }
        });
        MenuItem miEasingCurve = new MenuItem("Edit Easing Curve...");
        miEasingCurve.setOnAction(e -> timelinePanel.openEasingCurveEditor());
        CheckMenuItem miRippleRetime = new CheckMenuItem("Ripple Retime");
        miRippleRetime.setOnAction(e -> timelinePanel.setRippleRetimeEnabled(miRippleRetime.isSelected()));
        MenuItem miClearKeyframeSelection = new MenuItem("Clear Keyframe Selection");
        miClearKeyframeSelection.setOnAction(e -> {
            timelinePanel.clearKeyframeSelection();
            updateStatusBar();
            refreshSidebarTabs();
        });
        MenuItem miApplyPreset = new MenuItem("Apply Animation Preset...");
        miApplyPreset.setOnAction(e -> showPresetMenuOverlay());
        MenuItem miPlaceInSlot = new MenuItem("Place Entity in VN Slot...");
        miPlaceInSlot.setOnAction(e -> showSlotMenuOverlay());
        MenuItem miEyeFocus = new MenuItem("Eye Focus / Look At...");
        miEyeFocus.setOnAction(e -> showEyeFocusOverlay());
        MenuItem miExpressionKeyframe = new MenuItem("Add Expression Keyframe...");
        miExpressionKeyframe.setOnAction(e -> showExpressionKeyframeDialog());
        MenuItem miSyncSnapshot = new MenuItem("Sync Snapshot from VNS Script");
        miSyncSnapshot.setOnAction(e -> requestSyncSnapshot());

        Menu editMenu = new Menu("Edit");
        editMenu.getItems().addAll(
            miUndo,
            miRedo
        );
        editMenu.setOnShowing(e -> {
            miUndo.setText(commandStack.canUndo()
                ? "Undo " + commandStack.undoDescription()
                : "Undo");
            miRedo.setText(commandStack.canRedo()
                ? "Redo " + commandStack.redoDescription()
                : "Redo");
            miUndo.setDisable(!commandStack.canUndo());
            miRedo.setDisable(!commandStack.canRedo());
        });

        Menu keyframesMenu = new Menu("Keyframes");
        keyframesMenu.getItems().addAll(
            miAddKeyframe,
            miBatchKeyframe,
            miDeleteKeyframes,
            miClearKeyframeSelection,
            new SeparatorMenuItem(),
            miCopyKeyframes,
            miPasteKeyframes,
            miDuplicateKeyframes,
            miClipboardHistory,
            new SeparatorMenuItem(),
            miDistributeKeyframes,
            miReverseKeyframes,
            miStretchKeyframes,
            miCompressKeyframes,
            new SeparatorMenuItem(),
            miEasingCurve,
            miRippleRetime,
            new SeparatorMenuItem(),
            miSaveClip,
            miLoadClip
        );
        keyframesMenu.setOnShowing(e -> {
            int selectionCount = timelinePanel != null ? timelinePanel.getSelectionCount() : 0;
            boolean hasSelection = selectionCount > 0;
            boolean hasTarget = timelinePanel != null
                && timelinePanel.getSelectedEntity() != null
                && !timelinePanel.getSelectedEntity().isBlank();
            boolean hasEditableKeyframe = hasSelection || (keyframeEditor != null && keyframeEditor.getCurrentKeyframe() != null);
            boolean hasTrack = selectedTrackForEditing(false) != null;

            miAddKeyframe.setDisable(!hasTarget);
            miBatchKeyframe.setDisable(project == null || !project.getTracks().iterator().hasNext());
            miDeleteKeyframes.setDisable(!hasEditableKeyframe);
            miClearKeyframeSelection.setDisable(!hasSelection);
            miCopyKeyframes.setDisable(!hasSelection);
            miPasteKeyframes.setDisable(!hasTarget || timelinePanel.getCopiedKeyframeCount() == 0);
            miDuplicateKeyframes.setDisable(!hasSelection);
            miClipboardHistory.setDisable(clipboardHistory.isEmpty());
            miDistributeKeyframes.setDisable(selectionCount < 3);
            miReverseKeyframes.setDisable(selectionCount < 2);
            miStretchKeyframes.setDisable(selectionCount < 2);
            miCompressKeyframes.setDisable(selectionCount < 2);
            miEasingCurve.setDisable(!hasEditableKeyframe);
            miRippleRetime.setSelected(timelinePanel != null && timelinePanel.isRippleRetimeEnabled());
            miSaveClip.setDisable(!hasTrack || projectRoot == null);
            miLoadClip.setDisable(!hasTrack || !hasSavedClips());
        });

        Menu characterMenu = new Menu("Character");
        characterMenu.getItems().addAll(
            miExpressionKeyframe,
            new SeparatorMenuItem(),
            miApplyPreset,
            miPlaceInSlot,
            miEyeFocus,
            new SeparatorMenuItem(),
            miSyncSnapshot,
            miImportAssets
        );
        characterMenu.setOnShowing(e -> {
            boolean hasTarget = timelinePanel != null
                && timelinePanel.getSelectedEntity() != null
                && !timelinePanel.getSelectedEntity().isBlank();
            boolean entityTarget = hasTarget && !timelinePanel.isSelectedGroup();
            miExpressionKeyframe.setDisable(!entityTarget || timelinePanel.isRuntimeCameraSelected());
            miApplyPreset.setDisable(!entityTarget);
            miPlaceInSlot.setDisable(!entityTarget);
            miEyeFocus.setDisable(project == null || timelinePanel == null);
            miSyncSnapshot.setDisable(onSyncSnapshotRequested == null);
            miImportAssets.setDisable(projectRoot == null || !projectRoot.isDirectory());
        });

        // === Timeline menu ===
        MenuItem miJumpStart = new MenuItem("Jump to Start");
        miJumpStart.setAccelerator(new KeyCodeCombination(KeyCode.HOME));
        miJumpStart.setOnAction(e -> rewind());

        MenuItem miJumpEnd = new MenuItem("Jump to End");
        miJumpEnd.setAccelerator(new KeyCodeCombination(KeyCode.END));
        miJumpEnd.setOnAction(e -> {
            if (project == null) return;
            project.setPlayheadMs(project.getTotalDurationMs());
            updateTimeLabel();
            updatePreview();
            timelinePanel.refresh();
        });

        MenuItem miTimelinePrevKey = new MenuItem("Previous Keyframe");
        miTimelinePrevKey.setAccelerator(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.SHORTCUT_DOWN));
        miTimelinePrevKey.setOnAction(e -> {
            if (timelinePanel.jumpPlayheadToPreviousKeyframe()) { updateTimeLabel(); updatePreview(); }
            refreshSidebarTabs();
        });

        MenuItem miTimelineNextKey = new MenuItem("Next Keyframe");
        miTimelineNextKey.setAccelerator(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.SHORTCUT_DOWN));
        miTimelineNextKey.setOnAction(e -> {
            if (timelinePanel.jumpPlayheadToNextKeyframe()) { updateTimeLabel(); updatePreview(); }
            refreshSidebarTabs();
        });

        MenuItem miTimelineFocusSel = new MenuItem("Focus Timeline on Selection");
        miTimelineFocusSel.setOnAction(e -> timelinePanel.zoomToSelection());
        MenuItem miTimelineZoomFit = new MenuItem("Zoom Timeline to Fit");
        miTimelineZoomFit.setOnAction(e -> timelinePanel.zoomToFit());
        MenuItem miTimelineFitDuration = new MenuItem("Fit Duration to Content");
        miTimelineFitDuration.setOnAction(e -> {
            project.fitDurationToContent();
            syncDurationUi();
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });
        CheckMenuItem miTimelineSnap = new CheckMenuItem("Timeline Snap");
        miTimelineSnap.setOnAction(e -> setTimelineSnapEnabled(miTimelineSnap.isSelected()));
        Menu snapStepMenu = new Menu("Snap Step");
        ToggleGroup snapStepGroup = new ToggleGroup();
        for (double step : new double[] {1.0, 10.0, 33.0, 50.0, 100.0, 250.0, 500.0}) {
            RadioMenuItem item = new RadioMenuItem(String.format(Locale.ROOT, "%.0f ms", step));
            item.setUserData(step);
            item.setToggleGroup(snapStepGroup);
            item.setOnAction(e -> setTimelineSnapStep(step));
            snapStepMenu.getItems().add(item);
        }

        Menu timelineMenu = new Menu("Timeline");
        timelineMenu.getItems().addAll(
            miJumpStart, miJumpEnd,
            new SeparatorMenuItem(),
            miTimelinePrevKey, miTimelineNextKey,
            new SeparatorMenuItem(),
            miTimelineFocusSel, miTimelineZoomFit,
            miTimelineFitDuration,
            new SeparatorMenuItem(),
            miTimelineSnap,
            snapStepMenu
        );
        timelineMenu.setOnShowing(e -> {
            boolean hasTarget = timelinePanel != null && timelinePanel.getSelectedEntity() != null
                && !timelinePanel.getSelectedEntity().isBlank();
            boolean hasSelection = timelinePanel != null && timelinePanel.getSelectionCount() > 0;
            miTimelinePrevKey.setDisable(!hasTarget);
            miTimelineNextKey.setDisable(!hasTarget);
            miTimelineFocusSel.setDisable(!hasSelection);
            miTimelineFitDuration.setDisable(project == null);
            miTimelineSnap.setSelected(timelinePanel != null && timelinePanel.isSnapEnabled());
            for (MenuItem item : snapStepMenu.getItems()) {
                if (item instanceof RadioMenuItem radio && item.getUserData() instanceof Double preset) {
                    radio.setSelected(timelinePanel != null && Math.abs(preset - timelinePanel.getSnapStepMs()) < 0.001);
                }
            }
        });

        // === Cues menu ===
        MenuItem miSceneAddCue = new MenuItem("Add Audio Cue at Playhead");
        miSceneAddCue.setOnAction(e -> showAddAudioCueDialog());

        MenuItem miSceneClearCues = new MenuItem("Clear All Audio Cues");
        miSceneClearCues.setOnAction(e -> {
            if (project == null || project.getAudioCues().isEmpty()) return;
            overlayDialog.showDialog("Clear Audio Cues",
                "Remove all audio cues from this animation? This cannot be undone from the cue panel.",
                null,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.danger("Clear", () -> {
                    project.clearAudioCues();
                    timelinePanel.refresh();
                    refreshExportPreviewAndMarkDirty();
                }));
        });

        MenuItem miSceneManageEvents = new MenuItem("Manage Event Cues...");
        miSceneManageEvents.setOnAction(e -> showEventCueManagerDialog(null));
        MenuItem miSceneAddExpressionCue = new MenuItem("Add Expression Cue at Playhead...");
        miSceneAddExpressionCue.setOnAction(e -> showExpressionKeyframeDialog());

        MenuItem miSceneClearEvents = new MenuItem("Clear All Event Cues");
        miSceneClearEvents.setOnAction(e -> {
            if (project == null || project.getEditorEventCues().isEmpty()) return;
            overlayDialog.showDialog("Clear Event Cues",
                "Remove all timeline event cues from this animation?",
                null,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.danger("Clear", () -> {
                    project.clearEditorEventCues();
                    timelinePanel.refresh();
                    updatePreview();
                    refreshExportPreviewAndMarkDirty();
                }));
        });

        Menu cuesMenu = new Menu("Cues");
        cuesMenu.getItems().addAll(
            miSceneAddCue, miSceneClearCues,
            new SeparatorMenuItem(),
            miSceneAddExpressionCue,
            miSceneManageEvents, miSceneClearEvents
        );
        cuesMenu.setOnShowing(e -> {
            miSceneAddCue.setDisable(project == null);
            miSceneAddExpressionCue.setDisable(project == null);
            miSceneManageEvents.setDisable(project == null);
            miSceneClearCues.setDisable(project == null || project.getAudioCues().isEmpty());
            miSceneClearEvents.setDisable(project == null || project.getEditorEventCues().isEmpty());
        });

        CheckMenuItem miShowToolbar = new CheckMenuItem("Show Menu Bar");
        miShowToolbar.setOnAction(e -> setTopToolbarVisible(miShowToolbar.isSelected()));
        CheckMenuItem miShowCodePane = new CheckMenuItem("Show Code Pane");
        miShowCodePane.setOnAction(e -> setCodePaneVisible(miShowCodePane.isSelected()));
        CheckMenuItem miOnionSkin = new CheckMenuItem("Onion Skin Preview");
        miOnionSkin.setOnAction(e -> animationPreview.setOnionSkinning(miOnionSkin.isSelected()));
        CheckMenuItem miInterpolationGhosts = new CheckMenuItem("Interpolation Ghosts");
        miInterpolationGhosts.setOnAction(e -> animationPreview.setShowInterpolationGhosts(miInterpolationGhosts.isSelected()));
        CheckMenuItem miShowSafeGuides = new CheckMenuItem("Show Safe Guides");
        miShowSafeGuides.setOnAction(e -> animationPreview.setShowSafeGuides(miShowSafeGuides.isSelected()));
        CheckMenuItem miShowTitleGuides = new CheckMenuItem("Show Title Guides");
        miShowTitleGuides.setOnAction(e -> animationPreview.setShowTitleGuides(miShowTitleGuides.isSelected()));
        RadioMenuItem miLayoutDynamic = new RadioMenuItem("Toolbar Layout: Dynamic");
        RadioMenuItem miLayoutCompact = new RadioMenuItem("Toolbar Layout: Compact");
        ToggleGroup layoutMenuGroup = new ToggleGroup();
        miLayoutDynamic.setToggleGroup(layoutMenuGroup);
        miLayoutCompact.setToggleGroup(layoutMenuGroup);
        miLayoutDynamic.setOnAction(e -> setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode.DYNAMIC));
        miLayoutCompact.setOnAction(e -> setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode.COMPACT));
        Menu uiScaleMenu = new Menu("UI Scale");
        ToggleGroup uiScaleGroup = new ToggleGroup();
        double[] scalePresets = {0.80, 0.90, 1.00, 1.10, 1.25, 1.50};
        for (double preset : scalePresets) {
            RadioMenuItem miScale = new RadioMenuItem(formatUiScaleLabel(preset));
            miScale.setUserData(preset);
            miScale.setToggleGroup(uiScaleGroup);
            miScale.setOnAction(e -> setUiScale(preset));
            uiScaleMenu.getItems().add(miScale);
        }
        MenuItem miResetUiScale = new MenuItem("Reset UI Scale");
        miResetUiScale.setOnAction(e -> setUiScale(1.0));
        MenuItem miResetLayout = new MenuItem("Reset Resizable Bars");
        miResetLayout.setOnAction(e -> resetWorkspaceLayout());
        MenuItem miFullscreenPreview = new MenuItem("Toggle Focused Preview Layout");
        miFullscreenPreview.setOnAction(e -> togglePreviewFocusMode());
        MenuItem miFitPreview = new MenuItem("Fit Preview Viewport");
        miFitPreview.setOnAction(e -> animationPreview.fitToContent());
        CheckMenuItem miAutoKeyMenu = new CheckMenuItem("Auto-Key on Drag");
        miAutoKeyMenu.setOnAction(e -> setAutoKeyEnabled(miAutoKeyMenu.isSelected()));
        CheckMenuItem miRuntimePreviewMenu = new CheckMenuItem("Runtime Data Preview");
        miRuntimePreviewMenu.setOnAction(e -> setRuntimeParityPreviewEnabled(miRuntimePreviewMenu.isSelected()));
        CheckMenuItem miPreviewStabilization = new CheckMenuItem("Stabilize Preview Viewport");
        miPreviewStabilization.setOnAction(e -> setViewportStabilizationEnabled(miPreviewStabilization.isSelected()));
        CheckMenuItem miPreviewSnapGrid = new CheckMenuItem("Snap Dragging to Grid");
        miPreviewSnapGrid.setOnAction(e -> setPreviewSnapToGridEnabled(miPreviewSnapGrid.isSelected()));
        CheckMenuItem miPreviewSnapEntity = new CheckMenuItem("Snap Dragging to Entities");
        miPreviewSnapEntity.setOnAction(e -> setPreviewSnapToEntityEnabled(miPreviewSnapEntity.isSelected()));
        RadioMenuItem miWheelView = new RadioMenuItem("Wheel Controls View");
        RadioMenuItem miWheelCamera = new RadioMenuItem("Wheel Controls Runtime Camera");
        ToggleGroup wheelModeGroup = new ToggleGroup();
        miWheelView.setToggleGroup(wheelModeGroup);
        miWheelCamera.setToggleGroup(wheelModeGroup);
        miWheelView.setOnAction(e -> setScrollZoomMode(AnimationPreview.ScrollZoomMode.VIEW));
        miWheelCamera.setOnAction(e -> setScrollZoomMode(AnimationPreview.ScrollZoomMode.CAMERA));
        Menu wheelModeMenu = new Menu("Mouse Wheel Mode");
        wheelModeMenu.getItems().addAll(miWheelView, miWheelCamera);

        Menu previewMenu = new Menu("Preview");
        previewMenu.getItems().addAll(
            miFitPreview,
            miFullscreenPreview,
            miRecordGif,
            new SeparatorMenuItem(),
            CompositionGuideOverlay.createMenu(),
            new SeparatorMenuItem(),
            miOnionSkin,
            miInterpolationGhosts,
            miShowSafeGuides,
            miShowTitleGuides,
            new SeparatorMenuItem(),
            miAutoKeyMenu,
            miRuntimePreviewMenu,
            miPreviewStabilization,
            new SeparatorMenuItem(),
            miPreviewSnapGrid,
            miPreviewSnapEntity,
            wheelModeMenu
        );
        previewMenu.setOnShowing(e -> {
            miFullscreenPreview.setDisable(scene == null && !isPreviewFullscreenActive());
            miOnionSkin.setSelected(animationPreview.isOnionSkinning());
            miInterpolationGhosts.setSelected(animationPreview.isShowInterpolationGhosts());
            miShowSafeGuides.setSelected(animationPreview.isShowSafeGuides());
            miShowTitleGuides.setSelected(animationPreview.isShowTitleGuides());
            miAutoKeyMenu.setSelected(autoKeyEnabled);
            miRuntimePreviewMenu.setSelected(runtimeParityPreview);
            miPreviewStabilization.setSelected(viewportStabilizationEnabled);
            miPreviewSnapGrid.setSelected(animationPreview.isSnapToGridEnabled());
            miPreviewSnapEntity.setSelected(animationPreview.isSnapToEntityEnabled());
            AnimationPreview.ScrollZoomMode wheelMode = animationPreview.getScrollZoomMode();
            miWheelView.setSelected(wheelMode == AnimationPreview.ScrollZoomMode.VIEW);
            miWheelCamera.setSelected(wheelMode == AnimationPreview.ScrollZoomMode.CAMERA);
        });

        MenuItem miExpandAll = new MenuItem("Expand All Clusters");
        miExpandAll.setOnAction(e -> toolbarClusters.values().forEach(c -> c.setExpanded(true)));
        MenuItem miCollapseAll = new MenuItem("Collapse All Clusters");
        miCollapseAll.setOnAction(e -> toolbarClusters.values().forEach(c -> c.setExpanded(false)));

        Menu toolbarClustersMenu = new Menu("Toolbar Clusters");
        toolbarClustersMenu.setOnShowing(e -> {
            toolbarClustersMenu.getItems().clear();
            for (CollapsibleToolbarCluster cluster : new LinkedHashSet<>(toolbarClusters.values())) {
                CheckMenuItem mi = new CheckMenuItem(cluster.getTitle());
                mi.setSelected(cluster.isExpanded());
                mi.setOnAction(ev -> cluster.setExpanded(mi.isSelected()));
                toolbarClustersMenu.getItems().add(mi);
            }
        });
        Menu dockersMenu = new Menu("Dockers");
        dockersMenu.setOnShowing(e -> populateDockersMenu(dockersMenu));
        populateDockersMenu(dockersMenu);
        Menu workspacePresetsMenu = buildWorkspacePresetsMenu();

        Menu layoutMenu = new Menu("Layout");
        layoutMenu.getItems().addAll(
            miShowToolbar,
            miShowCodePane,
            new SeparatorMenuItem(),
            miLayoutDynamic,
            miLayoutCompact,
            toolbarClustersMenu,
            miExpandAll,
            miCollapseAll,
            new SeparatorMenuItem(),
            uiScaleMenu,
            miResetUiScale,
            new SeparatorMenuItem(),
            workspacePresetsMenu,
            dockersMenu,
            new SeparatorMenuItem(),
            miResetLayout
        );
        layoutMenu.setOnShowing(e -> {
            populateDockersMenu(dockersMenu);
            miShowToolbar.setSelected(isTopToolbarVisible());
            miShowCodePane.setSelected(isCodePaneVisible());
            miLayoutDynamic.setSelected(getToolbarLayoutMode() == AnimatedToolbarPane.LayoutMode.DYNAMIC);
            miLayoutCompact.setSelected(getToolbarLayoutMode() == AnimatedToolbarPane.LayoutMode.COMPACT);
            for (MenuItem item : uiScaleMenu.getItems()) {
                if (item instanceof RadioMenuItem radio && item.getUserData() instanceof Double preset) {
                    radio.setSelected(Math.abs(preset - uiScale) < 0.001);
                }
            }
        });

        MenuItem miPlayPause = new MenuItem("Play");
        miPlayPause.setOnAction(e -> {
            if (project.isPlaying()) pause();
            else play();
        });
        MenuItem miStop = new MenuItem("Stop");
        miStop.setOnAction(e -> stop());
        MenuItem miRewind = new MenuItem("Rewind");
        miRewind.setOnAction(e -> rewind());
        CheckMenuItem miLoopPlayback = new CheckMenuItem("Loop Playback");
        miLoopPlayback.setOnAction(e -> {
            project.setLooping(miLoopPlayback.isSelected());
            if (cbLoop != null) cbLoop.setSelected(miLoopPlayback.isSelected());
            refreshExportPreviewAndMarkDirty();
        });
        MenuItem miLoopIn = new MenuItem("Set Loop In at Playhead");
        miLoopIn.setOnAction(e -> {
            double inMs = project.getPlayheadMs();
            double outMs = project.hasLoopRegion() ? project.getLoopEndMs() : project.getTotalDurationMs();
            if (inMs < outMs) {
                project.setLoopRegion(inMs, outMs);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            }
        });
        MenuItem miLoopOut = new MenuItem("Set Loop Out at Playhead");
        miLoopOut.setOnAction(e -> {
            double outMs = project.getPlayheadMs();
            double inMs = project.hasLoopRegion() ? project.getLoopStartMs() : 0.0;
            if (outMs > inMs) {
                project.setLoopRegion(inMs, outMs);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            }
        });
        MenuItem miLoopClear = new MenuItem("Clear Loop Region");
        miLoopClear.setOnAction(e -> {
            project.clearLoopRegion();
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });
        CheckMenuItem miViewportStabilization = new CheckMenuItem("Stabilize Preview Viewport");
        miViewportStabilization.setOnAction(e -> setViewportStabilizationEnabled(miViewportStabilization.isSelected()));
        Menu playbackSpeedMenu = new Menu("Playback Speed");
        ToggleGroup playbackSpeedGroup = new ToggleGroup();
        for (double speed : new double[] {0.25, 0.5, 1.0, 2.0, 4.0}) {
            RadioMenuItem item = new RadioMenuItem(formatPlaybackSpeedLabel(speed));
            item.setUserData(speed);
            item.setToggleGroup(playbackSpeedGroup);
            item.setOnAction(e -> setPlaybackSpeed(speed));
            playbackSpeedMenu.getItems().add(item);
        }

        Menu playbackMenu = new Menu("Playback");
        playbackMenu.getItems().addAll(
            miPlayPause,
            miStop,
            miRewind,
            playbackSpeedMenu,
            miViewportStabilization,
            new SeparatorMenuItem(),
            miLoopPlayback,
            miLoopIn,
            miLoopOut,
            miLoopClear
        );
        playbackMenu.setOnShowing(e -> {
            miPlayPause.setText(project.isPlaying() ? "Pause" : "Play");
            miViewportStabilization.setSelected(viewportStabilizationEnabled);
            miLoopPlayback.setSelected(project.isLooping());
            miLoopClear.setDisable(!project.hasLoopRegion());
            for (MenuItem item : playbackSpeedMenu.getItems()) {
                if (item instanceof RadioMenuItem radio && item.getUserData() instanceof Double speed) {
                    radio.setSelected(Math.abs(playbackSpeed - speed) < 0.001);
                }
            }
        });

        MenuItem miExportSaveRegister = new MenuItem("Save & Register Timeline");
        miExportSaveRegister.setOnAction(e -> requestRegisterTimeline());
        MenuItem miExportRecordGif = new MenuItem("Record Preview as GIF...");
        miExportRecordGif.setOnAction(e -> showRecordGifDialog());
        CheckMenuItem miCompactExportMenu = new CheckMenuItem("Compact Code Preview");
        miCompactExportMenu.setOnAction(e -> setCompactExportEnabled(miCompactExportMenu.isSelected()));

        Menu exportMenu = new Menu("Export");
        exportMenu.getItems().addAll(
            miExportSaveRegister,
            new SeparatorMenuItem(),
            miRefreshCode,
            miStagePreview,
            miCommitPreview,
            miDiscardPreview,
            new SeparatorMenuItem(),
            miCompactExportMenu,
            miCopyExportCode,
            miExportRecordGif
        );
        exportMenu.setOnShowing(e -> {
            String timelineName = tfTimelineName != null ? tfTimelineName.getText().trim() : "";
            boolean hasTimelineName = timelineName != null && !timelineName.isBlank();
            boolean hasCode = codePreview != null && codePreview.getCode() != null && !codePreview.getCode().isBlank();
            miExportSaveRegister.setText(dirty || previewStaged ? "Save & Register Timeline" : "Save & Register Timeline Again");
            miExportSaveRegister.setDisable(!hasTimelineName);
            miRefreshCode.setDisable(project == null);
            miStagePreview.setText(previewStaged ? "Restage Code Preview" : "Stage Code Preview");
            miStagePreview.setDisable(!hasCode);
            miCommitPreview.setDisable(!previewStaged);
            miDiscardPreview.setDisable(!previewStaged);
            miCompactExportMenu.setSelected(compactExport);
            miCopyExportCode.setDisable(!hasCode);
            miExportRecordGif.setDisable(scene == null);
        });

        Menu diagnosticsMenu = new Menu("Diagnostics");
        diagnosticsMenu.getItems().add(miVerifyRuntime);
        diagnosticsMenu.setOnShowing(e -> miVerifyRuntime.setDisable(project == null));

        MenuItem miShowShortcuts = new MenuItem("Keyboard Shortcuts");
        miShowShortcuts.setOnAction(e -> showShortcutsOverlay());
        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().add(miShowShortcuts);

        MenuBar menuBar = new MenuBar(
            fileMenu,
            editMenu,
            keyframesMenu,
            timelineMenu,
            characterMenu,
            previewMenu,
            playbackMenu,
            cuesMenu,
            exportMenu,
            layoutMenu,
            diagnosticsMenu,
            helpMenu
        );
        menuBar.setUseSystemMenuBar(false);
        menuBar.setFocusTraversable(false);
        menuBar.setMinHeight(Region.USE_PREF_SIZE);
        menuBar.setMaxWidth(Region.USE_PREF_SIZE);

        lblToolbarCommandSummary = null;

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, menuBar, spacer);
        bar.getStyleClass().add("puppeteer-toolbar-command-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        return bar;
    }

    void refreshToolbarCommandSummary() {
        refreshUndoRedoControls();
        if (lblToolbarCommandSummary == null) return;
        List<String> parts = new ArrayList<>();
        parts.add(dirty ? "Unsaved" : "Saved");
        if (previewStaged) parts.add("Preview Staged");
        parts.add(isCodePaneVisible() ? "Code Pane On" : "Code Pane Off");
        parts.add(getToolbarLayoutMode() == AnimatedToolbarPane.LayoutMode.COMPACT
            ? "Compact Toolbar"
            : "Dynamic Toolbar");
        parts.add("UI " + formatUiScaleLabel(uiScale));

        if (timelinePanel != null) {
            int selectionCount = timelinePanel.getSelectionCount();
            if (selectionCount > 0) {
                parts.add(selectionCount + " Key" + (selectionCount == 1 ? "" : "s") + " Selected");
            } else if (timelinePanel.getSelectedEntity() != null && !timelinePanel.getSelectedEntity().isBlank()) {
                parts.add(selectionLabel(timelinePanel.getSelectedEntity(), timelinePanel.isSelectedGroup()));
            }
            int copiedCount = timelinePanel.getCopiedKeyframeCount();
            if (copiedCount > 0) {
                parts.add("Clipboard " + copiedCount);
            }
        }
        lblToolbarCommandSummary.setText(String.join("  •  ", parts));
    }

    void refreshUndoRedoControls() {
        if (btnUndo != null) {
            btnUndo.setDisable(!commandStack.canUndo());
            String undoText = commandStack.canUndo()
                ? "Undo " + commandStack.undoDescription() + " (Ctrl/Cmd+Z)"
                : "Undo (Ctrl/Cmd+Z)";
            installToolbarTooltip(btnUndo, undoText);
        }
        if (btnRedo != null) {
            btnRedo.setDisable(!commandStack.canRedo());
            String redoText = commandStack.canRedo()
                ? "Redo " + commandStack.redoDescription() + " (Ctrl/Cmd+Shift+Z)"
                : "Redo (Ctrl/Cmd+Shift+Z)";
            installToolbarTooltip(btnRedo, redoText);
        }
    }

    private Tab buildSelectionTab() {
        lblSidebarSelectionTarget = buildSidebarValueLabel();
        lblSidebarSelectionScope = buildSidebarValueLabel();
        lblSidebarSelectionProperty = buildSidebarValueLabel();
        lblSidebarSelectionPlayhead = buildSidebarValueLabel();
        lblSidebarSelectionCount = buildSidebarValueLabel();

        btnSidebarAddKeyframe = buildSidebarActionButton("Add Keyframe", () -> {
            if (timelinePanel.getSelectedEntity() == null) return;
            timelinePanel.addKeyframeAtPlayhead();
            refreshExportPreviewAndMarkDirty();
            refreshSidebarTabs();
        }, "Add a keyframe at the current playhead for the selected entity");
        btnSidebarAddExpressionKeyframe = buildSidebarActionButton("Expression", this::showExpressionKeyframeDialog,
            "Add an expression keyframe for the selected character at the current playhead");
        btnSidebarFocusSelection = buildSidebarActionButton("Focus Timeline", () -> {
            timelinePanel.zoomToSelection();
            refreshSidebarTabs();
        }, "Zoom the timeline to show the selected keyframes");
        Button btnPrevKey = buildSidebarActionButton("Prev Key", () -> {
            if (timelinePanel.jumpPlayheadToPreviousKeyframe()) {
                updateTimeLabel();
                updatePreview();
            }
            refreshSidebarTabs();
        }, "Jump to the previous keyframe");
        Button btnNextKey = buildSidebarActionButton("Next Key", () -> {
            if (timelinePanel.jumpPlayheadToNextKeyframe()) {
                updateTimeLabel();
                updatePreview();
            }
            refreshSidebarTabs();
        }, "Jump to the next keyframe");
        btnSidebarClearSelection = buildSidebarActionButton("Clear", () -> {
            entitySelector.selectEntity(null);
            animationPreview.clearSelection();
            timelinePanel.setSelectedTarget(null, false);
            keyframeEditor.setSelectionContext(null, false, false);
            keyframeEditor.setKeyframe(null, null);
            refreshSidebarTabs();
        }, "Clear the current selection in the timeline and viewport");

        HBox selectionActionsPrimary = buildSidebarButtonRow(btnSidebarAddKeyframe, btnSidebarAddExpressionKeyframe);
        HBox selectionActionsSecondary = buildSidebarButtonRow(btnSidebarFocusSelection, btnPrevKey, btnNextKey, btnSidebarClearSelection);

        sidebarAdvancedUnavailableCard = buildSidebarCard(
            "Advanced",
            "The Matrix / Effects, Color Matrix, Camera DOF, and Custom Channels inspectors are available when an entity track or the runtime camera track is selected.\n\nSelect an entity or the camera in the timeline, then return to this tab to access the advanced controls.",
            createSidebarHintLabel("Advanced transform and custom-channel authoring is available for entity and runtime camera tracks."));
        sidebarMatrixCard = buildSidebarCard("Matrix / Effects",
            "Raw 2D transform coefficients for advanced per-keyframe control.\n\n" +
            "• MXX / MXY — first column of the 2×2 linear transform (scale X and horizontal shear)\n" +
            "• MYX / MYY — second column (vertical shear and scale Y)\n" +
            "• TX / TY — translation offsets applied after the linear transform\n" +
            "• Blur — Gaussian blur radius in scene units (0 = sharp)\n\n" +
            "• Brightness — RGB exposure multiplier (1 = neutral)\n\n" +
            "Identity: MXX=1, MXY=0, MYX=0, MYY=1, TX=0, TY=0, Blur=0, Brightness=1.\n" +
            "\"Fill Identity\" resets all fields. \"Key At Playhead\" writes all values as a single keyframe.",
            buildSidebarMatrixInspector());
        sidebarColorMatrixCard = buildSidebarCard("Color Matrix",
            "A 4×5 RGBA color transform matrix applied per-pixel to the entity.\n\n" +
            "Each row targets an output channel (R, G, B, A). Each row of 5 values computes:\n" +
            "  out = m_0·R + m_1·G + m_2·B + m_3·A + m_4\n\n" +
            "The fifth column (m_4) is a constant offset added to the channel (0–1 range).\n\n" +
            "Identity: diagonal = 1, all other values = 0.\n" +
            "\"Fill Identity\" resets to no color transformation. \"Key At Playhead\" writes all 20 values.",
            buildSidebarColorMatrixInspector());
        sidebarCameraDofCard = buildSidebarCard("Camera DOF",
            "Depth of Field applies a focus-blur effect to the scene camera.\n\n" +
            "• Focus — Z depth of the focal plane; entities at this depth appear sharp\n" +
            "• Strength — defocus rate for entities moving away from the focal plane\n" +
            "• Max Blur — upper limit on the blur radius applied to out-of-focus entities\n\n" +
            "Set Strength to 0 to disable DOF. \"Fill Neutral\" resets all values to no-blur defaults.",
            buildSidebarCameraDofInspector());
        sidebarCustomChannelsCard = buildSidebarCard("Custom Channels",
            "Key arbitrary numeric properties beyond the standard property types.\n\n" +
            "• Enter a dot-separated key (e.g. my.effect.intensity) to create a freeform channel.\n" +
            "• Known engine keys (color.m00, dof.focus, etc.) appear in the dropdown for quick access.\n" +
            "• Dedicated matrix and color matrix keys are mirrored by the inspectors above.\n\n" +
            "Values are keyed at the current playhead using the selected easing and interpolation.",
            buildSidebarCustomChannelInspector());

        ScrollPane content = buildSidebarTabContent(
            buildSidebarCard(
                "Selection",
                "Shows the current editing context in the timeline.\n\n" +
                "• Target — the entity or group whose track is active\n" +
                "• Scope — whether the full entity or a single property track is selected\n" +
                "• Property — the currently active property type (X, Rotation, Alpha, etc.)\n" +
                "• Playhead — current time position in milliseconds\n" +
                "• Selected Keyframes — count of highlighted keyframes in the timeline",
                buildSidebarInfoBlock("Target", lblSidebarSelectionTarget),
                buildSidebarInfoBlock("Scope", lblSidebarSelectionScope),
                buildSidebarInfoBlock("Property", lblSidebarSelectionProperty),
                buildSidebarInfoBlock("Playhead", lblSidebarSelectionPlayhead),
                buildSidebarInfoBlock("Selected Keyframes", lblSidebarSelectionCount)
            ),
            buildSidebarCard(
                "Actions",
                "Keyframe actions for the current selection.\n\n" +
                "• Add Keyframe — inserts a keyframe at the playhead for the selected entity and property\n" +
                "• Expression — switches a character to a layered expression preset at the playhead\n" +
                "• Focus Timeline — zooms the timeline to fit all selected keyframes on screen\n" +
                "• Prev / Next Key — moves the playhead to the nearest keyframe on either side\n" +
                "• Clear — deselects all entities and keyframes",
                selectionActionsPrimary,
                selectionActionsSecondary
            ),
            sidebarAdvancedUnavailableCard,
            sidebarMatrixCard,
            sidebarColorMatrixCard,
            sidebarCameraDofCard,
            sidebarCustomChannelsCard
        );
        Tab tab = new Tab("Selection", content);
        refreshSidebarTabs();
        return tab;
    }

    private Tab buildSceneTab() {
        lblSidebarSceneTracks = buildSidebarValueLabel();
        lblSidebarSceneGroups = buildSidebarValueLabel();
        lblSidebarSceneDuration = buildSidebarValueLabel();
        lblSidebarSceneViewport = buildSidebarValueLabel();
        lblSidebarSceneCamera = buildSidebarValueLabel();
        lblSidebarSceneCodePane = buildSidebarValueLabel();
        lblSidebarSceneAnchors = buildSidebarValueLabel();
        lblSidebarSceneStage = buildSidebarValueLabel();

        Button btnFitPreview = buildSidebarActionButton("Fit Preview", () -> {
            animationPreview.fitToContent();
            refreshSidebarTabs();
        }, "Fit the preview viewport to show all scene content");
        btnSidebarPreviewLayout = buildSidebarActionButton("Focus Preview", this::togglePreviewFocusMode, "Toggle between workspace and focused preview layout");
        btnSidebarCodePane = buildSidebarActionButton("Hide Code Pane", () -> {
            setCodePaneVisible(!isCodePaneVisible());
            refreshSidebarTabs();
        }, "Toggle visibility of the VNS code preview pane");
        Button btnRefreshCode = buildSidebarActionButton("Refresh Code", this::refreshExportPreview, "Regenerate the VNS code from the current timeline");
        btnRefreshCode.setGraphic(RefreshIcon.compact());

        javafx.scene.control.CheckBox cbExportNested = new javafx.scene.control.CheckBox("Export Nested Groups");
        cbExportNested.setStyle("-fx-text-fill: #a0aabf; -fx-font-family: \"Inter\", sans-serif; -fx-font-size: 11px;");
        cbExportNested.setSelected(exportNestedBlocks);
        cbExportNested.setOnAction(e -> {
            exportNestedBlocks = cbExportNested.isSelected();
            refreshExportPreview();
        });

        HBox previewActions = buildSidebarButtonRow(btnFitPreview, btnSidebarPreviewLayout);
        HBox workspaceActions = new HBox(4, btnSidebarCodePane, btnRefreshCode, cbExportNested);
        workspaceActions.setAlignment(Pos.CENTER_LEFT);

        ScrollPane content = buildSidebarTabContent(
            buildSidebarCard(
                "Project",
                "Overview of the current animation project.\n\n" +
                "• Tracks — total entity tracks in the timeline\n" +
                "• Groups — entity groups for coordinated multi-track animation\n" +
                "• Duration — total timeline length in milliseconds\n" +
                "• Orbit Anchors — named pivot points used by the orbit animation tool\n" +
                "• Lighting Stage — the VN stage preset loaded for lighting reference",
                buildSidebarInfoBlock("Tracks", lblSidebarSceneTracks),
                buildSidebarInfoBlock("Groups", lblSidebarSceneGroups),
                buildSidebarInfoBlock("Duration", lblSidebarSceneDuration),
                buildSidebarInfoBlock("Orbit Anchors", lblSidebarSceneAnchors),
                buildSidebarInfoBlock("Lighting Stage", lblSidebarSceneStage)
            ),
            buildSidebarCard(
                "Preview",
                "Live preview viewport state and layout controls.\n\n" +
                "• Viewport — current canvas resolution and pan/zoom offset\n" +
                "• Camera — whether a runtime camera track is active\n" +
                "• Code Pane — whether the JES export panel is currently shown\n\n" +
                "\"Fit Preview\" resets zoom to show all scene content.\n" +
                "\"Focus Preview\" switches to a full-screen preview layout, hiding the timeline.\n" +
                "\"Hide/Show Code Pane\" toggles the right-side JES output panel.\n" +
                "\"Refresh Code\" regenerates JES from the current timeline state.",
                buildSidebarInfoBlock("Viewport", lblSidebarSceneViewport),
                buildSidebarInfoBlock("Camera", lblSidebarSceneCamera),
                buildSidebarInfoBlock("Code Pane", lblSidebarSceneCodePane),
                previewActions,
                workspaceActions
            )
        );
        Tab tab = new Tab("Scene", content);
        refreshSidebarTabs();
        return tab;
    }

    private Node buildSidebarMatrixInspector() {
        String[] captions = {"MXX", "MXY", "MYX", "MYY", "TX", "TY", "Blur", "Brightness"};
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        for (int i = 0; i < captions.length; i++) {
            Label label = new Label(captions[i]);
            label.setStyle(STYLE_SIDEBAR_META_LABEL);
            TextField field = buildSidebarNumberField(captions[i].toLowerCase(Locale.ROOT));
            sidebarMatrixFields[i] = field;
            int row = i / 2;
            int col = (i % 2) * 2;
            grid.add(label, col, row);
            grid.add(field, col + 1, row);
        }

        Button btnFillIdentity = buildSidebarActionButton("Fill Identity", this::fillSidebarMatrixIdentity, "Reset the matrix to identity (no transformation)");
        Button btnKeyMatrix = buildSidebarActionButton("Key At Playhead", this::applySidebarMatrixKeyframes, "Key the current matrix values at the playhead");
        return new VBox(8, grid, buildSidebarButtonRow(btnFillIdentity, btnKeyMatrix));
    }

    private Node buildSidebarColorMatrixInspector() {
        VBox body = new VBox(8);
        lblSidebarAdvancedHint = createSidebarHintLabel("RGBA 4x5 matrix. Values are keyed as dedicated color channels at the current playhead.");
        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        String[] rowLabels = {"R", "G", "B", "A"};
        for (int row = 0; row < 4; row++) {
            Label rowLabel = new Label(rowLabels[row]);
            rowLabel.setStyle(STYLE_SIDEBAR_META_LABEL);
            grid.add(rowLabel, 0, row);
            for (int col = 0; col < 5; col++) {
                TextField field = buildSidebarCompactNumberField("m" + row + col);
                sidebarColorMatrixFields[row * 5 + col] = field;
                grid.add(field, col + 1, row);
            }
        }
        Button btnFillIdentity = buildSidebarActionButton("Fill Identity", this::fillSidebarColorMatrixIdentity, "Reset the color matrix to identity (no color transformation)");
        Button btnKeyColors = buildSidebarActionButton("Key At Playhead", this::applySidebarColorMatrixKeyframes, "Key the current color matrix values at the playhead");
        body.getChildren().addAll(lblSidebarAdvancedHint, grid, buildSidebarButtonRow(btnFillIdentity, btnKeyColors));
        return body;
    }

    private Node buildSidebarCameraDofInspector() {
        String[] captions = {"Focus", "Strength", "Max Blur"};
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        for (int i = 0; i < captions.length; i++) {
            Label label = new Label(captions[i]);
            label.setStyle(STYLE_SIDEBAR_META_LABEL);
            TextField field = buildSidebarNumberField(captions[i].toLowerCase(Locale.ROOT));
            sidebarCameraDofFields[i] = field;
            grid.add(label, 0, i);
            grid.add(field, 1, i);
        }
        Button btnReset = buildSidebarActionButton("Fill Neutral", this::fillSidebarDofNeutral, "Reset DOF settings to neutral (no blur)");
        Button btnKey = buildSidebarActionButton("Key At Playhead", this::applySidebarDofKeyframes, "Key the current DOF values at the playhead");
        return new VBox(8, grid, buildSidebarButtonRow(btnReset, btnKey));
    }

    private Node buildSidebarCustomChannelInspector() {
        cbSidebarCustomPropertyKey = new ComboBox<>();
        cbSidebarCustomPropertyKey.setEditable(true);
        cbSidebarCustomPropertyKey.setMaxWidth(Double.MAX_VALUE);
        cbSidebarCustomPropertyKey.setStyle(STYLE_TEXT_FIELD);
        cbSidebarCustomPropertyKey.setPromptText("property.key");
        cbSidebarCustomPropertyKey.setTooltip(new Tooltip("Registered or freeform numeric channel key"));
        cbSidebarCustomPropertyKey.getEditor().setStyle(STYLE_TEXT_FIELD);
        cbSidebarCustomPropertyKey.setOnAction(event -> refreshSidebarCustomPropertyValue());
        cbSidebarCustomPropertyKey.getEditor().textProperty().addListener((obs, oldValue, newValue) -> refreshSidebarCustomPropertyValue());

        tfSidebarCustomPropertyValue = buildSidebarNumberField("value");

        Label lblKey = new Label("Key");
        lblKey.setStyle(STYLE_SIDEBAR_META_LABEL);
        Label lblValue = new Label("Value");
        lblValue.setStyle(STYLE_SIDEBAR_META_LABEL);
        Button btnKey = buildSidebarActionButton("Key At Playhead", this::applySidebarCustomPropertyKeyframe, "Key the custom property value at the playhead");
        Button btnRemove = buildSidebarActionButton("Remove Key", this::removeSidebarCustomPropertyKeyframe, "Remove the keyframe for this custom property at the playhead");
        VBox body = new VBox(6,
            lblKey,
            cbSidebarCustomPropertyKey,
            lblValue,
            tfSidebarCustomPropertyValue,
            createSidebarHintLabel("Uses the engine registry for known numeric channels, but also accepts freeform keys."),
            buildSidebarButtonRow(btnKey, btnRemove));
        body.setFillWidth(true);
        return body;
    }

    private ScrollPane buildSidebarTabContent(Node... content) {
        VBox body = new VBox(10);
        body.setPadding(new Insets(8));
        body.setFillWidth(true);
        body.setMinWidth(0);
        if (content != null) {
            body.getChildren().addAll(content);
        }

        ScrollPane scrollPane = new ScrollPane(body);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setPannable(false);
        scrollPane.setMinWidth(0);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        return scrollPane;
    }

    private TextField buildSidebarNumberField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setStyle(STYLE_TEXT_FIELD);
        field.setPrefWidth(90);
        return field;
    }

    private TextField buildSidebarCompactNumberField(String prompt) {
        TextField field = buildSidebarNumberField(prompt);
        field.setPrefWidth(48);
        field.setMaxWidth(54);
        return field;
    }

    private Label createSidebarHintLabel(String text) {
        Label label = new Label(text);
        label.setStyle(STYLE_SIDEBAR_META_LABEL + " -fx-font-weight: normal;");
        label.setWrapText(true);
        return label;
    }

    private VBox buildSidebarCard(String title, Node... content) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle(STYLE_SIDEBAR_CARD_TITLE);

        VBox card = new VBox(8);
        card.setStyle(STYLE_SIDEBAR_CARD);
        card.setMinWidth(0);
        card.getChildren().add(titleLabel);
        if (content != null) {
            card.getChildren().addAll(content);
        }
        return card;
    }

    private VBox buildSidebarCard(String title, String helpText, Node... content) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle(STYLE_SIDEBAR_CARD_TITLE);
        HBox titleRow = new HBox(4, titleLabel, SidebarToolHelp.button(this, title, helpText));
        titleRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8);
        card.setStyle(STYLE_SIDEBAR_CARD);
        card.setMinWidth(0);
        card.getChildren().add(titleRow);
        if (content != null) {
            card.getChildren().addAll(content);
        }
        return card;
    }

    private VBox buildSidebarInfoBlock(String title, Label valueLabel) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle(STYLE_SIDEBAR_META_LABEL);
        VBox block = new VBox(2, titleLabel, valueLabel);
        block.setMinWidth(0);
        return block;
    }

    private Label buildSidebarValueLabel() {
        Label label = new Label("-");
        label.setStyle(STYLE_SIDEBAR_VALUE_LABEL);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private Button buildSidebarActionButton(String text, Runnable action) {
        return buildSidebarActionButton(text, action, null);
    }

    private Button buildSidebarActionButton(String text, Runnable action, String tooltip) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle(STYLE_BTN_DARK);
        if (tooltip != null && !tooltip.isBlank()) {
            button.setTooltip(new Tooltip(tooltip));
        }
        button.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
            event.consume();
        });
        return button;
    }

    private HBox buildSidebarButtonRow(Button... buttons) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        if (buttons != null) {
            for (Button button : buttons) {
                if (button == null) continue;
                HBox.setHgrow(button, Priority.ALWAYS);
                row.getChildren().add(button);
            }
        }
        return row;
    }

    private void refreshExpressionPreviewPanel(String name, boolean isGroup) {
        if (expressionPreviewPanel == null) return;
        if (isGroup || name == null || name.isBlank()) {
            expressionPreviewPanel.setCharacterContext(null, List.of(), null);
            expressionPreviewPanel.setDisable(true);
            animationPreview.setExpressionPreviewIndicator(null, null, false);
            return;
        }
        String characterId = inferCharacterIdFromSelection(name);
        List<String> expressionNames = expressionSuggestionsForTarget(name);
        String currentExpression = preferredExpressionForTarget(name);
        expressionPreviewPanel.setDisable(false);
        expressionPreviewPanel.setCharacterContext(characterId, expressionNames, currentExpression);
        animationPreview.setExpressionPreviewIndicator(name, currentExpression, false);
    }

    public void refreshSidebarTabs() {
        String selectedName = timelinePanel != null ? timelinePanel.getSelectedEntity() : null;
        boolean selectedGroup = timelinePanel != null && timelinePanel.isSelectedGroup();
        boolean runtimeCamera = timelinePanel != null && timelinePanel.isRuntimeCameraSelected();
        PropertyType selectedProperty = timelinePanel != null ? timelinePanel.getSelectedProperty() : null;
        int selectionCount = timelinePanel != null ? timelinePanel.getSelectionCount() : 0;
        boolean hasTarget = selectedName != null && !selectedName.isBlank();

        if (lblSidebarSelectionTarget != null) {
            lblSidebarSelectionTarget.setText(hasTarget ? selectionLabel(selectedName, selectedGroup) : "No target");
        }
        if (lblSidebarSelectionScope != null) {
            lblSidebarSelectionScope.setText(
                selectionCount > 1
                    ? "Multi-keyframe selection"
                    : timelinePanel != null && timelinePanel.isRuntimeCameraSelected()
                        ? "Runtime camera track"
                    : selectedGroup
                        ? "Group track"
                        : hasTarget
                            ? "Entity track"
                            : "Nothing selected");
        }
        if (lblSidebarSelectionProperty != null) {
            lblSidebarSelectionProperty.setText(selectedProperty != null ? selectedProperty.getDisplayName() : "None");
        }
        if (lblSidebarSelectionPlayhead != null) {
            lblSidebarSelectionPlayhead.setText(String.format("%.0f ms", project.getPlayheadMs()));
        }
        if (lblSidebarSelectionCount != null) {
            lblSidebarSelectionCount.setText(String.valueOf(selectionCount));
        }
        if (btnSidebarAddKeyframe != null) {
            btnSidebarAddKeyframe.setDisable(!hasTarget);
        }
        if (btnSidebarAddExpressionKeyframe != null) {
            btnSidebarAddExpressionKeyframe.setDisable(!hasTarget || selectedGroup || runtimeCamera);
        }
        if (btnSidebarFocusSelection != null) {
            btnSidebarFocusSelection.setDisable(!hasTarget);
        }
        if (btnSidebarClearSelection != null) {
            btnSidebarClearSelection.setDisable(!hasTarget && selectionCount == 0);
        }

        if (lblSidebarSceneTracks != null) {
            lblSidebarSceneTracks.setText(String.valueOf(countItems(project.getTracks())));
        }
        if (lblSidebarSceneGroups != null) {
            lblSidebarSceneGroups.setText(String.valueOf(countItems(project.getGroups())));
        }
        if (lblSidebarSceneDuration != null) {
            lblSidebarSceneDuration.setText(String.format("%.0f ms", project.getTotalDurationMs()));
        }
        if (lblSidebarSceneAnchors != null) {
            lblSidebarSceneAnchors.setText(String.valueOf(project.getOrbitAnchorsView().size()));
        }
        if (lblSidebarSceneStage != null) {
            lblSidebarSceneStage.setText(describeStageContext(project.getStageContext()));
        }
        if (lblSidebarSceneViewport != null) {
            ProjectViewportSpec.Dimensions viewport = ProjectViewportSpec.resolve(projectRoot);
            lblSidebarSceneViewport.setText(viewport.width() + " x " + viewport.height());
        }
        if (lblSidebarSceneCamera != null) {
            var camera = animationPreview.getCamera();
            lblSidebarSceneCamera.setText(String.format("X %.1f  Y %.1f  Z %.2f", camera.getX(), camera.getY(), camera.getZoom()));
        }
        if (lblSidebarSceneCodePane != null) {
            lblSidebarSceneCodePane.setText(isCodePaneVisible() ? "Visible" : "Hidden");
        }
        if (btnSidebarPreviewLayout != null) {
            btnSidebarPreviewLayout.setText(previewFocusMode ? "Back to Workspace" : "Focus Preview");
            btnSidebarPreviewLayout.setDisable(scene == null && !previewFocusMode);
        }
        if (btnSidebarCodePane != null) {
            btnSidebarCodePane.setText(isCodePaneVisible() ? "Hide Code Pane" : "Show Code Pane");
        }
        if (constraintEditor != null) {
            constraintEditor.selectEntity(hasTarget && !selectedGroup && !runtimeCamera ? selectedName : null);
        }
        refreshSidebarAdvancedPanels(selectedName, selectedGroup, runtimeCamera, hasTarget);
        refreshToolbarCommandSummary();
    }

    private void refreshSidebarAdvancedPanels(
        String selectedName,
        boolean selectedGroup,
        boolean runtimeCamera,
        boolean hasTarget
    ) {
        boolean entityTrack = hasTarget && !selectedGroup && !runtimeCamera;
        boolean cameraTrack = hasTarget && runtimeCamera;

        setSidebarCardVisible(sidebarAdvancedUnavailableCard, !entityTrack && !cameraTrack);
        setSidebarCardVisible(sidebarMatrixCard, entityTrack);
        setSidebarCardVisible(sidebarColorMatrixCard, entityTrack);
        setSidebarCardVisible(sidebarCameraDofCard, cameraTrack);
        setSidebarCardVisible(sidebarCustomChannelsCard, entityTrack || cameraTrack);

        if (sidebarAdvancedUnavailableCard != null && !entityTrack && !cameraTrack && sidebarAdvancedUnavailableCard.getChildren().size() > 1) {
            Node messageNode = sidebarAdvancedUnavailableCard.getChildren().get(1);
            if (messageNode instanceof Label label) {
                label.setText(
                    selectedGroup
                        ? "Advanced authoring is available on entity tracks and the runtime camera track, not on group selections."
                        : "Select an entity or the runtime camera to author matrix, color, DOF, and custom numeric channels."
                );
            }
        }

        if (entityTrack) {
            refreshSidebarEntityAdvancedFields(selectedName);
        } else if (cameraTrack) {
            refreshSidebarCameraAdvancedFields(selectedName);
        } else {
            refreshSidebarCustomPropertyOptions(false, false, null);
        }
    }

    private void refreshSidebarEntityAdvancedFields(String entityName) {
        EntityTrack track = entityName != null ? project.getTrack(entityName) : null;
        com.jvn.core.scene2d.Entity2D entity = scene != null && entityName != null ? scene.find(entityName) : null;
        double timeMs = project.getPlayheadMs();

        for (int i = 0; i < ENTITY_MATRIX_PROPERTY_CHOICES.size() && i < sidebarMatrixFields.length; i++) {
            PropertyType property = ENTITY_MATRIX_PROPERTY_CHOICES.get(i);
            double fallback = fallbackPropertyValue(entity, property);
            double value = track != null && track.hasKeyframes(property)
                ? track.getValueAt(property, timeMs)
                : fallback;
            setSidebarFieldValue(sidebarMatrixFields[i], value);
        }

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                int index = row * 5 + col;
                String propertyKey = "color.m" + row + col;
                double fallback = entity != null
                    ? entity.readCustomProperty(propertyKey)
                    : defaultCustomPropertyValue(false, propertyKey);
                double value = track != null && track.hasCustomKeyframes(propertyKey)
                    ? track.getCustomValueAt(propertyKey, timeMs, fallback)
                    : fallback;
                setSidebarFieldValue(sidebarColorMatrixFields[index], value);
            }
        }

        refreshSidebarCustomPropertyOptions(true, false, track);
    }

    private void refreshSidebarCameraAdvancedFields(String targetName) {
        EntityTrack track = resolveRuntimeCameraTrack(false);
        var camera = animationPreview.getCamera();
        double timeMs = project.getPlayheadMs();
        PropertyType[] properties = {
            PropertyType.CAMERA_DOF_FOCUS,
            PropertyType.CAMERA_DOF_STRENGTH,
            PropertyType.CAMERA_DOF_MAX_BLUR
        };
        double[] fallbackValues = {
            camera != null ? camera.getFocusDepth() : PropertyType.CAMERA_DOF_FOCUS.getDefaultValue(),
            camera != null ? camera.getDepthOfFieldStrength() : PropertyType.CAMERA_DOF_STRENGTH.getDefaultValue(),
            camera != null ? camera.getDepthOfFieldMaxBlur() : PropertyType.CAMERA_DOF_MAX_BLUR.getDefaultValue()
        };
        for (int i = 0; i < properties.length && i < sidebarCameraDofFields.length; i++) {
            double value = track != null && track.hasKeyframes(properties[i])
                ? track.getValueAt(properties[i], timeMs)
                : fallbackValues[i];
            setSidebarFieldValue(sidebarCameraDofFields[i], value);
        }

        refreshSidebarCustomPropertyOptions(false, TimelinePanel.isRuntimeCameraTarget(targetName), track);
    }

    private void refreshSidebarCustomPropertyOptions(boolean entityTrack, boolean cameraTrack, EntityTrack track) {
        if (cbSidebarCustomPropertyKey == null) return;
        Set<String> options = new LinkedHashSet<>();
        if (entityTrack) {
            for (var definition : com.jvn.core.scene2d.Entity2D.animatableProperties()) {
                if (definition == null) continue;
                String key = definition.getKey();
                if (key != null && !ENTITY_DEDICATED_CUSTOM_KEYS.contains(key)) {
                    options.add(key);
                }
            }
        } else if (cameraTrack) {
            for (var definition : com.jvn.core.graphics.Camera2D.animatableProperties()) {
                if (definition == null) continue;
                String key = definition.getKey();
                if (key != null && !CAMERA_DEDICATED_CUSTOM_KEYS.contains(key)) {
                    options.add(key);
                }
            }
        }
        if (track != null) {
            for (String key : track.getAnimatedCustomProperties()) {
                if (key == null || key.isBlank()) continue;
                if (entityTrack && ENTITY_DEDICATED_CUSTOM_KEYS.contains(key)) continue;
                if (cameraTrack && CAMERA_DEDICATED_CUSTOM_KEYS.contains(key)) continue;
                options.add(key);
            }
        }

        String currentKey = readSidebarCustomPropertyKey();
        cbSidebarCustomPropertyKey.getItems().setAll(options);
        if (!cameraTrack && !entityTrack) {
            if (!cbSidebarCustomPropertyKey.isFocused() && !cbSidebarCustomPropertyKey.getEditor().isFocused()) {
                cbSidebarCustomPropertyKey.setValue(null);
                cbSidebarCustomPropertyKey.getEditor().clear();
            }
            clearSidebarFieldValue(tfSidebarCustomPropertyValue);
            return;
        }

        if ((currentKey == null || currentKey.isBlank()) && !options.isEmpty()) {
            currentKey = cbSidebarCustomPropertyKey.getItems().get(0);
        }
        if (currentKey != null && !currentKey.isBlank()
            && !cbSidebarCustomPropertyKey.isFocused()
            && !cbSidebarCustomPropertyKey.getEditor().isFocused()) {
            cbSidebarCustomPropertyKey.setValue(currentKey);
            cbSidebarCustomPropertyKey.getEditor().setText(currentKey);
        }
        refreshSidebarCustomPropertyValue();
    }

    private void refreshSidebarCustomPropertyValue() {
        if (tfSidebarCustomPropertyValue == null) return;
        String propertyKey = readSidebarCustomPropertyKey();
        if (propertyKey == null || propertyKey.isBlank()) {
            clearSidebarFieldValue(tfSidebarCustomPropertyValue);
            return;
        }

        EntityTrack track = selectedTrackForEditing(false);
        double timeMs = project.getPlayheadMs();
        String targetName = timelinePanel != null ? timelinePanel.getSelectedEntity() : null;
        boolean runtimeCamera = timelinePanel != null && timelinePanel.isRuntimeCameraSelected();
        double value;

        PropertyType mapped = PropertyType.fromTimelineCustomKey(propertyKey);
        if (mapped != null && track != null) {
            double fallback = runtimeCamera
                ? defaultCustomPropertyValue(true, propertyKey)
                : defaultCustomPropertyValue(false, propertyKey);
            value = track.hasKeyframes(mapped) ? track.getValueAt(mapped, timeMs) : fallback;
        } else if (runtimeCamera) {
            var camera = animationPreview.getCamera();
            double fallback = baselineCustomPropertyValue(targetName, propertyKey, camera);
            value = track != null && track.hasCustomKeyframes(propertyKey)
                ? track.getCustomValueAt(propertyKey, timeMs, fallback)
                : fallback;
        } else {
            com.jvn.core.scene2d.Entity2D entity = scene != null && targetName != null ? scene.find(targetName) : null;
            double fallback = baselineCustomPropertyValue(targetName, propertyKey, entity);
            value = track != null && track.hasCustomKeyframes(propertyKey)
                ? track.getCustomValueAt(propertyKey, timeMs, fallback)
                : fallback;
        }
        setSidebarFieldValue(tfSidebarCustomPropertyValue, value);
    }

    private void fillSidebarMatrixIdentity() {
        double[] values = {1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0};
        for (int i = 0; i < values.length && i < sidebarMatrixFields.length; i++) {
            forceSidebarFieldValue(sidebarMatrixFields[i], values[i]);
        }
    }

    private void applySidebarMatrixKeyframes() {
        String entityName = timelinePanel != null ? timelinePanel.getSelectedEntity() : null;
        if (entityName == null || entityName.isBlank() || timelinePanel == null || timelinePanel.isSelectedGroup()
            || timelinePanel.isRuntimeCameraSelected()) {
            return;
        }
        EntityTrack track = project.getOrCreateTrack(entityName);
        com.jvn.core.scene2d.Entity2D entity = scene != null ? scene.find(entityName) : null;
        double timeMs = project.getPlayheadMs();
        List<PuppeteerCommand> commands = new ArrayList<>();
        for (int i = 0; i < ENTITY_MATRIX_PROPERTY_CHOICES.size() && i < sidebarMatrixFields.length; i++) {
            PropertyType property = ENTITY_MATRIX_PROPERTY_CHOICES.get(i);
            double fallback = fallbackPropertyValue(entity, property);
            double value = parseSidebarFieldValue(sidebarMatrixFields[i], fallback);
            commands.add(PuppeteerCommand.upsertKeyframe(track, property, timeMs, value));
        }
        executeSidebarCommand("Key matrix/effect properties", commands);
    }

    private void fillSidebarColorMatrixIdentity() {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                int index = row * 5 + col;
                double value = col == row ? 1.0 : 0.0;
                if (col == 4) value = 0.0;
                forceSidebarFieldValue(sidebarColorMatrixFields[index], value);
            }
        }
    }

    private void applySidebarColorMatrixKeyframes() {
        String entityName = timelinePanel != null ? timelinePanel.getSelectedEntity() : null;
        if (entityName == null || entityName.isBlank() || timelinePanel == null || timelinePanel.isSelectedGroup()
            || timelinePanel.isRuntimeCameraSelected()) {
            return;
        }
        EntityTrack track = project.getOrCreateTrack(entityName);
        com.jvn.core.scene2d.Entity2D entity = scene != null ? scene.find(entityName) : null;
        double timeMs = project.getPlayheadMs();
        List<PuppeteerCommand> commands = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                int index = row * 5 + col;
                String propertyKey = "color.m" + row + col;
                double fallback = entity != null
                    ? entity.readCustomProperty(propertyKey)
                    : defaultCustomPropertyValue(false, propertyKey);
                double value = parseSidebarFieldValue(sidebarColorMatrixFields[index], fallback);
                commands.add(PuppeteerCommand.upsertCustomKeyframe(track, propertyKey, timeMs, value));
            }
        }
        executeSidebarCommand("Key color matrix", commands);
    }

    private void fillSidebarDofNeutral() {
        for (TextField field : sidebarCameraDofFields) {
            forceSidebarFieldValue(field, 0.0);
        }
    }

    private void applySidebarDofKeyframes() {
        if (timelinePanel == null || !timelinePanel.isRuntimeCameraSelected()) return;
        EntityTrack track = resolveRuntimeCameraTrack(true);
        double timeMs = project.getPlayheadMs();
        PropertyType[] properties = {
            PropertyType.CAMERA_DOF_FOCUS,
            PropertyType.CAMERA_DOF_STRENGTH,
            PropertyType.CAMERA_DOF_MAX_BLUR
        };
        double[] fallbackValues = {
            animationPreview.getCamera().getFocusDepth(),
            animationPreview.getCamera().getDepthOfFieldStrength(),
            animationPreview.getCamera().getDepthOfFieldMaxBlur()
        };
        List<PuppeteerCommand> commands = new ArrayList<>();
        for (int i = 0; i < properties.length && i < sidebarCameraDofFields.length; i++) {
            double value = parseSidebarFieldValue(sidebarCameraDofFields[i], fallbackValues[i]);
            commands.add(PuppeteerCommand.upsertKeyframe(track, properties[i], timeMs, value));
        }
        executeSidebarCommand("Key DOF properties", commands);
    }

    private void applySidebarCustomPropertyKeyframe() {
        EntityTrack track = selectedTrackForEditing(true);
        if (track == null) return;
        String propertyKey = readSidebarCustomPropertyKey();
        if (propertyKey == null || propertyKey.isBlank()) return;

        double timeMs = project.getPlayheadMs();
        boolean runtimeCamera = timelinePanel != null && timelinePanel.isRuntimeCameraSelected();
        double fallback = resolveSidebarCustomFallback(propertyKey, runtimeCamera);
        double value = parseSidebarFieldValue(tfSidebarCustomPropertyValue, fallback);
        PropertyType mapped = PropertyType.fromTimelineCustomKey(propertyKey);
        PuppeteerCommand command = mapped != null
            ? PuppeteerCommand.upsertKeyframe(track, mapped, timeMs, value)
            : PuppeteerCommand.upsertCustomKeyframe(track, propertyKey, timeMs, value);
        executeSidebarCommand(command);
    }

    private void removeSidebarCustomPropertyKeyframe() {
        EntityTrack track = selectedTrackForEditing(false);
        if (track == null) return;
        String propertyKey = readSidebarCustomPropertyKey();
        if (propertyKey == null || propertyKey.isBlank()) return;

        double timeMs = project.getPlayheadMs();
        PropertyType mapped = PropertyType.fromTimelineCustomKey(propertyKey);
        if (mapped != null) {
            Keyframe existing = track.findKeyframeAt(mapped, timeMs);
            if (existing == null) return;
            executeSidebarCommand(PuppeteerCommand.removeKeyframe(track, mapped, existing));
            return;
        }
        executeSidebarCommand(PuppeteerCommand.removeCustomKeyframeAt(track, propertyKey, timeMs));
    }

    private void executeSidebarCommand(String description, List<PuppeteerCommand> commands) {
        if (commands == null || commands.isEmpty()) return;
        executeSidebarCommand(PuppeteerCommand.composite(description, commands));
    }

    private void executeSidebarCommand(PuppeteerCommand command) {
        if (command == null) return;
        commandStack.execute(command);
        timelinePanel.refresh();
        updatePreview();
        refreshExportPreviewAndMarkDirty();
    }

    private String readSidebarCustomPropertyKey() {
        if (cbSidebarCustomPropertyKey == null) return null;
        String key = cbSidebarCustomPropertyKey.getEditor() != null
            ? cbSidebarCustomPropertyKey.getEditor().getText()
            : null;
        if ((key == null || key.isBlank()) && cbSidebarCustomPropertyKey.getValue() != null) {
            key = cbSidebarCustomPropertyKey.getValue();
        }
        return key != null ? key.trim() : null;
    }

    private double resolveSidebarCustomFallback(String propertyKey, boolean runtimeCamera) {
        String targetName = timelinePanel != null ? timelinePanel.getSelectedEntity() : null;
        if (runtimeCamera) {
            return baselineCustomPropertyValue(targetName, propertyKey, animationPreview.getCamera());
        }
        com.jvn.core.scene2d.Entity2D entity = scene != null && targetName != null ? scene.find(targetName) : null;
        return baselineCustomPropertyValue(targetName, propertyKey, entity);
    }

    private double defaultCustomPropertyValue(boolean camera, String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) return 0.0;
        if (camera) {
            var definition = com.jvn.core.graphics.Camera2D.getAnimatableProperty(propertyKey);
            return definition != null ? definition.getDefaultValue() : 0.0;
        }
        var definition = com.jvn.core.scene2d.Entity2D.getAnimatableProperty(propertyKey);
        return definition != null ? definition.getDefaultValue() : 0.0;
    }

    private double parseSidebarFieldValue(TextField field, double fallback) {
        if (field == null) return fallback;
        String text = field.getText();
        if (text == null || text.isBlank()) return fallback;
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void setSidebarFieldValue(TextField field, double value) {
        if (field == null || field.isFocused()) return;
        field.setText(formatSidebarNumber(value));
    }

    private void forceSidebarFieldValue(TextField field, double value) {
        if (field == null) return;
        field.setText(formatSidebarNumber(value));
    }

    private void clearSidebarFieldValue(TextField field) {
        if (field == null || field.isFocused()) return;
        field.clear();
    }

    private String formatSidebarNumber(double value) {
        if (!Double.isFinite(value)) return "0";
        String text = String.format(Locale.ROOT, "%.3f", value);
        while (text.contains(".") && text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return "-0".equals(text) ? "0" : text;
    }

    private void setSidebarCardVisible(VBox card, boolean visible) {
        if (card == null) return;
        card.setVisible(visible);
        card.setManaged(visible);
    }

    private int countItems(Iterable<?> items) {
        if (items == null) return 0;
        int count = 0;
        for (Object ignored : items) {
            count++;
        }
        return count;
    }

    private String describeStageContext(AnimationProject.StageContext stage) {
        if (stage == null || !stage.isPresent()) return "None";
        StringBuilder sb = new StringBuilder(stage.presetId());
        List<String> counts = new ArrayList<>();
        if (stage.lightCount() > 0) counts.add(stage.lightCount() + " lights");
        if (stage.occluderCount() > 0) counts.add(stage.occluderCount() + " occluders");
        if (stage.responseZoneCount() > 0) counts.add(stage.responseZoneCount() + " zones");
        if (!counts.isEmpty()) {
            sb.append(" (").append(String.join(", ", counts)).append(")");
        }
        if (!stage.sourcePath().isBlank()) {
            sb.append("\n").append(stage.sourcePath());
        }
        return sb.toString();
    }

    private void applyLinuxDefaultWindowState() {
        if (!isLinux()) return;
        setIconified(false);
        setMaximized(true);
        Platform.runLater(() -> {
            setIconified(false);
            setMaximized(true);
        });
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    public void setToolbarClustersExpanded(boolean expanded) {
        for (CollapsibleToolbarCluster cluster : toolbarClusters.values()) {
            cluster.setExpanded(expanded);
        }
    }

    public void setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode mode) {
        AnimatedToolbarPane.LayoutMode resolved = mode != null
            ? mode
            : AnimatedToolbarPane.LayoutMode.DYNAMIC;
        if (toolbarPane != null) {
            toolbarPane.setLayoutMode(resolved);
            toolbarPane.setPadding(resolved == AnimatedToolbarPane.LayoutMode.COMPACT
                ? TOOLBAR_PADDING_COMPACT
                : TOOLBAR_PADDING_DYNAMIC);
        }
        applyToolbarChromeDensity(resolved);
        applyToolbarDensity(resolved);
        refreshToolbarCommandSummary();
        if (workspacePrefs != null) {
            workspacePrefs.setString(PuppeteerWorkspacePrefs.KEY_TOOLBAR_LAYOUT_MODE, resolved.name());
            workspacePrefs.save();
        }
    }

    public AnimatedToolbarPane.LayoutMode getToolbarLayoutMode() {
        return toolbarPane != null ? toolbarPane.getLayoutMode() : AnimatedToolbarPane.LayoutMode.DYNAMIC;
    }

    private DockItem createDockItem(String id, String title, Node node, boolean homeToolbar) {
        DockItem item = new DockItem(id, title, node, homeToolbar);
        dockItems.put(item.id(), item);
        return item;
    }

    private DockSlot createDockSlot(DockItem item) {
        DockSlot slot = new DockSlot(item.id(), item);
        dockSlots.put(slot.slotId(), slot);
        return slot;
    }

    private void createFloatingToolbarDocker(DockItem item, double x, double y) {
        if (item == null || floatingToolbarLayer == null) return;
        if (floatingToolbarDockers.containsKey(item.id())) return;
        if (item.contentNode() instanceof CollapsibleToolbarCluster cluster) {
            cluster.setDockedChromeVisible(false);
        }
        FloatingDocker docker = new FloatingDocker(item, x, y);
        floatingToolbarDockers.put(item.id(), docker);
        floatingToolbarLayer.getChildren().addAll(docker, docker.restoreTab());
        applyToolbarDensity(docker, getToolbarLayoutMode() == AnimatedToolbarPane.LayoutMode.COMPACT);
        if (edgeBar != null) {
            edgeBar.toFront();
        }
    }

    private void relayoutFloatingRestoreTabs() {
        for (FloatingDocker docker : floatingToolbarDockers.values()) {
            docker.refreshRestoreTabPosition();
        }
    }

    private void setFloatingLayerDropCaptureEnabled(boolean enabled) {
        floatingLayerDropCaptureEnabled = enabled;
    }

    private void attachFloatingDockerToLayer(FloatingDocker floating) {
        if (floating == null || floatingToolbarLayer == null) return;
        detachNode(floating);
        detachNode(floating.restoreTab());
        floating.setEdgeBarMounted(false);
        if (!floatingToolbarLayer.getChildren().contains(floating)) {
            floatingToolbarLayer.getChildren().add(floating);
        }
        if (!floatingToolbarLayer.getChildren().contains(floating.restoreTab())) {
            floatingToolbarLayer.getChildren().add(floating.restoreTab());
        }
        if (edgeBar != null) {
            edgeBar.toFront();
        }
    }

    private FloatingDocker ensureFloatingToolbarDocker(DockItem item, double x, double y) {
        if (item == null || !item.homeToolbar()) return null;
        FloatingDocker existing = floatingToolbarDockers.get(item.id());
        if (existing != null) return existing;

        CollapsibleToolbarCluster cluster = item.toolbarCluster();
        if (cluster == null && item.contentNode() instanceof CollapsibleToolbarCluster contentCluster) {
            cluster = contentCluster;
            item.setToolbarCluster(contentCluster);
        }
        if (cluster != null) {
            if (toolbarPane != null && toolbarPane.getClustersSnapshot().contains(cluster)) {
                toolbarPane.removeCluster(cluster);
            }
            toolbarDockItems.remove(cluster.getClusterKey(), item);
            cluster.setDockedChromeVisible(false);
            cluster.setManaged(true);
            cluster.setVisible(true);
            installToolbarDockHandlers(cluster);
        }
        item.contentNode().setManaged(true);
        item.contentNode().setVisible(true);
        createFloatingToolbarDocker(item, x, y);
        return floatingToolbarDockers.get(item.id());
    }

    private boolean floatToolbarDockItem(DockItem item) {
        if (item == null || !item.homeToolbar()) return false;
        FloatingDocker existing = floatingToolbarDockers.get(item.id());
        if (existing != null) {
            edgeBarDockItemIds.remove(item.id());
            hiddenDockItemIds.remove(item.id());
            attachFloatingDockerToLayer(existing);
            setFloatingDockerVisibility(item.id(), true);
            existing.toFront();
            refreshAfterDockSwap();
            return true;
        }
        int index = floatingToolbarDockers.size();
        double x = 18.0 + (index % 4) * 220.0;
        double y = 18.0 + (index / 4) * 76.0;
        return floatToolbarDockItem(item, x, y, true);
    }

    private boolean floatToolbarDockItem(DockItem item, double x, double y, boolean visible) {
        FloatingDocker floating = ensureFloatingToolbarDocker(item, x, y);
        if (floating == null) return false;
        attachFloatingDockerToLayer(floating);
        floating.setLayoutX(x);
        floating.setLayoutY(y);
        edgeBarDockItemIds.remove(item.id());
        setFloatingDockerVisibility(item.id(), visible);
        refreshAfterDockSwap();
        return true;
    }

    private boolean dockFloatingToolbarItem(DockItem item) {
        if (item == null || !item.homeToolbar()) return false;
        FloatingDocker floating = floatingToolbarDockers.remove(item.id());
        if (floating != null) {
            detachNode(floating);
            detachNode(floating.restoreTab());
        }
        removeFloatingSnapLinks(item.id());
        edgeBarDockItemIds.remove(item.id());
        hiddenDockItemIds.remove(item.id());
        CollapsibleToolbarCluster cluster = item.asToolbarCluster();
        cluster.setDockedChromeVisible(true);
        cluster.setManaged(true);
        cluster.setVisible(true);
        if (toolbarPane != null && !toolbarPane.getClustersSnapshot().contains(cluster)) {
            toolbarPane.addCluster(cluster);
        }
        toolbarDockItems.put(cluster.getClusterKey(), item);
        refreshAfterDockSwap();
        return true;
    }

    private DockSlot createDynamicDockSlot(String slotId, DockItem item, SplitPane group, int index) {
        String resolvedId = slotId == null || slotId.isBlank()
            ? "custom-dock-" + dynamicDockSlotCounter++
            : slotId.trim();
        DockSlot slot = new DockSlot(resolvedId, item);
        dockSlots.put(slot.slotId(), slot);
        registerDockSlotHome(slot, group, index, true);
        if (item != null) {
            attachDockSlot(slot);
        }
        return slot;
    }

    private SplitPane createDockRow(String groupId, CollapsibleToolbarCluster... clusters) {
        SplitPane row = new SplitPane();
        row.getStyleClass().add("puppeteer-split-pane");
        row.getStyleClass().add("puppeteer-control-dock-row");
        row.setOrientation(Orientation.HORIZONTAL);
        row.setMinWidth(0);
        row.setMinHeight(0);
        registerDockGroup(groupId, row);
        if (clusters != null) {
            for (CollapsibleToolbarCluster cluster : clusters) {
                DockItem item = cluster == null ? null : dockItems.get(cluster.getClusterKey());
                if (item == null) continue;
                DockSlot slot = createDockSlot(item);
                row.getItems().add(slot);
                SplitPane.setResizableWithParent(slot, Boolean.TRUE);
                registerDockSlotHome(slot, row, row.getItems().size() - 1, false);
            }
        }
        installNewDockDropTarget(row);
        refreshDockGroupVisibility(row);
        return row;
    }

    private void registerDockGroup(String groupId, SplitPane group) {
        if (group == null || groupId == null || groupId.isBlank()) return;
        dockGroupIds.put(group, groupId);
        dockGroupsById.put(groupId, group);
        installNewDockDropTarget(group);
    }

    private void registerDockSlotHome(DockSlot slot, SplitPane group, int index, boolean dynamic) {
        if (slot == null) return;
        slot.homeGroup = group;
        slot.homeIndex = Math.max(0, index);
        slot.dynamic = dynamic;
    }

    private void attachDockSlot(DockSlot slot) {
        if (slot == null || slot.homeGroup == null || slot.homeGroup.getItems().contains(slot)) return;
        int index = Math.max(0, Math.min(slot.homeIndex, slot.homeGroup.getItems().size()));
        slot.homeGroup.getItems().add(index, slot);
        SplitPane.setResizableWithParent(slot, Boolean.TRUE);
        refreshDockGroupVisibility(slot.homeGroup);
    }

    private void detachDockSlot(DockSlot slot) {
        if (slot == null || slot.homeGroup == null) return;
        slot.homeGroup.getItems().remove(slot);
        refreshDockGroupVisibility(slot.homeGroup);
        if (slot.dynamic && !slot.hasItems()) {
            dockSlots.remove(slot.slotId());
        }
    }

    private void refreshDockGroupVisibility(SplitPane group) {
        if (group == null) return;
        boolean visible = !group.getItems().isEmpty();
        group.setManaged(visible);
        group.setVisible(visible);
    }

    private void installNewDockDropTarget(SplitPane group) {
        if (group == null) return;
        group.setOnDragOver(event -> {
            String payload = dockPayload(event.getDragboard());
            if (payload == null) return;
            DockItem item = dockItemFromPayload(payload);
            if (item != null && item.homeToolbar()) return;
            event.acceptTransferModes(TransferMode.MOVE);
            event.consume();
        });
        group.setOnDragDropped(event -> {
            String payload = dockPayload(event.getDragboard());
            DockItem item = dockItemFromPayload(payload);
            boolean success = item != null && moveDockItemToNewSlot(item, group);
            event.setDropCompleted(success);
            setFloatingLayerDropCaptureEnabled(false);
            event.consume();
        });
    }

    private void installToolbarDockHandlers(CollapsibleToolbarCluster cluster) {
        if (cluster == null) return;
        DockItem item = dockItems.get(cluster.getClusterKey());
        if (item == null) {
            item = createDockItem(cluster.getClusterKey(), cluster.getTitle(), cluster, true);
        }
        item.setToolbarCluster(cluster);
        cluster.setOnDragDetected(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            Dragboard dragboard = cluster.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(DOCK_DRAG_TOOLBAR_PREFIX + cluster.getClusterKey());
            dragboard.setContent(content);
            setFloatingLayerDropCaptureEnabled(true);
            event.consume();
        });
        cluster.setOnDragDone(event -> setFloatingLayerDropCaptureEnabled(false));
        cluster.setOnDragOver(event -> {
            String payload = dockPayload(event.getDragboard());
            if (payload != null && isDockDragPayload(payload, cluster.getClusterKey())) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        cluster.setOnDragDropped(event -> {
            String payload = dockPayload(event.getDragboard());
            boolean success = false;
            if (payload != null) {
                if (payload.startsWith(DOCK_DRAG_SLOT_PREFIX)) {
                    success = swapDockSlotIntoToolbar(payload.substring(DOCK_DRAG_SLOT_PREFIX.length()), cluster.getClusterKey());
                } else if (payload.startsWith(DOCK_DRAG_TOOLBAR_PREFIX)) {
                    success = swapToolbarClusters(payload.substring(DOCK_DRAG_TOOLBAR_PREFIX.length()), cluster.getClusterKey());
                }
            }
            event.setDropCompleted(success);
            setFloatingLayerDropCaptureEnabled(false);
            event.consume();
        });
    }

    private boolean swapDockSlots(String sourceSlotId, String targetSlotId) {
        if (sourceSlotId == null || targetSlotId == null || sourceSlotId.equals(targetSlotId)) return false;
        DockSlot source = dockSlots.get(sourceSlotId);
        DockSlot target = dockSlots.get(targetSlotId);
        if (source == null || target == null) return false;
        DockItem sourceItem = source.item();
        if (sourceItem == null) return false;
        return moveDockItemToSlot(sourceItem, target.slotId());
    }

    private boolean swapToolbarIntoDockSlot(String toolbarKey, String targetSlotId) {
        if (toolbarKey == null || targetSlotId == null) return false;
        if (!toolbarDockItems.containsKey(toolbarKey)) {
            DockItem dockItem = dockItems.get(toolbarKey);
            return dockItem != null && moveDockItemToSlot(dockItem, targetSlotId);
        }
        DockSlot target = dockSlots.get(targetSlotId);
        DockItem toolbarItem = toolbarDockItems.get(toolbarKey);
        CollapsibleToolbarCluster toolbarCluster = toolbarItem == null ? null : toolbarItem.toolbarCluster();
        if (target == null || toolbarItem == null || toolbarCluster == null) {
            return false;
        }
        toolbarPane.removeCluster(toolbarCluster);
        toolbarDockItems.remove(toolbarKey);
        toolbarItem.clearToolbarCluster();
        target.addItem(toolbarItem);
        refreshAfterDockSwap();
        return true;
    }

    private boolean swapDockSlotIntoToolbar(String sourceSlotId, String targetToolbarKey) {
        if (sourceSlotId == null || targetToolbarKey == null) return false;
        DockSlot source = dockSlots.get(sourceSlotId);
        DockItem toolbarItem = toolbarDockItems.get(targetToolbarKey);
        CollapsibleToolbarCluster toolbarCluster = toolbarItem == null ? null : toolbarItem.toolbarCluster();
        if (source == null || toolbarItem == null || toolbarCluster == null) {
            return false;
        }
        DockItem sourceItem = source.item();
        if (sourceItem == null) return false;
        CollapsibleToolbarCluster replacement = sourceItem.asToolbarCluster();
        toolbarPane.replaceCluster(toolbarCluster, replacement);
        toolbarDockItems.remove(targetToolbarKey);
        toolbarDockItems.put(replacement.getClusterKey(), sourceItem);
        toolbarItem.clearToolbarCluster();
        source.removeItem(sourceItem);
        source.addItem(toolbarItem);
        refreshAfterDockSwap();
        return true;
    }

    private boolean swapToolbarClusters(String sourceToolbarKey, String targetToolbarKey) {
        if (sourceToolbarKey == null || targetToolbarKey == null || sourceToolbarKey.equals(targetToolbarKey)) return false;
        DockItem source = toolbarDockItems.get(sourceToolbarKey);
        DockItem target = toolbarDockItems.get(targetToolbarKey);
        CollapsibleToolbarCluster sourceCluster = source == null ? null : source.toolbarCluster();
        CollapsibleToolbarCluster targetCluster = target == null ? null : target.toolbarCluster();
        if (source == null || target == null || sourceCluster == null || targetCluster == null) {
            return false;
        }
        toolbarPane.swapClusters(sourceCluster, targetCluster);
        refreshAfterDockSwap();
        return true;
    }

    private void refreshAfterDockSwap() {
        hiddenDockItemIds.removeIf(id -> {
            DockItem item = dockItems.get(id);
            return item != null && !item.homeToolbar() && isDockItemVisible(item);
        });
        syncCodePaneVisibilityState();
        if (!applyingDockLayoutPrefs) {
            persistWorkspacePrefsNow();
        }
        refreshSidebarTabs();
        refreshToolbarCommandSummary();
        updateStatusBar();
        Platform.runLater(() -> {
            if (topWorkspaceSplit != null && !topWorkspaceSplit.getDividers().isEmpty()) {
                topWorkspaceSplit.setDividerPositions(readDividerPosition(topWorkspaceSplit, topWorkspaceDividerPosition));
            }
            if (bottomWorkspaceSplit != null && !bottomWorkspaceSplit.getDividers().isEmpty()) {
                bottomWorkspaceSplit.setDividerPositions(readDividerPosition(bottomWorkspaceSplit, bottomWorkspaceDividerPosition));
            }
            if (workspaceContentSplit != null && !workspaceContentSplit.getDividers().isEmpty()) {
                workspaceContentSplit.setDividerPositions(readDividerPosition(workspaceContentSplit, workspaceContentDividerPosition));
            }
            if (mainWorkspaceSplit != null && !mainWorkspaceSplit.getDividers().isEmpty()) {
                mainWorkspaceSplit.setDividerPositions(readDividerPosition(mainWorkspaceSplit, codePaneDividerPosition));
            }
        });
    }

    private void populateDockersMenu(Menu dockersMenu) {
        if (dockersMenu == null) return;
        dockersMenu.getItems().clear();

        MenuItem miRestoreAllDockers = new MenuItem("Restore All Dockers");
        miRestoreAllDockers.setOnAction(ev -> {
            for (DockItem item : dockItems.values()) {
                showDockItem(item);
            }
        });
        MenuItem miResetDockArrangement = new MenuItem("Reset Dock Arrangement");
        miResetDockArrangement.setOnAction(ev -> resetDockArrangement());
        dockersMenu.getItems().addAll(miRestoreAllDockers, miResetDockArrangement, new SeparatorMenuItem());

        Menu mainDockersMenu = new Menu("Main Dockers");
        Menu floatingDockersMenu = new Menu("Floating Dockers");
        floatingDockersMenu.getItems().add(createEdgeBarMenu());
        floatingDockersMenu.getItems().add(new SeparatorMenuItem());
        boolean hasMainDockers = false;
        boolean hasFloatingDockers = false;
        for (DockItem item : dockItems.values()) {
            if (item.homeToolbar()) {
                hasFloatingDockers = true;
                floatingDockersMenu.getItems().add(createDockerMenuItem(item));
            } else {
                hasMainDockers = true;
                mainDockersMenu.getItems().add(createDockerMenuItem(item));
            }
        }
        if (!hasMainDockers) {
            MenuItem empty = new MenuItem("No Main Dockers");
            empty.setDisable(true);
            mainDockersMenu.getItems().add(empty);
        }
        if (!hasFloatingDockers) {
            MenuItem empty = new MenuItem("No Floating Dockers");
            empty.setDisable(true);
            floatingDockersMenu.getItems().add(empty);
        }
        dockersMenu.getItems().addAll(mainDockersMenu, floatingDockersMenu);
    }

    private Menu createDockerMenuItem(DockItem item) {
        Menu itemMenu = new Menu(item.title());
        CheckMenuItem miVisible = new CheckMenuItem("Visible");
        miVisible.setSelected(isDockItemVisible(item));
        miVisible.setOnAction(ev -> {
            if (miVisible.isSelected()) {
                showDockItem(item);
            } else {
                hideDockItem(item);
            }
        });
        itemMenu.getItems().add(miVisible);

        MenuItem miRestore = new MenuItem(isDockItemVisible(item) ? "Bring Forward" : "Restore");
        miRestore.setOnAction(ev -> showDockItem(item));
        itemMenu.getItems().add(miRestore);

        MenuItem miHide = new MenuItem("Kill / Hide");
        miHide.setDisable(!isDockItemVisible(item));
        miHide.setOnAction(ev -> hideDockItem(item));
        itemMenu.getItems().add(miHide);

        itemMenu.getItems().add(new SeparatorMenuItem());
        if (item.homeToolbar()) {
            MenuItem miFloat = new MenuItem(floatingToolbarDockers.containsKey(item.id()) ? "Show Floating" : "Float");
            miFloat.setOnAction(ev -> floatToolbarDockItem(item));
            MenuItem miEdgeBar = new MenuItem(edgeBarDockItemIds.contains(item.id()) ? "Show From Edge Bar" : "Send to Edge Bar");
            miEdgeBar.setOnAction(ev -> {
                if (edgeBarDockItemIds.contains(item.id())) {
                    restoreEdgeBarDockItem(item);
                } else {
                    sendDockItemToEdgeBar(item);
                }
            });
            MenuItem miReturnToToolbar = new MenuItem("Return to Toolbar Row");
            miReturnToToolbar.setDisable(!floatingToolbarDockers.containsKey(item.id()));
            miReturnToToolbar.setOnAction(ev -> dockFloatingToolbarItem(item));
            itemMenu.getItems().addAll(miFloat, miEdgeBar, miReturnToToolbar, new SeparatorMenuItem());
        }
        itemMenu.getItems().add(createMoveDockItemMenu(item));
        return itemMenu;
    }

    private Menu createEdgeBarMenu() {
        Menu menu = new Menu("Edge Bar");
        CheckMenuItem miEnabled = new CheckMenuItem("Enabled");
        miEnabled.setSelected(edgeBarEnabled);
        miEnabled.setOnAction(event -> setEdgeBarEnabled(miEnabled.isSelected()));

        Menu sideMenu = new Menu("Side");
        ToggleGroup sideGroup = new ToggleGroup();
        for (FloatingEdge edge : FloatingEdge.values()) {
            RadioMenuItem sideItem = new RadioMenuItem(edgeBarSideLabel(edge));
            sideItem.setToggleGroup(sideGroup);
            sideItem.setSelected(edge == edgeBarEdge);
            sideItem.setOnAction(event -> setEdgeBarEdge(edge));
            sideMenu.getItems().add(sideItem);
        }

        Menu addMenu = new Menu("Add Docker");
        boolean hasEligible = false;
        for (DockItem item : dockItems.values()) {
            if (item == null || !item.homeToolbar() || edgeBarDockItemIds.contains(item.id())) continue;
            hasEligible = true;
            MenuItem addItem = new MenuItem(item.title());
            addItem.setOnAction(event -> sendDockItemToEdgeBar(item));
            addMenu.getItems().add(addItem);
        }
        if (!hasEligible) {
            MenuItem empty = new MenuItem("No Available Floating Dockers");
            empty.setDisable(true);
            addMenu.getItems().add(empty);
        }

        MenuItem miShowPanel = new MenuItem("Show Panel");
        miShowPanel.setDisable(!edgeBarEnabled);
        miShowPanel.setOnAction(event -> {
            if (edgeBar != null) {
                edgeBar.setExpanded(true, true);
            }
        });
        MenuItem miPopAll = new MenuItem("Pop Out All");
        miPopAll.setDisable(edgeBarDockItemIds.isEmpty());
        miPopAll.setOnAction(event -> restoreAllEdgeBarDockItems());
        MenuItem miClear = new MenuItem("Kill / Hide All");
        miClear.setDisable(edgeBarDockItemIds.isEmpty());
        miClear.setOnAction(event -> clearEdgeBarDockItems());

        menu.getItems().addAll(miEnabled, sideMenu, addMenu, new SeparatorMenuItem(), miShowPanel, miPopAll, miClear);
        return menu;
    }

    private Menu buildWorkspacePresetsMenu() {
        Menu menu = new Menu("Workspace Presets");
        menu.setOnShowing(event -> populateWorkspacePresetsMenu(menu));
        populateWorkspacePresetsMenu(menu);
        return menu;
    }

    private void populateWorkspacePresetsMenu(Menu menu) {
        if (menu == null) return;
        menu.getItems().clear();
        MenuItem balanced = new MenuItem("Balanced Floating Tools");
        balanced.setOnAction(event -> applyWorkspacePreset("balanced"));
        MenuItem animation = new MenuItem("Animation Controls");
        animation.setOnAction(event -> applyWorkspacePreset("animation"));
        MenuItem preview = new MenuItem("Clean Preview");
        preview.setOnAction(event -> applyWorkspacePreset("preview"));
        MenuItem export = new MenuItem("Register & Export");
        export.setOnAction(event -> applyWorkspacePreset("export"));
        MenuItem saveCurrent = new MenuItem("Save Current Workspace...");
        saveCurrent.setDisable(workspacePrefs == null);
        saveCurrent.setOnAction(event -> saveCurrentWorkspacePresetDialog());
        Menu saved = new Menu("Saved Workspaces");
        populateSavedWorkspaceMenu(saved, false);
        Menu delete = new Menu("Delete Saved Workspace");
        populateSavedWorkspaceMenu(delete, true);
        menu.getItems().addAll(
            balanced,
            animation,
            preview,
            export,
            new SeparatorMenuItem(),
            saveCurrent,
            saved,
            delete
        );
    }

    private void populateSavedWorkspaceMenu(Menu menu, boolean deleteMode) {
        if (menu == null) return;
        menu.getItems().clear();
        List<String> presetIds = savedWorkspacePresetIds();
        if (presetIds.isEmpty()) {
            MenuItem empty = new MenuItem("No Saved Workspaces");
            empty.setDisable(true);
            menu.getItems().add(empty);
            return;
        }
        for (String presetId : presetIds) {
            String name = workspacePrefs.getString(workspacePresetKey(presetId, WORKSPACE_PRESET_NAME_SUFFIX))
                .orElse(presetId);
            MenuItem item = new MenuItem(deleteMode ? "Delete " + name : name);
            if (deleteMode) {
                item.setOnAction(event -> deleteWorkspacePreset(presetId));
            } else {
                item.setOnAction(event -> applySavedWorkspacePreset(presetId));
            }
            menu.getItems().add(item);
        }
    }

    private List<String> savedWorkspacePresetIds() {
        if (workspacePrefs == null) return List.of();
        List<String> ids = new ArrayList<>();
        for (String id : parseDockIdList(workspacePrefs.getString(PuppeteerWorkspacePrefs.KEY_WORKSPACE_PRESET_ORDER).orElse(""))) {
            if (workspacePrefs.getString(workspacePresetKey(id, WORKSPACE_PRESET_PAYLOAD_SUFFIX)).isPresent()
                && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private void saveCurrentWorkspacePresetDialog() {
        if (workspacePrefs == null) return;
        TextInputDialog dialog = new TextInputDialog("My Workspace");
        dialog.initOwner(this);
        dialog.setTitle("Save Workspace Preset");
        dialog.setHeaderText("Save Current Workspace");
        dialog.setContentText("Name:");
        dialog.showAndWait()
            .map(String::trim)
            .filter(name -> !name.isBlank())
            .ifPresent(this::saveCurrentWorkspacePreset);
    }

    private void saveCurrentWorkspacePreset(String name) {
        if (workspacePrefs == null || name == null || name.isBlank()) return;
        String payload = captureWorkspacePresetPayload();
        if (payload.isBlank()) return;
        String presetId = workspacePresetId(name);
        workspacePrefs.setString(workspacePresetKey(presetId, WORKSPACE_PRESET_NAME_SUFFIX), name.trim());
        workspacePrefs.setString(workspacePresetKey(presetId, WORKSPACE_PRESET_PAYLOAD_SUFFIX), payload);
        List<String> order = new ArrayList<>(savedWorkspacePresetIds());
        if (!order.contains(presetId)) {
            order.add(presetId);
        }
        workspacePrefs.setString(PuppeteerWorkspacePrefs.KEY_WORKSPACE_PRESET_ORDER, formatDockIdList(order));
        workspacePrefs.save();
    }

    private void applySavedWorkspacePreset(String presetId) {
        if (workspacePrefs == null || presetId == null || presetId.isBlank()) return;
        workspacePrefs.getString(workspacePresetKey(presetId, WORKSPACE_PRESET_PAYLOAD_SUFFIX))
            .ifPresent(this::applyWorkspacePresetPayload);
    }

    private void deleteWorkspacePreset(String presetId) {
        if (workspacePrefs == null || presetId == null || presetId.isBlank()) return;
        workspacePrefs.remove(workspacePresetKey(presetId, WORKSPACE_PRESET_NAME_SUFFIX));
        workspacePrefs.remove(workspacePresetKey(presetId, WORKSPACE_PRESET_PAYLOAD_SUFFIX));
        List<String> order = new ArrayList<>(savedWorkspacePresetIds());
        order.remove(presetId);
        workspacePrefs.setString(PuppeteerWorkspacePrefs.KEY_WORKSPACE_PRESET_ORDER, formatDockIdList(order));
        workspacePrefs.save();
    }

    private String captureWorkspacePresetPayload() {
        PuppeteerWorkspacePrefs snapshot = PuppeteerWorkspacePrefs.transientPrefs();
        captureWorkspacePrefsInto(snapshot);
        snapshot.remove(PuppeteerWorkspacePrefs.KEY_VIEWPORT_PAN_X);
        snapshot.remove(PuppeteerWorkspacePrefs.KEY_VIEWPORT_PAN_Y);
        snapshot.remove(PuppeteerWorkspacePrefs.KEY_VIEWPORT_ZOOM);
        snapshot.remove(PuppeteerWorkspacePrefs.KEY_TIMELINE_PLAYHEAD);
        Properties props = new Properties();
        props.putAll(snapshot.snapshotEntries());
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            props.store(out, "Puppeteer workspace preset");
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException ex) {
            return "";
        }
    }

    private void applyWorkspacePresetPayload(String payload) {
        if (workspacePrefs == null || payload == null || payload.isBlank()) return;
        Properties props = new Properties();
        try {
            byte[] bytes = Base64.getDecoder().decode(payload.trim());
            props.load(new ByteArrayInputStream(bytes));
        } catch (IOException | IllegalArgumentException ex) {
            return;
        }
        clearWorkspaceLayoutPrefs(workspacePrefs);
        for (String key : props.stringPropertyNames()) {
            workspacePrefs.setString(key, props.getProperty(key, ""));
        }
        workspacePrefs.save();
        applyWorkspacePrefs();
        refreshSidebarTabs();
        refreshToolbarCommandSummary();
        updateStatusBar();
    }

    private void clearWorkspaceLayoutPrefs(PuppeteerWorkspacePrefs prefs) {
        if (prefs == null) return;
        prefs.remove(PuppeteerWorkspacePrefs.DIVIDER_TOP);
        prefs.remove(PuppeteerWorkspacePrefs.DIVIDER_BOTTOM);
        prefs.remove(PuppeteerWorkspacePrefs.DIVIDER_CONTENT);
        prefs.remove(PuppeteerWorkspacePrefs.DIVIDER_CODE_PANE);
        prefs.remove(PuppeteerWorkspacePrefs.DIVIDER_TOOLBAR);
        prefs.remove(PuppeteerWorkspacePrefs.DIVIDER_PREVIEW_FOCUS);
        prefs.remove(PuppeteerWorkspacePrefs.KEY_TOP_TOOLBAR_VISIBLE);
        prefs.remove(PuppeteerWorkspacePrefs.KEY_TOOLBAR_LAYOUT_MODE);
        prefs.remove(PuppeteerWorkspacePrefs.KEY_UI_SCALE);
        prefs.remove(PuppeteerWorkspacePrefs.KEY_DOCK_TOOLBAR_ORDER);
        prefs.remove(PuppeteerWorkspacePrefs.KEY_DOCK_HIDDEN_ITEMS);
        prefs.remove(PuppeteerWorkspacePrefs.KEY_DOCK_DYNAMIC_SLOTS);
        prefs.remove(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_ENABLED);
        prefs.remove(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_EDGE);
        prefs.remove(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_OFFSET);
        prefs.remove(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_WIDTH);
        prefs.remove(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_HEIGHT);
        prefs.remove(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_ITEMS);
        prefs.removeKeysStartingWith(PuppeteerWorkspacePrefs.KEY_DOCK_SLOT_PREFIX);
        prefs.removeKeysStartingWith(PuppeteerWorkspacePrefs.KEY_FLOATING_DOCKER_PREFIX);
    }

    private String workspacePresetKey(String presetId, String suffix) {
        return PuppeteerWorkspacePrefs.KEY_WORKSPACE_PRESET_PREFIX + presetId + suffix;
    }

    private static String workspacePresetId(String name) {
        String source = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        boolean dash = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
                dash = false;
            } else if (!dash && out.length() > 0) {
                out.append('-');
                dash = true;
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '-') {
            out.deleteCharAt(out.length() - 1);
        }
        return out.length() == 0 ? "workspace" : out.toString();
    }

    private void applyWorkspacePreset(String preset) {
        if (preset == null || preset.isBlank()) return;
        switch (preset.trim().toLowerCase(Locale.ROOT)) {
            case "animation" -> {
                placeFloatingDocker("toolbar-transport", 14, 14, true);
                placeFloatingDocker("toolbar-duration", 260, 14, true);
                placeFloatingDocker("toolbar-property", 14, 92, true);
                placeFloatingDocker("toolbar-keyframes", 220, 92, true);
                placeFloatingDocker("toolbar-snap", 560, 92, true);
                placeFloatingDocker("toolbar-preview", 700, 92, true);
                placeFloatingDocker("toolbar-orbit", 14, 170, true);
                setFloatingDockerVisibility("toolbar-history", false);
                setFloatingDockerVisibility("toolbar-presets", false);
                setFloatingDockerVisibility("toolbar-audio", false);
                setFloatingDockerVisibility("toolbar-register", false);
                setFloatingDockerVisibility("toolbar-export", false);
                setFloatingDockerVisibility("toolbar-diagnostics", false);
                setFloatingDockerVisibility("toolbar-help", false);
            }
            case "preview" -> {
                placeFloatingDocker("toolbar-transport", 14, 14, true);
                placeFloatingDocker("toolbar-preview", 260, 14, true);
                placeFloatingDocker("toolbar-snap", 620, 14, true);
                placeFloatingDocker("toolbar-orbit", 14, 92, true);
                for (DockItem item : dockItems.values()) {
                    if (item.homeToolbar()
                        && !Set.of("toolbar-transport", "toolbar-preview", "toolbar-snap", "toolbar-orbit").contains(item.id())) {
                        setFloatingDockerVisibility(item.id(), false);
                    }
                }
            }
            case "export" -> {
                hideFloatingToolbarsExcept("toolbar-register", "toolbar-export", "toolbar-diagnostics", "toolbar-help", "toolbar-transport");
                placeFloatingDocker("toolbar-register", 14, 14, true);
                placeFloatingDocker("toolbar-export", 310, 14, true);
                placeFloatingDocker("toolbar-diagnostics", 470, 14, true);
                placeFloatingDocker("toolbar-help", 610, 14, true);
                placeFloatingDocker("toolbar-transport", 14, 92, true);
            }
            default -> {
                placeFloatingDocker("toolbar-transport", 14, 14, true);
                placeFloatingDocker("toolbar-history", 260, 14, true);
                placeFloatingDocker("toolbar-duration", 410, 14, true);
                placeFloatingDocker("toolbar-register", 620, 14, true);
                placeFloatingDocker("toolbar-export", 880, 14, true);
                placeFloatingDocker("toolbar-presets", 14, 92, true);
                placeFloatingDocker("toolbar-property", 180, 92, true);
                placeFloatingDocker("toolbar-keyframes", 360, 92, true);
                placeFloatingDocker("toolbar-snap", 660, 92, true);
                placeFloatingDocker("toolbar-preview", 800, 92, true);
                placeFloatingDocker("toolbar-orbit", 14, 170, true);
                placeFloatingDocker("toolbar-audio", 170, 170, true);
                placeFloatingDocker("toolbar-diagnostics", 430, 170, true);
                placeFloatingDocker("toolbar-help", 560, 170, true);
            }
        }
        persistWorkspacePrefsNow();
        refreshToolbarCommandSummary();
        updateStatusBar();
    }

    private void placeFloatingDocker(String itemId, double x, double y, boolean visible) {
        DockItem item = dockItems.get(itemId);
        if (item != null && item.homeToolbar()) {
            floatToolbarDockItem(item, x, y, visible);
            return;
        }
        FloatingDocker floating = floatingToolbarDockers.get(itemId);
        if (floating == null) return;
        floating.setLayoutX(x);
        floating.setLayoutY(y);
        setFloatingDockerVisibility(itemId, visible);
    }

    private void setFloatingDockerVisibility(String itemId, boolean visible) {
        DockItem item = dockItems.get(itemId);
        FloatingDocker floating = floatingToolbarDockers.get(itemId);
        if (item == null || floating == null) return;
        if (visible) {
            hiddenDockItemIds.remove(itemId);
            edgeBarDockItemIds.remove(itemId);
            attachFloatingDockerToLayer(floating);
            floating.showSmoothly();
        } else {
            edgeBarDockItemIds.remove(itemId);
            hiddenDockItemIds.add(itemId);
            removeFloatingSnapLinks(itemId);
            floating.hideSmoothly();
        }
        refreshEdgeBar();
    }

    private void stashFloatingDocker(DockItem item) {
        if (item == null) return;
        FloatingDocker floating = floatingToolbarDockers.get(item.id());
        if (floating == null) return;
        edgeBarDockItemIds.remove(item.id());
        hiddenDockItemIds.add(item.id());
        floating.stashSmoothly();
        refreshEdgeBar();
        refreshAfterDockSwap();
    }

    private boolean sendDockItemToEdgeBar(DockItem item) {
        if (item == null || !item.homeToolbar()) return false;
        FloatingDocker floating = ensureFloatingToolbarDocker(item, 18.0, 18.0);
        if (floating == null) return false;
        if (!edgeBarEnabled) {
            edgeBarEnabled = true;
        }
        hiddenDockItemIds.remove(item.id());
        removeFloatingSnapLinks(item.id());
        edgeBarDockItemIds.add(item.id());
        floating.hideImmediately();
        refreshEdgeBar();
        if (edgeBar != null) {
            edgeBar.setExpanded(true, true);
        }
        refreshAfterDockSwap();
        return true;
    }

    private void restoreEdgeBarDockItem(DockItem item) {
        if (item == null) return;
        edgeBarDockItemIds.remove(item.id());
        hiddenDockItemIds.remove(item.id());
        FloatingDocker floating = ensureFloatingToolbarDocker(item, 18.0, 18.0);
        if (floating != null) {
            attachFloatingDockerToLayer(floating);
            placeFloatingDockerNearEdgeBar(floating);
            floating.showSmoothly();
        } else {
            showDockItem(item);
        }
        refreshEdgeBar();
        refreshAfterDockSwap();
    }

    private void restoreAllEdgeBarDockItems() {
        for (String itemId : new ArrayList<>(edgeBarDockItemIds)) {
            DockItem item = dockItems.get(itemId);
            if (item != null) {
                restoreEdgeBarDockItem(item);
            } else {
                edgeBarDockItemIds.remove(itemId);
            }
        }
        refreshEdgeBar();
    }

    private void clearEdgeBarDockItems() {
        for (String itemId : new ArrayList<>(edgeBarDockItemIds)) {
            DockItem item = dockItems.get(itemId);
            if (item != null) {
                hideEdgeBarDockItem(item);
            } else {
                edgeBarDockItemIds.remove(itemId);
            }
        }
        refreshEdgeBar();
        refreshAfterDockSwap();
    }

    private void hideEdgeBarDockItem(DockItem item) {
        if (item == null) return;
        edgeBarDockItemIds.remove(item.id());
        hideDockItem(item);
        refreshEdgeBar();
    }

    private void placeFloatingDockerNearEdgeBar(FloatingDocker floating) {
        if (floating == null) return;
        Scene scene = getScene();
        double sceneWidth = scene == null ? 1280.0 : Math.max(320.0, scene.getWidth());
        double sceneHeight = scene == null ? 720.0 : Math.max(240.0, scene.getHeight());
        double width = Math.max(180.0, floating.getBoundsInParent().getWidth());
        double height = Math.max(64.0, floating.getBoundsInParent().getHeight());
        double x;
        double y;
        if (edgeBarEdge == FloatingEdge.LEFT) {
            x = EDGE_BAR_HANDLE_THICKNESS + 16.0;
            y = Math.max(54.0, sceneHeight * 0.5 - height * 0.5);
        } else if (edgeBarEdge == FloatingEdge.TOP) {
            x = Math.max(18.0, sceneWidth * 0.5 - width * 0.5);
            y = EDGE_BAR_HANDLE_THICKNESS + 16.0;
        } else if (edgeBarEdge == FloatingEdge.BOTTOM) {
            x = Math.max(18.0, sceneWidth * 0.5 - width * 0.5);
            y = sceneHeight - height - EDGE_BAR_HANDLE_THICKNESS - 22.0;
        } else {
            x = sceneWidth - width - EDGE_BAR_HANDLE_THICKNESS - 22.0;
            y = Math.max(54.0, sceneHeight * 0.5 - height * 0.5);
        }
        floating.setLayoutX(clampDouble(x, 14.0, Math.max(14.0, sceneWidth - width - 18.0)));
        floating.setLayoutY(clampDouble(y, 36.0, Math.max(36.0, sceneHeight - height - 34.0)));
    }

    private void setEdgeBarEnabled(boolean enabled) {
        edgeBarEnabled = enabled;
        refreshEdgeBar();
        persistWorkspacePrefsNow();
    }

    private void setEdgeBarEdge(FloatingEdge edge) {
        if (edge == null) return;
        edgeBarEdge = edge;
        refreshEdgeBar();
        persistWorkspacePrefsNow();
    }

    private void refreshEdgeBar() {
        edgeBarDockItemIds.removeIf(id -> {
            DockItem item = dockItems.get(id);
            return item == null || !item.homeToolbar();
        });
        if (edgeBar != null) {
            edgeBar.refresh();
            edgeBar.relayout();
        }
    }

    private void relayoutEdgeBar() {
        if (edgeBar != null) {
            edgeBar.relayout();
        }
    }

    private boolean isEdgeBarDrop(MouseEvent event) {
        if (event == null || !edgeBarEnabled || edgeBar == null) return false;
        Scene scene = getScene();
        if (scene == null) return false;
        double x = event.getSceneX();
        double y = event.getSceneY();
        if (edgeBar.containsScenePoint(x, y)) return true;
        return switch (edgeBarEdge) {
            case LEFT -> x <= 28.0;
            case RIGHT -> x >= scene.getWidth() - 28.0;
            case TOP -> y <= 28.0;
            case BOTTOM -> y >= scene.getHeight() - 28.0;
        };
    }

    private static String edgeBarSideLabel(FloatingEdge edge) {
        if (edge == null) return "Right";
        return switch (edge) {
            case LEFT -> "Left";
            case RIGHT -> "Right";
            case TOP -> "Top";
            case BOTTOM -> "Bottom";
        };
    }

    private static FloatingEdge parseFloatingEdge(String raw, FloatingEdge fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return FloatingEdge.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private void addFloatingSnapLink(String firstId, String secondId) {
        if (firstId == null || secondId == null || firstId.equals(secondId)) return;
        if (!floatingToolbarDockers.containsKey(firstId) || !floatingToolbarDockers.containsKey(secondId)) return;
        floatingDockerSnapLinks.computeIfAbsent(firstId, key -> new LinkedHashSet<>()).add(secondId);
        floatingDockerSnapLinks.computeIfAbsent(secondId, key -> new LinkedHashSet<>()).add(firstId);
    }

    private void removeFloatingSnapLinks(String itemId) {
        if (itemId == null) return;
        Set<String> linked = floatingDockerSnapLinks.remove(itemId);
        if (linked != null) {
            for (String linkedId : linked) {
                Set<String> peers = floatingDockerSnapLinks.get(linkedId);
                if (peers != null) {
                    peers.remove(itemId);
                    if (peers.isEmpty()) {
                        floatingDockerSnapLinks.remove(linkedId);
                    }
                }
            }
        }
        floatingDockerSnapLinks.values().forEach(peers -> peers.remove(itemId));
        floatingDockerSnapLinks.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private void unsnapFloatingDocker(String itemId) {
        removeFloatingSnapLinks(itemId);
        persistWorkspacePrefsNow();
    }

    private void unsnapFloatingDockerCluster(String itemId) {
        Set<String> group = floatingSnapGroupIds(itemId);
        for (String id : new ArrayList<>(group)) {
            removeFloatingSnapLinks(id);
        }
        persistWorkspacePrefsNow();
    }

    private boolean hasFloatingSnapLinks(String itemId) {
        Set<String> peers = floatingDockerSnapLinks.get(itemId);
        return peers != null && !peers.isEmpty();
    }

    private Set<String> floatingSnapGroupIds(String itemId) {
        LinkedHashSet<String> group = new LinkedHashSet<>();
        if (itemId == null || !floatingToolbarDockers.containsKey(itemId)) return group;
        List<String> stack = new ArrayList<>();
        group.add(itemId);
        stack.add(itemId);
        while (!stack.isEmpty()) {
            String current = stack.remove(stack.size() - 1);
            Set<String> peers = floatingDockerSnapLinks.get(current);
            if (peers == null) continue;
            for (String peer : peers) {
                if (!floatingToolbarDockers.containsKey(peer) || group.contains(peer)) continue;
                group.add(peer);
                stack.add(peer);
            }
        }
        return group;
    }

    private List<FloatingDocker> visibleFloatingSnapGroup(FloatingDocker docker) {
        if (docker == null) return List.of();
        List<FloatingDocker> group = new ArrayList<>();
        for (String id : floatingSnapGroupIds(docker.item.id())) {
            FloatingDocker member = floatingToolbarDockers.get(id);
            if (isActiveFloatingDocker(member)) {
                group.add(member);
            }
        }
        if (group.isEmpty() && isActiveFloatingDocker(docker)) {
            group.add(docker);
        }
        return group;
    }

    private boolean isActiveFloatingDocker(FloatingDocker docker) {
        return docker != null
            && docker.isVisible()
            && docker.isManaged()
            && !docker.isMouseTransparent()
            && !edgeBarDockItemIds.contains(docker.item.id())
            && !hiddenDockItemIds.contains(docker.item.id());
    }

    private FloatingSnapAdjustment calculateFloatingSnapAdjustment(List<FloatingDocker> group) {
        if (group == null || group.isEmpty()) return new FloatingSnapAdjustment(0.0, 0.0);
        Set<String> groupIds = new LinkedHashSet<>();
        for (FloatingDocker docker : group) {
            groupIds.add(docker.item.id());
        }
        FloatingBounds groupBounds = floatingBounds(group);
        if (groupBounds == null) return new FloatingSnapAdjustment(0.0, 0.0);

        FloatingSnapCandidate best = null;
        for (FloatingDocker other : floatingToolbarDockers.values()) {
            if (!isActiveFloatingDocker(other) || groupIds.contains(other.item.id())) continue;
            FloatingBounds otherBounds = floatingBounds(other);
            if (otherBounds == null) continue;

            FloatingAlignment verticalAlignment = bestFloatingVerticalAlignment(groupBounds, otherBounds);
            if (floatingRangesCanSnap(
                groupBounds.minY(), groupBounds.maxY(), otherBounds.minY(), otherBounds.maxY(), verticalAlignment
            )) {
                best = betterFloatingSnapCandidate(
                    best,
                    horizontalFloatingSnapCandidate(otherBounds.minX() - groupBounds.maxX(), verticalAlignment, 0.0)
                );
                best = betterFloatingSnapCandidate(
                    best,
                    horizontalFloatingSnapCandidate(otherBounds.maxX() - groupBounds.minX(), verticalAlignment, 0.0)
                );
                best = betterFloatingSnapCandidate(
                    best,
                    horizontalFloatingSnapCandidate(otherBounds.minX() - groupBounds.minX(), verticalAlignment, 10.0)
                );
                best = betterFloatingSnapCandidate(
                    best,
                    horizontalFloatingSnapCandidate(otherBounds.maxX() - groupBounds.maxX(), verticalAlignment, 10.0)
                );
            }

            FloatingAlignment horizontalAlignment = bestFloatingHorizontalAlignment(groupBounds, otherBounds);
            if (floatingRangesCanSnap(
                groupBounds.minX(), groupBounds.maxX(), otherBounds.minX(), otherBounds.maxX(), horizontalAlignment
            )) {
                best = betterFloatingSnapCandidate(
                    best,
                    verticalFloatingSnapCandidate(otherBounds.minY() - groupBounds.maxY(), horizontalAlignment, 0.0)
                );
                best = betterFloatingSnapCandidate(
                    best,
                    verticalFloatingSnapCandidate(otherBounds.maxY() - groupBounds.minY(), horizontalAlignment, 0.0)
                );
                best = betterFloatingSnapCandidate(
                    best,
                    verticalFloatingSnapCandidate(otherBounds.minY() - groupBounds.minY(), horizontalAlignment, 10.0)
                );
                best = betterFloatingSnapCandidate(
                    best,
                    verticalFloatingSnapCandidate(otherBounds.maxY() - groupBounds.maxY(), horizontalAlignment, 10.0)
                );
            }
        }
        return best == null ? new FloatingSnapAdjustment(0.0, 0.0) : new FloatingSnapAdjustment(best.dx(), best.dy());
    }

    private FloatingSnapCandidate horizontalFloatingSnapCandidate(double dx, FloatingAlignment verticalAlignment, double bias) {
        return floatingSnapCandidate(dx, verticalAlignment.delta(), dx, verticalAlignment, bias);
    }

    private FloatingSnapCandidate verticalFloatingSnapCandidate(double dy, FloatingAlignment horizontalAlignment, double bias) {
        return floatingSnapCandidate(horizontalAlignment.delta(), dy, dy, horizontalAlignment, bias);
    }

    private FloatingSnapCandidate floatingSnapCandidate(
        double dx,
        double dy,
        double contactDelta,
        FloatingAlignment alignment,
        double bias
    ) {
        double contactDistance = Math.abs(contactDelta);
        if (contactDistance > FLOATING_DOCKER_SNAP_DISTANCE) return null;
        double score = contactDistance + alignment.score() * 0.45 + bias;
        return new FloatingSnapCandidate(dx, dy, score);
    }

    private FloatingSnapCandidate betterFloatingSnapCandidate(FloatingSnapCandidate best, FloatingSnapCandidate candidate) {
        if (candidate == null) return best;
        if (best == null || candidate.score() < best.score()) return candidate;
        return best;
    }

    private boolean floatingRangesCanSnap(double aMin, double aMax, double bMin, double bMax, FloatingAlignment alignment) {
        if (rangesOverlapEnough(aMin, aMax, bMin, bMax)) return true;
        if (alignment.active()) return true;
        return rangeGap(aMin, aMax, bMin, bMax) <= FLOATING_DOCKER_SNAP_RANGE_PADDING;
    }

    private FloatingAlignment bestFloatingVerticalAlignment(FloatingBounds groupBounds, FloatingBounds otherBounds) {
        FloatingAlignment best = defaultFloatingAlignment(
            groupBounds.minY(), groupBounds.maxY(), otherBounds.minY(), otherBounds.maxY()
        );
        best = betterFloatingAlignment(best, otherBounds.minY() - groupBounds.minY(), 0.0);
        best = betterFloatingAlignment(best, otherBounds.maxY() - groupBounds.maxY(), 0.0);
        best = betterFloatingAlignment(best, otherBounds.centerY() - groupBounds.centerY(), 3.0);
        best = betterFloatingAlignment(best, otherBounds.minY() - groupBounds.maxY(), 8.0);
        best = betterFloatingAlignment(best, otherBounds.maxY() - groupBounds.minY(), 8.0);
        return best;
    }

    private FloatingAlignment bestFloatingHorizontalAlignment(FloatingBounds groupBounds, FloatingBounds otherBounds) {
        FloatingAlignment best = defaultFloatingAlignment(
            groupBounds.minX(), groupBounds.maxX(), otherBounds.minX(), otherBounds.maxX()
        );
        best = betterFloatingAlignment(best, otherBounds.minX() - groupBounds.minX(), 0.0);
        best = betterFloatingAlignment(best, otherBounds.maxX() - groupBounds.maxX(), 0.0);
        best = betterFloatingAlignment(best, otherBounds.centerX() - groupBounds.centerX(), 3.0);
        best = betterFloatingAlignment(best, otherBounds.minX() - groupBounds.maxX(), 8.0);
        best = betterFloatingAlignment(best, otherBounds.maxX() - groupBounds.minX(), 8.0);
        return best;
    }

    private FloatingAlignment defaultFloatingAlignment(double aMin, double aMax, double bMin, double bMax) {
        if (rangesOverlapEnough(aMin, aMax, bMin, bMax)) return new FloatingAlignment(0.0, 0.0);
        double gap = rangeGap(aMin, aMax, bMin, bMax);
        if (gap <= FLOATING_DOCKER_SNAP_RANGE_PADDING) {
            return new FloatingAlignment(0.0, FLOATING_DOCKER_SNAP_ALIGNMENT_DISTANCE + gap * 0.5);
        }
        return new FloatingAlignment(0.0, FLOATING_DOCKER_SNAP_ALIGNMENT_DISTANCE * 2.0 + gap);
    }

    private FloatingAlignment betterFloatingAlignment(FloatingAlignment best, double delta, double penalty) {
        double distance = Math.abs(delta);
        if (distance > FLOATING_DOCKER_SNAP_ALIGNMENT_DISTANCE) return best;
        double score = distance + penalty;
        if (score < best.score()) {
            return new FloatingAlignment(delta, score);
        }
        return best;
    }

    private double rangeGap(double aMin, double aMax, double bMin, double bMax) {
        if (aMax < bMin) return bMin - aMax;
        if (bMax < aMin) return aMin - bMax;
        return 0.0;
    }

    private void updateFloatingSnapLinksAfterDrag(List<FloatingDocker> group) {
        if (group == null || group.isEmpty()) return;
        Set<String> groupIds = new LinkedHashSet<>();
        for (FloatingDocker docker : group) {
            groupIds.add(docker.item.id());
        }
        for (FloatingDocker docker : group) {
            if (!isActiveFloatingDocker(docker)) continue;
            for (FloatingDocker other : floatingToolbarDockers.values()) {
                if (!isActiveFloatingDocker(other) || groupIds.contains(other.item.id())) continue;
                if (areFloatingDockersSnapped(docker, other)) {
                    addFloatingSnapLink(docker.item.id(), other.item.id());
                }
            }
        }
        persistWorkspacePrefsNow();
    }

    private boolean areFloatingDockersSnapped(FloatingDocker first, FloatingDocker second) {
        FloatingBounds a = floatingBounds(first);
        FloatingBounds b = floatingBounds(second);
        if (a == null || b == null) return false;
        boolean verticalOverlap = rangesTouchForSnapLink(a.minY(), a.maxY(), b.minY(), b.maxY());
        boolean horizontalOverlap = rangesTouchForSnapLink(a.minX(), a.maxX(), b.minX(), b.maxX());
        return (verticalOverlap
            && (Math.abs(a.maxX() - b.minX()) <= FLOATING_DOCKER_SNAP_LINK_TOLERANCE
                || Math.abs(a.minX() - b.maxX()) <= FLOATING_DOCKER_SNAP_LINK_TOLERANCE))
            || (horizontalOverlap
                && (Math.abs(a.maxY() - b.minY()) <= FLOATING_DOCKER_SNAP_LINK_TOLERANCE
                    || Math.abs(a.minY() - b.maxY()) <= FLOATING_DOCKER_SNAP_LINK_TOLERANCE));
    }

    private FloatingBounds floatingBounds(FloatingDocker docker) {
        if (docker == null) return null;
        double width = docker.getBoundsInParent().getWidth();
        double height = docker.getBoundsInParent().getHeight();
        if (width <= 0.0) width = Math.max(1.0, docker.prefWidth(-1));
        if (height <= 0.0) height = Math.max(1.0, docker.prefHeight(-1));
        double x = docker.getLayoutX();
        double y = docker.getLayoutY();
        return new FloatingBounds(x, y, x + width, y + height);
    }

    private FloatingBounds floatingBounds(List<FloatingDocker> dockers) {
        if (dockers == null || dockers.isEmpty()) return null;
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        boolean found = false;
        for (FloatingDocker docker : dockers) {
            FloatingBounds bounds = floatingBounds(docker);
            if (bounds == null) continue;
            minX = Math.min(minX, bounds.minX());
            minY = Math.min(minY, bounds.minY());
            maxX = Math.max(maxX, bounds.maxX());
            maxY = Math.max(maxY, bounds.maxY());
            found = true;
        }
        return found ? new FloatingBounds(minX, minY, maxX, maxY) : null;
    }

    private boolean rangesOverlapEnough(double aMin, double aMax, double bMin, double bMax) {
        double overlap = Math.min(aMax, bMax) - Math.max(aMin, bMin);
        if (overlap <= 0.0) return false;
        double minSpan = Math.min(Math.max(1.0, aMax - aMin), Math.max(1.0, bMax - bMin));
        double required = Math.min(32.0, Math.max(6.0, minSpan * FLOATING_DOCKER_SNAP_OVERLAP_RATIO));
        return overlap >= required;
    }

    private boolean rangesTouchForSnapLink(double aMin, double aMax, double bMin, double bMax) {
        if (rangesOverlapEnough(aMin, aMax, bMin, bMax)) return true;
        return rangeGap(aMin, aMax, bMin, bMax) <= FLOATING_DOCKER_SNAP_LINK_TOLERANCE;
    }

    private String formatFloatingSnapLinks() {
        List<String> records = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : floatingDockerSnapLinks.entrySet()) {
            String first = entry.getKey();
            for (String second : entry.getValue()) {
                if (!floatingToolbarDockers.containsKey(first) || !floatingToolbarDockers.containsKey(second)) continue;
                String key = first.compareTo(second) <= 0 ? first + "~" + second : second + "~" + first;
                if (emitted.add(key)) {
                    records.add(key);
                }
            }
        }
        return String.join(",", records);
    }

    private void applyFloatingSnapLinks(String raw) {
        floatingDockerSnapLinks.clear();
        if (raw == null || raw.isBlank()) return;
        for (String record : raw.split(",")) {
            String[] ids = record.trim().split("~", 2);
            if (ids.length != 2) continue;
            addFloatingSnapLink(ids[0].trim(), ids[1].trim());
        }
    }

    private ContextMenu createFloatingDockerContextMenu(FloatingDocker docker) {
        ContextMenu menu = new ContextMenu();
        if (docker == null) return menu;
        String itemId = docker.item.id();
        boolean inEdgeBar = edgeBarDockItemIds.contains(itemId);
        boolean snapped = hasFloatingSnapLinks(itemId);
        MenuItem miUnsnapThis = new MenuItem("Unsnap This Docker");
        miUnsnapThis.setDisable(inEdgeBar || !snapped);
        miUnsnapThis.setOnAction(event -> unsnapFloatingDocker(itemId));
        MenuItem miUnsnapCluster = new MenuItem("Unsnap Cluster");
        miUnsnapCluster.setDisable(inEdgeBar || floatingSnapGroupIds(itemId).size() <= 1);
        miUnsnapCluster.setOnAction(event -> unsnapFloatingDockerCluster(itemId));
        MenuItem miHide = new MenuItem("Kill / Hide " + docker.item.title());
        miHide.setOnAction(event -> hideDockItem(docker.item));
        MenuItem miEdgeBar = new MenuItem(inEdgeBar ? "Pop Out From Edge Bar" : "Send to Edge Bar");
        miEdgeBar.setOnAction(event -> {
            if (inEdgeBar) {
                restoreEdgeBarDockItem(docker.item);
            } else {
                sendDockItemToEdgeBar(docker.item);
            }
        });
        MenuItem miToolbar = new MenuItem("Return to Toolbar Row");
        miToolbar.setOnAction(event -> dockFloatingToolbarItem(docker.item));
        menu.getItems().addAll(miUnsnapThis, miUnsnapCluster, new SeparatorMenuItem(), miEdgeBar, miHide, miToolbar);
        return menu;
    }

    private void hideFloatingToolbarsExcept(String... visibleIds) {
        Set<String> visible = visibleIds == null
            ? Set.of()
            : new LinkedHashSet<>(List.of(visibleIds));
        for (DockItem item : dockItems.values()) {
            if (item.homeToolbar() && !visible.contains(item.id())) {
                setFloatingDockerVisibility(item.id(), false);
            }
        }
    }

    private Menu createMoveDockItemMenu(DockItem item) {
        Menu moveMenu = new Menu("Move To");
        if (item != null && item.homeToolbar()) {
            MenuItem miShow = new MenuItem(floatingToolbarDockers.containsKey(item.id()) ? "Show Floating" : "Float");
            miShow.setOnAction(ev -> floatToolbarDockItem(item));
            MenuItem miHide = new MenuItem("Hide Floating");
            miHide.setDisable(!floatingToolbarDockers.containsKey(item.id()));
            miHide.setOnAction(ev -> hideDockItem(item));
            MenuItem miEdgeBar = new MenuItem(edgeBarDockItemIds.contains(item.id()) ? "Show From Edge Bar" : "Send to Edge Bar");
            miEdgeBar.setOnAction(ev -> {
                if (edgeBarDockItemIds.contains(item.id())) {
                    restoreEdgeBarDockItem(item);
                } else {
                    sendDockItemToEdgeBar(item);
                }
            });
            MenuItem miDockToolbar = new MenuItem("Return to Toolbar Row");
            miDockToolbar.setDisable(!floatingToolbarDockers.containsKey(item.id()));
            miDockToolbar.setOnAction(ev -> dockFloatingToolbarItem(item));
            moveMenu.getItems().addAll(miShow, miEdgeBar, miHide, miDockToolbar);
            return moveMenu;
        }
        MenuItem miNewTop = new MenuItem("New Top Container");
        miNewTop.setOnAction(ev -> moveDockItemToNewSlot(item, firstAvailableDockGroup("controls-primary", "workspace-top")));
        MenuItem miNewSide = new MenuItem("New Side Container");
        miNewSide.setOnAction(ev -> moveDockItemToNewSlot(item, firstAvailableDockGroup("workspace-main", "workspace-top")));
        MenuItem miNewBottom = new MenuItem("New Bottom Container");
        miNewBottom.setOnAction(ev -> moveDockItemToNewSlot(item, firstAvailableDockGroup("workspace-bottom", "workspace-top")));
        moveMenu.getItems().addAll(miNewTop, miNewSide, miNewBottom);
        if (!dockSlots.isEmpty()) {
            moveMenu.getItems().add(new SeparatorMenuItem());
        }
        for (DockSlot slot : dockSlots.values()) {
            MenuItem miSlot = new MenuItem(dockSlotMenuLabel(slot));
            miSlot.setOnAction(ev -> moveDockItemToSlot(item, slot.slotId()));
            moveMenu.getItems().add(miSlot);
        }
        return moveMenu;
    }

    private SplitPane firstAvailableDockGroup(String... groupIds) {
        if (groupIds != null) {
            for (String groupId : groupIds) {
                SplitPane group = dockGroupsById.get(groupId);
                if (group != null) return group;
            }
        }
        return dockGroupsById.values().stream().findFirst().orElse(null);
    }

    private String dockSlotMenuLabel(DockSlot slot) {
        if (slot == null) return "Dock Slot";
        DockItem defaultItem = dockItems.get(slot.slotId());
        String base = defaultItem == null ? slot.slotId() : defaultItem.title();
        DockItem current = slot.item();
        if (current == null) return base + " Area (Empty)";
        int count = slot.items().size();
        return count <= 1
            ? base + " Area (" + current.title() + ")"
            : base + " Area (" + current.title() + " +" + (count - 1) + ")";
    }

    private boolean moveDockItemToNewSlot(DockItem item, SplitPane group) {
        if (item == null || group == null) return false;
        if (previewFocusMode && (item.contentNode() == previewPane || item.contentNode() == timelinePanel)) {
            exitFullscreenPreview();
        }
        hiddenDockItemIds.remove(item.id());
        item.contentNode().setManaged(true);
        item.contentNode().setVisible(true);

        DockSlot sourceSlot = findDockSlotContaining(item.contentNode());
        if (sourceSlot != null) {
            sourceSlot.removeItem(item);
        } else {
            CollapsibleToolbarCluster sourceCluster = item.toolbarCluster();
            if (sourceCluster != null && toolbarDockItems.get(sourceCluster.getClusterKey()) == item) {
                toolbarPane.removeCluster(sourceCluster);
                toolbarDockItems.remove(sourceCluster.getClusterKey());
                item.clearToolbarCluster();
            }
        }
        String slotId = "custom-dock-" + dynamicDockSlotCounter++;
        createDynamicDockSlot(slotId, item, group, group.getItems().size());
        refreshAfterDockSwap();
        return true;
    }

    private boolean moveDockItemToSlot(DockItem item, String targetSlotId) {
        if (item == null || targetSlotId == null) return false;
        DockSlot targetSlot = dockSlots.get(targetSlotId);
        if (targetSlot == null) return false;
        if (previewFocusMode && (item.contentNode() == previewPane || item.contentNode() == timelinePanel)) {
            exitFullscreenPreview();
        }
        if (targetSlot.containsItem(item)) {
            hiddenDockItemIds.remove(item.id());
            item.contentNode().setManaged(true);
            item.contentNode().setVisible(true);
            targetSlot.activateItem(item);
            refreshAfterDockSwap();
            return true;
        }

        hiddenDockItemIds.remove(item.id());
        item.contentNode().setManaged(true);
        item.contentNode().setVisible(true);

        DockSlot sourceSlot = findDockSlotContaining(item.contentNode());
        if (sourceSlot != null) {
            sourceSlot.removeItem(item);
        } else {
            CollapsibleToolbarCluster sourceCluster = item.toolbarCluster();
            if (sourceCluster != null && toolbarDockItems.get(sourceCluster.getClusterKey()) == item) {
                toolbarPane.removeCluster(sourceCluster);
                toolbarDockItems.remove(sourceCluster.getClusterKey());
                item.clearToolbarCluster();
            }
        }
        targetSlot.addItem(item);
        refreshAfterDockSwap();
        return true;
    }

    private DockSlot findDockSlotContaining(Node node) {
        if (node == null) return null;
        for (DockSlot slot : dockSlots.values()) {
            if (slot.containsNode(node)) {
                return slot;
            }
        }
        return null;
    }

    private DockItem findDockItemContaining(Node node) {
        DockSlot slot = findDockSlotContaining(node);
        return slot == null ? null : slot.itemForNode(node);
    }

    private DockItem dockItemFromPayload(String payload) {
        if (payload == null) return null;
        if (payload.startsWith(DOCK_DRAG_SLOT_PREFIX)) {
            DockSlot slot = dockSlots.get(payload.substring(DOCK_DRAG_SLOT_PREFIX.length()));
            return slot == null ? null : slot.item();
        }
        if (payload.startsWith(DOCK_DRAG_TOOLBAR_PREFIX)) {
            String key = payload.substring(DOCK_DRAG_TOOLBAR_PREFIX.length());
            DockItem item = toolbarDockItems.get(key);
            return item != null ? item : dockItems.get(key);
        }
        return null;
    }

    private static String dockPayload(Dragboard dragboard) {
        if (dragboard == null || !dragboard.hasString()) return null;
        String value = dragboard.getString();
        if (value == null) return null;
        return value.startsWith(DOCK_DRAG_SLOT_PREFIX) || value.startsWith(DOCK_DRAG_TOOLBAR_PREFIX)
            ? value
            : null;
    }

    private static boolean isDockDragPayload(String payload, String currentToolbarKey) {
        if (payload == null) return false;
        if (payload.startsWith(DOCK_DRAG_SLOT_PREFIX)) return true;
        if (!payload.startsWith(DOCK_DRAG_TOOLBAR_PREFIX)) return false;
        String source = payload.substring(DOCK_DRAG_TOOLBAR_PREFIX.length());
        return currentToolbarKey == null || !currentToolbarKey.equals(source);
    }

    private boolean isFloatingToolbarPayload(String payload) {
        DockItem item = dockItemFromPayload(payload);
        return item != null && item.homeToolbar();
    }

    private static List<String> parseDockIdList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> ids = new ArrayList<>();
        for (String part : raw.split(",")) {
            String id = part.trim();
            if (!id.isEmpty() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String formatDockIdList(Iterable<String> ids) {
        if (ids == null) return "";
        List<String> cleaned = new ArrayList<>();
        for (String id : ids) {
            if (id != null && !id.isBlank() && !cleaned.contains(id.trim())) {
                cleaned.add(id.trim());
            }
        }
        return String.join(",", cleaned);
    }

    private static List<String> parseDockSlotItemList(String raw) {
        if (raw == null || raw.isBlank() || EMPTY_DOCK_VALUE.equals(raw.trim())) return List.of();
        List<String> ids = new ArrayList<>();
        for (String part : raw.split("\\|")) {
            String id = part.trim();
            if (!id.isEmpty() && !EMPTY_DOCK_VALUE.equals(id) && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String formatDockSlotItemList(List<DockItem> items) {
        if (items == null || items.isEmpty()) return EMPTY_DOCK_VALUE;
        List<String> ids = new ArrayList<>();
        for (DockItem item : items) {
            if (item != null && !ids.contains(item.id())) {
                ids.add(item.id());
            }
        }
        return ids.isEmpty() ? EMPTY_DOCK_VALUE : String.join(DOCK_SLOT_ITEM_DELIMITER, ids);
    }

    private void restoreDynamicDockSlots(String raw) {
        if (raw == null || raw.isBlank()) return;
        for (String record : raw.split(",")) {
            String[] parts = record.trim().split("@", 2);
            if (parts.length != 2) continue;
            String slotId = parts[0].trim();
            String groupId = parts[1].trim();
            if (slotId.isEmpty() || groupId.isEmpty() || dockSlots.containsKey(slotId)) continue;
            SplitPane group = dockGroupsById.get(groupId);
            if (group == null) continue;
            createDynamicDockSlot(slotId, null, group, group.getItems().size());
            updateDynamicDockSlotCounter(slotId);
        }
    }

    private void updateDynamicDockSlotCounter(String slotId) {
        if (slotId == null) return;
        int dash = slotId.lastIndexOf('-');
        if (dash < 0 || dash >= slotId.length() - 1) return;
        try {
            dynamicDockSlotCounter = Math.max(dynamicDockSlotCounter, Integer.parseInt(slotId.substring(dash + 1)) + 1);
        } catch (NumberFormatException ignored) {
            // reason: dynamic slot identifiers can be hand-edited; the next generated id remains valid
        }
    }

    private String formatDynamicDockSlots() {
        List<String> records = new ArrayList<>();
        for (DockSlot slot : dockSlots.values()) {
            if (!slot.dynamic || !slot.hasItems() || slot.homeGroup == null) continue;
            String groupId = dockGroupIds.get(slot.homeGroup);
            if (groupId == null || groupId.isBlank()) continue;
            records.add(slot.slotId() + "@" + groupId);
        }
        return String.join(",", records);
    }

    private DockSlot findFirstEmptyDockSlot() {
        for (DockSlot slot : dockSlots.values()) {
            if (!slot.hasItems()) return slot;
        }
        return null;
    }

    private boolean isDockItemVisible(String itemId) {
        DockItem item = dockItems.get(itemId);
        return item != null && isDockItemVisible(item);
    }

    private boolean isDockItemVisible(DockItem item) {
        if (item == null) return false;
        if (edgeBarDockItemIds.contains(item.id())) return false;
        FloatingDocker floating = floatingToolbarDockers.get(item.id());
        if (floating != null) {
            return floating.isVisible() && floating.isManaged() && !hiddenDockItemIds.contains(item.id());
        }
        for (DockSlot slot : dockSlots.values()) {
            if (slot.containsItem(item)) return true;
        }
        CollapsibleToolbarCluster cluster = item.toolbarCluster();
        return cluster != null && toolbarPane != null && toolbarPane.getClustersSnapshot().contains(cluster);
    }

    private void hideDockItem(DockItem item) {
        if (item == null) return;
        edgeBarDockItemIds.remove(item.id());
        if (hiddenDockItemIds.contains(item.id())) {
            refreshEdgeBar();
            return;
        }
        FloatingDocker floating = floatingToolbarDockers.get(item.id());
        if (floating != null) {
            hiddenDockItemIds.add(item.id());
            removeFloatingSnapLinks(item.id());
            floating.hideSmoothly();
            refreshEdgeBar();
            refreshAfterDockSwap();
            return;
        }
        if (previewFocusMode && (item.contentNode() == previewPane || item.contentNode() == timelinePanel)) {
            exitFullscreenPreview();
        }
        DockSlot slot = findDockSlotContaining(item.contentNode());
        if (slot != null) {
            slot.removeItem(item);
        }
        CollapsibleToolbarCluster cluster = item.toolbarCluster();
        if (cluster != null) {
            toolbarPane.removeCluster(cluster);
            toolbarDockItems.remove(cluster.getClusterKey());
            item.clearToolbarCluster();
        }
        item.contentNode().setManaged(false);
        item.contentNode().setVisible(false);
        hiddenDockItemIds.add(item.id());
        refreshEdgeBar();
        refreshAfterDockSwap();
    }

    private void showDockItem(DockItem item) {
        if (item == null) return;
        edgeBarDockItemIds.remove(item.id());
        if (isDockItemVisible(item)) return;
        hiddenDockItemIds.remove(item.id());
        FloatingDocker floating = floatingToolbarDockers.get(item.id());
        if (floating != null) {
            attachFloatingDockerToLayer(floating);
            floating.showSmoothly();
            refreshAfterDockSwap();
            return;
        }
        item.contentNode().setManaged(true);
        item.contentNode().setVisible(true);
        DockSlot targetSlot = dockSlots.get(item.defaultSlotId());
        if (targetSlot != null) {
            targetSlot.addItem(item);
        } else if (item.homeToolbar()) {
            CollapsibleToolbarCluster cluster = item.asToolbarCluster();
            toolbarPane.addCluster(cluster);
            toolbarDockItems.put(cluster.getClusterKey(), item);
        } else {
            targetSlot = findFirstEmptyDockSlot();
            if (targetSlot != null) {
                targetSlot.addItem(item);
            } else {
                moveDockItemToNewSlot(item, firstAvailableDockGroup("workspace-top", "workspace-main"));
            }
        }
        refreshAfterDockSwap();
    }

    private void syncCodePaneVisibilityState() {
        DockItem codeItem = dockItems.get("code");
        if (codeItem != null) {
            codePaneVisible = isDockItemVisible(codeItem);
        }
    }

    private void resetDockArrangement() {
        if (previewFocusMode) {
            exitFullscreenPreview();
        }
        applyingDockLayoutPrefs = true;
        try {
            hiddenDockItemIds.clear();
            edgeBarDockItemIds.clear();
            for (CollapsibleToolbarCluster cluster : toolbarPane.getClustersSnapshot()) {
                toolbarPane.removeCluster(cluster);
            }
            toolbarDockItems.clear();
            for (DockSlot slot : new ArrayList<>(dockSlots.values())) {
                DockItem item = dockItems.get(slot.slotId());
                if (item != null && !slot.dynamic) {
                    slot.setItem(item);
                } else {
                    slot.clearItem();
                }
            }
            for (DockItem item : dockItems.values()) {
                if (isDockItemVisible(item)) continue;
                showDockItem(item);
            }
        } finally {
            applyingDockLayoutPrefs = false;
        }
        refreshAfterDockSwap();
    }

    private enum FloatingEdge {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }

    private final class FloatingDockerLayer extends Pane {
        FloatingDockerLayer() {
            setPickOnBounds(false);
        }

        @Override
        public boolean contains(double localX, double localY) {
            if (floatingLayerDropCaptureEnabled) {
                return localX >= 0.0 && localY >= 0.0 && localX <= getWidth() && localY <= getHeight();
            }
            for (int i = getChildren().size() - 1; i >= 0; i--) {
                Node child = getChildren().get(i);
                if (!child.isVisible() || child.isMouseTransparent()) continue;
                if (!child.getBoundsInParent().contains(localX, localY)) continue;
                var childPoint = child.parentToLocal(localX, localY);
                if (child.contains(childPoint.getX(), childPoint.getY())) {
                    return true;
                }
            }
            return false;
        }
    }

    private final class EdgeBar extends Pane {
        private final VBox panel = new VBox(8);
        private final VBox itemList = new VBox(8);
        private final Button addButton = makeEdgeBarIconButton(CssIcon.plus("#ffffff"), "Add docker to edge bar");
        private final ToggleButton resizeButton = makeEdgeBarToggleButton(CssIcon.openInFull("#f0c36a"), "Resize edge bar");
        private final Button handle = new Button();
        private final Rectangle panelClip = new Rectangle();
        private final Region resizeGrip = new Region();
        private double panelWidth = EDGE_BAR_PANEL_WIDTH;
        private double panelHeight = EDGE_BAR_PANEL_HEIGHT;
        private boolean expanded;
        private boolean resizeMode;
        private boolean handleHover;
        private double handlePressSceneX;
        private double handlePressSceneY;
        private double resizePressSceneX;
        private double resizePressSceneY;
        private double resizeStartWidth;
        private double resizeStartHeight;
        private boolean handleDragMoved;
        private boolean suppressNextHandleAction;

        EdgeBar() {
            setPickOnBounds(false);
            setManaged(false);
            setVisible(false);
            setMouseTransparent(true);

            addButton.setOnAction(event -> {
                ContextMenu menu = createEdgeBarAddMenu();
                menu.show(addButton, Side.BOTTOM, 0, 4);
                event.consume();
            });
            resizeButton.setOnAction(event -> {
                setResizeMode(resizeButton.isSelected());
                if (resizeMode) {
                    setExpanded(true, true);
                }
                event.consume();
            });

            HBox controls = new HBox(6, resizeButton, addButton);
            controls.setAlignment(Pos.CENTER_RIGHT);
            controls.setPadding(new Insets(0, 0, 2, 0));

            ScrollPane scroll = new ScrollPane(itemList);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
            VBox.setVgrow(scroll, Priority.ALWAYS);

            panel.getChildren().addAll(controls, scroll);
            panel.setPadding(new Insets(10));
            panel.setStyle(
                "-fx-background-color: rgba(54,54,52,0.98);"
                    + "-fx-background-radius: 14;"
                    + "-fx-border-color: rgba(255,255,255,0.12);"
                    + "-fx-border-radius: 14;"
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.30), 18, 0.22, 0, 5);"
            );
            panelClip.setArcWidth(26);
            panelClip.setArcHeight(26);
            panel.setClip(panelClip);
            updatePanelStyle();

            resizeGrip.setManaged(false);
            resizeGrip.setVisible(false);
            resizeGrip.setMouseTransparent(true);
            Tooltip.install(resizeGrip, new Tooltip("Drag to resize edge bar"));
            resizeGrip.setOnMousePressed(event -> {
                resizePressSceneX = event.getSceneX();
                resizePressSceneY = event.getSceneY();
                resizeStartWidth = panelWidth;
                resizeStartHeight = panelHeight;
                event.consume();
            });
            resizeGrip.setOnMouseDragged(event -> {
                resizeFromScene(event.getSceneX(), event.getSceneY());
                event.consume();
            });
            resizeGrip.setOnMouseReleased(event -> {
                persistWorkspacePrefsNow();
                event.consume();
            });

            handle.setText("");
            handle.setMnemonicParsing(false);
            handle.setFocusTraversable(false);
            handle.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            installToolbarTooltip(handle, "Edge Bar");
            handle.setOnAction(event -> {
                if (suppressNextHandleAction) {
                    suppressNextHandleAction = false;
                    event.consume();
                    return;
                }
                setExpanded(!expanded, true);
                event.consume();
            });
            handle.setOnMousePressed(event -> {
                handlePressSceneX = event.getSceneX();
                handlePressSceneY = event.getSceneY();
                handleDragMoved = false;
                suppressNextHandleAction = false;
                event.consume();
            });
            handle.setOnMouseDragged(event -> {
                double dx = event.getSceneX() - handlePressSceneX;
                double dy = event.getSceneY() - handlePressSceneY;
                if (!handleDragMoved && Math.hypot(dx, dy) < 4.0) {
                    return;
                }
                handleDragMoved = true;
                suppressNextHandleAction = true;
                setExpanded(false, false);
                updateEdgeBarPlacementFromScene(event.getSceneX(), event.getSceneY());
                event.consume();
            });
            handle.setOnMouseReleased(event -> {
                if (handleDragMoved) {
                    updateEdgeBarPlacementFromScene(event.getSceneX(), event.getSceneY());
                    persistWorkspacePrefsNow();
                    handleDragMoved = false;
                    updateHandleStyle();
                    event.consume();
                }
            });
            handle.setOnMouseEntered(event -> {
                handleHover = true;
                updateHandleStyle();
            });
            handle.setOnMouseExited(event -> {
                handleHover = false;
                updateHandleStyle();
            });

            getChildren().addAll(panel, resizeGrip, handle);
            setOnMouseEntered(event -> setExpanded(true, true));
            setOnMouseExited(event -> {
                if (!resizeMode && !containsScenePoint(event.getSceneX(), event.getSceneY())) {
                    setExpanded(false, true);
                }
            });
        }

        void refresh() {
            setManaged(edgeBarEnabled);
            setVisible(edgeBarEnabled);
            setMouseTransparent(!edgeBarEnabled);
            itemList.getChildren().clear();
            for (String itemId : edgeBarDockItemIds) {
                DockItem item = dockItems.get(itemId);
                if (item == null || !item.homeToolbar()) continue;
                FloatingDocker floating = ensureFloatingToolbarDocker(item, 18.0, 18.0);
                if (floating == null) continue;
                detachNode(floating);
                detachNode(floating.restoreTab());
                floating.setEdgeBarMounted(true);
                itemList.getChildren().add(floating);
                VBox.setVgrow(floating, Priority.NEVER);
            }
            updateHandleStyle();
            if (edgeBarEnabled) {
                toFront();
            } else {
                expanded = false;
            }
        }

        void relayout() {
            if (!edgeBarEnabled) {
                setManaged(false);
                setVisible(false);
                setMouseTransparent(true);
                return;
            }
            Scene scene = getScene();
            double sceneWidth = scene == null ? 1280.0 : Math.max(320.0, scene.getWidth());
            double sceneHeight = scene == null ? 720.0 : Math.max(240.0, scene.getHeight());
            boolean vertical = edgeBarEdge == FloatingEdge.LEFT || edgeBarEdge == FloatingEdge.RIGHT;
            panelWidth = vertical
                ? clampDouble(edgeBarPreferredWidth, EDGE_BAR_PANEL_MIN_WIDTH, Math.max(EDGE_BAR_PANEL_MIN_WIDTH, sceneWidth - EDGE_BAR_HANDLE_THICKNESS - 32.0))
                : clampDouble(edgeBarPreferredWidth, 260.0, Math.max(260.0, sceneWidth - 48.0));
            panelHeight = vertical
                ? clampDouble(edgeBarPreferredHeight, 220.0, Math.max(220.0, sceneHeight - 84.0))
                : clampDouble(edgeBarPreferredHeight, EDGE_BAR_PANEL_MIN_HEIGHT, Math.max(EDGE_BAR_PANEL_MIN_HEIGHT, sceneHeight - EDGE_BAR_HANDLE_THICKNESS - 64.0));

            double maxPanelX = Math.max(0.0, sceneWidth - panelWidth);
            double maxPanelY = Math.max(0.0, sceneHeight - panelHeight);
            double panelX = maxPanelX * edgeBarOffsetRatio;
            double panelY = maxPanelY * edgeBarOffsetRatio;
            if (edgeBarEdge == FloatingEdge.LEFT) {
                resizeRelocate(0.0, panelY, panelWidth + EDGE_BAR_HANDLE_THICKNESS, panelHeight);
                panel.resizeRelocate(-panelWidth, 0.0, panelWidth, panelHeight);
                handle.resizeRelocate(0.0, handleOffsetY(), EDGE_BAR_HANDLE_THICKNESS, EDGE_BAR_HANDLE_LENGTH);
            } else if (edgeBarEdge == FloatingEdge.RIGHT) {
                resizeRelocate(sceneWidth - EDGE_BAR_HANDLE_THICKNESS, panelY, panelWidth + EDGE_BAR_HANDLE_THICKNESS, panelHeight);
                panel.resizeRelocate(EDGE_BAR_HANDLE_THICKNESS, 0.0, panelWidth, panelHeight);
                handle.resizeRelocate(0.0, handleOffsetY(), EDGE_BAR_HANDLE_THICKNESS, EDGE_BAR_HANDLE_LENGTH);
            } else if (edgeBarEdge == FloatingEdge.TOP) {
                resizeRelocate(panelX, 0.0, panelWidth, panelHeight + EDGE_BAR_HANDLE_THICKNESS);
                panel.resizeRelocate(0.0, -panelHeight, panelWidth, panelHeight);
                handle.resizeRelocate(handleOffsetX(), 0.0, EDGE_BAR_HANDLE_LENGTH, EDGE_BAR_HANDLE_THICKNESS);
            } else {
                resizeRelocate(panelX, sceneHeight - EDGE_BAR_HANDLE_THICKNESS, panelWidth, panelHeight + EDGE_BAR_HANDLE_THICKNESS);
                panel.resizeRelocate(0.0, EDGE_BAR_HANDLE_THICKNESS, panelWidth, panelHeight);
                handle.resizeRelocate(handleOffsetX(), 0.0, EDGE_BAR_HANDLE_LENGTH, EDGE_BAR_HANDLE_THICKNESS);
            }
            panel.setPrefSize(panelWidth, panelHeight);
            panelClip.setWidth(panelWidth);
            panelClip.setHeight(panelHeight);
            updateHandleStyle();
            updateResizeGrip();
            updateResizeButtonStyle();
            applyPanelOffset(false);
            toFront();
        }

        void setExpanded(boolean expanded, boolean animate) {
            if (!edgeBarEnabled) return;
            this.expanded = expanded;
            panel.setMouseTransparent(!expanded);
            toFront();
            updateHandleStyle();
            updateResizeGrip();
            applyPanelOffset(animate);
        }

        boolean containsScenePoint(double sceneX, double sceneY) {
            var local = sceneToLocal(sceneX, sceneY);
            return contains(local.getX(), local.getY());
        }

        @Override
        public boolean contains(double localX, double localY) {
            if (!edgeBarEnabled || !isVisible() || isMouseTransparent()) return false;
            if (containsChild(handle, localX, localY)) return true;
            if (containsChild(resizeGrip, localX, localY)) return true;
            return expanded && containsChild(panel, localX, localY);
        }

        private ContextMenu createEdgeBarAddMenu() {
            ContextMenu menu = new ContextMenu();
            boolean added = false;
            for (DockItem item : dockItems.values()) {
                if (item == null || !item.homeToolbar() || edgeBarDockItemIds.contains(item.id())) continue;
                MenuItem addItem = new MenuItem(item.title());
                addItem.setOnAction(event -> sendDockItemToEdgeBar(item));
                menu.getItems().add(addItem);
                added = true;
            }
            if (!added) {
                MenuItem empty = new MenuItem("No Available Floating Dockers");
                empty.setDisable(true);
                menu.getItems().add(empty);
            }
            return menu;
        }

        private void updateEdgeBarPlacementFromScene(double sceneX, double sceneY) {
            Scene scene = getScene();
            if (scene == null) return;
            edgeBarEdge = nearestEdge(sceneX, sceneY, scene);
            edgeBarOffsetRatio = edgeOffsetRatio(edgeBarEdge, sceneX, sceneY, scene);
            relayout();
        }

        private FloatingEdge nearestEdge(double sceneX, double sceneY, Scene scene) {
            double left = sceneX;
            double right = scene.getWidth() - sceneX;
            double top = sceneY;
            double bottom = scene.getHeight() - sceneY;
            double nearest = Math.min(Math.min(left, right), Math.min(top, bottom));
            if (nearest == left) return FloatingEdge.LEFT;
            if (nearest == right) return FloatingEdge.RIGHT;
            if (nearest == top) return FloatingEdge.TOP;
            return FloatingEdge.BOTTOM;
        }

        private double edgeOffsetRatio(FloatingEdge edge, double sceneX, double sceneY, Scene scene) {
            if (edge == FloatingEdge.LEFT || edge == FloatingEdge.RIGHT) {
                double maxPanelY = Math.max(1.0, scene.getHeight() - panelHeight);
                return clampDouble((sceneY - panelHeight * 0.5) / maxPanelY, 0.0, 1.0);
            }
            double maxPanelX = Math.max(1.0, scene.getWidth() - panelWidth);
            return clampDouble((sceneX - panelWidth * 0.5) / maxPanelX, 0.0, 1.0);
        }

        private void setResizeMode(boolean enabled) {
            resizeMode = enabled;
            resizeButton.setSelected(enabled);
            updatePanelStyle();
            updateResizeButtonStyle();
            updateResizeGrip();
        }

        private void resizeFromScene(double sceneX, double sceneY) {
            Scene scene = getScene();
            if (scene == null) return;
            boolean vertical = edgeBarEdge == FloatingEdge.LEFT || edgeBarEdge == FloatingEdge.RIGHT;
            double dx = sceneX - resizePressSceneX;
            double dy = sceneY - resizePressSceneY;
            double nextWidth = edgeBarEdge == FloatingEdge.RIGHT ? resizeStartWidth - dx : resizeStartWidth + dx;
            double nextHeight = edgeBarEdge == FloatingEdge.BOTTOM ? resizeStartHeight - dy : resizeStartHeight + dy;
            edgeBarPreferredWidth = clampDouble(nextWidth, vertical ? EDGE_BAR_PANEL_MIN_WIDTH : 260.0, maxEdgeBarWidth(scene, vertical));
            edgeBarPreferredHeight = clampDouble(nextHeight, vertical ? 220.0 : EDGE_BAR_PANEL_MIN_HEIGHT, maxEdgeBarHeight(scene, vertical));
            expanded = true;
            panel.setMouseTransparent(false);
            relayout();
        }

        private double maxEdgeBarWidth(Scene scene, boolean vertical) {
            double sceneWidth = scene == null ? 1280.0 : Math.max(320.0, scene.getWidth());
            return vertical
                ? Math.max(EDGE_BAR_PANEL_MIN_WIDTH, sceneWidth - EDGE_BAR_HANDLE_THICKNESS - 32.0)
                : Math.max(260.0, sceneWidth - 48.0);
        }

        private double maxEdgeBarHeight(Scene scene, boolean vertical) {
            double sceneHeight = scene == null ? 720.0 : Math.max(240.0, scene.getHeight());
            return vertical
                ? Math.max(220.0, sceneHeight - 84.0)
                : Math.max(EDGE_BAR_PANEL_MIN_HEIGHT, sceneHeight - EDGE_BAR_HANDLE_THICKNESS - 64.0);
        }

        private void updatePanelStyle() {
            panel.setStyle(
                "-fx-background-color: rgba(54,54,52,0.98);"
                    + "-fx-background-radius: 14;"
                    + "-fx-border-color: " + (resizeMode ? "rgba(240,157,61,0.72)" : "rgba(255,255,255,0.12)") + ";"
                    + "-fx-border-radius: 14;"
                    + "-fx-effect: " + (resizeMode
                        ? "dropshadow(gaussian, rgba(240,157,61,0.24), 20, 0.22, 0, 4)"
                        : "dropshadow(gaussian, rgba(0,0,0,0.30), 18, 0.22, 0, 5)") + ";"
            );
        }

        private void updateResizeGrip() {
            boolean visible = edgeBarEnabled && expanded && resizeMode;
            resizeGrip.setVisible(visible);
            resizeGrip.setMouseTransparent(!visible);
            resizeGrip.setManaged(false);
            if (!visible) return;
            double size = 20.0;
            resizeGrip.resize(size, size);
            resizeGrip.setStyle(
                "-fx-background-color: rgba(240,157,61,0.38);"
                    + "-fx-background-radius: 7;"
                    + "-fx-border-color: rgba(240,157,61,0.9);"
                    + "-fx-border-radius: 7;"
                    + "-fx-effect: dropshadow(gaussian, rgba(240,157,61,0.55), 12, 0.42, 0, 0);"
            );
            double panelX = panel.getLayoutX() + panelTranslateXForState(true);
            double panelY = panel.getLayoutY() + panelTranslateYForState(true);
            double x;
            double y;
            if (edgeBarEdge == FloatingEdge.RIGHT) {
                x = panelX + 8.0;
                y = panelY + panelHeight - size - 8.0;
            } else if (edgeBarEdge == FloatingEdge.BOTTOM) {
                x = panelX + panelWidth - size - 8.0;
                y = panelY + 8.0;
            } else {
                x = panelX + panelWidth - size - 8.0;
                y = panelY + panelHeight - size - 8.0;
            }
            resizeGrip.relocate(x, y);
            resizeGrip.toFront();
            handle.toFront();
        }

        private void updateResizeButtonStyle() {
            resizeButton.setStyle(edgeBarButtonStyle(resizeMode));
        }

        private double handleOffsetX() {
            return clampDouble(panelWidth * 0.5 - EDGE_BAR_HANDLE_LENGTH * 0.5, 10.0, Math.max(10.0, panelWidth - EDGE_BAR_HANDLE_LENGTH - 10.0));
        }

        private double handleOffsetY() {
            return clampDouble(panelHeight * 0.5 - EDGE_BAR_HANDLE_LENGTH * 0.5, 10.0, Math.max(10.0, panelHeight - EDGE_BAR_HANDLE_LENGTH - 10.0));
        }

        private void applyPanelOffset(boolean animate) {
            double targetX = panelTranslateXForState(expanded);
            double targetY = panelTranslateYForState(expanded);
            if (animate) {
                TranslateTransition slide = new TranslateTransition(Duration.millis(expanded ? 170 : 135), panel);
                slide.setToX(targetX);
                slide.setToY(targetY);
                slide.setOnFinished(event -> updateResizeGrip());
                slide.play();
            } else {
                panel.setTranslateX(targetX);
                panel.setTranslateY(targetY);
                updateResizeGrip();
            }
        }

        private double panelTranslateXForState(boolean open) {
            if (!open) return 0.0;
            if (edgeBarEdge == FloatingEdge.LEFT) return panelWidth;
            if (edgeBarEdge == FloatingEdge.RIGHT) return -panelWidth;
            return 0.0;
        }

        private double panelTranslateYForState(boolean open) {
            if (!open) return 0.0;
            if (edgeBarEdge == FloatingEdge.TOP) return panelHeight;
            if (edgeBarEdge == FloatingEdge.BOTTOM) return -panelHeight;
            return 0.0;
        }

        private void updateHandleStyle() {
            boolean vertical = edgeBarEdge == FloatingEdge.LEFT || edgeBarEdge == FloatingEdge.RIGHT;
            String radius = vertical ? "7" : "7";
            handle.setMinSize(
                vertical ? EDGE_BAR_HANDLE_THICKNESS : EDGE_BAR_HANDLE_LENGTH,
                vertical ? EDGE_BAR_HANDLE_LENGTH : EDGE_BAR_HANDLE_THICKNESS
            );
            handle.setPrefSize(
                vertical ? EDGE_BAR_HANDLE_THICKNESS : EDGE_BAR_HANDLE_LENGTH,
                vertical ? EDGE_BAR_HANDLE_LENGTH : EDGE_BAR_HANDLE_THICKNESS
            );
            handle.setMaxSize(
                vertical ? EDGE_BAR_HANDLE_THICKNESS : EDGE_BAR_HANDLE_LENGTH,
                vertical ? EDGE_BAR_HANDLE_LENGTH : EDGE_BAR_HANDLE_THICKNESS
            );
            boolean active = expanded || handleHover || handleDragMoved;
            handle.setStyle(
                "-fx-background-color: " + (active ? "rgba(190,190,184,0.52)" : "rgba(168,168,162,0.34)") + ";"
                    + "-fx-background-radius: " + radius + ";"
                    + "-fx-border-color: " + (active ? "rgba(240,157,61,0.86)" : "rgba(255,255,255,0.28)") + ";"
                    + "-fx-border-radius: " + radius + ";"
                    + "-fx-padding: 0;"
                    + "-fx-effect: " + (active
                        ? "dropshadow(gaussian, rgba(240,157,61,0.72), 14, 0.48, 0, 0)"
                        : "dropshadow(gaussian, rgba(0,0,0,0.24), 5, 0.20, 0, 1)") + ";"
            );
            Tooltip tooltip = handle.getTooltip();
            if (tooltip != null) {
                tooltip.setText("Edge Bar - drag to reposition");
            }
        }

        private boolean containsChild(Node child, double localX, double localY) {
            if (child == null || !child.isVisible() || child.isMouseTransparent()) return false;
            if (!child.getBoundsInParent().contains(localX, localY)) return false;
            var childPoint = child.parentToLocal(localX, localY);
            return child.contains(childPoint.getX(), childPoint.getY());
        }

        private Button makeEdgeBarIconButton(Region icon, String tooltip) {
            Button button = new Button();
            button.setGraphic(icon);
            button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            button.setGraphicTextGap(0);
            button.setMnemonicParsing(false);
            button.setFocusTraversable(false);
            button.setMinSize(28, 26);
            button.setPrefSize(28, 26);
            button.setMaxSize(28, 26);
            button.setStyle(edgeBarButtonStyle(false));
            installToolbarTooltip(button, tooltip);
            return button;
        }

        private ToggleButton makeEdgeBarToggleButton(Region icon, String tooltip) {
            ToggleButton button = new ToggleButton();
            button.setGraphic(icon);
            button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            button.setGraphicTextGap(0);
            button.setMnemonicParsing(false);
            button.setFocusTraversable(false);
            button.setMinSize(28, 26);
            button.setPrefSize(28, 26);
            button.setMaxSize(28, 26);
            button.setStyle(edgeBarButtonStyle(false));
            installToolbarTooltip(button, tooltip);
            return button;
        }

        private String edgeBarButtonStyle(boolean active) {
            return "-fx-background-color: " + (active ? "rgba(83,67,43,0.96)" : "rgba(45,45,45,0.92)") + ";"
                + "-fx-background-radius: 7;"
                + "-fx-border-color: " + (active ? "rgba(240,157,61,0.82)" : "rgba(255,255,255,0.18)") + ";"
                + "-fx-border-radius: 7;"
                + "-fx-padding: 0;"
                + "-fx-effect: " + (active
                    ? "dropshadow(gaussian, rgba(240,157,61,0.42), 10, 0.36, 0, 0)"
                    : "none") + ";";
        }
    }

    private record FloatingBounds(double minX, double minY, double maxX, double maxY) {
        double width() {
            return Math.max(1.0, maxX - minX);
        }

        double height() {
            return Math.max(1.0, maxY - minY);
        }

        double centerX() {
            return minX + width() * 0.5;
        }

        double centerY() {
            return minY + height() * 0.5;
        }
    }

    private record FloatingAlignment(double delta, double score) {
        boolean active() {
            return Math.abs(delta) > 0.01 && Math.abs(delta) <= FLOATING_DOCKER_SNAP_ALIGNMENT_DISTANCE;
        }
    }

    private record FloatingSnapCandidate(double dx, double dy, double score) {
    }

    private record FloatingSnapAdjustment(double dx, double dy) {
        boolean active() {
            return Math.abs(dx) > 0.01 || Math.abs(dy) > 0.01;
        }
    }

    private final class FloatingDocker extends BorderPane {
        private final DockItem item;
        private final Label titleLabel = new Label();
        private final Button restoreTab;
        private boolean edgeBarMounted;
        private FloatingEdge stashEdge = FloatingEdge.RIGHT;
        private double dragSceneX;
        private double dragSceneY;
        private double dragLayoutX;
        private double dragLayoutY;
        private List<FloatingDocker> dragGroup = List.of();
        private final Map<FloatingDocker, double[]> dragGroupOrigins = new LinkedHashMap<>();

        FloatingDocker(DockItem item, double x, double y) {
            this.item = item;
            getStyleClass().add("puppeteer-floating-docker");
            setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            setLayoutX(x);
            setLayoutY(y);
            titleLabel.setText(item.title());
            titleLabel.getStyleClass().add("puppeteer-floating-docker-title");

            restoreTab = new Button(item.title());
            restoreTab.getStyleClass().add("puppeteer-floating-docker-restore-tab");
            restoreTab.setFocusTraversable(false);
            restoreTab.setMinHeight(24);
            restoreTab.setPrefHeight(24);
            restoreTab.setMaxHeight(24);
            restoreTab.setOnAction(event -> {
                showDockItem(item);
                event.consume();
            });
            Tooltip.install(restoreTab, new Tooltip("Restore " + item.title()));

            Label grip = new Label("::");
            grip.getStyleClass().add("puppeteer-dock-slot-grip");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button close = makeToolbarIconButton(CssIcon.clearX("#b9a489"), "Hide this floating docker");
            close.getStyleClass().add("puppeteer-dock-slot-close");
            close.setMinSize(22, 22);
            close.setPrefSize(22, 22);
            close.setMaxSize(22, 22);
            close.setOnAction(event -> {
                hideDockItem(item);
                event.consume();
            });
            HBox header = new HBox(7, grip, titleLabel, spacer, close);
            header.getStyleClass().add("puppeteer-floating-docker-header");
            header.setAlignment(Pos.CENTER_LEFT);
            header.setMinHeight(26);
            header.setPrefHeight(26);
            Tooltip.install(header, new Tooltip("Drag freely. Drag off screen to hide."));
            header.setOnMousePressed(this::beginDrag);
            header.setOnMouseDragged(this::drag);
            header.setOnMouseReleased(this::finishDrag);
            header.setOnContextMenuRequested(event -> {
                ContextMenu menu = createFloatingDockerContextMenu(this);
                menu.show(header, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            setOnContextMenuRequested(event -> {
                ContextMenu menu = createFloatingDockerContextMenu(this);
                menu.show(this, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            setTop(header);

            detachNode(item.contentNode());
            if (item.contentNode() instanceof CollapsibleToolbarCluster cluster) {
                cluster.setDockedChromeVisible(false);
            }
            setCenter(item.contentNode());
            showImmediately();
        }

        Button restoreTab() {
            return restoreTab;
        }

        void setEdgeBarMounted(boolean mounted) {
            edgeBarMounted = mounted;
            getStyleClass().remove("puppeteer-edge-bar-mounted-docker");
            if (mounted) {
                getStyleClass().add("puppeteer-edge-bar-mounted-docker");
                hideRestoreTabImmediately();
                setOpacity(1.0);
                setManaged(true);
                setVisible(true);
                setMouseTransparent(false);
                setLayoutX(0.0);
                setLayoutY(0.0);
                setTranslateX(0.0);
                setTranslateY(0.0);
                setMinWidth(0.0);
                setMaxWidth(Double.MAX_VALUE);
                setPrefWidth(Region.USE_COMPUTED_SIZE);
                return;
            }
            setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            setTranslateX(0.0);
            setTranslateY(0.0);
        }

        void beginDrag(MouseEvent event) {
            if (event == null || event.getButton() != MouseButton.PRIMARY) return;
            if (edgeBarMounted) {
                event.consume();
                return;
            }
            dragSceneX = event.getSceneX();
            dragSceneY = event.getSceneY();
            dragLayoutX = getLayoutX();
            dragLayoutY = getLayoutY();
            dragGroup = visibleFloatingSnapGroup(this);
            dragGroupOrigins.clear();
            for (FloatingDocker docker : dragGroup) {
                dragGroupOrigins.put(docker, new double[] { docker.getLayoutX(), docker.getLayoutY() });
                docker.toFront();
            }
            toFront();
            event.consume();
        }

        void drag(MouseEvent event) {
            if (event == null || !event.isPrimaryButtonDown()) return;
            if (edgeBarMounted) {
                event.consume();
                return;
            }
            if (dragGroupOrigins.isEmpty()) {
                dragGroup = List.of(this);
                dragGroupOrigins.put(this, new double[] { dragLayoutX, dragLayoutY });
            }
            double dx = event.getSceneX() - dragSceneX;
            double dy = event.getSceneY() - dragSceneY;
            for (Map.Entry<FloatingDocker, double[]> entry : dragGroupOrigins.entrySet()) {
                FloatingDocker docker = entry.getKey();
                double[] origin = entry.getValue();
                docker.setLayoutX(origin[0] + dx);
                docker.setLayoutY(origin[1] + dy);
            }
            FloatingSnapAdjustment snap = calculateFloatingSnapAdjustment(dragGroup);
            if (snap.active()) {
                for (FloatingDocker docker : dragGroupOrigins.keySet()) {
                    docker.setLayoutX(docker.getLayoutX() + snap.dx());
                    docker.setLayoutY(docker.getLayoutY() + snap.dy());
                }
            }
            event.consume();
        }

        void finishDrag(MouseEvent event) {
            if (edgeBarMounted) {
                if (event != null) event.consume();
                return;
            }
            if (isEdgeBarDrop(event)) {
                List<FloatingDocker> group = dragGroup == null || dragGroup.isEmpty() ? List.of(this) : List.copyOf(dragGroup);
                for (FloatingDocker docker : group) {
                    sendDockItemToEdgeBar(docker.item);
                }
            } else if (isMostlyOffScreen(event)) {
                List<FloatingDocker> group = dragGroup == null || dragGroup.isEmpty() ? List.of(this) : List.copyOf(dragGroup);
                for (FloatingDocker docker : group) {
                    stashFloatingDocker(docker.item);
                }
            } else {
                updateFloatingSnapLinksAfterDrag(dragGroup == null || dragGroup.isEmpty() ? List.of(this) : dragGroup);
            }
            dragGroup = List.of();
            dragGroupOrigins.clear();
            if (event != null) event.consume();
        }

        boolean isMostlyOffScreen(MouseEvent event) {
            Scene scene = getScene();
            if (scene == null) return false;
            if (event != null) {
                double mx = event.getSceneX();
                double my = event.getSceneY();
                if (mx < 16.0 || my < 16.0 || mx > scene.getWidth() - 16.0 || my > scene.getHeight() - 16.0) {
                    return true;
                }
            }
            double width = getBoundsInParent().getWidth();
            double height = getBoundsInParent().getHeight();
            double x = getLayoutX();
            double y = getLayoutY();
            return x < -width + 32.0
                || y < -height + 32.0
                || x > scene.getWidth() - 32.0
                || y > scene.getHeight() - 32.0;
        }

        void hideSmoothly() {
            setMouseTransparent(true);
            hideRestoreTabSmoothly();
            FadeTransition fade = new FadeTransition(Duration.millis(150), this);
            fade.setFromValue(getOpacity());
            fade.setToValue(0.0);
            fade.setOnFinished(event -> hideDockerOnly());
            fade.play();
        }

        void stashSmoothly() {
            setMouseTransparent(true);
            showRestoreTabSmoothly();
            FadeTransition fade = new FadeTransition(Duration.millis(150), this);
            fade.setFromValue(getOpacity());
            fade.setToValue(0.0);
            fade.setOnFinished(event -> hideDockerOnly());
            fade.play();
        }

        void showSmoothly() {
            hideRestoreTabSmoothly();
            clampIntoView();
            setManaged(true);
            setVisible(true);
            setMouseTransparent(false);
            toFront();
            FadeTransition fade = new FadeTransition(Duration.millis(160), this);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.play();
        }

        void hideImmediately() {
            hideRestoreTabImmediately();
            hideDockerOnly();
        }

        void stashImmediately() {
            showRestoreTabImmediately();
            hideDockerOnly();
        }

        private void hideDockerOnly() {
            setOpacity(0.0);
            setManaged(false);
            setVisible(false);
            setMouseTransparent(true);
        }

        void showImmediately() {
            hideRestoreTabImmediately();
            clampIntoView();
            setOpacity(1.0);
            setManaged(true);
            setVisible(true);
            setMouseTransparent(false);
        }

        void clampIntoView() {
            Scene scene = getScene();
            if (scene == null) {
                if (getLayoutX() < 0.0) setLayoutX(14.0);
                if (getLayoutY() < 0.0) setLayoutY(14.0);
                return;
            }
            double maxX = Math.max(14.0, scene.getWidth() - Math.max(120.0, getBoundsInParent().getWidth()) - 18.0);
            double maxY = Math.max(14.0, scene.getHeight() - Math.max(60.0, getBoundsInParent().getHeight()) - 34.0);
            setLayoutX(Math.max(14.0, Math.min(maxX, getLayoutX())));
            setLayoutY(Math.max(14.0, Math.min(maxY, getLayoutY())));
        }

        String formatPositionPref() {
            return String.format(Locale.ROOT, "%.1f,%.1f", getLayoutX(), getLayoutY());
        }

        void applyPositionPref(String raw) {
            if (raw == null || raw.isBlank()) return;
            String[] parts = raw.split(",", 2);
            if (parts.length != 2) return;
            try {
                double x = Double.parseDouble(parts[0].trim());
                double y = Double.parseDouble(parts[1].trim());
                if (Double.isFinite(x) && Double.isFinite(y)) {
                    setLayoutX(x);
                    setLayoutY(y);
                }
            } catch (NumberFormatException ignored) {
                // reason: hand-edited position preference; keep current placement
            }
        }

        private void showRestoreTabSmoothly() {
            positionRestoreTab();
            restoreTab.setManaged(true);
            restoreTab.setVisible(true);
            restoreTab.setMouseTransparent(false);
            restoreTab.toFront();
            FadeTransition fade = new FadeTransition(Duration.millis(150), restoreTab);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.play();
        }

        private void showRestoreTabImmediately() {
            positionRestoreTab();
            restoreTab.setOpacity(1.0);
            restoreTab.setManaged(true);
            restoreTab.setVisible(true);
            restoreTab.setMouseTransparent(false);
            restoreTab.toFront();
        }

        private void hideRestoreTabSmoothly() {
            restoreTab.setMouseTransparent(true);
            FadeTransition fade = new FadeTransition(Duration.millis(120), restoreTab);
            fade.setFromValue(restoreTab.getOpacity());
            fade.setToValue(0.0);
            fade.setOnFinished(event -> hideRestoreTabImmediately());
            fade.play();
        }

        private void hideRestoreTabImmediately() {
            restoreTab.setOpacity(0.0);
            restoreTab.setManaged(false);
            restoreTab.setVisible(false);
            restoreTab.setMouseTransparent(true);
        }

        private void refreshRestoreTabPosition() {
            if (restoreTab.isVisible()) {
                positionRestoreTab();
            }
        }

        private void positionRestoreTab() {
            stashEdge = determineStashEdge();
            updateRestoreTabText();
            Scene scene = getScene();
            double sceneWidth = scene == null ? 960.0 : Math.max(120.0, scene.getWidth());
            double sceneHeight = scene == null ? 640.0 : Math.max(120.0, scene.getHeight());
            double tabWidth = Math.max(96.0, Math.min(156.0, restoreTab.prefWidth(-1) + 10.0));
            double tabHeight = 24.0;
            restoreTab.setPrefSize(tabWidth, tabHeight);
            restoreTab.setMinSize(Math.min(86.0, tabWidth), tabHeight);
            restoreTab.setMaxSize(tabWidth, tabHeight);

            double x = clampDouble(getLayoutX(), 6.0, sceneWidth - tabWidth - 6.0);
            double y = clampDouble(getLayoutY(), 36.0, sceneHeight - tabHeight - 12.0);
            if (stashEdge == FloatingEdge.LEFT) {
                x = 3.0;
            } else if (stashEdge == FloatingEdge.RIGHT) {
                x = sceneWidth - tabWidth - 3.0;
            } else if (stashEdge == FloatingEdge.TOP) {
                y = 3.0;
            } else {
                y = sceneHeight - tabHeight - 3.0;
            }
            restoreTab.setLayoutX(x);
            restoreTab.setLayoutY(y);
        }

        private FloatingEdge determineStashEdge() {
            Scene scene = getScene();
            if (scene == null) return stashEdge;
            double width = Math.max(1.0, getBoundsInParent().getWidth());
            double height = Math.max(1.0, getBoundsInParent().getHeight());
            double x = getLayoutX();
            double y = getLayoutY();
            double sceneWidth = Math.max(1.0, scene.getWidth());
            double sceneHeight = Math.max(1.0, scene.getHeight());
            if (x + width < 32.0) return FloatingEdge.LEFT;
            if (x > sceneWidth - 32.0) return FloatingEdge.RIGHT;
            if (y + height < 32.0) return FloatingEdge.TOP;
            if (y > sceneHeight - 32.0) return FloatingEdge.BOTTOM;
            double left = Math.max(0.0, x);
            double right = Math.max(0.0, sceneWidth - (x + width));
            double top = Math.max(0.0, y);
            double bottom = Math.max(0.0, sceneHeight - (y + height));
            double nearest = Math.min(Math.min(left, right), Math.min(top, bottom));
            if (nearest == left) return FloatingEdge.LEFT;
            if (nearest == right) return FloatingEdge.RIGHT;
            if (nearest == top) return FloatingEdge.TOP;
            return FloatingEdge.BOTTOM;
        }

        private void updateRestoreTabText() {
            String title = item.title();
            if (stashEdge == FloatingEdge.LEFT) {
                restoreTab.setText(title + " >");
            } else if (stashEdge == FloatingEdge.RIGHT) {
                restoreTab.setText("< " + title);
            } else if (stashEdge == FloatingEdge.TOP) {
                restoreTab.setText("v " + title);
            } else {
                restoreTab.setText("^ " + title);
            }
        }
    }

    private final class DockItem {
        private final String id;
        private final String title;
        private final Node contentNode;
        private final boolean homeToolbar;
        private final String defaultSlotId;
        private CollapsibleToolbarCluster toolbarCluster;

        DockItem(String id, String title, Node contentNode, boolean homeToolbar) {
            this.id = id == null || id.isBlank() ? "dock-item" : id.trim();
            this.title = title == null || title.isBlank() ? this.id : title.trim();
            this.contentNode = contentNode;
            this.homeToolbar = homeToolbar;
            this.defaultSlotId = this.id;
            if (contentNode instanceof CollapsibleToolbarCluster cluster) {
                this.toolbarCluster = cluster;
            }
        }

        String id() { return id; }
        String title() { return title; }
        Node contentNode() { return contentNode; }
        boolean homeToolbar() { return homeToolbar; }
        String defaultSlotId() { return defaultSlotId; }
        CollapsibleToolbarCluster toolbarCluster() { return toolbarCluster; }
        void setToolbarCluster(CollapsibleToolbarCluster cluster) { toolbarCluster = cluster; }
        void clearToolbarCluster() {
            if (!(contentNode instanceof CollapsibleToolbarCluster)) {
                toolbarCluster = null;
            }
        }

        CollapsibleToolbarCluster asToolbarCluster() {
            if (toolbarCluster != null) {
                detachNode(toolbarCluster);
                installToolbarDockHandlers(toolbarCluster);
                return toolbarCluster;
            }
            detachNode(contentNode);
            CollapsibleToolbarCluster cluster = new CollapsibleToolbarCluster("dockitem-" + id, title, contentNode);
            cluster.setExpanded(false);
            toolbarCluster = cluster;
            installToolbarDockHandlers(cluster);
            return cluster;
        }
    }

    private final class DockSlot extends BorderPane {
        private final String slotId;
        private final Label titleLabel = new Label();
        private final StackPane body = new StackPane();
        private final HBox tabBar = new HBox(3);
        private final Button closeButton;
        private final List<DockItem> items = new ArrayList<>();
        private DockItem item;
        private SplitPane homeGroup;
        private int homeIndex;
        private boolean dynamic;

        DockSlot(String slotId, DockItem item) {
            this.slotId = slotId;
            getStyleClass().add("puppeteer-dock-slot");
            setMinSize(0, 0);
            setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            body.getStyleClass().add("puppeteer-dock-slot-body");
            body.setMinSize(0, 0);
            tabBar.getStyleClass().add("puppeteer-dock-slot-tab-bar");
            tabBar.setAlignment(Pos.CENTER_LEFT);
            tabBar.setManaged(false);
            tabBar.setVisible(false);

            Label grip = new Label("::");
            grip.getStyleClass().add("puppeteer-dock-slot-grip");
            titleLabel.getStyleClass().add("puppeteer-dock-slot-title");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            closeButton = makeToolbarIconButton(CssIcon.clearX("#b9a489"), "Hide this dock");
            closeButton.getStyleClass().add("puppeteer-dock-slot-close");
            closeButton.setMinSize(22, 22);
            closeButton.setPrefSize(22, 22);
            closeButton.setMaxSize(22, 22);
            closeButton.setOnAction(event -> {
                if (item != null) hideDockItem(item);
                event.consume();
            });
            HBox header = new HBox(7, grip, titleLabel, spacer, closeButton);
            header.getStyleClass().add("puppeteer-dock-slot-header");
            header.setAlignment(Pos.CENTER_LEFT);
            header.setMinHeight(26);
            header.setPrefHeight(26);
            Tooltip.install(header, new Tooltip("Drag the active docker. Drop onto another container to group."));
            installDockSlotDragHandlers(header, true);
            header.setOnContextMenuRequested(event -> {
                ContextMenu menu = createDockSlotContextMenu();
                menu.show(header, event.getScreenX(), event.getScreenY());
                event.consume();
            });

            setTop(new VBox(header, tabBar));
            setCenter(body);
            installDockSlotDragHandlers(this, false);
            setItem(item);
        }

        String slotId() {
            return slotId;
        }

        DockItem item() {
            return item;
        }

        List<DockItem> items() {
            return List.copyOf(items);
        }

        boolean hasItems() {
            return !items.isEmpty();
        }

        boolean containsItem(DockItem candidate) {
            return candidate != null && items.contains(candidate);
        }

        boolean containsNode(Node node) {
            return itemForNode(node) != null;
        }

        DockItem itemForNode(Node node) {
            if (node == null) return null;
            for (DockItem candidate : items) {
                if (candidate.contentNode() == node) return candidate;
            }
            return null;
        }

        void setItem(DockItem nextItem) {
            setItems(nextItem == null ? List.of() : List.of(nextItem));
        }

        void setItems(List<DockItem> nextItems) {
            body.getChildren().clear();
            for (DockItem previous : items) {
                Node previousNode = previous.contentNode();
                detachNode(previousNode);
                previousNode.setManaged(false);
                previousNode.setVisible(false);
            }
            items.clear();
            item = null;
            if (nextItems != null) {
                for (DockItem nextItem : nextItems) {
                    if (nextItem != null && nextItem.contentNode() != null && !items.contains(nextItem)) {
                        items.add(nextItem);
                    }
                }
            }
            if (items.isEmpty()) {
                updateEmptyChrome();
                detachDockSlot(this);
                return;
            }
            attachDockSlot(this);
            activateItem(items.get(0));
        }

        void addItem(DockItem nextItem) {
            if (nextItem == null || nextItem.contentNode() == null) return;
            DockSlot existingSlot = findDockSlotContaining(nextItem.contentNode());
            if (existingSlot != null && existingSlot != this) {
                existingSlot.removeItem(nextItem);
            }
            if (!items.contains(nextItem)) {
                items.add(nextItem);
            }
            attachDockSlot(this);
            activateItem(nextItem);
        }

        void removeItem(DockItem removedItem) {
            if (removedItem == null || !items.contains(removedItem)) return;
            int index = items.indexOf(removedItem);
            items.remove(removedItem);
            detachNode(removedItem.contentNode());
            removedItem.contentNode().setManaged(false);
            removedItem.contentNode().setVisible(false);
            if (removedItem.contentNode() instanceof CollapsibleToolbarCluster cluster) {
                cluster.setDockedChromeVisible(true);
            }
            if (items.isEmpty()) {
                item = null;
                body.getChildren().clear();
                updateEmptyChrome();
                detachDockSlot(this);
                return;
            }
            activateItem(items.get(Math.max(0, Math.min(index, items.size() - 1))));
        }

        void releaseItemForPreviewFocus(DockItem releasedItem) {
            if (releasedItem == null || !items.contains(releasedItem)) return;
            Node node = releasedItem.contentNode();
            detachNode(node);
            node.setManaged(false);
            node.setVisible(false);
            if (node instanceof CollapsibleToolbarCluster cluster) {
                cluster.setDockedChromeVisible(true);
            }
        }

        void activateItem(DockItem nextItem) {
            if (nextItem == null || !items.contains(nextItem)) return;
            item = nextItem;
            attachDockSlot(this);
            titleLabel.setText(nextItem.title());
            closeButton.setVisible(true);
            closeButton.setManaged(true);
            body.getChildren().clear();
            for (DockItem dockItem : items) {
                Node node = dockItem.contentNode();
                detachNode(node);
                node.setManaged(dockItem == nextItem);
                node.setVisible(dockItem == nextItem);
                if (node instanceof CollapsibleToolbarCluster cluster) {
                    cluster.setDockedChromeVisible(false);
                }
            }
            body.getChildren().setAll(nextItem.contentNode());
            refreshTabBar();
        }

        void clearItem() {
            List<DockItem> previousItems = new ArrayList<>(items);
            item = null;
            items.clear();
            body.getChildren().clear();
            for (DockItem previous : previousItems) {
                detachNode(previous.contentNode());
                previous.contentNode().setManaged(false);
                previous.contentNode().setVisible(false);
                if (previous.contentNode() instanceof CollapsibleToolbarCluster cluster) {
                    cluster.setDockedChromeVisible(true);
                }
            }
            updateEmptyChrome();
            detachDockSlot(this);
        }

        private void updateEmptyChrome() {
            titleLabel.setText("Empty");
            closeButton.setVisible(false);
            closeButton.setManaged(false);
            tabBar.getChildren().clear();
            tabBar.setManaged(false);
            tabBar.setVisible(false);
        }

        private void refreshTabBar() {
            tabBar.getChildren().clear();
            boolean showTabs = items.size() > 1;
            tabBar.setManaged(showTabs);
            tabBar.setVisible(showTabs);
            if (!showTabs) return;
            for (DockItem dockItem : items) {
                ToggleButton tab = new ToggleButton(dockItem.title());
                tab.getStyleClass().add("puppeteer-dock-slot-tab");
                tab.setSelected(dockItem == item);
                tab.setFocusTraversable(false);
                tab.setMinHeight(22);
                tab.setPrefHeight(22);
                tab.setMaxHeight(22);
                tab.setOnAction(event -> {
                    activateItem(dockItem);
                    refreshAfterDockSwap();
                    event.consume();
                });
                tab.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    if (event.getButton() == MouseButton.PRIMARY) {
                        activateItem(dockItem);
                    }
                });
                installDockSlotDragHandlers(tab, true);
                tabBar.getChildren().add(tab);
            }
        }

        private ContextMenu createDockSlotContextMenu() {
            ContextMenu menu = new ContextMenu();
            DockItem current = item;
            if (current != null) {
                MenuItem miHide = new MenuItem("Kill / Hide " + current.title());
                miHide.setOnAction(ev -> hideDockItem(current));
                MenuItem miNew = new MenuItem((items.size() > 1 ? "Split " : "Move ") + current.title() + " to New Container");
                miNew.setOnAction(ev -> moveDockItemToNewSlot(current, firstAvailableDockGroup("controls-primary", "workspace-top")));
                menu.getItems().addAll(miHide, miNew, createMoveDockItemMenu(current), new SeparatorMenuItem());
            }
            MenuItem miRestoreAll = new MenuItem("Restore All Dockers");
            miRestoreAll.setOnAction(ev -> {
                for (DockItem dockItem : dockItems.values()) {
                    showDockItem(dockItem);
                }
            });
            MenuItem miReset = new MenuItem("Reset Dock Arrangement");
            miReset.setOnAction(ev -> resetDockArrangement());
            menu.getItems().addAll(miRestoreAll, miReset);
            return menu;
        }

        private void installDockSlotDragHandlers(Node node, boolean canStartDockerDrag) {
            if (canStartDockerDrag) {
                node.setOnDragDetected(event -> {
                    if (event.getButton() != MouseButton.PRIMARY || item == null) return;
                    Dragboard dragboard = startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(DOCK_DRAG_SLOT_PREFIX + slotId);
                    dragboard.setContent(content);
                    setFloatingLayerDropCaptureEnabled(item.homeToolbar());
                    event.consume();
                });
            } else {
                node.setOnDragDetected(null);
            }
            node.setOnDragDone(event -> {
                getStyleClass().remove("drop-target");
                setFloatingLayerDropCaptureEnabled(false);
            });
            node.setOnDragOver(event -> {
                String payload = dockPayload(event.getDragboard());
                if (payload == null) return;
                if (payload.equals(DOCK_DRAG_SLOT_PREFIX + slotId)) return;
                if (isFloatingToolbarPayload(payload)) return;
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            });
            node.setOnDragEntered(event -> {
                String payload = dockPayload(event.getDragboard());
                if (payload != null && !payload.equals(DOCK_DRAG_SLOT_PREFIX + slotId)
                    && !isFloatingToolbarPayload(payload)
                    && !getStyleClass().contains("drop-target")) {
                    getStyleClass().add("drop-target");
                }
            });
            node.setOnDragExited(event -> getStyleClass().remove("drop-target"));
            node.setOnDragDropped(event -> {
                getStyleClass().remove("drop-target");
                String payload = dockPayload(event.getDragboard());
                boolean success = false;
                if (payload != null) {
                    if (payload.startsWith(DOCK_DRAG_SLOT_PREFIX)) {
                        success = swapDockSlots(payload.substring(DOCK_DRAG_SLOT_PREFIX.length()), slotId);
                    } else if (payload.startsWith(DOCK_DRAG_TOOLBAR_PREFIX)) {
                        success = !isFloatingToolbarPayload(payload)
                            && swapToolbarIntoDockSlot(payload.substring(DOCK_DRAG_TOOLBAR_PREFIX.length()), slotId);
                    }
                }
                event.setDropCompleted(success);
                setFloatingLayerDropCaptureEnabled(false);
                event.consume();
            });
        }
    }

    public void setUiScale(double scale) {
        uiScale = clampUiScale(scale);
        applyUiScale();
        if (workspacePrefs != null) {
            workspacePrefs.setDouble(PuppeteerWorkspacePrefs.KEY_UI_SCALE, uiScale);
            workspacePrefs.save();
        }
        refreshToolbarCommandSummary();
    }

    public double getUiScale() {
        return uiScale;
    }

    private void applyUiScale() {
        Scene currentScene = getScene();
        if (currentScene == null || currentScene.getRoot() == null) return;
        double fontSize = BASE_UI_FONT_SIZE * uiScale;
        currentScene.getRoot().setStyle(String.format(Locale.ROOT, "-fx-font-size: %.2fpx;", fontSize));
    }

    static double clampUiScale(double scale) {
        if (!Double.isFinite(scale)) return 1.0;
        return Math.max(MIN_UI_SCALE, Math.min(MAX_UI_SCALE, scale));
    }

    static String formatUiScaleLabel(double scale) {
        return String.format(Locale.ROOT, "%.0f%%", clampUiScale(scale) * 100.0);
    }

    public void resetWorkspaceLayout() {
        topToolbarVisible = true;
        toolbarDividerPosition = DEFAULT_TOOLBAR_DIVIDER_POSITION;
        topWorkspaceDividerPosition = DEFAULT_TOP_WORKSPACE_DIVIDER_POSITION;
        bottomWorkspaceDividerPosition = DEFAULT_BOTTOM_WORKSPACE_DIVIDER_POSITION;
        workspaceContentDividerPosition = DEFAULT_CONTENT_DIVIDER_POSITION;
        codePaneDividerPosition = DEFAULT_CODE_PANE_DIVIDER_POSITION;
        previewFocusDividerPosition = DEFAULT_PREVIEW_FOCUS_DIVIDER_POSITION;

        setTopToolbarVisible(true);
        setCodePaneVisible(true);
        if (topWorkspaceSplit != null && !topWorkspaceSplit.getDividers().isEmpty()) {
            topWorkspaceSplit.setDividerPositions(DEFAULT_TOP_WORKSPACE_DIVIDER_POSITION);
        }
        if (bottomWorkspaceSplit != null && !bottomWorkspaceSplit.getDividers().isEmpty()) {
            bottomWorkspaceSplit.setDividerPositions(DEFAULT_BOTTOM_WORKSPACE_DIVIDER_POSITION);
        }
        if (workspaceContentSplit != null && !workspaceContentSplit.getDividers().isEmpty()) {
            workspaceContentSplit.setDividerPositions(DEFAULT_CONTENT_DIVIDER_POSITION);
        }
        if (mainWorkspaceSplit != null && !mainWorkspaceSplit.getDividers().isEmpty()) {
            mainWorkspaceSplit.setDividerPositions(DEFAULT_CODE_PANE_DIVIDER_POSITION);
        }
        if (previewFocusSplit != null && !previewFocusSplit.getDividers().isEmpty()) {
            previewFocusSplit.setDividerPositions(DEFAULT_PREVIEW_FOCUS_DIVIDER_POSITION);
        }
        applyToolbarDivider();
        persistWorkspacePrefsNow();
        refreshSidebarTabs();
        refreshToolbarCommandSummary();
        updateStatusBar();
    }

    private void applyToolbarDensity(AnimatedToolbarPane.LayoutMode mode) {
        boolean compact = mode == AnimatedToolbarPane.LayoutMode.COMPACT;
        if (toolbarPane != null) {
            applyToolbarDensity(toolbarPane, compact);
            toolbarPane.requestLayout();
        }
        for (FloatingDocker docker : floatingToolbarDockers.values()) {
            applyToolbarDensity(docker, compact);
            docker.requestLayout();
        }
    }

    private void applyToolbarChromeDensity(AnimatedToolbarPane.LayoutMode mode) {
        boolean compact = mode == AnimatedToolbarPane.LayoutMode.COMPACT;
        if (toolbarCommandBar != null) {
            toolbarCommandBar.setPadding(compact ? TOOLBAR_COMMAND_BAR_PADDING_COMPACT : TOOLBAR_COMMAND_BAR_PADDING_DYNAMIC);
            toolbarCommandBar.setSpacing(compact ? 4.0 : 10.0);
        }
    }

    private void applyToolbarDensity(Node node, boolean compact) {
        if (node == null) return;

        if (node instanceof HBox hBox) {
            double baseSpacing = rememberToolbarMetric(hBox, PROP_TOOLBAR_BASE_SPACING, hBox.getSpacing());
            hBox.setSpacing(compact ? Math.max(0.0, baseSpacing - 4.0) : baseSpacing);
        } else if (node instanceof VBox vBox) {
            double baseSpacing = rememberToolbarMetric(vBox, PROP_TOOLBAR_BASE_SPACING, vBox.getSpacing());
            vBox.setSpacing(compact ? Math.max(0.0, baseSpacing - 5.0) : baseSpacing);
        }

        if (node instanceof ButtonBase button && isToolbarIconControl(button)) {
            applyToolbarButtonDensity(button, compact);
        } else if (node instanceof Label label && label.getStyleClass().contains("puppeteer-toolbar-icon")) {
            applyToolbarIconDensity(label, compact);
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyToolbarDensity(child, compact);
            }
        }
    }

    private void applyToolbarButtonDensity(ButtonBase button, boolean compact) {
        double baseWidth = rememberToolbarMetric(button, PROP_TOOLBAR_BASE_PREF_WIDTH, sanitizeToolbarSize(button.getPrefWidth(), button.getMinWidth()));
        double baseHeight = rememberToolbarMetric(button, PROP_TOOLBAR_BASE_PREF_HEIGHT, sanitizeToolbarSize(button.getPrefHeight(), button.getMinHeight()));
        double targetWidth = compact ? Math.max(24.0, baseWidth - 6.0) : baseWidth;
        double targetHeight = compact ? Math.max(17.0, baseHeight - 8.0) : baseHeight;
        button.setMinSize(targetWidth, targetHeight);
        button.setPrefSize(targetWidth, targetHeight);
        button.setMaxSize(targetWidth, targetHeight);
    }

    private void applyToolbarIconDensity(Label icon, boolean compact) {
        double baseWidth = rememberToolbarMetric(icon, PROP_TOOLBAR_BASE_ICON_WIDTH, sanitizeToolbarSize(icon.getPrefWidth(), 16.0));
        double baseHeight = rememberToolbarMetric(icon, PROP_TOOLBAR_BASE_ICON_HEIGHT, sanitizeToolbarSize(icon.getPrefHeight(), 16.0));
        double targetWidth = compact ? Math.max(11.0, baseWidth - 3.0) : baseWidth;
        double targetHeight = compact ? Math.max(11.0, baseHeight - 3.0) : baseHeight;
        icon.setMinSize(targetWidth, targetHeight);
        icon.setPrefSize(targetWidth, targetHeight);
        icon.setMaxSize(targetWidth, targetHeight);
    }

    private boolean isToolbarIconControl(ButtonBase button) {
        if (button == null) return false;
        List<String> styles = button.getStyleClass();
        return styles.contains("puppeteer-toolbar-icon-button")
            || styles.contains("puppeteer-toolbar-icon-toggle")
            || styles.contains("puppeteer-toolbar-icon-menu");
    }

    private double rememberToolbarMetric(Node node, String key, double fallback) {
        if (node == null || key == null) return fallback;
        Object existing = node.getProperties().get(key);
        if (existing instanceof Number number) {
            return number.doubleValue();
        }
        double value = fallback;
        node.getProperties().put(key, value);
        return value;
    }

    private double sanitizeToolbarSize(double value, double fallback) {
        if (Double.isFinite(value) && value > 0.0 && value != Region.USE_COMPUTED_SIZE && value != Region.USE_PREF_SIZE) {
            return value;
        }
        if (Double.isFinite(fallback) && fallback > 0.0 && fallback != Region.USE_COMPUTED_SIZE && fallback != Region.USE_PREF_SIZE) {
            return fallback;
        }
        return 16.0;
    }

    public void setCodePaneVisible(boolean visible) {
        codePaneVisible = visible;
        DockItem codeItem = dockItems.get("code");
        if (codeItem != null) {
            if (visible) {
                showDockItem(codeItem);
            } else {
                hideDockItem(codeItem);
            }
            syncCodePaneVisibilityState();
            return;
        }
        if (codePreview != null) {
            codePreview.setManaged(visible);
            codePreview.setVisible(visible);
        }
        refreshSidebarTabs();
        refreshToolbarCommandSummary();
        updateStatusBar();
    }

    public boolean isCodePaneVisible() {
        DockItem codeItem = dockItems.get("code");
        return codeItem == null ? codePaneVisible : isDockItemVisible(codeItem);
    }

    public void setTopToolbarVisible(boolean visible) {
        if (!visible) {
            double current = readDividerPosition(rootWorkspaceSplit, toolbarDividerPosition);
            if (current > TOOLBAR_MIN_VISIBLE_DIVIDER_POSITION) {
                toolbarDividerPosition = clampToolbarDivider(current, true);
            }
        }
        topToolbarVisible = visible;
        if (toolbarCommandBar != null) {
            toolbarCommandBar.setManaged(visible);
            toolbarCommandBar.setVisible(visible);
        }
        updateTopToolbarChrome();
        applyToolbarDivider();
    }

    public boolean isTopToolbarVisible() {
        return topToolbarVisible;
    }

    private void beginToolbarDrag(MouseEvent event) {
        if (event == null || event.getButton() != MouseButton.PRIMARY) return;
        toolbarDragStartSceneY = event.getSceneY();
        toolbarDragStartDivider = readDividerPosition(rootWorkspaceSplit,
            topToolbarVisible ? toolbarDividerPosition : TOOLBAR_COLLAPSED_DIVIDER_POSITION);
    }

    private void dragToolbar(MouseEvent event) {
        if (event == null || !event.isPrimaryButtonDown()) return;
        if (rootWorkspaceSplit == null || rootWorkspaceSplit.getDividers().isEmpty()) return;
        double height = rootWorkspaceSplit.getHeight();
        if (!Double.isFinite(height) || height <= 1.0) return;
        double next = toolbarDragStartDivider + ((event.getSceneY() - toolbarDragStartSceneY) / height);
        if (!topToolbarVisible && next > TOOLBAR_MIN_VISIBLE_DIVIDER_POSITION) {
            setTopToolbarVisible(true);
        }
        setToolbarDividerPosition(next, true);
        event.consume();
    }

    private void applyToolbarDivider() {
        if (rootWorkspaceSplit == null || rootWorkspaceSplit.getDividers().isEmpty()) return;
        double target = topToolbarVisible
            ? clampToolbarDivider(toolbarDividerPosition, true)
            : TOOLBAR_COLLAPSED_DIVIDER_POSITION;
        rootWorkspaceSplit.setDividerPositions(target);
    }

    private void setToolbarDividerPosition(double dividerPosition, boolean remember) {
        if (rootWorkspaceSplit == null || rootWorkspaceSplit.getDividers().isEmpty()) return;
        double target = clampToolbarDivider(dividerPosition, topToolbarVisible);
        rootWorkspaceSplit.setDividerPositions(target);
        if (topToolbarVisible && remember) {
            toolbarDividerPosition = target;
        }
    }

    private void updateTopToolbarChrome() {
        // The old resizable Puppeteer toolbar shell has been replaced by dockers.
    }

    private static double clampToolbarDivider(double value, boolean visible) {
        double min = visible ? TOOLBAR_MIN_VISIBLE_DIVIDER_POSITION : TOOLBAR_COLLAPSED_DIVIDER_POSITION;
        if (!Double.isFinite(value)) return visible ? 0.16 : TOOLBAR_COLLAPSED_DIVIDER_POSITION;
        return Math.max(min, Math.min(TOOLBAR_MAX_DIVIDER_POSITION, value));
    }

    private static void installLayoutClip(Region region) {
        if (region == null) return;
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        region.setClip(clip);
    }

    public void setToolbarClusterExpanded(String key, boolean expanded) {
        if (key == null || key.isBlank()) return;
        CollapsibleToolbarCluster cluster = toolbarClusters.get(key.trim().toLowerCase(Locale.ROOT));
        if (cluster != null) {
            cluster.setExpanded(expanded);
        }
    }

    public void setToolbarClusterPinned(String key, boolean pinned) {
        if (key == null || key.isBlank()) return;
        CollapsibleToolbarCluster cluster = toolbarClusters.get(key.trim().toLowerCase(Locale.ROOT));
        if (cluster != null) {
            cluster.setPinned(pinned);
        }
    }

    public void setScene(JesScene2D scene) {
        this.scene = scene;
        animationPreview.setScene(scene);
        entitySelector.setScene(scene);
        if (scene != null) {
            for (String name : scene.names()) {
                EntityTrack track = project.getOrCreateTrack(name);
                var entity = scene.find(name);
                if (entity != null) {
                    track.setLayerOrder((int) Math.round(entity.getZ()));
                }
            }
            PuppeteerRigStore.load(projectRoot, project);
            applyLaunchScenePresetGrouping();
            captureSceneStateBaseline();
            captureProjectSnapshotBaseline();
            entitySelector.refresh(project);
            timelinePanel.refresh();
            animationPreview.setOrbitAnchors(project.getOrbitAnchorsView());
            animationPreview.setOrbitAnchorSources(project.getOrbitAnchorSourcesView());
            animationPreview.setOrbitAnchorSourceOffsets(project.getOrbitAnchorSourceOffsetsView());
            updatePreview();
            refreshExportPreview();
        }
        updatePreviewOverlayVisibility();
        refreshSidebarTabs();
        updateStatusBar();
    }

    public void setLaunchSceneSnapshot(PuppeteerLauncherPanel.SceneSnapshot snapshot) {
        this.launchSceneSnapshot = snapshot;
        launchCharacterImagePaths.clear();
        launchCharacterPresetLayers.clear();
        launchBackgroundPaths.clear();
        if (snapshot != null) {
            if (snapshot.characterImagePaths != null) {
                launchCharacterImagePaths.putAll(snapshot.characterImagePaths);
            }
            if (snapshot.characterPresetLayers != null) {
                launchCharacterPresetLayers.putAll(snapshot.characterPresetLayers);
            }
            if (snapshot.backgroundPaths != null) {
                launchBackgroundPaths.putAll(snapshot.backgroundPaths);
            }
            applyLaunchStageContext(snapshot);
            applyLaunchScenePresetGrouping();
        }
    }

    public void setRuntimeExportBaselines(Map<String, Map<PropertyType, Double>> baselines) {
        runtimeExportBaselines.clear();
        if (baselines == null || baselines.isEmpty()) return;
        for (Map.Entry<String, Map<PropertyType, Double>> entry : baselines.entrySet()) {
            String entityName = entry.getKey();
            Map<PropertyType, Double> props = entry.getValue();
            if (entityName == null || entityName.isBlank() || props == null || props.isEmpty()) continue;
            Map<PropertyType, Double> copy = new EnumMap<>(PropertyType.class);
            for (Map.Entry<PropertyType, Double> prop : props.entrySet()) {
                if (prop.getKey() == null || prop.getValue() == null || !Double.isFinite(prop.getValue())) continue;
                copy.put(prop.getKey(), prop.getValue());
            }
            if (!copy.isEmpty()) {
                runtimeExportBaselines.put(entityName.trim(), copy);
            }
        }
    }

    public void setOnSyncSnapshotRequested(Runnable r) {
        this.onSyncSnapshotRequested = r;
    }

    private void requestSyncSnapshot() {
        if (onSyncSnapshotRequested != null) {
            onSyncSnapshotRequested.run();
        }
    }

    public void syncWithSnapshot(PuppeteerLauncherPanel.SceneSnapshot snapshot, JesScene2D newScene) {
        if (snapshot == null || newScene == null) return;
        setLaunchSceneSnapshot(snapshot);
        setScene(newScene);
    }

    private void applyLaunchScenePresetGrouping() {
        if (launchSceneSnapshot == null || scene == null || project == null) return;
        boolean changed = false;
        
        Map<String, String> orbitSources = new java.util.LinkedHashMap<>(project.getOrbitAnchorSourcesView());
        Map<String, double[]> orbitOffsets = new java.util.LinkedHashMap<>(project.getOrbitAnchorSourceOffsetsView());
        
        for (PuppeteerLauncherPanel.CharacterEntry character : launchSceneSnapshot.characters) {
            if (character == null) continue;
            List<PuppeteerLauncherPanel.CharacterLayerEntry> layers =
                launchSceneSnapshot.resolveCharacterLayers(character.characterId, character.expression);
            if (layers == null || layers.isEmpty()) continue;
            String groupName = snapshotCharacterGroupName(character);
            EntityGroup group = project.getOrCreateGroup(groupName);
            int groupLayer = Integer.MAX_VALUE;
            String baseEntityName = null;
            for (PuppeteerLauncherPanel.CharacterLayerEntry layer : layers) {
                if (layer == null) continue;
                List<String> entityNames = findSnapshotLayerEntityNames(character, layer.layerId);
                if (entityNames.isEmpty()) continue;
                String primaryEntityName = entityNames.get(0);

                boolean baseLayer = baseEntityName == null;
                if (baseEntityName == null) {
                    baseEntityName = primaryEntityName;
                }

                for (String entityName : entityNames) {
                    if (entityName == null || entityName.isBlank()) continue;
                    if (!baseLayer && !entityName.equals(baseEntityName)) {
                        orbitSources.put(entityName, baseEntityName);
                        com.jvn.core.scene2d.Entity2D baseEntity = scene.find(baseEntityName);
                        com.jvn.core.scene2d.Entity2D childEntity = scene.find(entityName);
                        if (baseEntity != null && childEntity != null) {
                            orbitOffsets.put(entityName, new double[]{childEntity.getX() - baseEntity.getX(), childEntity.getY() - baseEntity.getY()});
                        }
                    }

                    EntityTrack track = project.getTrack(entityName);
                    if (track == null) continue;
                    track.setLayerOrder(0);
                    project.addEntityToGroup(entityName, groupName);
                    groupLayer = character.layerOrder == null ? 0 : character.layerOrder;
                }
                changed = true;
            }
            if (groupLayer != Integer.MAX_VALUE) {
                group.setLayerOrder(groupLayer);
            }

            for (PuppeteerLauncherPanel.CharacterLayerGroupEntry rigGroup :
                    launchSceneSnapshot.resolveCharacterLayerGroups(character.characterId, character.expression)) {
                if (rigGroup == null || rigGroup.groupId == null || rigGroup.groupId.isBlank()) continue;
                String rigGroupName = snapshotRuntimeLayerGroupName(character, rigGroup.groupId);
                if (rigGroupName.isBlank()) continue;
                EntityGroup rigEntityGroup = project.getOrCreateGroup(rigGroupName);
                int rigGroupLayer = Integer.MAX_VALUE;
                for (String layerId : rigGroup.layerIds) {
                    if (layerId == null || layerId.isBlank()) continue;
                    var chain = PuppeteerLauncherPanel.snapshotLayerGroupChain(layerId,
                        launchSceneSnapshot.resolveCharacterLayerGroups(character.characterId, character.expression));
                    if (chain.isEmpty() || chain.getLast() != rigGroup) continue;
                    for (String entityName : findSnapshotLayerEntityNames(character, layerId)) {
                        EntityTrack track = project.getTrack(entityName);
                        if (track == null) continue;
                        project.addEntityToGroup(entityName, rigGroupName);
                        rigGroupLayer = Math.min(rigGroupLayer, track.getLayerOrder());
                    }
                }
                if (rigGroupLayer != Integer.MAX_VALUE) {
                    rigEntityGroup.setLayerOrder(rigGroupLayer);
                }
                if (rigGroup.hasPivot) {
                    EntityTrack groupTrack = rigEntityGroup.getGroupTrack();
                    groupTrack.upsertKeyframe(PropertyType.PIVOT_X, new Keyframe(0.0, rigGroup.pivotX));
                    groupTrack.upsertKeyframe(PropertyType.PIVOT_Y, new Keyframe(0.0, rigGroup.pivotY));
                }

                String parentGroupName = groupName;
                if (rigGroup.parentGroupId != null && !rigGroup.parentGroupId.isBlank()) {
                    String declaredParentName = snapshotRuntimeLayerGroupName(character, rigGroup.parentGroupId);
                    if (!declaredParentName.isBlank()) parentGroupName = declaredParentName;
                }
                project.getOrCreateGroup(parentGroupName);
                project.addGroupToGroup(rigGroupName, parentGroupName);
                changed = true;
            }
        }
        for (Map.Entry<String, String> entry : launchSceneSnapshot.dynamicGroups.entrySet()) {
            String child = snapshotHierarchyTarget(entry.getKey());
            String parent = snapshotHierarchyTarget(entry.getValue());
            project.getOrCreateGroup(parent);
            if (project.getTrack(child) != null) {
                project.addEntityToGroup(child, parent);
            } else {
                project.getOrCreateGroup(child);
                project.addGroupToGroup(child, parent);
            }
            changed = true;
        }
        if (changed) {
            project.setOrbitAnchorSources(orbitSources);
            project.setOrbitAnchorSourceOffsets(orbitOffsets);
            entitySelector.refresh(project);
            timelinePanel.refresh();
            refreshPropertyPickerChoices();
            refreshExportPreview();
        }
    }

    private String snapshotHierarchyTarget(String target) {
        for (var character : launchSceneSnapshot.characters) {
            if ((target.equals(character.characterId) || target.equals(character.displaySlot))
                    && !launchSceneSnapshot.resolveCharacterLayers(character.characterId, character.expression).isEmpty()) {
                return snapshotCharacterGroupName(character);
            }
        }
        return target;
    }

    private List<String> findSnapshotLayerEntityNames(PuppeteerLauncherPanel.CharacterEntry character, String layerId) {
        if (character == null || layerId == null || layerId.isBlank()) return List.of();
        List<String> names = new java.util.ArrayList<>();
        List<String> candidates = new java.util.ArrayList<>(PuppeteerLauncherPanel.equivalentSnapshotLayerEntityNames(launchSceneSnapshot, character, layerId));
        candidates.addAll(PuppeteerLauncherPanel.snapshotLayerOccurrenceNames(launchSceneSnapshot, character, layerId));
        for (String candidate : candidates) {
            String entityName = findSnapshotLayerEntityName(candidate);
            if (entityName != null && !entityName.isBlank() && !names.contains(entityName)) {
                names.add(entityName);
            }
        }
        return names;
    }

    private String snapshotRuntimeLayerGroupName(PuppeteerLauncherPanel.CharacterEntry character, String groupId) {
        List<String> names = PuppeteerLauncherPanel.equivalentSnapshotLayerGroupEntityNames(
            launchSceneSnapshot,
            character,
            groupId);
        return names.isEmpty() ? "" : names.get(0);
    }

    private String findSnapshotLayerEntityName(String groupName, String layerId) {
        String expected = groupName + "_" + selectorSafeName(layerId);
        return findSnapshotLayerEntityName(expected);
    }

    private String findSnapshotLayerEntityName(String expected) {
        if (expected == null || expected.isBlank()) return null;
        if (scene.find(expected) != null) return expected;
        for (String name : scene.names()) {
            if (name != null && name.startsWith(expected + "_")) return name;
        }
        return null;
    }

    private void applyLaunchStageContext(PuppeteerLauncherPanel.SceneSnapshot snapshot) {
        if (snapshot == null || snapshot.activeStagePresetId == null || snapshot.activeStagePresetId.isBlank()) return;
        String sourcePath = snapshot.resolveStagePresetPath(projectRoot);
        AnimationProject.StageContext context = buildStageContext(
            snapshot.activeStagePresetId,
            sourcePath,
            snapshot.backgroundId,
            snapshot.characters.isEmpty() ? "" : snapshot.characters.get(0).characterId);
        project.setStageContext(context);
        refreshExportPreview();
        refreshSidebarTabs();
    }

    private AnimationProject.StageContext buildStageContext(
        String presetId,
        String sourcePath,
        String fallbackBackgroundTag,
        String fallbackSubjectTag
    ) {
        if (presetId == null || presetId.isBlank()) return null;
        String metadataPath = relativizePreviewAssetPath(sourcePath);
        if (sourcePath != null && !sourcePath.isBlank()) {
            try {
                Path path = Path.of(sourcePath).toAbsolutePath().normalize();
                if (Files.isRegularFile(path)) {
                    try (var input = Files.newInputStream(path)) {
                        VnStagePreset preset = VnStagePresetLoader.load(presetId, sourcePath, input);
                        return new AnimationProject.StageContext(
                            presetId,
                            metadataPath,
                            firstNonBlank(preset.getBackgroundTag(), fallbackBackgroundTag),
                            firstNonBlank(preset.getSubjectTag(), fallbackSubjectTag),
                            preset.getLights().size(),
                            preset.getOccluders().size(),
                            preset.getResponseZones().size()
                        );
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new AnimationProject.StageContext(
            presetId,
            metadataPath,
            fallbackBackgroundTag,
            fallbackSubjectTag,
            0,
            0,
            0
        );
    }

    public java.io.File projectRoot;
    private java.io.File scriptTargetFile;
    private PuppeteerLauncherPanel.SceneSnapshot launchSceneSnapshot;
    private final Map<String, Map<PropertyType, Double>> runtimeExportBaselines = new LinkedHashMap<>();
    private PuppeteerWorkspacePrefs workspacePrefs;
    private PuppeteerDraftStore draftStore;
    private PuppeteerPreviewRecorder previewRecorder;
    private boolean draftRestorePromptShown;

    public void setProjectRoot(java.io.File root) {
        this.projectRoot = root;
        reloadProjectExpressionPresets();
        animationPreview.setProjectRoot(root);
        ProjectViewportSpec.Dimensions guideDimensions = animationPreview.getViewportDimensions();
        compositionGuideOverlay.setVirtualResolution(guideDimensions.width(), guideDimensions.height());
        if (assetImporterPanel != null) {
            assetImporterPanel.setProjectRoot(root);
        }
        keyframeEditor.setProjectRoot(root);
        codePreview.setProjectRoot(root);
        anchorEditor.setProjectRoot(root);
        updateViewportInfoLabel();
        refreshSidebarTabs();
        installWorkspaceServicesForProjectRoot();
        updateStatusBar();
    }

    private void reloadProjectExpressionPresets() {
        projectCharacterPresetLayers.clear();
        Map<String, Map<String, List<LayeredCharacterProjectCatalog.ExpressionLayer>>> catalog =
            LayeredCharacterProjectCatalog.loadExpressionPresets(projectRoot);
        for (Map.Entry<String, Map<String, List<LayeredCharacterProjectCatalog.ExpressionLayer>>> character
            : catalog.entrySet()) {
            String characterId = character.getKey();
            if (characterId == null || characterId.isBlank() || character.getValue() == null) continue;
            for (Map.Entry<String, List<LayeredCharacterProjectCatalog.ExpressionLayer>> expression
                : character.getValue().entrySet()) {
                if (expression.getKey() == null || expression.getKey().isBlank()) continue;
                List<ExpressionLayerSpec> layers = expression.getValue() == null
                    ? List.of()
                    : expression.getValue().stream()
                        .filter(layer -> layer != null && layer.path() != null && !layer.path().isBlank())
                        .map(layer -> new ExpressionLayerSpec(layer.layerId(), layer.path()))
                        .toList();
                if (!layers.isEmpty()) {
                    projectCharacterPresetLayers.put(
                        characterId.trim() + "/" + expression.getKey().trim(),
                        layers);
                }
            }
        }
    }

    private void installWorkspaceServicesForProjectRoot() {
        // Replace any previously bound services if the project root changed.
        if (draftStore != null) {
            draftStore.shutdown();
            draftStore = null;
        }
        previewRecorder = null;
        workspacePrefs = null;
        draftRestorePromptShown = false;
        if (projectRoot == null) return;

        workspacePrefs = PuppeteerWorkspacePrefs.load(projectRoot);
        applyWorkspacePrefs();
        PuppeteerAnchorStore.load(projectRoot, project);
        project.setEyeFocusProfiles(VnEyeFocusProfileStore.load(projectRoot));
        if (scene != null) {
            PuppeteerRigStore.load(projectRoot, project);
            applyLaunchScenePresetGrouping();
        }

        draftStore = new PuppeteerDraftStore(projectRoot);
        draftStore.setOnSaveCallback(timelineName -> Platform.runLater(() -> showAutoSaveIndicator(timelineName)));
        previewRecorder = new PuppeteerPreviewRecorder(animationPreview.getPreviewCanvas());

        // Run on the next tick so the timeline name field has the resolved value.
        Platform.runLater(this::promptDraftRestoreIfNeeded);
    }

    private void applyWorkspacePrefs() {
        if (workspacePrefs == null) return;
        workspacePrefs.getDivider(PuppeteerWorkspacePrefs.DIVIDER_TOOLBAR).ifPresent(v -> {
            toolbarDividerPosition = clampToolbarDivider(v, true);
            if (topToolbarVisible) {
                applyToolbarDivider();
            }
        });
        workspacePrefs.getBoolean(PuppeteerWorkspacePrefs.KEY_TOP_TOOLBAR_VISIBLE)
            .ifPresent(this::setTopToolbarVisible);
        workspacePrefs.getDivider(PuppeteerWorkspacePrefs.DIVIDER_TOP).ifPresent(v -> {
            if (topWorkspaceSplit != null && !topWorkspaceSplit.getDividers().isEmpty()) {
                topWorkspaceSplit.setDividerPositions(v);
                topWorkspaceDividerPosition = v;
            }
        });
        workspacePrefs.getDivider(PuppeteerWorkspacePrefs.DIVIDER_BOTTOM).ifPresent(v -> {
            if (bottomWorkspaceSplit != null && !bottomWorkspaceSplit.getDividers().isEmpty()) {
                bottomWorkspaceSplit.setDividerPositions(v);
                bottomWorkspaceDividerPosition = v;
            }
        });
        workspacePrefs.getDivider(PuppeteerWorkspacePrefs.DIVIDER_CONTENT).ifPresent(v -> {
            workspaceContentDividerPosition = v;
            if (workspaceContentSplit != null && !workspaceContentSplit.getDividers().isEmpty()) {
                workspaceContentSplit.setDividerPositions(v);
            }
        });
        workspacePrefs.getDivider(PuppeteerWorkspacePrefs.DIVIDER_CODE_PANE).ifPresent(v -> {
            codePaneDividerPosition = v;
            if (mainWorkspaceSplit != null && !mainWorkspaceSplit.getDividers().isEmpty()) {
                mainWorkspaceSplit.setDividerPositions(v);
            }
        });
        workspacePrefs.getDivider(PuppeteerWorkspacePrefs.DIVIDER_PREVIEW_FOCUS).ifPresent(v -> {
            previewFocusDividerPosition = v;
            if (previewFocusMode && previewFocusSplit != null && !previewFocusSplit.getDividers().isEmpty()) {
                previewFocusSplit.setDividerPositions(v);
            }
        });
        workspacePrefs.getDouble(PuppeteerWorkspacePrefs.KEY_UI_SCALE).ifPresent(v -> {
            uiScale = clampUiScale(v);
            applyUiScale();
        });
        workspacePrefs.getString(PuppeteerWorkspacePrefs.KEY_TOOLBAR_LAYOUT_MODE).ifPresent(raw -> {
            try {
                setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // reason: stale or hand-edited workspace preference; keep the current toolbar mode
            }
        });
        applyDockLayoutPrefs();
        applyFloatingDockerPrefs();

        if (animationPreview != null) {
            double panX = workspacePrefs.getDouble(PuppeteerWorkspacePrefs.KEY_VIEWPORT_PAN_X).orElse(0.0);
            double panY = workspacePrefs.getDouble(PuppeteerWorkspacePrefs.KEY_VIEWPORT_PAN_Y).orElse(0.0);
            double zoom = workspacePrefs.getDouble(PuppeteerWorkspacePrefs.KEY_VIEWPORT_ZOOM).orElse(1.0);
            animationPreview.setViewPanAndZoom(panX, panY, zoom);
        }
        if (project != null) {
            workspacePrefs.getDouble(PuppeteerWorkspacePrefs.KEY_TIMELINE_PLAYHEAD).ifPresent(ms -> {
                project.setPlayheadMs(ms);
            });
        }
    }

    private void applyDockLayoutPrefs() {
        if (workspacePrefs == null || dockSlots.isEmpty() || dockItems.isEmpty()) return;
        boolean hasDockPrefs = workspacePrefs.getString(PuppeteerWorkspacePrefs.KEY_DOCK_TOOLBAR_ORDER).isPresent()
            || workspacePrefs.getString(PuppeteerWorkspacePrefs.KEY_DOCK_HIDDEN_ITEMS).isPresent()
            || workspacePrefs.getString(PuppeteerWorkspacePrefs.KEY_DOCK_DYNAMIC_SLOTS).isPresent();
        if (!hasDockPrefs) {
            for (String slotId : dockSlots.keySet()) {
                if (workspacePrefs.getString(PuppeteerWorkspacePrefs.KEY_DOCK_SLOT_PREFIX + slotId).isPresent()) {
                    hasDockPrefs = true;
                    break;
                }
            }
        }
        if (!hasDockPrefs) return;

        applyingDockLayoutPrefs = true;
        try {
            restoreDynamicDockSlots(workspacePrefs.getString(PuppeteerWorkspacePrefs.KEY_DOCK_DYNAMIC_SLOTS).orElse(""));
            hiddenDockItemIds.clear();
            hiddenDockItemIds.addAll(parseDockIdList(
                workspacePrefs.getString(PuppeteerWorkspacePrefs.KEY_DOCK_HIDDEN_ITEMS).orElse("")));

            Set<String> placed = new LinkedHashSet<>();
            for (CollapsibleToolbarCluster cluster : toolbarPane.getClustersSnapshot()) {
                toolbarPane.removeCluster(cluster);
            }
            toolbarDockItems.clear();
            for (DockSlot slot : new ArrayList<>(dockSlots.values())) {
                String itemRecord = workspacePrefs
                    .getString(PuppeteerWorkspacePrefs.KEY_DOCK_SLOT_PREFIX + slot.slotId())
                    .orElse(EMPTY_DOCK_VALUE);
                List<DockItem> slotItems = new ArrayList<>();
                for (String itemId : parseDockSlotItemList(itemRecord)) {
                    if (hiddenDockItemIds.contains(itemId) || placed.contains(itemId)) continue;
                    DockItem item = dockItems.get(itemId);
                    if (item != null) {
                        slotItems.add(item);
                        placed.add(item.id());
                    }
                }
                if (slotItems.isEmpty()) {
                    slot.clearItem();
                    continue;
                }
                slot.setItems(slotItems);
            }

            List<String> toolbarOrder = parseDockIdList(
                workspacePrefs.getString(PuppeteerWorkspacePrefs.KEY_DOCK_TOOLBAR_ORDER).orElse(""));
            for (String itemId : toolbarOrder) {
                if (placed.contains(itemId) || hiddenDockItemIds.contains(itemId)) continue;
                DockItem item = dockItems.get(itemId);
                if (item == null) continue;
                showDockItem(item);
                placed.add(item.id());
            }

            for (DockItem item : dockItems.values()) {
                if (placed.contains(item.id()) || hiddenDockItemIds.contains(item.id())) continue;
                if (item.homeToolbar()) {
                    showDockItem(item);
                    placed.add(item.id());
                    continue;
                }
                DockSlot defaultSlot = dockSlots.get(item.defaultSlotId());
                if (defaultSlot != null) {
                    defaultSlot.addItem(item);
                    placed.add(item.id());
                } else {
                    DockSlot emptySlot = findFirstEmptyDockSlot();
                    if (emptySlot != null) {
                        emptySlot.setItem(item);
                        placed.add(item.id());
                    } else {
                        SplitPane group = item.homeToolbar()
                            ? firstAvailableDockGroup("controls-primary", "workspace-top")
                            : firstAvailableDockGroup("workspace-top", "workspace-main");
                        if (moveDockItemToNewSlot(item, group)) {
                            placed.add(item.id());
                        } else {
                            hiddenDockItemIds.add(item.id());
                        }
                    }
                }
            }
            applyFloatingDockerPrefs();
            refreshAfterDockSwap();
        } finally {
            applyingDockLayoutPrefs = false;
        }
    }

    private void captureDockLayoutPrefsInto(PuppeteerWorkspacePrefs prefs) {
        captureFloatingDockerPrefsInto(prefs);
        prefs.setString(PuppeteerWorkspacePrefs.KEY_DOCK_DYNAMIC_SLOTS, formatDynamicDockSlots());
        for (DockSlot slot : dockSlots.values()) {
            prefs.setString(PuppeteerWorkspacePrefs.KEY_DOCK_SLOT_PREFIX + slot.slotId(),
                formatDockSlotItemList(slot.items()));
        }
        List<String> toolbarIds = new ArrayList<>();
        for (CollapsibleToolbarCluster cluster : toolbarPane.getClustersSnapshot()) {
            DockItem item = toolbarDockItems.get(cluster.getClusterKey());
            if (item != null && !toolbarIds.contains(item.id())) {
                toolbarIds.add(item.id());
            }
        }
        List<String> hiddenIds = new ArrayList<>();
        for (String itemId : hiddenDockItemIds) {
            if (!edgeBarDockItemIds.contains(itemId)) {
                hiddenIds.add(itemId);
            }
        }
        prefs.setString(PuppeteerWorkspacePrefs.KEY_DOCK_TOOLBAR_ORDER, formatDockIdList(toolbarIds));
        prefs.setString(PuppeteerWorkspacePrefs.KEY_DOCK_HIDDEN_ITEMS, formatDockIdList(hiddenIds));
    }

    private void applyFloatingDockerPrefs() {
        if (workspacePrefs == null) return;
        edgeBarDockItemIds.clear();
        edgeBarDockItemIds.addAll(parseDockIdList(
            workspacePrefs.getString(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_ITEMS).orElse("")));
        edgeBarEnabled = workspacePrefs.getBoolean(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_ENABLED)
            .orElse(!edgeBarDockItemIds.isEmpty()
                && workspacePrefs.getString(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_ITEMS).isPresent());
        edgeBarEdge = parseFloatingEdge(
            workspacePrefs.getString(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_EDGE).orElse(""),
            FloatingEdge.RIGHT
        );
        edgeBarOffsetRatio = clampDouble(
            workspacePrefs.getDouble(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_OFFSET).orElse(0.5),
            0.0,
            1.0
        );
        edgeBarPreferredWidth = Math.max(
            EDGE_BAR_PANEL_MIN_WIDTH,
            workspacePrefs.getDouble(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_WIDTH).orElse(EDGE_BAR_PANEL_WIDTH)
        );
        edgeBarPreferredHeight = Math.max(
            EDGE_BAR_PANEL_MIN_HEIGHT,
            workspacePrefs.getDouble(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_HEIGHT).orElse(EDGE_BAR_PANEL_HEIGHT)
        );
        hiddenDockItemIds.removeAll(edgeBarDockItemIds);
        for (String itemId : new ArrayList<>(edgeBarDockItemIds)) {
            DockItem item = dockItems.get(itemId);
            if (item != null && item.homeToolbar()) {
                ensureFloatingToolbarDocker(item, 18.0, 18.0);
            }
        }
        for (String key : workspacePrefs.snapshotEntries().keySet()) {
            if (!key.startsWith(PuppeteerWorkspacePrefs.KEY_FLOATING_DOCKER_PREFIX)) continue;
            String itemId = key.substring(PuppeteerWorkspacePrefs.KEY_FLOATING_DOCKER_PREFIX.length());
            DockItem item = dockItems.get(itemId);
            if (item != null && item.homeToolbar()) {
                ensureFloatingToolbarDocker(item, 18.0, 18.0);
            }
        }
        workspacePrefs.getString(PuppeteerWorkspacePrefs.KEY_FLOATING_DOCKER_SNAP_LINKS)
            .ifPresentOrElse(this::applyFloatingSnapLinks, floatingDockerSnapLinks::clear);
        for (Map.Entry<String, FloatingDocker> entry : floatingToolbarDockers.entrySet()) {
            FloatingDocker floating = entry.getValue();
            workspacePrefs.getString(PuppeteerWorkspacePrefs.KEY_FLOATING_DOCKER_PREFIX + entry.getKey())
                .ifPresent(raw -> floating.applyPositionPref(raw));
            if (hiddenDockItemIds.contains(entry.getKey())) {
                floating.hideImmediately();
            } else if (edgeBarDockItemIds.contains(entry.getKey())) {
                floating.hideImmediately();
            } else {
                floating.showImmediately();
            }
        }
        refreshEdgeBar();
    }

    private void captureFloatingDockerPrefsInto(PuppeteerWorkspacePrefs prefs) {
        if (prefs == null) return;
        prefs.removeKeysStartingWith(PuppeteerWorkspacePrefs.KEY_FLOATING_DOCKER_PREFIX);
        for (Map.Entry<String, FloatingDocker> entry : floatingToolbarDockers.entrySet()) {
            prefs.setString(PuppeteerWorkspacePrefs.KEY_FLOATING_DOCKER_PREFIX + entry.getKey(),
                entry.getValue().formatPositionPref());
        }
        prefs.setString(PuppeteerWorkspacePrefs.KEY_FLOATING_DOCKER_SNAP_LINKS, formatFloatingSnapLinks());
        prefs.setBoolean(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_ENABLED, edgeBarEnabled);
        prefs.setString(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_EDGE, edgeBarEdge.name());
        prefs.setDouble(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_OFFSET, edgeBarOffsetRatio);
        prefs.setDouble(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_WIDTH, edgeBarPreferredWidth);
        prefs.setDouble(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_HEIGHT, edgeBarPreferredHeight);
        prefs.setString(PuppeteerWorkspacePrefs.KEY_EDGE_BAR_ITEMS, formatDockIdList(edgeBarDockItemIds));
    }

    private File resolveRegisteredJesFile(String timelineName) {
        if (projectRoot == null || timelineName == null || timelineName.isBlank()) return null;
        if (!PuppeteerVerification.isValidTimelineName(timelineName)) return null;
        return projectRoot.toPath()
            .resolve("scripts").resolve("timelines").resolve(timelineName + ".jes")
            .toFile();
    }

    private void persistWorkspacePrefsNow() {
        if (workspacePrefs == null) return;
        captureWorkspacePrefsInto(workspacePrefs);
        workspacePrefs.save();
    }

    private void scheduleDraftSave() {
        if (draftStore == null) return;
        String name = tfTimelineName != null ? tfTimelineName.getText().trim() : "";
        if (name.isEmpty()) return;
        if (!PuppeteerVerification.isValidTimelineName(name)) return;
        String code = codePreview != null ? codePreview.getCode() : null;
        if (code == null || code.isBlank()) return;
        draftStore.scheduleSave(name, code);
    }

    public void showRecordGifDialog() {
        if (previewRecorder == null || projectRoot == null) {
            overlayDialog.showDialog(
                "Record Preview as GIF",
                "Recording requires a saved project (no project root is set).",
                null,
                ActionEditorDialogOverlay.ActionSpec.accent("Close", overlayDialog::hideOverlay));
            return;
        }
        if (previewRecorder.isActive()) {
            overlayDialog.showDialog(
                "Record Preview as GIF",
                "A recording is already in progress.",
                null,
                ActionEditorDialogOverlay.ActionSpec.accent("OK", overlayDialog::hideOverlay));
            return;
        }

        double durationMs = Math.max(100.0, project.getTotalDurationMs());
        double loopStart = project.hasLoopRegion() ? project.getLoopStartMs() : 0.0;
        double loopEnd = project.hasLoopRegion() ? project.getLoopEndMs() : durationMs;

        String defaultBaseName = (tfTimelineName != null && !tfTimelineName.getText().isBlank())
            ? tfTimelineName.getText().trim()
            : "preview";
        File defaultDir = projectRoot.toPath().resolve("exports").resolve("puppeteer").toFile();

        TextField tfBaseName = new TextField(defaultBaseName);
        tfBaseName.setPrefColumnCount(20);
        TextField tfOutputDir = new TextField(defaultDir.getAbsolutePath());
        tfOutputDir.setPrefColumnCount(28);
        Button btnBrowse = new Button("Browse...");
        btnBrowse.setStyle(STYLE_BTN_DARK);
        btnBrowse.setOnAction(ev -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose recording output directory");
            File current = new File(tfOutputDir.getText().trim());
            File initial = current.isDirectory() ? current
                : (current.getParentFile() != null && current.getParentFile().isDirectory()
                    ? current.getParentFile()
                    : projectRoot);
            if (initial != null && initial.isDirectory()) chooser.setInitialDirectory(initial);
            File picked = chooser.showDialog(this);
            if (picked != null) tfOutputDir.setText(picked.getAbsolutePath());
        });

        TextField tfStart = new TextField(String.valueOf((long) loopStart));
        tfStart.setPrefColumnCount(7);
        TextField tfEnd = new TextField(String.valueOf((long) loopEnd));
        tfEnd.setPrefColumnCount(7);
        Spinner<Integer> spFps = new Spinner<>();
        spFps.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, 24));
        spFps.setEditable(true);
        spFps.setPrefWidth(90);
        CheckBox cbGif = new CheckBox("Animated GIF");
        cbGif.setSelected(true);
        CheckBox cbPng = new CheckBox("PNG sequence");
        cbPng.setSelected(false);

        // Resolution controls — default to canvas dimensions, lock aspect by default.
        javafx.scene.canvas.Canvas previewCanvas = animationPreview.getPreviewCanvas();
        int nativeW = previewCanvas != null ? Math.max(1, (int) Math.round(previewCanvas.getWidth())) : 1280;
        int nativeH = previewCanvas != null ? Math.max(1, (int) Math.round(previewCanvas.getHeight())) : 720;
        double aspectRatio = nativeH == 0 ? 1.0 : (double) nativeW / nativeH;

        Spinner<Integer> spWidth = new Spinner<>();
        spWidth.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(16, 4096, nativeW, 16));
        spWidth.setEditable(true);
        spWidth.setPrefWidth(120);

        Spinner<Integer> spHeight = new Spinner<>();
        spHeight.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(16, 4096, nativeH, 16));
        spHeight.setEditable(true);
        spHeight.setPrefWidth(120);

        CheckBox cbLockAspect = new CheckBox("Lock aspect");
        cbLockAspect.setSelected(true);

        Button btnResetRes = new Button("Native");
        btnResetRes.setStyle(STYLE_BTN_DARK);
        btnResetRes.setTooltip(new Tooltip("Reset to native preview size (" + nativeW + " x " + nativeH + ")"));
        btnResetRes.setOnAction(ev -> {
            spWidth.getValueFactory().setValue(nativeW);
            spHeight.getValueFactory().setValue(nativeH);
        });

        // Lock aspect ratio: editing one side updates the other.
        final boolean[] aspectGuard = {false};
        spWidth.valueProperty().addListener((obs, oldV, newV) -> {
            if (!cbLockAspect.isSelected() || aspectGuard[0] || newV == null) return;
            aspectGuard[0] = true;
            try {
                int derivedH = Math.max(16, Math.min(4096, (int) Math.round(newV / aspectRatio)));
                spHeight.getValueFactory().setValue(derivedH);
            } finally {
                aspectGuard[0] = false;
            }
        });
        spHeight.valueProperty().addListener((obs, oldV, newV) -> {
            if (!cbLockAspect.isSelected() || aspectGuard[0] || newV == null) return;
            aspectGuard[0] = true;
            try {
                int derivedW = Math.max(16, Math.min(4096, (int) Math.round(newV * aspectRatio)));
                spWidth.getValueFactory().setValue(derivedW);
            } finally {
                aspectGuard[0] = false;
            }
        });

        ProgressBar progress = new ProgressBar(0.0);
        progress.setPrefWidth(420);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);
        progress.setManaged(false);

        Label lblStatus = new Label("Captures the preview canvas frame-by-frame using the chosen FPS.");
        lblStatus.setWrapText(true);
        lblStatus.setStyle("-fx-text-fill: #989898; -fx-font-size: 11px;");

        HBox baseRow = new HBox(8, tfBaseName);
        HBox.setHgrow(tfBaseName, Priority.ALWAYS);
        tfBaseName.setMaxWidth(Double.MAX_VALUE);

        HBox outputRow = new HBox(6, tfOutputDir, btnBrowse);
        HBox.setHgrow(tfOutputDir, Priority.ALWAYS);
        tfOutputDir.setMaxWidth(Double.MAX_VALUE);

        HBox rangeRow = new HBox(6,
            new Label("Start"), tfStart,
            spacer(8),
            new Label("End"), tfEnd);
        rangeRow.setAlignment(Pos.CENTER_LEFT);

        HBox fpsRow = new HBox(6,
            new Label("FPS"), spFps,
            spacer(12),
            cbGif, cbPng);
        fpsRow.setAlignment(Pos.CENTER_LEFT);

        HBox resolutionRow = new HBox(6,
            new Label("Width"), spWidth,
            new Label("\u00d7"), spHeight,
            spacer(8),
            cbLockAspect,
            spacer(8),
            btnResetRes);
        resolutionRow.setAlignment(Pos.CENTER_LEFT);

        Label headerBaseName = sectionLabel("Base name");
        Label headerOutputDir = sectionLabel("Output directory");
        Label headerRange = sectionLabel("Time range (ms)");
        Label headerFps = sectionLabel("Playback");
        Label headerResolution = sectionLabel("Resolution");

        VBox content = new VBox(6,
            headerBaseName, baseRow,
            headerOutputDir, outputRow,
            headerRange, rangeRow,
            headerFps, fpsRow,
            headerResolution, resolutionRow,
            progress, lblStatus);
        content.setFillWidth(true);
        content.setSpacing(6);
        content.getStyleClass().add("puppeteer-record-form");

        // Holders so callbacks can mutate state.
        final Runnable[] showInitialActions = {null};
        final Runnable[] showRecordingActions = {null};
        final Runnable[] showFinishedActions = {null};
        // Captured at record-start so the post-export Reveal button can target it
        // even after the user typed something else into the directory field afterwards.
        final File[] lastOutputDir = {null};
        final java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<String>> showErrorAction =
            new java.util.concurrent.atomic.AtomicReference<>();

        Runnable beginRecording = () -> {
            double startMs;
            double endMs;
            try {
                startMs = Double.parseDouble(tfStart.getText().trim());
                endMs = Double.parseDouble(tfEnd.getText().trim());
            } catch (NumberFormatException ex) {
                lblStatus.setText("Start and End must be numeric milliseconds.");
                showInitialActions[0].run();
                return;
            }
            if (endMs <= startMs) {
                lblStatus.setText("End must be greater than Start.");
                showInitialActions[0].run();
                return;
            }
            String baseName = tfBaseName.getText().trim();
            if (baseName.isEmpty()) {
                lblStatus.setText("Choose a base name for the output files.");
                showInitialActions[0].run();
                return;
            }
            String dirPath = tfOutputDir.getText().trim();
            if (dirPath.isEmpty()) {
                lblStatus.setText("Choose an output directory.");
                showInitialActions[0].run();
                return;
            }
            File outputDir = new File(dirPath);
            if (!cbGif.isSelected() && !cbPng.isSelected()) {
                lblStatus.setText("Enable at least one output format (GIF or PNG sequence).");
                showInitialActions[0].run();
                return;
            }
            int fps = spFps.getValue() != null ? spFps.getValue() : 24;
            int outW = spWidth.getValue() != null ? spWidth.getValue() : nativeW;
            int outH = spHeight.getValue() != null ? spHeight.getValue() : nativeH;
            PuppeteerPreviewRecorder.Spec spec = new PuppeteerPreviewRecorder.Spec(
                outputDir, baseName, startMs, endMs, fps,
                cbPng.isSelected(), cbGif.isSelected(),
                outW, outH);

            if (project.isPlaying()) pause();

            // Snapshot the output folder so the finished-state Reveal action survives
            // the user editing the directory field after recording completes.
            lastOutputDir[0] = outputDir;

            tfBaseName.setDisable(true);
            tfOutputDir.setDisable(true);
            btnBrowse.setDisable(true);
            tfStart.setDisable(true);
            tfEnd.setDisable(true);
            spFps.setDisable(true);
            cbGif.setDisable(true);
            cbPng.setDisable(true);
            spWidth.setDisable(true);
            spHeight.setDisable(true);
            cbLockAspect.setDisable(true);
            btnResetRes.setDisable(true);

            progress.setVisible(true);
            progress.setManaged(true);
            progress.setProgress(0.0);
            lblStatus.setText("Recording " + spec.frameCount() + " frames at " + fps + " fps...");
            showRecordingActions[0].run();

            previewRecorder.record(spec, new PuppeteerPreviewRecorder.Hooks() {
                @Override
                public void seekAndRender(double timeMs) {
                    project.setPlayheadMs(timeMs);
                    timelinePanel.refresh();
                    updateTimeLabel();
                    updatePreview();
                }
                @Override
                public void onProgress(double normalizedProgress) {
                    progress.setProgress(normalizedProgress);
                }
                @Override
                public void onFinished(PuppeteerPreviewRecorder.Result result) {
                    if (result.success()) {
                        StringBuilder sb = new StringBuilder("Recording complete:\n");
                        for (File f : result.outputs()) {
                            sb.append("• ").append(f.getAbsolutePath()).append('\n');
                        }
                        lblStatus.setText(sb.toString().trim());
                        showFinishedActions[0].run();
                    } else {
                        showErrorAction.get().accept(result.error());
                    }
                }
            });
        };

        showInitialActions[0] = () -> overlayDialog.showDialog(
            "Record Preview as GIF",
            "Capture the preview canvas frame-by-frame to PNG and/or animated GIF.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", () -> {
                if (previewRecorder.isActive()) previewRecorder.cancel();
            }),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Start Recording",
                ActionEditorDialogOverlay.ButtonStyle.ACCENT, beginRecording));

        showRecordingActions[0] = () -> overlayDialog.showDialog(
            "Recording Puppeteer Preview",
            "Recording is in progress. Stay on this dialog to monitor progress.",
            content,
            ActionEditorDialogOverlay.ActionSpec.danger("Cancel Recording", () -> {
                if (previewRecorder.isActive()) previewRecorder.cancel();
            }));

        showFinishedActions[0] = () -> overlayDialog.showDialog(
            "Recording Complete",
            "Files were written to disk.",
            content,
            ActionEditorDialogOverlay.ActionSpec.accent("Reveal in Folder", () -> {
                File dir = lastOutputDir[0];
                if (dir == null || !dir.isDirectory()) {
                    lblStatus.setText("Output folder is no longer available.");
                    return;
                }
                if (!com.jvn.editor.ui.EditorPathExplorer.show(this, dir)) {
                    lblStatus.setText("Could not reveal folder: " + dir.getAbsolutePath());
                }
            }),
            ActionEditorDialogOverlay.ActionSpec.neutral("Close", overlayDialog::hideOverlay));

        showErrorAction.set(errorMessage -> {
            lblStatus.setText("Recording failed: " + errorMessage);
            tfBaseName.setDisable(false);
            tfOutputDir.setDisable(false);
            btnBrowse.setDisable(false);
            tfStart.setDisable(false);
            tfEnd.setDisable(false);
            spFps.setDisable(false);
            cbGif.setDisable(false);
            cbPng.setDisable(false);
            spWidth.setDisable(false);
            spHeight.setDisable(false);
            cbLockAspect.setDisable(false);
            btnResetRes.setDisable(false);
            progress.setVisible(false);
            progress.setManaged(false);
            showInitialActions[0].run();
        });

        showInitialActions[0].run();
    }

    private static Region spacer(double width) {
        Region r = new Region();
        r.setMinWidth(width);
        r.setPrefWidth(width);
        return r;
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #c0c0c0; -fx-font-size: 10px; -fx-font-weight: bold;");
        return label;
    }

    private void promptDraftRestoreIfNeeded() {
        if (draftRestorePromptShown || draftStore == null || tfTimelineName == null) return;
        String name = tfTimelineName.getText().trim();
        if (name.isEmpty()) return;
        File registered = resolveRegisteredJesFile(name);
        Optional<PuppeteerDraftStore.DraftRecord> draft =
            draftStore.findRestorableDraft(name, registered);
        if (draft.isEmpty()) return;
        draftRestorePromptShown = true;
        PuppeteerDraftStore.DraftRecord rec = draft.get();

        long ageSec = Math.max(0L,
            (System.currentTimeMillis() - rec.lastModifiedMs()) / 1000L);
        String ageText = ageSec < 60 ? ageSec + "s ago"
            : ageSec < 3600 ? (ageSec / 60) + " min ago"
            : (ageSec / 3600) + "h " + ((ageSec % 3600) / 60) + "m ago";

        Label ageLabel = sectionLabel("Saved");
        Label ageValue = new Label(ageText);
        ageValue.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 12px;");

        Label pathLabel = sectionLabel("Draft file");
        Label pathValue = new Label(rec.file().getAbsolutePath());
        pathValue.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 11px; -fx-font-family: 'JetBrains Mono', 'SF Mono', 'Consolas', monospace;");
        pathValue.setWrapText(true);

        Label hint = new Label(
            "Restore loads the draft into the code preview where you can review it and "
                + "Commit it (overwriting the registered timeline). Discard deletes the draft "
                + "and continues with the registered timeline. Keep Draft leaves the file untouched "
                + "for next session.");
        hint.setStyle("-fx-text-fill: #989898; -fx-font-size: 11px;");
        hint.setWrapText(true);

        VBox content = new VBox(6,
            ageLabel, ageValue,
            pathLabel, pathValue,
            hint);
        content.setFillWidth(true);

        overlayDialog.showDialog(
            "Restore Puppeteer draft?",
            "An unsaved draft for \"" + name + "\" was found.",
            content,
            ActionEditorDialogOverlay.ActionSpec.danger("Discard", () -> draftStore.deleteDraft(name)),
            ActionEditorDialogOverlay.ActionSpec.neutral("Keep Draft", () -> {}),
            ActionEditorDialogOverlay.ActionSpec.accent("Restore", () -> {
                if (codePreview != null) {
                    codePreview.setCode(rec.code());
                    stagePreviewFromCode();
                }
            }));
    }

    public void setSourceScriptFile(java.io.File file) {
        this.scriptTargetFile = file;
        if (assetImporterPanel != null) {
            assetImporterPanel.setScriptTargetFile(file);
        }
    }

    java.io.File getSourceScriptFile() {
        return scriptTargetFile;
    }

    public void showAssetImporterWindow() {
        if (assetImporterWindow != null) {
            assetImporterWindow.show();
            assetImporterWindow.toFront();
            assetImporterWindow.requestFocus();
            return;
        }

        assetImporterPanel = new AssetPickerPanel();
        assetImporterPanel.setImportEnabled(true);
        assetImporterPanel.setPlacementActionsVisible(true);
        assetImporterPanel.setOnAddToScene(this::addImporterEntryToScene);
        assetImporterPanel.setProjectRoot(projectRoot);
        assetImporterPanel.setScriptTargetFile(scriptTargetFile);
        assetImporterPanel.setMinWidth(0);

        BorderPane root = new BorderPane(assetImporterPanel);
        root.setStyle("-fx-background-color: #121212;");

        Scene importerScene = new Scene(root, 920, 720);
        EditorTheme.apply(importerScene);

        Stage window = new Stage();
        window.initOwner(this);
        window.setTitle("Puppeteer Asset Importer");
        window.setScene(importerScene);
        window.setOnHidden(event -> {
            assetImporterWindow = null;
            assetImporterPanel = null;
        });

        assetImporterWindow = window;
        window.show();
    }

    private void addImporterEntryToScene(AssetPickerPanel.AssetEntry entry, PuppeteerAssetPlacementRole role) {
        if (entry == null) return;
        if (entry.isPresetEntry()) {
            addPresetToScene(entry, role);
        } else {
            addAssetToScene(entry.relativePath, entry.baseName, role);
        }
    }

    private void addAssetToScene(String relativePath, String suggestedName, PuppeteerAssetPlacementRole role) {
        if (relativePath == null || relativePath.isBlank()) return;
        if (scene == null) {
            setScene(new JesScene2D());
        }
        if (scene == null) return;

        PuppeteerAssetPlacementRole resolvedRole = role != null ? role : PuppeteerAssetPlacementRole.PROP;
        String entityName = resolveUniqueEntityName(suggestedName, resolvedRole);
        double[] naturalSize = resolveAssetNaturalSize(relativePath);
        PuppeteerAssetPlacement.Placement placement = PuppeteerAssetPlacement.plan(
            resolvedRole,
            animationPreview.getViewportDimensions(),
            naturalSize[0],
            naturalSize[1]
        );

        com.jvn.core.scene2d.Sprite2D sprite = new com.jvn.core.scene2d.Sprite2D(relativePath, placement.width(), placement.height());
        sprite.setOrigin(placement.originX(), placement.originY());
        sprite.setPosition(placement.x(), placement.y());
        sprite.setZ(placement.z());

        scene.add(sprite);
        scene.registerEntity(entityName, sprite);

        EntityTrack track = project.getOrCreateTrack(entityName);
        track.setLayerOrder((int) Math.round(placement.z()));
        captureProjectSnapshotBaseline();
        entitySelector.refresh(project);
        timelinePanel.refresh();
        timelinePanel.setSelectedTarget(entityName, false);
        animationPreview.selectEntity(entityName);
        updatePreview();
        refreshExportPreviewAndMarkDirty();
    }

    private void addPresetToScene(AssetPickerPanel.AssetEntry presetEntry, PuppeteerAssetPlacementRole role) {
        if (presetEntry == null || !presetEntry.isPresetEntry() || presetEntry.presetLayers.isEmpty()) {
            return;
        }
        if (scene == null) {
            setScene(new JesScene2D());
        }
        if (scene == null) return;

        PuppeteerAssetPlacementRole resolvedRole = role != null ? role : PuppeteerAssetPlacementRole.CHARACTER;
        String groupBase = buildPresetGroupBaseName(presetEntry);
        String groupName = resolveUniqueGroupName(groupBase);

        double[] compositeSize = resolvePresetNaturalSize(presetEntry);
        PuppeteerAssetPlacement.Placement placement = PuppeteerAssetPlacement.plan(
            resolvedRole,
            animationPreview.getViewportDimensions(),
            compositeSize[0],
            compositeSize[1]
        );

        double naturalWidth = compositeSize[0] > 1.0 ? compositeSize[0] : placement.width();
        double naturalHeight = compositeSize[1] > 1.0 ? compositeSize[1] : placement.height();
        double scaleX = naturalWidth > 0.0 ? placement.width() / naturalWidth : 1.0;
        double scaleY = naturalHeight > 0.0 ? placement.height() / naturalHeight : 1.0;
        double scale = Double.isFinite(scaleX) && Double.isFinite(scaleY) ? Math.min(scaleX, scaleY) : 1.0;
        if (!Double.isFinite(scale) || scale <= 0.0) {
            scale = 1.0;
        }

        EntityGroup group = project.getOrCreateGroup(groupName);
        group.setLayerOrder((int) Math.round(placement.z()));

        int layerIndex = 0;
        for (AssetPickerPanel.AssetEntry.PresetLayer layer : presetEntry.presetLayers) {
            if (layer == null || layer.relativePath() == null || layer.relativePath().isBlank()) {
                continue;
            }
            String layerBase = buildPresetLayerEntityBaseName(groupBase, layer);
            String entityName = resolveUniqueEntityName(layerBase, resolvedRole);
            double[] layerSize = resolveAssetNaturalSize(layer.relativePath());
            double layerWidth = layerSize[0] > 1.0 ? layerSize[0] * scale : placement.width();
            double layerHeight = layerSize[1] > 1.0 ? layerSize[1] * scale : placement.height();

            com.jvn.core.scene2d.Sprite2D sprite = new com.jvn.core.scene2d.Sprite2D(layer.relativePath(), layerWidth, layerHeight);
            sprite.setOrigin(placement.originX(), placement.originY());
            sprite.setPosition(placement.x(), placement.y());
            sprite.setZ(placement.z() + layerIndex);

            scene.add(sprite);
            scene.registerEntity(entityName, sprite);

            EntityTrack track = project.getOrCreateTrack(entityName);
            track.setLayerOrder(layerIndex);
            project.addEntityToGroup(entityName, groupName);
            layerIndex++;
        }

        if (layerIndex == 0) {
            project.removeGroup(groupName);
            return;
        }

        captureProjectSnapshotBaseline();
        entitySelector.refresh(project);
        entitySelector.selectGroup(groupName);
        timelinePanel.refresh();
        timelinePanel.setSelectedTarget(groupName, true);
        animationPreview.clearSelection();
        updatePreview();
        refreshExportPreviewAndMarkDirty();

    }

    private void deleteSceneSelection(String name, boolean group) {
        if (name == null || name.isBlank()) return;

        String selectedTarget = timelinePanel.getSelectedEntity();
        boolean clearSelection = Objects.equals(selectedTarget, name) && timelinePanel.isSelectedGroup() == group;
        if (!group && Objects.equals(animationPreview.getSelectedEntityName(), name)) {
            clearSelection = true;
        }

        if (group) {
            project.removeGroup(name);
        } else {
            project.removeTrack(name);
            if (scene != null) {
                scene.removeEntity(name);
                animationPreview.setScene(scene);
                entitySelector.setScene(scene);
            }
        }

        project.pruneOrbitAnchors(collectProjectAnchorTargetNames());
        animationPreview.setOrbitAnchors(project.getOrbitAnchorsView());
        animationPreview.setOrbitAnchorSources(project.getOrbitAnchorSourcesView());
        animationPreview.setOrbitAnchorSourceOffsets(project.getOrbitAnchorSourceOffsetsView());

        if (clearSelection) {
            clearInspectorSelection();
        }

        captureSceneStateBaseline();
        captureProjectSnapshotBaseline();
        entitySelector.refresh(project);
        timelinePanel.refresh();
        refreshPropertyPickerChoices();
        updatePreview();
        refreshExportPreviewAndMarkDirty();
    }

    private void clearInspectorSelection() {
        entitySelector.selectEntity(null);
        animationPreview.clearSelection();
        timelinePanel.setSelectedTarget(null, false);
        keyframeEditor.setSelectionContext(null, false, false);
        keyframeEditor.setKeyframe(null, null);
    }

    private String resolveUniqueEntityName(String suggestedName, PuppeteerAssetPlacementRole role) {
        String base = suggestedName == null ? "" : suggestedName.trim();
        if (base.isBlank()) {
            base = switch (role != null ? role : PuppeteerAssetPlacementRole.PROP) {
                case BACKGROUND -> "background";
                case CHARACTER -> "character";
                case PROP -> "prop";
            };
        }
        String entityName = base;
        int suffix = 2;
        while (scene != null && scene.find(entityName) != null) {
            entityName = base + "_" + suffix++;
        }
        return entityName;
    }

    private EntityTrack resolveAnimatedTrack(String name, boolean createIfMissing) {
        if (name == null || name.isBlank()) {
            return null;
        }
        EntityGroup group = project.getGroup(name);
        if (group != null) {
            return group.getGroupTrack();
        }
        return createIfMissing ? project.getOrCreateTrack(name) : project.getTrack(name);
    }

    private String resolveUniqueGroupName(String suggestedName) {
        String base = suggestedName == null ? "" : suggestedName.trim();
        if (base.isBlank()) {
            base = "CharacterGroup";
        }
        String groupName = base;
        int suffix = 2;
        while (project.getGroup(groupName) != null) {
            groupName = base + "_" + suffix++;
        }
        return groupName;
    }

    private String buildPresetGroupBaseName(AssetPickerPanel.AssetEntry presetEntry) {
        if (presetEntry == null) {
            return "CharacterPreset";
        }
        String character = presetEntry.presetCharacterId != null && !presetEntry.presetCharacterId.isBlank()
            ? presetEntry.presetCharacterId
            : "character";
        String preset = presetEntry.presetId != null && !presetEntry.presetId.isBlank()
            ? presetEntry.presetId
            : "preset";
        return character + "_" + preset;
    }

    private String buildPresetLayerEntityBaseName(String groupBase, AssetPickerPanel.AssetEntry.PresetLayer layer) {
        String suffix = layer != null && layer.layerId() != null && !layer.layerId().isBlank()
            ? layer.layerId()
            : (layer != null && layer.displayName() != null && !layer.displayName().isBlank() ? layer.displayName() : "layer");
        return (groupBase == null || groupBase.isBlank() ? "character" : groupBase) + "_" + suffix;
    }

    private double[] resolvePresetNaturalSize(AssetPickerPanel.AssetEntry presetEntry) {
        double width = -1.0;
        double height = -1.0;
        if (presetEntry == null || presetEntry.presetLayers.isEmpty()) {
            return new double[]{width, height};
        }
        for (AssetPickerPanel.AssetEntry.PresetLayer layer : presetEntry.presetLayers) {
            if (layer == null || layer.relativePath() == null || layer.relativePath().isBlank()) {
                continue;
            }
            double[] size = resolveAssetNaturalSize(layer.relativePath());
            if (size[0] > width) {
                width = size[0];
            }
            if (size[1] > height) {
                height = size[1];
            }
        }
        return new double[]{width, height};
    }

    private double[] resolveAssetNaturalSize(String relativePath) {
        javafx.scene.image.Image image = loadAssetImage(relativePath);
        if (image != null && image.getWidth() > 1.0 && image.getHeight() > 1.0) {
            return new double[] { image.getWidth(), image.getHeight() };
        }
        return new double[] { -1.0, -1.0 };
    }

    private javafx.scene.image.Image loadAssetImage(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        try {
            java.net.URL resource = getClass().getClassLoader().getResource(relativePath);
            if (resource != null) {
                javafx.scene.image.Image image = new javafx.scene.image.Image(resource.toExternalForm(), false);
                if (image.getWidth() > 1.0 && image.getHeight() > 1.0) {
                    return image;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            java.io.File file = new java.io.File(relativePath);
            if (!file.isAbsolute() && projectRoot != null) {
                file = new java.io.File(projectRoot, relativePath);
            }
            if (file.isFile()) {
                javafx.scene.image.Image image = new javafx.scene.image.Image(file.toURI().toString(), false);
                if (image.getWidth() > 1.0 && image.getHeight() > 1.0) {
                    return image;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public AnimationProject getProject() { return project; }

    public void setTimelineName(String name) {
        String normalized = name != null ? name.trim() : "";
        if (normalized.isBlank()) return;
        project.setName(normalized);
        tfTimelineName.setText(normalized);
        setDirty(dirty);
    }

    public void setOnCopyCode(Consumer<String> callback) { this.onCopyCode = callback; }

    public void play() {
        if (project.isPlaying()) return;
        clearExpressionPreview();
        project.setPlaying(true);
        lastNanos = System.nanoTime();
        playbackTimelineRefreshGate.reset();
        playbackChromeRefreshGate.reset();
        playbackTimer.start();
        refreshTransportButtonStates();
        updateStatusBar();
    }

    public void pause() {
        project.setPlaying(false);
        playbackTimer.stop();
        playbackTimelineRefreshGate.reset();
        playbackChromeRefreshGate.reset();
        timelinePanel.setPlayhead(project.getPlayheadMs());
        refreshSidebarTabs();
        refreshTransportButtonStates();
        updateTimeLabel();
    }

    public void stop() {
        pause();
        project.setPlayheadMs(0);
        timelinePanel.setPlayhead(0);
        updateTimeLabel();
        updatePreview();
    }

    public void rewind() {
        project.setPlayheadMs(0);
        timelinePanel.setPlayhead(0);
        updateTimeLabel();
        updatePreview();
    }

    private void setTimelineSnapEnabled(boolean enabled) {
        if (timelinePanel == null) return;
        timelinePanel.setSnapEnabled(enabled);
        if (cbSnap != null && cbSnap.isSelected() != enabled) {
            cbSnap.setSelected(enabled);
        }
        updateStatusBar();
        refreshToolbarCommandSummary();
    }

    private void setTimelineSnapStep(double stepMs) {
        if (timelinePanel == null) return;
        timelinePanel.setSnapStepMs(stepMs);
        if (tfSnapMs != null) {
            tfSnapMs.setText(String.format(Locale.ROOT, "%.0f", timelinePanel.getSnapStepMs()));
        }
        updateStatusBar();
        refreshToolbarCommandSummary();
    }

    private void setAutoKeyEnabled(boolean enabled) {
        autoKeyEnabled = enabled;
        if (cbAutoKey != null && cbAutoKey.isSelected() != enabled) {
            cbAutoKey.setSelected(enabled);
        }
        updateStatusBar();
        refreshToolbarCommandSummary();
    }

    private void setRuntimeParityPreviewEnabled(boolean enabled) {
        runtimeParityPreview = enabled;
        if (cbRuntimePreview != null && cbRuntimePreview.isSelected() != enabled) {
            cbRuntimePreview.setSelected(enabled);
        }
        updatePreview();
        updateStatusBar();
        refreshToolbarCommandSummary();
    }

    private void setViewportStabilizationEnabled(boolean enabled) {
        viewportStabilizationEnabled = enabled;
        if (cbViewportStabilize != null && cbViewportStabilize.isSelected() != enabled) {
            cbViewportStabilize.setSelected(enabled);
        }
        animationPreview.setViewportStabilizationEnabled(enabled);
        updatePreview();
        updateStatusBar();
        refreshToolbarCommandSummary();
    }

    private void setPreviewSnapToGridEnabled(boolean enabled) {
        if (animationPreview == null) return;
        animationPreview.setSnapToGridEnabled(enabled);
        if (cbSnapGrid != null && cbSnapGrid.isSelected() != enabled) {
            cbSnapGrid.setSelected(enabled);
        }
        updateStatusBar();
        refreshToolbarCommandSummary();
    }

    private void setPreviewSnapToEntityEnabled(boolean enabled) {
        if (animationPreview == null) return;
        animationPreview.setSnapToEntityEnabled(enabled);
        if (cbSnapEntity != null && cbSnapEntity.isSelected() != enabled) {
            cbSnapEntity.setSelected(enabled);
        }
        updateStatusBar();
        refreshToolbarCommandSummary();
    }

    private void setScrollZoomMode(AnimationPreview.ScrollZoomMode mode) {
        AnimationPreview.ScrollZoomMode resolved = mode == null
            ? AnimationPreview.ScrollZoomMode.VIEW
            : mode;
        if (animationPreview != null) {
            animationPreview.setScrollZoomMode(resolved);
        }
        String label = resolved == AnimationPreview.ScrollZoomMode.CAMERA ? "Wheel: Camera" : "Wheel: View";
        if (cbWheelMode != null && !Objects.equals(cbWheelMode.getValue(), label)) {
            cbWheelMode.setValue(label);
        }
        updateStatusBar();
        refreshToolbarCommandSummary();
    }

    private void setPlaybackSpeed(double speed) {
        double resolved = Double.isFinite(speed) && speed > 0.0 ? speed : 1.0;
        playbackSpeed = resolved;
        String label = formatPlaybackSpeedLabel(resolved);
        if (cbSpeed != null && !Objects.equals(cbSpeed.getValue(), label)) {
            cbSpeed.setValue(label);
        }
        updateStatusBar();
    }

    private static String formatPlaybackSpeedLabel(double speed) {
        double resolved = Double.isFinite(speed) && speed > 0.0 ? speed : 1.0;
        if (Math.abs(resolved - Math.rint(resolved)) < 0.0001) {
            return String.format(Locale.ROOT, "%.0fx", resolved);
        }
        return String.format(Locale.ROOT, "%.2fx", resolved).replaceAll("0+x$", "x");
    }

    private void setCompactExportEnabled(boolean enabled) {
        compactExport = enabled;
        if (cbCompactExport != null && cbCompactExport.isSelected() != enabled) {
            cbCompactExport.setSelected(enabled);
        }
        refreshExportPreview();
        updateStatusBar();
        refreshToolbarCommandSummary();
    }

    private void setupPlaybackTimer() {
        playbackTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!project.isPlaying()) return;
                long deltaNanos = now - lastNanos;
                lastNanos = now;
                double deltaMs = deltaNanos / 1_000_000.0;

                double newTime = project.getPlayheadMs() + deltaMs * playbackSpeed;
                double loopEnd = project.hasLoopRegion()
                    ? project.getLoopEndMs()
                    : project.getTotalDurationMs();
                double loopStart = project.hasLoopRegion()
                    ? project.getLoopStartMs()
                    : 0;
                if (newTime >= loopEnd) {
                    if (project.isLooping()) {
                        newTime = loopStart;
                    } else {
                        newTime = project.getTotalDurationMs();
                        pause();
                    }
                }
                project.setPlayheadMs(newTime);
                if (playbackTimelineRefreshGate.shouldRefresh(now)) {
                    timelinePanel.setPlayhead(newTime);
                }
                boolean refreshPlaybackChrome = playbackChromeRefreshGate.shouldRefresh(now);
                updatePreview(false);
                if (refreshPlaybackChrome) updatePlaybackChrome();
            }
        };
        refreshTransportButtonStates();
    }

    public void updateTimeLabel() {
        lblTime.setText(String.format("%.0f ms", project.getPlayheadMs()));
        updateStatusBar();
    }

    private void updatePlaybackStatusTime() {
        if (statusTimelineLabel == null) return;
        statusTimelineLabel.setText(
            formatStatusTime(project.getPlayheadMs())
                + " / "
                + formatStatusTime(project.getTotalDurationMs()));
    }

    private void updatePlaybackChrome() {
        lblTime.setText(String.format(Locale.ROOT, "%.0f ms", project.getPlayheadMs()));
        updatePlaybackStatusTime();
        if (lblSidebarSelectionPlayhead != null) {
            lblSidebarSelectionPlayhead.setText(
                String.format(Locale.ROOT, "%.0f ms", project.getPlayheadMs()));
        }
        if (lblSidebarSceneCamera != null && animationPreview != null) {
            var camera = animationPreview.getCamera();
            lblSidebarSceneCamera.setText(String.format(
                Locale.ROOT,
                "X %.1f  Y %.1f  Z %.2f",
                camera.getX(),
                camera.getY(),
                camera.getZoom()));
        }
    }

    private void refreshPropertyPickerChoices() {
        if (cbProperty == null || timelinePanel == null) return;
        List<PropertyType> allowed;
        if (timelinePanel.isRuntimeCameraSelected()) {
            allowed = List.of(
                PropertyType.CAMERA_X,
                PropertyType.CAMERA_Y,
                PropertyType.CAMERA_ZOOM,
                PropertyType.CAMERA_DOF_FOCUS,
                PropertyType.CAMERA_DOF_STRENGTH,
                PropertyType.CAMERA_DOF_MAX_BLUR);
        } else if (timelinePanel.isSelectedGroup()) {
            allowed = GROUP_PROPERTY_CHOICES;
        } else {
            allowed = List.of(PropertyType.values());
        }
        if (!cbProperty.getItems().equals(allowed)) {
            cbProperty.getItems().setAll(allowed);
        }
        PropertyType selected = timelinePanel.getSelectedProperty();
        if (selected == null || !allowed.contains(selected)) {
            selected = allowed.isEmpty() ? null : allowed.get(0);
        }
        if (selected != null && cbProperty.getValue() != selected) {
            cbProperty.setValue(selected);
        }
    }

    private static ListCell<PropertyType> propertyTypeListCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(PropertyType property, boolean empty) {
                super.updateItem(property, empty);
                setText(empty || property == null ? null : property.getDisplayName());
            }
        };
    }

    public void updatePreview() {
        updatePreview(true);
    }

    private void updatePreview(boolean refreshEditorChrome) {
        project.evaluateFrame(() -> updatePreviewFrame(refreshEditorChrome));
    }

    private void updatePreviewFrame(boolean refreshEditorChrome) {
        if (scene == null) {
            if (refreshEditorChrome) refreshSidebarTabs();
            return;
        }

        double time = project.getPlayheadMs();
        restorePreviewBaselineState();
        if (runtimeParityPreview) {
            applyRuntimeParityPreview(time);
            animationPreview.render();
            if (refreshEditorChrome) refreshSidebarTabs();
            return;
        }
        var previewCamera = animationPreview.getCamera();
        double cameraX = previewCamera.getX();
        double cameraY = previewCamera.getY();
        double cameraZoom = previewCamera.getZoom();
        boolean hasCameraX = false;
        boolean hasCameraY = false;
        boolean hasCameraZoom = false;
        double dofFocus = previewCamera.getFocusDepth();
        double dofStrength = previewCamera.getDepthOfFieldStrength();
        double dofMaxBlur = previewCamera.getDepthOfFieldMaxBlur();
        boolean hasDofFocus = false;
        boolean hasDofStrength = false;
        boolean hasDofMaxBlur = false;
        for (EntityTrack track : project.getTracks()) {
            if (track.hasKeyframes(PropertyType.CAMERA_X)) {
                cameraX = project.computeValueAt(track.getEntityName(), PropertyType.CAMERA_X, time);
                hasCameraX = true;
            }
            if (track.hasKeyframes(PropertyType.CAMERA_Y)) {
                cameraY = project.computeValueAt(track.getEntityName(), PropertyType.CAMERA_Y, time);
                hasCameraY = true;
            }
            if (track.hasKeyframes(PropertyType.CAMERA_ZOOM)) {
                cameraZoom = project.computeValueAt(track.getEntityName(), PropertyType.CAMERA_ZOOM, time);
                hasCameraZoom = true;
            }
            if (track.hasKeyframes(PropertyType.CAMERA_DOF_FOCUS)) {
                dofFocus = project.computeValueAt(track.getEntityName(), PropertyType.CAMERA_DOF_FOCUS, time, dofFocus);
                hasDofFocus = true;
            }
            if (track.hasKeyframes(PropertyType.CAMERA_DOF_STRENGTH)) {
                dofStrength = project.computeValueAt(track.getEntityName(), PropertyType.CAMERA_DOF_STRENGTH, time, dofStrength);
                hasDofStrength = true;
            }
            if (track.hasKeyframes(PropertyType.CAMERA_DOF_MAX_BLUR)) {
                dofMaxBlur = project.computeValueAt(track.getEntityName(), PropertyType.CAMERA_DOF_MAX_BLUR, time, dofMaxBlur);
                hasDofMaxBlur = true;
            }
            for (String customPropertyKey : track.getAnimatedCustomProperties()) {
                if (TimelinePanel.isRuntimeCameraTarget(track.getEntityName())) {
                    double baselineValue = baselineCustomPropertyValue(track.getEntityName(), customPropertyKey, previewCamera);
                    double customValue = track.getCustomValueAt(customPropertyKey, time, baselineValue);
                    previewCamera.applyCustomProperty(customPropertyKey, customValue);
                }
            }
        }

        if (hasCameraX || hasCameraY || hasCameraZoom) {
            previewCamera.setPosition(cameraX, cameraY);
            previewCamera.setZoom(cameraZoom);
        }
        if (hasDofFocus) previewCamera.setFocusDepth(dofFocus);
        if (hasDofStrength) previewCamera.setDepthOfFieldStrength(dofStrength);
        if (hasDofMaxBlur) previewCamera.setDepthOfFieldMaxBlur(dofMaxBlur);
        keyframeEditor.setCameraState(previewCamera.getX(), previewCamera.getY(), previewCamera.getZoom());

        Set<com.jvn.core.scene2d.Entity2D> previewAppliedEntities =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (EntityTrack track : project.getTracks()) {
            var entity = scene.find(track.getEntityName());
            if (entity == null) continue;
            boolean authoredTrack = trackHasAuthoredValues(track);
            if (!authoredTrack && previewAppliedEntities.contains(entity)) {
                continue;
            }
            if (!track.isVisible()) {
                entity.setVisible(false);
                previewAppliedEntities.add(entity);
                continue;
            }
            String entityName = track.getEntityName();
            Map<PropertyType, Double> baselineValues = new EnumMap<>(PropertyType.class);
            for (PropertyType property : PropertyType.values()) {
                if (property.isEntityProperty()) {
                    baselineValues.put(property, baselinePropertyValue(entityName, entity, property));
                }
            }
            AnimationProject.EffectiveEntityTransform effectiveTransform =
                project.computeEffectiveEntityTransform(entityName, time, baselineValues);

            double z = project.hasEffectiveAnimation(entityName, PropertyType.Z)
                ? effectiveTransform.z()
                : project.computeEffectiveLayerOrder(entityName);
            entity.setZ(z);

            double visualX = effectiveTransform.x();
            double visualY = effectiveTransform.y();
            if (track.hasKeyframes(PropertyType.PIVOT_X) || track.hasKeyframes(PropertyType.PIVOT_Y)) {
                double[] size = entityVisualSize(entity);
                if (!track.hasKeyframes(PropertyType.X)) {
                    double basePivotX = baselineValues.getOrDefault(PropertyType.PIVOT_X, entity.getOriginX());
                    visualX += (effectiveTransform.pivotX() - basePivotX) * size[0];
                }
                if (!track.hasKeyframes(PropertyType.Y)) {
                    double basePivotY = baselineValues.getOrDefault(PropertyType.PIVOT_Y, entity.getOriginY());
                    visualY += (effectiveTransform.pivotY() - basePivotY) * size[1];
                }
            }
            entity.setPosition(visualX, visualY);

            setEntityPivot(entity, effectiveTransform.pivotX(), effectiveTransform.pivotY());
            entity.setRotationDeg(effectiveTransform.rotationDeg());
            entity.setScale(effectiveTransform.scaleX(), effectiveTransform.scaleY());
            setEntityAlpha(entity, effectiveTransform.alpha());
            entity.setVisible(effectiveTransform.visibility() >= 0.5);

            double matrixMxx = project.hasEffectiveAnimation(entityName, PropertyType.MATRIX_MXX)
                ? project.computeValueAt(entityName, PropertyType.MATRIX_MXX, time, baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MXX))
                : baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MXX);
            double matrixMxy = project.hasEffectiveAnimation(entityName, PropertyType.MATRIX_MXY)
                ? project.computeValueAt(entityName, PropertyType.MATRIX_MXY, time, baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MXY))
                : baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MXY);
            double matrixMyx = project.hasEffectiveAnimation(entityName, PropertyType.MATRIX_MYX)
                ? project.computeValueAt(entityName, PropertyType.MATRIX_MYX, time, baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MYX))
                : baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MYX);
            double matrixMyy = project.hasEffectiveAnimation(entityName, PropertyType.MATRIX_MYY)
                ? project.computeValueAt(entityName, PropertyType.MATRIX_MYY, time, baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MYY))
                : baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MYY);
            double matrixTx = project.hasEffectiveAnimation(entityName, PropertyType.MATRIX_TX)
                ? project.computeValueAt(entityName, PropertyType.MATRIX_TX, time, baselinePropertyValue(entityName, entity, PropertyType.MATRIX_TX))
                : baselinePropertyValue(entityName, entity, PropertyType.MATRIX_TX);
            double matrixTy = project.hasEffectiveAnimation(entityName, PropertyType.MATRIX_TY)
                ? project.computeValueAt(entityName, PropertyType.MATRIX_TY, time, baselinePropertyValue(entityName, entity, PropertyType.MATRIX_TY))
                : baselinePropertyValue(entityName, entity, PropertyType.MATRIX_TY);
            // Compose parent-group supplemental matrices (outermost group applied last)
            {
                String grpCursor = project.getEntityParentGroupName(entityName);
                Set<String> grpVisited = new LinkedHashSet<>();
                while (grpCursor != null && grpVisited.add(grpCursor)) {
                    EntityGroup grp = project.getGroup(grpCursor);
                    if (grp == null) break;
                    EntityTrack grpTrack = grp.getGroupTrack();
                    boolean hasMatrix = grpTrack.hasKeyframes(PropertyType.MATRIX_MXX)
                        || grpTrack.hasKeyframes(PropertyType.MATRIX_MXY)
                        || grpTrack.hasKeyframes(PropertyType.MATRIX_MYX)
                        || grpTrack.hasKeyframes(PropertyType.MATRIX_MYY)
                        || grpTrack.hasKeyframes(PropertyType.MATRIX_TX)
                        || grpTrack.hasKeyframes(PropertyType.MATRIX_TY);
                    if (hasMatrix) {
                        double gMxx = grpTrack.hasKeyframes(PropertyType.MATRIX_MXX) ? grpTrack.getValueAt(PropertyType.MATRIX_MXX, time) : 1.0;
                        double gMxy = grpTrack.hasKeyframes(PropertyType.MATRIX_MXY) ? grpTrack.getValueAt(PropertyType.MATRIX_MXY, time) : 0.0;
                        double gMyx = grpTrack.hasKeyframes(PropertyType.MATRIX_MYX) ? grpTrack.getValueAt(PropertyType.MATRIX_MYX, time) : 0.0;
                        double gMyy = grpTrack.hasKeyframes(PropertyType.MATRIX_MYY) ? grpTrack.getValueAt(PropertyType.MATRIX_MYY, time) : 1.0;
                        double gTx  = grpTrack.hasKeyframes(PropertyType.MATRIX_TX)  ? grpTrack.getValueAt(PropertyType.MATRIX_TX,  time) : 0.0;
                        double gTy  = grpTrack.hasKeyframes(PropertyType.MATRIX_TY)  ? grpTrack.getValueAt(PropertyType.MATRIX_TY,  time) : 0.0;
                        // G * E  (group transform wraps entity transform)
                        double rMxx = gMxx * matrixMxx + gMxy * matrixMyx;
                        double rMxy = gMxx * matrixMxy + gMxy * matrixMyy;
                        double rMyx = gMyx * matrixMxx + gMyy * matrixMyx;
                        double rMyy = gMyx * matrixMxy + gMyy * matrixMyy;
                        double rTx  = gMxx * matrixTx  + gMxy * matrixTy  + gTx;
                        double rTy  = gMyx * matrixTx  + gMyy * matrixTy  + gTy;
                        matrixMxx = rMxx; matrixMxy = rMxy;
                        matrixMyx = rMyx; matrixMyy = rMyy;
                        matrixTx  = rTx;  matrixTy  = rTy;
                    }
                    grpCursor = grp.getParentGroupName();
                }
            }
            entity.setSupplementalTransform(matrixMxx, matrixMxy, matrixMyx, matrixMyy, matrixTx, matrixTy);

            double baseBlur = baselinePropertyValue(entityName, entity, PropertyType.BLUR);
            double blur = project.hasEffectiveAnimation(entityName, PropertyType.BLUR)
                ? project.computeValueAt(entityName, PropertyType.BLUR, time, baseBlur)
                : baseBlur;
            // Add blur contributions from parent groups
            {
                String grpCursor = project.getEntityParentGroupName(entityName);
                Set<String> grpVisited = new LinkedHashSet<>();
                while (grpCursor != null && grpVisited.add(grpCursor)) {
                    EntityGroup grp = project.getGroup(grpCursor);
                    if (grp == null) break;
                    EntityTrack grpTrack = grp.getGroupTrack();
                    if (grpTrack.hasKeyframes(PropertyType.BLUR)) {
                        blur += grpTrack.getValueAt(PropertyType.BLUR, time);
                    }
                    grpCursor = grp.getParentGroupName();
                }
            }
            entity.setBlurRadius(blur);

            double baseBrightness = baselinePropertyValue(entityName, entity, PropertyType.BRIGHTNESS);
            double brightness = project.hasEffectiveAnimation(entityName, PropertyType.BRIGHTNESS)
                ? project.computeValueAt(entityName, PropertyType.BRIGHTNESS, time, baseBrightness)
                : baseBrightness;
            entity.setBrightness(brightness);

            for (String customPropertyKey : track.getAnimatedCustomProperties()) {
                double baselineValue = baselineCustomPropertyValue(entityName, customPropertyKey, entity);
                double customValue = track.getCustomValueAt(customPropertyKey, time, baselineValue);
                entity.applyCustomProperty(customPropertyKey, customValue);
            }
            previewAppliedEntities.add(entity);
        }

        applyPreviewEventCuesUpTo(time);

        animationPreview.render();
        if (refreshEditorChrome) refreshSidebarTabs();
    }

    private boolean trackHasAuthoredValues(EntityTrack track) {
        if (track == null) return false;
        for (PropertyType property : PropertyType.values()) {
            if (track.hasKeyframes(property)) return true;
        }
        return track.getAnimatedCustomProperties().iterator().hasNext();
    }

    private void applyRuntimeParityPreview(double timeMs) {
        TimelineData data = project.toTimelineData(resolveTimelineNameForRuntimeData());
        TimelineRunner runner = new TimelineRunner(data, createPreviewSceneAccessor());
        runner.update(Math.max(0L, Math.round(timeMs)));
        var previewCamera = animationPreview.getCamera();
        keyframeEditor.setCameraState(previewCamera.getX(), previewCamera.getY(), previewCamera.getZoom());
    }

    private SceneAccessor createPreviewSceneAccessor() {
        return new SceneAccessor() {
            @Override
            public com.jvn.core.scene2d.Entity2D findEntity(String name) {
                return scene == null || name == null ? null : scene.find(name);
            }

            @Override
            public void setCameraX(double x) {
                var camera = animationPreview.getCamera();
                camera.setPosition(x, camera.getY());
            }

            @Override
            public void setCameraY(double y) {
                var camera = animationPreview.getCamera();
                camera.setPosition(camera.getX(), y);
            }

            @Override
            public void setCameraZoom(double zoom) {
                animationPreview.getCamera().setZoom(zoom);
            }

            @Override
            public void applyCustomProperty(String target, String propertyKey, double value) {
                if (TimelinePanel.isRuntimeCameraTarget(target)) {
                    animationPreview.getCamera().applyCustomProperty(propertyKey, value);
                    return;
                }
                SceneAccessor.super.applyCustomProperty(target, propertyKey, value);
            }

            @Override
            public void onEventCue(String type, Map<String, String> payload) {
                applyPreviewEventCue(type, payload);
            }
        };
    }

    private String resolveTimelineNameForRuntimeData() {
        String timelineName = tfTimelineName != null ? tfTimelineName.getText().trim() : "";
        if (timelineName.isBlank()) timelineName = project.getName();
        if (timelineName == null || timelineName.isBlank()) timelineName = "Untitled Animation";
        return timelineName;
    }

    private void captureSceneStateBaseline() {
        sceneBaselineImagePaths.clear();
        sceneBaselineVisibility.clear();
        sceneBaselineCustomProperties.clear();
        sceneBaselineCameraCustomProperties.clear();
        if (scene == null) return;
        for (String entityName : scene.names()) {
            if (entityName == null || entityName.isBlank()) continue;
            var entity = scene.find(entityName);
            if (entity == null) continue;
            sceneBaselineVisibility.put(entityName, entity.isVisible());
            sceneBaselineCustomProperties.put(entityName, captureEntityCustomPropertyBaseline(entity));
            if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                sceneBaselineImagePaths.put(entityName, sprite.getImagePath());
            }
        }
        sceneBaselineCameraCustomProperties.putAll(captureCameraCustomPropertyBaseline(animationPreview.getCamera()));
    }

    private void restorePreviewBaselineState() {
        if (scene == null) return;
        for (String entityName : scene.names()) {
            if (entityName == null || entityName.isBlank()) continue;
            var entity = scene.find(entityName);
            if (entity == null) continue;
            Boolean visible = sceneBaselineVisibility.get(entityName);
            if (visible != null) {
                entity.setVisible(visible);
            }
            restoreEntityCustomPropertyBaseline(entity, sceneBaselineCustomProperties.get(entityName));
            if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                String baselinePath = sceneBaselineImagePaths.get(entityName);
                if (baselinePath != null) {
                    sprite.setImagePath(baselinePath);
                }
            }
        }
        restoreCameraCustomPropertyBaseline(animationPreview.getCamera(), sceneBaselineCameraCustomProperties);
    }

    private void applyPreviewEventCuesUpTo(double timeMs) {
        for (EditorEventCue cue : project.getEditorEventCues()) {
            if (cue == null || cue.getType() == null || cue.getType().isBlank()) continue;
            if (cue.getTimeMs() > timeMs + 0.001) break;
            applyPreviewEventCue(cue);
        }
    }

    private void applyPreviewEventCue(EditorEventCue cue) {
        if (cue == null) return;
        applyPreviewEventCue(cue.getType(), cue.getPayloadView());
    }

    private void applyPreviewEventCue(String rawType, Map<String, String> payload) {
        if (scene == null || rawType == null) return;
        String type = rawType.trim().toLowerCase(Locale.ROOT);
        String target = payloadValue(payload, "target");
        com.jvn.core.scene2d.Entity2D entity = target == null || target.isBlank() ? null : scene.find(target);

        switch (type) {
            case "expression" -> {
                String expression = payloadValue(payload, "value");
                if (expression.isBlank()) expression = payloadValue(payload, "expression");
                if (applyLayeredExpressionCue(target, expression, payload)) {
                    break;
                }
                String directPath = firstNonBlank(payloadValue(payload, "path"), pathSpecFromLayerPayload(payload));
                String path = resolveCueAssetPath(target, expression, directPath, false);
                if (path != null && entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                    sprite.setImagePath(path);
                    entity.setVisible(true);
                }
            }
            case "show" -> {
                if (entity != null) entity.setVisible(true);
                String expression = payloadValue(payload, "expression");
                if (expression.isBlank()) expression = payloadValue(payload, "value");
                if (applyLayeredExpressionCue(target, expression, payload)) {
                    break;
                }
                String directPath = firstNonBlank(payloadValue(payload, "path"), pathSpecFromLayerPayload(payload));
                String path = resolveCueAssetPath(target, expression, directPath, false);
                if (path != null && entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                    sprite.setImagePath(path);
                }
            }
            case "hide" -> {
                if (entity != null) entity.setVisible(false);
            }
            case "replace" -> {
                String expression = payloadValue(payload, "expression");
                if (expression.isBlank()) expression = payloadValue(payload, "value");
                if (applyLayeredExpressionCue(target, expression, payload)) {
                    break;
                }
                String directPath = firstNonBlank(payloadValue(payload, "path"), pathSpecFromLayerPayload(payload));
                String path = resolveCueAssetPath(target, expression, directPath, false);
                if (path != null && entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                    sprite.setImagePath(path);
                    entity.setVisible(true);
                }
            }
            case "scene" -> {
                String resolvedPath = resolveCueAssetPath(target, payloadValue(payload, "id"), payloadValue(payload, "path"), true);
                com.jvn.core.scene2d.Entity2D background = entity;
                if (background == null) {
                    background = findBackgroundEntity();
                }
                if (resolvedPath != null && background instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                    sprite.setImagePath(resolvedPath);
                    background.setVisible(true);
                }
            }
            default -> {
            }
        }
    }

    private static String payloadValue(Map<String, String> payload, String key) {
        if (payload == null || key == null) return "";
        return payload.getOrDefault(key, "");
    }

    private com.jvn.core.scene2d.Entity2D findBackgroundEntity() {
        if (scene == null) return null;
        for (String entityName : scene.names()) {
            if (entityName == null || entityName.isBlank()) continue;
            if (!entityName.startsWith("bg_")) continue;
            return scene.find(entityName);
        }
        return null;
    }

    private String inferExpressionTargetFromSelection() {
        String selected = timelinePanel != null ? timelinePanel.getSelectedEntity() : "";
        String inferred = inferCharacterIdFromSelection(selected);
        if (inferred != null && !inferred.isBlank()) return inferred;
        if (launchSceneSnapshot != null && !launchSceneSnapshot.characters.isEmpty()) {
            PuppeteerLauncherPanel.CharacterEntry first = launchSceneSnapshot.characters.get(0);
            if (first != null && first.characterId != null && !first.characterId.isBlank()) {
                return first.characterId;
            }
        }
        return "";
    }

    private String inferCharacterIdFromSelection(String rawSelection) {
        String selection = rawSelection == null ? "" : rawSelection.trim();
        if (selection.isBlank()) return "";
        if (launchSceneSnapshot != null) {
            for (PuppeteerLauncherPanel.CharacterEntry character : launchSceneSnapshot.characters) {
                if (character == null || character.characterId == null || character.characterId.isBlank()) continue;
                String characterId = character.characterId.trim();
                String safeCharacter = selectorSafeName(characterId);
                String groupName = snapshotCharacterGroupName(character);
                if (selection.equals(characterId)
                    || selection.equals(groupName)
                    || selection.startsWith(groupName + "_")
                    || (!safeCharacter.isBlank() && selection.startsWith(safeCharacter + "_"))) {
                    return characterId;
                }
            }
        }
        for (String key : projectCharacterPresetLayers.keySet()) {
            int slash = key == null ? -1 : key.indexOf('/');
            if (slash <= 0) continue;
            String characterId = key.substring(0, slash);
            String safeCharacter = selectorSafeName(characterId);
            if (selection.equals(characterId)
                || (!safeCharacter.isBlank() && selection.startsWith(safeCharacter + "_"))) {
                return characterId;
            }
        }
        return selection;
    }

    private record ResolvedCharacterContext(com.jvn.core.vn.VnCharacter character, String characterId) {
        static final ResolvedCharacterContext EMPTY = new ResolvedCharacterContext(null, null);
    }

    private ResolvedCharacterContext resolveCharacterForDiagnostics() {
        File scriptFile = getSourceScriptFile();
        if (scriptFile == null) return ResolvedCharacterContext.EMPTY;
        String characterId = inferExpressionTargetFromSelection();
        if (characterId == null || characterId.isBlank()) return ResolvedCharacterContext.EMPTY;
        com.jvn.core.vn.VnCharacter character = PuppeteerCharacterRigResolver.resolve(scriptFile, characterId);
        if (character == null) return ResolvedCharacterContext.EMPTY;
        return new ResolvedCharacterContext(character, characterId);
    }

    private List<String> expressionTargetSuggestions() {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        String inferred = inferExpressionTargetFromSelection();
        if (inferred != null && !inferred.isBlank()) targets.add(inferred);
        if (launchSceneSnapshot != null) {
            for (PuppeteerLauncherPanel.CharacterEntry character : launchSceneSnapshot.characters) {
                if (character != null && character.characterId != null && !character.characterId.isBlank()) {
                    targets.add(character.characterId.trim());
                }
            }
        }
        for (String key : launchCharacterImagePaths.keySet()) {
            int slash = key == null ? -1 : key.indexOf('/');
            if (slash > 0) targets.add(key.substring(0, slash));
        }
        for (String key : launchCharacterPresetLayers.keySet()) {
            int slash = key == null ? -1 : key.indexOf('/');
            if (slash > 0) targets.add(key.substring(0, slash));
        }
        for (String key : projectCharacterPresetLayers.keySet()) {
            int slash = key == null ? -1 : key.indexOf('/');
            if (slash > 0) targets.add(key.substring(0, slash));
        }
        for (EntityTrack track : project.getTracks()) {
            if (track != null && track.getEntityName() != null && !track.getEntityName().isBlank()) {
                targets.add(inferCharacterIdFromSelection(track.getEntityName()));
            }
        }
        return List.copyOf(targets);
    }

    private List<String> expressionSuggestionsForTarget(String rawTarget) {
        String target = inferCharacterIdFromSelection(rawTarget);
        LinkedHashSet<String> expressions = new LinkedHashSet<>();
        if (launchSceneSnapshot != null) {
            for (PuppeteerLauncherPanel.CharacterEntry character : launchSceneSnapshot.characters) {
                if (character == null || !Objects.equals(character.characterId, target)) continue;
                if (character.expression != null && !character.expression.isBlank()) {
                    expressions.add(character.expression.trim());
                }
            }
        }
        String prefix = target == null || target.isBlank() ? "" : target + "/";
        for (String key : launchCharacterImagePaths.keySet()) {
            if (key != null && key.startsWith(prefix) && key.length() > prefix.length()) {
                expressions.add(key.substring(prefix.length()));
            }
        }
        for (String key : launchCharacterPresetLayers.keySet()) {
            if (key != null && key.startsWith(prefix) && key.length() > prefix.length()) {
                expressions.add(key.substring(prefix.length()));
            }
        }
        for (String key : projectCharacterPresetLayers.keySet()) {
            if (key != null && key.startsWith(prefix) && key.length() > prefix.length()) {
                expressions.add(key.substring(prefix.length()));
            }
        }
        if (expressions.isEmpty()) expressions.add("neutral");
        return List.copyOf(expressions);
    }

    private String preferredExpressionForTarget(String rawTarget) {
        String target = inferCharacterIdFromSelection(rawTarget);
        if (launchSceneSnapshot != null) {
            for (PuppeteerLauncherPanel.CharacterEntry character : launchSceneSnapshot.characters) {
                if (character == null || !Objects.equals(character.characterId, target)) continue;
                if (character.expression != null && !character.expression.isBlank()) return character.expression.trim();
            }
        }
        List<String> suggestions = expressionSuggestionsForTarget(target);
        return suggestions.isEmpty() ? "neutral" : suggestions.get(0);
    }

    private void enrichExpressionPayload(String type,
                                         Map<String, String> payload,
                                         String rawTarget,
                                         String expression,
                                         String directPath) {
        if (payload == null) return;
        String normalizedType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("expression", "show", "replace").contains(normalizedType)) return;
        String target = inferCharacterIdFromSelection(rawTarget);
        if (target.isBlank() || expression == null || expression.isBlank()) return;
        String path = directPath == null || directPath.isBlank()
            ? resolveRawCueAssetPath(target, expression)
            : directPath.trim();
        if (!path.isBlank()) {
            payload.putIfAbsent("path", path);
        }
        if (!payload.containsKey("layers")) {
            Map<String, String> pathPayload = path.isBlank() ? Map.of() : Map.of("path", path);
            String layerPayload = encodeLayerPayload(resolveExpressionLayerSpecs(target, expression, pathPayload));
            if (!layerPayload.isBlank()) {
                payload.put("layers", layerPayload);
            }
        }
    }

    private String resolveRawCueAssetPath(String rawTarget, String expressionOrId) {
        String target = inferCharacterIdFromSelection(rawTarget);
        String token = expressionOrId == null ? "" : expressionOrId.trim();
        if (target.isBlank() || token.isBlank()) return "";
        String mapped = launchCharacterImagePaths.get(target + "/" + token);
        return mapped == null ? "" : mapped.trim();
    }

    private boolean applyLayeredExpressionCue(String rawTarget, String expression, Map<String, String> payload) {
        if (scene == null) return false;
        String target = inferCharacterIdFromSelection(rawTarget);
        if (target.isBlank()) return false;
        List<ExpressionLayerSpec> layers = resolveExpressionLayerSpecs(target, expression, payload);
        if (layers.isEmpty()) return false;

        List<ExpressionLayerCandidate> candidates = expressionLayerCandidates(target);
        if (candidates.isEmpty()) return false;

        Set<String> used = new LinkedHashSet<>();
        ExpressionLayerCandidate template = candidates.get(0);
        int applied = 0;
        for (int i = 0; i < layers.size(); i++) {
            ExpressionLayerSpec layer = layers.get(i);
            if (layer == null || layer.path() == null || layer.path().isBlank()) continue;
            ExpressionLayerCandidate candidate = findExpressionLayerCandidate(candidates, layer.layerId(), used);
            if (candidate == null) {
                candidate = createExpressionLayerEntity(target, expression, layer, template, i);
                if (candidate == null) continue;
                candidates.add(candidate);
            }
            candidate.sprite().setImagePath(resolvePreviewAssetPath(layer.path()));
            candidate.sprite().setVisible(true);
            used.add(candidate.name());
            applied++;
        }
        for (ExpressionLayerCandidate candidate : candidates) {
            if (candidate != null && !used.contains(candidate.name())) {
                candidate.sprite().setVisible(false);
            }
        }
        return applied > 0;
    }

    void previewExpression(String rawTarget, String expression) {
        clearExpressionPreview();
        if (scene == null || rawTarget == null || rawTarget.isBlank()
            || expression == null || expression.isBlank()) return;
        List<ExpressionLayerCandidate> candidates = expressionLayerCandidates(rawTarget);
        if (candidates.isEmpty()) return;
        List<com.jvn.core.scene2d.Sprite2D> sprites = new ArrayList<>();
        for (ExpressionLayerCandidate candidate : candidates) {
            if (candidate != null && candidate.sprite() != null) sprites.add(candidate.sprite());
        }
        expressionPreviewSnapshot = expressionOverride.snapshot(sprites);
        boolean applied = applyLayeredExpressionCue(rawTarget, expression, Map.of());
        if (!applied) {
            expressionOverride.revert(expressionPreviewSnapshot);
            expressionPreviewSnapshot = List.of();
            return;
        }
        expressionPreviewTarget = rawTarget;
        expressionPreviewExpression = expression;
        animationPreview.render();
    }

    void clearExpressionPreview() {
        if (expressionPreviewSnapshot.isEmpty()) return;
        expressionOverride.revert(expressionPreviewSnapshot);
        expressionPreviewSnapshot = List.of();
        expressionPreviewTarget = null;
        expressionPreviewExpression = null;
        if (animationPreview != null) animationPreview.render();
    }

    boolean isExpressionPreviewActive() {
        return !expressionPreviewSnapshot.isEmpty();
    }

    String getExpressionPreviewEntityName() {
        return expressionPreviewTarget;
    }

    String getExpressionPreviewName() {
        return expressionPreviewExpression;
    }

    private List<ExpressionLayerSpec> resolveExpressionLayerSpecs(String rawTarget,
                                                                  String expression,
                                                                  Map<String, String> payload) {
        List<ExpressionLayerSpec> fromPayload = parseLayerPayload(payloadValue(payload, "layers"));
        if (!fromPayload.isEmpty()) return fromPayload;

        String target = inferCharacterIdFromSelection(rawTarget);
        String token = expression == null ? "" : expression.trim();
        if (!target.isBlank() && !token.isBlank()) {
            List<PuppeteerLauncherPanel.CharacterLayerEntry> mapped = launchCharacterPresetLayers.get(target + "/" + token);
            if (mapped != null && !mapped.isEmpty()) {
                List<ExpressionLayerSpec> specs = new ArrayList<>();
                for (PuppeteerLauncherPanel.CharacterLayerEntry layer : mapped) {
                    if (layer == null || layer.path == null || layer.path.isBlank()) continue;
                    specs.add(new ExpressionLayerSpec(layer.layerId, layer.path));
                }
                if (!specs.isEmpty()) return specs;
            }
            List<ExpressionLayerSpec> projectMapped = projectCharacterPresetLayers.get(target + "/" + token);
            if (projectMapped != null && !projectMapped.isEmpty()) {
                return List.copyOf(projectMapped);
            }
        }

        String path = firstNonBlank(payloadValue(payload, "path"), resolveRawCueAssetPath(target, token));
        if (path == null || path.isBlank() || path.indexOf('|') < 0) return List.of();
        List<ExpressionLayerSpec> specs = new ArrayList<>();
        int index = 1;
        for (String part : path.split("\\|")) {
            String layerPath = part == null ? "" : part.trim();
            if (layerPath.isBlank()) continue;
            specs.add(new ExpressionLayerSpec("layer" + index++, layerPath));
        }
        return List.copyOf(specs);
    }

    private List<ExpressionLayerSpec> parseLayerPayload(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<ExpressionLayerSpec> specs = new ArrayList<>();
        int index = 1;
        for (String token : raw.split("\\|")) {
            String part = token == null ? "" : token.trim();
            if (part.isBlank()) continue;
            int sep = part.indexOf('=');
            String layerId = sep > 0 ? part.substring(0, sep).trim() : "layer" + index;
            String path = sep > 0 && sep < part.length() - 1 ? part.substring(sep + 1).trim() : part;
            if (!path.isBlank()) specs.add(new ExpressionLayerSpec(layerId, path));
            index++;
        }
        return List.copyOf(specs);
    }

    private String encodeLayerPayload(List<ExpressionLayerSpec> layers) {
        if (layers == null || layers.isEmpty()) return "";
        StringBuilder encoded = new StringBuilder();
        int index = 1;
        for (ExpressionLayerSpec layer : layers) {
            if (layer == null || layer.path() == null || layer.path().isBlank()) continue;
            if (!encoded.isEmpty()) encoded.append(" | ");
            String layerId = layer.layerId() == null || layer.layerId().isBlank()
                ? "layer" + index
                : layer.layerId().trim();
            encoded.append(layerId).append('=').append(layer.path().trim());
            index++;
        }
        return encoded.toString();
    }

    private String pathSpecFromLayerPayload(Map<String, String> payload) {
        return pathSpecFromLayers(parseLayerPayload(payloadValue(payload, "layers")));
    }

    private String pathSpecFromLayers(List<ExpressionLayerSpec> layers) {
        if (layers == null || layers.isEmpty()) return "";
        StringBuilder pathSpec = new StringBuilder();
        for (ExpressionLayerSpec layer : layers) {
            if (layer == null || layer.path() == null || layer.path().isBlank()) continue;
            if (!pathSpec.isEmpty()) pathSpec.append(" | ");
            pathSpec.append(layer.path().trim());
        }
        return pathSpec.toString();
    }

    private List<ExpressionLayerCandidate> expressionLayerCandidates(String rawTarget) {
        if (scene == null) return List.of();
        String target = inferCharacterIdFromSelection(rawTarget);
        String safeTarget = selectorSafeName(target);
        if (safeTarget.isBlank()) return List.of();
        List<ExpressionLayerCandidate> candidates = new ArrayList<>();
        for (String name : scene.names()) {
            if (name == null || name.isBlank() || name.equals(target)) continue;
            if (!name.startsWith(safeTarget + "_")) continue;
            com.jvn.core.scene2d.Entity2D entity = scene.find(name);
            if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                candidates.add(new ExpressionLayerCandidate(name, sprite));
            }
        }
        candidates.sort(java.util.Comparator
            .comparingDouble((ExpressionLayerCandidate candidate) -> candidate.sprite().getZ())
            .thenComparing(ExpressionLayerCandidate::name));
        return candidates;
    }

    private ExpressionLayerCandidate findExpressionLayerCandidate(List<ExpressionLayerCandidate> candidates,
                                                                  String layerId,
                                                                  Set<String> used) {
        if (candidates == null || candidates.isEmpty()) return null;
        String safeLayer = selectorSafeName(layerId);
        if (!safeLayer.isBlank()) {
            for (ExpressionLayerCandidate candidate : candidates) {
                if (candidate == null || used.contains(candidate.name())) continue;
                if (candidate.name().endsWith("_" + safeLayer)) return candidate;
            }
            for (ExpressionLayerCandidate candidate : candidates) {
                if (candidate == null || used.contains(candidate.name())) continue;
                if (candidate.name().contains("_" + safeLayer + "_")) return candidate;
            }
        }
        for (ExpressionLayerCandidate candidate : candidates) {
            if (candidate != null && !used.contains(candidate.name())) return candidate;
        }
        return null;
    }

    private ExpressionLayerCandidate createExpressionLayerEntity(String target,
                                                                 String expression,
                                                                 ExpressionLayerSpec layer,
                                                                 ExpressionLayerCandidate template,
                                                                 int index) {
        if (scene == null || template == null || template.sprite() == null || layer == null) return null;
        com.jvn.core.scene2d.Sprite2D source = template.sprite();
        String baseName = selectorSafeName(target) + "_"
            + selectorSafeName(expression == null || expression.isBlank() ? "expression" : expression)
            + "_" + selectorSafeName(layer.layerId() == null || layer.layerId().isBlank() ? "layer" + (index + 1) : layer.layerId());
        if (baseName.isBlank()) baseName = "expression_layer";
        String entityName = baseName;
        int suffix = 2;
        while (scene.find(entityName) != null) {
            entityName = baseName + "_" + suffix++;
        }
        com.jvn.core.scene2d.Sprite2D sprite = new com.jvn.core.scene2d.Sprite2D(
            resolvePreviewAssetPath(layer.path()),
            source.getWidth(),
            source.getHeight());
        sprite.setOrigin(source.getOriginX(), source.getOriginY());
        sprite.setPosition(source.getX(), source.getY());
        sprite.setScale(source.getScaleX(), source.getScaleY());
        sprite.setRotationDeg(source.getRotationDeg());
        sprite.setZ(source.getZ() + 0.01 * (index + 1));
        sprite.setAlpha(source.getAlpha());
        sprite.setVisible(false);
        scene.add(sprite);
        scene.registerEntity(entityName, sprite);
        sceneBaselineVisibility.put(entityName, false);
        sceneBaselineImagePaths.put(entityName, sprite.getImagePath());
        sceneBaselineCustomProperties.put(entityName, captureEntityCustomPropertyBaseline(sprite));
        return new ExpressionLayerCandidate(entityName, sprite);
    }

    private String resolveCueAssetPath(String target, String expressionOrId, String directPath, boolean backgroundCue) {
        if (directPath != null && !directPath.isBlank()) {
            return resolvePreviewAssetPathSpec(directPath.trim());
        }
        String token = expressionOrId == null ? "" : expressionOrId.trim();
        if (token.isBlank()) return null;
        if (backgroundCue) {
            String mapped = launchBackgroundPaths.get(token);
            return mapped == null || mapped.isBlank() ? null : resolvePreviewAssetPathSpec(mapped);
        }
        target = inferCharacterIdFromSelection(target);
        if (target == null || target.isBlank()) return null;
        String mapped = launchCharacterImagePaths.get(target + "/" + token);
        if ((mapped == null || mapped.isBlank()) && !"neutral".equals(token)) {
            mapped = launchCharacterImagePaths.get(target + "/neutral");
        }
        return mapped == null || mapped.isBlank() ? null : resolvePreviewAssetPathSpec(mapped);
    }

    private String resolvePreviewAssetPathSpec(String pathSpec) {
        if (pathSpec == null || pathSpec.isBlank()) return null;
        if (pathSpec.indexOf('|') < 0) {
            return resolvePreviewAssetPath(pathSpec.trim());
        }
        StringBuilder out = new StringBuilder();
        for (String token : pathSpec.split("\\|")) {
            String part = token == null ? "" : token.trim();
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(" | ");
            out.append(resolvePreviewAssetPath(part));
        }
        return out.isEmpty() ? null : out.toString();
    }

    private String resolvePreviewAssetPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return rawPath;
        File file = new File(rawPath);
        if (!file.isAbsolute() && projectRoot != null) {
            file = new File(projectRoot, rawPath);
        }
        return file.exists() ? file.getAbsolutePath() : rawPath;
    }

    private void setEntityAlpha(com.jvn.core.scene2d.Entity2D entity, double alpha) {
        if (entity instanceof com.jvn.core.scene2d.Sprite2D s) s.setAlpha(alpha);
        else if (entity instanceof com.jvn.core.scene2d.SpriteAnimation2D a) a.setAlpha(alpha);
        else if (entity instanceof com.jvn.core.scene2d.Label2D l) 
            l.setColor(l.getColorR(), l.getColorG(), l.getColorB(), alpha);
        else if (entity instanceof com.jvn.core.scene2d.Panel2D p)
            p.setFill(p.getFillR(), p.getFillG(), p.getFillB(), alpha);
    }

    private double baselinePropertyValue(com.jvn.core.scene2d.Entity2D entity, PropertyType property) {
        return baselinePropertyValue(null, entity, property);
    }

    private double baselinePropertyValue(String entityName, com.jvn.core.scene2d.Entity2D entity, PropertyType property) {
        Double initialValue = entityName != null ? project.getInitialSnapshotValue(entityName, property) : null;
        if (initialValue != null && Double.isFinite(initialValue)) {
            return initialValue;
        }
        return fallbackPropertyValue(entity, property);
    }

    private static double[] entityVisualSize(com.jvn.core.scene2d.Entity2D entity) {
        if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
            return new double[] { Math.max(1.0, sprite.getWidth()), Math.max(1.0, sprite.getHeight()) };
        }
        if (entity instanceof com.jvn.core.scene2d.Panel2D panel) {
            return new double[] { Math.max(1.0, panel.getWidth()), Math.max(1.0, panel.getHeight()) };
        }
        return new double[] { 1.0, 1.0 };
    }

    private static double getEntityPivotX(com.jvn.core.scene2d.Entity2D entity) {
        return entity.getOriginX();
    }

    private static double getEntityPivotY(com.jvn.core.scene2d.Entity2D entity) {
        return entity.getOriginY();
    }

    private static void setEntityPivot(com.jvn.core.scene2d.Entity2D entity, double pivotX, double pivotY) {
        entity.setOrigin(clampPivot(pivotX), clampPivot(pivotY));
    }

    private void upsertGroupPivotPreservingPose(
        String groupName,
        EntityTrack track,
        double timeMs,
        double pivotX,
        double pivotY
    ) {
        GroupPivotAdjustment adjustment = computeGroupPivotAdjustment(groupName, track, timeMs, pivotX, pivotY);
        track.upsertKeyframe(PropertyType.PIVOT_X, new Keyframe(timeMs, pivotX));
        track.upsertKeyframe(PropertyType.PIVOT_Y, new Keyframe(timeMs, pivotY));
        if (adjustment != null && adjustment.translate()) {
            track.upsertKeyframe(PropertyType.X, new Keyframe(timeMs, adjustment.x()));
            track.upsertKeyframe(PropertyType.Y, new Keyframe(timeMs, adjustment.y()));
        }
    }

    private List<PuppeteerCommand> groupPivotPreservingCommands(
        String groupName,
        EntityTrack track,
        double timeMs,
        double pivotX,
        double pivotY
    ) {
        List<PuppeteerCommand> commands = new ArrayList<>();
        GroupPivotAdjustment adjustment = computeGroupPivotAdjustment(groupName, track, timeMs, pivotX, pivotY);
        commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_X, timeMs, pivotX));
        commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_Y, timeMs, pivotY));
        if (adjustment != null && adjustment.translate()) {
            commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.X, timeMs, adjustment.x()));
            commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.Y, timeMs, adjustment.y()));
        }
        return commands;
    }

    private GroupPivotAdjustment computeGroupPivotAdjustment(
        String groupName,
        EntityTrack track,
        double timeMs,
        double newPivotX,
        double newPivotY
    ) {
        if (groupName == null || groupName.isBlank() || track == null || project.getGroup(groupName) == null) {
            return null;
        }
        double oldPivotX = trackValueAt(track, PropertyType.PIVOT_X, timeMs);
        double oldPivotY = trackValueAt(track, PropertyType.PIVOT_Y, timeMs);
        double[] oldPivot = project.computeGroupLocalPivot(groupName, oldPivotX, oldPivotY);
        double[] newPivot = project.computeGroupLocalPivot(groupName, newPivotX, newPivotY);
        if (oldPivot == null || oldPivot.length < 2 || newPivot == null || newPivot.length < 2) {
            return null;
        }
        double dx = newPivot[0] - oldPivot[0];
        double dy = newPivot[1] - oldPivot[1];
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) return null;

        double scaleX = trackValueAt(track, PropertyType.SCALE_X, timeMs);
        if (track.hasKeyframes(PropertyType.MIRROR_X)) {
            scaleX *= mirrorFactor(track.getValueAt(PropertyType.MIRROR_X, timeMs));
        }
        double scaleY = trackValueAt(track, PropertyType.SCALE_Y, timeMs);
        double radians = Math.toRadians(trackValueAt(track, PropertyType.ROTATION, timeMs));
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double transformedDx = dx * scaleX * cos - dy * scaleY * sin;
        double transformedDy = dx * scaleX * sin + dy * scaleY * cos;
        double compensationX = transformedDx - dx;
        double compensationY = transformedDy - dy;
        if (!Double.isFinite(compensationX) || !Double.isFinite(compensationY)) return null;

        boolean translate = Math.abs(compensationX) > 1e-6 || Math.abs(compensationY) > 1e-6;
        double currentX = trackValueAt(track, PropertyType.X, timeMs);
        double currentY = trackValueAt(track, PropertyType.Y, timeMs);
        return new GroupPivotAdjustment(currentX + compensationX, currentY + compensationY, translate);
    }

    private static double trackValueAt(EntityTrack track, PropertyType property, double timeMs) {
        if (track == null || property == null) return 0.0;
        return track.hasKeyframes(property)
            ? track.getValueAt(property, timeMs)
            : property.getDefaultValue();
    }

    private static double mirrorFactor(double mirrorX) {
        if (!Double.isFinite(mirrorX)) return 1.0;
        return Math.cos(clampPivot(mirrorX) * Math.PI);
    }

    /**
     * Computes the world-space position delta needed to keep an entity visually
     * stationary when its pivot changes from (oldPX, oldPY) to (newPX, newPY).
     * Returns [deltaX, deltaY] to add to the entity's current position.
     */
    private static double[] pivotCompensation(com.jvn.core.scene2d.Entity2D entity,
                                               double newPX, double newPY,
                                               double oldPX, double oldPY) {
        if (entity == null) return new double[]{0.0, 0.0};
        double w = 1.0, h = 1.0;
        if (entity instanceof com.jvn.core.scene2d.Sprite2D sp) {
            w = Math.max(1.0, sp.getWidth());
            h = Math.max(1.0, sp.getHeight());
        }
        double scaleX  = entity.getScaleX();
        double scaleY  = entity.getScaleY();
        double rotRad  = Math.toRadians(entity.getRotationDeg());
        double cos     = Math.cos(rotRad);
        double sin     = Math.sin(rotRad);
        double dx      = (newPX - oldPX) * w * scaleX;
        double dy      = (newPY - oldPY) * h * scaleY;
        return new double[]{dx * cos - dy * sin, dx * sin + dy * cos};
    }

    private static double clampPivot(double value) {
        if (!Double.isFinite(value)) return 0.5;
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    void refreshTransportButtonStates() {
        refreshTransportButtonState(btnPlay, btnPause);
    }

    private void refreshTransportButtonState(Button playButton, Button pauseButton) {
        if (playButton == null || pauseButton == null) return;
        if (project.isPlaying()) {
            playButton.setStyle(STYLE_BTN_DARK + "-fx-opacity: 0.5;");
            playButton.setDisable(true);
            pauseButton.setStyle(STYLE_BTN_ACCENT);
            pauseButton.setDisable(false);
        } else {
            playButton.setStyle(STYLE_BTN_ACCENT);
            playButton.setDisable(false);
            pauseButton.setStyle(STYLE_BTN_DARK + "-fx-opacity: 0.5;");
            pauseButton.setDisable(true);
        }
    }

    public boolean isPreviewFullscreenActive() {
        return previewFocusMode;
    }

    public void togglePreviewFocusMode() {
        if (isPreviewFullscreenActive()) exitFullscreenPreview();
        else enterFullscreenPreview();
    }

    private void enterFullscreenPreview() {
        if (scene == null || isPreviewFullscreenActive()) return;
        rememberWorkspaceDividerPositions();
        previewFocusReturnActiveItems.clear();

        previewFocusPreviewReturnSlot = findDockSlotContaining(previewPane);
        previewFocusTimelineReturnSlot = findDockSlotContaining(timelinePanel);
        previewFocusPreviewItem = previewFocusPreviewReturnSlot == null
            ? dockItems.get("preview")
            : previewFocusPreviewReturnSlot.itemForNode(previewPane);
        previewFocusTimelineItem = previewFocusTimelineReturnSlot == null
            ? dockItems.get("timeline-panel")
            : previewFocusTimelineReturnSlot.itemForNode(timelinePanel);
        rememberPreviewFocusActiveItem(previewFocusPreviewReturnSlot);
        rememberPreviewFocusActiveItem(previewFocusTimelineReturnSlot);
        releasePreviewFocusNode(previewFocusPreviewReturnSlot, previewFocusPreviewItem, previewPane);
        releasePreviewFocusNode(previewFocusTimelineReturnSlot, previewFocusTimelineItem, timelinePanel);
        previewPane.setManaged(true);
        previewPane.setVisible(true);
        timelinePanel.setManaged(true);
        timelinePanel.setVisible(true);
        previewFocusSplit.getItems().setAll(previewPane, timelinePanel);
        SplitPane.setResizableWithParent(previewPane, Boolean.TRUE);
        SplitPane.setResizableWithParent(timelinePanel, Boolean.TRUE);

        previewFocusMode = true;
        updatePreviewWorkspaceModeVisibility();
        updatePreviewOverlayVisibility();
        refreshSidebarTabs();
        Platform.runLater(() -> {
            previewFocusSplit.setDividerPositions(previewFocusDividerPosition);
            animationPreview.requestFocus();
            updatePreview();
        });
    }

    void exitFullscreenPreview() {
        if (!previewFocusMode) return;
        previewFocusDividerPosition = readDividerPosition(previewFocusSplit, previewFocusDividerPosition);

        previewFocusSplit.getItems().remove(previewPane);
        previewFocusSplit.getItems().remove(timelinePanel);

        restorePreviewFocusDockItem(previewFocusPreviewReturnSlot, previewFocusPreviewItem);
        restorePreviewFocusDockItem(previewFocusTimelineReturnSlot, previewFocusTimelineItem);
        restorePreviewFocusActiveItems();

        previewFocusMode = false;
        updatePreviewWorkspaceModeVisibility();
        previewFocusPreviewReturnSlot = null;
        previewFocusTimelineReturnSlot = null;
        previewFocusPreviewItem = null;
        previewFocusTimelineItem = null;
        previewFocusReturnActiveItems.clear();

        updatePreviewOverlayVisibility();
        refreshSidebarTabs();

        Platform.runLater(() -> {
            restoreWorkspaceDividerPositions();
            updatePreview();
            Platform.runLater(this::restoreWorkspaceDividerPositions);
        });
    }

    private void rememberWorkspaceDividerPositions() {
        topWorkspaceDividerPosition = readDividerPosition(topWorkspaceSplit, topWorkspaceDividerPosition);
        bottomWorkspaceDividerPosition = readDividerPosition(bottomWorkspaceSplit, bottomWorkspaceDividerPosition);
        workspaceContentDividerPosition = readDividerPosition(workspaceContentSplit, workspaceContentDividerPosition);
        codePaneDividerPosition = readDividerPosition(mainWorkspaceSplit, codePaneDividerPosition);
    }

    private void restoreWorkspaceDividerPositions() {
        if (topWorkspaceSplit != null && !topWorkspaceSplit.getDividers().isEmpty()) {
            topWorkspaceSplit.setDividerPositions(topWorkspaceDividerPosition);
        }
        if (bottomWorkspaceSplit != null && !bottomWorkspaceSplit.getDividers().isEmpty()) {
            bottomWorkspaceSplit.setDividerPositions(bottomWorkspaceDividerPosition);
        }
        if (workspaceContentSplit != null && !workspaceContentSplit.getDividers().isEmpty()) {
            workspaceContentSplit.setDividerPositions(workspaceContentDividerPosition);
        }
        if (mainWorkspaceSplit != null && !mainWorkspaceSplit.getDividers().isEmpty()) {
            mainWorkspaceSplit.setDividerPositions(codePaneDividerPosition);
        }
    }

    private void rememberPreviewFocusActiveItem(DockSlot slot) {
        if (slot == null || slot.item() == null) return;
        previewFocusReturnActiveItems.putIfAbsent(slot, slot.item());
    }

    private void releasePreviewFocusNode(DockSlot returnSlot, DockItem returnItem, Node fallbackNode) {
        if (returnSlot != null && returnItem != null && returnSlot.containsItem(returnItem)) {
            returnSlot.releaseItemForPreviewFocus(returnItem);
        } else {
            detachNode(fallbackNode);
        }
    }

    private void restorePreviewFocusDockItem(DockSlot returnSlot, DockItem returnItem) {
        if (returnItem == null || returnItem.contentNode() == null) return;
        DockSlot targetSlot = returnSlot;
        if (targetSlot == null || !dockSlots.containsValue(targetSlot)) {
            targetSlot = dockSlots.get(returnItem.defaultSlotId());
        }
        returnItem.contentNode().setManaged(true);
        returnItem.contentNode().setVisible(true);
        if (targetSlot != null) {
            if (targetSlot.containsItem(returnItem)) {
                targetSlot.activateItem(returnItem);
            } else {
                targetSlot.addItem(returnItem);
            }
            return;
        }
        SplitPane group = firstAvailableDockGroup("workspace-top", "workspace-main");
        if (group != null) {
            createDynamicDockSlot(null, returnItem, group, group.getItems().size());
        }
    }

    private void restorePreviewFocusActiveItems() {
        for (Map.Entry<DockSlot, DockItem> entry : previewFocusReturnActiveItems.entrySet()) {
            DockSlot slot = entry.getKey();
            DockItem activeItem = entry.getValue();
            if (slot != null && activeItem != null && slot.containsItem(activeItem)) {
                slot.activateItem(activeItem);
            }
        }
    }

    private void updatePreviewOverlayVisibility() {
        if (btnPreviewFullscreen == null || previewViewportHost == null) return;
        boolean fullscreen = isPreviewFullscreenActive();
        boolean hasScene = scene != null;
        boolean hovering = previewViewportHost.isHover();

        // Focus button: always visible when scene loaded and not in fullscreen;
        // swap between idle (subtle) and hover (prominent) style classes
        btnPreviewFullscreen.setVisible(hasScene && !fullscreen);
        btnPreviewFullscreen.getStyleClass().removeAll(
            "puppeteer-preview-overlay-button-idle", "puppeteer-preview-overlay-button-hover");
        btnPreviewFullscreen.getStyleClass().add(
            hovering ? "puppeteer-preview-overlay-button-hover" : "puppeteer-preview-overlay-button-idle");

        // Back button: always visible during fullscreen
        if (btnPreviewBack != null) {
            btnPreviewBack.setVisible(fullscreen);
        }
    }

    private void updatePreviewWorkspaceModeVisibility() {
        if (mainWorkspaceSplit == null || previewFocusSplit == null) return;
        mainWorkspaceSplit.setManaged(!previewFocusMode);
        mainWorkspaceSplit.setVisible(!previewFocusMode);
        previewFocusSplit.setManaged(previewFocusMode);
        previewFocusSplit.setVisible(previewFocusMode);
    }

    private static double readDividerPosition(SplitPane splitPane, double fallback) {
        if (splitPane == null || splitPane.getDividers().isEmpty()) return fallback;
        return splitPane.getDividerPositions()[0];
    }

    private static double clampDouble(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        double safeMax = Math.max(min, max);
        return Math.max(min, Math.min(safeMax, value));
    }

    private static void attachToSplitPane(SplitPane splitPane, Node node, int index) {
        if (splitPane == null || node == null || splitPane.getItems().contains(node)) return;
        int safeIndex = Math.max(0, Math.min(index, splitPane.getItems().size()));
        splitPane.getItems().add(safeIndex, node);
        SplitPane.setResizableWithParent(node, Boolean.TRUE);
    }

    private static void detachNode(Node node) {
        if (node == null) return;
        Parent parent = node.getParent();
        if (parent instanceof SplitPane splitPane) {
            splitPane.getItems().remove(node);
            return;
        }
        if (parent instanceof BorderPane borderPane) {
            if (borderPane.getTop() == node) borderPane.setTop(null);
            else if (borderPane.getBottom() == node) borderPane.setBottom(null);
            else if (borderPane.getLeft() == node) borderPane.setLeft(null);
            else if (borderPane.getRight() == node) borderPane.setRight(null);
            else if (borderPane.getCenter() == node) borderPane.setCenter(null);
            return;
        }
        if (parent instanceof Pane pane) {
            pane.getChildren().remove(node);
        }
    }

    private void setupKeyboardShortcuts(Scene scene) {
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.SPACE),
            () -> { if (project.isPlaying()) pause(); else play(); }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.HOME),
            this::rewind
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.ESCAPE),
            () -> {
                if (isPreviewFullscreenActive()) {
                    exitFullscreenPreview();
                }
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.PAGE_UP),
            () -> timelinePanel.jumpPlayheadToPreviousKeyframe()
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.PAGE_DOWN),
            () -> timelinePanel.jumpPlayheadToNextKeyframe()
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            () -> timelinePanel.zoomToSelection()
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.K),
            () -> {
                timelinePanel.addKeyframeAtPlayhead();
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.DELETE),
            () -> {
                timelinePanel.deleteSelectedKeyframe();
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
            this::copyExportedCodeToClipboard
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            this::copySelectedKeyframesToClipboard
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            this::pasteCopiedKeyframesAtPlayhead
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            this::duplicateSelectedKeyframesBySnapStep
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN),
            this::executeUndo
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
            this::executeRedo
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN),
            this::executeRedo
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN),
            () -> animationPreview.setOnionSkinning(!animationPreview.isOnionSkinning())
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN),
            () -> {
                timelinePanel.nudgeSelectedKeyframes(-timelinePanel.getSnapStepMs());
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN),
            () -> {
                timelinePanel.nudgeSelectedKeyframes(timelinePanel.getSnapStepMs());
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                timelinePanel.nudgeSelectedKeyframes(-1.0);
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                timelinePanel.nudgeSelectedKeyframes(1.0);
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.R, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                if (timelinePanel.reverseSelectedKeyframes()) {
                    refreshExportPreviewAndMarkDirty();
                }
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.E, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                if (timelinePanel.distributeSelectedKeyframes()) {
                    refreshExportPreviewAndMarkDirty();
                }
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.A),
            () -> {
                if (cbOrbitTool == null) return;
                cbOrbitTool.setSelected(!cbOrbitTool.isSelected());
                animationPreview.setOrbitToolEnabled(cbOrbitTool.isSelected());
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.A, KeyCombination.SHIFT_DOWN),
            () -> {
                animationPreview.clearOrbitAnchorForSelectedEntity();
                updatePreview();
            }
        );
    }

    public void copySelectedKeyframesToClipboard() {
        timelinePanel.copySelectedKeyframes();
        // Save to clipboard history
        List<com.jvn.editor.ui.actioneditor.TimelinePanel.ClipboardEntry> current = timelinePanel.getCopiedKeyframes();
        if (!current.isEmpty()) {
            clipboardHistory.add(0, List.copyOf(current));
            if (clipboardHistory.size() > MAX_CLIPBOARD_HISTORY) {
                clipboardHistory.remove(clipboardHistory.size() - 1);
            }
        }
        refreshToolbarCommandSummary();
    }

    public void copyExportedCodeToClipboard() {
        showCodeGenerationActionPreview(
            "Copy Generated Timeline Code?",
            "Puppeteer will generate JES code from the current visual timeline and copy it to the clipboard.",
            "Copy Code",
            List.of(
                "Generate a standard JES timeline block from the current visual model.",
                "Run export/runtime diagnostics before copying.",
                "Copy the generated text to the system clipboard.",
                "Leave the project file, registered timeline file, and TimelineRegistry unchanged.",
                "This action does not include named Puppeteer reopen metadata; Save & Register writes the named metadata-rich .jes file."
            ),
            this::performCopyExportedCodeToClipboard
        );
    }

    private void performCopyExportedCodeToClipboard() {
        try {
            String code = CodeExporter.export(project);
            ResolvedCharacterContext rigContext = resolveCharacterForDiagnostics();
            List<TimelineDiagnostic.Message> findings = new ArrayList<>(
                PuppeteerVerification.diagnose(
                    project,
                    knownSceneEntities(),
                    projectRoot,
                    PuppeteerVerification.Mode.EXPORT_CODE,
                    rigContext.character(),
                    rigContext.characterId()
                )
            );
            findings.addAll(TimelineDiagnostic.diagnoseDsl(code));
            boolean hasErrors = findings.stream()
                .anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.ERROR);
            if (hasErrors) {
                showVerificationOverlay(
                    "Export Blocked",
                    "Puppeteer found export errors. Fix them before copying this timeline code.",
                    findings,
                    null,
                    null
                );
                return;
            }
            copyToClipboard(code);
            if (statusBar != null) statusBar.setText("Exported timeline code copied to clipboard");
            if (onCopyCode != null) onCopyCode.accept(code);
        } catch (Exception ex) {
            showOverlayError(
                PuppeteerErrorType.EXPORT,
                "Export Failed",
                "Could not generate timeline code.",
                ex.getMessage(),
                ex
            );
        }
    }

    public void requestRefreshGeneratedCode() {
        showCodeGenerationActionPreview(
            "Regenerate Timeline Code?",
            "Puppeteer will replace the right-side Timeline Code text with freshly generated code from the visual model.",
            "Regenerate",
            List.of(
                "Discard manual edits currently present in the Timeline Code panel.",
                "Generate " + (compactExport ? "compact" : "standard") + " JES timeline code from the current visual model.",
                "Refresh parse and timeline diagnostics for the generated code.",
                "Leave saved files and TimelineRegistry unchanged.",
                "Use Preview Parse and Commit if you want hand-edited code to become the visual model instead."
            ),
            this::refreshExportPreview
        );
    }

    public void pasteCopiedKeyframesAtPlayhead() {
        timelinePanel.pasteCopiedKeyframesAtPlayhead();
        refreshToolbarCommandSummary();
    }

    public void duplicateSelectedKeyframesBySnapStep() {
        double delta = Math.max(1.0, timelinePanel.getSnapStepMs());
        timelinePanel.duplicateSelectedKeyframes(delta);
        refreshToolbarCommandSummary();
    }

    public void executeUndo() {
        if (!commandStack.canUndo()) return;
        commandStack.undo();
        timelinePanel.refresh();
        updatePreview();
        refreshExportPreviewAndMarkDirty();
        refreshUndoRedoControls();
    }

    public void executeRedo() {
        if (!commandStack.canRedo()) return;
        commandStack.redo();
        timelinePanel.refresh();
        updatePreview();
        refreshExportPreviewAndMarkDirty();
        refreshUndoRedoControls();
    }

    public void showPresetMenuOverlay() {
        VBox content = new VBox(10);
        String lastCategory = "";
        for (AnimationPreset preset : AnimationPreset.ALL) {
            if (!preset.getCategory().equals(lastCategory)) {
                lastCategory = preset.getCategory();
                Label header = new Label(lastCategory);
                header.setStyle("-fx-text-fill: #8ea4c6; -fx-font-size: 10px; -fx-font-weight: bold;");
                content.getChildren().add(header);
            }
            content.getChildren().add(buildOverlayMenuButton(
                preset.getName(),
                () -> applyPreset(preset)));
        }
        overlayDialog.showDialog(
            "Animation Presets",
            "Apply a preset to the selected entity at the current playhead.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Close", overlayDialog::hideOverlay).defaultFocus(true)
        );
    }

    public void showSlotMenuOverlay() {
        VBox content = new VBox(8);
        for (VnSlotHelper.Slot slot : VnSlotHelper.Slot.values()) {
            content.getChildren().add(buildOverlayMenuButton(
                slot.name().replace('_', ' '),
                () -> placeEntityAtSlot(slot)));
        }
        overlayDialog.showDialog(
            "VN Slots",
            "Place the selected entity on a standard visual-novel staging slot.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Close", overlayDialog::hideOverlay).defaultFocus(true)
        );
    }

    public void showEyeFocusOverlay() {
        String selected = timelinePanel != null ? timelinePanel.getSelectedEntity() : "";
        String[] inferred = inferEyeFocusSelection(selected);
        TextField characterField = new TextField(inferred[0].isBlank() ? "john" : inferred[0]);
        TextField expressionField = new TextField(inferred[1].isBlank() ? "neutral" : inferred[1]);
        VnEyeFocusProfile existing = project.getEyeFocusProfile(characterField.getText(), expressionField.getText());

        TextField sourceXField = new TextField(formatDialogNumber(existing != null ? existing.sourceX() : 0.5));
        TextField sourceYField = new TextField(formatDialogNumber(existing != null ? existing.sourceY() : 0.26));
        TextField targetXField = new TextField("1");
        TextField targetYField = new TextField(formatDialogNumber(existing != null ? existing.sourceY() : 0.26));
        TextField deadZoneField = new TextField(formatDialogNumber(existing != null ? existing.deadZone() : 0.12));
        TextField maxNudgeField = new TextField(formatDialogNumber(existing != null ? existing.maxNudgePx() : 3.0));
        TextField strengthField = new TextField(formatDialogNumber(existing != null ? existing.strength() : 1.0));

        TextField[] layerFields = new TextField[10];
        for (int i = 1; i <= 9; i++) {
            String layer = existing != null ? existing.layerIdFor(i) : "";
            if (layer == null || layer.isBlank()) {
                layer = inferred[2].isBlank() ? String.format(Locale.ROOT, "eyes_%02d", i) : layerVariant(inferred[2], i);
            }
            layerFields[i] = new TextField(layer);
        }

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(formLabel("Character"), 0, 0);
        grid.add(characterField, 1, 0);
        grid.add(formLabel("Expression"), 2, 0);
        grid.add(expressionField, 3, 0);
        grid.add(formLabel("Source X"), 0, 1);
        grid.add(sourceXField, 1, 1);
        grid.add(formLabel("Source Y"), 2, 1);
        grid.add(sourceYField, 3, 1);
        grid.add(formLabel("Target X"), 0, 2);
        grid.add(targetXField, 1, 2);
        grid.add(formLabel("Target Y"), 2, 2);
        grid.add(targetYField, 3, 2);
        grid.add(formLabel("Dead Zone"), 0, 3);
        grid.add(deadZoneField, 1, 3);
        grid.add(formLabel("Max Nudge"), 2, 3);
        grid.add(maxNudgeField, 3, 3);
        grid.add(formLabel("Strength"), 0, 4);
        grid.add(strengthField, 1, 4);

        int row = 5;
        int[][] keypad = {{7, 8, 9}, {4, 5, 6}, {1, 2, 3}};
        for (int r = 0; r < keypad.length; r++) {
            for (int c = 0; c < keypad[r].length; c++) {
                int index = keypad[r][c];
                VBox cell = new VBox(3, formLabel(Integer.toString(index)), layerFields[index]);
                cell.setPrefWidth(150);
                grid.add(cell, c, row + r);
            }
        }

        VBox content = new VBox(10);
        content.setFillWidth(true);
        Label note = new Label("Applies visibility keys for the 9 mapped pupil layers and X/Y nudge keys for the selected gaze at the current playhead.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: #bfc7d5; -fx-font-size: 11px;");
        content.getChildren().addAll(note, grid);

        overlayDialog.showDialog(
            "Eye Focus / Look At",
            "Bake a keypad pupil focus pose into the timeline.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Apply at Playhead", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
                try {
                    Map<Integer, String> layers = new LinkedHashMap<>();
                    for (int i = 1; i <= 9; i++) {
                        String layer = layerFields[i].getText() == null ? "" : layerFields[i].getText().trim();
                        if (!layer.isBlank()) layers.put(i, layer);
                    }
                    VnEyeFocusProfile profile = new VnEyeFocusProfile(
                        characterField.getText(),
                        expressionField.getText(),
                        "eyes",
                        parseDialogDouble(sourceXField, 0.5),
                        parseDialogDouble(sourceYField, 0.26),
                        parseDialogDouble(deadZoneField, 0.12),
                        parseDialogDouble(maxNudgeField, 3.0),
                        parseDialogDouble(strengthField, 1.0),
                        layers);
                    if (profile.characterId().isBlank() || profile.layerIds().isEmpty()) {
                        showOverlayError("Eye Focus Failed", "Missing eye-focus rig data.", "Provide a character id and at least one pupil layer mapping.");
                        return;
                    }
                    AnimationProject before = project.copy();
                    AnimationProject after = project.copy();
                    PuppeteerEyeFocusBaker.applyAt(
                        after,
                        profile,
                        project.getPlayheadMs(),
                        parseDialogDouble(sourceXField, 0.5),
                        parseDialogDouble(sourceYField, 0.26),
                        parseDialogDouble(targetXField, 1.0),
                        parseDialogDouble(targetYField, 0.0));
                    commandStack.execute(new PuppeteerCommand(
                        "Apply eye focus",
                        () -> project.replaceFrom(after),
                        () -> project.replaceFrom(before)
                    ));
                    timelinePanel.refresh();
                    updatePreview();
                    refreshExportPreviewAndMarkDirty();
                    overlayDialog.hideOverlay();
                } catch (Exception ex) {
                    showOverlayError("Eye Focus Failed", "Could not bake the eye focus pose.", ex.getMessage());
                }
            })
        );
    }

    private Label formLabel(String text) {
        Label label = new Label(text == null ? "" : text);
        label.setStyle("-fx-text-fill: #9fb1c9; -fx-font-size: 10px; -fx-font-weight: bold;");
        return label;
    }

    private double parseDialogDouble(TextField field, double fallback) {
        try {
            double value = Double.parseDouble(field == null || field.getText() == null ? "" : field.getText().trim());
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String formatDialogNumber(double value) {
        if (!Double.isFinite(value)) return "0";
        if (Math.abs(value - Math.rint(value)) < 0.000001) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.4f", value)
            .replaceAll("0+$", "")
            .replaceAll("\\.$", "");
    }

    private String[] inferEyeFocusSelection(String selectedTarget) {
        String value = selectedTarget == null ? "" : selectedTarget.trim();
        if (value.isBlank()) return new String[] {"", "neutral", ""};
        String[] parts = value.split("_");
        for (int i = 0; i < parts.length; i++) {
            String one = parts[i];
            String two = i + 1 < parts.length ? one + "_" + parts[i + 1] : one;
            int oneIndex = VnEyeFocusProfile.detectKeypadIndex(one);
            int twoIndex = VnEyeFocusProfile.detectKeypadIndex(two);
            if (twoIndex >= 1) {
                String expression = i > 0 ? parts[i - 1] : "neutral";
                String character = joinParts(parts, 0, Math.max(0, i - 1));
                return new String[] {character, expression, two};
            }
            if (oneIndex >= 1) {
                String expression = i > 0 ? parts[i - 1] : "neutral";
                String character = joinParts(parts, 0, Math.max(0, i - 1));
                return new String[] {character, expression, one};
            }
        }
        return new String[] {"", "neutral", ""};
    }

    private String layerVariant(String selectedLayer, int keypadIndex) {
        if (selectedLayer == null || selectedLayer.isBlank()) {
            return String.format(Locale.ROOT, "eyes_%02d", keypadIndex);
        }
        return selectedLayer.replaceFirst("(?i)(0?[1-9])$", String.format(Locale.ROOT, "%02d", keypadIndex));
    }

    private String joinParts(String[] parts, int startInclusive, int endExclusive) {
        if (parts == null || startInclusive >= endExclusive) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, startInclusive); i < Math.min(parts.length, endExclusive); i++) {
            if (parts[i] == null || parts[i].isBlank()) continue;
            if (sb.length() > 0) sb.append('_');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private void applyPreset(AnimationPreset preset) {
        if (timelinePanel.isSelectedGroup()) return;
        EntityTrack track = selectedTrackForEditing(true);
        if (track == null) return;
        double startTime = project.getPlayheadMs();
        commandStack.execute(PuppeteerCommand.applyPreset(track, preset, startTime));
        timelinePanel.refresh();
        updatePreview();
        refreshExportPreviewAndMarkDirty();
    }

    public void refreshExportPreview() {
        try {
            codePreview.setCode(compactExport ? CodeExporter.exportCompact(project) : CodeExporter.export(project));
            ResolvedCharacterContext rigContext = resolveCharacterForDiagnostics();
            List<TimelineDiagnostic.Message> diags = new ArrayList<>(
                PuppeteerVerification.diagnose(
                    project,
                    knownSceneEntities(),
                    projectRoot,
                    PuppeteerVerification.Mode.EXPORT_CODE,
                    rigContext.character(),
                    rigContext.characterId()
                )
            );
            diags.addAll(TimelineDiagnostic.diagnoseDsl(codePreview.getCode()));
            codePreview.setDiagnostics(diags);
        } catch (Exception ex) {
            codePreview.setDiagnostics(List.of(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.ERROR,
                "(export)",
                "Code preview could not be regenerated: " + firstNonBlank(ex.getMessage(), ex.getClass().getSimpleName()),
                "Review recent timeline edits, invalid numeric values, and custom property keys"
            )));
            if (statusBar != null) {
                statusBar.setText("Code preview refresh failed: " + firstNonBlank(ex.getMessage(), ex.getClass().getSimpleName()));
            }
        }
        refreshSidebarTabs();
    }

    private void syncDurationUi() {
        String current = tfDuration.getText();
        String updated = String.valueOf((int) project.getTotalDurationMs());
        if (!updated.equals(current)) {
            tfDuration.setText(updated);
            keyframeEditor.setTimelineDurationMs(project.getTotalDurationMs());
        }
    }

    public void refreshExportPreviewAndMarkDirty() {
        refreshExportPreview();
        setDirty(true);
        updateStatusBar();
        scheduleDraftSave();
    }

    private void updateStatusBar() {
        if (statusBar == null) return;
        StringBuilder sb = new StringBuilder();
        if (commandStack.canUndo()) {
            sb.append("Undo: ").append(commandStack.undoDescription());
        }
        if (commandStack.canRedo()) {
            if (sb.length() > 0) sb.append("  │  ");
            sb.append("Redo: ").append(commandStack.redoDescription());
        }
        if (sb.length() == 0) sb.append("Ready");
        if (autoKeyEnabled) sb.append("  │  Auto-Key ON");
        if (runtimeParityPreview) sb.append("  │  Runtime Preview ON");
        if (viewportStabilizationEnabled) {
            sb.append("  │  Stabilization ");
            sb.append(animationPreview.isViewportStabilizationActive() ? "ACTIVE" : "ON");
        }
        sb.append("  │  Speed: ").append(playbackSpeed).append("x");
        statusBar.setText(sb.toString());
        statusBar.setTooltip(new Tooltip(sb.toString()));

        if (statusPlaybackLabel != null) {
            String playback = project.isPlaying() ? "Playing" : "Stopped";
            if (project.isLooping()) playback += " / Loop";
            playback += " / " + trimPlaybackSpeed(playbackSpeed) + "x";
            statusPlaybackLabel.setText(playback);
            statusPlaybackLabel.setTooltip(new Tooltip("Playback state. Click for play, stop, rewind, and loop controls."));
            setSegmentState(statusPlaybackSegment, project.isPlaying() ? "jvn-status-diagnostics-ok" : "",
                "jvn-status-diagnostics-ok");
        }
        if (statusTimelineLabel != null) {
            String timeline = formatStatusTime(project.getPlayheadMs()) + " / " + formatStatusTime(project.getTotalDurationMs());
            statusTimelineLabel.setText(timeline);
            statusTimelineLabel.setTooltip(new Tooltip("Playhead and duration. Click for keyframe navigation."));
        }
        if (statusSelectionLabel != null) {
            String selection = "No selection";
            if (timelinePanel != null) {
                int selectedKeys = timelinePanel.getSelectionCount();
                if (selectedKeys > 0) {
                    selection = selectedKeys + " keyframe" + (selectedKeys == 1 ? "" : "s");
                } else if (timelinePanel.getSelectedEntity() != null && !timelinePanel.getSelectedEntity().isBlank()) {
                    selection = selectionLabel(timelinePanel.getSelectedEntity(), timelinePanel.isSelectedGroup());
                }
                PropertyType selectedProperty = timelinePanel.getSelectedProperty();
                if (selectedProperty != null && !selection.equals("No selection")) {
                    selection += " / " + selectedProperty.getDisplayName();
                }
            }
            statusSelectionLabel.setText(selection);
            statusSelectionLabel.setTooltip(new Tooltip("Current target, property, and keyframe selection."));
        }
        if (statusSceneLabel != null) {
            int tracks = project.getTrackCount();
            int groups = 0;
            for (EntityGroup ignored : project.getGroups()) groups++;
            int audio = project.getAudioCues().size();
            int events = project.getEditorEventCues().size();
            String sceneStats = tracks + " track" + (tracks == 1 ? "" : "s")
                + " / " + groups + " group" + (groups == 1 ? "" : "s");
            if (audio > 0 || events > 0) {
                sceneStats += " / " + audio + " audio / " + events + " events";
            }
            statusSceneLabel.setText(sceneStats);
            statusSceneLabel.setTooltip(new Tooltip("Scene contents, groups, audio cues, and event cues."));
        }
        if (statusComplexityLabel != null) {
            StatusComplexity complexity = computeStatusComplexity();
            String text = complexity.keyframes() + " key" + (complexity.keyframes() == 1 ? "" : "s")
                + " / " + complexity.channels() + " chan" + (complexity.channels() == 1 ? "" : "s");
            statusComplexityLabel.setText(text);
            statusComplexityLabel.setTooltip(new Tooltip(
                "Animated keyframes/channels: " + complexity.keyframes() + " / " + complexity.channels()
                    + "\nConstraints: " + complexity.constraints()
                    + "\nAnchors: " + complexity.anchors()
                    + "\nEye focus profiles: " + complexity.eyeProfiles()));
            setSegmentState(statusComplexitySegment,
                complexity.keyframes() > 240 || complexity.channels() > 32 ? "jvn-status-diagnostics-warn" : "",
                "jvn-status-diagnostics-warn");
        }
        if (statusModeLabel != null) {
            List<String> modes = new ArrayList<>();
            modes.add(timelinePanel != null && timelinePanel.isSnapEnabled()
                ? "Snap " + String.format(Locale.ROOT, "%.0fms", timelinePanel.getSnapStepMs())
                : "Snap Off");
            if (autoKeyEnabled) modes.add("Auto-Key");
            if (runtimeParityPreview) modes.add("Runtime");
            if (viewportStabilizationEnabled) {
                modes.add(animationPreview.isViewportStabilizationActive() ? "Stable Active" : "Stable");
            }
            statusModeLabel.setText(String.join(" / ", modes));
            statusModeLabel.setTooltip(new Tooltip("Timeline snapping, auto-key, runtime preview, and viewport stabilization."));
            setSegmentState(statusModeSegment,
                autoKeyEnabled || runtimeParityPreview || viewportStabilizationEnabled ? "jvn-status-diagnostics-warn" : "",
                "jvn-status-diagnostics-warn");
        }
        if (statusSaveLabel != null) {
            String saveState = previewStaged ? "Preview Staged" : dirty ? "Unsaved" : "Saved";
            statusSaveLabel.setText(saveState);
            statusSaveLabel.setTooltip(new Tooltip("Timeline save and staged code-preview state."));
            setSegmentState(statusSaveSegment,
                previewStaged || dirty ? "jvn-status-dirty" : "jvn-status-clean",
                "jvn-status-clean", "jvn-status-dirty");
        }
        if (statusViewportLabel != null) {
            ProjectViewportSpec.Dimensions viewport = animationPreview != null
                ? animationPreview.getViewportDimensions()
                : ProjectViewportSpec.resolve(projectRoot);
            String viewportText = viewport.width() + "x" + viewport.height();
            if (previewFocusMode) viewportText += " Focus";
            statusViewportLabel.setText(viewportText);
            String cameraText = animationPreview == null
                ? ""
                : String.format(Locale.ROOT,
                    "\nCamera: X %.1f  Y %.1f  Z %.2f",
                    animationPreview.getCamera().getX(),
                    animationPreview.getCamera().getY(),
                    animationPreview.getCamera().getZoom());
            statusViewportLabel.setTooltip(new Tooltip(
                "Runtime viewport: " + viewport.width() + " x " + viewport.height()
                    + cameraText
                    + "\nClick for fit, focus, and recording actions."));
            setSegmentState(statusViewportSegment,
                previewFocusMode ? "jvn-status-diagnostics-ok" : "",
                "jvn-status-diagnostics-ok");
        }
        if (statusProjectLabel != null) {
            String projectName = projectRoot == null
                ? "No project"
                : firstNonBlank(projectRoot.getName(), projectRoot.getAbsolutePath());
            statusProjectLabel.setText(projectName);
            statusProjectLabel.setTooltip(new Tooltip(projectRoot == null
                ? "No project root is bound."
                : projectRoot.getAbsolutePath()));
        }
        if (statusExportLabel != null) {
            List<String> export = new ArrayList<>();
            export.add(compactExport ? "Compact JES" : "Standard JES");
            export.add(isCodePaneVisible() ? "Code visible" : "Code hidden");
            int codeLines = countCodeLines(codePreview == null ? null : codePreview.getCode());
            if (codeLines > 0) export.add(codeLines + " lines");
            int copied = timelinePanel == null ? 0 : timelinePanel.getCopiedKeyframeCount();
            if (copied > 0) export.add("Clipboard " + copied);
            statusExportLabel.setText(String.join(" / ", export));
            statusExportLabel.setTooltip(new Tooltip(
                "Export format, generated code size, code pane visibility, and keyframe clipboard state."));
            setSegmentState(statusExportSegment, isCodePaneVisible() ? "" : "jvn-status-diagnostics-warn",
                "jvn-status-diagnostics-warn");
        }
        refreshToolbarCommandSummary();
    }

    private StatusComplexity computeStatusComplexity() {
        int keyframes = 0;
        int channels = 0;
        int anchors = 0;
        for (EntityTrack track : project.getTracks()) {
            if (track == null) continue;
            for (PropertyType property : track.getAnimatedProperties()) {
                int count = track.getKeyframes(property).size();
                if (count > 0) {
                    keyframes += count;
                    channels++;
                }
            }
            for (String propertyKey : track.getAnimatedCustomProperties()) {
                int count = track.getCustomKeyframes(propertyKey).size();
                if (count > 0) {
                    keyframes += count;
                    channels++;
                }
            }
            anchors += project.getAnchorsForEntity(track.getEntityName()).size();
        }
        return new StatusComplexity(
            keyframes,
            channels,
            project.getConstraintsView().size(),
            anchors,
            project.getEyeFocusProfilesView().size());
    }

    private static int countCodeLines(String code) {
        if (code == null || code.isBlank()) return 0;
        int lines = 1;
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) == '\n') lines++;
        }
        return lines;
    }

    private static void setSegmentState(HBox segment, String active, String... states) {
        if (segment == null || states == null) return;
        segment.getStyleClass().removeAll(states);
        if (active != null && !active.isBlank() && !segment.getStyleClass().contains(active)) {
            segment.getStyleClass().add(active);
        }
    }

    private static String formatStatusTime(double millis) {
        if (!Double.isFinite(millis)) millis = 0.0;
        long totalMillis = Math.max(0L, Math.round(millis));
        long minutes = totalMillis / 60_000L;
        long seconds = (totalMillis / 1_000L) % 60L;
        long ms = totalMillis % 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, ms);
    }

    private static String trimPlaybackSpeed(double speed) {
        if (!Double.isFinite(speed)) return "1";
        if (Math.abs(speed - Math.rint(speed)) < 0.0001) {
            return String.format(Locale.ROOT, "%.0f", speed);
        }
        return String.format(Locale.ROOT, "%.2f", speed).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private record StatusComplexity(
        int keyframes,
        int channels,
        int constraints,
        int anchors,
        int eyeProfiles
    ) {}

    private void updateViewportInfoLabel() {
        if (viewportInfoLabel == null) return;
        ProjectViewportSpec.Dimensions vp = ProjectViewportSpec.resolve(projectRoot);
        viewportInfoLabel.setText(
            "Viewport: " + vp.width() + "x" + vp.height()
                + ""
        );
    }

    public void showShortcutsOverlay() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8;");

        String[][] shortcuts = {
            {"Space", "Play / Pause"},
            {"Home", "Rewind"},
            {"Page Up / Down", "Jump to previous / next keyframe"},
            {"K", "Add keyframe at playhead"},
            {"Del", "Delete selected keyframe"},
            {"Ctrl/Cmd + Alt + F", "Focus timeline on selection"},
            {"Alt + ← / →", "Nudge keyframe by snap step"},
            {"Alt + Shift + ← / →", "Nudge keyframe by 1ms"},
            {"Alt + Shift + R", "Reverse selected keyframes"},
            {"Alt + Shift + E", "Distribute selected keyframes"},
            {"Ctrl/Cmd + Alt + C", "Copy selected keyframes"},
            {"Ctrl/Cmd + Alt + V", "Paste keyframes at playhead"},
            {"Ctrl/Cmd + Alt + D", "Duplicate keyframes"},
            {"Ctrl/Cmd + Shift + C", "Copy exported code"},
            {"Ctrl/Cmd + Z", "Undo"},
            {"Ctrl/Cmd + Shift + Z", "Redo"},
            {"Ctrl/Cmd + O", "Toggle onion skinning"},
            {"A", "Toggle orbit tool"},
            {"Shift + A", "Clear orbit anchor"}
        };

        for (int i = 0; i < shortcuts.length; i++) {
            Label keyLabel = new Label(shortcuts[i][0]);
            keyLabel.setStyle("-fx-text-fill: #9cdcfe; -fx-font-family: Monospaced; -fx-font-weight: bold; -fx-background-color: #2d2d2d; -fx-padding: 4 8; -fx-background-radius: 4; -fx-border-color: #3d3d3d; -fx-border-radius: 4;");
            
            Label descLabel = new Label(shortcuts[i][1]);
            descLabel.setStyle("-fx-text-fill: #d7d7d7; -fx-font-size: 13px;");
            
            grid.add(keyLabel, 0, i);
            grid.add(descLabel, 1, i);
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(380);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-padding: 0;");

        overlayDialog.showDialog(
            "Keyboard Shortcuts",
            "Puppeteer keyboard shortcuts",
            scroll,
            ActionEditorDialogOverlay.ActionSpec.accent("Close", overlayDialog::hideOverlay)
        );
    }

    private void setDirty(boolean value) {
        dirty = value;
        String timelineName = tfTimelineName != null ? tfTimelineName.getText().trim() : "";
        if (timelineName.isBlank()) timelineName = project.getName();
        if (timelineName == null || timelineName.isBlank()) timelineName = "Untitled Animation";
        String dirtySuffix = dirty ? " *" : "";
        String previewSuffix = previewStaged ? " [preview]" : "";
        setTitle("Puppeteer - " + timelineName + dirtySuffix + previewSuffix);
        refreshToolbarCommandSummary();
        updateStatusBar();
    }

    private void applySnapStepFromField() {
        try {
            double step = Double.parseDouble(tfSnapMs.getText().trim());
            timelinePanel.setSnapStepMs(step);
            tfSnapMs.setText(String.format("%.0f", timelinePanel.getSnapStepMs()));
        } catch (Exception ex) {
            tfSnapMs.setText(String.format("%.0f", timelinePanel.getSnapStepMs()));
        }
        updateStatusBar();
    }

    public void showAddAudioCueDialog() {
        TextField tfPath = new TextField();
        tfPath.setPromptText("assets/audio/music/softbreeze.mp3");
        tfPath.setStyle(STYLE_TEXT_FIELD);

        List<PuppeteerAudioLibrary.AudioEntry> libraryEntries = new ArrayList<>(PuppeteerAudioLibrary.scan(projectRoot));
        TextField tfLibraryFilter = new TextField();
        tfLibraryFilter.setPromptText("Filter project audio...");
        tfLibraryFilter.setStyle(STYLE_TEXT_FIELD);

        ListView<PuppeteerAudioLibrary.AudioEntry> libraryList = new ListView<>();
        libraryList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(PuppeteerAudioLibrary.AudioEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.relativePath());
            }
        });
        libraryList.setPrefHeight(180);
        libraryList.setMinHeight(120);
        libraryList.setStyle("-fx-background-color: #14171d; -fx-control-inner-background: #14171d;");

        Label libraryMeta = makeToolbarLabel("");
        refreshAudioLibraryList(libraryList, libraryEntries, tfLibraryFilter.getText());
        libraryMeta.setText(libraryEntries.isEmpty()
            ? "No project audio found yet. Use Browse or Import to seed the library."
            : libraryEntries.size() + " project audio files");
        tfLibraryFilter.textProperty().addListener((obs, oldValue, newValue) ->
            refreshAudioLibraryList(libraryList, libraryEntries, newValue)
        );
        libraryList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                tfPath.setText(newValue.relativePath());
            }
        });
        libraryList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && libraryList.getSelectionModel().getSelectedItem() != null) {
                tfPath.setText(libraryList.getSelectionModel().getSelectedItem().relativePath());
                playAudioPreview(tfPath.getText());
                event.consume();
            }
        });

        Button btnBrowseAudio = new Button("Browse...");
        btnBrowseAudio.setStyle(STYLE_BTN_DARK);
        btnBrowseAudio.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose Audio Cue");
            chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("Audio", "*.ogg", "*.wav", "*.mp3", "*.m4a", "*.aac", "*.flac", "*.opus"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
            if (projectRoot != null && projectRoot.isDirectory()) {
                chooser.setInitialDirectory(projectRoot);
            }
            File selected = chooser.showOpenDialog(this);
            if (selected == null) return;
            if (projectRoot != null && projectRoot.isDirectory()) {
                Path rootPath = projectRoot.toPath().toAbsolutePath().normalize();
                Path selectedPath = selected.toPath().toAbsolutePath().normalize();
                if (selectedPath.startsWith(rootPath)) {
                    tfPath.setText(rootPath.relativize(selectedPath).toString().replace('\\', '/'));
                    return;
                }
            }
            tfPath.setText(selected.getAbsolutePath().replace('\\', '/'));
        });

        Button btnImportAudio = new Button("Import...");
        btnImportAudio.setStyle(STYLE_BTN_DARK);
        btnImportAudio.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Import Audio Into Project");
            chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("Audio", "*.ogg", "*.wav", "*.mp3", "*.m4a", "*.aac", "*.flac", "*.opus"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
            File initialDir = projectRoot != null && projectRoot.isDirectory()
                ? new File(projectRoot, PuppeteerAudioLibrary.IMPORT_RELATIVE_DIR)
                : null;
            if (initialDir != null && initialDir.isDirectory()) {
                chooser.setInitialDirectory(initialDir);
            } else if (projectRoot != null && projectRoot.isDirectory()) {
                chooser.setInitialDirectory(projectRoot);
            }
            File selected = chooser.showOpenDialog(this);
            if (selected == null) return;
            if (projectRoot == null || !projectRoot.isDirectory()) {
                tfPath.setText(selected.getAbsolutePath().replace('\\', '/'));
                return;
            }
            try {
                Path importDir = projectRoot.toPath().resolve(PuppeteerAudioLibrary.IMPORT_RELATIVE_DIR);
                Files.createDirectories(importDir);
                Path target = PuppeteerAudioLibrary.resolveUniqueImportTarget(importDir, selected.getName());
                Files.copy(selected.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                String relativePath = projectRoot.toPath().toAbsolutePath().normalize()
                    .relativize(target.toAbsolutePath().normalize())
                    .toString()
                    .replace('\\', '/');
                tfPath.setText(relativePath);
                libraryEntries.clear();
                libraryEntries.addAll(PuppeteerAudioLibrary.scan(projectRoot));
                refreshAudioLibraryList(libraryList, libraryEntries, tfLibraryFilter.getText());
                libraryMeta.setText(libraryEntries.size() + " project audio files");
            } catch (IOException ex) {
                statusBar.setText("Audio import failed: " + ex.getMessage());
            }
        });

        Button btnPreviewAudio = new Button("Preview");
        btnPreviewAudio.setStyle(STYLE_BTN_DARK);
        btnPreviewAudio.setOnAction(e -> playAudioPreview(tfPath.getText()));

        Button btnStopAudio = new Button("Stop");
        btnStopAudio.setStyle(STYLE_BTN_DARK);
        btnStopAudio.setOnAction(e -> stopAudioPreview());

        HBox pathRow = new HBox(8, tfPath, btnBrowseAudio, btnImportAudio);
        HBox.setHgrow(tfPath, Priority.ALWAYS);
        pathRow.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup channelGroup = new ToggleGroup();
        RadioButton rbMusic = buildChannelButton("music", channelGroup, true);
        RadioButton rbSound = buildChannelButton("sound", channelGroup, false);
        RadioButton rbVoice = buildChannelButton("voice", channelGroup, false);

        javafx.scene.control.Slider volume = new javafx.scene.control.Slider(0.0, 1.0, 1.0);
        volume.setBlockIncrement(0.05);
        volume.setMajorTickUnit(0.25);
        volume.setMinorTickCount(4);
        volume.setShowTickMarks(false);
        volume.setShowTickLabels(false);

        Label volumeLabel = new Label("1.00");
        volumeLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
        volume.valueProperty().addListener((obs, ov, nv) -> volumeLabel.setText(String.format("%.2f", nv.doubleValue())));

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(8, 8, 4, 8));
        Label lPath = makeToolbarLabel("Path");
        Label lChannel = makeToolbarLabel("Channel");
        Label lVolume = makeToolbarLabel("Volume");
        grid.add(lPath, 0, 0);
        grid.add(pathRow, 1, 0);
        grid.add(lChannel, 0, 1);
        grid.add(new HBox(8, rbMusic, rbSound, rbVoice), 1, 1);
        grid.add(lVolume, 0, 2);
        grid.add(new HBox(8, volume, volumeLabel), 1, 2);

        Label libraryTitle = makeToolbarLabel("Project Audio");
        HBox libraryActions = new HBox(8, btnPreviewAudio, btnStopAudio, libraryMeta);
        libraryActions.setAlignment(Pos.CENTER_LEFT);
        VBox libraryBox = new VBox(8, libraryTitle, tfLibraryFilter, libraryList, libraryActions);
        libraryBox.setPadding(new Insets(0, 8, 8, 8));
        overlayDialog.showDialog(
            "Add Audio Cue",
            "Create an audio trigger at playhead " + String.format("%.0fms", project.getPlayheadMs()),
            new VBox(8, grid, libraryBox),
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", () -> {
                stopAudioPreview();
                overlayDialog.hideOverlay();
            }).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Add Cue", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
            String path = tfPath.getText() != null ? tfPath.getText().trim() : "";
                if (path.isBlank()) {
                    tfPath.requestFocus();
                    return;
                }
                RadioButton selectedChannel = (RadioButton) channelGroup.getSelectedToggle();
                String channel = selectedChannel != null ? selectedChannel.getText() : "music";
                AudioCue cue = new AudioCue(project.getPlayheadMs(), path, channel);
            cue.setVolume(volume.getValue());
            project.addAudioCue(cue);
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
                stopAudioPreview();
                overlayDialog.hideOverlay();
            })
        );
    }

    private void showExpressionKeyframeDialog() {
        showExpressionKeyframeDialog(null, null);
    }

    private void showExpressionKeyframeDialog(String presetTarget, String presetExpression) {
        String initialTarget = (presetTarget != null && !presetTarget.isBlank())
            ? presetTarget
            : inferExpressionTargetFromSelection();
        ComboBox<String> cbTarget = new ComboBox<>();
        cbTarget.setEditable(true);
        styleExpressionKeyframeCombo(cbTarget);
        cbTarget.getItems().setAll(expressionTargetSuggestions());
        cbTarget.setValue(initialTarget);
        cbTarget.setPromptText("character / entity");

        ComboBox<String> cbExpression = new ComboBox<>();
        cbExpression.setEditable(true);
        styleExpressionKeyframeCombo(cbExpression);
        cbExpression.getItems().setAll(expressionSuggestionsForTarget(initialTarget));
        String initialExpression = (presetExpression != null && !presetExpression.isBlank()
            && expressionSuggestionsForTarget(initialTarget).contains(presetExpression))
            ? presetExpression
            : preferredExpressionForTarget(initialTarget);
        cbExpression.setValue(initialExpression);
        cbExpression.setPromptText("expression");

        TextField tfTime = new TextField(String.format(Locale.ROOT, "%.0f", project.getPlayheadMs()));
        tfTime.setPromptText("ms");
        tfTime.getStyleClass().add("expression-keyframe-text-field");

        TextField tfPath = new TextField();
        tfPath.setPromptText("optional path override");
        tfPath.getStyleClass().add("expression-keyframe-text-field");

        CheckBox cbEmbedResolved = new CheckBox("Embed resolved sprite path / layers");
        cbEmbedResolved.setSelected(true);
        cbEmbedResolved.getStyleClass().add("expression-keyframe-check");

        Label lblResolved = new Label();
        lblResolved.setWrapText(true);
        lblResolved.getStyleClass().add("expression-keyframe-resolved");

        TextArea taDslPreview = new TextArea();
        taDslPreview.setEditable(false);
        taDslPreview.setFocusTraversable(false);
        taDslPreview.setPrefRowCount(5);
        taDslPreview.setWrapText(true);
        taDslPreview.getStyleClass().add("expression-keyframe-preview");

        Runnable refreshResolved = () -> {
            String target = editableComboText(cbTarget);
            String expression = editableComboText(cbExpression);
            List<ExpressionLayerSpec> layers = resolveExpressionLayerSpecs(target, expression, Map.of());
            String path = firstNonBlank(tfPath.getText(), resolveRawCueAssetPath(target, expression));
            if (!layers.isEmpty()) {
                setExpressionResolutionState(lblResolved, true);
                String layerNames = String.join(", ", layers.stream()
                    .map(ExpressionLayerSpec::layerId)
                    .filter(name -> name != null && !name.isBlank())
                    .toList());
                lblResolved.setText(layers.size() + " layered sprite entries resolved"
                    + (layerNames.isBlank() ? "" : ": " + layerNames));
            } else if (path != null && !path.isBlank()) {
                setExpressionResolutionState(lblResolved, true);
                lblResolved.setText("Sprite path resolved: " + path);
            } else {
                setExpressionResolutionState(lblResolved, false);
                lblResolved.setText("No mapping found; the expression name will still export for VN runtime playback.");
            }
            double previewTime = parseNonNegativeDoubleOr(tfTime.getText(), project.getPlayheadMs());
            Map<String, String> payload = buildExpressionCuePayload(
                target,
                expression,
                tfPath.getText(),
                cbEmbedResolved.isSelected());
            taDslPreview.setText(new EditorEventCue(previewTime, "expression", payload).getDslPreview());
        };

        cbTarget.valueProperty().addListener((obs, oldValue, newValue) -> {
            List<String> expressions = expressionSuggestionsForTarget(newValue);
            cbExpression.getItems().setAll(expressions);
            if (cbExpression.getValue() == null || cbExpression.getValue().isBlank()
                || !expressions.contains(cbExpression.getValue())) {
                cbExpression.setValue(preferredExpressionForTarget(newValue));
            }
            refreshResolved.run();
        });
        cbExpression.valueProperty().addListener((obs, oldValue, newValue) -> refreshResolved.run());
        cbTarget.getEditor().textProperty().addListener((obs, oldValue, newValue) -> refreshResolved.run());
        cbExpression.getEditor().textProperty().addListener((obs, oldValue, newValue) -> refreshResolved.run());
        tfPath.textProperty().addListener((obs, oldValue, newValue) -> refreshResolved.run());
        tfTime.textProperty().addListener((obs, oldValue, newValue) -> refreshResolved.run());
        cbEmbedResolved.selectedProperty().addListener((obs, oldValue, newValue) -> refreshResolved.run());
        refreshResolved.run();

        VBox targetField = expressionKeyframeField("Character target", cbTarget);
        VBox expressionField = expressionKeyframeField("Expression preset", cbExpression);
        HBox selectorRow = new HBox(12, targetField, expressionField);
        HBox.setHgrow(targetField, Priority.ALWAYS);
        HBox.setHgrow(expressionField, Priority.ALWAYS);

        VBox timeField = expressionKeyframeField("Timeline position (ms)", tfTime);
        timeField.setMinWidth(150);
        timeField.setPrefWidth(150);
        timeField.setMaxWidth(150);
        VBox pathField = expressionKeyframeField("Sprite path override", tfPath);
        HBox detailsRow = new HBox(12, timeField, pathField);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        VBox form = new VBox(12, selectorRow, detailsRow, cbEmbedResolved);
        form.getStyleClass().add("expression-keyframe-form");

        Label previewTitle = new Label("Timeline payload");
        previewTitle.getStyleClass().add("expression-keyframe-section-title");
        Label previewHint = new Label("Generated automatically");
        previewHint.getStyleClass().add("expression-keyframe-section-hint");
        Region previewSpacer = new Region();
        HBox.setHgrow(previewSpacer, Priority.ALWAYS);
        HBox previewHeader = new HBox(8, previewTitle, previewSpacer, previewHint);
        previewHeader.setAlignment(Pos.CENTER_LEFT);

        VBox previewCard = new VBox(8, previewHeader, taDslPreview);
        previewCard.getStyleClass().add("expression-keyframe-preview-card");
        VBox.setVgrow(taDslPreview, Priority.ALWAYS);

        VBox body = new VBox(12, form, lblResolved, previewCard);
        body.getStyleClass().add("expression-keyframe-dialog");

        overlayDialog.showCompactDialog(
            "Expression Keyframe",
            "Choose a character preset and place the expression change on the timeline.",
            body,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Add Keyframe", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
                double timeMs;
                try {
                    timeMs = Math.max(0.0, Double.parseDouble(tfTime.getText().trim()));
                } catch (NumberFormatException ex) {
                    tfTime.requestFocus();
                    return;
                }
                String target = editableComboText(cbTarget);
                String expression = editableComboText(cbExpression);
                if (target.isBlank()) {
                    cbTarget.requestFocus();
                    return;
                }
                if (expression.isBlank()) {
                    cbExpression.requestFocus();
                    return;
                }
                String directPath = tfPath.getText() == null ? "" : tfPath.getText().trim();
                Map<String, String> payload = buildExpressionCuePayload(
                    target,
                    expression,
                    directPath,
                    cbEmbedResolved.isSelected());
                project.addEditorEventCue(new EditorEventCue(timeMs, "expression", payload));
                timelinePanel.refresh();
                clearExpressionPreview();
                if (expressionPreviewPanel != null) expressionPreviewPanel.setPreviewingState(null);
                animationPreview.setExpressionPreviewIndicator(target, expression, false);
                updatePreview();
                refreshExportPreviewAndMarkDirty();
                overlayDialog.hideOverlay();
            })
        );
    }

    private static void styleExpressionKeyframeCombo(ComboBox<String> combo) {
        combo.getStyleClass().add("expression-keyframe-combo");
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setVisibleRowCount(8);
        combo.setCellFactory(list -> {
            if (!list.getStyleClass().contains("expression-keyframe-popup-list")) {
                list.getStyleClass().add("expression-keyframe-popup-list");
            }
            return new ListCell<>() {
                {
                    getStyleClass().add("expression-keyframe-popup-cell");
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item);
                }
            };
        });
    }

    private static void setExpressionResolutionState(Label label, boolean resolved) {
        label.getStyleClass().removeAll(
            "expression-keyframe-resolved-success",
            "expression-keyframe-resolved-warning");
        label.getStyleClass().add(resolved
            ? "expression-keyframe-resolved-success"
            : "expression-keyframe-resolved-warning");
    }

    private static VBox expressionKeyframeField(String labelText, Region control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("expression-keyframe-label");
        control.setMaxWidth(Double.MAX_VALUE);
        VBox field = new VBox(6, label, control);
        field.getStyleClass().add("expression-keyframe-field");
        return field;
    }

    private static String editableComboText(ComboBox<String> combo) {
        if (combo == null) return "";
        String editorText = combo.isEditable() && combo.getEditor() != null
            ? combo.getEditor().getText()
            : null;
        if (editorText != null && !editorText.isBlank()) return editorText.trim();
        String value = combo.getValue();
        return value == null ? "" : value.trim();
    }

    public void showEventCueManagerDialog(EditorEventCue initialSelection) {
        List<String> presets = List.of(
            "expression",
            "show",
            "hide",
            "replace",
            "scene",
            "dialogue_marker",
            "script_call",
            "custom"
        );

        ListView<EditorEventCue> cueList = new ListView<>();
        cueList.setPrefHeight(180);
        cueList.setMinHeight(120);
        cueList.setStyle("-fx-background-color: #14171d; -fx-control-inner-background: #14171d;");
        cueList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(EditorEventCue item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item.getDisplayLabel());
            }
        });

        ComboBox<String> cbTypePreset = new ComboBox<>();
        cbTypePreset.getItems().setAll(presets);
        cbTypePreset.setValue("expression");
        cbTypePreset.setStyle(STYLE_TEXT_FIELD);
        cbTypePreset.setPrefWidth(160);

        TextField tfType = new TextField("expression");
        tfType.setPromptText("event type");
        tfType.setStyle(STYLE_TEXT_FIELD);

        TextField tfTime = new TextField(String.format("%.0f", project.getPlayheadMs()));
        tfTime.setPromptText("playhead ms");
        tfTime.setStyle(STYLE_TEXT_FIELD);

        TextField tfTarget = new TextField();
        tfTarget.setPromptText("entity / character id");
        tfTarget.setStyle(STYLE_TEXT_FIELD);

        TextField tfValue = new TextField();
        tfValue.setPromptText("expression / id / name");
        tfValue.setStyle(STYLE_TEXT_FIELD);

        TextField tfPath = new TextField();
        tfPath.setPromptText("optional image path");
        tfPath.setStyle(STYLE_TEXT_FIELD);

        TextField tfPosition = new TextField();
        tfPosition.setPromptText("optional slot like left / center / right");
        tfPosition.setStyle(STYLE_TEXT_FIELD);

        TextArea taExtra = new TextArea();
        taExtra.setPromptText("extra payload\nkey=value");
        taExtra.setPrefRowCount(5);
        taExtra.setWrapText(false);
        taExtra.setStyle("-fx-control-inner-background: #111111; -fx-text-fill: #ececec;");

        TextArea taDslPreview = new TextArea();
        taDslPreview.setEditable(false);
        taDslPreview.setFocusTraversable(false);
        taDslPreview.setPrefRowCount(6);
        taDslPreview.setWrapText(false);
        taDslPreview.setStyle("-fx-control-inner-background: #111111; -fx-text-fill: #ececec;");

        Label lblTypeHint = makeToolbarLabel("");
        Label lblValue = makeToolbarLabel("Value");
        Label lblPath = makeToolbarLabel("Path");
        Label lblPosition = makeToolbarLabel("Position");

        Runnable refreshList = () -> cueList.getItems().setAll(project.getEditorEventCues());
        Runnable refreshCuePreview = () -> {
            String preset = cbTypePreset.getValue() == null ? "expression" : cbTypePreset.getValue();
            String type = "custom".equals(preset) ? tfType.getText().trim() : preset;
            double timeMs = parseNonNegativeDoubleOr(tfTime.getText(), project.getPlayheadMs());
            Map<String, String> payload = buildEventCuePayload(
                type,
                tfTarget.getText(),
                tfValue.getText(),
                tfPath.getText(),
                tfPosition.getText(),
                parsePayloadLines(taExtra.getText()),
                true);
            taDslPreview.setText(new EditorEventCue(timeMs, type, payload).getDslPreview());
        };
        Runnable clearForm = () -> {
            cueList.getSelectionModel().clearSelection();
            cbTypePreset.setValue("expression");
            tfType.setText("expression");
            tfTime.setText(String.format("%.0f", project.getPlayheadMs()));
            tfTarget.clear();
            tfValue.clear();
            tfPath.clear();
            tfPosition.clear();
            taExtra.clear();
            refreshCuePreview.run();
        };
        Runnable refreshTypeUi = () -> {
            String preset = cbTypePreset.getValue() == null ? "expression" : cbTypePreset.getValue();
            boolean custom = "custom".equals(preset);
            tfType.setDisable(!custom);
            if (!custom) {
                tfType.setText(preset);
            }
            switch (preset) {
                case "expression" -> {
                    lblValue.setText("Expression");
                    lblPath.setText("Path Override");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Swap a character or sprite to another expression at an exact frame.");
                }
                case "show" -> {
                    lblValue.setText("Expression");
                    lblPath.setText("Path Override");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Reveal an existing entity or character, optionally swapping its image or slot.");
                }
                case "hide" -> {
                    lblValue.setText("Value");
                    lblPath.setText("Path");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Hide an entity or character instantly.");
                }
                case "replace" -> {
                    lblValue.setText("Expression");
                    lblPath.setText("Replacement Path");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Replace the current sprite path or expression mid-sequence.");
                }
                case "scene" -> {
                    lblValue.setText("Scene / BG Id");
                    lblPath.setText("Background Path");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Trigger a background or cutaway change without leaving the timeline.");
                }
                case "dialogue_marker" -> {
                    lblValue.setText("Marker Id");
                    lblPath.setText("Path");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Drop a script-facing dialogue marker at the playhead.");
                }
                case "script_call" -> {
                    lblValue.setText("Call Name");
                    lblPath.setText("Arg");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Trigger a named script call cue with optional payload lines.");
                }
                default -> {
                    lblValue.setText("Value");
                    lblPath.setText("Path");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Author a custom event type with any payload keys you need.");
                }
            }
        };

        java.util.function.Consumer<EditorEventCue> loadCue = cue -> {
            if (cue == null) {
                clearForm.run();
                refreshTypeUi.run();
                return;
            }
            String type = cue.getType() == null ? "" : cue.getType().trim();
            cbTypePreset.setValue(presets.contains(type) ? type : "custom");
            tfType.setText(type);
            tfTime.setText(String.format("%.0f", cue.getTimeMs()));
            tfTarget.setText(cue.getPayloadValue("target"));

            Map<String, String> remaining = new LinkedHashMap<>(cue.getPayloadView());
            remaining.remove("target");
            switch (type) {
                case "expression" -> {
                    tfValue.setText(firstNonBlank(remaining.remove("value"), remaining.remove("expression")));
                    tfPath.setText(remaining.remove("path"));
                    tfPosition.setText(remaining.remove("position"));
                }
                case "show" -> {
                    tfValue.setText(firstNonBlank(remaining.remove("expression"), remaining.remove("value")));
                    tfPath.setText(remaining.remove("path"));
                    tfPosition.setText(remaining.remove("position"));
                }
                case "hide" -> {
                    tfValue.clear();
                    tfPath.clear();
                    tfPosition.clear();
                }
                case "replace" -> {
                    tfValue.setText(firstNonBlank(remaining.remove("expression"), remaining.remove("value")));
                    tfPath.setText(remaining.remove("path"));
                    tfPosition.clear();
                }
                case "scene" -> {
                    tfValue.setText(firstNonBlank(remaining.remove("id"), remaining.remove("value")));
                    tfPath.setText(remaining.remove("path"));
                    tfPosition.clear();
                }
                case "dialogue_marker" -> {
                    tfValue.setText(firstNonBlank(remaining.remove("id"), remaining.remove("value")));
                    tfPath.clear();
                    tfPosition.clear();
                }
                case "script_call" -> {
                    tfValue.setText(firstNonBlank(remaining.remove("name"), remaining.remove("value")));
                    tfPath.setText(firstNonBlank(remaining.remove("arg"), remaining.remove("path")));
                    tfPosition.clear();
                }
                default -> {
                    tfValue.setText(remaining.remove("value"));
                    tfPath.setText(remaining.remove("path"));
                    tfPosition.setText(remaining.remove("position"));
                }
            }
            taExtra.setText(formatPayloadLines(remaining));
            refreshTypeUi.run();
            refreshCuePreview.run();
        };

        cueList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> loadCue.accept(newValue));
        cbTypePreset.setOnAction(e -> {
            refreshTypeUi.run();
            refreshCuePreview.run();
        });
        tfType.textProperty().addListener((obs, oldValue, newValue) -> refreshCuePreview.run());
        tfTime.textProperty().addListener((obs, oldValue, newValue) -> refreshCuePreview.run());
        tfTarget.textProperty().addListener((obs, oldValue, newValue) -> refreshCuePreview.run());
        tfValue.textProperty().addListener((obs, oldValue, newValue) -> refreshCuePreview.run());
        tfPath.textProperty().addListener((obs, oldValue, newValue) -> refreshCuePreview.run());
        tfPosition.textProperty().addListener((obs, oldValue, newValue) -> refreshCuePreview.run());
        taExtra.textProperty().addListener((obs, oldValue, newValue) -> refreshCuePreview.run());

        refreshList.run();
        if (initialSelection != null) {
            cueList.getSelectionModel().select(initialSelection);
            loadCue.accept(initialSelection);
        } else {
            clearForm.run();
            refreshTypeUi.run();
        }

        javafx.scene.layout.GridPane form = new javafx.scene.layout.GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.setPadding(new Insets(8, 8, 4, 8));
        form.add(makeToolbarLabel("Cue Type"), 0, 0);
        form.add(new HBox(8, cbTypePreset, tfType), 1, 0);
        form.add(makeToolbarLabel("Time"), 0, 1);
        form.add(tfTime, 1, 1);
        form.add(makeToolbarLabel("Target"), 0, 2);
        form.add(tfTarget, 1, 2);
        form.add(lblValue, 0, 3);
        form.add(tfValue, 1, 3);
        form.add(lblPath, 0, 4);
        form.add(tfPath, 1, 4);
        form.add(lblPosition, 0, 5);
        form.add(tfPosition, 1, 5);

        VBox body = new VBox(
            8,
            form,
            lblTypeHint,
            makeToolbarLabel("DSL Preview"),
            taDslPreview,
            makeToolbarLabel("Timeline Event Cues"),
            cueList,
            makeToolbarLabel("Extra Payload"),
            taExtra
        );
        body.setPadding(new Insets(0, 8, 8, 8));

        overlayDialog.showDialog(
            "Timeline Event Cues",
            "Author discrete sprite swaps, show/hide beats, and scene changes with a live DSL preview.",
            body,
            ActionEditorDialogOverlay.ActionSpec.neutral("Close", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("New Cue", ActionEditorDialogOverlay.ButtonStyle.NEUTRAL, clearForm),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Save Cue", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
                double timeMs;
                try {
                    timeMs = Math.max(0.0, Double.parseDouble(tfTime.getText().trim()));
                } catch (NumberFormatException ex) {
                    tfTime.requestFocus();
                    return;
                }

                String preset = cbTypePreset.getValue() == null ? "expression" : cbTypePreset.getValue();
                String type = "custom".equals(preset) ? tfType.getText().trim() : preset;
                if (type.isBlank()) {
                    tfType.requestFocus();
                    return;
                }

                String target = tfTarget.getText() == null ? "" : tfTarget.getText().trim();
                String value = tfValue.getText() == null ? "" : tfValue.getText().trim();
                String path = tfPath.getText() == null ? "" : tfPath.getText().trim();
                String position = tfPosition.getText() == null ? "" : tfPosition.getText().trim();
                Map<String, String> payload = buildEventCuePayload(
                    type,
                    target,
                    value,
                    path,
                    position,
                    parsePayloadLines(taExtra.getText()),
                    true);

                EditorEventCue selected = cueList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    project.removeEditorEventCue(selected);
                    selected.setTimeMs(timeMs);
                    selected.setType(type);
                    selected.getPayload().clear();
                    selected.getPayload().putAll(payload);
                    project.addEditorEventCue(selected);
                } else {
                    selected = new EditorEventCue(timeMs, type, payload);
                    project.addEditorEventCue(selected);
                }

                refreshList.run();
                cueList.getSelectionModel().select(selected);
                timelinePanel.refresh();
                updatePreview();
                refreshExportPreviewAndMarkDirty();
            }),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Delete Cue", ActionEditorDialogOverlay.ButtonStyle.DANGER, () -> {
                EditorEventCue selected = cueList.getSelectionModel().getSelectedItem();
                if (selected == null) return;
                project.removeEditorEventCue(selected);
                refreshList.run();
                clearForm.run();
                refreshTypeUi.run();
                timelinePanel.refresh();
                updatePreview();
                refreshExportPreviewAndMarkDirty();
            })
        );
    }

    private Map<String, String> buildExpressionCuePayload(String target,
                                                          String expression,
                                                          String directPath,
                                                          boolean embedResolved) {
        Map<String, String> payload = new LinkedHashMap<>();
        if (target != null && !target.isBlank()) {
            payload.put("target", target.trim());
        }
        if (expression != null && !expression.isBlank()) {
            payload.put("value", expression.trim());
        }
        if (directPath != null && !directPath.isBlank()) {
            payload.put("path", directPath.trim());
        }
        if (embedResolved) {
            enrichExpressionPayload("expression", payload, target, expression, directPath);
        }
        return payload;
    }

    private Map<String, String> buildEventCuePayload(String type,
                                                     String target,
                                                     String value,
                                                     String path,
                                                     String position,
                                                     Map<String, String> extraPayload,
                                                     boolean enrichResolvedExpression) {
        Map<String, String> payload = extraPayload == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(extraPayload);
        String normalizedType = type == null ? "" : type.trim();
        String typeKey = normalizedType.toLowerCase(Locale.ROOT);
        String cleanTarget = target == null ? "" : target.trim();
        String cleanValue = value == null ? "" : value.trim();
        String cleanPath = path == null ? "" : path.trim();
        String cleanPosition = position == null ? "" : position.trim();

        if (!cleanTarget.isBlank()) payload.put("target", cleanTarget);

        switch (typeKey) {
            case "expression" -> {
                if (!cleanValue.isBlank()) payload.put("value", cleanValue);
                if (!cleanPath.isBlank()) payload.put("path", cleanPath);
                if (!cleanPosition.isBlank()) payload.put("position", cleanPosition);
            }
            case "show" -> {
                if (!cleanValue.isBlank()) payload.put("expression", cleanValue);
                if (!cleanPath.isBlank()) payload.put("path", cleanPath);
                if (!cleanPosition.isBlank()) payload.put("position", cleanPosition);
            }
            case "replace" -> {
                if (!cleanValue.isBlank()) payload.put("expression", cleanValue);
                if (!cleanPath.isBlank()) payload.put("path", cleanPath);
            }
            case "scene" -> {
                if (!cleanValue.isBlank()) payload.put("id", cleanValue);
                if (!cleanPath.isBlank()) payload.put("path", cleanPath);
            }
            case "dialogue_marker" -> {
                if (!cleanValue.isBlank()) payload.put("id", cleanValue);
            }
            case "script_call" -> {
                if (!cleanValue.isBlank()) payload.put("name", cleanValue);
                if (!cleanPath.isBlank()) payload.put("arg", cleanPath);
            }
            default -> {
                if (!cleanValue.isBlank()) payload.putIfAbsent("value", cleanValue);
                if (!cleanPath.isBlank()) payload.putIfAbsent("path", cleanPath);
                if (!cleanPosition.isBlank()) payload.putIfAbsent("position", cleanPosition);
            }
        }
        if (enrichResolvedExpression) {
            enrichExpressionPayload(typeKey, payload, cleanTarget, cleanValue, cleanPath);
        }
        return payload;
    }

    private static double parseNonNegativeDoubleOr(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return Math.max(0.0, fallback);
        try {
            return Math.max(0.0, Double.parseDouble(raw.trim()));
        } catch (NumberFormatException ex) {
            return Math.max(0.0, fallback);
        }
    }

    private static Map<String, String> parsePayloadLines(String raw) {
        Map<String, String> payload = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return payload;
        for (String line : raw.split("\\r?\\n")) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if (key.isEmpty()) continue;
            payload.put(key, value);
        }
        return payload;
    }

    private static String formatPayloadLines(Map<String, String> payload) {
        if (payload == null || payload.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) continue;
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(entry.getKey()).append('=').append(entry.getValue() == null ? "" : entry.getValue());
        }
        return sb.toString();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second == null ? "" : second;
    }

    private void refreshAudioLibraryList(ListView<PuppeteerAudioLibrary.AudioEntry> listView,
                                         List<PuppeteerAudioLibrary.AudioEntry> allEntries,
                                         String filterText) {
        if (listView == null) return;
        String query = filterText == null ? "" : filterText.trim().toLowerCase(Locale.ROOT);
        listView.getItems().clear();
        for (PuppeteerAudioLibrary.AudioEntry entry : allEntries) {
            if (entry == null) continue;
            if (query.isEmpty() || entry.relativePath().toLowerCase(Locale.ROOT).contains(query)) {
                listView.getItems().add(entry);
            }
        }
    }

    private void playAudioPreview(String rawPath) {
        stopAudioPreview();
        String uri = resolveAudioPreviewUri(rawPath);
        if (uri == null || uri.isBlank()) {
            if (statusBar != null) statusBar.setText("Audio preview not found");
            return;
        }
        try {
            audioPreviewPlayer = new MediaPlayer(new Media(uri));
            audioPreviewPlayer.setOnError(() -> {
                if (statusBar != null) statusBar.setText("Audio preview failed");
                stopAudioPreview();
            });
            audioPreviewPlayer.play();
        } catch (Exception ex) {
            if (statusBar != null) statusBar.setText("Audio preview failed: " + ex.getMessage());
            stopAudioPreview();
        }
    }

    private void stopAudioPreview() {
        if (audioPreviewPlayer == null) return;
        try {
            audioPreviewPlayer.stop();
            audioPreviewPlayer.dispose();
        } catch (Exception ignored) {
        }
        audioPreviewPlayer = null;
    }

    private String resolveAudioPreviewUri(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isBlank()) return null;
        File direct = new File(path);
        if (direct.isFile()) return direct.toURI().toString();
        if (projectRoot != null) {
            File fromRoot = new File(projectRoot, path.replace('\\', '/'));
            if (fromRoot.isFile()) return fromRoot.toURI().toString();
        }
        return null;
    }

    public void requestWindowClose() {
        if (!dirty && !previewStaged) {
            stopAudioPreview();
            closeNow();
            return;
        }
        String message = previewStaged
            ? "A staged code preview is active. Save & Register keeps the staged changes; Discard closes without them."
            : "Save animation changes before closing Puppeteer?";
        overlayDialog.showDialog(
            "Unsaved Animation",
            message,
            null,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.danger("Discard", this::closeNow),
            ActionEditorDialogOverlay.ActionSpec.accent("Save & Register", () -> {
                requestRegisterTimeline(this::closeNow);
            })
        );
    }

    private void closeNow() {
        // Hide the window FIRST so the user doesn't see a frozen editor while we clean up.
        // Each cleanup step is best-effort and isolated; a failure in any one step must not
        // prevent the rest from running, and must not block the JavaFX Application Thread.
        bypassCloseConfirmation = true;
        try { stopAudioPreview(); } catch (Exception ignored) {}
        try { if (previewRecorder != null) previewRecorder.cancel(); } catch (Exception ignored) {}
        try {
            if (assetImporterWindow != null) {
                assetImporterWindow.close();
                assetImporterWindow = null;
                assetImporterPanel = null;
            }
        } catch (Exception ignored) {}

        // Snapshot divider positions on the FX thread (Node properties are FX-only),
        // but defer the actual disk write to the background.
        PuppeteerWorkspacePrefs prefsSnapshot = null;
        try {
            if (workspacePrefs != null) {
                captureWorkspacePrefsInto(workspacePrefs);
                prefsSnapshot = workspacePrefs;
            }
        } catch (Exception ignored) {}

        // Hand off the draft store; null out the field immediately so no more save tasks
        // can be scheduled by listeners that fire during close.
        PuppeteerDraftStore draftSnapshot = draftStore;
        draftStore = null;

        try { close(); } catch (Exception ignored) {}

        // Background best-effort persistence — never blocks the FX thread.
        final PuppeteerWorkspacePrefs prefsToSave = prefsSnapshot;
        final PuppeteerDraftStore draftsToFlush = draftSnapshot;
        final java.io.File anchorRoot = projectRoot;
        final AnimationProject anchorProject = project;
        if (prefsToSave != null || draftsToFlush != null || anchorRoot != null) {
            Thread cleanup = new Thread(() -> {
                try { if (prefsToSave != null) prefsToSave.save(); } catch (Throwable ignored) {}
                try { if (draftsToFlush != null) draftsToFlush.shutdown(); } catch (Throwable ignored) {}
                try { PuppeteerAnchorStore.save(anchorRoot, anchorProject); } catch (Throwable ignored) {}
                try { VnEyeFocusProfileStore.save(anchorRoot, anchorProject.getEyeFocusProfilesView().values()); } catch (Throwable ignored) {}
                try { PuppeteerRigStore.save(anchorRoot, anchorProject); } catch (Throwable ignored) {}
            }, "puppeteer-close-cleanup");
            cleanup.setDaemon(true);
            cleanup.start();
        }
    }

    /** Copy the current SplitPane divider positions and camera state into the prefs object (no IO). */
    private void captureWorkspacePrefsInto(PuppeteerWorkspacePrefs prefs) {
        if (prefs == null) return;
        if (rootWorkspaceSplit != null && !rootWorkspaceSplit.getDividers().isEmpty() && topToolbarVisible) {
            toolbarDividerPosition = clampToolbarDivider(
                rootWorkspaceSplit.getDividerPositions()[0],
                true
            );
        }
        prefs.setDivider(PuppeteerWorkspacePrefs.DIVIDER_TOOLBAR, toolbarDividerPosition);
        prefs.setBoolean(PuppeteerWorkspacePrefs.KEY_TOP_TOOLBAR_VISIBLE, topToolbarVisible);
        prefs.setString(PuppeteerWorkspacePrefs.KEY_TOOLBAR_LAYOUT_MODE, getToolbarLayoutMode().name());
        prefs.setDouble(PuppeteerWorkspacePrefs.KEY_UI_SCALE, uiScale);
        captureDockLayoutPrefsInto(prefs);
        if (!previewFocusMode) {
            rememberWorkspaceDividerPositions();
        }
        prefs.setDivider(PuppeteerWorkspacePrefs.DIVIDER_TOP, topWorkspaceDividerPosition);
        prefs.setDivider(PuppeteerWorkspacePrefs.DIVIDER_BOTTOM, bottomWorkspaceDividerPosition);
        prefs.setDivider(PuppeteerWorkspacePrefs.DIVIDER_CONTENT, workspaceContentDividerPosition);
        prefs.setDivider(PuppeteerWorkspacePrefs.DIVIDER_CODE_PANE, codePaneDividerPosition);
        if (previewFocusSplit != null && !previewFocusSplit.getDividers().isEmpty()) {
            previewFocusDividerPosition = readDividerPosition(previewFocusSplit, previewFocusDividerPosition);
        }
        prefs.setDivider(PuppeteerWorkspacePrefs.DIVIDER_PREVIEW_FOCUS, previewFocusDividerPosition);
        if (animationPreview != null) {
            prefs.setDouble(PuppeteerWorkspacePrefs.KEY_VIEWPORT_PAN_X, animationPreview.getViewPanX());
            prefs.setDouble(PuppeteerWorkspacePrefs.KEY_VIEWPORT_PAN_Y, animationPreview.getViewPanY());
            prefs.setDouble(PuppeteerWorkspacePrefs.KEY_VIEWPORT_ZOOM, animationPreview.getViewZoomFactor());
        }
        if (project != null) {
            prefs.setDouble(PuppeteerWorkspacePrefs.KEY_TIMELINE_PLAYHEAD, project.getPlayheadMs());
        }
    }

    public PuppeteerCommand.Stack getCommandStack() { return commandStack; }

    // --- Toolbar styling helpers ---

    private static final String STYLE_BTN_DARK =
        "-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-background-radius: 4; " +
        "-fx-border-color: #3a3a3a; -fx-border-radius: 4; -fx-padding: 4 10; -fx-font-size: 11px; -fx-cursor: hand;";
    private static final String STYLE_BTN_ACCENT =
        "-fx-background-color: #4da3ff; -fx-text-fill: #0a0a0a; -fx-background-radius: 4; " +
        "-fx-border-color: #5bb3ff; -fx-border-radius: 4; -fx-padding: 4 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String STYLE_BTN_GREEN =
        "-fx-background-color: #58d68d; -fx-text-fill: #0a0a0a; -fx-background-radius: 4; " +
        "-fx-border-color: #68e69d; -fx-border-radius: 4; -fx-padding: 4 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String STYLE_TEXT_FIELD =
        "-fx-background-color: #1a1a1a; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; " +
        "-fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 3 6; -fx-font-size: 11px;";
    private static final String STYLE_SIDEBAR_CARD =
        "-fx-background-color: #151515; -fx-border-color: #2f2f2f; -fx-border-radius: 8; "
            + "-fx-background-radius: 8; -fx-padding: 10 12;";
    private static final String STYLE_SIDEBAR_CARD_TITLE =
        "-fx-text-fill: #efefef; -fx-font-size: 12px; -fx-font-weight: bold;";
    private static final String STYLE_SIDEBAR_META_LABEL =
        "-fx-text-fill: #8c8c8c; -fx-font-size: 10px; -fx-font-weight: bold;";
    private static final String STYLE_SIDEBAR_VALUE_LABEL =
        "-fx-text-fill: #f0f0f0; -fx-font-size: 12px;";

    private static Button makeToolbarIconButton(javafx.scene.layout.Region iconClass, String tooltip) {
        Button btn = new Button();
        btn.getStyleClass().add("puppeteer-toolbar-icon-button");
        btn.setText("");
        btn.setGraphic(iconClass);
        installToolbarTooltip(btn, tooltip);
        btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btn.setGraphicTextGap(0);
        btn.setMnemonicParsing(false);
        btn.setMinSize(36, 32);
        btn.setPrefSize(36, 32);
        btn.setMaxSize(36, 32);
        btn.setFocusTraversable(false);
        return btn;
    }

    private static com.jvn.editor.ui.PuppeteerAeroIcon puppeteerAeroIcon(
        com.jvn.editor.ui.PuppeteerAeroIcon.Kind kind) {
        return com.jvn.editor.ui.PuppeteerAeroIcon.of(kind, 23);
    }

    private static Button makeToolbarSuccessIconButton(javafx.scene.layout.Region iconClass, String tooltip) {
        Button btn = makeToolbarIconButton(iconClass, tooltip);
        btn.getStyleClass().add("puppeteer-toolbar-icon-button-success");
        return btn;
    }

    private static ToggleButton makeToolbarIconToggle(javafx.scene.layout.Region iconClass, String tooltip) {
        ToggleButton toggle = new ToggleButton();
        toggle.getStyleClass().add("puppeteer-toolbar-icon-toggle");
        toggle.setText("");
        toggle.setGraphic(iconClass);
        installToolbarTooltip(toggle, tooltip);
        toggle.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        toggle.setGraphicTextGap(0);
        toggle.setMnemonicParsing(false);
        toggle.setMinSize(36, 32);
        toggle.setPrefSize(36, 32);
        toggle.setMaxSize(36, 32);
        toggle.setFocusTraversable(false);
        return toggle;
    }

    private static void installToolbarTooltip(ButtonBase control, String tooltipText) {
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setShowDelay(Duration.millis(220));
        tooltip.setShowDuration(Duration.seconds(8));
        tooltip.setHideDelay(Duration.millis(80));
        control.setTooltip(tooltip);
        control.setAccessibleText(tooltipText);
        control.setAccessibleHelp(tooltipText);
    }

    private static Label makeToolbarIcon(String iconClass) {
        Label icon = new Label();
        icon.getStyleClass().addAll("icon", "puppeteer-toolbar-icon", iconClass);
        icon.setMouseTransparent(true);
        return icon;
    }

    private static Label makeToolbarLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
        return lbl;
    }

    private static RadioButton buildChannelButton(String channel, ToggleGroup group, boolean selected) {
        RadioButton button = new RadioButton(channel);
        button.setToggleGroup(group);
        button.setSelected(selected);
        button.setStyle("-fx-text-fill: #d7d7d7; -fx-font-size: 11px;");
        return button;
    }

    private Button buildOverlayMenuButton(String label, Runnable action) {
        Button button = new Button(label);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle(
            "-fx-background-color: #232323; -fx-text-fill: #d7d7d7; -fx-background-radius: 4; "
                + "-fx-border-color: #444444; -fx-border-radius: 4; -fx-padding: 7 10; -fx-font-size: 11px; -fx-cursor: hand;");
        button.setOnAction(event -> {
            overlayDialog.hideOverlay();
            if (action != null) {
                action.run();
            }
            event.consume();
        });
        return button;
    }

    private CollapsibleToolbarCluster registerToolbarCluster(String key, String title, Node... rows) {
        VBox content = new VBox(6);
        content.setAlignment(Pos.CENTER_LEFT);
        for (Node row : rows) {
            if (row != null) {
                content.getChildren().add(row);
            }
        }
        String stableKey = "toolbar-" + (key == null || key.isBlank() ? title : key).trim().toLowerCase(Locale.ROOT);
        CollapsibleToolbarCluster cluster = new CollapsibleToolbarCluster(stableKey, title, content);
        if (key != null && !key.isBlank()) {
            toolbarClusters.put(key.trim().toLowerCase(Locale.ROOT), cluster);
        }
        toolbarClusters.put(cluster.getClusterKey(), cluster);
        installToolbarDockHandlers(cluster);
        return cluster;
    }

    private static Region makeSpacer(double width) {
        Region r = new Region();
        r.setMinWidth(width);
        r.setPrefWidth(width);
        return r;
    }

    private record ClipLibraryEntry(
        Path file,
        String relativePath,
        String folderLabel,
        AnimationClip clip
    ) {
        String displayName() {
            return clip != null ? clip.getName() : file.getFileName().toString();
        }
    }

    public boolean hasSavedClips() {
        return !scanClipLibrary().isEmpty();
    }

    public void saveSelectionAsClip() {
        EntityTrack track = selectedTrackForEditing(false);
        if (track == null) return;
        double start = project.hasLoopRegion() ? project.getLoopStartMs() : 0;
        double end = project.hasLoopRegion() ? project.getLoopEndMs() : project.getTotalDurationMs();

        AnimationClip clip = new AnimationClip(track.getEntityName() + "_clip");
        clip.captureFromTrack(track, start, end);
        if (clip.getChannels().isEmpty()) return;

        if (projectRoot == null) {
            showSaveError(clip.getName(), "No project root set.");
            return;
        }

        TextField clipPathField = new TextField(clip.getName());
        clipPathField.setPromptText("motion/hero_enter");
        clipPathField.setStyle(STYLE_TEXT_FIELD);

        Label summary = makeToolbarLabel(
            String.format(
                "Range %.0fms → %.0fms  •  %.0fms  •  %d animated channel(s)",
                start,
                end,
                clip.getDurationMs(),
                clip.getChannels().size()
            )
        );
        summary.setWrapText(true);

        Label hint = makeToolbarLabel("Use nested paths to organize clips into folders under config/puppeteer/clips.");
        hint.setWrapText(true);

        VBox content = new VBox(8,
            makeToolbarLabel("Clip Path"),
            clipPathField,
            summary,
            hint
        );
        content.setPadding(new Insets(4, 4, 0, 4));

        overlayDialog.showDialog(
            "Save Clip",
            "Save the selected track range as a reusable clip.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Save", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
                Path clipsRoot = projectRoot.toPath().resolve("config").resolve("puppeteer").resolve("clips").normalize();
                Path clipFile = resolveClipFile(clipsRoot, clipPathField.getText());
                if (clipFile == null) {
                    clipPathField.requestFocus();
                    return;
                }
                try {
                    clip.setName(stripClipExtension(clipFile.getFileName().toString()));
                    clip.saveTo(clipFile);
                    setTitle("Puppeteer - saved clip '" + clipFile.getFileName() + "'");
                    overlayDialog.hideOverlay();
                } catch (IOException ex) {
                    showSaveError(clip.getName(), ex.getMessage());
                }
            })
        );
    }

    public void loadAndApplyClip() {
        EntityTrack track = selectedTrackForEditing(true);
        if (track == null || projectRoot == null) return;
        List<ClipLibraryEntry> entries = scanClipLibrary();
        if (entries.isEmpty()) return;

        TextField filterField = new TextField();
        filterField.setPromptText("Filter clips...");
        filterField.setStyle(STYLE_TEXT_FIELD);

        ListView<ClipLibraryEntry> clipList = new ListView<>();
        clipList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ClipLibraryEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label name = new Label(item.displayName());
                name.setStyle("-fx-text-fill: #f0f0f0; -fx-font-size: 12px; -fx-font-weight: bold;");
                Label meta = new Label(item.folderLabel().isBlank()
                    ? clipMetaLine(item.clip())
                    : item.folderLabel() + "  •  " + clipMetaLine(item.clip()));
                meta.setStyle("-fx-text-fill: #8c8c8c; -fx-font-size: 10px;");
                VBox box = new VBox(2, name, meta);
                box.setMinWidth(0);
                setGraphic(box);
                setText(null);
            }
        });
        clipList.setPrefHeight(280);
        clipList.setMinHeight(180);
        clipList.setStyle("-fx-background-color: #14171d; -fx-control-inner-background: #14171d;");

        Label selectionMeta = makeToolbarLabel("");
        selectionMeta.setWrapText(true);
        Label selectionProps = makeToolbarLabel("");
        selectionProps.setWrapText(true);

        TextField durationScaleField = new TextField("1.00");
        durationScaleField.setStyle(STYLE_TEXT_FIELD);
        durationScaleField.setPrefColumnCount(5);

        ComboBox<String> applyModeBox = new ComboBox<>();
        applyModeBox.getItems().setAll("Layer On Top", "Replace Range");
        applyModeBox.setValue("Layer On Top");
        applyModeBox.setStyle(STYLE_TEXT_FIELD);

        CheckBox reverseClipCheck = new CheckBox("Reverse motion");
        reverseClipCheck.setTooltip(new Tooltip("Apply the clip backwards without modifying the saved clip."));
        reverseClipCheck.setStyle("-fx-text-fill: #d0d0d0;");

        Runnable refreshClipFilter = () -> {
            String query = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
            clipList.getItems().clear();
            for (ClipLibraryEntry entry : entries) {
                if (query.isBlank()
                    || entry.displayName().toLowerCase(Locale.ROOT).contains(query)
                    || entry.relativePath().toLowerCase(Locale.ROOT).contains(query)) {
                    clipList.getItems().add(entry);
                }
            }
            if (!clipList.getItems().isEmpty() && clipList.getSelectionModel().getSelectedItem() == null) {
                clipList.getSelectionModel().select(0);
            }
        };
        filterField.textProperty().addListener((obs, oldValue, newValue) -> refreshClipFilter.run());
        refreshClipFilter.run();

        clipList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || newValue.clip() == null) {
                selectionMeta.setText("");
                selectionProps.setText("");
                return;
            }
            selectionMeta.setText("Target: " + track.getEntityName() + "  •  " + clipMetaLine(newValue.clip()));
            selectionProps.setText("Channels: " + clipPropertySummary(newValue.clip()));
        });

        HBox options = new HBox(10,
            new VBox(4, makeToolbarLabel("Scale"), durationScaleField),
            new VBox(4, makeToolbarLabel("Apply Mode"), applyModeBox),
            new VBox(4, makeToolbarLabel("Transform"), reverseClipCheck)
        );
        VBox preview = new VBox(6,
            makeToolbarLabel("Clip Preview"),
            selectionMeta,
            selectionProps
        );
        preview.setStyle(STYLE_SIDEBAR_CARD);

        VBox content = new VBox(10,
            filterField,
            clipList,
            preview,
            options
        );
        content.setPadding(new Insets(4, 4, 0, 4));

        overlayDialog.showDialog(
            "Load Clip",
            "Apply a saved clip to '" + track.getEntityName() + "' at playhead.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Apply", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
                ClipLibraryEntry selectedClip = clipList.getSelectionModel().getSelectedItem();
                if (selectedClip == null || selectedClip.clip() == null) {
                    clipList.requestFocus();
                    return;
                }
                double scale;
                try {
                    scale = Math.max(0.05, Double.parseDouble(durationScaleField.getText().trim()));
                } catch (Exception ex) {
                    durationScaleField.requestFocus();
                    return;
                }
                boolean replaceRange = "Replace Range".equals(applyModeBox.getValue());
                try {
                    AnimationClip clip = reverseClipCheck.isSelected()
                        ? selectedClip.clip().reversed()
                        : selectedClip.clip();
                    applyClipToTrack(clip, track, project.getPlayheadMs(), scale, replaceRange);
                    timelinePanel.refresh();
                    refreshExportPreviewAndMarkDirty();
                    overlayDialog.hideOverlay();
                } catch (Exception ex) {
                    showOverlayError("Clip Load Failed", "Could not apply clip '" + selectedClip.displayName() + "'", ex.getMessage());
                }
            })
        );
    }

    private List<ClipLibraryEntry> scanClipLibrary() {
        if (projectRoot == null || !projectRoot.isDirectory()) return List.of();
        Path clipsRoot = projectRoot.toPath().resolve("config").resolve("puppeteer").resolve("clips").normalize();
        if (!Files.isDirectory(clipsRoot)) return List.of();

        List<ClipLibraryEntry> entries = new ArrayList<>();
        try (var paths = Files.walk(clipsRoot)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".clip"))
                .forEach(path -> {
                    try {
                        AnimationClip clip = AnimationClip.loadFrom(path);
                        String relative = clipsRoot.relativize(path).toString().replace('\\', '/');
                        String folderLabel = "";
                        int slash = relative.lastIndexOf('/');
                        if (slash >= 0) {
                            folderLabel = relative.substring(0, slash);
                        }
                        entries.add(new ClipLibraryEntry(path, relative, folderLabel, clip));
                    } catch (Exception ignored) {
                    }
                });
        } catch (IOException ignored) {
            return List.of();
        }
        entries.sort((left, right) -> left.relativePath().compareToIgnoreCase(right.relativePath()));
        return entries;
    }

    private static String clipMetaLine(AnimationClip clip) {
        if (clip == null) return "Invalid clip";
        return String.format(
            "%.0fms  •  %d channel(s)  •  %d key(s)",
            clip.getDurationMs(),
            clip.getChannels().size(),
            clip.getKeyframeCount()
        );
    }

    private static String clipPropertySummary(AnimationClip clip) {
        if (clip == null || clip.getChannels().isEmpty()) return "No animated channels";
        List<String> labels = new ArrayList<>();
        for (PropertyType property : clip.getChannels().keySet()) {
            if (property != null) {
                labels.add(property.getDisplayName());
            }
        }
        return String.join(", ", labels);
    }

    private void applyClipToTrack(
        AnimationClip clip,
        EntityTrack track,
        double insertTimeMs,
        double durationScale,
        boolean replaceRange
    ) {
        if (clip == null || track == null) return;
        double scale = Math.max(0.05, durationScale);
        if (replaceRange) {
            double endTimeMs = insertTimeMs + clip.getDurationMs() * scale;
            for (PropertyType property : clip.getChannels().keySet()) {
                List<Keyframe> existing = new ArrayList<>(track.getKeyframes(property));
                for (Keyframe keyframe : existing) {
                    double time = keyframe.getTimeMs();
                    if (time >= insertTimeMs - 0.001 && time <= endTimeMs + 0.001) {
                        track.removeKeyframe(property, keyframe);
                    }
                }
            }
        }
        clip.applyToTrack(track, insertTimeMs, scale);
    }

    private static Path resolveClipFile(Path clipsRoot, String rawPath) {
        if (clipsRoot == null || rawPath == null) return null;
        String normalized = sanitizeClipRelativePath(rawPath);
        if (normalized.isBlank()) return null;
        Path relative = Path.of(normalized).normalize();
        if (relative.isAbsolute()) return null;
        if (relative.startsWith("..")) return null;
        Path file = clipsRoot.resolve(relative + ".clip").normalize();
        return file.startsWith(clipsRoot) ? file : null;
    }

    private static String sanitizeClipRelativePath(String rawPath) {
        String normalized = rawPath == null ? "" : rawPath.trim().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.endsWith(".clip")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        return normalized.trim();
    }

    private static String stripClipExtension(String fileName) {
        if (fileName == null) return "";
        return fileName.endsWith(".clip")
            ? fileName.substring(0, fileName.length() - 5)
            : fileName;
    }

    private void placeEntityAtSlot(VnSlotHelper.Slot slot) {
        String entityName = timelinePanel.getSelectedEntity();
        if (entityName == null || entityName.isBlank()) return;
        double viewportW = animationPreview.getWidth() > 0 ? animationPreview.getWidth() : 1280;
        double viewportH = animationPreview.getHeight() > 0 ? animationPreview.getHeight() : 720;
        double time = project.getPlayheadMs();
        EntityTrack track = project.getOrCreateTrack(entityName);
        commandStack.execute(PuppeteerCommand.composite(
            "Place entity at VN slot",
            List.of(
                PuppeteerCommand.upsertKeyframe(track, PropertyType.X, time, VnSlotHelper.slotX(slot, viewportW)),
                PuppeteerCommand.upsertKeyframe(track, PropertyType.Y, time, VnSlotHelper.baselineY(viewportH))
            )
        ));
        timelinePanel.refresh();
        updatePreview();
        refreshExportPreviewAndMarkDirty();
    }

    public KeyframeSelectionModel getSelectionModel() { return selectionModel; }

    public void showRuntimeVerificationReport() {
        ResolvedCharacterContext rigContext = resolveCharacterForDiagnostics();
        List<TimelineDiagnostic.Message> findings = PuppeteerVerification.diagnose(
            project,
            knownSceneEntities(),
            projectRoot,
            PuppeteerVerification.Mode.REGISTER_RUNTIME,
            rigContext.character(),
            rigContext.characterId()
        );
        boolean hasErrors = findings.stream().anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.ERROR);
        boolean hasWarnings = findings.stream().anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.WARNING);

        if (findings.isEmpty()) {
            findings = List.of(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.INFO,
                "(timeline)",
                "No runtime registration issues found",
                "This timeline is ready to register with the current project state"
            ));
        }

        if (hasErrors) {
            showVerificationOverlay(
                "Runtime Verification (Errors Found)",
                "Puppeteer checked this timeline and found errors blocking registration.",
                findings,
                null,
                null
            );
        } else if (hasWarnings) {
            showVerificationOverlay(
                "Runtime Verification (Warnings Found)",
                "Puppeteer checked this timeline and found potential issues. Proceed with registration?",
                findings,
                "Register Anyway",
                () -> requestRegisterTimeline() 
            );
        } else {
            showVerificationOverlay(
                "Runtime Verification Passed",
                "Puppeteer checked this timeline. It is ready for runtime registration.",
                findings,
                "Register Now",
                () -> requestRegisterTimeline()
            );
        }
    }

    public void requestRegisterTimeline() {
        requestRegisterTimeline(null);
    }

    public void requestRegisterTimeline(Runnable onSuccess) {
        String name = tfTimelineName.getText().trim();
        List<TimelineDiagnostic.Message> nameFindings = PuppeteerVerification.validateTimelineName(name);
        boolean hasNameErrors = nameFindings.stream()
            .anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.ERROR);
        if (hasNameErrors) {
            showVerificationOverlay(
                "Registration Blocked",
                "Puppeteer found timeline name problems. Fix the name before registering.",
                nameFindings,
                null,
                null
            );
            return;
        }
        project.setName(name);
        ResolvedCharacterContext rigContext = resolveCharacterForDiagnostics();
        List<TimelineDiagnostic.Message> findings = PuppeteerVerification.diagnose(
            project,
            knownSceneEntities(),
            projectRoot,
            PuppeteerVerification.Mode.REGISTER_RUNTIME,
            rigContext.character(),
            rigContext.characterId()
        );
        boolean hasErrors = findings.stream().anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.ERROR);
        boolean hasWarnings = findings.stream().anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.WARNING);

        if (hasErrors) {
            showVerificationOverlay(
                "Registration Blocked",
                "Puppeteer found runtime registration errors. Fix them before registering this timeline.",
                findings,
                null,
                null
            );
            return;
        }
        showRegistrationActionPreview(name, findings, onSuccess, hasWarnings);
    }

    private void showRegistrationActionPreview(
        String name,
        List<TimelineDiagnostic.Message> findings,
        Runnable onSuccess,
        boolean hasWarnings
    ) {
        String filePath = Optional.ofNullable(resolveRegisteredJesFile(name))
            .map(File::getAbsolutePath)
            .orElse("scripts/timelines/" + name + ".jes");
        String backupPath = projectRoot != null
            ? projectRoot.toPath()
                .resolve("scripts")
                .resolve("timelines")
                .resolve(".backups")
                .resolve(name + ".jes.bak")
                .toString()
            : "scripts/timelines/.backups/" + name + ".jes.bak";

        List<String> details = new ArrayList<>();
        details.add("Validate the timeline name and runtime registration diagnostics.");
        details.add("Capture current scene entity snapshots so Puppeteer can reopen the animation with the same visible scene context.");
        details.add("Convert the visual timeline to TimelineData named \"" + name + "\".");
        details.add("Generate a named JES export with VNS usage comments and Puppeteer metadata comments.");
        details.add("Write the file to: " + filePath);
        details.add("If that file already exists, write a backup to: " + backupPath);
        details.add("Save Puppeteer anchor/orbit metadata for this project.");
        details.add("Register the TimelineData in TimelineRegistry for immediate runtime/VNS playback in this editor session.");
        details.add("Mark the animation clean, remove its autosave draft, and add it to Puppeteer's recent registered timelines.");
        if (previewStaged) {
            details.add("Keep the currently staged code-preview model as the saved registered model.");
        }
        if (onSuccess != null) {
            details.add("Run the requested follow-up action after registration succeeds, such as closing this Puppeteer window.");
        }
        details.add("This will not insert or edit any .vns script line; reference it manually with @external jes_timeline " + name + " when needed.");

        boolean hasOrbitPivotRisk = findings.stream()
            .anyMatch(message -> message.description().contains(PuppeteerVerification.ORBIT_PIVOT_RISK_MARKER));
        boolean hasUnresolvedLayerProxies = findings.stream()
            .anyMatch(message -> message.description().contains(PuppeteerVerification.UNRESOLVED_LAYER_PROXY_MARKER));

        VBox body = new VBox(10);
        body.setFillWidth(true);
        body.getChildren().add(buildExportSummaryContent(name));
        body.getChildren().add(buildActionDetailsContent(details));
        if (hasWarnings) {
            Label warningHeader = new Label("Warnings that will be accepted if you continue:");
            warningHeader.setStyle("-fx-text-fill: #ffe2a8; -fx-font-size: 11px; -fx-font-weight: bold;");
            body.getChildren().add(warningHeader);
            body.getChildren().add(buildVerificationContent(findings));
        }

        javafx.scene.control.CheckBox strictOrbitCheck = null;
        if (hasOrbitPivotRisk) {
            strictOrbitCheck = new javafx.scene.control.CheckBox(
                "Treat orbit-pivot warnings as blocking for this registration");
            strictOrbitCheck.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 11px;");
            body.getChildren().add(strictOrbitCheck);
        }
        javafx.scene.control.CheckBox strictCheckRef = strictOrbitCheck;

        javafx.scene.control.CheckBox strictUnresolvedLayerCheck = null;
        if (hasUnresolvedLayerProxies) {
            strictUnresolvedLayerCheck = new javafx.scene.control.CheckBox(
                "Treat unresolved-layer warnings as blocking for this registration");
            strictUnresolvedLayerCheck.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 11px;");
            body.getChildren().add(strictUnresolvedLayerCheck);
        }
        javafx.scene.control.CheckBox strictUnresolvedLayerCheckRef = strictUnresolvedLayerCheck;

        Label playbackHeader = new Label("Timeline playback:");
        playbackHeader.setStyle("-fx-text-fill: #d8eefc; -fx-font-size: 11px; -fx-font-weight: bold;");
        javafx.scene.control.ToggleGroup playbackGroup = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.RadioButton nonBlockingOption = new javafx.scene.control.RadioButton(
            "Non-blocking - dialogue can continue during playback");
        nonBlockingOption.setToggleGroup(playbackGroup);
        nonBlockingOption.setSelected(true);
        nonBlockingOption.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 11px;");
        javafx.scene.control.RadioButton blockingOption = new javafx.scene.control.RadioButton(
            "Blocking - VNS waits for the timeline to finish before advancing");
        blockingOption.setToggleGroup(playbackGroup);
        blockingOption.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 11px;");
        body.getChildren().add(playbackHeader);
        body.getChildren().add(nonBlockingOption);
        body.getChildren().add(blockingOption);

        overlayDialog.showDialog(
            hasWarnings ? "Register Timeline With Warnings?" : "Register Timeline?",
            "Review exactly what Puppeteer will do before it saves and registers \"" + name + "\".",
            body,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.accent("Register", () -> {
                if (strictCheckRef != null && strictCheckRef.isSelected()) {
                    showVerificationOverlay(
                        "Registration Blocked",
                        "Strict validation is on for this registration. Resolve the orbit-pivot warnings below before registering.",
                        findings.stream()
                            .filter(message -> message.description().contains(PuppeteerVerification.ORBIT_PIVOT_RISK_MARKER))
                            .toList(),
                        null,
                        null
                    );
                    return;
                }
                if (strictUnresolvedLayerCheckRef != null && strictUnresolvedLayerCheckRef.isSelected()) {
                    showVerificationOverlay(
                        "Registration Blocked",
                        "Strict validation is on for this registration. Resolve the unresolved-layer warnings below before registering.",
                        findings.stream()
                            .filter(message -> message.description().contains(PuppeteerVerification.UNRESOLVED_LAYER_PROXY_MARKER))
                            .toList(),
                        null,
                        null
                    );
                    return;
                }
                performRegisterTimeline(name, onSuccess, blockingOption.isSelected());
            })
        );
    }

    private boolean performRegisterTimeline(String name, Runnable onSuccess) {
        return performRegisterTimeline(name, onSuccess, false);
    }

    private boolean performRegisterTimeline(String name, Runnable onSuccess, boolean blockingPlayback) {
        try {
            List<TimelineDiagnostic.Message> nameFindings = PuppeteerVerification.validateTimelineName(name);
            boolean hasNameErrors = nameFindings.stream()
                .anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.ERROR);
            if (hasNameErrors) {
                showVerificationOverlay(
                    "Registration Blocked",
                    "Puppeteer found timeline name problems. Fix the name before registering.",
                    nameFindings,
                    null,
                    null
                );
                return false;
            }
            project.setName(name);
            project.setSceneEntitySnapshots(captureSceneEntitySnapshots());
            TimelineData data = project.toTimelineData(name);
            String code = CodeExporter.exportNamed(project, name, exportNestedBlocks, blockingPlayback);
            codePreview.setCode(code);
            boolean saved = saveTimelineFile(name, code);
            if (!saved) {
                setTitle("Puppeteer - " + name + " (save failed)");
                return false;
            }
            PuppeteerAnchorStore.save(projectRoot, project);
            VnEyeFocusProfileStore.save(projectRoot, project.getEyeFocusProfilesView().values());
            PuppeteerRigStore.save(projectRoot, project);
            TimelineRegistry.register(data);
            if (previewStaged) {
                previewStaged = false;
                previewBaselineProject = null;
                codePreview.markPreviewCommitted();
            }
            setDirty(false);
            setTitle("Puppeteer - " + name + " (saved & registered)");
            if (draftStore != null) draftStore.deleteDraft(name);
            if (workspacePrefs != null) {
                workspacePrefs.pushRecent(name, resolveRegisteredJesFile(name));
            }
            if (onSuccess != null) {
                onSuccess.run();
            }
            return true;
        } catch (Exception ex) {
            showOverlayError(
                PuppeteerErrorType.REGISTRATION,
                "Registration Failed",
                "Puppeteer could not register this timeline for runtime playback.",
                ex.getMessage(),
                ex
            );
            return false;
        }
    }

    void showVerificationOverlay(
        String title,
        String header,
        List<TimelineDiagnostic.Message> findings,
        String continueText,
        Runnable onContinue
    ) {
        VBox body = buildVerificationContent(findings);
        if (continueText != null) {
            overlayDialog.showDialog(
                title,
                header,
                body,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.accent(continueText, () -> {
                    if (onContinue != null) {
                        onContinue.run();
                    }
                })
            );
            return;
        }
        overlayDialog.showDialog(
            title,
            header,
            body,
            ActionEditorDialogOverlay.ActionSpec.neutral("Close", overlayDialog::hideOverlay).defaultFocus(true)
        );
    }

    private void showCodeGenerationActionPreview(
        String title,
        String header,
        String actionLabel,
        List<String> details,
        Runnable onContinue
    ) {
        String previewName = tfTimelineName.getText() != null ? tfTimelineName.getText().trim() : "";
        VBox body = new VBox(10);
        body.setFillWidth(true);
        body.getChildren().add(buildExportSummaryContent(previewName.isBlank() ? project.getName() : previewName));
        body.getChildren().add(buildActionDetailsContent(details));

        List<TimelineDiagnostic.Message> orbitRiskFindings = new ArrayList<>();
        for (EntityTrack track : project.getTracks()) {
            if (track == null) continue;
            String entity = track.getEntityName();
            if (!project.isOrbitPivotAtRisk(entity)) continue;
            orbitRiskFindings.add(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.WARNING,
                entity,
                "\"" + entity + "\" " + PuppeteerVerification.ORBIT_PIVOT_RISK_MARKER
                    + "; rotation will export as a plain positional move instead of pivoting",
                "Open the orbit-pivot warning badge on this entity's track for details"
            ));
        }

        List<TimelineDiagnostic.Message> unresolvedLayerFindings = new ArrayList<>();
        ResolvedCharacterContext rigContext = resolveCharacterForDiagnostics();
        if (rigContext.character() != null) {
            for (TimelineDiagnostic.Message message : PuppeteerVerification.diagnose(
                project,
                knownSceneEntities(),
                projectRoot,
                PuppeteerVerification.Mode.EXPORT_CODE,
                rigContext.character(),
                rigContext.characterId()
            )) {
                if (message.description().contains(PuppeteerVerification.UNRESOLVED_LAYER_PROXY_MARKER)) {
                    unresolvedLayerFindings.add(message);
                }
            }
        }

        javafx.scene.control.CheckBox strictOrbitCheck = null;
        if (!orbitRiskFindings.isEmpty()) {
            Label warningHeader = new Label("Orbit-pivot warnings that will be accepted if you continue:");
            warningHeader.setStyle("-fx-text-fill: #ffe2a8; -fx-font-size: 11px; -fx-font-weight: bold;");
            body.getChildren().add(warningHeader);
            body.getChildren().add(buildVerificationContent(orbitRiskFindings));
            strictOrbitCheck = new javafx.scene.control.CheckBox(
                "Treat orbit-pivot warnings as blocking for this action");
            strictOrbitCheck.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 11px;");
            body.getChildren().add(strictOrbitCheck);
        }
        javafx.scene.control.CheckBox strictCheckRef = strictOrbitCheck;
        List<TimelineDiagnostic.Message> orbitRiskFindingsRef = orbitRiskFindings;

        javafx.scene.control.CheckBox strictUnresolvedLayerCheck = null;
        if (!unresolvedLayerFindings.isEmpty()) {
            Label unresolvedLayerHeader = new Label("Unresolved-layer warnings that will be accepted if you continue:");
            unresolvedLayerHeader.setStyle("-fx-text-fill: #ffe2a8; -fx-font-size: 11px; -fx-font-weight: bold;");
            body.getChildren().add(unresolvedLayerHeader);
            body.getChildren().add(buildVerificationContent(unresolvedLayerFindings));
            strictUnresolvedLayerCheck = new javafx.scene.control.CheckBox(
                "Treat unresolved-layer warnings as blocking for this action");
            strictUnresolvedLayerCheck.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 11px;");
            body.getChildren().add(strictUnresolvedLayerCheck);
        }
        javafx.scene.control.CheckBox strictUnresolvedLayerCheckRef = strictUnresolvedLayerCheck;
        List<TimelineDiagnostic.Message> unresolvedLayerFindingsRef = unresolvedLayerFindings;

        overlayDialog.showDialog(
            title,
            header,
            body,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.accent(actionLabel, () -> {
                if (strictCheckRef != null && strictCheckRef.isSelected()) {
                    showVerificationOverlay(
                        "Action Blocked",
                        "Strict validation is on for this action. Resolve the orbit-pivot warnings below before continuing.",
                        orbitRiskFindingsRef,
                        null,
                        null
                    );
                    return;
                }
                if (strictUnresolvedLayerCheckRef != null && strictUnresolvedLayerCheckRef.isSelected()) {
                    showVerificationOverlay(
                        "Action Blocked",
                        "Strict validation is on for this action. Resolve the unresolved-layer warnings below before continuing.",
                        unresolvedLayerFindingsRef,
                        null,
                        null
                    );
                    return;
                }
                if (onContinue != null) {
                    onContinue.run();
                }
            })
        );
    }

    private VBox buildExportSummaryContent(String timelineName) {
        String safeName = timelineName == null || timelineName.isBlank() ? project.getName() : timelineName;
        String code = CodeExporter.exportNamed(project, safeName, exportNestedBlocks);
        TimelineData data = project.toTimelineData(safeName);
        TimelineExportSummary summary = TimelineExportSummary.of(project, code, data);

        VBox content = new VBox(4);
        content.setFillWidth(true);
        content.setMaxWidth(520);
        content.setStyle(
            "-fx-background-color: #14202b;"
                + "-fx-background-radius: 6;"
                + "-fx-border-color: #2c4257;"
                + "-fx-border-radius: 6;"
                + "-fx-padding: 8 10;"
        );

        Label statsLine = new Label(String.format(
            Locale.ROOT,
            "%d lines (%d metadata comments, %d script actions) - %d tracks - %d actions - %.1fs duration",
            summary.totalLineCount(),
            summary.commentLineCount(),
            summary.actionLineCount(),
            summary.trackCount(),
            summary.actionCount(),
            summary.durationMs() / 1000.0
        ));
        statsLine.setWrapText(true);
        statsLine.setStyle("-fx-text-fill: #d8eefc; -fx-font-size: 11px; -fx-font-weight: bold;");
        content.getChildren().add(statsLine);

        if (!summary.affectedEntityNames().isEmpty()) {
            Label affected = new Label("Affected: " + String.join(", ", summary.affectedEntityNames()));
            affected.setWrapText(true);
            affected.setStyle("-fx-text-fill: #9eb8d8; -fx-font-size: 11px;");
            content.getChildren().add(affected);
        }

        if (summary.isLarge()) {
            Label warning = new Label(
                "Large export - may increase editor parse time and runtime script load.");
            warning.setWrapText(true);
            warning.setStyle("-fx-text-fill: #ffe2a8; -fx-font-size: 11px; -fx-font-weight: bold;");
            content.getChildren().add(warning);
        }

        return content;
    }

    private VBox buildActionDetailsContent(List<String> details) {
        VBox content = new VBox(8);
        content.setFillWidth(true);
        content.setMaxWidth(520);
        content.setStyle("-fx-padding: 0 0 2 0;");

        for (String detail : details == null ? List.<String>of() : details) {
            if (detail == null || detail.isBlank()) continue;
            HBox row = new HBox(8);
            row.setAlignment(Pos.TOP_LEFT);
            Label bullet = new Label("-");
            bullet.setStyle("-fx-text-fill: #7ab3e0; -fx-font-size: 12px; -fx-font-weight: bold;");
            Label text = new Label(detail.trim());
            text.setWrapText(true);
            text.setMaxWidth(470);
            text.setStyle("-fx-text-fill: #d7d7d7; -fx-font-size: 11px;");
            HBox.setHgrow(text, Priority.ALWAYS);
            row.getChildren().addAll(bullet, text);
            content.getChildren().add(row);
        }

        if (content.getChildren().isEmpty()) {
            Label fallback = new Label("Puppeteer will perform the selected action after you confirm.");
            fallback.setWrapText(true);
            fallback.setStyle("-fx-text-fill: #d7d7d7; -fx-font-size: 11px;");
            content.getChildren().add(fallback);
        }
        return content;
    }

    private VBox buildVerificationContent(List<TimelineDiagnostic.Message> findings) {
        VBox content = new VBox(8);
        content.setFillWidth(true);
        content.setMaxWidth(500);
        content.setStyle("-fx-padding: 0 0 2 0;");

        List<TimelineDiagnostic.Message> safeFindings = findings == null
            ? List.of()
            : findings.stream().filter(Objects::nonNull).toList();
        if (safeFindings.isEmpty()) {
            safeFindings = List.of(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.INFO,
                "(timeline)",
                "No issues found.",
                ""
            ));
        }

        for (TimelineDiagnostic.Message finding : safeFindings) {
            content.getChildren().add(buildVerificationMessageRow(finding));
        }
        return content;
    }

    private Node buildVerificationMessageRow(TimelineDiagnostic.Message finding) {
        TimelineDiagnostic.Severity severity = finding.severity() == null
            ? TimelineDiagnostic.Severity.INFO
            : finding.severity();

        Label badge = new Label(verificationSeverityLabel(severity));
        badge.setMinWidth(58);
        badge.setAlignment(Pos.CENTER);
        badge.setStyle(verificationSeverityBadgeStyle(severity));

        Label target = new Label(firstNonBlank(finding.entityOrTrack(), "(timeline)"));
        target.setWrapText(true);
        target.setStyle("-fx-text-fill: #f0f0f0; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label description = new Label(firstNonBlank(finding.description(), "No details available."));
        description.setWrapText(true);
        description.setStyle("-fx-text-fill: #cfcfcf; -fx-font-size: 11px;");

        VBox text = new VBox(3, target, description);
        text.setFillWidth(true);
        HBox.setHgrow(text, Priority.ALWAYS);

        String quickFix = finding.quickFix();
        if (quickFix != null && !quickFix.isBlank()) {
            Label fix = new Label("Fix: " + quickFix.trim());
            fix.setWrapText(true);
            fix.setStyle("-fx-text-fill: #9eb8d8; -fx-font-size: 11px;");
            text.getChildren().add(fix);
        }

        HBox row = new HBox(10, badge, text);
        row.setAlignment(Pos.TOP_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setStyle(
            "-fx-background-color: #111111;"
                + "-fx-background-radius: 6;"
                + "-fx-border-color: #2e2e2e;"
                + "-fx-border-radius: 6;"
                + "-fx-padding: 8 10;"
        );
        return row;
    }

    private static String verificationSeverityLabel(TimelineDiagnostic.Severity severity) {
        return switch (severity) {
            case ERROR -> "ERROR";
            case WARNING -> "WARN";
            case INFO -> "INFO";
        };
    }

    private static String verificationSeverityBadgeStyle(TimelineDiagnostic.Severity severity) {
        String colors = switch (severity) {
            case ERROR -> "-fx-background-color: #5d2430; -fx-text-fill: #ffd9df; -fx-border-color: #8a3a49;";
            case WARNING -> "-fx-background-color: #4b3a1d; -fx-text-fill: #ffe2a8; -fx-border-color: #765c2d;";
            case INFO -> "-fx-background-color: #213a4a; -fx-text-fill: #d8eefc; -fx-border-color: #365a70;";
        };
        return colors
            + " -fx-background-radius: 999;"
            + " -fx-border-radius: 999;"
            + " -fx-font-size: 10px;"
            + " -fx-font-weight: bold;"
            + " -fx-padding: 3 8;";
    }

    private boolean saveTimelineFile(String name, String jesCode) {
        if (projectRoot == null) {
            showSaveError(name, "No project root is set. Open Puppeteer from a saved JVN project before registering.");
            return false;
        }
        if (!PuppeteerVerification.isValidTimelineName(name)) {
            showSaveError(name, "Timeline name is not safe to use as a .jes filename.");
            return false;
        }
        try {
            Path dir = projectRoot.toPath().resolve("scripts").resolve("timelines");
            Files.createDirectories(dir);
            Path file = dir.resolve(name + ".jes");
            backupExistingTimelineFile(file);
            Files.writeString(file, jesCode);
            return true;
        } catch (IOException ex) {
            showSaveError(name, ex.getMessage());
            return false;
        }
    }

    static Path backupExistingTimelineFile(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) return null;
        Path backupDir = file.getParent().resolve(".backups");
        Files.createDirectories(backupDir);
        String fileName = file.getFileName().toString();
        Path backup = backupDir.resolve(fileName + ".bak");
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        return backup;
    }

    private void showSaveError(String name, String detail) {
        Platform.runLater(() -> showOverlayError(
            PuppeteerErrorType.SAVE_IO,
            "Save Failed",
            "Could not save timeline '" + name + "' to disk.",
            detail != null ? detail : "Unknown error",
            null));
    }

    void showOverlayError(String title, String header, String detail) {
        showOverlayError(classifyPuppeteerError(title, header, detail, null), title, header, detail, null);
    }

    void showOverlayError(PuppeteerErrorType type,
                                  String title,
                                  String header,
                                  String detail,
                                  Throwable cause) {
        PuppeteerErrorType effectiveType = type == null
            ? classifyPuppeteerError(title, header, detail, cause)
            : type;
        String normalizedTitle = title == null || title.isBlank() ? "Puppeteer Error" : title.trim();
        String normalizedHeader = header == null || header.isBlank()
            ? "Puppeteer could not complete this action."
            : header.trim();
        String normalizedDetail = firstNonBlank(detail, cause != null ? cause.getMessage() : "Unknown error").trim();
        if (normalizedDetail.isBlank()) normalizedDetail = "Unknown error";
        List<String> hints = overlayErrorHints(effectiveType, normalizedHeader, normalizedDetail);

        VBox content = new VBox(10);
        content.setFillWidth(true);
        content.getChildren().addAll(
            overlaySectionLabel("Type"),
            overlayBodyLabel(effectiveType.label(), "#9eb8d8"),
            overlaySectionLabel("What happened"),
            overlayBodyLabel(normalizedHeader, "#d8d8d8"),
            overlaySectionLabel("Details"),
            overlayBodyLabel(normalizedDetail, "#e6a8b3"));
        if (!hints.isEmpty()) {
            content.getChildren().add(overlaySectionLabel("What you can try"));
            for (String hint : hints) {
                content.getChildren().add(overlayBodyLabel("- " + hint, "#b8b8b8"));
            }
        }

        String report = overlayErrorReport(effectiveType, normalizedTitle, normalizedHeader, normalizedDetail, hints, cause);
        overlayDialog.showDialog(
            normalizedTitle,
            "JVN could not complete this Puppeteer action.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Copy Details", () -> copyToClipboard(report))
                .closeOnAction(false),
            ActionEditorDialogOverlay.ActionSpec.accent("Close", overlayDialog::hideOverlay)
        );
    }

    private Label overlaySectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #f0f0f0; -fx-font-size: 11px; -fx-font-weight: bold;");
        return label;
    }

    private Label overlayBodyLabel(String text, String color) {
        Label label = new Label(text == null ? "" : text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: " + (color == null ? "#b0b0b0" : color) + "; -fx-font-size: 11px;");
        return label;
    }

    private List<String> overlayErrorHints(PuppeteerErrorType type, String header, String detail) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        String haystack = ((header == null ? "" : header) + " " + (detail == null ? "" : detail))
            .toLowerCase(Locale.ROOT);
        switch (type == null ? PuppeteerErrorType.UNKNOWN : type) {
            case VALIDATION -> hints.add("Correct the highlighted field or timeline diagnostic, then try again.");
            case PROJECT_CONTEXT -> hints.add("Open Puppeteer from a project-backed scene or reopen the project in the editor.");
            case CODE_PARSE -> hints.add("Check the edited JES timeline code for missing braces, missing quoted targets, or invalid values.");
            case EXPORT -> hints.add("Open the code pane diagnostics and fix blocking export errors before copying or registering.");
            case SAVE_IO -> hints.add("Confirm the project folder exists, is writable, and is not locked by another process.");
            case REGISTRATION -> hints.add("Run Runtime Verification and fix errors before registering for VNS interop.");
            case PREVIEW -> hints.add("Regenerate the code preview, then stage the code again once parser diagnostics are clear.");
            case ASSET -> hints.add("Relink missing image or stage assets from inside the project folder.");
            case AUDIO -> hints.add("Check the audio path, channel, and file format, then retry preview or registration.");
            case CLIP -> hints.add("Verify the clip still matches the selected track and entity/property type.");
            case CLIPBOARD -> hints.add("Try copying again after focusing the editor window.");
            case TIMELINE -> hints.add("Check timeline duration, loop region bounds, and playhead position. Use Fit Duration if needed.");
            case KEYFRAME -> hints.add("Verify keyframe times are non-negative and properly sorted. Use Timeline Diagnostics to locate conflicts.");
            case PROPERTY -> hints.add("Confirm the property is supported for the selected entity type. Check value ranges in the Keyframe Editor.");
            case ENTITY -> hints.add("Ensure the entity exists in the project and is visible in the Entities tab. Check spelling and case sensitivity.");
            case CAMERA -> hints.add("Verify camera bounds are within the viewport. Check DOF values are non-negative and within valid ranges.");
            case CUE -> hints.add("Confirm audio cue files exist and event cue payloads are valid. Check cue timing is within timeline duration.");
            case INTERPOLATION -> hints.add("Verify easing curve control points are valid. Try resetting to LINEAR or a built-in easing preset.");
            case IMPORT -> hints.add("Check the source file exists and has a valid format. Ensure you have read permissions on the file.");
            case STATE -> hints.add("Try undoing recent changes or reopening Puppeteer. Check if the command stack is corrupted.");
            case PLAYBACK -> hints.add("Verify the timeline has keyframes to play. Check if auto-key is enabled and try setting a keyframe first.");
            case UNKNOWN -> hints.add("Review the selected timeline, track, and project context, then try again.");
        }
        if (haystack.contains("project root")) {
            hints.add("Open Puppeteer from a project-backed scene or reopen the project in the editor.");
        }
        if (haystack.contains("save") || haystack.contains("disk") || haystack.contains("write")) {
            hints.add("Confirm the destination folder exists and your account can write to it.");
        }
        if (haystack.contains("parse") || haystack.contains("code") || haystack.contains("syntax")) {
            hints.add("Use the line-numbered diagnostics in the code pane to locate the bad action or property.");
        }
        if (haystack.contains("asset") || haystack.contains("image") || haystack.contains("missing")) {
            hints.add("Verify referenced files exist relative to the project root.");
        }
        if (haystack.contains("audio")) {
            hints.add("Use music, sound, or voice channels and keep audio assets under the project folder.");
        }
        if (haystack.contains("timeline name") || haystack.contains("filename")) {
            hints.add("Use a portable name like hero_intro_01 with no spaces or path separators.");
        }
        if (haystack.contains("loop") || haystack.contains("in") || haystack.contains("out")) {
            hints.add("Ensure loop IN is before loop OUT and both are within the timeline duration.");
        }
        if (haystack.contains("duration") && haystack.contains("negative")) {
            hints.add("Timeline duration must be a positive number. Use Fit Duration to auto-adjust based on keyframes.");
        }
        if (haystack.contains("snap") || haystack.contains("grid")) {
            hints.add("Adjust the snap step value or disable snapping if you need finer time resolution.");
        }
        if (hints.isEmpty()) {
            hints.add("Review the selected timeline, track, and project context, then try again.");
        }
        hints.add("Copy the details if you need to report the issue.");
        return new ArrayList<>(hints);
    }

    private String overlayErrorReport(PuppeteerErrorType type,
                                      String title,
                                      String header,
                                      String detail,
                                      List<String> hints,
                                      Throwable cause) {
        StringBuilder report = new StringBuilder();
        report.append("Title: ").append(title).append('\n');
        report.append("Type: ").append(type == null ? PuppeteerErrorType.UNKNOWN.label() : type.label()).append('\n');
        report.append("Summary: ").append(header).append('\n');
        report.append("Details: ").append(detail).append('\n');
        if (hints != null && !hints.isEmpty()) {
            report.append('\n').append("Suggested next steps:").append('\n');
            for (String hint : hints) {
                report.append("- ").append(hint).append('\n');
            }
        }
        if (cause != null) {
            report.append('\n').append("Stack trace:").append('\n');
            StringWriter stack = new StringWriter();
            cause.printStackTrace(new PrintWriter(stack));
            report.append(stack.toString().stripTrailing());
        }
        return report.toString().stripTrailing();
    }

    private PuppeteerErrorType classifyPuppeteerError(String title, String header, String detail, Throwable cause) {
        String haystack = ((title == null ? "" : title) + " "
            + (header == null ? "" : header) + " "
            + (detail == null ? "" : detail) + " "
            + (cause == null || cause.getMessage() == null ? "" : cause.getMessage()))
            .toLowerCase(Locale.ROOT);
        if (haystack.contains("duration") || haystack.contains("timeline name") || haystack.contains("validation")) {
            return PuppeteerErrorType.VALIDATION;
        }
        if (haystack.contains("project root") || haystack.contains("project-backed")) {
            return PuppeteerErrorType.PROJECT_CONTEXT;
        }
        if (haystack.contains("parse") || haystack.contains("syntax") || haystack.contains("code")) {
            return PuppeteerErrorType.CODE_PARSE;
        }
        if (haystack.contains("export") || haystack.contains("generate")) {
            return PuppeteerErrorType.EXPORT;
        }
        if (haystack.contains("save") || haystack.contains("disk") || haystack.contains("write")) {
            return PuppeteerErrorType.SAVE_IO;
        }
        if (haystack.contains("register") || haystack.contains("runtime")) {
            return PuppeteerErrorType.REGISTRATION;
        }
        if (haystack.contains("preview")) {
            return PuppeteerErrorType.PREVIEW;
        }
        if (haystack.contains("asset") || haystack.contains("image") || haystack.contains("missing")) {
            return PuppeteerErrorType.ASSET;
        }
        if (haystack.contains("audio") || haystack.contains("media")) {
            return PuppeteerErrorType.AUDIO;
        }
        if (haystack.contains("clip")) {
            return PuppeteerErrorType.CLIP;
        }
        if (haystack.contains("clipboard") || haystack.contains("copy")) {
            return PuppeteerErrorType.CLIPBOARD;
        }
        if (haystack.contains("timeline") || haystack.contains("loop") || haystack.contains("playhead")) {
            return PuppeteerErrorType.TIMELINE;
        }
        if (haystack.contains("keyframe") || haystack.contains("key") || haystack.contains("kf")) {
            return PuppeteerErrorType.KEYFRAME;
        }
        if (haystack.contains("property") || haystack.contains("prop") || haystack.contains("channel")) {
            return PuppeteerErrorType.PROPERTY;
        }
        if (haystack.contains("entity") || haystack.contains("track") || haystack.contains("sprite") || haystack.contains("character")) {
            return PuppeteerErrorType.ENTITY;
        }
        if (haystack.contains("camera") || haystack.contains("dof") || haystack.contains("zoom") || haystack.contains("pan")) {
            return PuppeteerErrorType.CAMERA;
        }
        if (haystack.contains("cue") || haystack.contains("event") || haystack.contains("marker")) {
            return PuppeteerErrorType.CUE;
        }
        if (haystack.contains("easing") || haystack.contains("interpol") || haystack.contains("curve") || haystack.contains("bezier")) {
            return PuppeteerErrorType.INTERPOLATION;
        }
        if (haystack.contains("import") || haystack.contains("load") || haystack.contains("read")) {
            return PuppeteerErrorType.IMPORT;
        }
        if (haystack.contains("state") || haystack.contains("undo") || haystack.contains("redo") || haystack.contains("command")) {
            return PuppeteerErrorType.STATE;
        }
        if (haystack.contains("playback") || haystack.contains("play") || haystack.contains("pause") || haystack.contains("stop")) {
            return PuppeteerErrorType.PLAYBACK;
        }
        return PuppeteerErrorType.UNKNOWN;
    }

    void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    void showClipboardHistoryPopup() {
        if (clipboardHistory.isEmpty()) {
            if (statusBar != null) statusBar.setText("Clipboard history is empty");
            return;
        }

        VBox content = new VBox(8);
        for (int i = 0; i < clipboardHistory.size(); i++) {
            List<com.jvn.editor.ui.actioneditor.TimelinePanel.ClipboardEntry> entries = clipboardHistory.get(i);
            int index = i;
            String description = describeClipboardEntry(entries);
            Button restoreButton = buildOverlayMenuButton(
                (i == 0 ? "Current: " : "Item " + (i + 1) + ": ") + description,
                () -> restoreClipboardEntry(index)
            );
            content.getChildren().add(restoreButton);
        }

        overlayDialog.showDialog(
            "Clipboard History",
            "Select a previous clipboard entry to restore.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Close", overlayDialog::hideOverlay).defaultFocus(true)
        );
    }

    private String describeClipboardEntry(List<com.jvn.editor.ui.actioneditor.TimelinePanel.ClipboardEntry> entries) {
        if (entries == null || entries.isEmpty()) return "Empty";
        int count = entries.size();
        String entity = entries.get(0).sourceName();
        if (entity == null || entity.isBlank()) entity = "Unknown";
        return count + " keyframe" + (count > 1 ? "s" : "") + " from " + entity;
    }

    private void restoreClipboardEntry(int index) {
        if (index < 0 || index >= clipboardHistory.size()) return;
        List<com.jvn.editor.ui.actioneditor.TimelinePanel.ClipboardEntry> entries = clipboardHistory.get(index);
        timelinePanel.setCopiedKeyframes(entries);
        overlayDialog.hideOverlay();
        if (statusBar != null) statusBar.setText("Restored clipboard entry " + (index + 1));
        refreshToolbarCommandSummary();
    }

    private void showAutoSaveIndicator(String timelineName) {
        if (statusBar == null) return;
        statusBar.setText("Auto-saved: " + timelineName);
        javafx.util.Duration duration = javafx.util.Duration.seconds(3);
        javafx.animation.KeyFrame keyFrame = new javafx.animation.KeyFrame(duration, e -> {
            if (statusBar != null) statusBar.setText("");
        });
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(keyFrame);
        timeline.play();
    }

    private void captureProjectSnapshotBaseline() {
        if (scene == null) {
            project.captureInitialSnapshot();
            return;
        }
        Map<String, Map<PropertyType, Double>> snapshot = new LinkedHashMap<>();
        for (String entityName : scene.names()) {
            if (entityName == null || entityName.isBlank()) continue;
            var entity = scene.find(entityName);
            if (entity == null) continue;
            Map<PropertyType, Double> props = new EnumMap<>(PropertyType.class);
            for (PropertyType property : PropertyType.values()) {
                if (!property.isEntityProperty()) continue;
                props.put(property, baselinePropertyValue(entity, property));
            }
            snapshot.put(entityName, props);
        }
        project.setInitialSnapshot(snapshot);
        project.setSceneEntitySnapshots(captureSceneEntitySnapshots());
    }

    private List<AnimationProject.SceneEntitySnapshot> captureSceneEntitySnapshots() {
        if (scene == null) return List.of();
        List<AnimationProject.SceneEntitySnapshot> snapshots = new ArrayList<>();
        SpritePixelAnalyzer pixelAnalyzer = new SpritePixelAnalyzer(projectRoot);
        for (String entityName : scene.names()) {
            if (entityName == null || entityName.isBlank()) continue;
            var entity = scene.find(entityName);
            if (entity == null) continue;

            String type = "entity";
            String imagePath = "";
            String rawImagePath = "";
            double width = 1.0;
            double height = 1.0;
            double alpha = snapshotOrCurrent(entityName, PropertyType.ALPHA, fallbackPropertyValue(entity, PropertyType.ALPHA));
            if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                type = "sprite";
                rawImagePath = sprite.getImagePath();
                imagePath = relativizePreviewAssetPathSpec(rawImagePath);
                width = sprite.getWidth();
                height = sprite.getHeight();
                alpha = snapshotOrCurrent(entityName, PropertyType.ALPHA, sprite.getAlpha());
            } else if (entity instanceof com.jvn.core.scene2d.Panel2D panel) {
                type = "panel";
                width = panel.getWidth();
                height = panel.getHeight();
                alpha = snapshotOrCurrent(entityName, PropertyType.ALPHA, panel.getFillA());
            } else if (entity instanceof com.jvn.core.scene2d.CharacterEntity2D character) {
                type = "character";
                width = character.getDrawWidth();
                height = character.getDrawHeight();
            }

            double x = snapshotOrCurrent(entityName, PropertyType.X, entity.getX());
            double y = snapshotOrCurrent(entityName, PropertyType.Y, entity.getY());
            double originX = snapshotOrCurrent(entityName, PropertyType.PIVOT_X, entity.getOriginX());
            double originY = snapshotOrCurrent(entityName, PropertyType.PIVOT_Y, entity.getOriginY());
            double runtimeBaseX = runtimeExportBaselineOrSnapshot(entityName, PropertyType.X, x);
            double runtimeBaseY = runtimeExportBaselineOrSnapshot(entityName, PropertyType.Y, y);
            double[] visualBounds = computeSnapshotVisualBounds(
                pixelAnalyzer,
                rawImagePath,
                x,
                y,
                width,
                height,
                originX,
                originY
            );
            snapshots.add(new AnimationProject.SceneEntitySnapshot(
                entityName,
                type,
                imagePath,
                x,
                y,
                width,
                height,
                originX,
                originY,
                snapshotOrCurrent(entityName, PropertyType.Z, entity.getZ()),
                snapshotOrCurrent(entityName, PropertyType.VISIBILITY, entity.isVisible() ? 1.0 : 0.0) >= 0.5,
                alpha,
                visualBounds[0],
                visualBounds[1],
                visualBounds[2],
                visualBounds[3],
                runtimeBaseX,
                runtimeBaseY
            ));
        }
        return snapshots;
    }

    private double[] computeSnapshotVisualBounds(
        SpritePixelAnalyzer pixelAnalyzer,
        String imagePathSpec,
        double x,
        double y,
        double width,
        double height,
        double originX,
        double originY
    ) {
        double fallbackMinX = x - originX * width;
        double fallbackMinY = y - originY * height;
        double fallbackMaxX = fallbackMinX + width;
        double fallbackMaxY = fallbackMinY + height;
        if (pixelAnalyzer == null || imagePathSpec == null || imagePathSpec.isBlank()) {
            return new double[]{fallbackMinX, fallbackMinY, fallbackMaxX, fallbackMaxY};
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (String layerPath : imagePathSpec.split("\\|")) {
            String path = layerPath == null ? "" : layerPath.trim();
            if (path.isEmpty()) continue;
            List<SpritePixelAnalyzer.DetectedRegion> regions = pixelAnalyzer.detectRegions(path);
            if (regions.isEmpty()) continue;
            javafx.scene.image.Image image = loadAssetImage(path);
            double imageW = image != null && !image.isError() && image.getWidth() > 1.0
                ? image.getWidth()
                : width;
            double imageH = image != null && !image.isError() && image.getHeight() > 1.0
                ? image.getHeight()
                : height;
            double imgToSpriteX = width / Math.max(1.0, imageW);
            double imgToSpriteY = height / Math.max(1.0, imageH);
            for (SpritePixelAnalyzer.DetectedRegion region : regions) {
                double regionMinX = fallbackMinX + region.minX * imgToSpriteX;
                double regionMinY = fallbackMinY + region.minY * imgToSpriteY;
                double regionMaxX = fallbackMinX + (region.minX + region.width) * imgToSpriteX;
                double regionMaxY = fallbackMinY + (region.minY + region.height) * imgToSpriteY;
                minX = Math.min(minX, regionMinX);
                minY = Math.min(minY, regionMinY);
                maxX = Math.max(maxX, regionMaxX);
                maxY = Math.max(maxY, regionMaxY);
            }
        }
        if (!Double.isFinite(minX) || !Double.isFinite(minY)
            || !Double.isFinite(maxX) || !Double.isFinite(maxY)) {
            return new double[]{fallbackMinX, fallbackMinY, fallbackMaxX, fallbackMaxY};
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    private double snapshotOrCurrent(String entityName, PropertyType property, double fallback) {
        Double value = project.getInitialSnapshotValue(entityName, property);
        return value != null && Double.isFinite(value) ? value : fallback;
    }

    private double runtimeExportBaselineOrSnapshot(String entityName, PropertyType property, double fallback) {
        if (entityName != null && property != null) {
            Map<PropertyType, Double> props = runtimeExportBaselines.get(entityName);
            Double value = props == null ? null : props.get(property);
            if (value != null && Double.isFinite(value)) {
                return value;
            }
        }
        return fallback;
    }

    private String relativizePreviewAssetPathSpec(String pathSpec) {
        if (pathSpec == null || pathSpec.isBlank()) return "";
        if (pathSpec.indexOf('|') < 0) return relativizePreviewAssetPath(pathSpec.trim());
        StringBuilder out = new StringBuilder();
        for (String token : pathSpec.split("\\|")) {
            String part = token == null ? "" : token.trim();
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(" | ");
            out.append(relativizePreviewAssetPath(part));
        }
        return out.toString();
    }

    private String relativizePreviewAssetPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank() || projectRoot == null) {
            return rawPath == null ? "" : rawPath.trim();
        }
        try {
            Path root = projectRoot.toPath().toAbsolutePath().normalize();
            Path path = Path.of(rawPath.trim()).toAbsolutePath().normalize();
            if (path.startsWith(root)) {
                return root.relativize(path).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
        }
        return rawPath.trim();
    }

    Set<String> knownSceneEntities() {
        if (scene == null) return null;
        Set<String> names = new LinkedHashSet<>();
        for (String name : scene.names()) {
            if (name != null && !name.isBlank()) names.add(name);
        }
        return names.isEmpty() ? null : names;
    }

    public void stagePreviewFromCode() {
        String code = codePreview.getCode();
        String name = tfTimelineName.getText().trim();
        if (name.isBlank()) name = project.getName();
        try {
            if (!previewStaged) {
                previewBaselineProject = project.copy();
                dirtyBeforePreviewStage = dirty;
            }

            AnimationProject imported = CodeImporter.importCode(name, code);
            applyImportedProject(imported);
            previewStaged = true;
            codePreview.setPreviewStaged(true);
            setDirty(dirty);
            refreshExportPreview();
        } catch (Exception ex) {
            Platform.runLater(() -> showOverlayError(
                PuppeteerErrorType.CODE_PARSE,
                "Preview Failed",
                "Could not parse the edited code.",
                ex.getMessage(),
                ex));
        }
    }

    public void commitStagedPreview() {
        if (!previewStaged) return;
        previewStaged = false;
        previewBaselineProject = null;
        codePreview.markPreviewCommitted();
        setDirty(true);
        refreshExportPreview();
    }

    public void discardStagedPreview() {
        if (!previewStaged) return;
        if (previewBaselineProject != null) {
            applyImportedProject(previewBaselineProject);
        }
        previewStaged = false;
        previewBaselineProject = null;
        codePreview.markPreviewDiscarded();
        setDirty(dirtyBeforePreviewStage);
        refreshExportPreview();
    }

    private void applyImportedProject(AnimationProject imported) {
        if (imported == null) return;
        double playhead = project.getPlayheadMs();
        Map<String, double[]> anchorSnapshot = project.getOrbitAnchorsView();
        Map<String, String> anchorSourceSnapshot = project.getOrbitAnchorSourcesView();
        Map<String, double[]> anchorOffsetSnapshot = project.getOrbitAnchorSourceOffsetsView();
        Map<String, Constraint> constraintSnapshot = project.getConstraintsView();
        Map<String, Map<String, Anchor>> namedAnchorSnapshot = project.getEntityAnchorsView();
        boolean importedHasOrbitAnchors = !imported.getOrbitAnchorsView().isEmpty();
        boolean importedHasConstraints = !imported.getConstraintsView().isEmpty();
        boolean importedHasNamedAnchors = !imported.getEntityAnchorsView().isEmpty();
        project.replaceFrom(imported);
        if (!importedHasOrbitAnchors) {
            project.setOrbitAnchors(anchorSnapshot);
            project.setOrbitAnchorSources(anchorSourceSnapshot);
            project.setOrbitAnchorSourceOffsets(anchorOffsetSnapshot);
        }
        if (!importedHasConstraints && !constraintSnapshot.isEmpty()) {
            project.clearConstraints();
            for (Map.Entry<String, Constraint> entry : constraintSnapshot.entrySet()) {
                project.setConstraint(entry.getKey(), entry.getValue());
            }
        }
        if (!importedHasNamedAnchors && !namedAnchorSnapshot.isEmpty()) {
            project.clearAllAnchors();
            for (Map.Entry<String, Map<String, Anchor>> entry : namedAnchorSnapshot.entrySet()) {
                for (Anchor anchor : entry.getValue().values()) {
                    project.setAnchor(entry.getKey(), anchor);
                }
            }
        }
        project.pruneOrbitAnchors(collectProjectAnchorTargetNames());
        project.setPlayheadMs(playhead);
        activeTransformInteraction = null;
        commandStack.clear();
        tfDuration.setText(String.valueOf((int) project.getTotalDurationMs()));
        cbLoop.setSelected(project.isLooping());
        keyframeEditor.setTimelineDurationMs(project.getTotalDurationMs());
        keyframeEditor.setKeyframe(null, null);
        entitySelector.refresh(project);
        timelinePanel.refresh();
        timelinePanel.setPlayhead(project.getPlayheadMs());
        animationPreview.setOrbitAnchors(project.getOrbitAnchorsView());
        animationPreview.setOrbitAnchorSources(project.getOrbitAnchorSourcesView());
        animationPreview.setOrbitAnchorSourceOffsets(project.getOrbitAnchorSourceOffsetsView());
        updateTimeLabel();
        updatePreview();
    }

    private Set<String> collectProjectEntityNames() {
        Set<String> names = new LinkedHashSet<>();
        for (EntityTrack track : project.getTracks()) {
            String name = track.getEntityName();
            if (name != null && !name.isBlank()) names.add(name);
        }
        return names;
    }

    private Set<String> collectProjectAnchorTargetNames() {
        Set<String> names = collectProjectEntityNames();
        for (EntityGroup group : project.getGroups()) {
            if (group == null) continue;
            String name = group.getName();
            if (name != null && !name.isBlank()) names.add(name);
        }
        return names;
    }

    private Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> captureTransformSnapshots(double timeMs) {
        return captureTransformSnapshots(null, timeMs);
    }

    private Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> captureTransformSnapshots(String primaryEntityName, double timeMs) {
        Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> snapshots = new LinkedHashMap<>();
        if (primaryEntityName != null && !primaryEntityName.isBlank()) {
            EntityGroup primaryGroup = project.getGroup(primaryEntityName);
            if (primaryGroup != null) {
                snapshots.put(primaryEntityName,
                    captureTrackSnapshots(primaryGroup.getGroupTrack(), timeMs, TRANSFORM_INTERACTION_PROPERTIES, null));
                return snapshots;
            }
        }
        Set<String> names = collectProjectEntityNames();
        if (primaryEntityName != null && !primaryEntityName.isBlank()) {
            names.add(primaryEntityName);
            resolveAnimatedTrack(primaryEntityName, true);
        }
        for (String entityName : names) {
            EntityTrack track = resolveAnimatedTrack(entityName, false);
            if (track == null) continue;
            var entity = scene != null ? scene.find(track.getEntityName()) : null;
            snapshots.put(entityName, captureTrackSnapshots(track, timeMs, TRANSFORM_INTERACTION_PROPERTIES, entity));
        }
        return snapshots;
    }

    private Map<PropertyType, PuppeteerCommand.PropertySnapshot> captureTrackSnapshots(
        EntityTrack track,
        double timeMs,
        PropertyType[] properties,
        com.jvn.core.scene2d.Entity2D entity
    ) {
        Map<PropertyType, PuppeteerCommand.PropertySnapshot> snapshots = new java.util.EnumMap<>(PropertyType.class);
        if (track == null || properties == null) {
            return snapshots;
        }
        for (PropertyType property : properties) {
            Keyframe keyframe = track.findKeyframeAt(property, timeMs);
            boolean present = keyframe != null;
            double value = present
                ? keyframe.getValue()
                : valueAtTimeOrFallback(track, property, timeMs, fallbackPropertyValue(entity, property));
            snapshots.put(property, new PuppeteerCommand.PropertySnapshot(present, value));
        }
        return snapshots;
    }

    private List<PuppeteerCommand> buildTransformInteractionCommands(
        double timeMs,
        Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> beforeStates,
        Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> afterStates
    ) {
        List<PuppeteerCommand> commands = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        names.addAll(beforeStates.keySet());
        names.addAll(afterStates.keySet());
        for (String entityName : names) {
            EntityTrack track = resolveAnimatedTrack(entityName, true);
            if (track == null) continue;
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> before = beforeStates.getOrDefault(entityName, Map.of());
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> after = afterStates.getOrDefault(entityName, Map.of());
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> changedBefore = new java.util.EnumMap<>(PropertyType.class);
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> changedAfter = new java.util.EnumMap<>(PropertyType.class);

            for (PropertyType property : TRANSFORM_INTERACTION_PROPERTIES) {
                PuppeteerCommand.PropertySnapshot beforeSnapshot = before.get(property);
                PuppeteerCommand.PropertySnapshot afterSnapshot = after.get(property);
                if (!transformChanged(property, beforeSnapshot, afterSnapshot)) continue;
                changedBefore.put(property, beforeSnapshot != null ? beforeSnapshot : new PuppeteerCommand.PropertySnapshot(false, 0.0));
                changedAfter.put(property, afterSnapshot != null ? afterSnapshot : new PuppeteerCommand.PropertySnapshot(false, 0.0));
            }

            if (!changedBefore.isEmpty()) {
                commands.add(PuppeteerCommand.applyPropertiesAtTime(
                    track,
                    timeMs,
                    changedBefore,
                    changedAfter,
                    "Edit " + entityName
                ));
            }
        }
        return commands;
    }

    private void restoreTransformSnapshots(
        double timeMs,
        Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> snapshots
    ) {
        for (Map.Entry<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> entry : snapshots.entrySet()) {
            EntityTrack track = resolveAnimatedTrack(entry.getKey(), true);
            if (track == null) continue;
            restoreTrackSnapshots(track, timeMs, entry.getValue());
        }
        updatePreview();
    }

    private void restoreTrackSnapshots(
        EntityTrack track,
        double timeMs,
        Map<PropertyType, PuppeteerCommand.PropertySnapshot> snapshots
    ) {
        if (track == null || snapshots == null) return;
        for (Map.Entry<PropertyType, PuppeteerCommand.PropertySnapshot> propertyEntry : snapshots.entrySet()) {
            restorePropertySnapshot(track, propertyEntry.getKey(), timeMs, propertyEntry.getValue());
        }
    }

    private void restorePropertySnapshot(
        EntityTrack track,
        PropertyType property,
        double timeMs,
        PuppeteerCommand.PropertySnapshot snapshot
    ) {
        if (track == null || property == null || snapshot == null) return;
        if (snapshot.present()) {
            track.upsertKeyframe(property, new Keyframe(timeMs, snapshot.value()));
        } else {
            Keyframe keyframe = track.findKeyframeAt(property, timeMs);
            if (keyframe != null) {
                track.removeKeyframe(property, keyframe);
            }
        }
        track.sortKeyframes(property);
    }

    private static boolean transformChanged(
        PropertyType property,
        PuppeteerCommand.PropertySnapshot before,
        PuppeteerCommand.PropertySnapshot after
    ) {
        if (before == null && after == null) return false;
        if (before == null || after == null) return true;
        double epsilon = property == PropertyType.ROTATION ? 0.1 : MOVE_INTERACTION_EPSILON;
        return Math.abs(before.value() - after.value()) > epsilon;
    }

    private void applyAnchorFollowerDelta(String sourceEntityName, double timeMs, double dx, double dy, Set<String> visitedSources) {
        if (sourceEntityName == null || sourceEntityName.isBlank()) return;
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) return;
        if (!visitedSources.add(sourceEntityName)) return;

        Map<String, String> sourceLinks = project.getOrbitAnchorSourcesView();
        if (sourceLinks.isEmpty()) return;
        for (Map.Entry<String, String> entry : sourceLinks.entrySet()) {
            String target = entry.getKey();
            String source = entry.getValue();
            if (target == null || target.isBlank()) continue;
            if (!sourceEntityName.equals(source)) continue;

            EntityTrack targetTrack = project.getOrCreateTrack(target);
            double fallbackX = 0.0;
            double fallbackY = 0.0;
            if (scene != null) {
                var targetEntity = scene.find(target);
                if (targetEntity != null) {
                    fallbackX = targetEntity.getX();
                    fallbackY = targetEntity.getY();
                }
            }
            double prevX = valueAtTimeOrFallback(targetTrack, PropertyType.X, timeMs, fallbackX);
            double prevY = valueAtTimeOrFallback(targetTrack, PropertyType.Y, timeMs, fallbackY);
            double nextX = prevX + dx;
            double nextY = prevY + dy;

            targetTrack.upsertKeyframe(PropertyType.X, new Keyframe(timeMs, nextX));
            targetTrack.upsertKeyframe(PropertyType.Y, new Keyframe(timeMs, nextY));

            if (scene != null) {
                var targetEntity = scene.find(target);
                if (targetEntity != null) {
                    targetEntity.setPosition(nextX, nextY);
                }
            }

            applyAnchorFollowerDelta(target, timeMs, dx, dy, visitedSources);
        }
    }

    private static double valueAtTimeOrFallback(EntityTrack track, PropertyType property, double timeMs, double fallback) {
        if (track == null || property == null) return fallback;
        Keyframe key = track.findKeyframeAt(property, timeMs);
        if (key != null && Double.isFinite(key.getValue())) return key.getValue();
        double value = track.getValueAt(property, timeMs);
        return Double.isFinite(value) ? value : fallback;
    }

    private static double fallbackPropertyValue(com.jvn.core.scene2d.Entity2D entity, PropertyType property) {
        if (property == null) return 0.0;
        if (entity == null) return property.getDefaultValue();
        if (property == PropertyType.X) return entity.getX();
        if (property == PropertyType.Y) return entity.getY();
        if (property == PropertyType.Z) return entity.getZ();
        if (property == PropertyType.PIVOT_X) return getEntityPivotX(entity);
        if (property == PropertyType.PIVOT_Y) return getEntityPivotY(entity);
        if (property == PropertyType.ROTATION) return entity.getRotationDeg();
        if (property == PropertyType.SCALE_X) return entity.getScaleX();
        if (property == PropertyType.SCALE_Y) return entity.getScaleY();
        if (property == PropertyType.ALPHA) return getEntityAlpha(entity);
        if (property == PropertyType.VISIBILITY) return entity.isVisible() ? 1.0 : 0.0;
        if (property == PropertyType.MATRIX_MXX) return entity.getMatrixMxx();
        if (property == PropertyType.MATRIX_MXY) return entity.getMatrixMxy();
        if (property == PropertyType.MATRIX_MYX) return entity.getMatrixMyx();
        if (property == PropertyType.MATRIX_MYY) return entity.getMatrixMyy();
        if (property == PropertyType.MATRIX_TX) return entity.getMatrixTx();
        if (property == PropertyType.MATRIX_TY) return entity.getMatrixTy();
        if (property == PropertyType.BLUR) return entity.getBlurRadius();
        if (property == PropertyType.BRIGHTNESS) return entity.getBrightness();
        return property.getDefaultValue();
    }

    private static double getEntityAlpha(com.jvn.core.scene2d.Entity2D entity) {
        if (entity instanceof com.jvn.core.scene2d.Sprite2D s) return s.getAlpha();
        if (entity instanceof com.jvn.core.scene2d.Label2D l) return l.getAlpha();
        return 1.0;
    }

    private Map<String, Double> captureEntityCustomPropertyBaseline(com.jvn.core.scene2d.Entity2D entity) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (entity == null) return values;
        for (var definition : com.jvn.core.scene2d.Entity2D.animatableProperties()) {
            if (definition == null) continue;
            values.put(definition.getKey(), definition.getValue(entity));
        }
        values.putAll(entity.getCustomPropertiesView());
        return values;
    }

    private Map<String, Double> captureCameraCustomPropertyBaseline(com.jvn.core.graphics.Camera2D camera) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (camera == null) return values;
        for (var definition : com.jvn.core.graphics.Camera2D.animatableProperties()) {
            if (definition == null) continue;
            values.put(definition.getKey(), definition.getValue(camera));
        }
        values.putAll(camera.getCustomPropertiesView());
        return values;
    }

    private void restoreEntityCustomPropertyBaseline(
        com.jvn.core.scene2d.Entity2D entity,
        Map<String, Double> baseline
    ) {
        if (entity == null) return;
        entity.resetSupplementalTransform();
        entity.resetColorMatrix();
        entity.setBlurRadius(0.0);
        entity.setBrightness(1.0);
        entity.clearCustomProperties();
        if (baseline == null) return;
        for (Map.Entry<String, Double> entry : baseline.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            entity.applyCustomProperty(entry.getKey(), entry.getValue());
        }
    }

    private void restoreCameraCustomPropertyBaseline(
        com.jvn.core.graphics.Camera2D camera,
        Map<String, Double> baseline
    ) {
        if (camera == null) return;
        camera.setFocusDepth(0.0);
        camera.setDepthOfFieldStrength(0.0);
        camera.setDepthOfFieldMaxBlur(0.0);
        camera.clearCustomProperties();
        if (baseline == null) return;
        for (Map.Entry<String, Double> entry : baseline.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            camera.applyCustomProperty(entry.getKey(), entry.getValue());
        }
    }

    private double baselineCustomPropertyValue(
        String targetName,
        String propertyKey,
        com.jvn.core.scene2d.Entity2D entity
    ) {
        if (targetName != null && !targetName.isBlank()) {
            Map<String, Double> baseline = sceneBaselineCustomProperties.get(targetName);
            if (baseline != null && baseline.containsKey(propertyKey)) {
                Double value = baseline.get(propertyKey);
                if (value != null && Double.isFinite(value)) return value;
            }
        }
        return entity != null ? entity.readCustomProperty(propertyKey) : 0.0;
    }

    private double baselineCustomPropertyValue(
        String targetName,
        String propertyKey,
        com.jvn.core.graphics.Camera2D camera
    ) {
        if (targetName != null && TimelinePanel.isRuntimeCameraTarget(targetName)) {
            Double value = sceneBaselineCameraCustomProperties.get(propertyKey);
            if (value != null && Double.isFinite(value)) return value;
        }
        return camera != null ? camera.readCustomProperty(propertyKey) : 0.0;
    }

    private String selectionLabel(String name, boolean group) {
        if (name == null || name.isBlank()) return "-";
        if (TimelinePanel.isRuntimeCameraTarget(name)) return "Runtime Camera / Frame";
        return group ? name + " [Group]" : name;
    }

    private static String snapshotCharacterGroupName(PuppeteerLauncherPanel.CharacterEntry character) {
        return PuppeteerLauncherPanel.snapshotCharacterGroupName(character);
    }

    private static String selectorSafeName(String raw) {
        return PuppeteerLauncherPanel.selectorSafeName(raw);
    }

    private java.util.List<Keyframe> findAdjacentKeyframes(Keyframe kf, PropertyType property) {
        if (kf == null || property == null) return null;
        EntityTrack track = selectedTrackForEditing(false);
        if (track == null) return null;
        java.util.List<Keyframe> all = track.getKeyframes(property);
        if (all == null || all.size() < 2) return null;
        java.util.List<Keyframe> adjacent = new java.util.ArrayList<>();
        int idx = all.indexOf(kf);
        if (idx < 0) return null;
        if (idx > 0) adjacent.add(all.get(idx - 1));
        if (idx < all.size() - 1) adjacent.add(all.get(idx + 1));
        return adjacent.isEmpty() ? null : adjacent;
    }

    public EntityTrack selectedTrackForEditing(boolean createEntityTrack) {
        String name = timelinePanel.getSelectedEntity();
        if (name == null || name.isBlank()) return null;
        if (TimelinePanel.isRuntimeCameraTarget(name)) {
            return resolveRuntimeCameraTrack(createEntityTrack);
        }
        if (timelinePanel.isSelectedGroup()) {
            EntityGroup group = project.getGroup(name);
            return group != null ? group.getGroupTrack() : null;
        }
        return createEntityTrack ? project.getOrCreateTrack(name) : project.getTrack(name);
    }

    private EntityTrack resolveRuntimeCameraTrack(boolean createIfMissing) {
        EntityTrack dedicated = project.getTrack(TimelinePanel.RUNTIME_CAMERA_TARGET);
        if (dedicated != null) return dedicated;
        for (EntityTrack track : project.getTracks()) {
            if (track == null) continue;
            if (track.hasKeyframes(PropertyType.CAMERA_X)
                || track.hasKeyframes(PropertyType.CAMERA_Y)
                || track.hasKeyframes(PropertyType.CAMERA_ZOOM)
                || track.hasKeyframes(PropertyType.CAMERA_DOF_FOCUS)
                || track.hasKeyframes(PropertyType.CAMERA_DOF_STRENGTH)
                || track.hasKeyframes(PropertyType.CAMERA_DOF_MAX_BLUR)) {
                return track;
            }
            for (String propertyKey : track.getAnimatedCustomProperties()) {
                if (com.jvn.core.graphics.Camera2D.getAnimatableProperty(propertyKey) != null) {
                    return track;
                }
            }
        }
        return createIfMissing ? project.getOrCreateTrack(TimelinePanel.RUNTIME_CAMERA_TARGET) : null;
    }

    @Override
    public void close() {
        if (playbackTimer != null) playbackTimer.stop();
        if (animationPreview != null) animationPreview.dispose();
        super.close();
    }

    private void collectResetCommands(EntityGroup group, double time, java.util.List<PuppeteerCommand> commands) {
        if (group == null || this.project == null) return;

        if (!group.isLocked()) {
            EntityTrack groupTrack = group.getGroupTrack();
            if (groupTrack != null) {
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.X, time, PropertyType.X.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.Y, time, PropertyType.Y.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.Z, time, PropertyType.Z.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.PIVOT_X, time, PropertyType.PIVOT_X.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.PIVOT_Y, time, PropertyType.PIVOT_Y.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.ROTATION, time, PropertyType.ROTATION.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.SCALE_X, time, PropertyType.SCALE_X.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.SCALE_Y, time, PropertyType.SCALE_Y.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.MIRROR_X, time, PropertyType.MIRROR_X.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.ALPHA, time, PropertyType.ALPHA.getDefaultValue()));
            }
        }
        
        for (String childEntity : group.getChildEntityNames()) {
            EntityTrack track = this.project.getTrack(childEntity);
            if (track != null && !track.isLocked()) {
                commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.X, time, PropertyType.X.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.Y, time, PropertyType.Y.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.ROTATION, time, PropertyType.ROTATION.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.SCALE_X, time, PropertyType.SCALE_X.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.SCALE_Y, time, PropertyType.SCALE_Y.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.MIRROR_X, time, PropertyType.MIRROR_X.getDefaultValue()));
            }
        }
        for (String childGroup : group.getChildGroupNames()) {
            EntityGroup child = this.project.getGroup(childGroup);
            if (child != null && !child.isLocked()) {
                collectResetCommands(child, time, commands);
            }
        }
    }
}
