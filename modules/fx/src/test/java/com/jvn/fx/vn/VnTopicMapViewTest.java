package com.jvn.fx.vn;

import com.jvn.core.vn.DefaultVnInterop;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class VnTopicMapViewTest {
  @Test
  void mapPansZoomsAndSelectsTheVnsRoute(@TempDir Path project) throws Exception {
    Files.createDirectories(project.resolve("config/ui"));
    Files.createDirectories(project.resolve("config/menu/theme"));
    Files.writeString(project.resolve("config/menu/theme/menu.theme"), """
        aeroGlass=true
        dockTitleFontFamily=Serif
        backgroundColor=#ffffff
        itemColor=#303030
        """);
    Files.writeString(project.resolve("config/ui/topic-map.properties"), """
        map.title=Workshop
        branches=writing,visuals
        writing.title=Writing
        writing.lessons=dialogue,branching
        dialogue.title=Dialogue
        dialogue.completed=done_dialogue
        branching.title=Branching
        visuals.title=Visuals
        visuals.lessons=images
        images.title=Images
        """);
    var scene = new VnScene(new VnScriptParser().parseFromString("""
        @scenario map_example
        @label start
        [set ui.topicMap config/ui/topic-map.properties]
        @label topics
        > Writing -> writing
        > Visuals -> visuals
        @label writing
        > Dialogue -> dialogue
        > Branching -> branching
        > Back -> topics
        @label visuals
        > Images -> images
        > Back -> topics
        @label dialogue
        narrator: Dialogue opened.
        [end]
        @label branching
        narrator: Branching opened.
        [end]
        @label images
        narrator: Images opened.
        [end]
        """));
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();
    assertEquals(VnNodeType.CHOICE, scene.getState().getCurrentNode().getType());

    AtomicInteger selected = new AtomicInteger();
    Stage stage = FxToolkit.runFx(() -> {
      var map = new VnTopicMapView(selected::incrementAndGet);
      var window = new Stage();
      window.setScene(new Scene(new StackPane(map), 900, 540));
      window.show();
      map.sync(scene, project.toFile());
      map.applyCss();
      map.layout();
      return window;
    });
    try {
      FxToolkit.runFx(() -> {
        var map = (VnTopicMapView) ((StackPane) stage.getScene().getRoot()).getChildren().getFirst();
        assertTrue(map.isActive());
        map.applyCss();
        map.layout();
        var scroll = (ScrollPane) map.lookup(".scroll-pane");
        assertTrue(scroll.isPannable());
        double before = scroll.getContent().getBoundsInLocal().getWidth();
        button(map, "+").fire();
        map.layout();
        assertTrue(scroll.getContent().getBoundsInLocal().getWidth() > before);
        double after = scroll.getContent().getBoundsInLocal().getWidth();
        button(map, "+").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "=", "=", KeyCode.EQUALS,
            false, false, false, false));
        assertTrue(scroll.getContent().getBoundsInLocal().getWidth() > after);
        scroll.setHvalue(0.3);
        assertEquals(0.3, scroll.getHvalue(), 0.01);
        button(map, "Fit map").fire();
        var fullscreen = button(map, "Full screen");
        var windows = java.util.List.copyOf(javafx.stage.Window.getWindows());
        fullscreen.fire();
        var expanded = fullscreen.getScene().getWindow();
        assertSame(stage, expanded);
        assertEquals(windows, javafx.stage.Window.getWindows());
        assertTrue(stage.isFullScreen());
        assertEquals("Exit full screen", fullscreen.getText());
        fullscreen.fire();
        assertSame(stage, fullscreen.getScene().getWindow());
        assertFalse(stage.isFullScreen());
        assertEquals("Full screen", fullscreen.getText());
        var card = button(map, "Images");
        assertEquals(javafx.scene.text.Font.font("Serif", 14).getFamily(), card.getFont().getFamily());
        assertEquals(javafx.scene.paint.Color.web("#303030"), card.getTextFill());
        assertInstanceOf(javafx.scene.paint.LinearGradient.class, card.getBackground().getFills().getFirst().getFill());
        var search = (TextField) map.lookup(".topic-map-search");
        var matches = (MenuButton) map.lookup(".topic-map-search-results");
        search.setText("unknown topic");
        assertTrue(matches.isDisabled());
        assertEquals("No matches", matches.getText());
        search.setText("WRITING");
        assertEquals(2, matches.getItems().size());
        search.setText("images visuals");
        assertEquals("Images · Visuals", matches.getItems().getFirst().getText());
        matches.getItems().getFirst().fire();
        assertSame(button(map, "Images"), stage.getScene().getFocusOwner());
        assertTrue(map.isActive());
        button(map, "Images").fire();
        assertEquals(1, selected.get());
        assertFalse(map.isActive());
        assertEquals("Images opened.", scene.getState().getCurrentNode().getDialogue().getText());
      });
    } finally {
      FxToolkit.runFx(stage::close);
    }
  }

  private static Button button(VnTopicMapView view, String label) {
    return view.lookupAll(".button").stream()
        .filter(Button.class::isInstance).map(Button.class::cast)
        .filter(button -> label.equals(button.getText())).findFirst().orElseThrow();
  }
}
