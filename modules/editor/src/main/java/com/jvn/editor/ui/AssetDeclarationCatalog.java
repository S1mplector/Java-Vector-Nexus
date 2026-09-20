package com.jvn.editor.ui;

import com.jvn.editor.ui.AssetAutoLabelService.AssetKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Reads the VNS declaration graph, which is the authoritative source of existing labels. */
@SuppressWarnings("NullAway")
final class AssetDeclarationCatalog {
  private static final Pattern BACKGROUND = Pattern.compile(
      "^\\s*@background\\s+(\\S+)\\s+(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHAR_IMAGE = Pattern.compile(
      "^\\s*@charimg\\s+(\\S+)\\s+(\\S+)\\s+(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHAR_LAYER = Pattern.compile(
      "^\\s*@charlayer\\s+(\\S+)\\s+(\\S+)\\s+(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARACTER = Pattern.compile(
      "^\\s*@character\\s+(\\S+)(?:\\s+.*)?$", Pattern.CASE_INSENSITIVE);

  Index scan(Path root) throws IOException {
    Map<String, Declaration> byPath = new LinkedHashMap<>();
    Map<String, List<Declaration>> byDirectory = new HashMap<>();
    Map<String, Map<String, Integer>> ownerVotes = new HashMap<>();
    Map<String, Declaration> byScopedLabel = new HashMap<>();
    Map<String, Integer> aliasCounts = new HashMap<>();
    Set<String> characterIds = new LinkedHashSet<>();
    Set<String> conflictingPaths = new LinkedHashSet<>();
    Path scripts = root.resolve("scripts");
    if (!Files.isDirectory(scripts)) return Index.empty();

    List<Path> files;
    try (Stream<Path> stream = Files.walk(scripts)) {
      files = stream.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".vns"))
          .sorted().toList();
    }
    for (Path script : files) {
      List<String> lines = Files.readAllLines(script, StandardCharsets.UTF_8);
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        Matcher character = CHARACTER.matcher(line);
        if (character.matches()) {
          characterIds.add(AssetPathHeuristics.sanitizeId(character.group(1)));
        }
        Declaration declaration = declaration(script, i + 1, line);
        if (declaration == null || declaration.relativePath().isBlank()) continue;
        Declaration previous = byPath.putIfAbsent(declaration.relativePath(), declaration);
        if (previous != null
            && (!previous.owner().equals(declaration.owner())
                || !previous.label().equals(declaration.label())
                || previous.kind() != declaration.kind())) {
          aliasCounts.merge(declaration.relativePath(), 2, (count, ignored) -> count + 1);
        }
        String scopedLabel = AssetPathHeuristics.scopeKey(
            declaration.owner(), declaration.label());
        Declaration previousLabel = byScopedLabel.putIfAbsent(scopedLabel, declaration);
        if (previousLabel != null
            && !previousLabel.relativePath().equals(declaration.relativePath())) {
          conflictingPaths.add(previousLabel.relativePath());
          conflictingPaths.add(declaration.relativePath());
        }
        String directory = AssetPathHeuristics.parentPath(declaration.relativePath());
        byDirectory.computeIfAbsent(directory, ignored -> new ArrayList<>()).add(declaration);
        if (!declaration.owner().isBlank()) {
          String voteDirectory = directory;
          while (!voteDirectory.isBlank()
              && AssetPathHeuristics.pathParts(voteDirectory).size() >= 3) {
            ownerVotes.computeIfAbsent(voteDirectory, ignored -> new HashMap<>())
                .merge(declaration.owner(), 1, Integer::sum);
            voteDirectory = AssetPathHeuristics.parentPath(voteDirectory);
          }
        }
      }
    }
    Map<String, String> ownerByDirectory = new HashMap<>();
    for (Map.Entry<String, Map<String, Integer>> entry : ownerVotes.entrySet()) {
      entry.getValue().entrySet().stream()
          .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry::getKey))
          .ifPresent(winner -> ownerByDirectory.put(entry.getKey(), winner.getKey()));
    }
    Map<String, List<Declaration>> immutableDirectories = new HashMap<>();
    byDirectory.forEach((key, value) -> immutableDirectories.put(key, List.copyOf(value)));
    return new Index(
        Map.copyOf(byPath), Map.copyOf(immutableDirectories), Map.copyOf(ownerByDirectory),
        Set.copyOf(characterIds), Set.copyOf(conflictingPaths), Map.copyOf(aliasCounts),
        Map.copyOf(byScopedLabel));
  }

  boolean containsCharacter(String source, String owner) {
    if (source == null || source.isBlank()) return false;
    for (String line : source.split("\\R")) {
      Matcher matcher = CHARACTER.matcher(line);
      if (matcher.matches()
          && AssetPathHeuristics.sanitizeId(matcher.group(1)).equals(owner)) return true;
    }
    return false;
  }

  private Declaration declaration(Path sourceFile, int lineNumber, String line) {
    Matcher background = BACKGROUND.matcher(line);
    if (background.matches()) {
      return new Declaration(
          declarationPath(background.group(2)), AssetKind.BACKGROUND, "",
          AssetPathHeuristics.sanitizeId(background.group(1)), sourceFile, lineNumber, line.strip());
    }
    Matcher image = CHAR_IMAGE.matcher(line);
    if (image.matches()) {
      return new Declaration(
          declarationPath(image.group(3)), AssetKind.CHARACTER_SPRITE,
          AssetPathHeuristics.sanitizeId(image.group(1)),
          AssetPathHeuristics.sanitizeId(image.group(2)), sourceFile, lineNumber, line.strip());
    }
    Matcher layer = CHAR_LAYER.matcher(line);
    if (layer.matches()) {
      String path = declarationPath(layer.group(3));
      AssetKind kind = AssetPathHeuristics.kindFromPath(Path.of(path.isBlank() ? "unknown" : path));
      if (!kind.usesCharacterDeclaration() || kind == AssetKind.CHARACTER_SPRITE) {
        kind = AssetKind.CHARACTER_LAYER;
      }
      return new Declaration(
          path, kind, AssetPathHeuristics.sanitizeId(layer.group(1)),
          AssetPathHeuristics.sanitizeId(layer.group(2)), sourceFile, lineNumber, line.strip());
    }
    return null;
  }

  private String declarationPath(String raw) {
    if (raw == null) return "";
    String value = stripInlineComment(raw.strip());
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      value = value.substring(1, value.length() - 1)
          .replace("\\\"", "\"").replace("\\\\", "\\");
    }
    return AssetPathHeuristics.normalizeRelative(value);
  }

  private String stripInlineComment(String value) {
    boolean quoted = false;
    boolean escaped = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (escaped) {
        escaped = false;
      } else if (c == '\\') {
        escaped = true;
      } else if (c == '"') {
        quoted = !quoted;
      } else if (c == '#' && !quoted && i > 0 && Character.isWhitespace(value.charAt(i - 1))) {
        return value.substring(0, i).stripTrailing();
      }
    }
    return value;
  }

  record Declaration(
      String relativePath, AssetKind kind, String owner, String label, Path sourceFile,
      int line, String sourceLineText) {}

  record Index(
      Map<String, Declaration> byPath, Map<String, List<Declaration>> byDirectory,
      Map<String, String> ownerByDirectory, Set<String> characterIds,
      Set<String> conflictingPaths, Map<String, Integer> aliasCounts,
      Map<String, Declaration> byScopedLabel) {
    static Index empty() {
      return new Index(Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Map.of(), Map.of());
    }
  }
}
