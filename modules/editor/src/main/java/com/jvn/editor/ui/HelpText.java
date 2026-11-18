package com.jvn.editor.ui;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/** Renders the small Markdown vocabulary used by editor help as native JavaFX text. */
final class HelpText {
  private static final Pattern HEADING = Pattern.compile("^#{1,6}\\h+(.+)$");
  private static final Pattern ITEM = Pattern.compile("^([•*+\\-]|\\d+[.)])\\h+(.+)$");
  private static final Pattern INLINE = Pattern.compile(
      "`([^`]+)`|\\*\\*(?=\\S)([^*]+?)(?<=\\S)\\*\\*|(?<!\\*)\\*(?=\\S)([^*]+?)(?<=\\S)\\*(?!\\*)");
  private static final String FENCE = "```";

  record Rendered(VBox view, String text) {}

  private HelpText() {}

  static Rendered render(String source) {
    VBox view = new VBox(8);
    view.setMinWidth(0);
    view.getStyleClass().add("help-text");
    StringBuilder plainText = new StringBuilder();
    List<String> lines = source == null ? List.of() : source.lines().toList();
    int index = 0;
    while (index < lines.size()) {
      String line = lines.get(index).strip();
      if (line.isEmpty()) {
        index++;
        continue;
      }
      if (!plainText.isEmpty()) plainText.append("\n\n");

      Matcher heading = HEADING.matcher(line);
      if (heading.matches()) {
        view.getChildren().add(inline(heading.group(1), "help-heading", plainText));
        index++;
      } else if (line.startsWith(FENCE)) {
        StringBuilder code = new StringBuilder();
        int firstLine = ++index;
        for (; index < lines.size() && !lines.get(index).strip().equals(FENCE); index++) {
          if (index > firstLine) code.append('\n');
          code.append(lines.get(index));
        }
        index++;
        TextFlow block = new TextFlow(run(code.toString(), "help-code"));
        block.getStyleClass().add("help-code-block");
        view.getChildren().add(block);
        plainText.append(code);
      } else if (ITEM.matcher(line).matches()) {
        VBox list = new VBox(3);
        while (index < lines.size()) {
          Matcher item = ITEM.matcher(lines.get(index).strip());
          if (!item.matches()) break;
          if (!list.getChildren().isEmpty()) plainText.append('\n');
          String marker = item.group(1).length() == 1 ? "•" : item.group(1);
          plainText.append(marker).append(' ');
          Label bullet = new Label(marker);
          bullet.getStyleClass().add("help-list-marker");
          bullet.setMinWidth(Region.USE_PREF_SIZE);
          StringBuilder itemText = new StringBuilder(item.group(2));
          for (index++; index < lines.size(); index++) {
            String continuation = lines.get(index);
            String unindented = continuation.stripLeading();
            if (continuation.equals(unindented) || startsBlock(unindented.stripTrailing())) break;
            itemText.append(' ').append(unindented.stripTrailing());
          }
          TextFlow content = inline(itemText.toString(), "help-paragraph", plainText);
          HBox row = new HBox(7, bullet, content);
          row.getStyleClass().add("help-list-row");
          HBox.setHgrow(content, Priority.ALWAYS);
          list.getChildren().add(row);
        }
        view.getChildren().add(list);
      } else {
        StringBuilder paragraph = new StringBuilder(line);
        for (index++; index < lines.size() && !startsBlock(lines.get(index).strip()); index++) {
          paragraph.append(' ').append(lines.get(index).strip());
        }
        view.getChildren().add(inline(paragraph.toString(), "help-paragraph", plainText));
      }
    }
    return new Rendered(view, plainText.toString());
  }

  private static boolean startsBlock(String line) {
    return line.isEmpty() || line.startsWith(FENCE) || HEADING.matcher(line).matches() || ITEM.matcher(line).matches();
  }

  private static TextFlow inline(String text, String style, StringBuilder plainText) {
    TextFlow flow = new TextFlow();
    flow.getStyleClass().add(style);
    Matcher match = INLINE.matcher(text);
    int end = 0;
    while (match.find()) {
      append(flow, text.substring(end, match.start()), null, plainText);
      if (match.group(1) != null) {
        append(flow, match.group(1), "help-code", plainText);
      } else if (match.group(2) != null) {
        append(flow, match.group(2), "help-strong", plainText);
      } else {
        append(flow, match.group(3), "help-emphasis", plainText);
      }
      end = match.end();
    }
    append(flow, text.substring(end), null, plainText);
    return flow;
  }

  private static void append(TextFlow flow, String text, String style, StringBuilder plainText) {
    if (text.isEmpty()) return;
    flow.getChildren().add(run(text, style));
    plainText.append(text);
  }

  private static Text run(String value, String style) {
    Text text = new Text(value);
    text.getStyleClass().add("help-run");
    if (style != null) text.getStyleClass().add(style);
    return text;
  }
}
