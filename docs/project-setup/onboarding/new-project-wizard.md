# New Project Wizard

The editor's new project wizard scaffolds a VN-ready project with layered config, scripts, assets, and manifest metadata.

Wizard class:
- `modules/editor/src/main/java/com/jvn/editor/ui/NewProjectWizard.java`

## Goals of Current Wizard

- produce clean, engine-aligned directory layout
- generate editable starter files (not opaque binary state)
- support immediate run from editor project tree
- include menu/dialogue/timeline config that the editor and runtime can open directly

## Wizard Sections

1. **Project Basics**
   - project name
   - author
   - location
   - output path preview (sanitized folder name)

2. **Engine Profile**
   - resolution preset
   - high-resolution presets up to 4K (`3840x2160`)
   - custom width/height override for non-standard targets (ultrawide, handheld, kiosk)
   - menu theme preset
   - entry script/story map/dialogue layout preview

3. **Feature Modules**
   - playable Lavender starter story
   - optional 30-lesson tutorial workspace
   - VNS/JES integration scene
   - main menu profile pack
   - save/load profile files
   - settings profile
   - history/backlog defaults

4. **Version Control**
   - initialize Git repository
   - optional initial commit

5. **Generated Layout Preview**
   - live tree preview before create

6. **Project Notes**
   - optional description for manifest + README

---

### Inline Validation

The wizard provides real-time validation feedback on critical fields:

| Field | Validation Rules | Visual Feedback |
|-------|-----------------|----------------|
| **Project name** | Non-empty, alphanumeric with underscores/hyphens | Green border = valid, Red border = error |
| **Location** | Non-empty, path exists | Green border = valid, Red border = error |
| **Output path** | Auto-generated from sanitized name | Gray display (read-only) |

A validation label at the bottom of the wizard shows the current status:
- "Ready to create" — all fields valid, Create button enabled
- "Project name cannot be empty" — specific error message
- "Location must be an existing directory" — specific error message
- "Project name must be alphanumeric" — specific error message

The Create button is disabled when validation fails.

### Tooltips

Comprehensive tooltips guide users through wizard options:

| Control | Tooltip Content |
|---------|-----------------|
| **txtProjectName** | "Enter a name for your project (alphanumeric, underscores, hyphens allowed)" |
| **txtAuthor** | "Your name or team name for project metadata" |
| **txtLocation** | "Parent directory where the project folder will be created" |
| **cmbResolution** | "Target game resolution (affects layout scaling)" |
| **cmbTheme** | "Menu theme preset for generated UI assets" |
| **cmbRuntimeUi** | "Runtime UI style (standard, minimal, or custom)" |
| **cmbAudioBackend** | "Audio system for playback (JavaFX Sound, LWJGL OpenAL, etc.)" |
| **cmbLocale** | "Primary language/locale for the game (editable for custom codes)" |
| **Playback spinners** | "Default player preferences for text speed, auto-advance, and volumes" |
| **Skip checkboxes** | "Enable skip modes for read text and unvisited text" |
| **txtInputProfilePath** | "Optional path to a custom input profile configuration" |

Tooltips appear on hover and provide context for each option.

### Editable Locale ComboBox

The locale ComboBox is editable to support custom locale codes beyond the preset list:

1. Click the locale dropdown to see common presets (en, ja, zh, ko, es, fr, de, etc.)
2. Type a custom locale code (e.g., `pt-BR`, `ru-RU`, `ar-SA`) if your language isn't listed
3. The wizard accepts any valid locale code format

### Unicode Box-Drawing

The structure preview tree uses proper Unicode box-drawing characters for a polished appearance:

```
├── config/
│   ├── settings/
│   │   └── vn.settings
│   ├── ui/
│   │   └── dialogue.layout
│   └── menu/
│       ├── registry/
│       │   └── menu.registry
│       ├── menus/
│       │   ├── main.menu
│       │   ├── load.menu
│       │   └── settings.menu
│       ├── layouts/
│       │   └── default.layout
│       └── styles/
│           └── default.style
```

Conditional entries (like save/load/settings menus) use dynamic connectors to show they're optional based on feature selections.

### Fullscreen Toggle

When the settings menu pack is enabled, the generated `settings.menu` includes a fullscreen toggle item:

```properties
item.fullscreen.label={fullscreen}
item.fullscreen.action=toggle_fullscreen
```

The `{fullscreen}` placeholder is resolved from the locale file, allowing for localized labels.

### Theme Font Weight

Menu theme creation now includes the `hintsFontWeight` property:

```properties
hintsFontWeight=NORMAL
```

This controls the font weight for hint text in menu screens (e.g., "Enter: Confirm" navigation hints).

### Resolution-Aware Dialogue Layout

When creating the dialogue layout, the wizard scales pixel-based values proportionally if the target resolution differs from the 1080p baseline:

| Resolution | Scaling Factor |
|------------|----------------|
| 720p (1280×720) | 0.67× |
| 1080p (1920×1080) | 1.0× (baseline) |
| 1440p (2560×1440) | 1.33× |
| 4K (3840×2160) | 2.0× |

Scaled values include:
- Textbox padding
- Name box dimensions and offsets
- Font sizes (name, dialogue, choice)
- Choice button dimensions and gaps

The generated `dialogue.layout` includes a comment header:
```properties
# Target resolution: 1920x1080
```

### Active Locale Entries

The generated locale stub (`config/locales/<locale>.properties`) now includes active entries based on enabled features:

```properties
# Menu labels (if menu pack enabled)
menu.new_game=New Game
menu.continue=Continue
menu.settings=Settings
menu.quit=Quit

# Settings entries (if settings pack enabled)
settings.fullscreen=Fullscreen
settings.text_speed=Text Speed
settings.auto_delay=Auto Delay

# Dialogue text keys (if sample content enabled)
intro.welcome=Welcome to your new visual novel project!
intro.instructions=Edit scripts/story/prologue.vns to begin your story.
```

Entries are uncommented only for the features you enable, keeping the locale file clean.

## Generated Directory Structure

```text
<project>/
|-- config/
|   |-- settings/vn.settings
|   |-- story/story.storymap
|   |-- ui/dialogue.layout
|   `-- menu/
|       |-- theme/menu.theme
|       |-- registry/menu.registry
|       |-- menus/
|       |   |-- main.menu
|       |   |-- load.menu      (optional)
|       |   |-- save.menu      (optional)
|       |   `-- settings.menu  (optional)
|       |-- layouts/default.layout
|       |-- styles/default.style
|       `-- assets/{buttons,icons}
|-- scripts/
|   |-- story/prologue.vns
|   |-- story/{tutorial_hub,branch_demo,epilogue}.vns
|   |-- scenes/studio_tour.jes
|   |-- tutorial/*.vns       (tutorial workspace)
|   |-- definitions/characters.vns
|   |-- common/
|   `-- system/
|-- assets/
|   `-- demo/
|       |-- backgrounds/{game,menu}.png
|       |-- characters/lavender/*
|       `-- audio/*
|-- save/
|-- .gitignore      (if Git init is enabled)
|-- .gitattributes  (if Git is enabled)
|-- README.md
`-- jvn.project
```

## Manifest (`jvn.project`)

Wizard writes key runtime/editor metadata such as:
- project identity (`name`, `author`, `type`)
- entry points (`entryVns`, `entryLabel`, `timeline`)
- config references (`settingsFile`, `dialogueLayout`, menu paths)
- resolution (`width`, `height`)
- selected module booleans
- VCS toggles (`vcs.git.*`)

This manifest is used by editor project-run workflow.

## Generated Starter Content

### Prologue script

The Starter Story opens with **The First Page**, a short scene with Lavender in a sketchbook studio.
It is designed to be played once and then edited immediately. It demonstrates:

- a shared character definition file with layered `@charpreset` expressions;
- named `@position` staging, dialogue modes, pacing, and transitions;
- choices, labels, variables, and local jumps;
- cross-file `[goto Scenario:label]` routing;
- a real `[jes push ... label ... with ...]` round trip.

Path:
- `scripts/story/prologue.vns`

### Bundled demo assets

Wizard packages and copies demo-ready starter assets so installed-editor builds and source checkouts
produce the same playable result:

- layered character sprites: `assets/demo/characters/lavender/*`
- procedurally generated plain-white backgrounds: `assets/demo/backgrounds/game.png` and `menu.png`
- sample BGM plus its license document under `assets/demo/audio/`

Default main-menu style points to the bundled background asset so new projects do not boot into an empty-color title screen.

### Tutorial workspace

The Tutorial Workspace adds 30 VNS lessons and authoring workshops under `scripts/tutorial/`. Each generated lesson
is rendered from the same templates tested in the editor build, then parsed by the runtime VNS parser.
The final lesson uses the current VNS/JES bridge rather than treating inline Java as the primary
extension path. The bundled `studio_tour.jes` fixture is also loaded through `JesLoader` in tests.

Tutorials are working scripts, but they are also source examples: keep the editor and running game
side by side, change one command, and rerun. Start at `scripts/story/tutorial_hub.vns`, or run an
individual lesson directly while focusing on one subsystem.

### Timeline starter

Creates initial arc:

```text
arc "Prologue" script "scripts/story/prologue.vns" entry "start" at 40,40
```

### Dialogue layout starter

Creates baseline `config/ui/dialogue.layout` values compatible with Layout Studio diagnostics and the runtime loader.

The default **Monochrome Sketch** theme ships a dedicated minimal variant. Its compact white
choice rows are aligned to the right of the viewport, leaving the left staging area clear for
Lavender. Thin black borders and restrained hover and selection colors replace the generic blue
full-width presentation. Selecting another theme retains the general-purpose dialogue preset.

The same theme also writes a coordinated minimal menu layout and style: the menu list occupies a
compact right-side column, typography uses neutral black and gray states, and button chrome is
removed. These presets remain available from the dialogue, menu-layout, and menu-style visual
editors and can be applied or undone while authoring.

### Menu profile starter

When enabled, creates:
- registry
- default layout/style
- main menu
- optional load/save/settings menu files

## Recommended First Steps After Creation

1. Open project in editor.
2. Edit `scripts/story/prologue.vns`.
3. Tune `config/ui/dialogue.layout` in visual dialogue editor.
4. Customize `config/menu/menus/*.menu` and `config/menu/layouts/*.layout`.
5. Open `scripts/scenes/studio_tour.jes`, change its label or timeline duration, and launch it from the prologue.
6. Add or replace assets under `assets/` (you can keep or remove `assets/demo`).
7. Run from the project explorer root **Run** button.

## Team Usage Notes

- Wizard output is intentionally plain text and source-control friendly.
- Keep `jvn.project` committed; it defines entry/config linkage.
- Use module toggles consistently across team templates to avoid layout drift.
- If Git is enabled, the wizard writes managed blocks into `.gitignore` and `.gitattributes`.
- Re-running Git setup from editor is safe; managed blocks are replaced in place.

## Lavender’s complete workshop

The Tutorial Workspace uses a six-page hub with 30 lessons covering VNS, presentation, animation, audio, state and branching, localization, menus, saves and rollback, reactive UI and facets, phone storytelling, particles, stage lighting, JES entities and gameplay systems, editor tools, assets and performance, accessibility, Java extensions, and release preparation.

Lavender appears waist-up on a plain white stage, with a translucent black textbox and spinning 3D continue arrow. The main menu uses a handwritten title and animated button dock. The supplied Apalonbeats track plays by default. Pikupiku remains an optional editor tool and is not applied to the tutorial. Choose a still arrow in the hub or accessibility lesson. Lesson completion is counted once on return to the hub and follows ordinary save/rollback state. Each lesson remains runnable from its `start` label. The generated `scripts/tutorial/README.md` maps the whole course to reference guides and distinguishes live demonstrations from authoring exercises.

New projects include a working facet and phone seed file. Existing projects are not rewritten. To adopt the new course, create a Tutorial Workspace and deliberately copy the lessons/configuration you want into your project. See the [workshop guide](../../guides/lavender-workshop.md) and [Pikupiku documentation](../../plugins/image-effects.md).
