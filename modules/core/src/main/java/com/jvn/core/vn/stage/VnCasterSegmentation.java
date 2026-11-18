package com.jvn.core.vn.stage;

import com.jvn.core.vn.stage.VnStagePreset.Point;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Traces 4-connected silhouettes from a sprite's alpha channel or a painted grayscale mask. */
public final class VnCasterSegmentation {
  private static final int[] TURN_ORDER = {1, 0, 3, 2};

  private VnCasterSegmentation() {}

  public static List<List<Point>> trace(int width, int height, int[] pixels, int threshold, double tolerancePx) {
    if (width < 1 || height < 1 || (long) width * height != pixels.length) {
      throw new IllegalArgumentException("Mask dimensions do not match its pixels");
    }
    int cutoff = Math.clamp(threshold, 1, 255);
    boolean alpha = false;
    for (int pixel : pixels) {
      if ((pixel >>> 24) < 255) { alpha = true; break; }
    }
    boolean[] foreground = new boolean[pixels.length];
    for (int i = 0; i < pixels.length; i++) {
      int p = pixels[i];
      int coverage = alpha ? p >>> 24 : (54 * ((p >>> 16) & 255) + 183 * ((p >>> 8) & 255) + 19 * (p & 255)) >>> 8;
      foreground[i] = coverage >= cutoff;
    }
    int stride = width + 1;
    byte[] edges = new byte[stride * (height + 1)];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int pixel = y * width + x;
        if (!foreground[pixel]) continue;
        int vertex = y * stride + x;
        if (y == 0 || !foreground[pixel - width]) edges[vertex] |= 1;
        if (x == width - 1 || !foreground[pixel + 1]) edges[vertex + 1] |= 2;
        if (y == height - 1 || !foreground[pixel + width]) edges[vertex + stride + 1] |= 4;
        if (x == 0 || !foreground[pixel - 1]) edges[vertex + stride] |= 8;
      }
    }
    int[] step = {1, stride, -1, -stride};
    List<Contour> contours = new ArrayList<>();
    for (int start = 0; start < edges.length; start++) {
      while (edges[start] != 0) {
        int dir = Integer.numberOfTrailingZeros(edges[start]);
        int vertex = start;
        List<Point> outline = new ArrayList<>();
        double area2 = 0;
        do {
          Point point = new Point(vertex % stride, vertex / stride);
          outline.add(point);
          edges[vertex] &= (byte) ~(1 << dir);
          vertex += step[dir];
          area2 += point.x() * (vertex / stride) - (vertex % stride) * point.y();
          if (vertex == start) break;
          int next = nextDirection(edges[vertex], dir);
          if (next < 0) throw new IllegalStateException("Mask boundary is not closed");
          dir = next;
        } while (true);
        if (area2 < 8) continue;
        List<Point> simplified = simplify(outline, Math.max(0, tolerancePx));
        if (simplified.size() >= 3) contours.add(new Contour(area2, simplified.stream()
            .map(point -> new Point(point.x() / width, point.y() / height)).toList()));
      }
    }
    contours.sort(Comparator.comparingDouble(Contour::area).reversed());
    return contours.stream().map(Contour::points).toList();
  }

  private static int nextDirection(byte edges, int current) {
    for (int turn : TURN_ORDER) {
      int candidate = (current + turn) & 3;
      if ((edges & 1 << candidate) != 0) return candidate;
    }
    return -1;
  }

  private static List<Point> simplify(List<Point> ring, double tolerance) {
    int n = ring.size();
    List<Point> points = new ArrayList<>(ring);
    points.add(points.getFirst());
    boolean[] keep = new boolean[n + 1];
    int opposite = n / 2;
    keep[0] = keep[opposite] = keep[n] = true;
    var sections = new ArrayDeque<int[]>();
    sections.add(new int[]{0, opposite});
    sections.add(new int[]{opposite, n});
    double toleranceSquared = tolerance * tolerance;
    while (!sections.isEmpty()) {
      int[] section = sections.removeLast();
      Point a = points.get(section[0]);
      Point b = points.get(section[1]);
      int furthest = -1;
      double distance = toleranceSquared;
      for (int i = section[0] + 1; i < section[1]; i++) {
        double candidate = distanceSquared(points.get(i), a, b);
        if (candidate > distance) { distance = candidate; furthest = i; }
      }
      if (furthest >= 0) {
        keep[furthest] = true;
        sections.add(new int[]{section[0], furthest});
        sections.add(new int[]{furthest, section[1]});
      }
    }
    List<Point> result = new ArrayList<>();
    for (int i = 0; i < n; i++) if (keep[i]) result.add(points.get(i));
    return result.size() >= 3 ? result : ring;
  }

  private static double distanceSquared(Point p, Point a, Point b) {
    double dx = b.x() - a.x();
    double dy = b.y() - a.y();
    double length = dx * dx + dy * dy;
    double t = length == 0 ? 0 : Math.clamp(((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / length, 0, 1);
    double x = p.x() - a.x() - t * dx;
    double y = p.y() - a.y() - t * dy;
    return x * x + y * y;
  }

  private record Contour(double area, List<Point> points) {}
}
