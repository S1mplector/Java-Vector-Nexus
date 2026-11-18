package com.jvn.hub;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSliderUI;

/** Windows 7 Aero scroll bars and trackbars for the Swing hub and its dialogs. */
public final class HubAeroControls {
  private static final Color TRACK = Color.decode("#e9e9e9");
  private static final Color EDGE = Color.decode("#979797");
  private static final Color HOVER_EDGE = Color.decode("#4287ad");
  private static final Color PRESSED_EDGE = Color.decode("#2c6285");

  private HubAeroControls() {}

  static void installDefaults() {
    UIManager.put("ScrollBarUI", ScrollBarUi.class.getName());
    UIManager.put("SliderUI", SliderUi.class.getName());
    UIManager.put("ScrollBar.width", 17);
  }

  public static final class ScrollBarUi extends BasicScrollBarUI {
    private final int size;

    public ScrollBarUi() { this(17); }
    ScrollBarUi(int size) { this.size = size; }
    public static ComponentUI createUI(JComponent component) { return new ScrollBarUi(); }

    @Override protected JButton createDecreaseButton(int direction) { return new Arrow(direction, size); }
    @Override protected JButton createIncreaseButton(int direction) { return new Arrow(direction, size); }
    @Override protected Dimension getMinimumThumbSize() { return new Dimension(size, size); }

    @Override protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
      Graphics2D g2 = (Graphics2D) g.create();
      boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
      g2.setPaint(new GradientPaint(bounds.x, bounds.y, Color.decode("#202020"),
          vertical ? bounds.x + bounds.width : bounds.x, vertical ? bounds.y : bounds.y + bounds.height,
          Color.decode("#303030")));
      g2.fill(bounds);
      g2.dispose();
    }

    @Override protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
      if (bounds.isEmpty()) return;
      Graphics2D g2 = (Graphics2D) g.create();
      g2.translate(bounds.x, bounds.y);
      boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
      if (scrollbar.isEnabled()) paintButton(g2, bounds.width, bounds.height, vertical, isThumbRollover(), isDragging);
      g2.dispose();
    }
  }

  public static final class SliderUi extends BasicSliderUI {
    private boolean rollover;
    private final MouseAdapter mouse = new MouseAdapter() {
      @Override public void mouseMoved(MouseEvent event) {
        updateRollover(thumbRect.contains(event.getPoint()));
      }
      @Override public void mouseExited(MouseEvent event) { updateRollover(false); }
    };

    public SliderUi(JSlider slider) { super(slider); }
    public static ComponentUI createUI(JComponent component) { return new SliderUi((JSlider) component); }

    @Override protected void installListeners(JSlider slider) {
      super.installListeners(slider);
      slider.addMouseListener(mouse);
      slider.addMouseMotionListener(mouse);
    }

    @Override protected void uninstallListeners(JSlider slider) {
      slider.removeMouseListener(mouse);
      slider.removeMouseMotionListener(mouse);
      super.uninstallListeners(slider);
    }

    private void updateRollover(boolean state) {
      if (rollover == state) return;
      rollover = state;
      slider.repaint(thumbRect);
    }

    @Override protected Dimension getThumbSize() {
      return slider.getOrientation() == SwingConstants.HORIZONTAL ? new Dimension(12, 21) : new Dimension(21, 12);
    }

    @Override public void paintTrack(Graphics g) {
      boolean horizontal = slider.getOrientation() == SwingConstants.HORIZONTAL;
      int x = horizontal ? trackRect.x : trackRect.x + (trackRect.width - 5) / 2;
      int y = horizontal ? trackRect.y + (trackRect.height - 5) / 2 : trackRect.y;
      int w = horizontal ? trackRect.width : 5;
      int h = horizontal ? 5 : trackRect.height;
      g.setColor(Color.decode("#868686"));
      g.fillRect(x, y, w, h);
      g.setColor(TRACK);
      g.fillRect(x + 1, y + 1, Math.max(0, w - 2), Math.max(0, h - 2));
    }

    @Override public void paintThumb(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.translate(thumbRect.x, thumbRect.y);
      if (slider.getOrientation() == SwingConstants.VERTICAL) {
        g2.translate(21, 0);
        g2.rotate(Math.PI / 2);
      }
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Polygon thumb = new Polygon(new int[]{1, 10, 11, 11, 6, 0, 0}, new int[]{0, 0, 1, 15, 20, 15, 1}, 7);
      boolean hovered = slider.isEnabled() && (rollover || slider.hasFocus());
      g2.setPaint(shine(true, 12, 21, hovered, isDragging()));
      g2.fill(thumb);
      g2.setColor(isDragging() ? PRESSED_EDGE : hovered ? HOVER_EDGE : EDGE);
      g2.draw(thumb);
      g2.dispose();
    }
  }

  private static final class Arrow extends JButton {
    private final int direction;

    Arrow(int direction, int size) {
      this.direction = direction;
      setPreferredSize(new Dimension(size, size));
      setMinimumSize(getPreferredSize());
      setFocusable(false);
      setRolloverEnabled(true);
      setBorderPainted(false);
    }

    @Override protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      boolean active = isEnabled();
      paintButton(g2, getWidth(), getHeight(), false, active && getModel().isRollover(), active && getModel().isPressed());
      int iconSize = Math.min(getWidth(), getHeight()) * 3 / 4;
      if (iconSize > 0) {
        HubIcon arrow = HubIcon.of(switch (direction) {
          case SwingConstants.SOUTH -> HubIcon.Kind.ARROW_DOWN;
          case SwingConstants.EAST -> HubIcon.Kind.ARROW_RIGHT;
          case SwingConstants.WEST -> HubIcon.Kind.ARROW_LEFT;
          default -> HubIcon.Kind.ARROW_UP;
        }, iconSize);
        arrow.paintIcon(this, g2, (getWidth() - iconSize) / 2, (getHeight() - iconSize) / 2);
      }
      g2.dispose();
    }
  }

  private static void paintButton(Graphics2D g, int w, int h, boolean vertical, boolean hovered, boolean pressed) {
    if (w < 5 || h < 5) return;
    g.setColor(Color.decode(pressed ? "#244c63" : hovered ? "#3f7189" : "#181818"));
    g.fillRoundRect(1, 1, w - 2, h - 2, 2, 2);
    g.setColor(Color.decode(pressed ? "#5b7c90" : hovered ? "#7998a8" : "#777777"));
    g.fillRect(2, 2, w - 4, h - 4);
    Color top = Color.decode(pressed ? "#3f5d70" : hovered ? "#5c7480" : "#626262");
    Color bottom = Color.decode(pressed ? "#263c4e" : hovered ? "#354b59" : "#404040");
    g.setPaint(new GradientPaint(0, 0, top, vertical ? w : 0, vertical ? 0 : h, bottom));
    g.fillRect(3, 3, w - 6, h - 6);
  }

  private static GradientPaint shine(boolean vertical, int w, int h, boolean hovered, boolean pressed) {
    Color top = Color.decode(pressed ? "#b6d8eb" : hovered ? "#effaff" : "#fcfcfc");
    Color bottom = Color.decode(pressed ? "#75acd1" : hovered ? "#a9d7ef" : "#dddddd");
    return new GradientPaint(0, 0, top, vertical ? w : 0, vertical ? 0 : h, bottom);
  }
}
