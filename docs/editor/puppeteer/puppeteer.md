# Puppeteer — Animation Timeline Editor

Puppeteer is JVN's visual keyframe animation editor. It allows authors to create entity animations by placing keyframes on a timeline, previewing them in real-time, and exporting the result as JES timeline code that can be embedded in both JES scenes and VNS scripts.

This document covers the system architecture, the JES/VNS relationship, the data pipeline, and a usability guide.

---

## Who This Is For

Use Puppeteer when you need animation that is more deliberate than a simple VNS `[show]` or `[move]` command, but you do not want to hand-author JES timeline code.

Typical uses:

- character entrances and exits
- camera moves and shot timing
- layered multi-property animation
- mid-sequence expression swaps and sprite replacement beats
- cutaway/background swaps inside a timeline
- reusable timeline clips
- animation that should retain Scene Lighting Studio stage context
- staging and timing that needs preview before export

## What You Will Learn

This page is the orientation layer for Puppeteer. It explains:

- how Puppeteer fits between VNS and JES
- what data Puppeteer exports
- how preview, registration, and runtime playback relate
- which docs to open next for actual day-to-day authoring

## Read This Next

- Need hands-on usage: [Puppeteer Editor Guide](puppeteer-editor-guide.md)
- Need the export syntax: [Puppeteer JES DSL Reference](puppeteer-jes-dsl.md)
- Need reusable parameterized animation: [Puppeteer Motifs](puppeteer-motifs.md)
- New to JVN overall: [Choose Your Path in JVN](../../guides/choose-your-path.md)
- Need file-level orientation: [Common JVN File Types](../../guides/common-file-types.md)

---

## Sub-Document Reference

- **[Puppeteer Editor Guide](puppeteer-editor-guide.md)** — complete usage guide: launching, UI panels, selection-sidebar inspectors, keyframe editing, all 12 presets, 37 easing options, event cues, audio cues, animation clips, VN slot positions, camera animation, groups, layer ordering, orbit tool, onion skinning, code round-trip editing, timeline diagnostics, export workflows, keyboard shortcuts
- **[Puppeteer JES DSL Reference](puppeteer-jes-dsl.md)** — exported timeline code syntax: `move`, `rotate`, `scale`, `fade`, `pivot`, `depth`, `visible`, `cameraMove`, `cameraZoom`, generic `property` actions, `event` cues, `playAudio`, `wait`, `parallel`, easing values, spring functions, named curves, custom cubic Bézier, export modes, VNS/JES integration examples
- **[Puppeteer Motifs](puppeteer-motifs.md)** — named, parameterized, reusable animation fragments that expand into ordinary Puppeteer timeline actions
- **[Sidebar Utilities](../sidebars/overview/sidebar-utilities.md)** — current editor sidebar panels including Puppeteer Launcher, VNS Diagnostics, Asset Browser, and more

---

## 1. JES and VNS — The Two Scripting Layers

JVN has two scripting systems that operate at different abstraction levels. Understanding their relationship is essential to understanding how Puppeteer fits in.

### JES — The Low-Level Engine Layer

**JES (JVN Engine Script)** is a scene-description DSL (Domain Specific Language). It directly controls 2D entities, their properties, physics, input bindings, and timelines. A JES file describes *what exists* in a scene and *how it behaves* frame-by-frame.

```jes
scene "BattleIntro" {
  entity "hero" {
    component Sprite2D {
      image: "sprites/hero.png"
      x: 200
      y: 400
      width: 128
      height: 256
    }
  }

  timeline "hero_entrance" {
    target: "hero"
    0ms   { x: -200, alpha: 0.0 }
    500ms { x: 200,  alpha: 1.0, easing: ease_out }
  }
}
```

Key characteristics:

- JES is **entity-centric** in the sense that everything is an `Entity2D` with typed properties (x, y, rotation, scaleX, scaleY, alpha)
- It allows for **frame-level control** — timelines interpolate properties between keyframes with configurable easing
- **Imperative** — JES scenes update every frame, running physics, input, and timeline ticks
- **Scene graph** — `JesScene2D` manages named entities, a camera, and an input system

Core files:
| File | Role |
|------|------|
| `scripting/.../jes/JesTokenizer.java` | Lexer |
| `scripting/.../jes/JesParser.java` | AST builder |
| `scripting/.../jes/JesLoader.java` | Materializes AST into `JesScene2D` |
| `scripting/.../jes/runtime/JesScene2D.java` | Runtime scene with named entity registry |

### VNS — The High-Level Narrative Layer

**VNS (Visual Novel Script)** is a dialogue-and-direction DSL. It controls *story flow*: who speaks, what background is shown, where characters stand, which choices the player sees. VNS does not manage frame-by-frame animation — it issues commands and lets lower-level systems handle the rendering.

```vns
@scenario tutorial_dialogue
@character codel "Codel"
@background field_day demo-assets/demo_bg_field/field.jpg
@charimg codel neutral demo-assets/demo_sprite_codel/Codel1.png
@charimg codel talking demo-assets/demo_sprite_codel/Codel3.png

@label start
[bg field_day]
[show codel center neutral]

Codel: Welcome to the tutorial!
[show codel center talking]
Codel: Let me show you how things work.
```

Key characteristics:
- **Narrative-centric** — commands are about story beats (show character, change background, present choice)
- **Declarative positioning** — characters are placed at named slots (`left`, `center`, `right`, `far_left`, `far_right`) rather than pixel coordinates
- **State-driven** — `VnState` tracks visible characters, current background, variables, and dialogue progress
- **Asset declarations** — `@background` and `@charimg` map IDs to file paths in the script header

Core files:
| File | Role |
|------|------|
| `core/.../vn/script/VnScriptParser.java` | Parses `.vns` text into `VnScenario` |
| `core/.../vn/VnScene.java` | Drives node progression |
| `core/.../vn/VnState.java` | Mutable narrative state (characters, bg, vars) |
| `fx/.../vn/VnRenderer.java` | JavaFX Canvas renderer for VN scenes |

### How They Relate: JES Coordinates VNS

The relationship is hierarchical:

```
┌─────────────────────────────────────────┐
│                  VNS                    │
│  (story flow, dialogue, character       │
│   placement, choices, transitions)      │
│                                         │
│  Delegates animation to:                │
│  ┌─────────────────────────────────┐    │
│  │              JES                │    │
│  │  (entity properties, timelines, │    │
│  │   keyframes, easing, physics)   │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

**VNS can invoke JES timelines** for complex animations that go beyond simple show/hide/move:

```vns
[call jes_timeline hero_entrance]
```

This loads a named JES timeline from the `TimelineRegistry` and runs it against the current VN scene's entities using `TimelineRunner` and `SceneAccessor`.

**JES can return to VNS** via `call "return"` or `call "vns"` commands.

In practice:
- **VNS** handles the 95% case: dialogue, character placement, transitions
- **JES** handles the 5%: custom animations, particle effects, minigames, complex camera work, and low-level property/event playback
- **Puppeteer** is the visual tool that generates the JES timeline code, making it accessible to authors who don't want to hand-write keyframe data

---

## 2. System Architecture

### Data Pipeline Overview

```
VNS Script (.vns)          JES Scene (.jes)
      │                          │
      ▼                          ▼
PuppeteerLauncherPanel     FileEditorTab.getJesScene()
      │                          │
      ▼                          │
 resolveSnapshot()               │
 (parse @background,             │
  @charimg, @stagepreset,        │
  [show], [bg], [stage]          │
  commands up                    │
  to cursor line)                │
      │                          │
      ▼                          │
 SceneSnapshot                   │
 {backgroundId,                  │
  characters[],                  │
  backgroundPaths{},             │
  characterImagePaths{},         │
  activeStagePresetId}           │
      │                          │
      ▼                          │
EditorApp.buildSceneFromSnapshot()
      │                          │
      ▼                          ▼
           JesScene2D
           (named Sprite2D entities
            at VN slot positions)
                │
                ▼
        PuppeteerWindow
        {AnimationProject,
         StageContext,
         AnimationPreview,
         EntitySelector,
         TimelinePanel,
         KeyframeEditor,
         CodePreviewPane}
                │
                ▼
        User edits keyframes
                │
                ▼
        CodeExporter.export()
                │
                ▼
        JES timeline code
        (clipboard or register
         to TimelineRegistry)
                │
                ▼
        VNS: [call jes_timeline <name>]
        (TimelineRunner applies entity/camera
         keyframes + custom channels + event/audio cues via SceneAccessor)
```

### Key Classes

| Class | Module | Role |
|-------|--------|------|
| `PuppeteerLauncherPanel` | editor | Right-panel widget; parses VNS source to build scene snapshots; provides direct launch and registered-animation reopen flows |
| `PuppeteerWindow` | editor | `Stage` subclass; main Puppeteer UI; assembles all sub-panels |
| `AnimationProject` | editor | Data model: entity tracks, groups, keyframes, custom channels, event/audio cues, loop region, playback state, and optional stage context |
| `AnimationPreview` | editor | Canvas-based scene renderer with entity selection, drag-to-move, onion skinning |
| `EntitySelector` | editor | TreeView of named entities and groups |
| `TimelinePanel` | editor | Horizontal timeline with keyframe diamonds, playhead, entity rows |
| `KeyframeEditor` | editor | Property editor for selected keyframe (time, value, easing, sliders) |
| `CodePreviewPane` | editor | Live-updating JES code output |
| `CodeExporter` | editor | Converts `AnimationProject` to JES timeline syntax |
| `CodeImporter` | editor | Parses exported timeline code back into `AnimationProject`, including Puppeteer metadata |
| `AnimationPreset` | editor | 12 built-in animation templates (fade, slide, bounce, shake, etc.) |
| `PuppeteerCommand` | editor | Undo/redo command stack |
| `FxBlitter2D` | fx | Canvas renderer for `Entity2D` — handles image loading from classpath and filesystem |
| `TimelineData` | core | Serializable timeline representation for registry transport |
| `TimelineRegistry` | core | Static registry mapping names to `TimelineData` for VNS interop |
| `TimelineRunner` | core | Applies `TimelineData` keyframes, custom numeric channels, and event/audio cues via `SceneAccessor` |
| `SceneAccessor` | core | Interface decoupling `TimelineRunner` from `JesScene2D` — allows JES scenes and VN scenes to share the same timeline runtime |

### Image Loading Pipeline

When Puppeteer renders entities from a VNS snapshot, images must be loaded from the filesystem (not just the classpath). The loading chain:

```
Sprite2D.render(blitter)
    └─▶ FxBlitter2D.drawImage(path, ...)
           └─▶ loadImage(path)
                  1. ClassLoader.getResource(path)     ← classpath
                  2. new File(path).exists()            ← absolute path
                  3. new File(projectRoot, path).exists() ← project-relative
```

`projectRoot` is set by `EditorApp` → `PuppeteerWindow.setProjectRoot()` → `AnimationPreview.setProjectRoot()` → `FxBlitter2D.setProjectRoot()`.

### VNS Snapshot Resolution

`PuppeteerLauncherPanel.resolveSnapshot(source, cursorLine)` performs a lightweight parse of VNS text without building a full `VnScenario`. It scans lines 0..cursorLine for:

| Pattern | What it captures |
|---------|-----------------|
| `@label <name>` | Current label context |
| `@background <id> <path>` | Background ID → file path mapping |
| `@charimg <charId> <expr> <path>` | Character expression → file path mapping |
| `@charlayer <charId> <layerId> <path>` | Layered character asset mapping |
| `@chargroup <charId> <groupId> <spec>` | Movable layer group mapping for nested character rigs |
| `@charpreset <charId> <expr> <spec>` | Composite preset resolution against declared layers |
| `@stagepreset <id> <path>` | Stage preset ID -> file path mapping |
| `[bg <id>]` / `[background <id>]` | Active background ID |
| `[stage <id>]` / `[stage preset=<id>]` | Active stage preset |
| `[stage clear]` / `[stage off]` / `[stage none]` | Clears active stage preset |
| `[show <charId> <position> <expr?>]` | Visible character with slot + expression |
| `[hide <charId>]` | Character removal |
| `[char <id> show/hide/move/expr ...]` | External character commands |
| `timeline { ... }` / `@external jes_timeline <name>` | Inline or registered timeline context near the caret |

The result is a `SceneSnapshot` containing:
- `backgroundId` — which background is currently active
- `characters` — list of `{characterId, position, expression}` tuples
- `backgroundPaths` — map of background IDs to their declared file paths
- `characterImagePaths` — map of `"charId/expression"` keys to file paths
- `characterLayerPaths` — layered rig mappings used to resolve preset-backed expressions
- `characterLayerGroups` — `@chargroup` metadata used to create nested Puppeteer rig groups and runtime aliases
- `stagePresetPaths` — map of stage preset IDs to exported `.stagepreset` paths
- `activeStagePresetId` — active stage preset at the caret
- `referencedTimelineName` / inline timeline data — launch context for reopening vs creating new work
- `resolveBackgroundPath()` — looks up the active background's file path
- `resolveCharacterPath(id, expr)` — resolves the best matching sprite path with fallback chain: exact expression → neutral → any expression for that character

### Scene Construction from Snapshot

`EditorApp.buildSceneFromSnapshot()` converts a `SceneSnapshot` into a `JesScene2D`:

1. Creates background `Sprite2D` at center (1280×720, origin 0.5/0.5)
2. Creates character `Sprite2D` entities at VN slot positions:
   - `far_left` → 10% of scene width
   - `left` → 25%
   - `center` → 50%
   - `right` → 75%
   - `far_right` → 90%
   - Y position: 55% of scene height, origin at bottom-center (0.5, 1.0)
3. Registers each entity by name in the `JesScene2D` (e.g. `"codel"`, `"bg_field_day"`)
4. For layered characters, creates individual layer entities and `@chargroup` rig groups such as `"aria_head"` or `"aria_face"`.
5. If a stage is active, builds `AnimationProject.StageContext` from the stage preset so Puppeteer can display and export the lighting handoff.

### Stage Context Preservation

When launched from a VNS beat with an active `[stage ...]`, Puppeteer stores:

- preset ID
- resolved source path
- background/subject tags when available
- counts for lights, occluders, and response zones

Named exports include a compact metadata line:

```jes
// @jvn-puppeteer-stage id=sunset_park source=config%2Fstage%2Fsunset_park.stagepreset bg=park_day subject=hero lights=3 occluders=1 zones=4
```

`CodeImporter` reads that metadata back when reopening registered timelines. The Scene sidebar also shows the active Lighting Stage so authors know which lighting setup the animation was staged against.

Named exports also carry editor-only Puppeteer metadata for reopening the full
authoring model: scene entity snapshots, group hierarchy, track/group locks,
layer order, loop settings, constraints, named anchors, and orbit-anchor source
links. Runtime timeline parsers ignore these comments, but the editor uses them
to restore the project when a registered animation is reopened.

---

## 3. Timeline Registry and Bridging Puppeteer to VNS Runtime

The `TimelineRegistry` is the bridge between the editor (Puppeteer) and the runtime (VNS playback).

### Registration Flow

1. Author creates animation in Puppeteer
2. Types a name in the "Name" field (e.g. `hero_entrance`)
3. Clicks **Register** button
4. Puppeteer runs a runtime verification pass first
5. `AnimationProject.toTimelineData(name)` serializes keyframes into `TimelineData`
6. `TimelineRegistry.register(name, data)` stores it globally
7. The editor writes `scripts/timelines/<name>.jes` for project-side reuse

The verification step is important because preview-safe authoring is not always runtime-safe authoring. For example, Puppeteer now explicitly warns or blocks registration when:

- camera keyframes are spread across multiple tracks
- camera keyframes are mixed into a normal entity track instead of the dedicated runtime camera lane
- an animated group has no runtime child entities to bake into
- an audio cue references a file that is missing on disk

What is runtime-safe now is broader than before:

- grouped entity rigs: group X/Y, pivot, rotation, scale, depth, and alpha are baked into child tracks
- event cues for `expression`, `show`, `hide`, `replace`, and `scene`
- dedicated matrix, blur, color-matrix, and DOF channels
- registry-backed or freeform custom numeric channels routed through `applyCustomProperty`

### Consumption Flow

1. VNS script contains: `[call jes_timeline hero_entrance]`
2. `DefaultVnInterop` handles this command
3. Looks up `TimelineRegistry.get("hero_entrance")`
4. Creates a `TimelineRunner` with the `TimelineData`
5. `TimelineRunner` uses `SceneAccessor` to read/write entity properties, apply custom numeric channels, and fire event/audio cues
6. `VnState.updateTimelineRunners(deltaMs)` ticks all active runners each frame

The `SceneAccessor` interface decouples the runner from `JesScene2D`, allowing it to work with VN scene entities that aren't managed by JES.

---

## 4. Workflow Patterns

This section covers the practical workflows for different Puppeteer use cases. For detailed UI controls, see the [Puppeteer Editor Guide](puppeteer-editor-guide.md). For hand-coding without the editor, see [Hand-Coding Timelines](../../scripting/timeline/animation/timeline-hand-coding.md).

### Workflow A: VNS Character Animation (Most Common)

The typical visual novel workflow — animate characters at a specific story moment.

```text
1. Write VNS script with character positioning
   │  [show hero center happy]
   │  [show villain right angry]
   │  hero: I challenge you!
   │
2. Place cursor on the dialogue line
   │
3. Open Puppeteer Launcher (right sidebar)
   │  → Shows: hero @ center [happy], villain @ right [angry]
   │
4. Click `Launch @ Cursor`
   │  → Puppeteer opens with both characters at their VNS positions
   │
5. Author keyframes (drag entities, press K, apply presets)
   │  → optionally add event cues for expression swaps / scene beats
   │  → optionally use the Selection sidebar for matrix, color, DOF, and custom channels
   │
6. Name the timeline: "hero_challenge_entrance"
   │
7. Click "Register" → Puppeteer verifies, then registers and writes `scripts/timelines/<name>.jes`
   │
8. In VNS script, add:
   │  [call jes_timeline hero_challenge_entrance]
   │
9. At runtime, TimelineRunner animates the characters
```

**Important:** The snapshot captures state *up to the cursor line*. Place the cursor where characters are already visible (after `[show]` commands) but before the animation should play.

### Workflow B: JES Scene Animation

For game scenes, cutscenes, or any JES-based content.

```text
1. Write JES scene with entities
   │
2. Open the .jes file in the editor
   │
3. Launch Puppeteer from the launcher panel
   │  → Puppeteer opens with all JES entities
   │
4. Author keyframes
   │
5. Click "Copy Code" → JES timeline block on clipboard
   │
6. Paste into the .jes file as a named timeline:
   │  timeline "my_animation" {
   │    move "hero" { x: 400 dur: 500 easing: ease_out_cubic }
   │    ...
   │  }
```

### Workflow C: Inline VNS Timeline

For quick one-off animations that don't warrant a registry name.

```text
1. Author animation in Puppeteer (or hand-code)
   │
2. Click "Copy Code"
   │
3. Paste directly into VNS script as inline block:
   │  narrator: Watch this!
   │  timeline {
   │    move "hero" { x: 500 dur: 400 easing: ease_out_cubic }
   │    fade "hero" { alpha: 1 dur: 300 }
   │  }
   │  hero: Here I am!
```

Inline `timeline { }` blocks in VNS are parsed by `TimelineDataParser` and executed via `jes_timeline_inline` under the hood. They run **asynchronously** — VNS advances to the next node immediately.

### Workflow D: Hybrid — Editor + Hand-Tuning

For complex animations that benefit from visual authoring but need precise values.

```text
1. Create animation in Puppeteer (rough positioning)
   │
2. Click "Copy Code" → clipboard
   │
3. Paste into a .jes file or VNS script
   │
4. Hand-edit specific values:
   │  - Adjust easing: ease_out_cubic → ease_out_back
   │  - Fine-tune timing: dur: 500 → dur: 420
   │  - Add audio cues that sync to specific moments
   │
5. Test in runtime
```

This hybrid approach is common for polished cutscenes where visual layout is easier in the editor but timing precision requires manual tuning.

### Workflow E: Standalone Timeline Files

For reusable animations shared across multiple scripts.

```text
1. Author timeline in Puppeteer or by hand
   │
2. Save to: scripts/timelines/hero_entrance.jes
   │
3. Register from Java (e.g. in a scene initializer):
   │  TimelineData data = TimelineDataParser.parse("hero_entrance",
   │      Files.readString(Path.of("scripts/timelines/hero_entrance.jes")));
   │  TimelineRegistry.register(data);
   │
4. Use from any VNS script:
   │  [external jes_timeline hero_entrance]
```

### Which Workflow to Choose

| Scenario | Recommended Workflow |
|----------|---------------------|
| Character entrance/exit in VN dialogue | **A** (VNS + Puppeteer) |
| JES game scene animation | **B** (JES + Copy Code) |
| Quick one-off animation in dialogue | **C** (Inline VNS) |
| Polished cutscene | **D** (Hybrid) |
| Shared animation across many scripts | **E** (Standalone file) |
| Simple fade/slide you know the values for | Hand-code directly |

---

## 5. Integration Paths

### VNS → Puppeteer Timeline (Registered)

```text
VNS Script                          Runtime
─────────                          ───────
[call jes_timeline hero_entrance]
        │
        ▼
DefaultVnInterop.handleJesTimeline()
        │
        ▼
TimelineRegistry.get("hero_entrance")
        │
        ▼
TimelineRunner(data, sceneAccessor)
        │
        ▼
VnState.addTimelineRunner(runner)
        │
        ▼
Each frame: VnState.updateTimelineRunners(deltaMs)
        │
        ▼
TimelineRunner.update(deltaMs)
        │
        ├─→ For each Track: interpolate properties, apply via SceneAccessor
        ├─→ Camera keyframes → SceneAccessor.setCameraX/Y/Zoom
        └─→ Audio cues → SceneAccessor.playAudioCue()
```

### VNS → Puppeteer Timeline (Inline)

```text
VNS Script                          Runtime
─────────                          ───────
timeline {                          VnScriptParser detects "timeline {"
  move "hero" { ... }               collects block content
  wait 200                          calls builder.external("jes_timeline_inline", blockText)
  fade "hero" { ... }
}
        │
        ▼
DefaultVnInterop.handleJesTimelineInline(blockText)
        │
        ▼
TimelineDataParser.parse("_inline_timeline_N", blockText)
        │
        ▼
TimelineRunner(data, sceneAccessor)
        │
        ▼
VnState.addTimelineRunner(runner)
```

### JES Scene → Timeline Block

```text
JES Scene File (.jes)
─────────────────────
scene "Demo" {
  entity "hero" { ... }

  timeline "intro" {
    move "hero" { x: 400 dur: 500 }
  }
}
        │
        ▼
JesParser → AST with timeline node
        │
        ▼
JesLoader materializes timeline actions
        │
        ▼
JesScene2D.executeTimeline("intro")
        │
        ▼
Sequential action execution (move, fade, etc.)
```

**Key difference:** JES runtime timelines are *action-based* (sequential execution) while Puppeteer/TimelineRunner timelines are *keyframe-based* (interpolation). Both use the same DSL syntax, but the execution model differs.

---

## 6. Runtime Behavior — Deep Dive

### TimelineRunner Execution Model

`TimelineRunner` is the engine that plays back Puppeteer animations at runtime. Understanding its behavior is crucial for debugging animation issues.

#### Per-Frame Update Cycle

```text
TimelineRunner.update(deltaMs)
    │
    ├─ 1. Guard: if finished, return immediately
    │
    ├─ 2. Advance elapsed time
    │     prevElapsed = elapsedMs
    │     nextElapsed = prevElapsed + deltaMs
    │
    ├─ 3. Handle looping
    │     if looping: elapsedMs = nextElapsed % duration
    │     else:       elapsedMs = min(nextElapsed, duration)
    │                 if nextElapsed >= duration → finished = true
    │
    ├─ 4. Trigger audio cues in the [prev, next] time window
    │     (handles loop boundaries: re-triggers cues each cycle)
    │
    └─ 5. Apply frame at current elapsedMs
          For each Track:
            Camera properties → SceneAccessor.setCameraX/Y/Zoom
            Entity properties → find entity by name, then:
              X/Y     → entity.setPosition(interpolated_x, interpolated_y)
              Z       → entity.setZ(interpolated_z)
              PIVOT   → entity.setOrigin(interpolated_ox, interpolated_oy)
              ROTATION→ entity.setRotationDeg(interpolated_deg)
              SCALE   → entity.setScale(interpolated_sx, interpolated_sy)
              ALPHA   → type-aware: Sprite2D.setAlpha / Label2D color alpha / Panel2D fill alpha
```

#### Property Preservation

The runner only writes properties that have keyframes. If a track has X keyframes but no Y keyframes, the entity's Y position is **untouched** — it retains whatever value it had before the timeline started. This is important for composing animations:

```jes
// This only animates X — Y stays at whatever position the entity currently has
timeline {
  move "hero" { x: 400 dur: 500 easing: ease_out_cubic }
}
```

#### Alpha Application

Alpha is applied differently depending on entity type:

| Entity Type | How Alpha is Applied |
|-------------|---------------------|
| `Sprite2D` | `setAlpha(value)` — direct alpha property |
| `Label2D` | `setColor(r, g, b, alpha)` — alpha channel of text color |
| `Panel2D` | `setFill(r, g, b, alpha)` — alpha channel of fill color |
| `CharacterEntity2D` | Inherits from `Sprite2D` — same behavior |
| Other `Entity2D` | **No effect** — alpha is silently ignored |

#### Pivot (Origin) Application

Pivot changes only affect `Sprite2D` and `CharacterEntity2D`. Pivot values are clamped to 0–1. Non-finite values (NaN, Infinity) fall back to 0.5 (center).

#### Camera Track

Camera properties are stored on a special track named `"__camera__"` internally. The runner detects `CAMERA_X`, `CAMERA_Y`, `CAMERA_ZOOM` keyframes and routes them to `SceneAccessor.setCameraX/Y/Zoom()` instead of looking for an entity.

In the editor, this is exposed as the dedicated **Runtime Camera / Frame** lane. That lane is also the recommended single source of truth for camera keyframes.

Camera-adjacent advanced channels such as `dof.focus`, `dof.strength`, and `dof.maxBlur` ride on the same runtime camera track, but they are exported through the generic `property` channel path rather than through `cameraMove` / `cameraZoom`.

#### Audio Cue Timing

Audio cues fire when the playhead crosses their timestamp during an update interval. Edge cases:

- **First frame:** Cues at t=0 fire on the first update
- **Looping:** Cues re-trigger at the start of each loop cycle
- **Loop boundary:** If the playhead wraps from 980ms→20ms (in a 1000ms loop), cues between 980-1000 AND 0-20 are triggered
- **Zero-duration timeline:** All cues fire once, then the runner finishes (or loops)

Event cues follow the same crossing rule. They trigger once when the playhead passes their timestamp and can mutate sprite/background/VN state instantly without an interpolation phase.

### VnState Timeline Management

`VnState` maintains a list of active `TimelineRunner` instances:

```java
// Add a runner (from VNS interop)
vnState.addTimelineRunner(runner);

// Each frame in the game loop:
vnState.updateTimelineRunners(deltaMs);
// → internally: activeTimelines.removeIf(r -> { r.update(deltaMs); return r.isFinished(); });

// Check if any animations are still playing:
if (vnState.hasActiveTimelines()) { ... }
```

Multiple timelines can run simultaneously. They are independent — each has its own elapsed time and applies its own keyframes. If two timelines animate the same property on the same entity, the **last one to write wins** (frame-order dependent). The same practical rule applies to custom numeric channels and event cues that target the same underlying sprite/background state.

---

## 7. Entity Name Resolution

Entity names are the bridge between Puppeteer and the runtime. Getting names right is essential.

### VNS Launch

When Puppeteer is launched from a VNS file, entity names come from the VNS character IDs:

| VNS Declaration | Puppeteer Entity Name |
|-----------------|----------------------|
| `@character hero "Hero"` + `[show hero center]` | `"hero"` |
| `@background park assets/bg/park.png` + `[bg park]` | `"bg_park"` |
| `@chargroup hero head ...` + `[show hero center neutral]` | `"hero_head"` group target |
| `@charlayer hero eyes_neutral ...` + `[show hero center neutral]` | `"hero_eyes_neutral"` layer target |

These names must match exactly in VNS `[call jes_timeline ...]` playback. The `SceneAccessor` provided by `DefaultVnInterop` looks up entities by character ID.

Layered character launches also create expression-specific aliases:

| Alias | Meaning |
|-------|---------|
| `hero_head` | Stable group alias, good for animation that should survive expression swaps. |
| `hero_neutral_head` | Expression-specific group alias. |
| `hero_eyes_neutral` | Stable layer alias. |
| `hero_neutral_eyes_neutral` | Expression-specific layer alias. |

Group transforms are applied before individual layer transforms. This lets authors animate broad motion on `hero_head` while keeping small corrections on `hero_eyes_neutral` or `hero_mouth_smile`.

Puppeteer uses the stable `hero_<layer>` name as the canonical exported track. The expression-specific form remains an alias for importing older timelines. A stable layer target follows that exact layer when it is reused by another expression and transfers to a uniquely inferred same-lane replacement. Use an `@chargroup` when several variants should share one explicit semantic target or naming is ambiguous.

### JES Launch

When launched from a JES file, entity names are the JES entity names:

```jes
entity "player_sprite" { ... }  // → Puppeteer name: "player_sprite"
entity "title_label"   { ... }  // → Puppeteer name: "title_label"
```

### Inline VNS Timelines

In inline VNS `timeline { }` blocks, entity names must match the character IDs used in `[show]` commands:

```vns
[show hero center happy]
[show villain right angry]

timeline {
  move "hero" { x: 500 dur: 400 }      // ✅ matches [show hero ...]
  move "villain" { x: 800 dur: 400 }   // ✅ matches [show villain ...]
  move "player" { x: 500 dur: 400 }    // ❌ "player" not shown — silently ignored
}
```

For declared layered characters, a timeline may intentionally target a layer or group absent from the current `[show]` composition. The engine pre-arms that proxy and the editor reports a warning so the delayed effect is explicit. When a later expression substitutes a conventionally named variant such as `body_default` → `body_no_limbs` or `arm_front_default` → `arm_front_holding_wrist`, the renderer infers the shared anatomical lane and carries the transform without requiring an `@chargroup`. An explicit group remains available when naming is ambiguous or a project wants to override the inferred lane.

Large rigs can keep that override concise. `@chargroup john body_orientation $body_* | $neck_* | $arm_front_* | $arm_behind_*` expands all matching declared layers, and Puppeteer exports the expression-independent `john_body_orientation` target as the canonical group track. Expression-qualified group names remain import aliases for older timelines.

### Contextual timeline diagnostics

The main VNS diagnostics pass and VN runtime share the same composition-aware timeline validator. The editor annotates the quoted target before preview; runtime turns blocking findings into the full-screen **Puppeteer Timeline Diagnostics** overlay and does not create a timeline runner or proxy.

| Diagnostic | Result | Suggested correction |
|------------|--------|----------------------|
| Character is not currently shown | Playback blocked | Move the timeline after the relevant `[show]` |
| Declared layer is absent from the active composition | Warning; transform is pre-armed | Keep it when intentional, or move the timeline after `[show]` to preview it immediately |
| Declared group has no visible member | Warning; transform is pre-armed | Keep it when intentional, or show a member first |
| Expression-qualified alias belongs to another composition | Compatibility warning | Prefer the stable `character_layer` or `character_group` name |
| Character-prefixed target matches no declared layer/group | Playback blocked | Correct the spelling or declare the intended group |
| Expression-specific target is valid now | Warning | Prefer the stable target if the animation should survive expression changes |
| Exact layer has replacement variants | Advisory | The renderer infers a unique anatomical lane; use `@chargroup` only to resolve ambiguity or override it |
| Layer disappears in another expression | Warning | Its state persists and follows a uniquely inferred replacement; reset it when that persistence is unwanted |
| Non-zero animation ends within one 60 Hz frame | Warning | Use more than `17ms` for visible interpolation, or `0ms` for an intentional cut |

Blocking validation also applies between chained timelines. If a later chain member becomes invalid after an event or expression change, the chain stops safely and releases a waiting VNS call instead of creating dormant state or deadlocking playback.

---

## 8. Export and Code Generation

### CodeExporter Pipeline

```text
AnimationProject
    │
    ▼
CodeExporter.export(project)
    │
    ├─ 1. Collect all timeline actions
    │     - property keyframes from every EntityTrack
    │     - custom numeric channels
    │     - event cues
    │     - audio cues
    │
    ├─ 2. Sort events by start time
    │
    ├─ 3. Group simultaneous events (same start time) into parallel blocks
    │
    ├─ 4. Insert wait statements for gaps > 0.5ms
    │
    ├─ 5. Merge per-entity properties into action blocks:
    │     X + Y at same time → single move "entity" { x: ... y: ... }
    │     SCALE_X + SCALE_Y → single scale "entity" { sx: ... sy: ... }
    │     PIVOT_X + PIVOT_Y → single pivot "entity" { ox: ... oy: ... }
    │     advanced/custom channels → property "entity" { key: "...", value: ... }
    │     cue payloads → event/show/hide/replace/scene blocks
    │
    └─ 6. Format as JES timeline code
```

### Export Modes Comparison

| Mode | Use Case | What It Includes | Header |
|------|----------|-----------------|--------|
| **Standard** | General purpose | All events | None |
| **Named** | VNS registry | All events | `// Timeline: <name>` + VNS usage hint |
| **With Groups** | Debugging | All events | `// Group: <name>` annotations |
| **Incremental** | Additive animations | Only changed properties | None |
| **Audio-Only** | Sound design | Only audio cues | Descriptive text format |

### Number Formatting

The exporter formats numbers cleanly:
- **Integers** when no fractional part: `500`, `0`, `360`
- **Two decimal places** otherwise, trailing zeros stripped: `320.5`, `1.15`, `0.25`
- Linear easing is **omitted** (it's the default)

---

## 9. Advanced Patterns

### Chaining Timelines

Play multiple timelines in sequence using VNS `[wait]`:

```vns
[call jes_timeline hero_entrance]
[wait 700]
[call jes_timeline villain_entrance]
[wait 700]
[call jes_timeline camera_dramatic_zoom]
[wait 500]
hero: Let's settle this!
```

The `[wait]` command pauses VNS progression. The timeline runs asynchronously, so you estimate (or know) the duration and wait that long before triggering the next.

### Overlapping Timelines

Launch multiple timelines without waiting — they run in parallel:

```vns
[call jes_timeline ambient_float]
[call jes_timeline camera_slow_pan]
hero: The world feels so peaceful...
```

Both `ambient_float` and `camera_slow_pan` execute simultaneously on separate `TimelineRunner` instances.

### Animation + Dialogue Sync

For precise timing, place the timeline just before dialogue:

```vns
timeline {
  move "hero" { x: 500 dur: 400 easing: ease_out_cubic }
  fade "hero" { alpha: 1 dur: 300 easing: ease_out_quad }
}
hero: I'm here now!
```

The inline timeline fires asynchronously, but the dialogue node appears immediately. The player reads the text while the animation plays — this creates a natural feel where the character "arrives" as they speak.

If the dialogue should be read *before* the animation starts, place the dialogue first and then the timeline followed by a matching `[wait]`. Once the player advances the line, its dialogue window remains visible for the animation; the following dialogue replaces it.

```vns
hero: Watch this.
timeline {
  move "hero" { x: 500 dur: 600 easing: ease_out_cubic }
}
[wait 600]
hero: Did you see that?
```

### Looping Ambient Animations

For idle animations (floating gems, breathing characters), create a looping timeline:

```java
TimelineData ambient = TimelineDataParser.parse("gem_float", """
    timeline {
      move "gem" { y: -10 dur: 1000 easing: ease_in_out_sine }
      wait 1000
      move "gem" { y: 0 dur: 1000 easing: ease_in_out_sine }
    }
    """);
ambient.setLooping(true);
TimelineRegistry.register(ambient);
```

Then trigger from VNS:

```vns
[external jes_timeline gem_float]
```

The animation loops until the scene changes or the runner is explicitly removed.

### Presets as Starting Points

The 12 built-in presets are designed as starting points:

| Category | Presets | Typical Customization |
|----------|---------|----------------------|
| **Entrance** | Fade In, Slide Left/Right/Bottom, Bounce In | Adjust end position, duration, easing |
| **Exit** | Fade Out, Zoom Out | Adjust target alpha, scale |
| **Emphasis** | Shake, Pulse, Spin | Adjust amplitude, repeat count |
| **Loop** | Float, Breathe | Adjust Y offset, scale range, period |

Apply a preset, then refine: change easing curves, adjust timing, modify target values. The preset provides the keyframe structure; you provide the specific values.

---

## 10. Troubleshooting

### Animation doesn't play

| Symptom | Cause | Fix |
|---------|-------|-----|
| `[call jes_timeline X]` does nothing | Timeline not registered | Ensure you clicked "Register" in Puppeteer, or register from Java code |
| Registration is blocked | Runtime verification found an issue | Read the verification report and fix the blocking error before registering |
| Timeline plays but nothing moves | Entity name mismatch | Check that entity names in the timeline match character IDs in VNS `[show]` commands |
| Inline `timeline { }` does nothing | Empty block or syntax error | Check for `"inline timeline: empty block"` HUD message; verify syntax |
| Camera doesn't move | SceneAccessor doesn't implement camera | Ensure `RuntimeVnInterop` has a valid `SceneAccessor` with camera hooks |

### Animation looks wrong

| Symptom | Cause | Fix |
|---------|-------|-----|
| Entity snaps to position | Missing starting keyframe | Add a `dur: 0` keyframe at the initial position before the animation |
| Jittery movement | Two timelines animating the same property | Avoid overlapping timelines on the same entity/property |
| Animation too fast/slow | Duration mismatch | Check `dur` values; ensure `[wait]` values in VNS match the timeline duration |
| Animation jumps straight to its result | Duration is shorter than one display frame | Durations are milliseconds; use more than `17ms` for a visible transition, or `0ms` for an intentional instant change |
| Easing feels wrong | Wrong easing direction | `ease_in_*` accelerates, `ease_out_*` decelerates — most entrances want `ease_out_*` |
| Alpha has no effect | Non-Sprite2D entity | Alpha only works on `Sprite2D`, `Label2D`, `Panel2D`, and `CharacterEntity2D` |

### Puppeteer editor issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| Entity images are missing | Asset path not resolved | Ensure `projectRoot` is set; check that image paths are relative to project root |
| Characters at wrong positions | VNS snapshot stale | Move cursor to a line after all relevant `[show]` commands |
| Keyframe not created | No entity selected | Click an entity in the preview or entity selector before pressing `K` |
| Code preview shows old code | Auto-refresh delay | Click in the timeline or press any key to trigger a refresh |
| Reopened registered timeline is missing targets | Snapshot context was unavailable | Reopen from a useful VNS cursor context so Puppeteer can backfill scene entities |

### Common VNS + Timeline pitfalls

```vns
// PITFALL: Timeline fires but characters aren't shown yet
[call jes_timeline hero_entrance]
[show hero center]
hero: Hello!

// CORRECT: Show characters first, then animate
[show hero center]
[call jes_timeline hero_entrance]
hero: Hello!
```

```vns
// PITFALL: Assuming timeline blocks VNS execution
timeline { move "hero" { x: 500 dur: 1000 } }
hero: I'm there!  // ← This appears immediately, not after 1000ms

// CORRECT: Add explicit wait if you need to sync
timeline { move "hero" { x: 500 dur: 1000 } }
[wait 1000]
hero: I'm there!
```

---

## 11. Performance Notes

- Puppeteer shares character-group rest bounds across all layer tracks within each preview frame. The cache ends with that frame, so edits to keyframes, membership, and sprite bounds are reflected on the next evaluation.
- Scene import reads image dimensions from metadata, without decoding full PNG/JPEG pixel buffers. A bounded cache checks file size and modification time before reusing dimensions. Formats without an ImageIO reader retain the JavaFX fallback.
- `PuppeteerGroupFrameBench` compares the same nested-rig transform evaluator with and without frame caching. On the development Mac, 32 layers measured approximately 54 → 6 microseconds; 128 layers measured 2.18 → 0.037 milliseconds. These are CPU transform timings, not total rendering time or FPS guarantees.
- GPU effects, image sizes, onion skins, and interpolation ghosts still contribute to total preview cost. Evaluate performance on the target machine with the intended scene and preview options.

---

## 12. File Map

### Editor Module (`editor/`)

```
modules/editor/src/main/java/com/jvn/editor/
├── EditorApp.java                          # Main editor app; wires Puppeteer launcher
├── ui/
│   ├── PuppeteerLauncherPanel.java         # VNS snapshot resolver + launch button
│   ├── FileEditorTab.java                  # Tab routing; exposes JES scene + VNS caret
│   ├── VnsCodeEditor.java                  # RichTextFX editor with caret tracking
│   └── actioneditor/
│       ├── PuppeteerWindow.java            # Main Puppeteer Stage
│       ├── AnimationProject.java           # Data model (tracks, groups, keyframes)
│       ├── AnimationPreview.java           # Canvas viewport with entity interaction
│       ├── EntitySelector.java             # Entity tree panel
│       ├── TimelinePanel.java              # Keyframe timeline panel
│       ├── KeyframeEditor.java             # Keyframe property editor
│       ├── KeyframeSelectionModel.java     # Box select, ripple retime, channel filters
│       ├── CodePreviewPane.java            # Live code output panel
│       ├── CodeExporter.java               # Project → JES code converter
│       ├── CodeImporter.java               # JES code → Project importer (round-trip)
│       ├── AnimationPreset.java            # Built-in animation templates
│       ├── AnimationClip.java              # Reusable clip capture/apply/serialize
│       ├── EditorEventCue.java             # Event cue editor model
│       ├── TimelineDiagnostic.java         # Validation: ranges, entities, easing names
│       ├── VnSlotHelper.java              # VN slot positions + expression/dialogue markers
│       ├── PuppeteerVerification.java      # Runtime registration verification pass
│       ├── PuppeteerCharacterRigResolver.java # Parses the source .vns script into a VnCharacter for rig-aware diagnostics
│       ├── PuppeteerCommand.java           # Undo/redo command stack
│       ├── PuppeteerDraftStore.java        # Auto-save/restore unsaved work
│       ├── PuppeteerWorkspacePrefs.java    # Per-project editor preferences
│       ├── PuppeteerAudioLibrary.java      # Project audio file scanner for cue dialog
│       ├── PuppeteerPreviewRecorder.java   # GIF/frame-sequence export from preview
│       ├── EasingCurveEditor.java          # Visual cubic Bézier + multi-point curve editor
│       ├── EasingPickerModel.java          # Searchable easing combo model
│       ├── PuppeteerEasingPresetStore.java # Project curve presets (save/load/delete)
│       ├── AssetPickerPanel.java           # Image browser + add-to-scene pipeline
│       ├── SplinePath.java                 # Catmull-Rom spline for motion paths
│       ├── AudioCue.java                   # Audio cue model
│       ├── EntityTrack.java                # Per-entity keyframe storage
│       ├── EntityGroup.java                # Hierarchical entity grouping
│       ├── Keyframe.java                   # Single keyframe (time, value, easing)
│       ├── PropertyType.java               # Animatable property enum
│       └── Easing.java                     # Easing curve implementations
```

### Core Module (`core/`)

```
modules/core/src/main/java/com/jvn/core/
├── animation/
│   ├── TimelineData.java                   # Serializable timeline for registry
│   ├── TimelineDataParser.java             # Inline JES block → TimelineData converter
│   ├── TimelineRunner.java                 # Applies timeline to entities at runtime
│   ├── TimelineRegistry.java              # Global name → TimelineData map
│   ├── SceneAccessor.java                 # Interface for entity property access
│   ├── Easing.java                        # Standard curves, spring family, named curves
│   └── EasingSpec.java                    # Parsed easing type + optional parameters
├── scene2d/
│   ├── Entity2D.java                       # Base entity (x, y, rotation, scale, alpha)
│   ├── Sprite2D.java                       # Image entity (width, height, origin)
│   ├── Label2D.java                        # Text entity (text, font, color)
│   ├── Panel2D.java                        # Rect entity (fill, border)
│   ├── CharacterEntity2D.java             # Animated character sprite
│   ├── Scene2DBase.java                    # Entity list + camera + input
│   └── Blitter2D.java                      # Rendering interface
└── vn/
    ├── VnState.java                        # Mutable VN state; manages activeTimelines list
    ├── VnScene.java                        # VN scene driver
    ├── DefaultVnInterop.java              # Handles jes_timeline + jes_timeline_inline
    ├── CharacterPosition.java              # LEFT/CENTER/RIGHT/FAR_LEFT/FAR_RIGHT
    └── script/VnScriptParser.java          # .vns text → VnScenario (detects inline timeline blocks)
```

### FX Module (`fx/`)

```
modules/fx/src/main/java/com/jvn/fx/
├── scene2d/FxBlitter2D.java               # JavaFX Canvas Blitter2D impl
│                                            (classpath + filesystem image loading)
└── vn/VnRenderer.java                      # VN scene Canvas renderer
```

---

## 13. Feature Highlights

This section summarizes key implementation details for Puppeteer subsystems not fully covered above.

### Asset Picker and Import Pipeline
The **Assets** tab scans the project for image files (png, jpg, gif, bmp, webp) and displays them with thumbnails. Clicking **+ Add to Scene** creates a `Sprite2D` entity at center-screen with the image's actual pixel dimensions. **Import...** copies external files into `assets/puppeteer/imported/`. `PuppeteerAudioLibrary` provides the same scan-and-filter functionality for audio formats in the Add Audio Cue dialog.

### Easing Subsystem
The easing picker (`EasingPickerModel`) is searchable — type part of a family name or semantic label to filter. The visual `EasingCurveEditor` renders the selected curve live (grid, linear reference line, blue output curve, start/end markers). For `CUSTOM` cubic Bézier and multi-point `curve(...)` entries, control points are directly draggable. Project-level presets are persisted via `PuppeteerEasingPresetStore` into `config/puppeteer/easing-presets.properties`.

### Round-Trip Code Editing
`CodeImporter` parses exported JES timeline blocks (including Puppeteer metadata comments) back into an `AnimationProject`. This enables a visual → text → visual round-trip workflow for collaborative editing and hand-tuning. The code panel provides Preview Parse → Commit/Discard staging. Named exports preserve editor state such as groups, locks, constraints, named anchors, and orbit-anchor tooling data so registered animations can be reopened for later work without losing rigging context.

### Timeline Diagnostics
`TimelineDiagnostic` validates timelines during registration and code preview. It catches alpha/zoom/pivot range issues, missing entities, empty event types, unknown easing names (with edit-distance suggestions), camera key misplacement, and missing audio files. `PuppeteerVerification` layers additional export/registration-time checks on top, including orbit-pivot-at-risk detection and unresolved-layer-proxy detection: the latter (`diagnoseUnresolvedLayerProxies`) flags tracks with keyframes on a character rig layer/group id that has no runtime-resolvable proxy name in the current scene, using `PuppeteerCharacterRigResolver` to parse the source `.vns` script's rig declarations. See [Puppeteer Editor Guide § Unresolved Layer Proxy Warnings](puppeteer-editor-guide.md#unresolved-layer-proxy-warnings).

### Animation Clips
`AnimationClip` captures keyframe segments, serializes them as `.properties` files under `config/puppeteer/clips/`, and supports apply with duration scaling and Layer On Top / Replace Range modes.

### VN Slot Helpers
`VnSlotHelper` maps FAR_LEFT/LEFT/CENTER/RIGHT/FAR_RIGHT to normalized X coordinates and provides methods for inserting expression cues and dialogue markers aligned with VN character positions.

### Layer Ordering
Entities and groups carry `layerOrder` metadata. Context-menu Raise (+10) / Lower (-10) controls adjust order. The effective Z is computed hierarchically through groups, exported to `TimelineData`, and applied at runtime via `entity.setZ()`.

### Draft Auto-Save
`PuppeteerDraftStore` auto-saves unsaved work periodically. If Puppeteer is reopened after a crash or accidental close, the draft is restored automatically with a notification.

Drafts are separate from registered timeline files. A successful Save & Register
writes the named `.jes` timeline with reopen metadata and clears the matching
draft.

### Preview Recorder
`PuppeteerPreviewRecorder` captures the preview canvas to image sequences or animated GIFs for documentation and asset review workflows.

---

## 14. Known Limitations

- **Single-scene scope** — Puppeteer operates on one scene at a time; cross-scene transitions must be authored separately
- **Snapshot is static** — the VNS snapshot at launch time captures the state up to the cursor line; it does not update if the VNS script changes while Puppeteer is open
- **No drag-from-asset-picker** — assets are added via button click; drag-and-drop onto the preview is not yet implemented
- **Group baking is scalar timeline output** — curved group rotation/scale is baked to child position samples for runtime playback; very long arcs may produce denser exported JES
- **No timeline chaining** — there is no built-in way to sequence named timelines; chain them manually with `[wait]` between `[call]` commands

---

## 15. Related Docs

- [Puppeteer Editor Guide](puppeteer-editor-guide.md) — comprehensive UI usage: launching, keyframes, presets, easing, clips, event cues, diagnostics, code round-trip, camera, groups, shortcuts
- [Puppeteer JES DSL Reference](puppeteer-jes-dsl.md) — complete exported timeline syntax: actions, depth/visible, property channels, event cues, easing, spring, Bézier, export modes
- [Hand-Coding Timelines](../../scripting/timeline/animation/timeline-hand-coding.md) — writing timelines by hand, time cursor model, 18 examples, reusable templates
- [Puppeteer Animation Timelines](../../scripting/timeline/animation/timeline-animation.md) — TimelineData model, TimelineRunner, TimelineRegistry, event cues, audio cues, SceneAccessor
- [Timeline Overview](../../scripting/timeline/overview/timeline-scripting.md) — story maps vs animation timelines
- [JES Timeline & Actions](../../scripting/jes/timeline/jes-timeline.md) — JES runtime timeline actions (superset: combat, flow control, loops)
- [Puppeteer Launcher Panel](../sidebars/right/sidebar-puppeteer-launcher.md) — VNS snapshot resolution details
- [VNS Characters](../../scripting/vns/presentation/vns-characters.md) — character positions and layering (the VNS context Puppeteer launches from)
- [VNS Interop](../../scripting/vns/integration/vns-interop.md) — inline timelines and `[call jes_timeline]` from VNS
