package com.jvn.pikupiku;

import com.jvn.plugin.api.image.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of the MIT-licensed Pikupiku renderer. Only the contour mask is displaced;
 * source colors and alpha stay fixed. Prepared drawings are deterministic.
 */
public final class PikupikuEffect implements ImageEffect {
  private static ImageEffectParameter p(String id, String label, double value, double low, double high, boolean integer) {
    return new ImageEffectParameter(id, label, value, low, high, integer);
  }
  private static final List<ImageEffectParameter> PARAMETERS = List.of(
      p("strength", "Strength", 1.4, 0, 4, false), p("amplitude", "Displacement (px)", 0.8, 0, 20, false),
      p("fps", "Drawings per second", 6, 1, 60, true), p("frames", "Distinct drawings", 3, 2, 12, true),
      p("ink", "Ink darkness", 0.58, 0, 1, false), p("threshold", "Edge threshold", 32, 1, 255, false),
      p("noiseScale", "Noise spacing (px)", 12, 2, 100, false), p("pressure", "Pencil pressure variation", 0.2, 0, 1, false),
      p("seed", "Pattern seed", 0, 0, 99999, true), p("lineWidth", "Line width", 1, 1, 5, true),
      p("grain", "Paper grain", 0, 0, 10, false), p("simplify", "Color simplification", 0, 0, 1, false),
      p("levels", "Color levels", 5, 2, 16, true), p("smoothing", "Smoothing radius", 0, 0, 5, true),
      p("saturation", "Saturation", 1, 0, 2, false), p("contrast", "Contrast", 1, 0.1, 3, false),
      p("brightness", "Brightness", 0, -100, 100, false), p("paper", "White paper blend", 0, 0, 1, false),
      p("hatch", "Shadow hatching", 0, 0, 1, false), p("inkTone", "Ink shade", 28, 0, 255, true),
      p("pingpong", "Ping-pong loop", 0, 0, 1, true));
  @Override public String label() { return "Pikupiku · Line boil"; }
  @Override public String description() { return "The outline wiggles. The pose, colors, and transparency remain anchored."; }
  @Override public List<ImageEffectParameter> parameters() { return PARAMETERS; }
  @Override public Map<String, Map<String, Double>> presets() {
    var presets = new LinkedHashMap<String, Map<String, Double>>();
    presets.put("Lavender", Map.of());
    presets.put("Subtle", Map.of("strength", 0.6, "ink", 0.35));
    presets.put("Strong", Map.of("strength", 2.0, "frames", 5.0, "lineWidth", 2.0, "ink", 0.7));
    presets.put("Graphite", Map.of("saturation", 0.0, "grain", 1.5, "simplify", 0.32, "smoothing", 2.0));
    presets.put("Omori", Map.of("saturation", 0.0, "paper", 0.65, "hatch", 0.2, "ink", 0.8, "strength", 2.0));
    presets.put("Omori Color", Map.of("paper", 0.2, "hatch", 0.1, "ink", 0.8, "strength", 2.0));
    presets.put("Off", Map.of("strength", 0.0));
    return java.util.Collections.unmodifiableMap(presets);
  }

  @Override public ImageAnimation prepare(ImagePixels source, Map<String, Double> supplied) throws InterruptedException {
    Map<String, Double> v = resolve(supplied);
    int width = source.width(), height = source.height(), count = v.get("frames").intValue();
    int total = v.get("pingpong") == 1 ? count * 2 - 2 : count;
    if ((long) width * height * 4 * total > ImageAnimation.MAX_BYTES)
      throw new IllegalArgumentException("Reduce image size or drawings to stay within 64 MiB");
    double thresholdSquared = v.get("threshold") * v.get("threshold");
    double amplitude = v.get("amplitude"), inkStrength = v.get("ink"), pressureVariation = v.get("pressure");
    double grainAmount = v.get("grain"), hatch = v.get("hatch"), saturation = v.get("saturation");
    double contrast = v.get("contrast"), brightness = v.get("brightness"), paper = v.get("paper"), inkTone = v.get("inkTone");
    int[] input = source.pixels(), flat = input.clone();
    int radius = v.get("smoothing").intValue(), levels = v.get("levels").intValue();
    double simplify = v.get("simplify"), strength = v.get("strength");
    if (radius > 0 || simplify > 0) {
      for (int y = 0; y < height; y++) {
        checkCancelled();
        for (int x = 0; x < width; x++) {
          int at = y * width + x;
          if ((input[at] >>> 24) == 0) continue;
          double[] sums = new double[3]; double weights = 0;
          for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++) {
            int pixel = input[clamp(y + dy, height) * width + clamp(x + dx, width)];
            double weight = (pixel >>> 24) / 255.0 / (1 + Math.abs(luma(pixel) - luma(input[at])) * 0.05);
            for (int c = 0; c < 3; c++) sums[c] += weight * channel(pixel, c);
            weights += weight;
          }
          int pixel = input[at] & 0xff000000;
          for (int c = 0; c < 3; c++) {
            double value = weights == 0 ? channel(input[at], c) : sums[c] / weights;
            double step = 255.0 / (levels - 1);
            pixel |= byteValue((1 - simplify) * value + simplify * Math.round(value / step) * step) << (16 - c * 8);
          }
          flat[at] = pixel;
        }
      }
    }
    float[] edges = new float[input.length];
    for (int y = 0; y < height; y++) {
      checkCancelled();
      for (int x = 0; x < width; x++) {
        double dx = paperLuma(flat[y * width + clamp(x + 1, width)]) - paperLuma(flat[y * width + clamp(x - 1, width)]);
        double dy = paperLuma(flat[clamp(y + 1, height) * width + x]) - paperLuma(flat[clamp(y - 1, height) * width + x]);
        edges[y * width + x] = dx * dx + dy * dy > thresholdSquared ? 1 : 0;
      }
    }
    int lineWidth = v.get("lineWidth").intValue();
    if (lineWidth > 1) {
      float[] wide = edges.clone(); int low = -(lineWidth / 2);
      for (int y = 0; y < height; y++) {
        checkCancelled();
        for (int x = 0; x < width; x++) for (int dy = low; dy < low + lineWidth; dy++) for (int dx = low; dx < low + lineWidth; dx++)
          wide[y * width + x] = Math.max(wide[y * width + x], edges[clamp(y + dy, height) * width + clamp(x + dx, width)]);
      }
      edges = wide;
    }
    List<ImagePixels> frames = new ArrayList<>(); int seed = v.get("seed").intValue();
    double mix = Math.min(1, strength), scale = v.get("noiseScale");
    for (int frame = 0; frame < count; frame++) {
      int[] output = new int[input.length];
      for (int y = 0; y < height; y++) {
        checkCancelled();
        for (int x = 0; x < width; x++) {
          int at = y * width + x, alpha = input[at] & 0xff000000;
          if (alpha == 0) continue;
          double sx = x + strength * amplitude * smoothNoise(x / scale, y / scale, 71 + frame * 103 + seed);
          double sy = y + strength * amplitude * smoothNoise(x / scale, y / scale, 193 + frame * 107 + seed);
          double ink = sample(edges, width, height, sx, sy);
          double pressure = Math.min(1, strength * inkStrength * (1 + pressureVariation * smoothNoise(x / 8.0, y / 8.0, 313 + frame * 29 + seed)));
          double grain = grainAmount == 0 ? 0 : strength * grainAmount * noise(x, y, 41 + seed);
          double gray = (1 - mix) * luma(input[at]) + mix * luma(flat[at]);
          double shadow = Math.max(0, (170 - gray) / 170);
          double pencil = hatch == 0 ? 0 : Math.max(0, 1 - Math.abs(Math.IEEEremainder(sx + sy, 7)));
          if (hatch != 0 && gray < 80) pencil = Math.max(pencil, Math.max(0, 1 - Math.abs(Math.IEEEremainder(sx - sy, 9))));
          double coverage = Math.max(ink * pressure, Math.min(1, hatch * strength * shadow * pencil));
          int pixel = alpha;
          for (int c = 0; c < 3; c++) {
            double base = (1 - mix) * channel(input[at], c) + mix * channel(flat[at], c);
            base = gray + (base - gray) * saturation;
            base = (base - 127.5) * contrast + 127.5 + brightness;
            base = base * (1 - paper * mix) + 255 * paper * mix;
            pixel |= byteValue(base * (1 - coverage) + inkTone * coverage + grain) << (16 - c * 8);
          }
          output[at] = pixel;
        }
      }
      frames.add(new ImagePixels(width, height, output));
    }
    if (total > count) for (int i = count - 2; i > 0; i--) frames.add(frames.get(i));
    return new ImageAnimation(frames, v.get("fps"));
  }
  private static void checkCancelled() throws InterruptedException { if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Image preparation cancelled"); }
  private static int channel(int pixel, int c) { return (pixel >>> (16 - c * 8)) & 255; }
  private static double luma(int p) { return channel(p, 0) * 0.299 + channel(p, 1) * 0.587 + channel(p, 2) * 0.114; }
  private static double paperLuma(int p) { double alpha = (p >>> 24) / 255.0; return luma(p) * alpha + 255 * (1 - alpha); }
  private static int byteValue(double value) { return (int) Math.max(0, Math.min(255, Math.round(value))); }
  private static int clamp(int value, int extent) { return Math.max(0, Math.min(extent - 1, value)); }
  private static double noise(int x, int y, int seed) {
    int n = x * 374761393 + y * 668265263 + seed * 1442695041;
    n = (n ^ (n >>> 13)) * 1274126177;
    return ((n ^ (n >>> 16)) & 65535) / 32767.5 - 1;
  }
  private static double smoothNoise(double x, double y, int seed) {
    int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
    double fx = x - ix, fy = y - iy; fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
    return (1 - fy) * ((1 - fx) * noise(ix, iy, seed) + fx * noise(ix + 1, iy, seed))
        + fy * ((1 - fx) * noise(ix, iy + 1, seed) + fx * noise(ix + 1, iy + 1, seed));
  }
  private static double sample(float[] pixels, int width, int height, double x, double y) {
    int ix = (int) Math.floor(x), iy = (int) Math.floor(y); double fx = x - ix, fy = y - iy;
    double result = 0;
    for (int dy = 0; dy <= 1; dy++) for (int dx = 0; dx <= 1; dx++)
      result += (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy) * pixels[clamp(iy + dy, height) * width + clamp(ix + dx, width)];
    return result;
  }
}
