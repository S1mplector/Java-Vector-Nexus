package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.assets.OverlayAssetManager;
import com.jvn.core.vn.DefaultVnInterop;
import com.jvn.core.vn.VnCharacterSceneAccessor;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioLoader;
import com.jvn.core.vn.VnScene;
import com.jvn.editor.EditorApp;
import com.jvn.editor.ui.actioneditor.AnimationProject;
import com.jvn.editor.ui.actioneditor.CodeImporter;
import com.jvn.editor.ui.actioneditor.EntityTrack;
import com.jvn.editor.ui.actioneditor.PropertyType;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import com.jvn.fx.vn.VnRenderer;
import com.jvn.scripting.jes.runtime.JesScene2D;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Opt-in visual oracle for Was I Write launch-at-cursor reconstruction.
 *
 * <p>The canonical side is the real VNS runtime and renderer, advanced linearly through each
 * script. The comparison side is the actual scene built by Puppeteer, including its launch-time
 * replay of prior inline timelines. Dialogue UI is hidden on the canonical side so the pixel
 * comparison covers scene content only.
 *
 * <p>Set {@code JVN_PUPPETEER_AUDIT_ROOT} to any JVN project. Optional settings are
 * {@code JVN_PUPPETEER_AUDIT_OUTPUT}, {@code JVN_PUPPETEER_AUDIT_SCRIPT}, and
 * {@code JVN_PUPPETEER_AUDIT_CHECKPOINTS}; checkpoint modes are {@code scene-changes}
 * (default), {@code grouped-timelines}, and {@code all-lines}. Each checkpoint writes a labeled
 * canonical/Puppeteer/diff contact sheet and one row in {@code metrics.csv}.
 */
@ExtendWith(FxToolkitExtension.class)
@SuppressWarnings("NullAway")
class PuppeteerVisualParityAuditTest {
  private static final int WIDTH = 1920;
  private static final int HEIGHT = 1080;
  private static final int SHEET_PANEL_WIDTH = 640;
  private static final int SHEET_PANEL_HEIGHT = 360;

  @Test
  void rendersCanonicalRuntimeAndPuppeteerAtSelectedProjectCheckpoints() throws Exception {
    String configuredRoot = setting("jvnPuppeteerAuditRoot", "JVN_PUPPETEER_AUDIT_ROOT", "");
    if (configuredRoot.isBlank()) {
      // Compatibility with the first corpus used to develop the general auditor.
      configuredRoot = setting("wasIWriteRoot", "WAS_I_WRITE_ROOT", "");
    }
    assumeTrue(!configuredRoot.isBlank(),
        "Set JVN_PUPPETEER_AUDIT_ROOT=/path/to/JVN-project to run the visual audit");
    Path projectRoot = Path.of(configuredRoot).toAbsolutePath().normalize();
    assumeTrue(Files.isRegularFile(projectRoot.resolve("jvn.project")),
        "Was I Write project root not found: " + projectRoot);
    Path outputRoot = Path.of(setting(
        "jvnPuppeteerAuditOutput",
        "JVN_PUPPETEER_AUDIT_OUTPUT",
        "build/reports/puppeteer-visual-parity")).toAbsolutePath().normalize();
    CheckpointMode checkpointMode = CheckpointMode.parse(setting(
        "jvnPuppeteerAuditCheckpoints", "JVN_PUPPETEER_AUDIT_CHECKPOINTS", "scene-changes"));
    String scriptFilter = setting(
        "jvnPuppeteerAuditScript", "JVN_PUPPETEER_AUDIT_SCRIPT", "").replace('\\', '/');
    Files.createDirectories(outputRoot);

    AssetCatalog.setDefaultManager(new OverlayAssetManager(
        new FilesystemAssetManager(projectRoot), new ClasspathAssetManager()));
    System.setProperty("jvn.assets.root", projectRoot.toString());
    System.setProperty("jvn.render.width", Integer.toString(WIDTH));
    System.setProperty("jvn.render.height", Integer.toString(HEIGHT));

    EditorApp app = configuredEditor(projectRoot);
    Method buildScene = privateMethod(
        "buildSceneFromSnapshot", PuppeteerLauncherPanel.SceneSnapshot.class);
    Method captureBaselines = privateMethod("captureRuntimeExportBaselines", JesScene2D.class);
    Method applyTimelineEndState = privateMethod(
        "applySnapshotTimelineEndStateToScene",
        JesScene2D.class,
        PuppeteerLauncherPanel.SceneSnapshot.class,
        String.class,
        Map.class);

    List<Path> scripts;
    try (Stream<Path> stream = Files.walk(projectRoot.resolve("scripts"))) {
      scripts = stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".vns"))
          .sorted(Comparator.comparing(path -> projectRoot.relativize(path).toString()))
          .toList();
    }

    PuppeteerRenderer puppeteerRenderer = FxToolkit.runFx(() -> {
      Canvas canvas = new Canvas(WIDTH, HEIGHT);
      FxBlitter2D blitter = new FxBlitter2D(canvas.getGraphicsContext2D());
      blitter.setProjectRoot(projectRoot.toFile());
      blitter.setViewport(WIDTH, HEIGHT);
      return new PuppeteerRenderer(canvas, blitter, new WritableImage(WIDTH, HEIGHT));
    });
    List<VisualMetric> metrics = new ArrayList<>();
    for (Path script : scripts) {
      String source = Files.readString(script, StandardCharsets.UTF_8);
      String relativeScript = relative(projectRoot, script);
      if (!scriptFilter.isBlank() && !relativeScript.contains(scriptFilter)) continue;
      List<Checkpoint> checkpoints = checkpoints(projectRoot, script, source, checkpointMode);
      if (checkpoints.isEmpty()) continue;

      VnScenario completeScenario = new VnScenarioLoader().load(relativeScript);

      CanonicalRenderer renderer = FxToolkit.runFx(() -> {
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        VnRenderer result = new VnRenderer(canvas.getGraphicsContext2D());
        result.setProjectRoot(projectRoot.toFile());
        return new CanonicalRenderer(canvas, result, new WritableImage(WIDTH, HEIGHT));
      });

      for (Checkpoint checkpoint : checkpoints) {
        // VnScene deliberately chains instantaneous nodes until the next interactive node. A
        // normal full-script runtime therefore overshoots cursors that sit between a timeline and
        // the following show commands. Bound the canonical scenario at the exact source line so
        // this side of the oracle represents what the VNS has executed at that cursor, not what it
        // will execute at the next dialogue.
        VnScenario scenario = scenarioThroughLine(completeScenario, checkpoint.oneBasedLine());
        VnCharacterSceneAccessor timelineAccessor = new VnCharacterSceneAccessor();
        DefaultVnInterop interop = new DefaultVnInterop();
        interop.setSceneAccessor(timelineAccessor);
        VnScene runtime = new VnScene(scenario);
        runtime.setInterop(interop);
        runtime.onEnter();
        runtime.getState().setUiHidden(true);
        advanceToScenarioEnd(runtime);
        runtime.getState().updateCharacterAnimations(10_000);
        FxToolkit.runFx(() -> {
          renderer.renderer().setTimelineAccessor(timelineAccessor);
          return null;
        });
        primeCanonical(renderer, runtime, scenario);
        // Background decoding is asynchronous. Cache clearing keeps the audit bounded, so each
        // checkpoint gets a short decode pass before the captured pass.
        Thread.sleep(250);
        BufferedImage canonical = renderCanonical(renderer, runtime, scenario);
        clearCanonicalCache(renderer);

        PuppeteerLauncherPanel.SceneSnapshot snapshot = resolve(
            projectRoot, script, source, checkpoint.zeroBasedCursor());
        JesScene2D puppeteer = (JesScene2D) buildScene.invoke(app, snapshot);
        @SuppressWarnings("unchecked")
        Map<String, Map<PropertyType, Double>> baselines =
            (Map<String, Map<PropertyType, Double>>) captureBaselines.invoke(app, puppeteer);
        applyTimelineEndState.invoke(app, puppeteer, snapshot, null, baselines);
        BufferedImage reconstructed = renderPuppeteer(puppeteerRenderer, puppeteer);

        VisualMetric metric = compareImages(
            relativeScript, checkpoint.oneBasedLine(), checkpoint.targets(), canonical, reconstructed);
        metrics.add(metric);
        Path sheet = outputRoot.resolve(sheetName(relativeScript, checkpoint.oneBasedLine()));
        writeContactSheet(sheet, metric, canonical, reconstructed);
        canonical.flush();
        reconstructed.flush();
        // The audit deliberately walks many full-resolution layered scenes. Encourage prompt
        // reclamation of JavaFX decode buffers after each completed sheet instead of depending on
        // the comparatively small unit-test heap's normal collection cadence.
        System.gc();
      }
    }

    Path csv = outputRoot.resolve("metrics.csv");
    writeMetrics(csv, metrics);
    long visibleDiffs = metrics.stream().filter(metric -> metric.changedPixelPercent() >= 0.01).count();
    assertTrue(!metrics.isEmpty(), "No visual checkpoints found for " + projectRoot);
    if (Boolean.parseBoolean(setting("jvnPuppeteerAuditStrict", "JVN_PUPPETEER_AUDIT_STRICT", "false"))) {
      assertTrue(visibleDiffs == 0, visibleDiffs + " checkpoints differ by at least 0.01% of pixels. See " + csv);
    }
    System.out.println("Rendered " + metrics.size() + " canonical/Puppeteer checkpoint pairs; "
        + visibleDiffs + " have >= 0.01% changed pixels (mode=" + checkpointMode.setting + "). Report: " + csv);
  }

  private static EditorApp configuredEditor(Path projectRoot) throws Exception {
    EditorApp app = new EditorApp();
    Field projectRootField = EditorApp.class.getDeclaredField("projectRoot");
    projectRootField.setAccessible(true);
    projectRootField.set(app, projectRoot.toFile());
    return app;
  }

  private static Method privateMethod(String name, Class<?>... parameterTypes) throws Exception {
    Method method = EditorApp.class.getDeclaredMethod(name, parameterTypes);
    method.setAccessible(true);
    return method;
  }

  private static List<Checkpoint> checkpoints(
      Path projectRoot, Path script, String source, CheckpointMode mode) throws Exception {
    return switch (mode) {
      case ALL_LINES -> allLineCheckpoints(source);
      case SCENE_CHANGES -> sceneChangeCheckpoints(projectRoot, script, source);
      case GROUPED_TIMELINES -> groupedTimelineCheckpoints(projectRoot, script, source);
    };
  }

  private static List<Checkpoint> allLineCheckpoints(String source) {
    String[] lines = source.split("\n", -1);
    List<Checkpoint> checkpoints = new ArrayList<>(lines.length);
    for (int cursor = 0; cursor < lines.length; cursor++) {
      checkpoints.add(new Checkpoint(cursor, cursor + 1, List.of("line")));
    }
    return List.copyOf(checkpoints);
  }

  private static List<Checkpoint> sceneChangeCheckpoints(
      Path projectRoot, Path script, String source) throws Exception {
    List<Checkpoint> checkpoints = new ArrayList<>();
    String previousSignature = null;
    String[] lines = source.split("\n", -1);
    for (int cursor = 0; cursor < lines.length; cursor++) {
      PuppeteerLauncherPanel.SceneSnapshot snapshot = resolve(projectRoot, script, source, cursor);
      String signature = sceneSignature(snapshot);
      if (!signature.equals(previousSignature)) {
        checkpoints.add(new Checkpoint(cursor, cursor + 1, List.of("scene-change")));
        previousSignature = signature;
      }
    }
    return List.copyOf(checkpoints);
  }

  private static String sceneSignature(PuppeteerLauncherPanel.SceneSnapshot snapshot) {
    StringBuilder signature = new StringBuilder();
    signature.append(snapshot.previousBackgroundId).append('>')
        .append(snapshot.backgroundId).append('|');
    for (PuppeteerLauncherPanel.CharacterEntry character : snapshot.characters) {
      signature.append(character.characterId).append('@')
          .append(character.displaySlot).append(':')
          .append(character.expression).append(':')
          .append(character.position).append(':')
          .append(character.positionX).append(':')
          .append(character.positionY).append(':')
          .append(character.layerOrder).append(';');
    }
    signature.append("timelines=").append(snapshot.inlineTimelineHistory.size());
    return signature.toString();
  }

  private static List<Checkpoint> groupedTimelineCheckpoints(
      Path projectRoot, Path script, String source) throws Exception {
    List<Checkpoint> checkpoints = new ArrayList<>();
    String[] lines = source.split("\n", -1);
    for (int cursor = 0; cursor < lines.length; cursor++) {
      PuppeteerLauncherPanel.SceneSnapshot snapshot = resolve(projectRoot, script, source, cursor);
      if (!snapshot.hasInlineTimelineHistory()) continue;
      PuppeteerLauncherPanel.InlineTimelineContext context = snapshot.inlineTimelineHistory.get(
          snapshot.inlineTimelineHistory.size() - 1);
      if (context == null || context.endLine() != cursor) continue;

      Set<String> groupNames = new LinkedHashSet<>();
      for (PuppeteerLauncherPanel.CharacterEntry character : snapshot.characters) {
        for (PuppeteerLauncherPanel.CharacterLayerGroupEntry group :
            snapshot.resolveCharacterLayerGroups(character.characterId, character.expression)) {
          groupNames.addAll(PuppeteerLauncherPanel.equivalentSnapshotLayerGroupEntityNames(
              snapshot, character, group.groupId));
        }
      }
      AnimationProject timeline = CodeImporter.importCode(
          "visual_inline_" + (context.startLine() + 1),
          "timeline {\n" + context.body().strip() + "\n}\n");
      Set<String> targets = new LinkedHashSet<>();
      for (EntityTrack track : timeline.getTracks()) {
        if (groupNames.contains(track.getEntityName())
            && hasNonIdentitySpatialEnd(track, timeline.getTotalDurationMs())) {
          targets.add(track.getEntityName());
        }
      }
      if (!targets.isEmpty()) {
        checkpoints.add(new Checkpoint(cursor, cursor + 1, List.copyOf(targets)));
      }
    }
    return List.copyOf(checkpoints);
  }

  private static VnScenario scenarioThroughLine(VnScenario source, int oneBasedLine) {
    VnScenario.Builder bounded = VnScenario.builder(source.getId() + "_through_" + oneBasedLine);
    source.getCharacters().values().forEach(bounded::addCharacter);
    source.getBackgrounds().values().forEach(bounded::addBackground);
    source.getStagePresets().values().forEach(bounded::addStagePreset);
    source.getGroups().values().forEach(bounded::addGroup);
    for (VnNode node : source.getNodes()) {
      if (node.getType() == VnNodeType.END || node.getSourceLine() > oneBasedLine) continue;
      bounded.addNode(node);
    }
    bounded.addNode(VnNode.builder(VnNodeType.END).sourceLine(oneBasedLine + 1).build());
    return bounded.build();
  }

  private static void advanceToScenarioEnd(VnScene scene) {
    for (int attempts = 0; attempts < 100_000; attempts++) {
      VnNode node = scene.getState().getCurrentNode();
      if (node == null) return;
      if (node.getType() == VnNodeType.END) return;
      if (node.getType() == VnNodeType.CHOICE) {
        throw new AssertionError("Cannot linearly render through choice at source line "
            + node.getSourceLine());
      }
      if (node.getType() == VnNodeType.DIALOGUE) scene.advance();
      else scene.update(10_000);
    }
    throw new AssertionError("Could not advance bounded canonical scenario to its end");
  }

  private static BufferedImage renderCanonical(
      CanonicalRenderer renderer, VnScene scene, VnScenario scenario) throws Exception {
    return FxToolkit.runFx(() -> {
      int nodeIndex = scene.getState().getCurrentNodeIndex();
      boolean uiHidden = scene.getState().isUiHidden();
      try {
        scene.getState().setCurrentNodeIndex(scenario.getNodes().size());
        scene.getState().setUiHidden(false);
        renderer.renderer().render(scene.getState(), scenario, WIDTH, HEIGHT);
        WritableImage image = renderer.canvas().snapshot(null, renderer.snapshot());
        return SwingFXUtils.fromFXImage(image, null);
      } finally {
        scene.getState().setCurrentNodeIndex(nodeIndex);
        scene.getState().setUiHidden(uiHidden);
      }
    });
  }

  private static void primeCanonical(
      CanonicalRenderer renderer, VnScene scene, VnScenario scenario) throws Exception {
    FxToolkit.runFx(() -> {
      int nodeIndex = scene.getState().getCurrentNodeIndex();
      boolean uiHidden = scene.getState().isUiHidden();
      try {
        scene.getState().setCurrentNodeIndex(scenario.getNodes().size());
        scene.getState().setUiHidden(false);
        renderer.renderer().render(scene.getState(), scenario, WIDTH, HEIGHT);
      } finally {
        scene.getState().setCurrentNodeIndex(nodeIndex);
        scene.getState().setUiHidden(uiHidden);
      }
      return null;
    });
  }

  private static void clearCanonicalCache(CanonicalRenderer renderer) throws Exception {
    FxToolkit.runFx(() -> {
      renderer.renderer().clearCache();
      return null;
    });
  }

  private static BufferedImage renderPuppeteer(PuppeteerRenderer renderer, JesScene2D scene)
      throws Exception {
    return FxToolkit.runFx(() -> {
      renderer.canvas().getGraphicsContext2D().clearRect(0, 0, WIDTH, HEIGHT);
      scene.render(renderer.blitter(), WIDTH, HEIGHT);
      WritableImage image = renderer.canvas().snapshot(null, renderer.snapshot());
      BufferedImage result = SwingFXUtils.fromFXImage(image, null);
      renderer.blitter().clearCache();
      return result;
    });
  }

  private static VisualMetric compareImages(
      String script,
      int line,
      List<String> targets,
      BufferedImage canonical,
      BufferedImage reconstructed) {
    long changed = 0;
    long totalDelta = 0;
    int minX = WIDTH;
    int minY = HEIGHT;
    int maxX = -1;
    int maxY = -1;
    for (int y = 0; y < HEIGHT; y++) {
      for (int x = 0; x < WIDTH; x++) {
        int expected = canonical.getRGB(x, y);
        int actual = reconstructed.getRGB(x, y);
        int dr = Math.abs(((expected >>> 16) & 0xff) - ((actual >>> 16) & 0xff));
        int dg = Math.abs(((expected >>> 8) & 0xff) - ((actual >>> 8) & 0xff));
        int db = Math.abs((expected & 0xff) - (actual & 0xff));
        totalDelta += dr + dg + db;
        if (Math.max(dr, Math.max(dg, db)) > 12) {
          changed++;
          minX = Math.min(minX, x);
          minY = Math.min(minY, y);
          maxX = Math.max(maxX, x);
          maxY = Math.max(maxY, y);
        }
      }
    }
    double pixelPercent = changed * 100.0 / (WIDTH * (double) HEIGHT);
    double meanRgbDelta = totalDelta / (WIDTH * (double) HEIGHT * 3.0);
    String bounds = changed == 0 ? "none" : minX + ":" + minY + ":" + maxX + ":" + maxY;
    return new VisualMetric(script, line, targets, pixelPercent, meanRgbDelta, bounds);
  }

  private static void writeContactSheet(
      Path output,
      VisualMetric metric,
      BufferedImage canonical,
      BufferedImage reconstructed) throws IOException {
    int headerHeight = 70;
    BufferedImage sheet = new BufferedImage(
        SHEET_PANEL_WIDTH * 3, SHEET_PANEL_HEIGHT + headerHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = sheet.createGraphics();
    try {
      graphics.setColor(new Color(24, 27, 33));
      graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.drawImage(canonical, 0, headerHeight, SHEET_PANEL_WIDTH, SHEET_PANEL_HEIGHT, null);
      graphics.drawImage(reconstructed, SHEET_PANEL_WIDTH, headerHeight,
          SHEET_PANEL_WIDTH, SHEET_PANEL_HEIGHT, null);
      BufferedImage diff = diffImage(canonical, reconstructed);
      graphics.drawImage(diff, SHEET_PANEL_WIDTH * 2, headerHeight, null);
      graphics.setColor(Color.WHITE);
      graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
      graphics.drawString(metric.script() + ":" + metric.line() + "  " + metric.targets(), 16, 24);
      graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
      graphics.drawString(String.format(Locale.ROOT,
          "changed %.3f%% | mean RGB delta %.3f | bounds %s",
          metric.changedPixelPercent(), metric.meanRgbDelta(), metric.diffBounds()), 16, 49);
      graphics.drawString("canonical VNS", 16, 66);
      graphics.drawString("Puppeteer launch", SHEET_PANEL_WIDTH + 16, 66);
      graphics.drawString("amplified pixel diff", SHEET_PANEL_WIDTH * 2 + 16, 66);
      diff.flush();
    } finally {
      graphics.dispose();
    }
    Files.createDirectories(output.getParent());
    assertTrue(ImageIO.write(sheet, "png", output.toFile()), "Could not write " + output);
  }

  private static BufferedImage diffImage(BufferedImage expected, BufferedImage actual) {
    BufferedImage diff = new BufferedImage(
        SHEET_PANEL_WIDTH, SHEET_PANEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < SHEET_PANEL_HEIGHT; y++) {
      int sourceY = Math.min(HEIGHT - 1, y * HEIGHT / SHEET_PANEL_HEIGHT);
      for (int x = 0; x < SHEET_PANEL_WIDTH; x++) {
        int sourceX = Math.min(WIDTH - 1, x * WIDTH / SHEET_PANEL_WIDTH);
        int a = expected.getRGB(sourceX, sourceY);
        int b = actual.getRGB(sourceX, sourceY);
        int delta = Math.max(
            Math.abs(((a >>> 16) & 0xff) - ((b >>> 16) & 0xff)),
            Math.max(
                Math.abs(((a >>> 8) & 0xff) - ((b >>> 8) & 0xff)),
                Math.abs((a & 0xff) - (b & 0xff))));
        int level = Math.min(255, delta * 4);
        int base = ((((a >>> 16) & 0xff) + ((a >>> 8) & 0xff) + (a & 0xff)) / 3) / 5;
        diff.setRGB(x, y, (Math.max(base, level) << 16) | (base << 8) | base);
      }
    }
    return diff;
  }

  private static void writeMetrics(Path output, List<VisualMetric> metrics) throws IOException {
    List<String> rows = new ArrayList<>();
    rows.add("script,line,targets,changed_pixel_percent,mean_rgb_delta,diff_bounds");
    for (VisualMetric metric : metrics) {
      rows.add(csv(metric.script()) + "," + metric.line() + "," + csv(String.join("|", metric.targets()))
          + "," + String.format(Locale.ROOT, "%.6f", metric.changedPixelPercent())
          + "," + String.format(Locale.ROOT, "%.6f", metric.meanRgbDelta())
          + "," + csv(metric.diffBounds()));
    }
    Files.write(output, rows, StandardCharsets.UTF_8);
  }

  private static String csv(String value) {
    return '"' + (value == null ? "" : value.replace("\"", "\"\"")) + '"';
  }

  private static String sheetName(String script, int line) {
    return script.replace('/', '_').replace('\\', '_').replace('.', '_') + "_line_" + line + ".png";
  }

  private static boolean hasNonIdentitySpatialEnd(EntityTrack track, double timeMs) {
    if (track == null) return false;
    if (track.hasKeyframes(PropertyType.X) && Math.abs(track.getValueAt(PropertyType.X, timeMs)) > 1e-9) return true;
    if (track.hasKeyframes(PropertyType.Y) && Math.abs(track.getValueAt(PropertyType.Y, timeMs)) > 1e-9) return true;
    if (track.hasKeyframes(PropertyType.ROTATION)
        && Math.abs(track.getValueAt(PropertyType.ROTATION, timeMs)) > 1e-9) return true;
    if (track.hasKeyframes(PropertyType.MIRROR_X)
        && Math.abs(track.getValueAt(PropertyType.MIRROR_X, timeMs)) > 1e-9) return true;
    if (track.hasKeyframes(PropertyType.SCALE_X)
        && Math.abs(track.getValueAt(PropertyType.SCALE_X, timeMs) - 1.0) > 1e-9) return true;
    return track.hasKeyframes(PropertyType.SCALE_Y)
        && Math.abs(track.getValueAt(PropertyType.SCALE_Y, timeMs) - 1.0) > 1e-9;
  }

  private static PuppeteerLauncherPanel.SceneSnapshot resolve(
      Path projectRoot, Path script, String source, int cursor) {
    return PuppeteerLauncherPanel.resolveSnapshot(
        source,
        cursor,
        script.toString(),
        (sourceName, includePath) -> {
          Path sourcePath = sourceName == null || sourceName.isBlank()
              ? script
              : Path.of(sourceName).toAbsolutePath().normalize();
          Path included = resolveInclude(projectRoot, sourcePath, includePath);
          return new PuppeteerLauncherPanel.ResolvedInclude(
              included.toString(), Files.readString(included, StandardCharsets.UTF_8));
        });
  }

  private static Path resolveInclude(Path projectRoot, Path sourceFile, String includePath)
      throws IOException {
    String normalized = includePath == null ? "" : includePath.trim().replace('\\', '/');
    List<Path> candidates = new ArrayList<>();
    if (normalized.startsWith("/")) {
      candidates.add(projectRoot.resolve("scripts").resolve(normalized.substring(1)));
    } else {
      Path parent = sourceFile == null ? null : sourceFile.toAbsolutePath().normalize().getParent();
      if (parent != null) candidates.add(parent.resolve(normalized));
      candidates.add(projectRoot.resolve("scripts").resolve(normalized));
      candidates.add(projectRoot.resolve(normalized));
    }
    for (Path candidate : candidates) {
      Path resolved = candidate.toAbsolutePath().normalize();
      if (resolved.startsWith(projectRoot) && Files.isRegularFile(resolved)) return resolved;
    }
    throw new IOException("Included script not found: " + includePath + " from " + sourceFile);
  }

  private static String relative(Path projectRoot, Path script) {
    return projectRoot.relativize(script.toAbsolutePath().normalize()).toString().replace('\\', '/');
  }

  private static String setting(String property, String environment, String fallback) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) value = System.getenv(environment);
    return value == null || value.isBlank() ? fallback : value;
  }

  private record Checkpoint(int zeroBasedCursor, int oneBasedLine, List<String> targets) {
  }

  private record CanonicalRenderer(Canvas canvas, VnRenderer renderer, WritableImage snapshot) {
  }

  private record PuppeteerRenderer(Canvas canvas, FxBlitter2D blitter, WritableImage snapshot) {
  }

  private record VisualMetric(
      String script,
      int line,
      List<String> targets,
      double changedPixelPercent,
      double meanRgbDelta,
      String diffBounds) {
  }

  private enum CheckpointMode {
    SCENE_CHANGES("scene-changes"),
    GROUPED_TIMELINES("grouped-timelines"),
    ALL_LINES("all-lines");

    private final String setting;

    CheckpointMode(String setting) {
      this.setting = setting;
    }

    private static CheckpointMode parse(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      for (CheckpointMode mode : values()) {
        if (mode.setting.equals(normalized)) return mode;
      }
      throw new IllegalArgumentException(
          "Unknown checkpoint mode '" + value + "' (use scene-changes, grouped-timelines, or all-lines)");
    }
  }
}
