package com.jvn.core.vn.stage;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Loads Scene Lighting Studio exports as runtime stage presets.
 */
public final class VnStagePresetLoader {
  private VnStagePresetLoader() {}

  public static VnStagePreset load(String id, String sourcePath, InputStream input) throws IOException {
    Properties props = new Properties();
    try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      props.load(reader);
    }
    return load(id, sourcePath, props);
  }

  public static VnStagePreset load(String id, String sourcePath, Properties props) {
    if (props == null) props = new Properties();
    VnStagePreset.BackgroundGrade grade = new VnStagePreset.BackgroundGrade(
        props.getProperty("bgTintColor", "#FFFFFF"),
        unitValue(props.getProperty("bgTintStrength"), 0.0),
        signedUnitValue(props.getProperty("bgSaturation"), 0.0),
        signedUnitValue(props.getProperty("bgContrast"), 0.0),
        props.getProperty("bgOverlayColor", "#000000"),
        unitValue(props.getProperty("bgOverlayOpacity"), 0.0),
        props.getProperty("bgTintBlend", "Normal"),
        props.getProperty("bgOverlayBlend", "Soft Light"),
        clamp(doubleValue(props.getProperty("bgExposure"), 0), -4, 4)
    );
    List<VnStagePreset.Light> lights = new ArrayList<>();
    int lightCount = Math.min(props.size(), intValue(props.getProperty("lights"), 0));
    for (int i = 0; i < lightCount; i++) {
      String prefix = "light." + i + ".";
      lights.add(new VnStagePreset.Light(
          props.getProperty(prefix + "name", "Light " + (i + 1)),
          VnStagePreset.LightType.from(props.getProperty(prefix + "shape")),
          VnStagePreset.LightLayer.from(props.getProperty(prefix + "layer")),
          unitValue(props.getProperty(prefix + "position"), 0.5, 0),
          unitValue(props.getProperty(prefix + "position"), 0.35, 1),
          unitValue(props.getProperty(prefix + "source"), 0.38, 0),
          unitValue(props.getProperty(prefix + "source"), 0.17, 1),
          props.getProperty(prefix + "color", "#FFD7A8"),
          unitValue(props.getProperty(prefix + "intensity"), 0.42),
          clamp(unitValue(props.getProperty(prefix + "radius"), 0.22), 0.05, 0.80),
          unitValue(props.getProperty(prefix + "softness"), 0.55),
          unitValue(props.getProperty(prefix + "silhouette"), 0.28),
          boolValue(props.getProperty(prefix + "muted"), false),
          boolValue(props.getProperty(prefix + "locked"), false),
          boolValue(props.getProperty(prefix + "solo"), false),
          props.getProperty(prefix + "group", ""),
          parsePoints(props.getProperty(prefix + "polygon")),
          VnStagePreset.LightBlend.from(props.getProperty(prefix + "blend")),
          VnStagePreset.LightFalloff.from(props.getProperty(prefix + "falloff")),
          boolValue(props.getProperty(prefix + "castsShadows"),
              VnStagePreset.LightType.from(props.getProperty(prefix + "shape")) != VnStagePreset.LightType.AMBIENT),
          VnStagePreset.ShadowQuality.from(props.getProperty(prefix + "shadowQuality")),
          doubleValue(props.getProperty(prefix + "beamAngle"), 0),
          unitValue(props.getProperty(prefix + "areaAspect"), 0),
          loadGroundProjection(props, prefix)
      ));
    }

    List<VnStagePreset.ResponseZone> responseZones = new ArrayList<>();
    int zoneCount = Math.min(props.size(), intValue(props.getProperty("zones"), 0));
    for (int i = 0; i < zoneCount; i++) {
      String prefix = "zone." + i + ".";
      String boundsRaw = props.getProperty(prefix + "bounds", "");
      double[] bounds = parseBounds(boundsRaw, new double[]{0.25, 0.25, 0.50, 0.50});
      responseZones.add(new VnStagePreset.ResponseZone(
          props.getProperty(prefix + "name", "Zone " + (i + 1)),
          clamp(bounds[0], 0.0, 1.0),
          clamp(bounds[1], 0.0, 1.0),
          clamp(bounds[2], 0.0, 1.0),
          clamp(bounds[3], 0.0, 1.0),
          doubleValue(props.getProperty(prefix + "rotation"), 0.0),
          VnStagePreset.SurfaceClass.from(props.getProperty(prefix + "surface")),
          signedUnitValue(props.getProperty(prefix + "depthBias"), 0.0),
          clamp(doubleValue(props.getProperty(prefix + "responseScale"), 1.0), 0.1, 2.5),
          parsePoints(props.getProperty(prefix + "polygon"))
      ));
    }

    String resolvedId = id == null || id.isBlank()
        ? props.getProperty("jvn.stagePreset.id", "")
        : id;
    String resolvedSourcePath = sourcePath == null || sourcePath.isBlank()
        ? props.getProperty("jvn.stagePreset.file", "")
        : sourcePath;
    return new VnStagePreset(
        resolvedId,
        resolvedSourcePath,
        boolValue(props.getProperty("jvn.stagePreset.useSceneBackground"), false)
            ? "" : props.getProperty("background", "").trim(),
        props.getProperty("character", "").trim(),
        grade,
        lights,
        loadOccluders(props),
        responseZones,
        loadMaterial(props),
        new VnStagePreset.SubjectGrade(props.getProperty("color", "#FFFFFF"),
            unitValue(props.getProperty("strength"), 0), signedUnitValue(props.getProperty("saturation"), 0),
            signedUnitValue(props.getProperty("contrast"), 0))
    );
  }

  public static VnStagePreset.GroundProjection loadGroundProjection(Properties props, String prefix) {
    String raw = props.getProperty(prefix + "ground", "");
    if (raw.isBlank()) return null;
    double[] values = parseBounds(raw, new double[]{0.5, 0.7, 0.25, 0.5});
    return new VnStagePreset.GroundProjection(values[0], values[1], values[2], values[3],
        unitValue(props.getProperty(prefix + "floorLight"), 0), doubleValue(props.getProperty(prefix + "floorExposure"), 0));
  }

  public static List<VnStagePreset.Occluder> loadOccluders(Properties props) {
    List<VnStagePreset.Occluder> occluders = new ArrayList<>();
    int occluderCount = Math.min(props.size(), intValue(props.getProperty("occluders"), 0));
    for (int i = 0; i < occluderCount; i++) {
      String prefix = "occluder." + i + ".";
      List<VnStagePreset.Point> polygon = parsePoints(props.getProperty(prefix + "polygon"));
      String boundsValue = props.getProperty(prefix + "bounds", "");
      double[] values = parseBounds(boundsValue, new double[4]);
      VnStagePreset.Bounds bounds = boundsValue.isBlank() ? null
          : new VnStagePreset.Bounds(values[0], values[1], values[2], values[3]);
      VnStagePreset.OccluderShape shape = VnStagePreset.OccluderShape.from(
          props.getProperty(prefix + "shape", bounds == null ? "polygon" : "box"));
      VnStagePreset.Occluder blocker = new VnStagePreset.Occluder(
          props.getProperty(prefix + "name", "Occluder " + (i + 1)),
          unitValue(props.getProperty(prefix + "opacity"), 0.85),
          unitValue(props.getProperty(prefix + "softness"), 0.12),
          boolValue(props.getProperty(prefix + "enabled"), true),
          polygon, shape, bounds,
          doubleValue(props.getProperty(prefix + "contactY"), -1),
          doubleValue(props.getProperty(prefix + "heightScale"), 1),
          doubleValue(props.getProperty(prefix + "footprintDepth"), 0),
          boolValue(props.getProperty(prefix + "roundFootprint"), false),
          loadSurfaceTone(props, prefix),
          parseFloorContacts(props.getProperty(prefix + "floorContacts")),
          unitValue(props.getProperty(prefix + "shadowFade"), 0),
          loadShadowFraming(props.getProperty(prefix + "shadowFraming"))
      );
      if (blocker.polygon().size() >= 3) occluders.add(blocker);
    }

    return occluders;
  }

  private static VnStagePreset.ShadowFraming loadShadowFraming(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String[] pieces = raw.split(",");
    if (pieces.length != 5) return null;
    return new VnStagePreset.ShadowFraming(doubleValue(pieces[0], 0), doubleValue(pieces[1], 0),
        doubleValue(pieces[2], 1), doubleValue(pieces[3], 1), doubleValue(pieces[4], 0));
  }

  private static List<Double> parseFloorContacts(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    List<Double> contacts = new ArrayList<>();
    for (String coordinate : raw.split(",")) {
      double value = doubleValue(coordinate, Double.NaN);
      if (!Double.isFinite(value) || value < 0 || value > 1) return List.of();
      contacts.add(value);
    }
    return contacts;
  }

  private static VnStagePreset.SurfaceTone loadSurfaceTone(Properties props, String prefix) {
    double exposure = doubleValue(props.getProperty(prefix + "surfaceExposure"), 0);
    double contrast = doubleValue(props.getProperty(prefix + "surfaceContrast"), 0);
    return exposure == 0 && contrast == 0 ? null : new VnStagePreset.SurfaceTone(exposure, contrast);
  }

  public static VnStagePreset.SubjectMaterial loadMaterial(Properties props) {
    List<String> subjectLayers = new ArrayList<>();
    int layerCount = Math.min(props.size(), intValue(props.getProperty("material.layers"), 0));
    for (int i = 0; i < layerCount; i++) {
      String layer = props.getProperty("material.layer." + i, "").strip();
      if (!layer.isEmpty()) subjectLayers.add(layer);
    }
    return new VnStagePreset.SubjectMaterial(
        props.getProperty("material.normalMap", ""), unitValue(props.getProperty("material.normalStrength"), 0.7),
        unitValue(props.getProperty("material.specular"), 0.2),
        clamp(doubleValue(props.getProperty("material.gloss"), 32), 2, 128), subjectLayers);
  }

  private static List<VnStagePreset.Point> parsePoints(String raw) {
    List<VnStagePreset.Point> points = new ArrayList<>();
    if (raw == null || raw.isBlank()) return points;
    for (String pair : raw.split(";")) {
      String[] xy = pair.split(",");
      if (xy.length != 2) continue;
      points.add(new VnStagePreset.Point(
          clamp(doubleValue(xy[0], 0.5), 0.0, 1.0),
          clamp(doubleValue(xy[1], 0.5), 0.0, 1.0)
      ));
    }
    return points;
  }

  private static double[] parseBounds(String raw, double[] fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    String[] parts = raw.split(",");
    if (parts.length != 4) return fallback;
    return new double[]{
        doubleValue(parts[0], fallback[0]),
        doubleValue(parts[1], fallback[1]),
        doubleValue(parts[2], fallback[2]),
        doubleValue(parts[3], fallback[3])
    };
  }

  private static int intValue(String raw, int fallback) {
    try {
      return raw == null ? fallback : Integer.parseInt(raw.trim());
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return fallback;
    }
  }

  private static boolean boolValue(String raw, boolean fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    String value = raw.trim().toLowerCase();
    return switch (value) {
      case "true", "yes", "on", "1" -> true;
      case "false", "no", "off", "0" -> false;
      default -> fallback;
    };
  }

  private static double unitValue(String raw, double fallback) {
    double value = doubleValue(raw, fallback);
    if (Math.abs(value) > 1.0) value /= 100.0;
    return clamp(value, 0.0, 1.0);
  }

  private static double signedUnitValue(String raw, double fallback) {
    double value = doubleValue(raw, fallback);
    if (Math.abs(value) > 1.0) value /= 100.0;
    return clamp(value, -1.0, 1.0);
  }

  private static double unitValue(String rawPair, double fallback, int index) {
    if (rawPair == null || rawPair.isBlank()) return fallback;
    String[] parts = rawPair.split(",");
    if (index < 0 || index >= parts.length) return fallback;
    return clamp(doubleValue(parts[index], fallback), 0.0, 1.0);
  }

  private static double doubleValue(String raw, double fallback) {
    try {
      double value = raw == null ? fallback : Double.parseDouble(raw.trim());
      return Double.isFinite(value) ? value : fallback;
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return fallback;
    }
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
