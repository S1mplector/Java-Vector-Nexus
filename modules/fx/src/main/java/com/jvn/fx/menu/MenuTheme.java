package com.jvn.fx.menu;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.menu.config.MenuActionType;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Theme configuration for menus (Main, Settings, Load, Save).
 * Values are loaded from config/menu/menu.theme when available, with
 * legacy fallbacks for older projects.
 */
public class MenuTheme {
  // Colors
  private Color backgroundColor = Color.rgb(10, 12, 18);
  private Color titleColor = Color.WHITE;
  private Color itemColor = Color.LIGHTGRAY;
  private Color itemSelectedColor = Color.YELLOW;
  private Color hintColor = Color.rgb(200, 200, 200, 0.8);
  private Color accentColor = Color.YELLOW;

  // Fonts
  private String titleFontFamily = "Arial";
  private FontWeight titleFontWeight = FontWeight.BOLD;
  private int titleFontSize = 32;

  private String itemFontFamily = "Arial";
  private FontWeight itemFontWeight = FontWeight.NORMAL;
  private int itemFontSize = 20;

  private String hintFontFamily = "Arial";
  private FontWeight hintFontWeight = FontWeight.NORMAL;
  private int hintFontSize = 14;
  
  // Layout and formatting
  private double titleY = 60.0; // if <1 treat as fraction of height
  private double listYStart = 0.35; // fraction of height
  private double lineHeight = 40.0; // pixels
  // Item prefixes
  private String itemPrefix = "  ";
  private String itemSelectedPrefix = "> ";
  // Hints (main menu only)
  private String mainHintsText = null;

  // Labels
  private String titleText = null; // default: Localization t("app.title")
  private String labelNewGame = null; // default localization
  private String labelLoad = null;
  private String labelSettings = null;
  private String labelQuit = null;

  private boolean dockPresentation;
  private boolean menuMotion = true;
  private String titleImageEffect;

  // Title screen assets
  private String backgroundImagePath = null; // path to background image
  private String logoImagePath = null; // path to logo/title image
  private String bgmPath = null; // path to background music
  private double logoX = 0.5; // logo X position (fraction of width, 0.5 = centered)
  private double logoY = 0.15; // logo Y position (fraction of height)
  private double logoScale = 1.0; // logo scale factor
  private double bgmVolume = 0.7; // title screen BGM volume
  private boolean logoShadow = true; // draw shadow behind logo

  // In-game menu overlay assets
  private Color gameplayDimColor = Color.rgb(0, 0, 0, 0.50);
  private String gameplayPanelImagePath = null;
  private String gameplayLogoImagePath = null;
  private double gameplayPanelScale = 1.0;
  private double gameplayLogoScale = 0.25;

  public static MenuTheme defaults() { return new MenuTheme(); }

  public static MenuTheme fromAssets() {
    MenuTheme t = fromMenuProfile(MenuProfileLoader.loadFromAssets());
    AssetCatalog cat = new AssetCatalog();
    String[] candidates = new String[] {
        "config/menu/theme/menu.theme",
        "config/menu/menu.theme",
        "config/menu.theme",
        "menu.theme"
    };
    for (String candidate : candidates) {
      try (InputStream in = cat.open(AssetType.SCRIPT, candidate)) {
        if (in == null) continue;
        Properties p = new Properties();
        p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        t.apply(p);
        break;
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    }
    return t;
  }

  private static MenuTheme fromMenuProfile(MenuProfile profile) {
    MenuTheme out = new MenuTheme();
    if (profile == null) return out;

    MenuScreenSpec screen = profile.screen(profile.defaultScreenId());
    if (screen == null) return out;
    MenuLayoutSpec layout = profile.layout(screen.layoutId());
    MenuStyleSpec style = profile.style(screen.defaultStyleId());

    if (layout != null) {
      out.listYStart = layout.listYStart();
      out.lineHeight = layout.lineHeight();
      if (layout.titleY() != null) out.titleY = layout.titleY();
    }

    if (style != null) {
      out.itemColor = parseColor(style.itemColor(), out.itemColor);
      out.itemSelectedColor = parseColor(style.itemSelectedColor(), out.itemSelectedColor);
      out.itemPrefix = valueOr(style.itemPrefix(), out.itemPrefix);
      out.itemSelectedPrefix = valueOr(style.itemSelectedPrefix(), out.itemSelectedPrefix);
      out.itemFontFamily = valueOr(style.itemFontFamily(), out.itemFontFamily);
      out.itemFontWeight = parseWeight(style.itemFontWeight(), out.itemFontWeight);
      if (style.itemFontSize() != null && style.itemFontSize() > 0) out.itemFontSize = style.itemFontSize();
    }

    out.titleText = valueOr(screen.titleText(), out.titleText);
    out.mainHintsText = valueOr(screen.hintsText(), out.mainHintsText);
    for (MenuItemSpec item : screen.items()) {
      if (item == null || item.label() == null || item.label().isBlank()) continue;
      if (item.action().type() == MenuActionType.NEW_GAME) out.labelNewGame = item.label();
      if (item.action().type() == MenuActionType.LOAD_MENU) out.labelLoad = item.label();
      if (item.action().type() == MenuActionType.SETTINGS_MENU) out.labelSettings = item.label();
      if (item.action().type() == MenuActionType.QUIT) out.labelQuit = item.label();
    }
    return out;
  }

  private static String valueOr(String value, String fallback) {
    if (value == null || value.isBlank()) return fallback;
    return value;
  }

  public void apply(Properties p) {
    if (p == null) return;
    // Colors
    backgroundColor = parseColor(p.getProperty("backgroundColor"), backgroundColor);
    titleColor = parseColor(p.getProperty("titleColor"), titleColor);
    itemColor = parseColor(p.getProperty("itemColor"), itemColor);
    itemSelectedColor = parseColor(p.getProperty("itemSelectedColor"), itemSelectedColor);
    hintColor = parseColor(p.getProperty("hintColor"), hintColor);
    accentColor = parseColor(p.getProperty("accentColor"), accentColor);

    // Fonts
    titleFontFamily = p.getProperty("titleFontFamily", titleFontFamily);
    titleFontWeight = parseWeight(p.getProperty("titleFontWeight"), titleFontWeight);
    titleFontSize = parseInt(p.getProperty("titleFontSize"), titleFontSize);

    itemFontFamily = p.getProperty("itemFontFamily", itemFontFamily);
    itemFontWeight = parseWeight(p.getProperty("itemFontWeight"), itemFontWeight);
    itemFontSize = parseInt(p.getProperty("itemFontSize"), itemFontSize);

    hintFontFamily = p.getProperty("hintFontFamily", hintFontFamily);
    hintFontWeight = parseWeight(p.getProperty("hintFontWeight"), hintFontWeight);
    hintFontSize = parseInt(p.getProperty("hintFontSize"), hintFontSize);

    // Layout and formatting
    titleY = parseDouble(p.getProperty("titleY"), titleY);
    listYStart = parseDouble(p.getProperty("listYStart"), listYStart);
    lineHeight = parseDouble(p.getProperty("lineHeight"), lineHeight);
    itemPrefix = p.getProperty("itemPrefix", itemPrefix);
    itemSelectedPrefix = p.getProperty("itemSelectedPrefix", itemSelectedPrefix);
    mainHintsText = emptyToNull(p.getProperty("hintsText"));

    // Labels
    titleText = emptyToNull(p.getProperty("titleText"));
    labelNewGame = emptyToNull(p.getProperty("label.new"));
    labelLoad = emptyToNull(p.getProperty("label.load"));
    labelSettings = emptyToNull(p.getProperty("label.settings"));
    labelQuit = emptyToNull(p.getProperty("label.quit"));

    // Title screen assets
    backgroundImagePath = emptyToNull(p.getProperty("backgroundImage"));
    dockPresentation = "dock".equalsIgnoreCase(p.getProperty("mainMenuPresentation", "list"));
    menuMotion = parseBool(p.getProperty("menuMotion"), true);
    titleImageEffect = emptyToNull(p.getProperty("titleImageEffect"));
    logoImagePath = emptyToNull(p.getProperty("logoImage"));
    bgmPath = emptyToNull(p.getProperty("bgm"));
    logoX = parseDouble(p.getProperty("logoX"), logoX);
    logoY = parseDouble(p.getProperty("logoY"), logoY);
    logoScale = parseDouble(p.getProperty("logoScale"), logoScale);
    bgmVolume = parseDouble(p.getProperty("bgmVolume"), bgmVolume);
    logoShadow = parseBool(p.getProperty("logoShadow"), logoShadow);

    gameplayDimColor = parseColor(p.getProperty("gameplayDimColor"), gameplayDimColor);
    gameplayPanelImagePath = emptyToNull(p.getProperty("gameplayPanelImage"));
    gameplayLogoImagePath = emptyToNull(p.getProperty("gameplayLogoImage"));
    gameplayPanelScale = Math.max(0.05, parseDouble(p.getProperty("gameplayPanelScale"), gameplayPanelScale));
    gameplayLogoScale = Math.max(0.05, parseDouble(p.getProperty("gameplayLogoScale"), gameplayLogoScale));
  }

  private static boolean parseBool(String s, boolean def) {
    if (s == null || s.isBlank()) return def;
    String t = s.trim().toLowerCase();
    if ("true".equals(t) || "yes".equals(t) || "1".equals(t)) return true;
    if ("false".equals(t) || "no".equals(t) || "0".equals(t)) return false;
    return def;
  }

  private static String emptyToNull(String s) { return (s == null || s.trim().isEmpty()) ? null : s; }

  private static int parseInt(String s, int def) {
    try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
  }

  private static double parseDouble(String s, double def) {
    try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
  }

  private static FontWeight parseWeight(String s, FontWeight def) {
    if (s == null) return def;
    try {
      return FontWeight.valueOf(s.trim().toUpperCase());
    } catch (Exception e) {
      return def;
    }
  }

  private static Color parseColor(String s, Color def) {
    if (s == null || s.isBlank()) return def;
    String t = s.trim();
    try {
      if (t.startsWith("#")) {
        String hex = t.substring(1);
        if (hex.length() == 6) {
          int r = Integer.parseInt(hex.substring(0,2), 16);
          int g = Integer.parseInt(hex.substring(2,4), 16);
          int b = Integer.parseInt(hex.substring(4,6), 16);
          return Color.rgb(r, g, b);
        } else if (hex.length() == 8) {
          int a = Integer.parseInt(hex.substring(0,2), 16);
          int r = Integer.parseInt(hex.substring(2,4), 16);
          int g = Integer.parseInt(hex.substring(4,6), 16);
          int b = Integer.parseInt(hex.substring(6,8), 16);
          return Color.rgb(r, g, b, a / 255.0);
        }
      } else if (t.toLowerCase().startsWith("rgb")) {
        // rgb(r,g,b) or rgba(r,g,b,a) in 0..1 floats
        int lp = t.indexOf('(');
        int rp = t.indexOf(')');
        if (lp >= 0 && rp > lp) {
          String[] parts = t.substring(lp+1, rp).split(",");
          double r = Double.parseDouble(parts[0].trim());
          double g = Double.parseDouble(parts[1].trim());
          double b = Double.parseDouble(parts[2].trim());
          double a = parts.length >= 4 ? Double.parseDouble(parts[3].trim()) : 1.0;
          if (r > 1 || g > 1 || b > 1 || a > 1) {
            // interpret as 0..255 if >1
            return Color.rgb((int)r, (int)g, (int)b, a > 1 ? (a/255.0) : a);
          }
          return Color.color(r, g, b, a);
        }
      }
    } catch (Exception ignored) {
      // reason: malformed color string in theme properties; return default color
    }
    return def;
  }

  public Color getBackgroundColor() { return backgroundColor; }
  public Color getTitleColor() { return titleColor; }
  public Color getItemColor() { return itemColor; }
  public Color getItemSelectedColor() { return itemSelectedColor; }
  public Color getHintColor() { return hintColor; }
  public Color getAccentColor() { return accentColor; }

  public String getTitleFontFamily() { return titleFontFamily; }
  public int getTitleFontSize() { return titleFontSize; }
  public Font getTitleFont() { return Font.font(titleFontFamily, titleFontWeight, titleFontSize); }
  public Font getItemFont() { return Font.font(itemFontFamily, itemFontWeight, itemFontSize); }
  public Font getHintFont() { return Font.font(hintFontFamily, hintFontWeight, hintFontSize); }

  public double getTitleY() { return titleY; }
  public double getListYStart() { return listYStart; }
  public double getLineHeight() { return lineHeight; }
  public String getItemPrefix() { return itemPrefix; }
  public String getItemSelectedPrefix() { return itemSelectedPrefix; }
  public String getMainHintsText() { return mainHintsText; }

  public String getTitleText() { return titleText; }
  public String getLabelNewGame() { return labelNewGame; }
  public String getLabelLoad() { return labelLoad; }
  public String getLabelSettings() { return labelSettings; }
  public String getLabelQuit() { return labelQuit; }

  // Title screen asset getters
  public String getBackgroundImagePath() { return backgroundImagePath; }
  public boolean isDockPresentation() { return dockPresentation; }
  public boolean isMenuMotion() { return menuMotion; }
  public String getTitleImageEffect() { return titleImageEffect; }
  public String getLogoImagePath() { return logoImagePath; }
  public String getBgmPath() { return bgmPath; }
  public double getLogoX() { return logoX; }
  public double getLogoY() { return logoY; }
  public double getLogoScale() { return logoScale; }
  public double getBgmVolume() { return bgmVolume; }
  public boolean isLogoShadow() { return logoShadow; }
  public Color getGameplayDimColor() { return gameplayDimColor; }
  public String getGameplayPanelImagePath() { return gameplayPanelImagePath; }
  public String getGameplayLogoImagePath() { return gameplayLogoImagePath; }
  public double getGameplayPanelScale() { return gameplayPanelScale; }
  public double getGameplayLogoScale() { return gameplayLogoScale; }
}
