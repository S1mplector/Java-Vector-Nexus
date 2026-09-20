package com.jvn.editor.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AssetAutoLabelMonitorTest {
  @Test void detectsExternalNewFilesAndRegistryDecisionsWithoutAnOpenPanel(@TempDir Path project) throws Exception {
    Files.createDirectories(project.resolve("assets/backgrounds"));
    Files.createDirectories(project.resolve("scripts"));
    Files.writeString(project.resolve("jvn.project"), "entryVns=scripts/main.vns\n");
    Files.writeString(project.resolve("scripts/main.vns"), "@scenario test\n");
    var results = new LinkedBlockingQueue<AssetAutoLabelService.ScanResult>();
    try (var monitor = new AssetAutoLabelMonitor(results::add)) {
      monitor.setProjectRoot(project.toFile());
      assertNotNull(results.poll(5, TimeUnit.SECONDS));
      assertFalse(Files.exists(project.resolve(AssetAutoLabelService.REGISTRY_PATH)), "Monitoring is read-only");
      Files.write(project.resolve("assets/backgrounds/new_room.png"), new byte[]{0});
      var detected = results.poll(7, TimeUnit.SECONDS);
      assertNotNull(detected, "External file creation triggers discovery without opening Assets");
      var asset = detected.assets().getFirst();
      assertEquals(AssetAutoLabelService.LabelStatus.SUGGESTED, asset.status());
      new AssetAutoLabelService().saveDecision(project, asset, AssetAutoLabelService.LabelStatus.IGNORED);
      var reviewed = results.poll(7, TimeUnit.SECONDS);
      assertNotNull(reviewed);
      assertEquals(AssetAutoLabelService.LabelStatus.IGNORED, reviewed.assets().getFirst().status());
    }
  }
}
