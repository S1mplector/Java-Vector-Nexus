package com.jvn.plugin.runtime;

import com.jvn.plugin.api.ExtensionEntry;
import com.jvn.plugin.api.ExtensionRegistry;
import com.jvn.plugin.api.Registration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class OwnedExtensionRegistry<T> {
  private final Map<String, ExtensionEntry<T>> entries = new LinkedHashMap<>();

  private final java.util.Set<String> revoked = new java.util.HashSet<>();

  synchronized ExtensionRegistry<T> forPlugin(String pluginId) {
    return new ExtensionRegistry<>() {
      @Override public Registration register(String id, T extension) {
        return registerOwned(pluginId, id, extension);
      }
      @Override public Optional<T> find(String id) { return OwnedExtensionRegistry.this.find(id); }
      @Override public List<ExtensionEntry<T>> entries() { return OwnedExtensionRegistry.this.entries(); }
    };
  }

  synchronized Registration registerOwned(String pluginId, String id, T extension) {
    if (revoked.contains(pluginId)) throw new IllegalStateException("Plugin registrations are closed: " + pluginId);
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Extension id is required");
    if (extension == null) throw new IllegalArgumentException("Extension is required");
    String normalized = id.trim().toLowerCase(java.util.Locale.ROOT);
    if (entries.containsKey(normalized)) throw new IllegalStateException("Extension id already registered: " + normalized);
    ExtensionEntry<T> entry = new ExtensionEntry<>(normalized, pluginId, extension);
    entries.put(normalized, entry);
    return () -> { synchronized (OwnedExtensionRegistry.this) { entries.remove(normalized, entry); } };
  }

  synchronized Optional<T> find(String id) {
    if (id == null) return Optional.empty();
    ExtensionEntry<T> entry = entries.get(id.trim().toLowerCase(java.util.Locale.ROOT));
    return entry == null ? Optional.empty() : Optional.of(entry.extension());
  }

  synchronized List<ExtensionEntry<T>> entries() { return List.copyOf(entries.values()); }

  synchronized void removePlugin(String pluginId) {
    revoked.add(pluginId);
    entries.values().removeIf(entry -> entry.pluginId().equals(pluginId));
  }
  ExtensionRegistry<T> view() {
    return new ExtensionRegistry<>() {
      @Override public Registration register(String id, T extension) {
        throw new UnsupportedOperationException("Host registry views are read-only");
      }
      @Override public Optional<T> find(String id) { return OwnedExtensionRegistry.this.find(id); }
      @Override public List<ExtensionEntry<T>> entries() { return OwnedExtensionRegistry.this.entries(); }
    };
  }
}
