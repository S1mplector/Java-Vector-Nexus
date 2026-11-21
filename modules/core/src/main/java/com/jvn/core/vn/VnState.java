package com.jvn.core.vn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.TimelineRunner;
import com.jvn.core.vn.rollback.VnRollbackStack;
import com.jvn.core.vn.ui.VnOverlayScreenSpec;
import com.jvn.core.vn.ui.VnReactiveOverlayScreenSpec;

/**
 * Manages the current state of a visual novel playthrough
 */
public class VnState {
  private static final String VAR_DIALOGUE_PRESENTATION_MODE = "ui.dialogueMode";
  private static final String VAR_BUBBLE_ANCHOR_PREFIX = "ui.bubble.anchor.";
  private static final String VAR_BUBBLE_OFFSET_X_PREFIX = "ui.bubble.offsetX.";
  private static final String VAR_BUBBLE_OFFSET_Y_PREFIX = "ui.bubble.offsetY.";
  private static final String VAR_ACTIVE_STAGE_PRESET_ID = "stage.activePreset";
  /** Expressions swap immediately unless a command explicitly requests a transition. */
  public static final long DEFAULT_EXPRESSION_TRANSITION_MS = 0L;

  private VnScenario scenario;
  private String sourceScriptName;
  private int currentNodeIndex;
  private int focusedChoiceIndex = -1;
  public int getFocusedChoiceIndex() { return focusedChoiceIndex; }
  public void setFocusedChoiceIndex(int index) { focusedChoiceIndex = index; }
  private String currentBackgroundId;
  private String previousBackgroundId;
  private final Map<CharacterPosition, CharacterSlot> visibleCharacters;
  private final Map<String, DetachedCharacterSlot> detachedCharacters;
  private final List<Integer> callStack; // For CALL/RETURN subroutine support
  private final VnRollbackStack rollbackStack;
  private final Map<CharacterPosition, CharacterVisual> characterVisuals;
  private final Map<CharacterPosition, PendingExpressionSwitch> pendingExpressionSwitches;
  private final Map<String, ExpressionTransition> expressionTransitions;
  private final Map<String, EyeFocusRequest> eyeFocusRequests;
  private final Map<String, TimelineDisplacement> timelineDisplacements;
  private final Map<String, TimelineTransform> timelineTransforms;
  private final Set<String> globalPositionCharacters;
  private final Map<String, CharacterPosition> characterDefinedPositions;
  private final Map<String, Object> variables; // For future flag/variable system
  private final Map<String, String> dynamicGroups; // targetId -> parentId
  private final VnPersistentStore persistentStore;
  private final Set<String> mirroredPersistentVariables;
  private final List<VnOverlayScreenSpec> overlayScreens = new ArrayList<>();
  private boolean waitingForInput;
  private int textRevealProgress; // For text animation
  // A completed line remains visible while a following blocking scene action
  // (such as a timeline or wait) is in progress.
  private DialogueLine retainedDialogue;
  private final VnHistory history;
  private final VnSettings settings;
  private boolean skipMode = false;
  private boolean autoPlayMode = false;
  private long autoPlayTimer = 0;
  private final Set<Integer> readNodes; // Track which nodes have been read
  private VnTransition activeTransition;
  private long transitionStartTime;
  private long transitionPausedAt;
  private String previousBackgroundIdDuringTransition;
  private boolean uiHidden = false; // H key toggle
  private boolean historyOverlayShown = false; // Backlog toggle
  private int historyScroll = 0; // lines to scroll back from newest (0 = show newest)
  private String hudMessage;
  private long hudMessageExpireAt;
  
  // Save slot overlay state
  private boolean saveSlotOverlayShown = false;
  private boolean saveSlotOverlayIsSaveMode = true; // true = save, false = load
  private int saveSlotSelected = 0; // 0-9 slots (0 = quick save)
  private Object rpgState = new com.jvn.core.rpg.RpgState(); // Optional RPG state payload (serializable)

  private final List<TimelineRunner> activeTimelines = new ArrayList<>();

  private float screenShakeIntensity = 0f;
  private long screenShakeDurationMs = 0;
  private long screenShakeRemainingMs = 0;
  private float flashR = 1f;
  private float flashG = 1f;
  private float flashB = 1f;
  private float flashStrength = 0f;
  private long flashDurationMs = 0;
  private long flashRemainingMs = 0;

  private static final long CHARACTER_TWEEN_MS = 220;
  private static final long CHARACTER_MOVE_MS = 320;
  private static final double TIMELINE_SLOT_DETACH_THRESHOLD_PX = 120.0;

  public VnState() {
    this.currentNodeIndex = 0;
    this.visibleCharacters = new HashMap<>();
    this.detachedCharacters = new HashMap<>();
    this.characterVisuals = new HashMap<>();
    this.pendingExpressionSwitches = new HashMap<>();
    this.expressionTransitions = new HashMap<>();
    this.eyeFocusRequests = new HashMap<>();
    this.timelineDisplacements = new HashMap<>();
    this.timelineTransforms = new HashMap<>();
    this.globalPositionCharacters = new HashSet<>();
    this.characterDefinedPositions = new HashMap<>();
    this.variables = new HashMap<>();
    this.dynamicGroups = new HashMap<>();
    this.persistentStore = new VnPersistentStore();
    this.mirroredPersistentVariables = new HashSet<>();
    this.waitingForInput = false;
    this.textRevealProgress = 0;
    this.history = new VnHistory();
    this.settings = new VnSettings();
    this.readNodes = new HashSet<>();
    this.callStack = new ArrayList<>();
    this.rollbackStack = new VnRollbackStack();
    syncPersistentVariableMirror();
  }

  public VnScenario getScenario() { return scenario; }
  public void setScenario(VnScenario scenario) {
    this.scenario = scenario;
    this.currentNodeIndex = 0;
    this.retainedDialogue = null;
  }

  public String getSourceScriptName() { return sourceScriptName; }
  public void setSourceScriptName(String sourceScriptName) { this.sourceScriptName = sourceScriptName; }

  public int getCurrentNodeIndex() { return currentNodeIndex; }
  public void setCurrentNodeIndex(int index) {
    this.currentNodeIndex = index;
    // Indexed navigation starts a new presentation position and must not
    // inherit dialogue from the point that was navigated away from.
    this.retainedDialogue = null;
  }
  public void advance() {
    VnNode current = getCurrentNode();
    if (current != null && current.getType() == VnNodeType.DIALOGUE) {
      retainedDialogue = current.getDialogue();
    }
    currentNodeIndex++;
    VnNode next = getCurrentNode();
    if (next == null || next.getType() == VnNodeType.CHOICE || next.getType() == VnNodeType.END) {
      retainedDialogue = null;
    }
  }

  public VnNode getCurrentNode() {
    return scenario != null ? scenario.getNode(currentNodeIndex) : null;
  }

  /**
   * The last completed line, retained while intervening scene actions are
   * presented. This lets dialogue remain readable during an animation.
   */
  public DialogueLine getRetainedDialogue() { return retainedDialogue; }

  /**
   * Reconstructs retained dialogue after a saved state is restored. Scene
   * actions between a completed line and the saved blocking action do not
   * replace the dialogue window; a choice or end node does.
   */
  public void restoreRetainedDialogue() {
    retainedDialogue = null;
    VnNode current = getCurrentNode();
    if (current == null || current.getType() == VnNodeType.DIALOGUE
        || current.getType() == VnNodeType.CHOICE || current.getType() == VnNodeType.END) {
      return;
    }
    for (int index = currentNodeIndex - 1; index >= 0; index--) {
      VnNode previous = scenario.getNode(index);
      if (previous == null || previous.getType() == VnNodeType.CHOICE || previous.getType() == VnNodeType.END) {
        return;
      }
      if (previous.getType() == VnNodeType.DIALOGUE) {
        retainedDialogue = previous.getDialogue();
        return;
      }
    }
  }

  public String getCurrentBackgroundId() { return currentBackgroundId; }
  public void setCurrentBackgroundId(String id) {
    this.previousBackgroundId = this.currentBackgroundId;
    this.currentBackgroundId = id;
  }
  public String getPreviousBackgroundId() { return previousBackgroundId; }

  public Map<CharacterPosition, CharacterSlot> getVisibleCharacters() {
    return visibleCharacters;
  }

  public Map<String, String> getDynamicGroups() {
    return dynamicGroups;
  }

  public Map<String, DetachedCharacterSlot> getDetachedCharacters() {
    return Collections.unmodifiableMap(detachedCharacters);
  }

  public DetachedCharacterSlot getDetachedCharacter(String characterId) {
    String key = stateKey(characterId, null);
    return key.isEmpty() ? null : detachedCharacters.get(key);
  }

  public DetachedCharacterSlot getDetachedCharacter(String characterId, String displaySlot) {
    String key = stateKey(characterId, displaySlot);
    return key.isEmpty() ? null : detachedCharacters.get(key);
  }

  public DetachedCharacterSlot getDetachedDisplaySlot(String displaySlot) {
    String key = stateKey(null, displaySlot);
    return key.isEmpty() ? null : detachedCharacters.get(key);
  }

  public void showCharacter(CharacterPosition position, String characterId, String expression) {
    showCharacter(position, characterId, expression, null);
  }

  public void showCharacter(CharacterPosition position, String characterId, String expression, Integer layerOrder) {
    showCharacter(position, characterId, expression, layerOrder, null);
  }

  public void showCharacter(CharacterPosition position,
                            String characterId,
                            String expression,
                            Integer layerOrder,
                            String displaySlot) {
    String normalizedSlot = normalizeDisplaySlotId(displaySlot);
    CharacterPosition baseTarget = fallbackPositionFor(characterId, position);
    CharacterPosition target = displayPositionFor(baseTarget, normalizedSlot);
    String resolvedExpression = normalizeExpression(expression, "neutral");
    CharacterPosition existingPos = findTargetPosition(characterId, normalizedSlot);
    CharacterSlot existingSlot = existingPos == null ? null : visibleCharacters.get(existingPos);
    int resolvedLayerOrder = resolveLayerOrder(baseTarget, layerOrder, existingSlot != null ? existingSlot.getLayerOrder() : null);
    removeConflictingSlots(characterId, normalizedSlot, target);
    detachedCharacters.remove(stateKey(characterId, normalizedSlot));
    if (normalizedSlot.isEmpty()) {
      detachTimelineDisplacedOccupant(target, characterId, normalizedSlot);
    }
    clearVisibleSlotState(target);
    visibleCharacters.put(target, new CharacterSlot(characterId, resolvedExpression, resolvedLayerOrder, normalizedSlot));
    pendingExpressionSwitches.remove(target);
    expressionTransitions.remove(stateKey(characterId, normalizedSlot));
    CharacterVisual visual = ensureCharacterVisual(target);
    visual.setImmediate(1.0, 0.0, 0.0);
    if (normalizedSlot.isEmpty() && isCharacterGlobalPositionEnabled(characterId)) {
      characterDefinedPositions.put(characterId, baseTarget);
    }
  }

  public void hideCharacter(CharacterPosition position) {
    removeSlot(position);
  }

  public void hideCharacter(String characterId, String displaySlot) {
    String key = stateKey(characterId, displaySlot);
    if (key.isEmpty()) return;
    CharacterPosition position = findTargetPosition(characterId, displaySlot);
    if (position != null) {
      removeSlot(position);
      return;
    }
    detachedCharacters.remove(key);
    expressionTransitions.remove(key);
    timelineDisplacements.remove(key);
    timelineTransforms.remove(key);
  }

  public void clearAllCharacters() {
    visibleCharacters.clear();
    detachedCharacters.clear();
    characterVisuals.clear();
    pendingExpressionSwitches.clear();
    expressionTransitions.clear();
    eyeFocusRequests.clear();
    timelineDisplacements.clear();
    timelineTransforms.clear();
  }

  public void showCharacterAnimated(CharacterPosition position, String characterId, String expression) {
    showCharacterAnimated(position, characterId, expression, null);
  }

  public void showCharacterAnimated(CharacterPosition position, String characterId, String expression, Integer layerOrder) {
    showCharacterAnimated(position, characterId, expression, layerOrder, null, 0);
  }

  public void showCharacterAnimated(CharacterPosition position, String characterId, String expression,
                                     Integer layerOrder, Easing.Type easingType, long customDurationMs) {
    showCharacterAnimated(position, characterId, expression, layerOrder, easingType, customDurationMs, null);
  }

  public void showCharacterAnimated(CharacterPosition position,
                                    String characterId,
                                    String expression,
                                    Integer layerOrder,
                                    Easing.Type easingType,
                                    long customDurationMs,
                                    String displaySlot) {
    showCharacterAnimated(
        position,
        characterId,
        expression,
        layerOrder,
        easingType,
        customDurationMs,
        displaySlot,
        -1L);
  }

  public void showCharacterAnimated(CharacterPosition position,
                                    String characterId,
                                    String expression,
                                    Integer layerOrder,
                                    Easing.Type easingType,
                                    long customDurationMs,
                                    String displaySlot,
                                    long expressionDurationMs) {
    String normalizedSlot = normalizeDisplaySlotId(displaySlot);
    CharacterPosition baseTarget = fallbackPositionFor(characterId, position);
    CharacterPosition target = displayPositionFor(baseTarget, normalizedSlot);
    CharacterPosition existingPos = findTargetPosition(characterId, normalizedSlot);
    CharacterSlot existingSlot = existingPos == null ? null : visibleCharacters.get(existingPos);
    String fallbackExpression = existingSlot != null ? existingSlot.getExpression() : "neutral";
    String resolvedExpression = normalizeExpression(expression, fallbackExpression);
    long resolvedExpressionDurationMs = expressionDurationMs >= 0L
        ? expressionDurationMs
        : DEFAULT_EXPRESSION_TRANSITION_MS;
    int resolvedLayerOrder = resolveLayerOrder(baseTarget, layerOrder, existingSlot != null ? existingSlot.getLayerOrder() : null);

    if (existingPos != null && existingSlot != null && existingPos.equals(target)) {
      updateVisibleSlotExpression(target, existingSlot, resolvedExpression, resolvedLayerOrder,
          resolvedExpressionDurationMs, null);
      pendingExpressionSwitches.remove(target);
      ensureCharacterVisual(target);
      if (normalizedSlot.isEmpty() && isCharacterGlobalPositionEnabled(characterId)) {
        characterDefinedPositions.put(characterId, baseTarget);
      }
      return;
    }

    if (isCharacterGlobalPositionEnabled(characterId) && existingPos != null && existingSlot != null && !existingPos.equals(target)) {
      // Move the same sprite between slots, then fade expression if needed.
      long moveDur = customDurationMs > 0 ? customDurationMs : CHARACTER_MOVE_MS;
      String movingExpression = normalizeExpression(existingSlot.getExpression(), resolvedExpression);
      removeSlot(existingPos);
      visibleCharacters.put(target, new CharacterSlot(characterId, movingExpression, resolvedLayerOrder, normalizedSlot));
      CharacterVisual visual = ensureCharacterVisual(target);
      double startOffset = positionDeltaOffset(existingPos, target);
      visual.startAnimation(1.0, 1.0, startOffset, 0.0, 0.0, 0.0, moveDur, false, easingType);
      pendingExpressionSwitches.remove(target);
      if (!resolvedExpression.equals(movingExpression)) {
        pendingExpressionSwitches.put(target, new PendingExpressionSwitch(
            characterId,
            normalizedSlot,
            resolvedExpression,
            moveDur,
            resolvedExpressionDurationMs,
            null));
      }
      if (normalizedSlot.isEmpty()) {
        characterDefinedPositions.put(characterId, baseTarget);
      }
      return;
    }

    long tweenDur = customDurationMs > 0 ? customDurationMs : CHARACTER_TWEEN_MS;
    removeConflictingSlots(characterId, normalizedSlot, target);
    detachedCharacters.remove(stateKey(characterId, normalizedSlot));
    if (normalizedSlot.isEmpty()) {
      detachTimelineDisplacedOccupant(target, characterId, normalizedSlot);
    }
    clearVisibleSlotState(target);
    visibleCharacters.put(target, new CharacterSlot(characterId, resolvedExpression, resolvedLayerOrder, normalizedSlot));
    pendingExpressionSwitches.remove(target);
    expressionTransitions.remove(stateKey(characterId, normalizedSlot));
    CharacterVisual visual = ensureCharacterVisual(target);
    double startX = entranceOffsetX(target);
    visual.startAnimation(0.0, 1.0, startX, 0.0, 0.0, 0.0, tweenDur, false, easingType);
    if (normalizedSlot.isEmpty() && isCharacterGlobalPositionEnabled(characterId)) {
      characterDefinedPositions.put(characterId, baseTarget);
    }
  }

  public boolean moveDisplaySlotAnimated(String displaySlot,
                                         CharacterPosition position,
                                         String expression,
                                         Easing.Type easingType,
                                         long customDurationMs) {
    return moveDisplaySlotAnimated(displaySlot, position, expression, easingType, customDurationMs, -1L);
  }

  public boolean moveDisplaySlotAnimated(String displaySlot,
                                         CharacterPosition position,
                                         String expression,
                                         Easing.Type easingType,
                                         long customDurationMs,
                                         long expressionDurationMs) {
    CharacterPosition existingPos = findDisplaySlotPosition(displaySlot);
    CharacterSlot existingSlot = existingPos == null ? null : visibleCharacters.get(existingPos);
    if (existingSlot == null) return false;
    showCharacterAnimated(position,
        existingSlot.getCharacterId(),
        expression == null ? existingSlot.getExpression() : expression,
        null,
        easingType,
        customDurationMs,
        existingSlot.getDisplaySlot(),
        expressionDurationMs);
    return true;
  }

  public void hideCharacterAnimated(CharacterPosition position) {
    if (position == null || !visibleCharacters.containsKey(position)) return;
    CharacterVisual visual = ensureCharacterVisual(position);
    double endX = entranceOffsetX(position);
    visual.startAnimation(visual.getAlpha(), 0.0, visual.getOffsetX(), endX, visual.getOffsetY(), 0.0, CHARACTER_TWEEN_MS, true);
  }

  public void hideCharacterAnimated(String characterId) {
    hideCharacterAnimated(characterId, null);
  }

  public void hideCharacterAnimated(String characterId, String displaySlot) {
    String id = normalizeCharacterId(characterId);
    String slot = normalizeDisplaySlotId(displaySlot);
    if (id.isEmpty() && slot.isEmpty()) return;
    CharacterPosition position = findTargetPosition(id, slot);
    if (position != null) {
      hideCharacterAnimated(position);
      return;
    }
    DetachedCharacterSlot detached = detachedCharacters.get(stateKey(id, slot));
    if (detached == null) return;
    CharacterVisual visual = detached.getVisual();
    double endX = entranceOffsetX(detached.getBasePosition());
    visual.startAnimation(visual.getAlpha(), 0.0, visual.getOffsetX(), endX, visual.getOffsetY(), 0.0, CHARACTER_TWEEN_MS, true);
  }

  public void hideDisplaySlotAnimated(String displaySlot) {
    hideCharacterAnimated(null, displaySlot);
  }

  public CharacterVisual getCharacterVisual(CharacterPosition position) {
    return characterVisuals.get(position);
  }

  public CharacterVisual getOrCreateCharacterVisual(CharacterPosition position) {
    return ensureCharacterVisual(position);
  }

  public void updateCharacterAnimations(long deltaMs) {
    if (!characterVisuals.isEmpty()) {
      var it = characterVisuals.entrySet().iterator();
      while (it.hasNext()) {
        var entry = it.next();
        CharacterVisual visual = entry.getValue();
        visual.update(deltaMs);
        if (visual.isFinished() && visual.isRemoveOnComplete()) {
          CharacterSlot slot = visibleCharacters.remove(entry.getKey());
          pendingExpressionSwitches.remove(entry.getKey());
          if (slot != null) {
            String key = stateKey(slot);
            expressionTransitions.remove(key);
            eyeFocusRequests.remove(slot.getCharacterId());
            detachedCharacters.remove(key);
            timelineDisplacements.remove(key);
            timelineTransforms.remove(key);
          }
          it.remove();
        }
      }
    }

    if (!detachedCharacters.isEmpty()) {
      var it = detachedCharacters.entrySet().iterator();
      while (it.hasNext()) {
        var entry = it.next();
        CharacterVisual visual = entry.getValue().getVisual();
        visual.update(deltaMs);
        if (visual.isFinished() && visual.isRemoveOnComplete()) {
          CharacterSlot slot = entry.getValue().getSlot();
          if (slot != null) eyeFocusRequests.remove(slot.getCharacterId());
          expressionTransitions.remove(entry.getKey());
          timelineDisplacements.remove(entry.getKey());
          timelineTransforms.remove(entry.getKey());
          it.remove();
        }
      }
    }

    if (!expressionTransitions.isEmpty()) {
      var it = expressionTransitions.entrySet().iterator();
      while (it.hasNext()) {
        var entry = it.next();
        String stateKey = entry.getKey();
        ExpressionTransition transition = entry.getValue();
        if (findStateKeyPosition(stateKey) == null && !detachedCharacters.containsKey(stateKey)) {
          it.remove();
          continue;
        }
        transition.update(deltaMs);
        if (transition.isFinished()) {
          it.remove();
        }
      }
    }

    // Start delayed expression swaps after advancing transitions that were
    // already active at the beginning of this frame. A newly-started
    // crossfade must not consume the movement frame's entire delta.
    if (!pendingExpressionSwitches.isEmpty()) {
      var it = pendingExpressionSwitches.entrySet().iterator();
      while (it.hasNext()) {
        var entry = it.next();
        CharacterPosition position = entry.getKey();
        PendingExpressionSwitch pending = entry.getValue();
        if (!pending.tick(deltaMs)) continue;

        CharacterSlot slot = visibleCharacters.get(position);
        if (slot == null || !pending.matches(slot)) {
          it.remove();
          continue;
        }
        updateVisibleSlotExpression(position, slot, pending.expression, slot.getLayerOrder(),
            pending.transitionDurationMs, pending.easingType);
        it.remove();
      }
    }
  }

  public void setCharacterGlobalPositionEnabled(String characterId, boolean enabled) {
    if (characterId == null || characterId.isBlank()) return;
    String id = characterId.trim();
    if (enabled) {
      globalPositionCharacters.add(id);
      CharacterPosition current = findCharacterPosition(id);
      if (current != null) characterDefinedPositions.put(id, current);
    } else {
      globalPositionCharacters.remove(id);
      characterDefinedPositions.remove(id);
    }
  }

  public boolean isCharacterGlobalPositionEnabled(String characterId) {
    if (characterId == null || characterId.isBlank()) return false;
    return globalPositionCharacters.contains(characterId.trim());
  }

  public Set<String> getGlobalPositionCharactersSnapshot() {
    return new HashSet<>(globalPositionCharacters);
  }

  public Map<String, CharacterPosition> getCharacterDefinedPositionsSnapshot() {
    return new HashMap<>(characterDefinedPositions);
  }

  public void setGlobalPositionState(Set<String> globalCharacters, Map<String, CharacterPosition> definedPositions) {
    globalPositionCharacters.clear();
    characterDefinedPositions.clear();

    if (globalCharacters != null) {
      for (String id : globalCharacters) {
        if (id != null && !id.isBlank()) {
          globalPositionCharacters.add(id.trim());
        }
      }
    }

    if (definedPositions != null) {
      for (var entry : definedPositions.entrySet()) {
        String id = entry.getKey();
        CharacterPosition position = entry.getValue();
        if (id == null || id.isBlank() || position == null) continue;
        characterDefinedPositions.put(id.trim(), position);
      }
    }
  }

  public void setCharacterDefinedPosition(String characterId, CharacterPosition position) {
    if (characterId == null || characterId.isBlank() || position == null) return;
    characterDefinedPositions.put(characterId.trim(), position);
  }

  public CharacterPosition getCharacterDefinedPosition(String characterId) {
    if (characterId == null || characterId.isBlank()) return null;
    return characterDefinedPositions.get(characterId.trim());
  }

  public CharacterPosition getCharacterPosition(String characterId) {
    return findCharacterPosition(characterId);
  }

  public CharacterPosition getCharacterPosition(String characterId, String displaySlot) {
    return findTargetPosition(characterId, displaySlot);
  }

  public CharacterPosition getDisplaySlotPosition(String displaySlot) {
    return findDisplaySlotPosition(displaySlot);
  }

  public TimelineDisplacement getTimelineDisplacement(String characterId) {
    if (characterId == null || characterId.isBlank()) return null;
    return timelineDisplacements.get(characterId.trim());
  }

  public void recordTimelineDisplacement(String characterId, double x, double y, boolean hasX, boolean hasY) {
    String id = normalizeCharacterId(characterId);
    if (id.isEmpty() || (!hasX && !hasY)) return;

    TimelineDisplacement existing = timelineDisplacements.get(id);
    double resolvedX = existing != null ? existing.getX() : 0.0;
    double resolvedY = existing != null ? existing.getY() : 0.0;
    boolean resolvedHasX = existing != null && existing.hasX();
    boolean resolvedHasY = existing != null && existing.hasY();

    if (hasX && Double.isFinite(x)) {
      resolvedX = x;
      resolvedHasX = true;
    }
    if (hasY && Double.isFinite(y)) {
      resolvedY = y;
      resolvedHasY = true;
    }
    timelineDisplacements.put(id, new TimelineDisplacement(resolvedX, resolvedY, resolvedHasX, resolvedHasY));
    recordTimelineTransform(
        id,
        resolvedX,
        resolvedY,
        resolvedHasX,
        resolvedHasY,
        1.0,
        1.0,
        false,
        false,
        0.0,
        false,
        0.5,
        1.0,
        false,
        false);
  }

  public TimelineTransform getTimelineTransform(String characterId) {
    if (characterId == null || characterId.isBlank()) return null;
    return timelineTransforms.get(characterId.trim());
  }

  public void recordTimelineTransform(
      String characterId,
      double x,
      double y,
      boolean hasX,
      boolean hasY,
      double scaleX,
      double scaleY,
      boolean hasScaleX,
      boolean hasScaleY,
      double rotationDeg,
      boolean hasRotation,
      double pivotX,
      double pivotY,
      boolean hasPivotX,
      boolean hasPivotY) {
    String id = normalizeCharacterId(characterId);
    if (id.isEmpty() || (!hasX && !hasY && !hasScaleX && !hasScaleY && !hasRotation && !hasPivotX && !hasPivotY)) {
      return;
    }

    TimelineTransform existing = timelineTransforms.get(id);
    double resolvedX = existing != null ? existing.getX() : 0.0;
    double resolvedY = existing != null ? existing.getY() : 0.0;
    double resolvedScaleX = existing != null ? existing.getScaleX() : 1.0;
    double resolvedScaleY = existing != null ? existing.getScaleY() : 1.0;
    double resolvedRotation = existing != null ? existing.getRotationDeg() : 0.0;
    double resolvedPivotX = existing != null ? existing.getPivotX() : 0.5;
    double resolvedPivotY = existing != null ? existing.getPivotY() : 1.0;
    boolean resolvedHasX = existing != null && existing.hasX();
    boolean resolvedHasY = existing != null && existing.hasY();
    boolean resolvedHasScaleX = existing != null && existing.hasScaleX();
    boolean resolvedHasScaleY = existing != null && existing.hasScaleY();
    boolean resolvedHasRotation = existing != null && existing.hasRotation();
    boolean resolvedHasPivotX = existing != null && existing.hasPivotX();
    boolean resolvedHasPivotY = existing != null && existing.hasPivotY();

    if (hasX && Double.isFinite(x)) {
      resolvedX = x;
      resolvedHasX = true;
    }
    if (hasY && Double.isFinite(y)) {
      resolvedY = y;
      resolvedHasY = true;
    }
    if (hasScaleX && Double.isFinite(scaleX)) {
      resolvedScaleX = scaleX;
      resolvedHasScaleX = true;
    }
    if (hasScaleY && Double.isFinite(scaleY)) {
      resolvedScaleY = scaleY;
      resolvedHasScaleY = true;
    }
    if (hasRotation && Double.isFinite(rotationDeg)) {
      resolvedRotation = rotationDeg;
      resolvedHasRotation = true;
    }
    if (hasPivotX && Double.isFinite(pivotX)) {
      resolvedPivotX = pivotX;
      resolvedHasPivotX = true;
    }
    if (hasPivotY && Double.isFinite(pivotY)) {
      resolvedPivotY = pivotY;
      resolvedHasPivotY = true;
    }

    timelineTransforms.put(id, new TimelineTransform(
        resolvedX,
        resolvedY,
        resolvedHasX,
        resolvedHasY,
        resolvedScaleX,
        resolvedScaleY,
        resolvedHasScaleX,
        resolvedHasScaleY,
        resolvedRotation,
        resolvedHasRotation,
        resolvedPivotX,
        resolvedPivotY,
        resolvedHasPivotX,
        resolvedHasPivotY));
  }

  public String getCharacterExpression(String characterId) {
    return getCharacterExpression(characterId, null);
  }

  public String getCharacterExpression(String characterId, String displaySlot) {
    CharacterPosition position = findTargetPosition(characterId, displaySlot);
    CharacterSlot slot = position == null ? null : visibleCharacters.get(position);
    if (slot == null) {
      DetachedCharacterSlot detached = getDetachedCharacter(characterId, displaySlot);
      slot = detached == null ? null : detached.getSlot();
    }
    return slot == null ? null : slot.getExpression();
  }

  public boolean setCharacterExpression(String characterId, String expression) {
    return setCharacterExpression(characterId, expression, DEFAULT_EXPRESSION_TRANSITION_MS);
  }

  public boolean setCharacterExpression(String characterId, String expression, long transitionDurationMs) {
    return setCharacterExpression(characterId, expression, transitionDurationMs, null);
  }

  public boolean setCharacterExpression(String characterId, String expression, long transitionDurationMs, Easing.Type easingType) {
    return setCharacterExpression(characterId, null, expression, transitionDurationMs, easingType);
  }

  public boolean setCharacterExpression(String characterId,
                                        String displaySlot,
                                        String expression,
                                        long transitionDurationMs,
                                        Easing.Type easingType) {
    String id = normalizeCharacterId(characterId);
    String slotId = normalizeDisplaySlotId(displaySlot);
    if (id.isEmpty() && slotId.isEmpty()) return false;
    String resolvedExpression = normalizeExpression(expression, "neutral");

    CharacterPosition position = findTargetPosition(id, slotId);
    if (position != null) {
      CharacterSlot slot = visibleCharacters.get(position);
      if (slot == null) return false;
      updateVisibleSlotExpression(position, slot, resolvedExpression, slot.getLayerOrder(), transitionDurationMs, easingType);
      pendingExpressionSwitches.remove(position);
      ensureCharacterVisual(position);
      return true;
    }

    String key = stateKey(id, slotId);
    DetachedCharacterSlot detached = detachedCharacters.get(key);
    if (detached == null || detached.getSlot() == null) return false;
    CharacterSlot slot = detached.getSlot();
    beginExpressionTransition(stateKey(slot), slot.getCharacterId(), slot.getExpression(), resolvedExpression, transitionDurationMs, easingType);
    detachedCharacters.put(key, new DetachedCharacterSlot(
        detached.getBasePosition(),
        new CharacterSlot(slot.getCharacterId(), resolvedExpression, slot.getLayerOrder(), slot.getDisplaySlot()),
        detached.getVisual()));
    return true;
  }

  public ExpressionTransition getExpressionTransition(String characterId) {
    String key = stateKey(characterId, null);
    if (key.isEmpty()) return null;
    ExpressionTransition transition = expressionTransitions.get(key);
    return transition != null && !transition.isFinished() ? transition : null;
  }

  public ExpressionTransition getExpressionTransition(CharacterSlot slot) {
    String key = stateKey(slot);
    if (key.isEmpty()) return null;
    ExpressionTransition transition = expressionTransitions.get(key);
    return transition != null && !transition.isFinished() ? transition : null;
  }

  public void setEyeFocusRequest(EyeFocusRequest request) {
    if (request == null || request.characterId().isBlank()) return;
    eyeFocusRequests.put(request.characterId(), request);
  }

  public void clearEyeFocusRequest(String characterId) {
    if (characterId == null || characterId.isBlank()) return;
    eyeFocusRequests.remove(characterId.trim());
  }

  public EyeFocusRequest getEyeFocusRequest(String characterId) {
    if (characterId == null || characterId.isBlank()) return null;
    return eyeFocusRequests.get(characterId.trim());
  }

  public Map<String, EyeFocusRequest> getEyeFocusRequestsSnapshot() {
    return new HashMap<>(eyeFocusRequests);
  }

  private CharacterVisual ensureCharacterVisual(CharacterPosition position) {
    return characterVisuals.computeIfAbsent(position, k -> new CharacterVisual());
  }

  private void removeSlot(CharacterPosition position) {
    if (position == null) return;
    CharacterSlot slot = visibleCharacters.get(position);
    visibleCharacters.remove(position);
    characterVisuals.remove(position);
    pendingExpressionSwitches.remove(position);
    if (slot != null) {
      String key = stateKey(slot);
      expressionTransitions.remove(key);
      eyeFocusRequests.remove(slot.getCharacterId());
      detachedCharacters.remove(key);
      timelineDisplacements.remove(key);
      timelineTransforms.remove(key);
    }
  }

  /**
   * Unslotted characters keep legacy one-slot-per-character behavior. Named display slots are
   * independent instances and only replace an older occupant of the same display slot.
   */
  private void removeConflictingSlots(String characterId, String displaySlot, CharacterPosition keepPosition) {
    if (visibleCharacters.isEmpty()) return;
    String normalizedSlot = normalizeDisplaySlotId(displaySlot);
    String normalizedCharacter = normalizeCharacterId(characterId);
    var it = visibleCharacters.entrySet().iterator();
    while (it.hasNext()) {
      var entry = it.next();
      if (entry.getKey().equals(keepPosition)) continue;
      CharacterSlot slot = entry.getValue();
      if (slot == null) continue;
      boolean remove = normalizedSlot.isEmpty()
          ? slot.getDisplaySlot().isEmpty() && !normalizedCharacter.isEmpty() && normalizedCharacter.equals(slot.getCharacterId())
          : normalizedSlot.equals(slot.getDisplaySlot());
      if (!remove) continue;
      clearSlotState(slot);
      characterVisuals.remove(entry.getKey());
      pendingExpressionSwitches.remove(entry.getKey());
      it.remove();
    }
    detachedCharacters.remove(stateKey(characterId, normalizedSlot));
  }

  private CharacterPosition fallbackPositionFor(String characterId, CharacterPosition requested) {
    if (requested != null) return requested.getBasePosition();
    CharacterPosition defined = getCharacterDefinedPosition(characterId);
    return defined != null ? defined : CharacterPosition.CENTER;
  }

  private CharacterPosition findCharacterPosition(String characterId) {
    if (characterId == null || characterId.isBlank()) return null;
    String id = characterId.trim();
    for (var entry : visibleCharacters.entrySet()) {
      CharacterSlot slot = entry.getValue();
      if (slot != null && slot.getDisplaySlot().isEmpty() && id.equals(slot.getCharacterId())) {
        return entry.getKey();
      }
    }
    return null;
  }

  private CharacterPosition findTargetPosition(String characterId, String displaySlot) {
    String slot = normalizeDisplaySlotId(displaySlot);
    return slot.isEmpty() ? findCharacterPosition(characterId) : findDisplaySlotPosition(slot);
  }

  private CharacterPosition findDisplaySlotPosition(String displaySlot) {
    String slotId = normalizeDisplaySlotId(displaySlot);
    if (slotId.isEmpty()) return null;
    for (var entry : visibleCharacters.entrySet()) {
      CharacterSlot slot = entry.getValue();
      if (slot != null && slotId.equals(slot.getDisplaySlot())) {
        return entry.getKey();
      }
    }
    return null;
  }

  private CharacterPosition findStateKeyPosition(String stateKey) {
    if (stateKey == null || stateKey.isBlank()) return null;
    for (var entry : visibleCharacters.entrySet()) {
      CharacterSlot slot = entry.getValue();
      if (slot != null && stateKey.equals(stateKey(slot))) {
        return entry.getKey();
      }
    }
    return null;
  }

  private CharacterPosition displayPositionFor(CharacterPosition basePosition, String displaySlot) {
    String slot = normalizeDisplaySlotId(displaySlot);
    CharacterPosition base = basePosition == null ? CharacterPosition.CENTER : basePosition.getBasePosition();
    return slot.isEmpty() ? base : CharacterPosition.slotted(base, slot);
  }

  private void detachTimelineDisplacedOccupant(CharacterPosition target, String incomingCharacterId, String incomingDisplaySlot) {
    if (target == null) return;
    CharacterSlot occupant = visibleCharacters.get(target);
    if (occupant == null) return;

    String occupantId = stateKey(occupant);
    String incomingId = stateKey(incomingCharacterId, incomingDisplaySlot);
    if (occupantId.isEmpty() || occupantId.equals(incomingId)) return;
    if (!hasTimelineDisplacementAwayFromSlot(occupantId)) return;

    CharacterVisual visual = characterVisuals.get(target);
    detachedCharacters.put(occupantId, new DetachedCharacterSlot(target, occupant, snapshotVisual(visual)));
    visibleCharacters.remove(target);
    characterVisuals.remove(target);
    pendingExpressionSwitches.remove(target);
  }

  private void updateVisibleSlotExpression(
      CharacterPosition position,
      CharacterSlot slot,
      String resolvedExpression,
      int layerOrder,
      long transitionDurationMs,
      Easing.Type easingType) {
    if (slot == null || position == null) return;
    String previousExpression = normalizeExpression(slot.getExpression(), "neutral");
    String nextExpression = normalizeExpression(resolvedExpression, previousExpression);
    visibleCharacters.put(position, new CharacterSlot(slot.getCharacterId(), nextExpression, layerOrder, slot.getDisplaySlot()));
    beginExpressionTransition(stateKey(slot), slot.getCharacterId(), previousExpression, nextExpression, transitionDurationMs, easingType);
  }

  private void beginExpressionTransition(
      String stateKey,
      String characterId,
      String previousExpression,
      String nextExpression,
      long transitionDurationMs,
      Easing.Type easingType) {
    String id = stateKey == null ? "" : stateKey.trim();
    if (id.isEmpty()) return;
    String from = normalizeExpression(previousExpression, "neutral");
    String to = normalizeExpression(nextExpression, from);
    if (transitionDurationMs <= 0L || from.equals(to)) {
      expressionTransitions.remove(id);
      return;
    }
    LayeredCharacterResolver.ExpressionLayerDiff layerDiff = computeExpressionLayerDiff(characterId, from, to);
    expressionTransitions.put(id, new ExpressionTransition(from, to, transitionDurationMs, easingType, layerDiff));
  }

  private LayeredCharacterResolver.ExpressionLayerDiff computeExpressionLayerDiff(
      String characterId, String fromExpression, String toExpression) {
    if (scenario == null || characterId == null || characterId.isBlank()) return null;
    VnCharacter character = scenario.getCharacter(characterId);
    if (character == null) return null;
    List<String> fromLayerIds = character.getExpressionLayerIds(fromExpression);
    List<String> toLayerIds = character.getExpressionLayerIds(toExpression);
    if (fromLayerIds.isEmpty() || toLayerIds.isEmpty()) return null;
    return LayeredCharacterResolver.diffExpressionLayers(fromLayerIds, toLayerIds);
  }

  private boolean hasTimelineDisplacementAwayFromSlot(String characterId) {
    TimelineDisplacement displacement = getTimelineDisplacement(characterId);
    if (displacement == null) return false;
    return (displacement.hasX() && Math.abs(displacement.getX()) >= TIMELINE_SLOT_DETACH_THRESHOLD_PX)
        || (displacement.hasY() && Math.abs(displacement.getY()) >= TIMELINE_SLOT_DETACH_THRESHOLD_PX);
  }

  private CharacterVisual snapshotVisual(CharacterVisual visual) {
    CharacterVisual copy = new CharacterVisual();
    if (visual == null) return copy;
    copy.setImmediate(visual.getAlpha(), visual.getOffsetX(), visual.getOffsetY());
    return copy;
  }

  private String normalizeCharacterId(String characterId) {
    return characterId == null ? "" : characterId.trim();
  }

  public String normalizeDisplaySlotId(String displaySlot) {
    return displaySlot == null ? "" : displaySlot.trim();
  }

  private String stateKey(CharacterSlot slot) {
    return slot == null ? "" : stateKey(slot.getCharacterId(), slot.getDisplaySlot());
  }

  private String stateKey(String characterId, String displaySlot) {
    String slot = normalizeDisplaySlotId(displaySlot);
    if (!slot.isEmpty()) return "slot:" + slot;
    return normalizeCharacterId(characterId);
  }

  private void clearVisibleSlotState(CharacterPosition position) {
    CharacterSlot existing = position == null ? null : visibleCharacters.get(position);
    if (existing != null) clearSlotState(existing);
  }

  private void clearSlotState(CharacterSlot slot) {
    if (slot == null) return;
    String key = stateKey(slot);
    expressionTransitions.remove(key);
    detachedCharacters.remove(key);
    timelineDisplacements.remove(key);
    timelineTransforms.remove(key);
    eyeFocusRequests.remove(slot.getCharacterId());
  }

  private String normalizeExpression(String expression, String fallback) {
    if (expression == null || expression.isBlank()) return fallback == null ? "neutral" : fallback;
    return expression.trim();
  }

  private double positionDeltaOffset(CharacterPosition from, CharacterPosition to) {
    return to.moveDeltaFrom(from);
  }

  private double entranceOffsetX(CharacterPosition position) {
    return position.getEntranceOffsetX();
  }

  private int resolveLayerOrder(CharacterPosition position, Integer requestedLayerOrder, Integer fallbackLayerOrder) {
    if (requestedLayerOrder != null) return requestedLayerOrder;
    if (fallbackLayerOrder != null) return fallbackLayerOrder;
    return position != null ? position.getDefaultLayerOrder() : 0;
  }

  public record EyeFocusRequest(
      String characterId,
      String expression,
      String targetCharacterId,
      Double targetX,
      Double targetY,
      long durationMs,
      double strength,
      double deadZone
  ) {
    public EyeFocusRequest {
      characterId = characterId == null ? "" : characterId.trim();
      expression = expression == null || expression.isBlank() ? "neutral" : expression.trim();
      targetCharacterId = targetCharacterId == null ? "" : targetCharacterId.trim();
      durationMs = Math.max(0L, durationMs);
      strength = Double.isFinite(strength) ? strength : 1.0;
      deadZone = Double.isFinite(deadZone) ? deadZone : 0.12;
    }

    public boolean hasPointTarget() {
      return targetX != null && targetY != null
          && Double.isFinite(targetX)
          && Double.isFinite(targetY);
    }

    public boolean hasCharacterTarget() {
      return targetCharacterId != null && !targetCharacterId.isBlank();
    }
  }

  public boolean isWaitingForInput() { return waitingForInput; }
  public void setWaitingForInput(boolean waiting) { this.waitingForInput = waiting; }

  public int getTextRevealProgress() { return textRevealProgress; }
  public void setTextRevealProgress(int progress) { this.textRevealProgress = progress; }
  public void incrementTextReveal(int amount) { this.textRevealProgress += amount; }

  public void setVariable(String key, Object value) { variables.put(key, value); }
  public Object getVariable(String key) { return variables.get(key); }
  public VnPersistentStore getPersistentStore() { return persistentStore; }

  public Object getPersistentValue(String key) {
    return persistentStore.get(key);
  }

  public void setPersistentValue(String key, Object value) {
    persistentStore.put(key, value);
    syncPersistentVariableMirror();
  }

  public void removePersistentValue(String key) {
    persistentStore.remove(key);
    syncPersistentVariableMirror();
  }

  public double addPersistentValue(String key, double delta) {
    double result = persistentStore.add(key, delta);
    syncPersistentVariableMirror();
    return result;
  }

  public DialoguePresentationMode getDialoguePresentationMode() {
    return DialoguePresentationMode.fromVariable(variables.get(VAR_DIALOGUE_PRESENTATION_MODE));
  }

  public void setDialoguePresentationMode(DialoguePresentationMode mode) {
    DialoguePresentationMode resolved = mode == null ? DialoguePresentationMode.STANDARD : mode;
    variables.put(VAR_DIALOGUE_PRESENTATION_MODE, resolved.token());
  }

  public void resetDialoguePresentationMode() {
    setDialoguePresentationMode(DialoguePresentationMode.STANDARD);
  }

  public BubbleAnchor getBubbleAnchorPreference(String characterId) {
    if (characterId == null || characterId.isBlank()) return BubbleAnchor.AUTO;
    return BubbleAnchor.fromVariable(variables.get(VAR_BUBBLE_ANCHOR_PREFIX + characterId.trim()));
  }

  public void setBubbleAnchorPreference(String characterId, BubbleAnchor anchor) {
    if (characterId == null || characterId.isBlank()) return;
    String key = VAR_BUBBLE_ANCHOR_PREFIX + characterId.trim();
    BubbleAnchor resolved = anchor == null ? BubbleAnchor.AUTO : anchor;
    if (resolved == BubbleAnchor.AUTO) {
      variables.remove(key);
    } else {
      variables.put(key, resolved.token());
    }
  }

  public double getBubbleOffsetXPreference(String characterId) {
    return readBubbleOffset(characterId, VAR_BUBBLE_OFFSET_X_PREFIX);
  }

  public double getBubbleOffsetYPreference(String characterId) {
    return readBubbleOffset(characterId, VAR_BUBBLE_OFFSET_Y_PREFIX);
  }

  public void setBubbleOffsetPreference(String characterId, double x, double y) {
    if (characterId == null || characterId.isBlank()) return;
    String id = characterId.trim();
    variables.put(VAR_BUBBLE_OFFSET_X_PREFIX + id, x);
    variables.put(VAR_BUBBLE_OFFSET_Y_PREFIX + id, y);
  }

  public void clearBubblePlacementPreference(String characterId) {
    if (characterId == null || characterId.isBlank()) return;
    String id = characterId.trim();
    variables.remove(VAR_BUBBLE_ANCHOR_PREFIX + id);
    variables.remove(VAR_BUBBLE_OFFSET_X_PREFIX + id);
    variables.remove(VAR_BUBBLE_OFFSET_Y_PREFIX + id);
  }

  public void jumpToLabel(String label) {
    if (scenario != null) {
      Integer index = scenario.getLabelIndex(label);
      if (index != null) {
        currentNodeIndex = index;
      }
    }
  }

  public boolean isScenarioComplete() {
    if (scenario == null) return true;
    VnNode node = getCurrentNode();
    return node == null || node.getType() == VnNodeType.END;
  }

  public VnHistory getHistory() { return history; }
  public VnSettings getSettings() { return settings; }

  public boolean isSkipMode() { return skipMode; }
  public void setSkipMode(boolean skip) { this.skipMode = skip; }

  public boolean isAutoPlayMode() { return autoPlayMode; }
  public void setAutoPlayMode(boolean auto) { this.autoPlayMode = auto; }

  public long getAutoPlayTimer() { return autoPlayTimer; }
  public void setAutoPlayTimer(long timer) { this.autoPlayTimer = timer; }
  public void incrementAutoPlayTimer(long delta) { this.autoPlayTimer += delta; }
  public void resetAutoPlayTimer() { this.autoPlayTimer = 0; }

  public boolean isNodeRead(int nodeIndex) { return readNodes.contains(nodeIndex); }
  public void markNodeAsRead(int nodeIndex) { readNodes.add(nodeIndex); }

  public VnTransition getActiveTransition() { return activeTransition; }
  public void setActiveTransition(VnTransition transition) { 
    this.activeTransition = transition;
    this.transitionStartTime = System.currentTimeMillis();
    this.transitionPausedAt = 0L;
    this.previousBackgroundIdDuringTransition = this.currentBackgroundId;
  }
  public void clearActiveTransition() {
    this.activeTransition = null;
    this.transitionPausedAt = 0L;
  }
  
  public long getTransitionStartTime() { return transitionStartTime; }
  public float getTransitionProgress() {
    if (activeTransition == null) return 1.0f;
    long now = transitionPausedAt > 0L ? transitionPausedAt : System.currentTimeMillis();
    long elapsed = now - transitionStartTime;
    return Math.min(1.0f, elapsed / (float) activeTransition.getDurationMs());
  }

  public void pauseVisualClock() {
    if (activeTransition != null && transitionPausedAt == 0L) {
      transitionPausedAt = System.currentTimeMillis();
    }
  }

  public void resumeVisualClock() {
    if (transitionPausedAt <= 0L) return;
    transitionStartTime += Math.max(0L, System.currentTimeMillis() - transitionPausedAt);
    transitionPausedAt = 0L;
  }

  public String getPreviousBackgroundIdDuringTransition() { return previousBackgroundIdDuringTransition; }
  public void clearPreviousBackgroundIdDuringTransition() { this.previousBackgroundIdDuringTransition = null; }

  public boolean isUiHidden() { return uiHidden; }
  public void setUiHidden(boolean hidden) { this.uiHidden = hidden; }
  public void toggleUiHidden() { this.uiHidden = !this.uiHidden; }

  public boolean isHistoryOverlayShown() { return historyOverlayShown; }
  public void setHistoryOverlayShown(boolean shown) { this.historyOverlayShown = shown; }
  public void toggleHistoryOverlay() { this.historyOverlayShown = !this.historyOverlayShown; }

  public int getHistoryScroll() { return Math.max(0, historyScroll); }
  public void clearHistoryScroll() { this.historyScroll = 0; }
  public void scrollHistoryByLines(int delta) {
    this.historyScroll = Math.max(0, this.historyScroll + delta);
  }

  // Save slot overlay
  public boolean isSaveSlotOverlayShown() { return saveSlotOverlayShown; }
  public void setSaveSlotOverlayShown(boolean shown) { this.saveSlotOverlayShown = shown; }
  public void showSaveSlotOverlay(boolean isSaveMode) {
    this.saveSlotOverlayShown = true;
    this.saveSlotOverlayIsSaveMode = isSaveMode;
    this.saveSlotSelected = 0;
  }
  public void hideSaveSlotOverlay() { this.saveSlotOverlayShown = false; }
  public boolean isSaveSlotOverlaySaveMode() { return saveSlotOverlayIsSaveMode; }
  public int getSaveSlotSelected() { return saveSlotSelected; }
  public void setSaveSlotSelected(int slot) { this.saveSlotSelected = Math.max(0, Math.min(9, slot)); }
  public void moveSaveSlotSelection(int delta) {
    int newSlot = saveSlotSelected + delta;
    if (newSlot < 0) newSlot = 9;
    if (newSlot > 9) newSlot = 0;
    this.saveSlotSelected = newSlot;
  }

  public String getHudMessage() { return hudMessage; }
  public long getHudMessageExpireAt() { return hudMessageExpireAt; }
  public void showHudMessage(String message, long durationMs) {
    this.hudMessage = VnTextFormatter.format(message, variables);
    this.hudMessageExpireAt = System.currentTimeMillis() + Math.max(0, durationMs);
  }

  public void triggerScreenShake(float intensity, long durationMs) {
    this.screenShakeIntensity = Math.max(0f, intensity);
    this.screenShakeDurationMs = Math.max(0L, durationMs);
    this.screenShakeRemainingMs = this.screenShakeDurationMs;
  }

  public float getScreenShakeMagnitude() {
    if (screenShakeRemainingMs <= 0 || screenShakeDurationMs <= 0) return 0f;
    float t = screenShakeRemainingMs / (float) screenShakeDurationMs;
    return screenShakeIntensity * t;
  }

  public void triggerFlash(float r, float g, float b, float strength, long durationMs) {
    this.flashR = clamp01(r);
    this.flashG = clamp01(g);
    this.flashB = clamp01(b);
    this.flashStrength = Math.max(0f, strength);
    this.flashDurationMs = Math.max(0L, durationMs);
    this.flashRemainingMs = this.flashDurationMs;
  }

  public float getFlashAlpha() {
    if (flashRemainingMs <= 0 || flashDurationMs <= 0) return 0f;
    float t = flashRemainingMs / (float) flashDurationMs;
    return flashStrength * t;
  }

  public float getFlashR() { return flashR; }
  public float getFlashG() { return flashG; }
  public float getFlashB() { return flashB; }

  public void updateScreenEffects(long deltaMs) {
    if (screenShakeRemainingMs > 0) {
      screenShakeRemainingMs = Math.max(0L, screenShakeRemainingMs - deltaMs);
    }
    if (flashRemainingMs > 0) {
      flashRemainingMs = Math.max(0L, flashRemainingMs - deltaMs);
    }
  }

  // ── Particle effects ──────────────────────────────────────────────────
  private VnParticleCommand activeParticleCommand;
  private long activeParticleRemainingMs = 0L;

  public VnParticleCommand getActiveParticleCommand() { return activeParticleCommand; }
  public long getActiveParticleRemainingMs() { return activeParticleRemainingMs; }

  public void setActiveParticleCommand(VnParticleCommand cmd) {
    this.activeParticleCommand = cmd;
    this.activeParticleRemainingMs = cmd == null ? 0L : cmd.getDurationMs();
  }

  public void clearParticleEffect() {
    this.activeParticleCommand = null;
    this.activeParticleRemainingMs = 0L;
  }

  public void updateParticleEffect(long deltaMs) {
    if (activeParticleCommand == null || activeParticleRemainingMs <= 0L) return;
    activeParticleRemainingMs = Math.max(0L, activeParticleRemainingMs - Math.max(0L, deltaMs));
    if (activeParticleRemainingMs <= 0L) {
      activeParticleCommand = null;
    }
  }

  public Map<String, Object> getVariables() { return variables; }
  public String getActiveStagePresetId() {
    Object value = variables.get(VAR_ACTIVE_STAGE_PRESET_ID);
    return value == null ? null : String.valueOf(value);
  }
  public void setActiveStagePresetId(String stagePresetId) {
    if (stagePresetId == null || stagePresetId.isBlank()) {
      variables.remove(VAR_ACTIVE_STAGE_PRESET_ID);
    } else {
      variables.put(VAR_ACTIVE_STAGE_PRESET_ID, stagePresetId.trim());
    }
  }
  public void setVariables(Map<String, Object> vars) {
    this.variables.clear();
    if (vars != null) this.variables.putAll(vars);
    syncPersistentVariableMirror();
  }

  public Object getRpgState() { return rpgState; }
  public void setRpgState(Object rpgState) { this.rpgState = rpgState; }

  public Set<Integer> getReadNodes() { return new HashSet<>(readNodes); }
  public void setReadNodes(Set<Integer> read) {
    this.readNodes.clear();
    if (read != null) this.readNodes.addAll(read);
  }

  // --- Call stack for CALL/RETURN subroutine support ---

  public void pushCallStack(int returnIndex) {
    callStack.add(returnIndex);
  }

  public int popCallStack() {
    if (callStack.isEmpty()) return -1;
    return callStack.remove(callStack.size() - 1);
  }

  public boolean hasCallStack() {
    return !callStack.isEmpty();
  }

  public int getCallStackDepth() {
    return callStack.size();
  }

  public void clearCallStack() {
    callStack.clear();
  }

  public List<Integer> getCallStackSnapshot() {
    return new ArrayList<>(callStack);
  }

  public void setCallStack(List<Integer> stack) {
    callStack.clear();
    if (stack != null) callStack.addAll(stack);
  }

  // --- Rollback system ---

  public VnRollbackStack getRollbackStack() {
    return rollbackStack;
  }

  public void captureRollbackState(String speaker, String text) {
    rollbackStack.capture(this, speaker, text);
  }

  public boolean canRollback() {
    return rollbackStack.canRollback();
  }

  public boolean canRollforward() {
    return rollbackStack.canRollforward();
  }

  public static class CharacterSlot {
    private final String characterId;
    private final String expression;
    private final int layerOrder;
    private final String displaySlot;

    public CharacterSlot(String characterId, String expression) {
      this(characterId, expression, 0);
    }

    public CharacterSlot(String characterId, String expression, int layerOrder) {
      this(characterId, expression, layerOrder, null);
    }

    public CharacterSlot(String characterId, String expression, int layerOrder, String displaySlot) {
      this.characterId = characterId;
      this.expression = expression;
      this.layerOrder = layerOrder;
      this.displaySlot = displaySlot == null ? "" : displaySlot.trim();
    }

    public String getCharacterId() { return characterId; }
    public String getExpression() { return expression; }
    public int getLayerOrder() { return layerOrder; }
    public String getDisplaySlot() { return displaySlot; }
  }

  public static class DetachedCharacterSlot {
    private final CharacterPosition basePosition;
    private final CharacterSlot slot;
    private final CharacterVisual visual;

    public DetachedCharacterSlot(CharacterPosition basePosition, CharacterSlot slot, CharacterVisual visual) {
      this.basePosition = basePosition == null ? CharacterPosition.CENTER : basePosition;
      this.slot = slot;
      this.visual = visual == null ? new CharacterVisual() : visual;
    }

    public CharacterPosition getBasePosition() { return basePosition; }
    public CharacterSlot getSlot() { return slot; }
    public CharacterVisual getVisual() { return visual; }
  }

  public static class TimelineDisplacement {
    private final double x;
    private final double y;
    private final boolean hasX;
    private final boolean hasY;

    public TimelineDisplacement(double x, double y, boolean hasX, boolean hasY) {
      this.x = Double.isFinite(x) ? x : 0.0;
      this.y = Double.isFinite(y) ? y : 0.0;
      this.hasX = hasX;
      this.hasY = hasY;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public boolean hasX() { return hasX; }
    public boolean hasY() { return hasY; }
  }

  public static class TimelineTransform {
    private final double x;
    private final double y;
    private final boolean hasX;
    private final boolean hasY;
    private final double scaleX;
    private final double scaleY;
    private final boolean hasScaleX;
    private final boolean hasScaleY;
    private final double rotationDeg;
    private final boolean hasRotation;
    private final double pivotX;
    private final double pivotY;
    private final boolean hasPivotX;
    private final boolean hasPivotY;

    public TimelineTransform(
        double x,
        double y,
        boolean hasX,
        boolean hasY,
        double scaleX,
        double scaleY,
        boolean hasScaleX,
        boolean hasScaleY,
        double rotationDeg,
        boolean hasRotation,
        double pivotX,
        double pivotY,
        boolean hasPivotX,
        boolean hasPivotY) {
      this.x = Double.isFinite(x) ? x : 0.0;
      this.y = Double.isFinite(y) ? y : 0.0;
      this.hasX = hasX;
      this.hasY = hasY;
      this.scaleX = Double.isFinite(scaleX) ? scaleX : 1.0;
      this.scaleY = Double.isFinite(scaleY) ? scaleY : 1.0;
      this.hasScaleX = hasScaleX;
      this.hasScaleY = hasScaleY;
      this.rotationDeg = Double.isFinite(rotationDeg) ? rotationDeg : 0.0;
      this.hasRotation = hasRotation;
      this.pivotX = Double.isFinite(pivotX) ? pivotX : 0.5;
      this.pivotY = Double.isFinite(pivotY) ? pivotY : 1.0;
      this.hasPivotX = hasPivotX;
      this.hasPivotY = hasPivotY;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public boolean hasX() { return hasX; }
    public boolean hasY() { return hasY; }
    public double getScaleX() { return scaleX; }
    public double getScaleY() { return scaleY; }
    public boolean hasScaleX() { return hasScaleX; }
    public boolean hasScaleY() { return hasScaleY; }
    public double getRotationDeg() { return rotationDeg; }
    public boolean hasRotation() { return hasRotation; }
    public double getPivotX() { return pivotX; }
    public double getPivotY() { return pivotY; }
    public boolean hasPivotX() { return hasPivotX; }
    public boolean hasPivotY() { return hasPivotY; }
  }

  public static class CharacterVisual {
    private double alpha = 1.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double startAlpha = 1.0;
    private double endAlpha = 1.0;
    private double startOffsetX = 0.0;
    private double startOffsetY = 0.0;
    private double endOffsetX = 0.0;
    private double endOffsetY = 0.0;
    private long durationMs = 1;
    private long elapsedMs = 0;
    private boolean animating = false;
    private boolean removeOnComplete = false;
    private Easing.Type easingType = null;

    public double getAlpha() { return alpha; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public boolean isRemoveOnComplete() { return removeOnComplete; }
    public boolean isFinished() { return !animating; }

    public void setOffsetX(double offsetX) { this.offsetX = offsetX; this.animating = false; }
    public void setOffsetY(double offsetY) { this.offsetY = offsetY; this.animating = false; }
    public void setAlpha(double alpha) { this.alpha = alpha; }

    public void setImmediate(double alpha, double offsetX, double offsetY) {
      this.alpha = alpha;
      this.offsetX = offsetX;
      this.offsetY = offsetY;
      this.animating = false;
      this.removeOnComplete = false;
    }

    public void startAnimation(double startAlpha, double endAlpha,
                               double startOffsetX, double endOffsetX,
                               double startOffsetY, double endOffsetY,
                               long durationMs, boolean removeOnComplete) {
      startAnimation(startAlpha, endAlpha, startOffsetX, endOffsetX, startOffsetY, endOffsetY, durationMs, removeOnComplete, null);
    }

    public void startAnimation(double startAlpha, double endAlpha,
                               double startOffsetX, double endOffsetX,
                               double startOffsetY, double endOffsetY,
                               long durationMs, boolean removeOnComplete,
                               Easing.Type easingType) {
      this.startAlpha = startAlpha;
      this.endAlpha = endAlpha;
      this.startOffsetX = startOffsetX;
      this.endOffsetX = endOffsetX;
      this.startOffsetY = startOffsetY;
      this.endOffsetY = endOffsetY;
      this.durationMs = Math.max(1L, durationMs);
      this.elapsedMs = 0L;
      this.animating = true;
      this.removeOnComplete = removeOnComplete;
      this.easingType = easingType;
      this.alpha = startAlpha;
      this.offsetX = startOffsetX;
      this.offsetY = startOffsetY;
    }

    public void update(long deltaMs) {
      if (!animating) return;
      elapsedMs += deltaMs;
      if (elapsedMs >= durationMs) {
        elapsedMs = durationMs;
      }
      double t = elapsedMs / (double) durationMs;
      double k = Easing.apply(easingType != null ? easingType : Easing.Type.EASE_OUT_QUAD, t);
      alpha = lerp(startAlpha, endAlpha, k);
      offsetX = lerp(startOffsetX, endOffsetX, k);
      offsetY = lerp(startOffsetY, endOffsetY, k);
      if (elapsedMs >= durationMs) {
        animating = false;
        alpha = endAlpha;
        offsetX = endOffsetX;
        offsetY = endOffsetY;
      }
    }

    private double lerp(double a, double b, double t) {
      return a + (b - a) * t;
    }
  }

  public static class ExpressionTransition {
    private final String fromExpression;
    private final String toExpression;
    private final long durationMs;
    private final Easing.Type easingType;
    private final LayeredCharacterResolver.ExpressionLayerDiff layerDiff;
    private long elapsedMs = 0L;

    private ExpressionTransition(String fromExpression, String toExpression, long durationMs, Easing.Type easingType) {
      this(fromExpression, toExpression, durationMs, easingType, null);
    }

    private ExpressionTransition(String fromExpression, String toExpression, long durationMs, Easing.Type easingType,
        LayeredCharacterResolver.ExpressionLayerDiff layerDiff) {
      this.fromExpression = fromExpression == null || fromExpression.isBlank() ? "neutral" : fromExpression.trim();
      this.toExpression = toExpression == null || toExpression.isBlank() ? "neutral" : toExpression.trim();
      this.durationMs = Math.max(1L, durationMs);
      this.easingType = easingType;
      this.layerDiff = layerDiff;
    }

    public String getFromExpression() { return fromExpression; }
    public String getToExpression() { return toExpression; }
    /** Per-layer diff between the from/to expressions, or {@code null} when the character has no layer data. */
    public LayeredCharacterResolver.ExpressionLayerDiff getLayerDiff() { return layerDiff; }
    public boolean isFinished() { return elapsedMs >= durationMs; }

    public double getProgress() {
      double raw = elapsedMs / (double) durationMs;
      double clamped = Math.max(0.0, Math.min(1.0, raw));
      return Easing.apply(easingType != null ? easingType : Easing.Type.EASE_OUT_QUAD, clamped);
    }

    private void update(long deltaMs) {
      elapsedMs = Math.min(durationMs, elapsedMs + Math.max(0L, deltaMs));
    }

    public boolean appliesTo(String expression) {
      return toExpression.equals(expression);
    }
  }

  private static class PendingExpressionSwitch {
    private final String characterId;
    private final String displaySlot;
    private final String expression;
    private final long transitionDurationMs;
    private final Easing.Type easingType;
    private long remainingMs;

    private PendingExpressionSwitch(String characterId,
                                    String displaySlot,
                                    String expression,
                                    long delayMs,
                                    long transitionDurationMs,
                                    Easing.Type easingType) {
      this.characterId = characterId;
      this.displaySlot = displaySlot == null ? "" : displaySlot.trim();
      this.expression = expression == null || expression.isBlank() ? "neutral" : expression.trim();
      this.transitionDurationMs = Math.max(0L, transitionDurationMs);
      this.easingType = easingType;
      this.remainingMs = Math.max(1L, delayMs);
    }

    private boolean tick(long deltaMs) {
      remainingMs = Math.max(0L, remainingMs - Math.max(0L, deltaMs));
      return remainingMs <= 0L;
    }

    private boolean matches(CharacterSlot slot) {
      if (slot == null) return false;
      String slotId = slot.getDisplaySlot() == null ? "" : slot.getDisplaySlot().trim();
      return characterId.equals(slot.getCharacterId()) && displaySlot.equals(slotId);
    }
  }

  // --- Timeline runner management ---

  public void addTimelineRunner(TimelineRunner runner) {
    if (runner != null) activeTimelines.add(runner);
  }

  public void updateTimelineRunners(long deltaMs) {
    if (activeTimelines.isEmpty()) return;
    List<TimelineRunner> snapshot = new ArrayList<>(activeTimelines);
    List<TimelineRunner> finished = new ArrayList<>();
    for (TimelineRunner runner : snapshot) {
      if (runner == null) continue;
      runner.update(deltaMs);
      if (runner.isFinished()) finished.add(runner);
    }
    activeTimelines.removeAll(finished);
  }

  public boolean hasActiveTimelines() {
    return !activeTimelines.isEmpty();
  }

  public List<TimelineRunner> getActiveTimelines() {
    return activeTimelines;
  }

  public List<VnOverlayScreenSpec> getOverlayScreens() {
    return java.util.Collections.unmodifiableList(overlayScreens);
  }

  public boolean hasOverlayScreen(String id) {
    if (id == null || id.isBlank()) return false;
    for (VnOverlayScreenSpec screen : overlayScreens) {
      if (screen != null && id.trim().equals(screen.getId())) return true;
    }
    return false;
  }

  public boolean hasOverlayScreens() {
    return !overlayScreens.isEmpty();
  }

  public boolean hasModalOverlayScreen() {
    for (int i = overlayScreens.size() - 1; i >= 0; i--) {
      VnOverlayScreenSpec screen = overlayScreens.get(i);
      if (screen != null && screen.isModal()) return true;
    }
    return false;
  }

  public VnOverlayScreenSpec getTopOverlayScreen() {
    return overlayScreens.isEmpty() ? null : overlayScreens.get(overlayScreens.size() - 1);
  }

  public void showOverlayScreen(VnOverlayScreenSpec screen) {
    if (screen == null) return;
    hideOverlayScreen(screen.getId());
    overlayScreens.add(screen);
  }

  public void hideOverlayScreen(String id) {
    if (id == null || id.isBlank()) return;
    overlayScreens.removeIf(screen -> screen != null && id.trim().equals(screen.getId()));
  }

  public void clearOverlayScreens() {
    overlayScreens.clear();
  }

  public void returnOverlayScreen(String id, String returnValue) {
    VnOverlayScreenSpec target = null;
    if (id != null && !id.isBlank()) {
      for (int i = overlayScreens.size() - 1; i >= 0; i--) {
        VnOverlayScreenSpec screen = overlayScreens.get(i);
        if (screen != null && id.trim().equals(screen.getId())) {
          target = screen;
          break;
        }
      }
    } else {
      target = getTopOverlayScreen();
    }
    if (target == null) return;
    String returnKey = target.getReturnKey();
    if (returnKey != null && !returnKey.isBlank()) {
      variables.put(returnKey, returnValue == null ? "" : returnValue);
      variables.put("screen.return", returnValue == null ? "" : returnValue);
    }
    hideOverlayScreen(target.getId());
  }

  public void dismissTopOverlayScreen() {
    VnOverlayScreenSpec top = getTopOverlayScreen();
    if (top == null) return;
    if (top.isCallScreen()) {
      returnOverlayScreen(top.getId(), "");
    } else {
      hideOverlayScreen(top.getId());
    }
  }

  public void updateOverlayScreens(long deltaMs) {
    if (overlayScreens.isEmpty()) return;
    List<VnOverlayScreenSpec> expired = new ArrayList<>();
    for (VnOverlayScreenSpec screen : overlayScreens) {
      if (screen instanceof VnReactiveOverlayScreenSpec reactive && !reactive.isVisibleNow()) {
        expired.add(screen);
        continue;
      }
      if (screen != null && screen.tick(deltaMs)) expired.add(screen);
    }
    for (VnOverlayScreenSpec screen : expired) {
      String action = screen.getTimerAction() == null ? "hide" : screen.getTimerAction().trim().toLowerCase();
      if ("return".equals(action)) {
        returnOverlayScreen(screen.getId(), screen.getTimerTarget());
      } else {
        hideOverlayScreen(screen.getId());
      }
    }
  }

  private float clamp01(float v) {
    if (v < 0f) return 0f;
    if (v > 1f) return 1f;
    return v;
  }

  private double readBubbleOffset(String characterId, String prefix) {
    if (characterId == null || characterId.isBlank()) return 0.0;
    Object value = variables.get(prefix + characterId.trim());
    if (value instanceof Number n) return n.doubleValue();
    if (value instanceof String s) {
      try {
        return Double.parseDouble(s.trim());
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        return 0.0;
      }
    }
    return 0.0;
  }

  private void syncPersistentVariableMirror() {
    for (String key : mirroredPersistentVariables) {
      variables.remove(key);
    }
    mirroredPersistentVariables.clear();
    for (Map.Entry<String, Object> entry : persistentStore.snapshot().entrySet()) {
      String key = entry.getKey();
      if (key == null || key.isBlank()) continue;
      String mirrorKey = "persistent." + key.trim();
      mirroredPersistentVariables.add(mirrorKey);
      variables.put(mirrorKey, entry.getValue());
    }
  }
}
