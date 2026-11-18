package com.jvn.fx.vn;

import java.util.ArrayList;
import java.util.List;

import com.jvn.core.vn.stage.VnStagePreset.Occluder;
import com.jvn.core.vn.stage.VnStagePreset.OccluderShape;

/** Pixel-space occluder with a four-tap penumbra and a bounds check before ray tests. */
public final class VnStageShadow {
  private final OccluderShape shape;

  private final double opacity;
  private final double blur;
  private final double[] xs;
  private final double[] ys;
  private final double left;
  private final double right;
  private final double top;
  private final double bottom;

  private VnStageShadow(Occluder shape, double width, double height) {
    this.shape = shape.shape();
    opacity = shape.opacity();
    blur = shape.softness() * Math.min(width, height) * 0.025;
    xs = new double[shape.polygon().size()];
    ys = new double[xs.length];
    double minX = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < xs.length; i++) {
      xs[i] = shape.polygon().get(i).x() * width;
      ys[i] = shape.polygon().get(i).y() * height;
      minX = Math.min(minX, xs[i]);
      maxX = Math.max(maxX, xs[i]);
      minY = Math.min(minY, ys[i]);
      maxY = Math.max(maxY, ys[i]);
    }
    left = minX;
    right = maxX;
    top = minY;
    bottom = maxY;
  }

  public static List<VnStageShadow> prepare(List<Occluder> shapes, double width, double height) {
    if (shapes.isEmpty()) return List.of();
    List<VnStageShadow> shadows = new ArrayList<>();
    for (Occluder shape : shapes) {
      if (shape.enabled() && shape.opacity() > 0 && shape.polygon() != null && shape.polygon().size() >= 3) {
        shadows.add(new VnStageShadow(shape, width, height));
      }
    }
    return shadows;
  }

  public static double transmission(List<VnStageShadow> shadows, double sx, double sy, double x, double y) {
    return transmission(shadows, sx, sy, x, y, 4);
  }

  static double transmission(List<VnStageShadow> shadows, double sx, double sy, double x, double y, int samples) {
    double blocked = 0;
    for (VnStageShadow shadow : shadows) {
      if (shadow.opacity <= blocked || Math.max(sx, x) < shadow.left - shadow.blur
          || Math.min(sx, x) > shadow.right + shadow.blur || Math.max(sy, y) < shadow.top - shadow.blur
          || Math.min(sy, y) > shadow.bottom + shadow.blur) continue;
      blocked = Math.max(blocked, shadow.coverage(sx, sy, x, y, samples) * shadow.opacity);
      if (blocked >= 1) return 0;
    }
    return 1 - blocked;
  }

  private double coverage(double sx, double sy, double x, double y, int samples) {
    double dx = x - sx;
    double dy = y - sy;
    double length = Math.sqrt(dx * dx + dy * dy);
    if (length <= 1e-6) return 0;
    if (samples <= 1 || blur < 0.5) return crosses(sx, sy, dx, dy) ? 1 : 0;
    double offsetX = -dy * blur / length;
    double offsetY = dx * blur / length;
    int hit = 0;
    for (int i = 0; i < samples; i++) {
      double tap = -1 + 2.0 * i / (samples - 1);
      if (crosses(sx + tap * offsetX, sy + tap * offsetY, dx, dy)) hit++;
    }
    return hit / (double) samples;
  }

  private boolean crosses(double sx, double sy, double dx, double dy) {
    if (shape == OccluderShape.BOX) return crossesBox(sx, sy, dx, dy);
    if (shape == OccluderShape.ELLIPSE) return crossesEllipse(sx, sy, dx, dy);
    boolean sourceInside = false;
    for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
      double ax = xs[j] - sx;
      double ay = ys[j] - sy;
      double bx = xs[i] - sx;
      double by = ys[i] - sy;
      double ex = bx - ax;
      double ey = by - ay;
      if ((ay > 0) != (by > 0) && ax - ex * ay / ey > 0) sourceInside = !sourceInside;
      double cross = dx * ey - dy * ex;
      if (Math.abs(cross) < 1e-9) continue;
      double along = (ax * ey - ay * ex) / cross;
      double edge = (ax * dy - ay * dx) / cross;
      if (along >= 0 && along <= 1 && edge >= 0 && edge <= 1) return true;
    }
    return sourceInside;
  }

  private boolean crossesBox(double sx, double sy, double dx, double dy) {
    if (dx == 0 && (sx < left || sx > right) || dy == 0 && (sy < top || sy > bottom)) return false;
    double tx1 = dx == 0 ? 0 : (left - sx) / dx;
    double tx2 = dx == 0 ? 1 : (right - sx) / dx;
    double ty1 = dy == 0 ? 0 : (top - sy) / dy;
    double ty2 = dy == 0 ? 1 : (bottom - sy) / dy;
    double near = Math.max(0, Math.max(Math.min(tx1, tx2), Math.min(ty1, ty2)));
    double far = Math.min(1, Math.min(Math.max(tx1, tx2), Math.max(ty1, ty2)));
    return near <= far;
  }

  private boolean crossesEllipse(double sx, double sy, double dx, double dy) {
    double rx = (right - left) / 2;
    double ry = (bottom - top) / 2;
    double px = (sx - left - rx) / rx;
    double py = (sy - top - ry) / ry;
    double vx = dx / rx;
    double vy = dy / ry;
    double t = Math.clamp(-(px * vx + py * vy) / (vx * vx + vy * vy), 0, 1);
    double nx = px + t * vx;
    double ny = py + t * vy;
    return nx * nx + ny * ny <= 1;
  }
}
