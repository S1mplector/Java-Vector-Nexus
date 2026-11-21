package com.jvn.core.script;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The syntax classes used by both the source editors and in-game Facet code blocks. */
public enum SyntaxRules {
  VNS(Vns.TOKEN_PATTERN, Vns::style),
  JES(Jes.PATTERN, Jes::style),
  PROPERTIES(PropertiesDsl.PROPERTIES_PATTERN, PropertiesDsl::style);

  @FunctionalInterface
  public interface SpanConsumer {
    /** An empty style denotes ordinary source text. Offsets are UTF-16 indices. */
    void accept(int start, int end, String style);
  }

  private final Pattern tokens;
  private final Function<Matcher, String> style;

  SyntaxRules(Pattern tokens, Function<Matcher, String> style) {
    this.tokens = tokens;
    this.style = style;
  }

  public void scan(String source, SpanConsumer consumer) {
    Matcher matcher = tokens.matcher(source);
    int last = 0;
    while (matcher.find()) {
      if (last < matcher.start()) consumer.accept(last, matcher.start(), "");
      consumer.accept(matcher.start(), matcher.end(), style.apply(matcher));
      last = matcher.end();
    }
    if (last < source.length() || source.isEmpty()) consumer.accept(last, source.length(), "");
  }

  private static final class Vns {
    private static final String COMMENT_PATTERN = "(?m)#.*$";
    private static final String STRING_PATTERN = "\"([^\\\\\"]|\\\\.)*\"";
    private static final String FORMAT_PATTERN = "\\{/?[bius]\\}|\\{color=[^}]*\\}|\\{/color\\}";
    private static final String DIRECTIVE_PATTERN = "@(?:scenario|character|background|charimg|charlayer|chargroup|charpreset|displaypreset|stagepreset|position|label|define|include|var|bind|jimport|external)\\b";
    private static final String CMD_OPEN_PATTERN =
        "\\[(?:showpreset|movepreset|hidepreset|show|move|hide|jump|end|wait|bg|background"
      + "|plugin|bgm_crossfade|bgm_fadeout|bgm_resume|bgm_pause|bgm_seek|bgm_stop|bgm"
      + "|particles|particle|weather|pfx|fx"
      + "|audio_resume_all|audio_pause_all|audio_stop_all|audio|sfx_stop|sfx|voice_stop|voice|volume|textspeed|autodelay"
      + "|hud|save|quickload|skip|auto|mode|ui|visualizer|viz|history|screen|phone"
      + "|jes_push|jes_replace|jes_pop|jes_call|jes|java"
      + "|transition|stage|menu|settings|mainmenu|load|goto"
      + "|set|eval|inc|dec|mul|div|toggle|flag|unflag|clear|persistent"
      + "|if|elif|else|endif|/if"
      + "|call|gosub|return|character|char|choice)\\b";
    private static final String ARROW_PATTERN = "->";
    private static final String SPEAKER_PATTERN = "(?m)^(?:[^\\s#:][^:\\r\\n]{0,30}):";
    private static final String CHOICE_MARK_PATTERN = "(?m)^\\s*>";
    private static final String TIMELINE_PATTERN = "(?m)^\\s*timeline\\b";
    private static final String VALUE_PATTERN =
        "\\b(?:left|right|center|far_left|far_right"
      + "|fade|dissolve|crossfade|slide_left|slide_right|wipe"
      + "|snow|rain|sakura|fireflies|dust|leaves"
      + "|true|false|on|off|yes|no"
      + "|goto|loop|neutral)\\b";
    private static final String NUMBER_PATTERN = "-?\\b\\d+(?:\\.\\d+)?\\b";
    private static final String PUNCT_PATTERN = "[\\[\\]()>:,=]";

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "(?<COMMENT>"    + COMMENT_PATTERN    + ")"
      + "|(?<STRING>"    + STRING_PATTERN     + ")"
      + "|(?<FORMAT>"    + FORMAT_PATTERN     + ")"
      + "|(?<DIRECTIVE>" + DIRECTIVE_PATTERN  + ")"
      + "|(?<CMDOPEN>"   + CMD_OPEN_PATTERN   + ")"
      + "|(?<ARROW>"     + ARROW_PATTERN      + ")"
      + "|(?<SPEAKER>"   + SPEAKER_PATTERN    + ")"
      + "|(?<CHOICEMK>"  + CHOICE_MARK_PATTERN + ")"
      + "|(?<TIMELINE>"  + TIMELINE_PATTERN   + ")"
      + "|(?<VALUEKW>"   + VALUE_PATTERN      + ")"
      + "|(?<NUMBER>"    + NUMBER_PATTERN     + ")"
      + "|(?<PUNCT>"     + PUNCT_PATTERN      + ")"
    );

    static String style(Matcher matcher) {
      if (matcher.group("COMMENT") != null) return "comment";
      if (matcher.group("STRING") != null) return "string";
      if (matcher.group("FORMAT") != null) return "vns-format";
      if (matcher.group("DIRECTIVE") != null) return "vns-directive";
      if (matcher.group("CMDOPEN") != null) return "vns-command";
      if (matcher.group("ARROW") != null) return "vns-arrow";
      if (matcher.group("SPEAKER") != null) return "vns-speaker";
      if (matcher.group("CHOICEMK") != null) return "vns-choice";
      if (matcher.group("TIMELINE") != null) return "vns-command";
      if (matcher.group("VALUEKW") != null) return "vns-value";
      if (matcher.group("NUMBER") != null) return "number";
      if (matcher.group("PUNCT") != null) return "punct";
      throw new IllegalStateException("Unclassified syntax token");
    }
  }

  private static final class Jes {
    // ── Structural keywords (purple bold) ──
    private static final String STRUCT_PATTERN = "\\b(?:scene|entity|component|tileset|map|layer|item|on|timeline)\\b";

    // ── Component types (gold) ──
    private static final String COMPTYPE_PATTERN = "\\b(?:Panel2D|Sprite2D|Label2D|ParticleEmitter2D|PhysicsBody2D"
      + "|Character2D|Stats|Inventory|Equipment|Ai2D|Button2D|Slider2D)\\b";

    // ── Timeline actions (blue) ──
    private static final String ACTION_PATTERN = "\\b(?:move|depth|pivot|rotate|scale|fade|visible|expression|show|hide|replace|event|property|walkToTile|wait|call"
      + "|cameraMove|cameraZoom|cameraShake|cameraFollow|damage|heal|waitForCall"
      + "|playAudio|stopAudio|emitParticles|setParallax|loop|parallel|label|jump)\\b";

    // ── Built-in functions (cyan) ──
    private static final String BUILTIN_PATTERN = "\\b(?:rgb|rgba)\\b";

    // ── Boolean literals (orange) ──
    private static final String BOOL_PATTERN = "\\b(?:true|false)\\b";

    // ── Binding sub-keywords ──
    private static final String SUBKW_PATTERN = "\\b(?:key|do)\\b";

    // ── Common value keywords ──
    private static final String VALUEKW_PATTERN = "\\b(?:shape|circle|box|static|sensor|additive|left|center|right)\\b";

    private static final String PAREN_PATTERN = "[(){}]";
    private static final String COLON_COMMA_PATTERN = "[:,]";
    private static final String STRING_PATTERN = "\"([^\\\\\"]|\\\\.)*\"";
    private static final String NUMBER_PATTERN = "-?\\b\\d+(?:\\.\\d+)?\\b";
    private static final String COMMENT_PATTERN = "//[^\\n]*";

    private static final Pattern PATTERN = Pattern.compile(
        "(?<COMMENT>"  + COMMENT_PATTERN  + ")"
      + "|(?<STRING>"  + STRING_PATTERN   + ")"
      + "|(?<BUILTIN>" + BUILTIN_PATTERN  + ")"
      + "|(?<STRUCT>"  + STRUCT_PATTERN   + ")"
      + "|(?<COMPTYPE>"+ COMPTYPE_PATTERN + ")"
      + "|(?<ACTION>"  + ACTION_PATTERN   + ")"
      + "|(?<BOOL>"    + BOOL_PATTERN     + ")"
      + "|(?<SUBKW>"   + SUBKW_PATTERN   + ")"
      + "|(?<VALUEKW>" + VALUEKW_PATTERN  + ")"
      + "|(?<NUMBER>"  + NUMBER_PATTERN   + ")"
      + "|(?<PAREN>"   + PAREN_PATTERN    + ")"
      + "|(?<PUNCT>"   + COLON_COMMA_PATTERN + ")"
    );

    static String style(Matcher matcher) {
      if (matcher.group("COMMENT") != null) return "comment";
      if (matcher.group("STRING") != null) return "string";
      if (matcher.group("BUILTIN") != null) return "jes-builtin";
      if (matcher.group("STRUCT") != null) return "jes-struct";
      if (matcher.group("COMPTYPE") != null) return "jes-comptype";
      if (matcher.group("ACTION") != null) return "jes-action";
      if (matcher.group("BOOL") != null) return "jes-bool";
      if (matcher.group("SUBKW") != null) return "keyword";
      if (matcher.group("VALUEKW") != null) return "jes-bool";
      if (matcher.group("NUMBER") != null) return "number";
      if (matcher.group("PAREN") != null) return "punct";
      if (matcher.group("PUNCT") != null) return "punct";
      throw new IllegalStateException("Unclassified syntax token");
    }
  }

  private static final class PropertiesDsl {
    // Boolean values
    private static final String BOOL_PAT = "\\b(?:true|false)\\b";

    // Menu action types
    private static final String[] ACTION_KEYWORDS = {
        "new_game", "load_menu", "save_menu", "settings_menu", "quit",
        "back", "main_menu", "open_menu", "run_script", "noop", "none", "no_op"
    };
    private static final String ACTION_PAT = "\\b(?:" + String.join("|", ACTION_KEYWORDS) + ")\\b";

    // Recognized DSL value keywords (alignment, weight, etc.)
    private static final String[] VALUE_KEYWORDS = {
        "default", "left", "center", "right", "normal", "bold", "italic"
    };
    private static final String KEYWORD_PAT = "\\b(?:" + String.join("|", VALUE_KEYWORDS) + ")\\b";

    // Section prefixes: item.<id>., button.<id>., textBoxButton.<id>.
    private static final String SECTION_PAT =
        "(?:item|button|textBoxButton)\\.[\\w]+\\.";

    // Hex colour literals (#rgb, #rrggbb, #rrggbbaa)
    private static final String COLOR_PAT = "#[0-9a-fA-F]{3,8}\\b";

    private static final String COMMENT_PAT  = "(?m)#(?![0-9a-fA-F]{3,8}\\b)[^\\n]*";
    private static final String STRING_PAT   = "\"([^\\\\\"]|\\\\.)*\"";
    private static final String NUMBER_PAT   = "(?<![\\w.])\\d+(?:\\.\\d+)?(?![\\w.])";
    private static final String PROPKEY_PAT  = "(?m)^[ \\t]*[\\w](?:[\\w.\\-])*(?=\\s*=)";
    private static final String PUNCT_PAT    = "[=,.:;]";

    private static final Pattern PROPERTIES_PATTERN = Pattern.compile(
        "(?<COMMENT>"  + COMMENT_PAT  + ")"
      + "|(?<STRING>"  + STRING_PAT   + ")"
      + "|(?<PROPKEY>" + PROPKEY_PAT  + ")"
      + "|(?<SECTION>" + SECTION_PAT  + ")"
      + "|(?<COLOR>"   + COLOR_PAT    + ")"
      + "|(?<BOOL>"    + BOOL_PAT     + ")"
      + "|(?<ACTION>"  + ACTION_PAT   + ")"
      + "|(?<NUMBER>"  + NUMBER_PAT   + ")"
      + "|(?<KEYWORD>" + KEYWORD_PAT  + ")"
      + "|(?<PUNCT>"   + PUNCT_PAT    + ")"
    );

    static String style(Matcher matcher) {
      if (matcher.group("COMMENT") != null) return "comment";
      if (matcher.group("STRING") != null) return "string";
      if (matcher.group("PROPKEY") != null) return "prop-key";
      if (matcher.group("SECTION") != null) return "dsl-section";
      if (matcher.group("COLOR") != null) return "dsl-color";
      if (matcher.group("BOOL") != null) return "dsl-bool";
      if (matcher.group("ACTION") != null) return "dsl-action";
      if (matcher.group("NUMBER") != null) return "number";
      if (matcher.group("KEYWORD") != null) return "keyword";
      if (matcher.group("PUNCT") != null) return "punct";
      throw new IllegalStateException("Unclassified syntax token");
    }
  }
}
