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

### Entry 2 addendum: releasing 0.5.1

- Run 35: compiled and passed all unit tests at the first attempt; the emulator failed 3 drawer checks. Cause: the
  emulator has no GPU, and with D72 full glass stayed on, so frames stalled and the test's swipe was read as 0 px/s.
  Fixed two ways (D84): the swipe falls back to its average speed, and a device whose full glass ran below 45 % of the
  refresh rate keeps the old cap on everything.
- Run 36: all checks passed. **0.5.1 build 36 released** (`v0.5.1`).
- The "service disconnected / connected" lines every few seconds in the emulator log come from the test's own screen
  dumps (`uiautomator dump` suspends accessibility services); they are not a fault.

---

## Entry 3: 0.6.0, feel, motion and design (2026-11-15)

| File | Change | Why |
|---|---|---|
| `feature/home/ScrollIndex.kt` (rewritten body), `HomeConfig.kt`, settings | String model (`stringWeight`, `stringReach`), one spring loop for the apex, bubble at the finger placed in the layout phase, touch strips on the right, left or both edges with `systemGestureExclusion` | D79, request 8 |
| `feature/home/Drawer.kt` (`contentLayer`, `overlayOffset`, `homeInDrawerTransition`, `wallpaperInDrawerTransition`), `HomeScreen.kt`, `HomeController.kt` (`drawerTravel`) | "One space" transition, finger travel, stiffer spring, wallpaper takes part | D80, request 7 |
| `core/common/Notices.kt` (+ test), `feature/home/NoticeHost.kt`, settings section "Glass notices" | Notice queue and host; events wired: layout switch, shuffle, wallpaper replaced elsewhere, tag filter, redirect paused / back, update found by the daily check | D81, request 9 |
| `feature/home/Motion.kt`, overlay call sites in `HomeScreen.kt` | `Motion` presets, `Presence` exits for search, folder, app menu, home menu, layouts sheet, wallpaper strip | D82, request 10 |
| `core/settings/LauncherSettings.kt` (groups, `advancedKeys`, `search`, `nearly`, `RecentlyChanged`), `SettingsStore.kt`, `feature/settings/MainScreen.kt` | Settings reorganised | D83, request 11 |

Approaches considered for the drawer (request 7), all kept selectable except the last:
1. **One space** (default): one tall canvas; home leaves upwards, apps arrive from below, wallpaper drifts. Cheapest and the most literal "continuous".
2. **Depth**: through the home screen into the apps (home grows and fades, apps come into focus from slightly larger).
3. **Slide**: a sheet under the finger, 1:1.
4. **Fade**.
5. Shared elements (home icons flying to their drawer places): not built. It needs every home icon measured in both places each frame and breaks down with folders and tabs; a later experiment at most.

Not verified on a device: everything in this entry is compiled, unit-tested where it is pure logic, and run through the
emulator smoke test only. Things to look at on the phone: the string's feel and the back gesture at the screen edge
(Android grants at most 200 dp of edge per side to an app; start the pull with a small vertical move if "back" fires),
frame rate of the "One space" transition, and the notice position over the clock.

Hooks left for Phase 5: `NoticeKind.Study` and `NoticeKind.Task` exist with their switches; nothing posts them yet.

### Entry 3 addendum

Run 37: compiled, all unit tests and all emulator checks passed at the first attempt. **0.6.0 build 37 released** (`v0.6.0`).

---

## Entry 4: 0.7.0, clock gallery (Phase 3, first part) (2026-11-15)

| File | Change | Why |
|---|---|---|
| `core/data/Models.kt` | `HomeItem.options: Map<String, String>?` | Per-widget options (D85) |
| `feature/home/Clocks.kt` (new) | `ClockStyle` (encode / decode over a base from the Clock settings), `ClockFonts` (system families and `files/fonts/*.ttf|otf`), `ClockFace` and the six faces; text is auto-fitted by measuring a template with the widest digits; `timeInWords` | D85 |
| `feature/home/ClockStudio.kt` (new) | The gallery sheet: live miniatures of the designs, tabs Text / Colours / Surface / Details, font import | D85 |
| `HomeController.kt`, `HomeGrid.kt`, `EditTools.kt`, `HomeScreen.kt`, `MainActivity.kt` | `clockStudio` draft state, `openClockStudio` / `closeClockStudio`, "Customise" chip on a selected clock, "Add clock" opens the gallery, font file picker | |
| `feature/home/Clock.kt` | Only `rememberMinuteTick` is left; the old fixed `ClockWidget` is gone | Replaced by `ClockFace` |
| Tests | `ClockStyleTest` (style round trip, fallbacks, words, and the index string's weight and reach) | |

Phase 3 state: clock gallery done. Still to build in Phase 3: a widget picker, other apps' widgets (AppWidgetHost: bind,
configure, resize), further built-in widgets (date, battery, media, note) and panels. The study widgets of Phase 5 are
built-in widgets too and will bring the picker with them.

Not verified on a device: how each face looks at the user's 5 × 3 clock size, the roll animation's clipping, glass text
fill on light wallpapers, sweep second hand cost (it redraws every frame while the home screen is visible; "Ticking" does not).

### Entry 4 addendum

Run 38 failed to compile: inside `ClockFace` the faces read `maxWidth` / `maxHeight` from within a `Column`, where the
`BoxWithConstraints` scope is not allowed (layout scope marker). Fixed by reading both at the top of the box. Run 39
carries the fix; see the release note in the session summary at the end of this file.

---

## Entry 5: 0.8.0, study companion over the calendar (Phase 5) (2026-11-15)

| File | Change | Why |
|---|---|---|
| `docs/05-study-integration.md` | Rewritten: comparison, decision, **event format version 1** with the rules for the assistant; the GitHub design moved to an appendix | D86 |
| `core/data/StudyFormat.kt` (+ `StudyFormatTest`) | Pure text handling: titles and status marks, checklist lines (plain text and Google's HTML), ticking one box in place, the launcher's status line; `StudyEvent`, `StudyDay` (current / next block) | D86 |
| `core/data/StudyCalendar.kt` | Reads `CalendarContract.Instances` for the chosen calendars (today, overdue days, tomorrow), classifies blocks / tasks / overdue; writes title and description back (an exception for one occurrence of a repeating event); content observer; focus timer (two timestamps in preferences) | D86, D88 |
| `feature/home/Study.kt` | `NowCard`, `TodayWidget`, `TodaySheet`, `WidgetPicker`, `StudyNotices`, `studyReport` (the text "Tell Claude" shares) | D87 |
| `HomeController.kt`, `HomeGrid.kt`, `HomeScreen.kt`, `MainActivity.kt`, `LauncherAction.kt`, `Pill.kt`, `HomeMenu.kt` | Actions Today / Tell Claude / Study plan / Focus timer / Widgets; `addWidget`; calendar permission request; refresh on resume | |
| `core/settings` | Section "Study companion" (calendars by name, write back, overdue days, notice lead, Claude app, plan link); home menu defaults gain Today and Widgets | |
| `feature/settings` | `StudyEditor`: access state, which calendar is used for what, button to switch on from the home screen | |
| `app/AndroidManifest.xml` | `READ_CALENDAR`, `WRITE_CALENDAR` | |

How to start on the phone: Home menu → Today (or add the "Now" widget) → "Switch it on" → allow the calendar. Blocks need
a calendar whose name contains "Study", tasks one whose name contains "Tasks" (both names are settings).

Not verified on a device (no calendar on the emulator test): reading instances on MagicOS, that Google's sync uploads the
edits promptly, exceptions of repeating events, and the Claude app's package name for the share (`com.anthropic.claude`
is the default; if it is wrong the phone's share sheet opens instead).

Open points for the user (collected, none blocks the work):
1. Should a block's notice also arrive as a real phone notification when the launcher is not in front? (Needs an alarm
   and the notification permission; D88.)
2. Should ticking the last task of a block mark the block done by itself? Now "Done" is its own tap.
3. Names of the two calendars your assistant uses, if not "Study" and "Tasks".


---

## Session of 2026-11-17 (Fable 5.2): take-over from 0.8.1

Working branch: `claude/exciting-knuth-s83frp`, fast-forwarded from tag `v0.8.1` (`492e59d`). `main` holds only the
first commit (a README); every release so far was built from a `claude/**` branch, and the updater reads GitHub's
"latest release", so releases from this branch reach the phone the same way. I did not push to `main` (the session may
only push to its own branch); see "Questions for Med".

Build path: no local Android build is possible in this session (the proxy refuses `dl.google.com`, which serves both
the Android SDK and Google's Maven repository), so every compile check is a CI run (push → Build workflow → unit tests,
release APK, emulator test, GitHub release `v<version>`; about 13 minutes). Nothing here was run on a phone.

### Audit of Opus decisions (entries 12–20 of `OPUS_LOG.md`, against what you asked for)

| # | Opus decision | Verdict |
|---|---|---|
| 1 | D89 ticks kept on the phone without write access; focus timer ends with its block | keep |
| 2 | D90 clock reads the icon palette only for the "tone" colour | keep |
| 3 | D92/D93 a text line under the clock + a "Study block" pill item as *the* study integration | redo (P2): you asked for a fast daily link, got a label. Kept as one surface, with a countdown and one action, next to a real hub |
| 4 | D94 Today panel (block cards, Tomorrow, Sync now) | fix (P2): becomes the hub's timeline; the panel no longer pops in from nowhere |
| 5 | D95 `GlassButton` + "Buttons" section with four styles | fix (P4): the styles are a fixed list; replaced by the theme engine's surfaces, old values migrated |
| 6 | D96 plan link through a Chrome tab / Claude app | keep |
| 7 | D97 letters bend past the finger, bubble past them | redo (P6): still on the screen edge, so Back fires |
| 8 | D99/D104/D107 search as "One space" / "Lift", pill becomes the field above the keyboard | fix (P3): the idea is right, the page it opens is empty and the calendar is clipped |
| 9 | D100/D105 other apps' widgets, widgets on the search page | keep; restyling added in P7 |
| 10 | D101 edge light off by default | keep |
| 11 | D103 wallpaper transitions as AGSL shaders | keep |
| 12 | D106 `assetsPaths` handled without a rebuild | keep |
| 13 | D108/D112 `PressFeel`: 11 press motions, wobble, tilt, light from the finger | redo (P9): most motions are noise; one crisp squish stays the base |
| 14 | D109 Ask Claude in search, `!c`, clipboard chips | keep; extended by D1–D3 of the spec |
| 15 | D110 "Looks" (Liquid, Nice liquid, Water drop, …) as bundles of slider values | redo (P4): "Nice liquid" only renames sliders of the old glass; it is not your keyboard theme. Replaced by a real shader and your imported theme |
| 16 | D111/D113 12 touch effects, 5 moving-wallpaper effects | fix (P4/P11): keep ripple, drop, light pool; the rest go behind "More effects" or are removed if they cost frames |
| 17 | D114 keyboard asked for again when it does not come | keep |
| 18 | D115/D118 liquid stretch of the search pill, search's own spring | fix (P3/P11): one spring vocabulary instead of per-feature sliders |
| 19 | D116 menus grow from the pressed icon | keep |
| 20 | D117 calendar widget | fix (P3): clipped on the search page (your screenshot 1), month grid does not fit its cell |
| 21 | Redirect: "Do not redirect during calls" left on by default (a Phase-1 setting of mine that Opus kept) | fix (P1a, done in 0.9.0) |
| 22 | Launch from the drawer closes it 250 ms later, keyboard left to the system | fix (P1b, done in 0.9.0) |

Earlier Opus work (entries 1–11) was reviewed in entries 1–3 above (0.5.1 and 0.6.0).

---

## Entry 6: 0.9.0, P1: the redirect during calls, and the drawer and keyboard that flashed on the way home (2026-11-17)

### P1a. Why going home during a call stayed on HONOR Home

Not the window list, not picture-in-picture: a setting. `exclude.inCall` ("Do not redirect during calls", on by
default, from Phase 1A) made `exclusionReason()` return "call in progress" whenever the phone's audio mode was
`MODE_IN_CALL`, `MODE_IN_COMMUNICATION` or `MODE_RINGTONE`. A cellular call sets the first, a Messenger / WhatsApp call
the second. The engine then retried once a second and never launched; the debug log said "not redirecting: call in
progress". Opus's log does not mention it.

| File | Change |
|---|---|
| `core/settings/LauncherSettings.kt` | `ExcludeInCall` (switch) replaced by `ExcludeCalls` (`exclude.calls`): "Redirect as usual" (default) / "Not while the phone rings" / "Not during any call". A new key, so the old default cannot survive in the stored settings |
| `feature/redirect/LauncherAccessibilityService.kt` | `exclusionReason()` follows the new setting. `probe()`: when the active window is not the system launcher within 3 s of the system launcher's own window event (or while the engine is already watching it), the window list is asked for the topmost *full-screen application* window; picture-in-picture windows, windows smaller than 90 % × 70 % of the screen, overlays, the status bar and the keyboard are skipped. If that window is the system launcher, it is probed instead. This covers a call shown as a floating window, chat head or picture-in-picture that was touched last and is therefore "active" |

Not changed: the 160 ms delay after HONOR Home appears, the going-home animation fast path, recents detection, the
reopen logic. The window list is only asked for in the cases above, never on the normal path (D39 still holds); a slow
answer is caught by the engine's existing "window check took N ms, checking again" guard.

Limits: none known from the OS side. An accessibility service may start activities during a call. If a redirect during
a call still fails on the phone, the debug log will now say either "not redirecting: …" (a setting) or show no
"system launcher appeared" line (then the window event itself did not arrive, and the log's `windows:` line at the
"tree" level will show what was in front).

### P1b. Why the drawer and keyboard flashed after coming home

From Android 13 the system takes the picture of a task that is going to the background at the *start* of the opening
animation of the app that replaces it, keyboard included (`Transition.onTransactionReady`: "take task snapshots before
the animation so that we can capture IME"). When the redirect brings the launcher back, that picture is shown as the
starting window until the launcher's first frame, and the keyboard is restored with it. The launcher closed its drawer
250 ms *after* starting the app, too late for the picture.

| File | Change |
|---|---|
| `feature/home/HomeController.kt` | `launch()` → `leaveForApp()`: the keyboard is hidden and the field's focus dropped at once; `tidyAfterLeaving()` closes menu, tag sheet, search and (if "Close after opening an app" is on) the drawer after 250 ms or when the launcher stops being visible, whichever comes first; `closesWhileAway`, `hideKeyboard()` |
| `feature/home/HomeScreen.kt` (`HomeReturnState`) | `closesWhileAway` and `onLeft` hooks between the activity and the home screen |
| `app/MainActivity.kt` | `onPause`: `RedirectStatus.staleSnapshot` = keyboard visible or something that will be closed; `onStop` and a redirect's `onNewIntent`: `onLeft()` |
| `feature/redirect` | `RedirectStatus.staleSnapshot`; the redirect intent carries an extra when it is set. AOSP's `ActivityRecord.allowTaskSnapshot()` refuses the old picture for an intent with extras that is not a plain launcher start, so the launcher's own first frame is the first thing shown |
| `app/AndroidManifest.xml`, `themes.xml` | `windowSoftInputMode="stateHidden|adjustNothing"` (the keyboard is not restored when the launcher comes forward), `windowDisablePreview` (no splash in place of the picture) |

When nothing was open the old picture is still used, as before: it is what makes the launcher appear before its first
frame (about 100 ms earlier), and it is identical to what the first frame will show.

Not verified on a device: both fixes. `allowTaskSnapshot()` is AOSP behaviour (13–15); if MagicOS changed it, the
state reset still makes the first real frame clean and only the system's one-frame picture could remain.
