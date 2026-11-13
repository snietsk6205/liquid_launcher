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

## Session summary (2026-11-13, Opus)

**State:** Phase 1B first part (0.2.0) is built. Local build and all 63 unit tests pass (redirect engine 35, layout 10, search 8, settings 7, latency 3). On the API 34 emulator every UI check passed in run 17 (drawer, app list, long-press menu, add to home, back, search + calculator, edit mode) and all redirect checks except the first Home press, which hit an emulator ANR dialog; run 18 retries that.

**Builds:** `./gradlew testDebugUnitTest :app:assembleRelease` locally (SDK in `/root/android-sdk`, see Entry 1). CI publishes release `v0.2.0` when the emulator test passes.

**Does not exist yet:** icon packs, folders, tags and custom drawer tabs, per-icon swipe actions, icon change, app shortcuts (all Phase 1B rest, see roadmap).

**Untested on the phone:** everything in 0.2.0, including the home-screen check in the redirect (Fable's `cc5e6e7`), which on the emulator worked ("home screen visible, redirect in 160 ms") but has no phone data yet.

**Next step:** user tests 0.2.0 on the phone (checklist in the hand-over message); then icon packs and folders.

### For Fable to review (overall)

1. `HomeController` + `Gestures.kt`: drag and drop and the remembered gesture modifiers.
2. The home-press heuristic in `MainActivity.onNewIntent`.
3. Emulator API 34 vs 35 (D35).
4. Defaults chosen in D31 (5 × 9 grid etc.) — check against the user's next feedback.
5. Glass on the long-press menu opened from the drawer samples the home layer, not the drawer.
