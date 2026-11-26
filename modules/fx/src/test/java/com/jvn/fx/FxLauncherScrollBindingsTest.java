package com.jvn.fx;

import static org.junit.jupiter.api.Assertions.*;

import com.jvn.core.input.ActionBindingProfile;
import com.jvn.core.input.ActionMap;
import com.jvn.core.input.Input;
import com.jvn.core.input.InputActions;
import com.jvn.core.input.InputCode;
import com.jvn.core.vn.VnSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FxLauncherScrollBindingsTest {
  @Test
  void wheelDirectionsAreMomentaryBindableInputs() {
    var input = new Input();
    var map = new ActionMap(input);
    map.loadProfile(InputActions.defaultProfile());

    input.addScrollDeltaY(12);
    assertTrue(map.wasPressed(InputActions.ROLLBACK));
    assertFalse(map.isDown(InputActions.ROLLBACK));
    input.endFrame();
    assertFalse(map.wasPressed(InputActions.ROLLBACK));
    input.addScrollDeltaY(-12);
    assertFalse(map.wasPressed(InputActions.ROLLBACK));
  }

  @Test
  void fileAndSerializedProfilesCanReplaceOrDisableDefaultWheelBinding(@TempDir Path home) throws Exception {
    var settings = new VnSettings();
    var file = home.resolve("bindings.properties");
    settings.setInputProfilePath(file.toString());
    var up = InputCode.mouseWheel(1);
    var down = InputCode.mouseWheel(-1);
    var defaults = FxLauncherBindings.load(settings);
    assertTrue(defaults.matches(InputActions.ROLLBACK, up));
    assertTrue(defaults.matches(InputActions.ROLLBACK, InputCode.key("Page Up")));
    assertTrue(defaults.matches(InputActions.ROLLFORWARD, InputCode.key("Page Down")));

    Files.writeString(file, "rollback=" + down.encode() + "\n");
    var remapped = FxLauncherBindings.load(settings);
    assertFalse(remapped.matches(InputActions.ROLLBACK, up));
    assertTrue(remapped.matches(InputActions.ROLLBACK, down));

    settings.setInputProfileSerialized(ActionBindingProfile.deserialize("rollback=\n").serialize());
    var disabled = FxLauncherBindings.load(settings);
    assertFalse(disabled.matches(InputActions.ROLLBACK, up));
    assertFalse(disabled.matches(InputActions.ROLLBACK, down));
  }
}
