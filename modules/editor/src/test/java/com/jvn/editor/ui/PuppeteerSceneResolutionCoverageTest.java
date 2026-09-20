package com.jvn.editor.ui;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Entity-by-entity coverage for the VNS state handed to Puppeteer at the cursor. */
class PuppeteerSceneResolutionCoverageTest {

  @Test
  void cursorSyncUsesTheSameNestedIncludeResolverAsLaunch(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
    java.nio.file.Files.createDirectories(root.resolve("scripts/defs"));
    java.nio.file.Path script = root.resolve("scripts/main.vns");
    String source = "@include defs/hero.vns\n[show hero center neutral]\n";
    java.nio.file.Files.writeString(script, source);
    java.nio.file.Files.writeString(root.resolve("scripts/defs/hero.vns"),
        "@group hero cast\n@include layers.vns\n@chargroup hero face parent=head $eyes\n@chargroup hero head $face\n@charpreset hero neutral $head\n");
    java.nio.file.Files.writeString(root.resolve("scripts/defs/layers.vns"), "@charlayer hero eyes assets/eyes.png\n");
    var snapshot = PuppeteerLauncherPanel.resolveProjectSnapshot(source, 1, script.toFile(), root.toFile());
    assertEquals("assets/eyes.png", snapshot.resolveCharacterLayers("hero", "neutral").getFirst().path);
    assertEquals("head", snapshot.resolveCharacterLayerGroup("hero", "face").parentGroupId);
    assertEquals("cast", snapshot.dynamicGroups.get("hero"));
  }

  @Nested
  class Backgrounds {
    @Test
    void keepsCurrentAndPreviousBackgroundWithTheirAssetMappings() {
      String source = """
          @background day assets/backgrounds/day.png
          @background night assets/backgrounds/night.png
          @label scene
          [bg day]
          [background night]
          """;

      PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 4);

      assertEquals("night", snapshot.backgroundId);
      assertEquals("day", snapshot.previousBackgroundId);
      assertEquals("assets/backgrounds/night.png", snapshot.resolveBackgroundPath());
      assertEquals("assets/backgrounds/day.png", snapshot.resolvePreviousBackgroundPath());
    }

    @Test
    void treatsTransitionsWithBackgroundTargetsAsSceneChanges() {
      String source = """
          @background woods assets/backgrounds/woods.png
          @background cg assets/cg/cg.png
          [transition DISSOLVE 500 woods]
          [transition type=FADE duration=300 bg=cg]
          """;

      PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 3);

      assertEquals("cg", snapshot.backgroundId);
      assertEquals("woods", snapshot.previousBackgroundId);
      assertEquals(3, snapshot.backgroundLine);
    }
  }

  @Nested
  class CharacterSpritesAndProps {
    @Test
    void resolvesPropSpriteCustomPositionDisplaySlotAndZOrder() {
      String source = """
          @character heart_effect ""
          @charimg heart_effect pulse assets/effects/heart_attack.png
          @position full_screen 0.5 1.0
          @label scene
          [show heart_effect full_screen pulse slot=heart z=100]
          """;

      PuppeteerLauncherPanel.CharacterEntry prop =
          PuppeteerLauncherPanel.resolveSnapshot(source, 4).characters.get(0);

      assertEquals("heart_effect", prop.characterId);
      assertEquals("pulse", prop.expression);
      assertEquals("full_screen", prop.position);
      assertEquals("heart", prop.displaySlot);
      assertEquals(100, prop.layerOrder);
      assertTrue(prop.customPosition);
      assertEquals(0.5, prop.positionX, 1e-9);
      assertEquals(1.0, prop.positionY, 1e-9);
    }

    @Test
    void appliesTopLevelMoveThenSlotBasedHide() {
      String source = """
          @charimg prop neutral assets/props/prop.png
          @charimg prop active assets/props/prop_active.png
          @position shelf 0.25 0.4
          [show prop left neutral slot=prop z=7]
          [move slot=prop shelf active ease_out_quad 240]
          [hide slot=prop]
          """;

      PuppeteerLauncherPanel.SceneSnapshot beforeHide = PuppeteerLauncherPanel.resolveSnapshot(source, 4);
      assertEquals(1, beforeHide.characters.size());
      assertEquals("shelf", beforeHide.characters.get(0).position);
      assertEquals("active", beforeHide.characters.get(0).expression);
      assertEquals(7, beforeHide.characters.get(0).layerOrder);

      assertTrue(PuppeteerLauncherPanel.resolveSnapshot(source, 5).characters.isEmpty());
    }

    @Test
    void resolvesInlineCoordinatesWithoutMistakingThemForAnExpression() {
      String source = """
          @charimg narrator thinking assets/narrator.png
          [show narrator at 0.3,0.55 thinking]
          """;

      PuppeteerLauncherPanel.CharacterEntry character =
          PuppeteerLauncherPanel.resolveSnapshot(source, 1).characters.get(0);

      assertEquals("thinking", character.expression);
      assertTrue(character.customPosition);
      assertEquals(0.3, character.positionX, 1e-9);
      assertEquals(0.55, character.positionY, 1e-9);
    }
  }

  @Nested
  class LayeredCharacters {
    @Test
    void expandsMultiLayerCharImgShortcutIntoIndependentPuppeteerEntities() {
      String source = """
          @charimg hero battle assets/hero/body.png | assets/hero/eyes.png | assets/hero/mouth.png
          [show hero center battle]
          """;

      PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 1);

      assertEquals(List.of("body", "eyes", "mouth"),
          snapshot.resolveCharacterLayers("hero", "battle").stream().map(layer -> layer.layerId).toList());
    }

    @Test
    void resolvesCharLayersNestedCharPresetsAndInlineComposites() {
      String source = """
          @charlayer hero base assets/hero/base.png
          @charlayer hero eyes assets/hero/eyes.png
          @charlayer hero smile assets/hero/smile.png
          @charpreset hero neutral $base | $eyes
          [show hero center @neutral+$smile]
          """;

      PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 4);
      PuppeteerLauncherPanel.CharacterEntry hero = snapshot.characters.get(0);

      assertTrue(hero.expression.startsWith("__inline_neutral_smile_"));
      assertEquals(List.of("base", "eyes", "smile"),
          snapshot.resolveCharacterLayers("hero", hero.expression).stream().map(layer -> layer.layerId).toList());
      assertEquals(
          "assets/hero/base.png | assets/hero/eyes.png | assets/hero/smile.png",
          snapshot.resolveCharacterPath("hero", hero.expression));
    }

    @Test
    void resolvesNestedCharGroupsWithPivotsAndParents() {
      String source = """
          @charlayer hero head assets/hero/head.png
          @charlayer hero eyes assets/hero/eyes.png
          @charlayer hero mouth assets/hero/mouth.png
          @chargroup hero face parent=head $eyes | $mouth
          @chargroup hero head pivot=0.5,0.2 $head | $face
          @charpreset hero neutral $head
          [show hero center neutral]
          """;

      PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 6);
      PuppeteerLauncherPanel.CharacterLayerGroupEntry head = snapshot.resolveCharacterLayerGroup("hero", "head");
      PuppeteerLauncherPanel.CharacterLayerGroupEntry face = snapshot.resolveCharacterLayerGroup("hero", "face");

      assertNotNull(head);
      assertNotNull(face);
      assertEquals(List.of("head", "eyes", "mouth"), head.layerIds);
      assertTrue(head.hasPivot);
      assertEquals("head", face.parentGroupId);
    }
  }

  @Nested
  class GroupedCharacters {
    @Test
    void resolvesDisplayPresetShowMoveAndHideAsIndependentSlots() {
      String source = """
          @charimg bust_body neutral assets/bust/body.png
          @charimg bust_head blink assets/bust/head.png
          @displaypreset bust
          body = bust_body center neutral z=0
          head = bust_head center blink z=10
          [showpreset bust]
          [movepreset bust at 0.6,0.75]
          """;

      PuppeteerLauncherPanel.SceneSnapshot moved = PuppeteerLauncherPanel.resolveSnapshot(source, 6);
      assertEquals(2, moved.characters.size());
      assertEquals(List.of("body", "head"), moved.characters.stream().map(entry -> entry.displaySlot).toList());
      assertEquals(List.of(0, 10), moved.characters.stream().map(entry -> entry.layerOrder).toList());
      assertTrue(moved.characters.stream().allMatch(entry -> entry.customPosition));
      assertTrue(moved.characters.stream().allMatch(entry -> Math.abs(entry.positionX - 0.6) < 1e-9));

      PuppeteerLauncherPanel.SceneSnapshot hidden =
          PuppeteerLauncherPanel.resolveSnapshot(source + "[hidepreset bust]\n", 7);
      assertTrue(hidden.characters.isEmpty());
    }

    @Test
    void preservesDynamicEntityGroupAssignmentsAtCursor() {
      String source = """
          @group cast
          @group hero cast
          [group prop cast]
          [group hero none]
          """;

      PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 3);

      assertEquals("cast", snapshot.dynamicGroups.get("prop"));
      assertFalse(snapshot.dynamicGroups.containsKey("hero"));
    }
  }
}
