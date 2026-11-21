package com.jvn.core.menu;

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
import com.jvn.core.vn.VnBackground;
import com.jvn.core.vn.VnEntryScriptResolver;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioLoader;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.save.VnSaveManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Save menu for creating/overwriting/deleting/renaming save slots.
 * Requires an active VnScene to save state.
 */
public class SaveMenuScene implements Scene {
  private static final Logger LOG = LoggerFactory.getLogger(SaveMenuScene.class);
  private final Engine engine;
  private final VnSaveManager saveManager;
  private final VnScene currentVnScene;
  private final String defaultScriptName;
  private final VnSettings settingsModel;
  private final MenuProfile menuProfile;
  private final MenuScreenSpec menuScreen;
  private final MenuLayoutSpec menuLayout;
  private final VnScenarioLoader scenarioLoader = new VnScenarioLoader();
  private int selected = 0;
  private List<String> saves = new ArrayList<>();

  public SaveMenuScene(Engine engine, VnSaveManager saveManager, VnScene vnScene) {
    this(engine, saveManager, vnScene, null, null);
  }

  public SaveMenuScene(Engine engine, VnSaveManager saveManager, VnScene vnScene, String defaultScriptName) {
    this(engine, saveManager, vnScene, defaultScriptName, null);
  }

  SaveMenuScene(
      Engine engine,
      VnSaveManager saveManager,
      VnScene vnScene,
      String defaultScriptName,
      MenuProfile profile
  ) {
    this.engine = engine;
    this.saveManager = saveManager == null ? new VnSaveManager() : saveManager;
    this.currentVnScene = vnScene;
    String resolvedDefault = VnEntryScriptResolver.resolveEntryScript(defaultScriptName, null);
    this.defaultScriptName = normalize(resolvedDefault, "story/prologue.vns");
    this.settingsModel = (vnScene != null && vnScene.getState() != null && vnScene.getState().getSettings() != null)
        ? vnScene.getState().getSettings()
        : new VnSettings();
    if (profile == null) {
      MenuProfileLoader.LoadResult menuLoad = MenuProfileLoader.loadWithDiagnostics();
      this.menuProfile = menuLoad.profile();
      for (String warning : menuLoad.diagnostics()) {
        LOG.warn("Menu profile: {}", warning);
      }
    } else {
      this.menuProfile = profile;
    }
    this.menuScreen = menuProfile.screen("save");
    this.menuLayout = menuProfile.layout(menuScreen.layoutId());
    refresh();
  }

  public void refresh() {
    List<String> list = new ArrayList<>(saveManager.listSaves());
    List<String> filtered = new ArrayList<>();
    String activeScriptName = resolveActiveScriptName();
    String activeScenarioId = resolveActiveScenarioId();
    boolean filterByScene = !saveManager.isGameScoped() && (activeScriptName != null || activeScenarioId != null);
    try {
      var times = new HashMap<String, Long>();
      for (String n : list) {
        try {
          var data = saveManager.load(n);
          times.put(n, data.getSaveTimestamp());
          if (!filterByScene || matchesCurrentScope(data, activeScriptName, activeScenarioId)) {
            filtered.add(n);
          }
        } catch (Exception e) {
          times.put(n, 0L);
        }
      }
      filtered.sort(Comparator.comparing((String n) -> times.getOrDefault(n, 0L)).reversed());
    } catch (Exception e) {
    }
    this.saves = filtered;
    if (selected >= getEntriesCount()) selected = getEntriesCount() - 1;
    if (selected < 0) selected = 0;
  }

  public List<String> getSaves() { return saves; }
  public int getSelected() { return selected; }
  public VnSaveManager getSaveManager() { return saveManager; }
  public String getDefaultScriptName() { return defaultScriptName; }
  public MenuLayoutSpec getMenuLayout() { return menuLayout; }
  public MenuScreenSpec getMenuScreen() { return menuScreen; }
  public MenuStyleSpec getDefaultMenuStyle() { return menuProfile.style(menuScreen.defaultStyleId()); }
  public int getItemCount() { return getEntriesCount(); }
  public boolean wrapsSelection() { return menuScreen.wrapSelection(); }

  public MenuStyleSpec getStyleForIndex(int idx) {
    String styleId = menuScreen.defaultStyleId();
    if (idx == 0) {
      MenuStyleSpec s = styleForItemId("new_save", "new_slot", "new");
      if (s != null) return s;
    } else {
      MenuStyleSpec s = styleForItemId("save_slot", "slot", "entry");
      if (s != null) return s;
    }
    if (idx >= 0 && idx < menuScreen.items().size()) {
      var item = menuScreen.items().get(idx);
      if (item != null && item.styleId() != null && !item.styleId().isBlank()) styleId = item.styleId();
    }
    return menuProfile.style(styleId);
  }

  public String getDisplayTitle() {
    String t = resolveDisplayText(menuScreen.titleText());
    return t != null ? t : Localization.t("save.title");
  }

  public String getDisplaySubtitle() {
    return resolveDisplayText(menuScreen.subtitleText());
  }

  public String getDisplayHints() {
    String t = resolveDisplayText(menuScreen.hintsText());
    if (t == null) {
      return Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc    "
          + Localization.t("save.delete") + ": Delete    " + Localization.t("save.rename") + ": R";
    }
    return t;
  }

  public String getNewSlotLabel() {
    String custom = labelForItemId("new_save", "new_slot", "new");
    return (custom == null || custom.isBlank()) ? Localization.t("save.new") : custom;
  }

  public MenuActionSpec getSelectedAction() {
    MenuActionSpec action;
    if (isNewItemSelected()) {
      action = actionForItemId("new_save", "new_slot", "new");
    } else {
      action = actionForItemId("save_slot", "slot", "entry");
    }
    return action != null ? action : new MenuActionSpec(MenuActionType.SAVE_MENU, null);
  }

  public MenuItemSpec getMenuItemSpec(int idx) {
    if (idx < 0) return null;
    if (idx == 0) {
      MenuItemSpec newSlot = itemForItemId("new_save", "new_slot", "new");
      if (newSlot != null) return newSlot;
    } else {
      MenuItemSpec saveSlot = itemForItemId("save_slot", "slot", "entry");
      if (saveSlot != null) return saveSlot;
    }
    if (idx < menuScreen.items().size()) {
      return menuScreen.items().get(idx);
    }
    return null;
  }

  public boolean activateSelectedWithoutPrompt() {
    MenuActionSpec action = getSelectedAction();
    if (handleCustomMenuAction(action)) {
      return true;
    }
    return switch (action.type()) {
      case NEW_GAME -> {
        startNewGame(defaultScriptName);
        yield true;
      }
      case RUN_SCRIPT -> {
        startNewGame(normalize(action.target(), defaultScriptName));
        yield true;
      }
      case LOAD_MENU -> {
        if (engine != null) {
          String script = normalize(action.target(), defaultScriptName);
          engine.scenes().push(new LoadMenuScene(engine, saveManager, script, settingsModel, currentAudio()));
        }
        yield true;
      }
      case SETTINGS_MENU -> {
        if (engine != null) {
          com.jvn.core.input.ActionBindingProfile profile =
              com.jvn.core.input.ActionBindingProfile.deserialize(settingsModel.getInputProfileSerialized());
          String targetMenu = normalize(action.target(), "settings");
          engine.scenes().push(new SettingsScene(
              engine,
              saveManager,
              defaultScriptName,
              settingsModel,
              currentAudio(),
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
      case SAVE_MENU -> false;
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
    String sourceItemId = selectedItem != null ? normalize(selectedItem.id(), "") : "";
    try {
      return handler.handle(new MenuActionContext(
          engine,
          "save",
          sourceItemId,
          defaultScriptName,
          action
      ));
    } catch (Exception ex) {
      LOG.warn("Custom menu action '{}' failed in save menu", action.actionKey(), ex);
      return false;
    }
  }

  public void moveSelection(int delta) {
    int count = getEntriesCount();
    if (menuScreen.wrapSelection()) {
      selected = (selected + delta + count) % count;
    } else {
      selected = Math.max(0, Math.min(selected + delta, count - 1));
    }
  }
  public void setSelected(int idx) {
    int count = getEntriesCount();
    if (idx < 0) idx = 0;
    if (idx >= count) idx = count - 1;
    selected = idx;
  }
  public String getSaveDirectory() { return saveManager.getSaveDirectory(); }
  public VnScene getCurrentVnScene() { return currentVnScene; }

  public boolean isNewItemSelected() { return selected == 0; }
  public String getSelectedName() {
    if (isNewItemSelected()) return null;
    int index = selected - 1;
    if (index >= 0 && index < saves.size()) return saves.get(index);
    return null;
  }
  public int getEntriesCount() { return saves.size() + 1; } // +1 for "New Save..."

  public String generateSaveName() {
    java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss");
    return "Save " + java.time.LocalDateTime.now().format(fmt);
  }

  public String saveNew(String name) {
    if (name == null || name.isBlank()) return null;
    if (currentVnScene == null || currentVnScene.getState() == null) {
      LOG.warn("Cannot save: no active VN scene or state");
      return null;
    }
    String slot = saveManager.sanitizeSaveName(name);
    try {
      saveManager.save(currentVnScene.getState(), slot);
      refresh();
      if (engine != null) engine.scenes().pop();
      return slot;
    } catch (Exception e) {
      LOG.warn("Save failed for '{}': {}", name, e.toString());
      return null;
    }
  }

  public String saveOverwriteSelected() {
    String name = getSelectedName();
    if (name == null) return null;
    if (currentVnScene == null || currentVnScene.getState() == null) {
      LOG.warn("Cannot overwrite save: no active VN scene or state");
      return null;
    }
    String slot = saveManager.sanitizeSaveName(name);
    try {
      saveManager.save(currentVnScene.getState(), slot);
      refresh();
      if (engine != null) engine.scenes().pop();
      return slot;
    } catch (Exception e) {
      LOG.warn("Overwrite save failed for '{}': {}", name, e.toString());
      return null;
    }
  }

  public boolean deleteSelected() {
    String name = getSelectedName();
    if (name == null) return false;
    boolean ok = saveManager.deleteSave(name);
    refresh();
    return ok;
  }

  public boolean renameSelected(String newName) {
    String old = getSelectedName();
    if (old == null || newName == null || newName.isBlank()) return false;
    boolean ok = saveManager.renameSave(old, newName);
    refresh();
    return ok;
  }

  public String getCurrentBackgroundPreviewPath() {
    try {
      var state = currentVnScene.getState();
      String bgId = state.getCurrentBackgroundId();
      if (bgId == null) return null;
      VnBackground bg = state.getScenario().getBackground(bgId);
      return bg != null ? bg.getImagePath() : null;
    } catch (Exception ignored) {
      // reason: background preview is display-only; unreadable scene state yields null
      return null;
    }
  }

  public Long getSelectedTimestamp() {
    String name = getSelectedName();
    if (name == null) return null;
    try {
      return saveManager.load(name).getSaveTimestamp();
    } catch (Exception ignored) {
      // reason: timestamp is display-only; unreadable save slot yields null
      return null;
    }
  }

  public String getCurrentRpgSummary() {
    try {
      Object rpg = currentVnScene.getState().getRpgState();
      return summarizeRpgState(rpg);
    } catch (Exception e) { return null; }
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

  @Override public void onEnter() { }
  @Override public void update(long deltaMs) { }
  @Override public void onExit() { }

  private void openConfiguredMenu(String targetMenu) {
    String requested = normalize(targetMenu, null);
    if (requested == null || engine == null) return;
    if (!menuProfile.screens().containsKey(requested)) {
      LOG.debug("Configured menu '{}' not found in profile", requested);
      return;
    }
    MainMenuScene child = new MainMenuScene(engine, settingsModel, saveManager, defaultScriptName, currentAudio(), requested);
    engine.scenes().push(child);
  }

  private boolean openQuitConfirmationMenu(String targetMenu) {
    String requested = normalize(targetMenu, null);
    if (requested == null && menuProfile.screens().containsKey("confirm_exit")) {
      requested = "confirm_exit";
    }
    if (requested == null || requested.isBlank() || "save".equalsIgnoreCase(requested)) return false;
    if (!menuProfile.screens().containsKey(requested) || engine == null) return false;
    MainMenuScene child = new MainMenuScene(engine, settingsModel, saveManager, defaultScriptName, currentAudio(), requested);
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
    if (currentAudio() != null) scene.setAudioFacade(currentAudio());
    if (engine != null && engine.getVnInteropFactory() != null) {
      scene.setInterop(engine.getVnInteropFactory().create(engine));
    }
    VnSettings s = scene.getState().getSettings();
    s.copyFrom(settingsModel);
    if (currentAudio() != null) {
      currentAudio().setBgmVolume(s.getBgmVolume());
      currentAudio().setSfxVolume(s.getSfxVolume());
      currentAudio().setVoiceVolume(s.getVoiceVolume());
    }
    if (engine != null) {
      engine.setFixedUpdateStepMs(settingsModel.getPhysicsFixedStepMs(), settingsModel.getPhysicsMaxSubSteps());
      MenuAudioLifecycle.beginGameplay(currentAudio());
      engine.scenes().push(scene);
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

  private com.jvn.core.audio.AudioFacade currentAudio() {
    return currentVnScene != null ? currentVnScene.getAudioFacade() : null;
  }

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

  private String labelForItemId(String... ids) {
    if (ids == null || ids.length == 0) return null;
    for (String id : ids) {
      if (id == null || id.isBlank()) continue;
      for (MenuItemSpec item : menuScreen.items()) {
        if (item != null && id.equalsIgnoreCase(item.id())) {
          String label = resolveDisplayText(item.label());
          if (label != null && !label.isBlank()) return label;
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

  private boolean matchesCurrentScope(com.jvn.core.vn.save.VnSaveData data, String scriptName, String scenarioId) {
    if (data == null) return false;
    String saveScriptName = normalizeScriptName(data.getScriptName());
    String saveScenarioId = normalize(data.getScenarioId(), null);
    boolean scriptMatch = saveScriptName != null && scriptName != null && saveScriptName.equals(scriptName);
    boolean scenarioMatch = saveScenarioId != null && scenarioId != null && saveScenarioId.equals(scenarioId);
    return scriptMatch || scenarioMatch;
  }

  private String resolveActiveScriptName() {
    if (currentVnScene != null && currentVnScene.getState() != null) {
      String script = normalizeScriptName(currentVnScene.getState().getSourceScriptName());
      if (script != null) return script;
    }
    return normalizeScriptName(defaultScriptName);
  }

  private String resolveActiveScenarioId() {
    if (currentVnScene == null || currentVnScene.getState() == null || currentVnScene.getState().getScenario() == null) {
      return null;
    }
    return normalize(currentVnScene.getState().getScenario().getId(), null);
  }

  private static String normalizeScriptName(String scriptName) {
    return VnEntryScriptResolver.normalizeScriptKey(scriptName);
  }
}
