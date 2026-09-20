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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
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
  private final TableView<AssetSuggestion> table = new TableView<>();
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
  private final Button batchButton = new Button("Auto-label High Confidence");
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
    getStyleClass().add("sidebar-tool-root");
    Label title = new Label("Asset Labels");
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
    kindFilter.getItems().addAll(AssetKind.values());
    kindFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());
    kindFilter.setButtonCell(new AssetKindCell());
    kindFilter.setCellFactory(ignored -> new AssetKindCell());
    statusFilter.setPromptText("All states");
    statusFilter.getItems().addAll(LabelStatus.values());
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

    HBox filters = new HBox(6, search, kindFilter, statusFilter, newOnly, clearFilters, refreshButton);
    HBox.setHgrow(search, Priority.ALWAYS);
    kindFilter.setPrefWidth(145);
    statusFilter.setPrefWidth(125);
    VBox header = new VBox(7, titleRow, summary, filters);
    header.setPadding(new Insets(9));
    header.getStyleClass().add("sidebar-tool-header");

    configureTable();
    VBox.setVgrow(table, Priority.ALWAYS);
    VBox list = new VBox(table);
    VBox.setVgrow(table, Priority.ALWAYS);

    preview.setPreserveRatio(true);
    preview.setFitWidth(250);
    preview.setFitHeight(150);
    selectedPath.setWrapText(true);
    kindEditor.getItems().addAll(AssetKind.values());
    kindEditor.setButtonCell(new AssetKindCell());
    kindEditor.setCellFactory(ignored -> new AssetKindCell());
    reason.setEditable(false);
    reason.setWrapText(true);
    reason.setPrefRowCount(3);
    declaration.setEditable(false);
    declaration.setWrapText(true);
    declaration.setPrefRowCount(3);
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
    HBox actions = new HBox(6, saveButton, generateButton, ignoreButton, openButton, copyButton);
    actions.setAlignment(Pos.CENTER_LEFT);

    VBox details = new VBox(
        7, preview, selectedPath, editor, new Label("Why this was suggested"), reason,
        new Label("VNS declaration"), declaration, actions, batchButton, status);
    details.setPadding(new Insets(9));
    details.getStyleClass().add("sidebar-tool-footer");

    javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane(list, details);
    split.setOrientation(javafx.geometry.Orientation.VERTICAL);
    split.setDividerPositions(0.55);
    setTop(header);
    setCenter(split);
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
    TableColumn<AssetSuggestion, String> path = new TableColumn<>("Asset");
    path.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().relativePath()));
    path.setPrefWidth(310);
    TableColumn<AssetSuggestion, String> type = new TableColumn<>("Type");
    type.setCellValueFactory(value ->
        new SimpleStringProperty(value.getValue().kind().displayName()));
    type.setPrefWidth(125);
    TableColumn<AssetSuggestion, String> owner = new TableColumn<>("Owner");
    owner.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().owner()));
    owner.setPrefWidth(105);
    TableColumn<AssetSuggestion, String> label = new TableColumn<>("Label");
    label.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().label()));
    label.setPrefWidth(135);
    TableColumn<AssetSuggestion, String> state = new TableColumn<>("State");
    state.setCellValueFactory(value -> new SimpleStringProperty(
        (value.getValue().isNew() ? "New · " : "") + value.getValue().status().displayName()));
    state.setPrefWidth(125);
    TableColumn<AssetSuggestion, String> confidence = new TableColumn<>("Confidence");
    confidence.setCellValueFactory(value -> new SimpleStringProperty(
        Math.round(value.getValue().confidence() * 100) + "%"));
    confidence.setPrefWidth(90);
    table.getColumns().addAll(path, type, owner, label, state, confidence);
    table.getSelectionModel().selectedItemProperty().addListener(
        (obs, oldValue, newValue) -> showSelection(newValue));
  }

  private void acceptScan(ScanResult result) {
    lastResult = result;
    allAssets.clear();
    allAssets.addAll(result.assets());
    summary.setText(result.currentAssetCount() + " assets · " + result.declaredCount()
        + " declared · " + result.labeledCount() + " labeled · " + result.reviewCount()
        + " need review · " + result.newAssets() + " new · " + result.missingCount()
        + " missing");
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
    }).toList();
    table.setItems(FXCollections.observableArrayList(filtered));
    if (!filtered.isEmpty()) table.getSelectionModel().selectFirst();
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
      preview.setImage(new Image(suggestion.file().toUri().toString(), true));
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
    try {
      AssetAutoLabelService.BatchAppliedDeclarations applied =
          service.applyDeclarations(projectRoot, candidates);
      pendingOutcome = "Generated " + applied.declarationsGenerated()
          + " VNS declaration(s); saved " + applied.labelsSaved() + " non-VNS label(s).";
    } catch (IOException error) {
      status.setText("Auto-labeling failed: " + error.getMessage());
      return;
    }
    notifyChanged();
    refresh();
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
      setText(empty || item == null ? null : item.displayName());
    }
  }

  private static final class LabelStatusCell extends javafx.scene.control.ListCell<LabelStatus> {
    @Override protected void updateItem(LabelStatus item, boolean empty) {
      super.updateItem(item, empty);
      setText(empty || item == null ? null : item.displayName());
    }
  }
}
