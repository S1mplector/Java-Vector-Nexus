package com.jvn.editor.ui.actioneditor;

import com.jvn.editor.EditorApp;
import com.jvn.editor.ui.PuppeteerLauncherPanel;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import com.jvn.scripting.jes.runtime.JesScene2D;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class PuppeteerLaunchHierarchyFxTest {
    @Test void realWindowKeepsHiddenParentsDuplicateChildrenAndDynamicMembership() throws Exception {
        String source = """
            @charlayer hero eyes eyes.png
            @charlayer hero body body.png
            @chargroup hero face parent=head $eyes
            @chargroup hero head parent=whole $body
            @chargroup hero overlap $eyes
            @chargroup hero whole $body
            @charpreset hero neutral $eyes | $eyes
            @group hero cast
            [show hero right neutral z=7]
            """;
        var snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, source.split("\n", -1).length - 1);
        assertEquals(4, snapshot.resolveCharacterLayerGroups("hero", "neutral").size());
        FxToolkit.runFx(() -> {
            var method = EditorApp.class.getDeclaredMethod("buildSceneFromSnapshot", PuppeteerLauncherPanel.SceneSnapshot.class);
            method.setAccessible(true);
            var scene = (JesScene2D) method.invoke(new EditorApp(), snapshot);
            var window = new PuppeteerWindow();
            try {
                window.setLaunchSceneSnapshot(snapshot);
                window.setScene(scene);
                var project = window.getProject();
                assertEquals("hero_face", project.getTrack("hero_eyes").getParentGroupName());
                assertEquals("hero_face", project.getTrack("hero_eyes_3").getParentGroupName());
                assertEquals("hero_head", project.getGroup("hero_face").getParentGroupName());
                assertEquals("hero_whole", project.getGroup("hero_head").getParentGroupName());
                assertTrue(project.getGroup("cast").getChildGroupNames().contains("hero_neutral"));
                assertEquals(7, project.computeEffectiveLayerOrder("hero_eyes"));
                assertEquals(7, scene.find("hero_eyes").getZ());
            } finally { window.close(); }
            return null;
        });
    }
}
