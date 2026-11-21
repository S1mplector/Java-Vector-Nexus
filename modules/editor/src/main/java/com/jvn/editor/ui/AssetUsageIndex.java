package com.jvn.editor.ui;

import com.jvn.core.assets.AssetType;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.vn.VnArgTokenizer;
import com.jvn.editor.ui.AssetAutoLabelService.AssetKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Literal media references used by the project, independent of VNS declaration namespaces. */
final class AssetUsageIndex {
  private final Path root;
  private final FilesystemAssetManager assets;
  private final Map<String, Map<AssetKind, String>> uses = new HashMap<>();
  private final Map<String, String> resolved = new HashMap<>();

  AssetUsageIndex(Path root) {
    this.root = root;
    assets = new FilesystemAssetManager(root);
  }

  Map<AssetKind, String> uses(String path) { return uses.getOrDefault(path, Map.of()); }

  void readCommand(String raw, Path file, int lineNumber) {
    String line = raw.strip();
    if (!line.startsWith("[")) return;
    int close = line.lastIndexOf(']');
    if (close < 1) return;
    List<String> args = VnArgTokenizer.tokenize(line.substring(1, close));
    if (args.size() < 2) return;
    String command = args.getFirst().toLowerCase(Locale.ROOT);
    AssetKind kind = switch (command) {
      case "sfx" -> AssetKind.SOUND_EFFECT;
      case "voice" -> AssetKind.VOICE;
      case "bgm", "bgm_crossfade" -> AssetKind.MUSIC;
      default -> null;
    };
    if (kind == null) return;
    for (String arg : args.subList(1, args.size())) {
      int eq = arg.indexOf('=');
      if (eq >= 0 && !Set.of("path", "track", "id", "file").contains(arg.substring(0, eq).toLowerCase(Locale.ROOT))) continue;
      String path = arg.substring(eq + 1);
      if (!"Audio".equals(AssetPathHeuristics.mediaType(path))) continue;
      add(path, kind, AssetType.AUDIO, "[" + command + "] in " + root.relativize(file) + ":" + lineNumber);
      return;
    }
  }

  static boolean isConfiguration(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.equals("jvn.project") || name.endsWith(".properties") || name.endsWith(".theme")
        || name.endsWith(".style") || name.endsWith(".menu") || name.endsWith(".layout");
  }

  void readConfigurations() throws IOException {
    readConfiguration(root.resolve("jvn.project"));
    for (String folder : List.of("config", "game/config")) {
      Path base = root.resolve(folder);
      if (!Files.isDirectory(base)) continue;
      try (var paths = Files.walk(base, 16)) {
        for (Path file : paths.filter(Files::isRegularFile).filter(AssetUsageIndex::isConfiguration).sorted().toList()) {
          readConfiguration(file);
        }
      }
    }
  }

  private void readConfiguration(Path file) throws IOException {
    if (!Files.isRegularFile(file)) return;
    Properties props = new Properties();
    try (var reader = Files.newBufferedReader(file)) {
      props.load(reader);
    } catch (IllegalArgumentException malformed) { return; }
    for (String key : props.stringPropertyNames().stream().sorted().toList()) {
      String path = props.getProperty(key).strip();
      String role = key.toLowerCase(Locale.ROOT);
      String media = AssetPathHeuristics.mediaType(path);
      AssetKind kind;
      if (media.equals("Font") && (role.endsWith("fontfamily") || role.endsWith("fontpath"))) kind = AssetKind.FONT;
      else if (media.equals("Image") && role.matches("profile\\.[^.]+\\.icon")) kind = AssetKind.PROJECT_ICON;
      else if (media.equals("Image") && Set.of("backgroundasset", "backgroundimage").contains(role)) kind = AssetKind.BACKGROUND;
      else if (media.equals("Image") && role.endsWith(".icon")) kind = AssetKind.UI;
      else continue;
      add(path, kind, kind == AssetKind.FONT ? AssetType.FONT : AssetType.IMAGE, key + " in " + root.relativize(file));
    }
  }

  private void add(String raw, AssetKind kind, AssetType type, String reason) {
    String relative = resolve(raw, type);
    if (!relative.isEmpty()) uses.computeIfAbsent(relative, ignored -> new EnumMap<>(AssetKind.class)).putIfAbsent(kind, reason);
  }

  String resolve(String raw, AssetType type) {
    return resolved.computeIfAbsent(type + "/" + raw, ignored -> {
      try {
        if (Path.of(raw).isAbsolute() || raw.contains("${")) return "";
        var url = assets.url(type, raw);
        if (url == null) return "";
        Path target = Path.of(url.toURI()).normalize();
        return target.startsWith(root) ? AssetPathHeuristics.relative(root, target) : "";
      } catch (IllegalArgumentException | java.net.URISyntaxException ex) { return ""; }
    });
  }
}
