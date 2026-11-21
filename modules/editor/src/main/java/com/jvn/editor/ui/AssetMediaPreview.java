package com.jvn.editor.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;

/** A preview of the selected file, shared by the browser and the review inspector. */
final class AssetMediaPreview extends StackPane {
  private final ImageView image = new ImageView();
  private final Label sample = new Label();

  AssetMediaPreview(String imageId) {
    getStyleClass().add("asset-media-preview");
    image.setId(imageId);
    image.setPreserveRatio(true);
    image.fitWidthProperty().bind(javafx.beans.binding.Bindings.max(1, widthProperty().subtract(16)));
    image.setFitHeight(132);
    image.setSmooth(true);
    sample.setWrapText(true);
    getChildren().addAll(image, sample);
    sample.managedProperty().bind(sample.visibleProperty());
    image.managedProperty().bind(image.visibleProperty());
    managedProperty().bind(visibleProperty());
    show(null);
  }

  void setImageHeight(double height) { image.setFitHeight(height); }

  void show(Path file) {
    image.setImage(null);
    image.setVisible(false);
    sample.setVisible(false);
    setVisible(false);
    if (file == null || !Files.isRegularFile(file)) return;
    String name = file.getFileName().toString();
    if (AssetBrowserView.isPreviewableImage(name)) {
      Image bitmap = new Image(file.toUri().toString(), 640, 400, true, true, true);
      image.setImage(bitmap);
      image.setVisible(true);
      setVisible(!bitmap.isError());
      bitmap.errorProperty().addListener((obs, before, failed) -> {
        if (failed && image.getImage() == bitmap) setVisible(false);
      });
    } else if ("Font".equals(AssetPathHeuristics.mediaType(name))) {
      Font font = Font.loadFont(file.toUri().toString(), 28);
      if (font != null) showText("Aa Bb Cc   0123456789", font);
    } else if ("Audio".equals(AssetPathHeuristics.mediaType(name))) {
      try {
        var audio = AudioSystem.getAudioFileFormat(file.toFile());
        var format = audio.getFormat();
        List<String> parts = new ArrayList<>();
        if (format.getFrameRate() > 0 && audio.getFrameLength() > 0)
          parts.add(String.format(Locale.ROOT, "%.2f s", audio.getFrameLength() / format.getFrameRate()));
        if (format.getSampleRate() > 0) {
          var rate = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ROOT));
          parts.add(rate.format(format.getSampleRate() / 1000.0) + " kHz");
        }
        if (format.getChannels() > 0) parts.add(switch (format.getChannels()) {
          case 1 -> "Mono";
          case 2 -> "Stereo";
          default -> format.getChannels() + " channels";
        });
        if (!parts.isEmpty()) showText(String.join("  ·  ", parts), Font.getDefault());
      } catch (IOException | UnsupportedAudioFileException ignored) {
        // Some inventory formats have no locally installed audio decoder.
      }
    }
  }

  private void showText(String text, Font font) {
    sample.setText(text);
    sample.setFont(font);
    sample.setVisible(true);
    setVisible(true);
  }
}
