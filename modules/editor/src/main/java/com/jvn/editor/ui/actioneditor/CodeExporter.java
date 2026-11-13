package com.jvn.editor.ui.actioneditor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;
import com.jvn.core.vn.VnEyeFocusProfile;

public class CodeExporter {
    private static final double TIME_QUANTIZATION_FACTOR = 1000.0; // 0.001ms

    public static String exportNamed(AnimationProject project, String name) {
        return exportNamed(project, name, false);
    }

    public static String exportNamed(AnimationProject project, String name, boolean exportNestedBlocks) {
        return exportNamed(project, name, exportNestedBlocks, false);
    }

    public static String exportNamed(AnimationProject project, String name, boolean exportNestedBlocks, boolean blocking) {
        String body = export(project, exportNestedBlocks);
        StringBuilder sb = new StringBuilder();
        sb.append("// Timeline: ").append(name).append("\n");
        sb.append("// Usage in VNS: @external jes_timeline ").append(name);
        if (blocking) {
            sb.append(" wait");
        }
        sb.append("\n\n");
        appendSceneEntityMetadata(sb, project);
        appendStageMetadata(sb, project);
        appendEditorProjectMetadata(sb, project);
        appendDialogueCueMetadata(sb, project);
        sb.append(body);
        return sb.toString();
    }

    /** Keeps reopen metadata inside the block so copying a VNS block preserves it. */
    public static String exportEditableInline(AnimationProject project, boolean nested) {
        StringBuilder metadata = new StringBuilder();
        appendSceneEntityMetadata(metadata, project);
        appendStageMetadata(metadata, project);
        appendEditorProjectMetadata(metadata, project);
        appendDialogueCueMetadata(metadata, project);
        String body = export(project, nested);
        int opening = body.indexOf('{');
        if (opening < 0) throw new IllegalStateException("Export has no timeline block.");
        return body.substring(0, opening + 1) + "\n" + metadata + body.substring(opening + 1);
    }

    private static void appendSceneEntityMetadata(StringBuilder sb, AnimationProject project) {
        if (sb == null || project == null || project.getSceneEntitySnapshotsView().isEmpty()) return;
        sb.append("// Puppeteer scene metadata. Runtime parsers ignore these comments.\n");
        for (AnimationProject.SceneEntitySnapshot entity : project.getSceneEntitySnapshotsView().values()) {
            if (entity == null || entity.name().isBlank()) continue;
            sb.append("// @jvn-puppeteer-entity")
                .append(" name=").append(encode(entity.name()))
                .append(" type=").append(encode(entity.type()))
                .append(" image=").append(encode(entity.imagePath()))
                .append(" x=").append(formatMetadataNumber(entity.x()))
                .append(" y=").append(formatMetadataNumber(entity.y()))
                .append(" rbx=").append(formatMetadataNumber(entity.runtimeBaseX()))
                .append(" rby=").append(formatMetadataNumber(entity.runtimeBaseY()))
                .append(" w=").append(formatMetadataNumber(entity.width()))
                .append(" h=").append(formatMetadataNumber(entity.height()))
                .append(" ox=").append(formatMetadataNumber(entity.originX()))
                .append(" oy=").append(formatMetadataNumber(entity.originY()))
                .append(" vminx=").append(formatMetadataNumber(entity.visualMinX()))
                .append(" vminy=").append(formatMetadataNumber(entity.visualMinY()))
                .append(" vmaxx=").append(formatMetadataNumber(entity.visualMaxX()))
                .append(" vmaxy=").append(formatMetadataNumber(entity.visualMaxY()))
                .append(" z=").append(formatMetadataNumber(entity.z()))
                .append(" visible=").append(entity.visible() ? "1" : "0")
                .append(" alpha=").append(formatMetadataNumber(entity.alpha()))
                .append("\n");
        }
        sb.append("\n");
    }

    private static void appendStageMetadata(StringBuilder sb, AnimationProject project) {
        if (sb == null || project == null) return;
        AnimationProject.StageContext stage = project.getStageContext();
        if (stage == null || !stage.isPresent()) return;
        sb.append("// Puppeteer stage metadata. Runtime parsers ignore these comments.\n");
        sb.append("// @jvn-puppeteer-stage")
            .append(" id=").append(encode(stage.presetId()))
            .append(" source=").append(encode(stage.sourcePath()))
            .append(" bg=").append(encode(stage.backgroundTag()))
            .append(" subject=").append(encode(stage.subjectTag()))
            .append(" lights=").append(Math.max(0, stage.lightCount()))
            .append(" occluders=").append(Math.max(0, stage.occluderCount()))
            .append(" zones=").append(Math.max(0, stage.responseZoneCount()))
            .append("\n\n");
    }

    private static void appendEditorProjectMetadata(StringBuilder sb, AnimationProject project) {
        if (sb == null || project == null) return;
        sb.append("// Puppeteer editor metadata. Runtime parsers ignore these comments.\n");
        sb.append("// @jvn-puppeteer-project")
            .append(" schema=2")
            .append(" duration=").append(formatMetadataNumber(project.getTotalDurationMs()))
            .append(" playhead=").append(formatMetadataNumber(project.getPlayheadMs()))
            .append(" looping=").append(project.isLooping() ? "1" : "0");
        if (project.hasLoopRegion()) {
            sb.append(" loopStart=").append(formatMetadataNumber(project.getLoopStartMs()))
                .append(" loopEnd=").append(formatMetadataNumber(project.getLoopEndMs()));
        }
        sb.append("\n");

        for (EntityGroup group : project.getGroups()) {
            if (group == null || group.getName() == null || group.getName().isBlank()) continue;
            sb.append("// @jvn-puppeteer-group")
                .append(" name=").append(encode(group.getName()))
                .append(" parent=").append(encode(group.getParentGroupName()))
                .append(" layer=").append(group.getLayerOrder())
                .append(" expanded=").append(group.isExpanded() ? "1" : "0")
                .append(" locked=").append(group.isLocked() ? "1" : "0")
                .append("\n");
        }

        for (EntityTrack track : project.getTracks()) {
            appendTrackMetadata(sb, track);
            appendTrackKeyframeMetadata(sb, track, false);
        }
        for (EntityGroup group : project.getGroups()) {
            if (group == null) continue;
            appendTrackKeyframeMetadata(sb, group.getGroupTrack(), true);
        }
        appendRiggingMetadata(sb, project);
        sb.append("\n");
    }

    private static void appendDialogueCueMetadata(StringBuilder sb, AnimationProject project) {
        if (sb == null || project == null) return;
        List<EditorEventCue> cues = project.getEditorEventCues().stream()
            .filter(cue -> cue != null && "dialogue_marker".equalsIgnoreCase(cue.getType()))
            .toList();
        if (cues.isEmpty()) return;

        sb.append("// Puppeteer dialogue cue metadata. Runtime parsers ignore these comments.\n");
        for (EditorEventCue cue : cues) {
            String speaker = cue.getPayloadValue("speaker");
            String text = cue.getPayloadValue("text");
            String id = cue.getPayloadValue("id");
            sb.append("// @jvn-puppeteer-cue")
                .append(" time=").append(formatMetadataNumber(cue.getTimeMs()))
                .append(" speaker=").append(encode(speaker))
                .append(" text=").append(encode(text));
            if (!id.isBlank()) {
                sb.append(" id=").append(encode(id));
            }
            sb.append("\n");
            String speakerForComment = sanitizeForInlineComment(speaker);
            String textForComment = sanitizeForInlineComment(text);
            sb.append("// cue: ");
            if (!speakerForComment.isBlank()) {
                sb.append(speakerForComment).append(" — ");
            }
            sb.append("\"").append(textForComment).append("\"\n");
        }
        sb.append("\n");
    }

    private static String sanitizeForInlineComment(String raw) {
        if (raw == null) return "";
        return raw.replace("\r\n", " ").replace("\n", " ").replace("\r", " ").replace("\"", "'").trim();
    }

    private static void appendTrackMetadata(StringBuilder sb, EntityTrack track) {
        if (sb == null || track == null || track.getEntityName() == null || track.getEntityName().isBlank()) return;
        sb.append("// @jvn-puppeteer-track")
            .append(" name=").append(encode(track.getEntityName()))
            .append(" parent=").append(encode(track.getParentGroupName()))
            .append(" visible=").append(track.isVisible() ? "1" : "0")
            .append(" expanded=").append(track.isExpanded() ? "1" : "0")
            .append(" locked=").append(track.isLocked() ? "1" : "0")
            .append(" layer=").append(track.getLayerOrder())
            .append("\n");
    }

    private static void appendRiggingMetadata(StringBuilder sb, AnimationProject project) {
        if (sb == null || project == null) return;
        for (Map.Entry<String, double[]> entry : project.getOrbitAnchorsView().entrySet()) {
            String target = entry.getKey();
            double[] anchor = entry.getValue();
            if (target == null || target.isBlank() || anchor == null || anchor.length < 2) continue;
            sb.append("// @jvn-puppeteer-orbit")
                .append(" target=").append(encode(target))
                .append(" x=").append(formatMetadataNumber(anchor[0]))
                .append(" y=").append(formatMetadataNumber(anchor[1]));
            String source = project.getOrbitAnchorSourcesView().get(target);
            if (source != null && !source.isBlank()) {
                sb.append(" source=").append(encode(source));
                double[] offset = project.getOrbitAnchorSourceOffsetsView().get(target);
                if (offset != null && offset.length >= 2) {
                    sb.append(" offsetX=").append(formatMetadataNumber(offset[0]))
                        .append(" offsetY=").append(formatMetadataNumber(offset[1]));
                }
            }
            sb.append("\n");
        }

        for (Map.Entry<String, Constraint> entry : project.getConstraintsView().entrySet()) {
            String target = entry.getKey();
            Constraint constraint = entry.getValue();
            if (target == null || target.isBlank() || constraint == null || constraint.getType() == null) continue;
            sb.append("// @jvn-puppeteer-constraint")
                .append(" target=").append(encode(target))
                .append(" type=").append(encode(constraint.getType().name()))
                .append(" source=").append(encode(constraint.getTargetEntityName()))
                .append(" offsetX=").append(formatMetadataNumber(constraint.getOffsetX()))
                .append(" offsetY=").append(formatMetadataNumber(constraint.getOffsetY()))
                .append(" inheritRot=").append(constraint.isInheritRotation() ? "1" : "0")
                .append(" inheritScale=").append(constraint.isInheritScale() ? "1" : "0")
                .append("\n");
        }

        for (Map.Entry<String, Map<String, Anchor>> entityEntry : project.getEntityAnchorsView().entrySet()) {
            String entity = entityEntry.getKey();
            Map<String, Anchor> anchors = entityEntry.getValue();
            if (entity == null || entity.isBlank() || anchors == null || anchors.isEmpty()) continue;
            for (Anchor anchor : anchors.values()) {
                if (anchor == null || anchor.getName() == null || anchor.getName().isBlank()) continue;
                sb.append("// @jvn-puppeteer-anchor")
                    .append(" entity=").append(encode(entity))
                    .append(" name=").append(encode(anchor.getName()))
                    .append(" x=").append(formatMetadataNumber(anchor.getX()))
                    .append(" y=").append(formatMetadataNumber(anchor.getY()))
                    .append(" relative=").append(anchor.isRelative() ? "1" : "0")
                    .append("\n");
            }
        }

        for (VnEyeFocusProfile profile : project.getEyeFocusProfilesView().values()) {
            if (profile == null || profile.characterId().isBlank()) continue;
            sb.append("// @jvn-puppeteer-eye-focus")
                .append(" character=").append(encode(profile.characterId()))
                .append(" expression=").append(encode(profile.expression()))
                .append(" sourceAnchor=").append(encode(profile.sourceAnchor()))
                .append(" sourceX=").append(formatMetadataNumber(profile.sourceX()))
                .append(" sourceY=").append(formatMetadataNumber(profile.sourceY()))
                .append(" deadZone=").append(formatMetadataNumber(profile.deadZone()))
                .append(" maxNudge=").append(formatMetadataNumber(profile.maxNudgePx()))
                .append(" strength=").append(formatMetadataNumber(profile.strength()));
            for (int keypad = 1; keypad <= 9; keypad++) {
                String layerId = profile.layerIdFor(keypad);
                if (layerId != null && !layerId.isBlank()) {
                    sb.append(" layer").append(keypad).append("=").append(encode(layerId));
                }
            }
            sb.append("\n");
        }
    }

    private static void appendTrackKeyframeMetadata(StringBuilder sb, EntityTrack track, boolean groupTrack) {
        if (sb == null || track == null || track.getEntityName() == null || track.getEntityName().isBlank()) return;
        String target = track.getEntityName();
        String kind = groupTrack ? "group" : "entity";
        for (PropertyType property : PropertyType.values()) {
            for (Keyframe keyframe : track.getKeyframes(property)) {
                appendKeyframeMetadata(sb, target, kind, property, null, keyframe);
            }
        }
        for (String customKey : track.getAnimatedCustomProperties()) {
            if (customKey == null || customKey.isBlank()) continue;
            for (Keyframe keyframe : track.getCustomKeyframes(customKey)) {
                appendKeyframeMetadata(sb, target, kind, null, customKey, keyframe);
            }
        }
    }

    private static void appendKeyframeMetadata(
        StringBuilder sb,
        String target,
        String kind,
        PropertyType property,
        String customKey,
        Keyframe keyframe
    ) {
        if (sb == null || target == null || target.isBlank() || keyframe == null) return;
        boolean custom = customKey != null && !customKey.isBlank();
        sb.append(custom ? "// @jvn-puppeteer-custom-key" : "// @jvn-puppeteer-key")
            .append(" target=").append(encode(target))
            .append(" kind=").append(encode(kind))
            .append(custom
                ? " key=" + encode(customKey)
                : " prop=" + encode(property != null ? property.getCode() : ""))
            .append(" time=").append(formatMetadataNumber(keyframe.getTimeMs()))
            .append(" value=").append(formatMetadataNumber(keyframe.getValue()))
            .append(" easing=").append(encode(keyframe.getEasingSpec().toDslString()))
            .append(" interp=").append(encode(keyframe.getInterpolation().name().toLowerCase()))
            .append("\n");
    }

    private static String encode(String raw) {
        return URLEncoder.encode(raw != null ? raw : "", StandardCharsets.UTF_8);
    }

    private static List<Double> collectUniqueTimes(List<Keyframe> list1, List<Keyframe> list2) {
        Map<Long, Double> timeMap = new TreeMap<>();
        for (Keyframe kf : list1) {
            long key = Math.round(kf.getTimeMs() * TIME_QUANTIZATION_FACTOR);
            timeMap.putIfAbsent(key, kf.getTimeMs());
        }
        if (list2 != null) {
            for (Keyframe kf : list2) {
                long key = Math.round(kf.getTimeMs() * TIME_QUANTIZATION_FACTOR);
                timeMap.putIfAbsent(key, kf.getTimeMs());
            }
        }
        return new ArrayList<>(timeMap.values());
    }

    public static String export(AnimationProject project) {
        return export(project, false);
    }

    public static String export(AnimationProject project, boolean exportNestedBlocks) {
        if (project == null) return "timeline {\n}\n";

        StringBuilder sb = new StringBuilder();
        sb.append("timeline {\n");

        List<TimelineEvent> allEvents = collectEvents(project);
        
        if (exportNestedBlocks) {
            // Group events by target
            Map<String, List<TimelineEvent>> eventsByTarget = new LinkedHashMap<>();
            for (TimelineEvent ev : allEvents) {
                eventsByTarget.computeIfAbsent(ev.target, k -> new ArrayList<>()).add(ev);
            }
            
            // Build root elements
            Set<String> processedTargets = new java.util.HashSet<>();
            for (String groupName : project.getRootGroupNames()) {
                exportGroupRecursive(sb, project, groupName, "  ", eventsByTarget, processedTargets);
            }
            
            // Export any unparented targets
            List<TimelineEvent> unparentedEvents = new ArrayList<>();
            for (Map.Entry<String, List<TimelineEvent>> entry : eventsByTarget.entrySet()) {
                if (!processedTargets.contains(entry.getKey())) {
                    unparentedEvents.addAll(entry.getValue());
                }
            }
            if (!unparentedEvents.isEmpty()) {
                appendScheduledEvents(sb, unparentedEvents, project.getTotalDurationMs(), true);
            }
        } else {
            appendScheduledEvents(sb, allEvents, project.getTotalDurationMs(), true);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static void exportGroupRecursive(StringBuilder sb, AnimationProject project, 
                                              String groupName, String indent,
                                              Map<String, List<TimelineEvent>> eventsByTarget,
                                              Set<String> processedTargets) {
        EntityGroup group = project.getGroup(groupName);
        if (group == null) return;
        
        processedTargets.add(groupName);
        
        sb.append(indent).append("[group ").append(groupName).append("]\n");
        
        List<TimelineEvent> localEvents = eventsByTarget.getOrDefault(groupName, List.of());
        if (!localEvents.isEmpty()) {
            appendScheduledEventsWithIndent(sb, localEvents, project.getTotalDurationMs(), true, indent + "  ");
        }

        for (String childGroup : group.getChildGroupNames()) {
            exportGroupRecursive(sb, project, childGroup, indent + "  ", eventsByTarget, processedTargets);
        }
        
        // Export character entities that belong to this group
        for (EntityTrack track : project.getTracks()) {
            if (groupName.equals(track.getParentGroupName())) {
                String targetId = track.getEntityName();
                if (!processedTargets.contains(targetId)) {
                    processedTargets.add(targetId);
                    List<TimelineEvent> charEvents = eventsByTarget.getOrDefault(targetId, List.of());
                    if (!charEvents.isEmpty()) {
                        sb.append(indent).append("  [character ").append(targetId).append("]\n");
                        appendScheduledEventsWithIndent(sb, charEvents, project.getTotalDurationMs(), true, indent + "    ");
                        sb.append(indent).append("  [/character]\n");
                    }
                }
            }
        }
        
        sb.append(indent).append("[/group]\n");
    }

    private static List<TimelineEvent> collectEvents(AnimationProject project) {
        List<TimelineEvent> events = new ArrayList<>();
        EntityTrack cameraTrack = null;

        // First pass: find entities whose orbit anchor should be emitted as a pivot
        // command rather than exporting the arc X/Y positions. This produces a clean
        // pivot+rotate that VnRenderer can apply without the entity visually drifting.
        Set<String> orbitPivotEntities = new java.util.HashSet<>();
        if (project != null) {
            for (EntityTrack track : project.getTracks()) {
                String entity = track.getEntityName();
                if (!project.hasOrbitAnchor(entity)) continue;
                if (!project.hasEffectiveAnimation(entity, PropertyType.ROTATION)) continue;
                if (project.isOrbitPivotAtRisk(entity)) continue;
                orbitPivotEntities.add(entity);
            }
        }

        for (EntityTrack track : project.getTracks()) {
            String entity = track.getEntityName();

            emitSnapshotPivotSeedEvent(events, project, entity, track, orbitPivotEntities.contains(entity));
            if (orbitPivotEntities.contains(entity)) {
                emitOrbitPivotEvent(events, project, entity);
            } else {
                collectPropertyEvents(events, project, entity, track, PropertyType.X, PropertyType.Y, "move", true);
            }
            collectPropertyEvents(events, project, entity, track, PropertyType.Z, null, "depth", true);
            collectPropertyEvents(events, project, entity, track, PropertyType.PIVOT_X, PropertyType.PIVOT_Y, "pivot", true);
            collectPropertyEvents(events, project, entity, track, PropertyType.ROTATION, null, "rotate", true);
            collectPropertyEvents(events, project, entity, track, PropertyType.SCALE_X, PropertyType.SCALE_Y, "scale", true);
            collectPropertyEvents(events, project, entity, track, PropertyType.MIRROR_X, null, "mirror", true);
            collectPropertyEvents(events, project, entity, track, PropertyType.ALPHA, null, "fade", true);
            collectVisibilityEvents(events, project, entity, track);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.MATRIX_MXX);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.MATRIX_MXY);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.MATRIX_MYX);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.MATRIX_MYY);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.MATRIX_TX);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.MATRIX_TY);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.BLUR);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.BRIGHTNESS);
            collectCustomPropertyEvents(events, entity, track);

            if (cameraTrack == null &&
                (track.hasKeyframes(PropertyType.CAMERA_X)
                    || track.hasKeyframes(PropertyType.CAMERA_Y)
                    || track.hasKeyframes(PropertyType.CAMERA_ZOOM)
                    || track.hasKeyframes(PropertyType.CAMERA_DOF_FOCUS)
                    || track.hasKeyframes(PropertyType.CAMERA_DOF_STRENGTH)
                    || track.hasKeyframes(PropertyType.CAMERA_DOF_MAX_BLUR)
                    || (TimelinePanel.RUNTIME_CAMERA_TARGET.equals(entity) && hasAnyCustomKeys(track)))) {
                cameraTrack = track;
            }
        }

        if (cameraTrack != null) {
            collectPropertyEvents(events, null, "__camera__", cameraTrack, PropertyType.CAMERA_X, PropertyType.CAMERA_Y, "cameraMove", false);
            collectPropertyEvents(events, null, "__camera__", cameraTrack, PropertyType.CAMERA_ZOOM, null, "cameraZoom", false);
            collectTimelineCustomPropertyEvents(events, "__camera__", cameraTrack, PropertyType.CAMERA_DOF_FOCUS);
            collectTimelineCustomPropertyEvents(events, "__camera__", cameraTrack, PropertyType.CAMERA_DOF_STRENGTH);
            collectTimelineCustomPropertyEvents(events, "__camera__", cameraTrack, PropertyType.CAMERA_DOF_MAX_BLUR);
            collectCustomPropertyEvents(events, "__camera__", cameraTrack);
        }

        for (AudioCue cue : project.getAudioCues()) {
            if (cue == null || cue.getAudioFile() == null || cue.getAudioFile().isBlank()) continue;
            TimelineEvent ev = new TimelineEvent();
            ev.actionType = "playAudio";
            ev.target = cue.getAudioFile().trim();
            ev.startTime = Math.max(0, cue.getTimeMs());
            ev.duration = 0;
            ev.props.put("volume", cue.getVolume());
            boolean isMusic = "music".equalsIgnoreCase(cue.getChannel());
            ev.props.put("loop", isMusic);
            ev.props.put("bgm", isMusic);
            events.add(ev);
        }

        for (EditorEventCue evt : project.getEditorEventCues()) {
            if (evt == null || evt.getType().isBlank()) continue;
            if ("dialogue_marker".equalsIgnoreCase(evt.getType())) continue;
            TimelineEvent ev = new TimelineEvent();
            ev.actionType = mapActionTypeForEventCue(evt);
            ev.target = resolveActionTarget(evt, ev.actionType);
            ev.startTime = Math.max(0, evt.getTimeMs());
            ev.duration = 0;
            for (Map.Entry<String, String> entry : evt.getPayloadView().entrySet()) {
                if (!"target".equals(entry.getKey()) || "event".equals(ev.actionType)) {
                    ev.props.put(entry.getKey(), entry.getValue());
                }
            }
            if ("event".equals(ev.actionType)) {
                ev.target = evt.getType();
            }
            events.add(ev);
        }

        return events;
    }

    /**
     * VN timeline layer proxies default to a 0,0 origin, while Puppeteer scene
     * snapshots often represent character layers with a center/bottom origin.
     * Seed any missing pivot axis before pivot-sensitive transforms so pasted
     * VNS code mirrors/rotates/scales around the same point as the preview.
     */
    private static void emitSnapshotPivotSeedEvent(
        List<TimelineEvent> events,
        AnimationProject project,
        String target,
        EntityTrack track,
        boolean hasOrbitPivot
    ) {
        if (events == null || project == null || target == null || target.isBlank() || track == null) return;
        AnimationProject.SceneEntitySnapshot snap = project.getSceneEntitySnapshotsView().get(target);
        if (snap == null) return;

        boolean hasScaleOrMirror =
            project.hasEffectiveAnimation(target, PropertyType.SCALE_X)
                || project.hasEffectiveAnimation(target, PropertyType.SCALE_Y)
                || project.hasEffectiveAnimation(target, PropertyType.MIRROR_X);
        boolean hasRotation = project.hasEffectiveAnimation(target, PropertyType.ROTATION);
        if (!hasScaleOrMirror && (!hasRotation || hasOrbitPivot)) return;

        boolean needsX = !track.hasKeyframes(PropertyType.PIVOT_X)
            && Math.abs(snap.originX()) > 0.001;
        boolean needsY = !track.hasKeyframes(PropertyType.PIVOT_Y)
            && Math.abs(snap.originY()) > 0.001;
        if (!needsX && !needsY) return;

        TimelineEvent ev = new TimelineEvent();
        ev.actionType = "pivot";
        ev.target = target;
        ev.startTime = 0.0;
        ev.duration = 0.0;
        if (needsX) ev.props.put("ox", snap.originX());
        if (needsY) ev.props.put("oy", snap.originY());
        events.add(ev);
    }

    private static String mapActionTypeForEventCue(EditorEventCue cue) {
        if (cue == null || cue.getType() == null) return "event";
        String normalized = cue.getType().trim().toLowerCase();
        if ("scene".equals(normalized)) return cue.getType().trim();
        if (("expression".equals(normalized)
            || "show".equals(normalized)
            || "hide".equals(normalized)
            || "replace".equals(normalized))
            && !cue.getPayloadValue("target").isBlank()) {
            return cue.getType().trim();
        }
        return "event";
    }

    private static String resolveActionTarget(EditorEventCue cue, String actionType) {
        if (cue == null || actionType == null) return "";
        if ("scene".equalsIgnoreCase(actionType)) {
            return cue.getPayloadValue("target");
        }
        if ("event".equalsIgnoreCase(actionType)) {
            return cue.getType();
        }
        return cue.getPayloadValue("target");
    }

    private static void collectPropertyEvents(
        List<TimelineEvent> events,
        AnimationProject project,
        String target,
        EntityTrack track,
        PropertyType p1,
        PropertyType p2,
        String actionType,
        boolean effective
    ) {
        if (track == null) return;
        List<Keyframe> list1 = effective && project != null ? project.getEffectiveKeyframes(target, p1) : track.getKeyframes(p1);
        List<Keyframe> list2 = p2 != null
            ? (effective && project != null ? project.getEffectiveKeyframes(target, p2) : track.getKeyframes(p2))
            : null;
        boolean hasP1 = !list1.isEmpty();
        boolean hasP2 = p2 != null && list2 != null && !list2.isEmpty();

        if (!hasP1 && !hasP2) return;

        List<Double> times = collectUniqueTimes(list1, list2);
        emitInitialPropertyEvent(events, project, target, track, p1, p2, actionType, effective, hasP1, hasP2);

        for (int i = 0; i < times.size() - 1; i++) {
            double startTime = times.get(i);
            double endTime = times.get(i + 1);
            double duration = endTime - startTime;

            double startVal1 = effective && project != null
                ? project.computeValueAt(target, p1, startTime, p1.getDefaultValue())
                : track.getValueAt(p1, startTime);
            double endVal1 = effective && project != null
                ? project.computeValueAt(target, p1, endTime, p1.getDefaultValue())
                : track.getValueAt(p1, endTime);
            double startVal2 = p2 != null
                ? (effective && project != null
                    ? project.computeValueAt(target, p2, startTime, p2.getDefaultValue())
                    : track.getValueAt(p2, startTime))
                : 0;
            double endVal2 = p2 != null
                ? (effective && project != null
                    ? project.computeValueAt(target, p2, endTime, p2.getDefaultValue())
                    : track.getValueAt(p2, endTime))
                : 0;

            EasingSpec easingSpec = findEasingSpecAt(list1, endTime);
            if (easingSpec == null && list2 != null) easingSpec = findEasingSpecAt(list2, endTime);
            if (easingSpec == null) easingSpec = EasingSpec.of(Easing.Type.LINEAR);

            boolean changed = (hasP1 && Math.abs(endVal1 - startVal1) > 0.001) ||
                             (hasP2 && Math.abs(endVal2 - startVal2) > 0.001);
            if (!changed) continue;

            Keyframe endKf = findKeyframeAt(list1, endTime);
            if (endKf == null && list2 != null) endKf = findKeyframeAt(list2, endTime);

            TimelineEvent ev = new TimelineEvent();
            ev.actionType = actionType;
            ev.target = target;
            ev.startTime = startTime;
            ev.duration = duration;
            ev.easingSpec = easingSpec;
            ev.interpolation = endKf != null
                ? endKf.getInterpolation()
                : Easing.Interpolation.TWEEN;

            putPropertyEventProps(ev, project, target, actionType, hasP1, hasP2, endVal1, endVal2);

            events.add(ev);
        }
    }

    private static void emitInitialPropertyEvent(
        List<TimelineEvent> events,
        AnimationProject project,
        String target,
        EntityTrack track,
        PropertyType p1,
        PropertyType p2,
        String actionType,
        boolean effective,
        boolean hasP1,
        boolean hasP2
    ) {
        if (events == null || track == null || actionType == null) return;
        double value1 = hasP1
            ? (effective && project != null
                ? project.computeValueAt(target, p1, 0.0, p1.getDefaultValue())
                : track.getValueAt(p1, 0.0))
            : 0.0;
        double value2 = hasP2 && p2 != null
            ? (effective && project != null
                ? project.computeValueAt(target, p2, 0.0, p2.getDefaultValue())
                : track.getValueAt(p2, 0.0))
            : 0.0;

        TimelineEvent ev = new TimelineEvent();
        ev.actionType = actionType;
        ev.target = target;
        ev.startTime = 0.0;
        ev.duration = 0.0;
        putPropertyEventProps(ev, project, target, actionType, hasP1, hasP2, value1, value2);
        ev.props.entrySet().removeIf(entry ->
            entry.getValue() instanceof Number n
                && Math.abs(n.doubleValue() - defaultActionPropValue(actionType, entry.getKey())) <= 0.001);
        if (!ev.props.isEmpty()) {
            events.add(ev);
        }
    }

    private static void putPropertyEventProps(
        TimelineEvent ev,
        AnimationProject project,
        String target,
        String actionType,
        boolean hasP1,
        boolean hasP2,
        double value1,
        double value2
    ) {
        if (ev == null || actionType == null) return;
        switch (actionType) {
            case "move" -> {
                AnimationProject.SceneEntitySnapshot snap = project != null
                    ? project.getSceneEntitySnapshotsView().get(target) : null;
                double ox = snap != null ? snap.runtimeBaseX() : 0.0;
                double oy = snap != null ? snap.runtimeBaseY() : 0.0;
                if (hasP1) ev.props.put("x", value1 - ox);
                if (hasP2) ev.props.put("y", value2 - oy);
            }
            case "depth" -> {
                if (hasP1) ev.props.put("z", value1);
            }
            case "pivot" -> {
                if (hasP1) ev.props.put("ox", value1);
                if (hasP2) ev.props.put("oy", value2);
            }
            case "rotate" -> {
                if (hasP1) ev.props.put("deg", value1);
            }
            case "scale" -> {
                if (hasP1) ev.props.put("sx", value1);
                if (hasP2) ev.props.put("sy", value2);
            }
            case "mirror" -> {
                if (hasP1) ev.props.put("mirrorX", value1);
            }
            case "fade" -> {
                if (hasP1) ev.props.put("alpha", value1);
            }
            case "cameraMove" -> {
                if (hasP1) ev.props.put("x", value1);
                if (hasP2) ev.props.put("y", value2);
            }
            case "cameraZoom" -> {
                if (hasP1) ev.props.put("zoom", value1);
            }
        }
    }

    private static double defaultActionPropValue(String actionType, String propKey) {
        if ("scale".equals(actionType) && ("sx".equals(propKey) || "sy".equals(propKey))) return 1.0;
        if ("fade".equals(actionType) && "alpha".equals(propKey)) return 1.0;
        if ("cameraZoom".equals(actionType) && "zoom".equals(propKey)) return 1.0;
        return 0.0;
    }

    private static void collectVisibilityEvents(
        List<TimelineEvent> events,
        AnimationProject project,
        String target,
        EntityTrack track
    ) {
        if (track == null) return;
        List<Keyframe> keyframes = project != null
            ? project.getEffectiveKeyframes(target, PropertyType.VISIBILITY)
            : track.getKeyframes(PropertyType.VISIBILITY);
        if (keyframes.isEmpty()) return;

        double previous = keyframes.get(0).getValue();
        boolean hasLaterChange = false;
        for (int i = 1; i < keyframes.size(); i++) {
            Keyframe keyframe = keyframes.get(i);
            if (keyframe != null && Math.abs(keyframe.getValue() - previous) > 0.001) {
                hasLaterChange = true;
                break;
            }
        }

        if (hasLaterChange || Math.abs(previous - PropertyType.VISIBILITY.getDefaultValue()) > 0.001) {
            TimelineEvent initial = new TimelineEvent();
            initial.actionType = "visible";
            initial.target = target;
            initial.startTime = 0.0;
            initial.props.put("value", previous >= 0.5);
            events.add(initial);
        }

        for (int i = 1; i < keyframes.size(); i++) {
            Keyframe keyframe = keyframes.get(i);
            if (keyframe == null) continue;
            double current = keyframe.getValue();
            if (Math.abs(current - previous) <= 0.001) continue;
            TimelineEvent ev = new TimelineEvent();
            ev.actionType = "visible";
            ev.target = target;
            ev.startTime = Math.max(0.0, keyframe.getTimeMs());
            ev.easingSpec = keyframe.getEasingSpec();
            ev.interpolation = keyframe.getInterpolation();
            ev.props.put("value", current >= 0.5);
            events.add(ev);
            previous = current;
        }
    }

    private static void collectTimelineCustomPropertyEvents(
        List<TimelineEvent> events,
        String target,
        EntityTrack track,
        PropertyType property
    ) {
        if (track == null || property == null || !property.isTimelineCustomProperty()) return;
        List<Keyframe> keyframes = track.getKeyframes(property);
        if (keyframes.isEmpty()) {
            collectCustomPropertyEvents(events, target, track, property.getTimelineCustomKey(), property.getDefaultValue());
            return;
        }
        collectCustomKeyframeEvents(
            events,
            target,
            property.getTimelineCustomKey(),
            keyframes,
            property.getDefaultValue()
        );
    }

    private static void collectCustomPropertyEvents(List<TimelineEvent> events, String target, EntityTrack track) {
        if (track == null) return;
        for (String propertyKey : track.getAnimatedCustomProperties()) {
            if (propertyKey == null || propertyKey.isBlank()) continue;
            collectCustomPropertyEvents(events, target, track, propertyKey, defaultCustomPropertyValue(target, propertyKey));
        }
    }

    private static void collectCustomPropertyEvents(
        List<TimelineEvent> events,
        String target,
        EntityTrack track,
        String propertyKey,
        double defaultValue
    ) {
        if (track == null || propertyKey == null || propertyKey.isBlank()) return;
        List<Keyframe> keyframes = track.getCustomKeyframes(propertyKey);
        if (keyframes.isEmpty()) return;
        collectCustomKeyframeEvents(events, target, propertyKey, keyframes, defaultValue);
    }

    private static void collectCustomKeyframeEvents(
        List<TimelineEvent> events,
        String target,
        String propertyKey,
        List<Keyframe> keyframes,
        double defaultValue
    ) {
        if (events == null || propertyKey == null || propertyKey.isBlank() || keyframes == null || keyframes.isEmpty()) return;
        String normalizedKey = propertyKey.trim();
        Keyframe first = keyframes.get(0);
        if (first == null) return;
        if (Math.abs(first.getValue() - defaultValue) > 0.001) {
            TimelineEvent ev = new TimelineEvent();
            ev.actionType = actionTypeForCustomProperty(normalizedKey);
            ev.target = target;
            ev.startTime = 0.0;
            if ("property".equals(ev.actionType)) {
                ev.props.put("key", normalizedKey);
            }
            ev.props.put("value", first.getValue());
            events.add(ev);
        }

        for (int i = 0; i + 1 < keyframes.size(); i++) {
            Keyframe start = keyframes.get(i);
            Keyframe end = keyframes.get(i + 1);
            if (start == null || end == null) continue;
            double duration = end.getTimeMs() - start.getTimeMs();
            if (duration < 0.0) continue;
            if (Math.abs(end.getValue() - start.getValue()) <= 0.001) continue;

            TimelineEvent ev = new TimelineEvent();
            ev.actionType = actionTypeForCustomProperty(normalizedKey);
            ev.target = target;
            ev.startTime = Math.max(0.0, start.getTimeMs());
            ev.duration = Math.max(0.0, duration);
            ev.easingSpec = end.getEasingSpec();
            ev.interpolation = end.getInterpolation();
            if ("property".equals(ev.actionType)) {
                ev.props.put("key", normalizedKey);
            }
            ev.props.put("value", end.getValue());
            events.add(ev);
        }
    }

    /**
     * For an entity whose rotation is orbit-anchored, emit a single instant {@code pivot}
     * event instead of arc X/Y moves.  The pivot fractions are derived from the orbit
     * anchor world position and the entity snapshot dimensions so that VnRenderer rotates
     * the sprite around the intended anchor point rather than around the sprite's top-left.
     */
    private static void emitOrbitPivotEvent(
            List<TimelineEvent> events,
            AnimationProject project,
            String entity) {
        double[] anchor = project.getOrbitAnchorsView().get(entity);
        AnimationProject.SceneEntitySnapshot snap = project.getSceneEntitySnapshotsView().get(entity);
        if (anchor == null || snap == null || snap.width() <= 0 || snap.height() <= 0) return;

        // Convert the Puppeteer-absolute anchor world position to a fraction of the
        // sprite's draw dimensions, using the snapshot's own origin as the base.
        double pivotOx = (anchor[0] - snap.x()) / snap.width() + snap.originX();
        double pivotOy = (anchor[1] - snap.y()) / snap.height() + snap.originY();

        // Place this at the start of the first rotation interval so it lands in the
        // same parallel block as the rotate command.
        List<Keyframe> rotKfs = project.getEffectiveKeyframes(entity, PropertyType.ROTATION);
        double startTime = 0.0;
        for (int i = 0; i + 1 < rotKfs.size(); i++) {
            double sv = rotKfs.get(i).getValue();
            double ev2 = rotKfs.get(i + 1).getValue();
            if (Math.abs(ev2 - sv) > 0.001) {
                startTime = rotKfs.get(i).getTimeMs();
                break;
            }
        }

        TimelineEvent ev = new TimelineEvent();
        ev.actionType = "pivot";
        ev.target = entity;
        ev.startTime = startTime;
        ev.duration = 0.0;
        ev.props.put("ox", pivotOx);
        ev.props.put("oy", pivotOy);
        events.add(ev);
    }

    private static String actionTypeForCustomProperty(String propertyKey) {
        if ("effect.brightness".equals(propertyKey) || "effect.exposure".equals(propertyKey)) {
            return "brightness";
        }
        return "property";
    }

    private static EasingSpec findEasingSpecAt(List<Keyframe> list, double time) {
        for (Keyframe kf : list) {
            if (Math.abs(kf.getTimeMs() - time) < 0.5) return kf.getEasingSpec();
        }
        return null;
    }

    private static Keyframe findKeyframeAt(List<Keyframe> list, double time) {
        for (Keyframe kf : list) {
            if (Math.abs(kf.getTimeMs() - time) < 0.5) return kf;
        }
        return null;
    }

    private static void appendScheduledEvents(
        StringBuilder sb,
        List<TimelineEvent> events,
        double totalDurationMs,
        boolean groupByStartTime
    ) {
        appendScheduledEventsWithIndent(sb, events, totalDurationMs, groupByStartTime, "  ");
    }

    private static void appendScheduledEventsWithIndent(
        StringBuilder sb,
        List<TimelineEvent> events,
        double totalDurationMs,
        boolean groupByStartTime,
        String indent
    ) {
        if (sb == null) return;
        List<TimelineEvent> safeEvents = events == null ? List.of() : new ArrayList<>(events);
        safeEvents.sort(Comparator.comparingDouble(e -> e.startTime));

        Map<Double, List<TimelineEvent>> byTime = new TreeMap<>();
        for (TimelineEvent e : safeEvents) {
            double rounded = quantizeTime(e.startTime);
            byTime.computeIfAbsent(rounded, k -> new ArrayList<>()).add(e);
        }

        double currentTime = 0.0;
        for (Map.Entry<Double, List<TimelineEvent>> entry : byTime.entrySet()) {
            double time = entry.getKey();
            List<TimelineEvent> group = entry.getValue();

            if (time > currentTime + 0.0005) {
                sb.append(indent).append("wait ").append(formatNumber(time - currentTime)).append("\n");
            }

            if (!groupByStartTime || group.size() == 1) {
                for (TimelineEvent ev : group) {
                    sb.append(formatEvent(ev, indent));
                }
            } else {
                sb.append(indent).append("parallel {\n");
                for (TimelineEvent ev : group) {
                    sb.append(formatEvent(ev, indent + "  "));
                }
                sb.append(indent).append("}\n");
            }
            currentTime = time;
        }

        double duration = Double.isFinite(totalDurationMs) ? Math.max(0.0, totalDurationMs) : 0.0;
        if (duration > currentTime + 0.0005) {
            sb.append(indent).append("wait ").append(formatNumber(duration - currentTime)).append("\n");
        }
    }

    private static double quantizeTime(double timeMs) {
        if (!Double.isFinite(timeMs)) return 0.0;
        return Math.round(Math.max(0.0, timeMs) * TIME_QUANTIZATION_FACTOR) / TIME_QUANTIZATION_FACTOR;
    }

    private static String formatEvent(TimelineEvent ev, String indent) {
        StringBuilder sb = new StringBuilder();
        if ("cameraMove".equals(ev.actionType) || "cameraZoom".equals(ev.actionType)) {
            sb.append(indent).append(ev.actionType).append(" {\n");
        } else if ("scene".equals(ev.actionType) && (ev.target == null || ev.target.isBlank())) {
            sb.append(indent).append("scene {\n");
        } else if ("event".equals(ev.actionType)) {
            sb.append(indent).append("event \"").append(ev.target).append("\" {\n");
        } else if ("property".equals(ev.actionType) && "__camera__".equals(ev.target)) {
            sb.append(indent).append("property {\n");
        } else {
            sb.append(indent).append(ev.actionType).append(" \"").append(ev.target).append("\" {\n");
        }

        for (Map.Entry<String, Object> entry : ev.props.entrySet()) {
            sb.append(indent).append("  ").append(entry.getKey()).append(": ")
              .append(formatPropValue(entry.getValue())).append("\n");
        }

        if (ev.duration > 0.0 && !"playAudio".equals(ev.actionType)) {
            sb.append(indent).append("  dur: ").append(formatNumber(ev.duration)).append("\n");
        }

        if (ev.interpolation != Easing.Interpolation.TWEEN) {
            sb.append(indent).append("  interp: ").append(ev.interpolation.name().toLowerCase()).append("\n");
        }

        if (ev.interpolation == Easing.Interpolation.TWEEN
            && ev.easingSpec != null
            && ev.easingSpec.getType() != Easing.Type.LINEAR) {
            sb.append(indent).append("  easing: ").append(ev.easingSpec.toDslString()).append("\n");
        }

        sb.append(indent).append("}\n");
        return sb.toString();
    }

    private static String formatNumber(double v) {
        if (!Double.isFinite(v)) return "0";
        if (Math.abs(v - Math.round(v)) < 0.0001) {
            return Long.toString(Math.round(v));
        }
        return String.format(Locale.ROOT, "%.6f", v)
            .replaceAll("0+$", "")
            .replaceAll("\\.$", "");
    }

    private static String formatMetadataNumber(double v) {
        if (!Double.isFinite(v)) return "0";
        if (Math.abs(v - Math.round(v)) < 0.000001) {
            return Long.toString(Math.round(v));
        }
        return String.format(Locale.ROOT, "%.6f", v)
            .replaceAll("0+$", "")
            .replaceAll("\\.$", "");
    }

    private static String formatPropValue(Object value) {
        if (value instanceof Number n) return formatNumber(n.doubleValue());
        if (value instanceof Boolean b) return b ? "true" : "false";
        if (value == null) return "\"\"";
        String text = value.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + text + "\"";
    }

    public static String exportIncremental(AnimationProject project) {
        if (project == null) return "timeline {\n}\n";

        Map<String, Map<PropertyType, Double>> snapshot = project.getInitialSnapshot();
        if (snapshot == null) return export(project);

        StringBuilder sb = new StringBuilder();
        sb.append("timeline {\n");

        List<TimelineEvent> events = collectEvents(project);
        events.sort(Comparator.comparingDouble(e -> e.startTime));

        List<TimelineEvent> filtered = new ArrayList<>();
        for (TimelineEvent ev : events) {
            boolean changed = false;
            Map<PropertyType, Double> initial = snapshot.get(ev.target);
            if (initial == null) { changed = true; }
            else {
                for (Map.Entry<String, Object> prop : ev.props.entrySet()) {
                    PropertyType pt = findPropertyByCode(prop.getKey());
                    if (pt != null && prop.getValue() instanceof Number n) {
                        Double initVal = initial.get(pt);
                        if (initVal == null || Math.abs(initVal - n.doubleValue()) > 0.001) {
                            changed = true;
                            break;
                        }
                    } else {
                        changed = true;
                        break;
                    }
                }
            }
            if (changed) filtered.add(ev);
        }

        appendScheduledEvents(sb, filtered, project.getTotalDurationMs(), true);

        sb.append("}\n");
        return sb.toString();
    }

    public static String exportAudioCues(AnimationProject project) {
        if (project == null) return "";
        List<AudioCue> cues = project.getAudioCues();
        if (cues.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (AudioCue cue : cues) {
            sb.append("at ").append(formatNumber(cue.getTimeMs())).append("ms: ");
            sb.append("play ").append(cue.getChannel()).append(" \"").append(cue.getAudioFile()).append("\"");
            if (cue.getVolume() < 1.0) {
                sb.append(" volume ").append(formatNumber(cue.getVolume()));
            }
            if (cue.isFadeIn() && cue.getFadeDurationMs() > 0) {
                sb.append(" fadein ").append(formatNumber(cue.getFadeDurationMs()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static PropertyType findPropertyByCode(String code) {
        for (PropertyType p : PropertyType.values()) {
            if (p.getCode().equals(code)) return p;
        }
        switch (code) {
            case "x": return PropertyType.X;
            case "y": return PropertyType.Y;
            case "z": return PropertyType.Z;
            case "ox": return PropertyType.PIVOT_X;
            case "oy": return PropertyType.PIVOT_Y;
            case "deg": return PropertyType.ROTATION;
            case "sx": return PropertyType.SCALE_X;
            case "sy": return PropertyType.SCALE_Y;
            case "mirror":
            case "mirrorX":
            case "flipX": return PropertyType.MIRROR_X;
            case "alpha": return PropertyType.ALPHA;
            case "visible":
            case "value": return PropertyType.VISIBILITY;
            default: return null;
        }
    }

    /**
     * Compact export: emits a single-line-per-event format for minimal file size.
     */
    public static String exportCompact(AnimationProject project) {
        if (project == null) return "timeline {}\n";

        StringBuilder sb = new StringBuilder();
        sb.append("timeline {\n");

        List<TimelineEvent> events = collectEvents(project);
        events.sort(Comparator.comparingDouble(e -> e.startTime));

        double currentTime = 0;
        for (TimelineEvent ev : events) {
            double time = quantizeTime(ev.startTime);
            if (time > currentTime + 0.0005) {
                sb.append("  wait ").append(formatNumber(time - currentTime)).append("\n");
                currentTime = time;
            }
            sb.append("  ").append(formatCompactEvent(ev)).append("\n");
        }
        double duration = Double.isFinite(project.getTotalDurationMs()) ? Math.max(0.0, project.getTotalDurationMs()) : 0.0;
        if (duration > currentTime + 0.0005) {
            sb.append("  wait ").append(formatNumber(duration - currentTime)).append("\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String formatCompactEvent(TimelineEvent ev) {
        StringBuilder sb = new StringBuilder();
        if ("event".equals(ev.actionType)) {
            sb.append("event \"").append(ev.target).append("\"");
        } else if ("property".equals(ev.actionType) && "__camera__".equals(ev.target)) {
            sb.append("property");
        } else if ("cameraMove".equals(ev.actionType) || "cameraZoom".equals(ev.actionType)) {
            sb.append(ev.actionType);
        } else {
            sb.append(ev.actionType).append(" \"").append(ev.target).append("\"");
        }
        sb.append(" {");
        for (Map.Entry<String, Object> entry : ev.props.entrySet()) {
            sb.append(" ").append(entry.getKey()).append(":").append(formatPropValue(entry.getValue()));
        }
        if (ev.duration > 0.0 && !"playAudio".equals(ev.actionType)) {
            sb.append(" dur:").append(formatNumber(ev.duration));
        }
        if (ev.interpolation != Easing.Interpolation.TWEEN) {
            sb.append(" interp:").append(ev.interpolation.name().toLowerCase());
        }
        if (ev.interpolation == Easing.Interpolation.TWEEN
            && ev.easingSpec != null
            && ev.easingSpec.getType() != com.jvn.core.animation.Easing.Type.LINEAR) {
            sb.append(" easing:").append(ev.easingSpec.toDslString());
        }
        sb.append(" }");
        return sb.toString();
    }

    private static class TimelineEvent {
        String actionType;
        String target;
        double startTime;
        double duration;
        EasingSpec easingSpec = EasingSpec.of(Easing.Type.LINEAR);
        Easing.Interpolation interpolation = Easing.Interpolation.TWEEN;
        Map<String, Object> props = new java.util.LinkedHashMap<>();
    }

    private static boolean hasAnyCustomKeys(EntityTrack track) {
        if (track == null) return false;
        return track.getAnimatedCustomProperties().iterator().hasNext();
    }

    private static double defaultCustomPropertyValue(String target, String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) return 0.0;
        if ("__camera__".equals(target)) {
            var definition = com.jvn.core.graphics.Camera2D.getAnimatableProperty(propertyKey);
            return definition != null ? definition.getDefaultValue() : 0.0;
        }
        var definition = com.jvn.core.scene2d.Entity2D.getAnimatableProperty(propertyKey);
        return definition != null ? definition.getDefaultValue() : 0.0;
    }
}
