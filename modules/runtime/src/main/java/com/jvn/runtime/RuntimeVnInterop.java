package com.jvn.runtime;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.animation.SceneAccessor;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.audio.AudioFacade;
import com.jvn.core.engine.Engine;
import com.jvn.core.graphics.Camera2D;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.SaveMenuScene;
import com.jvn.core.menu.SettingsScene;
import com.jvn.core.phone.PhoneScene;
import com.jvn.core.phone.VnPhoneCommands;
import com.jvn.core.phone.VnPhoneData;
import com.jvn.core.phone.VnPhonePropertiesCodec;
import com.jvn.core.phone.VnPhoneStateStore;
import com.jvn.core.project.StoryMapPaths;
import com.jvn.core.scene.Scene;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.Sprite2D;
import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.DefaultVnInterop;
import com.jvn.core.vn.VnArgTokenizer;
import com.jvn.core.vn.VnCharacterSceneAccessor;
import com.jvn.core.vn.VnErrorOverlay;
import com.jvn.core.vn.VnExternalCommand;
import com.jvn.core.vn.VnInterop;
import com.jvn.core.vn.VnInteropResult;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioLoader;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnEntryScriptResolver;
import com.jvn.core.vn.VnTimelineAccessorProvider;
import com.jvn.scripting.jes.JesLoader;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.jvn.plugin.api.PluginRegistries;
import com.jvn.plugin.api.script.ScriptCommandInvocation;

public class RuntimeVnInterop implements VnInterop, VnTimelineAccessorProvider {
  private static final Logger log = LoggerFactory.getLogger(RuntimeVnInterop.class);
  private static final String DEFAULT_ENTRY_SCRIPT = "story/prologue.vns";
  private final Engine engine;
  private final PluginRegistries pluginRegistries;
  private final DefaultVnInterop base = new DefaultVnInterop();
  private final VnScenarioLoader scenarioLoader = new VnScenarioLoader();
  private final VnCharacterSceneAccessor timelineAccessor = new VnCharacterSceneAccessor();
  private final java.util.Map<String, VnCharacterProxyEntity> vnCharacterProxies = new java.util.HashMap<>();

  public RuntimeVnInterop(Engine engine) {
    this(engine, null);
  }

  public RuntimeVnInterop(Engine engine, PluginRegistries pluginRegistries) {
    this.engine = engine;
    this.pluginRegistries = pluginRegistries;
    configureDefaultSceneAccessor();
  }

  private void configureDefaultSceneAccessor() {
    base.setSceneAccessor(new SceneAccessor() {
      @Override
      public com.jvn.core.scene2d.Entity2D findEntity(String name) {
        if (name == null || name.isBlank()) return null;
        // Try JES scene first (Puppeteer / standalone JES)
        JesScene2D jes = topJesScene();
        if (jes != null) return jes.find(name);
        // Fallback: bridge VN characters as Entity2D proxies
        VnScene vn = topVnScene();
        if (vn != null) {
          Entity2D characterProxy = getOrCreateVnCharacterProxy(vn, name);
          return characterProxy != null ? characterProxy : timelineAccessor.findEntity(name);
        }
        return null;
      }

      @Override
      public void setCameraX(double x) {
        Camera2D cam = activeCamera();
        if (cam == null) return;
        cam.setPosition(x, cam.getY());
      }

      @Override
      public void setCameraY(double y) {
        Camera2D cam = activeCamera();
        if (cam == null) return;
        cam.setPosition(cam.getX(), y);
      }

      @Override
      public void setCameraZoom(double zoom) {
        Camera2D cam = activeCamera();
        if (cam == null) return;
        cam.setZoom(zoom);
      }

      @Override
      public void applyCustomProperty(String target, String propertyKey, double value) {
        if (propertyKey == null || propertyKey.isBlank()) return;
        if ("__camera__".equals(target)) {
          Camera2D cam = activeCamera();
          if (cam != null) cam.applyCustomProperty(propertyKey, value);
          return;
        }
        Entity2D entity = findEntity(target);
        if (entity != null) entity.applyCustomProperty(propertyKey, value);
      }

      @Override
      public void playAudioCue(String trackPath, String channel, double volume, boolean loop, double fadeInMs) {
        if (trackPath == null || trackPath.isBlank()) return;
        VnScene vn = topVnScene();
        if (vn == null) return;
        AudioFacade audio = vn.getAudioFacade();
        if (audio == null) return;

        float vol = (float) clamp01(volume);
        String normalized = channel == null ? "sound" : channel.trim().toLowerCase();
        switch (normalized) {
          case "music":
          case "bgm":
            audio.setBgmVolume(vol);
            if (fadeInMs > 0.0) {
              audio.crossfadeBgm(trackPath, Math.max(0L, Math.round(fadeInMs)), loop);
            } else {
              audio.playBgm(trackPath, loop);
            }
            break;
          case "voice":
            audio.setVoiceVolume(vol);
            audio.playVoice(trackPath);
            break;
          default:
            audio.setSfxVolume(vol);
            audio.playSfx(trackPath);
            break;
        }
      }

      @Override
      public void stopAudio(String channel) {
        VnScene vn = topVnScene();
        if (vn == null) return;
        AudioFacade audio = vn.getAudioFacade();
        if (audio == null) return;

        String normalized = channel == null ? "all" : channel.trim().toLowerCase();
        switch (normalized) {
          case "music":
          case "bgm":
            audio.stopBgm();
            break;
          case "voice":
            audio.stopVoice();
            break;
          case "sfx":
          case "sound":
            audio.stopSfx();
            break;
          default:
            audio.stopAllAudio();
            break;
        }
      }

      @Override
      public void onEventCue(String type, Map<String, String> payload) {
        JesScene2D jes = topJesScene();
        if (jes != null) {
          applyJesSceneEventCue(jes, type, payload);
          return;
        }
        VnScene vn = topVnScene();
        if (vn != null) {
          applyVnSceneEventCue(vn, type, payload);
        }
      }
    });
  }

  /**
   * Wire a SceneAccessor for timeline execution support.
   * This must be called by the runtime to enable jes_timeline commands.
   */
  public void setSceneAccessor(SceneAccessor accessor) {
    if (accessor == null) {
      configureDefaultSceneAccessor();
    } else {
      base.setSceneAccessor(accessor);
    }
  }

  @Override
  public VnCharacterSceneAccessor getTimelineAccessor() {
    return timelineAccessor;
  }

  /**
   * Reload the VN script at {@code path} in-place, seeking to the node whose
   * source line is closest to the current position.  Safe to call from any thread.
   */
  public void reloadScenario(String path) {
    if (path == null || path.isBlank()) return;
    try {
      timelineAccessor.clear();
      vnCharacterProxies.clear();
      VnScenario newScenario = scenarioLoader.load(path.trim());
      VnScene vn = topVnScene();
      if (vn == null) {
        log.warn("HotReload: no active VnScene — reload of '{}' ignored", path);
        return;
      }
      int currentLine = -1;
      VnNode currentNode = vn.getState().getCurrentNode();
      if (currentNode != null) currentLine = currentNode.getSourceLine();

      vn.getState().setScenario(newScenario);

      // seek to the node whose source line is closest to the pre-reload position
      if (currentLine >= 0) {
        List<VnNode> nodes = newScenario.getNodes();
        int bestIndex = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
          int dist = Math.abs(nodes.get(i).getSourceLine() - currentLine);
          if (dist < bestDist) {
            bestDist = dist;
            bestIndex = i;
          }
        }
        vn.getState().setCurrentNodeIndex(bestIndex);
      }
      RuntimeCrashSupport.updateVnStateSnapshot("hot-reloaded:" + path);
      log.info("HotReload: scenario '{}' reloaded successfully", path);
    } catch (IOException e) {
      log.warn("HotReload: failed to reload '{}': {}", path, e.getMessage());
    }
  }

  private Scene topScene() {
    if (engine == null || engine.scenes() == null) return null;
    return engine.scenes().peek();
  }

  private JesScene2D topJesScene() {
    Scene top = topScene();
    return top instanceof JesScene2D jes ? jes : null;
  }

  private VnScene topVnScene() {
    Scene top = topScene();
    return top instanceof VnScene vn ? vn : null;
  }

  private Camera2D activeCamera() {
    JesScene2D jes = topJesScene();
    return jes == null ? null : jes.getCamera();
  }

  private static double clamp01(double value) {
    if (value < 0.0) return 0.0;
    if (value > 1.0) return 1.0;
    return value;
  }

  private void applyJesSceneEventCue(JesScene2D scene, String type, Map<String, String> payload) {
    if (scene == null || type == null || type.isBlank()) return;
    Map<String, String> safePayload = payload == null ? Map.of() : payload;
    String normalized = type.trim().toLowerCase(Locale.ROOT);
    String target = safePayload.getOrDefault("target", "");
    Entity2D entity = target.isBlank() ? null : scene.find(target);

    switch (normalized) {
      case "script_call":
      case "scriptcall":
        invokeJesScriptCallEvent(scene, safePayload);
        break;
      case "show":
        if (entity != null) entity.setVisible(true);
        applySceneSpritePath(entity, safePayload.get("path"));
        break;
      case "hide":
        if (entity != null) entity.setVisible(false);
        break;
      case "replace":
      case "expression":
        applySceneSpritePath(entity, safePayload.get("path"));
        if (entity != null && safePayload.get("path") != null && !safePayload.get("path").isBlank()) {
          entity.setVisible(true);
        }
        break;
      case "scene":
        Entity2D background = entity != null ? entity : findBackgroundEntity(scene);
        applySceneSpritePath(background, safePayload.get("path"));
        if (background != null && safePayload.get("path") != null && !safePayload.get("path").isBlank()) {
          background.setVisible(true);
        }
        break;
      default:
        break;
    }
  }

  private void applyVnSceneEventCue(VnScene scene, String type, Map<String, String> payload) {
    if (scene == null || type == null || type.isBlank()) return;
    Map<String, String> safePayload = payload == null ? Map.of() : payload;
    String normalized = type.trim().toLowerCase(Locale.ROOT);
    String target = safePayload.getOrDefault("target", "").trim();
    var state = scene.getState();

    switch (normalized) {
      case "script_call":
      case "scriptcall":
        invokeVnScriptCallEvent(scene, safePayload);
        break;
      case "expression":
      case "replace": {
        if (target.isBlank()) return;
        String expression = firstNonBlank(safePayload.get("expression"), safePayload.get("value"));
        if (expression == null || expression.isBlank()) return;
        if (!state.setCharacterExpression(target, expression)) {
          CharacterPosition position = state.getCharacterPosition(target);
          state.showCharacterAnimated(position == null ? CharacterPosition.CENTER : position, target, expression);
        }
        break;
      }
      case "show": {
        if (target.isBlank()) return;
        CharacterPosition position = parseCharacterPosition(safePayload.get("position"));
        if (position == null) {
          position = state.getCharacterPosition(target);
        }
        if (position == null) {
          position = CharacterPosition.CENTER;
        }
        String expression = firstNonBlank(safePayload.get("expression"), safePayload.get("value"));
        if (expression == null || expression.isBlank()) {
          expression = state.getCharacterExpression(target);
        }
        state.showCharacterAnimated(position, target, expression == null || expression.isBlank() ? "neutral" : expression);
        break;
      }
      case "hide": {
        if (target.isBlank()) return;
        state.hideCharacterAnimated(target);
        break;
      }
      case "scene": {
        String backgroundId = firstNonBlank(safePayload.get("id"), safePayload.get("value"));
        if (backgroundId != null && !backgroundId.isBlank()) {
          state.setCurrentBackgroundId(backgroundId);
        }
        break;
      }
      default:
        break;
    }
  }

  private void invokeJesScriptCallEvent(JesScene2D scene, Map<String, String> payload) {
    if (scene == null || payload == null) return;
    String handler = firstNonBlank(
        payload.get("handler"),
        firstNonBlank(payload.get("call"), firstNonBlank(payload.get("name"), payload.get("target")))
    );
    if (handler == null || handler.isBlank()) return;
    scene.invokeCall(handler, eventPayloadToProps(payload));
  }

  private void invokeVnScriptCallEvent(VnScene scene, Map<String, String> payload) {
    if (scene == null || payload == null) return;
    String provider = firstNonBlank(payload.get("provider"), payload.get("target"));
    String command = firstNonBlank(
        payload.get("command"),
        firstNonBlank(payload.get("payload"), firstNonBlank(payload.get("value"), payload.get("arg")))
    );
    if (command == null || command.isBlank()) return;

    String resolvedProvider = provider == null ? "" : provider.trim();
    String resolvedPayload = command.trim();
    if (resolvedProvider.isBlank()) {
      String[] tokens = split(resolvedPayload);
      if (tokens.length == 0) return;
      resolvedProvider = tokens[0];
      resolvedPayload = joinTokens(tokens, 1);
    }
    if (resolvedProvider.isBlank()) return;

    try {
      if ("vn".equalsIgnoreCase(resolvedProvider)) {
        handleVnTimelineCommand(scene, resolvedPayload);
        return;
      }
      RuntimeVnInterop.this.handle(new VnExternalCommand(resolvedProvider, resolvedPayload), scene);
    } catch (Exception ex) {
      reportInteropFailure(scene, "timeline", resolvedProvider + " " + resolvedPayload, ex);
    }
  }

  private void handleVnTimelineCommand(VnScene scene, String command) {
    if (scene == null || scene.getState() == null || command == null || command.isBlank()) return;
    String[] toks = split(command);
    if (toks.length == 0) return;
    String op = toks[0].trim().toLowerCase(Locale.ROOT);
    var state = scene.getState();
    switch (op) {
      case "show" -> {
        if (toks.length < 2) return;
        String target = toks[1];
        CharacterPosition position = toks.length >= 3 ? parseCharacterPosition(toks[2]) : state.getCharacterPosition(target);
        if (position == null) position = CharacterPosition.CENTER;
        String expression = toks.length >= 4 ? toks[3] : state.getCharacterExpression(target);
        state.showCharacterAnimated(position, target, expression == null || expression.isBlank() ? "neutral" : expression);
      }
      case "hide" -> {
        if (toks.length < 2) return;
        state.hideCharacterAnimated(toks[1]);
      }
      case "expression", "expr", "replace" -> {
        if (toks.length < 3) return;
        String target = toks[1];
        if (!state.setCharacterExpression(target, toks[2])) {
          CharacterPosition position = state.getCharacterPosition(target);
          if (position == null) position = CharacterPosition.CENTER;
          state.showCharacterAnimated(position, target, toks[2]);
        }
      }
      case "scene", "background", "bg" -> {
        if (toks.length >= 2) state.setCurrentBackgroundId(toks[1]);
      }
      default -> {
      }
    }
  }

  private static Map<String, Object> eventPayloadToProps(Map<String, String> payload) {
    Map<String, Object> props = new java.util.LinkedHashMap<>();
    if (payload == null) return props;
    for (Map.Entry<String, String> entry : payload.entrySet()) {
      if (entry == null || entry.getKey() == null) continue;
      String key = entry.getKey().trim();
      if (key.isEmpty() || isScriptCallMetaKey(key)) continue;
      props.put(key, parseScalar(entry.getValue()));
    }
    return props;
  }

  private static boolean isScriptCallMetaKey(String key) {
    String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    return "handler".equals(normalized)
        || "call".equals(normalized)
        || "name".equals(normalized)
        || "provider".equals(normalized)
        || "command".equals(normalized)
        || "payload".equals(normalized)
        || "arg".equals(normalized);
  }

  private static void applySceneSpritePath(Entity2D entity, String rawPath) {
    if (!(entity instanceof Sprite2D sprite)) return;
    if (rawPath == null || rawPath.isBlank()) return;
    sprite.setImagePath(resolveScenePathSpec(rawPath));
  }

  private static Entity2D findBackgroundEntity(JesScene2D scene) {
    if (scene == null) return null;
    for (String name : scene.names()) {
      if (name == null || name.isBlank() || !name.startsWith("bg_")) continue;
      return scene.find(name);
    }
    return null;
  }

  private static CharacterPosition parseCharacterPosition(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return CharacterPosition.predefined(raw.trim());
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) return first;
    return second;
  }

  private static String resolveScenePathSpec(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) return rawPath;
    if (rawPath.indexOf('|') < 0) {
      return resolveScenePath(rawPath.trim());
    }
    StringBuilder out = new StringBuilder();
    for (String token : rawPath.split("\\|")) {
      String part = token == null ? "" : token.trim();
      if (part.isEmpty()) continue;
      if (!out.isEmpty()) out.append(" | ");
      out.append(resolveScenePath(part));
    }
    return out.toString();
  }

  private static String resolveScenePath(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) return rawPath;
    File file = new File(rawPath);
    return file.exists() ? file.getAbsolutePath() : rawPath;
  }

  /**
   * Get the underlying DefaultVnInterop for direct access.
   */
  public DefaultVnInterop getBase() {
    return base;
  }

  @Override
  public VnInteropResult handle(VnExternalCommand command, VnScene scene) {
    String provider = safe(command.getProvider());
    String payload = safe(command.getPayload());
    switch (provider.toLowerCase()) {
      case "jes":
        return handleJes(payload, scene);
      case "menu":
        return handleMenu(payload, scene);
      case "phone":
        return handlePhone(payload, scene);
      case "vns":
        return handleVns(payload, scene);
      case "plugin":
        return handlePlugin(payload, scene);
      default:
        return base.handle(command, scene);
    }
  }

  private VnInteropResult handlePlugin(String payload, VnScene scene) {
    if (pluginRegistries == null) return VnInteropResult.advance();
    String[] tokens = split(payload);
    if (tokens.length == 0) return VnInteropResult.advance();
    var command = pluginRegistries.scriptCommands().find(tokens[0]);
    if (command.isEmpty()) {
      log.warn("Unknown plugin script command: {}", tokens[0]);
      return VnInteropResult.advance();
    }
    try {
      List<String> arguments = tokens.length <= 1 ? List.of() : List.of(tokens).subList(1, tokens.length);
      Map<String, Object> variables = scene == null || scene.getState() == null
          ? Map.of() : scene.getState().getVariables();
      var result = command.get().execute(new ScriptCommandInvocation(
          "vns", tokens[0], arguments, variables,
          System.getProperty("jvn.assets.root") == null ? null : java.nio.file.Path.of(System.getProperty("jvn.assets.root"))));
      if (scene != null && scene.getState() != null) {
        result.variables().forEach((key, value) -> scene.getState().setVariable(key, value));
      }
      return VnInteropResult.advance();
    } catch (Exception error) {
      reportInteropFailure(scene, "plugin", payload, error);
      return VnInteropResult.advance();
    }
  }

  private VnInteropResult handleJes(String payload, VnScene scene) {

    String[] toks = split(payload);
    if (toks.length == 0) return VnInteropResult.advance();
    String cmd = toks[0].toLowerCase();
    try {
      switch (cmd) {
        case "push": {
          String script = toks.length >= 2 ? toks[1] : null;
          String label = null;
          for (int i = 2; i < toks.length - 1; i++) {
            if ("label".equalsIgnoreCase(toks[i])) { label = toks[i+1]; break; }
          }
          java.util.Map<String,Object> initProps = new java.util.HashMap<>();
          int withIdx = -1;
          for (int i = 2; i < toks.length; i++) { if ("with".equalsIgnoreCase(toks[i])) { withIdx = i; break; } }
          if (withIdx >= 0) {
            for (int i = withIdx + 1; i < toks.length; i++) {
              String t = toks[i]; int eq = t.indexOf('='); if (eq > 0) {
                String k = t.substring(0, eq); String v = t.substring(eq+1);
                initProps.put(k, parseScalar(v));
              }
            }
          }
          JesScene2D js = loadJes(script, scene, label, initProps);
          if (js != null) engine.scenes().push(js);
          return VnInteropResult.advance();
        }
        case "replace": {
          String script = toks.length >= 2 ? toks[1] : null;
          String label = null;
          for (int i = 2; i < toks.length - 1; i++) {
            if ("label".equalsIgnoreCase(toks[i])) { label = toks[i+1]; break; }
          }
          java.util.Map<String,Object> initProps = new java.util.HashMap<>();
          int withIdx = -1;
          for (int i = 2; i < toks.length; i++) { if ("with".equalsIgnoreCase(toks[i])) { withIdx = i; break; } }
          if (withIdx >= 0) {
            for (int i = withIdx + 1; i < toks.length; i++) {
              String t = toks[i]; int eq = t.indexOf('='); if (eq > 0) {
                String k = t.substring(0, eq); String v = t.substring(eq+1);
                initProps.put(k, parseScalar(v));
              }
            }
          }
          JesScene2D js = loadJes(script, scene, label, initProps);
          if (js != null) engine.scenes().replace(js);
          return VnInteropResult.advance();
        }
        case "pop": {
          engine.scenes().pop();
          return VnInteropResult.advance();
        }
        case "call": {
          String name = toks.length >= 2 ? toks[1] : null;
          if (name != null) {
            java.util.Map<String,Object> props = new java.util.HashMap<>();
            for (int i = 2; i < toks.length; i++) {
              String t = toks[i];
              int eq = t.indexOf('=');
              if (eq > 0) {
                String k = t.substring(0, eq);
                String v = t.substring(eq + 1);
                props.put(k, parseScalar(v));
              }
            }
            Scene top = engine.scenes().peek();
            if (top instanceof JesScene2D jes) {
              jes.invokeCall(name, props);
            }
          }
          return VnInteropResult.advance();
        }
      }
    } catch (Exception ex) {
      reportInteropFailure(scene, "jes", payload, ex);
    }
    return VnInteropResult.advance();
  }

  private JesScene2D loadJes(String script, VnScene vnScene, String defaultReturnLabel, java.util.Map<String,Object> initProps) throws Exception {
    if (script == null || script.isBlank()) {
      throw new IllegalArgumentException("JES script path is required");
    }
    AssetCatalog cat = new AssetCatalog();
    try (InputStream in = cat.open(AssetType.SCRIPT, script)) {
      JesScene2D js = JesLoader.load(in);
      // Bridge calls from JES back into VN/runtime
      js.registerCall("hud", props -> {
        Object msg = props == null ? null : props.get("msg");
        if (msg != null) vnScene.getState().showHudMessage(String.valueOf(msg), 1500);
      });
      js.registerCall("pop", props -> engine.scenes().pop());
      new JesVnBridge(engine).attach(js);
      java.util.function.Consumer<java.util.Map<String,Object>> doReturn = props -> {
        // Set variables if provided
        if (props != null) {
          for (var e : props.entrySet()) {
            String k = String.valueOf(e.getKey());
            if ("label".equalsIgnoreCase(k) || "goto".equalsIgnoreCase(k)) continue;
            vnScene.getState().setVariable(k, e.getValue());
          }
        }
        // Pop JES and jump to label if specified or default
        String label = null;
        if (props != null) {
          Object l1 = props.get("label");
          Object l2 = props.get("goto");
          if (l1 != null) label = String.valueOf(l1);
          else if (l2 != null) label = String.valueOf(l2);
        }
        if (label == null) label = defaultReturnLabel;
        engine.scenes().pop();
        if (label != null && !label.isBlank()) {
          vnScene.getState().jumpToLabel(label);
        }
      };
      js.registerCall("return", doReturn);
      js.registerCall("vns", doReturn); // alias
      if (initProps != null && !initProps.isEmpty()) {
        try { js.invokeCall("init", initProps); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
      }
      return js;
    }
  }

  private VnInteropResult handleMenu(String payload, VnScene scene) {
    String[] toks = split(payload);
    if (toks.length == 0) return VnInteropResult.advance();
    String kind = toks[0].toLowerCase();
    switch (kind) {
      case "settings": {
        VnSettings s = scene.getState().getSettings();
        String fallbackScript = resolveDefaultScript(scene);
        SettingsScene m = new SettingsScene(
            engine,
            new com.jvn.core.vn.save.VnSaveManager(),
            fallbackScript,
            s,
            scene.getAudioFacade()
        );
        engine.scenes().push(m);
        return VnInteropResult.advance();
      }
      case "save": {
        SaveMenuScene m = new SaveMenuScene(engine, new com.jvn.core.vn.save.VnSaveManager(), scene);
        engine.scenes().push(m);
        return VnInteropResult.advance();
      }
      case "load": {
        String defScript = toks.length >= 2 ? toks[1] : resolveDefaultScript(scene);
        LoadMenuScene m = new LoadMenuScene(engine, new com.jvn.core.vn.save.VnSaveManager(), defScript, scene.getState().getSettings(), scene.getAudioFacade());
        engine.scenes().push(m);
        return VnInteropResult.advance();
      }
      case "main": {
        String script = toks.length >= 2 ? toks[1] : resolveDefaultScript(scene);
        MainMenuScene m = new MainMenuScene(engine, new VnSettings(), new com.jvn.core.vn.save.VnSaveManager(), script, scene.getAudioFacade());
        engine.scenes().push(m);
        return VnInteropResult.advance();
      }
      default:
        // Treat unknown menu kind as a configured custom menu id.
        // Optional second token can override default script for run_script/new_game actions.
        String script = toks.length >= 2 ? toks[1] : resolveDefaultScript(scene);
        MainMenuScene custom = new MainMenuScene(
            engine,
            new VnSettings(),
            new com.jvn.core.vn.save.VnSaveManager(),
            script,
            scene.getAudioFacade(),
            kind
        );
        engine.scenes().push(custom);
        return VnInteropResult.advance();
    }
  }

  private VnInteropResult handlePhone(String payload, VnScene scene) {
    VnPhoneCommands.Result result =
        VnPhoneCommands.handle(payload, scene, VnPhonePropertiesCodec::loadSeedFromAssets);
    switch (result.action()) {
      case OPEN_HOME -> {
        VnPhoneData data = VnPhoneStateStore.load(scene.getState(), VnPhonePropertiesCodec::loadSeedFromAssets);
        engine.scenes().push(new PhoneScene(
            scene,
            data,
            updated -> VnPhoneStateStore.save(scene.getState(), updated)));
      }
      case OPEN_CHAT -> {
        VnPhoneData data = VnPhoneStateStore.load(scene.getState(), VnPhonePropertiesCodec::loadSeedFromAssets);
        engine.scenes().push(new PhoneScene(
            scene,
            data,
            updated -> VnPhoneStateStore.save(scene.getState(), updated),
            result.targetId()));
      }
      case OPEN_CALL -> {
        VnPhoneData data = VnPhoneStateStore.load(scene.getState(), VnPhonePropertiesCodec::loadSeedFromAssets);
        PhoneScene phone = new PhoneScene(
            scene,
            data,
            updated -> VnPhoneStateStore.save(scene.getState(), updated));
        phone.openCall(result.targetId());
        engine.scenes().push(phone);
      }
      case CLOSE -> {
        Scene top = topScene();
        if (top instanceof PhoneScene phone && phone.getVnScene() == scene) {
          engine.scenes().pop();
        }
      }
      case NONE -> {
      }
    }
    return VnInteropResult.advance();
  }

  private VnInteropResult handleVns(String payload, VnScene scene) {
    // payload: push|replace scriptName [label LABEL]
    List<String> toks = new ArrayList<>(java.util.Arrays.asList(split(payload)));
    if (toks.isEmpty()) return VnInteropResult.advance();
    String cmd = toks.remove(0).toLowerCase();
    if ("goto".equals(cmd)) {
      if (toks.isEmpty()) return VnInteropResult.advance();
      String target = toks.remove(0);
      int colon = target.indexOf(':');
      if (colon < 0) {
        scene.getState().jumpToLabel(target);
        return VnInteropResult.stay();
      }
      String arc = target.substring(0, colon);
      String label = target.substring(colon + 1);
      String script = arc.contains(".") ? arc : resolveArcScript(arc, arc + ".vns");
      try {
        VnScene newScene = loadVnScene(script, scene);
        if (newScene != null) {
          if (label != null && !label.isBlank()) newScene.getState().jumpToLabel(label);
          engine.scenes().replace(newScene);
          return VnInteropResult.block();
        }
      } catch (Exception ex) {
        reportInteropFailure(scene, "vns", "goto " + target, ex);
      }
      return VnInteropResult.advance();
    }
    String script = toks.isEmpty() ? null : toks.remove(0);
    String label = null;
    if (!toks.isEmpty() && "label".equalsIgnoreCase(toks.get(0))) {
      toks.remove(0);
      if (!toks.isEmpty()) label = toks.remove(0);
    }
    if (script == null) return VnInteropResult.advance();
    try {
      VnScene newScene = loadVnScene(script, scene);
      if (newScene == null) return VnInteropResult.advance();
      if (label != null) newScene.getState().jumpToLabel(label);
      switch (cmd) {
        case "push":
          engine.scenes().push(newScene);
          break;
        case "replace":
        default:
          engine.scenes().replace(newScene);
          return VnInteropResult.block();
      }
    } catch (Exception ex) {
      reportInteropFailure(scene, "vns", cmd + " " + script, ex);
    }
    return VnInteropResult.advance();
  }

  private void reportInteropFailure(VnScene scene, String provider, String action, Exception ex) {
    if (scene == null || scene.getState() == null) return;
    String detail = ex == null ? "unknown error" : ex.getClass().getSimpleName();
    if (ex != null && ex.getMessage() != null && !ex.getMessage().isBlank()) {
      detail += ": " + ex.getMessage();
    }
    String message = provider + (action == null || action.isBlank() ? "" : " " + action) + " failed: " + detail;
    scene.getState().showHudMessage(message, 2400);
    scene.setActiveError(VnErrorOverlay.interopError(provider, message, ex));
  }

  private VnScene loadVnScene(String script, VnScene current) throws Exception {
    VnScenario sc = scenarioLoader.load(script);
    VnScene vn = new VnScene(sc);
    vn.getState().setSourceScriptName(script);
    if (current.getAudioFacade() != null) vn.setAudioFacade(current.getAudioFacade());
    // carry settings
    copySettings(current.getState().getSettings(), vn.getState().getSettings());
    vn.getState().getVariables().putAll(current.getState().getVariables());
    vn.setInterop(this);
    return vn;
  }

  private void copySettings(VnSettings src, VnSettings dst) {
    if (src == null || dst == null) return;
    dst.copyFrom(src);
  }

  private String resolveArcScript(String arcName, String fallback) {
    if (arcName == null || arcName.isBlank()) return fallback;
    String projectRoot = System.getProperty("jvn.assets.root");
    if (projectRoot == null || projectRoot.isBlank()) return fallback;
    File timelineFile = StoryMapPaths.resolveForProjectRoot(new File(projectRoot));
    if (timelineFile == null || !timelineFile.isFile()) return fallback;
    java.util.regex.Pattern arcPattern = java.util.regex.Pattern.compile(
        "^\\s*arc\\s+(?:\"([^\"]+)\"|(\\S+))\\s+script\\s+(?:\"([^\"]+)\"|(\\S+)).*$");
    try {
      for (String line : java.nio.file.Files.readAllLines(timelineFile.toPath())) {
        if (line == null || line.trim().isEmpty() || line.trim().startsWith("#")) continue;
        java.util.regex.Matcher m = arcPattern.matcher(line.trim());
        if (!m.matches()) continue;
        String arc = m.group(1) != null ? m.group(1) : m.group(2);
        String script = m.group(3) != null ? m.group(3) : m.group(4);
        if (arcName.equalsIgnoreCase(arc != null ? arc.trim() : "")) {
          return script;
        }
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return fallback;
  }

  // --- VN Character → Entity2D proxy bridge ---

  private com.jvn.core.scene2d.Entity2D getOrCreateVnCharacterProxy(VnScene vn, String characterId) {
    if (vn == null || characterId == null) return null;
    com.jvn.core.vn.VnState state = vn.getState();
    CharacterPosition pos = state.getCharacterPosition(characterId);
    com.jvn.core.vn.VnState.CharacterVisual visual = null;
    if (pos != null) {
      visual = state.getOrCreateCharacterVisual(pos);
    } else {
      com.jvn.core.vn.VnState.DetachedCharacterSlot detached = state.getDetachedCharacter(characterId);
      if (detached != null) {
        pos = detached.getBasePosition();
        visual = detached.getVisual();
      }
    }
    if (pos == null || visual == null) return null; // character not currently visible
    VnCharacterProxyEntity proxy = vnCharacterProxies.get(characterId);
    if (proxy == null || proxy.position != pos || proxy.visual != visual) {
      proxy = new VnCharacterProxyEntity(characterId, pos, visual);
      vnCharacterProxies.put(characterId, proxy);
    }
    return proxy;
  }

  /**
   * Lightweight Entity2D proxy that maps timeline property changes
   * (setPosition, setScale, setRotationDeg, alpha) to VN CharacterVisual offsets.
   * <p>
   * Timeline x/y values are interpreted as pixel <em>offsets</em> from the
   * character's natural slot position.
   */
  static final class VnCharacterProxyEntity extends com.jvn.core.scene2d.Entity2D {
    final String characterId;
    final CharacterPosition position;
    private final com.jvn.core.vn.VnState.CharacterVisual visual;

    VnCharacterProxyEntity(String characterId, CharacterPosition position,
                           com.jvn.core.vn.VnState.CharacterVisual visual) {
      this.characterId = characterId;
      this.position = position;
      this.visual = visual;
      // Seed Entity2D fields from current visual state
      this.x = visual.getOffsetX();
      this.y = visual.getOffsetY();
    }

    @Override
    public void setPosition(double x, double y) {
      this.x = x;
      this.y = y;
      visual.setOffsetX(x);
      visual.setOffsetY(y);
    }

    @Override
    public double getX() { return visual.getOffsetX(); }

    @Override
    public double getY() { return visual.getOffsetY(); }
  }

  private static String safe(String s) { return s == null ? "" : s; }
  private static String[] split(String s) { return VnArgTokenizer.tokenizeToArray(s); }
  private static String joinTokens(String[] tokens, int start) {
    if (tokens == null || start >= tokens.length) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = Math.max(0, start); i < tokens.length; i++) {
      if (tokens[i] == null || tokens[i].isBlank()) continue;
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(quoteIfNeeded(tokens[i]));
    }
    return sb.toString();
  }

  private static String quoteIfNeeded(String token) {
    if (token == null) return "\"\"";
    if (!token.isBlank() && token.chars().noneMatch(Character::isWhitespace)
        && token.indexOf('"') < 0 && token.indexOf('\\') < 0) {
      return token;
    }
    return "\"" + token.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private String resolveDefaultScript(VnScene scene) {
    if (scene != null && scene.getState() != null) {
      String source = scene.getState().getSourceScriptName();
      String normalized = VnEntryScriptResolver.normalizeScriptKey(source);
      if (normalized != null) return normalized;
    }
    String resolved = VnEntryScriptResolver.resolveEntryScript(null, null);
    if (resolved != null) {
      return resolved;
    }
    return DEFAULT_ENTRY_SCRIPT;
  }

  private static Object parseScalar(String s) {
    if (s == null) return "";
    String t = s.trim();
    if (t.length() >= 2) {
      if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
        t = t.substring(1, t.length() - 1);
      }
    }
    if (t.equalsIgnoreCase("true")) return Boolean.TRUE;
    if (t.equalsIgnoreCase("false")) return Boolean.FALSE;
    try { if (t.contains(".")) return Double.parseDouble(t); else return Integer.parseInt(t); }
    catch (Exception ignored) { // reason: not a number; caller treats it as a string
    }
    return t;
  }
}
