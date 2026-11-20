package com.jvn.fx;

import com.jvn.core.diagnostics.RunTelemetry;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VnCacheInventoryTest {
  @Test void inventorySeparatesResidentSourceFromImageOnDisk(@TempDir Path root) throws Exception {
    var images = Files.createDirectories(root.resolve("assets/images"));
    Files.createFile(images.resolve("pose.png"));
    Files.createFile(images.resolve("unused.png"));
    Files.createFile(images.resolve("notes.txt"));
    VnCacheInventory.write(root, root, Map.of("Sprite / UI", List.of("pose.png")));
    var report = Files.readString(root.resolve(RunTelemetry.INVENTORY_REPORT));
    assertTrue(report.contains("Sprite / UI (1 resident)"));
    String outside = report.substring(report.indexOf("Project images outside"));
    assertTrue(outside.contains("unused.png"));
    assertFalse(outside.contains("pose.png"));
    assertFalse(outside.contains("notes.txt"));
  }
}
