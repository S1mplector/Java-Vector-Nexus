package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Live CPU, heap, and FPS history for the editor toolbar. */
public final class EditorPerformanceGraph extends Region {
  public enum Series {
    CPU("#ffbd74"), HEAP("#6bcdf6"), FPS("#8ce381");

    private final Color color;

    Series(String color) {
      this.color = Color.web(color);
    }

    public Circle legendMarker() {
      Circle marker = new Circle(3.25, color);
      marker.getStyleClass().add("editor-perf-marker");
      marker.setMouseTransparent(true);
      return marker;
    }
  }

  private static final int HISTORY_SIZE = 240;
  private static final Color GRID_LINE = Color.web("#383838");
  private static final Color GRID_AXIS = Color.web("#505050");
  private static final Color FACE_SHADOW = Color.web("#141414");
  private static final Color AREA_HIGHLIGHT = Color.rgb(180, 180, 180, 0.11);
  private static final LinearGradient FACE = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
      new Stop(0, Color.web("#2d2d2d")), new Stop(0.18, Color.web("#232323")),
      new Stop(1, Color.web("#1d1d1d")));

  private final Canvas canvas = new Canvas();
  private final double[] cpu = new double[HISTORY_SIZE];
  private final double[] heap = new double[HISTORY_SIZE];
  private final double[] fps = new double[HISTORY_SIZE];
  private int nextSample;
  private int sampleCount;

  public EditorPerformanceGraph() {
    getStyleClass().add("editor-perf-graph");
    setMinSize(160, USE_PREF_SIZE);
    setPrefSize(320, 70);
    setMaxHeight(USE_PREF_SIZE);
    canvas.setManaged(false);
    canvas.setMouseTransparent(true);
    getChildren().add(canvas);

    String description = "Recent CPU and heap use. FPS is plotted as a percentage of the editor's frame-rate target. "
        + "Latest samples are on the right.";
    setAccessibleText(description);
    Tooltip.install(this, EditorTooltip.rich("""
        **Performance history**

        CPU and heap show recent usage. FPS is relative to the editor’s frame-rate target.
        The latest samples appear on the right.
        """));
  }

  public void pushSample(double cpuRatio, double heapRatio, double fpsRatio) {
    cpu[nextSample] = normalize(cpuRatio);
    heap[nextSample] = normalize(heapRatio);
    fps[nextSample] = normalize(fpsRatio);
    nextSample = (nextSample + 1) % HISTORY_SIZE;
    sampleCount = Math.min(sampleCount + 1, HISTORY_SIZE);
    redraw();
  }

  @Override
  protected void layoutChildren() {
    Insets insets = getInsets();
    canvas.relocate(insets.getLeft(), insets.getTop());
    canvas.setWidth(Math.max(0, getWidth() - insets.getLeft() - insets.getRight()));
    canvas.setHeight(Math.max(0, getHeight() - insets.getTop() - insets.getBottom()));
    redraw();
  }

  private void redraw() {
    double width = canvas.getWidth();
    double height = canvas.getHeight();
    if (width < 54 || height < 22) return;
    GraphicsContext g = canvas.getGraphicsContext2D();
    g.setFill(FACE);
    g.fillRect(0, 0, width, height);

    double left = 7.5;
    double right = Math.floor(width) - 7.5;
    double top = 8.5;
    double bottom = Math.floor(height) - 8.5;
    drawGrid(g, left, right, top, bottom);
    if (sampleCount == 0) return;

    double step = (right - left) / (HISTORY_SIZE - 1);
    double start = right - (sampleCount - 1) * step;
    drawTrace(g, fps, Series.FPS.color, start, step, right, top, bottom, true);
    drawTrace(g, heap, Series.HEAP.color, start, step, right, top, bottom, false);
    drawTrace(g, cpu, Series.CPU.color, start, step, right, top, bottom, false);
  }

  private static void drawGrid(GraphicsContext g, double left, double right, double top, double bottom) {
    g.setLineWidth(1);
    g.setLineCap(StrokeLineCap.BUTT);
    g.setStroke(GRID_LINE);
    for (int column = 1; column <= 6; column++) {
      double x = Math.floor(left + (right - left) * column / 6) + 0.5;
      g.strokeLine(x, top, x, bottom);
    }
    for (int row = 0; row <= 4; row++) {
      double y = Math.floor(top + (bottom - top) * row / 4) + 0.5;
      g.setStroke(row % 2 == 0 ? GRID_AXIS : GRID_LINE);
      g.strokeLine(left, y, right, y);
    }
    g.setStroke(GRID_AXIS);
    g.strokeLine(left, top, left, bottom);
  }

  private void drawTrace(GraphicsContext g, double[] samples, Color color,
                         double start, double step, double end, double top, double bottom, boolean fill) {
    int oldest = (nextSample - sampleCount + HISTORY_SIZE) % HISTORY_SIZE;
    double range = bottom - top;
    g.beginPath();
    g.moveTo(start, bottom - samples[oldest] * range);
    for (int offset = 1; offset < sampleCount; offset++) {
      int index = (oldest + offset) % HISTORY_SIZE;
      g.lineTo(start + offset * step, bottom - samples[index] * range);
    }
    g.setLineWidth(1.5);
    g.setLineCap(StrokeLineCap.ROUND);
    g.setLineJoin(StrokeLineJoin.ROUND);
    g.setStroke(color);
    g.stroke();

    if (fill && sampleCount > 1) {
      g.lineTo(end, bottom);
      g.lineTo(start, bottom);
      g.closePath();
      g.setFill(new LinearGradient(0, top, 0, bottom, false, CycleMethod.NO_CYCLE,
          new Stop(0, AREA_HIGHLIGHT),
          new Stop(1, Color.TRANSPARENT)));
      g.fill();
    }

    int latest = (nextSample - 1 + HISTORY_SIZE) % HISTORY_SIZE;
    double tipY = bottom - samples[latest] * range;
    g.setFill(FACE_SHADOW);
    g.fillOval(end - 2.75, tipY - 2.75, 5.5, 5.5);
    g.setFill(color);
    g.fillOval(end - 1.85, tipY - 1.85, 3.7, 3.7);
  }

  private static double normalize(double ratio) {
    return Double.isFinite(ratio) ? Math.clamp(ratio, 0, 1) : 0;
  }
}
