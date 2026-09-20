package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PuppeteerFrameEvaluationTest {
    @Test void cachedGroupBoundsMatchUncachedFramesAndObserveEdits() {
        AnimationProject project = new AnimationProject();
        project.getOrCreateGroup("body");
        project.getOrCreateGroup("head");
        project.addGroupToGroup("head", "body");
        for (int i = 0; i < 64; i++) {
            var track = project.getOrCreateTrack("layer" + i);
            track.upsertKeyframe(PropertyType.X, new Keyframe(0, i * 10));
            track.upsertKeyframe(PropertyType.Y, new Keyframe(0, i * 3));
            project.addEntityToGroup(track.getEntityName(), i % 2 == 0 ? "head" : "body");
        }
        project.getGroup("body").getGroupTrack().upsertKeyframe(PropertyType.ROTATION, new Keyframe(0, 35));
        project.getGroup("head").getGroupTrack().upsertKeyframe(PropertyType.SCALE_X, new Keyframe(0, -1));
        for (int pass = 0; pass < 2; pass++) {
            var expected = new ArrayList<AnimationProject.EffectiveEntityTransform>();
            for (var track : project.getTracks()) expected.add(project.computeEffectiveEntityTransform(track.getEntityName(), 0));
            var actual = new ArrayList<AnimationProject.EffectiveEntityTransform>();
            project.evaluateFrame(() -> {
                for (var track : project.getTracks()) actual.add(project.computeEffectiveEntityTransform(track.getEntityName(), 0));
            });
            assertEquals(expected, actual);
            project.getTrack("layer0").upsertKeyframe(PropertyType.X, new Keyframe(0, 3000));
        }
        assertThrows(IllegalStateException.class, () -> project.evaluateFrame(() -> { throw new IllegalStateException(); }));
        var after = project.computeEffectiveEntityTransform("layer0", 0);
        project.evaluateFrame(() -> assertEquals(after, project.computeEffectiveEntityTransform("layer0", 0)));
    }
}
