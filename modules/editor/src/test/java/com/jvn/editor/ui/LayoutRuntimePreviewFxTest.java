package com.jvn.editor.ui;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.fx.menu.MenuRenderer;
import com.jvn.fx.menu.MenuTheme;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.jvn.editor.ui.LayoutStudioWindowManager.Kind;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class LayoutRuntimePreviewFxTest {
  @TempDir Path project;

  @Test void nativeFrameMatchesRuntimeAndUsesOpenDependencies() throws Exception {
    Files.writeString(project.resolve("jvn.project"), "width=480\nheight=270\n");
    String menu = """
        titleText=Title
        defaultItemStyle=example
        items=start
        item.start.label=Play
        item.start.action=new_game
        """;
    Path screen = write("config/menu/menus/main.menu", menu);
    Path style = write("config/menu/styles/example.style", "backgroundColor=#303040\n");

    FxToolkit.runFx(() -> {
      Canvas game = new Canvas(480, 270);
      var assets = new AssetCatalog(new FilesystemAssetManager(project));
      var profile = MenuProfileLoader.load(assets);
      var renderer = new MenuRenderer(game.getGraphicsContext2D(), MenuTheme.fromAssets(assets, profile));
      try (var preview = new LayoutRuntimePreview(screen.toFile(), project.toFile(), Kind.MENU_SCREEN)) {
        renderer.setProjectRoot(project.toFile());
        renderer.renderMainMenu(new MainMenuScene(null, null,
            new VnSaveManager(project.resolve("test-saves").toString()), null, null, "main", profile), 480, 270);
        preview.render(menu, null, null, Map.of());
        assertArrayEquals(pixels(game), pixels(preview.frame));
        assertTrue(preview.elements.containsKey("start"));

        preview.render(menu, null, null,
            Map.of(LayoutRuntimePreview.menuPath(Kind.MENU_STYLE, style.toFile()), "backgroundColor=#884422\n"));
        assertNotEquals(pixels(game)[0], pixels(preview.frame)[0]);
        assertEquals("backgroundColor=#303040\n", Files.readString(style));

        preview.render("itemsMode=explicit\nitems=\n", null, null, Map.of());
        assertTrue(preview.screen.items().isEmpty());
        assertFalse(preview.elements.containsKey("start"));
      } finally {
        renderer.dispose();
      }
      return null;
    });
  }

  @Test void visualPatchPreservesCommentsAndChangesEffectiveValue() {
    String text = "# keep this\r\nx=1\r\nx : 2\\\r\n  3\r\nextra=value\r\n";
    String changed = LayoutPropertyDocument.edit(text, Map.of("x", "  C:\\test"));
    assertEquals("# keep this\r\nx=1\r\nx=\\  C:\\\\test\r\nextra=value\r\n", changed);
    assertEquals("  C:\\test", LayoutPropertyDocument.read(changed).getProperty("x"));
  }

  private Path write(String relative, String text) throws Exception {
    Path file = project.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
    return file;
  }

  private static int[] pixels(Canvas canvas) {
    int w = (int) canvas.getWidth(), h = (int) canvas.getHeight();
    WritableImage image = canvas.snapshot(null, null);
    int[] pixels = new int[w * h];
    image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
    return pixels;
  }
}
