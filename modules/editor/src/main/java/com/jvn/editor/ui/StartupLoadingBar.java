package com.jvn.editor.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/** Glossy, segmented Aero-style progress with a moving light, not simulated progress. */
final class StartupLoadingBar extends Region {
  private final Canvas canvas = new Canvas();
  private double progress;
  private double phase;
  private final AnimationTimer animation = new AnimationTimer() {
    @Override public void handle(long now) {
      phase = (now % 2_000_000_000L) / 2_000_000_000.0;
      draw();
    }
  };

  StartupLoadingBar() {
    getChildren().add(canvas);
    setMinSize(100, 20);
    setPrefSize(320, 20);
    setMaxSize(320, 20);
    setAccessibleRole(javafx.scene.AccessibleRole.PROGRESS_INDICATOR);
  }

  void setProgress(double value) {
    progress = !Double.isFinite(value) || value < 0 ? -1 : Math.min(1, value);
    setAccessibleText(progress < 0 ? "Loading" : "Loading " + Math.round(progress * 100) + " percent");
    draw();
  }

  double getProgress() { return progress; }
  void start() { animation.start(); }
  void stop() { animation.stop(); }

  @Override protected void layoutChildren() {
    canvas.setWidth(getWidth());
    canvas.setHeight(getHeight());
    draw();
  }

  private void draw() {
    double w = canvas.getWidth(), h = canvas.getHeight();
    if (w <= 4 || h <= 4) return;
    GraphicsContext g = canvas.getGraphicsContext2D();
    g.clearRect(0, 0, w, h);
    g.setFill(Color.web("#101817"));
    g.fillRoundRect(0, 0, w, h, 7, 7);
    g.setStroke(Color.web("#778b88"));
    g.strokeRoundRect(.5, .5, w - 1, h - 1, 7, 7);
    double inner = w - 4;
    double fill = progress < 0 ? inner * .27 : inner * progress;
    double x = progress < 0 ? 2 + (inner - fill) * phase : 2;
    g.save();
    g.beginPath();
    g.rect(x, 2, fill, h - 4);
    g.clip();
    g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#d6ffb0")), new Stop(.45, Color.web("#72d84c")),
        new Stop(.5, Color.web("#31a52d")), new Stop(1, Color.web("#55ce37"))));
    g.fillRect(x, 2, fill, h - 4);
    g.setStroke(Color.web("#155b25", .25));
    for (double segment = 10; segment < w; segment += 10) g.strokeLine(segment, 2, segment, h - 2);
    double light = (inner + 100) * phase - 100;
    g.setFill(new LinearGradient(light, 0, light + 90, 0, false, CycleMethod.NO_CYCLE,
        new Stop(0, Color.TRANSPARENT), new Stop(.5, Color.web("#ecffce", .7)),
        new Stop(1, Color.TRANSPARENT)));
    g.fillRect(light, 2, 90, h - 4);
    g.restore();
  }
}
