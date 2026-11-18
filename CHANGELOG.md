# Changelog

What changed in each version of Liquid Launcher, newest first, in four parts: **Added**, **Changed**,
**Removed** (or replaced) and **Fixed**. The GitHub release of a version shows its part of this file, the
Updates screen shows it before you install, and the launcher shows it once after an update.

Versions marked *not published on its own* were built but came to the phone inside the next release.

## 0.10.1 — 2026-11-18

### Added
- The Theme lab (the first row of Settings): every option of a WM Keyboard theme, in nine groups (Glass,
  Material, Textures, Colours, Shape and size, Press effect, Moving background, Background image, Extras) and
  Pills. A large live preview shows a sample, the home screen, the drawer or search, and every change is applied at
  once. Undo and redo, reset per group, and a long press on an option to reset it, explain it or copy its id.
  Options that only matter with another one are greyed, with the reason.
- Presets in the lab: your keyboard backup, the look you had before this version, the default. Import and export
  of `.wmtheme.json` files, and "Export my tuning" (only what you changed).
- Any colour of the theme can follow the wallpaper: its dominant, vibrant, muted, dark or light colour.
- The three pills (home, drawer, search) share one look, or each has its own; "Copy from" copies one to another.
- The theme reaches every surface: sheets, menus, cards, fields, checkboxes, the pills, the hub. Buttons take the
  keyboard's material (caps, metal, wood, leather, paper, neon…) with its edge and label effect, textures, the 11
  shapes, sizes, gaps, fonts, the press motion, the light under the finger and particle bursts. The moving
  background moves over the home wallpaper; the background picture and stickers sit behind sheets; the blur
  behind sheets blurs the home screen while one is open.
- A frame meter: while the home screen keeps drawing (a moving background), the debug log says every ten seconds
  how many frames were late and what was on screen, so the cost of an effect is known on the phone itself.
- "Customise …" on the home screen: a long press on the dock, an open folder, the search page or the hub (and on
  the pill, while its long press is left on Settings) shows one glass chip that opens that surface's settings page.
- Every option can be held for Reset, Explain (what it does, where it lives, its default, range, what it needs,
  whether layouts keep it, its id) or Copy id.
- Backup and Claude (Settings → System): "Back up now", with or without wallpapers. A backup every day into a
  folder you choose (on the phone or a cloud drive), keeping the newest seven copies. Restoring first shows what
  the backup holds and every option it would change (now → then).
- Tune with Claude: "Send to Claude" shares every option (what it does, its range, default and value now) with your
  question and the rules for answering (only a JSON list of changes) to the Claude app; "Export for AI" saves the
  same as a Markdown file. Paste Claude's answer back: every change is listed before anything happens, entries that
  are not valid (an unknown option, a wrong kind of value, out of range) are shown in red and left out, and "Undo"
  puts everything back in one tap. The launcher makes no network call for this.
- Picking many apps in the drawer: hold an app and choose Select, then tap the others. A bar offers Tag, Untag,
  Hide and Add to home for all of them at once.
- A tag's own list: in the drawer's tag list, the apps button beside a tag opens every app with a check, and a
  search, to tag or untag many apps in one place.

### Changed
- Settings in seven entries: Home, Drawer and search, Study, Look, Wallpaper and layouts, Motion and feel, System.
  Each holds pages, one per surface (Pill, Dock, App drawer, Letter index, Liquid glass…); a page keeps its live
  preview at the top and shows its options in tabs: Layout, Look, Behaviour, More. A dot marks what you changed,
  the tabs count it, and "Reset" resets one tab.
- Search shows where each option lives (Home › Pill › Behaviour) and opens its page on its tab with the option lit
  up; "Recently changed" does the same. Search also matches page and entry names.
- An option that only matters when another one is on stays in its place, greyed, and says why ("Only when
  “Haptic feedback” is on") instead of disappearing.
- Merged pages: Drawer motion into App drawer (Behaviour), Hand-over into Redirect, Detection with Exclusions and
  safety, Home menu with Long-press menu (Menus). Effects and touch is split into Press and touch and Moving
  wallpaper. Renamed: Theme → Colours and fonts, Scroll index → Letter index, Saved layouts and backup → Layouts
  and backup, which is now Layouts (backup moved to System → Backup and Claude). Setup checklist, Diagnostics,
  Calibration, Debug log, Updates and About are in System; the Theme lab is in Look. No option was removed and none was reset: their stored names are unchanged, and links to the old
  sections open the page that has their options.
- The accent is a fixed blue (Blueberry, #5484ED) unless you had chosen your own colour. The main button keeps
  its block colour.
- The search bar and the other pills are dark by default when a theme does not say otherwise.
- A theme's moving background and press splash take the place of the launcher's own while the theme has one.

- Backups now include the theme's pictures (its background, textures and stickers).

### Removed
- The theme editor of 0.9.14 (Settings → Buttons → Edit the theme): the Theme lab replaces it, with every option
  it had.
- Keyboard options that mean nothing in a launcher are kept in the theme file but not shown: the suggestion bar,
  toolbar, one-handed panel and navigation bar colours, landscape and animated backgrounds, popup placement,
  gesture trail width, sounds, key lighting, per-key overrides, and the "meld" splash (letters merging between
  keys).

## 0.10.0 — 2026-11-17

### Added
- This changelog. The Updates screen shows what a new version brings before you install it, and lists every
  version under "All changes". After an update, a "What's new" card appears once above the pill ("Read all" shows
  everything, "Got it" hides it).

### Changed
- Sheets can no longer run off the screen: at most 85 % of the space above the keyboard, the title and the
  buttons stay in place and the middle scrolls. The keyboard lifts the whole sheet, so the field you type in stays
  in sight.
- An app's Tags (from its long-press menu) open as a sheet: the list of tags scrolls, and the "New tag" field stays
  at the bottom with a + button, however many tags you have. With more than eight tags, a search narrows the list.
- The drawer's tag list uses the whole height left and ends above the drawer's pill, so Clear and Done are always
  visible.
- The layouts sheet and a widget's Customise sheet use the same sheet.
- Long menus (an app's long press, a tab's menu) and big folders scroll inside their card.
- Lists in dialogs (choices in Settings, colour pickers, list editors) scroll.
- A new version is published only when it is meant to be: two releases at most per working session, each with
  its changelog entry. Builds in between are only tested.

### Fixed
- With nine tags or more, the "New tag" field of an app's Tags could not be reached.
- The drawer's tag list hid its Clear and Done buttons behind the drawer's pill.
- 0.9.22 and 0.9.23 were never published: their build was fine but the emulator test failed on a Home-button check
  that ran while the accessibility service was reconnecting, and on a clock-line check that did not expect the
  evening check-in after 22:30. Both checks were wrong, not the launcher; they arrive with this version.

## 0.9.23 — 2026-11-17

*Not published on its own: it came in 0.10.0.*

### Added
- The calendar widget has its own "Customise" sheet in edit mode: a glass card or none, corner size,
  colour (text or accent), the month grid or a compact week, and the next events under it. It changes as
  you choose.

## 0.9.22 — 2026-11-17

*Not published on its own: it came in 0.10.0.*

### Added
- The date and battery widgets have a "Customise" sheet in edit mode, like the clock: size, weight,
  colour, font, and which parts show (weekday, month, year; ring, percentage, "charging").

## 0.9.21 — 2026-11-17

### Added
- Layouts for a while: hold a layout in the layouts sheet and switch to it for 30 minutes, 1 hour or
  2 hours. When the time is up the previous layout comes back, unless you changed layout by hand. "Keep it"
  stays on the new one.

## 0.9.20 — 2026-11-17

*Not published on its own: it came in 0.9.21.*

### Changed
- Every animation follows the motion speed setting (26 of them used a fixed speed before).
- Every vibration follows the haptics switch and strength (the letter index and the tag strip had their own).

## 0.9.19 — 2026-11-17

*Not published on its own: it came in 0.9.21.*

### Added
- Past papers for exam week: choose a folder with one sub-folder per module. In the week before an exam
  the search page shows a "Past papers" card, the next exam's module first; a tap opens a paper in your PDF
  app.

## 0.9.18 — 2026-11-17

### Added
- Battery saving on the trip home: under 30 % and not charging, the glass is frosted, other apps' widgets
  rest and the moving wallpaper stops, until you are home or charging.

## 0.9.17 — 2026-11-17

*Not published on its own: it came in 0.9.18.*

### Added
- Other apps' widgets take the launcher's look: their solid backgrounds give way to the wallpaper or the
  glass, and their text takes the launcher's colour, after every update they send. A switch for all of
  them and one per widget.

## 0.9.16 — 2026-11-17

*Not published on its own: it came in 0.9.18.*

### Added
- Quiet during study blocks (off by default): notifications wait until the block ends, then a summary says
  what came. Calls, alarms, ongoing ones, chosen people, school mail and apps tagged "important" get
  through. Nothing is lost: Android gives the held ones back at the end of the block.

## 0.9.15 — 2026-11-17

*Not published on its own: it came in 0.9.18.*

### Changed
- Switching tags in the drawer glides: one selection slides from chip to chip, and apps glide to their new
  places instead of jumping.
- Typing `!` or `?` first in the drawer's field hands over to the search page, where the study commands and
  questions to Claude run.

## 0.9.14 — 2026-11-17

### Added
- A theme editor (Settings → Buttons → Edit the theme): every colour and its opacity, border, shape,
  corners, sheen, glass, shadow and glow of buttons, the main button, chips and round buttons, the frost
  under them, the accent and the glass tint, with a live preview.

### Fixed
- A sheen on a button now covers the whole button at any size.

## 0.9.13 — 2026-11-17

*Not published on its own: it came in 0.9.14.*

### Added
- Your WM Keyboard theme styles every button, chip and round button by default. Themes can be imported
  (WM Keyboard themes and backups) and exported.

### Changed
- The theme's accent and glass colour were applied once where you had not chosen your own.
- "Tune the glass" stays still unless you press Move.

### Removed
- The launcher's own button style is no longer the default; it stays available as an option.

## 0.9.12 — 2026-11-17

*Not published on its own: it came in 0.9.14.*

### Added
- Real liquid glass: light is bent through the rounded edge of each glass surface, split into colours,
  caught on one edge and shaded on the other.
- "Tune the glass": live sliders and presets (Real glass, Clear, Frosted, Thick, Subtle, Your keyboard).

### Removed
- The glass library's lens effect is replaced by the new glass. It stays as the fallback when a phone
  cannot run the new one, and as a setting.

## 0.9.11 — 2026-11-17

### Added
- Layouts that switch by themselves: one for the trip home (after the last class until you are home) and
  one for exam week; the previous layout comes back afterwards unless you changed it by hand.
- The hub's "On the way home?" card shows what the check-in planned for the trip.

## 0.9.10 — 2026-11-17

*Not published on its own: it came in 0.9.11.*

### Removed
- The glass box behind the calendar, Now card and Today widgets. They now sit on the wallpaper; the card
  is an option.

## 0.9.9 — 2026-11-17

### Added
- Sunday review card on the search page: the week's blocks, focus time, absences and days to the next
  exam; a tap reviews the week with Claude.

## 0.9.8 — 2026-11-17

*Not published on its own: it came in 0.9.9.*

### Added
- The drawer's search field shows the study glance and has a one-tap Ask Claude.
- A size setting for the tag button (bigger by default, 50 dp).

## 0.9.7 — 2026-11-17

*Not published on its own: it came in 0.9.9.*

### Added
- Glass takes on the colour of the current study block's module.
- "Photo of the board": the camera saves the photo straight into a folder named after the class.

## 0.9.6 — 2026-11-17

*Not published on its own: it came in 0.9.9.*

### Changed
- The drawer's letter index is now a short rail within thumb reach, with a magnifier while you drag. It no
  longer triggers Back by accident.

### Removed
- The full-height letter strip on the edge is no longer the default; it stays as an option.

## 0.9.5 — 2026-11-17

*Not published on its own: it came in 0.9.9.*

### Added
- Data from the evening check-in: confirmed exam dates (≈ until confirmed), an absence meter per module
  (marked unverified), the grade average once every coefficient is known, and a daily flashcard that turns
  over.

## 0.9.4 — 2026-11-17

### Added
- Rest after getting home: a rest ring, then a Start button for the evening's first block.
- Wind-down: from 22:30 the wallpaper loses colour and the evening check-in comes first.
- Speed bump: during a study block, apps tagged "distracting" wait behind a card for 5 seconds.
- Icon motion: press speed, an optional wobble while editing, and icons that land when you come back.

### Removed
- Nine of the eleven press motions. Squish (crisper), Tilt and Off remain.

## 0.9.3 — 2026-11-17

*Not published on its own: it came in 0.9.4.*

### Added
- A study-first search page: cards you put in your order (widgets, timeline, exam countdown, next block,
  study commands, Ask Claude, apps).
- Commands `!next`, `!exam`, `!plan`, `!abs`, and `?question` for Claude.
- "Leave by" before the first class, "present?" after each class, a pulse when a block ended without a
  mark, and "Reschedule".

### Fixed
- The calendar on the search page is no longer cut off at the bottom.

## 0.9.2 — 2026-11-17

### Added
- The pill's study item is yours: an icon, an app's icon, your picture or a liquid circle; frame, size,
  colour and day ring. Every pill item can have its own tap, long press and swipes.
- A long press on the study item writes a note for tonight's check-in.

## 0.9.1 — 2026-11-17

### Added
- The study hub ("Today"): grows out of the pill on a spring and shrinks back under your finger, with the
  timeline, the block now or next, tasks, notes, tomorrow, quick notes and Claude prompts.
- Arrival home (home Wi-Fi, the charger or "I'm home") and "Shift tonight", which moves the evening's
  blocks.
- A double tap on the clock opens the hub with the note field ready.

### Removed
- The old Today panel: the hub replaces it.

## 0.9.0 — 2026-11-17

### Changed
- Going home during a call now redirects as usual. The old switch "Do not redirect during calls" is
  replaced by "During calls" with three choices.

### Fixed
- Going home during a phone or Messenger call stayed on HONOR Home.
- The drawer and keyboard no longer flash on the way home after opening an app from the drawer.
