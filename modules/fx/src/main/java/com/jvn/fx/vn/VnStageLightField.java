package com.jvn.fx.vn;

import java.util.List;

import com.jvn.core.vn.stage.VnStagePreset.Light;
import com.jvn.core.vn.stage.VnStagePreset.LightFalloff;
import com.jvn.core.vn.stage.VnStagePreset.LightType;
import com.jvn.core.vn.stage.VnStagePreset.ShadowQuality;

/** A light's pixel-space geometry. Shared by the editor preview and gameplay. */
public final class VnStageLightField {
  private static final int CURVE_STEPS = 256;

  private final LightType type;
  private final double targetX;
  private final double targetY;
  private final double sourceX;
  private final double sourceY;
  private final double radius;
  private final double axisX;
  private final double axisY;
  private final double axisLength;
  private final double width;
  private final double coneRange;
  private final double coneAngle;
  private final double coneCutoff;
  private final double shadowReach;
  private final double areaAspect;
  private final double left;
  private final double right;
  private final double top;
  private final double bottom;
  private final List<double[]> polygon;
  private final double[] curve = new double[CURVE_STEPS + 1];

  public static VnStageLightField forLight(Light light, double width, double height, double scale) {
    double w = width * scale;
    double h = height * scale;
    double radius = Math.max(12, light.radius() * Math.min(width, height));
    return new VnStageLightField(light.type(), light.sceneX() * w, light.sceneY() * h,
        light.sourceX() * w, light.sourceY() * h, radius * scale, light.softness(), light.falloff(),
        light.polygon().stream().map(point -> new double[]{point.x() * w, point.y() * h}).toList(),
        2 * Math.hypot(w, h), light.beamAngle() == 0 ? Math.min(175, 12 + radius * 0.14) : light.beamAngle() * 0.5,
        light.areaAspect());
  }

  double left() { return left; }
  double right() { return right; }
  double top() { return top; }
  double bottom() { return bottom; }

  public VnStageLightField(LightType type, double targetX, double targetY, double sourceX,
      double sourceY, double radius, double softness, LightFalloff falloff, List<double[]> polygon) {
    this(type, targetX, targetY, sourceX, sourceY, radius, softness, falloff, polygon, radius * 8, Math.min(175, 12 + radius * 0.14), 0);
  }

  private VnStageLightField(LightType type, double targetX, double targetY, double sourceX,
      double sourceY, double radius, double softness, LightFalloff falloff, List<double[]> polygon,
      double shadowReach, double halfAngle, double areaAspect) {
    this.areaAspect = areaAspect;
    this.shadowReach = shadowReach;
    this.type = type;
    this.targetX = targetX;
    this.targetY = targetY;
    this.sourceX = sourceX;
    this.sourceY = sourceY;
    this.radius = Math.max(1, radius);
    this.polygon = polygon == null ? List.of() : polygon;
    axisLength = Math.hypot(targetX - sourceX, targetY - sourceY);
    axisX = axisLength == 0 ? 0 : (targetX - sourceX) / axisLength;
    axisY = axisLength == 0 ? 0 : (targetY - sourceY) / axisLength;
    width = Math.max(10, radius * 0.42);
    coneRange = Math.max(axisLength, radius * 1.6);
    coneAngle = Math.toRadians(Math.max(1, halfAngle));
    coneCutoff = Math.cos(coneAngle);

    double minX = targetX;
    double maxX = targetX;
    double minY = targetY;
    double maxY = targetY;
    double margin = this.radius;
    switch (type) {
      case CONE -> {
        minX = maxX = sourceX;
        minY = maxY = sourceY;
        margin = coneRange;
      }
      case STRIP -> {
        minX = Math.min(sourceX, targetX);
        maxX = Math.max(sourceX, targetX);
        minY = Math.min(sourceY, targetY);
        maxY = Math.max(sourceY, targetY);
        margin = width;
      }
      case AMBIENT, DIRECTIONAL -> margin = Double.POSITIVE_INFINITY;
      case POLYGON -> {
        for (double[] point : this.polygon) {
          minX = Math.min(minX, point[0]);
          maxX = Math.max(maxX, point[0]);
          minY = Math.min(minY, point[1]);
          maxY = Math.max(maxY, point[1]);
        }
        margin = this.radius;
      }
      default -> { }
    }
    left = minX - margin;
    right = maxX + margin;
    top = minY - margin;
    bottom = maxY + margin;

    double exponent = switch (type) {
      case CONE -> 0.55 + (1 - softness) * 1.8;
      case STRIP, WINDOW -> 0.65 + (1 - softness) * 2.0;
      case BOUNCE -> 0.65 + (1 - Math.min(softness * 1.15, 1)) * 2.15;
      default -> 0.65 + (1 - softness) * 2.15;
    };
    for (int i = 0; i <= CURVE_STEPS; i++) {
      curve[i] = falloff.weight(i / (double) CURVE_STEPS, exponent);
    }
  }

  public boolean intersects(double x, double y, double w, double h) {
    return x <= right && x + w >= left && y <= bottom && y + h >= top;
  }

  public double weight(double x, double y) {
    if (x < left || x > right || y < top || y > bottom) return 0;
    return switch (type) {
      case POLYGON -> polygonWeight(x, y);
      case CONE -> coneWeight(x, y);
      case STRIP -> stripWeight(x, y);
      case AREA, WINDOW -> areaWeight(x, y, type == LightType.WINDOW);
      case DIRECTIONAL, AMBIENT -> 1.0;
      default -> sample(1 - Math.sqrt(squaredDistance(x - targetX, y - targetY)) / radius);
    };
  }

  public double transmission(List<VnStageShadow> shadows, double x, double y, ShadowQuality quality) {
    if (shadows.isEmpty() || type == LightType.AMBIENT) return 1;
    return VnStageShadow.transmission(shadows, raySourceX(x), raySourceY(y), x, y, quality.samples());
  }

  double raySourceX(double x) {
    return type == LightType.DIRECTIONAL ? x - (axisLength > 0 ? axisX : 0) * shadowReach : sourceX;
  }

  double raySourceY(double y) {
    return type == LightType.DIRECTIONAL ? y - (axisLength > 0 ? axisY : 1) * shadowReach : sourceY;
  }

  private double areaWeight(double x, double y, boolean window) {
    double dx = x - targetX;
    double dy = y - targetY;
    double ax = axisLength > 0 ? axisX : 1;
    double ay = axisLength > 0 ? axisY : 0;
    double a = (dx * ax + dy * ay) / radius;
    double aspect = areaAspect > 0 ? Math.max(0.05, areaAspect) : window ? 0.65 : 0.45;
    double b = (-dx * ay + dy * ax) / (radius * aspect);
    return sample(1 - (window ? Math.max(Math.abs(a), Math.abs(b)) : Math.sqrt(a * a + b * b)));
  }

  private double coneWeight(double x, double y) {
    double dx = x - sourceX;
    double dy = y - sourceY;
    double lengthSquared = squaredDistance(dx, dy);
    if (lengthSquared >= coneRange * coneRange || lengthSquared <= 1e-12 || axisLength <= 1e-6) return 0;
    double distance = Math.sqrt(lengthSquared);
    double facing = (dx * axisX + dy * axisY) / distance;
    if (facing <= coneCutoff) return 0;
    double angle = Math.acos(Math.min(1, facing));
    return sample(1 - angle / coneAngle) * (1 - distance / coneRange);
  }

  private double stripWeight(double x, double y) {
    if (axisLength <= 1e-6) return 0;
    double dx = x - sourceX;
    double dy = y - sourceY;
    double along = Math.clamp(dx * axisX + dy * axisY, 0, axisLength);
    double distanceSquared = squaredDistance(dx - axisX * along, dy - axisY * along);
    if (distanceSquared >= width * width) return 0;
    return sample(1 - Math.sqrt(distanceSquared) / width)
        * Math.min(1, Math.sin(along / axisLength * Math.PI) + 0.18);
  }

  private double polygonWeight(double x, double y) {
    if (polygon.size() < 3) return 0;
    boolean inside = false;
    double closestSquared = Double.POSITIVE_INFINITY;
    for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
      double[] a = polygon.get(j);
      double[] b = polygon.get(i);
      double dx = b[0] - a[0];
      double dy = b[1] - a[1];
      double px = x - a[0];
      double py = y - a[1];
      if ((a[1] > y) != (b[1] > y) && px < dx * py / dy) inside = !inside;
      double lengthSquared = squaredDistance(dx, dy);
      double t = lengthSquared == 0 ? 0 : Math.clamp((px * dx + py * dy) / lengthSquared, 0, 1);
      closestSquared = Math.min(closestSquared, squaredDistance(px - dx * t, py - dy * t));
    }
    double d = Math.sqrt(closestSquared);
    if (inside) return 1 - 0.28 * sample(1 - d / radius);
    return 0.72 * sample(1 - d / radius);
  }

  private double sample(double proximity) {
    if (proximity <= 0) return 0;
    if (proximity >= 1) return 1;
    double index = proximity * CURVE_STEPS;
    int lower = (int) index;
    return curve[lower] + (curve[lower + 1] - curve[lower]) * (index - lower);
  }

  private static double squaredDistance(double x, double y) { return x * x + y * y; }
}
