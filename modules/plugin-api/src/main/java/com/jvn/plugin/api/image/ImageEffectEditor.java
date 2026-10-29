package com.jvn.plugin.api.image;

/** Optional typed editor service. The host provides its native effect-preview window. */
@FunctionalInterface
public interface ImageEffectEditor {
  /**
   * Opens the host's native preview and parameter editor on the UI thread.
   * @param effectId qualified identifier of a registered effect
   */
  void open(String effectId);
}
