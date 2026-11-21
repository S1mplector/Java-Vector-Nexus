package com.jvn.editor.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds byte-identical declared art, even when a copy has been renamed or moved. */
final class AssetContentIndex {
  private final Path root;
  private final Map<Long, List<AssetDeclarationCatalog.Declaration>> bySize = new HashMap<>();

  AssetContentIndex(Path root, Collection<List<AssetDeclarationCatalog.Declaration>> directories,
      Set<String> conflicts) {
    this.root = root;
    for (var declarations : directories) {
      for (var declaration : declarations) {
        String relative = declaration.relativePath();
        if (conflicts.contains(relative)) continue;
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) continue;
        long bytes = file.toFile().length();
        // Empty files and truncated headers carry no evidence about the artwork.
        if (bytes >= 32) bySize.computeIfAbsent(bytes, ignored -> new ArrayList<>()).add(declaration);
      }
    }
  }

  List<AssetDeclarationCatalog.Declaration> copiesOf(Path file) {
    var candidates = bySize.get(file.toFile().length());
    if (candidates == null) return List.of();
    List<AssetDeclarationCatalog.Declaration> matches = new ArrayList<>();
    for (var candidate : candidates) {
      try {
        if (Files.mismatch(file, root.resolve(candidate.relativePath())) == -1) matches.add(candidate);
      } catch (IOException ignored) {
        // A file may be moved while the project is being scanned.
      }
    }
    return matches;
  }
}
