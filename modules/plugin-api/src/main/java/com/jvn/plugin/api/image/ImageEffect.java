package com.jvn.plugin.api.image;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A thread-safe image transformation. Prepare on a worker, never on the UI thread. */
public interface ImageEffect {
  /** Describes this value.
   * @return human-readable effect name */
  String label();
  /** Describes this value.
   * @return optional explanation displayed in the editor */
  default String description() { return ""; }
  /** Describes this value.
   * @return ordered, uniquely named numeric controls */
  List<ImageEffectParameter> parameters();
  /** Describes this value.
   * @return named sets of parameter overrides; omitted values use schema defaults */
  default Map<String, Map<String, Double>> presets() { return Map.of(); }
  /**
   * Prepares drawings off the UI thread; preserve source dimensions and observe interruption.
   * @param source immutable source pixels
   * @param parameters values validated by {@link #resolve(Map)}
   * @return immutable held-frame animation
   * @throws Exception if preparation fails or is cancelled
   */
  ImageAnimation prepare(ImagePixels source, Map<String, Double> parameters) throws Exception;

  /**
   * Applies schema defaults and rejects unknown, non-finite, or invalid overrides.
   * @param supplied parameter overrides
   * @return immutable complete parameter map
   * @throws IllegalArgumentException if the schema or supplied values are invalid
   */
  default Map<String, Double> resolve(Map<String, Double> supplied) {
    Map<String, Double> resolved = new LinkedHashMap<>();
    for (ImageEffectParameter parameter : parameters()) {
      if (resolved.containsKey(parameter.name())) throw new IllegalArgumentException("Duplicate parameter: " + parameter.name());
      resolved.put(parameter.name(), parameter.validate(supplied.getOrDefault(parameter.name(), parameter.defaultValue())));
    }
    for (String name : supplied.keySet()) {
      if (!resolved.containsKey(name)) throw new IllegalArgumentException("Unknown image effect parameter: " + name);
    }
    return Map.copyOf(resolved);
  }
}
