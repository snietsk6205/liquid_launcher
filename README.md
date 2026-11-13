# Liquid Launcher

A personal Android launcher for the HONOR 90 Lite (MagicOS 9, Android 15, no root).

HONOR Home stays the default launcher so recents stays smooth. Liquid Launcher is a normal app that its own accessibility service brings to the front whenever HONOR Home would be shown.

Kotlin, Jetpack Compose, Material 3 Expressive motion, liquid glass, everything customizable, offline-first.

## Status

0.3.0 (Phase 2, part 1 of 3): hold empty space for the home menu (two glass pills you fill yourself; press and slide); wallpaper screen with photo picker, recent wallpapers, and blur and dim for home, drawer and search, previewed live; saved layouts you can switch between in one tap; backup and restore to one file.

0.2.2 (Phase 1B polish): curved letter index in the drawer with a bubble and continuous scrolling; icons that follow the wallpaper colours (themed icons, colour-following icon packs); floating drawer tabs (top, bottom, inside the search pill or hidden) with a per-tab menu and search filtering.

0.2.1 (Phase 1B): real home screen with pages (reorder, home page), free grid, resizable clock and drag and drop with undo; customizable liquid-glass pill that turns into the drawer's search field; app drawer with depth / slide / fade transitions, glass tabs, glass scroll bar and scroll effects; glass long-press menu; swipe-down search with calculator and bangs; per-surface glass settings and live previews. Tags and your own drawer tabs built from them; icon packs (separate for home and drawer, uncovered icons fitted to the pack) and a per-app icon picker. Redirect: strict home check when leaving recents and recovery when a redirect covers recents. Folders, per-icon swipe actions on home icons, and app shortcuts in the long-press menu.

## Install

Download the APK from the [latest release](https://github.com/snietsk6205/liquid_launcher/releases/latest) on the phone and open it. Then open Liquid Launcher and follow **Settings → Setup checklist**. New builds install over the old one.

## Build

GitHub Actions builds every push: unit tests, a signed release APK, then a test on an Android 14 emulator (Android 15 images refuse to enable the accessibility service from adb) that checks the redirect against the emulator's launcher, and finally a release. Locally: `./gradlew testDebugUnitTest :app:assembleRelease` (needs the Android SDK).

## Project layout

| Module | Content |
|---|---|
| `app` | Application class, home activity, manifest |
| `core/common` | Debug log, latency statistics |
| `core/settings` | The settings registry (every option declared once) and its store |
| `core/data` | Apps, home layout with undo, launch counts, actions, pill items, search (fuzzy, calculator, bangs) |
| `core/designsystem` | Theme, liquid glass surfaces, launcher-managed wallpaper, icons |
| `feature/redirect` | Accessibility service, redirect state machine, recents detection, calibration, Quick Settings tile |
| `feature/home` | Home screen, pill, drawer, long-press menu, search, settings previews |
| `feature/settings` | Settings screens, setup checklist, diagnostics, glass benchmark, debug log |
| `ci` | Emulator test script and helpers |

## Design documents

| # | Document | Content |
|---|---|---|
| 1 | [Feasibility](docs/01-feasibility.md) | What works without root, what is risky, what is cut |
| 2 | [Screens](docs/02-screens.md) | Home, edit mode, drawer, search, panels, folders, transitions |
| 3 | [Settings tree](docs/03-settings-tree.md) | Every section and option |
| 4 | [Widgets](docs/04-widgets.md) | Built-in widgets, AppWidgets, placing, resizing, theming |
| 5 | [Study integration](docs/05-study-integration.md) | Tasks sync, `plan.json` / `state.json` schema, calendar |
| 6 | [Roadmap](docs/06-roadmap.md) | Phases and what to test in each |
| 7 | [Questions](docs/07-questions.md) | Open questions with the default I use if unanswered |

Decisions taken along the way: [DECISIONS.md](DECISIONS.md).

## Credits

- [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (Apache-2.0): glass effects
- [Chethan616/Artefact](https://github.com/Chethan616/Artefact) (MIT or Apache-2.0): reference for glass surfaces and motion
- [wasi-master/wmkeyboard](https://github.com/wasi-master/wmkeyboard) (MIT): reference for the customization system and module structure
