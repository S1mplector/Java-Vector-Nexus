package com.jvn.licensing;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptanceReliabilityTest {
  @TempDir Path temporary;
  private static final LicenseTerms TERMS = new LicenseTerms("Version 1.0\nLicense", "Version 1.0\nTerms");
  private Path accept(AcceptanceStore store) throws Exception {
    return store.accept(TERMS, TERMS.bundleHash(), "Test Person", "", "test", "test", true);
  }
  @Test void interruptedStagingDoesNotBecomeAcceptance() throws Exception {
    var store = new AcceptanceStore(temporary.resolve("legal"));
    Path pending = Files.createDirectories(store.root().resolve("receipts/.pending-interrupted"));
    Files.writeString(pending.resolve("receipt.properties"), "schema=jvn-acceptance-1");
    assertEquals(AcceptanceStore.State.NOT_ACCEPTED, store.status(TERMS).state());
    Path valid = accept(store);
    assertTrue(AcceptanceStore.verify(valid).valid());
    assertEquals(AcceptanceStore.State.CURRENT, store.status(TERMS).state());
  }
  @Test void duplicateFieldsAndUnexpectedFieldsAreRejected() throws Exception {
    var store = new AcceptanceStore(temporary.resolve("legal"));
    Path receipt = accept(store);
    Path properties = receipt.resolve("receipt.properties");
    String original = Files.readString(properties);
    Files.writeString(properties, original + "\nname=Test Person\n");
    assertFalse(AcceptanceStore.verify(receipt).valid());
    Files.writeString(properties, original + "\nunrecognized=yes\n");
    assertFalse(AcceptanceStore.verify(receipt).valid());
  }
  @Test void unavailableStorageNeverReportsSuccess() throws Exception {
    Path blocker = temporary.resolve("file"); Files.writeString(blocker, "not a directory");
    var store = new AcceptanceStore(blocker.resolve("legal"));
    assertThrows(IOException.class, () -> accept(store));
    assertNotEquals(AcceptanceStore.State.CURRENT, store.status(TERMS).state());
  }
  @Test void mismatchedKeyPairCannotPublishReceipt() throws Exception {
    var store = new AcceptanceStore(temporary.resolve("first")); accept(store);
    var other = new AcceptanceStore(temporary.resolve("second")); accept(other);
    Files.copy(other.root().resolve("identity/public-key.der"), store.root().resolve("identity/public-key.der"),
        StandardCopyOption.REPLACE_EXISTING);
    assertThrows(IOException.class, () -> accept(store));
    try (var entries = Files.list(store.root().resolve("receipts"))) { assertEquals(1, entries.count()); }
  }
  @Test void concurrentAcceptancesAreEitherCompleteOrExplicitlyRejected() throws Exception {
    var store = new AcceptanceStore(temporary.resolve("legal"));
    var start = new CountDownLatch(1);
    List<Future<Path>> futures = new ArrayList<>();
    try (var executor = Executors.newFixedThreadPool(8)) {
      for (int i = 0; i < 16; i++) futures.add(executor.submit(() -> {
        start.await();
        try { return accept(store); }
        catch (IOException busy) {
          assertTrue(busy.getMessage().contains("Another acceptance"), busy.toString());
          return null;
        }
      }));
      start.countDown();
      int saved = 0;
      for (var future : futures) {
        Path receipt = future.get(10, TimeUnit.SECONDS);
        if (receipt != null) { saved++; assertTrue(AcceptanceStore.verify(receipt).valid()); }
      }
      assertTrue(saved >= 1);
      try (var entries = Files.list(store.root().resolve("receipts"))) { assertEquals(saved, entries.count()); }
    }
  }
  @Test void exportedSnapshotsVerifyAfterSourceIsDeleted() throws Exception {
    var store = new AcceptanceStore(temporary.resolve("legal")); Path receipt = accept(store);
    Path zip = temporary.resolve("export.zip"); AcceptanceStore.exportReceipt(receipt, zip);
    try (var paths = Files.walk(store.root())) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
    }
    Path extracted = Files.createDirectory(temporary.resolve("extracted"));
    try (var archive = new java.util.zip.ZipFile(zip.toFile())) {
      for (var entry : archive.stream().toList()) {
        try (var input = archive.getInputStream(entry)) { Files.copy(input, extracted.resolve(entry.getName())); }
      }
    }
    assertTrue(AcceptanceStore.verify(extracted).valid());
    assertEquals(AcceptanceStore.State.NOT_ACCEPTED, store.status(TERMS).state());
  }
  @Test void exportFailurePreservesReceiptAndLeavesNoDestination() throws Exception {
    Path receipt = accept(new AcceptanceStore(temporary.resolve("legal")));
    Path bad = temporary.resolve("missing/receipt.zip");
    assertThrows(IOException.class, () -> AcceptanceStore.exportReceipt(receipt, bad));
    assertFalse(Files.exists(bad)); assertTrue(AcceptanceStore.verify(receipt).valid());
  }
  @Test void localKeyAndExportPermissionsArePrivateOnPosix() throws Exception {
    var store = new AcceptanceStore(temporary.resolve("legal")); Path receipt = accept(store);
    if (!Files.getFileStore(temporary).supportsFileAttributeView("posix")) return;
    assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(store.root()));
    assertEquals(PosixFilePermissions.fromString("rw-------"),
        Files.getPosixFilePermissions(store.root().resolve("identity/private-key.pk8")));
    Path zip = temporary.resolve("export.zip"); AcceptanceStore.exportReceipt(receipt, zip);
    assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(zip));
  }
}
