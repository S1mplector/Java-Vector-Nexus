package com.jvn.editor.ui;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class VnsPuppeteerNavigationFxTest {
  @Test void opensContainingTimelineIncludingFoldedBlocksButNotEarlierBlocks() throws Exception {
    FxToolkit.runFx(() -> {
      VnsCodeEditor editor = new VnsCodeEditor();
      String source = "timeline {\n  wait 100\n}\n[say hero Hello]\ntimeline {\n  wait 200\n}\n";
      editor.setText(source);
      int[] line = {-1};
      editor.setOnEditTimelineRequested(value -> line[0] = value);
      editor.getCodeArea().moveTo(source.indexOf("wait 200"));
      assertTrue(editor.editTimelineAtCaret());
      assertEquals(4, line[0]);
      editor.getCodeArea().moveTo(source.indexOf("[say"));
      line[0] = -1;
      assertFalse(editor.editTimelineAtCaret());
      assertEquals(-1, line[0]);
      editor.toggleTimelineFoldAtLineForTest(0);
      editor.getCodeArea().moveTo(0);
      assertTrue(editor.editTimelineAtCaret());
      assertEquals(0, line[0]);
      editor.setOnEditTimelineRequested(null);
      assertFalse(editor.editTimelineAtCaret());
      return null;
    });
  }
}
