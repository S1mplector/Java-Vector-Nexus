package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Specialized vector icon family for Layered Image Visualizer commands. */
public final class LayeredVisualizerIcon extends Pane {
  public enum Kind {
    REFRESH_SET,
    REFRESH_ERROR,
    NEW_SET,
    LOAD_PRESET,
    DELETE_PRESET,
    SAVE_PRESET,
    CHOOSE_FOLDER,
    REVEAL_FOLDER,
    COPY_CHARPRESET,
    SAVE_CHARPRESET,
    CHARPRESET_AS,
    EXPORT_BUNDLE,
    EXPORT_PNG,
    PNG_AS,
    SAVE_SETUP,
    SETUP_AS,
    IMPORT_SETUP,
    RANDOMIZE,
    DEFAULTS,
    CLEAR_LAYERS,
    PREVIOUS_VARIANT,
    NEXT_VARIANT,
    RESET_VIEW,
    FULLSCREEN,
    EXIT_FULLSCREEN,
    HIDE_CONTROLS,
    SHOW_CONTROLS
  }

  private static final double SIZE = 22.0;
  private static final Color SILVER = Color.web("#bac5cb");
  private static final Color BRIGHT = Color.web("#f6fafb");
  private static final Color DARK = Color.web("#182126");
  private static final Color ORANGE = Color.web("#f3a34d");
  private static final Color BLUE = Color.web("#78bce8");
  private static final Color GREEN = Color.web("#83cf75");
  private static final Color PURPLE = Color.web("#c190e6");
  private static final Color RED = Color.web("#ee8197");

  private final Kind kind;

  private LayeredVisualizerIcon(Kind requestedKind) {
    kind = requestedKind == null ? Kind.REFRESH_SET : requestedKind;
    setMinSize(SIZE, SIZE);
    setPrefSize(SIZE, SIZE);
    setMaxSize(SIZE, SIZE);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().add("layered-visualizer-command-icon");

    Group artwork = switch (kind) {
      case REFRESH_SET -> refreshSet(false);
      case REFRESH_ERROR -> refreshSet(true);
      case NEW_SET -> newSet();
      case LOAD_PRESET -> presetAction(Action.LOAD);
      case DELETE_PRESET -> presetAction(Action.DELETE);
      case SAVE_PRESET -> presetAction(Action.SAVE);
      case CHOOSE_FOLDER -> folderAction(false);
      case REVEAL_FOLDER -> folderAction(true);
      case COPY_CHARPRESET -> charpresetAction(Action.COPY);
      case SAVE_CHARPRESET -> charpresetAction(Action.SAVE);
      case CHARPRESET_AS -> charpresetAction(Action.AS);
      case EXPORT_BUNDLE -> exportBundle();
      case EXPORT_PNG -> pngAction(false);
      case PNG_AS -> pngAction(true);
      case SAVE_SETUP -> setupAction(Action.SAVE);
      case SETUP_AS -> setupAction(Action.AS);
      case IMPORT_SETUP -> setupAction(Action.LOAD);
      case RANDOMIZE -> randomize();
      case DEFAULTS -> defaults();
      case CLEAR_LAYERS -> clearLayers();
      case PREVIOUS_VARIANT -> cycleVariant(false);
      case NEXT_VARIANT -> cycleVariant(true);
      case RESET_VIEW -> viewFrame(false);
      case FULLSCREEN -> fullscreen(false);
      case EXIT_FULLSCREEN -> fullscreen(true);
      case HIDE_CONTROLS -> sidebar(true);
      case SHOW_CONTROLS -> sidebar(false);
    };
    artwork.setEffect(new DropShadow(1.3, 0, .7, Color.rgb(10, 26, 36, 0.45)));
    getChildren().setAll(artwork);
  }

  public static LayeredVisualizerIcon of(Kind kind) {
    return new LayeredVisualizerIcon(kind);
  }

  public Kind kind() {
    return kind;
  }

  private static Group refreshSet(boolean error) {
    Color accent = error ? RED : Color.web("#75d0e8");
    Group stack = layerStack(accent, 0.80);
    SVGPath arrow = stroked(
        "M17.8 7.4 A7 7 0 0 0 6.1 5.3 L4 7.3 M6.1 5.3 L6.1 2.5 "
            + "M4.2 14.2 A7 7 0 0 0 15.9 16.3 L18 14.3 M15.9 16.3 L15.9 19.1",
        accent, 1.55);
    if (!error) return new Group(stack, arrow);
    SVGPath cross = stroked("M8.6 8.6 L13.4 13.4 M13.4 8.6 L8.6 13.4", BRIGHT, 1.5);
    return new Group(stack, arrow, cross);
  }

  private static Group newSet() {
    Group stack = layerStack(GREEN, 1.0);
    Circle badge = badge(17.2, 5.0, 4.0, Color.web("#21362a"), GREEN);
    SVGPath plus = stroked("M17.2 2.8 V7.2 M15 5 H19.4", BRIGHT, 1.55);
    return new Group(stack, badge, plus);
  }

  private static Group presetAction(Action action) {
    Color accent = switch (action) {
      case DELETE -> RED;
      case LOAD -> BLUE;
      default -> GREEN;
    };
    Group card = presetCard(accent);
    Group mark = switch (action) {
      case LOAD -> downArrow(16.8, 12.2, BLUE);
      case DELETE -> cross(16.4, 15.8, RED);
      default -> saveLamp(16.6, 14.9, GREEN);
      case COPY, AS -> new Group();
    };
    return new Group(card, mark);
  }

  private static Group folderAction(boolean reveal) {
    SVGPath rearLayer = filled("M3.2 7.4 L8.6 5.2 L14 7.4 L8.6 9.7 Z", Color.web("#59656a"));
    rearLayer.setStroke(SILVER.deriveColor(0, 1, 1, 0.7));
    SVGPath folder = filled(
        "M2.2 7.3 Q2.2 5.8 3.8 5.8 H8 L9.7 7.5 H18.5 Q20 7.5 20 9 V18.2 "
            + "Q20 19.7 18.5 19.7 H3.7 Q2.2 19.7 2.2 18.2 Z",
        metallic(ORANGE));
    folder.setStroke(Color.web("#ffe2ad"));
    folder.setStrokeWidth(1.0);
    if (!reveal) {
      SVGPath layers = stroked("M6 11 L10.5 9.4 L15 11 L10.5 12.7 Z M6 14 L10.5 12.4 L15 14 L10.5 15.7 Z", DARK, 1.0);
      return new Group(rearLayer, folder, layers);
    }
    Circle portal = badge(16.8, 14.8, 4.0, Color.web("#192b38"), BLUE);
    SVGPath arrow = stroked("M14.5 17.1 L19.2 12.4 M16.2 12.4 H19.2 V15.4", BRIGHT, 1.3);
    return new Group(rearLayer, folder, portal, arrow);
  }

  private static Group charpresetAction(Action action) {
    Color accent = PURPLE;
    Group card = characterCard(accent);
    Group mark = switch (action) {
      case COPY -> copyBadge(16.7, 15.8, accent);
      case SAVE -> downArrow(16.7, 13.0, accent);
      case AS -> outwardArrow(16.5, 14.7, accent);
      default -> new Group();
    };
    return new Group(card, mark);
  }

  private static Group exportBundle() {
    Group picture = pictureCard(1.6, 5.4, ORANGE);
    Group setup = setupCard(8.2, 2.5, GREEN);
    SVGPath bracket = stroked("M4.2 18.2 V20 H18.6 V18.2", ORANGE, 1.4);
    Group arrow = downArrow(11.4, 14.4, BRIGHT);
    return new Group(picture, setup, bracket, arrow);
  }

  private static Group pngAction(boolean saveAs) {
    Group picture = pictureCard(2.2, 3.2, BLUE);
    Group mark = saveAs
        ? outwardArrow(16.8, 15.5, BLUE)
        : downArrow(16.6, 13.1, BLUE);
    return new Group(picture, mark);
  }

  private static Group setupAction(Action action) {
    Color accent = action == Action.LOAD ? ORANGE : GREEN;
    Group card = setupCard(2.5, 2.6, accent);
    Group mark = switch (action) {
      case LOAD -> inwardArrow(16.7, 15.5, ORANGE);
      case AS -> outwardArrow(16.7, 15.5, GREEN);
      default -> downArrow(16.7, 13.0, GREEN);
      case COPY, DELETE -> new Group();
    };
    return new Group(card, mark);
  }

  private static Group randomize() {
    Group stack = layerStack(ORANGE, 0.72);
    SVGPath shuffle = stroked(
        "M3 6.2 H6.2 Q8.2 6.2 9.8 9 L12.2 13 Q13.8 15.8 16 15.8 H19 "
            + "M16.7 13.6 L19 15.8 L16.7 18 M3 15.8 H5.8 Q7.6 15.8 9 13.4 "
            + "L12.6 7.9 Q13.8 6.2 16 6.2 H19 M16.7 4 L19 6.2 L16.7 8.4",
        BRIGHT, 1.35);
    return new Group(stack, shuffle);
  }

  private static Group defaults() {
    Group stack = layerStack(GREEN, 0.92);
    SVGPath origin = filled("M11 3.1 L16.1 7.2 V15.9 H12.9 V11.4 H9.1 V15.9 H5.9 V7.2 Z", metallic(GREEN));
    origin.setStroke(BRIGHT);
    origin.setStrokeWidth(0.8);
    return new Group(stack, origin);
  }

  private static Group clearLayers() {
    Group stack = layerStack(RED, 0.60);
    Circle badge = badge(15.8, 15.6, 5.0, Color.web("#371d25"), RED);
    SVGPath mark = stroked("M13.3 13.1 L18.3 18.1 M18.3 13.1 L13.3 18.1", BRIGHT, 1.55);
    return new Group(stack, badge, mark);
  }

  private static Group cycleVariant(boolean next) {
    Group stack = layerStack(BLUE, 0.78);
    String path = next
        ? "M7.3 15.9 A6.6 6.6 0 0 0 17.6 9.1 M15.1 9.3 L17.8 8.9 L17.5 11.6"
        : "M14.7 15.9 A6.6 6.6 0 0 1 4.4 9.1 M6.9 9.3 L4.2 8.9 L4.5 11.6";
    SVGPath arrow = stroked(path, BRIGHT, 1.6);
    return new Group(stack, arrow);
  }

  private static Group viewFrame(boolean unused) {
    Group stack = layerStack(BLUE, 0.88);
    SVGPath frame = stroked(
        "M3 7 V3 H7 M15 3 H19 V7 M19 15 V19 H15 M7 19 H3 V15",
        BRIGHT, 1.55);
    Circle center = new Circle(11, 11, 1.25, ORANGE);
    center.setStroke(Color.web("#ffe1bd"));
    center.setStrokeWidth(0.55);
    return new Group(stack, frame, center);
  }

  private static Group fullscreen(boolean exit) {
    Group stack = layerStack(ORANGE, 0.55);
    String path = exit
        ? "M3 8 H8 V3 M14 3 V8 H19 M19 14 H14 V19 M8 19 V14 H3"
        : "M8 3 H3 V8 M14 3 H19 V8 M19 14 V19 H14 M8 19 H3 V14";
    SVGPath corners = stroked(path, exit ? RED : Color.web("#ffd17d"), 1.7);
    return new Group(stack, corners);
  }

  private static Group sidebar(boolean hide) {
    Rectangle viewport = roundedRect(2.4, 3, 17.2, 16, 2.1, paperGlass(BLUE), Color.web("#52697b"), .85);
    Rectangle controls = roundedRect(
        hide ? 3.8 : 12.6, 4.5, 5.4, 13, 1.0,
        paperGlass(ORANGE), Color.web("#96662b"), 0.9);
    SVGPath layers = stroked(
        hide
            ? "M5 8 L7.7 6.8 L10.4 8 L7.7 9.2 Z M5 11 L7.7 9.8 L10.4 11 L7.7 12.2 Z"
            : "M11.6 8 L14.3 6.8 L17 8 L14.3 9.2 Z M11.6 11 L14.3 9.8 L17 11 L14.3 12.2 Z",
        SILVER, 0.7);
    SVGPath arrow = stroked(
        hide ? "M12.3 8 L15.4 11 L12.3 14" : "M9.7 8 L6.6 11 L9.7 14",
        BRIGHT, 1.55);
    return new Group(viewport, controls, layers, arrow);
  }

  private static Group layerStack(Color accent, double opacity) {
    SVGPath rear = filled("M3.2 6.1 L11 2.6 L18.8 6.1 L11 9.6 Z", metallic(accent.deriveColor(0,.55,.9,1)));
    rear.setStroke(Color.web("#3c5669"));
    rear.setStrokeWidth(0.9);
    SVGPath middle = filled("M3.2 10.4 L11 6.9 L18.8 10.4 L11 13.9 Z", metallic(accent.deriveColor(0,.7,1,1)));
    middle.setStroke(accent.deriveColor(0, .8, .45, 1));
    middle.setStrokeWidth(1.05);
    SVGPath front = filled("M3.2 14.7 L11 11.2 L18.8 14.7 L11 18.3 Z", metallic(accent));
    front.setStroke(accent.deriveColor(0, .8, .4, 1));
    front.setStrokeWidth(1.15);
    Line shine = line(6.3, 13.35, 11, 11.35, Color.rgb(255, 255, 255, opacity * 0.55), 0.7);
    return new Group(rear, middle, front, shine);
  }

  private static Group presetCard(Color accent) {
    Rectangle card = roundedRect(3, 2.8, 14.8, 16.6, 2.2, paperGlass(accent), Color.web("#52697b"), .85);
    SVGPath layers = stroked(
        "M5.5 8 L10.4 5.8 L15.3 8 L10.4 10.2 Z "
            + "M5.5 11.2 L10.4 9 L15.3 11.2 L10.4 13.4 Z",
        Color.web("#426985"), 1.0);
    Line label = line(6, 16.2, 12.2, 16.2, accent, 1.0);
    return new Group(card, layers, label);
  }

  private static Group characterCard(Color accent) {
    Rectangle card = roundedRect(2.5, 2.4, 15.4, 17.2, 2.2, paperGlass(accent), Color.web("#52697b"), .85);
    Circle head = new Circle(10.2, 7.3, 2.55, Color.web("#566f91"));
    SVGPath bust = filled("M5.8 15.8 Q6.3 11.2 10.2 11.2 Q14.1 11.2 14.6 15.8 Z", Color.web("#7b92af"));
    Line layerOne = line(6.4, 13.3, 14, 13.3, accent, 0.95);
    Line layerTwo = line(6.1, 15.4, 14.3, 15.4, BRIGHT, 0.75);
    return new Group(card, head, bust, layerOne, layerTwo);
  }

  private static Group pictureCard(double x, double y, Color accent) {
    Rectangle card = roundedRect(x, y, 15.7, 13.7, 2.0, paperGlass(accent), Color.web("#52697b"), .85);
    Circle sun = new Circle(x + 11.9, y + 3.7, 1.25, Color.web("#ffd477"));
    SVGPath landscape = filled(
        "M" + (x + 1.8) + " " + (y + 11.7) + " L" + (x + 5.4) + " " + (y + 7.6)
            + " L" + (x + 8.0) + " " + (y + 10.0) + " L" + (x + 10.4) + " " + (y + 7.0)
            + " L" + (x + 13.9) + " " + (y + 11.7) + " Z",
        metallic(Color.web("#52996d")));
    Line layer = line(x + 3.0, y + 12.5, x + 12.8, y + 12.5, BRIGHT, 0.65);
    return new Group(card, sun, landscape, layer);
  }

  private static Group setupCard(double x, double y, Color accent) {
    Rectangle card = roundedRect(x, y, 15.2, 16.7, 2.1, paperGlass(accent), Color.web("#52697b"), .85);
    Line one = line(x + 3, y + 5, x + 12.2, y + 5, Color.web("#55717d"), 1.0);
    Line two = line(x + 3, y + 8.5, x + 12.2, y + 8.5, Color.web("#55717d"), 1.0);
    Line three = line(x + 3, y + 12, x + 12.2, y + 12, Color.web("#55717d"), 1.0);
    Circle knobOne = new Circle(x + 6, y + 5, 1.3, accent);
    Circle knobTwo = new Circle(x + 10, y + 8.5, 1.3, accent);
    Circle knobThree = new Circle(x + 7.8, y + 12, 1.3, accent);
    return new Group(card, one, two, three, knobOne, knobTwo, knobThree);
  }

  private static Group copyBadge(double x, double y, Color accent) {
    Rectangle rear = roundedRect(x - 3.5, y - 3.6, 5.8, 6.2, 1.0, DARK, accent.deriveColor(0, 1, 0.7, 1), 0.8);
    Rectangle front = roundedRect(x - 1.6, y - 2.2, 5.8, 6.2, 1.0, Color.web("#242a2e"), BRIGHT, 0.85);
    return new Group(rear, front);
  }

  private static Group saveLamp(double x, double y, Color accent) {
    Rectangle disk = roundedRect(x - 3.6, y - 3.6, 7.2, 7.2, 1.3, Color.web("#1b2c20"), accent, 1.0);
    Rectangle label = roundedRect(x - 1.8, y - 2.7, 3.6, 2.0, 0.4, BRIGHT, BRIGHT, 0);
    Circle lamp = new Circle(x, y + 1.5, 1.1, accent);
    return new Group(disk, label, lamp);
  }

  private static Group downArrow(double x, double y, Color accent) {
    SVGPath arrow = filled(
        "M" + (x - 1.2) + " " + (y - 4.5) + " H" + (x + 1.2) + " V" + y
            + " H" + (x + 3.7) + " L" + x + " " + (y + 3.8)
            + " L" + (x - 3.7) + " " + y + " H" + (x - 1.2) + " Z",
        metallic(accent));
    arrow.setStroke(BRIGHT);
    arrow.setStrokeWidth(0.65);
    Line tray = line(x - 3.8, y + 5.0, x + 3.8, y + 5.0, accent, 1.2);
    return new Group(arrow, tray);
  }

  private static Group outwardArrow(double x, double y, Color accent) {
    Circle base = badge(x, y, 4.4, Color.web("#1d282d"), accent);
    SVGPath arrow = stroked(
        "M" + (x - 2.1) + " " + (y + 2.1) + " L" + (x + 2.2) + " " + (y - 2.2)
            + " M" + (x - 0.7) + " " + (y - 2.2) + " H" + (x + 2.2)
            + " V" + (y + 0.7),
        BRIGHT, 1.25);
    return new Group(base, arrow);
  }

  private static Group inwardArrow(double x, double y, Color accent) {
    Circle base = badge(x, y, 4.4, Color.web("#302719"), accent);
    SVGPath arrow = stroked(
        "M" + (x + 2.1) + " " + (y - 2.1) + " L" + (x - 2.2) + " " + (y + 2.2)
            + " M" + (x + 0.7) + " " + (y + 2.2) + " H" + (x - 2.2)
            + " V" + (y - 0.7),
        BRIGHT, 1.25);
    return new Group(base, arrow);
  }

  private static Group cross(double x, double y, Color accent) {
    Circle base = badge(x, y, 4.1, Color.web("#351e24"), accent);
    SVGPath mark = stroked(
        "M" + (x - 2) + " " + (y - 2) + " L" + (x + 2) + " " + (y + 2)
            + " M" + (x + 2) + " " + (y - 2) + " L" + (x - 2) + " " + (y + 2),
        BRIGHT, 1.35);
    return new Group(base, mark);
  }

  private static Circle badge(double x, double y, double radius, Color fill, Color stroke) {
    Circle badge = new Circle(x, y, radius, fill);
    badge.setStroke(stroke);
    badge.setStrokeWidth(1.0);
    return badge;
  }

  private static Rectangle roundedRect(
      double x, double y, double width, double height, double radius,
      javafx.scene.paint.Paint fill, Color stroke, double strokeWidth) {
    Rectangle rectangle = new Rectangle(x, y, width, height);
    rectangle.setArcWidth(radius * 2);
    rectangle.setArcHeight(radius * 2);
    rectangle.setFill(fill);
    rectangle.setStroke(stroke);
    rectangle.setStrokeWidth(strokeWidth);
    return rectangle;
  }

  private static SVGPath stroked(String content, Color color, double width) {
    SVGPath path = new SVGPath();
    path.setContent(content);
    path.setFill(Color.TRANSPARENT);
    path.setStroke(color);
    path.setStrokeWidth(width);
    path.setStrokeLineCap(StrokeLineCap.ROUND);
    path.setStrokeLineJoin(StrokeLineJoin.ROUND);
    return path;
  }

  private static SVGPath filled(String content, javafx.scene.paint.Paint fill) {
    SVGPath path = new SVGPath();
    path.setContent(content);
    path.setFill(fill);
    path.setStrokeLineCap(StrokeLineCap.ROUND);
    path.setStrokeLineJoin(StrokeLineJoin.ROUND);
    return path;
  }

  private static Line line(
      double startX, double startY, double endX, double endY, Color color, double width) {
    Line line = new Line(startX, startY, endX, endY);
    line.setStroke(color);
    line.setStrokeWidth(width);
    line.setStrokeLineCap(StrokeLineCap.ROUND);
    return line;
  }

  private static LinearGradient paperGlass(Color accent) {
    return new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,
        new Stop(0,Color.web("#fbfeff")), new Stop(.42,accent.interpolate(Color.WHITE,.75)),
        new Stop(.46,accent.interpolate(Color.WHITE,.55)), new Stop(1,accent.deriveColor(0,.45,.85,1)));
  }

  private static LinearGradient metallic(Color accent) {
    return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, accent.deriveColor(0, 0.65, 1.35, 1)),
        new Stop(0.42, accent.deriveColor(0, 0.88, 1.12, 1)),
        new Stop(1, accent.deriveColor(0, 1.05, 0.64, 1)));
  }

  private enum Action {
    LOAD,
    SAVE,
    DELETE,
    COPY,
    AS
  }
}
