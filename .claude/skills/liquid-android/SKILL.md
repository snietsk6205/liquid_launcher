---
name: liquid-android
description: Med's house style and defaults for Android apps, distilled from Liquid Launcher (Kotlin + Jetpack Compose, 196 recorded decisions) - liquid-glass design, wallpaper-derived Material You colours, spring motion that follows the finger, smoothness rules measured on a real mid-range phone, a settings system where every option is declared once (registry, search, reset, backup, live previews, WM-Keyboard-style settings screens), diagnostics (debug log, frame meter, notices, What's new), the DECISIONS/CHANGELOG/session-log habits and the CI conventions, plus a ready-to-copy Compose kit module. Use this skill whenever starting, planning or extending an Android or Compose app for Med, adding a screen, a settings page, an animation, a glass/blur effect, haptics or a release, or when he says "liquid", "like the launcher", "same defaults", "smooth", "it lags", "WM Keyboard style", even if he does not name the skill.
---

# Liquid Android: Med's defaults for Android apps

Liquid Launcher is a Compose launcher built over many sessions for one person (Med) and one phone (HONOR 90 Lite,
MagicOS 9 / Android 15, 1080 x 2388, 90 Hz, density 3.0, a Dimensity 6020: a mid-range GPU). Everything here was
either asked for by Med, measured on that phone, or learned from a CI failure. Treat these as defaults: use them
unless the new app has a reason not to, and say so when you deviate.

## How to use this skill

1. Read the section below that matches the task, then the reference file it points to (only that one).
2. For a new app, follow "Starting a new app" at the end and copy the kit module from `assets/kit/`.
3. For CI, releases and saving time on GitHub, use the **github-fast-ci** skill (it has the workflow templates).

| Task | Read |
|---|---|
| Look, glass, colours, shapes, settings screen style | `references/design.md` |
| Animations, gestures, haptics, keyboard, transitions | `references/motion.md` |
| Lag, dropped frames, "make it smooth", measuring | `references/smoothness.md` |
| Options, settings screens, search, backup, migrations | `references/settings.md` |
| Logging, diagnostics, notices, What's new, docs habits, working with Med | `references/concepts.md` |
| Gradle modules, versions, signing, local type check without an Android SDK | `references/project-setup.md` |
| Ready code (settings, settings UI kit, glass, motion, diagnostics) | `assets/kit/README.md` |

## The quality bar (why the defaults look the way they do)

- **Smooth on a mid-range phone, measured, not guessed.** 90 Hz means 11 ms a frame. Every expensive effect has a
  cheaper tier, and the app measures its own frames (frame meter, speed check). The CI emulator draws in software:
  never judge smoothness from it.
- **Everything customisable, but the defaults are opinionated and good.** Each option has a sensible default, a
  one-line explanation in plain words, search keywords, a reset, and a live preview when it is visual.
- **Nothing fake.** No placeholder settings, no button that does nothing, no effect that only pretends. When Android
  or the phone makes something impossible, log why, tell Med, and build the best real alternative.
- **No data loss.** Settings keys never change once shipped; old values are migrated, never reset; data files are
  written whole and renamed into place; a broken file is kept aside, not deleted.
- **Offline first, private.** No analytics, no crash service; crashes and decisions go to a local debug log the user
  can export. Network only for what the user asked (updates, sync).
- **Plain words.** UI text and log lines are sentences a non-developer understands ("the keyboard opened 120 ms after
  search", not "IME_ANIM_END").

## Design in one screen (details: `references/design.md`)

- **Liquid glass** on a few surfaces at a time (pills, chips, buttons, sheets), never per item in a scrolling list.
  Three tiers: Full (refraction + blur + rim), Frosted (blur + tint), Tint (no shader). Glass only refracts what the
  app draws itself, so the app draws its own wallpaper/background (since Android 13 apps cannot read the system
  wallpaper). Implementation: Kyant0's `backdrop` library plus an AGSL "real glass" shader (`assets/kit/.../glass`).
- **Colours follow the wallpaper** (Material You / `WallpaperColors`), with a contrast check: text below 3:1 against
  what it sits on becomes a dark or light ink, unless the user fixed the colour.
- **Settings look like WM Keyboard's**: a hue per entry on 40 dp icon tiles, big headings (32 sp down to 19 sp)
  with breadcrumbs that fold into the bar, rows on cards stacked in groups (24 dp corners at the ends, 6 dp where
  two cards meet, 3 dp apart), segmented tabs, chips, a thick slider. The kit implements all of it.
- **Thumb first**: primary actions low, results nearest the thumb, stacks anchored at the bottom.
- Med dislikes glass boxes behind the clock and widgets, animations that start late or play after the fact, and
  anything slow. He likes WM Keyboard's style, Material You colours, glass on buttons, squishy presses, ripple-like
  transitions, iOS-smooth motion.

## Motion in one screen (details: `references/motion.md`)

- **One motion vocabulary** (`Motion`): `enter()` for sheets and menus, `pop()` for small things, `exit()` quick and
  without bounce, `leave()` for an overlay leaving as a picture of itself. One "Feel" setting: calm (damping 1.0,
  stiffness 440), liquid (0.78, 540, the default), lively (0.6, 640).
- **One speed setting** (`MotionScale`): durations divide by it, stiffness multiplies by its square.
- **Follow the finger**, then spring with the finger's speed; open past 40 % or above 800 px/s; most of the motion
  happens under the finger (the drawer needs 36 % of the screen height, not 100 %).
- **Interruptible**: a spring sent the other way starts from its current speed, never from rest.
- **Grow out of what was pressed, shrink back into it.** Overlays leave as a GPU picture of themselves (nothing live
  is redrawn while they go).
- **Presses squash like a drop** (height more than width, two springs out of step, wobble back).
- **At a physical stop, squash instead of overshooting** (the search pill squashes onto the keyboard).
- **Haptic tick** when something passes a threshold or snaps; all behind one switch.
- **Settings**: page push 340 ms on emphasized-decelerate (0.05, 0.7, 0.1, 1), the page behind drifts a third left;
  rows arrive staggered (220 ms each, 35 ms apart, 8 steps max, rising 6 dp).

## Smoothness in one screen (details: `references/smoothness.md`)

1. Read animation values in layout or draw (`graphicsLayer { }`, `Modifier.layout { }`, `drawBehind { }`), never
   while composing.
2. No live full-screen blur: blur a copy once, off the main thread, and fade it in.
3. Glass never per item in lists; scrolling content draws "without backdrop".
4. Record expensive layers only when their inputs change (size, picture, blur), not every frame.
5. Spread a screen's first composition over frames (one card per frame) and keep heavy surfaces composed but
   hidden when they must open instantly.
6. Fades: `CompositingStrategy.ModulateAlpha`, not an offscreen layer.
7. Derive booleans (`derivedStateOf`) so per-frame values do not recompose whole screens.
8. Lists: scroll by pixels near the target so prefetch works; never jump a whole screen of new rows in one frame.
9. Measure on the phone: a FrameMetrics frame meter that logs late frames by screen and phase, and a "speed check"
   that plays the main animations with each costly part turned off in turn. Build only what the numbers justify.

## Settings in one screen (details: `references/settings.md`)

Declare every option once in a registry (`Setting` types: Bool, Int with range/step/unit, Choice, Text, Color,
StringList, Action). Screens, search, reset, "changed" dots, export/import, backups and an AI-tuning export are
generated from it. A map places each key on an entry > page > tab (Layout, Look, Behaviour, More); a unit test fails
if an option has no page or a page names a missing key. Only non-default values are stored. Keys never change.

## Project habits (details: `references/concepts.md`)

- `DECISIONS.md`: one row per choice made without asking (D-number, date, decision, why, cost to reverse).
- `CHANGELOG.md`: `## <version> — <date>` then `### Added / Changed / Removed / Fixed`, written for the user; the app
  shows it as "What's new" and CI uses it as release notes.
- A session log with, per session: what was asked (quoted), a table of points (done / partly / not started), known
  limits, "Questions for Med", and the **exact next step**.
- Before big work, ask Med the few questions whose answers change the design, each with a recommended option first;
  tell him where any setting moved.
- Releases only when Med asks; CI publishes only from a commit whose message contains `[release]`.

## Starting a new app

1. Gradle: `app` + `core/common` (plain JVM, testable) + `core/settings` + `core/data` + `core/designsystem` +
   `feature/*`; minSdk 33, targetSdk 33 unless a feature needs more, compileSdk latest; no DI framework
   (`references/project-setup.md`).
2. Copy `assets/kit/` as a module `kit` (or split it into `core/*`), rename the package if wanted, and depend on it
   (`assets/kit/README.md`).
3. Replace `AppSettings` with the app's registry; wire `MotionScale.speed`, `Motion.style` and `Haptics.enabled`
   to its Speed, Feel and Haptics settings.
4. Build Settings from the kit (`KitScaffold`, `KitGroup`, `KitNavRow`, `KitSegments`, `KitSlider`, `KitChip`) with
   a hue per entry, and a settings search over title + summary + keywords.
5. Theme from the wallpaper (Material You), glass on the few surfaces that float, the app drawing its own background.
6. Diagnostics screen: device facts, the debug log (export as text), the frame meter's lines, a speed check.
7. `CHANGELOG.md`, `DECISIONS.md`, a session log, and the CI workflow from the **github-fast-ci** skill
   (build + unit tests on every push, emulator test on `[smoke]`, release on `[release]`).
