package com.jvn.editor.ui;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class StartupSplashOverlayTest {
  private static javafx.scene.SnapshotParameters transparentSnapshot() {
    var parameters = new javafx.scene.SnapshotParameters();
    parameters.setFill(javafx.scene.paint.Color.TRANSPARENT);
    return parameters;
  }

  @Test void normalStartupDisplaysStatusAndRealProgressAndResetsForRetry() throws Exception {
    FxToolkit.runFx(() -> {
      var splash = new StartupSplashOverlay();
      try {
        splash.prepareForChecks(true);
        splash.setSubtitle("Loading editor environment");
        splash.setStatus("Discovering and starting editor plugins...");
        splash.setProgress(.63);
        splash.show();
        var root = splash.rootForTest();
        root.applyCss();
        root.layout();
        assertEquals(.63, splash.progressForTest());
        assertTrue(root.lookupAll(".label").stream().filter(Label.class::isInstance)
            .map(Label.class::cast).anyMatch(label -> label.isVisible()
                && label.getText().equals("Discovering and starting editor plugins...")
                && label.getParent().isVisible()));
        String output = System.getenv("JVN_SPLASH_PREVIEW");
        if (output != null) {
          var file = new java.io.File(output);
          file.getParentFile().mkdirs();
          javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(
              root.snapshot(transparentSnapshot(), null), null), "png", file);
        }
        splash.setProgress(Double.NaN);
        assertEquals(-1, splash.progressForTest());
        splash.setProgress(2);
        assertEquals(1, splash.progressForTest());
        splash.showFailure("Cannot start", "Test failure", () -> {}, () -> {});
        splash.prepareForChecks(true);
        assertEquals(0, splash.progressForTest());
      } finally { splash.close(); }
      return null;
    });
  }

  @Test void initializationRunsInOrderAndOnlyCompletesAfterAllSteps() throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    List<String> calls = new ArrayList<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    java.util.concurrent.atomic.AtomicLong shownAt = new java.util.concurrent.atomic.AtomicLong();
    var splash = FxToolkit.runFx(() -> {
      var overlay = new StartupSplashOverlay();
      overlay.show();
      shownAt.set(System.nanoTime());
      overlay.initialize(List.of(
          new StartupSplashOverlay.InitializationStep("Preferences", () -> calls.add("preferences")),
          new StartupSplashOverlay.InitializationStep("Plugins", () -> calls.add("plugins"))
      ), () -> { calls.add("ready"); done.countDown(); }, error -> { failure.set(error); done.countDown(); });
      return overlay;
    });
    try {
      assertFalse(done.await(1, TimeUnit.SECONDS), "Fast initialization must not dismiss the splash early");
      assertTrue(FxToolkit.runFx(() -> {
        assertEquals(List.of("preferences"), calls,
            "The first initialization message should retain its share of the display time");
        assertTrue(splash.rootForTest().lookupAll(".label").stream()
            .filter(Label.class::isInstance).map(Label.class::cast)
            .anyMatch(label -> label.getText().equals("Preferences") && label.getParent().isVisible()));
        return splash.rootForTest().getScene().getWindow().isShowing();
      }));
      assertTrue(done.await(7, TimeUnit.SECONDS));
      assertTrue(System.nanoTime() - shownAt.get() >= TimeUnit.SECONDS.toNanos(5));
      assertNull(failure.get());
      assertEquals(List.of("preferences", "plugins", "ready"), calls);
      assertEquals(1, FxToolkit.runFx(splash::progressForTest));
    } finally { FxToolkit.runFx(() -> { splash.close(); return null; }); }
  }

  @Test void failedInitializationStopsLaterStepsAndDoesNotReportReady() throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    List<String> calls = new ArrayList<>();
    var splash = FxToolkit.runFx(() -> {
      var overlay = new StartupSplashOverlay();
      overlay.show();
      overlay.setProgress(.75);
      overlay.initialize(List.of(
          new StartupSplashOverlay.InitializationStep("Broken", () -> { throw new IllegalStateException("broken"); }),
          new StartupSplashOverlay.InitializationStep("Must not run", () -> calls.add("later"))
      ), () -> calls.add("ready"), error -> { calls.add(error.getMessage()); done.countDown(); });
      return overlay;
    });
    try {
      assertTrue(done.await(5, TimeUnit.SECONDS));
      assertEquals(List.of("broken"), calls);
      assertEquals(.75, FxToolkit.runFx(splash::progressForTest));
    } finally { FxToolkit.runFx(() -> { splash.close(); return null; }); }
  }
}
