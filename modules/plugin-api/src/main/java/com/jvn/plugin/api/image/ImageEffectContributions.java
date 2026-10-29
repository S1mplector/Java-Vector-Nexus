package com.jvn.plugin.api.image;

import com.jvn.plugin.api.Registration;

/** Plugin-owned image effects; requires the image.effect capability. */
@FunctionalInterface
public interface ImageEffectContributions {
  /**
   * Registers an effect owned by the contributing plugin.
   * @param id qualified effect identifier
   * @param effect thread-safe effect implementation
   * @return handle that unregisters this contribution when closed
   */
  Registration effect(String id, ImageEffect effect);
}
