package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.util.List;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class SidebarToolLayoutFxTest {
  @Test
  void workspaceDividersHaveAGripAndInteractiveBevelInBothThemes() throws Exception {
    for (String theme : List.of("editor.css", "editor-light.css")) {
      FxToolkit.runFx(() -> {
        SplitPane workspace = new SplitPane(new Region(), new Region(), new Region());
        workspace.getStyleClass().add("editor-main-split-pane");
        workspace.setDividerPositions(0.2, 0.8);
        Scene scene = new Scene(workspace, 600, 360);
        scene.getStylesheets().add(getClass().getResource("/com/jvn/editor/" + theme).toExternalForm());
        workspace.applyCss();
        workspace.layout();

        var dividers = workspace.lookupAll(".split-pane-divider");
        assertEquals(2, dividers.size());
        for (var divider : dividers) {
          Region rail = (Region) divider;
          Region grip = (Region) rail.lookup(".horizontal-grabber");
          assertEquals(6, rail.getWidth(), 0.01);
          assertTrue(grip.getHeight() >= 20);
          var idlePaint = rail.getBackground();
          rail.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true);
          workspace.applyCss();
          assertNotEquals(idlePaint, rail.getBackground());
        }
        workspace.setDividerPositions(0.3, 0.7);
        workspace.layout();
        assertEquals(0.3, workspace.getDividerPositions()[0], 0.01);
        assertEquals(0.7, workspace.getDividerPositions()[1], 0.01);
        return null;
      });
    }
  }

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
        List<ButtonBase> commands = java.util.stream.Stream.concat(
            header.lookupAll(".sidebar-tool-btn").stream(), header.lookupAll(".menu-button").stream())
            .map(ButtonBase.class::cast).toList();
        assertEquals(List.of("Add Arc", "Add Link", "Connections", "Map tools", "Open", "Run entry"),
            commands.stream().map(ButtonBase::getText).sorted().toList());
        MenuButton tools = commands.stream().filter(command -> command.getText().equals("Map tools"))
            .map(MenuButton.class::cast).findFirst().orElseThrow();
        assertEquals(List.of("Edit", "Copy Goto", "Auto Layout", "Fit", "Validate", "Refresh", "Delete"),
            tools.getItems().stream().map(MenuItem::getText).toList());
        for (ButtonBase command : commands) {
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
