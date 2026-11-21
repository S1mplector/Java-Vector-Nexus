# Editor

The JVN Editor is the main authoring environment for VN scripts, menu/config assets, timeline flow, and runtime launch.

## Who This Is For

Use this page if you are getting comfortable with the editor itself: panels, tabs, project run behavior, and where different tools live.

## What You Will Learn

This page explains:

- the main editor layout
- what each major area is responsible for
- how files open into different tool tabs
- how project run and build from the editor work

## Read This Next

- Need the full docs map: [Documentation Index](../../INDEX.md)
- Need the editor docs hub: [JVN Editor Docs](../README.md)
- Need a beginner route through JVN: [Choose Your Path in JVN](../../guides/choose-your-path.md)
- Need editor or launcher preferences: [Editor And Launcher Settings](settings.md)
- Need sidebar tool coverage: [Sidebar Utilities Overview](../sidebars/overview/sidebar-utilities.md)

Launch command:

```bash
./jvnw editor
```

## Layout Overview

### Startup progress

The editor and launcher show a glossy green loading bar with the current startup task.
Environment checks run in the background. Preferences, editor plugins, window construction,
and first-frame layout then run in order before the splash closes. The bar tracks completed
milestones, not elapsed time; slower steps can hold it in place. The five-second minimum is distributed across the check and initialization messages, so
fast steps remain readable rather than leaving a long pause at “Ready”. Expensive window
initialization steps consume their display interval while they run. Window construction uses the
JavaFX UI thread, so the animation may pause during an individual expensive initialization step.

Preflight failures keep diagnostics and a **Retry** action visible. If window initialization
fails after services have started, the splash offers **Quit** and explains that a relaunch is
required, avoiding duplicate partially initialized services. Release notes open only after
successful startup.

### Startup welcome dashboard

- The center area opens with a `Welcome` tab by default.
- Shows:
  - JVN logo + editor version
  - recent projects (with one-click open)
  - workspace/project health checks (Java, Gradle wrapper, Git, missing project files)
- Quick actions:
  - `New Project`
  - `Open Project`
  - `Refresh Health`

### Top bar

- Menus: `File`, `Edit`, `View`, `Navigate`, `Run`, `Build`, `Tools`, `Version Control`, `Window`, `Help`
- Main actions: `Open`, `Save`, `Undo`, `Redo`, `Apply Code`, `Fullscreen`
- Status + performance strip (CPU/GPU/RAM/FPS)
- In Engine Hub Developer Mode, a collapsed `Logs` panel appears under the top bar for crude log-file viewing.
- In Engine Hub Developer Mode, a `DevTools` menu appears in the menu bar for runtime diagnostics, log refresh, manual GC, and next-launch editor heap settings.
- `Cmd/Ctrl+,` opens Editor Settings.

### Left panel: Project Explorer

- Project tree with filter box
- Root-level **Run** button for full project runtime launch
- Root-level **Build** button for the game build/publish popup
- Context menu actions:
  - open
  - reveal in file manager
  - new JES/VNS/Java/folder
  - rename
  - delete

### Center panel: Tabbed editors

Files open into typed tabs via `FileEditorTab` dispatch:

- VNS (`.vns`) -> VNS code editor + VN preview
- JES (`.jes`) -> JES code editor + viewport preview
- Timeline (`.timeline`) -> timeline graph + timeline code editor
- Menu/theme/layout config -> text-first Layout Studio with diagnostics and runtime launch
- General text/code formats -> text editor

For VNS tabs, diagnostics are routed to the side diagnostics panel to preserve vertical editor space.

### Side panels (+ chooser system)

- Left defaults to `Project` tab.
- Right starts empty; use `+` to open a panel chooser.
- Common addable panels include:
  - `Project`
  - `Timeline`
  - `VNS Diagnostics`
  - `Label Flow`
  - `Assets`
  - `Version Control`
  - `Inspector`
  - `Puppeteer Launcher`
  - `Storyboard Overlay`
  - `Layered Image Visualizer`
  - `Image Attributes Tool`
  - `Scene Lighting Studio`
  - `Layout Launcher`
  - `Text Editor`

For the complete panel map, use [Sidebar Utilities Overview](../sidebars/overview/sidebar-utilities.md).

## Project Run Behavior

Project run from editor:

1. Reads `jvn.project` from project root.
2. For VN projects (`type=vn`), launches runtime with:
   - `--assets <projectRoot>`
   - normalized `--script <entryVns>` when manifest entry is defined
   - otherwise runtime-side entry resolution (`entryVns`/property/discovery)
3. Opens the [Run workspace](run-console.md) for output, filtering and runtime inspection. Source launches use the configured Gradle policy. **Fast Launch** reuses the daemon and caches. Choose **Compatibility** in Gradle Launch Options to use the project-local Gradle home.

## Game Build & Publish

Choose **Build → Build & Publish...** or the **Build** action at the project root. Use **Package** to select a format and target, **Checks** for dependency and test results, **Release** for the plan and release profile, and **Artifacts** for outputs and commands.

Follow the [Build workspace walkthrough](build-workspace.md) to produce a test build. For task names, target combinations and profile keys, see the [build system reference](../../project-setup/release/build-system.md).

## Tool Access Patterns

The editor now exposes most tooling through more than one route so teams can keep a stable workflow even when the sidebar layout changes:

- **Sidebar chooser (`+`)** for persistent left/right panel placement
- **View -> Panels** for panel-first navigation
- **Navigate** for quick jumps to the same tools
- **Tools** for task-oriented grouping
- **Window -> Open Tool Window** for floating utility windows such as Storyboard Overlay

This matters for workflow-critical utilities such as **Build & Publish...**, **Storyboard Overlay**, **Version Control**, **Puppeteer Launcher**, and **Scene Lighting Studio**, because they can be reopened from the menu system even when their sidebar tab is closed.

## Supported Editing Modes

### VNS editor

- syntax highlighting
- strict parser diagnostics (same parser as runtime)
- compact VNS tool strip with dedicated actions for:
  - run from the nearest `@label` above the caret (`F5`)
  - run from the project entry point (`Shift+F5`)
  - open the label/symbol navigator (`Cmd/Ctrl+Shift+O`)
  - open the VNS snippet picker (`Cmd/Ctrl+J`)
  - find text in the current script (`Cmd/Ctrl+F`)
  - search all editor actions in the command palette (`Cmd/Ctrl+Shift+P`)
  - toggle line wrapping (`Cmd/Ctrl+Shift+W`)
  - compare the current buffer with the saved version (`Cmd/Ctrl+Shift+D`)
  - open the diagnostics panel
  - open the runtime preview in a separate window
  - open the standard `?` help panel for these controls
- native Aero command artwork shared with Build Project and Run Project, grouped
  44 × 42 hit targets, glass-and-metal hover chrome, accessible action names, and
  distinct silhouettes for each run, navigation, authoring, review, and preview action
- all text authoring stays in the main tabbed editor; the legacy Text Editor
  workspace and standalone text window are no longer exposed
- lint for:
  - undefined labels
  - missing assets/background ids
  - unreachable labels/blocks
- quick fixes via context menu on diagnostics:
  - create missing label
  - replace label with existing one
  - replace missing asset path from discovered assets
  - remove unreachable block
- side-panel integrations:
  - `VNS Diagnostics` panel with filter + click-to-jump lines
  - `Label Flow` graph panel for label/jump/choice flow
- persisted VNS authoring defaults in **Editor Settings** for long-line wrapping and minimap
  visibility; changes apply to open VNS tabs immediately
- minimap hover tooltips give context per marker kind: label markers show the label
  name and line; dialogue/text lines show the nearest speaker above them; timeline
  bands show the line range, duration, track count, and action count (parsed from
  that block's own text, cached per script snapshot so hovering stays cheap on large
  scripts)
- folded `timeline { ... }` blocks are remembered per script and restored when the
  script is reopened, so a large pasted animation stays collapsed instead of
  reappearing expanded every time; state is keyed by each block's position among
  the script's timeline blocks plus a hash of its header line, so it survives edits
  that shift line numbers and is dropped (not misapplied to another block) if the
  block itself changed or moved; stored in
  `<project>/config/editor/timeline-folds.properties`

The VNS strip is action-only: it does not repeat explanatory preview text. Hover
an icon for its short description or use the `?` control for the complete key
map.

### JES editor

- syntax highlighting
- parser lint diagnostics
- live apply into preview viewport

### Timeline editor

- graph editing + text DSL editing
- arc/link validation against script existence and labels
- drag-and-drop `.vns` files to create arcs quickly
- cluster filtering/collapse and auto layout

### Menu flow editor

- dedicated graph for menu-to-menu navigation wiring across `config/menu/menus/*.menu`
- visual wiring support for:
  - `OPEN_MENU` (click source item, then click target menu node)
  - `MAIN_MENU`
  - `BACK`
- inline item action/target editing per selected menu screen
- validation for:
  - missing `OPEN_MENU` targets
  - unknown menu targets
  - missing standard targets (`main`, `load`, `save`, `settings`) when referenced
  - duplicate/empty item ids
  - unreachable menus from `main`
- save one screen or save all modified screens

### Layout configuration authoring

These open in dedicated external **Layout Studio** windows with:

- DSL-aware source editing and line diagnostics
- complete, commented source templates
- direct Save / Reload / Save and Run Runtime
- asset utilities (browse/import/copy/apply path helpers)

The supported sources are `config/ui/dialogue.layout`, `config/menu/menus/*.menu`,
`config/menu/layouts/*.layout`, and `config/menu/styles/*.style`. They are ordinary properties files
consumed directly by the runtime. Layout Studio does not maintain a parallel form model or preview
renderer; use Save and Run Runtime for authoritative rendering and interaction checks.

## New Project Wizard

The wizard creates a layered VN project scaffold with:

- `config/settings`, `config/story`, `config/ui`, `config/menu/...`
- `scripts/story`, `scripts/common`, `scripts/system`
- structured `assets/` subfolders for backgrounds/characters/audio/ui/fonts
- `jvn.project` manifest with entry script and config paths
- optional Git repository + initial commit

See full wizard documentation:
- [New Project Wizard](../../project-setup/onboarding/new-project-wizard.md)

## Keyboard Shortcuts

- `Cmd/Ctrl+O` -> Open Project
- `Cmd/Ctrl+Shift+O` -> Open JES file
- `Cmd/Ctrl+Alt+O` -> Open VNS file
- `Cmd/Ctrl+S` -> Save
- `Cmd/Ctrl+Shift+S` -> Save As
- `Cmd/Ctrl+W` -> Close Tab
- `Cmd/Ctrl+Enter` -> Apply Code
- `F11` -> Toggle editor fullscreen (for split preview+editor tabs)
- `Cmd/Ctrl+Z` -> Undo
- `Cmd/Ctrl+Shift+Z` -> Redo
- `Cmd/Ctrl+Shift+G` -> Open/select Version Control
- `Cmd/Ctrl+Shift+H` -> Open/select Welcome tab

## VNS Preview Virtual Viewport

The VNS preview renders at the **game's target resolution** (read from `jvn.project`), then scales to fit the editor canvas. This ensures that what you see in the editor preview matches exactly what the player sees at runtime.

### How it works

1. `ProjectViewportSpec` reads `width` and `height` from `jvn.project` (defaults to 1920×1080).
2. The preview computes a uniform scale factor: `min(canvasWidth / gameWidth, canvasHeight / gameHeight)`.
3. The scene is rendered at the virtual game resolution inside a `gc.scale()` transform.
4. Black letterbox bars fill any remaining canvas area when aspect ratios differ.
5. Mouse coordinates are inverse-transformed from canvas space → virtual space so clicks, hover detection, and overlays work correctly.

### Why this matters

- **Puppeteer timelines** use absolute pixel coordinates (e.g., `x: 739, y: 140`) authored for the game resolution. Without the virtual viewport, these coordinates go off-screen in the smaller editor preview.
- **Custom character positions** use screen fractions that resolve to game-resolution pixels. The preview must render at that resolution for correct placement.
- **Dialogue and UI layouts** are resolution-dependent. The preview now shows the correct textbox proportions and choice button positions.

### Configuration

The game resolution is set in `jvn.project`:

```properties
width=1920
height=1080
```

This is automatically configured by the New Project Wizard based on the selected resolution. If no `jvn.project` exists, the preview defaults to 1920×1080.

Source: `modules/editor/src/main/java/com/jvn/editor/ui/VnPreviewView.java`, `modules/editor/src/main/java/com/jvn/editor/ui/ProjectViewportSpec.java`

---

## Recommended Team Workflow

1. Keep story logic in VNS with clear label naming.
2. Keep UI tuning in config files edited through visual tools when practical.
3. Use timeline graph for arc-level narrative structure.
4. Run from project tree root button frequently for full end-to-end checks.
5. Keep source control clean with the Version Control panel (`Cmd/Ctrl+Shift+G`).
6. Use the public documentation website or repository [Documentation Index](../../INDEX.md) for reference.
