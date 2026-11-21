package com.jvn.editor.ui;

import java.util.ArrayDeque;
import java.util.Deque;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

/** A two-minute, uptime-aligned plot. Gaps in sampling remain visible. */
final class TrashmanHeapChart extends Region {
  private static final long WINDOW_MS = 120_000;
  private static final long GAP_MS = 6_000;
  private record Point(long time, long used, long committed) {}
  private final ReadOnlyStringWrapper windowLabel = new ReadOnlyStringWrapper("10 s ago");
  private long firstSample = -1;
  private long window = 10_000;
  private final Deque<Point> points = new ArrayDeque<>();
  private final Path grid = path("tm-grid");
  private final Path fill = path("tm-fill");
  private final Path used = path("tm-used-line");
  private final Path committed = path("tm-committed-line");
  private final Label high = tick();
  private final Label mid = tick();
  private final Label zero = tick();

  TrashmanHeapChart() {
    getStyleClass().add("tm-chart");
    setMinWidth(0);
    setMinHeight(110);
    setPrefHeight(152);
    zero.setText("0");
    getChildren().addAll(grid, fill, committed, used, high, mid, zero);
    setMouseTransparent(true);
  }

  void add(long time, long usedBytes, long committedBytes) {
    if (firstSample < 0) firstSample = time;
    window = Math.min(WINDOW_MS, Math.max(10_000L, ((time - firstSample + 9_999) / 10_000) * 10_000));
    windowLabel.set(window == WINDOW_MS ? "2 min ago" : (window / 1000) + " s ago");
    if (!points.isEmpty() && points.getLast().time() == time) points.removeLast();
    points.addLast(new Point(time, usedBytes, committedBytes));
    while (points.getFirst().time() < time - WINDOW_MS || points.size() > 512) points.removeFirst();
    requestLayout();
  }

  ReadOnlyStringProperty windowLabel() { return windowLabel.getReadOnlyProperty(); }

  private static Path path(String style) {
    Path path = new Path();
    path.getStyleClass().add(style);
    path.setManaged(false);
    return path;
  }

  private static Label tick() {
    Label label = new Label();
    label.getStyleClass().add("tm-axis");
    label.setManaged(false);
    return label;
  }

  @Override protected void layoutChildren() {
    double left = 6, top = 10, right = Math.max(left, getWidth() - 72), bottom = getHeight() - 10;
    long max = 1;
    for (Point p : points) max = Math.max(max, Math.max(p.used(), p.committed()));
    double mib = 1024.0 * 1024;
    double step = Math.pow(2, Math.ceil(Math.log(max / mib) / Math.log(2)) - 2) * mib;
    double ceiling = Math.max(mib, Math.ceil(max / step) * step);
    high.setText(TrashmanTelemetry.formatBytes((long) ceiling));
    mid.setText(TrashmanTelemetry.formatBytes((long) (ceiling / 2)));
    double center = (bottom + top) / 2;
    placeTick(high, right + 6, top);
    placeTick(mid, right + 6, center);
    placeTick(zero, right + 6, bottom);
    grid.getElements().clear();
    for (int i = 0; i <= 4; i++) {
      double y = top + (bottom - top) * i / 4;
      grid.getElements().addAll(new MoveTo(left, y), new LineTo(right, y));
    }
    used.getElements().clear();
    committed.getElements().clear();
    fill.getElements().clear();
    if (points.isEmpty()) return;
    long start = points.getLast().time() - window;
    Point previous = null;
    double prevX = left;
    for (Point p : points) {
      double x = left + (right - left) * (p.time() - start) / window;
      double y = bottom - (bottom - top) * p.used() / ceiling;
      double c = bottom - (bottom - top) * p.committed() / ceiling;
      if (previous == null || p.time() - previous.time() > GAP_MS) {
        if (previous != null) fill.getElements().addAll(new LineTo(prevX, bottom), new ClosePath());
        // A single sample has a visible endpoint even before a second reading arrives.
        used.getElements().add(new MoveTo(x - 1, y));
        committed.getElements().add(new MoveTo(x - 1, c));
        fill.getElements().add(new MoveTo(x, bottom));
      }
      used.getElements().add(new LineTo(x, y));
      committed.getElements().add(new LineTo(x, c));
      fill.getElements().add(new LineTo(x, y));
      previous = p;
      prevX = x;
    }
    fill.getElements().addAll(new LineTo(prevX, bottom), new ClosePath());
  }

  private void placeTick(Label label, double x, double y) {
    label.autosize();
    label.relocate(x, y - label.getHeight() / 2);
  }
}
