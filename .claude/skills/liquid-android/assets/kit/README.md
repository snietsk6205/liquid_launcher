# The Liquid kit (a Compose library module)

Working code from Liquid Launcher 0.15.3, moved into one package (`liquid.kit`) so it compiles on its own (checked
with the launcher's type-check harness: the only error is the desktop-only gap `painterResource(Int)`, which is
correct on Android).

| File | What it gives |
|---|---|
| `settings/Setting.kt` | option types: Bool, Int (range, step, unit), Choice, Text, Color, StringList, Action |
| `settings/SettingsStore.kt` | in-memory values persisted to DataStore; `get/set/reset`, `observe`, `derive`, export/import, "changed", recently changed |
| `settings/AppSettings.kt` | the registry to replace (Feel, Speed, Haptics examples; keep `RecentlyChanged`) |
| `ui/SettingsKit.kt` | WM-Keyboard-style settings: `KitScaffold`, `KitSection`, `KitGroup`, `kitLoneCard`, `KitNavRow`, `KitCard`, `KitTile`, `KitSegments`, `KitChips`, `KitChip`, `KitSlider`, `KitSlide`, `KitFoldRow`, `kitFlight`, `kitStaggerIn`, locals `LocalKitScreen`, `LocalKitBackTo`, `LocalKitShared`, `LocalKitNav` |
| `glass/Glass.kt`, `glass/LiquidShader.kt` | `Modifier.liquidGlass(backdrop, shape, style)`, tiers, `GlassStyle`, `GlassTest` variants, the AGSL real-glass shader |
| `motion/Motion.kt` | `Motion` springs (Feel), `Presence` (overlays that leave as a picture, sheets with drag and predictive Back) |
| `motion/PressFeel.kt` | `Modifier.squishClickable`, `squishOn`, `PressStyle` |
| `motion/Stagger.kt` | `StaggerIn`, `rememberRevealCount`, `gapAboveWhenShown` |
| `diagnostics/DebugLog.kt` | the ring-buffer log with levels (plain Kotlin) |
| `diagnostics/Notices.kt` | the notice queue (adapt `NoticeKind` to the app) |
| `diagnostics/FrameMeter.kt` | the FrameMetrics frame meter |
| `diagnostics/Changelog.kt` | CHANGELOG.md parser for What's new |
| `Shims.kt` | `MotionScale`, `Haptics`, `FrameCost`, `LauncherIcon` / `LauncherIcons` |
| `src/main/res/drawable` | the five icons the kit draws |

## Install in a new project

1. Copy this folder into the project as `kit/` and add `include(":kit")` to `settings.gradle.kts`.
2. In `kit/build.gradle.kts`, set the versions to current stable ones (at least two weeks old) and the SDK levels.
3. `implementation(project(":kit"))` in the app (and in feature modules that draw settings or glass).
4. Rename the package if wanted: `sed -i 's/^package liquid.kit$/package com.you.app.kit/' $(find kit -name '*.kt')`
   and set `namespace` to match (`R` is the module's own).
5. Replace `AppSettings` with the app's options; get the store with `SettingsStore.get(context)` (one instance,
   loaded once; `SettingsStore.inMemory()` for tests and previews), and keep `MotionScale.speed`, `Motion.style` and `Haptics.enabled` in step with
   the Speed, Feel and Haptics options.
6. Settings navigation: one `SharedTransitionLayout { AnimatedContent(...) }` host providing `LocalKitShared`,
   `LocalKitNav`, `LocalKitScreen` (title, hue, icon, breadcrumbs) and `LocalKitBackTo` (see
   `../../references/motion.md` §6).
7. Glass: put `Modifier.layerBackdrop(backdrop)` (`rememberLayerBackdrop()`) on the background the glass samples,
   and `Modifier.liquidGlass(backdrop, RoundedCornerShape(50), GlassStyle.Default)` on the few floating surfaces.
8. Frame meter: `window.addOnFrameMetricsAvailableListener(FrameMeter(), Handler(Looper.getMainLooper()))` in the
   main activity, and set `FrameCost.label` as screens open and close; show `DebugLog` on a Diagnostics screen.

## Credits to keep

- `ui/SettingsKit.kt` follows WM Keyboard's settings (github.com/wasi-master/wmkeyboard, MIT, (c) 2026 Wasi Master).
- `glass/` builds on Kyant0's AndroidLiquidGlass `backdrop` library (Apache-2.0).
