package com.jvn.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionRangeTest {
  @Test void caretRangesRespectPreOneZeroCompatibilityBoundaries() {
    assertTrue(VersionRange.accepts("^0.7.0", "0.7.9"));
    assertFalse(VersionRange.accepts("^0.7.0", "0.8.0"));
    assertFalse(VersionRange.accepts("^0.7.0", "0.6.9"));
    assertTrue(VersionRange.accepts("^0.0.4", "0.0.4"));
    assertFalse(VersionRange.accepts("^0.0.4", "0.0.5"));
    assertFalse(VersionRange.accepts("^0.0.4", "0.1.0"));
    assertTrue(VersionRange.accepts("^0.0", "0.0.9"));
    assertFalse(VersionRange.accepts("^0.0", "0.1.0"));
    assertTrue(VersionRange.accepts("^0", "0.9.0"));
    assertFalse(VersionRange.accepts("^0", "1.0.0"));
  }

  @Test void supportsCommonManifestRanges() {
    assertTrue(VersionRange.accepts("*", "4.8.0"));
    assertTrue(VersionRange.accepts("1.x", "1.9.2"));
    assertTrue(VersionRange.accepts("1.2.x", "1.2.9"));
    assertTrue(VersionRange.accepts("^1.2.0", "1.8.0"));
    assertTrue(VersionRange.accepts(">=1.2.0 <2.0.0", "1.5.1"));
    assertFalse(VersionRange.accepts("1.2.x", "1.3.0"));
    assertFalse(VersionRange.accepts("^1.2.0", "2.0.0"));
  }
}
