package com.jvn.fx.scene2d;

import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.math.Capsule2;
import com.jvn.core.assets.BoundedImageCache;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RenderFeature;
import com.jvn.core.scene2d.RenderBlendMode;
import com.jvn.core.scene2d.RenderDiagnostics;
import com.jvn.core.scene2d.RenderTarget2D;
import com.jvn.core.scene2d.RendererCapabilities;
import com.jvn.core.scene2d.TextFontMetrics2D;
import com.jvn.fx.FxImageMemory;

import javafx.geometry.VPos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

public class FxBlitter2D implements Blitter2D {
  public static final RendererCapabilities CAPABILITIES = RendererCapabilities.of(
      "JavaFX",
      RenderFeature.AFFINE_TRANSFORM,
      RenderFeature.VECTOR_PATHS,
      RenderFeature.ADVANCED_STROKE,
      RenderFeature.RECTANGULAR_CLIP,
      RenderFeature.POLYGONS,
      RenderFeature.TEXT_ALIGNMENT,
      RenderFeature.COLOR_MATRIX,
      RenderFeature.BLUR,
      RenderFeature.OFFSCREEN_RENDER_TARGETS,
      RenderFeature.ALPHA_MASKS,
      RenderFeature.TEXT_LAYOUT)
      .withBlendModes(
          RenderBlendMode.NORMAL,
          RenderBlendMode.ADDITIVE,
          RenderBlendMode.MULTIPLY,
          RenderBlendMode.SCREEN);
  private static final Logger log = LoggerFactory.getLogger(FxBlitter2D.class);

  private static final double[] IDENTITY_COLOR_MATRIX = new double[] {
      1.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 1.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 1.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 1.0, 0.0
  };
  private static final double COLOR_MATRIX_EPSILON = 1e-9;

  private static final class RenderState {
    final double[] colorMatrix = new double[IDENTITY_COLOR_MATRIX.length];
    boolean colorMatrixSet = false;
    double blurRadius = 0.0;

    void copyFrom(RenderState source) {
      colorMatrixSet = source.colorMatrixSet;
      if (colorMatrixSet) {
        System.arraycopy(source.colorMatrix, 0, colorMatrix, 0, colorMatrix.length);
      }
      blurRadius = source.blurRadius;
    }

    void reset() {
      colorMatrixSet = false;
      blurRadius = 0.0;
    }

    boolean hasColorMatrix() {
      if (!colorMatrixSet) return false;
      for (int i = 0; i < IDENTITY_COLOR_MATRIX.length; i++) {
        if (Math.abs(colorMatrix[i] - IDENTITY_COLOR_MATRIX[i]) > COLOR_MATRIX_EPSILON) return true;
      }
      return false;
    }

    double gpuBrightness() {
      return colorMatrixSet ? gpuBrightnessFor(colorMatrix) : Double.NaN;
    }
  }

  private final GraphicsContext gc;
  private final FxTextMeasurementCache textMeasurements = new FxTextMeasurementCache();
  private double[] polygonX = new double[0];
  private double[] polygonY = new double[0];
  private double viewportW = 0;
  private double viewportH = 0;
  private static final int CACHE_MAX_ENTRIES = 128;
  private static final long SOURCE_CACHE_MAX_BYTES = 96L * 1024L * 1024L;
  private static final long PROCESSED_CACHE_MAX_BYTES = 64L * 1024L * 1024L;
  private final BoundedImageCache<Image> cache = new BoundedImageCache<>(
      CACHE_MAX_ENTRIES, SOURCE_CACHE_MAX_BYTES, FxImageMemory::estimatedBytes);
  private final BoundedImageCache<Image> processedCache = new BoundedImageCache<>(
      CACHE_MAX_ENTRIES, PROCESSED_CACHE_MAX_BYTES, FxImageMemory::estimatedBytes);
  private final Set<String> missing = new HashSet<>();
  private final Deque<RenderState> stateStack = new ArrayDeque<>();
  private final Deque<RenderState> statePool = new ArrayDeque<>();
  private RenderState state = new RenderState();
  private FxRenderTarget2D ownerTarget;
  
  private final Map<String, MediaPlayer> videoPlayers = new LinkedHashMap<>();
  private final Map<String, MediaView> videoViews = new LinkedHashMap<>();
  private final Map<String, WritableImage> videoFrames = new LinkedHashMap<>();

  public FxBlitter2D(GraphicsContext gc) {
    this.gc = gc;
  }

  @Override
  public RendererCapabilities getCapabilities() {
    return CAPABILITIES;
  }

  public void setViewport(double w, double h) {
    this.viewportW = w;
    this.viewportH = h;
  }

  void attachRenderTarget(FxRenderTarget2D target) {
    this.ownerTarget = target;
  }

  private void markOwnerDirty() {
    if (ownerTarget != null) ownerTarget.markDirty();
  }

  @Override
  public void clear(double r, double g, double b, double a) {
    markOwnerDirty();
    gc.setFill(Color.color(clamp01(r), clamp01(g), clamp01(b), clamp01(a)));
    gc.fillRect(0, 0, viewportW, viewportH);
  }

  @Override
  public void setFill(double r, double g, double b, double a) {
    gc.setFill(Color.color(clamp01(r), clamp01(g), clamp01(b), clamp01(a)));
  }

  @Override
  public void setStroke(double r, double g, double b, double a) {
    gc.setStroke(Color.color(clamp01(r), clamp01(g), clamp01(b), clamp01(a)));
  }

  @Override
  public void setStrokeWidth(double w) { gc.setLineWidth(w); }

  @Override
  public void setGlobalAlpha(double a) { gc.setGlobalAlpha(clamp01(a)); }

  @Override
  public void setFont(String family, double size, boolean bold) {
    String fam = (family == null || family.isBlank()) ? "Arial" : family;
    gc.setFont(textMeasurements.font(fam, size, bold));
  }

  @Override
  public void push() {
    gc.save();
    RenderState next = statePool.pollFirst();
    if (next == null) next = new RenderState();
    next.copyFrom(state);
    stateStack.push(state);
    state = next;
  }

  @Override
  public void pop() {
    gc.restore();
    RenderState completed = state;
    RenderState restored = stateStack.pollFirst();
    if (restored == null) {
      completed.reset();
      state = completed;
    } else {
      state = restored;
      completed.reset();
      statePool.offerFirst(completed);
    }
    // GraphicsContext.restore() already restores the saved Effect. Re-applying it
    // here would allocate another shader/effect chain at every nested entity pop.
  }

  @Override
  public void translate(double x, double y) { gc.translate(x, y); }

  @Override
  public void rotateDeg(double degrees) { gc.rotate(degrees); }

  @Override
  public void scale(double sx, double sy) { gc.scale(sx, sy); }

  @Override
  public void transform(double mxx, double myx, double mxy, double myy, double tx, double ty) {
    gc.transform(mxx, myx, mxy, myy, tx, ty);
  }

  @Override
  public void fillRect(double x, double y, double w, double h) { markOwnerDirty(); gc.fillRect(x, y, w, h); }

  @Override
  public void strokeRect(double x, double y, double w, double h) { markOwnerDirty(); gc.strokeRect(x, y, w, h); }

  @Override
  public void fillCircle(double cx, double cy, double radius) {
    markOwnerDirty();
    double d = radius * 2;
    gc.fillOval(cx - radius, cy - radius, d, d);
  }

  @Override
  public void strokeCircle(double cx, double cy, double radius) {
    markOwnerDirty();
    double d = radius * 2;
    gc.strokeOval(cx - radius, cy - radius, d, d);
  }

  @Override
  public void drawLine(double x1, double y1, double x2, double y2) { markOwnerDirty(); gc.strokeLine(x1, y1, x2, y2); }

  @Override
  public void drawImage(String classpath, double x, double y, double w, double h) {
    if (classpath == null || classpath.isBlank()) return;
    String lower = classpath.toLowerCase();
    if (lower.endsWith(".mp4") || lower.endsWith(".mov")) {
        drawVideo(classpath, x, y, w, h, -1, -1, -1, -1, false);
        return;
    }

    Image img = cache.computeIfAbsent(classpath, this::loadImage);
    if (img != null) {
      markOwnerDirty();
      gc.drawImage(resolveProcessedImage(classpath, img), x, y, w, h);
    } else {
      markOwnerDirty();
      MissingAssetPlaceholder.report(gc, classpath, null, x, y, w, h);
    }
  }

  @Override
  public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
                              double dx, double dy, double dw, double dh) {
    if (classpath == null || classpath.isBlank()) return;
    String lower = classpath.toLowerCase();
    if (lower.endsWith(".mp4") || lower.endsWith(".mov")) {
        drawVideo(classpath, dx, dy, dw, dh, sx, sy, sw, sh, true);
        return;
    }

    Image img = cache.computeIfAbsent(classpath, this::loadImage);
    if (img != null) {
      markOwnerDirty();
      gc.drawImage(resolveProcessedImage(classpath, img), sx, sy, sw, sh, dx, dy, dw, dh);
    } else {
      markOwnerDirty();
      MissingAssetPlaceholder.report(gc, classpath, null, dx, dy, dw, dh);
    }
  }

  @Override
  public void drawText(String text, double x, double y, double size, boolean bold) {
    if (text == null) return;
    markOwnerDirty();
    Font cur = gc.getFont();
    String fam = (cur != null && cur.getFamily() != null && !cur.getFamily().isBlank()) ? cur.getFamily() : "Arial";
    gc.setFont(textMeasurements.font(fam, size, bold));
    gc.fillText(text, x, y);
  }

  @Override
  public double measureTextWidth(String text, double size, boolean bold) {
    if (text == null) return 0;
    Font cur = gc.getFont();
    String fam = (cur != null && cur.getFamily() != null && !cur.getFamily().isBlank()) ? cur.getFamily() : "Arial";
    return textMeasurements.measure(text, fam, size, bold).width();
  }

  @Override
  public TextFontMetrics2D measureTextMetrics(String text, String family, double size, boolean bold) {
    return textMeasurements.measure(text, family, size, bold);
  }

  @Override
  public boolean isFontAvailable(String family) {
    return family != null && Font.getFamilies().stream().anyMatch(name -> name.equalsIgnoreCase(family));
  }

  @Override
  public void setClipRect(double x, double y, double w, double h) {
    gc.beginPath();
    gc.rect(x, y, w, h);
    gc.closePath();
    gc.clip();
  }

  @Override
  public void setTextAlign(String hAlign, String vAlign) {
    if (hAlign != null) {
      switch (hAlign.toLowerCase()) {
        case "center" -> gc.setTextAlign(TextAlignment.CENTER);
        case "right" -> gc.setTextAlign(TextAlignment.RIGHT);
        default -> gc.setTextAlign(TextAlignment.LEFT);
      }
    }
    if (vAlign != null) {
      switch (vAlign.toLowerCase()) {
        case "top" -> gc.setTextBaseline(VPos.TOP);
        case "middle" -> gc.setTextBaseline(VPos.CENTER);
        case "bottom" -> gc.setTextBaseline(VPos.BOTTOM);
        default -> gc.setTextBaseline(VPos.BASELINE);
      }
    }
  }

  @Override
  public void setBlendMode(String mode) {
    if (mode == null || mode.isBlank()) {
      gc.setGlobalBlendMode(BlendMode.SRC_OVER);
      return;
    }
    switch (mode.toLowerCase()) {
      case "normal", "source-over":
        gc.setGlobalBlendMode(BlendMode.SRC_OVER); break;
      case "add": case "additive":
        gc.setGlobalBlendMode(BlendMode.ADD); break;
      case "multiply":
        gc.setGlobalBlendMode(BlendMode.MULTIPLY); break;
      case "screen":
        gc.setGlobalBlendMode(BlendMode.SCREEN); break;
      default: {
        RenderDiagnostics.unsupported(this, RenderFeature.BLEND_MODES, "setBlendMode(" + mode + ")");
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
      }
    }
  }

  @Override
  public void beginPath() {
    gc.beginPath();
  }

  @Override
  public void moveTo(double x, double y) {
    gc.moveTo(x, y);
  }

  @Override
  public void lineTo(double x, double y) {
    gc.lineTo(x, y);
  }

  @Override
  public void closePath() {
    gc.closePath();
  }

  @Override
  public void fillPath() {
    markOwnerDirty();
    gc.fill();
  }

  @Override
  public void strokePath() {
    markOwnerDirty();
    gc.stroke();
  }

  @Override
  public void setStrokeCap(String cap) {
    String normalized = cap == null ? "square" : cap.toLowerCase();
    gc.setLineCap(switch (normalized) {
      case "butt" -> javafx.scene.shape.StrokeLineCap.BUTT;
      case "round" -> javafx.scene.shape.StrokeLineCap.ROUND;
      default -> javafx.scene.shape.StrokeLineCap.SQUARE;
    });
  }

  @Override
  public void setStrokeJoin(String join) {
    String normalized = join == null ? "miter" : join.toLowerCase();
    gc.setLineJoin(switch (normalized) {
      case "round" -> javafx.scene.shape.StrokeLineJoin.ROUND;
      case "bevel" -> javafx.scene.shape.StrokeLineJoin.BEVEL;
      default -> javafx.scene.shape.StrokeLineJoin.MITER;
    });
  }

  @Override
  public void setMiterLimit(double limit) {
    gc.setMiterLimit(limit);
  }

  @Override
  public void setDash(double[] dashes, double phase) {
    gc.setLineDashes(dashes == null ? new double[0] : dashes);
    gc.setLineDashOffset(phase);
  }

  @Override
  public void fillPolygon(double[] xy) {
    if (xy == null || xy.length < 6 || xy.length % 2 != 0) return;
    markOwnerDirty();
    int count = xy.length / 2;
    preparePolygon(xy, count);
    gc.fillPolygon(polygonX, polygonY, count);
  }

  @Override
  public void strokePolygon(double[] xy) {
    if (xy == null || xy.length < 6 || xy.length % 2 != 0) return;
    markOwnerDirty();
    int count = xy.length / 2;
    preparePolygon(xy, count);
    gc.strokePolygon(polygonX, polygonY, count);
  }

  private void preparePolygon(double[] xy, int count) {
    // GraphicsContext copies coordinates into its command buffer during the call.
    if (polygonX.length < count) {
      int capacity = Math.max(count, Math.max(8, polygonX.length * 2));
      polygonX = new double[capacity];
      polygonY = new double[capacity];
    }
    for (int i = 0; i < count; i++) {
      polygonX[i] = xy[i * 2];
      polygonY[i] = xy[i * 2 + 1];
    }
  }

  @Override
  public void fillCapsule(Capsule2 capsule) {
    if (capsule == null) return;
    markOwnerDirty();
    gc.save();
    gc.setStroke(gc.getFill());
    gc.setLineWidth(capsule.r * 2.0);
    gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
    gc.strokeLine(capsule.x1, capsule.y1, capsule.x2, capsule.y2);
    gc.restore();
  }

  @Override
  public void setColorMatrix(double[] matrix) {
    if (matrix == null || matrix.length < IDENTITY_COLOR_MATRIX.length) {
      clearColorMatrix();
      return;
    }
    double previousGpuBrightness = state.gpuBrightness();
    if (state.colorMatrixSet && sameColorMatrix(state.colorMatrix, matrix)) return;
    System.arraycopy(matrix, 0, state.colorMatrix, 0, state.colorMatrix.length);
    state.colorMatrixSet = true;
    if (Double.compare(previousGpuBrightness, state.gpuBrightness()) != 0) {
      applyEffectState();
    }
  }

  @Override
  public void clearColorMatrix() {
    if (!state.colorMatrixSet) return;
    double previousGpuBrightness = state.gpuBrightness();
    state.colorMatrixSet = false;
    if (!Double.isNaN(previousGpuBrightness)) applyEffectState();
  }

  @Override
  public void setBlurRadius(double radius) {
    double nextRadius = Math.max(0.0, radius);
    if (Math.abs(state.blurRadius - nextRadius) <= COLOR_MATRIX_EPSILON) return;
    state.blurRadius = nextRadius;
    applyEffectState();
  }

  @Override
  public RenderTarget2D createRenderTarget(double width, double height, double pixelScale) {
    return new FxRenderTarget2D(width, height, pixelScale);
  }

  @Override
  public void drawRenderTarget(RenderTarget2D target, double x, double y, double width, double height) {
    if (!(target instanceof FxRenderTarget2D fxTarget)) {
      throw new IllegalArgumentException("FxBlitter2D requires a JavaFX render target");
    }
    Image image = fxTarget.snapshot();
    if (state.hasColorMatrix() && !hasGpuColorMatrix()) {
      image = applyColorMatrix(image, state.colorMatrix);
    }
    markOwnerDirty();
    gc.drawImage(image, x, y, width, height);
  }

  @Override
  public void applyAlphaMask(RenderTarget2D mask) {
    if (ownerTarget == null) {
      throw new IllegalStateException("Alpha masks can only be applied while drawing an offscreen target");
    }
    if (!(mask instanceof FxRenderTarget2D fxMask)) {
      throw new IllegalArgumentException("FxBlitter2D requires a JavaFX mask target");
    }
    WritableImage contentImage = ownerTarget.snapshot();
    WritableImage maskImage = fxMask.snapshot();
    int width = (int) contentImage.getWidth();
    int height = (int) contentImage.getHeight();
    int maskWidth = (int) maskImage.getWidth();
    int maskHeight = (int) maskImage.getHeight();
    WritableImage result = new WritableImage(width, height);
    PixelReader contentReader = contentImage.getPixelReader();
    PixelReader maskReader = maskImage.getPixelReader();
    PixelWriter writer = result.getPixelWriter();
    int[] contentPixels = new int[width * height];
    int[] maskPixels = new int[maskWidth * maskHeight];
    contentReader.getPixels(
        0, 0, width, height, PixelFormat.getIntArgbInstance(), contentPixels, 0, width);
    maskReader.getPixels(
        0, 0, maskWidth, maskHeight, PixelFormat.getIntArgbInstance(), maskPixels, 0, maskWidth);
    PixelEffects.applyAlphaMask(contentPixels, width, height, maskPixels, maskWidth, maskHeight);
    writer.setPixels(
        0, 0, width, height, PixelFormat.getIntArgbInstance(), contentPixels, 0, width);
    markOwnerDirty();
    gc.save();
    gc.setTransform(1, 0, 0, 1, 0, 0);
    gc.setGlobalAlpha(1.0);
    gc.setGlobalBlendMode(BlendMode.SRC_OVER);
    gc.setEffect(null);
    gc.clearRect(0, 0, width, height);
    gc.drawImage(result, 0, 0);
    gc.restore();
  }

  public void setCacheCapacity(int capacity) {
    int boundedCapacity = Math.max(16, capacity);
    cache.setMaxEntries(boundedCapacity);
    processedCache.setMaxEntries(boundedCapacity);
  }
  public void evict(String path) {
    if (path != null) {
      cache.remove(path);
      processedCache.removeKeysIf(key -> key.startsWith(path + "::"));
      missing.remove(path);
      MediaPlayer mp = videoPlayers.remove(path);
      if (mp != null) {
          mp.stop();
          mp.dispose();
      }
      videoViews.remove(path);
      videoFrames.remove(path);
    }
  }
  public void clearCache() {
    cache.clear();
    processedCache.clear();
    missing.clear();
    for (MediaPlayer mp : videoPlayers.values()) {
        mp.stop();
        mp.dispose();
    }
    videoPlayers.clear();
    videoViews.clear();
    videoFrames.clear();
  }

  private Image loadImage(String path) {
    String url = resolveMediaUrl(path);
    if (url != null) return new Image(url);
    return null;
  }

  private String resolveMediaUrl(String path) {
    try {
      URL u = getClass().getClassLoader().getResource(path);
      if (u != null) return u.toExternalForm();

      java.io.File f = new java.io.File(path);
      if (f.isAbsolute() && f.exists()) return f.toURI().toString();

      if (projectRoot != null) {
        java.io.File pf = new java.io.File(projectRoot, path);
        if (pf.exists()) return pf.toURI().toString();
      }
    } catch (Exception e) {
      log.warn("Failed to resolve asset path: {}", path, e);
    }
    return null;
  }

  private java.io.File projectRoot;
  public void setProjectRoot(java.io.File root) {
    if (java.util.Objects.equals(this.projectRoot, root)) return;
    clearCache();
    this.projectRoot = root;
  }

  private double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

  private void applyEffectState() {
    Effect effect = null;
    double gpuBrightness = state.gpuBrightness();
    if (!Double.isNaN(gpuBrightness)) {
      // For values from 0 through 1 JavaFX's ColorAdjust shader scales HSB value,
      // which is exactly the uniform RGB multiplication represented by this matrix.
      // Prism executes the effect on the selected graphics pipeline, avoiding a CPU
      // pixel pass and a new texture upload for every animated Puppeteer frame.
      ColorAdjust brightness = new ColorAdjust();
      brightness.setBrightness(gpuBrightness - 1.0);
      effect = brightness;
    }
    if (state.blurRadius > 1e-9) {
      GaussianBlur blur = new GaussianBlur(Math.min(63.0, state.blurRadius));
      blur.setInput(effect);
      effect = blur;
    }
    gc.setEffect(effect);
  }

  private Image resolveProcessedImage(String path, Image source) {
    if (source == null || !state.hasColorMatrix() || hasGpuColorMatrix()) return source;
    String cacheKey = buildProcessedKey(path, state.colorMatrix);
    return processedCache.computeIfAbsent(cacheKey, key -> applyColorMatrix(source, state.colorMatrix));
  }

  private boolean hasGpuColorMatrix() {
    return !Double.isNaN(state.gpuBrightness());
  }

  static double gpuBrightnessFor(double[] matrix) {
    if (matrix == null || matrix.length < IDENTITY_COLOR_MATRIX.length) return Double.NaN;
    double brightness = matrix[0];
    if (!Double.isFinite(brightness) || brightness < 0.0 || brightness >= 1.0) {
      return Double.NaN;
    }
    for (int i = 0; i < IDENTITY_COLOR_MATRIX.length; i++) {
      double expected = switch (i) {
        case 0, 6, 12 -> brightness;
        case 18 -> 1.0;
        default -> 0.0;
      };
      if (Math.abs(matrix[i] - expected) > COLOR_MATRIX_EPSILON) return Double.NaN;
    }
    return brightness;
  }

  private static boolean sameColorMatrix(double[] left, double[] right) {
    for (int i = 0; i < IDENTITY_COLOR_MATRIX.length; i++) {
      if (Double.compare(left[i], right[i]) != 0) return false;
    }
    return true;
  }

  private String buildProcessedKey(String path, double[] matrix) {
    StringBuilder sb = new StringBuilder(path).append("::");
    for (int i = 0; i < IDENTITY_COLOR_MATRIX.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(Math.round(matrix[i] * 100000.0) / 100000.0);
    }
    return sb.toString();
  }

  private WritableImage applyColorMatrix(Image source, double[] matrix) {
    int width = Math.max(1, (int) Math.round(source.getWidth()));
    int height = Math.max(1, (int) Math.round(source.getHeight()));
    WritableImage output = new WritableImage(width, height);
    PixelReader reader = source.getPixelReader();
    PixelWriter writer = output.getPixelWriter();
    if (reader == null || writer == null) return output;
    int[] pixels = new int[width * height];
    reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
    PixelEffects.applyColorMatrix(pixels, matrix);
    writer.setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
    return output;
  }

  private void drawVideo(String path, double dx, double dy, double dw, double dh, double sx, double sy, double sw, double sh, boolean isRegion) {
      MediaPlayer player = videoPlayers.get(path);
      if (player == null && !missing.contains(path)) {
          String url = resolveMediaUrl(path);
          if (url != null) {
              Media media = new Media(url);
              player = new MediaPlayer(media);
              player.setCycleCount(MediaPlayer.INDEFINITE);
              player.setMute(true);
              player.play();
              videoPlayers.put(path, player);
              
              MediaView view = new MediaView(player);
              videoViews.put(path, view);
          } else {
              missing.add(path);
          }
      }
      
      if (player != null) {
          MediaView view = videoViews.get(path);
          Media media = player.getMedia();
          int vw = media.getWidth();
          int vh = media.getHeight();
          if (vw > 0 && vh > 0) {
              markOwnerDirty();
              WritableImage frame = videoFrames.get(path);
              if (frame == null || frame.getWidth() != vw || frame.getHeight() != vh) {
                  frame = new WritableImage(vw, vh);
                  videoFrames.put(path, frame);
              }
              SnapshotParameters params = new SnapshotParameters();
              params.setFill(Color.TRANSPARENT);
              view.snapshot(params, frame);
              
              Image processed = resolveProcessedImage(path, frame);
              
              if (isRegion) {
                  gc.drawImage(processed, sx, sy, sw, sh, dx, dy, dw, dh);
              } else {
                  gc.drawImage(processed, dx, dy, dw, dh);
              }
              return;
          }
      }
      
      markOwnerDirty();
      MissingAssetPlaceholder.report(gc, path, "video", dx, dy, dw, dh);
  }
}
