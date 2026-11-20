package com.jvn.fx;

import com.jvn.core.diagnostics.RunTelemetry;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class VnCacheInventory {
  private static final Set<String> FORMATS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
  private VnCacheInventory() {}

  static void write(Path session, Path project, Map<String, List<String>> resident) throws IOException {
    var report = new StringBuilder();
    var sources = new HashSet<Path>();
    var assets = new com.jvn.core.assets.FilesystemAssetManager(project);
    for (var cache : resident.entrySet()) {
      report.append(cache.getKey()).append(" (").append(cache.getValue().size()).append(" resident)\n");
      for (String key : cache.getValue()) {
        report.append("  ").append(key).append('\n');
        if (project != null && (cache.getKey().equals("Sprite / UI") || cache.getKey().equals("Background"))) {
          var url = assets.url(com.jvn.core.assets.AssetType.IMAGE, key);
          if (url != null) {
            try { sources.add(Path.of(url.toURI()).toAbsolutePath().normalize()); }
            catch (java.net.URISyntaxException ex) { throw new IOException("Invalid asset URL", ex); }
          }
        }
      }
      report.append('\n');
    }
    report.append("Project images outside the VN source caches\n");
    if (project != null) {
      for (String dir : List.of("assets", "images", "game/assets", "game/images")) {
        Path root = project.resolve(dir);
        if (!Files.isDirectory(root)) continue;
        try (var paths = Files.walk(root)) {
          paths.filter(Files::isRegularFile).filter(p -> !sources.contains(p.toAbsolutePath().normalize()))
              .filter(p -> {
                String name = p.getFileName().toString();
                return FORMATS.contains(name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
              }).sorted().forEach(p -> report.append("  ").append(project.relativize(p)).append('\n'));
        }
      }
    }
    Files.writeString(session.resolve(RunTelemetry.INVENTORY_REPORT), report);
  }
}
