package com.jvn.plugin.api.editor;

import java.nio.file.Path;
import java.util.Map;

/**
 * Editor action context.
 * @param projectDirectory active project, or {@code null}
 * @param services immutable optional host services; {@code window} is currently supplied when available
 */
public record EditorToolContext(Path projectDirectory, Map<String, Object> services) {
  /** Normalizes services to an immutable map. */
  public EditorToolContext {
    services = services == null ? Map.of() : Map.copyOf(services);
  }
  /** Finds an optional typed host service without coupling plugins to a UI toolkit.
   * @param <T> requested service type
   * @param type service contract
   * @return a matching host service, or an empty optional
   */
  public <T> java.util.Optional<T> service(Class<T> type) {
    return services.values().stream().filter(type::isInstance).map(type::cast).findFirst();
  }
}
