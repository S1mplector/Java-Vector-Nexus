package com.jvn.editor.ui;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.menu.HistoryMenuScene;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.PauseMenuScene;
import com.jvn.core.menu.SaveMenuScene;
import com.jvn.core.menu.SettingsScene;
import com.jvn.core.menu.config.MenuActionSpec;
import com.jvn.core.menu.config.MenuActionType;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.vn.Choice;
import com.jvn.core.vn.DialogueLine;
import com.jvn.core.vn.DialoguePresentationMode;
import com.jvn.core.vn.VnBackground;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;
import com.jvn.fx.menu.MenuRenderer;
import com.jvn.fx.menu.MenuTheme;
import com.jvn.fx.vn.VnRenderer;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static com.jvn.editor.ui.LayoutStudioWindowManager.Kind;

final class LayoutRuntimePreview implements AutoCloseable {
  record Element(Rectangle2D bounds, Rectangle2D space) {}
  static final String SAMPLE = "Sample menu";
  final Canvas frame = new Canvas();
  final Map<String, Element> elements = new LinkedHashMap<>();
  final String id;
  private final Kind kind;
  private final File file;
  private final @Nullable MenuRenderer menu;
  private final @Nullable VnRenderer dialogue;
  private final VnState state = new VnState();
  private final VnScenario sampleScenario = VnScenario.builder("layout_preview")
      .addNode(VnNode.builder(VnNodeType.DIALOGUE).dialogue(DialogueLine.builder()
          .speakerName("Speaker").text("This is how a line of dialogue appears in your game.").build()).build())
      .addNode(VnNode.builder(VnNodeType.CHOICE).choices(List.of(
          Choice.builder().text("First choice").build(), Choice.builder().text("Second choice").build())).build())
      .addNode(VnNode.builder(VnNodeType.DIALOGUE).dialogue(DialogueLine.builder()
          .text("A narration line uses the same layout without a speaker name.").build()).build())
      .build();
  private VnScenario scenario = sampleScenario;
  private File root;
  private @Nullable Path saveDir;
  private @Nullable VnSaveManager saves;
  MenuProfile profile = MenuProfile.defaults();
  MenuScreenSpec screen = MenuProfile.defaultMainScreen();
  List<String> screens = List.of();
  String screenId = "main";

  LayoutRuntimePreview(File file, @Nullable File root, Kind kind) {
    this.kind = kind;
    this.file = file;
    String filename = file.getName();
    id = filename.substring(0, filename.lastIndexOf('.'));
    frame.setId("layout-runtime-frame");
    if (kind == Kind.DIALOGUE_LAYOUT) {
      menu = null;
      dialogue = new VnRenderer(frame.getGraphicsContext2D());
      dialogue.setRenderedBoundsListener(this::record);
    } else {
      dialogue = null;
      menu = new MenuRenderer(frame.getGraphicsContext2D());
      menu.setRenderedBoundsListener(this::record);
    }
    state.setScenario(scenario);
    state.getHistory().addEntry("Speaker", "Earlier lines stay visible in this mode.");
    state.getHistory().addEntry("Reader", "The panel scrolls as the conversation continues.");
    state.setVariable("ui.dialogueFadeMs", 0);
    setRoot(root);
  }

  void setRoot(@Nullable File project) {
    root = project == null ? file.getAbsoluteFile().getParentFile() : project;
    if (menu != null) menu.setProjectRoot(root);
    if (dialogue != null) dialogue.setProjectRoot(root);
    Properties manifest = new Properties();
    Path manifestFile = root.toPath().resolve("jvn.project");
    if (Files.isRegularFile(manifestFile)) {
      try (Reader reader = Files.newBufferedReader(manifestFile)) {
        manifest.load(reader);
      } catch (IOException | IllegalArgumentException ignored) { /* Use runtime defaults. */ }
    }
    ApplicationConfig defaults = ApplicationConfig.builder().build();
    frame.setWidth(dimension(manifest, "width", defaults.width()));
    frame.setHeight(dimension(manifest, "height", defaults.height()));
  }

  void setBackdrop(@Nullable String path) {
    if (path == null) {
      scenario = sampleScenario;
    } else {
      var builder = VnScenario.builder(sampleScenario.getId());
      sampleScenario.getNodes().forEach(builder::addNode);
      scenario = builder.addBackground(new VnBackground("preview", path)).build();
    }
    state.setScenario(scenario);
    state.setCurrentBackgroundId(path == null ? null : "preview");
  }

  private static int dimension(Properties manifest, String key, int fallback) {
    try {
      int value = Integer.parseInt(manifest.getProperty(key, ""));
      return value > 0 ? value : fallback;
    } catch (NumberFormatException ex) { return fallback; }
  }

  private void record(String name, double x, double y, double w, double h,
                      double ox, double oy, double sw, double sh) {
    elements.put(name, new Element(new Rectangle2D(x, y, w, h), new Rectangle2D(ox, oy, sw, sh)));
  }

  void render(String source, @Nullable String requestedScreen, @Nullable String focusedItem, Map<String, String> openDocuments) throws IOException {
    double w = frame.getWidth(), h = frame.getHeight();
    var gc = frame.getGraphicsContext2D();
    gc.save();
    try {
      gc.setFill(Color.BLACK);
      gc.fillRect(0, 0, w, h);
      elements.clear();
      if (dialogue != null) {
        var result = VnUiLayoutLoader.parseWithDiagnostics(LayoutPropertyDocument.read(source),
            VnUiLayoutSpec.defaults(), VnUiStyleSpec.defaults());
        dialogue.setUiLayout(result.layout());
        dialogue.setUiStyle(result.style());
        dialogue.setTextBoxButtons(result.textBoxButtons());
        String mode = requestedScreen == null ? "Dialogue" : requestedScreen;
        state.setDialoguePresentationMode(switch (mode) {
          case "NVL" -> DialoguePresentationMode.NVL;
          case "Bubble" -> DialoguePresentationMode.BUBBLE;
          default -> DialoguePresentationMode.STANDARD;
        });
        state.setCurrentNodeIndex(switch (mode) {
          case "Choices" -> 1;
          case "Narration" -> 2;
          default -> 0;
        });
        state.setTextRevealProgress(10000);
        dialogue.render(state, scenario, w, h);
        return;
      }
      if (menu == null) return;
      AssetCatalog catalog = catalog(source, openDocuments);
      profile = MenuProfileLoader.load(catalog);
      menu.setTheme(MenuTheme.fromAssets(catalog, profile));
      if (kind == Kind.MENU_SCREEN) {
        screenId = id;
        screens = List.of(id);
      } else {
        screens = profile.screens().values().stream().filter(this::usesDocument)
            .map(MenuScreenSpec::id).sorted().toList();
        if (screens.isEmpty()) screens = List.of(SAMPLE);
        screenId = requestedScreen != null && screens.contains(requestedScreen) ? requestedScreen : screens.get(0);
      }
      if (SAMPLE.equals(screenId)) {
        var base = MenuProfile.defaultMainScreen();
        screen = new MenuScreenSpec(SAMPLE, "Menu preview", null,
            kind == Kind.MENU_LAYOUT ? id : base.layoutId(),
            kind == Kind.MENU_STYLE ? id : base.defaultStyleId(), true,
            List.of(new MenuItemSpec("example", "Menu item", null, null, true,
                MenuActionSpec.noop(), null, null, null, null, null, null, null)));
        var withSample = new HashMap<>(profile.screens());
        withSample.put(SAMPLE, screen);
        profile = new MenuProfile(profile.defaultScreenId(), withSample, profile.layouts(), profile.styles());
      } else {
        screen = profile.screen(screenId);
      }
      if (saves == null) {
        saveDir = Files.createTempDirectory("jvn-layout-preview-");
        saves = new VnSaveManager(saveDir.toString());
      }
      int focus = -1;
      for (int i = 0; i < screen.items().size(); i++) {
        if (screen.items().get(i).id().equals(focusedItem)) focus = i;
      }
      switch (presentation()) {
        case "settings" -> {
          var model = new SettingsScene(null, saves, null, state.getSettings(), null, null, profile, screenId);
          if (focus >= 0) model.setSelected(focus);
          menu.renderSettings(model, w, h);
        }
        case "load" -> menu.renderLoadMenu(new LoadMenuScene(null, saves, null, state.getSettings(), null, profile), w, h);
        case "save" -> menu.renderSaveMenu(new SaveMenuScene(null, saves, null, null, profile), w, h);
        case "pause" -> {
          var model = new PauseMenuScene(null, null, saves, null, null, profile);
          if (focus >= 0) model.setSelected(focus);
          menu.renderPauseMenu(model, w, h);
        }
        case "history" -> menu.renderHistoryMenu(new HistoryMenuScene(null, null, profile), w, h);
        default -> {
          var model = new MainMenuScene(null, state.getSettings(), saves, null, null, screenId, profile);
          if (focus >= 0) model.setSelected(focus);
          menu.renderMainMenu(model, w, h);
        }
      }
      boolean dock = screenId.equals(profile.defaultScreenId()) && menu.getTheme().isDockPresentation();
      if (kind == Kind.MENU_LAYOUT && !dock) recordAutomaticRows();
    } finally {
      gc.restore();
    }
  }

  private String presentation() {
    if (screenId.equals(profile.defaultScreenId())) return "main";
    boolean isSettingsTarget = profile.screens().values().stream().flatMap(menu -> menu.items().stream())
        .anyMatch(item -> item.action().type() == MenuActionType.SETTINGS_MENU && screenId.equals(item.action().target()));
    return isSettingsTarget ? "settings" : screenId;
  }

  private void recordAutomaticRows() {
    Rectangle2D group = null;
    Rectangle2D listSpace = null;
    var layout = profile.layout(screen.layoutId());
    for (MenuItemSpec item : screen.items()) {
      if (item.boundsX() != null) continue;
      Element element = elements.get(item.id());
      if (element == null) continue;
      var row = element.bounds();
      listSpace = element.space();
      if (group == null) group = row;
      else {
        double x = Math.min(group.getMinX(), row.getMinX()), y = Math.min(group.getMinY(), row.getMinY());
        group = new Rectangle2D(x, y, Math.max(group.getMaxX(), row.getMaxX()) - x,
            Math.max(group.getMaxY(), row.getMaxY()) - y);
      }
    }
    if (group == null || listSpace == null) return;
    double start = layout.listYStart() <= 1 ? layout.listYStart() * listSpace.getHeight() : layout.listYStart();
    elements.put("@list", new Element(group, new Rectangle2D(listSpace.getMinX(),
        group.getMinY() - start, listSpace.getWidth(), listSpace.getHeight())));
  }

  private boolean usesDocument(MenuScreenSpec candidate) {
    return kind == Kind.MENU_LAYOUT ? id.equals(candidate.layoutId()) :
        id.equals(candidate.defaultStyleId()) || candidate.items().stream().anyMatch(i -> id.equals(i.styleId()));
  }

  static String menuPath(Kind kind, File file) {
    String folder = switch (kind) {
      case MENU_LAYOUT -> "layouts";
      case MENU_STYLE -> "styles";
      default -> "menus";
    };
    return "config/menu/" + folder + "/" + file.getName();
  }

  /** Mount working buffers at their runtime paths without changing the project or global assets. */
  private AssetCatalog catalog(String source, Map<String, String> openDocuments) {
    Map<String, byte[]> buffers = new LinkedHashMap<>();
    openDocuments.forEach((name, text) -> buffers.put(name, text.getBytes(StandardCharsets.UTF_8)));
    buffers.put(menuPath(kind, file), source.getBytes(StandardCharsets.UTF_8));
    return new AssetCatalog(new FilesystemAssetManager(root.toPath()) {
      @Override public boolean exists(AssetType type, String name) {
        return (type == AssetType.SCRIPT && buffers.containsKey(name)) || super.exists(type, name);
      }
      @Override public InputStream open(AssetType type, String name) throws IOException {
        byte[] data = type == AssetType.SCRIPT ? buffers.get(name) : null;
        return data == null ? super.open(type, name) : new ByteArrayInputStream(data);
      }
      @Override public List<String> list(String dir) {
        Set<String> names = new LinkedHashSet<>(super.list(dir));
        String prefix = dir + "/";
        for (String name : buffers.keySet()) {
          if (name.startsWith(prefix)) {
            String relative = name.substring(prefix.length());
            if (!relative.contains("/")) names.add(relative);
          }
        }
        return new ArrayList<>(names);
      }
    });
  }

  @Override public void close() {
    if (dialogue != null) dialogue.dispose();
    if (menu != null) menu.dispose();
    if (saveDir != null) {
      try { Files.deleteIfExists(saveDir); }
      catch (IOException ex) { saveDir.toFile().deleteOnExit(); }
    }
  }
}
