package com.jvn.plugin.api;

import com.jvn.plugin.api.asset.AssetImporter;
import com.jvn.plugin.api.editor.EditorTool;
import com.jvn.plugin.api.runtime.RuntimeListener;
import com.jvn.plugin.api.script.ScriptCommand;
import com.jvn.plugin.api.animation.AnimationEasing;

/** Supported extension families. Access is checked against manifest capabilities. */
public interface PluginRegistries {
  /** Returns commands; requires {@code script.command}.
   * @return owned registry
   */
  ExtensionRegistry<ScriptCommand> scriptCommands();
  /** Returns tools; requires {@code editor.tool}.
   * @return owned registry
   */
  ExtensionRegistry<EditorTool> editorTools();
  /** Returns importers; requires {@code asset.importer}.
   * @return owned registry
   */
  ExtensionRegistry<AssetImporter> assetImporters();
  /** Returns listeners; requires {@code runtime.listener}.
   * @return owned registry
   */
  ExtensionRegistry<RuntimeListener> runtimeListeners();
  /** Returns contributed easing curves; requires {@code animation.easing} for plugin access.
   * @return owned registry
   */
  default ExtensionRegistry<AnimationEasing> animationEasings() {
    throw new UnsupportedOperationException("This host does not provide animation easing extensions");
  }
  /** Returns image effects; requires image.effect. Added in Plugin API 1.2.
   * @return read-only registry for the host; owned registry for plugins
   */
  default ExtensionRegistry<com.jvn.plugin.api.image.ImageEffect> imageEffects() {
    throw new UnsupportedOperationException("This host does not provide image effects");
  }
}
