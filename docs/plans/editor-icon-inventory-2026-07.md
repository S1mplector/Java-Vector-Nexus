# Editor icon inventory

Date: 2026-07-18

This document maps the editor's icon systems and migration surface before a possible Aero-style redesign. Counts describe the current source tree and intentionally distinguish reusable icon concepts from the hundreds of places where they are instantiated.

## September 2026 visual review

The production icon atlas now covers **414 semantic roles across 12 families**. It includes
shared actions, sidebar utilities, Puppeteer, diagnostics, console controls, code dockers,
layer tools, panel actions, file types, and compact glyphs. Each role is rendered on dark
and light surfaces at native size and enlarged for review. Repeated call sites share the
same artwork; brand wordmarks, OS window controls, and arbitrary user-installed icon
packs are not part of the command-icon atlas.

Generate a fresh atlas from the actual JavaFX factories:

```bash
JVN_EDITOR_ICON_ATLAS="$PWD/build/reports/editor-icons/after" ./gradlew :editor:test \
  --tests '*EditorIconContactSheetTest'
```

Open `build/reports/editor-icons/after/index.html` for all contact sheets;
`inventory.csv` lists every exported family and role.

This polish pass replaces flat bundled explorer folders with manila folders, paper
inserts, and semantic emblems. Generic documents and media files use folded paper,
while language and ecosystem logos remain recognizable. Installed icon themes keep
priority over the bundled artwork. Main project/file actions now use the same shell
materials. Layered Image Visualizer commands have filled glass layers, paper cards,
stronger edges, and lighter shadows. Puppeteer commands use distinct clipboard,
folder, character, help, audio-note, recording, and validation metaphors where generic
boxed glyphs were ambiguous. Existing button sizes and hover/press behavior are retained.

The July inventory below is the historical migration baseline; its counts and visual
status predate this review.

## Inventory summary

| System | Source | Inventory | Current rendering | Aero migration fit |
| --- | --- | ---: | --- | --- |
| Shared action icons | `CssIcon.java` | 84 concepts, 454 call sites | JavaFX `Region` with an SVG path mask and flat color | High; replace factory output without changing most callers |
| Project explorer icons | `ProjectFileIcons.java` / `MaterialProjectIconPack.java` | 116 semantic kinds, 113 SVG files | Parsed SVG assets, clipped and scaled in JavaFX | Medium; preserve language identity, Aero-treat folders and generic files first |
| Sidebar tool icons | `EditorSidebarPanel.java` | 15 tools, 12 assigned PNG assets, CSS fallbacks | PNG artwork or flat `CssIcon` fallback | High; ideal first editor-specific Aero set |
| Status bar icons | `JvnStatusBar.java` | 14 segments | Recolored `CssIcon` instances | Low; glass detail will not survive the compact status-bar size |
| Brand/lifecycle artwork | `MetallicJvnLogo`, startup/shutdown overlays, `jvn_logo.png` | 1 shared wordmark plus legacy raster | JavaFX vector construction and PNG fallback | Keep; already has the dimensional material language |
| Text-symbol controls | scattered | 20+ distinct symbols | Unicode text such as `▶`, `×`, `▲`, `⚙`, `?` | High; inconsistent across fonts and operating systems |
| Tool-specific drawings | performance graphs, curves, preview canvases | not command icons | JavaFX Canvas and shapes | Out of scope; these are data visualization, not icon chrome |

## Shared action vocabulary

`CssIcon` is the main compatibility boundary. Its 84 public concepts are:

- Navigation and direction: `arrowUp`, `arrowDown`, `arrowLeft`, `arrowRight`, `fastForward`, `fastRewind`, `skipPrevious`, `nearMe`, `myLocation`, `explore`.
- Creation and removal: `plus`, `plusBold`, `minus`, `clearX`, `delete`, `libraryAdd`, `controlPointDuplicate`.
- Editing and clipboard: `edit`, `freehand`, `contentPaste`, `copy`, `save`, `undo`, `redo`, `sort`, `wrapText`, `formatAlignJustify`.
- Runtime and transport: `play`, `stop`, `pause`, `loop`, `runtimePlay`, `runtimeStop`, `runtimeClear`, `runtimeCopy`, `fiberSmartRecord`.
- Files and workspace: `folder`, `folderZip`, `document`, `download`, `home`, `dock`, `popOut`, `openInFull`, `closeFullscreen`.
- Selection and layout: `grid`, `grid4x4`, `borderAll`, `rectSelect`, `polygon`, `joinInner`, `swapHoriz`, `verticalAlignTop`, `verticalAlignBottom`, `zoomOutMap`.
- State and feedback: `check`, `error`, `warning`, `visibility`, `visibilityOff`, `sparkles`, `auto`.
- Domain and object: `speech`, `list`, `search`, `palette`, `link`, `memory`, `person`, `emojiPeople`, `landscape`, `timeline`, `movie`, `label`, `lightbulb`, `theater`, `robot`, `rocket`, `branchPlus`, `input`, `threeSixty`, `settings`.

Highest-density consumers are `ImageTintToolView` (74 calls), `PuppeteerWindow` (67), `LayeredImageVisualizerView` (48), `EditorApp` (30), `ImageAttributesToolView` (25), `StoryboardOverlayView` (22), `ScriptEditorLauncherView` (20), `TrashmanView` (17), and `WelcomeCenterView` (16).

## Editor surfaces

| Surface | Current icon roles | Recommended Aero treatment |
| --- | --- | --- |
| Main editor chrome | sidebar tabs, add-tab, chooser actions, settings | Full-color 22-28 px glass icons; keep compact tab affordances simple |
| Welcome Center | create/open/recent/docs/spotlight actions | Full Aero, 20-32 px; strong candidate after sidebar |
| Run console | run, stop, clear, copy, performance status | Aero for primary commands; retain simple telemetry marks |
| Build & Ship | selected-state check and mostly text buttons | Add a restrained shipping/toolbox family after common controls migrate |
| Project and path explorers | semantic file/folder icons, up, refresh | Keep language/file logos; Aero folders, archive, executable, generic document |
| Script editor | file actions, search navigation, dirty dot | Migrate commands; replace Unicode search arrows and close mark |
| Version control | branch, refresh, sync, warnings, close | Aero shell/network family; preserve semantic green/amber/red states |
| Story Map and Storyboard | timeline, movie, navigation, visibility | Aero media/timeline family |
| Puppeteer and keyframes | transport, recording, transforms, alignment, clipboard | Largest migration group; establish animation-tool palette before converting |
| Image tools | visibility, layers, tint, lighting, export, layout | Aero imaging family; avoid using gloss on tiny per-row toggles |
| Trashman | GC, memory, heap, reports, thresholds | Aero diagnostics/maintenance family |
| Status bar | home, branch, check, speech, folder, document, edit, label, dock, save, warning, memory, Java, palette | Keep monochrome at 14 px; use Aero only if a larger status presentation is introduced |

## Sidebar tool map

| Tool | Current primary asset | CSS fallback |
| --- | --- | --- |
| Project | `project_inspector_orange_transparent.png` | folder |
| Trashman | none | delete |
| Story Map | `timeline_editor_orange_transparent.png` | timeline |
| Inspector | `project_inspector_orange_transparent.png` | search |
| Diagnostics | `vns_diagnostics_orange_transparent.png` | warning |
| Label Flow | `label_flow_inspector_orange_transparent.png` | link |
| Assets | none | folder |
| Layout Launcher | `layout_editor_manager_orange_transparent.png` | rectangular selection |
| Storyboard Overlay | `storyboard_overlay_tool_orange_transparent.png` | movie |
| Layered Images | `layered_image_visualizer_orange_transparent.png` | copy |
| Image Attributes | none | edit |
| Scene Lighting Studio | `scene_lighting_studio_tool_orange_transparent.png` | lightbulb |
| Version Control | `version_control_orange_transparent_v2.png` | timeline/branch-like mark |
| Puppeteer Launcher | `puppetteer_orange_transparent.png` | theater masks |
| Script Editor | `code_editor_orange_transparent.png` | edit |

The resource directory also contains `documentation.png` and `settings_orange_transparent.png`, used outside the 15-panel enum.

## Explorer semantic map

The explorer exposes 116 `ProjectFileIcons.Kind` values. They divide into:

- 58 folder/root roles: root, generic, source, config, export, scripts, story, assets, audio, layout, style, docs, build, save, template, test, Java, UI, video, fonts, resources, public, tools, archive, backup, CI, components, content, core, coverage, debug, Docker, downloads, examples, features, functions, Git, GitHub, i18n, input, interface, JSON, libraries, logs, messages, mocks, Node, packages, private, repository, routes, Sass, shaders, shared, temp, tasks, TypeScript, uploads, and views.
- 58 file roles: script, Java, Kotlin, Python, Markdown, JSON/schema, XML, CSS/style, Sass, Less, JavaScript, TypeScript, React, Vue, image, SVG, audio, video, YAML, HTML, Gradle, TOML, settings, archive, database, font, console, PowerShell, Docker, Git, GitHub Actions, GitLab, Taskfile, log, PDF, office, executable, DLL, Node, NPM, license, changelog, authors, credits, Mermaid, Draw.io, ESLint, Prettier, EditorConfig, story, menu, layout, timeline, document, and note.

The 113 SVG resources under `icons/material` include both this active semantic set and a few aliases/specialized folder variants. `MaterialProjectIconPack` is the single loader, so the explorer can migrate incrementally without changing its classification rules.

## Non-factory symbols to retire

- `EditorSearchBar`: `◀`, `▶`, `✕`.
- Timeline and anchor editors: text `+`, `-`, `−`, and `×` controls.
- Animation preview controls: `▶ Preview` and `■ Stop`.
- Layered image controls: `▶▶`, `▼▼`, `▶`, `▼`, `↑`, `↓`, `×`, and a text drag handle.
- Version control: `▲`, `×`, `⚠`, and `⚙` embedded in labels.
- Maintenance/help/status: `⚙`, `?`, `●`, bullets, and dirty-file dots.
- Puppeteer: text back arrows, restore arrows, close marks, and drag handles.

## Migration plan

1. Extract the hub's glass renderer into a shared JavaFX-capable icon API with semantic `IconKind`, size tiers, selected/disabled variants, and no Swing dependency.
2. Convert the 15 sidebar tools and Welcome Center first. They have enough area for the Aero material to read and establish the editor palette.
3. Convert global commands: run/stop, save, undo/redo, search, settings, build, version control, and window actions.
4. Convert tool families one domain at a time: animation, image/layers, layout, diagnostics, then shipping.
5. Replace all Unicode controls with the shared API.
6. Keep status-bar and dense row icons in a simplified companion style; Aero should degrade intentionally below 18 px.
7. Migrate explorer folders and generic file types, while retaining recognizable language and ecosystem logos.

## Guardrails

- Use three supported size tiers: 14 px compact, 20-24 px command, and 28-32 px feature/tool.
- Every icon needs normal, hover, pressed, selected, and disabled behavior where applicable.
- Color must reinforce meaning, not be the only state signal.
- Preserve stable control dimensions when icon detail changes.
- Test representative icons through rendered pixel assertions and screenshot the main editor at normal and compact UI scales.
- Keep tooltips and accessible text on icon-only controls.
