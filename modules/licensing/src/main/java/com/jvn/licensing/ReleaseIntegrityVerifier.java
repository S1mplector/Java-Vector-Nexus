package com.jvn.licensing;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Final verification of the actual shipped payload against this build's expected engine files. */
public final class ReleaseIntegrityVerifier {
  private static final String CONTRACT = "JVN-EXPORT-CONTRACT.properties";
  private static final Set<String> REQUIRED_CODE = Set.of(
      "com/jvn/branding/EngineBranding.class", "com/jvn/branding/jvn_logo.svg",
      "com/jvn/branding/engine.properties", "com/jvn/fx/RuntimeAttributionOverlay.class",
      "com/jvn/fx/FxLauncher.class", "com/jvn/swing/SwingLauncher.class",
      "com/jvn/runtime/GamePackageLauncher.class");
  private ReleaseIntegrityVerifier() {}

  public static void verifyZip(Path archive, Properties expected) throws IOException {
    try (var zip = new ZipFile(archive.toFile())) {
      Map<String, ZipEntry> entries = new HashMap<>();
      Set<String> portableNames = new HashSet<>();
      for (var entry : zip.stream().toList()) {
        String name = entry.getName(); validatePath(name);
        if (!portableNames.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate/case-colliding archive entry: " + name);
        if (!entry.isDirectory()) entries.put(name, entry);
      }
      List<String> contracts = entries.keySet().stream().filter(n -> n.endsWith("/" + CONTRACT) || n.equals(CONTRACT)).toList();
      if (contracts.size() != 1) throw new IOException("Expected exactly one export contract");
      String root = contracts.getFirst().substring(0, contracts.getFirst().length() - CONTRACT.length());
      verify(new Payload() {
        public Set<String> names() { return entries.keySet(); }
        public InputStream open(String name) throws IOException {
          ZipEntry entry = entries.get(name);
          if (entry == null) throw new IOException("Missing package file: " + name);
          return zip.getInputStream(entry);
        }
      }, root, expected);
    }
  }

  public static void verifyDirectory(Path directory, Properties expected) throws IOException {
    Map<String, Path> entries = new HashMap<>();
    try (var files = Files.walk(directory)) {
      for (Path path : files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList())
        entries.put(directory.relativize(path).toString().replace('\\', '/'), path);
    }
    List<String> contracts = entries.keySet().stream().filter(n -> n.endsWith("/" + CONTRACT) || n.equals(CONTRACT)).toList();
    if (contracts.size() != 1) throw new IOException("Expected exactly one native export contract");
    String root = contracts.getFirst().substring(0, contracts.getFirst().length() - CONTRACT.length());
    verify(new Payload() {
      public Set<String> names() { return entries.keySet(); }
      public InputStream open(String name) throws IOException {
        Path path = entries.get(name);
        if (path == null) throw new IOException("Missing native package file: " + name);
        return Files.newInputStream(path);
      }
    }, root, expected);
  }

  private interface Payload { Set<String> names(); InputStream open(String name) throws IOException; }
  private static void verify(Payload payload, String root, Properties expected) throws IOException {
    if (!"jvn-settings-credit-v1".equals(expected.getProperty("policy"))) throw new IOException("Unsupported export policy");
    Properties actual;
    try (var input = payload.open(root + CONTRACT)) { actual = readProperties(input); }
    if (!expected.equals(actual)) throw new IOException("Export contract differs from current engine build or license");
    try (var input = payload.open(root + "JVN-LICENSE.md")) {
      if (!digest(input).equals(expected.getProperty("license.sha256"))) throw new IOException("Packaged license is outdated or modified");
    }
    try (var input = payload.open(root + "JVN-ATTRIBUTION.txt")) {
      if (!digest(input).equals(expected.getProperty("notice.sha256"))) throw new IOException("Packaged attribution notice is outdated or modified");
    }
    Set<String> code = new HashSet<>();
    int jars = 0;
    for (String key : expected.stringPropertyNames()) {
      if (!key.startsWith("jar.sha256.")) continue;
      jars++;
      String base = key.substring("jar.sha256.".length());
      List<String> matches = payload.names().stream().filter(n -> n.equals(base) || n.endsWith("/" + base)).toList();
      if (matches.size() != 1) throw new IOException("Missing or ambiguous runtime jar: " + base);
      String name = matches.getFirst();
      try (var input = payload.open(name)) {
        if (!digest(input).equals(expected.getProperty(key))) throw new IOException("Stale or modified runtime jar: " + base);
      }
      try (var jar = new ZipInputStream(payload.open(name))) {
        Set<String> seen = new HashSet<>();
        for (ZipEntry entry; (entry = jar.getNextEntry()) != null;) {
          if (!seen.add(entry.getName())) throw new IOException("Duplicate entry in runtime jar: " + base);
          if (REQUIRED_CODE.contains(entry.getName())) {
            if (!code.add(entry.getName())) throw new IOException("Duplicate branding/runtime class: " + entry.getName());
            if (entry.getName().endsWith("jvn_logo.svg") && !digest(jar).equals(expected.getProperty("logo.sha256")))
              throw new IOException("Official SVG is missing or replaced");
            if (entry.getName().equals("com/jvn/branding/engine.properties")) {
              Properties p = readProperties(jar);
              if (!expected.getProperty("engine.version").equals(p.getProperty("version"))
                  || !"required".equals(p.getProperty("settings.attribution"))) throw new IOException("Invalid runtime branding version or policy");
            }
          }
        }
      }
    }
    if (jars == 0 || !code.containsAll(REQUIRED_CODE)) throw new IOException("Required settings-credit runtime support is missing");
  }

  public static Properties readProperties(InputStream input) throws IOException {
    Properties p = new Properties();
    byte[] bytes = input.readNBytes(1_048_577);
    if (bytes.length > 1_048_576) throw new IOException("Contract exceeds size limit");
    p.load(new ByteArrayInputStream(bytes)); return p;
  }
  private static void validatePath(String name) throws IOException {
    if (name.contains("\\") || name.startsWith("/") || name.contains(":")
        || Arrays.stream(name.split("/", -1)).anyMatch(s -> s.equals("..") || s.equals(".")))
      throw new IOException("Unsafe archive path: " + name);
  }
  private static String digest(InputStream input) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[65536]; long total = 0;
      for (int n; (n = input.read(buffer)) != -1;) {
        total += n;
        if (total > 512L * 1024 * 1024) throw new IOException("Engine file exceeds verification size limit");
        digest.update(buffer, 0, n);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
