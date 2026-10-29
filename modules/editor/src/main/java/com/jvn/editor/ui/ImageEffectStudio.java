package com.jvn.editor.ui;

import com.jvn.core.assets.ImageEffects;
import com.jvn.fx.image.FxImageEffectCache;
import com.jvn.plugin.api.image.*;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
import javafx.util.Duration;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Native, toolkit-owned preview shared by image-effect plugins. */
public final class ImageEffectStudio {
  private final Stage stage = new Stage();
  private final FxImageEffectCache cache = new FxImageEffectCache();
  private final ImageEffect effect;
  private final String effectId;
  private final Path project;
  private final Canvas preview = new Canvas(560, 620);
  private final Label status = new Label();
  private final TextField character = new TextField("lavender");
  private final Map<String, Slider> sliders = new LinkedHashMap<>();
  private Map<String, Double> parameters;
  private Image source;
  private boolean applyingPreset;
  private final PauseTransition debounce = new PauseTransition(Duration.millis(180));
  private final ExecutorService exporter = Executors.newSingleThreadExecutor(task -> {
    Thread thread = new Thread(task, "jvn-image-export"); thread.setDaemon(true); return thread;
  });

  public static void open(Window owner, Path project, String effectId) {
    if (ImageEffects.registry() == null) throw new IllegalStateException("No plugin host is active");
    ImageEffect effect = ImageEffects.registry().find(effectId).orElseThrow(() -> new IllegalArgumentException("Unknown effect: " + effectId));
    new ImageEffectStudio(owner, project, effectId, effect).stage.show();
  }
  private ImageEffectStudio(Window owner, Path project, String effectId, ImageEffect effect) {
    this.project = project; this.effectId = effectId; this.effect = effect;
    parameters = effect.resolve(Map.of());
    if (owner != null) stage.initOwner(owner);
    stage.setTitle(effect.label()); stage.setMinWidth(850); stage.setMinHeight(600);
    Button choose = new Button("Open image…"); choose.setOnAction(event -> chooseImage());
    Button lavender = new Button("Use Lavender"); lavender.setOnAction(event -> loadLavender());
    Button copy = new Button("Copy VNS settings"); copy.setOnAction(event -> copySettings());
    Button export = new Button("Export PNG drawings…"); export.setOnAction(event -> exportDrawings(export));
    CheckBox animate = new CheckBox("Animate"); animate.setSelected(true);
    ComboBox<String> presets = new ComboBox<>(); presets.getItems().addAll(effect.presets().keySet());
    presets.setPromptText("Preset"); presets.setOnAction(event -> {
      if (presets.getValue() == null) return;
      var values = effect.resolve(effect.presets().get(presets.getValue()));
      applyingPreset = true;
      sliders.forEach((key, slider) -> slider.setValue(values.get(key)));
      applyingPreset = false; updateParameters();
    });
    VBox controls = new VBox(8, new Label(effect.description()), new HBox(8, choose, lavender), presets);
    ((Label) controls.getChildren().getFirst()).setWrapText(true);
    controls.setPadding(new Insets(16)); controls.setPrefWidth(320);
    for (ImageEffectParameter parameter : effect.parameters()) {
      Label value = new Label();
      Slider slider = new Slider(parameter.minimum(), parameter.maximum(), parameter.defaultValue());
      slider.setAccessibleText(parameter.label());
      slider.setBlockIncrement(parameter.integer() ? 1 : (parameter.maximum() - parameter.minimum()) / 100);
      Runnable label = () -> value.setText(parameter.label() + " · " + format(parameter.integer() ? Math.rint(slider.getValue()) : slider.getValue()));
      slider.valueProperty().addListener((obs, before, after) -> { label.run(); if (!applyingPreset) debounce.playFromStart(); });
      label.run(); sliders.put(parameter.name(), slider); controls.getChildren().addAll(value, slider);
    }
    character.setPromptText("Character identifier"); character.setAccessibleText("VNS character identifier");
    controls.getChildren().addAll(new Label("Apply to character"), character, copy, export);
    debounce.setOnFinished(event -> updateParameters());
    ScrollPane scroll = new ScrollPane(controls); scroll.setFitToWidth(true); scroll.setPrefWidth(340);
    StackPane canvas = new StackPane(preview); canvas.setStyle("-fx-background-color: white;");
    preview.widthProperty().bind(canvas.widthProperty()); preview.heightProperty().bind(canvas.heightProperty());
    BorderPane root = new BorderPane(canvas); root.setRight(scroll);
    HBox footer = new HBox(16, animate, status); footer.setPadding(new Insets(10)); root.setBottom(footer);
    stage.setScene(new Scene(root, 960, 740));
    loadLavender();
    AnimationTimer timer = new AnimationTimer() {
      long started;
      @Override public void handle(long now) {
        if (started == 0) started = now;
        var gc = preview.getGraphicsContext2D();
        gc.setFill(Color.WHITE); gc.fillRect(0, 0, preview.getWidth(), preview.getHeight());
        if (source == null) return;
        Image frame = cache.frame(source, effectId, parameters, animate.isSelected() ? (now - started) / 1_000_000 : 0);
        double scale = Math.min((preview.getWidth() - 40) / frame.getWidth(), (preview.getHeight() - 40) / frame.getHeight());
        if (scale > 0) gc.drawImage(frame, (preview.getWidth() - frame.getWidth() * scale) / 2,
            (preview.getHeight() - frame.getHeight() * scale) / 2, frame.getWidth() * scale, frame.getHeight() * scale);
        status.setText(cache.status());
      }
    };
    timer.start();
    stage.setOnHidden(event -> { timer.stop(); debounce.stop(); cache.close(); exporter.shutdownNow(); });
  }
  private void updateParameters() {
    Map<String, Double> values = new LinkedHashMap<>();
    for (ImageEffectParameter p : effect.parameters()) {
      double value = sliders.get(p.name()).getValue(); values.put(p.name(), p.integer() ? Math.rint(value) : Math.round(value * 1000) / 1000.0);
    }
    parameters = effect.resolve(values); cache.clear();
  }
  private void chooseImage() {
    FileChooser chooser = new FileChooser(); chooser.setTitle("Choose a character or line drawing");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
    if (project != null && Files.isDirectory(project)) chooser.setInitialDirectory(project.toFile());
    var file = chooser.showOpenDialog(stage); if (file == null) return;
    Image image = new Image(file.toURI().toString(), 768, 768, true, true);
    if (image.isError()) { error("Could not read image", image.getException()); return; }
    source = image; cache.clear();
  }
  private void loadLavender() {
    try {
      Canvas composition = new Canvas(720, 1080);
      for (String layer : List.of("base/lavender_test_sprite_base.png", "eyes/lavender_test_sprite_eyes_neutral.png", "mouth/lavender_test_sprite_mouth_neutral.png")) {
        Path local = project == null ? null : project.resolve("assets/demo/characters/lavender/" + layer);
        Image image;
        if (local != null && Files.isRegularFile(local)) image = new Image(local.toUri().toString());
        else {
          var url = getClass().getResource("/com/jvn/editor/templates/new-project/demo-assets/Lavender_test_sprite/" + layer);
          if (url == null) throw new IllegalStateException("Bundled Lavender layer is missing: " + layer);
          image = new Image(url.toExternalForm());
        }
        if (image.isError()) throw new IllegalStateException("Could not read Lavender layer: " + layer);
        composition.getGraphicsContext2D().drawImage(image, 0, 0, 720, 1080);
      }
      SnapshotParameters snapshot = new SnapshotParameters(); snapshot.setFill(Color.TRANSPARENT);
      source = composition.snapshot(snapshot, null); cache.clear();
    } catch (Exception failure) { error("Could not load Lavender", failure); }
  }
  private String script() {
    String id = character.getText().trim();
    if (!id.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Use a VNS character identifier, such as lavender");
    String key = "character." + id + ".imageEffect";
    StringBuilder text = new StringBuilder("[set " + key + " " + effectId + "]\n");
    for (ImageEffectParameter parameter : effect.parameters()) text.append("[set ").append(key).append('.').append(parameter.name())
        .append(' ').append(format(parameters.get(parameter.name()))).append("]\n");
    return text.toString();
  }
  private void copySettings() {
    try {
      updateParameters(); ClipboardContent content = new ClipboardContent(); content.putString(script()); Clipboard.getSystemClipboard().setContent(content);
    } catch (Exception failure) { error("Could not copy settings", failure); }
  }
  private void exportDrawings(Button button) {
    if (source == null) return;
    updateParameters();
    String settings;
    try { settings = script(); } catch (Exception failure) { error("Invalid character identifier", failure); return; }
    DirectoryChooser chooser = new DirectoryChooser(); chooser.setTitle("Choose a parent folder for a new drawing export");
    var folder = chooser.showDialog(stage); if (folder == null) return;
    ImagePixels pixels = FxImageEffectCache.copyPixels(source); var values = parameters;
    button.setDisable(true);
    exporter.submit(() -> {
      try {
        ImageAnimation animation = effect.prepare(pixels, values);
        Path output = Files.createTempDirectory(folder.toPath(), "pikupiku-");
        for (int i = 0; i < animation.frames().size(); i++) {
          ImagePixels frame = animation.frames().get(i);
          BufferedImage png = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_ARGB);
          png.setRGB(0, 0, frame.width(), frame.height(), frame.pixels(), 0, frame.width());
          ImageIO.write(png, "png", output.resolve(String.format(Locale.ROOT, "drawing-%02d.png", i + 1)).toFile());
        }
        Files.writeString(output.resolve("effect.vns"), settings, StandardOpenOption.CREATE_NEW);
        Files.writeString(output.resolve("animation.properties"), "frames=" + animation.frames().size() + "\nfps=" + animation.framesPerSecond() + "\n", StandardOpenOption.CREATE_NEW);
        Platform.runLater(() -> { if (stage.isShowing()) { button.setDisable(false); new Alert(Alert.AlertType.INFORMATION, "Drawings exported to " + output, ButtonType.OK).show(); } });
      } catch (Exception failure) {
        Platform.runLater(() -> { if (stage.isShowing()) { button.setDisable(false); error("Export failed", failure); } });
      }
    });
  }
  private void error(String title, Exception failure) {
    Alert alert = new Alert(Alert.AlertType.ERROR, failure.getMessage(), ButtonType.OK); alert.initOwner(stage); alert.setHeaderText(title); alert.show();
  }
  private static String format(double value) { return java.math.BigDecimal.valueOf(Math.round(value * 1000) / 1000.0).stripTrailingZeros().toPlainString(); }
}
