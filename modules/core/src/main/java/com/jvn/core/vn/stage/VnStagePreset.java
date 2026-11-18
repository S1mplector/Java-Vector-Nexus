package com.jvn.core.vn.stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runtime-capable stage lighting preset exported from Scene Lighting Studio.
 */
public final class VnStagePreset {
  private final String id;
  private final String sourcePath;
  private final String backgroundTag;
  private final String subjectTag;
  private final BackgroundGrade backgroundGrade;
  private final List<Light> lights;
  private final List<Occluder> occluders;
  private final List<ResponseZone> responseZones;
  private final String cacheToken;
  private final SubjectMaterial subjectMaterial;
  private final SubjectGrade subjectGrade;
  private final boolean soloLights;
  private final boolean characterLighting;
  private final boolean backgroundLighting;

  public VnStagePreset(
      String id,
      String sourcePath,
      String backgroundTag,
      String subjectTag,
      BackgroundGrade backgroundGrade,
      List<Light> lights,
      List<Occluder> occluders,
      List<ResponseZone> responseZones
  ) {
    this(id, sourcePath, backgroundTag, subjectTag, backgroundGrade, lights, occluders, responseZones,
        SubjectMaterial.defaults(), SubjectGrade.defaults());
  }

  public VnStagePreset(String id, String sourcePath, String backgroundTag, String subjectTag,
      BackgroundGrade backgroundGrade, List<Light> lights, List<Occluder> occluders,
      List<ResponseZone> responseZones, SubjectMaterial subjectMaterial, SubjectGrade subjectGrade) {
    this.subjectGrade = subjectGrade == null ? SubjectGrade.defaults() : subjectGrade;
    this.subjectMaterial = subjectMaterial == null ? SubjectMaterial.defaults() : subjectMaterial;
    this.id = id == null ? "" : id.trim();
    this.sourcePath = sourcePath == null ? "" : sourcePath.trim();
    this.backgroundTag = backgroundTag == null ? "" : backgroundTag.trim();
    this.subjectTag = subjectTag == null ? "" : subjectTag.trim();
    this.backgroundGrade = backgroundGrade == null ? BackgroundGrade.defaults() : backgroundGrade;
    this.lights = lights == null ? List.of() : List.copyOf(new ArrayList<>(lights));
    this.occluders = occluders == null ? List.of() : List.copyOf(new ArrayList<>(occluders));
    this.responseZones = responseZones == null ? List.of() : List.copyOf(new ArrayList<>(responseZones));
    this.soloLights = this.lights.stream().anyMatch(light -> light.solo() && !light.muted() && light.intensity() > 0);
    this.characterLighting = this.subjectGrade.active() || this.lights.stream().anyMatch(light -> isActive(light)
        && (light.layer() == LightLayer.CHARACTER || light.silhouette() > 0));
    this.backgroundLighting = this.lights.stream().anyMatch(light -> isActive(light) && (light.layer() == LightLayer.BACKGROUND || light.groundProjection() != null))
        || this.backgroundGrade.tintStrength() != 0 || this.backgroundGrade.saturation() != 0
        || this.backgroundGrade.contrast() != 0 || this.backgroundGrade.overlayOpacity() != 0
        || this.backgroundGrade.exposure() != 0
        || this.occluders.stream().anyMatch(caster -> caster.surfaceTone() != null);
    this.cacheToken = buildCacheToken();
  }

  public String getId() { return id; }
  public String getSourcePath() { return sourcePath; }
  public String getBackgroundTag() { return backgroundTag; }
  public String getSubjectTag() { return subjectTag; }
  public BackgroundGrade getBackgroundGrade() { return backgroundGrade; }
  public List<Light> getLights() { return lights; }
  public List<Occluder> getOccluders() { return occluders; }
  public List<ResponseZone> getResponseZones() { return responseZones; }
  public String getCacheToken() { return cacheToken; }
  public SubjectMaterial getSubjectMaterial() { return subjectMaterial; }
  public SubjectGrade getSubjectGrade() { return subjectGrade; }

  public boolean matchesSubject(String sprite) {
    return subjectTag.isEmpty() || subjectTag.equals(sprite) || subjectMaterial.layers().contains(sprite);
  }

  public boolean hasSoloLights() { return soloLights; }

  public boolean hasCharacterLighting() { return characterLighting; }

  public boolean hasBackgroundLighting() { return backgroundLighting; }

  public boolean isActive(Light light) {
    return !light.muted() && light.intensity() > 0 && (!soloLights || light.solo());
  }

  private String buildCacheToken() {
    StringBuilder sb = new StringBuilder();
    sb.append(id).append("|").append(sourcePath).append("|").append(backgroundTag).append("|").append(subjectTag);
    sb.append("|bg=").append(backgroundGrade.tintColor()).append(",")
        .append(backgroundGrade.tintStrength()).append(",")
        .append(backgroundGrade.saturation()).append(",")
        .append(backgroundGrade.contrast()).append(",")
        .append(backgroundGrade.overlayColor()).append(",")
        .append(backgroundGrade.overlayOpacity()).append(",")
        .append(backgroundGrade.tintBlend()).append(",").append(backgroundGrade.overlayBlend()).append(",")
        .append(backgroundGrade.exposure());
    for (int i = 0; i < lights.size(); i++) {
      Light light = lights.get(i);
      if (light == null) continue;
      sb.append("|l").append(i).append(":")
          .append(light.name()).append(",")
          .append(light.type().persisted()).append(",")
          .append(light.layer().persisted()).append(",")
          .append(light.sceneX()).append(",")
          .append(light.sceneY()).append(",")
          .append(light.sourceX()).append(",")
          .append(light.sourceY()).append(",")
          .append(light.color()).append(",")
          .append(light.intensity()).append(",")
          .append(light.radius()).append(",")
          .append(light.softness()).append(",")
          .append(light.silhouette()).append(",")
          .append(light.muted()).append(",")
          .append(light.locked()).append(",")
          .append(light.solo()).append(",")
          .append(light.group()).append(',').append(light.blend()).append(',')
          .append(light.falloff()).append(',').append(light.castsShadows()).append(',').append(light.shadowQuality()).append(',').append(light.beamAngle()).append(',').append(light.areaAspect()).append(',').append(light.groundProjection());
      if (light.polygon() != null && !light.polygon().isEmpty()) {
        for (Point point : light.polygon()) {
          sb.append(";").append(point.x()).append(",").append(point.y());
        }
      }
    }
    for (int i = 0; i < occluders.size(); i++) {
      Occluder occluder = occluders.get(i);
      if (occluder == null) continue;
      sb.append("|o").append(i).append(":")
          .append(occluder.name()).append(",")
          .append(occluder.opacity()).append(",")
          .append(occluder.softness()).append(",")
          .append(occluder.enabled()).append(',').append(occluder.contactY()).append(',').append(occluder.heightScale()).append(',').append(occluder.footprintDepth()).append(',').append(occluder.roundFootprint()).append(',').append(occluder.surfaceTone()).append(',').append(occluder.floorContacts()).append(',').append(occluder.shadowFade()).append(',').append(occluder.shadowFraming());
      if (occluder.polygon() != null && !occluder.polygon().isEmpty()) {
        for (Point point : occluder.polygon()) {
          sb.append(";").append(point.x()).append(",").append(point.y());
        }
      }
    }
    for (int i = 0; i < responseZones.size(); i++) {
      ResponseZone zone = responseZones.get(i);
      if (zone == null) continue;
      sb.append("|z").append(i).append(":")
          .append(zone.name()).append(",")
          .append(zone.surfaceClass().persisted()).append(",")
          .append(zone.depthBias()).append(",")
          .append(zone.responseScale()).append(",")
          .append(zone.boundsX()).append(",")
          .append(zone.boundsY()).append(",")
          .append(zone.boundsW()).append(",")
          .append(zone.boundsH()).append(",")
          .append(zone.rotationDeg());
      if (zone.polygon() != null && !zone.polygon().isEmpty()) {
        for (Point point : zone.polygon()) {
          sb.append(";").append(point.x()).append(",").append(point.y());
        }
      }
    }
    return sb.append("|material:").append(subjectMaterial).append("|subjectGrade:").append(subjectGrade).toString();
  }

  public record BackgroundGrade(
      String tintColor,
      double tintStrength,
      double saturation,
      double contrast,
      String overlayColor,
      double overlayOpacity,
      String tintBlend,
      String overlayBlend,
      double exposure
  ) {
    public BackgroundGrade(String tintColor, double tintStrength, double saturation, double contrast,
        String overlayColor, double overlayOpacity) {
      this(tintColor, tintStrength, saturation, contrast, overlayColor, overlayOpacity, "Normal", "Soft Light", 0);
    }

    public static BackgroundGrade defaults() {
      return new BackgroundGrade("#FFFFFF", 0.0, 0.0, 0.0, "#000000", 0.0);
    }
  }

  public record SubjectGrade(String color, double strength, double saturation, double contrast) {
    public static SubjectGrade defaults() { return new SubjectGrade("#FFFFFF", 0, 0, 0); }

    public boolean active() { return strength != 0 || saturation != 0 || contrast != 0; }
  }

  public record SubjectMaterial(String normalMap, double normalStrength, double specular, double gloss,
      List<String> layers) {
    public SubjectMaterial {
      normalMap = normalMap == null ? "" : normalMap.strip();
      layers = layers == null ? List.of() : List.copyOf(layers);
    }

    public static SubjectMaterial defaults() { return new SubjectMaterial("", 0.7, 0.2, 32, List.of()); }
  }

  public record Point(double x, double y) {}

  public record Light(
      String name,
      LightType type,
      LightLayer layer,
      double sceneX,
      double sceneY,
      double sourceX,
      double sourceY,
      String color,
      double intensity,
      double radius,
      double softness,
      double silhouette,
      boolean muted,
      boolean locked,
      boolean solo,
      String group,
      List<Point> polygon,
      LightBlend blend,
      LightFalloff falloff,
      boolean castsShadows,
      ShadowQuality shadowQuality,
      double beamAngle,
      double areaAspect,
      GroundProjection groundProjection
  ) {
    public Light {
      polygon = polygon == null ? List.of() : List.copyOf(polygon);
      blend = blend == null ? LightBlend.PHYSICAL : blend;
      falloff = falloff == null ? LightFalloff.CLASSIC : falloff;
      shadowQuality = shadowQuality == null ? ShadowQuality.SOFT : shadowQuality;
      beamAngle = Math.clamp(Double.isFinite(beamAngle) ? beamAngle : 0, 0, 175);
      areaAspect = Math.clamp(Double.isFinite(areaAspect) ? areaAspect : 0, 0, 1);
    }

    public Light(String name, LightType type, LightLayer layer, double sceneX, double sceneY,
        double sourceX, double sourceY, String color, double intensity, double radius,
        double softness, double silhouette, boolean muted, boolean locked, boolean solo,
        String group, List<Point> polygon) {
      this(name, type, layer, sceneX, sceneY, sourceX, sourceY, color, intensity, radius,
          softness, silhouette, muted, locked, solo, group, polygon, LightBlend.PHYSICAL, LightFalloff.CLASSIC, true, ShadowQuality.SOFT, 0, 0, null);
    }
  }

  public enum LightBlend {
    PHYSICAL("Physical"), NORMAL("Normal"), ADD("Add"), SCREEN("Screen"),
    MULTIPLY("Multiply"), OVERLAY("Overlay"), SOFT_LIGHT("Soft Light");

    private final String label;

    LightBlend(String label) {
      this.label = label;
    }

    @Override public String toString() { return label; }

    public String persisted() { return name().toLowerCase(Locale.ROOT); }

    public static LightBlend from(String raw) {
      if (raw == null) return PHYSICAL;
      String normalized = raw.strip();
      for (LightBlend mode : values()) {
        if (mode.name().equalsIgnoreCase(normalized) || mode.label.equalsIgnoreCase(normalized)) return mode;
      }
      return PHYSICAL;
    }

    public double composite(double base, double light, double weight) {
      double b = Math.clamp(base, 0.0, 1.0);
      double s = Math.clamp(light, 0.0, 1.0);
      double blended = switch (this) {
        case PHYSICAL, NORMAL -> s;
        case ADD -> Math.min(1.0, b + s);
        case SCREEN -> b + s - b * s;
        case MULTIPLY -> b * s;
        case OVERLAY -> b < 0.5 ? 2 * b * s : 1 - 2 * (1 - b) * (1 - s);
        case SOFT_LIGHT -> s <= 0.5
            ? b - (1 - 2 * s) * b * (1 - b)
            : b + (2 * s - 1) * (softLightCurve(b) - b);
      };
      return b + (blended - b) * Math.clamp(weight, 0.0, 1.0);
    }

    private static double softLightCurve(double base) {
      return base <= 0.25 ? ((16 * base - 12) * base + 4) * base : Math.sqrt(base);
    }
  }

  public enum LightFalloff {
    CLASSIC("Classic"), LINEAR("Linear"), SMOOTH("Smooth"), INVERSE_SQUARE("Inverse Square");

    private final String label;

    LightFalloff(String label) { this.label = label; }

    @Override public String toString() { return label; }

    public String persisted() { return name().toLowerCase(Locale.ROOT); }

    public static LightFalloff from(String raw) {
      if (raw == null) return CLASSIC;
      String normalized = raw.strip();
      for (LightFalloff mode : values()) {
        if (mode.name().equalsIgnoreCase(normalized) || mode.label.equalsIgnoreCase(normalized)) return mode;
      }
      return CLASSIC;
    }

    public double weight(double proximity, double exponent) {
      double t = Math.clamp(proximity, 0.0, 1.0);
      return switch (this) {
        case CLASSIC -> Math.pow(t, exponent);
        case LINEAR -> t;
        case SMOOTH -> t * t * (3 - 2 * t);
        case INVERSE_SQUARE -> {
          // Finite at the emitter, with a smooth cutoff at the configured radius.
          double d = 1 - t;
          double range = 1 - d * d;
          yield range * range / (1 + 12 * d * d);
        }
      };
    }
  }

  /** Light footprint, perspective horizon and the darkness of projected floor shadows. */
  public record GroundProjection(double groundX, double groundY, double horizon, double strength,
      double floorLight, double floorExposure) {
    public GroundProjection {
      horizon = Math.clamp(Double.isFinite(horizon) ? horizon : 0.25, 0, 0.95);
      groundX = Math.clamp(Double.isFinite(groundX) ? groundX : 0.5, 0, 1);
      groundY = Math.clamp(Double.isFinite(groundY) ? groundY : 0.7, horizon + 0.02, 1);
      strength = Math.clamp(Double.isFinite(strength) ? strength : 0.5, 0, 1);
      floorLight = Math.clamp(Double.isFinite(floorLight) ? floorLight : 0, 0, 1);
      floorExposure = Math.clamp(Double.isFinite(floorExposure) ? floorExposure : 0, -4, 4);
    }

    public GroundProjection(double groundX, double groundY, double horizon, double strength) {
      this(groundX, groundY, horizon, strength, 0, 0);
    }
  }

  public enum ShadowQuality {
    HARD("Hard", 1), SOFT("Soft · 4 samples", 4), HIGH("Soft · 8 samples", 8);

    private final String label;
    private final int samples;

    ShadowQuality(String label, int samples) { this.label = label; this.samples = samples; }
    public int samples() { return samples; }
    public String persisted() { return name().toLowerCase(Locale.ROOT); }
    @Override public String toString() { return label; }

    public static ShadowQuality from(String raw) {
      if (raw != null) for (ShadowQuality value : values()) {
        if (value.name().equalsIgnoreCase(raw.strip())) return value;
      }
      return SOFT;
    }
  }

  public enum OccluderShape {
    POLYGON, BOX, ELLIPSE;

    public static OccluderShape from(String raw) {
      if (raw != null) for (OccluderShape shape : values()) {
        if (shape.name().equalsIgnoreCase(raw.strip())) return shape;
      }
      return POLYGON;
    }
  }

  /** A box in fractional stage coordinates, with the origin in the upper-left. */
  public record Bounds(double x, double y, double width, double height) {
    public Bounds {
      x = Math.clamp(Double.isFinite(x) ? x : 0, 0, 1);
      y = Math.clamp(Double.isFinite(y) ? y : 0, 0, 1);
      width = Math.clamp(Double.isFinite(width) ? width : 0, 0, 1 - x);
      height = Math.clamp(Double.isFinite(height) ? height : 0, 0, 1 - y);
    }

    public List<Point> outline(OccluderShape shape) {
      if (width <= 0 || height <= 0) return List.of();
      if (shape == OccluderShape.BOX) {
        return List.of(new Point(x, y), new Point(x + width, y),
            new Point(x + width, y + height), new Point(x, y + height));
      }
      var result = new ArrayList<Point>(24);
      for (int i = 0; i < 24; i++) {
        double angle = 2 * Math.PI * i / 24;
        result.add(new Point(x + (1 + Math.cos(angle)) * width / 2,
            y + (1 + Math.sin(angle)) * height / 2));
      }
      return List.copyOf(result);
    }
  }

  public record Occluder(String name, double opacity, double softness, boolean enabled,
      List<Point> polygon, OccluderShape shape, Bounds bounds, double contactY, double heightScale, double footprintDepth, boolean roundFootprint, SurfaceTone surfaceTone, List<Double> floorContacts, double shadowFade, ShadowFraming shadowFraming) {
    public Occluder {
      shape = shape == null || bounds == null ? OccluderShape.POLYGON : shape;
      contactY = Double.isFinite(contactY) ? Math.clamp(contactY, -1, 1) : -1;
      heightScale = Math.clamp(Double.isFinite(heightScale) ? heightScale : 1, 0, 3);
      footprintDepth = Math.clamp(Double.isFinite(footprintDepth) ? footprintDepth : 0, 0, 1);
      shadowFade = Math.clamp(Double.isFinite(shadowFade) ? shadowFade : 0, 0, 1);
      polygon = shape == OccluderShape.POLYGON ? (polygon == null ? List.of() : List.copyOf(polygon)) : bounds.outline(shape);
      floorContacts = floorContacts != null && floorContacts.size() == polygon.size()
          ? List.copyOf(floorContacts) : List.of();
    }

    public Occluder(String name, double opacity, double softness, boolean enabled, List<Point> polygon,
        OccluderShape shape, Bounds bounds) {
      this(name, opacity, softness, enabled, polygon, shape, bounds, -1, 1, 0, false, null, List.of(), 0, null);
    }

    public Occluder(String name, double opacity, double softness, boolean enabled, List<Point> polygon) {
      this(name, opacity, softness, enabled, polygon, OccluderShape.POLYGON, null, -1, 1, 0, false, null, List.of(), 0, null);
    }
  }

  /** An artistic adjustment to the projected floor cast around its center. */
  public record ShadowFraming(double offsetX, double offsetY, double scaleX, double scaleY, double angle) {
    public ShadowFraming {
      offsetX = Math.clamp(Double.isFinite(offsetX) ? offsetX : 0, -1, 1);
      offsetY = Math.clamp(Double.isFinite(offsetY) ? offsetY : 0, -1, 1);
      scaleX = Math.clamp(Double.isFinite(scaleX) ? scaleX : 1, 0.1, 4);
      scaleY = Math.clamp(Double.isFinite(scaleY) ? scaleY : 1, 0.1, 4);
      angle = Math.clamp(Double.isFinite(angle) ? angle : 0, -180, 180);
    }
  }

  /** Optional local exposure and midtone contrast for a segmented object on a flattened plate. */
  public record SurfaceTone(double exposure, double contrast) {
    public SurfaceTone {
      exposure = Math.clamp(Double.isFinite(exposure) ? exposure : 0, -4, 4);
      contrast = Math.clamp(Double.isFinite(contrast) ? contrast : 0, -0.8, 2);
    }
  }

  public record ResponseZone(
      String name,
      double boundsX,
      double boundsY,
      double boundsW,
      double boundsH,
      double rotationDeg,
      SurfaceClass surfaceClass,
      double depthBias,
      double responseScale,
      List<Point> polygon
  ) {
    public boolean isPolygon() {
      return polygon != null && polygon.size() >= 3;
    }
  }

  public enum LightType {
    RADIAL("Radial", "radial"),
    POLYGON("Polygon", "polygon"),
    CONE("Cone", "cone"),
    STRIP("Strip", "strip"),
    WINDOW("Window", "window"),
    BOUNCE("Bounce", "bounce"),
    AREA("Area", "area"),
    DIRECTIONAL("Directional", "directional"),
    AMBIENT("Ambient", "ambient");

    private final String label;
    private final String persisted;

    LightType(String label, String persisted) {
      this.label = label;
      this.persisted = persisted;
    }

    public String persisted() { return persisted; }

    public static LightType from(String raw) {
      if (raw == null || raw.isBlank()) return RADIAL;
      String normalized = raw.trim().toLowerCase(Locale.ROOT);
      for (LightType value : values()) {
        if (value.persisted.equals(normalized) || value.label.toLowerCase(Locale.ROOT).equals(normalized)) {
          return value;
        }
      }
      return RADIAL;
    }
  }

  public enum LightLayer {
    BACKGROUND("Behind Character", "background"),
    CHARACTER("On Character", "character"),
    FOREGROUND("In Front", "foreground");

    private final String label;
    private final String persisted;

    LightLayer(String label, String persisted) {
      this.label = label;
      this.persisted = persisted;
    }

    public String persisted() { return persisted; }

    public static LightLayer from(String raw) {
      if (raw == null || raw.isBlank()) return CHARACTER;
      String normalized = raw.trim().toLowerCase(Locale.ROOT);
      for (LightLayer value : values()) {
        if (value.persisted.equals(normalized) || value.label.toLowerCase(Locale.ROOT).equals(normalized)) {
          return value;
        }
      }
      return CHARACTER;
    }
  }

  public enum SurfaceClass {
    DEFAULT("Default", "default"),
    SKIN("Skin", "skin"),
    HAIR("Hair", "hair"),
    FABRIC("Fabric", "fabric"),
    METAL("Metal", "metal"),
    GLASS("Glass", "glass");

    private final String label;
    private final String persisted;

    SurfaceClass(String label, String persisted) {
      this.label = label;
      this.persisted = persisted;
    }

    public String persisted() { return persisted; }

    public static SurfaceClass from(String raw) {
      if (raw == null || raw.isBlank()) return DEFAULT;
      String normalized = raw.trim().toLowerCase(Locale.ROOT);
      for (SurfaceClass value : values()) {
        if (value.persisted.equals(normalized) || value.label.toLowerCase(Locale.ROOT).equals(normalized)) {
          return value;
        }
      }
      return DEFAULT;
    }
  }
}
