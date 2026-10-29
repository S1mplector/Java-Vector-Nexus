package com.jvn.runtime;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.assets.AssetManager;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.assets.OverlayAssetManager;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnSettingsStore;
import com.jvn.core.vn.VnEntryScriptResolver;
import com.jvn.core.vn.VnStoragePaths;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.project.ProjectHealthChecker;
import com.jvn.fx.FxLauncher;
import com.jvn.fx.audio.FxAudioService;
import com.jvn.runtime.hotreload.HotReloadServer;
import com.jvn.core.audio.AudioFacade;
import com.jvn.scripting.jes.JesLoader;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.jvn.plugin.api.PluginEnvironment;
import com.jvn.plugin.runtime.PluginHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

public class JvnApp {
  private static final Logger log = LoggerFactory.getLogger(JvnApp.class);
  private static final String DEFAULT_ENTRY_SCRIPT = "story/prologue.vns";

  public static void main(String[] args) {
    com.jvn.core.diagnostics.GraphicsPipeline.configure();
    RuntimeCrashSupport.install();
    ApplicationConfig.Builder builder = ApplicationConfig.builder().title("JVN Runtime").width(960).height(540);
    String scriptName = null;
    String locale = "en";
    String ui = "fx"; // fx | swing
    String audioBackend = "auto"; // fx | simp3 | auto
    String jesScript = null;
    String assetRoot = null;
    boolean showPerfHud = false;
    boolean widthSpecified = false;
    boolean heightSpecified = false;
    boolean titleSpecified = false;
    boolean uiSpecified = false;
    boolean audioSpecified = false;
    boolean localeSpecified = false;

    for (int i = 0; i < args.length; i++) {
      String a = args[i];

      String inlineTitle = inlineOptionValue(a, "--title");
      if (inlineTitle != null) {
        builder.title(cleanCliValue(inlineTitle));
        titleSpecified = true;
        continue;
      }
      String inlineWidth = inlineOptionValue(a, "--width");
      if (inlineWidth != null) {
        builder.width(Integer.parseInt(cleanCliValue(inlineWidth)));
        widthSpecified = true;
        continue;
      }
      String inlineHeight = inlineOptionValue(a, "--height");
      if (inlineHeight != null) {
        builder.height(Integer.parseInt(cleanCliValue(inlineHeight)));
        heightSpecified = true;
        continue;
      }
      String inlineScript = inlineOptionValue(a, "--script");
      if (inlineScript != null) {
        scriptName = cleanCliValue(inlineScript);
        continue;
      }
      String inlineLocale = inlineOptionValue(a, "--locale");
      if (inlineLocale != null) {
        locale = cleanCliValue(inlineLocale);
        localeSpecified = true;
        continue;
      }
      String inlineUi = inlineOptionValue(a, "--ui");
      if (inlineUi != null) {
        ui = cleanCliValue(inlineUi);
        uiSpecified = true;
        continue;
      }
      String inlineJes = inlineOptionValue(a, "--jes");
      if (inlineJes != null) {
        jesScript = cleanCliValue(inlineJes);
        continue;
      }
      String inlineAudio = inlineOptionValue(a, "--audio");
      if (inlineAudio != null) {
        audioBackend = cleanCliValue(inlineAudio);
        audioSpecified = true;
        continue;
      }
      String inlineAssets = inlineOptionValue(a, "--assets");
      if (inlineAssets != null) {
        assetRoot = cleanCliValue(inlineAssets);
        continue;
      }
      String inlinePerfHud = inlineOptionValue(a, "--perf-hud");
      if (inlinePerfHud != null) {
        showPerfHud = parseBooleanFlag(cleanCliValue(inlinePerfHud));
        continue;
      }

      switch (a) {
        case "--title":
          if (i + 1 < args.length) {
            builder.title(cleanCliValue(args[++i]));
            titleSpecified = true;
          }
          break;
        case "--width":
          if (i + 1 < args.length) {
            builder.width(Integer.parseInt(cleanCliValue(args[++i])));
            widthSpecified = true;
          }
          break;
        case "--height":
          if (i + 1 < args.length) {
            builder.height(Integer.parseInt(cleanCliValue(args[++i])));
            heightSpecified = true;
          }
          break;
        case "--script":
          if (i + 1 < args.length) scriptName = cleanCliValue(args[++i]);
          break;
        case "--locale":
          if (i + 1 < args.length) {
            locale = cleanCliValue(args[++i]);
            localeSpecified = true;
          }
          break;
        case "--ui":
          if (i + 1 < args.length) {
            ui = cleanCliValue(args[++i]);
            uiSpecified = true;
          }
          break;
        case "--jes":
          if (i + 1 < args.length) jesScript = cleanCliValue(args[++i]);
          break;
        case "--audio":
          if (i + 1 < args.length) {
            audioBackend = cleanCliValue(args[++i]);
            audioSpecified = true;
          }
          break;
        case "--assets":
          if (i + 1 < args.length) assetRoot = cleanCliValue(args[++i]);
          break;
        case "--perf-hud":
          if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
            showPerfHud = parseBooleanFlag(cleanCliValue(args[++i]));
          } else {
            showPerfHud = true;
          }
          break;
        default:
          log.warn("Unknown argument: {}", a);
      }
    }

    Properties manifest = loadProjectManifest(assetRoot);
    Path projectRoot = assetRoot == null || assetRoot.isBlank() ? null : Paths.get(assetRoot);
    String gameId = manifest == null && projectRoot == null
        ? ""
        : VnStoragePaths.resolveGameId(manifest, projectRoot);
    VnStoragePaths.configureGame(gameId);
    log.info("Game storage -> id={}, root={}", gameId.isBlank() ? "(legacy)" : gameId, VnStoragePaths.root());
    if (manifest != null) {
      if (!titleSpecified) {
        String manifestTitle = manifest.getProperty("name", "").trim();
        if (!manifestTitle.isBlank()) builder.title(manifestTitle);
      }
      if (!widthSpecified) {
        Integer manifestWidth = parsePositiveInt(manifest.getProperty("width"));
        if (manifestWidth != null) builder.width(manifestWidth);
      }
      if (!heightSpecified) {
        Integer manifestHeight = parsePositiveInt(manifest.getProperty("height"));
        if (manifestHeight != null) builder.height(manifestHeight);
      }
      if (!uiSpecified) {
        String manifestUi = manifest.getProperty("runtime.ui", "").trim();
        if (!manifestUi.isBlank()) ui = manifestUi;
      }
      if (!audioSpecified) {
        String manifestAudio = manifest.getProperty("runtime.audio", "").trim();
        if (!manifestAudio.isBlank()) audioBackend = manifestAudio;
      }
      if (!localeSpecified) {
        String manifestLocale = manifest.getProperty("runtime.locale", "").trim();
        if (!manifestLocale.isBlank()) locale = manifestLocale;
      }
      Integer renderWidth = firstPositiveInt(
          manifest.getProperty("renderWidth"),
          manifest.getProperty("display.renderWidth"),
          manifest.getProperty("logicalWidth"),
          manifest.getProperty("display.logicalWidth")
      );
      Integer renderHeight = firstPositiveInt(
          manifest.getProperty("renderHeight"),
          manifest.getProperty("display.renderHeight"),
          manifest.getProperty("logicalHeight"),
          manifest.getProperty("display.logicalHeight")
      );
      if (renderWidth != null && renderHeight != null) {
        System.setProperty("jvn.render.width", Integer.toString(renderWidth));
        System.setProperty("jvn.render.height", Integer.toString(renderHeight));
      }
    }

    ApplicationConfig cfg = builder.build();
    
    File assetRootDir = (assetRoot == null || assetRoot.isBlank())
        ? null
        : new File(assetRoot);
    AssetManager manager = (assetRootDir == null)
        ? new ClasspathAssetManager()
        : new OverlayAssetManager(new FilesystemAssetManager(Paths.get(assetRootDir.getPath())), new ClasspathAssetManager());
    if (assetRootDir != null) {
      System.setProperty("jvn.assets.root", assetRootDir.getAbsolutePath());
    } else {
      System.clearProperty("jvn.assets.root");
    }
    AssetCatalog.setDefaultManager(manager);

    PluginHost pluginHost = PluginHost.builder(PluginEnvironment.RUNTIME)
        .jvnVersion(System.getProperty("jvn.version", "dev"))
        .projectDirectory(assetRootDir == null ? null : assetRootDir.toPath())
        .build();
    pluginHost.discoverAndStart();
    com.jvn.core.animation.EasingExtensions.install(pluginHost.registries().animationEasings());
    com.jvn.core.assets.ImageEffects.install(pluginHost.registries().imageEffects());
    Runtime.getRuntime().addShutdownHook(new Thread(pluginHost::close, "jvn-plugin-shutdown"));
    if (!pluginHost.plugins().isEmpty()) {
      log.info("Plugins -> discovered={}, diagnostics={}", pluginHost.plugins().size(), pluginHost.diagnostics().size());
    }

    // Init localization after the asset manager is configured so project string
    // tables resolve through the same overlay as other runtime assets.
    Localization.init(locale, Thread.currentThread().getContextClassLoader());

    String resolvedEntryScript = VnEntryScriptResolver.resolveEntryScript(scriptName, assetRootDir);
    if (resolvedEntryScript == null) {
      resolvedEntryScript = DEFAULT_ENTRY_SCRIPT;
      log.warn(
          "Could not resolve startup script from --script, jvn.project, system property, or script discovery; falling back to {}",
          DEFAULT_ENTRY_SCRIPT);
    }
    VnEntryScriptResolver.publishToSystemProperty(resolvedEntryScript);

    // Log asset availability on startup
    AssetCatalog assets = new AssetCatalog(manager);
    try {
      int img = assets.listImages().size();
      int aud = assets.listAudio().size();
      int scr = assets.listScripts().size();
      int fnt = assets.listFonts().size();
      log.info("Assets -> images={}, audio={}, scripts={}, fonts={}", img, aud, scr, fnt);
    } catch (Exception e) {
      log.warn("Unable to list assets: {}", e.toString());
    }
    if (assetRootDir != null) {
      ProjectHealthChecker.Report report = ProjectHealthChecker.inspect(assetRootDir);
      if (report.hasIssues()) {
        log.warn("Project health -> errors={}, warnings={}", report.errorCount(), report.warningCount());
        for (ProjectHealthChecker.Diagnostic diagnostic : report.diagnostics()) {
          String location = diagnostic.location();
          if (location == null || location.isBlank()) {
            log.warn("Project health [{}]: {}", diagnostic.category(), diagnostic.message());
          } else {
            log.warn("Project health [{}] {}: {}", diagnostic.category(), location, diagnostic.message());
          }
        }
      }
    }
    
    // Create engine and show scene
    Engine engine = new Engine(cfg);
    RuntimeVnInterop[] interopRef = new RuntimeVnInterop[1];
    engine.setVnInteropFactory(e -> {
      interopRef[0] = new RuntimeVnInterop(e, pluginHost.registries());
      return interopRef[0];
    });
    HotReloadServer.startIfEnabled(path -> {
      RuntimeVnInterop interop = interopRef[0];
      if (interop != null) interop.reloadScenario(path);
    });
    engine.start();

    VnSettings settingsModel = new VnSettingsStore().load();
    if (jesScript != null) {
      loadJes(engine, jesScript);
    } else {
      VnSaveManager saveManager = new VnSaveManager();
      AudioFacade audio = null;
      if ("simp3".equalsIgnoreCase(audioBackend) || "auto".equalsIgnoreCase(audioBackend)) {
        try {
          Class<?> cls = Class.forName("com.jvn.audio.simp3.Simp3AudioService");
          Object inst = cls.getDeclaredConstructor().newInstance();
          audio = new com.jvn.audio.simp3.AsyncAudioService((AudioFacade) inst);
        } catch (Throwable t) {
          // Fallback to FX if adapter not on classpath
          audio = new FxAudioService();
        }
      } else {
        audio = new FxAudioService();
      }
      engine.own(audio);
      configureAudioProjectRoot(audio, assetRoot);
      // Apply user settings to audio backend immediately
      try {
        if (audio != null && settingsModel != null) {
          audio.setBgmVolume(settingsModel.getBgmVolume());
          audio.setSfxVolume(settingsModel.getSfxVolume());
          audio.setVoiceVolume(settingsModel.getVoiceVolume());
        }
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
      MainMenuScene menu = new MainMenuScene(engine, settingsModel, saveManager, resolvedEntryScript, audio);
      if (settingsModel != null) {
        engine.setFixedUpdateStepMs(settingsModel.getPhysicsFixedStepMs(), settingsModel.getPhysicsMaxSubSteps());
      }
      engine.scenes().push(menu);
    }

    if ("swing".equalsIgnoreCase(ui)) {
      com.jvn.swing.SwingLauncher.launch(engine);
    } else {
      FxLauncher.launch(
          engine,
          showPerfHud,
          settingsModel.getAccessibilityTheme(),
          settingsModel.getUiFontScale()
      );
    }
  }

  private static void configureAudioProjectRoot(AudioFacade audio, String assetRoot) {
    if (audio == null || assetRoot == null || assetRoot.isBlank()) return;
    try {
      File root = new File(assetRoot).getCanonicalFile();
      audio.setProjectRoot(root);
    } catch (Exception ex) {
      log.debug("Could not set audio project root to {}", assetRoot, ex);
    }
  }

  private static String inlineOptionValue(String arg, String option) {
    if (arg == null || option == null || option.isEmpty()) return null;
    String prefix = option + "=";
    if (!arg.startsWith(prefix)) return null;
    return arg.substring(prefix.length());
  }

  static String cleanCliValue(String raw) {
    if (raw == null) return "";
    String value = raw;
    while (value.length() >= 2) {
      int start = firstNonWhitespace(value);
      int end = lastNonWhitespace(value);
      if (start < 0 || end < start) return "";
      char first = value.charAt(start);
      char last = value.charAt(end);
      boolean doubleQuoted = first == '"' && last == '"';
      boolean singleQuoted = first == '\'' && last == '\'';
      if (!doubleQuoted && !singleQuoted) break;
      value = value.substring(start + 1, end);
    }
    return value;
  }

  private static int firstNonWhitespace(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (!Character.isWhitespace(value.charAt(i))) return i;
    }
    return -1;
  }

  private static int lastNonWhitespace(String value) {
    for (int i = value.length() - 1; i >= 0; i--) {
      if (!Character.isWhitespace(value.charAt(i))) return i;
    }
    return -1;
  }

  private static boolean parseBooleanFlag(String raw) {
    if (raw == null || raw.isBlank()) return true;
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return !("0".equals(normalized)
        || "false".equals(normalized)
        || "no".equals(normalized)
        || "off".equals(normalized));
  }

  private static Properties loadProjectManifest(String assetRoot) {
    if (assetRoot == null || assetRoot.isBlank()) return null;
    File root = new File(assetRoot);
    File manifest = new File(root, "jvn.project");
    if (!manifest.exists() || !manifest.isFile()) return null;
    try (FileInputStream fis = new FileInputStream(manifest)) {
      Properties p = new Properties();
      p.load(fis);
      return p;
    } catch (Exception ex) {
      log.warn("Failed to read {}: {}", manifest.getAbsolutePath(), ex.toString());
      return null;
    }
  }

  private static Integer parsePositiveInt(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      int parsed = Integer.parseInt(raw.trim());
      return parsed > 0 ? parsed : null;
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return null;
    }
  }

  private static Integer firstPositiveInt(String... values) {
    if (values == null) return null;
    for (String value : values) {
      Integer parsed = parsePositiveInt(value);
      if (parsed != null) return parsed;
    }
    return null;
  }

  private static void loadJes(Engine engine, String jesScript) {
    try {
      AssetCatalog cat = new AssetCatalog();
      String[] parts = jesScript.split("[,;]");
      if (parts.length == 1) {
        try (InputStream in = cat.open(AssetType.SCRIPT, jesScript)) {
          JesScene2D scene = JesLoader.load(in);
          new JesVnBridge(engine).attach(scene);
          engine.scenes().push(scene);
        }
      } else {
        java.util.List<InputStream> ins = new java.util.ArrayList<>();
        for (String p : parts) {
          String path = p.trim();
          if (path.isEmpty()) continue;
          ins.add(cat.open(AssetType.SCRIPT, path));
        }
        JesScene2D scene = JesLoader.loadMerged(ins);
        new JesVnBridge(engine).attach(scene);
        engine.scenes().push(scene);
      }
    } catch (Exception e) {
      log.warn("Failed to load JES script '{}': {}. Loading inline sample.", jesScript, e.toString());
      try {
        String sample = "scene \"Sample\" {\n" +
            "  entity \"panel\" {\n" +
            "    component Panel2D { x: 0.2 y: 0.2 w: 1.0 h: 0.6 fill: rgb(0.1,0.6,0.2,0.8) }\n" +
            "  }\n" +
            "}\n";
        var in2 = new ByteArrayInputStream(sample.getBytes());
        var scene = JesLoader.load(in2);
        new JesVnBridge(engine).attach(scene);
        engine.scenes().push(scene);
      } catch (Exception ex) {
        log.warn("Inline JES sample failed: {}", ex.toString());
      }
    }
  }
}
