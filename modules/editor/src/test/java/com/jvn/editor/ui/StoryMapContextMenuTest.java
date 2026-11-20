package com.jvn.editor.ui;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.ContextMenuEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class StoryMapContextMenuTest {
  @Test void switchingProjectsKeepsTheSecondMapsArcsAndFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
      throws Exception {
    var first = java.nio.file.Files.createDirectory(dir.resolve("first"));
    var second = java.nio.file.Files.createDirectory(dir.resolve("second"));
    java.nio.file.Files.writeString(first.resolve("intro.vns"), "@label start");
    java.nio.file.Files.writeString(second.resolve("route.vns"), "@label midway");
    var one = java.nio.file.Files.writeString(first.resolve("one.storymap"), "arc Intro script \"intro.vns\" entry \"start\"");
    String secondMap = "arc Route script \"route.vns\" entry \"midway\"";
    var two = java.nio.file.Files.writeString(second.resolve("two.storymap"), secondMap);
    FxToolkit.runFx(() -> {
      var view = new StoryTimelineView();
      view.setProjectMap(first.toFile(), one.toFile());
      view.setProjectMap(second.toFile(), two.toFile());
      assertEquals(List.of("Route"), view.getArcs().stream().map(a -> a.name).toList());
      assertEquals(secondMap, java.nio.file.Files.readString(two));
      view.setProjectMap(first.toFile(), first.resolve("absent.storymap").toFile());
      assertTrue(view.getArcs().isEmpty());
      return null;
    });
  }

  @Test void openAndPlayDispatchDistinctActionsForClickedArc() throws Exception {
    FxToolkit.runFx(() -> {
      var arc = new StoryTimelineView.Arc();
      arc.name = "Route B";
      arc.script = "scripts/story/route.vns";
      arc.entryLabel = "midway";
      var graph = new StoryGraphPane();
      var opened = new AtomicReference<StoryTimelineView.Arc>();
      var started = new AtomicReference<StoryTimelineView.Arc>();
      graph.setOnOpenArc(opened::set);
      graph.setOnRunArc(started::set);
      graph.setModel(List.of(arc), List.of());
      var stage = new Stage();
      try {
        stage.setScene(new Scene(graph, 800, 500));
        stage.show();
        var card = graph.getChildren().stream().filter(StoryGraphPane.NodeView.class::isInstance)
            .findFirst().orElseThrow();
        card.fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
            40, 40, 150, 150, false, null));
        var menu = Window.getWindows().stream().filter(w -> w instanceof ContextMenu && w.isShowing())
            .map(ContextMenu.class::cast).findFirst().orElseThrow();
        menu.getItems().stream().filter(i -> i.getText().equals("Run from Entry")).findFirst().orElseThrow().fire();
        assertNull(opened.get());
        assertSame(arc, started.get());
        menu.getItems().stream().filter(i -> i.getText().equals("Open")).findFirst().orElseThrow().fire();
        assertSame(arc, opened.get());
        menu.hide();
      } finally { stage.close(); }
      return null;
    });
  }
}
