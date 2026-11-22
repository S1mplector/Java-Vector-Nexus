package com.jvn.branding;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Properties;

/** Official bundled SVG and build version. Project assets and preferences cannot override these. */
public final class EngineBranding {
  public static final String POLICY = "jvn-settings-credit-v1";
  private static final String VERSION = loadVersion();
  private static final BufferedImage LOGO = renderLogo();
  private EngineBranding() {}

  public static String version() { return VERSION; }
  public static String accessibleCredit() { return "Made with JVN (" + VERSION + ")"; }
  public static BufferedImage logo() { return LOGO; }
  public static byte[] svg() {
    try (var input = EngineBranding.class.getResourceAsStream("jvn_logo.svg")) {
      if (input == null) throw new IllegalStateException("Official JVN SVG is missing");
      return input.readAllBytes();
    } catch (IOException e) { throw new IllegalStateException("Cannot read official JVN SVG", e); }
  }
  private static String loadVersion() {
    try (var input = EngineBranding.class.getResourceAsStream("engine.properties")) {
      if (input == null) throw new IllegalStateException("Engine branding metadata is missing");
      var p = new Properties(); p.load(input);
      String version = p.getProperty("version", "");
      if (version.isBlank() || !"required".equals(p.getProperty("settings.attribution")))
        throw new IllegalStateException("Invalid engine branding metadata");
      return version;
    } catch (IOException e) { throw new IllegalStateException(e); }
  }
  private static BufferedImage renderLogo() {
    try {
      // Only the engine-bundled document is accepted, never a project-provided SVG.
      return SvgRasterizer.render(new ByteArrayInputStream(svg()), 760);
    } catch (IOException e) { throw new IllegalStateException("Cannot render official JVN SVG", e); }
  }
}
