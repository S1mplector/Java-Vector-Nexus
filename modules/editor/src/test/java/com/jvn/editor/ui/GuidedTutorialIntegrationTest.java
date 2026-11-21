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
import java.nio.file.*;
import java.util.*;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class GuidedTutorialIntegrationTest {
  @TempDir Path parent;
  private Path project;

  @Test void generatedCoursePlaysEachLessonAndCountsReplaysOnce() throws Exception {
    FxToolkit.runFx(() -> {
      Stage owner = new Stage();
      owner.setScene(new javafx.scene.Scene(new javafx.scene.layout.Pane()));
      owner.show();
      NewProjectWizard wizard = new NewProjectWizard(owner);
      try {
        wizard.show();
        RadioButton tutorial = wizard.getScene().getRoot().lookupAll(".radio-button").stream()
            .filter(RadioButton.class::isInstance).map(RadioButton.class::cast)
            .filter(choice -> choice.getText().equals("Tutorial Workspace with Ginger")).findFirst().orElseThrow();
        tutorial.fire();
        assertTrue(((CheckBox) field(wizard, "chkTutorialPack")).isSelected());
        ((CheckBox) field(wizard, "chkGitInit")).setSelected(false);
        ((javafx.scene.control.TextField) field(wizard, "txtLocation")).setText(parent.toString());
        ((javafx.scene.control.TextField) field(wizard, "txtProjectName")).setText("GingerStudio");
        var create = (javafx.scene.control.Button) field(wizard, "btnCreate");
        assertTrue(tutorial.isSelected());
        assertEquals("Create Ginger Tutorial", create.getText());
        assertFalse(create.isDisabled());
        create.fire();
        assertNotNull(wizard.getCreatedProjectDir());
        project = wizard.getCreatedProjectDir().toPath();
        assertEquals(parent.resolve("GingerStudio"), project);
      } finally { wizard.close(); owner.close(); }
      return null;
    });
    assertFalse(Files.exists(project.resolve("assets/demo/backgrounds/game.png")));
    assertFalse(Files.exists(project.resolve("assets/tutorial/lofi-music-2.mp3")));
    assertFalse(Files.exists(project.resolve("assets/tutorial/window-room.jpg")));
    var backdrop = ImageIO.read(project.resolve("assets/tutorial/white.png").toFile());
    for (int y = 0; y < backdrop.getHeight(); y++)
      for (int x = 0; x < backdrop.getWidth(); x++) assertEquals(0xffffffff, backdrop.getRGB(x, y));
    Properties layout = new Properties();
    try (var reader = Files.newBufferedReader(project.resolve("config/ui/dialogue.layout"))) { layout.load(reader); }
    assertEquals("#FFFFFF", layout.getProperty("nameTextColor"));
    assertEquals("#000000B8", layout.getProperty("textBoxColor"));
    for (String pose : List.of("normal", "angry", "shy", "sad", "confused", "embarrassed", "annoyed", "thoughtful")) {
      var bitmap = ImageIO.read(project.resolve("assets/tutorial/ginger_" + pose + ".png").toFile());
      assertEquals(500, bitmap.getWidth(), pose);
      assertEquals(1280, bitmap.getHeight(), pose);
      assertTrue(bitmap.getColorModel().hasAlpha(), pose);
    }
    assertDoesNotThrow(() -> JesLoader.load(Files.readString(project.resolve("scripts/scenes/panel.jes"))));
    AssetCatalog.setDefaultManager(new FilesystemAssetManager(project));
    try {
      VnScenarioLoader loader = new VnScenarioLoader();
      String map = Files.readString(project.resolve("config/story/story.storymap"));
      var manifest = new Properties();
      try (var reader = Files.newBufferedReader(project.resolve("jvn.project"))) { manifest.load(reader); }
      assertEquals("tutorial_workspace", manifest.getProperty("scaffold.template"));
      assertEquals("true", manifest.getProperty("feature.tutorialPack"));
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
        var desk = state.getCharacterPosition("ginger");
        movement.advance();
        var mark = state.getCharacterPosition("ginger");
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
      assertEquals(VnNodeType.DIALOGUE, hub.getState().getCurrentNode().getType());
      goToChoice(hub, "goodbye", "greeting");
      assertEquals(8, hub.getState().getCurrentNode().getChoices().size());
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
        assertEquals("30 lessons with Ginger", menu.getDisplaySubtitle());
        assertEquals("Start tutorial", menu.getDisplayItems()[0]);
        menu.setSelected(0);
        Canvas canvas = new Canvas(1280, 720);
        var renderer = new MenuRenderer(canvas.getGraphicsContext2D(), MenuTheme.fromAssets());
        renderer.setProjectRoot(project.toFile());
        renderer.renderMainMenu(menu, 1280, 720);
        save(canvas, "title");
        var saves = new com.jvn.core.vn.save.VnSaveManager(parent.resolve("menu-saves").toString());
        var pause = new com.jvn.core.menu.PauseMenuScene(null, hub, saves, "story/tutorial_hub.vns", null);
        assertEquals("resume", pause.getItem(0).id());
        renderer.renderPauseMenu(pause, 1280, 720);
        save(canvas, "pause");
        renderer.renderSettings(new com.jvn.core.menu.SettingsScene(new VnSettings()), 1280, 720);
        save(canvas, "settings");
        com.jvn.core.localization.Localization.init("en", getClass().getClassLoader());
        assertEquals("No saved games yet.", com.jvn.core.localization.Localization.t("load.no_saves"));
        var loadMenu = new com.jvn.core.menu.LoadMenuScene(null, saves, "story/tutorial_hub.vns", new VnSettings(), null);
        renderer.renderLoadMenu(loadMenu, 1280, 720);
        save(canvas, "empty-load");
        saves.save(hub.getState(), "Studio");
        String captures = System.getenv("JVN_TUTORIAL_QA_DIR");
        if (captures != null && !captures.isBlank()) {
          Files.copy(Path.of(captures, "board.png"), Path.of(saves.getSaveDirectory(), "Studio.png"));
        }
        var saveMenu = new com.jvn.core.menu.SaveMenuScene(null, saves, hub);
        saveMenu.onEnter();
        assertEquals("Save game", saveMenu.getDisplayTitle());
        renderer.renderSaveMenu(saveMenu, 1280, 720);
        save(canvas, "save");
        loadMenu = new com.jvn.core.menu.LoadMenuScene(null, saves, "story/tutorial_hub.vns", new VnSettings(), null);
        assertEquals("Studio", loadMenu.getSelectedName());
        assertEquals("Load game", loadMenu.getDisplayTitle());
        renderer.renderLoadMenu(loadMenu, 1280, 720);
        save(canvas, "load");
        return null;
      });
      String export = System.getenv("JVN_TUTORIAL_QA_DIR");
      if (export != null && !export.isBlank()) {
        Path output = Path.of(export);
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(output.resolve("Ginger-Tutorial.zip")));
             var files = Files.walk(project)) {
          for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
            String name = "GingerTutorial/" + project.relativize(file).toString().replace('\\', '/');
            zip.putNextEntry(new java.util.zip.ZipEntry(name));
            Files.copy(file, zip);
            zip.closeEntry();
          }
        }
        var sheet = new java.awt.image.BufferedImage(1280, 720, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = sheet.createGraphics();
        try {
          graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
              java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
          var menus = List.of("pause", "settings", "save", "load");
          for (int i = 0; i < menus.size(); i++) {
            var screen = ImageIO.read(output.resolve(menus.get(i) + ".png").toFile());
            graphics.drawImage(screen, i % 2 * 640, i / 2 * 360, 640, 360, null);
          }
        } finally { graphics.dispose(); }
        ImageIO.write(sheet, "png", output.resolve("Ginger-menus.png").toFile());
      }
    } finally {
      AssetCatalog.setDefaultManager(new ClasspathAssetManager());
      com.jvn.core.localization.Localization.init("en", getClass().getClassLoader());
    }
  }

  private static VnScene open(VnScenarioLoader loader, String path, Map<String, Object> variables) throws Exception {
    var scene = new VnScene(loader.load(path));
    scene.getState().setSourceScriptName(path);
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
        pick(scene, node.getChoices().stream().anyMatch(c -> "read_note".equals(c.getTargetLabel())) ? "read_note" : "check");
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
      assertTrue(ready, "Background failed to render at " + name);
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
}
