package com.jvn.licensing;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.spec.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Local evidence only. A valid signature does not authenticate a person or a timestamp. */
public final class AcceptanceStore {
  public enum State { NOT_ACCEPTED, CURRENT, OUTDATED, INVALID }
  public record Status(State state, String detail, Path receipt) {}
  public record Verification(boolean valid, String detail, String keyFingerprint) {}
  private static final Set<String> FIELDS = Set.of("schema", "id", "acceptedAt", "name", "organization",
      "appVersion", "interface", "consent", "licenseVersion", "termsVersion", "licenseSha256",
      "termsSha256", "bundleSha256", "keySha256", "signature");
  private static final List<String> EXPORT_FILES = List.of("receipt.properties", "LICENSE.md", "TERMS.md", "public-key.der");
  private final Path root;
  private final Clock clock;

  public AcceptanceStore(Path root) { this(root, Clock.systemUTC()); }
  public AcceptanceStore(Path root, Clock clock) {
    this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
    this.clock = Objects.requireNonNull(clock);
  }
  public static AcceptanceStore local() {
    String override = System.getProperty("jvn.legalDir", "").trim();
    return new AcceptanceStore(override.isEmpty()
        ? Path.of(System.getProperty("user.home"), ".jvn", "legal") : Path.of(override));
  }
  public Path root() { return root; }

  public void requireCurrent(LicenseTerms terms) throws IOException {
    Status status = status(terms);
    if (status.state() != State.CURRENT) throw new IOException(
        "Current JVN license acceptance is required (" + status.state() + "). "
        + "Open Help > License & Terms in JVN, or review ':licensing:run --args=show' "
        + "and use the explicit accept command. " + status.detail());
  }

  /** Read-only: querying status never creates a key, receipt, or data directory. */
  public Status status(LicenseTerms current) {
    Path receipts = root.resolve("receipts");
    if (!Files.exists(receipts)) return new Status(State.NOT_ACCEPTED, "No local acceptance recorded", null);
    Path match = null;
    Path older = null;
    boolean invalid = false;
    try (var entries = Files.list(receipts)) {
      for (Path receipt : entries.sorted().toList()) {
        if (receipt.getFileName().toString().startsWith(".")) continue;
        Verification verification = verify(receipt);
        if (!verification.valid()) { invalid = true; continue; }
        if (!Files.isRegularFile(root.resolve("identity/public-key.der"))
            || !verification.keyFingerprint().equals(LicenseTerms.sha256(readLimited(root.resolve("identity/public-key.der"))))) {
          invalid = true;
          continue;
        }
        Properties p = readProperties(receipt.resolve("receipt.properties"));
        if (current.bundleHash().equals(p.getProperty("bundleSha256"))) match = receipt;
        else older = receipt;
      }
      if (match != null) return new Status(State.CURRENT,
          "Current terms accepted locally" + (invalid ? "; other invalid records also exist" : ""), match);
      if (invalid) return new Status(State.INVALID, "Unverifiable local records; no valid current acceptance", null);
      if (older != null) return new Status(State.OUTDATED, "Earlier terms accepted; current terms not accepted", older);
      return new Status(State.NOT_ACCEPTED, "No local acceptance recorded", null);
    } catch (IOException | RuntimeException e) {
      return new Status(State.INVALID, "Cannot read local acceptance records: " + e.getMessage(), null);
    }
  }

  /** Caller must display this immutable snapshot and collect an explicit unchecked-to-checked action. */
  public Path accept(LicenseTerms documents, String reviewedHash, String name, String organization,
                     String appVersion, String surface, boolean agreed) throws IOException {
    if (!agreed) throw new IllegalArgumentException("Explicit agreement is required");
    if (!documents.bundleHash().equals(reviewedHash)) throw new IllegalArgumentException("Reviewed terms do not match");
    name = field(name, "Name", false);
    organization = field(organization, "Organization", true);
    appVersion = field(appVersion, "Application version", false);
    surface = field(surface, "Acceptance interface", false);
    privateDirectory(root);
    try (var channel = FileChannel.open(root.resolve("acceptance.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
         var lock = channel.tryLock()) {
      if (lock == null) throw new IOException("Another acceptance is being saved; try again");
      KeyPair keys = identity();
      Path receipts = root.resolve("receipts");
      privateDirectory(receipts);
      String id = UUID.randomUUID().toString();
      Path staging = Files.createTempDirectory(receipts, ".pending-");
      try {
        Properties p = new Properties();
        p.setProperty("schema", "jvn-acceptance-1");
        p.setProperty("id", id);
        p.setProperty("acceptedAt", clock.instant().toString());
        p.setProperty("name", name);
        p.setProperty("organization", organization);
        p.setProperty("appVersion", appVersion);
        p.setProperty("interface", surface);
        p.setProperty("consent", LicenseTerms.CONSENT);
        p.setProperty("licenseVersion", documents.licenseVersion());
        p.setProperty("termsVersion", documents.termsVersion());
        p.setProperty("licenseSha256", documents.licenseHash());
        p.setProperty("termsSha256", documents.termsHash());
        p.setProperty("bundleSha256", documents.bundleHash());
        p.setProperty("keySha256", LicenseTerms.sha256(keys.getPublic().getEncoded()));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(canonical(p));
        p.setProperty("signature", Base64.getEncoder().encodeToString(signer.sign()));
        try (var out = Files.newOutputStream(staging.resolve("receipt.properties"))) {
          p.store(out, "JVN local acceptance receipt; identity and time are not independently verified");
        }
        Files.writeString(staging.resolve("LICENSE.md"), documents.license(), StandardCharsets.UTF_8);
        Files.writeString(staging.resolve("TERMS.md"), documents.terms(), StandardCharsets.UTF_8);
        Files.write(staging.resolve("public-key.der"), keys.getPublic().getEncoded());
        Verification verified = verify(staging);
        if (!verified.valid()) throw new IOException("Receipt verification failed: " + verified.detail());
        Path destination = receipts.resolve(id);
        forceFiles(staging);
        moveDirectory(staging, destination);
        return destination;
      } finally {
        removeStaging(staging);
      }
    } catch (GeneralSecurityException e) {
      throw new IOException("Could not sign acceptance receipt", e);
    } catch (OverlappingFileLockException e) {
      throw new IOException("Another acceptance is being saved; try again", e);
    }
  }

  private KeyPair identity() throws IOException, GeneralSecurityException {
    Path directory = root.resolve("identity");
    KeyFactory factory = KeyFactory.getInstance("Ed25519");
    if (Files.exists(directory)) {
      PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(readLimited(directory.resolve("private-key.pk8"))));
      PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(readLimited(directory.resolve("public-key.der"))));
      return new KeyPair(publicKey, privateKey);
    }
    KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    Path staging = Files.createTempDirectory(root, ".identity-");
    try {
      privateDirectory(staging);
      Files.write(staging.resolve("private-key.pk8"), pair.getPrivate().getEncoded());
      Files.write(staging.resolve("public-key.der"), pair.getPublic().getEncoded());
      if (Files.getFileStore(staging).supportsFileAttributeView("posix")) {
        Files.setPosixFilePermissions(staging.resolve("private-key.pk8"), PosixFilePermissions.fromString("rw-------"));
      }
      forceFiles(staging);
      moveDirectory(staging, directory);
    } finally { removeStaging(staging); }
    return pair;
  }

  /** Verifies contents against the included self-issued key, not against a trusted identity. */
  public static Verification verify(Path receipt) {
    try {
      Properties p = readProperties(receipt.resolve("receipt.properties"));
      if (!p.stringPropertyNames().equals(FIELDS) || !"jvn-acceptance-1".equals(p.getProperty("schema")))
        return invalid("Unknown or incomplete receipt schema");
      UUID.fromString(p.getProperty("id"));
      Instant.parse(p.getProperty("acceptedAt"));
      field(p.getProperty("name"), "Name", false);
      field(p.getProperty("organization"), "Organization", true);
      field(p.getProperty("appVersion"), "Application version", false);
      field(p.getProperty("interface"), "Acceptance interface", false);
      if (!LicenseTerms.CONSENT.equals(p.getProperty("consent"))) return invalid("Unknown consent statement");
      byte[] license = readLimited(receipt.resolve("LICENSE.md"));
      byte[] terms = readLimited(receipt.resolve("TERMS.md"));
      LicenseTerms documents = new LicenseTerms(new String(license, StandardCharsets.UTF_8), new String(terms, StandardCharsets.UTF_8));
      if (!LicenseTerms.sha256(license).equals(p.getProperty("licenseSha256"))
          || !LicenseTerms.sha256(terms).equals(p.getProperty("termsSha256"))
          || !documents.bundleHash().equals(p.getProperty("bundleSha256"))
          || !documents.licenseVersion().equals(p.getProperty("licenseVersion"))
          || !documents.termsVersion().equals(p.getProperty("termsVersion"))) return invalid("Document fingerprint mismatch");
      byte[] publicBytes = readLimited(receipt.resolve("public-key.der"));
      String fingerprint = LicenseTerms.sha256(publicBytes);
      if (!fingerprint.equals(p.getProperty("keySha256"))) return invalid("Public key fingerprint mismatch");
      PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(publicBytes));
      Signature verifier = Signature.getInstance("Ed25519");
      verifier.initVerify(key);
      verifier.update(canonical(p));
      if (!verifier.verify(Base64.getDecoder().decode(p.getProperty("signature")))) return invalid("Invalid signature");
      return new Verification(true, "Signature and document hashes verified; identity and time are self-declared", fingerprint);
    } catch (IOException | GeneralSecurityException | RuntimeException e) {
      return invalid("Unreadable or invalid receipt: " + e.getMessage());
    }
  }

  /** Exports evidence, never the installation private key. Refuses to overwrite existing files. */
  public static void exportReceipt(Path receipt, Path destination) throws IOException {
    // Snapshot before verification so concurrent changes cannot alter the exported evidence.
    Path staging = Files.createTempDirectory("jvn-receipt-export-");
    try {
      for (String name : EXPORT_FILES) Files.write(staging.resolve(name), readLimited(receipt.resolve(name)));
      Verification verification = verify(staging);
      if (!verification.valid()) throw new IOException(verification.detail());
      Path output = destination.toAbsolutePath().normalize();
      if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(output.toString());
      Path pending = Files.createTempFile(output.getParent(), ".jvn-receipt-", ".tmp");
      try {
        try (var zip = new ZipOutputStream(Files.newOutputStream(pending))) {
          for (String name : EXPORT_FILES) {
            zip.putNextEntry(new ZipEntry(name));
            Files.copy(staging.resolve(name), zip);
            zip.closeEntry();
          }
        }
        try (var file = FileChannel.open(pending, StandardOpenOption.WRITE)) { file.force(true); }
        // No REPLACE_EXISTING: exporting must never overwrite another receipt.
        Files.move(pending, output);
      } finally { Files.deleteIfExists(pending); }
    } finally { removeStaging(staging); }
  }

  private static byte[] canonical(Properties p) throws IOException {
    var bytes = new ByteArrayOutputStream();
    try (var output = new DataOutputStream(bytes)) {
      for (String key : new TreeSet<>(p.stringPropertyNames())) {
        if (key.equals("signature")) continue;
        for (String value : List.of(key, p.getProperty(key))) {
          byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
          output.writeInt(encoded.length);
          output.write(encoded);
        }
      }
    }
    return bytes.toByteArray();
  }
  private static String field(String value, String label, boolean optional) {
    String result = value == null ? "" : value.strip();
    if ((!optional && result.isEmpty()) || result.length() > 250 || result.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException(label + " must be a single line of at most 250 characters" + (optional ? "" : " and cannot be empty"));
    return result;
  }
  private static byte[] readLimited(Path file) throws IOException {
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing regular file: " + file.getFileName());
    try (InputStream input = Files.newInputStream(file)) {
      byte[] bytes = input.readNBytes(1_048_577);
      if (bytes.length > 1_048_576) throw new IOException("Receipt file exceeds size limit");
      return bytes;
    }
  }
  private static Properties readProperties(Path file) throws IOException {
    Properties p = new Properties() {
      @Override public synchronized Object put(Object key, Object value) {
        if (containsKey(key)) throw new IllegalArgumentException("Duplicate receipt field: " + key);
        return super.put(key, value);
      }
    };
    try (var input = new java.io.ByteArrayInputStream(readLimited(file))) { p.load(input); }
    return p;
  }
  private static Verification invalid(String reason) { return new Verification(false, reason, ""); }
  private static void privateDirectory(Path directory) throws IOException {
    Files.createDirectories(directory);
    if (Files.getFileStore(directory).supportsFileAttributeView("posix"))
      Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
  }
  private static void forceFiles(Path directory) throws IOException {
    try (var files = Files.list(directory)) {
      for (Path path : files.toList()) {
        try (var file = FileChannel.open(path, StandardOpenOption.WRITE)) { file.force(true); }
      }
    }
  }
  private static void moveDirectory(Path source, Path destination) throws IOException {
    try { Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE); }
    catch (AtomicMoveNotSupportedException e) { Files.move(source, destination); }
  }
  private static void removeStaging(Path directory) throws IOException {
    if (!Files.exists(directory)) return;
    try (var paths = Files.walk(directory)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
    }
  }
}
