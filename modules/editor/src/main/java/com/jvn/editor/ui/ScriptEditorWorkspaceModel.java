package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure data/model layer for the text editor sidebar.
 * Keeps filesystem indexing, metadata extraction, filtering, and safe
 * file creation out of the JavaFX view code.
 */
public final class ScriptEditorWorkspaceModel {
  private static final Pattern LABEL_PATTERN = Pattern.compile("^\\s*@label\\s+([^\\s#]+)");
  private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*@include\\s+(.+)$");
  private static final Set<String> IGNORED_DIR_NAMES = Set.of(
      ".git", ".gradle", ".idea", ".vscode", "build", "out", "bin", "target", "node_modules");

  private ScriptEditorWorkspaceModel() {}

  public static WorkspaceSnapshot emptySnapshot() {
    return new WorkspaceSnapshot(null, null, List.of(), 0, 0);
  }

  public static WorkspaceSnapshot index(File launchRoot) {
    if (launchRoot == null || !launchRoot.isDirectory()) return emptySnapshot();
    Path contentRoot = resolveTextWorkspaceRoot(launchRoot);
    if (contentRoot == null) {
      return new WorkspaceSnapshot(launchRoot, null, List.of(), 0, 0);
    }

    List<ScriptFileEntry> files = collectTextFiles(launchRoot, contentRoot);
    int totalLabels = files.stream().mapToInt(ScriptFileEntry::labelCount).sum();
    Set<String> folders = new LinkedHashSet<>();
    for (ScriptFileEntry entry : files) {
      Path parent = Path.of(entry.relativePath()).getParent();
      if (parent != null) folders.add(parent.toString().replace('\\', '/'));
    }
    return new WorkspaceSnapshot(launchRoot, contentRoot, List.copyOf(files), folders.size(), totalLabels);
  }

  public static Path resolveTextWorkspaceRoot(File root) {
    if (root == null || !root.isDirectory()) return null;
    return root.toPath().toAbsolutePath().normalize();
  }

  public static Path resolveScriptsRoot(File root) {
    if (root == null) return null;
    Path scripts = root.toPath().resolve("scripts").normalize();
    return Files.isDirectory(scripts) ? scripts : resolveTextWorkspaceRoot(root);
  }

  public static List<ScriptFileEntry> filter(List<ScriptFileEntry> files, String query) {
    if (files == null || files.isEmpty()) return List.of();
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (needle.isEmpty()) return List.copyOf(files);

    List<ScriptFileEntry> filtered = new ArrayList<>();
    for (ScriptFileEntry entry : files) {
      String haystack = (entry.relativePath() + "\n"
          + entry.projectRelativePath() + "\n"
          + entry.displayName() + "\n"
          + entry.kind().name() + "\n"
          + String.join("\n", entry.labelNames())).toLowerCase(Locale.ROOT);
      if (haystack.contains(needle)) filtered.add(entry);
    }
    return filtered;
  }

  public static File createTextFile(File launchRoot, String requestedRelativePath) throws IOException {
    Objects.requireNonNull(launchRoot, "launchRoot");
    Path contentRoot = resolveTextWorkspaceRoot(launchRoot);
    if (contentRoot == null) {
      throw new IOException("Project root is not available.");
    }

    String normalized = normalizeRelativeTextPath(requestedRelativePath);
    Path target = contentRoot.resolve(normalized).normalize();
    if (!target.startsWith(contentRoot)) {
      throw new IOException("Text file path must stay inside the project root.");
    }
    if (!FileEditorTab.supportsTextEditing(target.toFile())) {
      throw new IOException("Unsupported text file type: " + target.getFileName());
    }

    // Check existing ancestors before creating directories through a symlink.
    Path realRoot = contentRoot.toRealPath();
    for (Path ancestor = target; ancestor != null && ancestor.startsWith(contentRoot); ancestor = ancestor.getParent()) {
      if (Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)
          && !ancestor.toRealPath().startsWith(realRoot)) {
        throw new IOException("Text file path must stay inside the project root.");
      }
    }
    Path parent = target.getParent();
    if (parent != null) Files.createDirectories(parent);
    try {
      Files.writeString(target, defaultTemplate(target), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    } catch (FileAlreadyExistsException ex) {
      if (!Files.isRegularFile(target)) throw new IOException("Path is not a text file: " + target, ex);
      // Creating an existing file opens it without replacing its contents.
    }
    return target.toFile();
  }

  public static File createScript(File launchRoot, String requestedRelativePath) throws IOException {
    return createTextFile(launchRoot, normalizeRelativeScriptPath(requestedRelativePath));
  }

  public record SearchHit(File file, String relativePath, int lineNumber, String lineText) {}

  public static List<SearchHit> searchContent(WorkspaceSnapshot snapshot, String query, int maxResults) {
    if (snapshot == null || snapshot.scripts() == null || query == null || query.isBlank()) return List.of();
    String lowerQuery = query.toLowerCase(Locale.ROOT);
    List<SearchHit> hits = new ArrayList<>();
    for (ScriptFileEntry entry : snapshot.scripts()) {
      if (hits.size() >= maxResults) break;
      try {
        List<String> lines = Files.readAllLines(entry.file().toPath(), StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size() && hits.size() < maxResults; i++) {
          if (lines.get(i).toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            hits.add(new SearchHit(entry.file(), entry.relativePath(), i + 1, lines.get(i).trim()));
          }
        }
      } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
      }
    }
    return hits;
  }

  public static File renameTextFile(File textFile, String newName) throws IOException {
    Objects.requireNonNull(textFile, "textFile");
    if (newName == null || newName.isBlank()) throw new IOException("New name must not be blank.");
    String sanitized = newName.trim();
    if (sanitized.equals(".") || sanitized.equals("..") || sanitized.contains("/")
        || sanitized.contains("\\") || sanitized.contains(":") || sanitized.indexOf('\0') >= 0) {
      throw new IOException("New name must be a file name without a directory path.");
    }
    String currentName = textFile.getName();
    int dot = currentName.lastIndexOf('.');
    if (!sanitized.contains(".") && dot > 0) {
      sanitized += currentName.substring(dot);
    }
    Path target = textFile.toPath().resolveSibling(sanitized).normalize();
    if (Files.exists(target)) throw new IOException("A file named '" + sanitized + "' already exists.");
    if (!FileEditorTab.supportsTextEditing(target.toFile())) {
      throw new IOException("Unsupported text file type: " + sanitized);
    }
    Files.move(textFile.toPath(), target);
    return target.toFile();
  }

  public static File renameScript(File scriptFile, String newName) throws IOException {
    return renameTextFile(scriptFile, ensureExtension(newName, ".vns"));
  }

  public static void deleteTextFile(File textFile) throws IOException {
    Objects.requireNonNull(textFile, "textFile");
    if (!textFile.exists()) throw new IOException("File does not exist: " + textFile.getAbsolutePath());
    Files.delete(textFile.toPath());
  }

  public static void deleteScript(File scriptFile) throws IOException {
    deleteTextFile(scriptFile);
  }

  public static File duplicateTextFile(File textFile) throws IOException {
    Objects.requireNonNull(textFile, "textFile");
    String name = textFile.getName();
    int dot = name.lastIndexOf('.');
    String stem = dot > 0 ? name.substring(0, dot) : name;
    String extension = dot > 0 ? name.substring(dot) : "";
    Path parent = textFile.toPath().toAbsolutePath().getParent();
    Path target = parent.resolve(stem + "_copy" + extension);
    int counter = 2;
    while (Files.exists(target)) {
      target = parent.resolve(stem + "_copy" + counter + extension);
      counter++;
    }
    Files.copy(textFile.toPath(), target);
    return target.toFile();
  }

  public static File duplicateScript(File scriptFile) throws IOException {
    return duplicateTextFile(scriptFile);
  }

  public static String normalizeRelativeTextPath(String requestedRelativePath) {
    String input = requestedRelativePath == null ? "" : requestedRelativePath.trim().replace('\\', '/');
    while (input.startsWith("/")) input = input.substring(1);
    while (input.startsWith("./")) input = input.substring(2);
    if (input.isBlank()) input = "scripts/story/new_scene.vns";
    if (input.endsWith("/")) input += "new_file.txt";
    String fileName = input.substring(input.lastIndexOf('/') + 1);
    if (!fileName.contains(".")) input += ".vns";
    return input;
  }

  public static String normalizeRelativeScriptPath(String requestedRelativePath) {
    String input = requestedRelativePath == null ? "" : requestedRelativePath.trim().replace('\\', '/');
    while (input.startsWith("/")) input = input.substring(1);
    if (input.startsWith("scripts/")) input = input.substring("scripts/".length());
    if (input.isBlank()) input = "story/new_scene";
    if (input.endsWith("/")) input += "new_scene";
    if (!input.toLowerCase(Locale.ROOT).endsWith(".vns")) input += ".vns";
    return "scripts/" + input;
  }

  private static List<ScriptFileEntry> collectTextFiles(File launchRoot, Path contentRoot) {
    List<ScriptFileEntry> files = new ArrayList<>();
    try {
      collectTextFiles(launchRoot, contentRoot, contentRoot, files, new HashSet<>());
    } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
    }
    files.sort(Comparator.comparing(entry -> entry.relativePath().toLowerCase(Locale.ROOT)));
    return files;
  }

  private static void collectTextFiles(File launchRoot, Path contentRoot, Path dir, List<ScriptFileEntry> out, Set<Path> visited) throws IOException {
    if (!Files.isDirectory(dir)) return;
    Path realDir = dir.toRealPath();
    if (!realDir.startsWith(contentRoot.toRealPath()) || !visited.add(realDir)) return;
    try (var stream = Files.list(dir)) {
      List<Path> children = stream
          .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
          .toList();
      for (Path child : children) {
        if (Files.isDirectory(child)) {
          if (isIgnoredDirectory(child, contentRoot)) continue;
          try {
            collectTextFiles(launchRoot, contentRoot, child, out, visited);
          } catch (IOException ignored) {
            // An unreadable child must not hide the rest of the workspace.
          }
        } else if (Files.isRegularFile(child) && FileEditorTab.supportsTextEditing(child.toFile())) {
          out.add(analyzeTextFile(launchRoot, contentRoot, child));
        }
      }
    }
  }

  private static boolean isIgnoredDirectory(Path dir, Path contentRoot) {
    if (dir == null) return false;
    if (dir.equals(contentRoot)) return false;
    Path name = dir.getFileName();
    return name != null && IGNORED_DIR_NAMES.contains(name.toString().toLowerCase(Locale.ROOT));
  }

  private static ScriptFileEntry analyzeTextFile(File launchRoot, Path contentRoot, Path file) {
    List<String> lines;
    try {
      lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      lines = List.of();
    }

    FileEditorTab.Kind kind = FileEditorTab.detectKind(file.toFile());
    if (kind == null) kind = FileEditorTab.Kind.OTHER;
    boolean vnsLike = kind == FileEditorTab.Kind.VNS;

    int includeCount = 0;
    List<String> labelNames = new ArrayList<>();
    Map<String, Integer> labelLines = new LinkedHashMap<>();
    List<String> includeTargets = new ArrayList<>();
    if (vnsLike) {
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        Matcher labelMatcher = LABEL_PATTERN.matcher(line);
        if (labelMatcher.find()) {
          String name = labelMatcher.group(1);
          labelNames.add(name);
          labelLines.put(name, i + 1);
        }
        Matcher includeMatcher = INCLUDE_PATTERN.matcher(line);
        if (includeMatcher.find()) {
          includeCount++;
          String target = includeMatcher.group(1).trim();
          if (!target.isEmpty()) includeTargets.add(target);
        }
      }
    }

    String relative = contentRoot.relativize(file).toString().replace('\\', '/');
    String projectRelative = launchRoot.toPath().toAbsolutePath().normalize().relativize(file).toString().replace('\\', '/');
    long sizeBytes = safeSize(file);
    long lastModifiedMillis = safeLastModified(file);
    String displayName = file.getFileName().toString();

    return new ScriptFileEntry(
        file.toFile(),
        displayName,
        relative,
        projectRelative,
        lines.size(),
        labelNames.size(),
        includeCount,
        List.copyOf(labelNames),
        Map.copyOf(labelLines),
        List.copyOf(includeTargets),
        kind,
        sizeBytes,
        lastModifiedMillis);
  }

  private static long safeSize(Path file) {
    try {
      return Files.size(file);
    } catch (IOException ex) {
      return 0L;
    }
  }

  private static long safeLastModified(Path file) {
    try {
      return Files.getLastModifiedTime(file).toMillis();
    } catch (IOException ex) {
      return 0L;
    }
  }

  private static String defaultTemplate(Path target) {
    String fileName = target.getFileName().toString();
    String lowerName = fileName.toLowerCase(Locale.ROOT);
    int dot = fileName.lastIndexOf('.');
    String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
    String title = stem.replace('_', ' ').replace('-', ' ').trim();
    if (title.isBlank()) title = "New File";
    title = Character.toUpperCase(title.charAt(0)) + title.substring(1);

    if ("jvn.project".equals(lowerName)) {
      return "name=" + title + System.lineSeparator()
          + "entryVns=scripts/story/prologue.vns" + System.lineSeparator();
    }
    if (lowerName.endsWith(".jes")) {
      return "scene \"" + stem + "\" {" + System.lineSeparator() + "}" + System.lineSeparator();
    }
    if (lowerName.endsWith(".vns")) {
      return "# " + title + System.lineSeparator() + System.lineSeparator()
          + "@label start" + System.lineSeparator()
          + "narrator: TODO" + System.lineSeparator();
    }
    if (lowerName.endsWith(".timeline")) {
      return "# " + title + System.lineSeparator();
    }
    if (lowerName.endsWith(".menu")) {
      return "# " + title + System.lineSeparator()
          + "title=" + title + System.lineSeparator();
    }
    if (lowerName.endsWith(".layout") || lowerName.endsWith(".style") || lowerName.endsWith(".theme")
        || lowerName.endsWith(".registry") || lowerName.endsWith(".settings")
        || lowerName.endsWith(".properties") || lowerName.endsWith(".cfg")
        || lowerName.endsWith(".ini") || lowerName.endsWith(".toml")
        || lowerName.endsWith(".yaml") || lowerName.endsWith(".yml")) {
      return "# " + title + System.lineSeparator();
    }
    if (lowerName.endsWith(".json")) {
      return "{\n}\n";
    }
    if (lowerName.endsWith(".xml")) {
      return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    }
    return "";
  }

  private static String ensureExtension(String value, String extension) {
    String sanitized = value == null ? "" : value.trim();
    if (sanitized.isEmpty()) return extension.startsWith(".") ? "untitled" + extension : "untitled." + extension;
    if (sanitized.toLowerCase(Locale.ROOT).endsWith(extension)) return sanitized;
    return sanitized + extension;
  }

  public record ScriptFileEntry(
      File file,
      String displayName,
      String relativePath,
      String projectRelativePath,
      int lineCount,
      int labelCount,
      int includeCount,
      List<String> labelNames,
      Map<String, Integer> labelLineNumbers,
      List<String> includeTargets,
      FileEditorTab.Kind kind,
      long sizeBytes,
      long lastModifiedMillis) {
  }

  public record WorkspaceSnapshot(
      File launchRoot,
      Path contentRoot,
      List<ScriptFileEntry> scripts,
      int folderCount,
      int totalLabelCount) {

    public boolean hasContentRoot() {
      return contentRoot != null;
    }

    public boolean hasScriptsRoot() {
      return hasContentRoot();
    }

    public Path scriptsRoot() {
      return contentRoot;
    }

    public List<ScriptFileEntry> includedBy(String relativePath) {
      if (relativePath == null || relativePath.isBlank() || scripts == null) return List.of();
      String needle = relativePath.replace('\\', '/');
      String fileName = needle.contains("/") ? needle.substring(needle.lastIndexOf('/') + 1) : needle;
      List<ScriptFileEntry> result = new ArrayList<>();
      for (ScriptFileEntry entry : scripts) {
        if (entry.kind() != FileEditorTab.Kind.VNS) continue;
        for (String target : entry.includeTargets()) {
          String normalized = target.replace('\\', '/').trim();
          if (normalized.equals(needle) || normalized.equals(fileName)
              || normalized.endsWith("/" + fileName)) {
            result.add(entry);
            break;
          }
        }
      }
      return result;
    }

    public ScriptFileEntry findByRelativePath(String relativePath) {
      if (relativePath == null || scripts == null) return null;
      String needle = relativePath.replace('\\', '/').trim();
      for (ScriptFileEntry entry : scripts) {
        if (entry.relativePath().equals(needle) || entry.projectRelativePath().equals(needle)) return entry;
        String fileName = entry.relativePath().contains("/")
            ? entry.relativePath().substring(entry.relativePath().lastIndexOf('/') + 1)
            : entry.relativePath();
        if (fileName.equals(needle)) return entry;
      }
      return null;
    }
  }
}
