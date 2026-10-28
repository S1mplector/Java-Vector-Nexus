package com.jvn.core.scene2d;

/**
 * A text-rendering 2D entity that draws a single line of styled text.
 *
 * <p>{@code Label2D} supports configurable font family, size, weight (bold),
 * colour (RGBA), and horizontal alignment. It measures the text width at
 * render time to correctly offset centred and right-aligned labels.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Label2D title = new Label2D("Hello World");
 * title.setFont("Noto Sans", 32, true);
 * title.setColor(1.0, 0.8, 0.0, 1.0); // gold
 * title.setAlign(Label2D.Align.CENTER);
 * title.setPosition(960, 100);
 * scene.add(title);
 * }</pre>
 *
 * @see Entity2D
 * @see Blitter2D#drawText(String, double, double, double, boolean)
 */
public class Label2D extends Entity2D {

  /** The text string to draw. */
  private String text;

  /** Font size in logical pixels. */
  private double size = 16;

  /** Whether to render in bold weight. */
  private boolean bold = false;

  /** Font family name (e.g. "Arial"). */
  private String fontFamily = "Arial";

  /** Text colour — red channel [0.0, 1.0]. */
  private double r = 1;

  /** Text colour — green channel [0.0, 1.0]. */
  private double g = 1;

  /** Text colour — blue channel [0.0, 1.0]. */
  private double blue = 1;

  /** Text opacity [0.0, 1.0]. */
  private double a = 1;

  /**
   * Horizontal text alignment relative to the entity's position.
   *
   * <ul>
   *   <li>{@code LEFT} — text starts at the entity position (default).</li>
   *   <li>{@code CENTER} — text is centred on the entity position.</li>
   *   <li>{@code RIGHT} — text ends at the entity position.</li>
   * </ul>
   */
  public enum Align { LEFT, CENTER, RIGHT }

  /** Current horizontal alignment. */
  private Align align = Align.LEFT;

  /**
   * Construct a label with the given text content.
   *
   * @param text the string to display
   */
  public Label2D(String text) { this.text = text; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Accessors
  // ──────────────────────────────────────────────────────────────────────────

  /** @return the current text string */
  public String getText() { return text; }

  /** @param t new text to display */
  public void setText(String t) { this.text = t; }

  /**
   * Set the text colour (RGBA, normalised [0.0, 1.0]).
   *
   * @param r red
   * @param g green
   * @param b blue
   * @param a alpha
   */
  public void setColor(double r, double g, double b, double a) { this.r = r; this.g = g; this.blue = b; this.a = a; }

  /**
   * Set the font family, size, and bold flag.
   *
   * @param family font family name
   * @param size   font size in logical pixels
   * @param bold   whether to use bold weight
   */
  public void setFont(String family, double size, boolean bold) { this.fontFamily = family; this.size = size; this.bold = bold; }

  /** @param a horizontal alignment; {@code null} defaults to {@link Align#LEFT} */
  public void setAlign(Align a) { this.align = a == null ? Align.LEFT : a; }

  /** @return font size in logical pixels */
  public double getSize() { return size; }

  /** @return {@code true} if bold weight is active */
  public boolean isBold() { return bold; }

  /** @return the font family name */
  public String getFontFamily() { return fontFamily; }

  /** @return the current horizontal alignment */
  public Align getAlign() { return align; }

  /** @return red colour component [0.0, 1.0] */
  public double getColorR() { return r; }

  /** @return green colour component [0.0, 1.0] */
  public double getColorG() { return g; }

  /** @return blue colour component [0.0, 1.0] */
  public double getColorB() { return blue; }

  /** @return alpha (opacity) [0.0, 1.0] */
  public double getAlpha() { return a; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Rendering
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Draw the label text at the entity's position, offset according to the
   * current {@link Align} setting.
   */
  @Override
  public void render(Blitter2D b) {
    if (text == null) return;
    b.push();
    b.setGlobalAlpha(a);
    b.setFill(r, g, blue, 1);
    b.setFont(fontFamily, size, bold);
    double w = align == Align.LEFT ? 0.0 : b.measureTextWidth(text, size, bold);
    double ox = 0;
    if (align == Align.CENTER) ox = -w / 2.0;
    else if (align == Align.RIGHT) ox = -w;
    b.drawText(text, ox, 0, size, bold);
    b.pop();
  }
}
