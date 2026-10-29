package com.jvn.fx.vn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** A tiny extruded arrow, projected onto the existing canvas without a 3D scene or textures. */
final class SpinningArrow {
  private static final double[][] OUTLINE = {{-12,-5},{2,-5},{2,-12},{15,0},{2,12},{2,5},{-12,5}};
  private record Face(double[] x, double[] y, double depth, Color color) {}
  private SpinningArrow() {}

  static void draw(GraphicsContext gc, double x, double y, long millis, boolean still) {
    double angle = still ? .4 : millis * Math.PI / 1500;
    double cos = Math.cos(angle), sin = Math.sin(angle);
    double[][] points = new double[14][3];
    for (int i = 0; i < 14; i++) {
      double[] p = OUTLINE[i % 7]; double z = i < 7 ? -3 : 3;
      double rx = p[0] * cos + z * sin, rz = -p[0] * sin + z * cos;
      double perspective = 90 / (90 - rz);
      points[i] = new double[]{x + rx * perspective, y + p[1] * perspective, rz};
    }
    List<Face> faces = new ArrayList<>(9);
    faces.add(face(points, new int[]{0,1,2,3,4,5,6}, Color.web("#d7c5f1").deriveColor(0, 1, .85 + .15 * Math.abs(cos), 1)));
    faces.add(face(points, new int[]{7,8,9,10,11,12,13}, Color.web("#f5edff").deriveColor(0, 1, .85 + .15 * Math.abs(cos), 1)));
    for (int i = 0; i < 7; i++) {
      int next = (i + 1) % 7;
      faces.add(face(points, new int[]{i,next,next+7,i+7}, Color.web("#89749f").deriveColor(0, 1, .8 + .2 * Math.abs(sin), 1)));
    }
    faces.sort(Comparator.comparingDouble(Face::depth));
    gc.save(); gc.setLineWidth(.6); gc.setStroke(Color.web("#776487"));
    for (Face face : faces) { gc.setFill(face.color()); gc.fillPolygon(face.x(), face.y(), face.x().length); gc.strokePolygon(face.x(), face.y(), face.x().length); }
    gc.restore();
  }

  private static Face face(double[][] points, int[] indices, Color color) {
    double[] x = new double[indices.length], y = new double[indices.length]; double depth = 0;
    for (int i = 0; i < indices.length; i++) { double[] point = points[indices[i]]; x[i] = point[0]; y[i] = point[1]; depth += point[2]; }
    return new Face(x, y, depth / indices.length, color);
  }
}
