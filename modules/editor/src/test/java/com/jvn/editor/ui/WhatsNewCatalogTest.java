package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WhatsNewCatalogTest {

  @Test
  void requestsPopupOnlyWhenVersionChanges() {
    assertTrue(WhatsNewCatalog.shouldShow("v0.5.0", ""));
    assertTrue(WhatsNewCatalog.shouldShow("v0.5.0", "v0.4.3.1"));
    assertTrue(WhatsNewCatalog.shouldShow("v0.5.0", "v0.5.0 Beta"));
    assertFalse(WhatsNewCatalog.shouldShow("v0.5.0", " v0.5.0 "));
    assertFalse(WhatsNewCatalog.shouldShow("", "v0.4.1"));
  }

  @Test
  void currentReleaseContainsCuratedDetailedNotes() {
    WhatsNewCatalog.Release release = WhatsNewCatalog.forVersion("v0.5.0");

    assertTrue(release.curated());
    assertEquals("v0.5.0", release.versionLabel());
    assertTrue(release.summary().contains("asset labeling"));
    assertEquals(4, release.sections().size());
    assertTrue(release.sections().get(0).title().contains("asset labels"));
  }

  @Test
  void maturityBuildUsesNumericReleaseNotesButKeepsFullLabel() {
    WhatsNewCatalog.Release release = WhatsNewCatalog.forVersion("v0.5.0 Beta");

    assertTrue(release.curated());
    assertEquals("v0.5.0 Beta", release.versionLabel());
  }

  @Test
  void unknownVersionStillGetsGracefulVersionSpecificScreen() {
    WhatsNewCatalog.Release release = WhatsNewCatalog.forVersion("v9.1.0");

    assertFalse(release.curated());
    assertEquals("v9.1.0", release.versionLabel());
    assertFalse(release.sections().isEmpty());
  }
}
