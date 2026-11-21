package com.jvn.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.vn.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

class TutorialChoiceRoutingTest {
  @Test
  void aChoiceCannotFallThroughToAnotherScript(@TempDir Path project) throws Exception {
    Path scripts = Files.createDirectories(project.resolve("scripts"));
    Files.writeString(scripts.resolve("first.vns"), "@scenario first\n@label start\n[inc visits]\nnarrator: First room.\n[end]\n");
    Files.writeString(scripts.resolve("second.vns"), "@scenario second\n@label start\n[inc visits]\nnarrator: Second room.\n[end]\n");
    Files.writeString(scripts.resolve("hub.vns"), """
        @scenario hub
        @label start
        > First room -> first
        > Second room -> second
        @label first
        [goto first.vns:start]
        @label second
        [goto second.vns:start]
        """);
    AssetCatalog.setDefaultManager(new FilesystemAssetManager(project));
    try {
      for (int index = 0; index < 2; index++) {
        var engine = new Engine(ApplicationConfig.builder().build());
        var hub = start(engine, "hub.vns");
        hub.getState().setVariable("visits", 5);
        hub.advanceFromClick();
        hub.advance();
        assertSame(hub, engine.scenes().peek());
        assertEquals(VnNodeType.CHOICE, hub.getState().getCurrentNode().getType());
        hub.moveChoiceFocus(index == 0 ? 1 : -1);
        assertEquals(index, hub.getState().getFocusedChoiceIndex());
        hub.selectChoice(index);
        var chosen = assertInstanceOf(VnScene.class, engine.scenes().peek());
        assertEquals(index == 0 ? "first" : "second", chosen.getScenario().getId());
        assertEquals(6, ((Number) chosen.getState().getVariable("visits")).intValue());
      }
    } finally { AssetCatalog.setDefaultManager(new ClasspathAssetManager()); }
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "JVN_GENERATED_TUTORIAL", matches = ".+")
  void allThirtyWizardLessonChoicesReturnToTheirOwnCategory(@TempDir Path home) throws Exception {
    Path project = Path.of(System.getenv("JVN_GENERATED_TUTORIAL"));
    String previousHome = System.getProperty("user.home");
    String previousGame = System.getProperty(VnStoragePaths.GAME_ID_PROPERTY);
    System.setProperty("user.home", home.toString());
    VnStoragePaths.configureGame("ginger-course");
    String previousRoot = System.getProperty("jvn.assets.root");
    System.setProperty("jvn.assets.root", project.toString());
    AssetCatalog.setDefaultManager(new FilesystemAssetManager(project));
    try {
      var engine = new Engine(ApplicationConfig.builder().build());
      var hub = start(engine, "story/tutorial_hub.vns");
      Set<String> visited = new HashSet<>();
      int[][] expected = {{1, 2, 7, 10, 11}, {3, 4, 5, 8, 13}, {6, 9, 18, 23, 24},
          {14, 15, 20, 21, 22}, {17, 19, 25, 26, 30}, {12, 16, 27, 28, 29}};
      for (int page = 0; page < expected.length; page++) {
        choose(hub, "topics_page_" + (page + 1));
        for (int index = 0; index < expected[page].length; index++) {
          var choice = choices(hub).get(index);
          hub.selectChoice(index);
          var lesson = assertInstanceOf(VnScene.class, engine.scenes().peek());
          String id = String.format(java.util.Locale.ROOT, "tutorial_lesson_%02d", expected[page][index]);
          assertEquals(id, lesson.getScenario().getId(), choice.getTargetLabel());
          assertTrue(visited.add(id));
          if (expected[page][index] == 7) {
            var saves = new com.jvn.core.vn.save.VnSaveManager();
            saves.save(lesson.getState(), "Rehearsal");
            var loadEngine = new Engine(ApplicationConfig.builder().build());
            var load = new com.jvn.core.menu.LoadMenuScene(loadEngine, saves, "story/tutorial_hub.vns", new VnSettings(), null);
            assertTrue(load.getSaves().contains("Rehearsal"));
            load.setSelected(load.getSaves().indexOf("Rehearsal"));
            load.loadSelected();
            var resumed = assertInstanceOf(VnScene.class, loadEngine.scenes().peek());
            assertEquals(id, resumed.getScenario().getId());
            assertEquals(((Number) lesson.getState().getVariable("tutorial_count")).intValue(),
                ((Number) resumed.getState().getVariable("tutorial_count")).intValue());
          }
          if (expected[page][index] == 10) choose(lesson, "read_note");
          choose(lesson, "return_hub");
          hub = assertInstanceOf(VnScene.class, engine.scenes().peek());
          assertEquals("tutorial_topics", hub.getScenario().getId());
          assertEquals(visited.size(), ((Number) hub.getState().getVariable("tutorial_count")).intValue());
          assertEquals(choice.getTargetLabel(), choices(hub).get(index).getTargetLabel());
        }
        choose(hub, "topics");
      }
      assertEquals(30, visited.size());
      choose(hub, "goodbye");
      hub.advance();
      assertInstanceOf(com.jvn.core.menu.MainMenuScene.class, engine.scenes().peek());
    } finally {
      AssetCatalog.setDefaultManager(new ClasspathAssetManager());
      System.setProperty("user.home", previousHome);
      VnStoragePaths.configureGame(previousGame);
      if (previousRoot == null) System.clearProperty("jvn.assets.root");
      else System.setProperty("jvn.assets.root", previousRoot);
    }
  }

  private static VnScene start(Engine engine, String script) throws Exception {
    var scene = new VnScene(new VnScenarioLoader().load(script));
    scene.getState().setSourceScriptName(script);
    scene.setInterop(new RuntimeVnInterop(engine));
    engine.scenes().push(scene);
    return scene;
  }

  private static java.util.List<Choice> choices(VnScene scene) {
    for (int i = 0; i < 150; i++) {
      var node = scene.getState().getCurrentNode();
      assertNotNull(node);
      if (node.getType() == VnNodeType.CHOICE) return node.getChoices();
      if (node.getType() == VnNodeType.DIALOGUE) scene.advance();
      else scene.update(1000);
    }
    throw new AssertionError("No choices in " + scene.getScenario().getId());
  }

  private static void choose(VnScene scene, String target) {
    var options = choices(scene);
    for (int i = 0; i < options.size(); i++) {
      if (target.equals(options.get(i).getTargetLabel())) {
        scene.selectChoice(i);
        return;
      }
    }
    fail("Choice missing in " + scene.getScenario().getId() + " -> " + target);
  }
}
