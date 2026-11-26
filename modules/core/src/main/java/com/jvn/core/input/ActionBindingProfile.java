package com.jvn.core.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Serialisable snapshot of action → {@link InputCode} bindings.
 *
 * <p>A profile captures the complete set of user key-bindings at a point
 * in time so they can be persisted to disk (via
 * {@link ActionBindingProfileStore}) or transferred between
 * {@link ActionMap} instances.</p>
 *
 * <h2>Text Format</h2>
 * <p>Each line maps one action to a comma-separated list of encoded
 * {@link InputCode} values:</p>
 * <pre>
 * advance=KEYBOARD|0|-1|SPACE,KEYBOARD|0|-1|ENTER,MOUSE_BUTTON|0|1|
 * menu_back=KEYBOARD|0|-1|ESCAPE
 * </pre>
 *
 * @see ActionMap#toProfile()
 * @see ActionMap#loadProfile(ActionBindingProfile)
 * @see ActionBindingProfileStore
 */
public class ActionBindingProfile {

  /** Action name → set of bound input codes. */
  private final Map<String, Set<InputCode>> bindings = new HashMap<>();

  /** @return the mutable bindings map (action → codes) */
  public Map<String, Set<InputCode>> bindings() { return bindings; }

  /**
   * Add a single binding. Returns {@code this} for fluent chaining.
   *
   * @param action the action name
   * @param code   the input code to bind
   * @return this profile
   */
  public ActionBindingProfile add(String action, InputCode code) {
    if (action == null || code == null) return this;
    bindings.computeIfAbsent(action, k -> new HashSet<>()).add(code);
    return this;
  }

  /**
   * Serialise all bindings into the text format described in the class Javadoc.
   *
   * @return a multi-line string suitable for writing to a file
   * @see #deserialize(String)
   */
  public String serialize() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Set<InputCode>> e : bindings.entrySet()) {
      StringJoiner join = new StringJoiner(",");
      for (InputCode code : e.getValue()) {
        join.add(code.encode());
      }
      sb.append(e.getKey()).append('=').append(join.toString()).append('\n');
    }
    return sb.toString();
  }

  /**
   * Deserialise a profile from its text representation.
   * Invalid lines and unrecognised codes are silently skipped.
   *
   * @param data the serialised string (may be {@code null} or blank)
   * @return a new profile with the parsed bindings
   * @see #serialize()
   */
  public static ActionBindingProfile deserialize(String data) {
    ActionBindingProfile profile = new ActionBindingProfile();
    if (data == null || data.isBlank()) return profile;
    String[] lines = data.split("\\R");
    for (String line : lines) {
      if (line == null || line.isBlank()) continue;
      int idx = line.indexOf('=');
      if (idx <= 0) continue;
      String action = line.substring(0, idx);
      String codes = line.substring(idx + 1);
      if (codes.isBlank()) {
        profile.bindings.computeIfAbsent(action, key -> new HashSet<>());
        continue;
      }
      for (String raw : codes.split(",")) {
        InputCode code = InputCode.decode(raw);
        if (code != null) profile.add(action, code);
      }
    }
    return profile;
  }

  /** @return a list of all action names that have at least one binding */
  public List<String> actions() { return new ArrayList<>(bindings.keySet()); }
}
