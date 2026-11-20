package com.jvn.fx.vn;

import com.jvn.core.vn.text.TextParser;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class DialogueWordWrapTest {
  @Test void wordsKeepTheirFinalLineDuringRevealEvenAcrossStyleSpans() throws Exception {
    FxToolkit.runFx(() -> {
      var renderer = new VnRenderer(new Canvas().getGraphicsContext2D());
      try {
        var font = Font.font("Monospaced", 20);
        Text measure = new Text("12345678");
        measure.setFont(font);
        double width = measure.getLayoutBounds().getWidth() + 0.5;
        var spans = TextParser.parse("Find the {b}of{/b}fice.\nABCDEFGHIJK");
        var full = renderer.layoutStyledLines(spans, 100, width, font, Color.BLACK);
        assertEquals("Find the", text(full.get(0)));
        assertEquals("office.", text(full.get(1)));
        assertEquals("ABCDEFGH", text(full.get(2)));
        assertEquals("IJK", text(full.get(3)));
        var partial = renderer.layoutStyledLines(spans, 10, width, font, Color.BLACK);
        assertEquals("o", text(partial.get(1)));
      } finally { renderer.dispose(); }
    });
  }

  private static String text(VnRenderer.StyledLine line) {
    StringBuilder value = new StringBuilder();
    line.glyphs().forEach(glyph -> value.append(glyph.value()));
    return value.toString();
  }
}
