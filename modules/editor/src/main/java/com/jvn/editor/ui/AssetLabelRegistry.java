package com.jvn.editor.ui;

import com.jvn.editor.ui.AssetAutoLabelService.AssetKind;
import com.jvn.editor.ui.AssetAutoLabelService.LabelStatus;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** Persists editor-reviewed labels and the baseline used to identify newly added assets. */
final class AssetLabelRegistry {
  Snapshot load(Path root) throws IOException {
    Path file = root.resolve(AssetAutoLabelService.REGISTRY_PATH);
    if (!Files.isRegularFile(file)) return Snapshot.empty();
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
    }
    boolean initialized = Boolean.parseBoolean(
        properties.getProperty("registry.initialized", "false"));
    Map<String, Entry> entries = new LinkedHashMap<>();
    Set<String> seen = new LinkedHashSet<>();
    Set<String> pending = new LinkedHashSet<>();
    for (String key : properties.stringPropertyNames()) {
      if (key.startsWith("new.")) decodeKey(key.substring(4)).ifPresent(pending::add);
      if (key.startsWith("seen.")) decodeKey(key.substring("seen.".length())).ifPresent(seen::add);
    }
    for (String key : properties.stringPropertyNames()) {
      if (!key.startsWith("asset.") || !key.endsWith(".status")) continue;
      String encoded = key.substring("asset.".length(), key.length() - ".status".length());
      String path = decodeKey(encoded).orElse("");
      if (path.isBlank()) continue;
      String prefix = "asset." + encoded + ".";
      entries.put(path, new Entry(
          AssetKind.parse(properties.getProperty(prefix + "kind")),
          properties.getProperty(prefix + "owner", ""),
          properties.getProperty(prefix + "label", ""),
          LabelStatus.parse(properties.getProperty(prefix + "status")),
          parseDouble(properties.getProperty(prefix + "confidence"), 1.0),
          properties.getProperty(prefix + "reason", ""),
          properties.getProperty(prefix + "updated", "")));
    }
    return new Snapshot(initialized, Map.copyOf(entries), Set.copyOf(seen), Set.copyOf(pending));
  }

  void save(Path root, Snapshot registry) throws IOException {
    Path target = root.resolve(AssetAutoLabelService.REGISTRY_PATH);
    Files.createDirectories(target.getParent());
    Properties properties = new Properties();
    properties.setProperty("registry.initialized", Boolean.toString(registry.initialized()));
    properties.setProperty("registry.version", "1");
    for (String path : registry.pendingNewPaths()) properties.setProperty("new." + encodeKey(path), "true");
    for (String path : registry.seenPaths()) {
      properties.setProperty("seen." + encodeKey(path), "true");
    }
    for (Map.Entry<String, Entry> saved : registry.entries().entrySet()) {
      String prefix = "asset." + encodeKey(saved.getKey()) + ".";
      Entry entry = saved.getValue();
      properties.setProperty(prefix + "kind", entry.kind().name());
      properties.setProperty(prefix + "owner", entry.owner());
      properties.setProperty(prefix + "label", entry.label());
      properties.setProperty(prefix + "status", entry.status().name());
      properties.setProperty(
          prefix + "confidence", String.format(Locale.ROOT, "%.4f", entry.confidence()));
      properties.setProperty(prefix + "reason", entry.reason());
      properties.setProperty(prefix + "updated", entry.updated());
    }
    Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
    try (OutputStream output = Files.newOutputStream(temporary)) {
      properties.store(output, "JVN editor asset auto-label registry");
    }
    AssetDeclarationWriter.moveAtomically(temporary, target);
  }

  private static String encodeKey(String path) {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(path.getBytes(StandardCharsets.UTF_8));
  }

  private static Optional<String> decodeKey(String encoded) {
    try {
      return Optional.of(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }

  private static double parseDouble(String value, double fallback) {
    try {
      double parsed = Double.parseDouble(value);
      return Double.isFinite(parsed) ? parsed : fallback;
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }

  record Entry(
      AssetKind kind, String owner, String label, LabelStatus status, double confidence,
      String reason, String updated) {
    Entry {
      kind = kind == null ? AssetKind.UNKNOWN : kind;
      owner = AssetPathHeuristics.sanitizeId(owner);
      label = AssetPathHeuristics.sanitizeId(label);
      status = status == null ? LabelStatus.SUGGESTED : status;
      reason = Objects.requireNonNullElse(reason, "");
      updated = Objects.requireNonNullElse(updated, "");
    }
  }

  record Snapshot(boolean initialized, Map<String, Entry> entries, Set<String> seenPaths,
                  Set<String> pendingNewPaths) {
    Snapshot(boolean initialized, Map<String, Entry> entries, Set<String> seenPaths) {
      this(initialized, entries, seenPaths, Set.of());
    }

    static Snapshot empty() {
      return new Snapshot(false, Map.of(), Set.of(), Set.of());
    }

    Snapshot withScanBaseline(Set<String> paths, Set<String> pending) {
      return new Snapshot(true, entries, Set.copyOf(paths), Set.copyOf(pending));
    }

    Snapshot withEntries(Map<String, Entry> updated) {
      Set<String> pending = new LinkedHashSet<>(pendingNewPaths);
      pending.removeIf(path -> updated.containsKey(path) && updated.get(path).status() != LabelStatus.SUGGESTED);
      return new Snapshot(true, Map.copyOf(updated), seenPaths, Set.copyOf(pending));
    }
  }
}
