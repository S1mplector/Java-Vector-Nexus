# New Game Workspace

Choose **New Project** in the editor’s Welcome dashboard to open the workspace. You can create a project directly from the **Project** tab or adjust the other tabs first.

## Choose a template

| Template | First playable scene | Menus and assets |
| --- | --- | --- |
| **Writing Project** (default) | One line of dialogue in `scripts/story/prologue.vns`, ready to replace | Title, save, load and settings screens. Bring your own art and music. |
| **Tutorial** | A topic picker leading to 30 independent lessons | Ginger, lesson examples, source panels and a dedicated interface. [Tutorial guide](../../guides/jvn-tutorial.md) |
| **Blank Sandbox** | The same editable prologue | An empty menu registry for designing your own interface. |

The display name appears in the game. **Auto** derives a folder name from it. If the destination already exists, **Use available folder name** picks the next free numbered folder and keeps your game title.

## Configure the project

| Tab | What you can set |
| --- | --- |
| **Project** | Starting template, display name, author and destination. The output path shows the exact folder the editor will create. |
| **Content** | Optional artwork and music, tutorial content, menu profiles, history defaults and Git initialization. Enable the initial commit separately if wanted. |
| **Runtime** | Game canvas, menu theme, renderer, audio backend and locale. Choose a resolution preset, including handheld and portrait, or enter custom dimensions. |
| **Playback** | Dialogue delay in milliseconds per character, auto-advance delay, music, effects and voice volumes, skip behavior, physics settings and input profile path. |
| **Review** | Release version, save ID, project notes and a tree of generated files. |

**Game canvas** shows the dimensions that will be written to the manifest. For example, on a display with 2× scaling, a 1920×1080 preset produces a 960×540 canvas with **Adjust for this display’s pixel density** enabled. Clear the option for an exact 1920×1080 canvas.

The **Release version** supplies the starting value in Build & Publish. The **Save ID** identifies the game’s saves and preferences. Leave it empty to use the suggested ID derived from the author and game title. Set a fixed ID if those names may change between releases.

## Open the generated files

| File | Use |
| --- | --- |
| `jvn.project` | Project identity, release version, dimensions, entry script and runtime configuration. |
| `scripts/story/prologue.vns` | Opening scene for Writing Project and Blank Sandbox. Edit the line after `@label start`, then use **Run** in the editor. |
| `scripts/definitions/characters.vns` | Shared character declarations for the opening script. |
| `scripts/story/tutorial_hub.vns` | Entry point when the tutorial pack is selected. Lessons live in `scripts/tutorial/`. |
| `config/story/story.storymap` | The opening arc, or the lesson routes in Tutorial. |
| `config/settings/vn.settings` | Generated project settings. |
| `config/ui/dialogue.layout` | Dialogue and choice layout, scaled to the selected canvas. |
| `config/locales/<locale>.properties` | Strings for the selected locale. |
| `config/menu/` | Registry, screens, layouts and styles for the menu options you selected. |

To build your own menus in Blank Sandbox, create a screen in the Layout Launcher and register it in `config/menu/registry/menu.registry`. For a writing project, open the existing `config/menu/menus/main.menu` to change the title screen or its actions.
