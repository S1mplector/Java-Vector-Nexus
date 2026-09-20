# VNS Directives & Declarations

Directives are header-level instructions that configure your script before story content begins. They start with `@` and define characters, assets, variables, macros, and structural metadata.

Parser source: `modules/core/src/main/java/com/jvn/core/vn/script/VnScriptParser.java`

---

## `@scenario`

Declares the scenario identity for this script.

```text
@scenario <id>
```

- Optional but recommended.
- Must appear before any content lines if used.
- Defaults to `untitled` when omitted.
- Only one `@scenario` per script (duplicates are parse errors).

**Examples:**

```vns
@scenario prologue
```

```vns
@scenario chapter_2_the_forest
```

---

## `@character`

Registers a display name for a speaker ID used in dialogue lines.

```text
@character <id> "Display Name" [scale=<factor>] [color=<#RRGGBB[AA]>]
```

- `id` is used in dialogue lines and commands.
- `"Display Name"` is what the renderer shows to the player.
- `scale` is an optional persistent sprite-size multiplier from `0.1` to `3.0`;
  it defaults to `1.0` and applies every time the character is shown.
- `color` is an optional speaker/name-box color in `#RRGGBB` or `#RRGGBBAA`
  format.
- Multiple `@character` declarations can exist per script.
- If a speaker ID is used without a matching `@character`, the raw ID is shown as the display name.

**Examples:**

```vns
@character narrator "Narrator"
@character hero "Aria" scale=1.1
@character villain "Lord Wraith" color=#A978B8
@character npc_guard "City Guard"
```

`scale` multiplies the active `characterHeightFactor` from
`config/ui/dialogue.layout` or `ui.characterHeightFactor`. Timeline scaling
remains an additional temporary transform.

**Usage in dialogue:**

```vns
hero: Hello there!
# Renderer shows "Aria: Hello there!"

narrator: The wind howled.
# Renderer shows "Narrator: The wind howled."
```

---

## `@background`

Maps a background ID to an image file path.

```text
@background <id> <path>
```

- `id` is used in `[bg]` / `[background]` commands and transitions.
- `path` is relative to the project asset root.

**Examples:**

```vns
@background classroom assets/backgrounds/school/classroom_day.png
@background classroom_night assets/backgrounds/school/classroom_night.png
@background forest assets/backgrounds/nature/forest_path.png
@background sky_sunset assets/backgrounds/sky/sunset_wide.png
```

**Usage:**

```vns
[bg classroom]
narrator: The morning light streamed through the windows.

[transition FADE 800 classroom_night]
narrator: Hours passed in silence.
```

---

## Asset Path Quoting

`@background`, `@charimg`, and `@charlayer` accept double-quoted paths containing spaces. The surrounding quotes are removed before resolving the image. Within quoted paths, `\\` represents a backslash and `\"` represents a literal quote. A whitespace-separated `#` outside quotes starts a trailing comment; `#` inside quotes is part of the filename. Existing unquoted paths containing spaces remain supported.

```vns
@background classroom "assets/backgrounds/classroom day.png"
@charlayer ari eyes_open "assets/characters/ari/eyes open.png" # neutral eyes
```

The editor's Auto-label dashboard generates this quoting automatically.

---

## `@charimg`

Adds an expression-specific sprite path for a character.

```text
@charimg <characterId> <expressionId> <path>
```

- `characterId` matches a declared `@character` (or creates an implicit one).
- `expressionId` is a named expression like `neutral`, `happy`, `angry`.
- `path` is a single image path, or multiple layered paths separated by `|`.

**Single-layer examples:**

```vns
@charimg hero neutral assets/characters/aria/aria_neutral.png
@charimg hero happy assets/characters/aria/aria_happy.png
@charimg hero angry assets/characters/aria/aria_angry.png
@charimg hero surprised assets/characters/aria/aria_surprised.png
```

**Multi-layer example (composited sprites):**

```vns
@charimg nora battle assets/characters/nora/body_base.png | assets/characters/nora/head_smile.png | assets/characters/nora/accessory_glasses.png
```

The renderer draws layers left-to-right (first layer is bottommost) on the same character slot. This is useful for mix-and-match character art pipelines.

**Usage:**

```vns
[show hero center neutral]
hero: I'm ready.

[show hero center happy]
hero: Let's go!
```

---

## `@charlayer`

Registers a named layer path for reuse in expression presets.

```text
@charlayer <characterId> <layerId> <path>
```

- Defines reusable parts (body, eyes, mouth, accessories) that can be mixed via `@charpreset`.
- Path cannot be empty (parse error).

**Examples:**

```vns
@charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
@charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
@charlayer lavender eyes_happy assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_happy.png
@charlayer lavender eyes_angry assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_angry.png
@charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
@charlayer lavender mouth_frown assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_frown.png
@charlayer lavender mouth_open assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_open.png
```

---

## `@chargroup`

Registers a named movable group of character layers. Groups make large rigs easier to author: define a `head` group once, use `$head` in presets, then animate one timeline target such as `john_head` instead of keying every head layer separately.

```text
@chargroup <characterId> <groupId> [parent=<parentGroupId>] [pivot=<x>,<y>] <layerSpec>
```

- `layerSpec` uses the same pipe-separated `$layerId` references as `@charpreset`.
- Layer globs are supported: `$body_*`, `$arm_front_*`, and `?` for one character. They expand to declared layer IDs in deterministic name order; a glob that matches nothing is an error.
- Long `@chargroup` and `@charpreset` declarations may continue onto the next line with a trailing `\`.
- `$groupId` can reference an earlier `@chargroup`, allowing nested groups.
- `parent=<parentGroupId>` makes this group inherit the parent group's timeline transform.
- `pivot=<x>,<y>` sets the default group pivot for rotation/scale until a timeline pivot overrides it.
- In timelines, target either `<character>_<expression>_<group>` or the stable alias `<character>_<group>`.
- Declare layers before groups that use them, and declare parent/nested groups before groups or presets that reference them.
- If a layer and group share the same name, `$name` resolves to the layer first.

**Examples:**

```vns
@charlayer john body_default assets/characters/john/body.png
@charlayer john head_base assets/characters/john/head.png
@charlayer john eyes_neutral assets/characters/john/eyes_neutral.png
@charlayer john mouth_smile assets/characters/john/mouth_smile.png

@chargroup john head pivot=0.5,0.28 $head_base | $eyes_neutral | $mouth_smile
@chargroup john body_orientation pivot=0.5,1 $body_* | $neck_* | $arm_front_* | $arm_behind_*
@charpreset john neutral $body_default | $head
```

This is equivalent to writing:

```vns
@charpreset john neutral $body_default | $head_base | $eyes_neutral | $mouth_smile
```

The grouped version keeps the preset shorter and also creates the `john_head` timeline target for moving the head layers together.

Nested groups:

```vns
@chargroup john face parent=head $eyes_neutral | $mouth_smile
@chargroup john head pivot=0.5,0.28 $head_base | $face
```

Runtime targets:

```jes
timeline {
  move "john_head" {
    x: 8
    y: -4
    dur: 180
  }
}
```

When a scene is launched into Puppeteer, group targets are created alongside the individual layer targets:

| Target | Meaning |
|--------|---------|
| `john_head` | Stable group alias; works across expression swaps when the group is visible. |
| `john_neutral_head` | Expression-specific group alias. |
| `john_head_base` | Stable individual layer alias. |
| `john_neutral_head_base` | Expression-specific individual layer alias. |

Use group targets for broad posing and individual layer targets for fine adjustments. If both are animated, the group transform is applied first, then the individual layer transform.

---

## `@charpreset`

Builds an expression from layer references and/or literal paths.

```text
@charpreset <characterId> <expressionId> <layerSpec>
```

### Layer reference rules

- `$layerId` — resolves from the **same** character's `@charlayer` declarations.
- `$groupId` — expands an earlier `@chargroup` when no layer with that name exists.
- `$otherChar.layerId` or `$otherChar:layerId` — resolves from **another** character's layers.
- Direct paths can be mixed with references, separated by `|`.
- Layer spec cannot be empty (parse error).
- Group expansion preserves the group's layer order exactly where `$groupId` appears in the preset.

**Examples:**

```vns
# Basic preset from same-character layers
@charpreset lavender neutral $base | $eyes_neutral | $mouth_smile
@charpreset lavender happy $base | $eyes_happy | $mouth_smile
@charpreset lavender angry $base | $eyes_angry | $mouth_frown
@charpreset lavender talking $base | $eyes_neutral | $mouth_open

# Mixing a reference with a literal path
@charpreset lavender special $base | $eyes_happy | assets/characters/lavender/mouth/custom_grin.png

# Cross-character layer reference
@charpreset twin_sister neutral $base | $lavender.eyes_neutral | $mouth_smile
```

**Full workflow example:**

```vns
@character lavender "Lavender"
@charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
@charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
@charlayer lavender eyes_happy assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_happy.png
@charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
@charlayer lavender mouth_open assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_open.png

@charpreset lavender neutral $base | $eyes_neutral | $mouth_smile
@charpreset lavender happy $base | $eyes_happy | $mouth_smile
@charpreset lavender talking $base | $eyes_neutral | $mouth_open

@label start
[show lavender center neutral]
lavender: Hello there.

[show lavender center happy]
lavender: Nice to meet you!
```

---

## `@displaypreset`

Defines a reusable group of display-slot sprite instances. Use this when a body, head, table overlay, or other scene-positioned sprite set should be authored once and shown together later.

```text
@displaypreset <presetId>
<slotId> = <characterId> <position|at x,y[,z]> [expression] [z=<layerOrder>]
```

Example:

```vns
@displaypreset x_bust
body = x_body center neutral z=0
head = x_head center neutral z=10

[showpreset x_bust]
[move @head at 0.5,0.72 ease_out_quad 240]
[hidepreset x_bust]
```

For full-scene transparent PNGs that are already positioned in the art canvas:

```vns
@displaypreset lunch_table
body = lunch_body at 0.5,1.0 neutral z=0
head = lunch_head at 0.5,1.0 neutral z=10

[showpreset lunch_table]
```

Short presets can be written inline:

```vns
@displaypreset x_bust body = x_body center z=0 | head = x_head center z=10
```

- The left side (`body`, `head`) becomes the display slot id.
- The right side uses the same position, inline `at x,y[,z]`, expression, and `z=` conventions as `[show]`.
- Define the preset before `[showpreset]`, `[movepreset]`, or `[hidepreset]` uses it.
- For reusable character rigs, place the preset in the character setup/include script. For scene-only sprite sets, place it near that scene.

See [Character Display Slots](../presentation/vns-display-slots.md) for movement and migration examples.

---

## `@position`

Defines a named custom position for character placement. Custom positions let you place characters at arbitrary screen coordinates instead of the five predefined slots.

```text
@position <name> <x> [y]
```

- `name` is a unique identifier used in `[show]` and `[move]` commands.
- `x` is the horizontal screen fraction (`0.0` = left edge, `1.0` = right edge).
- `y` is the optional vertical screen fraction (`0.0` = top, `1.0` = bottom). Defaults to `0.85` when omitted (standard character baseline).
- Values are normalized to the game's target resolution.
- Duplicate names in the same script are parse errors.

**Examples:**

```vns
# Character standing on a balcony (slightly left, raised)
@position balcony 0.3 0.6

# Character at far edge, default vertical baseline
@position doorway 0.1

# Character at a podium (centered, slightly forward)
@position podium 0.5 0.75
```

**Usage in commands:**

```vns
@position balcony 0.3 0.6
@position doorway 0.1

[show hero balcony neutral]
hero: The view from up here is breathtaking.

[move hero doorway]
hero: I should head inside.
```

Named custom positions work everywhere predefined positions (`center`, `left`, etc.) are accepted — in `[show]`, `[move]`, and `[char ... move/at]` commands.

If you need multiple sprites at the same visual position, prefer display slots (`slot=<id>`) over duplicate `@position` declarations with identical coordinates.

**See also:** [Characters & Sprites — Custom Positions](../presentation/vns-characters.md#custom-positions) for inline `at x,y[,z]` syntax and full examples, and [Character Display Slots](../presentation/vns-display-slots.md) for same-position sprite instances.

---

## `@label`

Declares a named jump target in the script.

```text
@label <name>
```

- Label name pattern: `^[A-Za-z_][A-Za-z0-9_.:-]*$`
- Duplicate labels in the same script are parse errors.
- Labels are used by `[jump]`, choices (`-> target`), and `[if ... goto ...]`.

**Legacy form (still accepted):**

```text
label <name>
```

**Examples:**

```vns
@label start
narrator: The story begins.

@label chapter1_intro
narrator: Chapter 1.

@label route_a.ending
narrator: Route A ending.

@label hub:return_point
narrator: Back at the hub.
```

---

## `@var`

Declares and initializes a variable at parse time.

```text
@var key = value
@var key value
@var flagOnly
```

- Emits an equivalent `[set key value]` command at the position it appears.
- `@var flagOnly` (no value) defaults to `true`, making it a boolean flag.

**Examples:**

```vns
# Set a numeric variable
@var score = 0
@var player_level = 1

# Set a string variable
@var player_name = Alice
@var difficulty = normal

# Set boolean flags
@var seen_intro
@var has_key
@var debug_mode = false
```

**Equivalent runtime behavior:**

```vns
# These two are identical:
@var score = 0
[set score 0]
```

---

## `@define`

Defines a compile-time macro for text substitution.

```text
@define KEY value
@define KEY "quoted value"
```

- Substitutes `${KEY}` tokens in **all subsequent script lines** during parsing.
- This is parser-time text replacement (not runtime interpolation).
- Useful for build-level constants like version strings, character name overrides, or path prefixes.

**Examples:**

```vns
@define PLAYER_NAME "Alice"
@define VERSION "1.2.0"
@define ASSET_ROOT assets/demo

@character hero "${PLAYER_NAME}"
@background park ${ASSET_ROOT}/backgrounds/park.png
@charimg hero neutral ${ASSET_ROOT}/characters/hero/neutral.png

@label start
hero: My name is ${PLAYER_NAME}. (Build ${VERSION})
```

After macro substitution, the parser sees:

```vns
@character hero "Alice"
@background park assets/demo/backgrounds/park.png
hero: My name is Alice. (Build 1.2.0)
```

**Key difference from `${var}` runtime interpolation:**
- `@define` runs at **parse time** — the substituted text is baked into the scenario.
- `${variableName}` in dialogue runs at **runtime** — it reads from the live variable map.

---

## `@include`

Includes another VNS file's content at the current position.

```text
@include <path>
```

- Requires an include resolver (provided by runtime and editor).
- Path is resolved relative to the current source file.
- Include cycles are detected and rejected with a parse error.
- Useful for shared character declarations, common labels, or reusable dialogue segments.

**Examples:**

```vns
# Include shared character definitions
@include common/characters.vns

# Include a reusable dialogue segment
@include common/opening_narration.vns

# Include system-level helpers
@include system/debug_commands.vns
```

**Typical project structure using includes:**

```text
scripts/
  common/
    characters.vns     # shared @character and @charimg declarations
    backgrounds.vns    # shared @background declarations
  story/
    prologue.vns       # @include common/characters.vns
    chapter1.vns       # @include common/characters.vns
```

**`common/characters.vns`:**

```vns
@character hero "Aria"
@character mentor "Professor Vale"
@charimg hero neutral assets/characters/aria/neutral.png
@charimg hero happy assets/characters/aria/happy.png
@charimg mentor neutral assets/characters/vale/neutral.png
```

**`story/prologue.vns`:**

```vns
@scenario prologue
@include common/characters.vns
@include common/backgrounds.vns

@label start
[bg classroom]
[show hero center neutral]
hero: Here we go.
```

---

## `@stagepreset` — Stage Lighting Preset

Loads a Scene Lighting Studio export (`.stagepreset` file) as a named stage preset available at runtime via the `[stage]` command.

### Syntax

```text
@stagepreset <id> <path>
```

### Parameters

| Parameter | Description |
|-----------|-------------|
| `id` | Unique preset identifier used in `[stage <id>]` |
| `path` | Relative path to the `.stagepreset` file exported from Scene Lighting Studio. Quote the path if it contains spaces. |

### Example

```vns
@stagepreset sunset_park config/stage/sunset_park.stagepreset
@stagepreset night_cave config/stage/night_cave.stagepreset

@label start
[bg park]
[stage sunset_park]
narrator: The park glows in the evening light.

[transition FADE 800 cave]
[stage night_cave]
narrator: The cave is dimly lit.

[stage clear]
narrator: Back to normal lighting.
```

### Stage Preset File Format

Stage preset files are Java `.properties` content with a `.stagepreset` extension. They are exported from Scene Lighting Studio and typically start with VNS handoff metadata:

```properties
# JVN Stage Preset
# @stagepreset sunset_park config/stage/sunset_park.stagepreset
# [stage sunset_park]
jvn.stagePreset.schema=2
jvn.stagePreset.id=sunset_park
jvn.stagePreset.file=sunset_park.stagepreset
jvn.stagePreset.vnsDeclaration=@stagepreset sunset_park config/stage/sunset_park.stagepreset
jvn.stagePreset.vnsCommand=[stage sunset_park]
jvn.stagePreset.lightCount=3
jvn.stagePreset.occluderCount=1
jvn.stagePreset.responseZoneCount=4
```

The loader accepts the ID and source path from the directive. If either is blank in an editor handoff context, it can fall back to `jvn.stagePreset.id` and `jvn.stagePreset.file`.

Stage preset files contain:

- **Background grade** — `bgTintColor`, `bgTintStrength`, `bgSaturation`, `bgContrast`, `bgOverlayColor`, `bgOverlayOpacity`
- **Lights** — `lights` count, then `light.<i>.name`, `light.<i>.shape` (radial/polygon/cone/strip/window/bounce), `light.<i>.layer` (background/character/foreground), `light.<i>.color`, `light.<i>.intensity`, `light.<i>.radius`, `light.<i>.softness`, `light.<i>.silhouette`, `light.<i>.position`, `light.<i>.source`, `light.<i>.group`, `light.<i>.muted`, `light.<i>.locked`, `light.<i>.solo`, `light.<i>.polygon`
- **Occluders** — `occluders` count, then `occluder.<i>.name`, `occluder.<i>.opacity`, `occluder.<i>.softness`, `occluder.<i>.enabled`, `occluder.<i>.polygon`
- **Response zones** — `zones` count, then `zone.<i>.name`, `zone.<i>.bounds`, `zone.<i>.surface`, `zone.<i>.depthBias`, `zone.<i>.responseScale`, `zone.<i>.rotation`, `zone.<i>.polygon`

Stage preset files are typically created using the **Scene Lighting Studio** editor sidebar, not hand-authored.

---

## Directive Ordering Rules

1. `@scenario` must come before any content (if used).
2. `@define` and `@include` are processed before other directives on each line.
3. `@character`, `@background`, `@charimg`, `@charlayer`, `@chargroup`, `@charpreset`, `@displaypreset`, `@position`, `@stagepreset` can appear in any order relative to content, but references are resolved when read: declare `@charlayer` before `@chargroup`/`@charpreset`, declare a group before another group or preset uses `$groupId`, and declare `@displaypreset` before `[showpreset]`, `[movepreset]`, or `[hidepreset]` uses it.
4. `@var` can appear anywhere — it emits a set command at that position.
5. `@label` must be unique within the script (including included files).

**Recommended ordering:**

```vns
@scenario my_story
@define PLAYER "Alice"
@include common/characters.vns

@character narrator "Narrator"
@background park assets/backgrounds/park.png
@charimg hero neutral assets/characters/hero/neutral.png
@charlayer hero body assets/characters/hero/body.png
@charlayer hero head assets/characters/hero/head.png
@chargroup hero head_group pivot=0.5,0.28 $head
@charpreset hero layered_neutral $body | $head_group
@displaypreset hero_bust
body = hero_body center z=0
head = hero_head center z=10
@stagepreset sunset_park config/stage/sunset_park.stagepreset

@var score = 0
@position balcony 0.3 0.6

@label start
# ... story content ...
```

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md)
- [Characters & Sprites](../presentation/vns-characters.md) — detailed character system docs, custom positions
- [Variables & Conditions](vns-variables.md) — runtime variable system
- [Parsing Internals](../internals/vns-parsing.md) — how directives are processed
- [Scene Lighting Studio](../../../editor/sidebars/right/sidebar-image-tint-tool.md) — visual stage preset authoring
