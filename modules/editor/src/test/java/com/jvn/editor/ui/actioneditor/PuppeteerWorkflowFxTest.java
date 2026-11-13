package com.jvn.editor.ui.actioneditor;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import javafx.event.Event;
import javafx.scene.control.Menu;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class PuppeteerWorkflowFxTest {
    @Test void workflowAvailabilityFollowsSourceAndStagedPreview() throws Exception {
        FxToolkit.runFx(() -> {
            var window = new PuppeteerWindow();
            try {
                Menu menu = window.buildWorkflowMenu();
                menu.getOnShowing().handle(new Event(Menu.ON_SHOWING));
                assertTrue(menu.getItems().get(0).isDisable());
                assertTrue(menu.getItems().get(6).isDisable());
                boolean[] opened = {false};
                window.setOnOpenTimelineFile(() -> opened[0] = true);
                window.setOnUpdateSource(code -> {});
                menu.getOnShowing().handle(new Event(Menu.ON_SHOWING));
                assertFalse(menu.getItems().get(0).isDisable());
                assertFalse(menu.getItems().get(6).isDisable());
                menu.getItems().get(0).fire();
                assertTrue(opened[0]);
                window.previewStaged = true;
                menu.getOnShowing().handle(new Event(Menu.ON_SHOWING));
                assertTrue(menu.getItems().get(4).isDisable());
                assertTrue(menu.getItems().get(5).isDisable());
                assertTrue(menu.getItems().get(6).isDisable());
                window.previewStaged = false;
            } finally { window.close(); }
            return null;
        });
    }
}
