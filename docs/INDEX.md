# JVN Documentation Index

This index is the main route through the Java Vector Nexus documentation. It is organized for task-based reading first, then as a detailed map of the docs tree.

Hosted documentation is available at [javavectornexus.com](https://javavectornexus.com). The hosted
site mirrors these Markdown files into a searchable guide tree while this directory remains the
source of truth for docs changes.

## Start Here

| Need | Best Entry |
|------|------------|
| First build and editor launch | [Getting Started Guide](guides/getting-started.md) |
| Why there are no prebuilt downloads yet | [Getting Started Guide - Why There Is No Prebuilt Download Yet](guides/getting-started.md#why-there-is-no-prebuilt-download-yet) |
| I want the desktop hub or OS shortcuts | [JVN Engine Hub](editor/core/engine-hub.md) |
| Which system should I learn first? | [Choose Your Path in JVN](guides/choose-your-path.md) |
| What does each file do? | [Common JVN File Types](guides/common-file-types.md) |
| I am working in the editor | [JVN Editor Docs](editor/README.md) |
| I am writing story scripts | [VNS Scripting](scripting/vns/overview/vns-scripting.md) |
| I am writing gameplay/scene scripts | [JES Scripting](scripting/jes/overview/jes-scripting.md) |
| I am animating a shot | [Puppeteer Editor Guide](editor/puppeteer/puppeteer-editor-guide.md) |
| I am lighting/staging a scene | [Scene Lighting Studio](editor/sidebars/right/sidebar-image-tint-tool.md) |
| I am building menus/UI | [UI By Example](guides/ui-by-example.md) |
| I want reusable UI and animation patterns | [Reusable Authoring With Facets And Motifs](guides/facets-and-motifs.md) |
| I am packaging a game | [Build And Release Docs](project-setup/release/README.md) |
| I need the normative VNS/JES rules | [Scripting Language Contract](scripting/spec/README.md) |
| I want to extend JVN | [Plugin authoring and reference](plugins/README.md) |
| I want to add animation curves | [Animation Easing Extensions](plugins/animation-extensions.md) |

## Browse By Section

- [Guides](guides/README.md)
- [Editor](editor/README.md)
- [Scripting](scripting/README.md)
- [Runtime](runtime/README.md)
- [Project Setup And Delivery](project-setup/README.md)
- [Architecture](architecture/README.md)
- [Plugins](plugins/README.md)
- [Animation Easing Extensions](plugins/animation-extensions.md)

## Common Workflows

### Make A Visual Novel

1. [Getting Started Guide](guides/getting-started.md)
2. [VNS By Example](guides/vns-by-example.md)
3. [VNS Directives & Declarations](scripting/vns/language/vns-directives.md)
4. [VNS Commands Reference](scripting/vns/language/vns-commands.md)
5. [VNS Best Practices & Common Pitfalls](scripting/vns/guides/vns-best-practices.md)

### Animate A VN Shot

1. [Puppeteer Launcher](editor/sidebars/right/sidebar-puppeteer-launcher.md)
2. [Puppeteer Editor Guide](editor/puppeteer/puppeteer-editor-guide.md)
3. [Puppeteer JES Timeline DSL Reference](editor/puppeteer/puppeteer-jes-dsl.md)
4. [Puppeteer Motifs](editor/puppeteer/puppeteer-motifs.md)
5. [Puppeteer Animation Timelines](scripting/timeline/animation/timeline-animation.md)
6. [Hand-Coding Puppeteer Timelines](scripting/timeline/animation/timeline-hand-coding.md)

### Carry Scene Lighting Into Animation

1. [Scene Lighting Studio](editor/sidebars/right/sidebar-image-tint-tool.md)
2. [VNS Directives & Declarations — @stagepreset](scripting/vns/language/vns-directives.md)
3. [VNS Commands Reference — Stage Lighting](scripting/vns/language/vns-commands.md)
4. [Puppeteer Launcher](editor/sidebars/right/sidebar-puppeteer-launcher.md)
5. [Puppeteer Editor Guide](editor/puppeteer/puppeteer-editor-guide.md)

### Build Menus And UI

1. [UI By Example](guides/ui-by-example.md)
2. [Menu Profiles](scripting/ui/menus/menu-profiles.md)
3. [Reactive Overlay Screens](scripting/ui/menus/reactive-screens.md)
4. [JVN Facets](scripting/ui/facets.md)
5. [Reusable Authoring With Facets And Motifs](guides/facets-and-motifs.md)
6. [Text-First Layout Workflow](scripting/ui/layout/workflow/text-first-layout-workflow.md)
7. [Layout DSL Cookbook](scripting/ui/layout/reference/layout-dsl-cookbook.md)
8. [Production UI Review Checklist](scripting/ui/layout/reference/production-review-checklist.md)

### Create Gameplay Or Interactive Scenes

1. [JES Scripting](scripting/jes/overview/jes-scripting.md)
2. [JES Scenes & Entities](scripting/jes/scene/jes-scenes-entities.md)
3. [JES Component Reference](scripting/jes/scene/components.md)
4. [JES Timeline & Actions](scripting/jes/timeline/jes-timeline.md)
5. [JES VN Bridge & Java Hooks](scripting/jes/integration/jes-bridge.md)

### Package And Ship

1. [Build And Release Docs](project-setup/release/README.md)
2. [Build System](project-setup/release/build-system.md)
3. [Deployment & Packaging](project-setup/release/deployment.md)
4. [Asset Management & Path Resolution](runtime/systems/asset-management.md)
5. [Save System](runtime/systems/save-system.md)

## Editor

### Editor Hubs And Core Windows

- [JVN Engine Hub](editor/core/engine-hub.md) - desktop control panel, no-terminal shortcuts, installer paths, and logs
- [JVN Editor Docs](editor/README.md) - editor-focused hub and common routes
- [Editor](editor/core/editor.md) - main editor layout, editing modes, run/build flow, and shortcuts
- [Editor And Launcher Settings](editor/core/settings.md) - preferences, launcher handoff, run defaults, and sidebar defaults
- [Welcome Center](editor/core/welcome-center.md) - startup dashboard, recent projects, environment health
- [Run Console](editor/core/run-console.md) - Gradle/runtime output, counters, filtering, diagnostics
- [Scene Graph View](editor/core/scene-graph.md) - entity tree, selection, rename/delete, fit-to-selection
- [Tilemap Editor](editor/core/tilemap-editor.md) - tile painting, layers, and tileset preview
- [Puppeteer Design Notes](editor/core/action-editor-design.md) - current animation editor architecture and data flow
- [VS Code Extension](editor/core/vscode-extension.md) - external-editor syntax highlighting and snippets for VNS, JES, Story Map, and config files

### Puppeteer

- [Puppeteer - Animation Timeline Editor](editor/puppeteer/puppeteer.md) - architecture and integration overview
- [Puppeteer Editor Guide](editor/puppeteer/puppeteer-editor-guide.md) - day-to-day usage, panels, timeline, keyframes, registration
- [Puppeteer JES Timeline DSL Reference](editor/puppeteer/puppeteer-jes-dsl.md) - exported syntax, JES parser compatibility, event cues, properties
- [Puppeteer Motifs](editor/puppeteer/puppeteer-motifs.md) - reusable parameterized animation fragments
- [Generated Puppeteer Screenshots](editor/puppeteer/generated-puppeteer-screenshots.md) - generated visual reference

### Sidebar Utilities

- [Sidebar Utilities Overview](editor/sidebars/overview/sidebar-utilities.md) - quick chooser for all sidebar panels
- [Project Explorer](editor/sidebars/left/sidebar-project-explorer.md) - file tree, run/build, context actions
- [Story Map](editor/sidebars/left/sidebar-story-timeline.md) - story graph and arc/link management
- [Inspector](editor/sidebars/right/sidebar-inspector.md) - JES entity property editing
- [Puppeteer Launcher](editor/sidebars/right/sidebar-puppeteer-launcher.md) - VNS scene snapshot and animation launch/reopen
- [VNS Diagnostics](editor/sidebars/right/sidebar-vns-diagnostics.md) - live script problems and click-to-jump
- [Label Flow Map](editor/sidebars/right/sidebar-label-flow-map.md) - visual VNS label graph
- [Asset Browser](editor/sidebars/right/sidebar-asset-browser.md) - asset discovery, preview, auto-label review, and VNS generation
- [Layout Launcher](editor/sidebars/right/sidebar-layout-launcher.md) - entrypoint for menu/layout/style editors
- [Storyboard Overlay](editor/sidebars/right/sidebar-storyboard-overlay.md) - reference overlays for staging previews
- [Layered Image Visualizer](editor/sidebars/right/sidebar-layered-image-visualizer.md) - layered sprite inspection and export
- [Image Attributes Tool](editor/sidebars/right/sidebar-image-attributes-tool.md) - attribute-driven image assembly
- [Scene Lighting Studio](editor/sidebars/right/sidebar-image-tint-tool.md) - lighting, grading, occlusion, setup, and stage-preset export
- [Version Control](editor/sidebars/right/sidebar-version-control.md) - Git status, commit, push, pull, branch, stash

### Editor File Formats And Generated References

- [.layersetup Files](editor/tools/layersetup-files.md)
- [Generated Welcome Center Screenshots](editor/core/generated-welcome-center-screenshots.md)
- [Generated Run Console Screenshots](editor/core/generated-run-console-screenshots.md)
- [Generated Project Explorer Screenshots](editor/sidebars/left/generated-project-explorer-screenshots.md)
- [Generated Story Map Screenshots](editor/sidebars/left/generated-story-timeline-screenshots.md)
- [Generated Asset Browser Screenshots](editor/sidebars/right/generated-asset-browser-screenshots.md)
- [Generated Image Attributes Screenshots](editor/sidebars/right/generated-image-attributes-screenshots.md)
- [Generated Scene Lighting Studio Screenshots](editor/sidebars/right/generated-image-tint-screenshots.md)
- [Generated Inspector Screenshots](editor/sidebars/right/generated-inspector-screenshots.md)
- [Generated Label Flow Map Screenshots](editor/sidebars/right/generated-label-flow-map-screenshots.md)
- [Generated Layered Image Visualizer Screenshots](editor/sidebars/right/generated-layered-image-visualizer-screenshots.md)
- [Generated Layout Launcher Screenshots](editor/sidebars/right/generated-layout-launcher-screenshots.md)
- [Generated Puppeteer Launcher Screenshots](editor/sidebars/right/generated-puppeteer-launcher-screenshots.md)
- [Generated Version Control Screenshots](editor/sidebars/right/generated-version-control-screenshots.md)
- [Generated VNS Diagnostics Screenshots](editor/sidebars/right/generated-vns-diagnostics-screenshots.md)

## Guides

- [Getting Started Guide](guides/getting-started.md)
- [Choose Your Path in JVN](guides/choose-your-path.md)
- [Common JVN File Types](guides/common-file-types.md)
- [JVN Cookbook & Recipes](guides/cookbook.md)
- [JES <-> VNS Integration Cookbook](guides/integration-cookbook.md)
- [JES By Example](guides/jes-by-example.md)
- [VNS By Example](guides/vns-by-example.md)
- [UI By Example](guides/ui-by-example.md)

### VNS By Example Chapters

- [01 - Hello World](guides/vns-by-example/01-hello-world.md)
- [02 - Characters and Backgrounds](guides/vns-by-example/02-characters-and-backgrounds.md)
- [03 - Choices and Branching](guides/vns-by-example/03-choices-and-branching.md)
- [04 - Variables and Conditions](guides/vns-by-example/04-variables-and-conditions.md)
- [05 - Audio and Transitions](guides/vns-by-example/05-audio-and-transitions.md)
- [06 - Screen Effects and Timing](guides/vns-by-example/06-effects-and-timing.md)
- [07 - Character Motion](guides/vns-by-example/07-character-motion.md)
- [08 - Script Structure](guides/vns-by-example/08-script-structure.md)
- [09 - Advanced Variables](guides/vns-by-example/09-advanced-variables.md)
- [10 - JES and Java Integration](guides/vns-by-example/10-jes-and-java-integration.md)
- [11 - Reactive UI with Facets](guides/vns-by-example/11-reactive-ui-and-facets.md)
- [12 - Phone Storytelling](guides/vns-by-example/12-phone-storytelling.md)

### JES By Example Chapters

- [01 - Hello World](guides/jes-by-example/01-hello-world.md)
- [02 - Shapes and Layout](guides/jes-by-example/02-shapes-and-layout.md)
- [03 - Sprites and Animation](guides/jes-by-example/03-sprites-and-animation.md)
- [04 - Input Bindings and Call Handlers](guides/jes-by-example/04-input-and-call-handlers.md)
- [05 - Parallel Animation and Camera](guides/jes-by-example/05-parallel-and-camera.md)
- [06 - Animated Characters](guides/jes-by-example/06-animated-characters.md)
- [07 - Tilemap World](guides/jes-by-example/07-tilemap-world.md)
- [08 - RPG Systems](guides/jes-by-example/08-rpg-systems.md)
- [09 - Physics Bodies](guides/jes-by-example/09-physics-bodies.md)
- [10 - VNS Bridge Integration](guides/jes-by-example/10-vns-bridge.md)
- [11 - Particle Effects](guides/jes-by-example/11-particle-effects.md)
- [12 - Interactive UI](guides/jes-by-example/12-interactive-ui.md)

### UI By Example Chapters

- [01 - Choose the Right UI System](guides/ui-by-example/01-choose-the-right-ui-system.md)
- [02 - Dialogue, Name Boxes, and Character Framing](guides/ui-by-example/02-dialogue-and-character-framing.md)
- [03 - Choices, Dialogue Modes, and Quick Controls](guides/ui-by-example/03-choices-modes-and-controls.md)
- [04 - Themes, Typography, and Assets](guides/ui-by-example/04-themes-typography-and-assets.md)
- [05 - A Complete Menu Profile](guides/ui-by-example/05-complete-menu-profile.md)
- [06 - Navigation, Scripts, and Custom Actions](guides/ui-by-example/06-navigation-and-actions.md)
- [07 - Layouts, Inheritance, and Bespoke Buttons](guides/ui-by-example/07-layouts-inheritance-and-buttons.md)
- [08 - Settings, Save/Load, and Help](guides/ui-by-example/08-settings-save-load-and-help.md)
- [09 - Reactive Overlay Screens](guides/ui-by-example/09-reactive-overlay-screens.md)
- [10 - Facet Fundamentals](guides/ui-by-example/10-facet-fundamentals.md)
- [11 - Advanced Facets and Reusable Presentation](guides/ui-by-example/11-advanced-facets.md)
- [12 - JES HUDs and Interactive Widgets](guides/ui-by-example/12-jes-huds-and-widgets.md)
- [13 - Phone, Gallery, and Music Room Surfaces](guides/ui-by-example/13-specialized-surfaces.md)
- [14 - Localization, Accessibility, and Responsive Layout](guides/ui-by-example/14-localization-accessibility-and-responsive-ui.md)
- [15 - Tooling, Diagnostics, and Shipping](guides/ui-by-example/15-tooling-validation-and-shipping.md)

## Scripting Language Contract

- [JVN Scripting Language Contract](scripting/spec/README.md) - normative VNS/JES entrypoint
- [Language Contract 1](scripting/spec/v1/README.md) - current contract and conformance status
- [Scripting Compatibility And Deprecation Policy](scripting/spec/compatibility-policy.md)
- [Scripting Diagnostic Contract](scripting/spec/diagnostics.md)
- [VNS 1 Specification](scripting/spec/v1/vns.md)
- [JES 1 Specification](scripting/spec/v1/jes.md)
- [Language Contract 1 Changelog](scripting/spec/v1/changelog.md)

## VNS

- [VNS Scripting](scripting/vns/overview/vns-scripting.md)
- [VNS Directives & Declarations](scripting/vns/language/vns-directives.md)
- [VNS Commands Reference](scripting/vns/language/vns-commands.md)
- [VNS Dialogue & Text](scripting/vns/language/vns-dialogue.md)
- [VNS Choices & Branching](scripting/vns/language/vns-choices.md)
- [VNS Variables & Conditions](scripting/vns/language/vns-variables.md)
- [VNS Text Formatting & ICU](scripting/vns/language/vns-text-formatting.md)
- [VNS Characters & Sprites](scripting/vns/presentation/vns-characters.md)
- [Layered Character Presets Guide](scripting/vns/presentation/vns-layered-charpresets.md)
- [Character Display Slots](scripting/vns/presentation/vns-display-slots.md)
- [Movable Character Layer Groups](scripting/vns/presentation/vns-movable-layer-groups.md)
- [VNS Audio Commands](scripting/vns/presentation/vns-audio.md)
- [VNS Transitions & Screen Effects](scripting/vns/presentation/vns-transitions.md)
- [VNS Subroutines & Flow Control](scripting/vns/flow/vns-flow-control.md)
- [VNS Interop & Integration](scripting/vns/integration/vns-interop.md)
- [Java + JES + VNS Cross Development](scripting/vns/integration/java-jes-cross-development.md)
- [VNS <-> JES Architecture & Coordination](scripting/vns/integration/vns-jes-architecture.md)
- [VNS Scene Lifecycle & State](scripting/vns/runtime/vns-scene-lifecycle.md)
- [VNS Save System](scripting/vns/runtime/vns-save-system.md)
- [VNS Rollback & History](scripting/vns/runtime/vns-rollback-history.md)
- [VNS Settings & Playback Modes](scripting/vns/runtime/vns-settings-modes.md)
- [VNS Localization](scripting/vns/runtime/vns-localization.md)
- [VNS Parsing Internals](scripting/vns/internals/vns-parsing.md)
- [VNS Tutorial: Building a Complete Visual Novel](scripting/vns/guides/vns-tutorial.md)
- [VNS Best Practices & Common Pitfalls](scripting/vns/guides/vns-best-practices.md)
- [VNS Debugging & Troubleshooting](scripting/vns/guides/vns-debugging.md)
- [VNS Project Organization & Scaling](scripting/vns/guides/vns-project-organization.md)

## JES

- [JES Scripting](scripting/jes/overview/jes-scripting.md)
- [JES Scenes & Entities](scripting/jes/scene/jes-scenes-entities.md)
- [JES Component Reference](scripting/jes/scene/components.md)
- [JES Timeline & Actions](scripting/jes/timeline/jes-timeline.md) - timeline actions, Puppeteer aliases, event cues, custom properties
- [JES Input Bindings](scripting/jes/systems/jes-input.md)
- [JES Camera System](scripting/jes/systems/jes-camera.md)
- [JES Physics & Collision](scripting/jes/systems/jes-physics.md)
- [JES Tilemaps & Maps](scripting/jes/systems/jes-tilemaps.md)
- [JES AI System](scripting/jes/gameplay/jes-ai.md)
- [JES RPG Stats, Combat & Inventory](scripting/jes/gameplay/jes-rpg.md)
- [JES UI Widgets](scripting/jes/gameplay/jes-ui-widgets.md)
- [JES VN Bridge & Java Hooks](scripting/jes/integration/jes-bridge.md)
- [JES Parsing Internals](scripting/jes/internals/jes-parsing.md)

## Timeline

- [Timeline Scripting](scripting/timeline/overview/timeline-scripting.md)
- [Story Map - Arcs & Links DSL](scripting/timeline/story/timeline-story-arcs.md)
- [Core Animation API](scripting/timeline/animation/core-animation-api.md) - TimelineRunner, Easing, SceneAccessor
- [Puppeteer Animation Timelines](scripting/timeline/animation/timeline-animation.md)
- [Hand-Coding Puppeteer Timelines](scripting/timeline/animation/timeline-hand-coding.md)
- [Puppeteer Motifs](editor/puppeteer/puppeteer-motifs.md)

## Menus And Layout

### Menus

- [Menu Profiles](scripting/ui/menus/menu-profiles.md)
- [Menu Screens](scripting/ui/menus/menu-screens.md)
- [Reactive Overlay Screens](scripting/ui/menus/reactive-screens.md)
- [JVN Facets](scripting/ui/facets.md)
- [Menu Styles](scripting/ui/menus/menu-styles.md)

### Layout Components

- [Dialogue Layout & Style](scripting/ui/layout/components/dialogue-layout.md)
- [Character Framing & Sprites](scripting/ui/layout/components/character-framing.md)
- [Choice Buttons](scripting/ui/layout/components/choice-buttons.md)
- [Textbox Action Buttons](scripting/ui/layout/components/textbox-action-buttons.md)

### Layout Structure And Styling

- [Text-First Layout Workflow](scripting/ui/layout/workflow/text-first-layout-workflow.md)
- [Menu Layouts](scripting/ui/layout/structure/menu-layouts.md)
- [Menu Button Layouts](scripting/ui/layout/structure/menu-button-layouts.md)
- [Menu Registry & File Discovery](scripting/ui/layout/structure/menu-registry.md)
- [Menu Actions & Navigation](scripting/ui/layout/structure/menu-actions.md)
- [Menu Inheritance & Composition](scripting/ui/layout/structure/menu-inheritance.md)
- [Colors & Theming](scripting/ui/layout/styling/colors-theming.md)
- [Fonts & Typography](scripting/ui/layout/styling/fonts-typography.md)
- [Assets & Backgrounds](scripting/ui/layout/styling/assets-backgrounds.md)

### Layout Screens, Tooling, And References

- [Save & Load Screen Configuration](scripting/ui/layout/screens/save-load-screens.md)
- [Settings Screen Configuration](scripting/ui/layout/screens/settings-screen.md)
- [Help Screen Configuration](scripting/ui/layout/screens/help-screen.md)
- [Layout DSL Cookbook](scripting/ui/layout/reference/layout-dsl-cookbook.md)
- [Scala DSL Reference](scripting/ui/layout/reference/scala-dsl.md)
- [Custom Layout Scenarios](scripting/ui/layout/tooling/custom-scenarios.md)
- [Layout Editor Tools](scripting/ui/layout/tooling/layout-editor-tools.md)
- [Validation & Diagnostics](scripting/ui/layout/tooling/validation-diagnostics.md)

## Runtime And Project Setup

### Runtime

- [Runtime Documentation](runtime/README.md)
- [Runtime](runtime/core/runtime.md)
- [Interop Guide](runtime/core/interop.md)
- [Asset Management & Path Resolution](runtime/systems/asset-management.md)
- [Audio System](runtime/systems/audio-system.md)
- [Display & Resolution Settings](runtime/systems/display-settings-guide.md)
- [Save System](runtime/systems/save-system.md)
- [VN Settings Reference](runtime/systems/vn-settings.md)

### Project Setup

- [Project Setup And Delivery](project-setup/README.md)
- [New Project Wizard](project-setup/onboarding/new-project-wizard.md)
- [Project Structure Conventions](project-setup/onboarding/project-structure.md)
- [Title Screen and Menu Presentation](project-setup/content/title-screen.md)
- [Text Effects](project-setup/content/text-effects.md)
- [Localization Workflow](project-setup/content/localization.md)
- [Version Control](project-setup/collaboration/version-control.md)
- [Build And Release Docs](project-setup/release/README.md)
- [Build System](project-setup/release/build-system.md)
- [Deployment & Packaging](project-setup/release/deployment.md)

## Architecture And Quality

- [Architecture Documentation](architecture/README.md)
- [Architecture Overview](architecture/core/overview.md)
- [System Architecture](architecture/core/system-architecture.md)
- [2D Engine](architecture/core/2d-engine.md)
- [Render-API: Graphics Abstraction](architecture/core/render-api.md)
- [Engine Lifecycle & Main Loop](architecture/core/engine-lifecycle.md)
- [Ren'Py UI Parity Roadmap](architecture/core/ui-parity-roadmap.md)
- [Performance and Build Footprint](architecture/quality/performance.md)
- [Debugging & Profiling](architecture/quality/debugging.md)

### Core Internal APIs

- [Core Physics API](architecture/internals/core-physics-api.md) - PhysicsWorld2D, RigidBody2D, raycasting

## Platform Runtimes

- [Platform Runtimes Overview](runtime/platforms/README.md)
- [Render-API](architecture/core/render-api.md) - graphics backend abstraction
- [Android Runtime](runtime/platforms/android-runtime.md) - non-deployable Android backend scaffold
- [iOS Runtime](runtime/platforms/ios-runtime.md) - non-deployable iOS backend scaffold
- [Web Runtime](runtime/platforms/web-runtime.md) - executable TeaVM/Canvas 2D bootstrap; not game-ready
- [Swing Runtime](runtime/platforms/swing-runtime.md) - secondary Java 21 Swing/AWT renderer

## Documentation Governance

- [Documentation Maintenance Guide](MAINTENANCE.md)
- [May 2026 Documentation Audit](plans/docs-audit-2026-05.md) - historical snapshot
