package com.jvn.editor.ui;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class ScriptEditingKeysFxTest {
  @Test void wrapsSelectionsAndSkipsTheirClosingDelimiter() throws Exception {
    FxToolkit.runFx(() -> {
      var area = new CodeArea("ginger");
      CodeEditingKeys.install(area, "[]\"\"", false);
      area.selectRange(0, 6);
      type(area, "[");
      assertEquals("[ginger]", area.getText());
      assertEquals("ginger", area.getSelectedText());
      area.moveTo(7);
      type(area, "]");
      assertEquals("[ginger]", area.getText());
      assertEquals(8, area.getCaretPosition());
    });
  }

  @Test void enterBetweenBracesIndentsOnceAndKeepsTheClosingBraceAligned() throws Exception {
    FxToolkit.runFx(() -> {
      var area = new CodeArea("  component Sprite2D ");
      CodeEditingKeys.install(area, "{}()\"\"", true);
      area.moveTo(area.getLength());
      type(area, "{");
      Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
      assertEquals("  component Sprite2D {\n    \n  }", area.getText());
      assertEquals(4, area.getCaretColumn());
    });
  }

  private static void type(CodeArea area, String input) {
    Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_TYPED, input, "", KeyCode.UNDEFINED, false, false, false, false));
  }
}
