package com.jvn.core.input;

import java.util.Locale;
import java.util.Objects;

/**
 * Backend-agnostic input identifier that can represent keyboard keys,
 * mouse buttons, wheel directions, or gamepad controls.
 *
 * <p>{@code InputCode} is an immutable value object used as the key for all
 * input-state lookups in {@link Input} and binding tables in {@link ActionMap}.
 * It unifies five device types under a single comparable identity:</p>
 * <ul>
 *   <li>{@link Device#KEYBOARD} — identified by canonical key name.</li>
 *   <li>{@link Device#MOUSE_BUTTON} — identified by button index (1-based).</li>
 *   <li>{@link Device#MOUSE_WHEEL} — identified by vertical scroll direction.</li>
 *   <li>{@link Device#GAMEPAD_BUTTON} — identified by gamepad index + button name.</li>
 *   <li>{@link Device#GAMEPAD_AXIS} — identified by gamepad index + axis name.</li>
 * </ul>
 *
 * <h2>Serialisation</h2>
 * <p>{@link #encode()} and {@link #decode(String)} support a pipe-delimited
 * text format ({@code DEVICE|gamepad|index|name}) suitable for config files.</p>
 *
 * @see Input
 * @see ActionMap
 * @see ActionBindingProfile
 */
public final class InputCode {

  /** The physical device family that this input code belongs to. */
  public enum Device { KEYBOARD, MOUSE_BUTTON, MOUSE_WHEEL, GAMEPAD_BUTTON, GAMEPAD_AXIS }

  /** Device family. */
  private final Device device;

  /** Canonical name (key name, button name, or axis name); may be {@code null} for mouse buttons. */
  private final String name;

  /** Numeric index (mouse button number); -1 when not applicable. */
  private final int index;

  /** Gamepad index (0-based); 0 for keyboard/mouse. */
  private final int gamepad;

  private InputCode(Device device, String name, int index, int gamepad) {
    this.device = device;
    this.name = name;
    this.index = index;
    this.gamepad = gamepad;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Factory methods
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Create a keyboard key code.
   *
   * @param keyName the key name (e.g. "SPACE", "A"); canonicalised to upper-case
   * @return an input code for the given key
   */
  public static InputCode key(String keyName) {
    return new InputCode(Device.KEYBOARD, canonicalKey(keyName), -1, 0);
  }

  /**
   * Create a mouse button code.
   *
   * @param button button index (1 = primary, 2 = secondary, 3 = middle)
   * @return an input code for the given button
   */
  public static InputCode mouse(int button) {
    return new InputCode(Device.MOUSE_BUTTON, null, button, 0);
  }

  private static final InputCode WHEEL_UP = new InputCode(Device.MOUSE_WHEEL, "UP", -1, 0);
  private static final InputCode WHEEL_DOWN = new InputCode(Device.MOUSE_WHEEL, "DOWN", -1, 0);

  /** Return the binding for a vertical scroll delta, or null for no vertical motion. */
  public static InputCode mouseWheel(double deltaY) {
    if (!Double.isFinite(deltaY) || deltaY == 0) return null;
    return deltaY > 0 ? WHEEL_UP : WHEEL_DOWN;
  }

  /**
   * Create a gamepad digital button code.
   *
   * @param padIndex   gamepad index (0-based; clamped to ≥ 0)
   * @param buttonName button name (e.g. "A", "START")
   */
  public static InputCode gamepadButton(int padIndex, String buttonName) {
    return new InputCode(Device.GAMEPAD_BUTTON, canonicalKey(buttonName), -1, Math.max(0, padIndex));
  }

  /**
   * Create a gamepad analog axis code.
   *
   * @param padIndex gamepad index (0-based)
   * @param axisName axis name (e.g. "LEFT_X", "RIGHT_TRIGGER")
   */
  public static InputCode gamepadAxis(int padIndex, String axisName) {
    return new InputCode(Device.GAMEPAD_AXIS, canonicalKey(axisName), -1, Math.max(0, padIndex));
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Accessors
  // ──────────────────────────────────────────────────────────────────────────

  /** @return the device family */
  public Device device() { return device; }

  /** @return the canonical name (key/button/axis), or {@code null} */
  public String name() { return name; }

  /** @return the numeric index (mouse button), or -1 */
  public int index() { return index; }

  /** @return the gamepad index (0 for keyboard/mouse) */
  public int gamepad() { return gamepad; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Display & serialisation
  // ──────────────────────────────────────────────────────────────────────────

  /** @return a human-readable label like {@code "Key:SPACE"} or {@code "Mouse:1"} */
  public String displayName() {
    return switch (device) {
      case KEYBOARD -> "Key:" + (name == null ? "" : name);
      case MOUSE_BUTTON -> "Mouse:" + index;
      case MOUSE_WHEEL -> "Wheel:" + name;
      case GAMEPAD_BUTTON -> "Pad" + gamepad + ":Btn:" + (name == null ? "" : name);
      case GAMEPAD_AXIS -> "Pad" + gamepad + ":Axis:" + (name == null ? "" : name);
    };
  }

  /**
   * Encode this code into a pipe-delimited string for config persistence.
   * Format: {@code DEVICE|gamepad|index|name}.
   *
   * @return the encoded string
   * @see #decode(String)
   */
  public String encode() {
    return device.name() + "|" + gamepad + "|" + (index >= 0 ? index : "") + "|" + (name == null ? "" : name);
  }

  /**
   * Decode a pipe-delimited string back into an {@code InputCode}.
   *
   * @param raw the encoded string from {@link #encode()}
   * @return the decoded input code, or {@code null} if the format is invalid
   */
  public static InputCode decode(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String[] parts = raw.split("\\|", -1);
    if (parts.length < 4) return null;
    Device d;
    try { d = Device.valueOf(parts[0]); } catch (Exception e) { return null; }
    int pad = parseIntSafe(parts[1]);
    int idx = parseIntSafe(parts[2]);
    String nm = parts[3].isEmpty() ? null : parts[3];
    if (d == Device.KEYBOARD && nm != null) nm = canonicalKey(nm);
    return new InputCode(d, nm, idx, pad);
  }

  /** Safely parse an integer, returning -1 on failure. */
  private static int parseIntSafe(String s) {
    try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
  }

  /** Canonicalise a key/button/axis name to upper-case, trimmed. */
  private static String canonicalKey(String key) {
    if (key == null) return "";
    return key.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Object identity
  // ──────────────────────────────────────────────────────────────────────────

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof InputCode that)) return false;
    return index == that.index && gamepad == that.gamepad && device == that.device && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(device, name, index, gamepad);
  }

  @Override
  public String toString() {
    return displayName();
  }
}
