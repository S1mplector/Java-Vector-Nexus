package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderPipelineSettingsTest {
  @TempDir Path temporaryDirectory;

  @Test
  void missingPreferenceDefaultsToAdaptiveSelection() {
    assertEquals(
        RenderPipelineSettings.Mode.AUTO,
        RenderPipelineSettings.load(temporaryDirectory.resolve("missing.properties")));
    assertEquals(
        RenderPipelineSettings.Options.defaults(),
        RenderPipelineSettings.loadOptions(temporaryDirectory.resolve("missing-tuning.properties")));
  }

  @Test
  void recognizesExistingGraphicsModeAliases() throws Exception {
    Path preferences = temporaryDirectory.resolve("editor-preferences.properties");
    Files.writeString(preferences, "graphics.mode=prefer-gpu\n");

    assertEquals(
        RenderPipelineSettings.Mode.HARDWARE,
        RenderPipelineSettings.load(preferences));
  }

  @Test
  void savingModePreservesUnrelatedEditorPreferences() throws Exception {
    Path preferences = temporaryDirectory.resolve("nested/editor-preferences.properties");
    Files.createDirectories(preferences.getParent());
    Files.writeString(preferences, "editor.theme=dark\neditorMaxFps=120\ngraphics.mode=auto\n");

    RenderPipelineSettings.save(preferences, RenderPipelineSettings.Mode.SOFTWARE);

    Properties stored = new Properties();
    try (var input = Files.newInputStream(preferences)) {
      stored.load(input);
    }
    assertEquals("dark", stored.getProperty("editor.theme"));
    assertEquals("120", stored.getProperty("editorMaxFps"));
    assertEquals("software", stored.getProperty("graphics.mode"));
  }

  @Test
  void managedLaunchesReceiveTheSelectedMode() {
    Map<String, String> environment = new HashMap<>();
    environment.put(RenderPipelineSettings.GRAPHICS_MODE_ENVIRONMENT, "stale-value");

    boolean applied = RenderPipelineSettings.applyLaunchEnvironment(
        environment,
        List.of("/engine/scripts/launch-app.sh", "editor"),
        RenderPipelineSettings.Mode.HARDWARE,
        RenderPipelineSettings.Options.defaults().withLinuxGlxRecovery(false));

    assertTrue(applied);
    assertEquals(
        "hardware",
        environment.get(RenderPipelineSettings.GRAPHICS_MODE_ENVIRONMENT));
    assertEquals(
        "1",
        environment.get(RenderPipelineSettings.DISABLE_GLX_RECOVERY_ENVIRONMENT));
    assertTrue(RenderPipelineSettings.isManagedLaunchCommand(
        List.of("./gradlew", ":editor:run")));
    assertTrue(RenderPipelineSettings.isManagedLaunchCommand(
        List.of("gradlew.bat", ":editor:runLauncher")));
    assertTrue(RenderPipelineSettings.isManagedLaunchCommand(
        List.of("./gradlew", ":runtime:run")));
  }

  @Test
  void unrelatedHubTasksDoNotReceiveGraphicsOverrides() {
    Map<String, String> environment = new HashMap<>();

    boolean applied = RenderPipelineSettings.applyLaunchEnvironment(
        environment,
        List.of("./gradlew", "build"),
        RenderPipelineSettings.Mode.SOFTWARE,
        RenderPipelineSettings.Options.defaults());

    assertFalse(applied);
    assertFalse(environment.containsKey(RenderPipelineSettings.GRAPHICS_MODE_ENVIRONMENT));
  }

  @Test
  void reportsBackendOrderForEachPlatformProfile() {
    assertEquals(
        "Direct3D → software",
        RenderPipelineSettings.Mode.HARDWARE.backendOrder("Windows 11"));
    assertEquals(
        "OpenGL ES2 → software",
        RenderPipelineSettings.Mode.HARDWARE.backendOrder("Linux"));
    assertEquals(
        "OpenGL ES2 → software",
        RenderPipelineSettings.Mode.HARDWARE.backendOrder("macOS"));
    assertEquals(
        "Software renderer only",
        RenderPipelineSettings.Mode.SOFTWARE.backendOrder("macOS"));
  }

  @Test
  void persistsCompleteRenderTuningConfiguration() throws Exception {
    Path tuning = temporaryDirectory.resolve("settings/render-pipeline.properties");
    RenderPipelineSettings.Options requested = new RenderPipelineSettings.Options(
        false,
        true,
        false,
        RenderPipelineSettings.ShapeCache.ALL,
        true,
        true,
        true,
        true,
        false,
        30);

    RenderPipelineSettings.saveOptions(tuning, requested);

    assertEquals(requested, RenderPipelineSettings.loadOptions(tuning));
    assertTrue(requested.diagnosticsEnabled());
  }

  @Test
  void renderGraphCaptureKeepsDirtyRegionRootsAvailable() {
    RenderPipelineSettings.Options options = RenderPipelineSettings.Options.defaults()
        .withDirtyRegions(false)
        .withPrintRenderGraph(true);

    assertTrue(options.printRenderGraph());
    assertTrue(options.dirtyRegions());
  }

  @Test
  void previewBudgetsPersistAndOldFilesKeepAutomaticPacing() throws Exception {
    Path tuning = temporaryDirectory.resolve("tuning.properties");
    var options = RenderPipelineSettings.Options.defaults().withPreviewMaxFps(30).withVsync(false);
    RenderPipelineSettings.saveOptions(tuning, options);
    assertEquals(30, RenderPipelineSettings.loadOptions(tuning).previewMaxFps());
    Files.writeString(tuning, "render.vsync=true\nrender.previewMaxFps=invalid\n");
    assertEquals(0, RenderPipelineSettings.loadOptions(tuning).previewMaxFps());
    assertEquals(240, options.withPreviewMaxFps(999).previewMaxFps());
  }

  @Test
  void recordingsFolderMatchesEachDesktopPlatformAndCustomOverrides() {
    Path home = temporaryDirectory.resolve("home");
    assertEquals(home.resolve("Library/Application Support/JVN Engine Hub/profiles"),
        RenderPipelineSettings.profileDirectory("Mac OS X", home, Map.of()));
    assertEquals(home.resolve("JVN Engine Hub/profiles"),
        RenderPipelineSettings.profileDirectory("Windows 11", home, Map.of("LOCALAPPDATA", home.toString())));
    assertEquals(home.resolve("jvn-engine-hub/profiles"),
        RenderPipelineSettings.profileDirectory("Linux", home, Map.of("XDG_STATE_HOME", home.toString())));
    assertEquals(home.toAbsolutePath(),
        RenderPipelineSettings.profileDirectory("Linux", home, Map.of("JVN_PROFILE_DIR", home.toString())));
  }

  @Test
  void enabledGlxRecoveryClearsAnInheritedDisableOverride() {
    Map<String, String> environment = new HashMap<>();
    environment.put(RenderPipelineSettings.DISABLE_GLX_RECOVERY_ENVIRONMENT, "1");

    RenderPipelineSettings.applyLaunchEnvironment(
        environment,
        List.of("/engine/scripts/launch-app.sh", "runtime"),
        RenderPipelineSettings.Mode.AUTO,
        RenderPipelineSettings.Options.defaults());

    assertFalse(environment.containsKey(RenderPipelineSettings.DISABLE_GLX_RECOVERY_ENVIRONMENT));
  }
}
