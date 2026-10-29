package com.jvn.plugin.runtime;

import com.jvn.plugin.api.*;
import com.jvn.plugin.api.image.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ImageEffectLifecycleTest {
  private final ImageEffect effect = new ImageEffect() {
    public String label() { return "Test"; }
    public List<ImageEffectParameter> parameters() { return List.of(new ImageEffectParameter("amount", "Amount", 1, 0, 2, false)); }
    public ImageAnimation prepare(ImagePixels source, Map<String, Double> params) { return new ImageAnimation(List.of(source), 1); }
  };
  private PluginDescriptor descriptor(Set<PluginCapability> capabilities) {
    return new PluginDescriptor("test.images", "Images", "1.0.0", ">=1.2.0 <2.0.0", "test.Images", "", "", List.of(), capabilities);
  }
  @Test void accessIsCapabilityGatedAndHostViewsCannotRegister() {
    var registries = new DefaultPluginRegistries();
    assertThrows(IllegalStateException.class, () -> registries.forPlugin(descriptor(Set.of())).imageEffects());
    assertThrows(UnsupportedOperationException.class, () -> registries.view().imageEffects().register("test.image", effect));
    var owned = registries.forPlugin(descriptor(Set.of(PluginCapability.IMAGE_EFFECT))).imageEffects();
    assertThrows(IllegalArgumentException.class, () -> owned.register("unqualified", effect));
    Registration handle = owned.register("test.image", effect);
    assertSame(effect, registries.view().imageEffects().find("test.image").orElseThrow());
    handle.close(); assertTrue(owned.entries().isEmpty());
  }
  @Test void cleanupRevokesRetainedHandlesAndPreservesOtherPlugins() {
    var registries = new DefaultPluginRegistries();
    var owned = registries.forPlugin(descriptor(Set.of(PluginCapability.IMAGE_EFFECT))).imageEffects();
    owned.register("test.image", effect);
    registries.removePlugin("test.images");
    assertTrue(registries.view().imageEffects().entries().isEmpty());
    assertThrows(IllegalStateException.class, () -> owned.register("test.late", effect));
  }
  @Test void closeBeforeStartAndRepeatedCloseAreTerminal() {
    PluginHost host = PluginHost.builder(PluginEnvironment.EDITOR).build();
    host.close(); host.close();
    assertThrows(IllegalStateException.class, host::start);
    assertThrows(IllegalStateException.class, host::discover);
  }
}
