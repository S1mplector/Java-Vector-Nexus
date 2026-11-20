package com.jvn.editor.ui;

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
    copy(root, "guided-tutorial/panel.jes", "scripts/scenes/panel.jes");
    copy(root, "assets/tutorial/icon.png", "assets/icon.png");
    for (String file : List.of("white.png", "welcome.wav", "CaveatBrush-Regular.ttf", "CaveatBrush-OFL.txt",
        "neutral.png", "wave.png", "curious.png", "point.png", "surprised.png", "listen.png", "cheer.png",
        "present.png", "notes.png",
        "stride-a-right.png", "stride-a-left.png", "stride-b-right.png", "stride-b-left.png",
        "stride-c-right.png", "stride-c-left.png")) {
      String path = "assets/tutorial/" + file;
      copy(root, path, path);
    }
    for (String path : List.of("scripts/story/tutorial_hub.vns", "config/facets/tutorial_card.facet",
        "config/phone/phone.properties", "scripts/tutorial/README.md")) copy(root, path, path);
    for (var lesson : TutorialCatalog.LESSONS) copy(root, lesson.path(), lesson.path());
  }

  static void copyGuide(Path root) throws IOException {
    copy(root, "scripts/tutorial/README.md", "README.md");
  }

  static void installPresentation(Path root, int height, boolean menus) throws IOException {
    Path dialogue = root.resolve("config/ui/dialogue.layout");
    Files.writeString(dialogue, LayoutDslTemplates.minimalMonochromeDialogueLayoutTemplate());
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
    Files.writeString(root.resolve("config/locales/en.properties"), "\ntutorial.greeting=Hello from the English translation.\n",
        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    if (!menus) return;
    copy(root, "guided-tutorial/menu.theme", "config/menu/theme/menu.theme");
    copy(root, "guided-tutorial/main.layout", "config/menu/layouts/default.layout");
    for (String style : List.of("default", "submenu", "settings", "slot")) {
      copy(root, "guided-tutorial/tutorial.style", "config/menu/styles/" + style + ".style");
    }
    Path screen = root.resolve("config/menu/menus/main.menu");
    Properties properties = new Properties();
    try (var reader = Files.newBufferedReader(screen)) { properties.load(reader); }
    var items = new java.util.ArrayList<>(List.of(properties.getProperty("items").split(",")));
    items.remove("extras");
    properties.setProperty("items", String.join(",", items));
    properties.setProperty("titleText", "JVN Tutorial");
    properties.remove("subtitleText");
    properties.setProperty("item.new_game.label", "Start tutorial");
    properties.setProperty("item.continue.label", "Load game");
    properties.setProperty("backgroundAsset", "assets/tutorial/white.png");
    properties.setProperty("hintsText", "Arrow keys to move   /   Enter to open");
    try (var writer = Files.newBufferedWriter(screen, StandardCharsets.UTF_8)) {
      properties.store(writer, "JVN Tutorial");
    }
  }

  private static void copy(Path root, String resource, String target) throws IOException {
    Path destination = root.resolve(target);
    Files.createDirectories(destination.getParent());
    try (InputStream stream = GuidedTutorial.class.getResourceAsStream(ROOT + resource)) {
      if (stream == null) throw new IOException("Missing JVN Tutorial resource " + resource);
      Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
