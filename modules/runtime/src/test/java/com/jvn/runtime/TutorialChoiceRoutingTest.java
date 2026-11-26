package com.jvn.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.vn.*;
import com.jvn.scripting.jes.runtime.JesScene2D;
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
  void allWizardLessonChoicesReturnToTheirOwnCategory(@TempDir Path home) throws Exception {
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
      assertEquals(VnNodeType.DIALOGUE, hub.getState().getCurrentNode().getType());
      assertNull(hub.getState().getVariable("ui.topicMap"));
      choices(hub);
      assertEquals("config/ui/tutorial-topics.properties", hub.getState().getVariable("ui.topicMap"));
      Set<String> visited = new HashSet<>();
      int[][] expected = {{1, 2, 7, 10, 11}, {3, 4, 5, 8, 13}, {6, 9, 18, 23, 24},
          {14, 15, 20, 21, 22}, {17, 19, 25, 26, 30}, {12, 16, 27, 28, 29},
          {31, 32, 33, 34, 35, 36}, {37, 38, 39, 40, 41, 42}, {43, 44, 45, 49, 51}, {46, 47, 48, 50, 52}};
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
            choose(lesson, "practice");
            var saves = new com.jvn.core.vn.save.VnSaveManager();
            saves.save(lesson.getState(), "Threshold");
            var loadEngine = new Engine(ApplicationConfig.builder().build());
            loadEngine.setVnInteropFactory(RuntimeVnInterop::new);
            var load = new com.jvn.core.menu.LoadMenuScene(loadEngine, saves, "story/tutorial_hub.vns", new VnSettings(), null);
            load.setSelected(load.getSaves().indexOf("Threshold"));
            load.loadSelected();
            var restored = assertInstanceOf(VnScene.class, loadEngine.scenes().peek());
            assertEquals("tutorial_code", restored.getState().getVariable("ui.dialogueFacet"));
            assertEquals("threshold", restored.getState().getVariable("tutorial_excerpt"));
            assertEquals(lesson.getState().getCurrentNodeIndex(), restored.getState().getCurrentNodeIndex());
            choose(lesson, "practice_result");
            for (int score = 0; score <= 2; score++) {
              choose(lesson, "pick_score");
              choose(lesson, java.util.List.of("score_zero", "score_one", "score_two").get(score));
              assertEquals(score + 1, ((Number) lesson.getState().getVariable("lab_score")).intValue());
              assertTrue(lesson.getState().getCurrentNode().getDialogue().getText()
                  .contains(score == 0 ? "failed" : "passed"));
            }
          }
          if (expected[page][index] == 26) {
            int[] squared = {4050, 2500, 2561};
            var points = java.util.List.of("point_corner", "point_boundary", "point_outside");
            for (int i = 0; i < points.size(); i++) {
              choose(lesson, "pick_point");
              choose(lesson, points.get(i));
              assertEquals(squared[i], ((Number) lesson.getState().getVariable("squared")).intValue());
              assertEquals(i == 1, lesson.getState().getVariable("inside"));
            }
          }
          if (expected[page][index] == 19) {
            choices(lesson);
            var saves = new com.jvn.core.vn.save.VnSaveManager();
            saves.save(lesson.getState(), "Tutorial");
            choose(lesson, "select_a");
            assertEquals("A", lesson.getState().getVariable("example_value"));
            var loadEngine = new Engine(ApplicationConfig.builder().build());
            loadEngine.setVnInteropFactory(RuntimeVnInterop::new);
            var load = new com.jvn.core.menu.LoadMenuScene(loadEngine, saves, "story/tutorial_hub.vns", new VnSettings(), null);
            assertTrue(load.getSaves().contains("Tutorial"));
            load.setSelected(load.getSaves().indexOf("Tutorial"));
            load.loadSelected();
            var resumed = assertInstanceOf(VnScene.class, loadEngine.scenes().peek());
            assertEquals(id, resumed.getScenario().getId());
            choose(resumed, "select_b");
            assertEquals("B", resumed.getState().getVariable("example_value"));
            assertEquals(((Number) lesson.getState().getVariable("tutorial_count")).intValue(),
                ((Number) resumed.getState().getVariable("tutorial_count")).intValue());
          }
          if (expected[page][index] == 10) choose(lesson, "branch_a");
          if (expected[page][index] == 21) {
            for (var take : java.util.List.of("quarter", "half", "checklist")) {
              choose(lesson, take);
              var panel = lesson.getState().getTopOverlayScreen();
              assertNotNull(panel, take);
              assertEquals("tutorial_card", panel.getId());
              var next = panel.getButtons().stream().filter(b -> "continue".equals(b.id())).findFirst().orElseThrow();
              assertEquals(!"quarter".equals(take), next.enabled());
              lesson.getState().returnOverlayScreen(panel.getId(), next.enabled() ? "continue" : "close");
              lesson.update(16);
            }
          }
          if (expected[page][index] == 30) {
            choose(lesson, "compare_presets");
            choose(lesson, "use_dusk");
            assertEquals(false, lesson.getState().getVariable("example_default"));
            assertEquals("dusk", lesson.getState().getActiveStagePresetId());
            choose(lesson, "compare_presets");
            choose(lesson, "use_default");
            assertEquals(true, lesson.getState().getVariable("example_default"));
            assertNotEquals("dusk", lesson.getState().getActiveStagePresetId());
          }
          if (expected[page][index] == 43 || expected[page][index] == 45) {
            choose(lesson, "demo");
            var lab = assertInstanceOf(JesScene2D.class, engine.scenes().peek());
            var hero = lab.find("hero");
            double start = hero.getX();
            press(lab, engine, "RIGHT");
            press(lab, engine, "RIGHT");
            assertEquals(start + 96, hero.getX(), 0.001);
            press(lab, engine, "RIGHT");
            assertEquals(start + 96, hero.getX(), 0.001);
            press(lab, engine, "ESCAPE");
            assertSame(lesson, engine.scenes().peek());
          }
          if (expected[page][index] == 49) {
            choose(lesson, "demo");
            var lab = assertInstanceOf(JesScene2D.class, engine.scenes().peek());
            press(lab, engine, "C", "B");
            var bodies = lab.getWorld().getBodies();
            assertEquals(2, bodies.size());
            assertEquals(RigidBody2D.ShapeType.CIRCLE, bodies.get(0).getShapeType());
            assertEquals(RigidBody2D.ShapeType.AABB, bodies.get(1).getShapeType());
            press(lab, engine, "ENTER", "ESCAPE");
            assertSame(lesson, engine.scenes().peek());
          }
          if (expected[page][index] == 50) {
            var keys = java.util.List.of("ESCAPE", "1", "2");
            var cards = java.util.List.of("none", "hillside", "waterfront");
            for (int stamps = 0; stamps < keys.size(); stamps++) {
              choose(lesson, "demo");
              var lab = assertInstanceOf(JesScene2D.class, engine.scenes().peek());
              press(lab, engine, keys.get(stamps));
              assertSame(lesson, engine.scenes().peek());
              assertEquals(cards.get(stamps), lesson.getState().getVariable("card"));
              assertEquals(stamps, ((Number) lesson.getState().getVariable("stamp_count")).intValue());
            }
          }
          if (expected[page][index] == 51) {
            choose(lesson, "demo");
            var lab = assertInstanceOf(JesScene2D.class, engine.scenes().peek());
            press(lab, engine, "RIGHT");
            assertSame(lab, engine.scenes().peek());
            press(lab, engine, "RIGHT");
            assertSame(lesson, engine.scenes().peek());
            assertEquals("stamped", lesson.getState().getVariable("checkpoint_status"));
            assertEquals(4, ((Number) lesson.getState().getVariable("tileX")).intValue());
            assertEquals(3, ((Number) lesson.getState().getVariable("tileY")).intValue());
            choose(lesson, "demo");
            var replay = assertInstanceOf(JesScene2D.class, engine.scenes().peek());
            press(replay, engine, "ESCAPE");
            assertSame(lesson, engine.scenes().peek());
            assertEquals("cancelled", lesson.getState().getVariable("checkpoint_status"));
            assertNull(lesson.getState().getVariable("tileX"));
          }
          if (expected[page][index] == 52) {
            choose(lesson, "demo");
            var lab = assertInstanceOf(JesScene2D.class, engine.scenes().peek());
            var status = assertInstanceOf(com.jvn.core.scene2d.Label2D.class, lab.find("status"));
            for (int frame = 0; frame < 70; frame++) lab.update(16);
            assertEquals(510.0, lab.find("fast").getX(), 0.001);
            assertTrue(lab.find("slow").getX() > 150 && lab.find("slow").getX() < 510);
            assertEquals("The timeline continues during movement.", status.getText());
            for (int frame = 0; frame < 55; frame++) lab.update(16);
            assertEquals(510.0, lab.find("slow").getX(), 0.001);
            assertEquals("Both markers arrived.", status.getText());
            press(lab, engine, "ENTER");
            assertSame(lesson, engine.scenes().peek());
          }
          choose(lesson, "return_hub");
          hub = assertInstanceOf(VnScene.class, engine.scenes().peek());
          assertEquals("tutorial_topics", hub.getScenario().getId());
          assertEquals(visited.size(), ((Number) hub.getState().getVariable("tutorial_count")).intValue());
          assertEquals(choice.getTargetLabel(), choices(hub).get(index).getTargetLabel());
          assertNull(hub.getState().getCharacterPosition("ginger"));
          assertNull(hub.getState().getVariable("ui.dialogueFacet"));
          assertEquals("config/ui/tutorial-topics.properties", hub.getState().getVariable("ui.topicMap"));
        }
        choose(hub, "topics");
      }
      assertEquals(52, visited.size());
      choose(hub, "goodbye");
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

  private static void press(JesScene2D scene, Engine engine, String... keys) {
    var input = engine.input();
    scene.setInput(input);
    for (String key : keys) input.keyDown(key);
    scene.update(16);
    for (String key : keys) input.keyUp(key);
    input.endFrame();
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
