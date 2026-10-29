package com.jvn.plugin.runtime;

import com.jvn.runtime.RuntimeVnInterop;
import com.jvn.plugin.api.PluginDescriptor;
import com.jvn.plugin.api.PluginCapability;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.vn.VnExternalCommand;
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.VnScene;
import com.jvn.plugin.api.script.ScriptCommandResult;
import com.jvn.plugin.runtime.DefaultPluginRegistries;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimePluginInteropTest {
  @Test
  void executesRegisteredCommandAndAppliesVariableUpdates() {
    DefaultPluginRegistries registries = new DefaultPluginRegistries();
    PluginDescriptor descriptor = new PluginDescriptor("test.commands", "Commands", "1.0.0",
        ">=1.2.0 <2.0.0", "test.Commands", "", "", List.of(), Set.of(PluginCapability.SCRIPT_COMMAND));
    registries.forPlugin(descriptor).scriptCommands().register("test.greet", invocation -> {
      assertEquals("vns", invocation.language());
      assertEquals(java.util.List.of("Ada", "Lovelace"), invocation.arguments());
      return new ScriptCommandResult(true, "ok", Map.of("greeted", true));
    });

    Engine engine = new Engine(ApplicationConfig.builder().build());
    RuntimeVnInterop interop = new RuntimeVnInterop(engine, registries.view());
    VnScene scene = new VnScene(new VnScenarioBuilder("test").label("start").end().build());

    interop.handle(new VnExternalCommand("plugin", "test.greet Ada Lovelace"), scene);

    assertEquals(true, scene.getState().getVariables().get("greeted"));
  }
}
