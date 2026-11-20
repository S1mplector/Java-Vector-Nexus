package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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

  @Test
  void outlinerKeepsSelectionAcrossLayoutsAndNudgesOnlyInEditMode() throws Exception {
    Path script = Files.createDirectories(project.resolve("scripts")).resolve("framing.vns");
    Files.writeString(script, "@label start\nNarrator: A scene beat.\n[end]\n");
    FxToolkit.runFx(() -> {
      var scriptTab = new FileEditorTab(script.toFile());
      var studio = new ImageTintToolView();
      try {
        EditorTheme.apply(new Scene(studio, 1260, 780));
        studio.setProjectRoot(project.toFile());
        studio.setActiveScriptSupplier(() -> scriptTab);
        scriptTab.navigateToLine(2);
        render(studio);
        button(studio, "lighting-load-vns").fire();
        button(studio, "lighting-add-light").fire();
        render(studio);
        var lights = (ListView<?>) studio.lookup("#lighting-light-list");
        var selection = lights.getSelectionModel().getSelectedItem();
        assertNotNull(selection);
        assertSame(lights, studio.lookup("#lighting-outliner").lookup("#lighting-light-list"));
        studio.resize(620, 780);
        render(studio);
        assertNull(studio.lookup("#lighting-outliner"));
        assertSame(lights, studio.lookup("#lighting-inspector").lookup("#lighting-light-list"));
        assertSame(selection, lights.getSelectionModel().getSelectedItem());
        studio.resize(1320, 780);
        render(studio);
        assertSame(lights, studio.lookup("#lighting-outliner").lookup("#lighting-light-list"));

        Canvas canvas = (Canvas) studio.lookup("#lighting-viewport");
        button(studio, "lighting-apply-vns").fire();
        var file = script.resolveSibling("lighting/framing_start.stagepreset");
        double initialX = lightX(file);
        var mode = (ComboBox<?>) studio.lookup("#lighting-preview-mode");
        chooseMode(mode, "Split");
        assertTrue(studio.lookup("#lighting-compare-position").isVisible());
        nudgeRight(canvas);
        button(studio, "lighting-apply-vns").fire();
        assertEquals(initialX, lightX(file));
        chooseMode(mode, "Edit");
        nudgeRight(canvas);
        button(studio, "lighting-apply-vns").fire();
        assertEquals(initialX + 10 / canvas.getWidth(), lightX(file), 0.001);
      } finally {
        studio.dispose();
      }
      return null;
    });
  }

  @Test
  void splitViewportShowsOriginalAndGradedPixelsAtIdenticalFraming() throws Exception {
    Path scripts = Files.createDirectories(project.resolve("scripts"));
    Path script = scripts.resolve("split.vns");
    Files.writeString(script, """
        @stagepreset compare look.stagepreset
        @label start
        [stage compare]
        Narrator: A scene beat.
        [end]
        """);
    Files.writeString(scripts.resolve("look.stagepreset"), """
        lights=1
        light.0.layer=foreground
        light.0.shape=ambient
        light.0.color=#ff6700
        light.0.intensity=0.9
        """);
    FxToolkit.runFx(() -> {
      var studio = new ImageTintToolView();
      try {
        var tab = new FileEditorTab(script.toFile());
        EditorTheme.apply(new Scene(studio, 1260, 780));
        studio.setProjectRoot(project.toFile());
        studio.setActiveScriptSupplier(() -> tab);
        tab.navigateToLine(4);
        render(studio);
        button(studio, "lighting-load-vns").fire();
        render(studio);
        var mode = (ComboBox<?>) studio.lookup("#lighting-preview-mode");
        Canvas canvas = (Canvas) studio.lookup("#lighting-viewport");
        chooseMode(mode, "Original");
        Image original = canvas.snapshot(null, null);
        chooseMode(mode, "Graded");
        Image graded = canvas.snapshot(null, null);
        chooseMode(mode, "Split");
        var position = (Slider) studio.lookup("#lighting-compare-position");
        int w = (int) canvas.getWidth(), y = (int) canvas.getHeight() / 2;
        for (double fraction : new double[]{0.25, 0.65}) {
          position.setValue(fraction);
          var split = canvas.snapshot(null, null).getPixelReader();
          int left = (int) (w * fraction / 2), right = (int) (w * (1 + fraction) / 2);
          assertTrue(original.getPixelReader().getArgb(right, y) != graded.getPixelReader().getArgb(right, y));
          assertEquals(original.getPixelReader().getArgb(left, y), split.getArgb(left, y));
          assertEquals(graded.getPixelReader().getArgb(right, y), split.getArgb(right, y));
        }
      } finally {
        studio.dispose();
      }
      return null;
    });
  }

  private static void nudgeRight(Canvas canvas) {
    canvas.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, true, false, false, false));
  }

  private static double lightX(Path path) throws Exception {
    var preset = new java.util.Properties();
    try (var reader = Files.newBufferedReader(path)) { preset.load(reader); }
    return Double.parseDouble(preset.getProperty("light.0.position").split(",")[0]);
  }

  private static void chooseMode(ComboBox<?> box, String name) {
    int index = box.getItems().stream().map(Object::toString).toList().indexOf(name);
    assertTrue(index >= 0);
    box.getSelectionModel().select(index);
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
