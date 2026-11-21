package com.jvn.editor.ui;

import com.jvn.scripting.jes.JesParser;

import java.io.File;
import java.util.Objects;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the unfinished block up to the caret, including during parser errors. */
final class JesCompletion {
  private static final Pattern TOKENS = Pattern.compile(
      "//[^\\r\\n]*|\"(?:[^\"\\\\]++|\\\\[\\s\\S])*+(?<closed>\")?|[\\w.-]+|[^\\s]");
  private static final Set<String> COMPONENTS = JesParser.componentTypes();
  private static final Set<String> ACTIONS = JesParser.timelineActions();
  private static final Set<String> IMAGE_KEYS = Set.of("image", "texture", "spriteSheet");
  private static final Set<String> ENTITY_ACTIONS = Set.of("move", "depth", "pivot", "rotate", "scale", "fade",
      "visible", "show", "hide", "replace", "expression", "damage", "heal", "walkToTile", "emitParticles",
      "property", "brightness", "exposure", "cameraFollow", "setParallax");

  private File assetRoot;
  private int assetQuote = -1;
  private List<String> imagePaths = List.of();

  List<CodeAutoCompleter.Suggestion> suggest(CodeAutoCompleter.Context ctx, List<String> entities,
                                                    List<String> labels, boolean timelineFragment) {
    String prefix = ctx.prefix;
    int start = ctx.caret - prefix.length();
    Matcher tokens = TOKENS.matcher(ctx.text).region(0, start);
    ArrayDeque<String> blocks = new ArrayDeque<>();
    String previous = "", last = "", blockName = "";
    boolean quoted = false;
    if (prefix.startsWith("//")) return List.of();
    while (tokens.find()) {
      String token = tokens.group();
      if (token.startsWith("//")) {
        if (tokens.end() == start && (start == ctx.text.length() || ctx.text.charAt(start) != '\n')) return List.of();
        continue;
      }
      if (token.startsWith("\"") && tokens.group("closed") == null) {
        if (tokens.end() != start || tokens.start() != start - 1) return List.of();
        quoted = true;
        break;
      }
      if (token.equals("{")) blocks.push(blockName);
      if (token.equals("}") && !blocks.isEmpty()) blocks.pop();
      if (!token.startsWith("\"")) blockName = token;
      previous = last;
      last = token;
    }
    String owner = blocks.isEmpty() ? "" : blocks.peek();
    Collection<String> options;
    String suffix = "";
    if (!quoted) assetQuote = -1;
    if (quoted) {
      if (last.equals(":") && IMAGE_KEYS.contains(previous)) {
        if (assetQuote != start || !Objects.equals(assetRoot, ctx.projectRoot)) {
          imagePaths = CodeAutoCompleter.listAssetPaths(ctx.projectRoot, "assets", ".png", ".jpg", ".jpeg", ".webp", ".gif");
          assetQuote = start;
          assetRoot = ctx.projectRoot;
        }
        options = imagePaths;
      } else if (last.equals("jump")) options = labels;
      else if (ENTITY_ACTIONS.contains(last) || last.equals(":") && previous.equals("target")) options = entities;
      else return List.of();
    } else if (last.equals("component")) options = COMPONENTS;
    else if (last.equals("do")) options = List.of("toggleDebug", "spawnCircle", "spawnBox", "moveHero", "interact", "attack");
    else if (last.equals(":")) {
      options = switch (previous) {
        case "static", "sensor", "bold", "controllable", "additive", "loop", "removeOnDeath", "requiresLineOfSight" -> List.of("false", "true");
        case "fill", "color", "textColor", "startColor", "endColor" -> List.of("rgb(", "rgba(");
        default -> List.of();
      };
    } else if (COMPONENTS.contains(owner)) {
      options = JesParser.componentProperties(owner);
      suffix = ": ";
    } else if (owner.equals("entity")) options = List.of("component");
    else if (owner.equals("timeline") || owner.equals("parallel") || owner.isEmpty() && timelineFragment) options = ACTIONS;
    else if (ACTIONS.contains(owner) && !owner.equals("scene")) {
      TreeSet<String> properties = new TreeSet<>();
      for (String key : JesParser.timelineProperties(owner)) properties.add(key + ": ");
      if (owner.equals("loop")) properties.addAll(ACTIONS);
      options = properties;
    } else if (owner.equals("scene")) options = List.of("entity", "timeline", "on", "map", "item", "tileset");
    else if (owner.isEmpty()) options = List.of("scene");
    else return List.of();

    String search = prefix.toLowerCase(Locale.ROOT);
    String ending = suffix;
    return options.stream().filter(value -> {
      String candidate = value.toLowerCase(Locale.ROOT);
      return candidate.startsWith(search) || candidate.contains("/") && candidate.substring(candidate.lastIndexOf('/') + 1).startsWith(search);
    }).sorted().map(value -> new CodeAutoCompleter.Suggestion(value + ending)).toList();
  }
}
