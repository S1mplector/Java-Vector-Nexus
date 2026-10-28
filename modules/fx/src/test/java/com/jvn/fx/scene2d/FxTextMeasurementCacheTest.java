package com.jvn.fx.scene2d;

import static org.junit.jupiter.api.Assertions.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.junit.jupiter.api.Test;

class FxTextMeasurementCacheTest {
  @Test void matchesFreshLayoutAcrossTextAndFontChanges() {
    FxTextMeasurementCache cache = new FxTextMeasurementCache();
    for (String family : new String[] {"Serif", "Monospaced", "Arial"}) {
      for (double size : new double[] {12, 32}) {
        for (boolean bold : new boolean[] {false, true}) {
          for (String value : new String[] {"", "Hello world", "Ågj مرحبا", "two\nlines"}) {
            Text fresh = new Text(value);
            fresh.setFont(Font.font(family, bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
            var actual = cache.measure(value, family, size, bold);
            assertEquals(fresh.getLayoutBounds().getWidth(), actual.width(), 1e-9);
            assertEquals(fresh.getBaselineOffset(), actual.ascent(), 1e-9);
            assertEquals(fresh.getLayoutBounds().getHeight(), actual.lineHeight(), 1e-9);
            assertSame(actual, cache.measure(value, family, size, bold));
          }
        }
      }
    }
  }

  @Test void evictsOldMeasurementsAndDoesNotRetainOversizedLines() {
    FxTextMeasurementCache cache = new FxTextMeasurementCache();
    var first = cache.measure("first", "Arial", 16, false);
    for (int i = 0; i < 600; i++) cache.measure("line " + i, "Arial", 16, false);
    var remeasured = cache.measure("first", "Arial", 16, false);
    assertEquals(first, remeasured);
    assertNotSame(first, remeasured);
    String longLine = "a".repeat(4097);
    assertNotSame(cache.measure(longLine, null, 16, false), cache.measure(longLine, null, 16, false));
    assertSame(cache.measure(null, null, 16, false), cache.measure("", "Arial", 16, false));
  }
}
