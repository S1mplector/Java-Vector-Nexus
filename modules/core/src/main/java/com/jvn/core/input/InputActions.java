package com.jvn.core.input;

/**
 * Centralised action identifier constants and a factory for the default
 * key-binding profile.
 *
 * <p>Action names are plain strings used as keys in {@link ActionMap} and
 * {@link ActionBindingProfile}. Keeping them in a single class prevents
 * typos and makes it easy to discover all engine-recognised actions.</p>
 *
 * <h2>Action Groups</h2>
 * <ul>
 *   <li><b>VN / narrative</b> — {@code ADVANCE}, {@code SKIP_TOGGLE},
 *       {@code AUTO_TOGGLE}, {@code HIDE_UI}, {@code HISTORY},
 *       {@code ROLLBACK}, {@code ROLLFORWARD}.</li>
 *   <li><b>Persistence</b> — {@code QUICK_SAVE}, {@code QUICK_LOAD},
 *       {@code SAVE_MENU}.</li>
 *   <li><b>Menu navigation</b> — {@code MENU_UP/DOWN/LEFT/RIGHT},
 *       {@code MENU_CONFIRM}, {@code MENU_BACK}, {@code MENU_DELETE},
 *       {@code MENU_RENAME}.</li>
 * </ul>
 *
 * @see ActionMap
 * @see ActionBindingProfile
 */
public final class InputActions {

  // ── VN / narrative actions ────────────────────────────────────────────

  /** Advance dialogue / click to continue. */
  public static final String ADVANCE = "advance";
  /** Toggle skip (fast-forward) mode. */
  public static final String SKIP_TOGGLE = "skip_toggle";
  /** Toggle auto-advance mode. */
  public static final String AUTO_TOGGLE = "auto_toggle";
  /** Hide the dialogue UI overlay. */
  public static final String HIDE_UI = "hide_ui";
  /** Open the dialogue history / backlog. */
  public static final String HISTORY = "history";
  /** Open the settings menu. */
  public static final String SETTINGS = "settings";
  /** Quick-save to the default slot. */
  public static final String QUICK_SAVE = "quick_save";
  /** Quick-load from the default slot. */
  public static final String QUICK_LOAD = "quick_load";
  /** Open the save-slot menu. */
  public static final String SAVE_MENU = "save_menu";
  /** Roll back to the previous dialogue state. */
  public static final String ROLLBACK = "rollback";
  /** Roll forward to the next saved state (undo a rollback). */
  public static final String ROLLFORWARD = "rollforward";

  // ── Menu navigation actions ───────────────────────────────────────────

  /** Navigate up in a menu list. */
  public static final String MENU_UP = "menu_up";
  /** Navigate down in a menu list. */
  public static final String MENU_DOWN = "menu_down";
  /** Navigate left (e.g. tabs, sliders). */
  public static final String MENU_LEFT = "menu_left";
  /** Navigate right. */
  public static final String MENU_RIGHT = "menu_right";
  /** Confirm / select the current menu item. */
  public static final String MENU_CONFIRM = "menu_confirm";
  /** Go back / cancel in a menu. */
  public static final String MENU_BACK = "menu_back";
  /** Delete the selected item (e.g. a save slot). */
  public static final String MENU_DELETE = "menu_delete";
  /** Rename the selected item. */
  public static final String MENU_RENAME = "menu_rename";

  /** Non-instantiable utility class. */
  private InputActions() {}

  /**
   * Create the engine's default key-binding profile with sensible
   * keyboard and mouse bindings for VN and menu navigation.
   *
   * @return a fully-populated {@link ActionBindingProfile}
   */
  public static ActionBindingProfile defaultProfile() {
    ActionBindingProfile p = new ActionBindingProfile();
    // Core VN/navigation
    p.add(ADVANCE, InputCode.key("SPACE"))
        .add(ADVANCE, InputCode.key("ENTER"))
        .add(ADVANCE, InputCode.mouse(1));
    p.add(SKIP_TOGGLE, InputCode.key("CONTROL"))
        .add(SKIP_TOGGLE, InputCode.key("COMMAND"));
    p.add(AUTO_TOGGLE, InputCode.key("A"));
    p.add(HIDE_UI, InputCode.key("H"));
    p.add(HISTORY, InputCode.key("B"));
    p.add(SETTINGS, InputCode.key("S"));
    p.add(QUICK_SAVE, InputCode.key("F5"));
    p.add(QUICK_LOAD, InputCode.key("F9"));
    p.add(SAVE_MENU, InputCode.key("F6"));
    p.add(ROLLBACK, InputCode.key("PAGE_UP"))
        .add(ROLLBACK, InputCode.key("BACK_QUOTE"))
        .add(ROLLBACK, InputCode.mouseWheel(1));
    p.add(ROLLFORWARD, InputCode.key("PAGE_DOWN"));

    // Menus
    p.add(MENU_UP, InputCode.key("UP")).add(MENU_UP, InputCode.key("W"));
    p.add(MENU_DOWN, InputCode.key("DOWN")).add(MENU_DOWN, InputCode.key("S"));
    p.add(MENU_LEFT, InputCode.key("LEFT")).add(MENU_LEFT, InputCode.key("A"));
    p.add(MENU_RIGHT, InputCode.key("RIGHT")).add(MENU_RIGHT, InputCode.key("D"));
    p.add(MENU_CONFIRM, InputCode.key("ENTER")).add(MENU_CONFIRM, InputCode.key("SPACE"));
    p.add(MENU_BACK, InputCode.key("ESCAPE"));
    p.add(MENU_DELETE, InputCode.key("DELETE"));
    p.add(MENU_RENAME, InputCode.key("R"));
    return p;
  }
}
