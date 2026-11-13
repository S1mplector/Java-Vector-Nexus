package com.jvn.hub;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.text.html.HTMLEditorKit;

/** Small, offline renderer for the Markdown used by bundled legal documents. */
final class LegalMarkdown {
  private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
  private static final Pattern ITEM = Pattern.compile("^(?:(\\d+)[.)]|([-*+]))\\s+(.+)$");
  private static final Pattern INLINE = Pattern.compile(
      "`([^`]+)`|\\*\\*([^*]+)\\*\\*|__([^_]+)__|\\*([^*]+)\\*|_([^_]+)_");

  private LegalMarkdown() {}

  static JScrollPane view(String source, double scale) {
    JEditorPane pane = new JEditorPane() {
      @Override public boolean getScrollableTracksViewportWidth() { return true; }
    };
    pane.setEditable(false);
    pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
    HTMLEditorKit kit = new HTMLEditorKit();
    var css = kit.getStyleSheet();
    int size = Math.max(10, (int) Math.round(13 * scale));
    css.addRule("body { font-family: sans-serif; font-size: " + size
        + "pt; color: #dedede; background-color: #1c1c1c; margin: " + Math.round(20 * scale) + "px; }");
    css.addRule("h1 { font-size: " + Math.round(23 * scale) + "pt; color: #f2f2f2; margin-top: 0; margin-bottom: 16px; }");
    css.addRule("h2 { font-size: " + Math.round(18 * scale) + "pt; color: #ededed; margin-top: 24px; margin-bottom: 10px; }");
    css.addRule("h3, h4, h5, h6 { font-size: " + Math.round(15 * scale) + "pt; color: #ededed; margin-top: 20px; }");
    css.addRule("p { margin-top: 0; margin-bottom: 12px; }");
    css.addRule("li { margin-bottom: 8px; }");
    css.addRule("code { font-family: monospace; color: #e8cfab; }");
    pane.setEditorKit(kit);
    pane.setText(render(source));
    pane.setCaretPosition(0);
    pane.getAccessibleContext().setAccessibleName("Legal document");
    return new JScrollPane(pane);
  }

  static String render(String source) {
    StringBuilder out = new StringBuilder("<html><body>");
    StringBuilder paragraph = new StringBuilder();
    String list = null;
    boolean item = false;
    for (String raw : source.split("\\R", -1)) {
      String line = raw.strip();
      Matcher heading = HEADING.matcher(line);
      Matcher bullet = ITEM.matcher(line);
      if (line.isEmpty() || heading.matches()) {
        flush(out, paragraph);
        if (item) { out.append("</li>"); item = false; }
        if (list != null) { out.append("</").append(list).append(">"); list = null; }
        if (!line.isEmpty()) {
          int level = heading.group(1).length();
          out.append("<h").append(level).append(">").append(inline(heading.group(2)))
              .append("</h").append(level).append(">");
        }
      } else if (bullet.matches()) {
        flush(out, paragraph);
        String type = bullet.group(1) != null ? "ol" : "ul";
        if (item) out.append("</li>");
        if (!type.equals(list)) {
          if (list != null) out.append("</").append(list).append(">");
          out.append("<").append(type).append(">");
          list = type;
        }
        out.append("<li>").append(inline(bullet.group(3)));
        item = true;
      } else if (item && !raw.equals(raw.stripLeading())) {
        out.append(" ").append(inline(line));
      } else {
        if (item) { out.append("</li>"); item = false; }
        if (list != null) { out.append("</").append(list).append(">"); list = null; }
        if (!paragraph.isEmpty()) paragraph.append(' ');
        paragraph.append(line);
      }
    }
    flush(out, paragraph);
    if (item) out.append("</li>");
    if (list != null) out.append("</").append(list).append(">");
    return out.append("</body></html>").toString();
  }

  private static void flush(StringBuilder out, StringBuilder paragraph) {
    if (paragraph.isEmpty()) return;
    out.append("<p>").append(inline(paragraph.toString())).append("</p>");
    paragraph.setLength(0);
  }

  private static String inline(String value) {
    Matcher matcher = INLINE.matcher(value);
    StringBuilder out = new StringBuilder();
    int last = 0;
    while (matcher.find()) {
      out.append(escape(value.substring(last, matcher.start())));
      int group = 1;
      while (matcher.group(group) == null) group++;
      String tag = group == 1 ? "code" : group <= 3 ? "strong" : "em";
      out.append("<").append(tag).append(">").append(escape(matcher.group(group)))
          .append("</").append(tag).append(">");
      last = matcher.end();
    }
    return out.append(escape(value.substring(last))).toString();
  }

  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
