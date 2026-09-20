package com.jvn.editor.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MacKeychainBackendTest {

  private MacKeychainBackend backend;

  @BeforeEach
  void setUp() {
    Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("mac"),
        "macOS Keychain only available on macOS");
    backend = new MacKeychainBackend("jvn-editor-test-" + java.util.UUID.randomUUID(), "test-token");
    try {
      backend.clear();
    } catch (Exception ignored) {
      // no pre-existing entry
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    if (backend != null) backend.clear();
  }

  @Test
  void hasNoTokenBeforeAnythingIsSaved() {
    assertFalse(backend.exists());
  }

  @Test
  void loadTokenReturnsEmptyWhenNothingIsSaved() throws Exception {
    assertEquals(Optional.empty(), backend.load());
  }

  @Test
  void savedTokenRoundTripsExactly() throws Exception {
    backend.save("ghp_exampleToken123");

    assertTrue(backend.exists());
    assertEquals(Optional.of("ghp_exampleToken123"), backend.load());
  }

  @Test
  void clearRemovesTokenAndLoadReturnsEmpty() throws Exception {
    backend.save("ghp_exampleToken123");
    assertTrue(backend.exists());

    backend.clear();

    assertFalse(backend.exists());
    assertEquals(Optional.empty(), backend.load());
  }

  @Test
  void savingBlankTokenThrows() {
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> backend.save(" "));
  }
}
