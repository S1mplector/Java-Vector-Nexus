package com.jvn.core.vn.script;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.animation.Easing;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.Choice;
import com.jvn.core.vn.DialogueLine;
import com.jvn.core.vn.LayeredCharacterResolver;
import com.jvn.core.vn.VnArgTokenizer;
import com.jvn.core.vn.VnConditionEvaluator;
import com.jvn.core.vn.VnParticleCommand;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.VnTransition;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.stage.VnStagePresetLoader;
import com.jvn.core.diagnostics.runtime_logs.warnings.WarningSubscriber;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_facades.WarningManager;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_factories.UnknownExpressionWarningFactory;

/**
 * Parses text-based VN scripts into {@link VnScenario} objects.
 */
public class VnScriptParser {
  private static final Pattern SCENARIO_PATTERN = Pattern.compile("^@scenario\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARACTER_PATTERN = Pattern.compile(
      "^@character\\s+(\\S+)\\s+\"([^\"]*)\"(?:\\s+(.+))?$",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern BACKGROUND_PATTERN = Pattern.compile("^@background\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARIMG_PATTERN = Pattern.compile("^@charimg\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARLAYER_PATTERN = Pattern.compile("^@charlayer\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARGROUP_PATTERN = Pattern.compile("^@chargroup\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARPRESET_PATTERN = Pattern.compile("^@charpreset\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DISPLAY_PRESET_PATTERN = Pattern.compile("^@displaypreset\\s+(\\S+)(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DISPLAY_PRESET_ENTRY_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_.:-]*)\\s*(?:=|:)\\s*(.+)$");
  private static final Pattern STAGE_PRESET_PATTERN = Pattern.compile("^@stagepreset\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern GROUP_PATTERN = Pattern.compile("^@group\\s+(\\S+)(?:\\s+(\\S+))?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern VAR_PATTERN = Pattern.compile("^@var\\s+(\\S+)(?:\\s*=\\s*(.+)|\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LABEL_PATTERN = Pattern.compile("^@label\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LABEL_LEGACY_PATTERN = Pattern.compile("^label\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DIALOGUE_PATTERN = Pattern.compile("^([^:]+):\\s*(.+)$");
  private static final Pattern DIALOGUE_QUOTED_PATTERN = Pattern.compile("^(\\S+)\\s+\"((?:[^\"\\\\]|\\\\.)*)\"$");
  private static final Pattern COMMAND_PATTERN = Pattern.compile("^\\[(.+)]$");
  private static final Pattern POSITION_PATTERN = Pattern.compile("^@position\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DEFINE_PATTERN = Pattern.compile("^@define\\s+(\\S+)(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern INCLUDE_PATTERN = Pattern.compile("^@include\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXTERNAL_PATTERN = Pattern.compile("^@external\\s+(\\S+)(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DEFINE_SUB_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
  private static final Pattern CHOICE_CONDITION_SUFFIX_PATTERN = Pattern.compile("^(.*)\\[if\\s+(.+)]\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern IF_GOTO_PATTERN = Pattern.compile("^(.+?)\\s+goto\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LABEL_NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.:-]*$");
  private static final Pattern JAVA_IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
  private static final Pattern JAVA_IMPORT_PATTERN = Pattern.compile("(?:static\\s+)?[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*(?:\\.\\*)?");
  private static final Pattern JAVA_TYPE_PATTERN = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$.]*(?:\\s*<[^;{}()]+>)?(?:\\s*\\[\\s*])*");

  private final WarningManager warningManager = new WarningManager();
  private final WarningSubscriber warningSubscriber = new WarningSubscriber(warningManager);

  public interface IncludeResolver {
    InputStream open(String path) throws IOException;
  }

  private boolean isIntegerToken(String token) {
    if (token == null || token.isBlank()) return false;
    int start = (token.charAt(0) == '-') ? 1 : 0;
    if (start >= token.length()) return false;
    for (int i = start; i < token.length(); i++) {
      if (!Character.isDigit(token.charAt(i))) return false;
    }
    return true;
  }

  private boolean isBooleanToken(String token) {
    if (token == null || token.isBlank()) return false;
    String t = token.trim().toLowerCase();
    return "true".equals(t) || "false".equals(t)
        || "on".equals(t) || "off".equals(t)
        || "1".equals(t) || "0".equals(t)
        || "yes".equals(t) || "no".equals(t);
  }

  private boolean parseBooleanToken(String token) {
    String t = token.trim().toLowerCase();
    return "true".equals(t) || "on".equals(t) || "1".equals(t) || "yes".equals(t);
  }

  private String quoteTokenIfNeeded(String token) {
    if (token == null) return "\"\"";
    boolean needsQuotes = token.isBlank()
        || token.chars().anyMatch(Character::isWhitespace)
        || token.indexOf('"') >= 0
        || token.indexOf('\\') >= 0;
    if (!needsQuotes) return token;
    return "\"" + token.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private float parseVolumeToken(String token,
                                 String sourceName,
                                 int lineNumber,
                                 String rawLine,
                                 String commandName) throws IOException {
    final float volume;
    try {
      volume = Float.parseFloat(token);
    } catch (NumberFormatException ex) {
      throw parseError(sourceName, lineNumber, commandName + " volume must be numeric", rawLine);
    }
    if (volume < 0f || volume > 1f) {
      throw parseError(sourceName, lineNumber, commandName + " volume must be between 0 and 1", rawLine);
    }
    return volume;
  }

  private float parseUnitRangeToken(String token,
                                    String sourceName,
                                    int lineNumber,
                                    String rawLine,
                                    String commandName,
                                    String fieldName) throws IOException {
    final float value;
    try {
      value = Float.parseFloat(token);
    } catch (NumberFormatException ex) {
      throw parseError(sourceName, lineNumber, commandName + " " + fieldName + " must be numeric", rawLine);
    }
    if (value < 0f || value > 1f) {
      throw parseError(sourceName, lineNumber, commandName + " " + fieldName + " must be between 0 and 1", rawLine);
    }
    return value;
  }

  private KeyValueOption parseKeyValueOption(String token,
                                             String sourceName,
                                             int lineNumber,
                                             String rawLine,
                                             String commandName) throws IOException {
    if (token == null || token.isBlank()) {
      throw parseError(sourceName, lineNumber, commandName + " has an empty option token", rawLine);
    }
    int eq = token.indexOf('=');
    int colon = token.indexOf(':');
    int sep;
    if (eq > 0 && colon > 0) sep = Math.min(eq, colon);
    else sep = Math.max(eq, colon);
    if (sep <= 0 || sep >= token.length() - 1) {
      throw parseError(sourceName, lineNumber, commandName + " options must use key:value or key=value syntax", rawLine);
    }
    String key = token.substring(0, sep).trim().toLowerCase();
    String value = token.substring(sep + 1).trim();
    if (key.isEmpty() || value.isEmpty()) {
      throw parseError(sourceName, lineNumber, commandName + " has malformed option: " + token, rawLine);
    }
    return new KeyValueOption(key, value);
  }

  private String formatNumber(double value) {
    if (Math.abs(value - Math.rint(value)) < 1e-9) {
      return Long.toString((long) Math.rint(value));
    }
    String s = String.format(java.util.Locale.ROOT, "%.4f", value);
    return s.replaceAll("0+$", "").replaceAll("\\.$", "");
  }

  private static class ParseState {
    String scenarioId = "untitled";
    VnScenarioBuilder builder;
    boolean scenarioDeclared = false;
    boolean contentEmitted = false;
    List<Choice> pendingChoices = new ArrayList<>();
    Map<String, com.jvn.core.vn.VnCharacter.Builder> charBuilders = new HashMap<>();
    Map<String, Map<String, String>> charLayers = new HashMap<>();
    Map<String, String> defines = new HashMap<>();
    Map<String, LabelDeclaration> declaredLabels = new HashMap<>();
    List<LabelReference> labelReferences = new ArrayList<>();
    Deque<ConditionalBlock> conditionalBlocks = new ArrayDeque<>();
    int syntheticLabelCounter = 0;
    Map<String, CharacterPosition> customPositions = new HashMap<>();
    Map<String, String> inlineCompositeExpressions = new HashMap<>();
    Map<String, DisplayPresetDef> displayPresets = new HashMap<>();
    String activeDisplayPresetId;
    String pendingVoiceTrackId;
    List<VnParseException> errors = new ArrayList<>();
    int currentSourceLine;

    boolean insideMenu = false;
    List<BufferedLine> menuBuffer = new ArrayList<>();

    boolean insideJava = false;
    StringBuilder javaBuffer = new StringBuilder();
    int javaBlockStartLine = 0;
    String javaBlockStartSource = null;
    String javaBlockClassName = null; // non-null for [java class X] blocks

    boolean insideInitJava = false;
    StringBuilder initJavaBuffer = new StringBuilder();
    int initJavaBlockStartLine = 0;
    String initJavaBlockStartSource = null;

    List<String> javaImports = new ArrayList<>();
    List<String> javaBinds = new ArrayList<>();

    CharacterPosition getCustomPosition(String name) {
      if (name == null || name.isBlank()) return null;
      return customPositions.get(name.trim().toLowerCase());
    }
  }

  private static final class LabelDeclaration {
    final String source;
    final int line;
    final boolean synthetic;

    LabelDeclaration(String source, int line, boolean synthetic) {
      this.source = source;
      this.line = line;
      this.synthetic = synthetic;
    }
  }

  private static final class BufferedLine {
    final String rawLine;
    final String sourceName;
    final int lineNumber;

    BufferedLine(String rawLine, String sourceName, int lineNumber) {
      this.rawLine = rawLine;
      this.sourceName = sourceName;
      this.lineNumber = lineNumber;
    }
  }

  private record KeyValueOption(String key, String value) {}
  private record LayerGroupOptions(String parentId, double pivotX, double pivotY, boolean hasPivot, String spec) {}

  private static final class LabelReference {
    final String label;
    final String source;
    final int line;
    final String rawLine;
    final String kind;

    LabelReference(String label, String source, int line, String rawLine, String kind) {
      this.label = label;
      this.source = source;
      this.line = line;
      this.rawLine = rawLine;
      this.kind = kind;
    }
  }

  private static final class ConditionalBlock {
    String falseLabel;
    final String endLabel;
    boolean elseSeen;
    final String source;
    final int line;

    ConditionalBlock(String falseLabel, String endLabel, String source, int line) {
      this.falseLabel = falseLabel;
      this.endLabel = endLabel;
      this.source = source;
      this.line = line;
      this.elseSeen = false;
    }
  }

  private static final class ParsedChoice {
    final String text;
    final String target;
    final String condition;

    ParsedChoice(String text, String target, String condition) {
      this.text = text;
      this.target = target;
      this.condition = condition;
    }

    Choice toChoice() {
      Choice.Builder b = Choice.builder().text(text);
      if (condition != null && !condition.isBlank()) b.condition(condition);
      if (target != null && !target.isBlank()) b.targetLabel(target);
      return b.build();
    }
  }

  private static final class IfGoto {
    final String expression;
    final String label;

    IfGoto(String expression, String label) {
      this.expression = expression;
      this.label = label;
    }
  }

  private record LayerReference(String path, String layerId) {}
  private record LayerPresetSpec(String pathSpec, List<String> layerIds) {}
  private record DisplayPresetEntry(String slotId,
                                    String characterId,
                                    CharacterPosition position,
                                    String expression,
                                    Integer layerOrder) {}

  private static final class DisplayPresetDef {
    final String id;
    final String source;
    final int line;
    final String rawLine;
    final List<DisplayPresetEntry> entries = new ArrayList<>();

    DisplayPresetDef(String id, String source, int line, String rawLine) {
      this.id = id;
      this.source = source;
      this.line = line;
      this.rawLine = rawLine;
    }
  }

  public VnScenario parse(InputStream input) throws IOException {
    return parse(input, "<input>", null);
  }

  public VnScenario parse(InputStream input, String sourceName, IncludeResolver resolver) throws IOException {
    ParseState state = new ParseState();
    Deque<String> includeStack = new ArrayDeque<>();
    String src = normalizeSourceName(sourceName);
    includeStack.push(src);
    try {
      parseInto(input, src, resolver, state, includeStack);
    } finally {
      includeStack.pop();
    }

    if (!state.conditionalBlocks.isEmpty()) {
      ConditionalBlock open = state.conditionalBlocks.peek();
      state.errors.add(parseError(open.source, open.line, "Unclosed [if] block (missing [endif])", "[if ...]"));
    }

    if (state.insideJava) {
      state.errors.add(parseError(state.javaBlockStartSource, state.javaBlockStartLine,
          "Unclosed [java] block (missing [/java])", "[java]"));
    }

    if (state.insideInitJava) {
      state.errors.add(parseError(state.initJavaBlockStartSource, state.initJavaBlockStartLine,
          "Unclosed [init java] block (missing [/init])", "[init java]"));
    }

    for (DisplayPresetDef preset : state.displayPresets.values()) {
      if (preset.entries.isEmpty()) {
        state.errors.add(parseError(preset.source, preset.line,
            "@displaypreset '" + preset.id + "' must contain at least one slot entry", preset.rawLine));
      }
    }

    ensureBuilder(state);
    flushChoices(state.builder, state.pendingChoices);
    flushPendingVoice(state);
    
    try {
      validateLabelReferences(state);
    } catch (VnParseException e) {
      state.errors.add(e);
    }

    if (!state.errors.isEmpty()) {
      throw new MultipleParseErrorsException(state.errors);
    }

    // Finalize characters with expressions (replaces earlier simple character entries).
    for (var e : state.charBuilders.entrySet()) {
      state.builder.addCharacter(e.getValue().build());
    }
    return state.builder.build();
  }

  public VnScenario parse(String scriptPath) throws IOException {
    AssetCatalog cat = new AssetCatalog();
    try (InputStream in = cat.open(AssetType.SCRIPT, scriptPath)) {
      return parse(in, scriptPath, p -> cat.open(AssetType.SCRIPT, p));
    }
  }

  public VnScenario parseFromString(String script) throws IOException {
    byte[] bytes = script == null ? new byte[0] : script.getBytes(StandardCharsets.UTF_8);
    return parse(new java.io.ByteArrayInputStream(bytes), "<string>", null);
  }

  private static String buildJavaPayload(String code, ParseState state, int sourceLine, String sourceName) {
    boolean hasImports = !state.javaImports.isEmpty();
    boolean hasBinds = !state.javaBinds.isEmpty();
    boolean hasSource = sourceName != null && !sourceName.isBlank();
    if (!hasImports && !hasBinds && sourceLine == 0 && !hasSource) {
      return code; // Legacy format: raw code only (backward compatible)
    }
    StringBuilder sb = new StringBuilder();
    if (hasImports) {
      sb.append("\u00a7IMPORTS\u00a7").append(String.join("|", state.javaImports)).append("\n");
    }
    if (hasBinds) {
      sb.append("\u00a7BINDS\u00a7").append(String.join("|", state.javaBinds)).append("\n");
    }
    if (sourceLine > 0) {
      sb.append("\u00a7LINE\u00a7").append(sourceLine).append("\n");
    }
    sb.append("\u00a7SCENARIO\u00a7").append(state.scenarioId).append("\n");
    if (hasSource) {
      sb.append("\u00a7SOURCE\u00a7").append(sourceName).append("\n");
    }
    sb.append("\u00a7CODE\u00a7").append(code);
    return sb.toString();
  }

  private void processMenu(List<BufferedLine> buffer, IncludeResolver resolver, ParseState state, Deque<String> includeStack) throws IOException {
    String menuEndLabel = "_menu_end_" + (++state.syntheticLabelCounter);
    StringBuilder syntheticScript = new StringBuilder();

    // Pass 1: Extract choices and generate Choice tags
    List<String> choiceLabels = new ArrayList<>();
    for (BufferedLine bl : buffer) {
      String trimmed = bl.rawLine.trim();
      if (trimmed.startsWith(">")) {
        ParsedChoice pc = parseChoiceSegment(trimmed.substring(1).trim(), bl.sourceName, bl.lineNumber, bl.rawLine);
        if (pc != null) {
          String choiceLabel = pc.target != null ? pc.target : ("_menu_choice_" + (++state.syntheticLabelCounter));
          choiceLabels.add(choiceLabel);

          syntheticScript.append("> ").append(pc.text).append(" -> ").append(choiceLabel);
          if (pc.condition != null && !pc.condition.isBlank()) {
            syntheticScript.append(" [if ").append(pc.condition).append("]");
          }
          syntheticScript.append("\n");
        }
      }
    }

    // Pass 2: Emit the blocks
    int choiceIndex = -1;
    for (BufferedLine bl : buffer) {
      String trimmed = bl.rawLine.trim();
      if (trimmed.startsWith(">")) {
        ParsedChoice pc = parseChoiceSegment(trimmed.substring(1).trim(), bl.sourceName, bl.lineNumber, bl.rawLine);
        if (pc != null) {
          if (choiceIndex >= 0) {
            syntheticScript.append("[jump ").append(menuEndLabel).append("]\n");
          }
          choiceIndex++;
          syntheticScript.append("[label ").append(choiceLabels.get(choiceIndex)).append("]\n");
        }
      } else {
        syntheticScript.append(bl.rawLine).append("\n");
      }
    }
    if (choiceIndex >= 0) {
      syntheticScript.append("[jump ").append(menuEndLabel).append("]\n");
    }
    syntheticScript.append("[label ").append(menuEndLabel).append("]\n");

    InputStream in = new java.io.ByteArrayInputStream(syntheticScript.toString().getBytes(StandardCharsets.UTF_8));
    parseInto(in, "<menu>", resolver, state, includeStack);
  }

  private void parseInto(InputStream input,
                         String sourceName,
                         IncludeResolver resolver,
                         ParseState state,
                         Deque<String> includeStack) throws IOException {
    String sourceText = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    BufferedReader reader = new BufferedReader(new StringReader(
        LayeredCharacterResolver.collapseLayerDirectiveContinuations(sourceText)));
    String line;
    int lineNumber = 0;

    while ((line = reader.readLine()) != null) {
      lineNumber++;
      state.currentSourceLine = lineNumber;
      if (state.builder != null) state.builder.sourceLine(lineNumber);
      String rawLine = line;
      String trimmed = rawLine.trim();

      // Inside java blocks, buffer ALL lines (including blank and # lines)
      if (state.insideJava) {
        if (trimmed.equals("[/java]")) {
          state.insideJava = false;
          state.contentEmitted = true;
          ensureBuilder(state);
          flushChoices(state.builder, state.pendingChoices);
          flushPendingVoice(state);
          if (state.javaBlockClassName != null) {
            // [java class X] block — emit as java_class
            state.builder.external("java_class", buildJavaPayload(
                state.javaBlockClassName + "\n" + state.javaBuffer.toString(),
                state, state.javaBlockStartLine, state.javaBlockStartSource));
          } else {
            // Regular [java] block
            state.builder.external("inline_java", buildJavaPayload(
                state.javaBuffer.toString(), state, state.javaBlockStartLine, state.javaBlockStartSource));
          }
          state.javaBuffer.setLength(0);
          state.javaBlockClassName = null;
        } else {
          state.javaBuffer.append(rawLine).append('\n');
        }
        continue;
      }

      // Inside init java blocks
      if (state.insideInitJava) {
        if (trimmed.equals("[/init]")) {
          state.insideInitJava = false;
          state.contentEmitted = true;
          ensureBuilder(state);
          flushChoices(state.builder, state.pendingChoices);
          flushPendingVoice(state);
          state.builder.external("init_java", buildJavaPayload(
              state.initJavaBuffer.toString(), state, state.initJavaBlockStartLine, state.initJavaBlockStartSource));
          state.initJavaBuffer.setLength(0);
        } else {
          state.initJavaBuffer.append(rawLine).append('\n');
        }
        continue;
      }

      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }

      try {


      Matcher defineMatcher = DEFINE_PATTERN.matcher(trimmed);
      if (defineMatcher.matches()) {
        String key = defineMatcher.group(1);
        String value = defineMatcher.group(2) != null ? defineMatcher.group(2).trim() : "";
        state.defines.put(key, stripQuotes(value));
        continue;
      }

      if (state.activeDisplayPresetId != null) {
        if (parseDisplayPresetEntryLine(state, trimmed, sourceName, lineNumber, rawLine)) {
          continue;
        }
        state.activeDisplayPresetId = null;
      }

      Matcher includeMatcher = INCLUDE_PATTERN.matcher(trimmed);
      if (includeMatcher.matches()) {
        String includePath = stripQuotes(includeMatcher.group(1).trim());
        if (includePath.isEmpty()) continue;
        if (resolver == null) {
          throw parseError(sourceName, lineNumber, "Include resolver not configured", rawLine);
        }
        IOException firstFailure = null;
        InputStream includeStream = null;
        String resolvedSource = null;
        for (String candidate : includeCandidates(sourceName, includePath)) {
          if (includeStack.contains(candidate)) {
            throw parseError(sourceName, lineNumber, "Include cycle detected for " + candidate, rawLine);
          }
          try {
            includeStream = resolver.open(candidate);
            resolvedSource = candidate;
            break;
          } catch (IOException ex) {
            if (firstFailure == null) firstFailure = ex;
          }
        }
        if (includeStream == null || resolvedSource == null) {
          if (firstFailure != null) throw firstFailure;
          throw parseError(sourceName, lineNumber, "Included script not found: " + includePath, rawLine);
        }
        try (InputStream inc = includeStream) {
          includeStack.push(resolvedSource);
          try {
            parseInto(inc, resolvedSource, resolver, state, includeStack);
          } finally {
            includeStack.pop();
          }
        }
        continue;
      }

      if (!state.defines.isEmpty()) {
        rawLine = applyDefines(rawLine, state.defines);
        trimmed = rawLine.trim();
      }

      if (trimmed.equals("[menu]")) {
        state.insideMenu = true;
        continue;
      }
      if (trimmed.equals("[/menu]")) {
        state.insideMenu = false;
        processMenu(state.menuBuffer, resolver, state, includeStack);
        state.menuBuffer.clear();
        continue;
      }
      if (state.insideMenu) {
        state.menuBuffer.add(new BufferedLine(rawLine, sourceName, lineNumber));
        continue;
      }

      // $ single-line Java shorthand
      if (trimmed.startsWith("$") && trimmed.length() > 1 && trimmed.charAt(1) == ' ') {
        String code = trimmed.substring(2);
        state.contentEmitted = true;
        ensureBuilder(state);
        flushChoices(state.builder, state.pendingChoices);
        flushPendingVoice(state);
        state.builder.external("inline_java", buildJavaPayload(code, state, lineNumber, sourceName));
        continue;
      }

      // @jimport directive
      if (trimmed.startsWith("@jimport ") || trimmed.startsWith("@jimport\t")) {
        String imp = trimmed.substring("@jimport".length()).trim();
        if (!imp.isEmpty()) {
          validateJavaImport(imp, sourceName, lineNumber, rawLine);
          state.javaImports.add(imp);
        }
        continue;
      }

      // @bind directive
      if (trimmed.startsWith("@bind ") || trimmed.startsWith("@bind\t")) {
        String bind = trimmed.substring("@bind".length()).trim();
        if (!bind.isEmpty()) {
          validateJavaBind(bind, sourceName, lineNumber, rawLine);
          state.javaBinds.add(bind);
        }
        continue;
      }

      if (trimmed.equals("[java]")) {
        state.insideJava = true;
        state.javaBuffer.setLength(0);
        state.javaBlockStartLine = lineNumber;
        state.javaBlockStartSource = sourceName;
        state.javaBlockClassName = null;
        continue;
      }
      if (trimmed.startsWith("[java class ") && trimmed.endsWith("]")) {
        state.insideJava = true;
        state.javaBuffer.setLength(0);
        state.javaBlockStartLine = lineNumber;
        state.javaBlockStartSource = sourceName;
        state.javaBlockClassName = trimmed.substring("[java class ".length(), trimmed.length() - 1).trim();
        continue;
      }
      if (trimmed.equals("[init java]")) {
        state.insideInitJava = true;
        state.initJavaBuffer.setLength(0);
        state.initJavaBlockStartLine = lineNumber;
        state.initJavaBlockStartSource = sourceName;
        continue;
      }
      if (trimmed.equals("[/java]")) {
        throw parseError(sourceName, lineNumber, "[/java] without matching [java]", rawLine);
      }
      if (trimmed.equals("[/init]")) {
        throw parseError(sourceName, lineNumber, "[/init] without matching [init java]", rawLine);
      }

      Matcher scenarioMatcher = SCENARIO_PATTERN.matcher(trimmed);
      if (scenarioMatcher.matches()) {
        if (state.scenarioDeclared) {
          throw parseError(sourceName, lineNumber, "Scenario already declared", rawLine);
        }
        if (state.contentEmitted) {
          throw parseError(sourceName, lineNumber, "@scenario must appear before script content", rawLine);
        }
        state.scenarioDeclared = true;
        state.scenarioId = scenarioMatcher.group(1).trim();
        state.builder = new VnScenarioBuilder(state.scenarioId);
        continue;
      }

      ensureBuilder(state);

      Matcher externalMatcher = EXTERNAL_PATTERN.matcher(trimmed);
      if (externalMatcher.matches()) {
        String provider = stripQuotes(externalMatcher.group(1).trim());
        if (provider.isBlank()) {
          throw parseError(sourceName, lineNumber, "@external requires a provider", rawLine);
        }
        String payload = externalMatcher.group(2) == null ? "" : stripQuotes(externalMatcher.group(2).trim());
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        flushPendingVoice(state);
        state.builder.external(provider, payload);
        continue;
      }

      Matcher varMatcher = VAR_PATTERN.matcher(trimmed);
      if (varMatcher.matches()) {
        state.contentEmitted = true;
        flushPendingVoice(state);
        String key = varMatcher.group(1).trim();
        String value = varMatcher.group(2) != null ? varMatcher.group(2) : varMatcher.group(3);
        if (value == null || value.isBlank()) {
          value = "true";
        }
        state.builder.external("var", "set " + key + " " + value.trim());
        continue;
      }

      Matcher charMatcher = CHARACTER_PATTERN.matcher(trimmed);
      if (charMatcher.matches()) {
        state.contentEmitted = true;
        String id = charMatcher.group(1);
        String name = charMatcher.group(2);
        com.jvn.core.vn.VnCharacter.Builder cb = state.charBuilders.get(id);
        if (cb == null) {
          cb = com.jvn.core.vn.VnCharacter.builder(id);
          state.charBuilders.put(id, cb);
        }
        cb.displayName(name);
        String options = charMatcher.group(3);
        if (options != null && !options.isBlank()) {
          for (String token : VnArgTokenizer.tokenize(options)) {
            KeyValueOption option = parseKeyValueOption(
                token, sourceName, lineNumber, rawLine, "@character");
            switch (option.key()) {
              case "color", "colour", "namecolor", "name_color", "who_color" ->
                  cb.nameColor(parseCharacterColor(
                      option.value(), sourceName, lineNumber, rawLine));
              case "scale" ->
                  cb.scale(parseCharacterScale(
                      option.value(), sourceName, lineNumber, rawLine));
              default -> throw parseError(
                  sourceName,
                  lineNumber,
                  "@character unknown option: " + option.key(),
                  rawLine);
            }
          }
        }
        state.builder.addCharacter(id, name);
        continue;
      }

      Matcher bgMatcher = BACKGROUND_PATTERN.matcher(trimmed);
      if (bgMatcher.matches()) {
        state.contentEmitted = true;
        String id = bgMatcher.group(1);
        String path = parseAssetDeclarationPath(bgMatcher.group(2));
        state.builder.addBackground(id, path);
        continue;
      }

      Matcher imgMatcher = CHARIMG_PATTERN.matcher(trimmed);
      if (imgMatcher.matches()) {
        state.contentEmitted = true;
        String id = imgMatcher.group(1);
        String expr = imgMatcher.group(2);
        String path = parseAssetDeclarationPath(imgMatcher.group(3));
        com.jvn.core.vn.VnCharacter.Builder cb = state.charBuilders.get(id);
        if (cb == null) {
          cb = com.jvn.core.vn.VnCharacter.builder(id);
          state.charBuilders.put(id, cb);
        }
        cb.addExpression(expr, path);
        continue;
      }

      Matcher charLayerMatcher = CHARLAYER_PATTERN.matcher(trimmed);
      if (charLayerMatcher.matches()) {
        state.contentEmitted = true;
        String id = charLayerMatcher.group(1);
        String layerId = charLayerMatcher.group(2);
        String path = parseAssetDeclarationPath(charLayerMatcher.group(3));
        if (path.isEmpty()) {
          throw parseError(sourceName, lineNumber, "@charlayer path cannot be empty", rawLine);
        }
        state.charLayers.computeIfAbsent(id, k -> new HashMap<>()).put(layerId, path);
        getOrCreateCharacterBuilder(state, id).addLayer(layerId, path);
        continue;
      }

      Matcher charGroupMatcher = CHARGROUP_PATTERN.matcher(trimmed);
      if (charGroupMatcher.matches()) {
        state.contentEmitted = true;
        String id = charGroupMatcher.group(1);
        String groupId = charGroupMatcher.group(2);
        String body = charGroupMatcher.group(3).trim();
        LayerGroupOptions options = parseLayerGroupOptions(body, sourceName, lineNumber, rawLine);
        if (options.spec().isBlank()) {
          throw parseError(sourceName, lineNumber, "@chargroup layer spec cannot be empty", rawLine);
        }
        LayerPresetSpec resolvedSpec = resolveLayerPresetSpec(
            state, id, options.spec(), sourceName, lineNumber, rawLine, "@chargroup");
        getOrCreateCharacterBuilder(state, id).addLayerGroup(
            groupId,
            options.parentId(),
            resolvedSpec.layerIds(),
            options.pivotX(),
            options.pivotY(),
            options.hasPivot());
        continue;
      }

      Matcher charPresetMatcher = CHARPRESET_PATTERN.matcher(trimmed);
      if (charPresetMatcher.matches()) {
        state.contentEmitted = true;
        String id = charPresetMatcher.group(1);
        String expr = charPresetMatcher.group(2);
        String spec = charPresetMatcher.group(3).trim();
        if (spec.isEmpty()) {
          throw parseError(sourceName, lineNumber, "@charpreset layer spec cannot be empty", rawLine);
        }
        LayerPresetSpec resolvedSpec = resolveLayerPresetSpec(
            state, id, spec, sourceName, lineNumber, rawLine, "@charpreset");
        com.jvn.core.vn.VnCharacter.Builder cb = state.charBuilders.get(id);
        if (cb == null) {
          cb = com.jvn.core.vn.VnCharacter.builder(id);
          state.charBuilders.put(id, cb);
        }
        cb.addExpression(expr, resolvedSpec.pathSpec(), resolvedSpec.layerIds());
        continue;
      }

      Matcher displayPresetMatcher = DISPLAY_PRESET_PATTERN.matcher(trimmed);
      if (displayPresetMatcher.matches()) {
        state.contentEmitted = true;
        String id = displayPresetMatcher.group(1).trim();
        if (state.displayPresets.containsKey(id)) {
          throw parseError(sourceName, lineNumber, "Duplicate @displaypreset id: " + id, rawLine);
        }
        DisplayPresetDef preset = new DisplayPresetDef(id, sourceName, lineNumber, rawLine);
        state.displayPresets.put(id, preset);
        state.activeDisplayPresetId = id;

        String inlineEntries = displayPresetMatcher.group(2);
        if (inlineEntries != null && !inlineEntries.isBlank()) {
          parseInlineDisplayPresetEntries(state, preset, inlineEntries, sourceName, lineNumber, rawLine);
          state.activeDisplayPresetId = null;
        }
        continue;
      }

      Matcher stagePresetMatcher = STAGE_PRESET_PATTERN.matcher(trimmed);
      if (stagePresetMatcher.matches()) {
        state.contentEmitted = true;
        String id = stagePresetMatcher.group(1).trim();
        String presetPath = stripQuotes(stagePresetMatcher.group(2).trim());
        if (presetPath.isEmpty()) {
          throw parseError(sourceName, lineNumber, "@stagepreset path cannot be empty", rawLine);
        }
        if (resolver == null) {
          throw parseError(sourceName, lineNumber, "@stagepreset requires a resolver", rawLine);
        }
        String resolved = normalizeSourceName(resolveIncludePath(sourceName, presetPath));
        try (InputStream presetStream = resolver.open(resolved)) {
          VnStagePreset stagePreset = VnStagePresetLoader.load(id, resolved, presetStream);
          state.builder.addStagePreset(stagePreset);
        }
        continue;
      }

      Matcher groupMatcher = GROUP_PATTERN.matcher(trimmed);
      if (groupMatcher.matches()) {
        state.contentEmitted = true;
        String id = groupMatcher.group(1).trim();
        String parentId = groupMatcher.group(2) != null ? groupMatcher.group(2).trim() : null;
        state.builder.addGroup(id, parentId);
        continue;
      }

      Matcher positionMatcher = POSITION_PATTERN.matcher(trimmed);
      if (positionMatcher.matches()) {
        String posName = positionMatcher.group(1).trim().toLowerCase();
        String coords = positionMatcher.group(2).trim();
        if (CharacterPosition.predefined(posName) != null) {
          throw parseError(sourceName, lineNumber, "@position name '" + posName + "' conflicts with a predefined position", rawLine);
        }
        String[] parts = coords.split("\\s+");
        try {
          double px = Double.parseDouble(parts[0]);
          double py = parts.length >= 2 ? Double.parseDouble(parts[1]) : -1.0;
          state.customPositions.put(posName, CharacterPosition.named(posName, px, py));
        } catch (NumberFormatException e) {
          throw parseError(sourceName, lineNumber, "@position coordinates must be numbers: " + coords, rawLine);
        }
        continue;
      }

      Matcher labelMatcher = LABEL_PATTERN.matcher(trimmed);
      if (labelMatcher.matches()) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        flushPendingVoice(state);
        String label = labelMatcher.group(1).trim();
        registerLabel(state, label, sourceName, lineNumber, rawLine, false);
        state.builder.label(label);
        continue;
      }

      Matcher legacyLabelMatcher = LABEL_LEGACY_PATTERN.matcher(trimmed);
      if (legacyLabelMatcher.matches()) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        flushPendingVoice(state);
        String label = legacyLabelMatcher.group(1).trim();
        registerLabel(state, label, sourceName, lineNumber, rawLine, false);
        state.builder.label(label);
        continue;
      }

      if (trimmed.startsWith(">")) {
        state.contentEmitted = true;
        flushPendingVoice(state);
        ParsedChoice parsedChoice = parseChoiceSegment(trimmed.substring(1).trim(), sourceName, lineNumber, rawLine);
        if (parsedChoice != null) {
          if (parsedChoice.target != null) {
            addLabelReference(state, parsedChoice.target, sourceName, lineNumber, rawLine, "choice");
          }
          state.pendingChoices.add(parsedChoice.toChoice());
        }
        continue;
      }

      // Inline timeline { ... } block. Scan braces lexically so values such as
      // text: "use {braces}" and commented-out braces do not corrupt block depth.
      String timelineHeader = timelineHeaderCode(trimmed);
      if (isTimelineHeader(timelineHeader)) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        flushPendingVoice(state);
        ensureBuilder(state);
        StringBuilder block = new StringBuilder();
        String timelineModifiers = timelineHeaderModifiers(timelineHeader);
        int braceDepth = scanTimelineBraces(timelineHeader, 0).depth();
        if (braceDepth == 0) {
          // Opening brace on next line
          String nextLine;
          boolean foundOpeningBrace = false;
          while ((nextLine = reader.readLine()) != null) {
            lineNumber++;
            String nt = timelineHeaderCode(nextLine);
            if (nt.isEmpty() || nt.startsWith("#")) continue;
            if (nt.equals("{")) {
              braceDepth = 1;
              foundOpeningBrace = true;
              break;
            }
            throw parseError(sourceName, lineNumber, "Expected '{' after timeline", nextLine);
          }
          if (!foundOpeningBrace) {
            throw parseError(sourceName, lineNumber, "Expected '{' after timeline", rawLine);
          }
        }
        while (braceDepth > 0 && (line = reader.readLine()) != null) {
          lineNumber++;
          TimelineBraceScan scan = scanTimelineBraces(line, braceDepth);
          braceDepth = scan.depth();
          int closingBrace = scan.closingBrace();
          if (braceDepth > 0) block.append(line).append('\n');
          else {
            if (closingBrace > 0) block.append(line, 0, closingBrace).append('\n');
            String trailing = closingBrace >= 0
                ? line.substring(closingBrace + 1).trim()
                : "";
            String trailingCode = timelineHeaderCode(trailing);
            if (!trailingCode.isEmpty()) {
              throw parseError(
                  sourceName,
                  lineNumber,
                  "Unexpected content after timeline block: " + trailingCode,
                  line);
            }
          }
        }
        if (braceDepth != 0) {
          throw parseError(sourceName, lineNumber, "Unterminated inline timeline block", rawLine);
        }
        String inlineCode = "timeline {\n" + block + "}";
        if (!timelineModifiers.isBlank()) {
          inlineCode += " " + timelineModifiers;
        }
        state.builder.external("jes_timeline_inline", inlineCode);
        continue;
      }

      Matcher cmdMatcher = COMMAND_PATTERN.matcher(trimmed);
      if (cmdMatcher.matches()) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        parseCommand(cmdMatcher.group(1), sourceName, lineNumber, rawLine, state);
        continue;
      }

      Matcher dialogueMatcher = DIALOGUE_PATTERN.matcher(trimmed);
      if (dialogueMatcher.matches()) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        String speakerId = dialogueMatcher.group(1).trim();
        String text = dialogueMatcher.group(2).trim();
        emitDialogue(state, speakerId, text);
        continue;
      }

      Matcher quotedDialogueMatcher = DIALOGUE_QUOTED_PATTERN.matcher(trimmed);
      if (quotedDialogueMatcher.matches()) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        String speakerId = quotedDialogueMatcher.group(1).trim();
        String text = unescapeQuoted(quotedDialogueMatcher.group(2));
        emitDialogue(state, speakerId, text);
        continue;
      }

      throw parseError(sourceName, lineNumber, "Unrecognized syntax", rawLine);
      } catch (VnParseException ex) {
        state.errors.add(ex);
      }
    }
  }

  private void parseCommand(String commandBody,
                            String sourceName,
                            int lineNumber,
                            String rawLine,
                            ParseState state) throws IOException {
    String body = commandBody == null ? "" : commandBody.trim();
    if (body.isEmpty()) {
      throw parseError(sourceName, lineNumber, "Empty command [] is not allowed", rawLine);
    }

    String[] parts = body.split("\\s+", 2);
    String cmd = parts[0].toLowerCase();
    String arg = parts.length > 1 ? parts[1].trim() : null;

    if ("voice".equals(cmd)) {
      if (state.pendingVoiceTrackId != null && !state.pendingVoiceTrackId.isBlank()) {
        flushPendingVoice(state);
      }
    } else {
      flushPendingVoice(state);
    }

    switch (cmd) {
      case "background":
      case "bg": {
        String bgId = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.background(bgId);
        return;
      }
      case "jump": {
        String label = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        addLabelReference(state, label, sourceName, lineNumber, rawLine, "jump");
        state.builder.jump(label);
        return;
      }
      case "end":
        flushPendingVoice(state);
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.end();
        return;
      case "eval": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("eval", payload);
        return;
      }
      case "plugin": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("plugin", payload);
        return;
      }
      case "bgm": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        if (toks.length == 0 || toks[0].isBlank()) {
          throw parseError(sourceName, lineNumber, "[bgm] requires a track id", rawLine);
        }

        String track = toks[0];
        boolean loop = true;
        Float volume = null;

        for (int i = 1; i < toks.length; i++) {
          String option = toks[i].trim();
          if (option.isEmpty()) continue;

          int eq = option.indexOf('=');
          if (eq > 0) {
            String key = option.substring(0, eq).trim().toLowerCase();
            String value = option.substring(eq + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
              throw parseError(sourceName, lineNumber, "[bgm] malformed option: " + option, rawLine);
            }
            switch (key) {
              case "loop" -> {
                if (!isBooleanToken(value)) {
                  throw parseError(sourceName, lineNumber, "[bgm] loop must be true/false/on/off/1/0", rawLine);
                }
                loop = parseBooleanToken(value);
              }
              case "vol", "volume" -> {
                volume = parseVolumeToken(value, sourceName, lineNumber, rawLine, "[bgm]");
              }
              default -> throw parseError(sourceName, lineNumber, "[bgm] unknown option: " + key, rawLine);
            }
          } else if (i == 1 && isBooleanToken(option)) {
            // Backward-friendly shorthand: [bgm track false]
            loop = parseBooleanToken(option);
          } else {
            throw parseError(sourceName, lineNumber, "[bgm] unrecognized token: " + option, rawLine);
          }
        }

        state.builder.playBgm(track, loop);
        if (volume != null) {
          state.builder.external("settings", "volume bgm " + formatNumber(volume));
        }
        return;
      }
      case "bgm_stop":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.stopBgm();
        return;
      case "bgm_fadeout":
        if (arg != null && !arg.isBlank()) {
          try {
            state.builder.fadeOutBgm(Long.parseLong(arg.trim()));
          } catch (NumberFormatException ex) {
            throw parseError(sourceName, lineNumber, "[bgm_fadeout] expects an integer duration in ms", rawLine);
          }
        } else {
          state.builder.fadeOutBgm();
        }
        return;
      case "bgm_pause":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", "pause");
        return;
      case "bgm_resume":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", "resume");
        return;
      case "bgm_seek": {
        String secondsArg = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        double seconds;
        try {
          seconds = Double.parseDouble(secondsArg);
        } catch (NumberFormatException ex) {
          throw parseError(sourceName, lineNumber, "[bgm_seek] expects a numeric seconds value", rawLine);
        }
        if (seconds < 0) {
          throw parseError(sourceName, lineNumber, "[bgm_seek] seconds must be >= 0", rawLine);
        }
        state.builder.external("audio", "seek " + formatNumber(seconds));
        return;
      }
      case "bgm_crossfade": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        String track = null;
        Long durationMs = null;
        Boolean loop = null;
        int positionalCount = 0;

        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;

          if (isNamedOptionToken(token, "bgm_crossfade")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[bgm_crossfade]");
            switch (option.key()) {
              case "track", "id", "file", "path" -> track = option.value();
              case "dur", "duration", "ms" -> {
                durationMs = parseLongValue(option.value(), "[bgm_crossfade]", "duration", sourceName, lineNumber, rawLine);
                if (durationMs < 0) {
                  throw parseError(sourceName, lineNumber, "[bgm_crossfade] duration must be >= 0", rawLine);
                }
              }
              case "loop" -> {
                if (!isBooleanToken(option.value())) {
                  throw parseError(sourceName, lineNumber, "[bgm_crossfade] loop must be true/false/on/off/1/0", rawLine);
                }
                loop = parseBooleanToken(option.value());
              }
              default -> throw parseError(sourceName, lineNumber, "[bgm_crossfade] unknown option: " + option.key(), rawLine);
            }
            continue;
          }

          positionalCount++;
          if (track == null) {
            track = token;
            continue;
          }
          if (durationMs == null) {
            durationMs = parseLongValue(token, "[bgm_crossfade]", "duration", sourceName, lineNumber, rawLine);
            if (durationMs < 0) {
              throw parseError(sourceName, lineNumber, "[bgm_crossfade] duration must be >= 0", rawLine);
            }
            continue;
          }
          if (loop == null && isBooleanToken(token)) {
            loop = parseBooleanToken(token);
            continue;
          }
          throw parseError(sourceName, lineNumber, "[bgm_crossfade] too many arguments", rawLine);
        }

        if (track == null || track.isBlank() || durationMs == null) {
          throw parseError(sourceName, lineNumber, "[bgm_crossfade] expects: [bgm_crossfade <trackId> <ms> [loop]] or named options", rawLine);
        }

        String normalized = "crossfade " + quoteTokenIfNeeded(track) + " " + durationMs;
        if (loop != null) normalized += " " + loop;
        state.builder.external("audio", normalized);
        return;
      }
      case "sfx": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        if (toks.length == 0) {
          throw parseError(sourceName, lineNumber, "[sfx] requires a track id", rawLine);
        }
        String track = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "sfx")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[sfx]");
            switch (option.key()) {
              case "track", "id", "file", "path" -> track = option.value();
              default -> throw parseError(sourceName, lineNumber, "[sfx] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (track == null) {
            track = token;
            continue;
          }
          throw parseError(sourceName, lineNumber, "[sfx] unexpected token: " + token, rawLine);
        }
        if (track == null || track.isBlank()) {
          throw parseError(sourceName, lineNumber, "[sfx] requires a track id", rawLine);
        }
        state.builder.playSfx(track);
        return;
      }
      case "sfx_stop":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", "sfx_stop");
        return;
      case "voice": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        if (toks.length == 0) {
          throw parseError(sourceName, lineNumber, "[voice] requires a track id", rawLine);
        }
        String track = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "voice")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[voice]");
            switch (option.key()) {
              case "track", "id", "file", "path" -> track = option.value();
              default -> throw parseError(sourceName, lineNumber, "[voice] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (track == null) {
            track = token;
            continue;
          }
          throw parseError(sourceName, lineNumber, "[voice] unexpected token: " + token, rawLine);
        }
        if (track == null || track.isBlank()) {
          throw parseError(sourceName, lineNumber, "[voice] requires a track id", rawLine);
        }
        state.pendingVoiceTrackId = track;
        return;
      }
      case "group": {
        String payload = arg == null ? "" : arg.trim();
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        if (toks.length == 0 || toks[0].isBlank()) {
          throw parseError(sourceName, lineNumber, "[group] requires a target id", rawLine);
        }
        String targetId = toks[0];
        String parentId = toks.length > 1 ? toks[1] : null;
        state.builder.group(targetId, parentId);
        return;
      }
      case "voice_stop":
        flushPendingVoice(state);
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", "voice_stop");
        return;
      case "audio_stop_all":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", "stop_all");
        return;
      case "audio_pause_all":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", "pause_all");
        return;
      case "audio_resume_all":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", "resume_all");
        return;
      case "audio": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", payload);
        return;
      }
      case "volume": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        String channel = null;
        Float level = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "volume")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[volume]");
            switch (option.key()) {
              case "channel", "target", "type" -> channel = option.value();
              case "level", "value", "vol", "volume" ->
                  level = parseUnitRangeToken(option.value(), sourceName, lineNumber, rawLine, "[volume]", "level");
              default -> throw parseError(sourceName, lineNumber, "[volume] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (channel == null) {
            channel = token;
            continue;
          }
          if (level == null) {
            level = parseUnitRangeToken(token, sourceName, lineNumber, rawLine, "[volume]", "level");
            continue;
          }
          throw parseError(sourceName, lineNumber, "[volume] unexpected token: " + token, rawLine);
        }
        if (channel == null || channel.isBlank() || level == null) {
          throw parseError(sourceName, lineNumber, "[volume] expects: [volume <channel> <level>] or named options", rawLine);
        }
        state.builder.external("settings", "volume " + channel + " " + formatNumber(level));
        return;
      }
      case "textspeed": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        Integer value = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "textspeed")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[textspeed]");
            switch (option.key()) {
              case "value", "speed", "chars" -> value = parseIntegerValue(option.value(), "[textspeed]", "value", sourceName, lineNumber, rawLine);
              default -> throw parseError(sourceName, lineNumber, "[textspeed] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (value == null) {
            value = parseIntegerValue(token, "[textspeed]", "value", sourceName, lineNumber, rawLine);
            continue;
          }
          throw parseError(sourceName, lineNumber, "[textspeed] unexpected token: " + token, rawLine);
        }
        if (value == null) {
          throw parseError(sourceName, lineNumber, "[textspeed] requires a value", rawLine);
        }
        state.builder.external("settings", "textspeed " + value);
        return;
      }
      case "autodelay": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        Long value = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "autodelay")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[autodelay]");
            switch (option.key()) {
              case "value", "delay", "ms", "duration" -> value = parseLongValue(option.value(), "[autodelay]", "value", sourceName, lineNumber, rawLine);
              default -> throw parseError(sourceName, lineNumber, "[autodelay] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (value == null) {
            value = parseLongValue(token, "[autodelay]", "value", sourceName, lineNumber, rawLine);
            continue;
          }
          throw parseError(sourceName, lineNumber, "[autodelay] unexpected token: " + token, rawLine);
        }
        if (value == null) {
          throw parseError(sourceName, lineNumber, "[autodelay] requires a value", rawLine);
        }
        state.builder.external("settings", "autodelay " + value);
        return;
      }
      case "hud": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("hud", payload);
        return;
      }
      case "save":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("save", "");
        return;
      case "quickload":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("save", "quickload");
        return;
      case "skip":
        state.builder.external("mode", "skip " + (arg == null ? "" : arg));
        return;
      case "auto":
        state.builder.external("mode", "auto " + (arg == null ? "" : arg));
        return;
      case "mode":
        state.builder.external("mode", arg == null ? "" : arg);
        return;
      case "ui":
        state.builder.external("ui", arg == null ? "" : arg);
        return;
      case "visualizer":
      case "viz": {
        String payload = (arg == null || arg.isBlank()) ? "toggle" : arg.trim();
        state.builder.external("ui", "visualizer " + payload);
        return;
      }
      case "history":
        state.builder.external("history", arg == null ? "" : arg);
        return;
      case "screen":
        state.builder.external("screen", arg == null ? "" : arg);
        return;
      case "persistent": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("persistent", payload);
        return;
      }
      case "jes_push": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("jes", "push " + payload);
        return;
      }
      case "jes_replace": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("jes", "replace " + payload);
        return;
      }
      case "jes_pop":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("jes", "pop");
        return;
      case "jes_call": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("jes", "call " + payload);
        return;
      }
      case "wait": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        Long waitMs = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "wait")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[wait]");
            switch (option.key()) {
              case "ms", "dur", "duration", "time", "value" -> waitMs = parseLongValue(option.value(), "[wait]", "duration", sourceName, lineNumber, rawLine);
              default -> throw parseError(sourceName, lineNumber, "[wait] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (waitMs == null) {
            waitMs = parseLongValue(token, "[wait]", "duration", sourceName, lineNumber, rawLine);
            continue;
          }
          throw parseError(sourceName, lineNumber, "[wait] unexpected token: " + token, rawLine);
        }
        if (waitMs == null) {
          throw parseError(sourceName, lineNumber, "[wait] expects an integer duration in ms", rawLine);
        }
        state.builder.waitMs(waitMs);
        return;
      }
      case "showpreset": {
        parseShowDisplayPresetCommand(arg, sourceName, lineNumber, rawLine, state);
        return;
      }
      case "movepreset": {
        parseMoveDisplayPresetCommand(arg, sourceName, lineNumber, rawLine, state);
        return;
      }
      case "hidepreset": {
        parseHideDisplayPresetCommand(arg, sourceName, lineNumber, rawLine, state);
        return;
      }
      case "show": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        if (toks.length < 2) {
          throw parseError(sourceName, lineNumber, "[show] expects: [show <charId> <pos|at x,y[,z]> [expression] [layer]]", rawLine);
        }
        String charId = toks[0];
        CharacterPosition pos = null;
        Integer layerOrder = null;
        String expr = "neutral";
        String displaySlot = null;
        boolean exprSet = false;
        for (int i = 1; i < toks.length; i++) {
          String token = toks[i].trim();
          if (token.isEmpty()) continue;
          if ("at".equalsIgnoreCase(token) && i + 1 < toks.length && !isNamedOptionToken(toks[i + 1], "show")) {
            InlinePosition ip = parseAtPosition(toks[++i], sourceName, lineNumber, rawLine);
            pos = ip.position();
            if (layerOrder == null) layerOrder = ip.layerOrder();
            continue;
          }
          if (isNamedOptionToken(token, "show")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[show]");
            switch (option.key()) {
              case "pos", "position" -> pos = parsePosition(option.value(), sourceName, lineNumber, rawLine, state);
              case "at", "coord", "coords", "xy" -> {
                InlinePosition ip = parseAtPosition(option.value(), sourceName, lineNumber, rawLine);
                pos = ip.position();
                if (layerOrder == null) layerOrder = ip.layerOrder();
              }
              case "expr", "expression", "preset" -> {
                expr = resolveInlineExpressionToken(state, charId, option.value(), sourceName, lineNumber, rawLine);
                exprSet = true;
              }
              case "layer", "z", "zorder" -> layerOrder = parseIntegerValue(option.value(), "[show]", "layer", sourceName, lineNumber, rawLine);
              case "slot", "as", "instance", "display", "display_slot", "display-slot" -> displaySlot = option.value();
              default -> throw parseError(sourceName, lineNumber, "[show] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (pos == null) {
            pos = parsePosition(token, sourceName, lineNumber, rawLine, state);
            continue;
          }
          if (isIntegerToken(token)) {
            layerOrder = Integer.parseInt(token);
            continue;
          }
          if (!exprSet) {
            expr = resolveInlineExpressionToken(state, charId, token, sourceName, lineNumber, rawLine);
            exprSet = true;
            continue;
          }
          throw parseError(sourceName, lineNumber, "[show] unexpected token: " + token, rawLine);
        }
        if (pos == null) {
          throw parseError(sourceName, lineNumber, "[show] requires a position via positional arg, pos=..., or at=...", rawLine);
        }
        state.builder.show(charId, expr, pos, layerOrder, displaySlot);
        return;
      }
      case "hide": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        String charId = null;
        String displaySlot = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isDisplaySlotShorthand(token)) {
            displaySlot = token.substring(1).trim();
            continue;
          }
          if (isNamedOptionToken(token, "hide")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[hide]");
            switch (option.key()) {
              case "slot", "as", "instance", "display", "display_slot", "display-slot" -> displaySlot = option.value();
              default -> throw parseError(sourceName, lineNumber, "[hide] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (charId == null) {
            charId = token;
            continue;
          }
          throw parseError(sourceName, lineNumber, "[hide] unexpected token: " + token, rawLine);
        }
        if ((charId == null || charId.isBlank()) && (displaySlot == null || displaySlot.isBlank())) {
          throw parseError(sourceName, lineNumber, "[hide] expects a character id or slot=...", rawLine);
        }
        state.builder.hide(charId, displaySlot);
        return;
      }
      case "lookat": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("eye_focus", payload);
        return;
      }
      case "move": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        if (toks.length < 2) {
          throw parseError(sourceName, lineNumber, "[move] expects: [move <charId> <pos|at x,y> [expression] [easing] [durationMs]]", rawLine);
        }

        String moveCharId = null;
        String moveDisplaySlot = null;
        CharacterPosition movePos = null;
        String moveExpr = null;
        Easing.Type moveEasing = null;
        long moveDur = 0;
        long moveExprDur = -1L;

        // Parse all tokens
        int i = 0;
        if (isDisplaySlotShorthand(toks[0])) {
          moveDisplaySlot = toks[0].substring(1).trim();
          i = 1;
        } else if (isNamedOptionToken(toks[0], "move")) {
          KeyValueOption option = parseKeyValueOption(toks[0], sourceName, lineNumber, rawLine, "[move]");
          switch (option.key()) {
            case "slot", "as", "instance", "display", "display_slot", "display-slot" -> moveDisplaySlot = option.value();
            default -> throw parseError(sourceName, lineNumber, "[move] requires a character id before option: " + option.key(), rawLine);
          }
          i = 1;
        } else {
          moveCharId = toks[0];
          i = 1;
        }

        // First, handle positional position (second token)
        if (i < toks.length && !isNamedOptionToken(toks[i], "move")) {
          String posToken = toks[i];
          if ("at".equalsIgnoreCase(posToken)) {
            i++;
            if (i < toks.length) {
              InlinePosition inline = parseAtPosition(toks[i], sourceName, lineNumber, rawLine);
              movePos = inline.position();
              i++;
            }
          } else {
            // Try built-in positions
            String upper = posToken.trim().toUpperCase(java.util.Locale.ENGLISH);
            if ("FAR_LEFT".equals(upper) || "FL".equals(upper) || "FARLEFT".equals(upper)) {
              movePos = CharacterPosition.FAR_LEFT;
              i++;
            } else if ("LEFT".equals(upper) || "L".equals(upper)) {
              movePos = CharacterPosition.LEFT;
              i++;
            } else if ("CENTER".equals(upper) || "C".equals(upper) || "CENTRE".equals(upper)) {
              movePos = CharacterPosition.CENTER;
              i++;
            } else if ("RIGHT".equals(upper) || "R".equals(upper)) {
              movePos = CharacterPosition.RIGHT;
              i++;
            } else if ("FAR_RIGHT".equals(upper) || "FR".equals(upper) || "FARRIGHT".equals(upper)) {
              movePos = CharacterPosition.FAR_RIGHT;
              i++;
            } else {
              // Try custom positions
              movePos = state.getCustomPosition(posToken);
              if (movePos != null) {
                i++;
              }
            }
          }
        }

        // Parse remaining tokens in order: expression, easing, duration, or named options
        while (i < toks.length) {
          String tok = toks[i].trim();
          if (tok.isEmpty()) {
            i++;
            continue;
          }

          // Check for named options
          if (isNamedOptionToken(tok, "move")) {
            KeyValueOption option = parseKeyValueOption(tok, sourceName, lineNumber, rawLine, "[move]");
            switch (option.key()) {
              case "pos", "position" -> movePos = parsePosition(option.value(), sourceName, lineNumber, rawLine, state);
              case "at", "coord", "coords", "xy" -> movePos = parseAtPosition(option.value(), sourceName, lineNumber, rawLine).position();
              case "expr", "expression", "preset" ->
                  moveExpr = moveCharId == null || moveCharId.isBlank()
                      ? option.value()
                      : resolveInlineExpressionToken(state, moveCharId, option.value(), sourceName, lineNumber, rawLine);
              case "slot", "as", "instance", "display", "display_slot", "display-slot" -> moveDisplaySlot = option.value();
              case "ease", "easing" -> {
                moveEasing = parseEasingToken(option.value());
                if (moveEasing == null) {
                  throw parseError(sourceName, lineNumber, "[move] unknown easing: " + option.value(), rawLine);
                }
              }
              case "dur", "duration", "ms" -> {
                moveDur = parseLongValue(option.value(), "[move]", "duration", sourceName, lineNumber, rawLine);
                if (moveDur < 0) throw parseError(sourceName, lineNumber, "[move] duration must be >= 0", rawLine);
              }
              case "exprdur", "exprduration", "expr_duration", "expr-duration",
                   "expressiondur", "expressionduration", "expression_duration", "expression-duration" -> {
                moveExprDur = parseLongValue(
                    option.value(),
                    "[move]",
                    "expression duration",
                    sourceName,
                    lineNumber,
                    rawLine);
                if (moveExprDur < 0) {
                  throw parseError(sourceName, lineNumber, "[move] expression duration must be >= 0", rawLine);
                }
              }
              default -> throw parseError(sourceName, lineNumber, "[move] unknown option: " + option.key(), rawLine);
            }
            i++;
            continue;
          }

          // Check for integer duration
          if (isIntegerToken(tok)) {
            moveDur = Long.parseLong(tok);
            if (moveDur < 0) throw parseError(sourceName, lineNumber, "[move] duration must be >= 0", rawLine);
            i++;
            continue;
          }

          // Check for easing
          Easing.Type easing = parseEasingToken(tok);
          if (easing != null) {
            moveEasing = easing;
            i++;
            continue;
          }

          // Otherwise treat as expression
          if (moveExpr == null) {
            moveExpr = moveCharId == null || moveCharId.isBlank()
                ? tok
                : resolveInlineExpressionToken(state, moveCharId, tok, sourceName, lineNumber, rawLine);
            i++;
            continue;
          }

          throw parseError(sourceName, lineNumber, "[move] unexpected token: " + tok, rawLine);
        }

        if (movePos == null) {
          throw parseError(sourceName, lineNumber, "[move] requires a position via positional arg, pos=..., or at=...", rawLine);
        }
        if ((moveCharId == null || moveCharId.isBlank()) && (moveDisplaySlot == null || moveDisplaySlot.isBlank())) {
          throw parseError(sourceName, lineNumber, "[move] expects a character id or slot=...", rawLine);
        }

        state.builder.move(
            moveCharId,
            movePos,
            moveExpr,
            moveEasing,
            moveDur,
            moveDisplaySlot,
            moveExprDur);
        return;
      }
      case "stage": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        String presetId = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "stage")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[stage]");
            switch (option.key()) {
              case "preset", "id", "name", "mode" -> presetId = option.value();
              default -> throw parseError(sourceName, lineNumber, "[stage] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (presetId == null) {
            presetId = token;
            continue;
          }
          throw parseError(sourceName, lineNumber, "[stage] unexpected token: " + token, rawLine);
        }
        if (presetId == null || presetId.isBlank()) {
          throw parseError(sourceName, lineNumber, "[stage] requires a preset id", rawLine);
        }
        state.builder.external("stage", presetId);
        return;
      }
      case "transition": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        VnTransition.TransitionType type = null;
        long dur = 500;
        String bg = null;
        String maskAssetPath = null;
        for (String tok : toks) {
          String token = tok.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "transition")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[transition]");
            switch (option.key()) {
              case "type", "kind", "style" -> {
                type = parseTransitionType(option.value());
                if (type == null) throw parseError(sourceName, lineNumber, "Unknown transition type: " + option.value(), rawLine);
              }
              case "dur", "duration", "ms" -> {
                dur = parseLongValue(option.value(), "[transition]", "duration", sourceName, lineNumber, rawLine);
                if (dur < 0) throw parseError(sourceName, lineNumber, "[transition] duration must be >= 0", rawLine);
              }
              case "bg", "background" -> bg = option.value();
              case "mask", "maskPath", "maskAsset" -> maskAssetPath = option.value();
              default -> throw parseError(sourceName, lineNumber, "[transition] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (type == null) {
            type = parseTransitionType(token);
            if (type == null) throw parseError(sourceName, lineNumber, "Unknown transition type: " + token, rawLine);
            continue;
          }
          if (isIntegerToken(token)) {
            dur = Long.parseLong(token);
            if (dur < 0) throw parseError(sourceName, lineNumber, "[transition] duration must be >= 0", rawLine);
            continue;
          }
          if (bg == null) {
            bg = token;
            continue;
          }
          throw parseError(sourceName, lineNumber, "[transition] unexpected token: " + token, rawLine);
        }
        if (type == null) {
          throw parseError(sourceName, lineNumber, "[transition] requires a type via positional arg or type=...", rawLine);
        }
        if (type == VnTransition.TransitionType.MASK && maskAssetPath == null) {
          throw parseError(sourceName, lineNumber, "[transition] MASK transition requires mask=... parameter", rawLine);
        }
        state.builder.transition(type, dur, bg, maskAssetPath);
        return;
      }
      case "particles":
      case "particle":
      case "weather":
      case "pfx":
      case "fx": {
        String tag = "[" + cmd + "]";
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        if (toks.length == 0) {
          throw parseError(sourceName, lineNumber, tag + " requires a preset or preset=...", rawLine);
        }
        VnParticleCommand.Preset preset = null;
        float intensity = 0.5f;
        int layer = 100;
        Double opacity = null;
        Double speed = null;
        Double wind = null;
        Double size = null;
        Long durationMs = null;
        Long prewarmMs = null;
        String texturePath = null;
        Integer tintArgb = null;
        boolean intensitySet = false;
        boolean layerSet = false;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, cmd)) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, tag);
            switch (option.key()) {
              case "preset", "type", "kind", "mode", "weather" -> preset = VnParticleCommand.Preset.parse(option.value());
              case "intensity", "amount", "strength", "level" -> {
                intensity = parseUnitRangeToken(option.value(), sourceName, lineNumber, rawLine, tag, "intensity");
                intensitySet = true;
              }
              case "layer", "z", "zorder" -> {
                layer = parseIntegerValue(option.value(), tag, "layer", sourceName, lineNumber, rawLine);
                layerSet = true;
              }
              case "opacity", "alpha" ->
                  opacity = (double) parseUnitRangeToken(option.value(), sourceName, lineNumber, rawLine, tag, "opacity");
              case "speed", "speedscale", "velocity" ->
                  speed = parseNonNegativeDouble(option.value(), tag, "speed", sourceName, lineNumber, rawLine);
              case "wind", "drift", "windx" ->
                  wind = parseDoubleValue(option.value(), tag, "wind", sourceName, lineNumber, rawLine);
              case "size", "sizescale", "scale" ->
                  size = parseNonNegativeDouble(option.value(), tag, "size", sourceName, lineNumber, rawLine);
              case "duration", "dur", "ms", "time" -> {
                durationMs = parseLongValue(option.value(), tag, "duration", sourceName, lineNumber, rawLine);
                if (durationMs < 0) {
                  throw parseError(sourceName, lineNumber, tag + " duration must be >= 0", rawLine);
                }
              }
              case "prewarm", "warmup", "warmupms" -> {
                prewarmMs = parseLongValue(option.value(), tag, "prewarm", sourceName, lineNumber, rawLine);
                if (prewarmMs < 0) {
                  throw parseError(sourceName, lineNumber, tag + " prewarm must be >= 0", rawLine);
                }
              }
              case "texture", "asset", "sprite", "image" -> texturePath = option.value();
              case "tint", "color", "colour" ->
                  tintArgb = parseHexColor(option.value(), tag, sourceName, lineNumber, rawLine);
              default -> throw parseError(sourceName, lineNumber, tag + " unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (preset == null) {
            preset = VnParticleCommand.Preset.parse(token);
            continue;
          }
          if (!intensitySet) {
            intensity = parseUnitRangeToken(token, sourceName, lineNumber, rawLine, tag, "intensity");
            intensitySet = true;
            continue;
          }
          if (!layerSet && isIntegerToken(token)) {
            layer = Integer.parseInt(token);
            layerSet = true;
            continue;
          }
          throw parseError(sourceName, lineNumber, tag + " unexpected token: " + token, rawLine);
        }
        if (preset == null) {
          throw parseError(sourceName, lineNumber, tag + " requires a preset via positional arg or preset=...", rawLine);
        }
        if (preset == VnParticleCommand.Preset.NONE) {
          state.builder.particles(VnParticleCommand.stop());
        } else {
          VnParticleCommand.Builder pb = VnParticleCommand.builder(preset)
              .intensity(intensity)
              .layer(layer);
          if (opacity != null)    pb.opacity(opacity);
          if (speed != null)      pb.speed(speed);
          if (wind != null)       pb.wind(wind);
          if (size != null)       pb.size(size);
          if (durationMs != null) pb.duration(durationMs);
          if (prewarmMs != null)  pb.prewarm(prewarmMs);
          if (texturePath != null) pb.texture(texturePath);
          if (tintArgb != null)   pb.tint(tintArgb);
          state.builder.particles(pb.build());
        }
        return;
      }
      case "menu":
        state.builder.external("menu", arg == null ? "" : arg);
        return;
      case "settings":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("menu", "settings");
        return;
      case "mainmenu":
        state.builder.external("menu", "main" + (arg == null || arg.isBlank() ? "" : (" " + arg)));
        return;
      case "load": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("vns", "replace " + payload);
        return;
      }
      case "goto": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("vns", "goto " + payload);
        return;
      }
      case "set": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "set " + payload);
        return;
      }
      case "inc": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "inc " + payload);
        return;
      }
      case "dec": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "dec " + payload);
        return;
      }
      case "flag": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "flag " + payload);
        return;
      }
      case "unflag": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "unflag " + payload);
        return;
      }
      case "clear": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "clear " + payload);
        return;
      }
      case "mul": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "mul " + payload);
        return;
      }
      case "div": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "div " + payload);
        return;
      }
      case "toggle": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "toggle " + payload);
        return;
      }
      case "if": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        IfGoto ifGoto = parseIfGoto(payload);
        if (ifGoto != null) {
          validateConditionExpression(ifGoto.expression, sourceName, lineNumber, rawLine);
          addLabelReference(state, ifGoto.label, sourceName, lineNumber, rawLine, "if-goto");
          state.builder.external("cond", "if " + ifGoto.expression + " goto " + ifGoto.label);
        } else {
          startConditionalBlock(state, payload, sourceName, lineNumber, rawLine);
        }
        return;
      }
      case "elif": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        continueConditionalBlock(state, payload, sourceName, lineNumber, rawLine);
        return;
      }
      case "else":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        enterElseBlock(state, sourceName, lineNumber, rawLine);
        return;
      case "endif":
      case "/if":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        closeConditionalBlock(state, sourceName, lineNumber, rawLine);
        return;
      case "call": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = payload.split("\\s+", 2);
        if (toks.length == 1) {
          String label = toks[0].trim();
          addLabelReference(state, label, sourceName, lineNumber, rawLine, "call");
          state.builder.call(label);
          return;
        }
        String provider = toks[0].trim();
        if (provider.isBlank()) {
          throw parseError(sourceName, lineNumber, "[call] requires a label or provider", rawLine);
        }
        String providerPayload = toks.length > 1 ? toks[1] : "";
        state.builder.external(provider, providerPayload);
        return;
      }
      case "jes_timeline": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("jes_timeline", payload);
        return;
      }
      case "gosub": {
        // Subroutine call - pushes return address and jumps to label
        String label = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        addLabelReference(state, label, sourceName, lineNumber, rawLine, "gosub");
        state.builder.call(label);
        return;
      }
      case "return":
        // Return from subroutine - pops return address from call stack
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.returnFromCall();
        return;
      case "char":
      case "character": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external(
            "char",
            normalizeCharacterInteropPayload(state, payload, sourceName, lineNumber, rawLine));
        return;
      }
      case "choice": {
        flushPendingVoice(state);
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        List<Choice> inlineChoices = parseInlineChoices(payload, sourceName, lineNumber, rawLine, state);
        if (inlineChoices.isEmpty()) {
          throw parseError(sourceName, lineNumber, "[choice] must contain at least one option", rawLine);
        }
        state.builder.choiceNodes(inlineChoices);
        return;
      }
      case "phone":
        state.builder.external("phone", arg == null ? "" : arg);
        return;
      case "jes":
        state.builder.external("jes", arg == null ? "" : arg);
        return;
      case "java":
        state.builder.external("java", arg == null ? "" : arg);
        return;
      default:
        throw parseError(sourceName, lineNumber, "Unknown command [" + cmd + "]", rawLine);
    }
  }

  private boolean parseDisplayPresetEntryLine(ParseState state,
                                              String trimmed,
                                              String sourceName,
                                              int lineNumber,
                                              String rawLine) throws IOException {
    Matcher matcher = DISPLAY_PRESET_ENTRY_PATTERN.matcher(trimmed);
    if (!matcher.matches()) return false;
    DisplayPresetDef preset = state.displayPresets.get(state.activeDisplayPresetId);
    if (preset == null) return false;
    String slotId = matcher.group(1).trim();
    String body = matcher.group(2).trim();
    preset.entries.add(parseDisplayPresetEntry(state, preset.id, slotId, body, sourceName, lineNumber, rawLine));
    return true;
  }

  private void parseInlineDisplayPresetEntries(ParseState state,
                                               DisplayPresetDef preset,
                                               String inlineEntries,
                                               String sourceName,
                                               int lineNumber,
                                               String rawLine) throws IOException {
    for (String rawEntry : inlineEntries.split("\\|")) {
      String entry = rawEntry == null ? "" : rawEntry.trim();
      if (entry.isEmpty()) continue;
      Matcher matcher = DISPLAY_PRESET_ENTRY_PATTERN.matcher(entry);
      if (!matcher.matches()) {
        throw parseError(sourceName, lineNumber,
            "@displaypreset inline entries must use slot = <charId> <position> [expression] [z=...]", rawLine);
      }
      preset.entries.add(parseDisplayPresetEntry(
          state,
          preset.id,
          matcher.group(1).trim(),
          matcher.group(2).trim(),
          sourceName,
          lineNumber,
          rawLine));
    }
  }

  private DisplayPresetEntry parseDisplayPresetEntry(ParseState state,
                                                     String presetId,
                                                     String slotId,
                                                     String body,
                                                     String sourceName,
                                                     int lineNumber,
                                                     String rawLine) throws IOException {
    if (slotId == null || slotId.isBlank()) {
      throw parseError(sourceName, lineNumber, "@displaypreset slot id cannot be empty", rawLine);
    }
    String[] toks = VnArgTokenizer.tokenizeToArray(body);
    if (toks.length == 0) {
      throw parseError(sourceName, lineNumber, "@displaypreset entry '" + slotId + "' cannot be empty", rawLine);
    }

    String charId = null;
    CharacterPosition position = null;
    Integer layerOrder = null;
    String expressionToken = null;
    boolean expressionSet = false;

    for (int i = 0; i < toks.length; i++) {
      String token = toks[i] == null ? "" : toks[i].trim();
      if (token.isEmpty()) continue;

      if ("at".equalsIgnoreCase(token) && i + 1 < toks.length && !isDisplayPresetEntryOption(toks[i + 1])) {
        InlinePosition inline = parseAtPosition(toks[++i], sourceName, lineNumber, rawLine);
        position = inline.position();
        if (layerOrder == null) layerOrder = inline.layerOrder();
        continue;
      }

      if (isDisplayPresetEntryOption(token)) {
        KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "@displaypreset");
        switch (option.key()) {
          case "char", "character", "id", "sprite" -> charId = option.value();
          case "pos", "position" -> position = parsePosition(option.value(), sourceName, lineNumber, rawLine, state);
          case "at", "coord", "coords", "xy" -> {
            InlinePosition inline = parseAtPosition(option.value(), sourceName, lineNumber, rawLine);
            position = inline.position();
            if (layerOrder == null) layerOrder = inline.layerOrder();
          }
          case "expr", "expression", "preset" -> {
            expressionToken = option.value();
            expressionSet = true;
          }
          case "layer", "z", "zorder" -> layerOrder = parseIntegerValue(option.value(), "@displaypreset", "layer", sourceName, lineNumber, rawLine);
          default -> throw parseError(sourceName, lineNumber, "@displaypreset unknown option: " + option.key(), rawLine);
        }
        continue;
      }

      if (charId == null) {
        charId = token;
        continue;
      }
      if (position == null) {
        position = parsePosition(token, sourceName, lineNumber, rawLine, state);
        continue;
      }
      if (isIntegerToken(token)) {
        layerOrder = Integer.parseInt(token);
        continue;
      }
      if (!expressionSet) {
        expressionToken = token;
        expressionSet = true;
        continue;
      }
      throw parseError(sourceName, lineNumber, "@displaypreset '" + presetId + "' entry has unexpected token: " + token, rawLine);
    }

    if (charId == null || charId.isBlank()) {
      throw parseError(sourceName, lineNumber, "@displaypreset entry '" + slotId + "' requires a character id", rawLine);
    }
    if (position == null) {
      throw parseError(sourceName, lineNumber, "@displaypreset entry '" + slotId + "' requires a position", rawLine);
    }

    String expression = expressionSet
        ? resolveInlineExpressionToken(state, charId, expressionToken, sourceName, lineNumber, rawLine)
        : "neutral";
    return new DisplayPresetEntry(slotId, charId, position, expression, layerOrder);
  }

  private void parseShowDisplayPresetCommand(String arg,
                                             String sourceName,
                                             int lineNumber,
                                             String rawLine,
                                             ParseState state) throws IOException {
    String payload = requireArg(arg, "showpreset", sourceName, lineNumber, rawLine);
    String[] toks = VnArgTokenizer.tokenizeToArray(payload);
    if (toks.length == 0 || toks[0].isBlank()) {
      throw parseError(sourceName, lineNumber, "[showpreset] requires a preset id", rawLine);
    }

    DisplayPresetDef preset = requireDisplayPreset(state, toks[0], sourceName, lineNumber, rawLine, "[showpreset]");
    CharacterPosition overridePosition = null;
    for (int i = 1; i < toks.length; i++) {
      String token = toks[i] == null ? "" : toks[i].trim();
      if (token.isEmpty()) continue;
      if ("at".equalsIgnoreCase(token) && i + 1 < toks.length && !isDisplayPresetCommandOption(toks[i + 1])) {
        overridePosition = parseAtPosition(toks[++i], sourceName, lineNumber, rawLine).position();
        continue;
      }
      if (isDisplayPresetCommandOption(token)) {
        KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[showpreset]");
        switch (option.key()) {
          case "pos", "position" -> overridePosition = parsePosition(option.value(), sourceName, lineNumber, rawLine, state);
          case "at", "coord", "coords", "xy" -> overridePosition = parseAtPosition(option.value(), sourceName, lineNumber, rawLine).position();
          default -> throw parseError(sourceName, lineNumber, "[showpreset] unknown option: " + option.key(), rawLine);
        }
        continue;
      }
      if (overridePosition == null) {
        overridePosition = parsePosition(token, sourceName, lineNumber, rawLine, state);
        continue;
      }
      throw parseError(sourceName, lineNumber, "[showpreset] unexpected token: " + token, rawLine);
    }

    for (DisplayPresetEntry entry : preset.entries) {
      state.builder.show(
          entry.characterId(),
          entry.expression(),
          overridePosition == null ? entry.position() : overridePosition,
          entry.layerOrder(),
          entry.slotId());
    }
  }

  private void parseMoveDisplayPresetCommand(String arg,
                                             String sourceName,
                                             int lineNumber,
                                             String rawLine,
                                             ParseState state) throws IOException {
    String payload = requireArg(arg, "movepreset", sourceName, lineNumber, rawLine);
    String[] toks = VnArgTokenizer.tokenizeToArray(payload);
    if (toks.length == 0 || toks[0].isBlank()) {
      throw parseError(sourceName, lineNumber, "[movepreset] requires a preset id", rawLine);
    }

    DisplayPresetDef preset = requireDisplayPreset(state, toks[0], sourceName, lineNumber, rawLine, "[movepreset]");
    CharacterPosition targetPosition = null;
    Easing.Type easing = null;
    long durationMs = 0L;

    for (int i = 1; i < toks.length; i++) {
      String token = toks[i] == null ? "" : toks[i].trim();
      if (token.isEmpty()) continue;
      if ("at".equalsIgnoreCase(token) && i + 1 < toks.length && !isDisplayPresetCommandOption(toks[i + 1])) {
        targetPosition = parseAtPosition(toks[++i], sourceName, lineNumber, rawLine).position();
        continue;
      }
      if (isDisplayPresetCommandOption(token)) {
        KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[movepreset]");
        switch (option.key()) {
          case "pos", "position" -> targetPosition = parsePosition(option.value(), sourceName, lineNumber, rawLine, state);
          case "at", "coord", "coords", "xy" -> targetPosition = parseAtPosition(option.value(), sourceName, lineNumber, rawLine).position();
          case "ease", "easing" -> {
            easing = parseEasingToken(option.value());
            if (easing == null) {
              throw parseError(sourceName, lineNumber, "[movepreset] unknown easing: " + option.value(), rawLine);
            }
          }
          case "dur", "duration", "ms" -> {
            durationMs = parseLongValue(option.value(), "[movepreset]", "duration", sourceName, lineNumber, rawLine);
            if (durationMs < 0) throw parseError(sourceName, lineNumber, "[movepreset] duration must be >= 0", rawLine);
          }
          default -> throw parseError(sourceName, lineNumber, "[movepreset] unknown option: " + option.key(), rawLine);
        }
        continue;
      }
      if (isIntegerToken(token)) {
        durationMs = Long.parseLong(token);
        if (durationMs < 0) throw parseError(sourceName, lineNumber, "[movepreset] duration must be >= 0", rawLine);
        continue;
      }
      Easing.Type parsedEasing = parseEasingToken(token);
      if (parsedEasing != null) {
        easing = parsedEasing;
        continue;
      }
      if (targetPosition == null) {
        targetPosition = parsePosition(token, sourceName, lineNumber, rawLine, state);
        continue;
      }
      throw parseError(sourceName, lineNumber, "[movepreset] unexpected token: " + token, rawLine);
    }

    for (DisplayPresetEntry entry : preset.entries) {
      state.builder.move(
          entry.characterId(),
          targetPosition == null ? entry.position() : targetPosition,
          null,
          easing,
          durationMs,
          entry.slotId());
    }
  }

  private void parseHideDisplayPresetCommand(String arg,
                                             String sourceName,
                                             int lineNumber,
                                             String rawLine,
                                             ParseState state) throws IOException {
    String payload = requireArg(arg, "hidepreset", sourceName, lineNumber, rawLine);
    String[] toks = VnArgTokenizer.tokenizeToArray(payload);
    if (toks.length != 1 || toks[0].isBlank()) {
      throw parseError(sourceName, lineNumber, "[hidepreset] expects exactly one preset id", rawLine);
    }
    DisplayPresetDef preset = requireDisplayPreset(state, toks[0], sourceName, lineNumber, rawLine, "[hidepreset]");
    for (DisplayPresetEntry entry : preset.entries) {
      state.builder.hide(null, entry.slotId());
    }
  }

  private DisplayPresetDef requireDisplayPreset(ParseState state,
                                                String presetId,
                                                String sourceName,
                                                int lineNumber,
                                                String rawLine,
                                                String commandName) throws IOException {
    String id = presetId == null ? "" : presetId.trim();
    DisplayPresetDef preset = state.displayPresets.get(id);
    if (preset == null) {
      throw parseError(sourceName, lineNumber, commandName + " unknown display preset: " + id, rawLine);
    }
    if (preset.entries.isEmpty()) {
      throw parseError(sourceName, lineNumber, commandName + " display preset has no entries: " + id, rawLine);
    }
    return preset;
  }

  private boolean isDisplayPresetEntryOption(String token) {
    if (token == null || token.isBlank()) return false;
    int sep = optionSeparator(token);
    if (sep <= 0) return false;
    String key = token.substring(0, sep).trim().toLowerCase();
    return switch (key) {
      case "char", "character", "id", "sprite",
           "pos", "position", "at", "coord", "coords", "xy",
           "expr", "expression", "preset",
           "layer", "z", "zorder" -> true;
      default -> false;
    };
  }

  private boolean isDisplayPresetCommandOption(String token) {
    if (token == null || token.isBlank()) return false;
    int sep = optionSeparator(token);
    if (sep <= 0) return false;
    String key = token.substring(0, sep).trim().toLowerCase();
    return switch (key) {
      case "pos", "position", "at", "coord", "coords", "xy",
           "ease", "easing", "dur", "duration", "ms" -> true;
      default -> false;
    };
  }

  private boolean isDisplaySlotShorthand(String token) {
    if (token == null || token.length() < 2 || token.charAt(0) != '@') return false;
    String slot = token.substring(1).trim();
    return !slot.isEmpty() && LABEL_NAME_PATTERN.matcher(slot).matches();
  }

  private void startConditionalBlock(ParseState state,
                                     String expression,
                                     String sourceName,
                                     int lineNumber,
                                     String rawLine) throws IOException {
    validateConditionExpression(expression, sourceName, lineNumber, rawLine);

    String thenLabel = nextSyntheticLabel(state, "if_then");
    String falseLabel = nextSyntheticLabel(state, "if_false");
    String endLabel = nextSyntheticLabel(state, "if_end");

    state.builder.external("cond", "if " + expression + " goto " + thenLabel);
    state.builder.jump(falseLabel);

    registerLabel(state, thenLabel, sourceName, lineNumber, rawLine, true);
    state.builder.label(thenLabel);

    state.conditionalBlocks.push(new ConditionalBlock(falseLabel, endLabel, sourceName, lineNumber));
  }

  private void continueConditionalBlock(ParseState state,
                                        String expression,
                                        String sourceName,
                                        int lineNumber,
                                        String rawLine) throws IOException {
    ConditionalBlock block = state.conditionalBlocks.peek();
    if (block == null) {
      throw parseError(sourceName, lineNumber, "[elif] used without matching [if]", rawLine);
    }
    if (block.elseSeen) {
      throw parseError(sourceName, lineNumber, "[elif] cannot appear after [else]", rawLine);
    }

    validateConditionExpression(expression, sourceName, lineNumber, rawLine);

    state.builder.jump(block.endLabel);
    registerLabel(state, block.falseLabel, sourceName, lineNumber, rawLine, true);
    state.builder.label(block.falseLabel);

    String thenLabel = nextSyntheticLabel(state, "elif_then");
    String nextFalseLabel = nextSyntheticLabel(state, "elif_false");

    state.builder.external("cond", "if " + expression + " goto " + thenLabel);
    state.builder.jump(nextFalseLabel);

    registerLabel(state, thenLabel, sourceName, lineNumber, rawLine, true);
    state.builder.label(thenLabel);

    block.falseLabel = nextFalseLabel;
  }

  private void enterElseBlock(ParseState state,
                              String sourceName,
                              int lineNumber,
                              String rawLine) throws IOException {
    ConditionalBlock block = state.conditionalBlocks.peek();
    if (block == null) {
      throw parseError(sourceName, lineNumber, "[else] used without matching [if]", rawLine);
    }
    if (block.elseSeen) {
      throw parseError(sourceName, lineNumber, "Duplicate [else] in the same [if] block", rawLine);
    }

    state.builder.jump(block.endLabel);
    registerLabel(state, block.falseLabel, sourceName, lineNumber, rawLine, true);
    state.builder.label(block.falseLabel);
    block.falseLabel = null;
    block.elseSeen = true;
  }

  private void closeConditionalBlock(ParseState state,
                                     String sourceName,
                                     int lineNumber,
                                     String rawLine) throws IOException {
    ConditionalBlock block = state.conditionalBlocks.poll();
    if (block == null) {
      throw parseError(sourceName, lineNumber, "[endif] used without matching [if]", rawLine);
    }

    if (!block.elseSeen && block.falseLabel != null) {
      registerLabel(state, block.falseLabel, sourceName, lineNumber, rawLine, true);
      state.builder.label(block.falseLabel);
    }

    registerLabel(state, block.endLabel, sourceName, lineNumber, rawLine, true);
    state.builder.label(block.endLabel);
  }

  private List<Choice> parseInlineChoices(String arg,
                                          String sourceName,
                                          int lineNumber,
                                          String rawLine,
                                          ParseState state) throws IOException {
    List<Choice> out = new ArrayList<>();
    String[] rawChoices = arg.split("\\|");
    for (String raw : rawChoices) {
      if (raw == null || raw.isBlank()) continue;
      ParsedChoice parsed = parseChoiceSegment(raw.trim(), sourceName, lineNumber, rawLine);
      if (parsed == null) continue;
      if (parsed.target != null) {
        addLabelReference(state, parsed.target, sourceName, lineNumber, rawLine, "inline-choice");
      }
      out.add(parsed.toChoice());
    }
    return out;
  }

  private ParsedChoice parseChoiceSegment(String segment,
                                          String sourceName,
                                          int lineNumber,
                                          String rawLine) throws IOException {
    if (segment == null) return null;
    String work = segment.trim();
    if (work.isEmpty()) return null;

    String condition = null;
    Matcher condMatcher = CHOICE_CONDITION_SUFFIX_PATTERN.matcher(work);
    if (condMatcher.matches()) {
      work = condMatcher.group(1).trim();
      condition = condMatcher.group(2).trim();
      validateConditionExpression(condition, sourceName, lineNumber, rawLine);
    }

    String text = work;
    String target = null;
    int arrow = work.indexOf("->");
    if (arrow >= 0) {
      text = work.substring(0, arrow).trim();
      target = work.substring(arrow + 2).trim();
      if (target.isEmpty()) {
        throw parseError(sourceName, lineNumber, "Choice target label is empty", rawLine);
      }
    }

    if (text.isEmpty()) {
      throw parseError(sourceName, lineNumber, "Choice text is empty", rawLine);
    }

    return new ParsedChoice(text, target, condition);
  }

  private String resolveDisplayName(ParseState state, String speakerId) {
    String displayName = speakerId;
    com.jvn.core.vn.VnCharacter.Builder cb = state.charBuilders.get(speakerId);
    if (cb != null) {
      String dn = cb.getDisplayName();
      if (dn != null) displayName = dn;
    }
    return displayName;
  }

  private void emitDialogue(ParseState state, String speakerId, String text) {
    String displayName = resolveDisplayName(state, speakerId);
    com.jvn.core.vn.VnCharacter.Builder character = state.charBuilders.get(speakerId);
    String speakerColor = character == null ? null : character.getNameColor();
    DialogueLine.Builder line = DialogueLine.builder()
        .speakerName(displayName)
        .speakerId(speakerId)
        .speakerColor(speakerColor)
        .text(text);
    if (state.pendingVoiceTrackId != null && !state.pendingVoiceTrackId.isBlank()) {
      line.voiceTrackId(state.pendingVoiceTrackId);
      state.pendingVoiceTrackId = null;
    }
    state.builder.dialogue(line.build());
  }

  private String parseCharacterColor(String token,
                                     String sourceName,
                                     int lineNumber,
                                     String rawLine) throws IOException {
    String color = token == null ? "" : token.trim();
    if (!color.matches("#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?")) {
      throw parseError(
          sourceName,
          lineNumber,
          "@character color must use #RRGGBB or #RRGGBBAA",
          rawLine);
    }
    return color.toUpperCase(java.util.Locale.ENGLISH);
  }

  private double parseCharacterScale(String token,
                                     String sourceName,
                                     int lineNumber,
                                     String rawLine) throws IOException {
    double scale;
    try {
      scale = Double.parseDouble(token == null ? "" : token.trim());
    } catch (NumberFormatException ex) {
      throw parseError(
          sourceName,
          lineNumber,
          "@character scale must be a number between 0.1 and 3.0",
          rawLine);
    }
    if (!Double.isFinite(scale) || scale < 0.1 || scale > 3.0) {
      throw parseError(
          sourceName,
          lineNumber,
          "@character scale must be between 0.1 and 3.0",
          rawLine);
    }
    return scale;
  }

  private void flushChoices(VnScenarioBuilder builder, List<Choice> choices) {
    if (!choices.isEmpty()) {
      builder.choiceNodes(new ArrayList<>(choices));
      choices.clear();
    }
  }

  private void flushPendingVoice(ParseState state) {
    if (state.pendingVoiceTrackId == null || state.pendingVoiceTrackId.isBlank()) return;
    ensureBuilder(state);
    state.builder.playVoice(state.pendingVoiceTrackId);
    state.pendingVoiceTrackId = null;
  }

  private boolean isNamedOptionToken(String token, String commandName) {
    if (token == null || token.isBlank() || commandName == null) return false;
    int eq = token.indexOf('=');
    int colon = token.indexOf(':');
    int sep;
    if (eq > 0 && colon > 0) sep = Math.min(eq, colon);
    else sep = Math.max(eq, colon);
    if (sep <= 0) return false;
    String key = token.substring(0, sep).trim().toLowerCase();
    return switch (commandName) {
      case "show" -> switch (key) {
        case "pos", "position", "at", "coord", "coords", "xy",
             "expr", "expression", "preset", "layer", "z", "zorder",
             "slot", "as", "instance", "display", "display_slot", "display-slot" -> true;
        default -> false;
      };
      case "move" -> switch (key) {
        case "pos", "position", "at", "coord", "coords", "xy",
             "expr", "expression", "preset", "ease", "easing", "dur", "duration", "ms",
             "exprdur", "exprduration", "expr_duration", "expr-duration",
             "expressiondur", "expressionduration", "expression_duration", "expression-duration",
             "slot", "as", "instance", "display", "display_slot", "display-slot" -> true;
        default -> false;
      };
      case "hide" -> switch (key) {
        case "slot", "as", "instance", "display", "display_slot", "display-slot" -> true;
        default -> false;
      };
      case "transition" -> switch (key) {
        case "type", "kind", "style", "dur", "duration", "ms", "bg", "background" -> true;
        default -> false;
      };
      case "bgm_crossfade" -> switch (key) {
        case "track", "id", "file", "path", "dur", "duration", "ms", "loop" -> true;
        default -> false;
      };
      case "volume" -> switch (key) {
        case "channel", "target", "type", "level", "value", "vol", "volume" -> true;
        default -> false;
      };
      case "textspeed" -> switch (key) {
        case "value", "speed", "chars" -> true;
        default -> false;
      };
      case "autodelay" -> switch (key) {
        case "value", "delay", "ms", "duration" -> true;
        default -> false;
      };
      case "wait" -> switch (key) {
        case "ms", "dur", "duration", "time", "value" -> true;
        default -> false;
      };
      case "stage" -> switch (key) {
        case "preset", "id", "name", "mode" -> true;
        default -> false;
      };
      case "sfx", "voice" -> switch (key) {
        case "track", "id", "file", "path" -> true;
        default -> false;
      };
      case "particles", "particle", "weather", "pfx", "fx" -> switch (key) {
        case "preset", "type", "kind", "mode", "weather",
             "intensity", "amount", "strength", "level",
             "layer", "z", "zorder",
             "opacity", "alpha", "speed", "speedscale", "velocity",
             "wind", "drift", "windx",
             "size", "sizescale", "scale",
             "duration", "dur", "ms", "time",
             "prewarm", "warmup", "warmupms",
             "texture", "asset", "sprite", "image",
             "tint", "color", "colour" -> true;
        default -> false;
      };
      default -> false;
    };
  }

  private int parseIntegerValue(String token,
                                String commandName,
                                String fieldName,
                                String sourceName,
                                int lineNumber,
                                String rawLine) throws IOException {
    try {
      return Integer.parseInt(token.trim());
    } catch (NumberFormatException ex) {
      throw parseError(sourceName, lineNumber, commandName + " " + fieldName + " must be an integer", rawLine);
    }
  }

  private long parseLongValue(String token,
                              String commandName,
                              String fieldName,
                              String sourceName,
                              int lineNumber,
                              String rawLine) throws IOException {
    try {
      return Long.parseLong(token.trim());
    } catch (NumberFormatException ex) {
      throw parseError(sourceName, lineNumber, commandName + " " + fieldName + " must be an integer", rawLine);
    }
  }

  private double parseDoubleValue(String token,
                                  String commandName,
                                  String fieldName,
                                  String sourceName,
                                  int lineNumber,
                                  String rawLine) throws IOException {
    if (token == null) {
      throw parseError(sourceName, lineNumber, commandName + " " + fieldName + " must be a number", rawLine);
    }
    try {
      return Double.parseDouble(token.trim());
    } catch (NumberFormatException ex) {
      throw parseError(sourceName, lineNumber, commandName + " " + fieldName + " must be a number", rawLine);
    }
  }

  private double parseNonNegativeDouble(String token,
                                        String commandName,
                                        String fieldName,
                                        String sourceName,
                                        int lineNumber,
                                        String rawLine) throws IOException {
    double v = parseDoubleValue(token, commandName, fieldName, sourceName, lineNumber, rawLine);
    if (v < 0.0) {
      throw parseError(sourceName, lineNumber, commandName + " " + fieldName + " must be >= 0", rawLine);
    }
    return v;
  }

  /**
   * Parse a hex colour token in any of the common forms — {@code #aabbcc},
   * {@code #aabbccdd}, {@code 0xaabbcc}, {@code aabbcc}, plus 3/4 digit
   * shorthand ({@code #fcf}, {@code #fcfa}). Returns a packed ARGB integer.
   *
   * <p>For 6-digit forms the alpha byte is left as {@code 0x00}; downstream
   * consumers (e.g. {@link com.jvn.core.vn.VnParticlePresetLibrary}) treat
   * a zero alpha as "use the preset's natural alpha" so users don't have to
   * spell out {@code ff} every time.</p>
   */
  private Integer parseHexColor(String token,
                                String commandName,
                                String sourceName,
                                int lineNumber,
                                String rawLine) throws IOException {
    if (token == null) return null;
    String t = token.trim();
    if (t.startsWith("#")) {
      t = t.substring(1);
    } else if (t.startsWith("0x") || t.startsWith("0X")) {
      t = t.substring(2);
    }
    if (t.length() == 3 || t.length() == 4) {
      // Expand shorthand: each digit doubled.
      StringBuilder sb = new StringBuilder(t.length() * 2);
      for (int i = 0; i < t.length(); i++) {
        char c = t.charAt(i);
        sb.append(c).append(c);
      }
      t = sb.toString();
    }
    if (t.length() != 6 && t.length() != 8) {
      throw parseError(sourceName, lineNumber,
          commandName + " tint expects 3/4/6/8 hex digits, got: " + token, rawLine);
    }
    try {
      long v = Long.parseLong(t, 16);
      if (t.length() == 6) {
        // RGB only: leave the alpha byte at 0 so the consumer keeps the
        // preset's alpha curve.
        return (int) (v & 0x00FFFFFFL);
      }
      return (int) v;
    } catch (NumberFormatException ex) {
      throw parseError(sourceName, lineNumber,
          commandName + " tint must be hex, got: " + token, rawLine);
    }
  }

  private LayerPresetSpec resolveLayerPresetSpec(ParseState state,
                                                 String characterId,
                                                 String spec,
                                                 String sourceName,
                                                 int lineNumber,
                                                 String rawLine,
                                                 String context) throws IOException {
    String[] tokens = spec.split("\\|");
    List<String> resolved = new ArrayList<>();
    List<String> layerIds = new ArrayList<>();
    for (String token : tokens) {
      if (token == null) continue;
      String part = token.trim();
      if (part.isEmpty()) continue;
      if (part.startsWith("$")) {
        String rawRef = part.substring(1).trim();
        if (rawRef.isEmpty()) {
          throw parseError(sourceName, lineNumber, context + " contains empty $layer reference", rawLine);
        }
        List<LayerReference> refs = resolveLayerOrGroupReference(
            state,
            characterId,
            rawRef,
            sourceName,
            lineNumber,
            rawLine,
            context);
        for (LayerReference ref : refs) {
          resolved.add(ref.path());
          layerIds.add(ref.layerId());
        }
      } else if (part.startsWith("@")) {
        String rawPresetRef = part.substring(1).trim();
        if (rawPresetRef.isEmpty()) {
          throw parseError(sourceName, lineNumber, "@charpreset contains empty @preset reference", rawLine);
        }
        LayeredCharacterResolver.CharacterRef presetRef = LayeredCharacterResolver.parseReference(rawPresetRef, characterId);
        com.jvn.core.vn.VnCharacter.Builder presetCharacter = state.charBuilders.get(presetRef.characterId());
        String presetPath = presetCharacter == null ? null : presetCharacter.getExpressionPath(presetRef.localId());
        if (presetPath == null || presetPath.isBlank()) {
          throw parseError(
              sourceName,
              lineNumber,
              "Unknown @charpreset reference '@" + rawPresetRef + "' for character '" + presetRef.characterId() + "'",
              rawLine
          );
        }
        List<String> presetLayers = splitResolvedLayerSpec(presetPath);
        List<String> presetLayerIds = presetCharacter.getExpressionLayerIds(presetRef.localId());
        for (int i = 0; i < presetLayers.size(); i++) {
          resolved.add(presetLayers.get(i));
          layerIds.add(i < presetLayerIds.size() ? presetLayerIds.get(i) : "");
        }
      } else {
        resolved.add(part);
        layerIds.add("");
      }
    }
    if (resolved.isEmpty()) {
      throw parseError(sourceName, lineNumber, context + " produced no layers", rawLine);
    }
    return new LayerPresetSpec(String.join(" | ", resolved), layerIds);
  }

  private LayerGroupOptions parseLayerGroupOptions(String body,
                                                   String sourceName,
                                                   int lineNumber,
                                                   String rawLine) throws IOException {
    String remaining = body == null ? "" : body.trim();
    String parentId = "";
    double pivotX = 0.5;
    double pivotY = 1.0;
    boolean hasPivot = false;

    while (!remaining.isBlank()) {
      int split = firstWhitespaceIndex(remaining);
      String token = split < 0 ? remaining : remaining.substring(0, split);
      String lower = token.toLowerCase(java.util.Locale.ROOT);
      String value = optionValue(token);
      boolean consumed = false;
      if ((lower.startsWith("parent=") || lower.startsWith("parent:")) && value != null) {
        parentId = normalizeLayerGroupParent(value);
        consumed = true;
      } else if ((lower.startsWith("in=") || lower.startsWith("in:")) && value != null) {
        parentId = normalizeLayerGroupParent(value);
        consumed = true;
      } else if ((lower.startsWith("pivot=") || lower.startsWith("pivot:")
          || lower.startsWith("origin=") || lower.startsWith("origin:")) && value != null) {
        double[] pivot = parsePivotPair(value, "@chargroup", sourceName, lineNumber, rawLine);
        pivotX = pivot[0];
        pivotY = pivot[1];
        hasPivot = true;
        consumed = true;
      }

      if (!consumed) break;
      remaining = split < 0 ? "" : remaining.substring(split + 1).trim();
    }

    return new LayerGroupOptions(parentId, pivotX, pivotY, hasPivot, remaining);
  }

  private int firstWhitespaceIndex(String value) {
    if (value == null) return -1;
    for (int i = 0; i < value.length(); i++) {
      if (Character.isWhitespace(value.charAt(i))) return i;
    }
    return -1;
  }

  private String optionValue(String token) {
    if (token == null) return null;
    int eq = token.indexOf('=');
    int colon = token.indexOf(':');
    int idx;
    if (eq > 0 && colon > 0) idx = Math.min(eq, colon);
    else idx = Math.max(eq, colon);
    if (idx <= 0 || idx >= token.length() - 1) return null;
    return token.substring(idx + 1).trim();
  }

  private String normalizeLayerGroupParent(String value) {
    String parent = value == null ? "" : stripQuotes(value).trim();
    if (parent.isBlank() || "none".equalsIgnoreCase(parent) || "root".equalsIgnoreCase(parent)) {
      return "";
    }
    return parent;
  }

  private double[] parsePivotPair(String value,
                                  String commandName,
                                  String sourceName,
                                  int lineNumber,
                                  String rawLine) throws IOException {
    String raw = stripQuotes(value == null ? "" : value.trim());
    String[] parts = raw.split(",", -1);
    if (parts.length != 2) {
      throw parseError(sourceName, lineNumber, commandName + " pivot expects x,y", rawLine);
    }
    try {
      double x = Double.parseDouble(parts[0].trim());
      double y = Double.parseDouble(parts[1].trim());
      if (!Double.isFinite(x) || !Double.isFinite(y)) {
        throw new NumberFormatException(raw);
      }
      return new double[] {x, y};
    } catch (NumberFormatException ex) {
      throw parseError(sourceName, lineNumber, commandName + " pivot values must be numeric", rawLine);
    }
  }

  private String normalizeCharacterInteropPayload(ParseState state,
                                                  String payload,
                                                  String sourceName,
                                                  int lineNumber,
                                                  String rawLine) throws IOException {
    String[] toks = VnArgTokenizer.tokenizeToArray(payload);
    if (toks.length < 2) return payload;

    String characterId = toks[0].trim();
    String cmd = toks[1].trim().toLowerCase();
    if (characterId.isEmpty() || cmd.isEmpty()) return payload;

    switch (cmd) {
      case "move": {
        int nextIdx = 3;
        if (toks.length >= 4 && "at".equalsIgnoreCase(toks[2])) {
          nextIdx = 4;
        }
        for (int ti = nextIdx; ti < toks.length; ti++) {
          String tok = toks[ti].trim();
          if (tok.isEmpty()) continue;
          if (isIntegerToken(tok)) continue;
          if (isNamedOptionToken(tok, "move")) {
            toks[ti] = resolveNamedExpressionOptionToken(state, characterId, tok, sourceName, lineNumber, rawLine);
            continue;
          }
          Easing.Type easing = parseEasingToken(tok);
          if (easing != null) continue;
          toks[ti] = resolveInlineExpressionToken(state, characterId, tok, sourceName, lineNumber, rawLine);
          break;
        }
        return joinNormalizedTokens(toks);
      }
      case "show": {
        int exprIdx = 3;
        if (toks.length >= 4 && "at".equalsIgnoreCase(toks[2])) {
          exprIdx = 4;
        }
        for (int ti = exprIdx; ti < toks.length; ti++) {
          String tok = toks[ti].trim();
          if (tok.isEmpty()) continue;
          if (isIntegerToken(tok)) continue;
          if (isNamedOptionToken(tok, "show")) {
            toks[ti] = resolveNamedExpressionOptionToken(state, characterId, tok, sourceName, lineNumber, rawLine);
            continue;
          }
          Easing.Type easing = parseEasingToken(tok);
          if (easing != null) continue;
          toks[ti] = resolveInlineExpressionToken(state, characterId, tok, sourceName, lineNumber, rawLine);
          break;
        }
        return joinNormalizedTokens(toks);
      }
      case "expression":
      case "expr": {
        if (toks.length >= 3) {
          toks[2] = resolveInlineExpressionToken(state, characterId, toks[2], sourceName, lineNumber, rawLine);
        }
        return joinNormalizedTokens(toks);
      }
      default:
        return payload;
    }
  }

  private String joinNormalizedTokens(String[] tokens) {
    StringBuilder sb = new StringBuilder();
    for (String token : tokens) {
      if (token == null || token.isEmpty()) continue;
      if (sb.length() > 0) sb.append(' ');
      sb.append(quoteTokenIfNeeded(token));
    }
    return sb.toString();
  }

  private String resolveNamedExpressionOptionToken(ParseState state,
                                                   String characterId,
                                                   String token,
                                                   String sourceName,
                                                   int lineNumber,
                                                   String rawLine) throws IOException {
    int sep = optionSeparator(token);
    if (sep <= 0 || sep >= token.length() - 1) return token;
    String key = token.substring(0, sep).trim().toLowerCase();
    if (!("expr".equals(key) || "expression".equals(key) || "preset".equals(key))) return token;
    String value = token.substring(sep + 1).trim();
    String resolved = resolveInlineExpressionToken(state, characterId, value, sourceName, lineNumber, rawLine);
    return token.substring(0, sep + 1) + resolved;
  }

  private int optionSeparator(String token) {
    if (token == null) return -1;
    int eq = token.indexOf('=');
    int colon = token.indexOf(':');
    if (eq > 0 && colon > 0) return Math.min(eq, colon);
    return Math.max(eq, colon);
  }

  private String resolveInlineExpressionToken(ParseState state,
                                              String characterId,
                                              String rawToken,
                                              String sourceName,
                                              int lineNumber,
                                              String rawLine) throws IOException {
    String token = rawToken == null ? "" : rawToken.trim();
    if (token.isEmpty()) return token;
    if (!token.startsWith("@") && token.indexOf('$') < 0 && token.indexOf('+') < 0) {
      if (!getOrCreateCharacterBuilder(state, characterId).hasExpression(token)) {
        warningSubscriber.onWarningEvent(
                UnknownExpressionWarningFactory.getInstance(
                        characterId, token, sourceName, lineNumber, rawLine));
      }
      return token;
    }

    if (token.startsWith("@") && token.indexOf('+') < 0 && token.indexOf('$') < 0) {
      String presetName = token.substring(1).trim();
      if (presetName.isEmpty()) {
        throw parseError(sourceName, lineNumber, "Inline preset reference cannot be empty", rawLine);
      }
      return presetName;
    }

    List<String> resolvedParts = new ArrayList<>();
    List<String> resolvedLayerIds = new ArrayList<>();
    for (String rawPart : token.split("\\+")) {
      String part = rawPart == null ? "" : rawPart.trim();
      if (part.isEmpty()) {
        throw parseError(sourceName, lineNumber, "Inline composite expression contains an empty segment", rawLine);
      }
      if (part.startsWith("@")) {
        String presetName = part.substring(1).trim();
        if (presetName.isEmpty()) {
          throw parseError(sourceName, lineNumber, "Inline preset reference cannot be empty", rawLine);
        }
        String presetPath = getOrCreateCharacterBuilder(state, characterId).getExpressionPath(presetName);
        if (presetPath == null || presetPath.isBlank()) {
          throw parseError(
              sourceName,
              lineNumber,
              "Unknown character preset '@" + presetName + "' for character '" + characterId + "'",
              rawLine);
        }
        List<String> presetLayers = splitResolvedLayerSpec(presetPath);
        List<String> presetLayerIds = getOrCreateCharacterBuilder(state, characterId).getExpressionLayerIds(presetName);
        for (int i = 0; i < presetLayers.size(); i++) {
          resolvedParts.add(presetLayers.get(i));
          resolvedLayerIds.add(i < presetLayerIds.size() ? presetLayerIds.get(i) : "");
        }
        continue;
      }
      if (part.startsWith("$")) {
        List<LayerReference> layers = resolveLayerOrGroupReference(
            state,
            characterId,
            part.substring(1),
            sourceName,
            lineNumber,
            rawLine,
            "Inline composite expression");
        for (LayerReference layer : layers) {
          resolvedParts.add(layer.path());
          resolvedLayerIds.add(layer.layerId());
        }
        continue;
      }
      throw parseError(
          sourceName,
          lineNumber,
          "Inline composite segments must use @preset or $layer syntax: " + part,
          rawLine);
    }

    if (resolvedParts.isEmpty()) {
      throw parseError(sourceName, lineNumber, "Inline composite expression produced no layers", rawLine);
    }

    String resolvedSpec = String.join(" | ", resolvedParts);
    String cacheKey = characterId + "|" + resolvedSpec + "|" + String.join("|", resolvedLayerIds);
    String existing = state.inlineCompositeExpressions.get(cacheKey);
    if (existing != null) {
      return existing;
    }

    String exprName = buildInlineExpressionName(token, resolvedSpec);
    com.jvn.core.vn.VnCharacter.Builder builder = getOrCreateCharacterBuilder(state, characterId);
    if (!builder.hasExpression(exprName)) {
      builder.addExpression(exprName, resolvedSpec, resolvedLayerIds);
    }
    state.inlineCompositeExpressions.put(cacheKey, exprName);
    return exprName;
  }

  private List<String> splitResolvedLayerSpec(String spec) {
    String[] tokens = spec.split("\\|");
    List<String> resolved = new ArrayList<>();
    for (String token : tokens) {
      if (token == null) continue;
      String trimmed = token.trim();
      if (!trimmed.isEmpty()) {
        resolved.add(trimmed);
      }
    }
    return resolved;
  }

  private LayerReference resolveLayerReference(ParseState state,
                                               String defaultCharacterId,
                                               String rawRef,
                                               String sourceName,
                                               int lineNumber,
                                               String rawLine) throws IOException {
    LayerReference resolved = tryResolveLayerReference(state, defaultCharacterId, rawRef);
    LayeredCharacterResolver.CharacterRef layerRef = LayeredCharacterResolver.parseReference(rawRef, defaultCharacterId);
    if (resolved == null) {
      throw parseError(
          sourceName,
          lineNumber,
          "Unknown @charlayer reference '$" + rawRef + "' for character '" + layerRef.characterId() + "'",
          rawLine);
    }
    return resolved;
  }

  private List<LayerReference> resolveLayerOrGroupReference(ParseState state,
                                                            String defaultCharacterId,
                                                            String rawRef,
                                                            String sourceName,
                                                            int lineNumber,
                                                            String rawLine,
                                                            String context) throws IOException {
    List<LayeredCharacterResolver.LayerMatch> layerMatches =
        LayeredCharacterResolver.resolveLayerMatches(state.charLayers, defaultCharacterId, rawRef);
    if (!layerMatches.isEmpty()) {
      LayeredCharacterResolver.CharacterRef layerRef =
          LayeredCharacterResolver.parseReference(rawRef, defaultCharacterId);
      boolean glob = LayeredCharacterResolver.containsGlob(layerRef.localId());
      return layerMatches.stream()
          .map(match -> new LayerReference(
              match.path(), glob ? match.layerId() : layerRef.localId()))
          .toList();
    }

    LayeredCharacterResolver.CharacterRef groupRef = LayeredCharacterResolver.parseReference(rawRef, defaultCharacterId);
    com.jvn.core.vn.VnCharacter.Builder groupCharacter = state.charBuilders.get(groupRef.characterId());
    com.jvn.core.vn.VnCharacter.LayerGroup group =
        groupCharacter == null ? null : groupCharacter.getLayerGroup(groupRef.localId());
    if (group != null && !group.layerIds().isEmpty()) {
      List<LayerReference> out = new ArrayList<>();
      for (String layerId : group.layerIds()) {
        LayerReference groupLayer = tryResolveLayerReference(state, groupRef.characterId(), layerId);
        if (groupLayer != null) out.add(groupLayer);
      }
      if (!out.isEmpty()) return out;
    }

    if (LayeredCharacterResolver.containsGlob(groupRef.localId())) {
      throw parseError(
          sourceName,
          lineNumber,
          context + " layer glob '$" + rawRef + "' matched no declared @charlayer IDs for character '"
              + groupRef.characterId() + "'",
          rawLine);
    }

    if (groupCharacter == null || groupCharacter.getLayerGroups().isEmpty()) {
      throw parseError(
          sourceName,
          lineNumber,
          "Unknown @charlayer reference '$" + rawRef + "' for character '" + groupRef.characterId() + "'",
          rawLine);
    }
    throw parseError(
        sourceName,
        lineNumber,
        context + " references unknown @charlayer/@chargroup '$" + rawRef
            + "' for character '" + groupRef.characterId() + "'",
        rawLine);
  }

  private LayerReference tryResolveLayerReference(ParseState state,
                                                  String defaultCharacterId,
                                                  String rawRef) {
    String path = LayeredCharacterResolver.resolveLayerPath(state.charLayers, defaultCharacterId, rawRef);
    LayeredCharacterResolver.CharacterRef layerRef = LayeredCharacterResolver.parseReference(rawRef, defaultCharacterId);
    if (path == null || path.isBlank()) {
      return null;
    }
    return new LayerReference(path, layerRef.localId());
  }

  private com.jvn.core.vn.VnCharacter.Builder getOrCreateCharacterBuilder(ParseState state, String characterId) {
    com.jvn.core.vn.VnCharacter.Builder cb = state.charBuilders.get(characterId);
    if (cb == null) {
      cb = com.jvn.core.vn.VnCharacter.builder(characterId);
      state.charBuilders.put(characterId, cb);
    }
    return cb;
  }

  private String buildInlineExpressionName(String token, String resolvedSpec) {
    String base = token
        .replace('@', ' ')
        .replace('$', ' ')
        .replace('+', ' ')
        .replace(':', ' ')
        .replace('.', ' ')
        .trim()
        .replaceAll("[^A-Za-z0-9_]+", "_")
        .replaceAll("_+", "_");
    if (base.isEmpty()) {
      base = "composite";
    }
    if (!Character.isLetter(base.charAt(0)) && base.charAt(0) != '_') {
      base = "_" + base;
    }
    String hash = Integer.toUnsignedString(resolvedSpec.hashCode(), 36);
    return "__inline_" + base.toLowerCase() + "_" + hash;
  }

  private void validateLabelReferences(ParseState state) throws IOException {
    for (LabelReference ref : state.labelReferences) {
      if (ref.label == null || ref.label.isBlank()) continue;
      if (!state.declaredLabels.containsKey(ref.label)) {
        throw parseError(
            ref.source,
            ref.line,
            "Undefined label '" + ref.label + "' referenced by " + ref.kind,
            ref.rawLine
        );
      }
    }
  }

  private void registerLabel(ParseState state,
                             String label,
                             String sourceName,
                             int lineNumber,
                             String rawLine,
                             boolean synthetic) throws IOException {
    String normalized = label == null ? "" : label.trim();
    if (normalized.isEmpty()) {
      throw parseError(sourceName, lineNumber, "Label name is empty", rawLine);
    }
    if (!LABEL_NAME_PATTERN.matcher(normalized).matches()) {
      throw parseError(sourceName, lineNumber, "Invalid label name '" + normalized + "'", rawLine);
    }

    LabelDeclaration prev = state.declaredLabels.get(normalized);
    if (prev != null) {
      String where = " (previously declared at " + prev.source + ":" + prev.line + ")";
      throw parseError(sourceName, lineNumber, "Duplicate label '" + normalized + "'" + where, rawLine);
    }
    state.declaredLabels.put(normalized, new LabelDeclaration(sourceName, lineNumber, synthetic));
  }

  private void addLabelReference(ParseState state,
                                 String label,
                                 String sourceName,
                                 int lineNumber,
                                 String rawLine,
                                 String kind) {
    if (label == null || label.isBlank()) return;
    state.labelReferences.add(new LabelReference(label.trim(), sourceName, lineNumber, rawLine, kind));
  }

  private String nextSyntheticLabel(ParseState state, String prefix) {
    state.syntheticLabelCounter++;
    return "__" + prefix + "_" + state.syntheticLabelCounter;
  }

  private IfGoto parseIfGoto(String payload) {
    Matcher m = IF_GOTO_PATTERN.matcher(payload == null ? "" : payload.trim());
    if (!m.matches()) return null;
    String expr = m.group(1) == null ? "" : m.group(1).trim();
    String label = m.group(2) == null ? "" : m.group(2).trim();
    if (expr.isEmpty() || label.isEmpty()) return null;
    return new IfGoto(expr, label);
  }

  private void validateConditionExpression(String expression,
                                           String sourceName,
                                           int lineNumber,
                                           String rawLine) throws IOException {
    try {
      VnConditionEvaluator.validate(expression);
    } catch (IllegalArgumentException ex) {
      throw parseError(sourceName, lineNumber, "Invalid condition expression: " + ex.getMessage(), rawLine);
    }
  }

  private record InlinePosition(CharacterPosition position, Integer layerOrder) {}

  private InlinePosition parseAtPosition(String coordToken, String sourceName,
                                         int lineNumber, String rawLine) throws IOException {
    String[] parts = coordToken.split(",");
    if (parts.length < 1 || parts.length > 3) {
      throw parseError(sourceName, lineNumber, "'at' expects x,y or x,y,z format, got: " + coordToken, rawLine);
    }
    try {
      double x = Double.parseDouble(parts[0].trim());
      double y = parts.length >= 2 ? Double.parseDouble(parts[1].trim()) : -1.0;
      Integer z = parts.length == 3 ? Integer.parseInt(parts[2].trim()) : null;
      return new InlinePosition(CharacterPosition.at(x, y), z);
    } catch (NumberFormatException e) {
      throw parseError(sourceName, lineNumber, "'at' coordinates must be numbers: " + coordToken, rawLine);
    }
  }

  private CharacterPosition tryParsePosition(String token) {
    if (token == null || token.isBlank()) return null;
    String t = token.trim().toUpperCase(java.util.Locale.ENGLISH);
    return switch (t) {
      case "FAR_LEFT", "FL", "FARLEFT" -> CharacterPosition.FAR_LEFT;
      case "LEFT", "L" -> CharacterPosition.LEFT;
      case "CENTER", "C", "CENTRE" -> CharacterPosition.CENTER;
      case "RIGHT", "R" -> CharacterPosition.RIGHT;
      case "FAR_RIGHT", "FR", "FARRIGHT" -> CharacterPosition.FAR_RIGHT;
      case null, default -> null;
    };
  }

  private CharacterPosition parsePosition(String token,
                                          String sourceName,
                                          int lineNumber,
                                          String rawLine,
                                          ParseState state) throws IOException {
    CharacterPosition predefined = CharacterPosition.predefined(token);
    if (predefined != null) return predefined;
    // Check custom positions defined via @position
    CharacterPosition custom = state.getCustomPosition(token);
    if (custom != null) return custom;
    throw parseError(sourceName, lineNumber, "Unknown character position: " + token, rawLine);
  }

  private VnTransition.TransitionType parseTransitionType(String token) {
    String t = token.trim().toUpperCase(java.util.Locale.ENGLISH);
    try {
      return VnTransition.TransitionType.valueOf(t);
    } catch (IllegalArgumentException e) {
      if (t.equals("FADE")) return VnTransition.TransitionType.FADE;
      if (t.equals("DISSOLVE")) return VnTransition.TransitionType.DISSOLVE;
      if (t.equals("CROSSFADE")) return VnTransition.TransitionType.CROSSFADE;
      if (t.equals("SLIDE_LEFT")) return VnTransition.TransitionType.SLIDE_LEFT;
      if (t.equals("SLIDE_RIGHT")) return VnTransition.TransitionType.SLIDE_RIGHT;
      if (t.equals("WIPE")) return VnTransition.TransitionType.WIPE;
      if (t.equals("PIXELATE") || t.equals("PIXEL")) return VnTransition.TransitionType.PIXELATE;
      if (t.equals("BLINDS") || t.equals("BLIND")) return VnTransition.TransitionType.BLINDS;
      if (t.equals("IRIS_IN") || t.equals("IRISIN")) return VnTransition.TransitionType.IRIS_IN;
      if (t.equals("IRIS_OUT") || t.equals("IRISOUT")) return VnTransition.TransitionType.IRIS_OUT;
      if (t.equals("MASK")) return VnTransition.TransitionType.MASK;
      return null;
    }
  }

  private void ensureBuilder(ParseState state) {
    if (state.builder == null) {
      state.builder = new VnScenarioBuilder(state.scenarioId)
          .sourceLine(state.currentSourceLine);
    }
  }

  private String requireArg(String arg,
                            String cmd,
                            String sourceName,
                            int lineNumber,
                            String rawLine) throws IOException {
    if (arg == null || arg.isBlank()) {
      throw parseError(sourceName, lineNumber, "[" + cmd + "] requires arguments", rawLine);
    }
    return arg.trim();
  }

  private void ensureNoArg(String arg,
                           String cmd,
                           String sourceName,
                           int lineNumber,
                           String rawLine) throws IOException {
    if (arg != null && !arg.isBlank()) {
      throw parseError(sourceName, lineNumber, "[" + cmd + "] does not accept arguments", rawLine);
    }
  }

  private String timelineHeaderModifiers(String header) {
    if (header == null) return "";
    String trimmed = header.trim();
    if (!hasTimelineKeyword(trimmed)) return "";
    String tail = trimmed.substring("timeline".length()).trim();
    int brace = timelineOpeningBrace(tail);
    if (brace >= 0) tail = tail.substring(0, brace).trim();
    return tail;
  }

  private boolean isTimelineHeader(String header) {
    if (!hasTimelineKeyword(header)) return false;
    String code = header.trim();
    return code.equals("timeline") || code.endsWith("{");
  }

  private boolean hasTimelineKeyword(String value) {
    if (value == null) return false;
    String trimmed = value.trim();
    if (!trimmed.startsWith("timeline")) return false;
    return trimmed.length() == "timeline".length()
        || Character.isWhitespace(trimmed.charAt("timeline".length()))
        || trimmed.charAt("timeline".length()) == '{';
  }

  /**
   * Removes a VNS-style trailing comment while respecting quoted values.
   * A hash begins a comment at the start of a line or after whitespace.
   */
  private String timelineHeaderCode(String value) {
    if (value == null || value.isEmpty()) return "";
    char quote = 0;
    boolean escaped = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (quote != 0) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == quote) {
          quote = 0;
        }
        continue;
      }
      if (c == '"' || c == '\'') {
        quote = c;
      } else if (isTimelineCommentStart(value, i)) {
        return value.substring(0, i).trim();
      }
    }
    return value.trim();
  }

  private TimelineBraceScan scanTimelineBraces(String value, int initialDepth) {
    int depth = initialDepth;
    char quote = 0;
    boolean escaped = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (quote != 0) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == quote) {
          quote = 0;
        }
        continue;
      }
      if (c == '"' || c == '\'') {
        quote = c;
        continue;
      }
      if (isTimelineCommentStart(value, i)) {
        break;
      }
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) return new TimelineBraceScan(depth, i);
      }
    }
    return new TimelineBraceScan(depth, -1);
  }

  private int timelineOpeningBrace(String value) {
    if (value == null || value.isEmpty()) return -1;
    char quote = 0;
    boolean escaped = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (quote != 0) {
        if (escaped) escaped = false;
        else if (c == '\\') escaped = true;
        else if (c == quote) quote = 0;
      } else if (c == '"' || c == '\'') {
        quote = c;
      } else if (isTimelineCommentStart(value, i)) {
        return -1;
      } else if (c == '{') {
        return i;
      }
    }
    return -1;
  }

  private boolean isTimelineCommentStart(String value, int index) {
    if (value == null || index < 0 || index >= value.length() || value.charAt(index) != '#') {
      return false;
    }
    if (isTimelineHexColor(value, index)) return false;
    if (index == 0 || Character.isWhitespace(value.charAt(index - 1))) return true;
    char previous = value.charAt(index - 1);
    return previous == '{' || previous == '}';
  }

  private boolean isTimelineHexColor(String value, int hashIndex) {
    int end = hashIndex + 1;
    while (end < value.length() && Character.digit(value.charAt(end), 16) >= 0) end++;
    int digits = end - hashIndex - 1;
    if (digits != 3 && digits != 4 && digits != 6 && digits != 8) return false;
    return end == value.length()
        || Character.isWhitespace(value.charAt(end))
        || value.charAt(end) == '}'
        || value.charAt(end) == ','
        || value.charAt(end) == ';';
  }

  private record TimelineBraceScan(int depth, int closingBrace) {}

  private void validateJavaImport(String imp, String sourceName, int lineNumber, String rawLine) throws IOException {
    if (!JAVA_IMPORT_PATTERN.matcher(imp).matches()) {
      throw parseError(sourceName, lineNumber,
          "@jimport must be a Java import path such as java.util.List or static com.example.Tools.*",
          rawLine);
    }
  }

  private void validateJavaBind(String bind, String sourceName, int lineNumber, String rawLine) throws IOException {
    int colon = bind.indexOf(':');
    String type = colon < 0 ? "Object" : bind.substring(0, colon).trim();
    String name = colon < 0 ? bind.trim() : bind.substring(colon + 1).trim();
    if (name.isEmpty() || !JAVA_IDENTIFIER_PATTERN.matcher(name).matches()) {
      throw parseError(sourceName, lineNumber, "@bind variable name must be a valid Java identifier", rawLine);
    }
    if (type.isEmpty() || !JAVA_TYPE_PATTERN.matcher(type).matches()) {
      throw parseError(sourceName, lineNumber, "@bind type must be a valid Java type name", rawLine);
    }
  }

  private VnParseException parseError(String sourceName, int lineNumber, String message, String line) {
    String src = (sourceName == null || sourceName.isBlank()) ? "<script>" : sourceName;
    return new VnParseException("Parse error in " + src + " at line " + lineNumber + ": " + message + " -> " + line, sourceName, lineNumber, message, line);
  }

  private String applyDefines(String line, Map<String, String> defines) {
    Matcher m = DEFINE_SUB_PATTERN.matcher(line);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String key = m.group(1);
      String val = defines.getOrDefault(key, "");
      m.appendReplacement(sb, Matcher.quoteReplacement(val));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private String parseAssetDeclarationPath(String raw) {
    String value = raw.trim();
    boolean quoted = false;
    boolean escaped = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (escaped) escaped = false;
      else if (c == '\\') escaped = true;
      else if (c == '"') quoted = !quoted;
      else if (c == '#' && !quoted && i > 0 && Character.isWhitespace(value.charAt(i - 1))) {
        value = value.substring(0, i).stripTrailing();
        break;
      }
    }
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1)
          .replace("\\\"", "\"").replace("\\\\", "\\");
    }
    return value;
  }

  private String resolveIncludePath(String sourceName, String includePath) {
    if (includePath.startsWith("/") || includePath.contains(":")) return includePath;
    String src = normalizeSourceName(sourceName);
    int idx = src.lastIndexOf('/');
    if (idx < 0) return includePath;
    return src.substring(0, idx + 1) + includePath;
  }

  private List<String> includeCandidates(String sourceName, String includePath) {
    List<String> candidates = new ArrayList<>();
    String resolved = normalizeSourceName(resolveIncludePath(sourceName, includePath));
    candidates.add(resolved);
    if (!includePath.startsWith("/") && !includePath.contains(":")) {
      String rootRelative = normalizeSourceName(includePath);
      if (!rootRelative.equals(resolved)) candidates.add(rootRelative);
    }
    return candidates;
  }

  private String normalizeSourceName(String sourceName) {
    if (sourceName == null || sourceName.isBlank()) return "<script>";
    return sourceName.replace('\\', '/');
  }

  private String stripQuotes(String value) {
    if (value == null) return "";
    String t = value.trim();
    if (t.length() >= 2) {
      char c0 = t.charAt(0);
      char c1 = t.charAt(t.length() - 1);
      if ((c0 == '"' && c1 == '"') || (c0 == '\'' && c1 == '\'')) {
        return t.substring(1, t.length() - 1);
      }
    }
    return t;
  }

  private String unescapeQuoted(String value) {
    if (value == null || value.isEmpty()) return "";
    StringBuilder out = new StringBuilder(value.length());
    boolean esc = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (esc) {
        switch (c) {
          case 'n' -> out.append('\n');
          case 't' -> out.append('\t');
          case 'r' -> out.append('\r');
          case '"' -> out.append('"');
          case '\\' -> out.append('\\');
          default -> out.append(c);
        }
        esc = false;
      } else if (c == '\\') {
        esc = true;
      } else {
        out.append(c);
      }
    }
    if (esc) out.append('\\');
    return out.toString();
  }

  private Easing.Type parseEasingToken(String token) {
    com.jvn.core.animation.EasingSpec spec = com.jvn.core.animation.EasingSpec.tryParse(token);
    return spec == null ? null : spec.getType();
  }
}
