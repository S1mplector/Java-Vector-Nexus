package com.jvn.fx.vn;

/** Linear-light blend functions for the grading controls. */
public final class VnStageGradeBlend {
  private static final int TABLE_SIZE = 4096;
  private static final double[] TO_LINEAR = new double[TABLE_SIZE + 1];
  private static final double[] TO_SRGB = new double[TABLE_SIZE + 1];

  static {
    for (int i = 0; i <= TABLE_SIZE; i++) {
      double value = i / (double) TABLE_SIZE;
      TO_LINEAR[i] = exactSrgbToLinear(value);
      TO_SRGB[i] = exactLinearToSrgb(value);
    }
  }

  private VnStageGradeBlend() {}

  public static double composite(double base, double zone, double weight, int mode) {
    return composite(base, zone, weight, mode, false);
  }

  static double fastComposite(double base, double zone, double weight, int mode) {
    return composite(base, zone, weight, mode, true);
  }

  private static double composite(double base, double zone, double weight, int mode, boolean sampled) {
    double b = Math.clamp(base, 0.0, 1.0);
    double s = Math.clamp(zone, 0.0, 1.0);
    double w = Math.clamp(weight, 0.0, 1.0);
    if (w <= 0.0) return b;
    if (mode == 0) {
      return b * (1.0 - w) + s * w;
    }

    double bLin = sampled ? srgbToLinear(b) : exactSrgbToLinear(b);
    double sLin = sampled ? srgbToLinear(s) : exactSrgbToLinear(s);
    double blended;
    switch (mode) {
      case 1: // Multiply
        blended = bLin * sLin;
        break;
      case 2: // Screen
        blended = 1.0 - (1.0 - bLin) * (1.0 - sLin);
        break;
      case 3: // Overlay
        blended = bLin < 0.5 ? 2.0 * bLin * sLin : 1.0 - 2.0 * (1.0 - bLin) * (1.0 - sLin);
        break;
      case 4: // Soft Light
        blended = sLin <= 0.5
            ? bLin - (1.0 - 2.0 * sLin) * bLin * (1.0 - bLin)
            : bLin + (2.0 * sLin - 1.0) * (softLightCurve(bLin) - bLin);
        break;
      case 5: // Hard Light
        blended = sLin < 0.5 ? 2.0 * bLin * sLin : 1.0 - 2.0 * (1.0 - bLin) * (1.0 - sLin);
        break;
      case 6: // Color Dodge
        blended = sLin >= (1.0 - 1e-6) ? 1.0 : Math.clamp(bLin / (1.0 - sLin), 0.0, 1.0);
        break;
      case 7: // Color Burn
        blended = sLin <= 1e-6 ? 0.0 : 1.0 - Math.clamp((1.0 - bLin) / sLin, 0.0, 1.0);
        break;
      case 8: // Difference
        blended = Math.abs(bLin - sLin);
        break;
      case 9: // Exclusion
        blended = bLin + sLin - (2.0 * bLin * sLin);
        break;
      case 10: // Lighten
        blended = Math.max(bLin, sLin);
        break;
      case 11: // Darken
        blended = Math.min(bLin, sLin);
        break;
      case 12: // Add
        blended = Math.min(1.0, bLin + sLin);
        break;
      case 13: // Subtract
        blended = Math.max(0.0, bLin - sLin);
        break;
      default: // Normal
        blended = sLin;
        break;
    }
    double c = Math.clamp(blended, 0.0, 1.0);
    double blendedSrgb = sampled ? linearToSrgb(c) : exactLinearToSrgb(c);
    return b * (1.0 - w) + blendedSrgb * w;
  }

  private static double softLightCurve(double b) {
    return b <= 0.25 ? ((16 * b - 12) * b + 4) * b : Math.sqrt(b);
  }

  static double srgbToLinear(double value) { return sample(TO_LINEAR, value); }

  static double linearToSrgb(double value) { return sample(TO_SRGB, value); }

  private static double exactSrgbToLinear(double value) {
    return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
  }

  private static double exactLinearToSrgb(double value) {
    return value <= 0.0031308 ? 12.92 * value : 1.055 * Math.pow(value, 1.0 / 2.4) - 0.055;
  }

  private static double sample(double[] table, double value) {
    if (value <= 0) return 0;
    if (value >= 1) return 1;
    double position = value * TABLE_SIZE;
    int start = (int) position;
    return table[start] + (table[start + 1] - table[start]) * (position - start);
  }

  public static int index(String mode) {
    if (mode == null) return 0;
    return switch (mode.toLowerCase(java.util.Locale.ROOT).strip()) {
      case "multiply" -> 1;
      case "screen" -> 2;
      case "overlay" -> 3;
      case "soft light" -> 4;
      case "hard light" -> 5;
      case "color dodge" -> 6;
      case "color burn" -> 7;
      case "difference" -> 8;
      case "exclusion" -> 9;
      case "lighten" -> 10;
      case "darken" -> 11;
      case "add" -> 12;
      case "subtract" -> 13;
      default -> 0;
    };
  }
}
