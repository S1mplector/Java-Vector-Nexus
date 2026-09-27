package com.jvn.licensing;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseCliTest {
  @TempDir Path temporary;
  private int run(AcceptanceStore store, String... args) {
    var output = new PrintStream(new ByteArrayOutputStream());
    return LicenseCli.run(args, store, output, output);
  }
  @Test void inspectionAndInvalidCommandsNeverCreateAcceptance() {
    var store = new AcceptanceStore(temporary.resolve("legal"));
    assertEquals(0, run(store, "show")); assertEquals(0, run(store, "status"));
    assertEquals(2, run(store, "accept"));
    assertEquals(2, run(store, "accept", "--agree", "wrong-hash", "Test"));
    assertFalse(Files.exists(store.root()));
  }
  @Test void enforcementFailsWithoutConsentAndSucceedsOnlyForCurrentDocuments() throws Exception {
    var store = new AcceptanceStore(temporary.resolve("required"));
    assertEquals(2, run(store, "require"));
    var terms = LicenseTerms.bundled();
    var old = new LicenseTerms(terms.license() + "obsolete", terms.terms());
    store.accept(old, old.bundleHash(), "Fixture", "", "test", "test", true);
    assertEquals(2, run(store, "require"));
    assertEquals(0, run(store, "accept", "--agree", terms.bundleHash(), "Fixture"));
    assertEquals(0, run(store, "require"));
  }

  @Test void explicitAcceptanceVerificationAndTrustedFingerprintMismatch() throws Exception {
    var store = new AcceptanceStore(temporary.resolve("legal"));
    var terms = LicenseTerms.bundled();
    assertEquals(0, run(store, "accept", "--agree", terms.bundleHash(), "Test"));
    Path receipt = store.status(terms).receipt(); assertNotNull(receipt);
    assertEquals(0, run(store, "verify", receipt.toString()));
    assertEquals(2, run(store, "verify", receipt.toString(), "wrong-key"));
    assertEquals(0, run(store, "export", receipt.toString(), temporary.resolve("receipt.zip").toString()));
  }
}
