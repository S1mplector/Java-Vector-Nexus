# JES Timeline & Actions

Complete reference for JES timeline blocks — scripted animation sequences, entity tweens, camera control, audio, branching, and composite actions.

Runtime: `modules/scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`

---

## Timeline Block

A timeline is a sequence of actions executed in order inside a scene.

```jes
scene "Demo" {
  timeline {
    // actions run sequentially
    move "hero" { x: 200 y: 100 dur: 500 easing: ease_out_quad }
    wait 300
    fade "hero" { alpha: 0.5 dur: 400 easing: linear }
  }
}
```

Timelines execute one action at a time. An action that has a `dur` (duration) property blocks until it completes, then the next action starts.

### Puppeteer-Compatible Timeline Dialect

JES scene timelines accept the same core action vocabulary used by Puppeteer exports. Generated Puppeteer snippets can be pasted into a `.jes` scene `timeline { ... }` block without hand-converting common keys.

| Purpose | Preferred JES key | Accepted aliases |
|---------|-------------------|------------------|
| Duration | `dur` | `duration` |
| Rotation | `deg` | `angle`, `rotation` |
| Scale X/Y | `sx`, `sy` | `x`, `y`, `scale_x`, `scale_y` |
| Visibility value | `value` | `visible` |
| Audio fade-in | `fadeinms` | `fadein_ms`, `fadein`, `fade_in` |

Timed actions also accept `interp` for interpolation mode, and `easing` can use named easing values or parameterized literals such as `spring(...)`, `damped_spring(...)`, `curve(...)`, `cubic_bezier(...)`, and CSS-style `cubic-bezier(...)`.

---

## Entity Movement Actions

### `move`

Tweens an entity's position over a duration.

```jes
move "entityName" { x: <target_x> y: <target_y> dur: <ms> easing: <type> }
```

| Property | Required | Description |
|----------|----------|-------------|
| `x` | No | Target X position (defaults to current) |
| `y` | No | Target Y position (defaults to current) |
| `dur` | Yes | Duration in milliseconds |
| `easing` | No | Easing function (default: `linear`) |

**Examples:**

```jes
// Move hero to position (300, 200) over 500ms
move "hero" { x: 300 y: 200 dur: 500 easing: ease_out_quad }

// Slide only horizontally
move "panel" { x: 400 dur: 300 easing: ease_in_out_cubic }

// Bounce into position
move "title" { x: 640 y: 80 dur: 600 easing: ease_out_bounce }
```

### `walkToTile`

Moves an entity to a tile coordinate (using the scene's grid size).

```jes
walkToTile "entityName" { tx: <tileX> ty: <tileY> dur: <ms> easing: <type> }
```

| Property | Required | Description |
|----------|----------|-------------|
| `tx` | No | Target tile X (defaults to current) |
| `ty` | No | Target tile Y (defaults to current) |
| `dur` | No | Duration in ms (if 0 or absent, instant warp) |
| `easing` | No | Easing function |

**Examples:**

```jes
// Walk to tile (5, 3)
walkToTile "hero" { tx: 5 ty: 3 dur: 400 easing: ease_in_out_quad }

// Instant warp to tile
walkToTile "npc" { tx: 10 ty: 7 }
```

### `pivot`

Changes an entity's origin/pivot point (for rotation and scaling).

```jes
pivot "entityName" { ox: <originX> oy: <originY> dur: <ms> easing: <type> }
```

**Example:**

```jes
// Shift pivot to center
pivot "sprite" { ox: 0.5 oy: 0.5 dur: 200 easing: linear }
```

---

## Transform Actions

### `rotate`

Tweens an entity's rotation.

```jes
rotate "entityName" { deg: <degrees> dur: <ms> easing: <type> }
```

**Examples:**

```jes
// Spin 360 degrees
rotate "coin" { deg: 360 dur: 1000 easing: linear }

// Tilt slightly
rotate "sign" { deg: 5 dur: 300 easing: ease_out_elastic }

// Rotate back
rotate "sign" { deg: 0 dur: 300 easing: ease_in_out_quad }
```

### `scale`

Tweens an entity's scale.

```jes
scale "entityName" { sx: <scaleX> sy: <scaleY> dur: <ms> easing: <type> }
```

**Examples:**

```jes
// Scale up to 2x
scale "icon" { sx: 2.0 sy: 2.0 dur: 400 easing: ease_out_back }

// Squash and stretch
scale "character" { sx: 1.2 sy: 0.8 dur: 100 easing: ease_out_quad }
wait 50
scale "character" { sx: 0.9 sy: 1.1 dur: 100 easing: ease_out_quad }
wait 50
scale "character" { sx: 1.0 sy: 1.0 dur: 150 easing: ease_in_out_sine }
```

### `depth`

Tweens or sets an entity's render depth. Higher values render above lower values.

```jes
depth "entityName" { z: <depth> dur: <ms> easing: <type> }
```

**Examples:**

```jes
// Put the prompt above the character layer instantly
depth "prompt" { z: 20 dur: 0 }

// Animate a card forward through layered UI
depth "card" { z: 8 dur: 160 easing: ease_out_quad }
```

### `fade`

Tweens an entity's alpha/opacity.

```jes
fade "entityName" { alpha: <0.0-1.0> dur: <ms> easing: <type> }
```

**Examples:**

```jes
// Fade out
fade "ghost" { alpha: 0.0 dur: 800 easing: ease_in_quad }

// Fade in
fade "title" { alpha: 1.0 dur: 500 easing: ease_out_quad }

// Pulsing effect (use in a loop)
fade "gem" { alpha: 0.5 dur: 400 easing: ease_in_out_sine }
wait 50
fade "gem" { alpha: 1.0 dur: 400 easing: ease_in_out_sine }
```

### `visible`

Instantly shows or hides an entity.

```jes
visible "entityName" { value: true }
visible "entityName" { value: false }
```

**Example:**

```jes
visible "secret_door" { value: false }
wait 2000
visible "secret_door" { value: true }
```

---

## Camera Actions

### `cameraMove`

Pans the camera to a position.

```jes
cameraMove { x: <x> y: <y> dur: <ms> easing: <type> }
```

**Examples:**

```jes
// Pan camera to (200, 100) over 500ms
cameraMove { x: 200 y: 100 dur: 500 easing: ease_out_quad }

// Quick snap
cameraMove { x: 0 y: 0 dur: 0 }
```

### `cameraZoom`

Zooms the camera.

```jes
cameraZoom { zoom: <factor> dur: <ms> easing: <type> }
```

| Value | Effect |
|-------|--------|
| `1.0` | Default zoom (no change) |
| `< 1.0` | Zoom out (wider view) |
| `> 1.0` | Zoom in (closer view) |

**Examples:**

```jes
// Zoom in for dramatic effect
cameraZoom { zoom: 1.5 dur: 600 easing: ease_in_out_quad }

// Zoom back out
cameraZoom { zoom: 1.0 dur: 400 easing: ease_out_quad }
```

### `cameraShake`

Triggers a screen shake effect.

```jes
cameraShake { ampX: <pixels> ampY: <pixels> dur: <ms> }
```

**Examples:**

```jes
// Strong impact
cameraShake { ampX: 8 ampY: 8 dur: 400 }

// Subtle horizontal rumble
cameraShake { ampX: 3 ampY: 1 dur: 200 }
```

### `cameraFollow`

Makes the camera smoothly follow a target entity.

```jes
cameraFollow "targetEntity" { lerp: <0.0-1.0> offsetX: <x> offsetY: <y> deadZoneW: <w> deadZoneH: <h> }
```

Short form:

```jes
cameraFollow "hero"
```

| Property | Default | Description |
|----------|---------|-------------|
| `target` | — | Entity name to follow |
| `lerp` | 0.2 | Smoothing factor (lower = smoother) |
| `offsetX` | 0 | Camera offset from target |
| `offsetY` | 0 | Camera offset from target |
| `deadZoneW` | 0 | Dead zone width (no movement until target exits) |
| `deadZoneH` | 0 | Dead zone height |

**Examples:**

```jes
// Simple follow with defaults
cameraFollow "hero"

// Follow with offset and dead zone
cameraFollow "hero" { lerp: 0.15 offsetX: 0 offsetY: -40 deadZoneW: 100 deadZoneH: 60 }
```

---

## Timing Actions

### `wait`

Pauses the timeline for a duration.

```jes
wait <milliseconds>
```

**Examples:**

```jes
wait 500
wait 1000
wait 2500
```

### `waitForCall`

Pauses the timeline until a named call/event is triggered.

```jes
waitForCall "eventName"
```

**Examples:**

```jes
// Wait for the player to trigger "doorOpened" event
waitForCall "doorOpened"
move "door" { x: 100 y: 0 dur: 500 }

// Wait for external signal
waitForCall "cutsceneReady"
```

Events are triggered by `invokeCall()` from input bindings, physics triggers, or Java hooks. Once a `waitForCall` is satisfied, the timeline advances.

---

## Event Cue Actions

Event cue actions fire immediately at the current timeline position. They are routed through the scene's timeline event bridge, so VN interop, JES scene playback, and Java handlers can react to the same authored cue.

### `event`

Fires a generic event with arbitrary key-value payload data.

```jes
event "script_call" {
  handler: spawnParticles
  count: 12
}
```

The event type is the quoted string after `event`. Payload values are kept as event properties and delivered to the registered handler or bridge.

### Shortcut Event Actions

JES also accepts the common Puppeteer shortcut actions. These parse to event cues internally.

```jes
expression "hero" { value: smile }
show "hero" { path: "assets/characters/hero.png" position: center }
hide "hero" {}
replace "hero" { path: "assets/characters/hero_angry.png" }
scene "bg_school" { path: "assets/bg/school.png" }
```

| Shortcut | Event type | Typical payload |
|----------|------------|-----------------|
| `expression "target"` | `expression` | `target`, `value` or `expression`, optional `path`, optional `position` |
| `show "target"` | `show` | `target`, optional `expression`, `value`, `path`, `position`, `layer` |
| `hide "target"` | `hide` | `target` |
| `replace "target"` | `replace` | `target`, optional `expression`, `value`, `path` |
| `scene "target"` | `scene` | optional `target`, `id`, `path`, `value` |

Use `event "custom_type" { ... }` for project-specific Java or JES callbacks that do not map to a built-in visual-novel cue.

---

## Audio Actions

### `playAudio`

Plays an audio file.

```jes
playAudio "path/to/audio.ogg" { volume: <0.0-1.0> loop: <bool> bgm: <bool> channel: <name> fadeinms: <ms> }
```

| Property | Default | Description |
|----------|---------|-------------|
| `volume` | 1.0 | Playback volume |
| `loop` | false | Loop playback |
| `bgm` | false | Treat as background music |
| `channel` | auto | Audio channel name. Defaults to `music` when `bgm` is true, otherwise `sound` |
| `fadeinms` | 0 | Fade-in duration in milliseconds. Aliases: `fadein_ms`, `fadein`, `fade_in` |

**Examples:**

```jes
// One-shot sound effect
playAudio "assets/audio/sfx/explosion.ogg"

// Looping background music at 70% volume
playAudio "assets/audio/bgm/dungeon.ogg" { volume: 0.7 loop: true bgm: true }

// Quiet ambient sound
playAudio "assets/audio/sfx/rain.ogg" { volume: 0.3 loop: true }
```

### `stopAudio`

Stops a playing audio track.

```jes
stopAudio "assets/audio/bgm/dungeon.ogg"
```

---

## Combat Actions

### `damage`

Applies damage to an entity's `Stats`.

```jes
damage "entityName" { amount: <value> source: "attackerName" }
```

**Examples:**

```jes
damage "enemy" { amount: 25 source: "hero" }
damage "hero" { amount: 10 }
```

Respects the entity's defense stat. If HP reaches 0 and `onDeathCall` is set, the death callback is invoked. If `removeOnDeath` is true, the entity is removed from the scene.

### `heal`

Restores HP to an entity.

```jes
heal "entityName" { amount: <value> source: "healerName" }
```

**Examples:**

```jes
heal "hero" { amount: 50 }
heal "ally" { amount: 30 source: "hero" }
```

HP is clamped to `maxHp`.

---

## Particle Actions

### `emitParticles`

Triggers a burst of particles from a `ParticleEmitter2D` entity.

```jes
emitParticles "emitterName" { count: <number> }
```

**Example:**

```jes
emitParticles "sparks" { count: 20 }
```

---

## Entity Appearance

### `setParallax`

Sets parallax scrolling factors on an entity.

```jes
setParallax "entityName" { px: <factorX> py: <factorY> }
```

| Factor | Effect |
|--------|--------|
| `1.0` | Moves with camera (default) |
| `0.0` | Stays fixed (HUD-like) |
| `0.5` | Half-speed scrolling (distant background) |

**Examples:**

```jes
// Distant mountains scroll at half speed
setParallax "mountains" { px: 0.5 py: 0.5 }

// Fixed HUD element
setParallax "score_label" { px: 0.0 py: 0.0 }
```

### `property`

Tweens a numeric custom property on an entity or on the runtime camera. This is the path used by Puppeteer for advanced channels such as matrix values, blur, color matrix entries, depth-of-field values, and any project-defined numeric channel.

```jes
property "entityName" { key: "<property.key>" value: <number> dur: <ms> easing: <type> }
```

Use an empty target or `__camera__` to write a camera custom property:

```jes
// Entity blur channel
property "hero" { key: "effect.blur" value: 6 dur: 180 easing: ease_out_quad }

// Runtime camera depth-of-field channel
property "__camera__" { key: "dof.strength" value: 0.35 duration: 240 easing: camera_glide }
```

---

## Flow Control

### `label`

Declares a named position in the timeline for `jump` targets.

```jes
label "labelName"
```

### `jump`

Jumps to a labeled position in the timeline.

```jes
jump "labelName"
```

**Example:**

```jes
label "idle"
move "enemy" { x: 100 y: 200 dur: 1000 easing: ease_in_out_sine }
wait 500
move "enemy" { x: 300 y: 200 dur: 1000 easing: ease_in_out_sine }
wait 500
jump "idle"
```

---

## Composite Actions

### `parallel`

Runs multiple actions simultaneously instead of sequentially.

```jes
parallel {
  move "hero" { x: 300 y: 200 dur: 500 easing: ease_out_quad }
  fade "hero" { alpha: 0.5 dur: 500 easing: linear }
  rotate "hero" { deg: 45 dur: 500 easing: ease_in_out_cubic }
}
```

All child actions start at the same time and run independently of the main timeline. The next action in the main timeline can run while the children are moving. Insert a `wait` before a later cue when it must follow moves with a known duration.

### `loop`

Repeats a block of actions.

**Count-based loop:**

```jes
loop 3 {
  move "star" { x: 100 y: 50 dur: 300 easing: ease_out_quad }
  wait 100
  move "star" { x: 200 y: 50 dur: 300 easing: ease_out_quad }
  wait 100
}
```

**Event-based loop (until a call is triggered):**

```jes
loop until "playerReady" {
  fade "prompt" { alpha: 0.3 dur: 500 easing: ease_in_out_sine }
  fade "prompt" { alpha: 1.0 dur: 500 easing: ease_in_out_sine }
}
```

---

## Call Actions

### `call`

Invokes a registered call handler or the scene's action handler.

```jes
call "handlerName" { key1: value1 key2: value2 }
```

**Examples:**

```jes
// Trigger a custom handler
call "spawnWave" { count: 5 speed: 120 }

// Simple event trigger
call "doorOpened"

// VN bridge return
call "return" { label: "after_game" score: 1200 rank: "S" }
```

Built-in call handlers:
- `warpMap` — teleport player to tile/position
- `useItem` — use an inventory item
- `giveItem` — add item to inventory
- `takeItem` — remove item from inventory
- `equipItem` / `unequipItem` — manage equipment
- `attack` — apply damage between entities
- `setLabelText` — update a Label2D's text
- `removeEntity` — remove an entity from the scene
- `resetBalls` — reset all entities to spawn positions

---

## Easing Types

All tween actions support an `easing` property. Available types:

| Category | Easings |
|----------|---------|
| **Linear** | `linear` |
| **Quadratic** | `ease_in_quad`, `ease_out_quad`, `ease_in_out_quad` |
| **Cubic** | `ease_in_cubic`, `ease_out_cubic`, `ease_in_out_cubic` |
| **Quartic** | `ease_in_quart`, `ease_out_quart`, `ease_in_out_quart` |
| **Exponential** | `ease_in_expo`, `ease_out_expo`, `ease_in_out_expo` |
| **Sine** | `ease_in_sine`, `ease_out_sine`, `ease_in_out_sine` |
| **Elastic** | `ease_in_elastic`, `ease_out_elastic`, `ease_in_out_elastic` |
| **Back** | `ease_in_back`, `ease_out_back`, `ease_in_out_back` |
| **Bounce** | `ease_in_bounce`, `ease_out_bounce`, `ease_in_out_bounce` |

**Quick guide:**
- `ease_in_*` — starts slow, ends fast
- `ease_out_*` — starts fast, ends slow (most natural for UI)
- `ease_in_out_*` — slow at both ends, fast in middle

### Parameterized Easing Literals

JES timeline actions also accept parameterized easing literals:

```jes
move "hero" { x: 500 dur: 420 easing: spring(220, 24, 1.0, 0) }
cameraMove { x: 140 y: -20 dur: 800 easing: damped_spring(1.25, 1.10, 0.92, 0) }
scale "title" { sx: 1.08 sy: 1.08 dur: 220 easing: cubic_bezier(0.25, 0.10, 0.25, 1.00) }
fade "panel" { alpha: 1 dur: 260 easing: cubic-bezier(0.16, 1.0, 0.3, 1.0) }
```

Accepted forms:

| Literal | Meaning |
|---------|---------|
| `spring(stiffness, damping, mass, velocity)` | Physical spring response |
| `damped_spring(frequency, damping_ratio, response, velocity)` | Motion-design spring response |
| `curve(x1, y1, x2, y2)` | Custom cubic curve shorthand |
| `cubic_bezier(x1, y1, x2, y2)` | CSS-style cubic Bezier with underscore |
| `cubic-bezier(x1, y1, x2, y2)` | CSS-style cubic Bezier spelling |

Named reusable curves such as `hero_pop`, `ui_soft_in`, and `camera_glide` are also accepted.

### Interpolation Modes

Timed actions support an optional `interp` key:

| Mode | Accepted values | Behavior |
|------|-----------------|----------|
| Tween | `tween` | Smooth interpolation using `easing` |
| Hold | `hold`, `step_end`, `step_hold`, `constant` | Hold the previous value until the target time |
| Step | `step`, `step_start`, `instant`, `jump` | Snap at the start of the segment |

---

## Full Timeline Example

```jes
scene "BattleIntro" {
  entity "hero" {
    component Sprite2D { image: "assets/characters/hero.png" x: -100 y: 300 w: 64 h: 64 alpha: 0.0 }
  }
  entity "enemy" {
    component Sprite2D { image: "assets/characters/enemy.png" x: 800 y: 300 w: 64 h: 64 alpha: 0.0 }
  }
  entity "vs_text" {
    component Label2D { text: "VS" x: 400 y: 250 size: 48 bold: true color: rgb(1, 0.2, 0.2, 1) }
  }

  timeline {
    // Fade in and slide both characters
    parallel {
      move "hero" { x: 200 y: 300 dur: 800 easing: ease_out_back }
      fade "hero" { alpha: 1.0 dur: 600 easing: ease_out_quad }
      move "enemy" { x: 600 y: 300 dur: 800 easing: ease_out_back }
      fade "enemy" { alpha: 1.0 dur: 600 easing: ease_out_quad }
    }

    wait 300

    // VS text zoom in
    scale "vs_text" { sx: 2.0 sy: 2.0 dur: 300 easing: ease_out_elastic }
    cameraShake { ampX: 5 ampY: 5 dur: 200 }
    playAudio "assets/audio/sfx/clash.ogg"

    wait 1000

    // Zoom camera in on hero
    cameraZoom { zoom: 1.3 dur: 500 easing: ease_in_out_quad }
    wait 800
    cameraZoom { zoom: 1.0 dur: 300 easing: ease_out_quad }

    // Signal ready for gameplay
    call "battleReady"
  }
}
```

---

## Related Docs

- [JES Overview](../overview/jes-scripting.md)
- [Scenes & Entities](../scene/jes-scenes-entities.md)
- [Component Reference](../scene/components.md)
- [Camera System](../systems/jes-camera.md)
- [Input Bindings](../systems/jes-input.md)
