# Design: the Liquid look

Contents: 1 Layers and the background · 2 Liquid glass · 3 Colour · 4 Shapes, sizes, type · 5 The settings screens
(WM Keyboard style) · 6 Layout habits · 7 Med's taste, as he said it

## 1. Layers and the background

Back to front: background (the app draws it) → optional dim / blur of it → content → floating controls (pills, dock,
chips) → overlays (sheets, menus, notices).

- **Draw your own background.** Glass can only bend what the app itself draws, and since Android 13 apps cannot read
  the system wallpaper. Liquid Launcher keeps its own copy of the wallpaper (a library of pictures with a crop and
  parallax each) and hands it to the system after the fact. A new app should draw its own background (a picture,
  a gradient, a moving board) inside the layer the glass samples.
- **Blur and dim are per surface** (home, drawer, search each have their own), previewed live.
- **Blur a copy, not the screen.** The blurred background is computed once off the main thread (a quarter-size box
  blur, cached) and faded in over the sharp one. A live `RenderEffect` blur whose radius changes every frame was
  the single most expensive effect in the launcher (D78).

## 2. Liquid glass

Code: `assets/kit/src/main/kotlin/liquid/kit/glass/` (`Glass.kt`, `LiquidShader.kt`). Library: Kyant0's
AndroidLiquidGlass, `io.github.kyant0:backdrop` (Apache-2.0).

How it works: the content behind is recorded into a layer (`Modifier.layerBackdrop(rememberLayerBackdrop())` on the
background), and each glass surface draws that layer through effects (`Modifier.liquidGlass(backdrop, shape,
style)`): saturation/brightness, blur, then refraction. "Full" refraction is an AGSL shader of our own: a slab with
a rounded edge (`bevel` wide, `thickness` x bevel high), rays bent by Snell's law where the edge slopes, red/green/
blue bent slightly differently (dispersion), an optional swell in the middle, a Fresnel-weighted specular rim on the
edge facing the light, shading on the far edge, and a fine grain. The library's `lens` is the fallback.

**Tiers** (`GlassQuality`): `Full` (refraction + blur + rim; needs RuntimeShader, Android 13), `Frosted` (blur +
tint, no refraction), `Tint` (a translucent fill, no shader). The user chooses; a benchmark can cap only the large
sheets to Frosted when Full is too heavy (small glass is cheap; one diagnostics run once flattened everything, D72).

**Default style** (`GlassStyle`): blur 4 dp, refraction 0.6, tint white at 18 %, saturation 1.5 (the old
"vibrancy"), bevel 16 dp, thickness 2 (0.5 thin to 3.5 slab), dispersion 0.35 when on, swell 0.4, specular 0.6,
shade 0.4, grain 0.1, shadow 1, light from 45° (top left). "Thick glass" and "rainbow edges" on; inner glow 20 %;
**edge light (rim) off on surfaces, on for buttons** (Med asked for the rimless surfaces back, D101).

**Rules**
- A few glass surfaces at a time; **never one per item in a scrolling list** (D8). Scrolling content inside a glass
  sheet draws its buttons "without backdrop" (`WithoutBackdrop`, a drawn "crystal" look).
- A glass surface must not be inside the content it samples (it would sample itself).
- Glass on glass: a sheet can export its glass (`exportedBackdrop = layer`) so buttons on it bend the sheet, not the
  wallpaper under it. This is how real liquid glass controls look, and it costs a second sampling pass, so use it
  for the few buttons of a sheet, not its list.
- Move glass by layout, not by scaling a layer: a scaled layer shows a stale copy of what is behind (D40, D65). To
  grow glass out of a button, draw it at the growing size each frame.
- Every look parameter is a setting with a live preview (Liquid Launcher's "Tune the glass" shows each value over
  the wallpaper, with presets). `GlassTest` variants let a speed check measure what each part costs.

## 3. Colour

- **From the wallpaper** (Material You / `WallpaperColors`): accent, tones for icons and text, a "glass tint" role.
  When the app sets a picture it extracts the colours itself at once (`WallpaperColors.fromBitmap`) instead of
  waiting for the system's callback, which can come late or never on MagicOS (D56). Before the system reports, a
  Material You tonal-spot guess from the same seed (CAM16/HCT, `MonetTones`, ported from material-color-utilities,
  Apache-2.0) avoids wrong colours for a while (D153).
- **Readable text, always**: a text colour that follows the wallpaper (or is "Auto") is checked against what it sits
  on (its fill over the picture's average colour) and becomes a dark or light ink below 3:1 contrast. A colour the
  user fixed is never changed (D145).
- **Changes ease**: palette changes fade (260 ms for icons, 600 ms for data-driven colours) except when a change is
  part of a transition (then set in the same frame as the new picture, D63).
- **Coloured while drawing, not re-rendered**: themed icons keep a mask and are tinted when drawn, so a new palette
  costs a redraw, not a re-render of every icon (D63, D75).
- **Theme files**: Liquid Launcher's theme *is* a WM Keyboard theme document (JSON, every field round-tripping, its
  own additions under one key), so themes import and export between the two apps; every theme option is declared once
  (group, meaning, range, condition) and the theme editor ("Theme lab") is generated from that list (D139).

## 4. Shapes, sizes, type

- Pills and capsules: `RoundedCornerShape(50)`. Sheets and big cards: 24 dp. Chips and small cards: 12 dp. Icon
  tiles: 40 dp with 13 dp corners. Grouped cards: 24 dp at the ends of a group, 6 dp between cards, 3 dp apart.
- Touch targets at least 44-48 dp; a primary action under the thumb.
- Fonts: system families every phone has, plus one imported TTF/OTF; one place (a theme object read as snapshot
  state) decides the font so a change redraws without a restart (D51).
- Icons: vector drawables, tinted at draw time, one `LauncherIcon(icon, contentDescription, tint, size)` helper.

## 5. The settings screens (WM Keyboard's house style)

Code: `assets/kit/src/main/kotlin/liquid/kit/ui/SettingsKit.kt` (from WM Keyboard's SettingsUi.kt / SettingsMotion.kt,
MIT, (c) 2026 Wasi Master; keep the credit). Med asked for exactly this and liked it (D191, D193).

- **A hue per place**: each top-level entry has its own colour (blue 0xFF42A5F5, indigo 0xFF5C6BC0, green
  0xFF66BB6A, pink 0xFFEC407A, cyan 0xFF00ACC1, purple 0xFF7E57C2, deep orange 0xFFFF7043; neighbours never share
  one), worn by its 40 dp icon tile (24 % wash in light mode, 15 % dark) and faintly by the heading (7 % / 5 %) and
  the bar once scrolled (18 % / 13 %).
- **Headings**: the page name large (32 sp, down to 19 sp for long names, semi-bold, tight) beside its tile, under
  breadcrumbs shown as chips that go back to that level (the current place wears its hue); the name moves into the
  top bar as the page scrolls (fold over 96 dp).
- **Rows on cards in groups** (`KitGroup`): 24 dp corners at the group's ends, 6 dp where two cards meet, 3 dp gaps,
  empty rows skipped; a lone row is a full card (`kitLoneCard`). A row: icon tile, title (medium), one-line summary,
  value on the right, chevron; a "?" button explains an option in a sentence.
- **Controls**: segmented tabs (`KitSegments`) for a page's tabs, chips (`KitChip`, `KitChips` for up to six short
  choices), a thick expressive slider (`KitSlider` / `KitSlide`), fold rows for "Advanced" (`KitFoldRow`).
- **Motion**: page push (see motion.md), the row's icon tile flies into the next page's heading (shared element,
  `kitFlight(key)`), rows staggered in (`kitStaggerIn(index)`).
- Colours: Material You from the wallpaper (Med chose "wallpaper colours" over WM Keyboard's fixed palette).

## 6. Layout habits

- **Thumb reach**: the most used things low; lists can start from the bottom; results grow upwards from the field
  with the best one nearest the thumb; search's field sits just above the keyboard.
- **One space**: drawer below the home screen, search above it; opening one is the home screen moving away under the
  finger, not a new screen appearing (D80, D99, D104).
- **Popups grow out of what was pressed** and shrink back into it (D116, D176).
- **No glass box behind widgets or the clock** by default (Med: "I didn't like it", D134); text sits on the
  background in a background-aware colour, with the box as an option.
- **A health banner only when something is wrong** (e.g. a needed service is off), one tap to fix.
- **Notices**: one glass label at a time for things that happened (layout switched, update ready), each kind behind
  its own switch (D81).

## 7. Med's taste, as he said it

- Likes: WM Keyboard (its settings design and animations, its themes), Material You colours, "liquid glass on
  everything, the buttons first", squishy presses, Ripple wallpaper transitions, iOS-smooth motion, haptics, things
  that grow out of what he pressed, being asked before big changes (with a recommended choice), being told where
  settings moved.
- Dislikes: glass boxes behind widgets, the pill flying too high, animations that start after the screen already
  changed ("I see my phone open, then after a bit the animation"), anything that lags, slow animations ("I want it
  instant"), placeholder features, losing data or settings.
