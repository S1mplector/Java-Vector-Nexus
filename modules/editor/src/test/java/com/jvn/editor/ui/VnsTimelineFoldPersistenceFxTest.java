package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.util.List;
import java.util.concurrent.Callable;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class VnsTimelineFoldPersistenceFxTest {

  private static final String TWO_TIMELINE_SCRIPT =
      "@label start\n"
          + "timeline {\n"
          + "  move \"hero\" {\n"
          + "    x: 100\n"
          + "    dur: 200\n"
          + "  }\n"
          + "}\n"
          + "\n"
          + "[say \"hero\" \"Hi\"]\n"
          + "\n"
          + "timeline {\n"
          + "  move \"hero\" {\n"
          + "    x: 0\n"
          + "    dur: 100\n"
          + "  }\n"
          + "}\n";

  @Test
  void foldedTimelineHeaderReopensWithoutEditingTheScript() throws Exception {
    Stage stage = runFx(() -> {
      VnsCodeEditor editor = new VnsCodeEditor();
      editor.setText(TWO_TIMELINE_SCRIPT);
      Stage window = new Stage();
      Scene scene = new Scene(editor, 640, 480);
      EditorTheme.apply(scene);
      window.setScene(scene);
      window.show();
      editor.toggleTimelineFoldAtLineForTest(1);
      return window;
    });
    try {
      runFx(() -> {
        var editor = (VnsCodeEditor) stage.getScene().getRoot();
        editor.applyCss();
        editor.layout();
        var area = (CodeArea) editor.lookup(".code-area");
        assertEquals(1, editor.exportFoldedTimelineBlocks().size());
        org.junit.jupiter.api.Assertions.assertNull(area.getParagraphGraphicFactory().apply(2));
        int start = area.getAbsolutePosition(1, 0);
        var glyph = area.getCharacterBoundsOnScreen(start, start + 1).orElseThrow();
        var point = area.getScene().getRoot().screenToLocal(glyph.getCenterX(), glyph.getCenterY());
        Event.fireEvent(area, new MouseEvent(MouseEvent.MOUSE_CLICKED,
            point.getX(), point.getY(), glyph.getCenterX(), glyph.getCenterY(),
            MouseButton.PRIMARY, 1, false, false, false, false, false, false, false,
            false, false, true, new PickResult(area, point.getX(), point.getY())));
        assertTrue(editor.exportFoldedTimelineBlocks().isEmpty());
        assertEquals(TWO_TIMELINE_SCRIPT, area.getText());
        return null;
      });
    } finally {
      FxToolkit.runFx(stage::close);
    }
  }

  @Test
  void restoreFoldedTimelineBlocksReappliesExportedFoldState() throws Exception {
    runFx(() -> {
      VnsCodeEditor original = new VnsCodeEditor();
      original.setText(TWO_TIMELINE_SCRIPT);
      original.toggleTimelineFoldAtLineForTest(10); // second timeline block (0-based line)

      List<VnsFoldStateStore.FoldedBlockKey> exported = original.exportFoldedTimelineBlocks();
      assertEquals(1, exported.size());

      VnsCodeEditor reopened = new VnsCodeEditor();
      reopened.setText(TWO_TIMELINE_SCRIPT);
      reopened.restoreFoldedTimelineBlocks(exported);

      assertEquals(exported, reopened.exportFoldedTimelineBlocks());
      return null;
    });
  }

  @Test
  void restoreFoldedTimelineBlocksSurvivesLinesShiftingAboveTheFoldedBlock() throws Exception {
    runFx(() -> {
      VnsCodeEditor original = new VnsCodeEditor();
      original.setText(TWO_TIMELINE_SCRIPT);
      original.toggleTimelineFoldAtLineForTest(10); // second timeline block
      List<VnsFoldStateStore.FoldedBlockKey> exported = original.exportFoldedTimelineBlocks();

      String shifted = "@label start\n\n\n" + TWO_TIMELINE_SCRIPT.substring("@label start\n".length());
      VnsCodeEditor reopened = new VnsCodeEditor();
      reopened.setText(shifted);
      reopened.restoreFoldedTimelineBlocks(exported);

      List<VnsCodeEditor.TimelineOutlineEntry> entries = reopened.computeTimelineOutlineEntries();
      assertEquals(2, entries.size());
      assertEquals(1, reopened.exportFoldedTimelineBlocks().size());
      // the first timeline block must remain untouched (unrelated block not folded)
      assertEquals(exported, reopened.exportFoldedTimelineBlocks());
      return null;
    });
  }

  @Test
  void restoreFoldedTimelineBlocksDropsEntriesForBlocksThatNoLongerMatch() throws Exception {
    runFx(() -> {
      VnsCodeEditor editor = new VnsCodeEditor();
      editor.setText("@label start\n[say \"hero\" \"Hi\"]\n");

      List<VnsFoldStateStore.FoldedBlockKey> stale =
          List.of(new VnsFoldStateStore.FoldedBlockKey(0, "does-not-match"));
      editor.restoreFoldedTimelineBlocks(stale);

      assertTrue(editor.exportFoldedTimelineBlocks().isEmpty());
      return null;
    });
  }

  private static <T> T runFx(Callable<T> callable) throws Exception {
    return FxToolkit.runFx(callable);
  }
}
