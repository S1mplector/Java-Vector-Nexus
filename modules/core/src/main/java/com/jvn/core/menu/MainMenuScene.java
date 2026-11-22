package com.jvn.core.menu;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.audio.AudioFacade;
import com.jvn.core.audio.AudioPlaybackStatus;
import com.jvn.core.engine.Engine;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.config.MenuActionSpec;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnEntryScriptResolver;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioLoader;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.save.VnSaveManager;

public class MainMenuScene implements Scene {
  private static final Logger LOG = LoggerFactory.getLogger(MainMenuScene.class);
  private static final int NO_SELECTION = -1;

  private final Engine engine;
  private final VnSettings settingsModel;
  private final VnSaveManager saveManager;
  private final String defaultScriptName;
  private final AudioFacade audio;
  private final String menuId;
  private final MenuProfile menuProfile;
  private final MenuScreenSpec menuScreen;
  private final MenuLayoutSpec menuLayout;
  private final boolean allowNoSelection;
  private final VnScenarioLoader scenarioLoader = new VnScenarioLoader();
  private int selected = NO_SELECTION;

  // Title screen configuration
  private String titleBgmPath = null;
  private double titleBgmVolume = 0.7;
  private boolean bgmStarted = false;

  public MainMenuScene(Engine engine, VnSettings settingsModel, VnSaveManager saveManager, String defaultScriptName, AudioFacade audio) {
    this(engine, settingsModel, saveManager, defaultScriptName, audio, "main");
  }

  public MainMenuScene(Engine engine, VnSettings settingsModel, VnSaveManager saveManager, String defaultScriptName, AudioFacade audio, String menuId) {
    this(engine, settingsModel, saveManager, defaultScriptName, audio, menuId, null);
  }

  public MainMenuScene(Engine engine, VnSettings settingsModel, VnSaveManager saveManager, String defaultScriptName, AudioFacade audio, String menuId, MenuProfile profile) {
    this.engine = engine;
    this.settingsModel = settingsModel == null ? new VnSettings() : settingsModel;
    this.saveManager = saveManager == null ? new VnSaveManager() : saveManager;
    String resolvedDefault = VnEntryScriptResolver.resolveEntryScript(defaultScriptName, null);
    this.defaultScriptName = resolvedDefault == null ? "story/prologue.vns" : resolvedDefault;
    this.audio = audio;
    if (profile == null) {
      MenuProfileLoader.LoadResult menuLoad = MenuProfileLoader.loadWithDiagnostics();
      this.menuProfile = menuLoad.profile();
      for (String warning : menuLoad.diagnostics()) {
        LOG.warn("Menu profile: {}", warning);
      }
    } else {
      this.menuProfile = profile;
    }
    this.menuId = normalize(menuId, menuProfile.defaultScreenId());
    this.menuScreen = menuProfile.screen(this.menuId);
    this.menuLayout = menuProfile.layout(menuScreen.layoutId());
    this.allowNoSelection = equalsIgnoreCase(this.menuId, menuProfile.defaultScreenId());
    this.selected = allowNoSelection ? NO_SELECTION : firstSelectableIndex(0);
  }

  /**
   * Configure title screen BGM. Pass NaN to use the player's music volume.
   */
  public void setTitleBgm(String path, double volume) {
    this.titleBgmPath = path;
    this.titleBgmVolume = Double.isNaN(volume) ? volume : Math.max(0, Math.min(1, volume));
  }

  public String getTitleBgmPath() { return titleBgmPath; }
  public double getTitleBgmVolume() { return titleBgmVolume; }
  public String getMenuId() { return menuId; }
  public VnSettings getSettingsModel() { return settingsModel; }
  public VnSaveManager getSaveManager() { return saveManager; }
  public String getDefaultScriptName() { return defaultScriptName; }
  public AudioFacade getAudioFacade() { return audio; }
  public MenuProfile getMenuProfile() { return menuProfile; }
  public MenuScreenSpec getMenuScreen() { return menuScreen; }
  public MenuLayoutSpec getMenuLayout() { return menuLayout; }
  public MenuStyleSpec getDefaultMenuStyle() { return menuProfile.style(menuScreen.defaultStyleId()); }
  public int getItemCount() { return menuScreen.items().size(); }
  public int getSelected() { return selected; }

  public boolean isItemEnabled(int idx) {
    MenuItemSpec item = getItem(idx);
    return item != null && item.enabled();
  }

  public MenuItemSpec getItem(int idx) {
    if (idx < 0 || idx >= getItemCount()) return null;
    return menuScreen.items().get(idx);
  }

  public MenuItemSpec getMenuItemSpec(int idx) {
    return getItem(idx);
  }

  public MenuStyleSpec getStyleForIndex(int idx) {
    MenuItemSpec item = getItem(idx);
    String styleId = item != null ? normalize(item.styleId(), menuScreen.defaultStyleId()) : menuScreen.defaultStyleId();
    return menuProfile.style(styleId);
  }

  public String getDisplayTitle() {
    return resolveDisplayText(menuScreen.titleText());
  }

  public String getDisplaySubtitle() {
    return resolveDisplayText(menuScreen.subtitleText());
  }

  public String getDisplayHints() {
    return resolveDisplayText(menuScreen.hintsText());
  }

  public String[] getDisplayItems() {
    List<MenuItemSpec> items = menuScreen.items();
    String[] labels = new String[items.size()];
    for (int i = 0; i < items.size(); i++) {
      labels[i] = displayLabel(items.get(i));
    }
    return labels;
  }

  public void moveSelection(int delta) {
    int count = getItemCount();
    if (count <= 0 || delta == 0) return;
    int dir = delta > 0 ? 1 : -1;
    int steps = Math.abs(delta);
    int next = selected;
    if (next < 0) {
      next = firstSelectableIndex(dir > 0 ? 0 : count - 1);
      steps = Math.max(0, steps - 1);
    }
    for (int i = 0; i < steps; i++) {
      next = nextSelectable(next, dir);
    }
    selected = next;
  }

  public void setSelected(int idx) {
    int count = getItemCount();
    if (count <= 0) {
      selected = allowNoSelection ? NO_SELECTION : 0;
      return;
    }
    if (idx < 0) {
      if (allowNoSelection) {
        selected = NO_SELECTION;
        return;
      }
      idx = 0;
    }
    int clamped = Math.max(0, Math.min(idx, count - 1));
    if (isItemEnabled(clamped)) {
      selected = clamped;
      return;
    }
    selected = firstSelectableIndex(clamped);
  }

  public void activateSelected() {
    MenuItemSpec item = getItem(selected);
    if (item == null || !item.enabled()) return;
    MenuActionSpec action = item.action();
    if (handleCustomMenuAction(action, item.id())) {
      return;
    }
    switch (action.type()) {
      case NEW_GAME -> startNewGame(defaultScriptName);
      case RUN_SCRIPT -> startNewGame(normalize(action.target(), defaultScriptName));
      case LOAD_MENU -> {
        String script = normalize(action.target(), defaultScriptName);
        engine.scenes().push(new LoadMenuScene(engine, saveManager, script, settingsModel, audio));
      }
      case SETTINGS_MENU -> {
        com.jvn.core.input.ActionBindingProfile profile =
            com.jvn.core.input.ActionBindingProfile.deserialize(settingsModel.getInputProfileSerialized());
        String targetMenu = normalize(action.target(), "settings");
        engine.scenes().push(new SettingsScene(
            engine,
            saveManager,
            defaultScriptName,
            settingsModel,
            audio,
            profile,
            null,
            targetMenu
        ));
      }
      case OPEN_MENU -> openConfiguredMenu(action.target());
      case MAIN_MENU -> openConfiguredMenu("main");
      case BACK -> {
        if (engine != null) engine.scenes().pop();
      }
      case QUIT -> {
        if (!openQuitConfirmationMenu(action) && engine != null) {
          engine.stop();
        }
      }
      case GALLERY -> {
        com.jvn.core.vn.VnPersistentStore store = new com.jvn.core.vn.VnPersistentStore();
        engine.scenes().push(new com.jvn.core.menu.gallery.GalleryScene(engine, store));
      }
      case MUSIC_ROOM -> {
        com.jvn.core.vn.VnPersistentStore store = new com.jvn.core.vn.VnPersistentStore();
        engine.scenes().push(new com.jvn.core.menu.gallery.MusicRoomScene(engine, audio, store));
      }
      case SAVE_MENU -> LOG.debug("Ignoring save menu action in title context");
      case HISTORY_MENU, TOGGLE_SKIP, TOGGLE_AUTO ->
          LOG.debug("Ignoring gameplay-only menu action {} in title context", action.type());
      case NOOP -> {
      }
    }
  }

  private boolean handleCustomMenuAction(MenuActionSpec action, String itemId) {
    if (action == null || !action.isCustomAction() || engine == null) return false;
    var handler = engine.getMenuActionHandler();
    if (handler == null) return false;
    try {
      return handler.handle(new MenuActionContext(
          engine,
          menuId,
          normalize(itemId, ""),
          defaultScriptName,
          action
      ));
    } catch (Exception ex) {
      LOG.warn("Custom menu action '{}' failed in menu '{}'", action.actionKey(), menuId, ex);
      return false;
    }
  }

  private void openConfiguredMenu(String targetMenu) {
    String requested = normalize(targetMenu, null);
    if (requested == null || engine == null) return;
    if (!menuProfile.screens().containsKey(requested)) {
      LOG.debug("Configured menu '{}' not found in profile", requested);
      return;
    }
    if (requested.equalsIgnoreCase(menuId)) return;
    MainMenuScene child = new MainMenuScene(engine, settingsModel, saveManager, defaultScriptName, audio, requested);
    if (titleBgmPath != null) {
      child.setTitleBgm(titleBgmPath, titleBgmVolume);
    }
    engine.scenes().push(child);
  }

  private boolean openQuitConfirmationMenu(MenuActionSpec action) {
    String target = normalize(action != null ? action.target() : null, null);
    if (target == null && !equalsIgnoreCase(menuId, "confirm_exit") && menuProfile.hasScreen("confirm_exit")) {
      target = "confirm_exit";
    }
    if (target == null || target.isBlank() || equalsIgnoreCase(target, menuId)) return false;
    if (!menuProfile.hasScreen(target)) return false;
    openConfiguredMenu(target);
    return true;
  }

  private void startNewGame(String scriptName) {
    startFromEntry(scriptName, "");
  }

  /** Start gameplay directly at an authored arc entry, with normal settings, audio and interop. */
  public void startFromEntry(String scriptName, String entryLabel) {
    String resolvedScript = normalize(scriptName, defaultScriptName);
    VnScene vnScene;
    int startIndex = 0;
    try {
      VnScenario scenario = scenarioLoader.load(resolvedScript);
      if (entryLabel != null && !entryLabel.isBlank()) {
        Integer index = scenario.getLabelIndex(entryLabel);
        if (index == null) throw new IllegalArgumentException("Unknown entry label @" + entryLabel);
        startIndex = index;
      }
      vnScene = new VnScene(scenario);
    } catch (Exception e) {
      LOG.warn("Failed to load script '{}': {}", resolvedScript, e.toString());
      vnScene = MenuScenarioFallbacks.scriptLoadErrorScene(resolvedScript, e);
    }
    vnScene.getState().setSourceScriptName(resolvedScript);
    if (audio != null) vnScene.setAudioFacade(audio);
    if (engine != null && engine.getVnInteropFactory() != null) {
      vnScene.setInterop(engine.getVnInteropFactory().create(engine));
    }
    // Apply settings model to scene settings
    VnSettings s = vnScene.getState().getSettings();
    s.copyFrom(settingsModel);
    if (audio != null) {
      audio.setBgmVolume(s.getBgmVolume());
      audio.setSfxVolume(s.getSfxVolume());
      audio.setVoiceVolume(s.getVoiceVolume());
    }
    if (engine != null) {
      engine.setFixedUpdateStepMs(settingsModel.getPhysicsFixedStepMs(), settingsModel.getPhysicsMaxSubSteps());
      MenuAudioLifecycle.beginGameplay(engine, audio);
      if (startIndex > 0) {
        vnScene.preflightState(startIndex);
        vnScene.getState().setCurrentNodeIndex(startIndex);
      }
      engine.scenes().push(vnScene);
    }
  }

  private VnScenario loadScenario(String scriptName) {
    try {
      return scenarioLoader.load(scriptName);
    } catch (Exception e) {
      LOG.warn("Failed to load script '{}': {}", scriptName, e.toString());
      return MenuScenarioFallbacks.missingScriptScenario(scriptName, e);
    }
  }

  @Override
  public void update(long deltaMs) {
    // Start title BGM on first update if configured
    if (!bgmStarted && audio != null && effectiveTitleBgm() != null) startTitleBgm();
  }

  @Override
  public void onEnter() {
    if (audio == null) return;
    // When no title BGM is configured, ensure gameplay BGM does not leak into menu screens.
    if (titleBgmPath == null || titleBgmPath.isBlank()) {
      if (sharedMenuBgm() == null) audio.stopBgm();
      bgmStarted = false;
      return;
    }
    if (!bgmStarted) startTitleBgm();
  }

  @Override
  public void onResume() {
    String track = effectiveTitleBgm();
    if (audio == null || track == null || !bgmStarted) return;
    if (track.equals(sharedMenuBgm())) {
      bgmStarted = false;
      return;
    }
    var snapshot = audio.snapshot();
    if ("unknown".equals(snapshot.backendId())) return;
    if (track.equals(snapshot.bgmTrackId())
        && (snapshot.bgmStatus() == AudioPlaybackStatus.PLAYING
            || snapshot.bgmStatus() == AudioPlaybackStatus.LOADING)) return;
    bgmStarted = false;
  }

  private void startTitleBgm() {
    String track = effectiveTitleBgm();
    float volume = titleBgmPath == null || Double.isNaN(titleBgmVolume)
        ? settingsModel.getBgmVolume() : (float) titleBgmVolume;
    audio.setBgmVolume(volume);
    if (track.equals(sharedMenuBgm())) audio.ensureBgmPlaying(track, true);
    else audio.playBgm(track, true);
    bgmStarted = true;
  }

  private String effectiveTitleBgm() {
    return titleBgmPath == null || titleBgmPath.isBlank() ? sharedMenuBgm() : titleBgmPath;
  }

  private String sharedMenuBgm() {
    return engine == null ? null : engine.getSharedMenuBgmPath();
  }

  @Override
  public void onExit() {
    // Don't stop BGM when pushing new scene - let child scenes control it
  }

  private int firstSelectableIndex(int fallback) {
    int count = getItemCount();
    if (count <= 0) return 0;
    int start = Math.max(0, Math.min(fallback, count - 1));
    if (isItemEnabled(start)) return start;
    for (int i = 1; i <= count; i++) {
      int idx = (start + i) % count;
      if (isItemEnabled(idx)) return idx;
    }
    return start;
  }

  private int nextSelectable(int from, int dir) {
    int count = getItemCount();
    if (count <= 0) return 0;
    if (from < 0) return firstSelectableIndex(dir > 0 ? 0 : count - 1);
    int start = Math.max(0, Math.min(from, count - 1));
    if (!menuScreen.wrapSelection()) {
      int idx = start;
      while (true) {
        idx += dir;
        if (idx < 0 || idx >= count) return from;
        if (isItemEnabled(idx)) return idx;
      }
    }
    int idx = start;
    for (int i = 0; i < count; i++) {
      idx = (idx + dir + count) % count;
      if (isItemEnabled(idx)) return idx;
    }
    return from;
  }

  private static boolean equalsIgnoreCase(String a, String b) {
    if (a == null || b == null) return false;
    return a.equalsIgnoreCase(b);
  }

  private String displayLabel(MenuItemSpec item) {
    if (item == null) return "";
    String explicit = resolveDisplayText(item.label());
    if (explicit != null) return explicit;
    return switch (item.action().type()) {
      case NEW_GAME -> Localization.t("menu.new_game");
      case LOAD_MENU -> Localization.t("menu.load");
      case SETTINGS_MENU -> Localization.t("menu.settings");
      case HISTORY_MENU -> Localization.t("history.title");
      case TOGGLE_SKIP -> "Skip";
      case TOGGLE_AUTO -> "Auto";
      case QUIT -> Localization.t("menu.quit");
      case BACK -> Localization.t("common.back");
      case SAVE_MENU -> Localization.t("save.title");
      case MAIN_MENU -> Localization.t("app.title");
      case RUN_SCRIPT -> Localization.t("menu.new_game");
      case OPEN_MENU -> titleize(normalize(item.action().target(), item.id()));
      case GALLERY -> Localization.t("menu.gallery");
      case MUSIC_ROOM -> Localization.t("menu.music_room");
      case NOOP -> titleize(item.id());
    };
  }

  private String resolveDisplayText(String raw) {
    if (raw == null) return null;
    String value = raw.trim();
    if (value.isEmpty()) return "";
    return Localization.translateText(raw);
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }

  private String titleize(String raw) {
    String s = normalize(raw, "item").replace('_', ' ').replace('-', ' ');
    if (s.isEmpty()) return "item";
    StringBuilder out = new StringBuilder();
    boolean upper = true;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        upper = true;
        out.append(c);
      } else if (upper) {
        out.append(Character.toUpperCase(c));
        upper = false;
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
