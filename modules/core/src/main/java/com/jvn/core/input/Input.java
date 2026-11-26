package com.jvn.core.input;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Platform-agnostic input state tracker for keyboard, mouse, and gamepad devices.
 *
 * <p>{@code Input} maintains three categories of state for each {@link InputCode}:</p>
 * <ul>
 *   <li><b>Down</b> — the control is currently held (persistent across frames).</li>
 *   <li><b>Pressed</b> — the control transitioned from up → down <em>this frame</em>
 *       (edge-triggered, cleared at {@link #endFrame()}).</li>
 *   <li><b>Released</b> — the control transitioned from down → up <em>this frame</em>
 *       (edge-triggered, cleared at {@link #endFrame()}).</li>
 * </ul>
 *
 * <h2>Frame Lifecycle</h2>
 * <ol>
 *   <li>Platform back-end calls {@code keyDown}/{@code mouseDown}/{@code gamepadButtonDown}
 *       events as they arrive.</li>
 *   <li>Game logic queries {@code isDown}/{@code wasPressed}/{@code wasReleased}.</li>
 *   <li>Engine calls {@link #endFrame()} at the end of the update tick to clear
 *       the edge-triggered sets and scroll delta.</li>
 * </ol>
 *
 * <p>Mouse position and scroll delta are also tracked here for convenience.</p>
 *
 * @see InputCode
 * @see ActionMap
 */
public class Input {

  /** Controls currently held down (persistent until released). */
  private final Set<InputCode> down = new HashSet<>();

  /** Controls that transitioned to down <em>this frame</em>. */
  private final Set<InputCode> pressed = new HashSet<>();

  /** Controls that transitioned to up <em>this frame</em>. */
  private final Set<InputCode> released = new HashSet<>();

  /** Current analog axis values keyed by their {@link InputCode}. */
  private final Map<InputCode, Double> axisValues = new HashMap<>();

  /** Current mouse X position in logical (viewport-scaled) coordinates. */
  private double mouseX;

  /** Current mouse Y position in logical coordinates. */
  private double mouseY;

  /** Accumulated mouse scroll delta for the current frame. */
  private double scrollDeltaY;

  // ──────────────────────────────────────────────────────────────────────────
  //  Keyboard events
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Signal that a keyboard key has been pressed down (by name).
   *
   * @param key the key name (e.g. "SPACE", "A"); {@code null} is ignored
   */
  public void keyDown(String key) {
    if (key == null) return;
    keyDown(InputCode.key(key));
  }

  /**
   * Signal that a keyboard key has been pressed down (by code).
   *
   * @param code the input code; {@code null} is ignored
   */
  public void keyDown(InputCode code) {
    if (code == null) return;
    if (down.add(code)) pressed.add(code);
  }

  /**
   * Signal that a keyboard key has been released (by name).
   *
   * @param key the key name; {@code null} is ignored
   */
  public void keyUp(String key) {
    if (key == null) return;
    keyUp(InputCode.key(key));
  }

  /**
   * Signal that a keyboard key has been released (by code).
   *
   * @param code the input code; {@code null} is ignored
   */
  public void keyUp(InputCode code) {
    if (code == null) return;
    if (down.remove(code)) released.add(code);
  }

  /** @return {@code true} if the named key is currently held */
  public boolean isKeyDown(String key) { return isDown(InputCode.key(key)); }

  /** @return {@code true} if the named key was pressed this frame */
  public boolean wasKeyPressed(String key) { return wasPressed(InputCode.key(key)); }

  /** @return {@code true} if the named key was released this frame */
  public boolean wasKeyReleased(String key) { return wasReleased(InputCode.key(key)); }

  // ──────────────────────────────────────────────────────────────────────────
  //  Mouse events
  // ──────────────────────────────────────────────────────────────────────────

  /** Signal mouse button down (1 = primary, 2 = secondary, 3 = middle). */
  public void mouseDown(int button) { handleButton(InputCode.mouse(button), true); }

  /** Signal mouse button up. */
  public void mouseUp(int button) { handleButton(InputCode.mouse(button), false); }

  /** @return {@code true} if the given mouse button is currently held */
  public boolean isMouseDown(int button) { return isDown(InputCode.mouse(button)); }

  /** @return {@code true} if the mouse button was pressed this frame */
  public boolean wasMousePressed(int button) { return wasPressed(InputCode.mouse(button)); }

  /** @return {@code true} if the mouse button was released this frame */
  public boolean wasMouseReleased(int button) { return wasReleased(InputCode.mouse(button)); }

  // ──────────────────────────────────────────────────────────────────────────
  //  Gamepad events
  // ──────────────────────────────────────────────────────────────────────────

  /** Signal a gamepad digital button press. */
  public void gamepadButtonDown(int pad, String button) { handleButton(InputCode.gamepadButton(pad, button), true); }

  /** Signal a gamepad digital button release. */
  public void gamepadButtonUp(int pad, String button) { handleButton(InputCode.gamepadButton(pad, button), false); }

  /**
   * Set the current value of a gamepad analog axis.
   *
   * @param pad   gamepad index (0-based)
   * @param axis  axis name (e.g. "LEFT_X")
   * @param value axis value, typically [-1.0, 1.0]
   */
  public void setGamepadAxis(int pad, String axis, double value) {
    InputCode code = InputCode.gamepadAxis(pad, axis);
    axisValues.put(code, value);
  }

  /**
   * @param pad  gamepad index
   * @param axis axis name
   * @return the current axis value, or 0.0 if not set
   */
  public double getGamepadAxis(int pad, String axis) {
    InputCode code = InputCode.gamepadAxis(pad, axis);
    return axisValues.getOrDefault(code, 0.0);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Generic queries
  // ──────────────────────────────────────────────────────────────────────────

  /** @return {@code true} if the given code is currently held */
  public boolean isDown(InputCode code) { return down.contains(code); }

  /** @return {@code true} if the given code was pressed this frame */
  public boolean wasPressed(InputCode code) { return pressed.contains(code); }

  /** @return {@code true} if the given code was released this frame */
  public boolean wasReleased(InputCode code) { return released.contains(code); }

  // ──────────────────────────────────────────────────────────────────────────
  //  Mouse position & scroll
  // ──────────────────────────────────────────────────────────────────────────

  /** Update the current mouse position (called by the platform back-end). */
  public void setMousePosition(double x, double y) { this.mouseX = x; this.mouseY = y; }

  /** @return current mouse X in logical coordinates */
  public double getMouseX() { return mouseX; }

  /** @return current mouse Y in logical coordinates */
  public double getMouseY() { return mouseY; }

  /** Accumulate vertical scroll and record its direction as a momentary input. */
  public void addScrollDeltaY(double dy) {
    InputCode direction = InputCode.mouseWheel(dy);
    if (direction == null) return;
    scrollDeltaY += dy;
    pressed.add(direction);
  }

  /** @return accumulated scroll delta since last {@link #endFrame()} */
  public double getScrollDeltaY() { return scrollDeltaY; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Frame management
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Clear edge-triggered state (pressed/released sets and scroll delta).
   * Must be called once per update tick, <em>after</em> game logic has
   * had a chance to query the state.
   */
  public void endFrame() {
    pressed.clear();
    released.clear();
    scrollDeltaY = 0;
  }

  /** Reset all input state — useful when switching scenes or losing focus. */
  public void reset() {
    down.clear();
    pressed.clear();
    released.clear();
    axisValues.clear();
    mouseX = 0;
    mouseY = 0;
    scrollDeltaY = 0;
  }

  /**
   * Internal helper to handle a digital button press or release event.
   *
   * @param code      the input code
   * @param downEvent {@code true} for press, {@code false} for release
   */
  private void handleButton(InputCode code, boolean downEvent) {
    if (code == null) return;
    if (downEvent) {
      if (down.add(code)) pressed.add(code);
    } else {
      if (down.remove(code)) released.add(code);
    }
  }
}
