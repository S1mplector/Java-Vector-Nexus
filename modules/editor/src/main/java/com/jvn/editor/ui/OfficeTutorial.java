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

/** Installs the First Shift course into a newly created project. */
final class OfficeTutorial {
  private static final String ROOT = "/com/jvn/editor/templates/new-project/";
  private OfficeTutorial() {}

  static void installContent(Path root) throws IOException {
    for (String file : List.of("prologue", "branch_demo", "epilogue")) {
      copy(root, "first-shift/" + file + ".vns", "scripts/story/" + file + ".vns");
    }
    copy(root, "first-shift/characters.vns", "scripts/definitions/characters.vns");
    copy(root, "assets/tutorial/dusk.stagepreset", "scripts/definitions/dusk.stagepreset");
    copy(root, "first-shift/office.jes", "scripts/scenes/office_board.jes");
    copy(root, "assets/tutorial/advance.png", "assets/ui/dialogue/ctc_marker.png");
    copy(root, "assets/tutorial/icon.png", "assets/icon.png");
    for (String file : List.of("office.jpg", "title.jpg", "welcome.wav",
        "neutral.png", "wave.png", "curious.png", "point.png", "surprised.png", "listen.png", "cheer.png",
        "present.png", "notes.png",
        "stride-a-right.png", "stride-a-left.png", "stride-b-right.png", "stride-b-left.png",
        "stride-c-right.png", "stride-c-left.png", "button.png", "button-active.png", "dialogue.png", "nameplate.png", "stationery.png")) {
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
    copy(root, "first-shift/dialogue.layout", "config/ui/dialogue.layout");
    float scale = height / 720f;
    Files.writeString(dialogue, String.format(Locale.ROOT,
        "dialogueTextFontSize=%d%nnameTextFontSize=%d%nchoiceFontSize=%d%nchoiceHeight=%d%n",
        Math.max(16, Math.round(25 * scale)), Math.max(14, Math.round(19 * scale)),
        Math.max(14, Math.round(20 * scale)), Math.max(26, Math.round(39 * scale))),
        java.nio.file.StandardOpenOption.APPEND);
    Files.writeString(root.resolve("config/locales/en.properties"), "\noffice.signoff=See you tomorrow, Pip.\n",
        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    if (!menus) return;
    copy(root, "first-shift/menu.theme", "config/menu/theme/menu.theme");
    copy(root, "first-shift/main.layout", "config/menu/layouts/default.layout");
    for (String style : List.of("default", "submenu", "settings", "slot")) {
      copy(root, "first-shift/office.style", "config/menu/styles/" + style + ".style");
    }
    Path screen = root.resolve("config/menu/menus/main.menu");
    Properties properties = new Properties();
    try (var reader = Files.newBufferedReader(screen)) { properties.load(reader); }
    double rowY = 0.38;
    for (String id : properties.getProperty("items").split(",")) {
      String prefix = "item." + id.trim() + ".bounds";
      properties.setProperty(prefix + "X", "0.10");
      properties.setProperty(prefix + "Y", String.format(Locale.ROOT, "%.3f", rowY));
      properties.setProperty(prefix + "Width", "0.31");
      properties.setProperty(prefix + "Height", "0.061");
      rowY += 0.079;
    }
    properties.setProperty("titleText", "First Shift");
    properties.setProperty("subtitleText", "A JVN tutorial with Pip");
    properties.setProperty("item.new_game.label", "Start your shift");
    properties.setProperty("item.continue.label", "Resume a shift");
    properties.setProperty("backgroundAsset", "assets/tutorial/title.jpg");
    properties.setProperty("hintsText", "Arrow keys to move   /   Enter to open");
    try (var writer = Files.newBufferedWriter(screen, StandardCharsets.UTF_8)) {
      properties.store(writer, "First Shift");
    }
  }

  private static void copy(Path root, String resource, String target) throws IOException {
    Path destination = root.resolve(target);
    Files.createDirectories(destination.getParent());
    try (InputStream stream = OfficeTutorial.class.getResourceAsStream(ROOT + resource)) {
      if (stream == null) throw new IOException("Missing First Shift resource " + resource);
      Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
