package com.jvn.editor.ui;

import com.jvn.fx.menu.MenuTheme;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Installs the JVN Tutorial course into a newly created project. */
final class GuidedTutorial {
  private static final String ROOT = "/com/jvn/editor/templates/new-project/";
  private GuidedTutorial() {}

  static void installContent(Path root) throws IOException {
    copy(root, "guided-tutorial/characters.vns", "scripts/definitions/characters.vns");
    copy(root, "assets/tutorial/dusk.stagepreset", "scripts/definitions/dusk.stagepreset");
    copy(root, "guided-tutorial/collider.jes", "scripts/scenes/collider.jes");
    copy(root, "guided-tutorial/panel.jes", "scripts/scenes/panel.jes");
    for (String file : List.of("white.png", "welcome.wav", "CaveatBrush-Regular.ttf", "CaveatBrush-OFL.txt",
        "ginger_angry.png", "ginger_annoyed.png", "ginger_confused.png", "ginger_embarrassed.png",
        "ginger_normal.png", "ginger_sad.png", "ginger_shy.png", "ginger_thoughtful.png")) {
      String path = "assets/tutorial/" + file;
      copy(root, path, path);
    }
    copyResource(root, "/" + MenuTheme.TUTORIAL_TITLE_ART, MenuTheme.TUTORIAL_TITLE_ART);
    copyResource(root, "/" + MenuTheme.TUTORIAL_TITLE_MUSIC, MenuTheme.TUTORIAL_TITLE_MUSIC);
    for (String id : List.of("new_game", "continue", "settings", "quit")) {
      String path = MenuTheme.tutorialDockIcon(id);
      copyResource(root, "/" + path, path);
    }
    Path portrait = root.resolve("assets/tutorial/ginger_normal.png");
    var image = javax.imageio.ImageIO.read(portrait.toFile());
    int size = Math.min(image.getWidth(), image.getHeight());
    javax.imageio.ImageIO.write(image.getSubimage((image.getWidth() - size) / 2, 0, size, size),
        "png", root.resolve("assets/icon.png").toFile());
    for (String path : List.of("config/facets/tutorial_card.facet", "config/phone/phone.properties"))
      copy(root, path, path);
    copyCourse(root, "config/facets/tutorial_code.facet");
    copyCourse(root, "scripts/story/tutorial_hub.vns");
    copyCourse(root, "scripts/tutorial/README.md");
    for (var lesson : TutorialCatalog.LESSONS) copyCourse(root, lesson.path());
  }

  private static void copyCourse(Path root, String target) throws IOException {
    copy(root, "guided-tutorial/" + target, target);
  }

  static void copyGuide(Path root) throws IOException {
    copy(root, "guided-tutorial/scripts/tutorial/README.md", "README.md");
  }

  static void installPresentation(Path root, int height, boolean menus) throws IOException {
    Path dialogue = root.resolve("config/ui/dialogue.layout");
    Files.writeString(dialogue, LayoutDslTemplates.minimalMonochromeDialogueLayoutTemplate()
        .replace("dialogueTextHorizontalPadding=22\n", "# region padding\ndialogueTextHorizontalPadding=22\n# endregion\n"));
    float scale = height / 720f;
    Files.writeString(dialogue, String.format(Locale.ROOT,
        "textBoxColor=#000000B8%ntextBoxOpacity=0.72%nnameBoxColor=#000000B8%nnameTextColor=#FFFFFF%n"
            + "textBoxY=0.74%ntextBoxHeight=0.22%ndialogueTextColor=#FFFFFF%ncharacterHeightFactor=0.90%n"
            + "choiceYStart=0.10%ndialogueTextRightPadding=54%nnameBoxWidth=%d%nnameBoxHeight=%d%nnameBoxYOffset=-%d%n"
            + "nameTextBaselineOffset=%d%ndialogueTextTopPadding=%d%ndialogueTextFontSize=%d%nnameTextFontSize=%d%nchoiceFontSize=%d%nchoiceHeight=%d%n",
        Math.round(160 * scale), Math.round(32 * scale), Math.round(32 * scale), Math.round(22 * scale),
        Math.round(24 * scale), Math.max(18, Math.round(24 * scale)), Math.max(14, Math.round(18 * scale)),
        Math.max(14, Math.round(20 * scale)), Math.max(26, Math.round(39 * scale))),
        java.nio.file.StandardOpenOption.APPEND);
    Files.writeString(root.resolve("config/locales/en.properties"), "\n# region greeting\ntutorial.greeting=This line comes from the English locale file.\n# endregion\n"
            + "load.no_saves=No saved games yet.\nload.no_preview=No thumbnail\n",
        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    if (!menus) return;
    for (String menu : List.of("save", "load")) {
      String target = "config/menu/menus/" + menu + ".menu";
      if (Files.exists(root.resolve(target))) copy(root, "guided-tutorial/" + menu + ".menu", target);
    }
    copy(root, "guided-tutorial/menu.theme", "config/menu/theme/menu.theme");
    copy(root, "guided-tutorial/main.layout", "config/menu/layouts/default.layout");
    for (String layout : List.of("settings", "slots", "submenu")) {
      copy(root, "guided-tutorial/" + layout + ".layout", "config/menu/layouts/" + layout + ".layout");
    }
    copy(root, "guided-tutorial/pause.menu", "config/menu/menus/pause.menu");
    Path registry = root.resolve("config/menu/registry/menu.registry");
    Properties menuRegistry = new Properties();
    try (var reader = Files.newBufferedReader(registry)) { menuRegistry.load(reader); }
    menuRegistry.setProperty("menus", menuRegistry.getProperty("menus") + ",pause");
    try (var writer = Files.newBufferedWriter(registry)) { menuRegistry.store(writer, "Tutorial menus"); }
    for (String style : List.of("default", "submenu", "settings", "slot")) {
      copy(root, "guided-tutorial/tutorial.style", "config/menu/styles/" + style + ".style");
    }
    Path screen = root.resolve("config/menu/menus/main.menu");
    Properties properties = new Properties();
    try (var reader = Files.newBufferedReader(screen)) { properties.load(reader); }
    // The generic scaffold places list rows explicitly. The tutorial dock lays out
    // its own cards, including their hover targets, at the current viewport size.
    properties.stringPropertyNames().stream()
        .filter(key -> key.startsWith("item.") && (
            key.endsWith(".boundsX") || key.endsWith(".boundsY")
            || key.endsWith(".boundsWidth") || key.endsWith(".boundsHeight")))
        .forEach(properties::remove);
    var items = new java.util.ArrayList<>(List.of(properties.getProperty("items").split(",")));
    items.remove("extras");
    properties.setProperty("items", String.join(",", items));
    properties.setProperty("titleText", "Tutorial");
    properties.setProperty("subtitleText", "");
    properties.setProperty("item.new_game.label", "Start tutorial");
    properties.setProperty("item.continue.label", "Load game");
    properties.setProperty("backgroundAsset", MenuTheme.TUTORIAL_TITLE_ART);
    properties.setProperty("hintsText", "");
    try (var writer = Files.newBufferedWriter(screen, StandardCharsets.UTF_8)) {
      properties.store(writer, "Tutorial");
      writer.write("\n# Custom SVG icons: keep the SVG in assets/ and point item.<id>.icon to it.\n"
          + "# A square viewBox leaves room for the icon above the label. The menu action is set separately.\n");
      for (String id : items) {
        String icon = MenuTheme.tutorialDockIcon(id);
        if (icon != null) writer.write("item." + id + ".icon=" + icon + "\n");
      }
    }
  }

  private static void copy(Path root, String resource, String target) throws IOException {
    copyResource(root, ROOT + resource, target);
  }

  private static void copyResource(Path root, String resource, String target) throws IOException {
    Path destination = root.resolve(target);
    Files.createDirectories(destination.getParent());
    try (InputStream stream = GuidedTutorial.class.getResourceAsStream(resource)) {
      if (stream == null) throw new IOException("Missing JVN Tutorial resource " + resource);
      Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
