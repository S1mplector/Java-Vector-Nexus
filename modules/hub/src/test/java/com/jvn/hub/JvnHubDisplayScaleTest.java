package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import javax.swing.ToolTipManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JvnHubDisplayScaleTest {
  @TempDir Path tempDir;

  @Test
  void retinaLogicalResolutionDoesNotReceiveASecondScaleFactor() {
    assertEquals(1.0, JvnHub.automaticScaleForDisplay(1470, 956, 72));
  }

  @Test
  void highResolutionDisplayStillReceivesAReadableAutomaticScale() {
    assertEquals(1.45, JvnHub.automaticScaleForDisplay(3840, 2160, 96));
  }

  @Test
  void compactAndDefaultPresetsResizeTheWholeHubFootprint() {
    assertEquals(new Dimension(480, 488), JvnHub.hubSizeForScale(0.75));
    assertEquals(new Dimension(640, 650), JvnHub.hubSizeForScale(1.0));
  }

  @Test
  void explicitScaleIsClampedToSupportedBounds() {
    assertEquals(new Dimension(480, 488), JvnHub.hubSizeForScale(0.1));
    assertEquals(new Dimension(1184, 1203), JvnHub.hubSizeForScale(3.0));
  }

  @Test
  void resizeOverlayReportsClampedPixelDimensions() {
    assertEquals("1280 × 720 px", JvnHub.formatWindowPixels(1280, 720));
    assertEquals("0 × 480 px", JvnHub.formatWindowPixels(-1, 480));
  }

  @Test
  void customScaleAcceptsPercentFactorAndLocaleDecimalForms() {
    assertEquals(1.25, JvnHub.parseCustomUiScale("125"));
    assertEquals(1.25, JvnHub.parseCustomUiScale("125%"));
    assertEquals(1.25, JvnHub.parseCustomUiScale("1.25"));
    assertEquals(1.25, JvnHub.parseCustomUiScale("1,25"));
    assertTrue(Double.isNaN(JvnHub.parseCustomUiScale("74%")));
    assertTrue(Double.isNaN(JvnHub.parseCustomUiScale("200%")));
    assertTrue(Double.isNaN(JvnHub.parseCustomUiScale("large")));
  }

  @Test
  void performanceVisibilityPreferencesAcceptCommonBooleanForms() {
    Properties properties = new Properties();
    properties.setProperty("performance.graph.visible", "yes");
    properties.setProperty("performance.chips.visible", "off");
    properties.setProperty("tooltips.enabled", "disabled");

    assertTrue(JvnHub.booleanPreference(properties, "performance.graph.visible", false));
    assertFalse(JvnHub.booleanPreference(properties, "performance.chips.visible", true));
    assertFalse(JvnHub.booleanPreference(properties, "tooltips.enabled", true));
    assertTrue(JvnHub.booleanPreference(properties, "missing", true));
  }

  @Test
  void tooltipVisibilityControlsTheSharedSwingManager() {
    boolean previous = ToolTipManager.sharedInstance().isEnabled();
    try {
      JvnHub.configureToolTipManager(false);
      assertFalse(ToolTipManager.sharedInstance().isEnabled());
      JvnHub.configureToolTipManager(true);
      assertTrue(ToolTipManager.sharedInstance().isEnabled());
    } finally {
      JvnHub.configureToolTipManager(previous);
    }
  }

  @Test
  void sourceBuildIndicatorUsesTheHubOrangeAccent() {
    String label = JvnHub.formatVersionLabel("0.4.3");
    if (label.startsWith("<html>")) {
      org.junit.jupiter.api.Assertions.assertTrue(label.contains("color:#ff9933"));
      org.junit.jupiter.api.Assertions.assertTrue(label.contains("Running from source"));
    }
  }

  @Test
  void engineModuleInventoryIncludesOnlyConfiguredModules() throws Exception {
    Path modules = Files.createDirectories(tempDir.resolve("modules"));
    Files.createDirectories(modules.resolve("core"));
    Files.writeString(modules.resolve("core").resolve("build.gradle.kts"), "");
    Files.createDirectories(modules.resolve("runtime"));
    Files.writeString(modules.resolve("runtime").resolve("build.gradle"), "");
    Files.createDirectories(modules.resolve("notes"));

    assertEquals(List.of("core", "runtime"), JvnHub.discoverEngineModules(tempDir));
  }
}
