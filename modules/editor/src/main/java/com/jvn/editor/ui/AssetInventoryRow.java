package com.jvn.editor.ui;

import com.jvn.editor.ui.AssetAutoLabelService.AssetKind;
import com.jvn.editor.ui.AssetAutoLabelService.AssetSuggestion;
import java.nio.file.Path;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Shared visual identity for an asset in Browse and label review. */
final class AssetInventoryRow extends HBox {
  private record ThumbnailKey(Path path, long modified, long length) {}
  // Only accessed on the FX thread. Virtualized rows share small decoded images across both tabs.
  private static final Map<ThumbnailKey, Image> THUMBNAILS = new LinkedHashMap<>(64, .75f, true) {
    @Override protected boolean removeEldestEntry(Map.Entry<ThumbnailKey, Image> eldest) { return size() > 192; }
  };

  private final StackPane thumbnail = new StackPane();
  private final Label name = new Label();
  private final Label description = new Label();
  private final Label trailing = new Label();

  AssetInventoryRow() {
    super(10);
    setAlignment(Pos.CENTER_LEFT);
    thumbnail.getStyleClass().add("asset-thumbnail");
    thumbnail.setMinSize(36, 36);
    thumbnail.setMaxSize(36, 36);
    name.getStyleClass().add("asset-name");
    description.getStyleClass().add("asset-secondary");
    trailing.getStyleClass().add("asset-row-state");
    trailing.setMinWidth(USE_PREF_SIZE);
    VBox labels = new VBox(2, name, description);
    labels.setMinWidth(0);
    labels.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(labels, Priority.ALWAYS);
    getChildren().addAll(thumbnail, labels, trailing);
  }

  void show(Path file, String subtitle, String state) {
    String filename = file.getFileName().toString();
    name.setText(filename);
    description.setText(subtitle);
    trailing.setText(state);
    trailing.setVisible(!state.isBlank());
    trailing.setManaged(!state.isBlank());
    var icon = ProjectFileIcons.iconFor(ProjectFileIcons.kindFor(filename, false, false), 22);
    thumbnail.getChildren().setAll(icon);
    if (AssetBrowserView.isPreviewableImage(filename) && java.nio.file.Files.isRegularFile(file)) {
      var diskFile = file.toFile();
      var key = new ThumbnailKey(file, diskFile.lastModified(), diskFile.length());
      Image image = THUMBNAILS.computeIfAbsent(key, entry -> new Image(entry.path().toUri().toString(), 68, 68, true, true, true));
      ImageView preview = new ImageView(image);
      preview.setFitWidth(32);
      preview.setFitHeight(32);
      preview.setPreserveRatio(true);
      thumbnail.getChildren().add(preview);
      icon.visibleProperty().bind(image.progressProperty().lessThan(1).or(image.errorProperty()));
    }
  }

  static String identity(AssetSuggestion asset) {
    String role = asset.kind() == AssetKind.UNKNOWN
        ? AssetPathHeuristics.mediaType(asset.relativePath()) : asset.kind().displayName();
    return asset.owner().isBlank() ? role : role + "  ·  " + asset.owner();
  }

  static String format(Path file) {
    String name = file.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toUpperCase(Locale.ROOT);
  }

  static void hideWhenEmpty(Labeled label) {
    label.visibleProperty().bind(label.textProperty().isNotEmpty());
    label.managedProperty().bind(label.visibleProperty());
  }

  static void style(javafx.scene.Parent root) {
    root.getStyleClass().add("asset-workspace");
    root.getStylesheets().add(AssetInventoryRow.class.getResource("/com/jvn/editor/asset-workspace.css").toExternalForm());
  }
}
