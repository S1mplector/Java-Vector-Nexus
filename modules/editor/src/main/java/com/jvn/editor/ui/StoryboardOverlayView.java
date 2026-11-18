package com.jvn.editor.ui;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Cursor;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

public class StoryboardOverlayView extends BorderPane {
  private static final String STATE_FILE = ".jvn/storyboard-overlay.properties";
  private static final String KEY_FOLDER = "folder";
  private static final String KEY_FILTER = "filter";
  private static final String KEY_ENABLED = "enabled";
  private static final String KEY_OPACITY = "opacity";
  private static final String KEY_SELECTED = "selected";
  private static final String KEY_FOLLOW_ACTIVE = "followActive";
  private static final String KEY_HIDE_UI = "hideUi";
  private static final String KEY_FIT_MODE = "fitMode";
  private static final String KEY_RUNTIME_WIDTH = "runtimeWidth";
  private static final String KEY_RUNTIME_HEIGHT = "runtimeHeight";
  private static final String KEY_RUNTIME_SIZE_EXPLICIT = "runtimeSizeExplicit";
  private static final String KEY_STORYBOARD_WIDTH = "storyboardWidth";
  private static final String KEY_STORYBOARD_HEIGHT = "storyboardHeight";
  private static final String KEY_STORYBOARD_SIZE_EXPLICIT = "storyboardSizeExplicit";
  private static final String KEY_SCALE = "scale";
  private static final String KEY_OFFSET_X = "offsetX";
  private static final String KEY_OFFSET_Y = "offsetY";
  private static final String CROP_PREFIX = "crop.";
  private static final String CROP_ENABLED = ".enabled";
  private static final String CROP_X = ".x";
  private static final String CROP_Y = ".y";
  private static final String CROP_W = ".w";
  private static final String CROP_H = ".h";
  private final Label titleLabel = new Label("Storyboard Overlay");
  private final Label summaryLabel =
      new Label("Pin a storyboard frame flush to the active preview for staging and shot matching.");
  private final Label targetLabel = new Label("Active preview: open a JES or VNS tab.");
  private final Label sourceLabel = new Label("Source: not scanned");
  private final Label framesSummaryLabel = new Label("No frames loaded.");
  private final Label matchLabel = new Label("No active scene match.");
  private final Label statusLabel = new Label("");
  private final TextField folderField = new TextField();
  private final TextField filterField = new TextField();
  private final ScrollPane toolScroll = new ScrollPane();
  private final ListView<StoryboardFrame> framesList = new ListView<>();
  private final ImageView previewImage = new ImageView();
  private final Canvas cropCanvas = new Canvas(280, 220);
  private final Label previewPathLabel = new Label("Select a storyboard frame.");
  private final Label previewMetaLabel = new Label("");
  private final Label cropMetaLabel = new Label("Crop: full frame");
  private final CheckBox enabledCheck = new CheckBox("Storyboard mode");
  private final CheckBox followActiveCheck = new CheckBox("Follow active scene");
  private final CheckBox hideUiCheck = new CheckBox("Hide VN UI");
  private final CheckBox cropEnabledCheck = new CheckBox("Show selected crop only");
  private final ComboBox<StoryboardOverlayState.FitMode> fitModeCombo = new ComboBox<>();
  private final TextField runtimeWidthField = new TextField();
  private final TextField runtimeHeightField = new TextField();
  private final TextField storyboardWidthField = new TextField();
  private final TextField storyboardHeightField = new TextField();
  private final Slider opacitySlider = new Slider(5, 100, 35);
  private final Label opacityValueLabel = new Label("35%");
  private final Slider scaleSlider = new Slider(25, 300, 100);
  private final Label scaleValueLabel = new Label("100%");
  private final TextField offsetXField = new TextField("0");
  private final TextField offsetYField = new TextField("0");
  private final Button previousButton = new Button("Previous");
  private final Button nextButton = new Button("Next");
  private final Button matchButton = new Button("Jump To Match");
  private final Button revealButton = new Button("Reveal Frame");
  private final Button browseButton = new Button("Browse");
  private final Button autoButton = new Button("Auto");
  private final Button refreshButton = new Button("Refresh");
  private final Button scaleBoardToRuntimeButton = new Button("Scale Board To Runtime");
  private final Button matchRuntimeToBoardButton = new Button("Match Runtime To Board");
  private final Button projectSizeButton = new Button("Project Size");
  private final Button imageSizeButton = new Button("Image Size");
  private final Button resetPlacementButton = new Button("Reset Align");
  private final Button enterCropModeButton = new Button("Crop Mode");
  private final Button applyCropButton = new Button("Apply Crop");
  private final Button exitCropModeButton = new Button("Exit Crop");
  private final Button expandCropButton = new Button("Full Screen Crop");
  private final Button clearCropButton = new Button("Clear Crop");

  private final List<StoryboardFrame> allFrames = new ArrayList<>();
  private final Map<Path, Image> imageCache = new HashMap<>();
  private final Properties persisted = new Properties();

  private File projectRoot;
  private File activeScriptFile;
  private StoryboardFrame activeMatchFrame;
  private boolean applyingState;
  private boolean runtimeSizeExplicit;
  private boolean storyboardSizeExplicit;
  private Consumer<StoryboardOverlayState> onOverlayChanged;
  private Task<StoryboardOverlayCatalog.ScanResult> scanTask;
  private double cropX;
  private double cropY;
  private double cropWidth;
  private double cropHeight;
  private double cropDragStartX;
  private double cropDragStartY;
  private boolean cropModeActive;
  private boolean draggingCrop;
  private Canvas activeCropCanvas;
  private ImageView activeCropImageView;
  private Scene activeCropScene;
  private EventHandler<MouseEvent> activeCropSceneDragHandler;
  private EventHandler<MouseEvent> activeCropSceneReleaseHandler;
  private Stage cropStage;
  private ImageView cropStageImage;
  private Canvas cropStageCanvas;
  private Label cropStageMetaLabel;
  private StackPane cropPreviewStack;

  public StoryboardOverlayView() {
    getStyleClass().add("sidebar-tool-root");
    titleLabel.getStyleClass().add("sidebar-tool-title");
    summaryLabel.getStyleClass().add("sidebar-tool-summary");
    summaryLabel.setWrapText(true);
    targetLabel.getStyleClass().add("sidebar-tool-subtitle");
    targetLabel.setWrapText(true);
    sourceLabel.getStyleClass().add("sidebar-tool-subtitle");
    sourceLabel.setWrapText(true);
    framesSummaryLabel.getStyleClass().add("sidebar-tool-subtitle");
    matchLabel.getStyleClass().addAll("sidebar-tool-status", "sidebar-tool-caution");
    matchLabel.setWrapText(true);
    statusLabel.getStyleClass().add("sidebar-tool-status");
    statusLabel.setWrapText(true);

    folderField.setPromptText("Auto-detect storyboard folder");
    folderField.getStyleClass().add("layout-launcher-field");
    filterField.setPromptText("Filter frames...");
    filterField.getStyleClass().add("layout-launcher-field");

    styleActionButton(browseButton, SidebarToolIcon.folder("#d5b36a"));
    styleActionButton(autoButton, SidebarToolIcon.search("#d6dbe5"));
    styleActionButton(refreshButton, SidebarToolIcon.refresh("#7ec8e3"));
    styleActionButton(previousButton, SidebarToolIcon.arrowLeft("#d6dbe5"));
    styleActionButton(nextButton, SidebarToolIcon.arrowRight("#d6dbe5"));
    styleActionButton(matchButton, SidebarToolIcon.link("#f0c27a"));
    styleActionButton(revealButton, SidebarToolIcon.expand("#d6dbe5"));
    styleActionButton(scaleBoardToRuntimeButton, SidebarToolIcon.expand("#f0c27a"));
    styleActionButton(matchRuntimeToBoardButton, SidebarToolIcon.grid("#f0c27a"));
    styleActionButton(projectSizeButton, SidebarToolIcon.grid("#d6dbe5"));
    styleActionButton(imageSizeButton, SidebarToolIcon.expand("#d6dbe5"));
    styleActionButton(resetPlacementButton, SidebarToolIcon.redo("#d6dbe5"));
    styleActionButton(enterCropModeButton, SidebarToolIcon.rectSelect("#f0c27a"));
    styleActionButton(applyCropButton, SidebarToolIcon.check("#8bd38b"));
    styleActionButton(exitCropModeButton, SidebarToolIcon.clearX("#d6dbe5"));
    styleActionButton(expandCropButton, SidebarToolIcon.popOut("#f0c27a"));
    styleActionButton(clearCropButton, SidebarToolIcon.clearX("#d6dbe5"));
    enabledCheck.setGraphic(SidebarToolIcon.visibility("#d6dbe5"));
    followActiveCheck.setGraphic(SidebarToolIcon.link("#d5b36a"));
    hideUiCheck.setGraphic(SidebarToolIcon.grid("#d6dbe5"));
    cropEnabledCheck.setGraphic(SidebarToolIcon.rectSelect("#f0c27a"));
    enabledCheck.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
    followActiveCheck.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
    hideUiCheck.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
    cropEnabledCheck.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);

    browseButton.setTooltip(new Tooltip("Choose a storyboard folder"));
    autoButton.setTooltip(new Tooltip("Return to automatic folder discovery"));
    refreshButton.setTooltip(new Tooltip("Rescan storyboard frames"));
    previousButton.setTooltip(new Tooltip("Select previous frame"));
    nextButton.setTooltip(new Tooltip("Select next frame"));
    matchButton.setTooltip(new Tooltip("Jump to the strongest match for the active JES/VNS file"));
    revealButton.setTooltip(new Tooltip("Open the selected frame in the editor image viewer"));
    scaleBoardToRuntimeButton.setTooltip(new Tooltip("Keep the game runtime size and scale the storyboard into it"));
    matchRuntimeToBoardButton.setTooltip(new Tooltip("Use the selected storyboard image size as the runtime viewport"));
    projectSizeButton.setTooltip(new Tooltip("Reset runtime size from the current project viewport"));
    imageSizeButton.setTooltip(new Tooltip("Reset storyboard size from the selected image dimensions"));
    resetPlacementButton.setTooltip(new Tooltip("Reset fit, scale, and offsets"));
    enterCropModeButton.setTooltip(new Tooltip("Enter crop mode for the selected storyboard frame"));
    applyCropButton.setTooltip(new Tooltip("Save the current crop rectangle and exit crop mode"));
    exitCropModeButton.setTooltip(new Tooltip("Exit crop mode without saving the current draft"));
    expandCropButton.setTooltip(new Tooltip("Open a large crop selector for the selected storyboard frame"));
    clearCropButton.setTooltip(new Tooltip("Clear the crop saved for this storyboard frame"));
    cropEnabledCheck.setTooltip(new Tooltip("Draw a rectangle on the preview to show only that part of this frame"));

    fitModeCombo.setItems(FXCollections.observableArrayList(StoryboardOverlayState.FitMode.values()));
    fitModeCombo.getSelectionModel().select(StoryboardOverlayState.FitMode.FIT);
    fitModeCombo.getStyleClass().add("layout-launcher-field");
    fitModeCombo.setTooltip(new Tooltip("How the storyboard frame maps onto the runtime viewport"));

    configureDimensionField(runtimeWidthField, "Width", "Runtime width");
    configureDimensionField(runtimeHeightField, "Height", "Runtime height");
    configureDimensionField(storyboardWidthField, "Width", "Storyboard width");
    configureDimensionField(storyboardHeightField, "Height", "Storyboard height");
    configureOffsetField(offsetXField, "Offset X");
    configureOffsetField(offsetYField, "Offset Y");

    filterField.textProperty().addListener((obs, oldValue, newValue) -> {
      applyFilter();
      saveState();
    });
    filterField.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.DOWN && !framesList.getItems().isEmpty()) {
        toolScroll.setVvalue(toolScroll.getVmax());
        framesList.requestFocus();
        int index = framesList.getSelectionModel().getSelectedIndex();
        framesList.getSelectionModel().select(index < 0 ? 0 : Math.min(index + 1, framesList.getItems().size() - 1));
        e.consume();
      } else if (e.getCode() == KeyCode.ENTER && framesList.getItems().size() == 1) {
        framesList.getSelectionModel().selectFirst();
        toolScroll.setVvalue(toolScroll.getVmax());
        framesList.requestFocus();
        e.consume();
      } else if (e.getCode() == KeyCode.ESCAPE && !filterField.getText().isBlank()) {
        filterField.clear();
        e.consume();
      }
    });
    folderField.setOnAction(e -> refreshCatalog());
    folderField.focusedProperty().addListener((obs, oldValue, focused) -> {
      if (!focused) refreshCatalog();
    });
    browseButton.setOnAction(e -> chooseFolder());
    autoButton.setOnAction(e -> {
      folderField.clear();
      refreshCatalog();
    });
    refreshButton.setOnAction(e -> refreshCatalog());

    framesList.setPlaceholder(new Label("No storyboard frames found"));
    framesList.setCellFactory(list -> new StoryboardFrameCell());
    framesList.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) {
        revealSelectedFrame();
        e.consume();
      } else if (e.getCode() == KeyCode.LEFT) {
        selectRelativeFrame(-1);
        e.consume();
      } else if (e.getCode() == KeyCode.RIGHT) {
        selectRelativeFrame(1);
        e.consume();
      } else if (e.getCode() == KeyCode.F && (e.isShortcutDown() || e.isMetaDown())) {
        filterField.requestFocus();
        filterField.selectAll();
        e.consume();
      }
    });
    framesList.getSelectionModel().selectedItemProperty().addListener((obs, oldFrame, newFrame) -> {
      updatePreview(newFrame);
      emitOverlayChanged();
      saveState();
    });
    framesList.setOnMouseClicked(e -> {
      if (e.getClickCount() >= 2) revealSelectedFrame();
    });

    enabledCheck.selectedProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingState) return;
      emitOverlayChanged();
      saveState();
    });
    hideUiCheck.selectedProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingState) return;
      emitOverlayChanged();
      saveState();
    });
    followActiveCheck.selectedProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingState) return;
      if (newValue) applyActiveContextMatch(true);
      saveState();
    });
    opacitySlider.valueProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingState) {
        opacityValueLabel.setText(Integer.toString((int) Math.round(newValue.doubleValue())) + "%");
        return;
      }
      opacityValueLabel.setText(Integer.toString((int) Math.round(newValue.doubleValue())) + "%");
      emitOverlayChanged();
      saveState();
    });
    scaleSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
      scaleValueLabel.setText(Integer.toString((int) Math.round(newValue.doubleValue())) + "%");
      if (applyingState) return;
      emitOverlayChanged();
      saveState();
    });
    fitModeCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingState) return;
      emitOverlayChanged();
      updatePreview(framesList.getSelectionModel().getSelectedItem());
      saveState();
    });
    cropEnabledCheck.selectedProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingState) return;
      persistSelectedCrop();
      drawCropOverlay();
      emitOverlayChanged();
      saveState();
    });

    previousButton.setOnAction(e -> selectRelativeFrame(-1));
    nextButton.setOnAction(e -> selectRelativeFrame(1));
    matchButton.setOnAction(e -> jumpToActiveMatch());
    revealButton.setOnAction(e -> revealSelectedFrame());
    scaleBoardToRuntimeButton.setOnAction(e -> scaleBoardToRuntime());
    matchRuntimeToBoardButton.setOnAction(e -> matchRuntimeToBoard());
    projectSizeButton.setOnAction(e -> useProjectRuntimeSize());
    imageSizeButton.setOnAction(e -> useSelectedImageSize());
    resetPlacementButton.setOnAction(e -> resetPlacement());
    enterCropModeButton.setOnAction(e -> enterCropMode());
    applyCropButton.setOnAction(e -> applyCropAndExit());
    exitCropModeButton.setOnAction(e -> exitCropMode(false));
    expandCropButton.setOnAction(e -> openCropStage());
    clearCropButton.setOnAction(e -> clearSelectedCrop());

    previewImage.setPreserveRatio(true);
    previewImage.setSmooth(true);
    previewImage.imageProperty().addListener((obs, oldImage, newImage) -> drawCropOverlay());
    cropCanvas.setFocusTraversable(true);
    cropCanvas.setPickOnBounds(true);
    cropCanvas.setCursor(Cursor.CROSSHAIR);

    previewPathLabel.setWrapText(true);
    previewMetaLabel.setWrapText(true);
    previewMetaLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #99aabb;");
    cropMetaLabel.setWrapText(true);
    cropMetaLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #d4b07c;");
    cropMetaLabel.setTooltip(new Tooltip("Drag on the preview image to save a crop for this storyboard frame"));

    Label filterHintLabel = new Label("↓ list  •  Enter: pick single  •  ⌘/Ctrl+F: focus");
    filterHintLabel.getStyleClass().add("sidebar-tool-subtitle");
    filterHintLabel.setWrapText(true);

    FlowPane folderRow = new FlowPane(6, 6, folderField, browseButton, autoButton, refreshButton);
    folderRow.setAlignment(Pos.CENTER_LEFT);
    folderField.prefWidthProperty().bind(Bindings.createDoubleBinding(() -> {
      double width = folderRow.getWidth();
      if (width <= 0) return 200.0;
      double actionsWidth = browseButton.prefWidth(-1) + autoButton.prefWidth(-1)
          + refreshButton.prefWidth(-1) + 3 * folderRow.getHgap();
      double remaining = width - actionsWidth;
      return remaining >= 175 ? Math.floor(remaining) : width;
    }, folderRow.widthProperty()));

    FlowPane checksRow = new FlowPane(12, 6, enabledCheck, followActiveCheck, hideUiCheck);
    checksRow.setAlignment(Pos.CENTER_LEFT);

    Label opacityLabel = new Label("Opacity");
    opacityLabel.getStyleClass().add("sidebar-tool-subtitle");
    opacityValueLabel.getStyleClass().add("sidebar-tool-subtitle");
    opacityValueLabel.setMinWidth(30);
    HBox opacityRow = new HBox(8, opacityLabel, opacitySlider, opacityValueLabel);
    opacityRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(opacitySlider, Priority.ALWAYS);

    Label fitLabel = new Label("Mode");
    fitLabel.getStyleClass().add("sidebar-tool-subtitle");
    HBox fitRow = new HBox(8, fitLabel, fitModeCombo);
    fitRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(fitModeCombo, Priority.ALWAYS);

    FlowPane setupActions = new FlowPane(8, 6, scaleBoardToRuntimeButton, matchRuntimeToBoardButton);
    setupActions.setAlignment(Pos.CENTER_LEFT);

    Label advancedLabel = new Label("Advanced mapping");
    advancedLabel.getStyleClass().add("sidebar-tool-section-title");

    Label runtimeLabel = new Label("Game");
    runtimeLabel.getStyleClass().add("sidebar-tool-subtitle");
    Label boardLabel = new Label("Board");
    boardLabel.getStyleClass().add("sidebar-tool-subtitle");
    HBox gameSize = new HBox(8, runtimeLabel, runtimeWidthField, runtimeHeightField);
    gameSize.setAlignment(Pos.CENTER_LEFT);
    HBox boardSize = new HBox(8, boardLabel, storyboardWidthField, storyboardHeightField);
    boardSize.setAlignment(Pos.CENTER_LEFT);
    FlowPane resolutionRow = new FlowPane(8, 6, gameSize, boardSize);
    resolutionRow.setAlignment(Pos.CENTER_LEFT);

    Label scaleLabel = new Label("Scale");
    scaleLabel.getStyleClass().add("sidebar-tool-subtitle");
    scaleValueLabel.getStyleClass().add("sidebar-tool-subtitle");
    scaleValueLabel.setMinWidth(38);
    scaleSlider.setPrefWidth(136);
    Label offsetLabel = new Label("Offset");
    offsetLabel.getStyleClass().add("sidebar-tool-subtitle");
    HBox scaleControls = new HBox(8, scaleLabel, scaleSlider, scaleValueLabel);
    scaleControls.setAlignment(Pos.CENTER_LEFT);
    HBox offsets = new HBox(8, offsetLabel, offsetXField, offsetYField);
    offsets.setAlignment(Pos.CENTER_LEFT);
    FlowPane transformRow = new FlowPane(8, 6, scaleControls, offsets);
    transformRow.setAlignment(Pos.CENTER_LEFT);

    FlowPane placementButtons = new FlowPane(8, 6, projectSizeButton, imageSizeButton, resetPlacementButton);
    placementButtons.setAlignment(Pos.CENTER_LEFT);

    FlowPane cropControls = new FlowPane(8, 6, cropEnabledCheck, enterCropModeButton, applyCropButton, exitCropModeButton, expandCropButton, clearCropButton);
    cropControls.setAlignment(Pos.CENTER_LEFT);

    VBox overlayControls = new VBox(6, checksRow, opacityRow, setupActions, advancedLabel, fitRow, resolutionRow, transformRow, placementButtons, cropControls);

    FlowPane navigationRow = new FlowPane(8, 6, previousButton, nextButton, matchButton, revealButton);
    navigationRow.setAlignment(Pos.CENTER_LEFT);

    HBox titleRow = new HBox(6, titleLabel, SidebarToolHelp.button(this, "Storyboard Overlay", """
        The Storyboard Overlay pins a storyboard image frame flush over the \
active VNS or JES preview window for shot matching and staging reference.

How to use:
  1. Put images in storyboard/ or game/storyboard/.
  2. Select a frame and turn on Storyboard mode.
  3. Use "Scale Board To Runtime" for normal game staging.
  4. Use "Match Runtime To Board" when the board image defines the target canvas.
  5. Only use Advanced mapping when the shot needs manual offsets.

"Follow active scene" mode automatically matches the overlay frame to the \
label name of the currently active script scene when a frame filename starts \
with or contains the label name — useful for structured storyboard exports.

"Hide UI" collapses editor chrome so you can compare composition cleanly."""));
    titleRow.setAlignment(Pos.CENTER_LEFT);
    VBox header = new VBox(6, titleRow, summaryLabel, targetLabel, sourceLabel, framesSummaryLabel, matchLabel, folderRow, filterField, filterHintLabel, overlayControls, navigationRow, statusLabel);
    header.getStyleClass().add("sidebar-tool-header");

    StackPane previewStack = new StackPane(previewImage, cropCanvas);
    cropPreviewStack = previewStack;
    previewStack.setAlignment(Pos.CENTER);
    previewStack.setMinSize(0, 140);
    previewStack.setPrefSize(520, 260);
    previewStack.setMaxSize(Double.MAX_VALUE, 520);
    installCropDragHandlers(previewStack, cropCanvas, previewImage);
    cropCanvas.widthProperty().bind(previewStack.widthProperty());
    cropCanvas.heightProperty().bind(previewStack.heightProperty());
    previewImage.fitWidthProperty().bind(previewStack.widthProperty());
    previewImage.fitHeightProperty().bind(previewStack.heightProperty());
    previewStack.widthProperty().addListener((obs, oldValue, newValue) -> drawCropOverlay());
    previewStack.heightProperty().addListener((obs, oldValue, newValue) -> drawCropOverlay());

    VBox previewBox = new VBox(8, previewStack, cropMetaLabel, previewPathLabel, previewMetaLabel);
    previewBox.setPadding(new Insets(10));
    previewBox.getStyleClass().add("storyboard-preview-box");
    framesList.setMinHeight(120);
    framesList.setPrefHeight(180);

    VBox content = new VBox(header, previewBox, new Separator(), framesList);
    toolScroll.setContent(content);
    toolScroll.getStyleClass().add("storyboard-tool-scroll");
    toolScroll.setFitToWidth(true);
    toolScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    setCenter(toolScroll);

    updateControlAvailability();
  }

  private void styleActionButton(Button button, Region icon) {
    button.getStyleClass().add("layout-launcher-button");
    button.setGraphic(icon);
    button.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
    button.setGraphicTextGap(6);
  }

  private void configureDimensionField(TextField field, String prompt, String accessibleText) {
    field.setPromptText(prompt);
    field.setAccessibleText(accessibleText);
    field.getStyleClass().add("layout-launcher-field");
    field.setPrefColumnCount(5);
    field.setOnAction(e -> commitPlacementField(field, false));
    field.focusedProperty().addListener((obs, oldValue, focused) -> {
      if (!focused) commitPlacementField(field, false);
    });
  }

  private void configureOffsetField(TextField field, String prompt) {
    field.setPromptText(prompt);
    field.getStyleClass().add("layout-launcher-field");
    field.setPrefColumnCount(5);
    field.setOnAction(e -> commitPlacementField(field, true));
    field.focusedProperty().addListener((obs, oldValue, focused) -> {
      if (!focused) commitPlacementField(field, true);
    });
  }

  private void commitPlacementField(TextField field, boolean allowNegative) {
    if (field == null) return;
    double fallback = allowNegative ? 0.0 : 1.0;
    double value = parseNumber(field.getText(), fallback);
    if (!allowNegative && value <= 0.0) value = fallback;
    field.setText(formatNumber(value));
    if (field == runtimeWidthField || field == runtimeHeightField) {
      runtimeSizeExplicit = true;
    } else if (field == storyboardWidthField || field == storyboardHeightField) {
      storyboardSizeExplicit = true;
    }
    if (applyingState) return;
    emitOverlayChanged();
    saveState();
  }

  public void setProjectRoot(File projectRoot) {
    if (Objects.equals(this.projectRoot, projectRoot)) {
      updateSummaryForProject();
      return;
    }
    this.projectRoot = projectRoot;
    loadState();
    refreshCatalog();
  }

  public void setActivePreviewLabel(String label) {
    if (label == null || label.isBlank()) {
      targetLabel.setText("Active preview: open a JES or VNS tab.");
      return;
    }
    targetLabel.setText(label);
  }

  public void setActiveScriptFile(File activeScriptFile) {
    File normalized = activeScriptFile == null ? null : activeScriptFile.getAbsoluteFile();
    if (Objects.equals(this.activeScriptFile, normalized)) return;
    this.activeScriptFile = normalized;
    applyActiveContextMatch(followActiveCheck.isSelected());
  }

  public void setOnOverlayChanged(Consumer<StoryboardOverlayState> onOverlayChanged) {
    this.onOverlayChanged = onOverlayChanged;
    emitOverlayChanged();
  }

  public void applyExternalState(StoryboardOverlayState state) {
    StoryboardOverlayState resolved = state == null ? StoryboardOverlayState.none() : state;
    applyingState = true;
    try {
      enabledCheck.setSelected(resolved.enabled());
      hideUiCheck.setSelected(resolved.hideUi());
      opacitySlider.setValue(resolved.opacity() * 100.0);
      opacityValueLabel.setText(Integer.toString((int) Math.round(opacitySlider.getValue())) + "%");
      fitModeCombo.getSelectionModel().select(resolved.fitMode());
      if (resolved.runtimeWidth() > 0.0) runtimeWidthField.setText(formatNumber(resolved.runtimeWidth()));
      if (resolved.runtimeHeight() > 0.0) runtimeHeightField.setText(formatNumber(resolved.runtimeHeight()));
      if (resolved.storyboardWidth() > 0.0) storyboardWidthField.setText(formatNumber(resolved.storyboardWidth()));
      if (resolved.storyboardHeight() > 0.0) storyboardHeightField.setText(formatNumber(resolved.storyboardHeight()));
      scaleSlider.setValue(resolved.scale() * 100.0);
      scaleValueLabel.setText(Integer.toString((int) Math.round(scaleSlider.getValue())) + "%");
      offsetXField.setText(formatNumber(resolved.offsetX()));
      offsetYField.setText(formatNumber(resolved.offsetY()));
      cropEnabledCheck.setSelected(resolved.cropEnabled());
      cropX = resolved.cropX();
      cropY = resolved.cropY();
      cropWidth = resolved.cropWidth();
      cropHeight = resolved.cropHeight();
      drawCropOverlay();
    } finally {
      applyingState = false;
    }
    saveState();
  }

  public void refreshCatalog() {
    saveState();
    if (scanTask != null) scanTask.cancel();
    allFrames.clear();
    framesList.getItems().clear();
    activeMatchFrame = null;
    previewImage.setImage(null);
    previewPathLabel.setText("Select a storyboard frame.");
    previewMetaLabel.setText("");
    clearCropFields();
    drawCropOverlay();
    imageCache.clear();
    updateControlAvailability();
    updateSummaryForProject();
    emitOverlayChanged();

    if (projectRoot == null || !projectRoot.isDirectory()) {
      sourceLabel.setText("Source: unavailable");
      statusLabel.setText("Open a project to browse storyboard frames.");
      return;
    }

    sourceLabel.setText("Source: scanning...");
    statusLabel.setText("Scanning storyboard frames...");

    final String folderOverride = folderField.getText();
    final File root = projectRoot;
    Task<StoryboardOverlayCatalog.ScanResult> task = new Task<>() {
      @Override
      protected StoryboardOverlayCatalog.ScanResult call() {
        return StoryboardOverlayCatalog.scan(root.toPath(), folderOverride);
      }
    };
    scanTask = task;
    task.setOnSucceeded(event -> {
      if (scanTask != task || task.isCancelled()) return;
      applyScanResult(task.getValue());
    });
    task.setOnFailed(event -> {
      if (scanTask != task || task.isCancelled()) return;
      Throwable failure = task.getException();
      sourceLabel.setText("Source: unavailable");
      statusLabel.setText("Storyboard scan failed: " + (failure == null ? "unknown error" : failure.getMessage()));
      updateControlAvailability();
    });
    Thread scanThread = new Thread(task, "jvn-storyboard-overlay-scan");
    scanThread.setDaemon(true);
    scanThread.start();
  }

  public void dispose() {
    if (scanTask != null) scanTask.cancel();
    uninstallSceneCropDragHandlers();
    saveState();
    imageCache.clear();
  }

  private void applyScanResult(StoryboardOverlayCatalog.ScanResult result) {
    sourceLabel.setText("Source: " + result.sourceLabel());
    statusLabel.setText(result.statusMessage());
    String desiredSelection = persisted.getProperty(KEY_SELECTED, "");
    StoryboardFrame previous = framesList.getSelectionModel().getSelectedItem();
    String previousSelection = previous == null ? desiredSelection : encodePath(previous.path());

    allFrames.clear();
    if (result.frames() != null) {
      for (Path frame : result.frames()) {
        allFrames.add(new StoryboardFrame(frame, StoryboardOverlayCatalog.displayPath(projectRoot == null ? null : projectRoot.toPath(), frame)));
      }
    }
    applyFilter();
    activeMatchFrame = findBestMatch(framesList.getItems());
    updateMatchLabel();
    reselectFrame(previousSelection);
    updateSummaryForProject();
    updateControlAvailability();
    emitOverlayChanged();
  }

  private void applyFilter() {
    StoryboardFrame currentSelection = framesList.getSelectionModel().getSelectedItem();
    String currentSelectionPath = currentSelection == null ? "" : encodePath(currentSelection.path());
    String query = filterField.getText();
    String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      framesList.setItems(FXCollections.observableArrayList(allFrames));
      reselectFilteredFrame(currentSelectionPath);
      activeMatchFrame = findBestMatch(framesList.getItems());
      updateFrameSummary();
      updateMatchLabel();
      updateControlAvailability();
      return;
    }
    List<StoryboardFrame> filtered = new ArrayList<>();
    for (StoryboardFrame frame : allFrames) {
      String haystack = frame.displayPath().toLowerCase(Locale.ROOT);
      if (haystack.contains(normalized)) filtered.add(frame);
    }
    framesList.setItems(FXCollections.observableArrayList(filtered));
    reselectFilteredFrame(currentSelectionPath);
    activeMatchFrame = findBestMatch(framesList.getItems());
    updateFrameSummary();
    updateMatchLabel();
    updateControlAvailability();
  }

  private void reselectFilteredFrame(String encodedPath) {
    if (encodedPath != null && !encodedPath.isBlank()) {
      for (StoryboardFrame frame : framesList.getItems()) {
        if (encodedPath.equals(encodePath(frame.path()))) {
          framesList.getSelectionModel().select(frame);
          return;
        }
      }
    }
    if (framesList.getSelectionModel().getSelectedItem() == null && !framesList.getItems().isEmpty()) {
      framesList.getSelectionModel().selectFirst();
    }
  }

  private void reselectFrame(String encodedPath) {
    if (encodedPath == null || encodedPath.isBlank()) {
      if (!framesList.getItems().isEmpty()) {
        framesList.getSelectionModel().selectFirst();
      }
      return;
    }
    for (StoryboardFrame frame : framesList.getItems()) {
      if (encodedPath.equals(encodePath(frame.path()))) {
        framesList.getSelectionModel().select(frame);
        framesList.scrollTo(frame);
        return;
      }
    }
    if (!framesList.getItems().isEmpty()) {
      framesList.getSelectionModel().selectFirst();
    }
  }

  private void updatePreview(StoryboardFrame frame) {
    if (frame == null) {
      previewImage.setImage(null);
      if (cropStageImage != null) cropStageImage.setImage(null);
      previewPathLabel.setText("Select a storyboard frame.");
      previewMetaLabel.setText("");
      clearCropFields();
      drawCropOverlay();
      updateControlAvailability();
      return;
    }
    previewPathLabel.setText(frame.displayPath());
    Image image = loadImage(frame.path());
    previewImage.setImage(image);
    if (cropStageImage != null) cropStageImage.setImage(image);
    applyPersistedCrop(frame);
    ProjectViewportSpec.Dimensions dims = ProjectViewportSpec.resolve(projectRoot);
    int selectedIndex = Math.max(0, framesList.getSelectionModel().getSelectedIndex()) + 1;
    int totalShown = framesList.getItems().size();
    if (image == null || image.isError()) {
      previewMetaLabel.setText("Preview unavailable.");
    } else {
      int imageWidth = (int) Math.round(image.getWidth());
      int imageHeight = (int) Math.round(image.getHeight());
      if (!storyboardSizeExplicit) {
        storyboardWidthField.setText(formatNumber(imageWidth));
        storyboardHeightField.setText(formatNumber(imageHeight));
      }
      double runtimeWidth = parseNumber(runtimeWidthField.getText(), dims.width());
      double runtimeHeight = parseNumber(runtimeHeightField.getText(), dims.height());
      double boardWidth = parseNumber(storyboardWidthField.getText(), imageWidth);
      double boardHeight = parseNumber(storyboardHeightField.getText(), imageHeight);
      String match = Math.round(boardWidth) == Math.round(runtimeWidth) && Math.round(boardHeight) == Math.round(runtimeHeight)
          ? "board and runtime are 1:1"
          : "board " + formatNumber(boardWidth) + "x" + formatNumber(boardHeight)
              + " -> runtime " + formatNumber(runtimeWidth) + "x" + formatNumber(runtimeHeight);
      StoryboardOverlayState.FitMode mode = fitModeCombo.getValue() == null
          ? StoryboardOverlayState.FitMode.FIT
          : fitModeCombo.getValue();
      previewMetaLabel.setText(
          imageWidth + "x" + imageHeight
              + "  •  " + mode.label()
              + "  •  " + match
              + "  •  Frame " + selectedIndex + " of " + totalShown);
    }
    drawCropOverlay();
    updateControlAvailability();
  }

  private Image loadImage(Path path) {
    if (path == null) return null;
    Path normalized = path.toAbsolutePath().normalize();
    Image cached = imageCache.get(normalized);
    if (cached != null) return cached;
    try {
      Image image = new Image(normalized.toUri().toString(), false);
      imageCache.put(normalized, image);
      return image;
    } catch (Exception ex) {
      return null;
    }
  }

  private void emitOverlayChanged() {
    if (onOverlayChanged == null) return;
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    Image image = selected == null ? null : loadImage(selected.path());
    if (!enabledCheck.isSelected() || selected == null || image == null || image.isError()) {
      onOverlayChanged.accept(StoryboardOverlayState.none());
      return;
    }
    double effectiveCropWidth = effectiveCropWidth(image);
    double effectiveCropHeight = effectiveCropHeight(image);
    onOverlayChanged.accept(new StoryboardOverlayState(
        true,
        image,
        opacitySlider.getValue() / 100.0,
        selected.displayPath(),
        hideUiCheck.isSelected(),
        fitModeCombo.getValue(),
        parseNumber(runtimeWidthField.getText(), ProjectViewportSpec.resolve(projectRoot).width()),
        parseNumber(runtimeHeightField.getText(), ProjectViewportSpec.resolve(projectRoot).height()),
        parseNumber(storyboardWidthField.getText(), image.getWidth()),
        parseNumber(storyboardHeightField.getText(), image.getHeight()),
        scaleSlider.getValue() / 100.0,
        parseNumber(offsetXField.getText(), 0.0),
        parseNumber(offsetYField.getText(), 0.0),
        cropEnabledCheck.isSelected() && effectiveCropWidth > 0.0 && effectiveCropHeight > 0.0,
        cropX,
        cropY,
        effectiveCropWidth,
        effectiveCropHeight));
  }

  private void selectRelativeFrame(int direction) {
    if (framesList.getItems().isEmpty()) return;
    int current = framesList.getSelectionModel().getSelectedIndex();
    if (current < 0) current = 0;
    int next = current + direction;
    if (next < 0) next = framesList.getItems().size() - 1;
    if (next >= framesList.getItems().size()) next = 0;
    framesList.getSelectionModel().select(next);
    framesList.scrollTo(next);
  }

  private void chooseFolder() {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Select Storyboard Folder");
    Path initial = resolveInitialFolder();
    if (initial != null && Files.isDirectory(initial)) {
      chooser.setInitialDirectory(initial.toFile());
    } else if (projectRoot != null && projectRoot.isDirectory()) {
      chooser.setInitialDirectory(projectRoot);
    }
    File selected = chooser.showDialog(getScene() == null ? null : getScene().getWindow());
    if (selected == null) return;
    folderField.setText(StoryboardOverlayCatalog.displayPath(projectRoot == null ? null : projectRoot.toPath(), selected.toPath()));
    refreshCatalog();
  }

  private void jumpToActiveMatch() {
    if (activeMatchFrame == null) return;
    framesList.getSelectionModel().select(activeMatchFrame);
    framesList.scrollTo(activeMatchFrame);
  }

  private void revealSelectedFrame() {
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    if (selected == null) return;
    Image image = loadImage(selected.path());
    if (!isUsableImage(image)) {
      statusLabel.setText("Could not open frame image.");
      return;
    }
    openFrameViewer(selected, image);
  }

  private void openFrameViewer(StoryboardFrame frame, Image image) {
    ImageView imageView = new ImageView(image);
    imageView.setPreserveRatio(true);
    imageView.setSmooth(true);

    StackPane imageHost = new StackPane(imageView);
    imageHost.setStyle("-fx-background-color: #151922;");
    imageHost.setMinSize(320, 220);
    imageView.fitWidthProperty().bind(imageHost.widthProperty());
    imageView.fitHeightProperty().bind(imageHost.heightProperty());

    Label pathLabel = new Label(frame.displayPath());
    pathLabel.setWrapText(true);
    pathLabel.setPadding(new Insets(8, 10, 8, 10));
    pathLabel.setStyle("-fx-background-color: #202633; -fx-text-fill: #d8dee9; -fx-font-size: 11px;");

    BorderPane root = new BorderPane(imageHost);
    root.setBottom(pathLabel);

    Stage stage = new Stage();
    stage.setTitle("Storyboard Frame - " + frame.fileName());
    if (getScene() != null && getScene().getWindow() != null) {
      stage.initOwner(getScene().getWindow());
    }
    stage.setScene(new Scene(root, 960, 640));
    stage.show();
  }

  private void useProjectRuntimeSize() {
    ProjectViewportSpec.Dimensions dims = ProjectViewportSpec.resolve(projectRoot);
    runtimeSizeExplicit = true;
    runtimeWidthField.setText(Integer.toString(dims.width()));
    runtimeHeightField.setText(Integer.toString(dims.height()));
    emitOverlayChanged();
    saveState();
    updatePreview(framesList.getSelectionModel().getSelectedItem());
  }

  private void useSelectedImageSize() {
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    Image image = selected == null ? null : loadImage(selected.path());
    if (image == null || image.isError()) return;
    storyboardSizeExplicit = true;
    storyboardWidthField.setText(formatNumber(image.getWidth()));
    storyboardHeightField.setText(formatNumber(image.getHeight()));
    emitOverlayChanged();
    saveState();
    updatePreview(selected);
  }

  private void scaleBoardToRuntime() {
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    Image image = selected == null ? null : loadImage(selected.path());
    ProjectViewportSpec.Dimensions dims = ProjectViewportSpec.resolve(projectRoot);
    runtimeSizeExplicit = true;
    runtimeWidthField.setText(Integer.toString(dims.width()));
    runtimeHeightField.setText(Integer.toString(dims.height()));
    if (image != null && !image.isError()) {
      storyboardSizeExplicit = true;
      storyboardWidthField.setText(formatNumber(image.getWidth()));
      storyboardHeightField.setText(formatNumber(image.getHeight()));
    }
    fitModeCombo.getSelectionModel().select(StoryboardOverlayState.FitMode.FIT);
    scaleSlider.setValue(100.0);
    offsetXField.setText("0");
    offsetYField.setText("0");
    emitOverlayChanged();
    saveState();
    updatePreview(selected);
    statusLabel.setText("Storyboard will scale into the project runtime viewport.");
  }

  private void matchRuntimeToBoard() {
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    Image image = selected == null ? null : loadImage(selected.path());
    if (image == null || image.isError()) return;
    String width = formatNumber(image.getWidth());
    String height = formatNumber(image.getHeight());
    runtimeSizeExplicit = true;
    storyboardSizeExplicit = true;
    runtimeWidthField.setText(width);
    runtimeHeightField.setText(height);
    storyboardWidthField.setText(width);
    storyboardHeightField.setText(height);
    fitModeCombo.getSelectionModel().select(StoryboardOverlayState.FitMode.STRETCH);
    scaleSlider.setValue(100.0);
    offsetXField.setText("0");
    offsetYField.setText("0");
    emitOverlayChanged();
    saveState();
    updatePreview(selected);
    statusLabel.setText("Runtime viewport now matches the selected storyboard frame.");
  }

  private void resetPlacement() {
    fitModeCombo.getSelectionModel().select(StoryboardOverlayState.FitMode.FIT);
    scaleSlider.setValue(100.0);
    offsetXField.setText("0");
    offsetYField.setText("0");
    emitOverlayChanged();
    saveState();
  }

  private void openCropStage() {
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    Image image = selected == null ? null : loadImage(selected.path());
    if (image == null || image.isError()) return;
    enterCropMode();
    if (cropStage != null && cropStage.isShowing()) {
      cropStage.toFront();
      cropStage.requestFocus();
      return;
    }

    cropStageImage = new ImageView(image);
    cropStageImage.setPreserveRatio(true);
    cropStageImage.setSmooth(true);
    cropStageCanvas = new Canvas(1280, 720);
    cropStageCanvas.setFocusTraversable(true);
    cropStageCanvas.setPickOnBounds(true);
    cropStageCanvas.setCursor(Cursor.CROSSHAIR);

    StackPane cropHost = new StackPane(cropStageImage, cropStageCanvas);
    cropHost.setAlignment(Pos.CENTER);
    cropHost.setStyle("-fx-background-color: #080a0f;");
    installCropDragHandlers(cropHost, cropStageCanvas, cropStageImage);
    cropStageCanvas.widthProperty().bind(cropHost.widthProperty());
    cropStageCanvas.heightProperty().bind(cropHost.heightProperty());
    cropStageImage.fitWidthProperty().bind(cropHost.widthProperty());
    cropStageImage.fitHeightProperty().bind(cropHost.heightProperty());
    cropHost.widthProperty().addListener((obs, oldValue, newValue) -> drawCropOverlay());
    cropHost.heightProperty().addListener((obs, oldValue, newValue) -> drawCropOverlay());

    cropStageMetaLabel = new Label("");
    cropStageMetaLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #f0c27a;");
    cropStageMetaLabel.setWrapText(true);
    Label title = new Label(selected.displayPath());
    title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #d6dbe5;");
    title.setWrapText(true);
    Button closeButton = new Button("Close");
    styleActionButton(closeButton, SidebarToolIcon.clearX("#d6dbe5"));
    closeButton.setOnAction(e -> {
      if (cropStage != null) cropStage.close();
    });
    HBox top = new HBox(10, title, cropStageMetaLabel, closeButton);
    top.setAlignment(Pos.CENTER_LEFT);
    top.setPadding(new Insets(10));
    HBox.setHgrow(title, Priority.ALWAYS);
    HBox.setHgrow(cropStageMetaLabel, Priority.ALWAYS);
    top.setStyle("-fx-background-color: #111318; -fx-border-color: #2a2f3a; -fx-border-width: 0 0 1 0;");

    BorderPane root = new BorderPane(cropHost);
    root.setTop(top);
    Scene scene = new Scene(root, 1280, 820);
    scene.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ESCAPE && cropStage != null) {
        cropStage.close();
        e.consume();
      }
    });
    EditorTheme.apply(scene);

    cropStage = new Stage();
    if (getScene() != null && getScene().getWindow() != null) {
      cropStage.initOwner(getScene().getWindow());
    }
    cropStage.setTitle("Storyboard Crop - " + selected.fileName());
    cropStage.setScene(scene);
    cropStage.setMinWidth(720);
    cropStage.setMinHeight(480);
    cropStage.setOnHidden(e -> {
      if (activeCropScene == cropStage.getScene()) {
        cancelActiveCropDrag();
      }
      cropStage = null;
      cropStageImage = null;
      cropStageCanvas = null;
      cropStageMetaLabel = null;
    });
    cropStage.show();
    cropStage.setFullScreen(true);
    cropStage.setFullScreenExitHint("");
    drawCropOverlay();
  }

  private void installCropDragHandlers(StackPane host, Canvas canvas, ImageView imageView) {
    if (host == null || canvas == null || imageView == null) return;
    host.setPickOnBounds(true);
    host.setCursor(Cursor.CROSSHAIR);
    imageView.setMouseTransparent(true);
    host.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      Point2D point = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
      if (beginCropDrag(canvas, imageView, point.getX(), point.getY())) {
        e.consume();
      }
    });
    host.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
      if (!draggingCrop) return;
      Point2D point = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
      updateCropDrag(canvas, imageView, point.getX(), point.getY());
      e.consume();
    });
    host.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
      if (!draggingCrop || e.getButton() != MouseButton.PRIMARY) return;
      Point2D point = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
      finishCropDrag(canvas, imageView, point.getX(), point.getY());
      e.consume();
    });
    canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      canvas.requestFocus();
      if (beginCropDrag(canvas, imageView, e.getX(), e.getY())) {
        e.consume();
      }
    });
    canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
      if (!draggingCrop) return;
      updateCropDrag(canvas, imageView, e.getX(), e.getY());
      e.consume();
    });
    canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      finishCropDrag(canvas, imageView, e.getX(), e.getY());
      e.consume();
    });
  }

  private boolean beginCropDrag(Canvas canvas, ImageView imageView, double canvasX, double canvasY) {
    if (!cropModeActive) return false;
    if (draggingCrop) return true;
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    if (selected == null) return false;
    Image image = imageView == null ? null : imageView.getImage();
    if (!isUsableImage(image)) {
      image = loadImage(selected.path());
    }
    if (image == null || image.isError()) return false;
    ImagePoint point = canvasToImagePoint(canvas, imageView, canvasX, canvasY, image);
    if (point == null) return false;
    draggingCrop = true;
    cropDragStartX = point.x();
    cropDragStartY = point.y();
    activeCropCanvas = canvas;
    activeCropImageView = imageView;
    installSceneCropDragHandlers(canvas, imageView);
    cropX = point.x();
    cropY = point.y();
    cropWidth = 0.0;
    cropHeight = 0.0;
    statusLabel.setText("Crop mode: dragging rectangle on " + selected.fileName() + "...");
    drawCropOverlay();
    return true;
  }

  private void updateCropDrag(Canvas canvas, ImageView imageView, double canvasX, double canvasY) {
    if (!draggingCrop) return;
    Image image = imageView == null ? null : imageView.getImage();
    ImagePoint point = canvasToImagePoint(canvas, imageView, canvasX, canvasY, image);
    if (point == null) return;
    cropX = Math.min(cropDragStartX, point.x());
    cropY = Math.min(cropDragStartY, point.y());
    cropWidth = Math.abs(point.x() - cropDragStartX);
    cropHeight = Math.abs(point.y() - cropDragStartY);
    drawCropOverlay();
    emitOverlayChanged();
  }

  private void finishCropDrag(Canvas canvas, ImageView imageView, double canvasX, double canvasY) {
    if (!draggingCrop) return;
    updateCropDrag(canvas, imageView, canvasX, canvasY);
    draggingCrop = false;
    uninstallSceneCropDragHandlers();
    Image image = imageView == null ? null : imageView.getImage();
    if (!hasValidCrop(image)) {
      statusLabel.setText(invalidCropStatus("Crop mode"));
      drawCropOverlay();
      updateControlAvailability();
      return;
    }
    statusLabel.setText("Crop draft ready. Click Apply Crop to save it.");
    drawCropOverlay();
    updateControlAvailability();
  }

  private void enterCropMode() {
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    if (selected == null) return;
    cropModeActive = true;
    draggingCrop = false;
    uninstallSceneCropDragHandlers();
    statusLabel.setText("Crop mode: drag a rectangle over the storyboard preview.");
    drawCropOverlay();
    updateControlAvailability();
  }

  private void applyCropAndExit() {
    if (!cropModeActive && !hasValidCrop(activeCropImage())) return;
    draggingCrop = false;
    uninstallSceneCropDragHandlers();
    if (!hasValidCrop(activeCropImage())) {
      statusLabel.setText(invalidCropStatus("No crop to apply"));
      drawCropOverlay();
      updateControlAvailability();
      return;
    }
    setCropEnabledSilently(true);
    persistSelectedCrop();
    cropModeActive = false;
    drawCropOverlay();
    emitOverlayChanged();
    saveState();
    statusLabel.setText("Saved crop " + formatNumber(cropWidth) + "x" + formatNumber(cropHeight) + ".");
    updateControlAvailability();
  }

  private void exitCropMode(boolean keepDraft) {
    if (!cropModeActive && !draggingCrop) return;
    draggingCrop = false;
    uninstallSceneCropDragHandlers();
    cropModeActive = false;
    if (!keepDraft) {
      applyPersistedCrop(framesList.getSelectionModel().getSelectedItem());
    }
    statusLabel.setText(keepDraft ? "Exited crop mode." : "Exited crop mode without applying draft.");
    drawCropOverlay();
    emitOverlayChanged();
    updateControlAvailability();
  }

  private ImagePoint canvasToImagePoint(Canvas canvas, ImageView imageView, double canvasX, double canvasY, Image image) {
    if (canvas == null || imageView == null || image == null || image.isError() || image.getWidth() <= 0.0 || image.getHeight() <= 0.0) return null;
    PreviewImageBounds bounds = previewImageBounds(canvas, imageView, image);
    if (bounds.width() <= 0.0 || bounds.height() <= 0.0) return null;
    double clampedX = Math.max(bounds.x(), Math.min(bounds.x() + bounds.width(), canvasX));
    double clampedY = Math.max(bounds.y(), Math.min(bounds.y() + bounds.height(), canvasY));
    double imageX = (clampedX - bounds.x()) / bounds.width() * image.getWidth();
    double imageY = (clampedY - bounds.y()) / bounds.height() * image.getHeight();
    return new ImagePoint(
        Math.max(0.0, Math.min(image.getWidth(), imageX)),
        Math.max(0.0, Math.min(image.getHeight(), imageY)));
  }

  private PreviewImageBounds previewImageBounds(Canvas canvas, ImageView imageView, Image image) {
    double imageWidth = image == null ? 0.0 : image.getWidth();
    double imageHeight = image == null ? 0.0 : image.getHeight();
    double fitWidth = imageView != null && imageView.getFitWidth() > 0.0 ? imageView.getFitWidth() : canvas.getWidth();
    double fitHeight = imageView != null && imageView.getFitHeight() > 0.0 ? imageView.getFitHeight() : canvas.getHeight();
    if (imageWidth <= 0.0 || imageHeight <= 0.0 || fitWidth <= 0.0 || fitHeight <= 0.0) {
      return new PreviewImageBounds(0, 0, 0, 0);
    }
    double scale = Math.min(fitWidth / imageWidth, fitHeight / imageHeight);
    double width = imageWidth * scale;
    double height = imageHeight * scale;
    return new PreviewImageBounds((canvas.getWidth() - width) / 2.0, (canvas.getHeight() - height) / 2.0, width, height);
  }

  private void drawCropOverlay() {
    drawCropOverlay(cropCanvas, previewImage, cropMetaLabel);
    if (cropStageCanvas != null && cropStageImage != null) {
      drawCropOverlay(cropStageCanvas, cropStageImage, cropStageMetaLabel);
    }
  }

  private void drawCropOverlay(Canvas canvas, ImageView imageView, Label metaLabel) {
    if (canvas == null || imageView == null) return;
    GraphicsContext gc = canvas.getGraphicsContext2D();
    gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    Image image = imageView.getImage();
    if (image == null || image.isError()) {
      if (metaLabel != null) metaLabel.setText("Crop: select a storyboard frame.");
      return;
    }
    PreviewImageBounds imageBounds = previewImageBounds(canvas, imageView, image);
    gc.setStroke(Color.rgb(154, 170, 187, 0.55));
    gc.setLineDashes(5, 4);
    gc.strokeRect(imageBounds.x(), imageBounds.y(), imageBounds.width(), imageBounds.height());
    gc.setLineDashes(null);

    boolean drawingInProgress = draggingCrop && cropWidth > 0.0 && cropHeight > 0.0;
    if (!hasValidCrop(image) && !drawingInProgress) {
      if (metaLabel != null) {
        metaLabel.setText(cropModeActive
            ? "Crop mode: drag on the preview, then Apply Crop."
            : "Crop: enter crop mode to draw a source rectangle.");
      }
      return;
    }

    PreviewImageBounds cropBounds = imageCropBounds(imageBounds, image);
    if (cropEnabledCheck.isSelected() && !cropModeActive && !drawingInProgress) {
      gc.setFill(Color.rgb(8, 11, 18, 0.48));
      gc.fillRect(imageBounds.x(), imageBounds.y(), imageBounds.width(), cropBounds.y() - imageBounds.y());
      gc.fillRect(imageBounds.x(), cropBounds.y() + cropBounds.height(), imageBounds.width(), imageBounds.y() + imageBounds.height() - cropBounds.y() - cropBounds.height());
      gc.fillRect(imageBounds.x(), cropBounds.y(), cropBounds.x() - imageBounds.x(), cropBounds.height());
      gc.fillRect(cropBounds.x() + cropBounds.width(), cropBounds.y(), imageBounds.x() + imageBounds.width() - cropBounds.x() - cropBounds.width(), cropBounds.height());
    }
    gc.setStroke(Color.rgb(240, 194, 122, 0.98));
    gc.setLineWidth(2.0);
    if (drawingInProgress || cropModeActive) {
      gc.setLineDashes(7, 5);
    }
    gc.strokeRect(cropBounds.x(), cropBounds.y(), cropBounds.width(), cropBounds.height());
    gc.setLineDashes(null);
    gc.setLineWidth(1.0);
    if (metaLabel != null) {
      metaLabel.setText(
          "Crop: "
              + formatNumber(cropX)
              + ", "
              + formatNumber(cropY)
              + "  "
              + formatNumber(effectiveCropWidth(image))
              + "x"
              + formatNumber(effectiveCropHeight(image))
              + (cropModeActive
                  ? " draft"
                  : cropEnabledCheck.isSelected() ? " shown" : " saved, full frame shown"));
    }
  }

  private PreviewImageBounds imageCropBounds(PreviewImageBounds imageBounds, Image image) {
    double sourceX = Math.max(0.0, Math.min(image.getWidth(), cropX));
    double sourceY = Math.max(0.0, Math.min(image.getHeight(), cropY));
    double sourceWidth = Math.max(0.0, Math.min(image.getWidth() - sourceX, cropWidth));
    double sourceHeight = Math.max(0.0, Math.min(image.getHeight() - sourceY, cropHeight));
    double x = imageBounds.x() + sourceX / image.getWidth() * imageBounds.width();
    double y = imageBounds.y() + sourceY / image.getHeight() * imageBounds.height();
    double width = sourceWidth / image.getWidth() * imageBounds.width();
    double height = sourceHeight / image.getHeight() * imageBounds.height();
    return new PreviewImageBounds(x, y, width, height);
  }

  private void applyPersistedCrop(StoryboardFrame frame) {
    clearCropFields();
    if (frame == null) {
      setCropEnabledSilently(false);
      return;
    }
    String prefix = cropPropertyPrefix(frame);
    cropX = parseNumber(persisted.getProperty(prefix + CROP_X), 0.0);
    cropY = parseNumber(persisted.getProperty(prefix + CROP_Y), 0.0);
    cropWidth = parseNumber(persisted.getProperty(prefix + CROP_W), 0.0);
    cropHeight = parseNumber(persisted.getProperty(prefix + CROP_H), 0.0);
    Image image = loadImage(frame.path());
    setCropEnabledSilently(Boolean.parseBoolean(persisted.getProperty(prefix + CROP_ENABLED, "false")) && hasValidCrop(image));
  }

  private void persistSelectedCrop() {
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    if (selected == null) return;
    if (hasValidCrop(loadImage(selected.path()))) {
      writeCropProperties(persisted, selected);
    } else {
      removeCropProperties(persisted, selected);
    }
  }

  private void clearSelectedCrop() {
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    if (selected != null) removeCropProperties(persisted, selected);
    clearCropFields();
    cropModeActive = false;
    setCropEnabledSilently(false);
    drawCropOverlay();
    emitOverlayChanged();
    saveState();
    updateControlAvailability();
  }

  private void clearCropFields() {
    cropX = 0.0;
    cropY = 0.0;
    cropWidth = 0.0;
    cropHeight = 0.0;
    cropModeActive = false;
    draggingCrop = false;
    activeCropCanvas = null;
    activeCropImageView = null;
    uninstallSceneCropDragHandlers();
  }

  private void cancelActiveCropDrag() {
    draggingCrop = false;
    cropModeActive = false;
    activeCropCanvas = null;
    activeCropImageView = null;
    uninstallSceneCropDragHandlers();
    drawCropOverlay();
    updateControlAvailability();
  }

  private void installSceneCropDragHandlers(Canvas canvas, ImageView imageView) {
    uninstallSceneCropDragHandlers();
    Scene scene = canvas == null ? null : canvas.getScene();
    if (scene == null) return;
    activeCropScene = scene;
    activeCropSceneDragHandler = event -> {
      if (!draggingCrop || activeCropCanvas == null || activeCropImageView == null) return;
      Point2D point = activeCropCanvas.sceneToLocal(event.getSceneX(), event.getSceneY());
      updateCropDrag(activeCropCanvas, activeCropImageView, point.getX(), point.getY());
      event.consume();
    };
    activeCropSceneReleaseHandler = event -> {
      if (!draggingCrop || activeCropCanvas == null || activeCropImageView == null) return;
      Point2D point = activeCropCanvas.sceneToLocal(event.getSceneX(), event.getSceneY());
      finishCropDrag(activeCropCanvas, activeCropImageView, point.getX(), point.getY());
      event.consume();
    };
    scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, activeCropSceneDragHandler);
    scene.addEventFilter(MouseEvent.MOUSE_RELEASED, activeCropSceneReleaseHandler);
  }

  private void uninstallSceneCropDragHandlers() {
    if (activeCropScene != null) {
      if (activeCropSceneDragHandler != null) {
        activeCropScene.removeEventFilter(MouseEvent.MOUSE_DRAGGED, activeCropSceneDragHandler);
      }
      if (activeCropSceneReleaseHandler != null) {
        activeCropScene.removeEventFilter(MouseEvent.MOUSE_RELEASED, activeCropSceneReleaseHandler);
      }
    }
    activeCropScene = null;
    activeCropSceneDragHandler = null;
    activeCropSceneReleaseHandler = null;
  }

  private boolean hasValidCrop(Image image) {
    return image != null
        && !image.isError()
        && cropWidth >= 1.0
        && cropHeight >= 1.0
        && cropX >= 0.0
        && cropY >= 0.0
        && cropX < image.getWidth()
        && cropY < image.getHeight();
  }

  private Image activeCropImage() {
    Image image = activeCropImageView == null ? null : activeCropImageView.getImage();
    if (isUsableImage(image)) return image;
    image = cropStageImage == null ? null : cropStageImage.getImage();
    if (isUsableImage(image)) return image;
    image = previewImage.getImage();
    if (isUsableImage(image)) return image;
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    return selected == null ? null : loadImage(selected.path());
  }

  private boolean isUsableImage(Image image) {
    return image != null && !image.isError() && image.getWidth() > 0.0 && image.getHeight() > 0.0;
  }

  private String invalidCropStatus(String prefix) {
    return prefix
        + ": drag a larger rectangle, then Apply Crop. Current draft is "
        + formatNumber(cropWidth)
        + "x"
        + formatNumber(cropHeight)
        + ".";
  }

  private void setCropEnabledSilently(boolean enabled) {
    boolean previous = applyingState;
    applyingState = true;
    try {
      cropEnabledCheck.setSelected(enabled);
    } finally {
      applyingState = previous;
    }
  }

  private void writeCropProperties(Properties props, StoryboardFrame frame) {
    if (props == null || frame == null) return;
    Image image = loadImage(frame.path());
    if (!hasValidCrop(image)) {
      removeCropProperties(props, frame);
      return;
    }
    String prefix = cropPropertyPrefix(frame);
    props.setProperty(prefix + CROP_ENABLED, Boolean.toString(cropEnabledCheck.isSelected()));
    props.setProperty(prefix + CROP_X, formatNumber(cropX));
    props.setProperty(prefix + CROP_Y, formatNumber(cropY));
    props.setProperty(prefix + CROP_W, formatNumber(effectiveCropWidth(image)));
    props.setProperty(prefix + CROP_H, formatNumber(effectiveCropHeight(image)));
  }

  private double effectiveCropWidth(Image image) {
    if (image == null || image.isError() || image.getWidth() <= 0.0) return 0.0;
    double sourceX = Math.max(0.0, Math.min(image.getWidth(), cropX));
    return Math.max(0.0, Math.min(image.getWidth() - sourceX, cropWidth));
  }

  private double effectiveCropHeight(Image image) {
    if (image == null || image.isError() || image.getHeight() <= 0.0) return 0.0;
    double sourceY = Math.max(0.0, Math.min(image.getHeight(), cropY));
    return Math.max(0.0, Math.min(image.getHeight() - sourceY, cropHeight));
  }

  private void removeCropProperties(Properties props, StoryboardFrame frame) {
    if (props == null || frame == null) return;
    String prefix = cropPropertyPrefix(frame);
    props.remove(prefix + CROP_ENABLED);
    props.remove(prefix + CROP_X);
    props.remove(prefix + CROP_Y);
    props.remove(prefix + CROP_W);
    props.remove(prefix + CROP_H);
  }

  private void copyPersistedCropProperties(Properties props) {
    if (props == null) return;
    for (String key : persisted.stringPropertyNames()) {
      if (key.startsWith(CROP_PREFIX)) {
        props.setProperty(key, persisted.getProperty(key, ""));
      }
    }
  }

  private String cropPropertyPrefix(StoryboardFrame frame) {
    String path = frame == null ? "" : encodePath(frame.path());
    String encoded = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(path.getBytes(StandardCharsets.UTF_8));
    return CROP_PREFIX + encoded;
  }

  private Path resolveInitialFolder() {
    if (projectRoot == null) return null;
    String override = cleanPathValue(folderField.getText());
    if (override == null || override.isBlank()) return projectRoot.toPath();
    Path path = Path.of(override);
    if (!path.isAbsolute()) path = projectRoot.toPath().resolve(path).normalize();
    return path.toAbsolutePath().normalize();
  }

  private void updateSummaryForProject() {
    ProjectViewportSpec.Dimensions dims = ProjectViewportSpec.resolve(projectRoot);
    summaryLabel.setText(
        "Use storyboard mode to stage JES and VNS scenes against a frame locked to the active preview. "
            + "Best alignment comes from "
            + dims.width()
            + "x"
            + dims.height()
            + " boards.");
    updateFrameSummary();
    updateMatchLabel();
  }

  private void updateControlAvailability() {
    boolean hasFrames = !framesList.getItems().isEmpty();
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    boolean hasSelection = selected != null;
    boolean hasCrop = hasSelection && hasValidCrop(loadImage(selected.path()));
    enabledCheck.setDisable(!hasSelection);
    hideUiCheck.setDisable(!hasSelection);
    opacitySlider.setDisable(!hasSelection);
    followActiveCheck.setDisable(activeScriptFile == null);
    previousButton.setDisable(!hasFrames);
    nextButton.setDisable(!hasFrames);
    matchButton.setDisable(activeMatchFrame == null);
    revealButton.setDisable(!hasSelection);
    imageSizeButton.setDisable(!hasSelection);
    projectSizeButton.setDisable(projectRoot == null || !projectRoot.isDirectory());
    resetPlacementButton.setDisable(!hasSelection);
    scaleBoardToRuntimeButton.setDisable(!hasSelection || projectRoot == null || !projectRoot.isDirectory());
    matchRuntimeToBoardButton.setDisable(!hasSelection);
    cropEnabledCheck.setDisable(!hasSelection || cropModeActive || !hasCrop);
    enterCropModeButton.setDisable(!hasSelection || cropModeActive);
    applyCropButton.setDisable(!hasSelection || !cropModeActive || !hasValidCrop(activeCropImage()));
    exitCropModeButton.setDisable(!cropModeActive);
    expandCropButton.setDisable(!hasSelection || cropModeActive);
    clearCropButton.setDisable(!hasSelection || !hasCrop);
    if (cropPreviewStack != null) cropPreviewStack.setCursor(hasSelection ? Cursor.CROSSHAIR : Cursor.DEFAULT);
    cropCanvas.setMouseTransparent(!hasSelection || !cropModeActive);
  }

  private void loadState() {
    persisted.clear();
    applyingState = true;
    try {
      folderField.clear();
      filterField.clear();
      enabledCheck.setSelected(false);
      followActiveCheck.setSelected(true);
      hideUiCheck.setSelected(false);
      runtimeSizeExplicit = false;
      storyboardSizeExplicit = false;
      opacitySlider.setValue(35.0);
      ProjectViewportSpec.Dimensions dims = ProjectViewportSpec.resolve(projectRoot);
      fitModeCombo.getSelectionModel().select(StoryboardOverlayState.FitMode.FIT);
      runtimeWidthField.setText(Integer.toString(dims.width()));
      runtimeHeightField.setText(Integer.toString(dims.height()));
      storyboardWidthField.setText(Integer.toString(dims.width()));
      storyboardHeightField.setText(Integer.toString(dims.height()));
      scaleSlider.setValue(100.0);
      scaleValueLabel.setText("100%");
      offsetXField.setText("0");
      offsetYField.setText("0");
      clearCropFields();
      cropEnabledCheck.setSelected(false);
      Path stateFile = stateFile();
      if (stateFile != null && Files.isRegularFile(stateFile)) {
        try (InputStream in = Files.newInputStream(stateFile)) {
          persisted.load(in);
        }
      }
      folderField.setText(persisted.getProperty(KEY_FOLDER, ""));
      filterField.setText(persisted.getProperty(KEY_FILTER, ""));
      enabledCheck.setSelected(Boolean.parseBoolean(persisted.getProperty(KEY_ENABLED, "false")));
      followActiveCheck.setSelected(Boolean.parseBoolean(persisted.getProperty(KEY_FOLLOW_ACTIVE, "true")));
      hideUiCheck.setSelected(Boolean.parseBoolean(persisted.getProperty(KEY_HIDE_UI, "false")));
      opacitySlider.setValue(parseOpacity(persisted.getProperty(KEY_OPACITY), 35.0));
      opacityValueLabel.setText(Integer.toString((int) Math.round(opacitySlider.getValue())) + "%");
      fitModeCombo.getSelectionModel().select(StoryboardOverlayState.FitMode.parse(persisted.getProperty(KEY_FIT_MODE)));
      runtimeSizeExplicit = Boolean.parseBoolean(persisted.getProperty(KEY_RUNTIME_SIZE_EXPLICIT, "false"));
      storyboardSizeExplicit = Boolean.parseBoolean(persisted.getProperty(KEY_STORYBOARD_SIZE_EXPLICIT, "false"));
      runtimeWidthField.setText(formatNumber(runtimeSizeExplicit
          ? parseNumber(persisted.getProperty(KEY_RUNTIME_WIDTH), dims.width())
          : dims.width()));
      runtimeHeightField.setText(formatNumber(runtimeSizeExplicit
          ? parseNumber(persisted.getProperty(KEY_RUNTIME_HEIGHT), dims.height())
          : dims.height()));
      storyboardWidthField.setText(formatNumber(storyboardSizeExplicit
          ? parseNumber(persisted.getProperty(KEY_STORYBOARD_WIDTH), dims.width())
          : dims.width()));
      storyboardHeightField.setText(formatNumber(storyboardSizeExplicit
          ? parseNumber(persisted.getProperty(KEY_STORYBOARD_HEIGHT), dims.height())
          : dims.height()));
      scaleSlider.setValue(parseScalePercent(persisted.getProperty(KEY_SCALE), 100.0));
      scaleValueLabel.setText(Integer.toString((int) Math.round(scaleSlider.getValue())) + "%");
      offsetXField.setText(formatNumber(parseNumber(persisted.getProperty(KEY_OFFSET_X), 0.0)));
      offsetYField.setText(formatNumber(parseNumber(persisted.getProperty(KEY_OFFSET_Y), 0.0)));
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    } finally {
      applyingState = false;
    }
  }

  private void saveState() {
    if (applyingState || projectRoot == null || !projectRoot.isDirectory()) return;
    Path stateFile = stateFile();
    if (stateFile == null) return;
    try {
      Files.createDirectories(stateFile.getParent());
      Properties props = new Properties();
      copyPersistedCropProperties(props);
      props.setProperty(KEY_FOLDER, pathTextOrEmpty(folderField.getText()));
      props.setProperty(KEY_FILTER, textOrEmpty(filterField.getText()));
      props.setProperty(KEY_ENABLED, Boolean.toString(enabledCheck.isSelected()));
      props.setProperty(KEY_FOLLOW_ACTIVE, Boolean.toString(followActiveCheck.isSelected()));
      props.setProperty(KEY_HIDE_UI, Boolean.toString(hideUiCheck.isSelected()));
      props.setProperty(KEY_OPACITY, Double.toString(opacitySlider.getValue()));
      props.setProperty(KEY_FIT_MODE, fitModeCombo.getValue() == null ? StoryboardOverlayState.FitMode.FIT.name() : fitModeCombo.getValue().name());
      props.setProperty(KEY_RUNTIME_SIZE_EXPLICIT, Boolean.toString(runtimeSizeExplicit));
      props.setProperty(KEY_STORYBOARD_SIZE_EXPLICIT, Boolean.toString(storyboardSizeExplicit));
      if (runtimeSizeExplicit) {
        props.setProperty(KEY_RUNTIME_WIDTH, formatNumber(parseNumber(runtimeWidthField.getText(), ProjectViewportSpec.resolve(projectRoot).width())));
        props.setProperty(KEY_RUNTIME_HEIGHT, formatNumber(parseNumber(runtimeHeightField.getText(), ProjectViewportSpec.resolve(projectRoot).height())));
      }
      if (storyboardSizeExplicit) {
        props.setProperty(KEY_STORYBOARD_WIDTH, formatNumber(parseNumber(storyboardWidthField.getText(), ProjectViewportSpec.resolve(projectRoot).width())));
        props.setProperty(KEY_STORYBOARD_HEIGHT, formatNumber(parseNumber(storyboardHeightField.getText(), ProjectViewportSpec.resolve(projectRoot).height())));
      }
      props.setProperty(KEY_SCALE, Double.toString(scaleSlider.getValue()));
      props.setProperty(KEY_OFFSET_X, formatNumber(parseNumber(offsetXField.getText(), 0.0)));
      props.setProperty(KEY_OFFSET_Y, formatNumber(parseNumber(offsetYField.getText(), 0.0)));
      StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
      if (selected != null) {
        if (hasValidCrop(loadImage(selected.path()))) {
          writeCropProperties(props, selected);
        } else {
          removeCropProperties(props, selected);
        }
      }
      props.setProperty(KEY_SELECTED, selected == null ? "" : encodePath(selected.path()));
      try (OutputStream out = Files.newOutputStream(stateFile)) {
        props.store(out, "JVN Storyboard Overlay");
      }
      persisted.clear();
      persisted.putAll(props);
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
  }

  private Path stateFile() {
    if (projectRoot == null || !projectRoot.isDirectory()) return null;
    return projectRoot.toPath().resolve(STATE_FILE);
  }

  private String encodePath(Path path) {
    return StoryboardOverlayCatalog.displayPath(projectRoot == null ? null : projectRoot.toPath(), path);
  }

  private static String textOrEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private static String pathTextOrEmpty(String value) {
    return value == null || value.isBlank() ? "" : value;
  }

  private static String cleanPathValue(String raw) {
    if (raw == null || raw.isBlank()) return "";
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

  private static double parseOpacity(String raw, double fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      double value = Double.parseDouble(raw.trim());
      if (!Double.isFinite(value)) return fallback;
      return Math.max(5.0, Math.min(100.0, value));
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return fallback;
    }
  }

  private static double parseScalePercent(String raw, double fallback) {
    double value = parseNumber(raw, fallback);
    if (!Double.isFinite(value)) return fallback;
    return Math.max(25.0, Math.min(300.0, value));
  }

  private static double parseNumber(String raw, double fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      double value = Double.parseDouble(raw.trim());
      return Double.isFinite(value) ? value : fallback;
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return fallback;
    }
  }

  private static String formatNumber(double value) {
    if (!Double.isFinite(value)) return "0";
    double rounded = Math.rint(value);
    if (Math.abs(value - rounded) < 0.0001) {
      return Long.toString(Math.round(rounded));
    }
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private void updateFrameSummary() {
    int total = allFrames.size();
    int shown = framesList.getItems().size();
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    if (total <= 0) {
      framesSummaryLabel.setText("No frames loaded.");
      return;
    }
    StringBuilder text = new StringBuilder();
    text.append(total).append(" frame").append(total == 1 ? "" : "s");
    if (shown != total) {
      text.append("  •  ").append(shown).append(" shown");
    }
    if (selected != null) {
      int index = Math.max(0, framesList.getItems().indexOf(selected)) + 1;
      text.append("  •  selected ").append(index).append("/").append(shown);
    }
    framesSummaryLabel.setText(text.toString());
  }

  private void updateMatchLabel() {
    if (activeScriptFile == null) {
      matchLabel.setText("No active JES or VNS scene.");
      return;
    }
    if (activeMatchFrame == null) {
      matchLabel.setText("No probable storyboard match for " + activeScriptFile.getName() + ".");
      return;
    }
    matchLabel.setText("Best match for " + activeScriptFile.getName() + ": " + activeMatchFrame.displayPath());
  }

  private void applyActiveContextMatch(boolean autoSelect) {
    activeMatchFrame = findBestMatch(framesList.getItems());
    updateMatchLabel();
    if (autoSelect && activeMatchFrame != null) {
      framesList.getSelectionModel().select(activeMatchFrame);
      framesList.scrollTo(activeMatchFrame);
    }
    updateControlAvailability();
  }

  private StoryboardFrame findBestMatch(List<StoryboardFrame> candidates) {
    if (activeScriptFile == null || candidates == null || candidates.isEmpty()) return null;
    String scriptStem = stripExtension(activeScriptFile.getName()).toLowerCase(Locale.ROOT);
    if (scriptStem.isBlank()) return null;
    List<String> strongTokens = significantTokens(activeScriptFile.toPath());
    return candidates.stream()
        .map(frame -> new MatchCandidate(frame, scoreFrameMatch(frame, scriptStem, strongTokens)))
        .filter(candidate -> candidate.score > 0)
        .max(Comparator.comparingInt(MatchCandidate::score)
            .thenComparing(candidate -> candidate.frame.displayPath(), String.CASE_INSENSITIVE_ORDER))
        .map(MatchCandidate::frame)
        .orElse(null);
  }

  private static int scoreFrameMatch(StoryboardFrame frame, String scriptStem, List<String> tokens) {
    if (frame == null) return Integer.MIN_VALUE;
    String path = frame.displayPath().toLowerCase(Locale.ROOT);
    String fileStem = stripExtension(frame.fileName()).toLowerCase(Locale.ROOT);
    int score = 0;
    if (fileStem.equals(scriptStem)) score += 1000;
    if (path.contains("/" + scriptStem) || path.contains(scriptStem + "/")) score += 420;
    if (path.contains(scriptStem)) score += 240;
    for (String token : tokens) {
      if (token.length() < 2) continue;
      if (fileStem.equals(token)) score += 220;
      else if (fileStem.contains(token)) score += 120;
      if (path.contains("/" + token + "/")) score += 75;
      else if (path.contains(token)) score += 35;
      if (token.chars().allMatch(Character::isDigit)) score += path.contains(token) ? 40 : 0;
    }
    return score;
  }

  private static List<String> significantTokens(Path path) {
    LinkedHashSet<String> tokens = new LinkedHashSet<>();
    if (path == null) return List.of();
    for (Path part : path) {
      String value = part == null ? "" : part.toString();
      for (String token : value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
        if (token.isBlank()) continue;
        if (token.length() == 1 && !Character.isDigit(token.charAt(0))) continue;
        if (isIgnoredMatchToken(token)) continue;
        tokens.add(token);
      }
    }
    return new ArrayList<>(tokens);
  }

  private static boolean isIgnoredMatchToken(String token) {
    return switch (token) {
      case "scripts", "script", "src", "main", "story", "stories", "jes", "vns", "game", "project", "assets" -> true;
      default -> false;
    };
  }

  private static String stripExtension(String value) {
    if (value == null) return "";
    int dot = value.lastIndexOf('.');
    return dot <= 0 ? value : value.substring(0, dot);
  }

  private record StoryboardFrame(Path path, String displayPath) {
    String fileName() {
      Path fileName = path == null ? null : path.getFileName();
      return fileName == null ? displayPath : fileName.toString();
    }

    String directoryPath() {
      String fileName = fileName();
      if (displayPath == null || displayPath.equals(fileName)) return "";
      int end = displayPath.length() - fileName.length();
      if (end <= 0) return displayPath;
      String prefix = displayPath.substring(0, end);
      return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }
  }

  private record MatchCandidate(StoryboardFrame frame, int score) {
  }

  private record ImagePoint(double x, double y) {
  }

  private record PreviewImageBounds(double x, double y, double width, double height) {
  }

  private static final class StoryboardFrameCell extends ListCell<StoryboardFrame> {
    private final Label nameLabel = new Label();
    private final Label pathLabel = new Label();
    private final VBox content = new VBox(2, nameLabel, pathLabel);

    private StoryboardFrameCell() {
      nameLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700;");
      pathLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #8ea0b7;");
      pathLabel.setWrapText(true);
    }

    @Override
    protected void updateItem(StoryboardFrame item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        setGraphic(null);
        return;
      }
      nameLabel.setText(item.fileName());
      pathLabel.setText(item.directoryPath());
      setText(null);
      setGraphic(content);
    }
  }
}
