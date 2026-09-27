package com.jvn.licensing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Immutable snapshot of precisely the text presented for acceptance (UTF-8, LF). */
public record LicenseTerms(String license, String terms) {
  public static final String CONSENT = "I have read and agree to the JVN License and Terms of Use, "
      + "and intend to record my electronic acceptance. If acting for an organization, I am authorized to do so.";

  public LicenseTerms {
    license = normalize(license);
    terms = normalize(terms);
    version(license);
    version(terms);
  }

  public static LicenseTerms bundled() throws IOException {
    return new LicenseTerms(resource("LICENSE.md"), resource("TERMS.md"));
  }

  public static String applicationVersion() throws IOException {
    var properties = new java.util.Properties();
    try (var input = LicenseTerms.class.getResourceAsStream("build.properties")) {
      if (input == null) throw new IOException("Missing licensing build version");
      properties.load(input);
    }
    String version = properties.getProperty("version", "").strip();
    if (version.isEmpty()) throw new IOException("Empty licensing build version");
    return version;
  }

  private static String resource(String name) throws IOException {
    try (var input = LicenseTerms.class.getResourceAsStream(name)) {
      if (input == null) throw new IOException("Missing bundled legal document: " + name);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Legal document is empty");
    return value.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static String version(String value) {
    var match = Pattern.compile("(?m)^Version ([0-9]+(?:\\.[0-9]+)*)\\b").matcher(value);
    if (!match.find()) throw new IllegalArgumentException("Legal document has no Version header");
    return match.group(1);
  }

  public String licenseVersion() { return version(license); }
  public String termsVersion() { return version(terms); }
  public String licenseHash() { return sha256(license.getBytes(StandardCharsets.UTF_8)); }
  public String termsHash() { return sha256(terms.getBytes(StandardCharsets.UTF_8)); }
  public String bundleHash() {
    return sha256(("JVN-LEGAL-1\n" + licenseHash() + "\n" + termsHash()).getBytes(StandardCharsets.UTF_8));
  }

  public static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
