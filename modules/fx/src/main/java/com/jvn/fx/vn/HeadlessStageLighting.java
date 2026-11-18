package com.jvn.fx.vn;

import com.jvn.core.vn.stage.VnCasterSegmentation;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.stage.VnStagePresetLoader;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Renders the gameplay background-lighting pass without starting JavaFX or opening a window. */
public final class HeadlessStageLighting {
  private HeadlessStageLighting() {}

  public static void main(String[] args) throws IOException {
    if (args.length >= 1 && "--segment".equals(args[0])) {
      segment(args);
      return;
    }
    if (args.length < 3 || args.length > 5) {
      throw new IllegalArgumentException("Usage: renderStageLighting <input-image> <stagepreset> <output-png> [silhouettes-png] [shadow-debug-png]");
    }
    Path input = Path.of(args[0]);
    Path preset = Path.of(args[1]);
    VnStagePreset stage;
    try (var stream = Files.newInputStream(preset)) {
      stage = VnStagePresetLoader.load("", preset.toString(), stream);
    }
    BufferedImage source = ImageIO.read(input.toFile());
    if (source == null) throw new IOException("Unsupported input image: " + input);
    int width = source.getWidth();
    int height = source.getHeight();
    var pixels = new VnStageLightingSupport.PixelData(width, height,
        source.getRGB(0, 0, width, height, null, 0, width));
    var output = VnStageLightingSupport.shadeBackground(pixels, stage, width, height);
    BufferedImage rendered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    rendered.setRGB(0, 0, width, height, output.pixels(), 0, width);
    writePng(rendered, Path.of(args[2]));
    if (args.length >= 4) writePng(drawBounds(source, stage), Path.of(args[3]));
    if (args.length == 5) writePng(drawShadows(source, stage), Path.of(args[4]));
  }

  private static void segment(String[] args) throws IOException {
    if (args.length < 3 || args.length > 5) {
      throw new IllegalArgumentException("Usage: --segment <mask-png> <output-stagepreset> [threshold] [tolerance-px]");
    }
    Path input = Path.of(args[1]);
    BufferedImage mask = ImageIO.read(input.toFile());
    if (mask == null) throw new IOException("Unsupported mask image: " + input);
    int width = mask.getWidth();
    int height = mask.getHeight();
    int threshold = args.length >= 4 ? Integer.parseInt(args[3]) : 128;
    double tolerance = args.length >= 5 ? Double.parseDouble(args[4]) : 0.8;
    var contours = VnCasterSegmentation.trace(width, height, mask.getRGB(0, 0, width, height, null, 0, width), threshold, tolerance);
    StringBuilder preset = new StringBuilder("occluders=").append(contours.size()).append('\n');
    for (int i = 0; i < contours.size(); i++) {
      String key = "occluder." + i + ".";
      preset.append(key).append("name=Caster ").append(i + 1).append('\n');
      preset.append(key).append("shape=polygon\n");
      preset.append(key).append("polygon=");
      for (int j = 0; j < contours.get(i).size(); j++) {
        var point = contours.get(i).get(j);
        if (j > 0) preset.append(';');
        preset.append(point.x()).append(',').append(point.y());
      }
      preset.append('\n');
    }
    Files.writeString(Path.of(args[2]), preset.toString());
  }

  private static BufferedImage drawShadows(BufferedImage source, VnStagePreset stage) {
    int width = source.getWidth();
    int height = source.getHeight();
    BufferedImage annotated = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = annotated.createGraphics();
    try {
      g.drawImage(source, 0, 0, null);
      VnStagePreset.Light light = stage.getLights().stream().filter(item -> stage.isActive(item) && item.castsShadows())
          .findFirst().orElse(null);
      if (light == null) return annotated;
      var debug = new VnStageShadowDebug(stage, light, width, height);
      BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      mask.setRGB(0, 0, width, height, debug.mask(), 0, width);
      g.drawImage(mask, 0, 0, null);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setStroke(new BasicStroke(1));
      for (var ray : debug.sampledRays()) {
        g.setColor(ray.blocked() > 0.05 ? new Color(255, 185, 204, 160) : new Color(255, 224, 141, 120));
        g.drawLine((int) ray.sourceX(), (int) ray.sourceY(), (int) ray.x(), (int) ray.y());
      }
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(16, height / 54)));
      g.setColor(new Color(10, 10, 10, 220));
      String legend = light.groundProjection() == null ? "Pink = blocked light · Lines = sample rays"
          : "Pink = floor shadows · Lines = projection rays";
      int legendWidth = Math.max(g.getFontMetrics().stringWidth(light.name()), g.getFontMetrics().stringWidth(legend));
      g.fillRoundRect(22, 22, legendWidth + 28, 76, 9, 9);
      g.setColor(Color.WHITE);
      g.drawString(light.name(), 35, 50);
      g.setColor(new Color(255, 155, 183));
      g.drawString(legend, 35, 79);
      return annotated;
    } finally {
      g.dispose();
    }
  }

  private static BufferedImage drawBounds(BufferedImage source, VnStagePreset stage) {
    int width = source.getWidth();
    int height = source.getHeight();
    BufferedImage annotated = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = annotated.createGraphics();
    try {
      g.drawImage(source, 0, 0, null);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(14, height / 75)));
      for (VnStagePreset.Occluder caster : stage.getOccluders()) {
        if (!caster.enabled() && caster.surfaceTone() != null) continue;
        var outline = caster.polygon();
        int[] xs = new int[outline.size()];
        int[] ys = new int[outline.size()];
        for (int i = 0; i < outline.size(); i++) {
          xs[i] = (int) Math.round(outline.get(i).x() * width);
          ys[i] = (int) Math.round(outline.get(i).y() * height);
        }
        Color color = caster.enabled() ? new Color(103, 219, 255) : new Color(255, 204, 115);
        g.setColor(color);
        g.setStroke(new BasicStroke(2));
        g.drawPolygon(xs, ys, xs.length);
        int left = java.util.Arrays.stream(xs).min().orElse(0);
        int top = java.util.Arrays.stream(ys).min().orElse(0);
        if (caster.shape() == VnStagePreset.OccluderShape.ELLIPSE) {
          g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 4}, 0));
          g.drawRect(left, top, (int) Math.round(caster.bounds().width() * width),
              (int) Math.round(caster.bounds().height() * height));
        }
        String label = caster.name();
        int labelWidth = g.getFontMetrics().stringWidth(label) + 10;
        g.setColor(new Color(0, 0, 0, 210));
        g.fillRoundRect(left, Math.max(0, top - 22), labelWidth, 21, 5, 5);
        g.setColor(color);
        g.drawString(label, left + 5, Math.max(15, top - 6));
      }
    } finally {
      g.dispose();
    }
    return annotated;
  }

  private static void writePng(BufferedImage image, Path destination) throws IOException {
    Path parent = destination.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    if (!ImageIO.write(image, "png", destination.toFile())) throw new IOException("PNG encoder is unavailable");
    System.out.println(destination.toAbsolutePath());
  }
}
