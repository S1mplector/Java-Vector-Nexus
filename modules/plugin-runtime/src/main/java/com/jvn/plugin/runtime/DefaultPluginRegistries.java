package com.jvn.plugin.runtime;

import com.jvn.plugin.api.ExtensionRegistry;
import com.jvn.plugin.api.PluginRegistries;
import com.jvn.plugin.api.PluginCapability;
import com.jvn.plugin.api.PluginDescriptor;
import com.jvn.plugin.api.PluginContributions;
import com.jvn.plugin.api.animation.AnimationEasing;
import com.jvn.plugin.api.animation.AnimationParameter;
import com.jvn.plugin.api.asset.AssetImporter;
import com.jvn.plugin.api.editor.EditorTool;
import com.jvn.plugin.api.runtime.RuntimeListener;
import com.jvn.plugin.api.script.ScriptCommand;

public final class DefaultPluginRegistries {
  private final OwnedExtensionRegistry<ScriptCommand> commands = new OwnedExtensionRegistry<>();
  private final OwnedExtensionRegistry<EditorTool> tools = new OwnedExtensionRegistry<>();
  private final OwnedExtensionRegistry<AssetImporter> importers = new OwnedExtensionRegistry<>();
  private final OwnedExtensionRegistry<RuntimeListener> listeners = new OwnedExtensionRegistry<>();
  private final OwnedExtensionRegistry<AnimationEasing> easings = new OwnedExtensionRegistry<>();

  private final OwnedExtensionRegistry<com.jvn.plugin.api.image.ImageEffect> images = new OwnedExtensionRegistry<>();

  PluginRegistries forPlugin(PluginDescriptor descriptor) {
    String pluginId = descriptor.id();
    return new PluginRegistries() {
      @Override public ExtensionRegistry<ScriptCommand> scriptCommands() { require(PluginCapability.SCRIPT_COMMAND); return commands.forPlugin(pluginId); }
      @Override public ExtensionRegistry<EditorTool> editorTools() { require(PluginCapability.EDITOR_TOOL); return tools.forPlugin(pluginId); }
      @Override public ExtensionRegistry<AssetImporter> assetImporters() { require(PluginCapability.ASSET_IMPORTER); return importers.forPlugin(pluginId); }
      @Override public ExtensionRegistry<RuntimeListener> runtimeListeners() { require(PluginCapability.RUNTIME_LISTENER); return listeners.forPlugin(pluginId); }
      @Override public ExtensionRegistry<AnimationEasing> animationEasings() {
        require(PluginCapability.ANIMATION_EASING);
        ExtensionRegistry<AnimationEasing> owned = easings.forPlugin(pluginId);
        return new ExtensionRegistry<>() {
          @Override public com.jvn.plugin.api.Registration register(String id, AnimationEasing easing) {
            if (id == null || !id.matches("[A-Za-z_][A-Za-z0-9_-]*(?:\\.[A-Za-z_][A-Za-z0-9_-]*)+")) {
              throw new IllegalArgumentException("Animation easing id must be a qualified identifier: " + id);
            }
            validateEasing(easing);
            return owned.register(id, easing);
          }
          @Override public java.util.Optional<AnimationEasing> find(String id) { return owned.find(id); }
          @Override public java.util.List<com.jvn.plugin.api.ExtensionEntry<AnimationEasing>> entries() { return owned.entries(); }
        };
      }
      @Override public ExtensionRegistry<com.jvn.plugin.api.image.ImageEffect> imageEffects() {
        require(PluginCapability.IMAGE_EFFECT);
        var owned = images.forPlugin(pluginId);
        return new ExtensionRegistry<>() {
          @Override public com.jvn.plugin.api.Registration register(String id, com.jvn.plugin.api.image.ImageEffect effect) {
            if (id == null || !id.matches("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+"))
              throw new IllegalArgumentException("Image effect id must be qualified: " + id);
            if (effect == null || effect.label() == null || effect.label().isBlank())
              throw new IllegalArgumentException("Image effect label is required");
            effect.resolve(java.util.Map.of());
            effect.presets().values().forEach(effect::resolve);
            return owned.register(id, effect);
          }
          @Override public java.util.Optional<com.jvn.plugin.api.image.ImageEffect> find(String id) { return owned.find(id); }
          @Override public java.util.List<com.jvn.plugin.api.ExtensionEntry<com.jvn.plugin.api.image.ImageEffect>> entries() { return owned.entries(); }
        };
      }
      private void require(PluginCapability capability) {
        if (!descriptor.capabilities().contains(capability)) {
          throw new IllegalStateException("Plugin " + pluginId + " did not declare capability " + capability.id());
        }
      }
    };
  }

  public PluginRegistries view() {
    return new PluginRegistries() {
      @Override public ExtensionRegistry<ScriptCommand> scriptCommands() { return commands.view(); }
      @Override public ExtensionRegistry<EditorTool> editorTools() { return tools.view(); }
      @Override public ExtensionRegistry<AssetImporter> assetImporters() { return importers.view(); }
      @Override public ExtensionRegistry<RuntimeListener> runtimeListeners() { return listeners.view(); }
      @Override public ExtensionRegistry<AnimationEasing> animationEasings() { return easings.view(); }
      @Override public ExtensionRegistry<com.jvn.plugin.api.image.ImageEffect> imageEffects() { return images.view(); }
    };
  }

  PluginContributions contributionsFor(PluginDescriptor descriptor) {
    PluginRegistries owned = forPlugin(descriptor);
    return new PluginContributions() {
      @Override public com.jvn.plugin.api.animation.AnimationContributions animations() {
        return (id, easing) -> owned.animationEasings().register(id, easing);
      }
      @Override public com.jvn.plugin.api.image.ImageEffectContributions images() {
        return (id, effect) -> owned.imageEffects().register(id, effect);
      }
    };
  }

  void removePlugin(String pluginId) {
    commands.removePlugin(pluginId);
    tools.removePlugin(pluginId);
    importers.removePlugin(pluginId);
    listeners.removePlugin(pluginId);
    easings.removePlugin(pluginId);
    images.removePlugin(pluginId);
  }

  private static void validateEasing(AnimationEasing easing) {
    if (easing == null) throw new IllegalArgumentException("Animation easing is required");
    if (easing.label() == null || easing.label().isBlank()) {
      throw new IllegalArgumentException("Animation easing label is required");
    }
    java.util.List<AnimationParameter> parameters = easing.parameters();
    if (parameters == null) throw new IllegalArgumentException("Animation easing parameters are required");
    java.util.Set<String> names = new java.util.LinkedHashSet<>();
    for (AnimationParameter parameter : parameters) {
      if (parameter == null) throw new IllegalArgumentException("Animation easing parameters cannot contain null");
      if (!names.add(parameter.name())) {
        throw new IllegalArgumentException("Duplicate animation easing parameter: " + parameter.name());
      }
    }
  }
}
