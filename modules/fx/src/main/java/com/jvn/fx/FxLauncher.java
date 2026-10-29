package com.jvn.fx;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.demo.Example2DScene;
import com.jvn.core.engine.Engine;
import com.jvn.core.graphics.Camera2D;
import com.jvn.core.graphics.ViewportScaler2D;
import com.jvn.core.input.ActionMap;
import com.jvn.core.input.InputActions;
import com.jvn.core.input.InputCode;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.HistoryMenuScene;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.PauseMenuScene;
import com.jvn.core.menu.SaveMenuScene;
import com.jvn.core.menu.SettingsScene;
import com.jvn.core.phone.PhoneScene;
import com.jvn.core.phone.VnPhoneData;
import com.jvn.core.phone.VnPhonePropertiesCodec;
import com.jvn.core.phone.VnPhoneStateStore;
import com.jvn.core.project.ProjectHealthChecker;
import com.jvn.core.scene2d.Scene2D;
import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.core.vn.VnCharacterSceneAccessor;
import com.jvn.core.vn.VnEntryScriptResolver;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioLoader;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnTimelineAccessorProvider;
import com.jvn.core.vn.ui.VnCursorConfigLoader;
import com.jvn.core.vn.ui.VnUiActionButtonActions;
import com.jvn.fx.menu.MenuRenderer;
import com.jvn.fx.menu.MenuTheme;
import com.jvn.fx.phone.PhoneRenderer;
import com.jvn.fx.render.FxSceneRendererRegistry;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.fx.ui.ProjectFontResolver;
import com.jvn.core.diagnostics.PerformanceHud;
import com.jvn.fx.diagnostics.FxPerformanceHudOverlay;
import com.jvn.fx.vn.VnRenderer;
import com.sun.management.OperatingSystemMXBean;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

public class FxLauncher extends Application {
  private static final Logger log = LoggerFactory.getLogger(FxLauncher.class);
  private static final String DEFAULT_ENTRY_SCRIPT = "story/prologue.vns";
  private static final String ASSETS_ROOT_PROPERTY = "jvn.assets.root";
  private static final long PERF_HUD_UPDATE_INTERVAL_NS = 300_000_000L;
  private static final double PERF_CPU_SMOOTH_ALPHA = 0.28;
  private static final double PERF_FPS_SMOOTH_ALPHA = 0.20;
  private static Engine engine;
  private static boolean showPerfHud;
  private static String pendingAccessibilityTheme = "none";
  private static double pendingUiFontScale = 1.0;
  private AnimationTimer timer;
  private Canvas canvas;
  private GraphicsContext gc;
  private VnRenderer vnRenderer;
  private MenuRenderer menuRenderer;
  private PhoneRenderer phoneRenderer;
  private FxBlitter2D blitter2D;
  private FxSceneRendererRegistry rendererRegistry;
  private ActionMap actionMap;
  private Cursor configuredCursor = Cursor.DEFAULT;
  private javafx.scene.Scene fxScene;
  private @Nullable StackPane runtimeLoadingOverlay;
  private @Nullable PauseTransition runtimeLoadingRevealDelay;
  private @Nullable FadeTransition runtimeLoadingFade;
  private File runtimeProjectRoot;
  private ProjectHotReloadTracker hotReloadTracker;
  private double mouseX = 0;
  private double mouseY = 0;
  private OperatingSystemMXBean osBean;
  private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
  private final VnCharacterSceneAccessor emptyTimelineAccessor = new VnCharacterSceneAccessor();
  private HBox perfHud;
  private Label perfCpuLabel;
  private Label perfJvmLabel;
  private Label perfFpsLabel;
  private long lastPerfHudUpdateNs = -1L;
  private final PerformanceHud hudData = new PerformanceHud();
  private final FxPerformanceHudOverlay hudOverlay = new FxPerformanceHudOverlay(hudData);
  private double smoothedProcessCpu = Double.NaN;
  private double smoothedFps = Double.NaN;
  private boolean firstFrameRendered;

  public static void launch(Engine eng) {
    launch(eng, false);
  }

  public static void launch(Engine eng, boolean perfHudEnabled) {
    com.jvn.core.diagnostics.GraphicsPipeline.configure();
    engine = eng;
    showPerfHud = perfHudEnabled;
    Application.launch();
  }

  public static void launch(Engine eng, boolean perfHudEnabled, String accessibilityTheme) {
    launch(eng, perfHudEnabled, accessibilityTheme, 1.0);
  }

  public static void launch(
      Engine eng,
      boolean perfHudEnabled,
      String accessibilityTheme,
      double uiFontScale
  ) {
    com.jvn.core.diagnostics.GraphicsPipeline.configure();
    engine = eng;
    showPerfHud = perfHudEnabled;
    pendingAccessibilityTheme = accessibilityTheme != null ? accessibilityTheme : "none";
    pendingUiFontScale = Double.isFinite(uiFontScale)
        ? Math.max(0.75, Math.min(2.0, uiFontScale))
        : 1.0;
    Application.launch();
  }

  private void handleToggleHistory() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof HistoryMenuScene) {
      engine.scenes().pop();
      return;
    }
    if (currentScene instanceof VnScene vnScene) {
      vnScene.getState().clearHistoryScroll();
      engine.scenes().push(new HistoryMenuScene(engine, vnScene));
    }
  }

  @Override
  public void start(Stage primaryStage) {
    String title = engine != null && engine.getConfig() != null ? engine.getConfig().title() : "JVN";
    int width = engine != null && engine.getConfig() != null ? engine.getConfig().width() : 960;
    int height = engine != null && engine.getConfig() != null ? engine.getConfig().height() : 540;
    primaryStage.setTitle(title);

    StackPane root = new StackPane();
    this.canvas = new Canvas(width, height);
    this.phoneRenderer = new PhoneRenderer();
    root.getChildren().addAll(this.canvas, this.phoneRenderer);
    this.runtimeLoadingOverlay = createRuntimeLoadingOverlay();
    root.getChildren().add(this.runtimeLoadingOverlay);
    this.osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    if (showPerfHud) {
      this.perfHud = createPerfHud();
      root.getChildren().add(this.perfHud);
    }
    RuntimeAttributionOverlay attribution = new RuntimeAttributionOverlay(() -> {
      if (engine == null || engine.scenes().peek() instanceof SettingsScene) return;
      var current = engine.scenes().peek();
      VnScene gameplay = current instanceof VnScene vn ? vn
          : current instanceof PauseMenuScene pause ? pause.getVnScene()
          : current instanceof SaveMenuScene save ? save.getCurrentVnScene()
          : current instanceof HistoryMenuScene history ? history.getVnScene() : null;
      var settings = gameplay != null ? gameplay.getState().getSettings()
          : current instanceof MainMenuScene main ? main.getSettingsModel()
          : current instanceof LoadMenuScene load ? load.getSettingsModel() : new com.jvn.core.vn.VnSettingsStore().load();
      var audio = gameplay != null ? gameplay.getAudioFacade() : null;
      engine.scenes().push(new SettingsScene(engine, new com.jvn.core.vn.save.VnSaveManager(), "main", settings, audio));
    }, () -> {
      if (engine != null && engine.scenes().peek() instanceof SettingsScene) engine.scenes().pop();
    });
    root.getChildren().add(attribution);
    StackPane.setAlignment(canvas, javafx.geometry.Pos.TOP_CENTER);
    javafx.scene.Scene scene = new javafx.scene.Scene(root, width, height);
    this.fxScene = scene;
    primaryStage.setScene(scene);
    primaryStage.focusedProperty().addListener((obs, oldValue, focused) -> {
      if (focused) applyConfiguredCursor(scene);
    });
    scene.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> applyConfiguredCursor(scene));
    applyLinuxDefaultWindowState(primaryStage);
    primaryStage.show();
    log.info(
        "Runtime viewport -> window={}x{}, logical={}x{}",
        width,
        height,
        (int) targetLogicalWidth(width),
        (int) targetLogicalHeight(height)
    );

    // Initialize graphics context and resize canvas with scene
    this.gc = this.canvas.getGraphicsContext2D();
    this.vnRenderer = new VnRenderer(gc);
    MenuTheme menuTheme = MenuTheme.fromAssets();
    this.menuRenderer = new MenuRenderer(gc, menuTheme);
    if (engine != null && engine.scenes().peek() instanceof MainMenuScene main
        && menuTheme.getBgmPath() != null && !menuTheme.getBgmPath().isBlank()) {
      main.setTitleBgm(menuTheme.getBgmPath(), menuTheme.getBgmVolume());
    }
    this.menuRenderer.setUiFontScale(pendingUiFontScale);
    this.runtimeProjectRoot = resolveAssetsRoot();
    this.vnRenderer.setProjectRoot(runtimeProjectRoot);
    this.menuRenderer.setProjectRoot(runtimeProjectRoot);
    if (pendingAccessibilityTheme != null && !"none".equals(pendingAccessibilityTheme)) {
      this.vnRenderer.setAccessibilityTheme(pendingAccessibilityTheme);
    }
    this.phoneRenderer.setProjectRoot(runtimeProjectRoot);
    this.blitter2D = new FxBlitter2D(gc);
    this.rendererRegistry = createRendererRegistry();
    this.actionMap = loadActionBindings();
    this.hotReloadTracker = ProjectHotReloadTracker.create(runtimeProjectRoot);
    canvas.widthProperty().bind(root.widthProperty());
    canvas.heightProperty().bind(root.heightProperty().subtract(attribution.reservedHeightProperty()));
    applyConfiguredCursor(scene);
    logProjectHealth("startup");

    // Input handling
    scene.setOnKeyPressed(e -> {
      com.jvn.core.scene.Scene cur = engine != null ? engine.scenes().peek() : null;
      if (cur instanceof VnScene vn && vn.getActiveError() != null) {
        if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.R || e.getCode() == KeyCode.F5) {
          handleRuntimeErrorButton(vn, 1);
        } else if (e.getCode() == KeyCode.ESCAPE) {
          handleRuntimeErrorButton(vn, 0);
        } else if (e.getCode() == KeyCode.C) {
          handleRuntimeErrorButton(vn, 2);
        }
        // Never let an error-screen keystroke advance or mutate the scene below it.
        e.consume();
        return;
      }
      if (cur instanceof PhoneScene phone) {
        if (phoneRenderer != null && phoneRenderer.handleKeyPressed(e.getCode(), e.isShiftDown())) {
          if (phone.consumeCloseRequested() && engine != null) {
            engine.scenes().pop();
            syncPhoneOverlay(engine.scenes().peek());
          } else {
            syncPhoneOverlay(phone);
          }
          e.consume();
          return;
        }
      }
      if (cur instanceof HistoryMenuScene historyScene) {
        handleHistoryMenuInput(historyScene, e.getCode(), e.isShiftDown());
        e.consume();
        return;
      }

      if (e.isShiftDown() && e.getCode() == KeyCode.R) {
        handleManualHotReload();
        e.consume();
        return;
      }

      if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
        if (!handleMenuEnter()) handleAdvance();
      } else if (e.getCode() == KeyCode.CONTROL || e.getCode() == KeyCode.COMMAND) {
        // Ctrl/Cmd = Skip mode toggle
        handleToggleSkip();
      } else if (e.getCode() == KeyCode.A) {
        // A = Auto-play toggle
        handleToggleAutoPlay();
      } else if (e.getCode() == KeyCode.H) {
        // H = Hide UI
        handleToggleUI();
      } else if (e.getCode() == KeyCode.B) {
        // B = Toggle history/backlog overlay
        handleToggleHistory();
      } else if (e.getCode() == KeyCode.S) {
        // S = Settings (in-game)
        com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
        if (currentScene instanceof VnScene vn) {
          String fallbackScript = resolveDefaultScriptForMenus(vn);
          engine.scenes().push(new SettingsScene(
              engine,
              new com.jvn.core.vn.save.VnSaveManager(),
              fallbackScript,
              vn.getState().getSettings(),
              vn.getAudioFacade()
          ));
        }
      } else if (e.getCode() == KeyCode.ESCAPE) {
        // ESC = Pop menu if in menu, otherwise open pause menu from VN scene
        if (!handleMenuBack()) handleOpenPauseMenu();
      } else if (e.getCode() == KeyCode.UP) {
        handleMenuMove(-1);
      } else if (e.getCode() == KeyCode.DOWN) {
        handleMenuMove(1);
      } else if (e.getCode() == KeyCode.LEFT) {
        handleSettingsAdjust(-1);
      } else if (e.getCode() == KeyCode.RIGHT) {
        handleSettingsAdjust(1);
      } else if (e.getCode() == KeyCode.F5) {
        // F5 = Open save menu
        handleOpenSaveMenu();
      } else if (e.getCode() == KeyCode.F9) {
        // F9 = Open load menu
        handleOpenLoadMenu();
      } else if (e.getCode() == KeyCode.F6) {
        // F6 = Save menu (in-game)
        com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
        if (currentScene instanceof VnScene vn) {
          engine.scenes().push(new SaveMenuScene(engine, new com.jvn.core.vn.save.VnSaveManager(), vn));
        }
      } else if (e.getCode() == KeyCode.F3) {
        // F3 = Toggle performance HUD overlay
        hudOverlay.toggle();
      } else if (e.getCode() == KeyCode.F10) {
        // F10 = Launch 2D demo scene (developer shortcut)
        if (engine != null) engine.scenes().push(new Example2DScene());
      } else if (e.getCode() == KeyCode.F11) {
        // F11 = Launch Billiards scene (developer shortcut via reflection)
        try {
          Class<?> cls = Class.forName("com.jvn.billiards.scene.BilliardsScene2D");
          Object obj = cls.getDeclaredConstructor().newInstance();
          if (obj instanceof com.jvn.core.scene2d.Scene2D s2d && engine != null) {
            engine.scenes().push(s2d);
          }
        } catch (Throwable ignored) {}
      } else if (e.getCode() == KeyCode.DELETE) {
        handleMenuDelete();
      } else if (e.getCode() == KeyCode.R) {
        handleMenuRename();
      }

      // Feed to engine input system
      if (engine != null && engine.input() != null) {
        InputCode code = InputCode.key(e.getCode().getName());
        engine.input().keyDown(code);
        if (actionMap != null) {
          if (actionMap.matches(InputActions.ADVANCE, code)) { if (!handleMenuEnter()) handleAdvance(); }
          if (actionMap.matches(InputActions.MENU_CONFIRM, code)) { if (!handleMenuEnter()) handleAdvance(); }
          if (actionMap.matches(InputActions.SKIP_TOGGLE, code)) handleToggleSkip();
          if (actionMap.matches(InputActions.AUTO_TOGGLE, code)) handleToggleAutoPlay();
          if (actionMap.matches(InputActions.HIDE_UI, code)) handleToggleUI();
          if (actionMap.matches(InputActions.HISTORY, code)) handleToggleHistory();
          if (actionMap.matches(InputActions.SETTINGS, code)) {
            com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
            if (currentScene instanceof VnScene vn) {
              String fallbackScript = resolveDefaultScriptForMenus(vn);
              engine.scenes().push(new SettingsScene(
                  engine,
                  new com.jvn.core.vn.save.VnSaveManager(),
                  fallbackScript,
                  vn.getState().getSettings(),
                  vn.getAudioFacade()
              ));
            }
          }
          if (actionMap.matches(InputActions.QUICK_SAVE, code)) handleQuickSave();
          if (actionMap.matches(InputActions.QUICK_LOAD, code)) handleQuickLoad();
          if (actionMap.matches(InputActions.ROLLBACK, code)) handleRollback();
          if (actionMap.matches(InputActions.ROLLFORWARD, code)) handleRollforward();
          if (actionMap.matches(InputActions.SAVE_MENU, code)) {
            com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
            if (currentScene instanceof VnScene vn) {
              engine.scenes().push(new SaveMenuScene(engine, new com.jvn.core.vn.save.VnSaveManager(), vn));
            }
          }
          if (actionMap.matches(InputActions.MENU_BACK, code)) handleMenuBack();
          if (actionMap.matches(InputActions.MENU_UP, code)) handleMenuMove(-1);
          if (actionMap.matches(InputActions.MENU_DOWN, code)) handleMenuMove(1);
          if (actionMap.matches(InputActions.MENU_LEFT, code)) handleSettingsAdjust(-1);
          if (actionMap.matches(InputActions.MENU_RIGHT, code)) handleSettingsAdjust(1);
          if (actionMap.matches(InputActions.MENU_DELETE, code)) handleMenuDelete();
          if (actionMap.matches(InputActions.MENU_RENAME, code)) handleMenuRename();
        }
      }
    });

    

    scene.setOnKeyReleased(e -> {
      // Feed to engine input system
      if (engine != null && engine.input() != null) {
        engine.input().keyUp(com.jvn.core.input.InputCode.key(e.getCode().getName()));
      }
    });

    canvas.setOnMouseMoved(e -> {
      com.jvn.core.scene.Scene currentScene = engine != null ? engine.scenes().peek() : null;
      mouseX = toSceneX(e.getX(), currentScene);
      mouseY = toSceneY(e.getY(), currentScene);
      if (engine != null && engine.input() != null) engine.input().setMousePosition(mouseX, mouseY);
      // Hover selection for menus
      if (engine != null) {
        double rw = renderWidthForScene(currentScene);
        double rh = renderHeightForScene(currentScene);
        if (currentScene instanceof PauseMenuScene pause) {
          int idx = menuRenderer.getHoverIndexForPauseMenu(pause, rw, rh, mouseX, mouseY);
          if (idx >= 0) pause.setSelected(idx);
        } else if (currentScene instanceof MainMenuScene main) {
          int idx = menuRenderer.getHoverIndexForMainMenu(main, rw, rh, mouseX, mouseY);
          main.setSelected(idx);
        } else if (currentScene instanceof LoadMenuScene load) {
          int idx = menuRenderer.getHoverIndexForLoadMenu(load, rw, rh, mouseX, mouseY);
          if (idx >= 0) {
            load.setSelected(idx);
          }
        } else if (currentScene instanceof SettingsScene settings) {
          int idx = menuRenderer.getHoverIndexForSettings(settings, rw, rh, mouseX, mouseY);
          if (idx >= 0) settings.setSelected(idx);
        } else if (currentScene instanceof SaveMenuScene save) {
          int idx = menuRenderer.getHoverIndexForSaveMenu(save, rw, rh, mouseX, mouseY);
          if (idx >= 0) save.setSelected(idx);
        }
      }
    });

    canvas.setOnMousePressed(e -> {
      com.jvn.core.scene.Scene currentScene = engine != null ? engine.scenes().peek() : null;
      mouseX = toSceneX(e.getX(), currentScene);
      mouseY = toSceneY(e.getY(), currentScene);
      if (engine != null && engine.input() != null) {
        engine.input().setMousePosition(mouseX, mouseY);
        int btn = e.getButton() == MouseButton.PRIMARY ? 1 : (e.getButton() == MouseButton.MIDDLE ? 2 : 3);
        engine.input().mouseDown(btn);
      }
    });

    canvas.setOnMouseReleased(e -> {
      com.jvn.core.scene.Scene currentScene = engine != null ? engine.scenes().peek() : null;
      mouseX = toSceneX(e.getX(), currentScene);
      mouseY = toSceneY(e.getY(), currentScene);
      if (engine != null && engine.input() != null) {
        engine.input().setMousePosition(mouseX, mouseY);
        int btn = e.getButton() == MouseButton.PRIMARY ? 1 : (e.getButton() == MouseButton.MIDDLE ? 2 : 3);
        engine.input().mouseUp(btn);
      }
    });

    canvas.setOnMouseClicked(e -> {
      if (e.getButton() == MouseButton.PRIMARY) {
        com.jvn.core.scene.Scene currentScene = engine != null ? engine.scenes().peek() : null;
        if (!isPointInSceneViewport(e.getX(), e.getY(), currentScene)) {
          return;
        }
        handleMouseClick(toSceneX(e.getX(), currentScene), toSceneY(e.getY(), currentScene));
      } else if (e.getButton() == MouseButton.SECONDARY) {
        // Right-click = open pause menu or pop menu
        if (!handleMenuBack()) handleOpenPauseMenu();
      }
    });

    
    canvas.setOnScroll(e -> {
      if (engine != null && engine.input() != null) {
        engine.input().addScrollDeltaY(e.getDeltaY());
      }
      if (engine != null) {
        com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
        if (currentScene instanceof HistoryMenuScene historyScene) {
          double dy = e.getDeltaY();
          int step = e.isShiftDown() ? 6 : 2;
          if (dy > 0) {
            historyScene.scrollByLines(step);
          } else if (dy < 0) {
            historyScene.scrollByLines(-step);
          }
        }
      }
    });

    canvas.setOnMouseDragged(e -> {
      if (e.isPrimaryButtonDown()) {
        com.jvn.core.scene.Scene currentScene = engine != null ? engine.scenes().peek() : null;
        handleMouseDrag(toSceneX(e.getX(), currentScene), toSceneY(e.getY(), currentScene));
      }
    });

    timer = new AnimationTimer() {
      private long lastNs = -1L;

      @Override
      public void handle(long now) {
        if (lastNs < 0) { lastNs = now; return; }
        long deltaMs = (now - lastNs) / 1_000_000L;
        lastNs = now;
        if (engine != null) engine.update(deltaMs);
        if (engine != null && !engine.isStarted()) {
          if (timer != null) timer.stop();
          primaryStage.close();
          return;
        }
        syncRequestedVnMenus();
        com.jvn.core.scene.Scene currentScene = engine != null ? engine.scenes().peek() : null;
        if (currentScene instanceof PhoneScene phone && phone.consumeCloseRequested() && engine != null) {
          engine.scenes().pop();
          currentScene = engine.scenes().peek();
        }
        attribution.showSettingsCredit(currentScene instanceof SettingsScene);
        syncPhoneOverlay(currentScene);
        pollProjectHotReload();
        // Use the AnimationTimer's nanosecond clock for the HUD. Engine frame
        // statistics intentionally store millisecond deltas and can therefore
        // lose precision for fast frames.
        hudData.tick(now);
        updatePerfHud(now);

        // Render
        if (gc != null && canvas != null) {
          double w = canvas.getWidth();
          double h = canvas.getHeight();
          gc.setFill(Color.BLACK);
          gc.fillRect(0, 0, w, h);

          boolean rendered = false;
          if (rendererRegistry != null) {
            if (shouldUseAspectFitViewport(currentScene)) {
              ViewportScaler2D.Transform vp = viewportTransform(w, h);
              gc.save();
              gc.translate(vp.offsetX(), vp.offsetY());
              gc.scale(vp.scale(), vp.scale());
              rendered = rendererRegistry.render(currentScene,
                  new com.jvn.fx.render.FxSceneRendererRegistry.RenderContext(
                      gc, blitter2D, vp.targetWidth(), vp.targetHeight(), mouseX, mouseY));
              if (!rendered) drawDefaultScene(vp.targetWidth(), vp.targetHeight());
              gc.restore();
            } else {
              rendered = rendererRegistry.render(currentScene,
                  new com.jvn.fx.render.FxSceneRendererRegistry.RenderContext(gc, blitter2D, w, h, mouseX, mouseY));
              if (!rendered) drawDefaultScene(w, h);
            }
          } else {
            drawDefaultScene(w, h);
          }
          if (vnRenderer != null) {
            com.jvn.core.diagnostics.DrawCallStats drawStats = vnRenderer.getDrawCallStats();
            hudData.setDrawCallStats(drawStats.getCharacterLayerDraws(), drawStats.getOtherDraws());
            hudData.setStageLightingRecomposites(drawStats.getStageLightingRecomposites());
          }
          hudOverlay.render(gc, w, h);
          hideRuntimeLoadingOverlayAfterFirstFrame();
        }
      }
    };
    timer.start();
  }

  private StackPane createRuntimeLoadingOverlay() {
    ProgressIndicator indicator = new ProgressIndicator();
    indicator.setMouseTransparent(true);
    indicator.setMaxSize(34, 34);
    indicator.setStyle(
        "-fx-progress-color: #82c8f5;"
            + "-fx-padding: 2;");

    String projectTitle = engine != null && engine.getConfig() != null
        && engine.getConfig().title() != null && !engine.getConfig().title().isBlank()
            ? engine.getConfig().title()
            : "JVN";
    Label title = new Label(projectTitle);
    title.setStyle(
        "-fx-text-fill: #f4f7fa;"
            + "-fx-font-size: 18px;"
            + "-fx-font-weight: 800;");
    Label status = new Label("Preparing the first frame…");
    status.setStyle(
        "-fx-text-fill: #aeb9c5;"
            + "-fx-font-size: 12px;");

    HBox progressRow = new HBox(12, indicator, status);
    progressRow.setAlignment(Pos.CENTER_LEFT);
    VBox card = new VBox(8, title, progressRow);
    card.setAlignment(Pos.CENTER_LEFT);
    card.setMaxSize(360, Region.USE_PREF_SIZE);
    card.setPadding(new Insets(20, 24, 20, 24));
    card.setStyle(
        "-fx-background-color: rgba(17, 21, 27, 0.96);"
            + "-fx-background-radius: 12;"
            + "-fx-border-color: rgba(145, 170, 194, 0.52);"
            + "-fx-border-width: 1;"
            + "-fx-border-radius: 12;"
            + "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.58), 28, 0.24, 0, 8);");

    StackPane overlay = new StackPane(card);
    overlay.setMouseTransparent(true);
    overlay.setPickOnBounds(false);
    overlay.setAlignment(Pos.CENTER);
    overlay.setOpacity(0.0);
    overlay.setStyle("-fx-background-color: rgba(3, 5, 8, 0.58);");

    runtimeLoadingRevealDelay = new PauseTransition(Duration.millis(160));
    runtimeLoadingRevealDelay.setOnFinished(event -> {
      if (runtimeLoadingOverlay != overlay || firstFrameRendered) return;
      runtimeLoadingFade = new FadeTransition(Duration.millis(150), overlay);
      runtimeLoadingFade.setFromValue(overlay.getOpacity());
      runtimeLoadingFade.setToValue(1.0);
      runtimeLoadingFade.play();
    });
    runtimeLoadingRevealDelay.play();
    return overlay;
  }

  private void hideRuntimeLoadingOverlayAfterFirstFrame() {
    if (firstFrameRendered) return;
    firstFrameRendered = true;
    if (runtimeLoadingOverlay == null) return;
    if (runtimeLoadingRevealDelay != null) {
      runtimeLoadingRevealDelay.stop();
      runtimeLoadingRevealDelay = null;
    }
    if (runtimeLoadingFade != null) runtimeLoadingFade.stop();

    StackPane overlay = runtimeLoadingOverlay;
    if (overlay.getOpacity() <= 0.01) {
      removeRuntimeLoadingOverlay(overlay);
      return;
    }
    runtimeLoadingFade = new FadeTransition(Duration.millis(130), overlay);
    runtimeLoadingFade.setFromValue(overlay.getOpacity());
    runtimeLoadingFade.setToValue(0.0);
    runtimeLoadingFade.setOnFinished(event -> removeRuntimeLoadingOverlay(overlay));
    runtimeLoadingFade.play();
  }

  private void removeRuntimeLoadingOverlay(StackPane overlay) {
    if (overlay == null) return;
    overlay.setVisible(false);
    if (overlay.getParent() instanceof StackPane parent) {
      parent.getChildren().remove(overlay);
    }
    if (runtimeLoadingOverlay == overlay) runtimeLoadingOverlay = null;
    runtimeLoadingFade = null;
  }

  private HBox createPerfHud() {
    perfCpuLabel = createPerfHudLabel("CPU --", "#f27333");
    perfJvmLabel = createPerfHudLabel("JVM -- MB", "#49a5ff");
    perfFpsLabel = createPerfHudLabel("FPS --", "#f4f4f4");

    HBox box = new HBox(6, perfCpuLabel, perfJvmLabel, perfFpsLabel);
    box.setAlignment(Pos.CENTER_LEFT);
    box.setMouseTransparent(true);
    box.setPickOnBounds(false);
    box.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    box.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
    box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    StackPane.setAlignment(box, Pos.TOP_LEFT);
    StackPane.setMargin(box, new Insets(10, 0, 0, 12));
    return box;
  }

  private Label createPerfHudLabel(String text, String color) {
    Label label = new Label(text);
    label.setMouseTransparent(true);
    label.setStyle(
        "-fx-text-fill: " + color + ";"
            + "-fx-font-size: 11px;"
            + "-fx-font-weight: 700;"
            + "-fx-padding: 4 8 4 8;"
            + "-fx-background-color: rgba(0, 0, 0, 0.72);"
            + "-fx-background-radius: 999;"
            + "-fx-border-color: rgba(255, 255, 255, 0.12);"
            + "-fx-border-width: 1;"
            + "-fx-border-radius: 999;");
    return label;
  }

  private void updatePerfHud(long nowNs) {
    if (!showPerfHud || perfHud == null || engine == null) return;
    if (lastPerfHudUpdateNs > 0L && (nowNs - lastPerfHudUpdateNs) < PERF_HUD_UPDATE_INTERVAL_NS) return;
    lastPerfHudUpdateNs = nowNs;

    double processCpu = Double.NaN;
    if (osBean != null) {
      processCpu = osBean.getProcessCpuLoad();
    }
    smoothedProcessCpu = smoothRatio(smoothedProcessCpu, processCpu, PERF_CPU_SMOOTH_ALPHA);
    smoothedFps = smoothRatio(smoothedFps, hudData.getFps(), PERF_FPS_SMOOTH_ALPHA);

    MemoryUsage heap = memoryBean.getHeapMemoryUsage();
    MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
    double heapUsedMb = Math.max(0.0, bytesToMb(heap == null ? -1L : heap.getUsed()));
    double nonHeapMb = Math.max(0.0, bytesToMb(nonHeap == null ? -1L : nonHeap.getUsed()));
    double jvmUsedMb = Math.max(0.0, heapUsedMb + nonHeapMb);

    String cpuText = isRatioValid(smoothedProcessCpu)
        ? String.format(Locale.ROOT, "CPU %.0f%%", smoothedProcessCpu * 100.0)
        : "CPU --";
    String jvmText = jvmMemoryText(jvmUsedMb);
    String fpsText = fpsText(smoothedFps);

    perfCpuLabel.setText(cpuText);
    perfJvmLabel.setText(jvmText);
    perfFpsLabel.setText(fpsText);
  }

  private FxSceneRendererRegistry createRendererRegistry() {
    FxSceneRendererRegistry reg = new FxSceneRendererRegistry();
    reg.register(VnScene.class, (vn, ctx) -> {
      vnRenderer.setAudioFacade(vn.getAudioFacade());
      syncTimelineAccessor(vn);
      vnRenderer.render(vn.getState(), vn.getScenario(), ctx.width(), ctx.height(), mouseX, mouseY);
      if (vn.hasActiveError()) {
        vnRenderer.renderErrorOverlay(vn.getActiveError(), ctx.width(), ctx.height(), mouseX, mouseY);
      }
    });

    reg.register(PauseMenuScene.class, (pause, ctx) -> {
      // Render the underlying VN scene first, then the pause overlay
      VnScene vn = pause.getVnScene();
      if (vn != null) {
        vnRenderer.setAudioFacade(vn.getAudioFacade());
        syncTimelineAccessor(vn);
        vnRenderer.renderFrozen(vn.getState(), vn.getScenario(), ctx.width(), ctx.height());
      }
      menuRenderer.renderPauseMenu(pause, ctx.width(), ctx.height());
    });
    reg.register(HistoryMenuScene.class, (history, ctx) -> {
      VnScene vn = history.getVnScene();
      if (vn != null) {
        vnRenderer.setAudioFacade(vn.getAudioFacade());
        syncTimelineAccessor(vn);
        vnRenderer.renderFrozen(vn.getState(), vn.getScenario(), ctx.width(), ctx.height());
      }
      menuRenderer.renderHistoryMenu(history, ctx.width(), ctx.height());
    });
    reg.register(PhoneScene.class, (phone, ctx) -> {
      VnScene vn = phone.getVnScene();
      if (vn != null) {
        vnRenderer.setAudioFacade(vn.getAudioFacade());
        syncTimelineAccessor(vn);
        vnRenderer.render(vn.getState(), vn.getScenario(), ctx.width(), ctx.height(), mouseX, mouseY);
      } else {
        ctx.gc().setFill(Color.BLACK);
        ctx.gc().fillRect(0, 0, ctx.width(), ctx.height());
      }
    });
    reg.register(MainMenuScene.class, (scene, ctx) -> menuRenderer.renderMainMenu(scene, ctx.width(), ctx.height()));
    reg.register(LoadMenuScene.class, (scene, ctx) -> {
      renderFrozenVnUnderlay(scene.getGameplayVnScene(), ctx.width(), ctx.height());
      menuRenderer.renderLoadMenu(scene, ctx.width(), ctx.height());
    });
    reg.register(SettingsScene.class, (scene, ctx) -> {
      renderFrozenVnUnderlay(scene.getGameplayVnScene(), ctx.width(), ctx.height());
      menuRenderer.renderSettings(scene, ctx.width(), ctx.height());
    });
    reg.register(SaveMenuScene.class, (scene, ctx) -> {
      renderFrozenVnUnderlay(scene.getCurrentVnScene(), ctx.width(), ctx.height());
      menuRenderer.renderSaveMenu(scene, ctx.width(), ctx.height());
    });

    reg.register(Scene2D.class, (scene2D, ctx) -> {
      double w = ctx.width();
      double h = ctx.height();
      FxBlitter2D b = ctx.blitter();
      b.setViewport(w, h);
      b.clear(0, 0, 0, 1);
      if (scene2D instanceof Scene2DBase s2db) {
        if (engine != null) s2db.setInput(engine.input());
        if (s2db.getCamera() == null) s2db.setCamera(new Camera2D());
      }
      double targetW = (engine != null && engine.getConfig() != null) ? engine.getConfig().width() : w;
      double targetH = (engine != null && engine.getConfig() != null) ? engine.getConfig().height() : h;
      targetW = targetLogicalWidth(targetW);
      targetH = targetLogicalHeight(targetH);
      var vp = ViewportScaler2D.fit(targetW, targetH, w, h);
      b.push();
      b.translate(vp.offsetX(), vp.offsetY());
      b.scale(vp.scale(), vp.scale());
      scene2D.render(b, vp.targetWidth(), vp.targetHeight());
      b.pop();
    });
    return reg;
  }

  private void syncTimelineAccessor(VnScene vn) {
    if (vn != null && vn.getInterop() instanceof VnTimelineAccessorProvider provider) {
      vnRenderer.setTimelineAccessor(provider.getTimelineAccessor());
    } else {
      vnRenderer.setTimelineAccessor(emptyTimelineAccessor);
    }
  }

  private void renderFrozenVnUnderlay(VnScene vn, double width, double height) {
    if (vn == null) return;
    vnRenderer.setAudioFacade(vn.getAudioFacade());
    syncTimelineAccessor(vn);
    vnRenderer.renderFrozen(vn.getState(), vn.getScenario(), width, height);
  }

  private void applyConfiguredCursor(javafx.scene.Scene scene) {
    if (scene == null) return;
    VnCursorConfigLoader.LoadResult loadResult = VnCursorConfigLoader.loadFromAssetsWithDiagnostics(new AssetCatalog());
    for (String diagnostic : loadResult.diagnostics()) {
      log.warn("Cursor config: {}", diagnostic);
    }
    VnCursorConfigLoader.VnCursorConfig config = loadResult.config();
    if (config == null || config.assetPath() == null || config.assetPath().isBlank()) {
      File projectRoot = resolveAssetsRoot();
      if (projectRoot != null) {
        VnCursorConfigLoader.LoadResult projectLoad = VnCursorConfigLoader.loadFromProjectRootWithDiagnostics(projectRoot);
        for (String diagnostic : projectLoad.diagnostics()) {
          log.warn("Cursor config (project): {}", diagnostic);
        }
        config = projectLoad.config();
      }
    }
    if (config == null || config.assetPath() == null || config.assetPath().isBlank()) {
      applyCursor(scene, Cursor.DEFAULT);
      return;
    }

    Image image = loadCursorImage(config.assetPath());
    if (image == null || image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
      log.warn("Cursor asset could not be loaded: {}", config.assetPath());
      applyCursor(scene, Cursor.DEFAULT);
      return;
    }

    double hotspotX = Math.max(0.0, Math.min(config.hotspotX(), Math.max(0.0, image.getWidth() - 1)));
    double hotspotY = Math.max(0.0, Math.min(config.hotspotY(), Math.max(0.0, image.getHeight() - 1)));
    applyCursor(scene, new ImageCursor(image, hotspotX, hotspotY));
    log.info("Applied custom cursor '{}' ({}, {})", config.assetPath(), hotspotX, hotspotY);
  }

  private Image loadCursorImage(String path) {
    if (path == null || path.isBlank()) return null;
    try {
      AssetCatalog assets = new AssetCatalog();
      var url = assets.url(AssetType.IMAGE, path);
      if (url != null) {
        Image image = new Image(url.toExternalForm());
        if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0) return image;
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }

    try {
      var classpath = getClass().getClassLoader().getResource(path);
      if (classpath != null) {
        Image image = new Image(classpath.toExternalForm());
        if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0) return image;
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }

    try {
      File file = new File(path);
      if (file.exists()) {
        Image image = new Image(file.toURI().toString());
        if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0) return image;
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }

    try {
      File projectRoot = resolveAssetsRoot();
      if (projectRoot != null) {
        File relative = new File(projectRoot, path);
        if (relative.exists()) {
          Image image = new Image(relative.toURI().toString());
          if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0) return image;
        }
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return null;
  }

  private File resolveAssetsRoot() {
    String raw = System.getProperty(ASSETS_ROOT_PROPERTY, "");
    if (raw == null || raw.isBlank()) return null;
    File root = new File(raw.trim());
    return root.exists() && root.isDirectory() ? root : null;
  }

  private void applyCursor(javafx.scene.Scene scene, Cursor cursor) {
    configuredCursor = cursor == null ? Cursor.DEFAULT : cursor;
    scene.setCursor(configuredCursor);
    if (scene.getRoot() != null) {
      scene.getRoot().setCursor(configuredCursor);
    }
    if (canvas != null) {
      canvas.setCursor(configuredCursor);
    }
    if (phoneRenderer != null) {
      phoneRenderer.setCursor(configuredCursor);
    }
  }

  private boolean shouldUseAspectFitViewport(com.jvn.core.scene.Scene scene) {
    return !(scene instanceof Scene2D);
  }

  private ViewportScaler2D.Transform viewportTransform(double viewportWidth, double viewportHeight) {
    return ViewportScaler2D.fit(
        targetLogicalWidth(viewportWidth),
        targetLogicalHeight(viewportHeight),
        viewportWidth,
        viewportHeight
    );
  }

  private double targetLogicalWidth(double fallback) {
    int override = logicalRenderWidthOverride();
    if (override > 0) {
      return override;
    }
    if (engine != null && engine.getConfig() != null && engine.getConfig().width() > 0) {
      return engine.getConfig().width();
    }
    return Math.max(1.0, fallback);
  }

  private double targetLogicalHeight(double fallback) {
    int override = logicalRenderHeightOverride();
    if (override > 0) {
      return override;
    }
    if (engine != null && engine.getConfig() != null && engine.getConfig().height() > 0) {
      return engine.getConfig().height();
    }
    return Math.max(1.0, fallback);
  }

  private double renderWidthForScene(com.jvn.core.scene.Scene scene) {
    if (canvas == null || !shouldUseAspectFitViewport(scene)) {
      return canvas != null ? canvas.getWidth() : 0.0;
    }
    return targetLogicalWidth(canvas.getWidth());
  }

  private double renderHeightForScene(com.jvn.core.scene.Scene scene) {
    if (canvas == null || !shouldUseAspectFitViewport(scene)) {
      return canvas != null ? canvas.getHeight() : 0.0;
    }
    return targetLogicalHeight(canvas.getHeight());
  }

  private double toSceneX(double canvasX, com.jvn.core.scene.Scene scene) {
    if (canvas == null || !shouldUseAspectFitViewport(scene)) return canvasX;
    ViewportScaler2D.Transform vp = viewportTransform(canvas.getWidth(), canvas.getHeight());
    return vp.screenToLogicalX(canvasX);
  }

  private double toSceneY(double canvasY, com.jvn.core.scene.Scene scene) {
    if (canvas == null || !shouldUseAspectFitViewport(scene)) return canvasY;
    ViewportScaler2D.Transform vp = viewportTransform(canvas.getWidth(), canvas.getHeight());
    return vp.screenToLogicalY(canvasY);
  }

  private boolean isPointInSceneViewport(double canvasX, double canvasY, com.jvn.core.scene.Scene scene) {
    if (canvas == null || !shouldUseAspectFitViewport(scene)) return true;
    ViewportScaler2D.Transform vp = viewportTransform(canvas.getWidth(), canvas.getHeight());
    return vp.containsScreen(canvasX, canvasY);
  }

  private static int firstPositiveSystemProperty(String... keys) {
    if (keys == null) return 0;
    for (String key : keys) {
      if (key == null || key.isBlank()) continue;
      String raw = System.getProperty(key);
      if (raw == null || raw.isBlank()) continue;
      try {
        int parsed = Integer.parseInt(raw.trim());
        if (parsed > 0) return parsed;
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
    }
    return 0;
  }

  private static int logicalRenderWidthOverride() {
    return firstPositiveSystemProperty(
        "jvn.render.width",
        "jvn.renderWidth",
        "jvn.logical.width",
        "jvn.logicalWidth"
    );
  }

  private static int logicalRenderHeightOverride() {
    return firstPositiveSystemProperty(
        "jvn.render.height",
        "jvn.renderHeight",
        "jvn.logical.height",
        "jvn.logicalHeight"
    );
  }

  private void drawDefaultScene(double width, double height) {
    gc.setFill(Color.WHITE);
    gc.fillText("JVN - Java Visual Novel", 20, 30);
    gc.fillText("No scene loaded. Push a Scene to the engine's scene manager.", 20, 60);
  }

  private void syncPhoneOverlay(com.jvn.core.scene.Scene currentScene) {
    if (phoneRenderer == null) return;
    if (currentScene instanceof PhoneScene phone) {
      phoneRenderer.setSceneModel(phone);
    } else {
      phoneRenderer.setSceneModel(null);
    }
  }

  private ActionMap loadActionBindings() {
    try {
      return FxLauncherBindings.load();
    } catch (Exception e) {
      ActionMap map = new ActionMap(new com.jvn.core.input.Input());
      map.loadProfile(InputActions.defaultProfile());
      return map;
    }
  }

  private void handleAdvance() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      ((VnScene) currentScene).advance();
    }
  }

  private void handleToggleSkip() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      ((VnScene) currentScene).toggleSkipMode();
    }
  }

  private void handleToggleAutoPlay() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      ((VnScene) currentScene).toggleAutoPlayMode();
    }
  }

  private void handleToggleUI() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      ((VnScene) currentScene).getState().toggleUiHidden();
    }
  }

  private void handleQuickSave() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      VnScene vn = (VnScene) currentScene;
      boolean success = vn.quickSave();
      if (success) {
        try { writeQuickSaveThumbnail(vn); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
      }
      // HUD toast
      vn.getState().showHudMessage(success ? "Quick saved" : "Quick save failed", 1500);
    }
  }

  private void handleQuickLoad() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      VnScene vn = (VnScene) currentScene;
      boolean success = vn.quickLoad();
      vn.getState().showHudMessage(success ? "Quick loaded" : "Quick load failed", 1500);
    }
  }

  private void handleRollback() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      boolean success = vn.rollback();
      if (success) {
        vn.getState().showHudMessage("Rolled back", 800);
      }
    }
  }

  private void handleRollforward() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      boolean success = vn.rollforward();
      if (success) {
        vn.getState().showHudMessage("Rolled forward", 800);
      }
    }
  }

  private void handleOpenSaveMenu() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      engine.scenes().push(new SaveMenuScene(engine, new com.jvn.core.vn.save.VnSaveManager(), vn));
    }
  }

  private void handleOpenLoadMenu() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      String fallbackScript = resolveDefaultScriptForMenus(vn);
      engine.scenes().push(new LoadMenuScene(
          engine,
          new com.jvn.core.vn.save.VnSaveManager(),
          fallbackScript,
          vn.getState().getSettings(),
          vn.getAudioFacade()
      ));
    }
  }

  private void handleHistoryMenuInput(HistoryMenuScene historyScene, KeyCode code, boolean shiftDown) {
    if (historyScene == null) return;
    int pageLines = historyScene.linesPerPage(renderHeightForScene(historyScene) > 0
        ? renderHeightForScene(historyScene)
        : 540.0);
    int step = shiftDown ? 5 : 1;
    if (code == KeyCode.ESCAPE || code == KeyCode.SPACE || code == KeyCode.ENTER || code == KeyCode.B) {
      historyScene.close();
    } else if (code == KeyCode.UP) {
      historyScene.scrollByLines(step);
    } else if (code == KeyCode.DOWN) {
      historyScene.scrollByLines(-step);
    } else if (code == KeyCode.PAGE_UP) {
      historyScene.scrollByLines(pageLines);
    } else if (code == KeyCode.PAGE_DOWN) {
      historyScene.scrollByLines(-pageLines);
    }
  }

  private void syncRequestedVnMenus() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (!(currentScene instanceof VnScene vn)) return;
    var state = vn.getState();
    if (state == null) return;
    if (state.isSaveSlotOverlayShown()) {
      boolean saveMode = state.isSaveSlotOverlaySaveMode();
      state.hideSaveSlotOverlay();
      if (saveMode) {
        engine.scenes().push(new SaveMenuScene(engine, new com.jvn.core.vn.save.VnSaveManager(), vn));
      } else {
        String fallbackScript = resolveDefaultScriptForMenus(vn);
        engine.scenes().push(new LoadMenuScene(
            engine,
            new com.jvn.core.vn.save.VnSaveManager(),
            fallbackScript,
            state.getSettings(),
            vn.getAudioFacade()
        ));
      }
      return;
    }
    if (state.isHistoryOverlayShown()) {
      state.setHistoryOverlayShown(false);
      engine.scenes().push(new HistoryMenuScene(engine, vn));
    }
  }

  private void writeSaveThumbnail(VnScene vnScene, String slotName) {
    try {
      if (vnScene == null || slotName == null || slotName.isBlank()) return;
      String dir = System.getProperty("user.home") + "/.jvn/saves";
      Path d = Paths.get(dir);
      Files.createDirectories(d);
      File out = d.resolve(slotName + ".png").toFile();
      captureVnThumbnail(vnScene, out);
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
  }

  private void handleMouseClick(double x, double y) {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    double rw = renderWidthForScene(currentScene);
    double rh = renderHeightForScene(currentScene);
    if (currentScene instanceof VnScene) {
      VnScene vnScene = (VnScene) currentScene;

      // Handle error overlay clicks before anything else
      if (vnScene.hasActiveError()) {
        int errBtn = vnRenderer.renderErrorOverlay(vnScene.getActiveError(), rw, rh, x, y);
        if (errBtn >= 0) {
          handleRuntimeErrorButton(vnScene, errBtn);
        }
        return;
      }

      com.jvn.core.vn.ui.VnOverlayButtonSpec overlayButton =
          vnRenderer.getHoveredOverlayButton(vnScene.getState(), rw, rh, x, y);
      if (overlayButton != null && executeOverlayButtonAction(vnScene, overlayButton)) {
        return;
      }
      if (vnScene.getState().hasModalOverlayScreen()) {
        if (vnScene.getState().getTopOverlayScreen() != null
            && vnScene.getState().getTopOverlayScreen().isDismissOnAdvance()) {
          vnScene.getState().dismissTopOverlayScreen();
        }
        return;
      }

      com.jvn.core.vn.ui.VnUiActionButtonSpec textBoxButton =
          vnRenderer.getHoveredTextBoxButton(vnScene.getState(), rw, rh, x, y);
      if (textBoxButton != null && executeTextBoxButtonAction(vnScene, textBoxButton)) {
        return;
      }
      
      // Check if clicking on a choice
      if (vnScene.getState().getCurrentNode() != null && 
          vnScene.getState().getCurrentNode().getType() == com.jvn.core.vn.VnNodeType.CHOICE) {
        int choiceIndex = vnRenderer.getHoveredChoiceIndex(
          vnScene.getState().getCurrentNode().getChoices(),
          rw, rh, x, y
        );
        if (choiceIndex >= 0) {
          vnScene.selectChoice(choiceIndex);
          return;
        }
      }
      
      // Otherwise treat as advance
      vnScene.advanceFromClick();
    } else if (currentScene instanceof PauseMenuScene pause) {
      int idx = menuRenderer.getHoverIndexForPauseMenu(pause, rw, rh, x, y);
      if (idx >= 0) {
        pause.setSelected(idx);
        pause.activateSelected();
      }
    } else if (currentScene instanceof MainMenuScene main) {
      int idx = menuRenderer.getHoverIndexForMainMenu(main, rw, rh, x, y);
      if (idx >= 0) {
        main.setSelected(idx);
        main.activateSelected();
      }
    } else if (currentScene instanceof HistoryMenuScene history) {
      history.close();
    } else if (currentScene instanceof LoadMenuScene load) {
      var controlHit = menuRenderer.getLoadControlHit(load, rw, rh, x, y);
      if (controlHit != null && controlHit.handled()) {
        switch (controlHit.type()) {
          case CYCLE_LEFT -> load.movePage(-1);
          case CYCLE_RIGHT -> load.movePage(1);
          case TOGGLE_FAVORITES_ONLY -> load.toggleFavoritesOnly();
          case TOGGLE_SLOT_FAVORITE -> load.toggleFavoriteAt(controlHit.saveIndex());
          case SET_PAGE -> load.setPageFromProgress01(controlHit.pageProgress01());
          default -> {
          }
        }
        return;
      }
      int idx = menuRenderer.getHoverIndexForLoadMenu(load, rw, rh, x, y);
      if (idx >= 0) {
        load.setSelected(idx);
        load.activateSelected();
      }
    } else if (currentScene instanceof SettingsScene settings) {
      int idx = menuRenderer.getHoverIndexForSettings(settings, rw, rh, x, y);
      if (idx >= 0) {
        settings.setSelected(idx);
        if (!settings.hasSliderAt(idx)) {
          settings.toggleCurrent();
          if (settings.consumeCloseRequested()) engine.scenes().pop();
        } else {
          if (menuRenderer.isSettingsSliderResetHit(settings, idx, rw, rh, x, y)) {
            settings.resetValueByIndex(idx);
          } else {
            double val = menuRenderer.computeSettingsSliderValue01(settings, idx, rw, rh, x);
            settings.setValueByIndex(idx, val);
          }
        }
      }
    } else if (currentScene instanceof SaveMenuScene save) {
      int idx = menuRenderer.getHoverIndexForSaveMenu(save, rw, rh, x, y);
      if (idx >= 0) {
        save.setSelected(idx);
        handleMenuEnter();
      }
    }
  }

  private boolean executeTextBoxButtonAction(VnScene vnScene, com.jvn.core.vn.ui.VnUiActionButtonSpec button) {
    if (vnScene == null || button == null || !button.enabled()) return false;
    String target = button.target() == null ? "" : button.target().trim();
    String action = normalizeButtonAction(button.action());

    String rawAction = button.action();
    if (rawAction != null) {
      String trimmed = rawAction.trim();
      int colon = trimmed.indexOf(':');
      if (colon > 0 && colon < trimmed.length() - 1) {
        action = normalizeButtonAction(trimmed.substring(0, colon));
        if (target.isBlank()) {
          String inlineTarget = trimmed.substring(colon + 1).trim();
          if (!inlineTarget.isBlank()) {
            target = inlineTarget;
          }
        }
      }
    }
    return executeUiAction(vnScene, action, target, null, true);
  }

  private boolean executeOverlayButtonAction(VnScene vnScene, com.jvn.core.vn.ui.VnOverlayButtonSpec button) {
    if (vnScene == null || button == null || !button.enabled()) return false;
    String target = button.target() == null ? "" : button.target().trim();
    String action = normalizeButtonAction(button.action());
    return executeUiAction(vnScene, action, target, button.screenId(), false);
  }

  private boolean executeUiAction(
      VnScene vnScene,
      String action,
      String target,
      String overlayScreenId,
      boolean clickAdvanceUsesReveal
  ) {
    if (vnScene == null) return false;
    var state = vnScene.getState();

    switch (action) {
      case "advance" -> {
        if (clickAdvanceUsesReveal) vnScene.advanceFromClick();
        else vnScene.advance();
        return true;
      }
      case "rollback", "back" -> {
        vnScene.rollback();
        return true;
      }
      case "quick_save", "save_quick" -> {
        handleQuickSave();
        return true;
      }
      case "quick_load", "load_quick" -> {
        handleQuickLoad();
        return true;
      }
      case "save_slots", "open_save_slots" -> {
        handleOpenSaveMenu();
        return true;
      }
      case "load_slots", "open_load_slots" -> {
        handleOpenLoadMenu();
        return true;
      }
      case "toggle_history", "history" -> {
        handleToggleHistory();
        return true;
      }
      case "toggle_skip", "skip" -> {
        vnScene.toggleSkipMode();
        return true;
      }
      case "toggle_auto", "auto" -> {
        vnScene.toggleAutoPlayMode();
        return true;
      }
      case "toggle_ui", "ui" -> {
        state.toggleUiHidden();
        return true;
      }
      case "hide", "close", "dismiss" -> {
        if (target == null || target.isBlank()) state.hideOverlayScreen(overlayScreenId);
        else state.hideOverlayScreen(target);
        return true;
      }
      case "return" -> {
        state.returnOverlayScreen(overlayScreenId, target);
        return true;
      }
      case "goto" -> {
        if (target == null || target.isBlank()) return true;
        state.hideOverlayScreen(overlayScreenId);
        vnScene.jumpToLabel(target);
        return true;
      }
      case "set", "flag", "unflag", "clear", "inc", "dec" -> {
        if (vnScene.getInterop() != null) {
          vnScene.getInterop().handle(new com.jvn.core.vn.VnExternalCommand("var", action + " " + target), vnScene);
        }
        return true;
      }
      case "persistent" -> {
        if (vnScene.getInterop() != null) {
          vnScene.getInterop().handle(new com.jvn.core.vn.VnExternalCommand("persistent", target), vnScene);
        }
        return true;
      }
      case "screen" -> {
        if (vnScene.getInterop() != null) {
          vnScene.getInterop().handle(new com.jvn.core.vn.VnExternalCommand("screen", target), vnScene);
        }
        return true;
      }
      case "save_menu", "open_save_menu", "menu_save" -> {
        engine.scenes().push(new SaveMenuScene(engine, new com.jvn.core.vn.save.VnSaveManager(), vnScene));
        return true;
      }
      case "load_menu", "open_load_menu", "menu_load" -> {
        String fallbackScript = resolveDefaultScriptForMenus(vnScene);
        String script = target == null || target.isBlank() ? fallbackScript : target;
        engine.scenes().push(new LoadMenuScene(
            engine,
            new com.jvn.core.vn.save.VnSaveManager(),
            script,
            vnScene.getState().getSettings(),
            vnScene.getAudioFacade()
        ));
        return true;
      }
      case "settings_menu", "open_settings_menu", "menu_settings" -> {
        String fallbackScript = resolveDefaultScriptForMenus(vnScene);
        String targetMenu = normalizeMenuId(target, "settings");
        com.jvn.core.input.ActionBindingProfile profile =
            com.jvn.core.input.ActionBindingProfile.deserialize(
                vnScene.getState().getSettings().getInputProfileSerialized());
        engine.scenes().push(new SettingsScene(
            engine,
            new com.jvn.core.vn.save.VnSaveManager(),
            fallbackScript,
            vnScene.getState().getSettings(),
            vnScene.getAudioFacade(),
            profile,
            targetMenu
        ));
        return true;
      }
      case "main_menu", "open_main_menu", "menu_main" -> {
        String fallbackScript = resolveDefaultScriptForMenus(vnScene);
        engine.scenes().push(new MainMenuScene(
            engine,
            vnScene.getState().getSettings(),
            new com.jvn.core.vn.save.VnSaveManager(),
            fallbackScript,
            vnScene.getAudioFacade()
        ));
        return true;
      }
      case "open_menu", "menu_open" -> {
        if (target == null || target.isBlank()) {
          vnScene.getState().showHudMessage("Button target missing", 1200);
          return true;
        }
        String fallbackScript = resolveDefaultScriptForMenus(vnScene);
        engine.scenes().push(new MainMenuScene(
            engine,
            vnScene.getState().getSettings(),
            new com.jvn.core.vn.save.VnSaveManager(),
            fallbackScript,
            vnScene.getAudioFacade(),
            target
        ));
        return true;
      }
      case "quit", "quit_game", "close_game", "exit" -> {
        String fallbackScript = resolveDefaultScriptForMenus(vnScene);
        String menuTarget = target == null || target.isBlank() ? "confirm_exit" : target;
        engine.scenes().push(new MainMenuScene(
            engine,
            vnScene.getState().getSettings(),
            new com.jvn.core.vn.save.VnSaveManager(),
            fallbackScript,
            vnScene.getAudioFacade(),
            menuTarget
        ));
        return true;
      }
      case "noop", "none" -> {
        return true;
      }
      default -> {
        vnScene.getState().showHudMessage("Unknown button action: " + action, 1200);
        return true;
      }
    }
  }

  private static String normalizeButtonAction(String raw) {
    return VnUiActionButtonActions.normalize(raw);
  }

  private void handleMouseDrag(double x, double y) {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    double rw = renderWidthForScene(currentScene);
    double rh = renderHeightForScene(currentScene);
    if (currentScene instanceof SettingsScene settings) {
      int idx = menuRenderer.getHoverIndexForSettings(settings, rw, rh, x, y);
      if (idx >= 0 && settings.hasSliderAt(idx)) {
        settings.setSelected(idx);
        double val = menuRenderer.computeSettingsSliderValue01(settings, idx, rw, rh, x);
        settings.setValueByIndex(idx, val);
      }
    } else if (currentScene instanceof LoadMenuScene load) {
      var controlHit = menuRenderer.getLoadControlHit(load, rw, rh, x, y);
      if (controlHit != null && controlHit.type() == com.jvn.fx.menu.MenuRenderer.LoadControlType.SET_PAGE) {
        load.setPageFromProgress01(controlHit.pageProgress01());
      }
    }
  }

  private boolean handleMenuEnter() {
    if (engine == null) return false;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof PauseMenuScene pause) {
      pause.activateSelected();
      return true;
    } else if (currentScene instanceof MainMenuScene main) {
      main.activateSelected();
      return true;
    } else if (currentScene instanceof HistoryMenuScene history) {
      history.close();
      return true;
    } else if (currentScene instanceof LoadMenuScene load) {
      load.activateSelected();
      return true;
    } else if (currentScene instanceof SettingsScene settings) {
      settings.toggleCurrent();
      if (settings.consumeCloseRequested()) engine.scenes().pop();
      return true;
    } else if (currentScene instanceof SaveMenuScene save) {
      if (save.activateSelectedWithoutPrompt()) {
        return true;
      }
      VnScene vnScene = save.getCurrentVnScene();
      String slotName;
      if (save.isNewItemSelected()) {
        slotName = save.saveNew(save.generateSaveName());
      } else {
        slotName = save.saveOverwriteSelected();
      }
      if (slotName != null) {
        writeSaveThumbnail(vnScene, slotName);
      }
      return true;
    }
    return false;
  }

  private boolean handleMenuBack() {
    if (engine == null) return false;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof PauseMenuScene || currentScene instanceof LoadMenuScene
        || currentScene instanceof SettingsScene || currentScene instanceof SaveMenuScene
        || currentScene instanceof HistoryMenuScene) {
      engine.scenes().pop();
      return true;
    }
    if (currentScene instanceof MainMenuScene main) {
      String activeMenuId = main.getMenuId();
      String rootMenuId = main.getMenuProfile() != null ? main.getMenuProfile().defaultScreenId() : "main";
      if (activeMenuId != null && rootMenuId != null && !activeMenuId.equalsIgnoreCase(rootMenuId)) {
        engine.scenes().pop();
        return true;
      }
    }
    return false;
  }

  private void handleOpenPauseMenu() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      String fallbackScript = resolveDefaultScriptForMenus(vn);
      String configuredPauseMenu = resolveConfiguredPauseMenuId();
      if (configuredPauseMenu != null) {
        com.jvn.core.input.ActionBindingProfile profile =
            com.jvn.core.input.ActionBindingProfile.deserialize(
                vn.getState().getSettings().getInputProfileSerialized());
        engine.scenes().push(new SettingsScene(
            engine,
            new com.jvn.core.vn.save.VnSaveManager(),
            fallbackScript,
            vn.getState().getSettings(),
            vn.getAudioFacade(),
            profile,
            configuredPauseMenu
        ));
        return;
      }
      engine.scenes().push(new PauseMenuScene(
          engine, vn,
          new com.jvn.core.vn.save.VnSaveManager(),
          fallbackScript,
          vn.getAudioFacade()
      ));
    }
  }

  private void handleMenuMove(int delta) {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof PauseMenuScene pause) {
      pause.moveSelection(delta);
    } else if (currentScene instanceof MainMenuScene main) {
      main.moveSelection(delta);
    } else if (currentScene instanceof LoadMenuScene load) {
      load.moveSelection(delta);
    } else if (currentScene instanceof SettingsScene settings) {
      settings.moveSelection(delta);
    } else if (currentScene instanceof SaveMenuScene save) {
      save.moveSelection(delta);
    }
  }

  private void handleSettingsAdjust(int delta) {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof SettingsScene settings) {
      settings.adjustCurrent(delta);
    } else if (currentScene instanceof LoadMenuScene load) {
      load.movePage(delta);
    } else if (currentScene instanceof MainMenuScene main && menuRenderer.getTheme().isDockPresentation()) {
      main.moveSelection(delta);
    }
  }


  private void writeQuickSaveThumbnail(VnScene vnScene) {
    try {
      if (vnScene == null) return;
      var qsm = vnScene.getQuickSaveManager();
      if (qsm == null) return;
      String dir = qsm.getSaveDirectory();
      String name = qsm.getQuickSaveSlotName();
      if (dir == null || name == null || name.isBlank()) return;
      Path d = Paths.get(dir);
      Files.createDirectories(d);
      File out = d.resolve(name + ".png").toFile();
      captureVnThumbnail(vnScene, out);
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
  }

  private static String resolveDefaultScriptForMenus(VnScene vnScene) {
    if (vnScene != null && vnScene.getState() != null) {
      String sourceScript = VnEntryScriptResolver.normalizeScriptKey(vnScene.getState().getSourceScriptName());
      if (sourceScript != null) return sourceScript;
    }
    String resolved = VnEntryScriptResolver.resolveEntryScript(null, null);
    if (resolved != null) return resolved;
    return DEFAULT_ENTRY_SCRIPT;
  }

  private String resolveConfiguredPauseMenuId() {
    String fromProperty = normalizeMenuId(System.getProperty("jvn.pause.menu"), null);
    if (fromProperty == null) fromProperty = normalizeMenuId(System.getProperty("jvn.pauseMenu"), null);
    if (fromProperty != null) return fromProperty;
    if (runtimeProjectRoot == null) return null;
    File manifest = new File(runtimeProjectRoot, "jvn.project");
    if (!manifest.isFile()) return null;
    try (var in = Files.newInputStream(manifest.toPath())) {
      var props = new java.util.Properties();
      props.load(in);
      String value = firstConfiguredProperty(
          props,
          "runtime.pauseMenu",
          "runtime.pause.menu",
          "menu.pause",
          "pauseMenu",
          "pause.menu"
      );
      return normalizeMenuId(value, null);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String firstConfiguredProperty(java.util.Properties props, String... keys) {
    if (props == null || keys == null) return null;
    for (String key : keys) {
      if (key == null || key.isBlank()) continue;
      String value = props.getProperty(key);
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }

  private static String normalizeMenuId(String raw, String fallback) {
    if (raw == null) return fallback;
    String value = raw.trim();
    return value.isEmpty() ? fallback : value;
  }

  private void captureVnThumbnail(VnScene vnScene, File out) throws Exception {
    if (vnScene == null || out == null || canvas == null || vnRenderer == null) return;
    double w = canvas.getWidth();
    double h = canvas.getHeight();
    if (w <= 1 || h <= 1) return;

    // Render the VN scene directly so the thumbnail includes dialogue, characters, and overlays.
    vnRenderer.setAudioFacade(vnScene.getAudioFacade());
    vnRenderer.render(vnScene.getState(), vnScene.getScenario(), w, h, mouseX, mouseY);
    var img = canvas.snapshot(null, null);
    ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
  }

  private void handleMenuDelete() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof LoadMenuScene load) {
      String sel = load.getSelectedName();
      if (sel == null) return;
      Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete save '" + sel + "'?", ButtonType.YES, ButtonType.NO);
      alert.setHeaderText(null);
      alert.setTitle("Confirm Delete");
      var result = alert.showAndWait();
      if (result.isPresent() && result.get() == ButtonType.YES) {
        load.deleteSelected();
      }
    } else if (currentScene instanceof SaveMenuScene save) {
      String sel = save.getSelectedName();
      if (sel == null) return;
      Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete save '" + sel + "'?", ButtonType.YES, ButtonType.NO);
      alert.setHeaderText(null);
      alert.setTitle("Confirm Delete");
      var result = alert.showAndWait();
      if (result.isPresent() && result.get() == ButtonType.YES) {
        save.deleteSelected();
      }
    }
  }

  private void handleMenuRename() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof LoadMenuScene load) {
      String sel = load.getSelectedName();
      if (sel == null) return;
      TextInputDialog dlg = new TextInputDialog(sel);
      dlg.setTitle("Rename Save");
      dlg.setHeaderText(null);
      dlg.setContentText("New name:");
      var result = dlg.showAndWait();
      if (result.isPresent()) {
        String newName = result.get().trim();
        if (!newName.isEmpty()) {
          load.renameSelected(newName);
        }
      }
    } else if (currentScene instanceof SaveMenuScene save) {
      String sel = save.getSelectedName();
      if (sel == null) return;
      TextInputDialog dlg = new TextInputDialog(sel);
      dlg.setTitle("Rename Save");
      dlg.setHeaderText(null);
      dlg.setContentText("New name:");
      var result = dlg.showAndWait();
      if (result.isPresent()) {
        String newName = result.get().trim();
        if (!newName.isEmpty()) {
          save.renameSelected(newName);
        }
      }
    }
  }

  private void pollProjectHotReload() {
    if (hotReloadTracker == null || engine == null) return;
    ProjectHotReloadTracker.ChangeSet changes = hotReloadTracker.poll(System.nanoTime());
    if (changes == null || !changes.hasChanges()) return;
    handleProjectHotReload(changes);
  }

  private void handleProjectHotReload(ProjectHotReloadTracker.ChangeSet changes) {
    if (changes.localizationChanged()) {
      reloadLocalizationFromProject();
    }
    if (changes.assetsChanged()) {
      ProjectFontResolver.clearCache();
      menuRenderer.clearImageCache();
      menuRenderer.clearTextMeasureCache();
      phoneRenderer.clearAssetCache();
    }
    if (changes.uiChanged() || changes.assetsChanged()) {
      vnRenderer.clearCache();
      vnRenderer.reloadUiLayout();
      if (fxScene != null) {
        applyConfiguredCursor(fxScene);
      }
    }
    if (changes.menuChanged() || changes.localizationChanged()) {
      menuRenderer.setTheme(MenuTheme.fromAssets());
      menuRenderer.clearImageCache();
      reloadTopMenuScene();
    }
    if (changes.phoneChanged() || (changes.assetsChanged() && engine.scenes().peek() instanceof PhoneScene)) {
      reloadTopPhoneScene();
    }
    if (changes.scriptsChanged() || changes.localizationChanged()) {
      reloadTopVnScene();
    }
    logProjectHealth("hot-reload");
  }

  private void handleManualHotReload() {
    if (engine == null) return;
    handleProjectHotReload(new ProjectHotReloadTracker.ChangeSet(true, true, true, true, true, true));
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn && vn.getState() != null) {
      vn.getState().showHudMessage("Hot reload requested", 1000);
    }
  }

  private void reloadLocalizationFromProject() {
    String locale = resolveRuntimeLocale();
    Localization.init(locale, Thread.currentThread().getContextClassLoader());
  }

  private String resolveRuntimeLocale() {
    if (runtimeProjectRoot != null) {
      File manifest = new File(runtimeProjectRoot, "jvn.project");
      if (manifest.isFile()) {
        try (var in = Files.newInputStream(manifest.toPath())) {
          var props = new java.util.Properties();
          props.load(in);
          String configured = props.getProperty("runtime.locale");
          if (configured != null && !configured.isBlank()) {
            return configured.trim();
          }
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        }
      }
    }
    String active = Localization.locale();
    return (active == null || active.isBlank()) ? "en" : active;
  }

  private void logProjectHealth(String reason) {
    if (runtimeProjectRoot == null) return;
    ProjectHealthChecker.Report report = ProjectHealthChecker.inspect(runtimeProjectRoot);
    if (!report.hasIssues()) return;
    log.warn("Project health ({}) -> errors={}, warnings={}", reason, report.errorCount(), report.warningCount());
    for (ProjectHealthChecker.Diagnostic diagnostic : report.diagnostics()) {
      if (diagnostic.location() == null || diagnostic.location().isBlank()) {
        log.warn("Project health [{}]: {}", diagnostic.category(), diagnostic.message());
      } else {
        log.warn("Project health [{}] {}: {}", diagnostic.category(), diagnostic.location(), diagnostic.message());
      }
    }
  }

  private void reloadTopMenuScene() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof MainMenuScene main) {
      MainMenuScene replacement = new MainMenuScene(
          engine,
          main.getSettingsModel(),
          main.getSaveManager(),
          main.getDefaultScriptName(),
          main.getAudioFacade(),
          main.getMenuId()
      );
      replacement.setSelected(main.getSelected());
      if (main.getTitleBgmPath() != null) {
        replacement.setTitleBgm(main.getTitleBgmPath(), main.getTitleBgmVolume());
      }
      engine.scenes().replace(replacement);
    } else if (currentScene instanceof PauseMenuScene pause) {
      PauseMenuScene replacement = new PauseMenuScene(
          engine,
          pause.getVnScene(),
          pause.getSaveManager(),
          pause.getDefaultScriptName(),
          pause.getAudioFacade()
      );
      replacement.setSelected(pause.getSelected());
      engine.scenes().replace(replacement);
    } else if (currentScene instanceof HistoryMenuScene history) {
      engine.scenes().replace(new HistoryMenuScene(engine, history.getVnScene()));
    } else if (currentScene instanceof SaveMenuScene save) {
      String selectedName = save.getSelectedName();
      SaveMenuScene replacement = new SaveMenuScene(
          engine,
          save.getSaveManager(),
          save.getCurrentVnScene(),
          save.getDefaultScriptName()
      );
      restoreSaveSelection(replacement, selectedName);
      engine.scenes().replace(replacement);
    } else if (currentScene instanceof LoadMenuScene load) {
      String selectedName = load.getSelectedName();
      LoadMenuScene replacement = new LoadMenuScene(
          engine,
          load.getSaveManager(),
          load.getDefaultScriptName(),
          load.getSettingsModel(),
          load.getAudioFacade()
      );
      if (load.isFavoritesOnly()) {
        replacement.toggleFavoritesOnly();
      }
      restoreLoadSelection(replacement, selectedName);
      engine.scenes().replace(replacement);
    } else if (currentScene instanceof SettingsScene settings) {
      SettingsScene replacement = new SettingsScene(
          engine,
          settings.getSaveManager(),
          settings.getDefaultScriptName(),
          settings.model(),
          settings.getAudioFacade(),
          settings.getBindings(),
          settings.getMenuId()
      );
      replacement.preferSelectionKey(settings.getSelectedKey());
      engine.scenes().replace(replacement);
    }
  }

  private void reloadTopPhoneScene() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (!(currentScene instanceof PhoneScene phone)) return;

    VnScene vnScene = phone.getVnScene();
    if (vnScene != null && vnScene.getState() != null
        && vnScene.getState().getVariable(VnPhoneStateStore.VAR_PHONE_PROPERTIES) != null) {
      return;
    }

    VnPhoneData data = runtimeProjectRoot != null
        ? VnPhonePropertiesCodec.loadFromProjectRootWithDiagnostics(runtimeProjectRoot).data()
        : VnPhonePropertiesCodec.loadSeedFromAssets();
    PhoneScene replacement = new PhoneScene(
        vnScene,
        data,
        updated -> {
          if (vnScene != null && vnScene.getState() != null) {
            VnPhoneStateStore.save(vnScene.getState(), updated);
          }
        }
    );
    replacement.setSelectedHomeIndex(phone.getSelectedHomeIndex());
    if (phone.isShowingCall() && phone.getCurrentCallId() != null) {
      replacement.openCall(phone.getCurrentCallId());
    } else if (phone.isShowingChat() && phone.getCurrentChatId() != null) {
      replacement.openChat(phone.getCurrentChatId());
    } else {
      replacement.showHome();
    }
    engine.scenes().replace(replacement);
  }

  private boolean reloadTopVnScene() {
    if (engine == null) return false;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (!(currentScene instanceof VnScene vnScene)) return false;

    String sourceScript = VnEntryScriptResolver.normalizeScriptKey(vnScene.getState().getSourceScriptName());
    if (sourceScript == null) {
      sourceScript = VnEntryScriptResolver.resolveEntryScript(null, runtimeProjectRoot);
    }
    if (sourceScript == null) return false;

    try {
      VnScenario reloadedScenario = new VnScenarioLoader().load(sourceScript);
      VnScene replacement = new VnScene(reloadedScenario);
      replacement.getState().setSourceScriptName(sourceScript);
      replacement.setAudioFacade(vnScene.getAudioFacade());
      replacement.setQuickSaveManager(vnScene.getQuickSaveManager());
      if (engine.getVnInteropFactory() != null) {
        replacement.setInterop(engine.getVnInteropFactory().create(engine));
      }
      copySettings(vnScene.getState().getSettings(), replacement.getState().getSettings());
      replacement.getState().getVariables().putAll(vnScene.getState().getVariables());
      replacement.getState().setUiHidden(vnScene.getState().isUiHidden());
      replacement.getState().setSkipMode(vnScene.getState().isSkipMode());
      replacement.getState().setAutoPlayMode(vnScene.getState().isAutoPlayMode());

      String anchorLabel = vnScene.getScenario() != null
          ? vnScene.getScenario().findLabelAtOrBefore(vnScene.getState().getCurrentNodeIndex())
          : null;
      if (anchorLabel != null && reloadedScenario.getLabelIndex(anchorLabel) != null) {
        replacement.getState().jumpToLabel(anchorLabel);
        replacement.preflightState(replacement.getState().getCurrentNodeIndex());
        replacement.getState().showHudMessage("Script reloaded at @" + anchorLabel, 1400);
      } else {
        replacement.getState().showHudMessage("Script reloaded", 1200);
      }
      engine.scenes().replace(replacement);
      return true;
    } catch (Exception ex) {
      vnScene.getState().showHudMessage("Script reload failed", 1400);
      vnScene.setActiveError(com.jvn.core.vn.VnErrorOverlay.fromScriptLoadFailure(sourceScript, ex));
      log.warn("Hot reload failed for script '{}': {}", sourceScript, ex.toString());
      return false;
    }
  }

  private void restoreSaveSelection(SaveMenuScene scene, String selectedName) {
    if (scene == null || selectedName == null || scene.getSaves() == null) return;
    int index = scene.getSaves().indexOf(selectedName);
    if (index >= 0) {
      scene.setSelected(index);
    }
  }

  private void restoreLoadSelection(LoadMenuScene scene, String selectedName) {
    if (scene == null || selectedName == null || scene.getSaves() == null) return;
    int index = scene.getSaves().indexOf(selectedName);
    if (index >= 0) {
      scene.setSelected(index);
    }
  }

  private void copySettings(com.jvn.core.vn.VnSettings src, com.jvn.core.vn.VnSettings dst) {
    if (src == null || dst == null) return;
    dst.copyFrom(src);
  }

  @Override
  public void stop() {
    if (timer != null) timer.stop();
    if (hotReloadTracker != null) {
      hotReloadTracker.close();
      hotReloadTracker = null;
    }
    if (engine != null) engine.stop();
    if (vnRenderer != null) vnRenderer.dispose();
    if (menuRenderer != null) menuRenderer.dispose();
    if (phoneRenderer != null) phoneRenderer.dispose();
  }

  private static void applyLinuxDefaultWindowState(Stage stage) {
    if (stage == null || !isLinux()) return;
    stage.setIconified(false);
    stage.setMaximized(true);
    Platform.runLater(() -> {
      stage.setIconified(false);
      stage.setMaximized(true);
    });
  }

  private static boolean isLinux() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
  }

  private static double bytesToMb(long bytes) {
    if (bytes <= 0L) return 0.0;
    return bytes / (1024.0 * 1024.0);
  }

  static String jvmMemoryText(double usedMb) {
    return String.format(Locale.ROOT, "JVM %.0f MB", Math.max(0.0, usedMb));
  }

  static String fpsText(double fps) {
    return isRatioValid(fps)
        ? String.format(Locale.ROOT, "FPS %.0f", fps)
        : "FPS --";
  }

  private static boolean isRatioValid(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0.0;
  }

  private static double smoothRatio(double current, double target, double alpha) {
    if (!isRatioValid(target)) return current;
    if (!isRatioValid(current)) return target;
    double clampedAlpha = Math.max(0.0, Math.min(1.0, alpha));
    return current + (target - current) * clampedAlpha;
  }

  private void handleRuntimeErrorButton(VnScene vnScene, int buttonIndex) {
    switch (buttonIndex) {
      case 0 -> vnScene.clearActiveError(); // Continue past the surfaced error.
      case 1 -> reloadTopVnScene(); // Reload the latest script content from disk.
      case 2 -> { // Copy the complete error details to the clipboard.
        com.jvn.core.vn.VnErrorOverlay error = vnScene.getActiveError();
        if (error != null) {
          javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
          javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
          content.putString(error.toDisplaySummary());
          clipboard.setContent(content);
        }
      }
    }
  }
}
