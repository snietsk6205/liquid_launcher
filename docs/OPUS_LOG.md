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

## Session summary (2026-11-13, Opus, updated 17:35 UTC)

**State:** Phase 1B is complete as 0.2.1: the fixes from the 0.2.0 phone test (redirect, drawer, pages, clock) plus everything that was left in 1B (tags and own drawer tabs, icon packs and icon change, folders, per-icon swipe actions, app shortcuts). 85 unit tests pass. CI run 24 passed every emulator check (redirect and UI) and published `v0.2.1` build 24 (`LiquidLauncher-0.2.1-build24.apk`). Runs 22–23 failed on emulator timing; the fixes (drag position, velocity samples, blur fallback, test waits) are in Entry 5.

**Builds:** `./gradlew testDebugUnitTest :app:assembleRelease` locally (see Entry 1). CI publishes `v0.2.1` when the emulator test passes; newer builds replace the APK in that release.

**Untested on the phone:** everything new in 0.2.1, in particular the reopen-recents recovery (D38), the strict left-recents check (D37), the morphing pill, scroll bar dragging, resize handles, icon packs with a real pack, folders and swipe actions.

**Next step:** the user tests 0.2.1; then 1B polish from that feedback (dragging apps out of folders is the known gap), then Phase 2.

### For Fable to review (overall)

1. D37–D39 in the redirect engine and the reopen-recents recovery.
2. The drawer rewrite (`Drawer.kt`, `DrawerParts.kt`, `PillMorph.kt`): glass parts moved by layout and redrawn by a draw-phase read.
3. `IconThemer` / `IconPacks` against a few real icon packs.
4. The gesture detector change in `Gestures.kt` (item swipes before the long press).
5. The saved-layouts proposal in the roadmap.
6. Earlier items still open: home-press heuristic in `MainActivity.onNewIntent`; emulator on API 34 (D35).
