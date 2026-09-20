package com.jvn.editor.ui;

import javafx.scene.control.ButtonBase;
import javafx.scene.CacheHint;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;

/** Dimensional glass-and-metal icon used by spacious editor command surfaces. */
public final class AeroIcon extends StackPane {
  public enum Kind {
    PROJECT, TRASHMAN, STORY_MAP, INSPECTOR, DIAGNOSTICS, LABEL_FLOW, TIMELINE_OUTLINE, ASSETS,
    LAYOUT, STORYBOARD, LAYERS, IMAGE_ATTRIBUTES, LIGHTING, VERSION_CONTROL,
    PUPPETEER, SCRIPT_EDITOR, SETTINGS,
    NEW_PROJECT, OPEN_PROJECT, RUN, BUILD, REFRESH, ENTRY_SCRIPT, MANIFEST,
    README, DOCUMENTATION, REVEAL, ARROW_BACK, HELP, WHATS_NEW, NO_PROJECT,
    VNS_RUN_LABEL, VNS_RUN_CURSOR, VNS_RUN_ENTRY, VNS_SYMBOLS, VNS_SNIPPET, VNS_FIND,
    VNS_COMMANDS, VNS_WORD_WRAP, VNS_DIFF, VNS_DIAGNOSTICS, VNS_PREVIEW
  }

  private record Palette(Color top, Color bottom, Color edge) {}
  private record Badge(Region glyph, Color color) {}

  private final Kind kind;
  private final double iconSize;

  private AeroIcon(Kind kind, double size) {
    this.kind = kind == null ? Kind.PROJECT : kind;
    this.iconSize = clampSize(size);
    double artworkScale = isVnsCommand(this.kind) ? 0.88 : isSidebarPanelKind(this.kind) ? 0.92 : 0.86;
    Region glyph = glyphFor(this.kind, Math.max(10, iconSize * artworkScale));
    glyph.setMouseTransparent(true);

    setMinSize(iconSize, iconSize);
    setPrefSize(iconSize, iconSize);
    setMaxSize(iconSize, iconSize);
    setPickOnBounds(false);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().add("jvn-aero-icon");
    getChildren().setAll(glyph);
    parentProperty().addListener((obs, oldParent, newParent) -> {
      if (newParent instanceof ButtonBase button) installButtonTreatment(button);
    });
  }

  public static AeroIcon of(Kind kind, double size) {
    return new AeroIcon(kind, size);
  }

  public static AeroIcon runProject(double size) {
    return of(Kind.RUN, size);
  }

  public static AeroIcon buildProject(double size) {
    return of(Kind.BUILD, size);
  }

  public Kind kind() {
    return kind;
  }

  public double iconSize() {
    return iconSize;
  }

  private void installButtonTreatment(ButtonBase button) {
    if (!button.getStyleClass().contains("aero-icon-button")) {
      button.getStyleClass().add("aero-icon-button");
    }
    button.hoverProperty().addListener((obs, wasHovered, hovered) -> updateButtonEffect(button, hovered));
    button.pressedProperty().addListener((obs, wasPressed, pressed) -> updateButtonEffect(button, button.isHover()));
    updateButtonEffect(button, button.isHover());
  }

  private void updateButtonEffect(ButtonBase button, boolean hovered) {
    if (button.isPressed()) {
      setScaleX(0.94);
      setScaleY(0.94);
      setEffect(new DropShadow(Math.max(3, iconSize * 0.16), paletteFor(kind).bottom()));
    } else if (hovered) {
      setScaleX(1.04);
      setScaleY(1.04);
      setEffect(new DropShadow(Math.max(7, iconSize * 0.34), paletteFor(kind).top()));
    } else {
      setScaleX(1.0);
      setScaleY(1.0);
      setEffect(null);
    }
  }

  private static Region glyphFor(Kind kind, double size) {
    Palette palette = paletteFor(kind);
    String color = toCss(mix(palette.top(), palette.edge(), 0.34));
    Region glyph = switch (kind) {
      case PROJECT, TRASHMAN, STORY_MAP, INSPECTOR, DIAGNOSTICS, LABEL_FLOW,
          TIMELINE_OUTLINE, ASSETS, LAYOUT, STORYBOARD, LAYERS, IMAGE_ATTRIBUTES,
          LIGHTING, VERSION_CONTROL, PUPPETEER, SCRIPT_EDITOR, SETTINGS ->
          SidebarPanelArtwork.of(kind, size);
      case OPEN_PROJECT, REVEAL, NEW_PROJECT -> ShellFileArtwork.folder(size, null);
      case DOCUMENTATION -> SidebarPanelArtwork.of(Kind.TIMELINE_OUTLINE, size);
      case RUN -> PuppeteerAeroIcon.of(PuppeteerAeroIcon.Kind.PLAY, size);
      case BUILD -> SidebarToolIcon.of(SidebarToolIcon.Kind.DOWNLOAD, "#57aadd", size);
      case REFRESH -> RefreshIcon.of(size);
      case ENTRY_SCRIPT -> ShellFileArtwork.document(size, CssIcon.play("#37a666"));
      case VNS_RUN_LABEL, VNS_RUN_CURSOR, VNS_RUN_ENTRY, VNS_SYMBOLS, VNS_SNIPPET, VNS_FIND,
          VNS_COMMANDS, VNS_WORD_WRAP, VNS_DIFF, VNS_DIAGNOSTICS, VNS_PREVIEW ->
          vnsCommandGlyph(kind, size, palette);
      case MANIFEST, README -> ShellFileArtwork.document(size, null);
      case ARROW_BACK -> SidebarToolIcon.of(SidebarToolIcon.Kind.PREVIOUS, "#55bce5", size);
      case HELP -> helpGlyph(size);
      case WHATS_NEW -> SidebarToolIcon.of(SidebarToolIcon.Kind.SPARKLES, "#e4ae54", size);
      case NO_PROJECT -> noProjectGlyph(size);
    };
    if (kind != Kind.HELP && kind != Kind.NO_PROJECT && kind != Kind.REFRESH
        && kind != Kind.RUN && kind != Kind.ARROW_BACK && kind != Kind.WHATS_NEW
        && !isVnsCommand(kind) && !isSidebarPanelKind(kind)) {
      glyph = decorate(kind, glyph, size, palette);
    }
    DropShadow depth = new DropShadow(Math.max(2, size * 0.14), 0, Math.max(1, size * 0.08),
        Color.rgb(0, 0, 0, 0.82));
    depth.setInput(glyph.getEffect());
    glyph.setEffect(depth);
    glyph.setCache(true);
    glyph.setCacheHint(CacheHint.SPEED);
    return glyph;
  }

  private static boolean isVnsCommand(Kind kind) {
    return kind == Kind.VNS_RUN_LABEL
        || kind == Kind.VNS_RUN_CURSOR
        || kind == Kind.VNS_RUN_ENTRY
        || kind == Kind.VNS_SYMBOLS
        || kind == Kind.VNS_SNIPPET
        || kind == Kind.VNS_FIND
        || kind == Kind.VNS_COMMANDS
        || kind == Kind.VNS_WORD_WRAP
        || kind == Kind.VNS_DIFF
        || kind == Kind.VNS_DIAGNOSTICS
        || kind == Kind.VNS_PREVIEW;
  }

  private static boolean isSidebarPanelKind(Kind kind) {
    return switch (kind) {
      case PROJECT, TRASHMAN, STORY_MAP, INSPECTOR, DIAGNOSTICS, LABEL_FLOW,
          TIMELINE_OUTLINE, ASSETS, LAYOUT, STORYBOARD, LAYERS, IMAGE_ATTRIBUTES,
          LIGHTING, VERSION_CONTROL, PUPPETEER, SCRIPT_EDITOR, SETTINGS -> true;
      default -> false;
    };
  }

  private static Region vnsCommandGlyph(Kind kind, double size, Palette palette) {
    return switch (kind) {
      case VNS_RUN_LABEL -> vnsRunLabelGlyph(size, palette);
      case VNS_RUN_CURSOR -> vnsRunCursorGlyph(size, palette);
      case VNS_RUN_ENTRY -> vnsRunEntryGlyph(size, palette);
      case VNS_SYMBOLS -> vnsSymbolsGlyph(size, palette);
      case VNS_SNIPPET -> vnsSnippetGlyph(size, palette);
      case VNS_FIND -> vnsFindGlyph(size, palette);
      case VNS_COMMANDS -> vnsCommandsGlyph(size, palette);
      case VNS_WORD_WRAP -> vnsWordWrapGlyph(size, palette);
      case VNS_DIFF -> vnsDiffGlyph(size, palette);
      case VNS_DIAGNOSTICS -> vnsDiagnosticsGlyph(size, palette);
      case VNS_PREVIEW -> vnsPreviewGlyph(size, palette);
      default -> throw new IllegalArgumentException("Not a VNS command icon: " + kind);
    };
  }

  private static Region vnsRunLabelGlyph(double size, Palette palette) {
    Pane art = vnsCanvas(size);
    Rectangle page = vnsDocument(size, palette, 0.05, 0.08, 0.65, 0.82);
    Polygon fold = vnsPageFold(size, palette, 0.49, 0.08, 0.21);
    Line ruleTop = styledLine(
        size * 0.16, size * 0.26, size * 0.54, size * 0.26,
        Color.rgb(236, 255, 252, 0.76), Math.max(0.7, size * 0.035));
    Line ruleBottom = styledLine(
        size * 0.16, size * 0.70, size * 0.49, size * 0.70,
        Color.rgb(204, 255, 246, 0.52), Math.max(0.6, size * 0.03));

    Polygon label = new Polygon(
        size * 0.10, size * 0.36,
        size * 0.55, size * 0.36,
        size * 0.68, size * 0.50,
        size * 0.55, size * 0.64,
        size * 0.10, size * 0.64);
    label.setFill(commandGradient("#45d7c3", "#116f6b"));
    label.setStroke(Color.web("#d8fff8"));
    label.setStrokeWidth(Math.max(0.7, size * 0.035));
    label.setEffect(new InnerShadow(Math.max(0.8, size * 0.05), Color.rgb(0, 54, 54, 0.72)));
    Circle pin = new Circle(size * 0.19, size * 0.50, size * 0.055, Color.web("#f5fffc"));
    pin.setStroke(Color.web("#55cdbd"));
    pin.setStrokeWidth(Math.max(0.4, size * 0.02));

    StackPane playOrb = vnsOrb(size, "#effff4", "#35c66f", "#12643d");
    playOrb.resizeRelocate(size * 0.58, size * 0.53, size * 0.39, size * 0.39);
    Polygon play = playTriangle(size * 0.16, Color.WHITE);
    playOrb.getChildren().add(play);
    art.getChildren().addAll(page, fold, ruleTop, ruleBottom, label, pin, playOrb);
    return art;
  }

  private static Region vnsRunCursorGlyph(double size, Palette palette) {
    Pane art = vnsCanvas(size);
    Rectangle page = vnsDocument(size, palette, 0.05, 0.08, 0.66, 0.82);
    Polygon fold = vnsPageFold(size, palette, 0.49, 0.08, 0.22);
    for (int i = 0; i < 3; i++) {
      art.getChildren().add(styledLine(
          size * 0.16, size * (0.30 + i * 0.17),
          size * 0.54, size * (0.30 + i * 0.17),
          Color.rgb(224, 247, 255, 0.66), Math.max(0.65, size * 0.032)));
    }
    Line caret = styledLine(
        size * 0.39, size * 0.36, size * 0.39, size * 0.69,
        Color.web("#fff3a6"), Math.max(1.35, size * 0.07));
    caret.setEffect(new DropShadow(Math.max(1, size * 0.06), Color.web("#b46a25")));
    StackPane playOrb = vnsOrb(size, "#f2fbff", "#35a9dd", "#155a82");
    playOrb.resizeRelocate(size * 0.58, size * 0.53, size * 0.39, size * 0.39);
    playOrb.getChildren().add(playTriangle(size * 0.16, Color.WHITE));
    art.getChildren().addAll(page, fold, caret, playOrb);
    return art;
  }

  private static Region vnsRunEntryGlyph(double size, Palette palette) {
    Pane art = vnsCanvas(size);
    Line poleShadow = styledLine(
        size * 0.17, size * 0.09, size * 0.17, size * 0.91,
        Color.rgb(0, 28, 20, 0.72), Math.max(2.2, size * 0.11));
    Line pole = styledLine(size * 0.15, size * 0.07, size * 0.15, size * 0.91,
        Color.web("#f3fff8"), Math.max(1.1, size * 0.06));
    Polygon flag = new Polygon(
        size * 0.18, size * 0.11,
        size * 0.78, size * 0.18,
        size * 0.67, size * 0.34,
        size * 0.78, size * 0.49,
        size * 0.18, size * 0.43);
    flag.setFill(commandGradient("#b8f5cb", "#23814b"));
    flag.setStroke(Color.web("#e8fff0"));
    flag.setStrokeWidth(Math.max(0.8, size * 0.04));
    flag.setEffect(new InnerShadow(Math.max(0.8, size * 0.05), Color.rgb(7, 72, 36, 0.64)));
    Line seam = styledLine(
        size * 0.46, size * 0.15, size * 0.46, size * 0.46,
        Color.rgb(234, 255, 241, 0.54), Math.max(0.5, size * 0.025));

    StackPane playOrb = vnsOrb(size, "#f4fff7", "#42ce78", "#12613b");
    playOrb.resizeRelocate(size * 0.43, size * 0.53, size * 0.46, size * 0.46);
    playOrb.getChildren().add(playTriangle(size * 0.19, Color.WHITE));
    art.getChildren().addAll(poleShadow, pole, flag, seam, playOrb);
    return art;
  }

  private static Region vnsSymbolsGlyph(double size, Palette palette) {
    Pane art = vnsCanvas(size);
    Rectangle index = new Rectangle(size * 0.08, size * 0.12, size * 0.66, size * 0.74);
    index.setArcWidth(size * 0.12);
    index.setArcHeight(size * 0.12);
    index.setFill(commandGradient("#a7e9ff", "#1e5b91"));
    index.setStroke(Color.web("#e1f8ff"));
    index.setStrokeWidth(Math.max(0.8, size * 0.04));
    index.setEffect(new InnerShadow(Math.max(1, size * 0.06), Color.rgb(8, 40, 70, 0.74)));

    for (int i = 0; i < 3; i++) {
      Rectangle tab = new Rectangle(
          size * 0.68, size * (0.20 + i * 0.17), size * 0.13, size * 0.10);
      tab.setArcWidth(size * 0.04);
      tab.setArcHeight(size * 0.04);
      tab.setFill(i == 1 ? Color.web("#ffd470") : Color.web("#7ed9f7"));
      tab.setStroke(Color.rgb(235, 250, 255, 0.78));
      tab.setStrokeWidth(Math.max(0.35, size * 0.018));
      art.getChildren().add(tab);
    }

    Text at = new Text("@");
    at.setFont(Font.font("System", FontWeight.BOLD, size * 0.57));
    at.setFill(commandGradient("#f4fbff", "#74cfee"));
    at.setStroke(Color.web("#174c77"));
    at.setStrokeWidth(Math.max(0.35, size * 0.022));
    at.relocate(size * 0.15, size * 0.16);

    Circle lens = new Circle(size * 0.70, size * 0.69, size * 0.17,
        new RadialGradient(
            0, 0, 0.35, 0.30, 0.86, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.rgb(225, 249, 255, 0.72)),
            new Stop(0.62, Color.rgb(55, 134, 175, 0.46)),
            new Stop(1, Color.rgb(12, 43, 67, 0.80))));
    lens.setStroke(Color.web("#ffd886"));
    lens.setStrokeWidth(Math.max(1.1, size * 0.055));
    lens.setEffect(new DropShadow(size * 0.10, Color.rgb(0, 0, 0, 0.72)));
    Line handle = styledLine(
        size * 0.82, size * 0.81, size * 0.94, size * 0.93,
        Color.web("#ffd886"), Math.max(1.5, size * 0.075));
    art.getChildren().add(0, index);
    art.getChildren().addAll(at, lens, handle, vnsGlint(size, 0.64, 0.63));
    return art;
  }

  private static Region vnsSnippetGlyph(double size, Palette palette) {
    Pane art = vnsCanvas(size);
    Rectangle page = vnsDocument(size, palette, 0.05, 0.08, 0.70, 0.82);
    Polygon fold = vnsPageFold(size, palette, 0.52, 0.08, 0.23);
    Polyline left = new Polyline(
        size * 0.33, size * 0.33,
        size * 0.18, size * 0.49,
        size * 0.33, size * 0.65);
    Polyline right = new Polyline(
        size * 0.46, size * 0.33,
        size * 0.61, size * 0.49,
        size * 0.46, size * 0.65);
    for (Polyline bracket : new Polyline[] {left, right}) {
      bracket.setFill(Color.TRANSPARENT);
      bracket.setStroke(Color.web("#f1edff"));
      bracket.setStrokeWidth(Math.max(1.5, size * 0.075));
      bracket.setStrokeLineCap(StrokeLineCap.ROUND);
      bracket.setStrokeLineJoin(StrokeLineJoin.ROUND);
      bracket.setEffect(new DropShadow(size * 0.08, Color.web("#3d2b7a")));
    }
    StackPane plusOrb = vnsOrb(size, "#fff4c4", "#e69a35", "#8c4715");
    plusOrb.resizeRelocate(size * 0.59, size * 0.58, size * 0.38, size * 0.38);
    Line plusH = styledLine(
        0, size * 0.19, size * 0.18, size * 0.19,
        Color.WHITE, Math.max(1.2, size * 0.065));
    Line plusV = styledLine(
        size * 0.09, size * 0.10, size * 0.09, size * 0.28,
        Color.WHITE, Math.max(1.2, size * 0.065));
    plusOrb.getChildren().addAll(plusH, plusV);
    art.getChildren().addAll(page, fold, left, right, plusOrb);
    return art;
  }

  private static Region vnsFindGlyph(double size, Palette palette) {
    Pane art = vnsCanvas(size);
    Rectangle page = vnsDocument(size, palette, 0.05, 0.08, 0.66, 0.82);
    Polygon fold = vnsPageFold(size, palette, 0.49, 0.08, 0.22);
    for (int i = 0; i < 3; i++) {
      Line rule = styledLine(
          size * 0.16, size * (0.31 + i * 0.15),
          size * (i == 1 ? 0.48 : 0.55), size * (0.31 + i * 0.15),
          Color.rgb(235, 250, 255, 0.66), Math.max(0.65, size * 0.032));
      art.getChildren().add(rule);
    }
    Circle lens = new Circle(size * 0.68, size * 0.66, size * 0.22,
        new RadialGradient(
            0, 0, 0.34, 0.28, 0.88, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.rgb(243, 253, 255, 0.76)),
            new Stop(0.58, Color.rgb(82, 176, 218, 0.46)),
            new Stop(1, Color.rgb(13, 54, 83, 0.84))));
    lens.setStroke(Color.web("#e8f9ff"));
    lens.setStrokeWidth(Math.max(1.25, size * 0.065));
    lens.setEffect(new DropShadow(Math.max(1.4, size * 0.09), Color.rgb(0, 0, 0, 0.76)));
    Line handle = styledLine(
        size * 0.83, size * 0.82, size * 0.96, size * 0.95,
        Color.web("#d8edf6"), Math.max(1.8, size * 0.09));
    art.getChildren().add(0, fold);
    art.getChildren().add(0, page);
    art.getChildren().addAll(lens, handle, vnsGlint(size, 0.61, 0.59));
    return art;
  }

  private static Region vnsCommandsGlyph(double size, Palette palette) {
    Pane art = vnsCanvas(size);
    Rectangle window = new Rectangle(size * 0.04, size * 0.10, size * 0.82, size * 0.76);
    window.setArcWidth(size * 0.14);
    window.setArcHeight(size * 0.14);
    window.setFill(commandGradient("#b9c9f6", "#324f88"));
    window.setStroke(Color.web("#eef4ff"));
    window.setStrokeWidth(Math.max(0.9, size * 0.045));
    window.setEffect(new InnerShadow(Math.max(1, size * 0.06), Color.rgb(17, 28, 65, 0.72)));
    Rectangle titleBar = new Rectangle(size * 0.08, size * 0.14, size * 0.74, size * 0.16);
    titleBar.setArcWidth(size * 0.08);
    titleBar.setArcHeight(size * 0.08);
    titleBar.setFill(commandGradient("#f0f5ff", "#8faade"));
    Circle titleDot = new Circle(size * 0.15, size * 0.22, size * 0.035, Color.web("#eb8b62"));

    Text prompt = new Text(">");
    prompt.setFont(Font.font("Monospaced", FontWeight.BOLD, size * 0.31));
    prompt.setFill(Color.web("#f5fbff"));
    prompt.relocate(size * 0.13, size * 0.34);
    for (int i = 0; i < 3; i++) {
      Line row = styledLine(
          size * 0.37, size * (0.46 + i * 0.13),
          size * (i == 0 ? 0.73 : 0.66), size * (0.46 + i * 0.13),
          i == 0 ? Color.web("#fff0a8") : Color.rgb(235, 244, 255, 0.64),
          Math.max(0.75, size * 0.038));
      art.getChildren().add(row);
    }
    StackPane sparkOrb = vnsOrb(size, "#fff7cb", "#e8a22e", "#884712");
    sparkOrb.resizeRelocate(size * 0.65, size * 0.61, size * 0.32, size * 0.32);
    Text spark = new Text("✦");
    spark.setFont(Font.font("System", FontWeight.BOLD, size * 0.17));
    spark.setFill(Color.WHITE);
    sparkOrb.getChildren().add(spark);
    art.getChildren().add(0, titleDot);
    art.getChildren().add(0, titleBar);
    art.getChildren().add(0, window);
    art.getChildren().addAll(prompt, sparkOrb);
    return art;
  }

  private static Region vnsWordWrapGlyph(double size, Palette palette) {
    Pane art = vnsCanvas(size);
    Rectangle page = vnsDocument(size, palette, 0.05, 0.08, 0.72, 0.82);
    Polygon fold = vnsPageFold(size, palette, 0.54, 0.08, 0.23);
    for (int i = 0; i < 3; i++) {
      Line rule = styledLine(
          size * 0.15, size * (0.31 + i * 0.16),
          size * (i == 1 ? 0.54 : 0.63), size * (0.31 + i * 0.16),
          Color.rgb(239, 248, 255, i == 2 ? 0.54 : 0.74),
          Math.max(0.7, size * 0.035));
      art.getChildren().add(rule);
    }

    Arc wrap = new Arc(
        size * 0.60, size * 0.60, size * 0.30, size * 0.25,
        86, -216);
    wrap.setType(ArcType.OPEN);
    wrap.setFill(Color.TRANSPARENT);
    wrap.setStroke(Color.web("#ffd572"));
    wrap.setStrokeWidth(Math.max(1.5, size * 0.075));
    wrap.setStrokeLineCap(StrokeLineCap.ROUND);
    wrap.setEffect(new DropShadow(size * 0.08, Color.rgb(89, 46, 8, 0.72)));
    Polygon arrow = new Polygon(
        size * 0.34, size * 0.69,
        size * 0.52, size * 0.67,
        size * 0.43, size * 0.82);
    arrow.setFill(commandGradient("#fff4bd", "#d68b28"));
    arrow.setStroke(Color.web("#fff2c4"));
    arrow.setStrokeWidth(Math.max(0.45, size * 0.022));
    art.getChildren().add(0, page);
    art.getChildren().add(1, fold);
    art.getChildren().addAll(wrap, arrow);
    return art;
  }

  private static Region vnsDiffGlyph(double size, Palette palette) {
    Pane art = vnsCanvas(size);
    Rectangle removed = new Rectangle(size * 0.04, size * 0.15, size * 0.58, size * 0.72);
    removed.setArcWidth(size * 0.10);
    removed.setArcHeight(size * 0.10);
    removed.setFill(commandGradient("#f6b1a7", "#8f3c45"));
    removed.setStroke(Color.web("#ffe2dc"));
    removed.setStrokeWidth(Math.max(0.75, size * 0.038));
    removed.setEffect(new InnerShadow(Math.max(0.9, size * 0.055), Color.rgb(79, 15, 26, 0.68)));

    Rectangle added = new Rectangle(size * 0.27, size * 0.07, size * 0.66, size * 0.76);
    added.setArcWidth(size * 0.10);
    added.setArcHeight(size * 0.10);
    added.setFill(commandGradient("#a5ecc2", "#28755a"));
    added.setStroke(Color.web("#e0fff0"));
    added.setStrokeWidth(Math.max(0.8, size * 0.04));
    added.setEffect(new InnerShadow(Math.max(0.9, size * 0.055), Color.rgb(11, 67, 49, 0.66)));

    Line minus = styledLine(
        size * 0.10, size * 0.63, size * 0.29, size * 0.63,
        Color.web("#fff0eb"), Math.max(1.2, size * 0.06));
    Line plusH = styledLine(
        size * 0.61, size * 0.38, size * 0.84, size * 0.38,
        Color.WHITE, Math.max(1.2, size * 0.06));
    Line plusV = styledLine(
        size * 0.725, size * 0.265, size * 0.725, size * 0.495,
        Color.WHITE, Math.max(1.2, size * 0.06));
    Line changeOne = styledLine(
        size * 0.38, size * 0.59, size * 0.78, size * 0.59,
        Color.rgb(231, 255, 242, 0.64), Math.max(0.6, size * 0.03));
    Line changeTwo = styledLine(
        size * 0.38, size * 0.70, size * 0.67, size * 0.70,
        Color.rgb(231, 255, 242, 0.50), Math.max(0.6, size * 0.03));
    art.getChildren().addAll(removed, minus, added, plusH, plusV, changeOne, changeTwo);
    return art;
  }

  private static Region vnsDiagnosticsGlyph(double size, Palette palette) {
    Pane art = vnsCanvas(size);
    Rectangle panel = new Rectangle(size * 0.04, size * 0.08, size * 0.80, size * 0.80);
    panel.setArcWidth(size * 0.14);
    panel.setArcHeight(size * 0.14);
    panel.setFill(commandGradient("#b8eaff", "#245c84"));
    panel.setStroke(Color.web("#e9f9ff"));
    panel.setStrokeWidth(Math.max(0.9, size * 0.045));
    panel.setEffect(new InnerShadow(Math.max(1, size * 0.06), Color.rgb(8, 45, 72, 0.74)));
    Rectangle header = new Rectangle(size * 0.08, size * 0.13, size * 0.72, size * 0.15);
    header.setArcWidth(size * 0.08);
    header.setArcHeight(size * 0.08);
    header.setFill(commandGradient("#f3fbff", "#82b8d7"));

    for (int i = 0; i < 3; i++) {
      double y = size * (0.41 + i * 0.16);
      Circle status = new Circle(size * 0.17, y, size * 0.045,
          i == 2 ? Color.web("#ffd16a") : Color.web("#77e39e"));
      status.setStroke(Color.rgb(246, 255, 249, 0.78));
      status.setStrokeWidth(Math.max(0.35, size * 0.018));
      Line row = styledLine(
          size * 0.28, y, size * (i == 1 ? 0.62 : 0.69), y,
          Color.rgb(238, 249, 255, 0.68), Math.max(0.65, size * 0.032));
      art.getChildren().addAll(status, row);
    }

    StackPane warningOrb = vnsOrb(size, "#fff8c9", "#e7a42c", "#8d4c15");
    warningOrb.resizeRelocate(size * 0.64, size * 0.61, size * 0.34, size * 0.34);
    Text warning = new Text("!");
    warning.setFont(Font.font("System", FontWeight.BOLD, size * 0.20));
    warning.setFill(Color.WHITE);
    warningOrb.getChildren().add(warning);
    art.getChildren().add(0, panel);
    art.getChildren().add(1, header);
    art.getChildren().add(warningOrb);
    return art;
  }

  private static Region vnsPreviewGlyph(double size, Palette palette) {
    Pane art = vnsCanvas(size);
    Rectangle stem = new Rectangle(size * 0.37, size * 0.72, size * 0.13, size * 0.13);
    stem.setFill(commandGradient("#eef9ff", "#5f788a"));
    Ellipse foot = new Ellipse(size * 0.435, size * 0.87, size * 0.25, size * 0.06);
    foot.setFill(commandGradient("#eef9ff", "#536b7d"));
    foot.setStroke(Color.web("#dff4ff"));
    foot.setStrokeWidth(Math.max(0.45, size * 0.022));

    Rectangle window = new Rectangle(size * 0.03, size * 0.10, size * 0.81, size * 0.66);
    window.setArcWidth(size * 0.14);
    window.setArcHeight(size * 0.14);
    window.setFill(commandGradient("#9ee9ff", "#123e66"));
    window.setStroke(Color.web("#e7faff"));
    window.setStrokeWidth(Math.max(0.9, size * 0.045));
    window.setEffect(new InnerShadow(Math.max(1, size * 0.06), Color.rgb(5, 31, 55, 0.80)));
    Rectangle screen = new Rectangle(size * 0.09, size * 0.20, size * 0.68, size * 0.47);
    screen.setArcWidth(size * 0.08);
    screen.setArcHeight(size * 0.08);
    screen.setFill(commandGradient("#2379a7", "#071e35"));
    screen.setStroke(Color.rgb(215, 247, 255, 0.58));
    screen.setStrokeWidth(Math.max(0.45, size * 0.022));
    Polygon play = new Polygon(
        size * 0.31, size * 0.30,
        size * 0.58, size * 0.44,
        size * 0.31, size * 0.58);
    play.setFill(commandGradient("#eaffef", "#4bd77e"));
    play.setStroke(Color.rgb(239, 255, 244, 0.84));
    play.setStrokeWidth(Math.max(0.4, size * 0.02));
    play.setEffect(new DropShadow(size * 0.09, Color.web("#2bc16d")));
    Line diagonal = styledLine(
        size * 0.69, size * 0.35, size * 0.96, size * 0.08,
        Color.web("#f3fbff"), Math.max(1.3, size * 0.07));
    Line arrowTop = styledLine(
        size * 0.79, size * 0.08, size * 0.96, size * 0.08,
        Color.web("#f3fbff"), Math.max(1.3, size * 0.07));
    Line arrowSide = styledLine(
        size * 0.96, size * 0.08, size * 0.96, size * 0.25,
        Color.web("#f3fbff"), Math.max(1.3, size * 0.07));
    art.getChildren().addAll(stem, foot, window, screen, play, diagonal, arrowTop, arrowSide);
    return art;
  }

  private static Pane vnsCanvas(double size) {
    Pane pane = new Pane();
    pane.setMinSize(size, size);
    pane.setPrefSize(size, size);
    pane.setMaxSize(size, size);
    return pane;
  }

  private static Rectangle vnsDocument(
      double size, Palette palette, double x, double y, double width, double height) {
    Rectangle page = new Rectangle(size * x, size * y, size * width, size * height);
    page.setArcWidth(size * 0.10);
    page.setArcHeight(size * 0.10);
    page.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, mix(palette.top(), Color.WHITE, 0.36)),
        new Stop(0.18, palette.top()),
        new Stop(1, palette.bottom())));
    page.setStroke(palette.edge());
    page.setStrokeWidth(Math.max(0.8, size * 0.04));
    page.setEffect(new InnerShadow(Math.max(0.9, size * 0.055),
        palette.bottom().deriveColor(0, 0.8, 0.55, 0.72)));
    return page;
  }

  private static Polygon vnsPageFold(
      double size, Palette palette, double x, double y, double foldSize) {
    Polygon fold = new Polygon(
        size * x, size * y,
        size * (x + foldSize), size * (y + foldSize),
        size * x, size * (y + foldSize));
    fold.setFill(commandGradient(toCss(mix(palette.edge(), Color.WHITE, 0.24)),
        toCss(mix(palette.bottom(), Color.WHITE, 0.18))));
    fold.setStroke(Color.rgb(245, 251, 255, 0.74));
    fold.setStrokeWidth(Math.max(0.4, size * 0.02));
    return fold;
  }

  private static StackPane vnsOrb(double size, String highlight, String middle, String edge) {
    Circle rim = new Circle();
    rim.setRadius(size * 0.19);
    rim.setFill(new RadialGradient(
        0, 0, 0.33, 0.26, 0.90, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web(highlight)),
        new Stop(0.44, Color.web(middle)),
        new Stop(1, Color.web(edge))));
    rim.setStroke(Color.rgb(255, 255, 255, 0.88));
    rim.setStrokeWidth(Math.max(0.7, size * 0.035));
    rim.setEffect(new DropShadow(Math.max(1.4, size * 0.09), 0, size * 0.035,
        Color.rgb(0, 0, 0, 0.78)));
    Ellipse shine = new Ellipse(size * 0.19, size * 0.075);
    shine.setFill(Color.rgb(255, 255, 255, 0.48));
    StackPane orb = new StackPane(rim, shine);
    StackPane.setAlignment(shine, Pos.TOP_CENTER);
    StackPane.setMargin(shine, new Insets(size * 0.055, 0, 0, 0));
    return orb;
  }

  private static Polygon playTriangle(double size, Color color) {
    Polygon play = new Polygon(0, 0, size, size * 0.5, 0, size);
    play.setFill(color);
    play.setStroke(Color.rgb(16, 76, 42, 0.45));
    play.setStrokeWidth(Math.max(0.35, size * 0.055));
    play.setTranslateX(size * 0.08);
    return play;
  }

  private static void polishShape(Shape shape, Palette palette, double size) {
    shape.setFill(commandGradient(toCss(palette.top()), toCss(palette.bottom())));
    shape.setStroke(palette.edge());
    shape.setStrokeWidth(Math.max(0.75, size * 0.045));
    shape.setEffect(new InnerShadow(Math.max(0.8, size * 0.06), Color.rgb(255, 255, 255, 0.25)));
  }

  private static LinearGradient commandGradient(String top, String bottom) {
    return new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web(top)),
        new Stop(1, Color.web(bottom)));
  }

  private static Circle vnsGlint(double size, double x, double y) {
    Circle glint = new Circle(size * x, size * y, size * 0.035, Color.rgb(255, 255, 255, 0.84));
    glint.setEffect(new DropShadow(size * 0.08, Color.rgb(255, 255, 255, 0.55)));
    return glint;
  }

  private static Line styledLine(
      double startX, double startY, double endX, double endY, Color color, double width) {
    Line line = new Line(startX, startY, endX, endY);
    line.setStroke(color);
    line.setStrokeWidth(width);
    line.setStrokeLineCap(StrokeLineCap.ROUND);
    return line;
  }

  private static Region decorate(Kind kind, Region base, double size, Palette palette) {
    Badge badge = badgeFor(kind, size);
    StackPane artwork = new StackPane(base);
    artwork.setMinSize(size, size);
    artwork.setPrefSize(size, size);
    artwork.setMaxSize(size, size);

    Circle glint = new Circle(size * 0.055, Color.rgb(255, 255, 255, 0.78));
    glint.setEffect(new DropShadow(size * 0.08, Color.rgb(255, 255, 255, 0.52)));
    StackPane.setAlignment(glint, Pos.TOP_LEFT);
    StackPane.setMargin(glint, new Insets(size * 0.16, 0, 0, size * 0.19));
    artwork.getChildren().add(glint);

    double badgeSize = Math.max(7, size * 0.39);
    Circle rim = new Circle(badgeSize / 2.0);
    rim.setFill(new RadialGradient(
        0, 0, 0.34, 0.27, 0.88, true, CycleMethod.NO_CYCLE,
        new Stop(0, badge.color().deriveColor(0, 0.52, 1.42, 1)),
        new Stop(0.58, badge.color()),
        new Stop(1, badge.color().deriveColor(0, 1, 0.42, 1))));
    rim.setStroke(Color.rgb(255, 255, 255, 0.82));
    rim.setStrokeWidth(Math.max(0.55, size * 0.032));
    rim.setEffect(new DropShadow(Math.max(1.4, size * 0.11), 0, size * 0.04,
        Color.rgb(0, 0, 0, 0.82)));

    Region badgeGlyph = badge.glyph();
    StackPane jewel = new StackPane(rim, badgeGlyph);
    jewel.setMinSize(badgeSize, badgeSize);
    jewel.setPrefSize(badgeSize, badgeSize);
    jewel.setMaxSize(badgeSize, badgeSize);
    jewel.setEffect(new InnerShadow(Math.max(0.5, size * 0.035), Color.rgb(255, 255, 255, 0.36)));
    StackPane.setAlignment(jewel, Pos.BOTTOM_RIGHT);
    StackPane.setMargin(jewel, new Insets(0, -size * 0.04, -size * 0.03, 0));
    artwork.getChildren().add(jewel);
    return artwork;
  }

  private static Badge badgeFor(Kind kind, double size) {
    double glyphSize = Math.max(5, size * 0.21);
    return switch (kind) {
      case PROJECT -> new Badge(sized(CssIcon.home("#ffffff"), glyphSize), Color.web("#4b82b1"));
      case TRASHMAN -> new Badge(sized(CssIcon.clearX("#ffffff"), glyphSize), Color.web("#c44b55"));
      case STORY_MAP -> new Badge(sized(CssIcon.arrowRight("#ffffff"), glyphSize), Color.web("#d16c25"));
      case INSPECTOR -> new Badge(sized(CssIcon.info("#ffffff"), glyphSize), Color.web("#288fc5"));
      case DIAGNOSTICS -> new Badge(sized(CssIcon.check("#ffffff"), glyphSize), Color.web("#27a45a"));
      case LABEL_FLOW -> new Badge(sized(CssIcon.arrowRight("#ffffff"), glyphSize), Color.web("#2c9a5a"));
      case TIMELINE_OUTLINE -> new Badge(sized(CssIcon.timeline("#ffffff"), glyphSize), Color.web("#d16c25"));
      case NEW_PROJECT -> new Badge(sized(CssIcon.plusBold("#ffffff"), glyphSize), Color.web("#28a75d"));
      case OPEN_PROJECT -> new Badge(sized(CssIcon.arrowRight("#ffffff"), glyphSize), Color.web("#2689d8"));
      case ASSETS -> new Badge(sized(CssIcon.landscape("#ffffff"), glyphSize), Color.web("#298ac4"));
      case LAYOUT -> new Badge(sized(CssIcon.grid("#ffffff"), glyphSize), Color.web("#4d7ea5"));
      case STORYBOARD -> new Badge(sized(CssIcon.play("#ffffff"), glyphSize), Color.web("#d16c25"));
      case LAYERS -> new Badge(sized(CssIcon.plusBold("#ffffff"), glyphSize), Color.web("#587fa2"));
      case DOCUMENTATION -> new Badge(sized(CssIcon.help("#ffffff"), glyphSize), Color.web("#397cc0"));
      case REVEAL -> new Badge(sized(CssIcon.popOut("#ffffff"), glyphSize), Color.web("#397cc0"));
      case IMAGE_ATTRIBUTES, SCRIPT_EDITOR ->
          new Badge(sized(CssIcon.edit("#ffffff"), glyphSize), Color.web("#168dbf"));
      case VERSION_CONTROL -> new Badge(sized(CssIcon.download("#ffffff"), glyphSize), Color.web("#2c9a5a"));
      case MANIFEST -> new Badge(sized(CssIcon.check("#ffffff"), glyphSize), Color.web("#35a45c"));
      case README -> new Badge(sized(CssIcon.list("#ffffff"), glyphSize), Color.web("#4b7ba6"));
      case ENTRY_SCRIPT -> new Badge(sized(CssIcon.play("#ffffff"), glyphSize), Color.web("#2fa35a"));
      case VNS_RUN_LABEL ->
          new Badge(sized(CssIcon.play("#ffffff"), glyphSize), Color.web("#2fa35a"));
      case VNS_RUN_CURSOR ->
          new Badge(sized(CssIcon.play("#ffffff"), glyphSize), Color.web("#2689d8"));
      case VNS_RUN_ENTRY ->
          new Badge(sized(CssIcon.rocket("#ffffff"), glyphSize), Color.web("#cf6927"));
      case VNS_SYMBOLS ->
          new Badge(sized(CssIcon.search("#ffffff"), glyphSize), Color.web("#c98225"));
      case VNS_SNIPPET ->
          new Badge(sized(CssIcon.plusBold("#ffffff"), glyphSize), Color.web("#b46a25"));
      case VNS_FIND ->
          new Badge(sized(CssIcon.search("#ffffff"), glyphSize), Color.web("#278bc5"));
      case VNS_COMMANDS ->
          new Badge(sized(CssIcon.sparkles("#ffffff"), glyphSize), Color.web("#b46a25"));
      case VNS_WORD_WRAP ->
          new Badge(sized(CssIcon.refresh("#ffffff"), glyphSize), Color.web("#c98225"));
      case VNS_DIFF ->
          new Badge(sized(CssIcon.branchPlus("#ffffff"), glyphSize), Color.web("#2c9a5a"));
      case VNS_DIAGNOSTICS ->
          new Badge(sized(CssIcon.check("#ffffff"), glyphSize), Color.web("#278bc5"));
      case VNS_PREVIEW ->
          new Badge(sized(CssIcon.popOut("#ffffff"), glyphSize), Color.web("#278bc5"));
      case BUILD -> new Badge(sized(CssIcon.wrench("#ffffff"), glyphSize), Color.web("#287fb4"));
      case LIGHTING, PUPPETEER ->
          new Badge(sized(CssIcon.sparkles("#ffffff"), glyphSize), Color.web("#b54f95"));
      case SETTINGS -> new Badge(sized(CssIcon.auto("#ffffff"), glyphSize), Color.web("#d27128"));
      case RUN -> new Badge(sized(CssIcon.rocket("#ffffff"), glyphSize), Color.web("#d16c25"));
      case REFRESH -> new Badge(sized(CssIcon.check("#ffffff"), glyphSize), Color.web("#2789c5"));
      case ARROW_BACK -> new Badge(sized(CssIcon.home("#ffffff"), glyphSize), Color.web("#c76328"));
      case HELP -> new Badge(sized(CssIcon.questionMark("#ffffff"), glyphSize), Color.web("#397cc0"));
      case WHATS_NEW -> new Badge(sized(CssIcon.check("#ffffff"), glyphSize), Color.web("#7654bd"));
      case NO_PROJECT -> new Badge(sized(CssIcon.minus("#ffffff"), glyphSize), Color.web("#c97b2d"));
    };
  }

  private static Region sized(Region glyph, double size) {
    double scale = size / Math.max(1, glyph.getPrefWidth());
    glyph.setScaleX(scale);
    glyph.setScaleY(scale);
    return glyph;
  }

  private static Region settingsGlyph(String color, double size) {
    double center = size / 2.0;
    Shape gear = new Circle(center, center, size * 0.31);
    for (int i = 0; i < 8; i++) {
      Rectangle tooth = new Rectangle(
          center - size * 0.075, size * 0.075, size * 0.15, size * 0.27);
      tooth.getTransforms().add(new Rotate(i * 45.0, center, center));
      gear = Shape.union(gear, tooth);
    }
    gear = Shape.subtract(gear, new Circle(center, center, size * 0.115));
    gear.setFill(Color.web(color));
    gear.setStroke(Color.web(color).deriveColor(0, 0.72, 0.68, 0.9));
    gear.setStrokeWidth(Math.max(0.45, size * 0.025));
    StackPane wrapper = new StackPane(gear);
    wrapper.setMinSize(size, size);
    wrapper.setPrefSize(size, size);
    wrapper.setMaxSize(size, size);
    return wrapper;
  }

  private static Region diagnosticsGlyph(double size) {
    Pane artwork = new Pane();
    artwork.setMinSize(size, size);
    artwork.setPrefSize(size, size);
    artwork.setMaxSize(size, size);

    Rectangle screen = new Rectangle(size * 0.07, size * 0.10, size * 0.86, size * 0.62);
    screen.setArcWidth(size * 0.16);
    screen.setArcHeight(size * 0.16);
    screen.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#e8f7ff")),
        new Stop(0.18, Color.web("#65c9ed")),
        new Stop(0.30, Color.web("#153e61")),
        new Stop(1, Color.web("#071b2b"))));
    screen.setStroke(Color.web("#dff7ff"));
    screen.setStrokeWidth(Math.max(0.7, size * 0.045));
    screen.setEffect(new DropShadow(Math.max(1.4, size * 0.11), 0, size * 0.05,
        Color.rgb(0, 0, 0, 0.82)));

    Rectangle reflection = new Rectangle(size * 0.13, size * 0.16, size * 0.52, size * 0.09);
    reflection.setArcWidth(size * 0.08);
    reflection.setArcHeight(size * 0.08);
    reflection.setFill(Color.rgb(255, 255, 255, 0.38));

    Polyline pulse = new Polyline(
        size * 0.15, size * 0.47,
        size * 0.31, size * 0.47,
        size * 0.39, size * 0.34,
        size * 0.48, size * 0.60,
        size * 0.59, size * 0.40,
        size * 0.67, size * 0.47,
        size * 0.84, size * 0.47);
    pulse.setFill(Color.TRANSPARENT);
    pulse.setStroke(Color.web("#72ff8f"));
    pulse.setStrokeWidth(Math.max(1.1, size * 0.075));
    pulse.setEffect(new DropShadow(Math.max(1.2, size * 0.09), Color.web("#38ff70")));

    Rectangle stem = new Rectangle(size * 0.43, size * 0.70, size * 0.14, size * 0.13);
    stem.setFill(new LinearGradient(
        0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#677987")),
        new Stop(0.48, Color.web("#f2fbff")),
        new Stop(1, Color.web("#566775"))));
    Ellipse foot = new Ellipse(size * 0.50, size * 0.86, size * 0.28, size * 0.075);
    foot.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#f5fbff")),
        new Stop(1, Color.web("#617584"))));
    foot.setStroke(Color.web("#d8ecf7"));
    foot.setStrokeWidth(Math.max(0.45, size * 0.025));

    artwork.getChildren().addAll(stem, foot, screen, reflection, pulse);
    return artwork;
  }

  private static Region helpGlyph(double size) {
    Circle orb = new Circle(size * 0.39);
    orb.setFill(new RadialGradient(
        0, 0, 0.33, 0.25, 0.88, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#f7fdff")),
        new Stop(0.34, Color.web("#94d9f4")),
        new Stop(0.72, Color.web("#3c7fae")),
        new Stop(1, Color.web("#173b5a"))));
    orb.setStroke(Color.web("#e8f8ff"));
    orb.setStrokeWidth(Math.max(0.7, size * 0.045));
    orb.setEffect(new InnerShadow(Math.max(1.2, size * 0.08), Color.rgb(5, 28, 45, 0.78)));

    Region question = sized(CssIcon.questionMark("#ffffff"), size * 0.48);
    question.setEffect(new DropShadow(Math.max(1, size * 0.06), 0, size * 0.04,
        Color.rgb(0, 23, 42, 0.88)));

    Ellipse reflection = new Ellipse(size * 0.43, size * 0.31, size * 0.19, size * 0.075);
    reflection.setFill(Color.rgb(255, 255, 255, 0.52));

    StackPane canvas = new StackPane(orb, question, reflection);
    canvas.setMinSize(size, size);
    canvas.setPrefSize(size, size);
    canvas.setMaxSize(size, size);
    StackPane.setAlignment(reflection, Pos.TOP_CENTER);
    StackPane.setMargin(reflection, new Insets(size * 0.17, 0, 0, 0));
    return canvas;
  }

  private static Region noProjectGlyph(double size) {
    Rectangle tab = new Rectangle(size * 0.13, size * 0.16, size * 0.38, size * 0.24);
    tab.setArcWidth(size * 0.10);
    tab.setArcHeight(size * 0.10);
    tab.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#e9f4fb")),
        new Stop(1, Color.web("#70889a"))));

    Rectangle folder = new Rectangle(size * 0.08, size * 0.27, size * 0.84, size * 0.57);
    folder.setArcWidth(size * 0.13);
    folder.setArcHeight(size * 0.13);
    folder.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#d8e7f0")),
        new Stop(0.22, Color.web("#7892a5")),
        new Stop(1, Color.web("#344b5c"))));
    folder.setStroke(Color.web("#edf8ff"));
    folder.setStrokeWidth(Math.max(0.8, size * 0.035));
    folder.setEffect(new DropShadow(Math.max(2, size * 0.10), 0, size * 0.06,
        Color.rgb(0, 0, 0, 0.82)));

    Rectangle emptyWell = new Rectangle(size * 0.18, size * 0.40, size * 0.64, size * 0.30);
    emptyWell.setArcWidth(size * 0.10);
    emptyWell.setArcHeight(size * 0.10);
    emptyWell.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#172b3a")),
        new Stop(1, Color.web("#0a1822"))));
    emptyWell.setStroke(Color.rgb(220, 241, 252, 0.44));
    emptyWell.setStrokeWidth(Math.max(0.5, size * 0.022));

    double markerSize = size * 0.31;
    Circle marker = new Circle(markerSize / 2.0);
    marker.setFill(new RadialGradient(
        0, 0, 0.32, 0.27, 0.88, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#fff2bc")),
        new Stop(0.55, Color.web("#e8a948")),
        new Stop(1, Color.web("#9b541e"))));
    marker.setStroke(Color.web("#fff5d8"));
    marker.setStrokeWidth(Math.max(0.6, size * 0.026));
    marker.setEffect(new DropShadow(Math.max(1.5, size * 0.08), Color.rgb(0, 0, 0, 0.75)));
    Rectangle minus = new Rectangle(markerSize * 0.24, markerSize * 0.45, markerSize * 0.52, markerSize * 0.11);
    minus.setArcWidth(markerSize * 0.08);
    minus.setArcHeight(markerSize * 0.08);
    minus.setFill(Color.web("#fff8df"));
    StackPane markerJewel = new StackPane(marker, minus);
    markerJewel.setMinSize(markerSize, markerSize);
    markerJewel.setPrefSize(markerSize, markerSize);
    markerJewel.setMaxSize(markerSize, markerSize);

    Pane artwork = new Pane(tab, folder, emptyWell, markerJewel);
    artwork.setMinSize(size, size);
    artwork.setPrefSize(size, size);
    artwork.setMaxSize(size, size);
    markerJewel.relocate(size * 0.66, size * 0.63);
    return artwork;
  }

  private static Palette paletteFor(Kind kind) {
    return switch (kind) {
      case PROJECT, OPEN_PROJECT, ASSETS, DOCUMENTATION, REVEAL ->
          palette("#ffe37a", "#b76a17", "#fff1b0");
      case TRASHMAN -> palette("#d9e5ec", "#61717e", "#f5fbff");
      case STORY_MAP, STORYBOARD, RUN ->
          palette("#ffbd69", "#c5521c", "#ffe0a2");
      case INSPECTOR, SCRIPT_EDITOR, IMAGE_ATTRIBUTES ->
          palette("#79d8ff", "#2267ad", "#c9f2ff");
      case DIAGNOSTICS -> palette("#72ddff", "#19547a", "#dff8ff");
      case LABEL_FLOW, VERSION_CONTROL, ENTRY_SCRIPT, NEW_PROJECT ->
          palette("#8de8aa", "#26824e", "#d8ffe4");
      case VNS_RUN_LABEL ->
          palette("#75e8d6", "#1d8074", "#d4fff8");
      case VNS_RUN_CURSOR ->
          palette("#8bdfff", "#26689f", "#dff7ff");
      case VNS_RUN_ENTRY ->
          palette("#8de8aa", "#26824e", "#d8ffe4");
      case VNS_SYMBOLS ->
          palette("#79d8ff", "#2267ad", "#c9f2ff");
      case VNS_SNIPPET ->
          palette("#c4b4ff", "#6450a8", "#eee9ff");
      case VNS_FIND ->
          palette("#8bdfff", "#26689f", "#dff7ff");
      case VNS_COMMANDS ->
          palette("#b9c9f6", "#324f88", "#eef4ff");
      case VNS_WORD_WRAP ->
          palette("#b8d9ef", "#4a708b", "#eef8ff");
      case VNS_DIFF ->
          palette("#a5ecc2", "#28755a", "#e0fff0");
      case VNS_DIAGNOSTICS ->
          palette("#b8eaff", "#245c84", "#e9f9ff");
      case VNS_PREVIEW ->
          palette("#72ddff", "#19547a", "#dff8ff");
      case LAYOUT, LAYERS, MANIFEST, README, TIMELINE_OUTLINE ->
          palette("#b9c9d8", "#586b7e", "#edf6ff");
      case LIGHTING, PUPPETEER ->
          palette("#f7a9dc", "#9a478c", "#ffd9ef");
      case SETTINGS -> palette("#ffb15a", "#9b4f1d", "#fff0d5");
      case BUILD -> palette("#87e79a", "#2d873e", "#ddffe3");
      case REFRESH -> palette("#7edbff", "#2471b3", "#d7f5ff");
      case ARROW_BACK -> palette("#ffb55d", "#b74c15", "#ffe0b0");
      case HELP -> palette("#a9e5fb", "#235b82", "#f1fbff");
      case WHATS_NEW -> palette("#c5b6ff", "#5f449e", "#f0ebff");
      case NO_PROJECT -> palette("#d8e7f0", "#344b5c", "#edf8ff");
    };
  }

  private static Palette palette(String top, String bottom, String edge) {
    return new Palette(Color.web(top), Color.web(bottom), Color.web(edge));
  }

  private static Color mix(Color first, Color second, double amount) {
    return first.interpolate(second, Math.max(0, Math.min(1, amount)));
  }

  private static String toCss(Color color) {
    return String.format("#%02x%02x%02x",
        Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255),
        Math.round(color.getBlue() * 255));
  }

  private static double clampSize(double size) {
    if (!Double.isFinite(size)) return 24;
    return Math.max(14, Math.min(48, size));
  }
}
