# Text-First Layout Workflow

> Official guide for editing JVN layouts in code, validating in runtime.

---

## Purpose

JVN stores layouts, styles, menus, and registries as plain-text properties. Layout Studio lets
you edit the same working document in **Design** and **Source**. This guide covers the source
workflow, including where each file lives, what keys are available, and how to debug changes.
For direct manipulation on the game canvas, see [Layout authoring tools](../tooling/layout-editor-tools.md).

For a worked multi-screen project, continue with the
[complete menu tutorial](complete-menu-tutorial.md).

---

## Fast Start

**Five minutes to your first layout change:**

1. Open your project in the JVN editor.
2. Navigate to `config/ui/dialogue.layout` in the file tree (or create it).
3. Add one line:

```properties
textBoxY=0.70
```

4. Run your project (`Ctrl+R`, Play button, or Layout Studio `Ctrl/Cmd+Enter`).
5. Observe: the dialogue text box now sits higher on screen.
6. Go back to the file, tweak the value, re-run. Done.

That's the entire workflow. Every layout change follows this pattern:
**edit text → run project → observe → adjust → re-run**.

---

## File Map

| File | Purpose | Location |
|---|---|---|
| `dialogue.layout` | Dialogue box geometry + visual style | `config/ui/dialogue.layout` |
| `*.layout` | Menu list positioning | `config/menu/layouts/<id>.layout` |
| `*.style` | Menu visual appearance | `config/menu/styles/<id>.style` |
| `*.menu` | Menu screen definition (items, actions) | `config/menu/menus/<id>.menu` |
| `menu.registry` | Wiring: which menus, layouts, styles exist | `config/menu/registry/menu.registry` |

All files use **Java `.properties` format**: `key=value`, one per line, `#` for comments.

---

## Step-by-Step Workflow

### Step 1: Identify What to Change

Decide which layer you're editing:

- **Dialogue box position/size** → `dialogue.layout`
- **Menu item list spacing** → `*.layout`
- **Colors, fonts, button skins** → `*.style`
- **Which items appear, their actions** → `*.menu`
- **Which files the engine discovers** → `menu.registry`

### Step 2: Open the File in Code Mode

Open the relevant file in Layout Studio, the editor's normal source tab, or any text editor.
The source file is the only authoring model and the runtime consumes it directly.

### Step 3: Edit Properties

Add or modify `key=value` lines. Use this document or the DSL Cookbook as your key reference.
Only set the keys you want to override; unset keys keep engine defaults.

### Step 4: Save and Run

Save the file, then run your project. The engine reloads all layout files from disk on
startup. There is no separate "build" step for layout files.

### Step 5: Observe in Runtime

Look at the running game window. Check:

- Are elements positioned where you expect?
- Are colors and fonts correct?
- Do menu items respond to input?
- Are transitions between screens working?

### Step 6: Adjust and Re-run

Go back to the text file, adjust values, save, re-run. Each cycle should take seconds,
not minutes. This tight loop is the core of text-first layout work.

---

## The Golden Iteration Loop

```
┌─────────────────────────────────────────────┐
│  1. Edit .layout / .style / .menu file      │
│  2. Save                                    │
│  3. Run project  (Ctrl+R / Play button)     │
│  4. Observe runtime behavior                │
│  5. Note what's wrong or needs adjustment   │
│  6. Stop runtime                            │
│  7. Edit file again                         │
│  8. Repeat from step 2                      │
└─────────────────────────────────────────────┘
```

**Key discipline:** Do not try to get everything perfect in one pass. Make one change, verify
it, then make the next. Small increments catch mistakes early.

**Tip:** Keep the text file and the runtime window side by side on your monitor. This
minimizes context-switching time.

---

## Annotated Examples

### dialogue.layout — Dialogue Box Geometry and Style

This single file controls both the VN dialogue box layout **and** its visual style.
It lives at `config/ui/dialogue.layout`. Fallback paths: `config/vn/dialogue.layout`,
then `dialogue.layout` at project root.

```properties
# ──────────────────────────────────────────────
# config/ui/dialogue.layout
# Full annotated example
# ──────────────────────────────────────────────

# ── Text Box Position (fraction of viewport, 0.0–1.0) ──
textBoxX=0.0                    # Left edge of text box (0.0 = flush left)
textBoxY=0.75                   # Top edge of text box (0.75 = bottom quarter)
textBoxWidth=1.0                # Width as fraction of viewport (1.0 = full width)
textBoxHeight=0.25              # Height as fraction of viewport

# ── Text Box Internal Padding (pixels) ──
textBoxPadding=20.0             # General padding inside the text box

# ── Name Box (relative to text box, pixels) ──
nameBoxXOffset=20.0             # Horizontal offset from text box left
nameBoxYOffset=-40.0            # Vertical offset (negative = above text box)
nameBoxWidth=200.0              # Width of the name plate
nameBoxHeight=40.0              # Height of the name plate
nameTextXOffset=10.0            # Text left-padding inside name box
nameTextBaselineOffset=25.0     # Baseline position of name text

# ── Dialogue Text Padding (pixels) ──
dialogueTextHorizontalPadding=20.0   # Left padding for dialogue text
dialogueTextTopPadding=40.0          # Top padding for dialogue text
dialogueTextRightPadding=20.0        # Right padding (defaults to horizontal if omitted)
dialogueTextBottomPadding=10.0       # Bottom padding

# ── Choice Buttons ──
choiceXCenter=0.5               # Horizontal center (0.5 = screen center)
choiceYStart=-1.0               # Vertical start (-1 = auto-center; 0.0–1.0 = fixed)
choiceWidthFactor=0.6           # Width as fraction of viewport (0.1–1.0)
choiceHeight=50.0               # Height of each choice button (pixels, min 14)
choiceGap=10.0                  # Gap between choice buttons (pixels)
choiceTextXPadding=20.0         # Horizontal text padding inside choice buttons

# ── Style Keys (visual, same file) ──
textBoxAsset=assets/ui/textbox.png       # (optional) 9-slice or image for text box
textBoxColor=#000000CC                   # (optional) Fallback color if no asset
textBoxOpacity=0.85                      # (optional) 0.0–1.0

nameBoxAsset=assets/ui/namebox.png       # (optional) Name box background image
nameBoxColor=#1A1A2E                     # (optional) Fallback color
nameTextColor=#FFFFFF                    # (optional) Name text color
nameTextFontFamily=Segoe UI              # (optional) Font family for name
nameTextFontSize=18                      # (optional) Font size for name
# nameTextFontWeight=NORMAL              # (optional) NORMAL or BOLD
# nameBoxOpacity=1.0                     # (optional) 0.0–1.0

dialogueTextColor=#F0F0F0                # (optional) Dialogue body text color
dialogueTextFontFamily=Segoe UI          # (optional) Font family for dialogue
dialogueTextFontSize=22                  # (optional) Font size for dialogue
# dialogueTextFontWeight=NORMAL          # (optional) NORMAL or BOLD

# ── Choice Visual Style ──
choiceButtonAsset=assets/ui/choice.png           # (optional) Button image
choiceButtonHoverAsset=assets/ui/choice_hover.png    # (optional)
choiceButtonSelectedAsset=assets/ui/choice_sel.png   # (optional)
choiceButtonDisabledAsset=assets/ui/choice_dis.png   # (optional)

choiceBackgroundColor=#2A2A4A            # (optional) Choice button fill color
choiceHoverColor=#3A3A6A                 # (optional) Hover state color
choiceSelectedColor=#4A4A8A              # (optional) Selected state color
choiceDisabledColor=#1A1A2A              # (optional) Disabled state color

choiceTextColor=#FFFFFF                  # (optional) Normal text color
choiceHoverTextColor=#FFE8A3             # (optional)
choiceSelectedTextColor=#FFD700          # (optional)
choiceDisabledTextColor=#666666          # (optional)

choiceBorderColor=#555588                # (optional) Border color
choiceHoverBorderColor=#7777AA           # (optional)
choiceSelectedBorderColor=#9999CC        # (optional)
choiceDisabledBorderColor=#333355        # (optional)

choiceCornerRadius=10.0                  # (optional) 0–96
choiceBorderWidth=2.0                    # (optional) 0–12
choiceTextBaselineOffset=5.0             # (optional) -120 to 120
choiceFontFamily=Segoe UI                # (optional)
choiceFontSize=20                        # (optional)
# choiceFontWeight=NORMAL                # (optional) NORMAL or BOLD

# ── Character Framing (optional) ──
characterHeightFactor=0.85               # (optional) 0.1–3.0
characterBaselineY=0.95                  # (optional) -0.5 to 2.0
```

**Key rules:**
- `textBoxX`, `textBoxY`, `textBoxWidth`, `textBoxHeight` are **fractions** (0.0–1.0).
- Padding and offset values are in **pixels**.
- `choiceYStart=-1` means auto-center vertically; any value 0.0–1.0 is a fixed position.
- Only set the keys you want to override. Omitted keys use engine defaults.

#### Runtime Validation Checklist — dialogue.layout

After running the project with a VN scene loaded:

- [ ] Text box appears at the expected screen position
- [ ] Text box width and height match your intent
- [ ] Name plate is positioned correctly relative to the text box
- [ ] Character name text is visible and not clipped
- [ ] Dialogue text has appropriate padding (no text touching edges)
- [ ] If `dialogueTextRightPadding` is set, long lines wrap before the right edge
- [ ] Choice buttons appear centered (or at your configured position)
- [ ] Choice buttons have correct width and spacing
- [ ] Choice text is not clipped inside buttons
- [ ] Hover/select/disabled colors work when navigating choices
- [ ] If using asset images, they load without console errors
- [ ] Character sprites are framed correctly (if using `characterHeightFactor`/`characterBaselineY`)

---

### Menu .layout — Menu List Positioning

Controls where the menu item list sits on screen, its spacing, and alignment.
Lives at `config/menu/layouts/<id>.layout`.

```properties
# ──────────────────────────────────────────────
# config/menu/layouts/default.layout
# Full annotated example — Main menu list layout
# ──────────────────────────────────────────────

# Where the item list starts vertically (fraction of screen height, >= 0)
listYStart=0.34

# Pixel height of each menu item row (must be > 0)
lineHeight=68.0

# Width of the item list as a fraction of screen width (0.1–1.0)
listWidthFactor=0.44

# Text alignment within items: left | center | right
textAlign=center

# Bottom margin for the hints bar (pixels, >= 0)
hintsBottomMargin=36.0

# (optional) Vertical position of the menu title (fraction, >= 0; omit for no title)
titleY=0.14
```

**Key rules:**
- `listYStart` and `titleY` are fractions of screen height.
- `lineHeight` and `hintsBottomMargin` are in pixels.
- `listWidthFactor` is clamped to 0.1–1.0.
- `textAlign` accepts only `left`, `center`, or `right`.
- `titleY` is optional — omit it entirely if the screen has no title.

**Built-in layout presets:**

| ID | listYStart | lineHeight | listWidthFactor | textAlign | hintsBottomMargin | titleY |
|---|---|---|---|---|---|---|
| `default` | 0.38 | 62.0 | 0.36 | center | 32.0 | 0.16 |
| `submenu` | 0.26 | 56.0 | 0.52 | left | 28.0 | 0.13 |
| `settings` | 0.16 | 50.0 | 0.56 | left | 24.0 | 0.07 |
| `slots` | 0.22 | 68.0 | 0.54 | left | 28.0 | 0.12 |

#### Runtime Validation Checklist — Menu .layout

- [ ] Menu items appear at the expected vertical position on screen
- [ ] Item spacing (line height) looks comfortable, not too tight or loose
- [ ] Item list width is appropriate (not too narrow or overflowing)
- [ ] Text alignment matches your intent (centered for main menu, left for submenus, etc.)
- [ ] Title text (if using `titleY`) is positioned above the item list
- [ ] Hints bar text at the bottom has enough margin from the screen edge
- [ ] On different window sizes, fractional values scale as expected

---

### Menu .style — Menu Visual Appearance

Controls colors, fonts, button skins, and text effects for menu items.
Lives at `config/menu/styles/<id>.style`.

```properties
# ──────────────────────────────────────────────
# config/menu/styles/default.style
# Full annotated example — Main menu visual style
# ──────────────────────────────────────────────

# ── Item Text Colors ──
itemColor=#DCE6F8                    # Normal item text color
itemSelectedColor=#FFE8A3            # Selected/focused item text color
itemHoverColor=#F4F8FF               # (optional) Mouse hover text color
itemDisabledColor=#7D8CA8            # Disabled item text color

# ── Item Text Prefixes ──
# Characters prepended to menu item labels in each state
itemPrefix=                          # (optional) Normal state prefix (empty = none)
itemSelectedPrefix=▶                 # (optional) Prefix when selected
itemDisabledPrefix=•                 # (optional) Prefix when disabled
# To clear an inherited prefix, keep the value explicitly blank:
# itemSelectedPrefix=

# ── Item Font ──
itemFontFamily=Segoe UI              # (optional) Font family name
itemFontWeight=SEMI_BOLD             # (optional) NORMAL | BOLD | SEMI_BOLD
itemFontSize=28                      # (optional) Font size in points (must be > 0)

# ── Item Text Effects ──
itemShadowColor=#000000AA            # (optional) Drop shadow color
itemShadowOffsetX=1.0                # (optional) Shadow X offset (pixels)
itemShadowOffsetY=1.0                # (optional) Shadow Y offset (pixels)
itemOpacity=1.0                      # (optional) Item opacity 0.0–1.0

# ── Button Skins (optional, for image-backed menu items) ──
buttonAsset=assets/ui/menu_btn.png            # (optional) Normal state button image
buttonSelectedAsset=assets/ui/menu_btn_sel.png    # (optional) Selected state
buttonHoverAsset=assets/ui/menu_btn_hover.png     # (optional) Hover state
buttonDisabledAsset=assets/ui/menu_btn_dis.png    # (optional) Disabled state
buttonTextPaddingX=28.0              # (optional) Horizontal text padding inside button
buttonTextPaddingY=2.0               # (optional) Vertical text padding inside button

# ── Title Styling ──
titleColor=#F2F7FF                   # (optional) Title text color
titleFontFamily=Segoe UI             # (optional) Title font family
titleFontWeight=BOLD                 # (optional) Title font weight
titleFontSize=56                     # (optional) Title font size
titleShadowColor=#000000A8           # (optional) Title shadow color

# ── Hints Bar Styling ──
hintsColor=#A8B6D2                   # (optional) Hints text color
hintsFontFamily=Segoe UI             # (optional) Hints font family
# hintsFontWeight=NORMAL             # (optional) NORMAL | BOLD | SEMI_BOLD
hintsFontSize=18                     # (optional) Hints font size

# ── Background ──
backgroundAsset=assets/demo/backgrounds/field/glorious_ricefield_day.png  # (optional)
backgroundColor=#050B16              # (optional) Fallback solid color
backgroundOpacity=1.0                # (optional) 0.0–1.0
```

**Key rules:**
- Colors use hex format: `#RRGGBB` or `#RRGGBBAA` (with alpha).
- `itemFontWeight` accepts `NORMAL`, `BOLD`, or `SEMI_BOLD`.
- `itemOpacity` and `backgroundOpacity` are clamped to 0.0–1.0.
- Button asset paths are relative to your project root.
- All keys are optional. Set only what you need to customize.

#### Runtime Validation Checklist — Menu .style

- [ ] Normal item text renders in the expected color
- [ ] Selected item text changes color and shows the selection prefix
- [ ] Disabled items show the correct dimmed color and prefix
- [ ] Font family renders correctly (check the system has the font installed)
- [ ] Font size is readable at your target resolution
- [ ] Text shadow appears in the correct direction and color
- [ ] If using button assets, images load and align with text
- [ ] Button text padding keeps text centered inside button images
- [ ] Title text appears with the correct color, font, and size
- [ ] Hints bar text is legible against the background
- [ ] Background image or color fills the screen behind the menu
- [ ] Hover color works when moving the mouse over items (if hover is configured)

---

### Menu .menu — Screen Definition

Defines a single menu screen: its title, items, item actions, and which layout/style to use.
Lives at `config/menu/menus/<id>.menu`.

```properties
# ──────────────────────────────────────────────
# config/menu/menus/main.menu
# Full annotated example — Main menu screen
# ──────────────────────────────────────────────

# (optional) Inherit all properties from another menu screen
# extends=base_menu

# Screen title and hints text shown by the renderer
titleText=My Visual Novel
hintsText=Enter: Select    Esc: Quit

# Which layout and style to use (must match IDs from .layout and .style files)
layout=default
defaultItemStyle=default

# Wrap selection: if true, pressing Down on the last item selects the first
wrapSelection=true

# ── Menu Items ──
# List item IDs explicitly (or let the engine auto-discover from item.* keys)
items=new_game,load,settings,extras,quit

# ── Item: new_game ──
item.new_game.label=New Game
item.new_game.action=new_game
item.new_game.enabled=true
# item.new_game.style=default        # (optional) Override per-item style
# item.new_game.icon=assets/ui/icon_new.png  # (optional)

# ── Item: load ──
item.load.label=Load Journey
item.load.action=load_menu

# ── Item: settings ──
item.settings.label=Settings
item.settings.action=settings_menu

# ── Item: extras ──
item.extras.label=Extras
item.extras.action=open_menu
item.extras.target=extras

# ── Item: quit ──
item.quit.label=Quit
item.quit.action=quit
```

**Available action types:**

| Action Value | Aliases | Requires `target`? | Description |
|---|---|---|---|
| `new_game` | `new`, `start`, `start_game` | No | Start a new game |
| `load_menu` | `load`, `continue` | No | Open load screen |
| `save_menu` | `save` | No | Open save screen |
| `settings_menu` | `settings`, `options` | No | Open settings screen |
| `main_menu` | `main`, `title`, `title_menu` | No | Return to main menu |
| `open_menu` | `submenu`, `menu` | **Yes** | Open another menu by ID |
| `run_script` | `script`, `start_script`, `play_script` | **Yes** | Run a VNS script |
| `back` | `return` | No | Go back to previous menu |
| `quit` | `exit` | No | Quit the game |
| `noop` | `none`, `no_op` | No | Do nothing (decorative items) |

**Action shorthand:** You can combine action and target with a colon:
`item.extras.action=open_menu:extras` instead of separate `action` and `target` lines.

**Per-item fields reference:**

| Field | Type | Description |
|---|---|---|
| `label` | String | Display text for the item |
| `style` | String | (optional) Style ID override for this item |
| `icon` | String | (optional) Icon asset path |
| `enabled` | Boolean | (optional) `true`/`false`/`yes`/`no`/`1`/`0` |
| `action` | String | Action type (see table above) |
| `target` | String | Target for `open_menu` or `run_script` actions |
| `bgAsset` | String | (optional) Per-item button background image |
| `bgSelectedAsset` | String | (optional) Selected state button image |
| `bgDisabledAsset` | String | (optional) Disabled state button image |
| `boundsX` | Double | (optional) Explicit X position (all 4 bounds required together) |
| `boundsY` | Double | (optional) Explicit Y position |
| `boundsWidth` | Double | (optional) Explicit width |
| `boundsHeight` | Double | (optional) Explicit height |
| `slotPreviewEnabled` | Boolean | (optional) Show save slot thumbnail preview |
| `slotPreviewPlaceholderAsset` | String | (optional) Placeholder image for empty slot |
| `slotPreviewFrameAsset` | String | (optional) Frame image around slot preview |
| `slotPreviewX` | Double | (optional) Preview thumbnail X |
| `slotPreviewY` | Double | (optional) Preview thumbnail Y |
| `slotPreviewWidth` | Double | (optional) Preview thumbnail width |
| `slotPreviewHeight` | Double | (optional) Preview thumbnail height |

**Item auto-discovery:** If you omit the `items=` line, the engine discovers items automatically
from any `item.<id>.<field>` keys present in the file.

**Inheritance:** Use `extends=other_menu` to inherit items and settings from another menu.
The child menu can override individual items by re-declaring them.

#### Runtime Validation Checklist — Menu .menu

- [ ] Menu screen opens with the correct title text
- [ ] Hints text appears at the bottom
- [ ] All expected items are visible and in the correct order
- [ ] Item labels display the right text
- [ ] Selecting each item triggers the correct action (navigate, start game, quit, etc.)
- [ ] `open_menu` items navigate to the target menu
- [ ] Disabled items cannot be selected (and show disabled styling)
- [ ] `wrapSelection` behavior works (Down on last item → first item, or stops)
- [ ] If using `extends`, inherited items appear and can be overridden
- [ ] Back/Esc navigation returns to the previous menu correctly
- [ ] Items with per-item button assets display the images correctly
- [ ] If using explicit bounds, items appear at the configured positions

---

### menu.registry — Wiring File

Tells the engine which menus, layouts, and styles exist and which menu is the default
entry point. Lives at `config/menu/registry/menu.registry`.

```properties
# ──────────────────────────────────────────────
# config/menu/registry/menu.registry
# Full annotated example
# ──────────────────────────────────────────────

# Which menu screen opens first (required)
defaultMenu=main

# Comma-separated list of menu screen IDs to load
menus=main,load,save,settings,extras,credits,confirm_exit

# Comma-separated list of layout IDs to load
layouts=default,submenu,slots

# Comma-separated list of style IDs to load
styles=default,submenu,slot
```

**Key rules:**
- `defaultMenu` (or alias `defaultScreen`) sets the first screen shown.
- IDs in `menus` correspond to `config/menu/menus/<id>.menu` files.
- IDs in `layouts` correspond to `config/menu/layouts/<id>.layout` files.
- IDs in `styles` correspond to `config/menu/styles/<id>.style` files.
- The engine also auto-discovers files by scanning the `config/menu/` directories,
  so the registry is not strictly required — but it gives you explicit control over
  load order and avoids ambiguity.

**Fallback paths for the registry** (first match wins):
1. `config/menu/registry/menu.registry`
2. `config/menu/menu.registry`
3. `config/menu/registry.properties`
4. `menu.registry`

#### Runtime Validation Checklist — menu.registry

- [ ] The game starts on the `defaultMenu` screen
- [ ] Every menu listed in `menus=` is reachable through navigation
- [ ] Every layout listed in `layouts=` loads without warnings in the console
- [ ] Every style listed in `styles=` loads without warnings in the console
- [ ] No "undefined layout" or "undefined style" warnings in the console
- [ ] Removing an ID from the registry makes the engine fall back to defaults (intentional)
- [ ] Adding a new menu screen and registering it makes it immediately accessible

---

## Common Mistakes and Debugging

### Problem: "My changes aren't showing up"

**Cause:** You edited a file that the engine isn't loading, or the file is in the wrong path.

**Fix:**
1. Check the file is in the correct directory (see File Map above).
2. Confirm the file extension matches: `.layout`, `.style`, `.menu`, or `.registry`.
3. Make sure the ID is listed in `menu.registry` (or the file is in a discoverable path).
4. Re-run the project — layout files are loaded at startup, not hot-reloaded.

### Problem: "Value clamped" or "Invalid value" warnings

**Cause:** A numeric value is outside the valid range.

**Fix:** Check the valid ranges documented above. Common traps:
- `textBoxX` + `textBoxWidth` must not exceed 1.0 (auto-clamped).
- `listWidthFactor` must be 0.1–1.0.
- `choiceHeight` minimum is 14, `lineHeight` must be > 0.

### Problem: "Unknown key" warnings in the console

**Cause:** A typo in a key name.

**Fix:** The engine reports unknown keys in diagnostics. Check your spelling against the
key tables in this guide. Common typos:
- `textbox` instead of `textBox` (camelCase required)
- `fontSize` instead of `itemFontSize` (menu styles need the `item` prefix)
- `color` instead of `itemColor`

### Problem: Menu items don't appear

**Cause:** The `items=` list is empty or item IDs don't match `item.<id>.label` keys.

**Fix:**
1. Either add `items=id1,id2,id3` explicitly, or ensure `item.<id>.label` keys exist.
2. Check for typos in item IDs — the ID in `items=` must match the `item.<id>.` prefix exactly.

### Problem: "OPEN_MENU action requires a target"

**Cause:** You used `action=open_menu` without specifying `target`.

**Fix:** Add `item.<id>.target=other_menu_id` or use the shorthand `action=open_menu:other_menu_id`.

### Problem: Partial bounds warning

**Cause:** You set some but not all four bounds fields (X, Y, Width, Height).

**Fix:** Either set all four (`boundsX`, `boundsY`, `boundsWidth`, `boundsHeight`) or none of them.

### Problem: Font doesn't render correctly

**Cause:** The font family specified isn't installed on the runtime system.

**Fix:** Use widely available fonts (`Segoe UI` on Windows, `Helvetica Neue` on macOS) or
bundle a custom font in your project assets.

---

## Move between Design and Source

Drag items or use the inspector in **Design**, then switch to **Source** for exact values, batch
edits, extensions, or diagnostics. Both tabs share undo and save state. Review the properties diff
to see which declarations changed. Use **Save and Run Runtime** to verify input, navigation, and
script-specific behavior.

---

## Best Practices

1. **One change at a time.** Modify one or two keys, then re-run. Resist the urge to
   change twenty values at once.

2. **Use comments.** Document your intent with `#` comments in layout files:
   ```properties
   # Moved text box up to leave room for character sprites
   textBoxY=0.70
   ```

3. **Version control everything.** Layout files are plain text — perfect for Git. Commit
   early and often.

4. **Use the registry.** Even though the engine auto-discovers files, an explicit
   `menu.registry` makes your project's structure clear to collaborators.

5. **Check console output.** The engine prints diagnostics for invalid values, unknown keys,
   and missing references. Read them after every run.

6. **Use default values as a starting point.** Don't set every key. Start with the engine
   defaults and override only what you need. This keeps files short and maintainable.

7. **Name files by their ID.** `main.menu`, `default.layout`, `default.style` — the file
   name (minus extension) is the ID the engine uses.

8. **Test at multiple resolutions.** Fractional values (`textBoxY`, `listYStart`) scale
   with viewport size, but pixel values (`lineHeight`, `textBoxPadding`) do not.

---

## FAQ

**Q: Do I need a `menu.registry` file?**
A: Not strictly. The engine auto-discovers `.layout`, `.style`, and `.menu` files in
`config/menu/` and its subdirectories. But an explicit registry is recommended for clarity
and to set `defaultMenu`.

**Q: Why is there no visual preview in Layout Studio?**
A: A preview duplicated layout parsing and rendering without being able to prove actions, input,
fonts, or final platform output. Layout Studio now concentrates on accurate source diagnostics and
launches the real runtime for visual and behavioral validation.

**Q: What happens if I misspell a key?**
A: The engine ignores unknown keys and logs a diagnostic warning. Your layout will use the
default value for that property. Check console output after running.

**Q: Can one menu screen use a different layout than another?**
A: Yes. Each `.menu` file has a `layout=<id>` property that references a layout by ID. Two
different menus can reference two different layouts.

**Q: Can I inherit from another menu?**
A: Yes. Use `extends=parent_menu_id` in a `.menu` file. The child inherits all items and
settings from the parent and can override individual items.

**Q: What's the difference between `listWidthFactor` and `listWidth`?**
A: `listWidth` is a deprecated compatibility alias for `listWidthFactor`. It still loads, but the
runtime and Layout Studio emit a warning so projects can migrate before a future specification
version removes it. Use `listWidthFactor` in new files. The same policy applies to the deprecated
screen key `layoutId`; replace it with `layout`.

**Q: How do I make a decorative (non-interactive) menu item?**
A: Set its action to `noop` and `enabled=false`:
```properties
item.credits_line.label=Made with JVN Engine
item.credits_line.action=noop
item.credits_line.enabled=false
```

**Q: Can I add custom per-item data that the engine preserves?**
A: Yes. Any `item.<id>.<field>` key that isn't a known field is stored as an "extra" and
preserved through save/load cycles. You can access extras from custom runtime code.

**Q: What units are used for positions?**
A: It depends on the key. Viewport fractions (0.0–1.0) are used for `textBoxX/Y/Width/Height`,
`listYStart`, `listWidthFactor`, `titleY`, `choiceXCenter`, `choiceYStart`, and
`choiceWidthFactor`. Everything else (padding, offsets, dimensions) is in pixels.

**Q: Do layout changes require recompiling the project?**
A: No. Layout files are plain-text configuration read at runtime startup. Just save and re-run.
