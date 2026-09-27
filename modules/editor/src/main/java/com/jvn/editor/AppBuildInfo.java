package com.jvn.editor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Locale;
import java.util.logging.Logger;

public final class AppBuildInfo {
  private static final Logger log = Logger.getLogger(AppBuildInfo.class.getName());
  private static final String FALLBACK_VERSION = "0.7.0";

  private AppBuildInfo() {
  }

  public static BuildInfo resolve(Class<?> anchor) {
    return new BuildInfo(displayVersionLabel(resolveRawVersion(anchor)), isRunningFromSource(anchor));
  }

  static String displayVersionLabel(String rawVersion) {
    String raw = rawVersion == null ? "" : rawVersion.trim();
    if (raw.isBlank() || raw.equalsIgnoreCase("dev") || raw.equalsIgnoreCase("vdev")) {
      return "v" + FALLBACK_VERSION;
    }

    String version = raw.startsWith("v") || raw.startsWith("V") ? raw.substring(1) : raw;
    String lower = version.toLowerCase(Locale.ROOT);
    String maturity = null;
    if (lower.contains("alpha") || lower.contains("snapshot") || lower.contains("dev")) {
      maturity = "Alpha";
    } else if (lower.contains("beta")) {
      maturity = "Beta";
    } else if (lower.contains("rc")) {
      maturity = "RC";
    }

    int suffix = version.indexOf('-');
    if (suffix >= 0) version = version.substring(0, suffix);
    int plus = version.indexOf('+');
    if (plus >= 0) version = version.substring(0, plus);
    version = version.trim();
    if (version.isBlank()) version = FALLBACK_VERSION;

    return "v" + version + (maturity == null ? "" : " " + maturity);
  }

  private static String resolveRawVersion(Class<?> anchor) {
    String version = System.getProperty("jvn.version");
    if (version != null && !version.isBlank()) return version.trim();
    Package pkg = anchor == null ? null : anchor.getPackage();
    if (pkg != null && pkg.getImplementationVersion() != null && !pkg.getImplementationVersion().isBlank()) {
      return pkg.getImplementationVersion().trim();
    }
    return "dev";
  }

  private static boolean isRunningFromSource(Class<?> anchor) {
    String override = System.getProperty("jvn.runningFromSource");
    if (override != null && !override.isBlank()) {
      String normalized = override.trim().toLowerCase(Locale.ROOT);
      return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes");
    }

    try {
      CodeSource source = anchor == null ? null : anchor.getProtectionDomain().getCodeSource();
      if (source == null || source.getLocation() == null) return false;
      Path location = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
      if (Files.isDirectory(location)) return true;
      String path = location.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
      return path.contains("/build/classes/") || path.contains("/out/production/");
    } catch (Exception e) {
      log.warning("Failed to determine if running from source: " + e.getMessage());
      return false;
    }
  }

  public record BuildInfo(String versionLabel, boolean runningFromSource) {
    public String sourceLabel() {
      return runningFromSource ? "Running from source" : "";
    }

    public String fullLabel() {
      return runningFromSource ? versionLabel + " | Running from source" : versionLabel;
    }
  }
}
