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

---

## End of session (2026-11-17, cut short: the usage limit was reached during P2)

**Version shipped:** 0.9.0 (run 62 on `claude/exciting-knuth-s83frp`; it was still building when the session ended:
check that the release `v0.9.0` exists, and if run 62 failed, read its log first).

| Workstream | State |
|---|---|
| Audit | done (table above) |
| P1a redirect during calls | done, in 0.9.0, not verified on the phone |
| P1b drawer / keyboard ghost | done, in 0.9.0, not verified on the phone |
| P2 study hub | started: only `StudyFormat.kt` is changed (pushed with `[skip ci]`, **never compiled**): `withMark`, `mark`, `shiftedBy` for `— launcher: home HH:MM` and `— launcher: shifted +N min` (kept on their own lines, after the `P2-BLOCK` line; the status line stays last), `StudyDay.classes`, `notes`, `lastClassEnd`, `blocksDone`. Nothing reads them yet |
| P3–P11 | not started |

**Design decided for P2, not built yet** (so the next session does not start from zero):
1. Hub: `controller.todaySheet` stays the open flag; a new `controller.hub` Animatable follows it with a spring. The hub is one
   glass surface laid out between `controller.pillBounds` (recorded by `Pill` / `MorphingPill` with `onGloballyPositioned`) and
   its open rectangle inside a `layout {}` block reading `hub.value` (as `morphLayout` does), the pill fading out under it.
   Content: day ring (C2) and date; arrival / shift chip; the block now or next, open, with its ticks (reuse `BlockCard`, make it
   internal); the timeline (blocks, tasks, `TomorrowSection`); a fixed bottom bar in thumb reach: quick note field (D5),
   Ask Claude, prompt templates, Evening check-in. Keep the texts the emulator test taps: "Switch it on", "Done", "Tomorrow · ".
2. A2: `StudyCalendar.homeSign()` (home Wi-Fi name → needs `ACCESS_FINE_LOCATION`; charger 45 min after the last class;
   manual chip), checked on the minute tick in `StudyNotices`; `markHome()` writes the mark on the first block not yet over.
3. A3: a pure `StudyShift.plan(blocks, homeAt, latestEnd)` in `core/data` with tests (delta rounded up to 5 min; blocks that are
   open, untouched and not over move; past 23:00 the lowest-priority block, Catch-up / Checkpoint / else the last, is shortened);
   `StudyCalendar.applyShift()` updates `DTSTART` / `DTEND` and adds the `shifted` mark.
4. D5: `StudyCalendar.addNote()` inserts an all-day `Note: …` event in the Tasks calendar; `load()` must keep such events out of the task list.

**Known issues:** the P2 code in `StudyFormat.kt` is uncompiled; `StudyFormatTest` has no case for the new marks yet.

**Questions for Med**
1. Releases are built from `claude/**` branches; `main` only holds the first README commit. Should the code be merged into `main`? (Assumed: no, keep releasing from the working branch, as every earlier session did.)
2. "Lowest-priority block" for shortening an evening: assumed Catch-up, then Checkpoint, then the last block. Is there a better rule?
3. Is the timetable in your primary Google calendar? (Assumed yes, for "end of the last class".)

**Exact next step:** push any commit without `[skip ci]` to compile the `StudyFormat.kt` change, add its tests, then build the
hub as described above (P2), then P3 (search page transition and calendar clipping), then the motion pass.

---

## Entry 7: 0.9.1, P2 study hub, arrival and shift tonight (2026-11-17, written by Opus 5.5)

The user switched this session to Opus 5.5 after Fable's usage ran out and asked to continue Fable's work here. I kept
logging in this file (not `OPUS_LOG.md`) because it is the same session and the same plan. Fable's design notes above
were followed; where I changed them it says so.

State found: 0.9.0 (run 62) passed every unit test and every emulator check and was released as `v0.9.0`. Fable had left
uncommitted, uncompiled P2 code (`StudyShift.kt` and its test, arrival / shift / note functions in `StudyCalendar.kt`,
new settings, an arrival notice and a one-button clock line in `Study.kt`). I reviewed it, fixed two risks (the
`isPrimary` column, read through `OWNER_ACCOUNT` instead; `WifiManager.UNKNOWN_SSID`, not public API) and built the rest.

| File | Change | Spec |
|---|---|---|
| `feature/home/StudyHub.kt` (new) | The hub: `HubGeometry` (origin = the pill or what was tapped; target = above nav bar and keyboard, `Study hub height` of the screen), `hubBounds` / `hubContent` layout modifiers (only this surface re-measures per frame), corner from capsule to 30 dp, scrim with the hub's own progress, `draggable` + `NestedScrollConnection` so the finger can shrink it back into the pill from anywhere; content: header with day ring (C2), "Now / Next" block opened with its steps, timeline (B4: classes grey, blocks on a rail, a now line), arrival and shift callout (A2, A3), tasks, notes, tomorrow, sync state; bottom bar: Evening check-in and template chips (D2), note-or-question field (D5, D1), Ask Claude, and a main action under the thumb (I'm home / Focus / Mark done / Start / Evening check-in) | P2, A2, A3, B4, C2, D1, D2, D5 |
| `HomeController.kt` | `hub` Animatable, `openHub` / `closeHub` (spring keeps the current velocity, so an interrupted open or close never jumps), `dragHubBy` / `settleHub`, `openToday(from)`, `studyHeader` / `withStudyHeader`, `blockContext`, `askTemplate`, `eveningCheckin`, `addStudyNote`, `markHomeNow`, `applyShift`, `keepEvening`, `rememberHomeWifi`; leaving for another app through `start()` tidies like a launch (P1b) | |
| `HomeScreen.kt` | Hub composed while open or shrinking; the pill fades as the hub covers it (no slide away); the home screen sinks with the hub's own progress; Settings preview replays the opening when size or bounce change | D121 |
| `Study.kt` | Old `TodaySheet` removed (the hub replaces it); shared parts made internal; the clock line opens the hub from its own bounds and has one button (Settings: "Button on the line under the clock") | |
| `HomeGrid.kt` | Double tap on a clock widget opens the hub with the note field focused | D5 |
| `SearchOverlay.kt` | "Ask Claude" from search is sent with the study header | D1 |
| `core/data/StudyCalendar.kt` | Classes from the main calendar (or the named ones), notes kept out of tasks, `homeSign` / `markHome` / `applyShift` / `addNote` / `latestEnd` / `shiftAnswered` | A2, A3, D5 |
| `core/data/StudyFormat.kt`, `StudyShift.kt`, `StudyShiftTest.kt` | `withMark` / `mark` / `shiftedBy` (home and shifted lines after `P2-BLOCK`, status line still last); the shift planner | Part 0 contract |
| `LauncherSettings.kt` | Study: Notice when I get home, Home Wi-Fi name, The charger also means home, shift offer threshold, An evening ends by, Calendars with classes, Header of what is sent to Claude, Evening check-in message, Prompt templates, Study hub height, Study hub bounce, Button on the line under the clock | |
| `MainActivity.kt`, manifest | Location permission request (only from "This Wi-Fi is home"), `ACCESS_WIFI_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | A2 |

Calendar contract: unchanged for everything the check-in already reads (title marks, `[x]`, the status line). The two
new lines are exactly the spec's `— launcher: home HH:MM` and `— launcher: shifted +N min` (with " · shortened N min"
appended when a block was cut). A shift needs write access; without it nothing moves and a message says why.

Not verified on a device (CI has no calendar with classes, no Wi-Fi name, no GPU glass): the morph's feel, the hub's
glass on the phone, Wi-Fi arrival on MagicOS (needs location on), the charger rule, shifting a synced Google event.

### Questions for Med (added)
4. The Claude app package is assumed to be `com.anthropic.claude`, and shared text is assumed to pre-fill a new chat.
   If it does not, tell me: the fallback (copy to the clipboard, then open the app) is one line away.
5. "End of the last class" uses every timed event of your main Google calendar today. If exams or personal events
   sit there in the evening, name your timetable calendar in Settings → Study companion → Calendars with classes.

### Next step
Wait for this push's CI (0.9.1). Then, in the spec's order of what is left in P2: the pill's full customization (icon:
own picture / icon-pack glyph / vector / liquid circle / any shape; frame, size, tint, day-ring style; tap / long press /
swipe per slot), then P3 (search page: finger-tracking transition, the clipped calendar, study-first cards, card order).

---

## Entry 8: 0.9.2, the pill made yours (2026-11-17, Opus 5.5)

Result of entry 7: run 63 green (unit tests, every emulator check including the study checks against a real calendar),
released as `v0.9.1`.

| File | Change |
|---|---|
| `feature/home/PillStudy.kt` (new) | `StudyItemLook`, `StudyPillItem`: glyph / app icon / picture / liquid circle / text; frame in any icon shape; colour from the block now or next unless chosen; ring (day by block, day as one arc, block now, none); the liquid sloshes only when its level changes |
| `feature/home/Pill.kt` | Every item reads its own gestures (`SlotGestures`); swipes are only caught by an item that has its own; the "Today" button draws the study item; the study item's long press writes a note |
| `core/data/PillSlot.kt` (+ `SlotGesturesTest`), `LauncherAction.kt` | `SlotGestures` (tab-separated lines per item code), action `StudyNote` ("Write a note for tonight's check-in") |
| `core/settings` | Pill → Study item: what it shows, icon, frame, size, colour, progress ring; hidden: app, picture, item gestures. `SettingDependencies.choiceParents` hides options that do not apply to the chosen look |
| `feature/settings/PillStudyEditor.kt` (new), `Editors.kt`, `MainScreen.kt` | Study item card (use an app's icon, use a picture, add it to the pill), sliders button per item for its own tap / long press / swipes |
| `StudyHub.kt`, `Study.kt` | `DayRing` takes a stroke width; the old `PillStudy` removed |

Not verified on the phone: picture decoding of HEIC photos (the picker hands JPEG for most), the look of the frames at small sizes.

---

## Entry 9: 0.9.3, P3: a study-first search page, the calendar no longer cut (2026-11-17, Opus 5.5)

Why the calendar was cut (screenshot 1): the empty page put the widgets in a scroll area anchored at the top, and a
widget's height was its cell count × (screen × 0.8 / rows), about half the screen for the calendar. Above the keyboard
there is less than that, so the scroll area started at the top and its bottom, the calendar's last week, was out of view.

| File | Change |
|---|---|
| `feature/home/SearchStudy.kt` (new) | `SearchPage` (cards in the chosen order, scrolling from the bottom), `NextBlockCard` (now / next, steps, Focus / Done / Start, the next step tickable; a tap grows the hub out of the card), `DayStrip` (classes grey, blocks in their colours: done full, running filling, missed outlined, skipped grey; the time now), `ExamCountdown` (B1 colours, hours at 3 days or less, long press for the next three), `CommandChips`, `ClaudeChips`, `StudyCommands`, `studyRows` |
| `feature/home/SearchOverlay.kt` | `SearchResult.Study`; `?text` to Claude and `!next !exam !plan !abs` before bangs; search widgets capped to the space above the keyboard; the field keeps its cursor at the end of text put in by a chip |
| `core/data/StudyFormat.kt`, `StudyCalendar.kt` (+ tests) | `StudyDay.exams` (class calendars, six months), `StudyDay.absences` (`Note: absent …`, five months), `isExam`, `absentModule` |
| `core/data/SearchCards.kt` (new), settings, `feature/settings/SearchCardsEditor.kt` (new) | The cards and their order (Settings → Search) |
| `HomeController.kt` | Drawer and search springs keep their speed when turned round without a finger (D125) |

Not changed: the search transition itself (finger tracking, "Lift", the pill turning into the field, the blurred
wallpaper fading in), which already did what P3 asks; the top-field variant of search keeps its old empty page.

Also in 0.9.3 (P10, study workflow):

| Spec | What | Where |
|---|---|---|
| A6 leave-by | `StudyGlance.LeaveBy`: from an hour before leaving to 15 min after, first class − "Morning trip to class" (90 min); ring = the hour running out, red from 10 min | `Study.kt`, settings `study.leaveBy`, `study.commute` |
| B3 attendance | `StudyGlance.Attendance` 5 min to 3 h after each class, ✓ / ✗ on the clock line and in the hub; ✗ writes `Note: absent <module> <HH:mm>` (read by !abs and the check-in); answers kept per day | `Study.kt`, `StudyHub.kt`, `StudyCalendar.answerAttendance`, setting `study.attendance` |
| C3 missed block | The pill's study item breathes twice every 15 s while a block ended unmarked (layer only, no redraw of the pill); ended blocks get "Reschedule" (Claude, pre-filled, with the block) | `PillStudy.kt`, `Study.kt` |

Tests: `StudyGlanceMorningTest` (leave-by window and lateness, attendance timing, answered, latest first).

Not verified on the phone: the exam regex against your real event titles, the cards' spacing at your font size.

---

## Entry 10: 0.9.4, P9: icon motion rebuilt (2026-11-17, Opus 5.5)

You called Opus's per-icon animations "bad and mostly useless". What was there: eleven press motions (squishy, jelly,
sink, grow, shrink, rise, tilt, pop, fade, bouncy, gentle), most of them variations of one scale; no edit-mode wobble;
nothing on launch or return.

| What | How |
|---|---|
| Press styles | Two kept: **Squish** (crisper: down with damping 0.86 at stiffness 1800, back with width and height a little out of step, 540 / 620) and **Tilt** (at most 9° towards the finger, smaller scale, always a highlight from the finger), plus **Off**. A stored removed style falls back to Squish; the Looks that used them now use Squish or Tilt |
| Settings | Effects and touch: Press motion, Press intensity (was Press depth), **Press speed** (new, 50–200 %), Wobble (how much the rebound rings), **Wobble while editing** (new: off / subtle 1.4° / lively 2.6°) |
| Edit-mode wobble | One frame clock for the whole page (runs only while editing with it on), read in each item's layer: no recomposition. Each item has its own phase and pace from its id; big items (widgets) turn less; the item being resized or dragged stays still |
| Close: landing | Coming back from an app opened from a home icon, that icon lands (starts about 13 % larger and settles with the rebound) at the moment the home screen settles in. Watched with a snapshot flow, so only that icon moves |
| Launch | Unchanged: the press squashes as the system grows the app's window out of the icon (Motion → how apps open); the press's way back is hidden under the opening app |
| Preview | Settings → Effects and touch: every few presses one icon lands; with "Wobble while editing" on, the demo icons shiver |

Not verified on the phone: the feel of the new springs at 90 Hz, the wobble's cost on a full page (one layer update
per item per frame while editing).

Also in 0.9.4 (P10, study workflow):

| Spec | What |
|---|---|
| A5 decompression | After "home" (Wi-Fi, charger or the chip), the line under the clock shows "Rest until 19:40 · then Proba 2" with the rest filling a ring (Settings: Rest after getting home, 20 min; 0 = off); then the block, with a Start button (focus timer) on the line once it is within 45 min. The hub's main action is "Start <block>" in that window |
| D4 wind-down | From 22:30 (Settings: Wind down from; off / 21:30 / 22:00 / 22:30 / 23:00) until 5:00 the wallpaper eases to 32 % saturation and a touch darker over 2.4 s (glass follows, it samples the wallpaper), and the line under the clock says "Evening check-in · 2/3 blocks done" with a button that sends it; once sent from the launcher it is not offered again that night |

| C1 speed bump | During a running, open block, an app tagged `distracting` (Settings: Tag of the apps that wait; any names, comma separated) opened from the launcher (home, dock, drawer, search, pill, folders: everything goes through `launch`) waits behind a glass card grown from its icon: "Micro · 23 min left", "Back to Micro" (the hub) and "Open anyway" unlocking after 5 s. Back closes it. **Limit:** apps opened from notifications, recents or another app are not held back; a launcher cannot intercept those without an accessibility service that watches every app, which this launcher deliberately does not do (it only watches the system launcher, D2) |

Tests: rest window and progress, no rest without a block to come, wind-down before everything but a running block, the wind-down hours.

---

## Entry 11: 0.9.5, launcher-data: confirmed exams, absence meter, grades, the daily flashcard (2026-11-17, Opus 5.5)

Result of the earlier pushes: run 64 (0.9.2) failed on the emulator with 19 checks and 11 "Liquid Launcher isn't
responding" dialogs from step 2 on. Nothing in 0.9.2 runs on the default home screen's path in a way that could block
(the pill's new code only works for items with their own gestures or the study item, neither is on by default), the
log showed a very slow emulator (window checks of 500+ ms, the launcher drawn 2.4 s after launch), and the re-run of
the same build passed every check. So: an emulator flake, as in run 39. 0.9.2 was released by the re-run; 0.9.3 and
0.9.4 were pushed together after it.

| File | Change |
|---|---|
| `core/data/StudyFeed.kt` (new, + `StudyFeedTest`) | `StudyFeed` and its parts (spec part 1), `StudyFeedParser` (plain or Google HTML descriptions, text around the JSON ignored), `AbsenceMeter.levels` (B2 bands), `Grades.average` (E2, null until every mark has its coefficient) |
| `core/data/StudyCalendar.kt`, `StudyFormat.kt` | `load()` finds today's "launcher-data" event (else the latest earlier one), else the local copy; `StudyDay.feed`, `feedSource`, `unconfirmedExams`; launcher-data's exams come first; `answerCard` (wrong → `Note: flashcard wrong …`) |
| `feature/home/SearchStudy.kt` | New cards: absence meter (liquid tubes, UNVERIFIED badge, never "safe"), grade average, daily flashcard (face down; each tap turns it a half turn on a spring; right / wrong); `≈` on unconfirmed exam dates; `!abs` reads launcher-data first; an exam without an event opens the calendar on its day |
| `core/data/SearchCards.kt`, settings | The three cards in the card list (default order: widgets, timeline, exam, absences, grades, flashcard, next, commands, claude, apps); "Calendar with launcher-data" (Launcher); the local copy |
| `feature/settings/HomeSetupEditors.kt` | Study companion → launcher-data: where it is read from now, the local copy with "Check and keep" (invalid JSON is not kept) and "Start from the example" |
| `docs/05-study-integration.md` | The launcher's new lines and notes, and launcher-data version 1, for the check-in's instructions |

**For the evening check-in (outside this repository):** its instructions must add writing the `launcher-data` event in
a calendar named "Launcher" (create it once in Google Calendar). Until then the local copy in settings works.

---

## Entry 12: 0.9.6, P6: the letter index as a short rail that never triggers Back (2026-11-17, Opus 5.5)

Why Back fired: the index's touch strip ran the whole height of the screen edge with `systemGestureExclusion`, but
Android honours at most 200 dp of exclusion per edge (it keeps the lowest 200 dp of what an app asks for and
ignores the rest), so most of the strip still belonged to the Back gesture.

| What | How |
|---|---|
| Rail (new default, Settings → Drawer index → Letter index) | A glass capsule 30 dp wide and at most 180 dp tall (Rail height), its lower end at 26 % of the drawer's height from the bottom (Rail position), 12 dp in from the edge (Rail distance). Its touch area (rail + 18 dp towards the middle, to the edge, 8 dp above and below) stays under 200 dp tall and is wholly excluded from Back |
| Letters | As many as fit at 17 dp (about ten), dots between; a drop of the accent marks where the list is and glides as it scrolls; letters on the drop take its readable colour |
| Magnifier | While the finger is on the rail, a glass capsule beside it turns like a wheel through the letters around the finger's continuous position: the one under the finger large and in the accent, the neighbours smaller, fainter and bending away in a wave; it opens and closes on springs from the rail's side |
| Haptics, glide, highlight | As before: a tick per letter (Feedback setting), the list glides with the Follow delay (now one helper for both styles), the apps of the letter are highlighted |
| String | The 0.6.0 index stays as the other style; its own options are hidden while the rail is chosen, and the rail's while the string is |
| Preview | Settings → Drawer index runs a pretend thumb up and down the rail |

Not verified on the phone: the rail's height for your thumb, whether MagicOS honours exclusion rects the same way
(AOSP does; the strip keeps working either way, the risk is only Back firing near it).

---

## Entry 13: 0.9.7, E3 module-coloured glass and E1 board photo (2026-11-17, Opus 5.5)

| Spec | What | Where |
|---|---|---|
| E3 | Every glass surface mixes 42 % of the block now or next's colour (its event colour, which the check-in sets from its colour map) into its tint, easing over 1.4 s when the block changes; dark and light glass keep their character (the mix is into each surface's own tint). Setting "Glass in the colour of the block" (on) | `GlassConfig.accentTint`, `HomeScreen.kt` |
| E1 | Action "Photo of the board" (gestures, pill, home menu) and a "Board · <module>" chip in the hub while a class is on or ended less than 30 min ago: the camera writes straight into `Pictures/ESSAI/<module>/<yyyy-MM-dd>_<module>.jpg` (a pending MediaStore entry, kept when the photo is taken, removed when not; Android numbers a second photo of the same day). The module is the class now, else the block now, else "Notes". No storage or camera permission is needed | `HomeController.boardPhoto`, `MainActivity` (TakePicture) |

---

## Entry 14: 0.9.8, P5 (part): a bigger tag button, a drawer field with a purpose (2026-11-17, Opus 5.5)

| What | How |
|---|---|
| Tag button | Settings → Drawer tabs and tags → Tag button size (36–64 dp, default 50, was a fixed 36): the round button at the end of the chip row, bottom right by default, under the thumb; the row and the drawer's padding grow with it; the icon and the filter dot scale with it |
| Drawer field, empty | Its hint is the study glance in a few words ("Micro · 23 min", "Leave 6:30", "Present?") with its ring in place of the magnifying glass; tapping the ring opens the study hub. Without a glance it stays "Search apps". The field's accessibility name is still "Search apps" |
| Drawer field, Ask Claude | A sparkle button in the field: closes the drawer and opens search in Claude mode |

Not done in P5 (next session): handing `!next !exam !plan !abs ?` typed in the drawer field over to search (needs the
keyboard to move from one field to the other without dropping: test on the phone first), the tag list opening as a
container transform from the button (today it grows from the button's centre with a spring, 0.4.2), and the drawer's
top tabs pill taking part in the home-pill morph.

---

## Entry 15: 0.9.9, E5 Sunday review (2026-11-17, Opus 5.5)

On Sundays `load()` also reads Monday to today: study blocks planned and done (✓), focus minutes from the status
lines, and "Note: absent" records. A search card "Sunday review" (in the card list, before Next block) shows the four
numbers with the days to the next exam in its B1 colour; a tap sends a weekly-review prompt (with the study header) to
Claude. On other days the card is not there.

---

## Entry 16: 0.9.10, P7 (part): no glass box behind the widgets (2026-11-17, Opus 5.5)

You did not like a liquid background box behind the clock or widgets. The clock had none by default already; the
Calendar, Now card and Today widgets sat on a glass card. Settings → Home screen → "Behind the calendar and study
widgets": **None** (new default: on the wallpaper in the clock's text colour; their buttons sample the wallpaper)
or "A liquid glass card" (as before). Applies on pages and on the search screen.

---

## Entry 17: 0.9.11, A4 commute layout and C4 exam-week layout (2026-11-17, Opus 5.5)

| What | How |
|---|---|
| Choosing | Settings → Study companion → "Layouts that switch by themselves": a saved layout (or none) for the trip home and one for exam week |
| When | Trip: from the end of today's last class until "home" is noticed (Wi-Fi, charger or the chip), at most 4 h. Exam week: the 7 days before the next exam (launcher-data's or the timetable's) and the exam day until it is over. The trip wins when both apply |
| Back | When the context ends, the layout from before comes back, through the usual layout transition; if you switched layout by hand meanwhile, it stays as you chose. The state (context on, layout applied, layout before) survives restarts; nothing happens until the calendar has been read, so a restart cannot end a trip early |
| Trip card | The hub's "On the way home?" shows the "Pendant le trajet : …" line of tonight's first block |
| Tests | `ContextLayoutsTest`: the trip window, home ends it, the 4 h cap, exam week, the trip first, steps remembering the base and respecting a hand switch |

Not done for A4: hiding heavy widgets below 30 % battery, data saver, offline notes of tonight's module. Not done for C4:
filtering the drawer to `study` and one folder per module of annales (the exam layout can carry its own drawer tab and
folders: layouts save the home screen and their own settings).

---

## Entry 18: 0.9.12, P4 part one: the real liquid glass (2026-11-17, Opus 5.5)

The glass you disliked was the backdrop library's lens: a fixed displacement curve with a 7-tap rainbow and a
separate highlight stroke; "Nice liquid" only renamed its sliders. It is replaced by a shader written for this
launcher, `core/designsystem/LiquidShader.kt`:

| Part | How |
|---|---|
| Shape | The glass is a slab with a rounded edge: "Edge width" wide (default 16 dp, never more than half a surface), "Thickness" × that high (0.5 to 3.5; default 2) |
| Refraction | Each ray, looking straight down, is bent by Snell's law where the edge slopes (index of refraction 1 + 0.8 × "Refraction": 1.48 by default, like glass) and lands further inside, by the glass's height there. So the edge squeezes what is behind it into a thin bright band and slightly magnifies further in; nothing is bent at the very rim or on the flat top |
| Dispersion | Red, green and blue are bent by three indices ("Rainbow strength"): fringes only where the edge bends, three samples instead of seven |
| Swell | "Thick glass" also magnifies the middle a little ("Swell") |
| Specular rim | From the surface normal: Fresnel-weighted light on the edge facing "Light comes from", 40 % of it on the opposite edge, in the edge light colour ("Light on the edge") |
| Depth shading | The edge away from the light darkens ("Depth shading") |
| Grain | A fine static grain ("Grain") |
| Blur | Unchanged: the backdrop blur runs first, the shader bends the blurred picture |

One compiled shader serves every surface (each makes its effect with its own values); it is compiled off the main
thread when the home screen starts and the log says "liquid glass shader ready" (the emulator test checks it). If it
does not compile on a phone, the glass falls back to the old lens by itself. minSdk is 33, so "below Android 13" does
not exist for this app; the fallback is for a driver that refuses the shader. Buttons use the same glass with a
narrower edge. Settings → Liquid glass → "Glass" switches back to the simple lens.

**Tune the glass** (Settings → Liquid glass, first row; Diagnostics' benchmark ends there too): three glass surfaces
over your wallpaper and moving tiles (Pause/Move), drawn with your settings; below, a live slider for every value
(each step is applied while the finger moves), the switches, the light's angle, and presets: Real glass (default),
Clear, Frosted, Thick, Subtle, Your keyboard. "Measure" still times the three quality tiers and sets the automatic
fallback. "Your keyboard" and the "Nice liquid" look now carry your theme's frost: blur 9, colour 125 %, edge light
0.86 (as Light on the edge 85 %), bend 0.6, rainbow 0.25, no grain, tint 10 %.

Checked before pushing: the shader was compiled (strict ES2 rules) and rendered with Skia on your own wallpaper from
the theme backup, at the defaults and with the keyboard preset: `docs/reference/real-glass-preview.jpg` (left the
default, right your keyboard's frost). Not measured on the phone: the frame rate (run Measure in Tune the glass).

Left of P4: the theme engine (your backup's surfaces, gradients, borders, glows, radii and pressed states applied
to every button, pill and chip, with an editor and JSON import/export).

---

## Entry 19: 0.9.13, P4 part two: your WM Keyboard theme styles every button and chip (2026-11-17, Opus 5.5)

WM Keyboard (MIT; this time `github.com` answered, so its code was read) keeps a theme as `ThemeSpec` with a
`FrostSpec`, and derives what a theme leaves out in `KbTheme`. The launcher's `LiquidTheme`
(`core/data/LiquidTheme.kt`) takes the parts a launcher has, with the same rules:

| WM Keyboard | Launcher | Your theme |
|---|---|---|
| keys: fill, gradient, text, pressed, border, elevation, shape, radius | buttons | `#1C3C323E` (11 % plum-grey), text `#E9E9EE`, pressed `#723F61`, pill |
| enter key | the main button when "Main button colour" is "Like the other buttons" (else the block's colour, as before) | same as the keys |
| chips: fill, text, on, border, shape, radius | chips | `#322B35`, on `#723F61` with `#E9E9EE` (your accent `#D182AF` does not read on it: under 3:1, so WM's rule picks the key text) |
| toolbar circles | round icon buttons | `#433D323F`, icon `#E9E9EE` at 65 % |
| frost: style, blur, saturation, brightness, rim, rim colour, grain, refraction, dispersion, surfaces | the glass under each control (when the control sits on glass) | FROSTED, blur 9, colour 125 %, rim 0.86, no grain, every surface glass |
| accent | Settings → Theme → Accent | `#D182AF` |
| (key colour) | Settings → Theme → Glass tint colour | `#3C323E` |

Missing values are derived as WM Keyboard does: pressed = a quarter from the key to its text (in Oklab, as
Compose), the active tool falls back to the pressed key and the active chip to the active tool, a text on a fill
is the first candidate with 3:1 contrast, else black or white. Decorative shapes (arch, leaf, hexagon, scallop,
ticket, slant) are drawn rounded, as WM Keyboard does for containers; squircle as a rounded rectangle with a larger
radius (Compose has none).

How it draws (`Controls.kt`): every `GlassButton` (Today, the Now card, the hub, sheets, the clock gallery, the
layouts sheet) and every chip row (drawer tags, wallpaper studio, edit chips) take the theme. Pressed and
selected colours ease with a spring (never a jump). On glass the frost is drawn with the real glass shader: for
FROSTED a hairline rim lit on the edge facing the light and shaded on the other, no bend; for LIQUID the edge bends
and splits colours too. A border, a shadow and a glow are drawn when the theme has them.

**Default:** your theme, from `docs/reference/nice_liquid.wmtheme.json`; a unit test checks that importing that
file gives exactly the built-in default. Settings → Buttons → "Buttons and chips": Your theme, or the launcher's own
buttons (the earlier options come back). The theme card there shows its colours and imports (the launcher's
`.liquidtheme.json`, WM Keyboard themes and backups), exports, and goes back to your keyboard theme; an imported
theme's accent and glass colour go to Settings → Theme. **Migration:** once, on the first start of 0.9.13, the
theme's accent and glass colour are written where you never chose your own; a colour you had set is kept.

Correction to 0.9.12: your theme's frost style is FROSTED (no bend; its refraction 0.6 and dispersion 0.25 are
WM's defaults, used only by LIQUID), and its rim is a hairline lit on one edge and dark on the other. The "Your
keyboard" glass preset and the "Nice liquid" look now say exactly that: blur 9, colour 125 %, bend 0, edge 4 dp,
light on the edge 85 %, shading 60 %, no rainbow, no swell, no grain (no glow either: WM has none). The real liquid
glass stays the launcher's default glass.

Also: Tune the glass is now still unless you press Move (it moves while measuring), which is what let the emulator test read the screen (0.9.12 was not released for that).

Not done: editing the theme surface by surface in the launcher (import/export works; the editor is next), menus
and popups (your theme's `#443A47` popups are opaque; the long-press menu keeps the glass for now, see Questions).

---

## Entry 20: 0.9.14, the theme editor (P4 done) (2026-11-17, Opus 5.5)

Settings → Buttons → "Edit the theme…", under the theme card: for buttons, the main button, chips and round buttons,
every colour of the theme with its opacity (fill, text, pressed, text when pressed, selected, text when selected,
border, glow colour), the border width, the shape (pill, rounded, squircle, cut, sharp; circle for round buttons),
the corner radius, a sheen (none, soft, bright, smoked: WM Keyboard's frost-preset sheens; a theme's own sheen is
kept and shown), glass under it on or off, shadow and glow. Then the frost under the controls (frosted or liquid,
blur, colour, brightness, rim of light and its colour, grain; bend and rainbow for liquid), and the theme's accent
and glass tint (written to Settings → Theme). Every change is written as the slider moves, so the buttons in the
preview at the top of the section follow; a colour picker's Cancel puts the colour back. The sheen now spans the
control whatever its size (it was drawn over a fixed 1000 px box in 0.9.13).

With this, P4 is done: the real glass, Tune the glass, and the theme engine with your theme as the default, import,
export and the editor.

---

## End of this session (2026-11-17): state for the next model

**Version shipped:** 0.9.14 is the last push of this session. Released and verified on the emulator by CI: 0.9.0 (run 62),
0.9.1 (run 63), 0.9.2 (run 64, second attempt; the first failed on a slow emulator, see entry 11). 0.9.3 and 0.9.4 were
pushed together (run 65); 0.9.5 to 0.9.9 together (run 66, passed); 0.9.10 and 0.9.11 together (run 67, passed); 0.9.12 (run 68: built, unit tests passed, the real glass shader compiled on the emulator, but not released: my new check of the tuning panel found no screen dump, because the bench kept moving after measuring and the screen never settled; 0.9.13 keeps it still unless you press Move); 0.9.13 (run 69: did not compile, a smart cast across modules in `Controls.kt`; fixed) and 0.9.14 together after it. Check the GitHub releases for the newest `v0.9.x`; if a
run failed, its log says where (`ci/smoke.sh` prints PASS / FAIL per check). Nothing of this session was tried on the phone.

| Workstream | State |
|---|---|
| Audit | Done (top of this session) |
| P1 a. redirect during calls | Done (0.9.0) |
| P1 b. drawer / keyboard ghost | Done (0.9.0) |
| P2 study hub, pill, clock notice, A2, A3, B4 | Done (0.9.1, 0.9.2): hub grown out of the pill, drag to close, timeline, arrival, shift tonight, notes, templates, check-in; the pill's study item fully customisable; the clock line with one action |
| P3 search page | Done (0.9.3): study-first cards in your order, calendar never cut, commands. The transition was already finger-driven and interruptible; unchanged |
| P4 real liquid glass, WM Keyboard theme engine | Done (0.9.12 to 0.9.14): the real glass (AGSL: Snell refraction by the edge's shape, dispersion, swell, specular rim, depth shading, grain) with Tune the glass (live sliders, presets); the theme engine after WM Keyboard's model, your backup as the default theme of every button, chip and round button, import/export, the editor, accent and glass colour. Menus and popups keep the glass (question 8) |
| P5 drawer | Partly (0.9.8): bigger tag button, field with the study glance and Ask Claude. Not: command hand-off from the drawer field, tag list as a container transform, the top tabs pill in the home-pill morph |
| P6 scroll index | Done (0.9.6): the rail |
| P7 widgets | Partly (0.9.10): no glass box behind the built-in widgets by default. Not: per-widget layout / font / accent / fields options, restyling other apps' widgets (re-tint their view tree) with a per-widget off switch |
| P8 layouts and wallpaper | Partly: context triggers for layouts (trip home, exam week; 0.9.11). Not: layout previews, per-layout wallpaper choice UI, switch timer, time windows, the LayoutsSheet redesign, per-wallpaper blur/dim/saturation/vignette, tone feeding the glass, wallpaper quick tools |
| P9 icon animations | Done (0.9.4) |
| P10 rest of Phase 5 | Done: A4 (layout switch + trip line), A5, A6, B1 (+ ≈), B2, B3, C1, C3, C4 (layout switch), D3, D4, E1, E2, E3, E4, E5, launcher-data. Not: C5 notification triage (needs a notification listener: a new permission and service), A4's battery rule, C4's annales folders |
| P11 motion pass | Partly: drawer, search and hub springs keep their speed when turned round (D125); the rest of the pass not done |

**Known bugs and risks**
- The emulator in CI is sometimes too slow (run 64: "isn't responding" dialogs and redirects missed); a re-run passes.
- Exclusion rects on MagicOS: the rail assumes AOSP's 200 dp rule; if Back still fires on the rail, lower "Rail height".
- The study hub's glass, the rail's magnifier and the module tint are unmeasured on the phone (frame rate).
- `Note:` events are written to the first writable calendar whose name contains your task-calendar word ("Tasks").

**Questions for Med** (each with the assumption used)
1. Merge into `main`? Assumed no: releases come from this branch, as always.
2. "Lowest-priority block" when shortening the evening: assumed catch-up / Sunday check / checkpoint, else the last block.
3. Your timetable is in your main Google calendar? Assumed yes (for "end of last class", attendance and leave-by).
4. Claude package `com.anthropic.claude` and pre-filled shared text: assumed both work; tell me if the text arrives empty.
5. The evening check-in must now also write the `launcher-data` event in a calendar named "Launcher": shall I write the
   instructions for it? Until then the local copy in settings is used.
6. Wobble while editing is off by default (you disliked the old wobbles): on, subtle?
7. Speed-bump tag name: "distracting" assumed; give your apps that tag from their long-press menu.
8. Your theme's popups are opaque plum (`#443A47`): shall the long-press menu and the home menu take that colour
   (solid, no glass), or keep the glass? Assumed: keep the glass (only buttons, chips and round buttons are themed).
9. The default glass is the real liquid glass; your keyboard is frosted (no bend). Assumed: liquid glass for the big
   surfaces (pill, drawer, menus), your frost for buttons and chips; "Your keyboard" in Tune the glass makes
   everything frosted in one tap.

**Exact next step:** P5's transitions. (1) The tag list as a container transform: the tag button in the drawer
grows into the tag sheet (shared bounds, corner radius and glass morphing with one spring, interruptible; the chosen
tag shrinks back into the button's dot). (2) The home pill and the drawer's search field as one element: while the
drawer opens, the pill's bounds, corners and content cross into the field's (`DrawerMotion` progress drives both),
instead of the pill fading and the field appearing. (3) Commands typed in the drawer field (`!abs`, `!next`,
`!exam`, `!plan`, `?`) hand over to the search page's study commands. Then P7 (per-widget options) and P8.