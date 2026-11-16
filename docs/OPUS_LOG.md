# Opus work log

Written by Claude Opus 5.5 while Fable 5.2 is unavailable, so Fable can review the work later.
Every Opus commit starts with `[opus]`: `git log --grep="\[opus\]"`.

---

## Entry 1: hand-over state (2026-11-13 12:04 UTC → 12:15 UTC)

**Model:** Opus 5.5 Extra
**Goal:** record where the project stood when Fable stopped, before changing anything.

### What Fable built (committed, last commit `cc5e6e7`)

- **Design** (`docs/01`–`07`, `DECISIONS.md` D1–D30). Not touched by me.
- **Phase 1A, released as v0.1.0 build 10** and tested by the user on the phone (HONOR, MagicOS 9, Android 15 API 35, 90 Hz):
  - `feature/redirect`: accessibility service + `RedirectEngine` state machine (unit-tested), recents detection by view id, calibration, loop guard, QS tile.
  - `core/settings`: settings registry (`LauncherSettings`) + `SettingsStore` (DataStore, in-memory reads).
  - `core/designsystem`: theme, `liquidGlass` modifier with Full/Frosted/Tint tiers (Kyant0 backdrop 2.0.1), launcher-managed wallpaper.
  - `feature/home`: temporary home screen (clock, all-apps grid, glass bar with Settings + System launcher).
  - `feature/settings`: generated settings screens, setup checklist, diagnostics, glass benchmark, debug log.
  - CI: unit tests → signed release APK → emulator smoke test → GitHub release `v0.1.0`.
- **User test results for build 10:** redirect works and replaces MacroDroid. Below 160 ms delay it interrupts opening recents; user set 160 ms. Glass benchmark: Full 76 fps / Frosted 60 / Tint 60 at 90 Hz (old measurement, see below).
- **After the user's report Fable committed `cc5e6e7`** (not yet released):
  - Engine waits until the system launcher's home screen views (`workspace`, `hotseat`) are visible before redirecting, falling back after 700 ms; counts outcomes and drops to a 30 ms delay once the check has proven reliable (≥10 home redirects, ≥5 recents openings, <5% of recents openings had the home views visible). Default delay 160 ms.
  - Engine and all window checks moved to a `HandlerThread`.
  - Glass benchmark asks for the fastest display mode, measures delivered frames and missed deadlines, stores the best tier that holds ≥80% of the refresh rate (`state.glassCap`).
  - Emulator test moved from API 33 to API 35.

### Uncommitted work Fable left (start of Phase 1B)

- New module `core/data`: `Models.kt` (AppRef, AppMeta, HomeItem/HomePage/HomeLayout, Usage), `JsonStore.kt` (debounced atomic JSON files), `LauncherAction.kt` (encodable actions for gestures/pill/menus), `LayoutRepository.kt` (pure `LayoutOps` + undo/redo), `UsageStore.kt`, `AppsRepository.kt` (apps + user labels/hidden), `LauncherData.kt` (process-wide graph), `LayoutOpsTest.kt`.
- `Setting.kt`: new `ActionSetting`, `SettingsSection.group`.
- `libs.versions.toml` / root build: kotlinx-serialization 1.13.0 and its plugin; `settings.gradle.kts` includes `:core:data`.
- A Python edit of `LauncherSettings.kt` (all Phase 1B options) **failed on an over-strict assertion and wrote nothing**. I redo it in entry 2.

### State of CI at hand-over

- Run 11 (`cc5e6e7`): build + unit tests pass; **emulator test failed 7 checks** because the accessibility service was never enabled on the API 35 image (`enabled in settings: no`, empty launcher log). On Android 15 the system drops `settings put secure enabled_accessibility_services` for an app with no installer (adb install) — the restricted-settings rule the user had to clear by hand on the phone. This is a test-setup problem; on the real phone the service runs. No release was published for run 11, so the user still has build 10.
- The emulator glass numbers (9/12/20 fps) are software rendering and mean nothing.

### Local build environment (this container, not in the repo)

- Android SDK at `/root/android-sdk` (platform 37.0, build-tools 37.0.0), `local.properties` points there (git-ignored).
- `~/.gradle/init.d/central-mirror.init.gradle.kts` puts Google's Maven Central mirror first, because Maven Central rate-limits (HTTP 429) the shared proxy address.
- `./gradlew testDebugUnitTest :app:assembleDebug` works locally; there is no emulator here (no KVM), so device tests still go through CI.

### For Fable to review

- Nothing of mine yet; this entry is a snapshot.

---

## Entry 2: Phase 1B, build 0.2.0 (2026-11-13 12:15 → 12:50 UTC)

**Model:** Opus 5.5 Extra
**Goal:** replace the Phase 1A placeholder home with the real home, pill, drawer, menu, search and settings editors; act on the user's 5 feedback points.

### Files changed

| File | Change | Why |
|---|---|---|
| `ci/smoke.sh` | Install with `-i com.android.vending`, allow restricted settings, print why the service did not bind; new UI checks (drawer, menu, add to home, search + calculator, edit mode) | Android 15 emulator refused the service for adb installs; UI needed device coverage I cannot get locally |
| `ci/find_text.py` | New: centre of an on-screen node by text | Lets the smoke test tap UI it found |
| `core/settings/LauncherSettings.kt` | Sections Home, Gestures, Pill, Drawer, Menu, Search, Glass (+ redirect sections grouped "Redirect"); all their options; per-surface glass (`GlassSurface`) | Fable's edit had not been applied |
| `core/settings/SettingsStore.kt` | `derive {}` helper | Redirect counters live in settings and change on every redirect; home must not recompose on them |
| `core/settings/SettingDependencies.kt` | New: hides options whose switch is off | Per-surface glass values only when "own values" is on |
| `core/data/PillSlot.kt`, `MenuEntry.kt`, `Search.kt` (+ tests) | Pill item model; menu entry ids; fuzzy matching, calculator, bangs, web URLs | Pure code, unit-tested (8 tests) |
| `core/data/LauncherAction.kt` | `OpenCalendar` | Date slot / pill panel request |
| `core/data/AppsRepository.kt` | `LaunchClock` | Home-press detection (below) |
| `core/designsystem/Glass.kt` | `GlassStyle` (quality, blur, refraction, tint) overload | Per-surface glass |
| `core/designsystem/Components.kt` + 24 drawables | Icons for actions, menu, editor | — |
| `feature/home/*` | Rewritten: `HomeScreen`, `HomeController`, `HomeGrid`, `Clock`, `Pill`, `Drawer`, `AppMenu`, `SearchOverlay`, `Gestures`, `HomeConfig`, `Previews`; old `AppsRepository` removed | Phase 1B |
| `feature/settings/*` | Action picker, app picker, pill items editor, menu entries editor, live preview card on section screens, grouped main list | User feedback 1, 3, 5 |
| `app/MainActivity.kt`, `LauncherApp.kt` | Uses `LauncherData`; Home press detection; preview wiring | — |
| `gradle.properties` | 0.2.0 | New release tag |

### Notes / shortcuts

- Home press on the launcher always passes through HONOR Home, so `onNewIntent` while resumed never happens. Detected instead as "returned within 1.5 s of pausing without having started an app" (`LaunchClock`). Heuristic.
- The drawer stays composed while closed (translated off screen) so opening has no first-frame stall and a drag that starts in it survives the drawer closing.
- Drawer "start from the bottom" uses `reverseLayout`; closing by swipe-down only works on the header strip, the search pill, or once the list cannot scroll further.
- The long-press menu opened from the drawer refracts the home layer, not the drawer grid (drawer layer is private to `AppDrawer`).
- The clock's glass plate samples a wallpaper-only backdrop (a surface cannot sample a layer it is part of); misaligned while the page is scaled in edit mode.
- Not done in this build: icon packs, folders, tags, custom drawer tabs, app shortcuts, swipe actions on icons, icon change. Listed in the menu editor as "coming next".
- Defaults I chose: home grid 5 × 9 (user's Nova shows 5 icons per row), clock 3 rows tall at the top, pill = Search + Date, long press on empty space = edit mode, double tap = lock, pill long press = settings.

### For Fable to review

- `HomeController.cellUnder()`: grab offset scaling while the page is scaled 0.9 in edit mode.
- `Gestures.kt` `iconGestures`: one detector for tap / long press / drag; relies on parents consuming swipes before the long-press timeout.
- `MainActivity.onNewIntent` home-press heuristic.
- `Drawer.kt` nested-scroll close connection with `reverseLayout`.
- `HomeScreen` reads `controller.drawer.value` only inside graphicsLayer lambdas; any read in composition would recompose every frame.

### Entry 2 addendum (12:50 → 13:00 UTC)

- **Reverted Fable's emulator API 35 → 34** (`.github/workflows/build.yml`). On the API 35 `google_apis` image the accessibility service never gets enabled from adb (`enabled_accessibility_services` reads `null`), even with `-i com.android.vending` and `ACCESS_RESTRICTED_SETTINGS allow`; so every redirect check fails for a test-setup reason. API 34 matches the earlier passing runs. Fable: if you want API 35 back, the likely fix is a `google_apis_playstore` image or clearing the Android 15 "enhanced confirmation" restriction from the shell.
- First UI run (build 14): search + calculator, edit mode, settings screens, glass benchmark pass; **swipe up did not open the drawer** although swipe down opened search through the same detector. Cause not found by reading; added `DebugLog` lines for swipe/drawer and a second slower swipe in the test to diagnose.
- Fixed after review: grid layout mis-placed items when an app was not loaded (one node per item now); dragging between pages (edge page-turn); drawer preview could open the keyboard; closed drawer hidden from accessibility.
- **Likely cause of the drawer swipe failure (13:00 UTC):** gesture modifiers were created in composition with fresh lambdas. When the drawer starts moving, the home screen recomposes (the "overlay open" flag flips) and the new `pointerInput` lambda restarts the detector, cancelling the swipe; `onDragCancel` then settles the drawer shut. Swipe down never recomposes mid-gesture, so search worked. Same failure would cancel icon drag-and-drop (drag start sets edit mode → recomposition). Fix: every gesture modifier is now `remember`ed (`Gestures.kt`, `HomeScreen.homeSwipes`, page taps in `HomeGrid`, `Pill`, drawer `closeOnDrag`). To confirm in the next CI log.

---

## Entry 3: CI fixes (2026-11-13 13:00 → 13:25 UTC)

**Model:** Opus 5.5 Extra
**Goal:** get the 0.2.0 build through the emulator test.

| File | Change | Why |
|---|---|---|
| `feature/home/Gestures.kt`, `HomeScreen.kt`, `HomeGrid.kt`, `Pill.kt`, `Drawer.kt` | Gesture modifiers `remember`ed | Confirmed by CI: swipe-up now opens the drawer (log "released at 0.05 … open") |
| `feature/home/Drawer.kt` | Grid bottom padding includes the navigation bar; search field has an accessibility label | First row was under the search pill |
| `feature/redirect/engine/RedirectEngine.kt` | No "waiting for the home screen" log after a redirect was sent | Misleading log line |
| `ci/smoke.sh`, `ci/describe_ui.py`, `ci/ascii_shot.py` | Drawer bottom dump; close system dialogs and retry the first Home press | Emulator's own launcher showed an ANR dialog after boot |

---

## Entry 4: fixes from the 0.2.0 phone test (2026-11-13 14:40 → 15:25 UTC)

**Model:** Opus 5.5
**Goal:** fix the bugs from the user's 0.2.0 test, build drawer and home feedback that belongs to 1B, place the rest in the roadmap.

| File | Change | Why |
|---|---|---|
| `feature/redirect/engine/RedirectEngine.kt` | Leaving recents needs home or drawer signature, no fallback; reopen recents after an interrupted redirect; retry timing after the exclusion check; late-timer and unreadable-window log lines | Log: redirects over MEGA, app lock, Messenger; 7 interrupted recents; 3.2 s delayed redirect with an instant retry |
| `feature/redirect/LauncherAccessibilityService.kt` | Side thread for tree logging and the split-screen window list; `reopenRecents()` via `GLOBAL_ACTION_RECENTS` | Tree walk of the new app's window (up to 1 s) and `getWindows()` (3 s) ran on the engine thread |
| `feature/redirect/RecentsDetector.kt` | Drawer ids (any of), bounds for the trace log | HONOR's drawer counts as home for leaving recents (user's rule) |
| `core/settings/LauncherSettings.kt` | Reopen recents (+ window), drawer ids, reopened counter; Drawer motion section (13 options); tabs position; Clock section | Feedback 3–5, point 2 |
| `feature/home/Drawer.kt` (rewrite), `DrawerParts.kt`, `PillMorph.kt` (new) | Depth / slide / fade; pill morph; glass tabs + quick menu; scroll bar; elastic / curve effects; keyboard-aware bottom padding | Feedback 3–6 |
| `feature/home/HomeController.kt` | Spring takes finger velocity; pages ops; widget selection and resize | Feedback 2–3 |
| `feature/home/EditTools.kt` (new), `HomeGrid.kt`, `HomeScreen.kt` | Pages overview, resize frame with handles and clock settings chip; home blur; morph pill above the drawer | Point 2 |
| `core/data` | `HomeLayout.defaultPage`, `movePage`, `setDefaultPage`, `resizeItem` + 3 tests | Point 2 |
| `ci/smoke.sh` | Pages overview check | New UI |
| Docs | Android 15 correction, D36–D41, roadmap placements and saved-layouts proposal | User request |

### Notes

- The "ListView" window event is not a usable early signal: it also arrives on plain home gestures (15:00:56, 15:01:12, 15:33:48).
- The home-screen check (Fable's) cannot tell recents apart on HONOR: workspace and hotseat are visible during the gesture (21 recents openings with them visible). The fast 30 ms mode will therefore never switch on; diagnostics now say so.
- Glass parts in the drawer read the drawer position in a draw block so they redraw while moving (kyant's node keeps the same coordinates object, so a pure move would not redraw it).
- Not tested on a device: morph pill, scroll bar drag, resize handles, reopen-recents. The emulator test covers drawer open / close, menu, edit mode and the pages overview.

### For Fable to review

1. D38 recovery: is `GLOBAL_ACTION_RECENTS` after the launcher resumes acceptable, or should the redirect itself be delayed further on slow swipes?
2. `PillMorph.kt`: one glass shape for pill and search field; geometry set in a layout modifier.
3. `ScrollFx` elastic formula and default strength.
4. `KeyboardAwarePadding` (insets read during layout to avoid recomposing the drawer).
5. Saved-layouts proposal in the roadmap: placement and scope.

---

## Entry 5: Phase 1B rest, part 1 (2026-11-13 15:25 → 15:35 UTC)

**Model:** Opus 5.5
**Goal:** start the rest of Phase 1B: organising apps and icon packs; fix the failed emulator run.

| File | Change | Why |
|---|---|---|
| `core/data/Organisation.kt` (new) | Tags, drawer tab definitions, pure ops + repository (`organisation.json`) | Feedback 4: organise apps |
| `core/data/Models.kt`, `AppsRepository.kt` | `AppMeta.tags`, `AppMeta.icon`; `AppEntry.tags`, `homeIcon`, `customIcon`; `setTag`, `setIcon` | Tags and per-app icons |
| `core/data/IconPacks.kt`, `IconThemer.kt` (new) | Find packs (Nova/ADW intents), read `appfilter.xml` / `drawable.xml`, mask uncovered icons, cache per pack and app, keep the work badge | 1B icon packs |
| `core/data/LauncherData.kt` | Organisation store; icon settings feed the apps repository | |
| `core/settings` | Sections Icons and Drawer tabs and tags; icon pack keys; menu entries "icon" and "tags" in the default | |
| `feature/home/Drawer.kt`, `DrawerParts.kt` | Tabs come from the user's list; `#tag` search; tabs share the width when there are many; closed drawer placed off screen | Run 20 failed: the hidden drawer (depth / fade keep it in place) took all home touches |
| `feature/home/AppMenu.kt`, `IconPicker.kt` (new) | Tags dialog (with "also add as a tab"); icon picker with the pack's match and name matches first | |
| `feature/settings/Editors.kt` | Tabs and tags editor; icon packs editor | |
| Tests | `OrganisationTest` (4), `AppFilterTest` (2) | |

### Notes

- The work tab is now a tab definition with source "work"; the old "Work profile apps" setting still decides whether it shows (tab / merged / hidden).
- Per-app icon overrides apply to home and drawer alike; the home / drawer pack split only affects the default icons.
- Not tested with a real icon pack (none on the emulator). Calendar-style dynamic icons and adaptive-icon reshaping are not done.

### Entry 5 addendum: rest of Phase 1B (15:35 → 15:50 UTC)

| File | Change | Why |
|---|---|---|
| `core/data/Models.kt`, `LayoutRepository.kt` | `ItemKind.FOLDER` with `folder` and `name`; `dropApp`, `dropItem`, `takeFromFolder`, `renameFolder`; `swipeUp` / `swipeDown` on items and `setSwipe` + 3 tests | Folders and per-icon swipe actions |
| `feature/home/Gestures.kt` | Before the long press, a vertical swipe the item has an action for is taken by the item; otherwise it is left to the pager, drawer or search as before | Swipe actions without breaking home swipes |
| `feature/home/Folders.kt` (new), `HomeGrid.kt`, `HomeController.kt` | Folder tile (2 × 2 preview), glass folder view with editable name; drop app on app makes a folder | 1B folders |
| `feature/home/SwipeActions.kt` (new) | Swipe up / down picker per home icon | 1B swipe actions |
| `core/data/StaticShortcuts.kt` (new), `AppMenu.kt` | Manifest shortcuts at the top of the long-press menu; failures explained in a toast | 1B shortcuts (D9) |
| `MenuEntry`, menu defaults | New entries: shortcuts, icon, swipe, tags | |

| `feature/home/HomeController.kt`, gesture files | Drawer drag keeps its own position (`dragPosition`) and snaps undispatched; velocity trackers use `addPointerInputChange` (historical points) | Run 22: on the slow emulator a 518 px swipe was released "at 0.08, 0 px/s" and closed; the same can happen on the phone during a dropped frame |
| `feature/home/HomeScreen.kt`, `ci/smoke.sh` | No home blur behind the drawer when the glass benchmark capped glass to tint; first emulator swipe 450 ms | Runs 23 / 23 re-run: software rendering of the full-screen blur starved the emulator's injected swipes. Redirect timing checks also made to wait for the redirect instead of a fixed 4 s |

Not done: dragging an app out of an open folder (use the menu's "Take out of the folder"), folder preview styles and shapes (Phase 2 per the roadmap), work-profile shortcuts.

### For Fable to review

1. `IconThemer` caching and running inside the `apps` combine (on the loading dispatcher).
2. Mask drawing in `IconPack.masked` (DST_OUT with the mask image, as most packs expect).
3. Tab state map in `AppDrawer` (plain `HashMap` of `LazyGridState`, not saved across process death).

---

## Entry 6: 0.2.2, feedback on 0.2.1 (2026-11-13, Opus 5.5)

**Goal:** the user's points A, B and D (bugs and current-phase work), placement and design of C.

| File | Change | Why |
|---|---|---|
| `core/data/GridIndex.kt` (new) + test | Rows of a drawer list with headers, index entries (sections, letters), continuous row for a finger position, letter normalisation | A: smooth index scrolling, testable without Android |
| `feature/home/ScrollIndex.kt` (new) | Curved letter index: cosine bulge around the finger, bubble (glass / solid / none), glide loop on frames, haptic tick per letter, visibility always / scroll / touch, demo finger in the settings preview | A |
| `core/settings`, `HomeConfig.kt` | Section "Scroll index" (8 options); scroll bar default "index"; tag sections at the top (`drawer.topSections`); tabs style / size / icons / buttons / held-tab menu / filter search | A, D |
| `feature/home/Drawer.kt` | Grid cells with section headers; list fills the screen with insets as content padding and a fade under the status bar; tabs bar model shared by pill and search pill; search scope by tab, `#tag` highlights its tab | D: the top pill now floats |
| `feature/home/DrawerParts.kt`, `DrawerTabsEdit.kt` (new) | Tabs in three styles and three sizes, optional buttons; held-tab menu (rename, icon, contents, choose apps, sort, move, new tag, remove; settings only if added); dialogs | D |
| `feature/home/PillMorph.kt` | Morphing pill grows a tabs row when tabs sit inside the search pill | D: "merged with the search pill" |
| `core/data/Organisation.kt` | `DrawerTabDef.icon`, `sort`, `extra` (several sources), `editTab`, `setTabSources`; filter takes the union | D: per-tab customisation |
| `core/data/ThemedColors.kt` (new), `IconThemer.kt`, `AppsRepository.kt`, `LauncherData.kt` | Monochrome masks kept per app; themed icons (system shape + glyph), glyph on the pack's background, two-tone for the rest; palette from `WallpaperColors` or the system palette; reload on wallpaper colour change and on resume when the colours differ | B |
| `core/designsystem` | 18 more icons and a name map for tab icons | D |
| Docs | Roadmap: plan as of 0.2.2, placement of every point, design for C; D43–D46 | |

Not done / limits: drawer folders (Phase 4); swipe along the tabs to scrub (0.5); the "solid" bubble uses neutral colours, not the wallpaper's; themed icons need Android 13+ monochrome layers (the phone has them).

### For Fable to review
1. `ScrollIndex` glide loop (`withFrameNanos` + `scrollToItem` per frame) and row height estimation for rows not on screen.
2. Offscreen layer for the status-bar fade over the whole list (cost while scrolling).
3. `ThemedColors.fromSeed` tones versus a real tonal palette.

---

## Entry 7: 0.3.0, Phase 2 part 1: home setup (2026-11-13, Opus 5.5)

**Goal:** the user's point C as designed in the roadmap ("Design for 0.3"), released without waiting for a phone test of 0.2.2 (the user asked for that).

| File | Change | Why |
|---|---|---|
| `core/data/LauncherAction.kt`, `Pill.kt` | New actions: home menu, wallpaper, add page, pages, layouts, save layout, next layout, switch to a layout, open a settings section; icons | Items for the home menu, usable by every gesture and pill slot too |
| `feature/home/HomeMenu.kt` (new), `HomeGrid.kt`, `HomeController.kt` | Two glass pills at the bottom; press-and-slide: the empty-space detector follows the same finger in the final pass and runs the item it is released over; home sinks back 6 % | D47 |
| `core/settings` | Sections Home menu, Wallpaper, Saved layouts and backup; long press default `home_menu`; search dim and blur; home wallpaper blur and dim; lock screen; history size; keep changes in layouts; `layoutKeys`, `isDeviceState`; `SettingsStore.exportValues` / `importValues` + test | |
| `core/designsystem/WallpaperStore.kt` | Decode without applying, apply with history and lock-screen flag, set from file, thumbnails; `WallpaperBlur` (quarter-size box blur) | D48 |
| `feature/home/WallpaperStudio.kt` (new), `HomeScreen.kt`, `SearchOverlay.kt` | Wallpaper screen: Home / Drawer / Search preview tabs, photo picker, current and recent wallpapers, blur and dim sliders, lock screen switch, tap the preview to hide the controls, Apply / Cancel; home blur and dim, blur behind search | D48, per-surface values moved forward from Phase 6 (D46) |
| `core/data/Layouts.kt` (new) + test, `LayoutsSheet.kt` (new) | Saved layouts: save as, keep changes, switch (one undo), rename, duplicate, delete, own wallpaper, export / import `.lllayout`; sheet with miniatures, hold for more, "add it to the home menu" | D49 |
| `core/data/LauncherData.kt`, `feature/settings/HomeSetupEditors.kt` (new) | Backup / restore (`.llbackup`); editors for the home menu, wallpaper entry, layouts list with export / import, backup | D50 |
| `app/MainActivity.kt` | Photo picker for the home screen; `EXTRA_OPEN` lets settings open the wallpaper screen or the layouts sheet (not counted as a Home press) | |
| `ci/smoke.sh` | Long press now checks the home menu, opens the wallpaper screen, then edit mode through the menu | |

Not done / limits: "Widgets" joins the home menu in Phase 3; no passphrase on backups; the wallpaper is centre-cropped (no pan or zoom yet); the search preview in the wallpaper screen shows only blur and dim, not the search field; automatic layout switching stays in Phase 6.

### For Fable to review
1. Press-and-slide in `HomeGrid.homeMenuSlide` (observes the final pass without consuming).
2. `LayoutsRepository` keeping changes on switch versus explicit saves.
3. Restore replacing `JsonStore` values in memory while screens observe them.

---

## Entry 8: 0.4.0, Phase 2 part 2: look (2026-11-13, Opus 5.5)

**Goal:** the next roadmap step after 0.3.0, on the user's "continue" (no phone test of 0.2.2 / 0.3.0 yet).

| File | Change | Why |
|---|---|---|
| `core/settings/Setting.kt`, `SettingRows.kt` | `ColorSetting` ("" = automatic) and a colour row: swatches, hue / saturation / brightness, hex, automatic | Theme colours |
| `core/settings/LauncherSettings.kt` | Sections Theme, Folders, Dock; page transition; `themeKeys`; layouts now include folders and dock settings | |
| `feature/home/HomeTheme.kt` (new), all home files | `HomeTheme` font / clock font and weight / accent; every home `TextStyle` takes the theme font; accent on tab highlight, index bubble, home menu | D51 |
| `HomeConfig.kt` | Glass tint colour from the theme; text colour override | |
| `core/data/IconShapes.kt` (new), `AppsRepository.kt`, `ThemedColors.kt` | Shapes (circle, squircle, rounded, square, teardrop, pebble) applied at render; themed icons follow the shape | D52 |
| `core/data/Themes.kt` (new) + test, `HomeSetupEditors.kt` | Six presets, `.lltheme` export / import, font import with a check that it loads | D51 |
| `HomeScreen.kt` | Page transitions (depth, stack, cube, carousel, fade) on the pager's own movement; per-page environment | |
| `Models.kt`, `LayoutRepository.kt` + tests, `EditTools.kt`, `HomeController.kt` | Page settings (grid, labels, name) with a dialog from the page overview; per-page grid used for drops, resizing, adding | D53 |
| `Folders.kt`, `HomeGrid.kt`, `HomeController.kt` | Folder preview (grid, stack, first app), background (tint, glass, accent, none), shape, tap opens the first app (swipe up opens), columns, grows from the tile, apps dragged out onto a page, the dock or Remove | 1B known gap closed; Phase 2 folder options |
| `Dock.kt` (new), `LayoutRepository.kt` + test | Dock row above the pill: drop from drawer / page / folder, move and swap slots, drag back to a page, usual app menu | D54 |
| `LauncherData.kt` | Imported fonts go into backups | |

Not done / limits: large folders; per-folder styles (all folders share one style); the colour row has no eyedropper; cube and carousel transitions were checked on the emulator only for crashes, not looks.

### For Fable to review
1. `HomeTheme` as global snapshot state versus a CompositionLocal.
2. Icon reload on shape change (full reload of the app list).
3. Dock drop detection by screen bounds while the page under it is also a drop target (dock wins).

---

## Entry 9: 0.4.1, fixes after the first phone test of 0.4.0 (2026-11-13, Opus 5.5)

**Goal:** the user's five points on 0.4.0 (which held 0.2.2 and 0.3.0), then a final pass over the whole launcher.

| File | Change | Why |
|---|---|---|
| `Drawer.kt`, `ScrollIndex.kt`, `DrawerParts.kt` | Index takes no space: equal side margins (`DrawerSideMargin`), letters float, lighter at rest, touch strip 24 dp, edge inset 12 dp; `IndexWave` bends list rows near the finger (away from the edge, towards the finger's height), fades other letters' apps, springs back; applied in the item's existing graphics layer | D55, point 1 |
| `core/common/WallpaperTones.kt` (new), `ThemedColors.kt`, `LauncherData.kt`, `IconThemer.kt` | Colours from the launcher's own image when it sets a wallpaper, with the system wallpaper id to detect outside changes (then the launcher stops drawing its own copy, so the outside wallpaper and its colours show together); `colourTick` for accent and text colour; themed icon cache keeps 4 colour sets; system palette rechecks only for the "system" colour source | D56, point 3 |
| `core/common/CropSpec.kt` (new) + test, `core/data/WallpaperLibrary.kt` (new) + test | Placement maths; library with content ids, recent / favourites / all, linked folders, thumbnails (disk and memory cache), migration of the old history | D57, D58, point 5 |
| `core/designsystem/WallpaperStore.kt` | Show at once, persist afterwards (JPEG, copy for layout files), render with crop and parallax, decode upright and capped at 3600 px | D57 |
| `feature/home/Wallpapers.kt` (new), `WallpaperViews.kt` (new), `WallpaperStudio.kt` (rewrite) | Prepare / persist / pool / next / automatic change; wallpaper drawn with parallax in the draw phase; transition overlay; wallpaper strip; layout name flash; wallpaper screen with Pictures / Blur and dim / Automatic pages, library tabs, folders, place mode (pinch, drag, parallax sweep), favourites, "Only for the layout" | D58, D61, D62, points 4–5 |
| `HomeController.kt`, `HomeScreen.kt` | `changeScreen` (prepare, snapshot, commit, melt, then write); layout switch loads its wallpaper first; home recorded into a graphics layer; system wallpaper offsets on page settle | D59, point 4 |
| `TagFilter.kt` (new) + test, `Drawer.kt`, `Editors.kt` | Tag chips, full tag list with search, filter chip on the search field, sideways swipe, pinned tags (settings card with order), filter replaces the tabs list with its own index, back clears the filter first | D60, point 2 |
| `LauncherAction.kt`, `Pill.kt`, `HomeMenu.kt` | Actions: wallpaper strip (the old "Wallpaper"), wallpaper screen, shuffle, filter by tag; icons shuffle, heart outline, filter, crop | |
| `LauncherApp.kt`, `MainActivity.kt` | Library migration at start; automatic changes on screen off; several-photo picker and folder picker | |
| `MainScreen.kt`, `HomeSetupEditors.kt` | The settings "Wallpaper" row opens the wallpaper screen on the home screen instead of a bare photo picker | Final pass: one place for wallpapers |
| `core/settings` | Side margins, index wave and highlight, tag filter options, parallax, transition and length, automatic change (mode, pool, shuffle); index visibility default "scroll" | |
| `ci/smoke.sh` | Home menu → wallpaper strip → wallpaper screen → Blur and dim page | |

Final pass (as a user): the layout switch toast became a glass label at the top; the wallpaper screen's sheet rises in like the other sheets; the settings wallpaper row now leads to the real wallpaper screen; texts of the wallpaper and tags sections describe what is there now.

Not done / limits: albums you make yourself (folders on the phone stand in); no eyedropper; the wave was tuned on paper, not on the phone; automatic changes need the process alive when the screen turns off (the accessibility service keeps it alive); a linked folder's sub-folders are not read.

### For Fable to review
1. Recording the whole home screen into a `GraphicsLayer` every frame (`HomeScreen.kt`) for the transition snapshot: cost on the phone.
2. `WallpaperTones.current` comparing system wallpaper ids (`getWallpaperId`) to tell launcher-set from outside wallpapers.
3. `TagFilter.tagSwipe` watching the initial pass before the text field.
4. The index default visibility change (D55) and the home menu "Wallpaper" item now opening the strip (D62).

---

## Entry 10: 0.4.2, adjustments after 0.4.1 (2026-11-14, Opus 5.5)

**Goal:** the user's three points on 0.4.1, released before Phase 2 part 3.

| File | Change | Why |
|---|---|---|
| `core/data/IconArt.kt` (new), `IconThemer.kt`, `AppsRepository.kt`, `ThemedColors.kt` | Themed icons as `IconArt` (glyph mask on a shared shape mask, or two-tone filter on the original); only pack-fitted and work-badged themed icons are still rendered; `duotoneMatrix`; palette with a known seed | D63 |
| `core/data/LauncherData.kt` | `iconPalette` flow; colour changes (`themeTick`) separated from pack reloads (`packTick`); `paletteFor(seed)`; "system" source holds the new wallpaper's tones until the phone palette changes | D63 |
| `feature/home/IconPaint.kt` (new), every icon call site, `DragState` | `IconPaint` (palette read while drawing; now / preview / eased), `AppIcon` and `ArtIcon` | D63 |
| `HomeController.kt`, `Wallpapers.kt`, `WallpaperStudio.kt`, `WallpaperStore.kt`, `HomeTheme.kt` | Palette worked out before a switch and set with the wallpaper in one frame; the wallpaper screen previews the colours of a picked picture before Apply; automatic accent follows in the same frame | D63 |
| `core/common/ColourTiming.kt` (new) | "Timing" lines in the debug log for each wallpaper change | Feedback: see where the time goes |
| `Models.kt`, `IconPicker.kt` | Picked icons follow as two-tone; per-app "Keep this icon's own colours" | D64 |
| `TagFilter.kt`, `Drawer.kt`, `HomeController.kt`, settings | Round tag list button (row end / row start / in the search field), list grows out of the button and shrinks back | D65 |
| `ScrollIndex.kt`, `Drawer.kt`, `DrawerParts.kt`, settings | Letter column follows the finger on a spring; list bend removed; highlight by list position; settings "Letters follow the finger", "Spring", "Hide after" (removed "List follows the finger") | D66 |

CI note: the first 0.4.2 run (30) failed 9 emulator checks with "Liquid Launcher isn't responding": the runner was overloaded (system load 24; the passing 0.4.1 run had load 0 but also multi-second frames on the emulator's software GPU). Fix in the same release: the home screen is recorded into the transition layer only for the frame a snapshot is taken (0.4.1 recorded it every frame). Run 31 failed the same 9 checks: the system's own window queries took up to 2.6 s (0.5 s the day before) and "Process system isn't responding" appeared too, so the emulator machines were slow; one stray "not responding" dialog then covered the screen for every later check. `ci/smoke.sh` now dismisses such dialogs with "Wait" (as a user would) before each UI step and reports how many it saw; crashes are still checked strictly.

Not done / limits: icons fitted to a pack (and work-profile apps) with themed icons on are still rendered once per colour set (cached); the "system" source can still shift slightly when the phone's palette arrives (eased); I could not measure timings on the phone, the log lines are there for that.

### For Fable to review
1. Drawing `ALPHA_8` masks with a paint colour on the hardware canvas (`ArtIcon`).
2. `IconPaint` as a global object written from three places (data flow, switches, wallpaper screen).
3. The tag list's morph (`Layout` placing the glass at an interpolated rectangle, content clipped with a layer shape).

---

## Entry 11: 0.5.0, Phase 2 part 3: touch and motion (2026-11-14, Opus 5.5)

**Goal:** the last part of Phase 2, built right after 0.4.2 as the user asked.

| File | Change | Why |
|---|---|---|
| `feature/home/HomeGestures.kt` (new), `HomeScreen.kt` | One detector for one and two fingers: swipes with the drawer following, edge-zone swipes, pinch in / out, two-finger swipes; replaces `homeSwipes` | D67 |
| `core/settings`, `HomeConfig.kt` | Gesture settings (pinch, two-finger, four edge swipes, edge width); Motion and haptics section; `GestureConfig` and `MotionConfig` | D67, D70 |
| `Models.kt`, `LayoutRepository.kt`, `Gestures.kt`, `HomeGrid.kt`, `SwipeActions.kt`, `AppMenu.kt`, `MenuEntry.kt` | Double tap per home icon; "Gestures on this icon" (swipe up, swipe down, double tap); only icons with a double tap wait before a single tap | D68 |
| `core/data/Activities.kt` (new), `LauncherAction.kt`, `HomeController.kt`, `Pickers.kt`, `SwipeActions.kt` | "Open a screen of an app" action and pickers in settings and on the home screen | D69 |
| `HomeGestures.kt` (`Haptics`, `MotionScale`), `HomeController.kt`, sheets and menus | Haptic ticks (drawer open point, pick-up, recognised gestures, double tap), launch animation choice, search depth, animation speed | D70 |
| `feature/settings/Updater.kt`, `UpdatesScreen.kt` (new) + test, `SettingsActivity.kt`, `MainScreen.kt`, `AboutScreen.kt`, app manifest, `res/xml/update_paths.xml` | In-app updater with daily opt-in and a banner on the settings main screen; About text updated (phase, network use) | D71 |
| `ci/smoke.sh` | Opens the Updates screen and checks for the check button | |

CI note: the first 0.5.0 run (33) failed "swipe up opens the drawer": the new detector fed the velocity tracker only each event's last position, so moves the system batched into one event were lost and the swipe's speed came out as 0 px/s (the drawer was released at 0.37 and closed). Fixed by feeding whole pointer changes, as the old detector did.

Not done / limits: two-finger gestures and edge swipes cannot be driven from adb, so the emulator test does not cover them; large folders and per-folder glass stay for Phase 7; the updater cannot check a private repository.

### For Fable to review
1. `homeGestures`: switching to the initial pass once a second finger lands, and undoing the drawer drag if the first finger already moved it.
2. Double-tap detection inside `iconPointerInput` (second down awaited in the same gesture block).
3. The updater's trust model: any newer build on the releases page is offered; Android's signature check keeps a differently signed APK from installing over this one.

---

## Session summary (2026-11-14, Opus, updated for 0.5.0)

**State:** 0.4.2 (entry 10) followed the user's notes on 0.4.1; its first CI run hung on an overloaded emulator and the release build includes a fix that records the transition snapshot only on demand. 0.5.0 (entry 11) completes Phase 2 with gestures, double tap per icon, the screen picker, motion and haptics settings and the in-app updater. Released: 0.4.2 as build 32 (run 32: all 29 emulator checks passed, no dialogs to dismiss) and 0.5.0 as build 34 (run 34, all checks passed after the swipe-speed fix). Unit tests pass. Neither 0.4.2 nor 0.5.0 is tested on the phone yet.

**Next step:** the user tests 0.4.2 and 0.5.0; then Phase 3 (widgets).

### For Fable to review (overall)
1. Entries 6–11 and decisions D43–D71.
2. The roadmap changes (plan as of 0.4.1): tag filtering moved from Phase 4 into 0.4.1, wallpaper library added; the user's later wallpaper and tag ideas are placed in Phases 4, 6 and 7.
3. Earlier items still open: D37–D39 redirect changes, the drawer rewrite, `IconThemer` with real packs, the home-press heuristic, emulator on API 34.

---

## Entry 12: 0.6.1, Fable's unfinished work released as he left it (2026-11-15, Opus 5.5)

**Handover.** Fable 5.2's tokens ran out again in the middle of his session, so he could not complete his tasks. His
last log entry is `FABLE_LOG.md` entry 5. Everything he wrote after 0.6.0 (the clock gallery and the study companion)
**needs a review later**, by Fable or another model; I have not reviewed it, only built and released it. The user asked
me not to change his work: his commits stay as they are and everything of mine is a separate `[opus]` commit on top.

**Where he stopped.** Fable's code said 0.7.0 (clock gallery) and 0.8.0 (study companion), and his roadmap and log call
both "Released". Neither was: the last release was **0.6.0, build 37**.

| Part | State when he stopped |
|---|---|
| Clock gallery (Phase 3 part 1, D85) | Committed (a191651, c81cd59). Run 38 failed to compile; his fix in c81cd59 compiled and passed the unit tests in run 39 |
| Run 39 (c81cd59) | Red: one emulator check, "home press redirected (0 -> 0)". After a screen dump Android suspended and re-bound the accessibility service three times, and for the whole step (both tries, 05:54:35–05:55:26) the service received no events at all; the next checks passed once it came back. The redirect code and the test script are identical to 0.6.0's passing run 37 (the 0.7.0 diff touches no redirect or CI file), so this is the emulator, not his code |
| Study format + tests (`StudyFormat.kt`, `StudyFormatTest`) | Done, committed, tests pass |
| Design and event format (`docs/05-study-integration.md`, D86) | Done, committed |
| Calendar reader and writer (`StudyCalendar.kt`) | Written, uncommitted, never compiled |
| Home screen: Now card, Today widget and panel, widget list, quick actions, notices (`Study.kt` and wiring) | Written, uncommitted, never compiled; a few parts of the design missing (below) |
| Study settings card (`StudyEditor`) and settings | Written, uncommitted, never compiled |
| D87, D88, log entry 5, roadmap | Written, uncommitted |
| Emulator run with the study code | Never ran |

Missing against his own design (`05-study-integration.md` 5.3), left for 0.6.2: the Now card has no "Skip" button; the
focus timer does not stop at the end of its block (a forgotten timer would write hours of focus); "tasks due today" is
announced once per launch of the home screen instead of once per day.

**What I did for 0.6.1**
| Commit | Change |
|---|---|
| 2460e73 (his work, my commit) | Fable's uncommitted files exactly as he left them |
| `[opus]` version commit | `app.versionName` 0.6.1 (his code said 0.7.0); roadmap rows for the clock gallery and Phase 5 say what really happened; this entry |

**Result:** run 40, all checks green on the first try: Fable's study code compiled unchanged, unit tests passed, and every
emulator check passed, including the home-press check that failed in run 39 (which confirms that failure was the
emulator). **0.6.1 released as build 40**: https://github.com/snietsk6205/liquid_launcher/releases/tag/v0.6.1. No fix
commit was needed.

---

## Entry 13: 0.6.2, the study companion finished as Fable designed it (2026-11-15, Opus 5.5)

**Goal:** the parts of Phase 5 that Fable's code did not do yet, following `docs/05-study-integration.md` and the event
format version 1 exactly. Nothing in the format, the design or his files' structure changed; the additions sit in his
files where they belong and in one new file.

| File | Change | Design point |
|---|---|---|
| `core/data/StudyLocal.kt` (new, + `StudyLocalTest`) | Ticks kept on the phone: the event's text with the marks applied and the calendar text it was made from; text form for preferences; focus minutes counted up to the block's end | 5.2 "keeps ticks to itself", 5.3 focus "to the end of the block" |
| `core/data/StudyCalendar.kt` | Without write permission or with "Write ticks back" off, a tick is kept on the phone instead of being lost; such ticks show while the calendar still holds the same text, are dropped when the event changes there, and go into the next write of that event. The focus timer stops by itself when its block ends (on refresh and on the home screen's minute tick) and writes only the block's minutes; the block's end is kept, so a timer left running overnight is still written to the right event. `firstToday` remembers announced notices per day across restarts | 5.2, 5.3, D89 |
| `feature/home/Study.kt` | Now card: "Skip" next to "Done"; notices use `firstToday`; the minute tick ends a finished focus timer | 5.3 |
| `HomeController.kt`, settings | "Plan" also opens an app given by its package name (a web address typed without `https://` still opens) | 5.3 "a link or app of your choice" |
| `ci/smoke.sh` | Two checks: edit mode lists the widgets; the home menu's Today item opens the panel (offers "Switch it on" on a fresh install) | the study code had never run on a device |
| `DECISIONS.md` | D89 | |

Not changed: the format, the marks, the status line, the classification of blocks and tasks, the UI layout, D86–D88.

Not verified on a device (no calendar on the emulator, as for 0.6.1): reading and writing real Google Calendar events on
MagicOS, the share to the Claude app, notices at a block's start.

**Result:** run 41, all green on the first try: compiled, unit tests passed (with `StudyLocalTest`), all 34 emulator
checks passed including the two new ones ("edit mode lists the widgets", "home menu opens the Today panel"). **0.6.2
released as build 41**: https://github.com/snietsk6205/liquid_launcher/releases/tag/v0.6.2.

### For the next model (review)
1. Fable's work after 0.6.0 has not been reviewed by anyone: the clock gallery (D85, `Clocks.kt`, `ClockStudio.kt`) and
   the study companion (D86–D88, `StudyCalendar.kt`, `Study.kt`). His tokens ran out before he could check it.
2. My additions (D89): the phone's copies of ticks in `StudyCalendar.edit` / `load`, and the focus timer's end.
3. Fable's open questions for the user (`FABLE_LOG.md` entry 5) are still open: a real phone notification at a block's
   start, ticking the last task marking the block done, the names of the two calendars.
4. Next by the plan: the rest of Phase 3 (other apps' widgets, more built-in widgets), then Phase 4.

---

## Entry 14: 0.6.3, the study block on the clock, icon colour lag, letter bubble (2026-11-15, Opus 5.5)

**Asked by the user:** move the study blocks into the new "Study" and "Tasks" calendars and update the schedules; say
whether Fable's code was changed (icons change colour more slowly and sometimes lag since 0.6.1, not in 0.6.0); find a
better way to see the current block than a Now card on a page; make the letter index's bubble visible (it was under the
finger); build a new version.

**Was Fable's code changed?** Not in 0.6.1 (his files were released exactly as he left them, entry 12). In 0.6.2 I added
to his study files (`StudyCalendar.kt`, `Study.kt`, `HomeController.kt`, settings; entry 13, D89), nothing that touches
icons or the clock. The lag comes from his clock gallery (D85): `ClockFace` read the icons' palette (`IconPaint`) while
composing, for the "tone" colour option. The palette eases over 260 ms on every wallpaper change, so every clock
recomposed and re-measured on each frame of that easing while the icons redrew. 0.6.0 had no reader of the palette in
composition (icons read it while drawing). Fixed in one line of `Clocks.kt` (D90). This is the only change to a file of
his outside the study companion; it is in a separate `[opus]` commit.

**Outside the repo (done with the user's connectors, not code):**
| What | Change |
|---|---|
| Google Calendar | 12 study blocks (16/11 → 6/12) recreated in "Study" in the launcher format (title "Module: what", colour per module, 2–4 `[ ]` steps, WHAT / WHY NOW / DONE WHEN, `P2-BLOCK` line, 10-min reminder); 5 errands in "Tasks"; the 17 originals deleted from the primary calendar. Fixed dates, exams, checkpoints and school events untouched |
| "Med evening check-in" task | Writes blocks to "Study" and errands to "Tasks" in the launcher format, reads the launcher's marks as Med's report, looks for `P2-BLOCK` anywhere, never edits today's or past events |
| "Med weekly supervisor" task | Reads all three calendars; never changes the launcher's marks |
| Plan doc ("Your companion") | Blocks are ticked on the phone, absences in the P2 plan |

**0.6.3**
| File | Change | Decision |
|---|---|---|
| `feature/home/Clocks.kt` (Fable's) | The palette is read only by a clock whose colour is "tone" | D90 |
| `feature/home/ScrollIndex.kt`, `HomeConfig.kt`, settings | `bubbleCentre`: beside the finger (52 dp inward, 20 dp higher), above it when there is no room; setting "Bubble position" (beside / above / under the finger) | D91 |
| `feature/home/Study.kt`, `HomeGrid.kt`, `Pill.kt`, `core/data/PillSlot.kt`, settings editors | `studyGlance` (running block with time left and progress, else next unmarked block, else open tasks); `StudyLine` under every clock widget (tap = Today; no long press, which would also start the widget's own edit-and-move); pill item "Study block"; setting "Under the clock"; the Study settings card explains it | D92 |
| Tests | `BubblePlaceTest`, `StudyGlanceTest`; the pill round-trip test includes the new item | |

Not changed: the event format, the marks, the Now card and Today widget (still in the widget list), Fable's clock faces
other than the one line.

**Result:** run 45, all green: unit tests (with `BubblePlaceTest`, `StudyGlanceTest`), every emulator check, released as
**0.6.3 build 45**: https://github.com/snietsk6205/liquid_launcher/releases/tag/v0.6.3. On the way: run 42 was replaced by
43 (the clock line's long press, below); run 43 was green and released build 43, but its new calendar check had been
skipped (the device's `content` tool refuses a bound value with a colon); run 44 showed the app working on the
emulator's calendar provider and failed on my test (a ticked box's icon is also labelled "Done", and the test tapped
it); run 45 presses Done before ticking. Build 45 replaced build 43 in the release.

**Verified on the emulator for the first time** (new check 14b, a local "Study" calendar with a running block): the
launcher reads the block from the real calendar provider, the line under the clock shows it, a tap opens Today with its
tasks, Done writes `✓ ` and the status line, a tick writes `[x]` and the status line counts it.

Changed after the first push: the clock line had "hold for the focus timer"; the clock widget's own gesture detector
starts edit-and-move after the long-press timeout without checking whether a child took the touch, so both would have
happened. The line now has a tap only.

Not verified on a device: Google's sync of the edits on MagicOS, the colour-change timing on the phone (the emulator
has no comparable GPU), how the line looks under each of the six clock designs, the bubble's distance for a real thumb.

### For the next model (review)
1. Fable's clock gallery and study companion are still not reviewed as a whole (entries 12–13).
2. D90 removes the only composition-time reader of `IconPaint` I found; a profile on the phone would confirm it was the cause.
3. The clock line shrinks the clock face by 30 dp while it is shown; if that looks wrong under the "stacked" or "words"
   designs, an overlay might be better.

## Entry 15: 0.6.4, Today, glass buttons, the clock line, the letter curve (2026-11-15, Opus 5.5)

**Asked by the user (after testing 0.6.3 on the phone):** the line under the clock did not show although it was on
(the pill item worked); in Today, open a block to read its description, put Done and Skip on the right, let tomorrow be
peeked or opened, with a setting to hide it; the letter index: the letters should be where the bubble was and the
bubble further out; liquid glass on everything, the buttons first, all customisable in settings; recommendations for
the study workspace; "Plan" showed two redirections before the P2 plan (a link-checking app, then the browser).

**Why the clock line was empty.** At 21:00 the day's only block had ended unmarked and nothing else was left, so
`studyGlance` returned nothing (D92's rule). Worse, Google Calendar had that block at 19:30–21:30 (moved at 19:43) while
the phone still showed 14:30–16:30, and the six ticks made at 20:15 had not reached Google: the phone's calendar sync
was hours behind in both directions. With the right time the line would have shown the block at 21:00.

**0.6.4**
| File | Change | Decision |
|---|---|---|
| `feature/home/Study.kt` | `studyGlance`: running, next within the hour, unmarked, next, tasks, tomorrow; "Today only" option; ring for each case | D93 |
| `feature/home/Study.kt` | Today panel: `BlockCard` (tap the title to open: all steps, notes in full; progress bar; steps count; buttons right with the main one nearest the edge), `TomorrowSection` / `TomorrowBlock` (peek, open, read only), "Sync now" with the number of unsent changes | D94, D98 |
| `core/data/StudyFormat.kt`, `StudyCalendar.kt` | `StudyDay.tomorrowBlocks`, `tomorrowTasks`, `waitingToSync`, `unmarked()`; `requestSync` (on resume, Today, 4 s after writes) | D94, D98 |
| `feature/home/Controls.kt` (new), `Glass.kt`, `HomeConfig.kt`, `HomeScreen.kt`, `LayoutsSheet.kt`, `ClockStudio.kt` | `GlassButton`, `ControlLook`, `ProvideControls`; glass on glass through the sheet's exported layer; crystal, tinted, outline, solid; edge light on every glass surface | D95 |
| `feature/home/HomeController.kt` | `openStudyLink`: Chrome tab / Chrome / Claude / the phone's choice, double tap ignored | D96 |
| `feature/home/ScrollIndex.kt` | `pullFor` (letters bend "Letters beside the finger" past it), bubble past the letter under the finger | D97 |
| `core/settings/LauncherSettings.kt` | Section "Buttons" (8 settings); Liquid glass → Edge light; Study: Blocks in Today, Done and Skip buttons, Tomorrow in Today, Open the plan and block links in, Under the clock gains "Today only"; Scroll index: Letters beside the finger, Bubble distance | |
| `feature/home/Previews.kt`, `HomeScreen.kt` | Settings → Buttons and Study companion show the Today panel live (`HomeMode.PreviewToday`); a sheet rising from the bottom puts the pill (and the morphing pill) away | D95 |
| Tests | `StudyGlanceTest` (unmarked, tomorrow, today only), `BubblePlaceTest` (bubble past the letters, `pullFor`) | |
| `ci/smoke.sh` | 14b also creates a block for tomorrow: Today's Tomorrow opens to it and its steps; with today done the clock line shows it. 14c: a picture of the letter index while held | |

Not changed: the event format, the marks, the check-in's prompt, Fable's files other than `ClockStudio.kt`'s chip (now a
`GlassButton`).

**Result:** released as **0.6.4 build 48**: https://github.com/snietsk6205/liquid_launcher/releases/tag/v0.6.4. Run 46
was green, with every new emulator check (Today's Tomorrow opens to tomorrow's block and its steps; with today's block
done the clock line shows "Tomorrow … · Micro smoke tomorrow"). Its pictures showed two things the checks could not:
the pill showing through the bottom of the Today panel (already in 0.6.3; the panel's glass is a tint on the emulator)
and "2 changes not sent to Google yet" for the emulator's phone-only calendar. Run 47: a sheet rising from the bottom
(Today, layouts, clock gallery) puts the pill away, phone-only calendars never count as waiting, no Focus on a marked
block, and the letter-index picture opens the drawer first; its picture showed the letters bent out from under the
finger with the bubble past them, and the pill still under Today: with "pill turns into the search field" on, the pill
is another composable. Run 48 moves that one away too; its picture shows the panel clear. Builds 46 and 47 were
replaced in the release by 48.

Verified on the emulator: the glance order (unit tests), the Tomorrow section, the clock line moving on to tomorrow,
the letter curve and bubble (picture), the Today panel's layout with the buttons on the right (picture). Not verified:
liquid glass buttons with real refraction (the emulator has no GPU, so they draw as crystal there), the Chrome tab and
the Claude app for links, whether MagicOS honours the sync request, the Buttons and Study settings previews.

### For the next model (review)
1. Glass buttons sample the sheet's exported layer; while Today scrolls they redraw each frame. Watch the frame rate on
   the phone with many blocks; "Crystal" is the cheap fallback.
2. `requestSync` is asked of the system; whether MagicOS honours it promptly is not verified.
3. The letter curve's defaults (64 dp, bubble 32 dp past) are a guess for a thumb; the user can tune both.

## Entry 16: 0.7.0, review of Opus's 0.6.x, search as "One space", other apps' widgets (2026-11-15, Opus 5.5)

**Asked by the user:** check everything Opus made after Fable's 0.6.0 and improve design, transitions and animations
(not the drawer's transition, which is good); give search the drawer's transition, "that makes you think everything is
part of one launcher"; give back the liquid glass of the pill and of everything Fable made (0.6.4 changed it); a
lock-screen problem (below), documented for Fable rather than solved; go to the next phase.

**Review of Opus's work since 0.6.1** (entries 12–15, `git diff v0.6.1..`, about 1 600 lines):
| Where | Finding | Done |
|---|---|---|
| `Glass.kt`, settings (0.6.4) | "Edge light" (a `Highlight` rim) was on for every glass surface by default, so the pill, menus and sheets no longer looked as Fable made them | Off by default (D101); buttons keep their own rim |
| Clock line (0.6.3) | Went blank when the day's blocks were over | Fixed in 0.6.4 (D93); now also slides between kinds of line, the ring grows smoothly |
| Today, sheets (0.6.3–0.6.4) | The pill showed through bottom sheets | Fixed in 0.6.4; the home now also sinks back behind sheets (D102) |
| Search (Fable's, kept by Opus) | Opened with its own fade, unrelated to the drawer | "One space" under the finger (D99) |
| Study data (0.6.2) | Ticks kept on the phone, focus timer, notices: covered by `StudyLocalTest`, `StudyFormatTest` and the emulator's calendar checks; no bug found | Kept |
| Ticks | Changed state in one frame | Fill and pop (D102) |
| Glass buttons (0.6.4) | Sample the sheet's exported layer and redraw while Today scrolls: cost not measured on the phone | Kept; "Crystal" is the cheap style |

**0.7.0**
| File | Change | Decision |
|---|---|---|
| `HomeController.kt`, `HomeGestures.kt`, `SearchOverlay.kt`, `HomeScreen.kt`, `Drawer.kt`, settings | `search` progress dragged by a swipe down, `openSearch` / `closeSearch` / `dragSearchBy` / `settleSearch`; home glides down and fades, wallpaper drifts (`wallpaperInSearchTransition`), pill and dock fade, field and results come from above by layout; "Opening search" gains "One space" (default) | D99 |
| `core/data/AppWidgets.kt` (new), `LauncherData.kt`, `MainActivity.kt` | Host, providers of every profile, bind (system dialog), set-up screen, ids given back at start | D100 |
| `feature/home/Widgets.kt` (new), `HomeGrid.kt`, `EditTools.kt`, `Study.kt` | Picker (launcher's widgets, apps with previews and sizes, search); `AppWidgetItem`; Date, Battery, Study block widgets; "Widget settings" and glass plate in edit mode; `placeWidget` shrinks a widget that does not fit | D100 |
| `Glass.kt` settings | Edge light off by default | D101 |
| `HomeScreen.kt`, `Study.kt` | Home sinks behind Today, layouts and the picker; clock line `AnimatedContent`; ring and tick animations | D102 |
| `feature/home/WallpaperTransitions.kt` (new), `WallpaperViews.kt`, `HomeScreen.kt`, `HomeController.kt`, settings | Asked during the same round: Ripple (from the picture tapped), Ink, Shine, Melt (AGSL shaders), Zoom; the new screen and wallpaper settle in for every style; easing and length per style; strip buttons are glass buttons | D103 |
| Tests | `AppWidgetsTest` (cell sizes, ids in use) | |
| `ci/smoke.sh` | 15: the four transition shaders compile on the emulator (from the launcher's log). 12b: the picker finds the Clock app's widgets by search, the system's "Create" is accepted and the widget's own views (TextClock / AnalogClock) run on the home screen; the picker's new heading | |

**For Fable: the lock-screen wallpaper and glass (not changed, as the user asked).** With "Also on the lock screen" on,
after changing the wallpaper the glass sometimes turns frosted or flat; with it off, glass stays liquid. Nothing lowers
the glass quality at run time (only the benchmark writes `GlassCap`), so the quality itself does not change. What
does make glass look frosted or flat is the launcher no longer drawing its own wallpaper copy: glass then has no
picture to refract (D73's symptom, "managed by launcher: no"). The likely path: `WallpaperStore.writeSystem` calls
`setStream(…, FLAG_SYSTEM | FLAG_LOCK)`; on MagicOS the lock-screen part (its magazine or theme engine) may re-set or
re-number the system wallpaper after the 8 s grace, and `WallpaperTones.current` then sees an id that is neither the
launcher's nor seen before, confirms it 1.5 s later and declares "wallpaper changed outside the launcher". To check on
the phone: the debug log for "wallpaper changed outside the launcher (id …)" after a lock-screen write, and Diagnostics
→ Wallpaper → "managed by launcher". Possible directions: write the lock screen in a separate `setStream(FLAG_LOCK)`
call before the system one and remember both ids as the launcher's own (`ownIds`), or treat a change of the lock id
alone as not a change of the home wallpaper.

**Result:** released as **0.7.0 build 51** (runs 49–51; 49 was replaced by 50, 50 failed only on the picker's search
field being hidden from accessibility, 51 green). The transition shaders compile on the emulator (check 15), search
follows the finger there ("search: released at 0.47"). Placing another app's widget was verified in 0.7.1's runs
(entry 17).

### For the next model (review)
1. Other apps' widgets were tested with the emulator's Clock widget only; widgets with set-up screens, scrolling lists
   and the work profile need the phone.
2. A long press on an app widget starts edit and move through the item's own gesture detector, but the widget may also
   receive the touch's end as a tap; Launcher3 cancels the view's touch on long press. Worth checking on the phone.
3. Search follows the finger only when "Swipe down" is "Open search".

## Entry 17: 0.7.1, search as part of the home screen, widgets in search, Ripple by default (2026-11-15, Opus 5.5)

**Asked by the user (same evening):** make swipe-down search feel like part of the home screen the way Fable's drawer
does, the pill turning smoothly into the search bar just above the keyboard; be able to add widgets there; smooth
and fast like the drawer, with zoom or something customisable. They liked the Ripple wallpaper transition.

| File | Change | Decision |
|---|---|---|
| `PillMorph.kt` (Fable's) | The morph follows `max(drawer, search)`; in search it holds `SearchPillField` instead of the drawer's field; dragging the field up puts search away | D104 |
| `SearchOverlay.kt` | `SearchPillField`; `BottomSearch` (results upwards from the field, frequent apps above it, widgets above them); Zoom; the query lives in `HomeController.searchQuery` | D104 |
| `HomeScreen.kt` | Search under the morphing pill; the pill no longer fades for search; Zoom on the home screen | D104 |
| `Models.kt`, `AppWidgets.kt`, `HomeController.kt`, `Widgets.kt`, `HomeGrid.kt` | `HomeLayout.search`; picker target ("page" / "search"); add, move, resize, remove search widgets; `WidgetContent` shared by pages and search | D105 |
| Settings | "Opening search" gains Zoom; Ripple is the default wallpaper transition | D104 |
| `ci/smoke.sh` | 10b: with nothing typed the search screen offers "Add widget" | |

Changed in Fable's `PillMorph.kt`: the progress it reads (drawer or search), the content in search, and the drag on
the open field. The drawer's own behaviour is unchanged.

**Icons lagging after several wallpaper changes (reported with a log, same night).** The log showed the transition
shaders' warm-up, which runs once per screen, again a few seconds after each system wallpaper hand-over: the home
activity was being rebuilt. On Android 12+ a new system wallpaper recolours the system theme (Material You) through
a resource overlay, an "assets paths" configuration change the activity did not declare, so Android restarted it and
everything (icons, wallpaper, glass) was drawn anew, often while the next wallpaper change was running. 0.7.1
declares `assetsPaths|colorMode` (D106) and logs "screen created again" / "system theme colours changed: the screen
is kept". Not changed: the second recolouring when the system reports its colours (Fable's D63 path).

**Result:** released as **0.7.1 build 56**: https://github.com/snietsk6205/liquid_launcher/releases/tag/v0.7.1. On
the emulator: swipe-down search with the pill as its field and the calculator, "Add widget" on the empty search
screen, every study check, the transition shaders, and, for the first time, another app's widget end to end: the
Clock app's Analog widget asked for permission ("Create"), opened its own set-up screen ("Select a Clock Face"),
and was placed (3 × 2). Runs 53–55 found the way there: run 53 placed nothing and said nothing (now every step is
logged), run 55 showed the set-up screen left open by the test, run 56 picks a face. Not verified: the screen no
longer being rebuilt after a wallpaper hand-over (the emulator does not recolour its theme; the log line will tell
on the phone), widgets on the search screen with a real app widget, Zoom on the phone.

## Entry 18: 0.7.2, search flows to the keyboard, squishy presses, Claude in the launcher (2026-11-16)

**Asked by the user (after 0.7.1 fixed the wallpaper and icon lag):** swipe-down search still did not feel part of the
home screen; the field went down to the bottom and then up again with the keyboard, with no setting to change it.
With "Open the keyboard" on, it should flow straight to the top of the keyboard, with blur and zoom like the drawer's
default. Buttons should squish or bounce when pressed. As a designer of transitions and liquid motion: improve the
whole experience, suggest and build better ways for the launcher to work with Claude, then build 0.7.2.

**Why the field went down and up.** 0.7.1's morphing pill measured its open place as 14 dp above the navigation bar
and rode on `imePadding()`. The keyboard is only asked for once search is opening for good, so the field first slid
to the bottom with the swipe and was then pushed up by the keyboard's own animation. Android cannot say how tall the
keyboard will be before it shows, so 0.7.2 remembers it.

| File | Change | Decision |
|---|---|---|
| `HomeController.kt` | `keyboardHeight` (remembered in `state.keyboardHeight`), `expectKeyboard`, `searchFieldLine` (remembered height, moving to the real one in step with the keyboard), `searchInPill`, `searchField`; `morph()` ignores search when its field is at the top | D107 |
| `PillMorph.kt` | `FieldInsets` (navigation bar, keyboard, keyboard animation target, read while laying out), `fieldBottom`; the pill computes its whole distance from the bottom instead of `navigationBarsPadding().imePadding()` | D107 |
| `SearchOverlay.kt` | Rewritten around the field: `KeyboardWatch` (remember height at the end of each animation, follow the keyboard down when put away, give up after 3 s, taken back by a keyboard that comes later), content laid out above the field's final place and riding with it, "Lift"; `SearchResult.Claude`, `FieldModeIcon` (search ↔ Claude), `ClipboardChips`, `AskClaudeHint`, `SuggestionRow` (frequent apps pop in one after another with the finger), result rows slide when the ranking changes (`animateItem`), `PreviewKeyboard` for the settings preview | D107, D109 |
| `HomeScreen.kt`, `Drawer.kt`, `Previews.kt` | "Lift" for the home screen (falls back, fades, sinks a little) and the wallpaper (grows into its blur); `HomeMode.PreviewSearch` loops search in Settings → Search | D107 |
| `PressFeel.kt` (new), `Gestures.kt`, `Pill.kt`, `Controls.kt`, `HomeMenu.kt`, `AppMenu.kt`, `DrawerParts.kt` | `PressFeel` / `squishOn` / `squishClickable`; every icon through `iconGestures`, pill items, glass buttons, menus, tab buttons | D108 |
| `core/data/Search.kt`, `LauncherAction.kt` | `ClaudeQuery` (bangs `!c` `!claude` `!ai`, question detection in English and French), `LauncherAction.AskClaude` | D109 |
| `HomeController.kt`, `Study.kt`, `HomeGestures.kt` | `askClaude` (Claude app share or claude.ai `?q=` in a Chrome tab; the Custom Tab builder moved out of `openStudyLink`), `askClaudeAbout(block)`, `recentClip` / `clipText`; a swipe set to "Ask Claude" follows the finger | D109 |
| Settings | Search: Opening search (moved from Motion, "Lift" default), Search field, Home screen falls back by, Blur behind search (14 dp, moved from Wallpaper), Dim (40 %), Ask Claude, Ask Claude in, Offer what you just copied. Motion: Pressing icons and buttons, Press depth | |
| Tests | `ClaudeQueryTest` | |
| `ci/smoke.sh` | 10c: a question offers "Ask Claude"; the field's centre is within 260 px above the keyboard the launcher logged | |

Not changed: the drawer's field and its keyboard (it keeps `imePadding`; its own "Open the keyboard" is off by
default), Fable's drawer transition, the 0.7.1 widgets on the search screen, the settings app's own buttons (they are
Material and live in a module that does not depend on `feature/home`).

**Run 57** (build 57): unit tests and APK green; on the emulator every search check passed, including the new
ones: a question offers "Ask Claude", and the field's centre sat 108 px above the keyboard (keyboard top 1517 px,
height 883 px remembered). The log also showed the emulator's keyboard taking 2.7 s to come, after the 1.2 s wait had
already sent the field down: the wait is now 3 s and a late keyboard takes the field back. The one failure, 12b, was
the test's: the system launcher was in front when it began (a redirect was lost while the accessibility service
reconnected, "redirect in 260 ms" with no REDIRECT after it), so its long press opened the Pixel launcher's own widget
list. 12b now starts the launcher first when it is not in front. No release from run 57.

### Ideas offered to the user, not built
1. A Claude home widget: a glass card with the field and the last three questions, so a question starts from the home
   screen without a swipe.
2. Recent questions to Claude in search's Claude mode (kept on the phone), one tap to ask again.
3. Selecting text in any app → "Ask Claude" from the system text menu (`ACTION_PROCESS_TEXT`), sending it with the
   same prompt templates (explain, translate, summarise).
4. The evening check-in prepared for Claude: Today's blocks done, skipped and unmarked, as a draft the user finishes.
5. Keyboard that follows the finger: Android 11+ lets an app drive the keyboard frame by frame
   (`WindowInsetsController.controlWindowInsetsAnimation`), so a slow swipe could pull the keyboard up with the field.
   Needs testing with the phone's keyboard; too risky to ship untested.

### For the next model (review)
1. The keyboard line depends on the IME insets reaching the app with `adjustNothing`; the user's report (the field rode
   up with the keyboard in 0.7.1) says they do on the phone, and the emulator check 10c measures it there.
2. `imeAnimationTarget` is experimental in Compose; if a keyboard does not animate its insets, the line still ends at
   the right place, without the in-step glide.
3. `PressFeel` adds one graphics layer to every icon; it draws nothing at rest (scale 1), but a profile on the phone
   with a full page of icons would confirm there is no cost.

## Entry 19: 0.8.0, liquid that is more liquid: looks, touch effects, press motions, moving wallpaper (2026-11-16)

**Asked by the user (with screenshots of wmkeyboard's theme editor, their "nice liquid" keyboard theme and a log):**
before moving to the next phase, make the liquid glass more liquid and customisable like wmkeyboard's keyboard themes
(press effects with Amount, Speed, Brightness, Size and colour; key motions Sink to Fade), bring these into the
picture, give the buttons a nice default; improve the search transition, the popups, transitions and animations;
build, push, and say which phase this is, what exists and what is missing.

**What the log and the diagnostics showed.** Redirects: 47 of 47 confirmed, launch to first frame median 170 ms, p95
284 ms. Glass: full 81 fps at 90 Hz with 11 % of frames late, frosted and tint at 89 fps. Search: "no keyboard came,
the field goes down" five times between 09:05 and 11:04 (wmkeyboard is the keyboard; windows list `wmkeyboard`), the
keyboard height was remembered once (933 px), so the keyboard did come at other times: an intermittent lost request,
not a missing keyboard (D114).

**0.8.0**
| File | Change | Decision |
|---|---|---|
| `core/designsystem/Glass.kt` | `GlassStyle` gains saturation, brightness, dispersion, depth, rim colour, light angle, shadow, glow; `look()` keeps the old `vibrancy()` for the old values | D110 |
| `feature/home/HomeConfig.kt`, `Controls.kt` | `GlassExtras` applied to every surface and to liquid glass buttons; `MotionConfig` gains wobble and light | D110, D112 |
| `core/data/Looks.kt` (new), `feature/settings/LooksEditor.kt` (new), `MainScreen.kt` | Eight looks as chips at the top of Liquid glass and Effects and touch; the chip of the look in use stays lit until something is changed | D110 |
| `feature/home/TouchEffects.kt` (new) | `TouchFx` (downs, one splash per touch, energy), `TouchFxLayer` (12 kinds), `WallpaperFxLayer` (5 kinds), `EffectsDemo` for the preview | D111, D113 |
| `feature/home/PressFeel.kt`, `Gestures.kt` | Motions, wobble, tilt from the press position, light from the finger; a press throws its splash when it shows (a quick tap on release) | D112 |
| `feature/home/HomeScreen.kt`, `Previews.kt` | `LocalTouchFx`, the finger watcher on the screen, the splash layer over everything, the moving wallpaper inside the glass's layer; `HomeMode.PreviewEffects` | D111, D113 |
| `feature/home/SearchOverlay.kt` | `SearchKeyboard`: window focus, explicit show, three retries, log lines | D114 |
| `feature/home/PillMorph.kt` | `rememberLiquidStretch`: search's pill stretches the way it travels and wobbles as it lands | D115 |
| `feature/home/AppMenu.kt`, `HomeMenu.kt` | Menu grows from the icon and shrinks back into it, rows staggered; home menu pills spread like drops | D116 |
| `core/settings/LauncherSettings.kt` | Section "Effects and touch" (Touch effect, Where, Amount, Speed, Brightness, Size, Colour, Your colour, Press motion, Press depth, Wobble, Light from the finger, Moving wallpaper and its six options); Liquid glass gains eight options; button tint 12 %, rim 85 % | |
| Tests | `LooksTest` (every value fits its setting, a look leaves quality and colours alone), `PressStyleTest` (every motion's spring) | |

Not changed: the drawer's transition (Fable's, called good), the 0.7.2 keyboard line (D107), the redirect.

Verified here: the unit tests and a release APK build. Not verified: everything on the screen. The emulator in CI has
no GPU, so its glass is a tint and depth, rainbow edges and glow cannot show there; the splashes, motions, the moving
wallpaper and the keyboard retries need the phone.

### For the next model (review)
1. The moving wallpaper sits inside the layer glass samples, so with it on the pill and dock redraw every frame. The
   benchmark (81 fps for full glass) suggests it holds; if not, "Speed 0" or "None" stop it.
2. `TouchFx` counts one splash per touch by a counter of finger-downs; a press shown from a pretend down (the preview)
   uses `pretend`/`noteDown` first.
3. The keyboard retries check `rootWindowInsets.isVisible(ime)`; if a keyboard reports itself visible before it
   draws, a retry is skipped, which is harmless.
