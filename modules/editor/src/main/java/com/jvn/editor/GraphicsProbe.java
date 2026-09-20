package com.jvn.editor;

import com.jvn.core.diagnostics.GraphicsPipeline;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;

/** Short, separate-process check of the real JavaFX renderer used by managed launches. */
public final class GraphicsProbe {
  private GraphicsProbe() {}

  public static void main(String[] args) throws Exception {
    GraphicsPipeline.configure();
    System.setProperty("prism.verbose", "true");
    CountDownLatch ready = new CountDownLatch(1);
    Platform.startup(() -> {
      try {
        System.out.println("[render-check] OS: " + System.getProperty("os.name"));
        System.out.println("[render-check] " + GraphicsPipeline.statusText());
        System.out.println("[render-check] Hardware features: "
            + Platform.isSupported(ConditionalFeature.SCENE3D));
        System.out.println("[render-check] Preview FPS override: "
            + System.getProperty("jvn.editor.previewMaxFps", "automatic"));
      } finally {
        ready.countDown();
      }
    });
    try {
      if (!ready.await(15, TimeUnit.SECONDS)) {
        throw new IllegalStateException("JavaFX renderer initialization timed out");
      }
    } finally {
      Platform.exit();
    }
  }
}
