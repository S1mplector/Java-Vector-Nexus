package com.jvn.licensing;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptanceStoreTest {
  @TempDir Path temporary;
  private static final LicenseTerms TERMS = new LicenseTerms("# License\nVersion 1.0\nOriginal license\n",
      "# Terms\nVersion 1.0\nOriginal terms\n");
  private AcceptanceStore store() {
    return new AcceptanceStore(temporary.resolve("legal"), Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC));
  }
  private Path accept(AcceptanceStore store) throws Exception {
    return store.accept(TERMS, TERMS.bundleHash(), "Test Person", "Test Studio", "0.4.4.1", "test", true);
  }
  private Properties properties(Path receipt) throws Exception {
    var p = new Properties();
    try (var input = Files.newInputStream(receipt.resolve("receipt.properties"))) { p.load(input); }
    return p;
  }
  private void rewrite(Path receipt, Properties p) throws Exception {
    try (var output = Files.newOutputStream(receipt.resolve("receipt.properties"))) { p.store(output, "rewritten"); }
  }

  @Test void statusDoesNotCreateFilesAndRefusalCannotRecordConsent() throws Exception {
    var store = store();
    assertEquals(AcceptanceStore.State.NOT_ACCEPTED, store.status(TERMS).state());
    assertThrows(IllegalArgumentException.class, () -> store.accept(TERMS, TERMS.bundleHash(), "Test", "", "test", "test", false));
    assertFalse(Files.exists(store.root()));
  }
  @Test void requiresNameAndMatchingReviewedDocuments() {
    var store = store();
    assertThrows(IllegalArgumentException.class, () -> store.accept(TERMS, "wrong", "Test", "", "test", "test", true));
    assertThrows(IllegalArgumentException.class, () -> store.accept(TERMS, TERMS.bundleHash(), "  ", "", "test", "test", true));
    assertThrows(IllegalArgumentException.class, () -> store.accept(TERMS, TERMS.bundleHash(), "A\nB", "", "test", "test", true));
    assertFalse(Files.exists(store.root()));
  }
  @Test void receiptPreservesConsentDocumentsTimeAndSignature() throws Exception {
    var store = store(); Path receipt = accept(store);
    assertTrue(AcceptanceStore.verify(receipt).valid());
    assertEquals(AcceptanceStore.State.CURRENT, store.status(TERMS).state());
    assertEquals(TERMS.license(), Files.readString(receipt.resolve("LICENSE.md")));
    var p = properties(receipt);
    assertEquals("2026-09-27T12:00:00Z", p.getProperty("acceptedAt"));
    assertEquals("Test Person", p.getProperty("name"));
    assertEquals(LicenseTerms.CONSENT, p.getProperty("consent"));
    assertEquals(TERMS.bundleHash(), p.getProperty("bundleSha256"));
  }
  @Test void documentChangeWithinSameVersionRequiresNewAcceptance() throws Exception {
    var store = store(); Path previous = accept(store);
    var changed = new LicenseTerms(TERMS.license() + "More text", TERMS.terms());
    assertEquals(AcceptanceStore.State.OUTDATED, store.status(changed).state());
    Path next = store.accept(changed, changed.bundleHash(), "Test", "", "next", "test", true);
    assertNotEquals(previous, next);
    assertTrue(AcceptanceStore.verify(previous).valid());
    assertEquals(AcceptanceStore.State.CURRENT, store.status(changed).state());
  }
  @Test void rejectsEditedDocumentsAndSignatureFields() throws Exception {
    var store = store(); Path receipt = accept(store);
    Files.writeString(receipt.resolve("LICENSE.md"), TERMS.license() + "Tampered");
    assertFalse(AcceptanceStore.verify(receipt).valid());
    assertEquals(AcceptanceStore.State.INVALID, store.status(TERMS).state());
    Files.writeString(receipt.resolve("LICENSE.md"), TERMS.license());
    var p = properties(receipt); p.setProperty("name", "Another Person"); rewrite(receipt, p);
    assertFalse(AcceptanceStore.verify(receipt).valid());
  }
  @Test void changedTimeOrConsentCannotReuseSignature() throws Exception {
    Path receipt = accept(store()); var p = properties(receipt);
    p.setProperty("acceptedAt", "2020-01-01T00:00:00Z"); rewrite(receipt, p);
    assertFalse(AcceptanceStore.verify(receipt).valid());
    p.setProperty("acceptedAt", "2026-09-27T12:00:00Z"); p.setProperty("consent", "I decline"); rewrite(receipt, p);
    assertFalse(AcceptanceStore.verify(receipt).valid());
  }
  @Test void detectsReplacedPublicKeyAndUnknownSchema() throws Exception {
    Path receipt = accept(store());
    byte[] original = Files.readAllBytes(receipt.resolve("public-key.der"));
    Files.write(receipt.resolve("public-key.der"), new byte[] {1, 2, 3});
    assertFalse(AcceptanceStore.verify(receipt).valid());
    Files.write(receipt.resolve("public-key.der"), original);
    var p = properties(receipt); p.setProperty("schema", "future"); rewrite(receipt, p);
    assertFalse(AcceptanceStore.verify(receipt).valid());
  }
  @Test void verificationIsIndependentOfPropertiesOrderingAndComments() throws Exception {
    Path receipt = accept(store()); rewrite(receipt, properties(receipt));
    assertTrue(AcceptanceStore.verify(receipt).valid());
  }
  @Test void corruptPrivateKeyIsNotSilentlyReplaced() throws Exception {
    var store = store(); Path old = accept(store);
    Files.write(store.root().resolve("identity/private-key.pk8"), new byte[] {0});
    assertThrows(IOException.class, () -> accept(store));
    assertTrue(AcceptanceStore.verify(old).valid());
    assertArrayEquals(new byte[] {0}, Files.readAllBytes(store.root().resolve("identity/private-key.pk8")));
  }
  @Test void competingWriterCannotPublishPartialReceipt() throws Exception {
    var store = store(); Files.createDirectories(store.root());
    try (var channel = FileChannel.open(store.root().resolve("acceptance.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
         var lock = channel.lock()) {
      assertTrue(lock.isValid());
      assertThrows(IOException.class, () -> accept(store));
    }
    assertEquals(AcceptanceStore.State.NOT_ACCEPTED, store.status(TERMS).state());
  }
  @Test void exportContainsOnlyPublicEvidenceAndCannotOverwrite() throws Exception {
    Path receipt = accept(store()); Path zipPath = temporary.resolve("receipt.zip");
    AcceptanceStore.exportReceipt(receipt, zipPath);
    try (var zip = new ZipFile(zipPath.toFile())) {
      assertEquals(Set.of("receipt.properties", "LICENSE.md", "TERMS.md", "public-key.der"),
          new HashSet<>(zip.stream().map(e -> e.getName()).toList()));
      Path unpacked = Files.createDirectory(temporary.resolve("unpacked"));
      for (var entry : zip.stream().toList()) {
        try (var input = zip.getInputStream(entry)) { Files.copy(input, unpacked.resolve(entry.getName())); }
      }
      assertTrue(AcceptanceStore.verify(unpacked).valid());
    }
    byte[] original = Files.readAllBytes(zipPath);
    assertThrows(FileAlreadyExistsException.class, () -> AcceptanceStore.exportReceipt(receipt, zipPath));
    assertArrayEquals(original, Files.readAllBytes(zipPath));
  }
  @Test void invalidReceiptCannotBeExported() throws Exception {
    Path receipt = accept(store()); Files.delete(receipt.resolve("TERMS.md"));
    Path zip = temporary.resolve("bad.zip");
    assertThrows(IOException.class, () -> AcceptanceStore.exportReceipt(receipt, zip));
    assertFalse(Files.exists(zip));
  }
  @Test void transplantedReceiptIsNotCurrentForAnotherInstallation() throws Exception {
    var first = store(); Path receipt = accept(first);
    var other = new AcceptanceStore(temporary.resolve("other")); accept(other);
    Path foreign = Files.createDirectory(other.root().resolve("receipts/foreign"));
    for (String file : List.of("receipt.properties", "LICENSE.md", "TERMS.md", "public-key.der"))
      Files.copy(receipt.resolve(file), foreign.resolve(file));
    assertTrue(other.status(TERMS).detail().contains("invalid records"));
    Files.delete(other.root().resolve("identity/public-key.der"));
    assertEquals(AcceptanceStore.State.INVALID, other.status(TERMS).state());
  }
  @Test void missingOversizedAndMalformedRecordsFailClosed() throws Exception {
    assertFalse(AcceptanceStore.verify(temporary.resolve("missing")).valid());
    Path receipt = accept(store());
    Files.write(receipt.resolve("receipt.properties"), new byte[1_048_577]);
    assertFalse(AcceptanceStore.verify(receipt).valid());
    Files.writeString(receipt.resolve("receipt.properties"), "name=\\uBAD!");
    assertFalse(AcceptanceStore.verify(receipt).valid());
  }
  @Test void lineEndingsNormalizeAcrossPlatformsAndHashHasKnownValue() {
    assertEquals(TERMS.bundleHash(), new LicenseTerms(TERMS.license().replace("\n", "\r\n"), TERMS.terms()).bundleHash());
    assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", LicenseTerms.sha256(new byte[0]));
    assertThrows(IllegalArgumentException.class, () -> new LicenseTerms("", TERMS.terms()));
    assertThrows(IllegalArgumentException.class, () -> new LicenseTerms("No version", TERMS.terms()));
  }
  @Test void bundledDocumentsAreAvailable() throws Exception {
    var documents = LicenseTerms.bundled();
    assertTrue(documents.license().contains("JVN Source-Available Attribution License"));
    assertTrue(documents.terms().contains("Electronic Acceptance"));
  }
}
