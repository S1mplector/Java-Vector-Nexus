package com.jvn.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.editor.ui.WhatsNewCatalog;

class AppBuildInfoTest {

  @Test
  void displayVersionLabelUsesFallbackForDev() {
    assertEquals("v0.9.1", AppBuildInfo.displayVersionLabel("dev"));
    assertEquals("v0.9.1", AppBuildInfo.displayVersionLabel("vdev"));
    assertEquals("v0.9.1", AppBuildInfo.displayVersionLabel(""));
  }

  @Test
  void displayVersionLabelMapsSnapshotToAlpha() {
    assertEquals("v0.9.1 Alpha", AppBuildInfo.displayVersionLabel("0.9.1-SNAPSHOT"));
  }

  @Test
  void displayVersionLabelKeepsStableVersionPlain() {
    assertEquals("v1.2.3", AppBuildInfo.displayVersionLabel("1.2.3"));
  }

  @Test
  void currentVersionAlwaysShipsCuratedWhatsNewNotes() {
    String currentVersion = AppBuildInfo.displayVersionLabel("dev");

    assertTrue(
        WhatsNewCatalog.forVersion(currentVersion).curated(),
        "Add a curated What's New entry whenever the application version changes.");
  }
}
