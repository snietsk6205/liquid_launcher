# Decisions

Choices made without asking, newest at the bottom. Each one says what was chosen, why, and what it would cost to reverse. Items marked **(open)** have a matching question in `docs/07-questions.md`; they are reversible until the phase named.

| # | Date | Decision | Why | Cost to reverse |
|---|---|---|---|---|
| D1 | 2026-11-12 | Design docs live in `docs/` as Markdown, numbered 01–07 to match the seven requested deliverables. | Readable on the phone in GitHub, versioned with the code. | None |
| D2 | 2026-11-12 | The redirect service subscribes to accessibility events from HONOR Home and from the launcher only; content-change events only while HONOR Home is in front. | Battery: no wake-ups while you are in other apps. | Low |
| D3 | 2026-11-12 | Recents detection is data-driven (id list, id substring, class names) with a calibration wizard, instead of one hard-coded view id. | Survives MagicOS renaming ids. | Low |
| D4 | 2026-11-12 | A loop guard pauses the engine after 4 redirects in 3 seconds. | The worst possible failure is a phone where recents cannot be opened. | Low |
| D5 | 2026-11-12 | Launcher activity is `singleTask`, excluded from recents, launched with no window animation; the launcher animates its own content in. **(open, Q1)** assumes HONOR Home's home screen is empty. | Hot start in a few frames, hand-over looks like plain wallpaper. | Low |
| D6 | 2026-11-12 | The launcher draws the wallpaper itself ("launcher-managed" mode) by default. **(open, Q2)** | The glass library can only refract content drawn by the app, and Android 13 does not let apps read the system wallpaper. | Medium: other modes exist as settings, but glass quality differs |
| D7 | 2026-11-12 | Screen-off trigger defaults to "always", 1000 ms, matching the existing macro. **(open, Q3)** | Replicate current behaviour first. | None (setting) |
| D8 | 2026-11-12 | Glass has three quality tiers (Full / Frosted / Tint) with automatic step-down, and is never applied per item in scrolling lists. | 90 Hz on a Dimensity 6020 is the main performance risk. | Low |
| D9 | 2026-11-12 | Deep shortcuts are replaced by static-manifest shortcuts, legacy shortcut providers, an activity picker and notification actions. No Shizuku. | Shizuku needs re-pairing after each reboot, which costs your time. | Low: can be added as an optional module |
| D10 | 2026-11-12 | All options are declared in one settings registry; settings UI, search, reset, presets, profiles and export are generated from it. | Hundreds of options stay maintainable and consistent. | High after Phase 1B, so it is built first |
| D11 | 2026-11-12 | Storage: settings in DataStore, layout / tags / usage / study cache in Room. Themes are versioned JSON (`.lltheme`, a zip with assets); backups are `.llbackup` zips with optional passphrase. | Typed, fast, exportable. | Medium |
| D12 | 2026-11-12 | Tasks sync uses two files with one writer each (`plan.json` by Claude, `state.json` by the launcher) in a separate private repo. | Removes merge conflicts; keeps the phone's token away from the source repo. | Low before Phase 5 |
| D13 | 2026-11-12 | Study mode is implemented as a profile switch, not as a separate feature. Friction stays separate. | One mechanism for layouts; fewer special cases. | Low |
| D14 | 2026-11-12 | Mindful friction applies to launches from the launcher only by default. **(open, Q17)** | Catching launches from recents needs events from all apps, which costs battery. | None (setting) |
| D15 | 2026-11-12 | Phase 1 is delivered as 1A (redirect engine + device diagnostics) and 1B (MVP). | Four device behaviours decide the architecture; a ten-minute test settles them before weeks of work. | None |
| D16 | 2026-11-12 | minSdk 33, targetSdk 33, compileSdk latest. | Matches the device; avoids behaviour changes that bring no benefit to a sideloaded app. Raised only for a concrete reason. | Low |
| D17 | 2026-11-12 | Gradle modules: `app`, `core/{common,model,settings,data,designsystem,icons,search,platform}`, `feature/{redirect,home,drawer,search,widgets,study,settings}`, `baselineprofile`. Core logic modules avoid `android.*` where possible so they test on a plain JVM. | Build speed, testability; mirrors the layering used by wmkeyboard. | Medium |
| D18 | 2026-11-12 | No DI framework: constructor injection with a small hand-written app graph. KSP only for Room. | Faster builds and startup; the object graph is small. | Medium |
| D19 | 2026-11-12 | Signing key committed to this private repo; APKs published as GitHub Releases. **(open, Q18, Q20)** | Every build installs over the previous one with no action from you. Must be rotated if the repo ever becomes public. | Low: a new key means one reinstall |
| D20 | 2026-11-12 | App name "Liquid Launcher", package `dev.liquid.launcher`. **(open, Q19)** | Needed for Phase 1A. | High after first install (reinstall, settings lost unless backed up) |
| D21 | 2026-11-12 | Network use is limited to GitHub sync, the updater and (opt-in) weather. No analytics, no crash reporting service; crashes go to the local debug log. | Offline-first, privacy. | None |
| D22 | 2026-11-12 | English UI first, all strings in resources. **(open, Q15)** | Fastest path; translation stays possible. | Low |
