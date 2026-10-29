package com.jvn.core.assets;

import com.jvn.plugin.api.ExtensionRegistry;
import com.jvn.plugin.api.image.ImageEffect;

/** Live bridge shared by editor previews and runtime image renderers. */
public final class ImageEffects {
  private static volatile ExtensionRegistry<ImageEffect> registry;
  private ImageEffects() {}
  public static void install(ExtensionRegistry<ImageEffect> extensions) { registry = extensions; }
  public static ExtensionRegistry<ImageEffect> registry() { return registry; }
  public static void clear() { registry = null; }
}
