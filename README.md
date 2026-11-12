# Liquid Launcher

A personal Android launcher for the HONOR 90 Lite (MagicOS, Android 13, no root).

HONOR Home stays the default launcher so recents stays smooth. Liquid Launcher is a normal app that its own accessibility service brings to the front whenever HONOR Home would be shown.

Kotlin, Jetpack Compose, Material 3 Expressive motion, liquid glass, everything customizable, offline-first.

## Status

Design phase. No code yet.

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
