package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class SidebarToolLayoutFxTest {
  @Test
  void storyMapKeepsLabeledActionsInViewAtDockWidthInBothThemes() throws Exception {
    for (String theme : List.of("editor.css", "editor-light.css")) {
      FxToolkit.runFx(() -> {
        StoryTimelineView map = new StoryTimelineView();
        Scene scene = new Scene(map, 280, 680);
        scene.getStylesheets().add(getClass().getResource("/com/jvn/editor/" + theme).toExternalForm());
        map.applyCss();
        map.layout();

        Region header = (Region) map.getTop();
        List<Button> commands = header.lookupAll(".sidebar-tool-btn").stream().map(Button.class::cast).toList();
        assertEquals(10, commands.size());
        for (Button command : commands) {
          assertEquals(ContentDisplay.LEFT, command.getContentDisplay());
          assertTrue(!command.getText().isBlank(), "Story Map command must have a visible label");
          assertTrue(command.localToScene(command.getLayoutBounds()).getMaxX() <= scene.getWidth(),
              command.getText() + " must fit the dock");
        }
        assertTrue(commands.stream().map(button -> button.localToScene(0, 0).getY()).distinct().count() > 1);
        assertTrue(map.getCenter().getLayoutBounds().getHeight() > 300);
        return null;
      });
    }
  }

  @Test
  void storyboardAndStoryMapUseTheSameHeaderInBothThemes() throws Exception {
    for (String theme : List.of("editor.css", "editor-light.css")) {
      FxToolkit.runFx(() -> {
        StoryTimelineView map = new StoryTimelineView();
        StoryboardOverlayView overlay = new StoryboardOverlayView();
        for (Region tool : List.of(overlay, map)) {
          Scene scene = new Scene(tool, 520, 800);
          scene.getStylesheets().add(getClass().getResource("/com/jvn/editor/" + theme).toExternalForm());
          tool.applyCss();
          tool.layout();
        }

        Region mapHeader = (Region) map.getTop();
        Region overlayHeader = (Region) overlay.lookup(".sidebar-tool-header");
        assertEquals(overlayHeader.getInsets(), mapHeader.getInsets());
        assertEquals(overlayHeader.getBackground(), mapHeader.getBackground());
        Label mapTitle = (Label) mapHeader.lookup(".sidebar-tool-title");
        Label overlayTitle = (Label) overlayHeader.lookup(".sidebar-tool-title");
        assertEquals(overlayTitle.getFont(), mapTitle.getFont());
        assertEquals(overlayTitle.getTextFill(), mapTitle.getTextFill());
        return null;
      });
    }
  }
}
