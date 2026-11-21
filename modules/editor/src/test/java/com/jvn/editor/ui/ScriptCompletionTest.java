package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScriptCompletionTest {
  private static CodeAutoCompleter.Context context(String input, Path root) {
    int caret = input.indexOf('|');
    String text = input.replace("|", "");
    return new CodeAutoCompleter.Context(text, caret, CodeAutoCompleter.currentPrefix(text, caret),
        root == null ? null : root.toFile());
  }

  private static List<String> vns(String input) {
    return VnsCodeEditor.provideSuggestions(context(input, null), List.of()).stream().map(s -> s.insert).toList();
  }

  private static List<String> jes(String input, Path root) {
    return new JesCompletion().suggest(context(input, root), List.of("hero"), List.of("again"), false)
        .stream().map(s -> s.insert).toList();
  }

  @Test void vnsNarrowsSuggestionsToTheCommandAndActor() {
    String declarations = "@character ginger \"Ginger\"\n@charimg ginger wink face.png\n@charimg john angry other.png\n";
    assertEquals(List.of("@background "), vns("@back|"));
    assertEquals(List.of("[bgm_stop]"), vns("[bgm_st|]"));
    assertEquals(List.of("ginger"), vns(declarations + "[show gi|]"));
    assertEquals(List.of("expr=wink"), vns(declarations + "[show ginger expr=|]"));
    assertEquals(List.of("ease=linear"), vns("[move ginger ease=li|]"));
    assertEquals(List.of("home"), vns("@label home\n> Go back -> ho|"));
    assertTrue(vns("@label home\nginger: [jump ho|").isEmpty());
    assertTrue(vns("# [show ginger |").isEmpty());
  }

  @Test void vnsLoadsNestedIncludeDeclarationsWhileAnalyzingTheOpenFile(@TempDir Path root) throws Exception {
    Path scripts = Files.createDirectories(root.resolve("scripts/lessons"));
    Files.writeString(scripts.resolve("cast.vns"), "@include poses.vns\n@character guide \"Guide\"\n");
    Files.writeString(scripts.resolve("poses.vns"), "@charimg guide curious face.png\n");
    String source = "@include cast.vns\n[show guide expr=cu";
    var included = new java.util.ArrayList<VnsCodeEditor.CompletionDeclaration>();
    VnsScriptAnalyzer.analyze(source, root.toFile(), scripts.resolve("intro.vns").toFile(), 30,
        text -> VnsCodeEditor.readDeclarations(text, included));
    var matches = VnsCodeEditor.provideSuggestions(context(source + "|", root), included);
    assertEquals(List.of("expr=curious"), matches.stream().map(s -> s.insert).toList());
  }

  @Test void jesReadsTheInnermostBlockAndQuotedArguments(@TempDir Path root) throws Exception {
    assertEquals(List.of("Sprite2D"), jes("scene \"a\" { entity \"b\" { component spr|", null));
    assertEquals(List.of("originX: ", "originY: "), jes("scene \"a\" { entity \"b\" { component Sprite2D { // }\nori|", null));
    assertEquals(List.of("cameraMove"), jes("timeline { cameram|", null));
    assertEquals(List.of("hero"), jes("timeline { move \"he|\"", null));
    assertTrue(jes("scene \"text { entity\" { // ent|", null).isEmpty());
    assertTrue(jes("scene \"text { entity\" { //c|", null).isEmpty());
    Path image = root.resolve("assets/portraits/New Pose.png");
    Files.createDirectories(image.getParent());
    Files.createFile(image);
    assertEquals(List.of("assets/portraits/New Pose.png"), jes("component Sprite2D { image: \"New P|\"", root));
    assertTrue(jes("component Label2D { text: \"New P|\"", root).isEmpty());
  }

  @Test void midTokenReplacementKeepsTheExistingDelimiterAndValue() {
    assertEquals("[bgm_stop]", apply("[bgm_st|op]", "[bgm_stop]"));
    assertEquals("[show ginger]", apply("[sh|ow ginger]", "[show "));
    assertEquals("[move ginger ease=linear]", apply("[move ginger ease=li|near]", "ease=linear"));
    assertEquals("[move ginger pos=left]", apply("[move ginger po|s=left]", "pos="));
    assertEquals("[move ginger pos=left]", apply("[move ginger pos=|left]", "pos="));
    assertEquals("originX: 0", apply("ori|ginY: 0", "originX: "));
    assertEquals("image: \"assets/New Pose.png\"", apply("image: \"old| name.png\"", "assets/New Pose.png"));
  }

  private static String apply(String input, String insert) {
    var ctx = context(input, null);
    return ctx.text.substring(0, ctx.caret - ctx.prefix.length()) + insert
        + ctx.text.substring(CodeAutoCompleter.replacementEnd(ctx.text, ctx.caret, insert));
  }
}
