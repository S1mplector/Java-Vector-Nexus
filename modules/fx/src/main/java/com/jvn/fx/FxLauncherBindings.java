package com.jvn.fx;

import com.jvn.core.input.ActionBindingProfile;
import com.jvn.core.input.ActionBindingProfileStore;
import com.jvn.core.input.ActionMap;
import com.jvn.core.input.InputActions;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnSettingsStore;
import java.util.HashSet;

/**
 * Helper to load action bindings from settings/user file.
 */
final class FxLauncherBindings {
  private FxLauncherBindings() {}

  static ActionMap load() {
    return load(new VnSettingsStore().load());
  }

  static ActionMap load(VnSettings settings) {
    ActionBindingProfile profile;
    if (settings.getInputProfileSerialized() != null && !settings.getInputProfileSerialized().isBlank()) {
      profile = ActionBindingProfile.deserialize(settings.getInputProfileSerialized());
    } else {
      profile = InputActions.defaultProfile();
      try {
        ActionBindingProfileStore store = new ActionBindingProfileStore(settings.getInputProfilePath());
        // if a saved profile exists, prefer it
        profile = store.load();
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    }
    // Explicit rollback and rollforward rows override their defaults, even when empty.
    profile.bindings().computeIfAbsent(InputActions.ROLLBACK,
        key -> new HashSet<>(InputActions.defaultProfile().bindings().get(key)));
    profile.bindings().computeIfAbsent(InputActions.ROLLFORWARD,
        key -> new HashSet<>(InputActions.defaultProfile().bindings().get(key)));
    ActionMap map = new ActionMap(new com.jvn.core.input.Input());
    map.loadProfile(profile);
    return map;
  }
}
