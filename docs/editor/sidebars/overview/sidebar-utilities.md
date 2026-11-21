# Editor — Sidebar Utilities

Reference for the maintained sidebar panels available in the JVN editor. Each listed panel has a dedicated sub-document with details, examples, and usage guidance.

Panels can be added to either the left or right sidebar via the **+** tab. Each panel is independently closable and repositionable.

Source: `modules/editor/src/main/java/com/jvn/editor/EditorApp.java`

---

## Who This Is For

Use this page when you know the editor has a sidebar tool for something, but you are not sure which one to open first.

## Fast Picks

If you only need a quick answer:

- Need to browse files or run the project: **Project Explorer**
- Need to package a game or reopen release tooling: **Project Explorer** -> root **Build** button, then **Build & Publish...**
- Need live VNS problems: **VNS Diagnostics**
- Need to launch or reopen animation work: **Puppeteer Launcher**
- Need storyboard continuity while staging a shot: **Storyboard Overlay**
- Need layered character inspection: **Layered Image Visualizer**
- Need attribute-based character assembly: **Image Attributes Tool**
- Need lighting, grading, and stage presets: **Scene Lighting Studio**
- Need menu/layout editing entrypoints: **Layout Launcher**
- Need menu navigation guidance: [Menu Actions And Navigation](../../../scripting/ui/layout/structure/menu-actions.md)
- Need documentation lookup: [Documentation Index](../../../INDEX.md)

## Shared Utility Icon Chart

![Windows 7-style sidebar utility icon contact sheet](../../../assets/images/sidebars/sidebar-utility-icons/sidebar_utility_icon_contact_sheet.png)

The shared chart covers all 44 production roles supplied by `SidebarToolIcon`, including the
New Panel memory and dock states. Twenty-six high-frequency commands use bespoke Windows 7 shell
objects with glass navigation orbs, chrome hardware, beveled storage and document forms, and
semantic enamel. Every role keeps a transparent button surface and the same hover-glow behavior
as Help, Refresh, Add Here, Pop Out, and the Version Control commands.

Regenerate the chart with:

```bash
JVN_SIDEBAR_ICON_CONTACT_SHEET="$PWD/docs/assets/images/sidebars/sidebar-utility-icons/sidebar_utility_icon_contact_sheet.png" \
  ./gradlew :editor:test --tests com.jvn.editor.ui.SidebarToolIconTest
```

## Read This Next

- New to the editor overall: [Editor Guide](../../core/editor.md)
- New to JVN overall: [Choose Your Path in JVN](../../../guides/choose-your-path.md)
- Need file-level orientation first: [Common JVN File Types](../../../guides/common-file-types.md)

---

## Sub-Document Reference

| # | Panel | Sub-Document | Default Side | Purpose |
|---|-------|-------------|-------------|---------|
| 1 | Project Explorer | **[sidebar-project-explorer.md](../left/sidebar-project-explorer.md)** | Left | File tree, create/rename/delete files, run project |
| 2 | Story Map | **[sidebar-story-timeline.md](../left/sidebar-story-timeline.md)** | Left | Multi-arc story graph with arc/link management |
| 3 | Inspector | **[sidebar-inspector.md](../right/sidebar-inspector.md)** | Right | Entity property editing for JES scenes |
| 4 | Puppeteer Launcher | **[sidebar-puppeteer-launcher.md](../right/sidebar-puppeteer-launcher.md)** | Right | VNS scene snapshot, stage-context handoff, and Puppeteer launch |
| 5 | VNS Diagnostics | **[sidebar-vns-diagnostics.md](../right/sidebar-vns-diagnostics.md)** | Right | Live error/warning list for .vns scripts |
| 6 | Label Flow Map | **[sidebar-label-flow-map.md](../right/sidebar-label-flow-map.md)** | Right | Visual label-to-label flow graph for VNS |
| 7 | Asset Browser | **[sidebar-asset-browser.md](../right/sidebar-asset-browser.md)** | Right | Project asset discovery, preview, copy path |
| 8 | Layout Launcher | **[sidebar-layout-launcher.md](../right/sidebar-layout-launcher.md)** | Right | Quick-launch layout/style/screen editors |
| 9 | Storyboard Overlay | **[sidebar-storyboard-overlay.md](../right/sidebar-storyboard-overlay.md)** | Right | Transparent board-frame overlays for JES and VNS staging previews |
| 10 | Layered Image Visualizer | **[sidebar-layered-image-visualizer.md](../right/sidebar-layered-image-visualizer.md)** | Right | Layered sprite exploration and snippet export |
| 11 | Image Attributes Tool | **[sidebar-image-attributes-tool.md](../right/sidebar-image-attributes-tool.md)** | Right | Attribute-based character image assembly |
| 12 | Scene Lighting Studio | **[sidebar-image-tint-tool.md](../right/sidebar-image-tint-tool.md)** | Right | Scene lighting, grading, occlusion, and `.stagepreset` export for VNS/Puppeteer |
| 13 | Version Control | **[sidebar-version-control.md](../right/sidebar-version-control.md)** | Right | Git operations: commit, push, pull, branch, stash |
| 14 | Trashman | [Editor memory and cleanup](#trashman) | Hidden | Live heap history, preview-cache release, GC measurements, and JVM diagnostics |

### Trashman

Open **Window → Open Tool Window → Trashman** or add it from either sidebar’s **+** tab. The readings belong to the editor process. A separately running game has its own JVM.

- **Monitor** samples every two seconds. The solid line shows heap in use and the dashed line shows memory committed by the JVM. The chart grows to a two-minute window. **Sampled peak** covers the time this panel has been open. Turn off **Live** to pause sampling and use the refresh icon for a single reading.
- **Pin baseline** before opening a script or running a preview. The comparison tracks heap, non-heap and buffer changes from that sample. **Replace baseline** starts a new comparison.
- **Cleanup** can release the reloadable caches in open VNS and JES previews. **Run GC** requests one collection. **Run sweep** repeats the request using the selected pass count and delay. **Stop sweep** skips the remaining passes. Legacy finalization is opt-in.
- **Cleanup history** keeps the last twelve measurements while the panel is open. A negative delta means heap use fell between the two readings. The JVM schedules collection, and editor allocations also affect the result.
- **Inspect** lists collectors, memory pools, buffers, classes and threads. **Class histogram** uses the JDK’s `jcmd` to count all objects, including unreachable ones. The ranking shows shallow bytes occupied by each class’s instances. Large heaps can pause the editor during the count. **Copy report** includes the current counters, baseline, history and latest histogram.

### Adding Panels

1. Click the **+** tab on the left or right sidebar
2. A **New Panel** chooser tab opens listing all available panels
3. Use the row actions:
   `+` to add or move the panel into that sidebar
   pop-out to open it in a separate window
   minus to remove it from the sidebars
4. Close regular sidebar tabs with the tab **×** button (`Project` stays pinned)

Most panels are also reachable from the menu bar:

- **View -> Panels**
- **Navigate**
- **Tools**
- **Window -> Open Tool Window**

## Shared Interaction Baseline

The sidebar-tool audit uses the following baseline across all maintained panels:

- A persistent title and contextual help entry point
- A useful no-project, no-selection, empty-result, or error state
- Destructive or committing actions disabled until their prerequisites exist
- Browsing separated from explicit insertion or mutation
- Visible feedback for scans, copies, opens, saves, and failures
- Keyboard access for primary list and form workflows
- Accessible names for icon-only controls and ambiguous inputs
- Undo-safe edits that skip unchanged values

The specialized image tools already satisfy most of this baseline through shared fullscreen, persistence, tooltips, status, and export patterns. The Project Explorer, Asset Browser, Inspector, Label Flow Map, and Layered Image Visualizer have additionally been aligned around keyboard navigation, explicit actions, field validation, graph navigation/reachability, and contextual onboarding.

---

## Fullscreen Mode (Image Tools)

The three image tools (Layered Image Visualizer, Image Attributes Tool, Scene Lighting Studio) share a **fullscreen toggle** that expands the panel to fill the entire editor window. Only one image tool can be fullscreen at a time. All three implement the `ImageToolPanel` interface:

```java
public interface ImageToolPanel {
    void setProjectRoot(File projectRoot);
    void refreshCatalog();
    void setOnToggleFullscreen(Runnable handler);
    void setFullscreenActive(boolean active);
}
```

---

## State Persistence

Several sidebar utilities persist their state in the project's `.jvn/` directory:

| Utility | State File |
|---------|-----------|
| Layered Image Visualizer | `.jvn/layered-image-visualizer.properties` |
| Image Attributes Tool | `.jvn/image-attributes-tool.properties` |
| Scene Lighting Studio | `.jvn/image-tint-tool.properties` |
| Storyboard Overlay | `.jvn/storyboard-overlay.properties` |
| Story Map | `.jvn/story-map.txt` |
| Version Control | Git state (`.git/`) |

---

## Related Docs

- [Editor Guide](../../core/editor.md) — main editor layout, editing modes, keyboard shortcuts
- [Documentation Index](../../../INDEX.md) — complete documentation map
- [Puppeteer Editor Guide](../../puppeteer/puppeteer-editor-guide.md) — comprehensive Puppeteer usage
- [Puppeteer JES DSL Reference](../../puppeteer/puppeteer-jes-dsl.md) — exported timeline code syntax
