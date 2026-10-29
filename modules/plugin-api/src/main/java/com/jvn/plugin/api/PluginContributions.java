package com.jvn.plugin.api;

import com.jvn.plugin.api.animation.AnimationContributions;

/** Author-focused entrypoint for contributing extensions. */
public interface PluginContributions {
  /**
   * Returns the plugin-owned animation authoring surface.
   * @return animation contributions
   */
  AnimationContributions animations();
  /** Returns the image-effect authoring surface, added in Plugin API 1.2.
   * @return image-effect contributions
   */
  default com.jvn.plugin.api.image.ImageEffectContributions images() {
    throw new UnsupportedOperationException("This host does not provide image effects");
  }
}
