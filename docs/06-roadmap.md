# 6. Roadmap

## Plan as of 0.2.0 (2026-11-13)

| Phase | Content | Builds (rough) | State |
|---|---|---|---|
| 1A | Redirect engine, diagnostics, glass benchmark | 1 | Done (0.1.0) |
| 1B | Home grid, pill, drawer, long-press menu, search v1, settings editors and previews | 0.2.0 | Tested on the phone |
| 1B | Fixes from the 0.2.0 test: redirect, drawer transition / tabs / scroll bar, page overview, clock resize; tags and own drawer tabs; icon packs and icon change; folders, per-icon swipe actions, app shortcuts | 0.2.1 | Built, needs phone test; completes Phase 1B |
| 1B (polish) | Whatever the 0.2.1 test turns up; dragging apps out of folders | 1 | Next |
| 2 | Theme editor (colours, fonts, shapes), theme files, more gestures (two-finger, pinch, edges), dock, page transitions, backup/restore, in-app updater, haptics | 3 | |
| 3 | Widgets: other apps' widgets, clock gallery, built-in widgets, custom panels | 2–3 | |
| 4 | Search sources (contacts, calendar, files, settings pages), suggested apps, notification badges | 2 | |
| 5 | Study companion: GitHub task sync, calendar blocks, Now card, study widgets | 2–3 | |
| 6 | Profiles (Study / Free / Night), study mode, mindful friction | 2 | |
| 7 | Performance and polish: baseline profiles, frame-time review, battery review | 1–2 | |

About 16–19 builds in total. The original phase descriptions below still apply.

## Feedback on 0.2.0: where each point went (2026-11-13, Opus)

| Point | Where | Notes and recommendations |
|---|---|---|
| Page reorder, home page | Done in 0.2.1 | Edit mode → Pages: miniatures, move left/right, house = page Home returns to, remove, add. |
| Widgets | Phase 3 | Unchanged. Recommendation: the edit toolbar's "Add clock" button becomes "Widgets" (picker with built-in and system widgets) in Phase 3. |
| Clock customisation and resize | Resize and settings entry done in 0.2.1; styles in Phase 3 | Hold the clock: edit mode with resize handles and a "Clock settings" chip. Clock gallery (stacked, analog, word, flip…) stays Phase 3. |
| Long-press on empty space menu | Phase 2 | Recommendation: make the edit toolbar a customisable list like the pill (items, order), and offer "hold empty space → glass quick menu" (Edit, Pages, Widgets, Wallpaper, Settings) as an alternative to going straight into edit mode. |
| Drawer transition | Done in 0.2.1 | Depth / slide / fade, pill morph, spring with finger speed, speed / bounce / rise / shrink / blur settings. Recommendations for Phase 2 (motion): a haptic tick when the drawer passes the open point; the same depth transition for the swipe-down search; icon launch animation from the drawer item. |
| Drawer tabs in a glass pill | Done in 0.2.1 | Top or bottom, tap / tap again / hold. |
| Organising apps (tags, own tabs) | 1B rest (next build) | Tags first, then custom tabs built from tags or rules; they appear in the same tabs pill. |
| Scroll bar and scroll effects | Done in 0.2.1 | Glass handle or line, letter bubble, elastic / curved effect, stretch switch. |
| Search results behind the keyboard | Fixed in 0.2.1 | |
| Recents interrupted, redirect after leaving recents | Fixed in 0.2.1 | D37–D39. |

## Proposal: saved home layouts (for Fable to review and place)

The user's idea: save the whole home setup under a name and switch between setups.

**What a layout contains**

| Included | Excluded (always global) |
|---|---|
| Pages and items (apps, widgets, clock size and place), home page | Installed apps, hidden apps, renamed labels |
| Pill items, position, size and actions | Redirect settings, debug and detection settings |
| Icon pack choice and per-app icons (once icon packs exist) | Glass quality and benchmark cap (device facts) |
| Home grid, margins, labels, clock options | Usage statistics |
| Gestures on the home screen | Drawer contents and tags (shared), drawer sort |
| Wallpaper (optional per layout, off by default) | |

Stored as `layouts/<id>.json`: the `HomeLayout` plus a snapshot of the setting keys listed above (the registry can mark keys as "part of a layout" with one flag, so new options join automatically).

**Switching**

- Edit mode → Layouts: list with live miniatures (the same drawing as the page overview), Save current as…, Switch, Rename, Duplicate, Delete.
- Pill or gesture action "Switch layout" (picker) and "Next layout".
- Switching is one undo step (undo returns to the previous layout) and cross-fades the home screen.
- A layout can be marked "temporary": switching back restores it exactly, unsaved changes to the temporary one are dropped.

**Automatic switching** (overlaps with Phase 6 profiles; recommendation: build layouts first, then let profiles pick a layout instead of owning their own copy)

- By time of day and weekday (simple schedule rows).
- By calendar study block (Phase 5) and by study mode.
- By headphones / charging / Wi-Fi name later if wanted.
- Manual switch pauses automatic switching until the next rule boundary.

**Export / import**: a layout exports as one `.lllayout` file (JSON plus missing icon files), and is part of the backup (Phase 2). Importing checks for apps that are not installed and leaves their cells empty with a placeholder.

**Placement suggestion**: core (save / switch / undo / export) in Phase 2 next to backup and theme files, about 1 build; automatic switching with Phase 6.


Each phase ends with: a signed APK on the GitHub Releases page, a test checklist of about ten minutes, and a known-issues list. Phases are sized so that I can work through them without input; the only stops are phone tests and decisions that are expensive to reverse.

Builds: GitHub Actions on every push to the working branch (compile, unit tests, lint), release APK attached to a GitHub Release at the end of each phase. From Phase 2 the launcher can update itself from those releases.

## Phase 1A — Redirect engine and device checks (small, first)

The whole design rests on a few device behaviours. This build proves them before the big work starts.

Contents:
- Project skeleton: modules, CI, signing, release pipeline
- AccessibilityService with the state machine, loop guard, calibration, debug log, settings page for delays / exclusions / on-off
- Onboarding checklist for MagicOS (restricted settings, battery, app launch)
- A bare home screen (wallpaper + clock + all apps list) so the redirect has something to show
- **Diagnostics page** that measures and records: redirect latency, background-start permission, wallpaper access, work-profile app listing, AppWidget binding, glass benchmark (three tiers, fps), refresh rate

What you test (10 min):
1. Install, follow onboarding, enable the service. Disable the MacroDroid macros.
2. Open and close several apps with the home gesture. Do you see HONOR Home, or only wallpaper?
3. Open recents, switch apps, leave recents to home. Does recents ever get interrupted?
4. Lock and unlock. Is the launcher already there?
5. Press Home while on the launcher. How does the round trip look?
6. Open recents from the launcher. Is the launcher listed as a card?
7. Reboot. Does it work without opening the app?
8. Leave the phone for a few hours, then check it still redirects.
9. Run Diagnostics → "Run all" and Share the report (one tap; send me the text).

## Phase 1B — MVP launcher

Contents:
- Home: pages, free grid up to 12 × 12, drag and drop, edit mode with undo, basic folders
- Drawer: smooth grid, bottom/top fill, floating glass search pill, sorting (alphabetical, most used, recently installed, manual), hide and rename
- Icon packs: global / home / drawer packs, per-app icon, masking for uncovered apps
- Universal search v1: apps, settings pages, calculator, units, web, bangs, launcher actions; fuzzy and typo-tolerant; usage ranking
- Glass: search pill, folder, popup, with quality tiers
- Launcher-managed wallpaper, wallpaper colors
- Widgets v1: Big Digital clock, date line, search pill
- Settings app with registry, search, per-option reset; "Current setup" preset
- Gestures: swipe up / down, double tap, long press
- Work profile apps listed (own tab)

What you test:
1. Rebuild your current home (clock, 5 × 3 icons, search pill) from the preset. Anything that cannot be matched?
2. Scroll the drawer fast, both fill directions. Any stutter?
3. Swipe up/down repeatedly and quickly. Do drawer and search always follow the finger?
4. Search: type app names with typos, initials (`yt`), a calculation, `!yt test`.
5. Apply your icon pack; check apps the pack does not cover.
6. Set a per-app icon and a custom label.
7. Create a folder, change its preview style, open and close it.
8. Change three settings found through settings search, then reset one.
9. Use it as your daily launcher for a day; note anything that feels slower than Nova.

## Phase 2 — Customization depth

- Theme editor (colors, fonts, shapes, glass), theme import/export, presets
- All folder options (shapes, glass, preview styles, tap-first, large folder)
- Per-icon swipe and double-tap actions; full gesture set (two-finger, pinch, edge zones)
- Dock, per-page settings, page transitions
- App popup with static and legacy shortcuts, activity picker
- Backup/restore to file; in-app updater
- Motion and haptics settings

Test: build one theme and export/import it; set swipe actions on two icons; back up, clear data, restore; update through the in-app updater.

## Phase 3 — Widgets

- AppWidget hosting (bind, configure, resize, clip, glass plate)
- Clock gallery (stacked, minimal, analog, word, flip, date + next event, world)
- Date, battery, media, text template, note, image, action button, app rows
- Widget picker, copy/paste style
- Custom panels

Test: add two AppWidgets from your apps and resize them; try each clock; build one panel; check battery use after a day with widgets.

## Phase 4 — Drawer organisation and search sources

- Tags (create, assign, filter, group), custom drawer tabs with rules
- Work tab options, paused state with Island
- Search sources: contacts, calendar, files, tags, folders; learned ranking
- Suggested and recent apps rows
- Notification badges and notifications in the popup

Test: tag ten apps and build Study / Media / Tools tabs; find a contact, an event and a file through search; check badges against real notifications.

## Phase 5 — Study companion

- GitHub sync (plan/state), offline outbox, token setup
- Calendar blocks, Now card, Today list, Week view, Study panel, study widgets
- Tasks in search, quick add to inbox
- Claude side: first real `plan.json` written from your P2 plan

Test: complete tasks online and in airplane mode, then reconnect and confirm `state.json` changed; move a block in Google Calendar and see it follow; mark a block done.

## Phase 6 — Profiles, study mode, friction

- Profiles (Study / Free / Night) with layout sets and switching rules
- Study mode on calendar blocks, manual override
- Mindful friction card and statistics
- Wallpaper dim/blur per surface and per profile

Test: run one real study block with study mode; open a distracting app; switch profiles by gesture; check the block statistics reach `state.json`.

## Phase 7 — Performance and polish

- Baseline and startup profiles generated in CI, R8 full mode
- Frame-time review of every transition on the device, glass budget tuning
- Battery review of the service over several days
- Widget stacks, GitHub backup, remaining options from your feedback list

Test: side-by-side feel against Nova for a week; report anything under 60 fps using the frame-time overlay.

## Performance targets (checked each phase)

| Metric | Target |
|---|---|
| Redirect: event → first launcher frame | under 100 ms median |
| Drawer and search open | follows the finger, no dropped frames at 90 Hz |
| Drawer scroll | no frame over 16 ms; target 11 ms |
| Search results after a keystroke | under 16 ms for apps, under 100 ms for all sources |
| Cold start to first frame | under 400 ms |
| Service battery use | not visible in the battery list (under 1 % per day) |
