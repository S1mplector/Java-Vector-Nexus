package com.jvn.editor.ui.actioneditor;

import com.jvn.editor.ui.PuppeteerLauncherPanel;
import com.jvn.core.scene2d.Sprite2D;
import com.jvn.scripting.jes.runtime.JesScene2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Retains independent VN group proxy state across prior timelines, then composes parent to child. */
public final class PuppeteerGroupReplay {
    private final Map<String, Map<PropertyType, Double>> states = new LinkedHashMap<>();

    public void record(AnimationProject timeline) {
        for (EntityTrack track : timeline.getTracks()) {
            var state = states.computeIfAbsent(track.getEntityName(), key -> new EnumMap<>(PropertyType.class));
            for (PropertyType property : track.getAnimatedProperties()) {
                state.put(property, track.getValueAt(property, timeline.getTotalDurationMs()));
            }
        }
    }

    public void apply(JesScene2D scene, PuppeteerLauncherPanel.SceneSnapshot snapshot,
                      Map<String, Map<PropertyType, Double>> baselines) {
        for (var character : snapshot.characters) {
            var groups = snapshot.resolveCharacterLayerGroups(character.characterId, character.expression);
            for (var layer : snapshot.resolveCharacterLayers(character.characterId, character.expression)) {
                var chain = PuppeteerLauncherPanel.snapshotLayerGroupChain(layer.layerId, groups);
                if (chain.size() < 2) continue;
                long animatedGroups = chain.stream().filter(group -> !stateFor(
                    PuppeteerLauncherPanel.equivalentSnapshotLayerGroupEntityNames(snapshot, character, group.groupId)).isEmpty()).count();
                if (animatedGroups < 2) continue;
                for (String name : PuppeteerLauncherPanel.snapshotLayerOccurrenceNames(snapshot, character, layer.layerId)) {
                    if (!(scene.find(name) instanceof Sprite2D sprite)) continue;
                    Map<PropertyType, Double> base = baselines.get(name);
                    if (base == null) continue;
                    double x = base.getOrDefault(PropertyType.X, sprite.getX());
                    double y = base.getOrDefault(PropertyType.Y, sprite.getY());
                    double ox = base.getOrDefault(PropertyType.PIVOT_X, .5);
                    double oy = base.getOrDefault(PropertyType.PIVOT_Y, 1.0);
                    double left = x - ox * sprite.getWidth();
                    double top = y - oy * sprite.getHeight();
                    AffineTransform transform = new AffineTransform();
                    double alpha = base.getOrDefault(PropertyType.ALPHA, 1.0);
                    boolean visible = base.getOrDefault(PropertyType.VISIBILITY, 1.0) >= .5;
                    for (var group : chain) {
                        var values = stateFor(PuppeteerLauncherPanel.equivalentSnapshotLayerGroupEntityNames(snapshot, character, group.groupId));
                        append(transform, values, left, top, sprite.getWidth(), sprite.getHeight(),
                            group.hasPivot ? group.pivotX : .5, group.hasPivot ? group.pivotY : 1.0);
                        alpha *= values.getOrDefault(PropertyType.ALPHA, 1.0);
                        visible &= values.getOrDefault(PropertyType.VISIBILITY, 1.0) >= .5;
                    }
                    var local = stateFor(PuppeteerLauncherPanel.equivalentSnapshotLayerEntityNames(snapshot, character, layer.layerId));
                    append(transform, local, left, top, sprite.getWidth(), sprite.getHeight(), ox, oy);
                    alpha *= local.getOrDefault(PropertyType.ALPHA, 1.0);
                    visible &= local.getOrDefault(PropertyType.VISIBILITY, 1.0) >= .5;
                    Point2D point = transform.transform(new Point2D.Double(x, y), null);
                    sprite.setPosition(point.getX(), point.getY());
                    sprite.setOrigin(ox, oy);
                    sprite.setRotationDeg(0);
                    sprite.setScale(1, 1);
                    sprite.setSupplementalTransform(transform.getScaleX(), transform.getShearX(),
                        transform.getShearY(), transform.getScaleY(), 0, 0);
                    sprite.setAlpha(alpha);
                    sprite.setVisible(visible);
                }
            }
        }
    }

    private Map<PropertyType, Double> stateFor(List<String> names) {
        for (String name : names) {
            var state = states.get(name);
            if (state != null && !state.isEmpty()) return state;
        }
        return Map.of();
    }

    private static void append(AffineTransform transform, Map<PropertyType, Double> state,
                               double x, double y, double width, double height, double ox, double oy) {
        transform.translate(state.getOrDefault(PropertyType.X, 0.0), state.getOrDefault(PropertyType.Y, 0.0));
        double px = x + state.getOrDefault(PropertyType.PIVOT_X, ox) * width;
        double py = y + state.getOrDefault(PropertyType.PIVOT_Y, oy) * height;
        transform.translate(px, py);
        transform.rotate(Math.toRadians(state.getOrDefault(PropertyType.ROTATION, 0.0)));
        double mirror = Math.cos(Math.PI * Math.max(0, Math.min(1, state.getOrDefault(PropertyType.MIRROR_X, 0.0))));
        transform.scale(state.getOrDefault(PropertyType.SCALE_X, 1.0) * mirror, state.getOrDefault(PropertyType.SCALE_Y, 1.0));
        transform.translate(-px, -py);
    }
}
