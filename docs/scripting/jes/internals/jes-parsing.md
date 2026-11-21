# JES Parsing Internals

> **Documentation role:** This page describes the current implementation. It does not override the
> normative [JES 1 specification](../../spec/v1/jes.md) or the
> [diagnostic contract](../../spec/diagnostics.md).

Complete reference for the JES parsing pipeline — tokenizer rules, grammar, AST structure, strict property validation, error reporting, and the loader stage.

Source files:
- `modules/scripting/src/main/java/com/jvn/scripting/jes/JesTokenizer.java`
- `modules/scripting/src/main/java/com/jvn/scripting/jes/JesParser.java`
- `modules/scripting/src/main/java/com/jvn/scripting/jes/JesLoader.java`
- `modules/scripting/src/main/java/com/jvn/scripting/jes/ast/JesAst.java`

---

## Pipeline Overview

Each stage has distinct responsibilities and error reporting. Errors at any stage halt processing with source position information.

---

## Stage 1: Tokenizer

The `JesTokenizer` converts raw source text into a flat list of `JesToken` values, each carrying a type, lexeme, line number, and column number.

### Token Types

| Token Type | Pattern | Examples |
|-----------|---------|---------|
| `IDENT` | Letter/underscore start, then letters/digits/underscores/dots | `scene`, `Panel2D`, `ease_out_back` |
| `STRING` | Double-quoted, with escape sequences | `"Hello"`, `"assets/img.png"` |
| `NUMBER` | Optional minus, digits, optional decimal point | `42`, `-3.5`, `0.75` |
| `LBRACE` | `{` | |
| `RBRACE` | `}` | |
| `COLON` | `:` | |
| `COMMA` | `,` | |
| `LPAREN` | `(` | |
| `RPAREN` | `)` | |
| `EOF` | End of input | |

### Tokenizer Rules

- **Identifiers** start with a letter or `_`, followed by letters, digits, `_`, or `.` (dots allow `rgb` function names and dotted identifiers)
- **Numbers** support negative values (`-3`), integers (`42`), and decimals (`0.75`)
- **Strings** use `"..."` delimiters with escape sequences: `\\`, `\"`, `\n`, `\t`
- **Comments** use `//` to end-of-line (skipped entirely during tokenization)
- **Whitespace** (spaces, tabs, newlines) is skipped; newlines increment the line counter
- **Unknown characters** throw `JesParseException` with the exact line and column

### Tokenizer Error Examples

```jes
scene "Demo" { @ }
```

```text
JesParseException: Unexpected character '@' at line 1, col 17
```

```jes
scene "Unterminated string {
```

```text
JesParseException: Unterminated string literal at line 1, col 7
```

---

## Stage 2: Parser

The `JesParser` consumes the token stream and builds an AST (`JesAst.Program`).

### Grammar (Informal BNF)

```text
program        = scene+
scene          = "scene" STRING "{" scene_body* "}"
scene_body     = tileset | item | map | entity | input_binding | timeline | scene_prop

tileset        = "tileset" STRING "{" prop* "}"
item           = "item" STRING "{" prop* "}"
map            = "map" STRING "{" prop* layer* "}"
layer          = "layer" STRING "{" prop* "}"

entity         = "entity" STRING "{" component* "}"
component      = "component" IDENT "{" prop* "}"

input_binding  = "on" "key" STRING "do" IDENT prop_block?
timeline       = "timeline" "{" timeline_action* "}"

timeline_action = action_type target? prop_block?
               | "wait" NUMBER
               | "parallel" "{" timeline_action* "}"
               | "loop" prop_block? "{" timeline_action* "}"
               | "label" prop_block
               | "jump" prop_block

action_type    = "move" | "rotate" | "scale" | "fade" | "visible"
               | "cameraMove" | "cameraZoom" | "cameraShake" | "cameraFollow"
               | "playAudio" | "stopAudio" | "emitParticles"
               | "damage" | "heal" | "waitForCall" | "call"
               | "walkToTile" | "pivot" | "setParallax"

target         = STRING
prop_block     = "{" prop* "}"
prop           = IDENT ":" value
value          = STRING | NUMBER | "true" | "false" | rgb_call | IDENT
rgb_call       = ("rgb" | "rgba") "(" NUMBER "," NUMBER "," NUMBER ("," NUMBER)? ")"
scene_prop     = IDENT ":" value
```

### AST Structure

The parser produces these AST node types:

| Node | Fields | Description |
|------|--------|-------------|
| `Program` | `List<SceneDecl>` | Root — one or more scenes |
| `SceneDecl` | `name`, `entities`, `bindings`, `timeline`, `tilesets`, `items`, `maps`, `props` | One scene block |
| `EntityDecl` | `name`, `List<ComponentDecl>` | An entity with components |
| `ComponentDecl` | `type`, `Map<String, Object> props` | A component with properties |
| `TilesetDecl` | `name`, `props` | Tileset definition |
| `ItemDecl` | `id`, `props` | Item definition |
| `MapDecl` | `name`, `props`, `List<LayerDecl>` | Tile map with layers |
| `LayerDecl` | `name`, `props` | Map layer |
| `InputBinding` | `key`, `action`, `props` | Input key binding |
| `TimelineAction` | `type`, `target`, `props`, `children` | Timeline action |

### Value Types in AST

| JES Syntax | AST Java Type |
|-----------|--------------|
| `"hello"` | `String` |
| `42`, `3.5` | `Double` |
| `true`, `false` | `Boolean` |
| `rgb(1, 0.5, 0, 1)` | `double[4]` |
| `left`, `circle` | `String` (bare ident) |

---

## Strict Property Validation

The parser enforces **known property sets** for components and timeline actions. This catches typos at parse time rather than silently ignoring them at runtime.

### Component Property Validation

Each known component type has an explicit set of allowed properties:

| Component | Allowed Properties |
|-----------|--------------------|
| `Panel2D` | `x`, `y`, `w`, `h`, `fill` |
| `Sprite2D` | `image`, `x`, `y`, `w`, `h`, `alpha`, `originX`, `originY`, `sx`, `sy`, `sw`, `sh`, `dw`, `dh` |
| `Label2D` | `text`, `x`, `y`, `size`, `bold`, `color`, `align` |
| `ParticleEmitter2D` | `x`, `y`, `emissionRate`, `minLife`, `maxLife`, `minSize`, `maxSize`, `endSizeScale`, `minSpeed`, `maxSpeed`, `minAngle`, `maxAngle`, `gravityY`, `texture`, `additive`, `startColor`, `endColor` |
| `PhysicsBody2D` | `shape`, `x`, `y`, `w`, `h`, `r`, `mass`, `restitution`, `static`, `sensor`, `vx`, `vy`, `color`, `onTrigger` |
| `Character2D` | `spriteSheet`, `frameW`, `frameH`, `cols`, `drawW`, `drawH`, `x`, `y`, `startTileX`, `startTileY`, `speed`, `originX`, `originY`, `animations`, `startAnim`, `dialogueId`, `z`, `controllable` |
| `Stats` | `maxHp`, `hp`, `maxMp`, `mp`, `atk`, `def`, `speed`, `onDeathCall`, `removeOnDeath` |
| `Inventory` | `slots`, `items` |
| `Ai2D` | `type`, `target`, `aggroRange`, `attackRange`, `attackIntervalMs`, `attackAmount`, `moveSpeed`, `attackCooldownMs`, `patrolRadius`, `patrolIntervalMs`, `requiresLineOfSight`, `guardRadius`, `fleeDistance` |
| `Button2D` | `x`, `y`, `w`, `h`, `text`, `call`, `normal`, `hover`, `pressed`, `textColor`, `fontSize` |
| `Slider2D` | `x`, `y`, `w`, `h`, `min`, `max`, `value`, `call`, `trackColor`, `fillColor`, `knobColor` |

**Free-form components:** `Equipment` allows any property key (slot names are user-defined).

**Unknown component types:** If the type name isn't in the known list, all properties are allowed. This enables extension without parser changes.

### Timeline Action Validation

| Action | Allowed Properties |
|--------|--------------------|
| `move` | `x`, `y`, `dur`, `easing` |
| `rotate` | `deg`, `dur`, `easing` |
| `scale` | `sx`, `sy`, `dur`, `easing` |
| `fade` | `alpha`, `dur`, `easing` |
| `visible` | `value` |
| `pivot` | `ox`, `oy`, `dur`, `easing` |
| `walkToTile` | `tx`, `ty`, `x`, `y`, `dur`, `easing` |
| `cameraMove` | `x`, `y`, `dur`, `easing` |
| `cameraZoom` | `zoom`, `dur`, `easing` |
| `cameraShake` | `ampX`, `ampY`, `dur` |
| `cameraFollow` | `target`, `lerp`, `offsetX`, `offsetY`, `deadZoneW`, `deadZoneH` |
| `playAudio` | `id`, `volume`, `loop`, `bgm` |
| `stopAudio` | `id` |
| `emitParticles` | `count` |
| `damage` | `amount`, `source` |
| `heal` | `amount`, `source` |
| `waitForCall` | `name` |
| `setParallax` | `px`, `py` |
| `loop` | `count`, `until` |
| `parallel` | (none — only contains children) |
| `label` | `name` |
| `jump` | `target` |

**Free-form actions:** `call` allows any property (call handler props are user-defined).

---

## Parser Error Examples

### Unknown component property

```jes
scene "Demo" {
  entity "logo" {
    component Sprite2D { x: 1 bogus: 2 }
  }
}
```

```text
JesParseException: Unknown property 'bogus' for component 'Sprite2D' at line 3, col 30
```

### Unknown timeline action

```jes
scene "Demo" {
  timeline {
    fly "hero" { x: 1 }
  }
}
```

```text
JesParseException: Unknown timeline action 'fly' at line 3, col 5
```

### Unterminated block

```jes
scene "Demo" {
  entity "hero" {
    component Sprite2D { x: 1
```

```text
JesParseException: Unterminated component block at line 3
```

### Missing expected token

```jes
scene "Demo" {
  entity "hero"
    component Sprite2D { x: 1 }
```

```text
JesParseException: Expected '{' at line 3
```

### Unexpected character in tokenizer

```jes
scene "Demo" { $ }
```

```text
JesParseException: Unexpected character '$' at line 1, col 16
```

---

## Stage 3: Loader

`JesLoader` converts the AST into live runtime objects (`JesScene2D`).

### Loader Responsibilities

1. **Tilesets** — creates `SpriteSheet` from tileset declarations
2. **Items** — registers item definitions by ID
3. **Maps** — constructs tile map, sets collision grid, registers trigger layers
4. **Entities** — creates runtime objects per component type:
   - `Panel2D` → colored rectangle
   - `Sprite2D` → image sprite (with optional region)
   - `Label2D` → text label
   - `ParticleEmitter2D` → particle system
   - `PhysicsBody2D` → rigid body + visual wrapper
   - `Character2D` → animated sprite-sheet character
   - `Stats` / `Inventory` / `Equipment` → RPG data
   - `Ai2D` → AI behavior controller
   - `Button2D` / `Slider2D` → UI widgets
5. **Input bindings** — registers keyboard handlers
6. **Timeline** — injects timeline action list for `JesScene2D` to execute

### Default Values

The loader applies defaults when properties are omitted. See [Component Reference](../scene/components.md) for the complete default value table per component.

### Entity Registration

Each entity is registered by name via `scene.registerEntity(name, object)`. This enables:
- Timeline actions targeting entities by name
- Call handlers looking up entities
- Inspector panel showing entity properties

---

## Runtime Execution

`JesScene2D` drives the loaded scene:

- **Input** — key bindings dispatch to registered action handlers
- **Physics** — `PhysicsWorld2D` steps, collisions detected, trigger callbacks fired
- **AI** — `Ai2D` controllers update per frame
- **Timeline** — actions execute sequentially with:
  - `wait` pauses
  - `waitForCall` blocks until a named call fires
  - `parallel` runs children simultaneously
  - `loop` repeats children
  - `label` / `jump` for branching
  - `call` invokes registered handlers

---

## Editor Integration

The JES parser powers several editor features:

- **Syntax highlighting** — token types map to colors
- **Error markers** — parse errors show as red underlines with tooltips
- **Auto-complete** — known component types and property names
- **Inspector** — parsed entities populate the sidebar inspector
- **Live preview** — the JES viewport loads and renders the parsed scene

---

## Why Strict Parsing Matters

1. **Catches typos before runtime** — `bogus: 2` on a Sprite2D fails immediately
2. **Improves editor diagnostics** — precise line/column error markers
3. **Enables CI validation** — parse all `.jes` files in a build step
4. **Keeps contracts clear** — team members know exactly which properties are valid
5. **Extension-safe** — unknown component *types* are still allowed for custom extensions

---

## Related Docs

- [JES Overview](../overview/jes-scripting.md)
- [Component Reference](../scene/components.md)
- [Timeline & Actions](../timeline/jes-timeline.md)
- [VNS Parsing Internals](../../vns/internals/vns-parsing.md)
