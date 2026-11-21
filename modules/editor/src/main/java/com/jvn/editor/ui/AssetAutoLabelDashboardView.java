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

  private final AssetMediaPreview preview = new AssetMediaPreview("asset-label-preview-image");
  private final Label selectedPath = new Label("Select an asset to review");
  private final ComboBox<AssetKind> kindEditor = new ComboBox<>();
  private final TextField ownerEditor = new TextField();
  private final TextField labelEditor = new TextField();
  private final Label reason = new Label();
  private final Label selectedName = new Label();
  private final Label ownerCaption = new Label("Owner");
  private final Label evidenceCaption = new Label("Recognition");
  private final TextArea declaration = new TextArea();
  private final Button saveButton = new Button("Save Label");
  private final Button generateButton = new Button("Generate VNS");
  private final Button ignoreButton = new Button("Ignore");
  private final Button openButton = new Button("Open");
  private final Button copyButton = new Button("Copy VNS");
  private final Button batchButton = new Button("Auto-label");
  private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(task -> {
    Thread thread = new Thread(task, "jvn-asset-label-scan");
    thread.setDaemon(true);
    return thread;
  });

  private Path projectRoot;
  private ScanResult lastResult;
  private Consumer<File> onOpenFile;
  private Runnable onChanged;
  private Consumer<ScanResult> onInventory;
  private int refreshGeneration;
  private String pendingOutcome;

  AssetAutoLabelDashboardView() {
    getStyleClass().addAll("sidebar-tool-root", "asset-label-dashboard");
    AssetInventoryRow.style(this);
    batchButton.setDisable(true);
    Label title = new Label("Label review");
    title.getStyleClass().add("asset-heading");
    HBox titleRow = new HBox(6, title, SidebarToolHelp.button(this, "Asset Auto-labeling", """
        Inspect a file to see the suggested role, owner and label. Recognition lists
        the evidence, including VNS declarations, file references, matching artwork
        and naming patterns. Use the status filter to see declared or missing files.

        Save Label records your edits in .jvn/asset-labels.properties. Generate VNS
        writes the declaration to scripts/definitions/auto_labels.vns and includes
        it from the project’s entry script. Open the declaration preview to copy it.

        Auto-label applies the ready suggestions at 80% confidence or above.
        It saves media labels and generates VNS for declarable assets. Files with
        conflicting evidence stay in review."""));
    titleRow.setAlignment(Pos.CENTER_LEFT);
    summary.getStyleClass().add("asset-secondary");
    summary.setWrapText(true);
    status.getStyleClass().add("sidebar-tool-status");
    status.setWrapText(true);

    search.setPromptText("Search names, paths or owners");
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
    Button refreshButton = new Button("Refresh");
    refreshButton.setOnAction(event -> refresh());
    refreshButton.setTooltip(new Tooltip("Rescan assets and VNS declarations"));

    search.setMinWidth(0);
    search.setMaxWidth(Double.MAX_VALUE);
    kindFilter.setPrefWidth(118);
    statusFilter.setPrefWidth(115);
    FlowPane filters = new FlowPane(6, 6, kindFilter, statusFilter, newOnly, clearFilters);
    javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    titleRow.getChildren().addAll(spacer, refreshButton, batchButton);
    batchButton.getStyleClass().add("asset-label-primary");
    batchButton.setId("asset-label-auto-ready");
    batchButton.setTooltip(new Tooltip("Generate labels for ready suggestions at 80% confidence or above. Other assets stay in the review queue."));
    VBox header = new VBox(7, titleRow, summary, search, filters);
    header.getStyleClass().add("asset-toolbar");

    configureTable();
    table.setMinHeight(180);
    table.setMinWidth(0);
    table.setPrefHeight(360);
    VBox list = new VBox(table);
    list.setMinWidth(0);
    list.setMinHeight(180);
    VBox.setVgrow(table, Priority.ALWAYS);

    selectedPath.setWrapText(true);
    selectedPath.setMinWidth(0);
    selectedPath.getStyleClass().add("asset-label-selected-path");
    kindEditor.getItems().addAll(AssetKind.values());
    kindEditor.setMaxWidth(Double.MAX_VALUE);
    kindEditor.setButtonCell(new AssetKindCell());
    kindEditor.setCellFactory(ignored -> new AssetKindCell());
    reason.setWrapText(true);
    reason.getStyleClass().add("asset-evidence");
    evidenceCaption.getStyleClass().add("asset-field-caption");
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
    editor.addRow(1, ownerCaption, ownerEditor);
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
    saveButton.getStyleClass().add("asset-action");
    FlowPane actions = new FlowPane(6, 6, saveButton, generateButton, ignoreButton, openButton);
    selectedName.getStyleClass().add("asset-inspector-title");
    selectedPath.getStyleClass().add("asset-secondary");

    TitledPane vnsPreview = new TitledPane("VNS declaration", new VBox(5, declaration, copyButton));
    vnsPreview.setExpanded(false);
    vnsPreview.visibleProperty().bind(declaration.textProperty().isNotEmpty());
    vnsPreview.managedProperty().bind(vnsPreview.visibleProperty());
    VBox identity = new VBox(2, selectedName, selectedPath);
    VBox evidence = new VBox(3, evidenceCaption, reason);
    evidence.visibleProperty().bind(reason.textProperty().isNotEmpty());
    evidence.managedProperty().bind(evidence.visibleProperty());
    VBox details = new VBox(10, identity, evidence, editor, actions, preview, vnsPreview);
    details.setMinWidth(0);
    details.setPadding(new Insets(12));
    details.getStyleClass().add("asset-label-details");
    ScrollPane detailScroll = new ScrollPane(details);
    detailScroll.setFitToWidth(true);
    detailScroll.getStyleClass().add("asset-inspector-scroll");
    detailScroll.setMinSize(0, 0);
    detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    detailScroll.setId("asset-label-review-details");
    javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane(list, detailScroll);
    split.setOrientation(javafx.geometry.Orientation.VERTICAL);
    split.setDividerPositions(0.52);
    split.setId("asset-label-review-split");
    widthProperty().addListener((obs, oldWidth, newWidth) -> {
      var orientation = newWidth.doubleValue() >= 640
          ? javafx.geometry.Orientation.HORIZONTAL : javafx.geometry.Orientation.VERTICAL;
      if (split.getOrientation() != orientation) {
        details.getChildren().remove(preview);
        boolean wide = orientation == javafx.geometry.Orientation.HORIZONTAL;
        details.getChildren().add(wide ? 1 : details.getChildren().indexOf(actions) + 1, preview);
        preview.setImageHeight(wide ? 144 : 90);
        split.setOrientation(orientation);
        split.setDividerPositions(0.52);
      }
    });
    status.setPadding(new Insets(8,12,8,12));
    AssetInventoryRow.hideWhenEmpty(status);
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
    batchButton.setDisable(true);
    showSelection(null);
    refresh();
  }

  void setOnOpenFile(Consumer<File> handler) {
    onOpenFile = handler;
  }

  void setOnInventory(Consumer<ScanResult> handler) { onInventory = handler; }

  void setOnChanged(Runnable handler) {
    onChanged = handler;
  }

  private boolean hasEdits(AssetSuggestion selected) {
    return selected != null && (kindEditor.getValue() != selected.kind()
        || !ownerEditor.getText().equals(selected.owner()) || !labelEditor.getText().equals(selected.label()));
  }

  void refreshUnlessEditing() {
    if (!hasEdits(table.getSelectionModel().getSelectedItem())) refresh();
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
        + "Auto-label applies suggestions at or above 80% confidence. It saves media labels and generates declarations for VNS assets."
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
    int saved = 0;
    if (choice == auto && !targets.isEmpty()) {
      try {
        List<AssetSuggestion> confident = service.suggestDroppedAssets(projectRoot, targets).stream()
            .filter(target -> target.status() == LabelStatus.SUGGESTED)
            .filter(target -> target.confidence() >= HIGH_CONFIDENCE).toList();
        var applied = service.applyDeclarations(projectRoot, confident);
        generated = applied.declarationsGenerated();
        saved = applied.labelsSaved();
      } catch (IOException error) {
        errors.add("Imported assets, but auto-labeling stopped: " + error.getMessage());
      }
    }
    pendingOutcome = "Imported " + imported + " · Declarations generated " + generated + " · Media labels saved " + saved
        + (errors.isEmpty() ? "" : "\n" + String.join("\n", errors));
    notifyChanged();
    refresh();
    return true;
  }

  private void configureTable() {
    table.setId("asset-auto-label-table");
    table.setPlaceholder(new Label("No matching assets"));
    table.getStyleClass().add("asset-label-queue");
    table.setFixedCellSize(55);
    table.setCellFactory(ignored -> new ListCell<>() {
      private final AssetInventoryRow row = new AssetInventoryRow();
      { row.prefWidthProperty().bind(widthProperty().subtract(20)); }
      @Override protected void updateItem(AssetSuggestion asset, boolean empty) {
        super.updateItem(asset, empty);
        if (empty || asset == null) { setGraphic(null); setTooltip(null); setAccessibleText(""); return; }
        String state = asset.status() == LabelStatus.SUGGESTED
            ? (asset.confidence() >= HIGH_CONFIDENCE ? "Ready" : "Review") : asset.status().displayName();
        String identity = AssetInventoryRow.identity(asset);
        row.show(asset.file(), identity, (asset.isNew() ? "New · " : "") + state);
        setGraphic(row);
        setTooltip(new Tooltip(asset.relativePath() + "\n" + asset.reason()));
        setAccessibleText(asset.relativePath() + ", " + identity + ", " + state);
      }
    });
    table.getSelectionModel().selectedItemProperty().addListener(
        (obs, oldValue, newValue) -> showSelection(newValue));
  }

  void acceptScan(ScanResult result) {
    AssetSuggestion selected = table.getSelectionModel().getSelectedItem();
    boolean preserveDraft = pendingOutcome == null && hasEdits(selected);
    AssetKind draftKind = kindEditor.getValue();
    String draftOwner = ownerEditor.getText();
    String draftLabel = labelEditor.getText();
    lastResult = result;
    allAssets.clear();
    allAssets.addAll(result.assets());
    long newUnlabeled = result.assets().stream().filter(asset -> asset.isNew()
        && asset.status() == LabelStatus.SUGGESTED).count();
    if (onInventory != null) onInventory.accept(result);
    String counts = result.byStatus().getOrDefault(LabelStatus.SUGGESTED, 0) + " unlabeled  ·  "
        + result.declaredCount() + " declared";
    if (result.labeledCount() > 0) counts += "  ·  " + result.labeledCount() + " labeled";
    if (result.missingCount() > 0) counts += "  ·  " + result.missingCount() + " missing";
    if (newUnlabeled > 0) counts += "  ·  " + newUnlabeled + " new";
    summary.setText(counts);
    int ready = result.highConfidenceSuggestions();
    batchButton.setText(ready == 0 ? "Auto-label" : "Auto-label " + ready);
    batchButton.setDisable(ready == 0);
    status.setText(result.scanIssues() == 0 ? "" : result.scanIssues() + " unreadable items");
    if (pendingOutcome != null) {
      status.setText(pendingOutcome + (status.getText().isEmpty() ? "" : "\n" + status.getText()));
      pendingOutcome = null;
    }
    applyFilter();
    AssetSuggestion current = table.getSelectionModel().getSelectedItem();
    if (preserveDraft && current != null && current.relativePath().equals(selected.relativePath())
        && current.status() == selected.status()) {
      kindEditor.setValue(draftKind);
      ownerEditor.setText(draftOwner);
      labelEditor.setText(draftLabel);
    }
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
      return (asset.kind().displayName() + " " + asset.relativePath() + " " + asset.owner() + " " + asset.label())
          .toLowerCase(Locale.ROOT).contains(query);
    }).sorted(java.util.Comparator.comparing((AssetSuggestion asset) -> !asset.isNew())
        .thenComparing(asset -> asset.confidence() < HIGH_CONFIDENCE)
        .thenComparing(AssetSuggestion::relativePath)).toList();
    AssetSuggestion selected = table.getSelectionModel().getSelectedItem();
    table.setItems(FXCollections.observableArrayList(filtered));
    table.setPlaceholder(new Label(query.isBlank() && kind == null && !newOnly.isSelected() && labelStatus == LabelStatus.SUGGESTED
        ? "All caught up. New unlabeled assets will appear here." : "No matching assets"));
    AssetSuggestion retained = selected == null ? null : filtered.stream()
        .filter(asset -> asset.relativePath().equals(selected.relativePath())).findFirst().orElse(null);
    if (retained != null) table.getSelectionModel().select(retained);
    else if (!filtered.isEmpty()) table.getSelectionModel().selectFirst();
    else showSelection(null);
  }

  private void showSelection(AssetSuggestion suggestion) {
    boolean present = suggestion != null;
    selectedName.setText(present ? suggestion.file().getFileName().toString() : "Select an asset");
    selectedPath.setText(present ? suggestion.relativePath() : "");
    kindEditor.setValue(present ? suggestion.kind() : null);
    ownerEditor.setText(present ? suggestion.owner() : "");
    labelEditor.setText(present ? suggestion.label() : "");
    reason.setText(present ? suggestion.reason() : "");
    preview.show(present ? suggestion.file() : null);
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
    AssetKind kind = kindEditor.getValue();
    boolean owner = kind != null && kind.acceptsOwner();
    ownerEditor.setVisible(owner);
    ownerEditor.setManaged(owner);
    ownerCaption.setVisible(owner);
    ownerCaption.setManaged(owner);
    generateButton.setVisible(kind != null && kind.isVnsDeclarable());
    generateButton.setManaged(generateButton.isVisible());
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
          "Apply labels to " + candidates.size()
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
