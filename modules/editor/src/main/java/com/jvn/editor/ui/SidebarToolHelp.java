package com.jvn.editor.ui;

import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Factory for the standardised sidebar-tool help (?) button. */
public final class SidebarToolHelp {
  private static final double HELP_WIDTH = 420;
  private static final double MAX_VIEWPORT_HEIGHT = 370;

  private SidebarToolHelp() {}

  /**
   * Creates a circular {@code ?} button that opens a non-modal help window when clicked.
   *
   * @param owner the node whose scene/window the help stage should be owned by
   * @param title window title and help topic heading
   * @param body  multi-line help text displayed in the window
   */
  public static Button button(Node owner, String title, String body) {
    Button btn = createHelpButton(title);
    btn.setTooltip(new Tooltip("Open help for " + title));
    btn.setOnAction(e -> {
      Scene ownerScene = owner.getScene();
      openHelp(ownerScene != null ? ownerScene.getWindow() : null,
               ownerScene != null ? ownerScene.getStylesheets() : null,
               title, body);
    });
    return btn;
  }

  /**
   * Creates a {@code ?} button owned by a {@link Window} (e.g. a {@link Stage}) rather than a Node.
   * Use this when the button lives inside a non-Node host such as {@code PuppeteerWindow}.
   */
  public static Button button(Window ownerWindow, String title, String body) {
    Button btn = createHelpButton(title);
    btn.setTooltip(new Tooltip("Open help for " + title));
    btn.setOnAction(e -> {
      Scene ownerScene = ownerWindow != null ? ownerWindow.getScene() : null;
      openHelp(ownerWindow,
               ownerScene != null ? ownerScene.getStylesheets() : null,
               title, body);
    });
    return btn;
  }

  private static Button createHelpButton(String title) {
    Button btn = new Button();
    btn.getStyleClass().addAll("help-button", "aero-help-button", "aero-icon-button");
    btn.setGraphic(AeroIcon.of(AeroIcon.Kind.HELP, 24));
    btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    btn.setMinSize(30, 30);
    btn.setPrefSize(30, 30);
    btn.setMaxSize(30, 30);
    btn.setAccessibleText("Help: " + title);
    btn.setAccessibleHelp("Open help for " + title);
    return btn;
  }

  private static void openHelp(Window ownerWindow,
                                List<String> stylesheets,
                                String title, String body) {
    createHelpWindow(ownerWindow, stylesheets, title, body).show();
  }

  static Stage createHelpWindow(Window ownerWindow, List<String> stylesheets, String title, String body) {
    Stage stage = new Stage();
    stage.initModality(Modality.NONE);
    stage.initOwner(ownerWindow);
    stage.setTitle(title);

    VBox content = HelpText.render(body).view();
    ScrollPane scroll = new ScrollPane(content);
    scroll.getStyleClass().add("editor-help-scroll");
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setPrefViewportWidth(HELP_WIDTH);
    scroll.setAccessibleText(title + " help");

    Button close = new Button("Close");
    close.setDefaultButton(true);
    close.setCancelButton(true);
    close.setOnAction(ev -> stage.close());
    close.getStyleClass().add("sidebar-tool-btn");
    HBox footer = new HBox(close);
    footer.setAlignment(Pos.CENTER_RIGHT);
    footer.getStyleClass().add("editor-help-footer");

    VBox root = new VBox(scroll, footer);
    root.getStyleClass().addAll("editor-help-window", "sidebar-tool-root");
    VBox.setVgrow(scroll, Priority.ALWAYS);

    Scene scene = new Scene(root);
    if (stylesheets != null && !stylesheets.isEmpty()) scene.getStylesheets().addAll(stylesheets);
    else EditorTheme.apply(scene);
    stage.setScene(scene);
    root.applyCss();
    scroll.setPrefViewportHeight(Math.clamp(content.prefHeight(HELP_WIDTH), 48, MAX_VIEWPORT_HEIGHT));
    stage.setMinWidth(280);
    stage.setMinHeight(135);
    stage.sizeToScene();
    return stage;
  }
}
