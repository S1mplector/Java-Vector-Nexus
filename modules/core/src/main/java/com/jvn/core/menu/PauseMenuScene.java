package com.jvn.core.menu;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.audio.AudioFacade;
import com.jvn.core.engine.Engine;
import com.jvn.core.input.ActionBindingProfile;
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
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.save.VnSaveManager;

/**
 * In-game pause menu that overlays the active VN scene.
 * Provides Resume, Save, Load, Settings, and Main Menu actions.
 */
public class PauseMenuScene implements Scene {
  private static final Logger LOG = LoggerFactory.getLogger(PauseMenuScene.class);

  private final Engine engine;
  private final VnScene vnScene;
  private final VnSaveManager saveManager;
  private final String defaultScriptName;
  private final AudioFacade audio;
  private final MenuProfile menuProfile;
  private final MenuScreenSpec menuScreen;
  private final MenuLayoutSpec menuLayout;
  private int selected = 0;

  public PauseMenuScene(Engine engine, VnScene vnScene, VnSaveManager saveManager,
                        String defaultScriptName, AudioFacade audio) {
    this(engine, vnScene, saveManager, defaultScriptName, audio, null);
  }

  public PauseMenuScene(Engine engine, VnScene vnScene, VnSaveManager saveManager,
                        String defaultScriptName, AudioFacade audio, MenuProfile profile) {
    this.engine = engine;
    this.vnScene = vnScene;
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
    this.menuScreen = menuProfile.screen("pause");
    this.menuLayout = menuProfile.layout(menuScreen.layoutId());
    this.selected = firstSelectableIndex(0);
  }

  public VnScene getVnScene() { return vnScene; }
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
    int steps = Math.abs(delta);
    int dir = delta > 0 ? 1 : -1;
    int next = selected;
    for (int i = 0; i < steps; i++) {
      next = nextSelectable(next, dir);
    }
    selected = next;
  }

  public void setSelected(int idx) {
    int count = getItemCount();
    if (count <= 0) { selected = 0; return; }
    int clamped = Math.max(0, Math.min(idx, count - 1));
    if (isItemEnabled(clamped)) { selected = clamped; return; }
    selected = firstSelectableIndex(clamped);
  }

  public void activateSelected() {
    MenuItemSpec item = getItem(selected);
    if (item == null || !item.enabled()) return;
    MenuActionSpec action = item.action();
    if (handleCustomMenuAction(action, item.id())) return;
    switch (action.type()) {
      case BACK -> {
        // Resume: just pop the pause menu
        if (engine != null) engine.scenes().pop();
      }
      case SAVE_MENU -> {
        if (engine != null && vnScene != null) {
          engine.scenes().push(new SaveMenuScene(engine, saveManager, vnScene));
        }
      }
      case LOAD_MENU -> {
        if (engine != null) {
          VnSettings s = vnScene != null ? vnScene.getState().getSettings() : new VnSettings();
          engine.scenes().push(
              new LoadMenuScene(engine, saveManager, defaultScriptName, s, audio)
                  .withGameplayVnScene(vnScene));
        }
      }
      case SETTINGS_MENU -> {
        if (engine != null) {
          VnSettings s = vnScene != null ? vnScene.getState().getSettings() : new VnSettings();
          ActionBindingProfile profile = ActionBindingProfile.deserialize(s.getInputProfileSerialized());
          String targetMenu = normalize(action.target(), "settings");
          engine.scenes().push(new SettingsScene(
              engine,
              saveManager,
              defaultScriptName,
              s,
              audio,
              profile,
              menuProfile,
              targetMenu
          ).withGameplayVnScene(vnScene));
        }
      }
      case HISTORY_MENU -> {
        if (engine != null && vnScene != null) {
          vnScene.getState().clearHistoryScroll();
          engine.scenes().push(new HistoryMenuScene(engine, vnScene));
        }
      }
      case TOGGLE_SKIP -> {
        if (vnScene != null) vnScene.toggleSkipMode();
        if (engine != null) engine.scenes().pop();
      }
      case TOGGLE_AUTO -> {
        if (vnScene != null) vnScene.toggleAutoPlayMode();
        if (engine != null) engine.scenes().pop();
      }
      case OPEN_MENU -> openConfiguredMenu(action.target());
      case MAIN_MENU -> {
        if (engine != null) {
          // Pop pause menu + VN scene, then push main menu
          engine.scenes().pop(); // pop pause
          engine.scenes().pop(); // pop VN scene
          VnSettings s = vnScene != null ? vnScene.getState().getSettings() : new VnSettings();
          MainMenuScene main = new MainMenuScene(engine, s, saveManager, defaultScriptName, audio);
          engine.scenes().push(main);
        }
      }
      case QUIT -> {
        if (!openQuitConfirmationMenu(action.target()) && engine != null) {
          engine.stop();
        }
      }
      case NOOP -> { }
      default -> LOG.debug("Unhandled pause menu action: {}", action.type());
    }
  }

  private boolean handleCustomMenuAction(MenuActionSpec action, String itemId) {
    if (action == null || !action.isCustomAction() || engine == null) return false;
    String key = normalize(action.actionKey(), "").toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    switch (key) {
      case "history", "toggle_history" -> {
        if (vnScene != null) {
          vnScene.getState().clearHistoryScroll();
          engine.scenes().push(new HistoryMenuScene(engine, vnScene));
        }
        return true;
      }
      case "skip", "toggle_skip" -> {
        if (vnScene != null) vnScene.toggleSkipMode();
        engine.scenes().pop();
        return true;
      }
      case "auto", "toggle_auto" -> {
        if (vnScene != null) vnScene.toggleAutoPlayMode();
        engine.scenes().pop();
        return true;
      }
      default -> { }
    }
    var handler = engine.getMenuActionHandler();
    if (handler == null) return false;
    try {
      return handler.handle(new MenuActionContext(
          engine, "pause", normalize(itemId, ""), defaultScriptName, action
      ));
    } catch (Exception ex) {
      LOG.warn("Custom menu action '{}' failed in pause menu", action.actionKey(), ex);
      return false;
    }
  }

  private void openConfiguredMenu(String targetMenu) {
    String target = normalize(targetMenu, null);
    if (target == null || !menuProfile.screens().containsKey(target) || engine == null) return;
    VnSettings settings = vnScene != null ? vnScene.getState().getSettings() : new VnSettings();
    ActionBindingProfile bindings =
        ActionBindingProfile.deserialize(settings.getInputProfileSerialized());
    engine.scenes().push(new SettingsScene(
        engine,
        saveManager,
        defaultScriptName,
        settings,
        audio,
        bindings,
        menuProfile,
        target
    ).withGameplayVnScene(vnScene));
  }

  private boolean openQuitConfirmationMenu(String targetMenu) {
    String requested = normalize(targetMenu, null);
    if (requested == null && menuProfile.screens().containsKey("confirm_exit")) {
      requested = "confirm_exit";
    }
    if (requested == null || requested.isBlank() || "pause".equalsIgnoreCase(requested)) return false;
    if (!menuProfile.screens().containsKey(requested) || engine == null) return false;
    VnSettings model = vnScene != null ? vnScene.getState().getSettings() : new VnSettings();
    MainMenuScene child = new MainMenuScene(engine, model, saveManager, defaultScriptName, audio, requested);
    engine.scenes().push(child);
    return true;
  }

  @Override
  public void update(long deltaMs) { }

  @Override
  public void onEnter() { }

  @Override
  public void onExit() { }

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

  private String displayLabel(MenuItemSpec item) {
    if (item == null) return "";
    String explicit = resolveDisplayText(item.label());
    if (explicit != null) return explicit;
    return switch (item.action().type()) {
      case BACK -> Localization.t("common.back");
      case SAVE_MENU -> Localization.t("save.title");
      case LOAD_MENU -> Localization.t("menu.load");
      case SETTINGS_MENU -> Localization.t("menu.settings");
      case MAIN_MENU -> Localization.t("app.title");
      case QUIT -> Localization.t("menu.quit");
      default -> titleize(item.id());
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
      if (Character.isWhitespace(c)) { upper = true; out.append(c); }
      else if (upper) { out.append(Character.toUpperCase(c)); upper = false; }
      else out.append(c);
    }
    return out.toString();
  }
}
