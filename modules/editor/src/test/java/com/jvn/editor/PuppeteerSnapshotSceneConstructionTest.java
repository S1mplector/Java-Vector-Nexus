package com.jvn.editor;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.Sprite2D;
import com.jvn.editor.ui.PuppeteerLauncherPanel;
import com.jvn.scripting.jes.runtime.JesScene2D;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that resolved cursor state survives the final handoff into Puppeteer's JES scene. */
class PuppeteerSnapshotSceneConstructionTest {

  @TempDir Path tempDir;

  @Test
  void customPositionAndZOrderReachTheLaunchScene() throws Exception {
    String source = """
        @charimg heart_effect pulse assets/effects/heart_attack.png
        @position full_screen 0.5 1.0
        [show heart_effect full_screen pulse slot=heart z=100]
        """;
    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 2);

    JesScene2D scene = buildScene(snapshot);
    Entity2D prop = scene.find("heart_effect");

    assertNotNull(prop);
    assertEquals(960.0, prop.getX(), 1e-9);
    assertEquals(1080.0, prop.getY(), 1e-9);
    assertEquals(100.0, prop.getZ(), 1e-9);
  }

  @Test
  void multiLayerCharImgCreatesEveryPuppeteerLayerTarget() throws Exception {
    String source = """
        @charimg hero battle assets/hero/body.png | assets/hero/eyes.png | assets/hero/mouth.png
        [show hero center battle]
        """;

    JesScene2D scene = buildScene(PuppeteerLauncherPanel.resolveSnapshot(source, 1));

    assertNotNull(scene.find("hero_body"));
    assertNotNull(scene.find("hero_eyes"));
    assertNotNull(scene.find("hero_mouth"));
  }

  @Test
  void negativeZSceneEntitiesRemainAboveTheBackground() throws Exception {
    String source = """
        @background lunchroom assets/backgrounds/lunchroom.png
        @charlayer crowd_milling crowd assets/props/crowd.png
        [bg lunchroom]
        [show crowd_milling at 0.4,1.325 $crowd slot=milling z=-5]
        """;

    JesScene2D scene = buildScene(PuppeteerLauncherPanel.resolveSnapshot(source, 3));
    Entity2D background = scene.find("bg_current");
    Entity2D crowd = scene.find("crowd_milling_crowd");

    assertNotNull(background);
    assertNotNull(crowd);
    assertEquals(-5.0, crowd.getZ(), 1e-9);
    assertTrue(background.getZ() < crowd.getZ(),
        "VNS backgrounds must stay behind negative-z scene entities in Puppeteer");
  }

  @Test
  void inlineTimelineGroupTransformReachesEveryVisibleMemberButNotSiblingLayers() throws Exception {
    String source = """
        @charlayer hero body assets/hero/body.png
        @charlayer hero eyes assets/hero/eyes.png
        @charlayer hero mouth assets/hero/mouth.png
        @chargroup hero face pivot=0.5,1 $eyes | $mouth
        @charpreset hero neutral $body | $eyes | $mouth
        [show hero center neutral]
        timeline {
          parallel {
            move "hero_face" { x: 125 dur: 0 }
            rotate "hero_face" { deg: 12 dur: 0 }
          }
        }
        Narrator: between
        timeline {
          parallel {
            move "hero_face" {
              x: 230
              dur: 0
            }
            rotate "hero_face" {
              deg: -7
              dur: 0
            }
          }
        }
        Narrator: done
        """;
    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(
        source, source.split("\n", -1).length - 1);
    EditorApp app = new EditorApp();
    JesScene2D scene = buildScene(app, snapshot);
    Entity2D body = scene.find("hero_body");
    Entity2D eyes = scene.find("hero_eyes");
    Entity2D mouth = scene.find("hero_mouth");

    assertNotNull(body);
    assertNotNull(eyes);
    assertNotNull(mouth);
    assertNotNull(scene.find("hero_face"), "chargroup target must be registered in the launch scene");
    double bodyX = body.getX();
    double eyesX = eyes.getX();
    double mouthX = mouth.getX();

    Method captureBaselines = EditorApp.class.getDeclaredMethod(
        "captureRuntimeExportBaselines", JesScene2D.class);
    captureBaselines.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, Map<?, ?>> baselines = (Map<String, Map<?, ?>>) captureBaselines.invoke(app, scene);
    Method applyEndState = EditorApp.class.getDeclaredMethod(
        "applySnapshotTimelineEndStateToScene",
        JesScene2D.class,
        PuppeteerLauncherPanel.SceneSnapshot.class,
        String.class,
        Map.class);
    applyEndState.setAccessible(true);
    applyEndState.invoke(app, scene, snapshot, null, baselines);

    assertEquals(bodyX, body.getX(), 1e-9, "non-member body layer must not inherit face transform");
    assertEquals(eyesX + 230.0, eyes.getX(), 1e-9,
        "later VN offsets must rebase from the original scene, not accumulate on earlier timelines");
    assertEquals(mouthX + 230.0, mouth.getX(), 1e-9);
    assertEquals(-7.0, eyes.getRotationDeg(), 1e-9);
    assertEquals(-7.0, mouth.getRotationDeg(), 1e-9);
  }

  @Test
  void overlappingGroupsUseTheSameActiveChainAsTheCanonicalRenderer() throws Exception {
    String source = """
        @charlayer hero body assets/hero/body.png
        @charlayer hero eyes assets/hero/eyes.png
        @chargroup hero whole pivot=0.5,1 $body | $eyes
        @chargroup hero face pivot=0.5,1 $eyes
        @charpreset hero neutral $body | $eyes
        [show hero center neutral]
        timeline {
          move "hero_face" { x: 125 dur: 0 }
        }
        Narrator: done
        """;
    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(
        source, source.split("\n", -1).length - 1);
    assertEquals(List.of("whole", "face"), snapshot.resolveCharacterLayerGroups("hero", "neutral")
        .stream().map(group -> group.groupId).toList());
    EditorApp app = new EditorApp();
    JesScene2D scene = buildScene(app, snapshot);
    Entity2D eyes = scene.find("hero_eyes");
    assertNotNull(eyes);
    double originalX = eyes.getX();

    Method applyEndState = EditorApp.class.getDeclaredMethod(
        "applySnapshotTimelineEndStateToScene",
        JesScene2D.class,
        PuppeteerLauncherPanel.SceneSnapshot.class,
        String.class,
        Map.class);
    applyEndState.setAccessible(true);
    applyEndState.invoke(app, scene, snapshot, null, Map.of());

    assertEquals(originalX, eyes.getX(), 1e-9,
        "an overlapping group must not affect a layer outside the runtime's active chain");
  }

  @Test
  void launchSceneUsesProjectCharacterHeightAndBaselineFraming() throws Exception {
    Files.createDirectories(tempDir.resolve("config/ui"));
    Files.writeString(tempDir.resolve("jvn.project"), """
        width=1920
        height=1080
        dialogueLayout=config/ui/dialogue.layout
        """);
    Files.writeString(tempDir.resolve("config/ui/dialogue.layout"), """
        characterHeightFactor=1.0
        characterBaselineY=1.325
        """);
    String source = """
        @charimg hero neutral assets/hero.png
        [show hero center neutral]
        """;
    EditorApp app = new EditorApp();
    Field projectRoot = EditorApp.class.getDeclaredField("projectRoot");
    projectRoot.setAccessible(true);
    projectRoot.set(app, tempDir.toFile());

    JesScene2D scene = buildScene(app, PuppeteerLauncherPanel.resolveSnapshot(source, 1));
    Sprite2D hero = (Sprite2D) scene.find("hero");

    assertNotNull(hero);
    assertEquals(1080.0, hero.getHeight(), 1e-9);
    assertEquals(1431.0, hero.getY(), 1e-9);
  }

  @Test
  void repeatedLayersFollowGroupTransformsAndStayBehindEqualZForeground() throws Exception {
    String source = """
        @charlayer hero body body.png
        @chargroup hero rig pivot=0.5,1 $body
        @charpreset hero neutral $body | $body
        @charimg booth neutral booth.png
        [show hero center neutral z=2]
        [show booth center neutral z=2]
        timeline {
          move "hero_rig" {
            x: 120
            dur: 0
          }
        }
        Narrator: done
        """;
    var snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, source.split("\n", -1).length - 1);
    EditorApp app = new EditorApp();
    var scene = buildScene(app, snapshot);
    var occurrences = PuppeteerLauncherPanel.snapshotLayerOccurrenceNames(snapshot, snapshot.characters.get(0), "body");
    assertEquals(2, occurrences.size());
    Entity2D first = scene.find(occurrences.get(0));
    Entity2D second = scene.find(occurrences.get(1));
    double baseX = first.getX();
    assertEquals(scene.find("booth").getZ(), second.getZ());
    Method capture = EditorApp.class.getDeclaredMethod("captureRuntimeExportBaselines", JesScene2D.class);
    capture.setAccessible(true);
    Method replay = EditorApp.class.getDeclaredMethod("applySnapshotTimelineEndStateToScene", JesScene2D.class,
        PuppeteerLauncherPanel.SceneSnapshot.class, String.class, Map.class);
    replay.setAccessible(true);
    replay.invoke(app, scene, snapshot, null, capture.invoke(app, scene));
    assertEquals(baseX + 120, first.getX(), 1e-9);
    assertEquals(first.getX(), second.getX(), 1e-9);
    assertTrue(scene.getChildren().indexOf(second) < scene.getChildren().indexOf(scene.find("booth")));
  }

  @Test
  void nestedParentAndChildTransformsComposeAcrossTimelineHistory() throws Exception {
    String source = """
        @charlayer hero eyes eyes.png
        @charlayer hero body body.png
        @chargroup hero face parent=head pivot=0.5,1 $eyes
        @chargroup hero head pivot=0.5,1 $body
        @charpreset hero neutral $eyes
        [show hero center neutral]
        timeline {
          parallel {
            move "hero_head" { x: 10 dur: 0 }
            rotate "hero_head" { deg: 90 dur: 0 }
          }
        }
        Narrator: next
        timeline {
          move "hero_face" { x: 20 dur: 0 }
        }
        Narrator: done
        """;
    var snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, source.split("\n", -1).length - 1);
    var app = new EditorApp();
    var scene = buildScene(app, snapshot);
    var eyes = scene.find("hero_eyes");
    double x = eyes.getX(), y = eyes.getY();
    Method capture = EditorApp.class.getDeclaredMethod("captureRuntimeExportBaselines", JesScene2D.class);
    capture.setAccessible(true);
    Method replay = EditorApp.class.getDeclaredMethod("applySnapshotTimelineEndStateToScene", JesScene2D.class,
        PuppeteerLauncherPanel.SceneSnapshot.class, String.class, Map.class);
    replay.setAccessible(true);
    replay.invoke(app, scene, snapshot, null, capture.invoke(app, scene));
    assertEquals(x + 10, eyes.getX(), 1e-9);
    assertEquals(y + 20, eyes.getY(), 1e-9);
    assertEquals(0, eyes.getMatrixMxx(), 1e-9);
    assertEquals(-1, eyes.getMatrixMxy(), 1e-9);
    assertEquals(1, eyes.getMatrixMyx(), 1e-9);
  }

  @Test
  void viewportSizedCharacterArtworkStillUsesDeclaredScale() throws Exception {
    Path image = tempDir.resolve("wide.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(192, 108, java.awt.image.BufferedImage.TYPE_INT_ARGB),
        "png", image.toFile());
    Files.writeString(tempDir.resolve("jvn.project"), "width=1920\nheight=1080\n");
    String source = "@character hero \"Hero\" scale=1.5\n@charimg hero neutral " + image + "\n[show hero center neutral]\n";
    var snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 2);
    var app = new EditorApp();
    Field root = EditorApp.class.getDeclaredField("projectRoot");
    root.setAccessible(true);
    root.set(app, tempDir.toFile());
    Sprite2D hero = (Sprite2D) buildScene(app, snapshot).find("hero");
    assertEquals(1620, hero.getHeight(), 1e-9);
    assertEquals(2880, hero.getWidth(), 1e-9);
  }

  private static JesScene2D buildScene(PuppeteerLauncherPanel.SceneSnapshot snapshot) throws Exception {
    return buildScene(new EditorApp(), snapshot);
  }

  private static JesScene2D buildScene(
      EditorApp app, PuppeteerLauncherPanel.SceneSnapshot snapshot) throws Exception {
    Method method = EditorApp.class.getDeclaredMethod(
        "buildSceneFromSnapshot", PuppeteerLauncherPanel.SceneSnapshot.class);
    method.setAccessible(true);
    return (JesScene2D) method.invoke(app, snapshot);
  }
}
