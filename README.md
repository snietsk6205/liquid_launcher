# Liquid Launcher

A personal Android launcher for the HONOR 90 Lite (MagicOS, Android 13, no root).

HONOR Home stays the default launcher so recents stays smooth. Liquid Launcher is a normal app that its own accessibility service brings to the front whenever HONOR Home would be shown.

Kotlin, Jetpack Compose, Material 3 Expressive motion, liquid glass, everything customizable, offline-first.

## Status

Phase 1A: the redirect engine, a temporary home screen, the settings app, device diagnostics and the glass benchmark. The real home grid, drawer, icon packs and search come in Phase 1B.

## Install

Download the APK from the [latest release](https://github.com/snietsk6205/liquid_launcher/releases/latest) on the phone and open it. Then open Liquid Launcher and follow **Settings → Setup checklist**. New builds install over the old one.

## Build

GitHub Actions builds every push: unit tests, a signed release APK, then a test on an Android 13 emulator that checks the redirect against the emulator's launcher, and finally a release. Locally: `./gradlew testDebugUnitTest :app:assembleRelease` (needs the Android SDK).

## Project layout

| Module | Content |
|---|---|
| `app` | Application class, home activity, manifest |
| `core/common` | Debug log, latency statistics |
| `core/settings` | The settings registry (every option declared once) and its store |
| `core/designsystem` | Theme, liquid glass surfaces, launcher-managed wallpaper, icons |
| `feature/redirect` | Accessibility service, redirect state machine, recents detection, calibration, Quick Settings tile |
| `feature/home` | Temporary home screen |
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
