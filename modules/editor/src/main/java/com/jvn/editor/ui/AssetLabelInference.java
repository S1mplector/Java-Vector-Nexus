package com.jvn.editor.ui;

import com.jvn.editor.ui.AssetAutoLabelService.AssetKind;
import com.jvn.editor.ui.AssetAutoLabelService.AssetSuggestion;
import com.jvn.editor.ui.AssetAutoLabelService.LabelStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Infers reviewable labels from VNS-declared siblings and the project's asset taxonomy. */
@SuppressWarnings("NullAway")
final class AssetLabelInference {
  List<Path> collectAssets(Path root) throws IOException {
    Set<Path> files = new LinkedHashSet<>();
    for (String folder : AssetPathHeuristics.ASSET_ROOTS) collectAssetsUnder(root.resolve(folder), files);
    return files.stream().sorted(Comparator.comparing(Path::toString)).toList();
  }

  AssetSuggestion fromDeclaration(
      Path file, String relativePath, AssetDeclarationCatalog.Declaration declaration,
      boolean isNew, boolean conflict, int aliasCount) {
    String source = declaration.sourceFile().getFileName() + ":" + declaration.line();
    String explanation = conflict
        ? "Conflicting VNS label points to multiple files; first found in " + source
        : aliasCount > 1
            ? "Declared with " + aliasCount + " VNS aliases; first found in " + source
            : "Declared in " + source;
    return new AssetSuggestion(
        file, relativePath, declaration.kind(), declaration.owner(), declaration.label(),
        conflict ? LabelStatus.CONFLICT : LabelStatus.DECLARED, 1.0,
        explanation,
        declaration.sourceFile(), declaration.line(), isNew);
  }

  AssetSuggestion fromRegistry(
      Path file, String relativePath, AssetLabelRegistry.Entry entry, boolean isNew) {
    return new AssetSuggestion(
        file, relativePath, entry.kind(), entry.owner(), entry.label(), entry.status(),
        entry.confidence(), entry.reason().isBlank() ? "Saved editor decision" : entry.reason(),
        null, -1, isNew);
  }

  AssetSuggestion fromMissingDeclaration(
      Path root, AssetDeclarationCatalog.Declaration declaration, boolean conflict,
      int aliasCount) {
    Path expected = root.resolve(declaration.relativePath()).normalize();
    String source = declaration.sourceFile().getFileName() + ":" + declaration.line();
    String aliasText = aliasCount > 1 ? " (one of " + aliasCount + " aliases)" : "";
    return new AssetSuggestion(
        expected, declaration.relativePath(), declaration.kind(), declaration.owner(),
        declaration.label(), conflict ? LabelStatus.CONFLICT : LabelStatus.MISSING, 1.0,
        "VNS declaration points to a missing asset" + aliasText + "; declared in " + source,
        declaration.sourceFile(), declaration.line(), false);
  }

  AssetSuggestion infer(
      Path root, Path file, String relativePath, AssetDeclarationCatalog.Index catalog,
      Set<String> usedScopedLabels, boolean isNew) {
    AssetKind pathKind = AssetPathHeuristics.kindFromPath(Path.of(relativePath));
    Map<AssetKind, String> uses = file.startsWith(root) ? catalog.usage().uses(relativePath) : Map.of();
    AssetKind kind = uses.size() == 1 ? uses.keySet().iterator().next() : pathKind;
    String stem = AssetPathHeuristics.nameTokens(AssetPathHeuristics.stem(file.getFileName().toString()));
    String namedOwner = knownCharacter(stem, catalog.characterIds());
    String directory = AssetPathHeuristics.parentPath(relativePath);
    String folderOwner = AssetPathHeuristics.inferOwner(relativePath, kind);
    String owner = "";
    List<String> reasons = new ArrayList<>();
    List<AssetDeclarationCatalog.Declaration> siblings = List.of();
    double confidence;
    if (!uses.isEmpty()) {
      reasons.addAll(uses.values());
      confidence = uses.size() == 1 ? 0.98 : 0.65;
      if (uses.size() > 1) {
        kind = "Audio".equals(AssetPathHeuristics.mediaType(relativePath)) ? AssetKind.AUDIO : AssetKind.UNKNOWN;
        reasons.add("Used in several roles");
      }
      if (kind.acceptsOwner()) owner = AssetPathHeuristics.inferOwner(relativePath, kind);
    } else if (!"Image".equals(AssetPathHeuristics.mediaType(relativePath)) || kind == AssetKind.PROJECT_ICON) {
      confidence = switch (kind) {
        case FONT, VIDEO -> 0.98;
        case PROJECT_ICON, MUSIC, SOUND_EFFECT, VOICE -> 0.90;
        default -> 0.62;
      };
      if (kind == AssetKind.VOICE) owner = folderOwner;
      reasons.add(switch (kind) {
        case PROJECT_ICON -> "Project icon filename";
        case MUSIC, SOUND_EFFECT, VOICE -> "Audio format and " + kind.displayName().toLowerCase(java.util.Locale.ROOT) + " naming convention";
        default -> file.getFileName().toString().replaceFirst("^.*\\.", "").toUpperCase(java.util.Locale.ROOT) + " file format";
      });
    } else {
      String poseOwner = poseOwner(stem);
      if (kind == AssetKind.UNKNOWN && (!namedOwner.isEmpty() || !poseOwner.isEmpty())) {
        kind = AssetPathHeuristics.isLayer(relativePath) ? AssetKind.CHARACTER_LAYER : AssetKind.CHARACTER_SPRITE;
      }
      boolean character = AssetPathHeuristics.characterArt(kind);
      String knownFolder = AssetPathHeuristics.characterFolder(relativePath);
      String folderId = knownCharacter(knownFolder, catalog.characterIds());
      String ownerHint = !character ? "" : !namedOwner.isEmpty() ? namedOwner : !folderId.isEmpty() ? folderId : poseOwner;
      String authoredOwner = ownerForDirectory(directory, catalog.ownerByDirectory());
      boolean explicitArtRole = AssetPathHeuristics.isLayer(relativePath) || !poseOwner.isBlank() || POSES.contains(stem);
      AssetKind requested = kind;
      List<AssetDeclarationCatalog.Declaration> compatible = catalog.byDirectory().getOrDefault(directory, List.of())
          .stream().filter(value -> !catalog.conflictingPaths().contains(value.relativePath()))
          .filter(value -> requested == AssetKind.UNKNOWN || value.kind() == requested
              || character && !explicitArtRole && AssetPathHeuristics.characterArt(value.kind()))
          .filter(value -> ownerHint.isEmpty() || ownerHint.equals(value.owner())
              || ownerHint.equals(knownFolder) && ownerHint.startsWith(value.owner() + "_"))
          .filter(value -> Files.isRegularFile(root.resolve(value.relativePath()))).toList();
      siblings = nearestSiblings(relativePath, compatible);
      if (siblings.isEmpty() && (pathKind != AssetKind.UNKNOWN || !ownerHint.isEmpty())) siblings = compatible;
      boolean mixed = siblings.stream().map(value -> value.kind() + "/" + value.owner()).distinct().count() > 1;
      confidence = kind == AssetKind.BACKGROUND ? 0.87 : 0.62;
      if (pathKind != AssetKind.UNKNOWN) reasons.add("Path identifies " + pathKind.displayName().toLowerCase(java.util.Locale.ROOT));
      if (!mixed && !siblings.isEmpty()) {
        kind = siblings.getFirst().kind();
        owner = siblings.getFirst().owner();
        confidence = 0.90;
        reasons.add(siblings.size() + " declared sibling" + (siblings.size() == 1 ? "" : "s") + " in " + directory);
      } else if (kind.usesCharacterDeclaration()) {
        owner = ownerHint;
        if (owner.isBlank()) owner = authoredOwner;
        if (owner.isBlank()) owner = AssetPathHeuristics.inferOwner(relativePath, kind);
        if (!namedOwner.isBlank() && character) {
          confidence = AssetPathHeuristics.isLayer(relativePath) || !poseOwner.isBlank() || pathKind != AssetKind.UNKNOWN ? 0.92 : 0.76;
          reasons.add("Filename matches declared character " + namedOwner);
        } else if (character && catalog.characterIds().contains(owner)) {
          confidence = 0.88;
          reasons.add(owner.equals(authoredOwner) ? "Nearby declarations use character " + owner : "Character folder matches " + owner);
        } else if (!poseOwner.isEmpty() && character) {
          reasons.add("Pose name follows " + poseOwner + " in the filename");
        }
      }
      if (mixed) {
        confidence = Math.min(confidence, 0.72);
        reasons.add("Nearby declarations have different owners or roles");
      }
      if (character && !namedOwner.isBlank() && catalog.characterIds().contains(folderOwner)
          && !folderOwner.equals(namedOwner)) {
        confidence = Math.min(confidence, 0.70);
        reasons.add("Folder names " + folderOwner + " while filename names " + namedOwner);
      }
      List<AssetDeclarationCatalog.Declaration> copies = catalog.content().copiesOf(file);
      if (!copies.isEmpty()) {
        var copy = copies.getFirst();
        boolean sameIdentity = copies.stream().allMatch(other -> other.kind() == copy.kind() && other.owner().equals(copy.owner()));
        if (sameIdentity && (kind == AssetKind.UNKNOWN || kind == copy.kind())
            && (ownerHint.isEmpty() || ownerHint.equals(copy.owner()))) {
          kind = copy.kind();
          owner = copy.owner();
          confidence = 0.98;
          reasons.add("Identical file declared in " + copy.sourceFile().getFileName() + ":" + copy.line()
              + " as " + (owner.isEmpty() ? "" : owner + "/") + copy.label());
        } else {
          confidence = Math.min(confidence, 0.72);
          reasons.add("Identical artwork has a different owner or role in the declarations");
        }
      }
      if (reasons.isEmpty()) reasons.add("No matching declaration, character name or role in the path");
    }
    String label = AssetPathHeuristics.inferLabel(relativePath, kind, owner);
    String learned = labelFromSiblings(relativePath, owner, siblings);
    if (!learned.isBlank()) {
      label = learned;
      reasons.add("label pattern learned from declared siblings");
    }
    label = AssetPathHeuristics.uniqueLabel(owner, label, usedScopedLabels);
    usedScopedLabels.add(AssetPathHeuristics.scopeKey(owner, label));
    return new AssetSuggestion(file, relativePath, kind, owner, label, LabelStatus.SUGGESTED,
        confidence, String.join(" · ", reasons), null, -1, isNew);
  }

  private static String knownCharacter(String stem, Set<String> ids) {
    return ids.stream().filter(id -> stem.equals(id) || stem.startsWith(id + "_"))
        .max(Comparator.comparingInt(String::length)).orElse("");
  }

  private static String poseOwner(String stem) {
    int split = stem.lastIndexOf('_');
    if (split < 1) return "";
    return POSES.contains(stem.substring(split + 1)) ? stem.substring(0, split) : "";
  }

  private static final Set<String> POSES = Set.of("happy", "sad", "neutral", "normal", "angry", "shy",
      "confused", "embarrassed", "annoyed", "thoughtful", "surprised", "smiling", "idle", "laughing");

  private List<AssetDeclarationCatalog.Declaration> nearestSiblings(
      String path, List<AssetDeclarationCatalog.Declaration> siblings) {
    String[] target = filenameTokens(path);
    int best = 1;
    List<AssetDeclarationCatalog.Declaration> nearest = new ArrayList<>();
    for (AssetDeclarationCatalog.Declaration sibling : siblings) {
      int shared = commonPrefix(target, filenameTokens(sibling.relativePath()));
      if (shared < 2 || shared < best) continue;
      if (shared > best) {
        nearest.clear();
        best = shared;
      }
      nearest.add(sibling);
    }
    return nearest;
  }

  /** Reuse a naming template only when at least two distinct sibling files agree. */
  private String labelFromSiblings(String path, String owner,
      List<AssetDeclarationCatalog.Declaration> siblings) {
    String[] target = filenameTokens(path);
    Map<String, Set<String>> votes = new LinkedHashMap<>();
    for (AssetDeclarationCatalog.Declaration sibling : siblings) {
      if (!owner.equals(sibling.owner())) continue;
      String[] source = filenameTokens(sibling.relativePath());
      int prefix = commonPrefix(source, target);
      if (prefix < 2 || prefix == source.length || prefix == target.length) continue;
      String oldSuffix = String.join("_", java.util.Arrays.copyOfRange(source, prefix, source.length));
      String newSuffix = String.join("_", java.util.Arrays.copyOfRange(target, prefix, target.length));
      String label = sibling.label();
      if (!label.equals(oldSuffix) && !label.endsWith("_" + oldSuffix)) continue;
      String candidate = label.substring(0, label.length() - oldSuffix.length()) + newSuffix;
      votes.computeIfAbsent(candidate, ignored -> new LinkedHashSet<>()).add(sibling.relativePath());
    }
    // Ambiguous aliases or naming styles should be reviewed rather than guessed.
    if (votes.size() != 1) return "";
    Map.Entry<String, Set<String>> winner = votes.entrySet().iterator().next();
    return winner.getValue().size() >= 2 ? winner.getKey() : "";
  }

  private String[] filenameTokens(String path) {
    String name = Path.of(path).getFileName().toString();
    int dot = name.lastIndexOf('.');
    return AssetPathHeuristics.sanitizeId(dot > 0 ? name.substring(0, dot) : name)
        .replaceAll("(?<=[a-z])(?=[0-9])|(?<=[0-9])(?=[a-z])", "_").split("_");
  }

  private int commonPrefix(String[] first, String[] second) {
    int count = 0;
    while (count < first.length && count < second.length && first[count].equals(second[count])) count++;
    return count;
  }

  private void collectAssetsUnder(Path directory, Set<Path> output) throws IOException {
    if (!Files.isDirectory(directory)) return;
    try (Stream<Path> stream = Files.walk(directory, 16)) {
      stream.filter(Files::isRegularFile)
          .filter(AssetPathHeuristics::isSupportedAsset)
          .map(path -> path.toAbsolutePath().normalize())
          .forEach(output::add);
    }
  }

  private String ownerForDirectory(String directory, Map<String, String> knownOwners) {
    String candidate = directory;
    while (!candidate.isBlank()) {
      String owner = knownOwners.getOrDefault(candidate, "");
      if (!owner.isBlank()) return owner;
      candidate = AssetPathHeuristics.parentPath(candidate);
    }
    return "";
  }

}
