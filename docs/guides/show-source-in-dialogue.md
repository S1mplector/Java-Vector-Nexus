# Show a source excerpt in dialogue

Use a Facet `code` node when a lesson needs to show the exact VNS, JES or configuration lines the reader can edit. The JavaFX runtime draws the filename, original line numbers and syntax highlighting. The source file supplies the text, so correcting the example updates the display too.

## Mark the lines to show

Add this region to a scene, for example `scripts/story/prologue.vns`. Put it inside a reachable label in the scene.

```vns
# region greeting
narrator: The station clock reads {b}eight{/b}.
# endregion
```

## Create the panel

Save the following as `config/facets/greeting_code.facet`. The rectangle uses fractions of the viewport. This layout leaves room below the panel for dialogue.

```properties
id=greeting_code
x=0.36
y=0.05
width=0.60
height=0.55
nodes=sample
node.sample.type=code
node.sample.x=0
node.sample.y=0
node.sample.width=1
node.sample.height=1
node.sample.source=scripts/story/prologue.vns
node.sample.region=greeting
```

Put `[set ui.dialogueFacet greeting_code]` before the marked region and `[clear ui.dialogueFacet]` after it. Run the scene. The panel shows the literal bold tags while the dialogue presents the formatted word. Change `eight` to `nine`, save the file and reopen the scene to verify both displays.

## Choose the right excerpt

| Source | Region markers |
| --- | --- |
| `.vns` | `# region greeting` and `# endregion` |
| `.jes` | `// region greeting` and `// endregion` |
| `.properties`, `.facet`, `.layout`, `.menu`, `.style`, `.theme`, `.registry`, `.stagepreset` | `# region greeting` and `# endregion` |

Omit `region` to read the entire file. Keep excerpts short enough for your smallest viewport. If the code is missing, check the path, extension, exact marker spelling, closing marker and the 256 KiB source-file limit. Paths are project-relative. The panel disappears during choices. Use a called screen when the player needs to click a control in the panel.

[Facet reference](../scripting/ui/facets.md#showing-source-beside-dialogue) · [Playable tutorial](jvn-tutorial.md)
