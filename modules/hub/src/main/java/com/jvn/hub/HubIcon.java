package com.jvn.hub;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.AbstractButton;
import javax.swing.Icon;

/** Resolution-independent Aero artwork for Engine Hub commands, controls, and status. */
final class HubIcon implements Icon {
  enum Kind {
    APP, EDITOR, LAUNCHER, BUILD, SHORTCUT, UPDATE, TESTS, OPTIONS, MORE, CANCEL, QUIT,
    DEVELOPER, DEVELOPER_ACTIVE, SAFE, SAFE_ACTIVE, DIAGNOSTICS, ABOUT, DOCUMENTATION,
    PLAY, EDIT, CHECK, REFRESH, STOP, CLOSE, HELP, WARNING, ERROR, PENDING, RUNNING, IDLE,
    BOX, BOX_CHECKED, RADIO, RADIO_CHECKED, ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT,
    DOCUMENT, FOLDER, IMAGE, JAVA, PLUGIN, PLUGIN_DISABLED, PLUGIN_ALERT, INSTALL, COPY, TRASH, SAVE, EXPORT, RECEIPT
  }

  private record Finish(Color shine, Color color, Color shade, Color edge) {}

  private static final Finish GLASS = finish(0xecfaff, 0x88c7ea, 0x35799f, 0x24485e);
  private static final Finish BLUE = finish(0xbcefff, 0x419dd9, 0x1d598d, 0x193d61);
  private static final Finish SCREEN = finish(0x49779b, 0x204866, 0x10263e, 0x102b42);
  private static final Finish TEAL = finish(0xc5fff6, 0x46bfb6, 0x1b6e74, 0x174d55);
  private static final Finish GREEN = finish(0xe0ffbe, 0x68ba47, 0x32723a, 0x254d2d);
  private static final Finish GOLD = finish(0xffedb0, 0xefb557, 0xbb711f, 0x79481b);
  private static final Finish ORANGE = finish(0xffe9b0, 0xf49b50, 0xb6532c, 0x743827);
  private static final Finish RED = finish(0xffd3be, 0xe56455, 0xa7353b, 0x732832);
  private static final Finish VIOLET = finish(0xeee1ff, 0xa793da, 0x625593, 0x3d365c);
  private static final Finish SILVER = finish(0xf5fcff, 0xa6bccb, 0x627783, 0x3c505e);
  private static final Finish PAPER = finish(0xffffff, 0xe8f4fb, 0xb9d2e4, 0x56778f);
  private static final Finish SLATE = finish(0xb1bfca, 0x758694, 0x43515e, 0x283743);
  private static final Color WHITE = Color.WHITE;
  private static final Color INK = color(0x233f56);
  private static final Color LIGHT_INK = color(0x54819b);
  private static final Color GLINT = new Color(255, 255, 255, 175);

  private final Kind kind;
  private final int size;

  private HubIcon(Kind kind, int size) {
    this.kind = kind;
    this.size = Math.max(1, size);
  }

  static HubIcon of(Kind kind, int size) {
    return new HubIcon(kind, size);
  }

  static BufferedImage image(Kind kind, int size) {
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      of(kind, size).paintIcon(null, g, 0, 0);
    } finally {
      g.dispose();
    }
    return image;
  }

  static Icon selection(boolean radio, int size) {
    HubIcon off = of(radio ? Kind.RADIO : Kind.BOX, size);
    HubIcon on = of(radio ? Kind.RADIO_CHECKED : Kind.BOX_CHECKED, size);
    return new Icon() {
      @Override public int getIconWidth() { return off.getIconWidth(); }
      @Override public int getIconHeight() { return off.getIconHeight(); }
      @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        boolean selected = component instanceof AbstractButton button && button.isSelected();
        (selected ? on : off).paintIcon(component, graphics, x, y);
      }
    };
  }

  @Override public int getIconWidth() { return size; }
  @Override public int getIconHeight() { return size; }

  @Override
  public void paintIcon(Component component, Graphics graphics, int x, int y) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      prepare(g, x, y, size);
      if (component != null && !component.isEnabled() && g.getComposite() instanceof AlphaComposite alpha) {
        g.setComposite(alpha.derive(alpha.getAlpha() * 0.47f));
      }
      Kind artwork = kind == Kind.ARROW_RIGHT && component instanceof javax.swing.JMenu
          && !component.getComponentOrientation().isLeftToRight() ? Kind.ARROW_LEFT : kind;
      paint(g, artwork);
    } finally {
      g.dispose();
    }
  }

  static void paintSpinner(Graphics2D source, int x, int y, int size, int phase) {
    Graphics2D g = (Graphics2D) source.create();
    try {
      prepare(g, x, y, size);
      surface(g, oval(2.5, 2.5, 19, 19), SCREEN);
      for (int i = 0; i < 12; i++) {
        double angle = Math.toRadians(i * 30 - 90);
        double dx = Math.cos(angle);
        double dy = Math.sin(angle);
        int age = Math.floorMod(phase - i, 12);
        Color glow = new Color(173, 234, 255, 255 - age * 18);
        line(g, glow, age == 0 ? 2.25f : 1.65f,
            12 + dx * 5.2, 12 + dy * 5.2, 12 + dx * 7.4, 12 + dy * 7.4);
      }
    } finally {
      g.dispose();
    }
  }

  private static void prepare(Graphics2D g, int x, int y, int size) {
    g.translate(x, y);
    g.scale(size / 24.0, size / 24.0);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
  }

  private static void paint(Graphics2D g, Kind kind) {
    switch (kind) {
      case APP -> app(g);
      case EDITOR -> editor(g);
      case LAUNCHER -> rocket(g);
      case BUILD -> build(g);
      case SHORTCUT -> shortcut(g);
      case UPDATE -> transfer(g, false);
      case REFRESH -> refresh(g);
      case TESTS -> clipboard(g);
      case OPTIONS -> settings(g);
      case MORE -> more(g);
      case CANCEL -> stopSign(g, false);
      case STOP -> stopSign(g, true);
      case QUIT -> power(g);
      case DEVELOPER, DEVELOPER_ACTIVE -> terminal(g, kind == Kind.DEVELOPER_ACTIVE);
      case SAFE, SAFE_ACTIVE -> shield(g, kind == Kind.SAFE_ACTIVE);
      case DIAGNOSTICS -> diagnostics(g);
      case ABOUT, HELP, CHECK, ERROR, PLAY, RUNNING, PENDING, IDLE -> jewel(g, kind);
      case DOCUMENTATION -> book(g);
      case EDIT -> pencil(g);
      case CLOSE -> close(g);
      case WARNING -> warning(g);
      case BOX, BOX_CHECKED -> box(g, kind == Kind.BOX_CHECKED);
      case RADIO, RADIO_CHECKED -> radio(g, kind == Kind.RADIO_CHECKED);
      case ARROW_UP -> chevron(g, 0);
      case ARROW_RIGHT -> chevron(g, Math.PI / 2);
      case ARROW_DOWN -> chevron(g, Math.PI);
      case ARROW_LEFT -> chevron(g, -Math.PI / 2);
      case DOCUMENT -> page(g);
      case FOLDER -> folder(g);
      case IMAGE -> picture(g);
      case JAVA -> java(g);
      case PLUGIN, PLUGIN_DISABLED, PLUGIN_ALERT -> plugin(g, kind);
      case INSTALL -> transfer(g, true);
      case COPY -> copy(g);
      case TRASH -> trash(g);
      case SAVE -> disk(g);
      case EXPORT -> export(g);
      case RECEIPT -> receipt(g);
    }
  }

  private static void app(Graphics2D g) {
    surface(g, rounded(2, 2, 20, 20, 5.5), SCREEN);
    filled(g, polygon(3.3, 14.5, 20.7, 8.1, 20.7, 15, 3.3, 19.7), color(0x417dab));
    surface(g, polygon(3.1, 14.9, 21, 8.2, 21, 11.5, 3.1, 18.3), ORANGE);
    Path2D letter = path(15.4, 5.8, 15.4, 14);
    letter.curveTo(15.4, 19.4, 8.2, 19.9, 7.2, 15.4);
    mark(g, letter, 3.5f);
  }

  private static void editor(Graphics2D g) {
    surface(g, rounded(8, 14.5, 8, 5.6, 1.5), SILVER);
    surface(g, rounded(5, 19, 14, 2.8, 1.7), SILVER);
    surface(g, rounded(1.8, 2.2, 20.4, 14.7, 3), SILVER);
    surface(g, rounded(3.5, 3.8, 17, 10.8, 1.7), SCREEN);
    mark(g, path(9, 6.5, 6.5, 9.2, 9, 11.8), 1.5f);
    mark(g, path(15, 6.5, 17.5, 9.2, 15, 11.8), 1.5f);
    line(g, color(0xffc68b), 1.55f, 12.9, 6.1, 11.1, 12.1);
  }

  private static void rocket(Graphics2D g) {
    surface(g, polygon(8.1, 14.5, 4.3, 20.9, 11, 17.6), ORANGE);
    surface(g, polygon(8.8, 8.9, 3.7, 11.6, 4.4, 16.5, 9, 13.9), BLUE);
    surface(g, polygon(15.2, 15.2, 12.4, 20.3, 7.5, 19.6, 10.1, 14.9), BLUE);
    Path2D body = path(7, 13.1);
    body.quadTo(10, 3.5, 21, 2.3);
    body.quadTo(20.1, 12.2, 10.6, 17);
    body.closePath();
    surface(g, body, PAPER);
    surface(g, oval(13.4, 5.6, 4.6, 4.6), TEAL);
    line(g, GLINT, 0.9f, 9.5, 11.9, 12.2, 8.1);
  }

  private static void build(Graphics2D g) {
    surface(g, rounded(2, 11, 17.6, 10.5, 2.3), GOLD);
    surface(g, rounded(3, 10, 16.4, 4.4, 1.4), ORANGE);
    filled(g, rounded(8, 13, 4.5, 3.4, 0.8), INK);
    filled(g, rounded(9, 13.6, 2.5, 1.2, 0.7), WHITE);
    surface(g, polygon(12.6, 8.8, 15.2, 6.8, 22.3, 16.1, 19.6, 18.2), BLUE);
    surface(g, polygon(8.5, 8, 17.2, 1.8, 20.6, 5.6, 12, 12.1, 9.4, 10.9), SILVER);
  }

  private static void shortcut(Graphics2D g) {
    page(g);
    badge(g, 10, 10, 13, art -> {
      surface(art, rounded(2.5, 2.5, 19, 19, 3.6), BLUE);
      mark(art, path(6, 17, 16.5, 6.5, 10, 6.5, 16.5, 6.5, 16.5, 13.2), 2.8f);
    });
  }

  private static void transfer(Graphics2D g, boolean install) {
    surface(g, rounded(2, 13, 20, 8, 3), SILVER);
    surface(g, rounded(4, 14.8, 16, 4, 1.7), SCREEN);
    filled(g, oval(17.7, 16, 1.7, 1.7), color(0xadf189));
    surface(g, polygon(8.3, 2.1, 15.7, 2.1, 15.7, 9.5, 20, 9.5,
        12, 17.2, 4, 9.5, 8.3, 9.5), install ? BLUE : GREEN);
  }

  private static void refresh(Graphics2D g) {
    arc(g, 10.7, 174, BLUE);
    arc(g, 190.7, 174, TEAL);
  }

  private static void arc(Graphics2D g, double start, double sweep, Finish finish) {
    Shape curve = new Arc2D.Double(4.5, 4.5, 15, 15, start, sweep - 28, Arc2D.OPEN);
    draw(g, curve, finish.edge(), 4.6f);
    draw(g, curve, finish.color(), 3.15f);
    draw(g, curve, finish.shine(), 0.9f);
    double end = Math.toRadians(start + sweep - 28);
    double x = 12 + Math.cos(end) * 7.5;
    double y = 12 - Math.sin(end) * 7.5;
    Graphics2D arrow = (Graphics2D) g.create();
    arrow.translate(x, y);
    arrow.rotate(-end - Math.PI / 2);
    surface(arrow, polygon(-3.6, -1.7, 4, 0, -3.6, 2.2), finish);
    arrow.dispose();
  }

  private static void clipboard(Graphics2D g) {
    surface(g, rounded(4, 3.8, 16, 18, 2.5), GOLD);
    surface(g, rounded(6, 5, 12, 15, 1.2), PAPER);
    surface(g, rounded(8, 2, 8, 4.5, 2), SILVER);
    draw(g, path(8.1, 10, 9.5, 11.7, 12.1, 8.9), GREEN.shade(), 1.45f);
    draw(g, path(8.1, 15.7, 9.5, 17.4, 12.1, 14.6), GREEN.shade(), 1.45f);
    line(g, LIGHT_INK, 1.1f, 13.6, 10.6, 16.5, 10.6);
    line(g, LIGHT_INK, 1.1f, 13.6, 16.3, 16.5, 16.3);
  }

  private static void settings(Graphics2D g) {
    surface(g, rounded(2, 3, 20, 18, 3), SILVER);
    int[] values = {9, 15, 11};
    for (int i = 0; i < values.length; i++) {
      int y = 7 + 5 * i;
      line(g, INK, 1.8f, 5.5, y, 18.5, y);
      surface(g, oval(values[i] - 2.4, y - 2.4, 4.8, 4.8), i == 1 ? TEAL : BLUE);
    }
  }

  private static void more(Graphics2D g) {
    surface(g, rounded(2.5, 4, 19, 16, 3.5), GLASS);
    line(g, GLINT, 0.95f, 6, 7, 18, 7);
    for (int x : new int[] {7, 12, 17}) {
      surface(g, oval(x - 1.5, 10.7, 3, 3), SCREEN);
    }
  }

  private static void stopSign(Graphics2D g, boolean stop) {
    surface(g, polygon(7, 2.3, 17, 2.3, 21.7, 7, 21.7, 17, 17, 21.7, 7, 21.7, 2.3, 17, 2.3, 7),
        stop ? ORANGE : RED);
    if (stop) filled(g, rounded(8.1, 8.1, 7.8, 7.8, 1.5), WHITE);
    else cross(g, 7.7, 16.3, 2.5f);
  }

  private static void power(Graphics2D g) {
    surface(g, oval(2.3, 2.3, 19.4, 19.4), RED);
    mark(g, new Arc2D.Double(6.9, 7.2, 10.2, 10.2, 125, 290, Arc2D.OPEN), 2.05f);
    mark(g, path(12, 5.6, 12, 11.4), 2.05f);
  }

  private static void terminal(Graphics2D g, boolean active) {
    surface(g, rounded(2, 3.9, 20, 16, 3), active ? VIOLET : SLATE);
    surface(g, rounded(3.7, 6.3, 16.6, 11.7, 1.5), active ? SCREEN : BLUE);
    mark(g, path(7, 9.3, 10.5, 12.2, 7, 15.1), 1.8f);
    line(g, WHITE, 1.55f, 12.6, 15.2, 16.8, 15.2);
    if (active) surface(g, oval(16.5, 2, 5.4, 5.4), GREEN);
  }

  private static void shield(Graphics2D g, boolean active) {
    Path2D outline = path(12, 2, 21.5, 5, 20.2, 13.5);
    outline.quadTo(18.9, 18.5, 12, 22);
    outline.quadTo(5.1, 18.5, 3.8, 13.5);
    outline.lineTo(2.5, 5);
    outline.closePath();
    surface(g, outline, active ? GOLD : GLASS);
    draw(g, path(5.7, 7, 12, 4.9, 18.3, 7), GLINT, 0.9f);
    mark(g, path(7.5, 11.8, 10.6, 15, 16.6, 8.7), 2.35f);
  }

  private static void diagnostics(Graphics2D g) {
    surface(g, rounded(1.6, 3.5, 20.8, 17, 3), TEAL);
    surface(g, rounded(3.7, 5.6, 16.6, 12.3, 1.6), SCREEN);
    line(g, color(0x4d7894), 0.7f, 5.2, 9, 18.8, 9);
    line(g, color(0x4d7894), 0.7f, 5.2, 14.5, 18.8, 14.5);
    draw(g, path(5.3, 12.2, 8.1, 12.2, 10.2, 8, 13.1, 15.7, 15.1, 10.9, 18.7, 10.9),
        color(0xb1ff9d), 1.6f);
  }

  private static void jewel(Graphics2D g, Kind kind) {
    Finish finish = switch (kind) {
      case CHECK, PLAY -> GREEN;
      case ERROR -> RED;
      case PENDING -> SLATE;
      case RUNNING -> TEAL;
      case IDLE -> SILVER;
      default -> BLUE;
    };
    surface(g, oval(2.5, 2.5, 19, 19), finish);
    draw(g, new Arc2D.Double(4.7, 4.6, 14.6, 14.6, 52, 98, Arc2D.OPEN), GLINT, 0.9f);
    switch (kind) {
      case ABOUT -> {
        filled(g, oval(10.65, 6, 2.7, 2.7), WHITE);
        mark(g, path(12, 11.15, 12, 17.25), 2.55f);
      }
      case HELP -> {
        Path2D question = path(8.7, 9.1);
        question.curveTo(8.9, 5.2, 15.7, 5.5, 15.3, 9.5);
        question.curveTo(15.1, 11.5, 12, 11.8, 12, 14);
        mark(g, question, 2.25f);
        filled(g, oval(10.85, 16, 2.3, 2.3), WHITE);
      }
      case CHECK -> mark(g, path(6.9, 12.1, 10.5, 15.6, 17.1, 8.3), 2.45f);
      case ERROR -> cross(g, 8.1, 15.9, 2.75f);
      case PLAY -> filled(g, polygon(9.3, 6.9, 17.4, 12, 9.3, 17.1), WHITE);
      case RUNNING -> filled(g, polygon(9.5, 7.9, 16.1, 12, 9.5, 16.1), WHITE);
      case PENDING -> line(g, color(0xe1edf5), 2.2f, 8.5, 12, 15.5, 12);
      case IDLE -> mark(g, path(12, 7.4, 12, 12, 15.8, 14), 1.8f);
      default -> throw new IllegalArgumentException("Not a jewel icon: " + kind);
    }
  }

  private static void book(Graphics2D g) {
    surface(g, rounded(1.5, 5.2, 21, 15, 2.3), BLUE);
    Path2D left = path(3, 4);
    left.quadTo(8.6, 2.4, 12, 5.5);
    left.lineTo(12, 19.1);
    left.quadTo(8, 16.1, 3, 17.7);
    left.closePath();
    Path2D right = path(12, 5.5);
    right.quadTo(16.4, 2.4, 21, 4);
    right.lineTo(21, 17.7);
    right.quadTo(16, 16.2, 12, 19.1);
    right.closePath();
    surface(g, left, PAPER);
    surface(g, right, PAPER);
    for (int y : new int[] {8, 11, 14}) {
      line(g, LIGHT_INK, 1, 5, y, 9.5, y + 0.65);
      line(g, LIGHT_INK, 1, 14.4, y + 0.65, 18.6, y);
    }
    surface(g, polygon(16.6, 6.5, 19.6, 5.9, 19.6, 21.1, 18, 19.1, 16.6, 20.4), ORANGE);
  }

  private static void pencil(Graphics2D g) {
    page(g);
    surface(g, polygon(7.2, 20.3, 8.4, 15.6, 18.8, 5.2, 22.1, 8.5, 11.7, 19), GOLD);
    surface(g, polygon(16.9, 7.1, 19.3, 4.7, 22.6, 8, 20.2, 10.4), TEAL);
    filled(g, polygon(7.2, 20.3, 8, 17.5, 10, 19.4), INK);
    line(g, GLINT, 0.9f, 9.9, 15.8, 17.8, 7.9);
  }

  private static void close(Graphics2D g) {
    surface(g, rounded(3.6, 3.6, 16.8, 16.8, 4), SLATE);
    cross(g, 7.5, 16.5, 2.3f);
  }

  private static void warning(Graphics2D g) {
    surface(g, polygon(12, 2.5, 22, 20, 2, 20), GOLD);
    line(g, INK, 2.3f, 12, 8.4, 12, 13.3);
    filled(g, oval(10.9, 15.1, 2.2, 2.2), INK);
  }

  private static void box(Graphics2D g, boolean selected) {
    surface(g, rounded(3.4, 3.4, 17.2, 17.2, 3.7), selected ? BLUE : SILVER);
    if (selected) mark(g, path(7.1, 12.3, 10.4, 15.4, 17.1, 8.5), 2.6f);
    else surface(g, rounded(6, 6, 12, 12, 2), SCREEN);
  }

  private static void radio(Graphics2D g, boolean selected) {
    surface(g, oval(3, 3, 18, 18), selected ? BLUE : SILVER);
    surface(g, oval(6, 6, 12, 12), SCREEN);
    if (selected) surface(g, oval(8, 8, 8, 8), TEAL);
  }

  private static void chevron(Graphics2D g, double angle) {
    g.rotate(angle, 12, 12);
    surface(g, polygon(4, 15.3, 12, 6.2, 20, 15.3), SILVER);
  }

  private static void page(Graphics2D g) {
    surface(g, polygon(4.8, 2.4, 14.4, 2.4, 19.5, 7.4, 19.5, 21.4, 4.8, 21.4), PAPER);
    surface(g, polygon(14.4, 2.4, 19.5, 7.4, 14.4, 7.4), GLASS);
    for (int y : new int[] {10, 13, 16}) {
      line(g, LIGHT_INK, 1.05f, 7.5, y, y == 16 ? 13.2 : 16.5, y);
    }
  }

  private static void folder(Graphics2D g) {
    surface(g, polygon(2, 6, 3.5, 4.5, 9.1, 4.5, 11, 6.6, 20.7, 6.6, 20.7, 18.6, 2, 18.6), GOLD);
    filled(g, rounded(4, 8, 15.8, 8.5, 1.2), PAPER.color());
    surface(g, polygon(3, 10, 22, 10, 20.2, 20, 2, 20), GOLD);
    line(g, GLINT, 0.9f, 4.1, 11.6, 20.5, 11.6);
  }

  private static void picture(Graphics2D g) {
    surface(g, rounded(2, 3.9, 20, 16.3, 2.4), SILVER);
    surface(g, rounded(4.2, 6.1, 15.6, 11.9, 1), GLASS);
    filled(g, oval(14, 7.4, 3.8, 3.8), color(0xffcf72));
    filled(g, polygon(4.7, 17.5, 9.8, 10.8, 13.1, 14.9, 15.8, 12.8, 19.3, 17.5), GREEN.shade());
    draw(g, path(5, 17, 9.8, 11.3, 13.1, 15.2), GREEN.shine(), 0.8f);
  }

  private static void java(Graphics2D g) {
    surface(g, oval(2.5, 15.4, 18.1, 5.5), SILVER);
    draw(g, new Arc2D.Double(14.2, 8.6, 6.5, 6, -98, 193, Arc2D.OPEN), BLUE.shade(), 2);
    Path2D cup = path(4.5, 8.7, 17, 8.7, 16.1, 14);
    cup.quadTo(15.3, 18.3, 10.7, 18.3);
    cup.quadTo(6.1, 18.3, 5.3, 14);
    cup.closePath();
    surface(g, cup, PAPER);
    surface(g, oval(4.5, 7.4, 12.5, 3), SILVER);
    Path2D steam = path(10.7, 6.3);
    steam.curveTo(7.1, 4.1, 13.6, 3.7, 11.8, 1.7);
    draw(g, steam, ORANGE.color(), 1.4f);
  }

  private static void plugin(Graphics2D g, Kind kind) {
    Finish base = kind == Kind.PLUGIN_DISABLED ? SLATE : VIOLET;
    surface(g, rounded(3, 3, 14, 17, 2.8), base);
    surface(g, rounded(6.5, 6.5, 7, 7.6, 1.55), SCREEN);
    for (int y : new int[] {7, 11, 15}) {
      line(g, SILVER.shine(), 1.3f, 1.6, y, 4, y);
      line(g, SILVER.shine(), 1.3f, 16.2, y, 19.1, y);
    }
    Kind badge = switch (kind) {
      case PLUGIN_DISABLED -> Kind.PENDING;
      case PLUGIN_ALERT -> Kind.WARNING;
      default -> Kind.CHECK;
    };
    badge(g, 11, 11, 13, art -> paint(art, badge));
  }

  private static void copy(Graphics2D g) {
    surface(g, rounded(2.7, 2.7, 13.4, 16, 2), GLASS);
    badge(g, 3.9, 3.2, 20.4, HubIcon::page);
  }

  private static void trash(Graphics2D g) {
    surface(g, polygon(5.4, 7.7, 18.6, 7.7, 17.3, 21, 6.7, 21), RED);
    surface(g, rounded(4, 5.7, 16, 3.2, 1.7), SILVER);
    surface(g, rounded(8, 3, 8, 2.9, 1.5), SILVER);
    line(g, GLINT, 1, 9.2, 10.3, 9.8, 18.2);
    line(g, GLINT, 1, 14.8, 10.3, 14.2, 18.2);
  }

  private static void disk(Graphics2D g) {
    surface(g, polygon(3, 2.7, 17.3, 2.7, 21, 6.5, 21, 21.3, 3, 21.3), BLUE);
    surface(g, rounded(7, 3.4, 9.5, 6.7, 1), SILVER);
    filled(g, rounded(12.8, 4.5, 2.5, 4.5, 0.7), INK);
    surface(g, rounded(6, 12.4, 12, 8.1, 1.6), PAPER);
    line(g, LIGHT_INK, 1, 8.8, 15.3, 15.2, 15.3);
    line(g, LIGHT_INK, 1, 8.8, 17.3, 13.9, 17.3);
  }

  private static void export(Graphics2D g) {
    folder(g);
    badge(g, 9, -0.3, 15, art -> surface(art,
        polygon(8.2, 13, 8.2, 21, 15.8, 21, 15.8, 13, 20.6, 13, 12, 3.5, 3.4, 13), TEAL));
  }

  private static void receipt(Graphics2D g) {
    page(g);
    badge(g, 11, 12, 11.5, art -> jewel(art, Kind.CHECK));
  }

  private static void badge(Graphics2D g, double x, double y, double size,
                            java.util.function.Consumer<Graphics2D> render) {
    Graphics2D badge = (Graphics2D) g.create();
    try {
      badge.translate(x, y);
      badge.scale(size / 24, size / 24);
      render.accept(badge);
    } finally {
      badge.dispose();
    }
  }

  private static void surface(Graphics2D g, Shape shape, Finish finish) {
    Graphics2D shadow = (Graphics2D) g.create();
    try {
      shadow.translate(0, 0.95);
      filled(shadow, shape, new Color(9, 17, 26, 126));
    } finally {
      shadow.dispose();
    }
    Rectangle2D bounds = shape.getBounds2D();
    float top = (float) bounds.getMinY();
    float bottom = (float) bounds.getMaxY();
    g.setPaint(new LinearGradientPaint(0, top, 0, bottom, new float[] {0, 0.39f, 0.64f, 1},
        new Color[] {finish.shine(), finish.color(), finish.color(), finish.shade()}));
    g.fill(shape);
    draw(g, shape, finish.edge(), 0.95f);
    g.setPaint(new LinearGradientPaint(0, top, 0, bottom, new float[] {0, 0.46f, 1},
        new Color[] {GLINT, new Color(255, 255, 255, 36), new Color(255, 255, 255, 0)}));
    g.setStroke(stroke(0.55f));
    g.draw(shape);
  }

  private static void cross(Graphics2D g, double low, double high, float width) {
    mark(g, path(low, low, high, high), width);
    mark(g, path(high, low, low, high), width);
  }

  private static void mark(Graphics2D g, Shape path, float width) {
    draw(g, path, new Color(23, 40, 58, 170), width + 0.85f);
    draw(g, path, WHITE, width);
  }

  private static void line(Graphics2D g, Color color, float width, double x1, double y1, double x2, double y2) {
    draw(g, path(x1, y1, x2, y2), color, width);
  }

  private static void draw(Graphics2D g, Shape shape, Color color, float width) {
    g.setColor(color);
    g.setStroke(stroke(width));
    g.draw(shape);
  }

  private static void filled(Graphics2D g, Shape shape, Color color) {
    g.setColor(color);
    g.fill(shape);
  }

  private static BasicStroke stroke(float width) {
    return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
  }

  private static Shape rounded(double x, double y, double width, double height, double round) {
    return new RoundRectangle2D.Double(x, y, width, height, round, round);
  }

  private static Shape oval(double x, double y, double width, double height) {
    return new Ellipse2D.Double(x, y, width, height);
  }

  private static Path2D path(double... points) {
    Path2D path = new Path2D.Double();
    path.moveTo(points[0], points[1]);
    for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
    return path;
  }

  private static Shape polygon(double... points) {
    Path2D polygon = path(points);
    polygon.closePath();
    return polygon;
  }

  private static Finish finish(int shine, int color, int shade, int edge) {
    return new Finish(color(shine), color(color), color(shade), color(edge));
  }

  private static Color color(int rgb) {
    return new Color(rgb);
  }
}
