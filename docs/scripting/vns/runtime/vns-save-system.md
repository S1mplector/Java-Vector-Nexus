# VNS Save System

Complete reference for the VN save/load system — named slots, autosave, quick save/load, schema versioning, migration, JSON format, atomic writes, sidecar thumbnails, and RPG state passthrough.

Manager: `modules/core/src/main/java/com/jvn/core/vn/save/VnSaveManager.java`
Data model: `modules/core/src/main/java/com/jvn/core/vn/save/VnSaveData.java`
Migration: `modules/core/src/main/java/com/jvn/core/vn/save/VnSaveMigration.java`
Quick save: `modules/core/src/main/java/com/jvn/core/vn/VnQuickSaveManager.java`

---

## Overview

The VN save system persists the complete playback state of a visual novel to disk and restores it later. It supports:

- **Named save slots** — arbitrary slot names (e.g., `"slot_1"`, `"chapter_3_save"`)
- **Quick save/load** — single-key F5/F9 instant save and restore
- **Autosave** — rotating autosave slots (3 by default)
- **Schema migration** — automatic upgrade of older saves to the current format
- **Atomic writes** — crash-safe persistence via temp-file + rename
- **Legacy compatibility** — reads old `.sav` Java serialization files and migrates to JSON

---

## Save Directory

Default: `~/.jvn/games/<game-id>/saves/`

The runtime gets `<game-id>` from `jvn.project`. Older manifests without an
explicit `id` derive the same value from `author` and `name`, so moving an
installation does not move its saves.

Custom:

```java
VnSaveManager manager = new VnSaveManager("/path/to/saves");
```

The directory is created automatically if it does not exist.

---

## Save Data Model

`VnSaveData` captures the complete playback state:

| Field | Type | Description |
|-------|------|-------------|
| `schemaVersion` | int | Schema version (current: 6) |
| `scenarioId` | String | Script identifier (`@scenario` ID) |
| `currentNodeIndex` | int | Position in the node list |
| `currentBackgroundId` | String | Active background |
| `variables` | Map<String, Object> | Script variables |
| `readNodes` | Set<Integer> | Which nodes have been visited |
| `visibleCharacters` | Map<String, String[]> | Render position → [charId, expression, layerOrder, displaySlot, basePosition, x, y, custom] |
| `callStack` | List<Integer> | Subroutine return addresses |
| `globalPositionCharacters` | Set<String> | Characters with global positioning enabled |
| `characterDefinedPositions` | Map<String, String> | Character → last assigned position |
| `skipMode` | boolean | Skip mode active |
| `autoPlayMode` | boolean | Auto-play mode active |
| `autoPlayTimer` | long | Current auto-play countdown |
| `uiHidden` | boolean | UI visibility toggle state |
| `settings` | SettingsData | All VnSettings values |
| `rpgState` | Object | Optional RPG state passthrough |
| `saveTimestamp` | long | Epoch millis when saved |
| `saveName` | String | Slot name |

### Settings Data

Nested `SettingsData` preserves all player preferences:

| Field | Default | Description |
|-------|---------|-------------|
| `textSpeed` | 30 | Ms per character |
| `bgmVolume` | 0.7 | BGM volume (0–1) |
| `sfxVolume` | 0.8 | SFX volume (0–1) |
| `voiceVolume` | 1.0 | Voice volume (0–1) |
| `autoPlayDelay` | 2000 | Ms to wait before auto-advancing |
| `skipUnreadText` | false | Allow skipping unread dialogue |
| `skipAfterChoices` | false | Continue skip mode after choices |
| `clickRevealBeforeAdvance` | true | First click reveals full text, second advances |
| `physicsFixedStepMs` | 0 | JES physics step (0 = variable) |
| `physicsMaxSubSteps` | 4 | Max physics sub-steps |
| `physicsDefaultFriction` | 0.2 | Default friction |
| `inputProfilePath` | `~/.jvn/input-bindings.properties` | Input bindings file |
| `inputProfileSerialized` | `""` | Serialized bindings |
| `displayWidth` | 1920 | Preferred display width |
| `displayHeight` | 1080 | Preferred display height |
| `autoFitResolution` | false | Fit the game viewport to the display |
| `accessibilityTheme` | `none` | Active accessibility theme |
| `textToSpeechEnabled` | false | Self-voice new dialogue nodes |
| `uiFontScale` | 1.0 | VN and settings-menu text scale |

---

## Saving

### Named Save

```java
VnSaveManager manager = new VnSaveManager();
manager.save(state, "slot_1");
```

This captures all state fields from `VnState` and writes to `~/.jvn/games/<game-id>/saves/slot_1.json`.

### Quick Save (F5)

```java
VnQuickSaveManager qsm = new VnQuickSaveManager();
qsm.quickSave(state);
```

Writes to the reserved `_quicksave` slot.

### Autosave

```java
// Automatic rotating slot selection
qsm.autoSave(state);

// Explicit slot index
manager.autosave(state, 0); // _autosave_0
manager.autosave(state, 1); // _autosave_1
manager.autosave(state, 2); // _autosave_2
```

Autosave uses rotating slots (3 by default). The oldest slot is overwritten.

### From VNS Script

```vns
# Quick save from script
[save quick]

# Trigger autosave
[save auto]
```

---

## Loading

### Named Load

```java
VnSaveData data = manager.load("slot_1");
manager.applyToState(data, state);
```

### Quick Load (F9)

```java
boolean ok = qsm.applyQuickLoad(state, scenario);
```

Verifies the scenario ID matches before applying.

### Latest Autosave

```java
boolean ok = qsm.applyLatestAutoSave(state, scenario);
```

Selects the autosave with the newest timestamp.

### From VNS Script

```vns
# Quick load
[quickload]
```

---

## State Restoration

When a save is loaded, `applyToState()` restores:

1. **Node position** — `currentNodeIndex`
2. **Background** — `currentBackgroundId`
3. **Variables** — full variable map
4. **Read nodes** — which dialogue has been seen
5. **Call stack** — subroutine return addresses
6. **Characters** — visible characters with positions, expressions, and layer order
7. **Global positioning** — character position memory
8. **UI state** — skip mode, auto-play, UI hidden
9. **Settings** — all VnSettings values
10. **RPG state** — optional game state payload
11. **Audio** — volumes are re-applied to audio facade

After restoration, `processCurrentNode()` is called to sync visuals with the loaded state.

---

## Listing and Managing Saves

```java
// List all save slot names
List<String> saves = manager.listSaves();

// List only autosave slots
List<String> autos = manager.listAutoSaves();

// Delete a save
manager.deleteSave("slot_1");

// Rename a save
manager.renameSave("slot_1", "chapter_3_checkpoint");

// Check quick save exists
boolean has = qsm.hasQuickSave();

// Check autosave exists
boolean hasAuto = qsm.hasAutoSave();
```

---

## Schema Migration

Saves are automatically migrated when loaded:

| Version | Changes |
|---------|---------|
| 1 → 2 | Added `saveTimestamp`, `settings` |
| 2 → 3 | Added `callStack`, `globalPositionCharacters`, `characterDefinedPositions` |
| 3 → 4 | Format normalization |

Migration is performed by `VnSaveMigration.migrateInPlace()`:

- Upgrades schema version step-by-step
- Normalizes null fields to empty defaults
- Sets missing timestamps to current time
- Writes back the migrated data to disk

Circular or forward migrations are safe — already-current saves pass through unchanged.

---

## File Format

### JSON (Current)

Saves are stored as `.json` files using `VnSaveSerializer`:

```text
~/.jvn/games/<game-id>/saves/slot_1.json
~/.jvn/games/<game-id>/saves/_quicksave.json
~/.jvn/games/<game-id>/saves/_autosave_0.json
```

### Legacy `.sav` (Backward Compatible)

Older saves using Java serialization (`.sav` extension) are read and automatically migrated to JSON. The legacy file is deleted after successful migration.

---

## Atomic Writes

All saves use atomic write-and-rename:

1. Write the complete JSON payload to a unique temporary file beside the slot.
2. Replace `<slot>.json` with the completed file, using an atomic move when the filesystem supports it.
3. If atomic rename is not supported, fall back to `REPLACE_EXISTING` move

This prevents corruption from crashes or power loss during write.

---

## Sidecar Thumbnails

Save slots can have associated screenshot thumbnails:

```text
~/.jvn/games/<game-id>/saves/slot_1.json      # Save data
~/.jvn/games/<game-id>/saves/slot_1.png       # Screenshot thumbnail
```

- Thumbnails are managed alongside saves
- `deleteSave()` removes both `.json` and `.png`
- `renameSave()` renames both files
- Menu UI uses `slotPreviewEnabled` to display thumbnails inline

---

## RPG State Passthrough

The `rpgState` field holds an optional serializable game state object (e.g., `RpgState`) that is saved and restored alongside VN state:

```java
state.setRpgState(myRpgState);
manager.save(state, "slot_1");

// Later...
VnSaveData data = manager.load("slot_1");
manager.applyToState(data, state);
RpgState restored = (RpgState) state.getRpgState();
```

---

## VNS Save Commands Reference

| Command | Description |
|---------|-------------|
| `[save quick]` | Quick save (F5 equivalent) |
| `[save auto]` | Trigger autosave |
| `[quickload]` | Quick load (F9 equivalent) |
| `[save slot <name>]` | Save to named slot |
| `[load slot <name>]` | Load from named slot |

---

## Common Patterns

### Checkpoint Save at Act Boundaries

```vns
@label act2_start
[save auto]
[bg city_night]
narrator: Act 2 begins...
```

### Pre-Choice Autosave

```vns
narrator: A critical choice approaches...
[save auto]

> Trust the stranger -> trust
> Walk away -> leave
```

### Quick Save/Load Hotkeys

The runtime binds these by default:

| Key | Action |
|-----|--------|
| **F5** | Quick save |
| **F9** | Quick load |

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md)
- [Settings & Playback Modes](vns-settings-modes.md)
- [Rollback & History](vns-rollback-history.md)
- [Scene Lifecycle & State](vns-scene-lifecycle.md)
- [Runtime Save System](../../../runtime/systems/save-system.md)
