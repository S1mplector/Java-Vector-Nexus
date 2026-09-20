package com.jvn.editor.ui;

import com.jvn.core.assets.*;
import com.jvn.core.vn.*;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.vn.VnRenderer;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import jdk.jfr.Recording;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in replay of the reported scene using real project assets and JavaFX rendering. */
class LightningRenderPerformanceTest {
  @Test void replayAnimatedLightningSection() throws Exception {
    String root = System.getenv().getOrDefault("JVN_LIGHTNING_ROOT", "");
    assumeTrue(!root.isBlank(), "Set JVN_LIGHTNING_ROOT to the Was_I_Write checkout");
    assumeTrue(FxToolkit.ensureStarted());
    Path project = Path.of(root).toAbsolutePath();
    AssetCatalog.setDefaultManager(new OverlayAssetManager(new FilesystemAssetManager(project), new ClasspathAssetManager()));
    VnScenario scenario = new VnScenarioLoader().load("scripts/story/620-686-lightning.vns");
    VnCharacterSceneAccessor accessor = new VnCharacterSceneAccessor();
    DefaultVnInterop interop = new DefaultVnInterop();
    interop.setSceneAccessor(accessor);
    VnScene scene = new VnScene(scenario);
    scene.setInterop(interop);
    scene.onEnter();
    for (int i = 0; i < 10000; i++) {
      VnNode node = scene.getState().getCurrentNode();
      if (node != null && node.getSourceLine() >= 263) break;
      if (node != null && node.getType() == VnNodeType.DIALOGUE) scene.advance();
      else scene.update(10000);
    }
    assertTrue(scene.getState().getCurrentNode().getSourceLine() >= 263);
    scene.getState().setUiHidden(true);
    var canvas = FxToolkit.runFx(() -> new Canvas(1280, 720));
    var renderer = FxToolkit.runFx(() -> {
      VnRenderer r = new VnRenderer(canvas.getGraphicsContext2D());
      r.setProjectRoot(project.toFile());
      r.setTimelineAccessor(accessor);
      return r;
    });
    WritableImage snapshot = new WritableImage(1280,720);
    double[] times = new double[180];
    Set<Double> animatedPositions = new HashSet<>();
    Path out = Path.of(System.getenv().getOrDefault("JVN_LIGHTNING_OUTPUT", "build/reports/lightning"));
    Files.createDirectories(out);
    try (Recording recording = new Recording(jdk.jfr.Configuration.getConfiguration("profile"))) {
      recording.start();
      for (int i = -20; i < times.length; i++) {
        final int frame = i;
        double elapsed = FxToolkit.runFx(() -> {
          if (frame == 0) scene.advance();
          long start = System.nanoTime();
          if (frame >= 0) {
            scene.update(16);
            var head = accessor.getProxy("wendi_head_orientation");
            if (head != null) animatedPositions.add(head.getX());
          }
          renderer.render(scene.getState(), scenario, 1280,720);
          canvas.snapshot(null, snapshot); // Flush actual render work, not just queued Canvas commands.
          return (System.nanoTime()-start)/1_000_000.0;
        });
        if (i >= 0) times[i] = elapsed;
      }
      recording.stop();
      recording.dump(out.resolve("render.jfr"));
    }
    javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(snapshot,null),"png",out.resolve("frame.png").toFile());
    assertTrue(animatedPositions.size() > 1, "Replay must execute the authored head/body moves: " + animatedPositions);
    System.out.println("Distinct animated head positions: " + animatedPositions.size());
    FxToolkit.runFx(renderer::dispose);
    AssetCatalog.setDefaultManager(new ClasspathAssetManager());
    Arrays.sort(times);
    String report=String.format(Locale.ROOT,"frames=%d median=%.2fms p95=%.2fms max=%.2fms%n", times.length,times[90],times[171],times[179]);
    System.out.println(report);
    Files.writeString(out.resolve("timing.txt"),report);
  }
}
