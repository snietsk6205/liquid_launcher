# 3. Settings tree

## 3.0 How the settings app works

Every option is declared once in a **settings registry**: key, type, default, allowed range, title, description, search keywords, section, and which presets touch it. Everything else is generated from that declaration:

- **Search**: one field at the top of Settings searches titles, descriptions and keywords with the same fuzzy engine as universal search, and jumps to the option with a highlight.
- **Per-option reset**: long-press any option → Reset to default / Copy value / Add to Favorites. A dot marks options changed from default; each section has "Show changed only" and "Reset section".
- **Presets**: named bundles of values per section (e.g. drawer "Thumb", glass "Performance") and whole-launcher presets ("Current setup", "Minimal", "Study").
- **Scopes**: an option can be global, per profile (Study / Free / Night), per page, or per item. Lower scopes show "Inherit" as their first choice.
- **Favorites and Recently changed** sit on the first screen.
- **Live preview**: appearance sections show a miniature home or drawer that updates while you drag sliders.
- **Export**: because all values live in one registry, backup, theme files and profiles are just filtered dumps of it.

Notation: `[type · default]`. Types: toggle, slider (range), choice, color, font, shape, action (see 3.16), text.

---

## 3.1 Redirect engine

- Enabled `[toggle · on]`
- Service status (connected / last event / HONOR Home version) `[read-only]`
- Triggers
  - HONOR Home appears → show launcher `[toggle · on]`, delay `[0–1000 ms · 50]`
  - Leaving recents → show launcher `[toggle · on]`, delay `[0–1000 ms · 80]`
  - Screen off → show launcher `[choice: always / only if home was in front / never · always]`, delay `[0–5000 ms · 1000]`
  - After unlock check (re-assert if HONOR Home is in front) `[toggle · on]`
- Detection
  - System launcher package `[text · automatic: the phone's default launcher]`
  - Home activity classes `[list · automatic: the system launcher's home activities]`
  - Recents signatures: view ids `[list]`, id substrings `[list]`, class names `[list]`
  - Calibration wizard `[action]`
  - Re-verify after system launcher update `[toggle · on]`
- Exclusions
  - Do not redirect while: in a call / split screen / these apps were in front before `[multi-choice + app list]`
  - Pause now for `[1 / 5 / 30 min / until re-enabled]`
  - "Open HONOR Home" pause duration `[slider · 60 s]`
- Hand-over
  - Launch animation `[choice: none / fade · none]`
  - Content settle animation `[choice: scale-in / fade / none · scale-in]`, duration `[0–400 ms · 180]`
  - Cover overlay when pressing Home on the launcher `[toggle · off]`
  - Exclude launcher from recents `[toggle · on]`
- Safety
  - Loop guard threshold `[redirects / seconds · 4 / 3]`, pause time `[slider · 30 s]`
  - Quick Settings tile `[toggle · on]`
- Debug log
  - Enabled `[toggle · on]`, verbosity `[choice: events / + decisions / + view tree · decisions]`
  - Size limit `[slider · 2000 lines]`, View / Share / Clear `[actions]`
  - Latency stats (event → first frame, median and p95) `[read-only]`
- Setup checklist (restricted settings, accessibility, battery, app launch, overlay permission) `[actions with status]`

## 3.2 Home screen

- Pages
  - Default page, infinite scroll `[toggle · off]`, page transition `[choice · slide]`
  - Indicator `[choice: dots / line / numbers / none · dots]`, position, size, color, auto-hide
  - Wallpaper scrolling with pages `[toggle · off]`
- Grid (global default; per-page override)
  - Rows `[1–12 · 7]`, columns `[1–12 · 6]`, max grid limit `[slider · 12]`
  - Half-cell placement `[toggle · off]`, allow overlap `[toggle · off]`, push items aside `[toggle · off]`
  - Margins left/right/top/bottom `[dp sliders]`, cell gap, respect status/navigation bar insets
- Icons
  - Size `[50–200 % · 100]`, shape mask `[shape]`, shadow, tint, press effect
  - Labels: show `[toggle · off]`, font, size, color, lines `[1–2]`, shadow, casing
- Dock
  - Enabled `[toggle · off]`, columns, rows, pages, height, bottom margin
  - Background `[none / tint / frosted / liquid glass]`, shape, labels
- Search pill on home: managed as a widget (3.6) with shortcut here
- Behaviour
  - Lock layout `[toggle · off]`, add new apps to home `[toggle · off]`
  - Tap empty space action, status bar `[show / hide / auto]`, navigation bar tint
  - Keep page on return `[toggle · on]`, return to default page after `[never / 30 s / 5 min]`
- Per-page settings (open from edit mode): name, grid, margins, icon size, labels, background dim, visible in profiles

## 3.3 Folders

- Closed: default span, shape `[shape]`, background `[none / tint / frosted / liquid glass]`, opacity, border, preview `[grid 2×2 / grid 3×3 / circle / stack / first icon]`, preview icon scale, label, badge
- Open: style `[expand / popup / full-screen / bottom sheet]`, rows × columns `[1–12]`, icon size, labels, background, backdrop blur `[0–40 dp]`, title position, close on launch `[toggle · on]`
- Interaction: tap `[open folder / launch first app]`, swipe up `[open folder / launch first / action]`, large-folder mode `[toggle]`
- Sorting inside: manual / alphabetical / most used
- Animation: style, speed
- Per-folder overrides: everything above, plus tags and "hidden in profiles"

## 3.4 App drawer

- Opening: gesture `[action · swipe up]`, focus search and open keyboard `[toggle · off]`, remember position `[toggle · off]`, close after launch `[toggle · on]`
- Layout
  - View `[grid / list / compact list]`, columns `[2–12 · 5]`, row height, icon size, labels (as home)
  - Fill from `[top / bottom · bottom]`, content padding, edge fade
  - Background `[wallpaper dim / blur / solid / glass]`, dim `[0–100 %]`, blur `[0–40 dp]`
- Sorting: `[manual / pinned first / most used / recently installed / alphabetical / by tag]`, secondary sort, section headers `[toggle]`
- Search pill: show `[toggle · on]`, position `[bottom / top]`, width, height, shape, glass intensity, placeholder text, show menu button, hide on scroll `[toggle · off]`, also search other sources `[toggle · off]`
- Tabs: enable, manage (create / rename / reorder / icon / color / rule), tab bar position `[top / bottom]`, style `[text / pills / icons]`, swipe between tabs, default tab, hide "All"
- Tags: manage tags (name, color, icon, flags: distracting / study), show tag chips, multi-select filter mode `[any / all]`, auto-tag by Play category `[toggle · off]`
- Work profile: show `[own tab / merged with badge / hidden]`, tab position, badge style, paused-state action `[open Island / system settings]`
- Suggested row `[toggle · on]`, count; Recently installed section `[toggle · off]`, period
- Fast scroller `[alphabet rail / bubble / off]`, haptic per letter
- Hidden apps: manage list, also hide from search `[toggle]`, reveal gesture
- Per-app (from popup → Edit): label, icon, tags, pin, hide, swipe actions, open in profile

## 3.5 Universal search

- Opening: gesture `[action · swipe down]`, open keyboard `[toggle · on]`, layout `[results on top / anchored to keyboard]`, background dim/blur
- Sources (each: enable, order, max results, show when query empty)
  - Apps, Tags, Folders, Launcher actions, Settings pages
  - Contacts (permission), Calendar events (permission), Tasks, Files (permission, folders to include, types)
  - Calculator, Unit conversion, Web search, Bangs
- Matching: typo tolerance `[off / 1 / 2 edits]`, match initials, match package names `[toggle · off]`, accent folding `[on]`, minimum query length per source
- Ranking: usage weight `[0–100 %]`, time-of-day weight `[0–100 %]`, learn query → result picks `[toggle · on]`, reset learned data `[action]`
- Web: engine `[DuckDuckGo / Google / Brave / Startpage / custom URL]`, open in `[default browser / chosen app]`
- Bangs: list editor (trigger, name, URL template or app intent), bang prefix `[! / @ / custom]`
- Quick add task prefix `[text · +]`
- Empty state: suggested apps, recent searches (count, clear), current block card
- Enter key: `[launch first result / web search]`; auto-launch when only one result `[toggle · off]`
- Result appearance: row height, icon size, section headers, glass cards

## 3.6 Widgets

- Built-in widgets: list with per-widget defaults (see doc 4 for each widget's options)
- AppWidgets: corner clipping `[toggle · on]`, radius, padding `[system / none / custom]`, glass plate behind `[toggle · off]`, allow scroll gestures inside, update size on resize
- Search pill widget: shape, width, height, glass intensity, tint, border, content left `[search icon / app icon / none]`, placeholder, content right `[date / weather / time / next event / battery / none]`, extra buttons `[voice / lens / app / action]`, tap action, long-press action
- Widget stacks `[toggle · off]`
- Resize: snap `[cell / half cell]`, show size label

## 3.7 Gestures and actions

- Home empty space: swipe up `[drawer]`, swipe down `[search]`, double tap `[lock]`, long press `[edit mode]`, pinch in `[page overview]`, pinch out `[none]`, two-finger swipe up `[none]`, two-finger swipe down `[notifications]`, two-finger tap `[none]`, swipe from left edge zone `[none]`, swipe from right edge zone `[none]`
- Edge zones: position and height per side `[≤ 200 dp]`, width, show hint
- Home button on default page `[none / action]` (fires after the redirect returns)
- Back gesture on home `[none / action]`
- Icon gestures: defaults for swipe up `[none]`, swipe down `[none]`, double tap `[none]`; per-icon overrides live on the icon
- Sensitivity: swipe distance threshold, velocity threshold, double-tap window `[150–400 ms · 250]`, long-press time
- Custom launcher actions list (name + action chain)

## 3.8 Look and feel (theme)

- Theme: active theme, theme gallery, **theme editor**, import / export `.lltheme`, duplicate, delete
- Theme editor
  - Colors: source `[wallpaper / Material You / custom seed / fully manual]`, light / dark / auto / scheduled, contrast, every color role editable, AMOLED-style pure black
  - Typography: font per role (clock, labels, titles, body), import font files, weight, width, letter spacing, size scale
  - Shapes: global corner style `[rounded / squircle / cut]`, radius scale, per-component shape
  - Glass: quality `[full / frosted / tint / auto]`, blur radius, refraction height and amount, chromatic dispersion, tint color and opacity, saturation (vibrancy), highlight (angle, intensity, follows tilt `[toggle · off]`), inner shadow, border, noise; presets (Clear, Frosted, Smoked, Performance)
  - Surfaces: which components use glass (search pill, dock, folders, popup, cards, drawer pill, panels)
  - Icons: shadow, tint mode `[none / monochrome / theme color]`, press style
  - Motion: see 3.10
- Wallpaper
  - Source `[launcher-managed / system with all-files access / system, transparent]`, pick image, crop, separate lock screen image
  - Dim `[0–100 %]`, blur `[0–40 dp]`, saturation, tint; separate values for home / drawer / search / folder open
  - Scroll with pages, zoom on open/close, per-profile wallpaper, day/night wallpaper
- Status and navigation bars: icon color `[auto / light / dark]`, scrims
- Dark mode schedule

## 3.9 Icons

- Global icon pack `[choice]`, home pack `[inherit]`, drawer pack `[inherit]`, folder preview pack `[inherit]`
- Fallback for apps not in the pack: `[pack mask (back/mask/upon) / generated: glyph on shape / monochrome system icon / original]`
- Generated fallback: shape, background color mode `[pack average / theme / app color]`, stroke-style match `[toggle]`, scale, padding
- Adaptive icon shape `[shape]`, force adaptive for legacy icons, themed (monochrome) icons `[toggle]`
- Icon size, per-surface scale (home / dock / drawer / folder / search)
- Calendar icon shows today's date, clock icon animated `[toggles]`
- Per-app overrides: list, reset all
- Icon cache: size, rebuild `[action]`

## 3.10 Motion and haptics

- Global animation speed `[0.25–3× · 1×]`, preset `[expressive / standard / calm / off]`
- Per element (drawer, search, folder, app launch, page change, edit mode, press, popup, panel, hand-over): style `[choices per element]`, speed `[multiplier]`, spring stiffness and damping (advanced)
- App launch animation `[clip reveal / scale up / fade / system default]`
- Haptics: master `[toggle · on]`, intensity, per event (tap, long press, drag start, snap to cell, page change, drawer snap, folder open, task complete, search result select, fast-scroll letter): pattern `[tick / click / heavy / double / none]`
- Sound effects `[toggle · off]`

## 3.11 Study

- Sync: GitHub repo (owner/name), token (enter, test, status, expiry warning), branch, file paths, sync now, interval `[15 / 30 / 60 min · 30]`, only on Wi-Fi `[toggle · off]`, last sync and pending changes `[read-only]`
- Calendar: permission, calendars to read `[multi-choice]`, study block rule `[all events in chosen calendars / title contains / description marker]`, marker text, write completion back to calendar `[toggle · off]`
- Tasks: show done tasks `[toggle]`, sort `[plan order / priority / time]`, undo window `[5 s]`, completing all tasks marks block done `[toggle · on]`
- Blocks: auto-prompt "block done?" at end `[toggle · on]`, minimum block length, treat breaks as blocks `[toggle · off]`
- Study mode: enabled `[toggle · on]`, trigger `[calendar block / manual / schedule]`, profile to activate `[Study]`, start early / end late `[minutes]`, manual override duration, show status chip on home
- Mindful friction: enabled `[toggle · on]`, when `[during study blocks / during Study profile / always]`, apps by tag `[distracting]` plus app list, wait before "Continue anyway" `[0–60 s · 5]`, ask for reason `[toggle · off]`, allowance per block, scope `[launcher launches only / everywhere]`
- Statistics: keep `[toggle · on]`, upload with state file `[toggle · on]`, view, reset
- Notifications: block start / end reminder `[toggle · off]`

## 3.12 Profiles

- List: Study / Free / Night (add, rename, duplicate, delete, icon)
- Each profile holds: home layout set, dock, wallpaper + dim, theme, drawer default tab, hidden tags/folders/apps, gesture overrides, friction rules; any registry option can be made per-profile
- Switching: manual (gesture / action / quick panel / search), schedule (time ranges, days), calendar (study blocks, or events matching a rule), charging / headphones (optional)
- Priority order when several rules match; "hold manual choice until" `[next rule change / fixed time]`
- Transition animation `[cross-fade / none]`

## 3.13 Suggestions and badges

- Predicted apps: enabled, data source `[launcher launches / + usage access]`, factors (time of day, weekday, profile, last app), row count, exclude apps/tags, reset
- Recent apps row: count, exclude
- Notification badges: enabled `[toggle · off]`, style `[dot / count / none]`, color, position, size, show on folders, exclude apps, notifications in popup `[toggle · on]`

## 3.14 Backup and updates

- Backup now → file `[.llbackup]`, contents `[settings / layout / themes / icons / tags / usage data / study cache]`, passphrase `[optional]`
- Restore from file, selective restore
- Automatic backup: frequency, keep last N, destination `[local folder / GitHub repo]`
- Layout history (last 20 saved layouts) → restore
- Updates: check GitHub releases `[toggle · on]`, channel `[stable / branch build]`, install `[ask / automatic when idle]`, changelog

## 3.15 Advanced, privacy, about

- Performance: glass quality auto-step-down `[toggle · on]`, max refresh rate `[auto / 60 / 90]`, icon bitmap resolution, preload drawer `[toggle · on]`, frame-time overlay
- Permissions overview: each permission, why, status, revoke link
- Network: allowed features `[GitHub sync / updater / weather]` (nothing else connects)
- Diagnostics page (device checks from Phase 1A), export diagnostics
- Reset: all settings / layout / learned data
- Language, restart launcher, about, licenses and attribution

## 3.16 The action picker (used everywhere an option says "action")

- **Launcher**: open drawer (optionally a tab), open search (optionally pre-filled or one source), open panel, edit mode, page overview, go to page N / default page, toggle dock, switch profile, toggle study mode, toggle redirect engine, open HONOR Home, open settings (optionally a section), wallpaper picker, toggle wallpaper dim, complete current task
- **System** (via accessibility service): notifications, quick settings, recents, lock screen, power menu, screenshot, split screen, back
- **Apps**: launch app (choose profile), app shortcut, activity, legacy shortcut
- **Content**: open URL, web search, dial contact, open file or folder
- **Media**: play/pause, next, previous, volume panel
- **Chain**: several actions in order, with optional delay
- **None**
