package com.jvn.core.menu.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;

public final class MenuProfileLoader {
  private static final String[] REGISTRY_PATHS = new String[] {
      "config/menu/registry/menu.registry",
      "config/menu/menu.registry",
      "config/menu/registry.properties",
      "menu.registry"
  };

  private static final Set<String> KNOWN_LAYOUT_FIELDS = Set.of(
      "extends",
      "listYStart",
      "lineHeight",
      "listWidth",
      "listWidthFactor",
      "textAlign",
      "hintsBottomMargin",
      "titleY",
      "subtitleGap",
      "listXCenter",
      "titleX",
      "maxVisibleItems",
      "titleAlign",
      "hintsAlign",
      "hintsX"
  );

  private static final Set<String> KNOWN_STYLE_FIELDS = Set.of(
      "extends",
      "itemColor",
      "itemSelectedColor",
      "itemHoverColor",
      "itemDisabledColor",
      "itemPrefix",
      "itemSelectedPrefix",
      "itemDisabledPrefix",
      "itemFontFamily",
      "itemFontWeight",
      "itemFontSize",
      "itemShadowColor",
      "itemShadowOffsetX",
      "itemShadowOffsetY",
      "itemOpacity",
      "buttonAsset",
      "buttonSelectedAsset",
      "buttonHoverAsset",
      "buttonDisabledAsset",
      "buttonTextPaddingX",
      "buttonTextPaddingY",
      "titleColor",
      "titleFontFamily",
      "titleFontWeight",
      "titleFontSize",
      "titleShadowColor",
      "hintsColor",
      "hintsFontFamily",
      "hintsFontWeight",
      "hintsFontSize",
      "backgroundAsset",
      "backgroundColor",
      "backgroundOpacity"
  );

  private static final Set<String> KNOWN_SCREEN_FIELDS = Set.of(
      "extends",
      "titleText",
      "subtitleText",
      "hintsText",
      "layout",
      "layoutId",
      "defaultItemStyle",
      "wrapSelection",
      "items",
      "itemsMode",
      "backgroundAsset"
  );

  public record LoadResult(MenuProfile profile, List<String> diagnostics) {
    public LoadResult {
      profile = profile == null ? MenuProfile.defaults() : profile;
      diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
  }

  private record LoadedProperties(String path, Properties properties) {}

  private MenuProfileLoader() {}

  /** Accepted top-level keys for menu layout sources. */
  public static Set<String> knownLayoutFields() {
    return KNOWN_LAYOUT_FIELDS;
  }

  /** Accepted top-level keys for menu style sources. */
  public static Set<String> knownStyleFields() {
    return KNOWN_STYLE_FIELDS;
  }

  /** Accepted top-level keys for menu screen sources. */
  public static Set<String> knownScreenFields() {
    return KNOWN_SCREEN_FIELDS;
  }

  /** Accepted fields below an {@code item.<id>.} menu-screen prefix. */
  public static Set<String> knownItemFields() {
    return KNOWN_ITEM_FIELDS;
  }

  /** Validate one layout source with the same parser and fallbacks used by runtime loading. */
  public static List<String> validateLayout(Properties properties, String sourceName) {
    List<String> diagnostics = new ArrayList<>();
    parseLayout("editor", properties == null ? new Properties() : properties,
        MenuProfile.defaultLayout(), diagnostics, normalize(sourceName, "layout source"));
    return List.copyOf(diagnostics);
  }

  /** Validate one style source with the same parser and fallbacks used by runtime loading. */
  public static List<String> validateStyle(Properties properties, String sourceName) {
    List<String> diagnostics = new ArrayList<>();
    parseStyle("editor", properties == null ? new Properties() : properties,
        MenuProfile.defaultStyle(), diagnostics, normalize(sourceName, "style source"));
    return List.copyOf(diagnostics);
  }

  /** Validate one menu screen source with the same parser and fallbacks used by runtime loading. */
  public static List<String> validateScreen(Properties properties, String sourceName) {
    List<String> diagnostics = new ArrayList<>();
    parseScreen("editor", properties == null ? new Properties() : properties,
        MenuProfile.defaultMainScreen(), diagnostics, normalize(sourceName, "menu source"));
    return List.copyOf(diagnostics);
  }

  public static MenuProfile loadFromAssets() {
    return load(new AssetCatalog());
  }

  public static LoadResult loadWithDiagnostics() {
    return loadWithDiagnostics(new AssetCatalog());
  }

  public static MenuProfile load(AssetCatalog assets) {
    return loadWithDiagnostics(assets).profile();
  }

  public static LoadResult loadWithDiagnostics(AssetCatalog assets) {
    List<String> diagnostics = new ArrayList<>();
    if (assets == null) {
      diagnostics.add("AssetCatalog was null; using default menu profile");
      return new LoadResult(MenuProfile.defaults(), diagnostics);
    }

    MenuProfile defaults = MenuProfile.defaults();

    Map<String, MenuLayoutSpec> layouts = new LinkedHashMap<>();
    Map<String, MenuStyleSpec> styles = new LinkedHashMap<>();
    Map<String, MenuScreenSpec> screens = new LinkedHashMap<>();
    String defaultScreen = defaults.defaultScreenId();

    LoadedProperties registryLoad = loadFirstProperties(assets, diagnostics, "menu registry", REGISTRY_PATHS);
    Properties registry = registryLoad == null ? null : registryLoad.properties();
    if (registry != null) {
      String configuredDefault = normalize(registry.getProperty("defaultMenu", registry.getProperty("defaultScreen")), null);
      if (configuredDefault != null) defaultScreen = configuredDefault;
    }

    Set<String> layoutIds = new LinkedHashSet<>();
    layoutIds.addAll(defaults.layouts().keySet());
    layoutIds.addAll(parseCsv(registry != null ? registry.getProperty("layouts") : null));
    layoutIds.addAll(discoverIds(assets, diagnostics, "config/menu/layouts", ".layout", ".properties"));
    layoutIds.addAll(discoverIds(assets, diagnostics, "config/menu", ".layout", ".properties"));

    Set<String> styleIds = new LinkedHashSet<>();
    styleIds.addAll(defaults.styles().keySet());
    styleIds.addAll(parseCsv(registry != null ? registry.getProperty("styles") : null));
    styleIds.addAll(discoverIds(assets, diagnostics, "config/menu/styles", ".style", ".properties"));
    styleIds.addAll(discoverIds(assets, diagnostics, "config/menu", ".style", ".properties"));

    Set<String> menuIds = new LinkedHashSet<>();
    menuIds.addAll(defaults.screens().keySet());
    menuIds.addAll(parseCsv(registry != null ? registry.getProperty("menus") : null));
    menuIds.addAll(discoverIds(assets, diagnostics, "config/menu/menus", ".menu", ".properties"));
    menuIds.addAll(discoverIds(assets, diagnostics, "config/menu", ".menu", ".properties"));

    for (String id : layoutIds) {
      MenuLayoutSpec spec = resolveLayout(id, assets, defaults.layouts(), layouts, new LinkedHashSet<>(), diagnostics);
      if (spec != null) layouts.put(id, spec);
    }

    for (String id : styleIds) {
      MenuStyleSpec spec = resolveStyle(id, assets, defaults.styles(), styles, new LinkedHashSet<>(), diagnostics);
      if (spec != null) styles.put(id, spec);
    }

    for (String id : menuIds) {
      MenuScreenSpec spec = resolveScreen(id, assets, defaults.screens(), screens, new LinkedHashSet<>(), diagnostics);
      if (spec != null) screens.put(id, spec);
    }

    if (!screens.containsKey(defaultScreen)) {
      diagnostics.add("Configured default menu '" + defaultScreen + "' is undefined; using fallback");
      defaultScreen = screens.containsKey("main") ? "main" : screens.keySet().stream().findFirst().orElse("main");
    }

    MenuProfile profile = new MenuProfile(defaultScreen, screens, layouts, styles);
    diagnostics.addAll(MenuProfileValidator.validate(profile));
    return new LoadResult(profile, diagnostics);
  }

  private static MenuLayoutSpec resolveLayout(
      String id,
      AssetCatalog assets,
      Map<String, MenuLayoutSpec> defaults,
      Map<String, MenuLayoutSpec> resolved,
      Set<String> visiting,
      List<String> diagnostics
  ) {
    String key = normalize(id, null);
    if (key == null) return resolved.getOrDefault("default", MenuProfile.defaultLayout());
    if (resolved.containsKey(key)) return resolved.get(key);
    if (!visiting.add(key)) {
      diagnostics.add("Circular layout inheritance detected at '" + key + "'");
      return defaults.getOrDefault(key, MenuProfile.defaultLayout());
    }

    MenuLayoutSpec base = defaults.getOrDefault(key, MenuProfile.defaultLayout());
    LoadedProperties loaded = loadFirstProperties(assets, diagnostics, "layout '" + key + "'", layoutPaths(key));
    if (loaded != null) {
      Properties p = loaded.properties();
      String parent = normalize(p.getProperty("extends"), null);
      if (parent != null && !parent.equalsIgnoreCase(key)) {
        MenuLayoutSpec parentLayout = resolveLayout(parent, assets, defaults, resolved, visiting, diagnostics);
        if (parentLayout != null) {
          base = parentLayout;
        } else {
          diagnostics.add("Layout '" + key + "' extends missing layout '" + parent + "'");
        }
      }
      base = parseLayout(key, p, base, diagnostics, loaded.path());
    }

    visiting.remove(key);
    resolved.put(key, base);
    return base;
  }

  private static MenuStyleSpec resolveStyle(
      String id,
      AssetCatalog assets,
      Map<String, MenuStyleSpec> defaults,
      Map<String, MenuStyleSpec> resolved,
      Set<String> visiting,
      List<String> diagnostics
  ) {
    String key = normalize(id, null);
    if (key == null) return resolved.getOrDefault("default", MenuProfile.defaultStyle());
    if (resolved.containsKey(key)) return resolved.get(key);
    if (!visiting.add(key)) {
      diagnostics.add("Circular style inheritance detected at '" + key + "'");
      return defaults.getOrDefault(key, MenuProfile.defaultStyle());
    }

    MenuStyleSpec base = defaults.getOrDefault(key, MenuProfile.defaultStyle());
    LoadedProperties loaded = loadFirstProperties(assets, diagnostics, "style '" + key + "'", stylePaths(key));
    if (loaded != null) {
      Properties p = loaded.properties();
      String parent = normalize(p.getProperty("extends"), null);
      if (parent != null && !parent.equalsIgnoreCase(key)) {
        MenuStyleSpec parentStyle = resolveStyle(parent, assets, defaults, resolved, visiting, diagnostics);
        if (parentStyle != null) {
          base = parentStyle;
        } else {
          diagnostics.add("Style '" + key + "' extends missing style '" + parent + "'");
        }
      }
      base = parseStyle(key, p, base, diagnostics, loaded.path());
    }

    visiting.remove(key);
    resolved.put(key, base);
    return base;
  }

  private static MenuScreenSpec resolveScreen(
      String id,
      AssetCatalog assets,
      Map<String, MenuScreenSpec> defaults,
      Map<String, MenuScreenSpec> resolved,
      Set<String> visiting,
      List<String> diagnostics
  ) {
    String key = normalize(id, null);
    if (key == null) return resolved.get("main");
    if (resolved.containsKey(key)) return resolved.get(key);
    if (!visiting.add(key)) {
      diagnostics.add("Circular menu inheritance detected at screen '" + key + "'");
      return defaults.getOrDefault(key, "main".equals(key) ? MenuProfile.defaultMainScreen() : null);
    }

    MenuScreenSpec base = defaults.get(key);
    if (base == null && "main".equals(key)) base = MenuProfile.defaultMainScreen();

    LoadedProperties loaded = loadFirstProperties(assets, diagnostics, "menu screen '" + key + "'", menuPaths(key));
    if (loaded == null && base == null) {
      visiting.remove(key);
      return null;
    }

    if (loaded != null) {
      Properties p = loaded.properties();
      String parent = normalize(p.getProperty("extends"), null);
      if (parent != null && !parent.equalsIgnoreCase(key)) {
        MenuScreenSpec parentScreen = resolveScreen(parent, assets, defaults, resolved, visiting, diagnostics);
        if (parentScreen != null) {
          base = parentScreen;
        } else {
          diagnostics.add("Menu screen '" + key + "' extends missing menu '" + parent + "'");
        }
      }
      if (base == null) {
        base = new MenuScreenSpec(key, null, null, "default", "default", true, List.of());
      }
      base = parseScreen(key, p, base, diagnostics, loaded.path());
    }

    visiting.remove(key);
    if (base != null) resolved.put(key, base);
    return base;
  }

  private static MenuLayoutSpec parseLayout(String id, Properties p, MenuLayoutSpec base, List<String> diagnostics, String sourcePath) {
    warnUnknownLayoutKeys(p, diagnostics, sourcePath);
    if (p.containsKey("listWidth") && !p.containsKey("listWidthFactor")) {
      diagnostics.add("Deprecated key 'listWidth' in " + sourcePath + "; use 'listWidthFactor' instead");
    }
    double listYStart = parseDouble(p.getProperty("listYStart"), base.listYStart(), diagnostics, sourcePath, "listYStart");
    if (listYStart < 0.0) {
      diagnostics.add("Invalid value for 'listYStart' in " + sourcePath + ": must be >= 0 (using " + base.listYStart() + ")");
      listYStart = base.listYStart();
    }

    double lineHeight = parseDouble(p.getProperty("lineHeight"), base.lineHeight(), diagnostics, sourcePath, "lineHeight");
    if (lineHeight <= 0.0) {
      diagnostics.add("Invalid value for 'lineHeight' in " + sourcePath + ": must be > 0 (using " + base.lineHeight() + ")");
      lineHeight = base.lineHeight();
    }

    double listWidthFactor = parseDouble(
        p.getProperty("listWidthFactor", p.getProperty("listWidth")),
        base.listWidthFactor(),
        diagnostics,
        sourcePath,
        "listWidthFactor");
    if (listWidthFactor < 0.1 || listWidthFactor > 1.0) {
      double clamped = clamp(listWidthFactor, 0.1, 1.0);
      diagnostics.add("Value for 'listWidthFactor' in " + sourcePath + " was out of range (0.1..1.0): " + listWidthFactor
          + " (using " + clamped + ")");
      listWidthFactor = clamped;
    }

    String textAlign = normalize(p.getProperty("textAlign"), base.textAlign());
    if (!isKnownAlign(textAlign)) {
      diagnostics.add("Invalid value for 'textAlign' in " + sourcePath + ": '" + textAlign
          + "' (expected left/center/right; using " + base.textAlign() + ")");
      textAlign = base.textAlign();
    }

    double hintsBottomMargin = parseDouble(
        p.getProperty("hintsBottomMargin"),
        base.hintsBottomMargin(),
        diagnostics,
        sourcePath,
        "hintsBottomMargin");
    if (hintsBottomMargin < 0.0) {
      diagnostics.add("Invalid value for 'hintsBottomMargin' in " + sourcePath + ": must be >= 0 (using "
          + base.hintsBottomMargin() + ")");
      hintsBottomMargin = base.hintsBottomMargin();
    }

    Double titleY = parseOptionalDouble(p.getProperty("titleY"), base.titleY(), diagnostics, sourcePath, "titleY");
    if (titleY != null && titleY < 0.0) {
      diagnostics.add("Invalid value for 'titleY' in " + sourcePath + ": must be >= 0 (using " + base.titleY() + ")");
      titleY = base.titleY();
    }
    double subtitleGap = parseDouble(p.getProperty("subtitleGap"), base.subtitleGap(), diagnostics, sourcePath, "subtitleGap");
    if (subtitleGap < 0.0) {
      diagnostics.add("Invalid value for 'subtitleGap' in " + sourcePath + ": must be >= 0 (using " + base.subtitleGap() + ")");
      subtitleGap = base.subtitleGap();
    }

    Double listXCenter = parseOptionalDouble(p.getProperty("listXCenter"), base.listXCenter(), diagnostics, sourcePath, "listXCenter");
    Double titleX = parseOptionalDouble(p.getProperty("titleX"), base.titleX(), diagnostics, sourcePath, "titleX");
    Integer maxVisibleItems = parseOptionalPositiveInt(p.getProperty("maxVisibleItems"), base.maxVisibleItems(), diagnostics, sourcePath, "maxVisibleItems");
    String titleAlign = normalize(p.getProperty("titleAlign"), base.titleAlign());
    if (!isKnownAlign(titleAlign)) {
      diagnostics.add("Invalid value for 'titleAlign' in " + sourcePath + ": '" + titleAlign
          + "' (expected left/center/right; using " + base.titleAlign() + ")");
      titleAlign = base.titleAlign();
    }
    String hintsAlign = normalize(p.getProperty("hintsAlign"), base.hintsAlign());
    if (!isKnownAlign(hintsAlign)) {
      diagnostics.add("Invalid value for 'hintsAlign' in " + sourcePath + ": '" + hintsAlign
          + "' (expected left/center/right; using " + base.hintsAlign() + ")");
      hintsAlign = base.hintsAlign();
    }
    Double hintsX = parseOptionalDouble(p.getProperty("hintsX"), base.hintsX(), diagnostics, sourcePath, "hintsX");

    return new MenuLayoutSpec(
        id,
        listYStart,
        lineHeight,
        listWidthFactor,
        textAlign,
        hintsBottomMargin,
        titleY,
        subtitleGap,
        listXCenter,
        titleX,
        maxVisibleItems,
        titleAlign,
        hintsAlign,
        hintsX
    );
  }

  private static MenuStyleSpec parseStyle(String id, Properties p, MenuStyleSpec base, List<String> diagnostics, String sourcePath) {
    warnUnknownStyleKeys(p, diagnostics, sourcePath);
    Double itemOpacity = parseOptionalDouble(p.getProperty("itemOpacity"), base.itemOpacity(), diagnostics, sourcePath, "itemOpacity");
    itemOpacity = clampOptional(itemOpacity, 0.0, 1.0, diagnostics, sourcePath, "itemOpacity");

    Double backgroundOpacity = parseOptionalDouble(
        p.getProperty("backgroundOpacity"),
        base.backgroundOpacity(),
        diagnostics,
        sourcePath,
        "backgroundOpacity");
    backgroundOpacity = clampOptional(backgroundOpacity, 0.0, 1.0, diagnostics, sourcePath, "backgroundOpacity");

    return new MenuStyleSpec(
        id,
        // Item colors
        normalize(p.getProperty("itemColor"), base.itemColor()),
        normalize(p.getProperty("itemSelectedColor"), base.itemSelectedColor()),
        normalize(p.getProperty("itemHoverColor"), base.itemHoverColor()),
        normalize(p.getProperty("itemDisabledColor"), base.itemDisabledColor()),
        // Prefixes
        normalizePrefix(p.getProperty("itemPrefix"), base.itemPrefix()),
        normalizePrefix(p.getProperty("itemSelectedPrefix"), base.itemSelectedPrefix()),
        normalizePrefix(p.getProperty("itemDisabledPrefix"), base.itemDisabledPrefix()),
        // Font
        normalize(p.getProperty("itemFontFamily"), base.itemFontFamily()),
        normalize(p.getProperty("itemFontWeight"), base.itemFontWeight()),
        parseOptionalPositiveInt(p.getProperty("itemFontSize"), base.itemFontSize(), diagnostics, sourcePath, "itemFontSize"),
        // Text effects
        normalize(p.getProperty("itemShadowColor"), base.itemShadowColor()),
        parseOptionalDouble(p.getProperty("itemShadowOffsetX"), base.itemShadowOffsetX(), diagnostics, sourcePath, "itemShadowOffsetX"),
        parseOptionalDouble(p.getProperty("itemShadowOffsetY"), base.itemShadowOffsetY(), diagnostics, sourcePath, "itemShadowOffsetY"),
        itemOpacity,
        // Button skins
        normalize(p.getProperty("buttonAsset"), base.buttonAssetPath()),
        normalize(p.getProperty("buttonSelectedAsset"), base.buttonSelectedAssetPath()),
        normalize(p.getProperty("buttonHoverAsset"), base.buttonHoverAssetPath()),
        normalize(p.getProperty("buttonDisabledAsset"), base.buttonDisabledAssetPath()),
        parseOptionalDouble(p.getProperty("buttonTextPaddingX"), base.buttonTextPaddingX(), diagnostics, sourcePath, "buttonTextPaddingX"),
        parseOptionalDouble(p.getProperty("buttonTextPaddingY"), base.buttonTextPaddingY(), diagnostics, sourcePath, "buttonTextPaddingY"),
        // Title styling
        normalize(p.getProperty("titleColor"), base.titleColor()),
        normalize(p.getProperty("titleFontFamily"), base.titleFontFamily()),
        normalize(p.getProperty("titleFontWeight"), base.titleFontWeight()),
        parseOptionalPositiveInt(p.getProperty("titleFontSize"), base.titleFontSize(), diagnostics, sourcePath, "titleFontSize"),
        normalize(p.getProperty("titleShadowColor"), base.titleShadowColor()),
        // Hints styling
        normalize(p.getProperty("hintsColor"), base.hintsColor()),
        normalize(p.getProperty("hintsFontFamily"), base.hintsFontFamily()),
        normalize(p.getProperty("hintsFontWeight"), base.hintsFontWeight()),
        parseOptionalPositiveInt(p.getProperty("hintsFontSize"), base.hintsFontSize(), diagnostics, sourcePath, "hintsFontSize"),
        // Background
        normalize(p.getProperty("backgroundAsset"), base.backgroundAssetPath()),
        normalize(p.getProperty("backgroundColor"), base.backgroundColor()),
        backgroundOpacity
    );
  }

  private static MenuScreenSpec parseScreen(String id, Properties p, MenuScreenSpec base, List<String> diagnostics, String sourcePath) {
    warnUnknownScreenKeys(p, diagnostics, sourcePath);
    if (p.containsKey("layoutId") && !p.containsKey("layout")) {
      diagnostics.add("Deprecated key 'layoutId' in " + sourcePath + "; use 'layout' instead");
    }
    String titleText = configuredText(p, "titleText", base == null ? null : base.titleText());
    String subtitleText = configuredText(p, "subtitleText", base == null ? null : base.subtitleText());
    String hintsText = configuredText(p, "hintsText", base == null ? null : base.hintsText());
    String layoutId = normalize(p.getProperty("layout", p.getProperty("layoutId")), base == null ? "default" : base.layoutId());
    String defaultStyleId = normalize(p.getProperty("defaultItemStyle"), base == null ? "default" : base.defaultStyleId());
    boolean wrapSelection = parseBoolean(p.getProperty("wrapSelection"), base == null || base.wrapSelection(), diagnostics, sourcePath, "wrapSelection");

    List<String> itemIds = parseCsv(p.getProperty("items"));
    if (!"explicit".equalsIgnoreCase(p.getProperty("itemsMode", "").trim())) {
      if (itemIds.isEmpty()) itemIds = collectItemIdsFromProperties(p);
      if (itemIds.isEmpty() && base != null) {
        for (MenuItemSpec i : base.items()) itemIds.add(i.id());
      }
    }

    Map<String, MenuItemSpec> baseItems = new LinkedHashMap<>();
    if (base != null) {
      for (MenuItemSpec i : base.items()) baseItems.put(i.id(), i);
    }

    List<MenuItemSpec> items = new ArrayList<>();
    Set<String> seenIds = new LinkedHashSet<>();
    for (String itemId : itemIds) {
      String idNorm = normalize(itemId, null);
      if (idNorm == null) continue;
      if (!seenIds.add(idNorm)) {
        diagnostics.add("Duplicate item id '" + idNorm + "' in " + sourcePath + "; later declaration ignored");
        continue;
      }
      String itemPrefix = "item." + idNorm + ".";
      MenuItemSpec bi = baseItems.get(idNorm);
      String label = configuredText(p, itemPrefix + "label", bi == null ? null : bi.label());
      String styleId = normalize(p.getProperty(itemPrefix + "style"), bi == null ? defaultStyleId : bi.styleId());
      String icon = normalize(p.getProperty(itemPrefix + "icon"), bi == null ? null : bi.iconPath());
      boolean enabled = parseBoolean(p.getProperty(itemPrefix + "enabled"), bi == null || bi.enabled(), diagnostics, sourcePath, itemPrefix + "enabled");
      String actionRaw = normalize(p.getProperty(itemPrefix + "action"), bi == null ? null : bi.action().type().name());
      String targetRaw = normalize(p.getProperty(itemPrefix + "target"), bi == null ? null : bi.action().target());
      String buttonAsset = normalize(p.getProperty(itemPrefix + "bgAsset"), bi == null ? null : bi.buttonAssetPath());
      String buttonSelectedAsset = normalize(p.getProperty(itemPrefix + "bgSelectedAsset"), bi == null ? null : bi.buttonSelectedAssetPath());
      String buttonDisabledAsset = normalize(p.getProperty(itemPrefix + "bgDisabledAsset"), bi == null ? null : bi.buttonDisabledAssetPath());
      Double boundsX = parseOptionalDouble(p.getProperty(itemPrefix + "boundsX"), bi == null ? null : bi.boundsX(), diagnostics, sourcePath, itemPrefix + "boundsX");
      Double boundsY = parseOptionalDouble(p.getProperty(itemPrefix + "boundsY"), bi == null ? null : bi.boundsY(), diagnostics, sourcePath, itemPrefix + "boundsY");
      Double boundsWidth = parseOptionalDouble(p.getProperty(itemPrefix + "boundsWidth"), bi == null ? null : bi.boundsWidth(), diagnostics, sourcePath, itemPrefix + "boundsWidth");
      Double boundsHeight = parseOptionalDouble(p.getProperty(itemPrefix + "boundsHeight"), bi == null ? null : bi.boundsHeight(), diagnostics, sourcePath, itemPrefix + "boundsHeight");
      BoundsFields itemBounds = sanitizeBoundsFields(boundsX, boundsY, boundsWidth, boundsHeight, diagnostics, sourcePath, "Item '" + idNorm + "'");
      boolean slotPreviewEnabled = parseBoolean(
          p.getProperty(itemPrefix + "slotPreviewEnabled"),
          bi != null ? bi.slotPreviewEnabled() : isSlotTemplateItemId(idNorm),
          diagnostics,
          sourcePath,
          itemPrefix + "slotPreviewEnabled"
      );
      String slotPreviewPlaceholderAsset = normalize(
          p.getProperty(itemPrefix + "slotPreviewPlaceholderAsset"),
          bi == null ? null : bi.slotPreviewPlaceholderAssetPath()
      );
      String slotPreviewFrameAsset = normalize(
          p.getProperty(itemPrefix + "slotPreviewFrameAsset"),
          bi == null ? null : bi.slotPreviewFrameAssetPath()
      );
      Double slotPreviewX = parseOptionalDouble(
          p.getProperty(itemPrefix + "slotPreviewX"),
          bi == null ? null : bi.slotPreviewX(),
          diagnostics,
          sourcePath,
          itemPrefix + "slotPreviewX"
      );
      Double slotPreviewY = parseOptionalDouble(
          p.getProperty(itemPrefix + "slotPreviewY"),
          bi == null ? null : bi.slotPreviewY(),
          diagnostics,
          sourcePath,
          itemPrefix + "slotPreviewY"
      );
      Double slotPreviewWidth = parseOptionalDouble(
          p.getProperty(itemPrefix + "slotPreviewWidth"),
          bi == null ? null : bi.slotPreviewWidth(),
          diagnostics,
          sourcePath,
          itemPrefix + "slotPreviewWidth"
      );
      Double slotPreviewHeight = parseOptionalDouble(
          p.getProperty(itemPrefix + "slotPreviewHeight"),
          bi == null ? null : bi.slotPreviewHeight(),
          diagnostics,
          sourcePath,
          itemPrefix + "slotPreviewHeight"
      );
      BoundsFields slotPreviewBounds = sanitizeBoundsFields(
          slotPreviewX,
          slotPreviewY,
          slotPreviewWidth,
          slotPreviewHeight,
          diagnostics,
          sourcePath,
          "Item '" + idNorm + "' slot preview");
      MenuActionSpec action = parseActionWithDiagnostics(actionRaw, targetRaw, diagnostics, sourcePath, itemPrefix + "action");
      Map<String, String> extras = collectItemExtras(p, itemPrefix, bi);
      String itemFontFamily = normalize(p.getProperty(itemPrefix + "fontFamily"), bi == null ? null : bi.fontFamily());
      String itemFontWeight = normalize(p.getProperty(itemPrefix + "fontWeight"), bi == null ? null : bi.fontWeight());
      Integer itemFontSize = parseOptionalPositiveInt(p.getProperty(itemPrefix + "fontSize"), bi == null ? null : bi.fontSize(), diagnostics, sourcePath, itemPrefix + "fontSize");
      items.add(new MenuItemSpec(
          idNorm,
          label,
          styleId,
          icon,
          enabled,
          action,
          buttonAsset,
          buttonSelectedAsset,
          buttonDisabledAsset,
          itemBounds.x(),
          itemBounds.y(),
          itemBounds.width(),
          itemBounds.height(),
          slotPreviewEnabled,
          slotPreviewPlaceholderAsset,
          slotPreviewFrameAsset,
          slotPreviewBounds.x(),
          slotPreviewBounds.y(),
          slotPreviewBounds.width(),
          slotPreviewBounds.height(),
          extras,
          itemFontFamily,
          itemFontWeight,
          itemFontSize
      ));
    }

    String backgroundAsset = normalize(p.getProperty("backgroundAsset"), base == null ? null : base.backgroundAsset());

    return new MenuScreenSpec(id, titleText, subtitleText, hintsText, layoutId, defaultStyleId, wrapSelection, items, backgroundAsset);
  }

  private static MenuActionSpec parseActionWithDiagnostics(
      String rawAction,
      String rawTarget,
      List<String> diagnostics,
      String sourcePath,
      String property
  ) {
    String action = rawAction == null ? "" : rawAction.trim();
    String target = normalize(rawTarget, null);
    if (!action.isEmpty()) {
      int colon = action.indexOf(':');
      if (colon > 0 && colon < action.length() - 1) {
        String inlineTarget = normalize(action.substring(colon + 1), null);
        action = action.substring(0, colon).trim();
        // Keep explicit/inherited target when present; otherwise use inline target.
        if (target == null || target.isBlank()) {
          target = inlineTarget;
        }
      }
    }
    MenuActionType type = MenuActionType.parse(action);
    if (type == MenuActionType.NOOP && !action.isBlank() && !isNoopAction(action)) {
      diagnostics.add("Unknown menu action '" + action + "' in " + sourcePath + " (" + property + "); falling back to noop");
    }
    if (type == MenuActionType.OPEN_MENU && (target == null || target.isBlank())) {
      diagnostics.add("OPEN_MENU action requires a target in " + sourcePath + " (" + property + ")");
    }
    if (type == MenuActionType.RUN_SCRIPT && (target == null || target.isBlank())) {
      diagnostics.add("RUN_SCRIPT action requires a script target in " + sourcePath + " (" + property + ")");
    }
    if (target != null && !target.isBlank()
        && type != MenuActionType.OPEN_MENU
        && type != MenuActionType.RUN_SCRIPT
        && type != MenuActionType.SETTINGS_MENU
        && type != MenuActionType.QUIT
        && type != MenuActionType.NOOP) {
      diagnostics.add("Target for action '" + action + "' is ignored in " + sourcePath + " (" + property + ")");
    }
    return new MenuActionSpec(type, target, action);
  }

  private static boolean isNoopAction(String raw) {
    String value = raw.trim().toLowerCase().replace('-', '_');
    return "noop".equals(value) || "no_op".equals(value) || "none".equals(value);
  }

  private static final Set<String> KNOWN_ITEM_FIELDS = Set.of(
      "label", "style", "icon", "enabled", "action", "target",
      "bgAsset", "bgSelectedAsset", "bgDisabledAsset",
      "boundsX", "boundsY", "boundsWidth", "boundsHeight",
      "slotPreviewEnabled", "slotPreviewPlaceholderAsset", "slotPreviewFrameAsset",
      "slotPreviewX", "slotPreviewY", "slotPreviewWidth", "slotPreviewHeight",
      "fontFamily", "fontWeight", "fontSize",
      "textAlign", "textX", "textY", "textBaselineY",
      "textPadX", "textPadY", "textPaddingX", "textPaddingY",
      "showValue", "valueVisible", "hideValue",
      "showBindingStatus", "hideBindingStatus",
      "sliderX", "sliderY", "sliderWidth",
      "sliderTrackAsset", "sliderBaseAsset", "sliderTrackHeight", "sliderShowFill",
      "sliderFillAsset", "sliderFillActiveAsset", "sliderFillInactiveAsset",
      "sliderKnobAsset", "sliderKnobActiveAsset", "sliderKnobInactiveAsset",
      "sliderKnobWidth", "sliderKnobHeight", "sliderKnobOffsetX", "sliderKnobOffsetY",
      "sliderResetAsset", "sliderResetActiveAsset", "sliderResetInactiveAsset",
      "sliderResetX", "sliderResetY", "sliderResetWidth", "sliderResetHeight",
      "toggleCheckedAsset", "toggleUncheckedAsset",
      "toggleX", "toggleY", "toggleWidth", "toggleHeight"
  );

  private static final Set<String> STRUCTURAL_ITEM_FIELDS = Set.of(
      "label", "style", "icon", "enabled", "action", "target",
      "bgAsset", "bgSelectedAsset", "bgDisabledAsset",
      "boundsX", "boundsY", "boundsWidth", "boundsHeight",
      "slotPreviewEnabled", "slotPreviewPlaceholderAsset", "slotPreviewFrameAsset",
      "slotPreviewX", "slotPreviewY", "slotPreviewWidth", "slotPreviewHeight",
      "fontFamily", "fontWeight", "fontSize"
  );

  private record BoundsFields(Double x, Double y, Double width, Double height) {}

  private static BoundsFields sanitizeBoundsFields(
      Double x,
      Double y,
      Double width,
      Double height,
      List<String> diagnostics,
      String sourcePath,
      String context
  ) {
    Double bx = x;
    Double by = y;
    Double bw = width;
    Double bh = height;
    int defined = 0;
    if (bx != null) defined++;
    if (by != null) defined++;
    if (bw != null) defined++;
    if (bh != null) defined++;
    if (defined > 0 && defined < 4) {
      diagnostics.add(context + " in " + sourcePath + " has partial bounds; X/Y/Width/Height must be set together");
      return new BoundsFields(null, null, null, null);
    }
    if (bx != null && bx < 0) {
      diagnostics.add(context + " in " + sourcePath + " has negative boundsX; using 0");
      bx = 0.0;
    }
    if (by != null && by < 0) {
      diagnostics.add(context + " in " + sourcePath + " has negative boundsY; using 0");
      by = 0.0;
    }
    if (bw != null && bw <= 0) {
      diagnostics.add(context + " in " + sourcePath + " has non-positive boundsWidth; dropping explicit bounds");
      return new BoundsFields(null, null, null, null);
    }
    if (bh != null && bh <= 0) {
      diagnostics.add(context + " in " + sourcePath + " has non-positive boundsHeight; dropping explicit bounds");
      return new BoundsFields(null, null, null, null);
    }
    return new BoundsFields(bx, by, bw, bh);
  }

  private static Map<String, String> collectItemExtras(Properties p, String itemPrefix, MenuItemSpec base) {
    Map<String, String> extras = new LinkedHashMap<>();
    if (base != null && base.extras() != null) {
      extras.putAll(base.extras());
    }
    for (String key : p.stringPropertyNames()) {
      if (!key.startsWith(itemPrefix)) continue;
      String field = key.substring(itemPrefix.length());
      if (STRUCTURAL_ITEM_FIELDS.contains(field)) continue;
      String value = p.getProperty(key);
      if (value != null && !value.isBlank()) {
        extras.put(field, value.trim());
      }
    }
    return extras.isEmpty() ? null : extras;
  }

  private static List<String> collectItemIdsFromProperties(Properties p) {
    Set<String> ids = new LinkedHashSet<>();
    for (String key : p.stringPropertyNames()) {
      if (!key.startsWith("item.")) continue;
      int dot = key.indexOf('.', 5);
      if (dot <= 5) continue;
      String id = key.substring(5, dot).trim();
      if (!id.isEmpty()) ids.add(id);
    }
    return new ArrayList<>(ids);
  }

  private static boolean isSlotTemplateItemId(String itemId) {
    String id = normalize(itemId, "").toLowerCase();
    return "save_slot".equals(id) || "slot".equals(id) || "entry".equals(id) || "new_slot".equals(id) || "new_save".equals(id) || "new".equals(id);
  }

  private static LoadedProperties loadFirstProperties(AssetCatalog assets, List<String> diagnostics, String purpose, String... paths) {
    if (assets == null || paths == null) return null;
    for (String path : paths) {
      if (path == null || path.isBlank()) continue;
      if (!assets.exists(AssetType.SCRIPT, path)) continue;
      try (InputStream in = assets.open(AssetType.SCRIPT, path)) {
        if (in == null) continue;
        Properties p = new Properties();
        p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        return new LoadedProperties(path, p);
      } catch (Exception ex) {
        if (isMissingAssetError(ex)) continue;
        diagnostics.add("Failed to parse " + purpose + " at '" + path + "': " + simplify(ex));
      }
    }
    return null;
  }

  private static String[] menuPaths(String id) {
    return new String[] {
        "config/menu/menus/" + id + ".menu",
        "config/menu/menus/" + id + ".properties",
        "config/menu/" + id + ".menu",
        "config/menu/" + id + ".properties",
        id + ".menu"
    };
  }

  private static String[] stylePaths(String id) {
    return new String[] {
        "config/menu/styles/" + id + ".style",
        "config/menu/styles/" + id + ".properties",
        "config/menu/" + id + ".style",
        id + ".style"
    };
  }

  private static String[] layoutPaths(String id) {
    return new String[] {
        "config/menu/layouts/" + id + ".layout",
        "config/menu/layouts/" + id + ".properties",
        "config/menu/" + id + ".layout",
        id + ".layout"
    };
  }

  private static Set<String> discoverIds(AssetCatalog assets, List<String> diagnostics, String directory, String... suffixes) {
    Set<String> ids = new LinkedHashSet<>();
    if (assets == null || directory == null || directory.isBlank()) return ids;
    try {
      List<String> entries = assets.list(directory);
      if (entries == null) return ids;
      for (String entry : entries) {
        String name = normalize(entry, null);
        if (name == null) continue;
        for (String suffix : suffixes) {
          if (suffix == null || suffix.isBlank()) continue;
          if (name.endsWith(suffix) && name.length() > suffix.length()) {
            ids.add(name.substring(0, name.length() - suffix.length()));
            break;
          }
        }
      }
    } catch (Exception ex) {
      diagnostics.add("Failed to discover menu files in '" + directory + "': " + simplify(ex));
    }
    return ids;
  }

  private static void warnUnknownLayoutKeys(Properties properties, List<String> diagnostics, String sourcePath) {
    if (properties == null) return;
    for (String key : properties.stringPropertyNames()) {
      if (KNOWN_LAYOUT_FIELDS.contains(key)) continue;
      String suggestion = closestKeyHint(key, KNOWN_LAYOUT_FIELDS);
      diagnostics.add("Unknown layout key '" + key + "' in " + sourcePath + suggestion);
    }
  }

  private static void warnUnknownStyleKeys(Properties properties, List<String> diagnostics, String sourcePath) {
    if (properties == null) return;
    for (String key : properties.stringPropertyNames()) {
      if (KNOWN_STYLE_FIELDS.contains(key)) continue;
      String suggestion = closestKeyHint(key, KNOWN_STYLE_FIELDS);
      diagnostics.add("Unknown style key '" + key + "' in " + sourcePath + suggestion);
    }
  }

  private static void warnUnknownScreenKeys(Properties properties, List<String> diagnostics, String sourcePath) {
    if (properties == null) return;
    for (String key : properties.stringPropertyNames()) {
      if (KNOWN_SCREEN_FIELDS.contains(key)) continue;
      if (!key.startsWith("item.")) {
        String suggestion = closestKeyHint(key, KNOWN_SCREEN_FIELDS);
        diagnostics.add("Unknown menu screen key '" + key + "' in " + sourcePath + suggestion);
        continue;
      }
      int secondDot = key.indexOf('.', "item.".length());
      if (secondDot <= "item.".length() || secondDot >= key.length() - 1) {
        diagnostics.add("Malformed item property key '" + key + "' in " + sourcePath + "; expected item.<id>.<field>");
        continue;
      }
      String field = key.substring(secondDot + 1);
      if (KNOWN_ITEM_FIELDS.contains(field)) continue;
      String suggestion = closestKeyHint(field, KNOWN_ITEM_FIELDS);
      if (suggestion.isBlank()) continue;
      diagnostics.add("Unknown item key '" + field + "' in " + sourcePath + " (" + key + ")" + suggestion);
    }
  }

  private static String closestKeyHint(String key, Set<String> candidates) {
    if (key == null || key.isBlank() || candidates == null || candidates.isEmpty()) return "";
    String source = key.trim().toLowerCase(Locale.ROOT);
    String best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (String candidate : candidates) {
      if (candidate == null || candidate.isBlank()) continue;
      String normalized = candidate.toLowerCase(Locale.ROOT);
      int distance = levenshteinDistance(source, normalized);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
    if (best == null || bestDistance > 2) return "";
    return " (did you mean '" + best + "'?)";
  }

  private static int levenshteinDistance(String a, String b) {
    if (a == null || b == null) return Integer.MAX_VALUE;
    int[][] dp = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
    for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        int deletion = dp[i - 1][j] + 1;
        int insertion = dp[i][j - 1] + 1;
        int substitution = dp[i - 1][j - 1] + cost;
        dp[i][j] = Math.min(Math.min(deletion, insertion), substitution);
      }
    }
    return dp[a.length()][b.length()];
  }

  private static boolean isKnownAlign(String value) {
    if (value == null) return false;
    String v = value.toLowerCase(Locale.ROOT);
    return "left".equals(v) || "center".equals(v) || "right".equals(v);
  }

  private static Double clampOptional(
      Double value,
      double min,
      double max,
      List<String> diagnostics,
      String sourcePath,
      String key
  ) {
    if (value == null) return null;
    double clamped = clamp(value, min, max);
    if (clamped != value) {
      diagnostics.add("Value for '" + key + "' in " + sourcePath + " was out of range (" + min + ".." + max + "): "
          + value + " (using " + clamped + ")");
    }
    return clamped;
  }

  private static List<String> parseCsv(String raw) {
    if (raw == null || raw.isBlank()) return new ArrayList<>();
    List<String> out = new ArrayList<>();
    String[] parts = raw.split(",");
    for (String part : parts) {
      String t = part == null ? "" : part.trim();
      if (!t.isEmpty()) out.add(t);
    }
    return out;
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }

  private static String normalizePrefix(String v, String def) {
    if (v == null) return def;
    return v.trim();
  }

  private static String configuredText(Properties properties, String key, String inheritedValue) {
    if (properties == null || key == null || !properties.containsKey(key)) return inheritedValue;
    String raw = properties.getProperty(key);
    if (raw == null) return inheritedValue;
    String trimmed = raw.trim();
    return trimmed.isEmpty() ? "" : trimmed;
  }

  private static double parseDouble(String raw, double def, List<String> diagnostics, String sourcePath, String key) {
    if (raw == null || raw.isBlank()) return def;
    try {
      double value = Double.parseDouble(raw.trim());
      if (!Double.isFinite(value)) throw new NumberFormatException("non-finite");
      return value;
    } catch (Exception ex) {
      diagnostics.add("Invalid number for '" + key + "' in " + sourcePath + ": '" + raw + "' (using " + def + ")");
      return def;
    }
  }

  private static Double parseOptionalDouble(String raw, Double def, List<String> diagnostics, String sourcePath, String key) {
    if (raw == null || raw.isBlank()) return def;
    try {
      double value = Double.parseDouble(raw.trim());
      if (!Double.isFinite(value)) throw new NumberFormatException("non-finite");
      return value;
    } catch (Exception ex) {
      diagnostics.add("Invalid number for '" + key + "' in " + sourcePath + ": '" + raw + "' (using " + def + ")");
      return def;
    }
  }

  private static Integer parseOptionalPositiveInt(String raw, Integer def, List<String> diagnostics, String sourcePath, String key) {
    Integer parsed = parseOptionalInt(raw, def, diagnostics, sourcePath, key);
    if (parsed == null) return null;
    if (parsed <= 0) {
      diagnostics.add("Invalid integer for '" + key + "' in " + sourcePath + ": '" + parsed + "' (must be > 0; using " + def + ")");
      return def;
    }
    return parsed;
  }

  private static Integer parseOptionalInt(String raw, Integer def, List<String> diagnostics, String sourcePath, String key) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception ex) {
      diagnostics.add("Invalid integer for '" + key + "' in " + sourcePath + ": '" + raw + "' (using " + def + ")");
      return def;
    }
  }

  private static boolean parseBoolean(String raw, boolean def, List<String> diagnostics, String sourcePath, String key) {
    if (raw == null || raw.isBlank()) return def;
    String v = raw.trim().toLowerCase(Locale.ROOT);
    if ("true".equals(v) || "yes".equals(v) || "1".equals(v) || "on".equals(v)) return true;
    if ("false".equals(v) || "no".equals(v) || "0".equals(v) || "off".equals(v)) return false;
    diagnostics.add("Invalid boolean for '" + key + "' in " + sourcePath + ": '" + raw + "' (using " + def + ")");
    return def;
  }

  private static double clamp(double value, double min, double max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  private static String simplify(Exception ex) {
    if (ex == null) return "unknown error";
    String message = ex.getMessage();
    if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
    return ex.getClass().getSimpleName() + ": " + message;
  }

  private static boolean isMissingAssetError(Exception ex) {
    if (ex == null) return false;
    String message = ex.getMessage();
    return message != null && message.startsWith("Asset not found:");
  }
}
