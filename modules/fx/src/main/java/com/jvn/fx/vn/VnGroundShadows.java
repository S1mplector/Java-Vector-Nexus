package com.jvn.fx.vn;

import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.stage.VnStagePreset.Light;
import com.jvn.core.vn.stage.VnStagePreset.Occluder;
import com.jvn.core.vn.stage.VnStagePreset.Point;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/** Perspective projection of painted silhouettes from a point light onto the floor. */
public final class VnGroundShadows {
  private VnGroundShadows() {}

  public static List<Point> project(Light light, Occluder sprite) { return project(light, sprite, 0); }

  private static List<Point> project(Light light, Occluder sprite, double depthOffset) {
    List<Point> points = projectRaw(light, sprite, depthOffset);
    var framing = sprite.shadowFraming();
    if (framing == null || points.isEmpty()) return points;
    double centerX = (points.stream().mapToDouble(Point::x).min().orElse(0)
        + points.stream().mapToDouble(Point::x).max().orElse(0)) / 2;
    double centerY = (points.stream().mapToDouble(Point::y).min().orElse(0)
        + points.stream().mapToDouble(Point::y).max().orElse(0)) / 2;
    double aspect = 16.0 / 9.0;
    double sin = Math.sin(Math.toRadians(framing.angle()));
    double cos = Math.cos(Math.toRadians(framing.angle()));
    return points.stream().map(point -> {
      double x = (point.x() - centerX) * aspect * framing.scaleX();
      double y = (point.y() - centerY) * framing.scaleY();
      return new Point(centerX + framing.offsetX() + (x * cos - y * sin) / aspect,
          centerY + framing.offsetY() + x * sin + y * cos);
    }).toList();
  }

  private static List<Point> projectRaw(Light light, Occluder sprite, double depthOffset) {
    var plane = light.groundProjection();
    if (plane == null || sprite.polygon().size() < 3 || sprite.heightScale() <= 0) return List.of();
    double contact = sprite.contactY() >= 0 ? sprite.contactY()
        : sprite.polygon().stream().mapToDouble(Point::y).max().orElse(plane.horizon());
    if (contact <= plane.horizon() + 0.001) return List.of();
    if (sprite.roundFootprint()) return projectRound(light, sprite, contact);
    double lightDepth = 1 / (plane.groundY() - plane.horizon());
    double lightX = (plane.groundX() - 0.5) * lightDepth;
    double lightHeight = Math.max(0.02, 1 - (light.sourceY() - plane.horizon()) * lightDepth);
    List<Point> result = new ArrayList<>(sprite.polygon().size());
    for (int i = 0; i < sprite.polygon().size(); i++) {
      Point vertex = sprite.polygon().get(i);
      double feet = sprite.floorContacts().isEmpty() ? contact : sprite.floorContacts().get(i);
      double spriteDepth = 1 / Math.max(0.01, feet + depthOffset - plane.horizon());
      double worldX = (vertex.x() - 0.5) * spriteDepth;
      double worldHeight = Math.clamp((feet - vertex.y()) * spriteDepth * sprite.heightScale(), 0, lightHeight * 0.92);
      double factor = lightHeight / (lightHeight - worldHeight);
      double x = lightX + (worldX - lightX) * factor;
      double depth = Math.max(0.10, lightDepth + (spriteDepth - lightDepth) * factor);
      result.add(new Point(0.5 + x / depth, plane.horizon() + 1 / depth));
    }
    return sprite.floorContacts().isEmpty() ? result : convexHull(result);
  }

  private static List<Point> convexHull(List<Point> points) {
    List<Point> ordered = points.stream().sorted(java.util.Comparator.comparingDouble(Point::x).thenComparingDouble(Point::y)).toList();
    if (ordered.size() < 3) return ordered;
    List<Point> lower = halfHull(ordered);
    List<Point> upper = halfHull(ordered.reversed());
    lower.removeLast();
    upper.removeLast();
    lower.addAll(upper);
    return lower;
  }

  private static List<Point> halfHull(List<Point> order) {
    List<Point> result = new ArrayList<>();
    for (Point point : order) {
      while (result.size() >= 2) {
        Point a = result.get(result.size() - 2);
        Point b = result.getLast();
        double cross = (b.x() - a.x()) * (point.y() - a.y()) - (b.y() - a.y()) * (point.x() - a.x());
        if (cross > 0) break;
        result.removeLast();
      }
      result.add(point);
    }
    return result;
  }

  private static List<Point> projectRound(Light light, Occluder caster, double contact) {
    var plane = light.groundProjection();
    double aspect = 16.0 / 9.0;
    double minX = 1, minY = 1, maxX = 0, maxY = 0;
    for (Point point : caster.polygon()) {
      minX = Math.min(minX, point.x());
      minY = Math.min(minY, point.y());
      maxX = Math.max(maxX, point.x());
      maxY = Math.max(maxY, point.y());
    }
    double depth = 1 / (contact - plane.horizon());
    double radius = Math.min((maxX - minX) * aspect, maxY - minY) * 0.5 * depth * caster.heightScale();
    if (radius <= 0) return List.of();
    Vec lightWorld = new Vec((plane.groundX() - 0.5) * aspect / (plane.groundY() - plane.horizon()),
        1 - (light.sourceY() - plane.horizon()) / (plane.groundY() - plane.horizon()),
        1 / (plane.groundY() - plane.horizon()));
    Vec center = new Vec(((minX + maxX) * 0.5 - 0.5) * aspect * depth,
        Math.max(radius, (contact - (minY + maxY) * 0.5) * depth * caster.heightScale()), depth);
    Vec delta = center.subtract(lightWorld);
    double distance = delta.length();
    if (distance <= radius || lightWorld.y <= center.y + radius) return List.of();
    Vec axis = delta.scale(1 / distance);
    Vec u = axis.cross(Math.abs(axis.y) < 0.99 ? new Vec(0, 1, 0) : new Vec(1, 0, 0));
    u = u.scale(1 / u.length());
    Vec v = axis.cross(u);
    Vec tangentCenter = center.subtract(axis.scale(radius * radius / distance));
    double tangentRadius = radius * Math.sqrt(1 - radius * radius / (distance * distance));
    List<Point> projection = new ArrayList<>(40);
    for (int i = 0; i < 40; i++) {
      double t = i * Math.PI * 2 / 40;
      Vec tangent = tangentCenter.add(u.scale(Math.cos(t) * tangentRadius)).add(v.scale(Math.sin(t) * tangentRadius));
      double factor = lightWorld.y / (lightWorld.y - tangent.y);
      Vec floor = lightWorld.add(tangent.subtract(lightWorld).scale(factor));
      double visibleDepth = Math.max(0.1, floor.z);
      projection.add(new Point(0.5 + floor.x / (visibleDepth * aspect), plane.horizon() + 1 / visibleDepth));
    }
    return projection;
  }

  private record Vec(double x, double y, double z) {
    Vec add(Vec other) { return new Vec(x + other.x, y + other.y, z + other.z); }
    Vec subtract(Vec other) { return new Vec(x - other.x, y - other.y, z - other.z); }
    Vec scale(double factor) { return new Vec(x * factor, y * factor, z * factor); }
    double length() { return Math.sqrt(x * x + y * y + z * z); }
    Vec cross(Vec other) { return new Vec(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x); }
  }

  public static byte[] mask(VnStagePreset preset, int width, int height) {
    return mask(preset, width, height, null);
  }

  static byte[] mask(VnStagePreset stage, int width, int height, Light selected) {
    List<Light> lights = stage.getLights().stream().filter(light -> (selected == null || light == selected) && stage.isActive(light)
        && light.castsShadows() && light.groundProjection() != null).toList();
    if (lights.isEmpty() || stage.getOccluders().isEmpty()) return null;
    byte[] coverage = new byte[width * height];
    byte[] silhouette = new byte[width * height];
    for (Occluder caster : stage.getOccluders()) fill(caster.polygon(), silhouette, width, height, 255);
    byte[] layer = new byte[width * height];
    for (Light light : lights) {
      for (Occluder caster : stage.getOccluders()) {
        if (Thread.currentThread().isInterrupted()) return null;
        if (!caster.enabled() || caster.opacity() <= 0) continue;
        List<Point> projection = project(light, caster);
        if (projection.size() < 3) continue;
        int opacity = (int) Math.round(caster.opacity() * light.groundProjection().strength() * 255);
        int blur = Math.min(12, (int) Math.round(caster.softness() * Math.min(width, height) * 0.018));
        boolean soften = light.shadowQuality().samples() > 1 && blur > 0;
        boolean filter = soften || caster.shadowFade() > 0;
        if (filter) Arrays.fill(layer, (byte) 0);
        byte[] destination = filter ? layer : coverage;
        fill(projection, destination, width, height, opacity);
        if (caster.footprintDepth() > 0 && !caster.roundFootprint() && caster.floorContacts().isEmpty()) {
          double depth = caster.footprintDepth() * 0.5;
          List<Point> near = project(light, caster, depth);
          List<Point> far = project(light, caster, -depth);
          fill(near, destination, width, height, opacity);
          fill(far, destination, width, height, opacity);
          for (int i = 0, j = near.size() - 1; i < near.size(); j = i++) {
            fill(List.of(near.get(j), near.get(i), far.get(i), far.get(j)), destination, width, height, opacity);
          }
        }
        if (filter) {
          double anchorX = caster.polygon().stream().mapToDouble(Point::x).average().orElse(0.5) * width;
          double anchorY = (caster.contactY() >= 0 ? caster.contactY()
              : caster.polygon().stream().mapToDouble(Point::y).max().orElse(1)) * height;
          double reach = projection.stream().mapToDouble(point -> Math.hypot(point.x() * width - anchorX,
              point.y() * height - anchorY)).max().orElse(1);
          blendSoft(coverage, layer, width, height, soften ? blur : 0, anchorX, anchorY, reach, caster.shadowFade());
        }
      }
    }
    for (int i = 0; i < silhouette.length; i++) {
      if (silhouette[i] != 0) coverage[i] = 0;
    }
    return coverage;
  }

  static int[] materialMap(VnStagePreset stage, int width, int height, boolean includeUngraded) {
    if (!includeUngraded && stage.getOccluders().stream().noneMatch(caster -> caster.surfaceTone() != null)) return null;
    int[] map = new int[width * height];
    for (int i = 0; i < stage.getOccluders().size(); i++) {
      Occluder caster = stage.getOccluders().get(i);
      if (!includeUngraded && caster.surfaceTone() == null) continue;
      int index = i + 1;
      spans(caster.polygon(), width, height, (offset, left, right) -> Arrays.fill(map, offset + left, offset + right, index));
    }
    return map;
  }

  record FloorLight(double x, double depth, double height, double horizon, double amount, double exposure) {
    static FloorLight from(Light light, double aspect) {
      var plane = light.groundProjection();
      double depth = 1 / (plane.groundY() - plane.horizon());
      double height = Math.max(0.02, 1 - (light.sourceY() - plane.horizon()) * depth);
      return new FloorLight((plane.groundX() - 0.5) * depth * aspect, depth, height,
          plane.horizon(), plane.floorLight(), Math.pow(2, plane.floorExposure()));
    }

    double illumination(double u, double depth, double aspect) {
      double dx = (u - 0.5) * depth * aspect - x;
      double dz = depth - this.depth;
      double relative = (dx * dx + dz * dz) / (height * height) + 1;
      return amount / (relative * Math.sqrt(relative));
    }
  }

  private static void fill(List<Point> polygon, byte[] image, int width, int height, int opacity) {
    if (opacity <= 0) return;
    spans(polygon, width, height, (offset, left, right) -> {
      for (int x = left; x < right; x++) {
        int index = offset + x;
        if ((image[index] & 255) < opacity) image[index] = (byte) opacity;
      }
    });
  }

  @FunctionalInterface
  private interface Span { void fill(int offset, int left, int right); }

  private static void spans(List<Point> polygon, int width, int height, Span span) {
    if (polygon.size() < 3) return;
    double minY = 1;
    double maxY = 0;
    for (Point p : polygon) { minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y()); }
    int start = Math.max(0, (int) Math.floor(minY * height));
    int end = Math.min(height, (int) Math.ceil(maxY * height));
    double[] intersections = new double[polygon.size()];
    for (int y = start; y < end; y++) {
      double py = (y + 0.5) / height;
      int n = 0;
      for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
        Point a = polygon.get(j);
        Point b = polygon.get(i);
        if ((a.y() > py) != (b.y() > py)) {
          intersections[n++] = a.x() + (b.x() - a.x()) * (py - a.y()) / (b.y() - a.y());
        }
      }
      Arrays.sort(intersections, 0, n);
      for (int i = 0; i + 1 < n; i += 2) {
        int left = Math.max(0, (int) Math.ceil(intersections[i] * width - 0.5));
        int right = Math.min(width, (int) Math.ceil(intersections[i + 1] * width - 0.5));
        if (left < right) span.fill(y * width, left, right);
      }
    }
  }

  private static void blendSoft(byte[] output, byte[] layer, int width, int height, int radius,
      double anchorX, double anchorY, double reach, double fade) {
    int[] vertical = new int[width];
    int window = radius * 2 + 1;
    for (int y = 0; y <= radius && y < height; y++) {
      for (int x = 0; x < width; x++) vertical[x] += layer[y * width + x] & 255;
    }
    for (int y = 0; y < height; y++) {
      int sum = 0;
      for (int x = 0; x <= radius && x < width; x++) sum += vertical[x];
      for (int x = 0; x < width; x++) {
        int index = y * width + x;
        int value = sum / (window * window);
        if (fade > 0 && value > 0) {
          double progress = Math.clamp((Math.hypot(x - anchorX, y - anchorY) / Math.max(1, reach) - 0.37) / 0.63, 0, 1);
          value = (int) Math.round(value * (1 - fade * progress * progress * (3 - 2 * progress)));
        }
        if ((output[index] & 255) < value) output[index] = (byte) value;
        if (x - radius >= 0) sum -= vertical[x - radius];
        if (x + radius + 1 < width) sum += vertical[x + radius + 1];
      }
      int drop = y - radius;
      int add = y + radius + 1;
      if (drop >= 0) for (int x = 0; x < width; x++) vertical[x] -= layer[drop * width + x] & 255;
      if (add < height) for (int x = 0; x < width; x++) vertical[x] += layer[add * width + x] & 255;
    }
  }
}
