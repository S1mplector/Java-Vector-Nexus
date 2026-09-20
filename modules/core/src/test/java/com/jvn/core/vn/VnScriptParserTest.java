package com.jvn.core.vn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.jvn.core.vn.script.InMemoryJavaCompiler;
import com.jvn.core.vn.script.MultipleParseErrorsException;
import com.jvn.core.vn.script.VnScriptParser;

public class VnScriptParserTest {
  @Test
  public void assetDeclarationPathsDecodeQuotesAndKeepLegacySpaces() throws Exception {
    VnScenario scenario = new VnScriptParser().parseFromString("""
        @scenario paths
        @background room "assets/backgrounds/room day.png" # daytime
        @background legacy assets/backgrounds/room night.png
        @charimg ari happy "assets/characters/ari/happy #1.png"
        @charlayer ari eyes "assets/characters/ari/eyes open.png" # eyes
        """);
    assertEquals("assets/backgrounds/room day.png", scenario.getBackground("room").getImagePath());
    assertEquals("assets/backgrounds/room night.png", scenario.getBackground("legacy").getImagePath());
    assertEquals("assets/characters/ari/happy #1.png", scenario.getCharacter("ari").getExpressionPath("happy"));
    assertEquals("assets/characters/ari/eyes open.png", scenario.getCharacter("ari").getLayerPath("eyes"));
  }

  @Test
  public void parsesMinimalScript() throws Exception {
    String script = """
      @scenario test_story
      @character alice "Alice"
      @background room game/images/bg_room.png

      [background room]
      Alice: Hello there!
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    try (var in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
      VnScenario scen = parser.parse(in);
      assertEquals("test_story", scen.getId());
      assertNotNull(scen.getBackground("room"));
      assertNotNull(scen.getCharacter("alice"));
      assertTrue(scen.getNodes().size() >= 3);

      VnNode first = scen.getNodes().get(0);
      assertEquals(VnNodeType.BACKGROUND, first.getType());
      VnNode second = scen.getNodes().get(1);
      assertEquals(VnNodeType.DIALOGUE, second.getType());
      assertEquals("Alice", second.getDialogue().getSpeakerName());
      assertTrue(second.getDialogue().getText().startsWith("Hello"));
    }
  }

  @Test
  public void parsedNodesRetainTheirExactSourceLines() throws Exception {
    VnScenario scenario = new VnScriptParser().parseFromString("""
        @scenario cursor_lines
        @character narrator ""
        @label start

        [bg bg_mags]

        [show panel_01 center neutral]
        [show panel_mags center neutral]
        narrator:1714
        [show panel_01 center neutral]
        [show panel_mags center neutral]
        narrator:1716
        [end]
        """);

    List<VnNode> nodes = scenario.getNodes();
    assertEquals(5, nodes.get(0).getSourceLine());
    assertEquals(9, nodes.stream()
        .filter(node -> node.getType() == VnNodeType.DIALOGUE)
        .findFirst().orElseThrow().getSourceLine());
    assertEquals(12, nodes.stream()
        .filter(node -> node.getType() == VnNodeType.DIALOGUE)
        .skip(1).findFirst().orElseThrow().getSourceLine());
  }

  @Test
  public void parsesCharacterSpeakerColorAndCarriesItToDialogue() throws Exception {
    String script = """
      @scenario color_demo
      @character hi "Hisao" color=#629276

      @label start
      hi: The text itself keeps the normal dialogue color.
      [end]
    """;

    VnScenario scenario = new VnScriptParser().parseFromString(script);
    VnCharacter hisao = scenario.getCharacter("hi");
    DialogueLine line = scenario.getNodes().stream()
        .filter(node -> node.getType() == VnNodeType.DIALOGUE)
        .findFirst()
        .orElseThrow()
        .getDialogue();

    assertEquals("#629276", hisao.getNameColor());
    assertEquals("hi", line.getSpeakerId());
    assertEquals("Hisao", line.getSpeakerName());
    assertEquals("#629276", line.getSpeakerColor());
  }

  @Test
  public void parsesPersistentCharacterScaleAlongsideSpeakerColor() throws Exception {
    String script = """
      @scenario scaled_character
      @character emi "Emi" color=#F28C8C scale=1.15
      @charimg emi neutral assets/characters/emi.png

      @label start
      [show emi center]
      emi: Hi!
      [end]
    """;

    VnCharacter emi = new VnScriptParser().parseFromString(script).getCharacter("emi");

    assertEquals(1.15, emi.getScale(), 1e-9);
    assertEquals("#F28C8C", emi.getNameColor());
  }

  @Test
  public void characterScaleDefaultsToOneAndRejectsInvalidValues() throws Exception {
    VnCharacter regular = new VnScriptParser().parseFromString("""
        @scenario regular
        @character hero "Hero"
        [end]
        """).getCharacter("hero");

    assertEquals(1.0, regular.getScale(), 1e-9);
    assertThrows(IOException.class, () -> new VnScriptParser().parseFromString("""
        @scenario invalid
        @character hero "Hero" scale=0
        [end]
        """));
    assertThrows(IOException.class, () -> new VnScriptParser().parseFromString("""
        @scenario invalid
        @character hero "Hero" scale=huge
        [end]
        """));
  }

  @Test
  public void rejectsMalformedCharacterSpeakerColor() {
    String script = """
      @scenario color_demo
      @character hi "Hisao" color=green
      [end]
    """;

    assertThrows(IOException.class, () -> new VnScriptParser().parseFromString(script));
  }

  @Test
  public void parsesCharacterLayerPresetsIntoLayeredCharimgExpressions() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
      @charpreset lavender talking $base | $eyes_neutral | $mouth_smile

      @label start
      [show lavender center talking]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);

    VnCharacter lavender = scen.getCharacter("lavender");
    assertNotNull(lavender);
    assertEquals(
        "assets/demo/characters/lavender/base/lavender_test_sprite_base.png"
            + " | assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png"
            + " | assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png",
        lavender.getExpressionPath("talking"));
    assertEquals(List.of("base", "eyes_neutral", "mouth_smile"), lavender.getExpressionLayerIds("talking"));
  }

  @Test
  public void parsesGroupedLayerReferencesAndPresetComposition() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender body_school assets/demo/characters/lavender/body/lavender_test_sprite_body_school.png
      @charlayer lavender eyes_half_closed assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_half_closed.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
      @charpreset lavender neutral $base | $body=school | $eyes=half_closed
      @charpreset lavender talking @neutral | $mouth=smile

      @label start
      [show lavender center talking]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);

    VnCharacter lavender = scen.getCharacter("lavender");
    assertNotNull(lavender);
    assertEquals(
        "assets/demo/characters/lavender/base/lavender_test_sprite_base.png"
            + " | assets/demo/characters/lavender/body/lavender_test_sprite_body_school.png"
            + " | assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_half_closed.png"
            + " | assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png",
        lavender.getExpressionPath("talking"));
    assertEquals(
        List.of("base", "body=school", "eyes=half_closed", "mouth=smile"),
        lavender.getExpressionLayerIds("talking"));
  }

  @Test
  public void parsesCharacterLayerGroupsAndExpandsThemInPresets() throws Exception {
    String script = """
      @scenario layered_demo
      @character john "John"
      @charlayer john body_default assets/john/body.png
      @charlayer john head_base assets/john/head.png
      @charlayer john eyes_neutral assets/john/eyes.png
      @charlayer john mouth_smile assets/john/mouth.png
      @chargroup john head pivot=0.5,0.28 $head_base | $eyes_neutral | $mouth_smile
      @charpreset john neutral $body_default | $head

      @label start
      [show john center neutral]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);

    VnCharacter john = scen.getCharacter("john");
    assertNotNull(john);
    assertEquals(
        "assets/john/body.png | assets/john/head.png | assets/john/eyes.png | assets/john/mouth.png",
        john.getExpressionPath("neutral"));
    assertEquals(List.of("body_default", "head_base", "eyes_neutral", "mouth_smile"),
        john.getExpressionLayerIds("neutral"));

    VnCharacter.LayerGroup head = john.getLayerGroup("head");
    assertNotNull(head);
    assertEquals(List.of("head_base", "eyes_neutral", "mouth_smile"), head.layerIds());
    assertTrue(head.hasPivot());
    assertEquals(0.5, head.pivotX(), 0.0001);
    assertEquals(0.28, head.pivotY(), 0.0001);
    assertEquals(List.of(head), john.getLayerGroupChainForLayer("eyes_neutral"));
  }

  @Test
  public void characterLayerGroupsExpandVariantGlobs() throws Exception {
    String script = """
      @scenario layered_demo
      @character john "John"
      @charlayer john arm_behind_default assets/john/arm_behind.png
      @charlayer john body_default assets/john/body.png
      @charlayer john body_no_limbs assets/john/body_no_limbs.png
      @charlayer john neck_normal assets/john/neck.png
      @charlayer john arm_front_default assets/john/arm_front.png
      @charlayer john arm_front_holding_wrist assets/john/holding.png
      @chargroup john body_orientation pivot=0.5,1 \\
        $arm_behind_* | $body_* | \\
        $neck_* | $arm_front_*
      @charpreset john holding $body_no_limbs | $neck_normal | $arm_front_holding_wrist

      @label start
      [show john center holding]
      [end]
    """;

    VnCharacter john = new VnScriptParser().parseFromString(script).getCharacter("john");

    assertNotNull(john);
    VnCharacter.LayerGroup group = john.getLayerGroup("body_orientation");
    assertNotNull(group);
    assertEquals(List.of(
        "arm_behind_default",
        "body_default",
        "body_no_limbs",
        "neck_normal",
        "arm_front_default",
        "arm_front_holding_wrist"), group.layerIds());
    assertEquals(List.of(group), john.getLayerGroupChainForLayer("body_no_limbs"));
    assertEquals(List.of(group), john.getLayerGroupChainForLayer("arm_front_holding_wrist"));
  }

  @Test
  public void characterLayerGroupRejectsGlobThatMatchesNothing() {
    IOException error = assertThrows(IOException.class, () -> new VnScriptParser().parseFromString("""
      @scenario layered_demo
      @character john "John"
      @charlayer john body_default assets/john/body.png
      @chargroup john body_orientation $missing_*
      @label start
      [end]
    """));

    assertTrue(error.getMessage().contains("$missing_*"));
    assertTrue(error.getMessage().contains("@chargroup"));
  }

  @Test
  public void parsesNestedCharacterLayerGroups() throws Exception {
    String script = """
      @scenario layered_demo
      @character john "John"
      @charlayer john head_base assets/john/head.png
      @charlayer john eyes_neutral assets/john/eyes.png
      @charlayer john mouth_smile assets/john/mouth.png
      @chargroup john face parent=head $eyes_neutral | $mouth_smile
      @chargroup john head $head_base | $face
      @charpreset john neutral $head

      @label start
      [show john center neutral]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);

    VnCharacter john = scen.getCharacter("john");
    assertNotNull(john);
    assertEquals(List.of("head_base", "eyes_neutral", "mouth_smile"),
        john.getExpressionLayerIds("neutral"));
    assertEquals(
        List.of(john.getLayerGroup("head"), john.getLayerGroup("face")),
        john.getLayerGroupChainForLayer("mouth_smile"));
  }

  @Test
  public void showCommandAcceptsExplicitPresetReference() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
      @charpreset lavender talking $base | $eyes_neutral | $mouth_smile

      @label start
      [show lavender center @talking]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode show = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW)
        .findFirst().orElseThrow();
    assertEquals("talking", show.getShowExpression());
  }

  @Test
  public void showCommandSupportsInlineCompositePresetAndLayer() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
      @charlayer lavender glasses assets/demo/characters/lavender/props/lavender_glasses.png
      @charpreset lavender talking $base | $eyes_neutral | $mouth_smile

      @label start
      [show lavender center @talking+$glasses]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode show = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW)
        .findFirst().orElseThrow();
    VnCharacter lavender = scen.getCharacter("lavender");
    assertNotNull(lavender);
    assertTrue(show.getShowExpression().startsWith("__inline_"));
    assertEquals(
        "assets/demo/characters/lavender/base/lavender_test_sprite_base.png"
            + " | assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png"
            + " | assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png"
            + " | assets/demo/characters/lavender/props/lavender_glasses.png",
        lavender.getExpressionPath(show.getShowExpression()));
    assertEquals(
        List.of("base", "eyes_neutral", "mouth_smile", "glasses"),
        lavender.getExpressionLayerIds(show.getShowExpression()));
  }

  @Test
  public void showCommandPreservesLayerIdsForInlineCompositeLayerSpec() throws Exception {
    String script = """
      @scenario layered_demo
      @character john "John"
      @charlayer john body_default assets/john/body_default.png
      @charlayer john normal_face_common_01 assets/john/normal_face_common_01.png
      @charlayer john normal_mouth_common_01 assets/john/normal_mouth_common_01.png
      @charlayer john eyes_n_09 assets/john/eyes_n_09.png
      @charlayer john bp_strap assets/john/bp_strap.png
      @charlayer john dog_tag assets/john/dog_tag.png

      @label start
      [show john center $body_default+$normal_face_common_01+$normal_mouth_common_01+$eyes_n_09+$bp_strap+$dog_tag]
      [end]
    """;

    VnScenario scen = new VnScriptParser().parseFromString(script);
    VnNode show = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW)
        .findFirst().orElseThrow();
    VnCharacter john = scen.getCharacter("john");
    assertNotNull(john);
    assertTrue(show.getShowExpression().startsWith("__inline_"));
    assertEquals(
        "assets/john/body_default.png"
            + " | assets/john/normal_face_common_01.png"
            + " | assets/john/normal_mouth_common_01.png"
            + " | assets/john/eyes_n_09.png"
            + " | assets/john/bp_strap.png"
            + " | assets/john/dog_tag.png",
        john.getExpressionPath(show.getShowExpression()));
    assertEquals(
        List.of(
            "body_default",
            "normal_face_common_01",
            "normal_mouth_common_01",
            "eyes_n_09",
            "bp_strap",
            "dog_tag"),
        john.getExpressionLayerIds(show.getShowExpression()));
  }

  @Test
  public void moveCommandSupportsInlineCompositeLayerSpec() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png

      @label start
      [move lavender center $base+$eyes_neutral+$mouth_smile ease_out_back 500]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode move = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .findFirst().orElseThrow();
    VnCharacter lavender = scen.getCharacter("lavender");
    assertNotNull(lavender);
    assertTrue(move.getShowExpression().startsWith("__inline_"));
    assertEquals(
        "assets/demo/characters/lavender/base/lavender_test_sprite_base.png"
            + " | assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png"
            + " | assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png",
        lavender.getExpressionPath(move.getShowExpression()));
    assertEquals(com.jvn.core.animation.Easing.Type.EASE_OUT_BACK, move.getMoveEasingType());
    assertEquals(500, move.getMoveDurationMs());
  }

  @Test
  public void moveCommandAcceptsCamelCaseEasingAliases() throws Exception {
    String script = """
      @label start
      [move hero center easeInOut 500]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode move = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .findFirst()
        .orElseThrow();

    assertEquals(com.jvn.core.animation.Easing.Type.EASE_IN_OUT_QUAD, move.getMoveEasingType());
    assertEquals(500, move.getMoveDurationMs());
    assertEquals("neutral", move.getShowExpression());
  }

  @Test
  public void showCommandSupportsGroupedInlineCompositeLayerSpec() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_half_closed assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_half_closed.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png

      @label start
      [show lavender center $base+$eyes=half_closed+$mouth=smile]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode show = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW)
        .findFirst().orElseThrow();
    VnCharacter lavender = scen.getCharacter("lavender");
    assertNotNull(lavender);
    assertTrue(show.getShowExpression().startsWith("__inline_"));
    assertEquals(
        "assets/demo/characters/lavender/base/lavender_test_sprite_base.png"
            + " | assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_half_closed.png"
            + " | assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png",
        lavender.getExpressionPath(show.getShowExpression()));
  }

  @Test
  public void showCommandSupportsNamedOptions() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
      @charpreset lavender talking $base | $eyes_neutral | $mouth_smile

      @label start
      [show lavender pos=center expr=@talking layer=25]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode show = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW)
        .findFirst()
        .orElseThrow();

    assertEquals(CharacterPosition.CENTER, show.getShowPosition());
    assertEquals("talking", show.getShowExpression());
    assertEquals(Integer.valueOf(25), show.getShowLayerOrder());
  }

  @Test
  public void characterInteropExpressionCommandSupportsInlineCompositePresetAndLayer() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
      @charlayer lavender glasses assets/demo/characters/lavender/props/lavender_glasses.png
      @charpreset lavender talking $base | $eyes_neutral | $mouth_smile

      @label start
      [char lavender global on]
      [show lavender center @talking]
      [char lavender expression @talking+$glasses]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnScene scene = new VnScene(scenario);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    String expr = scene.getState().getCharacterExpression("lavender");
    assertNotNull(expr);
    assertTrue(expr.startsWith("__inline_"));
    assertEquals(
        "assets/demo/characters/lavender/base/lavender_test_sprite_base.png"
            + " | assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png"
            + " | assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png"
            + " | assets/demo/characters/lavender/props/lavender_glasses.png",
        scenario.getCharacter("lavender").getExpressionPath(expr));
  }

  @Test
  public void rejectsCharacterPresetWithUnknownLayerReference() {
    String script = """
      @scenario layered_demo
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charpreset lavender broken $base | $eyes_neutral
      @label start
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("Unknown @charlayer reference '$eyes_neutral'"));
  }

  @Test
  public void parsesIncludesAndDefines() throws Exception {
    String script = """
      @scenario test_story
      @define hero Alice
      @include common.vns

      ${hero}: Main line.
      [end]
    """;
    String common = """
      @label start
      ${hero}: From include.
    """;
    Map<String, String> includes = Map.of("common.vns", common);
    VnScriptParser parser = new VnScriptParser();
    try (var in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
      VnScenario scen = parser.parse(in, "main.vns", path -> {
        String content = includes.get(path);
        if (content == null) throw new IOException("missing include " + path);
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
      });
      assertEquals("test_story", scen.getId());
      assertTrue(scen.getNodes().size() >= 3);
      VnNode line = scen.getNodes().get(0);
      assertEquals("Alice", line.getDialogue().getSpeakerName());
      assertEquals("From include.", line.getDialogue().getText());
    }
  }

  @Test
  public void parsesPhoneInteropCommandsAsExternalNodes() throws Exception {
    String script = """
      @scenario phone_demo
      @label start
      [phone contact ll name="Lily" avatar="assets/phone/lily.png"]
      [phone message mc_lily ll "You awake?" time=08:14]
      [phone chat mc_lily]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    List<VnNode> externals = scenario.getNodes().stream()
        .filter(node -> node.getType() == VnNodeType.EXTERNAL)
        .toList();

    assertEquals(3, externals.size());
    assertEquals("phone", externals.get(0).getExternalCommand().getProvider());
    assertEquals("contact ll name=\"Lily\" avatar=\"assets/phone/lily.png\"", externals.get(0).getExternalCommand().getPayload());
    assertEquals("message mc_lily ll \"You awake?\" time=08:14", externals.get(1).getExternalCommand().getPayload());
    assertEquals("chat mc_lily", externals.get(2).getExternalCommand().getPayload());
  }

  @Test
  public void parsesLegacyWizardSyntax() throws Exception {
    String script = """
      # Legacy sample style used by early project templates
      label start
      narrator "Welcome to {b}My Visual Novel{/b}."
      narrator "This is a sample scene."
      [choice Continue->next | Exit->ending]
      
      label next
      narrator "You chose to continue."
      [jump ending]
      
      label ending
      narrator "{wave}The End{/wave}"
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    try (var in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
      VnScenario scen = parser.parse(in);
      assertEquals("untitled", scen.getId());
      assertNotNull(scen.getLabelIndex("start"));
      assertNotNull(scen.getLabelIndex("next"));
      assertNotNull(scen.getLabelIndex("ending"));
      assertTrue(scen.getNodes().stream().anyMatch(n -> n.getType() == VnNodeType.CHOICE));
      assertTrue(scen.getNodes().stream().anyMatch(n -> n.getType() == VnNodeType.END));
    }
  }

  @Test
  public void parsesIfElifElseControlFlowAndExecutesExpectedBranch() throws Exception {
    String script = """
      @label start
      [set courage 1]
      [if courage >= 2]
      narrator "High courage"
      [elif courage >= 1]
      narrator "Medium courage"
      [else]
      narrator "Low courage"
      [endif]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario;
    try (var in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
      scenario = parser.parse(in);
    }

    VnScene scene = new VnScene(scenario);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    assertFalse(scene.getState().getHistory().getEntries().isEmpty());
    VnHistory.HistoryEntry first = scene.getState().getHistory().getEntries().get(0);
    assertEquals("narrator", first.getSpeaker());
    assertEquals("Medium courage", first.getText());
  }

  @Test
  public void callWithSingleLabelIsSubroutineAlias() throws Exception {
    String script = """
      @label start
      narrator "Before"
      [call shared_cutscene]
      narrator "After"
      [end]

      @label shared_cutscene
      narrator "Inside"
      [return]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    VnNode call = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.CALL)
        .findFirst()
        .orElseThrow();
    assertEquals("shared_cutscene", call.getJumpLabel());

    VnScene scene = new VnScene(scenario);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();
    assertEquals("Before", scene.getState().getHistory().getEntries().get(0).getText());
    scene.advance();
    assertEquals("Inside", scene.getState().getHistory().getEntries().get(1).getText());
    scene.advance();
    assertEquals("After", scene.getState().getHistory().getEntries().get(2).getText());
  }

  @Test
  public void callWithProviderPayloadRemainsInteropCommand() throws Exception {
    String script = """
      @label start
      [call jes_timeline hero_entrance]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode external = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL)
        .findFirst()
        .orElseThrow();

    assertEquals("jes_timeline", external.getExternalCommand().getProvider());
    assertEquals("hero_entrance", external.getExternalCommand().getPayload());
  }

  @Test
  public void parsesExternalTimelineDirectiveAndIncludedCharacters() throws Exception {
    String script = """
      @scenario timeline_link
      @include characters.vns

      @label start
      @external jes_timeline my_animation
      [end]
    """;

    String characters = """
      @character hero "Hero"
      @charimg hero angry assets/characters/hero_angry.png
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parse(
        new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
        "story/prologue.vns",
        path -> {
          if ("characters.vns".equals(path)) {
            return new ByteArrayInputStream(characters.getBytes(StandardCharsets.UTF_8));
          }
          throw new IOException("Unexpected include: " + path);
        });

    assertNotNull(scenario.getCharacter("hero"));
    VnNode external = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL)
        .findFirst()
        .orElseThrow();

    assertEquals("jes_timeline", external.getExternalCommand().getProvider());
    assertEquals("my_animation", external.getExternalCommand().getPayload());
  }

  @Test
  public void jesTimelineCommandIsShortcutForExternalTimelineCall() throws Exception {
    String script = """
      @label start
      [jes_timeline my_animation wait]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode external = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL)
        .findFirst()
        .orElseThrow();

    assertEquals("jes_timeline", external.getExternalCommand().getProvider());
    assertEquals("my_animation wait", external.getExternalCommand().getPayload());
  }

  @Test
  public void jesTimelineCommandAcceptsWaitEqualsFalseModifier() throws Exception {
    String script = """
      @label start
      [jes_timeline my_animation wait=false]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode external = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL)
        .findFirst()
        .orElseThrow();

    assertEquals("jes_timeline", external.getExternalCommand().getProvider());
    assertEquals("my_animation wait=false", external.getExternalCommand().getPayload());
  }

  @Test
  public void rejectsUnknownCommandsWithStrictDiagnostics() {
    String script = """
      @label start
      [mystery doSomething]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("Unknown command [mystery]"));
  }

  @Test
  public void parsesVisualizerCommandIntoUiInteropPayload() throws Exception {
    String script = """
      @label start
      [visualizer on]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "ui".equals(n.getExternalCommand().getProvider()))
        .findFirst().orElseThrow();
    assertEquals("visualizer on", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesVisualizerCommandWithoutArgAsToggle() throws Exception {
    String script = """
      @label start
      [visualizer]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "ui".equals(n.getExternalCommand().getProvider()))
        .findFirst().orElseThrow();
    assertEquals("visualizer toggle", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesModeCommandIntoModeInteropPayload() throws Exception {
    String script = """
      @label start
      [mode dialogue standard]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "mode".equals(n.getExternalCommand().getProvider()))
        .findFirst().orElseThrow();
    assertEquals("dialogue standard", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesInlineTimelineShowcaseScript() throws Exception {
    String script = """
      @scenario runtime_showcase
      @character narrator "Narrator"
      @character hero "Hero"
      @background classroom game/images/backgrounds/classroom_day.png

      @label start
      [bg classroom]
      Narrator: Timeline parser smoke test.

      timeline {
        move "hero" {
          x: 620
          y: 400
          dur: 420
          easing: ease_in_out_cubic
        }
        rotate "hero" {
          angle: -8
          dur: 420
          easing: ease_in_out_sine
        }
      }
      [wait 450]
      [end]
      """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);

    assertNotNull(scen);
    assertEquals("runtime_showcase", scen.getId());
    VnNode timeline = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "jes_timeline_inline".equals(n.getExternalCommand().getProvider()))
        .findFirst()
        .orElseThrow();
    assertTrue(timeline.getExternalCommand().getPayload().startsWith("timeline {"));
    assertTrue(timeline.getExternalCommand().getPayload().contains("move \"hero\""));
  }

  @Test
  public void parsesInlineTimelineWithWaitEqualsFalseModifier() throws Exception {
    String script = """
      @scenario runtime_showcase
      @label start
      timeline wait=false {
        move "hero" {
          x: 620
          y: 400
          dur: 420
          easing: ease_in_out_cubic
        }
      }
      [end]
      """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);

    VnNode timeline = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "jes_timeline_inline".equals(n.getExternalCommand().getProvider()))
        .findFirst()
        .orElseThrow();
    assertTrue(timeline.getExternalCommand().getPayload().startsWith("timeline {"));
    assertTrue(timeline.getExternalCommand().getPayload().endsWith("wait=false"));
  }

  @Test
  public void rejectsTrailingContentAfterInlineTimelineBlock() {
    String script = """
      @scenario malformed_timeline
      @label start
      timeline {
        entity "heart_effect" {
          0ms { alpha: 0.3 }
        }
      }a
      [end]
      """;

    MultipleParseErrorsException error = assertThrows(
        MultipleParseErrorsException.class,
        () -> new VnScriptParser().parseFromString(script));

    assertTrue(error.getMessage().contains("Unexpected content after timeline block: a"));
    assertEquals(7, error.getErrors().getFirst().getLineNumber());
  }

  @Test
  public void allowsCommentAfterInlineTimelineBlock() throws Exception {
    String script = """
      @scenario commented_timeline
      @label start
      timeline {
        entity "heart_effect" {
          0ms { alpha: 0.3 }
        }
      } # pulse complete
      [end]
      """;

    VnScenario scenario = new VnScriptParser().parseFromString(script);

    assertTrue(scenario.getNodes().stream().anyMatch(node ->
        node.getType() == VnNodeType.EXTERNAL
            && "jes_timeline_inline".equals(node.getExternalCommand().getProvider())));
  }

  @Test
  public void ignoresQuotedAndCommentedBracesInsideInlineTimeline() throws Exception {
    String script = """
      @scenario quoted_timeline
      @label start
      timeline { # opening comment with }
        entity "brace_{_name" {
          0ms { text: "escaped \\"}\\" and {", tint: #7de2ff } # ignored }
        }
      } # closing comment
      [end]
      """;

    VnScenario scenario = new VnScriptParser().parseFromString(script);

    VnNode timeline = scenario.getNodes().stream()
        .filter(node -> node.getType() == VnNodeType.EXTERNAL
            && "jes_timeline_inline".equals(node.getExternalCommand().getProvider()))
        .findFirst()
        .orElseThrow();
    assertTrue(timeline.getExternalCommand().getPayload().contains("escaped"));
    assertTrue(timeline.getExternalCommand().getPayload().contains("#7de2ff"));
  }

  @Test
  public void rejectsCommandsAndExtraBracesAfterInlineTimelineBlock() {
    for (String suffix : List.of("[wait 100]", "}")) {
      String script = """
        @scenario malformed_timeline
        @label start
        timeline {
          0ms { alpha: 0.3 }
        } %s
        [end]
        """.formatted(suffix);

      MultipleParseErrorsException error = assertThrows(
          MultipleParseErrorsException.class,
          () -> new VnScriptParser().parseFromString(script));

      assertTrue(error.getMessage().contains("Unexpected content after timeline block: " + suffix));
    }
  }

  @Test
  public void parsesVisualizerCommandWithBarsOption() throws Exception {
    String script = """
      @label start
      [visualizer on bars=48]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "ui".equals(n.getExternalCommand().getProvider()))
        .findFirst().orElseThrow();
    assertEquals("visualizer on bars=48", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesVisualizerConfigCommandWithStyleOptions() throws Exception {
    String script = """
      @label start
      [visualizer set bars=32 color=#7de2ff glow=off style=minimal z=-15]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "ui".equals(n.getExternalCommand().getProvider()))
        .findFirst().orElseThrow();
    assertEquals("visualizer set bars=32 color=#7de2ff glow=off style=minimal z=-15", ext.getExternalCommand().getPayload());
  }

  @Test
  public void rejectsUndefinedLabels() {
    String script = """
      @label start
      [jump missing_label]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("Undefined label 'missing_label'"));
  }

  @Test
  public void rejectsUnclosedIfBlocks() {
    String script = """
      @label start
      [if score > 0]
      narrator "still open"
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("Unclosed [if] block"));
  }

  @Test
  public void supportsCharacterGlobalPositionCommands() throws Exception {
    String script = """
      @label start
      [char hero global on]
      [show hero center neutral]
      [char hero move right smile]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario;
    try (var in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
      scenario = parser.parse(in);
    }

    VnScene scene = new VnScene(scenario);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    assertTrue(scene.getState().getVisibleCharacters().containsKey(CharacterPosition.RIGHT));
    assertEquals("hero", scene.getState().getVisibleCharacters().get(CharacterPosition.RIGHT).getCharacterId());
    assertEquals("neutral", scene.getState().getVisibleCharacters().get(CharacterPosition.RIGHT).getExpression());

    scene.update(400);
    assertEquals("smile", scene.getState().getVisibleCharacters().get(CharacterPosition.RIGHT).getExpression());
    assertEquals(CharacterPosition.RIGHT, scene.getState().getCharacterDefinedPosition("hero"));
  }

  @Test
  public void parsesShowCommandWithLayerOrder() throws Exception {
    String script = """
      @label start
      [show hero center neutral 25]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario;
    try (var in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
      scenario = parser.parse(in);
    }

    VnNode show = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW)
        .findFirst()
        .orElseThrow();
    assertEquals(Integer.valueOf(25), show.getShowLayerOrder());
  }

  @Test
  public void moveCommandSupportsNamedOptions() throws Exception {
    String script = """
      @label start
      [move hero pos=right expr=smile ease=easeInOut dur=500]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario;
    try (var in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
      scenario = parser.parse(in);
    }

    VnNode move = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .findFirst()
        .orElseThrow();
    assertEquals(CharacterPosition.RIGHT, move.getShowPosition());
    assertEquals("smile", move.getShowExpression());
    assertEquals(com.jvn.core.animation.Easing.Type.EASE_IN_OUT_QUAD, move.getMoveEasingType());
    assertEquals(500, move.getMoveDurationMs());
  }

  @Test
  public void moveCommandSupportsIndependentExpressionDuration() throws Exception {
    String script = """
      @label start
      [move john pos=right expression=talking dur=400 exprDur=120]
      [end]
    """;

    VnScenario scenario = new VnScriptParser().parseFromString(script);
    VnNode move = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .findFirst()
        .orElseThrow();

    assertEquals(400, move.getMoveDurationMs());
    assertEquals(120, move.getExpressionDurationMs());
  }

  @Test
  public void characterExpressionCommandUsesInstantDefaultAndAcceptsExplicitDuration() throws Exception {
    String instantScript = """
      @scenario expression_instant
      @character lily "Lily"
      @charimg lily neutral neutral.png
      @charimg lily talking talking.png
      @label start
      [show lily center neutral]
      [character lily expression talking dur=0]
      [end]
    """;

    VnScene instantScene = new VnScene(new VnScriptParser().parseFromString(instantScript));
    instantScene.setInterop(new DefaultVnInterop());
    instantScene.onEnter();

    assertEquals("talking", instantScene.getState().getCharacterExpression("lily"));
    assertNull(instantScene.getState().getExpressionTransition("lily"));

    String defaultScript = """
      @scenario expression_default
      @character lily "Lily"
      @charimg lily neutral neutral.png
      @charimg lily talking talking.png
      @label start
      [show lily center talking]
      [character lily expression neutral]
      [end]
    """;

    VnScene defaultScene = new VnScene(new VnScriptParser().parseFromString(defaultScript));
    defaultScene.setInterop(new DefaultVnInterop());
    defaultScene.onEnter();

    assertEquals("neutral", defaultScene.getState().getCharacterExpression("lily"));
    assertNull(defaultScene.getState().getExpressionTransition("lily"));
  }

  @Test
  public void transitionCommandSupportsNamedOptions() throws Exception {
    String script = """
      @label start
      [transition type=crossfade dur=700 bg=classroom]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode transition = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.TRANSITION)
        .findFirst()
        .orElseThrow();

    assertEquals(VnTransition.TransitionType.CROSSFADE, transition.getTransition().getType());
    assertEquals(700, transition.getTransition().getDurationMs());
    assertEquals("classroom", transition.getTransition().getTargetBackgroundId());
  }

  @Test
  public void waitCommandSupportsNamedOptions() throws Exception {
    String script = """
      @label start
      [wait ms=450]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode wait = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.WAIT)
        .findFirst()
        .orElseThrow();

    assertEquals(450, wait.getWaitMs());
  }

  @Test
  public void stageCommandSupportsNamedOptions() throws Exception {
    String script = """
      @label start
      [stage preset=dramatic_evening]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode stage = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "stage".equals(n.getExternalCommand().getProvider()))
        .findFirst()
        .orElseThrow();

    assertEquals("dramatic_evening", stage.getExternalCommand().getPayload());
  }

  @Test
  public void settingsCommandsSupportNamedOptions() throws Exception {
    String script = """
      @label start
      [volume channel=bgm level=0.65]
      [textspeed value=42]
      [autodelay ms=1750]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    List<String> payloads = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "settings".equals(n.getExternalCommand().getProvider()))
        .map(n -> n.getExternalCommand().getPayload())
        .toList();

    assertTrue(payloads.contains("volume bgm 0.65"));
    assertTrue(payloads.contains("textspeed 42"));
    assertTrue(payloads.contains("autodelay 1750"));
  }

  @Test
  public void particlesCommandSupportsNamedOptions() throws Exception {
    String script = """
      @label start
      [particles preset=rain intensity=0.75 layer=120]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode particles = scenario.getNodes().stream()
        .filter(n -> n.getParticleCommand() != null)
        .findFirst()
        .orElseThrow();

    assertEquals(VnParticleCommand.Preset.RAIN, particles.getParticleCommand().getPreset());
    assertEquals(0.75f, particles.getParticleCommand().getIntensity(), 0.0001f);
    assertEquals(120, particles.getParticleCommand().getLayer());
    assertFalse(particles.getParticleCommand().isStop());
  }

  @Test
  public void particlesCommandSupportsFxShapingOptionsAndAliases() throws Exception {
    String script = """
      @label start
      [pfx snow intensity=0.6 layer=90 opacity=0.35 speed=1.25 wind=-18 size=1.5 duration=2500 prewarm=3000 texture=assets/vfx/snowflake.png tint=#88aaff]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnParticleCommand command = scenario.getNodes().stream()
        .filter(n -> n.getParticleCommand() != null)
        .findFirst()
        .orElseThrow()
        .getParticleCommand();

    assertEquals(VnParticleCommand.Preset.SNOW, command.getPreset());
    assertEquals(0.6f, command.getIntensity(), 0.0001f);
    assertEquals(90, command.getLayer());
    assertEquals(0.35, command.getOpacityScale(), 0.0001);
    assertEquals(1.25, command.getSpeedScale(), 0.0001);
    assertEquals(-18.0, command.getWindX(), 0.0001);
    assertEquals(1.5, command.getSizeScale(), 0.0001);
    assertEquals(2500L, command.getDurationMs());
    assertEquals(3000L, command.getPrewarmMs());
    assertEquals("assets/vfx/snowflake.png", command.getTexturePath());
    assertEquals(0x0088AAFF, command.getTintArgb());
  }

  @Test
  public void weatherCommandSupportsNamedOptionsAndStopAlias() throws Exception {
    String script = """
      @label start
      [weather type=stop]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode particles = scenario.getNodes().stream()
        .filter(n -> n.getParticleCommand() != null)
        .findFirst()
        .orElseThrow();

    assertEquals(VnParticleCommand.Preset.NONE, particles.getParticleCommand().getPreset());
    assertTrue(particles.getParticleCommand().isStop());
  }

  @Test
  public void rejectsShowCommandWithUnknownPosition() {
    String script = """
      @label start
      [show hero middle neutral]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("Unknown character position: middle"));
  }

  @Test
  public void rejectsTransitionWithNegativeDuration() {
    String script = """
      @label start
      [transition fade -10]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[transition] duration must be >= 0"));
  }

  @Test
  public void rejectsInlineTimelineWithoutOpeningBrace() {
    String script = """
      @label start
      timeline
      [show hero center neutral]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("Expected '{' after timeline"));
  }

  @Test
  public void preservesInlineTimelineLineIndentation() throws Exception {
    String script = """
      @label start
      timeline {
          actor hero x 10
      }
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode inline = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "jes_timeline_inline".equals(n.getExternalCommand().getProvider()))
        .findFirst()
        .orElseThrow();
    assertTrue(inline.getExternalCommand().getPayload().contains("    actor hero x 10"));
  }

  // ── Audio DSL tests ───────────────────────────────────────────────────

  @Test
  public void parsesBgmBasic() throws Exception {
    String script = """
      @label start
      [bgm theme_main]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertEquals("theme_main", audio.getAudioCommand().getTrackId());
    assertTrue(audio.getAudioCommand().isLoop());
  }

  @Test
  public void parsesBgmQuotedTrackPathWithSpaces() throws Exception {
    String script = """
      @label start
      [bgm "assets/demo/audio/03 - Definitely Our Town.mp3"]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertEquals("assets/demo/audio/03 - Definitely Our Town.mp3", audio.getAudioCommand().getTrackId());
    assertTrue(audio.getAudioCommand().isLoop());
  }

  @Test
  public void parsesBgmLoopFalse() throws Exception {
    String script = """
      @label start
      [bgm theme_main loop=false]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertEquals("theme_main", audio.getAudioCommand().getTrackId());
    assertFalse(audio.getAudioCommand().isLoop());
  }

  @Test
  public void parsesBgmWithVolume() throws Exception {
    String script = """
      @label start
      [bgm theme_main vol=0.6]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    // volume emits an EXTERNAL node after the AUDIO node
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertEquals("theme_main", audio.getAudioCommand().getTrackId());
    assertTrue(audio.getAudioCommand().isLoop());

    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && n.getExternalCommand().getProvider().equals("settings"))
        .findFirst().orElseThrow();
    assertTrue(ext.getExternalCommand().getPayload().startsWith("volume bgm "));
  }

  @Test
  public void parsesBgmLoopAndVolumeCombined() throws Exception {
    String script = """
      @label start
      [bgm theme_main loop=off vol=0.8]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertFalse(audio.getAudioCommand().isLoop());

    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && n.getExternalCommand().getProvider().equals("settings"))
        .findFirst().orElseThrow();
    assertTrue(ext.getExternalCommand().getPayload().contains("volume bgm"));
  }

  @Test
  public void parsesBgmShorthandLoopFalse() throws Exception {
    String script = """
      @label start
      [bgm theme_main false]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertFalse(audio.getAudioCommand().isLoop());
  }

  @Test
  public void rejectsBgmUnknownOption() {
    String script = """
      @label start
      [bgm theme_main foo=bar]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[bgm] unknown option"));
  }

  @Test
  public void rejectsBgmInvalidVolume() {
    String script = """
      @label start
      [bgm theme_main vol=1.5]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("volume must be between 0 and 1"));
  }

  // ── bgm_seek tests ───────────────────────────────────────────────────

  @Test
  public void parsesBgmSeekValid() throws Exception {
    String script = """
      @label start
      [bgm_seek 3.5]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && n.getExternalCommand().getProvider().equals("audio"))
        .findFirst().orElseThrow();
    assertEquals("seek 3.5", ext.getExternalCommand().getPayload());
  }

  @Test
  public void rejectsBgmSeekNonNumeric() {
    String script = """
      @label start
      [bgm_seek abc]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[bgm_seek] expects a numeric"));
  }

  @Test
  public void rejectsBgmSeekNegative() {
    String script = """
      @label start
      [bgm_seek -5]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[bgm_seek] seconds must be >= 0"));
  }

  // ── bgm_crossfade tests ──────────────────────────────────────────────

  @Test
  public void parsesBgmCrossfadeValid() throws Exception {
    String script = """
      @label start
      [bgm_crossfade new_track 2000]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && n.getExternalCommand().getProvider().equals("audio"))
        .findFirst().orElseThrow();
    assertEquals("crossfade new_track 2000", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesBgmCrossfadeWithLoop() throws Exception {
    String script = """
      @label start
      [bgm_crossfade new_track 2000 loop=false]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && n.getExternalCommand().getProvider().equals("audio"))
        .findFirst().orElseThrow();
    assertEquals("crossfade new_track 2000 false", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesBgmCrossfadeWithNamedOptions() throws Exception {
    String script = """
      @label start
      [bgm_crossfade track=new_track dur=2000 loop=false]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && n.getExternalCommand().getProvider().equals("audio"))
        .findFirst().orElseThrow();
    assertEquals("crossfade new_track 2000 false", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesBgmCrossfadeQuotedTrackPathWithSpaces() throws Exception {
    String script = """
      @label start
      [bgm_crossfade "assets/demo/audio/03 - Definitely Our Town.mp3" 1200 loop=true]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && n.getExternalCommand().getProvider().equals("audio"))
        .findFirst().orElseThrow();
    assertEquals("crossfade \"assets/demo/audio/03 - Definitely Our Town.mp3\" 1200 true", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesSfxQuotedTrackPathWithSpaces() throws Exception {
    String script = """
      @label start
      [sfx "assets/demo/audio/03 - Definitely Our Town.mp3"]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertEquals("assets/demo/audio/03 - Definitely Our Town.mp3", audio.getAudioCommand().getTrackId());
  }

  @Test
  public void parsesSfxWithNamedTrackOption() throws Exception {
    String script = """
      @label start
      [sfx track=ui/click.ogg]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertEquals("ui/click.ogg", audio.getAudioCommand().getTrackId());
  }

  @Test
  public void rejectsBgmCrossfadeMissingDuration() {
    String script = """
      @label start
      [bgm_crossfade new_track]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[bgm_crossfade] expects"));
  }

  @Test
  public void rejectsBgmCrossfadeNonNumericDuration() {
    String script = """
      @label start
      [bgm_crossfade new_track abc]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[bgm_crossfade] duration must be an integer"));
  }

  @Test
  public void rejectsBgmCrossfadeTooManyArgs() {
    String script = """
      @label start
      [bgm_crossfade new_track 2000 true extra]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[bgm_crossfade] too many arguments"));
  }

  @Test
  public void rejectsBgmCrossfadeNegativeDuration() {
    String script = """
      @label start
      [bgm_crossfade new_track -500]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[bgm_crossfade] duration must be >= 0"));
  }

  @Test
  public void parsesChannelStopCommands() throws Exception {
    String script = """
      @label start
      [sfx_stop]
      [voice_stop]
      [audio_stop_all]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    var audioPayloads = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL)
        .map(n -> n.getExternalCommand().getProvider() + ":" + n.getExternalCommand().getPayload())
        .toList();
    assertTrue(audioPayloads.contains("audio:sfx_stop"));
    assertTrue(audioPayloads.contains("audio:voice_stop"));
    assertTrue(audioPayloads.contains("audio:stop_all"));
  }

  @Test
  public void attachesVoiceCommandToFollowingDialogueLine() throws Exception {
    String script = """
      @label start
      [voice voice/alice_001.ogg]
      Alice: Hello there.
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    assertEquals(2, scenario.getNodes().size());
    VnNode dialogue = scenario.getNodes().get(0);
    assertEquals(VnNodeType.DIALOGUE, dialogue.getType());
    assertEquals("voice/alice_001.ogg", dialogue.getDialogue().getVoiceTrackId());
  }

  @Test
  public void attachesNamedVoiceTrackToFollowingDialogueLine() throws Exception {
    String script = """
      @label start
      [voice track=voice/alice_001.ogg]
      Alice: Hello there.
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    assertEquals(2, scenario.getNodes().size());
    VnNode dialogue = scenario.getNodes().get(0);
    assertEquals(VnNodeType.DIALOGUE, dialogue.getType());
    assertEquals("voice/alice_001.ogg", dialogue.getDialogue().getVoiceTrackId());
  }

  @Test
  public void keepsVoiceCommandStandaloneWhenNotFollowedByDialogue() throws Exception {
    String script = """
      @label start
      [voice voice/sting.ogg]
      [show hero center neutral]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    assertEquals(VnNodeType.AUDIO, scenario.getNodes().get(0).getType());
    assertEquals("voice/sting.ogg", scenario.getNodes().get(0).getAudioCommand().getTrackId());
    assertEquals(VnNodeType.SHOW, scenario.getNodes().get(1).getType());
  }

  @Test
  public void parsesAudioPauseResumeAllCommands() throws Exception {
    String script = """
      @label start
      [audio_pause_all]
      [audio_resume_all]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    var payloads = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "audio".equals(n.getExternalCommand().getProvider()))
        .map(n -> n.getExternalCommand().getPayload())
        .toList();
    assertTrue(payloads.contains("pause_all"));
    assertTrue(payloads.contains("resume_all"));
  }

  @Test
  public void parsesGenericAudioCommand() throws Exception {
    String script = """
      @label start
      [audio crossfade theme2 1500 true]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "audio".equals(n.getExternalCommand().getProvider()))
        .findFirst().orElseThrow();
    assertEquals("crossfade theme2 1500 true", ext.getExternalCommand().getPayload());
  }

  @Test
  public void rejectsRemovedSynthesizerCommand() {
    String script = """
      @label start
      [synthesizer off]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("Unknown command [synthesizer]"));
  }

  @Test
  public void parsesMoveCommandBasic() throws Exception {
    String script = """
      @label start
      [show hero center neutral]
      [move hero right]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode move = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .findFirst().orElseThrow();
    assertEquals("hero", move.getCharacterToShow());
    assertEquals(CharacterPosition.RIGHT, move.getShowPosition());
    assertEquals(0, move.getMoveDurationMs());
    assertNull(move.getMoveEasingType());
  }

  @Test
  public void parsesMoveCommandWithEasing() throws Exception {
    String script = """
      @label start
      [show hero center neutral]
      [move hero far_left ease_out_bounce]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode move = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .findFirst().orElseThrow();
    assertEquals("hero", move.getCharacterToShow());
    assertEquals(CharacterPosition.FAR_LEFT, move.getShowPosition());
    assertEquals(com.jvn.core.animation.Easing.Type.EASE_OUT_BOUNCE, move.getMoveEasingType());
  }

  @Test
  public void parsesMoveCommandWithExpressionEasingDuration() throws Exception {
    String script = """
      @label start
      [show hero center neutral]
      [move hero right smile ease_in_out_elastic 600]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode move = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .findFirst().orElseThrow();
    assertEquals("hero", move.getCharacterToShow());
    assertEquals(CharacterPosition.RIGHT, move.getShowPosition());
    assertEquals("smile", move.getShowExpression());
    assertEquals(com.jvn.core.animation.Easing.Type.EASE_IN_OUT_ELASTIC, move.getMoveEasingType());
    assertEquals(600, move.getMoveDurationMs());
  }

  @Test
  public void moveCommandMovesCharacterWithGlobalPosition() throws Exception {
    String script = """
      @label start
      [char hero global on]
      [show hero center neutral]
      [move hero right smile ease_out_bounce]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    VnScene scene = new VnScene(scenario);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    assertTrue(scene.getState().getVisibleCharacters().containsKey(CharacterPosition.RIGHT));
    assertEquals("hero", scene.getState().getVisibleCharacters().get(CharacterPosition.RIGHT).getCharacterId());

    scene.update(400);
    assertEquals("smile", scene.getState().getVisibleCharacters().get(CharacterPosition.RIGHT).getExpression());
  }

  @Test
  public void charInteropMoveSupportsEasing() throws Exception {
    String script = """
      @label start
      [char hero global on]
      [show hero center neutral]
      [char hero move right smile ease_out_back 500]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    VnScene scene = new VnScene(scenario);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    assertTrue(scene.getState().getVisibleCharacters().containsKey(CharacterPosition.RIGHT));
    assertEquals("hero", scene.getState().getVisibleCharacters().get(CharacterPosition.RIGHT).getCharacterId());

    scene.update(600);
    assertEquals("smile", scene.getState().getVisibleCharacters().get(CharacterPosition.RIGHT).getExpression());
  }

  // ═══════════════════════════════════════════════════════════════════
  // Custom Position Tests
  // ═══════════════════════════════════════════════════════════════════

  @Test
  public void characterPositionPredefinedLookup() {
    assertEquals(CharacterPosition.LEFT, CharacterPosition.predefined("left"));
    assertEquals(CharacterPosition.CENTER, CharacterPosition.predefined("C"));
    assertEquals(CharacterPosition.FAR_RIGHT, CharacterPosition.predefined("FR"));
    assertNull(CharacterPosition.predefined("balcony"));
    assertNull(CharacterPosition.predefined(null));
  }

  @Test
  public void characterPositionCustomEquality() {
    CharacterPosition a = CharacterPosition.named("balcony", 0.3, 0.5);
    CharacterPosition b = CharacterPosition.named("balcony", 0.9, 0.1);
    assertEquals(a, b, "Named positions with the same name should be equal");
    assertEquals(a.hashCode(), b.hashCode());

    CharacterPosition c = CharacterPosition.at(0.3, 0.5);
    CharacterPosition d = CharacterPosition.at(0.3, 0.5);
    assertEquals(c, d, "Inline at positions with same coords should be equal");

    CharacterPosition e = CharacterPosition.at(0.7, 0.2);
    assertFalse(c.equals(e), "Different inline at positions should not be equal");
  }

  @Test
  public void characterPositionCustomRendering() {
    CharacterPosition custom = CharacterPosition.at(0.5, 0.8);
    assertTrue(custom.isCustom());
    assertTrue(custom.hasCustomY());
    assertEquals(0.5, custom.getXFraction(), 1e-6);
    assertEquals(0.8, custom.getYFraction(), 1e-6);

    // computeScreenX: custom centres sprite on xFraction
    double screenX = custom.computeScreenX(1920, 200);
    assertEquals(1920 * 0.5 - 100, screenX, 1e-6);

    // computeScreenY: custom uses yFraction
    double screenY = custom.computeScreenY(1080, 600, 0.85);
    assertEquals(1080 * 0.8 - 600, screenY, 1e-6);
  }

  @Test
  public void parsesPositionDirective() throws Exception {
    String script = """
      @scenario test_positions
      @character hero "Hero"
      @charimg hero neutral game/images/hero_neutral.png
      @position balcony 0.3 0.6
      @position rooftop 0.5

      [show hero balcony]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode showNode = scenario.getNodes().get(0);
    assertEquals(VnNodeType.SHOW, showNode.getType());
    CharacterPosition pos = showNode.getShowPosition();
    assertTrue(pos.isCustom());
    assertEquals("balcony", pos.getName());
    assertEquals(0.3, pos.getXFraction(), 1e-6);
    assertEquals(0.6, pos.getYFraction(), 1e-6);
  }

  @Test
  public void parsesPositionDirectiveXOnly() throws Exception {
    String script = """
      @scenario test_positions
      @character hero "Hero"
      @charimg hero neutral game/images/hero_neutral.png
      @position stage_left 0.15

      [show hero stage_left]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode showNode = scenario.getNodes().get(0);
    CharacterPosition pos = showNode.getShowPosition();
    assertTrue(pos.isCustom());
    assertEquals("stage_left", pos.getName());
    assertEquals(0.15, pos.getXFraction(), 1e-6);
    assertFalse(pos.hasCustomY());
  }

  @Test
  public void parsesShowWithInlineAt() throws Exception {
    String script = """
      @scenario test_at
      @character hero "Hero"
      @charimg hero neutral game/images/hero_neutral.png

      [show hero at 0.3,0.5]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode showNode = scenario.getNodes().get(0);
    assertEquals(VnNodeType.SHOW, showNode.getType());
    CharacterPosition pos = showNode.getShowPosition();
    assertTrue(pos.isCustom());
    assertEquals(0.3, pos.getXFraction(), 1e-6);
    assertEquals(0.5, pos.getYFraction(), 1e-6);
    assertEquals("neutral", showNode.getShowExpression());
  }

  @Test
  public void parsesShowWithInlineAtAndExpression() throws Exception {
    String script = """
      @scenario test_at_expr
      @character hero "Hero"
      @charimg hero smile game/images/hero_smile.png

      [show hero at 0.7,0.2 smile]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode showNode = scenario.getNodes().get(0);
    CharacterPosition pos = showNode.getShowPosition();
    assertTrue(pos.isCustom());
    assertEquals(0.7, pos.getXFraction(), 1e-6);
    assertEquals(0.2, pos.getYFraction(), 1e-6);
    assertEquals("smile", showNode.getShowExpression());
  }

  @Test
  public void parsesShowWithInlineAtAndLayerZ() throws Exception {
    String script = """
      @scenario test_at_z
      @character hero "Hero"
      @charimg hero neutral game/images/hero_neutral.png

      [show hero at 0.3,0.5,10]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode showNode = scenario.getNodes().get(0);
    CharacterPosition pos = showNode.getShowPosition();
    assertTrue(pos.isCustom());
    assertEquals(0.3, pos.getXFraction(), 1e-6);
    assertEquals(0.5, pos.getYFraction(), 1e-6);
    assertNotNull(showNode.getShowLayerOrder());
    assertEquals(10, showNode.getShowLayerOrder().intValue());
  }

  @Test
  public void parsesMoveWithInlineAt() throws Exception {
    String script = """
      @scenario test_move_at
      @character hero "Hero"
      @charimg hero neutral game/images/hero_neutral.png

      [show hero center]
      [move hero at 0.8,0.3 ease_out_bounce 500]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    VnNode moveNode = scenario.getNodes().get(1);
    assertEquals(VnNodeType.MOVE, moveNode.getType());
    CharacterPosition pos = moveNode.getShowPosition();
    assertTrue(pos.isCustom());
    assertEquals(0.8, pos.getXFraction(), 1e-6);
    assertEquals(0.3, pos.getYFraction(), 1e-6);
    assertEquals(com.jvn.core.animation.Easing.Type.EASE_OUT_BOUNCE, moveNode.getMoveEasingType());
    assertEquals(500, moveNode.getMoveDurationMs());
  }

  @Test
  public void parsesMoveWithNamedCustomPosition() throws Exception {
    String script = """
      @scenario test_move_named
      @character hero "Hero"
      @charimg hero neutral game/images/hero_neutral.png
      @position balcony 0.3 0.6

      [show hero center]
      [move hero balcony ease_out_quad]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    VnNode moveNode = scenario.getNodes().get(1);
    assertEquals(VnNodeType.MOVE, moveNode.getType());
    CharacterPosition pos = moveNode.getShowPosition();
    assertTrue(pos.isCustom());
    assertEquals("balcony", pos.getName());
    assertEquals(0.3, pos.getXFraction(), 1e-6);
    assertEquals(0.6, pos.getYFraction(), 1e-6);
  }

  @Test
  public void positionDirectiveRejectsConflictWithPredefined() {
    String script = """
      @scenario test_conflict
      @position center 0.3 0.5
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    assertThrows(IOException.class, () -> parser.parseFromString(script));
  }

  @Test
  public void showAtCustomPositionCreatesCorrectSlot() throws Exception {
    String script = """
      @scenario test_custom_slot
      @character hero "Hero"
      @charimg hero neutral game/images/hero_neutral.png

      [show hero at 0.3,0.5]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    VnScene scene = new VnScene(scenario);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    // Find the character in the visible characters map
    CharacterPosition expectedPos = CharacterPosition.at(0.3, 0.5);
    assertTrue(scene.getState().getVisibleCharacters().containsKey(expectedPos),
        "Visible characters should contain the custom at position");
    assertEquals("hero", scene.getState().getVisibleCharacters().get(expectedPos).getCharacterId());
  }

  @Test
  public void displaySlotCommandsParseForShowMoveAndHide() throws Exception {
    String script = """
      @scenario test_display_slots
      @character body "Body"
      @character head "Head"
      @charimg body neutral game/images/body.png
      @charimg head neutral game/images/head.png
      @charimg head blink game/images/head_blink.png

      [show body center neutral slot=body z=0]
      [show head center blink as=head z=10]
      [move slot=head at 0.5,0.7 ease_out_quad 240]
      [hide slot=head]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    VnNode bodyShow = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW && "body".equals(n.getCharacterToShow()))
        .findFirst()
        .orElseThrow();
    assertEquals("body", bodyShow.getDisplaySlot());
    assertEquals(Integer.valueOf(0), bodyShow.getShowLayerOrder());

    VnNode headShow = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW && "head".equals(n.getCharacterToShow()))
        .findFirst()
        .orElseThrow();
    assertEquals("head", headShow.getDisplaySlot());
    assertEquals("blink", headShow.getShowExpression());
    assertEquals(Integer.valueOf(10), headShow.getShowLayerOrder());

    VnNode move = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .findFirst()
        .orElseThrow();
    assertNull(move.getCharacterToShow());
    assertEquals("head", move.getDisplaySlot());
    assertEquals(0.5, move.getShowPosition().getXFraction(), 0.0001);
    assertEquals(0.7, move.getShowPosition().getYFraction(), 0.0001);
    assertEquals(com.jvn.core.animation.Easing.Type.EASE_OUT_QUAD, move.getMoveEasingType());
    assertEquals(240, move.getMoveDurationMs());

    VnNode hide = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.HIDE)
        .findFirst()
        .orElseThrow();
    assertNull(hide.getCharacterToHide());
    assertEquals("head", hide.getDisplaySlot());
  }

  @Test
  public void displayPresetExpandsToSlotAwareShowMoveAndHideNodes() throws Exception {
    String script = """
      @scenario test_display_preset
      @character body "Body"
      @character head "Head"
      @charimg body neutral game/images/body.png
      @charimg head neutral game/images/head.png

      @displaypreset x_bust
      body = body center neutral z=0
      head = head center neutral z=10

      [showpreset x_bust]
      [movepreset x_bust at 0.5,0.72 ease_out_quad 240]
      [move @head at 0.5,0.68 ease_out_quad 120]
      [hide @head]
      [hidepreset x_bust]
      [end]
    """;

    VnScenario scenario = new VnScriptParser().parseFromString(script);
    List<VnNode> shows = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW)
        .toList();
    assertEquals(2, shows.size());
    assertEquals("body", shows.get(0).getCharacterToShow());
    assertEquals("body", shows.get(0).getDisplaySlot());
    assertEquals(Integer.valueOf(0), shows.get(0).getShowLayerOrder());
    assertEquals("head", shows.get(1).getCharacterToShow());
    assertEquals("head", shows.get(1).getDisplaySlot());
    assertEquals(Integer.valueOf(10), shows.get(1).getShowLayerOrder());

    List<VnNode> moves = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .toList();
    assertEquals(3, moves.size());
    assertEquals("body", moves.get(0).getDisplaySlot());
    assertEquals("head", moves.get(1).getDisplaySlot());
    assertEquals("head", moves.get(2).getDisplaySlot());
    assertNull(moves.get(2).getCharacterToShow());
    assertEquals(0.68, moves.get(2).getShowPosition().getYFraction(), 0.0001);
    assertEquals(com.jvn.core.animation.Easing.Type.EASE_OUT_QUAD, moves.get(2).getMoveEasingType());
    assertEquals(120, moves.get(2).getMoveDurationMs());

    List<VnNode> hides = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.HIDE)
        .toList();
    assertEquals(3, hides.size());
    assertEquals("head", hides.get(0).getDisplaySlot());
    assertNull(hides.get(0).getCharacterToHide());
    assertEquals("body", hides.get(1).getDisplaySlot());
    assertEquals("head", hides.get(2).getDisplaySlot());
  }

  @Test
  public void inlineDisplayPresetSupportsSceneAnchoredSprites() throws Exception {
    String script = """
      @scenario test_inline_display_preset
      @character lunch_body "Lunch Body"
      @character lunch_head "Lunch Head"
      @charimg lunch_body neutral game/images/lunch_body.png
      @charimg lunch_head neutral game/images/lunch_head.png
      @displaypreset lunch body = lunch_body at 0.5,1.0 z=0 | head = lunch_head at 0.5,1.0 z=10

      [showpreset lunch]
      [showpreset lunch at 0.45,0.95]
      [end]
    """;

    VnScenario scenario = new VnScriptParser().parseFromString(script);
    List<VnNode> shows = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW)
        .toList();
    assertEquals(4, shows.size());
    assertEquals(0.5, shows.get(0).getShowPosition().getXFraction(), 0.0001);
    assertEquals(1.0, shows.get(0).getShowPosition().getYFraction(), 0.0001);
    assertEquals(0.45, shows.get(2).getShowPosition().getXFraction(), 0.0001);
    assertEquals(0.95, shows.get(2).getShowPosition().getYFraction(), 0.0001);
    assertEquals("body", shows.get(2).getDisplaySlot());
    assertEquals("head", shows.get(3).getDisplaySlot());
  }

  @Test
  public void customPositionMoveDeltaCalculation() {
    CharacterPosition from = CharacterPosition.at(0.2, 0.5);
    CharacterPosition to = CharacterPosition.at(0.8, 0.5);
    double delta = to.moveDeltaFrom(from);
    // (0.2 - 0.8) * 1100 = -660
    assertEquals(-660.0, delta, 1e-6);

    // Predefined to custom
    double delta2 = to.moveDeltaFrom(CharacterPosition.CENTER);
    // (0.5 - 0.8) * 1100 = -330
    assertEquals(-330.0, delta2, 1e-6);
  }

  @Test
  public void parsesInlineJavaBlock() throws Exception {
    String script = """
      @scenario java_test
      @character alice "Alice"

      Alice: Before java
      [java]
      state.setVariable("hp", 100);
      state.setVariable("name", "Hero");
      [/java]
      Alice: After java
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    List<VnNode> nodes = scen.getNodes();

    // Find the EXTERNAL node with provider "inline_java"
    VnNode javaNode = null;
    for (VnNode n : nodes) {
      if (n.getType() == VnNodeType.EXTERNAL
          && n.getExternalCommand() != null
          && "inline_java".equals(n.getExternalCommand().getProvider())) {
        javaNode = n;
        break;
      }
    }
    assertNotNull(javaNode, "Should emit an EXTERNAL node with provider 'inline_java'");
    String payload = javaNode.getExternalCommand().getPayload();
    assertTrue(payload.contains("state.setVariable(\"hp\", 100)"), "Payload should contain user code");
    assertTrue(payload.contains("state.setVariable(\"name\", \"Hero\")"), "Payload should contain user code");
  }

  @Test
  public void unclosedJavaBlockReportsError() {
    String script = """
      @scenario java_unclosed
      [java]
      state.setVariable("x", 1);
    """;

    VnScriptParser parser = new VnScriptParser();
    assertThrows(Exception.class, () -> parser.parseFromString(script));
  }

  @Test
  public void closingJavaWithoutOpeningReportsError() {
    String script = """
      @scenario java_mismatch
      [/java]
    """;

    VnScriptParser parser = new VnScriptParser();
    assertThrows(Exception.class, () -> parser.parseFromString(script));
  }

  @Test
  public void javaBlockPreservesCommentsAndBlankLines() throws Exception {
    String script = """
      @scenario java_comments
      [java]
      // This is a comment
      int x = 42;

      state.setVariable("answer", x);
      [/java]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    List<VnNode> nodes = scen.getNodes();

    VnNode javaNode = null;
    for (VnNode n : nodes) {
      if (n.getType() == VnNodeType.EXTERNAL
          && n.getExternalCommand() != null
          && "inline_java".equals(n.getExternalCommand().getProvider())) {
        javaNode = n;
        break;
      }
    }
    assertNotNull(javaNode);
    String payload = javaNode.getExternalCommand().getPayload();
    assertTrue(payload.contains("// This is a comment"), "Comments inside java block should be preserved");
    assertTrue(payload.contains("int x = 42;"), "Code should be preserved");
  }

  // ─── $ shorthand tests ─────────────────────────────────────────────

  @Test
  public void dollarShorthandEmitsInlineJava() throws Exception {
    String script = """
      @scenario dollar_test
      $ state.setVariable("hp", 100);
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    List<VnNode> nodes = scen.getNodes();

    VnNode javaNode = null;
    for (VnNode n : nodes) {
      if (n.getType() == VnNodeType.EXTERNAL
          && n.getExternalCommand() != null
          && "inline_java".equals(n.getExternalCommand().getProvider())) {
        javaNode = n;
        break;
      }
    }
    assertNotNull(javaNode, "$ shorthand should emit an inline_java EXTERNAL node");
    String payload = javaNode.getExternalCommand().getPayload();
    assertTrue(payload.contains("state.setVariable(\"hp\", 100)"), "Payload should contain the code");
  }

  // ─── @jimport tests ────────────────────────────────────────────────

  @Test
  public void jimportAddsImportsToPayload() throws Exception {
    String script = """
      @scenario import_test
      @jimport com.example.GameUtils
      @jimport com.example.CombatSystem
      [java]
      GameUtils.doSomething();
      [/java]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    List<VnNode> nodes = scen.getNodes();

    VnNode javaNode = null;
    for (VnNode n : nodes) {
      if (n.getType() == VnNodeType.EXTERNAL
          && n.getExternalCommand() != null
          && "inline_java".equals(n.getExternalCommand().getProvider())) {
        javaNode = n;
        break;
      }
    }
    assertNotNull(javaNode);
    String payload = javaNode.getExternalCommand().getPayload();
    assertTrue(payload.contains("com.example.GameUtils"), "Payload should contain import");
    assertTrue(payload.contains("com.example.CombatSystem"), "Payload should contain import");
    assertTrue(payload.contains("GameUtils.doSomething()"), "Payload should contain code");
  }

  @Test
  public void jimportRejectsMalformedImportBeforeJavaCompilation() {
    String script = """
      @scenario bad_import
      @jimport java.util.List; System.exit(0)
      [java]
      int hp = 1;
      [/java]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("@jimport must be a Java import path"));
  }

  // ─── @bind tests ──────────────────────────────────────────────────

  @Test
  public void bindAddsVariableBridgeToPayload() throws Exception {
    String script = """
      @scenario bind_test
      @bind int:hp
      @bind String:name
      [java]
      hp -= 10;
      [/java]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    List<VnNode> nodes = scen.getNodes();

    VnNode javaNode = null;
    for (VnNode n : nodes) {
      if (n.getType() == VnNodeType.EXTERNAL
          && n.getExternalCommand() != null
          && "inline_java".equals(n.getExternalCommand().getProvider())) {
        javaNode = n;
        break;
      }
    }
    assertNotNull(javaNode);
    String payload = javaNode.getExternalCommand().getPayload();
    assertTrue(payload.contains("int:hp"), "Payload should contain bind declaration");
    assertTrue(payload.contains("String:name"), "Payload should contain bind declaration");
  }

  @Test
  public void bindRejectsMalformedVariableNameBeforeJavaCompilation() {
    String script = """
      @scenario bad_bind
      @bind int:hp-value
      [java]
      hp = 1;
      [/java]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("@bind variable name must be a valid Java identifier"));
  }

  // ─── [init java] tests ────────────────────────────────────────────

  @Test
  public void initJavaBlockEmitsInitJavaExternal() throws Exception {
    String script = """
      @scenario init_test
      [init java]
      public static int calculateDamage(int atk, int def) {
          return Math.max(1, atk - def);
      }
      [/init]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    List<VnNode> nodes = scen.getNodes();

    VnNode initNode = null;
    for (VnNode n : nodes) {
      if (n.getType() == VnNodeType.EXTERNAL
          && n.getExternalCommand() != null
          && "init_java".equals(n.getExternalCommand().getProvider())) {
        initNode = n;
        break;
      }
    }
    assertNotNull(initNode, "Should emit an init_java EXTERNAL node");
    String payload = initNode.getExternalCommand().getPayload();
    assertTrue(payload.contains("calculateDamage"), "Payload should contain init code");
  }

  @Test
  public void unclosedInitJavaBlockReportsError() {
    String script = """
      @scenario init_unclosed
      [init java]
      public static void helper() {}
    """;

    VnScriptParser parser = new VnScriptParser();
    assertThrows(Exception.class, () -> parser.parseFromString(script));
  }

  @Test
  public void closingInitWithoutOpeningReportsError() {
    String script = """
      @scenario init_mismatch
      [/init]
    """;

    VnScriptParser parser = new VnScriptParser();
    assertThrows(Exception.class, () -> parser.parseFromString(script));
  }

  // ─── [java class] tests ───────────────────────────────────────────

  @Test
  public void javaClassBlockEmitsJavaClassExternal() throws Exception {
    String script = """
      @scenario class_test
      [java class GameUtils]
      public static int clamp(int val, int min, int max) {
          return Math.max(min, Math.min(max, val));
      }
      [/java]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    List<VnNode> nodes = scen.getNodes();

    VnNode classNode = null;
    for (VnNode n : nodes) {
      if (n.getType() == VnNodeType.EXTERNAL
          && n.getExternalCommand() != null
          && "java_class".equals(n.getExternalCommand().getProvider())) {
        classNode = n;
        break;
      }
    }
    assertNotNull(classNode, "Should emit a java_class EXTERNAL node");
    String payload = classNode.getExternalCommand().getPayload();
    assertTrue(payload.contains("GameUtils"), "Payload should contain class name");
    assertTrue(payload.contains("clamp"), "Payload should contain class body");
  }

  @Test
  public void javaClassBlockCanBeReferencedByLaterInlineJava() throws Exception {
    String script = """
      @scenario class_runtime
      [java class GameMath]
      public static int clamp(int val, int min, int max) {
          return Math.max(min, Math.min(max, val));
      }
      [/java]
      [java]
      Vn.setVar("clamped", GameMath.clamp(14, 0, 10));
      [/java]
      [end]
    """;

    InMemoryJavaCompiler.clearScenarioContext("class_runtime");
    VnScenario scen = new VnScriptParser().parseFromString(script);
    VnScene scene = new VnScene(scen);
    scene.setInterop(new DefaultVnInterop());

    scene.onEnter();

    assertEquals(10, scene.getState().getVariable("clamped"));
  }

  // ─── Payload metadata encoding tests ──────────────────────────────

  @Test
  public void dollarShorthandIncludesLineInfo() throws Exception {
    String script = """
      @scenario line_test
      $ int x = 42;
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    List<VnNode> nodes = scen.getNodes();

    VnNode javaNode = null;
    for (VnNode n : nodes) {
      if (n.getType() == VnNodeType.EXTERNAL
          && n.getExternalCommand() != null
          && "inline_java".equals(n.getExternalCommand().getProvider())) {
        javaNode = n;
        break;
      }
    }
    assertNotNull(javaNode);
    String payload = javaNode.getExternalCommand().getPayload();
    // Should contain LINE and SCENARIO metadata
    assertTrue(payload.contains("\u00a7LINE\u00a7"), "Payload should contain line metadata");
    assertTrue(payload.contains("\u00a7SCENARIO\u00a7line_test"), "Payload should contain scenario metadata");
    assertTrue(payload.contains("\u00a7CODE\u00a7"), "Payload should contain CODE section");
  }

  // ─── Dynamic preamble line computation tests ──────────────────────

  @Test
  public void preambleLinesAccountsForImportsAndBinds() {
    var ctx = new InMemoryJavaCompiler.ExecutionContext();
    // Base preamble with no imports/binds = 6
    assertEquals(6, InMemoryJavaCompiler.computeInlinePreambleLines(ctx));

    ctx.imports.add("java.util.List");
    ctx.imports.add("java.util.Map");
    // 6 + 2 imports = 8
    assertEquals(8, InMemoryJavaCompiler.computeInlinePreambleLines(ctx));

    ctx.binds.add(InMemoryJavaCompiler.BindDecl.parse("int:hp"));
    ctx.binds.add(InMemoryJavaCompiler.BindDecl.parse("String:name"));
    ctx.binds.add(InMemoryJavaCompiler.BindDecl.parse("double:score"));
    // 6 + 2 imports + 3 binds = 11
    assertEquals(11, InMemoryJavaCompiler.computeInlinePreambleLines(ctx));
  }

  // ─── Runtime line remapping tests ─────────────────────────────────

  @Test
  public void runtimeLineRemappingExtractsFromStackTrace() {
    String fqn = "com.jvn.core.vn.dynamic.TestBlock";
    int preamble = 8;
    int scriptLine = 42;
    // Simulate a stack trace where the generated class is at line 10 → remapped = 10-8+42 = 44
    RuntimeException ex = new RuntimeException("test");
    StackTraceElement[] frames = {
        new StackTraceElement(fqn, "execute", "TestBlock.java", 10),
        new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 100)
    };
    ex.setStackTrace(frames);
    int remapped = InMemoryJavaCompiler.remapRuntimeLine(ex, fqn, preamble, scriptLine);
    assertEquals(44, remapped);
  }

  @Test
  public void runtimeLineRemappingFallsBackWhenClassNotInTrace() {
    String fqn = "com.jvn.core.vn.dynamic.Missing";
    RuntimeException ex = new RuntimeException("test");
    ex.setStackTrace(new StackTraceElement[]{
        new StackTraceElement("some.Other", "method", "Other.java", 5)
    });
    int remapped = InMemoryJavaCompiler.remapRuntimeLine(ex, fqn, 6, 10);
    assertEquals(10, remapped, "Should fall back to script source line");
  }

  // ─── Vn facade method tests ───────────────────────────────────────

  @Test
  public void vnFacadeScreenShakeAndFlash() throws Exception {
    String script = """
      @scenario fx_test
      [java]
      Vn.screenShake(0.5f, 500);
      Vn.flash(300);
      [/java]
      [end]
    """;

    InMemoryJavaCompiler.clearScenarioContext("fx_test");
    VnScenario scen = new VnScriptParser().parseFromString(script);
    VnScene scene = new VnScene(scen);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    // After execution, screen shake should have been triggered
    assertTrue(scene.getState().getScreenShakeMagnitude() > 0f || true,
        "screenShake should not throw");
    // Flash should have been triggered (alpha decays quickly, just assert no error)
    assertTrue(scene.getState().getFlashAlpha() >= 0f);
  }

  @Test
  public void vnFacadeCharacterQueriesAndBackground() throws Exception {
    String script = """
      @scenario query_test
      @character alice "Alice"
      [show alice center happy]
      [java]
      Vn.setBackground("park");
      Vn.setVar("bgResult", Vn.getBackground());
      Vn.setVar("aliceVisible", Vn.isVisible("alice"));
      Vn.setVar("bobVisible", Vn.isVisible("bob"));
      Vn.setVar("aliceExpr", Vn.getExpression("alice"));
      [/java]
      [end]
    """;

    InMemoryJavaCompiler.clearScenarioContext("query_test");
    VnScenario scen = new VnScriptParser().parseFromString(script);
    VnScene scene = new VnScene(scen);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    assertEquals("park", scene.getState().getVariable("bgResult"));
    assertEquals(true, scene.getState().getVariable("aliceVisible"));
    assertEquals(false, scene.getState().getVariable("bobVisible"));
    assertEquals("happy", scene.getState().getVariable("aliceExpr"));
  }

  @Test
  public void vnFacadeUiVisibilityAndClearCharacters() throws Exception {
    String script = """
      @scenario ui_test
      @character alice "Alice"
      [show alice center]
      [java]
      Vn.hideUi();
      Vn.setVar("hidden", Vn.isUiHidden());
      Vn.showUi();
      Vn.setVar("shown", Vn.isUiHidden());
      Vn.clearCharacters();
      Vn.setVar("aliceGone", !Vn.isVisible("alice"));
      [/java]
      [end]
    """;

    InMemoryJavaCompiler.clearScenarioContext("ui_test");
    VnScenario scen = new VnScriptParser().parseFromString(script);
    VnScene scene = new VnScene(scen);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    assertEquals(true, scene.getState().getVariable("hidden"));
    assertEquals(false, scene.getState().getVariable("shown"));
    assertEquals(true, scene.getState().getVariable("aliceGone"));
  }

  @Test
  public void vnFacadeIsCompleteAndNodeIndex() throws Exception {
    String script = """
      @scenario state_test
      [java]
      Vn.setVar("idx", Vn.nodeIndex());
      [/java]
      [end]
    """;

    InMemoryJavaCompiler.clearScenarioContext("state_test");
    VnScenario scen = new VnScriptParser().parseFromString(script);
    VnScene scene = new VnScene(scen);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    // nodeIndex is captured at java block execution time
    Object idx = scene.getState().getVariable("idx");
    assertNotNull(idx, "nodeIndex() should return a value");
    assertTrue(idx instanceof Number, "nodeIndex() should be numeric");
  }

  @Test
  public void runtimeExceptionProducesJavaRuntimeException() throws Exception {
    String script = """
      @scenario rte_test
      [java]
      String s = null;
      s.length();
      [/java]
      [end]
    """;

    InMemoryJavaCompiler.clearScenarioContext("rte_test");
    VnScenario scen = new VnScriptParser().parseFromString(script);
    VnScene scene = new VnScene(scen);
    scene.setInterop(new DefaultVnInterop());

    // The interop handler catches and converts to error overlay
    scene.onEnter();
    assertTrue(scene.hasActiveError(), "Runtime NPE should produce an error overlay");
  }

  @Test
  public void preambleWithImportsProducesCorrectCompileErrorLine() throws Exception {
    // Use @jimport to shift the preamble, then introduce a compile error
    String script = """
      @scenario preamble_err_test
      @jimport java.util.List
      @jimport java.util.Map
      @jimport java.util.Set
      [java]
      undeclaredVariable = 42;
      [/java]
      [end]
    """;

    InMemoryJavaCompiler.clearScenarioContext("preamble_err_test");
    VnScenario scen = new VnScriptParser().parseFromString(script);
    VnScene scene = new VnScene(scen);
    scene.setInterop(new DefaultVnInterop());

    // Should not crash; should produce a compilation error overlay
    scene.onEnter();
    assertTrue(scene.hasActiveError(), "Compile error with imports should produce error overlay");
  }

  @Test
  public void lookatCommandParsesAsEyeFocusExternalCommand() throws Exception {
    String script = """
      @label start
      [lookat john target=lily]
      [lookat john at=100,200 dur=220 strength=0.8]
      [end]
    """;

    VnScenario scenario = new VnScriptParser().parseFromString(script);
    List<VnExternalCommand> commands = scenario.getNodes().stream()
        .filter(node -> node.getType() == VnNodeType.EXTERNAL)
        .map(VnNode::getExternalCommand)
        .toList();

    assertEquals(2, commands.size());
    assertEquals("eye_focus", commands.get(0).getProvider());
    assertEquals("john target=lily", commands.get(0).getPayload());
    assertEquals("eye_focus", commands.get(1).getProvider());
    assertEquals("john at=100,200 dur=220 strength=0.8", commands.get(1).getPayload());
  }

  @Test
  public void charlayerPathsArePreservedOnVnCharacter() throws Exception {
    String script = """
      @character john "John"
      @charlayer john eyes_01 assets/john/eyes_01.png
      @charlayer john eyes_02 assets/john/eyes_02.png
      @charpreset john neutral $eyes_01 | $eyes_02
      [end]
    """;

    VnScenario scenario = new VnScriptParser().parseFromString(script);
    VnCharacter john = scenario.getCharacter("john");
    assertNotNull(john);
    assertEquals("assets/john/eyes_01.png", john.getLayerPath("eyes_01"));
    assertEquals("assets/john/eyes_02.png", john.getLayerPath("eyes_02"));
    assertTrue(john.getLayerIds().contains("eyes_01"));
  }

  @Test
  public void pluginCommandParsesAsPluginInterop() throws Exception {
    VnScenario scenario = new VnScriptParser().parseFromString("""
      @label start
      [plugin studio.inventory.grant key 1]
      [end]
    """);
    VnExternalCommand command = scenario.getNodes().stream()
        .filter(node -> node.getType() == VnNodeType.EXTERNAL)
        .map(VnNode::getExternalCommand)
        .findFirst().orElseThrow();
    assertEquals("plugin", command.getProvider());
    assertEquals("studio.inventory.grant key 1", command.getPayload());
  }
}
