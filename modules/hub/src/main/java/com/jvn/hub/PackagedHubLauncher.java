package com.jvn.hub;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Bootstrap entry point for the self-contained Engine Hub jar.
 *
 * <p>The normal hub expects a real engine checkout so it can invoke Gradle,
 * open docs, install shortcuts, and launch the editor/launcher. This class
 * unpacks the checkout bundled inside the jar into the user's local JVN cache,
 * then delegates to {@link JvnHub} with {@code --project-root} pointing there.</p>
 */
public final class PackagedHubLauncher {
  private static final String BUNDLE_RESOURCE = "/com/jvn/hub/packaged-engine.zip";
  private static final String GRADLE_CACHE_RESOURCE = "/com/jvn/hub/packaged-gradle-cache.zip";
  private static final String INSTALL_ROOT_PROPERTY = "jvn.packagedEngineRoot";
  private static final String INSTALL_ROOT_ENV = "JVN_PACKAGED_ENGINE_ROOT";
  private static final String MARKER_FILE = ".jvn-packaged-engine.properties";
  private static final String GRADLE_CACHE_MARKER_FILE = ".jvn-packaged-gradle-cache.properties";

  private PackagedHubLauncher() {
  }

  public static void main(String[] args) {
    try {
      Path explicitRoot = explicitProjectRoot(args);
      if (explicitRoot != null) {
        launchHub(args, explicitRoot);
        return;
      }

      Bundle bundle = copyBundleToTempFile();
      Path projectRoot = ensureExtractedWorkspace(bundle);
      Bundle gradleCache = copyOptionalBundleToTempFile(
          GRADLE_CACHE_RESOURCE,
          "jvn-packaged-gradle-cache-");
      if (gradleCache != null) {
        ensureExtractedGradleCache(projectRoot, gradleCache);
      }
      if (hasFlag(args, "--extract-only")) {
        System.out.println(projectRoot.toAbsolutePath().normalize());
        return;
      }
      List<String> delegatedArgs = new ArrayList<>(List.of(args));
      delegatedArgs.removeIf("--extract-only"::equals);
      delegatedArgs.add("--project-root");
      delegatedArgs.add(projectRoot.toString());
      launchHub(delegatedArgs.toArray(String[]::new), projectRoot);
    } catch (Exception e) {
      System.err.println("JVN packaged hub failed to start: " + e.getMessage());
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  private static void launchHub(String[] args, Path projectRoot) {
    System.setProperty("jvn.projectRoot", projectRoot.toAbsolutePath().normalize().toString());
    System.setProperty("jvn.runningFromSource", "false");
    JvnHub.main(args);
  }

  private static Path explicitProjectRoot(String[] args) {
    for (int i = 0; args != null && i < args.length - 1; i++) {
      if ("--project-root".equals(args[i])) {
        return Paths.get(args[i + 1]).toAbsolutePath().normalize();
      }
    }
    String prop = System.getProperty("jvn.projectRoot");
    if (prop != null && !prop.isBlank()) {
      return Paths.get(prop).toAbsolutePath().normalize();
    }
    return null;
  }

  private static boolean hasFlag(String[] args, String flag) {
    if (args == null || flag == null) return false;
    for (String arg : args) {
      if (flag.equals(arg)) return true;
    }
    return false;
  }

  private static Bundle copyBundleToTempFile() throws IOException, NoSuchAlgorithmException {
    Bundle bundle = copyOptionalBundleToTempFile(BUNDLE_RESOURCE, "jvn-packaged-engine-");
    if (bundle == null) {
      throw new IOException("missing bundled engine workspace resource: " + BUNDLE_RESOURCE);
    }
    return bundle;
  }

  private static Bundle copyOptionalBundleToTempFile(String resource, String prefix)
      throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    Path temp = Files.createTempFile(prefix, ".zip");
    temp.toFile().deleteOnExit();
    try (InputStream raw = PackagedHubLauncher.class.getResourceAsStream(resource)) {
      if (raw == null) {
        Files.deleteIfExists(temp);
        return null;
      }
      try (DigestInputStream in = new DigestInputStream(new BufferedInputStream(raw), digest)) {
        Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
    }
    return new Bundle(temp, HexFormat.of().formatHex(digest.digest()));
  }

  private static Path ensureExtractedWorkspace(Bundle bundle) throws IOException {
    String version = readVersionLabel();
    Path installDir = installBase()
        .resolve(sanitize(version))
        .resolve(bundle.sha256().substring(0, 16));
    Path projectRoot = installDir.resolve("engine");
    Path marker = installDir.resolve(MARKER_FILE);

    if (looksReady(projectRoot, marker, bundle.sha256())) {
      return projectRoot;
    }

    deleteRecursively(installDir);
    Files.createDirectories(installDir);
    unzip(bundle.zipFile(), installDir);
    ensureGradleWrapperExecutable(projectRoot);
    writeMarker(marker, version, bundle.sha256());
    return projectRoot;
  }

  private static void ensureExtractedGradleCache(Path projectRoot, Bundle cache) throws IOException {
    Path cacheHome = projectRoot.resolve(".jvn-gradle-user-home");
    Path marker = cacheHome.resolve(GRADLE_CACHE_MARKER_FILE);
    if (cacheReady(marker, cache.sha256())) {
      return;
    }
    deleteRecursively(cacheHome);
    Files.createDirectories(projectRoot);
    unzip(cache.zipFile(), projectRoot);
    ensureGradleCacheExecutables(cacheHome);
    writeGradleCacheMarker(marker, cache.sha256());
  }

  private static Path installBase() {
    String prop = System.getProperty(INSTALL_ROOT_PROPERTY);
    if (prop != null && !prop.isBlank()) {
      return Paths.get(prop).toAbsolutePath().normalize();
    }
    String env = System.getenv(INSTALL_ROOT_ENV);
    if (env != null && !env.isBlank()) {
      return Paths.get(env).toAbsolutePath().normalize();
    }
    return Paths.get(System.getProperty("user.home", "."), ".jvn", "engine-hub").toAbsolutePath().normalize();
  }

  private static void unzip(Path zipFile, Path destination) throws IOException {
    try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile), StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        Path target = destination.resolve(entry.getName()).normalize();
        if (!target.startsWith(destination)) {
          throw new IOException("refusing to extract suspicious zip entry: " + entry.getName());
        }
        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        zip.closeEntry();
      }
    }
  }

  private static boolean looksReady(Path projectRoot, Path marker, String sha256) {
    if (!Files.isDirectory(projectRoot) || !Files.isRegularFile(marker)) return false;
    if (!Files.isRegularFile(projectRoot.resolve("settings.gradle.kts"))) return false;
    if (!Files.isRegularFile(projectRoot.resolve(gradleWrapperName()))) return false;
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(marker)) {
      props.load(in);
    } catch (IOException e) {
      return false;
    }
    return sha256.equals(props.getProperty("sha256"));
  }

  private static boolean cacheReady(Path marker, String sha256) {
    if (!Files.isRegularFile(marker)) return false;
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(marker)) {
      props.load(in);
    } catch (IOException e) {
      return false;
    }
    return sha256.equals(props.getProperty("sha256"));
  }

  private static void writeMarker(Path marker, String version, String sha256) throws IOException {
    Properties props = new Properties();
    props.setProperty("version", version);
    props.setProperty("sha256", sha256);
    props.setProperty("projectRoot", marker.getParent().resolve("engine").toString());
    try (var out = Files.newBufferedWriter(marker, StandardCharsets.UTF_8)) {
      props.store(out, "JVN packaged engine workspace. Auto-generated.");
    }
  }

  private static void writeGradleCacheMarker(Path marker, String sha256) throws IOException {
    Files.createDirectories(marker.getParent());
    Properties props = new Properties();
    props.setProperty("sha256", sha256);
    props.setProperty("gradleUserHome", marker.getParent().toString());
    try (var out = Files.newBufferedWriter(marker, StandardCharsets.UTF_8)) {
      props.store(out, "JVN packaged Gradle cache. Auto-generated.");
    }
  }

  private static void ensureGradleWrapperExecutable(Path projectRoot) {
    if (isWindows()) return;
    Path wrapper = projectRoot.resolve("gradlew");
    if (Files.isRegularFile(wrapper)) {
      wrapper.toFile().setExecutable(true, false);
    }
    for (String script : List.of("jvnw", "scripts/launch-app.sh", "scripts/launch-hub.sh",
        "install-macos-launcher.sh", "install-linux-launcher.sh")) {
      Path path = projectRoot.resolve(script);
      if (Files.isRegularFile(path)) {
        path.toFile().setExecutable(true, false);
      }
    }
  }

  private static void ensureGradleCacheExecutables(Path cacheHome) throws IOException {
    if (isWindows() || !Files.isDirectory(cacheHome)) return;
    Path jdks = cacheHome.resolve("jdks");
    if (!Files.isDirectory(jdks)) return;
    try (var walk = Files.walk(jdks)) {
      walk.filter(Files::isRegularFile)
          .filter(PackagedHubLauncher::isLikelyJdkExecutable)
          .forEach(path -> path.toFile().setExecutable(true, false));
    }
  }

  private static boolean isLikelyJdkExecutable(Path path) {
    String normalized = path.toString().replace('\\', '/');
    String name = path.getFileName() == null ? "" : path.getFileName().toString();
    return normalized.contains("/bin/") || "jspawnhelper".equals(name);
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) return;
    try (var walk = Files.walk(path)) {
      List<Path> paths = walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
      for (Path item : paths) {
        Files.deleteIfExists(item);
      }
    }
  }

  private static String readVersionLabel() {
    try (InputStream in = PackagedHubLauncher.class.getResourceAsStream("/com/jvn/hub/version.properties")) {
      if (in != null) {
        Properties props = new Properties();
        props.load(in);
        String version = props.getProperty("version");
        if (version != null && !version.isBlank()) return version.trim();
      }
    } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
      // Fall through to the manifest/default path.
    }
    String implementationVersion = PackagedHubLauncher.class.getPackage().getImplementationVersion();
    return implementationVersion == null || implementationVersion.isBlank() ? "dev" : implementationVersion.trim();
  }

  private static String sanitize(String value) {
    String raw = value == null || value.isBlank() ? "dev" : value.trim();
    return raw.replaceAll("[^A-Za-z0-9._-]+", "-");
  }

  private static String gradleWrapperName() {
    return isWindows() ? "gradlew.bat" : "gradlew";
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private record Bundle(Path zipFile, String sha256) {
  }
}
