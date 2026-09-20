package com.jvn.editor.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Coordinates asset discovery, label inference, persistence, and reviewed VNS generation. */
@SuppressWarnings("NullAway")
public final class AssetAutoLabelService {
  public static final String REGISTRY_PATH = ".jvn/asset-labels.properties";
  public static final String AUTO_DECLARATIONS_PATH = "scripts/definitions/auto_labels.vns";

  private final AssetDeclarationCatalog declarations = new AssetDeclarationCatalog();
  private final AssetLabelRegistry registry = new AssetLabelRegistry();
  private final AssetLabelInference inference = new AssetLabelInference();
  private final AssetDeclarationWriter writer = new AssetDeclarationWriter(declarations);

  /** Scans the project and records the current asset set as the new-file baseline. */
  public ScanResult scan(Path projectRoot) throws IOException {
    return scan(projectRoot, true);
  }

  /** Scans without changing project metadata. Useful for audits and command-line tooling. */
  public ScanResult preview(Path projectRoot) throws IOException {
    return scan(projectRoot, false);
  }

  private synchronized ScanResult scan(Path projectRoot, boolean persistBaseline) throws IOException {
    Path root = AssetPathHeuristics.requireProjectRoot(projectRoot);
    AssetLabelRegistry.Snapshot saved = registry.load(root);
    AssetDeclarationCatalog.Index catalog = declarations.scan(root);
    Set<Path> discovered = new LinkedHashSet<>(inference.collectAssets(root));
    for (String path : catalog.byPath().keySet()) {
      Path file = root.resolve(path).normalize();
      if (file.startsWith(root) && Files.isRegularFile(file)) discovered.add(file);
    }
    List<Path> files = discovered.stream().sorted().toList();
    Set<String> usedLabels = new LinkedHashSet<>();
    catalog.byScopedLabel().values().forEach(value ->
        usedLabels.add(AssetPathHeuristics.scopeKey(value.owner(), value.label())));
    saved.entries().values().stream().filter(entry -> entry.status() != LabelStatus.DECLARED)
        .filter(entry -> !entry.label().isBlank()).forEach(entry ->
        usedLabels.add(AssetPathHeuristics.scopeKey(entry.owner(), entry.label())));

    boolean hasBaseline = saved.initialized();
    List<AssetSuggestion> suggestions = new ArrayList<>();
    Set<String> seenNow = new LinkedHashSet<>();
    int scanIssues = 0;
    for (Path file : files) {
      try {
        String relativePath = AssetPathHeuristics.relative(root, file);
        seenNow.add(relativePath);
        boolean isNew = hasBaseline && !saved.seenPaths().contains(relativePath);
        AssetDeclarationCatalog.Declaration declared = catalog.byPath().get(relativePath);
        AssetLabelRegistry.Entry decision = saved.entries().get(relativePath);
        if (declared != null) {
          suggestions.add(inference.fromDeclaration(
              file, relativePath, declared, isNew,
              catalog.conflictingPaths().contains(relativePath),
              catalog.aliasCounts().getOrDefault(relativePath, 1)));
        } else if (decision != null && decision.status() != LabelStatus.SUGGESTED
            && decision.status() != LabelStatus.DECLARED) {
          suggestions.add(inference.fromRegistry(file, relativePath, decision, isNew));
        } else {
          suggestions.add(inference.infer(file, relativePath, catalog, usedLabels, isNew));
        }
      } catch (RuntimeException error) {
        scanIssues++;
      }
    }
    for (AssetDeclarationCatalog.Declaration declaration : catalog.byPath().values()) {
      if (seenNow.contains(declaration.relativePath())) continue;
      suggestions.add(inference.fromMissingDeclaration(
          root, declaration, catalog.conflictingPaths().contains(declaration.relativePath()),
          catalog.aliasCounts().getOrDefault(declaration.relativePath(), 1)));
    }
    suggestions.sort(Comparator
        .comparing(AssetSuggestion::isNew).reversed()
        .thenComparing(suggestion -> suggestion.status().sortOrder())
        .thenComparing(AssetSuggestion::relativePath, String.CASE_INSENSITIVE_ORDER));
    AssetLabelRegistry.Snapshot nextBaseline = saved.withScanBaseline(seenNow);
    if (persistBaseline && !nextBaseline.equals(saved)) registry.save(root, nextBaseline);
    return summarize(root, suggestions, catalog.characterIds(), scanIssues);
  }

  public AssetSuggestion suggestDroppedAsset(Path projectRoot, Path source) throws IOException {
    if (source == null) throw new IOException("Dropped asset does not exist");
    return suggestDroppedAssets(projectRoot, List.of(source)).getFirst();
  }

  /** Suggests a multi-file drop with one declaration scan and collision-free labels. */
  public List<AssetSuggestion> suggestDroppedAssets(Path projectRoot, List<Path> sources)
      throws IOException {
    Path root = AssetPathHeuristics.requireProjectRoot(projectRoot);
    if (sources == null || sources.isEmpty()) return List.of();
    AssetDeclarationCatalog.Index catalog = declarations.scan(root);
    Set<String> used = new LinkedHashSet<>();
    catalog.byScopedLabel().values().forEach(value ->
        used.add(AssetPathHeuristics.scopeKey(value.owner(), value.label())));
    AssetLabelRegistry.Snapshot saved = registry.load(root);
    saved.entries().values().stream().filter(value -> value.status() != LabelStatus.DECLARED).forEach(value ->
        used.add(AssetPathHeuristics.scopeKey(value.owner(), value.label())));
    List<AssetSuggestion> result = new ArrayList<>();
    for (Path source : sources) {
      Path file = source == null ? null : source.toAbsolutePath().normalize();
      if (file == null || !Files.isRegularFile(file)) {
        throw new IOException("Dropped asset does not exist");
      }
      String relativePath = file.startsWith(root)
          ? AssetPathHeuristics.relative(root, file)
          : "assets/" + AssetPathHeuristics.recommendedDirectory(kindFromPath(file), "")
              + "/" + file.getFileName();
      AssetDeclarationCatalog.Declaration declared = catalog.byPath().get(relativePath);
      AssetLabelRegistry.Entry decision = saved.entries().get(relativePath);
      if (file.startsWith(root) && declared != null) {
        result.add(inference.fromDeclaration(file, relativePath, declared, false,
            catalog.conflictingPaths().contains(relativePath), catalog.aliasCounts().getOrDefault(relativePath, 1)));
      } else if (file.startsWith(root) && decision != null
          && decision.status() != LabelStatus.SUGGESTED && decision.status() != LabelStatus.DECLARED) {
        result.add(inference.fromRegistry(file, relativePath, decision, false));
      } else {
        result.add(inference.infer(file, relativePath, catalog, used, true));
      }
    }
    return List.copyOf(result);
  }

  public Path importDroppedAsset(Path projectRoot, Path source, AssetSuggestion suggestion)
      throws IOException {
    Path root = AssetPathHeuristics.requireProjectRoot(projectRoot);
    AssetKind kind = suggestion == null ? kindFromPath(source) : suggestion.kind();
    String owner = suggestion == null ? "" : suggestion.owner();
    return writer.importAsset(root, source, kind, owner);
  }

  public synchronized void saveDecision(Path projectRoot, AssetSuggestion suggestion, LabelStatus status)
      throws IOException {
    if (suggestion == null) return;
    Path root = AssetPathHeuristics.requireProjectRoot(projectRoot);
    AssetLabelRegistry.Snapshot saved = registry.load(root);
    Map<String, AssetLabelRegistry.Entry> entries = new LinkedHashMap<>(saved.entries());
    entries.put(suggestion.relativePath(), new AssetLabelRegistry.Entry(
        suggestion.kind(), suggestion.owner(), suggestion.label(),
        status == null ? LabelStatus.LABELED : status, suggestion.confidence(),
        suggestion.reason(), Instant.now().toString()));
    registry.save(root, new AssetLabelRegistry.Snapshot(true, entries, saved.seenPaths()));
  }

  public synchronized AppliedDeclaration applyDeclaration(Path projectRoot, AssetSuggestion suggestion)
      throws IOException {
    Path root = AssetPathHeuristics.requireProjectRoot(projectRoot);
    if (suggestion == null) throw new IOException("No asset selected");
    AppliedDeclaration result = writer.apply(root, suggestion);
    saveDecision(root, suggestion, result.declaration().isBlank()
        ? LabelStatus.LABELED : LabelStatus.DECLARED);
    return result;
  }

  /** Applies a reviewed batch with one VNS/catalog pass and one registry write. */
  public synchronized BatchAppliedDeclarations applyDeclarations(
      Path projectRoot, List<AssetSuggestion> suggestions) throws IOException {
    Path root = AssetPathHeuristics.requireProjectRoot(projectRoot);
    List<AssetSuggestion> batch = suggestions == null ? List.of() : List.copyOf(suggestions);
    AssetDeclarationWriter.BatchWriteResult written = writer.applyAll(root, batch);
    AssetLabelRegistry.Snapshot saved = registry.load(root);
    Map<String, AssetLabelRegistry.Entry> entries = new LinkedHashMap<>(saved.entries());
    int labelsSaved = 0;
    for (AssetSuggestion suggestion : batch) {
      if (suggestion == null) continue;
      boolean declarable = !writer.declarationFor(suggestion).isBlank();
      if (!declarable) labelsSaved++;
      entries.put(suggestion.relativePath(), new AssetLabelRegistry.Entry(
          suggestion.kind(), suggestion.owner(), suggestion.label(),
          declarable ? LabelStatus.DECLARED : LabelStatus.LABELED,
          suggestion.confidence(), suggestion.reason(), Instant.now().toString()));
    }
    if (!batch.isEmpty()) {
      registry.save(root, new AssetLabelRegistry.Snapshot(true, entries, saved.seenPaths()));
    }
    return new BatchAppliedDeclarations(
        written.declarationFile(), written.declarationsGenerated(), labelsSaved,
        written.charactersAdded(), written.entryIncludeAdded());
  }

  public String declarationFor(AssetSuggestion suggestion) {
    return writer.declarationFor(suggestion);
  }

  public static boolean isSupportedAsset(Path path) {
    return AssetPathHeuristics.isSupportedAsset(path);
  }

  public static AssetKind kindFromPath(Path path) {
    return AssetPathHeuristics.kindFromPath(path);
  }

  public static String sanitizeId(String value) {
    return AssetPathHeuristics.sanitizeId(value);
  }

  private ScanResult summarize(
      Path root, List<AssetSuggestion> suggestions, Set<String> characterIds, int scanIssues) {
    EnumMap<LabelStatus, Integer> byStatus = new EnumMap<>(LabelStatus.class);
    EnumMap<AssetKind, Integer> byKind = new EnumMap<>(AssetKind.class);
    int highConfidence = 0;
    int newAssets = 0;
    for (AssetSuggestion suggestion : suggestions) {
      byStatus.merge(suggestion.status(), 1, Integer::sum);
      byKind.merge(suggestion.kind(), 1, Integer::sum);
      if (suggestion.status() == LabelStatus.SUGGESTED && suggestion.confidence() >= 0.80) {
        highConfidence++;
      }
      if (suggestion.isNew()) newAssets++;
    }
    return new ScanResult(
        root, List.copyOf(suggestions), Map.copyOf(byStatus), Map.copyOf(byKind),
        Set.copyOf(characterIds), highConfidence, newAssets, scanIssues);
  }

  public enum AssetKind {
    BACKGROUND("Background", true, false), CHARACTER_LAYER("Character layer", true, true),
    CHARACTER_SPRITE("Character sprite", true, true), PROP("Prop / scene object", true, true),
    PANEL("Comic panel", true, true), UI("UI art", true, true),
    EFFECT("Visual effect", true, true), AUDIO("Audio", false, false),
    VIDEO("Video", false, false), FONT("Font", false, false),
    DATA("Data", false, false), UNKNOWN("Unknown", false, false);

    private final String displayName;
    private final boolean vnsDeclarable;
    private final boolean characterDeclaration;

    AssetKind(String displayName, boolean vnsDeclarable, boolean characterDeclaration) {
      this.displayName = displayName;
      this.vnsDeclarable = vnsDeclarable;
      this.characterDeclaration = characterDeclaration;
    }

    public String displayName() { return displayName; }
    public boolean isVnsDeclarable() { return vnsDeclarable; }
    public boolean usesCharacterDeclaration() { return characterDeclaration; }

    public static AssetKind parse(String value) {
      if (value == null || value.isBlank()) return UNKNOWN;
      try {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        return UNKNOWN;
      }
    }
  }

  public enum LabelStatus {
    NEW("New", 0), MISSING("Missing file", 1), SUGGESTED("Needs review", 2),
    LABELED("Labeled", 3), DECLARED("Declared in VNS", 4), IGNORED("Ignored", 5),
    CONFLICT("Conflict", 6);

    private final String displayName;
    private final int sortOrder;

    LabelStatus(String displayName, int sortOrder) {
      this.displayName = displayName;
      this.sortOrder = sortOrder;
    }

    public String displayName() { return displayName; }
    int sortOrder() { return sortOrder; }

    public static LabelStatus parse(String value) {
      if (value == null || value.isBlank()) return SUGGESTED;
      try {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        return SUGGESTED;
      }
    }
  }

  public record AssetSuggestion(
      Path file, String relativePath, AssetKind kind, String owner, String label,
      LabelStatus status, double confidence, String reason, Path declarationFile,
      int declarationLine, boolean isNew) {
    public AssetSuggestion {
      relativePath = AssetPathHeuristics.normalizeRelative(relativePath);
      kind = kind == null ? AssetKind.UNKNOWN : kind;
      owner = sanitizeId(owner);
      label = sanitizeId(label);
      status = status == null ? LabelStatus.SUGGESTED : status;
      confidence = Double.isFinite(confidence) ? Math.max(0.0, Math.min(1.0, confidence)) : 0.0;
      reason = reason == null ? "" : reason;
    }

    public AssetSuggestion reviewed(AssetKind nextKind, String nextOwner, String nextLabel) {
      return new AssetSuggestion(
          file, relativePath, nextKind, nextOwner, nextLabel, LabelStatus.LABELED,
          1.0, "Reviewed in the Auto-label dashboard", declarationFile, declarationLine, isNew);
    }
  }

  public record ScanResult(
      Path projectRoot, List<AssetSuggestion> assets, Map<LabelStatus, Integer> byStatus,
      Map<AssetKind, Integer> byKind, Set<String> characterIds, int highConfidenceSuggestions,
      int newAssets, int scanIssues) {
    public int declaredCount() { return byStatus.getOrDefault(LabelStatus.DECLARED, 0); }
    public int labeledCount() { return byStatus.getOrDefault(LabelStatus.LABELED, 0); }
    public int ignoredCount() { return byStatus.getOrDefault(LabelStatus.IGNORED, 0); }
    public int reviewCount() {
      return byStatus.getOrDefault(LabelStatus.SUGGESTED, 0)
          + byStatus.getOrDefault(LabelStatus.NEW, 0)
          + byStatus.getOrDefault(LabelStatus.MISSING, 0)
          + byStatus.getOrDefault(LabelStatus.CONFLICT, 0);
    }
    public int missingCount() { return byStatus.getOrDefault(LabelStatus.MISSING, 0); }
    public int currentAssetCount() {
      return (int) assets.stream().filter(asset -> Files.isRegularFile(asset.file())).count();
    }
  }

  public record AppliedDeclaration(
      Path declarationFile, String declaration, boolean characterAdded,
      boolean entryIncludeAdded) {}

  public record BatchAppliedDeclarations(
      Path declarationFile, int declarationsGenerated, int labelsSaved, int charactersAdded,
      boolean entryIncludeAdded) {}
}
