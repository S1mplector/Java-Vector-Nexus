package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.core.vn.stage.VnStagePreset.LightBlend;
import com.jvn.core.vn.stage.VnStagePreset.LightFalloff;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TabPane;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FxToolkitExtension.class)
class SceneLightingStudioFxTest {
  @TempDir Path project;

  @Test
  void loadApplyReopenAndUpdateSceneLighting() throws Exception {
    Path script = Files.createDirectories(project.resolve("scripts")).resolve("opening.vns");
    Files.writeString(script, "@label start\nNarrator: A scene beat.\n[end]\n");
    FxToolkit.runFx(() -> {
      FileEditorTab scriptTab = new FileEditorTab(script.toFile());
      ImageTintToolView studio = new ImageTintToolView();
      Scene scene = new Scene(studio, 1260, 780);
      EditorTheme.apply(scene);
      studio.setProjectRoot(project.toFile());
      studio.setActiveScriptSupplier(() -> scriptTab);
      scriptTab.navigateToLine(2);
      render(studio);
      button(studio, "lighting-load-vns").fire();
      render(studio);
      assertEquals("Rig", ((TabPane) studio.lookup("#lighting-inspector")).getSelectionModel().getSelectedItem().getText());
      button(studio, "lighting-add-light").fire();
      render(studio);
      setValue(studio, "lighting-blend", LightBlend.SCREEN);
      setValue(studio, "lighting-falloff", LightFalloff.SMOOTH);
      button(studio, "lighting-apply-vns").fire();
      assertFalse(scriptTab.isDirty());
      String linkedSource = Files.readString(script);
      assertTrue(linkedSource.contains("@stagepreset opening_start lighting/opening_start.stagepreset"), linkedSource);
      assertTrue(linkedSource.indexOf("[stage opening_start]") < linkedSource.indexOf("A scene beat"), linkedSource);
      Path presetFile = script.resolveSibling("lighting/opening_start.stagepreset");
      assertTrue(Files.isRegularFile(presetFile));
      try (var input = Files.newInputStream(script)) {
        var scenario = new VnScriptParser().parse(input, "scripts/opening.vns", path -> Files.newInputStream(project.resolve(path)));
        var preset = scenario.getStagePreset("opening_start");
        assertNotNull(preset);
        assertEquals(LightBlend.SCREEN, preset.getLights().getFirst().blend());
        assertEquals(LightFalloff.SMOOTH, preset.getLights().getFirst().falloff());
      }
      button(studio, "lighting-load-vns").fire();
      render(studio);
      assertEquals(LightBlend.SCREEN, ((ComboBox<?>) studio.lookup("#lighting-blend")).getValue());
      setValue(studio, "lighting-blend", LightBlend.MULTIPLY);
      button(studio, "lighting-apply-vns").fire();
      assertEquals(linkedSource, Files.readString(script));
      assertTrue(Files.readString(presetFile).contains("light.0.blend=multiply"));
      String output = System.getenv("JVN_LIGHTING_TEST_PREVIEW");
      if (output != null) {
        ImageIO.write(SwingFXUtils.fromFXImage(studio.snapshot(null, null), null), "png", Path.of(output).toFile());
      }
      studio.dispose();
      return null;
    });
  }

  private static void render(ImageTintToolView studio) {
    studio.applyCss();
    studio.layout();
  }

  private static Button button(ImageTintToolView studio, String id) {
    Button button = (Button) studio.lookup("#" + id);
    assertNotNull(button, id);
    return button;
  }

  @SuppressWarnings("unchecked")
  private static <T> void setValue(ImageTintToolView studio, String id, T value) {
    ComboBox<T> box = (ComboBox<T>) studio.lookup("#" + id);
    assertNotNull(box, id);
    box.setValue(value);
  }
}
