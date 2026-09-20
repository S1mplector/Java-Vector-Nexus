package com.jvn.fx.vn;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.accessibility.AccessibilityThemeLoader;
import com.jvn.core.accessibility.NoopTextToSpeechService;
import com.jvn.core.accessibility.TextToSpeechService;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.assets.BoundedImageCache;
import com.jvn.core.animation.TimelineDrivenEntity;
import com.jvn.core.audio.AudioFacade;
import com.jvn.core.config.VnConfig;
import com.jvn.core.localization.Localization;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.ParticleEmitter2D;
import com.jvn.core.scene2d.RenderDiagnostics;
import com.jvn.core.scene2d.Sprite2D;
import com.jvn.fx.scene2d.MissingAssetPlaceholder;
import com.jvn.core.ui.BoundsPointCodec;
import com.jvn.core.vn.BubbleAnchor;
import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.Choice;
import com.jvn.core.vn.DialogueLine;
import com.jvn.core.vn.DialoguePresentationMode;
import com.jvn.core.vn.EyeFocusResolver;
import com.jvn.core.vn.LayeredCharacterResolver;
import com.jvn.core.vn.VnAudioVisualizerConfig;
import com.jvn.core.vn.VnBackground;
import com.jvn.core.vn.VnCharacter;
import com.jvn.core.vn.VnCharacterSceneAccessor;
import com.jvn.core.vn.VnEyeFocusProfile;
import com.jvn.core.vn.VnEyeFocusProfileStore;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnParticleCommand;
import com.jvn.core.vn.VnParticlePresetLibrary;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.VnVariableInterpolator;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.text.TextEffect;
import com.jvn.core.vn.text.TextParser;
import com.jvn.core.vn.text.TextSpan;
import com.jvn.core.vn.ui.VnOverlayButtonSpec;
import com.jvn.core.vn.ui.VnOverlayScreenSpec;
import com.jvn.core.vn.ui.VnFacetSpec;
import com.jvn.core.vn.ui.VnReactiveOverlayScreenSpec;
import com.jvn.core.vn.ui.VnUiActionButtonSpec;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;
import com.jvn.fx.RenderThreadGuard;
import com.jvn.fx.FxImageMemory;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.fx.ui.FxTextMetrics;
import com.jvn.fx.ui.ProjectFontResolver;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Renders visual novel elements using JavaFX Canvas
 */
public class VnRenderer {
  private static final Logger log = LoggerFactory.getLogger(VnRenderer.class);
  private static final long MIB = 1024L * 1024L;
  static final long SOURCE_IMAGE_CACHE_BUDGET_BYTES = 96L * MIB;
  static final long COMPOSITE_SPRITE_CACHE_BUDGET_BYTES = 96L * MIB;
  static final long BACKGROUND_IMAGE_CACHE_BUDGET_BYTES = 48L * MIB;
  static final long STAGE_BACKGROUND_CACHE_BUDGET_BYTES = 48L * MIB;
  static final long STAGE_CHARACTER_CACHE_BUDGET_BYTES = 64L * MIB;

  private final GraphicsContext gc;
  private final BoundedImageCache<Image> imageCache = new BoundedImageCache<>(
      VnConfig.defaults().getImageCacheMaxEntries(), SOURCE_IMAGE_CACHE_BUDGET_BYTES, FxImageMemory::estimatedBytes);
  // Layer sources are transient working data while a composite is assembled. Keeping completed
  // sprites in the same cache lets full-canvas layers evict the composite that they just built,
  // forcing expensive Canvas snapshots again on every frame in projects with several characters.
  private final BoundedImageCache<Image> compositeSpriteCache = new BoundedImageCache<>(
      64, COMPOSITE_SPRITE_CACHE_BUDGET_BYTES, FxImageMemory::estimatedBytes);
  // Backgrounds must remain resident while expression changes churn through full-canvas sprite
  // layers. Reloading them asynchronously leaves the cleared canvas black for a frame.
  private final BoundedImageCache<Image> backgroundImageCache = new BoundedImageCache<>(
      16, BACKGROUND_IMAGE_CACHE_BUDGET_BYTES, FxImageMemory::estimatedBytes);
  private final BoundedImageCache<Image> stageBackgroundCache = new BoundedImageCache<>(
      16, STAGE_BACKGROUND_CACHE_BUDGET_BYTES, FxImageMemory::estimatedBytes);
  private final BoundedImageCache<Image> stageCharacterCache = new BoundedImageCache<>(
      64, STAGE_CHARACTER_CACHE_BUDGET_BYTES, FxImageMemory::estimatedBytes);
  // Independently transformed layers are a live render working set, not ordinary reusable source
  // data. Keep exactly the layers used by consecutive frames resident so two large layered
  // characters cannot evict and synchronously reload one another through imageCache every frame.
  private final FrameRetainedCache<Image> timelineLayerWorkingSet = new FrameRetainedCache<>();
  // Character definitions and target spellings are immutable. Do not rebuild their alias/group
  // topology every animation frame; proxies and their transforms are still resolved live.
  private record LayerMetadataKey(VnCharacter character, String expression, String characterId) {}
  private static final class LayerMetadata {
    final Map<String, List<String>> targetNames = new HashMap<>();
    final Map<List<String>, List<SpriteLayer>> expressions = new HashMap<>();
  }
  private final Map<LayerMetadataKey, LayerMetadata> layerMetadataCache =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<LayerMetadataKey, LayerMetadata> eldest) {
          return size() > 16;
        }
      };

  private LayerMetadata layerMetadata(VnCharacter character, String expression, String characterId) {
    return layerMetadataCache.computeIfAbsent(new LayerMetadataKey(character, expression, characterId),
        ignored -> new LayerMetadata());
  }

  private final FxTextMetrics textMetrics = new FxTextMetrics();
  private Font nameFont;
  private Font dialogueFont;
  private Font choiceFont;

  // Default font settings
  private static final String DEFAULT_FONT_FAMILY = "SansSerif";
  private static final int DEFAULT_NAME_FONT_SIZE = 18;
  private static final int DEFAULT_DIALOGUE_FONT_SIZE = 22;
  private static final int DEFAULT_CHOICE_FONT_SIZE = 20;
  private VnState currentState;
  private long animationTime = 0;
  private AudioFacade audioFacade;
  private VnUiLayoutSpec uiLayout;
  private VnUiStyleSpec uiStyle = VnUiStyleSpec.defaults();
  private AccessibilityThemeLoader accessibilityTheme = AccessibilityThemeLoader.load("none");
  private String appliedAccessibilityThemeName = "none";
  private List<VnUiActionButtonSpec> textBoxButtons = List.of();
  private VnCharacterSceneAccessor timelineAccessor;
  private Map<String, VnEyeFocusProfile> eyeFocusProfiles;
  private double styleCharacterHeightFactor = DEFAULT_CHARACTER_HEIGHT_FACTOR;
  private double styleCharacterBaselineY = DEFAULT_CHARACTER_BASELINE_Y;
  private double characterHeightFactor = DEFAULT_CHARACTER_HEIGHT_FACTOR;
  private double characterBaselineY = DEFAULT_CHARACTER_BASELINE_Y;

  private final TextToSpeechService tts = ServiceLoader.load(TextToSpeechService.class)
      .findFirst().orElseGet(NoopTextToSpeechService::new);
  private String lastTtsNodeId = null;
  private boolean textToSpeechEnabled;
  private double appliedUiFontScale = 1.0;

  public void setTimelineAccessor(VnCharacterSceneAccessor accessor) { this.timelineAccessor = accessor; }
  public void setAudioFacade(AudioFacade facade) { this.audioFacade = facade; }

  // UI Colors
  private static final Color TEXTBOX_COLOR = Color.rgb(12, 18, 32, 0.88);
  private static final Color NAME_BOX_COLOR = Color.rgb(20, 32, 56, 0.56);
  private static final Color TEXT_COLOR = Color.web("#E8EDF6");
  private static final Color CHOICE_BG_COLOR = Color.web("#1A2640D8");
  private static final Color CHOICE_HOVER_COLOR = Color.web("#243358E8");
  private static final Color CHOICE_DISABLED_COLOR = Color.web("#121826A0");
  private static final Color TEXT_COLOR_DISABLED = Color.web("#6878A0");
  private static final Color CHOICE_DISABLED_BORDER_COLOR = Color.web("#28345060");
  private static final double DEFAULT_CHOICE_RADIUS = 8.0;
  private static final double DEFAULT_CHOICE_BORDER_WIDTH = 1.5;
  private static final double DEFAULT_CHOICE_TEXT_BASELINE_OFFSET = 4.0;
  private static final double DEFAULT_CHARACTER_HEIGHT_FACTOR = 0.85;
  private static final double DEFAULT_CHARACTER_BASELINE_Y = 1.0;
  private static final int VISUALIZER_BAR_COUNT = VnAudioVisualizerConfig.MAX_BARS;
  private static final int MAX_CACHED_LAYER_PATH_SPECS = 256;
  // Position is bucketed to this grid (in scene pixels) when building the stage-lighting
  // cache key, so idle-bob/breathing jitter of a few pixels reuses the last lit bitmap
  // instead of forcing a full per-pixel relight every frame. Lighting falloff is smooth
  // over distances far larger than this, so the visual difference is imperceptible; any
  // movement crossing a bucket boundary still relights correctly.
  private static final double LIGHTING_CACHE_POSITION_GRID_PX = 4.0;
  private static final String VAR_CHARACTER_HEIGHT_FACTOR = "ui.characterHeightFactor";
  private static final String VAR_CHARACTER_BASELINE_Y = "ui.characterBaselineY";
  private static final String VAR_DIALOGUE_FADE_MS = "ui.dialogueFadeMs";
  private static final String VAR_DIALOGUE_UI = "ui.dialogueUi";
  private static final String VAR_DIALOGUE_STYLE = "ui.dialogueStyle";
  private static final String VAR_TEXT_BOX_ASSET = "ui.textBoxAsset";
  private static final String VAR_TEXT_BOX_ASSET_ENABLED = "ui.textBoxAssetEnabled";
  private static final String VAR_TEXT_BOX_BUTTONS = "ui.textBoxButtons";
  private static final String VAR_TEXT_BOX_BUTTONS_ENABLED = "ui.textBoxButtonsEnabled";

  private Image choiceButtonImage;
  private Image choiceButtonHoverImage;
  private Image choiceButtonDisabledImage;
  private Image textBoxImage;
  private Image narrationTextBoxImage;
  private Image nameBoxImage;
  private Image nvlPanelImage;
  private Image bubbleImage;
  private Image continueIndicatorImage;
  private Color textBoxFillColor = TEXTBOX_COLOR;
  private Color nameBoxFillColor = NAME_BOX_COLOR;
  private Color nameTextFillColor = Color.web("#FFD78A");
  private Color dialogueTextFillColor = TEXT_COLOR;
  private Color nvlPanelFillColor = Color.web("#08111acc");
  private Color nvlSpeakerTextFillColor = Color.web("#F7D89A");
  private Color nvlTextFillColor = TEXT_COLOR;
  private Color bubbleFillColor = Color.web("#152238ee");
  private Color bubbleBorderFillColor = Color.web("#A9BCD9");
  private Color bubbleSpeakerTextFillColor = Color.web("#FFD78A");
  private Color bubbleTextFillColor = Color.web("#F1F5FF");
  private double textBoxAssetOverlayOpacity = 0.28;
  private double nameBoxRenderOpacity = 1.0;
  private double nvlPanelOpacity = 0.84;
  private double bubbleOpacity = 0.96;
  private Color choiceBgColor = CHOICE_BG_COLOR;
  private Color choiceHoverColor = CHOICE_HOVER_COLOR;
  private Color choiceDisabledColor = CHOICE_DISABLED_COLOR;
  private Color choiceTextColor = TEXT_COLOR;
  private Color choiceHoverTextColor = TEXT_COLOR;
  private Color choiceDisabledTextColor = TEXT_COLOR_DISABLED;
  private Color choiceBorderColor = TEXT_COLOR;
  private Color choiceHoverBorderColor = TEXT_COLOR;
  private Color choiceDisabledBorderColor = CHOICE_DISABLED_BORDER_COLOR;
  private boolean freezeTransientEffects;
  private VnState dialogueFadeState;
  private DialogueLine dialogueFadeLine;
  private boolean dialogueFadeVisible;
  private double dialogueFadeAlpha;
  private double dialogueFadeFrom;
  private double dialogueFadeTarget;
  private long dialogueFadeStartedAtNanos;
  private double choiceCornerRadius = DEFAULT_CHOICE_RADIUS;
  private double choiceBorderWidth = DEFAULT_CHOICE_BORDER_WIDTH;
  private double choiceTextBaselineOffset = DEFAULT_CHOICE_TEXT_BASELINE_OFFSET;
  private double bubbleCornerRadius = 20.0;
  private double bubbleBorderWidth = 2.0;
  private double nameTextXAlign = 0.0;
  private double dialogueTextXAlign = 0.0;
  private double choiceTextXAlign = 0.0;
  private List<BoundsPointCodec.Point> textBoxBoundsPolygon = List.of();
  private List<BoundsPointCodec.Point> nameBoxBoundsPolygon = List.of();
  private List<BoundsPointCodec.Point> dialogueTextBoundsPolygon = List.of();
  private List<BoundsPointCodec.Point> choiceButtonBoundsPolygon = List.of();
  private final double[] visualizerLevels = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerTargets = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerLevelVelocities = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerPeakLevels = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerPeakVelocities = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerWidthMultipliers = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerWidthVelocities = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerGlowLevels = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerBassHistory = new double[12];
  private int visualizerBassHistoryIndex = 0;
  private long visualizerLastBeatAtNanos = 0L;
  private double visualizerBeatFlashIntensity = 0.0;
  private double visualizerHue = 182.0;
  private final ParticleEmitter2D particleEmitter = new ParticleEmitter2D();
  private final FxBlitter2D particleBlitter;
  private VnParticleCommand renderedParticleCommand;
  private long particleLastFrameNanos = 0L;
  private int lastParticleLayer = 100;
  private double particleConfigWidth = -1.0;
  private double particleConfigHeight = -1.0;
  
  // Reusable per-frame lists to reduce GC pressure (cleared + re-populated each frame)
  private final List<CharacterRenderEntry> reusableCharacterEntries = new ArrayList<>();
  private final List<LayeredSceneDraw> reusableLayeredDraws = new ArrayList<>();
  private final com.jvn.core.diagnostics.DrawCallStats drawCallStats = new com.jvn.core.diagnostics.DrawCallStats();
  // Expression path specifications are immutable for a rendered scenario. Caching their
  // layer lists avoids splitting and allocating once per character on every frame.
  private final Map<String, List<String>> layerPathCache = new HashMap<>();

  public VnRenderer(GraphicsContext gc) {
    this.gc = gc;
    this.particleBlitter = new FxBlitter2D(gc);
    resetParticleState();
    this.nameFont = Font.font(DEFAULT_FONT_FAMILY, FontWeight.BOLD, DEFAULT_NAME_FONT_SIZE);
    this.dialogueFont = Font.font(DEFAULT_FONT_FAMILY, FontWeight.NORMAL, DEFAULT_DIALOGUE_FONT_SIZE);
    this.choiceFont = Font.font(DEFAULT_FONT_FAMILY, FontWeight.NORMAL, DEFAULT_CHOICE_FONT_SIZE);
    Arrays.fill(visualizerWidthMultipliers, 1.0);
    reloadUiLayout();
  }

  // Optional base directory used to resolve asset paths from filesystem (editor preview)
  private File projectRoot;
  public void setProjectRoot(File root) {
    if (java.util.Objects.equals(this.projectRoot, root)) return;
    this.projectRoot = root;
    this.eyeFocusProfiles = null;
    imageCache.clear();
    compositeSpriteCache.clear();
    timelineLayerWorkingSet.clear();
    layerMetadataCache.clear();
    backgroundImageCache.clear();
    particleBlitter.clearCache();
    particleBlitter.setProjectRoot(root);
    stageBackgroundCache.clear();
    stageCharacterCache.clear();
    layerPathCache.clear();
    reloadUiLayout();
  }

  public VnUiLayoutSpec getUiLayout() {
    return uiLayout;
  }

  public void resetParticleState() {
    particleEmitter.clear();
    particleEmitter.setEmitting(false);
    particleEmitter.setEmissionRate(0);
    renderedParticleCommand = null;
    particleLastFrameNanos = 0L;
    lastParticleLayer = 100;
    particleConfigWidth = -1.0;
    particleConfigHeight = -1.0;
  }

  public void setUiLayout(VnUiLayoutSpec layout) {
    this.uiLayout = layout == null ? VnUiLayoutSpec.defaults() : layout;
    applyUiStyle(uiStyle);
  }

  public VnUiStyleSpec getUiStyle() {
    return uiStyle;
  }

  public void setUiStyle(VnUiStyleSpec style) {
    this.uiStyle = style == null ? VnUiStyleSpec.defaults() : style;
    applyUiStyle(this.uiStyle);
  }

  public void setAccessibilityTheme(String themeName) {
    this.appliedAccessibilityThemeName = normalizeAccessibilityThemeName(themeName);
    this.accessibilityTheme = AccessibilityThemeLoader.load(appliedAccessibilityThemeName);
    applyUiStyle(this.uiStyle);
  }

  public List<VnUiActionButtonSpec> getTextBoxButtons() {
    return textBoxButtons;
  }

  public void setTextBoxButtons(List<VnUiActionButtonSpec> buttons) {
    this.textBoxButtons = buttons == null ? List.of() : List.copyOf(buttons);
  }

  public void reloadUiLayout() {
    VnUiLayoutLoader.LoadResult result = projectRoot != null
        ? VnUiLayoutLoader.loadFromProjectRootWithDiagnostics(projectRoot)
        : VnUiLayoutLoader.loadFromAssetsWithDiagnostics();
    this.uiLayout = result.layout();
    this.uiStyle = result.style();
    this.textBoxButtons = result.textBoxButtons();
    applyUiStyle(this.uiStyle);
  }

  /**
   * Render the complete VN scene
   */
  public com.jvn.core.diagnostics.DrawCallStats getDrawCallStats() {
    return drawCallStats;
  }

  public void render(VnState state, VnScenario scenario, double width, double height) {
    RenderThreadGuard.requireFxThread("VnRenderer.render");
    drawCallStats.reset();
    this.currentState = state;
    syncAccessibilitySettings(state);
    applyRuntimeCharacterFramingOverrides(state);
    VnStagePreset activeStage = resolveActiveStagePreset(state, scenario);
    // Clear screen
    gc.setFill(Color.BLACK);
    gc.fillRect(0, 0, width, height);

    double shakeMagnitude = state.getScreenShakeMagnitude();
    boolean shaking = shakeMagnitude > 0.01;
    if (shaking) {
      double t = System.currentTimeMillis() * 0.02;
      double shakeX = Math.sin(t * 2.3) * shakeMagnitude;
      double shakeY = Math.cos(t * 1.7) * shakeMagnitude;
      gc.save();
      gc.translate(shakeX, shakeY);
    }

    boolean handledTransitionBackground = activeStage != null && activeStage.getBackgroundTag() != null && !activeStage.getBackgroundTag().isBlank();
    var transition = handledTransitionBackground ? null : state.getActiveTransition();
    if (transition != null) {
      switch (transition.getType()) {
        case CROSSFADE -> {
          String prevId = state.getPreviousBackgroundIdDuringTransition();
          String curId = state.getCurrentBackgroundId();
          if (prevId != null && curId != null) {
            renderCrossfadeBackground(scenario.getBackground(prevId), scenario.getBackground(curId), state.getTransitionProgress(), width, height);
            handledTransitionBackground = true;
          }
        }
        case SLIDE_LEFT -> {
          renderSlideBackground(
            scenario.getBackground(state.getPreviousBackgroundIdDuringTransition()),
            scenario.getBackground(state.getCurrentBackgroundId()),
            state.getTransitionProgress(),
            width, height, true
          );
          handledTransitionBackground = true;
        }
        case SLIDE_RIGHT -> {
          renderSlideBackground(
            scenario.getBackground(state.getPreviousBackgroundIdDuringTransition()),
            scenario.getBackground(state.getCurrentBackgroundId()),
            state.getTransitionProgress(),
            width, height, false
          );
          handledTransitionBackground = true;
        }
        case WIPE -> {
          renderWipeBackground(
            scenario.getBackground(state.getPreviousBackgroundIdDuringTransition()),
            scenario.getBackground(state.getCurrentBackgroundId()),
            state.getTransitionProgress(),
            width, height
          );
          handledTransitionBackground = true;
        }
        default -> {
        }
      }
    }
    if (!handledTransitionBackground) {
      VnBackground prevBg = state.getPreviousBackgroundId() != null ? scenario.getBackground(state.getPreviousBackgroundId()) : null;
      if (prevBg != null) {
        renderBackground(prevBg, null, width, height, "bg_prev");
      }
      VnBackground bg = state.getCurrentBackgroundId() != null ? scenario.getBackground(state.getCurrentBackgroundId()) : null;
      if (bg != null || activeStage != null) {
        renderBackground(bg, activeStage, width, height, "bg_current");
      }
    } else if (activeStage != null) {
      renderBackground(null, activeStage, width, height, "bg_current");
    }

    // Apply transition effect if active
    if (state.getActiveTransition() != null) {
      renderTransitionOverlay(state, width, height);
    }

    List<CharacterRenderEntry> orderedCharacters = orderedCharacterEntries(state);
    AudioVisualizerSettings visualizerSettings = resolveAudioVisualizerSettings();
    timelineLayerWorkingSet.beginFrame();
    try {
      renderLayeredScene(orderedCharacters, state, scenario, activeStage, width, height, visualizerSettings);
    } finally {
      timelineLayerWorkingSet.endFrame();
    }
    renderStageLightOverlays(activeStage, width, height, VnStagePreset.LightLayer.FOREGROUND);

    // Render current node content. Dialogue visibility is animated so Ren'Py-
    // style window show/hide dissolves do not pop between scene statements.
    VnNode currentNode = state.getCurrentNode();
    DialogueLine displayedDialogue = displayedDialogue(state);
    syncDialogueFade(state, displayedDialogue);
    if (currentNode != null && !state.isUiHidden()) {
      if (currentNode.getType() == VnNodeType.DIALOGUE && currentNode.getDialogue() != null) {
        String scenarioId = scenario == null ? "" : String.valueOf(scenario.getId());
        String nodeKey = scenarioId + ":" + state.getCurrentNodeIndex() + ":" + currentNode.getSourceLine();
        if (!nodeKey.equals(lastTtsNodeId) && textToSpeechEnabled && tts.isAvailable()) {
          lastTtsNodeId = nodeKey;
          String spokenText = resolveRuntimeText(currentNode.getDialogue().getText());
          String speaker = resolveRuntimeText(currentNode.getDialogue().getSpeakerName());
          if (speaker != null && !speaker.isBlank()) {
            spokenText = speaker + ". " + spokenText;
          }
          tts.speak(spokenText, Locale.getDefault());
        }
      }
      switch (currentNode.getType()) {
        case DIALOGUE:
          renderDialogueWithFade(displayedDialogue, state, scenario, width, height, -1);
          break;
        case CHOICE:
          if (state.getDialoguePresentationMode() == DialoguePresentationMode.NVL) {
            renderNvlHistory(state, width, height);
          }
          renderChoices(currentNode.getChoices(), width, height, -1);
          break;
        case BACKGROUND:
        case TRANSITION:
        case SHOW:
        case HIDE:
        case MOVE:
        case WAIT:
        case AUDIO:
        case PARTICLE:
        case JUMP:
        case CALL:
        case RETURN:
        case EXTERNAL:
          break;
        case END:
          renderEnd(width, height);
          break;
      }
      if (currentNode.getType() != VnNodeType.DIALOGUE
          && currentNode.getType() != VnNodeType.CHOICE
          && currentNode.getType() != VnNodeType.END) {
        // A completed line stays on screen while an intervening scene action,
        // including a blocking timeline or wait, is playing.
        renderDialogueWithFade(displayedDialogue, state, scenario, width, height, -1);
      }
    }
    if ((displayedDialogue == null || state.isUiHidden())
        && dialogueFadeLine != null && dialogueFadeAlpha > 0.001) {
      renderDialogueWithFade(dialogueFadeLine, state, scenario, width, height, -1);
    }

    renderOverlayScreens(state, width, height, null);

    // Render mode indicators (always visible)
    renderModeIndicators(state, width, height);

    // HUD message (toast)
    long now = System.currentTimeMillis();
    if (state.getHudMessage() != null && now < state.getHudMessageExpireAt()) {
      Font hudFont = Font.font(nameFont.getFamily(), FontWeight.BOLD, 16);
      gc.setFont(hudFont);
      String msg = state.getHudMessage();
      double lineHeight = Math.max(hudFont.getSize() * 1.25, computeTextHeight(hudFont) + 3.0);
      HudToastLayout.Layout toast = HudToastLayout.compute(
          msg,
          width,
          lineHeight,
          line -> computeTextWidth(line, hudFont));
      gc.setFill(Color.rgb(0, 0, 0, 0.6));
      double boxW = toast.width();
      double boxH = toast.height();
      double bx = (width - boxW) / 2;
      double by = clamp(height * 0.1, 16.0, Math.max(16.0, height - boxH - 16.0));
      gc.fillRoundRect(bx, by, boxW, boxH, 10, 10);
      gc.setFill(Color.WHITE);
      double baseline = by + HudToastLayout.VERTICAL_PADDING + computeTextAscent(hudFont);
      for (String line : toast.lines()) {
        gc.fillText(line, bx + HudToastLayout.HORIZONTAL_PADDING, baseline);
        baseline += lineHeight;
      }
    }

    if (shaking) {
      gc.restore();
    }

    renderFlashOverlay(state, width, height);
  }

  /** Chooses the dialogue line rendered over the current non-dialogue action. */
  static DialogueLine displayedDialogue(VnState state) {
    if (state == null) return null;
    VnNode currentNode = state.getCurrentNode();
    return currentNode != null && currentNode.getType() == VnNodeType.DIALOGUE
        ? currentNode.getDialogue()
        : state.getRetainedDialogue();
  }

  /**
   * Render with mouse hover support for choices
   */
  public void render(VnState state, VnScenario scenario, double width, double height, double mouseX, double mouseY) {
    this.currentState = state;
    render(state, scenario, width, height);
    
    // Re-render choices/buttons with hover effect (if UI not hidden)
    VnNode currentNode = state.getCurrentNode();
    if (currentNode != null && !state.isUiHidden()) {
      if (currentNode.getType() == VnNodeType.CHOICE) {
        int hoverIndex = getHoveredChoiceIndex(currentNode.getChoices(), width, height, mouseX, mouseY);
        if (state.getDialoguePresentationMode() == DialoguePresentationMode.NVL) {
          renderNvlHistory(state, width, height);
        }
        renderChoices(currentNode.getChoices(), width, height, hoverIndex);
      } else if (currentNode.getType() == VnNodeType.DIALOGUE) {
        int hoverButton = getHoveredTextBoxButtonIndex(state, width, height, mouseX, mouseY);
        // Avoid double-compositing a partially transparent dialogue window.
        if (dialogueFadeAlpha >= 0.999) {
          renderDialogueWithFade(currentNode.getDialogue(), state, scenario, width, height, hoverButton);
        }
      }
    }
    renderOverlayScreens(state, width, height, getHoveredOverlayButton(state, width, height, mouseX, mouseY));
  }

  private boolean prepareParticles(VnState state, double width, double height) {
    VnParticleCommand cmd = state == null ? null : state.getActiveParticleCommand();
    boolean sizeChanged = Math.abs(width - particleConfigWidth) > 0.5
        || Math.abs(height - particleConfigHeight) > 0.5;

    if (cmd == null && renderedParticleCommand == null && particleEmitter.getParticleCount() <= 0) {
      return false;
    }

    if (cmd != null && (cmd != renderedParticleCommand || sizeChanged)) {
      VnParticlePresetLibrary.apply(particleEmitter, cmd, width, height);
      if (cmd.getPrewarmMs() > 0L && particleEmitter.getParticleCount() == 0) {
        long remaining = cmd.getPrewarmMs();
        while (remaining > 0L) {
          long step = Math.min(16L, remaining);
          particleEmitter.update(step);
          remaining -= step;
        }
      }
      renderedParticleCommand = cmd;
      lastParticleLayer = cmd.getLayer();
      particleConfigWidth = width;
      particleConfigHeight = height;
    } else if (cmd == null && renderedParticleCommand != null) {
      particleEmitter.setEmitting(false);
      renderedParticleCommand = null;
    }

    long now = System.nanoTime();
    if (freezeTransientEffects) {
      particleLastFrameNanos = now;
      return cmd != null || particleEmitter.getParticleCount() > 0;
    }
    long deltaMs = 16L;
    if (particleLastFrameNanos > 0L) {
      deltaMs = Math.max(0L, Math.min(250L, (now - particleLastFrameNanos) / 1_000_000L));
    }
    particleLastFrameNanos = now;
    particleEmitter.update(deltaMs);

    return cmd != null || particleEmitter.getParticleCount() > 0;
  }

  public void renderFrozen(VnState state, VnScenario scenario, double width, double height) {
    boolean previous = freezeTransientEffects;
    freezeTransientEffects = true;
    try {
      render(state, scenario, width, height);
    } finally {
      freezeTransientEffects = previous;
    }
  }

  private void renderParticles(double width, double height) {
    if (particleEmitter.getParticleCount() <= 0 && renderedParticleCommand == null) return;
    drawCallStats.incrementOther();
    particleBlitter.setViewport(width, height);
    particleBlitter.push();
    particleBlitter.translate(particleEmitter.getX(), particleEmitter.getY());
    if (particleEmitter.getRotationDeg() != 0.0) {
      particleBlitter.rotateDeg(particleEmitter.getRotationDeg());
    }
    if (particleEmitter.getScaleX() != 1.0 || particleEmitter.getScaleY() != 1.0) {
      particleBlitter.scale(particleEmitter.getScaleX(), particleEmitter.getScaleY());
    }
    particleEmitter.render(particleBlitter);
    particleBlitter.pop();
  }

  private int positionOrdinal(CharacterPosition position) {
    if (position == null) return 0;
    return position.getOrdinal();
  }

  private void renderBackground(VnBackground background, VnStagePreset stage, double width, double height, String timelineEntityId) {
    String backgroundPath = resolveBackgroundPath(background, stage);
    if (backgroundPath == null || backgroundPath.isBlank()) return;
    drawCallStats.incrementOther();
    Image img = loadBackgroundImage(backgroundPath);
    com.jvn.core.scene2d.Entity2D proxy = timelineAccessor != null
        && background != null
        && (stage == null || stage.getBackgroundTag() == null || stage.getBackgroundTag().isBlank())
        ? timelineAccessor.getProxy(timelineEntityId != null ? timelineEntityId : background.getId())
        : null;
    if (img != null) {
      if (stage != null && proxy == null) {
        drawStageBackgroundImage(backgroundPath, img, stage, width, height);
      } else {
        drawBackgroundImage(img, proxy, width, height);
        if (stage != null) {
          applyStageBackgroundFallbackOverlay(stage, width, height);
          renderStageLightOverlays(stage, width, height, VnStagePreset.LightLayer.BACKGROUND);
        }
      }
    } else {
      // Placeholder background
      gc.setFill(Color.DARKSLATEGRAY);
      gc.fillRect(0, 0, width, height);
      gc.setFill(Color.WHITE);
      gc.setFont(Font.font(nameFont.getFamily(), FontWeight.BOLD, 24));
      gc.fillText("No Background Image", 20, 40);
    }
  }

  private String resolveBackgroundPath(VnBackground background, VnStagePreset stage) {
    if (stage != null && stage.getBackgroundTag() != null && !stage.getBackgroundTag().isBlank()) {
      return stage.getBackgroundTag();
    }
    return background == null ? null : background.getImagePath();
  }

  private void drawStageBackgroundImage(String backgroundPath, Image img, VnStagePreset stage, double width, double height) {
    String key = backgroundPath
        + "|stage:" + (stage == null ? "none" : stage.getCacheToken())
        + "|size:" + Math.round(width) + "x" + Math.round(height);
    Image lit = stageBackgroundCache.computeIfAbsent(key, unused ->
        VnStageLightingSupport.buildLitBackground(img, stage, width, height));
    gc.drawImage(lit, 0, 0, width, height);
  }

  private void applyStageBackgroundFallbackOverlay(VnStagePreset stage, double width, double height) {
    if (stage == null) return;
    VnStagePreset.BackgroundGrade grade = stage.getBackgroundGrade();
    if (grade == null) return;
    Color tint = VnStageLightingSupport.parseColor(grade.tintColor(), Color.WHITE);
    double tintStrength = VnStageLightingSupport.clamp(grade.tintStrength(), 0.0, 1.0);
    if (tintStrength > 1e-6) {
      gc.setFill(Color.color(tint.getRed(), tint.getGreen(), tint.getBlue(), tintStrength * 0.14));
      gc.fillRect(0, 0, width, height);
    }
    Color overlay = VnStageLightingSupport.parseColor(grade.overlayColor(), Color.BLACK);
    double overlayOpacity = VnStageLightingSupport.clamp(grade.overlayOpacity(), 0.0, 1.0);
    if (overlayOpacity > 1e-6) {
      gc.setFill(Color.color(overlay.getRed(), overlay.getGreen(), overlay.getBlue(), overlayOpacity * 0.20));
      gc.fillRect(0, 0, width, height);
    }
  }

  private void drawBackgroundImage(Image img, com.jvn.core.scene2d.Entity2D proxy, double width, double height) {
    if (proxy != null && !proxy.isVisible()) return;
    double x = proxy != null ? proxy.getX() : 0.0;
    double y = proxy != null ? proxy.getY() : 0.0;
    double rotation = proxy != null ? proxy.getRotationDeg() : 0.0;
    double scaleX = proxy != null ? proxy.getScaleX() : 1.0;
    double scaleY = proxy != null ? proxy.getScaleY() : 1.0;
    boolean transformed = Math.abs(x) > 1e-6
        || Math.abs(y) > 1e-6
        || Math.abs(rotation) > 1e-6
        || Math.abs(scaleX - 1.0) > 1e-6
        || Math.abs(scaleY - 1.0) > 1e-6;

    if (!transformed) {
      gc.drawImage(img, 0, 0, width, height);
      return;
    }

    gc.save();
    gc.translate(x, y);
    if (Math.abs(rotation) > 1e-6) gc.rotate(rotation);
    if (Math.abs(scaleX - 1.0) > 1e-6 || Math.abs(scaleY - 1.0) > 1e-6) gc.scale(scaleX, scaleY);
    gc.drawImage(img, 0, 0, width, height);
    gc.restore();
  }


  private List<CharacterRenderEntry> orderedCharacterEntries(VnState state) {
    Map<CharacterPosition, VnState.CharacterSlot> characters = state.getVisibleCharacters();

    reusableCharacterEntries.clear();
    for (Map.Entry<CharacterPosition, VnState.CharacterSlot> entry : characters.entrySet()) {
      reusableCharacterEntries.add(new CharacterRenderEntry(
          entry.getKey(),
          entry.getValue(),
          state.getCharacterVisual(entry.getKey()),
          positionOrdinal(entry.getKey())));
    }
    int detachedOrder = 1_000;
    for (VnState.DetachedCharacterSlot detached : state.getDetachedCharacters().values()) {
      if (detached == null || detached.getSlot() == null) continue;
      CharacterPosition basePosition = detached.getBasePosition();
      reusableCharacterEntries.add(new CharacterRenderEntry(
          basePosition,
          detached.getSlot(),
          detached.getVisual(),
          positionOrdinal(basePosition) + detachedOrder++));
    }
    reusableCharacterEntries.sort(
        java.util.Comparator
            .comparingInt((CharacterRenderEntry e) ->
                e.slot() != null ? e.slot().getLayerOrder() : 0)
            .thenComparingInt(CharacterRenderEntry::order)
    );
    return reusableCharacterEntries;
  }

  private void renderLayeredScene(
      List<CharacterRenderEntry> orderedCharacters,
      VnState state,
      VnScenario scenario,
      VnStagePreset stage,
      double width,
      double height,
      AudioVisualizerSettings visualizerSettings) {

    reusableLayeredDraws.clear();
    List<LayeredSceneDraw> draws = reusableLayeredDraws;
    if (orderedCharacters != null) {
      for (int i = 0; i < orderedCharacters.size(); i++) {
        CharacterRenderEntry entry = orderedCharacters.get(i);
        VnState.CharacterSlot slot = entry.slot();
        int z = slot != null ? slot.getLayerOrder() : 0;
        draws.add(new LayeredSceneDraw(z, i, () -> renderCharacterEntry(entry, state, scenario, stage, width, height)));
      }
    }

    draws.add(new LayeredSceneDraw(visualizerSettings.zIndex(), 10_000, () ->
        renderAudioVisualizer(width, height, visualizerSettings)));

    if (prepareParticles(state, width, height)) {
      draws.add(new LayeredSceneDraw(lastParticleLayer, 20_000, () ->
          renderParticles(width, height)));
    }

    draws.sort(java.util.Comparator
        .comparingInt((LayeredSceneDraw item) -> item.z)
        .thenComparingInt(item -> item.order));

    for (LayeredSceneDraw draw : draws) {
      draw.action.run();
    }
  }

  private static final class LayeredSceneDraw {
    final int z;
    final int order;
    final Runnable action;

    LayeredSceneDraw(int z, int order, Runnable action) {
      this.z = z;
      this.order = order;
      this.action = action;
    }
  }

  private record SpriteLayer(
      String path,
      String layerId,
      List<String> targetNames,
      List<GroupLayerTarget> groupTargets,
      Image image
  ) {
    String targetName() {
      return targetNames == null || targetNames.isEmpty() ? null : targetNames.get(0);
    }
  }

  private record GroupLayerTarget(
      VnCharacter.LayerGroup group,
      List<String> targetNames
  ) {
  }

  private record LayerTransformTarget(
      Entity2D proxy,
      VnCharacter.LayerGroup group,
      boolean exact
  ) {
  }

  private record CharacterRenderEntry(
      CharacterPosition position,
      VnState.CharacterSlot slot,
      VnState.CharacterVisual visual,
      int order
  ) {
  }

  record LayerDrawPlanEntry(String layerId, String path, double alpha) {}

  /**
   * Builds a per-layer draw plan for an expression transition: unchanged layers draw once at
   * full alpha (no flicker), changed pairs crossfade, added layers fade in, removed layers fade out.
   */
  static List<LayerDrawPlanEntry> buildLayerCrossfadePlan(
      LayeredCharacterResolver.ExpressionLayerDiff diff,
      List<String> toLayerOrder,
      Map<String, String> fromLayerPathsById,
      Map<String, String> toLayerPathsById,
      double baseAlpha,
      double progress) {
    List<LayerDrawPlanEntry> plan = new ArrayList<>();
    for (String layerId : diff.unchangedLayerIds()) {
      String path = toLayerPathsById.get(layerId);
      if (path != null) plan.add(new LayerDrawPlanEntry(layerId, path, baseAlpha));
    }
    for (LayeredCharacterResolver.LayerChange change : diff.changedPairs()) {
      String fromPath = fromLayerPathsById.get(change.fromLayerId());
      if (fromPath != null) plan.add(new LayerDrawPlanEntry(change.fromLayerId(), fromPath, baseAlpha * (1.0 - progress)));
      String toPath = toLayerPathsById.get(change.toLayerId());
      if (toPath != null) plan.add(new LayerDrawPlanEntry(change.toLayerId(), toPath, baseAlpha * progress));
    }
    for (String layerId : diff.addedLayerIds()) {
      String path = toLayerPathsById.get(layerId);
      if (path != null) plan.add(new LayerDrawPlanEntry(layerId, path, baseAlpha * progress));
    }
    for (String layerId : diff.removedLayerIds()) {
      String path = fromLayerPathsById.get(layerId);
      if (path != null) plan.add(new LayerDrawPlanEntry(layerId, path, baseAlpha * (1.0 - progress)));
    }
    return plan;
  }

  private record EyeFocusDraw(
      boolean active,
      String selectedLayerId,
      String selectedPath,
      String selectedTargetName,
      String replacementSlotLayerId,
      double nudgeX,
      double nudgeY,
      Set<String> mappedLayerIds
  ) {
    static EyeFocusDraw inactive() {
      return new EyeFocusDraw(false, "", "", "", "", 0.0, 0.0, Set.of());
    }

    boolean isMappedLayer(String layerId) {
      return layerId != null && mappedLayerIds.contains(layerId);
    }
  }

  private void renderCharacterEntry(
      CharacterRenderEntry entry,
      VnState state,
      VnScenario scenario,
      VnStagePreset stage,
      double width,
      double height) {
    if (entry == null) return;
    CharacterPosition position = entry.position();
    VnState.CharacterSlot slot = entry.slot();
    if (slot == null) return;
    VnState.CharacterVisual visual = entry.visual();
    double alpha = visual != null ? visual.getAlpha() : 1.0;
    double offsetX = visual != null ? visual.getOffsetX() : 0.0;
    double offsetY = visual != null ? visual.getOffsetY() : 0.0;

    VnCharacter character = scenario.getCharacter(slot.getCharacterId());
    if (character != null) {
      String expression = slot.getExpression();
      String imagePath = character.getExpressionPath(expression);
      VnState.ExpressionTransition transition = state != null ? state.getExpressionTransition(slot) : null;
      if (transition != null && transition.appliesTo(expression)) {
        LayeredCharacterResolver.ExpressionLayerDiff layerDiff = transition.getLayerDiff();
        if (layerDiff != null) {
          renderLayeredExpressionCrossfade(layerDiff, transition, character, position,
              width, height, offsetX, offsetY, slot.getCharacterId(), state, scenario, stage, alpha);
          return;
        }
        String fromPath = character.getExpressionPath(transition.getFromExpression());
        String toPath = character.getExpressionPath(transition.getToExpression());
        if (fromPath != null && toPath != null) {
          preloadSpriteSource(fromPath);
          preloadSpriteSource(toPath);
          double progress = transition.getProgress();
          renderCharacterSpriteWithAlpha(
              fromPath, transition.getFromExpression(), character, position,
              width, height, offsetX, offsetY, slot.getCharacterId(), state, scenario, stage,
              alpha * (1.0 - progress));
          renderCharacterSpriteWithAlpha(
              toPath, transition.getToExpression(), character, position,
              width, height, offsetX, offsetY, slot.getCharacterId(), state, scenario, stage,
              alpha * progress);
          return;
        }
      }
      if (imagePath != null) {
        renderCharacterSpriteWithAlpha(imagePath, expression, character, position,
            width, height, offsetX, offsetY, slot.getCharacterId(), state, scenario, stage, alpha);
      }
    }
  }

  private void preloadSpriteSource(String imagePath) {
    if (imagePath == null || imagePath.isBlank()) return;
    List<String> layerPaths = layerPathsFor(imagePath);
    loadSpriteSourceImage(imagePath, layerPaths);
  }

  private void renderCharacterSpriteWithAlpha(
      String imagePath,
      String expression,
      VnCharacter character,
      CharacterPosition position,
      double width,
      double height,
      double offsetX,
      double offsetY,
      String characterId,
      VnState state,
      VnScenario scenario,
      VnStagePreset stage,
      double alpha) {
    if (imagePath == null || alpha <= 0.001) return;
    gc.save();
    if (alpha < 0.999) gc.setGlobalAlpha(alpha);
    applyGroupTransforms(characterId, state);
    renderCharacterSprite(imagePath, expression, character, position, width, height, offsetX, offsetY,
        characterId, state, scenario, stage);
    gc.restore();
  }

  private void renderLayeredExpressionCrossfade(
      LayeredCharacterResolver.ExpressionLayerDiff layerDiff,
      VnState.ExpressionTransition transition,
      VnCharacter character,
      CharacterPosition position,
      double width,
      double height,
      double offsetX,
      double offsetY,
      String characterId,
      VnState state,
      VnScenario scenario,
      VnStagePreset stage,
      double alpha) {
    String fromExpression = transition.getFromExpression();
    String toExpression = transition.getToExpression();
    Map<String, String> fromLayerPathsById = layerPathsById(character, fromExpression);
    Map<String, String> toLayerPathsById = layerPathsById(character, toExpression);
    List<String> toLayerOrder = character.getExpressionLayerIds(toExpression);

    String toImagePath = character.getExpressionPath(toExpression);
    List<String> toImageLayerPaths = layerPathsFor(toImagePath);
    Image reference = loadSpriteSourceImage(toImagePath, toImageLayerPaths);
    double spriteHeight = height * characterHeightFactor * characterScale(character);
    double spriteWidth = reference != null ? reference.getWidth() * (spriteHeight / reference.getHeight()) : spriteHeight * 0.5;
    double x = position.computeScreenX(width, spriteWidth) + offsetX;
    double y = position.computeScreenY(height, spriteHeight, characterBaselineY) + offsetY;

    List<LayerDrawPlanEntry> plan = buildLayerCrossfadePlan(
        layerDiff, toLayerOrder, fromLayerPathsById, toLayerPathsById, alpha, transition.getProgress());

    gc.save();
    applyGroupTransforms(characterId, state);
    for (LayerDrawPlanEntry planEntry : plan) {
      if (planEntry.alpha() <= 0.001) continue;
      Image layerImage = loadTimelineSpriteLayerImage(planEntry.path());
      if (layerImage == null) {
        String context = characterId + ":" + fromExpression + "->" + toExpression + ":" + planEntry.layerId();
        MissingAssetPlaceholder.report(gc, planEntry.path(), context, x, y, spriteWidth, spriteHeight);
        continue;
      }
      gc.save();
      if (planEntry.alpha() < 0.999) gc.setGlobalAlpha(planEntry.alpha());
      drawCharacterImage(layerImage, planEntry.path(), x, y, spriteWidth, spriteHeight, width, height, stage);
      gc.restore();
    }
    gc.restore();
  }

  /**
   * Warns once per missing character layer path so a blank/silhouette sprite is traceable
   * back to the offending character/expression/layer instead of failing silently.
   */
  static void reportMissingCharacterLayers(VnCharacter character, String characterId, String expression, String imagePath) {
    if (character == null) return;
    List<String> layerIds = character.getExpressionLayerIds(expression);
    if (layerIds.isEmpty()) {
      RenderDiagnostics.missingAsset(imagePath, characterId + ":" + expression);
      return;
    }
    List<String> layerPaths = parseLayerPaths(character.getExpressionPath(expression));
    for (int i = 0; i < layerIds.size(); i++) {
      String layerId = layerIds.get(i);
      String path = i < layerPaths.size() ? layerPaths.get(i) : character.getLayerPath(layerId);
      RenderDiagnostics.missingAsset(path == null ? imagePath : path, characterId + ":" + expression + ":" + layerId);
    }
  }

  private Map<String, String> layerPathsById(VnCharacter character, String expression) {
    List<String> layerIds = character.getExpressionLayerIds(expression);
    List<String> layerPaths = layerPathsFor(character.getExpressionPath(expression));
    Map<String, String> byId = new LinkedHashMap<>();
    for (int i = 0; i < layerIds.size(); i++) {
      String layerId = layerIds.get(i);
      String path = i < layerPaths.size() ? layerPaths.get(i) : character.getLayerPath(layerId);
      if (path != null) byId.put(layerId, path);
    }
    return byId;
  }

  private void applyGroupTransforms(String targetId, VnState state) {
    if (state == null || timelineAccessor == null) return;
    String parentId = state.getDynamicGroups().get(targetId);
    if (parentId == null) return;
    
    applyGroupTransforms(parentId, state);
    
    Entity2D proxy = timelineAccessor.getProxy(parentId);
    if (proxy != null) {
      double px = proxy.getX();
      double py = proxy.getY();
      gc.translate(px, py);
      if (proxy.getRotationDeg() != 0.0) gc.rotate(proxy.getRotationDeg());
      if (proxy.getScaleX() != 1.0 || proxy.getScaleY() != 1.0) gc.scale(proxy.getScaleX(), proxy.getScaleY());
    }
  }

  private void renderCharacterSprite(String imagePath, String expression, VnCharacter character, CharacterPosition position, double width, double height, double offsetX, double offsetY, String characterId, VnState state, VnScenario scenario, VnStagePreset stage) {
    List<String> layerPaths = layerPathsFor(imagePath);
    Image reference = loadSpriteSourceImage(imagePath, layerPaths);
    if (reference == null) {
      reportMissingCharacterLayers(character, characterId, expression, imagePath);
    }
    SpriteLayout spriteLayout = resolveSpriteLayout(
        reference == null ? 0.0 : reference.getWidth(),
        reference == null ? 0.0 : reference.getHeight(),
        width,
        height,
        characterHeightFactor,
        characterBaselineY,
        characterScale(character));
    double spriteHeight = spriteLayout.height();
    double spriteWidth = spriteLayout.width();
    double defaultX = position.computeScreenX(width, spriteWidth) + offsetX;
    double defaultY = position.computeScreenY(height, spriteHeight, spriteLayout.baselineY()) + offsetY;

    if (timelineAccessor != null && characterId != null) {
      Entity2D proxy = timelineAccessor.getProxy(characterId);
      if (proxy != null && hasTimelinePosition(proxy)) {
        double px = timelineDrawX(proxy, defaultX);
        double py = timelineDrawY(proxy, defaultY);
        boolean hasEyeFocus = state != null && state.getEyeFocusRequest(characterId) != null && layerPaths.size() > 1;
        if (hasEyeFocus && reference != null && renderTimelineDrivenLayers(
            character, expression, characterId, layerPaths, px, py, spriteWidth, spriteHeight, width, height, state, scenario, stage)) {
          return;
        }
        if (reference != null) {
          drawCharacterImage(reference, imagePath, px, py, spriteWidth, spriteHeight, width, height, stage);
        } else {
          gc.setFill(Color.rgb(200, 200, 200, 0.4));
          gc.fillRoundRect(px, py, spriteWidth, spriteHeight, 20, 20);
        }
        return;
      }
      if (reference != null && renderTimelineDrivenLayers(
          character, expression, characterId, layerPaths, defaultX, defaultY, spriteWidth, spriteHeight, width, height, state, scenario, stage)) {
        return;
      }
    } else if (reference != null && renderTimelineDrivenLayers(
        character, expression, characterId, layerPaths, defaultX, defaultY, spriteWidth, spriteHeight, width, height, state, scenario, stage)) {
      return;
    }
    double resolvedX = timelineDisplacementFallbackX(defaultX, state, characterId, offsetX);
    double resolvedY = timelineDisplacementFallbackY(defaultY, state, characterId, offsetY);
    if (reference == null) {
      // Draw placeholder silhouette box
      gc.setFill(Color.rgb(200, 200, 200, 0.4));
      gc.fillRoundRect(resolvedX, resolvedY, spriteWidth, spriteHeight, 20, 20);
      gc.setStroke(Color.WHITE);
      gc.setLineWidth(2);
      gc.strokeRoundRect(resolvedX, resolvedY, spriteWidth, spriteHeight, 20, 20);
      return;
    }

    drawCharacterImageWithTimelineTransform(reference, imagePath, resolvedX, resolvedY, spriteWidth, spriteHeight, width, height, stage, state, characterId);
  }

  private void drawCharacterImageWithTimelineTransform(
      Image image,
      String imagePath,
      double x,
      double y,
      double width,
      double height,
      double canvasWidth,
      double canvasHeight,
      VnStagePreset stage,
      VnState state,
      String characterId) {
    VnState.TimelineTransform transform = state == null ? null : state.getTimelineTransform(characterId);
    if (transform == null || !hasRenderableTimelineTransform(transform)) {
      drawCharacterImage(image, imagePath, x, y, width, height, canvasWidth, canvasHeight, stage);
      return;
    }

    double originX = transform.hasPivotX() ? transform.getPivotX() : 0.5;
    double originY = transform.hasPivotY() ? transform.getPivotY() : 1.0;
    double scaleX = transform.hasScaleX() ? transform.getScaleX() : 1.0;
    double scaleY = transform.hasScaleY() ? transform.getScaleY() : 1.0;
    double rotation = transform.hasRotation() ? transform.getRotationDeg() : 0.0;
    double pivotX = x + originX * width;
    double pivotY = y + originY * height;

    gc.save();
    gc.translate(pivotX, pivotY);
    if (Math.abs(rotation) > 1e-6) gc.rotate(rotation);
    if (Math.abs(scaleX - 1.0) > 1e-6 || Math.abs(scaleY - 1.0) > 1e-6) gc.scale(scaleX, scaleY);
    gc.translate(-pivotX, -pivotY);
    drawCharacterImage(image, imagePath, x, y, width, height, canvasWidth, canvasHeight, stage);
    gc.restore();
  }

  private boolean hasRenderableTimelineTransform(VnState.TimelineTransform transform) {
    if (transform == null) return false;
    return (transform.hasScaleX() && Math.abs(transform.getScaleX() - 1.0) > 1e-6)
        || (transform.hasScaleY() && Math.abs(transform.getScaleY() - 1.0) > 1e-6)
        || (transform.hasRotation() && Math.abs(transform.getRotationDeg()) > 1e-6);
  }

  private double timelineDisplacementFallbackX(double defaultX, VnState state, String characterId, double visualOffsetX) {
    VnState.TimelineDisplacement displacement = state == null ? null : state.getTimelineDisplacement(characterId);
    if (displacement == null || !displacement.hasX() || Math.abs(visualOffsetX) > 1e-6) return defaultX;
    return defaultX + displacement.getX();
  }

  private double timelineDisplacementFallbackY(double defaultY, VnState state, String characterId, double visualOffsetY) {
    VnState.TimelineDisplacement displacement = state == null ? null : state.getTimelineDisplacement(characterId);
    if (displacement == null || !displacement.hasY() || Math.abs(visualOffsetY) > 1e-6) return defaultY;
    return defaultY + displacement.getY();
  }

  private boolean renderTimelineDrivenLayers(
      VnCharacter character,
      String expression,
      String characterId,
      List<String> layerPaths,
      double defaultX,
      double defaultY,
      double spriteWidth,
      double spriteHeight,
      double canvasWidth,
      double canvasHeight,
      VnState state,
      VnScenario scenario,
      VnStagePreset stage
  ) {
    if (layerPaths == null || layerPaths.size() <= 1) return false;
    List<SpriteLayer> layers = spriteLayers(character, expression, characterId, layerPaths);
    Map<String, Entity2D> inferredReplacementProxies = inferredReplacementProxies(
        character, characterId, expression, layers);
    EyeFocusDraw eyeFocus = resolveEyeFocusDraw(
        state, scenario, character, expression, characterId, layers,
        defaultX, defaultY, spriteWidth, spriteHeight, canvasWidth, canvasHeight);
    boolean hasLayerProxy = false;
    if (timelineAccessor != null) {
      for (SpriteLayer layer : layers) {
        if (layer != null && hasAnyLayerTransformProxy(layer, inferredReplacementProxies)) {
          hasLayerProxy = true;
          break;
        }
      }
    }
    if (!hasLayerProxy && !eyeFocus.active()) return false;

    for (SpriteLayer layer : layers) {
      if (layer == null) continue;
      Image layerImage = isLoadedImage(layer.image())
          ? layer.image()
          : loadTimelineSpriteLayerImage(layer.path());
      if (!isLoadedImage(layerImage)) {
        MissingAssetPlaceholder.report(gc, layer.path(), "layer:" + layer.layerId(), defaultX, defaultY, spriteWidth, spriteHeight);
        continue;
      }
      SpriteLayer drawLayer = new SpriteLayer(
          layer.path(), layer.layerId(), layer.targetNames(), layer.groupTargets(), layerImage);
      double nudgeX = 0.0;
      double nudgeY = 0.0;
      if (eyeFocus.active() && eyeFocus.isMappedLayer(layer.layerId())) {
        boolean selectedLayerPresent = expressionContainsLayer(layers, eyeFocus.selectedLayerId());
        boolean drawSelected = layer.layerId().equals(eyeFocus.selectedLayerId())
            || (!selectedLayerPresent && layer.layerId().equals(eyeFocus.replacementSlotLayerId()));
        if (!drawSelected) continue;
        Image selectedImage = loadTimelineSpriteLayerImage(eyeFocus.selectedPath());
        if (!isLoadedImage(selectedImage)) {
          MissingAssetPlaceholder.report(gc, eyeFocus.selectedPath(), "layer:" + eyeFocus.selectedLayerId(), defaultX, defaultY, spriteWidth, spriteHeight);
          continue;
        }
        drawLayer = new SpriteLayer(
            eyeFocus.selectedPath(),
            eyeFocus.selectedLayerId(),
            timelineDeclaredLayerTargetNames(character, characterId, expression, eyeFocus.selectedLayerId()),
            layer.groupTargets(),
            selectedImage);
        nudgeX = eyeFocus.nudgeX();
        nudgeY = eyeFocus.nudgeY();
      }
      List<LayerTransformTarget> transforms = resolveLayerTransforms(
          drawLayer, inferredReplacementProxies);
      if (transforms.stream().anyMatch(target -> target.exact() && target.proxy() != null && !target.proxy().isVisible())) {
        continue;
      }
      drawTimelineLayer(drawLayer, transforms, defaultX + nudgeX, defaultY + nudgeY,
          spriteWidth, spriteHeight, canvasWidth, canvasHeight, stage);
    }
    return true;
  }

  private EyeFocusDraw resolveEyeFocusDraw(
      VnState state,
      VnScenario scenario,
      VnCharacter character,
      String expression,
      String characterId,
      List<SpriteLayer> layers,
      double defaultX,
      double defaultY,
      double spriteWidth,
      double spriteHeight,
      double canvasWidth,
      double canvasHeight
  ) {
    if (state == null || scenario == null || character == null || characterId == null || layers == null || layers.isEmpty()) {
      return EyeFocusDraw.inactive();
    }
    VnState.EyeFocusRequest request = state.getEyeFocusRequest(characterId);
    if (request == null) return EyeFocusDraw.inactive();

    VnEyeFocusProfile profile = resolveEyeFocusProfile(character, expression, request.expression());
    if (profile == null || profile.layerIds().isEmpty()) return EyeFocusDraw.inactive();

    double targetX;
    double targetY;
    if (request.hasPointTarget()) {
      targetX = request.targetX();
      targetY = request.targetY();
    } else if (request.hasCharacterTarget()) {
      double[] point = characterFocusPoint(state, scenario, request.targetCharacterId(), canvasWidth, canvasHeight);
      if (point == null) return EyeFocusDraw.inactive();
      targetX = point[0];
      targetY = point[1];
    } else {
      return EyeFocusDraw.inactive();
    }

    double sourceX = defaultX + spriteWidth * profile.sourceX();
    double sourceY = defaultY + spriteHeight * profile.sourceY();
    double dx = (targetX - sourceX) / Math.max(1.0, spriteWidth);
    double dy = (targetY - sourceY) / Math.max(1.0, spriteHeight);
    EyeFocusResolver.Result resolved = EyeFocusResolver.resolve(
        0.0,
        0.0,
        dx,
        dy,
        request.deadZone(),
        profile.maxNudgePx(),
        request.strength());

    String selectedLayerId = profile.layerIdFor(resolved.keypadIndex());
    if (selectedLayerId == null || selectedLayerId.isBlank()) {
      selectedLayerId = profile.layerIdFor(5);
    }
    if (selectedLayerId == null || selectedLayerId.isBlank()) return EyeFocusDraw.inactive();

    String selectedPath = character.getLayerPath(selectedLayerId);
    if (selectedPath == null || selectedPath.isBlank()) {
      for (SpriteLayer layer : layers) {
        if (layer != null && selectedLayerId.equals(layer.layerId())) {
          selectedPath = layer.path();
          break;
        }
      }
    }
    if (selectedPath == null || selectedPath.isBlank()) return EyeFocusDraw.inactive();

    Set<String> mapped = new LinkedHashSet<>();
    for (String layerId : profile.layerIds().values()) {
      if (layerId != null && !layerId.isBlank()) mapped.add(layerId);
    }
    String replacementSlot = replacementSlotLayerId(profile, layers, mapped, selectedLayerId);
    if (replacementSlot.isBlank()) return EyeFocusDraw.inactive();
    String selectedTargetName = timelineLayerTargetName(characterId, expression, selectedLayerId);
    return new EyeFocusDraw(
        true,
        selectedLayerId,
        selectedPath,
        selectedTargetName,
        replacementSlot,
        resolved.nudgeX(),
        resolved.nudgeY(),
        Set.copyOf(mapped));
  }

  private VnEyeFocusProfile resolveEyeFocusProfile(VnCharacter character, String expression, String requestedExpression) {
    if (character == null) return null;
    Map<String, VnEyeFocusProfile> profiles = eyeFocusProfiles();
    String currentExpression = expression == null || expression.isBlank() ? "neutral" : expression;
    VnEyeFocusProfile profile = profiles.get(VnEyeFocusProfile.key(character.getId(), currentExpression));
    if (profile != null) return profile;
    if (requestedExpression != null && !requestedExpression.isBlank()) {
      profile = profiles.get(VnEyeFocusProfile.key(character.getId(), requestedExpression));
      if (profile != null) return profile;
    }
    profile = profiles.get(VnEyeFocusProfile.key(character.getId(), "neutral"));
    if (profile != null) return profile;
    return VnEyeFocusProfile.autoDetect(character, currentExpression).orElse(null);
  }

  private Map<String, VnEyeFocusProfile> eyeFocusProfiles() {
    if (eyeFocusProfiles != null) return eyeFocusProfiles;
    if (projectRoot != null) {
      eyeFocusProfiles = VnEyeFocusProfileStore.byKey(VnEyeFocusProfileStore.load(projectRoot));
    } else {
      eyeFocusProfiles = VnEyeFocusProfileStore.loadFromAssets(new AssetCatalog());
    }
    return eyeFocusProfiles;
  }

  private String replacementSlotLayerId(
      VnEyeFocusProfile profile,
      List<SpriteLayer> layers,
      Set<String> mapped,
      String selectedLayerId
  ) {
    for (SpriteLayer layer : layers) {
      if (layer != null && selectedLayerId.equals(layer.layerId())) {
        return selectedLayerId;
      }
    }
    String neutral = profile.layerIdFor(5);
    if (neutral != null && !neutral.isBlank()) {
      for (SpriteLayer layer : layers) {
        if (layer != null && neutral.equals(layer.layerId())) {
          return neutral;
        }
      }
    }
    for (SpriteLayer layer : layers) {
      if (layer != null && mapped.contains(layer.layerId())) {
        return layer.layerId();
      }
    }
    return "";
  }

  private boolean expressionContainsLayer(List<SpriteLayer> layers, String layerId) {
    if (layers == null || layerId == null || layerId.isBlank()) return false;
    for (SpriteLayer layer : layers) {
      if (layer != null && layerId.equals(layer.layerId())) return true;
    }
    return false;
  }

  private double[] characterFocusPoint(VnState state, VnScenario scenario, String characterId, double canvasWidth, double canvasHeight) {
    if (state == null || scenario == null || characterId == null || characterId.isBlank()) return null;
    CharacterPosition position = state.getCharacterPosition(characterId);
    VnState.CharacterSlot slot = position == null ? null : state.getVisibleCharacters().get(position);
    VnState.CharacterVisual visual = position == null ? null : state.getCharacterVisual(position);
    VnState.DetachedCharacterSlot detached = null;
    if (slot == null) {
      detached = state.getDetachedCharacter(characterId);
      if (detached == null) return null;
      position = detached.getBasePosition();
      slot = detached.getSlot();
      visual = detached.getVisual();
    }
    if (slot == null) return null;
    VnCharacter character = scenario.getCharacter(slot.getCharacterId());
    if (character == null) return null;
    String imagePath = character.getExpressionPath(slot.getExpression());
    List<String> layerPaths = layerPathsFor(imagePath);
    Image reference = loadSpriteSourceImage(imagePath, layerPaths);
    SpriteLayout spriteLayout = resolveSpriteLayout(
        reference == null ? 0.0 : reference.getWidth(),
        reference == null ? 0.0 : reference.getHeight(),
        canvasWidth,
        canvasHeight,
        characterHeightFactor,
        characterBaselineY,
        characterScale(character));
    double spriteHeight = spriteLayout.height();
    double spriteWidth = spriteLayout.width();
    double offsetX = visual != null ? visual.getOffsetX() : 0.0;
    double offsetY = visual != null ? visual.getOffsetY() : 0.0;
    VnState.TimelineDisplacement displacement = state.getTimelineDisplacement(characterId);
    if (displacement != null) {
      if (displacement.hasX() && Math.abs(offsetX) < 1e-6) offsetX += displacement.getX();
      if (displacement.hasY() && Math.abs(offsetY) < 1e-6) offsetY += displacement.getY();
    }
    double x = position.computeScreenX(canvasWidth, spriteWidth) + offsetX;
    double y = position.computeScreenY(canvasHeight, spriteHeight, spriteLayout.baselineY()) + offsetY;
    return new double[] {x + spriteWidth * 0.5, y + spriteHeight * 0.26};
  }

  static SpriteLayout resolveSpriteLayout(
      double imageWidth,
      double imageHeight,
      double viewportWidth,
      double viewportHeight,
      double characterHeightFactor,
      double characterBaselineY,
      double characterScale
  ) {
    double safeViewportHeight = Math.max(1.0, viewportHeight);
    double safeScale = Double.isFinite(characterScale) && characterScale > 0.0 ? characterScale : 1.0;
    boolean canvasAligned = isCanvasAlignedSprite(imageWidth, imageHeight, viewportWidth, safeViewportHeight);
    double spriteHeight = safeViewportHeight
        * (canvasAligned ? 1.0 : characterHeightFactor)
        * safeScale;
    double spriteWidth = imageWidth > 0.0 && imageHeight > 0.0
        ? imageWidth * (spriteHeight / imageHeight)
        : spriteHeight * 0.5;
    double baselineY = canvasAligned ? 1.0 : characterBaselineY;
    return new SpriteLayout(spriteWidth, spriteHeight, baselineY, canvasAligned);
  }

  private static boolean isCanvasAlignedSprite(
      double imageWidth,
      double imageHeight,
      double viewportWidth,
      double viewportHeight
  ) {
    if (imageWidth <= 0.0 || imageHeight <= 0.0 || viewportWidth <= 0.0 || viewportHeight <= 0.0) {
      return false;
    }
    double imageAspect = imageWidth / imageHeight;
    double viewportAspect = viewportWidth / viewportHeight;
    return Math.abs(imageAspect - viewportAspect) <= Math.max(1e-6, viewportAspect * 0.001);
  }

  record SpriteLayout(double width, double height, double baselineY, boolean canvasAligned) {}

  private void drawTimelineLayer(
      SpriteLayer layer,
      List<LayerTransformTarget> transforms,
      double x,
      double y,
      double width,
      double height,
      double canvasWidth,
      double canvasHeight,
      VnStagePreset stage
  ) {
    gc.save();
    double alpha = 1.0;
    if (transforms != null) {
      for (LayerTransformTarget target : transforms) {
        if (target != null && target.proxy() instanceof Sprite2D sprite) {
          alpha *= sprite.getAlpha();
        }
      }
    }
    if (alpha < 0.999) gc.setGlobalAlpha(Math.max(0.0, Math.min(1.0, alpha)));
    if (transforms != null) {
      for (LayerTransformTarget target : transforms) {
        applyLayerTransformTarget(target, x, y, width, height);
      }
    }
    drawCharacterImage(layer.image(), layer.path(), x, y, width, height, canvasWidth, canvasHeight, stage);
    gc.restore();
  }

  private void applyLayerTransformTarget(LayerTransformTarget target,
                                         double baseX,
                                         double baseY,
                                         double width,
                                         double height) {
    if (target == null || target.proxy() == null) return;
    Entity2D proxy = target.proxy();
    double dx = timelineTransformOffsetX(proxy, baseX);
    double dy = timelineTransformOffsetY(proxy, baseY);
    if (Math.abs(dx) > 1e-6 || Math.abs(dy) > 1e-6) {
      gc.translate(dx, dy);
    }

    double originX = transformOriginX(target);
    double originY = transformOriginY(target);
    double pivotX = baseX + originX * width;
    double pivotY = baseY + originY * height;
    gc.translate(pivotX, pivotY);
    if (proxy.getRotationDeg() != 0.0) gc.rotate(proxy.getRotationDeg());
    if (proxy.getScaleX() != 1.0 || proxy.getScaleY() != 1.0) gc.scale(proxy.getScaleX(), proxy.getScaleY());
    gc.translate(-pivotX, -pivotY);
  }

  private double transformOriginX(LayerTransformTarget target) {
    Entity2D proxy = target == null ? null : target.proxy();
    if (proxy == null) return 0.5;
    VnCharacter.LayerGroup group = target.group();
    if (group != null && group.hasPivot() && !hasAuthoredProxyOriginX(proxy)) {
      return group.pivotX();
    }
    return proxy.getOriginX();
  }

  private double transformOriginY(LayerTransformTarget target) {
    Entity2D proxy = target == null ? null : target.proxy();
    if (proxy == null) return 1.0;
    VnCharacter.LayerGroup group = target.group();
    if (group != null && group.hasPivot() && !hasAuthoredProxyOriginY(proxy)) {
      return group.pivotY();
    }
    return proxy.getOriginY();
  }

  private boolean hasAuthoredProxyOriginX(Entity2D proxy) {
    return proxy instanceof VnCharacterSceneAccessor.TimelineProxyEntity timelineProxy
        && timelineProxy.hasTimelineOriginX();
  }

  private boolean hasAuthoredProxyOriginY(Entity2D proxy) {
    return proxy instanceof VnCharacterSceneAccessor.TimelineProxyEntity timelineProxy
        && timelineProxy.hasTimelineOriginY();
  }

  private double timelineTransformOffsetX(Entity2D proxy, double defaultX) {
    if (proxy == null) return 0.0;
    if (proxy instanceof TimelineDrivenEntity driven) {
      return driven.hasTimelineX() ? proxy.getX() : 0.0;
    }
    return hasTimelinePosition(proxy) ? proxy.getX() - defaultX : 0.0;
  }

  private double timelineTransformOffsetY(Entity2D proxy, double defaultY) {
    if (proxy == null) return 0.0;
    if (proxy instanceof TimelineDrivenEntity driven) {
      return driven.hasTimelineY() ? proxy.getY() : 0.0;
    }
    return hasTimelinePosition(proxy) ? proxy.getY() - defaultY : 0.0;
  }

  private boolean hasAnyLayerTransformProxy(
      SpriteLayer layer,
      Map<String, Entity2D> inferredReplacementProxies
  ) {
    if (timelineAccessor == null || layer == null) return false;
    if (firstProxy(layer.targetNames()) != null) return true;
    if (layer.groupTargets() != null) {
      for (GroupLayerTarget groupTarget : layer.groupTargets()) {
        if (groupTarget != null && firstProxy(groupTarget.targetNames()) != null) return true;
      }
    }
    return inferredReplacementProxies != null && inferredReplacementProxies.containsKey(layer.layerId());
  }

  private List<LayerTransformTarget> resolveLayerTransforms(
      SpriteLayer layer,
      Map<String, Entity2D> inferredReplacementProxies
  ) {
    if (timelineAccessor == null || layer == null) return List.of();
    List<LayerTransformTarget> transforms = new ArrayList<>();
    if (layer.groupTargets() != null) {
      for (GroupLayerTarget groupTarget : layer.groupTargets()) {
        if (groupTarget == null) continue;
        Entity2D proxy = firstProxy(groupTarget.targetNames());
        if (proxy != null) transforms.add(new LayerTransformTarget(proxy, groupTarget.group(), true));
      }
    }

    Entity2D exact = firstProxy(layer.targetNames());
    if (exact != null) {
      transforms.add(new LayerTransformTarget(exact, null, true));
      return List.copyOf(transforms);
    }

    Entity2D inferred = inferredReplacementProxies == null ? null : inferredReplacementProxies.get(layer.layerId());
    if (inferred != null) {
      transforms.add(new LayerTransformTarget(inferred, null, false));
      return List.copyOf(transforms);
    }

    return List.copyOf(transforms);
  }

  private Map<String, Entity2D> inferredReplacementProxies(
      VnCharacter character,
      String characterId,
      String expression,
      List<SpriteLayer> expressionLayers
  ) {
    if (timelineAccessor == null || character == null || expressionLayers == null || expressionLayers.isEmpty()) {
      return Map.of();
    }
    Set<String> activeLayerIds = new LinkedHashSet<>();
    for (SpriteLayer layer : expressionLayers) {
      if (layer != null && layer.layerId() != null && !layer.layerId().isBlank()) {
        activeLayerIds.add(layer.layerId());
      }
    }
    Map<String, Entity2D> animatedLayers = new LinkedHashMap<>();
    for (String declaredLayerId : character.getLayerIds()) {
      if (declaredLayerId == null || declaredLayerId.isBlank() || activeLayerIds.contains(declaredLayerId)) continue;
      Entity2D proxy = firstProxy(timelineDeclaredLayerTargetNames(
          character, characterId, expression, declaredLayerId));
      if (proxy != null) animatedLayers.put(declaredLayerId, proxy);
    }
    if (animatedLayers.isEmpty()) return Map.of();

    Map<String, Entity2D> inferred = new LinkedHashMap<>();
    for (SpriteLayer layer : expressionLayers) {
      if (layer == null || layer.layerId() == null || layer.layerId().isBlank()
          || firstProxy(layer.targetNames()) != null) {
        continue;
      }
      String inferredLayerId = LayeredCharacterResolver.inferReplacementLayerId(
          layer.layerId(), animatedLayers.keySet());
      if (inferredLayerId != null) {
        Entity2D proxy = animatedLayers.get(inferredLayerId);
        if (proxy != null) inferred.put(layer.layerId(), proxy);
      }
    }
    return inferred.isEmpty() ? Map.of() : Map.copyOf(inferred);
  }

  private List<String> timelineDeclaredLayerTargetNames(
      VnCharacter character,
      String characterId,
      String expression,
      String layerId
  ) {
    return layerMetadata(character, expression, characterId).targetNames.computeIfAbsent(layerId,
        ignored -> com.jvn.core.vn.LayerTargetNaming.declaredLayerTargetNames(character, characterId, expression, layerId));
  }

  private Entity2D firstProxy(List<String> targetNames) {
    if (timelineAccessor == null || targetNames == null || targetNames.isEmpty()) return null;
    for (String targetName : targetNames) {
      if (targetName == null || targetName.isBlank()) continue;
      Entity2D proxy = timelineAccessor.getProxy(targetName);
      if (proxy != null) return proxy;
    }
    return null;
  }

  private boolean hasTimelinePosition(Entity2D proxy) {
    if (proxy instanceof TimelineDrivenEntity driven) {
      return driven.hasTimelineX() || driven.hasTimelineY();
    }
    return proxy != null && (Math.abs(proxy.getX()) > 1e-6 || Math.abs(proxy.getY()) > 1e-6);
  }

  private double timelineDrawX(Entity2D proxy, double defaultX) {
    if (proxy instanceof TimelineDrivenEntity driven) {
      return driven.hasTimelineX() ? defaultX + proxy.getX() : defaultX;
    }
    return hasTimelinePosition(proxy) ? proxy.getX() : defaultX;
  }

  private double timelineDrawY(Entity2D proxy, double defaultY) {
    if (proxy instanceof TimelineDrivenEntity driven) {
      return driven.hasTimelineY() ? defaultY + proxy.getY() : defaultY;
    }
    return hasTimelinePosition(proxy) ? proxy.getY() : defaultY;
  }

  private List<SpriteLayer> spriteLayers(VnCharacter character, String expression, String characterId, List<String> layerPaths) {
    LayerMetadata metadata = layerMetadata(character, expression, characterId);
    List<SpriteLayer> cached = metadata.expressions.get(layerPaths);
    if (cached != null) return cached;
    List<SpriteLayer> layers = new ArrayList<>();
    List<String> layerIds = character != null ? character.getExpressionLayerIds(expression) : List.of();
    for (int i = 0; i < layerPaths.size(); i++) {
      String path = layerPaths.get(i);
      String layerId = i < layerIds.size() ? layerIds.get(i) : "";
      if (layerId == null || layerId.isBlank()) layerId = fallbackLayerId(path, i);
      List<String> targetNames = timelineDeclaredLayerTargetNames(character, characterId, expression, layerId);
      List<GroupLayerTarget> groupTargets = timelineGroupTargets(character, characterId, expression, layerId);
      // Layer rasters are only needed when an active timeline or eye-focus request requires
      // independent drawing. Loading them here made every static composite reload all of its
      // full-canvas sources on every frame, even though the composite itself was cached.
      layers.add(new SpriteLayer(path, layerId, targetNames, groupTargets, null));
    }
    List<SpriteLayer> result = List.copyOf(layers);
    metadata.expressions.put(List.copyOf(layerPaths), result);
    return result;
  }

  private String timelineLayerTargetName(String characterId, String expression, String layerId) {
    List<String> names = timelineLayerTargetNames(characterId, expression, layerId);
    return names.isEmpty() ? null : names.get(0);
  }

  private List<String> timelineLayerTargetNames(String characterId, String expression, String layerId) {
    return com.jvn.core.vn.LayerTargetNaming.layerTargetNames(characterId, expression, layerId);
  }

  private List<GroupLayerTarget> timelineGroupTargets(
      VnCharacter character,
      String characterId,
      String expression,
      String layerId
  ) {
    if (character == null || layerId == null || layerId.isBlank()) return List.of();
    List<VnCharacter.LayerGroup> chain = character.getLayerGroupChainForLayer(layerId);
    if (chain.isEmpty()) return List.of();
    List<GroupLayerTarget> targets = new ArrayList<>();
    for (VnCharacter.LayerGroup group : chain) {
      if (group == null || group.id().isBlank()) continue;
      List<String> names = timelineGroupTargetNames(characterId, expression, group.id());
      if (!names.isEmpty()) targets.add(new GroupLayerTarget(group, names));
    }
    return List.copyOf(targets);
  }

  static List<String> timelineGroupTargetNames(String characterId, String expression, String groupId) {
    return com.jvn.core.vn.LayerTargetNaming.groupTargetNames(characterId, expression, groupId);
  }

  private String fallbackLayerId(String path, int index) {
    if (path == null || path.isBlank()) return "layer" + (index + 1);
    String normalized = path.replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
    int dot = name.lastIndexOf('.');
    if (dot > 0) name = name.substring(0, dot);
    String safe = selectorSafeName(name);
    return safe.isBlank() ? "layer" + (index + 1) : safe;
  }

  private List<String> layerPathsFor(String imagePathSpec) {
    if (imagePathSpec == null) return List.of();
    List<String> cached = layerPathCache.get(imagePathSpec);
    if (cached != null) return cached;
    if (layerPathCache.size() >= MAX_CACHED_LAYER_PATH_SPECS) layerPathCache.clear();
    List<String> parsed = parseLayerPaths(imagePathSpec);
    layerPathCache.put(imagePathSpec, parsed);
    return parsed;
  }

  private String selectorSafeName(String raw) {
    return com.jvn.core.vn.LayerTargetNaming.selectorSafeName(raw);
  }


  public static List<String> parseLayerPaths(String imagePathSpec) {
    if (imagePathSpec == null || imagePathSpec.isBlank()) return List.of();
    if (imagePathSpec.indexOf('|') < 0) return List.of(imagePathSpec.trim());

    List<String> layers = new ArrayList<>();
    int start = 0;
    for (int separator; (separator = imagePathSpec.indexOf('|', start)) >= 0; start = separator + 1) {
      String path = imagePathSpec.substring(start, separator).trim();
      if (!path.isEmpty()) layers.add(path);
    }
    String path = imagePathSpec.substring(start).trim();
    if (!path.isEmpty()) layers.add(path);
    return layers.isEmpty() ? List.of(imagePathSpec.trim()) : List.copyOf(layers);
  }

  private Image firstAvailableImage(List<String> layerPaths) {
    if (layerPaths == null) return null;
    for (String path : layerPaths) {
      Image img = loadSpriteLayerImage(path);
      if (isLoadedImage(img)) return img;
    }
    return null;
  }

  private Image loadSpriteSourceImage(String imagePathSpec, List<String> layerPaths) {
    if (imagePathSpec == null || imagePathSpec.isBlank()) return firstAvailableImage(layerPaths);
    if (layerPaths == null || layerPaths.size() <= 1) return firstAvailableImage(layerPaths);
    String cacheKey = "__composite_sprite__:" + imagePathSpec;
    Image cached = compositeSpriteCache.get(cacheKey);
    if (isLoadedImage(cached) && cached.getWidth() > 1.0 && cached.getHeight() > 1.0) return cached;
    List<Image> layers = new ArrayList<>();
    int width = 1;
    int height = 1;
    for (String path : layerPaths) {
      Image layer = loadSpriteLayerImage(path);
      if (!isLoadedImage(layer)) continue;
      layers.add(layer);
      width = Math.max(width, (int) Math.round(layer.getWidth()));
      height = Math.max(height, (int) Math.round(layer.getHeight()));
    }
    if (layers.isEmpty()) return null;
    Canvas canvas = new Canvas(width, height);
    GraphicsContext spriteGc = canvas.getGraphicsContext2D();
    for (Image layer : layers) {
      spriteGc.drawImage(layer, 0, 0);
    }
    SnapshotParameters snapshotParameters = new SnapshotParameters();
    snapshotParameters.setFill(Color.TRANSPARENT);
    WritableImage out = new WritableImage(width, height);
    canvas.snapshot(snapshotParameters, out);
    compositeSpriteCache.put(cacheKey, out);
    return out;
  }

  private Image loadSpriteLayerImage(String path) {
    if (path == null || path.isBlank()) return null;
    Image cached = imageCache.get(path);
    if (isLoadedImage(cached)) return cached;
    Image loaded = loadImageBlocking(path);
    if (isLoadedImage(loaded)) {
      imageCache.put(path, loaded);
      return loaded;
    }
    return null;
  }

  private Image loadTimelineSpriteLayerImage(String path) {
    return timelineLayerWorkingSet.getOrLoad(path, this::loadSpriteLayerImage);
  }

  private boolean isLoadedImage(Image image) {
    return image != null && !image.isError() && image.getWidth() > 0.0 && image.getHeight() > 0.0;
  }

  private void drawCharacterImage(Image source,
                                  String spriteTag,
                                  double x,
                                  double y,
                                  double drawWidth,
                                  double drawHeight,
                                  double canvasWidth,
                                  double canvasHeight,
                                  VnStagePreset stage) {
    if (source == null) return;
    drawCallStats.incrementCharacterLayer();
    if (stage == null || stage.getLights().isEmpty()) {
      gc.drawImage(source, x, y, drawWidth, drawHeight);
      return;
    }
    String key = stageCharacterCacheKey(spriteTag, stage.getCacheToken(), x, y, drawWidth, drawHeight, canvasWidth, canvasHeight);
    boolean[] missed = {false};
    Image lit = stageCharacterCache.computeIfAbsent(key, unused -> {
      missed[0] = true;
      return VnStageLightingSupport.buildLitCharacter(source, spriteTag, x, y, drawWidth, drawHeight, canvasWidth, canvasHeight, stage);
    });
    if (missed[0]) drawCallStats.incrementStageLightingRecomposite();
    gc.drawImage(lit, x, y, drawWidth, drawHeight);
  }

  /**
   * Builds the stage-lighting cache key for a character layer. Position is snapped to
   * {@link #LIGHTING_CACHE_POSITION_GRID_PX} so idle-bob/breathing jitter reuses the last
   * lit bitmap instead of forcing a relight every frame; the draw itself still uses the
   * exact float position passed separately to {@code gc.drawImage}. Everything else that
   * can change rendered output (sprite identity, stage/lighting config, drawn size, canvas
   * size) stays exact so a real change always invalidates the cache.
   */
  static String stageCharacterCacheKey(
      String spriteTag,
      String stageCacheToken,
      double x,
      double y,
      double drawWidth,
      double drawHeight,
      double canvasWidth,
      double canvasHeight) {
    long gx = Math.round(x / LIGHTING_CACHE_POSITION_GRID_PX);
    long gy = Math.round(y / LIGHTING_CACHE_POSITION_GRID_PX);
    return spriteTag
        + "|stage:" + stageCacheToken
        + "|pos:" + gx + "," + gy
        + "|size:" + Math.round(drawWidth) + "x" + Math.round(drawHeight)
        + "|canvas:" + Math.round(canvasWidth) + "x" + Math.round(canvasHeight);
  }

  private void drawLayerStack(List<String> layerPaths, double x, double y, double width, double height) {
    if (layerPaths == null) return;
    for (String path : layerPaths) {
      Image img = loadImage(path);
      if (img != null) {
        gc.drawImage(img, x, y, width, height);
      }
    }
  }

  private VnStagePreset resolveActiveStagePreset(VnState state, VnScenario scenario) {
    if (state == null || scenario == null) return null;
    String stageId = state.getActiveStagePresetId();
    if (stageId == null || stageId.isBlank()) return null;
    return scenario.getStagePreset(stageId);
  }

  private void renderStageLightOverlays(VnStagePreset stage, double canvasWidth, double canvasHeight, VnStagePreset.LightLayer layer) {
    if (stage == null || layer == null || stage.getLights().isEmpty()) return;
    boolean hasSolo = stage.hasSoloLights();
    double minDimension = Math.max(1.0, Math.min(canvasWidth, canvasHeight));
    for (VnStagePreset.Light light : stage.getLights()) {
      if (light == null || light.layer() != layer || light.muted() || (hasSolo && !light.solo())) continue;
      Color color = VnStageLightingSupport.parseColor(light.color(), Color.web("#ffd7a8"));
      double alpha = layer == VnStagePreset.LightLayer.FOREGROUND
          ? VnStageLightingSupport.foregroundLightAlpha(light)
          : VnStageLightingSupport.backgroundLightAlpha(light);
      if (alpha <= 1e-6) continue;
      double targetX = light.sceneX() * canvasWidth;
      double targetY = light.sceneY() * canvasHeight;
      double sourceX = light.sourceX() * canvasWidth;
      double sourceY = light.sourceY() * canvasHeight;
      double radius = Math.max(10.0, light.radius() * minDimension);

      switch (light.type()) {
        case POLYGON -> drawPolygonLightOverlay(light, color, alpha, canvasWidth, canvasHeight);
        case CONE -> drawConeLightOverlay(sourceX, sourceY, targetX, targetY, radius, color, alpha);
        case STRIP, WINDOW -> drawStripLightOverlay(sourceX, sourceY, targetX, targetY, radius * 0.42, color, alpha);
        default -> {
          gc.setFill(new javafx.scene.paint.RadialGradient(
              0, 0, targetX, targetY, radius, false, CycleMethod.NO_CYCLE,
              new Stop(0.0, Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha)),
              new Stop(0.42, Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha * 0.55)),
              new Stop(1.0, Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.0))
          ));
          gc.fillOval(targetX - radius, targetY - radius, radius * 2.0, radius * 2.0);
        }
      }
    }
  }

  private void drawPolygonLightOverlay(VnStagePreset.Light light, Color color, double alpha, double canvasWidth, double canvasHeight) {
    List<VnStagePreset.Point> polygon = light.polygon();
    if (polygon == null || polygon.size() < 3) return;
    double[] xs = new double[polygon.size()];
    double[] ys = new double[polygon.size()];
    for (int i = 0; i < polygon.size(); i++) {
      xs[i] = polygon.get(i).x() * canvasWidth;
      ys[i] = polygon.get(i).y() * canvasHeight;
    }
    gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
    gc.fillPolygon(xs, ys, xs.length);
  }

  private void drawConeLightOverlay(double sourceX, double sourceY, double targetX, double targetY, double radius, Color color, double alpha) {
    double dx = targetX - sourceX;
    double dy = targetY - sourceY;
    double length = Math.max(1.0, Math.hypot(dx, dy));
    double nx = dx / length;
    double ny = dy / length;
    double px = -ny;
    double py = nx;
    double endWidth = Math.max(18.0, radius * 0.48);
    double sourceWidth = Math.max(6.0, endWidth * 0.18);
    double[] xs = {
        sourceX + px * sourceWidth,
        sourceX - px * sourceWidth,
        targetX - px * endWidth,
        targetX + px * endWidth
    };
    double[] ys = {
        sourceY + py * sourceWidth,
        sourceY - py * sourceWidth,
        targetY - py * endWidth,
        targetY + py * endWidth
    };
    gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
    gc.fillPolygon(xs, ys, 4);
  }

  private void drawStripLightOverlay(double sourceX, double sourceY, double targetX, double targetY, double halfWidth, Color color, double alpha) {
    double dx = targetX - sourceX;
    double dy = targetY - sourceY;
    double length = Math.max(1.0, Math.hypot(dx, dy));
    double px = -dy / length;
    double py = dx / length;
    double[] xs = {
        sourceX + px * halfWidth,
        sourceX - px * halfWidth,
        targetX - px * halfWidth,
        targetX + px * halfWidth
    };
    double[] ys = {
        sourceY + py * halfWidth,
        sourceY - py * halfWidth,
        targetY - py * halfWidth,
        targetY + py * halfWidth
    };
    gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
    gc.fillPolygon(xs, ys, 4);
  }

  private void renderAudioVisualizer(double width, double height, AudioVisualizerSettings settings) {
    if (!settings.enabled()) {
      decayVisualizer(0.86, true);
      return;
    }
    drawCallStats.incrementOther();
    int activeBars = settings.bars();
    if (activeBars <= 0) {
      decayVisualizer(0.86, true);
      return;
    }
    if (audioFacade == null) {
      decayVisualizer(0.86, true);
      return;
    }

    float[] magnitudes = audioFacade.getBgmSpectrumMagnitudes();
    long updatedAt = audioFacade.getBgmSpectrumUpdatedAtNanos();
    long nowNs = System.nanoTime();
    boolean hasFreshData = magnitudes != null
        && magnitudes.length > 0
        && (updatedAt <= 0L || (nowNs - updatedAt) <= VnAudioVisualizerConfig.STALE_NS);

    if (hasFreshData) {
      mapSpectrumToTargets(magnitudes, visualizerTargets, activeBars);
      updateAudioVisualizerState(activeBars);
    } else {
      decayVisualizer(0.9, false);
      clearInactiveVisualizerState(activeBars);
    }

    double maxLevel = 0.0;
    for (int i = 0; i < activeBars; i++) {
      double level = visualizerLevels[i];
      if (level > maxLevel) maxLevel = level;
    }
    if (maxLevel < 0.015) return;

    TextBoxGeometry textBox = computeTextBoxGeometry(width, height);
    double regionBottom = Math.min(height, textBox.y() - 2.0);
    if (regionBottom <= 8.0) return;
    double regionHeight = Math.max(24.0, regionBottom * settings.heightFactor());
    double regionTop = Math.max(0.0, regionBottom - regionHeight);
    double sidePadding = Math.max(0.0, width * 0.018);
    double regionWidth = Math.max(1.0, width - sidePadding * 2.0);
    if (regionWidth <= 8.0) return;

    AudioVisualizerPalette palette = resolveAudioVisualizerPalette(settings, maxLevel);

    gc.save();
    gc.setGlobalAlpha(1.0);
    drawAudioVisualizerBackdrop(settings, palette, sidePadding, regionTop, regionWidth, regionHeight, regionBottom);
    drawAudioVisualizerBars(settings, palette, activeBars, sidePadding, regionWidth, regionTop, regionBottom, regionHeight);
    gc.restore();
  }

  private void updateAudioVisualizerState(int activeBars) {
    boolean beat = detectVisualizerBeat(activeBars);
    visualizerBeatFlashIntensity = beat ? 1.0 : visualizerBeatFlashIntensity * 0.90;

    for (int i = 0; i < activeBars; i++) {
      double target = visualizerTargets[i];
      double diff = target - visualizerLevels[i];

      visualizerLevelVelocities[i] = (visualizerLevelVelocities[i] + diff * 0.28) * 0.84;
      visualizerLevels[i] = clamp(visualizerLevels[i] + visualizerLevelVelocities[i], 0.0, 1.0);
      if (Math.abs(diff) < 0.015) {
        visualizerLevels[i] = clamp(visualizerLevels[i] * 0.82 + target * 0.18, 0.0, 1.0);
      }

      if (visualizerLevels[i] > visualizerPeakLevels[i]) {
        visualizerPeakLevels[i] = visualizerLevels[i];
        visualizerPeakVelocities[i] = 0.0;
        visualizerGlowLevels[i] = Math.max(visualizerGlowLevels[i], 0.18 + visualizerLevels[i] * 0.82);
      } else {
        visualizerPeakVelocities[i] += 0.012 + (1.0 - visualizerLevels[i]) * 0.010;
        visualizerPeakLevels[i] = Math.max(visualizerLevels[i], visualizerPeakLevels[i] - visualizerPeakVelocities[i] * 0.045);
      }

      double targetWidth = 0.70 + Math.pow(visualizerLevels[i], 0.72) * 0.62;
      visualizerWidthVelocities[i] = (visualizerWidthVelocities[i]
          + (targetWidth - visualizerWidthMultipliers[i]) * 0.22) * 0.86;
      visualizerWidthMultipliers[i] = clamp(visualizerWidthMultipliers[i] + visualizerWidthVelocities[i], 0.62, 1.42);
      visualizerGlowLevels[i] *= 0.91;

      if (beat) {
        visualizerGlowLevels[i] = Math.max(visualizerGlowLevels[i], 0.28 + visualizerLevels[i] * 0.45);
        visualizerWidthMultipliers[i] = clamp(visualizerWidthMultipliers[i] + 0.06, 0.62, 1.42);
      }
    }

    clearInactiveVisualizerState(activeBars);
  }

  private boolean detectVisualizerBeat(int activeBars) {
    int bassBars = Math.min(6, activeBars);
    if (bassBars <= 0) return false;

    double bassEnergy = 0.0;
    for (int i = 0; i < bassBars; i++) {
      bassEnergy += visualizerTargets[i];
    }
    bassEnergy /= bassBars;

    double average = 0.0;
    for (double value : visualizerBassHistory) {
      average += value;
    }
    average /= visualizerBassHistory.length;

    visualizerBassHistory[visualizerBassHistoryIndex] = bassEnergy;
    visualizerBassHistoryIndex = (visualizerBassHistoryIndex + 1) % visualizerBassHistory.length;

    long nowNs = System.nanoTime();
    double threshold = average * 1.35 + 0.06;
    if (bassEnergy > threshold && (nowNs - visualizerLastBeatAtNanos) > 180_000_000L) {
      visualizerLastBeatAtNanos = nowNs;
      return true;
    }
    return false;
  }

  private AudioVisualizerPalette resolveAudioVisualizerPalette(AudioVisualizerSettings settings, double maxLevel) {
    boolean cycleColors = VnAudioVisualizerConfig.isAutoToken(settings.colorToken());
    if (cycleColors) {
      visualizerHue += 0.55 + maxLevel * 0.45 + visualizerBeatFlashIntensity * 0.30;
      while (visualizerHue >= 360.0) visualizerHue -= 360.0;
    }

    Color base = cycleColors
        ? Color.hsb(visualizerHue, 0.76, 1.0)
        : parseColor(settings.colorToken(), Color.web("#7DE2FF"));
    Color accent = VnAudioVisualizerConfig.isAutoToken(settings.accentToken())
        ? base.interpolate(Color.WHITE, 0.36)
        : parseColor(settings.accentToken(), base.interpolate(Color.WHITE, 0.36));
    return new AudioVisualizerPalette(base, accent, base.darker().darker());
  }

  private void drawAudioVisualizerBackdrop(
      AudioVisualizerSettings settings,
      AudioVisualizerPalette palette,
      double x,
      double regionTop,
      double regionWidth,
      double regionHeight,
      double regionBottom) {
    gc.setFill(new LinearGradient(
        0, regionTop, 0, regionBottom,
        false, CycleMethod.NO_CYCLE,
        new Stop(0.0, palette.base().deriveColor(0, 1.0, 1.12, settings.alpha() * 0.12)),
        new Stop(0.42, palette.base().deriveColor(0, 1.0, 1.0, settings.alpha() * 0.03)),
        new Stop(1.0, Color.TRANSPARENT)));
    gc.fillRect(x, regionTop, regionWidth, regionHeight);

    if (visualizerBeatFlashIntensity > 0.02 && VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style())) {
      gc.setFill(palette.accent().deriveColor(0, 1.0, 1.0, settings.alpha() * 0.08 * visualizerBeatFlashIntensity));
      gc.fillRect(x, regionTop, regionWidth, regionHeight);
    }

    gc.setStroke(palette.accent().deriveColor(0, 1.0, 1.0, settings.alpha() * 0.28));
    gc.setLineWidth(1.0);
    gc.strokeLine(x, regionBottom + 0.5, x + regionWidth, regionBottom + 0.5);
  }

  private void drawAudioVisualizerBars(
      AudioVisualizerSettings settings,
      AudioVisualizerPalette palette,
      int activeBars,
      double sidePadding,
      double regionWidth,
      double regionTop,
      double regionBottom,
      double regionHeight) {
    double bandWidth = regionWidth / activeBars;
    double baseBarWidth = bandWidth * (VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style()) ? 0.76 : 0.68);
    boolean traceStarted = false;

    if (VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style())) {
      gc.setStroke(palette.accent().deriveColor(0, 1.0, 1.0, settings.alpha() * 0.52));
      gc.setLineWidth(1.8);
      gc.beginPath();
    }

    for (int i = 0; i < activeBars; i++) {
      double level = visualizerLevels[i];
      if (level <= 0.002) continue;

      double normalized = Math.pow(level, VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style()) ? 0.68 : 0.78);
      double barHeight = Math.max(2.0, normalized * regionHeight);
      double widthMultiplier = VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style()) ? visualizerWidthMultipliers[i] : 1.0;
      double actualWidth = clamp(baseBarWidth * widthMultiplier, 1.0, Math.max(1.0, bandWidth - 0.6));
      double barX = sidePadding + i * bandWidth + (bandWidth - actualWidth) * 0.5;
      double barY = regionBottom - barHeight;

      Color barBase = palette.base().interpolate(palette.accent(), (i / (double) Math.max(1, activeBars - 1)) * 0.24);
      Color barTop = barBase.interpolate(palette.accent(), 0.46).interpolate(Color.WHITE, Math.min(0.28, level * 0.24));
      Color barBottom = palette.shadow().interpolate(barBase, 0.30);

      if (settings.glow() && VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style())) {
        double glowPad = 2.0 + visualizerGlowLevels[i] * 5.0;
        gc.setFill(barBase.deriveColor(0, 1.0, 1.0, settings.alpha() * (0.08 + visualizerGlowLevels[i] * 0.12)));
        gc.fillRoundRect(
            barX - glowPad * 0.5,
            Math.max(regionTop, barY - glowPad),
            actualWidth + glowPad,
            Math.min(regionHeight, barHeight + glowPad * 1.5),
            actualWidth + glowPad,
            actualWidth + glowPad);
      }

      gc.setFill(new LinearGradient(
          0, barY, 0, regionBottom,
          false, CycleMethod.NO_CYCLE,
          new Stop(0.0, barTop.deriveColor(0, 1.0, 1.0, settings.alpha())),
          new Stop(0.55, barBase.deriveColor(0, 1.0, 1.0, settings.alpha() * 0.96)),
          new Stop(1.0, barBottom.deriveColor(0, 1.0, 1.0, settings.alpha() * 0.92))));
      gc.fillRoundRect(barX, barY, actualWidth, barHeight, 5.0, 5.0);

      if (VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style())) {
        double peakLevel = Math.max(level, visualizerPeakLevels[i]);
        if (peakLevel > level + 0.015) {
          double peakY = regionBottom - Math.max(2.0, Math.pow(peakLevel, 0.70) * regionHeight);
          gc.setFill(palette.accent().interpolate(Color.WHITE, 0.20).deriveColor(0, 1.0, 1.0, settings.alpha() * 0.90));
          gc.fillRoundRect(barX, peakY, actualWidth, 3.0, 3.0, 3.0);
        }

        double traceX = barX + actualWidth * 0.5;
        double traceY = Math.max(regionTop, barY - Math.min(14.0, 3.0 + visualizerGlowLevels[i] * 7.0));
        if (!traceStarted) {
          gc.moveTo(traceX, traceY);
          traceStarted = true;
        } else {
          gc.lineTo(traceX, traceY);
        }
      }
    }

    if (traceStarted && VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style())) {
      gc.stroke();
    }
  }

  private void decayVisualizer(double factor, boolean hard) {
    for (int i = 0; i < visualizerLevels.length; i++) {
      visualizerLevels[i] *= factor;
      if (visualizerLevels[i] < 0.0001) visualizerLevels[i] = 0.0;
      visualizerLevelVelocities[i] *= hard ? 0.68 : 0.82;
      visualizerPeakVelocities[i] += hard ? 0.006 : 0.010;
      visualizerPeakLevels[i] = Math.max(visualizerLevels[i], visualizerPeakLevels[i] - visualizerPeakVelocities[i] * (hard ? 0.065 : 0.045));
      if (visualizerPeakLevels[i] < 0.0001) visualizerPeakLevels[i] = 0.0;
      visualizerWidthVelocities[i] *= hard ? 0.72 : 0.82;
      visualizerWidthMultipliers[i] = clamp(1.0 + (visualizerWidthMultipliers[i] - 1.0) * factor, 0.62, 1.42);
      if (Math.abs(visualizerWidthMultipliers[i] - 1.0) < 0.002) visualizerWidthMultipliers[i] = 1.0;
      visualizerGlowLevels[i] *= hard ? 0.80 : 0.88;
    }
    visualizerBeatFlashIntensity *= hard ? 0.82 : 0.90;
  }

  private void clearInactiveVisualizerState(int activeBars) {
    for (int i = activeBars; i < visualizerLevels.length; i++) {
      visualizerLevels[i] = 0.0;
      visualizerTargets[i] = 0.0;
      visualizerLevelVelocities[i] = 0.0;
      visualizerPeakLevels[i] = 0.0;
      visualizerPeakVelocities[i] = 0.0;
      visualizerWidthMultipliers[i] = 1.0;
      visualizerWidthVelocities[i] = 0.0;
      visualizerGlowLevels[i] = 0.0;
    }
  }

  private void mapSpectrumToTargets(float[] magnitudes, double[] out, int activeBars) {
    Arrays.fill(out, 0.0);
    if (magnitudes == null || magnitudes.length == 0 || out.length == 0 || activeBars <= 0) return;
    double bandsPerBar = magnitudes.length / (double) activeBars;

    for (int i = 0; i < activeBars; i++) {
      int start = (int) Math.floor(i * bandsPerBar);
      int end = (int) Math.ceil((i + 1) * bandsPerBar);
      if (end <= start) end = start + 1;
      start = Math.max(0, Math.min(start, magnitudes.length - 1));
      end = Math.max(start + 1, Math.min(end, magnitudes.length));

      double sum = 0.0;
      int count = 0;
      for (int j = start; j < end; j++) {
        double db = magnitudes[j];
        double normalized = (db + 60.0) / 60.0;
        normalized = clamp(normalized, 0.0, 1.0);
        normalized = Math.pow(normalized, 0.72);
        sum += normalized;
        count++;
      }
      double avg = count == 0 ? 0.0 : (sum / count);
      double freqWeight = 1.0 - (i / (double) activeBars) * 0.35;
      out[i] = clamp(avg * freqWeight, 0.0, 1.0);
    }
  }

  private record AudioVisualizerSettings(
      boolean enabled,
      int bars,
      String style,
      String colorToken,
      String accentToken,
      double alpha,
      boolean glow,
      double heightFactor,
      int zIndex) {}

  private record AudioVisualizerPalette(Color base, Color accent, Color shadow) {}

  private record DialogueRenderEntry(
      String speaker,
      String speakerColor,
      String text,
      int revealedChars
  ) {}

  private record BubbleGeometry(
      double x,
      double y,
      double width,
      double height,
      double anchorX,
      double anchorY,
      double tailSize,
      boolean tailOnTop
  ) {}

  private void renderDialogue(
      DialogueLine dialogue,
      VnState state,
      VnScenario scenario,
      double width,
      double height,
      int hoveredButtonIndex) {
    if (dialogue == null) return;
    DialoguePresentationMode mode = state == null ? DialoguePresentationMode.STANDARD : state.getDialoguePresentationMode();
    switch (mode) {
      case NVL -> renderNvlDialogue(dialogue, state, width, height);
      case BUBBLE -> renderBubbleDialogue(dialogue, state, scenario, width, height);
      default -> renderStandardDialogue(dialogue, state, width, height, hoveredButtonIndex);
    }
  }

  private void renderDialogueWithFade(
      DialogueLine dialogue,
      VnState state,
      VnScenario scenario,
      double width,
      double height,
      int hoveredButtonIndex
  ) {
    if (dialogue == null || dialogueFadeAlpha <= 0.001) return;
    gc.save();
    gc.setGlobalAlpha(gc.getGlobalAlpha() * clamp(dialogueFadeAlpha, 0.0, 1.0));
    renderDialogue(dialogue, state, scenario, width, height, hoveredButtonIndex);
    gc.restore();
  }

  private void syncDialogueFade(VnState state, DialogueLine displayedDialogue) {
    boolean requestedVisible = state != null
        && !state.isUiHidden()
        && displayedDialogue != null;

    if (state != dialogueFadeState) {
      dialogueFadeState = state;
      dialogueFadeLine = requestedVisible ? displayedDialogue : null;
      dialogueFadeVisible = requestedVisible;
      dialogueFadeAlpha = requestedVisible ? 0.0 : 0.0;
      dialogueFadeFrom = dialogueFadeAlpha;
      dialogueFadeTarget = requestedVisible ? 1.0 : 0.0;
      dialogueFadeStartedAtNanos = System.nanoTime();
    } else if (requestedVisible != dialogueFadeVisible) {
      dialogueFadeVisible = requestedVisible;
      dialogueFadeFrom = dialogueFadeAlpha;
      dialogueFadeTarget = requestedVisible ? 1.0 : 0.0;
      dialogueFadeStartedAtNanos = System.nanoTime();
    }

    if (requestedVisible) {
      dialogueFadeLine = displayedDialogue;
    }
    if (freezeTransientEffects) {
      dialogueFadeFrom = dialogueFadeAlpha;
      dialogueFadeStartedAtNanos = System.nanoTime();
    } else {
      double durationMs = 500.0;
      Double configured = readDoubleVariable(state, VAR_DIALOGUE_FADE_MS);
      if (configured != null && Double.isFinite(configured)) {
        durationMs = clamp(configured, 0.0, 10_000.0);
      }
      if (durationMs <= 0.0) {
        dialogueFadeAlpha = dialogueFadeTarget;
      } else {
        double elapsedMs = Math.max(0.0, (System.nanoTime() - dialogueFadeStartedAtNanos) / 1_000_000.0);
        double progress = clamp(elapsedMs / durationMs, 0.0, 1.0);
        dialogueFadeAlpha = dialogueFadeFrom + (dialogueFadeTarget - dialogueFadeFrom) * progress;
      }
    }
    if (!requestedVisible && dialogueFadeAlpha <= 0.001) {
      dialogueFadeLine = null;
    }
  }

  private void renderStandardDialogue(DialogueLine dialogue, VnState state, double width, double height, int hoveredButtonIndex) {
    if (dialogue == null) return;
    boolean defaultDialogueStyle = shouldUseDefaultDialogueUi(state) || shouldUseDefaultDialogueStyle(state);
    Color activeTextBoxFillColor = defaultDialogueStyle ? TEXTBOX_COLOR : textBoxFillColor;
    Color activeNameBoxFillColor = defaultDialogueStyle ? NAME_BOX_COLOR : nameBoxFillColor;
    Color activeNameTextFillColor = resolveSpeakerColor(
        dialogue,
        defaultDialogueStyle ? Color.web("#FFD78A") : nameTextFillColor);
    Color activeDialogueTextFillColor = defaultDialogueStyle ? TEXT_COLOR : dialogueTextFillColor;
    double fscale = state == null ? 1.0 : state.getSettings().getUiFontScale();
    Font activeNameFont = defaultDialogueStyle
        ? Font.font(DEFAULT_FONT_FAMILY, FontWeight.BOLD, DEFAULT_NAME_FONT_SIZE * fscale)
        : nameFont;
    Font activeDialogueFont = defaultDialogueStyle
        ? Font.font(DEFAULT_FONT_FAMILY, FontWeight.NORMAL, DEFAULT_DIALOGUE_FONT_SIZE * fscale)
        : dialogueFont;
    double activeNameBoxOpacity = defaultDialogueStyle ? 1.0 : nameBoxRenderOpacity;

    TextBoxGeometry textBox = computeTextBoxGeometry(width, height);
    double textBoxX = textBox.x();
    double textBoxY = textBox.y();
    double textBoxWidth = textBox.width();
    double textBoxHeight = textBox.height();

    // Draw text box background (asset if provided, otherwise default fill).
    String speakerName = resolveRuntimeText(dialogue.getSpeakerName());
    boolean hasSpeaker = speakerName != null && !speakerName.isEmpty();
    boolean useTextBoxAsset = shouldUseTextBoxAsset(state);
    Image activeTextBoxImage = useTextBoxAsset
        ? (hasSpeaker || narrationTextBoxImage == null ? textBoxImage : narrationTextBoxImage)
        : null;
    boolean clipTextBox = useTextBoxAsset && hasPolygon(textBoxBoundsPolygon);
    if (clipTextBox) {
      gc.save();
      clipToLocalPolygon(textBoxBoundsPolygon, textBoxX, textBoxY, textBoxWidth, textBoxHeight);
    }
    if (activeTextBoxImage != null) {
      gc.drawImage(activeTextBoxImage, textBoxX, textBoxY, textBoxWidth, textBoxHeight);
      if (textBoxAssetOverlayOpacity > 0.001) {
        gc.setFill(withOpacity(activeTextBoxFillColor, textBoxAssetOverlayOpacity));
        gc.fillRect(textBoxX, textBoxY, textBoxWidth, textBoxHeight);
      }
    } else {
      gc.setFill(activeTextBoxFillColor);
      gc.fillRect(textBoxX, textBoxY, textBoxWidth, textBoxHeight);
    }
    if (clipTextBox) gc.restore();

    // Draw name box if speaker exists
    if (hasSpeaker) {
      double nameBoxX = textBoxX + uiLayout.nameBoxXOffset();
      double nameBoxY = textBoxY + uiLayout.nameBoxYOffset();
      double nameBoxW;
      if (uiLayout.nameBoxAutoWidth()) {
        double textW = computeTextWidth(speakerName, activeNameFont);
        nameBoxW = Math.max(textW + uiLayout.nameTextXOffset() * 2, uiLayout.nameBoxWidth());
      } else {
        nameBoxW = uiLayout.nameBoxWidth();
      }
      double nameBoxH = uiLayout.nameBoxHeight();
      Image activeNameBoxImage = defaultDialogueStyle ? null : nameBoxImage;
      boolean clipNameBox = !defaultDialogueStyle && hasPolygon(nameBoxBoundsPolygon);
      if (clipNameBox) {
        gc.save();
        clipToLocalPolygon(nameBoxBoundsPolygon, nameBoxX, nameBoxY, nameBoxW, nameBoxH);
      }
      double prevAlpha = gc.getGlobalAlpha();
      if (activeNameBoxOpacity < 0.999) gc.setGlobalAlpha(prevAlpha * activeNameBoxOpacity);
      if (activeNameBoxImage != null) {
        gc.drawImage(activeNameBoxImage, nameBoxX, nameBoxY, nameBoxW, nameBoxH);
      } else {
        gc.setFill(activeNameBoxFillColor);
        gc.fillRect(nameBoxX, nameBoxY, nameBoxW, nameBoxH);
      }
      gc.setGlobalAlpha(prevAlpha);
      if (clipNameBox) gc.restore();

      gc.setFill(activeNameTextFillColor);
      gc.setFont(activeNameFont);
      double nameContentX = nameBoxX + uiLayout.nameTextXOffset();
      double nameContentW = Math.max(0, nameBoxW - uiLayout.nameTextXOffset() * 2);
      double nameTextW = computeTextWidth(speakerName, activeNameFont);
      double nameTextBaselineY = uiLayout.nameTextYAlign() >= 0.0
          ? resolvePaddedTextBaselineY(
              nameBoxY,
              nameBoxH,
              uiLayout.nameTextTopPadding(),
              uiLayout.nameTextBottomPadding(),
              activeNameFont,
              uiLayout.nameTextYAlign())
          : nameBoxY + uiLayout.nameTextBaselineOffset();
      gc.fillText(
          speakerName,
          resolveAlignedTextX(nameContentX, nameContentW, nameTextW, nameTextXAlign),
          nameTextBaselineY
      );
    }

    // Parse and render dialogue text with effects
    String fullText = resolveRuntimeText(dialogue.getText());
    List<TextSpan> spans = TextParser.parse(fullText);
    int plainLength = TextParser.plainLength(fullText);
    int revealedLength = Math.min(state.getTextRevealProgress(), plainLength);

    double textX = textBoxX + uiLayout.dialogueTextHorizontalPadding();
    double textTop = textBoxY + uiLayout.dialogueTextTopPadding();
    double textWidth = Math.max(
        60,
        textBoxWidth - uiLayout.dialogueTextHorizontalPadding() - uiLayout.dialogueTextRightPadding());
    double textHeight = Math.max(
        20,
        textBoxHeight - uiLayout.dialogueTextTopPadding() - uiLayout.dialogueTextBottomPadding());
    double textBaselineY = textTop + computeTextAscent(activeDialogueFont);
    gc.save();
    if (!defaultDialogueStyle && hasPolygon(dialogueTextBoundsPolygon)) {
      clipToLocalPolygon(dialogueTextBoundsPolygon, textX, textTop, textWidth, textHeight);
    } else {
      gc.beginPath();
      gc.rect(textX, textTop, textWidth, textHeight);
      gc.closePath();
      gc.clip();
    }
    List<StyledLine> dialogueLines = layoutStyledLines(
        spans,
        revealedLength,
        textWidth,
        activeDialogueFont,
        activeDialogueTextFillColor);
    drawStyledLines(
        dialogueLines,
        textX,
        textBaselineY,
        textWidth,
        dialogueTextXAlign,
        activeDialogueFont,
        activeDialogueTextFillColor);
    gc.restore();

    // Draw continue indicator if text is fully revealed
    if (revealedLength >= plainLength && state.isWaitingForInput()) {
      drawContinueIndicatorAfterText(
          dialogueLines,
          textX,
          textBaselineY,
          textWidth,
          dialogueTextXAlign,
          activeDialogueFont,
          textBoxX + textBoxWidth - 30,
          textBoxY + textBoxHeight - 20);
    }

    renderTextBoxButtons(textBox, width, height, hoveredButtonIndex, state);
  }

  private void renderNvlHistory(VnState state, double width, double height) {
    renderNvlEntries(collectNvlEntries(state, null), width, height);
  }

  private void renderNvlDialogue(DialogueLine dialogue, VnState state, double width, double height) {
    renderNvlEntries(collectNvlEntries(state, dialogue), width, height);
  }

  private void renderNvlEntries(List<DialogueRenderEntry> entries, double width, double height) {
    if (entries == null || entries.isEmpty()) return;
    double panelX = clamp(uiLayout.nvlX() * width, 0.0, width);
    double panelY = clamp(uiLayout.nvlY() * height, 0.0, height);
    double panelW = clamp(uiLayout.nvlWidth() * width, 120.0, width - panelX);
    double panelH = clamp(uiLayout.nvlHeight() * height, 80.0, height - panelY);
    double pad = uiLayout.nvlPadding();
    double speakerW = Math.max(40.0, uiLayout.nvlSpeakerWidth());
    double entryGap = Math.max(0.0, uiLayout.nvlEntryGap());
    double bodyGap = 16.0;

    drawPanel(nvlPanelImage, nvlPanelFillColor, nvlPanelOpacity, panelX, panelY, panelW, panelH, 18.0, null, 0.0);

    gc.save();
    gc.beginPath();
    gc.rect(panelX, panelY, panelW, panelH);
    gc.closePath();
    gc.clip();

    double y = panelY + pad + nameFont.getSize();
    double textX = panelX + pad + speakerW + bodyGap;
    double textWidth = Math.max(80.0, panelW - pad * 2 - speakerW - bodyGap);
    double speakerX = panelX + pad;

    for (DialogueRenderEntry entry : entries) {
      if (y > panelY + panelH) break;
      String speaker = entry.speaker() == null ? "" : entry.speaker();
      gc.setFill(parseColor(entry.speakerColor(), nvlSpeakerTextFillColor));
      gc.setFont(nameFont);
      gc.fillText(truncateText(speaker, Math.max(20.0, speakerW - 8.0), nameFont), speakerX, y);

      String text = entry.text() == null ? "" : entry.text();
      List<TextSpan> spans = TextParser.parse(text);
      int revealed = Math.min(entry.revealedChars(), TextParser.plainLength(text));
      double bodyTop = y;
      drawStyledText(spans, revealed, textX, bodyTop, textWidth, 0.0, dialogueFont, nvlTextFillColor);
      double bodyHeight = measureStyledTextHeight(spans, revealed, textWidth, dialogueFont);
      double entryHeight = Math.max(nameFont.getSize() * 1.2, bodyHeight);
      y += entryHeight + entryGap;
    }

    gc.restore();
  }

  private void renderBubbleDialogue(
      DialogueLine dialogue,
      VnState state,
      VnScenario scenario,
      double width,
      double height) {
    if (dialogue == null) return;
    String speaker = resolveRuntimeText(dialogue.getSpeakerName());
    String fullText = resolveRuntimeText(dialogue.getText());
    List<TextSpan> spans = TextParser.parse(fullText);
    int revealedChars = Math.min(state.getTextRevealProgress(), TextParser.plainLength(fullText));

    BubbleGeometry bubble = resolveBubbleGeometry(
        dialogue, state, scenario, width, height, speaker, spans, revealedChars);
    drawBubblePanel(bubble);

    double pad = uiLayout.bubbleTextPadding();
    double textX = bubble.x() + pad;
    double contentWidth = Math.max(80.0, bubble.width() - pad * 2);
    double y = bubble.y() + pad + nameFont.getSize();
    if (speaker != null && !speaker.isBlank()) {
      gc.setFill(resolveSpeakerColor(dialogue, bubbleSpeakerTextFillColor));
      gc.setFont(nameFont);
      gc.fillText(speaker, textX, y);
      y += nameFont.getSize() * 0.95;
    }
    drawStyledText(spans, revealedChars, textX, y, contentWidth, 0.0, dialogueFont, bubbleTextFillColor);

    if (revealedChars >= TextParser.plainLength(fullText) && state.isWaitingForInput()) {
      double indicatorY = bubble.tailOnTop() ? bubble.y() + bubble.height() - 14.0 : bubble.y() + bubble.height() - 18.0;
      drawContinueIndicator(bubble.x() + bubble.width() - 24.0, indicatorY);
    }
  }

  private BubbleGeometry resolveBubbleGeometry(
      DialogueLine dialogue,
      VnState state,
      VnScenario scenario,
      double width,
      double height,
      String speaker,
      List<TextSpan> spans,
      int revealedChars
  ) {
    double maxWidth = clamp(width * uiLayout.bubbleWidthFactor(), 180.0, Math.min(width - 32.0, 620.0));
    double pad = uiLayout.bubbleTextPadding();
    double contentWidth = Math.max(120.0, maxWidth - pad * 2);
    double textHeight = measureStyledTextHeight(spans, revealedChars, contentWidth, dialogueFont);
    double speakerHeight = (speaker == null || speaker.isBlank()) ? 0.0 : nameFont.getSize() * 1.15;
    double bubbleH = Math.max(uiLayout.bubbleMinHeight(), pad * 2 + textHeight + speakerHeight);
    double tailSize = uiLayout.bubbleTailSize();

    double anchorX = width * 0.5;
    double anchorY = height * 0.58;
    String characterId = dialogue.getCharacterId();
    VnCharacter dialogueCharacter = scenario == null || characterId == null
        ? null
        : scenario.getCharacter(characterId);
    double dialogueCharacterScale = characterScale(dialogueCharacter);
    if (characterId != null && !characterId.isBlank()) {
      BubbleAnchor pref = state.getBubbleAnchorPreference(characterId);
      if (pref != BubbleAnchor.AUTO) {
        anchorX = switch (pref) {
          case LEFT -> width * 0.25;
          case CENTER -> width * 0.50;
          case RIGHT -> width * 0.75;
          default -> anchorX;
        };
      } else {
        CharacterPosition position = state.getCharacterPosition(characterId);
        VnState.CharacterVisual visual = null;
        VnState.TimelineDisplacement displacement = state.getTimelineDisplacement(characterId);
        if (position == null) {
          VnState.DetachedCharacterSlot detached = state.getDetachedCharacter(characterId);
          if (detached != null) {
            position = detached.getBasePosition();
            visual = detached.getVisual();
          }
        }
        if (position == null) position = state.getCharacterDefinedPosition(characterId);
        if (position == null) position = dialogue.getPosition();
        if (position != null) {
          anchorX = width * position.getXFraction();
          double spriteHeight = height * characterHeightFactor * dialogueCharacterScale;
          double topY = position.computeScreenY(height, spriteHeight, characterBaselineY);
          if (visual == null) visual = state.getCharacterVisual(position);
          if (visual != null) {
            anchorX += visual.getOffsetX();
            topY += visual.getOffsetY();
          }
          if (displacement != null) {
            if (displacement.hasX() && (visual == null || Math.abs(visual.getOffsetX()) < 1e-6)) {
              anchorX += displacement.getX();
            }
            if (displacement.hasY() && (visual == null || Math.abs(visual.getOffsetY()) < 1e-6)) {
              topY += displacement.getY();
            }
          }
          anchorY = topY + spriteHeight * 0.22;
        }
      }
      anchorX += state.getBubbleOffsetXPreference(characterId);
      anchorY += state.getBubbleOffsetYPreference(characterId);
    } else if (dialogue.getPosition() != null) {
      anchorX = width * dialogue.getPosition().getXFraction();
      double spriteHeight = height * characterHeightFactor * dialogueCharacterScale;
      anchorY = dialogue.getPosition().computeScreenY(height, spriteHeight, characterBaselineY) + spriteHeight * 0.22;
    }

    double bubbleX = clamp(anchorX - maxWidth / 2.0, 16.0, Math.max(16.0, width - maxWidth - 16.0));
    double bubbleY = anchorY - bubbleH - tailSize - uiLayout.bubbleYOffset();
    boolean tailOnTop = false;
    if (bubbleY < 16.0) {
      bubbleY = anchorY + tailSize + Math.max(8.0, uiLayout.bubbleYOffset() * 0.35);
      tailOnTop = true;
    }
    bubbleY = clamp(bubbleY, 16.0, Math.max(16.0, height - bubbleH - 16.0));
    return new BubbleGeometry(bubbleX, bubbleY, maxWidth, bubbleH, anchorX, anchorY, tailSize, tailOnTop);
  }

  private List<DialogueRenderEntry> collectNvlEntries(VnState state, DialogueLine currentDialogue) {
    List<DialogueRenderEntry> entries = new ArrayList<>();
    if (state == null) return entries;
    List<com.jvn.core.vn.VnHistory.HistoryEntry> historyEntries = state.getHistory().getEntries();
    int maxEntries = Math.max(1, uiLayout.nvlMaxEntries());
    int start = Math.max(0, historyEntries.size() - maxEntries);
    for (int i = start; i < historyEntries.size(); i++) {
      var entry = historyEntries.get(i);
      String text = entry.getText() == null ? "" : entry.getText();
      entries.add(new DialogueRenderEntry(
          entry.getSpeaker(),
          entry.getSpeakerColor(),
          text,
          TextParser.plainLength(text)));
    }
    if (currentDialogue != null && !entries.isEmpty()) {
      String text = resolveRuntimeText(currentDialogue.getText());
      entries.set(entries.size() - 1, new DialogueRenderEntry(
          resolveRuntimeText(currentDialogue.getSpeakerName()),
          currentDialogue.getSpeakerColor(),
          text,
          Math.min(state.getTextRevealProgress(), TextParser.plainLength(text))));
    }
    return entries;
  }

  private Color resolveSpeakerColor(DialogueLine dialogue, Color fallback) {
    if (dialogue == null) return fallback;
    return parseColor(dialogue.getSpeakerColor(), fallback);
  }

  private void renderTextBoxButtons(TextBoxGeometry textBox, double viewportWidth, double viewportHeight, int hoveredButtonIndex, VnState state) {
    if (!shouldRenderTextBoxButtons(state)) return;
    if (textBoxButtons == null || textBoxButtons.isEmpty()) return;
    for (int i = 0; i < textBoxButtons.size(); i++) {
      VnUiActionButtonSpec button = textBoxButtons.get(i);
      if (button == null) continue;
      ButtonGeometry geometry = computeButtonGeometry(button, textBox, viewportWidth, viewportHeight);
      boolean hovered = i == hoveredButtonIndex;
      boolean enabled = button.enabled();

      Image asset = firstNonBlank(button.assetPath(), null) != null ? loadImage(button.assetPath()) : null;
      Image hoverAsset = firstNonBlank(button.hoverAssetPath(), null) != null ? loadImage(button.hoverAssetPath()) : asset;
      Image disabledAsset = firstNonBlank(button.disabledAssetPath(), null) != null ? loadImage(button.disabledAssetPath()) : asset;
      Image drawAsset = !enabled
          ? firstNonNull(disabledAsset, asset)
          : (hovered ? firstNonNull(hoverAsset, asset) : asset);
      boolean imageBacked = drawAsset != null;
      List<BoundsPointCodec.Point> buttonPolygon = parseBoundsPoints(button.boundsPoints());
      boolean clipButton = hasPolygon(buttonPolygon);
      if (imageBacked) {
        if (clipButton) {
          gc.save();
          clipToLocalPolygon(buttonPolygon, geometry.x(), geometry.y(), geometry.width(), geometry.height());
        }
        if (!enabled) gc.setGlobalAlpha(0.55);
        gc.drawImage(drawAsset, geometry.x(), geometry.y(), geometry.width(), geometry.height());
        gc.setGlobalAlpha(1.0);
        if (clipButton) gc.restore();
      } else {
        Color fill = !enabled
            ? Color.rgb(38, 40, 48, 0.7)
            : (hovered ? Color.rgb(90, 120, 180, 0.8) : Color.rgb(32, 36, 46, 0.78));
        if (clipButton) {
          gc.save();
          clipToLocalPolygon(buttonPolygon, geometry.x(), geometry.y(), geometry.width(), geometry.height());
          gc.setFill(fill);
          gc.fillRect(geometry.x(), geometry.y(), geometry.width(), geometry.height());
          gc.restore();
        } else {
          gc.setFill(fill);
          gc.fillRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 8, 8);
        }
        gc.setStroke(!enabled
            ? Color.rgb(120, 125, 136, 0.75)
            : (hovered ? Color.rgb(170, 210, 255, 0.95) : Color.rgb(120, 135, 170, 0.82)));
        gc.setLineWidth(hovered ? 2.0 : 1.2);
        if (clipButton) {
          strokeLocalPolygon(buttonPolygon, geometry.x(), geometry.y(), geometry.width(), geometry.height());
        } else {
          gc.strokeRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 8, 8);
        }

        gc.setFill(!enabled ? Color.rgb(172, 176, 188, 0.75) : (hovered ? Color.rgb(245, 252, 255) : Color.rgb(225, 232, 246)));
        gc.setFont(Font.font(choiceFont.getFamily(), FontWeight.BOLD, clamp(geometry.height() * 0.42, 10, 18)));
        String label = button.label() == null || button.label().isBlank() ? button.id() : button.label();
        double textW = computeTextWidth(label, gc.getFont());
        double textX = geometry.x() + Math.max(8, (geometry.width() - textW) / 2.0);
        double textY = geometry.y() + geometry.height() * 0.64;
        gc.fillText(label, textX, textY);
      }
    }
  }

  private record StyledGlyph(
      char value,
      Font font,
      Color color,
      TextEffect effect,
      int glyphIndex,
      double width,
      boolean syntheticItalic
  ) {}

  private record StyledLine(List<StyledGlyph> glyphs, double width) {}

  private void drawStyledText(List<TextSpan> spans, int revealedChars, double startX, double startY, double maxWidth, double xAlign) {
    drawStyledText(spans, revealedChars, startX, startY, maxWidth, xAlign, dialogueFont, dialogueTextFillColor);
  }

  private void drawStyledText(
      List<TextSpan> spans,
      int revealedChars,
      double startX,
      double startY,
      double maxWidth,
      double xAlign,
      Font baseFont,
      Color defaultTextColor
  ) {
    List<StyledLine> lines = layoutStyledLines(spans, revealedChars, maxWidth, baseFont, defaultTextColor);
    drawStyledLines(lines, startX, startY, maxWidth, xAlign, baseFont, defaultTextColor);
  }

  private List<StyledLine> layoutStyledLines(
      List<TextSpan> spans,
      int revealedChars,
      double maxWidth,
      Font baseFont,
      Color defaultTextColor
  ) {
    gc.setFont(baseFont);
    List<StyledLine> lines = new ArrayList<>();
    List<StyledGlyph> currentLine = new ArrayList<>();
    double currentLineWidth = 0.0;
    int charCount = 0;
    int glyphIndex = 0;

    for (TextSpan span : spans) {
      String text = span.getText();
      int spanLen = text.length();
      int visibleChars = 0;
      if (charCount < revealedChars) {
        visibleChars = Math.min(spanLen, revealedChars - charCount);
      }

      if (visibleChars > 0) {
        Color spanColor = span.hasColor() ? parseColorHex(span.getColorHex()) : defaultTextColor;
        Font effectFont = baseFont;
        boolean syntheticItalic = false;
        if (span.getEffect() == TextEffect.BOLD) {
          effectFont = Font.font(baseFont.getFamily(), FontWeight.BOLD, baseFont.getSize());
        } else if (span.getEffect() == TextEffect.ITALIC) {
          ItalicFontSupport.ResolvedItalic resolvedItalic = ItalicFontSupport.resolve(
              baseFont,
              fontWeightOf(baseFont, FontWeight.NORMAL)
          );
          effectFont = resolvedItalic.font();
          syntheticItalic = resolvedItalic.synthetic();
        }

        for (int i = 0; i < visibleChars; i++) {
          char c = text.charAt(i);
          if (c == '\n') {
            lines.add(new StyledLine(List.copyOf(currentLine), currentLineWidth));
            currentLine.clear();
            currentLineWidth = 0.0;
            continue;
          }

          double charWidth = computeTextWidth(String.valueOf(c), effectFont);
          if (!currentLine.isEmpty() && currentLineWidth + charWidth > maxWidth) {
            lines.add(new StyledLine(List.copyOf(currentLine), currentLineWidth));
            currentLine.clear();
            currentLineWidth = 0.0;
          }

          currentLine.add(new StyledGlyph(
              c,
              effectFont,
              spanColor,
              span.getEffect(),
              glyphIndex,
              charWidth,
              syntheticItalic
          ));
          currentLineWidth += charWidth;
          glyphIndex++;
        }
      }

      charCount += spanLen;
    }

    if (!currentLine.isEmpty() || lines.isEmpty()) {
      lines.add(new StyledLine(List.copyOf(currentLine), currentLineWidth));
    }
    return lines;
  }

  private void drawStyledLines(
      List<StyledLine> lines,
      double startX,
      double startY,
      double maxWidth,
      double xAlign,
      Font baseFont,
      Color defaultTextColor
  ) {
    double lineHeight = Math.max(22.0, baseFont.getSize() * 1.15);
    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      StyledLine line = lines.get(lineIndex);
      double x = resolveAlignedTextX(startX, maxWidth, line.width(), xAlign);
      double y = startY + lineIndex * lineHeight;

      for (StyledGlyph glyph : line.glyphs()) {
        gc.setFill(glyph.color());
        gc.setFont(glyph.font());

        double offsetX = 0.0;
        double offsetY = 0.0;
        double effectPhase = (animationTime * 0.01) + glyph.glyphIndex() * 0.3;
        switch (glyph.effect()) {
          case SHAKE -> {
            offsetX = (Math.random() - 0.5) * 3;
            offsetY = (Math.random() - 0.5) * 3;
          }
          case WAVE -> offsetY = Math.sin(effectPhase) * 3;
          case BOUNCE -> offsetY = Math.abs(Math.sin(effectPhase * 2)) * -4;
          case RAINBOW -> gc.setFill(Color.hsb((effectPhase * 50) % 360, 0.8, 1.0));
          default -> {}
        }

        double drawX = x + offsetX;
        double drawY = y + offsetY;
        if (glyph.syntheticItalic()) {
          gc.save();
          gc.transform(
              1.0,
              0.0,
              ItalicFontSupport.SYNTHETIC_SHEAR,
              1.0,
              -ItalicFontSupport.SYNTHETIC_SHEAR * drawY,
              0.0
          );
          gc.fillText(String.valueOf(glyph.value()), drawX, drawY);
          gc.restore();
        } else {
          gc.fillText(String.valueOf(glyph.value()), drawX, drawY);
        }
        x += glyph.width();
      }
    }

    gc.setFont(baseFont);
    gc.setFill(defaultTextColor);
  }

  private void drawContinueIndicatorAfterText(
      List<StyledLine> lines,
      double startX,
      double startY,
      double maxWidth,
      double xAlign,
      Font baseFont,
      double fallbackX,
      double fallbackY
  ) {
    if (continueIndicatorImage == null || lines == null || lines.isEmpty()) {
      drawContinueIndicator(fallbackX, fallbackY);
      return;
    }

    StyledLine lastLine = lines.get(lines.size() - 1);
    double lineHeight = Math.max(22.0, baseFont.getSize() * 1.15);
    double lineX = resolveAlignedTextX(startX, maxWidth, lastLine.width(), xAlign);
    double imageWidth = continueIndicatorImage.getWidth();
    double imageHeight = continueIndicatorImage.getHeight();
    double x = lineX + lastLine.width() + 4.0;
    double y = startY + (lines.size() - 1) * lineHeight - imageHeight + baseFont.getSize() * 0.42;
    if (x + imageWidth > startX + maxWidth) {
      x = startX + maxWidth - imageWidth;
      y += lineHeight;
    }
    gc.drawImage(continueIndicatorImage, x, y, imageWidth, imageHeight);
  }

  private double measureStyledTextHeight(List<TextSpan> spans, int revealedChars, double maxWidth, Font baseFont) {
    List<StyledLine> lines = layoutStyledLines(spans, revealedChars, maxWidth, baseFont, dialogueTextFillColor);
    double lineHeight = Math.max(22.0, baseFont.getSize() * 1.15);
    return Math.max(lineHeight, lines.size() * lineHeight);
  }

  private Color parseColorHex(String hex) {
    if (hex == null || hex.isEmpty()) return TEXT_COLOR;
    try {
      String h = hex.startsWith("#") ? hex.substring(1) : hex;
      if (h.length() == 6) {
        int r = Integer.parseInt(h.substring(0, 2), 16);
        int g = Integer.parseInt(h.substring(2, 4), 16);
        int b = Integer.parseInt(h.substring(4, 6), 16);
        return Color.rgb(r, g, b);
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    return TEXT_COLOR;
  }

  private void applyUiStyle(VnUiStyleSpec style) {
    VnUiStyleSpec resolved = style == null ? VnUiStyleSpec.defaults() : style;
    textBoxImage = loadImage(resolved.textBoxAssetPath());
    narrationTextBoxImage = loadImage(resolved.textBoxNarrationAssetPath());
    nameBoxImage = loadImage(resolved.nameBoxAssetPath());
    nvlPanelImage = loadImage(resolved.nvlPanelAssetPath());
    bubbleImage = loadImage(resolved.bubbleAssetPath());
    continueIndicatorImage = loadImage("assets/ui/dialogue/ctc_marker.png");
    textBoxFillColor = parseColor(resolved.textBoxColor(), TEXTBOX_COLOR);
    nameBoxFillColor = parseColor(resolved.nameBoxColor(), NAME_BOX_COLOR);
    nameTextFillColor = parseColor(resolved.nameTextColor(), Color.web("#FFD78A"));
    dialogueTextFillColor = parseColor(resolved.dialogueTextColor(), TEXT_COLOR);
    nvlPanelFillColor = parseColor(resolved.nvlPanelColor(), Color.web("#08111acc"));
    nvlSpeakerTextFillColor = parseColor(resolved.nvlSpeakerTextColor(), Color.web("#F7D89A"));
    nvlTextFillColor = parseColor(resolved.nvlTextColor(), dialogueTextFillColor);
    bubbleFillColor = parseColor(resolved.bubbleColor(), Color.web("#152238ee"));
    bubbleBorderFillColor = parseColor(resolved.bubbleBorderColor(), Color.web("#A9BCD9"));
    bubbleSpeakerTextFillColor = parseColor(resolved.bubbleSpeakerTextColor(), Color.web("#FFD78A"));
    bubbleTextFillColor = parseColor(resolved.bubbleTextColor(), dialogueTextFillColor);
    textBoxAssetOverlayOpacity = clamp(
        resolved.textBoxOpacity() == null ? 0.88 : resolved.textBoxOpacity(),
        0.0,
        1.0
    );
    nvlPanelOpacity = clamp(
        resolved.nvlPanelOpacity() == null ? 0.84 : resolved.nvlPanelOpacity(),
        0.0,
        1.0
    );
    bubbleOpacity = clamp(
        resolved.bubbleOpacity() == null ? 0.96 : resolved.bubbleOpacity(),
        0.0,
        1.0
    );
    textBoxBoundsPolygon = parseBoundsPoints(resolved.textBoxBoundsPoints());
    nameBoxBoundsPolygon = parseBoundsPoints(resolved.nameBoxBoundsPoints());
    dialogueTextBoundsPolygon = parseBoundsPoints(resolved.dialogueTextBoundsPoints());
    choiceButtonImage = loadImage(resolved.choiceButtonAssetPath());
    choiceButtonHoverImage = loadImage(firstNonBlank(
        resolved.choiceButtonHoverAssetPath(),
        resolved.choiceButtonSelectedAssetPath()));
    choiceButtonDisabledImage = loadImage(resolved.choiceButtonDisabledAssetPath());
    choiceButtonBoundsPolygon = parseBoundsPoints(resolved.choiceButtonBoundsPoints());

    choiceBgColor = parseColor(resolved.choiceBackgroundColor(), CHOICE_BG_COLOR);
    choiceHoverColor = parseColor(
        firstNonBlank(resolved.choiceHoverColor(), resolved.choiceSelectedColor()),
        CHOICE_HOVER_COLOR
    );
    choiceDisabledColor = parseColor(resolved.choiceDisabledColor(), CHOICE_DISABLED_COLOR);

    choiceTextColor = parseColor(resolved.choiceTextColor(), TEXT_COLOR);
    choiceHoverTextColor = parseColor(
        firstNonBlank(resolved.choiceHoverTextColor(), resolved.choiceSelectedTextColor()),
        choiceTextColor
    );
    choiceDisabledTextColor = parseColor(resolved.choiceDisabledTextColor(), TEXT_COLOR_DISABLED);

    choiceBorderColor = parseColor(resolved.choiceBorderColor(), TEXT_COLOR);
    choiceHoverBorderColor = parseColor(
        firstNonBlank(resolved.choiceHoverBorderColor(), resolved.choiceSelectedBorderColor()),
        choiceBorderColor
    );
    choiceDisabledBorderColor = parseColor(resolved.choiceDisabledBorderColor(), CHOICE_DISABLED_BORDER_COLOR);

    choiceBorderWidth = clamp(resolved.choiceBorderWidth(), 0.0, 12.0);
    choiceCornerRadius = clamp(resolved.choiceCornerRadius(), 0.0, 96.0);
    choiceTextBaselineOffset = clamp(resolved.choiceTextBaselineOffset(), -120.0, 120.0);
    bubbleCornerRadius = clamp(resolved.bubbleCornerRadius(), 0.0, 96.0);
    bubbleBorderWidth = clamp(resolved.bubbleBorderWidth(), 0.0, 12.0);
    nameTextXAlign = clamp(resolved.nameTextXAlign() == null ? 0.0 : resolved.nameTextXAlign(), 0.0, 1.0);
    dialogueTextXAlign = clamp(resolved.dialogueTextXAlign() == null ? 0.0 : resolved.dialogueTextXAlign(), 0.0, 1.0);
    choiceTextXAlign = clamp(resolved.choiceTextXAlign() == null ? 0.0 : resolved.choiceTextXAlign(), 0.0, 1.0);

    // Apply font settings from style spec (font weight from spec, with sensible defaults)
    String nameFontFamily = resolved.nameTextFontFamily() != null ? resolved.nameTextFontFamily() : DEFAULT_FONT_FAMILY;
    int nameFontSize = resolved.nameTextFontSize() != null ? resolved.nameTextFontSize() : DEFAULT_NAME_FONT_SIZE;
    FontWeight nameFontWeight = parseFontWeight(resolved.nameTextFontWeight(), FontWeight.BOLD);
    this.nameFont = ProjectFontResolver.resolve(projectRoot, nameFontFamily, nameFontWeight, nameFontSize, DEFAULT_FONT_FAMILY);

    String dialogueFontFamily = resolved.dialogueTextFontFamily() != null ? resolved.dialogueTextFontFamily() : DEFAULT_FONT_FAMILY;
    int dialogueFontSize = resolved.dialogueTextFontSize() != null ? resolved.dialogueTextFontSize() : DEFAULT_DIALOGUE_FONT_SIZE;
    FontWeight dialogueFontWeight = parseFontWeight(resolved.dialogueTextFontWeight(), FontWeight.NORMAL);
    this.dialogueFont = ProjectFontResolver.resolve(projectRoot, dialogueFontFamily, dialogueFontWeight, dialogueFontSize, DEFAULT_FONT_FAMILY);

    String choiceFontFamily = resolved.choiceFontFamily() != null ? resolved.choiceFontFamily() : DEFAULT_FONT_FAMILY;
    int choiceFontSize = resolved.choiceFontSize() != null ? resolved.choiceFontSize() : DEFAULT_CHOICE_FONT_SIZE;
    FontWeight choiceFontWeightVal = parseFontWeight(resolved.choiceFontWeight(), FontWeight.NORMAL);
    this.choiceFont = ProjectFontResolver.resolve(projectRoot, choiceFontFamily, choiceFontWeightVal, choiceFontSize, DEFAULT_FONT_FAMILY);

    // Name box opacity
    this.nameBoxRenderOpacity = clamp(
        resolved.nameBoxOpacity() == null ? 1.0 : resolved.nameBoxOpacity(),
        0.0, 1.0
    );

    // Character framing: lets projects opt into waist-up portraits.
    this.styleCharacterHeightFactor = clamp(
        resolved.characterHeightFactor() == null ? DEFAULT_CHARACTER_HEIGHT_FACTOR : resolved.characterHeightFactor(),
        0.1, 3.0
    );
    this.styleCharacterBaselineY = clamp(
        resolved.characterBaselineY() == null ? DEFAULT_CHARACTER_BASELINE_Y : resolved.characterBaselineY(),
        -0.5, 2.0
    );
    this.characterHeightFactor = styleCharacterHeightFactor;
    this.characterBaselineY = styleCharacterBaselineY;

    // Apply accessibility theme overrides on top of the resolved style.
    if (accessibilityTheme != null && accessibilityTheme.isActive()) {
      applyAccessibilityThemeOverrides(accessibilityTheme);
    }
    applyUiFontScale();
  }

  private void syncAccessibilitySettings(VnState state) {
    VnSettings settings = state == null ? null : state.getSettings();
    String requestedTheme = normalizeAccessibilityThemeName(
        settings == null ? "none" : settings.getAccessibilityTheme());
    double requestedScale = settings == null ? 1.0 : settings.getUiFontScale();
    boolean requestedTts = settings != null && settings.isTextToSpeechEnabled();

    boolean themeChanged = !appliedAccessibilityThemeName.equals(requestedTheme);
    boolean scaleChanged = Math.abs(appliedUiFontScale - requestedScale) > 0.0001;
    if (themeChanged || scaleChanged) {
      accessibilityTheme = AccessibilityThemeLoader.load(requestedTheme);
      appliedAccessibilityThemeName = requestedTheme;
      appliedUiFontScale = requestedScale;
      applyUiStyle(uiStyle);
    }
    if (textToSpeechEnabled && !requestedTts) {
      tts.stop();
      lastTtsNodeId = null;
    }
    textToSpeechEnabled = requestedTts;
  }

  private void applyUiFontScale() {
    if (Math.abs(appliedUiFontScale - 1.0) < 0.0001) return;
    nameFont = Font.font(nameFont.getFamily(), fontWeightOf(nameFont, FontWeight.BOLD),
        nameFont.getSize() * appliedUiFontScale);
    dialogueFont = Font.font(dialogueFont.getFamily(), fontWeightOf(dialogueFont, FontWeight.NORMAL),
        dialogueFont.getSize() * appliedUiFontScale);
    choiceFont = Font.font(choiceFont.getFamily(), fontWeightOf(choiceFont, FontWeight.NORMAL),
        choiceFont.getSize() * appliedUiFontScale);
  }

  private static String normalizeAccessibilityThemeName(String themeName) {
    return themeName == null || themeName.isBlank()
        ? "none"
        : themeName.trim().toLowerCase(Locale.ROOT);
  }

  private static FontWeight fontWeightOf(Font font, FontWeight fallback) {
    if (font == null || font.getStyle() == null) return fallback;
    String style = font.getStyle().toLowerCase(Locale.ROOT);
    if (style.contains("black")) return FontWeight.BLACK;
    if (style.contains("extra bold") || style.contains("ultra bold")) return FontWeight.EXTRA_BOLD;
    if (style.contains("semi bold") || style.contains("demi bold")) return FontWeight.SEMI_BOLD;
    if (style.contains("bold")) return FontWeight.BOLD;
    if (style.contains("medium")) return FontWeight.MEDIUM;
    if (style.contains("light")) return FontWeight.LIGHT;
    if (style.contains("thin")) return FontWeight.THIN;
    return fallback;
  }

  private void applyAccessibilityThemeOverrides(AccessibilityThemeLoader theme) {
    // Colours
    dialogueTextFillColor  = parseColor(theme.dialogueTextColor(null),  dialogueTextFillColor);
    textBoxFillColor       = parseColor(theme.dialogueTextboxColor(null), textBoxFillColor);
    textBoxAssetOverlayOpacity = theme.dialogueTextboxOpacity(textBoxAssetOverlayOpacity);
    nameBoxFillColor       = parseColor(theme.nameBoxColor(null),        nameBoxFillColor);
    nameBoxRenderOpacity   = theme.nameBoxOpacity(nameBoxRenderOpacity);
    nameTextFillColor      = parseColor(theme.nameTextColor(null),       nameTextFillColor);
    choiceBgColor          = parseColor(theme.choiceBackgroundColor(null), choiceBgColor);
    choiceTextColor        = parseColor(theme.choiceTextColor(null),     choiceTextColor);
    choiceHoverColor       = parseColor(theme.choiceHoverColor(null),    choiceHoverColor);
    choiceHoverTextColor   = parseColor(theme.choiceHoverTextColor(null), choiceHoverTextColor);
    choiceBorderColor      = parseColor(theme.choiceBorderColor(null),   choiceBorderColor);
    choiceHoverBorderColor = parseColor(theme.choiceHoverBorderColor(null), choiceHoverBorderColor);
    choiceBorderWidth      = theme.choiceBorderWidth(choiceBorderWidth);
    choiceCornerRadius     = theme.choiceCornerRadius(choiceCornerRadius);
    nvlPanelFillColor      = parseColor(theme.nvlPanelColor(null),       nvlPanelFillColor);
    nvlPanelOpacity        = theme.nvlPanelOpacity(nvlPanelOpacity);
    nvlTextFillColor       = parseColor(theme.nvlTextColor(null),        nvlTextFillColor);
    nvlSpeakerTextFillColor = parseColor(theme.nvlSpeakerTextColor(null), nvlSpeakerTextFillColor);
    bubbleFillColor        = parseColor(theme.bubbleColor(null),         bubbleFillColor);
    bubbleOpacity          = theme.bubbleOpacity(bubbleOpacity);
    bubbleTextFillColor    = parseColor(theme.bubbleTextColor(null),     bubbleTextFillColor);
    bubbleSpeakerTextFillColor = parseColor(theme.bubbleSpeakerTextColor(null), bubbleSpeakerTextFillColor);
    bubbleBorderFillColor  = parseColor(theme.bubbleBorderColor(null),   bubbleBorderFillColor);
    bubbleBorderWidth      = theme.bubbleBorderWidth(bubbleBorderWidth);

    // Fonts — override family/weight if theme specifies them
    String themeNameFamily = theme.nameTextFontFamily(null);
    if (themeNameFamily != null) {
      FontWeight w = parseFontWeight(theme.nameTextFontWeight(null), FontWeight.BOLD);
      int sz = nameFont != null ? (int) nameFont.getSize() : DEFAULT_NAME_FONT_SIZE;
      this.nameFont = ProjectFontResolver.resolve(projectRoot, themeNameFamily, w, sz, DEFAULT_FONT_FAMILY);
    }
    String themeDialogueFamily = theme.dialogueTextFontFamily(null);
    if (themeDialogueFamily != null) {
      FontWeight w = parseFontWeight(theme.dialogueTextFontWeight(null), FontWeight.NORMAL);
      int sz = dialogueFont != null ? (int) dialogueFont.getSize() : DEFAULT_DIALOGUE_FONT_SIZE;
      this.dialogueFont = ProjectFontResolver.resolve(projectRoot, themeDialogueFamily, w, sz, DEFAULT_FONT_FAMILY);
    }
    String themeChoiceFamily = theme.choiceFontFamily(null);
    if (themeChoiceFamily != null) {
      FontWeight w = parseFontWeight(theme.choiceFontWeight(null), FontWeight.NORMAL);
      int sz = choiceFont != null ? (int) choiceFont.getSize() : DEFAULT_CHOICE_FONT_SIZE;
      this.choiceFont = ProjectFontResolver.resolve(projectRoot, themeChoiceFamily, w, sz, DEFAULT_FONT_FAMILY);
    }
  }

  private void applyRuntimeCharacterFramingOverrides(VnState state) {
    if (state == null) {
      characterHeightFactor = styleCharacterHeightFactor;
      characterBaselineY = styleCharacterBaselineY;
      return;
    }
    Double heightOverride = readDoubleVariable(state, VAR_CHARACTER_HEIGHT_FACTOR);
    Double baselineOverride = readDoubleVariable(state, VAR_CHARACTER_BASELINE_Y);
    characterHeightFactor = clamp(
        heightOverride == null ? styleCharacterHeightFactor : heightOverride,
        0.1,
        3.0
    );
    characterBaselineY = clamp(
        baselineOverride == null ? styleCharacterBaselineY : baselineOverride,
        -0.5,
        2.0
    );
  }

  private Double readDoubleVariable(VnState state, String key) {
    if (state == null || key == null || key.isBlank()) return null;
    Object value = state.getVariables().get(key);
    if (value == null) return null;
    if (value instanceof Number n) return n.doubleValue();
    if (value instanceof String s) {
      try {
        return Double.parseDouble(s.trim());
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        return null;
      }
    }
    return null;
  }

  static double characterScale(VnCharacter character) {
    if (character == null) return 1.0;
    return Math.max(0.1, Math.min(3.0, character.getScale()));
  }

  private String readStringVariable(VnState state, String key) {
    if (state == null || key == null || key.isBlank()) return null;
    Object value = state.getVariables().get(key);
    if (value == null) return null;
    String text = value.toString().trim();
    return text.isEmpty() ? null : text;
  }

  private Boolean readBooleanVariable(VnState state, String key) {
    if (state == null || key == null || key.isBlank()) return null;
    Object value = state.getVariables().get(key);
    if (value == null) return null;
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.doubleValue() != 0.0;
    if (value instanceof String s) {
      String text = s.trim();
      if (text.isEmpty()) return null;
      return VnAudioVisualizerConfig.isTruthy(text);
    }
    return null;
  }

  private boolean shouldUseDefaultDialogueUi(VnState state) {
    return disablesCustomDialogueUi(readStringVariable(state, VAR_DIALOGUE_UI));
  }

  private boolean shouldUseDefaultDialogueStyle(VnState state) {
    return shouldUseDefaultDialogueUi(state)
        || disablesCustomDialogueUi(readStringVariable(state, VAR_DIALOGUE_STYLE));
  }

  private boolean shouldUseTextBoxAsset(VnState state) {
    if (shouldUseDefaultDialogueUi(state)) return false;
    Boolean enabled = readBooleanVariable(state, VAR_TEXT_BOX_ASSET_ENABLED);
    if (enabled != null) return enabled;

    String mode = readStringVariable(state, VAR_TEXT_BOX_ASSET);
    if (mode == null) return true;
    String normalized = mode.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "default", "builtin", "built-in", "solid", "fill", "none", "off", "false", "0", "no" -> false;
      default -> true;
    };
  }

  private boolean shouldRenderTextBoxButtons(VnState state) {
    if (shouldUseDefaultDialogueUi(state)) return false;
    Boolean enabled = readBooleanVariable(state, VAR_TEXT_BOX_BUTTONS_ENABLED);
    if (enabled != null) return enabled;
    String mode = readStringVariable(state, VAR_TEXT_BOX_BUTTONS);
    if (mode == null) return true;
    return !disablesCustomDialogueUi(mode);
  }

  private boolean disablesCustomDialogueUi(String raw) {
    if (raw == null || raw.isBlank()) return false;
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "default", "builtin", "built-in", "solid", "fill", "plain", "none", "off", "false", "0", "no" -> true;
      default -> false;
    };
  }

  private boolean isAudioVisualizerEnabled() {
    return currentState != null && VnAudioVisualizerConfig.isTruthy(currentState.getVariables().get(VnAudioVisualizerConfig.VAR_ENABLED));
  }

  private int resolveAudioVisualizerBarCount() {
    Double override = readDoubleVariable(currentState, VnAudioVisualizerConfig.VAR_BARS);
    if (override == null) return VnAudioVisualizerConfig.DEFAULT_BARS;
    return VnAudioVisualizerConfig.clampBars((int) Math.round(override));
  }

  private AudioVisualizerSettings resolveAudioVisualizerSettings() {
    String style = VnAudioVisualizerConfig.normalizeStyle(readStringVariable(currentState, VnAudioVisualizerConfig.VAR_STYLE));
    String colorToken = readStringVariable(currentState, VnAudioVisualizerConfig.VAR_COLOR);
    String accentToken = readStringVariable(currentState, VnAudioVisualizerConfig.VAR_ACCENT);
    Double alphaValue = readDoubleVariable(currentState, VnAudioVisualizerConfig.VAR_ALPHA);
    Double heightValue = readDoubleVariable(currentState, VnAudioVisualizerConfig.VAR_HEIGHT);
    Double zValue = readDoubleVariable(currentState, VnAudioVisualizerConfig.VAR_Z);
    Boolean glowValue = readBooleanVariable(currentState, VnAudioVisualizerConfig.VAR_GLOW);
    return new AudioVisualizerSettings(
        isAudioVisualizerEnabled(),
        resolveAudioVisualizerBarCount(),
        style,
        colorToken,
        accentToken,
        alphaValue == null ? VnAudioVisualizerConfig.DEFAULT_ALPHA : VnAudioVisualizerConfig.clampAlpha(alphaValue),
        glowValue == null || glowValue,
        heightValue == null ? VnAudioVisualizerConfig.DEFAULT_HEIGHT : VnAudioVisualizerConfig.clampHeight(heightValue),
        zValue == null ? VnAudioVisualizerConfig.DEFAULT_Z : (int) Math.round(zValue));
  }

  private Color parseColor(String raw, Color fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Color.web(raw.trim());
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return fallback;
    }
  }

  private static FontWeight parseFontWeight(String raw, FontWeight def) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return FontWeight.valueOf(raw.trim().toUpperCase());
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return def;
    }
  }

  private List<BoundsPointCodec.Point> parseBoundsPoints(String raw) {
    List<BoundsPointCodec.Point> parsed = BoundsPointCodec.parse(raw);
    return parsed.size() >= 3 ? parsed : List.of();
  }

  private boolean hasPolygon(List<BoundsPointCodec.Point> points) {
    return points != null && points.size() >= 3;
  }

  private void clipToLocalPolygon(List<BoundsPointCodec.Point> localPoints, double rectX, double rectY, double rectW, double rectH) {
    if (!hasPolygon(localPoints)) return;
    gc.beginPath();
    for (int i = 0; i < localPoints.size(); i++) {
      BoundsPointCodec.Point point = localPoints.get(i);
      double x = rectX + rectW * point.x();
      double y = rectY + rectH * point.y();
      if (i == 0) gc.moveTo(x, y);
      else gc.lineTo(x, y);
    }
    gc.closePath();
    gc.clip();
  }

  private void strokeLocalPolygon(List<BoundsPointCodec.Point> localPoints, double rectX, double rectY, double rectW, double rectH) {
    if (!hasPolygon(localPoints)) return;
    gc.beginPath();
    for (int i = 0; i < localPoints.size(); i++) {
      BoundsPointCodec.Point point = localPoints.get(i);
      double x = rectX + rectW * point.x();
      double y = rectY + rectH * point.y();
      if (i == 0) gc.moveTo(x, y);
      else gc.lineTo(x, y);
    }
    gc.closePath();
    gc.stroke();
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) return first;
    if (second != null && !second.isBlank()) return second;
    return null;
  }

  private static Image firstNonNull(Image primary, Image fallback) {
    return primary != null ? primary : fallback;
  }

  private static Color withOpacity(Color base, double opacity) {
    if (base == null) base = Color.BLACK;
    double clamped = opacity;
    if (Double.isNaN(clamped) || Double.isInfinite(clamped)) clamped = 0.0;
    if (clamped < 0.0) clamped = 0.0;
    if (clamped > 1.0) clamped = 1.0;
    return Color.color(base.getRed(), base.getGreen(), base.getBlue(), clamped);
  }

  private void renderChoices(List<Choice> choices, double width, double height, int hoverIndex) {
    if (choices == null || choices.isEmpty()) return;
    ChoiceGeometry geo = computeChoiceGeometry(choices.size(), width, height);

    for (int i = 0; i < choices.size(); i++) {
      Choice choice = choices.get(i);
      double y = geo.startY() + i * (geo.choiceHeight() + geo.choiceGap());
      boolean enabled = choice.isEnabled() && choiceConditionSatisfied(choice);
      boolean hovered = i == hoverIndex;

      Image buttonImage = !enabled
          ? firstNonNull(choiceButtonDisabledImage, choiceButtonImage)
          : (hovered ? firstNonNull(choiceButtonHoverImage, choiceButtonImage) : choiceButtonImage);
      double radius = choiceCornerRadius;
      boolean clipChoiceButton = hasPolygon(choiceButtonBoundsPolygon);
      if (buttonImage != null) {
        if (clipChoiceButton) {
          gc.save();
          clipToLocalPolygon(choiceButtonBoundsPolygon, geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
          gc.drawImage(buttonImage, geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
          gc.restore();
        } else {
          gc.drawImage(buttonImage, geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
        }
      } else {
        Color bg = !enabled ? choiceDisabledColor : (hovered ? choiceHoverColor : choiceBgColor);
        if (clipChoiceButton) {
          gc.save();
          clipToLocalPolygon(choiceButtonBoundsPolygon, geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
          gc.setFill(bg);
          gc.fillRect(geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
          gc.restore();
        } else {
          gc.setFill(bg);
          gc.fillRoundRect(geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight(), radius, radius);
        }
      }

      // Border
      Color borderColor = !enabled ? choiceDisabledBorderColor : (hovered ? choiceHoverBorderColor : choiceBorderColor);
      gc.setStroke(borderColor);
      gc.setLineWidth(choiceBorderWidth);
      if (clipChoiceButton) {
        strokeLocalPolygon(choiceButtonBoundsPolygon, geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
      } else {
        gc.strokeRoundRect(geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight(), radius, radius);
      }

      // Text
      Color textColor = !enabled ? choiceDisabledTextColor : (hovered ? choiceHoverTextColor : choiceTextColor);
      gc.setFill(textColor);
      gc.setFont(choiceFont);
      String choiceText = resolveRuntimeText(choice.getText());
      double contentX = geo.choiceX() + uiLayout.choiceTextXPadding();
      double contentWidth = Math.max(0, geo.choiceWidth() - uiLayout.choiceTextXPadding() * 2);
      double textWidth = computeTextWidth(choiceText, choiceFont);
      double textBaselineY = uiLayout.choiceTextYAlign() >= 0.0
          ? resolvePaddedTextBaselineY(
              y,
              geo.choiceHeight(),
              uiLayout.choiceTextTopPadding(),
              uiLayout.choiceTextBottomPadding(),
              choiceFont,
              uiLayout.choiceTextYAlign())
          : y + geo.choiceHeight() / 2 + choiceTextBaselineOffset;
      gc.fillText(
          choiceText,
          resolveAlignedTextX(contentX, contentWidth, textWidth, choiceTextXAlign),
          textBaselineY
      );
    }
  }

  private boolean choiceConditionSatisfied(Choice c) {
    String cond = c.getCondition();
    if (cond == null || cond.isEmpty()) return true;
    String[] toks = cond.trim().split("\\s+");
    if (toks.length < 3) return true;
    Object lhs = null;
    if (toks.length >= 1) lhs = getVariableSafe(toks[0]);
    String op = toks.length >= 2 ? toks[1] : "==";
    String rhsRaw = toks.length >= 3 ? toks[2] : "";
    Object rhs = parseScalar(rhsRaw);
    if (lhs instanceof Number ln && rhs instanceof Number rn) {
      double a = ln.doubleValue();
      double b = rn.doubleValue();
      if ("==".equals(op)) return a == b;
      if ("!=".equals(op)) return a != b;
      if (">".equals(op)) return a > b;
      if ("<".equals(op)) return a < b;
      if (">=".equals(op)) return a >= b;
      if ("<=".equals(op)) return a <= b;
      return false;
    }
    String a = lhs == null ? "" : lhs.toString();
    String b = rhs == null ? "" : rhs.toString();
    if ("==".equals(op)) return a.equals(b);
    if ("!=".equals(op)) return !a.equals(b);
    return false;
  }

  private Object getVariableSafe(String key) {
    return key == null ? null : currentState != null ? currentState.getVariables().get(key) : null;
  }

  private static Object parseScalar(String s) {
    if (s == null) return "";
    String t = s.trim();
    if (t.equalsIgnoreCase("true")) return Boolean.TRUE;
    if (t.equalsIgnoreCase("false")) return Boolean.FALSE;
    try { if (t.contains(".")) return Double.parseDouble(t); else return Integer.parseInt(t); }
    catch (Exception ignored) { // reason: not a number; caller treats it as a string
    }
    return t;
  }

  private void renderEnd(double width, double height) {
    gc.setFill(TEXT_COLOR);
    gc.setFont(Font.font(nameFont.getFamily(), FontWeight.BOLD, 32));
    String text = "End";
    gc.fillText(text, width / 2 - 30, height / 2);
  }

  private void renderTransitionOverlay(VnState state, double width, double height) {
    if (state.getActiveTransition() == null) return;
    float progress = state.getTransitionProgress();
    var transitionType = state.getActiveTransition().getType();
    
    switch (transitionType) {
      case FADE -> {
        // Fade effect: black overlay with opacity based on progress
        double opacity = 1.0 - progress; // Fade out from 1.0 to 0.0
        gc.setFill(Color.rgb(0, 0, 0, opacity));
        gc.fillRect(0, 0, width, height);
      }
      case DISSOLVE -> {
        // Dissolve: smoother fade with easing
        double eased = easeInOutQuad(progress);
        double opacity = 1.0 - eased;
        gc.setFill(Color.rgb(0, 0, 0, opacity * 0.85));
        gc.fillRect(0, 0, width, height);
      }
      case SLIDE_LEFT -> {
        // Slide from right: black panel slides off to left
        double eased = easeOutCubic(progress);
        double panelX = -width * eased;
        gc.setFill(Color.BLACK);
        gc.fillRect(panelX, 0, width, height);
      }
      case SLIDE_RIGHT -> {
        // Slide from left: black panel slides off to right
        double eased = easeOutCubic(progress);
        double panelX = width * (1.0 - eased) - width;
        gc.setFill(Color.BLACK);
        gc.fillRect(width - panelX - width, 0, width, height);
      }
      case WIPE -> {
        // Horizontal wipe: black rectangle shrinks from left to right
        double eased = easeInOutQuad(progress);
        double wipeWidth = width * (1.0 - eased);
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, wipeWidth, height);
      }
      case PIXELATE -> {
        // Pixelate: black overlay with decreasing mosaic-like opacity
        double eased = easeInOutQuad(progress);
        double opacity = 1.0 - eased;
        int blockSize = Math.max(2, (int) (40 * (1.0 - eased)));
        gc.setFill(Color.rgb(0, 0, 0, Math.max(0, Math.min(1, opacity * 0.8))));
        for (int bx = 0; bx < width; bx += blockSize * 2) {
          for (int by = 0; by < height; by += blockSize * 2) {
            gc.fillRect(bx, by, blockSize, blockSize);
          }
        }
      }
      case BLINDS -> {
        // Horizontal blinds closing/opening
        double eased = easeInOutQuad(progress);
        int slats = 12;
        double slatH = height / slats;
        double slatVisible = slatH * (1.0 - eased);
        gc.setFill(Color.BLACK);
        for (int i = 0; i < slats; i++) {
          gc.fillRect(0, i * slatH, width, slatVisible);
        }
      }
      case IRIS_IN -> {
        // Circular iris opening from center
        double eased = easeOutCubic(progress);
        double maxRadius = Math.hypot(width / 2, height / 2);
        double radius = maxRadius * eased;
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, width, height);
        gc.save();
        gc.beginPath();
        gc.arc(width / 2, height / 2, radius, radius, 0, 360);
        gc.closePath();
        gc.clip();
        gc.clearRect(0, 0, width, height);
        gc.restore();
      }
      case IRIS_OUT -> {
        // Circular iris closing to center
        double eased = easeOutCubic(progress);
        double maxRadius = Math.hypot(width / 2, height / 2);
        double radius = maxRadius * (1.0 - eased);
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, width, height);
        gc.save();
        gc.beginPath();
        gc.arc(width / 2, height / 2, radius, radius, 0, 360);
        gc.closePath();
        gc.clip();
        gc.clearRect(0, 0, width, height);
        gc.restore();
      }
      case CROSSFADE -> {
        // Crossfade is handled separately in render() for backgrounds
      }
      case NONE -> {
        // No visual effect
      }
    }
  }

  private double easeInOutQuad(double t) {
    return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
  }

  private double easeOutCubic(double t) {
    return 1 - Math.pow(1 - t, 3);
  }

  private double easeInCubic(double t) {
    return t * t * t;
  }

  private void renderSlideBackground(VnBackground prev, VnBackground cur, float progress, double width, double height, boolean left) {
    double p = Math.max(0, Math.min(1, progress));
    double offset = width * p;
    double prevX = left ? -offset : offset;
    double curX = left ? (width - offset) : (-width + offset);
    if (prev != null || cur != null) {
      drawBackgroundAt(prev, prevX, 0, width, height);
      drawBackgroundAt(cur, curX, 0, width, height);
    }
  }

  private void renderWipeBackground(VnBackground prev, VnBackground cur, float progress, double width, double height) {
    drawBackgroundAt(prev, 0, 0, width, height);
    if (cur != null) {
      double p = Math.max(0, Math.min(1, progress));
      double wipeW = width * p;
      gc.save();
      gc.beginPath();
      gc.rect(0, 0, wipeW, height);
      gc.closePath();
      gc.clip();
      drawBackgroundAt(cur, 0, 0, width, height);
      gc.restore();
    }
  }

  private void renderCrossfadeBackground(VnBackground prev, VnBackground cur, float progress, double width, double height) {
    double alphaCur = Math.max(0, Math.min(1, progress));
    double alphaPrev = 1.0 - alphaCur;
    if (prev != null) {
      Image imgPrev = loadBackgroundImage(prev.getImagePath());
      if (imgPrev != null) {
        gc.setGlobalAlpha(alphaPrev);
        gc.drawImage(imgPrev, 0, 0, width, height);
      }
    }
    if (cur != null) {
      Image imgCur = loadBackgroundImage(cur.getImagePath());
      if (imgCur != null) {
        gc.setGlobalAlpha(alphaCur);
        gc.drawImage(imgCur, 0, 0, width, height);
      }
    }
    gc.setGlobalAlpha(1.0);
  }

  private void drawBackgroundAt(VnBackground background, double x, double y, double width, double height) {
    if (background == null) {
      gc.setFill(Color.DARKSLATEGRAY);
      gc.fillRect(x, y, width, height);
      return;
    }
    Image img = loadBackgroundImage(background.getImagePath());
    if (img != null) {
      gc.drawImage(img, x, y, width, height);
    } else {
      gc.setFill(Color.DARKSLATEGRAY);
      gc.fillRect(x, y, width, height);
      gc.setFill(Color.WHITE);
      gc.setFont(Font.font(nameFont.getFamily(), FontWeight.BOLD, 22));
      gc.fillText("No Background Image", x + 20, y + 40);
    }
  }

  private void renderFlashOverlay(VnState state, double width, double height) {
    float alpha = state.getFlashAlpha();
    if (alpha <= 0.001f) return;
    float r = state.getFlashR();
    float g = state.getFlashG();
    float b = state.getFlashB();
    gc.setFill(Color.color(r, g, b, Math.min(1f, alpha)));
    gc.fillRect(0, 0, width, height);
  }

  private void drawPanel(
      Image asset,
      Color fillColor,
      double opacity,
      double x,
      double y,
      double width,
      double height,
      double radius,
      Color borderColor,
      double borderWidth
  ) {
    double prevAlpha = gc.getGlobalAlpha();
    gc.setGlobalAlpha(prevAlpha * clamp(opacity, 0.0, 1.0));
    if (asset != null) {
      gc.drawImage(asset, x, y, width, height);
    } else {
      gc.setFill(fillColor);
      gc.fillRoundRect(x, y, width, height, radius, radius);
    }
    gc.setGlobalAlpha(prevAlpha);
    if (borderColor != null && borderWidth > 0.0) {
      gc.setStroke(borderColor);
      gc.setLineWidth(borderWidth);
      gc.strokeRoundRect(x, y, width, height, radius, radius);
    }
  }

  private void drawBubblePanel(BubbleGeometry bubble) {
    if (bubble == null) return;
    drawPanel(
        bubbleImage,
        bubbleFillColor,
        bubbleOpacity,
        bubble.x(),
        bubble.y(),
        bubble.width(),
        bubble.height(),
        bubbleCornerRadius,
        bubbleBorderFillColor,
        bubbleBorderWidth
    );

    double tailHalf = bubble.tailSize() * 0.58;
    double tailCenter = clamp(
        bubble.anchorX(),
        bubble.x() + bubble.tailSize(),
        bubble.x() + bubble.width() - bubble.tailSize());
    double baseY = bubble.tailOnTop() ? bubble.y() : bubble.y() + bubble.height();
    double tipY = bubble.anchorY();
    double[] xs = new double[] {tailCenter - tailHalf, tailCenter + tailHalf, bubble.anchorX()};
    double[] ys = bubble.tailOnTop()
        ? new double[] {baseY, baseY, tipY}
        : new double[] {baseY, baseY, tipY};
    gc.setFill(withOpacity(bubbleFillColor, bubbleOpacity));
    gc.fillPolygon(xs, ys, 3);
    gc.setStroke(bubbleBorderFillColor);
    gc.setLineWidth(bubbleBorderWidth);
    gc.strokePolygon(xs, ys, 3);
  }

  private void drawWrappedText(String text, double x, double y, double maxWidth, Font font) {
    gc.setFont(font);
    String[] words = text.split(" ");
    StringBuilder line = new StringBuilder();
    double currentY = y;
    double lineHeight = 22;

    for (String word : words) {
      int originalLength = line.length();
      if (originalLength > 0) {
        line.append(' ');
      }
      line.append(word);
      double testWidth = computeTextWidth(line.toString(), font);

      if (testWidth > maxWidth && originalLength > 0) {
        line.setLength(originalLength);
        gc.fillText(line.toString(), x, currentY);
        line.setLength(0);
        line.append(word);
        currentY += lineHeight;
      }
    }
    
    if (line.length() > 0) {
      gc.fillText(line.toString(), x, currentY);
    }
  }

  private double computeTextWidth(String text, Font font) {
    return textMetrics.width(text, font);
  }

  private double computeTextAscent(Font font) {
    return textMetrics.ascent(font);
  }

  private double computeTextHeight(Font font) {
    return textMetrics.height(font);
  }

  private double resolvePaddedTextBaselineY(
      double boxY,
      double boxHeight,
      double topPadding,
      double bottomPadding,
      Font font,
      double yAlign
  ) {
    double contentTop = boxY + Math.max(0.0, topPadding);
    double contentHeight = Math.max(1.0, boxHeight - Math.max(0.0, topPadding) - Math.max(0.0, bottomPadding));
    double textHeight = computeTextHeight(font);
    double ascent = computeTextAscent(font);
    double clampedAlign = clamp(yAlign, 0.0, 1.0);
    double extra = Math.max(0.0, contentHeight - textHeight);
    return contentTop + ascent + extra * clampedAlign;
  }

  private double resolveAlignedTextX(double contentX, double contentWidth, double textWidth, double xAlign) {
    double clampedAlign = clamp(xAlign, 0.0, 1.0);
    double available = Math.max(0.0, contentWidth - textWidth);
    return contentX + available * clampedAlign;
  }

  private double clamp(double value, double min, double max) {
    if (Double.isNaN(value) || Double.isInfinite(value)) return min;
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  private String truncateText(String text, double maxWidth, Font font) {
    if (text == null) return "";
    if (computeTextWidth(text, font) <= maxWidth) return text;
    String ellipsis = "...";
    double ellipsisWidth = computeTextWidth(ellipsis, font);
    int len = text.length();
    while (len > 0) {
      String base = text.substring(0, len);
      if (computeTextWidth(base, font) + ellipsisWidth <= maxWidth) {
        return base + ellipsis;
      }
      len--;
    }
    return ellipsis;
  }

  private String resolveRuntimeText(String text) {
    if (text == null) return "";
    String translated = Localization.translateText(text);
    if (currentState == null) return translated;
    return VnVariableInterpolator.interpolate(translated, currentState.getVariables());
  }

  private void drawContinueIndicator(double x, double y) {
    // Bounce animation: offset Y by sine wave
    double bounce = Math.sin(animationTime * 0.005) * 4;
    double animY = y + bounce;
    
    gc.setFill(TEXT_COLOR);
    gc.fillPolygon(
      new double[]{x, x + 10, x + 5},
      new double[]{animY, animY, animY + 10},
      3
    );
  }

  public void updateAnimation(long deltaMs) {
    animationTime += deltaMs;
  }

  public int getHoveredChoiceIndex(List<Choice> choices, double width, double height, double mouseX, double mouseY) {
    if (choices == null || choices.isEmpty()) return -1;
    ChoiceGeometry geo = computeChoiceGeometry(choices.size(), width, height);

    for (int i = 0; i < choices.size(); i++) {
      double y = geo.startY() + i * (geo.choiceHeight() + geo.choiceGap());
      if (hasPolygon(choiceButtonBoundsPolygon)) {
        if (BoundsPointCodec.containsInRect(
            choiceButtonBoundsPolygon,
            geo.choiceX(),
            y,
            geo.choiceWidth(),
            geo.choiceHeight(),
            mouseX,
            mouseY
        )) return i;
      } else if (mouseX >= geo.choiceX() && mouseX <= geo.choiceX() + geo.choiceWidth()
          && mouseY >= y && mouseY <= y + geo.choiceHeight()) {
        return i;
      }
    }
    return -1;
  }

  public VnUiActionButtonSpec getHoveredTextBoxButton(VnState state, double width, double height, double mouseX, double mouseY) {
    int idx = getHoveredTextBoxButtonIndex(state, width, height, mouseX, mouseY);
    if (idx < 0 || idx >= textBoxButtons.size()) return null;
    return textBoxButtons.get(idx);
  }

  public VnOverlayButtonSpec getHoveredOverlayButton(VnState state, double width, double height, double mouseX, double mouseY) {
    if (state == null || !state.hasOverlayScreens()) return null;
    List<VnOverlayScreenSpec> screens = state.getOverlayScreens();
    for (int screenIndex = screens.size() - 1; screenIndex >= 0; screenIndex--) {
      VnOverlayScreenSpec screen = screens.get(screenIndex);
      if (screen == null) continue;
      if (screen instanceof VnReactiveOverlayScreenSpec reactive && !reactive.isVisibleNow()) continue;
      ScreenGeometry screenGeometry = computeOverlayScreenGeometry(screen, width, height);
      List<VnOverlayButtonSpec> buttons = screen.getButtons();
      for (int i = buttons.size() - 1; i >= 0; i--) {
        VnOverlayButtonSpec button = buttons.get(i);
        if (button == null || !button.enabled()) continue;
        ButtonGeometry geometry = computeOverlayButtonGeometry(button, screenGeometry, width, height);
        if (geometry.contains(mouseX, mouseY)) return button;
      }
      if (screen.isModal()) break;
    }
    return null;
  }

  private int getHoveredTextBoxButtonIndex(VnState state, double width, double height, double mouseX, double mouseY) {
    if (state == null || state.isUiHidden()) return -1;
    if (state.getDialoguePresentationMode() != DialoguePresentationMode.STANDARD) return -1;
    if (textBoxButtons == null || textBoxButtons.isEmpty()) return -1;
    VnNode currentNode = state.getCurrentNode();
    if (currentNode == null || currentNode.getType() != VnNodeType.DIALOGUE) return -1;

    TextBoxGeometry textBox = computeTextBoxGeometry(width, height);
    for (int i = textBoxButtons.size() - 1; i >= 0; i--) {
      VnUiActionButtonSpec button = textBoxButtons.get(i);
      if (button == null || !button.enabled()) continue;
      ButtonGeometry geometry = computeButtonGeometry(button, textBox, width, height);
      if (buttonContainsPoint(button, geometry, mouseX, mouseY)) return i;
    }
    return -1;
  }

  private boolean buttonContainsPoint(VnUiActionButtonSpec button, ButtonGeometry geometry, double mouseX, double mouseY) {
    if (button == null || geometry == null) return false;
    String raw = button.boundsPoints();
    if (raw != null && !raw.isBlank()) {
      List<BoundsPointCodec.Point> points = BoundsPointCodec.parse(raw);
      if (points.size() >= 3) {
        return BoundsPointCodec.containsInRect(
            points,
            geometry.x(),
            geometry.y(),
            geometry.width(),
            geometry.height(),
            mouseX,
            mouseY
        );
      }
    }
    return geometry.contains(mouseX, mouseY);
  }

  private ChoiceGeometry computeChoiceGeometry(int count, double width, double height) {
    double choiceHeight = Math.max(12, uiLayout.choiceHeight());
    double choiceGap = Math.max(0, uiLayout.choiceGap());
    double choiceWidth = clamp(width * uiLayout.choiceWidthFactor(), 20, width);
    double choiceX = width * uiLayout.choiceXCenter() - choiceWidth / 2.0;
    choiceX = clamp(choiceX, 0, Math.max(0, width - choiceWidth));
    double totalHeight = count * choiceHeight + Math.max(0, count - 1) * choiceGap;
    double startY = uiLayout.choiceYStart() < 0
        ? (height - totalHeight) / 2.0
        : (height * uiLayout.choiceYStart()) - totalHeight * uiLayout.choiceYAnchor();
    startY = clamp(startY, 0, Math.max(0, height - totalHeight));
    return new ChoiceGeometry(choiceX, startY, choiceWidth, choiceHeight, choiceGap);
  }

  private TextBoxGeometry computeTextBoxGeometry(double width, double height) {
    double textBoxX = clamp(width * uiLayout.textBoxX(), 0, width);
    double textBoxY = clamp(height * uiLayout.textBoxY(), 0, height);
    double maxBoxWidth = Math.max(1, width - textBoxX);
    double maxBoxHeight = Math.max(1, height - textBoxY);
    double textBoxWidth = clamp(width * uiLayout.textBoxWidth(), 1, maxBoxWidth);
    double textBoxHeight = clamp(height * uiLayout.textBoxHeight(), 1, maxBoxHeight);
    return new TextBoxGeometry(textBoxX, textBoxY, textBoxWidth, textBoxHeight);
  }

  private ButtonGeometry computeButtonGeometry(
      VnUiActionButtonSpec button,
      TextBoxGeometry textBox,
      double viewportWidth,
      double viewportHeight
  ) {
    double baseX = button.viewportSpace() ? 0.0 : textBox.x();
    double baseY = button.viewportSpace() ? 0.0 : textBox.y();
    double baseW = button.viewportSpace() ? Math.max(1.0, viewportWidth) : textBox.width();
    double baseH = button.viewportSpace() ? Math.max(1.0, viewportHeight) : textBox.height();
    double x = baseX + baseW * button.x();
    double y = baseY + baseH * button.y();
    double width = Math.max(8, baseW * button.width());
    double height = Math.max(8, baseH * button.height());
    return new ButtonGeometry(x, y, width, height);
  }

  private void renderOverlayScreens(VnState state, double width, double height, VnOverlayButtonSpec hoveredButton) {
    if (state == null || !state.hasOverlayScreens()) return;
    boolean dimDrawn = false;
    for (VnOverlayScreenSpec screen : state.getOverlayScreens()) {
      if (screen == null) continue;
      if (screen instanceof VnReactiveOverlayScreenSpec reactive && !reactive.isVisibleNow()) continue;
      if (screen.isDimBackground() && !dimDrawn) {
        gc.setFill(Color.rgb(0, 0, 0, 0.42));
        gc.fillRect(0, 0, width, height);
        dimDrawn = true;
      }
      ScreenGeometry screenGeometry = computeOverlayScreenGeometry(screen, width, height);
      renderOverlayPanel(screen, screenGeometry, width, height);
      if (screen instanceof VnReactiveOverlayScreenSpec reactive && reactive.getFacet() != null) {
        renderFacet(reactive, screenGeometry);
      }
      for (VnOverlayButtonSpec button : screen.getButtons()) {
        if (button == null || !button.enabled()) continue;
        ButtonGeometry geometry = computeOverlayButtonGeometry(button, screenGeometry, width, height);
        renderOverlayButton(button, geometry, sameOverlayButton(hoveredButton, button));
      }
    }
  }

  private void renderFacet(VnReactiveOverlayScreenSpec screen, ScreenGeometry root) {
    VnFacetSpec facet = screen.getFacet();
    if (facet == null) return;
    Map<String, ScreenGeometry> geometryById = new HashMap<>();
    geometryById.put(facet.rootId(), root);
    geometryById.put("root", root);
    for (VnFacetSpec.Node node : facet.nodes()) {
      if (node == null || !screen.isFacetNodeVisible(node)) continue;
      ScreenGeometry parent = geometryById.getOrDefault(node.parent(), root);
      ScreenGeometry box = new ScreenGeometry(
          parent.x() + parent.width() * node.x(),
          parent.y() + parent.height() * node.y(),
          Math.max(1, parent.width() * node.width()),
          Math.max(1, parent.height() * node.height())
      );
      geometryById.put(node.id(), box);
      switch (node.type()) {
        case GROUP -> { }
        case TEXT -> {
          String value = screen.resolveFacetText(node.text());
          if (!value.isBlank()) {
            gc.setFill(Color.rgb(236, 240, 248, 0.98));
            gc.setFont(Font.font(dialogueFont.getFamily(), FontWeight.NORMAL, Math.max(12, box.height() * 0.42)));
            double lineY = box.y() + Math.min(box.height() * 0.7, 22);
            for (String line : wrapText(value, box.width(), gc.getFont())) {
              gc.fillText(line, box.x(), lineY);
              lineY += Math.max(16, gc.getFont().getSize() * 1.25);
              if (lineY > box.y() + box.height()) break;
            }
          }
        }
        case IMAGE -> {
          String path = screen.resolveFacetText(node.value());
          Image image = path.isBlank() ? null : loadImage(path);
          if (image != null) gc.drawImage(image, box.x(), box.y(), box.width(), box.height());
        }
        case BAR -> {
          double value = Math.max(0.0, Math.min(1.0, screen.resolveFacetNumber(node.value(), 0.0)));
          gc.setFill(Color.rgb(255, 255, 255, 0.12));
          gc.fillRoundRect(box.x(), box.y(), box.width(), box.height(), box.height(), box.height());
          gc.setFill(Color.rgb(82, 210, 255, 0.88));
          gc.fillRoundRect(box.x(), box.y(), box.width() * value, box.height(), box.height(), box.height());
        }
      }
    }
  }

  private void renderOverlayPanel(VnOverlayScreenSpec screen, ScreenGeometry geometry, double viewportWidth, double viewportHeight) {
    if (screen == null || geometry == null) return;
    gc.setFill(Color.rgb(18, 21, 28, 0.95));
    gc.fillRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 22, 22);
    gc.setStroke(Color.rgb(210, 220, 240, 0.22));
    gc.setLineWidth(1.5);
    gc.strokeRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 22, 22);

    double innerX = geometry.x() + 22;
    double innerY = geometry.y() + 20;
    double innerWidth = Math.max(40, geometry.width() - 44);
    gc.setFill(Color.WHITE);
    gc.setFont(Font.font(nameFont.getFamily(), FontWeight.BOLD, 22));
    gc.fillText(resolveRuntimeText(screen.getTitle()), innerX, innerY + 4);

    String screenText = resolveRuntimeText(screen.getText());
    if (screenText != null && !screenText.isBlank()) {
      gc.setFill(Color.rgb(228, 232, 240, 0.95));
      gc.setFont(Font.font(dialogueFont.getFamily(), FontWeight.NORMAL, 17));
      double textY = innerY + 34;
      for (String line : wrapText(screenText, innerWidth, gc.getFont())) {
        gc.fillText(line, innerX, textY);
        textY += 22;
        if (textY > geometry.y() + geometry.height() - 44) break;
      }
    }

    if (screen.getTimerRemainingMs() > 0) {
      double ratio = Math.max(0.0, Math.min(1.0, screen.getTimerRemainingMs() / 5000.0));
      gc.setFill(Color.rgb(82, 210, 255, 0.65));
      gc.fillRoundRect(
          geometry.x() + 18,
          geometry.y() + geometry.height() - 12,
          Math.max(18, (geometry.width() - 36) * ratio),
          4,
          4,
          4
      );
    }
  }

  private void renderOverlayButton(VnOverlayButtonSpec button, ButtonGeometry geometry, boolean hovered) {
    if (button == null || geometry == null) return;
    Color fill = hovered ? Color.rgb(74, 122, 214, 0.92) : Color.rgb(43, 49, 60, 0.92);
    Color stroke = hovered ? Color.rgb(144, 192, 255, 0.95) : Color.rgb(255, 255, 255, 0.18);
    gc.setFill(fill);
    gc.fillRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 16, 16);
    gc.setStroke(stroke);
    gc.setLineWidth(1.2);
    gc.strokeRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 16, 16);
    String label = resolveRuntimeText(button.label());
    if (label != null && !label.isBlank()) {
      gc.setFill(Color.WHITE);
      gc.setFont(Font.font(choiceFont.getFamily(), FontWeight.NORMAL, 16));
      gc.fillText(label, geometry.x() + 12, geometry.y() + geometry.height() * 0.62);
    }
  }

  private boolean sameOverlayButton(VnOverlayButtonSpec a, VnOverlayButtonSpec b) {
    if (a == null || b == null) return false;
    return java.util.Objects.equals(a.screenId(), b.screenId())
        && java.util.Objects.equals(a.id(), b.id());
  }

  private List<String> wrapText(String text, double maxWidth, Font font) {
    List<String> lines = new ArrayList<>();
    if (text == null || text.isBlank()) return lines;
    String[] words = text.split("\\s+");
    StringBuilder current = new StringBuilder();
    gc.setFont(font);
    for (String word : words) {
      String candidate = current.length() == 0 ? word : (current + " " + word);
      if (measureText(candidate) <= maxWidth || current.length() == 0) {
        current.setLength(0);
        current.append(candidate);
      } else {
        lines.add(current.toString());
        current.setLength(0);
        current.append(word);
      }
    }
    if (current.length() > 0) lines.add(current.toString());
    return lines;
  }

  private double measureText(String text) {
    return textMetrics.width(text, gc.getFont());
  }

  private ScreenGeometry computeOverlayScreenGeometry(VnOverlayScreenSpec screen, double viewportWidth, double viewportHeight) {
    double x = clamp(viewportWidth * screen.getX(), 0, viewportWidth);
    double y = clamp(viewportHeight * screen.getY(), 0, viewportHeight);
    double width = clamp(viewportWidth * screen.getWidth(), 40, viewportWidth - x);
    double height = clamp(viewportHeight * screen.getHeight(), 40, viewportHeight - y);
    return new ScreenGeometry(x, y, width, height);
  }

  private ButtonGeometry computeOverlayButtonGeometry(
      VnOverlayButtonSpec button,
      ScreenGeometry screen,
      double viewportWidth,
      double viewportHeight
  ) {
    double baseX = button.viewportSpace() ? 0.0 : screen.x();
    double baseY = button.viewportSpace() ? 0.0 : screen.y();
    double baseW = button.viewportSpace() ? viewportWidth : screen.width();
    double baseH = button.viewportSpace() ? viewportHeight : screen.height();
    double x = baseX + baseW * button.x();
    double y = baseY + baseH * button.y();
    double width = Math.max(8, baseW * button.width());
    double height = Math.max(8, baseH * button.height());
    return new ButtonGeometry(x, y, width, height);
  }

  private record ChoiceGeometry(double choiceX, double startY, double choiceWidth, double choiceHeight, double choiceGap) {}
  private record TextBoxGeometry(double x, double y, double width, double height) {}
  private record ScreenGeometry(double x, double y, double width, double height) {}
  private record ButtonGeometry(double x, double y, double width, double height) {
    boolean contains(double px, double py) {
      return px >= x && px <= x + width && py >= y && py <= y + height;
    }
  }

  private Image loadImage(String path) {
    if (path == null) return null;

    return imageCache.computeIfAbsent(path, p -> loadResolvedImage(p, true));
  }

  private Image loadBackgroundImage(String path) {
    if (path == null) return null;
    return backgroundImageCache.computeIfAbsent(path, p -> loadResolvedImage(p, true));
  }

  private Image loadImageBlocking(String path) {
    if (path == null) return null;
    return loadResolvedImage(path, false);
  }

  private Image loadResolvedImage(String path, boolean backgroundLoading) {
    try {
      URL url = resolveImageUrl(path);
      if (url != null) {
        return new Image(url.toExternalForm(), 0, 0, false, false, backgroundLoading);
      }
    } catch (Exception e) {
      log.warn("Failed to load image: {}", path);
    }
    return null;
  }

  private URL resolveImageUrl(String path) throws Exception {
    if (path == null || path.isBlank()) return null;

    // Prefer configured asset manager (runtime/editor project overlay support).
    var assetUrl = new AssetCatalog().url(AssetType.IMAGE, path);
    if (assetUrl != null) return assetUrl;

    // Try to load from classpath.
    var url = getClass().getClassLoader().getResource(path);
    if (url != null) return url;

    // Fallback: filesystem (absolute or relative to project root).
    File file = new File(path);
    if (file.exists()) return file.toURI().toURL();

    if (projectRoot != null) {
      // If path starts with the project directory name, strip it.
      String normalized = path.replace('\\', '/');
      String rootName = projectRoot.getName();
      if (normalized.startsWith(rootName + "/")) {
        normalized = normalized.substring(rootName.length() + 1);
      }
      File projectFile = new File(projectRoot, normalized);
      if (projectFile.exists()) return projectFile.toURI().toURL();
    }

    return null;
  }

  private void renderModeIndicators(VnState state, double width, double height) {
    gc.setFont(Font.font(nameFont.getFamily(), FontWeight.BOLD, 14));
    gc.setFill(Color.rgb(255, 255, 255, 0.9));
    
    double y = 25;
    
    // Skip mode indicator
    if (state.isSkipMode()) {
      gc.fillText(Localization.t("hud.skip"), width - 100, y);
      y += 20;
    }
    
    // Auto-play mode indicator
    if (state.isAutoPlayMode()) {
      gc.fillText(Localization.t("hud.auto"), width - 100, y);
      y += 20;
    }
    
    // UI hidden indicator
    if (state.isUiHidden()) {
      gc.fillText(Localization.t("hud.ui_off"), width - 110, y);
    }
  }

  public void clearCache() {
    imageCache.clear();
    compositeSpriteCache.clear();
    timelineLayerWorkingSet.clear();
    layerMetadataCache.clear();
    backgroundImageCache.clear();
    stageBackgroundCache.clear();
    stageCharacterCache.clear();
    layerPathCache.clear();
    particleBlitter.clearCache();
    textMetrics.clear();
  }

  private boolean disposed = false;

  public void dispose() {
    if (disposed) return;
    disposed = true;
    imageCache.clear();
    compositeSpriteCache.clear();
    timelineLayerWorkingSet.clear();
    layerMetadataCache.clear();
    backgroundImageCache.clear();
    stageBackgroundCache.clear();
    stageCharacterCache.clear();
    layerPathCache.clear();
    particleBlitter.clearCache();
    textMetrics.clear();
    timelineAccessor = null;
    currentState = null;
    projectRoot = null;
  }

  // ─── Error Overlay ─────────────────────────────────────────────────

  private static final Color ERROR_BG_COLOR = Color.rgb(28, 30, 34, 0.97);
  private static final Color ERROR_TEXT_COLOR = Color.web("#F2F2F2");
  private static final Color ERROR_DIM_TEXT_COLOR = Color.web("#C8CDD4");
  private static final Color ERROR_BOX_COLOR = Color.rgb(18, 20, 24, 0.88);
  private static final Color ERROR_ACCENT_COLOR = Color.rgb(230, 62, 72);
  private static final Color ERROR_PANEL_BORDER_COLOR = Color.rgb(86, 92, 102);
  private static final Color ERROR_BUTTON_COLOR = Color.rgb(56, 60, 68);
  private static final Color ERROR_BUTTON_HOVER_COLOR = Color.rgb(76, 82, 92);
  private static final Color ERROR_BUTTON_TEXT_COLOR = Color.web("#F0F3F7");
  private static final Color ERROR_PRIMARY_BUTTON_COLOR = Color.web("#236b9a");
  private static final Color ERROR_PRIMARY_BUTTON_HOVER_COLOR = Color.web("#2e84b9");

  /**
   * Renders a full-screen error overlay, similar to Ren'Py's traceback screen.
   * Covers everything and shows error details with action buttons.
   *
   * @param error   the error data to display
   * @param width   canvas width
   * @param height  canvas height
   * @param mouseX  mouse x for button hover
   * @param mouseY  mouse y for button hover
   * @return index of hovered button (0=Continue, 1=Reload, 2=Copy Details) or -1
   */
  public int renderErrorOverlay(com.jvn.core.vn.VnErrorOverlay error,
                                 double width, double height,
                                 double mouseX, double mouseY) {
    if (error == null) return -1;

    // Preserve the existing full-screen safety surface, but organize it like a
    // recoverable runtime interruption rather than a raw traceback dump.
    gc.setFill(ERROR_BG_COLOR);
    gc.fillRect(0, 0, width, height);
    gc.setFill(new LinearGradient(0, 0, 0, 6, false, CycleMethod.NO_CYCLE,
        new Stop(0, ERROR_ACCENT_COLOR), new Stop(1, Color.TRANSPARENT)));
    gc.fillRect(0, 0, width, 6);

    double outerPadding = Math.max(16, Math.min(42, width * 0.045));
    double contentW = Math.min(1080, Math.max(240, width - outerPadding * 2));
    double contentX = (width - contentW) * 0.5;
    double y = Math.max(20, Math.min(36, height * 0.045));

    // Clear error marker and title.
    double markerSize = Math.min(38, Math.max(30, height * 0.055));
    gc.setFill(Color.rgb(88, 30, 36, 0.96));
    gc.fillOval(contentX, y, markerSize, markerSize);
    gc.setStroke(Color.rgb(244, 100, 108, 0.92));
    gc.setLineWidth(1.5);
    gc.strokeOval(contentX + 0.75, y + 0.75, markerSize - 1.5, markerSize - 1.5);
    Font markerFont = Font.font(DEFAULT_FONT_FAMILY, FontWeight.BOLD, markerSize * 0.66);
    gc.setFont(markerFont);
    gc.setFill(Color.web("#FFE4E6"));
    gc.fillText("!", contentX + markerSize * 0.40, y + markerSize * 0.73);

    Font titleFont = Font.font(DEFAULT_FONT_FAMILY, FontWeight.BOLD,
        Math.min(30, Math.max(21, height * 0.040)));
    gc.setFont(titleFont);
    gc.setFill(ERROR_TEXT_COLOR);
    String title = error.getTitle() == null || error.getTitle().isBlank()
        ? "Runtime Error"
        : error.getTitle();
    gc.fillText(title, contentX + markerSize + 14, y + titleFont.getSize());

    Font subtitleFont = Font.font(DEFAULT_FONT_FAMILY, FontWeight.NORMAL,
        Math.min(15, Math.max(12, height * 0.020)));
    String subtitle = switch (error.getType()) {
      case PARSE_ERROR, DSL_PARSE_ERROR, COMPILATION_ERROR ->
          "The script could not be loaded. Fix the source, then reload to try again.";
      default ->
          "Playback paused safely. You can reload, copy the details, or continue past this error.";
    };
    gc.setFont(subtitleFont);
    gc.setFill(ERROR_DIM_TEXT_COLOR);
    gc.fillText(subtitle, contentX + markerSize + 14,
        y + titleFont.getSize() + subtitleFont.getSize() + 5);
    y += Math.max(markerSize, titleFont.getSize() + subtitleFont.getSize() + 7) + 14;

    // Compact source/type metadata keeps the cause in view on smaller windows.
    Font infoFont = Font.font(DEFAULT_FONT_FAMILY, FontWeight.BOLD,
        Math.min(14, Math.max(11, height * 0.018)));
    String source = error.getSourceName() == null || error.getSourceName().isBlank()
        ? "Unknown source"
        : error.getSourceName();
    String location = error.getLineNumber() > 0 ? source + ":" + error.getLineNumber() : source;
    String type = error.getType().name().replace('_', ' ');
    String timeStr = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(error.getTimestamp()));
    String metadata = location + "   •   " + type + "   •   " + timeStr;
    gc.setFont(infoFont);
    gc.setFill(ERROR_DIM_TEXT_COLOR);
    gc.fillText(metadata, contentX, y + infoFont.getSize());
    y += infoFont.getSize() + 12;

    double buttonH = Math.min(44, Math.max(38, height * 0.065));
    double buttonY = Math.max(8, height - outerPadding - buttonH);
    double bodyBottom = buttonY - 16;
    double bodyAvailable = Math.max(72, bodyBottom - y);

    Font sectionFont = Font.font(DEFAULT_FONT_FAMILY, FontWeight.BOLD,
        Math.min(15, Math.max(12, height * 0.019)));
    Font msgFont = Font.font("Monospaced", FontWeight.NORMAL,
        Math.min(15, Math.max(12, height * 0.019)));

    // The human-readable cause is always first and always visible.
    String message = error.getMessage() != null ? error.getMessage() : "(unknown error)";
    gc.setFont(sectionFont);
    gc.setFill(ERROR_TEXT_COLOR);
    gc.fillText("What happened", contentX, y + sectionFont.getSize());
    y += sectionFont.getSize() + 7;
    double messageRoom = Math.max(34, bodyBottom - y);
    double msgBoxH = Math.min(92, Math.max(54, Math.min(bodyAvailable * 0.25, messageRoom)));
    gc.setFill(ERROR_BOX_COLOR);
    gc.fillRoundRect(contentX, y, contentW, msgBoxH, 8, 8);
    gc.setStroke(ERROR_PANEL_BORDER_COLOR);
    gc.setLineWidth(1);
    gc.strokeRoundRect(contentX, y, contentW, msgBoxH, 8, 8);

    gc.setFont(msgFont);
    gc.setFill(ERROR_TEXT_COLOR);
    drawWrappedText(message, contentX + 14, y + 22, contentW - 28, msgBoxH - 10, msgFont);
    y += msgBoxH + 12;

    String rawLine = error.getRawLine();
    boolean showRawLine = rawLine != null && !rawLine.isBlank() && y + 72 <= bodyBottom;
    if (showRawLine) {
      gc.setFont(sectionFont);
      gc.setFill(ERROR_TEXT_COLOR);
      gc.fillText("Source line", contentX, y + sectionFont.getSize());
      y += sectionFont.getSize() + 7;
      double lineBoxH = Math.min(66, bodyBottom - y);
      gc.setFill(ERROR_BOX_COLOR);
      gc.fillRoundRect(contentX, y, contentW, lineBoxH, 8, 8);
      gc.setStroke(ERROR_PANEL_BORDER_COLOR);
      gc.setLineWidth(1);
      gc.strokeRoundRect(contentX, y, contentW, lineBoxH, 8, 8);
      Font lineFont = Font.font("Monospaced", FontWeight.BOLD,
          Math.min(14, Math.max(11, height * 0.018)));
      gc.setFont(lineFont);
      gc.setFill(ERROR_TEXT_COLOR);
      drawWrappedText(rawLine, contentX + 14, y + 20, contentW - 28, lineBoxH - 10, lineFont);
      y += lineBoxH + 12;
    }

    String likelyCause = error.getLikelyCause();
    boolean showLikelyCause = likelyCause != null && !likelyCause.isBlank()
        && y + 90 <= bodyBottom;
    if (showLikelyCause) {
      gc.setFont(sectionFont);
      gc.setFill(ERROR_TEXT_COLOR);
      gc.fillText("Likely cause", contentX, y + sectionFont.getSize());
      y += sectionFont.getSize() + 7;

      double likelyBoxH = Math.min(88, Math.max(62, (bodyBottom - y) * 0.34));
      gc.setFill(ERROR_BOX_COLOR);
      gc.fillRoundRect(contentX, y, contentW, likelyBoxH, 8, 8);
      gc.setStroke(ERROR_PANEL_BORDER_COLOR);
      gc.setLineWidth(1);
      gc.strokeRoundRect(contentX, y, contentW, likelyBoxH, 8, 8);

      gc.setFont(msgFont);
      gc.setFill(ERROR_TEXT_COLOR);
      drawWrappedText(likelyCause, contentX + 14, y + 22, contentW - 28, likelyBoxH - 10, msgFont);
      y += likelyBoxH + 12;
    }

    // Technical details use only the remaining room; Copy Details always
    // includes them even when the viewport is too small to show them.
    String trace = error.getStackTrace();
    if (trace != null && !trace.isBlank() && y + 72 <= bodyBottom) {
      gc.setFont(sectionFont);
      gc.setFill(ERROR_DIM_TEXT_COLOR);
      gc.fillText("Technical details", contentX, y + sectionFont.getSize());
      y += sectionFont.getSize() + 7;
      double traceBoxH = Math.max(54, bodyBottom - y);
      gc.setFill(ERROR_BOX_COLOR);
      gc.fillRoundRect(contentX, y, contentW, traceBoxH, 8, 8);
      gc.setStroke(ERROR_PANEL_BORDER_COLOR);
      gc.setLineWidth(1);
      gc.strokeRoundRect(contentX, y, contentW, traceBoxH, 8, 8);
      Font traceFont = Font.font("Monospaced", FontWeight.NORMAL,
          Math.min(13, Math.max(10, height * 0.016)));
      gc.setFont(traceFont);
      gc.setFill(ERROR_DIM_TEXT_COLOR);
      drawWrappedText(trace, contentX + 14, y + 18, contentW - 28, traceBoxH - 12, traceFont);
    }

    double buttonGap = Math.max(8, Math.min(14, contentW * 0.018));
    double buttonW = Math.min(164, (contentW - buttonGap * 2) / 3.0);
    double buttonsWidth = buttonW * 3 + buttonGap * 2;
    double buttonsStartX = contentX + Math.max(0, contentW - buttonsWidth);
    int hoveredButton = -1;

    if (contentW - buttonsWidth >= 250) {
      gc.setFont(Font.font(DEFAULT_FONT_FAMILY, FontWeight.NORMAL,
          Math.min(12, Math.max(10, height * 0.016))));
      gc.setFill(ERROR_DIM_TEXT_COLOR);
      gc.fillText("Enter / R  Reload    Esc  Continue    C  Copy",
          contentX, buttonY + buttonH / 2 + 5);
    }

    String[] labels = {"Continue", "Reload Script", "Copy Details"};
    for (int i = 0; i < labels.length; i++) {
      double bx = buttonsStartX + i * (buttonW + buttonGap);
      boolean hovered = mouseX >= bx && mouseX <= bx + buttonW
          && mouseY >= buttonY && mouseY <= buttonY + buttonH;
      if (hovered) hoveredButton = i;

      boolean primary = i == 1;
      gc.setFill(primary
          ? (hovered ? ERROR_PRIMARY_BUTTON_HOVER_COLOR : ERROR_PRIMARY_BUTTON_COLOR)
          : (hovered ? ERROR_BUTTON_HOVER_COLOR : ERROR_BUTTON_COLOR));
      gc.fillRoundRect(bx, buttonY, buttonW, buttonH, 6, 6);
      gc.setStroke(primary ? Color.web("#7cc8f4") : ERROR_PANEL_BORDER_COLOR);
      gc.setLineWidth(1);
      gc.strokeRoundRect(bx, buttonY, buttonW, buttonH, 6, 6);

      gc.setFont(Font.font(DEFAULT_FONT_FAMILY, FontWeight.BOLD,
          Math.min(14, Math.max(11, buttonW / 11.0))));
      gc.setFill(ERROR_BUTTON_TEXT_COLOR);
      double textW = computeTextWidth(labels[i], gc.getFont());
      gc.fillText(labels[i], bx + (buttonW - textW) / 2, buttonY + buttonH / 2 + 6);
    }

    return hoveredButton;
  }

  private void drawWrappedText(String text, double x, double y,
                               double maxWidth, double maxHeight, Font font) {
    if (text == null || text.isEmpty()) return;
    gc.setFont(font);
    double lineH = font.getSize() * 1.3;
    double currentY = y;
    String[] lines = text.split("\n");
    for (String line : lines) {
      if (line.isEmpty()) {
        currentY += lineH;
        continue;
      }
      String remaining = line;
      while (!remaining.isEmpty()) {
        if (currentY + lineH > y + maxHeight) {
          gc.fillText("…", x, Math.min(currentY, y + maxHeight));
          return;
        }
        int end = fittingTextEnd(remaining, maxWidth, font);
        if (end < remaining.length()) {
          int whitespace = lastWhitespaceBefore(remaining, end);
          if (whitespace >= Math.max(1, end / 3)) end = whitespace;
        }
        end = Math.max(1, end);
        String visualLine = remaining.substring(0, end).stripTrailing();
        gc.fillText(visualLine, x, currentY);
        currentY += lineH;
        remaining = remaining.substring(end).stripLeading();
      }
    }
  }

  private int fittingTextEnd(String text, double maxWidth, Font font) {
    if (text == null || text.isEmpty()) return 0;
    if (computeTextWidth(text, font) <= maxWidth) return text.length();
    int low = 1;
    int high = text.length();
    while (low < high) {
      int mid = (low + high + 1) >>> 1;
      if (computeTextWidth(text.substring(0, mid), font) <= maxWidth) {
        low = mid;
      } else {
        high = mid - 1;
      }
    }
    return low;
  }

  private static int lastWhitespaceBefore(String text, int end) {
    int safeEnd = Math.min(text == null ? 0 : text.length(), Math.max(0, end));
    for (int i = safeEnd - 1; i > 0; i--) {
      if (Character.isWhitespace(text.charAt(i))) return i;
    }
    return -1;
  }
}
