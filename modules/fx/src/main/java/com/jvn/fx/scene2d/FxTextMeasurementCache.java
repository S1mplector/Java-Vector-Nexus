package com.jvn.fx.scene2d;

import com.jvn.core.scene2d.TextFontMetrics2D;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/** Render-thread-only, bounded text measurements shared by width and layout queries. */
final class FxTextMeasurementCache {
  private final Text probe = new Text();
  private final Map<FontKey, Font> fonts = boundedMap(64);
  private final Map<MeasurementKey, TextFontMetrics2D> measurements = boundedMap(512);

  Font font(String family, double size, boolean bold) {
    FontKey key = new FontKey(family == null || family.isBlank() ? "Arial" : family, size, bold);
    return fonts.computeIfAbsent(key,
        k -> Font.font(k.family(), k.bold() ? FontWeight.BOLD : FontWeight.NORMAL, k.size()));
  }

  TextFontMetrics2D measure(String text, String family, double size, boolean bold) {
    String value = text == null ? "" : text;
    Font font = font(family, size, bold);
    MeasurementKey key = new MeasurementKey(value, font);
    TextFontMetrics2D cached = measurements.get(key);
    if (cached != null) return cached;
    probe.setText(value);
    probe.setFont(font);
    javafx.geometry.Bounds bounds = probe.getLayoutBounds();
    double ascent = Math.max(0.0, probe.getBaselineOffset());
    TextFontMetrics2D result = new TextFontMetrics2D(
        bounds.getWidth(), ascent, Math.max(0.0, bounds.getHeight() - ascent), 0.0);
    // Bound retained text as well as entry count (scripts may contain very long lines).
    if (value.length() <= 4096) measurements.put(key, result);
    return result;
  }

  private static <K, V> Map<K, V> boundedMap(int limit) {
    return new LinkedHashMap<>(16, 0.75f, true) {
      @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > limit;
      }
    };
  }

  private record FontKey(String family, double size, boolean bold) {}
  private record MeasurementKey(String text, Font font) {}
}
