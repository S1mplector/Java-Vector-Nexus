package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FxToolkitExtension.class)
class AssetBrowserViewFxTest {
  // 1x1 transparent PNG, so JavaFX's Image decoder succeeds and releases its file handle
  // immediately instead of leaving it open after a failed decode of bogus placeholder bytes,
  // which raced @TempDir cleanup on Windows.
  private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

  @Test
  void selectingOnlyPreviewsAndUseAssetIsExplicit(@TempDir Path project) throws Exception {
    Files.createDirectories(project.resolve("assets/characters"));
    Files.write(project.resolve("assets/characters/hero.png"), ONE_PIXEL_PNG);

    AssetBrowserView view = runFx(() -> {
      AssetBrowserView v = new AssetBrowserView();
      AtomicInteger uses = new AtomicInteger();
      v.setOnAssetSelected(path -> uses.incrementAndGet());
      v.setProjectRoot(project.toFile());
      new Scene(v, 800, 600);
      v.applyCss();
      v.layout();

      @SuppressWarnings("unchecked")
      ListView<Object> assets = (ListView<Object>) v.lookup("#asset-browser-list-view");
      assets.getSelectionModel().select(0);
      assertEquals(0, uses.get(), "browsing must not insert an asset path");

      Button use = v.lookupAll(".button").stream()
          .filter(Button.class::isInstance)
          .map(Button.class::cast)
          .filter(button -> "Use Asset".equals(button.getText()))
          .findFirst()
          .orElseThrow();
      assertFalse(use.isDisabled());
      use.fire();
      assertEquals(1, uses.get());
      return v;
    });

    // The preview thumbnail loads the selected asset's bytes off-thread; wait for it to
    // finish before the @TempDir cleanup runs, otherwise Windows can still hold the file open.
    for (int i = 0; i < 40; i++) {
      Image image = runFx(() -> ((ImageView) view.lookup("#asset-browser-preview-image")).getImage());
      if (image == null || image.getProgress() >= 1.0) return;
      Thread.sleep(50);
    }
  }

  @Test
  void autoLabelDashboardHeadlesslyInventoriesProjectAssets(@TempDir Path project) throws Exception {
    Files.createDirectories(project.resolve("assets/backgrounds"));
    Files.write(project.resolve("assets/backgrounds/school.png"), ONE_PIXEL_PNG);

    AssetBrowserView view = runFx(() -> {
      AssetBrowserView created = new AssetBrowserView();
      created.setProjectRoot(project.toFile());
      created.showAutoLabelDashboard();
      new Scene(created, 1100, 800);
      created.applyCss();
      created.layout();
      return created;
    });

    for (int i = 0; i < 80; i++) {
      int count = runFx(() -> {
        ListView<?> table = (ListView<?>) view.lookup("#asset-auto-label-table");
        assertNotNull(table, "Auto-label dashboard table should be attached to the scene");
        return table.getItems().size();
      });
      if (count == 1) return;
      Thread.sleep(50);
    }
    assertEquals(1, runFx(() ->
        ((ListView<?>) view.lookup("#asset-auto-label-table")).getItems().size()));
  }

  @Test
  void dashboardGeneratesReviewedVnsAndKeepsResultVisible(@TempDir Path project) throws Exception {
    Files.createDirectories(project.resolve("assets/backgrounds"));
    Files.createDirectories(project.resolve("scripts"));
    Files.write(project.resolve("assets/backgrounds/school.png"), ONE_PIXEL_PNG);
    Files.writeString(project.resolve("jvn.project"), "entryVns=scripts/main.vns\n");
    Files.writeString(project.resolve("scripts/main.vns"), "@scenario school\n");
    AssetAutoLabelDashboardView view = runFx(() -> {
      var created = new AssetAutoLabelDashboardView();
      created.setProjectRoot(project.toFile());
      new Scene(created, 1100, 800);
      created.applyCss();
      created.layout();
      return created;
    });
    boolean generated = false;
    for (int i = 0; i < 100; i++) {
      if (runFx(() -> !((ListView<?>) view.lookup("#asset-auto-label-table")).getItems().isEmpty())) {
        runFx(() -> {
          Button generate = view.lookupAll(".button").stream().filter(Button.class::isInstance)
              .map(Button.class::cast).filter(button -> "Generate VNS".equals(button.getText()))
              .findFirst().orElseThrow();
          assertFalse(generate.isDisabled());
          generate.fire();
          return null;
        });
        generated = true;
        break;
      }
      Thread.sleep(50);
    }
    org.junit.jupiter.api.Assertions.assertTrue(generated, "Dashboard scan should finish");
    org.junit.jupiter.api.Assertions.assertTrue(Files.readString(project.resolve(
        AssetAutoLabelService.AUTO_DECLARATIONS_PATH)).contains("@background school"));
    for (int i = 0; i < 100; i++) {
      if (runFx(() -> view.lookupAll(".label").stream()
          .filter(javafx.scene.control.Label.class::isInstance)
          .map(javafx.scene.control.Label.class::cast)
          .anyMatch(label -> label.getText().contains("Reviewed VNS declaration saved.")))) return;
      Thread.sleep(50);
    }
    org.junit.jupiter.api.Assertions.fail("Refresh must retain the generation result");
  }

  private static <T> T runFx(Callable<T> callable) throws Exception {
    return FxToolkit.runFx(callable);
  }
}
