package com.jvn.editor.ui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.vn.VnArgTokenizer;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.script.MultipleParseErrorsException;
import com.jvn.core.vn.script.VnParseException;
import com.jvn.core.vn.script.VnScriptParser;

/**
 * Shared VNS static analysis used by editor features (code lint, diagnostics panel, and flow map).
 */
public final class VnsScriptAnalyzer {
  private static final Pattern LABEL_PATTERN = Pattern.compile("^\\s*(?:@label|label)\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern BG_DECL_PATTERN = Pattern.compile("^\\s*@background\\s+(\\S+)\\s+(.+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARIMG_PATTERN = Pattern.compile(
      "^\\s*@charimg\\s+(\\S+)\\s+(\\S+)\\s+(.+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARLAYER_PATTERN = Pattern.compile(
      "^\\s*@charlayer\\s+(\\S+)\\s+(\\S+)\\s+(.+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern COMMAND_PATTERN = Pattern.compile("^\\s*\\[(.+)]\\s*$");
  private static final Pattern CHOICE_IF_SUFFIX_PATTERN = Pattern.compile("^(.*)\\[if\\s+(.+)]\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern IF_GOTO_PATTERN = Pattern.compile("^(.+?)\\s+goto\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern PARSE_LINE_PATTERN = Pattern.compile("\\bat line (\\d+)\\b", Pattern.CASE_INSENSITIVE);

  private VnsScriptAnalyzer() {
  }

  public static Analysis analyze(String text, File projectRoot) {
    return analyze(text, projectRoot, null);
  }

  public static Analysis analyze(String text, File projectRoot, File sourceFile) {
    return analyze(text, projectRoot, sourceFile,
        EditorPreferences.DEFAULT_LARGE_TIMELINE_BLOCK_ACTION_THRESHOLD);
  }

  public static Analysis analyze(
      String text, File projectRoot, File sourceFile, int largeTimelineBlockActionThreshold) {
    return analyze(text, projectRoot, sourceFile, largeTimelineBlockActionThreshold, null);
  }

  static Analysis analyze(String text, File projectRoot, File sourceFile, int largeTimelineBlockActionThreshold,
                          java.util.function.Consumer<String> onIncludedSource) {
    String source = text == null ? "" : text;
    List<Diagnostic> diagnostics = new ArrayList<>();
    File effectiveRoot = resolveProjectRoot(projectRoot, sourceFile);
    String analyzedSourceName = effectiveRoot == null || !effectiveRoot.exists()
        ? "<string>"
        : resolveSourceName(effectiveRoot, sourceFile);

    // Strict parser diagnostics first.
    VnScenario parsedScenario = null;
    try {
      parsedScenario = parseWithIncludeResolver(source, projectRoot, sourceFile, onIncludedSource);
    } catch (MultipleParseErrorsException mex) {
      for (VnParseException ex : mex.getErrors()) {
        boolean fromIncludedFile = !sameSource(ex.getSourceName(), analyzedSourceName);
        int line = fromIncludedFile
            ? findIncludeLine(source, ex.getSourceName())
            : ex.getLineNumber() - 1;
        int start = 0;
        int end = Math.max(0, source.length());
        if (line >= 0) {
          int[] bounds = fromIncludedFile
              ? lineBounds(source, line)
              : parseDiagnosticBounds(source, line, ex);
          start = bounds[0];
          end = bounds[1];
        }
        String message = ex.getDetailMessage();
        if (fromIncludedFile) {
          message = "In included file " + ex.getSourceName() + ":" + ex.getLineNumber()
              + ": " + message;
          if (ex.getRawLine() != null && !ex.getRawLine().isBlank()) {
            message += " -> " + ex.getRawLine().trim();
          }
        }
        diagnostics.add(Diagnostic.error(
            "parse_error",
            message,
            start,
            end,
            Math.max(line, 0),
            null,
            null,
            -1
        ));
      }
    } catch (Exception ex) {
      int line = parseLineFromMessage(ex.getMessage()) - 1;
      int start = 0;
      int end = Math.max(0, source.length());
      if (line >= 0) {
        int[] bounds = lineBounds(source, line);
        start = bounds[0];
        end = bounds[1];
      }
      diagnostics.add(Diagnostic.error(
          "parse_error",
          ex.getMessage(),
          start,
          end,
          Math.max(line, 0),
          null,
          null,
          -1
      ));
    }

    List<LineInfo> lines = splitLines(source);
    Map<String, LabelNode> labelsByName = new LinkedHashMap<>();
    List<LabelRef> refs = new ArrayList<>();
    Map<String, String> backgrounds = new LinkedHashMap<>();

    for (LineInfo line : lines) {
      String trimmed = line.trimmed();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

      Matcher labelMatcher = LABEL_PATTERN.matcher(line.text);
      if (labelMatcher.matches()) {
        String name = labelMatcher.group(1);
        int tokenStart = line.start + safeIndexOf(line.text, name, 0);
        int tokenEnd = tokenStart + name.length();
        labelsByName.putIfAbsent(name, new LabelNode(name, line.index, tokenStart, tokenEnd));
      }

      Matcher bgMatcher = BG_DECL_PATTERN.matcher(line.text);
      if (bgMatcher.matches()) {
        String bgId = bgMatcher.group(1).trim();
        String path = bgMatcher.group(2).trim();
        backgrounds.put(bgId, path);

        if (!assetExists(projectRoot, path)) {
          int pathStart = line.start + safeIndexOf(line.text, path, 0);
          diagnostics.add(Diagnostic.error(
              "missing_asset",
              "Missing background asset: " + path,
              pathStart,
              pathStart + path.length(),
              line.index,
              null,
              path,
              -1
          ));
        }
      }

      Matcher charImageMatcher = CHARIMG_PATTERN.matcher(line.text);
      if (charImageMatcher.matches()) {
        addAssetDiagnostic(
            projectRoot,
            diagnostics,
            line,
            stripWrappingQuotes(charImageMatcher.group(3)),
            "character image",
            "missing_character_asset");
      }

      Matcher charLayerMatcher = CHARLAYER_PATTERN.matcher(line.text);
      if (charLayerMatcher.matches()) {
        addAssetDiagnostic(
            projectRoot,
            diagnostics,
            line,
            stripWrappingQuotes(charLayerMatcher.group(3)),
            "character layer",
            "missing_character_asset");
      }

      if (trimmed.startsWith(">")) {
        addChoiceReference(line, trimmed.substring(1).trim(), refs);
        continue;
      }

      Matcher cmdMatcher = COMMAND_PATTERN.matcher(trimmed);
      if (!cmdMatcher.matches()) continue;

      String body = cmdMatcher.group(1).trim();
      if (body.isEmpty()) continue;
      String[] parts = body.split("\\s+", 2);
      String cmd = parts[0].toLowerCase(Locale.ROOT);
      String arg = parts.length > 1 ? parts[1].trim() : "";

      if (("jump".equals(cmd) || "goto".equals(cmd)) && !arg.isBlank()) {
        String target = firstToken(arg);
        if (isLocalGotoTarget(target)) {
          int st = line.start + safeIndexOf(line.text, target, 0);
          refs.add(new LabelRef(target, st, st + target.length(), line.index));
        } else if ("goto".equals(cmd) && projectRoot != null) {
          addScriptAssetDiagnostic(projectRoot, diagnostics, line, cmd, arg);
        }
      } else if (("gosub".equals(cmd) || isSingleLabelCall(cmd, arg)) && !arg.isBlank()) {
        String target = firstToken(arg);
        int st = line.start + safeIndexOf(line.text, target, 0);
        refs.add(new LabelRef(target, st, st + target.length(), line.index));
      } else if ("if".equals(cmd) && !arg.isBlank()) {
        Matcher m = IF_GOTO_PATTERN.matcher(arg);
        if (m.matches()) {
          String target = m.group(2).trim();
          int st = line.start + safeIndexOf(line.text, target, 0);
          refs.add(new LabelRef(target, st, st + target.length(), line.index));
        }
      } else if ("choice".equals(cmd) && !arg.isBlank()) {
        String[] segs = arg.split("\\|");
        for (String seg : segs) {
          addChoiceReference(line, seg == null ? "" : seg.trim(), refs);
        }
      } else if (("bg".equals(cmd) || "background".equals(cmd)) && !arg.isBlank()) {
        String bgId = firstToken(arg);
        if (!backgrounds.containsKey(bgId)) {
          int st = line.start + safeIndexOf(line.text, bgId, 0);
          diagnostics.add(Diagnostic.error(
              "missing_asset",
              "Unknown background id: " + bgId,
              st,
              st + bgId.length(),
              line.index,
              null,
              null,
              -1
          ));
        }
      } else if (isAudioAssetCommand(cmd)) {
        String path = audioAssetPath(cmd, arg);
        addAssetDiagnostic(
            projectRoot,
            diagnostics,
            line,
            path,
            audioAssetKind(cmd),
            "missing_audio_asset");
      } else if (projectRoot != null) {
        addScriptAssetDiagnostic(projectRoot, diagnostics, line, cmd, arg);
      }
    }

    for (LabelRef ref : refs) {
      if (!labelsByName.containsKey(ref.label)) {
        diagnostics.add(Diagnostic.error(
            "undefined_label",
            "Undefined label: " + ref.label,
            ref.start,
            ref.end,
            ref.line,
            ref.label,
            null,
            -1
        ));
      }
    }

    List<LabelNode> labels = new ArrayList<>(labelsByName.values());
    if (parsedScenario != null) {
      diagnostics.addAll(VnsTimelineDiagnostics.analyze(
          source, parsedScenario, largeTimelineBlockActionThreshold));
    }

    labels.sort((a, b) -> Integer.compare(a.line(), b.line()));
    List<FlowEdge> edges = computeFlowEdges(source, lines, labelsByName, labels);
    addUnreachableStatementDiagnostics(lines, diagnostics);

    if (!labels.isEmpty()) {
      String startLabel = pickStartLabel(labelsByName, labels);
      Set<String> reachable = reachableLabels(startLabel, edges);
      for (int i = 0; i < labels.size(); i++) {
        LabelNode label = labels.get(i);
        if (reachable.contains(label.name())) continue;
        int blockEnd = i + 1 < labels.size() ? labels.get(i + 1).tokenStart() : source.length();
        diagnostics.add(Diagnostic.warning(
            "unreachable_label",
            "Unreachable label: " + label.name(),
            label.tokenStart(),
            label.tokenEnd(),
            label.line(),
            label.name(),
            null,
            blockEnd
        ));
      }
    }

    diagnostics = deduplicateDiagnostics(diagnostics);
    diagnostics.sort((a, b) -> {
      int sa = a.start();
      int sb = b.start();
      if (sa != sb) return Integer.compare(sa, sb);
      if (a.warning() != b.warning()) return a.warning() ? 1 : -1;
      return a.kind().compareTo(b.kind());
    });

    String startLabel = labels.isEmpty() ? null : pickStartLabel(labelsByName, labels);

    // Compute script statistics
    ScriptStats stats = computeStats(lines, labels, edges);

    return new Analysis(source, diagnostics, labels, edges, startLabel, backgrounds, stats);
  }

  private static boolean sameSource(String left, String right) {
    if (left == null || right == null) return left == null && right == null;
    return left.replace('\\', '/').equals(right.replace('\\', '/'));
  }

  private static int findIncludeLine(String source, String includedSourceName) {
    String target = includedSourceName == null ? "" : includedSourceName.replace('\\', '/');
    List<LineInfo> lines = splitLines(source);
    for (LineInfo line : lines) {
      String trimmed = line.trimmed();
      if (!trimmed.toLowerCase(Locale.ROOT).startsWith("@include ")) continue;
      String value = trimmed.substring("@include".length()).trim();
      if (value.length() >= 2
          && ((value.startsWith("\"") && value.endsWith("\""))
              || (value.startsWith("'") && value.endsWith("'")))) {
        value = value.substring(1, value.length() - 1);
      }
      String normalized = value.replace('\\', '/');
      while (normalized.startsWith("/")) normalized = normalized.substring(1);
      if (target.equals(normalized) || target.endsWith("/" + normalized)) {
        return line.index;
      }
    }
    return 0;
  }

  private static int[] parseDiagnosticBounds(String source, int line, VnParseException error) {
    int[] lineRange = lineBounds(source, line);
    if (error == null
        || error.getDetailMessage() == null
        || !error.getDetailMessage().startsWith("Unexpected content after timeline block:")) {
      return lineRange;
    }

    String rawLine = error.getRawLine();
    int colon = error.getDetailMessage().indexOf(':');
    String trailing = colon < 0 ? "" : error.getDetailMessage().substring(colon + 1).trim();
    if (rawLine == null || trailing.isEmpty()) return lineRange;

    int relativeStart = rawLine.lastIndexOf(trailing);
    if (relativeStart < 0) return lineRange;
    int absoluteStart = Math.min(lineRange[1], lineRange[0] + relativeStart);
    int absoluteEnd = Math.min(lineRange[1], absoluteStart + trailing.length());
    return new int[] {absoluteStart, Math.max(absoluteStart + 1, absoluteEnd)};
  }

  private static VnScenario parseWithIncludeResolver(String source, File projectRoot, File sourceFile,
                                                     java.util.function.Consumer<String> onIncludedSource) throws Exception {
    VnScriptParser parser = new VnScriptParser();
    File effectiveRoot = resolveProjectRoot(projectRoot, sourceFile);
    if (effectiveRoot == null || !effectiveRoot.exists()) {
      return parser.parseFromString(source);
    }

    String sourceName = resolveSourceName(effectiveRoot, sourceFile);
    byte[] bytes = source == null ? new byte[0] : source.getBytes(StandardCharsets.UTF_8);
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      return parser.parse(in, sourceName, includePath -> {
        InputStream included = openIncludeForDiagnostics(effectiveRoot, sourceName, includePath);
        if (onIncludedSource == null) return included;
        try (included) {
          byte[] contents = included.readAllBytes();
          onIncludedSource.accept(new String(contents, StandardCharsets.UTF_8));
          return new ByteArrayInputStream(contents);
        }
      });
    }
  }

  private static File resolveProjectRoot(File projectRoot, File sourceFile) {
    File inferred = inferProjectRootFromScript(sourceFile);
    if (projectRoot == null) {
      if (inferred != null) return inferred;
      return sourceFile == null ? null : sourceFile.getParentFile();
    }
    if (sourceFile == null || inferred == null) return projectRoot;

    try {
      Path suppliedPath = projectRoot.toPath().toAbsolutePath().normalize();
      Path inferredPath = inferred.toPath().toAbsolutePath().normalize();
      Path sourcePath = sourceFile.toPath().toAbsolutePath().normalize();

      boolean suppliedContainsSource = sourcePath.startsWith(suppliedPath);
      boolean suppliedHasScripts = Files.isDirectory(suppliedPath.resolve("scripts"));
      boolean inferredHasScripts = Files.isDirectory(inferredPath.resolve("scripts"));
      if (!suppliedContainsSource) return inferred;
      if (!suppliedHasScripts && inferredHasScripts && suppliedPath.startsWith(inferredPath)) {
        return inferred;
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return projectRoot;
  }

  private static File inferProjectRootFromScript(File sourceFile) {
    if (sourceFile == null) return null;
    Path current = sourceFile.toPath().toAbsolutePath().normalize().getParent();
    while (current != null) {
      Path name = current.getFileName();
      if (name != null && "scripts".equalsIgnoreCase(name.toString())) {
        Path parent = current.getParent();
        return (parent != null ? parent : current).toFile();
      }
      current = current.getParent();
    }
    return null;
  }

  private static String resolveSourceName(File projectRoot, File sourceFile) {
    if (sourceFile == null) return "story/_analysis.vns";
    try {
      Path root = projectRoot.toPath().toAbsolutePath().normalize();
      Path scriptsRoot = root.resolve("scripts").normalize();
      Path file = sourceFile.toPath().toAbsolutePath().normalize();
      if (file.startsWith(scriptsRoot)) {
        return scriptsRoot.relativize(file).toString().replace('\\', '/');
      }
      if (file.startsWith(root)) {
        return root.relativize(file).toString().replace('\\', '/');
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return sourceFile.getName();
  }

  private static InputStream openIncludeForDiagnostics(File projectRoot, String sourceName, String includePath) throws IOException {
    String normalized = includePath == null ? "" : includePath.trim().replace('\\', '/');
    if (normalized.isBlank()) {
      throw new IOException("Include path is empty");
    }

    Path root = projectRoot.toPath().toAbsolutePath().normalize();
    Path scriptsRoot = root.resolve("scripts").normalize();
    if (!Files.isDirectory(scriptsRoot)) {
      scriptsRoot = root;
    }

    List<Path> candidates = new ArrayList<>();
    if (normalized.startsWith("/")) {
      candidates.add(scriptsRoot.resolve(normalized.substring(1)));
    } else {
      if (sourceName != null && sourceName.contains("/")) {
        Path sourceBase = scriptsRoot.resolve(sourceName).normalize().getParent();
        if (sourceBase != null) {
          candidates.add(sourceBase.resolve(normalized));
        }
      }
      candidates.add(scriptsRoot.resolve(normalized));
    }
    candidates.add(root.resolve(normalized));

    for (Path candidate : candidates) {
      if (candidate == null) continue;
      Path resolved = candidate.toAbsolutePath().normalize();
      if (!resolved.startsWith(root)) continue;
      if (Files.isRegularFile(resolved)) {
        return Files.newInputStream(resolved);
      }
    }

    throw new IOException("Included script not found: " + includePath);
  }

  private static String pickStartLabel(Map<String, LabelNode> labelsByName, List<LabelNode> labels) {
    if (labelsByName.containsKey("start")) return "start";
    return labels.isEmpty() ? null : labels.get(0).name();
  }

  private static Set<String> reachableLabels(String startLabel, List<FlowEdge> edges) {
    if (startLabel == null || startLabel.isBlank()) return Set.of();
    Map<String, Set<String>> adjacency = new HashMap<>();
    for (FlowEdge edge : edges) {
      if (!edge.definedTarget()) continue;
      adjacency.computeIfAbsent(edge.fromLabel(), k -> new HashSet<>()).add(edge.toLabel());
    }

    Set<String> reachable = new HashSet<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    queue.add(startLabel);
    while (!queue.isEmpty()) {
      String current = queue.removeFirst();
      if (!reachable.add(current)) continue;
      for (String next : adjacency.getOrDefault(current, Set.of())) {
        if (!reachable.contains(next)) queue.addLast(next);
      }
    }
    return reachable;
  }

  private static List<FlowEdge> computeFlowEdges(String source,
                                                 List<LineInfo> lines,
                                                 Map<String, LabelNode> labelsByName,
                                                 List<LabelNode> orderedLabels) {
    if (labelsByName.isEmpty() || orderedLabels.isEmpty()) return List.of();

    List<FlowEdge> edges = new ArrayList<>();
    Set<String> dedupe = new LinkedHashSet<>();

    for (int idx = 0; idx < orderedLabels.size(); idx++) {
      LabelNode label = orderedLabels.get(idx);
      int startLine = label.line() + 1;
      int endLine = idx + 1 < orderedLabels.size() ? orderedLabels.get(idx + 1).line() : lines.size();

      boolean terminal = false;
      for (int lineIndex = startLine; lineIndex < endLine; lineIndex++) {
        if (lineIndex < 0 || lineIndex >= lines.size()) continue;
        LineInfo line = lines.get(lineIndex);
        String trimmed = line.trimmed();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

        if (trimmed.startsWith(">")) {
          addChoiceEdge(label.name(), line, trimmed.substring(1).trim(), labelsByName, dedupe, edges);
          continue;
        }

        Matcher cmdMatcher = COMMAND_PATTERN.matcher(trimmed);
        if (!cmdMatcher.matches()) continue;
        String body = cmdMatcher.group(1).trim();
        if (body.isEmpty()) continue;
        String[] parts = body.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : "";

        if ("jump".equals(cmd) || "goto".equals(cmd)) {
          String target = firstToken(arg);
          if (isLocalGotoTarget(target)) {
            addEdge(label.name(), target, line.index, FlowEdgeKind.JUMP, labelsByName, dedupe, edges);
          }
          terminal = true;
          break;
        }

        if ("gosub".equals(cmd) || isSingleLabelCall(cmd, arg)) {
          String target = firstToken(arg);
          addEdge(label.name(), target, line.index, FlowEdgeKind.CALL, labelsByName, dedupe, edges);
          continue;
        }

        if ("end".equals(cmd)) {
          terminal = true;
          break;
        }

        if ("if".equals(cmd)) {
          Matcher m = IF_GOTO_PATTERN.matcher(arg);
          if (m.matches()) {
            String target = m.group(2).trim();
            addEdge(label.name(), target, line.index, FlowEdgeKind.IF_GOTO, labelsByName, dedupe, edges);
          }
          continue;
        }

        if ("choice".equals(cmd)) {
          String[] segments = arg.split("\\|");
          for (String segment : segments) {
            addChoiceEdge(label.name(), line, segment == null ? "" : segment.trim(), labelsByName, dedupe, edges);
          }
        }
      }

      if (!terminal && idx + 1 < orderedLabels.size()) {
        String nextLabel = orderedLabels.get(idx + 1).name();
        addEdge(label.name(), nextLabel, label.line(), FlowEdgeKind.FALLTHROUGH, labelsByName, dedupe, edges);
      }
    }

    return edges;
  }

  private static void addChoiceEdge(String fromLabel,
                                    LineInfo line,
                                    String segment,
                                    Map<String, LabelNode> labelsByName,
                                    Set<String> dedupe,
                                    List<FlowEdge> edges) {
    LabelRef ref = extractChoiceReference(line, segment);
    if (ref == null) return;
    addEdge(fromLabel, ref.label, line.index, FlowEdgeKind.CHOICE, labelsByName, dedupe, edges);
  }

  private static void addEdge(String fromLabel,
                              String toLabel,
                              int sourceLine,
                              FlowEdgeKind kind,
                              Map<String, LabelNode> labelsByName,
                              Set<String> dedupe,
                              List<FlowEdge> edges) {
    if (fromLabel == null || fromLabel.isBlank() || toLabel == null || toLabel.isBlank()) return;
    boolean definedTarget = labelsByName.containsKey(toLabel);
    String key = fromLabel + "|" + toLabel + "|" + kind + "|" + sourceLine;
    if (!dedupe.add(key)) return;
    edges.add(new FlowEdge(fromLabel, toLabel, sourceLine, kind, definedTarget));
  }

  private static void addChoiceReference(LineInfo line, String segment, List<LabelRef> refs) {
    LabelRef ref = extractChoiceReference(line, segment);
    if (ref != null) refs.add(ref);
  }

  private static LabelRef extractChoiceReference(LineInfo line, String segment) {
    if (segment == null || segment.isBlank()) return null;
    String work = segment.trim();

    Matcher suffix = CHOICE_IF_SUFFIX_PATTERN.matcher(work);
    if (suffix.matches()) {
      work = suffix.group(1).trim();
    }

    int arrow = work.indexOf("->");
    if (arrow < 0) return null;

    String right = work.substring(arrow + 2).trim();
    if (right.isEmpty()) return null;

    String target = firstToken(right);
    int targetInLine = safeIndexOf(line.text, target, 0);
    int start = line.start + targetInLine;
    return new LabelRef(target, start, start + target.length(), line.index);
  }

  private static void addUnreachableStatementDiagnostics(List<LineInfo> lines,
                                                         List<Diagnostic> diagnostics) {
    boolean flowTerminated = false;
    boolean reportedInBlock = false;
    for (LineInfo line : lines) {
      String trimmed = line.trimmed();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
      if (LABEL_PATTERN.matcher(line.text).matches()) {
        flowTerminated = false;
        reportedInBlock = false;
        continue;
      }

      if (flowTerminated) {
        if (!reportedInBlock) {
          int start = line.start + firstNonWhitespaceIndex(line.text);
          diagnostics.add(Diagnostic.warning(
              "unreachable_statement",
              "This statement cannot run because an earlier command already exits this label.",
              start,
              Math.max(start + 1, line.end),
              line.index,
              null,
              null,
              -1
          ));
          reportedInBlock = true;
        }
        continue;
      }

      Matcher commandMatcher = COMMAND_PATTERN.matcher(trimmed);
      if (!commandMatcher.matches()) continue;
      String body = commandMatcher.group(1).trim();
      if (body.isBlank()) continue;
      String command = body.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
      if ("end".equals(command) || "jump".equals(command) || "goto".equals(command)) {
        flowTerminated = true;
      }
    }
  }

  private static int firstNonWhitespaceIndex(String text) {
    if (text == null) return 0;
    for (int i = 0; i < text.length(); i++) {
      if (!Character.isWhitespace(text.charAt(i))) return i;
    }
    return 0;
  }

  private static boolean isAudioAssetCommand(String command) {
    if (command == null) return false;
    return switch (command.toLowerCase(Locale.ROOT)) {
      case "bgm", "bgm_crossfade", "sfx", "voice" -> true;
      default -> false;
    };
  }

  private static String audioAssetKind(String command) {
    if (command == null) return "audio";
    return switch (command.toLowerCase(Locale.ROOT)) {
      case "bgm", "bgm_crossfade" -> "background music";
      case "voice" -> "voice";
      case "sfx" -> "sound effect";
      default -> "audio";
    };
  }

  private static String audioAssetPath(String command, String argument) {
    String[] tokens = VnArgTokenizer.tokenizeToArray(argument);
    if (tokens.length == 0) return "";
    for (String rawToken : tokens) {
      String token = rawToken == null ? "" : rawToken.trim();
      if (token.isBlank()) continue;
      int separator = token.indexOf('=');
      if (separator <= 0) separator = token.indexOf(':');
      if (separator > 0 && separator < token.length() - 1) {
        String key = token.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        if (Set.of("track", "id", "file", "path").contains(key)) {
          return stripWrappingQuotes(token.substring(separator + 1));
        }
        continue;
      }
      return stripWrappingQuotes(token);
    }
    return "";
  }

  private static void addAssetDiagnostic(File projectRoot,
                                         List<Diagnostic> diagnostics,
                                         LineInfo line,
                                         String rawPath,
                                         String assetKind,
                                         String missingKind) {
    String path = rawPath == null ? "" : rawPath.trim();
    if (projectRoot == null || path.isBlank() || isDynamicAssetPath(path)) return;

    int pathStart = line.start + safeIndexOf(line.text, path, firstNonWhitespaceIndex(line.text));
    File direct = new File(path);
    if (direct.isAbsolute()) {
      diagnostics.add(Diagnostic.warning(
          "external_asset_path",
          "Absolute " + assetKind + " path is not portable: " + path,
          pathStart,
          pathStart + path.length(),
          line.index,
          null,
          path,
          -1
      ));
    }

    if (assetExists(projectRoot, path)) return;
    diagnostics.add(Diagnostic.error(
        missingKind,
        "Missing " + assetKind + " asset: " + path,
        pathStart,
        pathStart + path.length(),
        line.index,
        null,
        path,
        -1
    ));
  }

  private static boolean isDynamicAssetPath(String path) {
    String normalized = path == null ? "" : path.trim().toLowerCase(Locale.ROOT);
    return normalized.isBlank()
        || normalized.contains("${")
        || normalized.startsWith("$")
        || normalized.startsWith("http://")
        || normalized.startsWith("https://")
        || normalized.startsWith("classpath:")
        || normalized.startsWith("data:");
  }

  private static String stripWrappingQuotes(String value) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.length() >= 2) {
      char first = trimmed.charAt(0);
      char last = trimmed.charAt(trimmed.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return trimmed.substring(1, trimmed.length() - 1);
      }
    }
    return trimmed;
  }

  private static boolean assetExists(File projectRoot, String path) {
    if (path == null || path.isBlank()) return false;
    File resolved = resolveAssetPath(projectRoot, path);
    return resolved != null && resolved.exists() && resolved.isFile();
  }

  private static void addScriptAssetDiagnostic(File projectRoot,
                                               List<Diagnostic> diagnostics,
                                               LineInfo line,
                                               String cmd,
                                               String arg) {
    ScriptRef ref = scriptReference(cmd, arg);
    if (ref == null || ref.path() == null || ref.path().isBlank()) return;
    String target = ref.label() == null || ref.label().isBlank()
        ? ref.path()
        : ref.path() + ":" + ref.label();
    int pathStart = line.start + safeIndexOf(line.text, target, safeIndexOf(line.text, ref.path(), 0));
    File scriptFile = resolveAssetPath(projectRoot, ref.path());
    if (scriptFile == null || !scriptFile.isFile()) {
      diagnostics.add(Diagnostic.error(
          "missing_script",
          "Missing " + ref.kind() + " script: " + ref.path(),
          pathStart,
          pathStart + target.length(),
          line.index,
          null,
          ref.path(),
          -1
      ));
      return;
    }
    if ("VNS".equals(ref.kind())
        && ref.label() != null
        && !ref.label().isBlank()
        && !scriptDeclaresLabel(scriptFile, ref.label())) {
      diagnostics.add(Diagnostic.error(
          "missing_script_label",
          "VNS script '" + ref.path() + "' has no label named '" + ref.label() + "'.",
          pathStart,
          pathStart + target.length(),
          line.index,
          ref.label(),
          ref.path(),
          -1
      ));
    }
  }

  private static boolean scriptDeclaresLabel(File scriptFile, String targetLabel) {
    if (scriptFile == null || targetLabel == null || targetLabel.isBlank()) return false;
    try {
      for (String line : Files.readAllLines(scriptFile.toPath(), StandardCharsets.UTF_8)) {
        Matcher matcher = LABEL_PATTERN.matcher(line);
        if (matcher.matches() && targetLabel.equals(matcher.group(1))) return true;
      }
    } catch (Exception ignored) {
      // A readable target is required for reliable cross-script navigation.
    }
    return false;
  }

  private static ScriptRef scriptReference(String cmd, String arg) {
    if (cmd == null) return null;
    String normalized = cmd.toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "load" -> directScriptRef("VNS", firstToken(arg));
      case "goto" -> gotoScriptRef(firstToken(arg));
      case "mainmenu" -> directScriptRef("VNS", firstToken(arg));
      case "jes_push", "jes_replace" -> directScriptRef("JES", firstToken(arg));
      case "vns" -> vnsPayloadRef(arg);
      case "jes" -> jesPayloadRef(arg);
      default -> null;
    };
  }

  private static ScriptRef vnsPayloadRef(String arg) {
    String[] toks = VnArgTokenizer.tokenizeToArray(arg);
    if (toks.length < 2) return null;
    String action = toks[0].toLowerCase(Locale.ROOT);
    if ("push".equals(action) || "replace".equals(action)) return directScriptRef("VNS", toks[1]);
    if ("goto".equals(action)) {
      int colon = toks[1].indexOf(':');
      if (colon > 0 && toks[1].substring(0, colon).contains(".")) {
        return new ScriptRef("VNS", toks[1].substring(0, colon), toks[1].substring(colon + 1));
      }
    }
    return null;
  }

  private static ScriptRef gotoScriptRef(String target) {
    if (target == null || target.isBlank()) return null;
    int colon = target.indexOf(':');
    if (colon <= 0) return null;
    String script = target.substring(0, colon);
    if (script.indexOf('.') < 0 && !script.contains("/")) return null;
    return new ScriptRef("VNS", script, target.substring(colon + 1));
  }

  private static ScriptRef jesPayloadRef(String arg) {
    String[] toks = VnArgTokenizer.tokenizeToArray(arg);
    if (toks.length < 2) return null;
    String action = toks[0].toLowerCase(Locale.ROOT);
    if ("push".equals(action) || "replace".equals(action)) return directScriptRef("JES", toks[1]);
    return null;
  }

  private static ScriptRef directScriptRef(String kind, String path) {
    if (path == null || path.isBlank()) return null;
    return new ScriptRef(kind, path, null);
  }

  private record ScriptRef(String kind, String path, String label) {}

  private static File resolveAssetPath(File projectRoot, String rawPath) {
    if (rawPath == null || rawPath.isBlank()) return null;
    String normalized = rawPath.trim().replace('\\', '/');
    File direct = new File(normalized);
    if (direct.isAbsolute()) return direct;
    if (projectRoot == null) return null;

    List<String> candidates = new ArrayList<>();
    candidates.add(normalized);

    String rel = normalized;
    if (rel.startsWith("./")) rel = rel.substring(2);
    if (rel.startsWith("game/images/")) rel = rel.substring("game/images/".length());
    if (rel.startsWith("images/")) rel = rel.substring("images/".length());
    if (rel.startsWith("assets/")) rel = rel.substring("assets/".length());

    candidates.add("assets/" + rel);
    candidates.add("scripts/" + rel);
    candidates.add("assets/scripts/" + rel);
    candidates.add("assets/images/" + rel);
    candidates.add("assets/backgrounds/" + rel);

    String fileName = rel.contains("/") ? rel.substring(rel.lastIndexOf('/') + 1) : rel;
    if (!fileName.isBlank()) {
      candidates.add("assets/backgrounds/" + fileName);
      candidates.add("assets/images/backgrounds/" + fileName);
      candidates.add("game/images/backgrounds/" + fileName);
    }

    for (String candidate : candidates) {
      File f = new File(projectRoot, candidate);
      if (f.exists()) return f;
    }
    return new File(projectRoot, normalized);
  }

  private static List<Diagnostic> deduplicateDiagnostics(List<Diagnostic> diagnostics) {
    if (diagnostics == null || diagnostics.isEmpty()) return new ArrayList<>();
    List<Diagnostic> unique = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (Diagnostic diagnostic : diagnostics) {
      if (diagnostic == null) continue;
      String key = diagnostic.kind()
          + "|" + diagnostic.start()
          + "|" + diagnostic.end()
          + "|" + diagnostic.warning()
          + "|" + diagnostic.message();
      if (seen.add(key)) unique.add(diagnostic);
    }
    return unique;
  }

  private static int parseLineFromMessage(String message) {
    if (message == null || message.isBlank()) return -1;
    Matcher m = PARSE_LINE_PATTERN.matcher(message);
    if (!m.find()) return -1;
    try {
      return Integer.parseInt(m.group(1));
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return -1;
    }
  }

  private static List<LineInfo> splitLines(String text) {
    List<LineInfo> out = new ArrayList<>();
    if (text == null) {
      out.add(new LineInfo(0, 0, 0, ""));
      return out;
    }
    int lineIndex = 0;
    int start = 0;
    for (int i = 0; i <= text.length(); i++) {
      if (i == text.length() || text.charAt(i) == '\n') {
        String line = text.substring(start, i);
        out.add(new LineInfo(lineIndex, start, i, line));
        lineIndex++;
        start = i + 1;
      }
    }
    if (out.isEmpty()) out.add(new LineInfo(0, 0, 0, ""));
    return out;
  }

  private static int[] lineBounds(String text, int zeroBasedLine) {
    if (zeroBasedLine < 0) return new int[] {0, 0};
    int currentLine = 0;
    int start = 0;
    for (int i = 0; i <= text.length(); i++) {
      if (i == text.length() || text.charAt(i) == '\n') {
        if (currentLine == zeroBasedLine) {
          return new int[] {start, i};
        }
        currentLine++;
        start = i + 1;
      }
    }
    return new int[] {Math.max(0, text.length() - 1), text.length()};
  }

  private static String firstToken(String value) {
    if (value == null) return "";
    String t = value.trim();
    if (t.isEmpty()) return "";
    int sp = t.indexOf(' ');
    return sp < 0 ? t : t.substring(0, sp);
  }

  private static boolean isSingleLabelCall(String cmd, String arg) {
    if (!"call".equals(cmd) || arg == null) return false;
    String t = arg.trim();
    return !t.isEmpty() && t.indexOf(' ') < 0 && t.indexOf('\t') < 0;
  }

  private static boolean isLocalGotoTarget(String target) {
    if (target == null || target.isBlank()) return false;
    return target.indexOf(':') < 0;
  }

  private static int safeIndexOf(String text, String needle, int fallback) {
    if (text == null || needle == null || needle.isEmpty()) return fallback;
    int idx = text.indexOf(needle);
    return idx >= 0 ? idx : fallback;
  }

  private static final class LineInfo {
    final int index;
    final int start;
    final int end;
    final String text;

    LineInfo(int index, int start, int end, String text) {
      this.index = index;
      this.start = start;
      this.end = end;
      this.text = text;
    }

    String trimmed() {
      return text == null ? "" : text.trim();
    }
  }

  private static final class LabelRef {
    final String label;
    final int start;
    final int end;
    final int line;

    LabelRef(String label, int start, int end, int line) {
      this.label = label;
      this.start = start;
      this.end = end;
      this.line = line;
    }
  }

  private static final Pattern DIALOGUE_LINE_PATTERN = Pattern.compile("^\\s*(\\w+)\\s*:\\s*(.+)$");

  private static ScriptStats computeStats(List<LineInfo> lines,
                                           List<LabelNode> labels,
                                           List<FlowEdge> edges) {
    int wordCount = 0;
    int dialogueLineCount = 0;
    Map<String, Integer> characterLineMap = new LinkedHashMap<>();
    int choiceBranchCount = 0;

    for (LineInfo line : lines) {
      String trimmed = line.trimmed();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

      // Count choice branches
      if (trimmed.startsWith(">")) {
        choiceBranchCount++;
        continue;
      }

      // Skip labels and directives
      if (LABEL_PATTERN.matcher(line.text).matches()) continue;
      if (BG_DECL_PATTERN.matcher(line.text).matches()) continue;
      if (trimmed.startsWith("@")) continue;

      // Skip commands (lines like [command args])
      if (COMMAND_PATTERN.matcher(trimmed).matches()) continue;

      // What's left is dialogue or narration
      String dialogueText = trimmed;
      Matcher dialogueMatcher = DIALOGUE_LINE_PATTERN.matcher(trimmed);
      if (dialogueMatcher.matches()) {
        String charId = dialogueMatcher.group(1).trim();
        dialogueText = dialogueMatcher.group(2).trim();
        characterLineMap.merge(charId, 1, Integer::sum);
      }

      dialogueLineCount++;
      // Count words (split on whitespace)
      String[] words = dialogueText.split("\\s+");
      for (String w : words) {
        if (!w.isBlank()) wordCount++;
      }
    }

    // Estimated playtime: ~150 words per minute for reading VN dialogue
    // Plus ~3 seconds per choice interaction
    double minutes = wordCount / 150.0 + (choiceBranchCount * 3.0) / 60.0;

    return new ScriptStats(
        wordCount,
        dialogueLineCount,
        characterLineMap,
        choiceBranchCount,
        labels.size(),
        Math.round(minutes * 10.0) / 10.0
    );
  }

  public record Analysis(String source,
                         List<Diagnostic> diagnostics,
                         List<LabelNode> labels,
                         List<FlowEdge> edges,
                         String startLabel,
                         Map<String, String> backgrounds,
                         ScriptStats stats) {
    public Analysis {
      diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
      labels = labels == null ? List.of() : List.copyOf(labels);
      edges = edges == null ? List.of() : List.copyOf(edges);
      backgrounds = backgrounds == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(backgrounds));
      stats = stats == null ? ScriptStats.EMPTY : stats;
    }

    public List<LanguageDiagnostic> languageDiagnostics() {
      return languageDiagnostics("");
    }

    public List<LanguageDiagnostic> languageDiagnostics(String sourceName) {
      List<LanguageDiagnostic> out = new ArrayList<>();
      for (Diagnostic diagnostic : diagnostics) {
        out.add(diagnostic.toLanguageDiagnostic(source, sourceName));
      }
      return List.copyOf(out);
    }
  }

  /**
   * Aggregated script statistics.
   */
  public record ScriptStats(
      int wordCount,
      int dialogueLineCount,
      Map<String, Integer> characterLineMap,
      int choiceBranchCount,
      int labelCount,
      double estimatedPlaytimeMinutes
  ) {
    static final ScriptStats EMPTY = new ScriptStats(0, 0, Map.of(), 0, 0, 0.0);

    public ScriptStats {
      characterLineMap = characterLineMap == null
          ? Map.of()
          : Collections.unmodifiableMap(new LinkedHashMap<>(characterLineMap));
    }
  }

  public record LabelNode(String name, int line, int tokenStart, int tokenEnd) {
  }

  public enum FlowEdgeKind {
    JUMP,
    CALL,
    CHOICE,
    IF_GOTO,
    FALLTHROUGH
  }

  public record FlowEdge(String fromLabel,
                         String toLabel,
                         int sourceLine,
                         FlowEdgeKind kind,
                         boolean definedTarget) {
  }

  public enum Severity {
    ERROR,
    WARNING,
    /** Lightweight advisory diagnostic (e.g. performance hints) — not a script problem. */
    INFO
  }

  public static final class Diagnostic {
    private final String kind;
    private final String message;
    private final int start;
    private final int end;
    private final int line;
    private final Severity severity;
    private final String label;
    private final String assetPath;
    private final int blockEnd;

    private Diagnostic(String kind,
                       String message,
                       int start,
                       int end,
                       int line,
                       Severity severity,
                       String label,
                       String assetPath,
                       int blockEnd) {
      this.kind = kind == null ? "unknown" : kind;
      this.message = message == null ? "" : message;
      this.start = Math.max(0, start);
      this.end = Math.max(this.start, end);
      this.line = Math.max(0, line);
      this.severity = severity == null ? Severity.ERROR : severity;
      this.label = label;
      this.assetPath = assetPath;
      this.blockEnd = blockEnd;
    }

    public static Diagnostic error(String kind,
                                   String message,
                                   int start,
                                   int end,
                                   int line,
                                   String label,
                                   String assetPath,
                                   int blockEnd) {
      return new Diagnostic(kind, message, start, end, line, Severity.ERROR, label, assetPath, blockEnd);
    }

    public static Diagnostic warning(String kind,
                                     String message,
                                     int start,
                                     int end,
                                     int line,
                                     String label,
                                     String assetPath,
                                     int blockEnd) {
      return new Diagnostic(kind, message, start, end, line, Severity.WARNING, label, assetPath, blockEnd);
    }

    public static Diagnostic info(String kind,
                                  String message,
                                  int start,
                                  int end,
                                  int line,
                                  String label,
                                  String assetPath,
                                  int blockEnd) {
      return new Diagnostic(kind, message, start, end, line, Severity.INFO, label, assetPath, blockEnd);
    }

    public String kind() {
      return kind;
    }

    public LanguageDiagnostic toLanguageDiagnostic(String source, String sourceName) {
      LanguageDiagnostic.Severity languageSeverity = switch (severity) {
        case WARNING -> LanguageDiagnostic.Severity.WARNING;
        case INFO -> LanguageDiagnostic.Severity.INFO;
        case ERROR -> LanguageDiagnostic.Severity.ERROR;
      };
      int computedColumn = columnForOffset(source, start);
      return new LanguageDiagnostic(
          "vns",
          sourceName,
          languageSeverity,
          kind,
          message,
          start,
          end,
          line,
          computedColumn);
    }

    public String message() {
      return message;
    }

    public int start() {
      return start;
    }

    public int end() {
      return end;
    }

    public int line() {
      return line;
    }

    public boolean warning() {
      return severity == Severity.WARNING;
    }

    public boolean info() {
      return severity == Severity.INFO;
    }

    public Severity severity() {
      return severity;
    }

    public String label() {
      return label;
    }

    public String assetPath() {
      return assetPath;
    }

    public int blockEnd() {
      return blockEnd;
    }
  }

  private static int columnForOffset(String source, int offset) {
    if (source == null || source.isEmpty()) return 0;
    int clamped = Math.max(0, Math.min(offset, source.length()));
    int lineStart = source.lastIndexOf('\n', Math.max(0, clamped - 1));
    return clamped - (lineStart < 0 ? 0 : lineStart + 1);
  }
}
