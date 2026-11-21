package com.jvn.editor.ui;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class CodeEditorGutterFxTest {
  @ParameterizedTest
  @EnumSource(EditorTheme.Theme.class)
  void firstColumnClearsLineNumbersAfterHorizontalScrolling(EditorTheme.Theme theme) throws Exception {
    EditorTheme.Theme previous = EditorTheme.theme();
    Stage stage = FxToolkit.runFx(() -> {
      EditorTheme.setTheme(theme);
      return new Stage();
    });
    String source = "textBoxX=0.04\ntextBoxY=0.77\n# " + "a".repeat(220) + "\n# line".repeat(80);
    List<Supplier<Parent>> editors = List.of(JavaCodeEditor::new, JesCodeEditor::new,
        VnsCodeEditor::new, TimelineCodeEditor::new);
    try {
      for (var factory : editors) {
        CodeArea area = FxToolkit.runFx(() -> {
          Parent editor = factory.get();
          var code = (CodeArea) editor.lookup(".code-area");
          code.setWrapText(false);
          code.replaceText(source);
          if (editor instanceof JavaCodeEditor layout) {
            layout.useDslHighlighting();
            layout.setDiagnostics(List.of(new JavaCodeEditor.Diagnostic(2, "textBoxY", "Duplicate key", null)));
          }
          code.moveTo(0);
          code.requestFollowCaret();
          Scene scene = new Scene(editor, 540, 350);
          EditorTheme.apply(scene);
          stage.setScene(scene);
          stage.show();
          editor.applyCss();
          editor.layout();
          return code;
        });
        // Flowless publishes the width of the realized paragraphs after layout.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!FxToolkit.runFx(() -> area.getTotalWidthEstimate() > area.getWidth() + 50)) {
          assertTrue(System.nanoTime() < deadline, "The long line should enable horizontal scrolling");
          Thread.sleep(16);
        }
        FxToolkit.runFx(() -> {
          double first = firstColumn(area, 0);
          assertEquals(first, firstColumn(area, 1), 0.5);
          area.scrollXToPixel(38);
          area.layout();
          assertEquals(38, area.getEstimatedScrollX(), 1);
          area.scrollXToPixel(0);
          area.layout();
          assertEquals(first, firstColumn(area, 0), 0.5);
          assertEquals(source, area.getText());
        });
      }
    } finally {
      FxToolkit.runFx(() -> {
        stage.close();
        EditorTheme.setTheme(previous);
      });
    }
  }

  private static double firstColumn(CodeArea area, int line) {
    Node gutter = area.getParagraphGraphic(line).lookup(".lineno");
    double edge = gutter.localToScreen(gutter.getLayoutBounds()).getMaxX();
    int start = area.getAbsolutePosition(line, 0);
    var firstGlyph = area.getCharacterBoundsOnScreen(start, start + 1).orElseThrow();
    assertTrue(firstGlyph.getMinX() >= edge + 3, "First character touches the line number on line " + (line + 1));
    return firstGlyph.getMinX();
  }
}
