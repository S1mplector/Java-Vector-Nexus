package com.jvn.editor.ui;

import com.jvn.core.assets.*;
import com.jvn.core.vn.*;
import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.fx.image.FxImageEffectCache;
import com.jvn.fx.testkit.*;
import com.jvn.fx.vn.VnRenderer;
import com.jvn.plugin.api.*;
import com.jvn.plugin.runtime.PluginHost;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javafx.embed.swing.SwingFXUtils;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class LavenderTutorialIntegrationTest {
  @TempDir Path temp;

  @Test void generatedWorkshopHasWorkingEntriesUniqueProgressAndAnimatedWhiteStage() throws Exception {
    Path project = temp.resolve("workshop");
    FxToolkit.runFx(() -> {
      NewProjectWizard wizard = new NewProjectWizard(new Stage());
      ((ComboBox<?>) field(wizard, "cmbProjectTemplate")).getSelectionModel().select(1);
      invoke(wizard, "applySelectedProjectTemplate", new Class<?>[]{});
      assertTrue(((CheckBox) field(wizard, "chkTutorialPack")).isSelected(), "Tutorial preset must enable the pack");
      ((CheckBox) field(wizard, "chkGitInit")).setSelected(false);
      invoke(wizard, "createProjectStructure", new Class<?>[]{java.io.File.class, String.class}, project.toFile(), "Lavender Workshop");
      wizard.close();
      return null;
    });
    AssetCatalog.setDefaultManager(new FilesystemAssetManager(project));
    assertTrue(Files.size(project.resolve("assets/tutorial/lofi-music-2.mp3")) > 1_000_000);
    assertTrue(Files.readString(project.resolve("config/menu/menus/main.menu")).contains("JVN v0.8.0 Tutorial"));
    String storyMap = Files.readString(project.resolve("config/story/story.storymap"));
    Map<String, Object> progress = new HashMap<>();
    for (TutorialCatalog.Lesson lesson : TutorialCatalog.LESSONS) {
      assertTrue(storyMap.contains("arc " + lesson.arc() + " script \"" + lesson.path() + "\""), storyMap);
      VnScene scene = scene(project, lesson.path());
      scene.getState().getVariables().putAll(progress);
      scene.onEnter();
      for (int tick = 0; tick < 20 && scene.getState().getCurrentNode().getType() != VnNodeType.DIALOGUE; tick++) scene.update(1000);
      assertEquals(VnNodeType.DIALOGUE, scene.getState().getCurrentNode().getType(), lesson.path());
      assertEquals("none", scene.getState().getVariables().get("character.lavender.imageEffect"));
      scene.jumpToLabel("return_hub");
      int count = ((Number) scene.getState().getVariables().get("tutorial_count")).intValue();
      scene.jumpToLabel("return_hub");
      assertEquals(count, ((Number) scene.getState().getVariables().get("tutorial_count")).intValue(), "Replays must not inflate progress");
      progress = new HashMap<>(scene.getState().getVariables());
    }
    assertEquals(30, ((Number) progress.get("tutorial_count")).intValue());
    assertTrue(Files.isRegularFile(project.resolve("config/facets/tutorial_card.facet")));
    assertTrue(Files.isRegularFile(project.resolve("config/phone/phone.properties")));
    try (PluginHost host = PluginHost.builder(PluginEnvironment.EDITOR).userDataDirectory(temp.resolve("plugin-data")).build()) {
      host.discoverAndStart();
      assertTrue(host.registries().imageEffects().find("pikupiku.lineboil").isPresent(), "Packaged service provider must be discoverable");
      ImageEffects.install(host.registries().imageEffects());
      AtomicReference<VnRenderer> renderer = new AtomicReference<>();
      AtomicReference<Canvas> canvas = new AtomicReference<>();
      VnScene scene = scene(project, "scripts/tutorial/18_plugins_and_line_boil.vns");
      scene.onEnter();
      for (int tick = 0; tick < 20 && scene.getState().getCurrentNode().getType() != VnNodeType.DIALOGUE; tick++) scene.update(1000); scene.update(1000); scene.getState().setTextRevealProgress(Integer.MAX_VALUE);
      // Exercise the optional effect explicitly; generated lessons leave it off.
      scene.getState().getVariables().put("character.lavender.imageEffect", "pikupiku.lineboil");
      FxToolkit.runFx(() -> {
        canvas.set(new Canvas(1280, 720));
        renderer.set(new VnRenderer(canvas.get().getGraphicsContext2D()));
        renderer.get().setProjectRoot(project.toFile());
      });
      try {
        for (int i = 0; i < 200; i++) {
          boolean ready = FxToolkit.runFx(() -> {
            scene.update(16);
            renderer.get().render(scene.getState(), scene.getState().getScenario(), 1280, 720);
            FxImageEffectCache cache = (FxImageEffectCache) field(renderer.get(), "imageEffects");
            return cache.status().contains("drawings ·") && scene.getState().getTransitionProgress() >= 1.0;
          });
          if (ready && i >= 35) break;
          Thread.sleep(20);
        }
        WritableImage first = FxToolkit.runFx(() -> {
          assertTrue(((FxImageEffectCache) field(renderer.get(), "imageEffects")).status().contains("drawings ·"),
              ((FxImageEffectCache) field(renderer.get(), "imageEffects")).status());
          renderer.get().updateAnimation(1000);
          renderer.get().render(scene.getState(), scene.getState().getScenario(), 1280, 720);
          return canvas.get().snapshot(null, null);
        });
        WritableImage second = FxToolkit.runFx(() -> {
          renderer.get().updateAnimation(170);
          renderer.get().render(scene.getState(), scene.getState().getScenario(), 1280, 720);
          return canvas.get().snapshot(null, null);
        });
        assertEquals(0xffffffff, first.getPixelReader().getArgb(20, 20));
        int changed = 0;
        for (int y = 40; y < 480; y++) for (int x = 400; x < 880; x++)
          if (first.getPixelReader().getArgb(x, y) != second.getPixelReader().getArgb(x, y)) changed++;
        assertTrue(changed > 50, "Lavender must actually animate in the VN renderer");
        String qa = System.getenv("JVN_TUTORIAL_QA_DIR");
        if (qa != null && !qa.isBlank()) {
          Path output = Path.of(qa); Files.createDirectories(output);
          try (var files = Files.walk(project)) {
            for (Path file : files.toList()) {
              Path target = output.resolve("project").resolve(project.relativize(file));
              if (Files.isDirectory(file)) Files.createDirectories(target);
              else Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
          }
          if (System.getenv("JVN_RENDER_PROFILE") != null) {
            try (var recording = new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"))) {
              recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(5));
              recording.start();
              List<String> timings = new ArrayList<>();
              timings.add("effect,update_ns,render_ns,flush_ns");
              WritableImage target = new WritableImage(1280, 720);
              for (String effect : List.of("none", "pikupiku.lineboil")) {
                scene.getState().getVariables().put("character.lavender.imageEffect", effect);
                for (int sample = 0; sample < 400; sample++) {
                  long[] times = FxToolkit.runFx(() -> {
                    long a = System.nanoTime(); scene.update(16); renderer.get().updateAnimation(16);
                    long b = System.nanoTime();
                    renderer.get().render(scene.getState(), scene.getScenario(), 1280, 720, -1, -1);
                    long c = System.nanoTime(); canvas.get().snapshot(null, target);
                    return new long[]{b-a, c-b, System.nanoTime()-c};
                  });
                  timings.add(effect + "," + times[0] + "," + times[1] + "," + times[2]);
                }
              }
              recording.stop(); recording.dump(output.resolve("render-profile.jfr"));
              Files.write(output.resolve("timings.csv"), timings);
            }
          }
          WritableImage tutorial = FxToolkit.runFx(() -> {
            scene.getState().getVariables().put("character.lavender.imageEffect", "none");
            renderer.get().render(scene.getState(), scene.getScenario(), 1280, 720);
            return canvas.get().snapshot(null, null);
          });
          ImageIO.write(SwingFXUtils.fromFXImage(tutorial, null), "png", output.resolve("lavender-tutorial.png").toFile());
          ImageIO.write(SwingFXUtils.fromFXImage(second, null), "png", output.resolve("lavender-tutorial-next.png").toFile());
          // Use the normal renderer for every animation preview frame.
          for (int i = 0; i < 6; i++) {
            WritableImage frame = FxToolkit.runFx(() -> {
              renderer.get().updateAnimation(167);
              renderer.get().render(scene.getState(), scene.getState().getScenario(), 1280, 720);
              return canvas.get().snapshot(null, null);
            });
            ImageIO.write(SwingFXUtils.fromFXImage(frame, null), "png", output.resolve("frame-" + i + ".png").toFile());
          }
        }
      } finally { FxToolkit.runFx(() -> renderer.get().dispose()); ImageEffects.clear(); AssetCatalog.setDefaultManager(new ClasspathAssetManager()); }
    }
  }
  private static VnScene scene(Path project, String path) throws Exception {
    VnScenario scenario;
    try (var input = Files.newInputStream(project.resolve(path))) {
      scenario = new VnScriptParser().parse(input, path, include -> {
        String normalized = include.startsWith("/") ? include.substring(1) : include;
        Path file = project.resolve(normalized);
        if (!Files.exists(file)) file = project.resolve("scripts").resolve(normalized);
        return Files.newInputStream(file);
      });
    }
    VnScene scene = new VnScene(scenario); scene.setInterop(new DefaultVnInterop()); return scene;
  }
  private static Object field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
  }
  private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
    Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args);
  }
}
