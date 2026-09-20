package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.jvn.core.animation.TimelineData;
import com.jvn.core.vn.VnEyeFocusProfile;

public class AnimationProject {
    private static final double MIN_DURATION_MS = 100.0;
    private static final double GROUP_POSITION_BAKE_INTERVAL_MS = 100.0;

    private String name = "Untitled Animation";
    private double totalDurationMs = 3000;
    private double playheadMs = 0;
    private boolean playing = false;
    private boolean looping = false;

    private final Map<String, EntityTrack> entityTracks = new LinkedHashMap<>();
    private final Map<String, EntityGroup> groups = new LinkedHashMap<>();
    private final List<String> rootEntityNames = new ArrayList<>();
    private final List<String> rootGroupNames = new ArrayList<>();

    private final List<AudioCue> audioCues = new ArrayList<>();
    private final List<EditorEventCue> editorEventCues = new ArrayList<>();
    private final Map<String, double[]> orbitAnchors = new LinkedHashMap<>();
    private final Map<String, String> orbitAnchorSources = new LinkedHashMap<>();
    private final Map<String, double[]> orbitAnchorSourceOffsets = new LinkedHashMap<>();
    private final Map<String, SceneEntitySnapshot> sceneEntitySnapshots = new LinkedHashMap<>();
    private final Map<String, Constraint> constraints = new LinkedHashMap<>();
    private final Map<String, Map<String, Anchor>> entityAnchors = new LinkedHashMap<>();
    private final Map<String, VnEyeFocusProfile> eyeFocusProfiles = new LinkedHashMap<>();
    private StageContext stageContext;

    private double loopStartMs = -1;
    private double loopEndMs = -1;

    private Map<String, Map<PropertyType, Double>> initialSnapshot;

    public AnimationProject() {}

    public record StageContext(
        String presetId,
        String sourcePath,
        String backgroundTag,
        String subjectTag,
        int lightCount,
        int occluderCount,
        int responseZoneCount
    ) {
        public StageContext {
            presetId = presetId == null ? "" : presetId.trim();
            sourcePath = sourcePath == null ? "" : sourcePath.trim();
            backgroundTag = backgroundTag == null ? "" : backgroundTag.trim();
            subjectTag = subjectTag == null ? "" : subjectTag.trim();
            lightCount = Math.max(0, lightCount);
            occluderCount = Math.max(0, occluderCount);
            responseZoneCount = Math.max(0, responseZoneCount);
        }

        public boolean isPresent() {
            return !presetId.isBlank();
        }
    }

    public record EffectiveEntityTransform(
        double x,
        double y,
        double z,
        double pivotX,
        double pivotY,
        double rotationDeg,
        double scaleX,
        double scaleY,
        double mirrorX,
        double alpha,
        double visibility
    ) {
        public double value(PropertyType property) {
            if (property == null) return 0.0;
            return switch (property) {
                case X -> x;
                case Y -> y;
                case Z -> z;
                case PIVOT_X -> pivotX;
                case PIVOT_Y -> pivotY;
                case ROTATION -> rotationDeg;
                case SCALE_X -> scaleX;
                case SCALE_Y -> scaleY;
                case MIRROR_X -> mirrorX;
                case ALPHA -> alpha;
                case VISIBILITY -> visibility;
                default -> property.getDefaultValue();
            };
        }
    }

    private record GroupBounds(double minX, double minY, double maxX, double maxY) {
        static GroupBounds empty() {
            return new GroupBounds(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        }

        boolean isEmpty() {
            return !Double.isFinite(minX) || !Double.isFinite(minY)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY)
                || maxX < minX || maxY < minY;
        }

        double width() {
            return isEmpty() ? 0.0 : maxX - minX;
        }

        double height() {
            return isEmpty() ? 0.0 : maxY - minY;
        }

        GroupBounds include(double x, double y) {
            if (!Double.isFinite(x) || !Double.isFinite(y)) return this;
            return new GroupBounds(
                Math.min(minX, x),
                Math.min(minY, y),
                Math.max(maxX, x),
                Math.max(maxY, y)
            );
        }

        GroupBounds include(double minX, double minY, double maxX, double maxY) {
            if (!Double.isFinite(minX) || !Double.isFinite(minY)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY)) {
                return this;
            }
            return new GroupBounds(
                Math.min(this.minX, Math.min(minX, maxX)),
                Math.min(this.minY, Math.min(minY, maxY)),
                Math.max(this.maxX, Math.max(minX, maxX)),
                Math.max(this.maxY, Math.max(minY, maxY))
            );
        }
    }

    public static final class SceneEntitySnapshot {
        private final String name;
        private final String type;
        private final String imagePath;
        private final double x;
        private final double y;
        private final double width;
        private final double height;
        private final double originX;
        private final double originY;
        private final double z;
        private final boolean visible;
        private final double alpha;
        private final double visualMinX;
        private final double visualMinY;
        private final double visualMaxX;
        private final double visualMaxY;
        private final double runtimeBaseX;
        private final double runtimeBaseY;

        public SceneEntitySnapshot(
            String name,
            String type,
            String imagePath,
            double x,
            double y,
            double width,
            double height,
            double originX,
            double originY,
            double z,
            boolean visible,
            double alpha
        ) {
            this(name, type, imagePath, x, y, width, height, originX, originY, z, visible, alpha,
                x - originX * width,
                y - originY * height,
                x - originX * width + width,
                y - originY * height + height);
        }

        public SceneEntitySnapshot(
            String name,
            String type,
            String imagePath,
            double x,
            double y,
            double width,
            double height,
            double originX,
            double originY,
            double z,
            boolean visible,
            double alpha,
            double visualMinX,
            double visualMinY,
            double visualMaxX,
            double visualMaxY
        ) {
            this(name, type, imagePath, x, y, width, height, originX, originY, z, visible, alpha,
                visualMinX, visualMinY, visualMaxX, visualMaxY, x, y);
        }

        public SceneEntitySnapshot(
            String name,
            String type,
            String imagePath,
            double x,
            double y,
            double width,
            double height,
            double originX,
            double originY,
            double z,
            boolean visible,
            double alpha,
            double visualMinX,
            double visualMinY,
            double visualMaxX,
            double visualMaxY,
            double runtimeBaseX,
            double runtimeBaseY
        ) {
            this.name = name != null ? name.trim() : "";
            this.type = type != null && !type.isBlank() ? type.trim() : "entity";
            this.imagePath = imagePath != null ? imagePath.trim() : "";
            this.x = sanitizeFinite(x, 0.0);
            this.y = sanitizeFinite(y, 0.0);
            this.width = Math.max(1.0, sanitizeFinite(width, 1.0));
            this.height = Math.max(1.0, sanitizeFinite(height, 1.0));
            this.originX = sanitizeFinite(originX, 0.0);
            this.originY = sanitizeFinite(originY, 0.0);
            this.z = sanitizeFinite(z, 0.0);
            this.visible = visible;
            this.alpha = sanitizeFinite(alpha, 1.0);
            double fallbackMinX = this.x - this.originX * this.width;
            double fallbackMinY = this.y - this.originY * this.height;
            double fallbackMaxX = fallbackMinX + this.width;
            double fallbackMaxY = fallbackMinY + this.height;
            this.visualMinX = sanitizeFinite(visualMinX, fallbackMinX);
            this.visualMinY = sanitizeFinite(visualMinY, fallbackMinY);
            this.visualMaxX = sanitizeFinite(visualMaxX, fallbackMaxX);
            this.visualMaxY = sanitizeFinite(visualMaxY, fallbackMaxY);
            this.runtimeBaseX = sanitizeFinite(runtimeBaseX, this.x);
            this.runtimeBaseY = sanitizeFinite(runtimeBaseY, this.y);
        }

        public String name() { return name; }
        public String type() { return type; }
        public String imagePath() { return imagePath; }
        public double x() { return x; }
        public double y() { return y; }
        public double width() { return width; }
        public double height() { return height; }
        public double originX() { return originX; }
        public double originY() { return originY; }
        public double z() { return z; }
        public boolean visible() { return visible; }
        public double alpha() { return alpha; }
        public double visualMinX() { return visualMinX; }
        public double visualMinY() { return visualMinY; }
        public double visualMaxX() { return visualMaxX; }
        public double visualMaxY() { return visualMaxY; }
        public double runtimeBaseX() { return runtimeBaseX; }
        public double runtimeBaseY() { return runtimeBaseY; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name != null ? name : "Untitled Animation"; }

    public double getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(double totalDurationMs) { 
        this.totalDurationMs = sanitizeDuration(totalDurationMs);
        this.playheadMs = clampTime(playheadMs, this.totalDurationMs);
        normalizeLoopRegion();
    }

    public double getPlayheadMs() { return playheadMs; }
    public void setPlayheadMs(double playheadMs) { 
        this.playheadMs = clampTime(playheadMs, totalDurationMs);
    }

    public boolean isPlaying() { return playing; }
    public void setPlaying(boolean playing) { this.playing = playing; }

    public boolean isLooping() { return looping; }
    public void setLooping(boolean looping) { this.looping = looping; }

    public double getLoopStartMs() { return loopStartMs; }
    public double getLoopEndMs() { return loopEndMs; }
    public boolean hasLoopRegion() { return loopStartMs >= 0 && loopEndMs > loopStartMs; }
    public void setLoopRegion(double startMs, double endMs) {
        this.loopStartMs = sanitizeNonNegativeFinite(startMs, 0.0);
        this.loopEndMs = sanitizeNonNegativeFinite(endMs, this.loopStartMs + 1.0);
        normalizeLoopRegion();
    }
    public void clearLoopRegion() {
        this.loopStartMs = -1;
        this.loopEndMs = -1;
    }

    public List<AudioCue> getAudioCues() { return Collections.unmodifiableList(audioCues); }
    public void addAudioCue(AudioCue cue) {
        if (cue != null) {
            audioCues.add(cue);
            audioCues.sort(AudioCue::compareTo);
        }
    }
    public void removeAudioCue(AudioCue cue) { audioCues.remove(cue); }
    public void clearAudioCues() { audioCues.clear(); }

    public List<EditorEventCue> getEditorEventCues() { return Collections.unmodifiableList(editorEventCues); }
    public void addEditorEventCue(EditorEventCue cue) {
        if (cue != null) {
            editorEventCues.add(cue);
            editorEventCues.sort(EditorEventCue::compareTo);
        }
    }
    public void removeEditorEventCue(EditorEventCue cue) { editorEventCues.remove(cue); }
    public void clearEditorEventCues() { editorEventCues.clear(); }
    public void sortEditorEventCues() { editorEventCues.sort(EditorEventCue::compareTo); }

    public Map<String, double[]> getOrbitAnchorsView() {
        Map<String, double[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> entry : orbitAnchors.entrySet()) {
            double[] value = entry.getValue();
            if (value == null || value.length < 2) continue;
            copy.put(entry.getKey(), new double[]{value[0], value[1]});
        }
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, String> getOrbitAnchorSourcesView() {
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : orbitAnchorSources.entrySet()) {
            String target = entry.getKey();
            String source = entry.getValue();
            if (target == null || target.isBlank() || source == null || source.isBlank()) continue;
            copy.put(target, source);
        }
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, double[]> getOrbitAnchorSourceOffsetsView() {
        Map<String, double[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> entry : orbitAnchorSourceOffsets.entrySet()) {
            String target = entry.getKey();
            double[] value = entry.getValue();
            if (target == null || target.isBlank() || value == null || value.length < 2) continue;
            if (!Double.isFinite(value[0]) || !Double.isFinite(value[1])) continue;
            copy.put(target, new double[]{value[0], value[1]});
        }
        return Collections.unmodifiableMap(copy);
    }

    public boolean hasOrbitAnchor(String entityName) {
        return entityName != null && orbitAnchors.containsKey(entityName);
    }

    public double[] getOrbitAnchor(String entityName) {
        if (entityName == null || entityName.isBlank()) return null;
        double[] value = orbitAnchors.get(entityName);
        if (value == null || value.length < 2) return null;
        return new double[]{value[0], value[1]};
    }

    public void setOrbitAnchor(String entityName, double worldX, double worldY) {
        if (entityName == null || entityName.isBlank()) return;
        if (!Double.isFinite(worldX) || !Double.isFinite(worldY)) return;
        orbitAnchors.put(entityName, new double[]{worldX, worldY});
        orbitAnchorSources.remove(entityName);
    }

    public void setOrbitAnchorSource(String entityName, String sourceEntityName) {
        setOrbitAnchorSource(entityName, sourceEntityName, 0.0, 0.0);
    }

    public void setOrbitAnchorSource(String entityName, String sourceEntityName, double offsetX, double offsetY) {
        if (entityName == null || entityName.isBlank()) return;
        if (sourceEntityName == null || sourceEntityName.isBlank()) {
            orbitAnchorSources.remove(entityName);
            orbitAnchorSourceOffsets.remove(entityName);
            return;
        }
        if (entityName.equals(sourceEntityName)) {
            orbitAnchorSources.remove(entityName);
            orbitAnchorSourceOffsets.remove(entityName);
            return;
        }
        orbitAnchorSources.put(entityName, sourceEntityName);
        setOrbitAnchorSourceOffset(entityName, offsetX, offsetY);
    }

    public void setOrbitAnchorSourceOffset(String entityName, double offsetX, double offsetY) {
        if (entityName == null || entityName.isBlank()) return;
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY)) return;
        if (!orbitAnchorSources.containsKey(entityName)) return;
        orbitAnchorSourceOffsets.put(entityName, new double[]{offsetX, offsetY});
    }

    public void clearOrbitAnchorSourceOffset(String entityName) {
        if (entityName == null || entityName.isBlank()) return;
        orbitAnchorSourceOffsets.remove(entityName);
    }

    public void removeOrbitAnchor(String entityName) {
        if (entityName == null || entityName.isBlank()) return;
        orbitAnchors.remove(entityName);
        orbitAnchorSources.remove(entityName);
        orbitAnchorSourceOffsets.remove(entityName);
    }

    public void clearOrbitAnchors() {
        orbitAnchors.clear();
        orbitAnchorSources.clear();
        orbitAnchorSourceOffsets.clear();
    }

    /**
     * True when {@code entityName} has rotation keyframes but its orbit pivot cannot be
     * resolved to a valid anchor point + non-degenerate scene snapshot at export time —
     * mirrors the fallback condition in CodeExporter.collectEvents, which silently exports
     * plain X/Y movement instead of pivot+rotate when this is the case. Also covers the
     * reload-risk case where the anchor is sourced from another entity whose own anchor/
     * snapshot is missing or invalid.
     */
    public boolean isOrbitPivotAtRisk(String entityName) {
        if (entityName == null || entityName.isBlank()) return false;
        if (!hasEffectiveAnimation(entityName, PropertyType.ROTATION)) return false;

        String sourceEntity = orbitAnchorSources.get(entityName);
        if (sourceEntity != null && !sourceEntity.isBlank()) {
            return !hasResolvableOrbitAnchor(sourceEntity);
        }
        return !hasResolvableOrbitAnchor(entityName);
    }

    private boolean hasResolvableOrbitAnchor(String entityName) {
        if (!hasOrbitAnchor(entityName)) return false;
        double[] anchor = orbitAnchors.get(entityName);
        if (anchor == null || anchor.length < 2) return false;
        SceneEntitySnapshot snap = sceneEntitySnapshots.get(entityName);
        return snap != null && snap.width() > 0 && snap.height() > 0;
    }

    public Map<String, SceneEntitySnapshot> getSceneEntitySnapshotsView() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(sceneEntitySnapshots));
    }

    public SceneEntitySnapshot getSceneEntitySnapshot(String entityName) {
        if (entityName == null || entityName.isBlank()) return null;
        return sceneEntitySnapshots.get(entityName);
    }

    public void setSceneEntitySnapshots(Iterable<SceneEntitySnapshot> snapshots) {
        sceneEntitySnapshots.clear();
        if (snapshots == null) return;
        for (SceneEntitySnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.name().isBlank()) continue;
            sceneEntitySnapshots.put(snapshot.name(), snapshot);
        }
    }

    public void clearSceneEntitySnapshots() {
        sceneEntitySnapshots.clear();
    }

    public StageContext getStageContext() {
        return stageContext;
    }

    public void setStageContext(StageContext stageContext) {
        this.stageContext = stageContext != null && stageContext.isPresent() ? stageContext : null;
    }

    public void clearStageContext() {
        this.stageContext = null;
    }

    public Map<String, VnEyeFocusProfile> getEyeFocusProfilesView() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(eyeFocusProfiles));
    }

    public VnEyeFocusProfile getEyeFocusProfile(String characterId, String expression) {
        if (characterId == null || characterId.isBlank()) return null;
        return eyeFocusProfiles.get(VnEyeFocusProfile.key(characterId, expression));
    }

    public void setEyeFocusProfile(VnEyeFocusProfile profile) {
        if (profile == null || profile.characterId().isBlank()) return;
        eyeFocusProfiles.put(profile.key(), profile);
    }

    public void setEyeFocusProfiles(Iterable<VnEyeFocusProfile> profiles) {
        eyeFocusProfiles.clear();
        if (profiles == null) return;
        for (VnEyeFocusProfile profile : profiles) {
            setEyeFocusProfile(profile);
        }
    }

    public void clearEyeFocusProfiles() {
        eyeFocusProfiles.clear();
    }

    // Constraint management
    public Map<String, Constraint> getConstraintsView() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(constraints));
    }

    public Constraint getConstraint(String entityName) {
        if (entityName == null || entityName.isBlank()) return null;
        return constraints.get(entityName);
    }

    public void setConstraint(String entityName, Constraint constraint) {
        if (entityName == null || entityName.isBlank()) return;
        if (constraint == null) {
            constraints.remove(entityName);
        } else {
            constraints.put(entityName, constraint);
        }
    }

    public void removeConstraint(String entityName) {
        if (entityName == null || entityName.isBlank()) return;
        constraints.remove(entityName);
    }

    public void clearConstraints() {
        constraints.clear();
    }

    // Anchor management
    public Map<String, Map<String, Anchor>> getEntityAnchorsView() {
        Map<String, Map<String, Anchor>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Anchor>> entry : entityAnchors.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, Anchor> getAnchorsForEntity(String entityName) {
        if (entityName == null || entityName.isBlank()) return Collections.emptyMap();
        Map<String, Anchor> anchors = entityAnchors.get(entityName);
        if (anchors == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(new LinkedHashMap<>(anchors));
    }

    public Anchor getAnchor(String entityName, String anchorName) {
        if (entityName == null || entityName.isBlank()) return null;
        if (anchorName == null || anchorName.isBlank()) return null;
        Map<String, Anchor> anchors = entityAnchors.get(entityName);
        if (anchors == null) return null;
        return anchors.get(anchorName);
    }

    public void setAnchor(String entityName, Anchor anchor) {
        if (entityName == null || entityName.isBlank()) return;
        if (anchor == null) return;
        entityAnchors.computeIfAbsent(entityName, k -> new LinkedHashMap<>()).put(anchor.getName(), anchor);
    }

    public void removeAnchor(String entityName, String anchorName) {
        if (entityName == null || entityName.isBlank()) return;
        if (anchorName == null || anchorName.isBlank()) return;
        Map<String, Anchor> anchors = entityAnchors.get(entityName);
        if (anchors != null) {
            anchors.remove(anchorName);
            if (anchors.isEmpty()) {
                entityAnchors.remove(entityName);
            }
        }
    }

    public void clearAnchorsForEntity(String entityName) {
        if (entityName == null || entityName.isBlank()) return;
        entityAnchors.remove(entityName);
    }

    public void clearAllAnchors() {
        entityAnchors.clear();
    }

    public void setOrbitAnchors(Map<String, double[]> anchors) {
        orbitAnchors.clear();
        if (anchors == null || anchors.isEmpty()) {
            orbitAnchorSources.clear();
            orbitAnchorSourceOffsets.clear();
            return;
        }
        for (Map.Entry<String, double[]> entry : anchors.entrySet()) {
            String name = entry.getKey();
            double[] value = entry.getValue();
            if (name == null || name.isBlank() || value == null || value.length < 2) continue;
            if (!Double.isFinite(value[0]) || !Double.isFinite(value[1])) continue;
            orbitAnchors.put(name, new double[]{value[0], value[1]});
        }
        orbitAnchorSources.keySet().removeIf(name -> !orbitAnchors.containsKey(name));
        orbitAnchorSourceOffsets.keySet().removeIf(name -> !orbitAnchorSources.containsKey(name));
    }

    public void setOrbitAnchorSources(Map<String, String> sources) {
        orbitAnchorSources.clear();
        orbitAnchorSourceOffsets.keySet().removeIf(name -> !orbitAnchors.containsKey(name));
        if (sources == null || sources.isEmpty()) return;
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String target = entry.getKey();
            String source = entry.getValue();
            if (target == null || target.isBlank() || source == null || source.isBlank()) continue;
            if (target.equals(source)) continue;
            if (!orbitAnchors.containsKey(target)) continue;
            orbitAnchorSources.put(target, source);
            orbitAnchorSourceOffsets.putIfAbsent(target, new double[]{0.0, 0.0});
        }
        orbitAnchorSourceOffsets.keySet().removeIf(name -> !orbitAnchorSources.containsKey(name));
    }

    public void setOrbitAnchorSourceOffsets(Map<String, double[]> offsets) {
        orbitAnchorSourceOffsets.clear();
        if (offsets == null || offsets.isEmpty()) return;
        for (Map.Entry<String, double[]> entry : offsets.entrySet()) {
            String target = entry.getKey();
            double[] value = entry.getValue();
            if (target == null || target.isBlank() || value == null || value.length < 2) continue;
            if (!Double.isFinite(value[0]) || !Double.isFinite(value[1])) continue;
            if (!orbitAnchorSources.containsKey(target)) continue;
            orbitAnchorSourceOffsets.put(target, new double[]{value[0], value[1]});
        }
        for (String target : orbitAnchorSources.keySet()) {
            orbitAnchorSourceOffsets.putIfAbsent(target, new double[]{0.0, 0.0});
        }
    }

    public void pruneOrbitAnchors(Set<String> validEntityNames) {
        if (validEntityNames == null || validEntityNames.isEmpty()) {
            orbitAnchors.clear();
            orbitAnchorSources.clear();
            return;
        }
        Set<String> allowed = new HashSet<>();
        for (String name : validEntityNames) {
            if (name != null && !name.isBlank()) allowed.add(name);
        }
        orbitAnchors.keySet().removeIf(name -> !allowed.contains(name));
        orbitAnchorSources.entrySet().removeIf(entry ->
            !allowed.contains(entry.getKey()) || !allowed.contains(entry.getValue()));
        orbitAnchorSourceOffsets.keySet().removeIf(name -> !orbitAnchorSources.containsKey(name));
    }

    private void removeOrbitAnchorReferencesTo(String entityName) {
        if (entityName == null || entityName.isBlank()) return;
        orbitAnchorSources.entrySet().removeIf(entry ->
            entityName.equals(entry.getKey()) || entityName.equals(entry.getValue()));
        orbitAnchorSourceOffsets.keySet().removeIf(name ->
            entityName.equals(name) || !orbitAnchorSources.containsKey(name));
        orbitAnchors.remove(entityName);
    }

    public void captureInitialSnapshot() {
        Map<String, Map<PropertyType, Double>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, EntityTrack> entry : entityTracks.entrySet()) {
            Map<PropertyType, Double> props = new LinkedHashMap<>();
            for (PropertyType p : PropertyType.values()) {
                props.put(p, entry.getValue().getValueAt(p, 0));
            }
            snapshot.put(entry.getKey(), props);
        }
        setInitialSnapshot(snapshot);
    }

    public Map<String, Map<PropertyType, Double>> getInitialSnapshot() {
        return initialSnapshot;
    }

    public void setInitialSnapshot(Map<String, Map<PropertyType, Double>> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            initialSnapshot = null;
            return;
        }
        Map<String, Map<PropertyType, Double>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<PropertyType, Double>> entry : snapshot.entrySet()) {
            String entityName = entry.getKey();
            if (entityName == null || entityName.isBlank()) continue;
            Map<PropertyType, Double> source = entry.getValue();
            Map<PropertyType, Double> props = new LinkedHashMap<>();
            if (source != null) {
                for (Map.Entry<PropertyType, Double> propEntry : source.entrySet()) {
                    PropertyType property = propEntry.getKey();
                    Double value = propEntry.getValue();
                    if (property == null || value == null || !Double.isFinite(value)) continue;
                    props.put(property, value);
                }
            }
            copy.put(entityName, props);
        }
        initialSnapshot = copy.isEmpty() ? null : copy;
    }

    public Double getInitialSnapshotValue(String entityName, PropertyType property) {
        if (initialSnapshot == null || entityName == null || property == null) return null;
        Map<PropertyType, Double> values = initialSnapshot.get(entityName);
        return values != null ? values.get(property) : null;
    }

    public void rebaseAutogeneratedStartKeyframes(Map<String, Map<PropertyType, Double>> baseline) {
        if (baseline == null || baseline.isEmpty()) return;
        for (EntityTrack track : entityTracks.values()) {
            if (track == null || track.getEntityName() == null) continue;
            Map<PropertyType, Double> values = baseline.get(track.getEntityName());
            if (values == null || values.isEmpty()) continue;
            for (PropertyType property : PropertyType.values()) {
                if (!property.isEntityProperty()) continue;
                Double baselineValue = values.get(property);
                if (baselineValue == null || !Double.isFinite(baselineValue)) continue;
                List<Keyframe> keyframes = track.getKeyframes(property);
                if (keyframes.size() < 2) continue;
                Keyframe first = keyframes.get(0);
                double parserDefault = parserDefaultValue(property);
                if (Math.abs(first.getValue() - parserDefault) > 0.0001) continue;
                if (Math.abs(baselineValue - parserDefault) <= 0.0001) continue;
                first.setValue(baselineValue);
            }
        }
    }

    public EntityTrack getTrack(String entityName) { return entityTracks.get(entityName); }

    public EntityTrack getOrCreateTrack(String entityName) {
        EntityTrack existing = entityTracks.get(entityName);
        if (existing != null) return existing;
        EntityTrack track = new EntityTrack(entityName);
        entityTracks.put(entityName, track);
        if (!rootEntityNames.contains(entityName)) {
            rootEntityNames.add(entityName);
        }
        return track;
    }

    public void addTrack(EntityTrack track) {
        if (track != null) {
            entityTracks.put(track.getEntityName(), track);
            if (!track.hasParent() && !rootEntityNames.contains(track.getEntityName())) {
                rootEntityNames.add(track.getEntityName());
            }
        }
    }

    public void removeTrack(String entityName) {
        EntityTrack track = entityTracks.remove(entityName);
        rootEntityNames.remove(entityName);
        removeOrbitAnchorReferencesTo(entityName);
        if (track != null && track.hasParent()) {
            EntityGroup g = groups.get(track.getParentGroupName());
            if (g != null) g.removeChildEntity(entityName);
        }
    }

    public Iterable<EntityTrack> getTracks() { return entityTracks.values(); }
    public int getTrackCount() { return entityTracks.size(); }

    public EntityGroup getGroup(String name) { return groups.get(name); }

    public String getEntityParentGroupName(String entityName) {
        EntityTrack track = entityTracks.get(entityName);
        return track != null ? track.getParentGroupName() : null;
    }

    public EntityGroup getOrCreateGroup(String name) {
        EntityGroup existing = groups.get(name);
        if (existing != null) return existing;

        EntityGroup group = new EntityGroup(name);
        groups.put(name, group);
        if (!group.hasParent() && !rootGroupNames.contains(group.getName())) {
            rootGroupNames.add(group.getName());
        }
        return group;
    }

    public void addGroup(EntityGroup group) {
        if (group != null) {
            groups.put(group.getName(), group);
            if (!group.hasParent() && !rootGroupNames.contains(group.getName())) {
                rootGroupNames.add(group.getName());
            }
        }
    }

    public void clearGroups() {
        groups.clear();
        rootGroupNames.clear();
        rootEntityNames.clear();
        for (EntityTrack track : entityTracks.values()) {
            track.setParentGroupName(null);
            if (!rootEntityNames.contains(track.getEntityName())) {
                rootEntityNames.add(track.getEntityName());
            }
        }
    }

    public void removeGroup(String name) {
        EntityGroup g = groups.remove(name);
        rootGroupNames.remove(name);
        if (g != null) {
            EntityGroup parent = g.hasParent() ? groups.get(g.getParentGroupName()) : null;
            if (g.hasParent()) {
                if (parent != null) parent.removeChildGroup(name);
            }
            for (String child : g.getChildEntityNames()) {
                EntityTrack t = entityTracks.get(child);
                if (t == null) continue;
                if (parent != null) {
                    t.setParentGroupName(parent.getName());
                    parent.addChildEntity(child);
                } else {
                    t.setParentGroupName(null);
                    if (!rootEntityNames.contains(child)) rootEntityNames.add(child);
                }
            }
            for (String childGroup : g.getChildGroupNames()) {
                EntityGroup cg = groups.get(childGroup);
                if (cg == null) continue;
                if (parent != null) {
                    cg.setParentGroupName(parent.getName());
                    parent.addChildGroup(childGroup);
                } else {
                    cg.setParentGroupName(null);
                    if (!rootGroupNames.contains(childGroup)) rootGroupNames.add(childGroup);
                }
            }
        }
    }

    public Iterable<EntityGroup> getGroups() { return groups.values(); }

    public List<String> getRootEntityNames() { return Collections.unmodifiableList(rootEntityNames); }
    public List<String> getRootGroupNames() { return Collections.unmodifiableList(rootGroupNames); }

    public List<String> collectGroupEntityNames(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return Collections.emptyList();
        }
        EntityGroup group = groups.get(groupName);
        if (group == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        collectGroupEntityNamesRecursive(group, names, new HashSet<>());
        return Collections.unmodifiableList(names);
    }

    private void collectGroupEntityNamesRecursive(EntityGroup group, List<String> out, Set<String> visitedGroups) {
        if (group == null || out == null || visitedGroups == null) {
            return;
        }
        if (!visitedGroups.add(group.getName())) {
            return;
        }
        for (String childEntity : group.getChildEntityNames()) {
            if (childEntity != null && !childEntity.isBlank() && !out.contains(childEntity)) {
                out.add(childEntity);
            }
        }
        for (String childGroupName : group.getChildGroupNames()) {
            EntityGroup childGroup = groups.get(childGroupName);
            if (childGroup != null) {
                collectGroupEntityNamesRecursive(childGroup, out, visitedGroups);
            }
        }
    }

    public static boolean isGroupProperty(PropertyType property) {
        return property == PropertyType.X
            || property == PropertyType.Y
            || property == PropertyType.Z
            || property == PropertyType.PIVOT_X
            || property == PropertyType.PIVOT_Y
            || property == PropertyType.ROTATION
            || property == PropertyType.SCALE_X
            || property == PropertyType.SCALE_Y
            || property == PropertyType.MIRROR_X
            || property == PropertyType.ALPHA
            || property == PropertyType.VISIBILITY
            || property == PropertyType.MATRIX_MXX
            || property == PropertyType.MATRIX_MXY
            || property == PropertyType.MATRIX_MYX
            || property == PropertyType.MATRIX_MYY
            || property == PropertyType.MATRIX_TX
            || property == PropertyType.MATRIX_TY
            || property == PropertyType.BLUR;
    }

    public void addEntityToGroup(String entityName, String groupName) {
        EntityTrack track = entityTracks.get(entityName);
        EntityGroup group = groups.get(groupName);
        if (track == null || group == null) return;

        String oldParent = track.getParentGroupName();
        if (oldParent != null) {
            EntityGroup oldGroup = groups.get(oldParent);
            if (oldGroup != null) oldGroup.removeChildEntity(entityName);
        }
        rootEntityNames.remove(entityName);

        track.setParentGroupName(groupName);
        group.addChildEntity(entityName);
    }

    public void removeEntityFromGroup(String entityName) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null || !track.hasParent()) return;

        EntityGroup group = groups.get(track.getParentGroupName());
        if (group != null) group.removeChildEntity(entityName);
        track.setParentGroupName(null);
        if (!rootEntityNames.contains(entityName)) rootEntityNames.add(entityName);
    }

    public void removeGroupFromParent(String groupName) {
        EntityGroup group = groups.get(groupName);
        if (group == null || !group.hasParent()) return;

        EntityGroup parent = groups.get(group.getParentGroupName());
        if (parent != null) {
            parent.removeChildGroup(groupName);
        }
        group.setParentGroupName(null);
        if (!rootGroupNames.contains(groupName)) {
            rootGroupNames.add(groupName);
        }
    }

    public boolean canAddGroupToGroup(String childGroupName, String parentGroupName) {
        EntityGroup child = groups.get(childGroupName);
        EntityGroup parent = groups.get(parentGroupName);
        if (child == null || parent == null) return false;
        if (childGroupName.equals(parentGroupName)) return false;
        return !wouldCreateCycle(parentGroupName, childGroupName);
    }

    public void addGroupToGroup(String childGroupName, String parentGroupName) {
        EntityGroup child = groups.get(childGroupName);
        EntityGroup parent = groups.get(parentGroupName);
        if (!canAddGroupToGroup(childGroupName, parentGroupName)) return;

        String oldParent = child.getParentGroupName();
        if (oldParent != null) {
            EntityGroup oldGroup = groups.get(oldParent);
            if (oldGroup != null) oldGroup.removeChildGroup(childGroupName);
        }
        rootGroupNames.remove(childGroupName);

        child.setParentGroupName(parentGroupName);
        parent.addChildGroup(childGroupName);
    }

    public boolean renameGroup(String currentName, String nextName) {
        if (currentName == null || currentName.isBlank() || nextName == null || nextName.isBlank()) return false;
        if (currentName.equals(nextName)) return true;
        EntityGroup source = groups.get(currentName);
        if (source == null || groups.containsKey(nextName)) return false;

        EntityGroup renamed = new EntityGroup(nextName);
        renamed.setParentGroupName(source.getParentGroupName());
        renamed.setExpanded(source.isExpanded());
        renamed.setLocked(source.isLocked());
        renamed.setLayerOrder(source.getLayerOrder());
        for (String childEntity : source.getChildEntityNames()) {
            renamed.addChildEntity(childEntity);
        }
        for (String childGroup : source.getChildGroupNames()) {
            renamed.addChildGroup(childGroup);
        }

        EntityTrack sourceTrack = source.getGroupTrack();
        EntityTrack renamedTrack = renamed.getGroupTrack();
        renamedTrack.setExpanded(sourceTrack.isExpanded());
        renamedTrack.setVisible(sourceTrack.isVisible());
        renamedTrack.setLocked(sourceTrack.isLocked());
        renamedTrack.setLayerOrder(sourceTrack.getLayerOrder());
        for (PropertyType property : PropertyType.values()) {
            List<Keyframe> sourceKeyframes = sourceTrack.getKeyframes(property);
            if (sourceKeyframes.isEmpty()) continue;
            List<Keyframe> copied = new ArrayList<>();
            for (Keyframe keyframe : sourceKeyframes) {
                copied.add(keyframe.copy());
            }
            renamedTrack.setKeyframes(property, copied);
        }
        for (String customKey : sourceTrack.getAnimatedCustomProperties()) {
            List<Keyframe> sourceKeyframes = sourceTrack.getCustomKeyframes(customKey);
            if (sourceKeyframes.isEmpty()) continue;
            List<Keyframe> copied = new ArrayList<>();
            for (Keyframe keyframe : sourceKeyframes) {
                copied.add(keyframe.copy());
            }
            renamedTrack.setCustomKeyframes(customKey, copied);
        }

        LinkedHashMap<String, EntityGroup> updated = new LinkedHashMap<>();
        for (Map.Entry<String, EntityGroup> entry : groups.entrySet()) {
            if (currentName.equals(entry.getKey())) {
                updated.put(nextName, renamed);
            } else {
                updated.put(entry.getKey(), entry.getValue());
            }
        }
        groups.clear();
        groups.putAll(updated);
        replaceName(rootGroupNames, currentName, nextName);

        if (source.hasParent()) {
            EntityGroup parent = groups.get(source.getParentGroupName());
            if (parent != null) {
                parent.replaceChildGroup(currentName, nextName);
            }
        }
        for (String childEntity : source.getChildEntityNames()) {
            EntityTrack track = entityTracks.get(childEntity);
            if (track != null) {
                track.setParentGroupName(nextName);
            }
        }
        for (String childGroup : source.getChildGroupNames()) {
            EntityGroup child = groups.get(childGroup);
            if (child != null) {
                child.setParentGroupName(nextName);
            }
        }
        return true;
    }

    private boolean wouldCreateCycle(String current, String proposedAncestor) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        String cursor = current;
        while (cursor != null) {
            if (!visited.add(cursor)) return true;
            if (cursor.equals(proposedAncestor)) return true;
            EntityGroup g = groups.get(cursor);
            cursor = (g != null) ? g.getParentGroupName() : null;
        }
        return false;
    }

    public double computeValueAt(String entityName, PropertyType property, double timeMs) {
        return computeValueAt(entityName, property, timeMs, property != null ? property.getDefaultValue() : 0.0);
    }

    public double computeValueAt(String entityName, PropertyType property, double timeMs, double fallbackLocalValue) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null || property == null) return fallbackLocalValue;
        if (usesEffectiveGroupTransform(track, property) || usesConstraintTransform(track, property)) {
            Map<PropertyType, Double> fallback = new EnumMap<>(PropertyType.class);
            fallback.put(property, fallbackLocalValue);
            return computeEffectiveEntityTransform(entityName, timeMs, fallback).value(property);
        }
        return track.hasKeyframes(property)
            ? track.getValueAt(property, timeMs)
            : fallbackLocalValue;
    }

    public EffectiveEntityTransform computeEffectiveEntityTransform(String entityName, double timeMs) {
        return computeEffectiveEntityTransform(entityName, timeMs, Collections.emptyMap());
    }

    public EffectiveEntityTransform computeEffectiveEntityTransform(
        String entityName,
        double timeMs,
        Map<PropertyType, Double> fallbackLocalValues
    ) {
        Set<String> constraintStack = constraints.containsKey(entityName)
            ? new HashSet<>()
            : Collections.emptySet();
        return computeEffectiveEntityTransform(entityName, timeMs, fallbackLocalValues, true, constraintStack);
    }

    EffectiveEntityTransform computeUnconstrainedEntityTransform(String entityName, double timeMs) {
        return computeEffectiveEntityTransform(
            entityName, timeMs, Collections.emptyMap(), false, Collections.emptySet());
    }

    EffectiveEntityTransform computeUnconstrainedEntityTransform(
        String entityName,
        double timeMs,
        Map<PropertyType, Double> fallbackLocalValues
    ) {
        return computeEffectiveEntityTransform(
            entityName, timeMs, fallbackLocalValues, false, Collections.emptySet());
    }

    private EffectiveEntityTransform computeEffectiveEntityTransform(
        String entityName,
        double timeMs,
        Map<PropertyType, Double> fallbackLocalValues,
        boolean applyConstraints,
        Set<String> constraintStack
    ) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null) {
            return defaultEffectiveEntityTransform(fallbackLocalValues);
        }

        double x = localValueAt(track, PropertyType.X, timeMs, fallbackLocalValues);
        double y = localValueAt(track, PropertyType.Y, timeMs, fallbackLocalValues);
        double z = localValueAt(track, PropertyType.Z, timeMs, fallbackLocalValues);
        double pivotX = localValueAt(track, PropertyType.PIVOT_X, timeMs, fallbackLocalValues);
        double pivotY = localValueAt(track, PropertyType.PIVOT_Y, timeMs, fallbackLocalValues);
        double rotation = localValueAt(track, PropertyType.ROTATION, timeMs, fallbackLocalValues);
        double scaleX = localValueAt(track, PropertyType.SCALE_X, timeMs, fallbackLocalValues);
        double scaleY = localValueAt(track, PropertyType.SCALE_Y, timeMs, fallbackLocalValues);
        double mirrorX = localValueAt(track, PropertyType.MIRROR_X, timeMs, fallbackLocalValues);
        scaleX *= mirrorFactor(mirrorX);
        double alpha = localValueAt(track, PropertyType.ALPHA, timeMs, fallbackLocalValues);
        double visibility = localValueAt(track, PropertyType.VISIBILITY, timeMs, fallbackLocalValues);

        String cursor = track.getParentGroupName();
        Set<String> visited = cursor != null ? new HashSet<>() : Collections.emptySet();
        while (cursor != null && visited.add(cursor)) {
            EntityGroup group = groups.get(cursor);
            if (group == null) break;
            EntityTrack groupTrack = group.getGroupTrack();

            double[] pivot = computeGroupPivot(group.getName(), timeMs);
            double tx = groupTrack.hasKeyframes(PropertyType.X)
                ? groupTrack.getValueAt(PropertyType.X, timeMs)
                : 0.0;
            double ty = groupTrack.hasKeyframes(PropertyType.Y)
                ? groupTrack.getValueAt(PropertyType.Y, timeMs)
                : 0.0;
            double groupRotation = groupTrack.hasKeyframes(PropertyType.ROTATION)
                ? groupTrack.getValueAt(PropertyType.ROTATION, timeMs)
                : 0.0;
            double groupScaleX = groupTrack.hasKeyframes(PropertyType.SCALE_X)
                ? groupTrack.getValueAt(PropertyType.SCALE_X, timeMs)
                : 1.0;
            if (groupTrack.hasKeyframes(PropertyType.MIRROR_X)) {
                groupScaleX *= mirrorFactor(groupTrack.getValueAt(PropertyType.MIRROR_X, timeMs));
            }
            double groupScaleY = groupTrack.hasKeyframes(PropertyType.SCALE_Y)
                ? groupTrack.getValueAt(PropertyType.SCALE_Y, timeMs)
                : 1.0;

            double dx = (x - pivot[0]) * groupScaleX;
            double dy = (y - pivot[1]) * groupScaleY;
            double radians = Math.toRadians(groupRotation);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            x = pivot[0] + tx + dx * cos - dy * sin;
            y = pivot[1] + ty + dx * sin + dy * cos;

            if (groupTrack.hasKeyframes(PropertyType.Z)) {
                z += groupTrack.getValueAt(PropertyType.Z, timeMs);
            }
            rotation += groupRotation;
            scaleX *= groupScaleX;
            scaleY *= groupScaleY;
            if (groupTrack.hasKeyframes(PropertyType.ALPHA)) {
                alpha *= groupTrack.getValueAt(PropertyType.ALPHA, timeMs);
            }
            if (groupTrack.hasKeyframes(PropertyType.VISIBILITY)
                    && groupTrack.getValueAt(PropertyType.VISIBILITY, timeMs) < 0.5) {
                visibility = 0.0;
            }
            cursor = group.getParentGroupName();
        }

        EffectiveEntityTransform transform =
            new EffectiveEntityTransform(x, y, z, pivotX, pivotY, rotation, scaleX, scaleY, mirrorX, alpha, visibility);
        if (!applyConstraints || entityName == null || constraintStack == null || constraintStack.contains(entityName)) {
            return transform;
        }
        Constraint constraint = constraints.get(entityName);
        if (constraint == null || constraint.getTargetEntityName() == null || constraint.getTargetEntityName().isBlank()) {
            return transform;
        }
        if (!constraintStack.add(entityName)) {
            return transform;
        }
        try {
            EffectiveEntityTransform target = computeEffectiveEntityTransform(
                constraint.getTargetEntityName(),
                timeMs,
                Collections.emptyMap(),
                true,
                constraintStack
            );
            return applyConstraint(transform, target, constraint);
        } finally {
            constraintStack.remove(entityName);
        }
    }

    private EffectiveEntityTransform applyConstraint(
        EffectiveEntityTransform base,
        EffectiveEntityTransform target,
        Constraint constraint
    ) {
        if (base == null || target == null || constraint == null || constraint.getType() == null) return base;
        if (constraint.getType() == Constraint.Type.LOOK_AT) {
            double dx = target.x() - base.x();
            double dy = target.y() - base.y();
            if (Math.abs(dx) < 0.001 && Math.abs(dy) < 0.001) return base;
            return new EffectiveEntityTransform(
                base.x(), base.y(), base.z(), base.pivotX(), base.pivotY(),
                Math.toDegrees(Math.atan2(dy, dx)),
                base.scaleX(), base.scaleY(), base.mirrorX(), base.alpha(), base.visibility()
            );
        }

        double offsetX = constraint.getOffsetX();
        double offsetY = constraint.getOffsetY();
        double radians = Math.toRadians(target.rotationDeg());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = target.x() + offsetX * cos - offsetY * sin;
        double y = target.y() + offsetX * sin + offsetY * cos;
        double rotation = constraint.isInheritRotation()
            ? target.rotationDeg() + base.rotationDeg()
            : base.rotationDeg();
        double scaleX = constraint.isInheritScale()
            ? target.scaleX() * base.scaleX()
            : base.scaleX();
        double scaleY = constraint.isInheritScale()
            ? target.scaleY() * base.scaleY()
            : base.scaleY();
        return new EffectiveEntityTransform(
            x, y, base.z(), base.pivotX(), base.pivotY(), rotation,
            scaleX, scaleY, base.mirrorX(), base.alpha(), base.visibility()
        );
    }

    public boolean hasEffectiveAnimation(String entityName, PropertyType property) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null || property == null) return false;
        if (track.hasKeyframes(property)) return true;
        if (usesConstraintTransform(track, property)) return true;
        if (!track.hasParent() || !property.isEntityProperty()) return false;
        if (property == PropertyType.X || property == PropertyType.Y) {
            return hasGroupSpatialAnimation(track.getParentGroupName());
        }
        return isInheritedGroupProperty(property)
            && hasInheritedGroupAnimation(track.getParentGroupName(), property);
    }

    public List<Keyframe> getEffectiveKeyframes(String entityName, PropertyType property) {
        if (entityName == null || entityName.isBlank() || property == null) return Collections.emptyList();
        EntityTrack track = entityTracks.get(entityName);
        if (track == null) return Collections.emptyList();
        if (!property.isEntityProperty()) return track.getKeyframes(property);
        if (!hasEffectiveAnimation(entityName, property)) {
            return track.getKeyframes(property);
        }

        Map<Long, Double> times = new TreeMap<>();
        collectEffectiveTimes(entityName, property, times);
        collectConstraintTimes(entityName, property, times);
        if (times.isEmpty()) return Collections.emptyList();

        List<Keyframe> out = new ArrayList<>();
        for (double time : times.values()) {
            Keyframe source = resolveEffectiveStyleSource(entityName, property, time);
            out.add(new Keyframe(
                time,
                computeValueAt(entityName, property, time, property.getDefaultValue()),
                source != null ? source.getEasingSpec() : com.jvn.core.animation.EasingSpec.of(com.jvn.core.animation.Easing.Type.LINEAR),
                source != null ? source.getInterpolation() : com.jvn.core.animation.Easing.Interpolation.TWEEN
            ));
        }
        return out;
    }

    public int computeEffectiveLayerOrder(String entityName) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null) return 0;

        int local = track.getLayerOrder();
        if (!track.hasParent()) return local;
        return local + computeGroupLayerOrder(track.getParentGroupName());
    }

    private int computeGroupLayerOrder(String groupName) {
        int total = 0;
        java.util.Set<String> visited = new java.util.HashSet<>();
        String cursor = groupName;
        while (cursor != null) {
            if (!visited.add(cursor)) break;
            EntityGroup group = groups.get(cursor);
            if (group == null) break;
            total += group.getLayerOrder();
            cursor = group.getParentGroupName();
        }
        return total;
    }

    private EffectiveEntityTransform defaultEffectiveEntityTransform(Map<PropertyType, Double> fallbackLocalValues) {
        return new EffectiveEntityTransform(
            fallbackValue(fallbackLocalValues, PropertyType.X),
            fallbackValue(fallbackLocalValues, PropertyType.Y),
            fallbackValue(fallbackLocalValues, PropertyType.Z),
            fallbackValue(fallbackLocalValues, PropertyType.PIVOT_X),
            fallbackValue(fallbackLocalValues, PropertyType.PIVOT_Y),
            fallbackValue(fallbackLocalValues, PropertyType.ROTATION),
            fallbackValue(fallbackLocalValues, PropertyType.SCALE_X),
            fallbackValue(fallbackLocalValues, PropertyType.SCALE_Y),
            fallbackValue(fallbackLocalValues, PropertyType.MIRROR_X),
            fallbackValue(fallbackLocalValues, PropertyType.ALPHA),
            fallbackValue(fallbackLocalValues, PropertyType.VISIBILITY)
        );
    }

    private double localValueAt(
        EntityTrack track,
        PropertyType property,
        double timeMs,
        Map<PropertyType, Double> fallbackLocalValues
    ) {
        if (track == null || property == null) return property != null ? property.getDefaultValue() : 0.0;
        return track.hasKeyframes(property)
            ? track.getValueAt(property, timeMs)
            : resolveBasePropertyValue(track.getEntityName(), property, fallbackLocalValues);
    }

    private double resolveBasePropertyValue(
        String entityName,
        PropertyType property,
        Map<PropertyType, Double> fallbackLocalValues
    ) {
        if (property == null) return 0.0;
        Double initial = getInitialSnapshotValue(entityName, property);
        if (initial != null && Double.isFinite(initial)) {
            return initial;
        }
        SceneEntitySnapshot snapshot = sceneEntitySnapshots.get(entityName);
        if (snapshot != null) {
            return switch (property) {
                case X -> snapshot.x();
                case Y -> snapshot.y();
                case Z -> snapshot.z();
                case PIVOT_X -> snapshot.originX();
                case PIVOT_Y -> snapshot.originY();
                case ALPHA -> snapshot.alpha();
                case VISIBILITY -> snapshot.visible() ? 1.0 : 0.0;
                default -> property.getDefaultValue();
            };
        }
        Double fallback = fallbackLocalValues != null ? fallbackLocalValues.get(property) : null;
        if (fallback != null && Double.isFinite(fallback)) {
            return fallback;
        }
        return property.getDefaultValue();
    }

    private static double fallbackValue(Map<PropertyType, Double> fallbackLocalValues, PropertyType property) {
        Double value = fallbackLocalValues != null ? fallbackLocalValues.get(property) : null;
        return value != null && Double.isFinite(value) ? value : property.getDefaultValue();
    }

    public double[] computeGroupLocalPivot(String groupName, double pivotX, double pivotY) {
        EntityGroup group = groups.get(groupName);
        if (group == null) return new double[]{0.0, 0.0};
        GroupBounds bounds = computeGroupBounds(groupName);
        if (bounds.isEmpty()) {
            return new double[]{0.0, 0.0};
        }
        pivotX = clamp01(pivotX);
        pivotY = clamp01(pivotY);

        return new double[]{
            bounds.minX() + bounds.width() * pivotX,
            bounds.minY() + bounds.height() * pivotY
        };
    }

    private double[] computeGroupPivot(String groupName, double timeMs) {
        EntityGroup group = groups.get(groupName);
        if (group == null) return new double[]{0.0, 0.0};
        EntityTrack groupTrack = group.getGroupTrack();
        double pivotX = groupTrack.hasKeyframes(PropertyType.PIVOT_X)
            ? groupTrack.getValueAt(PropertyType.PIVOT_X, timeMs)
            : PropertyType.PIVOT_X.getDefaultValue();
        double pivotY = groupTrack.hasKeyframes(PropertyType.PIVOT_Y)
            ? groupTrack.getValueAt(PropertyType.PIVOT_Y, timeMs)
            : PropertyType.PIVOT_Y.getDefaultValue();
        return computeGroupLocalPivot(groupName, pivotX, pivotY);
    }

    public double[] computeGroupRotationCenter(String groupName, double timeMs) {
        EntityGroup group = groups.get(groupName);
        if (group == null) return null;
        double[] center = computeGroupPivot(groupName, timeMs);
        if (center == null || center.length < 2
            || !Double.isFinite(center[0]) || !Double.isFinite(center[1])) {
            return null;
        }

        EntityTrack groupTrack = group.getGroupTrack();
        center[0] += groupTrack.hasKeyframes(PropertyType.X)
            ? groupTrack.getValueAt(PropertyType.X, timeMs)
            : 0.0;
        center[1] += groupTrack.hasKeyframes(PropertyType.Y)
            ? groupTrack.getValueAt(PropertyType.Y, timeMs)
            : 0.0;

        String cursor = group.getParentGroupName();
        Set<String> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            EntityGroup parent = groups.get(cursor);
            if (parent == null) break;
            EntityTrack parentTrack = parent.getGroupTrack();
            double[] pivot = computeGroupPivot(parent.getName(), timeMs);
            double tx = parentTrack.hasKeyframes(PropertyType.X)
                ? parentTrack.getValueAt(PropertyType.X, timeMs)
                : 0.0;
            double ty = parentTrack.hasKeyframes(PropertyType.Y)
                ? parentTrack.getValueAt(PropertyType.Y, timeMs)
                : 0.0;
            double sx = parentTrack.hasKeyframes(PropertyType.SCALE_X)
                ? parentTrack.getValueAt(PropertyType.SCALE_X, timeMs)
                : 1.0;
            if (parentTrack.hasKeyframes(PropertyType.MIRROR_X)) {
                sx *= mirrorFactor(parentTrack.getValueAt(PropertyType.MIRROR_X, timeMs));
            }
            double sy = parentTrack.hasKeyframes(PropertyType.SCALE_Y)
                ? parentTrack.getValueAt(PropertyType.SCALE_Y, timeMs)
                : 1.0;
            double rotation = parentTrack.hasKeyframes(PropertyType.ROTATION)
                ? parentTrack.getValueAt(PropertyType.ROTATION, timeMs)
                : 0.0;

            double dx = (center[0] - pivot[0]) * sx;
            double dy = (center[1] - pivot[1]) * sy;
            double radians = Math.toRadians(rotation);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            center[0] = pivot[0] + tx + dx * cos - dy * sin;
            center[1] = pivot[1] + ty + dx * sin + dy * cos;

            cursor = parent.getParentGroupName();
        }
        return center;
    }

    private Map<String, GroupBounds> frameGroupBounds;

    /** Evaluate one read-only preview frame, sharing group bounds across all layer tracks. */
    public void evaluateFrame(Runnable evaluation) {
        Map<String, GroupBounds> previous = frameGroupBounds;
        frameGroupBounds = new LinkedHashMap<>();
        try { evaluation.run(); }
        finally { frameGroupBounds = previous; }
    }

    private GroupBounds computeGroupBounds(String groupName) {
        if (frameGroupBounds != null) {
            GroupBounds cached = frameGroupBounds.get(groupName);
            if (cached != null) return cached;
        }
        GroupBounds bounds = GroupBounds.empty();
        for (String entityName : collectGroupEntityNames(groupName)) {
            bounds = includeEntityRestBounds(bounds, entityName);
        }
        if (frameGroupBounds != null) frameGroupBounds.put(groupName, bounds);
        return bounds;
    }

    private GroupBounds includeEntityRestBounds(GroupBounds bounds, String entityName) {
        SceneEntitySnapshot snapshot = sceneEntitySnapshots.get(entityName);
        if (snapshot != null) {
            return bounds.include(snapshot.visualMinX(), snapshot.visualMinY(), snapshot.visualMaxX(), snapshot.visualMaxY());
        }

        EntityTrack track = entityTracks.get(entityName);
        double x = track != null && track.hasKeyframes(PropertyType.X)
            ? track.getValueAt(PropertyType.X, 0.0)
            : resolveBasePropertyValue(entityName, PropertyType.X, Collections.emptyMap());
        double y = track != null && track.hasKeyframes(PropertyType.Y)
            ? track.getValueAt(PropertyType.Y, 0.0)
            : resolveBasePropertyValue(entityName, PropertyType.Y, Collections.emptyMap());
        return bounds.include(x, y);
    }

    private boolean usesEffectiveGroupTransform(EntityTrack track, PropertyType property) {
        return track != null
            && track.hasParent()
            && property != null
            && property.isEntityProperty()
            && (property == PropertyType.X
                || property == PropertyType.Y
                || isInheritedGroupProperty(property))
            && (property == PropertyType.X || property == PropertyType.Y
                ? hasGroupSpatialAnimation(track.getParentGroupName())
                : hasInheritedGroupAnimation(track.getParentGroupName(), property));
    }

    private boolean usesConstraintTransform(EntityTrack track, PropertyType property) {
        if (track == null || property == null || !property.isEntityProperty()) return false;
        Constraint constraint = constraints.get(track.getEntityName());
        if (constraint == null || constraint.getType() == null
                || constraint.getTargetEntityName() == null
                || !entityTracks.containsKey(constraint.getTargetEntityName())) {
            return false;
        }
        if (constraint.getType() == Constraint.Type.LOOK_AT) {
            return property == PropertyType.ROTATION;
        }
        if (property == PropertyType.X || property == PropertyType.Y) return true;
        if (property == PropertyType.ROTATION) return constraint.isInheritRotation();
        return (property == PropertyType.SCALE_X || property == PropertyType.SCALE_Y)
            && constraint.isInheritScale();
    }

    private boolean hasGroupSpatialAnimation(String groupName) {
        return hasAnyGroupAnimation(groupName,
            PropertyType.X,
            PropertyType.Y,
            PropertyType.PIVOT_X,
            PropertyType.PIVOT_Y,
            PropertyType.ROTATION,
            PropertyType.SCALE_X,
            PropertyType.SCALE_Y,
            PropertyType.MIRROR_X);
    }

    private boolean hasAnyGroupAnimation(String groupName, PropertyType... properties) {
        if (groupName == null || properties == null || properties.length == 0) return false;
        Set<String> visited = new HashSet<>();
        String cursor = groupName;
        while (cursor != null && visited.add(cursor)) {
            EntityGroup group = groups.get(cursor);
            if (group == null) break;
            EntityTrack groupTrack = group.getGroupTrack();
            for (PropertyType property : properties) {
                if (property != null && groupTrack.hasKeyframes(property)) return true;
            }
            cursor = group.getParentGroupName();
        }
        return false;
    }

    public double computeMaxTimeMs() {
        double max = 0;
        for (EntityTrack t : entityTracks.values()) {
            double tm = t.getMaxTimeMs();
            if (tm > max) max = tm;
        }
        for (EntityGroup g : groups.values()) {
            double tm = g.getMaxTimeMs();
            if (tm > max) max = tm;
        }
        return Math.max(max, 100);
    }

    public void fitDurationToContent() {
        setTotalDurationMs(computeMaxTimeMs() + 500);
    }

    public AnimationProject copy() {
        AnimationProject copy = new AnimationProject();
        copy.name = name;
        copy.totalDurationMs = totalDurationMs;
        copy.playheadMs = playheadMs;
        copy.looping = looping;
        copy.loopStartMs = loopStartMs;
        copy.loopEndMs = loopEndMs;
        copy.setOrbitAnchors(getOrbitAnchorsView());
        copy.setOrbitAnchorSources(getOrbitAnchorSourcesView());
        copy.setOrbitAnchorSourceOffsets(getOrbitAnchorSourceOffsetsView());
        for (Map.Entry<String, Constraint> entry : constraints.entrySet()) {
            copy.constraints.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Map<String, Anchor>> entry : entityAnchors.entrySet()) {
            copy.entityAnchors.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        for (EntityTrack t : entityTracks.values()) {
            copy.entityTracks.put(t.getEntityName(), t.copy());
        }
        for (EntityGroup g : groups.values()) {
            copy.groups.put(g.getName(), g.copy());
        }
        for (AudioCue cue : audioCues) {
            copy.audioCues.add(cue.copy());
        }
        for (EditorEventCue evt : editorEventCues) {
            copy.editorEventCues.add(evt.copy());
        }
        copy.rootEntityNames.addAll(rootEntityNames);
        copy.rootGroupNames.addAll(rootGroupNames);
        copy.setInitialSnapshot(initialSnapshot);
        copy.setSceneEntitySnapshots(sceneEntitySnapshots.values());
        copy.setStageContext(stageContext);
        copy.setEyeFocusProfiles(eyeFocusProfiles.values());
        return copy;
    }

    /**
     * Convert this editor-side AnimationProject to a lightweight runtime
     * {@link TimelineData} suitable for registration in
     * {@link com.jvn.core.animation.TimelineRegistry}.
     */
    public TimelineData toTimelineData(String timelineName) {
        TimelineData data = new TimelineData(
            timelineName != null ? timelineName : name,
            totalDurationMs
        );
        data.setLooping(looping);

        for (EntityTrack et : entityTracks.values()) {
            TimelineData.Track track = new TimelineData.Track(et.getEntityName());
            boolean hasData = false;

            for (PropertyType prop : PropertyType.values()) {
                List<Keyframe> keyframes = prop.isEntityProperty()
                    ? getEffectiveKeyframes(et.getEntityName(), prop)
                    : et.getKeyframes(prop);
                if (keyframes.isEmpty()) continue;
                hasData = true;

                for (Keyframe kf : keyframes) {
                    TimelineData.Keyframe runtimeKeyframe = new TimelineData.Keyframe(
                        kf.getTimeMs(),
                        kf.getValue(),
                        kf.getEasingSpec(),
                        kf.getInterpolation()
                    );
                    TimelineData.Property runtimeProp = mapProperty(prop);
                    if (runtimeProp != null) {
                        track.addKeyframe(runtimeProp, runtimeKeyframe);
                    } else if (prop.isTimelineCustomProperty()) {
                        track.addCustomKeyframe(prop.getTimelineCustomKey(), runtimeKeyframe);
                    }
                }
            }

            for (String customPropertyKey : et.getAnimatedCustomProperties()) {
                if (customPropertyKey == null || customPropertyKey.isBlank()) continue;
                List<Keyframe> keyframes = et.getCustomKeyframes(customPropertyKey);
                if (keyframes.isEmpty()) continue;
                hasData = true;
                for (Keyframe kf : keyframes) {
                    track.addCustomKeyframe(customPropertyKey,
                        new TimelineData.Keyframe(
                            kf.getTimeMs(),
                            kf.getValue(),
                            kf.getEasingSpec(),
                            kf.getInterpolation()
                        ));
                }
            }

            int layerOrder = computeEffectiveLayerOrder(et.getEntityName());
            if (layerOrder != 0 && !track.hasKeyframes(TimelineData.Property.Z)) {
                hasData = true;
                track.addKeyframe(TimelineData.Property.Z,
                    new TimelineData.Keyframe(0, layerOrder, com.jvn.core.animation.Easing.Type.LINEAR));
            }

            if (hasData) data.addTrack(track);
        }
        for (AudioCue cue : audioCues) {
            if (cue == null || cue.getAudioFile() == null || cue.getAudioFile().isBlank()) continue;
            data.addAudioCue(new TimelineData.AudioCue(
                cue.getTimeMs(),
                cue.getAudioFile(),
                cue.getChannel(),
                cue.getVolume(),
                "music".equalsIgnoreCase(cue.getChannel()),
                cue.isFadeIn() ? cue.getFadeDurationMs() : 0.0
            ));
        }
        for (EditorEventCue evt : editorEventCues) {
            if (evt == null || evt.getType().isBlank()) continue;
            data.addEventCue(new TimelineData.EventCue(
                evt.getTimeMs(), evt.getType(), evt.getPayloadView()));
        }
        return data;
    }

    /**
     * Replaces all tracks, cues, and settings in this project with data from another.
     * The scene graph (entities) is not affected — only the animation model is swapped.
     */
    public void replaceFrom(AnimationProject other) {
        if (other == null) return;
        this.name = other.name;
        setTotalDurationMs(other.totalDurationMs);
        setPlayheadMs(other.playheadMs);
        this.looping = other.looping;
        if (other.hasLoopRegion()) {
            setLoopRegion(other.loopStartMs, other.loopEndMs);
        } else {
            clearLoopRegion();
        }

        this.entityTracks.clear();
        this.rootEntityNames.clear();
        for (EntityTrack t : other.entityTracks.values()) {
            this.entityTracks.put(t.getEntityName(), t.copy());
            if (!t.hasParent()) this.rootEntityNames.add(t.getEntityName());
        }

        this.groups.clear();
        this.rootGroupNames.clear();
        for (EntityGroup g : other.groups.values()) {
            EntityGroup copy = g.copy();
            this.groups.put(copy.getName(), copy);
            if (!g.hasParent()) this.rootGroupNames.add(g.getName());
        }

        this.audioCues.clear();
        for (AudioCue cue : other.audioCues) {
            this.audioCues.add(cue.copy());
        }
        this.editorEventCues.clear();
        for (EditorEventCue evt : other.editorEventCues) {
            this.editorEventCues.add(evt.copy());
        }
        this.setOrbitAnchors(other.getOrbitAnchorsView());
        this.setOrbitAnchorSources(other.getOrbitAnchorSourcesView());
        this.setOrbitAnchorSourceOffsets(other.getOrbitAnchorSourceOffsetsView());
        this.constraints.clear();
        for (Map.Entry<String, Constraint> entry : other.constraints.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                this.constraints.put(entry.getKey(), entry.getValue());
            }
        }
        this.entityAnchors.clear();
        for (Map.Entry<String, Map<String, Anchor>> entry : other.entityAnchors.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
            this.entityAnchors.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        this.setInitialSnapshot(other.initialSnapshot);
        this.setSceneEntitySnapshots(other.sceneEntitySnapshots.values());
        this.setStageContext(other.stageContext);
        this.setEyeFocusProfiles(other.eyeFocusProfiles.values());
    }

    private boolean hasGroupAnimation(String groupName, PropertyType property) {
        if (!isGroupProperty(property)) return false;
        Set<String> visited = new HashSet<>();
        String cursor = groupName;
        while (cursor != null && visited.add(cursor)) {
            EntityGroup group = groups.get(cursor);
            if (group == null) break;
            if (group.getGroupTrack().hasKeyframes(property)) {
                return true;
            }
            cursor = group.getParentGroupName();
        }
        return false;
    }

    private boolean hasInheritedGroupAnimation(String groupName, PropertyType property) {
        if (property == PropertyType.SCALE_X && hasGroupAnimation(groupName, PropertyType.MIRROR_X)) {
            return true;
        }
        return hasGroupAnimation(groupName, property);
    }

    private void collectEffectiveTimes(String entityName, PropertyType property, Map<Long, Double> out) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null || property == null || out == null) return;
        collectTimes(track.getKeyframes(property), out);
        if (!track.hasParent()) return;

        if (property == PropertyType.X || property == PropertyType.Y) {
            PropertyType paired = property == PropertyType.X ? PropertyType.Y : PropertyType.X;
            collectTimes(track.getKeyframes(paired), out);
            collectGroupTimes(track.getParentGroupName(), out,
                PropertyType.X,
                PropertyType.Y,
                PropertyType.PIVOT_X,
                PropertyType.PIVOT_Y,
                PropertyType.ROTATION,
                PropertyType.SCALE_X,
                PropertyType.SCALE_Y,
                PropertyType.MIRROR_X);
            addGroupPositionBakeSamples(track.getParentGroupName(), out);
            return;
        }

        if (property == PropertyType.SCALE_X) {
            collectGroupTimes(track.getParentGroupName(), out, PropertyType.MIRROR_X);
            addGroupMirrorBakeSamples(track.getParentGroupName(), out);
        }

        if (isInheritedGroupProperty(property)) {
            collectGroupTimes(track.getParentGroupName(), out, property);
        }
    }

    private void collectConstraintTimes(String entityName, PropertyType property, Map<Long, Double> out) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null || property == null || out == null || !usesConstraintTransform(track, property)) return;
        Constraint constraint = constraints.get(entityName);
        if (constraint == null) return;

        putTime(out, 0.0);
        if (constraint.getType() == Constraint.Type.PARENT_CHILD) {
            collectParentChildConstraintTimes(track, constraint, property, out);
        } else if (property == PropertyType.ROTATION) {
            collectUnconstrainedTimes(entityName, PropertyType.X, out);
            collectUnconstrainedTimes(entityName, PropertyType.Y, out);
            collectUnconstrainedTimes(constraint.getTargetEntityName(), PropertyType.X, out);
            collectUnconstrainedTimes(constraint.getTargetEntityName(), PropertyType.Y, out);
            addBakeSamplesBetweenCollectedTimes(out);
        }
    }

    private void collectParentChildConstraintTimes(
        EntityTrack childTrack,
        Constraint constraint,
        PropertyType property,
        Map<Long, Double> out
    ) {
        String target = constraint.getTargetEntityName();
        if (property == PropertyType.X || property == PropertyType.Y) {
            collectUnconstrainedTimes(target, PropertyType.X, out);
            collectUnconstrainedTimes(target, PropertyType.Y, out);
            if (Math.abs(constraint.getOffsetX()) > 0.0001 || Math.abs(constraint.getOffsetY()) > 0.0001) {
                collectUnconstrainedTimes(target, PropertyType.ROTATION, out);
                addBakeSamplesBetweenCollectedTimes(out);
            }
            return;
        }
        if (property == PropertyType.ROTATION && constraint.isInheritRotation()) {
            collectTimes(childTrack.getKeyframes(PropertyType.ROTATION), out);
            collectUnconstrainedTimes(target, PropertyType.ROTATION, out);
            return;
        }
        if ((property == PropertyType.SCALE_X || property == PropertyType.SCALE_Y) && constraint.isInheritScale()) {
            collectTimes(childTrack.getKeyframes(property), out);
            if (property == PropertyType.SCALE_X) collectTimes(childTrack.getKeyframes(PropertyType.MIRROR_X), out);
            collectUnconstrainedTimes(target, property, out);
        }
    }

    private void collectUnconstrainedTimes(String entityName, PropertyType property, Map<Long, Double> out) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null || property == null || out == null) return;
        collectTimes(track.getKeyframes(property), out);
        if (!track.hasParent()) return;

        if (property == PropertyType.X || property == PropertyType.Y) {
            PropertyType paired = property == PropertyType.X ? PropertyType.Y : PropertyType.X;
            collectTimes(track.getKeyframes(paired), out);
            collectGroupTimes(track.getParentGroupName(), out,
                PropertyType.X,
                PropertyType.Y,
                PropertyType.PIVOT_X,
                PropertyType.PIVOT_Y,
                PropertyType.ROTATION,
                PropertyType.SCALE_X,
                PropertyType.SCALE_Y,
                PropertyType.MIRROR_X);
            addGroupPositionBakeSamples(track.getParentGroupName(), out);
            return;
        }

        if (property == PropertyType.SCALE_X) {
            collectTimes(track.getKeyframes(PropertyType.MIRROR_X), out);
            collectGroupTimes(track.getParentGroupName(), out, PropertyType.MIRROR_X);
            addGroupMirrorBakeSamples(track.getParentGroupName(), out);
        }
        if (isInheritedGroupProperty(property)) {
            collectGroupTimes(track.getParentGroupName(), out, property);
        }
    }

    private Keyframe resolveEffectiveStyleSource(String entityName, PropertyType property, double timeMs) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null) return null;
        Keyframe local = track.findKeyframeAt(property, timeMs);
        if (local != null) return local;

        if (property == PropertyType.X || property == PropertyType.Y) {
            PropertyType paired = property == PropertyType.X ? PropertyType.Y : PropertyType.X;
            Keyframe pairedLocal = track.findKeyframeAt(paired, timeMs);
            if (pairedLocal != null) return pairedLocal;
            Keyframe source = findGroupKeyframeAt(track.getParentGroupName(), timeMs,
                PropertyType.X,
                PropertyType.Y,
                PropertyType.PIVOT_X,
                PropertyType.PIVOT_Y,
                PropertyType.ROTATION,
                PropertyType.SCALE_X,
                PropertyType.SCALE_Y,
                PropertyType.MIRROR_X);
            if (source != null) return source;
            return null;
        }

        if (property == PropertyType.SCALE_X) {
            Keyframe source = findGroupKeyframeAt(track.getParentGroupName(), timeMs, PropertyType.MIRROR_X);
            if (source != null) return source;
        }

        return findGroupKeyframeAt(track.getParentGroupName(), timeMs, property);
    }

    private void collectGroupTimes(String groupName, Map<Long, Double> out, PropertyType... properties) {
        if (groupName == null || out == null || properties == null) return;
        Set<String> visited = new HashSet<>();
        String cursor = groupName;
        while (cursor != null && visited.add(cursor)) {
            EntityGroup group = groups.get(cursor);
            if (group == null) break;
            EntityTrack groupTrack = group.getGroupTrack();
            for (PropertyType property : properties) {
                collectTimes(groupTrack.getKeyframes(property), out);
            }
            cursor = group.getParentGroupName();
        }
    }

    private Keyframe findGroupKeyframeAt(String groupName, double timeMs, PropertyType... properties) {
        if (groupName == null || properties == null) return null;
        Set<String> visited = new HashSet<>();
        String cursor = groupName;
        while (cursor != null && visited.add(cursor)) {
            EntityGroup group = groups.get(cursor);
            if (group == null) break;
            EntityTrack groupTrack = group.getGroupTrack();
            for (PropertyType property : properties) {
                Keyframe source = groupTrack.findKeyframeAt(property, timeMs);
                if (source != null) return source;
            }
            cursor = group.getParentGroupName();
        }
        return null;
    }

    private void addGroupPositionBakeSamples(String groupName, Map<Long, Double> out) {
        if (groupName == null || out == null) return;
        Map<Long, Double> arcTimes = new TreeMap<>();
        collectGroupTimes(groupName, arcTimes,
            PropertyType.PIVOT_X,
            PropertyType.PIVOT_Y,
            PropertyType.ROTATION,
            PropertyType.SCALE_X,
            PropertyType.SCALE_Y,
            PropertyType.MIRROR_X);
        if (arcTimes.size() < 2) return;

        List<Double> times = new ArrayList<>(arcTimes.values());
        Collections.sort(times);
        for (int i = 0; i < times.size() - 1; i++) {
            double start = times.get(i);
            double end = times.get(i + 1);
            double span = end - start;
            if (span <= GROUP_POSITION_BAKE_INTERVAL_MS) continue;
            int steps = (int) Math.floor(span / GROUP_POSITION_BAKE_INTERVAL_MS);
            for (int step = 1; step <= steps; step++) {
                double sample = start + step * GROUP_POSITION_BAKE_INTERVAL_MS;
                if (sample >= end - 0.001) continue;
                long quantized = Math.round(sample * 1000.0);
                out.putIfAbsent(quantized, sample);
            }
        }
    }

    private void addGroupMirrorBakeSamples(String groupName, Map<Long, Double> out) {
        if (groupName == null || out == null) return;
        Map<Long, Double> mirrorTimes = new TreeMap<>();
        collectGroupTimes(groupName, mirrorTimes, PropertyType.MIRROR_X);
        if (mirrorTimes.size() < 2) return;

        List<Double> times = new ArrayList<>(mirrorTimes.values());
        Collections.sort(times);
        for (int i = 0; i < times.size() - 1; i++) {
            double start = times.get(i);
            double end = times.get(i + 1);
            double span = end - start;
            if (span <= GROUP_POSITION_BAKE_INTERVAL_MS) continue;
            int steps = (int) Math.floor(span / GROUP_POSITION_BAKE_INTERVAL_MS);
            for (int step = 1; step <= steps; step++) {
                double sample = start + step * GROUP_POSITION_BAKE_INTERVAL_MS;
                if (sample >= end - 0.001) continue;
                long quantized = Math.round(sample * 1000.0);
                out.putIfAbsent(quantized, sample);
            }
        }
    }

    private void addBakeSamplesBetweenCollectedTimes(Map<Long, Double> out) {
        if (out == null || out.size() < 2) return;
        List<Double> times = new ArrayList<>(out.values());
        Collections.sort(times);
        for (int i = 0; i < times.size() - 1; i++) {
            double start = times.get(i);
            double end = times.get(i + 1);
            double span = end - start;
            if (span <= GROUP_POSITION_BAKE_INTERVAL_MS) continue;
            int steps = (int) Math.floor(span / GROUP_POSITION_BAKE_INTERVAL_MS);
            for (int step = 1; step <= steps; step++) {
                double sample = start + step * GROUP_POSITION_BAKE_INTERVAL_MS;
                if (sample >= end - 0.001) continue;
                putTime(out, sample);
            }
        }
    }

    private void collectTimes(List<Keyframe> keyframes, Map<Long, Double> out) {
        if (keyframes == null || out == null) return;
        for (Keyframe keyframe : keyframes) {
            if (keyframe == null) continue;
            putTime(out, keyframe.getTimeMs());
        }
    }

    private void putTime(Map<Long, Double> out, double timeMs) {
        if (out == null || !Double.isFinite(timeMs)) return;
        long quantized = Math.round(timeMs * 1000.0);
        out.putIfAbsent(quantized, Math.max(0.0, timeMs));
    }

    private static boolean isInheritedGroupProperty(PropertyType property) {
        return property == PropertyType.X
            || property == PropertyType.Y
            || property == PropertyType.Z
            || property == PropertyType.ROTATION
            || property == PropertyType.SCALE_X
            || property == PropertyType.SCALE_Y
            || property == PropertyType.ALPHA
            || property == PropertyType.VISIBILITY;
    }

    private static void replaceName(List<String> values, String currentName, String nextName) {
        if (values == null || currentName == null || nextName == null || currentName.equals(nextName)) return;
        int index = values.indexOf(currentName);
        if (index < 0) return;
        if (values.contains(nextName)) {
            values.remove(index);
            return;
        }
        values.set(index, nextName);
    }

    private static TimelineData.Property mapProperty(PropertyType p) {
        return switch (p) {
            case X -> TimelineData.Property.X;
            case Y -> TimelineData.Property.Y;
            case Z -> TimelineData.Property.Z;
            case PIVOT_X -> TimelineData.Property.PIVOT_X;
            case PIVOT_Y -> TimelineData.Property.PIVOT_Y;
            case ROTATION -> TimelineData.Property.ROTATION;
            case SCALE_X -> TimelineData.Property.SCALE_X;
            case SCALE_Y -> TimelineData.Property.SCALE_Y;
            case MIRROR_X -> TimelineData.Property.MIRROR_X;
            case ALPHA -> TimelineData.Property.ALPHA;
            case VISIBILITY -> TimelineData.Property.VISIBILITY;
            case CAMERA_X -> TimelineData.Property.CAMERA_X;
            case CAMERA_Y -> TimelineData.Property.CAMERA_Y;
            case CAMERA_ZOOM -> TimelineData.Property.CAMERA_ZOOM;
            case MATRIX_MXX, MATRIX_MXY, MATRIX_MYX, MATRIX_MYY, MATRIX_TX, MATRIX_TY,
                BLUR, BRIGHTNESS, CAMERA_DOF_FOCUS, CAMERA_DOF_STRENGTH, CAMERA_DOF_MAX_BLUR -> null;
        };
    }

    private void normalizeLoopRegion() {
        if (!Double.isFinite(loopStartMs) || !Double.isFinite(loopEndMs)) {
            clearLoopRegion();
            return;
        }
        if (loopStartMs < 0 || loopEndMs <= loopStartMs) {
            clearLoopRegion();
            return;
        }

        double maxStart = Math.max(0.0, totalDurationMs - 1.0);
        double start = Math.min(loopStartMs, maxStart);
        double end = Math.max(start + 1.0, loopEndMs);
        end = Math.min(end, totalDurationMs);

        if (end <= start) {
            clearLoopRegion();
            return;
        }
        loopStartMs = start;
        loopEndMs = end;
    }

    private static double sanitizeDuration(double value) {
        if (!Double.isFinite(value)) return MIN_DURATION_MS;
        return Math.max(MIN_DURATION_MS, value);
    }

    private static double sanitizeNonNegativeFinite(double value, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(0.0, value);
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0.5;
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private static double mirrorFactor(double mirrorX) {
        if (!Double.isFinite(mirrorX)) return 1.0;
        return Math.cos(clamp01(mirrorX) * Math.PI);
    }

    private static double sanitizeFinite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double parserDefaultValue(PropertyType property) {
        return switch (property) {
            case SCALE_X, SCALE_Y, ALPHA, VISIBILITY, MATRIX_MXX, MATRIX_MYY, BRIGHTNESS -> 1.0;
            default -> 0.0;
        };
    }

    private static double clampTime(double value, double durationMs) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(durationMs, value));
    }
}
