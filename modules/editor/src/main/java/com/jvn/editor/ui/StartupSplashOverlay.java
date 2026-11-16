package com.jvn.editor.ui;

import com.jvn.editor.AppBuildInfo;
import java.util.ArrayDeque;
import java.util.Deque;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Transparent startup splash used while the editor and launcher perform preflight checks.
 * Displays real startup milestones; detailed diagnostics appear if launch is blocked.
 */
public final class StartupSplashOverlay {
  private static final String SURFACE_TOP = "#232323";
  private static final String SURFACE_BOTTOM = "#171717";
  private static final String BORDER = "#3a3a3a";
  private static final int MAX_LOG_LINES = 80;
  private static final long MIN_VISIBLE_NANOS = 5_000_000_000L;
  private int startupStepCount;

  private final Stage stage = new Stage(StageStyle.TRANSPARENT);
  private final StackPane root = new StackPane();
  private final VBox splashPane = new VBox(4);
  private final VBox failurePane = new VBox(10);
  private final Label versionLabel = new Label();
  private final Label sourceLabel = new Label();
  private final Label failureKickerLabel = new Label("Startup blocked");
  private final Label statusLabel = new Label("Starting JVN...");
  private final Label detailLabel = new Label();
  private final Label loadingStatus = new Label("Starting JVN...");
  private final Label subtitleLabel = new Label("Loading editor environment");
  private final StartupLoadingBar loadingBar = new StartupLoadingBar();
  private javafx.animation.AnimationTimer initialization;
  private final ProgressBar progressBar = new ProgressBar(0);
  private final Button retryButton = createActionButton("Retry", true);
  private final Button quitButton = createActionButton("Quit", false);
  private final HBox actionBar = new HBox(8, retryButton, quitButton);
  private final Deque<String> recentLogLines = new ArrayDeque<>();
  private String progressAccent = "#f38ba8";

  public StartupSplashOverlay() {
    AppBuildInfo.BuildInfo buildInfo = AppBuildInfo.resolve(StartupSplashOverlay.class);
    configureSplashPane(buildInfo);
    configureFailurePane();

    root.setAlignment(Pos.CENTER);
    root.setStyle("-fx-background-color: transparent;");
    root.getChildren().setAll(splashPane, failurePane);
    failurePane.setVisible(false);
    failurePane.setManaged(false);

    Scene scene = new Scene(root, 520, 360, Color.TRANSPARENT);
    scene.setFill(Color.TRANSPARENT);
    stage.setScene(scene);
    stage.setTitle("JVN");
    stage.setResizable(false);
    stage.setAlwaysOnTop(true);
    stage.setOnHidden(event -> {
      loadingBar.stop();
      if (initialization != null) initialization.stop();
    });
  }

  public void show() {
    runOnFx(() -> {
      if (!stage.isShowing()) {
        stage.show();
      }
      loadingBar.start();
      stage.centerOnScreen();
      stage.toFront();
    });
  }

  public void close() {
    runOnFx(() -> {
      loadingBar.stop();
      if (initialization != null) initialization.stop();
      stage.hide();
    });
  }

  public void prepareForChecks(boolean clearLogs) {
    prepareForChecks(clearLogs, 0);
  }

  public static long minimumStepMillis(int totalSteps) {
    return (long) Math.ceil(5_000.0 / Math.max(1, totalSteps));
  }

  public void prepareForChecks(boolean clearLogs, int totalSteps) {
    runOnFx(() -> {
      startupStepCount = Math.max(0, totalSteps);
      if (initialization != null) initialization.stop();
      loadingBar.setProgress(0);
      loadingStatus.setText("Starting JVN...");
      if (stage.isShowing()) loadingBar.start();
      if (clearLogs) {
        recentLogLines.clear();
      }
      splashPane.setVisible(true);
      splashPane.setManaged(true);
      failurePane.setVisible(false);
      failurePane.setManaged(false);
      failureKickerLabel.setText("Startup blocked");
      statusLabel.setText("Starting JVN...");
      statusLabel.setTextFill(Color.web("#f2f2f2"));
      detailLabel.setText("");
      detailLabel.setVisible(false);
      detailLabel.setManaged(false);
      retryButton.setDisable(true);
      quitButton.setDisable(true);
      progressAccent = "#f38ba8";
      styleProgressBar();
    });
  }

  public void setProgress(double progress) {
    runOnFx(() -> {
      loadingBar.setProgress(progress);
      progressBar.setProgress(loadingBar.getProgress());
    });
  }

  public void setStatus(String status) {
    runOnFx(() -> {
      String text = status == null || status.isBlank() ? "Starting JVN..." : status.trim();
      statusLabel.setText(text);
      loadingStatus.setText(text);
      stage.setTitle("JVN - " + text);
    });
  }

  public void setSubtitle(String subtitle) {
    runOnFx(() -> {
      if (subtitle != null && !subtitle.isBlank()) {
        failureKickerLabel.setText(subtitle.trim());
        subtitleLabel.setText(subtitle.trim());
      }
    });
  }

  public void showFailure(String summary, String detail, Runnable onRetry, Runnable onQuit) {
    runOnFx(() -> {
      loadingBar.stop();
      if (initialization != null) initialization.stop();
      splashPane.setVisible(false);
      splashPane.setManaged(false);
      failurePane.setVisible(true);
      failurePane.setManaged(true);
      failureKickerLabel.setText("Startup blocked");
      statusLabel.setText(summary == null || summary.isBlank() ? "Startup checks failed" : summary.trim());
      statusLabel.setTextFill(Color.web("#ff9abb"));
      String message = failureDetail(detail);
      detailLabel.setText(message);
      detailLabel.setVisible(!message.isBlank());
      detailLabel.setManaged(!message.isBlank());
      progressAccent = "#ff8fb6";
      styleProgressBar();
      retryButton.setDisable(onRetry == null);
      quitButton.setDisable(false);
      retryButton.setOnAction(evt -> {
        retryButton.setDisable(true);
        quitButton.setDisable(true);
        if (onRetry != null) onRetry.run();
      });
      quitButton.setOnAction(evt -> {
        if (onQuit != null) onQuit.run();
      });
      stage.centerOnScreen();
      stage.toFront();
    });
  }

  public void showLaunchingEditor() {
    runOnFx(() -> {
      setStatus("Preparing editor window...");
      detailLabel.setText("");
      detailLabel.setVisible(false);
      detailLabel.setManaged(false);
      retryButton.setDisable(true);
      quitButton.setDisable(true);
    });
  }

  public void appendLog(String line) {
    if (line == null || line.isBlank()) return;
    runOnFx(() -> {
      recentLogLines.addLast(line.trim());
      while (recentLogLines.size() > MAX_LOG_LINES) {
        recentLogLines.removeFirst();
      }
    });
  }

  private void configureSplashPane(AppBuildInfo.BuildInfo buildInfo) {
    Node logoView = new MetallicJvnLogo(230, 118);
    versionLabel.setText(buildInfo.versionLabel());
    versionLabel.setTextFill(Color.web("#f4f6f8"));
    versionLabel.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 18));
    versionLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 8, 0.28, 0, 2);");

    sourceLabel.setText(buildInfo.sourceLabel());
    sourceLabel.setTextFill(Color.web("#d5dae0", 0.86));
    sourceLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
    sourceLabel.setVisible(!sourceLabel.getText().isBlank());
    sourceLabel.setManaged(!sourceLabel.getText().isBlank());
    sourceLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.48), 7, 0.24, 0, 2);");

    splashPane.setAlignment(Pos.CENTER);
    splashPane.setPadding(new Insets(18));
    splashPane.setStyle("-fx-background-color: transparent;");
    subtitleLabel.setTextFill(Color.web("#aebdc4"));
    subtitleLabel.setFont(Font.font("System", 12));
    loadingStatus.setTextFill(Color.web("#edf6ed"));
    loadingStatus.setFont(Font.font("System", 12));
    loadingStatus.setWrapText(true);
    loadingStatus.setAlignment(Pos.CENTER);
    loadingStatus.setMinHeight(36);
    loadingStatus.setMaxWidth(400);
    VBox.setMargin(subtitleLabel, new Insets(12, 0, 8, 0));
    VBox.setMargin(loadingStatus, new Insets(6, 0, 0, 0));
    splashPane.setMaxSize(480, 330);
    String textShadow = "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.85), 5, 0.5, 0, 1);";
    subtitleLabel.setStyle(textShadow);
    loadingStatus.setStyle(textShadow);
    splashPane.getChildren().setAll(logoView, versionLabel, sourceLabel, subtitleLabel, loadingBar, loadingStatus);
  }

  private void configureFailurePane() {
    failureKickerLabel.setTextFill(Color.web("#a7afbd"));
    failureKickerLabel.setFont(Font.font("System", FontWeight.BOLD, 12));

    statusLabel.setTextFill(Color.web("#f2f2f2"));
    statusLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
    statusLabel.setWrapText(true);

    detailLabel.setTextFill(Color.web("#d8b5c2"));
    detailLabel.setFont(Font.font("System", 12));
    detailLabel.setWrapText(true);
    detailLabel.setMaxWidth(456);
    detailLabel.setVisible(false);
    detailLabel.setManaged(false);

    progressBar.setMaxWidth(Double.MAX_VALUE);
    progressBar.setPrefHeight(6);
    progressBar.skinProperty().addListener((obs, oldSkin, newSkin) -> styleProgressBar());

    actionBar.setAlignment(Pos.CENTER_RIGHT);
    failurePane.setAlignment(Pos.CENTER_LEFT);
    failurePane.setMaxWidth(500);
    failurePane.setPadding(new Insets(22));
    failurePane.setStyle(
        "-fx-background-color: linear-gradient(to bottom, " + SURFACE_TOP + ", " + SURFACE_BOTTOM + ");"
            + " -fx-background-radius: 14;"
            + " -fx-border-color: " + BORDER + ";"
            + " -fx-border-radius: 14;"
            + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.42), 28, 0.24, 0, 12);");
    failurePane.getChildren().setAll(
        new MetallicJvnLogo(108, 56),
        failureKickerLabel,
        statusLabel,
        detailLabel,
        progressBar,
        actionBar);
  }

  public record InitializationStep(String message, Runnable action) {}

  /** Runs FX-owned initialization in order, allowing each status to paint before its work starts. */
  public void initialize(java.util.List<InitializationStep> steps, Runnable onReady,
                         java.util.function.Consumer<Throwable> onFailure) {
    java.util.List<InitializationStep> sequence = java.util.List.copyOf(steps);
    runOnFx(() -> {
      if (initialization != null) initialization.stop();
      initialization = new javafx.animation.AnimationTimer() {
        private int index;
        private boolean announced;
        private boolean executed;
        private long announcedAt;
        private final long stepDuration = MIN_VISIBLE_NANOS
            / Math.max(1, startupStepCount > 0 ? startupStepCount : sequence.size());
        @Override public void handle(long now) {
          try {
            if (index == sequence.size()) {
              setProgress(1);
              stop();
              onReady.run();
              return;
            }
            InitializationStep step = sequence.get(index);
            if (!announced) {
              setStatus(step.message());
              announced = true;
              announcedAt = System.nanoTime();
              return;
            }
            if (!executed) {
              step.action().run();
              executed = true;
            }
            // Work and its message share this interval; slow work consumes the interval itself.
            if (System.nanoTime() - announcedAt < stepDuration) return;
            index++;
            setProgress(.75 + .25 * index / sequence.size());
            announced = false;
            executed = false;
          } catch (Exception error) {
            stop();
            onFailure.accept(error);
          }
        }
      };
      initialization.start();
    });
  }

  StackPane rootForTest() { return root; }
  double progressForTest() { return loadingBar.getProgress(); }

  private String failureDetail(String detail) {
    if (detail != null && !detail.isBlank()) {
      return detail.trim();
    }
    if (recentLogLines.isEmpty()) {
      return "";
    }
    return recentLogLines.peekLast();
  }

  private void styleProgressBar() {
    Node track = progressBar.lookup(".track");
    if (track instanceof Region region) {
      region.setStyle("-fx-background-color: #111111;"
          + " -fx-background-radius: 3;"
          + " -fx-border-color: #2f2f2f;"
          + " -fx-border-radius: 3;");
    }
    Node bar = progressBar.lookup(".bar");
    if (bar instanceof Region region) {
      region.setStyle("-fx-background-color: " + progressAccent + "; -fx-background-radius: 3;");
    }
  }

  private static Button createActionButton(String label, boolean accent) {
    Button button = new Button(label);
    button.setFocusTraversable(false);
    button.setStyle(
        (accent
            ? "-fx-background-color: linear-gradient(to bottom, #6d2d46, #411a2a);"
            : "-fx-background-color: linear-gradient(to bottom, #2b2b2b, #1c1c1c);")
            + (accent ? " -fx-text-fill: #ffd7e4;" : " -fx-text-fill: #e8e8e8;")
            + (accent ? " -fx-border-color: #ff8fb6;" : " -fx-border-color: #444444;")
            + " -fx-border-radius: 8;"
            + " -fx-background-radius: 8;"
            + " -fx-padding: 7 16 7 16;"
            + " -fx-font-weight: 700;");
    return button;
  }

  private static void runOnFx(Runnable task) {
    if (task == null) return;
    if (Platform.isFxApplicationThread()) {
      task.run();
    } else {
      Platform.runLater(task);
    }
  }
}
