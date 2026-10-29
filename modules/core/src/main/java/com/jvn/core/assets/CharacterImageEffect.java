package com.jvn.core.assets;

import java.util.LinkedHashMap;
import java.util.Map;

/** Character effect settings are ordinary saved/rollback-aware VNS variables. */
public record CharacterImageEffect(String id, Map<String, Double> parameters) {
  public static final CharacterImageEffect NONE = new CharacterImageEffect("", Map.of());
  public CharacterImageEffect { parameters = Map.copyOf(parameters); }
  public static CharacterImageEffect from(Map<String, Object> variables, String characterId) {
    String key = "character." + characterId + ".imageEffect";
    String id = String.valueOf(variables.getOrDefault(key, "")).trim();
    if (id.isEmpty() || id.equalsIgnoreCase("none") || id.equalsIgnoreCase("off")) return NONE;
    Map<String, Double> parameters = new LinkedHashMap<>();
    String prefix = key + ".";
    for (var entry : variables.entrySet()) {
      if (!entry.getKey().startsWith(prefix)) continue;
      Object value = entry.getValue();
      // 'default' removes an override without needing to delete a saved variable.
      if ("default".equals(value)) continue;
      double number = value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
      if (!Double.isFinite(number)) throw new IllegalArgumentException("Non-finite image effect parameter");
      parameters.put(entry.getKey().substring(prefix.length()), number);
    }
    return new CharacterImageEffect(id, parameters);
  }
}
