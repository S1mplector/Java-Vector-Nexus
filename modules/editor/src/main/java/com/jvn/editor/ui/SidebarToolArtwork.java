package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Rotate;

/** Bespoke Windows 7 shell-style vector artwork for the shared sidebar command set. */
final class SidebarToolArtwork {
  private static final double SIZE = 20.0;

  private SidebarToolArtwork() {}

  static Pane remove(String requested) {
    return glassCommand("#e46b78", "minus", requested);
  }

  static Pane previous(String requested) {
    return glassCommand("#51b9e5", "left", requested);
  }

  static Pane next(String requested) {
    return glassCommand("#51b9e5", "right", requested);
  }

  static Pane up(String requested) {
    return glassCommand("#55bde4", "up", requested);
  }

  static Pane down(String requested) {
    return glassCommand("#55bde4", "down", requested);
  }

  static Pane close(String requested) {
    return glassCommand("#df5a66", "close", requested);
  }

  private static Pane glassCommand(String fallback, String symbol, String requested) {
    Color accent = accent(requested, fallback);
    Pane art = canvas();
    Circle chrome = new Circle(10, 10, 8.9, metal());
    chrome.setStroke(Color.web("#35434d"));
    chrome.setStrokeWidth(0.72);
    Circle glass = new Circle(10, 10, 7.55, radial(accent));
    glass.setStroke(accent.deriveColor(0, 0.76, 0.72, 1));
    glass.setStrokeWidth(0.7);
    glass.setEffect(new InnerShadow(1.25, 0, 0.65, Color.rgb(5, 24, 38, 0.7)));
    Ellipse shine = new Ellipse(8.25, 6.4, 4.75, 2.15);
    shine.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.rgb(255, 255, 255, 0.76)),
        new Stop(1, Color.rgb(255, 255, 255, 0.04))));

    Node shadow;
    Node glyph;
    if ("close".equals(symbol)) {
      Group shadowLines = cross(Color.rgb(70, 6, 11, 0.74), 2.9);
      shadowLines.setTranslateY(0.55);
      shadow = shadowLines;
      glyph = cross(Color.web("#fffafa"), 1.72);
    } else if ("minus".equals(symbol)) {
      Rectangle shadowBar = roundedRect(5.1, 9.1, 9.8, 3.25,
          Color.rgb(71, 8, 16, 0.72), Color.TRANSPARENT, 0, 1.4);
      shadowBar.setTranslateY(0.55);
      Rectangle bar = roundedRect(5.0, 8.8, 10.0, 2.75, whiteEnamel(),
          Color.rgb(80, 14, 20, 0.68), 0.45, 1.25);
      shadow = shadowBar;
      glyph = bar;
    } else {
      String path = switch (symbol) {
        case "left" -> "M11.6 4.8 L5.0 10 L11.6 15.2 V12.0 H15.6 V8.0 H11.6 Z";
        case "right" -> "M8.4 4.8 L15.0 10 L8.4 15.2 V12.0 H4.4 V8.0 H8.4 Z";
        case "up" -> "M4.8 11.6 L10 5.0 L15.2 11.6 H12.0 V15.6 H8.0 V11.6 Z";
        default -> "M4.8 8.4 L10 15.0 L15.2 8.4 H12.0 V4.4 H8.0 V8.4 Z";
      };
      SVGPath glyphShadow = svg(path, Color.rgb(3, 34, 55, 0.78), Color.TRANSPARENT, 0);
      glyphShadow.setTranslateY(0.6);
      SVGPath glyphMain = svg(path, whiteEnamel(), Color.rgb(17, 68, 92, 0.78), 0.48);
      shadow = glyphShadow;
      glyph = glyphMain;
    }
    art.getChildren().addAll(chrome, glass, shine, shadow, glyph);
    return finish(art);
  }

  static Pane sort(String requested) {
    Color accent = accent(requested, "#e89b45");
    Pane art = canvas();
    Rectangle plate = roundedRect(2.6, 2.8, 14.8, 14.4,
        glassPanel(Color.web("#f7fbfd"), Color.web("#8299a8")),
        Color.web("#354854"), 0.7, 2.2);
    plate.setEffect(new InnerShadow(1.0, 0, 0.6, Color.rgb(0, 24, 36, 0.5)));
    art.getChildren().add(plate);
    double[] widths = {9.4, 7.0, 4.5};
    for (int i = 0; i < widths.length; i++) {
      Rectangle bar = roundedRect(4.3, 5.0 + i * 4.0, widths[i], 2.05,
          enamel(accent), accent.darker(), 0.4, 0.8);
      art.getChildren().add(bar);
    }
    SVGPath arrow = svg("M14.0 4.3 H16.3 V12.1 H18.0 L15.15 15.6 L12.3 12.1 H14.0 Z",
        enamel(Color.web("#55bce4")), Color.web("#245f79"), 0.4);
    art.getChildren().add(arrow);
    return finish(art);
  }

  static Pane undo(String requested) {
    return curvedArrow(requested, "#9c79dc", false);
  }

  static Pane redo(String requested) {
    return curvedArrow(requested, "#45b6df", true);
  }

  private static Pane curvedArrow(String requested, String fallback, boolean mirror) {
    Color accent = accent(requested, fallback);
    Pane art = canvas();
    SVGPath shadow = svg(
        "M3.2 9.3 L8.4 4.2 V7.0 C14.0 6.9 17.0 10.0 16.9 15.6 C15.1 12.7 12.6 11.2 8.4 11.5 V14.2 Z",
        Color.rgb(8, 24, 46, 0.75), Color.TRANSPARENT, 0);
    shadow.setTranslateY(0.8);
    SVGPath arrow = svg(
        "M3.2 8.7 L8.4 3.6 V6.4 C14.0 6.3 17.0 9.4 16.9 15.0 C15.1 12.1 12.6 10.6 8.4 10.9 V13.6 Z",
        enamel(accent), accent.darker(), 0.68);
    SVGPath shine = svg("M7.0 6.2 C11.0 4.6 14.4 6.4 15.7 9.1",
        Color.TRANSPARENT, Color.rgb(255, 255, 255, 0.72), 0.7);
    Group group = new Group(shadow, arrow, shine);
    if (mirror) group.getTransforms().add(new Rotate(180, 10, 9.5));
    art.getChildren().add(group);
    return finish(art);
  }

  static Pane list(String requested) {
    Color accent = accent(requested, "#62bfe5");
    Pane art = canvas();
    Rectangle page = roundedRect(2.8, 2.0, 14.6, 16.1,
        glassPanel(Color.web("#f9fdff"), Color.web("#9ab0bd")),
        Color.web("#425968"), 0.72, 2.0);
    Rectangle header = roundedRect(4.3, 3.4, 11.6, 2.4, enamel(accent), accent.darker(), 0.4, 0.9);
    art.getChildren().addAll(page, header);
    for (int i = 0; i < 3; i++) {
      double y = 7.3 + i * 3.0;
      Rectangle bullet = roundedRect(4.3, y, 2.0, 2.0, enamel(accent), accent.darker(), 0.3, 0.55);
      Line rule = line(7.4, y + 1.0, 15.1, y + 1.0, Color.web("#486575"), 1.15);
      Line highlight = line(7.4, y + 0.65, 14.6, y + 0.65, Color.rgb(255, 255, 255, 0.42), 0.45);
      art.getChildren().addAll(bullet, rule, highlight);
    }
    return finish(art);
  }

  static Pane search(String requested) {
    Color accent = accent(requested, "#57bee8");
    Pane art = canvas();
    Circle rim = new Circle(8.1, 8.0, 5.75, metal());
    rim.setStroke(Color.web("#314754"));
    rim.setStrokeWidth(0.72);
    Circle lens = new Circle(8.1, 8.0, 4.45, new RadialGradient(
        -30, 0.35, 6.3, 5.9, 6.2, false, CycleMethod.NO_CYCLE,
        new Stop(0, Color.rgb(245, 254, 255, 0.96)),
        new Stop(0.3, accent.brighter()), new Stop(0.72, accent),
        new Stop(1, accent.darker().darker())));
    lens.setStroke(Color.rgb(233, 251, 255, 0.82));
    lens.setStrokeWidth(0.55);
    lens.setEffect(new InnerShadow(1.1, Color.rgb(0, 35, 58, 0.62)));
    Line handleShadow = line(12.0, 12.0, 17.7, 17.7, Color.rgb(0, 18, 27, 0.76), 4.0);
    Line handle = line(11.9, 11.7, 17.4, 17.2, metal(), 2.65);
    Ellipse glint = new Ellipse(6.8, 6.3, 1.9, 1.05);
    glint.setFill(Color.rgb(255, 255, 255, 0.64));
    art.getChildren().addAll(handleShadow, handle, rim, lens, glint);
    return finish(art);
  }

  static Pane grid(String requested) {
    Color accent = accent(requested, "#65b9e5");
    Pane art = canvas();
    Rectangle backing = roundedRect(1.9, 1.9, 16.2, 16.2, metal(), Color.web("#354852"), 0.65, 2.2);
    art.getChildren().add(backing);
    for (int row = 0; row < 2; row++) {
      for (int col = 0; col < 2; col++) {
        Color tileColor = accent.deriveColor((row * 2 + col) * 8, 0.94, 1.0 - row * 0.12, 1);
        Rectangle tile = roundedRect(3.3 + col * 7.1, 3.3 + row * 7.1, 6.0, 6.0,
            enamel(tileColor), tileColor.darker(), 0.45, 1.35);
        Line gloss = line(4.1 + col * 7.1, 4.3 + row * 7.1,
            8.4 + col * 7.1, 4.3 + row * 7.1, Color.rgb(255, 255, 255, 0.62), 0.55);
        art.getChildren().addAll(tile, gloss);
      }
    }
    return finish(art);
  }

  static Pane palette(String requested) {
    Color accent = accent(requested, "#d99b54");
    Pane art = canvas();
    Ellipse palette = new Ellipse(10, 10.2, 8.2, 6.9);
    palette.setFill(new RadialGradient(-25, 0.25, 7, 6, 10, false, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#fff5ce")), new Stop(0.55, Color.web("#e9bd72")),
        new Stop(1, accent.darker())));
    palette.setStroke(Color.web("#704b24"));
    palette.setStrokeWidth(0.7);
    palette.setEffect(new InnerShadow(1.1, Color.rgb(80, 42, 10, 0.52)));
    Circle thumbRim = new Circle(13.8, 11.7, 2.2, metal());
    Circle thumb = new Circle(13.8, 11.7, 1.45, Color.web("#26323a"));
    Color[] paints = {Color.web("#e74e5e"), Color.web("#f4c64e"), Color.web("#55c878"), Color.web("#4daae8")};
    double[][] points = {{6.0, 6.3}, {9.2, 5.0}, {12.4, 5.8}, {5.2, 10.1}};
    art.getChildren().add(palette);
    for (int i = 0; i < paints.length; i++) {
      Circle dab = new Circle(points[i][0], points[i][1], 1.35, radial(paints[i]));
      dab.setStroke(Color.rgb(255, 255, 255, 0.58));
      dab.setStrokeWidth(0.35);
      art.getChildren().add(dab);
    }
    art.getChildren().addAll(thumbRim, thumb);
    return finish(art);
  }

  static Pane download(String requested) {
    Color accent = accent(requested, "#55bce5");
    Pane art = canvas();
    Rectangle tray = roundedRect(2.2, 13.6, 15.6, 4.1, metal(), Color.web("#344752"), 0.72, 1.2);
    Rectangle trayInset = roundedRect(4.1, 14.4, 11.8, 1.5, Color.web("#516775"), Color.web("#e9f4fa"), 0.35, 0.55);
    SVGPath shadow = svg("M7.2 2.5 H12.8 V9.1 H16.2 L10 14.7 L3.8 9.1 H7.2 Z",
        Color.rgb(0, 31, 53, 0.74), Color.TRANSPARENT, 0);
    shadow.setTranslateY(0.7);
    SVGPath arrow = svg("M7.2 1.9 H12.8 V8.5 H16.2 L10 14.1 L3.8 8.5 H7.2 Z",
        enamel(accent), accent.darker(), 0.65);
    SVGPath shine = svg("M8.1 3.0 H11.9 V7.9", Color.TRANSPARENT,
        Color.rgb(255, 255, 255, 0.62), 0.75);
    art.getChildren().addAll(tray, trayInset, shadow, arrow, shine);
    return finish(art);
  }

  static Pane save(String requested) {
    Color accent = accent(requested, "#559fd3");
    Pane art = canvas();
    Rectangle body = roundedRect(2.2, 1.7, 15.6, 16.6, enamel(accent), accent.darker().darker(), 0.75, 2.0);
    body.setEffect(new InnerShadow(1.2, 0, 0.7, Color.rgb(0, 27, 48, 0.68)));
    Rectangle label = roundedRect(5.0, 2.5, 9.6, 6.1,
        glassPanel(Color.web("#f8fcff"), Color.web("#a9bbc7")), Color.web("#405766"), 0.52, 0.8);
    Rectangle shutter = roundedRect(11.3, 3.2, 2.2, 4.3, metal(), Color.web("#647782"), 0.35, 0.25);
    Rectangle lower = roundedRect(4.4, 11.0, 11.2, 6.5,
        glassPanel(Color.web("#e8f3f9"), Color.web("#607d8e")), Color.web("#314854"), 0.58, 1.15);
    Circle hub = new Circle(10, 14.3, 1.55, radial(Color.web("#4a6575")));
    hub.setStroke(Color.web("#d9e7ee"));
    hub.setStrokeWidth(0.45);
    Line labelRule = line(6.1, 5.0, 10.1, 5.0, Color.web("#748a96"), 0.65);
    art.getChildren().addAll(body, label, shutter, labelRule, lower, hub);
    return finish(art);
  }

  static Pane expand(String requested) {
    Color accent = accent(requested, "#68b8e2");
    Pane art = canvas();
    Rectangle window = roundedRect(2.0, 2.0, 16.0, 16.0,
        glassPanel(Color.web("#e9f8ff"), accent.darker()), Color.web("#355467"), 0.72, 2.1);
    Rectangle title = roundedRect(3.3, 3.2, 13.4, 2.4, enamel(accent), accent.darker(), 0.35, 0.7);
    SVGPath arrows = svg(
        "M8.1 6.7 H4.8 V10 H3.1 V4.9 H8.1 Z M11.9 6.7 V4.9 H16.9 V10 H15.2 V6.7 Z "
            + "M8.1 13.3 V15.1 H3.1 V10 H4.8 V13.3 Z M11.9 13.3 H15.2 V10 H16.9 V15.1 H11.9 Z",
        whiteEnamel(), Color.web("#24566f"), 0.35);
    art.getChildren().addAll(window, title, arrows);
    return finish(art);
  }

  static Pane link(String requested) {
    Color accent = accent(requested, "#6ebee1");
    Pane art = canvas();
    Group links = new Group();
    for (int i = 0; i < 2; i++) {
      Ellipse outer = new Ellipse(7.0 + i * 6.0, 10, 4.6, 3.0);
      outer.setFill(Color.TRANSPARENT);
      outer.setStroke(metal());
      outer.setStrokeWidth(2.8);
      outer.setRotate(i == 0 ? -38 : 38);
      Ellipse enamelEdge = new Ellipse(7.0 + i * 6.0, 9.7, 4.25, 2.65);
      enamelEdge.setFill(Color.TRANSPARENT);
      enamelEdge.setStroke(accent.brighter());
      enamelEdge.setStrokeWidth(0.78);
      enamelEdge.setRotate(i == 0 ? -38 : 38);
      links.getChildren().addAll(outer, enamelEdge);
    }
    Line bridgeShadow = line(7.5, 10.5, 12.5, 10.5, Color.rgb(0, 24, 36, 0.72), 3.2);
    Line bridge = line(7.5, 10.0, 12.5, 10.0, whiteEnamel(), 1.6);
    art.getChildren().addAll(links, bridgeShadow, bridge);
    return finish(art);
  }

  static Pane polygon(String requested) {
    Color accent = accent(requested, "#a688df");
    Pane art = canvas();
    Polygon fill = new Polygon(3.2, 13.8, 5.4, 4.0, 13.8, 2.8, 17.0, 10.0, 12.8, 17.0);
    fill.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, accent.deriveColor(0, 0.8, 1.25, 0.48)),
        new Stop(1, accent.darker().deriveColor(0, 1, 1, 0.18))));
    fill.setStroke(accent.brighter());
    fill.setStrokeWidth(1.15);
    fill.setStrokeLineJoin(StrokeLineJoin.ROUND);
    art.getChildren().add(fill);
    double[][] points = {{3.2, 13.8}, {5.4, 4.0}, {13.8, 2.8}, {17.0, 10.0}, {12.8, 17.0}};
    for (double[] point : points) {
      Circle rim = new Circle(point[0], point[1], 1.65, metal());
      Circle node = new Circle(point[0], point[1], 1.05, radial(accent));
      art.getChildren().addAll(rim, node);
    }
    return finish(art);
  }

  static Pane freehand(String requested) {
    Color accent = accent(requested, "#9f83df");
    Pane art = canvas();
    Group pencil = new Group();
    Rectangle shadow = roundedRect(8.3, 2.2, 4.6, 13.8, Color.rgb(0, 18, 30, 0.7), Color.TRANSPARENT, 0, 1.1);
    shadow.setTranslateX(0.65);
    shadow.setTranslateY(0.65);
    Rectangle body = roundedRect(8.0, 1.8, 4.4, 13.5, enamel(accent), accent.darker(), 0.55, 1.0);
    Rectangle ferrule = roundedRect(8.0, 2.0, 4.4, 2.5, metal(), Color.web("#53656f"), 0.38, 0.45);
    Polygon wood = new Polygon(8.0, 15.0, 12.4, 15.0, 10.2, 19.0);
    wood.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#e9b875")), new Stop(0.5, Color.web("#fff1c4")),
        new Stop(1, Color.web("#bf7840"))));
    wood.setStroke(Color.web("#6b452d"));
    wood.setStrokeWidth(0.4);
    Polygon lead = new Polygon(9.35, 17.55, 11.05, 17.55, 10.2, 19.0);
    lead.setFill(Color.web("#26343e"));
    Line shine = line(9.0, 5.0, 9.0, 13.2, Color.rgb(255, 255, 255, 0.64), 0.55);
    pencil.getChildren().addAll(shadow, body, ferrule, wood, lead, shine);
    pencil.getTransforms().add(new Rotate(39, 10, 10));
    art.getChildren().add(pencil);
    return finish(art);
  }

  static Pane show(String requested) {
    return eye(requested, false);
  }

  static Pane hide(String requested) {
    return eye(requested, true);
  }

  private static Pane eye(String requested, boolean hidden) {
    Color accent = accent(requested, hidden ? "#d96875" : "#62bee6");
    Pane art = canvas();
    SVGPath eye = svg("M1.4 10 C4.4 4.0 8.0 2.7 10 2.7 C12.0 2.7 15.6 4.0 18.6 10 "
            + "C15.6 16.0 12.0 17.3 10 17.3 C8.0 17.3 4.4 16.0 1.4 10 Z",
        glassPanel(Color.web("#edfaff"), accent.darker()), Color.web("#34556a"), 0.72);
    Circle irisRim = new Circle(10, 10, 4.25, metal());
    Circle iris = new Circle(10, 10, 3.35, radial(accent));
    iris.setEffect(new InnerShadow(0.85, Color.rgb(0, 24, 42, 0.7)));
    Circle pupil = new Circle(10, 10, 1.4, Color.web("#102938"));
    Circle glint = new Circle(8.9, 8.7, 0.72, Color.rgb(255, 255, 255, 0.92));
    art.getChildren().addAll(eye, irisRim, iris, pupil, glint);
    if (hidden) {
      Line slashShadow = line(3.2, 2.8, 17.1, 17.2, Color.rgb(35, 0, 4, 0.75), 3.5);
      Line slash = line(3.0, 2.5, 17.0, 17.0,
          new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
              new Stop(0, Color.web("#ffbbc0")), new Stop(0.5, Color.web("#df5361")),
              new Stop(1, Color.web("#8d1f2b"))), 2.0);
      art.getChildren().addAll(slashShadow, slash);
    }
    return finish(art);
  }

  static Pane delete(String requested) {
    Color accent = accent(requested, "#df6472");
    Pane art = canvas();
    Rectangle body = roundedRect(4.3, 6.0, 11.4, 11.8, enamel(accent), accent.darker().darker(), 0.72, 1.6);
    body.setEffect(new InnerShadow(1.0, Color.rgb(55, 4, 10, 0.58)));
    Rectangle lid = roundedRect(2.8, 4.1, 14.4, 3.0, metal(), Color.web("#5a454a"), 0.58, 0.8);
    Rectangle handle = roundedRect(7.1, 2.1, 5.8, 2.7, metal(), Color.web("#5b4247"), 0.5, 0.8);
    for (double x : new double[] {7.1, 10.0, 12.9}) {
      Line groove = line(x, 8.2, x, 15.7, Color.rgb(90, 15, 25, 0.72), 0.85);
      Line highlight = line(x + 0.45, 8.2, x + 0.45, 15.2, Color.rgb(255, 220, 223, 0.35), 0.4);
      art.getChildren().addAll(groove, highlight);
    }
    art.getChildren().addAll(body, handle, lid);
    body.toBack();
    return finish(art);
  }

  static Pane timeline(String requested) {
    Color accent = accent(requested, "#58add9");
    Pane art = canvas();
    Rectangle panel = roundedRect(1.5, 3.0, 17.0, 14.0,
        glassPanel(Color.web("#e8f7fd"), accent.darker().darker()), Color.web("#344d5c"), 0.72, 2.0);
    Rectangle header = roundedRect(2.7, 4.1, 14.6, 2.7, enamel(accent), accent.darker(), 0.38, 0.75);
    art.getChildren().addAll(panel, header);
    for (double x : new double[] {4.0, 7.0, 10.0, 13.0, 16.0}) {
      Circle sprocket = new Circle(x, 5.45, 0.55, Color.web("#eaf8ff"));
      art.getChildren().add(sprocket);
    }
    Line trackA = line(3.7, 9.6, 16.2, 9.6, Color.web("#8fcfe8"), 1.15);
    Line trackB = line(3.7, 13.5, 16.2, 13.5, Color.web("#7ba6bd"), 1.15);
    Rectangle clipA = roundedRect(5.0, 8.2, 4.2, 2.7, enamel(Color.web("#68d08a")), Color.web("#287446"), 0.35, 0.7);
    Rectangle clipB = roundedRect(10.0, 12.1, 5.0, 2.7, enamel(Color.web("#d89b55")), Color.web("#805126"), 0.35, 0.7);
    Line playhead = line(10.0, 7.1, 10.0, 16.0, Color.web("#f4d866"), 1.0);
    Polygon cap = new Polygon(8.8, 7.0, 11.2, 7.0, 10.0, 8.4);
    cap.setFill(enamel(Color.web("#f1c34f")));
    art.getChildren().addAll(trackA, trackB, clipA, clipB, playhead, cap);
    return finish(art);
  }

  static Pane auto(String requested) {
    Color accent = accent(requested, "#e9a04f");
    Pane art = canvas();
    Group wand = new Group();
    Line shadow = line(5.0, 15.5, 14.8, 5.7, Color.rgb(40, 21, 4, 0.78), 4.0);
    shadow.setTranslateY(0.65);
    Line body = line(4.8, 15.0, 14.6, 5.2,
        new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#5d3423")), new Stop(0.48, accent),
            new Stop(0.7, Color.web("#fff2ab")), new Stop(1, Color.web("#8b5425"))), 2.55);
    Line tip = line(12.2, 7.7, 14.7, 5.2, Color.web("#f8fbff"), 1.1);
    wand.getChildren().addAll(shadow, body, tip);
    art.getChildren().add(wand);
    sparkle(art, 4.1, 5.0, 2.2, Color.web("#78d8f3"));
    sparkle(art, 15.8, 12.4, 1.8, Color.web("#ffe47a"));
    sparkle(art, 10.4, 2.8, 1.15, Color.WHITE);
    return finish(art);
  }

  static Pane label(String requested) {
    Color accent = accent(requested, "#58b6de");
    Pane art = canvas();
    Polygon shadow = new Polygon(2.4, 4.6, 11.7, 4.6, 18.0, 10.2, 11.7, 16.0, 2.4, 16.0);
    shadow.setFill(Color.rgb(0, 23, 37, 0.72));
    shadow.setTranslateY(0.75);
    Polygon tag = new Polygon(2.2, 3.8, 11.7, 3.8, 18.0, 10.0, 11.7, 16.2, 2.2, 16.2);
    tag.setFill(enamel(accent));
    tag.setStroke(accent.darker().darker());
    tag.setStrokeWidth(0.72);
    tag.setStrokeLineJoin(StrokeLineJoin.ROUND);
    Circle holeRim = new Circle(5.1, 10.0, 2.0, metal());
    Circle hole = new Circle(5.1, 10.0, 1.15, Color.web("#213440"));
    Line shine = line(8.0, 5.4, 11.1, 5.4, Color.rgb(255, 255, 255, 0.64), 0.65);
    art.getChildren().addAll(shadow, tag, holeRim, hole, shine);
    return finish(art);
  }

  static Pane memory(String requested) {
    Color accent = accent(requested, "#55b9df");
    Pane art = canvas();
    Rectangle chip = roundedRect(4.0, 4.0, 12.0, 12.0, enamel(accent), Color.web("#274b5e"), 0.75, 1.9);
    chip.setEffect(new InnerShadow(1.0, Color.rgb(0, 34, 52, 0.65)));
    Rectangle core = roundedRect(6.5, 6.5, 7.0, 7.0,
        glassPanel(Color.web("#d8f5ff"), accent.darker()), Color.web("#e8fbff"), 0.55, 1.1);
    for (int i = 0; i < 4; i++) {
      double p = 5.5 + i * 3.0;
      Line topPin = line(p, 1.5, p, 4.0, metal(), 1.15);
      Line bottomPin = line(p, 16.0, p, 18.5, metal(), 1.15);
      Line leftPin = line(1.5, p, 4.0, p, metal(), 1.15);
      Line rightPin = line(16.0, p, 18.5, p, metal(), 1.15);
      art.getChildren().addAll(topPin, bottomPin, leftPin, rightPin);
    }
    Circle status = new Circle(11.4, 8.7, 0.85, radial(Color.web("#68dc82")));
    art.getChildren().addAll(chip, core, status);
    chip.toBack();
    return finish(art);
  }

  static Pane javaRuntime() {
    Pane art = canvas();
    Ellipse saucer = new Ellipse(9.6, 16.7, 8.1, 2.1);
    saucer.setFill(metal());
    saucer.setStroke(Color.web("#46647b"));
    saucer.setStrokeWidth(0.7);
    SVGPath handle = svg("M14.6 8.9 H16.3 C19.5 8.9 18.7 13.5 14.6 13.5",
        Color.TRANSPARENT, Color.web("#91c5dd"), 1.8);
    SVGPath cup = svg("M3.1 8.1 H15.8 L14.9 12.6 Q14.1 16.6 9.5 16.6 Q4.8 16.6 4 12.6 Z",
        glassPanel(Color.web("#f6fdff"), Color.web("#5ca0c6")), Color.web("#325772"), 0.85);
    Ellipse rim = new Ellipse(9.45, 8.2, 6.3, 1.55);
    rim.setFill(Color.web("#f5fcff"));
    rim.setStroke(Color.web("#7198ad"));
    rim.setStrokeWidth(0.75);
    SVGPath steam = svg("M8.9 6.7 C5.8 4.4 11.9 3.3 9.7 1.2 M11.7 6.1 Q14.3 4.7 12.7 3.2",
        Color.TRANSPARENT, Color.web("#e88b55"), 1.25);
    art.getChildren().addAll(saucer, handle, cup, rim, steam);
    return finish(art);
  }

  static Pane open(String requested) {
    Color accent = accent(requested, "#dfa44f");
    Pane art = canvas();
    Rectangle tab = roundedRect(3.0, 3.0, 8.5, 5.0, enamel(accent.brighter()),
        accent.darker(), 0.55, 1.7);
    Rectangle back = roundedRect(2.0, 6.0, 16.0, 11.7, enamel(accent),
        accent.darker().darker(), 0.72, 1.8);
    Polygon face = new Polygon(2.0, 8.0, 18.0, 8.0, 16.6, 18.0, 3.4, 18.0);
    face.setFill(glassPanel(Color.web("#fff1ad"), accent.darker()));
    face.setStroke(Color.web("#774718"));
    face.setStrokeWidth(0.62);
    Line shine = line(4.0, 9.3, 15.6, 9.3, Color.rgb(255, 255, 255, 0.68), 0.65);
    art.getChildren().addAll(tab, back, face, shine);
    return finish(art);
  }

  static Pane speech(String requested) {
    Color accent = accent(requested, "#58bce4");
    Pane art = canvas();
    SVGPath bubble = svg("M3 3.2 H17 Q18.5 3.2 18.5 4.8 V13.0 Q18.5 14.6 17 14.6 "
            + "H9.0 L4.2 18.2 L5.2 14.6 H3 Q1.5 14.6 1.5 13 V4.8 Q1.5 3.2 3 3.2 Z",
        glassPanel(Color.web("#e9faff"), accent.darker()), Color.web("#31566b"), 0.7);
    bubble.setEffect(new InnerShadow(1.0, Color.rgb(0, 31, 48, 0.58)));
    art.getChildren().add(bubble);
    for (double x : new double[] {6.0, 10.0, 14.0}) {
      Circle dot = new Circle(x, 9.0, 1.2, radial(accent));
      dot.setStroke(Color.rgb(255, 255, 255, 0.74));
      dot.setStrokeWidth(0.35);
      art.getChildren().add(dot);
    }
    return finish(art);
  }

  static Pane apply(String requested) {
    Color accent = accent(requested, "#58c975");
    Pane art = canvas();
    Circle rim = new Circle(10, 10, 8.7, metal());
    rim.setStroke(Color.web("#385044"));
    rim.setStrokeWidth(0.68);
    Circle glass = new Circle(10, 10, 7.25, radial(accent));
    glass.setEffect(new InnerShadow(1.2, Color.rgb(5, 45, 24, 0.68)));
    SVGPath shadow = svg("M5.2 10.2 L8.5 13.5 L15.4 6.6", Color.TRANSPARENT,
        Color.rgb(11, 59, 29, 0.78), 3.2);
    shadow.setTranslateY(0.6);
    SVGPath check = svg("M5.2 9.7 L8.5 13.0 L15.4 6.1", Color.TRANSPARENT,
        Color.WHITE, 1.85);
    art.getChildren().addAll(rim, glass, shadow, check);
    return finish(art);
  }

  static Pane home(String requested) {
    Color accent = accent(requested, "#62cb72");
    Pane art = canvas();
    Polygon roofShadow = new Polygon(1.6, 9.3, 10.0, 1.8, 18.4, 9.3, 16.2, 11.0,
        10.0, 5.5, 3.8, 11.0);
    roofShadow.setFill(Color.rgb(0, 35, 20, 0.75));
    roofShadow.setTranslateY(0.7);
    Polygon roof = new Polygon(1.6, 8.7, 10.0, 1.2, 18.4, 8.7, 16.2, 10.4,
        10.0, 4.9, 3.8, 10.4);
    roof.setFill(enamel(accent));
    roof.setStroke(Color.web("#285c39"));
    roof.setStrokeWidth(0.6);
    Rectangle house = roundedRect(4.2, 8.3, 11.6, 9.8,
        glassPanel(Color.web("#edfff1"), accent.darker()), Color.web("#2a5838"), 0.65, 1.2);
    Rectangle door = roundedRect(8.1, 12.0, 3.8, 6.1, enamel(Color.web("#58a5d0")),
        Color.web("#27546d"), 0.45, 0.65);
    Circle knob = new Circle(10.9, 15.1, 0.38, Color.web("#fff1a5"));
    art.getChildren().addAll(house, roofShadow, roof, door, knob);
    return finish(art);
  }

  static Pane copy(String requested) {
    Color accent = accent(requested, "#62bde5");
    Pane art = canvas();
    Rectangle rear = roundedRect(2.5, 2.5, 11.7, 13.8,
        glassPanel(Color.web("#f8fdff"), Color.web("#7798aa")), Color.web("#405866"), 0.62, 1.5);
    Rectangle front = roundedRect(6.0, 5.3, 11.7, 13.0,
        glassPanel(Color.web("#effbff"), accent.darker()), Color.web("#315a70"), 0.7, 1.5);
    Rectangle title = roundedRect(7.2, 6.5, 9.2, 2.0, enamel(accent), accent.darker(), 0.3, 0.6);
    Line row1 = line(8.0, 11.1, 15.7, 11.1, Color.rgb(231, 249, 255, 0.82), 0.75);
    Line row2 = line(8.0, 14.0, 14.3, 14.0, Color.rgb(231, 249, 255, 0.68), 0.75);
    art.getChildren().addAll(rear, front, title, row1, row2);
    return finish(art);
  }

  static Pane select(String requested) {
    Color accent = accent(requested, "#62b9e4");
    Pane art = canvas();
    Rectangle panel = roundedRect(2.0, 2.0, 16.0, 16.0,
        glassPanel(Color.web("#edfaff"), Color.web("#486f86")), Color.web("#314b5a"), 0.65, 2.0);
    Rectangle selection = new Rectangle(5.0, 5.0, 10.0, 10.0);
    selection.setFill(Color.rgb(103, 198, 238, 0.18));
    selection.setStroke(accent.brighter());
    selection.setStrokeWidth(0.8);
    selection.getStrokeDashArray().setAll(1.8, 1.5);
    art.getChildren().addAll(panel, selection);
    for (double x : new double[] {5.0, 15.0}) {
      for (double y : new double[] {5.0, 15.0}) {
        Rectangle handle = roundedRect(x - 1.0, y - 1.0, 2.0, 2.0, metal(), Color.web("#31566b"), 0.35, 0.45);
        art.getChildren().add(handle);
      }
    }
    return finish(art);
  }

  static Pane person(String requested) {
    Color accent = accent(requested, "#62bce3");
    Pane art = canvas();
    Circle medallion = new Circle(10, 10, 8.6, metal());
    Circle glass = new Circle(10, 10, 7.25,
        new RadialGradient(-35, 0.25, 7.5, 6.5, 9, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#effbff")), new Stop(0.45, accent.brighter()),
            new Stop(1, accent.darker().darker())));
    Circle head = new Circle(10, 7.2, 3.0, whiteEnamel());
    head.setStroke(Color.web("#526b78"));
    head.setStrokeWidth(0.45);
    SVGPath shoulders = svg("M4.5 17.0 Q5.3 11.6 10 11.6 Q14.7 11.6 15.5 17.0 Z",
        whiteEnamel(), Color.web("#465e6b"), 0.55);
    art.getChildren().addAll(medallion, glass, shoulders, head);
    return finish(art);
  }

  static Pane image(String requested) {
    Color accent = accent(requested, "#62b9e1");
    Pane art = canvas();
    Rectangle frame = roundedRect(1.8, 2.2, 16.4, 15.8, metal(), Color.web("#354954"), 0.72, 1.8);
    Rectangle photo = roundedRect(3.3, 3.7, 13.4, 12.8,
        glassPanel(Color.web("#e9fbff"), accent.darker()), Color.web("#dff8ff"), 0.4, 0.8);
    Polygon mountain = new Polygon(4.2, 15.5, 8.0, 9.7, 10.4, 12.3, 13.0, 8.5, 16.0, 15.5);
    mountain.setFill(enamel(Color.web("#5fc877")));
    Circle sun = new Circle(13.8, 6.6, 1.55, radial(Color.web("#f2bd50")));
    art.getChildren().addAll(frame, photo, mountain, sun);
    return finish(art);
  }

  static Pane edit(String requested) {
    Color accent = accent(requested, "#59b9e2");
    Pane art = copy(requested);
    art.getChildren().clear();
    Rectangle page = roundedRect(2.2, 2.0, 13.3, 16.3,
        glassPanel(Color.web("#ffffff"), Color.web("#9fc4d5")), Color.web("#456373"), 0.65, 1.4);
    Line row1 = line(4.5, 6.0, 12.3, 6.0, Color.web("#617e8d"), 0.65);
    Line row2 = line(4.5, 9.0, 11.4, 9.0, Color.web("#617e8d"), 0.65);
    Rectangle pencil = roundedRect(11.2, 5.0, 3.4, 13.0, enamel(accent), accent.darker(), 0.5, 0.8);
    pencil.getTransforms().add(new Rotate(38, 12.9, 11.5));
    Polygon nib = new Polygon(15.7, 16.0, 18.5, 18.5, 14.4, 19.0);
    nib.setFill(metal());
    art.getChildren().addAll(page, row1, row2, pencil, nib);
    return finish(art);
  }

  static Pane sparkles(String requested) {
    Color accent = accent(requested, "#e5a24e");
    Pane art = canvas();
    Line wandShadow = line(4.0, 16.3, 13.8, 6.5, Color.rgb(55, 28, 5, 0.78), 4.0);
    Line wand = line(3.8, 15.7, 13.6, 5.9,
        new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#74452a")), new Stop(0.48, accent),
            new Stop(0.72, Color.web("#fff1aa")), new Stop(1, Color.web("#85501f"))), 2.4);
    art.getChildren().addAll(wandShadow, wand);
    sparkle(art, 14.7, 4.4, 2.5, Color.web("#ffe174"));
    sparkle(art, 5.0, 5.5, 1.55, Color.web("#7ed8f2"));
    sparkle(art, 16.2, 11.7, 1.35, Color.web("#e690db"));
    return finish(art);
  }

  static Pane movie(String requested) {
    Color accent = accent(requested, "#5eb8df");
    Pane art = canvas();
    Rectangle body = roundedRect(2.0, 5.0, 16.0, 12.8,
        glassPanel(Color.web("#ecfaff"), accent.darker()), Color.web("#344e5e"), 0.7, 1.5);
    Rectangle screen = roundedRect(5.0, 7.8, 10.0, 7.2, Color.web("#18394d"),
        Color.web("#dff8ff"), 0.45, 0.7);
    for (double x : new double[] {3.5, 16.5}) {
      for (double y : new double[] {7.2, 10.7, 14.2}) {
        Rectangle hole = roundedRect(x - 0.6, y - 0.7, 1.2, 1.4,
            Color.web("#dff6ff"), Color.TRANSPARENT, 0, 0.2);
        art.getChildren().add(hole);
      }
    }
    Polygon play = new Polygon(8.6, 9.2, 13.0, 11.4, 8.6, 13.6);
    play.setFill(enamel(Color.web("#f2ae50")));
    art.getChildren().addAll(body, screen, play);
    body.toBack();
    return finish(art);
  }

  static Pane locate(String requested) {
    Color accent = accent(requested, "#5abbe4");
    Pane art = canvas();
    Circle rim = new Circle(10, 10, 8.7, metal());
    Circle face = new Circle(10, 10, 7.3,
        new RadialGradient(-35, 0.25, 7.5, 6.4, 9.2, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#f2fbff")), new Stop(0.5, accent.brighter()),
            new Stop(1, accent.darker().darker())));
    Polygon needleShadow = new Polygon(10.6, 3.5, 12.4, 10.2, 9.4, 16.5, 7.7, 9.8);
    needleShadow.setFill(Color.rgb(0, 28, 44, 0.72));
    needleShadow.setTranslateY(0.55);
    Polygon needle = new Polygon(10.5, 3.0, 12.0, 10.0, 9.5, 16.0, 8.0, 10.0);
    needle.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#f15d65")), new Stop(0.5, Color.WHITE),
        new Stop(1, Color.web("#317da7"))));
    Circle hub = new Circle(10, 10, 1.25, metal());
    art.getChildren().addAll(rim, face, needleShadow, needle, hub);
    return finish(art);
  }

  static Pane dock(String requested) {
    Color accent = accent(requested, "#5dbce3");
    Pane art = canvas();
    Rectangle window = roundedRect(2.0, 2.0, 16.0, 16.0,
        glassPanel(Color.web("#effaff"), Color.web("#486c82")), Color.web("#304754"), 0.7, 2.0);
    Rectangle title = roundedRect(3.2, 3.2, 13.6, 2.4, enamel(accent), accent.darker(), 0.35, 0.7);
    Rectangle stage = roundedRect(4.0, 6.7, 8.4, 8.9, Color.web("#d9eef8"),
        Color.web("#456575"), 0.4, 0.8);
    Rectangle rail = roundedRect(13.3, 6.7, 3.1, 8.9, enamel(accent), accent.darker(), 0.4, 0.7);
    Line grip1 = line(14.4, 9.0, 15.3, 9.0, Color.web("#eefaff"), 0.55);
    Line grip2 = line(14.4, 11.1, 15.3, 11.1, Color.web("#eefaff"), 0.55);
    art.getChildren().addAll(window, title, stage, rail, grip1, grip2);
    return finish(art);
  }

  private static Pane canvas() {
    Pane pane = new Pane();
    pane.setMinSize(SIZE, SIZE);
    pane.setPrefSize(SIZE, SIZE);
    pane.setMaxSize(SIZE, SIZE);
    pane.setPickOnBounds(false);
    pane.setMouseTransparent(true);
    return pane;
  }

  private static Pane finish(Pane art) {
    art.getStyleClass().add("jvn-sidebar-tool-bespoke-artwork");
    art.setEffect(new DropShadow(1.7, 0, 0.85, Color.rgb(0, 0, 0, 0.82)));
    art.setCache(true);
    art.setCacheHint(CacheHint.SPEED);
    return art;
  }

  private static Color accent(String requested, String fallback) {
    Color fallbackColor = Color.web(fallback);
    if (requested == null || requested.isBlank()) return fallbackColor;
    try {
      Color parsed = Color.web(requested);
      if (parsed.getSaturation() < 0.16 && parsed.getBrightness() > 0.28) return fallbackColor;
      return parsed;
    } catch (IllegalArgumentException ignored) {
      return fallbackColor;
    }
  }

  private static Paint enamel(Color color) {
    return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, color.brighter().brighter()),
        new Stop(0.2, color.brighter()),
        new Stop(0.48, color),
        new Stop(0.72, color.darker()),
        new Stop(1, color.darker().darker()));
  }

  private static Paint whiteEnamel() {
    return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.WHITE), new Stop(0.48, Color.web("#f6fbfd")),
        new Stop(1, Color.web("#aebdc6")));
  }

  private static Paint metal() {
    return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.WHITE), new Stop(0.18, Color.web("#edf3f6")),
        new Stop(0.5, Color.web("#9caab3")), new Stop(0.72, Color.web("#dce5ea")),
        new Stop(1, Color.web("#64747e")));
  }

  private static Paint glassPanel(Color top, Color bottom) {
    return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, top), new Stop(0.24, top.deriveColor(0, 0.8, 0.92, 0.94)),
        new Stop(0.5, bottom.brighter()), new Stop(1, bottom));
  }

  private static Paint radial(Color accent) {
    return new RadialGradient(-35, 0.25, 7.0, 6.1, 9.0, false, CycleMethod.NO_CYCLE,
        new Stop(0, Color.WHITE), new Stop(0.18, accent.brighter().brighter()),
        new Stop(0.5, accent), new Stop(0.8, accent.darker()),
        new Stop(1, accent.darker().darker()));
  }

  private static Rectangle roundedRect(double x, double y, double width, double height,
      Paint fill, Paint stroke, double strokeWidth, double arc) {
    Rectangle rectangle = new Rectangle(x, y, width, height);
    rectangle.setArcWidth(arc * 2);
    rectangle.setArcHeight(arc * 2);
    rectangle.setFill(fill);
    rectangle.setStroke(stroke);
    rectangle.setStrokeWidth(strokeWidth);
    return rectangle;
  }

  private static SVGPath svg(String content, Paint fill, Paint stroke, double strokeWidth) {
    SVGPath path = new SVGPath();
    path.setContent(content);
    path.setFill(fill);
    path.setStroke(stroke);
    path.setStrokeWidth(strokeWidth);
    path.setStrokeLineCap(StrokeLineCap.ROUND);
    path.setStrokeLineJoin(StrokeLineJoin.ROUND);
    return path;
  }

  private static Line line(double startX, double startY, double endX, double endY,
      Paint stroke, double width) {
    Line line = new Line(startX, startY, endX, endY);
    line.setStroke(stroke);
    line.setStrokeWidth(width);
    line.setStrokeLineCap(StrokeLineCap.ROUND);
    return line;
  }

  private static Group cross(Paint stroke, double width) {
    return new Group(
        line(6.0, 6.0, 14.0, 14.0, stroke, width),
        line(14.0, 6.0, 6.0, 14.0, stroke, width));
  }

  private static void sparkle(Pane art, double x, double y, double radius, Color color) {
    Polygon star = new Polygon(
        x, y - radius, x + radius * 0.35, y - radius * 0.35,
        x + radius, y, x + radius * 0.35, y + radius * 0.35,
        x, y + radius, x - radius * 0.35, y + radius * 0.35,
        x - radius, y, x - radius * 0.35, y - radius * 0.35);
    star.setFill(enamel(color));
    star.setStroke(Color.rgb(255, 255, 255, 0.72));
    star.setStrokeWidth(0.35);
    art.getChildren().add(star);
  }
}
