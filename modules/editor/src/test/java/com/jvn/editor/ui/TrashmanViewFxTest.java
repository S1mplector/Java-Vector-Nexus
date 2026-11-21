package com.jvn.editor.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class TrashmanViewFxTest {
  @Test void cacheReleaseRunsOnFxAndCanBeRetriedAfterFailure() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    TrashmanView panel = FxToolkit.runFx(() -> {
      TrashmanView view = new TrashmanView(() -> {
        assertTrue(Platform.isFxApplicationThread());
        if (attempts.getAndIncrement() == 0) throw new IllegalStateException("cache busy");
        return 0;
      });
      new Scene(view, 300, 600);
      view.applyCss();
      view.layout();
      ((TabPane) view.getCenter()).getSelectionModel().select(1);
      view.applyCss();
      return view;
    });
    try {
      awaitStatus(panel, s -> s.contains("PID"), () -> {});
      Runnable release = () -> {
        Button button = panel.lookupAll(".button").stream().filter(n -> n instanceof Button b
            && b.getText().equals("Release preview caches")).map(Button.class::cast).findFirst().orElseThrow();
        assertFalse(button.isDisabled());
        button.fire();
        assertTrue(button.isDisabled());
      };
      awaitStatus(panel, s -> s.contains("cache busy"), release);
      awaitStatus(panel, s -> s.contains("No open previews"), release);
      assertEquals(2, attempts.get());
    } finally {
      FxToolkit.runFx(panel::dispose);
    }
  }

  private static void awaitStatus(TrashmanView panel, Predicate<String> expected, Runnable action) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    FxToolkit.runFx(() -> {
      Label status = (Label) panel.lookup(".tm-status");
      status.textProperty().addListener((o, old, value) -> {
        if (expected.test(value)) done.countDown();
      });
      action.run();
      if (expected.test(status.getText())) done.countDown();
    });
    assertTrue(done.await(8, TimeUnit.SECONDS), () -> "Waiting for Trashman status");
  }

  @Test void chartKeepsGapsAndExpiresOldMeasurements() throws Exception {
    FxToolkit.runFx(() -> {
      TrashmanHeapChart chart = new TrashmanHeapChart();
      chart.resize(320, 150);
      chart.add(1000, 5, 10);
      chart.add(3000, 7, 10);
      chart.add(20_000, 6, 10);
      chart.layout();
      Path used = (Path) chart.lookup(".tm-used-line");
      assertEquals(2, used.getElements().stream().filter(MoveTo.class::isInstance).count());
      chart.add(150_000, 8, 12);
      chart.layout();
      assertEquals(2, used.getElements().size());
      assertEquals("2 min ago", chart.windowLabel().get());
    });
  }

  @Test void classRankingSkipsJvmHeadersAndPreservesArrayTypes() {
    String rows = """
        123:
         num     #instances       #bytes  class name (module)
        -----------------------------------------------------
           1:          2400        32000  [B (java.base@21)
           2:            11          264  java.lang.String (java.base@21)
        Total          2411        32264
        """;
    var classes = TrashmanTelemetry.largestClasses(rows);
    assertEquals(2, classes.size());
    assertEquals(new TrashmanTelemetry.ClassEntry("[B", 2400, 32000), classes.get(0));
    assertEquals("java.lang.String", classes.get(1).name());
  }
}
