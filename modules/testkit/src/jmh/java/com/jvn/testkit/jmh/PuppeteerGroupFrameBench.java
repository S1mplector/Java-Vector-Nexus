package com.jvn.testkit.jmh;

import com.jvn.editor.ui.actioneditor.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/** Same layered rig and transform evaluator, with and without frame-scoped bounds reuse. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class PuppeteerGroupFrameBench {
    @Param({"32", "128"}) public int layers;
    private AnimationProject project;

    @Setup public void setup() {
        project = new AnimationProject();
        project.getOrCreateGroup("body");
        project.getOrCreateGroup("head");
        project.addGroupToGroup("head", "body");
        for (int i = 0; i < layers; i++) {
            EntityTrack track = project.getOrCreateTrack("layer" + i);
            track.upsertKeyframe(PropertyType.X, new Keyframe(0, i * 10));
            track.upsertKeyframe(PropertyType.Y, new Keyframe(0, i * 3));
            project.addEntityToGroup(track.getEntityName(), i % 2 == 0 ? "head" : "body");
        }
        project.getGroup("body").getGroupTrack().upsertKeyframe(PropertyType.ROTATION, new Keyframe(0, 35));
        project.getGroup("head").getGroupTrack().upsertKeyframe(PropertyType.SCALE_X, new Keyframe(0, -1));
    }

    @Benchmark public void uncachedFrame(Blackhole sink) { evaluate(sink); }
    @Benchmark public void cachedFrame(Blackhole sink) { project.evaluateFrame(() -> evaluate(sink)); }
    private void evaluate(Blackhole sink) {
        for (EntityTrack track : project.getTracks()) sink.consume(project.computeEffectiveEntityTransform(track.getEntityName(), 150));
    }
}
