package com.jvn.licensing;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseIntegrityVerifierTest {
  @TempDir Path temporary;
  private final byte[] logo = "<svg>JVN</svg>".getBytes();
  private final byte[] license = "License".getBytes();
  private final byte[] notice = "Settings credit required".getBytes();
  private Properties expected;

  private Map<String, byte[]> payload() throws Exception {
    var jar = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(jar)) {
      for (String name : List.of("com/jvn/branding/EngineBranding.class", "com/jvn/fx/RuntimeAttributionOverlay.class",
          "com/jvn/fx/FxLauncher.class", "com/jvn/swing/SwingLauncher.class", "com/jvn/runtime/GamePackageLauncher.class")) {
        zip.putNextEntry(new ZipEntry(name)); zip.write(new byte[] {1,2,3}); zip.closeEntry();
      }
      zip.putNextEntry(new ZipEntry("com/jvn/branding/jvn_logo.svg")); zip.write(logo); zip.closeEntry();
      zip.putNextEntry(new ZipEntry("com/jvn/branding/engine.properties"));
      zip.write("version=0.7.0\nsettings.attribution=required\n".getBytes()); zip.closeEntry();
    }
    expected = new Properties();
    expected.setProperty("policy", "jvn-settings-credit-v1"); expected.setProperty("engine.version", "0.7.0");
    expected.setProperty("license.sha256", LicenseTerms.sha256(license));
    expected.setProperty("notice.sha256", LicenseTerms.sha256(notice));
    expected.setProperty("logo.sha256", LicenseTerms.sha256(logo));
    expected.setProperty("jar.sha256.runtime-0.7.0.jar", LicenseTerms.sha256(jar.toByteArray()));
    var result = new LinkedHashMap<String, byte[]>();
    result.put("game/JVN-EXPORT-CONTRACT.properties", properties(expected));
    result.put("game/JVN-LICENSE.md", license); result.put("game/JVN-ATTRIBUTION.txt", notice);
    result.put("game/lib/runtime-0.7.0.jar", jar.toByteArray()); return result;
  }
  private byte[] properties(Properties p) throws Exception {
    var bytes = new ByteArrayOutputStream(); p.store(bytes,null); return bytes.toByteArray();
  }
  private Path zip(Map<String,byte[]> files) throws Exception {
    Path path = temporary.resolve(UUID.randomUUID()+".zip");
    try (var out = new ZipOutputStream(Files.newOutputStream(path))) {
      for (var entry : files.entrySet()) { out.putNextEntry(new ZipEntry(entry.getKey())); out.write(entry.getValue()); out.closeEntry(); }
    }
    return path;
  }
  @Test void acceptsMatchingArchiveAndNativeDirectory() throws Exception {
    var files = payload(); ReleaseIntegrityVerifier.verifyZip(zip(files), expected);
    Path directory = temporary.resolve("native");
    for (var entry : files.entrySet()) {
      Path file = directory.resolve(entry.getKey()); Files.createDirectories(file.getParent()); Files.write(file,entry.getValue());
    }
    ReleaseIntegrityVerifier.verifyDirectory(directory, expected);
  }
  @Test void rejectsMissingSettingsRuntimeAndMissingContract() throws Exception {
    var files = payload(); files.remove("game/lib/runtime-0.7.0.jar");
    final var missingRuntime = files;
    assertThrows(IOException.class, () -> ReleaseIntegrityVerifier.verifyZip(zip(missingRuntime), expected));
    files = payload(); files.remove("game/JVN-EXPORT-CONTRACT.properties");
    final var missing = files;
    assertThrows(IOException.class, () -> ReleaseIntegrityVerifier.verifyZip(zip(missing), expected));
  }
  @Test void rejectsModifiedJarLicenseAndNotice() throws Exception {
    for (String name : List.of("game/lib/runtime-0.7.0.jar","game/JVN-LICENSE.md","game/JVN-ATTRIBUTION.txt")) {
      var files = payload(); files.put(name,new byte[]{42});
      assertThrows(IOException.class, () -> ReleaseIntegrityVerifier.verifyZip(zip(files), expected));
    }
  }
  @Test void rejectsStaleVersionEvenWithOtherwiseValidFiles() throws Exception {
    var files=payload(); expected.setProperty("engine.version","0.8.0");
    assertThrows(IOException.class, () -> ReleaseIntegrityVerifier.verifyZip(zip(files),expected));
  }
  @Test void rejectsWrongLogoFingerprint() throws Exception {
    var files=payload(); expected.setProperty("logo.sha256", "wrong");
    files.put("game/JVN-EXPORT-CONTRACT.properties",properties(expected));
    assertThrows(IOException.class, () -> ReleaseIntegrityVerifier.verifyZip(zip(files),expected));
  }
  @Test void rejectsTraversalWindowsPathsAndCaseCollisions() throws Exception {
    for (String name : List.of("../bad", "/absolute", "C:/bad", "game\\bad", "game/./bad", "GAME/JVN-LICENSE.MD")) {
      var files=payload(); files.put(name,new byte[]{1});
      assertThrows(IOException.class, () -> ReleaseIntegrityVerifier.verifyZip(zip(files),expected),name);
    }
  }
  @Test void rejectsAmbiguousContractsAndJars() throws Exception {
    for (String name : List.of("JVN-EXPORT-CONTRACT.properties", "runtime-0.7.0.jar")) {
      var files=payload(); files.put("other/"+name,new byte[]{1});
      assertThrows(IOException.class, () -> ReleaseIntegrityVerifier.verifyZip(zip(files),expected));
    }
  }
  @Test void rejectsContractWithoutRuntimeCoverage() throws Exception {
    var files=payload(); expected.remove("jar.sha256.runtime-0.7.0.jar");
    files.put("game/JVN-EXPORT-CONTRACT.properties",properties(expected));
    assertThrows(IOException.class, () -> ReleaseIntegrityVerifier.verifyZip(zip(files),expected));
  }
}
