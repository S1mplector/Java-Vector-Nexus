package com.jvn.fx.menu;

import com.jvn.branding.EngineBranding;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.fx.image.FxImageEffectCache;
import com.jvn.fx.ui.AeroGlass;
import com.jvn.fx.ui.FxTextMetrics;
import com.jvn.fx.ui.ProjectFontResolver;
import com.jvn.fx.ui.RenderedBoundsListener;
import java.io.File;
import java.util.Map;
import java.util.function.Function;
import javafx.embed.swing.SwingFXUtils;
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
  private final FxTextMetrics textMetrics = new FxTextMetrics();
  private Image titleImage;
  private Image engineWordmark;
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

  private static Bounds itemBounds(MainMenuScene scene, int index, double w, double h, boolean useProjectBounds) {
    MenuRenderer.Rect rect = useProjectBounds ? MenuRenderer.explicitItemRect(scene.getItem(index), 0, w, h) : null;
    return rect == null ? bounds(index, scene.getItemCount(), w, h) : new Bounds(rect.x(), rect.y(), rect.w(), rect.h());
  }

  private static boolean hasLegacyTutorialRows(MainMenuScene scene) {
    if (!"assets/tutorial/white.png".equals(scene.getMenuScreen().backgroundAsset())) return false;
    int count = scene.getItemCount();
    if (count < 2 || count > 4 || !"new_game".equals(scene.getItem(0).id())
        || !"quit".equals(scene.getItem(count - 1).id())) return false;
    // The old wizard left its list coordinates on the tutorial's dock, including a
    // gap for the removed Extras button. Recognize that generated layout exactly.
    for (int i = 0; i < count; i++) {
      MenuItemSpec item = scene.getItem(i);
      if (i > 0 && i < count - 1 && !"continue".equals(item.id()) && !"settings".equals(item.id())) return false;
      int row = i + (i == count - 1 ? 1 : 0);
      if (!matchesCoordinate(item.boundsX(), .28) || !matchesCoordinate(item.boundsWidth(), .44)
          || !matchesCoordinate(item.boundsHeight(), .072)
          || !matchesCoordinate(item.boundsY(), .34 + .082 * row)) return false;
    }
    return true;
  }

  private static boolean matchesCoordinate(Double value, double expected) {
    return value != null && Math.abs(value - expected) < 0.000001;
  }

  int hover(MainMenuScene scene, double w, double h, double x, double y) {
    boolean useProjectBounds = !hasLegacyTutorialRows(scene);
    for (int i = 0; i < scene.getItemCount(); i++) {
      Bounds b = itemBounds(scene, i, w, h, useProjectBounds);
      // Include the whole lift envelope, so a rising button cannot lose its hover.
      if (scene.isItemEnabled(i) && new Bounds(b.x(), b.y() - 16, b.width(), b.height() + 16).contains(x, y)) return i;
    }
    return -1;
  }

  void render(GraphicsContext gc, MainMenuScene scene, MenuTheme theme, File root, double w, double h,
              double fontScale, RenderedBoundsListener boundsListener, Function<String, Image> loadIcon) {
    long now = System.nanoTime();
    double dt = previousTime == 0 ? 1.0 / 60 : Math.min(.05, (now - previousTime) / 1e9);
    previousTime = now;
    long timeMs = now / 1_000_000;
    int count = scene.getItemCount();
    boolean useProjectBounds = !hasLegacyTutorialRows(scene);
    if (focus.length != count) focus = new double[count];
    gc.save();
    gc.setTextAlign(TextAlignment.CENTER);
    gc.setTextBaseline(VPos.BASELINE);
    String title = scene.getDisplayTitle();
    if (title == null || title.isBlank()) title = theme.getTitleText();
    if (title == null || title.isBlank()) title = "JVN";
    Font font = ProjectFontResolver.resolve(root, theme.getDockTitleFontFamily(), FontWeight.NORMAL,
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
    boolean showVersion = theme.showsEngineVersionOnDock();
    if (showVersion) drawEngineVersion(gc, theme, w, h, fontScale);
    String subtitle = scene.getDisplaySubtitle();
    if (subtitle != null) {
      gc.setFill(theme.getHintColor());
      gc.setFont(Font.font(theme.interfaceFontFamily("SansSerif"), Math.min(18, w * .015) * fontScale));
      gc.fillText(subtitle, w / 2, h * (showVersion ? .61 : .53), w * .88);
    }

    if (count > 0) {
      Bounds first = bounds(0, count, w, h), last = bounds(count - 1, count, w, h);
      double trayWidth = last.x() + last.width() - first.x() + 32;
      if (theme.usesAeroGlass()) {
        AeroGlass.TRAY.draw(gc, first.x() - 16, first.y() + 20, trayWidth, first.height() + 2, 26);
      } else {
        gc.setFill(Color.WHITE.interpolate(theme.getAccentColor(), .08));
        gc.fillRoundRect(first.x() - 16, first.y() + 20, trayWidth, first.height() + 2, 32, 32);
      }
    }
    double layerOpacity = gc.getGlobalAlpha();
    for (int i = 0; i < count; i++) {
      boolean selected = scene.getSelected() == i && scene.isItemEnabled(i);
      double target = selected ? 1 : 0;
      focus[i] = theme.isMenuMotion() ? focus[i] + (target - focus[i]) * (1 - Math.exp(-dt * 16)) : target;
      Bounds b = itemBounds(scene, i, w, h, useProjectBounds);
      double y = b.y() - (theme.isMenuMotion() ? focus[i] * 14 : 0);
      if (boundsListener != null)
        boundsListener.accept(scene.getItem(i).id(), b.x(), y, b.width(), b.height(), 0, 0, w, h);
      gc.setGlobalAlpha(layerOpacity * (scene.isItemEnabled(i) ? 1 : .4));
      if (theme.usesAeroGlass()) {
        (selected ? AeroGlass.FOCUSED : AeroGlass.BUTTON).draw(gc, b.x(), y, b.width(), b.height(), 16);
      } else {
        gc.setFill(Color.WHITE.interpolate(theme.getAccentColor(), .20));
        gc.fillRoundRect(b.x(), y + 5, b.width(), b.height(), 20, 20);
        gc.setFill(Color.WHITE.interpolate(Color.WHITE.interpolate(theme.getAccentColor(), .12), focus[i]));
        gc.fillRoundRect(b.x(), y, b.width(), b.height(), 20, 20);
        gc.setStroke(Color.WHITE.interpolate(theme.getAccentColor(), .24).interpolate(theme.getAccentColor(), focus[i]));
        gc.setLineWidth(selected ? 2 : 1);
        gc.strokeRoundRect(b.x(), y, b.width(), b.height(), 20, 20);
      }
      Color ink = selected ? theme.getItemSelectedColor() : theme.getItemColor();
      gc.setStroke(ink); gc.setFill(ink); gc.setLineWidth(1.8);
      double cx = b.x() + b.width() / 2, cy = y + b.height() * .33;
      MenuItemSpec item = scene.getMenuItemSpec(i);
      String iconPath = item.iconPath();
      if (iconPath == null && theme.isTutorialDock()) iconPath = MenuTheme.tutorialDockIcon(item.id());
      Image customIcon = iconPath == null ? null : loadIcon.apply(iconPath);
      if (customIcon != null) {
        double size = Math.min(42.0, Math.min(b.height() * .44, b.width() * .36));
        double scale = size / Math.max(customIcon.getWidth(), customIcon.getHeight());
        double iw = customIcon.getWidth() * scale, ih = customIcon.getHeight() * scale;
        gc.drawImage(customIcon, cx - iw / 2, cy - ih / 2, iw, ih);
      } else {
        icon(gc, item.id(), cx, cy);
      }
      gc.setFont(Font.font(theme.interfaceFontFamily("SansSerif"), FontWeight.MEDIUM,
          Math.min(18 * fontScale, b.width() * .15)));
      gc.fillText(scene.getDisplayItems()[i], cx, y + b.height() * .78, b.width() - 16);
    }
    gc.setGlobalAlpha(layerOpacity);
    gc.setFill(theme.getHintColor()); gc.setFont(Font.font(theme.interfaceFontFamily("SansSerif"), 13 * fontScale));
    String hint = scene.getDisplayHints();
    if (hint != null) gc.fillText(hint, w / 2, h * .94, w * .9);
    gc.restore();
  }

  private void drawEngineVersion(GraphicsContext gc, MenuTheme theme, double w, double h, double fontScale) {
    if (engineWordmark == null) engineWordmark = SwingFXUtils.toFXImage(EngineBranding.logo(), null);
    String version = "v" + EngineBranding.version();
    double size = Math.min(16 * fontScale, Math.min(w * .03, h * .038));
    Font font = ProjectFontResolver.resolve(null, theme.interfaceFontFamily("SansSerif"), FontWeight.MEDIUM, size, "SansSerif");
    double prefixWidth = textMetrics.width("For", font);
    double versionWidth = textMetrics.width(version, font);
    double logoWidth = size * 4.7;
    double logoHeight = logoWidth * engineWordmark.getHeight() / engineWordmark.getWidth();
    double gap = size * .35;
    double left = (w - prefixWidth - logoWidth - versionWidth - 2 * gap) / 2;
    double centerY = h * .545;

    gc.save();
    gc.setFont(font);
    gc.setFill(theme.getTitleColor());
    gc.setTextBaseline(VPos.CENTER);
    gc.fillText("For", left + prefixWidth / 2, centerY);
    double logoX = left + prefixWidth + gap;
    gc.drawImage(engineWordmark, logoX, centerY - logoHeight / 2, logoWidth, logoHeight);
    gc.fillText(version, logoX + logoWidth + gap + versionWidth / 2, centerY);
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

  void clear() { titleImage = null; titleKey = null; effects.clear(); textMetrics.clear(); previousTime = 0; }
  @Override public void close() { clear(); effects.close(); }
}
