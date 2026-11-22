package com.jvn.fx;

import com.jvn.branding.EngineBranding;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/** Engine-owned settings access and credit. Not part of project-authored menu layouts. */
public final class RuntimeAttributionOverlay extends StackPane {
  public static final double FOOTER_HEIGHT = 72;
  private static final String SHORTCUT_STYLE =
      "-fx-background-color: #303030; -fx-text-fill: white; -fx-padding: 8 14; -fx-font-size: 13px;";
  private static final String GLASS_SHORTCUT_STYLE =
      "-fx-background-color: linear-gradient(to bottom, #f8fcffe6, #b4d9efcc 48%, #79b4d9bd 50%, #d9f2ffe0);"
          + "-fx-border-color: #668eac; -fx-background-radius: 6px; -fx-border-radius: 6px;"
          + "-fx-text-fill: #183349; -fx-padding: 8 14; -fx-font-size: 13px;";
  private final SimpleDoubleProperty reservedHeight = new SimpleDoubleProperty(0);
  private final HBox credit;
  private final Button settings;

  public RuntimeAttributionOverlay(Runnable openSettings, Runnable closeSettings) {
    setPickOnBounds(false);
    settings = new Button("Settings");
    settings.setId("jvn-runtime-settings");
    settings.setAccessibleText("Open game settings");
    settings.setStyle(SHORTCUT_STYLE);
    var logo = new ImageView(SwingFXUtils.toFXImage(EngineBranding.logo(), null));
    logo.setFitWidth(100); logo.setFitHeight(50); logo.setPreserveRatio(true);
    logo.setAccessibleText("JVN");
    Label prefix = new Label("Made with"); Label version = new Label("(" + EngineBranding.version() + ")");
    for (Label label : new Label[] {prefix, version})
      label.setStyle("-fx-text-fill: white; -fx-font-size: 15px;");
    credit = new HBox(9, prefix, logo, version);
    credit.setId("jvn-settings-credit"); credit.setAlignment(Pos.CENTER);
    credit.setAccessibleText(EngineBranding.accessibleCredit());
    credit.setStyle("-fx-background-color: #202020; -fx-opacity: 1;");
    credit.setMinHeight(FOOTER_HEIGHT); credit.setMaxHeight(FOOTER_HEIGHT);
    credit.setPickOnBounds(true);
    credit.setVisible(false); credit.setManaged(false);
    StackPane.setAlignment(settings, Pos.TOP_RIGHT); StackPane.setMargin(settings, new Insets(8));
    StackPane.setAlignment(credit, Pos.BOTTOM_CENTER);
    settings.setOnAction(event -> { if (credit.isVisible()) closeSettings.run(); else openSettings.run(); });
    getChildren().addAll(credit, settings);
  }
  public void setGlassShortcut(boolean glass) {
    settings.setStyle(glass ? GLASS_SHORTCUT_STYLE : SHORTCUT_STYLE);
  }

  public ReadOnlyDoubleProperty reservedHeightProperty() { return reservedHeight; }
  public void showSettingsCredit(boolean visible) {
    showSettingsCredit(visible, false);
  }

  public void showSettingsCredit(boolean visible, boolean hasDockSettings) {
    credit.setVisible(visible); credit.setManaged(visible);
    reservedHeight.set(visible ? FOOTER_HEIGHT : 0);
    boolean showShortcut = visible || !hasDockSettings;
    settings.setVisible(showShortcut);
    settings.setManaged(showShortcut);
    settings.setText(visible ? "Back" : "Settings");
    settings.setAccessibleText(visible ? "Close game settings" : "Open game settings");
    toFront();
  }
}
