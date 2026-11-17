# Liquid Launcher

A personal Android launcher for the HONOR 90 Lite (MagicOS 9, Android 15, no root).

HONOR Home stays the default launcher so recents stays smooth. Liquid Launcher is a normal app that its own accessibility service brings to the front whenever HONOR Home would be shown.

Kotlin, Jetpack Compose, Material 3 Expressive motion, liquid glass, everything customizable, offline-first.

## Status

0.9.9: on Sundays the search page shows the week in four numbers (blocks done of planned, focus minutes, absences, days to the next exam); a tap reviews the week with Claude.

0.9.8: in the drawer, the search field shows where your day is (tap the ring for the study hub) and has a one-tap Ask Claude; the tag button is bigger (its size is a setting).

0.9.7: the glass takes a touch of the colour of the block now or next; a board-photo button during and just after class saves straight into Pictures/ESSAI/<module>/ with the date.

0.9.6: the drawer's letter index is a short glass rail in thumb reach that never triggers Back: a few letters with dots, a drop marking where the list is, and a magnifier that turns like a wheel through the letters while you drag, with a tick per letter. The old full-edge index is still in Settings → Drawer index.

0.9.5: launcher-data from the evening check-in: exam dates (≈ until confirmed), an absence meter with one liquid tube per module (always UNVERIFIED until the rule is confirmed), your weighted average once the coefficients are known, and a daily flashcard that turns over on the search page; a local copy you can edit in settings works until the check-in writes it.

0.9.4: icons move better: a crisp squish and rebound or a subtle tilt with a highlight (or off), with intensity, speed and wobble; an optional wobble while editing, each icon at its own pace; and the icon you opened an app from lands softly when you come back. Study: a 20-minute rest after you get home, then one tap to start tonight's first block; from 22:30 the colours soften and the evening check-in is one tap under the clock.

0.9.3: the search page is study first: with nothing typed it stacks your widgets, today's timeline, the next exam's countdown (grey, amber from 14 days, red from 3 with hours; hold it for the next dates), the block now or next with one action and its next step, study commands and Ask Claude with your templates, and your frequent apps, from the field upwards; choose which and in what order in Settings → Search. The calendar is never cut off any more. Typing !next, !exam, !plan or !abs answers from your study calendars, and ?question goes straight to Claude. Closing the drawer or search halfway now turns the motion round smoothly instead of stopping first. In the morning the line under the clock says when to leave for the first class (red from 10 minutes before); five minutes after each class it asks "present?" (✗ writes a note for the check-in); and while a block has ended without a mark, the pill's study item pulses and the block offers to reschedule with Claude.

0.9.2: the pill's study item is yours to design: an icon, an app's icon, your own picture or a liquid circle that fills as blocks are done, in a frame of any icon shape, any size and colour (or the next block's), with the day as a ring around it; holding it writes a note for tonight. Every pill item can have its own tap, long press and swipes (Settings → Pill).

0.9.1: Today is now the study hub: it grows out of the pill (or the line under the clock) on a spring and shrinks back into it under the finger; the block now or next with its steps, today's timeline with your classes, the day as a ring, "I'm home" and "shift tonight", a note field for tonight's check-in, prompt templates, Ask Claude with your study header, and one big action right under the thumb. Double tap the clock to write a note. The line under the clock gains one button (focus, or ✓ for a block that ended).

0.9.0: going home during a phone or Messenger call opens Liquid Launcher (the old "do not redirect during calls" default is gone; a floating call window no longer hides the system launcher from the redirect); starting an app from the drawer or search puts the keyboard away at once and coming home shows a clean home screen, with no flash of the drawer and keyboard.

0.8.1: a Calendar widget on liquid glass (month with your events, next events, opens the calendar) for pages and the search screen; search has its own Bounce, Speed and Liquid stretch.

0.8.0: liquid that is more liquid, customisable like a keyboard theme. Looks set the glass, the buttons and the effects in one tap (Liquid, Nice liquid, Water drop, Frosted ice, Neon, Bubble, Calm, Glass of 0.7). Glass gains thickness, rainbow edges, an inner glow, colour and brightness controls, and coloured, angled edge light. Presses throw a touch effect (ripple, drop, light pool, ink, paint, colour flood, smoke, glint, pop, swoosh, specks, bubbles) and move the way you choose (squish, jelly, sink, grow, shrink, rise, tilt, pop, fade) with a light from the finger; colour orbs, bubbles, motes, snow or rain can move over the wallpaper. Search asks for the keyboard again when it does not come, its pill stretches like a drop on the way, and menus grow out of what you pressed. Settings → Effects and touch has a live preview.

0.7.2: search flows straight to the keyboard: the pill becomes the search field just above where the keyboard will be (its height is remembered), the home screen falls back into the blurred, growing wallpaper ("Lift", the new default) and the results grow out of the field, with a live preview and new options in Settings → Search. Icons, pill items, results, menus and buttons squish like a drop when pressed and wobble back (Motion → Pressing icons and buttons). Claude in the launcher: "Ask Claude" in search (questions first, `!c`, or tap the search icon to switch to Claude), an "Ask Claude" action for gestures, the pill and the home menu, Paste / Ask Claude for text you just copied, and "Ask Claude" on each study block in Today.

0.5.0 (Phase 2, part 3 of 3, the end of Phase 2): pinch and two-finger swipes, swipes along the side edges, double tap per home icon, any screen of an app as an action, a Motion and haptics section (ticks, how apps and search open, animation speed) and an in-app updater from GitHub releases.

0.4.2: themed icons change colour in the same frame as the wallpaper (coloured while drawing, palette worked out before the switch, previewed in the wallpaper screen), icons you picked follow too (or keep their colours), a round button opens the tag list with a liquid morph, and the letter column slides with your finger while the app list stays still.

0.4.1 (fixes after the first phone test of 0.4.0): filter the drawer by tag in one tap (tag chips, a searchable tag list, the filter shown on the search field, sideways swipes); a wallpaper library (recent without duplicates, favourites, linked phone folders) with placing by pinch and drag, parallax, a one-tap wallpaper strip in the home menu, shuffle and automatic changes; layout and wallpaper switches change the whole screen together with a transition; themed icons recolour at once when the launcher sets a wallpaper; the letter index floats over the list with equal margins and the list bends with your finger.

0.4.0 (Phase 2, part 2 of 3): theme with colours, fonts (or your own font file), clock font and weight, icon shapes, presets and theme files; folder styles, tap-to-open-first-app and dragging apps out of folders; a dock; per-page grid, labels and name; page transitions.

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
