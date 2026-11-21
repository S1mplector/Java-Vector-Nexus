package com.jvn.fx.vn;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.vn.ui.FacetCodeExcerpt;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/** A read-only source view. File reads, highlighting and text measurement occur on changes only. */
final class FacetCodeBlock {
  private static final String FONT_FAMILY = List.of("Menlo", "Consolas", "DejaVu Sans Mono").stream()
      .filter(Font.getFamilies()::contains).findFirst().orElse("Monospaced");
  private static final Color PAPER = Color.web("#232323");
  private static final Color CHROME = Color.web("#303030");
  private static final Color RULE = Color.web("#535353");
  private static final Color TEXT = Color.web("#ececec");
  private static final Color MUTED = Color.web("#adadad");
  private static final Color COMMAND = Color.web("#b5d3ec");
  private static final Color STRING = Color.web("#c3e88d");
  private static final Color NUMBER = Color.web("#f5ab91");
  private static final Color NAME = Color.web("#ffcc77");

  private record Run(String text, Color color, double x, double baseline) {}
  private record Layout(Font font, double header, double height, double inset, List<Run> runs) {}

  private String source;
  private String region;
  private FacetCodeExcerpt excerpt;
  private String error;
  private double lastWidth, lastHeight;
  private Layout layout;

  void draw(GraphicsContext gc, AssetCatalog assets, String path, String selector,
            double x, double y, double w, double h) {
    if (path.isBlank()) return;
    if (!Objects.equals(source, path) || !Objects.equals(region, selector)) {
      source = path;
      region = selector;
      layout = null;
      try {
        excerpt = FacetCodeExcerpt.load(assets, path, selector);
        error = null;
      } catch (IOException ex) {
        excerpt = null;
        error = "Source unavailable";
        org.slf4j.LoggerFactory.getLogger(FacetCodeBlock.class)
            .warn("Could not display {} region {}: {}", path, selector, ex.getMessage());
      }
    }
    if (layout == null || w != lastWidth || h != lastHeight) {
      lastWidth = w;
      lastHeight = h;
      layout = arrange(w, h);
    }
    double top = y + (h - layout.height()) / 2;
    gc.save();
    gc.translate(x, top);
    gc.beginPath();
    gc.rect(0, 0, w, layout.height());
    gc.clip();
    gc.setTextBaseline(VPos.BASELINE);
    gc.setTextAlign(TextAlignment.LEFT);
    gc.setFill(PAPER);
    gc.fillRect(0, 0, w, layout.height());
    gc.setFill(CHROME);
    gc.fillRect(0, 0, w, layout.header());
    gc.setFill(RULE);
    gc.fillRect(0, layout.header() - 1, w, 1);
    gc.setFont(layout.font());
    gc.setFill(TEXT);
    String filename = excerpt == null ? path.substring(path.lastIndexOf('/') + 1) : excerpt.filename();
    gc.fillText(filename, layout.inset(), layout.header() * 0.66);
    for (Run run : layout.runs()) {
      gc.setFill(run.color());
      gc.fillText(run.text(), run.x(), run.baseline());
    }
    gc.restore();
  }

  private Layout arrange(double w, double h) {
    double size = Math.max(8, Math.min(24, Math.min(w / 37, h / 12)));
    Font font = Font.font(FONT_FAMILY, size);
    Text measure = new Text("0");
    measure.setFont(font);
    double cell = measure.getLayoutBounds().getWidth();
    double inset = size * 1.1;
    double header = size * 2.25;
    double leading = size * 1.55;
    List<Run> runs = new ArrayList<>();
    if (excerpt == null) {
      runs.add(new Run(error, MUTED, inset, header + leading));
      return new Layout(font, header, Math.min(h, header + leading + inset), inset, runs);
    }
    int digits = Integer.toString(excerpt.firstLine() + excerpt.lines().size()).length();
    double bodyX = inset + (digits + 2) * cell;
    int columns = Math.max(1, (int) ((w - bodyX - inset) / cell));
    int maxRows = Math.max(1, (int) ((h - header - inset) / leading));
    List<String> lines = excerpt.lines().stream().map(line -> line.replace("\t", "    ")).toList();
    String code = String.join("\n", lines);
    Color[] colors = new Color[code.length()];
    excerpt.syntax().scan(code, (from, to, style) -> Arrays.fill(colors, from, to, ink(style)));
    int lineStart = 0;
    int row = 0;
    for (int i = 0; i < excerpt.lines().size(); i++) {
      String line = lines.get(i);
      int lineNumber = excerpt.firstLine() + i;
      // A wrapped row keeps the source line's number only on its first segment.
      int offset = 0;
      do {
        int to = Math.min(line.length(), offset + columns);
        if (to < line.length()) {
          int space = line.lastIndexOf(' ', to - 1);
          if (space > offset && !line.substring(offset, space).isBlank()) to = space + 1;
        }
        double baseline = header + (row + 1) * leading;
        if (row == maxRows - 1 && (i + 1 < excerpt.lines().size() || to < line.length())) {
          runs.add(new Run("…", MUTED, bodyX, baseline));
          return new Layout(font, header, h, inset, runs);
        }
        if (offset == 0) {
          String label = Integer.toString(lineNumber);
          runs.add(new Run(label, MUTED, inset + (digits - label.length()) * cell, baseline));
        }
        int start = offset;
        while (start < to) {
          Color color = colors[lineStart + start];
          int end = start + 1;
          while (end < to && colors[lineStart + end] == color) end++;
          runs.add(new Run(line.substring(start, end), color, bodyX + (start - offset) * cell, baseline));
          start = end;
        }
        row++;
        offset = to;
      } while (offset < line.length());
      lineStart += line.length() + 1;
    }
    return new Layout(font, header, Math.min(h, header + Math.max(1, row) * leading + inset), inset, runs);
  }

  // Classes and colors follow the graphite palette in editor-code.css and editor.css.
  private static Color ink(String style) {
    return switch (style) {
      case "keyword", "vns-directive", "vns-command", "prop-key", "dsl-action",
          "jes-struct", "jes-action" -> COMMAND;
      case "string", "vns-choice" -> STRING;
      case "number", "vns-arrow", "vns-value", "dsl-bool", "jes-bool" -> NUMBER;
      case "vns-format", "vns-variable", "vns-option", "jes-prop", "vns-speaker", "dsl-section", "jes-comptype" -> NAME;
      case "comment" -> MUTED;
      default -> TEXT;
    };
  }
}
