package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FxToolkitExtension.class)
class GameBuildPublisherViewFxTest {
  @TempDir Path tempDir;

  @Test
  void buildAndRunUseFlatGraphiteChromeAcrossEditorThemes() throws Exception {
    runFx(() -> {
      var original = EditorTheme.theme();
      var chrome = new Pane();
      chrome.getStyleClass().add("editor-workspace-panel");
      var editor = new Pane(chrome);
      style(new Scene(editor));
      editor.applyCss();
      var run = new RunConsoleView("JVN Runtime");
      try {
        for (Region workspace : java.util.List.of(createView(), run)) {
          var scene = new Scene(workspace);
          for (var theme : EditorTheme.Theme.values()) {
            EditorTheme.setTheme(theme);
            EditorTheme.apply(scene);
            workspace.applyCss();
            assertEquals(background(editor), background(workspace));
            assertEquals(background(chrome), background((Region) workspace.lookup(".session-header")));
            String status = workspace == run ? ".run-console-state" : ".build-publisher-badge";
            var label = (javafx.scene.control.Label) workspace.lookup(status);
            assertEquals(javafx.scene.paint.Color.TRANSPARENT, background(label));
            assertEquals(javafx.geometry.Insets.EMPTY, label.getPadding());
          }
        }
      } finally {
        run.dispose();
        EditorTheme.setTheme(original);
      }
      return null;
    });
  }

  private static Paint background(Region region) {
    return region.getBackground().getFills().getFirst().getFill();
  }

  @Test
  void validationActionsStayCompactAndWrapInNarrowLayouts() throws Exception {
    runFx(() -> {
      GameBuildPublisherView view = createView();
      Scene scene = style(new Scene(view, 760, 900));
      ((javafx.scene.control.ListView<?>) view.lookup(".build-navigation")).getSelectionModel().select(1);
      scene.getRoot().applyCss();
      scene.getRoot().layout();

      assertTrue(view.preflightButtonForTest().getGraphic().lookup(".jvn-aero-icon") instanceof AeroIcon);
      assertTrue(view.dependencyScanButtonForTest().getGraphic().lookup(".jvn-aero-icon") instanceof AeroIcon);
      assertTrue(view.preflightButtonForTest().minHeight(-1) <= 40);
      assertTrue(view.dependencyScanButtonForTest().minHeight(-1) <= 40);
      assertTrue(view.preflightButtonForTest().minWidth(-1) < 220);
      assertTrue(view.dependencyScanButtonForTest().minWidth(-1) < 220);

      view.validationActionsRowForTest().resize(220, 120);
      view.validationActionsRowForTest().layout();
      assertTrue(view.validationActionsRowForTest().prefHeight(220) > 50);
      return null;
    });
  }

  @Test
  void nativeModeControlsGuidanceVisibilityAndPlatformChecklist() throws Exception {
    runFx(() -> {
      GameBuildPublisherView view = createView();
      style(new Scene(view, 800, 900));

      view.selectPortableModeForTest();
      assertFalse(view.nativeReleaseBoxForTest().isManaged());
      assertFalse(view.nativeReleaseBoxForTest().isVisible());

      view.selectNativeModeForTest();
      assertTrue(view.nativeReleaseBoxForTest().isManaged());
      assertTrue(view.nativeReleaseBoxForTest().isVisible());
      assertTrue(view.nativeReleaseSummaryForTest().getText().contains("1."));
      assertTrue(view.nativeReleaseSummaryForTest().getText().contains("2."));
      assertTrue(view.nativeReleaseActionsRowForTest().getChildren().size() >= 5);
      return null;
    });
  }

  @Test
  void selectingPngGeneratesAndSelectsPlatformIcon() throws Exception {
    Path source = transparentPng(tempDir.resolve("game-icon.png"));
    runFx(() -> {
      GameBuildPublisherView view = createView();
      style(new Scene(view, 800, 900));
      view.installGameIcon(source.toFile());

      String configured = view.gameIconFieldForTest().getText();
      assertTrue(configured.startsWith("packaging/icon."));
      assertTrue(tempDir.resolve("game/" + configured).toFile().isFile());
      assertTrue(tempDir.resolve("game/packaging/icon-source.png").toFile().isFile());
      return null;
    });
  }

  @Test void packageOptionsReachTheActualBuildRequest() throws Exception {
    var requests = new java.util.ArrayList<GameBuildPublisherView.BuildRequest>();
    runFx(() -> {
      var view = createView(requests::add);
      var scene = style(new Scene(view, 980, 720));
      scene.getRoot().applyCss();
      scene.getRoot().layout();
      view.lookupAll(".titled-pane").stream().map(n -> (javafx.scene.control.TitledPane)n)
          .filter(pane -> pane.getText().equals("Runtime and archive options")).findFirst().orElseThrow().setExpanded(true);
      view.applyCss();
      view.layout();
      ((javafx.scene.control.CheckBox) view.lookupAll(".check-box").stream()
          .filter(n -> n instanceof javafx.scene.control.CheckBox c && c.getText().startsWith("Store ZIP"))
          .findFirst().orElseThrow()).fire();
      var ship = (javafx.scene.control.Button) view.lookupAll(".button").stream()
          .filter(n -> n instanceof javafx.scene.control.Button b && "Ship Portable".equals(b.getText()))
          .findFirst().orElseThrow();
      ship.fire();
      assertTrue(java.util.List.of(requests.getLast().args()).contains("-PjvnZipCompression=store"));
      view.selectNativeModeForTest();
      ship.fire();
      assertFalse(java.util.List.of(requests.getLast().args()).contains("-PjvnZipCompression=store"));
      return null;
    });
  }

  @Test void selectedChecksRunTogetherWithStrictWarnings() throws Exception {
    var requests = new java.util.ArrayList<GameBuildPublisherView.BuildRequest>();
    runFx(() -> {
      var view = createView(requests::add);
      var scene = style(new Scene(view, 980, 720));
      var sections = (javafx.scene.control.ListView<?>) view.lookup(".build-navigation");
      assertEquals(java.util.List.of("Package", "Checks", "Release", "Artifacts"),
          sections.getItems().stream().map(Object::toString).toList());
      sections.getSelectionModel().select(1);
      scene.getRoot().applyCss();
      scene.getRoot().layout();
      var checks = view.lookupAll(".check-box").stream().map(n -> (javafx.scene.control.CheckBox)n).toList();
      checks.stream().filter(c -> c.getText().equals("Renderer tests") || c.getText().startsWith("Fail dependency"))
          .forEach(javafx.scene.control.CheckBox::fire);
      ((javafx.scene.control.Button) view.lookupAll(".button").stream()
          .filter(n -> n instanceof javafx.scene.control.Button b && "Run selected checks".equals(b.getText()))
          .findFirst().orElseThrow()).fire();
      var request = requests.getFirst();
      assertEquals("validateJvnGameProject", request.taskName());
      assertTrue(java.util.List.of(request.args()).containsAll(java.util.List.of("validateJvnGameDependencies",
          "preflightJvnGameBuild", ":fx:test", "-PjvnFailOnWarning=true")));
      return null;
    });
  }

  private GameBuildPublisherView createView() throws Exception {
    return createView(request -> {});
  }

  private GameBuildPublisherView createView(java.util.function.Consumer<GameBuildPublisherView.BuildRequest> consumer) throws Exception {
    Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
    Path project = Files.createDirectories(tempDir.resolve("game"));
    Files.createDirectories(project.resolve("scripts/story"));
    Files.writeString(project.resolve("scripts/story/prologue.vns"), "@scene prologue\n");
    Files.writeString(project.resolve("jvn.project"), """
        name=UI Test Game
        version=1.0.0
        type=vn
        entryVns=scripts/story/prologue.vns
        runtime.ui=fx
        """);
    return new GameBuildPublisherView(workspace.toFile(), project.toFile(), consumer);
  }

  private static Scene style(Scene scene) {
    var css = GameBuildPublisherView.class.getResource("/com/jvn/editor/editor.css");
    if (css != null) scene.getStylesheets().add(css.toExternalForm());
    return scene;
  }

  private static Path transparentPng(Path path) throws Exception {
    BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(256, 256, 0xffffaa00);
    ImageIO.write(image, "png", path.toFile());
    return path;
  }

  private static <T> T runFx(Callable<T> callable) throws Exception {
    return FxToolkit.runFx(callable);
  }
}
