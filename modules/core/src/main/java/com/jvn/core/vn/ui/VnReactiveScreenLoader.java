package com.jvn.core.vn.ui;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Loads reactive overlay screen definitions from project properties files.
 */
public final class VnReactiveScreenLoader {
  public record LoadResult(VnReactiveScreenSpec screen, List<String> diagnostics) {
    public LoadResult {
      diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
  }

  private record LoadedProperties(String path, Properties properties) {
  }

  private VnReactiveScreenLoader() {
  }

  public static LoadResult loadFromAssets(String id) {
    return load(new AssetCatalog(), id);
  }

  public static LoadResult load(AssetCatalog assets, String id) {
    List<String> diagnostics = new ArrayList<>();
    String screenId = normalize(id, null);
    if (screenId == null) {
      diagnostics.add("Reactive screen id is blank");
      return new LoadResult(null, diagnostics);
    }
    LoadedProperties loaded = loadFirstProperties(assets, diagnostics, screenPaths(screenId));
    if (loaded == null) {
      return new LoadResult(null, diagnostics);
    }
    return new LoadResult(parse(screenId, loaded.properties(), diagnostics, loaded.path()), diagnostics);
  }

  private static VnReactiveScreenSpec parse(String fallbackId, Properties p, List<String> diagnostics, String sourcePath) {
    String id = normalize(p.getProperty("id"), fallbackId);
    double x = parseDouble(p, "x", 0.18, diagnostics, sourcePath);
    double y = parseDouble(p, "y", 0.18, diagnostics, sourcePath);
    double width = parseDoubleAlias(p, "width", "w", 0.64, diagnostics, sourcePath);
    double height = parseDoubleAlias(p, "height", "h", 0.42, diagnostics, sourcePath);
    boolean callScreen = parseBoolean(p.getProperty("call"), false);
    List<VnReactiveScreenSpec.Button> buttons = parseButtons(id, p, diagnostics, sourcePath);
    return new VnReactiveScreenSpec(
        id,
        text(p, "title", id),
        text(p, "text", text(p, "body", "")),
        text(p, "visibleIf", ""),
        x,
        y,
        width,
        height,
        parseBoolean(p.getProperty("modal"), callScreen),
        parseBoolean(p.getProperty("dim"), parseBoolean(p.getProperty("dimBackground"), true)),
        parseBoolean(p.getProperty("dismiss"), parseBoolean(p.getProperty("dismissOnAdvance"), !callScreen)),
        callScreen,
        parseLong(p.getProperty("timer"), 0L, diagnostics, sourcePath, "timer"),
        text(p, "timerAction", callScreen ? "return" : "hide"),
        text(p, "timerTarget", ""),
        text(p, "returnKey", "screen.return." + id),
        buttons,
        parseFacet(p, diagnostics, sourcePath)
    );
  }

  private static VnFacetSpec parseFacet(Properties p, List<String> diagnostics, String sourcePath) {
    List<String> ids = parseCsv(p.getProperty("nodes"));
    if (ids.isEmpty()) {
      Set<String> found = new LinkedHashSet<>();
      for (String key : p.stringPropertyNames()) {
        if (!key.startsWith("node.")) continue;
        int dot = key.indexOf('.', "node.".length());
        if (dot > "node.".length()) found.add(key.substring("node.".length(), dot));
      }
      ids = new ArrayList<>(found);
    }
    if (ids.isEmpty()) return null;
    List<VnFacetSpec.Node> nodes = new ArrayList<>();
    for (String id : ids) {
      String prefix = "node." + id + ".";
      String typeRaw = text(p, prefix + "type", "text");
      VnFacetSpec.Type type = VnFacetSpec.Type.parse(typeRaw);
      if (!type.name().equalsIgnoreCase(typeRaw)) {
        diagnostics.add("Unknown facet node type '" + typeRaw + "' for " + id + " in " + sourcePath);
      }
      nodes.add(new VnFacetSpec.Node(
          id, type, text(p, prefix + "parent", "root"),
          text(p, prefix + "text", ""), text(p, prefix + "value", ""),
          text(p, prefix + "visibleIf", ""),
          parseDouble(p, prefix + "x", 0.0, diagnostics, sourcePath),
          parseDouble(p, prefix + "y", 0.0, diagnostics, sourcePath),
          parseDouble(p, prefix + "width", 1.0, diagnostics, sourcePath),
          parseDouble(p, prefix + "height", 0.1, diagnostics, sourcePath),
          text(p, prefix + "source", ""), text(p, prefix + "region", "")
      ));
    }
    return new VnFacetSpec(text(p, "root", "root"), nodes);
  }

  private static List<VnReactiveScreenSpec.Button> parseButtons(
      String screenId,
      Properties p,
      List<String> diagnostics,
      String sourcePath
  ) {
    List<String> ids = parseCsv(first(p.getProperty("buttons"), p.getProperty("items")));
    if (ids.isEmpty()) ids = collectButtonIds(p);
    List<VnReactiveScreenSpec.Button> buttons = new ArrayList<>();
    int index = 0;
    int count = Math.max(1, ids.size());
    double buttonWidth = Math.min(0.28, Math.max(0.18, 0.82 / count));
    double gap = 0.03;
    double totalWidth = count * buttonWidth + Math.max(0, count - 1) * gap;
    double startX = Math.max(0.06, (1.0 - totalWidth) * 0.5);
    for (String rawId : ids) {
      String id = normalize(rawId, null);
      if (id == null) continue;
      String prefix = "button." + id + ".";
      buttons.add(new VnReactiveScreenSpec.Button(
          id,
          text(p, prefix + "label", id),
          text(p, prefix + "action", "noop"),
          text(p, prefix + "target", ""),
          parseBoolean(p.getProperty(prefix + "enabled"), true),
          text(p, prefix + "enabledIf", ""),
          text(p, prefix + "visibleIf", ""),
          parseDouble(p, prefix + "x", startX + index * (buttonWidth + gap), diagnostics, sourcePath),
          parseDouble(p, prefix + "y", 0.74, diagnostics, sourcePath),
          parseDouble(p, prefix + "width", buttonWidth, diagnostics, sourcePath),
          parseDouble(p, prefix + "height", 0.16, diagnostics, sourcePath),
          text(p, prefix + "space", text(p, prefix + "coordinateSpace", "screen"))
      ));
      index++;
    }
    return buttons;
  }

  private static LoadedProperties loadFirstProperties(AssetCatalog assets, List<String> diagnostics, String... paths) {
    if (assets == null || paths == null) return null;
    for (String path : paths) {
      if (path == null || path.isBlank()) continue;
      for (AssetType type : new AssetType[] {AssetType.CONFIG, AssetType.SCRIPT, AssetType.OTHER}) {
        try {
          if (!assets.exists(type, path)) continue;
          try (InputStream in = assets.open(type, path)) {
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return new LoadedProperties(path, props);
          }
        } catch (Exception ex) {
          diagnostics.add("Failed to load reactive screen '" + path + "': " + ex.getMessage());
        }
      }
    }
    return null;
  }

  private static String[] screenPaths(String id) {
    return new String[] {
        "config/facets/" + id + ".facet",
        "config/screens/" + id + ".screen",
        "config/screens/" + id + ".properties",
        "screens/" + id + ".screen",
        "screens/" + id + ".properties",
        id + ".screen",
        id + ".properties",
        id + ".facet"
    };
  }

  private static List<String> collectButtonIds(Properties p) {
    Set<String> ids = new LinkedHashSet<>();
    for (String key : p.stringPropertyNames()) {
      if (!key.startsWith("button.")) continue;
      int dot = key.indexOf('.', "button.".length());
      if (dot <= "button.".length()) continue;
      String id = key.substring("button.".length(), dot).trim();
      if (!id.isEmpty()) ids.add(id);
    }
    return new ArrayList<>(ids);
  }

  private static List<String> parseCsv(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    List<String> out = new ArrayList<>();
    for (String part : raw.split(",")) {
      String value = normalize(part, null);
      if (value != null) out.add(value);
    }
    return out;
  }

  private static String text(Properties p, String key, String fallback) {
    String value = p.getProperty(key);
    return value == null ? fallback : value.trim();
  }

  private static String first(String a, String b) {
    return a != null && !a.isBlank() ? a : b;
  }

  private static double parseDouble(Properties p, String key, double fallback, List<String> diagnostics, String sourcePath) {
    String raw = p.getProperty(key);
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException ex) {
      diagnostics.add("Invalid number for '" + key + "' in " + sourcePath + ": " + raw);
      return fallback;
    }
  }

  private static double parseDoubleAlias(
      Properties p,
      String primary,
      String alias,
      double fallback,
      List<String> diagnostics,
      String sourcePath
  ) {
    if (p.getProperty(primary) != null) {
      return parseDouble(p, primary, fallback, diagnostics, sourcePath);
    }
    return parseDouble(p, alias, fallback, diagnostics, sourcePath);
  }

  private static long parseLong(String raw, long fallback, List<String> diagnostics, String sourcePath, String key) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException ex) {
      diagnostics.add("Invalid integer for '" + key + "' in " + sourcePath + ": " + raw);
      return fallback;
    }
  }

  private static boolean parseBoolean(String raw, boolean fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    String value = raw.trim().toLowerCase(Locale.ROOT);
    return switch (value) {
      case "true", "on", "yes", "1" -> true;
      case "false", "off", "no", "0" -> false;
      default -> fallback;
    };
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? fallback : trimmed;
  }
}
