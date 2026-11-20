package com.jvn.editor.ui;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Release notes, shared by the Editor and the standalone launcher. */
public final class WhatsNewDialog {
  private WhatsNewDialog() {}

  public static void show(Window owner, WhatsNewCatalog.Release release) {
    if (release == null) return;
    Stage stage = new Stage();
    if (owner != null) stage.initOwner(owner);
    stage.initModality(owner == null ? Modality.APPLICATION_MODAL : Modality.WINDOW_MODAL);
    stage.setTitle("What's New in JVN " + release.versionLabel());
    var icon = WhatsNewDialog.class.getResource("/com/jvn/editor/images/jvn_editor_icon.png");
    var image = new Image(java.util.Objects.requireNonNull(icon).toExternalForm(), 96, 96, true, true);
    stage.getIcons().add(image);
    var logo = new ImageView(image);
    logo.setFitWidth(46);
    logo.setFitHeight(46);
    Label heading = label("What's new in JVN " + release.versionLabel(), "session-window-title");
    Label summary = label(release.summary(), "release-summary");
    VBox intro = new VBox(4, heading, summary);
    HBox.setHgrow(intro, Priority.ALWAYS);
    HBox header = new HBox(14, logo, intro);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("session-header");

    ListView<WhatsNewCatalog.Section> topics = new ListView<>();
    topics.getStyleClass().add("release-topics");
    topics.setAccessibleText("Release topics");
    topics.setPrefWidth(190);
    topics.getItems().setAll(release.sections());
    topics.setCellFactory(list -> new ListCell<>() {
      @Override protected void updateItem(WhatsNewCatalog.Section section, boolean empty) {
        super.updateItem(section, empty);
        setText(empty || section == null ? null : section.title());
        setGraphic(empty || section == null ? null : AeroIcon.of(section.icon(), 19));
        setWrapText(true);
      }
    });

    VBox detail = new VBox(12);
    detail.getStyleClass().add("release-detail");
    ScrollPane reader = new ScrollPane(detail);
    reader.setFitToWidth(true);
    reader.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    reader.getStyleClass().add("release-reader");
    topics.getSelectionModel().selectedItemProperty().addListener((obs, previous, section) -> {
      detail.getChildren().clear();
      if (section == null) return;
      detail.getChildren().addAll(label(section.title(), "release-topic-title"),
          label(section.summary(), "release-topic-summary"));
      for (String change : section.changes()) {
        Label text = label(change, "release-change");
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(8, label("•", "release-bullet"), text);
        detail.getChildren().add(row);
      }
      if (section.preview() != null) {
        var url = WhatsNewDialog.class.getResource("release/" + section.preview());
        if (url != null) {
          ImageView preview = new ImageView(new Image(url.toExternalForm()));
          preview.setPreserveRatio(true);
          preview.fitWidthProperty().bind(reader.viewportBoundsProperty().map(bounds -> Math.max(1, bounds.getWidth() - 40)));
          detail.getChildren().add(preview);
        }
      }
      reader.setVvalue(0);
    });
    topics.getSelectionModel().selectFirst();
    Button close = new Button("Close");
    close.getStyleClass().add("editor-dialog-button");
    close.setCancelButton(true);
    close.setOnAction(e -> stage.close());
    HBox footer = new HBox(close);
    footer.getStyleClass().add("release-footer");
    footer.setAlignment(Pos.CENTER_RIGHT);
    BorderPane root = new BorderPane(reader, header, null, footer, topics);
    root.getStyleClass().add("release-window");

    Rectangle2D bounds = owner == null ? Screen.getPrimary().getVisualBounds()
        : Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
            .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
    Scene scene = new Scene(root, Math.min(846, bounds.getWidth() - 40), Math.min(590, bounds.getHeight() - 80));
    EditorTheme.apply(scene);
    scene.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ESCAPE) stage.close();
    });
    stage.setScene(scene);
    stage.setMinWidth(Math.min(590, scene.getWidth()));
    stage.setMinHeight(Math.min(390, scene.getHeight()));
    stage.setOnShown(e -> topics.requestFocus());
    stage.showAndWait();
  }

  private static Label label(String text, String style) {
    Label label = new Label(text);
    label.getStyleClass().add(style);
    label.setWrapText(true);
    label.setMaxWidth(Double.MAX_VALUE);
    return label;
  }
}
