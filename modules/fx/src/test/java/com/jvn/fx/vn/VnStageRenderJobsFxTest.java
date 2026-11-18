package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.assets.BoundedImageCache;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class VnStageRenderJobsFxTest {
  @Test
  void frameCanContinueWhileRelightingRunsAndPublishesTheCompletedImage() throws Exception {
    try (VnStageRenderJobs jobs = new VnStageRenderJobs()) {
      CountDownLatch workerStarted = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      BoundedImageCache<Image> cache = new BoundedImageCache<>(4);
      FxToolkit.runFx(() -> {
        jobs.submit("hero", "pose-1", new WritableImage(1, 1), cache, () -> {
          workerStarted.countDown();
          try {
            if (!release.await(4, TimeUnit.SECONDS)) throw new IllegalStateException("Worker was not released");
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
          }
          return new VnStageLightingSupport.Raster(1, 1, new int[]{0xffaabbcc});
        });
        return null;
      });
      try {
        assertTrue(workerStarted.await(4, TimeUnit.SECONDS));
        FxToolkit.runFx(() -> {
          jobs.collect();
          assertNull(cache.get("pose-1"));
          assertFalse(jobs.canSubmit("hero"));
          assertTrue(jobs.canSubmit("other character"));
          return null;
        });
      } finally {
        release.countDown();
      }
      Image rendered = null;
      for (int i = 0; i < 100 && rendered == null; i++) {
        rendered = FxToolkit.runFx(() -> {
          jobs.collect();
          return jobs.previous("hero", cache);
        });
        if (rendered == null) Thread.sleep(10);
      }
      assertTrue(rendered != null);
      assertEquals(0xffaabbcc, rendered.getPixelReader().getArgb(0, 0));
    }
  }
}
