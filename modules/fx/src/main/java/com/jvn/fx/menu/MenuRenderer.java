package com.jvn.fx.menu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.jvn.fx.RenderThreadGuard;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.BoundedImageCache;
import com.jvn.fx.FxImageMemory;
import com.jvn.core.assets.AssetType;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.HistoryMenuScene;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.PauseMenuScene;
import com.jvn.core.menu.SaveMenuScene;
import com.jvn.core.menu.SettingsScene;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.menu.gallery.GalleryEntry;
import com.jvn.core.menu.gallery.GalleryScene;
import com.jvn.core.menu.gallery.MusicRoomEntry;
import com.jvn.core.menu.gallery.MusicRoomScene;
import com.jvn.core.ui.BoundsPointCodec;
import com.jvn.fx.ui.FxTextMetrics;
import com.jvn.fx.ui.ProjectFontResolver;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class MenuRenderer {
  private static final Logger log = LoggerFactory.getLogger(MenuRenderer.class);

  private static final double SUBMENU_BACKGROUND_BLUR_RADIUS = 14.0;
  private static final Color SUBMENU_FROST_TINT = Color.rgb(224, 236, 255, 0.28);
  private static final String LOAD_CYCLE_LEFT_ACTIVE_ASSET = "assets/ui/load/controls/page_turn_left_active.png";
  private static final String LOAD_CYCLE_LEFT_INACTIVE_ASSET = "assets/ui/load/controls/page_turn_left_inactive.png";
  private static final String LOAD_CYCLE_RIGHT_ACTIVE_ASSET = "assets/ui/load/controls/page_turn_right_active.png";
  private static final String LOAD_CYCLE_RIGHT_INACTIVE_ASSET = "assets/ui/load/controls/page_turn_right_inactive.png";
  private static final String LOAD_PAGE_TRACK_ASSET = "assets/ui/load/controls/page_track.png";
  private static final String LOAD_PAGE_SELECTOR_ASSET = "assets/ui/load/controls/page_selector.png";
  private static final String LOAD_FAVORITES_BUTTON_ACTIVE_ASSET = "assets/ui/load/controls/favorites_button_active.png";
  private static final String LOAD_FAVORITES_BUTTON_INACTIVE_ASSET = "assets/ui/load/controls/favorites_button_inactive.png";
  private static final String LOAD_SLOT_FAVORITE_ICON_ASSET = "assets/ui/load/controls/slot_favorite_icon.png";

  private final GraphicsContext gc;
  private final DockMenuPresentation dock = new DockMenuPresentation();
  private MenuTheme theme;
  private static final long IMAGE_CACHE_BUDGET_BYTES = 48L * 1024L * 1024L;
  private final BoundedImageCache<Image> imageCache =
      new BoundedImageCache<>(128, IMAGE_CACHE_BUDGET_BYTES, FxImageMemory::estimatedBytes);
  private final FxTextMetrics textMetrics = new FxTextMetrics();
  private File projectRoot;
  private double activeUiFontScale = 1.0;

  public enum LoadControlType {
    NONE,
    CYCLE_LEFT,
    CYCLE_RIGHT,
    TOGGLE_FAVORITES_ONLY,
    TOGGLE_SLOT_FAVORITE,
    SET_PAGE
  }

  public record LoadControlHit(LoadControlType type, int saveIndex, double pageProgress01) {
    public static LoadControlHit none() { return new LoadControlHit(LoadControlType.NONE, -1, 0.0); }
    public boolean handled() { return type != null && type != LoadControlType.NONE; }
  }

  public MenuRenderer(GraphicsContext gc) { this.gc = gc; this.theme = MenuTheme.defaults(); }
  public MenuRenderer(GraphicsContext gc, MenuTheme theme) { this.gc = gc; this.theme = (theme == null ? MenuTheme.defaults() : theme); }
  public void setTheme(MenuTheme t) {
    this.theme = (t == null ? MenuTheme.defaults() : t);
    textMetrics.clear();
    dock.clear();
  }
  public MenuTheme getTheme() { return theme; }
  public void setProjectRoot(File root) {
    if (!java.util.Objects.equals(projectRoot, root)) imageCache.clear();
    this.projectRoot = root;
    textMetrics.clear();
    dock.clear();
  }
  public void setUiFontScale(double scale) {
    if (!Double.isFinite(scale)) scale = 1.0;
    activeUiFontScale = Math.max(0.75, Math.min(2.0, scale));
    textMetrics.clear();
    dock.clear();
  }

  public void renderMainMenu(MainMenuScene scene, double w, double h) {
    RenderThreadGuard.requireFxThread("MenuRenderer.renderMainMenu");
    MenuLayoutSpec layout = scene != null ? scene.getMenuLayout() : null;
    MenuStyleSpec screenStyle = scene != null ? scene.getDefaultMenuStyle() : null;
    String screenBg = scene != null && scene.getMenuScreen() != null ? scene.getMenuScreen().backgroundAsset() : null;
    drawScreenBackground(w, h, screenStyle, true, screenBg);

    if (usesDock(scene)) {
      dock.render(gc, scene, theme, projectRoot, w, h, activeUiFontScale);
      return;
    }

    // Draw logo if configured, otherwise draw text title
    if (theme.getLogoImagePath() != null) {
      drawLogo(theme.getLogoImagePath(), w, h);
    } else {
      String title = scene != null ? scene.getDisplayTitle() : null;
      String subtitle = scene != null ? scene.getDisplaySubtitle() : null;
      if (title == null) title = theme.getTitleText();
      if (title == null) title = Localization.t("app.title");
      double titleY = (layout != null && layout.titleY() != null)
          ? resolve(layout.titleY(), h)
          : resolve(theme.getTitleY(), h);
      drawHeader(title, subtitle, w, titleY, screenStyle, layout);
    }

    String[] items;
    if (scene != null && scene.getItemCount() > 0) {
      items = scene.getDisplayItems();
    } else {
      items = new String[] {
        (theme.getLabelNewGame() != null ? theme.getLabelNewGame() : Localization.t("menu.new_game")),
        (theme.getLabelLoad() != null ? theme.getLabelLoad() : Localization.t("menu.load")),
        (theme.getLabelSettings() != null ? theme.getLabelSettings() : Localization.t("menu.settings")),
        (theme.getLabelQuit() != null ? theme.getLabelQuit() : Localization.t("menu.quit"))
      };
    }

    boolean[] enabled = new boolean[items.length];
    MenuStyleSpec[] styles = new MenuStyleSpec[items.length];
    MenuItemSpec[] specs = new MenuItemSpec[items.length];
    for (int i = 0; i < items.length; i++) {
      enabled[i] = scene == null || scene.isItemEnabled(i);
      styles[i] = scene != null ? scene.getStyleForIndex(i) : null;
      specs[i] = scene != null ? scene.getMenuItemSpec(i) : null;
    }

    drawMenuList(items, scene != null ? scene.getSelected() : 0, enabled, styles, specs, layout, 0, w, h);

    String hints = scene != null ? scene.getDisplayHints() : null;
    if (hints == null) hints = theme.getMainHintsText();
    if (hints == null) {
      hints = Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc";
    }
    double bottomMargin = layout != null ? layout.hintsBottomMargin() : 20.0;
    drawHints(hints, w, h, bottomMargin, screenStyle, layout);
  }

  public void renderPauseMenu(PauseMenuScene scene, double w, double h) {
    MenuLayoutSpec layout = scene != null ? scene.getMenuLayout() : null;
    MenuStyleSpec screenStyle = scene != null ? scene.getDefaultMenuStyle() : null;
    drawGameplayMenuBackdrop(w, h);

    String title = scene != null ? scene.getDisplayTitle() : "Paused";
    String subtitle = scene != null ? scene.getDisplaySubtitle() : null;
    double titleY = (layout != null && layout.titleY() != null)
        ? resolve(layout.titleY(), h)
        : resolve(theme.getTitleY(), h);
    drawHeader(title, subtitle, w, titleY, screenStyle, layout);

    String[] items = scene != null ? scene.getDisplayItems() : new String[]{"Resume"};
    boolean[] enabled = new boolean[items.length];
    MenuStyleSpec[] styles = new MenuStyleSpec[items.length];
    MenuItemSpec[] specs = new MenuItemSpec[items.length];
    for (int i = 0; i < items.length; i++) {
      enabled[i] = scene == null || scene.isItemEnabled(i);
      styles[i] = scene != null ? scene.getStyleForIndex(i) : null;
      specs[i] = scene != null ? scene.getMenuItemSpec(i) : null;
    }

    drawMenuList(items, scene != null ? scene.getSelected() : 0, enabled, styles, specs, layout, 0, w, h);

    String hints = scene != null ? scene.getDisplayHints() : null;
    if (hints == null) hints = "Esc: Resume";
    double bottomMargin = layout != null ? layout.hintsBottomMargin() : 20.0;
    drawHints(hints, w, h, bottomMargin, screenStyle, layout);
  }

  public void renderSaveMenu(SaveMenuScene scene, double w, double h) {
    MenuLayoutSpec layout = scene != null ? scene.getMenuLayout() : null;
    MenuStyleSpec screenStyle = scene != null ? scene.getDefaultMenuStyle() : null;
    String screenBg = scene != null && scene.getMenuScreen() != null ? scene.getMenuScreen().backgroundAsset() : null;
    if (scene != null && scene.getCurrentVnScene() != null) drawGameplayMenuBackdrop(w, h);
    else drawScreenBackground(w, h, screenStyle, false, screenBg);
    String title = scene != null ? scene.getDisplayTitle() : Localization.t("save.title");
    String subtitle = scene != null ? scene.getDisplaySubtitle() : null;
    double titleY = (layout != null && layout.titleY() != null) ? resolve(layout.titleY(), h) : 60.0;
    drawHeader(title, subtitle, w, titleY, screenStyle, layout);
    List<String> saves = scene.getSaves();
    String[] items = new String[(saves.size() + 1)];
    items[0] = scene.getNewSlotLabel();
    for (int i = 0; i < saves.size(); i++) items[i + 1] = saves.get(i);
    boolean[] enabled = new boolean[items.length];
    MenuStyleSpec[] styles = new MenuStyleSpec[items.length];
    MenuItemSpec[] specs = new MenuItemSpec[items.length];
    for (int i = 0; i < items.length; i++) {
      enabled[i] = true;
      styles[i] = scene.getStyleForIndex(i);
      specs[i] = scene.getMenuItemSpec(i);
    }
    boolean showSidePreview = shouldShowSaveSidePreview(scene, specs);
    double listAreaWidth = showSidePreview ? w * 0.6 : w;
    drawMenuList(items, scene.getSelected(), enabled, styles, specs, layout, 0, listAreaWidth, h, true);
    drawInlineSaveSlotPreviews(scene, layout, specs, 0, listAreaWidth, h);

    if (showSidePreview) {
      // Preview: prefer thumbnail when selecting existing; when selecting new, try current background
      if (scene.isNewItemSelected()) {
        String path = scene.getCurrentBackgroundPreviewPath();
        if (path != null) drawPreviewResource(path, w, h); else drawPreviewPlaceholder(w, h);
      } else {
        File f = new File(scene.getSaveDirectory(), scene.getSelectedName() + ".png");
        if (f.exists()) drawPreviewFile(f, w, h); else drawPreviewPlaceholder(w, h);
        drawPreviewMetadata(null, scene.getSelectedTimestamp(), null, w, h);
      }
      String rpg = scene.getCurrentRpgSummary();
      if (rpg != null && !rpg.isBlank()) {
        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(theme.getHintFont());
        gc.fillText(rpg, 20, h - 50);
      }
    }
    String hints = scene != null ? scene.getDisplayHints() : null;
    if (hints == null) {
      hints = Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc    "
          + Localization.t("save.delete") + ": Delete    " + Localization.t("save.rename") + ": R";
    }
    drawHints(hints, w, h, layout != null ? layout.hintsBottomMargin() : 20.0, screenStyle, layout);
  }

  public void renderLoadMenu(LoadMenuScene scene, double w, double h) {
    MenuLayoutSpec layout = scene != null ? scene.getMenuLayout() : null;
    MenuStyleSpec screenStyle = scene != null ? scene.getDefaultMenuStyle() : null;
    String screenBg = scene != null && scene.getMenuScreen() != null ? scene.getMenuScreen().backgroundAsset() : null;
    if (scene != null && scene.getGameplayVnScene() != null) drawGameplayMenuBackdrop(w, h);
    else drawScreenBackground(w, h, screenStyle, false, screenBg);
    String title = scene != null ? scene.getDisplayTitle() : Localization.t("load.title");
    String subtitle = scene != null ? scene.getDisplaySubtitle() : null;
    double titleY = (layout != null && layout.titleY() != null) ? resolve(layout.titleY(), h) : 60.0;
    drawHeader(title, subtitle, w, titleY, screenStyle, layout);
    List<String> saves = scene.getSaves();
    int visibleStartIndex = 0;
    int visibleDrawCount = 0;
    MenuItemSpec[] visibleSpecs = null;
    double listAreaWidth = resolveLoadListAreaWidth(scene, null, w);
    if (saves.isEmpty()) {
      MenuItemSpec template = scene != null ? scene.getMenuItemSpec(0) : null;
      int emptySlots = parseItemExtraInt(template, "emptySlotCount", scene != null ? scene.getPageSize() : 0);
      int configuredVisible = parseItemExtraInt(template, "visibleSlotCount", 0);
      int drawSlots = Math.max(emptySlots, configuredVisible);
      if (drawSlots > 0 && scene != null) {
        String[] items = new String[drawSlots];
        boolean[] enabled = new boolean[drawSlots];
        MenuStyleSpec[] styles = new MenuStyleSpec[drawSlots];
        MenuItemSpec[] specs = new MenuItemSpec[drawSlots];
        for (int i = 0; i < drawSlots; i++) {
          items[i] = "";
          enabled[i] = false;
          styles[i] = scene.getStyleForIndex(i);
          MenuItemSpec candidate = scene.getMenuItemSpec(i);
          specs[i] = candidate != null ? candidate : template;
        }
        listAreaWidth = resolveLoadListAreaWidth(scene, specs, w);
        drawMenuList(items, -1, enabled, styles, specs, layout, 0, listAreaWidth, h, true);
        visibleDrawCount = drawSlots;
        visibleSpecs = specs;
      } else {
        drawCenteredText(Localization.t("load.no_saves"), w, h / 2, theme.getItemFont(), Color.GRAY);
      }
    } else {
      String[] items = saves.toArray(new String[0]);
      int startIndex = scene.getVisibleStartIndex();
      int dataVisibleCount = scene.getVisibleCount();
      dataVisibleCount = Math.max(0, Math.min(dataVisibleCount, items.length - startIndex));
      if (dataVisibleCount <= 0) {
        drawCenteredText(Localization.t("load.no_saves"), w, h / 2, theme.getItemFont(), Color.GRAY);
      } else {
        MenuItemSpec template = scene.getMenuItemSpec(startIndex);
        int configuredVisible = parseItemExtraInt(template, "visibleSlotCount", 0);
        boolean fillVisibleSlots = parseItemExtraBoolean(template, "fillVisibleSlots", configuredVisible > 0);
        int drawSlots = fillVisibleSlots
            ? Math.max(dataVisibleCount, configuredVisible > 0 ? configuredVisible : scene.getPageSize())
            : dataVisibleCount;
        drawSlots = Math.max(dataVisibleCount, drawSlots);
        String[] visibleItems = new String[drawSlots];
        boolean[] enabled = new boolean[drawSlots];
        MenuStyleSpec[] styles = new MenuStyleSpec[drawSlots];
        MenuItemSpec[] specs = new MenuItemSpec[drawSlots];
        for (int i = 0; i < drawSlots; i++) {
          int globalIndex = startIndex + i;
          boolean hasSave = globalIndex >= 0 && globalIndex < items.length;
          visibleItems[i] = hasSave ? items[globalIndex] : "";
          enabled[i] = hasSave;
          styles[i] = scene.getStyleForIndex(globalIndex);
          MenuItemSpec candidate = scene.getMenuItemSpec(globalIndex);
          specs[i] = candidate != null ? candidate : template;
        }
        boolean showSidePreview = shouldShowLoadSidePreview(scene, specs);
        listAreaWidth = showSidePreview ? w * 0.6 : w;
        int selectedGlobal = scene.getSelected();
        int localSelected = (selectedGlobal >= startIndex && selectedGlobal < startIndex + drawSlots)
            ? (selectedGlobal - startIndex)
            : -1;
        drawMenuList(visibleItems, localSelected, enabled, styles, specs, layout, 0, listAreaWidth, h, true);
        drawInlineLoadSlotPreviews(scene, layout, specs, startIndex, drawSlots, 0, listAreaWidth, h);
        visibleStartIndex = startIndex;
        visibleDrawCount = drawSlots;
        visibleSpecs = specs;
        if (showSidePreview) {
          File thumb = getThumbnailFile(scene);
          if (thumb != null) {
            drawPreviewFile(thumb, w, h);
          } else {
            String previewPath = scene.getSelectedPreviewImagePath();
            if (previewPath != null) {
              drawPreviewResource(previewPath, w, h);
            } else {
              drawPreviewPlaceholder(w, h);
            }
          }
          drawPreviewMetadata(
            scene.getSelectedScenarioId(),
            scene.getSelectedTimestamp(),
            scene.getSelectedNodeIndex(),
            w, h
          );
          String rpg = scene.getSelectedRpgSummary();
          if (rpg != null && !rpg.isBlank()) {
            gc.setFill(Color.LIGHTGRAY);
            gc.setFont(theme.getHintFont());
            gc.fillText(rpg, 20, h - 50);
          }
        }
      }
    }
    drawLoadMenuControls(scene, layout, visibleSpecs, visibleStartIndex, visibleDrawCount, w, h, listAreaWidth);
    String hints = scene != null ? scene.getDisplayHints() : null;
    if (hints == null) {
      hints = Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc    "
          + Localization.t("load.delete") + ": Delete    " + Localization.t("load.rename") + ": R";
    }
    drawHints(hints, w, h, layout != null ? layout.hintsBottomMargin() : 20.0, screenStyle, layout);
  }

  public void renderHistoryMenu(HistoryMenuScene scene, double w, double h) {
    MenuLayoutSpec layout = scene != null ? scene.getMenuLayout() : null;
    MenuStyleSpec screenStyle = scene != null ? scene.getDefaultMenuStyle() : null;
    MenuStyleSpec entryStyle = scene != null ? scene.getEntryStyle() : screenStyle;
    String screenBg = scene != null && scene.getMenuScreen() != null ? scene.getMenuScreen().backgroundAsset() : null;
    drawGameplayMenuBackdrop(w, h);

    String title = scene != null ? scene.getDisplayTitle() : Localization.t("history.title");
    String subtitle = scene != null ? scene.getDisplaySubtitle() : null;
    double titleY = (layout != null && layout.titleY() != null) ? resolve(layout.titleY(), h) : resolve(0.1, h);
    drawHeader(title, subtitle, w, titleY, screenStyle, layout);

    Font titleFont = resolveTitleFont(screenStyle);
    Font subtitleFont = resolveSubtitleFont(screenStyle);
    Font entryFont = resolveItemFont(entryStyle);
    double lineHeight = layout != null && layout.lineHeight() > 0 ? layout.lineHeight() : 34.0;
    Rect content = resolveHistoryContentRect(layout, w, h, titleFont, subtitleFont, subtitle);

    gc.setFill(Color.rgb(8, 12, 20, 0.74));
    gc.fillRoundRect(content.x() - 12, content.y() - 10, content.w() + 24, content.h() + 20, 14, 14);
    gc.setStroke(Color.rgb(215, 225, 245, 0.18));
    gc.setLineWidth(1.2);
    gc.strokeRoundRect(content.x() - 12, content.y() - 10, content.w() + 24, content.h() + 20, 14, 14);

    List<com.jvn.core.vn.VnHistory.HistoryEntry> entries = scene != null ? scene.getEntries() : List.of();
    int linesPerPage = scene != null ? scene.linesPerPage(h) : Math.max(1, (int) Math.floor(content.h() / lineHeight));
    int total = entries.size();
    int maxOffset = Math.max(0, total - linesPerPage);
    int effectiveOffset = Math.min(Math.max(0, scene != null ? scene.getScrollOffset() : 0), maxOffset);
    int startIdx = Math.max(0, total - 1 - effectiveOffset);

    gc.setFont(entryFont);
    Color entryColor = resolveItemColor(entryStyle, false, true);
    Color emptyColor = parseColor(entryStyle != null ? entryStyle.itemDisabledColor() : null, Color.rgb(160, 170, 190, 0.9));
    int drawn = 0;
    for (int i = startIdx; i >= 0 && drawn < linesPerPage; i--) {
      com.jvn.core.vn.VnHistory.HistoryEntry entry = entries.get(i);
      String speakerPrefix = entry.getSpeaker() != null && !entry.getSpeaker().isBlank() ? entry.getSpeaker() + ": " : "";
      String line = speakerPrefix + entry.getText();
      String truncated = truncateToWidth(line, Math.max(40, content.w() - 18), entryFont);
      double rowY = content.y() + drawn * lineHeight;
      gc.setFill((drawn % 2 == 0) ? Color.rgb(255, 255, 255, 0.055) : Color.rgb(255, 255, 255, 0.03));
      gc.fillRoundRect(content.x() - 4, rowY + 2, content.w() + 8, Math.max(18, lineHeight - 6), 8, 8);
      String shadowRaw = entryStyle != null ? entryStyle.itemShadowColor() : null;
      if (shadowRaw != null && !shadowRaw.isBlank()) {
        Color shadow = parseColor(shadowRaw, null);
        if (shadow != null) {
          double sx = entryStyle.itemShadowOffsetX() != null ? entryStyle.itemShadowOffsetX() : 1.0;
          double sy = entryStyle.itemShadowOffsetY() != null ? entryStyle.itemShadowOffsetY() : 1.0;
          gc.setFill(shadow);
          gc.fillText(truncated, content.x() + sx, rowY + lineHeight * 0.72 + sy);
        }
      }
      gc.setFill(entryColor);
      gc.fillText(truncated, content.x(), rowY + lineHeight * 0.72);
      drawn++;
    }

    if (total == 0) {
      gc.setFill(emptyColor);
      gc.setFont(entryFont);
      gc.fillText(Localization.t("history.empty"), content.x(), content.y() + lineHeight * 0.72);
    }

    if (maxOffset > 0) {
      double trackX = content.x() + content.w() + 10;
      double trackY = content.y();
      double trackW = 6;
      double trackH = content.h();
      gc.setFill(Color.rgb(255, 255, 255, 0.12));
      gc.fillRoundRect(trackX, trackY, trackW, trackH, 6, 6);
      double thumbFrac = Math.max(0.08, Math.min(1.0, (double) linesPerPage / (double) total));
      double thumbH = trackH * thumbFrac;
      double posFrac = maxOffset == 0 ? 0.0 : (double) effectiveOffset / (double) maxOffset;
      double thumbY = trackY + (trackH - thumbH) * posFrac;
      gc.setFill(parseColor(entryStyle != null ? entryStyle.itemSelectedColor() : null, Color.rgb(150, 200, 255, 0.8)));
      gc.fillRoundRect(trackX, thumbY, trackW, thumbH, 4, 4);
    }

    if (total > 0) {
      int totalPages = maxOffset == 0 ? 1 : (maxOffset / linesPerPage) + 1;
      int currentPage = maxOffset == 0 ? 1 : (effectiveOffset / linesPerPage) + 1;
      String pageText = "Page " + currentPage + " / " + totalPages;
      Font hintFont = resolveHintFont(screenStyle);
      gc.setFont(hintFont);
      gc.setFill(parseColor(screenStyle != null ? screenStyle.hintsColor() : null, theme.getHintColor()));
      gc.fillText(pageText, content.x() + content.w() - measure(pageText, hintFont), content.y() - 14);
    }

    String hints = scene != null ? scene.getDisplayHints() : Localization.t("history.hint");
    drawHints(hints, w, h, layout != null ? layout.hintsBottomMargin() : 18.0, screenStyle, layout);
  }

  public void renderSettings(SettingsScene scene, double w, double h) {
    if (scene != null) setUiFontScale(scene.getUiFontScale());
    MenuLayoutSpec layout = scene != null ? scene.getMenuLayout() : null;
    MenuStyleSpec screenStyle = scene != null ? scene.getDefaultMenuStyle() : null;
    String screenBg = scene != null && scene.getMenuScreen() != null ? scene.getMenuScreen().backgroundAsset() : null;
    if (scene != null && scene.getGameplayVnScene() != null) drawGameplayMenuBackdrop(w, h);
    else drawScreenBackground(w, h, screenStyle, false, screenBg);
    String title = scene != null ? scene.getDisplayTitle() : Localization.t("settings.title");
    String subtitle = scene != null ? scene.getDisplaySubtitle() : null;
    double titleY = (layout != null && layout.titleY() != null) ? resolve(layout.titleY(), h) : 60.0;
    drawHeader(title, subtitle, w, titleY, screenStyle, layout);

    String[] items = scene.getDisplayItems();
    boolean[] enabled = new boolean[items.length];
    MenuStyleSpec[] styles = new MenuStyleSpec[items.length];
    MenuItemSpec[] specs = new MenuItemSpec[items.length];
    for (int i = 0; i < items.length; i++) {
      enabled[i] = scene.isItemEnabled(i);
      styles[i] = scene.getStyleForIndex(i);
      specs[i] = scene.getMenuItemSpec(i);
    }
    drawMenuList(items, scene.getSelected(), enabled, styles, specs, layout, 0, w, h);

    for (int i = 0; i < items.length; i++) {
      boolean hasSlider = scene.hasSliderAt(i);
      if (!hasSlider) continue;
      double value = scene.sliderValue01At(i);
      MenuItemSpec item = specs[i];
      double[] geo = sliderGeometry(i, items.length, item, specs, layout, w, h);
      drawSlider(geo[0], geo[1], geo[2], value, i == scene.getSelected(), item);
      Rect resetRect = resolveSettingsSliderResetRect(item, i == scene.getSelected(), geo[0], geo[1], geo[2], w, h);
      drawSettingsSliderReset(item, i == scene.getSelected(), resetRect);
    }
    for (int i = 0; i < items.length; i++) {
      if (!scene.hasToggleAt(i)) continue;
      MenuItemSpec item = specs[i];
      Rect toggleRect = resolveSettingsToggleRect(item, i, items.length, specs, layout, w, h);
      drawSettingsToggle(item, scene.toggleValueAt(i), i == scene.getSelected(), toggleRect);
    }
    String hints = scene != null ? scene.getDisplayHints() : null;
    if (hints == null) {
      hints = "Up/Down, Left/Right, Enter • " + Localization.t("common.back") + ": Esc";
    }
    drawHints(hints, w, h, layout != null ? layout.hintsBottomMargin() : 20.0, screenStyle, layout);
  }

  private void clear(double w, double h) {
    gc.setFill(theme.getBackgroundColor());
    gc.fillRect(0, 0, w, h);
  }

  private void drawGameplayMenuBackdrop(double w, double h) {
    Color dim = theme.getGameplayDimColor();
    if (dim == null) dim = Color.rgb(0, 0, 0, 0.50);
    gc.setFill(dim);
    gc.fillRect(0, 0, w, h);

    Image panel = loadImage(theme.getGameplayPanelImagePath());
    double panelX = w / 2.0;
    double panelY = h / 2.0;
    double panelScale = theme.getGameplayPanelScale();
    if (panel != null) {
      double panelW = panel.getWidth() * panelScale;
      double panelH = panel.getHeight() * panelScale;
      panelX = (w - panelW) / 2.0;
      panelY = (h - panelH) / 2.0;
      gc.drawImage(panel, panelX, panelY, panelW, panelH);
      panelX += panelW / 2.0;
    }

    Image logo = loadImage(theme.getGameplayLogoImagePath());
    if (logo != null) {
      double logoScale = theme.getGameplayLogoScale();
      double logoW = logo.getWidth() * logoScale;
      double logoH = logo.getHeight() * logoScale;
      double logoX = panelX - logoW / 2.0;
      double logoY = panel == null ? h * 0.08 : panelY + 18.0;
      gc.drawImage(logo, logoX, logoY, logoW, logoH);
    }
  }

  private void drawScreenBackground(double w, double h, MenuStyleSpec style, boolean allowThemeImageFallback) {
    drawScreenBackground(w, h, style, allowThemeImageFallback, null);
  }

  private void drawScreenBackground(double w, double h, MenuStyleSpec style, boolean allowThemeImageFallback, String screenBgAsset) {
    if (screenBgAsset != null && !screenBgAsset.isBlank()) {
      Image screenBgImage = loadImage(screenBgAsset);
      if (screenBgImage != null) {
        // Always paint a base layer first so transparent menu background PNGs
        // do not leak pixels from the previously rendered scene.
        Color base = parseColor(style != null ? style.backgroundColor() : null, theme.getBackgroundColor());
        if (base == null) base = Color.BLACK;
        double baseOpacity = style != null && style.backgroundOpacity() != null
            ? clamp01(style.backgroundOpacity())
            : 1.0;
        gc.setFill(Color.color(base.getRed(), base.getGreen(), base.getBlue(), baseOpacity));
        gc.fillRect(0, 0, w, h);
        gc.drawImage(screenBgImage, 0, 0, w, h);
        return;
      }
    }
    String styleAsset = style != null ? style.backgroundAssetPath() : null;
    if (styleAsset != null && !styleAsset.isBlank()) {
      Image styleImage = loadImage(styleAsset);
      if (styleImage != null) {
        double alpha = style != null && style.backgroundOpacity() != null ? clamp01(style.backgroundOpacity()) : 1.0;
        if (isFrostedOverlayStyle(style)) {
          drawBlurredSubmenuBackground(styleImage, w, h, alpha);
          return;
        }
        double prevAlpha = gc.getGlobalAlpha();
        gc.setGlobalAlpha(alpha);
        gc.drawImage(styleImage, 0, 0, w, h);
        gc.setGlobalAlpha(prevAlpha);
        return;
      }
    }

    // Backward-compatibility: older submenu/slot styles may not inherit a background asset.
    // In that case, use the themed main-menu background as the frosted source.
    if (isFrostedOverlayStyle(style) && theme.getBackgroundImagePath() != null) {
      Image fallbackFrostSource = loadImage(theme.getBackgroundImagePath());
      if (fallbackFrostSource != null) {
        double alpha = style != null && style.backgroundOpacity() != null ? clamp01(style.backgroundOpacity()) : 1.0;
        drawBlurredSubmenuBackground(fallbackFrostSource, w, h, alpha);
        return;
      }
    }

    // Submenus should visually stay tied to the main menu background.
    // If no image source exists, use themed background color plus frosted tint
    // instead of falling back to slot/submenu hardcoded colors.
    if (isFrostedOverlayStyle(style)) {
      Color base = theme.getBackgroundColor() == null ? Color.BLACK : theme.getBackgroundColor();
      double opacity = style != null && style.backgroundOpacity() != null ? clamp01(style.backgroundOpacity()) : base.getOpacity();
      gc.setFill(Color.color(base.getRed(), base.getGreen(), base.getBlue(), opacity));
      gc.fillRect(0, 0, w, h);
      gc.setFill(SUBMENU_FROST_TINT);
      gc.fillRect(0, 0, w, h);
      return;
    }

    String styleColorRaw = style != null ? style.backgroundColor() : null;
    if (styleColorRaw != null && !styleColorRaw.isBlank()) {
      Color color = parseColor(styleColorRaw, theme.getBackgroundColor());
      double opacity = style != null && style.backgroundOpacity() != null ? clamp01(style.backgroundOpacity()) : color.getOpacity();
      gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), opacity));
      gc.fillRect(0, 0, w, h);
      return;
    }

    if (allowThemeImageFallback && theme.getBackgroundImagePath() != null) {
      drawBackgroundImage(theme.getBackgroundImagePath(), w, h);
      return;
    }
    clear(w, h);
  }

  private boolean isFrostedOverlayStyle(MenuStyleSpec style) {
    if (style == null || style.id() == null) return false;
    String id = style.id().trim().toLowerCase();
    return "submenu".equals(id) || "slot".equals(id) || "settings".equals(id);
  }

  private void drawBlurredSubmenuBackground(Image image, double w, double h, double alpha) {
    gc.save();
    gc.setGlobalAlpha(alpha);
    gc.setEffect(new GaussianBlur(SUBMENU_BACKGROUND_BLUR_RADIUS));
    gc.drawImage(image, 0, 0, w, h);
    gc.restore();

    // Frosted overlay to create the "iced glass" submenu surface.
    gc.setFill(SUBMENU_FROST_TINT);
    gc.fillRect(0, 0, w, h);
  }

  private void drawBackgroundImage(String path, double w, double h) {
    Image img = loadImage(path);
    if (img != null) {
      // Draw scaled to fill
      gc.drawImage(img, 0, 0, w, h);
    } else {
      clear(w, h);
    }
  }

  private void drawLogo(String path, double w, double h) {
    Image img = loadImage(path);
    if (img == null) return;
    
    double scale = theme.getLogoScale();
    double logoW = img.getWidth() * scale;
    double logoH = img.getHeight() * scale;
    
    // Calculate position (logoX/logoY are fractions)
    double x = w * theme.getLogoX() - logoW / 2;
    double y = h * theme.getLogoY();
    
    // Draw shadow if enabled
    if (theme.isLogoShadow()) {
      gc.setGlobalAlpha(0.4);
      gc.drawImage(img, x + 4, y + 4, logoW, logoH);
      gc.setGlobalAlpha(1.0);
    }
    
    gc.drawImage(img, x, y, logoW, logoH);
  }

  private Image loadImage(String path) {
    if (path == null || path.isBlank()) return null;
    Image cached = imageCache.get(path);
    if (cached != null) return cached;

    List<String> candidates = new ArrayList<>();
    candidates.add(path);
    candidates.addAll(buildFallbackImageCandidates(path));

    for (String candidate : candidates) {
      Image img = loadImageDirect(candidate);
      if (img != null) {
        imageCache.put(path, img);
        imageCache.put(candidate, img);
        return img;
      }
    }
    return null;
  }

  private Image loadImageDirect(String path) {
    if (path == null || path.isBlank()) return null;
    try {
      // Resolve via active asset manager first (supports --assets and project roots).
      try {
        AssetCatalog catalog = new AssetCatalog();
        var resolved = catalog.url(AssetType.IMAGE, path);
        if (resolved != null) {
          Image img = new Image(resolved.toExternalForm());
          if (!img.isError() && img.getWidth() > 0 && img.getHeight() > 0) return img;
        }
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }

      // Try classpath first
      var url = getClass().getClassLoader().getResource(path);
      if (url != null) {
        Image img = new Image(url.toExternalForm());
        if (!img.isError() && img.getWidth() > 0 && img.getHeight() > 0) return img;
      }
      // Then filesystem
      File f = new File(path);
      if (f.exists()) {
        Image img = new Image(f.toURI().toString());
        if (!img.isError() && img.getWidth() > 0 && img.getHeight() > 0) return img;
      }
    } catch (Exception e) {
      log.warn("Failed to load menu image: {}", path);
    }
    return null;
  }

  private List<String> buildFallbackImageCandidates(String originalPath) {
    List<String> out = new ArrayList<>();
    String lower = originalPath.toLowerCase();
    int dot = originalPath.lastIndexOf('.');
    String base = dot > 0 ? originalPath.substring(0, dot) : originalPath;
    if (dot > 0) {
      out.add(base + ".png");
      out.add(base + ".jpg");
      out.add(base + ".jpeg");
      out.add(base + ".webp");
      out.add(base + ".bmp");
      out.add(base + ".gif");
    }

    // Generic sibling fallbacks for old projects that may not have the same filename set.
    int slash = originalPath.lastIndexOf('/');
    if (slash > 0) {
      String parent = originalPath.substring(0, slash + 1);
      out.add(parent + "field.jpg");
      out.add(parent + "field.png");
    }
    return out;
  }

  public void clearImageCache() {
    imageCache.clear();
    dock.clear();
  }

  public void clearTextMeasureCache() {
    textMetrics.clear();
    dock.clear();
  }

  private boolean disposed = false;

  public void dispose() {
    if (disposed) return;
    disposed = true;
    dock.close();
    imageCache.clear();
    textMetrics.clear();
    dock.clear();
  }

  private void drawTitle(String text, double w, double y) {
    drawTitle(text, w, y, null, null);
  }

  private void drawTitle(String text, double w, double y, MenuStyleSpec style) {
    drawTitle(text, w, y, style, null);
  }

  private void drawTitle(String text, double w, double y, MenuStyleSpec style, MenuLayoutSpec layout) {
    if (text == null || text.isBlank()) text = "JVN";
    Color titleColor = parseColor(style != null ? style.titleColor() : null, theme.getTitleColor());
    Font titleFont = resolveTitleFont(style);
    gc.setFont(titleFont);
    double tx = resolveTitleAlignedX(text, titleFont, w, layout);

    // Title shadow
    String shadowRaw = style != null ? style.titleShadowColor() : null;
    if (shadowRaw != null && !shadowRaw.isBlank()) {
      Color shadow = parseColor(shadowRaw, null);
      if (shadow != null) {
        gc.setFill(shadow);
        gc.fillText(text, tx + 2, y + 2);
      }
    }

    gc.setFill(titleColor);
    gc.fillText(text, tx, y);
  }

  private void drawHeader(String title, String subtitle, double w, double titleY, MenuStyleSpec style, MenuLayoutSpec layout) {
    drawTitle(title, w, titleY, style, layout);
    if (subtitle == null || subtitle.isBlank()) return;
    Font titleFont = resolveTitleFont(style);
    Font subtitleFont = resolveSubtitleFont(style);
    double subtitleY = titleY + Math.max(titleFont.getSize() * 0.82, subtitleFont.getSize()) + (layout != null ? layout.subtitleGap() : 12.0);
    drawSubtitle(subtitle, w, subtitleY, style, layout, subtitleFont);
  }

  private void drawSubtitle(String text, double w, double y, MenuStyleSpec style, MenuLayoutSpec layout, Font subtitleFont) {
    if (text == null || text.isBlank()) return;
    Color subtitleColor = parseColor(style != null ? style.hintsColor() : null,
        parseColor(style != null ? style.titleColor() : null, theme.getHintColor()));
    gc.setFont(subtitleFont);
    double tx = resolveTitleAlignedX(text, subtitleFont, w, layout);
    gc.setFill(subtitleColor);
    gc.fillText(text, tx, y);
  }

  private double resolveTitleAlignedX(String text, Font font, double w, MenuLayoutSpec layout) {
    double textW = measure(text, font);
    Double txOverride = layout != null ? layout.titleX() : null;
    String align = layout != null ? layout.titleAlign() : "center";
    double tx = txOverride != null
        ? w * txOverride - textW / 2.0
        : switch (align == null ? "center" : align.toLowerCase()) {
          case "left" -> 24.0;
          case "right" -> w - textW - 24.0;
          default -> (w - textW) / 2.0;
        };
    return clamp(tx, 0, Math.max(0, w - textW));
  }

  private void drawMenuList(String[] items, int selected, double w, double h) {
    drawMenuList(items, selected, null, null, null, null, 0, w, h, false);
  }

  private void drawMenuList(
      String[] items,
      int selected,
      boolean[] enabled,
      MenuStyleSpec[] styles,
      MenuItemSpec[] itemSpecs,
      MenuLayoutSpec layout,
      double areaX,
      double areaWidth,
      double h
  ) {
    drawMenuList(items, selected, enabled, styles, itemSpecs, layout, areaX, areaWidth, h, false);
  }

  private void drawMenuList(
      String[] items,
      int selected,
      boolean[] enabled,
      MenuStyleSpec[] styles,
      MenuItemSpec[] itemSpecs,
      MenuLayoutSpec layout,
      double areaX,
      double areaWidth,
      double h,
      boolean reserveInlineSlotPreviewSpace
  ) {
    String align = layout != null ? layout.textAlign() : "center";
    double textPadXDefault = 18;
    double textPadYDefault = 0;
    for (int i = 0; i < items.length; i++) {
      MenuStyleSpec style = styles != null && i < styles.length ? styles[i] : null;
      MenuItemSpec item = itemSpecs != null && i < itemSpecs.length ? itemSpecs[i] : null;
      boolean isEnabled = enabled == null || i >= enabled.length || enabled[i];
      boolean sel = i == selected;
      boolean sectionItem = isSectionItem(item);
      boolean bodyItem = isBodyTextItem(item);
      boolean noteItem = isNoteTextItem(item);
      String label = (sectionItem || bodyItem || noteItem)
          ? (items[i] == null ? "" : items[i])
          : withPrefix(items[i], style, sel, isEnabled);
      Color color = resolveItemColor(style, sel, isEnabled);
      Font font = resolveItemFont(style, item);
      Rect rect = resolveItemRect(i, items.length, item, itemSpecs, layout, areaX, areaWidth, h);
      boolean inlinePreviewEnabled = reserveInlineSlotPreviewSpace && isInlineSlotPreviewEnabled(item, true);
      Rect inlinePreviewRect = inlinePreviewEnabled ? resolveInlineSlotPreviewRect(item, rect) : null;
      double reservedRightSpace = inlinePreviewRect != null
          ? Math.max(0, rect.x() + rect.w() - inlinePreviewRect.x() + 8)
          : 0;

      if (!sectionItem && !bodyItem && !noteItem) {
        String backgroundAsset = resolveButtonAssetPath(item, style, sel, isEnabled);
        Image buttonBg = loadImage(backgroundAsset);
        if (buttonBg != null) {
          gc.drawImage(buttonBg, rect.x(), rect.y(), rect.w(), rect.h());
        } else {
          Color bgFill = !isEnabled
              ? Color.rgb(80, 80, 90, 0.45)
              : (sel ? Color.rgb(90, 120, 180, 0.5) : Color.rgb(32, 36, 46, 0.55));
          gc.setFill(bgFill);
          gc.fillRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 10, 10);
          gc.setStroke(sel ? Color.rgb(170, 210, 255, 0.9) : Color.rgb(110, 130, 160, 0.55));
          gc.setLineWidth(sel ? 2.0 : 1.1);
          gc.strokeRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 10, 10);
        }
      } else {
        if (sectionItem) {
          double dividerY = rect.y() + rect.h() * 0.62;
          gc.setStroke(Color.rgb(160, 176, 210, 0.28));
          gc.setLineWidth(1.0);
          gc.strokeLine(rect.x(), dividerY, rect.x() + rect.w(), dividerY);
        } else if (noteItem) {
          gc.setFill(Color.rgb(24, 31, 42, sel && isEnabled ? 0.86 : 0.72));
          gc.fillRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 10, 10);
          gc.setStroke(Color.rgb(118, 138, 172, 0.38));
          gc.setLineWidth(1.0);
          gc.strokeRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 10, 10);
        }
      }

      Image iconImage = item != null ? loadImage(item.iconPath()) : null;
      double iconSize = 0;
      if (iconImage != null) {
        iconSize = clamp(rect.h() * 0.56, 12, 36);
        double iconX = rect.x() + 10;
        double iconY = rect.y() + (rect.h() - iconSize) / 2.0;
        double previousAlpha = gc.getGlobalAlpha();
        gc.setGlobalAlpha(isEnabled ? 0.98 : 0.55);
        gc.drawImage(iconImage, iconX, iconY, iconSize, iconSize);
        gc.setGlobalAlpha(previousAlpha);
      }

      if (bodyItem || noteItem) {
        drawWrappedItemText(label, rect, item, style, layout, font, color, iconSize, reservedRightSpace);
        continue;
      }

      gc.setFont(font);
      double tw = measure(label, font);
      String itemAlign = resolveItemTextAlign(item, align);
      Double textXRaw = parseExtraDouble(item, "textX");
      Double textBaselineRaw = firstNonNull(parseExtraDouble(item, "textBaselineY"), parseExtraDouble(item, "textY"));
      double textPadX = resolveItemTextPaddingX(item, style, textPadXDefault);
      double textPadY = resolveItemTextPaddingY(item, style, textPadYDefault);
      double leftInset = rect.x() + Math.max(0, textPadX) + (iconSize > 0 ? iconSize + 8 : 0);
      double rightInset = rect.x() + Math.max(0, rect.w() - textPadX - reservedRightSpace);
      double x = textXRaw != null
          ? rect.x() + resolveLocalCoordinate(textXRaw, rect.w())
          : switch (itemAlign) {
            case "left" -> leftInset;
            case "right" -> rightInset - tw;
            default -> leftInset + Math.max(0, (rightInset - leftInset - tw) / 2.0);
          };
      if (sectionItem && textXRaw == null) {
        x = rect.x();
      }
      double baseline = textBaselineRaw != null
          ? rect.y() + resolveLocalCoordinate(textBaselineRaw, rect.h())
          : rect.y() + rect.h() * 0.55 + textPadY;
      drawItemText(label, x, baseline, style, font, color);
    }
  }

  private void drawHints(String text, double w, double h) {
    drawHints(text, w, h, 20.0);
  }

  private void drawHints(String text, double w, double h, double bottomMargin) {
    drawHints(text, w, h, bottomMargin, null);
  }

  private void drawHints(String text, double w, double h, double bottomMargin, MenuStyleSpec style) {
    drawHints(text, w, h, bottomMargin, style, null);
  }

  private void drawHints(String text, double w, double h, double bottomMargin, MenuStyleSpec style, MenuLayoutSpec layout) {
    Font hintFont = resolveHintFont(style);
    Color hintColor = parseColor(style != null ? style.hintsColor() : null, theme.getHintColor());
    gc.setFill(hintColor);
    gc.setFont(hintFont);
    double textW = measure(text, hintFont);
    Double hintsX = layout != null ? layout.hintsX() : null;
    String align = layout != null ? layout.hintsAlign() : "center";
    double x = hintsX != null
        ? w * hintsX - textW / 2.0
        : switch (align == null ? "center" : align.toLowerCase()) {
          case "left" -> 20.0;
          case "right" -> w - textW - 20.0;
          default -> (w - textW) / 2.0;
        };
    gc.fillText(text, clamp(x, 0, Math.max(0, w - textW)), h - Math.max(0, bottomMargin));
  }

  private void drawCenteredText(String text, double w, double y, Font font, Color color) {
    gc.setFill(color);
    gc.setFont(font);
    gc.fillText(text, (w - measure(text, font)) / 2, y);
  }

  private double measure(String s, Font f) {
    return textMetrics.width(s, f);
  }

  private String truncateToWidth(String text, double maxWidth, Font font) {
    if (text == null) return "";
    if (maxWidth <= 0) return "";
    if (measure(text, font) <= maxWidth) return text;
    String ellipsis = "...";
    if (measure(ellipsis, font) > maxWidth) return ellipsis;
    int end = text.length();
    while (end > 0) {
      String candidate = text.substring(0, end).stripTrailing() + ellipsis;
      if (measure(candidate, font) <= maxWidth) return candidate;
      end--;
    }
    return ellipsis;
  }

  public int getHoverIndexForList(int count, double w, double h, double mouseX, double mouseY) {
    if (count <= 0) return -1;
    return hoverIndex(count, null, null, 0, w, h, mouseX, mouseY);
  }

  private boolean usesDock(MainMenuScene scene) {
    return scene != null && theme.isDockPresentation()
        && scene.getMenuId().equals(scene.getMenuProfile().defaultScreenId());
  }

  public int getHoverIndexForMainMenu(MainMenuScene scene, double w, double h, double mouseX, double mouseY) {
    if (usesDock(scene)) return dock.hover(scene, w, h, mouseX, mouseY);
    if (scene == null) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[scene.getItemCount()];
    for (int i = 0; i < specs.length; i++) specs[i] = scene.getMenuItemSpec(i);
    return hoverIndex(scene.getItemCount(), scene.getMenuLayout(), specs, 0, w, h, mouseX, mouseY);
  }

  public int getHoverIndexForPauseMenu(PauseMenuScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[scene.getItemCount()];
    for (int i = 0; i < specs.length; i++) specs[i] = scene.getMenuItemSpec(i);
    return hoverIndex(scene.getItemCount(), scene.getMenuLayout(), specs, 0, w, h, mouseX, mouseY);
  }

  public int getHoverIndexForLoadMenu(LoadMenuScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return -1;
    int total = scene.getItemCount();
    if (total <= 0) return -1;
    MenuLayoutSpec layout = scene.getMenuLayout();
    int startIndex = scene.getVisibleStartIndex();
    int drawCount = scene.getVisibleCount();
    drawCount = Math.max(0, Math.min(drawCount, total - startIndex));
    if (drawCount <= 0) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[drawCount];
    for (int i = 0; i < drawCount; i++) specs[i] = scene.getMenuItemSpec(startIndex + i);
    double listAreaWidth = resolveLoadListAreaWidth(scene, specs, w);
    int local = hoverIndex(drawCount, layout, specs, 0, listAreaWidth, h, mouseX, mouseY);
    return local < 0 ? -1 : (startIndex + local);
  }

  public LoadControlHit getLoadControlHit(LoadMenuScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return LoadControlHit.none();
    MenuItemSpec template = resolveLoadTemplateItem(scene, null);
    if (!areLoadControlsVisible(template)) return LoadControlHit.none();

    // Per-slot favorite icon hit (visible rows only).
    int total = scene.getItemCount();
    if (total > 0) {
      MenuLayoutSpec layout = scene.getMenuLayout();
      int startIndex = scene.getVisibleStartIndex();
      int drawCount = scene.getVisibleCount();
      drawCount = Math.max(0, Math.min(drawCount, total - startIndex));
      if (drawCount > 0) {
        MenuItemSpec[] specs = new MenuItemSpec[drawCount];
        for (int i = 0; i < drawCount; i++) specs[i] = scene.getMenuItemSpec(startIndex + i);
        double listAreaWidth = resolveLoadListAreaWidth(scene, specs, w);
        for (int i = 0; i < drawCount; i++) {
          Rect itemRect = resolveItemRect(i, drawCount, specs[i], specs, layout, 0, listAreaWidth, h);
          Rect iconRect = resolveLoadSlotFavoriteIconRect(template, itemRect);
          if (iconRect.contains(mouseX, mouseY)) {
            return new LoadControlHit(LoadControlType.TOGGLE_SLOT_FAVORITE, startIndex + i, 0.0);
          }
        }
      }
    }

    Rect leftRect = resolveLoadCycleLeftRect(template, w, h);
    if (leftRect.contains(mouseX, mouseY)) {
      return new LoadControlHit(LoadControlType.CYCLE_LEFT, -1, 0.0);
    }
    Rect rightRect = resolveLoadCycleRightRect(template, w, h);
    if (rightRect.contains(mouseX, mouseY)) {
      return new LoadControlHit(LoadControlType.CYCLE_RIGHT, -1, 0.0);
    }
    Rect favoritesRect = resolveLoadFavoritesButtonRect(template, w, h);
    if (favoritesRect.contains(mouseX, mouseY)) {
      return new LoadControlHit(LoadControlType.TOGGLE_FAVORITES_ONLY, -1, 0.0);
    }
    Rect trackRect = resolveLoadPageTrackRect(template, w, h);
    Rect selectorRect = resolveLoadPageSelectorRect(template, trackRect, scene.getPageProgress01());
    if (trackRect.contains(mouseX, mouseY) || selectorRect.contains(mouseX, mouseY)) {
      double t = trackRect.w() <= 1 ? 0.0 : (mouseX - trackRect.x()) / trackRect.w();
      return new LoadControlHit(LoadControlType.SET_PAGE, -1, clamp01(t));
    }
    return LoadControlHit.none();
  }

  public int getHoverIndexForSaveMenu(SaveMenuScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[scene.getItemCount()];
    for (int i = 0; i < specs.length; i++) specs[i] = scene.getMenuItemSpec(i);
    double listAreaWidth = shouldShowSaveSidePreview(scene, specs) ? w * 0.6 : w;
    return hoverIndex(scene.getItemCount(), scene.getMenuLayout(), specs, 0, listAreaWidth, h, mouseX, mouseY);
  }

  public int getHoverIndexForSettings(SettingsScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[scene.itemCount()];
    for (int i = 0; i < specs.length; i++) specs[i] = scene.getMenuItemSpec(i);
    return hoverIndex(scene.itemCount(), scene.getMenuLayout(), specs, 0, w, h, mouseX, mouseY);
  }

  private int hoverIndex(
      int count,
      MenuLayoutSpec layout,
      MenuItemSpec[] itemSpecs,
      double areaX,
      double areaWidth,
      double h,
      double mouseX,
      double mouseY
  ) {
    if (count <= 0) return -1;
    for (int i = 0; i < count; i++) {
      MenuItemSpec item = itemSpecs != null && i < itemSpecs.length ? itemSpecs[i] : null;
      Rect rect = resolveItemRect(i, count, item, itemSpecs, layout, areaX, areaWidth, h);
      if (itemContainsPoint(item, rect, mouseX, mouseY)) return i;
    }
    return -1;
  }

  private boolean itemContainsPoint(MenuItemSpec itemSpec, Rect rect, double mouseX, double mouseY) {
    if (rect == null) return false;
    if (itemSpec != null && !itemSpec.enabled()) return false;
    if (isSectionItem(itemSpec) || isBodyTextItem(itemSpec) || isNoteTextItem(itemSpec)) return false;
    if (itemSpec != null && itemSpec.extras() != null) {
      String raw = itemSpec.extras().get("boundsPoints");
      if (raw != null && !raw.isBlank()) {
        List<BoundsPointCodec.Point> points = BoundsPointCodec.parse(raw);
        if (points.size() >= 3) {
          if (BoundsPointCodec.containsInRect(points, rect.x(), rect.y(), rect.w(), rect.h(), mouseX, mouseY)) {
            return true;
          }
          return false;
        }
      }
    }
    return rect.contains(mouseX, mouseY);
  }

  private Rect resolveItemRect(
      int index,
      int count,
      MenuItemSpec itemSpec,
      MenuItemSpec[] itemSpecs,
      MenuLayoutSpec layout,
      double areaX,
      double areaWidth,
      double h
  ) {
    double yStart = layout != null ? resolve(layout.listYStart(), h) : resolve(theme.getListYStart(), h);
    double lineH = layout != null ? layout.lineHeight() : theme.getLineHeight();
    if (lineH <= 0) lineH = theme.getLineHeight();
    double widthFactor = layout != null ? clamp(layout.listWidthFactor(), 0.1, 1.0) : 1.0;
    double listW = areaWidth * widthFactor;
    Double xCenter = layout != null ? layout.listXCenter() : null;
    double listX;
    if (xCenter != null) {
      listX = areaX + areaWidth * xCenter - listW / 2.0;
      listX = clamp(listX, areaX, areaX + Math.max(0, areaWidth - listW));
    } else {
      String align = layout != null ? layout.textAlign() : "center";
      listX = switch (align == null ? "center" : align.toLowerCase()) {
        case "left" -> areaX;
        case "right" -> areaX + areaWidth - listW;
        default -> areaX + (areaWidth - listW) / 2.0;
      };
    }

    if (itemSpec != null && itemSpec.boundsX() != null && itemSpec.boundsY() != null
        && itemSpec.boundsWidth() != null && itemSpec.boundsHeight() != null) {
      double x = resolveCoordinate(itemSpec.boundsX(), areaWidth) + areaX;
      double y = resolveCoordinate(itemSpec.boundsY(), h);
      double w = resolveSize(itemSpec.boundsWidth(), areaWidth);
      double hh = resolveSize(itemSpec.boundsHeight(), h);
      w = clamp(w, 8, Math.max(8, areaWidth));
      hh = clamp(hh, 8, Math.max(8, h));
      x = clamp(x, areaX, areaX + Math.max(0, areaWidth - w));
      y = clamp(y, 0, Math.max(0, h - hh));
      return new Rect(x, y, w, hh);
    }

    double rowCursor = 0.0;
    if (itemSpecs != null) {
      for (int i = 0; i < index && i < itemSpecs.length; i++) {
        rowCursor += resolveItemRowSpan(itemSpecs[i]);
      }
    } else {
      rowCursor = index;
    }
    int rowSpan = resolveItemRowSpan(itemSpec);
    double itemH = Math.max(24, lineH * rowSpan * 0.92);
    double itemY = (yStart - lineH * 0.70) + rowCursor * lineH;
    return new Rect(listX, itemY, Math.max(1, listW), itemH);
  }

  private Rect resolveHistoryContentRect(MenuLayoutSpec layout, double w, double h, Font titleFont, Font subtitleFont, String subtitleText) {
    double yStart = layout != null ? resolve(layout.listYStart(), h) : resolve(0.16, h);
    double lineH = layout != null && layout.lineHeight() > 0 ? layout.lineHeight() : 34.0;
    double widthFactor = layout != null ? clamp(layout.listWidthFactor(), 0.1, 1.0) : 0.88;
    double listW = w * widthFactor;
    Double xCenter = layout != null ? layout.listXCenter() : null;
    double listX;
    if (xCenter != null) {
      listX = w * xCenter - listW / 2.0;
      listX = clamp(listX, 24, Math.max(24, w - listW - 24));
    } else {
      String align = layout != null ? layout.textAlign() : "center";
      listX = switch (align == null ? "center" : align.toLowerCase()) {
        case "left" -> 24;
        case "right" -> Math.max(24, w - listW - 24);
        default -> (w - listW) / 2.0;
      };
    }
    double titleY = layout != null && layout.titleY() != null
        ? resolve(layout.titleY(), h)
        : titleFont.getSize() * 1.2;
    double titleBottom = titleY + titleFont.getSize() * 0.82;
    if (subtitleText != null && !subtitleText.isBlank()) {
      double gap = layout != null ? layout.subtitleGap() : 12.0;
      titleBottom += gap + subtitleFont.getSize() * 1.2;
    }
    double top = Math.max(yStart, titleBottom);
    double bottom = h - (layout != null ? Math.max(0.0, layout.hintsBottomMargin()) : 18.0) - 28.0;
    return new Rect(listX, top, Math.max(120, listW), Math.max(lineH, bottom - top));
  }

  private boolean isInlineSlotPreviewEnabled(MenuItemSpec itemSpec, boolean defaultIfMissingSpec) {
    if (itemSpec == null) return defaultIfMissingSpec;
    return itemSpec.slotPreviewEnabled();
  }

  private boolean isSectionItem(MenuItemSpec itemSpec) {
    String normalized = normalizedRenderRole(itemSpec);
    if (normalized == null) return false;
    return "section".equals(normalized) || "header".equals(normalized);
  }

  private boolean isBodyTextItem(MenuItemSpec itemSpec) {
    String normalized = normalizedRenderRole(itemSpec);
    if (normalized == null) return false;
    return "body".equals(normalized) || "paragraph".equals(normalized) || "text".equals(normalized);
  }

  private boolean isNoteTextItem(MenuItemSpec itemSpec) {
    String normalized = normalizedRenderRole(itemSpec);
    if (normalized == null) return false;
    return "note".equals(normalized) || "card".equals(normalized);
  }

  private String normalizedRenderRole(MenuItemSpec itemSpec) {
    if (itemSpec == null || itemSpec.extras() == null) return null;
    String raw = firstNonBlank(itemSpec.extras().get("renderAs"), itemSpec.extras().get("role"));
    if (raw == null) return null;
    return raw.trim().toLowerCase();
  }

  private int resolveItemRowSpan(MenuItemSpec itemSpec) {
    if (itemSpec == null || itemSpec.extras() == null) return 1;
    String raw = firstNonBlank(itemSpec.extras().get("rowSpan"), itemSpec.extras().get("rows"));
    if (raw == null || raw.isBlank()) return 1;
    try {
      return Math.max(1, Math.min(32, Integer.parseInt(raw.trim())));
    } catch (NumberFormatException ex) {
      return 1;
    }
  }

  private String resolveBodyAlign(MenuItemSpec itemSpec, MenuLayoutSpec layout) {
    if (itemSpec != null && itemSpec.extras() != null) {
      String raw = firstNonBlank(itemSpec.extras().get("bodyAlign"), itemSpec.extras().get("textAlign"));
      if (raw != null) {
        String normalized = raw.trim().toLowerCase();
        if ("left".equals(normalized) || "center".equals(normalized) || "right".equals(normalized)) {
          return normalized;
        }
      }
    }
    return layout != null ? layout.textAlign() : "left";
  }

  private String resolveItemTextAlign(MenuItemSpec itemSpec, String defaultAlign) {
    if (itemSpec != null && itemSpec.extras() != null) {
      String raw = firstNonBlank(itemSpec.extras().get("textAlign"), itemSpec.extras().get("align"));
      if (raw != null) {
        String normalized = raw.trim().toLowerCase();
        if ("left".equals(normalized) || "center".equals(normalized) || "right".equals(normalized)) {
          return normalized;
        }
      }
    }
    return defaultAlign == null ? "center" : defaultAlign.toLowerCase();
  }

  private double resolveItemTextPaddingX(MenuItemSpec itemSpec, MenuStyleSpec style, double defaultValue) {
    Double parsed = firstNonNull(parseExtraDouble(itemSpec, "textPaddingX"), parseExtraDouble(itemSpec, "textPadX"));
    if (parsed != null) return parsed;
    return style != null && style.buttonTextPaddingX() != null ? style.buttonTextPaddingX() : defaultValue;
  }

  private double resolveItemTextPaddingY(MenuItemSpec itemSpec, MenuStyleSpec style, double defaultValue) {
    Double parsed = firstNonNull(parseExtraDouble(itemSpec, "textPaddingY"), parseExtraDouble(itemSpec, "textPadY"));
    if (parsed != null) return parsed;
    return style != null && style.buttonTextPaddingY() != null ? style.buttonTextPaddingY() : defaultValue;
  }

  private double resolveBodyPaddingX(MenuItemSpec itemSpec, MenuStyleSpec style) {
    Double parsed = parseExtraDouble(itemSpec, "bodyPaddingX");
    if (parsed != null) return Math.max(0.0, parsed);
    return style != null && style.buttonTextPaddingX() != null ? Math.max(0.0, style.buttonTextPaddingX()) : 18.0;
  }

  private double resolveBodyPaddingY(MenuItemSpec itemSpec, MenuStyleSpec style) {
    Double parsed = parseExtraDouble(itemSpec, "bodyPaddingY");
    if (parsed != null) return Math.max(0.0, parsed);
    return style != null && style.buttonTextPaddingY() != null ? Math.max(0.0, style.buttonTextPaddingY()) : 10.0;
  }

  private double resolveBodyLineHeight(MenuItemSpec itemSpec, Font font) {
    Double parsed = parseExtraDouble(itemSpec, "bodyLineHeight");
    if (parsed != null && parsed > 0) return parsed;
    return Math.max(font.getSize() * 1.35, font.getSize() + 6.0);
  }

  private Double parseExtraDouble(MenuItemSpec itemSpec, String key) {
    if (itemSpec == null || itemSpec.extras() == null || key == null) return null;
    String raw = itemSpec.extras().get(key);
    if (raw == null || raw.isBlank()) return null;
    try {
      double value = Double.parseDouble(raw.trim());
      return Double.isFinite(value) ? value : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static <T> T firstNonNull(T first, T second) {
    return first != null ? first : second;
  }

  private void drawWrappedItemText(
      String label,
      Rect rect,
      MenuItemSpec item,
      MenuStyleSpec style,
      MenuLayoutSpec layout,
      Font font,
      Color color,
      double iconSize,
      double reservedRightSpace
  ) {
    gc.setFont(font);
    double padX = resolveBodyPaddingX(item, style);
    double padY = resolveBodyPaddingY(item, style);
    double leftInset = rect.x() + padX + (iconSize > 0 ? iconSize + 8 : 0);
    double rightInset = rect.x() + Math.max(0, rect.w() - padX - reservedRightSpace);
    double maxWidth = Math.max(16.0, rightInset - leftInset);
    double lineHeight = resolveBodyLineHeight(item, font);
    List<String> lines = wrapTextToWidth(label, maxWidth, font);
    double baseline = rect.y() + padY + font.getSize();
    double maxBaseline = rect.y() + rect.h() - padY;
    String align = resolveBodyAlign(item, layout);
    for (String line : lines) {
      if (baseline > maxBaseline) break;
      double x = switch (align) {
        case "center" -> leftInset + Math.max(0, (maxWidth - measure(line, font)) / 2.0);
        case "right" -> rightInset - measure(line, font);
        default -> leftInset;
      };
      drawItemText(line, x, baseline, style, font, color);
      baseline += lineHeight;
    }
  }

  private void drawItemText(String label, double x, double baseline, MenuStyleSpec style, Font font, Color color) {
    double prevAlpha = gc.getGlobalAlpha();
    Double itemOp = style != null ? style.itemOpacity() : null;
    if (itemOp != null && itemOp < 0.999) gc.setGlobalAlpha(prevAlpha * itemOp);

    String shadowRaw = style != null ? style.itemShadowColor() : null;
    if (shadowRaw != null && !shadowRaw.isBlank()) {
      Color shadow = parseColor(shadowRaw, null);
      if (shadow != null) {
        double sx = style.itemShadowOffsetX() != null ? style.itemShadowOffsetX() : 1.5;
        double sy = style.itemShadowOffsetY() != null ? style.itemShadowOffsetY() : 1.5;
        gc.setFill(shadow);
        gc.setFont(font);
        gc.fillText(label, x + sx, baseline + sy);
      }
    }

    gc.setFill(color);
    gc.setFont(font);
    gc.fillText(label, x, baseline);
    gc.setGlobalAlpha(prevAlpha);
  }

  private List<String> wrapTextToWidth(String text, double maxWidth, Font font) {
    List<String> lines = new ArrayList<>();
    if (text == null || text.isBlank()) {
      lines.add("");
      return lines;
    }
    String[] paragraphs = text.replace("\r", "").split("\n", -1);
    for (int p = 0; p < paragraphs.length; p++) {
      String paragraph = paragraphs[p].trim();
      if (paragraph.isEmpty()) {
        lines.add("");
        continue;
      }
      String[] words = paragraph.split("\\s+");
      String current = "";
      for (String word : words) {
        String candidate = current.isEmpty() ? word : current + " " + word;
        if (measure(candidate, font) <= maxWidth) {
          current = candidate;
          continue;
        }
        if (!current.isEmpty()) {
          lines.add(current);
          current = "";
        }
        if (measure(word, font) <= maxWidth) {
          current = word;
        } else {
          lines.addAll(breakLongWord(word, maxWidth, font));
        }
      }
      if (!current.isEmpty()) lines.add(current);
      if (p < paragraphs.length - 1) lines.add("");
    }
    return lines.isEmpty() ? List.of("") : lines;
  }

  private List<String> breakLongWord(String word, double maxWidth, Font font) {
    List<String> pieces = new ArrayList<>();
    if (word == null || word.isEmpty()) return pieces;
    int start = 0;
    while (start < word.length()) {
      int end = start + 1;
      while (end <= word.length() && measure(word.substring(start, end), font) <= maxWidth) {
        end++;
      }
      int safeEnd = Math.max(start + 1, end - 1);
      pieces.add(word.substring(start, safeEnd));
      start = safeEnd;
    }
    return pieces;
  }

  private Rect resolveInlineSlotPreviewRect(MenuItemSpec itemSpec, Rect itemRect) {
    if (itemRect == null) return new Rect(0, 0, 1, 1);
    if (itemSpec != null
        && itemSpec.slotPreviewX() != null
        && itemSpec.slotPreviewY() != null
        && itemSpec.slotPreviewWidth() != null
        && itemSpec.slotPreviewHeight() != null) {
      double x = itemRect.x() + resolveCoordinate(itemSpec.slotPreviewX(), itemRect.w());
      double y = itemRect.y() + resolveCoordinate(itemSpec.slotPreviewY(), itemRect.h());
      double w = resolveSize(itemSpec.slotPreviewWidth(), itemRect.w());
      double h = resolveSize(itemSpec.slotPreviewHeight(), itemRect.h());
      w = clamp(w, 8, Math.max(8, itemRect.w()));
      h = clamp(h, 8, Math.max(8, itemRect.h()));
      x = clamp(x, itemRect.x(), itemRect.x() + Math.max(0, itemRect.w() - w));
      y = clamp(y, itemRect.y(), itemRect.y() + Math.max(0, itemRect.h() - h));
      return new Rect(x, y, w, h);
    }

    double margin = 6;
    double h = clamp(itemRect.h() - margin * 2, 14, Math.max(14, itemRect.h() - margin * 2));
    double w = clamp(Math.min(itemRect.w() * 0.34, h * 1.6), 24, Math.max(24, itemRect.w() - margin * 2));
    double x = itemRect.x() + itemRect.w() - w - margin;
    double y = itemRect.y() + (itemRect.h() - h) / 2.0;
    return new Rect(x, y, w, h);
  }

  private String resolveButtonAssetPath(MenuItemSpec item, MenuStyleSpec style, boolean selected, boolean enabled) {
    String path = null;
    if (!enabled) {
      path = firstNonBlank(
          item != null ? item.buttonDisabledAssetPath() : null,
          item != null ? item.buttonAssetPath() : null,
          style != null ? style.buttonDisabledAssetPath() : null,
          style != null ? style.buttonAssetPath() : null
      );
    } else if (selected) {
      path = firstNonBlank(
          item != null ? item.buttonSelectedAssetPath() : null,
          style != null ? style.buttonSelectedAssetPath() : null,
          style != null ? style.buttonHoverAssetPath() : null,
          item != null ? item.buttonAssetPath() : null,
          style != null ? style.buttonAssetPath() : null
      );
    } else {
      path = firstNonBlank(
          item != null ? item.buttonAssetPath() : null,
          style != null ? style.buttonAssetPath() : null
      );
    }
    return path;
  }

  private double resolveCoordinate(double value, double total) {
    return value <= 1.0 ? total * value : value;
  }

  private double resolveLocalCoordinate(double value, double total) {
    return Math.abs(value) <= 1.0 ? total * value : value;
  }

  private double resolveSize(double value, double total) {
    return value <= 1.0 ? total * Math.max(0, value) : value;
  }

  private File getThumbnailFile(LoadMenuScene scene) {
    String dir = scene.getSaveDirectory();
    String name = scene.getSelectedName();
    if (dir == null || name == null) return null;
    File f = new File(dir, name + ".png");
    return f.exists() ? f : null;
  }

  private boolean shouldShowLoadSidePreview(LoadMenuScene scene, MenuItemSpec[] specs) {
    if (scene == null) return true;
    MenuItemSpec selectedSpec = scene.getSelected() >= 0 ? scene.getMenuItemSpec(scene.getSelected()) : null;
    List<MenuItemSpec> candidates = collectSidePreviewCandidates(specs, scene.getMenuItemSpec(0), selectedSpec);
    return resolveSidePreviewPreference(candidates);
  }

  private boolean shouldShowSaveSidePreview(SaveMenuScene scene, MenuItemSpec[] specs) {
    if (scene == null) return true;
    List<MenuItemSpec> candidates = collectSidePreviewCandidates(specs, scene.getMenuItemSpec(0), scene.getMenuItemSpec(scene.getSelected()));
    return resolveSidePreviewPreference(candidates);
  }

  private List<MenuItemSpec> collectSidePreviewCandidates(MenuItemSpec[] specs, MenuItemSpec... fallbacks) {
    List<MenuItemSpec> out = new ArrayList<>();
    if (specs != null) {
      for (MenuItemSpec spec : specs) {
        if (spec != null) out.add(spec);
      }
    }
    if (out.isEmpty() && fallbacks != null) {
      for (MenuItemSpec fallback : fallbacks) {
        if (fallback != null) out.add(fallback);
      }
    }
    return out;
  }

  private boolean resolveSidePreviewPreference(List<MenuItemSpec> candidates) {
    if (candidates == null || candidates.isEmpty()) return true;

    boolean sawExplicitPreference = false;
    for (MenuItemSpec item : candidates) {
      Boolean show = parseItemExtraBooleanNullable(item, "showSidePreview");
      if (show != null) {
        sawExplicitPreference = true;
        if (!show) return false;
      }
      Boolean alias = parseItemExtraBooleanNullable(item, "sidePreview");
      if (alias != null) {
        sawExplicitPreference = true;
        if (!alias) return false;
      }
    }
    if (sawExplicitPreference) return true;

    // Explicit absolute bounds are typically authored against full-width card layouts.
    // When side preview defaults on, cards are clamped into a narrower list area and can overlap.
    for (MenuItemSpec item : candidates) {
      if (hasExplicitBounds(item)) return false;
    }
    return true;
  }

  private boolean hasExplicitBounds(MenuItemSpec itemSpec) {
    if (itemSpec == null) return false;
    return itemSpec.boundsX() != null
        && itemSpec.boundsY() != null
        && itemSpec.boundsWidth() != null
        && itemSpec.boundsHeight() != null;
  }

  private boolean areLoadControlsVisible(MenuItemSpec template) {
    return parseItemExtraBoolean(template, "controlsVisible", true)
        && parseItemExtraBoolean(template, "showControls", true);
  }

  private double resolveLoadListAreaWidth(LoadMenuScene scene, MenuItemSpec[] specs, double viewportWidth) {
    return shouldShowLoadSidePreview(scene, specs) ? viewportWidth * 0.6 : viewportWidth;
  }

  private boolean parseItemExtraBoolean(MenuItemSpec itemSpec, String key, boolean defaultValue) {
    Boolean parsed = parseItemExtraBooleanNullable(itemSpec, key);
    return parsed != null ? parsed : defaultValue;
  }

  private Boolean parseItemExtraBooleanNullable(MenuItemSpec itemSpec, String key) {
    if (itemSpec == null || key == null || itemSpec.extras() == null) return null;
    String raw = itemSpec.extras().get(key);
    if (raw == null || raw.isBlank()) return null;
    return switch (raw.trim().toLowerCase()) {
      case "true", "1", "yes", "y", "on" -> Boolean.TRUE;
      case "false", "0", "no", "n", "off" -> Boolean.FALSE;
      default -> null;
    };
  }

  private int parseItemExtraInt(MenuItemSpec itemSpec, String key, int defaultValue) {
    if (itemSpec == null || key == null || itemSpec.extras() == null) return defaultValue;
    String raw = itemSpec.extras().get(key);
    if (raw == null || raw.isBlank()) return defaultValue;
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
      return defaultValue;
    }
  }

  private void drawLoadMenuControls(
      LoadMenuScene scene,
      MenuLayoutSpec layout,
      MenuItemSpec[] visibleSpecs,
      int visibleStartIndex,
      int visibleDrawCount,
      double w,
      double h,
      double listAreaWidth
  ) {
    if (scene == null) return;
    MenuItemSpec template = resolveLoadTemplateItem(scene, visibleSpecs);
    if (!areLoadControlsVisible(template)) return;

    int pageCount = scene.getPageCount();
    int currentPage = scene.getCurrentPageIndex();
    boolean canPageLeft = currentPage > 0;
    boolean canPageRight = currentPage < Math.max(0, pageCount - 1);

    Rect leftRect = resolveLoadCycleLeftRect(template, w, h);
    Rect rightRect = resolveLoadCycleRightRect(template, w, h);
    Rect trackRect = resolveLoadPageTrackRect(template, w, h);
    Rect selectorRect = resolveLoadPageSelectorRect(template, trackRect, scene.getPageProgress01());
    Rect favoritesRect = resolveLoadFavoritesButtonRect(template, w, h);

    drawLoadControlImage(resolveLoadCycleLeftAsset(template, canPageLeft), leftRect);
    drawLoadControlImage(resolveLoadCycleRightAsset(template, canPageRight), rightRect);
    drawLoadControlImage(resolveLoadPageTrackAsset(template), trackRect);
    drawLoadControlImage(resolveLoadPageSelectorAsset(template), selectorRect);
    drawLoadControlImage(resolveLoadFavoritesButtonAsset(template, scene.isFavoritesOnly()), favoritesRect);

    String slotFavoriteAsset = resolveLoadSlotFavoriteIconAsset(template);
    Image slotFavoriteIcon = loadImage(slotFavoriteAsset);
    if (slotFavoriteIcon == null || slotFavoriteIcon.isError()) return;
    if (visibleDrawCount <= 0) return;

    MenuItemSpec[] specs = visibleSpecs;
    if (specs == null || specs.length < visibleDrawCount) {
      specs = new MenuItemSpec[visibleDrawCount];
      for (int i = 0; i < visibleDrawCount; i++) {
        specs[i] = scene.getMenuItemSpec(visibleStartIndex + i);
      }
    }
    for (int i = 0; i < visibleDrawCount; i++) {
      int globalIndex = visibleStartIndex + i;
      if (globalIndex < 0 || globalIndex >= scene.getItemCount()) continue;
      Rect itemRect = resolveItemRect(i, visibleDrawCount, specs[i], specs, layout, 0, listAreaWidth, h);
      Rect iconRect = resolveLoadSlotFavoriteIconRect(template, itemRect);
      double prevAlpha = gc.getGlobalAlpha();
      gc.setGlobalAlpha(scene.isFavoriteAt(globalIndex) ? 1.0 : 0.28);
      gc.drawImage(slotFavoriteIcon, iconRect.x(), iconRect.y(), iconRect.w(), iconRect.h());
      gc.setGlobalAlpha(prevAlpha);
    }
  }

  private void drawLoadControlImage(String assetPath, Rect target) {
    if (target == null || target.w() <= 0 || target.h() <= 0) return;
    Image img = loadImage(assetPath);
    if (img == null || img.isError()) return;
    gc.drawImage(img, target.x(), target.y(), target.w(), target.h());
  }

  private MenuItemSpec resolveLoadTemplateItem(LoadMenuScene scene, MenuItemSpec[] visibleSpecs) {
    if (visibleSpecs != null && visibleSpecs.length > 0 && visibleSpecs[0] != null) return visibleSpecs[0];
    if (scene == null) return null;
    MenuItemSpec first = scene.getMenuItemSpec(0);
    if (first != null) return first;
    return scene.getMenuItemSpec(scene.getSelected());
  }

  private Rect resolveLoadCycleLeftRect(MenuItemSpec itemSpec, double w, double h) {
    return resolveLoadControlRect(itemSpec, "cycleLeft", 0.084375, 0.48148, 0.01979, 0.04630, w, h);
  }

  private Rect resolveLoadCycleRightRect(MenuItemSpec itemSpec, double w, double h) {
    return resolveLoadControlRect(itemSpec, "cycleRight", 0.702083, 0.48148, 0.01979, 0.04630, w, h);
  }

  private Rect resolveLoadPageTrackRect(MenuItemSpec itemSpec, double w, double h) {
    return resolveLoadControlRect(itemSpec, "pageTrack", 0.23906, 0.74444, 0.28854, 0.06019, w, h);
  }

  private Rect resolveLoadFavoritesButtonRect(MenuItemSpec itemSpec, double w, double h) {
    return resolveLoadControlRect(itemSpec, "favoritesButton", 0.51094, 0.74537, 0.04635, 0.05556, w, h);
  }

  private Rect resolveLoadPageSelectorRect(MenuItemSpec itemSpec, Rect trackRect, double progress01) {
    if (trackRect == null) return new Rect(0, 0, 1, 1);
    Double selectorWRaw = parseExtraDouble(itemSpec, "pageSelectorWidth");
    Double selectorHRaw = parseExtraDouble(itemSpec, "pageSelectorHeight");
    double selectorW = selectorWRaw == null
        ? trackRect.w() * 0.0903
        : (selectorWRaw <= 1.0 ? trackRect.w() * selectorWRaw : selectorWRaw);
    double selectorH = selectorHRaw == null
        ? trackRect.h() * 0.8769
        : (selectorHRaw <= 1.0 ? trackRect.h() * selectorHRaw : selectorHRaw);
    selectorW = clamp(selectorW, 10.0, Math.max(10.0, trackRect.w()));
    selectorH = clamp(selectorH, 10.0, Math.max(10.0, trackRect.h() * 1.4));
    double t = clamp01(progress01);
    double x = trackRect.x() + t * Math.max(0.0, trackRect.w() - selectorW);
    double y = trackRect.y() + (trackRect.h() - selectorH) / 2.0;
    return new Rect(x, y, selectorW, selectorH);
  }

  private Rect resolveLoadSlotFavoriteIconRect(MenuItemSpec itemSpec, Rect itemRect) {
    if (itemRect == null) return new Rect(0, 0, 1, 1);
    Double xVal = parseExtraDouble(itemSpec, "slotFavoriteX");
    Double yVal = parseExtraDouble(itemSpec, "slotFavoriteY");
    Double wVal = parseExtraDouble(itemSpec, "slotFavoriteWidth");
    Double hVal = parseExtraDouble(itemSpec, "slotFavoriteHeight");
    double x = itemRect.x() + resolveCoordinate(xVal != null ? xVal : 0.0335, itemRect.w());
    double y = itemRect.y() + resolveCoordinate(yVal != null ? yVal : 0.002, itemRect.h());
    double w = resolveSize(wVal != null ? wVal : 0.0928, itemRect.w());
    double h = resolveSize(hVal != null ? hVal : 0.1455, itemRect.h());
    w = clamp(w, 8, Math.max(8, itemRect.w()));
    h = clamp(h, 8, Math.max(8, itemRect.h()));
    x = clamp(x, itemRect.x(), itemRect.x() + Math.max(0.0, itemRect.w() - w));
    y = clamp(y, itemRect.y(), itemRect.y() + Math.max(0.0, itemRect.h() - h));
    return new Rect(x, y, w, h);
  }

  private Rect resolveLoadControlRect(
      MenuItemSpec itemSpec,
      String keyPrefix,
      double defaultX,
      double defaultY,
      double defaultW,
      double defaultH,
      double viewportW,
      double viewportH
  ) {
    Double xVal = parseExtraDouble(itemSpec, keyPrefix + "X");
    Double yVal = parseExtraDouble(itemSpec, keyPrefix + "Y");
    Double wVal = parseExtraDouble(itemSpec, keyPrefix + "Width");
    Double hVal = parseExtraDouble(itemSpec, keyPrefix + "Height");
    double x = resolveCoordinate(xVal != null ? xVal : defaultX, viewportW);
    double y = resolveCoordinate(yVal != null ? yVal : defaultY, viewportH);
    double w = resolveSize(wVal != null ? wVal : defaultW, viewportW);
    double h = resolveSize(hVal != null ? hVal : defaultH, viewportH);
    w = clamp(w, 8, Math.max(8, viewportW));
    h = clamp(h, 8, Math.max(8, viewportH));
    x = clamp(x, 0, Math.max(0, viewportW - w));
    y = clamp(y, 0, Math.max(0, viewportH - h));
    return new Rect(x, y, w, h);
  }

  private String resolveLoadCycleLeftAsset(MenuItemSpec itemSpec, boolean active) {
    return active
        ? firstNonBlank(extra(itemSpec, "cycleLeftActiveAsset"), LOAD_CYCLE_LEFT_ACTIVE_ASSET)
        : firstNonBlank(extra(itemSpec, "cycleLeftInactiveAsset"), LOAD_CYCLE_LEFT_INACTIVE_ASSET);
  }

  private String resolveLoadCycleRightAsset(MenuItemSpec itemSpec, boolean active) {
    return active
        ? firstNonBlank(extra(itemSpec, "cycleRightActiveAsset"), LOAD_CYCLE_RIGHT_ACTIVE_ASSET)
        : firstNonBlank(extra(itemSpec, "cycleRightInactiveAsset"), LOAD_CYCLE_RIGHT_INACTIVE_ASSET);
  }

  private String resolveLoadPageTrackAsset(MenuItemSpec itemSpec) {
    return firstNonBlank(extra(itemSpec, "pageTrackAsset"), LOAD_PAGE_TRACK_ASSET);
  }

  private String resolveLoadPageSelectorAsset(MenuItemSpec itemSpec) {
    return firstNonBlank(extra(itemSpec, "pageSelectorAsset"), LOAD_PAGE_SELECTOR_ASSET);
  }

  private String resolveLoadFavoritesButtonAsset(MenuItemSpec itemSpec, boolean active) {
    return active
        ? firstNonBlank(extra(itemSpec, "favoritesButtonActiveAsset"), LOAD_FAVORITES_BUTTON_ACTIVE_ASSET)
        : firstNonBlank(extra(itemSpec, "favoritesButtonInactiveAsset"), LOAD_FAVORITES_BUTTON_INACTIVE_ASSET);
  }

  private String resolveLoadSlotFavoriteIconAsset(MenuItemSpec itemSpec) {
    return firstNonBlank(extra(itemSpec, "slotFavoriteAsset"), LOAD_SLOT_FAVORITE_ICON_ASSET);
  }

  private String extra(MenuItemSpec itemSpec, String key) {
    if (itemSpec == null || key == null || itemSpec.extras() == null) return null;
    return itemSpec.extras().get(key);
  }

  private void drawInlineSaveSlotPreviews(
      SaveMenuScene scene,
      MenuLayoutSpec layout,
      MenuItemSpec[] specs,
      double areaX,
      double areaWidth,
      double h
  ) {
    if (scene == null) return;
    List<String> saves = scene.getSaves();
    int count = scene.getItemCount();
    for (int i = 0; i < count; i++) {
      MenuItemSpec spec = specs != null && i < specs.length ? specs[i] : scene.getMenuItemSpec(i);
      if (!isInlineSlotPreviewEnabled(spec, true)) continue;
      Rect itemRect = resolveItemRect(i, count, spec, specs, layout, areaX, areaWidth, h);
      String previewPath = null;
      if (i == 0) {
        previewPath = scene.getCurrentBackgroundPreviewPath();
      } else {
        int saveIndex = i - 1;
        if (saveIndex >= 0 && saveIndex < saves.size()) {
          File thumb = new File(scene.getSaveDirectory(), saves.get(saveIndex) + ".png");
          if (thumb.exists()) previewPath = thumb.getAbsolutePath();
        }
      }
      drawInlineSlotPreview(itemRect, spec, previewPath, i == scene.getSelected(), i == 0 ? Localization.t("save.new") : Localization.t("load.no_preview"));
    }
  }

  private void drawInlineLoadSlotPreviews(
      LoadMenuScene scene,
      MenuLayoutSpec layout,
      MenuItemSpec[] specs,
      int startIndex,
      int visibleCount,
      double areaX,
      double areaWidth,
      double h
  ) {
    if (scene == null) return;
    List<String> saves = scene.getSaves();
    int count = Math.max(0, Math.min(visibleCount, saves.size() - Math.max(0, startIndex)));
    for (int i = 0; i < count; i++) {
      int globalIndex = startIndex + i;
      MenuItemSpec spec = specs != null && i < specs.length ? specs[i] : scene.getMenuItemSpec(globalIndex);
      if (!isInlineSlotPreviewEnabled(spec, true)) continue;
      Rect itemRect = resolveItemRect(i, count, spec, specs, layout, areaX, areaWidth, h);
      String previewPath = null;
      if (globalIndex >= 0 && globalIndex < saves.size()) {
        File thumb = new File(scene.getSaveDirectory(), saves.get(globalIndex) + ".png");
        if (thumb.exists()) previewPath = thumb.getAbsolutePath();
      }
      drawInlineSlotPreview(itemRect, spec, previewPath, globalIndex == scene.getSelected(), Localization.t("load.no_preview"));
    }
  }

  private void drawInlineSlotPreview(Rect itemRect, MenuItemSpec itemSpec, String previewPath, boolean selected, String fallbackText) {
    if (itemRect == null) return;
    Rect previewRect = resolveInlineSlotPreviewRect(itemSpec, itemRect);
    if (previewRect.w() <= 1 || previewRect.h() <= 1) return;
    String fitMode = resolveSlotPreviewFitMode(itemSpec);
    boolean containFit = "contain".equals(fitMode) || "fit".equals(fitMode);

    gc.setFill(Color.rgb(6, 9, 14, 0.95));
    gc.fillRoundRect(previewRect.x(), previewRect.y(), previewRect.w(), previewRect.h(), 7, 7);

    Image previewImage = loadImage(previewPath);
    if (previewImage != null && !previewImage.isError()) {
      if (containFit) {
        drawImageContain(previewImage, previewRect);
      } else {
        drawImageCover(previewImage, previewRect);
      }
    } else {
      Image placeholder = itemSpec != null ? loadImage(itemSpec.slotPreviewPlaceholderAssetPath()) : null;
      if (placeholder != null && !placeholder.isError()) {
        if (containFit) {
          drawImageContain(placeholder, previewRect);
        } else {
          drawImageCover(placeholder, previewRect);
        }
      } else {
        gc.setFill(Color.rgb(32, 36, 48, 0.95));
        gc.fillRoundRect(previewRect.x(), previewRect.y(), previewRect.w(), previewRect.h(), 7, 7);
        gc.setFill(Color.rgb(205, 212, 225, 0.85));
        gc.setFont(theme.getHintFont());
        String txt = firstNonBlank(
            extra(itemSpec, "slotPreviewFallbackText"),
            extra(itemSpec, "previewFallbackText"),
            fallbackText
        );
        if (txt == null || txt.isBlank()) txt = Localization.t("load.no_preview");
        double tw = measure(txt, theme.getHintFont());
        double tx = previewRect.x() + Math.max(6, (previewRect.w() - tw) / 2.0);
        double ty = previewRect.y() + previewRect.h() * 0.56;
        gc.fillText(txt, tx, ty);
      }
    }

    Image frame = itemSpec != null ? loadImage(itemSpec.slotPreviewFrameAssetPath()) : null;
    if (frame != null && !frame.isError()) {
      gc.drawImage(frame, previewRect.x(), previewRect.y(), previewRect.w(), previewRect.h());
    } else {
      gc.setStroke(selected ? Color.rgb(170, 220, 255, 0.95) : Color.rgb(150, 170, 205, 0.7));
      gc.setLineWidth(selected ? 1.8 : 1.1);
      gc.strokeRoundRect(previewRect.x(), previewRect.y(), previewRect.w(), previewRect.h(), 7, 7);
    }
  }

  private String resolveSlotPreviewFitMode(MenuItemSpec itemSpec) {
    if (itemSpec == null || itemSpec.extras() == null) return "cover";
    String raw = firstNonBlank(itemSpec.extras().get("slotPreviewFit"), itemSpec.extras().get("previewFit"));
    if (raw == null || raw.isBlank()) return "cover";
    String normalized = raw.trim().toLowerCase();
    if ("contain".equals(normalized) || "fit".equals(normalized)) return "contain";
    return "cover";
  }

  private void drawImageCover(Image image, Rect target) {
    if (image == null || target == null) return;
    double iw = image.getWidth();
    double ih = image.getHeight();
    if (iw <= 0 || ih <= 0) return;

    double targetRatio = target.w() / target.h();
    double imageRatio = iw / ih;
    double sx = 0;
    double sy = 0;
    double sw = iw;
    double sh = ih;
    if (imageRatio > targetRatio) {
      sw = ih * targetRatio;
      sx = (iw - sw) / 2.0;
    } else {
      sh = iw / targetRatio;
      sy = (ih - sh) / 2.0;
    }
    gc.drawImage(image, sx, sy, sw, sh, target.x(), target.y(), target.w(), target.h());
  }

  private void drawImageContain(Image image, Rect target) {
    if (image == null || target == null) return;
    double iw = image.getWidth();
    double ih = image.getHeight();
    if (iw <= 0 || ih <= 0) return;
    double scale = Math.min(target.w() / iw, target.h() / ih);
    double dw = iw * scale;
    double dh = ih * scale;
    double dx = target.x() + (target.w() - dw) / 2.0;
    double dy = target.y() + (target.h() - dh) / 2.0;
    gc.drawImage(image, dx, dy, dw, dh);
  }

  private void drawPreviewResource(String path, double w, double h) {
    try {
      var url = getClass().getClassLoader().getResource(path);
      if (url == null) { drawPreviewPlaceholder(w, h); return; }
      Image img = new Image(url.toExternalForm());
      drawPreviewImage(img, w, h);
    } catch (Exception e) {
      drawPreviewPlaceholder(w, h);
    }
  }

  private void drawPreviewFile(File file, double w, double h) {
    try {
      Image img = new Image(file.toURI().toString());
      drawPreviewImage(img, w, h);
    } catch (Exception e) {
      drawPreviewPlaceholder(w, h);
    }
  }

  private void drawPreviewImage(Image img, double w, double h) {
    double panelX = w * 0.65;
    double panelY = h * 0.25;
    double panelW = w * 0.3;
    double panelH = h * 0.5;
    gc.setFill(Color.rgb(255,255,255,0.1));
    gc.fillRoundRect(panelX - 8, panelY - 8, panelW + 16, panelH + 16, 12, 12);
    double scale = Math.min(panelW / img.getWidth(), panelH / img.getHeight());
    double iw = img.getWidth() * scale;
    double ih = img.getHeight() * scale;
    double ix = panelX + (panelW - iw) / 2;
    double iy = panelY + (panelH - ih) / 2;
    gc.drawImage(img, ix, iy, iw, ih);
  }

  private void drawPreviewPlaceholder(double w, double h) {
    double panelX = w * 0.65;
    double panelY = h * 0.25;
    double panelW = w * 0.3;
    double panelH = h * 0.5;
    gc.setFill(Color.rgb(255,255,255,0.1));
    gc.fillRoundRect(panelX - 8, panelY - 8, panelW + 16, panelH + 16, 12, 12);
    gc.setFill(Color.GRAY);
    gc.setFont(theme.getItemFont());
    drawCenteredText(Localization.t("load.no_preview"), panelX + panelW/2, panelY + panelH/2, theme.getItemFont(), Color.GRAY);
  }

  private void drawPreviewMetadata(String scenarioId, Long timestampMs, Integer nodeIndex, double w, double h) {
    double panelX = w * 0.65;
    double panelY = h * 0.25;
    double panelH = h * 0.5;
    double textY = panelY + panelH + 20;
    gc.setFill(Color.LIGHTGRAY);
    gc.setFont(theme.getHintFont());
    String ts = timestampMs != null ? formatTimestamp(timestampMs) : "";
    String line1 = (ts.isEmpty() ? "" : ts);
    String line2 = (scenarioId != null ? (Localization.t("meta.scenario") + ": " + scenarioId) : "");
    String line3 = (nodeIndex != null ? (Localization.t("meta.node") + ": " + nodeIndex) : "");
    double x = panelX;
    if (!line1.isEmpty()) gc.fillText(line1, x, textY);
    if (!line2.isEmpty()) gc.fillText(line2, x, textY + 18);
    if (!line3.isEmpty()) gc.fillText(line3, x, textY + 36);
  }

  private String formatTimestamp(long millis) {
    try {
      java.time.Instant inst = java.time.Instant.ofEpochMilli(millis);
      java.time.ZonedDateTime z = java.time.ZonedDateTime.ofInstant(inst, java.time.ZoneId.systemDefault());
      return z.toLocalDate().toString() + " " + z.toLocalTime().withNano(0).toString();
    } catch (Exception e) { return Long.toString(millis); }
  }

  private double[] sliderGeometry(int index, int count, MenuItemSpec item, MenuItemSpec[] itemSpecs, MenuLayoutSpec layout, double w, double h) {
    Rect rowRect = resolveItemRect(index, count, item, itemSpecs, layout, 0, w, h);
    double padX = Math.max(20, rowRect.w() * 0.16);
    double defaultSliderX = rowRect.x() + padX;
    double defaultSliderW = Math.max(140, rowRect.w() - (padX * 2));
    double defaultSliderY = rowRect.y() + rowRect.h() * 0.7;

    Double sliderXRaw = parseExtraDouble(item, "sliderX");
    Double sliderYRaw = parseExtraDouble(item, "sliderY");
    Double sliderWRaw = parseExtraDouble(item, "sliderWidth");

    double sliderX = sliderXRaw != null ? resolveCoordinate(sliderXRaw, w) : defaultSliderX;
    double sliderY = sliderYRaw != null ? resolveCoordinate(sliderYRaw, h) : defaultSliderY;
    double sliderW = sliderWRaw != null ? resolveSize(sliderWRaw, w) : defaultSliderW;

    sliderW = clamp(sliderW, 12, Math.max(12, w));
    sliderX = clamp(sliderX, 0, Math.max(0, w - sliderW));
    sliderY = clamp(sliderY, 0, Math.max(0, h - 1));
    return new double[]{sliderX, sliderY, sliderW};
  }

  public double computeSettingsSliderValue01(SettingsScene scene, int itemIndex, double canvasW, double canvasH, double mouseX) {
    if (scene == null || itemIndex < 0) return 0;
    int count = scene.itemCount();
    if (itemIndex >= count) return 0;
    MenuItemSpec item = scene.getMenuItemSpec(itemIndex);
    MenuLayoutSpec layout = scene.getMenuLayout();
    MenuItemSpec[] itemSpecs = new MenuItemSpec[count];
    for (int i = 0; i < count; i++) itemSpecs[i] = scene.getMenuItemSpec(i);
    double[] geo = sliderGeometry(itemIndex, count, item, itemSpecs, layout, canvasW, canvasH);
    double sliderX = geo[0];
    double sliderW = geo[2];
    double v = (mouseX - sliderX) / sliderW;
    if (v < 0) v = 0;
    if (v > 1) v = 1;
    return v;
  }

  public boolean isSettingsSliderResetHit(
      SettingsScene scene,
      int itemIndex,
      double canvasW,
      double canvasH,
      double mouseX,
      double mouseY
  ) {
    if (scene == null || itemIndex < 0) return false;
    int count = scene.itemCount();
    if (itemIndex >= count || !scene.hasSliderAt(itemIndex)) return false;
    MenuItemSpec item = scene.getMenuItemSpec(itemIndex);
    MenuLayoutSpec layout = scene.getMenuLayout();
    MenuItemSpec[] itemSpecs = new MenuItemSpec[count];
    for (int i = 0; i < count; i++) itemSpecs[i] = scene.getMenuItemSpec(i);
    double[] geo = sliderGeometry(itemIndex, count, item, itemSpecs, layout, canvasW, canvasH);
    Rect resetRect = resolveSettingsSliderResetRect(item, true, geo[0], geo[1], geo[2], canvasW, canvasH);
    return resetRect != null && resetRect.contains(mouseX, mouseY);
  }

  private void drawSlider(double x, double y, double w, double value01, boolean highlight, MenuItemSpec item) {
    double fill = clamp01(value01);
    Double sliderTrackHeight = parseExtraDouble(item, "sliderTrackHeight");
    boolean showFill = parseItemExtraBoolean(item, "sliderShowFill", true);

    String trackAssetPath = firstNonBlank(
        extra(item, "sliderTrackAsset"),
        extra(item, "sliderBaseAsset")
    );
    String fillAssetPath = firstNonBlank(
        highlight ? extra(item, "sliderFillActiveAsset") : null,
        !highlight ? extra(item, "sliderFillInactiveAsset") : null,
        extra(item, "sliderFillAsset")
    );
    String knobAssetPath = firstNonBlank(
        highlight ? extra(item, "sliderKnobActiveAsset") : null,
        !highlight ? extra(item, "sliderKnobInactiveAsset") : null,
        extra(item, "sliderKnobAsset"),
        !highlight ? extra(item, "sliderKnobActiveAsset") : null
    );

    Image trackImage = loadImage(trackAssetPath);
    double trackH = sliderTrackHeight != null
        ? Math.max(2.0, sliderTrackHeight)
        : (trackImage != null && !trackImage.isError() && trackImage.getHeight() > 0
            ? trackImage.getHeight()
            : 8.0);

    if (trackImage != null && !trackImage.isError()) {
      gc.drawImage(trackImage, x, y, w, trackH);
    } else {
      gc.setFill(Color.rgb(255, 255, 255, 0.15));
      gc.fillRoundRect(x, y, w, trackH, 6, 6);
    }

    if (showFill) {
      Image fillImage = loadImage(fillAssetPath);
      if (fillImage != null && !fillImage.isError()) {
        gc.save();
        gc.beginPath();
        gc.rect(x, y, w * fill, trackH);
        gc.closePath();
        gc.clip();
        gc.drawImage(fillImage, x, y, w, trackH);
        gc.restore();
      } else {
        gc.setFill(highlight ? theme.getItemSelectedColor() : theme.getItemColor());
        gc.fillRoundRect(x, y, w * fill, trackH, 6, 6);
      }
    }

    Image knobImage = loadImage(knobAssetPath);
    Double knobWidth = parseExtraDouble(item, "sliderKnobWidth");
    Double knobHeight = parseExtraDouble(item, "sliderKnobHeight");
    Double knobOffsetX = parseExtraDouble(item, "sliderKnobOffsetX");
    Double knobOffsetY = parseExtraDouble(item, "sliderKnobOffsetY");

    double knobW = knobWidth != null
        ? Math.max(2.0, knobWidth)
        : (knobImage != null && !knobImage.isError() && knobImage.getWidth() > 0 ? knobImage.getWidth() : 12.0);
    double knobH = knobHeight != null
        ? Math.max(2.0, knobHeight)
        : (knobImage != null && !knobImage.isError() && knobImage.getHeight() > 0 ? knobImage.getHeight() : 12.0);

    double knobX = x + w * fill - knobW * 0.5 + (knobOffsetX != null ? knobOffsetX : 0.0);
    double knobY = y + (trackH - knobH) * 0.5 + (knobOffsetY != null ? knobOffsetY : 0.0);
    if (knobImage != null && !knobImage.isError()) {
      gc.drawImage(knobImage, knobX, knobY, knobW, knobH);
    } else {
      gc.setFill(Color.WHITE);
      gc.fillOval(knobX, knobY, knobW, knobH);
    }
  }

  private void drawSettingsSliderReset(MenuItemSpec item, boolean highlight, Rect rect) {
    if (item == null || rect == null) return;
    String resetAssetPath = firstNonBlank(
        highlight ? extra(item, "sliderResetActiveAsset") : null,
        !highlight ? extra(item, "sliderResetInactiveAsset") : null,
        extra(item, "sliderResetAsset"),
        !highlight ? extra(item, "sliderResetActiveAsset") : null
    );
    Image resetImage = loadImage(resetAssetPath);
    if (resetImage != null && !resetImage.isError()) {
      gc.drawImage(resetImage, rect.x(), rect.y(), rect.w(), rect.h());
      return;
    }
    gc.setFill(highlight ? Color.rgb(229, 101, 94, 0.95) : Color.rgb(220, 212, 154, 0.95));
    gc.fillRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 4, 4);
    gc.setFill(Color.rgb(18, 18, 18, 0.95));
    gc.setFont(Font.font("SansSerif", FontWeight.BOLD, Math.max(10, Math.min(20, rect.h() * 0.58))));
    gc.fillText("R", rect.x() + rect.w() * 0.33, rect.y() + rect.h() * 0.72);
  }

  private Rect resolveSettingsSliderResetRect(
      MenuItemSpec item,
      boolean highlight,
      double sliderX,
      double sliderY,
      double sliderW,
      double canvasW,
      double canvasH
  ) {
    if (item == null) return null;
    String resetAssetPath = firstNonBlank(
        highlight ? extra(item, "sliderResetActiveAsset") : null,
        !highlight ? extra(item, "sliderResetInactiveAsset") : null,
        extra(item, "sliderResetAsset"),
        !highlight ? extra(item, "sliderResetActiveAsset") : null
    );
    Double resetXRaw = parseExtraDouble(item, "sliderResetX");
    Double resetYRaw = parseExtraDouble(item, "sliderResetY");
    Double resetWidthRaw = parseExtraDouble(item, "sliderResetWidth");
    Double resetHeightRaw = parseExtraDouble(item, "sliderResetHeight");
    if ((resetAssetPath == null || resetAssetPath.isBlank())
        && resetWidthRaw == null && resetHeightRaw == null
        && resetXRaw == null && resetYRaw == null) {
      return null;
    }

    Image resetImage = loadImage(resetAssetPath);
    Image trackImage = loadImage(firstNonBlank(extra(item, "sliderTrackAsset"), extra(item, "sliderBaseAsset")));
    Double sliderTrackHeight = parseExtraDouble(item, "sliderTrackHeight");
    double trackH = sliderTrackHeight != null
        ? Math.max(2.0, sliderTrackHeight)
        : (trackImage != null && !trackImage.isError() && trackImage.getHeight() > 0
            ? trackImage.getHeight()
            : 8.0);

    double resetW = resetWidthRaw != null
        ? Math.max(2.0, resolveSize(resetWidthRaw, canvasW))
        : (resetImage != null && !resetImage.isError() && resetImage.getWidth() > 0
            ? resetImage.getWidth()
            : 24.0);
    double resetH = resetHeightRaw != null
        ? Math.max(2.0, resolveSize(resetHeightRaw, canvasH))
        : (resetImage != null && !resetImage.isError() && resetImage.getHeight() > 0
            ? resetImage.getHeight()
            : 24.0);

    double defaultX = sliderX - resetW - 8.0;
    double defaultY = sliderY + (trackH - resetH) * 0.5;
    double x = resetXRaw != null ? resolveCoordinate(resetXRaw, canvasW) : defaultX;
    double y = resetYRaw != null ? resolveCoordinate(resetYRaw, canvasH) : defaultY;
    x = clamp(x, 0, Math.max(0, canvasW - resetW));
    y = clamp(y, 0, Math.max(0, canvasH - resetH));
    return new Rect(x, y, resetW, resetH);
  }

  private void drawSettingsToggle(MenuItemSpec item, boolean enabled, boolean highlight, Rect rect) {
    if (item == null || rect == null) return;
    String assetPath = firstNonBlank(
        enabled ? extra(item, "toggleCheckedAsset") : null,
        !enabled ? extra(item, "toggleUncheckedAsset") : null,
        enabled ? extra(item, "toggleUncheckedAsset") : null
    );
    Image image = loadImage(assetPath);
    if (image != null && !image.isError()) {
      gc.drawImage(image, rect.x(), rect.y(), rect.w(), rect.h());
      return;
    }
    gc.setFill(enabled ? Color.rgb(213, 108, 94, 0.95) : Color.rgb(232, 225, 178, 0.92));
    gc.fillRect(rect.x(), rect.y(), rect.w(), rect.h());
    gc.setStroke(highlight ? Color.rgb(18, 18, 18, 0.95) : Color.rgb(38, 38, 38, 0.7));
    gc.setLineWidth(highlight ? 2.2 : 1.2);
    gc.strokeRect(rect.x(), rect.y(), rect.w(), rect.h());
    if (enabled) {
      gc.setStroke(Color.rgb(15, 15, 15, 0.95));
      gc.setLineWidth(Math.max(2.0, rect.w() * 0.12));
      gc.strokeLine(rect.x() + rect.w() * 0.18, rect.y() + rect.h() * 0.56, rect.x() + rect.w() * 0.42, rect.y() + rect.h() * 0.82);
      gc.strokeLine(rect.x() + rect.w() * 0.42, rect.y() + rect.h() * 0.82, rect.x() + rect.w() * 0.84, rect.y() + rect.h() * 0.16);
    }
  }

  private Rect resolveSettingsToggleRect(
      MenuItemSpec item,
      int itemIndex,
      int count,
      MenuItemSpec[] itemSpecs,
      MenuLayoutSpec layout,
      double canvasW,
      double canvasH
  ) {
    if (item == null) return null;
    Rect rowRect = resolveItemRect(itemIndex, count, item, itemSpecs, layout, 0, canvasW, canvasH);
    Double toggleXRaw = parseExtraDouble(item, "toggleX");
    Double toggleYRaw = parseExtraDouble(item, "toggleY");
    Double toggleWidthRaw = parseExtraDouble(item, "toggleWidth");
    Double toggleHeightRaw = parseExtraDouble(item, "toggleHeight");
    String checkedAsset = extra(item, "toggleCheckedAsset");
    String uncheckedAsset = extra(item, "toggleUncheckedAsset");
    if ((checkedAsset == null || checkedAsset.isBlank())
        && (uncheckedAsset == null || uncheckedAsset.isBlank())
        && toggleXRaw == null && toggleYRaw == null && toggleWidthRaw == null && toggleHeightRaw == null) {
      return null;
    }

    Image toggleImage = loadImage(firstNonBlank(checkedAsset, uncheckedAsset));
    double toggleW = toggleWidthRaw != null
        ? Math.max(2.0, resolveSize(toggleWidthRaw, canvasW))
        : (toggleImage != null && !toggleImage.isError() && toggleImage.getWidth() > 0 ? toggleImage.getWidth() : 26.0);
    double toggleH = toggleHeightRaw != null
        ? Math.max(2.0, resolveSize(toggleHeightRaw, canvasH))
        : (toggleImage != null && !toggleImage.isError() && toggleImage.getHeight() > 0 ? toggleImage.getHeight() : 26.0);
    double defaultX = rowRect.x() + rowRect.w() - toggleW - 8.0;
    double defaultY = rowRect.y() + Math.max(0.0, (rowRect.h() - toggleH) * 0.5);
    double x = toggleXRaw != null ? resolveCoordinate(toggleXRaw, canvasW) : defaultX;
    double y = toggleYRaw != null ? resolveCoordinate(toggleYRaw, canvasH) : defaultY;
    x = clamp(x, 0, Math.max(0, canvasW - toggleW));
    y = clamp(y, 0, Math.max(0, canvasH - toggleH));
    return new Rect(x, y, toggleW, toggleH);
  }

  private double clamp01(double v) {
    return v < 0 ? 0 : (v > 1 ? 1 : v);
  }

  private double clamp(double v, double min, double max) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return min;
    if (v < min) return min;
    if (v > max) return max;
    return v;
  }

  private String withPrefix(String label, MenuStyleSpec style, boolean selected, boolean enabled) {
    String base = label == null ? "" : label;
    String prefix;
    if (!enabled) {
      prefix = firstNonBlank(style != null ? style.itemDisabledPrefix() : null,
          style != null ? style.itemPrefix() : null,
          theme.getItemPrefix());
    } else if (selected) {
      prefix = firstNonBlank(style != null ? style.itemSelectedPrefix() : null, theme.getItemSelectedPrefix());
    } else {
      prefix = firstNonBlank(style != null ? style.itemPrefix() : null, theme.getItemPrefix());
    }
    return (prefix == null ? "" : prefix) + base;
  }

  private Color resolveItemColor(MenuStyleSpec style, boolean selected, boolean enabled) {
    if (!enabled) {
      return parseColor(
          style != null ? style.itemDisabledColor() : null,
          Color.rgb(160, 160, 160, 0.8)
      );
    }
    if (selected) {
      return parseColor(style != null ? style.itemSelectedColor() : null, theme.getItemSelectedColor());
    }
    return parseColor(style != null ? style.itemColor() : null, theme.getItemColor());
  }

  private Font resolveItemFont(MenuStyleSpec style) {
    return resolveItemFont(style, null);
  }

  private Font resolveItemFont(MenuStyleSpec style, MenuItemSpec item) {
    if (style == null && item == null) return scaledFont(theme.getItemFont());
    String family = firstNonBlank(
        item != null ? item.fontFamily() : null,
        style != null ? style.itemFontFamily() : null,
        theme.getItemFont().getFamily());
    double size = (item != null && item.fontSize() != null ? item.fontSize()
        : style != null && style.itemFontSize() != null ? style.itemFontSize()
        : theme.getItemFont().getSize()) * activeUiFontScale;
    String weightRaw = firstNonBlank(
        item != null ? item.fontWeight() : null,
        style != null ? style.itemFontWeight() : null);
    if (weightRaw == null || weightRaw.isBlank()) {
      return ProjectFontResolver.resolve(projectRoot, family, null, size, theme.getItemFont().getFamily());
    }
    FontWeight weight = parseFontWeight(weightRaw, FontWeight.NORMAL);
    return ProjectFontResolver.resolve(projectRoot, family, weight, size, theme.getItemFont().getFamily());
  }

  private Font resolveTitleFont(MenuStyleSpec style) {
    if (style == null) return scaledFont(theme.getTitleFont());
    String family = firstNonBlank(style.titleFontFamily(), theme.getTitleFont().getFamily());
    double size = (style.titleFontSize() != null ? style.titleFontSize() : theme.getTitleFont().getSize())
        * activeUiFontScale;
    FontWeight weight = parseFontWeight(style.titleFontWeight(), FontWeight.BOLD);
    return ProjectFontResolver.resolve(projectRoot, family, weight, size, theme.getTitleFont().getFamily());
  }

  private Font resolveSubtitleFont(MenuStyleSpec style) {
    Font titleFont = resolveTitleFont(style);
    Font hintFont = resolveHintFont(style);
    String family = style != null ? firstNonBlank(style.titleFontFamily(), hintFont.getFamily(), titleFont.getFamily()) : hintFont.getFamily();
    double size = Math.max(14.0, Math.min(titleFont.getSize() * 0.55, hintFont.getSize() * 1.35));
    FontWeight weight = style != null
        ? parseFontWeight(firstNonBlank(style.titleFontWeight(), style.hintsFontWeight()), FontWeight.NORMAL)
        : FontWeight.NORMAL;
    return ProjectFontResolver.resolve(projectRoot, family, weight, size, hintFont.getFamily());
  }

  private Font resolveHintFont(MenuStyleSpec style) {
    if (style == null) return scaledFont(theme.getHintFont());
    String family = firstNonBlank(style.hintsFontFamily(), theme.getHintFont().getFamily());
    double size = (style.hintsFontSize() != null ? style.hintsFontSize() : theme.getHintFont().getSize())
        * activeUiFontScale;
    FontWeight weight = parseFontWeight(style.hintsFontWeight(), FontWeight.NORMAL);
    return ProjectFontResolver.resolve(projectRoot, family, weight, size, theme.getHintFont().getFamily());
  }

  private Font scaledFont(Font font) {
    if (font == null || Math.abs(activeUiFontScale - 1.0) < 0.0001) return font;
    return Font.font(font.getFamily(), font.getSize() * activeUiFontScale);
  }

  private FontWeight parseFontWeight(String raw, FontWeight def) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return FontWeight.valueOf(raw.trim().toUpperCase());
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return def;
    }
  }

  private Color parseColor(String raw, Color def) {
    if (raw == null || raw.isBlank()) return def;
    String t = raw.trim();
    try {
      if (t.startsWith("#")) {
        String hex = t.substring(1);
        if (hex.length() == 6) {
          int r = Integer.parseInt(hex.substring(0, 2), 16);
          int g = Integer.parseInt(hex.substring(2, 4), 16);
          int b = Integer.parseInt(hex.substring(4, 6), 16);
          return Color.rgb(r, g, b);
        }
        if (hex.length() == 8) {
          int a = Integer.parseInt(hex.substring(0, 2), 16);
          int r = Integer.parseInt(hex.substring(2, 4), 16);
          int g = Integer.parseInt(hex.substring(4, 6), 16);
          int b = Integer.parseInt(hex.substring(6, 8), 16);
          return Color.rgb(r, g, b, a / 255.0);
        }
      } else if (t.toLowerCase().startsWith("rgb")) {
        int lp = t.indexOf('(');
        int rp = t.indexOf(')');
        if (lp >= 0 && rp > lp) {
          String[] parts = t.substring(lp + 1, rp).split(",");
          double r = Double.parseDouble(parts[0].trim());
          double g = Double.parseDouble(parts[1].trim());
          double b = Double.parseDouble(parts[2].trim());
          double a = parts.length >= 4 ? Double.parseDouble(parts[3].trim()) : 1.0;
          if (r > 1 || g > 1 || b > 1 || a > 1) {
            return Color.rgb((int) r, (int) g, (int) b, a > 1 ? (a / 255.0) : a);
          }
          return Color.color(r, g, b, a);
        }
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return def;
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }

  private double resolve(double v, double total) {
    // if v <= 1, treat as fraction of total; otherwise pixels
    return v <= 1.0 ? (total * v) : v;
  }

  // --- Gallery rendering ---

  public void renderGallery(GalleryScene scene, double w, double h) {
    gc.setFill(Color.rgb(20, 20, 30));
    gc.fillRect(0, 0, w, h);

    if (scene == null) return;

    Font titleFont = theme.getTitleFont();
    Font catFont = theme.getItemFont();
    Font smallFont = theme.getHintFont();

    // Title
    gc.setFont(titleFont);
    gc.setFill(Color.WHITE);
    gc.fillText(Localization.t("menu.gallery"), w * 0.04, h * 0.06);

    // Category tabs
    List<String> cats = scene.getCategories();
    double tabX = w * 0.04;
    gc.setFont(catFont);
    for (int i = 0; i < cats.size(); i++) {
      boolean active = i == scene.getCategoryIndex();
      gc.setFill(active ? Color.web("#f0b673") : Color.gray(0.6));
      gc.fillText(cats.get(i), tabX, h * 0.12);
      tabX += gc.getFont().getSize() * cats.get(i).length() * 0.65 + 24;
    }

    // Counter
    gc.setFont(smallFont);
    gc.setFill(Color.gray(0.5));
    gc.fillText(scene.getUnlockedCount() + " / " + scene.getTotalCount(), w * 0.88, h * 0.06);

    if (scene.isViewingFullscreen()) {
      renderGalleryFullscreen(scene, w, h);
      return;
    }

    // Thumbnail grid
    List<GalleryEntry> entries = scene.getPageEntries();
    int cols = scene.getColumns();
    double gridX = w * 0.04;
    double gridY = h * 0.17;
    double cellW = (w * 0.92) / cols;
    double cellH = cellW * 0.6;
    double gap = 8;

    for (int i = 0; i < entries.size(); i++) {
      GalleryEntry entry = entries.get(i);
      int col = i % cols;
      int row = i / cols;
      double cx = gridX + col * (cellW + gap);
      double cy = gridY + row * (cellH + gap);
      boolean selected = i == scene.getSelectedIndex();
      boolean unlocked = scene.isUnlocked(entry);

      if (unlocked) {
        Image img = loadImage(entry.imagePath());
        if (img != null) {
          gc.drawImage(img, cx, cy, cellW, cellH);
        } else {
          gc.setFill(Color.rgb(40, 40, 55));
          gc.fillRect(cx, cy, cellW, cellH);
        }
      } else {
        gc.setFill(Color.rgb(30, 30, 40));
        gc.fillRect(cx, cy, cellW, cellH);
        gc.setFont(smallFont);
        gc.setFill(Color.gray(0.35));
        gc.fillText("???", cx + cellW / 2 - 10, cy + cellH / 2 + 5);
      }

      if (selected) {
        gc.setStroke(Color.web("#f0b673"));
        gc.setLineWidth(2.5);
        gc.strokeRect(cx - 1, cy - 1, cellW + 2, cellH + 2);
      }
    }

    // Page indicator
    int pageCount = scene.getPageCount();
    if (pageCount > 1) {
      gc.setFont(smallFont);
      gc.setFill(Color.gray(0.5));
      gc.fillText("Page " + (scene.getPage() + 1) + " / " + pageCount, w * 0.46, h * 0.95);
    }
  }

  private void renderGalleryFullscreen(GalleryScene scene, double w, double h) {
    GalleryEntry entry = scene.getFullscreenEntry();
    if (entry == null) return;
    gc.setFill(Color.BLACK);
    gc.fillRect(0, 0, w, h);
    Image img = loadImage(entry.imagePath());
    if (img == null) return;
    double iw = img.getWidth();
    double ih = img.getHeight();
    double scale = Math.min(w / iw, h / ih);
    double dw = iw * scale;
    double dh = ih * scale;
    gc.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh);
  }

  // --- Music Room rendering ---

  public void renderMusicRoom(MusicRoomScene scene, double w, double h) {
    gc.setFill(Color.rgb(18, 18, 28));
    gc.fillRect(0, 0, w, h);

    if (scene == null) return;

    Font titleFont = theme.getTitleFont();
    Font trackFont = theme.getItemFont();
    Font artistFont = theme.getHintFont();
    Font catFont = theme.getItemFont();

    // Title
    gc.setFont(titleFont);
    gc.setFill(Color.WHITE);
    gc.fillText(Localization.t("menu.music_room"), w * 0.04, h * 0.06);

    // Category tabs
    List<String> cats = scene.getCategories();
    double tabX = w * 0.04;
    gc.setFont(catFont);
    for (int i = 0; i < cats.size(); i++) {
      boolean active = i == scene.getCategoryIndex();
      gc.setFill(active ? Color.web("#7ec8e3") : Color.gray(0.6));
      gc.fillText(cats.get(i), tabX, h * 0.12);
      tabX += gc.getFont().getSize() * cats.get(i).length() * 0.65 + 24;
    }

    // Track list
    List<MusicRoomEntry> entries = scene.getCurrentEntries();
    double listX = w * 0.06;
    double listY = h * 0.18;
    double lineH = h * 0.06;

    for (int i = 0; i < entries.size(); i++) {
      MusicRoomEntry entry = entries.get(i);
      double y = listY + i * lineH;
      boolean selected = i == scene.getSelectedIndex();
      boolean unlocked = scene.isUnlocked(entry);
      boolean playing = entry.equals(scene.getNowPlaying()) && scene.isPlaying();

      if (selected) {
        gc.setFill(Color.rgb(255, 255, 255, 0.06));
        gc.fillRoundRect(listX - 8, y - lineH * 0.65, w * 0.88, lineH, 6, 6);
      }

      if (!unlocked) {
        gc.setFont(trackFont);
        gc.setFill(Color.gray(0.3));
        gc.fillText("??? - Locked", listX, y);
        continue;
      }

      // Now-playing indicator
      if (playing) {
        gc.setFill(Color.web("#7ec8e3"));
        gc.fillText("\u266B", listX - 20, y);
      }

      gc.setFont(trackFont);
      gc.setFill(selected ? Color.web("#7ec8e3") : Color.WHITE);
      gc.fillText(entry.title(), listX, y);

      if (!entry.artist().isBlank()) {
        gc.setFont(artistFont);
        gc.setFill(Color.gray(0.5));
        gc.fillText(entry.artist(), listX + w * 0.5, y);
      }
    }
  }

  private record Rect(double x, double y, double w, double h) {
    boolean contains(double px, double py) {
      return px >= x && px <= x + w && py >= y && py <= y + h;
    }
  }
}
