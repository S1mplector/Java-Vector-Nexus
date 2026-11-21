package com.jvn.editor.ui;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class EditorTheme {
  public enum Theme {
    DARK,
    LIGHT
  }

  private static final String DARK_CSS_PATH = "/com/jvn/editor/editor.css";
  private static final String LIGHT_CSS_PATH = "/com/jvn/editor/editor-light.css";
  private static final String THEME_PROPERTY = "jvn.editor.theme";
  private static final String THEME_ENV = "JVN_EDITOR_THEME";

  private static Theme currentTheme = resolveInitialTheme();
  private static String cachedCssPath;
  private static String cachedCssUrl;

  static {
    javafx.stage.Window.getWindows().addListener((javafx.collections.ListChangeListener<javafx.stage.Window>) c -> {
      while (c.next()) {
        if (c.wasAdded()) {
          for (javafx.stage.Window window : c.getAddedSubList()) {
            if (window instanceof javafx.stage.PopupWindow && window.getClass().getName().contains("Tooltip")) {
              window.setOpacity(0.0);
              javafx.application.Platform.runLater(() -> {
                javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, new javafx.animation.KeyValue(window.opacityProperty(), 0.0)),
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(150), new javafx.animation.KeyValue(window.opacityProperty(), 1.0))
                );
                timeline.play();
              });
            }
          }
        }
      }
    });
  }

  private EditorTheme() {}

  private static Theme resolveInitialTheme() {
    String value = System.getProperty(THEME_PROPERTY);
    if (value == null || value.isBlank()) {
      value = System.getenv(THEME_ENV);
    }
    return parseTheme(value);
  }

  private static Theme parseTheme(String value) {
    if (value == null || value.isBlank()) return Theme.DARK;
    String normalized = value.trim().toLowerCase();
    return normalized.equals("light") ? Theme.LIGHT : Theme.DARK;
  }

  private static String cssPath(Theme theme) {
    return theme == Theme.LIGHT ? LIGHT_CSS_PATH : DARK_CSS_PATH;
  }

  private static boolean isThemeStylesheet(String stylesheet) {
    if (stylesheet == null || stylesheet.isBlank()) return false;
    return stylesheet.contains(DARK_CSS_PATH) || stylesheet.contains(LIGHT_CSS_PATH);
  }

  private static String cssUrl(Theme theme) {
    String path = cssPath(theme);
    if (cachedCssUrl != null && path.equals(cachedCssPath)) return cachedCssUrl;
    var url = EditorTheme.class.getResource(path);
    cachedCssPath = path;
    cachedCssUrl = (url == null) ? "" : url.toExternalForm();
    return cachedCssUrl;
  }

  public static String stylesheetUrl() {
    return cssUrl(currentTheme);
  }

  public static Theme theme() {
    return currentTheme;
  }

  public static void setTheme(Theme theme) {
    Theme resolved = theme == null ? Theme.DARK : theme;
    if (resolved == currentTheme) return;
    currentTheme = resolved;
    cachedCssPath = null;
    cachedCssUrl = null;
  }

  public static void setTheme(String themeName) {
    setTheme(parseTheme(themeName));
  }

  // Keep the filter on the icon wrapper so hover effects on the artwork stay monochrome.
  static Node graphiteGraphic(Node artwork) {
    StackPane graphic = new StackPane(artwork);
    graphic.setEffect(new ColorAdjust(0, -1, 0, 0));
    graphic.setMouseTransparent(true);
    return graphic;
  }

  public static void apply(Scene scene) {
    if (scene == null) return;
    boolean graphite = scene.getRoot() instanceof RunConsoleView || scene.getRoot() instanceof GameBuildPublisherView;
    String css = cssUrl(graphite ? Theme.DARK : currentTheme);
    if (css.isEmpty()) return;
    scene.getStylesheets().removeIf(EditorTheme::isThemeStylesheet);
    if (!scene.getStylesheets().contains(css)) scene.getStylesheets().add(css);
  }

  public static void apply(Stage stage) {
    if (stage == null) return;
    apply(stage.getScene());
  }

  public static <T extends Dialog<?>> T apply(T dialog) {
    if (dialog == null) return null;
    DialogPane pane = dialog.getDialogPane();
    String css = cssUrl(currentTheme);
    if (!css.isEmpty()) {
      pane.getStylesheets().removeIf(EditorTheme::isThemeStylesheet);
      if (!pane.getStylesheets().contains(css)) pane.getStylesheets().add(css);
    }
    if (!pane.getStyleClass().contains("jvn-dialog")) pane.getStyleClass().add("jvn-dialog");
    return dialog;
  }
}
