package com.jvn.core.menu;

import com.jvn.core.engine.Engine;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnHistory;
import com.jvn.core.vn.VnScene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class HistoryMenuScene implements Scene {
  private static final Logger LOG = LoggerFactory.getLogger(HistoryMenuScene.class);

  private final Engine engine;
  private final VnScene vnScene;
  private final MenuProfile menuProfile;
  private final MenuScreenSpec menuScreen;
  private final MenuLayoutSpec menuLayout;

  public HistoryMenuScene(Engine engine, VnScene vnScene) {
    this(engine, vnScene, null);
  }

  public HistoryMenuScene(Engine engine, VnScene vnScene, MenuProfile profile) {
    this.engine = engine;
    this.vnScene = vnScene;
    if (profile == null) {
      MenuProfileLoader.LoadResult menuLoad = MenuProfileLoader.loadWithDiagnostics();
      this.menuProfile = menuLoad.profile();
      for (String warning : menuLoad.diagnostics()) {
        LOG.warn("Menu profile: {}", warning);
      }
    } else {
      this.menuProfile = profile;
    }
    this.menuScreen = menuProfile.screen("history");
    this.menuLayout = menuProfile.layout(menuScreen.layoutId());
  }

  public VnScene getVnScene() {
    return vnScene;
  }

  public MenuLayoutSpec getMenuLayout() {
    return menuLayout;
  }

  public MenuScreenSpec getMenuScreen() {
    return menuScreen;
  }

  public MenuStyleSpec getDefaultMenuStyle() {
    return menuProfile.style(menuScreen.defaultStyleId());
  }

  public MenuStyleSpec getEntryStyle() {
    for (MenuItemSpec item : menuScreen.items()) {
      if (item != null && "history_entry".equalsIgnoreCase(item.id())) {
        String styleId = item.styleId() == null || item.styleId().isBlank()
            ? menuScreen.defaultStyleId()
            : item.styleId();
        return menuProfile.style(styleId);
      }
    }
    return getDefaultMenuStyle();
  }

  public String getDisplayTitle() {
    String title = resolveDisplayText(menuScreen.titleText());
    return title != null ? title : Localization.t("history.title");
  }

  public String getDisplaySubtitle() {
    return resolveDisplayText(menuScreen.subtitleText());
  }

  public String getDisplayHints() {
    String hints = resolveDisplayText(menuScreen.hintsText());
    return hints != null ? hints : Localization.t("history.hint");
  }

  public List<VnHistory.HistoryEntry> getEntries() {
    if (vnScene == null || vnScene.getState() == null || vnScene.getState().getHistory() == null) {
      return List.of();
    }
    return vnScene.getState().getHistory().getEntries();
  }

  public int getScrollOffset() {
    if (vnScene == null || vnScene.getState() == null) return 0;
    return vnScene.getState().getHistoryScroll();
  }

  public void clearScroll() {
    if (vnScene == null || vnScene.getState() == null) return;
    vnScene.getState().clearHistoryScroll();
  }

  public void scrollByLines(int delta) {
    if (vnScene == null || vnScene.getState() == null) return;
    vnScene.getState().scrollHistoryByLines(delta);
  }

  public void close() {
    if (engine != null) {
      engine.scenes().pop();
    }
  }

  public int linesPerPage(double height) {
    double top = resolveCoordinate(menuLayout.listYStart(), height);
    double titleY = menuLayout.titleY() != null ? resolveCoordinate(menuLayout.titleY(), height) : 0.0;
    double lineHeight = menuLayout.lineHeight() > 0 ? menuLayout.lineHeight() : 34.0;
    double contentTop = Math.max(top, titleY + lineHeight * 1.4);
    double contentBottom = Math.max(contentTop + lineHeight, height - Math.max(0.0, menuLayout.hintsBottomMargin()) - 26.0);
    double available = Math.max(lineHeight, contentBottom - contentTop);
    if (menuLayout.maxVisibleItems() != null) {
      return Math.max(1, menuLayout.maxVisibleItems());
    }
    return Math.max(1, (int) Math.floor(available / lineHeight));
  }

  @Override
  public void update(long deltaMs) {
  }

  private static String resolveDisplayText(String raw) {
    if (raw == null) return null;
    String value = raw.trim();
    if (value.isEmpty()) return "";
    return Localization.translateText(raw);
  }

  private static double resolveCoordinate(double value, double total) {
    return value <= 1.0 ? total * value : value;
  }
}
