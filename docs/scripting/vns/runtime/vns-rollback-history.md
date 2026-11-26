# VNS Rollback & History

Complete reference for the VN rollback (rewind/fast-forward) system and the dialogue history backlog.

Rollback stack: `modules/core/src/main/java/com/jvn/core/vn/rollback/VnRollbackStack.java`
Rollback entry: `modules/core/src/main/java/com/jvn/core/vn/rollback/VnRollbackEntry.java`
History: `modules/core/src/main/java/com/jvn/core/vn/VnHistory.java`

---

## Overview

JVN provides two complementary systems for reviewing past story content:

- **Rollback** — rewinds or fast-forwards the entire VN state to a previous/future dialogue point. Restores all visuals, characters, variables, and modes exactly as they were.
- **History** — a read-only log of speaker/text pairs for scrolling through past dialogue without changing game state.

---

## Rollback System

### How It Works

Every time a **dialogue node** is processed, the engine captures a snapshot of the full VN state and pushes it onto the rollback stack. This snapshot includes:

| Captured State | Description |
|---------------|-------------|
| `nodeIndex` | Position in the node list |
| `backgroundId` | Current background |
| `variables` | Full variable map (deep copy) |
| `visibleCharacters` | Character positions, expressions, layer orders, and display slot ids |
| `readNodes` | Which nodes have been visited |
| `callStack` | Subroutine return addresses |
| `globalPositionCharacters` | Characters with global positioning |
| `characterDefinedPositions` | Character → last assigned position |
| `skipMode` | Skip mode state |
| `autoPlayMode` | Auto-play mode state |
| `autoPlayTimer` | Auto-play countdown |
| `uiHidden` | UI visibility toggle |
| `dialogueSpeaker` | Speaker name at this point |
| `dialogueText` | Dialogue text at this point |
| `timestamp` | Capture time (epoch millis) |

### Stack Architecture

- **History stack** — past rollback entries (newest on top)
- **Future stack** — entries that were rolled past (for forward navigation)
- **Max entries** — oldest entries are pruned when the limit is exceeded (default: 100)

### Capturing State

State is captured automatically after each dialogue node:

```java
// Called internally by VnScene.processDialogueNode()
state.captureRollbackState(speaker, text);
```

This calls:

```java
rollbackStack.capture(state, speaker, text);
```

Which creates an immutable `VnRollbackEntry` snapshot.

### Rolling Back

```java
if (state.canRollback()) {
    // Capture current state for the future stack
    VnRollbackEntry current = VnRollbackEntry.capture(state, currentSpeaker, currentText);
    VnRollbackEntry previous = rollbackStack.rollback(current);
    previous.applyTo(state);
}
```

When rolling back:
1. The current state is pushed onto the **future stack** (enabling forward)
2. The top of the **history stack** is popped
3. The popped entry is applied to `VnState`, restoring everything

### Rolling Forward

```java
if (state.canRollforward()) {
    VnRollbackEntry current = VnRollbackEntry.capture(state, currentSpeaker, currentText);
    VnRollbackEntry next = rollbackStack.rollforward(current);
    next.applyTo(state);
}
```

The inverse: current → history, future → applied.

### State Restoration Details

When `applyTo(state)` is called, it restores:

1. Node index and background
2. Variables (full map replacement)
3. Read nodes set
4. Call stack
5. All visible characters (cleared first, then re-shown with correct layer order and display slot identity)
6. Global position state
7. Skip mode, auto-play mode, and timers
8. UI hidden state
9. Resets: `waitingForInput=false`, `textRevealProgress=0`, history overlay closed, save slot overlay closed

### New Path Behavior

When a new dialogue is reached (new `push()`), the **future stack is cleared**. This matches the undo/redo pattern — once you take a new action after undoing, you can't redo the old path.

### Configuration

```java
// Default: 100 entries
VnRollbackStack stack = new VnRollbackStack();

// Custom depth
VnRollbackStack stack = new VnRollbackStack(200);
```

### API Reference

| Method | Description |
|--------|-------------|
| `push(entry)` | Push entry, clear future stack |
| `capture(state, speaker, text)` | Capture and push current state |
| `rollback(currentEntry)` | Roll back one step, returns entry to restore |
| `rollforward(currentEntry)` | Roll forward one step, returns entry to restore |
| `canRollback()` | True if history stack is non-empty |
| `canRollforward()` | True if future stack is non-empty |
| `peek()` | View top entry without removing |
| `size()` | Number of entries in history |
| `futureSize()` | Number of entries in future stack |
| `clear()` | Clear both stacks |
| `clearFuture()` | Clear only the future stack |
| `getMaxEntries()` | Get max depth limit |

---

## Default Key Bindings

| Key | Action |
|-----|--------|
| **Scroll up** / **Page Up** / **Backquote** | Roll back in the JavaFX player |
| **Page Down** | Roll forward |

To customize rollback, edit `~/.jvn/input-bindings.properties` or the configured input-profile path. For example:

```properties
rollback=MOUSE_WHEEL|0||UP,KEYBOARD|0||PAGE_UP
```

Remove the wheel entry for keyboard-only rollback. Use `rollback=` to disable all rollback bindings. You can bind `MOUSE_WHEEL|0||DOWN` to `rollforward` as well. Restart the player after editing the file. If you previously loaded an Input Profile through Settings, reload it from disk before restarting. The history menu uses the wheel to move through its log.

---

## History Backlog

### How It Works

Every dialogue node adds a `(speaker, text)` pair to the history log. This is independent of the rollback system — it is a simple, append-only chronological record.

### VnHistory API

```java
VnHistory history = state.getHistory();

// Entries are added automatically by VnScene
history.addEntry("Hero", "Let's go!");

// Read entries (newest last)
List<VnHistory.HistoryEntry> entries = history.getEntries();
for (VnHistory.HistoryEntry e : entries) {
    String speaker = e.getSpeaker();   // may be null for narration
    String text = e.getText();
    long timestamp = e.getTimestamp();  // epoch millis
}

// Stats
int count = history.size();

// Clear (e.g., on new game)
history.clear();
```

### Max Entries

Default: **200** entries. Oldest entries are pruned when the limit is exceeded.

```java
VnHistory history = new VnHistory(500); // Custom max
```

### History Overlay

The renderer provides a scrollable history overlay:

| Key | Action |
|-----|--------|
| **L** / **Backspace** | Toggle history overlay |
| **Mouse Wheel** | Scroll history up/down |

The overlay shows:
- Timestamped speaker/text pairs
- Alternating row backgrounds for readability
- Header and footer with scroll hints
- Total entry count

### VNS Commands for History

```vns
# Toggle history overlay
[history toggle]

# Show history
[history show]

# Hide history
[history hide]
```

---

## Rollback vs History

| Feature | Rollback | History |
|---------|----------|---------|
| **Purpose** | Rewind/restore game state | Review past dialogue |
| **Changes state** | Yes — full state restoration | No — read-only log |
| **Characters** | Restored to exact positions/expressions | Not tracked |
| **Variables** | Restored | Not tracked |
| **Background** | Restored | Not tracked |
| **Direction** | Forward and backward | Scroll only |
| **Max entries** | 100 (configurable) | 200 (configurable) |
| **Cleared on** | New game | New game |

---

## Interaction with Save/Load

- **Save** captures the current state but does **not** save the rollback stack or history. After loading a save, both start empty.
- **Autosave** works the same — rollback/history are session-only.
- Rolling back to a point before a save does not affect the save file.

---

## Common Patterns

### Preventing Rollback Past Critical Points

If you want to prevent the player from rolling back past a major story branch:

```vns
@label point_of_no_return
narrator: There's no going back now.
# The rollback stack still exists, but the player can only
# roll back to the most recent 100 dialogue lines.
# For hard prevention, clear rollback from Java code.
```

### Dialogue-Only Rollback

Rollback snapshots are only created at **dialogue nodes**. Non-interactive nodes (backgrounds, audio, jumps) are not individually captured. This means rolling back skips over groups of non-interactive commands and lands on the previous dialogue line — which is the expected UX for visual novels.

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md)
- [Save System](vns-save-system.md)
- [Settings & Playback Modes](vns-settings-modes.md)
- [Scene Lifecycle & State](vns-scene-lifecycle.md)
