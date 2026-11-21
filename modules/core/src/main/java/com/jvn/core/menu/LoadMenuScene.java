package com.jvn.core.menu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.audio.AudioFacade;
import com.jvn.core.engine.Engine;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.config.MenuActionSpec;
import com.jvn.core.menu.config.MenuActionType;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnScenarioLoader;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnEntryScriptResolver;
import com.jvn.core.vn.save.VnSaveData;
import com.jvn.core.vn.save.VnSaveManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoadMenuScene implements Scene {
  private static final Logger log = LoggerFactory.getLogger(LoadMenuScene.class);
  private static final Logger LOG = LoggerFactory.getLogger(LoadMenuScene.class);
  private static final String FAVORITES_FILE_NAME = ".load-favorites.properties";
  private static final String FAVORITES_PROPERTY = "favorites";
  private final Engine engine;
  private final VnSaveManager saveManager;
  private final String defaultScriptName;
  private final com.jvn.core.vn.VnSettings settingsModel;
  private final AudioFacade audio;
  private final MenuProfile menuProfile;
  private final MenuScreenSpec menuScreen;
  private final MenuLayoutSpec menuLayout;
  private final VnScenarioLoader scenarioLoader = new VnScenarioLoader();
  private final List<String> saves = new ArrayList<>();
  private final Set<String> favorites = new HashSet<>();
  private final String scopedScriptName;
  private final String scopedScenarioId;
  private VnScene gameplayVnScene;
  private boolean favoritesOnly = false;
  private int selected = -1;

  public LoadMenuScene(Engine engine, VnSaveManager saveManager, String defaultScriptName, com.jvn.core.vn.VnSettings settingsModel, AudioFacade audio) {
    this(engine, saveManager, defaultScriptName, settingsModel, audio, null);
  }

  LoadMenuScene(
      Engine engine,
      VnSaveManager saveManager,
      String defaultScriptName,
      com.jvn.core.vn.VnSettings settingsModel,
      AudioFacade audio,
      MenuProfile profile
  ) {
    this.engine = engine;
    this.saveManager = saveManager;
    String resolvedDefault = VnEntryScriptResolver.resolveEntryScript(defaultScriptName, null);
    this.defaultScriptName = resolvedDefault == null ? "story/prologue.vns" : resolvedDefault;
    this.settingsModel = settingsModel == null ? new com.jvn.core.vn.VnSettings() : settingsModel;
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
    this.menuScreen = menuProfile.screen("load");
    this.menuLayout = menuProfile.layout(menuScreen.layoutId());
    this.scopedScriptName = normalizeScriptName(this.defaultScriptName);
    this.scopedScenarioId = resolveScenarioIdForScript(this.defaultScriptName);
    loadFavoritesFromDisk();
    refresh();
  }

  public Long getSelectedTimestamp() {
    String name = getSelectedName();
    if (name == null) return null;
    try {
      VnSaveData data = saveManager.load(name);
      return data.getSaveTimestamp();
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return null;
    }
  }

  public String getSelectedScenarioId() {
    String name = getSelectedName();
    if (name == null) return null;
    try {
      VnSaveData data = saveManager.load(name);
      return data.getScenarioId();
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return null;
    }
  }

  public Integer getSelectedNodeIndex() {
    String name = getSelectedName();
    if (name == null) return null;
    try {
      VnSaveData data = saveManager.load(name);
      return data.getCurrentNodeIndex();
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return null;
    }
  }

  public void refresh() {
    String selectedName = selected >= 0 && selected < saves.size() ? saves.get(selected) : null;
    saves.clear();
    List<String> names = new ArrayList<>(saveManager.listSaves());
    favorites.retainAll(names);
    boolean gameScoped = saveManager.isGameScoped();
    List<String> filtered = new ArrayList<>();
    try {
      var times = new HashMap<String, Long>();
      for (String n : names) {
        try {
          VnSaveData d = saveManager.load(n);
          times.put(n, d.getSaveTimestamp());
          if (gameScoped || matchesCurrentScope(d)) {
            if (!favoritesOnly || favorites.contains(n)) {
              filtered.add(n);
            }
          }
        } catch (Exception e) {
          times.put(n, 0L);
        }
      }
      filtered.sort(Comparator.comparing((String n) -> times.getOrDefault(n, 0L)).reversed());
    } catch (Exception e) {
      // reason: timestamp-based sort is best-effort; unsorted list is a safe fallback
      log.warn("Save list sort failed, falling back to insertion order", e);
    }
    saves.addAll(filtered);
    if (selectedName != null) {
      int idx = saves.indexOf(selectedName);
      if (idx >= 0) {
        selected = idx;
        return;
      }
    }
    selected = saves.isEmpty() ? -1 : Math.max(0, Math.min(selected, saves.size() - 1));
  }

  public List<String> getSaves() { return saves; }
  public int getSelected() { return selected; }
  public VnSaveManager getSaveManager() { return saveManager; }
  public String getDefaultScriptName() { return defaultScriptName; }
  public com.jvn.core.vn.VnSettings getSettingsModel() { return settingsModel; }
  public AudioFacade getAudioFacade() { return audio; }
  public VnScene getGameplayVnScene() { return gameplayVnScene; }
  public LoadMenuScene withGameplayVnScene(VnScene scene) {
    this.gameplayVnScene = scene;
    return this;
  }
  public int getItemCount() { return saves.size(); }
  public boolean isFavoritesOnly() { return favoritesOnly; }

  public int getPageSize() {
    Integer configured = menuLayout != null ? menuLayout.maxVisibleItems() : null;
    return configured != null && configured > 0 ? configured : Math.max(1, saves.size());
  }

  public int getPageCount() {
    if (saves.isEmpty()) return 1;
    int pageSize = getPageSize();
    return Math.max(1, (saves.size() + pageSize - 1) / pageSize);
  }

  public int getCurrentPageIndex() {
    if (saves.isEmpty()) return 0;
    int pageSize = getPageSize();
    int maxPage = Math.max(0, getPageCount() - 1);
    return Math.max(0, Math.min(maxPage, selected / pageSize));
  }

  public int getVisibleStartIndex() {
    int pageSize = getPageSize();
    int start = getCurrentPageIndex() * pageSize;
    return Math.max(0, Math.min(start, Math.max(0, saves.size() - 1)));
  }

  public int getVisibleCount() {
    if (saves.isEmpty()) return 0;
    int start = getVisibleStartIndex();
    return Math.max(0, Math.min(getPageSize(), saves.size() - start));
  }

  public double getPageProgress01() {
    int pages = getPageCount();
    if (pages <= 1) return 0.0;
    return (double) getCurrentPageIndex() / (double) (pages - 1);
  }

  public void movePage(int deltaPages) {
    if (deltaPages == 0) return;
    setPageIndex(getCurrentPageIndex() + deltaPages);
  }

  public void setPageIndex(int pageIndex) {
    if (saves.isEmpty()) {
      selected = -1;
      return;
    }
    int pages = getPageCount();
    int pageSize = getPageSize();
    int clampedPage = Math.max(0, Math.min(pageIndex, pages - 1));
    int local = Math.max(0, Math.min(pageSize - 1, selected % pageSize));
    int next = clampedPage * pageSize + local;
    if (next >= saves.size()) next = clampedPage * pageSize;
    selected = Math.max(0, Math.min(next, saves.size() - 1));
  }

  public void setPageFromProgress01(double progress01) {
    int pages = getPageCount();
    if (pages <= 1) return;
    double t = progress01;
    if (Double.isNaN(t) || Double.isInfinite(t)) t = 0.0;
    if (t < 0.0) t = 0.0;
    if (t > 1.0) t = 1.0;
    int targetPage = (int) Math.round(t * (pages - 1));
    setPageIndex(targetPage);
  }

  public boolean isFavoriteAt(int index) {
    if (index < 0 || index >= saves.size()) return false;
    return favorites.contains(saves.get(index));
  }

  public boolean toggleFavoriteAt(int index) {
    if (index < 0 || index >= saves.size()) return false;
    String name = saves.get(index);
    if (name == null || name.isBlank()) return false;
    boolean nowFavorite;
    if (favorites.contains(name)) {
      favorites.remove(name);
      nowFavorite = false;
    } else {
      favorites.add(name);
      nowFavorite = true;
    }
    saveFavoritesToDisk();
    if (favoritesOnly) {
      refresh();
    }
    return nowFavorite;
  }

  public boolean toggleFavoriteSelected() {
    return toggleFavoriteAt(selected);
  }

  public void toggleFavoritesOnly() {
    favoritesOnly = !favoritesOnly;
    refresh();
  }

  public MenuLayoutSpec getMenuLayout() { return menuLayout; }
  public MenuScreenSpec getMenuScreen() { return menuScreen; }
  public MenuStyleSpec getDefaultMenuStyle() { return menuProfile.style(menuScreen.defaultStyleId()); }

  public MenuStyleSpec getStyleForIndex(int idx) {
    if (idx < 0) return menuProfile.style(menuScreen.defaultStyleId());
    if (idx < menuScreen.items().size()) {
      var item = menuScreen.items().get(idx);
      String styleId = (item != null && item.styleId() != null && !item.styleId().isBlank())
          ? item.styleId()
          : menuScreen.defaultStyleId();
      return menuProfile.style(styleId);
    }
    MenuStyleSpec slotStyle = styleForItemId("save_slot", "slot", "entry");
    if (slotStyle != null) return slotStyle;
    if (!menuScreen.items().isEmpty()) {
      var repeated = menuScreen.items().get(Math.floorMod(idx, menuScreen.items().size()));
      String styleId = (repeated != null && repeated.styleId() != null && !repeated.styleId().isBlank())
          ? repeated.styleId()
          : menuScreen.defaultStyleId();
      return menuProfile.style(styleId);
    }
    return menuProfile.style(menuScreen.defaultStyleId());
  }

  public String getDisplayTitle() {
    String t = resolveDisplayText(menuScreen.titleText());
    String resolved = t != null ? t : Localization.t("load.title");
    return !resolved.isBlank() && "load journey".equalsIgnoreCase(resolved.trim()) ? "Load Save" : resolved;
  }

  public String getDisplaySubtitle() {
    return resolveDisplayText(menuScreen.subtitleText());
  }

  public String getDisplayHints() {
    String t = resolveDisplayText(menuScreen.hintsText());
    if (t == null) {
      return Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc    "
          + Localization.t("load.delete") + ": Delete    " + Localization.t("load.rename") + ": R";
    }
    return t;
  }

  public boolean wrapsSelection() { return menuScreen.wrapSelection(); }

  public void moveSelection(int delta) {
    if (saves.isEmpty()) return;
    if (selected < 0 || selected >= saves.size()) {
      selected = delta < 0 ? Math.max(0, saves.size() - 1) : 0;
      return;
    }
    int count = saves.size();
    if (menuScreen.wrapSelection()) {
      selected = (selected + delta + count) % count;
    } else {
      selected = Math.max(0, Math.min(selected + delta, count - 1));
    }
  }
  public void setSelected(int idx) {
    int count = saves.size();
    if (count <= 0) { selected = -1; return; }
    if (idx < 0) { selected = -1; return; }
    selected = Math.max(0, Math.min(idx, count - 1));
  }

  public MenuActionSpec getSelectedAction() {
    MenuActionSpec action = actionForItemId("save_slot", "slot", "entry");
    return action != null ? action : new MenuActionSpec(MenuActionType.LOAD_MENU, null);
  }

  public MenuItemSpec getMenuItemSpec(int idx) {
    if (idx < 0) return null;
    if (idx < menuScreen.items().size()) {
      return menuScreen.items().get(idx);
    }
    MenuItemSpec template = itemForItemId("save_slot", "slot", "entry");
    if (template != null) return template;
    if (!menuScreen.items().isEmpty()) {
      return menuScreen.items().get(Math.floorMod(idx, menuScreen.items().size()));
    }
    return null;
  }

  public boolean activateSelected() {
    MenuActionSpec action = getSelectedAction();
    if (handleCustomMenuAction(action)) {
      return true;
    }
    return switch (action.type()) {
      case LOAD_MENU -> {
        loadSelected();
        yield true;
      }
      case NEW_GAME -> {
        startNewGame(defaultScriptName);
        yield true;
      }
      case RUN_SCRIPT -> {
        startNewGame(normalize(action.target(), defaultScriptName));
        yield true;
      }
      case SETTINGS_MENU -> {
        com.jvn.core.input.ActionBindingProfile profile =
            com.jvn.core.input.ActionBindingProfile.deserialize(settingsModel.getInputProfileSerialized());
        if (engine != null) {
          String targetMenu = normalize(action.target(), "settings");
          engine.scenes().push(new SettingsScene(
              engine,
              saveManager,
              defaultScriptName,
              settingsModel,
              audio,
              profile,
              menuProfile,
              targetMenu
          ));
        }
        yield true;
      }
      case OPEN_MENU -> {
        openConfiguredMenu(action.target());
        yield true;
      }
      case MAIN_MENU -> {
        openConfiguredMenu("main");
        yield true;
      }
      case SAVE_MENU -> {
        com.jvn.core.scene.Scene current = engine != null ? engine.scenes().peek() : null;
        if (current instanceof VnScene vnScene && engine != null) {
          engine.scenes().push(new SaveMenuScene(engine, saveManager, vnScene));
        }
        yield true;
      }
      case BACK -> {
        if (engine != null) {
          engine.scenes().pop();
        }
        yield true;
      }
      case QUIT -> {
        if (!openQuitConfirmationMenu(action.target()) && engine != null) {
          engine.stop();
        }
        yield true;
      }
      case NOOP -> true;
      default -> true;
    };
  }

  private boolean handleCustomMenuAction(MenuActionSpec action) {
    if (action == null || !action.isCustomAction() || engine == null) return false;
    var handler = engine.getMenuActionHandler();
    if (handler == null) return false;

    MenuItemSpec selectedItem = getMenuItemSpec(selected);
    if (selectedItem == null) {
      selectedItem = getMenuItemSpec(0);
    }
    String sourceItemId = selectedItem != null ? normalize(selectedItem.id(), "") : "";
    try {
      return handler.handle(new MenuActionContext(
          engine,
          "load",
          sourceItemId,
          defaultScriptName,
          action
      ));
    } catch (Exception ex) {
      LOG.warn("Custom menu action '{}' failed in load menu", action.actionKey(), ex);
      return false;
    }
  }

  public String getSaveDirectory() { return saveManager.getSaveDirectory(); }
  public String getSelectedName() {
    if (saves.isEmpty() || selected < 0 || selected >= saves.size()) return null;
    return saves.get(selected);
  }
  public String getSelectedRpgSummary() {
    String name = getSelectedName();
    if (name == null) return null;
    try {
      VnSaveData data = saveManager.load(name);
      return summarizeRpgState(data.getRpgState());
    } catch (Exception ignored) {
      // reason: RPG state summary is display-only; unreadable save data yields null preview
      return null;
    }
  }

  public boolean deleteSelected() {
    if (saves.isEmpty() || selected < 0 || selected >= saves.size()) return false;
    String name = saves.get(selected);
    boolean ok = saveManager.deleteSave(name);
    if (ok && name != null) {
      favorites.remove(name);
      saveFavoritesToDisk();
    }
    refresh();
    return ok;
  }

  public boolean renameSelected(String newName) {
    if (saves.isEmpty() || selected < 0 || selected >= saves.size() || newName == null || newName.isBlank()) return false;
    String old = saves.get(selected);
    boolean ok = saveManager.renameSave(old, newName);
    if (ok) {
      String oldSanitized = sanitizeSaveName(old);
      String newSanitized = sanitizeSaveName(newName);
      if (oldSanitized != null && newSanitized != null && favorites.remove(oldSanitized)) {
        favorites.add(newSanitized);
      }
      saveFavoritesToDisk();
    }
    refresh();
    return ok;
  }

  /**
   * Try to provide a preview image path for the selected save.
   * Uses the currentBackgroundId from the saved data and maps it via the scenario's backgrounds.
   * Returns a classpath resource path (e.g., game/images/bg_room.png) or null on failure.
   */
  public String getSelectedPreviewImagePath() {
    String name = getSelectedName();
    if (name == null) return null;
    try {
      VnSaveData data = saveManager.load(name);
      String bgId = data.getCurrentBackgroundId();
      if (bgId == null) return null;
      String scenId = data.getScenarioId();
      String script = scenId != null ? resolveScriptForScenarioId(scenId) : null;
      VnScenario scen = loadScenario(script != null ? script : defaultScriptName);
      if (scen == null) return null;
      com.jvn.core.vn.VnBackground bg = scen.getBackground(bgId);
      return bg != null ? bg.getImagePath() : null;
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return null;
    }
  }

  public void loadSelected() {
    if (saves.isEmpty() || selected < 0 || selected >= saves.size()) return;
    String name = saves.get(selected);
    try {
      VnSaveData data = saveManager.load(name);
      String script = data.getScriptName();
      if (script == null || script.isBlank()) {
        String scenId = data.getScenarioId();
        script = scenId != null ? resolveScriptForScenarioId(scenId) : null;
      }
      if (script == null) {
        LOG.warn("Could not resolve script from save data, falling back to {}", defaultScriptName);
        script = defaultScriptName;
      }
      VnScenario scenario = loadScenario(script);
      VnScene scene = new VnScene(scenario);
      scene.getState().setSourceScriptName(script);
      if (audio != null) scene.setAudioFacade(audio);
      if (engine != null && engine.getVnInteropFactory() != null) {
        scene.setInterop(engine.getVnInteropFactory().create(engine));
      }
      saveManager.applyToState(data, scene.getState());
      if (audio != null) {
        var s = scene.getState().getSettings();
        audio.setBgmVolume(s.getBgmVolume());
        audio.setSfxVolume(s.getSfxVolume());
        audio.setVoiceVolume(s.getVoiceVolume());
      }
      MenuAudioLifecycle.beginGameplay(audio);
      engine.scenes().push(scene);
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
  }

  private void openConfiguredMenu(String targetMenu) {
    String requested = normalize(targetMenu, null);
    if (requested == null || engine == null) return;
    if (!menuProfile.screens().containsKey(requested)) {
      LOG.debug("Configured menu '{}' not found in profile", requested);
      return;
    }
    MainMenuScene child = new MainMenuScene(engine, settingsModel, saveManager, defaultScriptName, audio, requested);
    engine.scenes().push(child);
  }

  private boolean openQuitConfirmationMenu(String targetMenu) {
    String requested = normalize(targetMenu, null);
    if (requested == null && menuProfile.screens().containsKey("confirm_exit")) {
      requested = "confirm_exit";
    }
    if (requested == null || requested.isBlank() || "load".equalsIgnoreCase(requested)) return false;
    if (!menuProfile.screens().containsKey(requested) || engine == null) return false;
    MainMenuScene child = new MainMenuScene(engine, settingsModel, saveManager, defaultScriptName, audio, requested);
    engine.scenes().push(child);
    return true;
  }

  private void startNewGame(String scriptName) {
    String resolvedScript = normalize(scriptName, defaultScriptName);
    VnScene scene;
    try {
      VnScenario scenario = scenarioLoader.load(resolvedScript);
      scene = new VnScene(scenario);
    } catch (Exception e) {
      LOG.warn("Failed to load script '{}': {}", resolvedScript, e.toString());
      scene = MenuScenarioFallbacks.scriptLoadErrorScene(resolvedScript, e);
    }
    scene.getState().setSourceScriptName(resolvedScript);
    if (audio != null) scene.setAudioFacade(audio);
    if (engine != null && engine.getVnInteropFactory() != null) {
      scene.setInterop(engine.getVnInteropFactory().create(engine));
    }
    var s = scene.getState().getSettings();
    s.copyFrom(settingsModel);
    if (audio != null) {
      audio.setBgmVolume(s.getBgmVolume());
      audio.setSfxVolume(s.getSfxVolume());
      audio.setVoiceVolume(s.getVoiceVolume());
    }
    if (engine != null) {
      engine.setFixedUpdateStepMs(settingsModel.getPhysicsFixedStepMs(), settingsModel.getPhysicsMaxSubSteps());
      MenuAudioLifecycle.beginGameplay(audio);
      engine.scenes().push(scene);
    }
  }

  private String summarizeRpgState(Object rpg) {
    if (rpg instanceof com.jvn.core.rpg.RpgState state) {
      int party = state.getActors().size();
      double totalHp = state.getActors().values().stream().mapToDouble(com.jvn.core.rpg.RpgStats::getHp).sum();
      double totalMax = state.getActors().values().stream().mapToDouble(com.jvn.core.rpg.RpgStats::getMaxHp).sum();
      return "Party " + party + " • HP " + Math.round(totalHp) + "/" + Math.round(totalMax);
    }
    return null;
  }

  private boolean matchesCurrentScope(VnSaveData data) {
    if (data == null) return false;
    String saveScriptName = normalizeScriptName(data.getScriptName());
    String saveScenarioId = normalize(data.getScenarioId(), null);
    boolean scriptMatch = saveScriptName != null && scopedScriptName != null && saveScriptName.equals(scopedScriptName);
    boolean scenarioMatch = saveScenarioId != null && scopedScenarioId != null && saveScenarioId.equals(scopedScenarioId);
    return scriptMatch || scenarioMatch;
  }

  private String resolveScenarioIdForScript(String scriptName) {
    String scopedScript = normalizeScriptName(scriptName);
    if (scopedScript == null) return null;
    try {
      VnScenario scenario = scenarioLoader.load(scopedScript);
      return scenario == null ? null : normalize(scenario.getId(), null);
    } catch (Exception e) {
      LOG.debug("Could not resolve scenario ID for script '{}': {}", scopedScript, e.toString());
      return null;
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

  private String resolveScriptForScenarioId(String scenarioId) {
    try {
      AssetCatalog assets = new AssetCatalog();
      List<String> scripts = assets.listScripts();
      Pattern p = Pattern.compile("^@scenario\\s+(.+)$");
      for (String s : scripts) {
        try (InputStream in = assets.open(AssetType.SCRIPT, s);
             BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
          String line;
          int lines = 0;
          while ((line = br.readLine()) != null && lines++ < 50) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            Matcher m = p.matcher(line);
            if (m.matches()) {
              String id = m.group(1);
              if (scenarioId.equals(id)) return s;
              break;
            }
          }
        } catch (Exception e) {
          LOG.debug("Failed to inspect script {}: {}", s, e.toString());
        }
      }
    } catch (Exception e) {
      LOG.warn("Failed to resolve script for scenarioId {}: {}", scenarioId, e.toString());
    }
    return null;
  }

  @Override
  public void update(long deltaMs) { }

  private MenuStyleSpec styleForItemId(String... ids) {
    if (ids == null || ids.length == 0) return null;
    for (String id : ids) {
      if (id == null || id.isBlank()) continue;
      for (MenuItemSpec item : menuScreen.items()) {
        if (item != null && id.equalsIgnoreCase(item.id())) {
          if (item.styleId() != null && !item.styleId().isBlank()) {
            return menuProfile.style(item.styleId());
          }
        }
      }
    }
    return null;
  }

  private MenuActionSpec actionForItemId(String... ids) {
    if (ids == null || ids.length == 0) return null;
    for (String id : ids) {
      if (id == null || id.isBlank()) continue;
      for (MenuItemSpec item : menuScreen.items()) {
        if (item != null && id.equalsIgnoreCase(item.id())) {
          return item.action();
        }
      }
    }
    return null;
  }

  private MenuItemSpec itemForItemId(String... ids) {
    if (ids == null || ids.length == 0) return null;
    for (String id : ids) {
      if (id == null || id.isBlank()) continue;
      for (MenuItemSpec item : menuScreen.items()) {
        if (item != null && id.equalsIgnoreCase(item.id())) {
          return item;
        }
      }
    }
    return null;
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

  private static String normalizeScriptName(String scriptName) {
    return VnEntryScriptResolver.normalizeScriptKey(scriptName);
  }

  private Path favoritesFilePath() {
    try {
      String dir = saveManager != null ? saveManager.getSaveDirectory() : null;
      if (dir == null || dir.isBlank()) return null;
      return Paths.get(dir).resolve(FAVORITES_FILE_NAME);
    } catch (Exception e) {
      return null;
    }
  }

  private void loadFavoritesFromDisk() {
    favorites.clear();
    Path path = favoritesFilePath();
    if (path == null || !Files.exists(path)) return;
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(path)) {
      p.load(new InputStreamReader(in));
      String raw = p.getProperty(FAVORITES_PROPERTY, "");
      if (raw == null || raw.isBlank()) return;
      String[] parts = raw.split(",");
      for (String part : parts) {
        String name = sanitizeSaveName(part);
        if (name != null) favorites.add(name);
      }
    } catch (Exception e) {
      LOG.debug("Could not load load-menu favorites from {}: {}", path, e.toString());
    }
  }

  private void saveFavoritesToDisk() {
    Path path = favoritesFilePath();
    if (path == null) return;
    try {
      if (path.getParent() != null) Files.createDirectories(path.getParent());
      List<String> ordered = new ArrayList<>(favorites);
      ordered.sort(String::compareToIgnoreCase);
      String joined = String.join(",", ordered);
      Properties p = new Properties();
      p.setProperty(FAVORITES_PROPERTY, joined);
      try (var out = Files.newOutputStream(path)) {
        p.store(out, "Load menu favorites");
      }
    } catch (Exception e) {
      LOG.debug("Could not save load-menu favorites to {}: {}", path, e.toString());
    }
  }

  private String sanitizeSaveName(String name) {
    if (name == null) return null;
    String t = name.trim();
    if (t.isEmpty()) return null;
    String sanitized = t.replaceAll("[^a-zA-Z0-9._-]", "_");
    return sanitized.isBlank() ? null : sanitized;
  }
}
