package com.jvn.editor.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/** Brief lifecycle screen shown while a major JVN window finishes its shutdown work. */
public final class ShutdownSplashOverlay {
  private final Stage stage = new Stage(StageStyle.TRANSPARENT);
  private final Label status = new Label("Verifying workspace state...");
  private final Label thanks = new Label("Thank you for choosing JVN.");
  private final ProgressBar progress = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);

  public ShutdownSplashOverlay() {
    this(null);
  }

  public ShutdownSplashOverlay(Stage primaryStage) {
    if (primaryStage != null) stage.getIcons().setAll(primaryStage.getIcons());
    Label title = new Label("Closing JVN");
    title.setTextFill(Color.web("#f4f4f4"));
    title.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 20));
    status.setTextFill(Color.web("#b8b8b8"));
    status.setFont(Font.font("System", FontWeight.BOLD, 12));
    thanks.setTextFill(Color.web("#ff9933"));
    thanks.setFont(Font.font("System", FontWeight.BOLD, 14));
    thanks.setVisible(false);
    thanks.setManaged(false);
    progress.setPrefWidth(300);
    progress.setPrefHeight(5);

    VBox pane = new VBox(9, new MetallicJvnLogo(150, 76), title, status, progress, thanks);
    pane.setAlignment(Pos.CENTER);
    pane.setPadding(new Insets(24, 34, 24, 34));
    pane.setStyle("-fx-background-color: transparent;");
    Scene scene = new Scene(pane, 390, 245, Color.TRANSPARENT);
    scene.setFill(Color.TRANSPARENT);
    stage.setScene(scene);
    stage.setAlwaysOnTop(true);
    stage.setResizable(false);
  }

  public void show(String closingLabel, Runnable verification, Runnable finished) {
    Runnable showTask = () -> {
      stage.setTitle("JVN - Closing");
      status.setText(closingLabel == null || closingLabel.isBlank()
          ? "Verifying workspace state..." : closingLabel.trim());
      stage.show();
      stage.centerOnScreen();
      stage.toFront();

      PauseTransition verifyDelay = new PauseTransition(Duration.millis(180));
      verifyDelay.setOnFinished(event -> {
        try {
          if (verification != null) verification.run();
          status.setText("Everything is safely closed.");
        } catch (RuntimeException error) {
          status.setText("Closed with a cleanup warning.");
        }
        progress.setProgress(1.0);
        thanks.setVisible(true);
        thanks.setManaged(true);
        PauseTransition farewell = new PauseTransition(Duration.millis(850));
        farewell.setOnFinished(done -> {
          stage.hide();
          if (finished != null) finished.run();
        });
        farewell.play();
      });
      verifyDelay.play();
    };
    if (Platform.isFxApplicationThread()) showTask.run(); else Platform.runLater(showTask);
  }
}
