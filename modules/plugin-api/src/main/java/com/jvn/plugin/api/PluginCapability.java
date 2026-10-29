package com.jvn.plugin.api;

/** Declared extension families used for review, diagnostics, and future policy enforcement. */
public enum PluginCapability {
  /** Permission to register script commands. */
  SCRIPT_COMMAND("script.command"),
  /** Permission to contribute editor tool actions. */
  EDITOR_TOOL("editor.tool"),
  /** Permission to register asset importers. */
  ASSET_IMPORTER("asset.importer"),
  /** Permission to observe runtime lifecycle events. */
  RUNTIME_LISTENER("runtime.listener"),
  /** Permission to contribute named easing curves. */
  ANIMATION_EASING("animation.easing"),
  /** Permission to contribute prepared raster image effects. */
  IMAGE_EFFECT("image.effect");

  private final String id;

  PluginCapability(String id) { this.id = id; }
  /** Returns the manifest identifier.
   * @return stable lowercase identifier
   */
  public String id() { return id; }

  /** Resolves a manifest identifier.
   * @param id identifier
   * @return matching capability
   * @throws IllegalArgumentException if unknown
   */
  public static PluginCapability fromId(String id) {
    for (PluginCapability value : values()) if (value.id.equals(id)) return value;
    throw new IllegalArgumentException("Unknown plugin capability: " + id);
  }
}
