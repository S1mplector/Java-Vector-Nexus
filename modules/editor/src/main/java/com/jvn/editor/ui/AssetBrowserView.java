package com.jvn.editor.ui;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Focused VN asset browser for quick discovery and reuse of project assets. */
public class AssetBrowserView extends BorderPane {
  private final Label titleLabel = new Label("Project assets");
  private final Label rootLabel = new Label("No project loaded");
  private final Label summaryLabel = new Label("Open a project to browse assets.");
  private final Label statusLabel = new Label("");
  private final TextField filterField = new TextField();
  private final ComboBox<String> typeFilter = new ComboBox<>();
  private final ListView<AssetItem> listView = new ListView<>();

  private final AssetMediaPreview mediaPreview = new AssetMediaPreview("asset-browser-preview-image");
  private final Label previewName = new Label();
  private final java.util.Map<String, AssetAutoLabelService.AssetSuggestion> inventory = new java.util.HashMap<>();
  private final Label previewPath = new Label("Select an asset");
  private final Label previewMeta = new Label("");
  private final Button copyPathButton = new Button("Copy Path");
  private final Button openButton = new Button("Open");
  private final Button useAssetButton = new Button("Use Asset");
  private final Button refreshButton = new Button("Refresh");
  private final AssetAutoLabelDashboardView autoLabelDashboard =
      new AssetAutoLabelDashboardView();
  private final TabPane workspaceTabs = new TabPane();
  private final Tab browseTab = new Tab("Browse");
  private final Tab autoLabelTab = new Tab("Auto-label");

  private final List<AssetItem> allItems = new ArrayList<>();
  private File projectRoot;
  private Consumer<File> onOpenAsset;
  private Consumer<String> onAssetSelected;
  private int scanIssueCount;

  public AssetBrowserView() {
    getStyleClass().add("sidebar-tool-root");
    AssetInventoryRow.style(this);
    titleLabel.getStyleClass().add("asset-heading");
    rootLabel.getStyleClass().add("sidebar-tool-subtitle");
    summaryLabel.getStyleClass().add("sidebar-tool-summary");
    statusLabel.getStyleClass().add("sidebar-tool-status");
    summaryLabel.setWrapText(true);
    statusLabel.setWrapText(true);

    filterField.setId("asset-browser-search");
    filterField.setPromptText("Search names, paths or owners");
    filterField.getStyleClass().add("sidebar-tool-search-field");
    filterField.setTooltip(new Tooltip("Search by filename, path, role, owner or label"));
    filterField.textProperty().addListener((obs, oldValue, newValue) -> applyFilter());
    filterField.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ESCAPE && !filterField.getText().isEmpty()) {
        filterField.clear();
        event.consume();
      }
    });

    typeFilter.getItems().addAll("All Types", "Image", "Audio", "Video", "Font", "Data", "File");
    for (var kind : AssetAutoLabelService.AssetKind.values()) {
      if (!typeFilter.getItems().contains(kind.displayName())) typeFilter.getItems().add(kind.displayName());
    }
    typeFilter.setValue("All Types");
    typeFilter.setMaxWidth(Double.MAX_VALUE);
    typeFilter.setTooltip(new Tooltip("Limit asset results to one file type"));
    typeFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());

    listView.setId("asset-browser-list-view");
    listView.getStyleClass().add("asset-inventory-list");
    listView.setFixedCellSize(55);
    listView.setCellFactory(lv -> new AssetCell());
    listView.setPlaceholder(new Label("No assets found"));
    listView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
      showPreview(newItem);
      updateActionState();
    });
    listView.setOnMouseClicked(e -> {
      if (e.getClickCount() < 2) return;
      AssetItem item = listView.getSelectionModel().getSelectedItem();
      if (item == null) return;
      openAsset(item.file());
    });
    listView.setOnKeyPressed(event -> {
      AssetItem item = listView.getSelectionModel().getSelectedItem();
      if (item == null) return;
      if (event.getCode() == KeyCode.ENTER) {
        openAsset(item.file());
        event.consume();
      } else if (event.getCode() == KeyCode.C && event.isShortcutDown()) {
        copyPath(item);
        event.consume();
      }
    });

    listView.setOnDragDetected(e -> {
      AssetItem item = listView.getSelectionModel().getSelectedItem();
      if (item == null) return;
      Dragboard db = listView.startDragAndDrop(TransferMode.COPY);
      ClipboardContent content = new ClipboardContent();
      content.putString(item.relativePath());
      db.setContent(content);
      e.consume();
    });

    copyPathButton.setOnAction(e -> {
      AssetItem item = listView.getSelectionModel().getSelectedItem();
      if (item != null) copyPath(item);
    });
    copyPathButton.setTooltip(new Tooltip("Copy the selected asset's project-relative path"));

    openButton.setOnAction(e -> {
      AssetItem item = listView.getSelectionModel().getSelectedItem();
      if (item == null) return;
      openAsset(item.file());
    });
    openButton.setTooltip(new Tooltip("Open the selected asset in the system default app"));

    useAssetButton.setTooltip(new Tooltip("Send the selected asset path to the active editor tool"));
    useAssetButton.setOnAction(e -> {
      AssetItem item = listView.getSelectionModel().getSelectedItem();
      if (item != null && onAssetSelected != null) {
        onAssetSelected.accept(item.relativePath());
        status("Sent " + item.relativePath() + " to the active editor.");
      }
    });

    refreshButton.setTooltip(new Tooltip("Rescan project asset folders"));
    refreshButton.setOnAction(e -> { refresh(); autoLabelDashboard.refreshUnlessEditing(); });

    HBox filterRow = new HBox(8, filterField, typeFilter);
    HBox.setHgrow(filterField, Priority.ALWAYS);
    typeFilter.setPrefWidth(144);
    filterField.setMinWidth(35);

    HBox titleRow = new HBox(6, titleLabel, SidebarToolHelp.button(this, "Asset Browser", """
        Browse includes the project’s assets folder and standard game media folders.
        Search for a name, path, role, character or label. The type filter can show a
        media format such as Audio or a role such as Voice or Background.

        Select a file to see its preview and project-relative path. Copy Path copies
        that path. Use Asset sends it to the active tool. Open launches the file.

        Drop files on the editor to import them and review their labels."""));
    titleRow.setAlignment(Pos.CENTER_LEFT);
    javafx.scene.layout.Region titleSpacer = new javafx.scene.layout.Region();
    HBox.setHgrow(titleSpacer, Priority.ALWAYS);
    titleRow.getChildren().addAll(titleSpacer, refreshButton);
    VBox header = new VBox(7, titleRow, new HBox(8, rootLabel, summaryLabel), filterRow);
    header.getStyleClass().add("asset-toolbar");

    javafx.scene.layout.FlowPane previewActions = new javafx.scene.layout.FlowPane(6, 6, useAssetButton, copyPathButton, openButton);
    useAssetButton.getStyleClass().add("asset-action");

    previewName.getStyleClass().add("asset-inspector-title");
    previewPath.getStyleClass().add("asset-secondary");
    AssetInventoryRow.hideWhenEmpty(statusLabel);
    VBox previewBox = new VBox(8, previewName, previewPath, mediaPreview, previewMeta, previewActions, statusLabel);
    previewBox.setMinWidth(0);
    previewBox.setPadding(new Insets(10));
    previewBox.getStyleClass().add("asset-label-details");
    previewPath.setWrapText(true);
    previewMeta.getStyleClass().add("sidebar-tool-subtitle");

    var inspector = new javafx.scene.control.ScrollPane(previewBox);
    inspector.getStyleClass().add("asset-inspector-scroll");
    inspector.setFitToWidth(true);
    inspector.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
    inspector.setMinSize(0, 0);
    SplitPane center = new SplitPane(listView, inspector);
    center.setOrientation(javafx.geometry.Orientation.VERTICAL);
    center.getStyleClass().add("sidebar-tool-split");
    center.setDividerPositions(0.7);
    widthProperty().addListener((obs, oldWidth, width) -> {
      var orientation = width.doubleValue() >= 640 ? javafx.geometry.Orientation.HORIZONTAL : javafx.geometry.Orientation.VERTICAL;
      mediaPreview.setImageHeight(orientation == javafx.geometry.Orientation.HORIZONTAL ? 192 : 108);
      if (center.getOrientation() != orientation) {
        center.setOrientation(orientation);
        center.setDividerPositions(orientation == javafx.geometry.Orientation.HORIZONTAL ? 0.55 : 0.60);
      }
    });

    BorderPane browsePane = new BorderPane(center);
    browsePane.setTop(header);
    browseTab.setContent(browsePane);
    browseTab.setClosable(false);
    autoLabelTab.setContent(autoLabelDashboard);
    autoLabelTab.setClosable(false);
    workspaceTabs.getTabs().addAll(browseTab, autoLabelTab);
    workspaceTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    autoLabelDashboard.setOnInventory(this::acceptInventory);
    autoLabelDashboard.setOnChanged(this::refresh);
    setCenter(workspaceTabs);
    updateActionState();
  }

  public void setOnOpenAsset(Consumer<File> onOpenAsset) {
    this.onOpenAsset = onOpenAsset;
    autoLabelDashboard.setOnOpenFile(onOpenAsset);
  }

  public void setOnAssetSelected(Consumer<String> onAssetSelected) {
    this.onAssetSelected = onAssetSelected;
    updateActionState();
  }

  public String getSelectedAssetPath() {
    AssetItem item = listView.getSelectionModel().getSelectedItem();
    return item != null ? item.relativePath() : null;
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
    inventory.clear();
    autoLabelDashboard.setProjectRoot(root);
    refresh();
  }

  /** Opens the label inventory and review dashboard. */
  public void showAutoLabelDashboard() {
    workspaceTabs.getSelectionModel().select(autoLabelTab);
    autoLabelDashboard.refreshUnlessEditing();
  }

  /** Refreshes the queue after background discovery without replacing an unsaved label edit. */
  public void refreshAutoLabelSuggestions() {
    autoLabelDashboard.refreshUnlessEditing();
  }

  /** Handles supported files dropped on the editor and prompts for import/auto-labeling. */
  public boolean acceptDroppedFiles(List<File> files) {
    boolean handled = autoLabelDashboard.acceptDroppedFiles(files);
    if (handled) showAutoLabelDashboard();
    return handled;
  }

  public static boolean isSupportedAssetFile(File file) {
    return file != null && AssetAutoLabelService.isSupportedAsset(file.toPath());
  }

  public void refresh() {
    String selectedPath = getSelectedAssetPath();
    allItems.clear();
    listView.getItems().clear();
    mediaPreview.show(null);
    previewPath.setText("Select an asset");
    previewMeta.setText("");
    scanIssueCount = 0;

    if (projectRoot == null || !projectRoot.isDirectory()) {
      rootLabel.setText("No project loaded");
      summaryLabel.setText("Open a project to browse assets.");
      listView.setPlaceholder(new Label("No project loaded"));
      status("");
      updateActionState();
      return;
    }

    rootLabel.setText(projectRoot.getName());
    for (String folder : AssetPathHeuristics.ASSET_ROOTS) collectAssets(projectRoot.toPath().resolve(folder));

    allItems.sort(Comparator.comparing(AssetItem::relativePath, String.CASE_INSENSITIVE_ORDER));
    applyFilter();
    restoreSelection(selectedPath);
    status(scanIssueCount == 0
        ? ""
        : "Asset scan completed with " + scanIssueCount + " unreadable path"
            + (scanIssueCount == 1 ? "." : "s."));
  }

  private void collectAssets(Path base) {
    if (base == null || !Files.isDirectory(base)) return;
    try (var stream = Files.walk(base, 16)) {
      stream
          .filter(Files::isRegularFile)
          .forEach(path -> {
            try {
              File file = path.toFile();
              String rel = projectRoot.toPath().relativize(path).toString().replace('\\', '/');
              String type = typeFor(path.getFileName().toString());
              allItems.add(new AssetItem(file, rel, type));
            } catch (Exception ignored) {
              scanIssueCount++;
            }
          });
    } catch (Exception ignored) {
      scanIssueCount++;
    }
  }

  private void acceptInventory(AssetAutoLabelService.ScanResult result) {
    if (projectRoot == null || !projectRoot.toPath().toAbsolutePath().normalize().equals(result.projectRoot())) return;
    String selected = getSelectedAssetPath();
    inventory.clear();
    var listed = new java.util.HashSet<String>();
    allItems.forEach(item -> listed.add(item.relativePath()));
    for (var asset : result.assets()) {
      inventory.put(asset.relativePath(), asset);
      if (Files.isRegularFile(asset.file()) && listed.add(asset.relativePath())) {
        allItems.add(new AssetItem(asset.file().toFile(), asset.relativePath(), typeFor(asset.relativePath())));
      }
    }
    allItems.removeIf(item -> !item.file().isFile());
    allItems.sort(Comparator.comparing(AssetItem::relativePath, String.CASE_INSENSITIVE_ORDER));
    applyFilter();
    restoreSelection(selected);
  }

  private void applyFilter() {
    String query = filterField.getText();
    String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    String selectedType = typeFilter.getValue();
    boolean filterByType = selectedType != null && !"All Types".equals(selectedType);

    if (normalized.isEmpty() && !filterByType) {
      listView.setItems(FXCollections.observableArrayList(allItems));
      updateResultsSummary();
      return;
    }

    List<AssetItem> filtered = new ArrayList<>();
    for (AssetItem item : allItems) {
      var asset = inventory.get(item.relativePath());
      if (filterByType && !selectedType.equals(item.type())
          && (asset == null || !selectedType.equals(asset.kind().displayName()))) continue;
      if (!normalized.isEmpty()) {
        String haystack = (item.relativePath() + " " + item.type() + (asset == null ? "" : " " + AssetInventoryRow.identity(asset) + " " + asset.label())).toLowerCase(Locale.ROOT);
        if (!haystack.contains(normalized)) continue;
      }
      filtered.add(item);
    }
    listView.setItems(FXCollections.observableArrayList(filtered));
    updateResultsSummary();
  }

  private void showPreview(AssetItem item) {
    previewName.setText(item == null ? "" : item.file().getName());
    if (item == null) {
      mediaPreview.show(null);
      previewPath.setText("Select an asset");
      previewMeta.setText("");
      updateActionState();
      return;
    }

    previewPath.setText(item.relativePath());
    long size = item.file().length();
    var asset = inventory.get(item.relativePath());
    String role = asset == null ? item.type() : AssetInventoryRow.identity(asset);
    previewMeta.setText(role + "  ·  " + AssetInventoryRow.format(item.file().toPath()) + "  ·  " + humanFileSize(size));
    mediaPreview.show(item.file().toPath());
  }

  private void copyPath(AssetItem item) {
    ClipboardContent content = new ClipboardContent();
    content.putString(item.relativePath());
    Clipboard.getSystemClipboard().setContent(content);
    status("Copied " + item.relativePath());
  }

  private void openAsset(File file) {
    if (file == null) return;
    if (onOpenAsset != null) {
      onOpenAsset.accept(file);
      status("Opened " + file.getName());
      return;
    }
    try {
      if (!Desktop.isDesktopSupported()) throw new UnsupportedOperationException("Desktop integration is unavailable");
      Desktop.getDesktop().open(file);
      status("Opened " + file.getName());
    } catch (Exception ex) {
      status("Could not open " + file.getName() + ": " + safeMessage(ex));
    }
  }

  static String typeFor(String fileName) {
    return AssetPathHeuristics.mediaType(fileName);
  }

  static boolean isPreviewableImage(String fileName) {
    String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
        || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp");
  }

  static String humanFileSize(long sizeBytes) {
    if (sizeBytes < 1024) return sizeBytes + " B";
    double kb = sizeBytes / 1024.0;
    if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
    double mb = kb / 1024.0;
    if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
    double gb = mb / 1024.0;
    return String.format(Locale.ROOT, "%.2f GB", gb);
  }

  /** Formats a project-relative asset path as one safe VNS argument token. */
  public static String vnsTokenForPath(String relativePath) {
    String value = relativePath == null ? "" : relativePath.trim();
    if (value.isBlank()) return "\"\"";
    if (value.matches("[A-Za-z0-9_./:@+-]+")) return value;
    return "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        + "\"";
  }

  private void updateResultsSummary() {
    int visible = listView.getItems().size();
    int total = allItems.size();
    rootLabel.setText(projectRoot == null ? "No project loaded" : projectRoot.getName());
    summaryLabel.setText(visible == total
        ? total + " asset" + (total == 1 ? "" : "s")
        : visible + " of " + total + " assets");
    boolean filtered = !filterField.getText().trim().isEmpty() || !"All Types".equals(typeFilter.getValue());
    listView.setPlaceholder(new Label(total == 0
        ? "No files in the project’s asset folders"
        : filtered ? "No assets match the current filters" : "No assets found"));
    updateActionState();
  }

  private void restoreSelection(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) return;
    listView.getItems().stream()
        .filter(item -> relativePath.equals(item.relativePath()))
        .findFirst()
        .ifPresent(item -> listView.getSelectionModel().select(item));
  }

  private void updateActionState() {
    boolean noSelection = listView.getSelectionModel().getSelectedItem() == null;
    copyPathButton.setDisable(noSelection);
    openButton.setDisable(noSelection);
    useAssetButton.setDisable(noSelection || onAssetSelected == null);
  }

  private void status(String message) {
    statusLabel.setText(message == null ? "" : message);
  }

  private static String safeMessage(Exception ex) {
    if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) return "unknown error";
    return ex.getMessage();
  }

  private record AssetItem(File file, String relativePath, String type) {
  }

  private final class AssetCell extends ListCell<AssetItem> {
    private final AssetInventoryRow row = new AssetInventoryRow();
    { row.prefWidthProperty().bind(widthProperty().subtract(20)); }
    @Override
    protected void updateItem(AssetItem item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setGraphic(null);
        setTooltip(null);
        setAccessibleText("");
        return;
      }
      var asset = inventory.get(item.relativePath());
      String role = asset == null ? item.type() : AssetInventoryRow.identity(asset);
      row.show(item.file().toPath(), role, AssetInventoryRow.format(item.file().toPath()));
      setGraphic(row);
      setAccessibleText(item.relativePath() + ", " + role);
      setTooltip(new Tooltip(item.relativePath()));
    }
  }
}
