# 2. Screens and transitions

All sizes, shapes, colors, fonts and motion below are defaults; each has a setting (doc 3). Portrait phone layout.

## 2.1 Home

**Layers, back to front:** wallpaper (drawn by the launcher) → optional dim/blur → page content → dock → overlays (folder, search, drawer, panels).

**Default first page**, modelled on your current setup:

```
┌──────────────────────────┐
│ status bar               │
│                          │
│       1 4 : 0 3          │  Big Digital clock widget
│                          │
│   (empty space)          │
│                          │
│    ▢  ▢  ▢  ▢  ▢         │  icons, no labels,
│    ▢  ▢  ▢  ▢  ▢         │  free placement on a 7 × 6 grid
│    ▢  ▢  ▢  ▢  ▢         │
│                          │
│  ( 🔍            12 Nov ) │  liquid-glass search pill
└──────────────────────────┘
```

- **Grid**: rows × columns per page, 1–12 each (limit can be raised). Items snap to cells; optional half-cell precision. Items never move on their own unless "push items aside" is on.
- **Pages**: horizontal pager with configurable transition (slide, depth, cube, fade, stack) and indicator (dots, line, numbers, none). Infinite scroll optional. Each page can override grid, margins, icon size and label settings.
- **Items**: app, shortcut, folder, built-in widget, AppWidget, launcher action.
- **Dock**: optional, own grid (default off because your layout has none), pages optional, glass plate optional. The search pill is a widget and can sit in the dock, on the grid, or float.
- **Icon interactions**: tap = launch; long-press = popup; long-press and drag = move (enters edit mode); swipe up / swipe down / double-tap = per-icon action.
- **Empty-space gestures**: swipe up = drawer, swipe down = search, long-press = edit mode, double-tap = lock (suggested default), pinch-in = page overview, two-finger swipe down = notifications. All reassignable.
- **Return to home**: when the redirect brings the launcher forward, icons and widgets settle in (scale 0.94 → 1 with fade, ~180 ms, slightly staggered from the center) to mask the hand-over from HONOR Home.
- **Health banner**: a small glass chip appears only if the accessibility service is disconnected, with one tap to fix.

### App popup (long-press an icon)

A glass menu anchored to the icon:
- shortcut rows (static shortcuts with exported targets, legacy shortcuts)
- the app's current notifications, swipe to dismiss, tap to open
- icon row: App info · Edit (label, icon, tags, swipe actions) · Hide · Uninstall · Add to… (page, folder, drawer tab)
- drag any shortcut row out to place it on the home screen

## 2.2 Edit mode

Entered by long-press on empty space, by dragging an item, or by gesture.

- The page scales to ~88 % and shows grid dots; neighbouring pages peek at the sides.
- **Bottom glass toolbar**: Add (apps / widgets / folder / action) · Wallpaper · Page settings · Layout (grid, margins) · Undo · Redo · Done.
- **Selected item**: resize handles (snap to cells), a small action bar: Edit · Style · Duplicate · Remove.
- **Multi-select**: tap more items after selecting one; move together, "make folder", remove.
- **Page strip** (pinch-in or toolbar): reorder, add, delete, duplicate, set default page, per-page settings.
- **Undo/redo**: every layout operation is a command on a stack (50 steps, kept until you leave edit mode; the last state is also restorable from Settings → Backup → "Layout history").
- Dragging to a screen edge switches page; dragging onto an app creates a folder; dragging to the top shows Remove / Info / Uninstall targets.

### Widget picker

Bottom sheet with two tabs: **Built-in** (grouped: Clocks, Date, Study, Search, Apps, System, Utility) and **Apps** (AppWidget providers grouped per app, including work-profile providers). Search field on top. Each entry shows a live preview and its default size; drag to place or tap to drop into the first free space.

### Icon picker

Tabs for each installed icon pack plus "Image" and "App's own". Search inside the pack by name. Options: apply shape mask, scale, color tint, and "use for all apps without a pack icon".

## 2.3 App drawer

Opened by swipe up from home. The gesture is finger-driven: the drawer sheet follows the finger and settles by velocity. Home content scales down and fades slightly as it rises; wallpaper dim/blur ramps with progress.

```
┌──────────────────────────┐
│ All  Study  Media  Tools │  tabs (yours), optional, swipe between
│ #math #video #tools …    │  tag chips (filter), optional
│                          │
│  ▢   ▢   ▢   ▢   ▢      │
│  ▢   ▢   ▢   ▢   ▢      │  grid or list, scrolls
│  ▢   ▢   ▢   ▢   ▢      │
│                          │
│   ( 🔍 Search apps    ⋮ ) │  floating liquid-glass pill
└──────────────────────────┘
```

- **Fill direction**: from the top, or from the bottom so the first apps sit next to your thumb (the list is reversed and anchored to the bottom).
- **Search pill**: floats above the content and rides the keyboard. Option: focus and open the keyboard when the drawer opens. Typing filters in place; Enter launches the first result. The pill's ⋮ opens sort, view options and drawer settings.
- **Sorting**: manual (drag in drawer edit mode), pinned first, most used, recently installed, alphabetical, by tag (sections with headers).
- **Tabs/pages**: created, named, reordered and filled by you. A tab is either a manual list or a rule (tags, work profile, "not in any tab"). The **Work** tab appears automatically when a work profile exists; it can be placed anywhere, merged into All with a badge, or hidden. When the profile is paused the tab shows a card with a button that opens Island.
- **Fast scroller**: alphabet rail or scrub bubble, optional.
- **Sections**: optional "Suggested" row on top and "Recently installed".
- **Close**: swipe down on the grid when scrolled to its start, back gesture, or home.
- Long-press works as on home, plus "Add to home".
- Performance: one lazy grid, icons pre-rendered to bitmaps at the exact pixel size and cached on disk, no glass per item, stable keys, no recomposition while scrolling.

## 2.4 Universal search

Opened by swipe down on home, by tapping the search pill, or by gesture. The home content blurs and dims, the pill morphs into the search field (shared-element shape morph), and the keyboard opens.

```
┌──────────────────────────┐
│ ( 🔍 yt lofi|          ✕ ) │
│                          │
│ ▢ ▢ ▢ ▢ ▢                │  top apps row
│ ─ YouTube: search "lofi" │  bang result
│ ─ Contact · Tasks · Event│  grouped sections
│ ─ Settings · Files       │
│ ─ = 42 (calculator)      │
│ ─ Search the web for …   │
└──────────────────────────┘
```

- **Empty query**: suggested apps for this time of day, recent searches, current study block with its tasks.
- **Providers**: apps, tags (`#study` lists a tag), folders, contacts, settings pages, calendar events, my tasks (tap to complete, long-press to open), files, calculator, unit conversion (`12 km in mi`; no currencies, they need network), web search with chosen engine, bangs (`!yt`, `!w`, `!g`, custom), launcher actions (`lock`, `edit home`, `study mode on`), quick task add (`+ read chapter 3`).
- **Matching**: case- and accent-insensitive; prefix > word-start > initials (`yt` → YouTube) > subsequence > one or two typos. Score is multiplied by a usage weight that depends on time of day and on what you picked for this exact query before.
- **Never blocking**: each provider runs on its own coroutine with a time budget; results stream into fixed sections so rows never jump under your finger; the first app result is ready before the keyboard finishes opening.
- **Keys**: Enter launches the top result. Swipe down or back closes. Result order within a section and section order are configurable.
- Result layout can be top (under the field) or bottom-anchored with the field above the keyboard.

## 2.5 Panels

A panel is a full-screen or partial sheet with its own content, opened by a gesture or action.

- **Study panel** (default: swipe right from the first page, i.e. the page to the left of home): Now card, Today list, Week strip. Details in doc 5.
- **Custom panels**: you create a panel, give it a small grid, and place any widgets on it (for example a media + notes panel). Panels can open from the left, right, top or bottom, as a sheet over home with glass background.
- **Quick panel** (optional): launcher actions as tiles (study mode, profile switch, wallpaper dim, redirect on/off, open HONOR Home).

## 2.6 Folders

- **Closed**: an icon-sized or larger item (any cell span). Preview style: grid (2×2, 3×3), circle/fan, stack, or first-icon-only. Background: none, tint, frosted, liquid glass. Shape: circle, squircle, rounded, cut corner, custom radius. Optional badge and label.
- **Open**: expands from the icon bounds with a shape morph into a glass card. Own grid (rows × columns), pages if needed, title editable in place, background blur behind. Opening style: expand (default), popup, full-screen, or bottom sheet.
- **Behaviour option**: "tap opens first app, swipe up opens folder" (or the reverse).
- **Large folder** mode: the folder stays expanded on the home screen as a widget-like grid; tapping an icon launches directly.
- Drag in/out works in both states. Folders may contain apps, shortcuts and actions. Folders can be tagged so study mode can hide them.

## 2.7 Mindful friction card

When you launch an app tagged "distracting" during a study block:

- a glass card slides up over a blurred home: current block, time left, current task with a "done" checkbox
- buttons: **Back to study** (primary) and **Continue anyway** (enabled after a configurable countdown, default 5 s)
- optional: ask for a reason (one tap chips), optional per-day counter shown on the card
- every pass-through is counted in the block statistics

## 2.8 Onboarding (first run)

Five short steps, each skippable, each with a one-tap deep link and a live check mark:
1. Allow restricted settings and enable the accessibility service.
2. HONOR power settings: App launch → manage manually; Battery → unrestricted.
3. Pick wallpaper (launcher-managed) and empty HONOR Home.
4. Import: pick icon pack, grid size, optionally start from the "Current setup" preset (big clock, 5 × 3 icon block, search pill).
5. Optional permissions, each explained: notifications access, calendar, contacts.

## 2.9 Transitions and motion

Motion uses Material 3 Expressive spring tokens: *spatial* springs (position, size, shape; may overshoot) and *effects* springs (alpha, color, blur; never overshoot), each in fast / default / slow. One global speed multiplier, plus per-element style and speed.

| Transition | Motion |
|---|---|
| Home → drawer | Finger-driven sheet; home scales to 0.96 and fades; blur ramps. Release settles by velocity. |
| Home → search | Search pill morphs to the field; results fade up in a 20 ms stagger; wallpaper blur ramps. |
| Icon → app | Reveal from the icon bounds (clip reveal or scale-up), chosen per setting. |
| App → home | System animation into HONOR Home's empty wallpaper, then the launcher content settles in. |
| Folder open/close | Container transform from the icon bounds with shape morph; other icons recede slightly. |
| Enter edit mode | Page scales to 0.88, grid dots fade in, toolbar slides up. |
| Page change | Chosen pager effect; indicator morphs. |
| Press feedback | Icon squish (scale 0.92 with spring back), optional glass highlight and haptic tick. |
| Profile switch | Cross-fade with a short shared-bounds move for items that exist in both layouts. |

Reduced-motion and "animations off" presets exist for battery or taste.
