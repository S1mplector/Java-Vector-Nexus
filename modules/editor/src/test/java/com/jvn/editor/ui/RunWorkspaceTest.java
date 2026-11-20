package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.jvn.core.diagnostics.RunTelemetry;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.nio.file.Files;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class RunWorkspaceTest {
  @Test void liveSessionUpdatesTheSidebarAndSupportsReloadThroughExit() throws Exception {
    FxToolkit.runFx(() -> {
      var view = new RunConsoleView("JVN Runtime");
      try {
        var scene = new Scene(view, 980, 620);
        EditorTheme.apply(scene);
        view.applyCss();
        view.layout();
        var tabs = (TabPane) view.getCenter();
        assertEquals(List.of("Output", "Render inspector"),
            tabs.getTabs().stream().map(tab -> tab.getText()).toList());
        var session = view.prepareInspector(":runtime:run");
        var state = (Label) view.lookup(".run-console-state");
        var reload = view.lookupAll(".button").stream().filter(Button.class::isInstance)
            .map(Button.class::cast).filter(button -> "Hot reload".equals(button.getText()))
            .findFirst().orElseThrow();
        assertTrue(reload.isDisabled());
        view.appendLine(new RunTelemetry("VnScene", 60, 1.2, 16777216L, 960, 540,
            1, 2, 0, 4, List.of()).encode());
        assertEquals("Running", state.getText());
        assertFalse(reload.isDisabled());
        reload.fire();
        assertTrue(Files.exists(session.resolve(RunTelemetry.RELOAD)));
        view.onProcessExit(0);
        assertEquals("Finished", state.getText());
        assertTrue(reload.isDisabled());
        assertFalse(Files.exists(session));
      } finally {
        view.dispose();
      }
      return null;
    });
  }
}
