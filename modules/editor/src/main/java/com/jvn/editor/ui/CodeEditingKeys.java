package com.jvn.editor.ui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.CodeArea;

/** Character pairs are inserted with one document edit so undo restores the selection's text. */
final class CodeEditingKeys {
  static void install(CodeArea area, String pairs, boolean indentBlocks) {
    area.addEventFilter(KeyEvent.KEY_TYPED, event -> {
      String typed = event.getCharacter();
      if (event.isConsumed() || !area.isEditable() || event.isShortcutDown() || event.isAltDown() || typed.length() != 1) return;
      char c = typed.charAt(0);
      int index = pairs.indexOf(c);
      if (index < 0) return;
      int start = area.getSelection().getStart();
      int end = area.getSelection().getEnd();
      String text = area.getText();
      if (c == '"' && escaped(text, start)) return;
      if (start == end && end < text.length() && text.charAt(end) == c && (index % 2 == 1 || c == '"')) {
        area.moveTo(end + 1);
      } else if (index % 2 == 0) {
        if (start == end && CodeAutoCompleter.openQuote(text, start) >= 0) return;
        char closer = pairs.charAt(index + 1);
        if (start == end && end < text.length() && text.charAt(end) == closer) return;
        String selected = area.getSelectedText();
        area.replaceText(start, end, c + selected + closer);
        area.selectRange(start + 1, start + 1 + selected.length());
      } else return;
      event.consume();
    });
    if (!indentBlocks) return;
    area.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.isConsumed() || !area.isEditable() || event.getCode() != KeyCode.ENTER || event.isShortcutDown()
          || event.isAltDown() || area.getSelection().getLength() != 0) return;
      int caret = area.getCaretPosition();
      String text = area.getText();
      String before = text.substring(text.lastIndexOf('\n', caret - 1) + 1, caret);
      String indent = before.substring(0, before.length() - before.stripLeading().length());
      boolean block = before.stripTrailing().endsWith("{") && CodeAutoCompleter.openQuote(text, caret) < 0;
      String inner = "\n" + indent + (block ? "  " : "");
      boolean betweenBraces = block && caret < text.length() && text.charAt(caret) == '}';
      area.insertText(caret, inner + (betweenBraces ? "\n" + indent : ""));
      area.moveTo(caret + inner.length());
      event.consume();
    });
  }

  private static boolean escaped(String text, int offset) {
    int start = offset;
    while (start > 0 && text.charAt(start - 1) == '\\') start--;
    return (offset - start) % 2 == 1;
  }
}
