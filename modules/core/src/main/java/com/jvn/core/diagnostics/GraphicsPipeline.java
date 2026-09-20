package com.jvn.core.diagnostics;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Configures the JavaFX Prism rendering preference before the JavaFX toolkit starts.
 *
 * <p>Set {@code -Djvn.graphics.mode=auto|hardware|software} or the
 * {@code JVN_GRAPHICS_MODE} environment variable. Hardware mode keeps the software pipeline as a
 * final fallback so a driver problem cannot make JVN unusable. Advanced Prism tuning is loaded
 * from {@code ~/.jvn-editor/render-pipeline.properties} before the toolkit initializes.</p>
 */
public final class GraphicsPipeline {
  public static final String MODE_PROPERTY = "jvn.graphics.mode";
  public static final String MODE_ENVIRONMENT = "JVN_GRAPHICS_MODE";
  static final String SETTINGS_FILE_PROPERTY = "jvn.graphics.settingsFile";
  static final String PRISM_ORDER_PROPERTY = "prism.order";
  static final String PRISM_FORCE_GPU_PROPERTY = "prism.forceGPU";
  static final String PRISM_VSYNC_PROPERTY = "prism.vsync";
  static final String PRISM_DIRTY_REGIONS_PROPERTY = "prism.dirtyopts";
  static final String PRISM_OCCLUSION_CULLING_PROPERTY = "prism.occlusion.culling";
  static final String PRISM_SHAPE_CACHE_PROPERTY = "prism.cacheshapes";
  static final String PRISM_VERBOSE_PROPERTY = "prism.verbose";
  static final String PRISM_SHOW_DIRTY_PROPERTY = "prism.showdirty";
  static final String PRISM_SHOW_OVERDRAW_PROPERTY = "prism.showoverdraw";
  static final String PRISM_PRINT_RENDER_GRAPH_PROPERTY = "prism.printrendergraph";
  static final String JAVAFX_PULSE_LOGGER_PROPERTY = "javafx.pulseLogger";

  public enum Mode {
    AUTO,
    HARDWARE,
    SOFTWARE;

    public static Mode parse(String value) {
      if (value == null || value.isBlank()) return AUTO;
      return switch (value.trim().toLowerCase(Locale.ROOT)) {
        case "gpu", "hardware", "accelerated", "prefer-gpu" -> HARDWARE;
        case "sw", "software", "compatibility" -> SOFTWARE;
        default -> AUTO;
      };
    }
  }

  private GraphicsPipeline() {
  }

  /**
   * Apply the requested pipeline preference. Call this before {@code Application.launch()}.
   *
   * @return the normalized requested mode
   */
  public static Mode configure() {
    applyUserTuning();
    configureRenderGraphRoots();
    Mode mode = requestedMode();
    // Launch settings are immutable for this process; the HUD must not reread disk every frame.
    System.setProperty(MODE_PROPERTY, mode.name().toLowerCase(Locale.ROOT));
    if (System.getProperty(PRISM_ORDER_PROPERTY) == null) {
      switch (mode) {
        case HARDWARE -> System.setProperty(PRISM_ORDER_PROPERTY, preferredHardwareOrder());
        case SOFTWARE -> System.setProperty(PRISM_ORDER_PROPERTY, "sw");
        case AUTO -> {
          // Leave Prism untouched so JavaFX can select its platform default.
        }
      }
    }
    return mode;
  }

  public static Mode requestedMode() {
    String property = System.getProperty(MODE_PROPERTY);
    if (property != null && !property.isBlank()) return Mode.parse(property);
    String environment = System.getenv(MODE_ENVIRONMENT);
    if (environment != null && !environment.isBlank()) return Mode.parse(environment);
    return Mode.parse(readUserPreference());
  }

  /** A compact description suitable for logs and the runtime performance HUD. */
  public static String statusText() {
    Mode mode = requestedMode();
    String order = System.getProperty(PRISM_ORDER_PROPERTY);
    return switch (mode) {
      case HARDWARE -> "GPU preferred (" + cleanOrder(order, preferredHardwareOrder()) + ")";
      case SOFTWARE -> "Software (" + cleanOrder(order, "sw") + ")";
      case AUTO -> order == null || order.isBlank()
          ? "Auto (JavaFX)"
          : "Auto (" + order.trim() + ")";
    };
  }

  static String preferredHardwareOrder() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("win")) return "d3d,sw";
    if (os.contains("mac")) return "es2,sw";
    return "es2,sw";
  }

  private static String cleanOrder(String order, String fallback) {
    return order == null || order.isBlank() ? fallback : order.trim();
  }

  private static void applyUserTuning() {
    Path file = renderSettingsFile();
    if (!Files.isRegularFile(file)) return;
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
    } catch (Exception ignored) {
      return;
    }
    applyBooleanSetting(properties, "render.vsync", PRISM_VSYNC_PROPERTY);
    applyBooleanSetting(properties, "render.dirtyRegions", PRISM_DIRTY_REGIONS_PROPERTY);
    applyBooleanSetting(properties, "render.occlusionCulling", PRISM_OCCLUSION_CULLING_PROPERTY);
    applyStringSetting(properties, "render.shapeCache", PRISM_SHAPE_CACHE_PROPERTY);
    if (System.getProperty("jvn.editor.previewMaxFps") == null) {
      try {
        int fps = Integer.parseInt(properties.getProperty("render.previewMaxFps", "0").trim());
        if (fps >= 15 && fps <= 240) System.setProperty("jvn.editor.previewMaxFps", Integer.toString(fps));
      } catch (NumberFormatException ignored) {
        // Malformed settings retain automatic frame pacing.
      }
    }
    applyBooleanSetting(properties, "diagnostics.verbose", PRISM_VERBOSE_PROPERTY);
    applyBooleanSetting(properties, "diagnostics.showDirtyRegions", PRISM_SHOW_DIRTY_PROPERTY);
    applyBooleanSetting(properties, "diagnostics.showOverdraw", PRISM_SHOW_OVERDRAW_PROPERTY);
    applyBooleanSetting(properties, "diagnostics.printRenderGraph", PRISM_PRINT_RENDER_GRAPH_PROPERTY);
  }

  private static void configureRenderGraphRoots() {
    if (Boolean.parseBoolean(System.getProperty(PRISM_PRINT_RENDER_GRAPH_PROPERTY, "false"))) {
      // ViewPainter derives the printable tree from dirty-region render roots.
      System.setProperty(PRISM_DIRTY_REGIONS_PROPERTY, "true");
    }
  }

  private static Path renderSettingsFile() {
    String override = System.getProperty(SETTINGS_FILE_PROPERTY);
    if (override != null && !override.isBlank()) return Path.of(override.trim());
    return Path.of(
        System.getProperty("user.home", "."),
        ".jvn-editor",
        "render-pipeline.properties");
  }

  private static void applyBooleanSetting(
      Properties properties,
      String preferenceKey,
      String systemProperty) {
    if (System.getProperty(systemProperty) != null) return;
    String value = properties.getProperty(preferenceKey);
    if (value == null || value.isBlank()) return;
    switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "true", "1", "yes", "on", "enabled" -> System.setProperty(systemProperty, "true");
      case "false", "0", "no", "off", "disabled" -> System.setProperty(systemProperty, "false");
      default -> {
        // Ignore malformed user settings and preserve JavaFX defaults.
      }
    }
  }

  private static void applyStringSetting(
      Properties properties,
      String preferenceKey,
      String systemProperty) {
    if (System.getProperty(systemProperty) != null) return;
    String value = properties.getProperty(preferenceKey);
    if (value == null || value.isBlank()) return;
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("all")
        || normalized.equals("true")
        || normalized.equals("complex")
        || normalized.equals("false")) {
      System.setProperty(systemProperty, normalized);
    }
  }

  private static String readUserPreference() {
    Path file = Path.of(
        System.getProperty("user.home", "."),
        ".jvn-editor",
        "editor-preferences.properties");
    if (!Files.isRegularFile(file)) return "";
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
      return properties.getProperty("graphics.mode", "");
    } catch (Exception ignored) {
      return "";
    }
  }
}
