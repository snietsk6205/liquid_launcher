# Fable work log

Written by Claude Fable 5.2 from 2026-11-14, after Opus 5.5 built 0.2.0 → 0.5.0 (see `OPUS_LOG.md`).
Another model must be able to continue from this file, `06-roadmap.md` and `DECISIONS.md` alone.

Working branch: `claude/launcher-review-fixes-qbfmok` (fast-forwarded from `claude/zealous-babbage-qfp434`, tip `b0c94f4`).
CI builds and releases every push to `claude/**` (`.github/workflows/build.yml`).

## Build environment in the cloud session (not in the repo)

- Android SDK installed at `/root/android-sdk` (platform 37.0, build-tools 37.0.0); `local.properties` points there (git-ignored).
- Maven Central answered HTTP 429 (rate limit on the shared proxy address) on 2026-11-14, so a local Gradle build could not
  resolve its dependencies. Compile checks then go through CI, as in Phase 1A. A plain retry loop was left running.

---

## Entry 1: diagnosis of the 0.5.0 phone report (2026-11-14)

Source: the user's debug log (14:45 → 20:30), the diagnostics report, and the code.

### 1. Glass looks flat ("not liquid")

Two independent causes, both confirmed in code:

1. **The benchmark cap.** `SettingsStore.glassConfig()` caps every surface at `state.glassCap` when "Lower quality
   automatically" is on (default). The diagnostics run measured Full at 61 fps of 90 and stored `tint` as "best tier
   that holds the frame rate", so *all* glass became a flat translucent fill. The cap is rewritten each time the
   benchmark runs, which is why it came and went.
2. **The launcher dropped its own wallpaper.** Diagnostics: "Wallpaper managed by launcher: no". `WallpaperTones.current()`
   treats "system wallpaper id ≠ the id stored with our colours" as "changed outside the launcher" and stops drawing the
   launcher's copy. With overlapping writes (layout switches a few seconds apart; each `setBitmap` takes 3–7 s on MagicOS
   and they finish out of order) the stored id belongs to an older write, so the launcher misreads its *own* wallpaper as
   an outside change (log 20:28:54 "id 184 -> 185", 20:29:21 "id 182 -> 187"). Without its own image, glass has nothing to
   refract.

### 2. Themed icons not following / late

- Icons an icon pack draws were never recoloured (D45/D64, by design then). The user wants all icons to follow.
- Icons fitted to a pack and work-profile icons are rendered in colour per palette ("baked") on the data thread, so they
  change late.
- After cause 1.2 above the launcher has no colours of its own and waits for MagicOS's colour callback (3–7 s late in the log).

### 3. Lag and redirect confusion with several wallpapers

- `WallpaperManager.setBitmap` takes 3–7 s on this phone (log: "system wallpaper set +4294 ms", "+7352 ms") and writes
  queue up when layouts are switched quickly ("+19486 ms", "+22773 ms"): the system is busy the whole time.
- When the system wallpaper changes, HONOR Home comes to the front by itself (log 15:18:07, 20:29:20: "window:
  DrawerLauncher (home)" right after "system wallpaper set", with HONOR Home as the only window) and the redirect has to
  bring the launcher back, twice in one case. That is the "redirect reacts to the wallpaper change".

### 4. Recents

Measured from the log (58 appearances of HONOR Home with a trace):

| At the trigger | Home followed | Recents followed |
|---|---|---|
| HONOR's workspace drawn at 0.9 scale (its "going home" animation has started) | 20 | 0 |
| Workspace at full size | 14 | 24 |

- So the workspace scale is a positive sign of "really going home" that Phase 1A's home-screen check never had.
- When recents follows, its panel becomes visible 24–250 ms after the trigger (median about 55 ms; 3 of 24 later than 150 ms).
  A fixed 160 ms therefore still interrupts about one recents gesture in ten.
- Recovery misses most interruptions: once the launcher is in front, `evaluate()` returns early, so a recents panel that
  appears *after* the launcher resumed is never seen (log 15:28:09, 20:24:59: the user had to swipe again).
- A probe that waited 3 s for the system was still acted on (19:10:16: redirect over Facebook, 3062 ms after the trigger).

### 5. Pause

"Pause for 5 min" in settings only pauses; it does not open HONOR Home.

---

## Entry 2: 0.5.1, review of Opus's work and the six bugs (2026-11-14)

| File | Change | Why |
|---|---|---|
| `core/common/WallpaperTones.kt` | Own writes are tracked (`beginOwnWrite` / `endOwnWrite` / `adopt` / `rebase`, `ownWriteActive`); an id counts as "changed elsewhere" only with no write of ours running or just finished, when it is not one we wrote, and when seen twice 1.5 s apart | Diagnosis 1.2: the launcher dropped its own wallpaper |
| `core/designsystem/WallpaperStore.kt` | `persist` writes the file at once and queues the system hand-over: one writer, newest picture wins, a picture the system already has (content hash) is skipped, `setStream` from the JPEG instead of `setBitmap`; modes `Now / Soon (3 s) / ScreenOff / Never`; a waiting write survives a restart and is flushed at screen off | Diagnosis 3 |
| `core/data/IconArt.kt`, `IconThemer.kt`, `ThemedColors.kt`, `LauncherData.kt`, `feature/home/IconPaint.kt` | Every themed icon is `IconArt` coloured while drawing: pack icons and fitted icons too (`IconArt.of`: line icons → `Tint`, others → `Duotone`), work apps keep their badge as an overlay; the "baked per palette" path and its cache are gone; `ThemedIcons` no longer carries colours, so a new wallpaper never rebuilds the app list | Diagnosis 2 |
| `feature/home/HomeConfig.kt` (`glassConfig`, `GlassConfig.largeQuality`) and the sheet call sites | The user's quality choice applies everywhere; the benchmark cap only turns *large sheets* frosted | Diagnosis 1.1 |
| `feature/home/TagFilter.kt` | The tag chip row is one glass surface; chips are plain | D8 (was one glass per chip) |
| `feature/home/HomeScreen.kt`, `WallpaperViews.kt` | Blur behind the drawer / search = a blurred copy of the wallpaper (made once) fading in; the live full-screen blur is an option (`drawer.blurLive`, off) | It was the most expensive part of the drawer transition and was switched off by the cap |
| `feature/redirect/engine/RedirectEngine.kt` + tests | Fast path when the system launcher's going-home animation is seen (`Probe.homeAnimating`); "unsure" delay 260 ms (new key `redirect.unsureDelay`); a look that took ≥ 400 ms is repeated, not acted on; recents appearing *behind* the launcher within 900 ms of an uncertain redirect reopens recents (`onActivityBehindLauncher`, verified through the event's source window, or as a hint); instant redirect when HONOR Home comes forward during our own wallpaper write (`homeBounceExpected`); a pause ends by itself with a check (`PAUSE_END`) | Diagnosis 3, 4 |
| `feature/redirect/RecentsDetector.kt`, `LauncherAccessibilityService.kt` | `homeAnimating` = first home id narrower than its window; events kept during the afterglow and looked at on the engine thread; the unused "proven reliable" statistics logic removed | |
| `feature/settings/MainScreen.kt` | "Pause 5 min" opens the system launcher and pauses | Bug 5 |
| `core/data/WallpaperLibrary.kt`, `feature/home/WallpaperStudio.kt` | `LinkedFolder.deep` (sub-folders, 6 levels, 3000 pictures); "Sub-folders" and "Remove folder" chips above a folder's pictures | Bug 6 |

Decisions: D72–D78.

Not verified on a device (only unit tests and the emulator smoke test): the going-home animation signal on other MagicOS
versions, whether a covered HONOR window can still be read (`recentsBehind`), `setStream` timing on MagicOS, and the look
of tinted line icons. The debug log has lines for each ("going-home animation", "recents opened behind the launcher",
"managed wallpaper set … took N ms").
