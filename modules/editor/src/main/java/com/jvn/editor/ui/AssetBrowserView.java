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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
  private final Label titleLabel = new Label("Asset Browser");
  private final Label rootLabel = new Label("No project loaded");
  private final Label summaryLabel = new Label("Open a project to browse assets.");
  private final Label statusLabel = new Label("");
  private final TextField filterField = new TextField();
  private final ComboBox<String> typeFilter = new ComboBox<>();
  private final ListView<AssetItem> listView = new ListView<>();

  private final ImageView previewImage = new ImageView();
  private final Label previewPath = new Label("Select an asset");
  private final Label previewMeta = new Label("");
  private final Button copyPathButton = new Button("Copy Path");
  private final Button openButton = new Button("Open");
  private final Button useAssetButton = new Button("Use Asset");
  private final Button refreshButton = new Button("Refresh", SidebarToolIcon.refresh());
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
    titleLabel.getStyleClass().add("sidebar-tool-title");
    rootLabel.getStyleClass().add("sidebar-tool-subtitle");
    summaryLabel.getStyleClass().add("sidebar-tool-summary");
    statusLabel.getStyleClass().add("sidebar-tool-status");
    summaryLabel.setWrapText(true);
    statusLabel.setWrapText(true);

    filterField.setPromptText("Filter assets...");
    filterField.getStyleClass().add("sidebar-tool-search-field");
    filterField.setTooltip(new Tooltip("Filter assets by filename or path"));
    filterField.textProperty().addListener((obs, oldValue, newValue) -> applyFilter());
    filterField.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ESCAPE && !filterField.getText().isEmpty()) {
        filterField.clear();
        event.consume();
      }
    });

    typeFilter.getItems().addAll("All Types", "Image", "Audio", "Video", "Font", "Data", "File");
    typeFilter.setValue("All Types");
    typeFilter.setMaxWidth(Double.MAX_VALUE);
    typeFilter.setTooltip(new Tooltip("Limit asset results to one file type"));
    typeFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());

    listView.setId("asset-browser-list-view");
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

    previewImage.setId("asset-browser-preview-image");
    previewImage.setPreserveRatio(true);
    previewImage.setFitHeight(150);
    previewImage.setFitWidth(260);
    previewImage.setSmooth(true);

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
    refreshButton.setOnAction(e -> refresh());

    HBox filterRow = new HBox(8, filterField, typeFilter);
    HBox.setHgrow(filterField, Priority.ALWAYS);
    typeFilter.setPrefWidth(100);

    HBox titleRow = new HBox(6, titleLabel, SidebarToolHelp.button(this, "Asset Browser", """
        The Asset Browser shows all media files (images, audio, fonts, etc.) \
found inside your project's assets directory.

Use the filter bar to narrow results by filename. The type dropdown limits \
results to a specific file extension category.

Clicking an asset shows a preview, its relative path, and metadata in the \
panel below. From there you can:
  • Copy path — copy the relative path to use in scripts or dialogue
  • Open      — open the file in the system default application
  • Use Asset — insert the asset reference at the current editor cursor

Browsing is read-only. Drop supported files anywhere on the editor to import \
them and review or generate labels in the Auto-label tab."""));
    titleRow.setAlignment(Pos.CENTER_LEFT);
    javafx.scene.layout.Region titleSpacer = new javafx.scene.layout.Region();
    HBox.setHgrow(titleSpacer, Priority.ALWAYS);
    titleRow.getChildren().addAll(titleSpacer, refreshButton);
    VBox header = new VBox(6, titleRow, rootLabel, summaryLabel, filterRow);
    header.setPadding(new Insets(10, 10, 8, 10));
    header.getStyleClass().add("sidebar-tool-header");

    HBox previewActions = new HBox(8, copyPathButton, openButton, useAssetButton);
    previewActions.setAlignment(Pos.CENTER_LEFT);

    VBox previewBox = new VBox(6, previewImage, previewPath, previewMeta, previewActions, statusLabel);
    previewBox.setPadding(new Insets(10));
    previewBox.getStyleClass().add("sidebar-tool-footer");
    previewPath.setWrapText(true);
    previewMeta.getStyleClass().add("sidebar-tool-subtitle");

    SplitPane center = new SplitPane(listView, previewBox);
    center.setOrientation(javafx.geometry.Orientation.VERTICAL);
    center.getStyleClass().add("sidebar-tool-split");
    center.setDividerPositions(0.7);
    SplitPane.setResizableWithParent(previewBox, true);

    BorderPane browsePane = new BorderPane(center);
    browsePane.setTop(header);
    browseTab.setContent(browsePane);
    browseTab.setClosable(false);
    autoLabelTab.setContent(autoLabelDashboard);
    autoLabelTab.setClosable(false);
    workspaceTabs.getTabs().addAll(browseTab, autoLabelTab);
    workspaceTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
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
    previewImage.setImage(null);
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
    collectAssets(projectRoot.toPath().resolve("assets"));
    collectAssets(projectRoot.toPath().resolve("game/images"));

    allItems.sort(Comparator.comparing(AssetItem::relativePath, String.CASE_INSENSITIVE_ORDER));
    applyFilter();
    restoreSelection(selectedPath);
    status(scanIssueCount == 0
        ? "Asset scan complete."
        : "Asset scan completed with " + scanIssueCount + " unreadable path"
            + (scanIssueCount == 1 ? "." : "s."));
  }

  private void collectAssets(Path base) {
    if (base == null || !Files.isDirectory(base)) return;
    try (var stream = Files.walk(base, 8)) {
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
      if (filterByType && !selectedType.equals(item.type())) continue;
      if (!normalized.isEmpty()) {
        String haystack = (item.relativePath() + " " + item.type()).toLowerCase(Locale.ROOT);
        if (!haystack.contains(normalized)) continue;
      }
      filtered.add(item);
    }
    listView.setItems(FXCollections.observableArrayList(filtered));
    updateResultsSummary();
  }

  private void showPreview(AssetItem item) {
    if (item == null) {
      previewImage.setImage(null);
      previewPath.setText("Select an asset");
      previewMeta.setText("");
      updateActionState();
      return;
    }

    previewPath.setText(item.relativePath());
    long size = item.file().length();
    previewMeta.setText(item.type() + "  •  " + humanFileSize(size));

    if (isPreviewableImage(item.file().getName())) {
      try {
        Image image = new Image(item.file().toURI().toString(), 260, 150, true, true, true);
        previewImage.setImage(image);
      } catch (Exception ex) {
        previewImage.setImage(null);
      }
    } else {
      previewImage.setImage(null);
    }
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
    String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
        || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp")
        || lower.endsWith(".svg") || lower.endsWith(".tif") || lower.endsWith(".tiff")) return "Image";
    if (lower.endsWith(".ogg") || lower.endsWith(".wav") || lower.endsWith(".mp3")
        || lower.endsWith(".flac") || lower.endsWith(".aac") || lower.endsWith(".m4a")) return "Audio";
    if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov")
        || lower.endsWith(".avi") || lower.endsWith(".mkv")) return "Video";
    if (lower.endsWith(".ttf") || lower.endsWith(".otf")) return "Font";
    if (lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml")
        || lower.endsWith(".toml") || lower.endsWith(".xml") || lower.endsWith(".properties")
        || lower.endsWith(".csv") || lower.endsWith(".tsv")) return "Data";
    return "File";
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
        ? "No assets found in assets/ or game/images/"
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

  private static final class AssetCell extends ListCell<AssetItem> {
    @Override
    protected void updateItem(AssetItem item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        setStyle("");
        return;
      }
      setText(item.relativePath() + "  [" + item.type() + "]");
      setStyle("");
    }
  }
}
