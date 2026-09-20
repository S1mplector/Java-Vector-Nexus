package com.jvn.editor.ui;

import com.jvn.editor.ui.AssetAutoLabelService.AssetKind;
import com.jvn.editor.ui.AssetAutoLabelService.AssetSuggestion;
import com.jvn.editor.ui.AssetAutoLabelService.LabelStatus;
import com.jvn.editor.ui.AssetAutoLabelService.ScanResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;

import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Review dashboard for discovered labels, new assets, and opt-in VNS generation. */
@SuppressWarnings({"NullAway", "unchecked"})
final class AssetAutoLabelDashboardView extends BorderPane {
  private static final double HIGH_CONFIDENCE = 0.80;

  private final AssetAutoLabelService service = new AssetAutoLabelService();
  private final Label summary = new Label("Open a project to inventory asset labels.");
  private final Label status = new Label("");
  private final TextField search = new TextField();
  private final ComboBox<AssetKind> kindFilter = new ComboBox<>();
  private final ComboBox<LabelStatus> statusFilter = new ComboBox<>();
  private final CheckBox newOnly = new CheckBox("New only");
  private final ListView<AssetSuggestion> table = new ListView<>();
  private final List<AssetSuggestion> allAssets = new ArrayList<>();

  private final ImageView preview = new ImageView();
  private final Label selectedPath = new Label("Select an asset to review");
  private final ComboBox<AssetKind> kindEditor = new ComboBox<>();
  private final TextField ownerEditor = new TextField();
  private final TextField labelEditor = new TextField();
  private final TextArea reason = new TextArea();
  private final TextArea declaration = new TextArea();
  private final Button saveButton = new Button("Save Label");
  private final Button generateButton = new Button("Generate VNS");
  private final Button ignoreButton = new Button("Ignore");
  private final Button openButton = new Button("Open");
  private final Button copyButton = new Button("Copy VNS");
  private final Button batchButton = new Button("Auto-label ready assets", SidebarToolIcon.auto());
  private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(task -> {
    Thread thread = new Thread(task, "jvn-asset-label-scan");
    thread.setDaemon(true);
    return thread;
  });

  private Path projectRoot;
  private ScanResult lastResult;
  private Consumer<File> onOpenFile;
  private Runnable onChanged;
  private int refreshGeneration;
  private String pendingOutcome;

  AssetAutoLabelDashboardView() {
    getStyleClass().addAll("sidebar-tool-root", "asset-label-dashboard");
    batchButton.setDisable(true);
    Label title = new Label("Unlabeled assets");
    title.getStyleClass().add("sidebar-tool-title");
    HBox titleRow = new HBox(6, title, SidebarToolHelp.button(this, "Asset Auto-labeling", """
        This dashboard inventories every supported project asset. Existing @background,
        @charimg, and @charlayer declarations are authoritative; reviewed editor-only labels
        are stored in .jvn/asset-labels.properties.

        Suggestions learn from declared assets in the same directory tree, then fall back to
        common background, character, panel, prop, UI, effect, audio, video, font, and data
        conventions. Confidence and the reason for each suggestion remain visible for review.

        Save Label records your decision without changing VNS. Generate VNS writes an
        idempotent declaration to scripts/definitions/auto_labels.vns and links it from the
        project's entry script. Auto-label High Confidence handles only suggestions at 80%
        confidence or above. Missing files and true owner/label conflicts are surfaced here.

        Dropping supported files anywhere on the editor opens a Review or Auto-label prompt.
        External files are copied into a recommended project asset folder first."""));
    titleRow.setAlignment(Pos.CENTER_LEFT);
    summary.getStyleClass().add("sidebar-tool-summary");
    summary.setWrapText(true);
    status.getStyleClass().add("sidebar-tool-status");
    status.setWrapText(true);

    search.setPromptText("Filter path, owner, or label...");
    search.textProperty().addListener((obs, oldValue, newValue) -> applyFilter());
    kindFilter.setPromptText("All types");
    kindFilter.getItems().add(null);
    kindFilter.getItems().addAll(AssetKind.values());
    kindFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());
    kindFilter.setButtonCell(new AssetKindCell());
    kindFilter.setCellFactory(ignored -> new AssetKindCell());
    statusFilter.setPromptText("All states");
    statusFilter.getItems().add(null);
    statusFilter.getItems().addAll(LabelStatus.values());
    statusFilter.setValue(LabelStatus.SUGGESTED);
    statusFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());
    statusFilter.setButtonCell(new LabelStatusCell());
    statusFilter.setCellFactory(ignored -> new LabelStatusCell());
    newOnly.selectedProperty().addListener((obs, oldValue, newValue) -> applyFilter());

    Button clearFilters = new Button("Clear");
    clearFilters.setOnAction(event -> {
      search.clear();
      kindFilter.setValue(null);
      statusFilter.setValue(null);
      newOnly.setSelected(false);
    });
    Button refreshButton = new Button("Refresh", SidebarToolIcon.refresh());
    refreshButton.setOnAction(event -> refresh());
    refreshButton.setTooltip(new Tooltip("Rescan assets and VNS declarations"));

    search.setMinWidth(0);
    search.setMaxWidth(Double.MAX_VALUE);
    kindFilter.setPrefWidth(130);
    statusFilter.setPrefWidth(140);
    FlowPane filters = new FlowPane(6, 6, kindFilter, statusFilter, newOnly, clearFilters);
    FlowPane primaryActions = new FlowPane(7, 7, batchButton, refreshButton);
    batchButton.getStyleClass().add("asset-label-primary");
    batchButton.setId("asset-label-auto-ready");
    batchButton.setTooltip(new Tooltip("Generate labels for ready suggestions at 80% confidence or above. Other assets stay in the review queue."));
    VBox header = new VBox(8, titleRow, summary, primaryActions, search, filters);
    header.setPadding(new Insets(12));
    header.getStyleClass().add("sidebar-tool-header");

    configureTable();
    table.setMinHeight(180);
    table.setMinWidth(0);
    table.setPrefHeight(360);
    VBox list = new VBox(table);
    list.setMinWidth(0);
    list.setMinHeight(180);
    VBox.setVgrow(table, Priority.ALWAYS);

    preview.setPreserveRatio(true);
    preview.setFitWidth(180);
    preview.setFitHeight(110);
    selectedPath.setWrapText(true);
    selectedPath.setMinWidth(0);
    selectedPath.getStyleClass().add("asset-label-selected-path");
    kindEditor.getItems().addAll(AssetKind.values());
    kindEditor.setButtonCell(new AssetKindCell());
    kindEditor.setCellFactory(ignored -> new AssetKindCell());
    reason.setEditable(false);
    reason.setWrapText(true);
    reason.setPrefRowCount(2);
    declaration.setEditable(false);
    declaration.setWrapText(true);
    declaration.setPrefRowCount(2);
    ownerEditor.setPromptText("Character / object id");
    labelEditor.setPromptText("VNS label");

    kindEditor.valueProperty().addListener((obs, oldValue, newValue) -> updateDeclarationPreview());
    ownerEditor.textProperty().addListener((obs, oldValue, newValue) -> updateDeclarationPreview());
    labelEditor.textProperty().addListener((obs, oldValue, newValue) -> updateDeclarationPreview());

    GridPane editor = new GridPane();
    editor.setHgap(7);
    editor.setVgap(6);
    editor.addRow(0, new Label("Type"), kindEditor);
    editor.addRow(1, new Label("Owner"), ownerEditor);
    editor.addRow(2, new Label("Label"), labelEditor);
    GridPane.setHgrow(kindEditor, Priority.ALWAYS);
    GridPane.setHgrow(ownerEditor, Priority.ALWAYS);
    GridPane.setHgrow(labelEditor, Priority.ALWAYS);

    saveButton.setOnAction(event -> saveSelected(LabelStatus.LABELED, false));
    generateButton.setOnAction(event -> saveSelected(LabelStatus.DECLARED, true));
    ignoreButton.setOnAction(event -> saveSelected(LabelStatus.IGNORED, false));
    openButton.setOnAction(event -> openSelected());
    copyButton.setOnAction(event -> copyDeclaration());
    batchButton.setOnAction(event -> autoLabelHighConfidence(true));
    FlowPane actions = new FlowPane(6, 6, generateButton, saveButton, ignoreButton, openButton, copyButton);

    TitledPane explanation = new TitledPane("Why this label?", reason);
    explanation.setExpanded(false);
    TitledPane vnsPreview = new TitledPane("VNS declaration", declaration);
    vnsPreview.setExpanded(false);
    TitledPane assetPreview = new TitledPane("Asset preview", preview);
    assetPreview.setExpanded(false);
    VBox details = new VBox(9, selectedPath, editor, actions, assetPreview, explanation, vnsPreview);
    details.setMinWidth(0);
    details.setPadding(new Insets(12));
    details.getStyleClass().add("asset-label-details");
    ScrollPane detailScroll = new ScrollPane(details);
    detailScroll.setFitToWidth(true);
    detailScroll.setMinSize(0, 0);
    detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    detailScroll.setId("asset-label-review-details");
    javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane(list, detailScroll);
    split.setOrientation(javafx.geometry.Orientation.VERTICAL);
    split.setDividerPositions(0.6);
    split.setId("asset-label-review-split");
    widthProperty().addListener((obs, oldWidth, newWidth) -> {
      var orientation = newWidth.doubleValue() >= 760
          ? javafx.geometry.Orientation.HORIZONTAL : javafx.geometry.Orientation.VERTICAL;
      if (split.getOrientation() != orientation) {
        split.setOrientation(orientation);
        split.setDividerPositions(0.6);
      }
    });
    status.setPadding(new Insets(8,12,8,12));
    setTop(header);
    setCenter(split);
    setBottom(status);
    showSelection(null);
  }

  void setProjectRoot(File root) {
    projectRoot = root == null ? null : root.toPath().toAbsolutePath().normalize();
    allAssets.clear();
    table.getItems().clear();
    lastResult = null;
    pendingOutcome = null;
    showSelection(null);
    refresh();
  }

  void setOnOpenFile(Consumer<File> handler) {
    onOpenFile = handler;
  }

  void setOnChanged(Runnable handler) {
    onChanged = handler;
  }

  void refreshUnlessEditing() {
    AssetSuggestion selected = table.getSelectionModel().getSelectedItem();
    if (selected != null && (kindEditor.getValue() != selected.kind()
        || !ownerEditor.getText().equals(selected.owner()) || !labelEditor.getText().equals(selected.label()))) return;
    refresh();
  }

  void refresh() {
    int generation = ++refreshGeneration;
    Path root = projectRoot;
    if (root == null || !root.toFile().isDirectory()) {
      allAssets.clear();
      table.getItems().clear();
      lastResult = null;
      summary.setText("Open a project to inventory asset labels.");
      status.setText("");
      showSelection(null);
      return;
    }
    status.setText("Scanning assets and VNS declarations...");
    scanExecutor.execute(() -> {
      try {
        ScanResult result = service.scan(root);
        Platform.runLater(() -> {
          if (generation != refreshGeneration || !root.equals(projectRoot)) return;
          acceptScan(result);
        });
      } catch (IOException error) {
        Platform.runLater(() -> {
          if (generation == refreshGeneration) status.setText("Scan failed: " + error.getMessage());
        });
      }
    });
  }

  boolean acceptDroppedFiles(List<File> files) {
    if (projectRoot == null || files == null) return false;
    List<Path> supported = files.stream().filter(file -> file != null)
        .map(File::toPath).filter(AssetAutoLabelService::isSupportedAsset).toList();
    if (supported.isEmpty()) return false;

    List<AssetSuggestion> detected;
    try {
      detected = service.suggestDroppedAssets(projectRoot, supported);
    } catch (IOException error) {
      status.setText("Could not inspect dropped assets: " + error.getMessage());
      return true;
    }
    long external = supported.stream()
        .map(path -> path.toAbsolutePath().normalize()).filter(path -> !path.startsWith(projectRoot))
        .count();
    EnumMap<AssetKind, Integer> types = new EnumMap<>(AssetKind.class);
    detected.forEach(asset -> types.merge(asset.kind(), 1, Integer::sum));
    String typeSummary = types.entrySet().stream()
        .map(entry -> entry.getValue() + " " + entry.getKey().displayName())
        .collect(java.util.stream.Collectors.joining(", "));
    ButtonType auto = new ButtonType("Auto-label", ButtonBar.ButtonData.YES);
    ButtonType review = new ButtonType(external > 0 ? "Import & Review" : "Review", ButtonBar.ButtonData.OK_DONE);
    Alert prompt = new Alert(Alert.AlertType.CONFIRMATION);
    prompt.setTitle("New assets detected");
    prompt.setHeaderText(supported.size() + " supported asset" + (supported.size() == 1 ? "" : "s")
        + " detected · " + typeSummary);
    prompt.setContentText((external > 0
        ? external + " file(s) will be copied into a recommended assets folder. " : "")
        + "Auto-label generates VNS only for suggestions at or above 80% confidence."
        + " You can review every suggestion in this dashboard.");
    prompt.getButtonTypes().setAll(auto, review, ButtonType.CANCEL);
    ButtonType choice = prompt.showAndWait().orElse(ButtonType.CANCEL);
    if (choice == ButtonType.CANCEL) return true;

    List<String> errors = new ArrayList<>();
    int imported = 0;
    List<Path> targets = new ArrayList<>();
    for (int i = 0; i < supported.size(); i++) {
      Path source = supported.get(i);
      try {
        Path target = service.importDroppedAsset(projectRoot, source, detected.get(i));
        if (!target.toAbsolutePath().normalize().equals(source.toAbsolutePath().normalize())) imported++;
        targets.add(target);
      } catch (IOException error) {
        errors.add("Could not import " + source.getFileName() + ": " + error.getMessage());
      }
    }
    int generated = 0;
    if (choice == auto && !targets.isEmpty()) {
      try {
        List<AssetSuggestion> confident = service.suggestDroppedAssets(projectRoot, targets).stream()
            .filter(target -> target.status() == LabelStatus.SUGGESTED)
            .filter(target -> target.confidence() >= HIGH_CONFIDENCE).toList();
        generated = service.applyDeclarations(projectRoot, confident).declarationsGenerated();
      } catch (IOException error) {
        errors.add("Imported assets, but auto-labeling stopped: " + error.getMessage());
      }
    }
    pendingOutcome = "Imported " + imported + "; generated " + generated
        + ". Review remaining suggestions below."
        + (errors.isEmpty() ? "" : "\n" + String.join("\n", errors));
    notifyChanged();
    refresh();
    return true;
  }

  private void configureTable() {
    table.setId("asset-auto-label-table");
    table.setPlaceholder(new Label("No matching assets"));
    table.getStyleClass().add("asset-label-queue");
    table.setFixedCellSize(72);
    table.setCellFactory(ignored -> new ListCell<>() {
      @Override protected void updateItem(AssetSuggestion asset, boolean empty) {
        super.updateItem(asset, empty);
        setText(null);
        if (empty || asset == null) { setGraphic(null); setTooltip(null); return; }
        Label name = new Label(asset.file().getFileName().toString());
        name.getStyleClass().add("asset-label-row-name");
        name.setMinWidth(0);
        Label path = new Label(asset.relativePath());
        path.getStyleClass().add("asset-label-row-path");
        path.setMinWidth(0);
        Label label = new Label((asset.owner().isBlank() ? "" : asset.owner() + " · ") + asset.label());
        label.getStyleClass().add("asset-label-row-path");
        label.setMinWidth(0);
        VBox text = new VBox(2, name, path, label);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        String readiness = asset.status() == LabelStatus.SUGGESTED
            ? (asset.confidence() >= HIGH_CONFIDENCE ? "Ready" : "Review") : asset.status().displayName();
        Label badge = new Label((asset.isNew() ? "NEW · " : "") + readiness);
        badge.getStyleClass().add("asset-label-badge");
        badge.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        HBox row = new HBox(9, text, badge);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.prefWidthProperty().bind(table.widthProperty().subtract(34));
        setGraphic(row);
        setTooltip(new Tooltip(asset.relativePath() + "\n" + asset.reason()));
        setAccessibleText(asset.relativePath() + ", " + readiness + ", label " + asset.label());
      }
    });
    table.getSelectionModel().selectedItemProperty().addListener(
        (obs, oldValue, newValue) -> showSelection(newValue));
  }

  void acceptScan(ScanResult result) {
    lastResult = result;
    allAssets.clear();
    allAssets.addAll(result.assets());
    long newUnlabeled = result.assets().stream().filter(asset -> asset.isNew()
        && asset.status() == LabelStatus.SUGGESTED).count();
    summary.setText(result.byStatus().getOrDefault(LabelStatus.SUGGESTED, 0) + " need labels · " + result.highConfidenceSuggestions()
        + " ready to auto-label" + (newUnlabeled > 0 ? " · " + newUnlabeled + " newly detected" : "")
        + "\n" + result.declaredCount() + " already declared · " + result.missingCount() + " missing files");
    batchButton.setText("Auto-label " + result.highConfidenceSuggestions() + " ready assets");
    batchButton.setDisable(result.highConfidenceSuggestions() == 0);
    status.setText(result.scanIssues() == 0
        ? result.highConfidenceSuggestions() + " high-confidence suggestion(s) ready."
        : "Scan completed with " + result.scanIssues() + " unreadable item(s).");
    if (pendingOutcome != null) {
      status.setText(pendingOutcome + "\n" + status.getText());
      pendingOutcome = null;
    }
    applyFilter();
  }

  private void applyFilter() {
    String query = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
    AssetKind kind = kindFilter.getValue();
    LabelStatus labelStatus = statusFilter.getValue();
    List<AssetSuggestion> filtered = allAssets.stream().filter(asset -> {
      if (kind != null && asset.kind() != kind) return false;
      if (labelStatus != null && asset.status() != labelStatus) return false;
      if (newOnly.isSelected() && !asset.isNew()) return false;
      if (query.isBlank()) return true;
      return (asset.relativePath() + " " + asset.owner() + " " + asset.label())
          .toLowerCase(Locale.ROOT).contains(query);
    }).sorted(java.util.Comparator.comparing((AssetSuggestion asset) -> !asset.isNew())
        .thenComparing(asset -> asset.confidence() < HIGH_CONFIDENCE)
        .thenComparing(AssetSuggestion::relativePath)).toList();
    AssetSuggestion selected = table.getSelectionModel().getSelectedItem();
    table.setItems(FXCollections.observableArrayList(filtered));
    table.setPlaceholder(new Label(labelStatus == LabelStatus.SUGGESTED
        ? "All caught up. New unlabeled assets will appear here." : "No matching assets"));
    AssetSuggestion retained = selected == null ? null : filtered.stream()
        .filter(asset -> asset.relativePath().equals(selected.relativePath())).findFirst().orElse(null);
    if (retained != null) table.getSelectionModel().select(retained);
    else if (!filtered.isEmpty()) table.getSelectionModel().selectFirst();
    else showSelection(null);
  }

  private void showSelection(AssetSuggestion suggestion) {
    boolean present = suggestion != null;
    selectedPath.setText(present ? suggestion.relativePath() : "Select an asset to review");
    kindEditor.setValue(present ? suggestion.kind() : null);
    ownerEditor.setText(present ? suggestion.owner() : "");
    labelEditor.setText(present ? suggestion.label() : "");
    reason.setText(present ? suggestion.reason() : "");
    preview.setImage(null);
    if (present && java.nio.file.Files.isRegularFile(suggestion.file())
        && isImage(suggestion.file())) {
      preview.setImage(new Image(suggestion.file().toUri().toString(), 240, 160, true, true, true));
    }
    saveButton.setDisable(!present);
    generateButton.setDisable(!present || !suggestion.kind().isVnsDeclarable());
    ignoreButton.setDisable(!present);
    openButton.setDisable(!present);
    copyButton.setDisable(!present || !suggestion.kind().isVnsDeclarable());
    updateDeclarationPreview();
  }

  private void updateDeclarationPreview() {
    AssetSuggestion reviewed = reviewedSelection();
    declaration.setText(reviewed == null ? "" : service.declarationFor(reviewed));
    boolean hasDeclaration = !declaration.getText().isBlank();
    generateButton.setDisable(!hasDeclaration || !java.nio.file.Files.isRegularFile(reviewed.file()));
    copyButton.setDisable(!hasDeclaration);
  }

  private AssetSuggestion reviewedSelection() {
    AssetSuggestion selected = table.getSelectionModel().getSelectedItem();
    if (selected == null || kindEditor.getValue() == null) return null;
    return selected.reviewed(kindEditor.getValue(), ownerEditor.getText(), labelEditor.getText());
  }

  private void saveSelected(LabelStatus targetStatus, boolean generate) {
    AssetSuggestion reviewed = reviewedSelection();
    if (reviewed == null || projectRoot == null) return;
    if (reviewed.label().isBlank()) {
      status.setText("A label is required.");
      return;
    }
    try {
      if (generate) service.applyDeclaration(projectRoot, reviewed);
      else service.saveDecision(projectRoot, reviewed, targetStatus);
      pendingOutcome = generate ? "Reviewed VNS declaration saved."
          : targetStatus == LabelStatus.IGNORED ? "Asset ignored." : "Label saved.";
      notifyChanged();
      refresh();
    } catch (IOException error) {
      status.setText("Could not save: " + error.getMessage());
    }
  }

  private void autoLabelHighConfidence(boolean confirm) {
    if (projectRoot == null || lastResult == null) return;
    List<AssetSuggestion> candidates = lastResult.assets().stream()
        .filter(asset -> asset.status() == LabelStatus.SUGGESTED)
        .filter(asset -> asset.confidence() >= HIGH_CONFIDENCE).toList();
    if (candidates.isEmpty()) {
      status.setText("No high-confidence suggestions are waiting for review.");
      return;
    }
    if (confirm) {
      Alert prompt = new Alert(Alert.AlertType.CONFIRMATION,
          "Generate reviewed declarations for " + candidates.size()
              + " suggestions at or above 80% confidence?",
          ButtonType.OK, ButtonType.CANCEL);
      prompt.setHeaderText("Auto-label high-confidence assets");
      if (prompt.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
    }
    Path root = projectRoot;
    batchButton.setDisable(true);
    status.setText("Applying " + candidates.size() + " ready labels...");
    scanExecutor.execute(() -> {
      try {
        AssetAutoLabelService.BatchAppliedDeclarations applied = service.applyDeclarations(root, candidates);
        Platform.runLater(() -> {
          if (!root.equals(projectRoot)) return;
          pendingOutcome = "Generated " + applied.declarationsGenerated()
              + " VNS declaration(s); saved " + applied.labelsSaved() + " non-VNS label(s).";
          notifyChanged();
          refresh();
        });
      } catch (IOException error) {
        Platform.runLater(() -> {
          if (!root.equals(projectRoot)) return;
          batchButton.setDisable(false);
          status.setText("Auto-labeling failed: " + error.getMessage());
        });
      }
    });
  }

  private void openSelected() {
    AssetSuggestion selected = table.getSelectionModel().getSelectedItem();
    if (selected == null || onOpenFile == null) return;
    Path target = selected.declarationFile() != null ? selected.declarationFile() : selected.file();
    onOpenFile.accept(target.toFile());
  }

  private void copyDeclaration() {
    if (declaration.getText().isBlank()) return;
    ClipboardContent content = new ClipboardContent();
    content.putString(declaration.getText());
    Clipboard.getSystemClipboard().setContent(content);
    status.setText("Declaration copied.");
  }

  private void notifyChanged() {
    if (onChanged != null) onChanged.run();
  }

  private boolean isImage(Path file) {
    if (file == null) return false;
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
        || name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp");
  }

  private static final class AssetKindCell extends javafx.scene.control.ListCell<AssetKind> {
    @Override protected void updateItem(AssetKind item, boolean empty) {
      super.updateItem(item, empty);
      setText(item == null ? "All types" : item.displayName());
    }
  }

  private static final class LabelStatusCell extends javafx.scene.control.ListCell<LabelStatus> {
    @Override protected void updateItem(LabelStatus item, boolean empty) {
      super.updateItem(item, empty);
      setText(item == null ? "All states" : item == LabelStatus.SUGGESTED ? "Unlabeled" : item.displayName());
    }
  }
}
