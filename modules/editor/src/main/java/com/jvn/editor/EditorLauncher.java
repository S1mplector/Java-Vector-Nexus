package com.jvn.editor;

import java.awt.Toolkit;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Registers the desktop identity before starting the JavaFX toolkit. */
public final class EditorLauncher {
  static final String NAME = "JVN Editor";
  static final String ICON_RESOURCE = "/com/jvn/editor/images/jvn_editor_icon.png";

  private EditorLauncher() {}

  public static void main(String[] args) {
    if (isMacOs()) {
      System.setProperty("apple.awt.application.name", NAME);
      setMacIconPath();
      // Once Glass starts its own NSApplication, AWT can change the icon but not the Dock name.
      Toolkit.getDefaultToolkit();
    }
    EditorApp.main(args);
  }

  static boolean isMacOs() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
  }

  private static void setMacIconPath() {
    URL icon = EditorLauncher.class.getResource(ICON_RESOURCE);
    if (icon == null) return;
    try {
      Path iconPath;
      if ("file".equals(icon.getProtocol())) {
        iconPath = Path.of(icon.toURI());
      } else {
        iconPath = Files.createTempFile("jvn-editor-", ".png");
        iconPath.toFile().deleteOnExit();
        try (InputStream input = icon.openStream()) {
          Files.copy(input, iconPath, StandardCopyOption.REPLACE_EXISTING);
        }
      }
      System.setProperty("apple.awt.application.icon", iconPath.toString());
    } catch (IOException | URISyntaxException ignored) {
      // EditorApp applies the embedded image once JavaFX is running.
    }
  }
}
