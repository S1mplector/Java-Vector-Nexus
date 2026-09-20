package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.control.ButtonBase;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;

/** Compact Aero artwork shared by Puppeteer's floating command dockers. */
public final class PuppeteerAeroIcon extends StackPane {
  public enum Kind {
    COPY, PASTE, HISTORY, DUPLICATE, ADD_ALL, SAVE_CLIP, LOAD_CLIP, CHARACTER_SLOT,
    PREVIOUS, NEXT, FOCUS, ZOOM_FIT, DISTRIBUTE, REVERSE, STRETCH, COMPRESS,
    RIPPLE, COMPACT_EXPORT,
    REWIND, PLAY, PAUSE, STOP, UNDO, REDO,
    FIT_DURATION, LOOP, LOOP_IN, LOOP_OUT, CLEAR,
    PRESET, SNAP, RUNTIME_PREVIEW, VIEWPORT_STABILIZE, AUTO_KEY,
    SNAP_GRID, SNAP_ENTITY, ORBIT, ORBIT_ALIGN, HELP,
    ADD_AUDIO_CUE, ADD_EXPRESSION_CUE, MANAGE_EVENTS,
    SYNC, REGISTER, RECORD_GIF, COPY_CODE, VERIFY
  }

  private record Palette(Color light, Color mid, Color dark, Color glow) {}

  private final Kind kind;
  private final double iconSize;

  private PuppeteerAeroIcon(Kind kind, double requestedSize) {
    this.kind = kind == null ? Kind.COPY : kind;
    this.iconSize = Math.max(18, Math.min(28, requestedSize));
    Palette palette = paletteFor(this.kind);

    Region shell = shellFor(this.kind, palette, iconSize);

    Region glyph = glyphFor(this.kind, iconSize * 0.54);
    glyph.setMouseTransparent(true);
    glyph.setEffect(new DropShadow(Math.max(1.2, iconSize * 0.08), 0, iconSize * 0.035,
        Color.rgb(0, 0, 0, 0.92)));

    Circle status = new Circle(iconSize * 0.055, palette.glow().deriveColor(0, 0.75, 1.45, 0.9));
    status.setTranslateX(iconSize * 0.31);
    status.setTranslateY(iconSize * 0.31);
    status.setEffect(new DropShadow(iconSize * 0.13, palette.glow()));

    setMinSize(iconSize, iconSize);
    setPrefSize(iconSize, iconSize);
    setMaxSize(iconSize, iconSize);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().add("puppeteer-aero-icon");
    Region object = objectArtwork(this.kind, iconSize);
    if (object != null) {
      object.getStyleClass().add("jvn-puppeteer-bespoke-shell");
      getChildren().setAll(object);
    }
    else getChildren().setAll(shell, glyph, status);
    parentProperty().addListener((observable, oldParent, newParent) -> {
      if (newParent instanceof ButtonBase button) installButtonTreatment(button, palette);
    });
  }

  public static PuppeteerAeroIcon of(Kind kind) {
    return new PuppeteerAeroIcon(kind, 23);
  }

  public static PuppeteerAeroIcon of(Kind kind, double size) {
    return new PuppeteerAeroIcon(kind, size);
  }

  public Kind kind() { return kind; }

  public double iconSize() { return iconSize; }

  private void installButtonTreatment(ButtonBase button, Palette palette) {
    if (!button.getStyleClass().contains("puppeteer-aero-button")) {
      button.getStyleClass().add("puppeteer-aero-button");
    }
    button.hoverProperty().addListener((obs, before, hovered) -> updateEffect(button, palette));
    button.pressedProperty().addListener((obs, before, pressed) -> updateEffect(button, palette));
    updateEffect(button, palette);
  }

  private void updateEffect(ButtonBase button, Palette palette) {
    if (button.isPressed()) {
      setScaleX(0.92);
      setScaleY(0.92);
      setEffect(new DropShadow(iconSize * 0.16, palette.glow()));
    } else if (button.isHover()) {
      setScaleX(1.07);
      setScaleY(1.07);
      setEffect(new DropShadow(iconSize * 0.36, palette.glow()));
    } else {
      setScaleX(1);
      setScaleY(1);
      setEffect(null);
    }
  }

  private static @org.jspecify.annotations.Nullable Region objectArtwork(Kind kind, double size) {
    return switch (kind) {
      case COPY, COPY_CODE -> SidebarToolIcon.of(SidebarToolIcon.Kind.COPY, "#6eadd5", size);
      case PASTE -> ShellFileArtwork.document(size, CssIcon.contentPaste("#c19642"));
      case HISTORY, MANAGE_EVENTS -> SidebarToolIcon.of(SidebarToolIcon.Kind.TIMELINE, "#76b7d7", size);
      case PRESET, LOAD_CLIP -> ShellFileArtwork.folder(size, null);
      case CHARACTER_SLOT -> SidebarToolIcon.of(SidebarToolIcon.Kind.PERSON, "#67b7df", size);
      case UNDO -> SidebarToolIcon.of(SidebarToolIcon.Kind.UNDO, "#89a2e3", size);
      case REDO -> SidebarToolIcon.of(SidebarToolIcon.Kind.REDO, "#61bfdc", size);
      case HELP -> AeroIcon.of(AeroIcon.Kind.HELP, size);
      case REGISTER -> SidebarToolIcon.of(SidebarToolIcon.Kind.SAVE, "#68afd1", size);
      case VERIFY -> SidebarToolIcon.of(SidebarToolIcon.Kind.APPLY, "#6ebd79", size);
      default -> null;
    };
  }

  private static Region glyphFor(Kind kind, double size) {
    String white = "#f4fbff";
    Region glyph = switch (kind) {
      case COPY -> CssIcon.copy(white);
      case PASTE -> CssIcon.contentPaste(white);
      case HISTORY -> CssIcon.timeline(white);
      case DUPLICATE -> CssIcon.controlPointDuplicate(white);
      case ADD_ALL -> CssIcon.plusBold(white);
      case SAVE_CLIP -> CssIcon.libraryAdd(white);
      case LOAD_CLIP -> CssIcon.input(white);
      case CHARACTER_SLOT -> CssIcon.emojiPeople(white);
      case PREVIOUS -> CssIcon.fastRewind(white);
      case NEXT -> CssIcon.fastForward(white);
      case FOCUS -> CssIcon.myLocation(white);
      case ZOOM_FIT -> CssIcon.zoomOutMap(white);
      case DISTRIBUTE -> CssIcon.formatAlignJustify(white);
      case REVERSE -> CssIcon.swapHoriz(white);
      case STRETCH -> CssIcon.openInFull(white);
      case COMPRESS -> CssIcon.closeFullscreen(white);
      case RIPPLE -> CssIcon.link(white);
      case COMPACT_EXPORT -> CssIcon.folderZip(white);
      case REWIND -> CssIcon.skipPrevious(white);
      case PLAY -> CssIcon.play(white);
      case PAUSE -> CssIcon.pause(white);
      case STOP -> CssIcon.stop(white);
      case UNDO -> CssIcon.undo(white);
      case REDO -> CssIcon.redo(white);
      case FIT_DURATION -> CssIcon.rectSelect(white);
      case LOOP, SYNC -> CssIcon.loop(white);
      case LOOP_IN -> CssIcon.verticalAlignBottom(white);
      case LOOP_OUT -> CssIcon.verticalAlignTop(white);
      case CLEAR -> CssIcon.clearX(white);
      case PRESET -> CssIcon.folder(white);
      case SNAP -> CssIcon.grid4x4(white);
      case RUNTIME_PREVIEW -> CssIcon.movie(white);
      case VIEWPORT_STABILIZE -> CssIcon.myLocation(white);
      case AUTO_KEY -> CssIcon.fiberSmartRecord(white);
      case RECORD_GIF -> CssIcon.icon("M12 4 A8 8 0 1 1 11.99 4 Z", "#ff837d", 14);
      case SNAP_GRID -> CssIcon.borderAll(white);
      case SNAP_ENTITY -> CssIcon.joinInner(white);
      case ORBIT -> CssIcon.threeSixty(white);
      case ORBIT_ALIGN -> CssIcon.explore(white);
      case HELP -> CssIcon.speech(white);
      case ADD_AUDIO_CUE -> CssIcon.icon("M10 3 H19 V16 A3 3 0 1 1 17 13 V7 H12 V18 A3 3 0 1 1 10 15 Z", white,14);
      case ADD_EXPRESSION_CUE -> CssIcon.theater(white);
      case MANAGE_EVENTS, VERIFY -> CssIcon.list(white);
      case REGISTER -> CssIcon.save(white);
      case COPY_CODE -> CssIcon.copy(white);
    };
    double base = Math.max(1, Math.max(glyph.getPrefWidth(), glyph.getPrefHeight()));
    double scale = size / base;
    glyph.setScaleX(scale);
    glyph.setScaleY(scale);
    return glyph;
  }

  private static Region shellFor(Kind kind, Palette palette, double size) {
    return switch (kind) {
      case PREVIOUS, NEXT, REWIND, PLAY, PAUSE, STOP, UNDO, REDO, LOOP, CLEAR ->
          transportShell(palette, size);
      case COPY, PASTE, HISTORY, DUPLICATE, ADD_ALL, SAVE_CLIP, LOAD_CLIP,
          COMPACT_EXPORT, PRESET, REGISTER, COPY_CODE -> documentShell(palette, size);
      case FOCUS, ZOOM_FIT, DISTRIBUTE, REVERSE, STRETCH, COMPRESS, FIT_DURATION,
          SNAP, VIEWPORT_STABILIZE, SNAP_GRID, SNAP_ENTITY, ORBIT, ORBIT_ALIGN ->
          transformShell(palette, size);
      case RIPPLE, LOOP_IN, LOOP_OUT, RUNTIME_PREVIEW, AUTO_KEY, RECORD_GIF, SYNC ->
          timelineShell(palette, size);
      case CHARACTER_SLOT, HELP, ADD_AUDIO_CUE, ADD_EXPRESSION_CUE, MANAGE_EVENTS, VERIFY ->
          eventShell(palette, size);
    };
  }

  private static StackPane transportShell(Palette palette, double size) {
    Circle chrome = new Circle(size * 0.42, metal());
    chrome.setStroke(Color.web("#364650"));
    chrome.setStrokeWidth(Math.max(0.6, size * 0.035));
    Circle glass = new Circle(size * 0.355, glassOrb(palette));
    glass.setStroke(Color.rgb(239, 250, 255, 0.82));
    glass.setStrokeWidth(Math.max(0.45, size * 0.025));
    glass.setEffect(new InnerShadow(size * 0.055, 0, size * 0.025, Color.rgb(2, 19, 31, 0.76)));
    Ellipse shine = new Ellipse(size * 0.16, size * 0.072);
    shine.setTranslateX(-size * 0.07);
    shine.setTranslateY(-size * 0.18);
    shine.setFill(Color.rgb(255, 255, 255, 0.54));
    return shell(size, chrome, glass, shine);
  }

  private static StackPane documentShell(Palette palette, double size) {
    Rectangle rear = rounded(size * 0.56, size * 0.67, size * 0.08, metal(), Color.web("#455863"));
    rear.setTranslateX(-size * 0.11);
    rear.setTranslateY(-size * 0.07);
    Rectangle page = rounded(size * 0.66, size * 0.72, size * 0.08,
        glassPanel(palette.light(), palette.dark()), Color.rgb(226, 246, 255, 0.82));
    page.setTranslateX(size * 0.07);
    page.setTranslateY(size * 0.05);
    page.setEffect(new InnerShadow(size * 0.05, Color.rgb(2, 22, 36, 0.62)));
    Polygon fold = new Polygon(0, 0, size * 0.17, 0, size * 0.17, size * 0.17);
    fold.setFill(metal());
    fold.setTranslateX(size * 0.24);
    fold.setTranslateY(-size * 0.22);
    return shell(size, rear, page, fold);
  }

  private static StackPane transformShell(Palette palette, double size) {
    Rectangle chassis = rounded(size * 0.80, size * 0.80, size * 0.12,
        metal(), Color.web("#3c4d57"));
    Rectangle glass = rounded(size * 0.68, size * 0.68, size * 0.09,
        glassPanel(palette.light(), palette.dark()), Color.rgb(226, 246, 255, 0.72));
    glass.setEffect(new InnerShadow(size * 0.055, Color.rgb(3, 23, 37, 0.67)));
    Group corners = new Group();
    double offset = size * 0.29;
    for (double x : new double[] {-offset, offset}) {
      for (double y : new double[] {-offset, offset}) {
        Circle rivet = new Circle(size * 0.038, metal());
        rivet.setTranslateX(x);
        rivet.setTranslateY(y);
        corners.getChildren().add(rivet);
      }
    }
    return shell(size, chassis, glass, corners);
  }

  private static StackPane timelineShell(Palette palette, double size) {
    Rectangle chassis = rounded(size * 0.86, size * 0.70, size * 0.11,
        metal(), Color.web("#394b56"));
    Rectangle panel = rounded(size * 0.76, size * 0.60, size * 0.08,
        glassPanel(palette.light(), palette.dark()), Color.rgb(226, 246, 255, 0.72));
    panel.setEffect(new InnerShadow(size * 0.05, Color.rgb(2, 20, 33, 0.7)));
    Line trackA = shellLine(-size * 0.27, size * 0.20, size * 0.27, size * 0.20,
        Color.rgb(232, 250, 255, 0.56), Math.max(0.5, size * 0.025));
    Line playhead = shellLine(0, -size * 0.27, 0, size * 0.27,
        palette.glow(), Math.max(0.6, size * 0.032));
    return shell(size, chassis, panel, trackA, playhead);
  }

  private static StackPane eventShell(Palette palette, double size) {
    Rectangle frame = rounded(size * 0.84, size * 0.74, size * 0.12,
        metal(), Color.web("#40515b"));
    Rectangle card = rounded(size * 0.73, size * 0.63, size * 0.09,
        glassPanel(palette.light(), palette.dark()), Color.rgb(231, 248, 255, 0.78));
    card.setEffect(new InnerShadow(size * 0.05, Color.rgb(2, 22, 35, 0.68)));
    Rectangle title = rounded(size * 0.53, size * 0.09, size * 0.04,
        new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, palette.mid().brighter()), new Stop(1, palette.dark())), Color.TRANSPARENT);
    title.setTranslateY(-size * 0.23);
    return shell(size, frame, card, title);
  }

  private static StackPane shell(double size, javafx.scene.Node... nodes) {
    StackPane pane = new StackPane(nodes);
    pane.setMinSize(size, size);
    pane.setPrefSize(size, size);
    pane.setMaxSize(size, size);
    pane.setMouseTransparent(true);
    pane.getStyleClass().add("jvn-puppeteer-bespoke-shell");
    pane.setEffect(new DropShadow(Math.max(1.4, size * 0.09), 0, Math.max(0.6, size * 0.045),
        Color.rgb(0, 0, 0, 0.82)));
    return pane;
  }

  private static Rectangle rounded(double width, double height, double arc,
      javafx.scene.paint.Paint fill, javafx.scene.paint.Paint stroke) {
    Rectangle rectangle = new Rectangle(width, height);
    rectangle.setArcWidth(arc * 2);
    rectangle.setArcHeight(arc * 2);
    rectangle.setFill(fill);
    rectangle.setStroke(stroke);
    rectangle.setStrokeWidth(Math.max(0.45, width * 0.035));
    return rectangle;
  }

  private static Line shellLine(double x1, double y1, double x2, double y2,
      Color color, double width) {
    Line line = new Line(x1, y1, x2, y2);
    line.setStroke(color);
    line.setStrokeWidth(width);
    line.setStrokeLineCap(StrokeLineCap.ROUND);
    return line;
  }

  private static LinearGradient metal() {
    return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.WHITE), new Stop(0.18, Color.web("#eaf1f4")),
        new Stop(0.48, Color.web("#83929c")), new Stop(0.70, Color.web("#dbe3e7")),
        new Stop(1, Color.web("#53616a")));
  }

  private static LinearGradient glassPanel(Color top, Color bottom) {
    return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, top.brighter()), new Stop(0.22, top),
        new Stop(0.48, bottom.brighter()), new Stop(1, bottom.darker()));
  }

  private static RadialGradient glassOrb(Palette palette) {
    return new RadialGradient(-35, 0.28, 0.35, 0.30, 0.65, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.WHITE), new Stop(0.2, palette.light()),
        new Stop(0.52, palette.mid()), new Stop(1, palette.dark().darker()));
  }

  private static Palette paletteFor(Kind kind) {
    return switch (kind) {
      case ADD_ALL, SAVE_CLIP, LOAD_CLIP, PLAY, REGISTER, ADD_AUDIO_CUE, ADD_EXPRESSION_CUE, VERIFY ->
          palette("#83dca9", "#23875f", "#0d3d31", "#62e2a2");
      case PREVIOUS, NEXT, FOCUS, ZOOM_FIT, REWIND, UNDO, REDO, SYNC, COPY_CODE,
          VIEWPORT_STABILIZE -> palette("#91d8ff", "#318ec2", "#123d5c", "#62c8ff");
      case DISTRIBUTE, REVERSE, STRETCH, COMPRESS -> palette("#c7b7f7", "#715aa8", "#30264f", "#ac8cff");
      case RIPPLE, COMPACT_EXPORT, PAUSE, LOOP, LOOP_IN, LOOP_OUT, PRESET ->
          palette("#ffd277", "#bd7621", "#51300f", "#ffc04f");
      case RUNTIME_PREVIEW, ORBIT, ORBIT_ALIGN -> palette("#d0b3ff", "#7954ad", "#34224f", "#b98cff");
      case STOP, CLEAR, AUTO_KEY, RECORD_GIF -> palette("#ffaaa5", "#b84747", "#521d22", "#ff716d");
      default -> palette("#c9d8e5", "#60788d", "#263744", "#a9d7f5");
    };
  }

  private static Palette palette(String light, String mid, String dark, String glow) {
    return new Palette(Color.web(light), Color.web(mid), Color.web(dark), Color.web(glow));
  }
}
