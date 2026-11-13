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
