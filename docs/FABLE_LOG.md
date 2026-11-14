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
