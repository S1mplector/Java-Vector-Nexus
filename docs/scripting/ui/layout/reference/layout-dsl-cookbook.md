# Layout DSL Cookbook (Runtime-First Validation)

> Practical reference for every JVN layout DSL file type, with copy-paste recipes
> and runtime validation steps.

---

## Purpose

This cookbook is a hands-on companion to the [Text-First Layout Workflow](../workflow/text-first-layout-workflow.md)
guide. Where the workflow guide teaches the *process*, this cookbook gives you *recipes*:
concrete, annotated examples you can copy into your project, tweak, and validate immediately
in runtime.

Every recipe follows the same structure:
1. What you're building
2. The complete file contents
3. What to check in runtime
4. Variations and tips

---

## Fast Start

Pick a recipe below, copy the file into the correct directory, run your project, and
verify the runtime checklist. Adjust values and re-run until satisfied.

**Minimum viable project structure for menus:**

```
your-project/
├── config/
│   ├── ui/
│   │   └── dialogue.layout          # VN dialogue box
│   └── menu/
│       ├── registry/
│       │   └── menu.registry         # Wiring file
│       ├── layouts/
│       │   └── default.layout        # Menu list positioning
│       ├── styles/
│       │   └── default.style         # Menu visual appearance
│       └── menus/
│           └── main.menu             # Main menu screen
```

You don't need all of these to get started. The engine provides sensible defaults for
everything. Add files only for the things you want to customize.

---

## Step-by-Step: Adding a New Menu Screen

This walkthrough creates a custom "Gallery" submenu from scratch.

### 1. Create the menu file

Create `config/menu/menus/gallery.menu`:

```properties
titleText=Gallery
hintsText=Enter: View    Esc: Back
layout=submenu
defaultItemStyle=submenu

items=cg_gallery,music_room,back

item.cg_gallery.label=CG Gallery
item.cg_gallery.action=noop
item.cg_gallery.enabled=false

item.music_room.label=Music Room
item.music_room.action=noop
item.music_room.enabled=false

item.back.label=Return
item.back.action=back
```

### 2. Register it

Add `gallery` to your `config/menu/registry/menu.registry`:

```properties
defaultMenu=main
menus=main,load,save,settings,extras,gallery,confirm_exit
layouts=default,submenu,slots
styles=default,submenu,slot
```

### 3. Link to it from another menu

In `config/menu/menus/main.menu`, add an item that navigates to gallery:

```properties
item.gallery.label=Gallery
item.gallery.action=open_menu
item.gallery.target=gallery
```

And add `gallery` to the `items=` list.

### 4. Run and verify

Run the project. Navigate to the gallery from the main menu. Confirm:

- [ ] Gallery screen opens with title "Gallery"
- [ ] Three items appear: CG Gallery, Music Room, Return
- [ ] CG Gallery and Music Room are grayed out (disabled)
- [ ] Return navigates back to the previous menu
- [ ] Hints text shows at the bottom

---

## Recipe 1: Dialogue Layout — Standard VN Text Box

A conventional bottom-of-screen dialogue box with a name plate above it.

### File: `config/ui/dialogue.layout`

```properties
# ── Position: full-width bar at the bottom 25% of screen ──
textBoxX=0.0
textBoxY=0.75
textBoxWidth=1.0
textBoxHeight=0.25
textBoxPadding=20.0

# ── Name plate: small box above the text box ──
nameBoxXOffset=20.0
nameBoxYOffset=-40.0
nameBoxWidth=200.0
nameBoxHeight=40.0
nameTextXOffset=10.0
nameTextBaselineOffset=25.0
# nameBoxAutoWidth=true

# ── Dialogue text area ──
dialogueTextHorizontalPadding=20.0
dialogueTextTopPadding=40.0
dialogueTextRightPadding=20.0
dialogueTextBottomPadding=10.0

# ── Choice buttons: centered horizontally, auto-centered vertically ──
choiceXCenter=0.5
choiceYStart=-1.0
choiceWidthFactor=0.6
choiceHeight=50.0
choiceGap=10.0
choiceTextXPadding=20.0

# ── Visual style ──
textBoxColor=#000000CC
textBoxOpacity=0.85
nameBoxColor=#1A1A2EEE
nameTextColor=#FFFFFF
nameTextFontFamily=Segoe UI
nameTextFontSize=18
# nameTextFontWeight=NORMAL
# nameBoxOpacity=1.0
dialogueTextColor=#F0F0F0
dialogueTextFontFamily=Segoe UI
dialogueTextFontSize=22
# dialogueTextFontWeight=NORMAL

# ── Choice button colors ──
choiceBackgroundColor=#2A2A4A
choiceHoverColor=#3A3A6A
choiceSelectedColor=#4A4A8A
choiceDisabledColor=#1A1A2A
choiceTextColor=#FFFFFF
choiceHoverTextColor=#FFE8A3
choiceSelectedTextColor=#FFD700
choiceDisabledTextColor=#666666
choiceBorderColor=#555588
choiceCornerRadius=8.0
choiceBorderWidth=2.0
choiceFontFamily=Segoe UI
choiceFontSize=20
# choiceFontWeight=NORMAL
```

### Runtime Checklist

- [ ] Text box covers the bottom quarter of the screen
- [ ] Name plate sits just above the text box, left-aligned
- [ ] Character name text fits inside the name plate
- [ ] Dialogue text wraps correctly with proper padding
- [ ] When choices appear, they are centered on screen
- [ ] Choice buttons have the correct background, hover, and selection colors
- [ ] Border radius is visible on choice buttons
- [ ] Font is readable at normal game resolution

### Variations

**Narrower text box (cinematic bars):**
```properties
textBoxX=0.1
textBoxWidth=0.8
```

**Taller text box for longer dialogue:**
```properties
textBoxY=0.65
textBoxHeight=0.35
```

**Choices pinned to a fixed position instead of auto-center:**
```properties
choiceYStart=0.3
```

**No name plate (narrator-only):**
```properties
nameBoxWidth=0.0
nameBoxHeight=0.0
```

---

### Separate dialogue and inner-monologue skins

The JavaFX renderer can switch textbox artwork per line. Configure both paths in
`config/ui/dialogue.layout`:

```properties
textBoxAsset=assets/ui/dialogue.png
textBoxNarrationAsset=assets/ui/monologue.png
```

A nonempty speaker display name selects `textBoxAsset` and shows the name plate.
An empty display name selects `textBoxNarrationAsset` and hides the name plate:

```vns
@character hero "Hero"
@character hero_thought ""
hero: Hello there.
hero_thought: I wonder what happens next.
hero: Shall we go?
```

Declare or include the thought character in every script that uses it. The ID
`hero_thought` is an ordinary character ID; its empty display name controls the
presentation. Parentheses and quotes in colon-style dialogue are literal text and
do not select a textbox style. A character named `narrator` with a nonempty display
name still uses the dialogue skin.

Both skins share textbox geometry, text insets, fonts, and action buttons. Export
the images on matching canvases. If no narration image is configured or resolved,
unnamed lines fall back to the dialogue image while keeping the name plate hidden.
The runtime's default-UI or disabled-asset overrides still take precedence.

## Recipe 2: Dialogue Layout — ADV-Style (Full-Screen Text)

An adventure-game style where text fills most of the screen.

### File: `config/ui/dialogue.layout`

```properties
# ── Full-screen text area ──
textBoxX=0.05
textBoxY=0.05
textBoxWidth=0.9
textBoxHeight=0.9
textBoxPadding=30.0

# ── Name plate: top-left inside the box ──
nameBoxXOffset=10.0
nameBoxYOffset=5.0
nameBoxWidth=250.0
nameBoxHeight=36.0
nameTextXOffset=8.0
nameTextBaselineOffset=24.0

# ── Generous padding for readability ──
dialogueTextHorizontalPadding=40.0
dialogueTextTopPadding=60.0
dialogueTextRightPadding=40.0
dialogueTextBottomPadding=30.0

# ── Choices below center ──
choiceXCenter=0.5
choiceYStart=0.6
choiceWidthFactor=0.5
choiceHeight=44.0
choiceGap=8.0
choiceTextXPadding=16.0

# ── Style: semi-transparent dark overlay ──
textBoxColor=#0A0A1AE8
textBoxOpacity=0.92
nameTextColor=#FFD700
dialogueTextColor=#E8E8F0
dialogueTextFontSize=20
```

### Runtime Checklist

- [ ] Text box covers almost the entire screen with margins
- [ ] Name plate is positioned inside the top of the text box
- [ ] Dialogue text starts below the name plate with proper top padding
- [ ] Long passages wrap and don't clip at the bottom
- [ ] Choices appear in the lower-center area
- [ ] Background is visible through the semi-transparent overlay

---

## Recipe 3: Menu Layout — Centered Main Menu

Classic centered main menu with a title at the top.

### File: `config/menu/layouts/default.layout`

```properties
# Item list starts at 38% from the top
listYStart=0.38

# Each item row is 62px tall
lineHeight=62.0

# Item list occupies 36% of screen width, centered
listWidthFactor=0.36

# Text centered within items
textAlign=center

# Hints bar margin from the bottom
hintsBottomMargin=32.0

# Title at 16% from the top
titleY=0.16
```

### Runtime Checklist

- [ ] Title appears in the upper portion of the screen
- [ ] Menu items are vertically centered in the middle area
- [ ] Items are horizontally centered
- [ ] Spacing between items is even and comfortable
- [ ] Hints text is visible at the bottom with adequate margin
- [ ] At different window sizes, proportions hold

### Variations

**Left-aligned submenu (for settings, load, etc.):**
```properties
listYStart=0.24
lineHeight=62.0
listWidthFactor=0.64
textAlign=left
hintsBottomMargin=30.0
titleY=0.11
```

**Compact slot list (for save/load screens):**
```properties
listYStart=0.20
lineHeight=74.0
listWidthFactor=0.58
textAlign=left
hintsBottomMargin=30.0
titleY=0.10
```

---

## Recipe 4: Menu Style — Dark Theme

A dark, atmospheric menu style suitable for horror or mystery games.

### File: `config/menu/styles/dark.style`

```properties
# ── Dark theme item colors ──
itemColor=#B8C4D8
itemSelectedColor=#FF6B6B
itemHoverColor=#DDE4F0
itemDisabledColor=#4A5568

# ── Selection prefix ──
itemSelectedPrefix=» 
itemDisabledPrefix=  

# ── Font ──
itemFontFamily=Georgia
itemFontWeight=NORMAL
itemFontSize=24

# ── Subtle shadow ──
itemShadowColor=#00000088
itemShadowOffsetX=2.0
itemShadowOffsetY=2.0

# ── Title ──
titleColor=#FF4444
titleFontFamily=Georgia
titleFontWeight=BOLD
titleFontSize=48
titleShadowColor=#000000CC

# ── Hints ──
hintsColor=#667788
hintsFontFamily=Georgia
hintsFontWeight=NORMAL
hintsFontSize=14

# ── Background ──
backgroundColor=#0A0A0F
backgroundOpacity=1.0
```

### Runtime Checklist

- [ ] Item text appears in a muted blue-grey color
- [ ] Selected item turns red with the `»` prefix
- [ ] Disabled items are dark and clearly non-interactive
- [ ] Font is Georgia (or system fallback if unavailable)
- [ ] Drop shadow is subtle but visible
- [ ] Title text is large, bold, and red
- [ ] Hints bar text is small and unobtrusive
- [ ] Background is near-black

---

## Recipe 5: Menu Style — Light/Pastel Theme

A bright, friendly style for slice-of-life or casual games.

### File: `config/menu/styles/pastel.style`

```properties
itemColor=#4A4A6A
itemSelectedColor=#E85D75
itemHoverColor=#6A6A8A
itemDisabledColor=#B0B0C8

itemSelectedPrefix=♦ 

itemFontFamily=Segoe UI
itemFontWeight=SEMI_BOLD
itemFontSize=26

itemOpacity=1.0

# ── Title ──
titleColor=#E85D75
titleFontFamily=Segoe UI
titleFontWeight=BOLD
titleFontSize=52

# ── Hints ──
hintsColor=#8888AA

# ── Background ──
backgroundColor=#FFF5F0
backgroundOpacity=1.0
```

### Runtime Checklist

- [ ] Items render in dark purple-grey text on a light background
- [ ] Selected item is a warm pink/coral
- [ ] Background is a warm off-white
- [ ] Title is prominent and colorful
- [ ] Overall feel is bright and inviting

---

## Recipe 6: Menu Style — With Button Images

Using image assets for menu item backgrounds instead of plain text.

### File: `config/menu/styles/fancy.style`

```properties
itemColor=#FFFFFF
itemSelectedColor=#FFD700
itemDisabledColor=#888888

itemFontFamily=Segoe UI
itemFontWeight=BOLD
itemFontSize=22

# ── Button images ──
buttonAsset=assets/ui/btn_normal.png
buttonSelectedAsset=assets/ui/btn_selected.png
buttonHoverAsset=assets/ui/btn_hover.png
buttonDisabledAsset=assets/ui/btn_disabled.png

# ── Text padding inside button images ──
buttonTextPaddingX=40.0
buttonTextPaddingY=8.0

# ── No text shadow (button images provide contrast) ──
# itemShadowColor=

# ── Title ──
titleColor=#FFFFFF
titleFontSize=44

# ── Background image ──
backgroundAsset=assets/ui/menu_bg.png
```

### Runtime Checklist

- [ ] Each menu item renders with the button image behind the text
- [ ] Selected state swaps to the selected button image
- [ ] Hover state (mouse) swaps to the hover button image
- [ ] Disabled items use the disabled button image
- [ ] Text is horizontally centered within the button with correct padding
- [ ] Title renders above the button items
- [ ] Background image fills the screen behind everything
- [ ] No console errors about missing asset paths

---

## Recipe 7: Menu Screen — Save/Load with Slot Previews

A save or load screen with thumbnail previews for each save slot.

### File: `config/menu/menus/save.menu`

```properties
titleText=Save Journey
hintsText=Enter: Save    Esc: Back    Del: Delete    R: Rename
layout=slots
defaultItemStyle=slot
wrapSelection=true

items=new_slot,save_slot

# ── "New Save" item at the top ──
item.new_slot.label=Create New Save
item.new_slot.style=submenu
item.new_slot.action=save_menu

# ── Template item for each save slot ──
# The engine generates one copy of this per existing save
item.save_slot.action=save_menu
item.save_slot.slotPreviewEnabled=true
item.save_slot.slotPreviewPlaceholderAsset=assets/ui/empty_slot.png
item.save_slot.slotPreviewFrameAsset=assets/ui/slot_frame.png
item.save_slot.slotPreviewX=10.0
item.save_slot.slotPreviewY=4.0
item.save_slot.slotPreviewWidth=120.0
item.save_slot.slotPreviewHeight=68.0
```

### Runtime Checklist

- [ ] Title says "Save Journey"
- [ ] "Create New Save" appears at the top with a different style
- [ ] Save slots appear below with thumbnail previews (or placeholder images)
- [ ] Slot preview frames render around each thumbnail
- [ ] Selecting a slot triggers the save action
- [ ] Empty slots show the placeholder asset
- [ ] Hints bar shows the correct key bindings

### Slot preview auto-detection

Item IDs `save_slot`, `slot`, `entry`, `new_slot`, `new_save`, and `new` automatically
enable slot preview thumbnails. For other IDs, set `slotPreviewEnabled=true` explicitly.

---

## Recipe 8: Menu Screen — Settings with Dynamic Values

The settings screen uses `{value}` placeholders that the runtime fills in.

### File: `config/menu/menus/settings.menu`

```properties
titleText=Settings
hintsText=Up/Down: Select    Left/Right: Adjust    Esc: Back
layout=submenu
defaultItemStyle=submenu
wrapSelection=true

items=text_speed,bgm_volume,sfx_volume,auto_play_delay,skip_unread,back

item.text_speed.label=Text Speed: {value}
item.bgm_volume.label=BGM Volume: {value}
item.sfx_volume.label=SFX Volume: {value}
item.auto_play_delay.label=Auto Advance Delay: {value}
item.skip_unread.label=Skip Unread Text: {value}

item.back.label=Back
item.back.style=slot
item.back.action=back
```

### Runtime Checklist

- [ ] Each setting shows its current value (not the literal `{value}` placeholder)
- [ ] Left/Right keys adjust the value and the label updates live
- [ ] Changes persist when navigating away and returning
- [ ] "Back" returns to the previous menu
- [ ] Values save correctly when quitting and reloading

---

## Recipe 9: Menu Screen — With Inheritance

A credits screen that inherits structure from a base submenu.

### File: `config/menu/menus/credits.menu`

```properties
# Inherit from extras screen
extends=extras

# Override title and hints
titleText=Credits
hintsText=Esc: Back

# Replace items entirely
items=line_engine,line_editor,line_thanks,back

item.line_engine.label=JVN Engine Team
item.line_engine.action=noop
item.line_engine.enabled=false

item.line_editor.label=Runtime, Editor, and VNS by JVN contributors
item.line_editor.action=noop
item.line_editor.enabled=false

item.line_thanks.label=Thanks for building with JVN.
item.line_thanks.action=noop
item.line_thanks.enabled=false

item.back.label=Back
item.back.action=open_menu
item.back.target=extras
```

### Runtime Checklist

- [ ] Credits screen inherits layout and style from the extras screen
- [ ] Title overrides to "Credits"
- [ ] Three informational lines appear as disabled (non-selectable) items
- [ ] "Back" navigates to the extras menu
- [ ] The layout/style from the parent screen applies unless overridden

---

## Recipe 10: Complete Menu Registry

Wiring everything together for a full game.

### File: `config/menu/registry/menu.registry`

```properties
# ── Entry point ──
defaultMenu=main

# ── All menu screens ──
menus=main,load,save,settings,extras,gallery,credits,confirm_exit

# ── All layout profiles ──
layouts=default,submenu,slots

# ── All style profiles ──
styles=default,submenu,slot,dark,pastel,fancy
```

### Runtime Checklist

- [ ] Game starts on the main menu
- [ ] Every screen listed in `menus=` is reachable through some navigation path
- [ ] No "undefined" warnings for layouts or styles in the console
- [ ] Removing a screen from `menus=` makes it unreachable (confirm intentional)
- [ ] Adding a new screen ID and creating the corresponding `.menu` file works immediately

---

## Complete Key Reference

### dialogue.layout — Layout Keys

| Key | Type | Default | Range/Notes |
|---|---|---|---|
| `textBoxX` | double | `0.0` | 0.0–1.0 (viewport fraction) |
| `textBoxY` | double | `0.75` | 0.0–1.0 |
| `textBoxWidth` | double | `1.0` | 0.05–1.0 |
| `textBoxHeight` | double | `0.25` | 0.05–1.0 |
| `textBoxPadding` | double | `20.0` | >= 0 (pixels) |
| `nameBoxXOffset` | double | `20.0` | pixels |
| `nameBoxYOffset` | double | `-40.0` | pixels (negative = above) |
| `nameBoxWidth` | double | `200.0` | >= 20 pixels |
| `nameBoxHeight` | double | `40.0` | >= 12 pixels |
| `nameTextXOffset` | double | `10.0` | pixels |
| `nameTextBaselineOffset` | double | `25.0` | pixels |
| `dialogueTextHorizontalPadding` | double | `20.0` | >= 0 pixels |
| `dialogueTextTopPadding` | double | `40.0` | pixels |
| `dialogueTextRightPadding` | double | *(same as horizontal)* | >= 0 pixels |
| `dialogueTextBottomPadding` | double | `10.0` | >= 0 pixels |
| `choiceXCenter` | double | `0.5` | 0.0–1.0 |
| `choiceYStart` | double | `-1.0` | -1 = auto-center; 0.0–1.0 = fixed |
| `choiceWidthFactor` | double | `0.6` | 0.1–1.0 |
| `choiceHeight` | double | `50.0` | >= 14 pixels |
| `choiceGap` | double | `10.0` | >= 0 pixels |
| `choiceTextXPadding` | double | `20.0` | >= 0 pixels |
| `nameBoxAutoWidth` | boolean | `false` | When `true`, name box expands to fit speaker name text; `nameBoxWidth` becomes the minimum |

### dialogue.layout — Style Keys

| Key | Type | Default | Notes |
|---|---|---|---|
| `textBoxAsset` | String | *(none)* | Path to textbox background image |
| `textBoxNarrationAsset` | String | *(none)* | Alternate JavaFX textbox image for an empty speaker display name; falls back to `textBoxAsset` |
| `textBoxColor` | String | *(none)* | Hex color `#RRGGBB` or `#RRGGBBAA` |
| `textBoxOpacity` | Double | *(none)* | 0.0–1.0 |
| `textBoxBoundsPoints` | String | *(none)* | Custom bounds polygon |
| `nameBoxAsset` | String | *(none)* | Path to name box image |
| `nameBoxColor` | String | *(none)* | Hex color |
| `nameTextColor` | String | *(none)* | Hex color |
| `nameTextFontFamily` | String | *(none)* | Font family name |
| `nameTextFontSize` | Integer | *(none)* | > 0 |
| `nameTextFontWeight` | String | *(none)* | `NORMAL` or `BOLD` |
| `nameBoxOpacity` | Double | *(none)* | 0.0–1.0 |
| `nameBoxBoundsPoints` | String | *(none)* | Custom bounds polygon |
| `dialogueTextColor` | String | *(none)* | Hex color |
| `dialogueTextFontFamily` | String | *(none)* | Font family name |
| `dialogueTextFontSize` | Integer | *(none)* | > 0 |
| `dialogueTextFontWeight` | String | *(none)* | `NORMAL` or `BOLD` |
| `dialogueTextBoundsPoints` | String | *(none)* | Custom bounds polygon |
| `choiceButtonAsset` | String | *(none)* | Normal state button image |
| `choiceButtonHoverAsset` | String | *(none)* | Hover state |
| `choiceButtonSelectedAsset` | String | *(none)* | Selected state |
| `choiceButtonDisabledAsset` | String | *(none)* | Disabled state |
| `choiceButtonBoundsPoints` | String | *(none)* | Custom bounds polygon |
| `choiceBackgroundColor` | String | *(none)* | Normal bg color |
| `choiceHoverColor` | String | *(none)* | Hover bg color |
| `choiceSelectedColor` | String | *(none)* | Selected bg color |
| `choiceDisabledColor` | String | *(none)* | Disabled bg color |
| `choiceTextColor` | String | *(none)* | Normal text color |
| `choiceHoverTextColor` | String | *(none)* | Hover text color |
| `choiceSelectedTextColor` | String | *(none)* | Selected text color |
| `choiceDisabledTextColor` | String | *(none)* | Disabled text color |
| `choiceBorderColor` | String | *(none)* | Normal border |
| `choiceHoverBorderColor` | String | *(none)* | Hover border |
| `choiceSelectedBorderColor` | String | *(none)* | Selected border |
| `choiceDisabledBorderColor` | String | *(none)* | Disabled border |
| `choiceCornerRadius` | double | `10.0` | 0–96 |
| `choiceBorderWidth` | double | `2.0` | 0–12 |
| `choiceTextBaselineOffset` | double | `5.0` | -120 to 120 |
| `choiceFontFamily` | String | *(none)* | Font family |
| `choiceFontSize` | Integer | *(none)* | > 0 |
| `choiceFontWeight` | String | *(none)* | `NORMAL` or `BOLD` |
| `characterHeightFactor` | Double | *(none)* | 0.1–3.0 |
| `characterBaselineY` | Double | *(none)* | -0.5 to 2.0 |

### dialogue.layout — Textbox Action Buttons

Inline buttons rendered inside the dialogue text box (e.g., Auto, Skip, Log, Hide).
Declared using `button.ids` and `button.<id>.<field>` keys in the same `dialogue.layout` file.

| Key pattern | Type | Notes |
|---|---|---|
| `button.ids` | CSV | Comma-separated button IDs |
| `button.<id>.label` | String | Button display text |
| `button.<id>.action` | String | Action identifier |
| `button.<id>.target` | String | (optional) Action target |
| `button.<id>.enabled` | Boolean | (optional) Default enabled state |
| `button.<id>.x` | Double | (optional) X position |
| `button.<id>.y` | Double | (optional) Y position |
| `button.<id>.width` | Double | (optional) Width |
| `button.<id>.height` | Double | (optional) Height |
| `button.<id>.asset` | String | (optional) Button image |
| `button.<id>.hoverAsset` | String | (optional) Hover image |
| `button.<id>.disabledAsset` | String | (optional) Disabled image |
| `button.<id>.boundsPoints` | String | (optional) Custom bounds polygon |

### Menu .layout Keys

| Key | Type | Default | Range/Notes |
|---|---|---|---|
| `listYStart` | double | `0.35` | >= 0 (fraction of screen height) |
| `lineHeight` | double | `40.0` | > 0 (pixels) |
| `listWidthFactor` | double | `1.0` | 0.1–1.0 (fraction of screen width) |
| `textAlign` | String | `"center"` | `left` / `center` / `right` |
| `hintsBottomMargin` | double | `20.0` | >= 0 (pixels) |
| `titleY` | Double | *(none)* | (optional) >= 0 (fraction); omit for no title |
| `subtitleGap` | double | `12.0` | >= 0; gap below subtitle |
| `listXCenter` | Double | *(none)* | (optional) 0.0–1.0; overrides textAlign-based horizontal positioning |
| `titleX` | Double | *(none)* | (optional) 0.0–1.0; centers title at this fraction of viewport |
| `maxVisibleItems` | Integer | *(none)* | (optional) > 0; limits visible items and enables scrolling |
| `titleAlign` | String | `center` | `left` / `center` / `right` |
| `hintsAlign` | String | `center` | `left` / `center` / `right` |
| `hintsX` | Double | *(none)* | (optional) 0.0–1.0 horizontal hint anchor |

*Deprecated alias:* `listWidth` remains accepted for compatibility but emits a warning. Replace it
with `listWidthFactor` before the next incompatible specification version.

### Menu .style Keys

| Key | Type | Default | Notes |
|---|---|---|---|
| `itemColor` | String | *(none)* | Normal item text color |
| `itemSelectedColor` | String | *(none)* | Selected item text color |
| `itemHoverColor` | String | *(none)* | Hover item text color |
| `itemDisabledColor` | String | *(none)* | Disabled item text color |
| `itemPrefix` | String | *(none)* | Normal state prefix text |
| `itemSelectedPrefix` | String | *(none)* | Selected state prefix |
| `itemDisabledPrefix` | String | *(none)* | Disabled state prefix |
| `itemFontFamily` | String | *(none)* | Font family name |
| `itemFontWeight` | String | *(none)* | `NORMAL` / `BOLD` / `SEMI_BOLD` |
| `itemFontSize` | Integer | *(none)* | > 0 |
| `itemShadowColor` | String | *(none)* | Shadow color (hex) |
| `itemShadowOffsetX` | Double | *(none)* | Shadow X offset (pixels) |
| `itemShadowOffsetY` | Double | *(none)* | Shadow Y offset (pixels) |
| `itemOpacity` | Double | *(none)* | 0.0–1.0 |
| `buttonAsset` | String | *(none)* | Normal button image path |
| `buttonSelectedAsset` | String | *(none)* | Selected button image |
| `buttonHoverAsset` | String | *(none)* | Hover button image |
| `buttonDisabledAsset` | String | *(none)* | Disabled button image |
| `buttonTextPaddingX` | Double | *(none)* | Horizontal text padding in buttons |
| `buttonTextPaddingY` | Double | *(none)* | Vertical text padding in buttons |
| `titleColor` | String | *(none)* | Title text color |
| `titleFontFamily` | String | *(none)* | Title font family |
| `titleFontWeight` | String | *(none)* | Title font weight |
| `titleFontSize` | Integer | *(none)* | > 0 |
| `titleShadowColor` | String | *(none)* | Title shadow color |
| `hintsColor` | String | *(none)* | Hints bar text color |
| `hintsFontFamily` | String | *(none)* | Hints font family |
| `hintsFontWeight` | String | *(none)* | `NORMAL` / `BOLD` / `SEMI_BOLD` |
| `hintsFontSize` | Integer | *(none)* | > 0 |
| `backgroundAsset` | String | *(none)* | Background image path |
| `backgroundColor` | String | *(none)* | Background solid color |
| `backgroundOpacity` | Double | *(none)* | 0.0–1.0 |

### Menu .menu Keys

**Screen-level keys:**

| Key | Type | Default | Notes |
|---|---|---|---|
| `extends` | String | *(none)* | (optional) Parent menu ID to inherit from |
| `titleText` | String | *(none)* | Screen title |
| `subtitleText` | String | *(none)* | Optional screen subtitle |
| `hintsText` | String | *(none)* | Hints/keybind text at bottom |
| `layout` | String | `"default"` | Layout ID to use |
| `defaultItemStyle` | String | `"default"` | Default style for items |
| `wrapSelection` | Boolean | `true` | Wrap cursor at list boundaries |
| `backgroundAsset` | String | *(none)* | (optional) Per-screen background image; overrides the style-level background |
| `items` | CSV | *(auto-discover)* | Comma-separated item IDs |

*Deprecated alias:* `layoutId` remains accepted for compatibility but emits a warning. Use `layout`.

**Per-item keys** (prefixed with `item.<id>.`):

| Field | Type | Notes |
|---|---|---|
| `label` | String | Display text |
| `style` | String | (optional) Style override |
| `icon` | String | (optional) Icon asset path |
| `enabled` | Boolean | (optional) `true`/`false`/`yes`/`no`/`1`/`0` |
| `action` | String | Action type (see action table below) |
| `target` | String | Target for `open_menu`/`run_script` |
| `bgAsset` | String | (optional) Button background image |
| `bgSelectedAsset` | String | (optional) Selected button image |
| `bgDisabledAsset` | String | (optional) Disabled button image |
| `boundsX` | Double | (optional) All 4 bounds required together |
| `boundsY` | Double | (optional) |
| `boundsWidth` | Double | (optional) |
| `boundsHeight` | Double | (optional) |
| `fontFamily` | String | (optional) Per-item font family override |
| `fontWeight` | String | (optional) Per-item font weight override (`NORMAL` / `BOLD` / `SEMI_BOLD`) |
| `fontSize` | Integer | (optional) Per-item font size override (> 0) |
| `slotPreviewEnabled` | Boolean | (optional) Enable save slot thumbnail |
| `slotPreviewPlaceholderAsset` | String | (optional) Empty slot placeholder |
| `slotPreviewFrameAsset` | String | (optional) Thumbnail frame image |
| `slotPreviewX` | Double | (optional) Thumbnail X position |
| `slotPreviewY` | Double | (optional) Thumbnail Y position |
| `slotPreviewWidth` | Double | (optional) Thumbnail width |
| `slotPreviewHeight` | Double | (optional) Thumbnail height |

Any `item.<id>.<field>` key that isn't in the table above is preserved as a custom "extra"
and accessible from runtime code.

**Action types:**

| Value | Aliases | Target? | Effect |
|---|---|---|---|
| `new_game` | `new`, `start`, `start_game` | No | Start new game |
| `load_menu` | `load`, `continue` | No | Open load screen |
| `save_menu` | `save` | No | Open save screen |
| `settings_menu` | `settings`, `options` | No | Open settings |
| `main_menu` | `main`, `title`, `title_menu` | No | Return to main menu |
| `open_menu` | `submenu`, `menu` | **Yes** | Navigate to a menu by ID |
| `run_script` | `script`, `start_script`, `play_script` | **Yes** | Run a VNS script |
| `back` | `return` | No | Go back |
| `quit` | `exit` | No | Quit game |
| `noop` | `none`, `no_op` | No | No action (decorative) |

### menu.registry Keys

| Key | Type | Notes |
|---|---|---|
| `defaultMenu` | String | Menu screen to show first (alias: `defaultScreen`) |
| `menus` | CSV | Comma-separated menu screen IDs |
| `layouts` | CSV | Comma-separated layout IDs |
| `styles` | CSV | Comma-separated style IDs |

---

## Troubleshooting

### "Value was out of range" in console

The engine clamps values to valid ranges and logs a diagnostic. Check:
- `listWidthFactor` is between 0.1 and 1.0
- `textBoxX + textBoxWidth` doesn't exceed 1.0
- `choiceHeight` is at least 14
- `lineHeight` is greater than 0
- Opacity values are 0.0–1.0

### "Unknown key" warnings

You have a typo. Common ones:
- `fontFamily` → should be `itemFontFamily` (in `.style` files)
- `color` → should be `itemColor`
- `textbox` → should be `textBox` (camelCase)
- `fontSize` → should be `itemFontSize` or `dialogueTextFontSize` depending on context

### "Circular menu inheritance detected"

Menu A extends B, and B extends A (directly or indirectly). Break the cycle by removing
one `extends=` reference.

### "extends missing menu"

The parent menu ID in `extends=` doesn't match any known menu file. Check:
- The parent `.menu` file exists
- The parent ID is listed in `menu.registry` or discoverable by the engine
- The spelling matches exactly (case-sensitive)

### "Partial bounds" warning

You set some but not all four bounds fields (X, Y, Width, Height). Either set all four
or none. The engine drops partial bounds entirely.

### Items not appearing

1. Check `items=` CSV lists the correct IDs
2. If relying on auto-discovery, ensure `item.<id>.label` exists for each item
3. Watch for duplicate item IDs — only the first is kept

### Menu screen not reachable

1. Verify the `.menu` file is in `config/menu/menus/` or `config/menu/`
2. Verify the ID is in `menu.registry` `menus=` list (or auto-discoverable)
3. Verify some other menu has an `open_menu` action targeting it

### Assets not loading

1. Verify asset paths are relative to project root
2. Check file extensions match exactly (`.png` vs `.PNG`)
3. Confirm the file exists on disk
4. Check console for specific asset loading errors

---

## Best Practices

1. **Start from defaults.** Don't copy every key — start minimal and add keys only as needed.

2. **Use inheritance for menu variants.** If your extras and credits screens share layout
   and style, have credits `extends=extras` and override only what differs.

3. **Name styles semantically.** `dark.style`, `pastel.style`, `slot.style` — not `style1.style`.

4. **Keep one style per file.** Don't try to combine unrelated styles. The registry can
   load as many style files as you need.

5. **Test at target resolution.** Pixel values (`lineHeight`, `textBoxPadding`) don't scale
   with viewport. Fractional values (`listYStart`, `textBoxY`) do.

6. **Use the registry for explicit control.** Auto-discovery is convenient but the registry
   makes your project's menu structure self-documenting.

7. **Comment your intent.** Use `#` comments to explain non-obvious values:
   ```properties
   # Extra padding to avoid text overlapping the 9-slice border
   dialogueTextHorizontalPadding=35.0
   ```

8. **Validate after every change.** The golden loop is: edit one thing, run, check, repeat.

9. **Read diagnostics.** The engine's console output tells you exactly what went wrong.
   Don't ignore warnings — they indicate real issues.

10. **Version control layout files.** They're plain text, diff cleanly, and merge easily.
    Review layout changes in PRs just like code changes.

---

## FAQ

**Q: Can I split dialogue layout and style into separate files?**
A: No. The `dialogue.layout` file contains both layout and style keys in a single file.
This is by design — the dialogue UI is a unified component.

**Q: What file format are these files?**
A: Standard Java `.properties` format. `key=value` pairs, one per line. Lines starting
with `#` or `!` are comments. Use `\` for line continuation.

**Q: Are keys case-sensitive?**
A: Yes. `textBoxY` and `TextBoxY` are different keys. Always use the exact camelCase
form documented in this cookbook.

**Q: Can I use RGB instead of hex colors?**
A: No. Colors must be in hex format: `#RRGGBB` or `#RRGGBBAA`.

**Q: What happens if I set contradictory values?**
A: The engine applies validation rules and clamps values. For example, if
`textBoxX + textBoxWidth > 1.0`, the width is reduced to fit. Diagnostics are logged.

**Q: Can I use a menu layout for dialogue, or vice versa?**
A: No. Menu layouts (`.layout` in `config/menu/layouts/`) and dialogue layouts
(`config/ui/dialogue.layout`) are different DSLs with different keys. They control
different runtime systems.

**Q: Do I need to restart the editor to pick up layout changes?**
A: You need to re-run the project (runtime). The editor's Layout Studio re-reads files when
you open it, but the authoritative test is always the runtime.

**Q: How do I create a custom action that isn't in the built-in list?**
A: Set the action to any string value. The engine will treat unrecognized actions as `noop`
by default, but your custom runtime code can read the raw `actionKey` from `MenuActionSpec`
and handle it however you want. The action string is preserved non-destructively.

**Q: Can two menu screens share the same layout?**
A: Yes. Multiple `.menu` files can reference the same `layout=default`. The layout is a
reusable positioning profile, not tied to a specific screen.

**Q: What's the difference between `defaultItemStyle` and per-item `style`?**
A: `defaultItemStyle` sets the fallback style for all items on the screen. Per-item
`item.<id>.style=other` overrides it for that specific item only.

**Q: How do I make a menu item that runs a VNS script?**
A: Use `run_script` action with a target:
```properties
item.prologue.label=Play Prologue
item.prologue.action=run_script
item.prologue.target=scripts/prologue.vns
```

**Q: Is there a maximum number of menu items?**
A: No hard limit. The engine renders as many items as you define. Practical limits depend
on your `lineHeight` and screen resolution — too many items may scroll off-screen.

**Q: Can I hot-reload layout changes without restarting the runtime?**
A: Not currently. Layout files are loaded at startup. Save your file, stop the runtime,
and re-run. The iteration cycle is fast enough that this is rarely a bottleneck.
