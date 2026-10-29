package com.jvn.fx.menu;

import com.jvn.core.menu.MainMenuScene;
import com.jvn.fx.image.FxImageEffectCache;
import com.jvn.fx.ui.ProjectFontResolver;
import java.io.File;
import java.util.Map;
import javafx.geometry.VPos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/** Optional title-screen presentation; shares the ordinary menu actions and selection model. */
final class DockMenuPresentation implements AutoCloseable {
  private final FxImageEffectCache effects = new FxImageEffectCache();
  private Image titleImage;
  private String titleKey;
  private double[] focus = new double[0];
  private long previousTime;

  record Bounds(double x, double y, double width, double height) {
    boolean contains(double px, double py) {
      return px >= x && px <= x + width && py >= y && py <= y + height;
    }
  }

  static Bounds bounds(int index, int count, double w, double h) {
    double gap = Math.min(18, w * .014);
    double width = Math.min(168, (w * .88 - gap * Math.max(0, count - 1)) / Math.max(1, count));
    double total = width * count + gap * Math.max(0, count - 1);
    return new Bounds((w - total) / 2 + index * (width + gap), h * .68, width, Math.min(112, h * .17));
  }

  int hover(MainMenuScene scene, double w, double h, double x, double y) {
    for (int i = 0; i < scene.getItemCount(); i++) {
      Bounds b = bounds(i, scene.getItemCount(), w, h);
      // Include the whole lift envelope, so a rising button cannot lose its hover.
      if (scene.isItemEnabled(i) && new Bounds(b.x(), b.y() - 16, b.width(), b.height() + 16).contains(x, y)) return i;
    }
    return -1;
  }

  void render(GraphicsContext gc, MainMenuScene scene, MenuTheme theme, File root, double w, double h, double fontScale) {
    long now = System.nanoTime();
    double dt = previousTime == 0 ? 1.0 / 60 : Math.min(.05, (now - previousTime) / 1e9);
    previousTime = now;
    long timeMs = now / 1_000_000;
    int count = scene.getItemCount();
    if (focus.length != count) focus = new double[count];
    gc.save();
    gc.setTextAlign(TextAlignment.CENTER);
    gc.setTextBaseline(VPos.BASELINE);
    gc.setFill(theme.getHintColor());
    gc.setFont(Font.font("SansSerif", FontWeight.BOLD, Math.min(13, w * .012) * fontScale));
    gc.fillText("THE LAVENDER WORKSHOP", w / 2, h * .19);

    String title = scene.getDisplayTitle();
    if (title == null || title.isBlank()) title = theme.getTitleText();
    if (title == null || title.isBlank()) title = "JVN";
    Font font = ProjectFontResolver.resolve(root, theme.getTitleFontFamily(), FontWeight.NORMAL,
        Math.min(theme.getTitleFontSize() * fontScale, w * .085), "SansSerif");
    String key = title + "|" + font + "|" + theme.getTitleColor();
    if (!key.equals(titleKey)) {
      Text measure = new Text(title); measure.setFont(font);
      int tw = Math.max(1, (int) Math.ceil(measure.getLayoutBounds().getWidth()) + 32);
      int th = Math.max(1, (int) Math.ceil(font.getSize() * 1.6));
      Canvas titleCanvas = new Canvas(tw, th);
      GraphicsContext titleGc = titleCanvas.getGraphicsContext2D();
      titleGc.setFont(font); titleGc.setFill(theme.getTitleColor());
      titleGc.fillText(title, 16, font.getSize() * 1.15);
      SnapshotParameters snapshot = new SnapshotParameters(); snapshot.setFill(Color.TRANSPARENT);
      titleImage = titleCanvas.snapshot(snapshot, null); titleKey = key; effects.clear();
    }
    Image titleFrame = theme.isMenuMotion()
        ? effects.frame(titleImage, theme.getTitleImageEffect(), Map.of(), timeMs)
        : titleImage;
    double titleWidth = Math.min(w * .9, titleImage.getWidth());
    double titleHeight = titleImage.getHeight() * titleWidth / titleImage.getWidth();
    gc.drawImage(titleFrame, (w - titleWidth) / 2, h * .26, titleWidth, titleHeight);
    String subtitle = scene.getDisplaySubtitle();
    if (subtitle != null) {
      gc.setFill(theme.getHintColor());
      gc.setFont(Font.font("SansSerif", Math.min(18, w * .015) * fontScale));
      gc.fillText(subtitle, w / 2, h * .53, w * .88);
    }

    if (count > 0) {
      Bounds first = bounds(0, count, w, h), last = bounds(count - 1, count, w, h);
      gc.setFill(Color.web("#f5f1fa"));
      gc.fillRoundRect(first.x() - 16, first.y() + 20, last.x() + last.width() - first.x() + 32, first.height() + 2, 32, 32);
    }
    for (int i = 0; i < count; i++) {
      boolean selected = scene.getSelected() == i && scene.isItemEnabled(i);
      double target = selected ? 1 : 0;
      focus[i] = theme.isMenuMotion() ? focus[i] + (target - focus[i]) * (1 - Math.exp(-dt * 16)) : target;
      Bounds b = bounds(i, count, w, h);
      double y = b.y() - (theme.isMenuMotion() ? focus[i] * 14 : 0);
      gc.setGlobalAlpha(scene.isItemEnabled(i) ? 1 : .4);
      gc.setFill(Color.web("#e9e0f1"));
      gc.fillRoundRect(b.x(), y + 5, b.width(), b.height(), 20, 20);
      gc.setFill(Color.WHITE.interpolate(Color.web("#f3eafb"), focus[i]));
      gc.fillRoundRect(b.x(), y, b.width(), b.height(), 20, 20);
      gc.setStroke(Color.web("#ded5e7").interpolate(theme.getAccentColor(), focus[i]));
      gc.setLineWidth(selected ? 2 : 1);
      gc.strokeRoundRect(b.x(), y, b.width(), b.height(), 20, 20);
      Color ink = selected ? theme.getItemSelectedColor() : theme.getItemColor();
      gc.setStroke(ink); gc.setFill(ink); gc.setLineWidth(1.8);
      double cx = b.x() + b.width() / 2, cy = y + b.height() * .33;
      icon(gc, scene.getMenuItemSpec(i).id(), cx, cy);
      gc.setFont(Font.font("SansSerif", FontWeight.MEDIUM, Math.min(18 * fontScale, b.width() * .15)));
      gc.fillText(scene.getDisplayItems()[i], cx, y + b.height() * .78, b.width() - 16);
    }
    gc.setGlobalAlpha(1);
    gc.setFill(theme.getHintColor()); gc.setFont(Font.font("SansSerif", 13 * fontScale));
    String hint = scene.getDisplayHints();
    if (hint != null) gc.fillText(hint, w / 2, h * .94, w * .9);
    gc.restore();
  }

  private static void icon(GraphicsContext gc, String id, double x, double y) {
    switch (id) {
      case "new_game" -> { gc.beginPath(); gc.moveTo(x - 7, y - 10); gc.lineTo(x + 10, y); gc.lineTo(x - 7, y + 10); gc.closePath(); gc.stroke(); }
      case "continue" -> { gc.strokeRoundRect(x - 11, y - 8, 22, 17, 3, 3); gc.strokeLine(x - 8, y - 12, x + 1, y - 12); }
      case "settings" -> { for (int i = -1; i <= 1; i++) { gc.strokeLine(x - 11, y + i * 7, x + 11, y + i * 7); gc.fillOval(x + i * 4 - 2.5, y + i * 7 - 2.5, 5, 5); } }
      case "quit" -> { gc.strokeArc(x - 10, y - 10, 20, 20, 130, 280, javafx.scene.shape.ArcType.OPEN); gc.strokeLine(x, y - 13, x, y); }
      default -> { gc.strokeRect(x - 9, y - 10, 18, 20); gc.strokeLine(x - 5, y - 4, x + 5, y - 4); gc.strokeLine(x - 5, y + 2, x + 5, y + 2); }
    }
  }

  void clear() { titleImage = null; titleKey = null; effects.clear(); previousTime = 0; }
  @Override public void close() { clear(); effects.close(); }
}
