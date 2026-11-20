package com.jvn.hub;

import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Desktop branding for the Hub, including windows shown before its main frame. */
final class HubApplicationIcon {
  static final String NAME = "JVN Engine Hub";
  private static final String ICON_RESOURCE = "images/jvn_hub_icon.png";

  private HubApplicationIcon() {}

  static void configureMacApplication() {
    if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac")) return;
    System.setProperty("apple.awt.application.name", NAME);
    URL icon = HubApplicationIcon.class.getResource(ICON_RESOURCE);
    if (icon == null) return;
    try {
      Path iconPath;
      if ("file".equals(icon.getProtocol())) {
        iconPath = Path.of(icon.toURI());
      } else {
        // AWT's native bootstrap needs a file even when the icon is bundled in a jar.
        iconPath = Files.createTempFile("jvn-engine-hub-", ".png");
        iconPath.toFile().deleteOnExit();
        try (InputStream input = icon.openStream()) {
          Files.copy(input, iconPath, StandardCopyOption.REPLACE_EXISTING);
        }
      }
      System.setProperty("apple.awt.application.icon", iconPath.toString());
    } catch (IOException | java.net.URISyntaxException ignored) {
      // The taskbar can still use the bundled image once AWT is running.
    }
  }

  static void applyTo(Window window) {
    window.setIconImages(Artwork.WINDOW_ICONS);
  }

  static void installTaskbarIcon() {
    try {
      if (Artwork.IMAGE != null && Taskbar.isTaskbarSupported()) {
        Taskbar taskbar = Taskbar.getTaskbar();
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) taskbar.setIconImage(Artwork.IMAGE);
      }
    } catch (UnsupportedOperationException | SecurityException ignored) {
      // The window icons cover desktops that provide no process-level icon API.
    }
  }

  private static final class Artwork {
    private static final BufferedImage IMAGE = loadImage();
    private static final List<Image> WINDOW_ICONS = windowIcons();

    private static BufferedImage loadImage() {
      try (InputStream input = HubApplicationIcon.class.getResourceAsStream(ICON_RESOURCE)) {
        return input == null ? null : ImageIO.read(input);
      } catch (IOException ignored) {
        return null;
      }
    }

    private static List<Image> windowIcons() {
      if (IMAGE == null) return List.of();
      List<Image> sizes = new ArrayList<>();
      for (int size : new int[] {16, 24, 32, 48, 64, 128, 256}) {
        sizes.add(IMAGE.getScaledInstance(size, size, Image.SCALE_AREA_AVERAGING));
      }
      sizes.add(IMAGE);
      return List.copyOf(sizes);
    }
  }
}
