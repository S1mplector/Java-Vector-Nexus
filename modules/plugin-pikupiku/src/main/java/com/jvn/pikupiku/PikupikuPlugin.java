package com.jvn.pikupiku;

import com.jvn.plugin.api.*;
import com.jvn.plugin.api.editor.*;
import com.jvn.plugin.api.image.ImageEffectEditor;
import java.util.List;
import java.util.Set;

/** Native Java port of Pikupiku's procedural line boil; no FFmpeg process is needed. */
public final class PikupikuPlugin implements JvnPlugin, BundledPluginProvider {
  public static final String EFFECT_ID = "pikupiku.lineboil";
  @Override public PluginDescriptor descriptor() {
    return new PluginDescriptor("com.jvn.pikupiku", "Pikupiku Line Wiggler", "1.0.0", ">=1.2.0 <2.0.0",
        getClass().getName(), "Held pencil drawings with stable colors and transparent silhouettes.",
        "JVN", List.of(), Set.of(PluginCapability.IMAGE_EFFECT, PluginCapability.EDITOR_TOOL));
  }
  @Override public JvnPlugin create() { return new PikupikuPlugin(); }
  @Override public void initialize(PluginContext context) {
    context.contribute().images().effect(EFFECT_ID, new PikupikuEffect());
    context.registries().editorTools().register("pikupiku.studio", new EditorTool() {
      @Override public String label() { return "Pikupiku Line Wiggler"; }
      @Override public String description() { return "Preview and export animated pencil contours."; }
      @Override public void open(EditorToolContext tools) {
        tools.service(ImageEffectEditor.class).orElseThrow(
            () -> new IllegalStateException("This host has no image-effect preview service")).open(EFFECT_ID);
      }
    });
  }
}
