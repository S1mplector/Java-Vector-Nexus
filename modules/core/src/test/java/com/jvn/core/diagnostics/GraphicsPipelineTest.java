package com.jvn.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphicsPipelineTest {
  @TempDir Path temporaryDirectory;
  private final String originalMode = System.getProperty(GraphicsPipeline.MODE_PROPERTY);
  private final String originalOrder = System.getProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY);
  private final String originalForceGpu =
      System.getProperty(GraphicsPipeline.PRISM_FORCE_GPU_PROPERTY);
  private final String originalSettingsFile =
      System.getProperty(GraphicsPipeline.SETTINGS_FILE_PROPERTY);
  private final String originalVsync = System.getProperty(GraphicsPipeline.PRISM_VSYNC_PROPERTY);
  private final String originalDirtyRegions =
      System.getProperty(GraphicsPipeline.PRISM_DIRTY_REGIONS_PROPERTY);
  private final String originalOcclusionCulling =
      System.getProperty(GraphicsPipeline.PRISM_OCCLUSION_CULLING_PROPERTY);
  private final String originalShapeCache =
      System.getProperty(GraphicsPipeline.PRISM_SHAPE_CACHE_PROPERTY);
  private final String originalVerbose = System.getProperty(GraphicsPipeline.PRISM_VERBOSE_PROPERTY);
  private final String originalShowDirty =
      System.getProperty(GraphicsPipeline.PRISM_SHOW_DIRTY_PROPERTY);
  private final String originalShowOverdraw =
      System.getProperty(GraphicsPipeline.PRISM_SHOW_OVERDRAW_PROPERTY);
  private final String originalPrintRenderGraph =
      System.getProperty(GraphicsPipeline.PRISM_PRINT_RENDER_GRAPH_PROPERTY);
  private final String originalPulseLogger =
      System.getProperty(GraphicsPipeline.JAVAFX_PULSE_LOGGER_PROPERTY);
  private final String originalPreviewFps = System.getProperty("jvn.editor.previewMaxFps");
  private final String originalOs = System.getProperty("os.name");

  @BeforeEach
  void isolateRenderSettings() {
    System.setProperty(
        GraphicsPipeline.SETTINGS_FILE_PROPERTY,
        temporaryDirectory.resolve("missing-render-pipeline.properties").toString());
  }

  @AfterEach
  void restoreProperties() {
    restore(GraphicsPipeline.MODE_PROPERTY, originalMode);
    restore(GraphicsPipeline.PRISM_ORDER_PROPERTY, originalOrder);
    restore(GraphicsPipeline.PRISM_FORCE_GPU_PROPERTY, originalForceGpu);
    restore(GraphicsPipeline.SETTINGS_FILE_PROPERTY, originalSettingsFile);
    restore(GraphicsPipeline.PRISM_VSYNC_PROPERTY, originalVsync);
    restore(GraphicsPipeline.PRISM_DIRTY_REGIONS_PROPERTY, originalDirtyRegions);
    restore(GraphicsPipeline.PRISM_OCCLUSION_CULLING_PROPERTY, originalOcclusionCulling);
    restore(GraphicsPipeline.PRISM_SHAPE_CACHE_PROPERTY, originalShapeCache);
    restore(GraphicsPipeline.PRISM_VERBOSE_PROPERTY, originalVerbose);
    restore(GraphicsPipeline.PRISM_SHOW_DIRTY_PROPERTY, originalShowDirty);
    restore(GraphicsPipeline.PRISM_SHOW_OVERDRAW_PROPERTY, originalShowOverdraw);
    restore(GraphicsPipeline.PRISM_PRINT_RENDER_GRAPH_PROPERTY, originalPrintRenderGraph);
    restore(GraphicsPipeline.JAVAFX_PULSE_LOGGER_PROPERTY, originalPulseLogger);
    restore("jvn.editor.previewMaxFps", originalPreviewFps);
    restore("os.name", originalOs);
  }

  @Test
  void hardwareModePrefersNativePipelinesAndKeepsSoftwareFallback_Windows() {
    System.setProperty(GraphicsPipeline.MODE_PROPERTY, "hardware");
    System.setProperty("os.name", "Windows 11");
    System.clearProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY);

    assertEquals(GraphicsPipeline.Mode.HARDWARE, GraphicsPipeline.configure());
    assertEquals("d3d,sw", System.getProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY));
    assertNull(System.getProperty(GraphicsPipeline.PRISM_FORCE_GPU_PROPERTY));
    assertTrue(GraphicsPipeline.statusText().startsWith("GPU preferred"));
  }

  @Test
  void hardwareModePrefersNativePipelinesAndKeepsSoftwareFallback_Mac() {
    System.setProperty(GraphicsPipeline.MODE_PROPERTY, "hardware");
    System.setProperty("os.name", "Mac OS X");
    System.clearProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY);

    assertEquals(GraphicsPipeline.Mode.HARDWARE, GraphicsPipeline.configure());
    assertEquals("es2,sw", System.getProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY));
    assertNull(System.getProperty(GraphicsPipeline.PRISM_FORCE_GPU_PROPERTY));
    assertTrue(GraphicsPipeline.statusText().startsWith("GPU preferred"));
  }

  @Test
  void hardwareModePrefersNativePipelinesAndKeepsSoftwareFallback_Linux() {
    System.setProperty(GraphicsPipeline.MODE_PROPERTY, "hardware");
    System.setProperty("os.name", "Linux");
    System.clearProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY);

    assertEquals(GraphicsPipeline.Mode.HARDWARE, GraphicsPipeline.configure());
    assertEquals("es2,sw", System.getProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY));
    assertNull(System.getProperty(GraphicsPipeline.PRISM_FORCE_GPU_PROPERTY));
    assertTrue(GraphicsPipeline.statusText().startsWith("GPU preferred"));
  }

  @Test
  void softwareModeSelectsCompatibilityPipeline() {
    System.setProperty(GraphicsPipeline.MODE_PROPERTY, "compatibility");
    System.clearProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY);

    assertEquals(GraphicsPipeline.Mode.SOFTWARE, GraphicsPipeline.configure());
    assertEquals("sw", System.getProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY));
    assertNull(System.getProperty(GraphicsPipeline.PRISM_FORCE_GPU_PROPERTY));
  }

  @Test
  void explicitPrismOrderIsNeverOverwritten() {
    System.setProperty(GraphicsPipeline.MODE_PROPERTY, "gpu");
    System.setProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY, "custom");

    GraphicsPipeline.configure();

    assertEquals("custom", System.getProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY));
    assertNull(System.getProperty(GraphicsPipeline.PRISM_FORCE_GPU_PROPERTY));
  }

  @Test
  void appliesPersistedRenderTuningBeforeJavaFxStarts() throws Exception {
    Path settings = temporaryDirectory.resolve("render-pipeline.properties");
    Files.writeString(settings, String.join("\n",
        "render.vsync=false",
        "render.dirtyRegions=true",
        "render.occlusionCulling=false",
        "render.shapeCache=all",
        "diagnostics.verbose=true",
        "diagnostics.showDirtyRegions=true",
        "diagnostics.showOverdraw=false",
        "diagnostics.printRenderGraph=true"));
    System.setProperty(GraphicsPipeline.SETTINGS_FILE_PROPERTY, settings.toString());
    System.setProperty(GraphicsPipeline.MODE_PROPERTY, "auto");
    System.clearProperty(GraphicsPipeline.PRISM_VSYNC_PROPERTY);
    System.clearProperty(GraphicsPipeline.PRISM_DIRTY_REGIONS_PROPERTY);
    System.clearProperty(GraphicsPipeline.PRISM_OCCLUSION_CULLING_PROPERTY);
    System.clearProperty(GraphicsPipeline.PRISM_SHAPE_CACHE_PROPERTY);
    System.clearProperty(GraphicsPipeline.PRISM_VERBOSE_PROPERTY);
    System.clearProperty(GraphicsPipeline.PRISM_SHOW_DIRTY_PROPERTY);
    System.clearProperty(GraphicsPipeline.PRISM_SHOW_OVERDRAW_PROPERTY);
    System.clearProperty(GraphicsPipeline.PRISM_PRINT_RENDER_GRAPH_PROPERTY);
    System.clearProperty(GraphicsPipeline.JAVAFX_PULSE_LOGGER_PROPERTY);

    GraphicsPipeline.configure();

    assertEquals("false", System.getProperty(GraphicsPipeline.PRISM_VSYNC_PROPERTY));
    assertEquals("true", System.getProperty(GraphicsPipeline.PRISM_DIRTY_REGIONS_PROPERTY));
    assertEquals("false", System.getProperty(GraphicsPipeline.PRISM_OCCLUSION_CULLING_PROPERTY));
    assertEquals("all", System.getProperty(GraphicsPipeline.PRISM_SHAPE_CACHE_PROPERTY));
    assertEquals("true", System.getProperty(GraphicsPipeline.PRISM_VERBOSE_PROPERTY));
    assertEquals("true", System.getProperty(GraphicsPipeline.PRISM_SHOW_DIRTY_PROPERTY));
    assertEquals("false", System.getProperty(GraphicsPipeline.PRISM_SHOW_OVERDRAW_PROPERTY));
    assertEquals("true", System.getProperty(GraphicsPipeline.PRISM_PRINT_RENDER_GRAPH_PROPERTY));
    assertNull(System.getProperty(GraphicsPipeline.JAVAFX_PULSE_LOGGER_PROPERTY));
  }

  @Test
  void explicitRenderGraphCaptureEnablesRenderRootsWithoutUnsafePulseLogger() {
    System.setProperty(GraphicsPipeline.PRISM_PRINT_RENDER_GRAPH_PROPERTY, "true");
    System.setProperty(GraphicsPipeline.PRISM_DIRTY_REGIONS_PROPERTY, "false");
    System.clearProperty(GraphicsPipeline.JAVAFX_PULSE_LOGGER_PROPERTY);

    GraphicsPipeline.configure();

    assertNull(System.getProperty(GraphicsPipeline.JAVAFX_PULSE_LOGGER_PROPERTY));
    assertEquals("true", System.getProperty(GraphicsPipeline.PRISM_DIRTY_REGIONS_PROPERTY));
  }

  @Test
  void previewBudgetIsLoadedBeforeToolkitAndExplicitOverrideWins() throws Exception {
    Path settings = temporaryDirectory.resolve("render.properties");
    Files.writeString(settings, "render.previewMaxFps=30\n");
    System.setProperty(GraphicsPipeline.SETTINGS_FILE_PROPERTY, settings.toString());
    System.clearProperty("jvn.editor.previewMaxFps");
    GraphicsPipeline.configure();
    assertEquals("30", System.getProperty("jvn.editor.previewMaxFps"));
    System.setProperty("jvn.editor.previewMaxFps", "120");
    GraphicsPipeline.configure();
    assertEquals("120", System.getProperty("jvn.editor.previewMaxFps"));
    System.clearProperty("jvn.editor.previewMaxFps");
    Files.writeString(settings, "render.previewMaxFps=-100\n");
    GraphicsPipeline.configure();
    assertNull(System.getProperty("jvn.editor.previewMaxFps"));
  }

  @Test
  void explicitDriverOverrideIsPreservedButNeverEnabledByPreference() {
    System.setProperty(GraphicsPipeline.MODE_PROPERTY, "hardware");
    System.setProperty(GraphicsPipeline.PRISM_FORCE_GPU_PROPERTY, "false");
    GraphicsPipeline.configure();
    assertEquals("false", System.getProperty(GraphicsPipeline.PRISM_FORCE_GPU_PROPERTY));
  }

  private static void restore(String key, String value) {
    if (value == null) System.clearProperty(key);
    else System.setProperty(key, value);
  }
}
