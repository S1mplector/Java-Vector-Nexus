package com.jvn.editor.ui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.scripting.jes.JesLoader;

class NewProjectScaffoldTemplateWiringTest {

  private static final Pattern UNRESOLVED_TOKEN = Pattern.compile("\\{\\{[A-Z0-9_]+\\}\\}");

  private static final List<String> STORY_TEMPLATE_PATHS = List.of(
      "scripts/story/prologue_blank.vns",
      "scripts/story/prologue_sample.vns",
      "scripts/story/tutorial_hub.vns",
      "scripts/story/tutorial_hub_blank.vns",
      "scripts/story/branch_demo_blank.vns",
      "scripts/story/branch_demo_sample.vns",
      "scripts/story/epilogue_blank.vns",
      "scripts/story/epilogue_sample.vns");

  private static final List<String> TUTORIAL_TEMPLATE_PATHS = TutorialCatalog.LESSONS.stream()
      .map(TutorialCatalog.Lesson::path).toList();

  private static final Map<String, String> TOKENS = buildTokens();

  @Test
  void storyTemplatesRenderWithoutUnresolvedTokens() throws Exception {
    assertEquals(8, STORY_TEMPLATE_PATHS.size(), "Story template inventory changed");
    for (String path : STORY_TEMPLATE_PATHS) {
      String rendered = render(path);
      assertFalse(
          UNRESOLVED_TOKEN.matcher(rendered).find(),
          () -> "Unresolved token found in rendered story template " + path + ":\n" + rendered);
    }
  }

  @Test
  void tutorialTemplatesRenderWithoutUnresolvedTokens() throws Exception {
    assertEquals(30, TUTORIAL_TEMPLATE_PATHS.size(), "Tutorial template inventory changed");
    for (String path : TUTORIAL_TEMPLATE_PATHS) {
      String rendered = render(path);
      assertFalse(
          UNRESOLVED_TOKEN.matcher(rendered).find(),
          () -> "Unresolved token found in rendered tutorial template " + path + ":\n" + rendered);
    }
  }

  @Test
  void everyRenderedVnsTemplateIsAcceptedByTheRuntimeParser() throws Exception {
    for (String path : STORY_TEMPLATE_PATHS) {
      String rendered = render(path);
      assertDoesNotThrow(
          () -> parseVns(rendered),
          () -> "Runtime parser rejected rendered story template " + path);
    }
    for (String path : TUTORIAL_TEMPLATE_PATHS) {
      String rendered = render(path);
      assertDoesNotThrow(
          () -> parseVns(rendered),
          () -> "Runtime parser rejected rendered tutorial template " + path);
    }
  }

  @Test
  void lavenderUsesWaistUpFramingInStarterScenes() throws Exception {
    String prologue = render("scripts/story/prologue_sample.vns");
    String tutorialHub = render("scripts/story/tutorial_hub.vns");

    assertTrue(prologue.contains("@position desk_left 0.28 0.96"));
    for (String scene : List.of(prologue, tutorialHub)) {
      assertTrue(scene.contains("[set ui.characterHeightFactor 1.5]"));
      assertTrue(scene.contains("[set ui.characterBaselineY 1.5]"));
      assertFalse(scene.contains("[set ui.characterHeightFactor 1.28]"));
      assertFalse(scene.contains("[set ui.characterBaselineY 1.42]"));
    }
  }

  @Test
  void bundledJesTutorialSceneLoadsThroughTheRuntimeLoader() throws Exception {
    String rendered = render("scripts/scenes/studio_tour.jes");
    assertFalse(UNRESOLVED_TOKEN.matcher(rendered).find(), "JES template contains unresolved tokens");
    assertDoesNotThrow(() -> JesLoader.load(rendered));
  }

  @Test
  void defaultGameAssetsArePackagedWithTheEditor() {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    String root = "com/jvn/editor/templates/new-project/demo-assets/";
    assertNotNull(loader.getResource(root + "demo_bg/game.png"));
    assertNotNull(loader.getResource(root + "demo_bg/menu.png"));
    assertNotNull(loader.getResource(root + "Lavender_test_sprite/base/lavender_test_sprite_base.png"));
    assertNotNull(loader.getResource(root + "demo_bgm/03 - Definitely Our Town.mp3"));
  }

  @Test
  void defaultGameBackgroundsAreFullHdAndPlainWhite() throws Exception {
    assertWhiteBackground("demo_bg/game.png");
    assertWhiteBackground("demo_bg/menu.png");
  }

  @Test
  void tutorialTemplatesResetDialoguePresentationMode() throws Exception {
    for (String path : TUTORIAL_TEMPLATE_PATHS) {
      String rendered = render(path);
      assertTrue(
          rendered.contains("[mode dialogue standard]\n[set ui.dialogueUi project]\n[set ui.textBoxAsset none]"),
          () -> "Tutorial does not reset dialogue presentation mode at entry: " + path);
    }

    String hub = render("scripts/story/tutorial_hub.vns");
    assertTrue(
        hub.contains("[mode dialogue standard]\n[set ui.dialogueUi project]\n[set ui.textBoxAsset none]"),
        "Tutorial hub does not reset dialogue presentation mode at entry");
  }

  @Test
  void sampleStoryArcTemplatesPointAtExpectedTargets() throws Exception {
    String prologue = render("scripts/story/prologue_sample.vns");
    assertTrue(prologue.contains("[set tutorial_count 0]"));
    assertTrue(prologue.contains("[goto TutorialHub:start]"));
    assertTrue(prologue.contains("[goto BranchDemo:start]"));

    String branch = render("scripts/story/branch_demo_sample.vns");
    assertTrue(branch.contains("scripts/story/epilogue.vns"));
    assertTrue(branch.contains("[goto Epilogue:start]"));

    String epilogue = render("scripts/story/epilogue_sample.vns");
    assertTrue(epilogue.contains("config/story/story.storymap"));
    assertTrue(epilogue.contains("[end]"));
  }

  @Test
  void tutorialHubRoutesToEveryCatalogLesson() throws Exception {
    String hub = render("scripts/story/tutorial_hub.vns");
    assertEquals(30, TutorialCatalog.LESSONS.size());
    for (TutorialCatalog.Lesson lesson : TutorialCatalog.LESSONS) {
      assertTrue(hub.contains("[goto " + lesson.arc() + ":start]"), "Missing hub route for " + lesson.arc());
      assertTrue(hub.contains("@label " + lesson.hubLabel()), "Missing hub entry for " + lesson.arc());
    }
    for (int page = 1; page <= 6; page++) assertTrue(hub.contains("@label topics_page_" + page));
    assertTrue(hub.contains("@label topics_page_1"));
    assertTrue(hub.contains("@label topics_page_2"));
    assertTrue(hub.contains("@label topics_page_3"));
    assertTrue(hub.contains("[goto T17_VnsJes:start]"));
  }

  @Test
  void blankTemplatesStillFormAPlayableMinimalArc() throws Exception {
    String prologue = render("scripts/story/prologue_blank.vns");
    String tutorialHub = render("scripts/story/tutorial_hub_blank.vns");
    String branch = render("scripts/story/branch_demo_blank.vns");
    String epilogue = render("scripts/story/epilogue_blank.vns");

    assertTrue(prologue.contains("[goto TutorialHub:start]"));
    assertTrue(prologue.contains("[goto BranchDemo:start]"));
    assertTrue(tutorialHub.contains("scripts/story/tutorial_hub.vns"));
    assertTrue(branch.contains("scripts/story/branch_demo.vns"));
    assertTrue(branch.contains("[goto Epilogue:start]"));
    assertTrue(epilogue.contains("[end]"));
  }

  @Test
  void lessonsRecordCompletionOnceAtReturnAndDefaultToStillArtwork() throws Exception {
    int number = 0;
    for (String path : TUTORIAL_TEMPLATE_PATHS) {
      String rendered = render(path);
      String done = String.format(java.util.Locale.ROOT, "tutorial_done_%02d", ++number);
      assertTrue(rendered.contains("[set character.lavender.imageEffect none]"), path);
      assertTrue(rendered.contains("[if tutorial_still]"), path);
      assertTrue(rendered.contains("[if !" + done + "]\n[flag " + done + "]\n[inc tutorial_count]\n[endif]"), path);
      assertTrue(rendered.indexOf("[inc tutorial_count]") > rendered.indexOf("@label return_hub"), path);
    }
  }

  @Test
  void everyLocalJumpAndChoiceHasATarget() throws Exception {
    var labels = Pattern.compile("(?m)^@label ([A-Za-z0-9_]+)");
    var jumps = Pattern.compile("(?m)(?:^\\[jump ([A-Za-z0-9_]+)\\]|^>.* -> ([A-Za-z0-9_]+)$)");
    var paths = new java.util.ArrayList<>(TUTORIAL_TEMPLATE_PATHS);
    paths.add("scripts/story/tutorial_hub.vns");
    for (String path : paths) {
      String script = render(path);
      java.util.Set<String> targets = new java.util.HashSet<>();
      var found = labels.matcher(script);
      while (found.find()) assertTrue(targets.add(found.group(1)), "Duplicate label in " + path);
      var jump = jumps.matcher(script);
      while (jump.find()) {
        String target = jump.group(1) == null ? jump.group(2) : jump.group(1);
        assertTrue(targets.contains(target), "Missing target " + target + " in " + path);
      }
    }
  }

  private static String render(String relativePath) throws Exception {
    String template = loadTemplate(relativePath);
    String rendered = template;
    for (Map.Entry<String, String> entry : TOKENS.entrySet()) {
      rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
    }
    return rendered.replace("\r\n", "\n").replace("\r", "\n");
  }

  private static void parseVns(String rendered) throws Exception {
    String sharedCharacters = """
        @character lavender "Lavender"
        @character narrator "Narrator"
        @charimg lavender neutral assets/lavender.png
        @charimg lavender idle assets/lavender.png
        @charimg lavender talking assets/lavender.png
        @charimg lavender happy assets/lavender.png
        @charimg lavender emphasis assets/lavender.png
        """;
    new VnScriptParser().parse(
        new ByteArrayInputStream(rendered.getBytes(StandardCharsets.UTF_8)),
        "scaffold-test.vns",
        ignored -> new ByteArrayInputStream(sharedCharacters.getBytes(StandardCharsets.UTF_8)));
  }

  private static void assertWhiteBackground(String relativePath) throws Exception {
    String resourcePath = "com/jvn/editor/templates/new-project/demo-assets/" + relativePath;
    try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
      assertNotNull(in, "Missing background resource: " + resourcePath);
      BufferedImage image = ImageIO.read(in);
      assertNotNull(image, "Unreadable background resource: " + resourcePath);
      assertEquals(1920, image.getWidth(), "Unexpected background width: " + relativePath);
      assertEquals(1080, image.getHeight(), "Unexpected background height: " + relativePath);
      for (int y = 0; y < image.getHeight(); y++) {
        for (int x = 0; x < image.getWidth(); x++) {
          int rgb = image.getRGB(x, y);
          int red = (rgb >>> 16) & 0xff;
          int green = (rgb >>> 8) & 0xff;
          int blue = rgb & 0xff;
          assertTrue(red == 255 && green == 255 && blue == 255,
              "Non-white pixel in " + relativePath + " at " + x + "," + y);
        }
      }
    }
  }

  private static String loadTemplate(String relativePath) throws Exception {
    String resourcePath = "com/jvn/editor/templates/new-project/" + relativePath;
    try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
      assertNotNull(in, "Missing scaffold template resource: " + resourcePath);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static Map<String, String> buildTokens() {
    Map<String, String> tokens = new LinkedHashMap<>();
    tokens.put("PROJECT_NAME", "Test Project");
    tokens.put("SCENARIO_PREFIX", "test_project");
    tokens.put("CHARACTERS_INCLUDE", "/definitions/characters.vns");
    tokens.put("LAVENDER_EXPR", "idle");
    tokens.put("TUTORIAL_TARGET", "TutorialHub");
    tokens.put("BRANCH_TARGET", "BranchDemo");
    tokens.put("EPILOGUE_TARGET", "Epilogue");
    tokens.put("STORY_TUTORIAL_SCRIPT_PATH", "scripts/story/tutorial_hub.vns");
    tokens.put("STORY_BRANCH_SCRIPT_PATH", "scripts/story/branch_demo.vns");
    tokens.put("STORY_EPILOGUE_SCRIPT_PATH", "scripts/story/epilogue.vns");
    tokens.put("STORY_MAP_PATH", "config/story/story.storymap");
    tokens.put("BG_DECL", "@background field_day assets/demo/backgrounds/game.png\n\n");
    tokens.put("BG_START", "[bg field_day]\n");
    tokens.put("BG_TRANSITION", "[transition fade 400]\n");
    tokens.put("DIALOGUE_TARGET", "T01_Dialogue");
    tokens.put("NARRATION_TARGET", "T02_Narration");
    tokens.put("EXPRESSIONS_TARGET", "T03_Expressions");
    tokens.put("IMAGES_TARGET", "T04_Images");
    tokens.put("TRANSITIONS_TARGET", "T05_Transitions");
    tokens.put("AUDIO_TARGET", "T06_Audio");
    tokens.put("VARIABLES_TARGET", "T07_Variables");
    tokens.put("MOVEMENT_TARGET", "T08_Movement");
    tokens.put("PUPPETEER_TARGET", "T09_Puppeteer");
    tokens.put("MENUS_TARGET", "T10_Menus");
    tokens.put("SUBROUTINES_TARGET", "T11_Subroutines");
    tokens.put("BEST_PRACTICES_TARGET", "T12_BestPractices");
    tokens.put("CAMERA_TARGET", "T13_Camera");
    tokens.put("LOCALIZATION_TARGET", "T14_Localization");
    tokens.put("UI_LAYOUT_TARGET", "T15_UILayout");
    tokens.put("TESTING_RELEASE_TARGET", "T16_TestingRelease");
    tokens.put("VNS_JES_TARGET", "T17_VnsJes");
    tokens.put("JES_SCENE_PATH", "scripts/scenes/studio_tour.jes");
    tokens.put(
        "JES_BACKGROUND_ENTITY",
        "  entity \"background\" { component Panel2D { x: 0 y: 0 w: 1920 h: 1080 fill: rgb(1, 1, 1, 1) } }\n\n");
    tokens.put("HUB_TARGET", "TutorialHub");
    tokens.put("CHARACTERS_SCRIPT_PATH", "scripts/definitions/characters.vns");
    tokens.put("BG_CROSSFADE", "[transition crossfade 600 field_evening]\n");
    tokens.put("BGM_START", "[bgm \"assets/demo/audio/theme.mp3\"]\n");
    tokens.put("BGM_FADE", "[bgm_fadeout 1200]\n[wait 1300]\n");
    tokens.put("EXPRESSION_HINT", "Lavender: This project uses layered presets for composited expressions.");
    return tokens;
  }
}
