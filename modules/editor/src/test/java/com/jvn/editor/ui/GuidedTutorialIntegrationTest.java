package com.jvn.editor.ui;

import com.jvn.core.assets.*;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.vn.*;
import com.jvn.fx.menu.MenuRenderer;
import com.jvn.fx.menu.MenuTheme;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import com.jvn.fx.vn.VnRenderer;
import com.jvn.scripting.jes.JesLoader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class GuidedTutorialIntegrationTest {
  @TempDir Path project;

  @Test void generatedCoursePlaysEachLessonAndCountsReplaysOnce() throws Exception {
    FxToolkit.runFx(() -> {
      NewProjectWizard wizard = new NewProjectWizard(new Stage());
      try {
        ((ComboBox<?>) field(wizard, "cmbProjectTemplate")).getSelectionModel().select(1);
        invoke(wizard, "applySelectedProjectTemplate", new Class<?>[]{});
        assertTrue(((CheckBox) field(wizard, "chkTutorialPack")).isSelected());
        ((CheckBox) field(wizard, "chkGitInit")).setSelected(false);
        invoke(wizard, "createProjectStructure", new Class<?>[]{java.io.File.class, String.class}, project.toFile(), "JVN Tutorial");
      } finally { wizard.close(); }
      return null;
    });
    assertFalse(Files.exists(project.resolve("assets/demo/backgrounds/game.png")));
    assertFalse(Files.exists(project.resolve("assets/tutorial/lofi-music-2.mp3")));
    for (String image : List.of("icon", "tutorial/wave", "tutorial/neutral", "tutorial/curious", "tutorial/point",
        "tutorial/surprised", "tutorial/listen", "tutorial/cheer", "tutorial/notes", "tutorial/present", "tutorial/stride-a-left", "tutorial/stride-b-left",
        "tutorial/stride-c-left", "tutorial/stride-a-right", "tutorial/stride-b-right", "tutorial/stride-c-right")) {
      var bitmap = ImageIO.read(project.resolve("assets/" + image + ".png").toFile());
      assertEquals(900, bitmap.getWidth(), image);
      assertEquals(900, bitmap.getHeight(), image);
      assertEquals(0, bitmap.getRGB(0, 0) >>> 24, image);
      assertTrue((bitmap.getRGB(450, 450) >>> 24) >= 248, image);
    }
    assertDoesNotThrow(() -> JesLoader.load(Files.readString(project.resolve("scripts/scenes/panel.jes"))));
    AssetCatalog.setDefaultManager(new FilesystemAssetManager(project));
    try {
      VnScenarioLoader loader = new VnScenarioLoader();
      String map = Files.readString(project.resolve("config/story/story.storymap"));
      var manifest = new Properties();
      try (var reader = Files.newBufferedReader(project.resolve("jvn.project"))) { manifest.load(reader); }
      assertEquals("scripts/story/tutorial_hub.vns", manifest.getProperty("entryVns"));
      for (String sample : List.of("prologue", "branch_demo", "epilogue")) {
        assertFalse(map.contains("scripts/story/" + sample + ".vns"));
        assertFalse(Files.exists(project.resolve("scripts/story/" + sample + ".vns")));
      }
      for (boolean still : List.of(false, true)) {
        Map<String, Object> progress = new HashMap<>();
        progress.put("tutorial_still", still);
        int complete = 0;
        for (var lesson : TutorialCatalog.LESSONS) {
          assertTrue(map.contains("arc " + lesson.arc() + " script \"" + lesson.path() + "\""), lesson.arc());
          VnScene scene = open(loader, lesson.path().substring("scripts/".length()), progress);
          assertNotNull(scene.getScenario().getStagePreset("dusk"), lesson.path());
          assertEquals("room", scene.getState().getCurrentBackgroundId(), lesson.path());
          if (!still && System.getenv("JVN_TUTORIAL_QA_DIR") != null) {
            scene.update(1000);
            capture(scene, "lesson-" + lesson.path().substring(lesson.path().lastIndexOf('/') + 1, lesson.path().lastIndexOf('/') + 3));
          }
          goToChoice(scene, "check", lesson.path());
          assertTrue(scene.getState().getCurrentNode().getChoices().stream()
              .anyMatch(c -> "return_hub".equals(c.getTargetLabel())), "Review must be optional: " + lesson.path());
          pick(scene, "check");
          goToChoice(scene, "retry", lesson.path());
          pick(scene, "retry");
          goToChoice(scene, "correct", lesson.path());
          pick(scene, "correct");
          goToChoice(scene, "return_hub", lesson.path());
          pick(scene, "return_hub");
          assertEquals(++complete, ((Number)scene.getState().getVariables().get("tutorial_count")).intValue(), lesson.path());
          scene.jumpToLabel("return_hub");
          assertEquals(complete, ((Number)scene.getState().getVariables().get("tutorial_count")).intValue(), lesson.path());
          progress = new HashMap<>(scene.getState().getVariables());
        }
      }
      var savedBoard = open(loader, "story/tutorial_hub.vns", Map.of("tutorial_count", 7));
      goToChoice(savedBoard, "options", "saved board");
      pick(savedBoard, "options");
      goToChoice(savedBoard, "still", "preferences");
      pick(savedBoard, "still");
      assertEquals(true, savedBoard.getState().getVariables().get("tutorial_still"));
      assertEquals(7, savedBoard.getState().getVariables().get("tutorial_count"));
      for (boolean still : List.of(false, true)) {
        var movement = open(loader, "tutorial/08_character_movement.vns", Map.of("tutorial_still", still));
        movement.update(800);
        var state = movement.getState();
        var desk = state.getCharacterPosition("pip");
        movement.advance();
        var mark = state.getCharacterPosition("pip");
        assertEquals(still, desk.equals(mark));
        var visual = state.getCharacterVisual(mark);
        double start = visual.getOffsetX();
        if (!still) {
          capture(movement, "walk-start");
          if (System.getenv("JVN_TUTORIAL_QA_DIR") != null) {
            var playback = open(loader, "tutorial/08_character_movement.vns", Map.of());
            playback.update(800);
            playback.advance();
            recordMovement(playback);
          }
        }
        for (int frame = 0; frame < 10; frame++) movement.update(37);
        double middle = visual.getOffsetX();
        if (!still) capture(movement, "walk-middle");
        for (int frame = 0; frame < 10; frame++) movement.update(37);
        assertEquals(0.0, visual.getOffsetX(), 0.00001);
        if (still) assertEquals(start, middle, 0.00001);
        else {
          assertTrue(Math.abs(start) > Math.abs(middle) && Math.abs(middle) > 0.01);
          capture(movement, "walk-end");
        }
      }
      var hub = open(loader, "story/tutorial_hub.vns", Map.of());
      assertEquals(VnNodeType.CHOICE, hub.getState().getCurrentNode().getType(), "Start opens the topic list immediately");
      assertEquals(7, hub.getState().getCurrentNode().getChoices().size());
      hub.update(700);
      capture(hub, "board");
      Set<String> visitedTopics = new HashSet<>();
      for (int page = 1; page <= 6; page++) {
        var pageScene = open(loader, "story/tutorial_hub.vns", Map.of());
        goToChoice(pageScene, "topics_page_" + page, "board");
        pick(pageScene, "topics_page_" + page);
        goToChoice(pageScene, "topics", "page " + page);
        var choices = pageScene.getState().getCurrentNode().getChoices();
        assertEquals(6, choices.size());
        for (var choice : choices) {
          if ("topics".equals(choice.getTargetLabel())) continue;
          assertTrue(visitedTopics.add(choice.getTargetLabel()));
          var lesson = TutorialCatalog.LESSONS.stream().filter(l -> l.hubLabel().equals(choice.getTargetLabel()))
              .findFirst().orElseThrow();
          assertTrue(Files.readString(project.resolve(lesson.path())).contains("[goto TutorialHub:topics_page_" + page + "]"),
              "Example should return to its own category: " + lesson.path());
        }
      }
      assertEquals(TutorialCatalog.LESSONS.size(), visitedTopics.size());
      FxToolkit.runFx(() -> {
        var menu = new MainMenuScene(null, new VnSettings(), null, "story/tutorial_hub.vns", null);
        assertEquals("JVN Tutorial", menu.getDisplayTitle());
        assertEquals("Start tutorial", menu.getDisplayItems()[0]);
        menu.setSelected(0);
        Canvas canvas = new Canvas(1280, 720);
        var renderer = new MenuRenderer(canvas.getGraphicsContext2D(), MenuTheme.fromAssets());
        renderer.setProjectRoot(project.toFile());
        renderer.renderMainMenu(menu, 1280, 720);
        save(canvas, "title");
        return null;
      });
    } finally { AssetCatalog.setDefaultManager(new ClasspathAssetManager()); }
  }

  private static VnScene open(VnScenarioLoader loader, String path, Map<String, Object> variables) throws Exception {
    var scene = new VnScene(loader.load(path));
    scene.setInterop(new DefaultVnInterop());
    scene.getState().getVariables().putAll(variables);
    scene.onEnter();
    return scene;
  }

  private static void goToChoice(VnScene scene, String target, String description) {
    for (int tick = 0; tick < 60; tick++) {
      var node = scene.getState().getCurrentNode();
      assertNotNull(node, description + " ended before " + target);
      if (node.getType() == VnNodeType.CHOICE) {
        if (node.getChoices().stream().anyMatch(c -> target.equals(c.getTargetLabel()))) return;
        pick(scene, "check");
      } else if (node.getType() == VnNodeType.DIALOGUE) {
        scene.advance();
      } else {
        scene.update(1000);
      }
    }
    fail(description + " did not reach " + target);
  }

  private static void pick(VnScene scene, String target) {
    var choices = scene.getState().getCurrentNode().getChoices();
    for (int i = 0; i < choices.size(); i++) {
      if (target.equals(choices.get(i).getTargetLabel())) { scene.selectChoice(i); return; }
    }
    fail("No choice leads to " + target);
  }

  private void recordMovement(VnScene scene) throws Exception {
    var canvas = FxToolkit.runFx(() -> new Canvas(960, 540));
    var renderer = FxToolkit.runFx(() -> {
      var view = new VnRenderer(canvas.getGraphicsContext2D());
      view.setProjectRoot(project.toFile());
      view.render(scene.getState(), scene.getScenario(), 960, 540);
      return view;
    });
    try {
      Thread.sleep(300);
      FxToolkit.runFx(() -> {
        for (int f = 0; f < 60; f++) {
          if (f == 32) scene.advance();
          scene.update(40);
          scene.getState().setTextRevealProgress(Integer.MAX_VALUE);
          renderer.updateAnimation(40);
          renderer.render(scene.getState(), scene.getScenario(), 960, 540);
          save(canvas, String.format(Locale.ROOT, "motion-%02d", f));
        }
        return null;
      });
    } finally { FxToolkit.runFx(renderer::dispose); }
  }

  private void capture(VnScene scene, String name) throws Exception {
    var canvas = FxToolkit.runFx(() -> new Canvas(1280, 720));
    var renderer = FxToolkit.runFx(() -> {
      var view = new VnRenderer(canvas.getGraphicsContext2D());
      view.setProjectRoot(project.toFile());
      scene.getState().setTextRevealProgress(Integer.MAX_VALUE);
      view.render(scene.getState(), scene.getScenario(), 1280, 720);
      return view;
    });
    try {
      // JavaFX decodes backgrounds asynchronously and fades dialogue on wall-clock time.
      long limit = System.nanoTime() + 4_000_000_000L;
      long readyAfter = System.nanoTime() + 260_000_000L;
      boolean ready;
      do {
        Thread.sleep(30);
        ready = FxToolkit.runFx(() -> {
          renderer.render(scene.getState(), scene.getScenario(), 1280, 720);
          int pixel = canvas.snapshot(null, null).getPixelReader().getArgb(1100, 30);
          return (pixel & 0xFFFFFF) > 0x101010;
        });
      } while ((!ready || System.nanoTime() < readyAfter) && System.nanoTime() < limit);
      assertTrue(ready, "Room failed to render at " + name);
      FxToolkit.runFx(() -> { save(canvas, name); return null; });
    } finally { FxToolkit.runFx(renderer::dispose); }
  }

  private static void save(Canvas canvas, String name) throws Exception {
    String destination = System.getenv("JVN_TUTORIAL_QA_DIR");
    if (destination == null || destination.isBlank()) return;
    Path root = Files.createDirectories(Path.of(destination));
    ImageIO.write(SwingFXUtils.fromFXImage(canvas.snapshot(null, null), null), "png", root.resolve(name + ".png").toFile());
  }
  private static Object field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
  }
  private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
    Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args);
  }
}
