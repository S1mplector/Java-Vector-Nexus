# Asset Management & Path Resolution

Complete guide to how JVN discovers, loads, and resolves assets at runtime — the `AssetCatalog`, filesystem vs classpath loading, path conventions, and troubleshooting missing assets.

Core classes:
- `modules/core/src/main/java/com/jvn/core/assets/AssetCatalog.java`
- `modules/core/src/main/java/com/jvn/core/assets/AssetPaths.java`
- `modules/core/src/main/java/com/jvn/core/assets/AssetType.java`

---

## Overview

JVN assets (images, audio, scripts, config files) are loaded through a layered resolution system. At runtime, assets can come from the **filesystem** (project directory) or the **classpath** (bundled JAR). The `--assets` CLI flag controls which source is used. Understanding this system is essential for reliable asset loading in both development and production.

---

## Asset Types

The `AssetType` enum classifies assets for path construction:

| Type | Prefix | Typical Content |
|------|--------|----------------|
| `IMAGE` | `game/images/` | Backgrounds, sprites, CG |
| `AUDIO` | `game/audio/` | BGM, SFX, voice |
| `SCRIPT` | `game/scripts/` | VNS and JES scripts |
| `UI` | `game/ui/` | UI elements, button skins |
| `VIDEO` | `game/video/` | Cutscene video files |
| `CONFIG` | `game/config/` | Configuration files |

`AssetPaths.build(type, id)` constructs a canonical path:

```java
AssetPaths.build(AssetType.IMAGE, "backgrounds/park.png")
// → "game/images/backgrounds/park.png"

AssetPaths.build(AssetType.AUDIO, "bgm/title.ogg")
// → "game/audio/bgm/title.ogg"
```

---

## Resolution Modes

### Mode 1: Classpath Only (Default)

When no `--assets` flag is provided, all assets are loaded from the classpath (JAR resources). This is the production/distribution mode.

```bash
./gradlew :runtime:run --args='--script demo.vns'
```

Assets must be bundled inside the JAR at the canonical `game/` prefix paths.

### Mode 2: Filesystem + Classpath Overlay

When `--assets <projectDir>` is provided, the runtime creates a filesystem overlay that checks the project directory first, then falls back to classpath:

```bash
./gradlew :runtime:run --args='--assets /path/to/MyProject --script scripts/story/prologue.vns'
```

Resolution order:
1. `<projectDir>/<asset_path>` — filesystem first
2. Classpath resource — fallback

This is the **development/iteration mode** used by the editor's Run button.

---

For editor discovery, character ownership and VNS declarations, see [Review asset labels](../../editor/tools/asset-label-review.md). Runtime lookup resolves a file path when the game requests media.

## Path Conventions

### Project-Relative Paths

All asset references in VNS scripts, menu files, and layout files use **project-relative paths**:

```vns
@background park assets/backgrounds/park.png
@charimg hero neutral assets/characters/aria/neutral.png
[bgm assets/audio/bgm/calm.ogg]
```

```properties
# dialogue.layout
textBoxAsset=assets/ui/textbox.png

# menu style
backgroundAsset=assets/backgrounds/title.png
```

### Recommended Directory Structure

```text
MyProject/
├── assets/
│   ├── backgrounds/        # Scene backgrounds
│   │   ├── classroom.png
│   │   └── forest.png
│   ├── characters/         # Character sprites
│   │   └── aria/
│   │       ├── neutral.png
│   │       ├── happy.png
│   │       └── angry.png
│   ├── portraits/          # Character portraits/thumbnails
│   ├── cg/                 # Full-screen CG images
│   ├── ui/                 # UI elements
│   │   ├── textbox.png
│   │   ├── namebox.png
│   │   ├── choice/
│   │   └── menu/
│   ├── fonts/              # Custom font files
│   └── audio/
│       ├── bgm/            # Background music
│       ├── sfx/            # Sound effects
│       └── voices/         # Voice clips
├── scripts/
│   ├── story/              # VNS story scripts
│   ├── common/             # Shared script includes
│   └── system/             # System scripts
├── config/
│   ├── ui/                 # Dialogue layout
│   ├── menu/               # Menu configuration
│   ├── settings/           # VN settings
│   └── story/              # Story map
├── save/                   # Save files (gitignored)
├── jvn.project             # Project manifest
└── README.md
```

---

## Audio Path Resolution (Detailed)

Audio files go through an extensive multi-step resolution in the Simp3 backend:

1. **Direct path** — treat the ID as an absolute or relative file path
2. **Project root** — `<projectRoot>/<normalizedPath>`
3. **Strip project name** — handles `MyProject/assets/audio/bgm.ogg`
4. **CWD strip** — handles accidental CWD prefix duplication
5. **Demo audio paths** — `assets/demo/audio/<id>`
6. **Standard audio paths** — `assets/audio/<id>`
7. **AssetPaths.build** — construct canonical `game/audio/<id>` path
8. **Classpath candidates** — try all path variants as classpath resources
9. **Classpath extraction** — extract JAR resource to temp file for playback

This extensive chain ensures audio plays in development, production, and demo modes.

---

## Image/Background Resolution

Images referenced in VNS scripts are resolved by the renderer:

```vns
@background park assets/backgrounds/park.png
[bg park]
```

The renderer looks up the background ID from `@background` declarations, then loads the image file. The file path goes through the `AssetCatalog`:
1. Filesystem check (if `--assets` is active)
2. Classpath resource
3. Log warning if not found (render shows black/empty)

### Missing Character Layer Warnings

When a character's sprite (or one of its `@charlayer` layers) fails to resolve to an image, `VnRenderer` logs a `WARN` via `RenderDiagnostics.missingAsset` naming the character, expression, layer ID, and the path that failed:

```text
Missing asset 'assets/characters/aria/mouth_open.png' (aria:happy:mouth_open)
```

Each distinct `(path, context)` pair is logged only once per process — repeated renders of the same missing layer (e.g. every frame a character stays on screen) do not spam the log. This is always-on diagnostic logging; it doesn't require developer mode and doesn't block playback. When `-Djvn.fx.developerMode=true` is set, a labeled magenta placeholder box is also drawn over the missing layer so it's visible on screen, not just in logs (see `MissingAssetPlaceholder`).

---

## Script Resolution

VNS and JES scripts use locale-aware resolution:

```text
scripts/story/prologue.vns
```

With `--locale ja`, the loader tries:
1. `scripts/story/prologue.ja.vns`
2. `scripts/story/prologue.vns`

This enables localized script variants without changing references.

---

## Config File Resolution

Config files (`.layout`, `.style`, `.menu`, `.registry`) use multiple fallback paths. For example, `menu.registry`:

1. `config/menu/registry/menu.registry`
2. `config/menu/menu.registry`
3. `config/menu/registry.properties`
4. `menu.registry`

See [Menu Registry & File Discovery](../../scripting/ui/layout/structure/menu-registry.md) for complete details.

---

## Editor Run vs. Direct Run

### Editor Run

The editor passes `--assets <projectRoot>` automatically when you click Run. This means all project files are directly accessible by path — no packaging needed.

### Direct Gradle Run

```bash
./gradlew :runtime:run --args='--assets /Users/me/MyProject --script scripts/story/prologue.vns --ui fx --audio auto'
```

### Production Run (Packaged JAR)

Assets must be included in the classpath at their canonical paths. No `--assets` flag is needed.

---

## AssetCatalog Logging

At startup, the runtime logs asset discovery:

```text
Assets -> images=24, audio=12, scripts=8, config=6
```

If you see `images=0, audio=0`, check:
1. `--assets` points to the correct project root
2. The directory contains `assets/` and `scripts/` subdirectories
3. Paths are relative, not absolute

---

## Case Sensitivity

- **macOS/Windows:** File paths are case-insensitive. `Assets/BG/Park.PNG` works.
- **Linux:** File paths are **case-sensitive**. `assets/bg/park.png` must match exactly.
- **Classpath (JAR):** Always case-sensitive regardless of OS.

**Best practice:** Always use lowercase paths with consistent casing. This prevents cross-platform issues.

---

## Runtime Validation Checklist

- [ ] Background images load and display (not black screens)
- [ ] Character sprites appear when `[show]` is used
- [ ] Audio files play (BGM, SFX, voice)
- [ ] UI assets load (textbox, button images)
- [ ] Menu backgrounds display
- [ ] Save slot thumbnails appear
- [ ] No "file not found" warnings in console
- [ ] Asset count at startup is non-zero
- [ ] Scripts load by name (no fallback to demo scenario)

---

## Common Mistakes

**Assets count is zero:**
The `--assets` flag points to the wrong directory. It should point to the project root (the directory containing `assets/` and `scripts/`), not to `assets/` itself.

**Works in editor but not in direct run:**
Forgot `--assets <projectDir>` in the Gradle command. The editor adds this automatically.

**Case mismatch on Linux:**
`assets/BG/Park.png` in the script but `assets/bg/park.png` on disk. Fix casing to match exactly.

**Leading slash in path:**
`/assets/audio/bgm.ogg` is an absolute path. Use `assets/audio/bgm.ogg` (no leading slash).

**Asset works locally but not in production:**
The file exists on disk but wasn't bundled into the JAR. Ensure your build process includes all assets in the classpath.

---

## Related Docs

- [Runtime Guide](../core/runtime.md)
- [Audio System](audio-system.md)
- [Assets & Backgrounds (Layout)](../../scripting/ui/layout/styling/assets-backgrounds.md)
- [VNS Characters & Sprites](../../scripting/vns/presentation/vns-characters.md)
- [Performance](../../architecture/quality/performance.md)
