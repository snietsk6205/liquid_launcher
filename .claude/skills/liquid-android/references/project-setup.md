# Project setup: modules, versions, checks, Android gotchas

Contents: 1 Modules · 2 Versions and build · 3 Signing and releases · 4 A local type check without the Android SDK
· 5 Tests · 6 Android gotchas met in Liquid Launcher

## 1. Modules

```
app                 Application class, activities, manifest, wiring (frame meter, clock refresher)
core/common         plain Kotlin (no android.* where possible): debug log, notices, time, math, pure logic + tests
core/settings       the settings registry, store, map, migrations (+ tests: every option placed, no dangling keys)
core/data           data, files, search, parsing (JSON written whole and renamed) + tests
core/designsystem   theme, glass, icons, the settings kit, wallpaper/background store
feature/<name>      screens (home, settings, …), each depending on core modules only
```

- **Core logic avoids `android.*`** so it tests on the plain JVM in seconds (D17). Pass Android things in.
- **No DI framework**: constructor injection and a small hand-written app graph in the Application class (D18).
- Settings in DataStore Preferences; other data as JSON files (kotlinx.serialization) with a version field; nothing
  that needs annotation processing unless it pays for itself.
- A module-boundary lint (a short Python script) catches imports across modules that a one-pass compile cannot.

## 2. Versions and build

- Kotlin 2.x with the Compose compiler plugin; Compose UI/foundation/animation, Material3 (Expressive motion).
  Liquid Launcher at 0.15.3: AGP 9.3.2, Kotlin 2.4.10, Compose 1.12.1, Material3 1.4.0, activity 1.13.0, DataStore
  1.3.0, coroutines 1.11.0, serialization 1.13.0, backdrop 2.0.1, CameraX 1.6.2.
- **Pick stable versions at least two weeks old**: check the release date on Google Maven
  (`https://dl.google.com/android/maven2/<group path>/<artifact>/maven-metadata.xml`, then the POM's
  `last-modified`), e.g. CameraX 1.7.0 was five days old, so 1.6.2 was used.
- Version catalog (`gradle/libs.versions.toml`); `app.versionName` in `gradle.properties`; `versionCode` passed by
  CI as the run number (`-PversionCode=${{ github.run_number }}`), so every build installs over the last.
- **minSdk 33, targetSdk 33 unless a feature needs more, compileSdk latest** (D16, D36): a sideloaded personal app
  gains nothing from newer enforced behaviour (forced edge-to-edge, background limits) until it needs an API.
  minSdk 33 gives RuntimeShader (AGSL) for the glass.
- Release builds: R8 on (`isMinifyEnabled = true`); libraries ship their own keep rules.
- Strings may live inline next to their option declarations until translation is wanted (D26).

## 3. Signing and releases

- A private repo can carry its release keystore so every CI build is signed the same way and installs over the
  previous one (D19); rotate it if the repo ever becomes public.
- Releases are GitHub Releases tagged `v<versionName>`, one APK per release named
  `App-<version>-build<run>.apk`; a newer build of the same version replaces the APK in that release (D27).
- Everything about the workflow (markers, caches, emulator test, release notes from CHANGELOG): the
  **github-fast-ci** skill.

## 4. A local type check without the Android SDK

Cloud sessions may not be able to run Gradle (Google's Maven was blocked in early sessions; even when reachable, a
full Android build there is slow). Liquid Launcher's `tools/typecheck/` compiles every Kotlin source in ~40 s
against JetBrains' Compose **desktop** jars, Robolectric's `android-all` jar and small stubs:

- `fetch.sh` downloads a fixed jar set from Maven Central once per session (Kotlin compiler + Compose compiler
  plugin, Compose desktop, android-all, backdrop's desktop jar, JUnit), with retries.
- `tc.sh` compiles everything with stubs, prints "N errors, M new" against `baseline.txt` (the known gaps of the
  desktop APIs, e.g. `painterResource(Int)`, dynamic colour schemes) and runs the module lint. Only new errors
  matter.
- `ut.sh [TestName…]` compiles the plain-JVM modules with their tests and runs JUnit.
- `stubs/*.kt`: just enough of the AndroidX APIs the code uses (activity, datastore, core, `LocalContext`,
  `LocalView`, `AndroidView`, CameraX…), written from the libraries' real sources (download the
  `-sources.jar` from Google Maven to get signatures right).
- A clean result is not a build: resources, manifests, AGP and Android-only APIs are checked only by CI. Push with
  `[smoke]` when behaviour needs the emulator.

For a new project, copy `tools/typecheck/` from Liquid Launcher, change the module list and the jar set, record a
new baseline (`tc.sh baseline`) and keep it in the repo.

## 5. Tests

- JUnit 4 on the plain-JVM modules: parsing, maths, state machines, the settings map, migrations, naming. Include
  a locale test for anything written into file names: format numbers with `Locale.ROOT`, or a phone set to Arabic
  writes Arabic-Indic digits.
- The emulator test (shell, in CI) checks real behaviour end to end, waits for conditions instead of sleeping, and
  ends with each section's time and its slow waits; see the github-fast-ci skill.

## 6. Android gotchas met in Liquid Launcher

- Apps cannot read the system wallpaper since Android 13: draw your own background (D6).
- The Android 15 emulator image refuses to enable an accessibility service from adb: test such apps on API 34 (D35).
- A new system wallpaper on Android 12+ recolours the theme through a resource overlay and restarts the activity
  unless it handles `assetsPaths|colorMode` in `configChanges` (D106).
- MagicOS (HONOR): `WallpaperColors` callbacks come late or never (extract colours yourself); setting the system
  wallpaper takes 3-7 s and brings the system launcher forward (one writer, after the transition, D74).
- `delay()` counts uptime, which stops in deep sleep: clocks need the wall clock (D168).
- `Window.getInsetsController()` in `onCreate` before the decor view exists throws on Android 14: touch
  `window.decorView` first (0.15.3, caught by the emulator test).
- Declaring `CAMERA` makes `ACTION_IMAGE_CAPTURE` throw until the permission is granted; without it, open the
  camera app (`INTENT_ACTION_STILL_IMAGE_CAMERA`) and pick the photo after (D196).
- Writing into `Pictures/<folder>` through MediaStore with `RELATIVE_PATH` needs no storage permission (D132).
  Give camera apps a FileProvider file to write into, not a pending MediaStore entry (D163).
- Android grants at most 200 dp of gesture exclusion per edge: an edge control taller than that loses Back (D131).
- Keyboard: `KeyboardOptions(showKeyboardOnFocus = false)` + `controlWindowInsetsAnimation` to move it with the UI;
  the system may cancel the control (Back): respect it (D194).
- An accessibility service should subscribe to as little as possible (packages, event types) for battery (D2, D29),
  and never do slow work on its event thread (D39).
- Keep the main activity `singleTask` and handle Home presses by `onNewIntent`; a separate activity opened from it
  lives in its task and is cleared when the main activity is brought back.
