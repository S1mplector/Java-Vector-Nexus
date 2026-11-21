package com.jvn.core.script;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SyntaxRulesTest {
  @Test void speechKeepsProsePlainAndHighlightsMarkupAndSubstitutions() {
    String source = "  ginger: left #3 is {wave}yours{/wave}, ${score}.\r\n[show ginger pos=left]\n# note";
    String[] styles = scan(SyntaxRules.VNS, source);
    assertEquals("vns-speaker", styles[source.indexOf("ginger")]);
    assertEquals("", styles[source.indexOf("left")]);
    assertEquals("", styles[source.indexOf("#3")]);
    assertEquals("vns-format", styles[source.indexOf("{wave}")]);
    assertEquals("vns-variable", styles[source.indexOf("$")]);
    assertEquals("vns-option", styles[source.indexOf("pos=")]);
    assertEquals("vns-value", styles[source.lastIndexOf("left")]);
    assertEquals("comment", styles[source.indexOf("# note")]);
  }

  @Test void jesPropertyStyleRespectsStringsAndComments() {
    String source = "component PhysicsBody2D { static: true }\n// shape: box\ntext: \"shape:\nstatic:\"";
    String[] styles = scan(SyntaxRules.JES, source);
    assertEquals("jes-prop", styles[source.indexOf("static")]);
    assertEquals("jes-bool", styles[source.indexOf("true")]);
    assertEquals("comment", styles[source.indexOf("shape")]);
    assertEquals("string", styles[source.lastIndexOf("shape")]);
    assertEquals("string", styles[source.lastIndexOf("static")]);
  }

  @Test void unfinishedVnsStringDoesNotConsumeFollowingCommands() {
    String source = "@character ginger \"" + "Long name ".repeat(1500) + "\n[end]";
    assertEquals("vns-command", scan(SyntaxRules.VNS, source)[source.indexOf("[end")]);
  }

  private static String[] scan(SyntaxRules syntax, String source) {
    String[] styles = new String[source.length()];
    int[] last = {0};
    syntax.scan(source, (start, end, style) -> {
      assertEquals(last[0], start);
      java.util.Arrays.fill(styles, start, end, style);
      last[0] = end;
    });
    assertEquals(styles.length, last[0]);
    return styles;
  }
}
