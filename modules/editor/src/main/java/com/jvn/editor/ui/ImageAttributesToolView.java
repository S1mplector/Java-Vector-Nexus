package com.jvn.editor.ui;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/**
 * Dedicated Image Attributes Tool (independent from Layered Image Visualizer).
 *
 * Workflow focus:
 * - select an image tag
 * - pick conflict-safe attributes by group
 * - swap marked groups quickly
 * - type short expressions / shortforms
 * - export tag+attributes strings for script authoring
 */
public class ImageAttributesToolView extends BorderPane implements ImageToolPanel {
  private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)");
  private static final String STATE_FILE = ".jvn/image-attributes-tool.properties";
  private static final String TOOL_TITLE = "Image Attributes Tool";
  private static final String NONE_LABEL = "(none)";

  private static final String EXPORT_SHOW_TAG_ATTRS = "show + tag + attrs";
  private static final String EXPORT_TAG_ATTRS = "tag + attrs";
  private static final String EXPORT_ATTRS_ONLY = "attrs only";
  private static final String EXPORT_JVN_CHARIMG = "JVN @charimg + [show]";

  private static final Map<String, String> GROUP_TOKEN_ALIASES = Map.ofEntries(
      Map.entry("eye", "eyes"),
      Map.entry("eyes", "eyes"),
      Map.entry("mouth", "mouth"),
      Map.entry("lip", "mouth"),
      Map.entry("lips", "mouth"),
      Map.entry("brow", "brow"),
      Map.entry("eyebrow", "brow"),
      Map.entry("eyebrows", "brow"),
      Map.entry("base", "base"),
      Map.entry("body", "body"),
      Map.entry("hair", "hair"),
      Map.entry("face", "face"),
      Map.entry("outfit", "outfit"),
      Map.entry("clothes", "outfit"),
      Map.entry("accessory", "accessory"),
      Map.entry("accessories", "accessory"),
      Map.entry("acc", "accessory"));

  private static final String DEFAULT_SHORTFORMS = String.join("\n",
      "# name = attributes",
      "# happy = eyes_neutral mouth_smile",
      "# upset = eyes_angry mouth_open");

  private final Label summaryLabel = new Label("Open a project to scan image tags.");
  private final Label statusLabel = new Label("");
  private final Label previewInfoLabel = new Label("No active attributes.");
  private final Label interactionHintLabel = new Label("Drag preview to pan, scroll to zoom, double-click to reset.");

  private final TextField tagFilterField = new TextField();
  private final ComboBox<String> tagBox = new ComboBox<>();
  private final TextField exportTagField = new TextField();

  private final ComboBox<String> profileBox = new ComboBox<>();
  private final TextField profileNameField = new TextField();

  private final TextField attributeFilterField = new TextField();
  private final TextField expressionField = new TextField();
  private final ComboBox<String> exportFormatBox = new ComboBox<>();

  private final TextField typedAttributesField = new TextField();
  private final CheckBox typedRealtime = new CheckBox("Realtime preview");
  private final TextArea shortformsArea = new TextArea(DEFAULT_SHORTFORMS);
  private final ComboBox<String> shortformBox = new ComboBox<>();

  private final CheckBox randomizeMarkedOnly = new CheckBox("Randomize marked only");

  private final Canvas previewCanvas = new Canvas(320, 250);
  private final VBox groupBox = new VBox(8);

  private final Map<String, ImageTag> tags = new LinkedHashMap<>();
  private final Map<String, ComboBox<AttributeOption>> selectors = new LinkedHashMap<>();
  private final Map<String, CheckBox> swapChecks = new LinkedHashMap<>();
  private final Map<String, HBox> groupRows = new LinkedHashMap<>();
  private final List<String> groupOrder = new ArrayList<>();
  private final Map<String, String> shortforms = new LinkedHashMap<>();
  private final Map<String, String> profileNameToKey = new LinkedHashMap<>();
  private final Map<String, Image> imageCache = new HashMap<>();
  private final Properties persisted = new Properties();
  private final Random random = new Random();

  private File projectRoot;
  private String currentTagId;
  private boolean applyingState;
  private boolean disposed;

  private Runnable fullscreenToggleHandler;
  private boolean fullscreenActive;
  private Button fullscreenButton;

  private boolean dragging;
  private double dragLastX;
  private double dragLastY;
  private double zoom = 1.0;
  private double offsetX;
  private double offsetY;
  private double previewVirtualWidth;
  private double previewVirtualHeight;

  public ImageAttributesToolView() {
    getStyleClass().add("sidebar-tool-root");
    buildUi();
    refreshCatalog();
  }

  private void buildUi() {
    Label title = new Label(TOOL_TITLE);
    title.getStyleClass().add("sidebar-tool-title");
    HBox titleRow = new HBox(6, title, SidebarToolHelp.button(this, TOOL_TITLE, """
        The Image Attributes Tool lets you visually preview and export image \
attribute declarations for use in VNS scripts.

Workflow:
  1. Select an image tag from the Tag dropdown (or type one in the filter).
  2. The preview canvas shows that image at its natural size.
  3. Drag the preview to pan, scroll to zoom, double-click to reset.
  4. Switch to the attributes view (bottom section) to inspect and copy \
the attribute string for use in @imgattr or a script tag.

Profiles save the current tag + attribute combination so you can quickly \
restore a previously tuned setup without retyping. Profiles are stored \
per-project.

The Script field shows the exact attribute string to paste into a \
dialogue line or @imgattr command. Clicking Copy puts it on the clipboard."""));
    titleRow.setAlignment(Pos.CENTER_LEFT);
    summaryLabel.getStyleClass().add("sidebar-tool-summary");
    summaryLabel.setWrapText(true);
    interactionHintLabel.getStyleClass().add("sidebar-tool-subtitle");
    interactionHintLabel.setWrapText(true);

    tagFilterField.setPromptText("Filter tags...");
    tagFilterField.textProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      refreshTagOptions();
      persistGlobalState();
    });

    tagBox.setPromptText("Image tag");
    tagBox.setConverter(new StringConverter<>() {
      @Override public String toString(String object) { return object == null ? "" : object; }
      @Override public String fromString(String string) { return string; }
    });
    tagBox.setOnAction(e -> {
      if (applyingState) return;
      onTagSelectionChanged();
    });

    exportTagField.setPromptText("Script tag (ex: lavender)");
    exportTagField.textProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      persistGlobalState();
    });

    profileBox.setPromptText("Profile");
    profileNameField.setPromptText("Profile name");

    attributeFilterField.setPromptText("Filter attributes/groups...");
    attributeFilterField.textProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      refreshGroupRows();
      persistGlobalState();
    });

    expressionField.setEditable(false);
    expressionField.setPromptText("Expression id");

    exportFormatBox.getItems().setAll(
        EXPORT_SHOW_TAG_ATTRS,
        EXPORT_TAG_ATTRS,
        EXPORT_ATTRS_ONLY,
        EXPORT_JVN_CHARIMG);
    exportFormatBox.getSelectionModel().select(EXPORT_SHOW_TAG_ATTRS);
    exportFormatBox.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) persistGlobalState();
    });

    typedAttributesField.setPromptText("eyes=angry mouth=smile or eyes_angry mouth_smile");
    typedAttributesField.setOnAction(e -> applyTypedAttributes(true));
    typedAttributesField.textProperty().addListener((o, ov, nv) -> {
      if (applyingState || !typedRealtime.isSelected()) return;
      applyTypedAttributes(false);
    });
    typedRealtime.setSelected(true);
    typedRealtime.selectedProperty().addListener((o, ov, nv) -> {
      if (!applyingState) persistGlobalState();
    });

    shortformsArea.setWrapText(false);
    shortformsArea.setPrefRowCount(8);
    shortformsArea.textProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      refreshShortforms();
      persistGlobalState();
    });

    shortformBox.setPromptText("Shortform");

    Button refreshButton = iconButton(SidebarToolIcon.refresh("#7ec8e3"), "Rescan image tags", this::refreshCatalog);

    HBox tagFilterRow = new HBox(8, new Label("Filter"), tagFilterField, refreshButton);
    tagFilterRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(tagFilterField, Priority.ALWAYS);

    HBox tagRow = new HBox(8, new Label("Tag"), tagBox);
    tagRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(tagBox, Priority.ALWAYS);

    HBox exportTagRow = new HBox(8, new Label("Script"), exportTagField);
    exportTagRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportTagField, Priority.ALWAYS);

    Button loadProfileButton = iconButton(SidebarToolIcon.download("#8ab4f8"), "Load selected profile", this::loadSelectedProfile);
    Button deleteProfileButton = iconButton(SidebarToolIcon.clearX("#f38ba8"), "Delete selected profile", this::deleteSelectedProfile);
    HBox profileLoadRow = new HBox(6, new Label("Profile"), profileBox, loadProfileButton, deleteProfileButton);
    profileLoadRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(profileBox, Priority.ALWAYS);

    Button saveProfileButton = iconButton(SidebarToolIcon.save("#9ed67a"), "Save profile", this::saveProfile);
    HBox profileSaveRow = new HBox(6, new Label("Name"), profileNameField, saveProfileButton);
    profileSaveRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(profileNameField, Priority.ALWAYS);

    VBox top = new VBox(6,
        titleRow,
        summaryLabel,
        tagFilterRow,
        tagRow,
        exportTagRow,
        profileLoadRow,
        profileSaveRow);
    top.getStyleClass().add("sidebar-tool-header");
    setTop(top);

    StackPane previewPane = new StackPane(previewCanvas);
    previewPane.setMinHeight(180);
    previewPane.setPrefHeight(250);
    previewPane.setStyle("-fx-background-color: #161616; -fx-border-color: #333333; -fx-border-radius: 6; -fx-background-radius: 6;");
    previewPane.widthProperty().addListener((o, ov, nv) -> {
      previewCanvas.setWidth(Math.max(140, nv.doubleValue() - 4));
      redrawPreview();
    });
    previewPane.heightProperty().addListener((o, ov, nv) -> {
      previewCanvas.setHeight(Math.max(140, nv.doubleValue() - 4));
      redrawPreview();
    });
    installPreviewInteractions();

    Button resetViewButton = iconButton(SidebarToolIcon.expand("#8ab4f8"), "Reset pan + zoom", this::resetView);
    fullscreenButton = iconButton(SidebarToolIcon.expand("#f5c46b"), "Fullscreen this panel in the current editor window", this::requestFullscreenToggle);
    updateFullscreenButtonUi();

    Button swapPrevButton = iconButton(SidebarToolIcon.undo("#d7b2ff"), "Swap marked groups to previous option", () -> cycleMarkedGroups(-1));
    Button swapNextButton = iconButton(SidebarToolIcon.redo("#8ab4f8"), "Swap marked groups to next option", () -> cycleMarkedGroups(1));
    Button randomizeButton = iconButton(SidebarToolIcon.sort("#f5b971"), "Randomize attributes", this::randomizeSelection);
    Button clearButton = iconButton(SidebarToolIcon.clearX("#f38ba8"), "Clear all attributes", this::clearSelection);
    Button defaultButton = iconButton(SidebarToolIcon.home("#9ed67a"), "Restore defaults", this::restoreDefaults);

    Button copyExportButton = iconButton(SidebarToolIcon.copy("#9ad19c"), "Copy export string", this::copyExport);

    HBox exportRow = new HBox(8,
        resetViewButton,
        fullscreenButton,
        swapPrevButton,
        swapNextButton,
        randomizeButton,
        clearButton,
        defaultButton,
        randomizeMarkedOnly,
        new Label("Export"),
        exportFormatBox,
        copyExportButton);
    exportRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportFormatBox, Priority.ALWAYS);

    HBox expressionRow = new HBox(8, new Label("Expr"), expressionField);
    expressionRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(expressionField, Priority.ALWAYS);

    Button markAllButton = iconButton(SidebarToolIcon.check("#9ed67a"), "Mark all groups for swapping", () -> setAllSwapGroups(true));
    Button unmarkAllButton = iconButton(SidebarToolIcon.clearX("#f5b971"), "Unmark all swap groups", () -> setAllSwapGroups(false));
    HBox attributesTools = new HBox(8, markAllButton, unmarkAllButton);
    attributesTools.setAlignment(Pos.CENTER_LEFT);

    ScrollPane groupsScroll = new ScrollPane(groupBox);
    groupsScroll.setFitToWidth(true);
    groupsScroll.setPrefHeight(340);

    VBox attributesRoot = new VBox(8,
        new HBox(8, new Label("Filter"), attributeFilterField),
        attributesTools,
        groupsScroll);
    HBox.setHgrow(attributeFilterField, Priority.ALWAYS);
    attributesRoot.setPadding(new Insets(8));

    Button applyTypedButton = iconButton(SidebarToolIcon.check("#9ed67a"), "Apply typed attributes", () -> applyTypedAttributes(true));
    HBox typedHeader = new HBox(8, typedRealtime, applyTypedButton);
    typedHeader.setAlignment(Pos.CENTER_LEFT);
    VBox typedRoot = new VBox(8, new Label("Type attributes to preview"), typedAttributesField, typedHeader);
    typedRoot.setPadding(new Insets(8));

    Button applyShortformButton = iconButton(SidebarToolIcon.check("#9ed67a"), "Apply selected shortform", this::applySelectedShortform);
    Button copyShortformButton = iconButton(SidebarToolIcon.copy("#d6b4ff"), "Copy selected shortform expression", this::copySelectedShortform);
    HBox shortformRow = new HBox(8, shortformBox, applyShortformButton, copyShortformButton);
    shortformRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(shortformBox, Priority.ALWAYS);
    VBox shortformsRoot = new VBox(8,
        new Label("Format: name = attributes"),
        shortformsArea,
        shortformRow);
    shortformsRoot.setPadding(new Insets(8));

    TabPane tabs = new TabPane();
    Tab attributesTab = new Tab("Attributes", attributesRoot);
    Tab typedTab = new Tab("Typed", typedRoot);
    Tab shortformsTab = new Tab("Shortforms", shortformsRoot);
    attributesTab.setClosable(false);
    typedTab.setClosable(false);
    shortformsTab.setClosable(false);
    tabs.getTabs().addAll(attributesTab, typedTab, shortformsTab);
    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

    Button exportPngButton = iconButton(SidebarToolIcon.download("#8ab4f8"), "Export composited image as PNG file", this::exportCompositePng);
    Button exportSetupBtn = iconButton(SidebarToolIcon.save("#9ed67a"), "Export setup to .attrsetup file", this::exportSetupToFile);
    Button importSetupBtn = iconButton(SidebarToolIcon.folder("#f5c46b"), "Import setup from .attrsetup file", this::importSetupFromFile);
    HBox fileRow = new HBox(8, exportPngButton, exportSetupBtn, importSetupBtn);
    fileRow.setAlignment(Pos.CENTER_LEFT);

    VBox exportRoot = new VBox(8, expressionRow, exportRow, fileRow);
    exportRoot.setPadding(new Insets(4, 0, 0, 0));
    TitledPane exportPane = new TitledPane("Export controls", exportRoot);
    exportPane.setExpanded(false);
    exportPane.setAnimated(false);
    exportPane.setCollapsible(true);

    VBox center = new VBox(8, previewPane, previewInfoLabel, interactionHintLabel, exportPane, tabs, statusLabel);
    center.setPadding(new Insets(0, 10, 8, 10));
    setCenter(center);

    refreshShortforms();
  }

  @Override
  public void setProjectRoot(File projectRoot) {
    if (disposed) return;
    persistCurrentTagState();
    persistGlobalState();
    this.projectRoot = projectRoot;
    refreshCatalog();
  }

  public void dispose() {
    if (disposed) return;
    disposed = true;
    tags.clear();
    selectors.clear();
    swapChecks.clear();
    groupRows.clear();
    groupOrder.clear();
    imageCache.clear();
    profileNameToKey.clear();
    groupBox.getChildren().clear();
    tagBox.getItems().clear();
    profileBox.getItems().clear();
    currentTagId = null;
    projectRoot = null;
    summaryLabel.setText("Image attributes tool disposed.");
    redrawPreview();
  }

  @Override
  public void refreshCatalog() {
    if (disposed) return;
    persistCurrentTagState();
    persistGlobalState();

    loadPersistentState();

    tags.clear();
    selectors.clear();
    swapChecks.clear();
    groupRows.clear();
    groupOrder.clear();
    groupBox.getChildren().clear();
    imageCache.clear();
    profileNameToKey.clear();
    profileBox.getItems().clear();

    applyingState = true;
    try {
      tagFilterField.setText(persisted.getProperty("global.tagFilter", ""));
      exportTagField.setText(persisted.getProperty("global.exportTag", ""));
      attributeFilterField.setText(persisted.getProperty("global.attributeFilter", ""));
      typedAttributesField.setText(persisted.getProperty("global.typedAttributes", ""));
      typedRealtime.setSelected(parseBoolean(persisted.getProperty("global.typedRealtime"), true));
      shortformsArea.setText(persisted.getProperty("global.shortforms", DEFAULT_SHORTFORMS));
      randomizeMarkedOnly.setSelected(parseBoolean(persisted.getProperty("global.randomizeMarkedOnly"), true));
      String exportFormat = persisted.getProperty("global.exportFormat", EXPORT_SHOW_TAG_ATTRS);
      if (!exportFormatBox.getItems().contains(exportFormat)) exportFormat = EXPORT_SHOW_TAG_ATTRS;
      exportFormatBox.getSelectionModel().select(exportFormat);
      zoom = clamp(parseDouble(persisted.getProperty("global.zoom"), 1.0), 0.1, 8.0);
      offsetX = parseDouble(persisted.getProperty("global.offsetX"), 0.0);
      offsetY = parseDouble(persisted.getProperty("global.offsetY"), 0.0);
    } finally {
      applyingState = false;
    }
    refreshShortforms();

    if (projectRoot == null || !projectRoot.isDirectory()) {
      currentTagId = null;
      summaryLabel.setText("Open a project to scan image tags.");
      tagBox.getItems().clear();
      redrawPreview();
      return;
    }

    Path root = projectRoot.toPath();
    int imageCount = 0;
    try (Stream<Path> stream = Files.walk(root, 10)) {
      List<Path> files = stream
          .filter(Files::isRegularFile)
          .filter(this::isImageFile)
          .filter(path -> !isIgnoredPath(root.relativize(path).toString().replace('\\', '/')))
          .sorted()
          .toList();
      for (Path path : files) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        AttributeOption option = parseOption(relative, path.toFile());
        if (option == null) continue;
        String tagId = deriveTagIdFromRelative(relative);
        tags.computeIfAbsent(tagId, ImageTag::new).add(option);
        imageCount++;
      }
    } catch (IOException ex) {
      currentTagId = null;
      summaryLabel.setText("Failed to scan project assets: " + ex.getMessage());
      tagBox.getItems().clear();
      redrawPreview();
      return;
    }

    summaryLabel.setText("Tags: " + tags.size() + "  |  Images: " + imageCount);
    refreshTagOptions();
  }

  private void refreshTagOptions() {
    String filter = sanitizeId(tagFilterField.getText());
    String previous = normalize(tagBox.getValue());

    List<String> visible = new ArrayList<>();
    for (String id : tags.keySet()) {
      if (filter.isBlank() || sanitizeId(id).contains(filter)) {
        visible.add(id);
      }
    }
    summaryLabel.setText("Tags: " + tags.size() + "  |  Visible: " + visible.size());

    tagBox.getItems().setAll(visible);
    String preferred = normalize(persisted.getProperty("global.selectedTag", ""));
    String target = chooseTagSelection(previous, preferred, visible);

    if (target == null) {
      currentTagId = null;
      selectors.clear();
      swapChecks.clear();
      groupRows.clear();
      groupOrder.clear();
      groupBox.getChildren().clear();
      profileNameToKey.clear();
      profileBox.getItems().clear();
      redrawPreview();
      return;
    }

    applyingState = true;
    try {
      tagBox.getSelectionModel().select(target);
    } finally {
      applyingState = false;
    }
    onTagSelectionChanged();
  }

  private void onTagSelectionChanged() {
    String selectedTag = normalize(tagBox.getValue());
    if (selectedTag.isBlank()) return;

    if (currentTagId != null && !currentTagId.equals(selectedTag)) {
      persistCurrentTagState();
    }
    currentTagId = selectedTag;

    selectors.clear();
    swapChecks.clear();
    groupRows.clear();
    groupOrder.clear();
    groupBox.getChildren().clear();

    ImageTag tag = tags.get(selectedTag);
    if (tag == null || tag.groups.isEmpty()) {
      redrawPreview();
      return;
    }

    List<String> groups = new ArrayList<>(tag.groups.keySet());
    groups.sort(Comparator.naturalOrder());
    for (String group : groups) {
      List<AttributeOption> options = new ArrayList<>(tag.groups.get(group));
      options.sort(Comparator
          .comparingInt((AttributeOption o) -> o.sortKey)
          .thenComparing(o -> o.label.toLowerCase(Locale.ROOT)));

      ComboBox<AttributeOption> combo = new ComboBox<>();
      combo.setConverter(new StringConverter<>() {
        @Override public String toString(AttributeOption object) {
          return object == null ? "" : object.label;
        }
        @Override public AttributeOption fromString(String string) { return null; }
      });
      combo.getItems().add(AttributeOption.none());
      combo.getItems().addAll(options);
      combo.setMaxWidth(Double.MAX_VALUE);
      selectPreferredOption(combo);

      CheckBox swapCheck = new CheckBox();
      swapCheck.setTooltip(new Tooltip("Mark this group for swap/randomize operations"));
      swapCheck.setSelected(false);
      swapCheck.selectedProperty().addListener((o, ov, nv) -> {
        if (!applyingState) persistCurrentTagState();
      });

      Button prevButton = iconButton(SidebarToolIcon.undo("#d7b2ff"), "Previous option", () -> cycleCombo(combo, -1));
      Button nextButton = iconButton(SidebarToolIcon.redo("#8ab4f8"), "Next option", () -> cycleCombo(combo, 1));
      Button openButton = iconButton(SidebarToolIcon.folder("#7ec8e3"), "Open selected image in OS viewer", () -> openSelectedImage(combo.getValue()));

      combo.valueProperty().addListener((o, ov, nv) -> {
        if (applyingState) {
          redrawPreview();
          return;
        }
        updateExpressionFromSelection();
        redrawPreview();
        persistCurrentTagState();
      });

      Label groupLabel = new Label(group);
      groupLabel.setMinWidth(110);
      groupLabel.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px;");

      HBox row = new HBox(6, swapCheck, groupLabel, combo, prevButton, nextButton, openButton);
      row.setAlignment(Pos.CENTER_LEFT);
      HBox.setHgrow(combo, Priority.ALWAYS);

      selectors.put(group, combo);
      swapChecks.put(group, swapCheck);
      groupRows.put(group, row);
      groupOrder.add(group);
    }

    if (exportTagField.getText() == null || exportTagField.getText().isBlank()) {
      exportTagField.setText(defaultScriptTag(selectedTag));
    }

    applyCurrentTagState();
    refreshProfileOptions();
    refreshGroupRows();
    updateExpressionFromSelection();
    redrawPreview();
    persistGlobalState();
    persistCurrentTagState();
  }

  private void refreshGroupRows() {
    groupBox.getChildren().clear();
    String filter = sanitizeId(attributeFilterField.getText());
    for (String group : groupOrder) {
      if (!matchesAttributeFilter(group, filter)) continue;
      HBox row = groupRows.get(group);
      if (row != null) groupBox.getChildren().add(row);
    }
    if (groupBox.getChildren().isEmpty()) {
      Label empty = new Label("No attributes match the current filter.");
      empty.setStyle("-fx-text-fill: rgba(255,255,255,0.7);");
      groupBox.getChildren().add(empty);
    }
  }

  private boolean matchesAttributeFilter(String group, String filter) {
    if (group == null) return false;
    if (filter == null || filter.isBlank()) return true;
    String groupId = sanitizeId(group);
    if (groupId.contains(filter)) return true;
    ComboBox<AttributeOption> combo = selectors.get(group);
    if (combo == null) return false;
    for (AttributeOption option : combo.getItems()) {
      if (option == null || option.isNone()) continue;
      if (sanitizeId(option.label).contains(filter)) return true;
    }
    return false;
  }

  private void setAllSwapGroups(boolean active) {
    for (CheckBox check : swapChecks.values()) {
      check.setSelected(active);
    }
    persistCurrentTagState();
  }

  private void cycleMarkedGroups(int direction) {
    if (selectors.isEmpty()) return;
    int changed = 0;
    applyingState = true;
    try {
      for (String group : groupOrder) {
        CheckBox mark = swapChecks.get(group);
        if (mark == null || !mark.isSelected()) continue;
        ComboBox<AttributeOption> combo = selectors.get(group);
        if (combo == null || combo.getItems().size() <= 1) continue;
        cycleCombo(combo, direction);
        changed++;
      }
    } finally {
      applyingState = false;
    }
    if (changed > 0) {
      updateExpressionFromSelection();
      redrawPreview();
      persistCurrentTagState();
      status("Swapped " + changed + " groups.");
    } else {
      status("No marked groups to swap.");
    }
  }

  private void randomizeSelection() {
    if (selectors.isEmpty()) return;
    int changed = 0;
    boolean markedOnly = randomizeMarkedOnly.isSelected();
    applyingState = true;
    try {
      for (String group : groupOrder) {
        if (markedOnly) {
          CheckBox mark = swapChecks.get(group);
          if (mark == null || !mark.isSelected()) continue;
        }
        ComboBox<AttributeOption> combo = selectors.get(group);
        if (combo == null || combo.getItems().size() <= 1) continue;
        int next = 1 + random.nextInt(combo.getItems().size() - 1);
        if (combo.getSelectionModel().getSelectedIndex() != next) {
          combo.getSelectionModel().select(next);
          changed++;
        }
      }
    } finally {
      applyingState = false;
    }
    if (changed > 0) {
      updateExpressionFromSelection();
      redrawPreview();
      persistCurrentTagState();
      status("Randomized " + changed + " groups.");
    } else {
      status(markedOnly ? "No marked groups available to randomize." : "No groups available to randomize.");
    }
  }

  private void clearSelection() {
    if (selectors.isEmpty()) return;
    applyingState = true;
    try {
      for (ComboBox<AttributeOption> combo : selectors.values()) {
        combo.getSelectionModel().select(0);
      }
    } finally {
      applyingState = false;
    }
    updateExpressionFromSelection();
    redrawPreview();
    persistCurrentTagState();
    status("Cleared all attributes.");
  }

  private void restoreDefaults() {
    if (selectors.isEmpty()) return;
    applyingState = true;
    try {
      for (ComboBox<AttributeOption> combo : selectors.values()) {
        selectPreferredOption(combo);
      }
    } finally {
      applyingState = false;
    }
    updateExpressionFromSelection();
    redrawPreview();
    persistCurrentTagState();
    status("Restored default attributes.");
  }

  private void cycleCombo(ComboBox<AttributeOption> combo, int direction) {
    if (combo == null || combo.getItems().size() <= 1) return;
    int idx = combo.getSelectionModel().getSelectedIndex();
    if (idx < 1) idx = 1;
    int next = idx + (direction < 0 ? -1 : 1);
    if (next < 1) next = combo.getItems().size() - 1;
    if (next >= combo.getItems().size()) next = 1;
    combo.getSelectionModel().select(next);
  }

  private void applyTypedAttributes(boolean persistStatus) {
    Map<String, String> assignments = parseAttributeAssignments(typedAttributesField.getText());
    if (assignments.isEmpty()) {
      if (persistStatus) status("No valid attributes found in typed input.");
      return;
    }
    int applied = applyAttributeAssignments(assignments);
    if (applied > 0) {
      updateExpressionFromSelection();
      redrawPreview();
      persistCurrentTagState();
      if (persistStatus) status("Applied " + applied + " typed attributes.");
    } else if (persistStatus) {
      status("Typed attributes did not match current groups.");
    }
  }

  private int applyAttributeAssignments(Map<String, String> assignments) {
    if (assignments == null || assignments.isEmpty()) return 0;
    int applied = 0;
    applyingState = true;
    try {
      for (Map.Entry<String, String> entry : assignments.entrySet()) {
        String group = resolveGroupName(entry.getKey());
        if (group == null || group.isBlank()) continue;
        ComboBox<AttributeOption> combo = selectors.get(group);
        if (combo == null) continue;
        AttributeOption target = findOptionByValue(combo, entry.getValue());
        if (target == null) continue;
        combo.getSelectionModel().select(target);
        applied++;
      }
    } finally {
      applyingState = false;
    }
    return applied;
  }

  private String resolveGroupName(String rawGroup) {
    String key = sanitizeId(rawGroup);
    if (key.isBlank()) return null;
    String alias = GROUP_TOKEN_ALIASES.getOrDefault(key, key);
    for (String group : selectors.keySet()) {
      if (sanitizeId(group).equals(alias)) return group;
    }
    for (String group : selectors.keySet()) {
      String normalizedGroup = sanitizeId(group);
      if (normalizedGroup.contains(alias) || alias.contains(normalizedGroup)) return group;
    }
    return null;
  }

  private AttributeOption findOptionByValue(ComboBox<AttributeOption> combo, String rawValue) {
    if (combo == null) return null;
    String value = sanitizeId(rawValue);
    if (value.isBlank()) return null;
    if ("none".equals(value) || "off".equals(value) || "clear".equals(value) || "-".equals(value)) {
      return combo.getItems().isEmpty() ? null : combo.getItems().get(0);
    }
    for (AttributeOption option : combo.getItems()) {
      if (option == null || option.isNone()) continue;
      if (sanitizeId(option.label).equals(value)) return option;
    }
    for (AttributeOption option : combo.getItems()) {
      if (option == null || option.isNone()) continue;
      String label = sanitizeId(option.label);
      if (label.contains(value) || value.contains(label)) return option;
    }
    return null;
  }

  private void refreshShortforms() {
    Map<String, String> parsed = parseAttributeShortforms(shortformsArea.getText());
    shortforms.clear();
    shortforms.putAll(parsed);

    String keep = shortformBox.getValue();
    shortformBox.getItems().setAll(shortforms.keySet());
    if (keep != null && shortforms.containsKey(keep)) {
      shortformBox.getSelectionModel().select(keep);
    } else if (!shortforms.isEmpty()) {
      shortformBox.getSelectionModel().select(0);
    }
  }

  private void applySelectedShortform() {
    String key = shortformBox.getValue();
    if (key == null || key.isBlank()) {
      status("Select a shortform first.");
      return;
    }
    String expression = shortforms.get(key);
    if (expression == null || expression.isBlank()) {
      status("Shortform is empty.");
      return;
    }
    typedAttributesField.setText(expression);
    applyTypedAttributes(true);
    status("Applied shortform: " + key);
  }

  private void copySelectedShortform() {
    String key = shortformBox.getValue();
    if (key == null || key.isBlank()) {
      status("Select a shortform first.");
      return;
    }
    String expression = shortforms.get(key);
    if (expression == null || expression.isBlank()) {
      status("Shortform is empty.");
      return;
    }
    copy(expression.trim());
    status("Copied shortform: " + key);
  }

  private void saveProfile() {
    if (currentTagId == null || currentTagId.isBlank()) {
      status("Select a tag first.");
      return;
    }
    String name = normalize(profileNameField.getText());
    if (name.isBlank()) {
      status("Profile name is required.");
      return;
    }

    String profileKey = encodeKey(name);
    String prefix = profilePrefix(currentTagId, profileKey);
    clearPrefix(prefix);
    persisted.setProperty(prefix + "name", name);
    writeSelectionState(prefix);
    persisted.setProperty("global.selectedProfile", name);
    savePersistentState();

    refreshProfileOptions();
    profileBox.getSelectionModel().select(name);
    status("Saved profile: " + name);
  }

  private void loadSelectedProfile() {
    if (currentTagId == null || currentTagId.isBlank()) {
      status("Select a tag first.");
      return;
    }
    String name = profileBox.getValue();
    if (name == null || name.isBlank()) {
      status("Select a profile first.");
      return;
    }
    String key = profileNameToKey.get(name);
    if (key == null || key.isBlank()) {
      status("Profile not found.");
      return;
    }

    String prefix = profilePrefix(currentTagId, key);
    applyingState = true;
    try {
      applySelectionState(prefix, false);
    } finally {
      applyingState = false;
    }
    updateExpressionFromSelection();
    redrawPreview();
    persisted.setProperty("global.selectedProfile", name);
    persistCurrentTagState();
    persistGlobalState();
    status("Loaded profile: " + name);
  }

  private void deleteSelectedProfile() {
    if (currentTagId == null || currentTagId.isBlank()) {
      status("Select a tag first.");
      return;
    }
    String name = profileBox.getValue();
    if (name == null || name.isBlank()) {
      status("Select a profile first.");
      return;
    }
    String key = profileNameToKey.get(name);
    if (key == null || key.isBlank()) {
      status("Profile not found.");
      return;
    }
    clearPrefix(profilePrefix(currentTagId, key));
    if (name.equals(normalize(persisted.getProperty("global.selectedProfile", "")))) {
      persisted.remove("global.selectedProfile");
    }
    savePersistentState();
    refreshProfileOptions();
    status("Deleted profile: " + name);
  }

  private void refreshProfileOptions() {
    profileNameToKey.clear();
    List<String> names = new ArrayList<>();
    if (currentTagId != null && !currentTagId.isBlank()) {
      String prefix = "profile." + encodeKey(currentTagId) + ".";
      for (String key : persisted.stringPropertyNames()) {
        if (!key.startsWith(prefix) || !key.endsWith(".name")) continue;
        String middle = key.substring(prefix.length(), key.length() - ".name".length());
        String name = normalize(persisted.getProperty(key, ""));
        if (middle.isBlank() || name.isBlank()) continue;
        profileNameToKey.put(name, middle);
        names.add(name);
      }
      names.sort(String.CASE_INSENSITIVE_ORDER);
    }

    profileBox.getItems().setAll(names);
    String selected = normalize(persisted.getProperty("global.selectedProfile", ""));
    if (!selected.isBlank() && names.contains(selected)) {
      profileBox.getSelectionModel().select(selected);
    } else if (!names.isEmpty()) {
      profileBox.getSelectionModel().select(0);
    }
  }

  private void copyExport() {
    String format = exportFormatBox.getValue();
    String payload = buildExportPayload(format);
    copy(payload);
    status("Copied export payload.");
  }

  private String buildExportPayload(String format) {
    String attrs = String.join(" ", buildAttributeTokens());
    String tag = sanitizeId(exportTagField.getText());
    if (tag.isBlank()) tag = defaultScriptTag(currentTagId);
    if (tag.isBlank()) tag = "character";

    if (EXPORT_ATTRS_ONLY.equals(format)) {
      return attrs;
    }
    if (EXPORT_TAG_ATTRS.equals(format)) {
      if (attrs.isBlank()) return tag;
      return tag + " " + attrs;
    }
    if (EXPORT_JVN_CHARIMG.equals(format)) {
      String expression = sanitizeId(expressionField.getText());
      if (expression.isBlank()) expression = "neutral";
      String layerSpec = buildLayerSpec();
      if (layerSpec.isBlank()) layerSpec = "assets/characters/" + tag + "/" + expression + ".png";
      String charimg = "@charimg " + tag + " " + expression + " " + layerSpec;
      String show = "[show " + tag + " center " + expression + "]";
      return charimg + "\n" + show;
    }

    // Default: show + tag + attrs
    StringBuilder out = new StringBuilder();
    out.append("show ").append(tag);
    if (!attrs.isBlank()) out.append(' ').append(attrs);
    return out.toString();
  }

  private String buildLayerSpec() {
    StringBuilder spec = new StringBuilder();
    for (String group : groupOrder) {
      ComboBox<AttributeOption> combo = selectors.get(group);
      AttributeOption option = combo == null ? null : combo.getValue();
      if (option == null || option.isNone()) continue;
      if (spec.length() > 0) spec.append(" | ");
      spec.append(option.relativePath);
    }
    return spec.toString();
  }

  private List<String> buildAttributeTokens() {
    List<String> tokens = new ArrayList<>();
    for (String group : groupOrder) {
      ComboBox<AttributeOption> combo = selectors.get(group);
      AttributeOption option = combo == null ? null : combo.getValue();
      if (option == null || option.isNone()) continue;
      String groupToken = sanitizeId(group);
      String optionToken = sanitizeId(option.label);
      if (groupToken.isBlank() || optionToken.isBlank()) continue;
      tokens.add(groupToken + "_" + optionToken);
    }
    return tokens;
  }

  private void updateExpressionFromSelection() {
    List<String> tokens = buildAttributeTokens();
    String expression;
    if (tokens.isEmpty()) {
      expression = "neutral";
    } else {
      String joined = String.join("_", tokens);
      expression = sanitizeId(joined);
      if (expression.length() > 72) {
        expression = expression.substring(0, 72);
        expression = expression.replaceAll("_+$", "");
      }
      if (expression.isBlank()) expression = "neutral";
    }
    expressionField.setText(expression);
  }

  private void redrawPreview() {
    GraphicsContext g = previewCanvas.getGraphicsContext2D();
    double cw = previewCanvas.getWidth();
    double ch = previewCanvas.getHeight();

    drawCheckerBackground(g, cw, ch);

    List<AttributeOption> selected = selectedOptions();
    if (selected.isEmpty()) {
      previewVirtualWidth = 0;
      previewVirtualHeight = 0;
      drawCenteredText(g, cw, ch, "Select attributes to preview");
      previewInfoLabel.setText("No active attributes.");
      return;
    }

    List<Image> layers = new ArrayList<>();
    double maxW = 0;
    double maxH = 0;
    for (AttributeOption option : selected) {
      Image img = loadImage(option);
      if (img == null || img.isError() || img.getWidth() <= 0 || img.getHeight() <= 0) continue;
      layers.add(img);
      if (img.getWidth() > maxW) maxW = img.getWidth();
      if (img.getHeight() > maxH) maxH = img.getHeight();
    }

    if (layers.isEmpty()) {
      previewVirtualWidth = 0;
      previewVirtualHeight = 0;
      drawCenteredText(g, cw, ch, "Selected attributes could not be loaded");
      previewInfoLabel.setText("Selected assets failed to decode.");
      return;
    }

    previewVirtualWidth = maxW;
    previewVirtualHeight = maxH;

    double drawW = maxW * zoom;
    double drawH = maxH * zoom;
    double drawX = (cw - drawW) * 0.5 + offsetX;
    double drawY = (ch - drawH) * 0.5 + offsetY;

    for (Image img : layers) {
      g.drawImage(img, drawX, drawY, img.getWidth() * zoom, img.getHeight() * zoom);
    }

    g.setStroke(Color.color(1, 1, 1, 0.20));
    g.strokeRect(drawX + 0.5, drawY + 0.5, Math.max(0, drawW - 1), Math.max(0, drawH - 1));

    previewInfoLabel.setText("Tag: " + shortTag(currentTagId)
        + "  |  Attributes: " + selected.size()
        + "  |  Zoom: " + formatDouble(zoom));
  }

  private List<AttributeOption> selectedOptions() {
    List<AttributeOption> out = new ArrayList<>();
    for (String group : groupOrder) {
      ComboBox<AttributeOption> combo = selectors.get(group);
      AttributeOption option = combo == null ? null : combo.getValue();
      if (option == null || option.isNone()) continue;
      out.add(option);
    }
    return out;
  }

  private void installPreviewInteractions() {
    previewCanvas.setOnMouseClicked(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (e.getClickCount() < 2) return;
      resetView();
      e.consume();
    });

    previewCanvas.setOnMousePressed(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      dragging = true;
      dragLastX = e.getX();
      dragLastY = e.getY();
      e.consume();
    });

    previewCanvas.setOnMouseDragged(e -> {
      if (!dragging || !e.isPrimaryButtonDown()) return;
      double dx = e.getX() - dragLastX;
      double dy = e.getY() - dragLastY;
      dragLastX = e.getX();
      dragLastY = e.getY();
      offsetX += dx;
      offsetY += dy;
      redrawPreview();
      e.consume();
    });

    previewCanvas.setOnMouseReleased(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      dragging = false;
      persistGlobalState();
      e.consume();
    });

    previewCanvas.setOnScroll(e -> {
      double oldZoom = zoom;
      double next = clamp(oldZoom * Math.pow(1.10, e.getDeltaY() / 40.0), 0.1, 8.0);
      if (Math.abs(next - oldZoom) < 0.0001) return;

      double cw = previewCanvas.getWidth();
      double ch = previewCanvas.getHeight();
      double vw = Math.max(1, previewVirtualWidth);
      double vh = Math.max(1, previewVirtualHeight);

      double oldW = vw * oldZoom;
      double oldH = vh * oldZoom;
      double oldX = (cw - oldW) * 0.5 + offsetX;
      double oldY = (ch - oldH) * 0.5 + offsetY;

      double localX = (e.getX() - oldX) / Math.max(oldZoom, 0.0001);
      double localY = (e.getY() - oldY) / Math.max(oldZoom, 0.0001);

      zoom = next;

      double newW = vw * zoom;
      double newH = vh * zoom;
      double newX = e.getX() - localX * zoom;
      double newY = e.getY() - localY * zoom;
      offsetX = newX - (cw - newW) * 0.5;
      offsetY = newY - (ch - newH) * 0.5;

      redrawPreview();
      persistGlobalState();
      e.consume();
    });
  }

  private void resetView() {
    zoom = 1.0;
    offsetX = 0.0;
    offsetY = 0.0;
    persistGlobalState();
    redrawPreview();
    status("Reset view.");
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

  private void persistGlobalState() {
    persisted.setProperty("global.tagFilter", normalize(tagFilterField.getText()));
    persisted.setProperty("global.selectedTag", normalize(tagBox.getValue()));
    persisted.setProperty("global.exportTag", normalize(exportTagField.getText()));
    persisted.setProperty("global.attributeFilter", normalize(attributeFilterField.getText()));
    persisted.setProperty("global.exportFormat", normalize(exportFormatBox.getValue()));
    persisted.setProperty("global.typedAttributes", normalize(typedAttributesField.getText()));
    persisted.setProperty("global.typedRealtime", Boolean.toString(typedRealtime.isSelected()));
    persisted.setProperty("global.shortforms", shortformsArea.getText() == null ? "" : shortformsArea.getText());
    persisted.setProperty("global.randomizeMarkedOnly", Boolean.toString(randomizeMarkedOnly.isSelected()));
    persisted.setProperty("global.zoom", formatDouble(zoom));
    persisted.setProperty("global.offsetX", formatDouble(offsetX));
    persisted.setProperty("global.offsetY", formatDouble(offsetY));
    savePersistentState();
  }

  private void persistCurrentTagState() {
    String tag = normalize(currentTagId);
    if (tag.isBlank()) return;
    String prefix = tagStatePrefix(tag);
    clearPrefix(prefix);
    writeSelectionState(prefix);
    savePersistentState();
  }

  private void applyCurrentTagState() {
    String tag = normalize(currentTagId);
    if (tag.isBlank()) return;
    String prefix = tagStatePrefix(tag);

    applyingState = true;
    try {
      applySelectionState(prefix, true);
    } finally {
      applyingState = false;
    }
  }

  private void writeSelectionState(String prefix) {
    if (prefix == null || prefix.isBlank()) return;
    for (String group : groupOrder) {
      ComboBox<AttributeOption> combo = selectors.get(group);
      AttributeOption option = combo == null ? null : combo.getValue();
      String selection = option == null || option.isNone() ? "" : option.label;
      persisted.setProperty(prefix + "sel." + encodeKey(group), selection);

      CheckBox swap = swapChecks.get(group);
      persisted.setProperty(prefix + "swap." + encodeKey(group), Boolean.toString(swap != null && swap.isSelected()));
    }
  }

  private void applySelectionState(String prefix, boolean fallbackToDefault) {
    if (prefix == null || prefix.isBlank()) return;
    for (String group : groupOrder) {
      ComboBox<AttributeOption> combo = selectors.get(group);
      if (combo == null) continue;

      String selected = persisted.getProperty(prefix + "sel." + encodeKey(group), null);
      if (selected != null) {
        if (selected.isBlank()) {
          combo.getSelectionModel().select(0);
        } else {
          AttributeOption option = findOptionByValue(combo, selected);
          if (option != null) {
            combo.getSelectionModel().select(option);
          } else if (fallbackToDefault) {
            selectPreferredOption(combo);
          }
        }
      } else if (fallbackToDefault) {
        selectPreferredOption(combo);
      }

      CheckBox swap = swapChecks.get(group);
      if (swap != null) {
        swap.setSelected(parseBoolean(persisted.getProperty(prefix + "swap." + encodeKey(group)), false));
      }
    }
  }

  private void loadPersistentState() {
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
    Path file = statePath();
    if (file == null) return;
    try {
      Path parent = file.getParent();
      if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
      try (OutputStream out = Files.newOutputStream(file)) {
        persisted.store(out, "JVN Image Attributes Tool State");
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
  }

  private Path statePath() {
    if (projectRoot == null || !projectRoot.isDirectory()) return null;
    return projectRoot.toPath().resolve(STATE_FILE);
  }

  private static String tagStatePrefix(String tag) {
    return "tag." + encodeKey(tag) + ".";
  }

  private static String profilePrefix(String tag, String profileKey) {
    return "profile." + encodeKey(tag) + "." + profileKey + ".";
  }

  private void clearPrefix(String prefix) {
    List<String> keys = new ArrayList<>(persisted.stringPropertyNames());
    for (String key : keys) {
      if (key.startsWith(prefix)) persisted.remove(key);
    }
  }

  private AttributeOption parseOption(String relative, File file) {
    if (relative == null || relative.isBlank() || file == null) return null;
    String normalizedRelative = relative.replace('\\', '/');

    String filename = file.getName();
    int dot = filename.lastIndexOf('.');
    String base = dot > 0 ? filename.substring(0, dot) : filename;
    if (base.isBlank()) return null;

    String tagId = deriveTagIdFromRelative(normalizedRelative);
    String group = inferGroupFromTagSubfolder(normalizedRelative, tagId);
    String label;
    if (!group.isBlank()) {
      label = inferLabelFromFilenameForGroup(base, group);
    } else {
      InferredGroupLabel inferred = inferGroupAndLabelFromFilename(base);
      group = inferred.group();
      label = inferred.label();
    }

    if (group.isBlank()) {
      group = sanitizeId(takeLastPathToken(parentPath(normalizedRelative)));
    }
    if (group.isBlank()) group = "layer";
    if (label.isBlank()) label = "default";

    int sortKey = Integer.MAX_VALUE / 2;
    Matcher m = LEADING_NUMBER.matcher(base);
    if (m.find()) {
      try {
        sortKey = Integer.parseInt(m.group(1));
      } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
      }
    }

    return new AttributeOption(label, group, normalizedRelative, file, sortKey);
  }

  private Image loadImage(AttributeOption option) {
    if (option == null || option.isNone()) return null;
    String key = option.relativePath;
    Image cached = imageCache.get(key);
    if (cached != null) return cached;
    try (InputStream in = Files.newInputStream(option.file.toPath())) {
      Image image = new Image(in);
      if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) return null;
      imageCache.put(key, image);
      return image;
    } catch (Exception ex) {
      return null;
    }
  }

  private void openSelectedImage(AttributeOption option) {
    if (option == null || option.isNone() || option.file == null || !option.file.isFile()) {
      status("No image selected.");
      return;
    }
    try {
      if (!Desktop.isDesktopSupported()) {
        status("Desktop open is not supported in this environment.");
        return;
      }
      Desktop.getDesktop().open(option.file);
      status("Opened: " + option.file.getName());
    } catch (IOException ex) {
      status("Failed to open image: " + ex.getMessage());
    }
  }

  private void selectPreferredOption(ComboBox<AttributeOption> combo) {
    if (combo == null || combo.getItems().isEmpty()) return;
    int bestIndex = 0;
    int bestScore = Integer.MAX_VALUE;
    for (int i = 0; i < combo.getItems().size(); i++) {
      AttributeOption option = combo.getItems().get(i);
      if (option == null || option.isNone()) continue;
      int score = defaultOptionScore(option.label);
      if (score < bestScore) {
        bestScore = score;
        bestIndex = i;
      }
    }
    combo.getSelectionModel().select(bestIndex);
  }

  static int defaultOptionScore(String label) {
    String key = sanitizeId(label);
    if ("neutral".equals(key)) return 0;
    if ("default".equals(key)) return 1;
    if ("base".equals(key)) return 2;
    if ("idle".equals(key)) return 3;
    return 10;
  }

  private static String chooseTagSelection(String previous, String preferred, List<String> visible) {
    if (visible == null || visible.isEmpty()) return null;
    if (previous != null && !previous.isBlank() && visible.contains(previous)) return previous;
    if (preferred != null && !preferred.isBlank() && visible.contains(preferred)) return preferred;
    for (String id : visible) {
      if (id.startsWith("assets/characters/")) return id;
    }
    return visible.get(0);
  }

  static String deriveTagIdFromRelative(String relative) {
    String path = relative == null ? "" : relative.replace('\\', '/');
    String parent = parentPath(path);
    if (parent.isBlank()) return "(root)";

    String[] parts = parent.split("/");
    if (parts.length >= 3 && "assets".equals(parts[0]) && "characters".equals(parts[1])) {
      return "assets/characters/" + parts[2];
    }
    if (parts.length >= 2 && "assets".equals(parts[0])) {
      return "assets/" + parts[1];
    }
    if (parts.length >= 2) {
      return parts[0] + "/" + parts[1];
    }
    return parent;
  }

  static String inferGroupFromTagSubfolder(String relativePath, String tagId) {
    if (relativePath == null || relativePath.isBlank() || tagId == null || tagId.isBlank()) return "";
    if ("(root)".equals(tagId)) return "";
    String normalized = relativePath.replace('\\', '/');
    String parent = parentPath(normalized);
    if (parent.isBlank()) return "";
    String prefix = tagId + "/";
    if (!parent.startsWith(prefix)) return "";
    String remainder = parent.substring(prefix.length());
    if (remainder.isBlank()) return "";
    return sanitizeId(takeLastPathToken(remainder));
  }

  static String inferLabelFromFilenameForGroup(String baseName, String group) {
    String[] tokens = splitTokens(baseName);
    if (tokens.length == 0) return "";
    String normalizedGroup = sanitizeId(group);

    int match = -1;
    for (int i = 0; i < tokens.length; i++) {
      String mapped = normalizeGroupToken(tokens[i]);
      if (!mapped.isBlank() && mapped.equals(normalizedGroup)) {
        match = i;
      }
    }

    if (match >= 0 && match + 1 < tokens.length) {
      return sanitizeLabel(String.join("_", java.util.Arrays.copyOfRange(tokens, match + 1, tokens.length)));
    }
    if (match >= 0) {
      return sanitizeLabel(tokens[match]);
    }
    return sanitizeLabel(tokens[tokens.length - 1]);
  }

  private static InferredGroupLabel inferGroupAndLabelFromFilename(String baseName) {
    String[] tokens = splitTokens(baseName);
    if (tokens.length == 0) return new InferredGroupLabel("", "");

    int groupTokenIndex = -1;
    String normalizedGroup = "";
    for (int i = 0; i < tokens.length; i++) {
      String mapped = normalizeGroupToken(tokens[i]);
      if (!mapped.isBlank()) {
        groupTokenIndex = i;
        normalizedGroup = mapped;
      }
    }
    if (groupTokenIndex >= 0) {
      return new InferredGroupLabel(normalizedGroup, inferLabelFromFilenameForGroup(baseName, normalizedGroup));
    }

    if (tokens.length >= 2) {
      String group = sanitizeId(tokens[0]);
      String label = sanitizeLabel(String.join("_", java.util.Arrays.copyOfRange(tokens, 1, tokens.length)));
      return new InferredGroupLabel(group, label);
    }

    return new InferredGroupLabel("", sanitizeLabel(tokens[0]));
  }

  private static String[] splitTokens(String raw) {
    if (raw == null || raw.isBlank()) return new String[0];
    return raw.split("[\\s._-]+");
  }

  private static String normalizeGroupToken(String rawToken) {
    String token = sanitizeId(rawToken);
    if (token.isBlank()) return "";
    return GROUP_TOKEN_ALIASES.getOrDefault(token, "");
  }

  static Map<String, String> parseAttributeAssignments(String raw) {
    Map<String, String> out = new LinkedHashMap<>();
    if (raw == null || raw.isBlank()) return out;
    String[] tokens = raw.split("[,\\s]+");
    for (String token : tokens) {
      if (token == null) continue;
      String part = token.trim();
      if (part.isEmpty()) continue;
      String[] kv;
      if (part.contains("=")) {
        kv = part.split("=", 2);
      } else if (part.contains(":")) {
        kv = part.split(":", 2);
      } else if (part.contains("_")) {
        kv = part.split("_", 2);
      } else {
        continue;
      }
      if (kv.length != 2) continue;
      String group = sanitizeId(kv[0]);
      String value = sanitizeId(kv[1]);
      if (group.isBlank() || value.isBlank()) continue;
      out.put(group, value);
    }
    return out;
  }

  static Map<String, String> parseAttributeShortforms(String raw) {
    Map<String, String> out = new LinkedHashMap<>();
    if (raw == null || raw.isBlank()) return out;
    String[] lines = raw.split("\\R");
    for (String line : lines) {
      if (line == null) continue;
      String work = line.trim();
      if (work.isBlank() || work.startsWith("#")) continue;
      int eq = work.indexOf('=');
      if (eq <= 0) continue;
      String key = sanitizeId(work.substring(0, eq));
      String value = work.substring(eq + 1).trim();
      if (key.isBlank() || value.isBlank()) continue;
      out.put(key, value);
    }
    return out;
  }

  private boolean isImageFile(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".png")
        || name.endsWith(".jpg")
        || name.endsWith(".jpeg")
        || name.endsWith(".webp")
        || name.endsWith(".gif");
  }

  private static boolean isIgnoredPath(String relative) {
    String path = normalize(relative).toLowerCase(Locale.ROOT);
    return path.startsWith(".git/")
        || path.startsWith("build/")
        || path.startsWith(".gradle/")
        || path.contains("/build/")
        || path.startsWith("out/")
        || path.contains("/out/")
        || path.startsWith(".jvn/");
  }

  static String sanitizeId(String raw) {
    if (raw == null) return "";
    String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    s = s.replaceAll("^_+", "").replaceAll("_+$", "");
    return s;
  }

  static String sanitizeLabel(String raw) {
    if (raw == null) return "";
    String s = raw.trim().replaceAll("[\\s]+", "_");
    s = s.replaceAll("[^a-zA-Z0-9_]+", "_");
    s = s.replaceAll("^_+", "").replaceAll("_+$", "");
    return s;
  }

  static String parentPath(String path) {
    if (path == null || path.isBlank()) return "";
    int slash = path.lastIndexOf('/');
    return slash <= 0 ? "" : path.substring(0, slash);
  }

  static String takeLastPathToken(String path) {
    if (path == null || path.isBlank()) return "";
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }

  private static String defaultScriptTag(String tagId) {
    String id = sanitizeId(takeLastPathToken(tagId));
    return id.isBlank() ? "character" : id;
  }

  private static String normalize(String raw) {
    if (raw == null) return "";
    return raw.trim().replace('\\', '/');
  }

  private static String encodeKey(String raw) {
    String value = normalize(raw);
    if (value.isBlank()) return "_";
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static boolean parseBoolean(String raw, boolean fallback) {
    String value = normalize(raw).toLowerCase(Locale.ROOT);
    if (value.isBlank()) return fallback;
    if ("true".equals(value) || "1".equals(value) || "yes".equals(value) || "on".equals(value)) return true;
    if ("false".equals(value) || "0".equals(value) || "no".equals(value) || "off".equals(value)) return false;
    return fallback;
  }

  private static double parseDouble(String raw, double fallback) {
    String value = normalize(raw);
    if (value.isBlank()) return fallback;
    try {
      double parsed = Double.parseDouble(value);
      return Double.isFinite(parsed) ? parsed : fallback;
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return fallback;
    }
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private static String formatDouble(double value) {
    return String.format(Locale.ROOT, "%.3f", value);
  }

  private static String shortTag(String raw) {
    String value = normalize(raw);
    if (value.isBlank()) return "(none)";
    if (value.length() <= 48) return value;
    return "..." + value.substring(value.length() - 45);
  }

  private static void drawCheckerBackground(GraphicsContext g, double w, double h) {
    double size = 18.0;
    Color c1 = Color.web("#171d28");
    Color c2 = Color.web("#1d2840");
    for (double y = 0; y < h; y += size) {
      for (double x = 0; x < w; x += size) {
        boolean alt = (((int) (x / size)) + ((int) (y / size))) % 2 == 0;
        g.setFill(alt ? c1 : c2);
        g.fillRect(x, y, size, size);
      }
    }
  }

  private static void drawCenteredText(GraphicsContext g, double w, double h, String text) {
    g.setFill(Color.color(0, 0, 0, 0.55));
    g.fillRect(0, h - 36, w, 36);
    g.setFill(Color.web("#d4d9e2"));
    g.setTextAlign(TextAlignment.LEFT);
    g.fillText(text == null ? "" : text, 10, h - 12);
  }

  private Button iconButton(Region icon, String tooltip, Runnable action) {
    Button button = new Button();
    button.setGraphic(icon);
    button.setMinSize(28, 28);
    button.setPrefSize(28, 28);
    button.setMaxSize(28, 28);
    button.setFocusTraversable(false);
    if (tooltip != null && !tooltip.isBlank()) {
      button.setTooltip(new Tooltip(tooltip));
    }
    button.setOnAction(e -> {
      if (action != null) action.run();
    });
    return button;
  }

  private void copy(String text) {
    ClipboardContent content = new ClipboardContent();
    content.putString(text == null ? "" : text);
    Clipboard.getSystemClipboard().setContent(content);
  }

  private void status(String message) {
    statusLabel.setText(message == null ? "" : message);
  }

  private static final class ImageTag {
    final String id;
    final Map<String, List<AttributeOption>> groups = new LinkedHashMap<>();

    ImageTag(String id) {
      this.id = Objects.requireNonNullElse(id, "(tag)");
    }

    void add(AttributeOption option) {
      if (option == null) return;
      groups.computeIfAbsent(option.group, k -> new ArrayList<>()).add(option);
    }
  }

  private static final class AttributeOption {
    final String label;
    final String group;
    final String relativePath;
    final File file;
    final int sortKey;

    AttributeOption(String label, String group, String relativePath, File file, int sortKey) {
      this.label = label;
      this.group = group;
      this.relativePath = relativePath;
      this.file = file;
      this.sortKey = sortKey;
    }

    static AttributeOption none() {
      return new AttributeOption(NONE_LABEL, "none", "", null, Integer.MIN_VALUE);
    }

    boolean isNone() {
      return file == null;
    }
  }

  private record InferredGroupLabel(String group, String label) {}

  // ── File export / import ──

  private void exportCompositePng() {
    List<AttributeOption> selected = selectedOptions();
    if (selected.isEmpty()) {
      status("No attributes selected — nothing to export.");
      return;
    }
    List<Image> layers = new ArrayList<>();
    double maxW = 0, maxH = 0;
    for (AttributeOption option : selected) {
      Image img = loadImage(option);
      if (img == null || img.isError() || img.getWidth() <= 0 || img.getHeight() <= 0) continue;
      layers.add(img);
      if (img.getWidth() > maxW) maxW = img.getWidth();
      if (img.getHeight() > maxH) maxH = img.getHeight();
    }
    if (layers.isEmpty()) {
      status("Selected attribute images could not be loaded.");
      return;
    }
    Canvas offscreen = new Canvas(maxW, maxH);
    GraphicsContext g = offscreen.getGraphicsContext2D();
    for (Image img : layers) {
      g.drawImage(img, 0, 0, img.getWidth(), img.getHeight());
    }
    WritableImage snapshot = offscreen.snapshot(new SnapshotParameters() {{
      setFill(Color.TRANSPARENT);
    }}, null);

    FileChooser fc = new FileChooser();
    fc.setTitle("Export Composited PNG");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
    String suggestedName = sanitizeFilename(currentTagId) + "_" + sanitizeFilename(expressionField.getText()) + ".png";
    fc.setInitialFileName(suggestedName);
    File file = fc.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
    if (file == null) return;
    try {
      BufferedImage bImg = SwingFXUtils.fromFXImage(snapshot, null);
      ImageIO.write(bImg, "png", file);
      status("Exported PNG: " + file.getName());
    } catch (Exception ex) {
      status("PNG export failed: " + ex.getMessage());
    }
  }

  private void exportSetupToFile() {
    String text = buildFullSetupText();
    FileChooser fc = new FileChooser();
    fc.setTitle("Export Attribute Setup");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Attribute Setup", "*.attrsetup"));
    String suggestedName = sanitizeFilename(currentTagId) + ".attrsetup";
    fc.setInitialFileName(suggestedName);
    File file = fc.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
    if (file == null) return;
    try {
      Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
      status("Exported setup: " + file.getName());
    } catch (Exception ex) {
      status("Setup export failed: " + ex.getMessage());
    }
  }

  private void importSetupFromFile() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Import Attribute Setup");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Attribute Setup", "*.attrsetup"));
    File file = fc.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
    if (file == null) return;
    try {
      List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
      Map<String, String> assignments = new LinkedHashMap<>();
      for (String line : lines) {
        if (line == null) continue;
        String work = line.trim();
        if (work.isBlank() || work.startsWith("#")) continue;
        int eq = work.indexOf('=');
        if (eq <= 0) continue;
        String key = work.substring(0, eq).trim();
        String value = work.substring(eq + 1).trim();
        if (key.isBlank() || value.isBlank()) continue;
        // Handle group selections: group.sel.XXX=value
        if (key.startsWith("group.")) {
          String groupName = key.substring("group.".length());
          assignments.put(groupName, value);
        }
      }
      if (assignments.isEmpty()) {
        status("No attribute assignments found in file.");
        return;
      }
      int applied = 0;
      applyingState = true;
      try {
        for (Map.Entry<String, String> entry : assignments.entrySet()) {
          String group = resolveGroupName(entry.getKey());
          if (group == null || group.isBlank()) continue;
          ComboBox<AttributeOption> combo = selectors.get(group);
          if (combo == null) continue;
          AttributeOption target = findOptionByValue(combo, entry.getValue());
          if (target != null) {
            combo.getSelectionModel().select(target);
            applied++;
          }
        }
      } finally {
        applyingState = false;
      }
      updateExpressionFromSelection();
      redrawPreview();
      persistCurrentTagState();
      status("Imported " + applied + " attribute(s) from " + file.getName());
    } catch (Exception ex) {
      status("Setup import failed: " + ex.getMessage());
    }
  }

  private String buildFullSetupText() {
    StringBuilder out = new StringBuilder();
    out.append("# JVN Image Attributes Tool Setup\n");
    out.append("tag=").append(currentTagId == null ? "" : currentTagId).append('\n');
    String scriptTag = normalize(exportTagField.getText());
    if (!scriptTag.isBlank()) {
      out.append("scriptTag=").append(scriptTag).append('\n');
    }
    out.append("expression=").append(normalize(expressionField.getText())).append('\n');
    out.append("exportFormat=").append(normalize(exportFormatBox.getValue())).append('\n');
    out.append('\n');
    for (String group : groupOrder) {
      ComboBox<AttributeOption> combo = selectors.get(group);
      AttributeOption option = combo == null ? null : combo.getValue();
      String selection = (option == null || option.isNone()) ? "(none)" : option.label;
      out.append("group.").append(group).append('=').append(selection).append('\n');
    }
    return out.toString();
  }

  private static String sanitizeFilename(String raw) {
    if (raw == null || raw.isBlank()) return "export";
    String s = raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    s = s.replaceAll("_+", "_").replaceAll("^_+", "").replaceAll("_+$", "");
    return s.isBlank() ? "export" : s;
  }
}
