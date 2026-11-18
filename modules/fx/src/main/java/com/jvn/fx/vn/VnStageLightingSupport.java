package com.jvn.fx.vn;

import java.util.ArrayList;
import java.util.List;

import com.jvn.core.vn.stage.VnStagePreset;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

public final class VnStageLightingSupport {
  private VnStageLightingSupport() {}

  /** Shades the same plate pass used by gameplay at the dimensions of an editor preview. */
  public static Image renderBackgroundPreview(Image source, VnStagePreset stage, double width, double height) {
    Raster raster = shadeBackground(PixelData.capture(source), stage, width, height);
    return raster == null ? null : raster.image();
  }

  static Raster shadeBackground(PixelData source, VnStagePreset stage, double width, double height) {
    if (source == null || width <= 0 || height <= 0) return null;
    int outWidth = Math.max(1, (int) Math.round(width));
    int outHeight = Math.max(1, (int) Math.round(height));

    VnStagePreset.BackgroundGrade grade = stage == null ? VnStagePreset.BackgroundGrade.defaults() : stage.getBackgroundGrade();
    Color tintColor = parseColor(grade.tintColor(), Color.WHITE);
    Color overlayColor = parseColor(grade.overlayColor(), Color.BLACK);
    double tintStrength = clamp(grade.tintStrength(), 0.0, 1.0);
    double satAdjust = clamp(grade.saturation(), -1.0, 1.0);
    double conAdjust = clamp(grade.contrast(), -1.0, 1.0);
    double overlayOpacity = clamp(grade.overlayOpacity(), 0.0, 1.0);
    double exposure = Math.pow(2, grade.exposure());
    int tintMode = VnStageGradeBlend.index(grade.tintBlend());
    int overlayMode = VnStageGradeBlend.index(grade.overlayBlend());

    double sourceWidth = source.width();
    double sourceHeight = source.height();
    double scale = Math.max(width / sourceWidth, height / sourceHeight);
    double drawWidth = sourceWidth * scale;
    double drawHeight = sourceHeight * scale;
    double drawX = (width - drawWidth) * 0.5;
    double drawY = (height - drawHeight) * 0.5;
    List<LightRuntime> runtimeLights = prepareLights(stage, width, height, true);
    int[] sourceColumns = new int[outWidth];
    for (int x = 0; x < outWidth; x++) {
      sourceColumns[x] = (int) Math.round(clamp((x - drawX) / scale, 0.0, sourceWidth - 1.0));
    }
    int[] output = new int[outWidth * outHeight];
    byte[] groundShadows = stage == null ? null : VnGroundShadows.mask(stage, outWidth, outHeight);
    int[] materialMap = stage == null ? null : VnGroundShadows.materialMap(stage, outWidth, outHeight,
        runtimeLights.stream().anyMatch(light -> light.floor != null));
    VnGroundShadows.FloorLight floor = runtimeLights.stream().map(light -> light.floor)
        .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    double aspect = width / height;
    double[] materialExposure = materialMap == null ? null : new double[stage.getOccluders().size() + 1];
    double[] materialContrast = materialMap == null ? null : new double[stage.getOccluders().size() + 1];
    if (materialMap != null) {
      for (int i = 0; i < stage.getOccluders().size(); i++) {
        var tone = stage.getOccluders().get(i).surfaceTone();
        if (tone == null) continue;
        materialExposure[i + 1] = Math.pow(2, tone.exposure());
        materialContrast[i + 1] = 1 + tone.contrast();
      }
    }
    List<VnStageShadow> occluders = VnStageShadow.prepare(stage == null ? List.of() : stage.getOccluders(), width, height);

    for (int y = 0; y < outHeight; y++) {
      if (Thread.currentThread().isInterrupted()) return null;
      double sourceY = clamp((y - drawY) / scale, 0.0, sourceHeight - 1.0);
      double v = (y + 0.5) / outHeight;
      boolean floorRow = floor != null && v > floor.horizon();
      int sy = (int) Math.round(sourceY);
      for (int x = 0; x < outWidth; x++) {
        int argb = source.at(sourceColumns[x], sy);
        int a = (argb >>> 24) & 0xFF;
        double r = ((argb >>> 16) & 0xFF) / 255.0;
        double g = ((argb >>> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;

        int material = materialMap == null ? 0 : materialMap[y * outWidth + x];
        double cameraExposure = floorRow && material == 0 ? exposure * floor.exposure() : exposure;
        if (cameraExposure != 1) {
          r = linearToSrgb(srgbToLinear(r) * cameraExposure);
          g = linearToSrgb(srgbToLinear(g) * cameraExposure);
          b = linearToSrgb(srgbToLinear(b) * cameraExposure);
        }
        double lum = linearLuminance(r, g, b);
        r = lum + (r - lum) * (1.0 + satAdjust);
        g = lum + (g - lum) * (1.0 + satAdjust);
        b = lum + (b - lum) * (1.0 + satAdjust);
        r = (r - 0.5) * (1.0 + conAdjust) + 0.5;
        g = (g - 0.5) * (1.0 + conAdjust) + 0.5;
        b = (b - 0.5) * (1.0 + conAdjust) + 0.5;
        r = VnStageGradeBlend.fastComposite(r, tintColor.getRed(), tintStrength, tintMode);
        g = VnStageGradeBlend.fastComposite(g, tintColor.getGreen(), tintStrength, tintMode);
        b = VnStageGradeBlend.fastComposite(b, tintColor.getBlue(), tintStrength, tintMode);

        if (overlayOpacity > 1e-6) {
          r = applyBlend(r, overlayColor.getRed(), overlayOpacity, overlayMode);
          g = applyBlend(g, overlayColor.getGreen(), overlayOpacity, overlayMode);
          b = applyBlend(b, overlayColor.getBlue(), overlayOpacity, overlayMode);
        }

        for (LightRuntime light : runtimeLights) {
          double influence;
          if (light.floor != null && material == 0) {
            if (v <= light.floor.horizon()) continue;
            influence = light.floor.illumination((x + 0.5) / outWidth, 1 / (v - light.floor.horizon()), aspect);
          } else {
            double weight = light.field.weight(x, y);
            if (weight <= 0) continue;
            if (light.castsShadows) weight *= light.field.transmission(occluders, x, y, light.shadowQuality);
            influence = weight * light.intensity;
          }
          if (influence <= 0.001) continue;
          if (light.blend == VnStagePreset.LightBlend.PHYSICAL) {
            double albedo = linearLuminance(r, g, b);
            double tintWeight = clamp(influence * 0.14, 0.0, 0.32);
            r = lightChannel(r, light.color.getRed(), influence, albedo, tintWeight);
            g = lightChannel(g, light.color.getGreen(), influence, albedo, tintWeight);
            b = lightChannel(b, light.color.getBlue(), influence, albedo, tintWeight);
          } else {
            r = light.blend.composite(r, light.color.getRed(), influence);
            g = light.blend.composite(g, light.color.getGreen(), influence);
            b = light.blend.composite(b, light.color.getBlue(), influence);
          }
        }

        if (materialMap != null) {
          int id = materialMap[y * outWidth + x];
          if (id > 0 && materialContrast[id] > 0) {
            double gain = materialExposure[id];
            double contrast = materialContrast[id];
            r = surfaceChannel(r, gain, contrast);
            g = surfaceChannel(g, gain, contrast);
            b = surfaceChannel(b, gain, contrast);
          }
        }
        if (groundShadows != null) {
          double shade = 1 - (groundShadows[y * outWidth + x] & 255) / 255.0;
          r *= shade;
          g *= shade;
          b *= shade;
        }
        int rr = (int) Math.round(clamp(r, 0.0, 1.0) * 255.0);
        int gg = (int) Math.round(clamp(g, 0.0, 1.0) * 255.0);
        int bb = (int) Math.round(clamp(b, 0.0, 1.0) * 255.0);
        output[y * outWidth + x] = (a << 24) | (rr << 16) | (gg << 8) | bb;
      }
    }
    return new Raster(outWidth, outHeight, output);
  }

  static Image buildLitCharacter(
      Image source,
      String spriteTag,
      double drawX,
      double drawY,
      double drawWidth,
      double drawHeight,
      double canvasWidth,
      double canvasHeight,
      VnStagePreset stage,
      Image normalMap,
      CharacterRig rig
  ) {
    Raster raster = shadeCharacter(PixelData.capture(source), spriteTag, drawX, drawY, drawWidth, drawHeight,
        canvasWidth, canvasHeight, stage, PixelData.capture(normalMap), rig);
    return raster == null ? source : raster.image();
  }

  static Raster shadeCharacter(
      PixelData source,
      String spriteTag,
      double drawX,
      double drawY,
      double drawWidth,
      double drawHeight,
      double canvasWidth,
      double canvasHeight,
      VnStagePreset stage,
      PixelData normalMap,
      CharacterRig rig
  ) {
    if (source == null || stage == null || drawWidth <= 0 || drawHeight <= 0) return null;
    int outWidth = Math.max(1, (int) Math.round(drawWidth));
    int outHeight = Math.max(1, (int) Math.round(drawHeight));

    double sourceWidth = source.width();
    double sourceHeight = source.height();
    List<CharacterLight> runtimeLights = rig.lights().stream()
        .filter(light -> light.light.field.intersects(drawX, drawY, drawWidth, drawHeight)).toList();
    boolean subjectMatched = stage.matchesSubject(spriteTag);
    VnStagePreset.SubjectGrade grade = stage.getSubjectGrade();
    boolean gradeActive = subjectMatched && grade.active();
    if (runtimeLights.isEmpty() && !gradeActive) return null;
    Color tint = parseColor(grade.color(), Color.WHITE);

    VnStageMaterial material = new VnStageMaterial(null, stage.getSubjectMaterial());
    double lightHeight = Math.max(1, Math.min(canvasWidth, canvasHeight) * 0.20);
    List<ResponseRuntime> responseZones = subjectMatched ? prepareResponseZones(stage) : List.of();
    int sourcePixelWidth = (int) sourceWidth;
    int sourcePixelHeight = (int) sourceHeight;
    int[] sourceColumns = new int[outWidth];
    double[] normalizedColumns = new double[outWidth];
    double[] sceneColumns = new double[outWidth];
    for (int x = 0; x < outWidth; x++) {
      double fraction = x / Math.max(1.0, outWidth - 1.0);
      sourceColumns[x] = (int) Math.round(fraction * (sourceWidth - 1.0));
      normalizedColumns[x] = outWidth == 1 ? 0.5 : fraction;
      sceneColumns[x] = drawX + normalizedColumns[x] * drawWidth;
    }
    int[] output = new int[outWidth * outHeight];
    double aspect = sourceWidth / sourceHeight;

    for (int y = 0; y < outHeight; y++) {
      if (Thread.currentThread().isInterrupted()) return null;
      double srcYf = clamp((y / Math.max(1.0, outHeight - 1.0)) * (sourceHeight - 1.0), 0.0, sourceHeight - 1.0);
      int sy = (int) Math.round(srcYf);
      double normalizedY = outHeight <= 1 ? 0.5 : (y / (double) (outHeight - 1));
      double sceneY = drawY + normalizedY * drawHeight;
      for (int x = 0; x < outWidth; x++) {
        int sx = sourceColumns[x];
        int argb = source.at(sx, sy);
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) continue;
        double r = ((argb >>> 16) & 0xFF) / 255.0;
        double g = ((argb >>> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;
        double normalizedX = normalizedColumns[x];
        double sceneX = sceneColumns[x];
        if (gradeActive) {
          double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
          r = gradeChannel(r, tint.getRed(), lum, grade);
          g = gradeChannel(g, tint.getGreen(), lum, grade);
          b = gradeChannel(b, tint.getBlue(), lum, grade);
        }
        int normal = 0;
        ResponseInfluence response = null;
        double edgeFactor = -1.0;
        double edgeX = 0.0;
        double edgeY = 0.0;

        for (CharacterLight prepared : runtimeLights) {
          LightRuntime light = prepared.light;
          double weight = prepared.weight(sceneX, sceneY);
          if (weight <= 0.0) continue;

          if (response == null) {
            response = resolveResponse(normalizedX, normalizedY, responseZones, aspect);
            if (subjectMatched && normalMap != null && stage.getSubjectMaterial().normalStrength() > 0) {
              normal = normalMap.sample(normalizedX, normalizedY);
            }
          }
          if (light.layer == VnStagePreset.LightLayer.CHARACTER) {
            double directional = sceneLightDirectionalBias(
                sceneX, sceneY, light.targetX, light.targetY, light.sourceX, light.sourceY);
            double influence = weight * light.intensity * directional
                * response.responseScale * response.directFactor * response.depthFactor
                * material.response(normal, light.sourceX - sceneX, light.sourceY - sceneY, lightHeight);
            if (light.blend == VnStagePreset.LightBlend.PHYSICAL) {
              double albedo = linearLuminance(r, g, b);
              double tintWeight = clamp(influence * response.tintFactor, 0.0, 0.35);
              r = lightChannel(r, light.color.getRed(), influence, albedo, tintWeight);
              g = lightChannel(g, light.color.getGreen(), influence, albedo, tintWeight);
              b = lightChannel(b, light.color.getBlue(), influence, albedo, tintWeight);
            } else {
              r = light.blend.composite(r, light.color.getRed(), influence);
              g = light.blend.composite(g, light.color.getGreen(), influence);
              b = light.blend.composite(b, light.color.getBlue(), influence);
            }
          }

          double silhouetteStrength = light.silhouette * response.rimFactor;
          if (silhouetteStrength <= 1e-6) continue;
          if (edgeFactor < 0.0) {
            int left = alphaAt(source, sx - 1, sy, sourcePixelWidth, sourcePixelHeight);
            int right = alphaAt(source, sx + 1, sy, sourcePixelWidth, sourcePixelHeight);
            int up = alphaAt(source, sx, sy - 1, sourcePixelWidth, sourcePixelHeight);
            int down = alphaAt(source, sx, sy + 1, sourcePixelWidth, sourcePixelHeight);
            int difference = Math.max(Math.max(Math.abs(a - left), Math.abs(a - right)),
                Math.max(Math.abs(a - up), Math.abs(a - down)));
            edgeFactor = difference > 0 ? difference / 255.0 : 1.0 - (left + right + up + down) / 1020.0;
            edgeX = left - right;
            edgeY = up - down;
            double edgeLength = Math.sqrt(edgeX * edgeX + edgeY * edgeY);
            if (edgeLength > 0) {
              edgeX /= edgeLength;
              edgeY /= edgeLength;
            }
          }
          if (edgeFactor <= 0.0) continue;
          double lightDirX = light.sourceX - sceneX;
          double lightDirY = light.sourceY - sceneY;
          double lightDirLength = Math.hypot(lightDirX, lightDirY);
          double facing = 1.0;
          if (lightDirLength > 1e-6 && (edgeX != 0 || edgeY != 0)) {
            facing = clamp(
                edgeX * (lightDirX / lightDirLength) + edgeY * (lightDirY / lightDirLength),
                0.0,
                1.0
            );
          }
          double rimWeight = weight * light.intensity * silhouetteStrength * edgeFactor
              * (0.28 + 0.72 * facing) * response.depthFactor;
          if (light.blend == VnStagePreset.LightBlend.PHYSICAL) {
            double albedo = linearLuminance(r, g, b);
            r = applySceneLightChannel(r, light.color.getRed(), rimWeight, albedo, true);
            g = applySceneLightChannel(g, light.color.getGreen(), rimWeight, albedo, true);
            b = applySceneLightChannel(b, light.color.getBlue(), rimWeight, albedo, true);
          } else {
            r = light.blend.composite(r, light.color.getRed(), rimWeight);
            g = light.blend.composite(g, light.color.getGreen(), rimWeight);
            b = light.blend.composite(b, light.color.getBlue(), rimWeight);
          }
        }

        int rr = (int) Math.round(clamp(r, 0.0, 1.0) * 255.0);
        int gg = (int) Math.round(clamp(g, 0.0, 1.0) * 255.0);
        int bb = (int) Math.round(clamp(b, 0.0, 1.0) * 255.0);
        output[y * outWidth + x] = (a << 24) | (rr << 16) | (gg << 8) | bb;
      }
    }
    return new Raster(outWidth, outHeight, output);
  }

  private static double surfaceChannel(double channel, double exposure, double contrast) {
    double exposed = linearToSrgb(srgbToLinear(channel) * exposure);
    return 0.29 + (exposed - 0.29) * contrast;
  }

  private static double gradeChannel(double base, double tint, double luma, VnStagePreset.SubjectGrade grade) {
    double saturated = luma + (base - luma) * (1 + grade.saturation());
    double contrasted = 0.5 + (saturated - 0.5) * (1 + grade.contrast());
    return contrasted * (1 - grade.strength()) + tint * grade.strength();
  }

  record Raster(int width, int height, int[] pixels) {
    Image image() {
      WritableImage result = new WritableImage(width, height);
      result.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
      return result;
    }
  }

  record PixelData(int width, int height, int[] pixels) {
    static PixelData capture(Image source) {
      if (source == null) return null;
      var reader = source.getPixelReader();
      if (reader == null) return null;
      int w = (int) source.getWidth();
      int h = (int) source.getHeight();
      int[] pixels = new int[w * h];
      reader.getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
      return new PixelData(w, h, pixels);
    }

    int at(int x, int y) { return pixels[y * width + x]; }

    int sample(double u, double v) {
      return at((int) Math.round(u * (width - 1)), (int) Math.round(v * (height - 1)));
    }
  }

  private static double lightChannel(double base, double color, double influence, double albedo, double tintWeight) {
    return applyBlend(applySceneLightChannel(base, color, influence, albedo, false), color, tintWeight, 4);
  }

  static final class CharacterRig {
    private static final int MAX_SAMPLED_LIGHTS = 12;
    private final VnStagePreset stage;
    private final double width;
    private final double height;
    private List<CharacterLight> prepared;

    CharacterRig(VnStagePreset stage, double width, double height) {
      this.stage = stage;
      this.width = width;
      this.height = height;
    }

    synchronized List<CharacterLight> lights() {
      if (prepared != null) return prepared;
      List<CharacterLight> lights = new ArrayList<>();
      List<VnStageShadow> shadows = VnStageShadow.prepare(stage.getOccluders(), width, height);
      for (LightRuntime light : prepareLights(stage, width, height, false)) {
        List<VnStageShadow> blockers = light.castsShadows ? shadows : List.of();
        VnStageLightSamples samples = !blockers.isEmpty() && lights.size() < MAX_SAMPLED_LIGHTS
            ? new VnStageLightSamples(light.field, blockers, light.shadowQuality, width, height) : null;
        lights.add(new CharacterLight(light, blockers, samples));
      }
      if (!Thread.currentThread().isInterrupted()) prepared = lights;
      return lights;
    }
  }

  private record CharacterLight(LightRuntime light, List<VnStageShadow> shadows, VnStageLightSamples samples) {
    double weight(double x, double y) {
      if (samples != null) return samples.weight(x, y);
      double direct = light.field.weight(x, y);
      return direct <= 0 || shadows.isEmpty() ? direct
          : direct * light.field.transmission(shadows, x, y, light.shadowQuality);
    }
  }

  private static List<LightRuntime> prepareLights(VnStagePreset stage, double canvasWidth,
      double canvasHeight, boolean background) {
    List<LightRuntime> lights = new ArrayList<>();
    if (stage == null) return lights;
    for (VnStagePreset.Light light : stage.getLights()) {
      if (!stage.isActive(light)) continue;
      if (background ? light.layer() != VnStagePreset.LightLayer.BACKGROUND
          : light.layer() != VnStagePreset.LightLayer.CHARACTER && light.silhouette() <= 0.0) continue;
      double x = light.sceneX() * canvasWidth;
      double y = light.sceneY() * canvasHeight;
      double sourceX = light.sourceX() * canvasWidth;
      double sourceY = light.sourceY() * canvasHeight;
      VnStageLightField field = VnStageLightField.forLight(light, canvasWidth, canvasHeight, 1);
      var floor = background && light.groundProjection() != null && light.groundProjection().floorLight() > 0
          ? VnGroundShadows.FloorLight.from(light, canvasWidth / canvasHeight) : null;
      lights.add(new LightRuntime(light.layer(), x, y, sourceX, sourceY,
          parseColor(light.color(), Color.web("#ffd7a8")), clamp(light.intensity(), 0, 1),
          clamp(light.silhouette(), 0, 1), light.blend(), light.castsShadows() && light.groundProjection() == null, light.shadowQuality(), field, floor));
    }
    return lights;
  }

  private static List<ResponseRuntime> prepareResponseZones(VnStagePreset stage) {
    List<ResponseRuntime> zones = new ArrayList<>();
    if (stage == null) return zones;
    for (VnStagePreset.ResponseZone zone : stage.getResponseZones()) {
      if (zone == null) continue;
      List<double[]> polygon = null;
      if (zone.polygon() != null && !zone.polygon().isEmpty()) {
        polygon = new ArrayList<>();
        for (VnStagePreset.Point point : zone.polygon()) {
          polygon.add(new double[]{point.x(), point.y()});
        }
      }
      zones.add(new ResponseRuntime(
          zone.boundsX(),
          zone.boundsY(),
          zone.boundsW(),
          zone.boundsH(),
          zone.rotationDeg(),
          zone.surfaceClass(),
          zone.depthBias(),
          zone.responseScale(),
          polygon
      ));
    }
    return zones;
  }

  static Color parseColor(String raw, Color fallback) {
    try {
      if (raw == null || raw.isBlank()) return fallback;
      return Color.web(raw.trim());
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return fallback;
    }
  }

  private static ResponseInfluence resolveResponse(double nx, double ny, List<ResponseRuntime> zones, double aspect) {
    ResponseInfluence influence = ResponseInfluence.defaults();
    for (ResponseRuntime zone : zones) {
      double weight = zone.polygon != null && zone.polygon.size() >= 3
          ? polyZoneWeight(nx, ny, zone.polygon, 0.12)
          : zoneWeight(nx, ny, zone.boundsX, zone.boundsY, zone.boundsW, zone.boundsH, 0.14, Math.toRadians(zone.rotationDeg), aspect);
      if (weight <= 0.0) continue;
      ResponseInfluence candidate = influenceFor(zone.surfaceClass, zone.depthBias, zone.responseScale);
      influence = influence.mix(candidate, weight);
    }
    return influence;
  }

  private static ResponseInfluence influenceFor(VnStagePreset.SurfaceClass surfaceClass, double depthBias, double responseScale) {
    double scale = clamp(responseScale, 0.1, 2.5);
    return switch (surfaceClass) {
      case SKIN -> new ResponseInfluence(1.05 * scale, 0.92, 0.18, 0.92 + depthBias * 0.12, 0.88 + depthBias * 0.16);
      case HAIR -> new ResponseInfluence(0.96 * scale, 1.12, 0.14, 0.96 + depthBias * 0.10, 1.08 + depthBias * 0.20);
      case FABRIC -> new ResponseInfluence(0.92 * scale, 0.86, 0.10, 0.90 + depthBias * 0.08, 0.84 + depthBias * 0.12);
      case METAL -> new ResponseInfluence(1.18 * scale, 1.34, 0.20, 1.06 + depthBias * 0.14, 1.28 + depthBias * 0.24);
      case GLASS -> new ResponseInfluence(0.86 * scale, 1.22, 0.12, 0.88 + depthBias * 0.10, 1.10 + depthBias * 0.18);
      default -> new ResponseInfluence(scale, 1.0, 0.12, 1.0 + depthBias * 0.10, 1.0 + depthBias * 0.14);
    };
  }

  static double sceneLightDirectionalBias(double sceneX,
                                          double sceneY,
                                          double targetX,
                                          double targetY,
                                          double sourceX,
                                          double sourceY) {
    double towardSourceX = sourceX - targetX;
    double towardSourceY = sourceY - targetY;
    double towardSourceLength = Math.hypot(towardSourceX, towardSourceY);
    double pixelOffsetX = sceneX - targetX;
    double pixelOffsetY = sceneY - targetY;
    double pixelOffsetLength = Math.hypot(pixelOffsetX, pixelOffsetY);
    if (towardSourceLength < 1e-6 || pixelOffsetLength < 1e-6) return 1.0;
    double facing = clamp(
        (pixelOffsetX / pixelOffsetLength) * (towardSourceX / towardSourceLength)
            + (pixelOffsetY / pixelOffsetLength) * (towardSourceY / towardSourceLength),
        -1.0,
        1.0
    );
    return 0.78 + 0.22 * Math.max(0.0, facing);
  }

  static double applySceneLightChannel(double base, double light, double influence, double albedo, boolean rim) {
    double safeBase = clamp(base, 0.0, 1.0);
    double safeLight = clamp(light, 0.0, 1.0);
    double strength = Math.max(0.0, influence);
    if (strength <= 1e-6) return safeBase;
    double safeAlbedo = clamp(albedo, 0.0, 1.0);
    double exposure = strength * (rim ? 1.85 : 1.35) * (0.55 + 0.45 * Math.sqrt(Math.max(0.02, safeAlbedo)));
    double litLinear = 1.0 - Math.exp(-(srgbToLinear(safeBase) + srgbToLinear(safeLight) * exposure));
    double lit = linearToSrgb(clamp(litLinear, 0.0, 1.0));
    double blendWeight = clamp(strength * (rim ? 0.92 : 0.78), 0.0, 1.0);
    return clamp(safeBase * (1.0 - blendWeight) + lit * blendWeight, 0.0, 1.0);
  }

  static double applyBlend(double base, double tone, double weight, int mode) {
    return VnStageGradeBlend.fastComposite(base, tone, weight, mode);
  }

  private static int alphaAt(PixelData source, int x, int y, int width, int height) {
    int safeX = Math.max(0, Math.min(width - 1, x));
    int safeY = Math.max(0, Math.min(height - 1, y));
    return (source.at(safeX, safeY) >>> 24) & 0xFF;
  }

  private static double zoneWeight(double nx, double ny, double zx, double zy, double zw, double zh, double feather, double rotRad, double imgAspect) {
    if (zw <= 0 || zh <= 0) return 0.0;
    if (rotRad != 0.0) {
      double cx = zx + zw * 0.5;
      double cy = zy + zh * 0.5;
      double cosR = Math.cos(-rotRad);
      double sinR = Math.sin(-rotRad);
      double dx = nx - cx;
      double dy = (ny - cy) * imgAspect;
      nx = cx + dx * cosR - dy * sinR;
      ny = cy + (dx * sinR + dy * cosR) / imgAspect;
    }
    double x1 = zx;
    double y1 = zy;
    double x2 = zx + zw;
    double y2 = zy + zh;
    if (nx < x1 || nx > x2 || ny < y1 || ny > y2) {
      if (feather <= 0.0) return 0.0;
      double dx = nx < x1 ? x1 - nx : (nx > x2 ? nx - x2 : 0.0);
      double dy = ny < y1 ? y1 - ny : (ny > y2 ? ny - y2 : 0.0);
      double dist = Math.sqrt(dx * dx + dy * dy);
      if (dist >= feather) return 0.0;
      return 1.0 - dist / feather;
    }
    return 1.0;
  }

  private static double polyZoneWeight(double px, double py, List<double[]> poly, double feather) {
    int n = poly == null ? 0 : poly.size();
    if (n < 3) return 0.0;
    boolean inside = false;
    for (int i = 0, j = n - 1; i < n; j = i++) {
      double yi = poly.get(i)[1];
      double yj = poly.get(j)[1];
      double xi = poly.get(i)[0];
      double xj = poly.get(j)[0];
      if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
        inside = !inside;
      }
    }
    if (inside) return 1.0;
    if (feather <= 0.0) return 0.0;
    double minDist = Double.MAX_VALUE;
    for (int i = 0, j = n - 1; i < n; j = i++) {
      double d = pointToSegmentDist(px, py, poly.get(j)[0], poly.get(j)[1], poly.get(i)[0], poly.get(i)[1]);
      if (d < minDist) minDist = d;
    }
    if (minDist >= feather) return 0.0;
    return 1.0 - minDist / feather;
  }

  private static double pointToSegmentDist(double px, double py, double ax, double ay, double bx, double by) {
    double dx = bx - ax;
    double dy = by - ay;
    double lenSq = dx * dx + dy * dy;
    if (lenSq < 1e-12) return Math.hypot(px - ax, py - ay);
    double t = clamp(((px - ax) * dx + (py - ay) * dy) / lenSq, 0.0, 1.0);
    double cx = ax + t * dx;
    double cy = ay + t * dy;
    return Math.hypot(px - cx, py - cy);
  }

  static double linearLuminance(double r, double g, double b) {
    return clamp(0.2126 * srgbToLinear(r) + 0.7152 * srgbToLinear(g) + 0.0722 * srgbToLinear(b), 0.0, 1.0);
  }



  private static double srgbToLinear(double value) { return VnStageGradeBlend.srgbToLinear(value); }

  private static double linearToSrgb(double value) { return VnStageGradeBlend.linearToSrgb(value); }

  static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private record LightRuntime(
      VnStagePreset.LightLayer layer,
      double targetX,
      double targetY,
      double sourceX,
      double sourceY,
      Color color,
      double intensity,
      double silhouette,
      VnStagePreset.LightBlend blend,
      boolean castsShadows,
      VnStagePreset.ShadowQuality shadowQuality,
      VnStageLightField field,
      VnGroundShadows.FloorLight floor
  ) {}

  private record ResponseRuntime(
      double boundsX,
      double boundsY,
      double boundsW,
      double boundsH,
      double rotationDeg,
      VnStagePreset.SurfaceClass surfaceClass,
      double depthBias,
      double responseScale,
      List<double[]> polygon
  ) {}

  private record ResponseInfluence(
      double responseScale,
      double rimFactor,
      double tintFactor,
      double directFactor,
      double depthFactor
  ) {
    private static final ResponseInfluence DEFAULT = new ResponseInfluence(1.0, 1.0, 0.12, 1.0, 1.0);

    static ResponseInfluence defaults() { return DEFAULT; }

    ResponseInfluence mix(ResponseInfluence other, double weight) {
      double w = clamp(weight, 0.0, 1.0);
      return new ResponseInfluence(
          lerp(responseScale, other.responseScale, w),
          lerp(rimFactor, other.rimFactor, w),
          lerp(tintFactor, other.tintFactor, w),
          lerp(directFactor, other.directFactor, w),
          lerp(depthFactor, other.depthFactor, w)
      );
    }
  }

  private static double lerp(double a, double b, double t) {
    return a * (1.0 - t) + b * t;
  }
}
