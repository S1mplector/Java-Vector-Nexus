# Migrating from Older Visual Layout Editors

Existing JVN projects already use the properties formats read by Layout Studio. Open the existing `.menu`, `.layout`, `.style`, or `dialogue.layout` file in **Layout Editors**. The current Studio renders a preview with the JavaFX game renderer and writes your design edits into the open file.

| Operation | Where to do it |
|---|---|
| Position a menu item | Drag it on the menu canvas |
| Create a menu or add actions | **New Menu** in the sidebar, then **Add** and the inspector |
| Position dialogue and speaker boxes | Drag the boxes in the Dialogue preview |
| Position choices | Switch the preview to **Choices** |
| Adjust automatic menu rows | Edit list geometry in the shared layout inspector |
| Change fonts, colors, or images | Open the shared style or edit an item's overrides |
| Inspect stored values and resolve warnings | Switch to **Source** |
| Validate navigation and actual game data | **Save and Run Runtime** |

## Review a migrated layout

Open the existing files first. Templates replace the whole working document, so reserve them for a new baseline or copy individual declarations from a template. Use source history to recover known-good values if the old editor saved duplicates or deprecated keys.

For a menu item, drag or resize it to write the full `boundsX`, `boundsY`, `boundsWidth`, and `boundsHeight` group. Fractional values scale with the runtime list area. A partial group falls back to automatic layout. Use **Reset to layout** to remove local overrides.

Validate the inherited style and assets in **Design**, then run at the resolutions you support. Check the initial screen, each navigation target, and the input methods your game uses.

- [Layout authoring tools](../tooling/layout-editor-tools.md)
- [Validation and diagnostics](../tooling/validation-diagnostics.md)
